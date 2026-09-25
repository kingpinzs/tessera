package app.tileshell.clock

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.settings.AboutPage
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.launch

/** The whole app: the tabs screen, or the sub-page the navigation holds. */
@Composable
fun ClockApp(nav: ClockNav, onBack: () -> Unit, onWindows: () -> Unit, onNotificationSettings: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ClockStore.get(context) }
    val world = remember(context) { WorldClockStore.get(context) }
    val menu = listOf(
        ClockMenuEntry("Notification settings", "clock_more:notifications", onNotificationSettings),
        ClockMenuEntry("About", "clock_more:about") { nav.barExpanded = false; nav.page = ClockPage.About },
    )
    when (val page = nav.page) {
        ClockPage.Tabs -> TabsScreen(nav, store, world, menu, onBack, onWindows)
        ClockPage.AlarmEditor -> AlarmEditorScreen(nav, store, menu, onBack, onWindows)
        ClockPage.Sounds -> SoundsScreen(nav, onBack, onWindows)
        ClockPage.MusicPicker -> MusicPickerScreen(nav, onBack, onWindows)
        ClockPage.TimerEditor -> TimerEditorScreen(nav, store, menu, onBack, onWindows)
        ClockPage.About -> ClockScaffold(onBack, onWindows) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("about_page")) { AboutPage() }
        }
        is ClockPage.TimerExpanded -> TimerExpandedScreen(nav, store, page.id, onBack, onWindows)
        ClockPage.StopwatchExpanded -> StopwatchExpandedScreen(nav, store, onBack, onWindows)
    }
}

/**
 * Every page's frame (phase 01's bar rule): the drawn status bar, an optional header (the tab band), the content
 * with its app bar at the bottom, and the drawn nav bar — Back is Back for the page, Windows goes Home.
 */
@Composable
fun ClockScaffold(
    onBack: () -> Unit,
    onWindows: () -> Unit,
    background: Color? = null,
    header: (@Composable () -> Unit)? = null,
    bar: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalShellColors.current
    val bg = background ?: colors.background
    Column(Modifier.fillMaxSize().background(bg).testTag("clock_root")) {
        W10mStatusBar(if (background != null) Modifier.background(background) else Modifier)
        header?.invoke()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            content()
            bar?.invoke(this)
        }
        W10mNavBar(onBack = onBack, onWindows = onWindows)
    }
}

/** The tabs screen: the band, the pager, and the current tab's app bar (or the compare strip in its place). */
@Composable
private fun TabsScreen(nav: ClockNav, store: ClockStore, world: WorldClockStore, menu: List<ClockMenuEntry>, onBack: () -> Unit, onWindows: () -> Unit) {
    val context = LocalContext.current
    val alarms by store.alarms.collectAsState()
    val timers by store.timers.collectAsState()
    val stopwatch by store.stopwatch.collectAsState()
    val zones by world.zones.collectAsState()
    val focusedTimer = nav.focusedTimer?.takeIf { id -> timers.any { it.id == id } } ?: timers.firstOrNull()?.id
    val buttons: List<BarButton> = when (nav.tab) {
        ClockTab.ALARM -> if (nav.alarmSelect) listOf(
            BarButton(Glyph.DELETE, "Delete", "alarm_select_delete", enabled = nav.alarmSelected.isNotEmpty()) {
                val ids = nav.alarmSelected
                Diagnostics.add("clock", "select delete ${ids.size} alarm(s): $ids")
                store.deleteAlarms(ids)
                nav.alarmSelect = false
                nav.alarmSelected = emptySet()
            },
        ) else listOf(
            BarButton(Glyph.ADD, "Add", "clock_bar:add") { nav.openAlarmEditor(AlarmDraft.new()) },
            BarButton(Glyph.MULTISELECT, "Select", "alarm_select", enabled = alarms.isNotEmpty()) { nav.alarmSelect = true },
        )
        ClockTab.WORLD_CLOCK -> listOf(
            BarButton(Glyph.ADD, "New", "clock_bar:add") { nav.worldSearch = true },
            BarButton(Glyph.GLOBE_CLOCK, "Compare", "clock_compare", enabled = zones.isNotEmpty()) { nav.compareOffset = 0; nav.compare = true },
        )
        ClockTab.TIMER -> if (nav.timerSelect) listOf(
            BarButton(Glyph.DELETE, "Delete", "timer_select_delete", enabled = nav.timerSelected.isNotEmpty()) {
                val ids = nav.timerSelected
                Diagnostics.add("clock", "select delete ${ids.size} timer(s): $ids")
                store.deleteTimers(ids)
                nav.timerSelect = false
                nav.timerSelected = emptySet()
            },
        ) else listOf(
            BarButton(Glyph.ADD, "Add", "clock_bar:add") { nav.openTimerEditor(TimerDraft.new()) },
            BarButton(Glyph.MULTISELECT, "Select", "timer_select", enabled = timers.isNotEmpty()) { nav.timerSelect = true },
            BarButton(Glyph.PIN, "Pin", "timer_pin:${focusedTimer ?: "none"}", enabled = focusedTimer != null) {
                timers.firstOrNull { it.id == focusedTimer }?.let { ClockTiles.pinTimer(context, it) }
            },
        )
        ClockTab.STOPWATCH -> listOf(
            BarButton(Glyph.PIN, "Pin", "stopwatch_pin") { ClockTiles.pinStopwatch(context) },
            BarButton(Glyph.SHARE, "Share", "stopwatch_share", enabled = stopwatch.laps.isNotEmpty()) { shareLaps(context, stopwatch.laps) },
        )
    }
    ClockScaffold(
        onBack = onBack,
        onWindows = onWindows,
        header = { TabHeader(nav) },
        bar = {
            if (nav.tab == ClockTab.WORLD_CLOCK && nav.compare) CompareStrip(nav)
            else ClockAppBar(buttons, menu, nav.barExpanded) { nav.barExpanded = it }
        },
    ) {
        ClockPager(nav, Modifier.fillMaxSize()) { tab ->
            Box(Modifier.fillMaxSize().testTag("clock_page:${tab.id}")) {
                when (tab) {
                    ClockTab.ALARM -> AlarmTab(nav, store)
                    ClockTab.WORLD_CLOCK -> WorldClockTab(nav, world)
                    ClockTab.TIMER -> TimerTab(nav, store)
                    ClockTab.STOPWATCH -> StopwatchTab(nav, store)
                }
            }
        }
    }
}

