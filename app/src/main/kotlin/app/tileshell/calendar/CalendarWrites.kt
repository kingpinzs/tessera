package app.tileshell.calendar

import android.provider.CalendarContract
import app.tileshell.calendar.CalendarWriteGuard.CalendarFacts
import app.tileshell.calendar.CalendarWriteGuard.Op
import app.tileshell.calendar.CalendarWriteGuard.Path
import app.tileshell.calendar.CalendarWriteGuard.Refusal
import app.tileshell.calendar.CalendarWriteGuard.Request
import app.tileshell.calendar.CalendarWriteGuard.SyncFacts
import app.tileshell.calendar.CalendarWriteGuard.Table
import app.tileshell.calendar.CalendarWriteGuard.Verdict
import app.tileshell.diag.Diagnostics

sealed interface WriteResult<out T> {
    data class Ok<T>(val value: T) : WriteResult<T>
    data class Refused(val why: Refusal) : WriteResult<Nothing>
    data class Failed(val error: String) : WriteResult<Nothing>

    val ok: Boolean get() = this is Ok
}

/** What an occurrence edit changes: the columns the provider lets an exception row override. */
data class ExceptionValues(
    val title: String? = null,
    val location: String? = null,
    val description: String? = null,
    val dtstart: Long? = null,
    /** RFC 2445, e.g. `P3600S`; the provider turns it into the exception row's DTEND. */
    val duration: String? = null,
    val allDay: Boolean? = null,
    val timezone: String? = null,
    val status: Int? = null,
)

/**
 * The ONE write layer (build task 3; the Trust line (a)): every `CalendarContract` insert, update and delete the shell
 * makes is in this file and nowhere else — the editor's, Sync's, the Birthdays writer's, the reminder receiver's,
 * `LocalCalendar`'s creation of Tessera, and Tess's insert and delete. Each write re-reads from the provider the
 * calendar its row belongs to, puts the facts to [CalendarWriteGuard], and reaches the provider only when the guard
 * allows it.
 *
 * The provider is [CalendarAccess]'s (fix round F15): on the phone the content resolver, in the JVM tests a recording
 * fake — `CalendarWriteLayerTest` asks of every function here that a refused request records no write at all and an
 * allowed one exactly the write it should.
 *
 * Lines: the editor's and Tess's event writes log `[calendar] write <op> event=<id>: ok | failed <err> | failed
 * refused (not allowed)`; a refusal on any other path but Sync's logs the same refused line; Sync's results are the
 * `[calendar] sync …` lines its engine writes, one per tapped Sync.
 */
object CalendarWrites {

    // ------------------------------------------------------------------------------------------ facts, re-read

    private val EVENTS = ProviderUri(ProviderTable.EVENTS)

    private fun eventUri(eventId: Long) = ProviderUri(ProviderTable.EVENTS, eventId)

    private fun facts(access: CalendarAccess, calendarId: Long?): CalendarFacts? = calendarId?.let { CalendarReads.calendar(access, it)?.facts }

    /** What a read of one event row found: the row, no such row, or no answer at all. */
    private sealed interface RowRead {
        data class Found(val calendarId: Long, val originalId: Long?) : RowRead
        /** The query answered and holds no such row. */
        data object Absent : RowRead
        /** The query did not answer or threw: nothing is known about the row. */
        data object Failed : RowRead
    }

    /** An event row, deleted or not, by one read that tells "no such row" from "the read failed" (F20). */
    private fun readRow(access: CalendarAccess, eventId: Long): RowRead = try {
        val rows = access.provider.query(
            EVENTS, listOf(CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.ORIGINAL_ID),
            "${CalendarContract.Events._ID} = ?", listOf(eventId.toString()),
        )
        when {
            rows == null -> RowRead.Failed
            rows.isEmpty() -> RowRead.Absent
            else -> rows.first().let { c -> RowRead.Found(c.long(0), if (c.isNull(1)) null else c.long(1)) }
        }
    } catch (e: Exception) {
        RowRead.Failed
    }

    /** (calendar_id, original_id) of an event row, deleted or not; null when there is no such row or it could not be read. */
    private fun eventRow(access: CalendarAccess, eventId: Long): Pair<Long, Long?>? =
        (readRow(access, eventId) as? RowRead.Found)?.let { it.calendarId to it.originalId }

