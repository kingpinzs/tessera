package app.tileshell.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import app.tileshell.diag.Diagnostics

/** A calendar the provider holds, whoever synced it. Every calendar but Tessera is read-only in the app (Q2 rule 1). */
data class CalendarInfo(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val displayName: String,
    /** Null when the provider holds no colour: the accent is drawn. */
    val color: Int?,
    val accessLevel: Int,
    /** The provider's `Calendars.NAME` — the calendar's own name inside its account, never shown; empty when the provider holds none. */
    val name: String,
) {
    val key: CalendarKey get() = CalendarKey(id, accountName, accountType, name)
    val facts: CalendarWriteGuard.CalendarFacts get() = CalendarWriteGuard.CalendarFacts(id, accountName, accountType, accessLevel, name)
    val isTessera: Boolean get() = CalendarWriteGuard.isTessera(facts)
    val isBirthdays: Boolean get() = CalendarWriteGuard.isBirthdays(facts)

    /** A calendar whose display name is empty shows its account name (Edge cases). */
    val shownName: String get() = displayName.ifBlank { accountName }

    /** "Can sync to" (r3 D5): a non-LOCAL calendar the phone may write — never Tessera, Birthdays or a read-only one. */
    val canBeSyncTarget: Boolean
        get() = accountType != CalendarWriteGuard.ACCOUNT_TYPE_LOCAL && accessLevel >= CalendarWriteGuard.ACCESS_CONTRIBUTOR
}

/** One row of `Instances`: an occurrence of an event inside a queried window. */
data class EventInstance(
    val eventId: Long,
    val title: String?,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val calendarId: Long,
    /** The event's own colour, else its calendar's; null when neither is set. */
    val color: Int?,
    val location: String?,
    /** Free or tentative: the bar is drawn as a 2-epx outline (r11/calendar.md K3.8). */
    val free: Boolean,
)

/** An `Events` row, as the event page, the editor and Sync read it. */
data class EventDetail(
    val id: Long,
    val calendarId: Long,
    val title: String?,
    val location: String?,
    val description: String?,
    val dtstart: Long,
    val dtend: Long?,
    val duration: String?,
    val allDay: Boolean,
    val timezone: String?,
    val rrule: String?,
    val rdate: String?,
    val exrule: String?,
    val exdate: String?,
    val originalId: Long?,
    val originalInstanceTime: Long?,
    val availability: Int,
    val status: Int?,
    val color: Int?,
) {
    val recurring: Boolean get() = !rrule.isNullOrBlank() || !rdate.isNullOrBlank()
}

/** A due alert row of `CalendarAlerts`, as the reminder receiver reads it. */
data class AlertRow(
    val id: Long,
    val eventId: Long,
    val beginMs: Long,
    val endMs: Long,
    val alarmTimeMs: Long,
    val state: Int,
    val minutes: Int,
    val title: String?,
    val allDay: Boolean,
) {
    /**
     * What the receiver remembers of an alert it has handled: the event, the occurrence and the alarm time — not the
     * row's `_id`, which the provider hands out again once the row is deleted (as it does calendar ids, Verify at build
     * start 3): a new alert that inherited a handled row's id would otherwise never notify.
     */
    val key: String get() = "$eventId:$beginMs:$alarmTimeMs"
}

sealed interface CalendarsResult {
    data class Ok(val calendars: List<CalendarInfo>) : CalendarsResult
    /** The query did not answer: the provider is off, or the permission is not held. */
    data class Failed(val error: String) : CalendarsResult
}

/**
 * Every read the Calendar app, Sync, the Birthdays writer and the receiver make of `CalendarContract`, each through
 * [CalendarAccess]'s provider (F15). Nothing here writes.
 */
object CalendarReads {
    fun canRead(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private val CALENDAR_COLUMNS = listOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.CALENDAR_COLOR,
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        CalendarContract.Calendars.NAME,
    )

