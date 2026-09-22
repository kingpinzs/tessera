package app.tileshell.cortana.ui

import androidx.compose.ui.graphics.Color
import app.tileshell.ui.tokens.CapMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every value in [CortanaUi] against the number the phase 03 Decisions quote from R7, in the style of
 * phase 02's `EditValuesTest`.
 *
 * This does not claim the values are RIGHT — R7 and the H rows own that. It claims the code still says
 * what the doc says, so an accidental edit turns red instead of quietly moving the ≡ pane by an epx.
 */
class CortanaValuesTest {

    /** ARGB without going through `toArgb`, which would drag a colour-space conversion into a JVM test. */
    private fun Color.argb(): Long = ((value shr 32).toLong()) and 0xFFFFFFFFL

    private fun rgb(r: Int, g: Int, b: Int): Long = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

    @Test
    fun `pane values match R7 §3_1`() {
        assertEquals(256f, CortanaUi.PANE_WIDTH_EPX)                         // §3.1.5, 256 ± 1 epx
        assertEquals(rgb(14, 19, 13), CortanaUi.PAGE_BG.argb())              // §3.1.6 / §3.5.1
        assertEquals(52f, CortanaUi.PANE_HEADER_EPX)                         // §3.1.7, 52 ± 1.5 epx
        assertEquals(48f, CortanaUi.MENU_BUTTON_EPX)                         // §3.1.7, 48 × 48 epx
        assertEquals(4f, CortanaUi.MENU_BUTTON_TOP_EPX)                      // §3.1.7, top 4 epx down
        assertEquals(16f, CortanaUi.MENU_GLYPH_LEFT_EPX)                     // §3.1.7, glyph x 16-32
        assertEquals(16f, CortanaUi.MENU_GLYPH_WIDTH_EPX)
        assertTrue("§3.1.7 bars at a 4.2-4.4 epx pitch", CortanaUi.MENU_GLYPH_BAR_PITCH_EPX in 4.2f..4.4f)
        assertEquals(1f, CortanaUi.MENU_GLYPH_BAR_EPX)
        assertEquals(49f, CortanaUi.PANE_TITLE_LEFT_EPX)                     // §3.1.7, "CORTANA" x 49 ± 1
        assertEquals(48f, CortanaUi.PANE_ITEM_PITCH_EPX)                     // §3.1.8, 48-epx pitch
        assertEquals(48f, CortanaUi.PANE_ITEM_FILL_EPX)                      // §3.1.8, fill 48 epx tall
        assertEquals(listOf(72f, 119.5f), CortanaUi.PANE_ITEM_CAP_TOPS_EPX)  // §3.1.8 / E15
        assertEquals(49f, CortanaUi.PANE_LABEL_LEFT_EPX)                     // §3.1.8, labels x 49 ± 1
        assertTrue("§3.1.8 icon left 17-18 epx", CortanaUi.PANE_ICON_LEFT_EPX in 17f..18f)
        assertEquals(16f, CortanaUi.PANE_ICON_EPX)                           // §3.1.8, ≈16-epx icons
        assertEquals(172f, CortanaUi.PANE_SETTINGS_CAP_TOP_ABOVE_BOTTOM_EPX) // §3.1.9, 172 ± 1 above the bottom
        assertEquals(rgb(63, 68, 64), CortanaUi.PRESSED_FILL.argb())         // §3.1.6 / §3.5.7
        assertEquals(Color.White, CortanaUi.SELECTED_TEXT)                   // §3.1.6
        assertEquals(250, CortanaUi.PANE_SLIDE_MS)                           // §3.1.10, 250 ± 17 ms
        assertEquals(350, CortanaUi.PANE_SELECT_TO_BLACK_MS)                 // §3.1.12, 350 ± 17 ms
        assertEquals(334L, CortanaUi.PANE_PRESS_TO_ACCENT_MS)                // §3.1.12, N2's fill frame
    }

    @Test
    fun `the Feedback slot is left empty exactly 48 epx below Settings`() {
        // A11 / H23 and E15: no Feedback item, and no node where its cap top would be.
        assertEquals(
            CortanaUi.PANE_SETTINGS_CAP_TOP_ABOVE_BOTTOM_EPX - CortanaUi.PANE_ITEM_PITCH_EPX,
            CortanaUi.PANE_EMPTY_SLOT_CAP_TOP_ABOVE_BOTTOM_EPX,
        )
        assertEquals(124f, CortanaUi.PANE_EMPTY_SLOT_CAP_TOP_ABOVE_BOTTOM_EPX)
    }

