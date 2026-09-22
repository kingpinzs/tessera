package app.tileshell.ime.engine

import kotlin.math.min

/**
 * How far a typed spelling is from a dictionary spelling, in typos, weighted by the keyboard.
 *
 * A finger that lands one key over is the commonest slip a touch keyboard sees, so swapping a letter
 * for an ADJACENT one (per [LetterLayout.adjacent], derived from the key centres — no hard-coded
 * neighbour table, so a docked layout or another language's rows work unchanged) costs [ADJACENT_SUB]
 * where any other substitution, a dropped letter, an extra letter or two letters swapped costs one.
 *
 * The distance is optimal-string-alignment (Levenshtein plus adjacent transposition): "teh" → "the"
 * is one edit, not two. The costs are all non-negative, which is what lets the trie search cut a
 * branch as soon as every cell of its row is over budget.
 */
internal class EditCosts(layout: LetterLayout) {

    /** Substitution cost for characters below 128, indexed a*128+b; 1.0 outside the table. */
    private val sub = FloatArray(128 * 128) { 1f }

    init {
        for (a in layout.centres.keys) for (b in layout.centres.keys) {
            if (a.code < 128 && b.code < 128 && layout.adjacent(a, b)) sub[a.code * 128 + b.code] = ADJACENT_SUB
        }
    }

    fun substitution(a: Char, b: Char): Float = when {
        a == b -> 0f
        a.code < 128 && b.code < 128 -> sub[a.code * 128 + b.code]
        else -> 1f
    }

    /**
     * The weighted distance between two lowercase spellings, or a value above [budget] as soon as the
     * best possible outcome passes it. Used for the user dictionary, which is small enough to compare
     * word by word; the lexicon is searched through its trie with the same recurrence.
     */
    fun distance(typed: CharArray, n: Int, word: CharSequence, budget: Float): Float {
        val m = word.length
        var prev2: FloatArray? = null
        var prev = FloatArray(n + 1) { it * DELETION }
        var cur = FloatArray(n + 1)
        for (i in 1..m) {
            val c = Character.toLowerCase(word[i - 1])
            cur[0] = prev[0] + INSERTION
            var rowMin = cur[0]
            for (j in 1..n) {
                var best = min(prev[j] + INSERTION, cur[j - 1] + DELETION)
                best = min(best, prev[j - 1] + substitution(typed[j - 1], c))
                if (i >= 2 && j >= 2 && prev2 != null &&
                    typed[j - 1] == Character.toLowerCase(word[i - 2]) && typed[j - 2] == c
                ) best = min(best, prev2[j - 2] + TRANSPOSITION)
                cur[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > budget) return rowMin
            val recycled = prev2 ?: FloatArray(n + 1)
            prev2 = prev
            prev = cur
            cur = recycled
        }
        return prev[n]
    }

    companion object {
        const val ADJACENT_SUB = 0.5f
        const val INSERTION = 1f
        const val DELETION = 1f
        const val TRANSPOSITION = 1f
    }
}
