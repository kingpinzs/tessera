package app.tileshell.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.applist.AppListMetrics
import app.tileshell.apps.toBitmap
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.music.catalogue.CatalogueTrack
import app.tileshell.music.catalogue.MusicCatalogueFetcher
import app.tileshell.music.catalogue.MusicCatalogueNotice
import app.tileshell.music.catalogue.MusicCatalogueResult
import app.tileshell.music.handoff.MusicHandoff
import app.tileshell.music.handoff.MusicHandoffEntry
import app.tileshell.music.handoff.MusicServicesTable
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import app.tileshell.video.handoff.StreamingHandoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Music's catalogue (phase 20 build task 8; Y5, a P4 design — phase 17's Y8 / Y9 forms in the Music idiom): a search
 * of MusicBrainz's recordings, and a title's page with its "Listen on <app>" list.
 *
 * The search is asked ONLY when the keyboard's Search key is pressed — never as the text is typed (MusicBrainz asks for
 * a request a second at most) — and one at a time: a submit made while one is out is not sent. Both requests and the
 * artwork's run off the main thread; [MusicCatalogueFetcher] holds the spacing and the session's rows.
 *
 * Tags: `catalogue_page:search`, `catalogue_search_box`, `catalogue_notice`, `catalogue_empty`,
 * `catalogue_row:<id>` (with `music_pri:` / `music_sub:` for the title and the artist) and `catalogue_art:<id>` or
 * `catalogue_placeholder:<id>`; `catalogue_page:title`, `catalogue_title`, `catalogue_title_artist`,
 * `catalogue_title_release`, `catalogue_title_art` or `catalogue_title_placeholder`, `handoff_heading`,
 * `handoff_service:<id>` with `handoff_label:<id>`, `handoff_none`, `handoff_gone`.
 */
@Composable
internal fun CatalogueSearch(nav: OnlineNav) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        val q = nav.catalogueText.trim()
        if (q.isEmpty() || nav.catalogueLoading) return
        nav.catalogueLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // Anything unexpected is "the catalogue isn't answering": never a crash carrying an address.
                runCatching { MusicCatalogueFetcher.search(context, q) }.getOrElse { MusicCatalogueResult(q, emptyList(), MusicCatalogueNotice.NOT_ANSWERING) }
            }
            nav.catalogueQuery = result.query
            nav.catalogueTracks = result.tracks
            nav.catalogueNotice = result.notice
            nav.catalogueLoading = false
        }
    }

    OnlineFrame(caption = "music", title = "find a song", tag = "catalogue_page:search") {
        Box(Modifier.fillMaxWidth().focusable()) {
            OutlinedField(
                value = nav.catalogueText, onValueChange = { nav.catalogueText = it }, tag = "catalogue_search_box",
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE, bottom = 8.dp).fillMaxWidth(),
                maxLength = 100, placeholder = "Search songs and artists", imeAction = ImeAction.Search,
                onImeAction = { submit() },
            )
        }
        nav.catalogueNotice.text?.let { text ->
            BasicText(
                text,
                style = ShellType.body.copy(color = colors.text),
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE, top = 4.dp, bottom = 8.dp).testTag("catalogue_notice"),
            )
        }
        val tracks = nav.catalogueTracks
        when {
            tracks.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                items(tracks.size, key = { tracks[it].id }) { i ->
                    val track = tracks[i]
                    TwoLineRow(tag = "catalogue_row:${track.id}", primary = track.title, secondary = track.artist, onTap = { nav.open(CatalogueTitlePage(track)) }) {
                        Cover(track, MusicMetrics.ART, "catalogue_art:${track.id}", "catalogue_placeholder:${track.id}")
                    }
                }
            }
            nav.catalogueQuery.isNotEmpty() && !nav.catalogueLoading && nav.catalogueNotice == MusicCatalogueNotice.NONE -> BasicText(
                "No results for “${nav.catalogueQuery}”",
                style = ShellType.body.copy(color = colors.subtleText),
                modifier = Modifier.padding(start = MusicMetrics.SIDE, top = 4.dp, end = MusicMetrics.SIDE).testTag("catalogue_empty"),
            )
        }
    }
}

/** A recording's artwork — its first release's front image — or the album-art placeholder when it has none to be had. */
@Composable
private fun Cover(track: CatalogueTrack, size: Dp, artTag: String, placeholderTag: String) {
    val context = LocalContext.current
    val art by produceState(CoverMemory.get(track.releaseId), track.releaseId) {
        if (value == null && track.releaseId != null) {
            value = withContext(Dispatchers.IO) { runCatching { MusicCatalogueFetcher.cover(context, track)?.asImageBitmap() }.getOrNull() }
                ?.also { CoverMemory.put(track.releaseId, it) }
        }
    }
    val bitmap = art
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).testTag(artTag))
    } else {
        Box(Modifier.size(size).background(LocalShellColors.current.accent).testTag(placeholderTag), Alignment.Center) {
            BasicText(Glyph.MUSIC, style = (if (size > MusicMetrics.ART) ShellType.header else ShellType.body).copy(color = Color.White, fontFamily = Brand.iconFont))
        }
    }
}