    @Test
    fun `transition values match R7 §3_2`() {
        assertTrue("§3.2.1, black 283-300 ms", CortanaUi.BLACK_MS in 283..300)
        assertTrue("§3.2.2, fade 200-317 ms", CortanaUi.FADE_MS in 200..317)
        assertTrue("§3.2.2, first frame 40-60 %", CortanaUi.FADE_FIRST_ALPHA in 0.4f..0.6f)
        assertEquals(550L, CortanaUi.ROW_TAP_TO_PAGE_MS)                     // §3.2.6, 550 ± 17 ms
    }

    @Test
    fun `header values match R7 §3_3_2`() {
        assertEquals(60f, CortanaUi.HEADER_TITLE_LEFT_EPX)                   // §3.3.2, x 60 ± 1
        assertEquals(14.1f, CortanaUi.HEADER_TITLE_CAP_EPX)                  // §3.3.2, cap 14.1 ± 0.6
        assertEquals(26.3f, CortanaUi.HEADER_TITLE_CAP_CENTRE_EPX)           // §3.3.2, cap centre 26.3 ± 0.5
    }

    /** The title is drawn in the 20-epx ramp step; Selawik's cap ratio has to land on §3.3.2's 14.1. */
    @Test
    fun `the 20-epx type step gives the measured title cap height`() {
        assertEquals(CortanaUi.HEADER_TITLE_CAP_EPX, CapMetrics.capHeight(20f), 0.6f)
        // §3.7.3's reminder-text field: "cap 16.5 epx (≈24 epx type)".
        assertEquals(CortanaUi.TEXT_FIELD_CAP_EPX, CapMetrics.capHeight(24f), 0.6f)
    }

    @Test
    fun `list values match R7 §3_5`() {
        assertEquals(11.9f, CortanaUi.GROUP_HEADER_LEFT_EPX)                 // §3.5.2
        assertEquals(rgb(231, 236, 230), CortanaUi.GROUP_HEADER_COLOR.argb())// §3.5.2
        assertEquals(67.4f, CortanaUi.FIRST_HEADER_CAP_TOP_EPX)              // §3.5.2, "Today" cap top
        assertEquals(26.3f, CortanaUi.HEADER_CAP_TO_ROW_TOP_EPX)             // §3.5.2, 26.3 ± 0.3
        assertEquals(13.9f, CortanaUi.ROW_BOTTOM_TO_HEADER_CAP_EPX)          // §3.5.2
        assertEquals(60f, CortanaUi.ROW_ONE_LINE_EPX)                        // §3.5.3, 60.0 ± 1
        assertEquals(32f, CortanaUi.ROW_ICON_BOX_EPX)                        // §3.5.3
        assertEquals(14f, CortanaUi.ROW_ICON_TOP_EPX)                        // §3.5.3
        assertEquals(55.8f, CortanaUi.ROW_TITLE_LEFT_EPX)                    // §3.5.3
        assertEquals(15.8f, CortanaUi.ROW_TITLE_CAP_TOP_EPX)                 // §3.5.3
        assertEquals(rgb(232, 237, 231), CortanaUi.ROW_TITLE_COLOR.argb())   // §3.5.3
        assertEquals(31.4f, CortanaUi.CLOCK_ICON_W_EPX)                      // §3.5.3, clock 31.4 × 31.0
        assertEquals(31f, CortanaUi.CLOCK_ICON_H_EPX)
        assertEquals(11.2f, CortanaUi.CLOCK_ICON_LEFT_EPX)
        assertEquals(22f, CortanaUi.BULB_ICON_W_EPX)                         // §3.5.3, bulb 22 × 32
        assertEquals(32f, CortanaUi.BULB_ICON_H_EPX)
        assertEquals(15.2f, CortanaUi.BULB_ICON_LEFT_EPX)
        assertEquals(55.9f, CortanaUi.SUBLINE_LEFT_EPX)                      // §3.5.4
        assertEquals(20.1f, CortanaUi.SUBLINE_CAP_PITCH_EPX)                 // §3.5.4, 368.7 → 388.8
        assertEquals(rgb(168, 170, 167), CortanaUi.SUBLINE_COLOR.argb())     // §3.5.4
        assertEquals(20.1f, CortanaUi.PHOTO_BELOW_TITLE_CAP_EPX)             // §3.5.5
        assertEquals(55.4f, CortanaUi.PHOTO_LEFT_EPX)                        // §3.5.5
        assertEquals(12.4f, CortanaUi.PHOTO_RIGHT_MARGIN_EPX)                // §3.5.5
        assertEquals(1.97f, CortanaUi.PHOTO_ASPECT)                          // §3.5.5, 292.6 × 148.6
        assertEquals(5.4f, CortanaUi.PHOTO_TIME_CAP_BELOW_PHOTO_EPX)         // §3.5.5
        assertEquals(rgb(158, 160, 157), CortanaUi.PHOTO_TIME_COLOR.argb())  // §3.5.5
        assertEquals(48.2f, CortanaUi.APPBAR_EPX)                            // §3.5.8, 48.2 ± 1
        assertEquals(rgb(22, 27, 21), CortanaUi.APPBAR_FILL.argb())          // §3.5.8
        assertEquals(68f, CortanaUi.APPBAR_BUTTON_PITCH_EPX)                 // §3.5.8
        assertEquals(48f, CortanaUi.APPBAR_MORE_EPX)                         // §3.5.8, "…" 48 epx flush right
        assertEquals("Select + to add a new reminder", CortanaUi.EMPTY_TEXT) // §3.5.9
        assertEquals(11.7f, CortanaUi.EMPTY_LEFT_EPX)                        // §3.5.9
        assertEquals(70.4f, CortanaUi.EMPTY_CAP_TOP_EPX)                     // §3.5.9
        assertEquals(333L, CortanaUi.DELETE_REMOVE_MS)                       // §3.5.10, 333 ± 33 ms
    }

