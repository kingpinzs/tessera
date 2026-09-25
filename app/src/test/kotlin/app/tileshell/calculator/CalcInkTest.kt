package app.tileshell.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The pure half of the ink placement (docs/plan/r11/calculator.md 1.5, 1.7, 2.14, 3.6, 3.12; qa/phase-15/E13/DEFECT.md):
 * sizing a run so its rasterised ink is exactly the height r11 measured, and the display fit taking that size as its
 * full face. The rasterising itself needs a device and is verified by E13.
 */
class CalcInkTest {

    /** A run whose ink is 0.72 of the size, rasterised to whole pixels. */
    private val ink = { size: Float -> (0.72f * size).roundToInt() }

    @Test fun `sizeForInkHeight lands in the middle of the sizes that give the target`() {
        val size = InkMath.sizeForInkHeight(99, start = 99f / 0.7f, stepPx = 0.125f, maxSteps = 64, inkHeight = ink)
        assertEquals(99, ink(size))
        // 0.72 × size rounds to 99 for 136.81 ≤ size < 138.19; the middle is 137.5
        assertEquals(137.5f, size, 0.2f)
    }

    @Test fun `sizeForInkHeight reaches a target from either side`() {
        for (start in listOf(50f, 137.5f, 400f)) {
            val size = InkMath.sizeForInkHeight(48, start, 0.125f, 64, ink)
            assertEquals("from $start", 48, ink(size))
        }
    }

    @Test fun `sizeForInkHeight returns the last size tried when nothing lands`() {
        val size = InkMath.sizeForInkHeight(99, 100f, 0.125f, 4) { 50 }
        assertTrue(size.isFinite())
    }

    @Test fun `the display fit scales from the measured full face, not S1's 46`() {
        assertEquals(45.2f, CalcDisplayFit.fontSize(100f, 340f, 45.2f), 0.001f)
        assertEquals(45.2f, CalcDisplayFit.fontSize(340f, 340f, 45.2f), 0.001f)
        assertEquals(45.2f * 340f / 510f, CalcDisplayFit.fontSize(510f, 340f, 45.2f), 0.001f)
        assertEquals(12f, CalcDisplayFit.fontSize(3400f, 340f, 45.2f), 0.001f)
        assertEquals(46f, CalcDisplayFit.fontSize(100f, 340f), 0.001f)
    }
}
