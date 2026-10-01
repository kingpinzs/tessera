package app.tileshell.start.podbay

import android.content.Intent
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.cortana.reminders.ReminderText
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.CalendarFeed
import app.tileshell.feeds.MusicFeed
import app.tileshell.prefs.PodId
import app.tileshell.prefs.ShellSettings
import app.tileshell.settings.SettingsActivity
import app.tileshell.settings.SettingsPage
import app.tileshell.start.drawTransport
import app.tileshell.tiles.Slot
import app.tileshell.tiles.engine.Transport
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.LocalStartTheme
import app.tileshell.clock.detectTapOrHold
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.input.pointer.pointerInput
import app.tileshell.ui.fluent.AppListBackdrop
import app.tileshell.ui.fluent.FluentSurface
import app.tileshell.ui.tokens.Scale
import app.tileshell.ui.tokens.ShellType
import app.tileshell.ui.tokens.StartGrid
import app.tileshell.weather.WeatherFeed
import app.tileshell.weather.WeatherFormat
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Where a pod's tap goes; Start opens it ([app.tileshell.StartActivity]), as the app list's taps are opened there. */
sealed interface PodTarget {
    /** The app behind a Start slot (CALENDAR, MUSIC), resolved as the tile resolves it; unassigned opens the picker. */
    data class SlotApp(val slot: Slot) : PodTarget
    data object Weather : PodTarget
    /** Tess's Reminders page, in her session. */
    data object TessReminders : PodTarget
}

/** Pod frame geometry in epx (P4 design, H2): nothing here is measured from W10M, which had no such page. */
private object PodMetrics {
    /** Under the drawn status bar, then the page title. */
    val TOP: Dp = BarMetrics.STATUS_EPX.dp
    val TITLE_BOTTOM = 12.dp
    /** Between pods. */
    val POD_GAP = 24.dp
    /** Between a pod's rows. */
    val ROW_GAP = 12.dp
    val HEADER_BOTTOM = 8.dp
    val CONTROL = 44.dp
    const val CONTROL_GLYPH_FRACTION = 0.5f
}

/**
 * Phase 14: the pod bay, the pager page left of Start (Decisions "Pod frame" and "The pods"). Pods stack in one
 * vertical scroll between the grid's margins in the fixed order Agenda, Weather, Now playing, Reminders, each a header
 * and its rows in Metro type on the app list's backdrop material, no box; a pod never scrolls inside itself.
 */
