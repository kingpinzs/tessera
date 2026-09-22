package app.tileshell.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flip's squash curve (Jeremy, 2026-09-22: "Tile flips seem a bit abrupt").
 *
 * R3 A7 measured the flip's form and its 108 ms, and gave no curve; the build filled that gap with a
 * linear squash, which leaves rest instantly and stops dead. This is the curve of a tile actually
 * turning about its horizontal axis, so what these tests pin is that it leaves and returns to rest
 * slowly and passes edge-on at the half-way point where the face swaps.
 */
class FlipScaleTest {

    @Test
    fun `the tile is whole at both ends and edge-on in the middle`() {
        assertEquals(1f, Motion.flipScale(0f), 1e-4f)
        assertEquals(0f, Motion.flipScale(0.5f), 1e-4f)
        assertEquals(1f, Motion.flipScale(1f), 1e-4f)
    }

    @Test
    fun `it leaves and returns to rest gently, which the linear squash did not`() {
        // Over the first twentieth of the flip a linear squash has already given up 10% of the tile's
        // height. The measured-form curve has barely moved, which is what "not abrupt" means here.
        val moved = 1f - Motion.flipScale(0.05f)
        assertTrue("moved $moved", moved < 0.05f)
        assertEquals(moved, 1f - Motion.flipScale(0.95f), 1e-4f) // and it settles as gently as it left
    }

    @Test
    fun `it is fastest as the face passes edge-on`() {
        // Sampled inside ONE half of the flip. Straddling the midpoint measures nothing: the curve is
        // symmetric about it, so both ends of such a window sit at the same height and the difference is
        // zero however fast the tile is moving through it.
        fun speed(a: Float, b: Float) = kotlin.math.abs(Motion.flipScale(b) - Motion.flipScale(a))
        assertTrue(speed(0.4f, 0.5f) > speed(0.0f, 0.1f))
        assertTrue(speed(0.5f, 0.6f) > speed(0.9f, 1.0f))
    }

    @Test
    fun `it never leaves the tile inside out or oversized`() {
        for (i in 0..100) {
            val v = Motion.flipScale(i / 100f)
            assertTrue("t=${i / 100f} v=$v", v in 0f..1f)
        }
        assertEquals(1f, Motion.flipScale(-1f), 1e-4f)
        assertEquals(1f, Motion.flipScale(2f), 1e-4f)
    }
}
