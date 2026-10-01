package app.tileshell.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.calculator.DatePickerPanel
import app.tileshell.calculator.DatePickerRequest
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockFlyout
import app.tileshell.clock.ClockMetrics
import app.tileshell.clock.LocalIs24h
import app.tileshell.clock.LoopSpinner
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.components.OutlinedFieldMetrics
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

// ============================================================================================ notices (approximations, H20)

internal object CalNotices {
    const val CANNOT_SAVE = "Calendar can't save events. Allow it to add them to your Tessera calendar."
    const val CANNOT_SYNC = "Calendar can't sync events. Allow it to change your calendar."
    const val CANNOT_DELETE = "Calendar can't delete events. Allow it to change your calendar."
    const val NO_CALENDAR = "I don't have a calendar to add that to."
    const val CALENDAR_GONE = "That calendar is no longer on this phone"
    const val READ_ONLY = "That calendar is read-only now"
    const val CHOOSE = "Choose which calendars Sync may use"
    const val EVENT_GONE = "That event is no longer on this phone."

    fun recreated(calendar: String) = "The copy in $calendar was gone, so Sync made it again."
    fun stale(calendar: String) = "The copy is no longer in $calendar, so nothing was changed."

    fun of(outcome: CalendarSync.Outcome, calendar: String): String? = when (outcome) {
        CalendarSync.Outcome.Ok, CalendarSync.Outcome.Updated -> null
        CalendarSync.Outcome.Recreated -> recreated(calendar)
        CalendarSync.Outcome.CalendarGone -> CALENDAR_GONE
        CalendarSync.Outcome.ReadOnly -> READ_ONLY
        CalendarSync.Outcome.MappingStale -> stale(calendar)
        CalendarSync.Outcome.Refused -> "Sync isn't allowed for that calendar."
        is CalendarSync.Outcome.Failed -> "That event couldn't be synced."
    }
}

// ============================================================================================ the event page (U2: approximation, H15)

private data class EventData(val event: EventDetail, val reminders: List<Pair<Int, Int>>)

/**
 * An event's page (`cal_event_page:<id>`; r11/calendar.md U2's card form): the title, then When, Where, Calendar,
 * Repeat, Reminder and Notes as two-line rows. Only an event of the Tessera calendar has actions — Edit, Delete and
 * Sync (`cal_event_action:<edit|delete|sync>`); every other calendar's event is read-only (Q2 rule 1). A synced event
 * carries `cal_synced_marker:<id>` — "synced to <calendar>" — with its account beside it (r3 D5).
 */
