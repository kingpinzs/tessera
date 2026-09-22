package app.tileshell.cortana

import app.tileshell.cortana.action.Contacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tolerant contact lookup (H31).
 *
 * The shipped recogniser mishears a short name more often than it mishears a command: on this build
 * "Text Mom" comes back as "TEXT MAM" and "Call Mom" as "CALL MA'AMS" — on the device AND on the host,
 * with "Mom" in the grammar pass's hotwords at a raised boost. Without a tolerant lookup the ruled
 * "call or text a contact" command cannot be used at all.
 *
 * The risk of a tolerant lookup is texting the WRONG person, so these pin both halves: what it must
 * catch, and what it must refuse.
 */
class ContactsNearMatchTest {

    private val phone = listOf("Mom", "Dad", "Dave Brong", "Sarah", "Jeremy King")

    @Test
    fun `the misrecognitions this build actually produces`() {
        assertEquals("Mom", Contacts.nearestName("mam", phone))
        assertEquals("Mom", Contacts.nearestName("ma'am", phone))
        assertEquals("Mom", Contacts.nearestName("Mum", phone))
    }

    @Test
    fun `an exact name is its own nearest`() {
        assertEquals("Mom", Contacts.nearestName("Mom", phone))
        assertEquals("Dave Brong", Contacts.nearestName("dave brong", phone))
    }

    @Test
    fun `a name that is nothing like a contact is refused`() {
        assertNull(Contacts.nearestName("Rumpelstiltskin", phone))
        assertNull(Contacts.nearestName("the capital of Peru", phone))
    }

    @Test
    fun `an ambiguous near-miss is refused rather than guessed`() {
        // "Jon" sits exactly as close to both, so there is no single contact that was meant.
        assertNull(Contacts.nearestName("Jon", listOf("Ron", "Don")))
    }

    @Test
    fun `nothing to match against`() {
        assertNull(Contacts.nearestName("Mom", emptyList()))
        assertNull(Contacts.nearestName("", phone))
        assertNull(Contacts.nearestName("   ", phone))
    }

    @Test
    fun `similarity is a real ratio`() {
        assertEquals(1f, Contacts.similarity("mom", "mom"), 0.001f)
        assertEquals(0f, Contacts.similarity("abc", "xyz"), 0.001f)
        // One substitution in three characters.
        assertEquals(2f / 3f, Contacts.similarity("mom", "mam"), 0.001f)
        assertTrue(Contacts.similarity("mom", "mam") >= Contacts.NEAR_MATCH_THRESHOLD)
        // A different short name must NOT clear the bar, or every contact matches every other.
        assertTrue(Contacts.similarity("mom", "dad") < Contacts.NEAR_MATCH_THRESHOLD)
    }

    @Test
    fun `a different real name on the same phone is not swallowed`() {
        // The dangerous failure is texting the wrong person, so this is the assertion that matters most.
        assertEquals("Dad", Contacts.nearestName("Dad", phone))
        assertEquals("Sarah", Contacts.nearestName("Sarah", phone))
        assertEquals("Sarah", Contacts.nearestName("Sara", phone))
    }
}
