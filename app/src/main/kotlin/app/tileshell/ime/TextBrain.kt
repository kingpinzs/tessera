package app.tileshell.ime

/** What the strip is offered for a word: [items] best first; when [autoCorrect], items[0] replaces the word on space (R6 §2.2.6). */
data class Offer(val items: List<String>, val autoCorrect: Boolean)

/**
 * The keyboard's one door into the text engine (`app.tileshell.ime.engine`): dictionary suggestions and
 * autocorrect, Word Flow decoding, word learning, alternates and the space / shift rules. The controller
 * never reaches past it, so the engine's own API can change without the touch code noticing.
 */
interface TextBrain {
    /** Suggestions for [word] as typed. Never autocorrects when the field says no (password, URL, email, textNoSuggestions). */
    fun offer(word: String, previous: String?, field: FieldInfo): Offer

    /** Word Flow: ranked words for a finger path in key-grid phys coordinates. */
    fun decodeSwipe(path: List<Pair<Float, Float>>): List<String>

    /** Long-press alternates for a key's character, the plain character first. */
    fun alternates(ch: String, upper: Boolean): List<String>

    /** Decisions stand-in (5): should this space turn the previous one into ". "? */
    fun doubleSpacePeriod(textBefore: String, sinceLastSpaceMs: Long, field: FieldInfo): Boolean

    /** A word was committed. Learning counts it (never in a password field). */
    fun committed(word: String, field: FieldInfo)

    fun isKnown(word: String): Boolean
    fun isLearned(word: String): Boolean
    fun add(word: String)
    fun remove(word: String)

    /** [word] (lower case) in its dictionary casing: "i" → "I", "monday" → "Monday"; unknown words unchanged. */
    fun properCase(word: String): String
}
