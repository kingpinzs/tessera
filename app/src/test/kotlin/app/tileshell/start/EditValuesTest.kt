package app.tileshell.start

import app.tileshell.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every edit-mode value against the number the phase 02 Decisions quote from R6 (gate finding: the ~30
 * constants in [Edit] were transcriptions that nothing pinned, so a drift would have been silent).
 *
 * This does not claim the values are RIGHT — R6 and the H rows own that. It claims the code still says what
 * the doc says, so an accidental edit turns red instead of quietly changing the shell's motion.
 */
class EditValuesTest {

    @Test
    fun `entry values match R6 §1_1`() {
        assertEquals(783L, Edit.HOLD_MS)                 // §1.1.1, 783 ± 33 ms
        assertEquals(0.835f, Edit.OTHER_TILE_SCALE)      // §1.1.2, 0.835 ± 0.01
        assertEquals(0.90f, Edit.PITCH_SCALE)            // §1.1.3, 0.90 ± 0.01
        assertEquals(0.5f, Edit.FIXED_POINT_X)           // §1.1.3, the screen's horizontal centre
        assertTrue("§1.1.3 puts the fixed point at 46-49 % of the screen height", Edit.FIXED_POINT_Y in 0.46f..0.49f)
        assertEquals(67f, Edit.SCALE_HALF_MS)            // §1.1.8, 50 % by ≈67 ms
        assertTrue("the scale curve has to be driven past its 417 ms settle", Edit.SCALE_MS >= 417)
        assertEquals(550, Edit.DIM_MS)                   // §1.1.9, settles in 550 ± 50 ms
        assertEquals(listOf(0 to 0f, 83 to 0.5f, 350 to 0.9f, 550 to 1f), Edit.dimKeyframes)
    }

    @Test
    fun `exit values match R6 §1_5`() {
        assertEquals(150L, Edit.EXIT_DELAY_MS)           // §1.5.2, 150 ± 17 ms after touch-up
        assertEquals(200, Edit.EXIT_SCALE_MS)            // §1.5.3, 183-217 ms
        assertTrue(Edit.EXIT_SCALE_MS in 183..217)
        assertEquals(300, Edit.EXIT_DIM_MS)              // §1.5.3, settled ≈300 ms
        assertEquals(listOf(0 to 0f, 33 to 0.5f, 100 to 0.9f, 300 to 1f), Edit.exitDimKeyframes)
        assertEquals(185, Edit.SELECT_MS)                // §1.5.4 (H8), ≈170-200 ms
        assertTrue(Edit.SELECT_MS in 170..200)
    }

    @Test
    fun `disc values match R6 §1_2`() {
        assertEquals(31f, Edit.DISC_EPX)                 // §1.2.1, 31 ± 1.5 epx
        assertEquals(16f, Edit.UNPIN_GLYPH_W_EPX)        // §1.2.5, the pin is 16 × 15 epx
        assertEquals(15f, Edit.UNPIN_GLYPH_H_EPX)
        assertEquals(12f, Edit.RESIZE_GLYPH_EPX)         // §1.2.5, the arrow is 12 × 12 epx
    }

    @Test
    fun `drag, dwell and resize values match the Decisions`() {
        assertEquals(2000L, Edit.DWELL_MS)               // §1.3.3 approximation (H20)
        assertEquals(300, Edit.REFLOW_MS)                // §1.3.2 (H4), ≈300 ms
        assertEquals(500, Edit.RESIZE_SHRINK_MS)         // §1.4.1 (H6), ≈0.5 s
        assertEquals(170, Edit.RESIZE_GROW_MS)           // §1.4.2, grows in ≈170 ms
        assertEquals(270, Edit.RESIZE_BLANK_MS)          // §1.4.2, blank ≈270 ms
        assertEquals(170, Edit.RESIZE_FADE_MS)           // §1.4.2, fades in over ≈170 ms
    }

    @Test
    fun `folder values match R6 §1_6 and §1_7`() {
        assertEquals(230, Edit.FOLDER_CREATE_MS)         // §1.6.2 (H11)
        assertEquals(375, Edit.FOLDER_EXPAND_MS)         // §1.6.6 (H15), ≈350-400 ms
        assertTrue(Edit.FOLDER_EXPAND_MS in 350..400)
        assertEquals(133, Edit.FOLDER_COLLAPSE_MS)       // §1.6.7 (H16)
        assertEquals(100, Edit.FOLDER_FACE_RETURN_MS)
        assertEquals(370, Edit.FOLDER_SCROLL_BACK_MS)
        assertEquals(0.20f, Edit.MINI_TILE)              // §1.6.3 (H12)
        assertEquals(0.31f, Edit.MINI_PITCH)
        assertEquals(0.07f, Edit.MINI_INSET)
        assertEquals(3, Edit.MINI_COLUMNS)
        assertEquals(0.20f, Edit.BAND_TOP_RULE)          // §1.6.5 (H14)
        assertEquals(0.22f, Edit.BAND_MEMBERS_TOP)
        assertEquals(0.19f, Edit.BAND_BOTTOM_RULE)
        assertEquals(1f, Edit.BAND_RULE_EPX)
        assertEquals(0.27f, Edit.NAME_BOX_HEIGHT)        // §1.7.2 (H18)
    }

    @Test
    fun `the dimming colours are the Decisions' formulas`() {
        // §1.1.5 dark: tile pixels × 0.53, i.e. black at 47 %.
        val dark = Edit.tileDim(ThemeMode.DARK)
        assertEquals(0f, dark.red)
        assertEquals(0.47f, dark.alpha, 1f / 255f)
        // §1.1.5 dark wallpaper: × 0.25-0.30, i.e. black at 70-75 %.
        val darkWall = Edit.wallpaperDim(ThemeMode.DARK)
        assertTrue("wallpaper factor ${1f - darkWall.alpha} outside 0.25..0.30", (1f - darkWall.alpha) in 0.25f..0.30f)
        // §1.1.6 light: c' ≈ 0.63c + 62. As one overlay that is grey 168 at 37 %.
        val light = Edit.tileDim(ThemeMode.LIGHT)
        assertEquals(0.37f, light.alpha, 1f / 255f)
        val slope = 1f - light.alpha
        val intercept = light.alpha * light.red * 255f
        assertEquals(0.63f, slope, 0.01f)
        assertEquals(62f, intercept, 2f)
    }

    @Test
    fun `the entry curve holds both of R6 §1_1_8's numbers`() {
        // 50 % of the move by ≈67 ms...
        assertEquals(0.5f, Edit.easeOutProgress(Edit.SCALE_HALF_MS, Edit.SCALE_MS, Edit.SCALE_HALF_MS), 0.01f)
        // ...and still visibly moving at 417 ms: more than one pixel of a ~53 px swing left to travel at 367 ms
        // (the bottom of the 417 ± 50 tolerance), and under one by 467 ms (the top).
        val swingPx = 53f
        val remainingAt = { ms: Float -> (1f - Edit.easeOutProgress(ms, Edit.SCALE_MS, Edit.SCALE_HALF_MS)) * swingPx }
        assertTrue("settled too early: ${remainingAt(367f)} px left at 367 ms", remainingAt(367f) > 0.5f)
        assertTrue("still moving too late: ${remainingAt(467f)} px left at 467 ms", remainingAt(467f) < 1.5f)
    }
}
