package app.tileshell.calendar

/**
 * The calendar write guard (phase 16 Decisions T16-11 with r3 D1 / D6; the Trust line (a)): the one rule every
 * `CalendarContract` insert, update and delete of the shell is put to before it reaches the provider. It is free of
 * Android types so the JVM test covers every case; [CalendarWrites] is the one layer that asks it, with facts it has
 * just re-read from the provider.
 *
 * The owner, 2026-09-23: "I dont want to add anything to my work calander from my phone ever." So nothing is allowed
 * by default, and the allow set is exactly four cases:
 *
 *  1. the `Tessera` calendar (the shell's own LOCAL calendar): every event and reminder op of the editor and of Tess
 *     as a normal app; as the LOCAL account's own sync adapter only the two writes the provider needs it for — the
 *     delete of an event row (editor, Tess) and the editor's update of an event's `_sync_id` alone; and the one
 *     insert that creates the calendar;
 *  2. the `Tessera Birthdays` calendar: the Birthdays writer's path only, through the sync-adapter URI;
 *  3. an allowed Sync target: only a push, update or delete of a copy `calendar_sync.json` maps, only while the copy's
 *     re-read `calendar_id` equals the mapping's target, the target is still on the allowed list and its re-read
 *     access level is at least CONTRIBUTOR (500);
 *  4. the reminder receiver's update of `CalendarAlerts.STATE`, and no other column or table.
 *
 * Every other combination is refused — and whatever the case, an update that names `calendar_id` among its columns
 * (a row moved to another calendar) is refused for every path.
 */
object CalendarWriteGuard {
    /** Who is writing: the code path, never a value a caller outside the shell can choose. */
    enum class Path {
        /** The Calendar app's editor and event page (create, edit, occurrence edits, delete). */
        EDITOR,
        /** Tess's "add … to my calendar" and "delete the event …". */
        TESS,
        /** `LocalCalendar`'s creation of the Tessera calendar. */
        LOCAL_CALENDAR,
        /** The Birthdays writer. */
        BIRTHDAYS,
        /** The tapped Sync: the copy in an allowed account calendar. */
        SYNC,
        /** The reminder receiver. */
        RECEIVER,
    }

    enum class Op(val word: String) { INSERT("insert"), UPDATE("update"), DELETE("delete") }

    enum class Table { CALENDARS, EVENTS, REMINDERS, CALENDAR_ALERTS, OTHER }

    /** A calendar row as the write layer has just re-read it. */
    data class CalendarFacts(val id: Long, val accountName: String?, val accountType: String?, val accessLevel: Int)

    /**
     * What Sync's write is about, each fact re-read just before the guard is asked.
     *
     * @param sourceInTessera the local event being pushed is an event of the Tessera calendar
     * @param mapped `calendar_sync.json` maps the row this write touches — the copy, an exception of the copy, or
     *   their reminders. False for the first insert of a copy (there is nothing to map yet)
     * @param targetAllowed the calendar's `_ID` + account name + account type is on the allowed list now
     * @param mappingTarget the calendar id the mapping names as its target; null when no mapping exists yet
     * @param copyCalendarId the copy's `calendar_id` as re-read from the provider; null when there is no copy row
     */
    data class SyncFacts(
        val sourceInTessera: Boolean,
        val mapped: Boolean,
        val targetAllowed: Boolean,
        val mappingTarget: Long?,
        val copyCalendarId: Long?,
    )

    /**
     * One write.
     *
     * @param calendar the calendar the written row belongs to (for a calendar row, the calendar itself); null when
     *   it could not be read — it is gone, or the write is to a table that has no calendar (the alerts)
     * @param columns the columns an update sets
     * @param viaSyncAdapter the write goes through the `caller_is_syncadapter` URI
     */
    data class Request(
        val path: Path,
        val op: Op,
        val table: Table,
        val calendar: CalendarFacts?,
        val columns: Set<String> = emptySet(),
        val viaSyncAdapter: Boolean = false,
        val sync: SyncFacts? = null,
    )