    private fun calendar(c: ProviderRow) = CalendarInfo(
        id = c.long(0),
        accountName = c.string(1).orEmpty(),
        accountType = c.string(2).orEmpty(),
        displayName = c.string(3).orEmpty(),
        color = if (c.isNull(4)) null else c.int(4),
        accessLevel = c.int(5),
        name = c.string(6).orEmpty(),
    )

    /** Every calendar, in the provider's order of account then name. No answer is a provider that is off. */
    fun calendars(access: CalendarAccess): CalendarsResult = runCatching {
        val rows = access.provider.query(
            ProviderUri(ProviderTable.CALENDARS), CALENDAR_COLUMNS,
            sort = "${CalendarContract.Calendars.ACCOUNT_NAME} COLLATE NOCASE ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC",
        ) ?: return@runCatching CalendarsResult.Failed("the calendar provider gave no answer")
        CalendarsResult.Ok(rows.map { calendar(it) })
    }.getOrElse { CalendarsResult.Failed(it.toString()) }

    /** One calendar row, re-read; null when it does not list (or cannot be read). */
    fun calendar(access: CalendarAccess, id: Long): CalendarInfo? = runCatching {
        access.provider.query(ProviderUri(ProviderTable.CALENDARS, id), CALENDAR_COLUMNS)?.firstOrNull()?.let { calendar(it) }
    }.getOrNull()

    /**
     * The id of the LOCAL calendar under [accountName] (Tessera's lookup, and the Birthdays calendar's): null when a
     * successful query found none, or the error of a failed one — a lookup that did not answer is not an empty one.
     */
    fun findByAccount(access: CalendarAccess, accountName: String): Result<Long?> = runCatching {
        val rows = access.provider.query(
            ProviderUri(ProviderTable.CALENDARS), listOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            listOf(CalendarContract.ACCOUNT_TYPE_LOCAL, accountName),
        ) ?: error("the calendar provider gave no answer")
        rows.firstOrNull()?.long(0)
    }

