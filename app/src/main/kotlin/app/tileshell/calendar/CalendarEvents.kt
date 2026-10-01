package app.tileshell.calendar

import android.content.Context
import android.provider.CalendarContract
import app.tileshell.calendar.CalendarWriteGuard.Path
import app.tileshell.feeds.LocalCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The editor's and the event page's writes (build task 3, "Event writes on Tessera only"): create, edit, delete, and
 * the three occurrence edits of a repeating event. Every write goes to the Tessera calendar through [CalendarWrites];
 * there is no calendar argument anywhere here, because no page offers another calendar (Q2 rule 2).
 */
object CalendarEvents {
    sealed interface SaveResult {
        data class Saved(val eventId: Long) : SaveResult
        data class Invalid(val reason: String) : SaveResult
        /** WRITE_CALENDAR is not held: the notice offers the grant in place (r3 D7). */
        data object NeedsWrite : SaveResult
        /** No Tessera calendar can be found or made: "I don't have a calendar to add that to." (T16-2 line 3). */
        data object NoCalendar : SaveResult
        data class Failed(val error: String) : SaveResult
    }

    /** A new event's draft: [day] at the next full hour, one hour long, or what an INSERT intent suggested. */
    fun newDraft(day: LocalDate, now: LocalDateTime, prefill: EventPrefill?, zone: ZoneId): EventDraft {
        val begin = prefill?.beginMs
        if (prefill != null && prefill.allDay) {
            val first = begin?.let { EventRules.local(it, EventRules.UTC).toLocalDate() } ?: day
            // An all-day INSERT's end is exclusive (the midnight after the last day).
            val last = prefill.endMs?.let { EventRules.local(it - 1, EventRules.UTC).toLocalDate() }?.takeIf { it >= first } ?: first
            return EventDraft(
                title = prefill.title.orEmpty(), location = prefill.location.orEmpty(), notes = prefill.notes.orEmpty(), allDay = true,
                startDate = first, startTime = LocalTime.of(9, 0), endDate = last, endTime = LocalTime.of(10, 0),
            )
        }
        val start = begin?.let { EventRules.local(it, zone) }
            ?: LocalDateTime.of(day, LocalTime.of(now.hour, 0)).plusHours(1).let { if (it.toLocalDate() != day) LocalDateTime.of(day, LocalTime.of(23, 0)) else it }
        val end = prefill?.endMs?.let { EventRules.local(it, zone) } ?: start.plusHours(1)
        return EventDraft(
            title = prefill?.title.orEmpty(), location = prefill?.location.orEmpty(), notes = prefill?.notes.orEmpty(),
            startDate = start.toLocalDate(), startTime = start.toLocalTime(), endDate = end.toLocalDate(), endTime = end.toLocalTime(),
        )
    }

    /**
     * An existing event's draft. For a repeating event [instanceBeginMs] / [instanceEndMs] name the occurrence the page
     * showed, and [scope] what the edit applies to.
     */
    fun draftOf(event: EventDetail, reminders: List<Pair<Int, Int>>, instanceBeginMs: Long?, instanceEndMs: Long?, scope: EditScope?, zone: ZoneId): EventDraft {
        val startMs = if (event.recurring) instanceBeginMs ?: event.dtstart else event.dtstart
        val endMs = if (event.recurring) instanceEndMs ?: (startMs + (EventRules.durationMs(event.duration) ?: 0L))
        else event.dtend ?: (event.dtstart + (EventRules.durationMs(event.duration) ?: 0L))
        val reminder = reminders.firstOrNull()?.first
        val repeat = EventRules.repeatOf(event.rrule)
        val z = if (event.allDay) EventRules.UTC else zone
        val start = EventRules.local(startMs, z)
        // An all-day event's stored end is the midnight AFTER its last day.
        val end = if (event.allDay) EventRules.local(maxOf(startMs, endMs - 1), z) else EventRules.local(endMs, z)
        return EventDraft(
            eventId = event.id,
            scope = if (event.recurring) scope ?: EditScope.ALL else null,
            instanceBeginMs = if (event.recurring) startMs else null,
            title = event.title.orEmpty(), location = event.location.orEmpty(), notes = event.description.orEmpty(),
            allDay = event.allDay,
            startDate = start.toLocalDate(), startTime = if (event.allDay) LocalTime.of(9, 0) else start.toLocalTime(),
            endDate = end.toLocalDate(), endTime = if (event.allDay) LocalTime.of(10, 0) else end.toLocalTime(),
            repeat = repeat, reminder = reminder,
            loadedStartMs = startMs, loadedEndMs = endMs, loadedRrule = event.rrule, loadedRepeat = repeat, loadedReminder = reminder,
            loadedAllDay = event.allDay,
            masterStartMs = if (event.recurring) event.dtstart else null,
            canRepeat = event.originalId == null && scope != EditScope.THIS,
        )
    }

