package app.tileshell.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerMathTest {
    @Test
    fun `a landscape photo is fitted to the width`() {
        val (w, h) = ViewerMath.fit(640, 480, 1080f, 2340f)
        assertEquals(1080f, w, 0.01f)
        assertEquals(810f, h, 0.01f)
    }

    @Test
    fun `a photo taller than the screen is fitted inside it`() {
        val (w, h) = ViewerMath.fit(1000, 4000, 1080f, 2340f)
        assertEquals(2340f, h, 0.01f)
        assertEquals(585f, w, 0.01f)
    }

    @Test
    fun `a double tap zooms to twice the fit or to the picture's own pixels`() {
        assertEquals(2f, ViewerMath.doubleTapScale(640, 1080f), 0.001f)
        assertEquals(8f, ViewerMath.doubleTapScale(8640, 1080f), 0.001f)
        assertEquals(4f, ViewerMath.maxScale(640, 1080f), 0.001f)
    }

    @Test
    fun `a pan never opens a gap`() {
        assertEquals(0f, ViewerMath.clampPan(300f, 1080f, 1080f), 0f)
        assertEquals(0f, ViewerMath.clampPan(-50f, 810f, 2340f), 0f)
        assertEquals(540f, ViewerMath.clampPan(900f, 2160f, 1080f), 0f)
        assertEquals(-540f, ViewerMath.clampPan(-900f, 2160f, 1080f), 0f)
        assertEquals(100f, ViewerMath.clampPan(100f, 2160f, 1080f), 0f)
    }

    @Test
    fun `the visible region of a centred double zoom is the middle half`() {
        // 4000 x 3000 shown 2160 x 1620 in a 1080 x 2340 view: all of its height, the middle half of its width.
        assertEquals(PxRect(1000, 0, 3000, 3000), ViewerMath.visibleRegion(4000, 3000, 2160f, 1620f, 0f, 0f, 1080f, 2340f))
        // Panned fully right (the picture moved left by 540): the right half.
        assertEquals(PxRect(2000, 0, 4000, 3000), ViewerMath.visibleRegion(4000, 3000, 2160f, 1620f, -540f, 0f, 1080f, 2340f))
    }

    @Test
    fun `the sample size keeps a region near the screen's size`() {
        assertEquals(1, ViewerMath.sampleSize(1500, 1080))
        assertEquals(2, ViewerMath.sampleSize(2160, 1080))
        assertEquals(8, ViewerMath.sampleSize(16320, 1080))
    }

    @Test
    fun `next wraps to the first`() {
        assertEquals(1, ViewerMath.nextIndex(0, 3))
        assertEquals(0, ViewerMath.nextIndex(2, 3))
        assertEquals(0, ViewerMath.nextIndex(0, 0))
    }

    @Test
    fun `orientation 6 maps the upright top-left to the raw bottom-left`() {
        // Raw 4000 x 3000 with orientation 6 (turn 90 clockwise to view) is upright 3000 x 4000.
        assertEquals(3000 to 4000, ImageOrientation.uprightSize(4000, 3000, 6))
        assertEquals(0f to 3000f, ImageOrientation.toRaw(0f, 0f, 4000, 3000, 6))
        assertEquals(4000f to 0f, ImageOrientation.toRaw(3000f, 4000f, 4000, 3000, 6))
        assertEquals(PxRect(0, 2000, 500, 3000), ImageOrientation.toRaw(PxRect(0, 0, 1000, 500), 4000, 3000, 6))
    }

    @Test
    fun `every orientation maps the upright corners onto the raw corners`() {
        val rawW = 40
        val rawH = 30
        for (o in 1..8) {
            val (w, h) = ImageOrientation.uprightSize(rawW, rawH, o)
            val corners = listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat()).map { (x, y) -> ImageOrientation.toRaw(x, y, rawW, rawH, o) }.toSet()
            assertEquals("orientation $o", setOf(0f to 0f, 40f to 0f, 0f to 30f, 40f to 30f), corners)
            assertEquals("orientation $o", PxRect(0, 0, rawW, rawH), ImageOrientation.toRaw(PxRect(0, 0, w, h), rawW, rawH, o))
        }
    }

    @Test
    fun `the turn that makes raw pixels upright agrees with the point mapping`() {
        // Apply rotationAndFlip to the raw top-left corner pixel's centre and compare with toRaw's inverse on a 4 x 2 picture.
        val rawW = 4
        val rawH = 2
        for (o in 1..8) {
            val (deg, flip) = ImageOrientation.rotationAndFlip(o)
            val (w, _) = ImageOrientation.uprightSize(rawW, rawH, o)
            // Where the raw point (0.5, 0.5) lands after the clockwise turn and the optional mirror.
            var (x, y) = when (deg) {
                90 -> (rawH - 0.5f) to 0.5f
                180 -> (rawW - 0.5f) to (rawH - 0.5f)
                270 -> 0.5f to (rawW - 0.5f)
                else -> 0.5f to 0.5f
            }
            if (flip) x = w - x
            assertEquals("orientation $o", 0.5f to 0.5f, ImageOrientation.toRaw(x, y, rawW, rawH, o))
        }
    }
}
