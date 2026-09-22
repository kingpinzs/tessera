package app.tileshell.ime.engine

import kotlin.math.log10
import kotlin.math.min

/**
 * What the strip shows for the word being composed. [items] are most likely first; when [autoCorrect]
 * is true, `items[0]` is drawn bold and replaces the typed word on space (R6 §2.2.6, D1 l.1553–1555:
 * "If the suggested word is marked in bold, your phone automatically uses it to replace the word you
 * wrote"). The typed word is always one of the items unless it is already the first, so a tap on it
 * keeps what was typed.
 */
data class Suggestions(val items: List<String>, val autoCorrect: Boolean) {
    companion object {
        val NONE = Suggestions(emptyList(), false)
    }
}

/**
 * Per-keystroke suggestions and autocorrect (phase 05 build task 4) over the [Lexicon] and the
 * [UserDictionary].
 *
 * Three sources feed one ranked list:
 *
 * - completions — dictionary words the typed text is a prefix of, the commonest first;
 * - corrections — dictionary words within a small typo budget of the typed text, measured by
 *   [EditCosts] (an adjacent-key slip is half a typo, a dropped, extra or swapped letter one);
 * - the user's own learned words, treated like a moderately common dictionary word.
 *
 * Each candidate scores `edits + FREQUENCY_WEIGHT × log10(rank + 1)`; a completion counts as
 * [COMPLETION_COST] edits so a plausible correction of the whole word beats a long shot completion
 * but a common completion beats an obscure correction ("tha" → "that", not "tea").
 *
 * Autocorrect is deliberately narrower than suggesting. It fires only when the typed word is NOT a
 * word (dictionary or learned — a real word is never replaced, however rare), the field allows it
 * ([FieldKind.autoCorrects]), the best candidate is a whole-word correction within [autoBudget] and
 * its score is under [AUTO_SCORE_MAX], which is what keeps gibberish like "qzxv" from being replaced
 * by whatever obscure word happens to be nearest. The one exception is casing: a typed word that IS
 * the dictionary word in a different case ("monday", "i") is corrected to the source spelling,
 * unless the source itself knows the lowercase spelling ("us" beside "US").
 *
 * The typist's own capitalisation wins over the dictionary's: "Teh" → "The", "TEH" → "THE".
 *
 * [previousWord] is accepted so the IME can pass it and is not consulted: the TSV is a unigram list
 * and carries nothing a previous word could select on.
 */
