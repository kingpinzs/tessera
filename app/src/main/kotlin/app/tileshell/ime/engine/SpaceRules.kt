package app.tileshell.ime.engine

/**
 * What the space key does to the text, and when a sentence starts.
 *
 * Double-space period (phase 05 Decisions stand-in (5), H23): "a second space within 1100 ms of the
 * first, directly after a letter, replaces the first space with '. ' (never after a digit, a period,
 * or in a URL or email field)". "Within" is inclusive: 1100 ms is the last tick that still counts.
 *
 * Pure functions over the text before the caret and the caller's clock, so the timing rule is tested
 * on both sides of its boundary without a device.
 */
object SpaceRules {

    const val DOUBLE_SPACE_WINDOW_MS = 1100L

    /**
     * An edit at the caret: delete [deleteBefore] characters before it, then insert [insert]. The
     * plain space is the edit that deletes nothing and inserts " ".
     */
    data class Edit(val deleteBefore: Int, val insert: String)

    val PLAIN_SPACE = Edit(0, " ")
    val PERIOD = Edit(1, ". ")

    /**
     * The edit for a space pressed with [before] in front of the caret, [msSincePreviousSpace] after
     * the previous space key (Long.MAX_VALUE when there was none), in a field of kind [field].
     */
    fun space(before: CharSequence, msSincePreviousSpace: Long, field: FieldKind): Edit {
        if (!field.doubleSpacePeriod) return PLAIN_SPACE
        if (msSincePreviousSpace > DOUBLE_SPACE_WINDOW_MS || msSincePreviousSpace < 0) return PLAIN_SPACE
        // "directly after a letter": the text must end in exactly one space that follows a letter.
        // Ending in "1 " (a digit), ". " (a period), ", " or "  " gets a plain space.
        if (before.length < 2 || before[before.length - 1] != ' ') return PLAIN_SPACE
        if (!Character.isLetter(before[before.length - 2])) return PLAIN_SPACE
        return PERIOD
    }

    /**
     * Whether the next letter starts a sentence, for auto-capitalisation: at the start of the field,
     * on a new line, or after ". ", "! " or "? " — only when the field asks for sentence caps
     * (`textCapSentences`), which is [sentenceCaps].
     */
    fun startsSentence(before: CharSequence, sentenceCaps: Boolean): Boolean {
        if (!sentenceCaps) return false
        var i = before.length
        var sawSpace = false
        while (i > 0 && before[i - 1] == ' ') {
            i--
            sawSpace = true
        }
        if (i == 0) return true
        val c = before[i - 1]
        if (c == '\n') return true
        return sawSpace && (c == '.' || c == '!' || c == '?')
    }
}
