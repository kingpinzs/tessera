package app.tileshell.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
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
 * Lines: the editor's and Tess's event writes log `[calendar] write <op> event=<id>: ok | failed <err> | failed
 * refused (not allowed)`; a refusal on any other path but Sync's logs the same refused line; Sync's results are the
 * `[calendar] sync …` lines its engine writes, one per tapped Sync.
 */
object CalendarWrites {

    // ------------------------------------------------------------------------------------------ facts, re-read

    private fun facts(context: Context, calendarId: Long?): CalendarFacts? = calendarId?.let { CalendarReads.calendar(context, it)?.facts }

    /** (calendar_id, original_id) of an event row, deleted or not; null when no such row. */
    private fun eventRow(context: Context, eventId: Long): Pair<Long, Long?>? = runCatching {
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.ORIGINAL_ID),
            "${CalendarContract.Events._ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) to (if (c.isNull(1)) null else c.getLong(1)) else null }
    }.getOrNull()

    /** A row's `_sync_id`, DTSTART and DURATION, for the two writes that must name them. */
    private data class SeriesRow(val syncId: String?, val dtstart: Long, val duration: String?)

    private fun seriesRow(context: Context, eventId: Long): SeriesRow? = runCatching {
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._SYNC_ID, CalendarContract.Events.DTSTART, CalendarContract.Events.DURATION),
            "${CalendarContract.Events._ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c -> if (c.moveToFirst()) SeriesRow(c.getString(0), c.getLong(1), c.getString(2)) else null }
    }.getOrNull()

    private fun eventCalendar(context: Context, eventId: Long): CalendarFacts? = facts(context, eventRow(context, eventId)?.first)

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

    private fun syncAdapterUri(base: Uri, accountName: String): Uri = base.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun eventColumns(v: EventValues): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.TITLE, v.title)
        put(CalendarContract.Events.EVENT_LOCATION, v.location)
        put(CalendarContract.Events.DESCRIPTION, v.description)
        put(CalendarContract.Events.DTSTART, v.dtstart)
        // DTEND or DURATION, never both: a repeating event carries its length as a duration (the provider's rule).
        if (v.dtend != null) {
            put(CalendarContract.Events.DTEND, v.dtend)
            putNull(CalendarContract.Events.DURATION)
        } else {
            putNull(CalendarContract.Events.DTEND)
            put(CalendarContract.Events.DURATION, v.duration)
        }
        put(CalendarContract.Events.ALL_DAY, if (v.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, v.timezone)
        if (v.rrule != null) put(CalendarContract.Events.RRULE, v.rrule) else putNull(CalendarContract.Events.RRULE)
        if (v.rdate != null) put(CalendarContract.Events.RDATE, v.rdate) else putNull(CalendarContract.Events.RDATE)
        if (v.exrule != null) put(CalendarContract.Events.EXRULE, v.exrule) else putNull(CalendarContract.Events.EXRULE)
        if (v.exdate != null) put(CalendarContract.Events.EXDATE, v.exdate) else putNull(CalendarContract.Events.EXDATE)
        put(CalendarContract.Events.AVAILABILITY, v.availability)
        if (v.status != null) put(CalendarContract.Events.STATUS, v.status)
    }

    private fun exceptionColumns(originalInstanceTime: Long, v: ExceptionValues): ContentValues = ContentValues().apply {
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

    private fun idOf(uri: Uri?): Long = uri?.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 } ?: error("the provider made no row")

    // ------------------------------------------------------------------------------------------ the two LOCAL calendars

    /**
     * Case 1's one calendar insert: the Tessera calendar, created by `LocalCalendar` as its own sync adapter (a LOCAL
     * account needs no Android account). Throws what the provider throws, and when the guard refuses.
     */
    fun createLocalCalendar(context: Context, accountName: String, color: Int): Uri? {
        val request = Request(
            Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS,
            CalendarFacts(0, accountName, CalendarContract.ACCOUNT_TYPE_LOCAL, CalendarContract.Calendars.CAL_ACCESS_OWNER),
            viaSyncAdapter = true,
        )
        check(CalendarWriteGuard.check(request) == Verdict.Allowed) { "the write guard refuses a calendar named $accountName" }
        return context.contentResolver.insert(
            syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, accountName),
            calendarColumns(accountName, accountName, CalendarContract.Calendars.CAL_ACCESS_OWNER, color),
        )
    }

    /**
     * Case 2's calendar insert: the read-only (access 200) LOCAL calendar "Birthdays" under its own account name, so
     * `LocalCalendar` — which finds Tessera by account name — can never pick it (T16-2 line 2).
     */
    fun createBirthdaysCalendar(context: Context, displayName: String, color: Int): Long {
        val account = CalendarWriteGuard.BIRTHDAYS_ACCOUNT
        val request = Request(
            Path.BIRTHDAYS, Op.INSERT, Table.CALENDARS,
            CalendarFacts(0, account, CalendarContract.ACCOUNT_TYPE_LOCAL, CalendarContract.Calendars.CAL_ACCESS_READ),
            viaSyncAdapter = true,
        )
        check(CalendarWriteGuard.check(request) == Verdict.Allowed) { "the write guard refuses the Birthdays calendar" }
        return idOf(
            context.contentResolver.insert(
                syncAdapterUri(CalendarContract.Calendars.CONTENT_URI, account),
                calendarColumns(account, displayName, CalendarContract.Calendars.CAL_ACCESS_READ, color),
            ),
        )
    }

    private fun calendarColumns(account: String, displayName: String, access: Int, color: Int) = ContentValues().apply {
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
    fun birthdayInsert(context: Context, calendarId: Long, values: EventValues): WriteResult<Long> {
        val request = Request(Path.BIRTHDAYS, Op.INSERT, Table.EVENTS, facts(context, calendarId), viaSyncAdapter = true)
        return logged(Path.BIRTHDAYS, Op.INSERT, "new", guarded(request) {
            idOf(
                context.contentResolver.insert(
                    syncAdapterUri(CalendarContract.Events.CONTENT_URI, CalendarWriteGuard.BIRTHDAYS_ACCOUNT),
                    eventColumns(values).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) },
                ),
            )
        })
    }

    fun birthdayDelete(context: Context, eventId: Long): WriteResult<Unit> {
        val request = Request(Path.BIRTHDAYS, Op.DELETE, Table.EVENTS, eventCalendar(context, eventId), viaSyncAdapter = true)
        return logged(Path.BIRTHDAYS, Op.DELETE, eventId.toString(), guarded(request) {
            context.contentResolver.delete(
                syncAdapterUri(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), CalendarWriteGuard.BIRTHDAYS_ACCOUNT), null, null,
            )
            Unit
        })
    }

    // ------------------------------------------------------------------------------------------ Tessera: the editor and Tess

    /** A new event, with its reminders as `Reminders` rows of `METHOD_ALERT`. */
    fun insertEvent(context: Context, path: Path, calendarId: Long, values: EventValues, reminderMinutes: List<Int>): WriteResult<Long> {
        val calendar = facts(context, calendarId)
        val result = guarded(Request(path, Op.INSERT, Table.EVENTS, calendar)) {
            idOf(context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventColumns(values).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) }))
        }
        if (result is WriteResult.Ok && reminderMinutes.isNotEmpty()) {
            val reminders = setReminders(context, path, result.value, reminderMinutes.map { it to CalendarContract.Reminders.METHOD_ALERT }, clear = false)
            if (reminders !is WriteResult.Ok) return logged(path, Op.INSERT, result.value.toString(), reminders.retype())
        }
        return logged(path, Op.INSERT, "new", result) { it.toString() }
    }

    /** An edit of an event row in place: the whole series ("all"), a single event, or an exception row. */
    fun updateEvent(context: Context, path: Path, eventId: Long, values: EventValues, reminderMinutes: List<Int>?): WriteResult<Unit> {
        val result = updateColumns(context, path, eventId, eventColumns(values).apply { remove(CalendarContract.Events.AVAILABILITY) })
        if (result is WriteResult.Ok && reminderMinutes != null) {
            val reminders = setReminders(context, path, eventId, reminderMinutes.map { it to CalendarContract.Reminders.METHOD_ALERT }, clear = true)
            if (reminders !is WriteResult.Ok) return logged(path, Op.UPDATE, eventId.toString(), reminders)
        }
        return logged(path, Op.UPDATE, eventId.toString(), result)
    }

    /**
     * "This and following", the old master's half: its rule ends before the occurrence. The row's own DTSTART and
     * DURATION are written with the rule, so the provider expands the series again.
     */
    fun setRule(context: Context, path: Path, eventId: Long, rrule: String): WriteResult<Unit> {
        val row = seriesRow(context, eventId)
        val columns = ContentValues().apply {
            put(CalendarContract.Events.RRULE, rrule)
            if (row != null) {
                put(CalendarContract.Events.DTSTART, row.dtstart)
                if (row.duration != null) put(CalendarContract.Events.DURATION, row.duration)
            }
        }
        return logged(path, Op.UPDATE, eventId.toString(), updateColumns(context, path, eventId, columns))
    }

    /** A row that is no longer there cannot be written: said as a failure, before the guard is asked about a calendar nobody can read. */
    private const val GONE = "the event is gone"

    private fun updateColumns(context: Context, path: Path, eventId: Long, columns: ContentValues): WriteResult<Unit> {
        if (eventRow(context, eventId) == null) return WriteResult.Failed(GONE)
        val request = Request(path, Op.UPDATE, Table.EVENTS, eventCalendar(context, eventId), columns.keySet())
        return guarded(request) {
            val rows = context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), columns, null, null)
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
    fun deleteEvent(context: Context, path: Path, eventId: Long): WriteResult<Unit> {
        val row = eventRow(context, eventId) ?: return logged(path, Op.DELETE, eventId.toString(), WriteResult.Failed(GONE))
        val calendar = facts(context, row.first)
        if (row.second != null) {
            val columns = ContentValues().apply { put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED) }
            return logged(path, Op.DELETE, eventId.toString(), guarded(Request(path, Op.DELETE, Table.EVENTS, calendar)) {
                val rows = context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), columns, null, null)
                if (rows != 1) error("no such event (rows=$rows)")
            })
        }
        return logged(path, Op.DELETE, eventId.toString(), guarded(Request(path, Op.DELETE, Table.EVENTS, calendar, viaSyncAdapter = true)) {
            val account = calendar?.accountName.orEmpty()
            context.contentResolver.delete(
                syncAdapterUri(CalendarContract.Events.CONTENT_URI, account), "${CalendarContract.Events.ORIGINAL_ID} = ?", arrayOf(eventId.toString()),
            )
            val rows = context.contentResolver.delete(syncAdapterUri(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), account), null, null)
            if (rows < 1) error("no such event (rows=$rows)")
        })
    }

    /** Removes one Tessera row for good, whatever it is: an exception row that has been written again elsewhere. */
    fun purgeRow(context: Context, path: Path, eventId: Long): WriteResult<Unit> {
        val calendar = eventCalendar(context, eventId)
        return guarded(Request(path, Op.DELETE, Table.EVENTS, calendar, viaSyncAdapter = true)) {
            context.contentResolver.delete(
                syncAdapterUri(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), calendar?.accountName.orEmpty()), null, null,
            )
            Unit
        }
    }

    /**
     * The provider pairs an occurrence's exception with its series by `_sync_id` (`ORIGINAL_SYNC_ID`): for a series
     * that has none, an exception insert loses the series' other occurrences from `Instances`
     * (qa/phase-16/dev-cal/P_EXCEPTION, P_EXCEPTION2). A LOCAL account's sync adapter is the app itself, so the shell
     * gives a Tessera series its `_sync_id` — once, before its first exception.
     */
    private fun ensureSyncId(context: Context, path: Path, masterId: Long): WriteResult<Unit> {
        if (seriesRow(context, masterId)?.syncId != null) return WriteResult.Ok(Unit)
        val calendar = eventCalendar(context, masterId)
        val columns = ContentValues().apply { put(CalendarContract.Events._SYNC_ID, "tessera-$masterId") }
        return guarded(Request(path, Op.UPDATE, Table.EVENTS, calendar, columns.keySet(), viaSyncAdapter = true)) {
            val rows = context.contentResolver.update(
                syncAdapterUri(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, masterId), calendar?.accountName.orEmpty()), columns, null, null,
            )
            if (rows != 1) error("no such event (rows=$rows)")
        }
    }

    /**
     * "Edit this occurrence" and "delete this occurrence": an exception event of [masterId] for the occurrence that
     * starts at [originalInstanceTime] — the provider clones the master, sets `ORIGINAL_ID` and `ORIGINAL_INSTANCE_TIME`
     * and applies [values]. [reminderMinutes] null keeps the reminders the provider copies from the master.
     */
    fun insertException(context: Context, path: Path, masterId: Long, originalInstanceTime: Long, values: ExceptionValues, reminderMinutes: List<Int>?): WriteResult<Long> {
        if (eventRow(context, masterId) == null) return logged(path, Op.INSERT, "new", WriteResult.Failed(GONE))
        val keyed = ensureSyncId(context, path, masterId)
        if (keyed !is WriteResult.Ok) return logged(path, Op.INSERT, "new", keyed.retype())
        val result = guarded(Request(path, Op.INSERT, Table.EVENTS, eventCalendar(context, masterId))) { exception(context, masterId, originalInstanceTime, values) }
        if (result is WriteResult.Ok && reminderMinutes != null) {
            val reminders = setReminders(context, path, result.value, reminderMinutes.map { it to CalendarContract.Reminders.METHOD_ALERT }, clear = true)
            if (reminders !is WriteResult.Ok) return logged(path, Op.INSERT, result.value.toString(), reminders.retype())
        }
        return logged(path, Op.INSERT, "new", result) { it.toString() }
    }

    private fun exception(context: Context, masterId: Long, originalInstanceTime: Long, values: ExceptionValues): Long = idOf(
        context.contentResolver.insert(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, masterId),
            exceptionColumns(originalInstanceTime, values),
        ),
    )

    /** The reminder rows of an event of [path]'s calendar: optionally cleared, then one row per (minutes, method). */
    private fun setReminders(context: Context, path: Path, eventId: Long, reminders: List<Pair<Int, Int>>, clear: Boolean, sync: SyncFacts? = null): WriteResult<Unit> {
        val calendar = eventCalendar(context, eventId)
        if (clear) {
            val cleared = guarded(Request(path, Op.DELETE, Table.REMINDERS, calendar, sync = sync)) {
                context.contentResolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
                Unit
            }
            if (cleared !is WriteResult.Ok) return cleared
        }
        for ((minutes, method) in reminders) {
            val inserted = guarded(Request(path, Op.INSERT, Table.REMINDERS, calendar, sync = sync)) {
                context.contentResolver.insert(
                    CalendarContract.Reminders.CONTENT_URI,
                    ContentValues().apply {
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
     */
    private fun syncRequest(context: Context, op: Op, table: Table, localEventId: Long, rowEventId: Long?, insertTarget: Long?): Request {
        val state = CalendarSyncStore.get(context).current
        val mapping = state.mappings[localEventId]
        val source = eventCalendar(context, localEventId)
        val row = rowEventId?.let { eventRow(context, it) }
        val calendar = facts(context, if (rowEventId != null) row?.first else insertTarget)
        val mapped = mapping != null && rowEventId != null && (rowEventId == mapping.copyEventId || row?.second == mapping.copyEventId)
        return Request(
            Path.SYNC, op, table, calendar,
            sync = SyncFacts(
                sourceInTessera = source != null && CalendarWriteGuard.isTessera(source),
                mapped = mapped,
                targetAllowed = calendar != null && CalendarKey(calendar.id, calendar.accountName.orEmpty(), calendar.accountType.orEmpty()) in state.allowed,
                mappingTarget = mapping?.target?.id,
                copyCalendarId = mapping?.let { eventRow(context, it.copyEventId)?.first },
            ),
        )
    }

    /**
     * What the guard says of a Sync of [localEventId] to [target] now — asked before anything is compared or written,
     * so a target that became read-only, or a copy that moved, refuses the Sync even when nothing has changed (r3 D5,
     * T16-12).
     */
    fun syncCheck(context: Context, localEventId: Long, target: CalendarKey): Verdict {
        val mapping = CalendarSyncStore.get(context).current.mappings[localEventId]
        val copyExists = mapping != null && eventRow(context, mapping.copyEventId) != null
        return CalendarWriteGuard.check(
            if (copyExists) syncRequest(context, Op.UPDATE, Table.EVENTS, localEventId, mapping!!.copyEventId, null)
            else syncRequest(context, Op.INSERT, Table.EVENTS, localEventId, null, target.id),
        )
    }

    /** What the guard says of deleting [localEventId]'s copy now (the delete choice's "both"). */
    fun syncDeleteCheck(context: Context, localEventId: Long): Verdict {
        val mapping = CalendarSyncStore.get(context).current.mappings[localEventId] ?: return Verdict.Refused(Refusal.NOT_ALLOWED)
        return CalendarWriteGuard.check(syncRequest(context, Op.DELETE, Table.EVENTS, localEventId, mapping.copyEventId, null))
    }

    /**
     * The push: a normal insert (no `caller_is_syncadapter`) into the allowed target, so the provider marks the row
     * dirty and the account's own adapter uploads it. The mapping is recorded with the insert, so the copy's
     * reminders and exceptions are writes to a mapped row.
     */
    fun syncInsertCopy(context: Context, localEventId: Long, target: CalendarKey, values: EventValues): WriteResult<Long> {
        val result = guarded(syncRequest(context, Op.INSERT, Table.EVENTS, localEventId, null, target.id)) {
            idOf(context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventColumns(values).apply { put(CalendarContract.Events.CALENDAR_ID, target.id) }))
        }
        if (result is WriteResult.Ok) {
            CalendarSyncStore.get(context).update { SyncStateRules.map(it, SyncMapping(localEventId, target, result.value)) }
        }
        return result
    }

    fun syncUpdateCopy(context: Context, localEventId: Long, copyEventId: Long, values: EventValues): WriteResult<Unit> {
        val columns = eventColumns(values)
        return guarded(syncRequest(context, Op.UPDATE, Table.EVENTS, localEventId, copyEventId, null).copy(columns = columns.keySet())) {
            val rows = context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, copyEventId), columns, null, null)
            if (rows != 1) error("the copy is gone (rows=$rows)")
        }
    }

    /** Deletes a mapped row: the copy (the delete choice's "both"), or one of its exception events before they are written again. */
    fun syncDeleteRow(context: Context, localEventId: Long, rowEventId: Long): WriteResult<Unit> =
        guarded(syncRequest(context, Op.DELETE, Table.EVENTS, localEventId, rowEventId, null)) {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rowEventId), null, null)
            Unit
        }

    /** The copy's reminders (or one of its exceptions'), replaced by the local event's. */
    fun syncSetReminders(context: Context, localEventId: Long, rowEventId: Long, reminders: List<Pair<Int, Int>>): WriteResult<Unit> {
        val request = syncRequest(context, Op.INSERT, Table.REMINDERS, localEventId, rowEventId, null)
        return setReminders(context, Path.SYNC, rowEventId, reminders, clear = true, sync = request.sync)
    }

    /** An exception of the copy's master: `ORIGINAL_ID` is the COPY's master, never the local one. */
    fun syncInsertException(context: Context, localEventId: Long, copyMasterId: Long, originalInstanceTime: Long, values: ExceptionValues): WriteResult<Long> =
        guarded(syncRequest(context, Op.INSERT, Table.EVENTS, localEventId, copyMasterId, null)) { exception(context, copyMasterId, originalInstanceTime, values) }

    // ------------------------------------------------------------------------------------------ the reminder receiver

    /**
     * Case 4: the state of one alert row (FIRED, DISMISSED) and nothing else — no other column, no other table. The row
     * is named by [ReminderRules.stateSelection]: its `_id` together with the event, occurrence and alarm time it was an
     * alert of, and only while it is SCHEDULED or FIRED — an `_id` the provider has handed to another alert since
     * matches nothing. Returns the number of rows written (0 or 1).
     */
    fun setAlertState(context: Context, alert: ReminderRules.AlertIdentity, state: Int): WriteResult<Int> {
        val columns = ContentValues().apply { put(CalendarContract.CalendarAlerts.STATE, state) }
        val request = Request(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns.keySet())
        val selection = ReminderRules.stateSelection(alert)
        return logged(Path.RECEIVER, Op.UPDATE, "alert ${alert.id}", guarded(request) {
            context.contentResolver.update(CalendarContract.CalendarAlerts.CONTENT_URI, columns, selection.where, selection.args.toTypedArray())
        })
    }
}
