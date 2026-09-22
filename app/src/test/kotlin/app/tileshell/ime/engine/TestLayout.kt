package app.tileshell.ime.engine

/**
 * The W10M QWERTY letter rows on a 1440-px-wide panel, from R6 §2.1: column pitch 144 (§2.1.1), row 1
 * keys 130 wide with a 5-px left margin (§2.1.2, §2.1.4), row 2 keys 128 wide inset 77.5 (§2.1.5,
 * §2.1.6), row 3 letters on row 2's s…k columns (§2.1.8), key height 202 at a row pitch of 217.5
 * (§2.1.10, §2.1.11). Row 1's top is y = 0 here; the strip above it is the IME's business.
 *
 * Test data only: the engine takes whatever centres the IME gives it and assumes none of these numbers.
 */
object TestLayout {

    const val PITCH_X = 144f
    const val PITCH_Y = 217.5f

    val w10m: LetterLayout by lazy {
        val centres = HashMap<Char, KeyPoint>()
        "qwertyuiop".forEachIndexed { i, c -> centres[c] = KeyPoint(5f + 65f + PITCH_X * i, 101f) }
        "asdfghjkl".forEachIndexed { i, c -> centres[c] = KeyPoint(77.5f + 64f + PITCH_X * i, 101f + PITCH_Y) }
        "zxcvbnm".forEachIndexed { i, c -> centres[c] = KeyPoint(77.5f + 64f + PITCH_X * (i + 1), 101f + 2 * PITCH_Y) }
        LetterLayout(centres, PITCH_X, PITCH_Y)
    }

    fun centre(c: Char): KeyPoint = w10m.centres.getValue(c)
}