    fun save(context: Context, draft: EventDraft, zone: ZoneId): SaveResult {
        if (!CalendarReads.canWrite(context)) return SaveResult.NeedsWrite
        val reminders = listOfNotNull(draft.reminder)
        val id = draft.eventId
        if (id == null) {
            val built = EventRules.build(draft, zone) as? EventRules.Built.Ok ?: return invalid(EventRules.build(draft, zone))
            // Not while READ_CALENDAR is denied: a lookup that cannot answer must never be followed by a create (r3 D7).
            val calendarId = (if (CalendarReads.canRead(context)) LocalCalendar.id(context) else null) ?: return SaveResult.NoCalendar
            return result(CalendarWrites.insertEvent(context, Path.EDITOR, calendarId, built.values, reminders))
        }
        val keptReminders = if (draft.reminderChanged) reminders else null
        return when (draft.scope) {
            null -> {
                val built = EventRules.build(draft, zone, recurring = draft.canRepeat && draft.repeat != Repeat.NONE) as? EventRules.Built.Ok
                    ?: return invalid(EventRules.build(draft, zone))
                result(CalendarWrites.updateEvent(context, Path.EDITOR, id, built.values, keptReminders), id)
            }
            EditScope.THIS -> saveThis(context, draft, id, zone, keptReminders)
            EditScope.ALL -> saveAll(context, draft, id, zone, keptReminders)
            EditScope.FOLLOWING ->
                if (draft.instanceBeginMs == null || draft.instanceBeginMs == draft.masterStartMs) saveAll(context, draft, id, zone, keptReminders)
                else saveFollowing(context, draft, id, zone, keptReminders)
        }
    }

    private fun invalid(built: EventRules.Built): SaveResult = SaveResult.Invalid((built as? EventRules.Built.Invalid)?.reason ?: "That event can't be saved.")

    private fun result(written: WriteResult<Long>): SaveResult = when (written) {
        is WriteResult.Ok -> SaveResult.Saved(written.value)
        is WriteResult.Refused -> SaveResult.Failed(written.why.text)
        is WriteResult.Failed -> SaveResult.Failed(written.error)
    }

    private fun result(written: WriteResult<Unit>, id: Long): SaveResult = when (written) {
        is WriteResult.Ok -> SaveResult.Saved(id)
        is WriteResult.Refused -> SaveResult.Failed(written.why.text)
        is WriteResult.Failed -> SaveResult.Failed(written.error)
    }

    /** "Edit this occurrence": an exception event (`ORIGINAL_ID` the master, `ORIGINAL_INSTANCE_TIME` the occurrence). */
    private fun saveThis(context: Context, draft: EventDraft, masterId: Long, zone: ZoneId, reminders: List<Int>?): SaveResult {
        val instance = draft.instanceBeginMs ?: return SaveResult.Failed("no occurrence")
        val built = EventRules.build(draft, zone, recurring = false) as? EventRules.Built.Ok ?: return invalid(EventRules.build(draft, zone, recurring = false))
        val v = built.values
        val values = ExceptionValues(
            title = v.title, location = v.location, description = v.description, dtstart = v.dtstart,
            duration = if (v.allDay) "P${ChronoUnit.DAYS.between(draft.startDate, draft.endDate) + 1}D" else "P${((v.dtend ?: v.dtstart) - v.dtstart) / 1000}S",
            allDay = v.allDay.takeIf { it != draft.loadedAllDay }, timezone = v.timezone.takeIf { v.allDay != draft.loadedAllDay },
            // The provider marks a new exception tentative unless told otherwise.
            status = CalendarContract.Events.STATUS_CONFIRMED,
        )
        return result(CalendarWrites.insertException(context, Path.EDITOR, masterId, instance, values, reminders))
    }

    /** "All": the master, edited in place. A time changed on a later occurrence moves the whole series by as much. */
    private fun saveAll(context: Context, draft: EventDraft, masterId: Long, zone: ZoneId, reminders: List<Int>?): SaveResult {
        val built = EventRules.build(draft, zone) as? EventRules.Built.Ok ?: return invalid(EventRules.build(draft, zone))
        val shift = built.values.dtstart - (draft.loadedStartMs ?: built.values.dtstart)
        val values = built.values.copy(dtstart = (draft.masterStartMs ?: built.values.dtstart) + shift)
            .let { v -> if (v.dtend != null) v.copy(dtend = v.dtstart + (built.values.dtend!! - built.values.dtstart)) else v }
        return result(CalendarWrites.updateEvent(context, Path.EDITOR, masterId, values, reminders), masterId)
    }