    /**
     * The instances the provider gives for [fromMs, toMs − 1] (its range is inclusive at both ends), every calendar's —
     * the provider's `visible` column is not consulted: show / hide is the shell's own state (r3 D4). The rows are the
     * provider's, unfiltered, so their count is the count `content query …/instances/when/<from>/<to − 1>` gives.
     * Null when the query failed.
     */
    fun instances(access: CalendarAccess, fromMs: Long, toMs: Long): List<EventInstance>? = runCatching {
        access.provider.query(
            ProviderUri(ProviderTable.INSTANCES, listOf(fromMs, toMs - 1)),
            listOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.AVAILABILITY,
            ),
            sort = "${CalendarContract.Instances.BEGIN} ASC, ${CalendarContract.Instances.TITLE} ASC",
        )?.map { c ->
            val availability = c.int(8)
            EventInstance(
                eventId = c.long(0),
                title = c.string(1),
                beginMs = c.long(2),
                endMs = c.long(3),
                allDay = c.int(4) == 1,
                calendarId = c.long(5),
                color = if (c.isNull(6) || c.int(6) == 0) null else c.int(6),
                location = c.string(7),
                free = availability == CalendarContract.Events.AVAILABILITY_FREE || availability == CalendarContract.Events.AVAILABILITY_TENTATIVE,
            )
        }
    }.onFailure { Diagnostics.add("calendar", "instances query failed: $it") }.getOrNull()

    private val EVENT_COLUMNS = listOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.RRULE,
        CalendarContract.Events.RDATE,
        CalendarContract.Events.EXRULE,
        CalendarContract.Events.EXDATE,
        CalendarContract.Events.ORIGINAL_ID,
        CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
        CalendarContract.Events.AVAILABILITY,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.DISPLAY_COLOR,
    )

    private fun event(c: ProviderRow) = EventDetail(
        id = c.long(0),
        calendarId = c.long(1),
        title = c.string(2),
        location = c.string(3),
        description = c.string(4),
        dtstart = c.long(5),
        dtend = if (c.isNull(6)) null else c.long(6),
        duration = c.string(7),
        allDay = c.int(8) == 1,
        timezone = c.string(9),
        rrule = c.string(10),
        rdate = c.string(11),
        exrule = c.string(12),
        exdate = c.string(13),
        originalId = if (c.isNull(14)) null else c.long(14),
        originalInstanceTime = if (c.isNull(15)) null else c.long(15),
        availability = c.int(16),
        status = if (c.isNull(17)) null else c.int(17),
        color = if (c.isNull(18) || c.int(18) == 0) null else c.int(18),
    )

    private const val NOT_DELETED = "${CalendarContract.Events.DELETED} != 1"

    /** An event row that still exists (a row marked deleted for its sync adapter does not). */
    fun event(access: CalendarAccess, id: Long): EventDetail? = runCatching {
        access.provider.query(
            ProviderUri(ProviderTable.EVENTS), EVENT_COLUMNS, "${CalendarContract.Events._ID} = ? AND $NOT_DELETED", listOf(id.toString()),
        )?.firstOrNull()?.let { event(it) }
    }.getOrNull()

    /** The exception events of [masterId]: the rows whose `ORIGINAL_ID` it is. */
    fun exceptions(access: CalendarAccess, masterId: Long): List<EventDetail> = runCatching {
        access.provider.query(
            ProviderUri(ProviderTable.EVENTS), EVENT_COLUMNS, "${CalendarContract.Events.ORIGINAL_ID} = ? AND $NOT_DELETED", listOf(masterId.toString()),
        )?.map { event(it) }
    }.getOrNull().orEmpty()

    /** (minutes, method) of every reminder row of [eventId]. */
    fun reminders(access: CalendarAccess, eventId: Long): List<Pair<Int, Int>> = runCatching {
        access.provider.query(
            ProviderUri(ProviderTable.REMINDERS), listOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?", listOf(eventId.toString()), "${CalendarContract.Reminders.MINUTES} ASC",
        )?.map { it.int(0) to it.int(1) }
    }.getOrNull().orEmpty()

    /** Calendar id → how many events it holds. One read of the calendar-id column, counted here (r3 V3). */
    fun eventCounts(access: CalendarAccess): Map<Long, Int> = runCatching {
        val counts = HashMap<Long, Int>()
        access.provider.query(ProviderUri(ProviderTable.EVENTS), listOf(CalendarContract.Events.CALENDAR_ID), NOT_DELETED)
            ?.forEach { counts.merge(it.long(0), 1, Int::plus) }
        counts
    }.getOrDefault(emptyMap())

    /** Which of [ids] are events that still exist; null when the query failed (nothing is concluded from that). */
    fun existingEvents(access: CalendarAccess, ids: Collection<Long>): Set<Long>? {
        if (ids.isEmpty()) return emptySet()
        return runCatching {
            access.provider.query(
                ProviderUri(ProviderTable.EVENTS), listOf(CalendarContract.Events._ID),
                "${CalendarContract.Events._ID} IN (${ids.joinToString(",")}) AND $NOT_DELETED",
            )?.mapTo(HashSet()) { it.long(0) }
        }.getOrNull()
    }

    /** master id → its exception event ids, for the masters in [masters]. */
    fun exceptionIds(access: CalendarAccess, masters: Collection<Long>): Map<Long, List<Long>> {
        if (masters.isEmpty()) return emptyMap()
        return runCatching {
            val out = HashMap<Long, MutableList<Long>>()
            access.provider.query(
                ProviderUri(ProviderTable.EVENTS), listOf(CalendarContract.Events._ID, CalendarContract.Events.ORIGINAL_ID),
                "${CalendarContract.Events.ORIGINAL_ID} IN (${masters.joinToString(",")})",
            )?.forEach { out.getOrPut(it.long(1)) { mutableListOf() }.add(it.long(0)) }
            out
        }.getOrDefault(emptyMap())
    }

    /**
     * The alert rows due at [nowMs] in state SCHEDULED or FIRED (r3 D6 (c)): another calendar app may have flipped a
     * row to FIRED first, so a SCHEDULED-only read can find nothing.
     */
    fun dueAlerts(access: CalendarAccess, nowMs: Long): List<AlertRow>? = runCatching {
        access.provider.query(
            ProviderUri(ProviderTable.ALERTS),
            listOf(
                CalendarContract.CalendarAlerts._ID,
                CalendarContract.CalendarAlerts.EVENT_ID,
                CalendarContract.CalendarAlerts.BEGIN,
                CalendarContract.CalendarAlerts.END,
                CalendarContract.CalendarAlerts.ALARM_TIME,
                CalendarContract.CalendarAlerts.STATE,
                CalendarContract.CalendarAlerts.MINUTES,
                CalendarContract.CalendarAlerts.TITLE,
                CalendarContract.CalendarAlerts.ALL_DAY,
            ),
            "${CalendarContract.CalendarAlerts.ALARM_TIME} <= ? AND ${CalendarContract.CalendarAlerts.STATE} IN " +
                "(${CalendarContract.CalendarAlerts.STATE_SCHEDULED}, ${CalendarContract.CalendarAlerts.STATE_FIRED})",
            listOf(nowMs.toString()),
            "${CalendarContract.CalendarAlerts.ALARM_TIME} ASC",
        )?.map { c -> AlertRow(c.long(0), c.long(1), c.long(2), c.long(3), c.long(4), c.int(5), c.int(6), c.string(7), c.int(8) == 1) }
    }.onFailure { Diagnostics.add("calendar", "alerts query failed: $it") }.getOrNull()
}

