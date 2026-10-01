package app.tileshell.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import app.tileshell.bars.BarMetrics
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** W10M's three views (r11/calendar.md K6.1); the month is the header's drop-down, not a view (K5, T16-13). */
enum class ViewMode(val id: String, val label: String) {
    AGENDA("agenda", "Agenda"),
    DAY("day", "Day"),
    WEEK("week", "Week"),
}

/** What an editor page holds while it is open; nothing of it reaches the provider until Save. */
class EditorState(initial: EventDraft, val titleText: String) {
    var draft by mutableStateOf(initial)
    var notice by mutableStateOf<EditorNotice?>(null)
    var saving by mutableStateOf(false)
}

/** The editor's notice: its text, and whether it offers WRITE_CALENDAR in place (r3 D7). */
data class EditorNotice(val text: String, val offersGrant: Boolean = false)

/** A page over the views. */
sealed interface CalPage {
    /** An event's page; [beginMs] / [endMs] name the occurrence of a repeating event. */
    class Event(val eventId: Long, val beginMs: Long?, val endMs: Long?) : CalPage {
        var notice by mutableStateOf<String?>(null)
    }
    class Editor(val state: EditorState) : CalPage
    class SyncPicker(val eventId: Long) : CalPage
    /** [forEvent]: the event whose first Sync routed here because nothing was allowed (T16-3); Back returns to its picker. */
    class CanSyncTo(val forEvent: Long?) : CalPage
    data object Settings : CalPage
}

/** A prompt or a picker over a page; Back closes it first. */
sealed interface CalDialog {
    /** "This occurrence / this and following / all" (r11/calendar.md U4), before an edit or a delete of a repeating event. */
    data class Occurrence(val delete: Boolean, val onPick: (EditScope) -> Unit) : CalDialog
    /** A synced event's delete: "here" (the default) or "here and from <calendar>" — the second only while its target is still allowed. */
    data class DeleteChoice(val calendarName: String, val offerBoth: Boolean, val onPick: (both: Boolean) -> Unit) : CalDialog
    data class DatePick(val initial: LocalDate, val onPick: (LocalDate) -> Unit) : CalDialog
    data class TimePick(val initial: java.time.LocalTime, val onPick: (java.time.LocalTime) -> Unit) : CalDialog
    data class Options(val tag: String, val options: List<Pair<String, String>>, val selected: String, val anchorTop: Float, val onPick: (String) -> Unit) : CalDialog
}

/**
 * The Calendar app's navigation, held above the composition so an intent can route while the activity is already
 * running (onNewIntent), and so Back unwinds the innermost thing first.
 */
class CalendarNav {
    /** What the last intent asked for; [routeToken] changes on every intent, so the same route twice is two opens. */
    var route by mutableStateOf<CalendarRoute>(CalendarRoute.Open(null))
        private set
    var routeToken by mutableIntStateOf(0)
        private set

    var mode by mutableStateOf(ViewMode.AGENDA)
    var selected by mutableStateOf(LocalDate.now())
    /** The month the open month panel shows. */
    var month by mutableStateOf(YearMonth.now())
    var monthOpen by mutableStateOf(false)
    var paneOpen by mutableStateOf(false)
    var barMenu by mutableStateOf(BarMenu.NONE)
    /** The views' own notice (an event that went away under its open page, a save that could not be made). */
    var notice by mutableStateOf<String?>(null)
    val pages = mutableStateListOf<CalPage>()
    var dialog by mutableStateOf<CalDialog?>(null)

    val page: CalPage? get() = pages.lastOrNull()

    fun open(route: CalendarRoute) {
        this.route = route
        routeToken++
    }

    fun push(page: CalPage) {
        closeOverlays()
        pages.add(page)
    }

    fun pop() {
        dialog = null
        if (pages.isNotEmpty()) pages.removeAt(pages.lastIndex)
    }

    fun closeOverlays() {
        dialog = null
        monthOpen = false
        paneOpen = false
        barMenu = BarMenu.NONE
    }

    fun select(day: LocalDate) {
        selected = day
        month = YearMonth.from(day)
    }

    fun showView(mode: ViewMode) {
        closeOverlays()
        pages.clear()
        this.mode = mode
        notice = null
    }

    /** Back: the innermost thing first. False when there was nothing left to unwind (the activity finishes). */
    fun back(): Boolean {
        when {
            dialog != null -> dialog = null
            barMenu != BarMenu.NONE -> barMenu = BarMenu.NONE
            pages.isNotEmpty() -> {
                val top = pages.removeAt(pages.lastIndex)
                // T16-3: the first Sync went straight to "Can sync to"; coming back lands on the Sync picker — never a
                // dead-end empty one, so only once something is allowed.
                if (top is CalPage.CanSyncTo && top.forEvent != null && canSyncAllowed()) pages.add(CalPage.SyncPicker(top.forEvent))
            }
            monthOpen -> monthOpen = false
            paneOpen -> paneOpen = false
            else -> return false
        }
        return true
    }

    /** Set by the app: whether "Can sync to" holds a ticked calendar that still lists. */
    var canSyncAllowed: () -> Boolean = { false }
}

/** The page behind every Calendar view (r11/calendar.md K1.3: #1A1A1A, with the status and nav bars black). */
val CalendarPageBackground = CalMetrics.PAGE

/**
 * Every Calendar page hides Samsung's bars and draws the W10M status bar and the Back / Windows / Search nav bar
 * (phase 01's bar rule); Back is Back for the page, Windows goes Home.
 *
 * @param clock bumped by the activity when the date, the time, the zone or the 12 / 24-hour setting changes
 * @param onGrant asks for a calendar permission in place (READ for the views, WRITE for a save or a Sync)
 */
