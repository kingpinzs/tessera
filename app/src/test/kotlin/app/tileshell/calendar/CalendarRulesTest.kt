package app.tileshell.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Phase 16 build task 3: the Calendar data layer's pure rules — the store, the birthday forms, event values, Sync's compare, the receiver's plan. */
class CalendarRulesTest {
    private val personal = CalendarKey(5, "qa.personal@example.com", "com.google")
    private val work = CalendarKey(6, "qa.work@example.com", "com.google")
    private val denver: ZoneId = ZoneId.of("America/Denver")

    // ---------------------------------------------------------------- calendar_sync.json's rules

    @Test fun nothingIsAllowedByDefault() {
        assertEquals(emptyList<CalendarKey>(), SyncState().allowed)
    }

    @Test fun aRemovedAndReAddedAccountStartsNotAllowed() {
        var state = SyncStateRules.setAllowed(SyncState(), personal, true)
        // The account is removed: its calendar no longer lists.
        state = SyncStateRules.prune(state, setOf(work))
        assertEquals(emptyList<CalendarKey>(), state.allowed)
        // Added again it has a new id — and even with the old id back, the tick is gone.
        state = SyncStateRules.prune(state, setOf(work, personal.copy(id = 9), personal))
        assertEquals(emptyList<CalendarKey>(), state.allowed)
    }

    @Test fun anIdHandedToAnotherAccountIsNotTheAllowedCalendar() {
        val state = SyncStateRules.setAllowed(SyncState(), personal, true)
        // Calendar ids are reused after a delete (Verify at build start 3): the same id under another account.
        assertEquals(emptyList<CalendarKey>(), SyncStateRules.prune(state, setOf(CalendarKey(5, "qa.work@example.com", "com.google"))).allowed)
    }

    @Test fun allowingTwiceKeepsOneEntryAndUntickingRemovesIt() {
        var state = SyncStateRules.setAllowed(SyncState(), personal, true)
        state = SyncStateRules.setAllowed(state, personal, true)
        assertEquals(listOf(personal), state.allowed)
        assertEquals(emptyList<CalendarKey>(), SyncStateRules.setAllowed(state, personal, false).allowed)
    }

    @Test fun pruningKeepsAMappingWhoseTargetIsGone() {
        val state = SyncStateRules.map(SyncState(allowed = listOf(personal)), SyncMapping(10, personal, 77))
        val pruned = SyncStateRules.prune(state, emptySet())
        // The mapping stays: it is what makes Sync say the calendar is no longer on this phone.
        assertEquals(setOf(10L), pruned.mappings.keys)
        assertEquals(emptyList<CalendarKey>(), pruned.allowed)
    }

    @Test fun aMappingWhoseLocalEventIsGoneIsDropped() {
        var state = SyncStateRules.map(SyncState(), SyncMapping(10, personal, 77))
        state = SyncStateRules.map(state, SyncMapping(11, personal, 78))
        val after = SyncStateRules.dropGoneOriginals(state, existingLocalEvents = setOf(11))
        assertEquals(setOf(11L), after.mappings.keys)
        assertEquals(setOf(78L), SyncStateRules.copyIds(after))
    }

    @Test fun theHiddenSetIsTheCopiesAndTheirExceptionEvents() {
        var state = SyncStateRules.map(SyncState(), SyncMapping(10, personal, 77))
        state = SyncStateRules.map(state, SyncMapping(11, personal, 78))
        // 77 is a recurring copy with two exception events; 90 is an exception of an event nobody maps.
        val hidden = SyncStateRules.hiddenEventIds(state, mapOf(77L to listOf(81L, 82L), 50L to listOf(90L)))
        assertEquals(setOf(77L, 78L, 81L, 82L), hidden)
    }

    @Test fun theHiddenListIsTheShellsOwnAndFollowsThePresentCalendars() {
        var state = SyncStateRules.setHidden(SyncState(), work, true)
        assertEquals(listOf(work), state.hidden)
        state = SyncStateRules.setHidden(state, work, false)
        assertEquals(emptyList<CalendarKey>(), state.hidden)
        assertEquals(emptyList<CalendarKey>(), SyncStateRules.prune(SyncStateRules.setHidden(state, work, true), setOf(personal)).hidden)
    }

    @Test fun theNotifiedSetKeepsOnlyAlertsThatCanStillComeBack() {
        val state = SyncState(notifiedAlerts = setOf("a", "b", "c"))
        // "a" was dismissed (no longer due in SCHEDULED or FIRED); "d" is new.
        assertEquals(setOf("b", "c", "d"), SyncStateRules.keepNotified(state, live = setOf("b", "c", "d"), added = setOf("d")).notifiedAlerts)
    }

    @Test fun theMarkerNamesTheCalendarAndItsAccountAndWarnsWhenTheTargetIsGoneOrReadOnly() {
        val state = SyncStateRules.map(SyncState(allowed = listOf(personal)), SyncMapping(10, personal, 77))
        val calendar = CalendarInfo(5, "qa.personal@example.com", "com.google", "Personal", null, 700)
        assertEquals(SyncMarker(5, "Personal", "qa.personal@example.com", warning = false, targetAllowed = true), CalendarSync.marker(state, 10, listOf(calendar)))
        // Removed from the phone: the account's name is what is left, with the warning.
        assertEquals(SyncMarker(5, "qa.personal@example.com", "qa.personal@example.com", warning = true, targetAllowed = false), CalendarSync.marker(state, 10, emptyList()))
        // Read-only now.
        assertEquals(true, CalendarSync.marker(state, 10, listOf(calendar.copy(accessLevel = 200)))?.warning)
        // Un-ticked on "Can sync to": no warning, and "both" is not offered.
        assertEquals(SyncMarker(5, "Personal", "qa.personal@example.com", warning = false, targetAllowed = false), CalendarSync.marker(state.copy(allowed = emptyList()), 10, listOf(calendar)))
        assertNull(CalendarSync.marker(state, 11, listOf(calendar)))
    }