/**
 * The one reader of the synced-copy ids (Q-16-2; r3 D2): the copy event ids `calendar_sync.json` maps whose local
 * original still exists, with a recurring copy's exception events counted with their master. The Calendar app's
 * views, `CalendarFeed`'s tile and agenda queries, Tess's calendar reads and the reminder receiver each drop every
 * instance whose event id is in this set — one event, shown once, one reminder.
 *
 * A mapping whose local event is gone is dropped here, at the read: the copy then shows as the account event it is.
 */
object SyncedCopies {
    fun hiddenEventIds(access: CalendarAccess): Set<Long> {
        val store = access.store
        val before = store.current
        if (before.mappings.isEmpty()) return emptySet()
        // A failed query concludes nothing: the mappings are kept and the copies stay hidden.
        val existing = CalendarReads.existingEvents(access, before.mappings.keys)
        val state = if (existing == null) before else store.update { SyncStateRules.dropGoneOriginals(it, existing) }
        if (existing != null && state.mappings.size != before.mappings.size) {
            Diagnostics.add("calendar", "sync mappings dropped (the local event is gone): ${(before.mappings.keys - state.mappings.keys).sorted()}")
        }
        val copies = SyncStateRules.copyIds(state)
        return SyncStateRules.hiddenEventIds(state, CalendarReads.exceptionIds(access, copies))
    }
}

/**
 * The store's `allowed` and `hidden` lists follow the provider's calendar list wherever the shell is alive, not only
 * while the Calendar app is open: `CalendarFeed`'s observer calls this on every provider change. A calendar removed
 * from the phone leaves both lists at once, so one that comes back — even under the `_ID` it had, which the provider
 * hands out again after a delete (Verify at build start 3) — starts NOT allowed (T16-1; the Trust line (e)).
 */
object SyncAllowList {
    fun followProvider(access: CalendarAccess) {
        val store = access.store
        val state = store.current
        if (state.allowed.isEmpty() && state.hidden.isEmpty()) return
        // A read that did not answer concludes nothing.
        val present = (CalendarReads.calendars(access) as? CalendarsResult.Ok)?.calendars?.mapTo(HashSet()) { it.key } ?: return
        val after = store.update { SyncStateRules.prune(it, present) }
        if (after.allowed.size != state.allowed.size) {
            Diagnostics.add("calendar", "can sync to: ${state.allowed.size - after.allowed.size} calendar(s) no longer on this phone left the list")
        }
    }
}