@Composable
fun CalendarApp(nav: CalendarNav, model: CalendarModel, clock: Int, onBack: () -> Unit, onWindows: () -> Unit, onGrant: (write: Boolean) -> Unit) {
    val context = LocalContext.current
    val sync by model.store.state.collectAsState()
    nav.canSyncAllowed = { CalendarSync.targets(model.calendars, model.store.current).isNotEmpty() }

    // What the last intent asked for (build task 2's routes): applied once per intent.
    LaunchedEffect(nav.routeToken) { applyRoute(nav, context, clock) }

    // While a permission is missing the app watches for it, so a grant made anywhere — in place, in Settings, by
    // `pm grant` — loads the views with no restart (E18).
    LaunchedEffect(model.canRead, model.canWrite) {
        while (!model.canRead || !model.canWrite) {
            delay(1000)
            model.checkPermissions("permission change")
        }
    }

    // While a keyboard is up the page ends at its top edge, so the app bar (Save) rides above it; the drawn nav bar
    // stays where it is, under the keyboard.
    val keyboard = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        W10mStatusBar()
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = (keyboard - BarMetrics.NAV_EPX.dp).coerceAtLeast(0.dp)).background(CalendarPageBackground)) {
            when (val page = nav.page) {
                null -> CalendarViews(nav, model, sync, clock, onGrant)
                is CalPage.Event -> EventPage(nav, model, sync, page, clock, onGrant)
                is CalPage.Editor -> EventEditor(nav, model, page.state, onGrant)
                is CalPage.SyncPicker -> SyncPickerPage(nav, model, sync, page)
                is CalPage.CanSyncTo -> CanSyncToPage(nav, model, sync, page)
                CalPage.Settings -> CalendarSettingsPage(nav, model, sync)
            }
            CalendarDialogs(nav)
        }
        W10mNavBar(onBack = onBack, onWindows = onWindows)
    }
}

/**
 * The routes (task 2's `CalendarIntents`; Decisions "Trust" (c)): Open — today, or the shortcut's page; Time — that
 * day; Event — its page; Edit — the editor only for an event of the Tessera calendar, any other event's page
 * read-only; Insert — the editor filled in, nothing saved until Save, into Tessera whatever the intent carried.
 */
private suspend fun applyRoute(nav: CalendarNav, context: android.content.Context, clock: Int) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    when (val route = nav.route) {
        is CalendarRoute.Open -> {
            nav.select(today)
            when (route.page) {
                null -> nav.showView(nav.mode)
                CalendarShortcut.AGENDA -> nav.showView(ViewMode.AGENDA)
                CalendarShortcut.DAY -> nav.showView(ViewMode.DAY)
                CalendarShortcut.MONTH -> {
                    // W10M had no Month page: Agenda, with the month drop-down open (T16-13).
                    nav.showView(ViewMode.AGENDA)
                    nav.monthOpen = true
                }
                CalendarShortcut.NEW_EVENT -> {
                    nav.showView(nav.mode)
                    nav.push(CalPage.Editor(EditorState(CalendarEvents.newDraft(today, LocalDateTime.now(zone), null, zone), "New event")))
                }
            }
        }
        is CalendarRoute.Time -> {
            nav.select(EventRules.local(route.millis, zone).toLocalDate())
            nav.showView(ViewMode.DAY)
        }
        is CalendarRoute.Event -> {
            nav.showView(nav.mode)
            // The occurrence times are the caller's: taken only when the provider holds that occurrence of that event
            // (F12). Otherwise the page opens with none, as a VIEW without extras does.
            val occurrence = route.beginMs?.let { begin ->
                CalendarIntents.occurrence(route, withContext(Dispatchers.IO) { CalendarReads.instances(context, begin, begin + 1) })
            }
            occurrence?.let { nav.select(EventRules.local(it.beginMs, zone).toLocalDate()) }
            nav.push(CalPage.Event(route.id, occurrence?.beginMs, occurrence?.endMs))
        }
        is CalendarRoute.Edit -> {
            nav.showView(nav.mode)
            val (event, calendar, reminders) = withContext(Dispatchers.IO) {
                val e = CalendarReads.event(context, route.id)
                Triple(e, e?.let { CalendarReads.calendar(context, it.calendarId) }, e?.let { CalendarReads.reminders(context, it.id) }.orEmpty())
            }
            if (event == null) {
                nav.notice = "That event isn't on this phone."
            } else if (calendar?.isTessera == true) {
                // The intent names the event row, so a repeating event is edited as a whole ("all").
                nav.push(CalPage.Event(event.id, null, null))
                nav.push(CalPage.Editor(EditorState(CalendarEvents.draftOf(event, reminders, null, null, if (event.recurring) EditScope.ALL else null, zone), "Edit event")))
            } else {
                // Any other calendar's event opens read-only (rule 1; E22).
                Diagnostics.add("calendar", "edit ${route.id}: not a Tessera event, opened read-only")
                nav.push(CalPage.Event(event.id, null, null))
            }
        }
        is CalendarRoute.Insert -> {
            nav.showView(nav.mode)
            val day = route.prefill.beginMs?.let { EventRules.local(it, if (route.prefill.allDay) EventRules.UTC else zone).toLocalDate() } ?: today
            nav.select(day)
            nav.push(CalPage.Editor(EditorState(CalendarEvents.newDraft(day, LocalDateTime.now(zone), route.prefill, zone), "New event")))
        }
    }
}