    @Test fun canSyncToListsOnlyNonLocalCalendarsThePhoneMayWrite() {
        val calendars = listOf(
            CalendarInfo(1, "Tessera", "LOCAL", "Tessera", null, 700),
            CalendarInfo(2, "Tessera Birthdays", "LOCAL", "Birthdays", null, 200),
            CalendarInfo(5, "qa.personal@example.com", "com.google", "Personal", null, 700),
            CalendarInfo(6, "qa.work@example.com", "com.google", "Work", null, 700),
            CalendarInfo(7, "qa.personal@example.com", "com.google", "Shared", null, 200),
            CalendarInfo(8, "qa", "LOCAL", "QA", null, 700),
            CalendarInfo(9, "x@example.com", "com.example", "Contributor", null, 500),
        )
        assertEquals(listOf(5L, 6L, 9L), CalendarSync.candidates(calendars).map { it.id })
        // The picker: only what the user ticked.
        assertEquals(listOf(5L), CalendarSync.targets(calendars, SyncState(allowed = listOf(personal))).map { it.id })
        assertEquals(emptyList<Long>(), CalendarSync.targets(calendars, SyncState()).map { it.id })
    }

    // ---------------------------------------------------------------- birthdays

    private val today = LocalDate.of(2026, 10, 1)

    @Test fun aFullDateIsAYearlyEventFromThatDate() {
        assertEquals(BirthdayRules.Birthday(LocalDate.of(1990, 10, 1), "FREQ=YEARLY"), BirthdayRules.parse("1990-10-01", today))
    }

    @Test fun aNoYearDateIsAYearlyEventFromThisYearsDate() {
        assertEquals(BirthdayRules.Birthday(LocalDate.of(2026, 10, 2), "FREQ=YEARLY"), BirthdayRules.parse("--10-02", today))
    }

    @Test fun the29thOfFebruaryRepeatsOnFebruarysLastDayEveryTwelveMonths() {
        assertEquals(BirthdayRules.Birthday(LocalDate.of(1992, 2, 29), "FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1"), BirthdayRules.parse("1992-02-29", today))
        // No year, in a year without a 29 February: from that year's 28 February.
        assertEquals(BirthdayRules.Birthday(LocalDate.of(2026, 2, 28), "FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1"), BirthdayRules.parse("--02-29", today))
        // No year, in a leap year: from its 29 February.
        assertEquals(BirthdayRules.Birthday(LocalDate.of(2028, 2, 29), "FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1"), BirthdayRules.parse("--02-29", LocalDate.of(2028, 6, 1)))
        // 28 February is an ordinary yearly birthday.
        assertEquals("FREQ=YEARLY", BirthdayRules.parse("1990-02-28", today)?.rrule)
    }

    @Test fun aValueInNeitherFormIsSkipped() {
        for (bad in listOf(null, "", "1990", "10/01/1990", "1990-13-01", "1990-02-30", "1991-02-29", "--13-01", "--02-30", "19901001", "1990-10-01T00:00:00Z", "-10-01", "Oct 1")) {
            assertNull("[$bad]", BirthdayRules.parse(bad, today))
        }
    }

    @Test fun theBirthdayTitle() {
        assertEquals("Ann Lee's birthday", BirthdayRules.title("Ann Lee"))
    }

    @Test fun theBirthdayDiffInsertsWhatIsMissingAndDeletesWhatIsNotWanted() {
        val ann = BirthdayRules.Entry("Ann Lee's birthday", 100, "FREQ=YEARLY")
        val bob = BirthdayRules.Entry("Bob Stone's birthday", 200, "FREQ=YEARLY")
        assertEquals(emptyList<Long>() to listOf(ann), BirthdayRules.diff(emptyList(), listOf(ann)))
        assertEquals(emptyList<Long>() to emptyList<BirthdayRules.Entry>(), BirthdayRules.diff(listOf(7L to ann), listOf(ann)))
        // The last birthday goes: its event is deleted (the calendar is kept, empty, by the writer).
        assertEquals(listOf(7L) to emptyList<BirthdayRules.Entry>(), BirthdayRules.diff(listOf(7L to ann), emptyList()))
        // A changed date is a delete and an insert.
        assertEquals(listOf(7L) to listOf(ann.copy(startMs = 150)), BirthdayRules.diff(listOf(7L to ann), listOf(ann.copy(startMs = 150))))
        // Two people with one name and one birthday are two events.
        assertEquals(emptyList<Long>() to listOf(ann), BirthdayRules.diff(listOf(7L to ann), listOf(ann, ann)))
        assertEquals(listOf(8L) to emptyList<BirthdayRules.Entry>(), BirthdayRules.diff(listOf(7L to ann, 8L to ann, 9L to bob), listOf(ann, bob)))
    }

    // ---------------------------------------------------------------- event values

    private fun draft(
        startDate: LocalDate = LocalDate.of(2026, 10, 2), startTime: LocalTime = LocalTime.of(9, 0),
        endDate: LocalDate = LocalDate.of(2026, 10, 2), endTime: LocalTime = LocalTime.of(10, 0),
    ) = EventDraft(title = " Standup ", location = "Room 2", startDate = startDate, startTime = startTime, endDate = endDate, endTime = endTime)

    private fun ok(built: EventRules.Built) = (built as EventRules.Built.Ok).values

    @Test fun aTimedEventCarriesTheDeviceZoneAndADtend() {
        val v = ok(EventRules.build(draft(), denver))
        assertEquals("Standup", v.title)
        assertEquals("America/Denver", v.timezone)
        // 2026-10-02 09:00 MDT is 15:00 UTC.
        assertEquals(1_790_953_200_000L, v.dtstart)
        assertEquals(v.dtstart + 3_600_000L, v.dtend)
        assertNull(v.duration)
        assertNull(v.rrule)
        assertFalse(v.allDay)
    }

