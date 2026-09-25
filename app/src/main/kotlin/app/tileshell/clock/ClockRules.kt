package app.tileshell.clock

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The clock's rules, free of Android so the host JVM proves every one (ClockRulesTest). Phase 15 Decisions
 * "Clock rules", "Ringing" and T15-55; the approximations are H6, H9 and H10.
 */
object ClockRules {
    /** An alarm nobody answers stops ringing after this long and counts as missed (approximation, H6). */
    const val RING_TIMEOUT_MS = 10 * 60 * 1000L

    /**
     * The instant a wall-clock [time] on [date] happens in [zone]. A time that does not exist (a spring-forward
     * gap) happens at the first instant after the gap; a time that happens twice (a fall-back overlap) happens
     * at its FIRST occurrence (Decisions "Clock rules", H9).
     */
    fun resolve(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val local = LocalDateTime.of(date, time)
        val rules = zone.rules
        return if (rules.getValidOffsets(local).isEmpty()) {
            rules.getTransition(local).instant
        } else {
            ZonedDateTime.ofLocal(local, zone, null).withEarlierOffsetAtOverlap().toInstant()
        }
    }

    /**
     * The date a one-shot alarm set now for [hour]:[minute] is armed for: today when that time is still ahead,
     * else tomorrow ("a one-shot alarm set for a time already past today arms for tomorrow").
     */
    fun oneShotDate(hour: Int, minute: Int, nowMs: Long, zone: ZoneId): LocalDate {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return if (resolve(today, LocalTime.of(hour, minute), zone).toEpochMilli() > nowMs) today else today.plusDays(1)
    }