/** The covers decoded in this run of the app, by release (the bytes are the catalogue session's). */
private object CoverMemory {
    private val map = LruCache<String, ImageBitmap>(64)
    fun get(release: String?): ImageBitmap? = release?.let(map::get)
    fun put(release: String, art: ImageBitmap) { map.put(release, art) }
}

/** Y9: the title page's art, as large as phase 17's poster is wide. */
private val TITLE_ART = 112.dp

/**
 * A title's page: the art at the left, the title, the artist and the release beside it, then one app-list row for
 * every music app on the phone — "Listen on <app>" — each opening that app's own search for the title.
 *
 * The list is the phone as it is NOW: it is asked of the package manager again whenever the page is drawn after an app
 * was installed or removed or the Music app was returned to, and it is never kept. A tap on an app that has gone since
 * says so and the list is read again.
 */
@Composable
internal fun CatalogueTitle(page: CatalogueTitlePage) {
    val track = page.track
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    var entries by remember(track.id) { mutableStateOf<List<MusicHandoffEntry>?>(null) }
    var gone by remember(track.id) { mutableStateOf(false) }
    var draws by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { draws++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { draws++ }
    LaunchedEffect(track.id, draws) {
        entries = withContext(Dispatchers.IO) { runCatching { MusicHandoff.entries(context) }.getOrDefault(emptyList()) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("catalogue_page:title")) {
        Row(Modifier.fillMaxWidth().padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE, top = 12.dp)) {
            Cover(track, TITLE_ART, "catalogue_title_art", "catalogue_title_placeholder")
            Column(Modifier.padding(start = 12.dp)) {
                BasicText(track.title, Modifier.testTag("catalogue_title"), style = ShellType.subtitle.copy(color = colors.text), maxLines = 3, overflow = TextOverflow.Clip)
                BasicText(track.artist, Modifier.padding(top = 4.dp).testTag("catalogue_title_artist"), style = ShellType.body.copy(color = colors.text), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val release = listOf(track.release, track.year).filter { it.isNotEmpty() }.joinToString(NowPlayingMetrics.META_SEPARATOR)
                if (release.isNotEmpty()) {
                    BasicText(release, Modifier.padding(top = 4.dp).testTag("catalogue_title_release"), style = ShellType.caption.copy(color = colors.subtleText), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val listed = entries ?: return@Column
        Box(
            Modifier.fillMaxWidth().height(AppListMetrics.HEADER_BLOCK).padding(start = AppListMetrics.HEADER_X).testTag("handoff_heading"),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText("listen", style = ShellType.title.copy(color = colors.accent), maxLines = 1)
        }
        if (gone) {
            BasicText(
                MusicServicesTable.TEXT_NOT_INSTALLED,
                Modifier.padding(start = MusicMetrics.SIDE, bottom = 8.dp).testTag("handoff_gone"),
                style = ShellType.body.copy(color = colors.text),
            )
        }
        if (listed.isEmpty()) {
            BasicText("No music apps on this phone", Modifier.padding(start = MusicMetrics.SIDE).testTag("handoff_none"), style = ShellType.body.copy(color = colors.subtleText))
        }
        for (entry in listed) {
            ListenRow(entry) {
                scope.launch {
                    val opened = withContext(Dispatchers.IO) {
                        runCatching { MusicHandoff.open(context, entry, track.title, track.artist) }.getOrDefault(StreamingHandoff.Opened.NOT_INSTALLED)
                    }
                    if (opened == StreamingHandoff.Opened.NOT_INSTALLED) { gone = true; draws++ } else gone = false
                }
            }
        }
    }
}

/** Y9: an app-list row (R3 C2) — the app's own icon where the list's icon sits, "Listen on <app>" as the row text. */
@Composable
private fun ListenRow(entry: MusicHandoffEntry, onTap: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, entry.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(entry.packageName).toBitmap(123).asImageBitmap() }.getOrNull()
        }
    }
    PressRow(onTap, Modifier.fillMaxWidth().height(AppListMetrics.ROW).testTag("handoff_service:${entry.id}")) {
        icon?.let {
            Image(it, contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).padding(start = AppListMetrics.ICON_X).size(AppListMetrics.ICON))
        }
        BasicText(
            "Listen on ${entry.label}",
            Modifier.align(Alignment.CenterStart).padding(start = AppListMetrics.TEXT_X).testTag("handoff_label:${entry.id}"),
            style = ShellType.body.copy(color = LocalShellColors.current.text), maxLines = 1,
        )
    }
}