@Composable
fun EventPage(nav: CalendarNav, model: CalendarModel, sync: SyncState, page: CalPage.Event, onGrant: (write: Boolean) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24h = LocalIs24h.current
    val zone = remember(model.changes) { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    var data by remember(page) { mutableStateOf<EventData?>(null) }
    var barMenu by remember(page) { mutableStateOf(BarMenu.NONE) }
    var offerGrant by remember(page) { mutableStateOf(false) }

    // Read again on every provider change: an edit shows at once, and an event that went away — its account's
    // calendar removed while the page was open — closes the page with a notice.
    LaunchedEffect(page, model.changes) {
        val read = withContext(Dispatchers.IO) { CalendarReads.event(context, page.eventId)?.let { EventData(it, CalendarReads.reminders(context, it.id)) } }
        if (read == null) {
            if (nav.page === page) {
                nav.pop()
                nav.notice = CalNotices.EVENT_GONE
            }
        } else {
            data = read
        }
    }

    val event = data?.event
    val calendar = event?.let { model.calendar(it.calendarId) }
    val local = calendar?.isTessera == true
    // A synced event is its series: an occurrence's page shows its master's marker.
    val masterId = event?.originalId ?: page.eventId
    val marker = if (local) CalendarSync.marker(sync, masterId, model.calendars) else null

    fun delete(scope_: EditScope?, both: Boolean) {
        if (!CalendarReads.canWrite(context)) {
            page.notice = CalNotices.CANNOT_DELETE
            offerGrant = true
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { CalendarEvents.delete(context, page.eventId, scope_, page.beginMs, both) }
            when (result) {
                is WriteResult.Ok -> if (nav.page === page) nav.pop()
                is WriteResult.Refused -> page.notice = when (result.why) {
                    CalendarWriteGuard.Refusal.MAPPING_STALE -> CalNotices.stale(marker?.calendarName ?: "that calendar")
                    CalendarWriteGuard.Refusal.READ_ONLY -> CalNotices.READ_ONLY
                    CalendarWriteGuard.Refusal.CALENDAR_GONE -> CalNotices.CALENDAR_GONE
                    CalendarWriteGuard.Refusal.NOT_ALLOWED -> "That event can't be deleted here."
                }
                is WriteResult.Failed -> page.notice = "That event couldn't be deleted."
            }
        }
    }

    fun askDelete(scope_: EditScope?) {
        if (marker != null && (scope_ == null || scope_ == EditScope.ALL)) {
            // "Delete here and from <calendar>" is never offered for a target that is no longer allowed (T16-12).
            nav.dialog = CalDialog.DeleteChoice(marker.calendarName, offerBoth = marker.targetAllowed) { both -> delete(scope_, both) }
        } else {
            delete(scope_, both = false)
        }
    }

    fun runSync(target: CalendarKey, name: String) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { CalendarSync.sync(context, masterId, target) }
            page.notice = CalNotices.of(outcome, name)
        }
    }

    Box(Modifier.fillMaxSize().testTag("cal_event_page:${page.eventId}")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CalMetrics.BAR_EXPANDED)) {
            page.notice?.let {
                CalNotice(it, Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp), onGrant = if (offerGrant) ({ onGrant(true) }) else null)
            }
            if (event != null) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    // The calendar's colour, as the K3.8 bar the views draw for this event.
                    Box(Modifier.size(CalMetrics.EVENT_BAR_W, 28.dp).background(colorOf(event.color ?: calendar?.color)))
                    BasicText(
                        EventRules.shownTitle(event.title),
                        Modifier.padding(start = 4.dp, end = 12.dp).testTag("cal_event_title:${page.eventId}"),
                        style = ShellType.subtitle.copy(color = Color.White),
                    )
                }
                DetailRow("When", whenText(event, page, zone, is24h, locale), "cal_event_time:${page.eventId}")
                event.location?.takeIf { it.isNotBlank() }?.let { DetailRow("Where", it, "cal_event_location") }
                DetailRow("Calendar", calendar?.let { if (it.shownName == it.accountName) it.shownName else "${it.shownName} · ${it.accountName}" } ?: "", "cal_event_calendar")
                if (marker != null) {
                    Row(Modifier.fillMaxWidth().padding(start = 13.dp, top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (marker.warning) BasicText(Glyph.WARNING, Modifier.padding(end = 6.dp).testTag("cal_synced_warning"), style = glyphStyle(16f, Color.White))
                        BasicText("synced to ${marker.calendarName}", Modifier.testTag("cal_synced_marker:${page.eventId}"), style = ShellType.body.copy(color = Color.White))
                        BasicText(
                            marker.accountName, Modifier.padding(start = 8.dp).testTag("cal_account:${marker.accountName}"),
                            style = ShellType.caption.copy(fontSize = 13.sp, color = CalMetrics.GREY), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (event.recurring) DetailRow("Repeat", EventRules.repeatOf(event.rrule).label, "cal_event_repeat")
                data?.reminders?.takeIf { it.isNotEmpty() }?.let { r -> DetailRow("Reminder", r.joinToString(", ") { EventRules.reminderLabel(it.first) }, "cal_event_reminder") }
                event.description?.takeIf { it.isNotBlank() }?.let { DetailRow("Notes", it, "cal_event_notes", maxLines = 40) }
            }
        }
        val buttons = if (!local) emptyList() else listOf(
            BarButton(Glyph.EDIT, "Edit", "cal_event_action:edit") {
                barMenu = BarMenu.NONE
                val reminders = data?.reminders.orEmpty()
                if (event.recurring) {
                    nav.dialog = CalDialog.Occurrence(delete = false) { picked ->
                        nav.push(CalPage.Editor(EditorState(CalendarEvents.draftOf(event, reminders, page.beginMs, page.endMs, picked, zone), "Edit event")))
                    }
                } else {
                    nav.push(CalPage.Editor(EditorState(CalendarEvents.draftOf(event, reminders, null, null, null, zone), "Edit event")))
                }
            },
            BarButton(Glyph.DELETE, "Delete", "cal_event_action:delete") {
                barMenu = BarMenu.NONE
                if (event.recurring) nav.dialog = CalDialog.Occurrence(delete = true) { picked -> askDelete(picked) } else askDelete(null)
            },
            BarButton(Glyph.SYNC, "Sync", "cal_event_action:sync") {
                barMenu = BarMenu.NONE
                val mapping = sync.mappings[masterId]
                val targets = CalendarSync.targets(model.calendars, sync)
                when {
                    !CalendarReads.canWrite(context) -> {
                        page.notice = CalNotices.CANNOT_SYNC
                        offerGrant = true
                    }
                    // Synced before: a later Sync pushes to the same calendar — or says it is gone.
                    mapping != null && (model.calendars.none { it.key == mapping.target } || mapping.target in sync.allowed) ->
                        runSync(mapping.target, marker?.calendarName ?: mapping.target.accountName)
                    targets.isEmpty() -> {
                        // T16-3: never a dead-end empty picker — straight to "Can sync to", and back to the picker from there.
                        Diagnostics.add("calendar", "sync event=$masterId: no calendar allowed -> can sync to")
                        nav.push(CalPage.CanSyncTo(masterId))
                    }
                    else -> nav.push(CalPage.SyncPicker(masterId))
                }
            },
        )
        CalAppBar(buttons, listOf(CalMenuEntry(Glyph.SETTINGS, "Settings", "cal_more:settings") { nav.push(CalPage.Settings) }), emptyList(), { barMenu }) { barMenu = it }
    }
}

