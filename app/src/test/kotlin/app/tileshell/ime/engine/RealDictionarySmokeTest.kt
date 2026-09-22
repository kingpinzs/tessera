package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The shipped dictionary, when it is in the tree: `assets/keyboard/en_US.tsv` is produced by another
 * build task and may be absent in a worktree, in which case this is skipped, not failed. Times the
 * load (target < 300 ms) and a few decodes on the real 60k–150k words.
 */
class RealDictionarySmokeTest {

    private val candidates = listOf(
        File("src/main/assets/keyboard/en_US.tsv"),
        File("app/src/main/assets/keyboard/en_US.tsv"),
        File(System.getProperty("user.home"), "projects/metro-launcher/app/src/main/assets/keyboard/en_US.tsv"),
    )

    @Test
    fun `the real TSV loads fast and the engines answer on it`() {
        val file = candidates.firstOrNull { it.isFile }
        assumeTrue("en_US.tsv not present; skipping the real-dictionary smoke test", file != null)
        file!!

        val t0 = System.nanoTime()
        val lexicon = file.reader(Charsets.UTF_8).use { Lexicon.load(it) }
        val loadMs = (System.nanoTime() - t0) / 1_000_000.0
        assertTrue("dictionary has ${lexicon.size} words; expected 60k–150k", lexicon.size in 60_000..150_000)
        // The chosen source ranks by 12dicts frequency BANDS, not counts (BUILD-START.md): every word in a
        // band shares one count and ties sort alphabetically, so "the" is in the top band, not at rank 0.
        val the = lexicon.rankOf("the")
        assertTrue("'the' is not in the dictionary", the >= 0)
        assertEquals("'the' is not in the top frequency band", lexicon.countAt(0), lexicon.countAt(the))
        assertTrue(lexicon.contains("because"))
        assertEquals("I", lexicon.canonical("i"))

        val suggester = Suggester(lexicon, TestLayout.w10m)
        val probes = listOf("teh", "becase", "th", "peopel", "qzxv", "monday", "tomorow")
        repeat(10) { for (p in probes) suggester.suggest(p) }
        val suggestMs = probes.map { p ->
            val s0 = System.nanoTime()
            repeat(10) { suggester.suggest(p) }
            (System.nanoTime() - s0) / 10 / 1_000_000.0
        }
        assertEquals("the", suggester.suggest("teh").items[0])
        assertEquals("because", suggester.suggest("becase").items[0])
        assertTrue(suggester.suggest("qzxv").autoCorrect.not())

        val t1 = System.nanoTime()
        val decoder = SwipeDecoder(lexicon, TestLayout.w10m)
        val indexMs = (System.nanoTime() - t1) / 1_000_000.0
        val words = listOf("hello", "world", "people", "because", "tomorrow", "the", "meeting", "school", "coffee", "little")
        repeat(3) { for (w in words) decoder.decode(SwipePaths.realistic(w)) }
        val decodeMs = words.map { w ->
            val path = SwipePaths.realistic(w)
            val d0 = System.nanoTime()
            repeat(5) { decoder.decode(path) }
            (System.nanoTime() - d0) / 5 / 1_000_000.0
        }
        val wrong = words.filter { decoder.decode(SwipePaths.realistic(it)).firstOrNull() != it }

        println(
            "TIMING real en_US.tsv (${lexicon.size} words): load ${"%.0f".format(loadMs)} ms, " +
                "suggest worst ${"%.2f".format(suggestMs.max())} ms, swipe index ${"%.0f".format(indexMs)} ms, " +
                "decode worst ${"%.2f".format(decodeMs.max())} ms, mis-decoded $wrong",
        )
        assertTrue("load took $loadMs ms", loadMs < 300.0)
        assertTrue("suggest took ${suggestMs.max()} ms", suggestMs.max() < 20.0)
        assertTrue("decode took ${decodeMs.max()} ms", decodeMs.max() < 100.0)
        assertTrue("mis-decoded on the real dictionary: $wrong", wrong.size <= 1)
    }
}