    @Test fun aRepeatingEventWritesRruleAndDurationAndNoDtend() {
        val v = ok(EventRules.build(draft().copy(repeat = Repeat.WEEKLY), denver))
        assertEquals("FREQ=WEEKLY", v.rrule)
        assertEquals("P3600S", v.duration)
        assertNull(v.dtend)
    }

    @Test fun anAllDayEventIsDateAnchoredInUtc() {
        val v = ok(EventRules.build(draft(endDate = LocalDate.of(2026, 10, 4)).copy(allDay = true), denver))
        assertTrue(v.allDay)
        assertEquals("UTC", v.timezone)
        // 2026-10-02 00:00 UTC, and the midnight after the last day (three days).
        assertEquals(1_790_899_200_000L, v.dtstart)
        assertEquals(v.dtstart + 3 * 86_400_000L, v.dtend)
        // The same instants whatever the device zone.
        assertEquals(v, ok(EventRules.build(draft(endDate = LocalDate.of(2026, 10, 4)).copy(allDay = true), ZoneId.of("Asia/Tokyo"))))
        // Repeating: a duration in days.
        assertEquals("P3D", ok(EventRules.build(draft(endDate = LocalDate.of(2026, 10, 4)).copy(allDay = true, repeat = Repeat.YEARLY), denver)).duration)
    }

    @Test fun anEndBeforeItsStartIsRefused() {
        assertTrue(EventRules.build(draft(endTime = LocalTime.of(8, 0)), denver) is EventRules.Built.Invalid)
        assertTrue(EventRules.build(draft(endDate = LocalDate.of(2026, 10, 1)).copy(allDay = true), denver) is EventRules.Built.Invalid)
        // A zero-length event is not before its start.
        assertTrue(EventRules.build(draft(endTime = LocalTime.of(9, 0)), denver) is EventRules.Built.Ok)
    }

    @Test fun anUntouchedStoredEndInsideARepeatedHourIsWrittenBackAsStored() {
        // 2026-11-01 is the fall-back night in Denver: 01:30 happens twice. 23:30 MDT → 01:30 MST is three hours.
        val start = EventRules.toMs(LocalDate.of(2026, 10, 31), LocalTime.of(23, 30), denver)
        val end = start + 3 * 3_600_000L
        assertEquals(LocalTime.of(1, 30), EventRules.local(end, denver).toLocalTime())
        val d = draft(LocalDate.of(2026, 10, 31), LocalTime.of(23, 30), LocalDate.of(2026, 11, 1), LocalTime.of(1, 30)).copy(eventId = 9, loadedStartMs = start, loadedEndMs = end)
        val v = ok(EventRules.build(d, denver))
        assertEquals(start, v.dtstart)
        assertEquals(end, v.dtend)
        // A changed end is read as wall-clock time.
        assertEquals(EventRules.toMs(LocalDate.of(2026, 11, 1), LocalTime.of(2, 30), denver), ok(EventRules.build(d.copy(endTime = LocalTime.of(2, 30)), denver)).dtend)
    }

    @Test fun anUntouchedRepeatWritesTheStoredRuleBackWhole() {
        val d = draft().copy(eventId = 9, repeat = Repeat.WEEKLY, loadedRepeat = Repeat.WEEKLY, loadedRrule = "FREQ=WEEKLY;COUNT=10;BYDAY=FR")
        assertEquals("FREQ=WEEKLY;COUNT=10;BYDAY=FR", ok(EventRules.build(d, denver)).rrule)
        assertEquals("FREQ=DAILY", ok(EventRules.build(d.copy(repeat = Repeat.DAILY), denver)).rrule)
        assertNull(ok(EventRules.build(d.copy(repeat = Repeat.NONE), denver)).rrule)
    }

    @Test fun theRepeatValueOfAStoredRuleIsItsFreq() {
        assertEquals(Repeat.WEEKLY, EventRules.repeatOf("FREQ=WEEKLY;COUNT=10"))
        assertEquals(Repeat.MONTHLY, EventRules.repeatOf("FREQ=MONTHLY;BYDAY=2TU"))
        assertEquals(Repeat.YEARLY, EventRules.repeatOf("RRULE:FREQ=YEARLY"))
        assertEquals(Repeat.DAILY, EventRules.repeatOf("freq=DAILY"))
        assertEquals(Repeat.NONE, EventRules.repeatOf(null))
        assertEquals(Repeat.NONE, EventRules.repeatOf(""))
    }

    @Test fun thisAndFollowingEndsTheMasterWithUntilAndStartsANewSeries() {
        // The fifth weekly occurrence of a series that starts 2026-10-02 15:00 UTC.
        val fifth = 1_790_953_200_000L + 4 * 7 * 86_400_000L
        assertEquals("FREQ=WEEKLY;UNTIL=20261030T145959Z", EventRules.endBefore("FREQ=WEEKLY;COUNT=10", fifth, allDay = false))
        assertEquals("FREQ=WEEKLY;BYDAY=FR;UNTIL=20261030T145959Z", EventRules.endBefore("FREQ=WEEKLY;UNTIL=20270101T000000Z;BYDAY=FR", fifth, allDay = false))
        // An all-day rule's UNTIL is a date: the day before the occurrence.
        assertEquals("FREQ=DAILY;UNTIL=20261005", EventRules.endBefore("FREQ=DAILY", EventRules.allDayStartMs(LocalDate.of(2026, 10, 6)), allDay = true))
        // The new series keeps what is left of a COUNT; a rule with none is kept as it is.
        assertEquals("FREQ=WEEKLY;COUNT=6", EventRules.tailOf("FREQ=WEEKLY;COUNT=10", 4))
        assertEquals("FREQ=WEEKLY;COUNT=1", EventRules.tailOf("FREQ=WEEKLY;COUNT=3", 9))
        assertEquals("FREQ=WEEKLY;BYDAY=FR", EventRules.tailOf("FREQ=WEEKLY;BYDAY=FR", 4))
    }