    enum class Refusal(val text: String) {
        /** The default: no case of the allow set covers this write. */
        NOT_ALLOWED("refused (not allowed)"),
        /** T16-12: the copy is no longer in the calendar the mapping names. */
        MAPPING_STALE("failed mapping stale"),
        /** r3 D5: the target's access level is below CONTRIBUTOR now. */
        READ_ONLY("failed calendar read-only"),
        /** T16-1: the Sync target is no longer on this phone. */
        CALENDAR_GONE("failed calendar gone"),
    }

    sealed interface Verdict {
        data object Allowed : Verdict
        data class Refused(val why: Refusal) : Verdict
    }

    /** CalendarContract.ACCOUNT_TYPE_LOCAL. */
    const val ACCOUNT_TYPE_LOCAL = "LOCAL"

    /** `LocalCalendar.ACCOUNT_NAME`: the shell's own calendar. */
    const val TESSERA_ACCOUNT = "Tessera"

    /** The Birthdays calendar's own LOCAL account name — never `Tessera`, so `LocalCalendar` cannot pick it (T16-2). */
    const val BIRTHDAYS_ACCOUNT = "Tessera Birthdays"

    /** CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR: the lowest level a Sync target may have (r3 D5). */
    const val ACCESS_CONTRIBUTOR = 500

    /** CalendarContract.CalendarAlerts.STATE: the one column the receiver may set. */
    const val ALERT_STATE_COLUMN = "state"

    /** CalendarContract.Events.CALENDAR_ID: no update of the shell may set it — a row never changes calendars. */
    const val CALENDAR_ID_COLUMN = "calendar_id"

    /** CalendarContract.Events._SYNC_ID: the one column the editor may set as Tessera's own sync adapter. */
    const val SYNC_ID_COLUMN = "_sync_id"

    fun isTessera(c: CalendarFacts): Boolean = c.accountType == ACCOUNT_TYPE_LOCAL && c.accountName == TESSERA_ACCOUNT

    fun isBirthdays(c: CalendarFacts): Boolean = c.accountType == ACCOUNT_TYPE_LOCAL && c.accountName == BIRTHDAYS_ACCOUNT

    fun check(r: Request): Verdict {
        // No write of the shell moves a row to another calendar: the provider would let a sync adapter re-home an
        // event with one column, so the column is refused in every update, whoever asks and whatever the calendar.
        if (r.op == Op.UPDATE && CALENDAR_ID_COLUMN in r.columns) return refused(Refusal.NOT_ALLOWED)
        // Case 4, and the receiver's only case: the state of an alert row. `calendar_alerts` is a table local to the
        // phone that no sync adapter uploads; any other column, table or op by the receiver is refused.
        if (r.path == Path.RECEIVER) {
            val stateOnly = r.op == Op.UPDATE && r.table == Table.CALENDAR_ALERTS && r.columns == setOf(ALERT_STATE_COLUMN) && !r.viaSyncAdapter
            return if (stateOnly) Verdict.Allowed else refused(Refusal.NOT_ALLOWED)
        }
        // Nobody but the receiver writes the alerts, and no path writes a table this rule does not name.
        if (r.table == Table.CALENDAR_ALERTS || r.table == Table.OTHER) return refused(Refusal.NOT_ALLOWED)

        val calendar = r.calendar ?: return refused(if (r.path == Path.SYNC) Refusal.CALENDAR_GONE else Refusal.NOT_ALLOWED)
        return when {
            isTessera(calendar) -> tessera(r)
            isBirthdays(calendar) -> birthdays(r)
            else -> account(r, calendar)
        }
    }

