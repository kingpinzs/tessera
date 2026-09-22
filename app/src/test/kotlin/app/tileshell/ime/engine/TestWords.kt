package app.tileshell.ime.engine

import kotlin.random.Random

/**
 * Dictionary fixtures. [common] is a short list of everyday words in rough frequency order, spelled
 * as the real TSV spells them ("I", "Monday"); [big] pads it to the size of the shipped dictionary with
 * synthetic pronounceable tokens so the timing tests run at scale without the asset, which is produced
 * in parallel and may not be in the tree yet.
 */
object TestWords {

    val common: List<String> = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "I",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "world", "been", "were", "where", "here", "very", "need", "keep", "feel",
        "three", "free", "week", "call", "little", "better", "coffee", "happy", "sorry", "school",
        "letter", "follow", "green", "tree", "moon", "food", "door", "book", "too", "tea",
        "thanks", "great", "morning", "tomorrow", "today", "please", "meeting", "phone", "home", "word",
        "Monday", "Tuesday", "Friday", "January", "English", "London",
        "don't", "can't", "I'm", "it's",
    )

    /** The [common] words as TSV lines, counts descending. */
    fun tsv(words: List<String> = common): Sequence<String> =
        words.asSequence().mapIndexed { i, w -> "$w\t${10_000_000L / (i + 1)}" }

    val small: Lexicon by lazy { Lexicon.load(tsv()) }

    /** [common] first, then 150k synthetic tokens, which is the shipped dictionary's upper size. */
    val big: Lexicon by lazy { Lexicon.load(tsv(common + synthetic(150_000))) }

    /** Pronounceable letter-only tokens, none of them in [common], stable across runs. */
    fun synthetic(count: Int, seed: Int = 7): List<String> {
        val rnd = Random(seed)
        val consonants = "bcdfghjklmnprstvwz"
        val vowels = "aeiou"
        val seen = HashSet<String>(common.map { it.lowercase() })
        val out = ArrayList<String>(count)
        while (out.size < count) {
            val length = 3 + rnd.nextInt(3) + rnd.nextInt(3) + rnd.nextInt(3) // 3..9, peaking mid-way
            val sb = StringBuilder(length)
            var vowel = rnd.nextBoolean()
            repeat(length) {
                sb.append(if (vowel) vowels[rnd.nextInt(vowels.length)] else consonants[rnd.nextInt(consonants.length)])
                vowel = !vowel
            }
            val w = sb.toString()
            if (seen.add(w)) out.add(w)
        }
        return out
    }
}
