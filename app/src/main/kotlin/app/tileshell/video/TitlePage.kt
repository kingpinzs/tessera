package app.tileshell.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.applist.AppListMetrics
import app.tileshell.apps.toBitmap
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import app.tileshell.video.catalogue.CatalogueItem
import app.tileshell.video.catalogue.CatalogueNotice
import app.tileshell.video.catalogue.CatalogueResult
import app.tileshell.video.catalogue.TitleDetails
import app.tileshell.video.handoff.ServicesTable
import app.tileshell.video.handoff.StreamingHandoff
import app.tileshell.video.handoff.StreamingService
import app.tileshell.video.handoff.TitleRef
import app.tileshell.BuildConfig
import kotlinx.coroutines.launch

/** A title's page, opened from Browse. */
data class TitleSub(val item: CatalogueItem) : HubSub {
    override val title: String get() = item.title
}

/** The services a title page lists: the ones the catalogue names for the title that are on the phone now. */
private class WatchRows(val installed: List<StreamingService>, val missing: List<StreamingService>)

/**
 * The title page (Y8 after r11/movies-tv.md 1.7.10; Y9): the art at the left, the title, "year • genre", the
 * description with More, then one app-list row per streaming app on the phone that has the title — the service's icon
 * at the glyph position and "Watch on <service>" (R3 C2's row) — with JustWatch's credit under them. No installed
 * service has it → "Not on this phone". The rows are worked out again whenever the page is drawn after a package
 * change or a return to the app, so the list is live.
 *
 * Tags: `hub_title`, `hub_title_facts`, `hub_overview`, `hub_overview_more`, `hub_title_poster`,
 * `hub_watch:<service>` with `hub_watch_label:<service>`, `hub_watch_none`, `hub_watch_gone`, `hub_justwatch`,
 * `hub_title_notice`.
 */
@Composable
fun TitlePage(nav: VideoNav, model: BrowseModel, sub: TitleSub) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    var details by remember(sub) { mutableStateOf<TitleDetails?>(null) }
    var notice by remember(sub) { mutableStateOf(CatalogueNotice.NONE) }
    var rows by remember(sub) { mutableStateOf<WatchRows?>(null) }
    var gone by remember(sub) { mutableStateOf(false) }
    var expanded by remember(sub) { mutableStateOf(false) }
    var packages by remember { mutableIntStateOf(0) }
    val ref = TitleRef(sub.item.type, sub.item.id, sub.item.title, sub.item.year)

    // An app installed or removed while the page is up changes the rows on the next draw.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { packages++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(sub) {
        val r = VideoCalls.io("catalogue lookup", CatalogueResult<TitleDetails>(null, CatalogueNotice.NOT_ANSWERING, true)) { model.catalogue.lookup(sub.item.type, sub.item.id, sub.item.title) }
        details = r.value
        notice = r.notice
    }
    LaunchedEffect(details, packages, nav.resumes) {
        val d = details ?: return@LaunchedEffect
        rows = VideoCalls.io("watch rows", WatchRows(emptyList(), emptyList())) {
            val named = d.providers.mapNotNull { ServicesTable.match(it, BuildConfig.DEBUG) }.distinctBy { it.id }
            val (here, away) = named.partition { StreamingHandoff.installed(context, it) }
            // Each service the catalogue names that is not on the phone gets its line; it gets no row.
            away.forEach { StreamingHandoff.noteNotInstalled(it, ref) }
            WatchRows(here, away)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("hub_page:title")) {
        notice.text?.let { BasicText(it, Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp).testTag("hub_title_notice"), style = ShellType.body.copy(color = Color.White)) }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Box(Modifier.testTag("hub_title_poster")) {
                Poster(model, details?.item ?: sub.item, BrowseMetrics.POSTER_W, BrowseMetrics.POSTER_H, "hub_title_image", "hub_title_placeholder")
            }
            Column(Modifier.padding(start = 12.dp)) {
                BasicText(sub.item.title, Modifier.testTag("hub_title"), style = ShellType.subtitle.copy(color = Color.White), maxLines = 3, overflow = TextOverflow.Clip)
                val facts = listOf(sub.item.year, details?.genres?.firstOrNull().orEmpty()).filter { it.isNotEmpty() }.joinToString(" • ")
                BasicText(facts, Modifier.padding(top = 4.dp).testTag("hub_title_facts"), style = ShellType.body.copy(color = colors.subtleText), maxLines = 1)
            }
        }
        val overview = details?.item?.overview?.takeIf { it.isNotBlank() } ?: sub.item.overview
        if (overview.isNotBlank()) {
            BasicText(
                overview, Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp).testTag("hub_overview"),
                style = ShellType.body.copy(color = Color.White), maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            )
            if (!expanded) {
                BasicText(
                    "More", Modifier.padding(start = 12.dp, top = 4.dp).testTag("hub_overview_more").pointerInput(Unit) { detectTapGestures { expanded = true } },
                    style = ShellType.body.copy(color = colors.accent),
                )
            }
        }
        val r = rows
        if (r != null) {
            Box(Modifier.height(16.dp))
            if (gone) {
                BasicText("That app isn't installed any more", Modifier.padding(start = 12.dp, bottom = 8.dp).testTag("hub_watch_gone"), style = ShellType.body.copy(color = Color.White))
            }
            if (r.installed.isEmpty()) {
                BasicText("Not on this phone", Modifier.padding(start = 12.dp).testTag("hub_watch_none"), style = ShellType.body.copy(color = colors.subtleText))
            } else {
                for (service in r.installed) {
                    WatchRow(service) {
                        scope.launch {
                            val opened = VideoCalls.io("hand-off", StreamingHandoff.Opened.NOT_INSTALLED) { StreamingHandoff.open(context, service, ref) }
                            if (opened == StreamingHandoff.Opened.NOT_INSTALLED) { gone = true; packages++ }
                        }
                    }
                }
                // TMDB's terms: watch-provider data is credited to JustWatch where it is shown (BS-5).
                BasicText(Attribution.JUSTWATCH, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp).testTag("hub_justwatch"), style = ShellType.caption.copy(color = colors.subtleText))
            }
        }
    }
}

/** Y9: an app-list row (R3 C2) — the service's own icon where the list's icon sits, "Watch on <service>" as the row text. */
@Composable
private fun WatchRow(service: StreamingService, onTap: () -> Unit) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(service.id) {
        runCatching { context.packageManager.getApplicationIcon(service.packageName).toBitmap(123).asImageBitmap() }.getOrNull()
    }
    PressRow(onTap, Modifier.fillMaxWidth().height(AppListMetrics.ROW).testTag("hub_watch:${service.id}")) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).padding(start = AppListMetrics.ICON_X).size(AppListMetrics.ICON))
        }
        BasicText(
            "Watch on ${service.label}",
            Modifier.align(Alignment.CenterStart).padding(start = AppListMetrics.TEXT_X).testTag("hub_watch_label:${service.id}"),
            style = ShellType.body.copy(color = Color.White), maxLines = 1,
        )
    }
}