    /** Case 1. */
    private fun tessera(r: Request): Verdict = when (r.path) {
        // The calendar row itself is only ever created, by its one creator, as its own sync adapter.
        Path.LOCAL_CALENDAR -> allowIf(r.table == Table.CALENDARS && r.op == Op.INSERT && r.viaSyncAdapter)
        Path.EDITOR, Path.TESS -> when {
            // As a normal app: events and their reminders, every op.
            !r.viaSyncAdapter -> allowIf(r.table == Table.EVENTS || r.table == Table.REMINDERS)
            // As the calendar's own sync adapter (a LOCAL account's adapter is the app itself), only the two writes
            // the provider needs an adapter for (qa/phase-16/dev-cal/P_EXCEPTION*): it removes a row for good only
            // for its adapter, and it pairs an occurrence's exception with its series by `_sync_id`, which the editor
            // gives a series before its first exception. No insert, and no other column.
            r.table != Table.EVENTS -> refused(Refusal.NOT_ALLOWED)
            r.op == Op.DELETE -> Verdict.Allowed
            r.op == Op.UPDATE -> allowIf(r.path == Path.EDITOR && r.columns == setOf(SYNC_ID_COLUMN))
            else -> refused(Refusal.NOT_ALLOWED)
        }
        else -> refused(Refusal.NOT_ALLOWED)
    }

    /** Case 2: one code path, and no reminder rows (T16-4). */
    private fun birthdays(r: Request): Verdict = allowIf(
        r.path == Path.BIRTHDAYS && r.viaSyncAdapter &&
            ((r.table == Table.CALENDARS && r.op == Op.INSERT) || r.table == Table.EVENTS),
    )

    /** Case 3: every other calendar is an account's (or another app's LOCAL one) and only a tapped Sync may reach it. */
    private fun account(r: Request, calendar: CalendarFacts): Verdict {
        if (r.path != Path.SYNC) return refused(Refusal.NOT_ALLOWED)
        // A copy is a normal app's row, so the account's own adapter uploads it; never the sync-adapter URI.
        if (r.viaSyncAdapter) return refused(Refusal.NOT_ALLOWED)
        if (r.table != Table.EVENTS && r.table != Table.REMINDERS) return refused(Refusal.NOT_ALLOWED)
        // "Can sync to" lists the non-LOCAL calendars only: another app's LOCAL calendar is never a target.
        if (calendar.accountType == ACCOUNT_TYPE_LOCAL) return refused(Refusal.NOT_ALLOWED)
        val sync = r.sync ?: return refused(Refusal.NOT_ALLOWED)
        // Sync only ever copies an event of the shell's own calendar.
        if (!sync.sourceInTessera) return refused(Refusal.NOT_ALLOWED)
        // Only the first insert of a copy may touch a row the store does not map.
        val firstInsert = r.op == Op.INSERT && r.table == Table.EVENTS && !sync.mapped
        if (!firstInsert && !sync.mapped) return refused(Refusal.NOT_ALLOWED)
        // T16-12: the copy must still be where the mapping says, and the write must be to that calendar.
        if (sync.mappingTarget != null && sync.mappingTarget != calendar.id) return refused(Refusal.MAPPING_STALE)
        if (sync.copyCalendarId != null && sync.copyCalendarId != sync.mappingTarget) return refused(Refusal.MAPPING_STALE)
        if (sync.mapped && sync.mappingTarget == null) return refused(Refusal.MAPPING_STALE)
        if (!sync.targetAllowed) return refused(Refusal.NOT_ALLOWED)
        // r3 D5: the provider accepts a normal insert whatever the level, and the copy would sit dirty and never upload.
        if (calendar.accessLevel < ACCESS_CONTRIBUTOR) return refused(Refusal.READ_ONLY)
        return Verdict.Allowed
    }

    private fun allowIf(condition: Boolean): Verdict = if (condition) Verdict.Allowed else refused(Refusal.NOT_ALLOWED)

    private fun refused(why: Refusal): Verdict = Verdict.Refused(why)
}
