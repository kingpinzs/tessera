package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Word Flow (phase 05 build task 3, E4): synthesised finger paths over the R6 §2.1 grid must come back
 * as the word they traced, with the strip's bold first candidate (R6 §2.4.5) right.
 */
class SwipeDecoderTest {

    private val decoder = SwipeDecoder(TestWords.small, TestLayout.w10m)

    /** Everyday words, a third of them with a doubled letter the finger does not loop for. */
    private val words = listOf(
        "hello", "world", "the", "and", "you", "that", "with", "have", "this", "from",
        "they", "will", "what", "there", "their", "would", "make", "like", "time", "look",
        "people", "think", "work", "well", "book", "see", "feel", "need", "keep", "three",
        "free", "week", "call", "little", "better", "coffee", "happy", "sorry", "school", "letter",
        "follow", "green", "tree", "moon", "food", "door", "tomorrow", "meeting", "please", "morning",
    )

    @Test
    fun `a realistic swipe over each of fifty common words ranks that word first`() {
        val wrong = words.filter { w -> decoder.decode(SwipePaths.realistic(w)).firstOrNull() != w }
        assertTrue("mis-decoded: ${wrong.map { it to decoder.decode(SwipePaths.realistic(it)).take(3) }}", wrong.isEmpty())
    }

    @Test
    fun `a noisier swipe still ranks the word first for nearly every word`() {
        val wrong = words.filter { w ->
            val path = SwipePaths.realistic(w, seed = 99, jitterPx = 12f, cornerCut = 0.35f, maxCutPx = 45f, startOffsetPx = 40f)
            decoder.decode(path).firstOrNull() != w
        }
        assertTrue("mis-decoded under heavy noise: $wrong", wrong.size <= 2)
    }

    @Test
    fun `double-letter words decode from a path that visits the key once`() {
        for (w in listOf("hello", "book", "coffee", "letter", "three", "keep", "week", "moon", "door", "happy")) {
            assertEquals(w, decoder.decode(SwipePaths.realistic(w)).first())
        }
    }

    @Test
    fun `a very fast swipe with only the corners sampled still decodes`() {
        // Edge cases: "very fast swipes".
        for (w in listOf("hello", "world", "people", "because", "the", "meeting")) {
            assertEquals(w, decoder.decode(SwipePaths.fast(w)).first())
        }
        // Two samples for a two-key word: nothing to resample between.
        assertEquals("to", decoder.decode(SwipePaths.fast("to")).first())
    }

    @Test
    fun `a swipe that fits two words offers both, commonest first`() {
        // "too" and "to" trace the same t → o path; frequency breaks the tie and both are offered.
        val out = decoder.decode(SwipePaths.realistic("too"))
        assertEquals("to", out[0])
        assertTrue("too" in out.take(3))
    }

    @Test
    fun `a swipe over a non-dictionary word returns the nearest real words, not nothing and not a throw`() {
        // Edge cases: "Swipe over non-dictionary words".
        // t → h → w is nobody's word; the words that start at t and end a key or so from w are offered.
        val out = decoder.decode(SwipePaths.realistic("thw"))
        assertTrue(out.isNotEmpty())
        assertTrue("expected 'the' among $out", "the" in out)
        assertTrue(out.all { it.startsWith("t") })
        val far = decoder.decode(SwipePaths.realistic("zxq"))
        assertTrue(far.size <= 8)
    }

    @Test
    fun `degenerate paths are sane`() {
        assertTrue(decoder.decode(emptyList()).isEmpty())
        assertTrue(decoder.decode(listOf(KeyPoint(1f, 1f)), maxResults = 0).isEmpty())
        // A single point on the a key is a tap: the one-letter words under it.
        val a = TestLayout.centre('a')
        assertEquals("a", decoder.decode(listOf(a, a, a)).first())
        // A path that starts off the keyboard entirely has no candidates.
        assertTrue(decoder.decode(listOf(KeyPoint(-9000f, -9000f), KeyPoint(-8000f, -8000f))).isEmpty())
        // Two points a few px apart on one key, and a word's worth of samples all on one key.
        assertTrue(decoder.decode(listOf(a, KeyPoint(a.x + 3f, a.y - 2f))).isNotEmpty())
    }

    @Test
    fun `results are capped and carry no duplicates`() {
        val out = decoder.decode(SwipePaths.realistic("there"), maxResults = 5)
        assertTrue(out.size <= 5)
        assertEquals(out.size, out.toSet().size)
    }

    @Test
    fun `a learned word can be swiped`() {
        val user = UserDictionary(MemoryStore()) { TestWords.small.contains(it) }
        user.add("selawik")
        val d = SwipeDecoder(TestWords.small, TestLayout.w10m, user)
        assertEquals("selawik", d.decode(SwipePaths.realistic("selawik")).first())
    }

    @Test
    fun `a lift decodes in under 100 ms on a 150k lexicon`() {
        val big = SwipeDecoder(TestWords.big, TestLayout.w10m)
        val probes = listOf("hello", "the", "people", "because", "tomorrow", "see", "meeting", "world", "a", "school")
        repeat(5) { for (p in probes) big.decode(SwipePaths.realistic(p)) }
        val times = probes.map { p ->
            val path = SwipePaths.realistic(p)
            val t0 = System.nanoTime()
            repeat(5) { big.decode(path) }
            (System.nanoTime() - t0) / 5 / 1_000_000.0
        }
        val worst = times.max()
        println("TIMING decode 150k: worst ${"%.2f".format(worst)} ms, per probe ${times.map { "%.2f".format(it) }}")
        assertTrue("decode took $worst ms", worst < 100.0)
        val wrong = probes.filter { big.decode(SwipePaths.realistic(it)).firstOrNull() != it }
        assertTrue("mis-decoded on the big lexicon: $wrong", wrong.isEmpty())
    }
}
