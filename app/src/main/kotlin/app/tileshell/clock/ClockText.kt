package app.tileshell.clock

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Every string Alarms & Clock shows, computed without Android so the host JVM proves them (ClockTextTest;
 * phase 15 build task 4). The wording is r11/clock.md's "Strings as shipped" where a source captured it and a
 * tagged approximation where none did (each one says so).
 */
object ClockText {
    /**
     * A wall-clock time as the app and the tile show it: "7:00 AM" in the 12-hour form (CK2 "7:10 AM", 2.3) and
     * "H:mm" in the 24-hour one (the phase's 24-hour edge case: "every time in the app and on the tile reads H:mm").
     * [is24h] is Android's own setting, read by the caller.
     */
    fun time(hour: Int, minute: Int, is24h: Boolean, locale: Locale): String =
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a", locale))

    fun timeAt(ms: Long, zone: ZoneId, is24h: Boolean, locale: Locale): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return time(t.hour, t.minute, is24h, locale)
    }

    /** The days of the week in the locale's order, starting at its first day (the 2015 English flyout starts Sunday, 4.2). */
    fun weekOrder(locale: Locale): List<DayOfWeek> {
        val first = WeekFields.of(locale).firstDayOfWeek
        return (0 until 7).map { first.plus(it.toLong()) }
    }

    fun dayShort(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.SHORT, locale)
    fun dayLong(day: DayOfWeek, locale: Locale): String = day.getDisplayName(TextStyle.FULL, locale)

    /**
     * The Repeats value and the row's repeat line: "Only once", "Every day", or the days in week order —
     * "Mon, Tue, Wed, Thu, Fri, Sat" (2.5; "Strings as shipped").
     */
    fun repeatSummary(days: Set<DayOfWeek>, locale: Locale): String = when {
        days.isEmpty() -> "Only once"
        days.size == 7 -> "Every day"
        else -> weekOrder(locale).filter { it in days }.joinToString(", ") { dayShort(it, locale) }
    }

    /**
     * The row's day / repeat line (`alarm_repeat:<id>`): a repeating alarm's days; a one-shot alarm reads "Today" or
     * "Tomorrow" for the day it is armed for (T15-57) and "Only once" when it is off or has no day left.
     */
    fun rowDayLine(alarm: Alarm, today: LocalDate, locale: Locale): String {
        if (alarm.repeating) return repeatSummary(alarm.days, locale)
        val date = alarm.date?.takeIf { alarm.enabled } ?: return "Only once"
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> "Only once"
        }
    }

    /**
     * The editor's caption under the spinner — "In 5 hours, 57 minutes" (3.8, CK2) — for the time until the alarm
     * being edited next rings. Rounded up to the minute, so 0:59:30 reads "In 1 hour"; a ring inside a minute reads
     * "In less than a minute" (approximation: the source shows none).
     */
    fun countdownCaption(untilMs: Long): String {
        val minutes = ((untilMs.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
        val h = minutes / 60
        val m = minutes % 60
        val hours = if (h == 1) "1 hour" else "$h hours"
        val mins = if (m == 1) "1 minute" else "$m minutes"
        return when {
            h == 0 && m == 0 -> "In less than a minute"
            h == 0 -> "In $mins"
            m == 0 -> "In $hours"
            else -> "In $hours, $mins"
        }
    }

    /** "hh:mm:ss" — the timer's digits and its original duration (6.2, 6.6 "00:00:05"). */
    fun hms(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000
        return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }

    /** "hh:mm:ss.cc" — the stopwatch's digits with hundredths (7.1, CK3 "00:00:00.00"). */
    fun stopwatch(ms: Long): String = "%s.%02d".format(hms(ms), (ms.coerceAtLeast(0L) / 10) % 100)

    /** A timer's remaining time split into the grey "hh:mm:" head and the white seconds tail (6.2). */
    fun splitSeconds(text: String): Pair<String, String> = text.substring(0, text.length - 2) to text.takeLast(2)

    /**
     * One lap row's text, and one line of the Share text (T15-16; the format is an approximation, H23): the lap's
     * index, the lap time (this lap's own duration) and its split (the total when it was taken), separated by two
     * spaces. The row draws the same string with its spans; `stopwatch_lap:<n>` and the shared line are equal.
     */
    fun lapLine(n: Int, lapMs: Long, splitMs: Long): String = "$n  ${stopwatch(lapMs)}  ${stopwatch(splitMs)}"

    /** The lap durations of a list of split times (the store keeps splits: total elapsed at each lap). */
    fun lapDurations(splits: List<Long>): List<Long> = splits.mapIndexed { i, s -> s - (if (i == 0) 0L else splits[i - 1]) }

    /** The next-alarm tile face (9.3): "7:00 AM" / the name / the repeat days. */
    fun tileLines(alarm: Alarm, today: LocalDate, is24h: Boolean, locale: Locale): Pair<String, List<String>> =
        time(alarm.hour, alarm.minute, is24h, locale) to listOf(alarm.name, rowDayLine(alarm, today, locale))

    /** The alarm editor's title (3.3, CK2) and the timer editor's (4.8; "EDIT TIMER" is U2's tagged approximation). */
    fun alarmTitle(editing: Boolean) = if (editing) "EDIT ALARM" else "NEW ALARM"
    fun timerTitle(editing: Boolean) = if (editing) "EDIT TIMER" else "NEW TIMER"

    /** Hours, minutes and seconds of a timer length, for the editor's three columns. */
    fun hmsParts(ms: Long): Triple<Int, Int, Int> {
        val s = (ms / 1000).toInt()
        return Triple(s / 3600, (s / 60) % 60, s % 60)
    }

    fun lengthMs(hours: Int, minutes: Int, seconds: Int): Long = ((hours * 60L + minutes) * 60L + seconds) * 1000L
}
