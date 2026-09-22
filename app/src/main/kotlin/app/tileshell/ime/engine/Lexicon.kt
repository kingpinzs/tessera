package app.tileshell.ime.engine

import java.io.Reader

/**
 * The offline dictionary (phase 05 Decisions: "suggestions + autocorrect from an offline dictionary",
 * English (US) only per R2D-12) loaded from `assets/keyboard/en_US.tsv`: one `word<TAB>count` per line,
 * count descending, spelled as the source spells it ("Monday", "I", "don't").
 *
 * Rank is the line order, so rank 0 is the commonest word and no sort is needed at load. Lookups are
 * case-insensitive — the typist writes "monday" and the strip offers "Monday" — but the source casing is
 * kept, because it is what the strip shows and what autocorrect inserts.
 *
 * Reads a [Sequence] of lines or a [Reader], never a path: the IME hands in the asset stream and the
 * tests hand in a handful of literal lines.
 */
class Lexicon private constructor(
    private val words: Array<String>,
    private val counts: LongArray,
    /**
     * True where the all-lowercase spelling was itself in the source. "US" and "us" are both real; when
     * the source carries both, typing "us" must not be "corrected" to "US" — but "monday" alone in a
     * source that only knows "Monday" is a casing slip worth fixing.
     */
    private val lowercaseInSource: BooleanArray,
    internal val trie: Trie,
) {

    val size: Int get() = words.size

    /** Rank of [word] (0 = commonest), ignoring case; -1 when it is not a word. */
    fun rankOf(word: CharSequence): Int {
        if (word.isEmpty()) return -1
        val node = trie.find(word)
        return if (node == Trie.NONE) -1 else trie.wordOf[node]
    }

    fun contains(word: CharSequence): Boolean = rankOf(word) >= 0

    /** The source spelling of [word] ("Monday" for "monday"), or null when it is not a word. */
    fun canonical(word: CharSequence): String? {
        val r = rankOf(word)
        return if (r < 0) null else words[r]
    }

    fun wordAt(rank: Int): String = words[rank]

    fun countAt(rank: Int): Long = counts[rank]

    /** Whether the source spelled the word at [rank] in lowercase too (see [lowercaseInSource]). */
    fun isLowercaseInSource(rank: Int): Boolean = lowercaseInSource[rank]

    companion object {

        fun load(reader: Reader): Lexicon = reader.buffered().useLines { load(it) }

        fun load(lines: Sequence<String>): Lexicon {
            val words = ArrayList<String>(1 shl 17)
            var counts = LongArray(1 shl 17)
            var lowercase = BooleanArray(1 shl 17)
            val trie = Trie(1 shl 18)
            for (line in lines) {
                if (line.isEmpty() || line[0] == '#') continue
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val count = parseCount(line, tab + 1)
                if (count < 0) continue
                val word = line.substring(0, tab)
                val node = trie.insert(word)
                val existing = trie.wordOf[node]
                val isLower = isAllLowercase(word)
                if (existing != Trie.NONE) {
                    // A second casing of a word already ranked: the first (higher count) stays canonical.
                    if (isLower) lowercase[existing] = true
                    continue
                }
                val index = words.size
                if (index == counts.size) {
                    counts = counts.copyOf(index * 2)
                    lowercase = lowercase.copyOf(index * 2)
                }
                trie.setWord(node, index)
                words.add(word)
                counts[index] = count
                lowercase[index] = isLower
            }
            return Lexicon(words.toTypedArray(), counts.copyOf(words.size), lowercase.copyOf(words.size), trie)
        }

        /** The count digits from [from] to the end of [line], or -1 if they are not a number. */
        private fun parseCount(line: String, from: Int): Long {
            if (from >= line.length) return -1
            var value = 0L
            for (i in from until line.length) {
                val c = line[i]
                if (c < '0' || c > '9') return -1
                value = value * 10 + (c - '0')
            }
            return value
        }

        private fun isAllLowercase(word: String): Boolean {
            for (c in word) if (Character.isUpperCase(c)) return false
            return true
        }
    }
}
