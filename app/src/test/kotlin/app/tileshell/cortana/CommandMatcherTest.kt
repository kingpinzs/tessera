package app.tileshell.cortana

import app.tileshell.cortana.match.CommandMatcher
import app.tileshell.cortana.match.Request
import app.tileshell.cortana.reminders.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Every phrasing in the ruled command list (interview Q2, Jeremy: "A"), pinned.
 *
 * The recognizer emits UPPERCASE words with no punctuation, and the text box can carry anything, so both
 * forms are exercised: they run the same matcher, which is what fidelity A4 requires.
 */
class CommandMatcherTest {

    /** A fixed clock: Wednesday 2026-09-16 at 09:00 local. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 9, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun match(text: String, awaiting: CommandMatcher.Awaiting? = null) =
        CommandMatcher.match(text, CommandMatcher.Context(awaiting, now))

    // ---------------- the ruled list ----------------

    @Test
    fun `open an app`() {
        assertEquals(Request.OpenApp("clock"), match("open Clock"))
        assertEquals(Request.OpenApp("clock"), match("OPEN CLOCK"))
        assertEquals(Request.OpenApp("f droid"), match("launch F-Droid"))
    }

    @Test
    fun `call a contact`() {
        // The name is title-cased because it is read back — on the card, and in "I couldn't find
        // <name> in your contacts". The Contacts lookup itself is case-insensitive.
        assertEquals(Request.CallContact("Mom"), match("call Mom"))
        assertEquals(Request.CallContact("Mom"), match("CALL MOM"))
        // The number label is read back from the contact, not taken from the utterance.
        assertEquals(Request.CallContact("Mom"), match("call Mom on mobile"))
    }

    @Test
    fun `text a contact, with and without the message`() {
        assertEquals(Request.TextContact("Mom", null), match("text Mom"))
        assertEquals(Request.TextContact("Mom", "i'm on my way"), match("text Mom I'm on my way"))
        assertEquals(Request.TextContact("Mom", "i'm late"), match("text Mom saying I'm late"))
        assertEquals(Request.TextContact("Mom", null), match("send a text to Mom"))
    }

    @Test
    fun `alarms and timers`() {
        assertEquals(Request.SetAlarm(7, 0), match("set an alarm for 7 am"))
        assertEquals(Request.SetAlarm(7, 30), match("set an alarm for 7:30 am"))
        assertEquals(Request.SetAlarm(6, 45), match("wake me up at six forty five am"))
        assertEquals(Request.SetTimer(300), match("set a timer for 5 minutes"))
        assertEquals(Request.SetTimer(10), match("set a timer for ten seconds"))
        assertEquals(Request.SetTimer(3600), match("set a timer for an hour"))
    }

    @Test
    fun `a timed reminder keeps only the task in its text`() {
        val request = match("remind me to take out the bins at 8 pm") as Request.SetReminder
        assertEquals("take out the bins", request.text)
        assertEquals(Recurrence.ONCE, request.recurrence)
        val at = Calendar.getInstance().apply { timeInMillis = request.timeMs!! }
        assertEquals(20, at.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, at.get(Calendar.MINUTE))
    }

    @Test
    fun `a reminder with no time has no time, which is what the missing-time card is for`() {
        val request = match("remind me to call the dentist") as Request.SetReminder
        assertEquals("call the dentist", request.text)
        assertEquals(null, request.timeMs)
    }

    @Test
    fun `a recurring reminder`() {
        val request = match("remind me to take my pills every day at 9 am") as Request.SetReminder
        assertEquals("take my pills", request.text)
        assertEquals(Recurrence.DAY, request.recurrence)
    }

    @Test
    fun `a place reminder, not a timed one`() {
        val request = match("remind me to take out the trash when I get home") as Request.SetReminder
        assertEquals("take out the trash", request.text)
        assertEquals("Home", request.placeName)
        assertEquals(null, request.timeMs)
        assertEquals(null, request.personName)
    }

    @Test
    fun `a person reminder does not lose the word time to the clock`() {
        val request = match("remind me to ask about dinner next time I talk to Mom") as Request.SetReminder
        assertEquals("ask about dinner", request.text)
        assertEquals("Mom", request.personName)
        assertEquals(null, request.timeMs)
        assertEquals(null, request.placeName)
    }

    @Test
    fun `calendar add and delete, and the day's list`() {
        val add = match("add a meeting called standup to my calendar at 10 am") as Request.AddCalendarEvent
        assertTrue("the calendar words are stripped from the title: '${add.title}'", "calendar" !in add.title)
        assertEquals(Request.DeleteCalendarEvent("standup"), match("delete the event standup"))
        assertEquals(Request.WhatsOnMyCalendar, match("what's on my calendar"))
    }

    @Test
    fun `adding to a list is not a calendar event`() {
        assertTrue(match("add milk to my shopping list") is Request.NotUnderstood)
    }

    @Test
    fun `time, date and weather`() {
        assertEquals(Request.TimeQuery, match("what time is it"))
        assertEquals(Request.DateQuery, match("what day is it"))
        assertEquals(Request.Weather, match("what's the weather"))
        assertEquals(Request.Weather, match("is it going to rain"))
    }

    @Test
    fun `a stray word at the edge does not lose a fixed query`() {
        // The device really produced this for "What time is it?" — an exact-equality match turned it
        // into "Sorry, I can't do that yet."
        assertEquals(Request.TimeQuery, match("at what time is it"))
        assertEquals(Request.TimeQuery, match("so what time is it now"))
        assertEquals(Request.DateQuery, match("um what day is it"))
        assertEquals(Request.WhatsOnMyCalendar, match("so what's on my calendar today"))
    }

    @Test
    fun `the recogniser spells the meridiem out as two letters`() {
        // Verbatim from the device: "SET IN ALARM FOR SEVEN TWENTY A M".
        assertEquals(Request.SetAlarm(7, 20), match("set in alarm for seven twenty a m"))
        assertEquals(Request.SetAlarm(7, 20), match("set an alarm for seven twenty a m"))
        assertEquals(Request.SetAlarm(19, 20), match("set an alarm for seven twenty p m"))
        // And the written forms still work.
        assertEquals(Request.SetAlarm(7, 20), match("set an alarm for 7:20 am"))
    }

    @Test
    fun `a one-word form still has to be the whole utterance`() {
        // "set a timer for 5 minutes" contains "time"; it is a timer, not the clock.
        assertEquals(Request.SetTimer(300), match("set a timer for 5 minutes"))
        assertEquals(Request.TimeQuery, match("time"))
        // And a reminder that mentions the date is a reminder: order settles it before this is reached.
        assertTrue(match("remind me to check the date at 9 am") is Request.SetReminder)
    }

    @Test
    fun `music, directions, photo and note`() {
        assertEquals(Request.PlayMusic(null), match("play music"))
        assertEquals(Request.PlayMusic("bohemian rhapsody"), match("play Bohemian Rhapsody"))
        assertEquals(Request.Directions("the airport"), match("directions to the airport"))
        assertEquals(Request.TakePhoto, match("take a photo"))
        assertEquals(Request.TakeNote(null), match("take a note"))
        assertEquals(Request.TakeNote("buy milk"), match("take a note buy milk"))
    }

    @Test
    fun `saving a place at the spot`() {
        assertEquals(Request.SavePlaceHere("Home"), match("this is home"))
        assertEquals(Request.SavePlaceHere("Work"), match("save this as work"))
    }

    // ---------------- what is deliberately NOT here ----------------

    @Test
    fun `settings toggles are phase 04 and must not match here`() {
        // Rule 16: no placeholder for them, and no silent "open Settings" either.
        assertTrue(match("turn on wifi") is Request.NotUnderstood)
        assertTrue(match("turn off bluetooth") is Request.NotUnderstood)
        assertTrue(match("turn on airplane mode") is Request.NotUnderstood)
        assertTrue(match("turn off mobile data") is Request.NotUnderstood)
    }

    @Test
    fun `unmatched speech carries its own text to the not-understood handler`() {
        val request = match("what is the capital of Peru") as Request.NotUnderstood
        assertEquals("what is the capital of Peru", request.text)
    }

    @Test
    fun `silence is not the same as not understood`() {
        assertEquals(Request.Silence, match(""))
        assertEquals(Request.Silence, match("   "))
    }

    // ---------------- answers ----------------

    @Test
    fun `an answer only counts while something is awaiting it`() {
        assertEquals(Request.Confirm, match("send it", CommandMatcher.Awaiting.TEXT_READBACK))
        assertEquals(Request.AddMore, match("add more", CommandMatcher.Awaiting.TEXT_READBACK))
        assertEquals(Request.TryAgain, match("try again", CommandMatcher.Awaiting.TEXT_READBACK))
        assertEquals(Request.Cancel, match("cancel", CommandMatcher.Awaiting.TEXT_READBACK))
        // With nothing pending, a bare "yes" is speech that matched no command.
        assertTrue(match("yes") is Request.NotUnderstood)
    }

    @Test
    fun `add more is offered on the read-back and nowhere else`() {
        // R6 §3.4.2: a reminder or calendar card takes yes / no / cancel only.
        assertTrue(match("add more", CommandMatcher.Awaiting.CARD) !is Request.AddMore)
    }

    @Test
    fun `whenever answers the missing-time card`() {
        assertEquals(Request.Whenever, match("whenever", CommandMatcher.Awaiting.MISSING_TIME))
        assertEquals(Request.Whenever, match("no time", CommandMatcher.Awaiting.MISSING_TIME))
        assertEquals(Request.Whenever, match("any time", CommandMatcher.Awaiting.MISSING_TIME))
    }

    @Test
    fun `a time at the missing-time card answers it`() {
        val request = match("at 8 pm", CommandMatcher.Awaiting.MISSING_TIME) as Request.SetReminder
        assertTrue("the answer carries a time", request.timeMs != null)
    }

    // ---------------- the grammar pass ----------------

    @Test
    fun `hotwords carry the command phrases plus this phone's contacts and apps`() {
        val hotwords = CommandMatcher.hotwords(listOf("Mom", "Dave"), listOf("Clock", "Maps"))
        val lines = hotwords.lines()
        assertTrue("call Mom" in lines)
        assertTrue("text Dave" in lines)
        assertTrue("open Clock" in lines)
        assertTrue("remind me to" in lines)
        assertTrue("every line is non-blank", lines.none { it.isBlank() })
        assertEquals("no duplicates", lines.size, lines.distinct().size)
    }

    // ---------------- normalisation ----------------

    @Test
    fun `speech and typing reach the same request`() {
        assertEquals(match("TEXT MOM I'M ON MY WAY"), match("Text Mom, I'm on my way."))
    }
}
