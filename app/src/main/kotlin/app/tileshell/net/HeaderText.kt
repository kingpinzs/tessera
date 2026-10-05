package app.tileshell.net

/**
 * What may go into a request header (phase 17 trust review, B-1). TRUST-TOUCHING.
 *
 * `HttpURLConnection.setRequestProperty` throws an IllegalArgumentException whose MESSAGE QUOTES THE WHOLE VALUE when
 * the value holds a character a header cannot carry — so a credential with a stray carriage return would be printed by
 * whatever reports the exception. These rules keep such a value from ever being stored or sent; none of them logs,
 * formats or throws a value. Pure, so each is unit-tested.
 */
object HeaderText {
    /** What a pasted secret may be: longer than this is not a key. */
    const val MAX_SECRET = 2000

    const val TEXT_BAD_SECRET = "That isn't a key: it holds a space, a line break or a character a key can't have. Nothing was saved."

    /**
     * A secret as the user pasted it, ready to store: the ends trimmed, and then printable ASCII only with no space
     * inside (0x21–0x7e). Null — store nothing — for an empty value and for one holding anything else: an interior
     * space, tab, CR, LF, another control character, 0x7f, or a character outside ASCII.
     */
    fun pastedSecret(typed: String): String? =
        typed.trim().takeIf { it.isNotEmpty() && it.length <= MAX_SECRET && it.all { c -> c in '!'..'~' } }

    private val SAFE_TOKEN = Regex("[A-Za-z0-9._-]{1,1024}")

    /** A token a server handed back, safe in a header and in a query: letters, digits, `.`, `_`, `-`. */
    fun isSafeToken(value: String): Boolean = SAFE_TOKEN.matches(value)

    /** True when [value] can be a header's value as it is: printable ASCII and the space, nothing else. */
    fun isHeaderSafe(value: String): Boolean = value.isNotEmpty() && value.all { it in ' '..'~' }

    /**
     * [value] as the inside of a quoted header field: every control character (below 0x20, and 0x7f), every character
     * outside ASCII, the quote and the backslash are dropped — nothing is left that could end the field, start
     * another, or make the platform refuse the header.
     */
    fun quoted(value: String): String = buildString(value.length) {
        for (c in value) if (c in ' '..'~' && c != '"' && c != '\\') append(c)
    }
}
