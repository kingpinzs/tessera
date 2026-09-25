package app.tileshell.clock

import app.tileshell.clock.AlarmApiRules.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class AlarmApiRulesTest {
    private fun alarm(hour: Int? = 6, minutes: Int? = 45, days: List<Int>? = null, message: String? = "Gym", skipUi: Boolean = true, ringtone: String? = null) =
        AlarmApiRules.parse(AlarmApiRules.ACTION_SET_ALARM, hour, minutes, days, message, skipUi, null, ringtone, null)

    private fun timer(length: Int? = 120, message: String? = null, skipUi: Boolean = true) =
        AlarmApiRules.parse(AlarmApiRules.ACTION_SET_TIMER, null, null, null, message, skipUi, length, null, null)

    @Test fun setAlarmWithSkipUiCreates() {
        val r = alarm() as Request.CreateAlarm
        assertEquals(6, r.fields.hour)
        assertEquals(45, r.fields.minute)
        assertEquals("Gym", r.fields.message)
        assertEquals(AlarmSound.DEFAULT, r.fields.sound)
        assertTrue(r.fields.days.isEmpty())
    }

    @Test fun setAlarmWithoutSkipUiOpensTheEditorFilledIn() {
        val r = alarm(skipUi = false) as Request.EditAlarm
        assertEquals(6, r.fields.hour)
        assertEquals("Gym", r.fields.message)
        // No hour: the editor opens for the user to pick one, SKIP_UI or not.
        assertTrue(alarm(hour = null) is Request.EditAlarm)
    }

    @Test fun setAlarmValidatesEveryField() {
        assertEquals(Request.Refused("hour out of range"), alarm(hour = 25))
        assertEquals(Request.Refused("hour out of range"), alarm(hour = -1))
        assertEquals(Request.Refused("minutes out of range"), alarm(minutes = 60))
        assertEquals(Request.Refused("days out of range"), alarm(days = listOf(1, 8)))
        assertEquals(Request.Refused("days out of range"), alarm(days = listOf(0)))
        assertEquals(Request.Refused("message longer than 64 characters"), alarm(message = "x".repeat(65)))
        assertEquals(Request.Refused("message holds a control character"), alarm(message = "a\u0007b"))
        assertTrue(alarm(message = "x".repeat(64)) is Request.CreateAlarm)
        // Minutes default to 0; a null message is the empty name (the store then names it "Alarm").
        val r = alarm(minutes = null, message = null) as Request.CreateAlarm
        assertEquals(0, r.fields.minute)
        assertEquals("", r.fields.message)
    }

    @Test fun daysAreCalendarConstants() {
        // Calendar.SUNDAY = 1 … SATURDAY = 7.
        val r = alarm(days = listOf(1, 2, 7)) as Request.CreateAlarm
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY), r.fields.days)
    }

    @Test fun ringtoneMapsToTheSound() {
        assertEquals(AlarmSound.Kind.VIBRATE, (alarm(ringtone = "silent") as Request.CreateAlarm).fields.sound.kind)
        val tone = (alarm(ringtone = "content://media/internal/audio/media/7") as Request.CreateAlarm).fields.sound
        assertEquals(AlarmSound.Kind.TONE, tone.kind)
        assertEquals("content://media/internal/audio/media/7", tone.uri)
        assertEquals(AlarmSound.DEFAULT, (alarm(ringtone = "") as Request.CreateAlarm).fields.sound)
    }

    @Test fun setTimerCreatesOpensOrRefuses() {
        assertEquals(Request.CreateTimer(120_000L, ""), timer())
        assertEquals(Request.EditTimer(120_000L, "Tea"), timer(message = "Tea", skipUi = false))
        assertEquals(Request.EditTimer(null, ""), timer(length = null))
        assertEquals(Request.Refused("length out of range"), timer(length = 0))
        assertEquals(Request.Refused("length out of range"), timer(length = -5))
        assertEquals(Request.Refused("length past 99:59:59"), timer(length = (ClockTimer.MAX_LENGTH_MS / 1000).toInt() + 1))
        assertEquals(Request.CreateTimer(ClockTimer.MAX_LENGTH_MS, ""), timer(length = (ClockTimer.MAX_LENGTH_MS / 1000).toInt()))
    }

    @Test fun showActionsAndUnknownOnes() {
        assertEquals(Request.ShowAlarms, AlarmApiRules.parse(AlarmApiRules.ACTION_SHOW_ALARMS, null, null, null, null, false, null, null, null))
        assertEquals(Request.ShowTimers, AlarmApiRules.parse(AlarmApiRules.ACTION_SHOW_TIMERS, null, null, null, null, false, null, null, null))
        assertEquals(Request.Refused("unsupported action android.intent.action.DISMISS_ALARM"), AlarmApiRules.parse("android.intent.action.DISMISS_ALARM", null, null, null, null, false, null, null, null))
        assertEquals(Request.Refused("unsupported action (none)"), AlarmApiRules.parse(null, null, null, null, null, false, null, null, null))
    }
}
