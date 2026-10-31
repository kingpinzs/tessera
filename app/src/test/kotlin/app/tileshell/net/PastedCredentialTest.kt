package app.tileshell.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17, the owner's ruling D2 (a) 2: a credential pasted into the TMDB key field or the password field is cleared
 * from the clipboard — but only a PASTE (typed text never), and only while the clipboard still holds exactly what was
 * pasted (a newer copy is never wiped). Nothing about the pasted value is ever written to a line.
 */
class PastedCredentialTest {
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJxYSJ9.s-g_n"

    /** A clipboard: [clips] are what successive reads see (the last one repeats); [throwOn] makes a read or the clear throw. */
    private class FakeClip(vararg clips: String?, val throwOn: String? = null) : ClipPort {
        private val seen = clips.toList()
        var reads = 0
        var cleared = false

        override fun text(): String? {
            if (throwOn == "read") throw SecurityException("clipboard holds $seen")
            return seen[minOf(reads++, seen.size - 1)]
        }

        override fun clear() {
            if (throwOn == "clear") throw SecurityException("clipboard holds $seen")
            cleared = true
        }
    }

    // ---- what a paste is

    @Test fun `a paste into the empty field is seen and the clipboard is cleared`() {
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("", token, clip))
        assertTrue(clip.cleared)
    }

    @Test fun `a paste at the end, at the start and in the middle of what is already there is seen`() {
        assertTrue(PastedCredential.isPaste("abc", "abc$token", token))
        assertTrue(PastedCredential.isPaste("abc", "${token}abc", token))
        assertTrue(PastedCredential.isPaste("abc", "a${token}bc", token))
        // Neighbours that repeat the paste's own first characters: the paste is still found where it was put.
        assertTrue(PastedCredential.isPaste("a", "aba", "ab"))
        assertTrue(PastedCredential.isPaste("xx", "xxxx", "xx"))
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("abc", "a${token}bc", clip))
        assertTrue(clip.cleared)
    }

    @Test fun `a paste over a selection is seen`() {
        assertTrue("the whole text replaced", PastedCredential.isPaste("old-key-typed-wrong", token, token))
        assertTrue("a stretch replaced", PastedCredential.isPaste("old-value", "${token}-value", token))
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("old-key-typed-wrong", token, clip))
        assertTrue(clip.cleared)
    }

    @Test fun `a token copied with its line break is seen in the field's raw text, which is why the raw text is weighed`() {
        val copied = "$token\r\n"
        val clip = FakeClip(copied)
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("", copied, clip))
        assertTrue(clip.cleared)
        // What the single-line field keeps (its line feed dropped) is not the clipboard's text.
        assertFalse(PastedCredential.isPaste("", "$token\r", copied))
    }

    // ---- typed text, and other fields' text, never clears it

    @Test fun `typed text is never a paste - one character at a time, the clipboard is not even read`() {
        val clip = FakeClip(token)
        var field = ""
        for (c in token) {
            val next = field + c
            assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit(field, next, clip))
            field = next
        }
        assertEquals(token, field)
        assertEquals("no read, so no 'pasted from your clipboard' notice either", 0, clip.reads)
        assertFalse(clip.cleared)
    }

    @Test fun `a typed character equal to a one-character clipboard is typing, not a paste`() {
        val clip = FakeClip("x")
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit("ab", "abx", clip))
        assertFalse(PastedCredential.isPaste("ab", "abx", "x"))
        assertFalse(clip.cleared)
    }

    @Test fun `a deletion and a one-character replacement are not pastes`() {
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit(token, token.dropLast(1), clip))
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit(token, "", clip))
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit("abc", "x", clip))
        assertEquals(0, clip.reads)
        assertFalse(clip.cleared)
    }

    @Test fun `text put in that is not the clipboard's - an autofill, an older copy from the keyboard's history - leaves it`() {
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.NotAPaste, PastedCredential.afterEdit("", "some-other-secret", clip))
        assertFalse(clip.cleared)
        // Part of the clipboard's text is not the clipboard's text, nor is it with more around it than was there.
        assertFalse(PastedCredential.isPaste("", token.take(10), token))
        assertFalse(PastedCredential.isPaste("", "x$token", token))
        assertFalse(PastedCredential.isPaste("ab", "x${token}y", token))
        assertFalse(PastedCredential.isPaste("", token, null))
        val empty = FakeClip(null)
        assertEquals(ClipOutcome.NotAPaste, PastedCredential.afterEdit("", token, empty))
        assertFalse(empty.cleared)
    }

    // ---- a newer copy is never wiped

    @Test fun `the clipboard is not cleared when it changed between the paste and the clear`() {
        val clip = FakeClip(token, "fresh-clip-text")
        assertEquals(ClipOutcome.Newer, PastedCredential.afterEdit("", token, clip))
        assertFalse(clip.cleared)
        val emptied = FakeClip(token, null)
        assertEquals(ClipOutcome.Newer, PastedCredential.afterEdit("", token, emptied))
        assertFalse(emptied.cleared)
    }

    @Test fun `only exactly what was pasted may be cleared`() {
        assertTrue(PastedCredential.mayClear(token, token))
        assertFalse(PastedCredential.mayClear(token, "$token "))
        assertFalse(PastedCredential.mayClear(token, token.drop(1)))
        assertFalse(PastedCredential.mayClear(token, "something else"))
        assertFalse(PastedCredential.mayClear(token, null))
    }

    @Test fun `a clipboard that throws is named by its class alone and nothing is cleared`() {
        val unreadable = FakeClip(token, throwOn = "read")
        assertEquals(ClipOutcome.Failed("SecurityException"), PastedCredential.afterEdit("", token, unreadable))
        assertFalse(unreadable.cleared)
        val refusing = FakeClip(token, throwOn = "clear")
        assertEquals(ClipOutcome.Failed("SecurityException"), PastedCredential.afterEdit("", token, refusing))
    }

    // ---- what is written

    @Test fun `no line carries the pasted value or its length, and typing writes none`() {
        val outcomes = listOf(
            PastedCredential.afterEdit("", token, FakeClip(token)),
            PastedCredential.afterEdit("", token, FakeClip(token, "fresh-clip-text")),
            PastedCredential.afterEdit("", "some-other-secret", FakeClip(token)),
            PastedCredential.afterEdit("", token, FakeClip(token, throwOn = "read")),
            PastedCredential.afterEdit("", token, FakeClip(token, throwOn = "clear")),
        )
        val lines = outcomes.map { PastedCredential.line(it) }
        assertTrue("every outcome but typing has a line: $lines", lines.all { !it.isNullOrBlank() })
        assertEquals("cleared, newer, not a paste and failed each say something different", 4, lines.toSet().size)
        for (line in lines.filterNotNull()) {
            assertFalse(line, line.contains(token) || line.contains("eyJ") || line.contains("some-other") || line.contains("fresh-clip"))
            assertFalse("no length or any other number: $line", line.any { it.isDigit() })
        }
        assertTrue(PastedCredential.line(ClipOutcome.Cleared)!!.contains("cleared"))
        assertNull(PastedCredential.line(PastedCredential.afterEdit("ab", "abc", FakeClip(token))))
    }
}
