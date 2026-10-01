package app.tileshell.calendar

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockMetrics
import app.tileshell.clock.LocalIs24h
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/** One event on one day of a view. */
private data class DayEvent(val instance: EventInstance, val day: LocalDate)

/** The instances of [day], all-day first, then by start, then by title (an Agenda group's order, and the Day view's). */
private fun eventsOf(instances: List<EventInstance>, day: LocalDate, zone: ZoneId): List<EventInstance> =
    instances.filter { day in EventRules.days(it.beginMs, it.endMs, it.allDay, zone) }
        .sortedWith(compareBy({ !it.allDay }, { it.beginMs }, { it.title.orEmpty() }))

/**
 * The views (build task 4; r11/calendar.md §1–§6): the header with ≡ and the month drop-down, the current view —
 * Agenda, Day or Week — and the app bar Today · New · View · …, on #1A1A1A. The view area carries
 * `cal_view_mode:<agenda|day|week>` with `selected`, the whole page `cal_view`.
 */
@Composable
fun CalendarViews(nav: CalendarNav, model: CalendarModel, sync: SyncState, clock: Int, onGrant: (write: Boolean) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(clock) { ZoneId.systemDefault() }
    val today = remember(clock) { LocalDate.now(zone) }
    val first = EventRules.firstDayOfWeek(sync.firstDayOfWeek, locale)
    val mode = nav.mode
    val selected = nav.selected

    // Each view asks the provider for its own window only (Decisions "Recurrence and time zones"): the day, the week,
    // or the weeks of the agenda loaded so far.
    val weekStart = EventRules.weekStart(selected, first)
    var agendaWeeks by remember(weekStart) { mutableIntStateOf(AGENDA_WEEKS) }
    val (from, to) = when (mode) {
        ViewMode.AGENDA -> weekStart to weekStart.plusWeeks(agendaWeeks.toLong())
        ViewMode.DAY -> selected to selected.plusDays(1)
        ViewMode.WEEK -> weekStart to weekStart.plusDays(7)
    }
    LaunchedEffect(mode, from, to, model.canRead, clock) { model.want(mode, from, to) }
    val loaded = model.loaded?.takeIf { it.view == mode && it.from == from && it.to == to }
    // `[calendar] view <name> <from>..<to>: n instances in <ms> ms` (T16-17): the query plus the first frame that shows it.
    LaunchedEffect(loaded?.token) {
        val window = loaded ?: return@LaunchedEffect
        withFrameNanos { }
        Diagnostics.add("calendar", "view ${window.view.id} ${window.from}..${window.to.minusDays(1)}: ${window.instances.size} instances in ${SystemClock.uptimeMillis() - window.startedUptimeMs} ms")
    }
    // ≡ show / hide is the shell's own list (r3 D4) and filters these views only.
    val hidden = remember(sync.hidden, model.calendars) { model.calendars.filter { it.key in sync.hidden }.mapTo(HashSet()) { it.id } }
    val shown = remember(loaded, hidden) { loaded?.instances?.filter { it.calendarId !in hidden } }
    val openEvent: (EventInstance) -> Unit = { nav.push(CalPage.Event(it.eventId, it.beginMs, it.endMs)) }

    Box(Modifier.fillMaxSize().testTag("cal_view")) {
        Column(Modifier.fillMaxSize()) {
            CalHeader(
                title = CalText.monthTitle(if (nav.monthOpen) nav.month.atDay(1) else selected, locale),
                monthOpen = nav.monthOpen,
                onMenu = {
                    val open = !nav.paneOpen
                    nav.closeOverlays()
                    nav.paneOpen = open
                    if (open) model.logCounts()
                },
                onTitle = {
                    val open = !nav.monthOpen
                    nav.closeOverlays()
                    nav.month = YearMonth.from(selected)
                    nav.monthOpen = open
                },
            )
            // The page tag carries `selected` (Harness contracts). Compose hands `selected` to accessibility as selected
            // only for a Tab — for any other role it reads as "checked" — so every node a row reads `selected` on is a Tab.
            Box(Modifier.fillMaxWidth().weight(1f).testTag("cal_view_mode:${mode.id}").semantics { role = Role.Tab; this.selected = true }) {
                when {
                    !model.canRead -> CalNotice(
                        "Calendar can't read your calendar. Allow it to see your events.",
                        Modifier.padding(start = CalMetrics.HEADING_X.dp, top = 24.dp, end = 24.dp), onGrant = { onGrant(false) },
                    )
                    model.providerOff != null -> CalNotice(
                        "The phone's calendar storage is turned off, so there are no events to show.",
                        Modifier.padding(start = CalMetrics.HEADING_X.dp, top = 24.dp, end = 24.dp),
                    )
                    else -> Column(Modifier.fillMaxSize()) {
                        nav.notice?.let { CalNotice(it, Modifier.padding(start = CalMetrics.HEADING_X.dp, top = 8.dp, end = 24.dp, bottom = 8.dp)) }
                        when (mode) {
                            ViewMode.AGENDA -> AgendaView(
                                nav, shown, from, to, today, zone, first, locale,
                                onMore = { if (loaded != null && agendaWeeks < AGENDA_MAX_WEEKS) agendaWeeks = minOf(agendaWeeks * 2, AGENDA_MAX_WEEKS) },
                                onEvent = openEvent,
                            )
                            ViewMode.DAY -> PagedView("cal_day_page", onPage = { nav.select(selected.plusDays(it.toLong())) }) { offset ->
                                DayView(selected.plusDays(offset.toLong()), if (offset == 0) shown else null, zone, locale, model, openEvent)
                            }
                            ViewMode.WEEK -> PagedView("cal_week_page", onPage = { nav.select(selected.plusWeeks(it.toLong())) }) { offset ->
                                WeekView(weekStart.plusWeeks(offset.toLong()), if (offset == 0) shown else null, today, zone, locale, onDay = { nav.select(it); nav.mode = ViewMode.DAY }, onEvent = openEvent)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(CalMetrics.BAR))
        }
        val buttons = listOf(
            BarButton(CalGlyph.TODAY, "Today", "cal_bar:today") { nav.closeOverlays(); nav.notice = null; nav.select(today) },
            BarButton(Glyph.ADD, "New", "cal_bar:new") {
                nav.push(CalPage.Editor(EditorState(CalendarEvents.newDraft(selected, LocalDateTime.now(zone), null, zone), "New event")))
            },
            BarButton(CalGlyph.WEEK, "View", "cal_bar:view") {
                val open = nav.barMenu != BarMenu.VIEW
                nav.closeOverlays()
                if (open) nav.barMenu = BarMenu.VIEW
            },
        )
        val viewMenu = listOf(ViewMode.AGENDA to CalGlyph.AGENDA, ViewMode.DAY to CalGlyph.DAY, ViewMode.WEEK to CalGlyph.WEEK).map { (m, glyph) ->
            CalMenuEntry(glyph, m.label, "cal_view_pick:${m.id}", selected = m == mode) { nav.notice = null; nav.mode = m }
        }
        val moreMenu = listOf(CalMenuEntry(Glyph.SETTINGS, "Settings", "cal_more:settings") { nav.push(CalPage.Settings) })
        CalAppBar(buttons, moreMenu, viewMenu, { nav.barMenu }) { next ->
            dismissOverlay { if (next != BarMenu.NONE) { nav.monthOpen = false; nav.paneOpen = false }; nav.barMenu = next }
        }
        MonthPanel(nav, today, first, locale)
        CalendarPane(nav, model, sync)
    }
}

/** The agenda loads this many weeks first, twice as many each time its end is reached, and no more than about a year ahead of the week it starts in. */
private const val AGENDA_WEEKS = 8
private const val AGENDA_MAX_WEEKS = 56

// ============================================================================================ Agenda (K2, K3)

/**
 * Agenda (K3.1): the week strip pinned above a scrolling list of day groups. A group is a day heading and its events;
 * the days listed are the days that have events, with today and the selected day always among them. Selecting a day
 * in the strip (or the month panel) brings its group to the top.
 */
@Composable
private fun AgendaView(
    nav: CalendarNav, instances: List<EventInstance>?, from: LocalDate, to: LocalDate, today: LocalDate, zone: ZoneId,
    first: DayOfWeek, locale: Locale, onMore: () -> Unit, onEvent: (EventInstance) -> Unit,
) {
    val selected = nav.selected
    val byDay = remember(instances, from, to, zone) {
        val map = HashMap<LocalDate, MutableList<EventInstance>>()
        instances.orEmpty().forEach { i ->
            val days = EventRules.days(i.beginMs, i.endMs, i.allDay, zone)
            var d = maxOf(days.start, from)
            val last = minOf(days.endInclusive, to.minusDays(1))
            while (d <= last) {
                map.getOrPut(d) { mutableListOf() }.add(i)
                d = d.plusDays(1)
            }
        }
        map.mapValues { (_, list) -> list.sortedWith(compareBy({ !it.allDay }, { it.beginMs }, { it.title.orEmpty() })) }
    }
    val days = remember(byDay, today, selected, from, to) {
        (byDay.keys + listOf(today, selected).filter { it >= from && it < to }).distinct().sorted()
    }
    Column(Modifier.fillMaxSize()) {
        WeekStrip(nav, today, first, locale, byDay.keys)
        val list = rememberLazyListState()
        // The selected day's group to the top: when the day is picked, and when its window first has something to show.
        LaunchedEffect(selected, from, instances != null) {
            val index = days.indexOfFirst { it >= selected }
            if (index >= 0) list.scrollToItem(index)
        }
        LazyColumn(Modifier.fillMaxSize().testTag("cal_agenda"), state = list) {
            items(days, key = { it.toString() }) { day -> DayGroup(day, byDay[day].orEmpty(), day == today, zone, locale, onEvent) }
            if (instances != null) {
                // The end of what is loaded: seeing it loads the next weeks.
                item(key = "more:$to") {
                    LaunchedEffect(to) { onMore() }
                    Spacer(Modifier.height(CalMetrics.ALL_DAY_PITCH))
                }
            }
        }
    }
}

/**
 * The week strip, the 15063 form (K2.1–K2.6; LOW, H1's note): day names over two week rows — the selected day's week
 * and the next, dimmed — on columns proportional to the width; the selected day a 32-epx accent square; a small
 * accent dot under a date that has events. A tap selects a day; a horizontal swipe moves a week.
 */
@Composable
private fun WeekStrip(nav: CalendarNav, today: LocalDate, first: DayOfWeek, locale: Locale, withEvents: Set<LocalDate>) {
    val accent = LocalShellColors.current.accent
    val start = EventRules.weekStart(nav.selected, first)
    val select by rememberUpdatedState<(LocalDate) -> Unit> { nav.select(it) }
    val current by rememberUpdatedState(nav.selected)
    val top = CalMetrics.HEADER.value
    BoxWithConstraints(
        Modifier.fillMaxWidth().height((CalMetrics.STRIP_BOTTOM - top).dp).testTag("cal_strip")
            // K1.4: a 1-epx black rule closes the strip.
            .drawBehind { drawRect(Color.Black, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > 40.dp.toPx()) select(current.plusWeeks(if (drag < 0) 1 else -1))
                    },
                ) { change, dx -> change.consume(); drag += dx }
            },
    ) {
        val scale = maxWidth.value / 360f
        val cellW = CalMetrics.COLUMN_PITCH * scale
        for (i in 0..6) {
            val x = CalMetrics.columnCentre(i) * scale - cellW / 2
            BasicText(
                CalText.shortDay(start.plusDays(i.toLong()).dayOfWeek, locale),
                Modifier.offset(x = x.dp, y = capTop(CalMetrics.DAY_NAMES_CAP_TOP - top, 15f)).width(cellW.dp),
                style = ShellType.body.copy(color = CalMetrics.GREY, textAlign = TextAlign.Center), maxLines = 1,
            )
        }
        for (row in 0..1) for (i in 0..6) {
            val day = start.plusDays((row * 7 + i).toLong())
            val isSelected = day == nav.selected
            val cy = CalMetrics.STRIP_ROW_CENTRE - top + row * CalMetrics.STRIP_ROW_PITCH
            Box(
                Modifier.offset(x = (CalMetrics.columnCentre(i) * scale - cellW / 2).dp, y = (cy - CalMetrics.STRIP_ROW_PITCH / 2).dp)
                    .size(cellW.dp, CalMetrics.STRIP_ROW_PITCH.dp)
                    .testTag("cal_strip_day:${CalText.iso(day)}").semantics { role = Role.Tab; this.selected = isSelected }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { nav.select(day) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Box(Modifier.size(CalMetrics.SELECTED).background(accent).testTag("cal_strip_selected"))
                BasicText(
                    day.dayOfMonth.toString(),
                    style = ShellType.body.copy(
                        color = when {
                            isSelected -> Color.White
                            day == today -> accent
                            row == 1 -> Color.White.copy(alpha = 0.5f)
                            else -> Color.White
                        },
                        textAlign = TextAlign.Center,
                    ),
                )
                if (day in withEvents && !isSelected) {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = (-5).dp).size(3.dp).background(accent.copy(alpha = if (row == 1) 0.5f else 1f), CircleShape))
                }
            }
        }
    }
}

/** The y that puts a text's cap top [capTopEpx] below its parent's top. */
private fun capTop(capTopEpx: Float, sizeEpx: Float): Dp = app.tileshell.ui.tokens.CapMetrics.topPaddingForCapTop(capTopEpx, sizeEpx).dp

/**
 * One day of the agenda (K3.2–K3.5; C1 083's rules): a 1-epx black rule, the heading — "Saturday 12" in white semibold,
 * today's in accent and larger — its events, or for a day with none "No events today" in grey.
 */
@Composable
private fun DayGroup(day: LocalDate, events: List<EventInstance>, isToday: Boolean, zone: ZoneId, locale: Locale, onEvent: (EventInstance) -> Unit) {
    val accent = LocalShellColors.current.accent
    Column(Modifier.fillMaxWidth().drawBehind { drawRect(Color.Black, size = Size(size.width, 1.dp.toPx())) }) {
        Box(Modifier.fillMaxWidth().height(CalMetrics.GROUP_HEAD)) {
            val style = if (isToday) ShellType.subtitle.copy(color = accent) else ShellType.base.copy(color = Color.White)
            CapText(CalText.dayHeading(day, locale), CalMetrics.HEADING_X, if (isToday) 18f else 16f, style, Modifier.testTag("cal_day:${CalText.iso(day)}"))
        }
        if (events.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(CalMetrics.ALL_DAY_PITCH)) {
                CapText(if (isToday) "No events today" else "No events", CalMetrics.HEADING_X, 12f, ShellType.subtitle.copy(color = CalMetrics.GREY), Modifier.testTag("cal_empty:${CalText.iso(day)}"))
            }
        }
        events.forEach { EventRow(it, zone, locale, onEvent) }
        Spacer(Modifier.height(CalMetrics.GROUP_FOOT))
    }
}

