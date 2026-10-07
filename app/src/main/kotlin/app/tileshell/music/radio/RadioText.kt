package app.tileshell.music.radio

import java.util.Locale

/**
 * Foreign text made fit to show (phase 20, r3 D9; a trust rule). A station's name, its tags and the StreamTitle a
 * stream sends come from a community directory and from whoever runs the stream, and they reach the tile, the
 * notification, a diagnostics line and Tess's reply. So every one passes [shown] first: `FilesIntents.lineSafe`'s rule
 * — no control, format, line- or paragraph-separator or unassigned character, so a newline cannot start a forged
 * `[music]` line — read by CODE POINT, so the bidi overrides and isolates and the format characters above the BMP
 * (the tag block) go too, and a lone surrogate with them. Pure (`RadioTextTest`).
 */
object RadioText {
    /** The longest station name, tag list or country name shown. */
    const val NAME_MAX = 80

    /** The longest StreamTitle shown. */
    const val TITLE_MAX = 120

    /** [raw] as it may be shown: the characters above removed, the ends trimmed, cut at [max] characters. */
    fun shown(raw: String?, max: Int): String {
        val text = raw.orEmpty()
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            i += width
            val keep = when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                Character.UNASSIGNED, Character.SURROGATE,
                -> false
                else -> true
            }
            if (!keep) continue
            // Leading space is never kept, so the cut below counts what is shown.
            if (out.isEmpty() && isSpace(cp)) continue
            if (out.length + width > max) break
            out.appendCodePoint(cp)
        }
        return out.toString().trimEnd { isSpace(it.code) }
    }

    /** White space of either kind: the breaking kinds and the no-break spaces. */
    private fun isSpace(cp: Int): Boolean = Character.isWhitespace(cp) || Character.isSpaceChar(cp)

    /** How a name, a tag or a query is compared: [shown] text in lower case with every run of white space one space. */
    fun key(text: String?): String =
        shown(text, 4 * TITLE_MAX).lowercase(Locale.ROOT).split { isSpace(it.code) }.filter { it.isNotEmpty() }.joinToString(" ")

    private inline fun String.split(isSeparator: (Char) -> Boolean): List<String> {
        val parts = ArrayList<String>()
        var start = 0
        for (i in indices) if (isSeparator(this[i])) { parts += substring(start, i); start = i + 1 }
        parts += substring(start)
        return parts
    }
}
