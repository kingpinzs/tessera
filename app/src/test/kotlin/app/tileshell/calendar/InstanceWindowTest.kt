package app.tileshell.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * "The next 24 hours" for the Calendar tile and for Tess: an all-day event (a birthday is one) is a DATE stored as its
 * UTC day, and counts by that date in any zone (gate review A, finding 1).
 */
class InstanceWindowTest {
    private val denver = ZoneId.of("America/Denver")       // UTC-6 in October: its UTC midnight is 18:00 local
    private val sydney = ZoneId.of("Australia/Sydney")     // UTC+11 in October
    private val day = 86_400_000L

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    /** An all-day instance of [date]: its UTC day. */
    private fun allDay(date: LocalDate, days: Long = 1): Pair<Long, Long> =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to date.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun shows(event: Pair<Long, Long>, now: Long, zone: ZoneId, window: Long = day): Boolean {
        // What the provider would return for the range asked, then the rule.
        val (from, to) = InstanceWindow.queryRange(now, window, zone)
        val returned = event.first <= to && event.second >= from
        return returned && InstanceWindow.shows(event.first, event.second, true, now, window, zone)
    }

    @Test fun `today's all-day event is shown all of the local day behind UTC`() {
        val today = allDay(LocalDate.of(2026, 10, 1))
        for (hour in listOf(0, 9, 17, 18, 19, 23)) {
            assertTrue("at $hour:00 MDT on the day itself", shows(today, at(denver, 2026, 10, 1, hour), denver))
        }
        // The old read, by the clock alone, lost it here: [19:00, 19:00 + 24 h) does not touch the UTC day that ended at 18:00.
        val now = at(denver, 2026, 10, 1, 19)
        assertFalse("the clock-only range misses it", today.first <= now + day && today.second >= now)
    }

    @Test fun `tomorrow's all-day event is not shown as today's`() {
        val tomorrow = allDay(LocalDate.of(2026, 10, 2))
        for (hour in listOf(0, 9, 18, 19, 23)) {
            assertFalse("at $hour:00 MDT the day before", shows(tomorrow, at(denver, 2026, 10, 1, hour), denver))
        }
        assertTrue("and it is shown once its day comes", shows(tomorrow, at(denver, 2026, 10, 2, 0, 1), denver))
    }

    @Test fun `yesterday's all-day event is gone at local midnight`() {
        val yesterday = allDay(LocalDate.of(2026, 9, 30))
        assertFalse(shows(yesterday, at(denver, 2026, 10, 1, 0, 1), denver))
        assertFalse(shows(yesterday, at(sydney, 2026, 10, 1, 8), sydney))   // ahead of UTC its UTC day still runs till 11:00 local
    }

    @Test fun `ahead of UTC today's all-day event is shown before its UTC day has begun`() {
        val today = allDay(LocalDate.of(2026, 10, 1))
        for (hour in listOf(0, 8, 11, 23)) assertTrue("at $hour:00 in Sydney", shows(today, at(sydney, 2026, 10, 1, hour), sydney))
        assertFalse(shows(allDay(LocalDate.of(2026, 10, 2)), at(sydney, 2026, 10, 1, 23), sydney))
    }

    @Test fun `an all-day event of several days is shown on each of its dates`() {
        val trip = allDay(LocalDate.of(2026, 10, 1), days = 3)
        assertFalse(shows(trip, at(denver, 2026, 9, 30, 20), denver))
        assertTrue(shows(trip, at(denver, 2026, 10, 1, 20), denver))
        assertTrue(shows(trip, at(denver, 2026, 10, 3, 20), denver))
        assertFalse(shows(trip, at(denver, 2026, 10, 4, 0, 1), denver))
    }

    @Test fun `a longer window covers the dates it starts on`() {
        // Tess's 30 days, for "delete the event …": today's date and the 29 after it.
        val now = at(denver, 2026, 10, 1, 19)
        assertTrue(shows(allDay(LocalDate.of(2026, 10, 1)), now, denver, 30 * day))
        assertTrue(shows(allDay(LocalDate.of(2026, 10, 30)), now, denver, 30 * day))
        assertFalse(shows(allDay(LocalDate.of(2026, 10, 31)), now, denver, 30 * day))
    }

    @Test fun `a timed event counts by the clock, as before`() {
        val now = at(denver, 2026, 10, 1, 19)
        fun timed(begin: Long, end: Long) = InstanceWindow.shows(begin, end, false, now, day, denver)
        assertTrue("running now", timed(now - 1_800_000, now + 1_800_000))
        assertTrue("tomorrow at 14:00, inside 24 h", timed(at(denver, 2026, 10, 2, 14), at(denver, 2026, 10, 2, 15)))
        assertFalse("tomorrow at 20:00, outside", timed(at(denver, 2026, 10, 2, 20), at(denver, 2026, 10, 2, 21)))
        // The wider range read (back to local midnight) must not bring this morning's finished meeting back.
        assertFalse("this morning's, over", timed(at(denver, 2026, 10, 1, 9), at(denver, 2026, 10, 1, 10)))
    }

    @Test fun `the range read reaches back to the local start of today and no further`() {
        val now = at(denver, 2026, 10, 1, 19)
        assertEquals(at(denver, 2026, 10, 1, 0) to now + day, InstanceWindow.queryRange(now, day, denver))
    }
}
