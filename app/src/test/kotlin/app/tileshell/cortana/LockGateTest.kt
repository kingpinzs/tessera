package app.tileshell.cortana

import app.tileshell.cortana.action.LockGate
import app.tileshell.cortana.match.Request
import app.tileshell.cortana.reminders.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PQ3, Jeremy: "(a)". Every command in the ruled list, on the right side of the gate.
 *
 * This is the whole table, not a sample: the doc names each command explicitly, and a command that
 * silently moved from one side to the other is exactly the kind of drift a gate cannot afford.
 */
class LockGateTest {

    @Test
    fun `run directly while locked`() {
        listOf(
            Request.TimeQuery,
            Request.DateQuery,
            Request.Weather,
            Request.SetAlarm(7, 0),
            Request.SetTimer(300),
            Request.PlayMusic(null),
            Request.PlayMusic("bohemian rhapsody"),
        ).forEach {
            assertTrue("$it should run while locked", LockGate.allowedWhileLocked(it))
        }
    }

    @Test
    fun `show Unlock to continue while locked`() {
        listOf(
            Request.CallContact("mom"),
            Request.TextContact("mom", "hi"),
            Request.WhatsOnMyCalendar,
            Request.AddCalendarEvent("standup", 0L),
            Request.DeleteCalendarEvent("standup"),
            Request.SetReminder("take out the bins", null, Recurrence.ONCE),
            Request.DeleteReminder("take out the bins"),
            Request.OpenApp("clock"),
            Request.Directions("the airport"),
            Request.TakePhoto,
            Request.TakeNote(null),
            Request.SavePlaceHere("Home"),
        ).forEach {
            assertFalse("$it should be gated while locked", LockGate.allowedWhileLocked(it))
        }
    }

    @Test
    fun `an answer to a card that is already showing is not itself gated`() {
        listOf(Request.Confirm, Request.Cancel, Request.AddMore, Request.TryAgain, Request.Whenever)
            .forEach { assertTrue("$it is an answer, not a request to gate", LockGate.allowedWhileLocked(it)) }
    }

    @Test
    fun `the card restates the request it is holding`() {
        assertEquals("Open Clock", LockGate.restate(Request.OpenApp("Clock")))
        assertEquals("Call Mom", LockGate.restate(Request.CallContact("Mom")))
        assertEquals("Text Mom", LockGate.restate(Request.TextContact("Mom", "hi")))
        assertEquals("Remind you to take out the bins", LockGate.restate(Request.SetReminder("take out the bins", null)))
        assertEquals("Take a photo", LockGate.restate(Request.TakePhoto))
    }
}