private fun whenText(event: EventDetail, page: CalPage.Event, zone: ZoneId, is24h: Boolean, locale: Locale): String {
    val beginMs = if (event.recurring) page.beginMs ?: event.dtstart else event.dtstart
    val endMs = if (event.recurring) page.endMs ?: (beginMs + (EventRules.durationMs(event.duration) ?: 0L))
    else event.dtend ?: (event.dtstart + (EventRules.durationMs(event.duration) ?: 0L))
    if (event.allDay) {
        val days = EventRules.days(beginMs, endMs, true, zone)
        return if (days.start == days.endInclusive) "${CalText.date(days.start, locale)}, all day"
        else "${CalText.date(days.start, locale)} – ${CalText.date(days.endInclusive, locale)}, all day"
    }
    val begin = EventRules.local(beginMs, zone)
    val end = EventRules.local(endMs, zone)
    return if (begin.toLocalDate() == end.toLocalDate()) "${CalText.date(begin.toLocalDate(), locale)}, ${CalText.time(begin, is24h, locale)} – ${CalText.time(end, is24h, locale)}"
    else "${CalText.date(begin.toLocalDate(), locale)}, ${CalText.time(begin, is24h, locale)} – ${CalText.date(end.toLocalDate(), locale)}, ${CalText.time(end, is24h, locale)}"
}