    /**
     * The first time [alarm] rings strictly after [afterMs], or null when it is off or has nothing left to ring.
     * A pending snooze wins. A repeating alarm rings on its days at its wall-clock time, re-resolved per day, so
     * a DST change keeps 07:00 at 07:00; a one-shot alarm rings once, on its [Alarm.date].
     */
    fun nextTrigger(alarm: Alarm, afterMs: Long, zone: ZoneId): Long? {
        if (!alarm.enabled) return null
        alarm.snoozedUntilMs?.let { if (it > afterMs) return it }
        val time = LocalTime.of(alarm.hour, alarm.minute)
        if (!alarm.repeating) {
            val date = alarm.date ?: return null
            return resolve(date, time, zone).toEpochMilli().takeIf { it > afterMs }
        }
        var day = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        repeat(9) {
            if (day.dayOfWeek in alarm.days) {
                val at = resolve(day, time, zone).toEpochMilli()
                if (at > afterMs) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * The lower bound an alarm is armed from: the last occurrence already handled (rung or missed), but never
     * later than now — after the clock is moved BACK, a repeating alarm rings at its next time on the new clock
     * rather than waiting for the old clock to come round again (edge case "Clock moved BACK").
     */
    fun armFrom(lastHandledMs: Long?, nowMs: Long): Long = minOf(lastHandledMs ?: nowMs, nowMs)

    /** What happens to an occurrence found already past at a re-arm (a reboot, a force-stop, a clock jump). */
    enum class Overdue { RING_NOW, MISSED }

    /** Inside the ring timeout it still rings; later it is missed (Decisions "Ringing": "rings on boot if …"). */
    fun overdue(occurrenceMs: Long, nowMs: Long): Overdue =
        if (nowMs - occurrenceMs < RING_TIMEOUT_MS) Overdue.RING_NOW else Overdue.MISSED

    /** Alarms due in the same wall-clock minute ring together on one toast (T15-41). */
    fun sameMinute(aMs: Long, bMs: Long, zone: ZoneId): Boolean {
        val a = Instant.ofEpochMilli(aMs).atZone(zone).withSecond(0).withNano(0)
        val b = Instant.ofEpochMilli(bMs).atZone(zone).withSecond(0).withNano(0)
        return a == b
    }

    // --- Timers (T15-55) --------------------------------------------------------------------------------------

    fun startTimer(t: ClockTimer, nowElapsed: Long, nowWall: Long, boot: Int): ClockTimer {
        val left = if (t.state == ClockTimer.State.IDLE) t.lengthMs else t.remainingMs
        return t.copy(
            state = ClockTimer.State.RUNNING,
            remainingMs = left,
            deadlineElapsedMs = nowElapsed + left,
            deadlineWallMs = nowWall + left,
            bootCount = boot,
        )
    }

    fun pauseTimer(t: ClockTimer, nowElapsed: Long, nowWall: Long, boot: Int): ClockTimer =
        if (t.state != ClockTimer.State.RUNNING) t
        else t.copy(state = ClockTimer.State.PAUSED, remainingMs = timerRemaining(t, nowElapsed, nowWall, boot),
            deadlineElapsedMs = null, deadlineWallMs = null, bootCount = null)

    fun resetTimer(t: ClockTimer): ClockTimer =
        t.copy(state = ClockTimer.State.IDLE, remainingMs = t.lengthMs, deadlineElapsedMs = null, deadlineWallMs = null, bootCount = null)

    /** Time left: on the elapsed clock within the boot it started in, on the wall clock across a reboot. */
    fun timerRemaining(t: ClockTimer, nowElapsed: Long, nowWall: Long, boot: Int): Long = when (t.state) {
        ClockTimer.State.RUNNING -> maxOf(0L, timerDeadlineElapsed(t, nowElapsed, nowWall, boot)!! - nowElapsed)
        else -> t.remainingMs
    }

    /**
     * The elapsed-clock instant a running timer rings at in THIS boot: its own deadline when it started in this
     * boot, else rebuilt from the wall-clock deadline (a reboot restarted the elapsed clock). Null when not running.
     */
    fun timerDeadlineElapsed(t: ClockTimer, nowElapsed: Long, nowWall: Long, boot: Int): Long? {
        if (t.state != ClockTimer.State.RUNNING) return null
        return if (t.bootCount == boot && t.deadlineElapsedMs != null) t.deadlineElapsedMs
        else nowElapsed + ((t.deadlineWallMs ?: nowWall) - nowWall)
    }

    /** A running timer whose deadline passed while the phone was off ("timer ended while the phone was off"). */
    fun endedWhileOff(t: ClockTimer, nowWall: Long, boot: Int): Boolean =
        t.state == ClockTimer.State.RUNNING && t.bootCount != boot && (t.deadlineWallMs ?: Long.MAX_VALUE) <= nowWall

    // --- The stopwatch ----------------------------------------------------------------------------------------

    /** Total elapsed: within a boot on the elapsed clock, across a reboot from the wall-clock start; rolls over at 100 h. */
    fun stopwatchElapsed(s: Stopwatch, nowElapsed: Long, nowWall: Long, boot: Int): Long {
        val stretch = if (!s.running) 0L
        else if (s.bootCount == boot && s.startElapsedMs != null) nowElapsed - s.startElapsedMs
        else nowWall - (s.startWallMs ?: nowWall)
        return (s.accumulatedMs + maxOf(0L, stretch)) % Stopwatch.ROLLOVER_MS
    }

    fun startStopwatch(s: Stopwatch, nowElapsed: Long, nowWall: Long, boot: Int): Stopwatch =
        if (s.running) s else s.copy(running = true, startElapsedMs = nowElapsed, startWallMs = nowWall, bootCount = boot)

    fun stopStopwatch(s: Stopwatch, nowElapsed: Long, nowWall: Long, boot: Int): Stopwatch =
        if (!s.running) s
        else s.copy(running = false, accumulatedMs = stopwatchElapsed(s, nowElapsed, nowWall, boot),
            startElapsedMs = null, startWallMs = null, bootCount = null)

    fun lap(s: Stopwatch, nowElapsed: Long, nowWall: Long, boot: Int): Stopwatch =
        s.copy(laps = s.laps + stopwatchElapsed(s, nowElapsed, nowWall, boot))
}
