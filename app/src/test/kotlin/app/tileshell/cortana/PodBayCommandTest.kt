package app.tileshell.cortana

import app.tileshell.brand.Brand
import app.tileshell.cortana.action.LockGate
import app.tileshell.cortana.match.CommandMatcher
import app.tileshell.cortana.match.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 (Decisions "Tess's command", "Locked gate", "Branding"): the pod bay's phrase rule, its grammar phrases and
 * its gate, pinned. The recogniser's UPPERCASE form and the text box's punctuated form run the same matcher (A4).
 */
class PodBayCommandTest {

    private fun match(text: String) = CommandMatcher.match(text)

    @Test
    fun `the doors line opens the pod bay with the easter egg`() {
        assertEquals(Request.OpenPodBay(doors = true), match("open the pod bay doors"))
        assertEquals(Request.OpenPodBay(doors = true), match("OPEN THE POD BAY DOORS"))
        assertEquals(Request.OpenPodBay(doors = true), match("Open the pod bay doors, Tess."))
        // Without the article (pod_bay_doors_noart), and with one door.
        assertEquals(Request.OpenPodBay(doors = true), match("OPEN POD BAY DOORS"))
        assertEquals(Request.OpenPodBay(doors = true), match("open the pod bay door"))
    }

    @Test
    fun `open and show and pull out without doors open it plainly`() {
        assertEquals(Request.OpenPodBay(doors = false), match("open the pod bay"))
        assertEquals(Request.OpenPodBay(doors = false), match("SHOW THE POD BAY"))
        assertEquals(Request.OpenPodBay(doors = false), match("pull out the pod bay"))
    }

    @Test
    fun `close and shut close it`() {
        assertEquals(Request.ClosePodBay, match("close the pod bay doors"))
        assertEquals(Request.ClosePodBay, match("CLOSE THE POD BAY"))
        assertEquals(Request.ClosePodBay, match("shut the pod bay doors"))
    }

    @Test
    fun `the recogniser's one-word podbay still matches`() {
        assertEquals(Request.OpenPodBay(doors = true), match("OPEN THE PODBAY DOORS"))
        assertEquals(Request.ClosePodBay, match("close the podbay"))
    }

    @Test
    fun `the pod and the bay are not the pod bay - they reach the open-app rule`() {
        assertEquals(Request.OpenApp("the pod"), match("open the pod"))
        assertEquals(Request.OpenApp("the bay"), match("OPEN THE BAY"))
    }

    @Test
    fun `the pod bay words never reach the open-app rule`() {
        listOf("open the pod bay doors", "open pod bay doors", "open the pod bay", "close the pod bay doors").forEach {
            assertFalse(it, match(it) is Request.OpenApp)
        }
    }

    @Test
    fun `a reminder about the pod bay is a reminder`() {
        assertTrue(match("remind me to open the pod bay doors at 5 pm") is Request.SetReminder)
    }

    @Test
    fun `a sentence that names the pod bay without an open or close verb is not the command`() {
        assertNull(CommandMatcher.podBay(CommandMatcher.normalise("where is the pod bay")))
    }

    @Test
    fun `the phrase rule follows the branding module's name`() {
        // A public build renames the bay; the matcher listens for whatever Brand says, so the rule is written against a
        // name parameter that defaults to Brand.POD_BAY_NAME.
        assertEquals(Request.OpenPodBay(doors = true), CommandMatcher.podBay("open the stargate doors", "stargate"))
        assertEquals(Request.ClosePodBay, CommandMatcher.podBay("close the stargate", "stargate"))
        assertNull(CommandMatcher.podBay("open the pod bay doors", "stargate"))
        assertEquals(Request.OpenPodBay(doors = true), CommandMatcher.podBay("open the pod bay doors"))
        assertEquals("pod bay", Brand.POD_BAY_NAME)
        assertEquals(
            listOf("open the stargate doors", "open the stargate", "close the stargate doors"),
            CommandMatcher.podBayPhrases("stargate"),
        )
    }

    @Test
    fun `the grammar pass is handed the three pod bay phrases`() {
        val phrases = CommandMatcher.hotwords().split('\n')
        listOf("open the pod bay doors", "open the pod bay", "close the pod bay doors").forEach {
            assertTrue("hotwords() is missing \"$it\"", it in phrases)
        }
    }

    @Test
    fun `both are gated while locked, with their captions`() {
        assertFalse(LockGate.allowedWhileLocked(Request.OpenPodBay(doors = true)))
        assertFalse(LockGate.allowedWhileLocked(Request.OpenPodBay(doors = false)))
        assertFalse(LockGate.allowedWhileLocked(Request.ClosePodBay))
        assertEquals("Open the pod bay", LockGate.restate(Request.OpenPodBay(doors = true)))
        assertEquals("Close the pod bay", LockGate.restate(Request.ClosePodBay))
    }

    @Test
    fun `the doors reply is the film's line exactly`() {
        assertEquals("I'm afraid I can't do that, Dave.", Brand.POD_BAY_DOORS_REPLY)
    }
}