/**
 * The tab header (r11/clock.md §1): a 68.4-epx band in the app-bar fill under the status bar; four tabs, each an
 * icon over a caption label, centred at 82.3 / 146.7 / 211.4 / 275.8 epx (a 64.5 pitch); the selected tab's icon
 * and label in accent with a 64.4 × 3.7-epx accent underline at the band's bottom; the others at ≈ 40 % ink (1.7).
 * A tap JUMPS (M1: no slide) and logs `[motion] clock_tab` from the input to the first frame that shows it.
 */
@Composable
fun TabHeader(nav: ClockNav) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(ClockMetrics.BAND).background(ClockMetrics.BAND_FILL).testTag("clock_tabs")) {
        ClockTab.entries.forEachIndexed { i, t ->
            val centre = ClockMetrics.TAB_FIRST_CENTRE + i * ClockMetrics.TAB_PITCH
            val selected = t == nav.tab
            val ink = if (selected) colors.accent else colors.text.copy(alpha = DIM_INK)
            Box(
                Modifier.offset(x = (centre - ClockMetrics.TAB_PITCH / 2f).dp).width(ClockMetrics.TAB_PITCH.dp).fillMaxHeight()
                    .testTag("clock_pivot:${t.id}")
                    .semantics { role = Role.Tab; this.selected = selected }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { nav.tapTab(t) },
            ) {
                // 1.5: the icon's centre 27.2 epx below the band top (ink ≈ 16 × 17 epx → a 20-epx glyph).
                Box(Modifier.align(Alignment.TopCenter).offset(y = (ClockMetrics.TAB_ICON_CY - 10f).dp).size(20.dp), contentAlignment = Alignment.Center) {
                    BasicText(t.glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 20.sp, color = ink, textAlign = TextAlign.Center))
                }
                // 1.6: caption class, centred under the icon, cap top ≈ 43.5 epx below the band top.
                BasicText(
                    t.label,
                    Modifier.align(Alignment.TopCenter).offset(y = CapMetrics.topPaddingForCapTop(ClockMetrics.TAB_LABEL_CAP_TOP, 12f, 16f).dp),
                    style = ShellType.caption.copy(color = ink, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                if (selected) {
                    Box(Modifier.align(Alignment.BottomCenter).width(ClockMetrics.UNDERLINE_W).height(ClockMetrics.UNDERLINE_H).background(colors.accent).testTag("clock_tab_underline"))
                }
            }
        }
    }
    // The jump's clock: the first frame drawn after the tap is the frame that shows the new tab (T15-32).
    LaunchedEffect(nav.jumpToken) {
        if (nav.jumpToken == 0) return@LaunchedEffect
        withFrameNanos { }
        MotionClock.jump("clock_tab", nav.jumpInputUptime, SystemClock.uptimeMillis())
        Diagnostics.add("clock", "tab ${nav.tab.id} (tap)")
    }
}

/**
 * The pager under the band. A swipe drags the current page with the neighbour following it in; the release
 * settles on X13's 250 ms (U12, tagged) through the motion clock as `[motion] clock_swipe` — never on a tap,
 * which is a jump the header logs. Only the current page is composed, plus the neighbour while a drag shows it.
 */
@Composable
fun ClockPager(nav: ClockNav, modifier: Modifier = Modifier, content: @Composable (ClockTab) -> Unit) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        var animating by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val current = nav.tab
        val prev = ClockTab.entries.getOrNull(current.ordinal - 1)
        val next = ClockTab.entries.getOrNull(current.ordinal + 1)

        fun settle() {
            if (animating) return
            val target = when {
                drag < -widthPx / 4f && next != null -> next
                drag > widthPx / 4f && prev != null -> prev
                else -> null
            }
            val end = when (target) { null -> 0f; next -> -widthPx; else -> widthPx }
            val start = drag
            if (start == end) {
                target?.let { nav.settleTab(it) }
                drag = 0f
                return
            }
            animating = true
            scope.launch {
                MotionClock.animate("clock_swipe", Motion.PIVOT_SETTLE_MS, FastOutSlowInEasing) { f -> drag = start + (end - start) * f }
                target?.let { nav.settleTab(it); Diagnostics.add("clock", "tab ${it.id} (swipe)") }
                drag = 0f
                animating = false
            }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(current, widthPx) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { change, dx ->
                    if (animating) return@detectHorizontalDragGestures
                    drag = (drag + dx).coerceIn(if (next != null) -widthPx else 0f, if (prev != null) widthPx else 0f)
                    change.consume()
                }
            },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag }) { content(current) }
            if (drag > 0f && prev != null) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag - widthPx }) { content(prev) }
            if (drag < 0f && next != null) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag + widthPx }) { content(next) }
        }
    }
}
