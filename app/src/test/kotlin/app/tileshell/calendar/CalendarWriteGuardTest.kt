package app.tileshell.calendar

import app.tileshell.calendar.CalendarWriteGuard.CalendarFacts
import app.tileshell.calendar.CalendarWriteGuard.Op
import app.tileshell.calendar.CalendarWriteGuard.Path
import app.tileshell.calendar.CalendarWriteGuard.Refusal
import app.tileshell.calendar.CalendarWriteGuard.Request
import app.tileshell.calendar.CalendarWriteGuard.SyncFacts
import app.tileshell.calendar.CalendarWriteGuard.Table
import app.tileshell.calendar.CalendarWriteGuard.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 16 build task 3: the calendar write guard (Decisions T16-11 with r3 D1 / D6; the Trust line (a); E22's list).
 * Each of the four allowed cases, and the refusal of every other combination the doc lists.
 */
class CalendarWriteGuardTest {
    private val tessera = CalendarFacts(1, "Tessera", "LOCAL", 700, "Tessera")
    private val birthdays = CalendarFacts(2, "Tessera Birthdays", "LOCAL", 200, "Birthdays")
    private val personal = CalendarFacts(5, "qa.personal@example.com", "com.google", 700, "personal-cal")
    private val work = CalendarFacts(6, "qa.work@example.com", "com.google", 700, "work-cal")
    private val qaLocal = CalendarFacts(7, "qa", "LOCAL", 700, "qa")

    /** Account calendars whose account is NAMED like the shell's two (a CalDAV account can be named anything): F18. */
    private val davNamedTessera = CalendarFacts(8, "Tessera", "com.example.dav", 700, "Tessera")
    private val googleNamedBirthdays = CalendarFacts(9, "Tessera Birthdays", "com.google", 700, "Birthdays")

    private val allowed = Verdict.Allowed
    private fun refused(why: Refusal = Refusal.NOT_ALLOWED) = Verdict.Refused(why)

    private fun check(path: Path, op: Op, table: Table, calendar: CalendarFacts?, columns: Set<String> = emptySet(), syncAdapter: Boolean = false, sync: SyncFacts? = null) =
        CalendarWriteGuard.check(Request(path, op, table, calendar, columns, syncAdapter, sync))

    /** A mapped copy sitting in the allowed calendar the mapping names. */
    private fun mappedCopy(target: CalendarFacts = personal) =
        SyncFacts(sourceInTessera = true, mapped = true, targetAllowed = true, mappingTarget = target.key, copyCalendarId = target.id, copyReadFailed = false, onExistingRow = true)

    /** The first push: nothing mapped yet. */
    private val firstPush = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = null, copyCalendarId = null, copyReadFailed = false, onExistingRow = false)

    // ---- case 1: Tessera, every op ----

    @Test fun theEditorMayInsertUpdateAndDeleteEventsAndRemindersInTessera() {
        for (op in Op.entries) for (table in listOf(Table.EVENTS, Table.REMINDERS)) {
            assertEquals("$op $table", allowed, check(Path.EDITOR, op, table, tessera))
        }
    }

    @Test fun tessMayInsertIntoTessera() {
        assertEquals(allowed, check(Path.TESS, Op.INSERT, Table.EVENTS, tessera))
    }

    @Test fun tessDeleteOfATesseraEventIsAllowed() {
        assertEquals(allowed, check(Path.TESS, Op.DELETE, Table.EVENTS, tessera))
    }

    @Test fun localCalendarMayCreateTesseraAsItsOwnSyncAdapterAndNothingElse() {
        assertEquals(allowed, check(Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS, tessera, syncAdapter = true))
        // Not without the sync-adapter URI, never an update or a delete of the calendar, never an event.
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS, tessera))
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.DELETE, Table.CALENDARS, tessera, syncAdapter = true))
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.UPDATE, Table.CALENDARS, tessera, syncAdapter = true))
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.INSERT, Table.EVENTS, tessera, syncAdapter = true))
        // And only under the name Tessera: any other calendar is not its to create.
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS, birthdays, syncAdapter = true))
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS, personal, syncAdapter = true))
        assertEquals(refused(), check(Path.LOCAL_CALENDAR, Op.INSERT, Table.CALENDARS, qaLocal, syncAdapter = true))
    }

    @Test fun nobodyDeletesOrRewritesTheTesseraCalendarRow() {
        for (path in Path.entries) for (op in listOf(Op.UPDATE, Op.DELETE)) {
            assertEquals("$path $op", refused(), check(path, op, Table.CALENDARS, tessera))
            assertEquals("$path $op (sync adapter)", refused(), check(path, op, Table.CALENDARS, tessera, syncAdapter = true))
        }
    }

    @Test fun tesserasEventRowsMayBeWrittenAsItsOwnSyncAdapterAndNothingElseMay() {
        // The LOCAL account's sync adapter is the shell itself, and it is needed for two writes only (fix round F19):
        // a row removed for good — the editor's delete and Tess's —
        assertEquals(allowed, check(Path.EDITOR, Op.DELETE, Table.EVENTS, tessera, syncAdapter = true))
        assertEquals(allowed, check(Path.TESS, Op.DELETE, Table.EVENTS, tessera, syncAdapter = true))
        // and a series given its _sync_id before its first exception, by the editor, that column alone.
        assertEquals(allowed, check(Path.EDITOR, Op.UPDATE, Table.EVENTS, tessera, columns = setOf("_sync_id"), syncAdapter = true))
        // Nothing else: no insert, no update of another column or of more than that one, and never by Tess.
        for (path in listOf(Path.EDITOR, Path.TESS)) {
            assertEquals("$path insert", refused(), check(path, Op.INSERT, Table.EVENTS, tessera, syncAdapter = true))
            assertEquals("$path insert", refused(), check(path, Op.INSERT, Table.EVENTS, tessera, columns = setOf("_sync_id"), syncAdapter = true))
            for (columns in listOf(emptySet(), setOf("title"), setOf("_sync_id", "title"), setOf("calendar_id"), setOf("_sync_id", "calendar_id"), setOf("deleted"), setOf("dirty"))) {
                assertEquals("$path update $columns", refused(), check(path, Op.UPDATE, Table.EVENTS, tessera, columns = columns, syncAdapter = true))
            }
        }
        assertEquals(refused(), check(Path.TESS, Op.UPDATE, Table.EVENTS, tessera, columns = setOf("_sync_id"), syncAdapter = true))
        // Reminder rows are a normal app's, and the calendar row is nobody's to rewrite.
        for (path in listOf(Path.EDITOR, Path.TESS)) for (op in Op.entries) {
            assertEquals("$path $op", refused(), check(path, op, Table.REMINDERS, tessera, columns = setOf("_sync_id"), syncAdapter = true))
            assertEquals("$path $op", refused(), check(path, op, Table.CALENDARS, tessera, columns = setOf("_sync_id"), syncAdapter = true))
        }
        // And the sync-adapter URI opens no other calendar to the editor or Tess — not one whose account is only named Tessera.
        for (calendar in listOf(birthdays, personal, work, qaLocal, davNamedTessera, googleNamedBirthdays)) for (op in Op.entries) {
            for (columns in listOf(emptySet(), setOf("_sync_id"))) {
                assertEquals("$op ${calendar.accountName}", refused(), check(Path.EDITOR, op, Table.EVENTS, calendar, columns = columns, syncAdapter = true))
                assertEquals("$op ${calendar.accountName}", refused(), check(Path.TESS, op, Table.EVENTS, calendar, columns = columns, syncAdapter = true))
            }
        }
    }

    @Test fun noUpdateMaySetCalendarIdWhoeverAsks() {
        // A row never changes calendars (fix round F19): calendar_id among an update's columns is refused on every
        // path, in every calendar, through either URI — also where the same update without it is allowed.
        assertEquals(allowed, check(Path.EDITOR, Op.UPDATE, Table.EVENTS, tessera, columns = setOf("title")))
        assertEquals(allowed, check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, columns = setOf("title"), sync = mappedCopy()))
        assertEquals(allowed, check(Path.BIRTHDAYS, Op.UPDATE, Table.EVENTS, birthdays, columns = setOf("title"), syncAdapter = true))
        assertEquals(refused(), check(Path.EDITOR, Op.UPDATE, Table.EVENTS, tessera, columns = setOf("title", "calendar_id")))
        assertEquals(refused(), check(Path.TESS, Op.UPDATE, Table.EVENTS, tessera, columns = setOf("calendar_id")))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, columns = setOf("title", "calendar_id"), sync = mappedCopy()))
        assertEquals(refused(), check(Path.BIRTHDAYS, Op.UPDATE, Table.EVENTS, birthdays, columns = setOf("title", "calendar_id"), syncAdapter = true))
        for (path in Path.entries) for (table in Table.entries) for (adapter in listOf(false, true)) {
            for (calendar in listOf(tessera, birthdays, personal, work, qaLocal, davNamedTessera, null)) for (sync in listOf(null, firstPush, mappedCopy())) {
                for (columns in listOf(setOf("calendar_id"), setOf("calendar_id", "state"), setOf("_sync_id", "calendar_id"), setOf("title", "calendar_id"))) {
                    assertEquals("$path $table ${calendar?.accountName} adapter=$adapter $columns", refused(), check(path, Op.UPDATE, table, calendar, columns, adapter, sync))
                }
            }
        }
    }

    // ---- an account calendar whose account is only NAMED like the shell's (fix round F18) ----

    @Test fun aNonLocalCalendarWhoseAccountIsNamedTesseraOrTesseraBirthdaysIsAnAccountCalendar() {
        for (calendar in listOf(davNamedTessera, googleNamedBirthdays)) {
            assertEquals(false, CalendarWriteGuard.isTessera(calendar))
            assertEquals(false, CalendarWriteGuard.isBirthdays(calendar))
            // Refused on every path but Sync: every op, every table that has a calendar (the alerts have none: the
            // receiver's case 4 is not a write to any calendar), either URI, whatever columns.
            val tables = listOf(Table.CALENDARS, Table.EVENTS, Table.REMINDERS, Table.OTHER)
            for (path in Path.entries.filter { it != Path.SYNC }) for (op in Op.entries) for (table in tables) for (adapter in listOf(false, true)) {
                for (columns in listOf(emptySet(), setOf("state"), setOf("_sync_id"), setOf("title"))) {
                    assertEquals("$path $op $table ${calendar.accountName} adapter=$adapter $columns", refused(), check(path, op, table, calendar, columns, adapter))
                }
            }
            // It is an account calendar like any other: only a tapped Sync into it, once it is allowed, may write it —
            assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, calendar, sync = firstPush))
            assertEquals(allowed, check(Path.SYNC, Op.UPDATE, Table.EVENTS, calendar, sync = mappedCopy(calendar)))
            // and not while it is not on the allowed list.
            assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, calendar, sync = firstPush.copy(targetAllowed = false)))
            assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, calendar, sync = mappedCopy(calendar).copy(targetAllowed = false)))
        }
    }

    // ---- case 2: Tessera Birthdays, the Birthdays writer's path only ----

    @Test fun theBirthdaysWriterMayCreateItsCalendarAndWriteItsEvents() {
        assertEquals(allowed, check(Path.BIRTHDAYS, Op.INSERT, Table.CALENDARS, birthdays, syncAdapter = true))
        for (op in Op.entries) assertEquals("$op", allowed, check(Path.BIRTHDAYS, op, Table.EVENTS, birthdays, syncAdapter = true))
    }

    @Test fun theBirthdaysWriterWritesNoReminderRowsAndOnlyThroughTheSyncAdapterUri() {
        assertEquals(refused(), check(Path.BIRTHDAYS, Op.INSERT, Table.REMINDERS, birthdays, syncAdapter = true))
        assertEquals(refused(), check(Path.BIRTHDAYS, Op.INSERT, Table.EVENTS, birthdays))
        assertEquals(refused(), check(Path.BIRTHDAYS, Op.DELETE, Table.CALENDARS, birthdays, syncAdapter = true))
    }

    @Test fun theEditorIsRefusedOnBirthdays() {
        for (op in Op.entries) for (table in listOf(Table.EVENTS, Table.REMINDERS)) {
            assertEquals("$op $table", refused(), check(Path.EDITOR, op, table, birthdays))
        }
    }

    @Test fun tessAndSyncAreRefusedOnBirthdays() {
        assertEquals(refused(), check(Path.TESS, Op.DELETE, Table.EVENTS, birthdays))
        assertEquals(refused(), check(Path.TESS, Op.INSERT, Table.EVENTS, birthdays))
        // Birthdays is never a Sync target, whatever the store says.
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, birthdays, sync = firstPush))
    }

    @Test fun theBirthdaysWriterIsRefusedOnTessera() {
        for (op in Op.entries) {
            assertEquals("$op", refused(), check(Path.BIRTHDAYS, op, Table.EVENTS, tessera, syncAdapter = true))
            assertEquals("$op (normal)", refused(), check(Path.BIRTHDAYS, op, Table.EVENTS, tessera))
        }
    }

    @Test fun theBirthdaysWriterIsRefusedOnAnAccountCalendar() {
        assertEquals(refused(), check(Path.BIRTHDAYS, Op.INSERT, Table.EVENTS, personal, syncAdapter = true))
    }

    // ---- case 3: an allowed Sync target, a mapped copy only ----

    @Test fun syncMayPushANewCopyIntoAnAllowedTarget() {
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = firstPush))
    }

    @Test fun syncMayUpdateAndDeleteAMappedCopyAndWriteItsReminders() {
        assertEquals(allowed, check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = mappedCopy()))
        assertEquals(allowed, check(Path.SYNC, Op.DELETE, Table.EVENTS, personal, sync = mappedCopy()))
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.REMINDERS, personal, sync = mappedCopy()))
        assertEquals(allowed, check(Path.SYNC, Op.DELETE, Table.REMINDERS, personal, sync = mappedCopy()))
        // An exception of the mapped copy is an insert on a mapped row.
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = mappedCopy()))
    }

    @Test fun syncMayRecreateACopyThatIsGoneInTheMappedTarget() {
        val recreate = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = personal.key, copyCalendarId = null, copyReadFailed = false, onExistingRow = false)
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = recreate))
    }

    @Test fun syncToAnIdNotAllowedIsRefused() {
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, work, sync = firstPush.copy(targetAllowed = false)))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, work, sync = mappedCopy(work).copy(targetAllowed = false)))
        assertEquals(refused(), check(Path.SYNC, Op.DELETE, Table.EVENTS, work, sync = mappedCopy(work).copy(targetAllowed = false)))
    }

    @Test fun syncToAnAllowedIdButAnUnmappedEventIsRefused() {
        val unmapped = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = null, copyCalendarId = null, copyReadFailed = false, onExistingRow = true)
        // An exception event is an insert that hangs on an existing row: on a row the store does not map it is refused,
        // with no mapping at all and with one that names this very calendar — it is not "the first insert of a copy".
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = unmapped))
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = unmapped.copy(mappingTarget = personal.key, copyCalendarId = personal.id)))
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = unmapped.copy(onExistingRow = false)))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = unmapped))
        assertEquals(refused(), check(Path.SYNC, Op.DELETE, Table.EVENTS, personal, sync = unmapped))
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.REMINDERS, personal, sync = unmapped))
        assertEquals(refused(), check(Path.SYNC, Op.DELETE, Table.REMINDERS, personal, sync = unmapped))
    }

    @Test fun syncOfAnEventThatIsNotInTesseraIsRefused() {
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = firstPush.copy(sourceInTessera = false)))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = mappedCopy().copy(sourceInTessera = false)))
    }

    @Test fun syncWithNoFactsIsRefused() {
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, personal))
    }

    @Test fun syncToATargetWhoseAccessLevelFellBelow500IsRefusedAsReadOnly() {
        val readOnly = personal.copy(accessLevel = 200)
        assertEquals(refused(Refusal.READ_ONLY), check(Path.SYNC, Op.INSERT, Table.EVENTS, readOnly, sync = firstPush))
        assertEquals(refused(Refusal.READ_ONLY), check(Path.SYNC, Op.UPDATE, Table.EVENTS, readOnly, sync = mappedCopy()))
        assertEquals(refused(Refusal.READ_ONLY), check(Path.SYNC, Op.DELETE, Table.EVENTS, readOnly, sync = mappedCopy()))
        // 500 (CONTRIBUTOR) is the floor.
        assertEquals(allowed, check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal.copy(accessLevel = 500), sync = mappedCopy()))
        assertEquals(refused(Refusal.READ_ONLY), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal.copy(accessLevel = 499), sync = mappedCopy()))
    }

    @Test fun aStaleMappingIsRefused() {
        // T16-12: the copy was moved to Work on the other side. The row now sits in Work; the mapping still names Personal.
        val moved = SyncFacts(sourceInTessera = true, mapped = true, targetAllowed = false, mappingTarget = personal.key, copyCalendarId = work.id, copyReadFailed = false, onExistingRow = true)
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, work, sync = moved))
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.DELETE, Table.EVENTS, work, sync = moved))
        // Even when the calendar it moved to is itself allowed, the mapping is stale and nothing is written.
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, work, sync = moved.copy(targetAllowed = true)))
        // A write aimed at the mapping's target while the copy sits elsewhere is stale too.
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = moved.copy(targetAllowed = true)))
        // A new copy may not be started in another calendar while a mapping names one.
        val elsewhere = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = personal.key, copyCalendarId = personal.id, copyReadFailed = false, onExistingRow = false)
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.INSERT, Table.EVENTS, work, sync = elsewhere))
    }

    @Test fun aMappingNamesItsTargetByItsWholeKeyNotByTheIdAlone() {
        // F16: the mapping's target was "Personal" under id 5. The account's calendars were re-made and id 5 is now
        // another calendar of the same account (its own name differs): the copy's mapping is stale, whatever is allowed.
        val reused = personal.copy(name = "team-cal")
        assertEquals(allowed, check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = mappedCopy(personal)))
        for (op in Op.entries) {
            assertEquals("$op", refused(Refusal.MAPPING_STALE), check(Path.SYNC, op, Table.EVENTS, reused, sync = mappedCopy(personal)))
            assertEquals("$op", refused(Refusal.MAPPING_STALE), check(Path.SYNC, op, Table.REMINDERS, reused, sync = mappedCopy(personal)))
        }
        // A copy that is gone is not made again in the calendar that took the id either.
        val recreate = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = personal.key, copyCalendarId = null, copyReadFailed = false, onExistingRow = false)
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.INSERT, Table.EVENTS, reused, sync = recreate))
        // The same id under another account, or another account type, is not the mapped calendar either.
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal.copy(accountName = "qa.work@example.com"), sync = mappedCopy(personal)))
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal.copy(accountType = "com.example"), sync = mappedCopy(personal)))
        // A mapping from a file written before the key carried the name (null) names no calendar at all.
        val old = mappedCopy(personal).copy(mappingTarget = personal.key.copy(name = null))
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = old))
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.DELETE, Table.EVENTS, personal, sync = old))
    }

    @Test fun aCopyRowThatCouldNotBeReadIsNeverTakenForNoCopy() {
        // F20 (A-F9): the mapping names Personal and the read of its copy row failed. Before, that read as "there is
        // no copy" and a new one was allowed — a second copy beside the first.
        val unread = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = personal.key, copyCalendarId = null, copyReadFailed = true, onExistingRow = false)
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = unread.copy(copyReadFailed = false)))
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = unread))
        // And nothing else is written either, whatever the rest of the facts say.
        for (op in Op.entries) for (table in listOf(Table.EVENTS, Table.REMINDERS)) {
            assertEquals("$op $table", refused(), check(Path.SYNC, op, table, personal, sync = mappedCopy().copy(copyReadFailed = true)))
        }
    }

    @Test fun aSyncTargetThatIsGoneIsRefused() {
        assertEquals(refused(Refusal.CALENDAR_GONE), check(Path.SYNC, Op.INSERT, Table.EVENTS, null, sync = firstPush))
        assertEquals(refused(Refusal.CALENDAR_GONE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, null, sync = mappedCopy()))
    }

    @Test fun syncNeverWritesThroughTheSyncAdapterUriOrToAnotherTable() {
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, syncAdapter = true, sync = firstPush))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.CALENDARS, personal, sync = mappedCopy()))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.CALENDAR_ALERTS, personal, sync = mappedCopy()))
    }

    @Test fun syncNeverWritesTesseraOrAnotherAppsLocalCalendar() {
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, tessera, sync = mappedCopy(tessera)))
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, qaLocal, sync = firstPush))
    }

    // ---- any op → an account calendar ----

    @Test fun everyOpOnAnAccountCalendarIsRefusedForEveryPathButSync() {
        for (path in Path.entries.filter { it != Path.SYNC }) for (op in Op.entries) {
            for (table in listOf(Table.CALENDARS, Table.EVENTS, Table.REMINDERS)) for (calendar in listOf(personal, work, qaLocal, davNamedTessera, googleNamedBirthdays)) {
                assertEquals("$path $op $table ${calendar.accountName}", refused(), check(path, op, table, calendar))
                assertEquals("$path $op $table ${calendar.accountName} (sync adapter)", refused(), check(path, op, table, calendar, syncAdapter = true))
            }
        }
    }

    @Test fun tessDeleteOfAnAccountCalendarEventIsRefused() {
        assertEquals(refused(), check(Path.TESS, Op.DELETE, Table.EVENTS, work))
        assertEquals(refused(), check(Path.TESS, Op.DELETE, Table.EVENTS, personal))
    }

    @Test fun aWriteToACalendarThatCannotBeReadIsRefused() {
        for (path in Path.entries.filter { it != Path.SYNC && it != Path.RECEIVER }) for (op in Op.entries) {
            assertEquals("$path $op", refused(), check(path, op, Table.EVENTS, null))
        }
    }

    // ---- case 4: the receiver's alert-state update ----

    @Test fun theReceiverMayUpdateAnAlertsState() {
        assertEquals(allowed, check(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns = setOf("state")))
    }

    @Test fun anyOtherWriteByTheReceiverIsRefused() {
        // Another column, with or without the state.
        assertEquals(refused(), check(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns = setOf("alarmTime")))
        assertEquals(refused(), check(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns = setOf("state", "minutes")))
        assertEquals(refused(), check(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns = emptySet()))
        // Another op on the alerts.
        assertEquals(refused(), check(Path.RECEIVER, Op.INSERT, Table.CALENDAR_ALERTS, null, columns = setOf("state")))
        assertEquals(refused(), check(Path.RECEIVER, Op.DELETE, Table.CALENDAR_ALERTS, null))
        // Through the sync-adapter URI.
        assertEquals(refused(), check(Path.RECEIVER, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns = setOf("state"), syncAdapter = true))
        // Another table, the shell's own calendar included.
        for (table in listOf(Table.EVENTS, Table.REMINDERS, Table.CALENDARS, Table.OTHER)) for (op in Op.entries) {
            for (calendar in listOf(tessera, birthdays, personal, null)) {
                assertEquals("$op $table ${calendar?.accountName}", refused(), check(Path.RECEIVER, op, table, calendar, columns = setOf("state")))
            }
        }
    }

    @Test fun nobodyButTheReceiverWritesTheAlerts() {
        for (path in Path.entries.filter { it != Path.RECEIVER }) {
            assertEquals("$path", refused(), check(path, Op.UPDATE, Table.CALENDAR_ALERTS, tessera, columns = setOf("state")))
            assertEquals("$path", refused(), check(path, Op.UPDATE, Table.CALENDAR_ALERTS, null, columns = setOf("state"), sync = mappedCopy()))
        }
    }

    @Test fun aTableTheRuleDoesNotNameIsRefusedForEveryone() {
        for (path in Path.entries) for (op in Op.entries) {
            assertEquals("$path $op", refused(), check(path, op, Table.OTHER, tessera, sync = mappedCopy()))
        }
    }

    // ---- the whole space: nothing is allowed outside the four cases ----

    @Test fun everyCombinationOutsideTheFourCasesIsRefused() {
        val calendars = listOf(tessera, birthdays, personal, work, qaLocal, davNamedTessera, googleNamedBirthdays, personal.copy(accessLevel = 200), null)
        val syncs = listOf(
            null, firstPush, mappedCopy(), mappedCopy(work), firstPush.copy(targetAllowed = false), mappedCopy().copy(sourceInTessera = false),
            firstPush.copy(onExistingRow = true), firstPush.copy(onExistingRow = true, mappingTarget = personal.key, copyCalendarId = personal.id),
            mappedCopy().copy(copyReadFailed = true), firstPush.copy(copyReadFailed = true, mappingTarget = personal.key),
            mappedCopy().copy(mappingTarget = personal.key.copy(name = "team-cal")), mappedCopy().copy(mappingTarget = personal.key.copy(name = null)),
        )
        var allowedCount = 0
        for (path in Path.entries) for (op in Op.entries) for (table in Table.entries) for (calendar in calendars) for (adapter in listOf(false, true)) for (sync in syncs) {
            for (columns in listOf(emptySet(), setOf("state"), setOf("title"), setOf("_sync_id"), setOf("calendar_id"), setOf("_sync_id", "calendar_id"))) {
                val verdict = check(path, op, table, calendar, columns, adapter, sync)
                if (verdict != allowed) continue
                allowedCount++
                // Whatever the case, an update never names calendar_id.
                assertEquals("an update of calendar_id was allowed: $path $table $calendar adapter=$adapter $sync $columns", false, op == Op.UPDATE && "calendar_id" in columns)
                val asNormalApp = !adapter && (table == Table.EVENTS || table == Table.REMINDERS)
                val asItsAdapter = adapter && table == Table.EVENTS && (op == Op.DELETE || (op == Op.UPDATE && path == Path.EDITOR && columns == setOf("_sync_id")))
                val case1 = calendar == tessera && (path == Path.EDITOR || path == Path.TESS) && (asNormalApp || asItsAdapter)
                val case1Create = calendar == tessera && adapter && path == Path.LOCAL_CALENDAR && table == Table.CALENDARS && op == Op.INSERT
                val case2 = calendar == birthdays && adapter && path == Path.BIRTHDAYS && (table == Table.EVENTS || (table == Table.CALENDARS && op == Op.INSERT))
                val case3 = path == Path.SYNC && !adapter && calendar != null && calendar.accountType != "LOCAL" && calendar.accessLevel >= 500 &&
                    (table == Table.EVENTS || table == Table.REMINDERS) && sync != null && sync.targetAllowed && sync.sourceInTessera && !sync.copyReadFailed &&
                    (sync.mappingTarget == null || sync.mappingTarget == calendar.key) && (sync.copyCalendarId == null || sync.copyCalendarId == calendar.id) &&
                    (sync.mapped || (op == Op.INSERT && table == Table.EVENTS && !sync.onExistingRow))
                val case4 = path == Path.RECEIVER && op == Op.UPDATE && table == Table.CALENDAR_ALERTS && columns == setOf("state") && !adapter
                assertEquals("allowed outside the four cases: $path $op $table $calendar adapter=$adapter $sync $columns", true, case1 || case1Create || case2 || case3 || case4)
            }
        }
        // The space was really walked, and the allow set is small.
        assertEquals(true, allowedCount > 0)
    }
}
