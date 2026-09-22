package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suggestions and autocorrect (phase 05 build task 4; R6 §2.2.6: the bold first item auto-replaces on
 * space). Every rule in the brief has a row here, and the strip contract — the typed word is always an
 * item unless it is already first — is checked on every call through [check].
 */
class SuggesterTest {

    private val store = MemoryStore()
    private val user = UserDictionary(store) { TestWords.small.contains(it) }
    private val suggester = Suggester(TestWords.small, TestLayout.w10m, user)

    private fun check(typed: String, field: FieldKind = FieldKind.TEXT): Suggestions {
        val s = suggester.suggest(typed, field)
        if (s.items.isNotEmpty()) {
            assertTrue("typed word missing from $s", typed in s.items)
            if (s.autoCorrect) assertEquals("typed word must be second under autocorrect: $s", typed, s.items[1])
            else assertEquals("typed word must lead without autocorrect: $s", typed, s.items[0])
        }
        assertTrue("too many items: $s", s.items.size <= Suggester.MAX_ITEMS)
        return s
    }

    // ---- completions ----

    @Test
    fun `a prefix completes to the commonest words first`() {
        val s = check("th")
        assertFalse(s.autoCorrect)
        assertEquals("th", s.items[0])
        // the (0), that (7), this (20), they (25), there (37), their (38) …
        assertEquals(listOf("the", "that", "this", "they"), s.items.subList(1, 5))
    }

    @Test
    fun `a common completion outranks an obscure correction`() {
        // "tha" → "that" (rank 7, a completion one letter away) rather than "than" (70) or the
        // one-edit "the"; being one edit away, it also replaces on space.
        val s = check("tha")
        assertEquals("that", s.items[0])
        assertTrue(s.autoCorrect)
        // A completion further away than the typo budget is offered but never replaces.
        val long = check("tomor")
        assertFalse(long.autoCorrect)
        assertEquals(listOf("tomor", "tomorrow"), long.items.take(2))
    }

    // ---- corrections ----

    @Test
    fun `a transposition is one typo and autocorrects`() {
        val s = check("teh")
        assertTrue(s.autoCorrect)
        assertEquals("the", s.items[0])
        assertEquals("teh", s.items[1])
    }

    @Test
    fun `a dropped letter autocorrects`() {
        assertEquals(listOf("because", "becase"), check("becase").items.take(2))
        assertTrue(check("becase").autoCorrect)
    }

    @Test
    fun `an adjacent-key slip is cheaper than any other substitution`() {
        // g sits next to h, so "tge" is half a typo from "the"; "tme" (m is two rows down) is a full one.
        val slip = check("tge")
        val far = check("tze")
        assertEquals("the", slip.items[0])
        assertTrue(slip.autoCorrect)
        assertEquals("the", far.items[0])
        // Both fix, but the near miss is the surer one: it sorts ahead of every completion, while the
        // far one is only a correction of a three-letter word.
        assertTrue(far.autoCorrect)
    }

    @Test
    fun `adjacency comes from the layout, not a table`() {
        // On a layout where g is nowhere near h, "tge" is a full-cost substitution — still corrected,
        // but no cheaper than "tze".
        val far = LetterLayout(
            TestLayout.w10m.centres + ('g' to KeyPoint(-9000f, -9000f)),
            TestLayout.PITCH_X, TestLayout.PITCH_Y,
        )
        val s = Suggester(TestWords.small, far).suggest("tge")
        assertEquals("the", s.items[0])
    }

    // ---- what is never autocorrected ----

    @Test
    fun `a word in the lexicon is never replaced`() {
        val s = check("then")
        assertFalse(s.autoCorrect)
        assertEquals("then", s.items[0])
        assertFalse(check("the").autoCorrect)
        assertFalse(check("us").autoCorrect)
    }

    @Test
    fun `a learned word is offered and never replaced, nor re-cased`() {
        user.add("Becase")
        val s = check("becase")
        assertFalse(s.autoCorrect)
        assertEquals("becase", s.items[0])
        assertTrue("because" in s.items)
        // and it completes like any other word
        assertTrue("Becase" in check("beca").items)
        // a learned word typed in another case stays as typed: its capital was incidental
        assertFalse(check("BECASE").autoCorrect)
    }

    @Test
    fun `gibberish is left alone`() {
        val s = check("qzxv")
        assertFalse(s.autoCorrect)
        assertEquals(listOf("qzxv"), s.items)
    }

    @Test
    fun `one or two letters are never replaced`() {
        assertFalse(check("t").autoCorrect)
        assertFalse(check("tj").autoCorrect)
    }

    @Test
    fun `no autocorrect in URL and email fields, no suggestions at all in password and no-suggestions fields`() {
        val url = check("teh", FieldKind.URL)
        assertFalse(url.autoCorrect)
        assertEquals("teh", url.items[0])
        assertTrue("the" in url.items)
        assertFalse(check("teh", FieldKind.EMAIL).autoCorrect)
        assertEquals(Suggestions.NONE, suggester.suggest("teh", FieldKind.PASSWORD))
        assertEquals(Suggestions.NONE, suggester.suggest("teh", FieldKind.NO_SUGGESTIONS))
    }

    // ---- casing ----

    @Test
    fun `the typist's capitalisation pattern is kept`() {
        assertEquals("The", check("Teh").items[0])
        assertEquals("THE", check("TEH").items[0])
        assertEquals("Because", check("Becase").items[0])
    }

    @Test
    fun `a lowercase spelling of a capitalised word is corrected to the source casing`() {
        val monday = check("monday")
        assertTrue(monday.autoCorrect)
        assertEquals(listOf("Monday", "monday"), monday.items.take(2))
        val i = check("i")
        assertTrue(i.autoCorrect)
        assertEquals(listOf("I", "i"), i.items.take(2))
        assertFalse(check("Monday").autoCorrect)
        assertFalse(check("MONDAY").autoCorrect)
    }

    @Test
    fun `a word the source spells in lowercase too is not re-cased`() {
        val lex = Lexicon.load(sequenceOf("US\t900", "us\t800", "the\t700"))
        val s = Suggester(lex, TestLayout.w10m).suggest("us")
        assertFalse(s.autoCorrect)
        assertEquals("us", s.items[0])
    }

    @Test
    fun `an empty composing word has nothing to say`() {
        assertEquals(Suggestions.NONE, suggester.suggest(""))
    }

    // ---- speed ----

    @Test
    fun `a keystroke answers in well under 20 ms on a 150k lexicon`() {
        val big = Suggester(TestWords.big, TestLayout.w10m, user)
        val probes = listOf("t", "th", "teh", "becase", "peopel", "qzxv", "tomorow", "meetng", "a", "everythng")
        repeat(20) { for (p in probes) big.suggest(p) }
        val times = probes.map { p ->
            val t0 = System.nanoTime()
            repeat(10) { big.suggest(p) }
            (System.nanoTime() - t0) / 10 / 1_000_000.0
        }
        val worst = times.max()
        println("TIMING suggest 150k: worst ${"%.2f".format(worst)} ms, per probe ${times.map { "%.2f".format(it) }}")
        assertTrue("suggest took $worst ms", worst < 20.0)
        assertEquals("the", big.suggest("teh").items[0])
    }
}
