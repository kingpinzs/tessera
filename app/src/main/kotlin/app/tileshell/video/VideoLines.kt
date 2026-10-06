package app.tileshell.video

/**
 * THE one cleaner for text that is not the shell's own and reaches a `[video]` diagnostics line or a session title
 * (trust review C-L1, C-L7, B2-L5). TRUST-TOUCHING: a line is one line and is bounded whatever was sent. A caller's
 * scheme, host and last path segment; a provider's display name; `EXTRA_TITLE`; a film's title and the typed query from
 * the catalogue; a "Watch on" address; the media server's name as its file gives it — all pass through [text] before
 * they are put into a line. Pure, and free of Android types.
 */
object VideoLines {
    /** The longest piece of foreign text a line carries. */
    const val MAX = 80

    /** The longest address a line carries (a "Watch on" link, a stream's address with its query removed). */
    const val ADDRESS_MAX = 300

    /**
     * [text] fit for a line: every control character is dropped — C0 and C1, DEL, and the Unicode line and paragraph
     * separators, so a line break in a title or a `%0A` in an address cannot start a forged line — and what is left is
     * cut to [max] characters.
     */
    fun text(text: String?, max: Int = MAX): String = buildString {
        for (c in text.orEmpty()) {
            if (length >= max) break
            val control = c < ' ' || c in '\u007f'..'\u009f' || c == ' ' || c == ' '
            if (!control) append(c)
        }
    }
}
