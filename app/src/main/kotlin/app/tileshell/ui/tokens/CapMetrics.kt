package app.tileshell.ui.tokens

/**
 * R6 and R7 measure text by its **cap top** — the top of a capital letter — not by the top of the text
 * layout box, which is where Compose places text. The two differ by the font's own metrics, so a value
 * like "cap top 94.6 epx from the screen top" has to be converted before it becomes a layout offset.
 *
 * Selawik (the branding module's stand-in for Segoe UI, Q8), read from the shipped TTFs:
 *   unitsPerEm 2048 · capHeight 1434 · hhea ascent 2027 · hhea descent −431 · xHeight 1024
 * All five weights carry the same values, so one set covers the ramp.
 *
 * That a 20-epx style has a 14.0-epx cap is the check that this mapping is the right one: R6 §3.4.2
 * measured the reminder card's title at "cap 14 epx, 20-epx class", and 0.70020 × 20 = 14.004.
 */
object CapMetrics {
    const val UNITS_PER_EM = 2048f
    const val CAP_HEIGHT = 1434f
    const val ASCENT = 2027f
    const val DESCENT = 431f

    /** 0.70020 — a capital's height as a fraction of the font size. */
    const val CAP_RATIO = CAP_HEIGHT / UNITS_PER_EM

    /** 0.98975 — the baseline's distance below the ascent line. */
    const val ASCENT_RATIO = ASCENT / UNITS_PER_EM

    /** 1.20020 — the font's natural line height. */
    const val NATURAL_LINE_RATIO = (ASCENT + DESCENT) / UNITS_PER_EM

    /** A capital's height in epx at [fontSizeEpx]. */
    fun capHeight(fontSizeEpx: Float): Float = fontSizeEpx * CAP_RATIO

    /**
     * How far the cap top sits below the top of the text's layout box: the ascent less the cap height, whatever the
     * line height. Compose's default LineHeightStyle trims the extra a declared line height adds above the first line
     * (and below the last), so the first baseline sits the font's own ascent below the box top. On the phone at
     * 25.4 epx with a 32-epx line the box is the natural 30.3 epx tall and the baseline 25.0 epx into it
     * (qa/phase-15/E10-run7/DEFECT.md). The model this replaces spread that extra over ascent and descent, which put every
     * text placed through it 0.7-2.6 epx high, by style.
     */
    fun capTopWithinBox(fontSizeEpx: Float): Float = fontSizeEpx * ASCENT_RATIO - capHeight(fontSizeEpx)

    /**
     * The top padding that puts a run of text's cap top exactly [capTopEpx] below the container's top.
     * Never negative: a value that would need the box above the container is clamped and the caller's
     * own comment has to say so.
     */
    fun topPaddingForCapTop(capTopEpx: Float, fontSizeEpx: Float): Float =
        (capTopEpx - capTopWithinBox(fontSizeEpx)).coerceAtLeast(0f)

    /** The line height [ShellType] gives a size: 125 % of it, rounded to the nearest 4 epx (R1 §5.1). */
    fun lineHeightFor(fontSizeEpx: Float): Float =
        (Math.round((fontSizeEpx * 1.25f) / 4f) * 4).toFloat()
}
