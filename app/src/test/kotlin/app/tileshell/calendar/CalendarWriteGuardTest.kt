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
    private val tessera = CalendarFacts(1, "Tessera", "LOCAL", 700)
    private val birthdays = CalendarFacts(2, "Tessera Birthdays", "LOCAL", 200)
    private val personal = CalendarFacts(5, "qa.personal@example.com", "com.google", 700)
    private val work = CalendarFacts(6, "qa.work@example.com", "com.google", 700)
    private val qaLocal = CalendarFacts(7, "qa", "LOCAL", 700)

    private val allowed = Verdict.Allowed
    private fun refused(why: Refusal = Refusal.NOT_ALLOWED) = Verdict.Refused(why)

    private fun check(path: Path, op: Op, table: Table, calendar: CalendarFacts?, columns: Set<String> = emptySet(), syncAdapter: Boolean = false, sync: SyncFacts? = null) =
        CalendarWriteGuard.check(Request(path, op, table, calendar, columns, syncAdapter, sync))

    /** A mapped copy sitting in the allowed calendar the mapping names. */
    private fun mappedCopy(target: CalendarFacts = personal) =
        SyncFacts(sourceInTessera = true, mapped = true, targetAllowed = true, mappingTarget = target.id, copyCalendarId = target.id)

    /** The first push: nothing mapped yet. */
    private val firstPush = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = null, copyCalendarId = null)

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
        // The LOCAL account's sync adapter is the shell itself: a series gets its _sync_id, and a row is removed for good.
        for (op in Op.entries) {
            assertEquals("$op", allowed, check(Path.EDITOR, op, Table.EVENTS, tessera, syncAdapter = true))
            assertEquals("$op", allowed, check(Path.TESS, op, Table.EVENTS, tessera, syncAdapter = true))
        }
        // Reminder rows are a normal app's; and the sync-adapter URI opens no other calendar to the editor or Tess.
        assertEquals(refused(), check(Path.EDITOR, Op.INSERT, Table.REMINDERS, tessera, syncAdapter = true))
        for (calendar in listOf(birthdays, personal, work, qaLocal)) for (op in Op.entries) {
            assertEquals("$op ${calendar.accountName}", refused(), check(Path.EDITOR, op, Table.EVENTS, calendar, syncAdapter = true))
            assertEquals("$op ${calendar.accountName}", refused(), check(Path.TESS, op, Table.EVENTS, calendar, syncAdapter = true))
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
        val recreate = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = personal.id, copyCalendarId = null)
        assertEquals(allowed, check(Path.SYNC, Op.INSERT, Table.EVENTS, personal, sync = recreate))
    }

    @Test fun syncToAnIdNotAllowedIsRefused() {
        assertEquals(refused(), check(Path.SYNC, Op.INSERT, Table.EVENTS, work, sync = firstPush.copy(targetAllowed = false)))
        assertEquals(refused(), check(Path.SYNC, Op.UPDATE, Table.EVENTS, work, sync = mappedCopy(work).copy(targetAllowed = false)))
        assertEquals(refused(), check(Path.SYNC, Op.DELETE, Table.EVENTS, work, sync = mappedCopy(work).copy(targetAllowed = false)))
    }

    @Test fun syncToAnAllowedIdButAnUnmappedEventIsRefused() {
        val unmapped = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = null, copyCalendarId = null)
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
        val moved = SyncFacts(sourceInTessera = true, mapped = true, targetAllowed = false, mappingTarget = personal.id, copyCalendarId = work.id)
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, work, sync = moved))
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.DELETE, Table.EVENTS, work, sync = moved))
        // Even when the calendar it moved to is itself allowed, the mapping is stale and nothing is written.
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, work, sync = moved.copy(targetAllowed = true)))
        // A write aimed at the mapping's target while the copy sits elsewhere is stale too.
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.UPDATE, Table.EVENTS, personal, sync = moved.copy(targetAllowed = true)))
        // A new copy may not be started in another calendar while a mapping names one.
        val elsewhere = SyncFacts(sourceInTessera = true, mapped = false, targetAllowed = true, mappingTarget = personal.id, copyCalendarId = personal.id)
        assertEquals(refused(Refusal.MAPPING_STALE), check(Path.SYNC, Op.INSERT, Table.EVENTS, work, sync = elsewhere))
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
            for (table in listOf(Table.CALENDARS, Table.EVENTS, Table.REMINDERS)) for (calendar in listOf(personal, work, qaLocal)) {
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
        val calendars = listOf(tessera, birthdays, personal, work, qaLocal, personal.copy(accessLevel = 200), null)
        val syncs = listOf(null, firstPush, mappedCopy(), mappedCopy(work), firstPush.copy(targetAllowed = false), mappedCopy().copy(sourceInTessera = false))
        var allowedCount = 0
        for (path in Path.entries) for (op in Op.entries) for (table in Table.entries) for (calendar in calendars) for (adapter in listOf(false, true)) for (sync in syncs) {
            for (columns in listOf(emptySet(), setOf("state"), setOf("title"))) {
                val verdict = check(path, op, table, calendar, columns, adapter, sync)
                if (verdict != allowed) continue
                allowedCount++
                val case1 = calendar == tessera && (path == Path.EDITOR || path == Path.TESS) && (table == Table.EVENTS || (table == Table.REMINDERS && !adapter))
                val case1Create = calendar == tessera && adapter && path == Path.LOCAL_CALENDAR && table == Table.CALENDARS && op == Op.INSERT
                val case2 = calendar == birthdays && adapter && path == Path.BIRTHDAYS && (table == Table.EVENTS || (table == Table.CALENDARS && op == Op.INSERT))
                val case3 = path == Path.SYNC && !adapter && calendar != null && calendar.accountType != "LOCAL" && calendar.accessLevel >= 500 &&
                    (table == Table.EVENTS || table == Table.REMINDERS) && sync != null && sync.targetAllowed && sync.sourceInTessera &&
                    (sync.mappingTarget == null || sync.mappingTarget == calendar.id) && (sync.copyCalendarId == null || sync.copyCalendarId == calendar.id) &&
                    (sync.mapped || (op == Op.INSERT && table == Table.EVENTS))
                val case4 = path == Path.RECEIVER && op == Op.UPDATE && table == Table.CALENDAR_ALERTS && columns == setOf("state") && !adapter
                assertEquals("allowed outside the four cases: $path $op $table $calendar adapter=$adapter $sync $columns", true, case1 || case1Create || case2 || case3 || case4)
            }
        }
        // The space was really walked, and the allow set is small.
        assertEquals(true, allowedCount > 0)
    }
}