/**
 * An event row (K3.5–K3.8): the 8-epx colour bar flush with the left edge — solid, or a 2-epx outline for a free or
 * tentative event — the label at x 24.3 in the calendar colour's tint ("All day", or the start and end times stacked),
 * the title in white at x 92.5. An all-day bar is 40 epx on a 44-epx pitch; a timed bar 56 epx.
 */
@Composable
internal fun EventRow(event: EventInstance, zone: ZoneId, locale: Locale, onEvent: (EventInstance) -> Unit) {
    val is24h = LocalIs24h.current
    val color = colorOf(event.color)
    val tint = tintOf(event.color)
    val bar = if (event.allDay) CalMetrics.ALL_DAY_BAR else CalMetrics.TIMED_BAR
    CalPress(
        Modifier.fillMaxWidth().height(if (event.allDay) CalMetrics.ALL_DAY_PITCH else CalMetrics.TIMED_PITCH).testTag("cal_event:${event.eventId}"),
        onClick = { onEvent(event) },
    ) {
        Box(
            Modifier.size(CalMetrics.EVENT_BAR_W, bar).testTag("cal_event_bar:${event.eventId}")
                .let { if (event.free) it.background(CalMetrics.PAGE).border(2.dp, color) else it.background(color) },
        )
        val label = ShellType.caption.copy(fontSize = 13.sp, lineHeight = 16.3.sp, color = tint)
        if (event.allDay) {
            CapText("All day", CalMetrics.LABEL_X, 16f, label)
        } else {
            val begin = EventRules.local(event.beginMs, zone)
            val end = EventRules.local(event.endMs, zone)
            CapText(
                "${CalText.time(begin, is24h, locale)}\n${CalText.time(end, is24h, locale)}", CalMetrics.LABEL_X, 15.3f, label,
                Modifier.testTag("cal_event_time:${event.eventId}"), maxLines = 2,
            )
        }
        BasicText(
            EventRules.shownTitle(event.title),
            Modifier.offset(x = CalMetrics.EVENT_TITLE_X.dp, y = capTop((bar.value - 14f) / 2f, 20f)).padding(end = (CalMetrics.EVENT_TITLE_X + 8).dp)
                .testTag("cal_event_title:${event.eventId}"),
            style = ShellType.subtitle.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================================================ paging (Day, Week)

/**
 * A page that a horizontal swipe changes: the page follows the finger with its neighbour coming in, and the release
 * settles on X13's 250 ms through the motion clock as `[motion] <name>` (r11 U8: tagged approximation, H17).
 * [content] draws the page at an offset of −1, 0 or +1 from the current one.
 */
@Composable
private fun PagedView(name: String, onPage: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        var animating by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val page by rememberUpdatedState(onPage)
        fun settle() {
            if (animating) return
            val step = when {
                drag < -widthPx / 4f -> 1
                drag > widthPx / 4f -> -1
                else -> 0
            }
            val end = -step * widthPx
            val start = drag
            if (start == end) {
                drag = 0f
                return
            }
            animating = true
            scope.launch {
                MotionClock.animate(name, Motion.PIVOT_SETTLE_MS, FastOutSlowInEasing) { f -> drag = start + (end - start) * f }
                if (step != 0) page(step)
                drag = 0f
                animating = false
            }
        }
        Box(
            Modifier.fillMaxSize().clipToBounds().pointerInput(widthPx) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { change, dx ->
                    if (animating) return@detectHorizontalDragGestures
                    drag = (drag + dx).coerceIn(-widthPx, widthPx)
                    change.consume()
                }
            },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag }) { content(0) }
            if (drag > 0f) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag - widthPx }) { content(-1) }
            if (drag < 0f) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag + widthPx }) { content(1) }
        }
    }
}