    /**
     * "This and following": the master's rule ends just before the occurrence (`UNTIL`), and a new master starts at
     * it with what the editor holds. An exception event later than the split belongs to the new series from then on.
     */
    private fun saveFollowing(context: Context, draft: EventDraft, masterId: Long, zone: ZoneId, reminders: List<Int>?): SaveResult {
        val master = CalendarReads.event(context, masterId) ?: return SaveResult.Failed("the event is gone")
        val rrule = master.rrule ?: return SaveResult.Failed("the event does not repeat")
        val instance = draft.instanceBeginMs ?: return SaveResult.Failed("no occurrence")
        val built = EventRules.build(draft, zone) as? EventRules.Built.Ok ?: return invalid(EventRules.build(draft, zone))
        val exceptions = CalendarReads.exceptions(context, masterId)
        // A COUNT counts every occurrence the rule makes, the ones an exception replaced included.
        val before = occurrencesBefore(context, master, instance) + exceptions.count { (it.originalInstanceTime ?: Long.MAX_VALUE) < instance }
        val tail = built.values.let { v -> if (v.rrule != null && draft.repeat == draft.loadedRepeat) v.copy(rrule = EventRules.tailOf(rrule, before)) else v }
        val calendarId = master.calendarId

        val ended = CalendarWrites.setRule(context, Path.EDITOR, masterId, EventRules.endBefore(rrule, instance, master.allDay))
        if (ended !is WriteResult.Ok) return result(ended, masterId)
        val kept = reminders ?: CalendarReads.reminders(context, masterId).map { it.first }
        val created = CalendarWrites.insertEvent(context, Path.EDITOR, calendarId, tail, kept)
        if (created !is WriteResult.Ok) return result(created)
        val later = exceptions.filter { (it.originalInstanceTime ?: Long.MIN_VALUE) >= instance }
        if (later.isNotEmpty() && tail.rrule != null) {
            later.forEach { CalendarWrites.repointException(context, Path.EDITOR, it.id, created.value) }
            // Writing the rule again makes the provider expand the new series with its exceptions in place.
            CalendarWrites.setRule(context, Path.EDITOR, created.value, tail.rrule)
        }
        return SaveResult.Saved(created.value)
    }

    /** How many occurrences of [master] itself start before [instanceBeginMs]. */
    private fun occurrencesBefore(context: Context, master: EventDetail, instanceBeginMs: Long): Int =
        CalendarReads.instances(context, master.dtstart, instanceBeginMs)?.count { it.eventId == master.id && it.beginMs < instanceBeginMs } ?: 0

    /**
     * Delete, for the scope the occurrence prompt gave (null for an event that does not repeat).
     * @param both the delete choice of a synced event: "here and from <calendar>"
     */
    fun delete(context: Context, eventId: Long, scope: EditScope?, instanceBeginMs: Long?, both: Boolean): WriteResult<Unit> {
        val event = CalendarReads.event(context, eventId) ?: return WriteResult.Failed("no such event")
        if (!event.recurring || scope == null || scope == EditScope.ALL || instanceBeginMs == null) {
            return CalendarSync.delete(context, Path.EDITOR, eventId, both)
        }
        return when (scope) {
            EditScope.THIS -> when (val made = CalendarWrites.insertException(
                context, Path.EDITOR, eventId, instanceBeginMs, ExceptionValues(status = CalendarContract.Events.STATUS_CANCELED), null,
            )) {
                is WriteResult.Ok -> WriteResult.Ok(Unit)
                is WriteResult.Refused -> made
                is WriteResult.Failed -> made
            }
            else -> {
                if (instanceBeginMs == event.dtstart) return CalendarSync.delete(context, Path.EDITOR, eventId, both)
                val rrule = event.rrule ?: return WriteResult.Failed("the event does not repeat")
                val later = CalendarReads.exceptions(context, eventId).filter { (it.originalInstanceTime ?: Long.MIN_VALUE) >= instanceBeginMs }
                val ended = CalendarWrites.setRule(context, Path.EDITOR, eventId, EventRules.endBefore(rrule, instanceBeginMs, event.allDay))
                if (ended is WriteResult.Ok) later.forEach { CalendarWrites.deleteEvent(context, Path.EDITOR, it.id) }
                ended
            }
        }
    }
}