/** r11/people.md P3.6's two-line row, which U2 proposes here: a 20-epx label over a ≈ 13-epx value, left 13 epx, a 65.5-epx pitch. */
@Composable
private fun DetailRow(label: String, value: String, tag: String, maxLines: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(start = 13.dp, end = 12.dp, top = 17.dp)) {
        BasicText(label, style = ShellType.subtitle.copy(color = Color.White), maxLines = 1)
        BasicText(
            value, Modifier.padding(top = 2.dp).testTag(tag),
            style = ShellType.caption.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Color(200, 200, 200)), maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================================================ the editor (U3: approximation, H15)

@Composable
private fun FieldLabel(text: String, accent: Boolean = false) {
    val color = if (accent) LocalShellColors.current.accent else Color.White
    // P4.5: the label's cap top 22.75 epx above its box.
    Box(Modifier.fillMaxWidth().height((12f + CalMetrics.FIELD_LABEL_TO_BOX).dp)) {
        CapText(text, CalMetrics.FIELD_X.value, 12f, ShellType.body.copy(color = color))
    }
}

/** P4.7: a 1-epx (103,103,103) rule from x 12 to W − 12, about 22 epx clear above and below. */
@Composable
private fun GroupRule() {
    Box(Modifier.fillMaxWidth().padding(horizontal = CalMetrics.FIELD_X).padding(top = 22.dp, bottom = 10.dp).height(1.dp).background(CalMetrics.GROUP_RULE))
}

/** A field that opens a picker or a list: the editor text box's own frame (P4.4) with its value, and ⌄ when it is a combo. */
@Composable
private fun PickBox(value: String, tag: String, modifier: Modifier = Modifier, combo: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    CalPress(
        modifier.height(OutlinedFieldMetrics.HEIGHT).background(OutlinedFieldMetrics.FILL).border(OutlinedFieldMetrics.BORDER, OutlinedFieldMetrics.BORDER_COLOR).testTag(tag),
        onClick = { if (enabled) onClick() },
    ) {
        BasicText(
            value, Modifier.align(Alignment.CenterStart).padding(start = OutlinedFieldMetrics.TEXT_INSET, end = if (combo) 28.dp else OutlinedFieldMetrics.TEXT_INSET),
            style = ShellType.body.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (combo) BasicText(Glyph.CHEVRON_DOWN, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), style = glyphStyle(12f, Color.White))
    }
}

/**
 * The editor (`cal_editor`; r11/calendar.md U3: People's editor form — 32-epx outlined fields, accent type-labels, group
 * rules): title, location, all day, start and end as a date and a time side by side, repeat, reminder, notes. Its
 * calendar field reads Tessera and opens nothing: every event made here goes into the shell's own calendar (Q2 rule
 * 2). Nothing reaches the provider until Save (`cal_editor_save`); Cancel (`cal_editor_cancel`) and Back discard.
 */
@Composable
fun EventEditor(nav: CalendarNav, model: CalendarModel, state: EditorState, onGrant: (write: Boolean) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24h = LocalIs24h.current
    val accent = LocalShellColors.current.accent
    val scope = rememberCoroutineScope()
    val draft = state.draft
    var barMenu by remember(state) { mutableStateOf(BarMenu.NONE) }
    // The keyboard's Done (or a hardware Enter) puts the keyboard away and leaves the field as it is; a tap on a picker
    // field, the all-day box, Save or Cancel ends the typing, so no picker and no app bar is ever left under a keyboard.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun setStart(date: LocalDate, time: LocalTime) {
        // The end keeps its distance from the start.
        val d = state.draft
        val length = ChronoUnit.MINUTES.between(LocalDateTime.of(d.startDate, d.startTime), LocalDateTime.of(d.endDate, d.endTime))
        val end = LocalDateTime.of(date, time).plusMinutes(length)
        state.draft = d.copy(startDate = date, startTime = time, endDate = if (d.allDay) date.plusDays(ChronoUnit.DAYS.between(d.startDate, d.endDate)) else end.toLocalDate(), endTime = end.toLocalTime())
    }

    fun save() {
        if (state.saving) return
        focus.clearFocus()
        state.saving = true
        scope.launch {
            val zone = ZoneId.systemDefault()
            val result = withContext(Dispatchers.IO) { CalendarEvents.save(context, state.draft, zone) { model.ensureLocal() } }
            state.saving = false
            when (result) {
                is CalendarEvents.SaveResult.Saved -> {
                    val d = state.draft
                    nav.pop()
                    // An occurrence edit made a new row (an exception, or a new series): the page under the editor showed
                    // the old one, so it closes too.
                    if (d.scope == EditScope.THIS || d.scope == EditScope.FOLLOWING) (nav.page as? CalPage.Event)?.let { nav.pop() }
                    if (d.isNew) nav.select(d.startDate)
                }
                is CalendarEvents.SaveResult.Invalid -> state.notice = EditorNotice(result.reason)
                CalendarEvents.SaveResult.NeedsWrite -> state.notice = EditorNotice(CalNotices.CANNOT_SAVE, offersGrant = true)
                CalendarEvents.SaveResult.NoCalendar -> state.notice = EditorNotice(CalNotices.NO_CALENDAR)
                is CalendarEvents.SaveResult.Failed -> state.notice = EditorNotice("That event couldn't be saved.")
            }
        }
    }

    Box(Modifier.fillMaxSize().testTag("cal_editor")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CalMetrics.BAR_EXPANDED),
        ) {
            PageTitle(state.titleText, "cal_editor_title")
            state.notice?.let { n ->
                CalNotice(n.text, Modifier.padding(start = CalMetrics.FIELD_X, end = CalMetrics.FIELD_X, bottom = 4.dp), onGrant = if (n.offersGrant) ({ onGrant(true) }) else null)
            }
            val field = Modifier.padding(horizontal = CalMetrics.FIELD_X).fillMaxWidth()
            FieldLabel("Title")
            OutlinedField(draft.title, { state.draft = state.draft.copy(title = it) }, "cal_editor_field:title", field, maxLength = CalendarIntents.MAX_TITLE, onImeAction = { keyboard?.hide() })
            FieldLabel("Location")
            OutlinedField(draft.location, { state.draft = state.draft.copy(location = it) }, "cal_editor_field:location", field, maxLength = CalendarIntents.MAX_LOCATION, onImeAction = { keyboard?.hide() })
            GroupRule()

            CalPress(
                Modifier.fillMaxWidth().height(44.dp).testTag("cal_editor_field:all_day").semantics { role = Role.Checkbox; toggleableState = ToggleableState(draft.allDay) },
                onClick = {
                    focus.clearFocus()
                    state.draft = state.draft.let { it.copy(allDay = !it.allDay, endDate = if (it.endDate < it.startDate) it.startDate else it.endDate) }
                },
            ) {
                ColorCheckbox(draft.allDay, accent, Modifier.align(Alignment.CenterStart).offset(x = CalMetrics.FIELD_X))
                BasicText("All day", Modifier.align(Alignment.CenterStart).offset(x = 44.dp), style = ShellType.body.copy(color = Color.White))
            }
            FieldLabel("Start")
            Row(field) {
                PickBox(CalText.date(draft.startDate, locale), "cal_editor_field:start_date", Modifier.weight(1.4f)) {
                    focus.clearFocus()
                    nav.dialog = CalDialog.DatePick(state.draft.startDate) { setStart(it, state.draft.startTime) }
                }
                if (!draft.allDay) {
                    Spacer(Modifier.width(12.dp))
                    PickBox(CalText.time(LocalDateTime.of(draft.startDate, draft.startTime), is24h, locale), "cal_editor_field:start_time", Modifier.weight(1f)) {
                        focus.clearFocus()
                        nav.dialog = CalDialog.TimePick(state.draft.startTime) { setStart(state.draft.startDate, it) }
                    }
                }
            }
            FieldLabel("End")
            Row(field) {
                PickBox(CalText.date(draft.endDate, locale), "cal_editor_field:end_date", Modifier.weight(1.4f)) {
                    focus.clearFocus()
                    nav.dialog = CalDialog.DatePick(state.draft.endDate) { state.draft = state.draft.copy(endDate = it) }
                }
                if (!draft.allDay) {
                    Spacer(Modifier.width(12.dp))
                    PickBox(CalText.time(LocalDateTime.of(draft.endDate, draft.endTime), is24h, locale), "cal_editor_field:end_time", Modifier.weight(1f)) {
                        focus.clearFocus()
                        nav.dialog = CalDialog.TimePick(state.draft.endTime) { state.draft = state.draft.copy(endTime = it) }
                    }
                }
            }
            GroupRule()

            if (draft.canRepeat) {
                FieldLabel("Repeat", accent = true)
                PickBox(draft.repeat.label, "cal_editor_field:repeat", field, combo = true) {
                    focus.clearFocus()
                    nav.dialog = CalDialog.Options("cal_editor_option", Repeat.entries.map { it.id to it.label }, state.draft.repeat.id, 96f) { id ->
                        state.draft = state.draft.copy(repeat = Repeat.entries.first { it.id == id })
                    }
                }
            }
            FieldLabel("Reminder", accent = true)
            PickBox(EventRules.reminderLabel(draft.reminder), "cal_editor_field:reminder", field, combo = true) {
                // What the event came with stays a choice, whatever its minutes.
                focus.clearFocus()
                val choices = (EventRules.REMINDER_CHOICES + state.draft.loadedReminder).distinct().sortedBy { it ?: -1 }
                nav.dialog = CalDialog.Options("cal_editor_option", choices.map { (it?.toString() ?: "none") to EventRules.reminderLabel(it) }, state.draft.reminder?.toString() ?: "none", 48f) { id ->
                    state.draft = state.draft.copy(reminder = id.toIntOrNull())
                }
            }
            FieldLabel("Calendar")
            // Always the shell's own calendar: no picker offers another (Q2 rule 2).
            PickBox(app.tileshell.feeds.LocalCalendar.ACCOUNT_NAME, "cal_editor_field:calendar", field, enabled = false) { }
            GroupRule()

            FieldLabel("Notes")
            OutlinedField(
                draft.notes, { state.draft = state.draft.copy(notes = it) }, "cal_editor_field:notes", field,
                height = 96.dp, maxLength = CalendarIntents.MAX_NOTES, singleLine = false,
            )
            Spacer(Modifier.height(24.dp))
        }
        val buttons = listOf(
            BarButton(Glyph.SAVE, "Save", "cal_editor_save", enabled = !state.saving) { barMenu = BarMenu.NONE; save() },
            BarButton(Glyph.DISMISS, "Cancel", "cal_editor_cancel") { barMenu = BarMenu.NONE; focus.clearFocus(); nav.pop() },
        )
        CalAppBar(buttons, listOf(CalMenuEntry(Glyph.SETTINGS, "Settings", "cal_more:settings") { nav.push(CalPage.Settings) }), emptyList(), { barMenu }) { barMenu = it }
    }
}

