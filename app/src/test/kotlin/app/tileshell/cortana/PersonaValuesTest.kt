package app.tileshell.cortana

import app.tileshell.cortana.reminders.Recurrence
import app.tileshell.cortana.ui.CardValues
import app.tileshell.cortana.ui.PersonaValues
import app.tileshell.cortana.ui.TextBoxValues
import app.tileshell.ui.tokens.CapMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every persona, text-box and card constant against the number the phase 03 Decisions quote from R3, R6
 * and R7 — the same discipline phase 02's gate ended up needing for its edit-mode values.
 *
 * This does not claim the values are RIGHT; R3/R6/R7 and the H rows own that. It claims the code still
 * says what the doc says, so an accidental edit turns red instead of quietly changing Cortana's motion.
 */
class PersonaValuesTest {

    @Test
    fun `idle and thinking ring match R3 A22`() {
        assertEquals(70f, PersonaValues.IDLE_OUTER_EPX)        // A22 governing row, 51 px = 70 epx
        assertEquals(48f, PersonaValues.IDLE_INNER_EPX)
        assertEquals(11f, PersonaValues.IDLE_STROKE_EPX)
        assertEquals(244f, PersonaValues.IDLE_CENTRE_Y_EPX)    // 33 % of 731
        assertEquals(79f, PersonaValues.THINKING_OUTER_EPX)    // 74 px = 79 epx
        assertEquals(15f, PersonaValues.THINKING_STROKE_EPX)
        assertEquals(183, PersonaValues.POP_GROW_MS)           // 11 frames at 60 fps
        assertEquals(650, PersonaValues.POP_TOTAL_MS)
        assertEquals(367, PersonaValues.ROTATE_TO_EDGE_MS)     // 22 frames
        assertEquals(917, PersonaValues.ROTATE_PERIOD_MS)      // 55 frames
        assertEquals(217, PersonaValues.MOVE_TO_TOP_MS)        // 13 frames
    }

    @Test
    fun `listening matches R6 3_1_6 to 3_1_10 and supersedes A22's pulse`() {
        assertEquals(85.8f, PersonaValues.LISTEN_HALO_MIN_EPX)
        assertEquals(94.7f, PersonaValues.LISTEN_HALO_MAX_EPX)
        assertEquals(41.1f, PersonaValues.LISTEN_DISC_MAX_EPX)
        assertEquals(37.3f, PersonaValues.LISTEN_DISC_MIN_EPX)
        assertEquals(1040, PersonaValues.LISTEN_PERIOD_MS)     // 1.04 ± 0.02 s
        assertEquals(243.8f, PersonaValues.LISTEN_CENTRE_Y_EPX)
        assertEquals(333, PersonaValues.LISTEN_ENTRANCE_MS)
        assertEquals(
            "the four segments have to add up to the period",
            PersonaValues.LISTEN_PERIOD_MS,
            PersonaValues.LISTEN_RISE_MS + PersonaValues.LISTEN_TOP_HOLD_MS +
                PersonaValues.LISTEN_FALL_MS + PersonaValues.LISTEN_BOTTOM_HOLD_MS,
        )
    }

    @Test
    fun `the listening pulse rises, holds, falls and holds`() {
        assertEquals(0f, PersonaValues.listenPhase(0), 0.001f)
        assertEquals(1f, PersonaValues.listenPhase(350), 0.001f)
        assertEquals(1f, PersonaValues.listenPhase(450), 0.001f)   // inside the top hold
        assertEquals(0f, PersonaValues.listenPhase(900), 0.001f)
        assertEquals(0f, PersonaValues.listenPhase(1000), 0.001f)  // inside the bottom hold
        // It repeats: the same phase one period later.
        assertEquals(
            PersonaValues.listenPhase(200),
            PersonaValues.listenPhase(200L + PersonaValues.LISTEN_PERIOD_MS),
            0.001f,
        )
    }

