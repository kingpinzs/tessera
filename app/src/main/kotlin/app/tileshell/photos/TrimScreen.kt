package app.tileshell.photos

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.media.MediaKind
import app.tileshell.media.MediaWrites
import app.tileshell.media.NewMedia
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Video trim (build task 5, Y13; r11/photos-pass2.md "Video trim"): the command bar at the TOP (Save a copy · Cancel ·
 * More), the track near the bottom over a scrim band with its selected part white, two filled white handle discs, a
 * hollow ring under the handle being moved, and the range's start and end times at the track's two ends — no centred
 * readout. The picture above is the frame at the handle last moved. Saving goes through Media3 Transformer to a
 * temporary file and from there into a NEW row through the shell's write layer; the original is never written.
 */
@Composable
fun BoxScope.TrimScreen(uri: Uri, entry: MediaEntry, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val retriever = remember { MediaMetadataRetriever() }
    var durationMs by remember { mutableLongStateOf(-1L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var active by remember { mutableStateOf(0) } // 0 the start handle, 1 the end handle
    var dragging by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var barExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { runCatching { retriever.release() } } }
    LaunchedEffect(uri) {
        val d = withContext(Dispatchers.IO) {
            runCatching {
                retriever.setDataSource(activity, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)
        }
        if (d < TrimMath.MIN_MS) {
            // An undecodable source: nothing is written, and the line says so (T17-23).
            Diagnostics.add("photosapp", "trim ${entry.id} failed: the video cannot be read")
            status = "This video can't be trimmed."
            durationMs = 0L
        } else {
            durationMs = d
            endMs = d
            Diagnostics.add("photosapp", "trim open ${entry.id} duration=$d")
        }
    }
    // The frame under the handle last moved.
    val showAt = if (active == 0) startMs else endMs
    LaunchedEffect(showAt, durationMs) {
        if (durationMs <= 0L) return@LaunchedEffect
        delay(60)
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { retriever.getScaledFrameAtTime(minOf(showAt, durationMs - 1) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, 1080, 1080) }.getOrNull()
        }
        if (bitmap != null) frame = bitmap
    }

    fun save() {
        if (saving || durationMs <= 0L) return
        saving = true
        status = null
        TrimExport.run(activity, uri, entry, startMs, endMs) { failure ->
            saving = false
            // As the photo editor: the screen stays up with the result, so this process's ring still carries the op's line.
            status = if (failure == null) "Saved a copy" else "Couldn't save a copy: $failure"
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        frame?.let {
            Image(it.asImageBitmap(), contentDescription = entry.name, modifier = Modifier.align(Alignment.Center).fillMaxWidth().testTag("trim_frame"), contentScale = ContentScale.FillWidth)
        }
        if (durationMs > 0L) {
            val inset = with(density) { PhotosMetrics.TRIM_INSET.toPx() }
            val trackW = widthPx - 2 * inset
            // The scrim band and, on it, the track, the handles, the ring and the two times.
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(PhotosMetrics.TRIM_SCRIM).background(Color.Black.copy(alpha = 0.5f)).testTag("trim_band")
                    .pointerInput(durationMs, trackW) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val sx = TrimMath.xAt(startMs, inset, trackW, durationMs)
                            val ex = TrimMath.xAt(endMs, inset, trackW, durationMs)
                            // The nearer handle takes the finger; between two equally near, the one on the finger's side.
                            active = if (abs(down.position.x - sx) < abs(down.position.x - ex) || (abs(down.position.x - sx) == abs(down.position.x - ex) && down.position.x < sx)) 0 else 1
                            dragging = true
                            // The handle keeps the offset it was picked up at, so a touch beside it does not jump it.
                            val grab = down.position.x - (if (active == 0) sx else ex)
                            down.consume()
                            do {
                                val e = awaitPointerEvent()
                                e.changes.firstOrNull()?.let { c ->
                                    val t = TrimMath.timeAt(c.position.x - grab, inset, trackW, durationMs)
                                    if (active == 0) startMs = TrimMath.moveStart(t, endMs) else endMs = TrimMath.moveEnd(t, startMs, durationMs)
                                    c.consume()
                                }
                            } while (e.changes.any { it.pressed })
                            dragging = false
                            Diagnostics.add("photosapp", "trim range ${entry.id} $startMs..$endMs")
                        }
                    },
            ) {
                val bandH = with(density) { PhotosMetrics.TRIM_SCRIM.toPx() }
                val cy = bandH - with(density) { PhotosMetrics.TRIM_TRACK_CY_FROM_BOTTOM.dp.toPx() }
                val sx = TrimMath.xAt(startMs, inset, trackW, durationMs)
                val ex = TrimMath.xAt(endMs, inset, trackW, durationMs)
                val thick = with(density) { PhotosMetrics.TRIM_TRACK.toPx() }
                Box(Modifier.offset { IntOffset(inset.roundToInt(), (cy - thick / 2f).roundToInt()) }.size(with(density) { trackW.toDp() }, PhotosMetrics.TRIM_TRACK).background(Color(135, 135, 135)).testTag("trim_track"))
                Box(Modifier.offset { IntOffset(sx.roundToInt(), (cy - thick / 2f).roundToInt()) }.size(with(density) { (ex - sx).coerceAtLeast(0f).toDp() }, PhotosMetrics.TRIM_TRACK).background(Color.White).testTag("trim_selected"))
                val handle = with(density) { PhotosMetrics.TRIM_HANDLE.toPx() }
                for ((id, x) in listOf("start" to sx, "end" to ex)) {
                    Box(Modifier.offset { IntOffset((x - handle / 2f).roundToInt(), (cy - handle / 2f).roundToInt()) }.size(PhotosMetrics.TRIM_HANDLE).background(Color.White, CircleShape).testTag("trim_handle:$id"))
                }
                if (dragging) {
                    val ring = with(density) { PhotosMetrics.TRIM_RING.toPx() }
                    val rx = if (active == 0) sx else ex
                    val ry = cy + with(density) { PhotosMetrics.TRIM_RING_BELOW.dp.toPx() }
                    Canvas(Modifier.offset { IntOffset((rx - ring / 2f).roundToInt(), (ry - ring / 2f).roundToInt()) }.size(PhotosMetrics.TRIM_RING).testTag("trim_ring")) {
                        drawCircle(Color.White, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
                val labelTop = CapMetrics.topPaddingForCapTop(PhotosMetrics.TRIM_SCRIM.value - PhotosMetrics.TRIM_TRACK_CY_FROM_BOTTOM - CapMetrics.capHeight(15f) / 2f, 15f)
                BasicText(TrimMath.label(startMs), Modifier.offset(x = 11.2.dp, y = labelTop.dp).testTag("trim_time:start"), style = ShellType.body.copy(color = Color.White), maxLines = 1)
                BasicText(TrimMath.label(endMs), Modifier.align(Alignment.TopEnd).padding(end = 11.2.dp).offset(y = labelTop.dp).testTag("trim_time:end"), style = ShellType.body.copy(color = Color.White), maxLines = 1)
            }
        }
        status?.let {
            BasicText(it, Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp).testTag("trim_status"), style = ShellType.body.copy(color = Color.White))
        }
        PhotoBar(
            buttons = listOf(
                PhotoBarButton(Glyph.SAVE_COPY, "Save a copy", "trim_save", enabled = durationMs > 0L && !saving && (startMs > 0L || endMs < durationMs)) { save() },
                PhotoBarButton(Glyph.DISMISS, "Cancel", "trim_cancel", enabled = !saving) { activity.finish() },
            ),
            menu = emptyList(), fill = PhotosMetrics.TRIM_BAR_FILL, tagPrefix = "trim", isExpanded = { barExpanded }, onExpand = { barExpanded = it }, top = true,
        )
        if (saving) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }) {
                BasicText("Saving a copy…", Modifier.align(Alignment.Center).testTag("trim_saving"), style = ShellType.subtitle.copy(color = Color.White))
            }
        }
    }
    BackHandler(enabled = barExpanded && !saving) { dismissOverlay { barExpanded = false } }
    BackHandler(enabled = saving) {}
}

