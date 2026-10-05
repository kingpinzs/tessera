package app.tileshell.video

import android.Manifest
import android.app.ActivityOptions
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The My videos page's measured places (Y5; r11/movies-tv.md 1.4, 10586, HIGH), in epx. */
object MyVideosMetrics {
    /** 1.4.5: the first row's top is the chrome's bottom + 48; the group header sits in that band. */
    const val HEADER_BLOCK = 48f

    /** 1.4.1: the header's cap top is 19.7 below the chrome. */
    const val HEADER_CAP_TOP = 19.7f

    /** 1.4.6: tile 112 + caption block 109. */
    const val ROW_PITCH = 221f

    /** 1.4.9: the caption's first baseline is 24.5 under the tile; 15 epx on a 20-epx line, two lines at most. */
    const val CAPTION_BASELINE = 24.5f
    const val CAPTION_FONT = 15f

    /** 1.4.10: clipped, with no ellipsis, at tile left + 100. */
    const val CAPTION_WIDTH = 100f
}

/** What the page shows: null while the first read runs. */
private data class Library(val access: Boolean, val groups: List<VideoGroup>)

/**
 * My videos (build task 7; Y5): the phone's videos as W10M's 112-epx tile grid, a group per folder under its accent
 * header, each tile captioned with the file name without its extension and nothing else (no duration, 1.4.11). It
 * follows MediaStore with a ContentObserver. Without the Videos grant the page says so, names the Setup checklist and
 * offers the grant where the empty state is (phase 10 task 10's pattern).
 *
 * Tags: `video_group:<folder>`, `video_tile:<id>` (the 112-epx square), `video_caption:<id>`, `video_empty`,
 * `video_grant`. Lines: `[video] library: <n>`, `[video] access=<GRANTED|DENIED>`.
 */
@Composable
fun MyVideosPage(nav: VideoNav, activity: ComponentActivity) {
    val context = activity as Context
    var changes by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { changes++ }
        }
        context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    var library by remember { mutableStateOf<Library?>(null) }
    LaunchedEffect(changes, nav.resumes) {
        // A scan changes many rows at once: one read after they settle.
        if (library != null) delay(250)
        val access = VideoLibrary.hasAccess(context)
        val rows = if (access) withContext(Dispatchers.IO) { runCatching { VideoLibrary.load(context) }.getOrDefault(emptyList()) } else emptyList()
        if (!access) Diagnostics.add("video", "access=DENIED")
        Diagnostics.add("video", "library: ${rows.size}")
        library = Library(access, VideoGroups.group(rows))
    }

    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Diagnostics.add("video", "videos permission request: ${if (granted) "granted" else "denied"}")
        if (granted) {
            changes++
        } else if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_VIDEO)) {
            // Android will not ask again: the app's own settings page is the only place left to allow it.
            Diagnostics.add("video", "videos permission will not be asked again: opening the app's settings")
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", activity.packageName, null)))
        }
    }

    val colors = LocalShellColors.current
    val lib = library ?: return
    BoxWithConstraints(Modifier.fillMaxSize().testTag("hub_page:myvideos")) {
        if (!lib.access || lib.groups.isEmpty()) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp)) {
                BasicText(
                    if (lib.access) "There are no videos on this phone."
                    else "Tessera can't read the videos on this phone yet. You can allow it here, or from Videos in Start settings > Setup checklist.",
                    Modifier.testTag("video_empty"), style = ShellType.subtitle.copy(color = Color.White),
                )
                if (!lib.access) {
                    BasicText(
                        "Allow access to videos",
                        Modifier.padding(top = 16.dp).testTag("video_grant")
                            .pointerInput(Unit) { detectTapGestures { grant.launch(Manifest.permission.READ_MEDIA_VIDEO) } },
                        style = ShellType.body.copy(color = colors.accent),
                    )
                }
            }
            return@BoxWithConstraints
        }
        val columns = VideoGroups.columns(maxWidth.value)
        LazyColumn(Modifier.fillMaxSize()) {
            for (group in lib.groups) {
                item(key = "g:${group.path}") {
                    Box(Modifier.fillMaxWidth().height(MyVideosMetrics.HEADER_BLOCK.dp)) {
                        BasicText(
                            group.name,
                            Modifier.padding(start = VideoGroups.MARGIN.dp, top = CapMetrics.topPaddingForCapTop(MyVideosMetrics.HEADER_CAP_TOP, 15f).dp)
                                .testTag("video_group:${group.name}"),
                            style = ShellType.base.copy(color = colors.accent), maxLines = 1,
                        )
                    }
                }
                val rows = group.items.chunked(columns)
                items(rows.size, key = { "r:${group.path}:${rows[it].first().id}" }) { index ->
                    Row(Modifier.fillMaxWidth().height(MyVideosMetrics.ROW_PITCH.dp).padding(start = VideoGroups.MARGIN.dp)) {
                        for ((i, video) in rows[index].withIndex()) {
                            if (i > 0) Spacer(Modifier.width((VideoGroups.PITCH - VideoGroups.TILE).dp))
                            VideoTile(video) { play(activity, video, group) }
                        }
                    }
                }
            }
        }
    }
}

/** One tile: the frame fitted inside the 112-epx square on black (1.4.7, 1.4.12), and the caption under it. */
@Composable
private fun VideoTile(video: VideoRow, onTap: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.width(VideoGroups.TILE.dp).pointerInput(video.id) { detectTapGestures { onTap() } }) {
        Box(Modifier.size(VideoGroups.TILE.dp).background(Color.Black).testTag("video_tile:${video.id}"), contentAlignment = Alignment.Center) {
            val thumb by produceState<ImageBitmap?>(VideoThumbs.cached(video.id), video.id) {
                if (value == null) value = VideoThumbs.load(context, video.id)
            }
            thumb?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        BasicText(
            VideoGroups.caption(video.displayName),
            Modifier.padding(top = (MyVideosMetrics.CAPTION_BASELINE - MyVideosMetrics.CAPTION_FONT * CapMetrics.ASCENT_RATIO).dp)
                .width(MyVideosMetrics.CAPTION_WIDTH.dp).clipToBounds().testTag("video_caption:${video.id}"),
            style = ShellType.body.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Clip,
        )
    }
}

/** MediaStore's own thumbnails, kept for the page's life. */
private object VideoThumbs {
    private val cache = LruCache<Long, ImageBitmap>(60)

    fun cached(id: Long): ImageBitmap? = cache.get(id)

    suspend fun load(context: Context, id: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.loadThumbnail(uri, Size(336, 336), null).asImageBitmap().also { cache.put(id, it) }
        }.getOrNull()
    }
}

/** A tap on a tile: the shared player by explicit component, as a cut (Y6: library → player has no transition). */
private fun play(activity: ComponentActivity, video: VideoRow, group: VideoGroup) {
    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video.id)
    val intent = Intent(Intent.ACTION_VIEW)
        .setClass(activity, PlayerActivity::class.java)
        .setDataAndType(uri, "video/*")
        .putExtra(PlayerActivity.EXTRA_QUEUE, group.queue)
    activity.startActivity(intent, ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle())
}