    /** A row's `_sync_id`, DTSTART and DURATION, for the two writes that must name them. */
    private data class SeriesRow(val syncId: String?, val dtstart: Long, val duration: String?)

    private fun seriesRow(access: CalendarAccess, eventId: Long): SeriesRow? = runCatching {
        access.provider.query(
            EVENTS, listOf(CalendarContract.Events._SYNC_ID, CalendarContract.Events.DTSTART, CalendarContract.Events.DURATION),
            "${CalendarContract.Events._ID} = ?", listOf(eventId.toString()),
        )?.firstOrNull()?.let { c -> SeriesRow(c.string(0), c.long(1), c.string(2)) }
    }.getOrNull()

    private fun eventCalendar(access: CalendarAccess, eventId: Long): CalendarFacts? = facts(access, eventRow(access, eventId)?.first)

    // ------------------------------------------------------------------------------------------ the gate

    private inline fun <T> guarded(request: Request, perform: () -> T): WriteResult<T> =
        when (val verdict = CalendarWriteGuard.check(request)) {
            is Verdict.Refused -> WriteResult.Refused(verdict.why)
            Verdict.Allowed -> try {
                WriteResult.Ok(perform())
            } catch (e: Exception) {
                WriteResult.Failed(e.toString())
            }
        }

    private fun <T> logged(path: Path, op: Op, event: String, result: WriteResult<T>, eventOf: (T) -> String = { event }): WriteResult<T> {
        val line = when (result) {
            is WriteResult.Ok -> if (path == Path.EDITOR || path == Path.TESS) "write ${op.word} event=${eventOf(result.value)}: ok" else null
            is WriteResult.Failed -> if (path == Path.EDITOR || path == Path.TESS) "write ${op.word} event=$event: failed ${result.error}" else null
            // A refusal is unreachable from the UI by design, so it is always said, whoever asked — but Sync's, which its own line says.
            is WriteResult.Refused -> if (path != Path.SYNC) "write ${op.word} event=$event: failed ${result.why.text}" else null
        }
        if (line != null) Diagnostics.add("calendar", line)
        return result
    }

    /** A write's columns and their values (a null value is written as NULL), in the order they were put. */
    private fun columns(fill: LinkedHashMap<String, Any?>.() -> Unit): LinkedHashMap<String, Any?> = LinkedHashMap<String, Any?>().apply(fill)

    private fun eventColumns(v: EventValues): LinkedHashMap<String, Any?> = columns {
        put(CalendarContract.Events.TITLE, v.title)
        put(CalendarContract.Events.EVENT_LOCATION, v.location)
        put(CalendarContract.Events.DESCRIPTION, v.description)
        put(CalendarContract.Events.DTSTART, v.dtstart)
        // DTEND or DURATION, never both: a repeating event carries its length as a duration (the provider's rule).
        if (v.dtend != null) {
            put(CalendarContract.Events.DTEND, v.dtend)
            put(CalendarContract.Events.DURATION, null)
        } else {
            put(CalendarContract.Events.DTEND, null)
            put(CalendarContract.Events.DURATION, v.duration)
        }
        put(CalendarContract.Events.ALL_DAY, if (v.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, v.timezone)
        put(CalendarContract.Events.RRULE, v.rrule)
        put(CalendarContract.Events.RDATE, v.rdate)
        put(CalendarContract.Events.EXRULE, v.exrule)
        put(CalendarContract.Events.EXDATE, v.exdate)
        put(CalendarContract.Events.AVAILABILITY, v.availability)
        if (v.status != null) put(CalendarContract.Events.STATUS, v.status)
    }

    private fun exceptionColumns(originalInstanceTime: Long, v: ExceptionValues): LinkedHashMap<String, Any?> = columns {
        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, originalInstanceTime)
        v.title?.let { put(CalendarContract.Events.TITLE, it) }
        v.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        v.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        v.dtstart?.let { put(CalendarContract.Events.DTSTART, it) }
        v.duration?.let { put(CalendarContract.Events.DURATION, it) }
        v.allDay?.let { put(CalendarContract.Events.ALL_DAY, if (it) 1 else 0) }
        v.timezone?.let { put(CalendarContract.Events.EVENT_TIMEZONE, it) }
        v.status?.let { put(CalendarContract.Events.STATUS, it) }
    }

    private fun idOf(uri: String?): Long = rowIdOf(uri)?.takeIf { it > 0 } ?: error("the provider made no row")