/**
 * The trim itself (r3 D7): Media3 Transformer clips [uri] to [fromMs]..[toMs] as an MP4 (H.264 video, the source's own
 * audio as AAC) in the app's cache, the original's capture time is written into the clip's movie header, and the
 * clip is copied into a new video row through [MediaWrites]. No microphone and no audio capture are involved.
 * `[photosapp] trim <id> <fromMs>..<toMs> -> <uri>`, or `[photosapp] trim <id> failed: <why>` and no row.
 */
object TrimExport {
    @androidx.annotation.OptIn(UnstableApi::class)
    fun run(activity: ComponentActivity, uri: Uri, entry: MediaEntry, fromMs: Long, toMs: Long, done: (failure: String?) -> Unit) {
        val app = activity.applicationContext
        val temp = File(File(app.cacheDir, "trim").apply { mkdirs() }, "trim_${entry.id}_${System.nanoTime()}.mp4")
        fun fail(why: String) {
            temp.delete()
            Diagnostics.add("photosapp", "trim ${entry.id} failed: $why")
            done(why)
        }
        val item = MediaItem.Builder().setUri(uri)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(fromMs).setEndPositionMs(toMs).build())
            .build()
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val result = runCatching {
                        entry.dateTakenMs?.let { Mp4Dates.setCreationTime(temp, it) }
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                        val name = "${entry.name.substringBeforeLast('.').ifEmpty { "video" }}_trim_$stamp.mp4"
                        val folder = MediaWrites.copyPlacement(MediaKind.VIDEO, entry.relativePath)
                        EditRender.writes(app).save(NewMedia(MediaKind.VIDEO, name, "video/mp4", folder, entry.dateTakenMs)) { out -> temp.inputStream().use { it.copyTo(out, 1 shl 16) } } to "$folder$name"
                    }
                    temp.delete()
                    withContext(Dispatchers.Main) {
                        val saved = result.getOrNull()
                        when (val r = saved?.first) {
                            is MediaWrites.Result.Saved -> {
                                Diagnostics.add("photosapp", "trim ${entry.id} $fromMs..$toMs -> ${r.row.uri} (${saved.second})")
                                done(null)
                            }
                            is MediaWrites.Result.Failed -> fail(r.why)
                            null -> fail(result.exceptionOrNull()?.javaClass?.simpleName ?: "unknown")
                        }
                    }
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                fail("the video could not be decoded (${exportException.errorCodeName})")
            }
        }
        runCatching {
            Transformer.Builder(app).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(listener).build().start(item, temp.absolutePath)
        }.onFailure { fail(it.javaClass.simpleName) }
    }
}