    @Test
    fun `the halo and the disc move in antiphase`() {
        // R6 §3.1.7: the halo grows while the disc shrinks. At phase 0 the disc is at its LARGEST.
        assertTrue(PersonaValues.LISTEN_DISC_MAX_EPX > PersonaValues.LISTEN_DISC_MIN_EPX)
        assertTrue(PersonaValues.LISTEN_HALO_MAX_EPX > PersonaValues.LISTEN_HALO_MIN_EPX)
    }

    @Test
    fun `the rotation never fully closes, as A22 measured`() {
        assertEquals(1f, PersonaValues.rotationScaleX(0), 0.001f)
        // A22 saw the apparent width go 74 -> 38 px, so it bottoms out at 38/74, not at zero.
        assertEquals(38f / 74f, PersonaValues.rotationScaleX(PersonaValues.ROTATE_TO_EDGE_MS.toLong()), 0.01f)
        assertTrue(PersonaValues.rotationScaleX(500) == 1f)
    }

    @Test
    fun `speaking and after-speaking match R6 3_2`() {
        assertEquals(19.0f, PersonaValues.SPEAK_DISC_EPX)
        assertEquals(34.5f, PersonaValues.SPEAK_HALO_MIN_EPX)
        assertEquals(40.4f, PersonaValues.SPEAK_HALO_MAX_EPX)
        assertEquals(51, PersonaValues.SPEAK_STEP_MS)          // steps every 51 ± 17 ms, no period
        assertEquals(40f, PersonaValues.SPEAK_CENTRE_Y_EPX)
        assertEquals(17.4f, PersonaValues.AWAIT_DISC_MIN_EPX)
        assertEquals(19.2f, PersonaValues.AWAIT_DISC_MAX_EPX)
        assertEquals(1000, PersonaValues.AWAIT_PERIOD_MS)
        assertEquals(25.9f, PersonaValues.AFTER_RING_MIN_EPX)
        assertEquals(29.1f, PersonaValues.AFTER_RING_MAX_EPX)
        assertEquals(3750, PersonaValues.AFTER_PERIOD_MS)      // 3.75 ± 0.1 s
    }

    @Test
    fun `the waveform glyph matches R6 3_1_2 to 3_1_5`() {
        assertEquals(23f, PersonaValues.WAVE_WIDTH_EPX)
        assertEquals(14f, PersonaValues.WAVE_HEIGHT_EPX)
        assertEquals(128, PersonaValues.WAVE_STEP_MS)          // replaced in steps, no interpolation
        assertEquals(5.4f, PersonaValues.WAVE_AFTER_TEXT_EPX)
        assertEquals(1.6f, PersonaValues.WAVE_CENTRE_ABOVE_X_HEIGHT_EPX)
    }

    @Test
    fun `the text box matches R6 3_3 and the listening box 3_1_11`() {
        assertEquals(48f, TextBoxValues.HEIGHT_EPX)
        assertEquals(48f, TextBoxValues.MIC_BUTTON_EPX)
        assertEquals(12f, TextBoxValues.TEXT_LEFT_EPX)
        assertEquals("Ask me anything", TextBoxValues.PLACEHOLDER)   // §3.3.5, H13
        assertEquals(52f, TextBoxValues.LISTENING_HEIGHT_EPX)
        assertEquals(2f, TextBoxValues.LISTENING_BORDER_EPX)
        assertEquals(15.7f, TextBoxValues.LISTENING_TEXT_LEFT_EPX)
        assertEquals(16.6f, TextBoxValues.LISTENING_SUBMIT_RIGHT_EPX)
        assertEquals("Listening...", TextBoxValues.LISTENING_PLACEHOLDER)
        assertEquals(26.8f, TextBoxValues.CALLOUT_HEIGHT_EPX)         // §3.3.9
        assertEquals(8.6f, TextBoxValues.CALLOUT_TAIL_HEIGHT_EPX)
        assertEquals(17f, TextBoxValues.CALLOUT_TAIL_BASE_EPX)
        // §3.1.14 (15063): the page background is black, not the 14393 dark grey.
        assertEquals(androidx.compose.ui.graphics.Color.Black, TextBoxValues.PAGE_BACKGROUND)
    }

