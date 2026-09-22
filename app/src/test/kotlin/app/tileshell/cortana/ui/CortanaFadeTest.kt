package app.tileshell.cortana.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7 §3.2's destination transition: the black hold, the fade window, and the curve inside it. */
class CortanaFadeTest {

    @Test
    fun `the black hold and the fade land inside R7's measured windows`() {
        // §3.2.1: 283-300 ms fully black (N2 18 frames, N1 17 frames twice).
        assertTrue("black ${CortanaUi.BLACK_MS} ms", CortanaUi.BLACK_MS in 283..300)
        // §3.2.2: the page then fades up over 200-317 ms (N1 200 and 317, N2 250).
        assertTrue("fade ${CortanaUi.FADE_MS} ms", CortanaUi.FADE_MS in 200..317)
        assertEquals(CortanaUi.BLACK_MS + CortanaUi.FADE_MS, CortanaFade.TOTAL_MS)
    }

    @Test
    fun `the fade's first frame is already 40 to 60 percent bright`() {
        val first = CortanaFade.fadeAlpha(0f)
        assertTrue("first frame $first", first in 0.4f..0.6f)
        // And still inside the window one 60-fps frame in.
        assertTrue(CortanaFade.fadeAlpha(1000f / 60f) in 0.4f..0.65f)
    }

    @Test
    fun `the fade is monotonic and reaches full brightness at the end`() {
        var previous = -1f
        for (step in 0..40) {
            val alpha = CortanaFade.fadeAlpha(CortanaUi.FADE_MS * step / 40f)
            assertTrue("alpha went backwards at step $step: $previous -> $alpha", alpha >= previous)
            assertTrue("alpha out of range at step $step: $alpha", alpha in 0f..1f)
            previous = alpha
        }
        assertEquals(1f, CortanaFade.fadeAlpha(CortanaUi.FADE_MS.toFloat()), 1e-4f)
        assertEquals(1f, CortanaFade.fadeAlpha(CortanaUi.FADE_MS * 10f), 1e-4f)
    }

    @Test
    fun `the curve eases OUT, so it is past halfway before half the time`() {
        val half = CortanaFade.fadeAlpha(CortanaUi.FADE_MS / 2f)
        val midpoint = (CortanaUi.FADE_FIRST_ALPHA + 1f) / 2f
        assertTrue("ease-out should be ahead of linear at the midpoint: $half vs $midpoint", half > midpoint)
    }

    @Test
    fun `the screen is fully black for the whole hold and not a frame longer`() {
        assertEquals(0f, CortanaFade.alphaFromChoice(0f), 1e-4f)
        assertEquals(0f, CortanaFade.alphaFromChoice(CortanaUi.BLACK_MS - 1f), 1e-4f)
        assertEquals(CortanaUi.FADE_FIRST_ALPHA, CortanaFade.alphaFromChoice(CortanaUi.BLACK_MS.toFloat()), 1e-4f)
        assertEquals(1f, CortanaFade.alphaFromChoice(CortanaFade.TOTAL_MS.toFloat()), 1e-4f)
    }

    @Test
    fun `the ease-out helper is clamped at both ends`() {
        assertEquals(0f, CortanaFade.easeOut(-1f), 1e-4f)
        assertEquals(0f, CortanaFade.easeOut(0f), 1e-4f)
        assertEquals(1f, CortanaFade.easeOut(1f), 1e-4f)
        assertEquals(1f, CortanaFade.easeOut(2f), 1e-4f)
    }

    /**
     * §3.1.12 and §3.2.1 together: the chosen item's accent fill lands one frame before the blackout at
     * 350 ms, and the black hold runs from there.
     */
    @Test
    fun `the pane's accent fill lands one frame before the blackout`() {
        val frame = 1000f / 60f
        assertEquals(
            CortanaUi.PANE_SELECT_TO_BLACK_MS.toFloat(),
            CortanaUi.PANE_PRESS_TO_ACCENT_MS + frame,
            1.0f,
        )
    }
}