// ============================================================================================ Sync's pages (P4 designs, H14)

/** A leaf page: its caps title, its body, and the app bar with "…" only. */
@Composable
private fun LeafPage(nav: CalendarNav, tag: String, title: String, settingsInMenu: Boolean = true, body: @Composable () -> Unit) {
    var barMenu by remember { mutableStateOf(BarMenu.NONE) }
    Box(Modifier.fillMaxSize().testTag(tag)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = CalMetrics.BAR_EXPANDED)) {
            PageTitle(title, "${tag}_title")
            body()
        }
        val more = if (settingsInMenu) listOf(CalMenuEntry(Glyph.SETTINGS, "Settings", "cal_more:settings") { nav.push(CalPage.Settings) }) else emptyList()
        CalAppBar(emptyList(), more, emptyList(), { barMenu }) { barMenu = it }
    }
}

/**
 * The Sync picker (`cal_sync`): the calendars the user allowed on "Can sync to", under their account's name
 * (`cal_account:<name>`; r3 D5), each a `cal_sync_target:<id>`. A tap is the Sync: the event is copied there and the
 * picker closes onto the event's page.
 */
@Composable
fun SyncPickerPage(nav: CalendarNav, model: CalendarModel, sync: SyncState, page: CalPage.SyncPicker) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targets = CalendarSync.targets(model.calendars, sync)
    LeafPage(nav, "cal_sync", "Sync to") {
        if (targets.isEmpty()) {
            CalNotice(CalNotices.CHOOSE, Modifier.padding(start = CalMetrics.PANE_ACCOUNT_X.dp, end = 12.dp, bottom = 8.dp))
            CalPress(Modifier.fillMaxWidth().height(CalMetrics.PANE_ROW).testTag("cal_sync_open_can_sync"), onClick = { nav.pop(); nav.push(CalPage.CanSyncTo(page.eventId)) }) {
                BasicText("Can sync to", Modifier.align(Alignment.CenterStart).offset(x = CalMetrics.PANE_ACCOUNT_X.dp), style = ShellType.body.copy(color = LocalShellColors.current.accent))
            }
        }
        byAccount(targets).forEach { (account, calendars) ->
            AccountHeader(account)
            calendars.forEach { calendar ->
                CalendarRow(calendar.shownName, colorOf(calendar.color), null, "cal_sync_target:${calendar.id}") {
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { CalendarSync.sync(context, page.eventId, calendar.key) }
                        if (nav.page === page) nav.pop()
                        (nav.page as? CalPage.Event)?.notice = CalNotices.of(outcome, calendar.shownName)
                    }
                }
            }
        }
    }
}