    @Test
    fun `the cards match R6 3_4_2 and R7 3_8_1`() {
        assertEquals(16f, CardValues.TITLE_LEFT_EPX)
        assertEquals(43f, CardValues.FIELD_HEIGHT_EPX)
        assertEquals(53.6f, CardValues.FIELD_PITCH_EPX)
        assertEquals(32f, CardValues.COMBO_HEIGHT_EPX)
        assertEquals(32.2f, CardValues.BUTTON_HEIGHT_EPX)
        assertEquals(164.6f, CardValues.BUTTON_LEFT_WIDTH_EPX)
        assertEquals(160.0f, CardValues.BUTTON_RIGHT_WIDTH_EPX)
        assertEquals(3.8f, CardValues.BUTTON_GAP_EPX)
        assertEquals(16.3f, CardValues.BUTTON_LEFT_MARGIN_EPX)
        assertEquals(15.7f, CardValues.BUTTON_RIGHT_MARGIN_EPX)
        assertEquals(94.6f, CardValues.SAVED_TITLE_CAP_TOP_EPX)
        assertEquals(133.9f, CardValues.SAVED_CAPTION_CAP_TOP_EPX)
        assertEquals(169.2f, CardValues.SAVED_ROW_CAP_TOP_EPX)
        assertEquals(62.9f, CardValues.SAVED_ROW_TEXT_LEFT_EPX)
        assertEquals(198.0f, CardValues.SAVED_SUBLINE_CAP_TOP_EPX)
        assertEquals(40f, CardValues.PERSONA_CENTRE_Y_EPX)
        // The two measured button widths plus their gap and margins must fill the 360-epx canvas.
        val total = CardValues.BUTTON_LEFT_MARGIN_EPX + CardValues.BUTTON_LEFT_WIDTH_EPX +
            CardValues.BUTTON_GAP_EPX + CardValues.BUTTON_RIGHT_WIDTH_EPX + CardValues.BUTTON_RIGHT_MARGIN_EPX
        assertEquals("R6 §3.4.2's buttons span the canvas", 360f, total, 1.0f)
    }

    @Test
    fun `cap metrics reproduce R6's measured title cap`() {
        // R6 §3.4.2 measured "cap 14 epx, 20-epx class". Selawik's cap ratio has to give that back.
        assertEquals(14.0f, CapMetrics.capHeight(20f), 0.05f)
        // R7 §3.8.1's "cap 16.7 epx (≈24-epx type)".
        assertEquals(16.7f, CapMetrics.capHeight(24f), 0.15f)
        // The ramp's own line-height rule, so the conversion does not need a hardcoded line height.
        assertEquals(20f, CapMetrics.lineHeightFor(15f), 0.001f)
        assertEquals(24f, CapMetrics.lineHeightFor(20f), 0.001f)
        // A cap top is always inside its box, and asking for one above the container clamps rather than
        // pushing the text off the top.
        assertTrue(CapMetrics.capTopWithinBox(15f, 20f) > 0f)
        assertEquals(0f, CapMetrics.topPaddingForCapTop(0f, 15f, 20f), 0.001f)
    }

    @Test
    fun `the recurrence options are the ruled five`() {
        assertEquals(
            listOf("Only once", "Every day", "Every week", "Every month", "Every year"),
            Recurrence.entries.map { it.label },
        )
        assertEquals(Recurrence.MONTH, Recurrence.of("Every Month"))   // R6 §3.4.3's spelling
        assertEquals(Recurrence.ONCE, Recurrence.of("nonsense"))
    }
}