@Composable
fun PodBayPage(onLaunch: (PodId, PodTarget, Rect?) -> Unit) {
    val context = LocalContext.current
    val theme = LocalStartTheme.current
    val colors = LocalShellColors.current
    val settings = remember { ShellSettings.get(context) }
    val pods by settings.pods.collectAsState()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(pods) {
        Diagnostics.add("podbay", "pods enabled: ${pods.shown.joinToString(",") { it.id }.ifEmpty { "none" }}")
    }

    Box(
        Modifier
            .fillMaxSize()
            .testTag("pod_bay")
            .onGloballyPositioned { c ->
                // The app list's rule (AppListPage): the page counts as shown when at least half of it is in the window.
                val width = c.size.width
                val now = width > 0 && c.boundsInWindow().width >= width * 0.5f
                if (now != visible) visible = now
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = Scale.portraitWidthPx(context).toFloat()
            val grid = StartGrid(widthPx, theme.mediumColumns)
            val left = with(density) { grid.leftMarginPx.toDp() }
            val right = with(density) { grid.rightMarginPx.toDp() }
            val artSize = with(density) { grid.smallPx.toDp() }
            val pageHeight = maxHeight
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize().verticalScroll(scroll).testTag("pod_bay_scroll")) {
                // T14-11 / r3 D7: the app list's material exactly, with the app list's key arguments (a root the size of
                // the pager page), so one layer serves both pages. It sits INSIDE the scroll, held on the viewport by the
                // scroll's own offset, so it stays still while the pods scroll — and, being a child of the scroll rather
                // than a sibling under it, it is not dropped from the accessibility tree as a node the scroll covers:
                // `acrylic:pod_bay` reports the page's bounds (E14).
                AppListBackdrop(
                    theme.backgroundUri, colors.background, visible,
                    modifier = Modifier.offset { IntOffset(0, scroll.value) }.height(pageHeight),
                    surface = FluentSurface.POD_BAY,
                )
                Column(Modifier.fillMaxWidth().padding(start = left, end = right, top = PodMetrics.TOP, bottom = PodMetrics.POD_GAP)) {
                    BasicText(
                        Brand.POD_BAY_NAME.replaceFirstChar { it.uppercase() },
                        style = ShellType.title.copy(color = colors.text),
                        modifier = Modifier.padding(bottom = PodMetrics.TITLE_BOTTOM).testTag("pod_bay_title"),
                    )
                    if (pods.shown.isEmpty()) {
                        PodLine("Turn on ${Brand.POD_NAME}s in Start settings", "pod_bay_empty") { openSettings(context, SettingsPage.POD_BAY) }
                    }
                    pods.shown.forEachIndexed { index, pod ->
                        if (index > 0) Spacer(Modifier.height(PodMetrics.POD_GAP))
                        when (pod) {
                            PodId.AGENDA -> AgendaPod(onLaunch)
                            PodId.WEATHER -> WeatherPod(visible, onLaunch)
                            PodId.NOWPLAYING -> NowPlayingPod(artSize, onLaunch)
                            PodId.REMINDERS -> RemindersPod(onLaunch)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A pod's press (Decisions "Edit mode": a hold on a pod does nothing — pods are configured in Settings): a tap opens,
 * a press held to [app.tileshell.start.Edit.HOLD_MS] (the shell's one hold) is a hold and does nothing, and a press a
 * scroll takes over is neither — the Clock's [detectTapOrHold]. The press highlight is the list rows' ([ROW_PRESS_ALPHA]).
 */
@Composable
private fun PodPress(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .pointerInput(onClick) { detectTapOrHold(onTap = onClick, onHold = {}) }
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent),
        content = content,
    )
}

private fun openSettings(context: android.content.Context, page: SettingsPage) {
    context.startActivity(Intent(context, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, page.name))
    Diagnostics.add("podbay", "settings ${page.name} opened")
}

/** Logs a pod's content whenever it changes: `[podbay] pod <id>: <n> rows` or `[podbay] pod <id>: empty: <reason>`. */
@Composable
private fun LogContent(pod: PodId, content: PodBayRules.Content) {
    val line = when (content) {
        is PodBayRules.Content.Rows -> "pod ${pod.id}: ${content.rows.size} rows"
        is PodBayRules.Content.Empty -> "pod ${pod.id}: empty: ${content.reason}"
    }
    LaunchedEffect(line) { Diagnostics.add("podbay", line) }
}

private fun boundsOf(c: LayoutCoordinates?): Rect? = c?.boundsInWindow()?.let {
    Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
}

/** A pod's header: 15-epx semibold in the accent; a tap opens the pod's app or page. */
@Composable
private fun PodHeader(pod: PodId, onTap: (Rect?) -> Unit) {
    val colors = LocalShellColors.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    PodPress({ onTap(boundsOf(coords)) }, Modifier.fillMaxWidth().onGloballyPositioned { coords = it }.testTag("pod_header:${pod.id}")) {
        BasicText(pod.title, style = ShellType.base.copy(color = colors.accent), modifier = Modifier.padding(bottom = PodMetrics.HEADER_BOTTOM))
    }
}

/** The one subtle line of an empty pod (or of the empty bay). */
@Composable
private fun PodLine(text: String, tag: String, onTap: (() -> Unit)? = null) {
    val colors = LocalShellColors.current
    val body = @Composable {
        BasicText(text, style = ShellType.body.copy(color = colors.subtleText), modifier = Modifier.testTag(tag))
    }
    if (onTap != null) PodPress(onTap, Modifier.fillMaxWidth()) { body() } else body()
}

/** A pod's rows: 15-epx body in the theme foreground, second lines subtle, 12 epx apart, one line each, ellipsised. */
@Composable
private fun PodRows(pod: PodId, rows: List<PodRow>, onTap: ((Int, Rect?) -> Unit)?) {
    val colors = LocalShellColors.current
    rows.forEachIndexed { n, row ->
        row.subheader?.let { sub ->
            if (n > 0) Spacer(Modifier.height(PodMetrics.ROW_GAP))
            BasicText(
                sub,
                style = ShellType.caption.copy(color = colors.subtleText, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.testTag("pod_subheader:${pod.id}:${sub.lowercase()}"),
            )
        }
        if (n > 0 || row.subheader != null) Spacer(Modifier.height(if (row.subheader != null) 4.dp else PodMetrics.ROW_GAP))
        var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val content = @Composable {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { coords = it }) {
                BasicText(
                    row.text,
                    style = ShellType.body.copy(color = colors.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("pod_row:${pod.id}:$n"),
                )
                row.subline?.let {
                    BasicText(
                        it,
                        style = ShellType.body.copy(color = colors.subtleText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("pod_subline:${pod.id}:$n"),
                    )
                }
            }
        }
        if (onTap != null) PodPress({ onTap(n, boundsOf(coords)) }, Modifier.fillMaxWidth()) { content() } else content()
    }
}

@Composable
private fun PodFrame(pod: PodId, content: PodBayRules.Content, onHeader: (Rect?) -> Unit, onRow: ((Int, Rect?) -> Unit)?, onEmpty: (() -> Unit)? = null) {
    LogContent(pod, content)
    Column(Modifier.fillMaxWidth().testTag("pod:${pod.id}")) {
        PodHeader(pod, onHeader)
        when (content) {
            is PodBayRules.Content.Rows -> PodRows(pod, content.rows, onRow)
            is PodBayRules.Content.Empty -> PodLine(content.line, "pod_empty:${pod.id}", onEmpty)
        }
    }
}

@Composable
private fun AgendaPod(onLaunch: (PodId, PodTarget, Rect?) -> Unit) {
    val context = LocalContext.current
    val items by CalendarFeed.agenda.collectAsState()
    // Read again on every resume: a grant made in Setup with nothing on the calendar changes no row (the agenda goes
    // from empty to empty), so nothing else would redraw the "Calendar access is off" line (gate review). A revoke
    // kills the process. The read is the permission check alone.
    var access by remember { mutableStateOf(CalendarFeed.hasAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = CalendarFeed.hasAccess(context) }
    val content = PodBayRules.agenda(access, items) { ReminderText.time(context, it) }
    val open: (Rect?) -> Unit = { onLaunch(PodId.AGENDA, PodTarget.SlotApp(Slot.CALENDAR), it) }
    PodFrame(
        PodId.AGENDA, content, open, { _, r -> open(r) },
        onEmpty = if (!access) ({ openSettings(context, SettingsPage.CHECKLIST) }) else null,
    )
}

@Composable
private fun WeatherPod(visible: Boolean, onLaunch: (PodId, PodTarget, Rect?) -> Unit) {
    val context = LocalContext.current
    val state by WeatherFeed.state.collectAsState()
    // The X22 line follows the feed's own stale flag; the clock text is WeatherFormat's, as on the tile.
    val content = PodBayRules.weather(state, System.currentTimeMillis()) { WeatherFormat.clock(context, it) }
    val open: (Rect?) -> Unit = { onLaunch(PodId.WEATHER, PodTarget.Weather, it) }
    val noLocation = content is PodBayRules.Content.Empty && content.reason == "no location"
    // The line sends the user to Setup; coming back, the pod must not keep saying "off" until the feed's 30-minute
    // refresh (E4 run 1: 90 s after the grant it still did). While it shows that line, Start resuming or the page coming
    // into view asks the feed whether access is back — and only then does the feed refresh (WeatherFeed.onAccessLineShown:
    // no request leaves the device while Location is still off or the permission still missing).
    val retry = { if (noLocation) WeatherFeed.onAccessLineShown() }
    LaunchedEffect(visible) { if (visible) retry() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { retry() }
    PodFrame(
        PodId.WEATHER, content, open, { _, r -> open(r) },
        onEmpty = if (noLocation) ({ openSettings(context, SettingsPage.CHECKLIST) }) else ({ open(null) }),
    )
}

@Composable
private fun NowPlayingPod(artSize: Dp, onLaunch: (PodId, PodTarget, Rect?) -> Unit) {
    val colors = LocalShellColors.current
    val now by MusicFeed.now.collectAsState()
    val playing = now
    val content: PodBayRules.Content =
        if (playing == null) PodBayRules.Content.Empty(PodBayRules.NOTHING_PLAYING, "no session")
        else PodBayRules.Content.Rows(listOf(PodRow(playing.track.title, playing.track.artist.ifBlank { null })))
    val open: (Rect?) -> Unit = { onLaunch(PodId.NOWPLAYING, PodTarget.SlotApp(Slot.MUSIC), it) }
    LogContent(PodId.NOWPLAYING, content)
    Column(Modifier.fillMaxWidth().testTag("pod:${PodId.NOWPLAYING.id}")) {
        PodHeader(PodId.NOWPLAYING, open)
        if (playing == null) {
            PodLine(PodBayRules.NOTHING_PLAYING, "pod_empty:${PodId.NOWPLAYING.id}")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One small-tile unit square: the session's art, or the tile fill with the music glyph (H3).
                Box(Modifier.size(artSize).background(colors.accent), contentAlignment = Alignment.Center) {
                    val art = playing.art
                    if (art != null) {
                        Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        BasicText(Glyph.MUSIC, style = ShellType.subtitle.copy(color = Color.White, fontFamily = Brand.iconFont))
                    }
                }
                Spacer(Modifier.width(PodMetrics.ROW_GAP))
                Box(Modifier.weight(1f)) {
                    PodRows(PodId.NOWPLAYING, (content as PodBayRules.Content.Rows).rows) { _, r -> open(r) }
                }
            }
            Row(Modifier.padding(top = PodMetrics.ROW_GAP / 2), horizontalArrangement = Arrangement.Start) {
                listOf(Transport.PREVIOUS, Transport.PLAY_PAUSE, Transport.NEXT).forEach { t ->
                    PodPress({ MusicFeed.send(t) }, Modifier.size(PodMetrics.CONTROL).testTag("pod_control:${PodId.NOWPLAYING.id}:${t.name}")) {
                        Canvas(Modifier.fillMaxSize().padding(PodMetrics.CONTROL * (1f - PodMetrics.CONTROL_GLYPH_FRACTION) / 2)) {
                            drawTransport(t, playing.playing, colors.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersPod(onLaunch: (PodId, PodTarget, Rect?) -> Unit) {
    val context = LocalContext.current
    val store = remember { ReminderStore.get(context) }
    val all by store.reminders.collectAsState()
    // Today / Coming up moves with the clock as well as with the store: re-derived each minute.
    val minute by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    val content = remember(all, minute) {
        PodBayRules.reminders(all.filterNot { it.completed }, minute, ReminderText.endOfToday(minute)) {
            ReminderText.listSubline(context, it, store)
        }
    }
    val open: (Rect?) -> Unit = { onLaunch(PodId.REMINDERS, PodTarget.TessReminders, it) }
    PodFrame(PodId.REMINDERS, content, open, { _, r -> open(r) })
}