    /**
     * §3.5.8 puts the "…" dots' centre 24.7 epx from the right edge; a 48-epx button flush right
     * centres them at 24.0, i.e. 0.7 epx out — inside R7's own ±1.1-epx single-edge tolerance.
     */
    @Test
    fun `the more button sits flush right with its dots 24 epx in`() {
        assertEquals(24.7f, CortanaUi.APPBAR_MORE_EPX / 2f, 1.1f)
    }

    @Test
    fun `menu values match R7 §3_6`() {
        assertEquals(243.3f, CortanaUi.MENU_W_EPX)                           // §3.6.2, outer 243.3 × 107.0
        assertEquals(107f, CortanaUi.MENU_H_EPX)
        assertEquals(1f, CortanaUi.MENU_BORDER_EPX)                          // §3.6.2
        assertEquals(rgb(71, 76, 70), CortanaUi.MENU_BORDER_COLOR.argb())    // §3.6.2
        assertEquals(rgb(40, 40, 40), CortanaUi.MENU_FILL.argb())            // §3.6.2
        assertEquals(44f, CortanaUi.MENU_ITEM_PITCH_EPX)                     // §3.6.2
        assertEquals(11f, CortanaUi.MENU_TEXT_INSET_EPX)                     // §3.6.2, ≈11 epx
        assertEquals(23.5f, CortanaUi.MENU_BOTTOM_ABOVE_TOUCH_EPX)           // §3.6.2
        assertEquals(rgb(79, 84, 80), CortanaUi.MENU_PRESSED_FILL.argb())    // §3.6.3
        assertEquals(233, CortanaUi.MENU_GROW_MS)                            // §3.6.4, ≈233 ms
        assertEquals(0.5f, CortanaUi.MENU_FIRST_FRAME_HEIGHT)                // §3.6.4, first frame at half height
        assertEquals(6.4f, CortanaUi.MENU_TEXT_RISE_EPX)                     // §3.6.4
        assertEquals(300L, CortanaUi.LONG_PRESS_FILL_MS)                     // §3.6.5
        assertEquals(700L, CortanaUi.LONG_PRESS_MENU_MS)                     // §3.6.5
        assertEquals(83L, CortanaUi.MENU_CHOICE_HOLD_MS)                     // §3.6.6, 5 frames
        assertTrue("§3.6.6, fades out in 67-83 ms", CortanaUi.MENU_FADE_OUT_MS in 67..83)
    }

