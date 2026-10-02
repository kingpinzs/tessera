package app.tileshell.calendar

import android.provider.CalendarContract
import app.tileshell.calendar.CalendarWriteGuard.Path
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.LocalCalendar

/**
 * Tess's part of the calendar (r3 D1; Q2 rule 1: "by any path (app, Tess, Sync)"): what she reads, which event she may
 * offer to delete, and her two writes — an insert into the Tessera calendar and a delete of a Tessera event "here".
 * `ActionLayer` asks the questions and speaks the answers; the provider calls and the rules are here, behind
 * [CalendarAccess], so the JVM tests reach them (fix round F15).
 */
object TessCalendar {
    data class Event(val id: Long, val title: String, val calendarId: Long)

    /** What "delete the event <title>" found. */
    sealed interface DeleteMatch {
        /** An event of the Tessera calendar: the one a Delete card may name. */
        data class Found(val event: Event) : DeleteMatch
        /** The title is only on another calendar — an account's, Birthdays: no card, Tess never writes there. */
        data object Elsewhere : DeleteMatch
        data object None : DeleteMatch
    }

    /**
     * The events with an instance inside [windowMs] from [fromMs], every calendar's — less a synced copy while its
     * Tessera original exists (Q-16-2: one event, named once). A timed instance counts by the clock, an all-day one by
     * its date ([InstanceWindow]): "today" holds today's birthday all day, in any zone.
     */
    fun events(access: CalendarAccess, fromMs: Long, windowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Event> = runCatching {
        val copies = SyncedCopies.hiddenEventIds(access)
        val (rangeFrom, rangeTo) = InstanceWindow.queryRange(fromMs, windowMs, zone)
        access.provider.query(
            ProviderUri(ProviderTable.INSTANCES, listOf(rangeFrom, rangeTo)),
            listOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            ),
            sort = "${CalendarContract.Instances.BEGIN} ASC",
        )?.mapNotNull { row ->
            val title = row.string(1) ?: return@mapNotNull null
            val allDay = !row.isNull(5) && row.long(5) == 1L
            if (!InstanceWindow.shows(row.long(2), row.long(4), allDay, fromMs, windowMs, zone)) return@mapNotNull null
            if (row.long(0) in copies) null else Event(row.long(0), title, row.long(3))
        }.orEmpty()
    }.onFailure { Diagnostics.add("cortana", "calendar instances failed: $it") }.getOrDefault(emptyList())

    /**
     * The title is matched in the Tessera calendar ALONE ([localCalendarId]; null when there is none): first a whole
     * title, then a title that contains it. A title found only on another calendar is [DeleteMatch.Elsewhere].
     */
    fun matchForDelete(events: List<Event>, localCalendarId: Long?, title: String): DeleteMatch {
        val mine = events.filter { it.calendarId == localCalendarId }
        val event = mine.firstOrNull { it.title.equals(title, ignoreCase = true) } ?: mine.firstOrNull { it.title.contains(title, ignoreCase = true) }
        if (event != null) return DeleteMatch.Found(event)
        val elsewhere = events.any { it.title.equals(title, ignoreCase = true) } || events.any { it.title.contains(title, ignoreCase = true) }
        return if (elsewhere) DeleteMatch.Elsewhere else DeleteMatch.None
    }

    /** The event a Delete card may name for [title], among the next 30 days' events. Never creates the calendar. */
    fun findForDelete(access: CalendarAccess, title: String, nowMs: Long): DeleteMatch =
        matchForDelete(events(access, nowMs, 30L * 86_400_000L), LocalCalendar.existingId(access), title)

    /**
     * The delete goes through the calendar write layer, whose guard re-reads the event's calendar and refuses any but
     * Tessera. A synced event is deleted "here" only: its copy in the account calendar is kept and the mapping dropped.
     */
    fun delete(access: CalendarAccess, eventId: Long): WriteResult<Unit> = CalendarSync.delete(access, Path.TESS, eventId, both = false)
}
