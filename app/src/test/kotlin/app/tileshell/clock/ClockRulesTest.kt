package app.tileshell.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockRulesTest {
    private val denver = ZoneId.of("America/Denver")
    private val utc = ZoneId.of("UTC")

    private fun ms(z: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, z).toInstant().toEpochMilli()

    private fun alarm(h: Int, m: Int, days: Set<DayOfWeek> = emptySet(), date: LocalDate? = null, snoozed: Long? = null, on: Boolean = true) =
        Alarm("a1", h, m, "Alarm", days, on, AlarmSound.DEFAULT, 10, date, snoozed, null)

    @Test fun springForwardGapRingsAtTheFirstInstantAfterTheGap() {
        // 2027-03-14 02:30 does not exist in Denver: the clocks go from 02:00 MST to 03:00 MDT.
        val at = ClockRules.resolve(LocalDate.of(2027, 3, 14), LocalTime.of(2, 30), denver)
        assertEquals(ms(denver, 2027, 3, 14, 3, 0), at.toEpochMilli())
        assertEquals("2027-03-14T09:00:00Z", at.toString())
    }

    @Test fun fallBackOverlapRingsAtTheFirstOccurrence() {
        // 2027-11-07 01:30 happens twice in Denver: first at MDT (UTC-6), then at MST (UTC-7).
        val at = ClockRules.resolve(LocalDate.of(2027, 11, 7), LocalTime.of(1, 30), denver)
        assertEquals("2027-11-07T07:30:00Z", at.toString())
    }

    @Test fun aDailyAlarmKeepsItsWallClockTimeAcrossDst() {
        val daily = alarm(7, 0, DayOfWeek.values().toSet())
        val beforeSpring = ms(denver, 2027, 3, 13, 8, 0)
        val first = ClockRules.nextTrigger(daily, beforeSpring, denver)!!
        assertEquals(ms(denver, 2027, 3, 14, 7, 0), first)
        val gap = first - ms(denver, 2027, 3, 13, 7, 0)
        assertEquals(23 * 3600_000L, gap) // a 23-hour night
        val beforeFall = ms(denver, 2027, 11, 6, 7, 0)
        val next = ClockRules.nextTrigger(daily, beforeFall, denver)!!
        assertEquals(25 * 3600_000L, next - beforeFall) // a 25-hour night
    }

    @Test fun aOneShotAlarmPastTodayArmsForTomorrow() {
        val now = ms(utc, 2026, 9, 23, 10, 0)
        assertEquals(LocalDate.of(2026, 9, 24), ClockRules.oneShotDate(9, 59, now, utc))
        assertEquals(LocalDate.of(2026, 9, 24), ClockRules.oneShotDate(10, 0, now, utc)) // exactly now is past
        assertEquals(LocalDate.of(2026, 9, 23), ClockRules.oneShotDate(10, 1, now, utc))
    }

    @Test fun aOneShotAlarmRingsOnceOnItsDate() {
        val a = alarm(7, 30, date = LocalDate.of(2026, 9, 24))
        val at = ms(utc, 2026, 9, 24, 7, 30)
        assertEquals(at, ClockRules.nextTrigger(a, ms(utc, 2026, 9, 23, 12, 0), utc))
        assertNull(ClockRules.nextTrigger(a, at, utc)) // handled: nothing left to ring
        // Moved back two days: still armed for its stored date, and only once.
        assertEquals(at, ClockRules.nextTrigger(a, ms(utc, 2026, 9, 22, 12, 0), utc))
    }

    @Test fun aRepeatingAlarmRingsOnItsDaysOnly() {
        val weekdays = alarm(6, 45, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        // 2026-09-23 is a Wednesday.
        assertEquals(ms(utc, 2026, 9, 25, 6, 45), ClockRules.nextTrigger(weekdays, ms(utc, 2026, 9, 23, 7, 0), utc))
        assertEquals(ms(utc, 2026, 9, 28, 6, 45), ClockRules.nextTrigger(weekdays, ms(utc, 2026, 9, 25, 6, 45), utc))
    }

    @Test fun aSnoozeWinsAndAnOffAlarmNeverRings() {
        val snooze = ms(utc, 2026, 9, 23, 7, 10)
        val a = alarm(7, 0, DayOfWeek.values().toSet(), snoozed = snooze)
        assertEquals(snooze, ClockRules.nextTrigger(a, ms(utc, 2026, 9, 23, 7, 0), utc))
        assertNull(ClockRules.nextTrigger(a.copy(enabled = false), 0, utc))
    }

    @Test fun armingNeverStartsFromTheFutureAfterTheClockMovesBack() {
        assertEquals(1_000L, ClockRules.armFrom(5_000L, 1_000L))
        assertEquals(500L, ClockRules.armFrom(500L, 1_000L))
        assertEquals(1_000L, ClockRules.armFrom(null, 1_000L))
    }

    @Test fun anOccurrencePastTheTimeoutIsMissed() {
        assertEquals(ClockRules.Overdue.RING_NOW, ClockRules.overdue(0, ClockRules.RING_TIMEOUT_MS - 1))
        assertEquals(ClockRules.Overdue.MISSED, ClockRules.overdue(0, ClockRules.RING_TIMEOUT_MS))
    }

    @Test fun sameMinuteIsTheWallClockMinute() {
        assertTrue(ClockRules.sameMinute(ms(utc, 2026, 9, 23, 7, 0, 0), ms(utc, 2026, 9, 23, 7, 0, 59), utc))
        assertFalse(ClockRules.sameMinute(ms(utc, 2026, 9, 23, 7, 0, 59), ms(utc, 2026, 9, 23, 7, 1, 0), utc))
    }

    @Test fun aTimerRunsOnTheElapsedClockAndSurvivesAReboot() {
        val t = ClockTimer("t1", "Tea", 90_000, ClockTimer.State.IDLE, 90_000, null, null, null)
        val running = ClockRules.startTimer(t, nowElapsed = 1_000, nowWall = 1_000_000, boot = 7)
        assertEquals(91_000L, running.deadlineElapsedMs)
        assertEquals(1_090_000L, running.deadlineWallMs)
        // Same boot: a wall-clock jump does not move it.
        assertEquals(60_000L, ClockRules.timerRemaining(running, nowElapsed = 31_000, nowWall = 9_999_999, boot = 7))
        // After a reboot the elapsed clock restarted: the wall deadline rebuilds it.
        assertEquals(5_000L + 40_000, ClockRules.timerDeadlineElapsed(running, nowElapsed = 5_000, nowWall = 1_050_000, boot = 8))
        assertFalse(ClockRules.endedWhileOff(running, nowWall = 1_050_000, boot = 8))
        assertTrue(ClockRules.endedWhileOff(running, nowWall = 1_095_000, boot = 8))
        assertFalse(ClockRules.endedWhileOff(running, nowWall = 1_095_000, boot = 7)) // same boot: it just rang late
    }

    @Test fun aPausedTimerHoldsItsRemainingNotADeadline() {
        val t = ClockTimer("t1", "", 60_000, ClockTimer.State.IDLE, 60_000, null, null, null)
        val r = ClockRules.startTimer(t, 0, 0, 1)
        val p = ClockRules.pauseTimer(r, nowElapsed = 20_000, nowWall = 20_000, boot = 1)
        assertEquals(ClockTimer.State.PAUSED, p.state)
        assertEquals(40_000L, p.remainingMs)
        assertNull(p.deadlineElapsedMs)
        assertEquals(40_000L, ClockRules.timerRemaining(p, nowElapsed = 999_999, nowWall = 5, boot = 3))
        val resumed = ClockRules.startTimer(p, nowElapsed = 100_000, nowWall = 100_000, boot = 1)
        assertEquals(140_000L, resumed.deadlineElapsedMs)
        assertEquals(60_000L, ClockRules.resetTimer(resumed).remainingMs)
    }

    @Test fun theStopwatchCountsAcrossProcessDeathRebootAndRollsOver() {
        var s = ClockRules.startStopwatch(Stopwatch.RESET, nowElapsed = 1_000, nowWall = 50_000, boot = 1)
        assertEquals(9_000L, ClockRules.stopwatchElapsed(s, nowElapsed = 10_000, nowWall = 59_000, boot = 1))
        // A reboot: the elapsed clock restarted, the wall clock did not.
        assertEquals(20_000L, ClockRules.stopwatchElapsed(s, nowElapsed = 3_000, nowWall = 70_000, boot = 2))
        s = ClockRules.lap(s, nowElapsed = 4_000, nowWall = 0, boot = 1)
        assertEquals(listOf(3_000L), s.laps)
        s = ClockRules.stopStopwatch(s, nowElapsed = 6_000, nowWall = 0, boot = 1)
        assertEquals(5_000L, s.accumulatedMs)
        assertEquals(5_000L, ClockRules.stopwatchElapsed(s, nowElapsed = 99_999, nowWall = 0, boot = 9))
        val nearly = s.copy(accumulatedMs = Stopwatch.ROLLOVER_MS - 10)
        val over = ClockRules.startStopwatch(nearly, 0, 0, 1)
        assertEquals(15L, ClockRules.stopwatchElapsed(over, 25, 0, 1))
    }
}