class Suggester(
    private val lexicon: Lexicon,
    layout: LetterLayout,
    private val userDictionary: UserDictionary? = null,
) {

    private val costs = EditCosts(layout)

    /** One row of the edit-distance table per trie depth, reused across calls. */
    private var rows: Array<FloatArray> = emptyArray()

    private class Candidate(val word: String, val lower: String, val rank: Int) {
        var edits = Float.MAX_VALUE
        var completion = false
        var score = Float.MAX_VALUE
    }

    fun suggest(typed: String, field: FieldKind = FieldKind.TEXT, previousWord: String? = null): Suggestions {
        if (typed.isEmpty() || !field.suggests) return Suggestions.NONE
        val n = typed.length
        val lower = CharArray(n) { Character.toLowerCase(typed[it]) }
        val lowerString = String(lower)

        val candidates = HashMap<String, Candidate>()
        collectCompletions(lower, candidates)
        collectCorrections(lower, searchBudget(n), candidates)
        collectUserWords(lower, lowerString, searchBudget(n), candidates)
        candidates.remove(lowerString)

        for (c in candidates.values) {
            val edits = if (c.completion) min(c.edits, COMPLETION_COST) else c.edits
            c.score = edits + FREQUENCY_WEIGHT * log10(c.rank + 1f)
        }
        val ranked = candidates.values.sortedBy { it.score }

        val known = knownSpelling(typed, lowerString)
        val items = ArrayList<String>(MAX_ITEMS)
        val autoCorrect: Boolean
        if (known != null) {
            val cased = applyCase(typed, known)
            if (cased != typed && field.autoCorrects) {
                autoCorrect = true
                items.add(cased)
                items.add(typed)
            } else {
                autoCorrect = false
                items.add(typed)
            }
        } else {
            val best = ranked.firstOrNull()
            if (best != null && field.autoCorrects && best.edits <= autoBudget(n) && best.score <= AUTO_SCORE_MAX) {
                autoCorrect = true
                items.add(applyCase(typed, best.word))
                items.add(typed)
            } else {
                autoCorrect = false
                items.add(typed)
            }
        }
        for (c in ranked) {
            if (items.size >= MAX_ITEMS) break
            val cased = applyCase(typed, c.word)
            if (cased !in items) items.add(cased)
        }
        return Suggestions(items, autoCorrect)
    }

    // ---- candidate sources ----

    private fun collectCompletions(prefix: CharArray, out: MutableMap<String, Candidate>) {
        val trie = lexicon.trie
        var node = 0
        for (c in prefix) {
            node = trie.child(node, c)
            if (node == Trie.NONE) return
        }
        // The commonest COMPLETIONS_KEPT words under the prefix node; rank is the word index, so a
        // bounded insertion keeps the best without sorting the (possibly tens of thousands of) rest.
        val best = IntArray(COMPLETIONS_KEPT) { Int.MAX_VALUE }
        var kept = 0
        // Sibling-list heads still to walk. Each node with children pushes one, and a one-letter
        // prefix pushes a few hundred before the first pop, so the stack grows rather than caps.
        var stack = IntArray(256)
        var top = 0
        stack[top++] = trie.firstChild[node]
        while (top > 0) {
            var n = stack[--top]
            while (n != Trie.NONE) {
                val w = trie.wordOf[n]
                if (w != Trie.NONE && (kept < best.size || w < best[kept - 1])) {
                    var i = if (kept < best.size) kept++ else kept - 1
                    while (i > 0 && best[i - 1] > w) { best[i] = best[i - 1]; i-- }
                    best[i] = w
                }
                val child = trie.firstChild[n]
                if (child != Trie.NONE) {
                    if (top == stack.size) stack = stack.copyOf(top * 2)
                    stack[top++] = child
                }
                n = trie.nextSibling[n]
            }
        }
        for (i in 0 until kept) {
            val word = lexicon.wordAt(best[i])
            out.getOrPut(word.lowercase()) { Candidate(word, word.lowercase(), best[i]) }.completion = true
        }
    }

    private fun collectCorrections(typed: CharArray, budget: Float, out: MutableMap<String, Candidate>) {
        val n = typed.size
        val maxDepth = n + budget.toInt() + 2
        if (rows.size < maxDepth + 1 || rows[0].size < n + 1) rows = Array(maxDepth + 1) { FloatArray(n + 1) }
        val root = rows[0]
        for (j in 0..n) root[j] = j * EditCosts.DELETION
        search(0, 0, '\u0000', typed, n, budget, maxDepth, out)
    }

    /**
     * Depth-first over the trie, one edit-distance row per node. A branch is dropped as soon as its
     * row's minimum passes [budget]: nothing below can cost less, because every edit is non-negative.
     */
    private fun search(
        node: Int, depth: Int, prevChar: Char, typed: CharArray, n: Int, budget: Float, maxDepth: Int,
        out: MutableMap<String, Candidate>,
    ) {
        val trie = lexicon.trie
        val parent = rows[depth]
        val grand = if (depth >= 1) rows[depth - 1] else null
        val cur = rows[depth + 1]
        var child = trie.firstChild[node]
        while (child != Trie.NONE) {
            val c = trie.label[child]
            cur[0] = parent[0] + EditCosts.INSERTION
            var rowMin = cur[0]
            for (j in 1..n) {
                var best = min(parent[j] + EditCosts.INSERTION, cur[j - 1] + EditCosts.DELETION)
                best = min(best, parent[j - 1] + costs.substitution(typed[j - 1], c))
                if (grand != null && j >= 2 && typed[j - 1] == prevChar && typed[j - 2] == c) {
                    best = min(best, grand[j - 2] + EditCosts.TRANSPOSITION)
                }
                cur[j] = best
                if (best < rowMin) rowMin = best
            }
            val w = trie.wordOf[child]
            if (w != Trie.NONE && cur[n] <= budget) {
                val word = lexicon.wordAt(w)
                val cand = out.getOrPut(word.lowercase()) { Candidate(word, word.lowercase(), w) }
                if (cur[n] < cand.edits) cand.edits = cur[n]
            }
            if (rowMin <= budget && depth + 1 < maxDepth) search(child, depth + 1, c, typed, n, budget, maxDepth, out)
            child = trie.nextSibling[child]
        }
    }

    private fun collectUserWords(typed: CharArray, lower: String, budget: Float, out: MutableMap<String, Candidate>) {
        val dict = userDictionary ?: return
        for (word in dict.words) {
            val key = word.lowercase()
            val isCompletion = key.length > lower.length && key.startsWith(lower)
            val edits = costs.distance(typed, typed.size, key, budget)
            if (!isCompletion && edits > budget) continue
            val cand = out.getOrPut(key) { Candidate(word, key, USER_WORD_RANK) }
            if (isCompletion) cand.completion = true
            if (edits <= budget && edits < cand.edits) cand.edits = edits
        }
    }

    // ---- the typed word itself ----

    /**
     * The spelling to keep when the typed word IS a word: the dictionary's own casing for a dictionary
     * word, and the typed casing for a learned one — a learned word's capital came from wherever the
     * typist first wrote it, not from its being a proper noun. Null when it is not a word.
     */
    private fun knownSpelling(typed: String, lower: String): String? {
        val rank = lexicon.rankOf(lower)
        if (rank >= 0) {
            // "us" beside "US": the lowercase spelling is a word in its own right, so leave it as typed.
            return if (lexicon.isLowercaseInSource(rank)) typed else lexicon.wordAt(rank)
        }
        return if (userDictionary?.contains(lower) == true) typed else null
    }

    companion object {
        const val MAX_ITEMS = 9
        const val COMPLETIONS_KEPT = 12
        const val COMPLETION_COST = 0.5f
        const val FREQUENCY_WEIGHT = 0.3f
        /**
         * A one-typo correction may replace the typed word only when the candidate is within the
         * commonest ~10k words (1 + 0.3·log10(10⁴) = 2.2); an adjacent-key slip (half a typo) may reach
         * any rank, and a two-typo correction only the first handful. Past that the typed word is
         * more likely deliberate than the candidate is likely meant.
         */
        const val AUTO_SCORE_MAX = 2.2f
        /** A learned word ranks as if it were the 500th commonest: the typist's own words are likely. */
        const val USER_WORD_RANK = 500

        /**
         * How many typos a correction may carry, by typed length. A one-letter slip in a three-letter
         * word is a third of the word, in a ten-letter word a tenth; the budget grows with the length.
         */
        fun searchBudget(n: Int): Float = when {
            n <= 1 -> 0.5f
            n <= 4 -> 1f
            n <= 7 -> 1.5f
            else -> 2f
        }

        /** Like [searchBudget] but for REPLACING the word: nothing under three letters is ever replaced. */
        fun autoBudget(n: Int): Float = if (n <= 2) 0f else searchBudget(n)

        /**
         * The candidate in the typist's capitalisation: all caps stays all caps, a leading capital stays
         * a capital, and lowercase takes the dictionary's own spelling ("monday" → "Monday", "i" → "I").
         */
        fun applyCase(typed: String, candidate: String): String {
            if (typed.length >= 2 && typed.all { !Character.isLetter(it) || Character.isUpperCase(it) } &&
                typed.any { Character.isLetter(it) }
            ) return candidate.uppercase()
            if (Character.isUpperCase(typed[0]) && candidate.isNotEmpty() && Character.isLowerCase(candidate[0])) {
                return candidate.replaceFirstChar { it.uppercaseChar() }
            }
            return candidate
        }
    }
}