    @Test fun durationsAsTheProviderStoresThem() {
        assertEquals(3_600_000L, EventRules.durationMs("P3600S"))
        assertEquals(3_600_000L, EventRules.durationMs("PT1H"))
        assertEquals(5_400_000L, EventRules.durationMs("PT1H30M"))
        assertEquals(86_400_000L, EventRules.durationMs("P1D"))
        assertEquals(7 * 86_400_000L, EventRules.durationMs("P1W"))
        assertEquals(90_000_000L, EventRules.durationMs("P1DT1H"))
        assertNull(EventRules.durationMs(null))
        assertNull(EventRules.durationMs("P"))
        assertNull(EventRules.durationMs("one hour"))
    }

    @Test fun theDaysAnInstanceCovers() {
        val start = EventRules.toMs(LocalDate.of(2026, 10, 2), LocalTime.of(9, 0), denver)
        // A timed event inside one day.
        assertEquals(LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 10, 2), EventRules.days(start, start + 3_600_000L, false, denver))
        // Three days.
        assertEquals(LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 10, 4), EventRules.days(start, start + 2 * 86_400_000L + 3_600_000L, false, denver))
        // Ending at midnight does not cover the next day.
        val evening = EventRules.toMs(LocalDate.of(2026, 10, 2), LocalTime.of(22, 0), denver)
        assertEquals(LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 10, 2), EventRules.days(evening, evening + 2 * 3_600_000L, false, denver))
        // An all-day event keeps its date in a zone behind UTC and in one ahead of it.
        val allDay = EventRules.allDayStartMs(LocalDate.of(2026, 10, 2))
        assertEquals(LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 10, 2), EventRules.days(allDay, allDay + 86_400_000L, true, denver))
        assertEquals(LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 10, 2), EventRules.days(allDay, allDay + 86_400_000L, true, ZoneId.of("Asia/Tokyo")))
        // A zero-length event is on its day.
        assertEquals(LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 10, 2), EventRules.days(start, start, false, denver))
    }

    @Test fun theFirstDayOfTheWeekFollowsTheLocaleUnlessSet() {
        assertEquals(DayOfWeek.SUNDAY, EventRules.firstDayOfWeek(null, Locale.US))
        assertEquals(DayOfWeek.MONDAY, EventRules.firstDayOfWeek(null, Locale.UK))
        assertEquals(DayOfWeek.SATURDAY, EventRules.firstDayOfWeek(6, Locale.US))
        assertEquals(DayOfWeek.SUNDAY, EventRules.firstDayOfWeek(0, Locale.US))
        // 2026-10-01 is a Thursday.
        assertEquals(LocalDate.of(2026, 9, 28), EventRules.weekStart(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 27), EventRules.weekStart(today, DayOfWeek.SUNDAY))
        assertEquals(today, EventRules.weekStart(today, DayOfWeek.THURSDAY))
    }

    @Test fun theMonthPanelIsSixWeeksAroundTheMonth() {
        val grid = EventRules.monthGrid(today, DayOfWeek.MONDAY)
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.first())
        assertEquals(LocalDate.of(2026, 11, 8), grid.last())
        assertTrue(LocalDate.of(2026, 10, 31) in grid)
    }

    @Test fun theLabelTintLightensTheCalendarColour() {
        fun rgb(argb: Int) = Triple(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
        // r11/calendar.md K3.9's three measured pairs, each channel within 40 levels of the measured tint (an approximation).
        val pairs = listOf(
            Triple(168, 0, 0) to Triple(245, 72, 85),
            Triple(0, 120, 215) to Triple(48, 163, 250),
            Triple(215, 59, 2) to Triple(254, 116, 87),
        )
        for ((color, measured) in pairs) {
            val tint = rgb(EventRules.tint((0xFF shl 24) or (color.first shl 16) or (color.second shl 8) or color.third))
            assertTrue("$color -> $tint, measured $measured", kotlin.math.abs(tint.first - measured.first) <= 40 && kotlin.math.abs(tint.second - measured.second) <= 40 && kotlin.math.abs(tint.third - measured.third) <= 40)
        }
        // A colour already light is kept.
        assertEquals(Triple(254, 200, 120), rgb(EventRules.tint(0xFFFEC878.toInt())))
    }

    @Test fun noTitleAndReminderWording() {
        assertEquals("(No title)", EventRules.shownTitle(null))
        assertEquals("(No title)", EventRules.shownTitle("  "))
        assertEquals("Standup", EventRules.shownTitle("Standup"))
        assertEquals("None", EventRules.reminderLabel(null))
        assertEquals("At start time", EventRules.reminderLabel(0))
        assertEquals("10 minutes", EventRules.reminderLabel(10))
        assertEquals("1 hour", EventRules.reminderLabel(60))
        assertEquals("12 hours", EventRules.reminderLabel(720))
        assertEquals("1 day", EventRules.reminderLabel(1440))
        assertEquals("1 week", EventRules.reminderLabel(10080))
        assertEquals("45 minutes", EventRules.reminderLabel(45))
    }

    // ---------------------------------------------------------------- Sync's compare (r3 V13)

    private fun event(id: Long, title: String = "Standup", rrule: String? = null, status: Int? = null, originalInstanceTime: Long? = null, dtstart: Long = 1000L, description: String? = null) = EventDetail(
        id = id, calendarId = 1, title = title, location = null, description = description, dtstart = dtstart,
        dtend = if (rrule == null) dtstart + 3_600_000L else null, duration = if (rrule == null) null else "P3600S", allDay = false, timezone = "America/Denver",
        rrule = rrule, rdate = null, exrule = null, exdate = null, originalId = null, originalInstanceTime = originalInstanceTime,
        availability = 0, status = status, color = null,
    )

    @Test fun aCopyEqualToTheLocalEventDiffersInNothing() {
        val local = SyncRules.snapshot(event(10), listOf(10 to 1), emptyList())
        // Another id, another calendar, an empty string where the local row holds null: the copied fields are equal.
        val copy = SyncRules.snapshot(event(77).copy(calendarId = 5, location = "", description = ""), listOf(10 to 1), emptyList())
        assertFalse(SyncRules.differs(local, copy))
    }

    @Test fun anEditOnTheOtherSideDiffers() {
        val local = SyncRules.snapshot(event(10), emptyList(), emptyList())
        // Under the old stored-hash rule this read a no-op; the copy is compared with the local event instead.
        assertTrue(SyncRules.differs(local, SyncRules.snapshot(event(77, title = "Standup (moved)"), emptyList(), emptyList())))
        assertTrue(SyncRules.differs(local, SyncRules.snapshot(event(77, dtstart = 2000L), emptyList(), emptyList())))
    }

    @Test fun remindersAndExceptionsAreCompared() {
        val master = event(10, rrule = "FREQ=WEEKLY;COUNT=5")
        val exception = event(11, title = "Standup (retitled)", originalInstanceTime = 5000L, dtstart = 5000L)
        val local = SyncRules.snapshot(master, listOf(10 to 1), listOf(exception))
        val copyMaster = event(77, rrule = "FREQ=WEEKLY;COUNT=5")
        assertFalse(SyncRules.differs(local, SyncRules.snapshot(copyMaster, listOf(10 to 1), listOf(exception.copy(id = 78)))))
        assertTrue(SyncRules.differs(local, SyncRules.snapshot(copyMaster, emptyList(), listOf(exception.copy(id = 78)))))
        assertTrue(SyncRules.differs(local, SyncRules.snapshot(copyMaster, listOf(10 to 1), emptyList())))
        assertTrue(SyncRules.differs(local, SyncRules.snapshot(copyMaster, listOf(10 to 1), listOf(exception.copy(id = 78, title = "Standup")))))
    }

    @Test fun aRepeatingRowsLengthIsItsDurationHoweverItIsSpelled() {
        val local = SyncRules.snapshot(event(10, rrule = "FREQ=WEEKLY"), emptyList(), emptyList())
        val copy = SyncRules.snapshot(event(77, rrule = "FREQ=WEEKLY").copy(duration = "PT1H", dtend = 99L), emptyList(), emptyList())
        assertFalse(SyncRules.differs(local, copy))
    }

    @Test fun aCancelledOccurrenceComparesByItsInstanceAlone() {
        val a = SyncRules.exception(event(11, title = "old title", status = SyncRules.STATUS_CANCELED, originalInstanceTime = 5000L))
        val b = SyncRules.exception(event(78, title = "new title", status = SyncRules.STATUS_CANCELED, originalInstanceTime = 5000L, dtstart = 7000L))
        assertEquals(a, b)
        assertTrue(a.cancelled)
    }

    // ---------------------------------------------------------------- the reminder receiver's plan (r3 D6 (c), Q-16-2)

    /** An alert of [event] whose occurrence begins at [begin] and whose alarm time is 600 ms before it. */
    private fun alert(id: Long, event: Long, state: Int = 0, begin: Long = 1000) = AlertRow(id, event, begin, begin + 1000, begin - 600, state, 10, "Standup", false)

    /** The plan of a shell that first started long before every alert here (a cut-off of 0). */
    private fun actions(due: List<AlertRow>, notified: Set<String> = emptySet(), copies: Set<Long> = emptySet()) =
        ReminderRules.plan(due, notified, copies, sinceMs = 0).actions

    @Test fun oneNotificationPerDueAlertNotYetHandled() {
        val plan = actions(listOf(alert(1, 10), alert(2, 11)))
        assertEquals(listOf(1L, 2L), plan.filterIsInstance<ReminderRules.Action.Notify>().map { it.row.id })
    }

    @Test fun aRowAnotherAppAlreadyFiredIsStillNotified() {
        val plan = actions(listOf(alert(1, 10, state = 1)))
        assertEquals(1, plan.filterIsInstance<ReminderRules.Action.Notify>().size)
    }

    @Test fun aSecondOrForgedPokeForAHandledAlertDoesNothing() {
        assertEquals(emptyList<ReminderRules.Action>(), actions(listOf(alert(1, 10, state = 1)), notified = setOf(alert(1, 10).key)))
        // And with nothing due, nothing happens at all.
        assertEquals(ReminderRules.Plan(emptyList(), 0), ReminderRules.plan(emptyList(), emptySet(), emptySet(), sinceMs = 0))
    }

    @Test fun aNewAlertThatInheritedAHandledRowsIdStillNotifies() {
        // The provider hands a deleted row's _id out again: alert 1 was event 10's and was handled; it is deleted, and
        // the next alert — another event, or the same event's next occurrence — is given id 1.
        val handled = setOf(alert(1, 10).key)
        assertEquals(1, actions(listOf(alert(1, 11)), handled).size)
        assertEquals(1, actions(listOf(alert(1, 10, begin = 9000)), handled).size)
        // The same alert under a new row id (the provider wrote its row again) is still the alert that was handled.
        assertEquals(0, actions(listOf(alert(7, 10)), handled).size)
    }

    @Test fun aSyncedCopysAlertIsSkippedAndItsOriginalsIsNotified() {
        val plan = actions(listOf(alert(1, 10), alert(2, 77)), copies = setOf(77))
        assertEquals(listOf(1L), plan.filterIsInstance<ReminderRules.Action.Notify>().map { it.row.id })
        assertEquals(listOf(77L), plan.filterIsInstance<ReminderRules.Action.SkipCopy>().map { it.row.eventId })
    }

    // ---------------------------------------------------------------- fix round F7: the notified key's parts, and what is recorded

    /** One occurrence (begin 100 000) of event 10 with two reminders: 60 minutes before and 10 minutes before. */
    private val sixtyBefore = AlertRow(1, 10, 100_000, 101_000, alarmTimeMs = 40_000, state = 0, minutes = 60, title = "Standup", allDay = false)
    private val tenBefore = AlertRow(2, 10, 100_000, 101_000, alarmTimeMs = 90_000, state = 0, minutes = 10, title = "Standup", allDay = false)

    @Test fun twoRemindersOnOneOccurrenceBothNotify() {
        // The alarm time is part of the key: the two alerts share the event and the occurrence and nothing else.
        assertEquals("10:100000:40000", sixtyBefore.key)
        assertEquals("10:100000:90000", tenBefore.key)
        // The first poke, at the 60-minute alarm: one notification, recorded.
        val first = actions(listOf(sixtyBefore))
        assertEquals(listOf<ReminderRules.Action>(ReminderRules.Action.Notify(sixtyBefore)), first)
        val notified = SyncStateRules.keepNotified(SyncState(), live = setOf(sixtyBefore.key), added = ReminderRules.recorded(first) { true }).notifiedAlerts
        // The second poke, at the 10-minute alarm: the first alert is still due (FIRED, not swiped) and handled; the
        // second is new and notifies — it is not taken for the first because it is the same event and occurrence.
        val second = actions(listOf(sixtyBefore.copy(state = 1), tenBefore), notified)
        assertEquals(listOf<ReminderRules.Action>(ReminderRules.Action.Notify(tenBefore)), second)
        // And the same under one row id (the 60-minute row was deleted and its id handed to the 10-minute alert).
        assertEquals(listOf<ReminderRules.Action>(ReminderRules.Action.Notify(tenBefore.copy(id = 1))), actions(listOf(tenBefore.copy(id = 1)), notified))
    }

    @Test fun theSameAlarmTimeOnAnotherOccurrenceStillNotifies() {
        // The begin is part of the key: a daily event with two reminders, 10 minutes and a day and 10 minutes, has two
        // alerts due at one instant — today's occurrence's short one and tomorrow's occurrence's long one. They share
        // the event and the alarm time and nothing else, and each one notifies.
        val today = AlertRow(1, 10, 100_000, 101_000, alarmTimeMs = 90_000, state = 0, minutes = 10, title = "Standup", allDay = false)
        val tomorrow = today.copy(id = 2, beginMs = 86_500_000, endMs = 86_501_000, minutes = 1450)
        assertEquals("10:100000:90000", today.key)
        assertEquals("10:86500000:90000", tomorrow.key)
        assertEquals(listOf<ReminderRules.Action>(ReminderRules.Action.Notify(tomorrow)), actions(listOf(today, tomorrow), notified = setOf(today.key)))
        // Another event with the same begin and alarm time is not that alert either.
        assertEquals(1, actions(listOf(today.copy(id = 3, eventId = 11)), notified = setOf(today.key)).size)
    }

    @Test fun aDueAlertWhosePostFailedIsNotRecordedAsNotified() {
        val copy = alert(3, 77, begin = 7000)
        val plan = actions(listOf(sixtyBefore, tenBefore, copy), copies = setOf(77))
        assertEquals(3, plan.size)
        // Notifications are off for the 10-minute alert's post (the post answers false): only what was done is recorded.
        val recorded = ReminderRules.recorded(plan) { it.row.id != tenBefore.id }
        assertEquals(setOf(sixtyBefore.key, copy.key), recorded)
        // The store after that poke: every row is still live, and the failed one is not among the notified.
        val live = setOf(sixtyBefore.key, tenBefore.key, copy.key)
        val after = SyncStateRules.keepNotified(SyncState(), live, recorded).notifiedAlerts
        assertEquals(setOf(sixtyBefore.key, copy.key), after)
        assertFalse(tenBefore.key in after)
        // So the next poke plans it again, and only it.
        assertEquals(listOf<ReminderRules.Action>(ReminderRules.Action.Notify(tenBefore)), actions(listOf(sixtyBefore, tenBefore, copy), after, copies = setOf(77)))
        // Nothing posted at all: nothing recorded, though every row is live.
        assertEquals(emptySet<String>(), SyncStateRules.keepNotified(SyncState(), live, ReminderRules.recorded(plan) { false }).notifiedAlerts)
        // A live alert the poke never planned (already skipped as old, say) is not recorded for being live.
        assertEquals(setOf("x"), SyncStateRules.keepNotified(SyncState(), live = setOf("x", "y"), added = setOf("x")).notifiedAlerts)
    }

    // ---------------------------------------------------------------- fix round F9: one waiting poke, and a worker nothing kills

    @Test fun aPokeIsDroppedWhileOneIsAlreadyQueued() {
        val gate = PokeGate()
        assertTrue(gate.offer())    // the first broadcast queues a poke
        assertFalse(gate.offer())   // a second while it waits is dropped
        assertFalse(gate.offer())
        gate.started()              // the queued poke starts its read
        assertTrue(gate.offer())    // a broadcast after that read began queues one more — and only one
        assertFalse(gate.offer())
    }

    @Test fun aBurstOfAThousandPokesQueuesOne() {
        val gate = PokeGate()
        assertEquals(1, (1..1000).count { gate.offer() })
        // While that one runs, the rest of the burst queues one more, however long it is.
        gate.started()
        assertEquals(1, (1..1000).count { gate.offer() })
    }

    @Test fun aFailureInOnePokeIsCaughtSaidInOneLineAndItsBroadcastIsFinished() {
        for (failure in listOf(IllegalStateException("the provider is gone"), SecurityException("denied"), OutOfMemoryError("big"), StackOverflowError(), AssertionError("no"))) {
            val lines = ArrayList<String>()
            var finished = 0
            ReminderRules.contained("reminder poke", { lines += it }, { finished++ }) { throw failure }
            assertEquals(1, finished)
            assertEquals(1, lines.size)
            assertTrue(lines[0], lines[0].startsWith("reminder poke failed: ${failure.javaClass.name}"))
        }
        // The line is one line, and bounded.
        val lines = ArrayList<String>()
        ReminderRules.contained("reminder poke", { lines += it }, {}) { throw IllegalStateException("a\nb" + "x".repeat(5000)) }
        assertFalse(lines[0].contains('\n'))
        assertTrue(lines[0].length <= "reminder poke failed: ".length + 160)
        // Work that does not fail: finished once, nothing said.
        var finished = 0
        var ran = 0
        ReminderRules.contained("reminder poke", { lines += it }, { finished++ }) { ran++ }
        assertEquals(listOf(1, 1, 1), listOf(ran, finished, lines.size))
        // A ring that fails, or a broadcast that cannot be finished: nothing escapes, and the finish is still tried.
        ReminderRules.contained("reminder poke", { throw IllegalStateException("ring") }, { finished++ }) { throw IllegalStateException("task") }
        assertEquals(2, finished)
        ReminderRules.contained("reminder poke", { lines += it }, { throw IllegalStateException("Broadcast already finished") }) { ran++ }
        assertEquals(2, ran)
    }

    @Test fun aPokeThatFailsLeavesTheGateOpenAndTheWorkerThreadAlive() {
        val gate = PokeGate()
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val threads = java.util.concurrent.CopyOnWriteArrayList<Thread>()
            val done = java.util.concurrent.CountDownLatch(2)
            repeat(2) { n ->
                assertTrue(gate.offer())
                worker.execute {
                    ReminderRules.contained("reminder poke", {}, { done.countDown() }) {
                        gate.started()
                        threads += Thread.currentThread()
                        if (n == 0) throw OutOfMemoryError("the first poke fails")
                    }
                }
                // The next broadcast arrives once this poke has started.
                while (threads.size <= n) Thread.sleep(1)
            }
            assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS))
            // The poke after the failed one ran on the same thread: the failure did not end it.
            assertEquals(2, threads.size)
            assertTrue(threads[0] === threads[1])
            assertTrue(gate.offer())
        } finally {
            worker.shutdownNow()
        }
    }

    // ---------------------------------------------------------------- fix round F3: which row a state write may touch

    @Test fun aStateWriteNamesTheAlertByItsRowItsEventItsOccurrenceAndItsAlarmTime() {
        val shown = alert(7, 10, state = 1, begin = 5000)   // alarm time 4400
        val selection = ReminderRules.stateSelection(ReminderRules.identity(shown))
        assertEquals("_id = ? AND event_id = ? AND begin = ? AND alarmTime = ? AND state IN (0, 1)", selection.where)
        assertEquals(listOf("7", "10", "5000", "4400"), selection.args)
    }

    @Test fun aStaleNotificationsSwipeCannotDismissAnotherEventsAlert() {
        // A's reminder was shown from row 7. A's row is deleted and the provider hands id 7 to B's future alert.
        val a = ReminderRules.identity(alert(7, 10, state = 1, begin = 5000))
        assertTrue(ReminderRules.isStillThatAlert(a, alert(7, 10, state = 1, begin = 5000)))
        assertTrue(ReminderRules.isStillThatAlert(a, alert(7, 10, state = 0, begin = 5000)))
        // The same row id, another event's alert (possibly a work calendar's): the swipe touches nothing.
        assertFalse(ReminderRules.isStillThatAlert(a, alert(7, 11, state = 0, begin = 5000)))
        // The same event's next occurrence under the same row id, or its other reminder: nothing.
        assertFalse(ReminderRules.isStillThatAlert(a, alert(7, 10, state = 0, begin = 9000)))
        assertFalse(ReminderRules.isStillThatAlert(a, AlertRow(7, 10, 5000, 6000, alarmTimeMs = 1400, state = 0, minutes = 60, title = "Standup", allDay = false)))
        // Another row, or a row already dismissed: nothing.
        assertFalse(ReminderRules.isStillThatAlert(a, alert(8, 10, state = 1, begin = 5000)))
        assertFalse(ReminderRules.isStillThatAlert(a, alert(7, 10, state = 2, begin = 5000)))
        // A notification an earlier build posted carries no occurrence or alarm time (-1): it matches no row.
        assertFalse(ReminderRules.isStillThatAlert(ReminderRules.AlertIdentity(7, 10, -1, -1), alert(7, 10, state = 1, begin = 5000)))
    }

    // ---------------------------------------------------------------- Q-16-4: alerts due before the shell's first start

    @Test fun anAlertDueBeforeTheShellsFirstStartIsSkippedAndCounted() {
        // The shell first started at 5000. Alarm times: 4400 (before), 5000 (at), 5400 (after).
        val before = alert(1, 10, begin = 5000)
        val at = alert(2, 11, begin = 5600)
        val after = alert(3, 12, begin = 6000)
        val plan = ReminderRules.plan(listOf(before, at, after), notified = emptySet(), copies = emptySet(), sinceMs = 5000)
        // The one due before the first start gets no action at all — no notification, so no write and no record of it.
        assertEquals(listOf(2L, 3L), plan.actions.map { it.row.id })
        assertTrue(plan.actions.all { it is ReminderRules.Action.Notify })
        assertEquals(1, plan.skippedBeforeStart)
        assertEquals(listOf(at, after), ReminderRules.sinceStart(listOf(before, at, after), 5000))
    }

    @Test fun aWeekOfAnotherAppsFiredAlertsIsNotABurstOnTheFirstPoke() {
        // What the phone's calendar app fired and nobody dismissed, all due before the shell first ran, and one new alert.
        val backlog = (1L..40L).map { alert(it, 100 + it, state = 1, begin = 1000 + it) }
        val fresh = alert(99, 500, begin = 90_000)
        val plan = ReminderRules.plan(backlog + fresh, emptySet(), emptySet(), sinceMs = 50_000)
        assertEquals(listOf(99L), plan.actions.map { it.row.id })
        assertEquals(40, plan.skippedBeforeStart)
    }

    @Test fun everyPokeCountsTheOldRowsAgainBecauseNothingIsWrittenForThem() {
        // An old row is never added to the notified set and its alert row is never touched, so the next poke finds it
        // again: the "n skipped" line is one per poke.
        val old = alert(1, 10, begin = 2000)
        val first = ReminderRules.plan(listOf(old), emptySet(), emptySet(), sinceMs = 5000)
        assertEquals(ReminderRules.Plan(emptyList(), 1), first)
        val notifiedAfterFirst = SyncStateRules.keepNotified(SyncState(), live = setOf(old.key), added = first.actions.mapTo(HashSet()) { it.row.key }).notifiedAlerts
        assertEquals(emptySet<String>(), notifiedAfterFirst)
        assertEquals(1, ReminderRules.plan(listOf(old), notifiedAfterFirst, emptySet(), sinceMs = 5000).skippedBeforeStart)
    }

    @Test fun aSyncedCopysAlertAfterTheCutOffIsStillSkippedAsACopy() {
        val copyAfter = alert(1, 77, begin = 6000)
        val copyBefore = alert(2, 77, begin = 2000)
        val plan = ReminderRules.plan(listOf(copyBefore, copyAfter), emptySet(), copies = setOf(77), sinceMs = 5000)
        assertEquals(listOf<ReminderRules.Action>(ReminderRules.Action.SkipCopy(copyAfter)), plan.actions)
        // A copy's alert from before the first start is one of the old rows: counted, not named.
        assertEquals(1, plan.skippedBeforeStart)
    }

    @Test fun anAlertAlreadyHandledIsNotCountedAsOld() {
        // Handled after the cut-off: it is neither notified again nor counted as due before the first start.
        val handled = alert(1, 10, begin = 6000)
        assertEquals(ReminderRules.Plan(emptyList(), 0), ReminderRules.plan(listOf(handled), setOf(handled.key), emptySet(), sinceMs = 5000))
    }

    @Test fun theCutOffIsWrittenOnceAtTheFirstStartAndKeptAfterIt() {
        // Absent (a fresh install, a pm clear, or the first build that carries the field): the first start writes it.
        val first = SyncStateRules.remindersSince(SyncState(), nowMs = 5000)
        assertEquals(5000L, first.remindersSince)
        // A later start, or an update, keeps it; nothing else in the store moves.
        val state = first.copy(allowed = listOf(personal), notifiedAlerts = setOf("10:1000:400"))
        assertEquals(state, SyncStateRules.remindersSince(state, nowMs = 9000))
        assertEquals(state, SyncStateRules.remindersSince(state, nowMs = 5000))
    }

    @Test fun aCutOffLaterThanNowIsLoweredToNow() {
        // The clock was set back behind the stored first start: left alone, every alert until then would be "old".
        val state = SyncState(remindersSince = 9000)
        assertEquals(4000L, SyncStateRules.remindersSince(state, nowMs = 4000).remindersSince)
        // From then on an alert that comes due after the lowered cut-off notifies.
        val plan = ReminderRules.plan(listOf(alert(1, 10, begin = 5000)), emptySet(), emptySet(), sinceMs = 4000)
        assertEquals(1, plan.actions.size)
        assertEquals(0, plan.skippedBeforeStart)
    }

    @Test fun aClockSetBackLowersTheCutOffBeforeAnyPokeAndNothingElseDoes() {
        // The shell first started at 9000; the clock is set back to 4000 while its process stays alive.
        val state = SyncState(remindersSince = 9000, notifiedAlerts = setOf("10:1000:400"))
        val lowered = SyncStateRules.lowerRemindersSince(state, nowMs = 4000)
        assertEquals(state.copy(remindersSince = 4000), lowered)
        // The alert that then comes due a moment later (alarm time 4400) notifies: it is not "before the first start".
        val next = alert(1, 10, begin = 5000)
        assertEquals(1, ReminderRules.plan(listOf(next), lowered.notifiedAlerts, emptySet(), lowered.remindersSince!!).actions.size)
        // Left to the poke itself (the backstop), the cut-off would be that poke's own now — after the alert's alarm time.
        assertEquals(1, ReminderRules.plan(listOf(next), emptySet(), emptySet(), sinceMs = 4500).skippedBeforeStart)
        // A clock set forward, or to the cut-off itself, changes nothing; and a clock change never SETS the cut-off.
        assertEquals(state, SyncStateRules.lowerRemindersSince(state, nowMs = 12_000))
        assertEquals(state, SyncStateRules.lowerRemindersSince(state, nowMs = 9000))
        assertEquals(SyncState(), SyncStateRules.lowerRemindersSince(SyncState(), nowMs = 4000))
    }

    @Test fun aPokeBeforeAnyStartUpSetsTheCutOffToNowAndSkipsWhatWasAlreadyDue() {
        // The receiver ran in a process no start-up ran in, and the store has no cut-off: it becomes now (7000).
        val since = SyncStateRules.remindersSince(SyncState(), nowMs = 7000).remindersSince!!
        val dueEarlier = alert(1, 10, begin = 6000)       // alarm time 5400
        val dueThisInstant = alert(2, 11, begin = 7600)   // alarm time 7000
        val plan = ReminderRules.plan(listOf(dueEarlier, dueThisInstant), emptySet(), emptySet(), since)
        assertEquals(listOf(2L), plan.actions.map { it.row.id })
        assertEquals(1, plan.skippedBeforeStart)
    }
}