    // ------------------------------------------------------------------------------------------ the two LOCAL calendars

    /**
     * Case 1's one calendar insert: the Tessera calendar, created by `LocalCalendar` as its own sync adapter (a LOCAL
     * account needs no Android account). Throws what the provider throws, and when the guard refuses.
     */
    fun createLocalCalendar(access: CalendarAccess, accountName: String, color: Int): String? {
        val request = Request(
            Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS,
            CalendarFacts(0, accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, CalendarContract.Calendars.CAL_ACCESS_OWNER, accountName),
            viaSyncAdapter = true,
        )
        check(CalendarWriteGuard.check(request) == Verdict.Allowed) { "the write guard refuses a calendar named $accountName" }
        return access.provider.insert(
            ProviderUri(ProviderTable.CALENDARS).asSyncAdapter(accountName),
            calendarColumns(accountName, accountName, CalendarContract.Calendars.CAL_ACCESS_OWNER, color),
        )
    }

    /**
     * Case 2's calendar insert: the read-only (access 200) LOCAL calendar "Birthdays" under its own account name, so
     * `LocalCalendar` — which finds Tessera by account name — can never pick it (T16-2 line 2).
     */
    fun createBirthdaysCalendar(access: CalendarAccess, displayName: String, color: Int): Long {
        val account = CalendarWriteGuard.BIRTHDAYS_ACCOUNT
        val request = Request(
            Path.BIRTHDAYS, Op.INSERT, Table.CALENDARS,
            CalendarFacts(0, account, CalendarContract.ACCOUNT_TYPE_LOCAL, CalendarContract.Calendars.CAL_ACCESS_READ, displayName),
            viaSyncAdapter = true,
        )
        check(CalendarWriteGuard.check(request) == Verdict.Allowed) { "the write guard refuses the Birthdays calendar" }
        return idOf(
            access.provider.insert(
                ProviderUri(ProviderTable.CALENDARS).asSyncAdapter(account),
                calendarColumns(account, displayName, CalendarContract.Calendars.CAL_ACCESS_READ, color),
            ),
        )
    }