    /** §3.6.2's padding and its outer height disagree by 1 epx; the outer height is what E15 measures. */
    @Test
    fun `the menu's padding is what its measured outer height leaves`() {
        val derived = (CortanaUi.MENU_H_EPX - 2 * CortanaUi.MENU_BORDER_EPX - 2 * CortanaUi.MENU_ITEM_PITCH_EPX) / 2f
        assertEquals(derived, CortanaUi.MENU_PAD_EPX, 1e-4f)
        // §3.6.2 also saw the pressed item sitting 8.6 epx above the bottom edge, which this matches.
        assertEquals(8.6f, CortanaUi.MENU_PAD_EPX, 0.2f)
    }

    @Test
    fun `reminder page values match R7 §3_7`() {
        assertEquals(20.6f, CortanaUi.CHECKBOX_EPX)                          // §3.7.2, 20.6 ± 1
        assertEquals(2f, CortanaUi.CHECKBOX_BORDER_EPX)                      // §3.7.2, ≈2-epx border
        assertEquals(11.7f, CortanaUi.CHECKBOX_LEFT_EPX)                     // §3.7.2
        assertEquals(57.2f, CortanaUi.CHECKBOX_TOP_EPX)                      // §3.7.2
        assertEquals(40.5f, CortanaUi.CHECKBOX_LABEL_LEFT_EPX)               // §3.7.2
        assertEquals("Complete and move to History", CortanaUi.CHECKBOX_LABEL_TEXT) // §3.7.1
        assertEquals(43.4f, CortanaUi.FIELD_EPX)                             // §3.7.3
        assertEquals(53.6f, CortanaUi.FIELD_PITCH_EPX)                       // §3.7.3
        assertEquals(rgb(55, 73, 119), CortanaUi.FIELD_BORDER_COLOR.argb())  // §3.7.3
        assertEquals(rgb(32, 37, 33), CortanaUi.FIELD_FILL.argb())           // §3.7.3
        assertEquals(95.9f, CortanaUi.TEXT_FIELD_TOP_EPX)                    // §3.7.3
        assertEquals(17.6f, CortanaUi.FIELD_TEXT_LEFT_EPX)                   // §3.7.3
        assertEquals(16.5f, CortanaUi.TEXT_FIELD_CAP_EPX)                    // §3.7.3
        assertEquals(149.5f, CortanaUi.TIME_DATE_TOP_EPX)                    // §3.7.3
        assertEquals(11.3f, CortanaUi.FIELD_GAP_EPX)                         // §3.7.3
        assertEquals(204.2f, CortanaUi.COMBO_TOP_EPX)                        // §3.7.3
        assertEquals(32f, CortanaUi.COMBO_EPX)                               // §3.7.3
        assertEquals(11.2f, CortanaUi.DETAIL_PHOTO_SIDE_EPX)                 // §3.7.4
        assertEquals(1.78f, CortanaUi.DETAIL_PHOTO_ASPECT)                   // §3.7.4, 337.6 × 189.8
        assertEquals(247.4f, CortanaUi.DETAIL_PHOTO_TOP_EPX)                 // §3.7.4
    }

    /** §3.7.3's 53.6-epx pitch has to carry the text field's top to the time and date row's. */
    @Test
    fun `the field pitch is consistent with the measured field tops`() {
        assertEquals(
            CortanaUi.TIME_DATE_TOP_EPX,
            CortanaUi.TEXT_FIELD_TOP_EPX + CortanaUi.FIELD_PITCH_EPX,
            0.1f,
        )
        // The combo is the next step down, within the ±1.1-epx single-edge tolerance R7 works to.
        assertEquals(
            CortanaUi.COMBO_TOP_EPX,
            CortanaUi.TIME_DATE_TOP_EPX + CortanaUi.FIELD_PITCH_EPX,
            1.2f,
        )
    }

    /** §3.7.4's photo is 337.6 × 189.8 epx on the 360-epx canvas at 11.2-epx side margins. */
    @Test
    fun `the reminder page photo reproduces its measured box`() {
        val w = 360f - 2 * CortanaUi.DETAIL_PHOTO_SIDE_EPX
        assertEquals(337.6f, w, 0.5f)
        assertEquals(189.8f, w / CortanaUi.DETAIL_PHOTO_ASPECT, 0.5f)
    }

    /** §3.5.8's dim glyphs read 110 and 123 against a bright 225. */
    @Test
    fun `a disabled app bar button is about half as bright as an enabled one`() {
        assertEquals((110f + 123f) / 2f / 225f, CortanaUi.APPBAR_DISABLED_ALPHA, 0.05f)
    }
}