// ============================================================================================ Day (U1: approximation, H15)

/** A timed event's block in the Day view: where it sits in the hour grid and which of its cluster's columns it takes. */
private data class Block(val event: EventInstance, val startMs: Long, val endMs: Long, val column: Int, val columns: Int)

/** Overlapping events share the width: each takes the first column free at its start, and a cluster's width is split by its widest moment. */
private fun layoutBlocks(events: List<EventInstance>, dayStartMs: Long, dayEndMs: Long, minMs: Long): List<Block> {
    data class Open(val event: EventInstance, val start: Long, val end: Long, var column: Int = 0)
    val items = events.map { e ->
        val start = maxOf(e.beginMs, dayStartMs)
        Open(e, start, maxOf(minOf(e.endMs, dayEndMs), start + minMs))
    }.sortedWith(compareBy({ it.start }, { -it.end }))
    val out = mutableListOf<Block>()
    var cluster = mutableListOf<Open>()
    var clusterEnd = Long.MIN_VALUE
    fun flush() {
        val columns = (cluster.maxOfOrNull { it.column } ?: 0) + 1
        cluster.forEach { out += Block(it.event, it.start, it.end, it.column, columns) }
        cluster = mutableListOf()
    }
    for (item in items) {
        if (cluster.isNotEmpty() && item.start >= clusterEnd) flush()
        val taken = cluster.filter { it.end > item.start }.mapTo(HashSet()) { it.column }
        item.column = generateSequence(0) { it + 1 }.first { it !in taken }
        cluster += item
        clusterEnd = maxOf(clusterEnd, item.end)
    }
    if (cluster.isNotEmpty()) flush()
    return out
}

