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
 *
 * A paste reaches the field two ways (the D2 review's F1, F2): Compose's own paste (the context menu's Paste, Ctrl+V,
 * accessibility's paste), which reads the clipboard through the field's Clipboard and is told to [SecretEdits] as it
 * reads; and a keyboard's commit (its clipboard chip), which only the text can tell — read, as little as can be, against
 * the clipboard's text.
 */
class PastedCredentialTest {
    private val token = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJxYSJ9.s-g_n"

    /**
     * A clipboard: [clips] are what successive reads see (the last one repeats); its description says it holds text
     * when the next read would see some, unless [described] says otherwise (an image, say); [throwOn] makes the
     * description, a read or the clear throw.
     */
    private class FakeClip(vararg clips: String?, val described: Boolean? = null, val throwOn: String? = null) : ClipPort {
        private val seen = clips.toList()
        var reads = 0
        var descriptions = 0
        var cleared = false

        override fun describesText(): Boolean {
            if (throwOn == "describe") throw SecurityException("clipboard holds $seen")
            descriptions++
            return described ?: (seen[minOf(reads, seen.size - 1)] != null)
        }

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
        // A paste that leaves the text SHORTER than it was is still a paste: what it put in is weighed, not the growth.
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("old-wrong-password-typed", "s3cret-pw", FakeClip("s3cret-pw")))
    }

    @Test fun `a keyboard's paste of exactly the fewest characters a paste may have, or one repeating its neighbours, is seen`() {
        // MIN_PASTE characters put in at once are already enough to be weighed.
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("x", "xab", FakeClip("ab")))
        // What was put in is found between the text kept before and after it, never counted twice when it repeats them.
        assertEquals(ClipOutcome.Cleared, PastedCredential.afterEdit("abab", "ababab", FakeClip("ab")))
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
        assertEquals("not even its description", 0, clip.descriptions)
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

    @Test fun `text put in that is not a paste - an autofill, a keyboard's commit - reads the clipboard only when its description says it holds text`() {
        // No text on the clipboard (empty, cleared by the last paste, or an image): its description says so, and that is
        // all that is asked — getPrimaryClipDescription(), which AOSP neither notes nor answers with the notice.
        val empty = FakeClip(null)
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit("", token, empty))
        assertEquals("the description, never the clip", 1 to 0, empty.descriptions to empty.reads)
        assertFalse(empty.cleared)
        val image = FakeClip(token, described = false)
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit("", "some-other-secret", image))
        assertEquals(0, image.reads)
        // Text on the clipboard: a keyboard's commit of two or more characters, or an autofill, can only be told from a
        // paste by its text, so it costs one read (the KDoc's trade-off) — and leaves the clipboard as it is.
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.NotAPaste, PastedCredential.afterEdit("", "some-other-secret", clip))
        assertEquals(1, clip.reads)
        assertFalse(clip.cleared)
        // Part of the clipboard's text is not the clipboard's text, nor is it with more around it than was there.
        assertFalse(PastedCredential.isPaste("", token.take(10), token))
        assertFalse(PastedCredential.isPaste("", "x$token", token))
        assertFalse(PastedCredential.isPaste("ab", "x${token}y", token))
        assertFalse(PastedCredential.isPaste("", token, null))
        // The text kept before the paste must be the old text's start, and the text kept after it the old text's end.
        assertFalse(PastedCredential.isPaste("abc", "Z${token}bc", token))
        assertFalse(PastedCredential.isPaste("abc", "a${token}ZZ", token))
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
        // Not the same text in another case, nor other text of the same length.
        assertFalse(PastedCredential.mayClear(token, token.uppercase()))
        assertFalse(PastedCredential.mayClear(token, token.reversed()))
    }

    @Test fun `a clipboard that throws is named by its class alone and nothing is cleared`() {
        val unreadable = FakeClip(token, throwOn = "read")
        assertEquals(ClipOutcome.Failed("SecurityException"), PastedCredential.afterEdit("", token, unreadable))
        assertFalse(unreadable.cleared)
        val refusing = FakeClip(token, throwOn = "clear")
        assertEquals(ClipOutcome.Failed("SecurityException"), PastedCredential.afterEdit("", token, refusing))
        val undescribed = FakeClip(token, throwOn = "describe")
        assertEquals(ClipOutcome.Failed("SecurityException"), PastedCredential.afterEdit("", token, undescribed))
        assertFalse(undescribed.cleared)
        val composeRefusing = FakeClip(token, throwOn = "clear")
        assertEquals(ClipOutcome.Failed("SecurityException"), PastedCredential.afterEdit("", token, composeRefusing, pasteRead = token))
    }

    // ---- Compose's own paste: told to the field as it reads the clipboard (the review's F1)

    @Test fun `Compose's own paste over a selection is seen however little of the text it changes`() {
        // The review's probe E1a / E1b: "Hunter2" or "Hunter2?" selected whole, "Hunter2!" pasted over it — one character
        // added or changed, which the keyboard's path takes for typing.
        assertEquals(ClipOutcome.NotRead, PastedCredential.afterEdit("Hunter2", "Hunter2!", FakeClip("Hunter2!")))
        for (before in listOf("Hunter2", "Hunter2?")) {
            val clip = FakeClip("Hunter2!")
            assertEquals(before, ClipOutcome.Cleared, PastedCredential.afterEdit(before, "Hunter2!", clip, pasteRead = "Hunter2!"))
            assertTrue(clip.cleared)
            assertEquals("what was pasted is known: the one read is the clear's check that the clipboard still holds it", 1, clip.reads)
            assertEquals("and the description is not asked", 0, clip.descriptions)
        }
        // Through the field's memory, as the field has it: the read is told, then the edit comes.
        val edits = SecretEdits("Hunter2")
        val clip = FakeClip("Hunter2!")
        edits.pasteRead("Hunter2!")
        assertEquals(ClipOutcome.Cleared, edits.edited("Hunter2!", clip))
        assertTrue(clip.cleared)
    }

    @Test fun `what Compose's paste read is weighed for the edit after it alone, and only if that edit put it in`() {
        // Pasted over the same text, Compose's field makes no edit; the read is not left to turn the next key into a paste.
        val clip = FakeClip("Hunter2!")
        val edits = SecretEdits("Hunter2!")
        edits.pasteRead("Hunter2!")
        assertEquals(ClipOutcome.NotRead, edits.edited("Hunter2!x", clip))
        assertEquals(0, clip.reads)
        assertFalse(clip.cleared)
        // A read is used once: the edit after the paste's own is the keyboard's path again.
        val again = SecretEdits("")
        again.pasteRead(token)
        assertEquals(ClipOutcome.Cleared, again.edited(token, FakeClip(token)))
        val typed = FakeClip(token)
        assertEquals(ClipOutcome.NotRead, again.edited("${token}x", typed))
        assertEquals(0, typed.reads)
        // Compose's paste, but by the clear the clipboard holds a newer copy: never wiped.
        val newer = FakeClip("fresh-clip-text")
        assertEquals(ClipOutcome.Newer, PastedCredential.afterEdit("", token, newer, pasteRead = token))
        assertFalse(newer.cleared)
    }

    // ---- the field's text before each edit (the review's N2)

    @Test fun `two edits before the page shows the first are each weighed against the field's text just before it`() {
        // Two keys typed in one frame: the second is one character, never a paste — the clipboard is not read.
        val edits = SecretEdits("")
        val clip = FakeClip(token)
        assertEquals(ClipOutcome.NotRead, edits.edited("a", clip))
        assertEquals(ClipOutcome.NotRead, edits.edited("ab", clip))
        assertEquals("typing never reads the clipboard", 0, clip.reads)
        // A key, then the keyboard's paste, in one frame: the paste is the clipboard's text put in after the key.
        assertEquals(ClipOutcome.Cleared, edits.edited("ab$token", clip))
        assertTrue(clip.cleared)
    }

    @Test fun `a value the page sets is the field's text for the next edit`() {
        // The token pasted with its line break; the page keeps it without, then a key is typed.
        val edits = SecretEdits("")
        val copied = "$token\n"
        assertEquals(ClipOutcome.Cleared, edits.edited(copied, FakeClip(copied)))
        edits.shown(token)
        val clip = FakeClip(copied)
        assertEquals(ClipOutcome.NotRead, edits.edited("${token}x", clip))
        assertEquals(0, clip.reads)
        // The page empties the field (a sign-in sent); the next paste is weighed against the empty field.
        edits.shown("")
        assertEquals(ClipOutcome.Cleared, edits.edited(token, FakeClip(token)))
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