/**
 * "Can sync to" (`cal_can_sync`; Q2 rule 4, r3 D5): the non-LOCAL calendars the phone may write, grouped under their
 * account's name, each a checkbox row `cal_settings_can_sync:<id>` in the ≡ pane's form — every one un-ticked until the
 * user ticks it. A calendar never ticked here can never be written, even by a mis-tap; Tessera, Birthdays and a
 * calendar the phone may only read are never listed.
 */
@Composable
fun CanSyncToPage(nav: CalendarNav, model: CalendarModel, sync: SyncState, page: CalPage.CanSyncTo) {
    val candidates = CalendarSync.candidates(model.calendars)
    LeafPage(nav, "cal_can_sync", "Can sync to", settingsInMenu = false) {
        CalNotice(CalNotices.CHOOSE, Modifier.padding(start = CalMetrics.PANE_ACCOUNT_X.dp, end = 12.dp, bottom = 8.dp))
        if (candidates.isEmpty()) {
            BasicText(
                "No calendar on this phone can be synced to.", Modifier.padding(start = CalMetrics.PANE_ACCOUNT_X.dp, top = 8.dp).testTag("cal_can_sync_empty"),
                style = ShellType.body.copy(color = CalMetrics.GREY),
            )
        }
        byAccount(candidates).forEach { (account, calendars) ->
            AccountHeader(account)
            calendars.forEach { calendar ->
                val allowed = calendar.key in sync.allowed
                CalendarRow(calendar.shownName, colorOf(calendar.color), allowed, "cal_settings_can_sync:${calendar.id}") {
                    model.store.update { SyncStateRules.setAllowed(it, calendar.key, !allowed) }
                }
            }
        }
    }
}

