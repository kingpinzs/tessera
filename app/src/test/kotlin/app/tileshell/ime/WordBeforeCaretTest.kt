package app.tileshell.ime

import org.junit.Assert.assertEquals
import org.junit.Test

/** The controller's idea of "the word before the caret", which autocorrect and learning both act on. */
class WordBeforeCaretTest {
    private fun w(s: String) = KeyboardController.wordBeforeCaret(s)

    @Test fun `the letters after the last separator`() {
        assertEquals("world", w("hello world"))
        assertEquals("", w("hello "))
        assertEquals("don't", w("I don't"))
        assertEquals("hello", w("hello"))
    }

    @Test fun `a run that fills the whole read window is not a word (EDGE1 run 2)`() {
        val window = "abcdefghij".repeat(7).take(KeyboardController.READ_WINDOW)
        assertEquals("", w(window))
        // A separator inside the window says where the word starts, so a long word is still a word.
        val long = "x".repeat(40)
        assertEquals(long, w("a".repeat(23) + " " + long))
    }
}
