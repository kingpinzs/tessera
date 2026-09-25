package app.tileshell.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class ClockTextTest {
    private val us = Locale.US

    private fun alarm(h: Int, m: Int, days: Set<DayOfWeek> = emptySet(), date: LocalDate? = null, on: Boolean = true, name: String = "Alarm") =
        Alarm("a1", h, m, name, days, on, AlarmSound.DEFAULT, 10, date, null, null)

    @Test fun timesFollowThe12And24HourSetting() {
        assertEquals("7:00 AM", ClockText.time(7, 0, false, us))
        assertEquals("7:10 AM", ClockText.time(7, 10, false, us))
        assertEquals("12:05 PM", ClockText.time(12, 5, false, us))
        assertEquals("12:05 AM", ClockText.time(0, 5, false, us))
        assertEquals("7:00", ClockText.time(7, 0, true, us))
        assertEquals("17:45", ClockText.time(17, 45, true, us))
    }

    @Test fun repeatSummaryIsW10MsWording() {
        assertEquals("Only once", ClockText.repeatSummary(emptySet(), us))
        assertEquals("Every day", ClockText.repeatSummary(DayOfWeek.values().toSet(), us))
        val sixDays = DayOfWeek.values().toSet() - DayOfWeek.SUNDAY
        assertEquals("Mon, Tue, Wed, Thu, Fri, Sat", ClockText.repeatSummary(sixDays, us))
        // The locale's week order: the US week starts on Sunday, so Sunday leads.
        assertEquals("Sun, Sat", ClockText.repeatSummary(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), us))
        assertEquals("Sat, Sun", ClockText.repeatSummary(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), Locale.UK))
        assertEquals(DayOfWeek.SUNDAY, ClockText.weekOrder(us).first())
        assertEquals(DayOfWeek.MONDAY, ClockText.weekOrder(Locale.UK).first())
    }

    @Test fun rowDayLineReadsTodayTomorrowOrTheDays() {
        val today = LocalDate.of(2026, 9, 23)
        assertEquals("Today", ClockText.rowDayLine(alarm(23, 0, date = today), today, us))
        assertEquals("Tomorrow", ClockText.rowDayLine(alarm(7, 0, date = today.plusDays(1)), today, us))
        assertEquals("Only once", ClockText.rowDayLine(alarm(7, 0, date = today.plusDays(1), on = false), today, us))
        assertEquals("Only once", ClockText.rowDayLine(alarm(7, 0, date = null), today, us))
        assertEquals("Mon, Fri", ClockText.rowDayLine(alarm(7, 0, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), today, us))
    }

    @Test fun countdownCaptionRoundsUpToTheMinute() {
        assertEquals("In 5 hours, 57 minutes", ClockText.countdownCaption((5 * 60 + 57) * 60_000L))
        assertEquals("In 1 hour, 1 minute", ClockText.countdownCaption(61 * 60_000L))
        assertEquals("In 1 hour", ClockText.countdownCaption(59 * 60_000L + 30_000L))
        assertEquals("In 42 minutes", ClockText.countdownCaption(42 * 60_000L))
        assertEquals("In 1 minute", ClockText.countdownCaption(5_000L)) // rounded up to the minute
        assertEquals("In less than a minute", ClockText.countdownCaption(0L))
        assertEquals("In less than a minute", ClockText.countdownCaption(-5_000L))
    }

    @Test fun timerAndStopwatchDisplays() {
        assertEquals("00:00:05", ClockText.hms(5_000))
        assertEquals("01:02:03", ClockText.hms(3_723_000))
        assertEquals("99:59:59", ClockText.hms(ClockTimer.MAX_LENGTH_MS))
        assertEquals("00:00:00.00", ClockText.stopwatch(0))
        assertEquals("00:00:12.34", ClockText.stopwatch(12_345))
        assertEquals("00:01:00.99", ClockText.stopwatch(60_999))
        assertEquals("00:00:" to "12", ClockText.splitSeconds("00:00:12"))
        assertEquals(Triple(1, 2, 3), ClockText.hmsParts(3_723_000))
        assertEquals(3_723_000L, ClockText.lengthMs(1, 2, 3))
    }

    @Test fun lapLinesCarryLapAndSplit() {
        val splits = listOf(12_340L, 35_100L, 40_000L)
        assertEquals(listOf(12_340L, 22_760L, 4_900L), ClockText.lapDurations(splits))
        assertEquals("2  00:00:22.76  00:00:35.10", ClockText.lapLine(2, 22_760, 35_100))
    }

    @Test fun theTileFaceIsTimeNameAndDays() {
        val today = LocalDate.of(2026, 9, 23)
        val weekdaysPlusSat = DayOfWeek.values().toSet() - DayOfWeek.SUNDAY
        val (headline, lines) = ClockText.tileLines(alarm(7, 0, weekdaysPlusSat, name = "Alarm work"), today, false, us)
        assertEquals("7:00 AM", headline)
        assertEquals(listOf("Alarm work", "Mon, Tue, Wed, Thu, Fri, Sat"), lines)
        val (h24, lines2) = ClockText.tileLines(alarm(6, 30, date = today.plusDays(1), name = "Gym"), today, true, us)
        assertEquals("6:30", h24)
        assertEquals(listOf("Gym", "Tomorrow"), lines2)
    }

    @Test fun editorTitles() {
        assertEquals("NEW ALARM", ClockText.alarmTitle(false))
        assertEquals("EDIT ALARM", ClockText.alarmTitle(true))
        assertEquals("NEW TIMER", ClockText.timerTitle(false))
        assertEquals("EDIT TIMER", ClockText.timerTitle(true))
    }
}