/**
 * Calendar settings (`cal_settings`; r11/calendar.md U6 / U7: no capture — approximation, H6): the first day of the
 * week, which follows the locale unless set here, and "Can sync to".
 */
@Composable
fun CalendarSettingsPage(nav: CalendarNav, model: CalendarModel, sync: SyncState) {
    val locale = LocalConfiguration.current.locales[0]
    fun dayName(d: DayOfWeek) = d.getDisplayName(java.time.format.TextStyle.FULL, locale)
    val localeDay = EventRules.firstDayOfWeek(null, locale)
    val options = listOf("locale" to "Follow the phone's region (${dayName(localeDay)})") + DayOfWeek.entries.map { it.value.toString() to dayName(it) }
    LeafPage(nav, "cal_settings", "Settings", settingsInMenu = false) {
        FieldLabel("First day of the week", accent = true)
        PickBox(
            options.first { it.first == (sync.firstDayOfWeek?.toString() ?: "locale") }.second, "cal_settings_first_day",
            Modifier.padding(horizontal = CalMetrics.FIELD_X).fillMaxWidth(), combo = true,
        ) {
            nav.dialog = CalDialog.Options("cal_settings_option", options, sync.firstDayOfWeek?.toString() ?: "locale", 64f) { id ->
                model.store.update { it.copy(firstDayOfWeek = id.toIntOrNull()) }
            }
        }
        GroupRule()
        CalPress(Modifier.fillMaxWidth().height(CalMetrics.PANE_ROW).testTag("cal_settings_open_can_sync"), onClick = { nav.push(CalPage.CanSyncTo(null)) }) {
            BasicText("Can sync to", Modifier.align(Alignment.CenterStart).offset(x = CalMetrics.FIELD_X), style = ShellType.body.copy(color = Color.White))
            BasicText(Glyph.CHEVRON_RIGHT, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), style = glyphStyle(14f, Color.White))
        }
        val allowed = CalendarSync.targets(model.calendars, sync)
        BasicText(
            if (allowed.isEmpty()) "Sync may not use any calendar yet." else "Sync may use: " + allowed.joinToString(", ") { it.shownName },
            Modifier.padding(start = CalMetrics.FIELD_X, end = 12.dp).testTag("cal_settings_can_sync_summary"),
            style = ShellType.caption.copy(fontSize = 13.sp, color = CalMetrics.GREY),
        )
    }
}

// ============================================================================================ prompts and pickers

