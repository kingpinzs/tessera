package app.tileshell.clock

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Alarms & Clock's data (phase 15 build task 2). Plain values: the store persists them, [ClockRules] computes
 * with them, the scheduler arms them, the ring service rings them and the app draws them.
 */

/** What an alarm plays (r11/clock.md 4.3: Vibrate only / Pick from my music / Pick from ringtones; the default sound otherwise). */
data class AlarmSound(val kind: Kind, val uri: String? = null, val title: String? = null) {
    enum class Kind { DEFAULT, VIBRATE, TONE, MUSIC }

    companion object {
        val DEFAULT = AlarmSound(Kind.DEFAULT)
    }
}

/**
 * One alarm. An alarm is WALL-CLOCK (Decisions "Clock rules"): a time-zone change keeps [hour]:[minute].
 * [days] empty means "only once": such an alarm is armed for [date], fixed when it was switched on, so a
 * clock moved back past it does not ring it twice (edge case). [snoozedUntilMs] is the pending snooze instant.
 * It is armed from [ClockRules.armFrom] of [lastHandledMs].
 */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val name: String,
    val days: Set<DayOfWeek>,
    val enabled: Boolean,
    val sound: AlarmSound,
    val snoozeMinutes: Int,
    val date: LocalDate?,
    val snoozedUntilMs: Long?,
    /** The last occurrence already handled — rung, snoozed, dismissed or missed — so a re-arm never rings it again. */
    val lastHandledMs: Long?,
) {
    val repeating: Boolean get() = days.isNotEmpty()

    companion object {
        /** r11/clock.md 4.4–4.5: the choices and the default, every source. */
        val SNOOZE_CHOICES = listOf(5, 10, 20, 30, 60)
        const val DEFAULT_SNOOZE = 10
        const val DEFAULT_NAME = "Alarm"
    }
}

/**
 * One timer. A timer is ELAPSED-TIME (T15-55): while running it is armed on the elapsed clock, so neither a
 * zone change nor a manual clock change moves it; [deadlineWallMs] and [bootCount] let it be re-armed after
 * a reboot, when the elapsed clock has started again from zero.
 */
data class ClockTimer(
    val id: String,
    val name: String,
    val lengthMs: Long,
    val state: State,
    /** Time left when paused or idle; the full length when idle. */
    val remainingMs: Long,
    val deadlineElapsedMs: Long?,
    val deadlineWallMs: Long?,
    val bootCount: Int?,
) {
    enum class State { IDLE, RUNNING, PAUSED }

    companion object {
        /** 99 h 59 min 59 s (approximation, H10). */
        const val MAX_LENGTH_MS = ((99L * 60 + 59) * 60 + 59) * 1000
    }
}

/**
 * The one stopwatch. Running time accumulates in [accumulatedMs]; while running, the current stretch started at
 * [startElapsedMs] on boot [bootCount] (and at [startWallMs] on the wall clock, which is what a reboot leaves).
 * [laps] are the lap marks as total elapsed times.
 */
data class Stopwatch(
    val running: Boolean,
    val accumulatedMs: Long,
    val startElapsedMs: Long?,
    val startWallMs: Long?,
    val bootCount: Int?,
    val laps: List<Long>,
) {
    companion object {
        val RESET = Stopwatch(false, 0, null, null, null, emptyList())

        /** 99:59:59.99 then 0 again (approximation, H10). */
        const val ROLLOVER_MS = 100L * 60 * 60 * 1000
    }
}
