package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/** The TSV dictionary: rank by line order, lookups ignoring case, the source casing kept. */
class LexiconTest {

    private val lex = TestWords.small

    @Test
    fun `rank is the line order, commonest first`() {
        assertEquals(0, lex.rankOf("the"))
        assertEquals(1, lex.rankOf("be"))
        assertEquals(TestWords.common.size, lex.size)
        assertEquals("the", lex.wordAt(0))
        assertEquals(10_000_000L, lex.countAt(0))
    }

    @Test
    fun `lookup ignores case but the source casing is what comes back`() {
        assertTrue(lex.contains("MONDAY"))
        assertEquals("Monday", lex.canonical("monday"))
        assertEquals("I", lex.canonical("i"))
        assertEquals("the", lex.canonical("THE"))
        assertFalse(lex.isLowercaseInSource(lex.rankOf("Monday")))
        assertTrue(lex.isLowercaseInSource(lex.rankOf("the")))
    }

    @Test
    fun `an unknown word has no rank and no spelling`() {
        assertEquals(-1, lex.rankOf("teh"))
        assertNull(lex.canonical("teh"))
        assertFalse(lex.contains(""))
    }

    @Test
    fun `apostrophes are part of the word`() {
        assertTrue(lex.contains("don't"))
        assertEquals("I'm", lex.canonical("i'm"))
    }

    @Test
    fun `a second casing of the same word keeps the first rank and notes the lowercase form`() {
        val l = Lexicon.load(sequenceOf("US\t900", "us\t800", "Us\t5"))
        assertEquals(1, l.size)
        assertEquals("US", l.canonical("us"))
        assertTrue(l.isLowercaseInSource(0))
    }

    @Test
    fun `blank lines, comments and malformed lines are skipped`() {
        val l = Lexicon.load(StringReader("# header\n\nalpha\t10\nnocount\nbad\tx1\n\t7\nbeta\t9\n"))
        assertEquals(2, l.size)
        assertEquals(0, l.rankOf("alpha"))
        assertEquals(1, l.rankOf("beta"))
    }
}
