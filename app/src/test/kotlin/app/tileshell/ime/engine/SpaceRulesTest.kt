package app.tileshell.ime.engine

import app.tileshell.ime.engine.SpaceRules.PERIOD
import app.tileshell.ime.engine.SpaceRules.PLAIN_SPACE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Double-space period (phase 05 Decisions stand-in (5), H23) on both sides of its 1100 ms window and
 * each of its exclusions, and sentence-start detection for auto-capitalisation.
 */
class SpaceRulesTest {

    private fun space(before: String, ms: Long, field: FieldKind = FieldKind.TEXT) = SpaceRules.space(before, ms, field)

    @Test
    fun `a second space within 1100 ms after a letter becomes a period`() {
        assertEquals(PERIOD, space("hello ", 500))
        assertEquals(PERIOD, space("hello ", 1100))
        assertEquals(PERIOD, space("hello ", 0))
        assertEquals(PERIOD, space("hello world ", 1099))
    }

    @Test
    fun `1101 ms is too late`() {
        assertEquals(PLAIN_SPACE, space("hello ", 1101))
        assertEquals(PLAIN_SPACE, space("hello ", 1500))
        assertEquals(PLAIN_SPACE, space("hello ", Long.MAX_VALUE))
        assertEquals(PLAIN_SPACE, space("hello ", -1))
    }

    @Test
    fun `never after a digit, a period, or any non-letter`() {
        // Edge cases: "double space after a number or URL (no period)".
        assertEquals(PLAIN_SPACE, space("room 101 ", 300))
        assertEquals(PLAIN_SPACE, space("done. ", 300))
        assertEquals(PLAIN_SPACE, space("hello, ", 300))
        assertEquals(PLAIN_SPACE, space("hello? ", 300))
        assertEquals(PLAIN_SPACE, space("hello) ", 300))
        assertEquals(PLAIN_SPACE, space("hello  ", 300))
    }

    @Test
    fun `only directly after a single space`() {
        assertEquals(PLAIN_SPACE, space("hello", 300))
        assertEquals(PLAIN_SPACE, space("", 300))
        assertEquals(PLAIN_SPACE, space(" ", 300))
        assertEquals(PLAIN_SPACE, space("hello\n", 300))
    }

    @Test
    fun `never in a URL, email or password field`() {
        assertEquals(PLAIN_SPACE, space("tileshell ", 300, FieldKind.URL))
        assertEquals(PLAIN_SPACE, space("jeremy ", 300, FieldKind.EMAIL))
        assertEquals(PLAIN_SPACE, space("hunter ", 300, FieldKind.PASSWORD))
        assertEquals(PERIOD, space("hello ", 300, FieldKind.NO_SUGGESTIONS))
    }

    @Test
    fun `the period edit replaces the first space`() {
        assertEquals(1, PERIOD.deleteBefore)
        assertEquals(". ", PERIOD.insert)
        assertEquals(SpaceRules.Edit(0, " "), PLAIN_SPACE)
        assertEquals(1100L, SpaceRules.DOUBLE_SPACE_WINDOW_MS)
    }

    // ---- sentence starts ----

    @Test
    fun `a sentence starts at the field start and after a terminal followed by a space`() {
        assertTrue(SpaceRules.startsSentence("", true))
        assertTrue(SpaceRules.startsSentence("Done. ", true))
        assertTrue(SpaceRules.startsSentence("Really! ", true))
        assertTrue(SpaceRules.startsSentence("Why? ", true))
        assertTrue(SpaceRules.startsSentence("Done.  ", true))
        assertTrue(SpaceRules.startsSentence("line one\n", true))
        assertTrue(SpaceRules.startsSentence("   ", true))
    }

    @Test
    fun `mid-sentence, mid-word, and a terminal with no space after it are not starts`() {
        assertFalse(SpaceRules.startsSentence("hello ", true))
        assertFalse(SpaceRules.startsSentence("hello", true))
        assertFalse(SpaceRules.startsSentence("e.g.", true))
        assertFalse(SpaceRules.startsSentence("3.14 ", true))
        assertFalse(SpaceRules.startsSentence("hello, ", true))
    }

    @Test
    fun `only when the field asks for sentence caps`() {
        assertFalse(SpaceRules.startsSentence("", false))
        assertFalse(SpaceRules.startsSentence("Done. ", false))
    }
}
