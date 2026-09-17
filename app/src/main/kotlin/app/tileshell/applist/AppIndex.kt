package app.tileshell.applist

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Pure grouping and search rules for the app list (no Android types, JVM-tested).
 *
 * Groups: "#" first (every label whose first character is not a Latin letter after accent folding: digits,
 * symbols, non-Latin scripts), then A-Z, only groups that have apps (R3 C2; no "Recently added" group, R3 A13).
 * Search: case- and accent-insensitive substring match, ranked name-prefix, word-prefix, then anywhere.
 */
object AppIndex {
    const val OTHER = "#"

    /** Jump grid order (X8): "#" first, then A-Z. */
    val JUMP_LETTERS: List<String> = listOf(OTHER) + ('A'..'Z').map { it.toString() }

    private val marks = Regex("\\p{M}+")

    /** Latin letters that do not decompose to a base letter under NFKD. */
    private val foldMap = mapOf(
        'æ' to "ae", 'ð' to "d", 'đ' to "d", 'ø' to "o", 'œ' to "oe", 'ł' to "l", 'þ' to "th",
        'ß' to "ss", 'ħ' to "h", 'ŧ' to "t", 'ı' to "i", 'ĸ' to "k", 'ŋ' to "n",
    )

    /** Lowercase, compatibility-decompose, drop combining marks, fold the few non-decomposing Latin letters. */
    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val stripped = marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFKD), "")
        if (stripped.none { it in foldMap }) return stripped
        val sb = StringBuilder(stripped.length + 4)
        for (c in stripped) sb.append(foldMap[c] ?: c)
        return sb.toString()
    }

    /** The group a label belongs to: "A".."Z" or [OTHER]. Leading whitespace is ignored. */
    fun letterOf(label: String): String {
        val first = normalize(label.trimStart()).firstOrNull() ?: return OTHER
        return if (first in 'a'..'z') first.uppercaseChar().toString() else OTHER
    }

    fun collator(locale: Locale): Collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }

    /** Sorted by the locale's collation, ties broken by the raw label then [tieKey] so the order is stable. */
    fun <T> sort(items: List<T>, label: (T) -> String, tieKey: (T) -> String, collator: Collator): List<T> {
        val keyed = items.map { Triple(it, collator.getCollationKey(label(it)), label(it)) }
        return keyed.sortedWith { a, b ->
            val c = a.second.compareTo(b.second)
            if (c != 0) c else {
                val r = a.third.compareTo(b.third)
                if (r != 0) r else tieKey(a.first).compareTo(tieKey(b.first))
            }
        }.map { it.first }
    }

    /** Ordered non-empty groups: "#" first, then A-Z; each group sorted by [sort]. */
    fun <T> group(items: List<T>, label: (T) -> String, tieKey: (T) -> String, collator: Collator): List<Pair<String, List<T>>> {
        val byLetter = sort(items, label, tieKey, collator).groupBy { letterOf(label(it)) }
        return JUMP_LETTERS.mapNotNull { letter -> byLetter[letter]?.let { letter to it } }
    }

    /** Match rank for a normalized label against a normalized query: 0 name prefix, 1 word prefix, 2 anywhere, null no match. */
    fun rank(normalizedLabel: String, normalizedQuery: String): Int? {
        if (normalizedQuery.isEmpty()) return null
        val at = normalizedLabel.indexOf(normalizedQuery)
        if (at < 0) return null
        if (at == 0) return 0
        var i = at
        while (i > 0) {
            if (!normalizedLabel[i - 1].isLetterOrDigit()) return 1
            i = normalizedLabel.indexOf(normalizedQuery, i + 1)
        }
        return 2
    }

    /** Items matching [query], best rank first; within a rank the input order is kept (the caller passes sorted items). */
    fun <T> search(items: List<T>, normalizedLabel: (T) -> String, query: String): List<T> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        return items.mapNotNull { item -> rank(normalizedLabel(item), q)?.let { it to item } }
            .sortedBy { it.first }
            .map { it.second }
    }
}
