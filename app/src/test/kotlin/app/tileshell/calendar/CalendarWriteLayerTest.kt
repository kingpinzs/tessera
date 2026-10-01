package app.tileshell.calendar

import app.tileshell.calendar.CalendarWriteGuard.Path
import app.tileshell.calendar.CalendarWriteGuard.Refusal
import app.tileshell.calendar.CalendarWriteGuard.Verdict
import app.tileshell.calendar.FakeCalendarProvider.Write
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.LocalCalendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 16 fix round F15 (trust review A-F1): the write LAYER, not only the guard. The provider is a recording fake
 * and the store a file in a temp dir, so each test sees every write that would have reached the phone's calendar
 * provider. For every public write function: where the guard refuses, NO write is recorded and the refusal comes
 * back; where it allows, exactly the expected write — the URI, the selection, the columns. Then Sync's engine, the
 * one creator of the Tessera calendar, the allowed list's prune, and Tess's two writes.
 *
 * The facts the guard is asked about are never the test's: each is what the layer re-read from the fake.
 */
class CalendarWriteLayerTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "cal-writes-${System.nanoTime()}").apply { mkdirs() }
    private val fake = FakeCalendarProvider()
    private val store = CalendarSyncStore(File(dir, CalendarSyncStore.FILE), TestJson)
    private val access = CalendarAccess(fake, store)

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    private val tessera = fake.calendar(1, "Tessera", "LOCAL", 700, "Tessera")
    private val birthdays = fake.calendar(2, "Tessera Birthdays", "LOCAL", 200, "Birthdays")
    private val personal = fake.calendar(5, "me@example.com", "com.google", 700, "personal-cal", "Personal")
    private val work = fake.calendar(6, "work@example.com", "com.google", 700, "work-cal", "Work")
    private val shared = fake.calendar(7, "me@example.com", "com.google", 200, "shared-cal", "Shared")
    private val otherLocal = fake.calendar(8, "qa", "LOCAL", 700, "qa")

    /** A local (Tessera) event, an event of each account calendar, a birthday. */
    private val LOCAL = 10L
    private val PERSONAL_EVENT = 50L
    private val WORK_EVENT = 60L
    private val BIRTHDAY = 20L

    init {
        fake.event(LOCAL, tessera.id, "Dentist")
        fake.event(PERSONAL_EVENT, personal.id, "Lunch")
        fake.event(WORK_EVENT, work.id, "Standup")
        fake.event(BIRTHDAY, birthdays.id, "Ada's birthday", rrule = "FREQ=YEARLY", duration = "P1D", dtend = null)
    }

    private val values = EventValues("Dentist", "", "", 1_790_000_000_000L, 1_790_003_600_000L, null, false, "UTC", null)

    private val eventColumns = setOf(
        "title", "eventLocation", "description", "dtstart", "dtend", "duration", "allDay", "eventTimezone", "rrule", "rdate", "exrule", "exdate", "availability",
    )

    private val EVENTS = ProviderUri(ProviderTable.EVENTS)
    private val REMINDERS = ProviderUri(ProviderTable.REMINDERS)

    private fun refused(why: Refusal = Refusal.NOT_ALLOWED) = WriteResult.Refused(why)

    private fun allow(vararg calendars: CalendarInfo) {
        store.update { calendars.fold(it) { s, c -> SyncStateRules.setAllowed(s, c.key, true) } }
    }

    /** A copy of [LOCAL] (the same fields) sitting in [calendar] as event [copyId], and the mapping that names it. */
    private fun mapped(calendar: CalendarInfo = personal, copyId: Long = 77): Long {
        fake.event(copyId, calendar.id, "Dentist")
        store.update { SyncStateRules.map(it, SyncMapping(LOCAL, calendar.key, copyId)) }
        return copyId
    }

    private fun noWrites(what: String = "") = assertEquals(what, emptyList<Write>(), fake.writes)

    /** The `[calendar]` lines the diagnostics ring gained while [block] ran. */
    private fun ring(block: () -> Unit): List<String> {
        val mark = Diagnostics.snapshot().lastOrNull()
        block()
        val all = Diagnostics.snapshot()
        return all.subList(all.indexOfLast { it === mark } + 1, all.size).filter { it.tag == "calendar" }.map { it.message }
    }

    // ================================================================== the two LOCAL calendars

    @Test fun createLocalCalendarInsertsTesseraAsItsOwnSyncAdapterAndNothingElse() {
        val uri = CalendarWrites.createLocalCalendar(access, "Tessera", 0x0063B1)
        val write = fake.writes.single()
        assertEquals("insert", write.op)
        assertEquals(ProviderUri(ProviderTable.CALENDARS, syncAdapterAccount = "Tessera"), write.uri)
        assertEquals(
            mapOf<String, Any?>(
                "account_name" to "Tessera", "account_type" to "LOCAL", "name" to "Tessera", "calendar_displayName" to "Tessera", "ownerAccount" to "Tessera",
                "calendar_access_level" to 700, "visible" to 1, "sync_events" to 1, "calendar_color" to 0x0063B1,
            ),
            write.values,
        )
        assertEquals(rowIdOf(uri), fake.calendars.last()["_id"])
    }

    @Test fun createLocalCalendarUnderAnyOtherNameIsRefusedAndWritesNothing() {
        for (name in listOf("Tessera Birthdays", "work@example.com", "qa", "tessera", "")) {
            val thrown = runCatching { CalendarWrites.createLocalCalendar(access, name, 0) }.exceptionOrNull()
            assertTrue(name, thrown is IllegalStateException)
            noWrites(name)
        }
    }

    @Test fun createBirthdaysCalendarInsertsTheReadOnlyBirthdaysCalendar() {
        val id = CalendarWrites.createBirthdaysCalendar(access, "Birthdays", 0x744DA9)
        val write = fake.writes.single()
        assertEquals("insert", write.op)
        assertEquals(ProviderUri(ProviderTable.CALENDARS, syncAdapterAccount = "Tessera Birthdays"), write.uri)
        assertEquals("Tessera Birthdays", write.values["account_name"])
        assertEquals("LOCAL", write.values["account_type"])
        assertEquals("Birthdays", write.values["calendar_displayName"])
        assertEquals(200, write.values["calendar_access_level"])
        assertEquals(id, fake.calendars.last()["_id"])
        // Its request names no caller's value: there is no refused form of it to try.
    }

    @Test fun birthdayInsertWritesBirthdaysOnlyAndThroughTheSyncAdapterUri() {
        val made = CalendarWrites.birthdayInsert(access, birthdays.id, values.copy(title = "Ada's birthday"))
        val write = fake.writes.single()
        assertEquals(WriteResult.Ok(fake.events.last()["_id"]), made)
        assertEquals("insert", write.op)
        assertEquals(EVENTS.asSyncAdapter("Tessera Birthdays"), write.uri)
        assertEquals(eventColumns + "calendar_id", write.columns)
        assertEquals(birthdays.id, write.values["calendar_id"])
    }

    @Test fun birthdayInsertIntoAnyOtherCalendarIsRefusedAndWritesNothing() {
        allow(personal, work)
        for (calendar in listOf(tessera.id, personal.id, work.id, otherLocal.id, 99L)) {
            assertEquals("$calendar", refused(), CalendarWrites.birthdayInsert(access, calendar, values))
            noWrites("$calendar")
        }
    }

    @Test fun birthdayDeleteRemovesABirthdayAndRefusesEveryOtherEvent() {
        for (event in listOf(LOCAL, PERSONAL_EVENT, WORK_EVENT, 999L)) {
            assertEquals("$event", refused(), CalendarWrites.birthdayDelete(access, event))
            noWrites("$event")
        }
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.birthdayDelete(access, BIRTHDAY))
        assertEquals(listOf(Write("delete", ProviderUri(ProviderTable.EVENTS, BIRTHDAY).asSyncAdapter("Tessera Birthdays"))), fake.writes)
    }

    // ================================================================== Tessera: the editor and Tess

    @Test fun insertEventWritesTheEventAndItsRemindersIntoTessera() {
        for (path in listOf(Path.EDITOR, Path.TESS)) {
            fake.writes.clear()
            lateinit var made: WriteResult.Ok<Long>
            val lines = ring { made = CalendarWrites.insertEvent(access, path, tessera.id, values, listOf(10, 60)) as WriteResult.Ok }
            assertEquals(listOf("write insert event=${made.value}: ok"), lines)
            assertEquals(3, fake.writes.size)
            assertEquals("insert", fake.writes[0].op)
            assertEquals(EVENTS, fake.writes[0].uri)
            assertEquals(eventColumns + "calendar_id", fake.writes[0].columns)
            assertEquals(tessera.id, fake.writes[0].values["calendar_id"])
            assertEquals(Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to made.value, "minutes" to 10, "method" to 1)), fake.writes[1])
            assertEquals(Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to made.value, "minutes" to 60, "method" to 1)), fake.writes[2])
        }
    }

    @Test fun insertEventIntoAnyOtherCalendarIsRefusedAndWritesNothing() {
        // Even a calendar the user ticked on "Can sync to": the list opens it to Sync, never to the editor or Tess.
        allow(personal, work)
        for (path in Path.entries) for (calendar in listOf(birthdays.id, personal.id, work.id, shared.id, otherLocal.id, 99L)) {
            val result = CalendarWrites.insertEvent(access, path, calendar, values, listOf(10))
            assertTrue("$path $calendar: $result", result is WriteResult.Refused)
            noWrites("$path $calendar")
        }
        // And no other path writes Tessera's events.
        for (path in listOf(Path.BIRTHDAYS, Path.SYNC, Path.RECEIVER, Path.LOCAL_CALENDAR)) {
            assertTrue("$path", CalendarWrites.insertEvent(access, path, tessera.id, values, emptyList()) is WriteResult.Refused)
            noWrites("$path")
        }
    }

    @Test fun updateEventWritesTheRowAndReplacesItsReminders() {
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.updateEvent(access, Path.EDITOR, LOCAL, values.copy(title = "Dentist, moved"), listOf(15)))
        assertEquals(3, fake.writes.size)
        assertEquals("update", fake.writes[0].op)
        assertEquals(ProviderUri(ProviderTable.EVENTS, LOCAL), fake.writes[0].uri)
        assertNull(fake.writes[0].where)
        // The row's own columns: no calendar_id (a row never changes calendars), and the availability is left as it was.
        assertEquals(eventColumns - "availability", fake.writes[0].columns)
        assertEquals("Dentist, moved", fake.writes[0].values["title"])
        assertEquals(Write("delete", REMINDERS, where = "event_id = ?", args = listOf("$LOCAL")), fake.writes[1])
        assertEquals(Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to LOCAL, "minutes" to 15, "method" to 1)), fake.writes[2])
        // Reminders the edit did not touch are left alone.
        fake.writes.clear()
        CalendarWrites.updateEvent(access, Path.EDITOR, LOCAL, values, null)
        assertEquals(listOf("update"), fake.writes.map { it.op })
    }

    @Test fun updateEventOfAnyOtherCalendarsEventIsRefusedAndWritesNothing() {
        allow(personal, work)
        for (path in listOf(Path.EDITOR, Path.TESS)) for (event in listOf(PERSONAL_EVENT, WORK_EVENT, BIRTHDAY)) {
            assertEquals("$path $event", refused(), CalendarWrites.updateEvent(access, path, event, values, listOf(15)))
            noWrites("$path $event")
            assertEquals("$path $event", refused(), CalendarWrites.setRule(access, path, event, "FREQ=DAILY;COUNT=2"))
            noWrites("$path $event")
        }
        // A row that is gone is a failure, said before anything is asked or written.
        assertEquals(WriteResult.Failed("the event is gone"), CalendarWrites.updateEvent(access, Path.EDITOR, 999, values, null))
        noWrites()
    }

    @Test fun setRuleWritesTheRuleWithTheRowsOwnStartAndLength() {
        fake.event(11, tessera.id, "Run", rrule = "FREQ=DAILY", duration = "P3600S", dtend = null, dtstart = 5000)
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.setRule(access, Path.EDITOR, 11, "FREQ=DAILY;UNTIL=20261002T000000Z"))
        assertEquals(
            listOf(Write("update", ProviderUri(ProviderTable.EVENTS, 11), mapOf<String, Any?>("rrule" to "FREQ=DAILY;UNTIL=20261002T000000Z", "dtstart" to 5000L, "duration" to "P3600S"))),
            fake.writes,
        )
    }

    @Test fun deleteEventRemovesATesseraRowForGoodAsItsOwnSyncAdapter() {
        for (path in listOf(Path.EDITOR, Path.TESS)) {
            fake.writes.clear()
            fake.event(11, tessera.id, "Run")
            assertEquals(WriteResult.Ok(Unit), CalendarWrites.deleteEvent(access, path, 11))
            assertEquals(
                listOf(
                    // Its exception rows first, then the row: both scoped to the LOCAL account Tessera.
                    Write("delete", EVENTS.asSyncAdapter("Tessera"), where = "original_id = ?", args = listOf("11")),
                    Write("delete", ProviderUri(ProviderTable.EVENTS, 11).asSyncAdapter("Tessera")),
                ),
                fake.writes,
            )
            assertNull(fake.eventRow(11))
        }
    }

    @Test fun deleteEventOfAnOccurrenceCancelsIt() {
        fake.event(12, tessera.id, "Run", originalId = 11, originalInstanceTime = 9000)
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.deleteEvent(access, Path.EDITOR, 12))
        // Removing the exception row would bring the series' own occurrence back: it is marked cancelled, as a normal app.
        assertEquals(listOf(Write("update", ProviderUri(ProviderTable.EVENTS, 12), mapOf<String, Any?>("eventStatus" to 2))), fake.writes)
    }

    @Test fun deleteEventAsksAboutTheRowsOwnReReadCalendar() {
        allow(personal, work)
        // An exception row of a Work series too: no status write reaches it.
        fake.event(61, work.id, "Standup, moved", originalId = WORK_EVENT, originalInstanceTime = 9000)
        for (path in listOf(Path.EDITOR, Path.TESS)) for (event in listOf(PERSONAL_EVENT, WORK_EVENT, 61L, BIRTHDAY)) {
            assertEquals("$path $event", refused(), CalendarWrites.deleteEvent(access, path, event))
            noWrites("$path $event")
            assertEquals("$path $event", refused(), CalendarWrites.purgeRow(access, path, event))
            noWrites("$path $event")
        }
        assertEquals(WriteResult.Failed("the event is gone"), CalendarWrites.deleteEvent(access, Path.EDITOR, 999))
        noWrites()
        // The refusal is said in the ring, whoever asked: the UI offers no such action, so the line is how it is seen.
        assertEquals(listOf("write delete event=$WORK_EVENT: failed refused (not allowed)"), ring { CalendarWrites.deleteEvent(access, Path.EDITOR, WORK_EVENT) })
        assertEquals(listOf("write delete event=$WORK_EVENT: failed refused (not allowed)"), ring { CalendarWrites.deleteEvent(access, Path.TESS, WORK_EVENT) })
        // A Tessera event that was re-homed to Work under an open page (the row's calendar is re-read at the write):
        fake.eventRow(LOCAL)!!["calendar_id"] = work.id
        assertEquals(refused(), CalendarWrites.deleteEvent(access, Path.EDITOR, LOCAL))
        assertEquals(listOf("write update event=$LOCAL: failed refused (not allowed)"), ring { assertEquals(refused(), CalendarWrites.updateEvent(access, Path.EDITOR, LOCAL, values, listOf(5))) })
        noWrites()
    }

    @Test fun purgeRowRemovesOneTesseraRowAsItsOwnSyncAdapter() {
        fake.event(12, tessera.id, "Run", originalId = 11, originalInstanceTime = 9000)
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.purgeRow(access, Path.EDITOR, 12))
        assertEquals(listOf(Write("delete", ProviderUri(ProviderTable.EVENTS, 12).asSyncAdapter("Tessera"))), fake.writes)
    }

    @Test fun insertExceptionGivesTheSeriesItsSyncIdOnceThenInsertsTheOccurrence() {
        fake.event(11, tessera.id, "Run", rrule = "FREQ=DAILY", duration = "P3600S", dtend = null, dtstart = 5000)
        val made = CalendarWrites.insertException(access, Path.EDITOR, 11, 91_400_000L, ExceptionValues(title = "Run, late", dtstart = 91_500_000L, duration = "P3600S", status = 1), listOf(5)) as WriteResult.Ok
        assertEquals(
            listOf(
                // The one update the editor may make as Tessera's sync adapter: the series' _sync_id, that column alone.
                Write("update", ProviderUri(ProviderTable.EVENTS, 11).asSyncAdapter("Tessera"), mapOf<String, Any?>("_sync_id" to "tessera-11")),
                // The exception itself is a normal app's insert under the master's id.
                Write(
                    "insert", ProviderUri(ProviderTable.EXCEPTIONS, 11),
                    mapOf<String, Any?>("originalInstanceTime" to 91_400_000L, "title" to "Run, late", "dtstart" to 91_500_000L, "duration" to "P3600S", "eventStatus" to 1),
                ),
                Write("delete", REMINDERS, where = "event_id = ?", args = listOf("${made.value}")),
                Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to made.value, "minutes" to 5, "method" to 1)),
            ),
            fake.writes,
        )
        // A second occurrence of the same series: the _sync_id is there, and is not written again.
        fake.writes.clear()
        CalendarWrites.insertException(access, Path.EDITOR, 11, 177_800_000L, ExceptionValues(status = 2), null)
        assertEquals(listOf(Write("insert", ProviderUri(ProviderTable.EXCEPTIONS, 11), mapOf<String, Any?>("originalInstanceTime" to 177_800_000L, "eventStatus" to 2))), fake.writes)
    }

    @Test fun insertExceptionOnAnyOtherCalendarsSeriesIsRefusedAndWritesNothing() {
        allow(personal, work)
        fake.event(62, work.id, "Weekly", rrule = "FREQ=WEEKLY", duration = "P3600S", dtend = null)
        fake.event(63, work.id, "Weekly, keyed", rrule = "FREQ=WEEKLY", duration = "P3600S", dtend = null, syncId = "abc")
        for (path in listOf(Path.EDITOR, Path.TESS)) for (event in listOf(62L, 63L, BIRTHDAY)) {
            assertEquals("$path $event", refused(), CalendarWrites.insertException(access, path, event, 9000, ExceptionValues(status = 2), null))
            noWrites("$path $event")
        }
        assertEquals(WriteResult.Failed("the event is gone"), CalendarWrites.insertException(access, Path.EDITOR, 999, 9000, ExceptionValues(status = 2), null))
        noWrites()
        // Tess makes no occurrence edits: she may not give a series its _sync_id (F19), so nothing is written for her.
        fake.event(11, tessera.id, "Run", rrule = "FREQ=DAILY", duration = "P3600S", dtend = null)
        assertEquals(refused(), CalendarWrites.insertException(access, Path.TESS, 11, 9000, ExceptionValues(status = 2), null))
        noWrites()
    }

    // ================================================================== Sync: the write layer

    @Test fun syncInsertCopyInsertsIntoAnAllowedTargetAndRecordsTheMapping() {
        allow(personal)
        val made = CalendarWrites.syncInsertCopy(access, LOCAL, personal.key, values) as WriteResult.Ok
        val write = fake.writes.single()
        assertEquals("insert", write.op)
        // A normal app's insert — never the sync-adapter URI — so the account's own adapter uploads it.
        assertEquals(EVENTS, write.uri)
        assertEquals(eventColumns + "calendar_id", write.columns)
        assertEquals(personal.id, write.values["calendar_id"])
        assertEquals(mapOf(LOCAL to SyncMapping(LOCAL, personal.key, made.value)), store.current.mappings)
    }

    @Test fun syncInsertCopyReadsTheAllowedListAndComparesTheWholeKey() {
        // Nothing ticked: refused.
        assertEquals(refused(), CalendarWrites.syncInsertCopy(access, LOCAL, personal.key, values))
        // Another calendar ticked: refused.
        allow(work)
        assertEquals(refused(), CalendarWrites.syncInsertCopy(access, LOCAL, personal.key, values))
        // The same _ID ticked under another account, another account type, another name of its own: each is another
        // calendar's tick (the provider hands ids out again), and none of them allows this one.
        for (other in listOf(personal.key.copy(accountName = "work@example.com"), personal.key.copy(accountType = "com.example"), personal.key.copy(name = "team-cal"), personal.key.copy(name = null))) {
            store.update { SyncState(allowed = listOf(other)) }
            assertEquals("$other", refused(), CalendarWrites.syncInsertCopy(access, LOCAL, personal.key, values))
        }
        noWrites()
        assertEquals(emptyMap<Long, SyncMapping>(), store.current.mappings)
    }

    @Test fun syncInsertCopyIsRefusedForEveryTargetTheRuleExcludes() {
        allow(personal, work, shared)
        // The source is not a Tessera event: Sync only ever copies the shell's own.
        assertEquals(refused(), CalendarWrites.syncInsertCopy(access, WORK_EVENT, personal.key, values))
        assertEquals(refused(), CalendarWrites.syncInsertCopy(access, PERSONAL_EVENT, work.key, values))
        assertEquals(refused(), CalendarWrites.syncInsertCopy(access, 999, personal.key, values))
        // A target that is read-only now (r3 D5), one that is gone, and the shell's own and another app's LOCAL calendars.
        assertEquals(refused(Refusal.READ_ONLY), CalendarWrites.syncInsertCopy(access, LOCAL, shared.key, values))
        assertEquals(refused(Refusal.CALENDAR_GONE), CalendarWrites.syncInsertCopy(access, LOCAL, CalendarKey(99, "me@example.com", "com.google", "x"), values))
        store.update { it.copy(allowed = it.allowed + tessera.key + birthdays.key + otherLocal.key) }
        for (local in listOf(tessera, birthdays, otherLocal)) assertEquals(local.accountName, refused(), CalendarWrites.syncInsertCopy(access, LOCAL, local.key, values))
        noWrites()
        assertEquals(emptyMap<Long, SyncMapping>(), store.current.mappings)
    }

    @Test fun syncInsertCopyReReadsWhereTheMappedCopySits() {
        // T16-12. The mapping names Personal; on the other side the copy was moved to Work. A new copy may not be
        // started while the old one sits elsewhere — whatever the mapping itself still says.
        allow(personal, work)
        val copy = mapped(personal)
        fake.eventRow(copy)!!["calendar_id"] = work.id
        assertEquals(refused(Refusal.MAPPING_STALE), CalendarWrites.syncInsertCopy(access, LOCAL, personal.key, values))
        assertEquals(refused(Refusal.MAPPING_STALE), CalendarWrites.syncInsertCopy(access, LOCAL, work.key, values))
        noWrites()
        assertEquals(Verdict.Refused(Refusal.MAPPING_STALE), CalendarWrites.syncCheck(access, LOCAL, personal.key))
        assertEquals(Verdict.Refused(Refusal.MAPPING_STALE), CalendarWrites.syncDeleteCheck(access, LOCAL))
    }

    @Test fun syncUpdateCopyWritesTheMappedCopyOnly() {
        allow(personal)
        val copy = mapped(personal)
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.syncUpdateCopy(access, LOCAL, copy, values.copy(title = "Dentist, moved")))
        val write = fake.writes.single()
        assertEquals("update", write.op)
        assertEquals(ProviderUri(ProviderTable.EVENTS, copy), write.uri)
        assertNull(write.where)
        assertEquals(eventColumns, write.columns)
        assertEquals("Dentist, moved", write.values["title"])
    }

    @Test fun onlyTheCopyAndItsExceptionsCountAsMapped() {
        allow(personal, work)
        val copy = mapped(personal)
        fake.event(78, personal.id, "Dentist, late", originalId = copy, originalInstanceTime = 9000)
        // Any other row — in the same allowed calendar, in Work, in Tessera, the local event itself — is not the copy:
        // a mapping existing for the local event opens none of them.
        for (row in listOf(PERSONAL_EVENT, WORK_EVENT, LOCAL, BIRTHDAY, 999L)) {
            assertTrue("update $row", CalendarWrites.syncUpdateCopy(access, LOCAL, row, values) is WriteResult.Refused)
            assertTrue("delete $row", CalendarWrites.syncDeleteRow(access, LOCAL, row) is WriteResult.Refused)
            assertTrue("reminders $row", CalendarWrites.syncSetReminders(access, LOCAL, row, listOf(10 to 1)) is WriteResult.Refused)
            assertTrue("exception $row", CalendarWrites.syncInsertException(access, LOCAL, row, 9000, ExceptionValues(status = 2)) is WriteResult.Refused)
            noWrites("$row")
        }
        // A local event with no mapping at all has no copy to write.
        fake.event(13, tessera.id, "Unsynced")
        assertEquals(refused(), CalendarWrites.syncUpdateCopy(access, 13, copy, values))
        assertEquals(refused(), CalendarWrites.syncDeleteRow(access, 13, copy))
        assertEquals(Verdict.Refused(Refusal.NOT_ALLOWED), CalendarWrites.syncDeleteCheck(access, 13))
        noWrites()
        // The copy and its exception are.
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.syncDeleteRow(access, LOCAL, 78))
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.syncDeleteRow(access, LOCAL, copy))
        assertEquals(listOf(Write("delete", ProviderUri(ProviderTable.EVENTS, 78)), Write("delete", ProviderUri(ProviderTable.EVENTS, copy))), fake.writes)
    }

    @Test fun aMappedCopyIsNotWrittenOnceItsTargetIsUnTickedReadOnlyOrMoved() {
        val copy = mapped(personal)
        val calls = listOf<Pair<String, () -> WriteResult<*>>>(
            "update" to { CalendarWrites.syncUpdateCopy(access, LOCAL, copy, values) },
            "delete" to { CalendarWrites.syncDeleteRow(access, LOCAL, copy) },
            "reminders" to { CalendarWrites.syncSetReminders(access, LOCAL, copy, listOf(10 to 1)) },
            "exception" to { CalendarWrites.syncInsertException(access, LOCAL, copy, 9000, ExceptionValues(status = 2)) },
        )
        // Not ticked (the user un-ticked it after the first Sync).
        for ((name, call) in calls) assertEquals(name, refused(), call())
        noWrites()
        // Ticked, but read-only now.
        allow(personal)
        (fake.calendars.first { it["_id"] == personal.id })["calendar_access_level"] = 200
        for ((name, call) in calls) assertEquals(name, refused(Refusal.READ_ONLY), call())
        noWrites()
        // Ticked and writable, but the copy was moved to Work on the other side — Work itself ticked or not.
        (fake.calendars.first { it["_id"] == personal.id })["calendar_access_level"] = 700
        fake.eventRow(copy)!!["calendar_id"] = work.id
        for ((name, call) in calls) assertEquals(name, refused(Refusal.MAPPING_STALE), call())
        allow(work)
        for ((name, call) in calls) assertEquals(name, refused(Refusal.MAPPING_STALE), call())
        noWrites()
    }

    @Test fun syncSetRemindersReplacesTheCopysReminders() {
        allow(personal)
        val copy = mapped(personal)
        fake.reminder(copy, 30)
        assertEquals(WriteResult.Ok(Unit), CalendarWrites.syncSetReminders(access, LOCAL, copy, listOf(10 to 1, 60 to 1)))
        assertEquals(
            listOf(
                Write("delete", REMINDERS, where = "event_id = ?", args = listOf("$copy")),
                Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to copy, "minutes" to 10, "method" to 1)),
                Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to copy, "minutes" to 60, "method" to 1)),
            ),
            fake.writes,
        )
    }

    @Test fun syncInsertExceptionHangsTheOccurrenceOnTheCopysMaster() {
        allow(personal)
        val copy = mapped(personal)
        val made = CalendarWrites.syncInsertException(access, LOCAL, copy, 9000, ExceptionValues(title = "Late", dtstart = 9500, duration = "P60S", status = 1))
        assertTrue(made is WriteResult.Ok)
        // ORIGINAL_ID is the COPY's master — never the local one, which would hang an account row on a Tessera row.
        assertEquals(
            listOf(Write("insert", ProviderUri(ProviderTable.EXCEPTIONS, copy), mapOf<String, Any?>("originalInstanceTime" to 9000L, "title" to "Late", "dtstart" to 9500L, "duration" to "P60S", "eventStatus" to 1))),
            fake.writes,
        )
    }

    // ================================================================== the reminder receiver

    @Test fun setAlertStateWritesTheStateOfTheOneAlertTheIdentityNames() {
        val alert = ReminderRules.AlertIdentity(7, 10, 5000, 4400)
        fake.alerts += mutableMapOf("_id" to 7L, "event_id" to 10L, "begin" to 5000L, "alarmTime" to 4400L, "state" to 1)
        fake.alerts += mutableMapOf("_id" to 8L, "event_id" to 10L, "begin" to 5000L, "alarmTime" to 1400L, "state" to 0)
        assertEquals(WriteResult.Ok(1), CalendarWrites.setAlertState(access, alert, 2))
        assertEquals(
            listOf(Write("update", ProviderUri(ProviderTable.ALERTS), mapOf<String, Any?>("state" to 2), "_id = ? AND event_id = ? AND begin = ? AND alarmTime = ? AND state IN (0, 1)", listOf("7", "10", "5000", "4400"))),
            fake.writes,
        )
        assertEquals(listOf(2, 0), fake.alerts.map { it["state"] })
        // A second swipe of the same notification, and a swipe whose row is now another alert: nothing is touched.
        assertEquals(WriteResult.Ok(0), CalendarWrites.setAlertState(access, alert, 2))
        assertEquals(WriteResult.Ok(0), CalendarWrites.setAlertState(access, ReminderRules.AlertIdentity(8, 11, 5000, 1400), 2))
        assertEquals(listOf(2, 0), fake.alerts.map { it["state"] })
    }

    // ================================================================== Sync: the engine

    @Test fun aFirstSyncCopiesTheEventAndASecondWritesNothing() {
        allow(personal)
        fake.reminder(LOCAL, 10)
        assertEquals(listOf("sync event=$LOCAL -> calendar ${personal.id}: ok"), ring { assertEquals(CalendarSync.Outcome.Ok, CalendarSync.sync(access, LOCAL, personal.key)) })
        val copy = store.current.mappings.getValue(LOCAL).copyEventId
        assertEquals(listOf("insert", "delete", "insert"), fake.writes.map { it.op })
        assertEquals(EVENTS, fake.writes[0].uri)
        assertEquals(personal.id, fake.writes[0].values["calendar_id"])
        assertEquals(Write("insert", REMINDERS, mapOf<String, Any?>("event_id" to copy, "minutes" to 10, "method" to 1)), fake.writes[2])
        assertEquals(personal.id, fake.eventRow(copy)!!["calendar_id"])
        // Nothing differs: `ok`, and not one write.
        fake.writes.clear()
        assertEquals(CalendarSync.Outcome.Ok, CalendarSync.sync(access, LOCAL, personal.key))
        noWrites()
        // The local event changed: the copy is updated in place.
        fake.eventRow(LOCAL)!!["title"] = "Dentist, moved"
        assertEquals(CalendarSync.Outcome.Updated, CalendarSync.sync(access, LOCAL, personal.key))
        assertEquals(listOf(ProviderUri(ProviderTable.EVENTS, copy)), fake.writes.map { it.uri })
        assertEquals("Dentist, moved", fake.eventRow(copy)!!["title"])
    }

    @Test fun aSyncToACalendarThatIsNotTickedWritesNothing() {
        for (target in listOf(personal, work)) {
            val lines = ring { assertEquals(CalendarSync.Outcome.Refused, CalendarSync.sync(access, LOCAL, target.key)) }
            // One line says how the Sync ended; the write layer adds none of its own for Sync.
            assertEquals(listOf("sync event=$LOCAL -> calendar ${target.id}: refused (not allowed)"), lines)
            noWrites(target.displayName)
        }
        allow(personal)
        assertEquals(CalendarSync.Outcome.Refused, CalendarSync.sync(access, LOCAL, work.key))
        assertEquals(CalendarSync.Outcome.ReadOnly, CalendarSync.sync(access, LOCAL, shared.key.also { allow(shared) }))
        assertEquals(CalendarSync.Outcome.Refused, CalendarSync.sync(access, WORK_EVENT, personal.key))
        noWrites()
        assertEquals(emptyMap<Long, SyncMapping>(), store.current.mappings)
    }

    @Test fun aSyncTargetIsTheCalendarTheUserTickedNotWhicheverHoldsItsIdNow() {
        // The user ticked Personal (id 5) and tapped Sync on its row. Before the write the account's calendars were
        // re-made: id 5 now belongs to Work's account — and that calendar is itself ticked under its own key.
        val reused = CalendarKey(personal.id, "work@example.com", "com.google", "work-cal")
        val row = fake.calendars.first { it["_id"] == personal.id }
        row["account_name"] = reused.accountName
        row["name"] = reused.name
        store.update { SyncState(allowed = listOf(personal.key, reused)) }
        assertEquals(CalendarSync.Outcome.CalendarGone, CalendarSync.sync(access, LOCAL, personal.key))
        noWrites()
        // The same id inside the SAME account under another name of its own (F16): gone too.
        row["account_name"] = personal.accountName
        row["name"] = "team-cal"
        store.update { SyncState(allowed = listOf(personal.key, personal.key.copy(name = "team-cal"))) }
        assertEquals(CalendarSync.Outcome.CalendarGone, CalendarSync.sync(access, LOCAL, personal.key))
        assertEquals(CalendarSync.Outcome.CalendarGone, CalendarSync.sync(access, LOCAL, CalendarKey(99, "me@example.com", "com.google", "x")))
        noWrites()
        assertEquals(emptyMap<Long, SyncMapping>(), store.current.mappings)
    }

    @Test fun theGuardIsAskedBeforeAnythingIsComparedOrWritten() {
        allow(personal)
        val copy = mapped(personal)
        // The copy equals the local event, so a compare would find nothing to write and say `ok` — but the target
        // became read-only, and the Sync must say so.
        assertEquals(CalendarSync.Outcome.Ok, CalendarSync.sync(access, LOCAL, personal.key))
        (fake.calendars.first { it["_id"] == personal.id })["calendar_access_level"] = 200
        assertEquals(CalendarSync.Outcome.ReadOnly, CalendarSync.sync(access, LOCAL, personal.key))
        // Likewise a copy that was moved, and a target that was un-ticked.
        (fake.calendars.first { it["_id"] == personal.id })["calendar_access_level"] = 700
        fake.eventRow(copy)!!["calendar_id"] = work.id
        assertEquals(CalendarSync.Outcome.MappingStale, CalendarSync.sync(access, LOCAL, personal.key))
        fake.eventRow(copy)!!["calendar_id"] = personal.id
        store.update { it.copy(allowed = emptyList()) }
        assertEquals(CalendarSync.Outcome.Refused, CalendarSync.sync(access, LOCAL, personal.key))
        noWrites()
    }

    @Test fun deleteHereKeepsTheCopyAndDeleteBothIsTheEditorsAlone() {
        allow(personal)
        val copy = mapped(personal)
        // Tess: "here and from <calendar>" is not hers to do — refused before anything is asked or written.
        assertEquals(refused(), CalendarSync.delete(access, Path.TESS, LOCAL, both = true))
        noWrites()
        assertEquals(setOf(LOCAL), store.current.mappings.keys)
        // The editor, both: the copy goes first (a normal app's delete), then the local row for good; the mapping is dropped.
        assertEquals(WriteResult.Ok(Unit), CalendarSync.delete(access, Path.EDITOR, LOCAL, both = true))
        assertEquals(
            listOf(
                Write("delete", ProviderUri(ProviderTable.EVENTS, copy)),
                Write("delete", EVENTS.asSyncAdapter("Tessera"), where = "original_id = ?", args = listOf("$LOCAL")),
                Write("delete", ProviderUri(ProviderTable.EVENTS, LOCAL).asSyncAdapter("Tessera")),
            ),
            fake.writes,
        )
        assertEquals(emptyMap<Long, SyncMapping>(), store.current.mappings)
    }

    @Test fun deleteBothIsRefusedWhenTheCopysCalendarIsNoLongerAllowedOrTheCopyMoved() {
        val copy = mapped(personal)
        // Asked before anything is deleted, and said in Sync's own line.
        assertEquals(
            listOf("sync event=$LOCAL -> calendar ${personal.id}: refused (not allowed)"),
            ring { assertEquals(refused(), CalendarSync.delete(access, Path.EDITOR, LOCAL, both = true)) },
        )
        allow(personal, work)
        fake.eventRow(copy)!!["calendar_id"] = work.id
        assertEquals(
            listOf("sync event=$LOCAL -> calendar ${personal.id}: failed mapping stale"),
            ring { assertEquals(refused(Refusal.MAPPING_STALE), CalendarSync.delete(access, Path.EDITOR, LOCAL, both = true)) },
        )
        noWrites()
        // Nothing was deleted, here or there, and the mapping stands.
        assertTrue(fake.eventRow(LOCAL) != null && fake.eventRow(copy) != null)
        assertEquals(setOf(LOCAL), store.current.mappings.keys)
    }

    // ================================================================== fix round F20

    @Test fun aSyncToAnotherCalendarKeepsTheOldMappingUntilTheNewPushIsAllowed() {
        // A-F7. The event is synced to Personal. A Sync tapped for another calendar starts a new copy there — but a
        // Sync that is refused must leave the store as it found it. Before, the old mapping was dropped first.
        allow(personal)
        val copy = mapped(personal)
        val old = SyncMapping(LOCAL, personal.key, copy)
        assertEquals(CalendarSync.Outcome.Refused, CalendarSync.sync(access, LOCAL, work.key))
        assertEquals(old, store.current.mappings[LOCAL])
        allow(shared)
        assertEquals(CalendarSync.Outcome.ReadOnly, CalendarSync.sync(access, LOCAL, shared.key))
        assertEquals(CalendarSync.Outcome.CalendarGone, CalendarSync.sync(access, LOCAL, CalendarKey(99, "me@example.com", "com.google", "x")))
        assertEquals(old, store.current.mappings[LOCAL])
        assertEquals(Verdict.Refused(Refusal.NOT_ALLOWED), CalendarWrites.syncRetargetCheck(access, LOCAL, work.key))
        noWrites()
        // Work ticked: the push is allowed, so the old mapping goes and a new copy is made there. The old copy is not
        // written — it stays in Personal as the account event it is.
        allow(work)
        assertEquals(Verdict.Allowed, CalendarWrites.syncRetargetCheck(access, LOCAL, work.key))
        assertEquals(CalendarSync.Outcome.Ok, CalendarSync.sync(access, LOCAL, work.key))
        val made = store.current.mappings.getValue(LOCAL)
        assertEquals(work.key, made.target)
        assertFalse(made.copyEventId == copy)
        assertEquals(listOf("insert"), fake.writes.map { it.op })
        assertEquals(work.id, fake.writes.single().values["calendar_id"])
        assertEquals(personal.id, fake.eventRow(copy)!!["calendar_id"])
    }

    @Test fun aCopyRowThatCouldNotBeReadIsNotTakenForACopyThatIsGone() {
        // A-F9. The read of the mapped copy's row fails (no answer, or it throws). Before, that read as "no copy" and
        // the guard allowed a new one: a second copy beside the first.
        allow(personal)
        val copy = mapped(personal)
        val readOfTheCopyRow: (FakeCalendarProvider.Query) -> Boolean = { it.uri.table == ProviderTable.EVENTS && it.where == "_id = ?" && it.args == listOf("$copy") }
        for (mode in listOf("no answer", "throws")) {
            fake.noAnswer = if (mode == "no answer") readOfTheCopyRow else { _ -> false }
            fake.throwOnQuery = if (mode == "throws") readOfTheCopyRow else { _ -> false }
            assertEquals(mode, Verdict.Refused(Refusal.NOT_ALLOWED), CalendarWrites.syncCheck(access, LOCAL, personal.key))
            assertEquals(mode, refused(), CalendarWrites.syncInsertCopy(access, LOCAL, personal.key, values))
            assertEquals(mode, CalendarSync.Outcome.Refused, CalendarSync.sync(access, LOCAL, personal.key))
            noWrites(mode)
            assertEquals(mode, SyncMapping(LOCAL, personal.key, copy), store.current.mappings[LOCAL])
        }
        // The engine's own read of the copy (the whole row) fails while the layer's read works: the Sync fails, and
        // nothing is made again.
        val readOfTheCopy: (FakeCalendarProvider.Query) -> Boolean = { it.uri.table == ProviderTable.EVENTS && it.where == "_id = ? AND deleted != 1" && it.args == listOf("$copy") }
        for (mode in listOf("no answer", "throws")) {
            fake.noAnswer = if (mode == "no answer") readOfTheCopy else { _ -> false }
            fake.throwOnQuery = if (mode == "throws") readOfTheCopy else { _ -> false }
            assertEquals(mode, "failed the copy could not be read", CalendarSync.sync(access, LOCAL, personal.key).text)
            noWrites(mode)
        }
        // A copy that IS gone — the query answered, and there is no such row — is made again in the mapped calendar.
        fake.noAnswer = { false }
        fake.throwOnQuery = { false }
        fake.events.removeAll { it["_id"] == copy }
        assertEquals(CalendarSync.Outcome.Recreated, CalendarSync.sync(access, LOCAL, personal.key))
        assertEquals(listOf("insert"), fake.writes.map { it.op })
    }

    @Test fun everyWriteOfAReminderBatchIsPutToTheGuardWithFactsReadThen() {
        // A-F13. The copy's reminders are a delete and one insert per reminder. Before, the Sync facts were read once
        // for the whole batch; now each write re-reads them.
        allow(personal, work)
        val copy = mapped(personal)
        fake.reminder(copy, 30)
        // The copy is moved to Work on the other side right after its reminders were cleared: the batch stops there.
        fake.afterWrite = { w -> if (w.op == "delete" && w.uri.table == ProviderTable.REMINDERS) fake.eventRow(copy)!!["calendar_id"] = work.id }
        assertEquals(refused(Refusal.MAPPING_STALE), CalendarWrites.syncSetReminders(access, LOCAL, copy, listOf(10 to 1, 60 to 1)))
        assertEquals(listOf("delete"), fake.writes.map { it.op })
        // The calendar is un-ticked between the two inserts: the first is written, the second is not.
        fake.writes.clear()
        fake.eventRow(copy)!!["calendar_id"] = personal.id
        fake.afterWrite = { w -> if (w.op == "insert" && w.uri.table == ProviderTable.REMINDERS) store.update { it.copy(allowed = emptyList()) } }
        assertEquals(refused(), CalendarWrites.syncSetReminders(access, LOCAL, copy, listOf(10 to 1, 60 to 1)))
        assertEquals(listOf("delete", "insert"), fake.writes.map { it.op })
        // The editor's batch too: the event is re-homed to Work under the open editor after its reminders were cleared.
        fake.writes.clear()
        fake.afterWrite = { w -> if (w.op == "delete" && w.uri.table == ProviderTable.REMINDERS) fake.eventRow(LOCAL)!!["calendar_id"] = work.id }
        assertEquals(refused(), CalendarWrites.updateEvent(access, Path.EDITOR, LOCAL, values, listOf(5, 10)))
        assertEquals(listOf("update", "delete"), fake.writes.map { it.op })
        assertEquals(emptyList<Map<String, Any?>>(), fake.reminders.filter { it["event_id"] == LOCAL })
    }

    @Test fun aSyncAdapterUriMustNameItsAccount() {
        // A-F14. The provider scopes a sync adapter's write to its account only when the URI names one.
        for (uri in listOf(ProviderUri(ProviderTable.EVENTS), ProviderUri(ProviderTable.EVENTS, 5), ProviderUri(ProviderTable.CALENDARS))) {
            assertTrue(runCatching { uri.asSyncAdapter("") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Tessera", uri.asSyncAdapter("Tessera").syncAdapterAccount)
            assertEquals(uri.ids, uri.asSyncAdapter("Tessera").ids)
        }
        // Every sync-adapter write the layer makes names Tessera or Tessera Birthdays, never nothing.
        fake.event(11, tessera.id, "Run", rrule = "FREQ=DAILY", duration = "P3600S", dtend = null)
        CalendarWrites.insertException(access, Path.EDITOR, 11, 9000, ExceptionValues(status = 2), null)
        CalendarWrites.deleteEvent(access, Path.EDITOR, 11)
        CalendarWrites.purgeRow(access, Path.EDITOR, LOCAL)
        CalendarWrites.birthdayInsert(access, birthdays.id, values)
        CalendarWrites.birthdayDelete(access, BIRTHDAY)
        CalendarWrites.createLocalCalendar(access, "Tessera", 0)
        CalendarWrites.createBirthdaysCalendar(access, "Birthdays", 0)
        val adapters = fake.writes.mapNotNull { it.uri.syncAdapterAccount }
        assertEquals(8, adapters.size)
        assertEquals(setOf("Tessera", "Tessera Birthdays"), adapters.toSet())
    }

    // ================================================================== Tess

    @Test fun tessDeletesHereOnlyAndOnlyATesseraEvent() {
        allow(personal)
        val copy = mapped(personal)
        assertEquals(WriteResult.Ok(Unit), TessCalendar.delete(access, LOCAL))
        // The local row and nothing else: no write names the copy or its calendar.
        assertEquals(
            listOf(
                Write("delete", EVENTS.asSyncAdapter("Tessera"), where = "original_id = ?", args = listOf("$LOCAL")),
                Write("delete", ProviderUri(ProviderTable.EVENTS, LOCAL).asSyncAdapter("Tessera")),
            ),
            fake.writes,
        )
        assertTrue(fake.eventRow(copy) != null)
        assertEquals(emptyMap<Long, SyncMapping>(), store.current.mappings)
        // An event id of any other calendar — a pending card that named one, however it got there — is refused.
        fake.writes.clear()
        for (event in listOf(WORK_EVENT, PERSONAL_EVENT, BIRTHDAY, copy)) {
            assertEquals("$event", refused(), TessCalendar.delete(access, event))
            noWrites("$event")
        }
    }

    @Test fun tessOffersToDeleteOnlyATitleFoundInTessera() {
        val events = listOf(
            TessCalendar.Event(WORK_EVENT, "Standup", work.id), TessCalendar.Event(LOCAL, "Dentist", tessera.id),
            TessCalendar.Event(PERSONAL_EVENT, "Dentist follow-up", personal.id), TessCalendar.Event(BIRTHDAY, "Ada's birthday", birthdays.id),
        )
        assertEquals(TessCalendar.DeleteMatch.Found(events[1]), TessCalendar.matchForDelete(events, tessera.id, "dentist"))
        // A title that is only on an account calendar, or on Birthdays, gets no Delete card: whole or in part.
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.matchForDelete(events, tessera.id, "Standup"))
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.matchForDelete(events, tessera.id, "follow-up"))
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.matchForDelete(events, tessera.id, "birthday"))
        assertEquals(TessCalendar.DeleteMatch.None, TessCalendar.matchForDelete(events, tessera.id, "Picnic"))
        // An exact title on Work never beats — or stands in for — a partial one in Tessera.
        val both = events + TessCalendar.Event(61, "Dentist", work.id)
        assertEquals(TessCalendar.DeleteMatch.Found(events[1]), TessCalendar.matchForDelete(both, tessera.id, "Dentist"))
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.matchForDelete(both - events[1], tessera.id, "Dentist"))
        // No Tessera calendar at all (never created, or its lookup failed): nothing is hers to delete.
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.matchForDelete(events, null, "Dentist"))
    }

    @Test fun tessFindsTheEventThroughTheProviderAndNeverCreatesACalendarToDoIt() {
        fake.instance(WORK_EVENT, "Standup", 1_790_000_000_000L, work.id)
        fake.instance(LOCAL, "Dentist", 1_790_000_100_000L, tessera.id)
        fake.instance(70, null, 1_790_000_200_000L, work.id)
        val found = TessCalendar.findForDelete(access, "dentist", 1_789_999_000_000L)
        assertEquals(TessCalendar.DeleteMatch.Found(TessCalendar.Event(LOCAL, "Dentist", tessera.id)), found)
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.findForDelete(access, "standup", 1_789_999_000_000L))
        // A synced copy is not named beside its original (Q-16-2).
        allow(personal)
        val copy = mapped(personal)
        fake.instance(copy, "Dentist", 1_790_000_100_000L, personal.id)
        assertEquals(listOf(WORK_EVENT, LOCAL), TessCalendar.events(access, 1_789_999_000_000L, 86_400_000L).map { it.id })
        // The Tessera calendar is gone: the title is "elsewhere", and looking for it created nothing.
        fake.calendars.removeAll { it["_id"] == tessera.id }
        assertEquals(TessCalendar.DeleteMatch.Elsewhere, TessCalendar.findForDelete(access, "standup", 1_789_999_000_000L))
        noWrites()
    }

    // ================================================================== the one creator of Tessera (r3 D7)

    @Test fun theTesseraCalendarIsCreatedOnlyAfterALookupThatAnsweredAndFoundNone() {
        // Found: its id, and no write.
        assertEquals(tessera.id, LocalCalendar.id(access))
        assertEquals(LocalCalendar.Lookup.Found(tessera.id), LocalCalendar.find(access))
        noWrites()
        // A lookup that did not answer — no cursor, or READ_CALENDAR revoked — is not a lookup that found nothing:
        // no calendar is created after it, however often it is asked.
        fake.calendars.removeAll { it["_id"] == tessera.id }
        fake.noAnswer = { it.uri.table == ProviderTable.CALENDARS }
        repeat(3) { assertNull(LocalCalendar.id(access)) }
        assertTrue(LocalCalendar.find(access) is LocalCalendar.Lookup.Failed)
        fake.noAnswer = { false }
        fake.throwOnQuery = { it.uri.table == ProviderTable.CALENDARS }
        repeat(3) { assertNull(LocalCalendar.id(access)) }
        assertNull(LocalCalendar.existingId(access))
        noWrites()
        // Absent, by a query that answered: created once, as its own sync adapter; found from then on.
        fake.throwOnQuery = { false }
        assertNull(LocalCalendar.existingId(access))
        noWrites()
        val created = LocalCalendar.id(access)
        assertEquals(listOf(ProviderUri(ProviderTable.CALENDARS, syncAdapterAccount = "Tessera")), fake.writes.map { it.uri })
        assertEquals(created, LocalCalendar.id(access))
        assertEquals(1, fake.writes.size)
        // Another LOCAL calendar — Birthdays, another app's — is never taken for it.
        assertFalse(created == birthdays.id || created == otherLocal.id)
    }

    // ================================================================== the allowed list follows the provider

    @Test fun theAllowedListDropsACalendarThatLeftThePhoneAndKeepsItWhenTheReadFailed() {
        allow(personal, work)
        store.update { SyncStateRules.setHidden(it, shared.key, true) }
        // A read that did not answer concludes nothing.
        fake.noAnswer = { it.uri.table == ProviderTable.CALENDARS }
        SyncAllowList.followProvider(access)
        fake.noAnswer = { false }
        fake.throwOnQuery = { it.uri.table == ProviderTable.CALENDARS }
        SyncAllowList.followProvider(access)
        fake.throwOnQuery = { false }
        assertEquals(listOf(personal.key, work.key), store.current.allowed)
        // Personal's account is removed; Work's calendar comes back under the same id with another name of its own.
        fake.calendars.removeAll { it["account_name"] == "me@example.com" }
        (fake.calendars.first { it["_id"] == work.id })["name"] = "team-cal"
        SyncAllowList.followProvider(access)
        assertEquals(emptyList<CalendarKey>(), store.current.allowed)
        assertEquals(emptyList<CalendarKey>(), store.current.hidden)
        // The account added again, under the ids it had: it starts not allowed, and a Sync to it writes nothing.
        fake.calendar(5, "me@example.com", "com.google", 700, "personal-cal", "Personal")
        SyncAllowList.followProvider(access)
        assertEquals(emptyList<CalendarKey>(), store.current.allowed)
        assertEquals(CalendarSync.Outcome.Refused, CalendarSync.sync(access, LOCAL, personal.key))
        noWrites()
    }

    // ================================================================== the row URI helper

    @Test fun aRowsIdIsTheEndOfItsUrisPath() {
        assertEquals(42L, rowIdOf("content://com.android.calendar/events/42"))
        assertEquals(3L, rowIdOf("content://com.android.calendar/calendars/3?caller_is_syncadapter=true&account_name=Tessera&account_type=LOCAL"))
        assertEquals(3L, rowIdOf("content://com.android.calendar/calendars/3#x"))
        assertNull(rowIdOf("content://com.android.calendar/events"))
        assertNull(rowIdOf(null))
    }
}