/** R7 §1.3.9's top-anchored dialog, as the recorder draws it: fill (74,74,74), the page dimmed to (2,2,2), bordered buttons. */
@Composable
private fun CalDialogBox(tag: String, title: String, buttons: List<Triple<String, String, () -> Unit>>, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF020202)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF4A4A4A)).padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp).testTag(tag)) {
            BasicText(title, Modifier.testTag("${tag}_title"), style = ShellType.subtitle.copy(color = Color.White))
            Spacer(Modifier.height(16.dp))
            (buttons + Triple("Cancel", "${tag}_cancel", onCancel)).forEach { (label, buttonTag, onClick) ->
                CalPress(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(34.dp).border(1.dp, Color.White.copy(alpha = 0.7f)).testTag(buttonTag), onClick = onClick) {
                    BasicText(label, Modifier.fillMaxWidth().align(Alignment.Center), style = ShellType.body.copy(color = Color.White, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The time picker: the Alarms & Clock editor's loop spinner (r11/clock.md 3.4–3.7) — hours and minutes, with AM / PM
 * in the 12-hour form — over the page, with ✓ (`cal_time_ok`) and ✕ (`cal_time_cancel`). The columns are
 * `cal_time_spinner:<hour|minute|ampm>`, each reading its selected value.
 */
@Composable
private fun TimePickerPanel(initial: LocalTime, onPick: (LocalTime) -> Unit, onDone: () -> Unit) {
    val is24h = LocalIs24h.current
    val locale = LocalConfiguration.current.locales[0]
    val accent = LocalShellColors.current.accent
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    val frame = ClockMetrics.SPINNER_ROW * 5.97f
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }.testTag("cal_time_scrim")) {
        Column(Modifier.offset(y = 48.dp).fillMaxWidth().background(CalMetrics.PANE_FILL).testTag("cal_time_picker")) {
            Box(Modifier.fillMaxWidth().height(frame)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(ClockMetrics.SPINNER_ROW).background(accent.copy(alpha = 0.6f)))
                val minutes = remember { (0..59).map { "%02d".format(it) } }
                Row(Modifier.fillMaxSize()) {
                    if (is24h) {
                        val hours = remember { (0..23).map { "%02d".format(it) } }
                        LoopSpinner(hours, hour, 5.97f, "cal_time_spinner:hour", Modifier.weight(1f)) { hour = it }
                        LoopSpinner(minutes, minute, 5.97f, "cal_time_spinner:minute", Modifier.weight(1f)) { minute = it }
                    } else {
                        val hours = remember { (1..12).map { it.toString() } }
                        val ampm = remember(locale) { DateFormatSymbols.getInstance(locale).amPmStrings.take(2) }
                        val h12 = if (hour % 12 == 0) 12 else hour % 12
                        val pm = hour >= 12
                        LoopSpinner(hours, h12 - 1, 5.97f, "cal_time_spinner:hour", Modifier.weight(1f)) { i -> hour = (i + 1) % 12 + if (pm) 12 else 0 }
                        LoopSpinner(minutes, minute, 5.97f, "cal_time_spinner:minute", Modifier.weight(1f)) { minute = it }
                        LoopSpinner(ampm, if (pm) 1 else 0, 5.97f, "cal_time_spinner:ampm", Modifier.weight(1f)) { i -> hour = hour % 12 + if (i == 1) 12 else 0 }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(48.dp).background(Color.Black)) {
                Row(Modifier.align(Alignment.CenterEnd)) {
                    CalPress(Modifier.size(48.dp).testTag("cal_time_ok"), onClick = { onPick(LocalTime.of(hour, minute)); onDone() }) {
                        BasicText(Glyph.CHECKMARK, Modifier.align(Alignment.Center), style = glyphStyle(20f, Color.White))
                    }
                    Spacer(Modifier.width(20.dp))
                    CalPress(Modifier.size(48.dp).testTag("cal_time_cancel"), onClick = onDone) {
                        BasicText(Glyph.DISMISS, Modifier.align(Alignment.Center), style = glyphStyle(20f, Color.White))
                    }
                }
            }
        }
    }
}

/** Whatever prompt or picker is open over the page. Back closes it (`CalendarNav.back`). */
@Composable
fun BoxScope.CalendarDialogs(nav: CalendarNav) {
    val close = { dismissOverlay { nav.dialog = null } }
    when (val d = nav.dialog) {
        null -> Unit
        is CalDialog.Occurrence -> CalDialogBox(
            "cal_occurrence", if (d.delete) "Delete which events?" else "Edit which events?",
            EditScope.entries.map { s -> Triple(s.label, "cal_occurrence:${s.id}") { close(); d.onPick(s) } }, close,
        )
        is CalDialog.DeleteChoice -> CalDialogBox(
            "cal_delete", "Delete this event?",
            listOfNotNull(
                Triple("Delete here", "cal_delete_choice:here") { close(); d.onPick(false) },
                if (d.offerBoth) Triple("Delete here and from ${d.calendarName}", "cal_delete_choice:both") { close(); d.onPick(true) } else null,
            ),
            close,
        )
        is CalDialog.DatePick -> BoxWithConstraints(Modifier.fillMaxSize()) {
            DatePickerPanel(DatePickerRequest.Date(d.initial) { d.onPick(it) }, maxWidth.value) { close() }
        }
        is CalDialog.TimePick -> TimePickerPanel(d.initial, d.onPick) { close() }
        is CalDialog.Options -> ClockFlyout(
            x = CalMetrics.FIELD_X, top = d.anchorTop.dp, width = 336.dp, height = ClockMetrics.MENU_ROW * d.options.size + 16.dp,
            tag = "${d.tag}s", onDismiss = { close() }, bottomInset = 8.dp,
        ) {
            Box(Modifier.height(8.dp))
            d.options.forEach { (id, label) ->
                CalPress(
                    Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).testTag("${d.tag}:$id").semantics { role = Role.Tab; selected = id == d.selected },
                    onClick = { close(); d.onPick(id) },
                ) {
                    BasicText(label, Modifier.align(Alignment.CenterStart).offset(x = 11.7.dp), style = ShellType.body.copy(color = Color.White), maxLines = 1)
                }
            }
        }
    }
}
