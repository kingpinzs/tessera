package app.tileshell.ime

/**
 * Phase 05's key grid, in R6 §2.1's own unit: physical pixels on a 1440-px-wide panel ("phys").
 *
 * R6's unit finding is the reason this file exists at all: on the two governing captures (14393 at
 * 400 %, 15063 at 350 %) the keys, their labels, the press popup and the cursor dot agree to ±3 phys
 * while their epx values differ by 12–15 %. So the key grid is laid out in phys and scaled by
 * display width / 1440 (Decisions "Key grid"), and ONLY the suggestion strip is in epx (§2.2.1).
 *
 * Coordinates here: x from the panel's left edge; y DOWN from the top of key row 1. Everything is a
 * plain number so the JVM tests can check each one against its R6 row.
 */
object KeyGrid {
    /** The reference panel width every phys value is measured on (R6 §2.0). */
    const val PANEL = 1440f

    /** R6 §2.1.1: column pitch = panel width / 10, HIGH. */
    const val PITCH = 144f

    /** R6 §2.1.10: key height 202 ± 3, HIGH. */
    const val KEY_H = 202f

    /** R6 §2.1.11: row pitch 217.5 ± 1.5, HIGH (so the vertical gap is 15.5, inside §2.1.11's 15 ± 3). */
    const val ROW_PITCH = 217.5f

    /** R6 §2.1.12: row 4's bottom to the nav bar's top, 7 ± 4, HIGH. */
    const val BOTTOM_MARGIN = 7f

    /**
     * Row 1 top to the nav bar's top. Built from the three values above (3 × 217.5 + 202 + 7 = 861.5)
     * rather than taken from R6 §2.1.13's own 865 ± 5, because every row position is measured directly
     * and the block total is their sum; 861.5 sits inside §2.1.13's tolerance, which E3 checks.
     */
    const val BLOCK_H = 3 * ROW_PITCH + KEY_H + BOTTOM_MARGIN

    /** R6 §2.1.2–2.1.4: row 1 keys 130 ± 3 wide, first key 5 ± 4 from the left edge (the split is MEDIUM). */
    const val ROW1_LEFT = 5f
    const val ROW1_KEY_W = 130f

    /** R6 §2.1.4: right edge margin 9 ± 4 (MEDIUM split; the sum with the left margin is HIGH). */
    const val RIGHT_MARGIN = 9f

    /** R6 §2.1.5–2.1.6: row 2 keys 128 ± 3 wide, inset 77.5 ± 3 from the left edge. */
    const val ROW2_LEFT = 77.5f
    const val ROW2_KEY_W = 128f

    /** The top of row [row] (0-based) below row 1's top. */
    fun rowTop(row: Int): Float = row * ROW_PITCH

    /** Row 1's column [i]: left edge. */
    fun row1Left(i: Int): Float = ROW1_LEFT + PITCH * i

    /**
     * Row 2's column [c]: left edge. Rows 3 and 4 hang off these columns too — R6 §2.1.8 measured z…m
     * on row 2's s…k columns to a hundredth of a video pixel — so every lower-row key edge is either a
     * row-2 column edge or one of the two panel margins.
     */
    fun col2Left(c: Int): Float = ROW2_LEFT + PITCH * c
    fun col2Right(c: Int): Float = col2Left(c) + ROW2_KEY_W

    /** The panel's inner right edge (1440 − 9). */
    val rightEdge: Float get() = PANEL - RIGHT_MARGIN

    // ---- the cursor-control dot (R6 §2.5, HIGH) ---------------------------------------------------

    /** §2.5.2: accent centre dot 21 ± 3 across. */
    const val DOT_CORE_D = 21f

    /** §2.5.3: key-grey disc 56 ± 4. */
    const val DOT_DISC_D = 56f

    /** §2.5.3: panel-colour ring, 87 ± 4 outer diameter, cutting into the four keys around it. */
    const val DOT_RING_D = 87f

    /**
     * §2.5.4: the dot sits where three gaps cross — row 3/4, z/x and emoji/comma — "exactly one row
     * pitch up and 2.5 pitches minus half a gap across". Computed from the grid (the midpoints of the
     * gaps it is centred on) so the ring cuts all four keys evenly: x 357.5 against R6's 358 ± 3.
     */
    val dotRightHandedX: Float get() = (col2Right(1) + col2Left(2)) / 2f

    /**
     * Decisions "Cursor-controller handedness": the left-handed mirror sits on the n/m, space/period and
     * row 3/4 gaps, 1077.5 ± 3 from the left (approximation, H15). A plain 1440 − 358 would miss the gap
     * because the grid's margins are asymmetric, so this is the gap midpoint, not a reflection.
     */
    val dotLeftHandedX: Float get() = (col2Right(6) + col2Left(7)) / 2f

    /** Row 3/4 gap midpoint, measured down from row 1's top (R6: 218 ± 3 above the nav bar's top). */
    val dotY: Float get() = (rowTop(2) + KEY_H + rowTop(3)) / 2f

    // ---- the key press popup (R6 §2.3, HIGH) -------------------------------------------------------

    /** §2.3.2: 173 × 233 ± 4. */
    const val POPUP_W = 173f
    const val POPUP_H = 233f

    /** §2.3.3: the popup's bottom edge sits 7 ± 4 above the pressed key's top. */
    const val POPUP_LIFT = 7f

    // ---- labels (R6 §2.1.14–2.1.16) ---------------------------------------------------------------

    /** §2.1.14: lowercase x-height 41 ± 2. */
    const val LABEL_X_HEIGHT = 41f

    /** §2.1.14: capitals 48 ± 3 (drawn smaller than a lowercase-sized capital would be). */
    const val LABEL_CAP_HEIGHT = 48f

    /** §2.1.15: the "&123" label's digits are 40 ± 2 tall. */
    const val SYMBOLS_LABEL_DIGIT_H = 40f

    /** §2.1.15: and the "&123" text is 112 ± 3 wide. */
    const val SYMBOLS_LABEL_W = 112f

    /** §2.3.4 (MEDIUM): the popup glyph's x-height 59.5 ± 5. */
    const val POPUP_X_HEIGHT = 59.5f

    /** §2.8.3 (MEDIUM): ".com"'s x-height 28, smaller than a letter label. */
    const val DOTCOM_X_HEIGHT = 28f

    /** §2.1.16: space-bar grip, two lines 59 × 18 ± 5 overall, its top 20 ± 4 below the key's top. */
    const val GRIP_W = 59f
    const val GRIP_H = 18f
    const val GRIP_TOP = 20f

    /** §2.1.17 (MEDIUM): &123's three hold dots — pitch 19, first 20 from the key's left, 26 below its top. */
    const val HOLD_DOT_PITCH = 19f
    const val HOLD_DOT_LEFT = 20f
    const val HOLD_DOT_TOP = 26f
}