    private fun calendarColumns(account: String, displayName: String, access: Int, color: Int) = columns {
        put(CalendarContract.Calendars.ACCOUNT_NAME, account)
        put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        put(CalendarContract.Calendars.NAME, displayName)
        put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
        put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
        put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, access)
        put(CalendarContract.Calendars.VISIBLE, 1)
        put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        put(CalendarContract.Calendars.CALENDAR_COLOR, color)
    }

    /** A birthday's yearly all-day event, written as the Birthdays calendar's own sync adapter. No reminder rows (T16-4). */
    fun birthdayInsert(access: CalendarAccess, calendarId: Long, values: EventValues): WriteResult<Long> {
        val request = Request(Path.BIRTHDAYS, Op.INSERT, Table.EVENTS, facts(access, calendarId), viaSyncAdapter = true)
        return logged(Path.BIRTHDAYS, Op.INSERT, "new", guarded(request) {
            idOf(
                access.provider.insert(
                    EVENTS.asSyncAdapter(CalendarWriteGuard.BIRTHDAYS_ACCOUNT),
                    eventColumns(values).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) },
                ),
            )
        })
    }

    fun birthdayDelete(access: CalendarAccess, eventId: Long): WriteResult<Unit> {
        val request = Request(Path.BIRTHDAYS, Op.DELETE, Table.EVENTS, eventCalendar(access, eventId), viaSyncAdapter = true)
        return logged(Path.BIRTHDAYS, Op.DELETE, eventId.toString(), guarded(request) {
            access.provider.delete(eventUri(eventId).asSyncAdapter(CalendarWriteGuard.BIRTHDAYS_ACCOUNT))
            Unit
        })
    }

    // ------------------------------------------------------------------------------------------ Tessera: the editor and Tess

    /** A new event, with its reminders as `Reminders` rows of `METHOD_ALERT`. */
    fun insertEvent(access: CalendarAccess, path: Path, calendarId: Long, values: EventValues, reminderMinutes: List<Int>): WriteResult<Long> {
        val calendar = facts(access, calendarId)
        val result = guarded(Request(path, Op.INSERT, Table.EVENTS, calendar)) {
            idOf(access.provider.insert(EVENTS, eventColumns(values).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) }))
        }
        if (result is WriteResult.Ok && reminderMinutes.isNotEmpty()) {
            val reminders = setReminders(access, result.value, reminderMinutes.map { it to CalendarContract.Reminders.METHOD_ALERT }, clear = false, reminderRequest(access, path, result.value))
            if (reminders !is WriteResult.Ok) return logged(path, Op.INSERT, result.value.toString(), reminders.retype())
        }
        return logged(path, Op.INSERT, "new", result) { it.toString() }
    }

    /** An edit of an event row in place: the whole series ("all"), a single event, or an exception row. */
    fun updateEvent(access: CalendarAccess, path: Path, eventId: Long, values: EventValues, reminderMinutes: List<Int>?): WriteResult<Unit> {
        val result = updateColumns(access, path, eventId, eventColumns(values).apply { remove(CalendarContract.Events.AVAILABILITY) })
        if (result is WriteResult.Ok && reminderMinutes != null) {
            val reminders = setReminders(access, eventId, reminderMinutes.map { it to CalendarContract.Reminders.METHOD_ALERT }, clear = true, reminderRequest(access, path, eventId))
            if (reminders !is WriteResult.Ok) return logged(path, Op.UPDATE, eventId.toString(), reminders)
        }
        return logged(path, Op.UPDATE, eventId.toString(), result)
    }

    /**
     * "This and following", the old master's half: its rule ends before the occurrence. The row's own DTSTART and
     * DURATION are written with the rule, so the provider expands the series again.
     */
    fun setRule(access: CalendarAccess, path: Path, eventId: Long, rrule: String): WriteResult<Unit> {
        val row = seriesRow(access, eventId)
        val columns = columns {
            put(CalendarContract.Events.RRULE, rrule)
            if (row != null) {
                put(CalendarContract.Events.DTSTART, row.dtstart)
                if (row.duration != null) put(CalendarContract.Events.DURATION, row.duration)
            }
        }
        return logged(path, Op.UPDATE, eventId.toString(), updateColumns(access, path, eventId, columns))
    }

    /** A row that is no longer there cannot be written: said as a failure, before the guard is asked about a calendar nobody can read. */
    private const val GONE = "the event is gone"

    private fun updateColumns(access: CalendarAccess, path: Path, eventId: Long, columns: Map<String, Any?>): WriteResult<Unit> {
        if (eventRow(access, eventId) == null) return WriteResult.Failed(GONE)
        val request = Request(path, Op.UPDATE, Table.EVENTS, eventCalendar(access, eventId), columns.keys.toSet())
        return guarded(request) {
            val rows = access.provider.update(eventUri(eventId), columns)
            if (rows != 1) error("no such event (rows=$rows)")
        }
    }

    /**
     * Deletes an event of the Tessera calendar.
     *
     * An exception row — one changed occurrence of a series — is not removed but marked cancelled: removing the row
     * would bring the series' own occurrence back. Any other row is removed for good, with the exception rows that
     * hang on it, as the LOCAL account's own sync adapter: a normal app's delete of a row that carries a `_sync_id`
     * only marks it deleted, and nobody but the shell would ever come to clear it (qa/phase-16/dev-cal/P_EXCEPTION2).
     */
    fun deleteEvent(access: CalendarAccess, path: Path, eventId: Long): WriteResult<Unit> {
        val row = eventRow(access, eventId) ?: return logged(path, Op.DELETE, eventId.toString(), WriteResult.Failed(GONE))
        val calendar = facts(access, row.first)
        if (row.second != null) {
            val columns = columns { put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED) }
            return logged(path, Op.DELETE, eventId.toString(), guarded(Request(path, Op.DELETE, Table.EVENTS, calendar)) {
                val rows = access.provider.update(eventUri(eventId), columns)
                if (rows != 1) error("no such event (rows=$rows)")
            })
        }
        return logged(path, Op.DELETE, eventId.toString(), guarded(Request(path, Op.DELETE, Table.EVENTS, calendar, viaSyncAdapter = true)) {
            val account = calendar?.accountName.orEmpty()
            access.provider.delete(EVENTS.asSyncAdapter(account), "${CalendarContract.Events.ORIGINAL_ID} = ?", listOf(eventId.toString()))
            val rows = access.provider.delete(eventUri(eventId).asSyncAdapter(account))
            if (rows < 1) error("no such event (rows=$rows)")
        })
    }

    /** Removes one Tessera row for good, whatever it is: an exception row that has been written again elsewhere. */
    fun purgeRow(access: CalendarAccess, path: Path, eventId: Long): WriteResult<Unit> {
        val calendar = eventCalendar(access, eventId)
        return guarded(Request(path, Op.DELETE, Table.EVENTS, calendar, viaSyncAdapter = true)) {
            access.provider.delete(eventUri(eventId).asSyncAdapter(calendar?.accountName.orEmpty()))
            Unit
        }
    }

    /**
     * The provider pairs an occurrence's exception with its series by `_sync_id` (`ORIGINAL_SYNC_ID`): for a series
     * that has none, an exception insert loses the series' other occurrences from `Instances`
     * (qa/phase-16/dev-cal/P_EXCEPTION, P_EXCEPTION2). A LOCAL account's sync adapter is the app itself, so the shell
     * gives a Tessera series its `_sync_id` — once, before its first exception.
     */
    private fun ensureSyncId(access: CalendarAccess, path: Path, masterId: Long): WriteResult<Unit> {
        if (seriesRow(access, masterId)?.syncId != null) return WriteResult.Ok(Unit)
        val calendar = eventCalendar(access, masterId)
        val columns = columns { put(CalendarContract.Events._SYNC_ID, "tessera-$masterId") }
        return guarded(Request(path, Op.UPDATE, Table.EVENTS, calendar, columns.keys.toSet(), viaSyncAdapter = true)) {
            val rows = access.provider.update(eventUri(masterId).asSyncAdapter(calendar?.accountName.orEmpty()), columns)
            if (rows != 1) error("no such event (rows=$rows)")
        }
    }

    /**
     * "Edit this occurrence" and "delete this occurrence": an exception event of [masterId] for the occurrence that
     * starts at [originalInstanceTime] — the provider clones the master, sets `ORIGINAL_ID` and `ORIGINAL_INSTANCE_TIME`
     * and applies [values]. [reminderMinutes] null keeps the reminders the provider copies from the master.
     */
    fun insertException(access: CalendarAccess, path: Path, masterId: Long, originalInstanceTime: Long, values: ExceptionValues, reminderMinutes: List<Int>?): WriteResult<Long> {
        if (eventRow(access, masterId) == null) return logged(path, Op.INSERT, "new", WriteResult.Failed(GONE))
        val keyed = ensureSyncId(access, path, masterId)
        if (keyed !is WriteResult.Ok) return logged(path, Op.INSERT, "new", keyed.retype())
        val result = guarded(Request(path, Op.INSERT, Table.EVENTS, eventCalendar(access, masterId))) { exception(access, masterId, originalInstanceTime, values) }
        if (result is WriteResult.Ok && reminderMinutes != null) {
            val reminders = setReminders(access, result.value, reminderMinutes.map { it to CalendarContract.Reminders.METHOD_ALERT }, clear = true, reminderRequest(access, path, result.value))
            if (reminders !is WriteResult.Ok) return logged(path, Op.INSERT, result.value.toString(), reminders.retype())
        }
        return logged(path, Op.INSERT, "new", result) { it.toString() }
    }

    private fun exception(access: CalendarAccess, masterId: Long, originalInstanceTime: Long, values: ExceptionValues): Long =
        idOf(access.provider.insert(ProviderUri(ProviderTable.EXCEPTIONS, masterId), exceptionColumns(originalInstanceTime, values)))

    /** The editor's and Tess's request for one reminder write of [eventId]: the event's calendar, re-read now. */
    private fun reminderRequest(access: CalendarAccess, path: Path, eventId: Long): (Op) -> Request =
        { op -> Request(path, op, Table.REMINDERS, eventCalendar(access, eventId)) }

    /**
     * The reminder rows of an event: optionally cleared, then one row per (minutes, method). It is a batch of writes,
     * and [request] is asked again before EACH of them (fix round F20, trust review A-F13), so every one is put to the
     * guard with facts re-read at that moment: a copy that was moved, or a calendar un-ticked, between two rows stops
     * the batch there.
     */
    private fun setReminders(access: CalendarAccess, eventId: Long, reminders: List<Pair<Int, Int>>, clear: Boolean, request: (Op) -> Request): WriteResult<Unit> {
        val table = ProviderUri(ProviderTable.REMINDERS)
        if (clear) {
            val cleared = guarded(request(Op.DELETE)) {
                access.provider.delete(table, "${CalendarContract.Reminders.EVENT_ID} = ?", listOf(eventId.toString()))
                Unit
            }
            if (cleared !is WriteResult.Ok) return cleared
        }
        for ((minutes, method) in reminders) {
            val inserted = guarded(request(Op.INSERT)) {
                access.provider.insert(
                    table,
                    columns {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, minutes)
                        put(CalendarContract.Reminders.METHOD, method)
                    },
                )
                Unit
            }
            if (inserted !is WriteResult.Ok) return inserted
        }
        return WriteResult.Ok(Unit)
    }

    private fun WriteResult<Unit>.retype(): WriteResult<Long> = when (this) {
        is WriteResult.Refused -> this
        is WriteResult.Failed -> this
        is WriteResult.Ok -> WriteResult.Failed("unreachable")
    }

    // ------------------------------------------------------------------------------------------ Sync: the copy in an allowed calendar

    /**
     * The facts of a Sync write, each re-read now: whether the source is a Tessera event, whether the store maps the
     * row, where the copy sits, whether the calendar the write touches is on the allowed list, and its access level.
     *
     * @param rowEventId the existing row the write touches (the copy, or an exception of it); null for the first
     *   insert of a copy, which goes to [insertTarget]
     * @param copyRead the read of the mapped copy's row the caller has just made, so one read decides both which
     *   request is built and what it says of the copy
     */
    private fun syncRequest(
        access: CalendarAccess, op: Op, table: Table, localEventId: Long, rowEventId: Long?, insertTarget: Long?,
        copyRead: RowRead? = null, asNewTarget: Boolean = false,
    ): Request {
        val state = access.store.current
        // [asNewTarget]: the Sync was tapped for a calendar other than the one the mapping names, and the question is
        // whether a first push THERE would be allowed — asked while the old mapping still stands (see syncRetargetCheck).
        val mapping = if (asNewTarget) null else state.mappings[localEventId]
        // The copy row is read once per request; [copyRead] is that read when the caller has just made it.
        val copyRow = if (mapping == null) null else copyRead ?: readRow(access, mapping.copyEventId)
        val source = eventCalendar(access, localEventId)
        val row = rowEventId?.let { eventRow(access, it) }
        val calendar = facts(access, if (rowEventId != null) row?.first else insertTarget)
        val mapped = mapping != null && rowEventId != null && (rowEventId == mapping.copyEventId || row?.second == mapping.copyEventId)
        return Request(
            Path.SYNC, op, table, calendar,
            sync = SyncFacts(
                sourceInTessera = source != null && CalendarWriteGuard.isTessera(source),
                mapped = mapped,
                targetAllowed = calendar != null && calendar.key in state.allowed,
                mappingTarget = mapping?.target,
                copyCalendarId = (copyRow as? RowRead.Found)?.calendarId,
                copyReadFailed = copyRow is RowRead.Failed,
                onExistingRow = rowEventId != null,
            ),
        )
    }

    /**
     * What the guard says of a Sync of [localEventId] to [target] now — asked before anything is compared or written,
     * so a target that became read-only, or a copy that moved, refuses the Sync even when nothing has changed (r3 D5,
     * T16-12).
     */
    fun syncCheck(access: CalendarAccess, localEventId: Long, target: CalendarKey): Verdict {
        val mapping = access.store.current.mappings[localEventId]
        // One read of the copy row: found, it is an update of that row; absent, a copy to make (again); failed, the
        // request says so and the guard refuses — a read that failed is never "the copy is gone" (F20).
        val copy = mapping?.let { readRow(access, it.copyEventId) }
        return CalendarWriteGuard.check(
            if (mapping != null && copy is RowRead.Found) syncRequest(access, Op.UPDATE, Table.EVENTS, localEventId, mapping.copyEventId, null, copyRead = copy)
            else syncRequest(access, Op.INSERT, Table.EVENTS, localEventId, null, target.id, copyRead = copy),
        )
    }

    /**
     * What the guard would say of a FIRST push of [localEventId] into [target], asked while a mapping to another
     * calendar still stands (fix round F20, trust review A-F7). A Sync tapped for another calendar starts a new copy
     * there and leaves the old one as the account event it is — but the old mapping may only be dropped once this says
     * Allowed: a Sync that is refused must leave the store as it found it.
     */
    fun syncRetargetCheck(access: CalendarAccess, localEventId: Long, target: CalendarKey): Verdict =
        CalendarWriteGuard.check(syncRequest(access, Op.INSERT, Table.EVENTS, localEventId, null, target.id, asNewTarget = true))

    /** What the guard says of deleting [localEventId]'s copy now (the delete choice's "both"). */
    fun syncDeleteCheck(access: CalendarAccess, localEventId: Long): Verdict {
        val mapping = access.store.current.mappings[localEventId] ?: return Verdict.Refused(Refusal.NOT_ALLOWED)
        return CalendarWriteGuard.check(syncRequest(access, Op.DELETE, Table.EVENTS, localEventId, mapping.copyEventId, null))
    }

    /**
     * The push: a normal insert (no `caller_is_syncadapter`) into the allowed target, so the provider marks the row
     * dirty and the account's own adapter uploads it. The mapping is recorded with the insert, so the copy's
     * reminders and exceptions are writes to a mapped row.
     */
    fun syncInsertCopy(access: CalendarAccess, localEventId: Long, target: CalendarKey, values: EventValues): WriteResult<Long> {
        val result = guarded(syncRequest(access, Op.INSERT, Table.EVENTS, localEventId, null, target.id)) {
            idOf(access.provider.insert(EVENTS, eventColumns(values).apply { put(CalendarContract.Events.CALENDAR_ID, target.id) }))
        }
        if (result is WriteResult.Ok) {
            access.store.update { SyncStateRules.map(it, SyncMapping(localEventId, target, result.value)) }
        }
        return result
    }

    fun syncUpdateCopy(access: CalendarAccess, localEventId: Long, copyEventId: Long, values: EventValues): WriteResult<Unit> {
        val columns = eventColumns(values)
        return guarded(syncRequest(access, Op.UPDATE, Table.EVENTS, localEventId, copyEventId, null).copy(columns = columns.keys.toSet())) {
            val rows = access.provider.update(eventUri(copyEventId), columns)
            if (rows != 1) error("the copy is gone (rows=$rows)")
        }
    }

    /** Deletes a mapped row: the copy (the delete choice's "both"), or one of its exception events before they are written again. */
    fun syncDeleteRow(access: CalendarAccess, localEventId: Long, rowEventId: Long): WriteResult<Unit> =
        guarded(syncRequest(access, Op.DELETE, Table.EVENTS, localEventId, rowEventId, null)) {
            access.provider.delete(eventUri(rowEventId))
            Unit
        }

    /** The copy's reminders (or one of its exceptions'), replaced by the local event's. */
    fun syncSetReminders(access: CalendarAccess, localEventId: Long, rowEventId: Long, reminders: List<Pair<Int, Int>>): WriteResult<Unit> =
        setReminders(access, rowEventId, reminders, clear = true) { op -> syncRequest(access, op, Table.REMINDERS, localEventId, rowEventId, null) }

    /** An exception of the copy's master: `ORIGINAL_ID` is the COPY's master, never the local one. */
    fun syncInsertException(access: CalendarAccess, localEventId: Long, copyMasterId: Long, originalInstanceTime: Long, values: ExceptionValues): WriteResult<Long> =
        guarded(syncRequest(access, Op.INSERT, Table.EVENTS, localEventId, copyMasterId, null)) { exception(access, copyMasterId, originalInstanceTime, values) }

    // ------------------------------------------------------------------------------------------ the reminder receiver

    /**
     * Case 4: the state of one alert row (FIRED, DISMISSED) and nothing else — no other column, no other table. The row
     * is named by [ReminderRules.stateSelection]: its `_id` together with the event, occurrence and alarm time it was an
     * alert of, and only while it is SCHEDULED or FIRED — an `_id` the provider has handed to another alert since
     * matches nothing. Returns the number of rows written (0 or 1).
     */
    fun setAlertState(access: CalendarAccess, alert: ReminderRules.AlertIdentity, state: Int): WriteResult<Int> {
        val columns = columns { put(CalendarContract.CalendarAlerts.STATE, state) }
        val request = Request(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns.keys.toSet())
        val selection = ReminderRules.stateSelection(alert)
        return logged(Path.RECEIVER, Op.UPDATE, "alert ${alert.id}", guarded(request) {
            access.provider.update(ProviderUri(ProviderTable.ALERTS), columns, selection.where, selection.args)
        })
    }
}
