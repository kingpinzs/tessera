package app.tileshell.cortana

import app.tileshell.cortana.match.TimeWords
import app.tileshell.cortana.reminders.Recurrence
import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderGroup
import app.tileshell.cortana.reminders.ReminderKind
import app.tileshell.cortana.reminders.ReminderText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The spoken time forms, and the grouping the Reminders page reads. Both are pure over a clock, which is
 * what lets them be pinned without a device — and both would otherwise only be exercised by a QA row
 * that happens to run at the right hour.
 */
class TimeWordsTest {

    /** Wednesday 2026-09-16 at 09:00 local. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 9, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun words(text: String) = text.split(' ')

    private fun parsed(text: String): Calendar {
        val result = TimeWords.parseTime(words(text), now)
            ?: error("no time parsed from \"$text\"")
        return Calendar.getInstance().apply { timeInMillis = result.value }
    }

    @Test
    fun `a bare hour that has passed goes to the evening, then to tomorrow`() {
        // Said at 09:00, "at 8" means 20:00 today — not 08:00, which is behind us.
        val evening = parsed("at 8")
        assertEquals(20, evening.get(Calendar.HOUR_OF_DAY))
        assertEquals(16, evening.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `an explicit meridiem is taken literally, and rolls to tomorrow when it is past`() {
        val morning = parsed("at 8 am")
        assertEquals(8, morning.get(Calendar.HOUR_OF_DAY))
        assertEquals("08:00 is behind 09:00, so it is tomorrow", 17, morning.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `minutes, spoken and written`() {
        assertEquals(30, parsed("at 8:30 pm").get(Calendar.MINUTE))
        assertEquals(30, parsed("at eight thirty pm").get(Calendar.MINUTE))
        assertEquals(5, parsed("at eight oh five pm").get(Calendar.MINUTE))
        assertEquals(0, parsed("at eight o'clock pm").get(Calendar.MINUTE))
    }

    @Test
    fun `tomorrow and a weekday`() {
        val tomorrow = parsed("tomorrow at 7 am")
        assertEquals(17, tomorrow.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, tomorrow.get(Calendar.HOUR_OF_DAY))
        val friday = parsed("on friday at 9 am")
        assertEquals(Calendar.FRIDAY, friday.get(Calendar.DAY_OF_WEEK))
        assertTrue("a weekday is always ahead", friday.timeInMillis > now)
    }

    @Test
    fun `noon and midnight`() {
        assertEquals(12, parsed("at noon").get(Calendar.HOUR_OF_DAY))
        assertEquals(0, parsed("at midnight").get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `in a duration is a time too`() {
        val result = TimeWords.parseTime(words("in 20 minutes"), now)!!
        assertEquals(now + 20 * 60_000L, result.value)
    }

    @Test
    fun `no time at all`() {
        assertNull(TimeWords.parseTime(words("call the dentist"), now))
    }

    @Test
    fun `durations, spoken and written`() {
        assertEquals(300, TimeWords.parseDuration(words("for 5 minutes"))!!.value)
        assertEquals(10, TimeWords.parseDuration(words("for ten seconds"))!!.value)
        assertEquals(3600, TimeWords.parseDuration(words("for an hour"))!!.value)
        assertEquals(90, TimeWords.parseDuration(words("for one minute and thirty seconds"))!!.value)
        assertNull(TimeWords.parseDuration(words("take out the bins")))
    }

    @Test
    fun `recurrence words`() {
        assertEquals(Recurrence.DAY, TimeWords.parseRecurrence(words("every day"))!!.value)
        assertEquals(Recurrence.WEEK, TimeWords.parseRecurrence(words("every week"))!!.value)
        assertEquals(Recurrence.MONTH, TimeWords.parseRecurrence(words("every month"))!!.value)
        assertEquals(Recurrence.YEAR, TimeWords.parseRecurrence(words("every year"))!!.value)
        assertNull(TimeWords.parseRecurrence(words("take out the bins")))
    }

    // ---------------- the Reminders page's groups (R7 §3.5.1) ----------------

    @Test
    fun `groups follow the kind and the time, not the kind alone`() {
        val endOfToday = ReminderText.endOfToday(now)
        val today = Reminder("1", "bins", timeMs = now + 3_600_000L)
        val tomorrow = Reminder("2", "dentist", timeMs = endOfToday + 3_600_000L)
        val whenever = Reminder("3", "read that book", timeMs = null)
        val place = Reminder("4", "trash", kind = ReminderKind.PLACE, placeId = "p")
        val person = Reminder("5", "dinner", kind = ReminderKind.PERSON, contactName = "Mom")

        assertEquals(ReminderGroup.TODAY, today.group(now, endOfToday))
        assertEquals(ReminderGroup.COMING_UP, tomorrow.group(now, endOfToday))
        assertEquals(ReminderGroup.WHENEVER, whenever.group(now, endOfToday))
        // H26: a place or person reminder lists under Whenever even though it does fire.
        assertEquals(ReminderGroup.WHENEVER, place.group(now, endOfToday))
        assertEquals(ReminderGroup.WHENEVER, person.group(now, endOfToday))
    }

    @Test
    fun `end of today is the last millisecond of today, not 24 hours from now`() {
        val end = Calendar.getInstance().apply { timeInMillis = ReminderText.endOfToday(now) }
        assertEquals(16, end.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, end.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, end.get(Calendar.MINUTE))
    }

    @Test
    fun `the day word is what the list and the saved card both build on`() {
        assertEquals("Today", ReminderText.dayWord(now + 3_600_000L))
        assertEquals("Tomorrow", ReminderText.dayWord(now + 86_400_000L))
        // Three days out is a weekday name, not a date.
        assertEquals("Saturday", ReminderText.dayWord(now + 3 * 86_400_000L))
        // Far out is a date.
        assertTrue("/" in ReminderText.dayWord(now + 40L * 86_400_000L))
    }
}