/**
 * The Day view (r11/calendar.md U1's proposal; no capture exists — approximation, H15): the week-cell label as the
 * day's header, the all-day events in a band of their own (`cal_allday:<date>`), then the hours at a 48-epx row with
 * each timed event a block in its calendar's colour, as tall as it is long. The grid has as many rows as the day has
 * hours — 23 or 25 on a DST day — so a block is placed by elapsed time and a span across the change keeps its length.
 */
@Composable
private fun DayView(day: LocalDate, instances: List<EventInstance>?, zone: ZoneId, locale: Locale, model: CalendarModel, onEvent: (EventInstance) -> Unit) {
    val is24h = LocalIs24h.current
    val events = remember(instances, day, zone) { eventsOf(instances.orEmpty(), day, zone) }
    val allDay = events.filter { it.allDay }
    val timed = events.filter { !it.allDay }
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val hours = ((dayEnd - dayStart) / 3_600_000L).toInt()
    val hourPx = with(LocalDensity.current) { CalMetrics.HOUR.toPx() }
    Column(Modifier.fillMaxSize().background(CalMetrics.PAGE)) {
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            CapText(
                CalText.weekLabel(day, locale), CalMetrics.WEEK_LABEL_X, CalMetrics.WEEK_LABEL_CAP_TOP,
                ShellType.subtitle.copy(fontSize = 18.6.sp, color = CalMetrics.GREY), Modifier.testTag("cal_day:${CalText.iso(day)}"),
            )
        }
        // The all-day band: every all-day event of the day, never at a time.
        Column(Modifier.fillMaxWidth().testTag("cal_allday:${CalText.iso(day)}")) {
            allDay.forEach { EventRow(it, zone, locale, onEvent) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
        }
        val scroll = rememberScrollState()
        // The grid opens an hour above the day's first timed event, or at 7:00 on a day with none.
        LaunchedEffect(day, instances != null) {
            val firstMs = timed.minOfOrNull { maxOf(it.beginMs, dayStart) }
            val hour = if (firstMs == null) 7f else ((firstMs - dayStart) / 3_600_000f - 1f).coerceAtLeast(0f)
            scroll.scrollTo((hour * hourPx).toInt())
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).testTag("cal_day_grid")) {
            val laneX = CalMetrics.HOUR_GUTTER
            val laneW = maxWidth - laneX - 8.dp
            Box(Modifier.fillMaxWidth().height(CalMetrics.HOUR * hours).drawBehind {
                for (h in 0..hours) drawRect(CalMetrics.RULE, topLeft = Offset(0f, h * hourPx), size = Size(size.width, 1.dp.toPx()))
            }) {
                for (h in 0 until hours) {
                    val at = EventRules.local(dayStart + h * 3_600_000L, zone)
                    BasicText(
                        CalText.time(at, is24h, locale),
                        Modifier.offset(x = CalMetrics.WEEK_LABEL_X.dp, y = CalMetrics.HOUR * h + 4.dp),
                        style = ShellType.caption.copy(color = CalMetrics.GREY), maxLines = 1,
                    )
                }
                layoutBlocks(timed, dayStart, dayEnd, minMs = 1_500_000L).forEach { b ->
                    val topDp = CalMetrics.HOUR * ((b.startMs - dayStart) / 3_600_000f)
                    val heightDp = CalMetrics.HOUR * ((b.endMs - b.startMs) / 3_600_000f)
                    val widthDp = laneW / b.columns
                    DayBlock(b.event, zone, locale, is24h, Modifier.offset(x = laneX + widthDp * b.column, y = topDp).size(widthDp - 2.dp, heightDp - 1.dp), tall = heightDp >= 36.dp, onEvent)
                }
            }
        }
    }
}

