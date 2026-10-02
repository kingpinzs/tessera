package app.tileshell.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Which instances "the next <window>" holds: the Calendar tile's 24 hours, Tess's 24 hours and her 30 days.
 *
 * A timed instance counts when it overlaps the window, as the provider's own range read has it. An all-day instance is
 * a DATE, stored as that date's UTC day (CalendarContract's convention), so it counts by its date: when one of its
 * dates is a local day the window covers — today alone for a 24-hour window. Read by the clock alone, a zone behind UTC
 * drops today's all-day events at its UTC midnight (18:00 MDT) and shows tomorrow's as today's; a birthday is an
 * all-day event (gate review A, finding 1).
 */
object InstanceWindow {
    private const val DAY_MS = 86_400_000L

    /**
     * The provider range to read: back to the local start of today — where the UTC day of today's date may already have
     * begun and, behind UTC, already ended by the clock — and on to the window's end.
     */
    fun queryRange(fromMs: Long, windowMs: Long, zone: ZoneId): Pair<Long, Long> {
        val startOfToday = localDate(fromMs, zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return minOf(startOfToday, fromMs) to fromMs + windowMs
    }

    fun shows(beginMs: Long, endMs: Long, allDay: Boolean, fromMs: Long, windowMs: Long, zone: ZoneId): Boolean {
        if (!allDay) return endMs >= fromMs && beginMs <= fromMs + windowMs
        val firstDay = localDate(fromMs, zone)
        val lastDay = firstDay.plusDays(maxOf(1L, windowMs / DAY_MS) - 1)
        val eventFirst = utcDate(beginMs)
        // An all-day instance ends at the UTC midnight after its last date.
        val eventLast = maxOf(eventFirst, utcDate(endMs - 1))
        return !eventFirst.isAfter(lastDay) && !eventLast.isBefore(firstDay)
    }

    private fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun utcDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
}