@Composable
private fun DayBlock(event: EventInstance, zone: ZoneId, locale: Locale, is24h: Boolean, modifier: Modifier, tall: Boolean, onEvent: (EventInstance) -> Unit) {
    val color = colorOf(event.color)
    val time = "${CalText.time(EventRules.local(event.beginMs, zone), is24h, locale)} – ${CalText.time(EventRules.local(event.endMs, zone), is24h, locale)}"
    val title = ShellType.caption.copy(fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    val small = ShellType.caption.copy(color = Color.White)
    Box(
        modifier.testTag("cal_event:${event.eventId}")
            .let { if (event.free) it.background(CalMetrics.PAGE).border(2.dp, color) else it.background(color) }
            .clickable(remember { MutableInteractionSource() }, indication = null) { onEvent(event) }
            .clipToBounds().padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (tall) {
            Column {
                BasicText(EventRules.shownTitle(event.title), Modifier.testTag("cal_event_title:${event.eventId}"), style = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(time, Modifier.testTag("cal_event_time:${event.eventId}"), style = small, maxLines = 1, overflow = TextOverflow.Clip)
            }
        } else {
            // A short block: the time and the title on its one line, the time first so it is never cut away.
            Row {
                BasicText(time, Modifier.testTag("cal_event_time:${event.eventId}"), style = small, maxLines = 1)
                BasicText(EventRules.shownTitle(event.title), Modifier.padding(start = 6.dp).testTag("cal_event_title:${event.eventId}"), style = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ============================================================================================ Week (K4)

/**
 * The portrait week view (K4.1–K4.4): two columns split at W/2 by four rows of 120 epx — seven day cells and, in the
 * eighth, a mini month. A cell is labelled "23 MON" in grey at x 10; its events are one line each in the calendar
 * colour's tint at a 19-epx pitch. A tap on a cell opens that day; a tap on a line, that event.
 */
@Composable
private fun WeekView(
    start: LocalDate, instances: List<EventInstance>?, today: LocalDate, zone: ZoneId, locale: Locale,
    onDay: (LocalDate) -> Unit, onEvent: (EventInstance) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(CalMetrics.PAGE).testTag("cal_week")
            // K1.2: a 1-epx black rule under the header in the week view.
            .drawBehind { drawRect(Color.Black, size = Size(size.width, 1.dp.toPx())) }.padding(top = 1.dp),
    ) {
        for (row in 0..3) {
            Row(Modifier.fillMaxWidth().height(CalMetrics.WEEK_CELL).drawBehind {
                // K1.4: 1-epx (80,80,80) rules between the cells.
                drawRect(CalMetrics.RULE, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
                drawRect(CalMetrics.RULE, topLeft = Offset(size.width / 2f - 0.5.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height))
            }) {
                for (col in 0..1) {
                    val index = row * 2 + col
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (index < 7) {
                            val day = start.plusDays(index.toLong())
                            WeekCell(day, remember(instances, day, zone) { eventsOf(instances.orEmpty(), day, zone) }, locale, onDay, onEvent)
                        } else {
                            MiniMonth(start, today, locale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCell(day: LocalDate, events: List<EventInstance>, locale: Locale, onDay: (LocalDate) -> Unit, onEvent: (EventInstance) -> Unit) {
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { onDay(day) }) {
        CapText(
            CalText.weekLabel(day, locale), CalMetrics.WEEK_LABEL_X, CalMetrics.WEEK_LABEL_CAP_TOP,
            ShellType.subtitle.copy(fontSize = 18.6.sp, color = CalMetrics.GREY), Modifier.testTag("cal_day:${CalText.iso(day)}"),
        )
        // Four lines fit under the label; with more events than that, the last line says how many are not shown.
        val lines = if (events.size <= WEEK_LINES) events else events.take(WEEK_LINES - 1)
        val line = ShellType.caption.copy(fontSize = 13.sp)
        lines.forEachIndexed { i, event ->
            val y = capTop(CalMetrics.WEEK_LABEL_CAP_TOP + CalMetrics.WEEK_FIRST_LINE + i * CalMetrics.WEEK_LINE_PITCH, 13f)
            Box(
                Modifier.offset(x = CalMetrics.WEEK_LABEL_X.dp, y = y).fillMaxWidth().padding(end = 14.dp).height(CalMetrics.WEEK_LINE_PITCH.dp)
                    .testTag("cal_event:${event.eventId}")
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onEvent(event) },
            ) {
                BasicText(
                    EventRules.shownTitle(event.title), Modifier.testTag("cal_event_title:${event.eventId}"),
                    style = line.copy(color = tintOf(event.color)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (events.size > WEEK_LINES) {
            val y = capTop(CalMetrics.WEEK_LABEL_CAP_TOP + CalMetrics.WEEK_FIRST_LINE + (WEEK_LINES - 1) * CalMetrics.WEEK_LINE_PITCH, 13f)
            BasicText(
                "+${events.size - (WEEK_LINES - 1)} more", Modifier.offset(x = CalMetrics.WEEK_LABEL_X.dp, y = y).testTag("cal_week_more:${CalText.iso(day)}"),
                style = line.copy(color = CalMetrics.GREY), maxLines = 1,
            )
        }
    }
}

private const val WEEK_LINES = 4

/** K4.4: the eighth cell — a seven-column month with two-letter day names, 14.1-epx rows and ≈ 10-epx digits; this week's dates in white. */
@Composable
private fun MiniMonth(weekStart: LocalDate, today: LocalDate, locale: Locale) {
    val accent = LocalShellColors.current.accent
    val first = weekStart.dayOfWeek
    val month = YearMonth.from(weekStart.plusDays(3))
    val grid = EventRules.monthGrid(month.atDay(1), first)
    BoxWithConstraints(Modifier.fillMaxSize().testTag("cal_mini_month")) {
        val column = (maxWidth.value - 2 * CalMetrics.WEEK_LABEL_X) / 7f
        val style = ShellType.caption.copy(fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
        for (i in 0..6) {
            BasicText(
                CalText.shortDay(first.plus(i.toLong()), locale).take(2),
                Modifier.offset(x = (CalMetrics.WEEK_LABEL_X + column * i).dp, y = 14.dp).width(column.dp), style = style.copy(color = CalMetrics.GREY), maxLines = 1,
            )
        }
        grid.forEachIndexed { n, day ->
            val inWeek = day >= weekStart && day < weekStart.plusDays(7)
            BasicText(
                day.dayOfMonth.toString(),
                Modifier.offset(x = (CalMetrics.WEEK_LABEL_X + column * (n % 7)).dp, y = (28.75f + 14.1f * (n / 7)).dp).width(column.dp),
                style = style.copy(color = when {
                    day == today -> accent
                    inWeek -> Color.White
                    YearMonth.from(day) != month -> CalMetrics.OTHER_MONTH
                    else -> CalMetrics.GREY
                }),
                maxLines = 1,
            )
        }
    }
}

// ============================================================================================ the month drop-down (K5)

/**
 * The month panel (K5.1–K5.3): from the header's bottom, x 5–355, 235 epx, a 1-epx (80,80,80) border on the page
 * colour, over the current view; day names and six date rows at a 34.25-epx pitch on the strip's columns; other-month
 * dates in (110,110,110); the selected date a dark-accent cell. It grows from its top edge in 200 ms ease-out, logged
 * as `[motion] cal_month_dropdown`. A tap on a date selects it and closes the panel; a swipe changes the month; a tap
 * outside, the header or Back closes it.
 */
@Composable
private fun MonthPanel(nav: CalendarNav, today: LocalDate, first: DayOfWeek, locale: Locale) {
    val accent = LocalShellColors.current.accent
    OverlayLayer(active = { nav.monthOpen }) {
        if (!nav.monthOpen) return@OverlayLayer
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = maxWidth.value / 360f
            Box(Modifier.offset(y = CalMetrics.HEADER).fillMaxSize().testTag("cal_month_scrim").pointerInput(Unit) {
                detectTapGestures { dismissOverlay { nav.monthOpen = false } }
            })
            var grow by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(Unit) { MotionClock.animate("cal_month_dropdown", CalMetrics.MONTH_MS, ClockMetrics.easeOut) { grow = it } }
            val month by rememberUpdatedState(nav.month)
            Box(
                Modifier.offset(x = CalMetrics.MONTH_X * scale, y = CalMetrics.HEADER).width(CalMetrics.MONTH_W * scale).height(CalMetrics.MONTH_H * grow)
                    .clipToBounds().testTag("cal_month_dropdown"),
            ) {
                Box(
                    Modifier.wrapContentHeight(Alignment.Top, unbounded = true).fillMaxWidth().requiredHeight(CalMetrics.MONTH_H)
                        .background(CalMetrics.PAGE).border(1.dp, CalMetrics.RULE)
                        .pointerInput(Unit) {
                            var dx = 0f
                            var dy = 0f
                            detectDragGestures(
                                onDragStart = { dx = 0f; dy = 0f },
                                onDragEnd = {
                                    val along = if (abs(dx) > abs(dy)) dx else dy
                                    if (abs(along) > 40.dp.toPx()) nav.month = month.plusMonths(if (along < 0) 1 else -1)
                                },
                            ) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
                        },
                ) {
                    val cellW = CalMetrics.COLUMN_PITCH * scale
                    val left = CalMetrics.MONTH_X.value * scale
                    val grid = remember(nav.month, first) { EventRules.monthGrid(nav.month.atDay(1), first) }
                    for (i in 0..6) {
                        BasicText(
                            CalText.shortDay(first.plus(i.toLong()), locale),
                            Modifier.offset(x = (CalMetrics.columnCentre(i) * scale - cellW / 2 - left).dp, y = capTop(CalMetrics.MONTH_NAMES_CAP_TOP, 15f)).width(cellW.dp),
                            style = ShellType.body.copy(color = CalMetrics.GREY, textAlign = TextAlign.Center), maxLines = 1,
                        )
                    }
                    grid.forEachIndexed { n, day ->
                        val isSelected = day == nav.selected
                        val cy = CalMetrics.MONTH_FIRST_ROW_CENTRE + (n / 7) * CalMetrics.MONTH_ROW_PITCH
                        Box(
                            Modifier.offset(x = (CalMetrics.columnCentre(n % 7) * scale - cellW / 2 - left).dp, y = (cy - CalMetrics.MONTH_ROW_PITCH / 2).dp)
                                .size(cellW.dp, CalMetrics.MONTH_ROW_PITCH.dp)
                                // K2.5 / K5.3: the selected date's cell in dark accent.
                                .background(if (isSelected) Color(accent.red * 0.56f, accent.green * 0.56f, accent.blue * 0.56f) else Color.Transparent)
                                .testTag("cal_month_cell:${CalText.iso(day)}").semantics { role = Role.Tab; this.selected = isSelected }
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    dismissOverlay { nav.monthOpen = false }
                                    nav.notice = null
                                    nav.select(day)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                day.dayOfMonth.toString(),
                                style = ShellType.body.copy(
                                    color = when {
                                        isSelected -> Color.White
                                        YearMonth.from(day) != nav.month -> CalMetrics.OTHER_MONTH
                                        day == today -> accent
                                        else -> Color.White
                                    },
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================================ the ≡ calendar pane (K6.4)

/** The order the lists show accounts in: the shell's own calendar first, then Birthdays, then every account by name. */
internal fun byAccount(calendars: List<CalendarInfo>): List<Pair<String, List<CalendarInfo>>> =
    calendars.groupBy { it.accountName }.toList().sortedWith(
        compareBy({ (name, list) -> if (list.any { it.isTessera }) 0 else if (list.any { it.isBirthdays }) 1 else 2 }, { it.first.lowercase() }),
    )

/**
 * The ≡ pane (K6.4, on U10's #1F1F1F chrome): every calendar under its account's header, a row of 48.1 epx with a
 * checkbox filled with the calendar's colour and its name at x 62. Un-ticking a calendar hides it in the Calendar
 * app's views — the shell's own list, never `Calendars.VISIBLE` (r3 D4): the tile, the pod, Tess and the reminders
 * still read every calendar.
 */
@Composable
private fun CalendarPane(nav: CalendarNav, model: CalendarModel, sync: SyncState) {
    OverlayLayer(active = { nav.paneOpen }) {
        if (!nav.paneOpen) return@OverlayLayer
        var collapsed by remember { mutableStateOf(setOf<String>()) }
        Box(
            Modifier.offset(y = CalMetrics.HEADER).fillMaxSize().padding(bottom = CalMetrics.HEADER).background(CalMetrics.PANE_FILL)
                .pointerInput(Unit) { detectTapGestures { } }.testTag("cal_pane"),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                byAccount(model.calendars).forEach { (account, calendars) ->
                    val isCollapsed = account in collapsed
                    AccountHeader(account, collapsed = isCollapsed) { collapsed = if (isCollapsed) collapsed - account else collapsed + account }
                    if (!isCollapsed) {
                        calendars.forEach { calendar ->
                            val shown = calendar.key !in sync.hidden
                            CalendarRow(calendar.shownName, colorOf(calendar.color), shown, "cal_calendar_row:${calendar.id}") {
                                model.store.update { SyncStateRules.setHidden(it, calendar.key, shown) }
                            }
                        }
                    }
                }
                if (model.calendars.isEmpty()) {
                    BasicText("No calendars", Modifier.padding(start = CalMetrics.PANE_ACCOUNT_X.dp, top = 16.dp).testTag("cal_pane_empty"), style = ShellType.body.copy(color = CalMetrics.GREY))
                }
            }
        }
    }
}
