package app.tileshell.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The viewfinder's and the dial's measured geometry (Approximations Y3, Y4, Y12; r11/camera.md, camera-pass2.md §4):
 * the numbers E19 reads from a dump, proven on the two canvases R11 measured (360 × 640) and the emulator's
 * (360 × 780), nav bar 48.
 */
class CameraGeometryTest {
    private val lumia = CameraGeometry(360f, 640f, 48f)
    private val avd = CameraGeometry(360f, 780f, 48f)
    private fun near(expected: Float, actual: Float, tolerance: Float = 0.05f) = assertEquals(expected, actual, tolerance)

    @Test fun `the 4 to 3 preview is fitted to the width and centred on the full screen`() {
        val p = lumia.preview(4, 3)
        near(80f, p.top); near(560f, p.bottom); near(360f, p.width)          // camera-pass2: 80 → 560 on 360 × 640
        near(avd.h / 2f, avd.preview(4, 3).cy)
        near(640f, avd.preview(16, 9).height)
        near(avd.h / 2f, avd.preview(16, 9).cy)
    }

    @Test fun `the grid is two lines each way at thirds of the preview`() {
        val p = avd.preview(4, 3)
        val (xs, ys) = avd.gridLines(p)
        assertEquals(listOf(120f, 240f), xs)
        near(p.top + 160f, ys[0]); near(p.top + 320f, ys[1])
    }

    @Test fun `the shutter and the discs sit where R11 measured them`() {
        near(180f, lumia.shutterCentre().x); near(592f - 56f, lumia.shutterCentre().y)
        near(592f - 74.75f, lumia.shutterCentre(fiveRings = true).y)
        near(240f, lumia.discCentre(right = true).x); near(120f, lumia.discCentre(right = false).x)
        near(592f - 36f, lumia.discCentre(true).y)
        near(336f, lumia.settingsCentre.x); near(592f - 27.9f, lumia.settingsCentre.y)
        near(24.7f, lumia.rollCentre.x); near(592f - 28f, lumia.rollCentre.y)
        near(336f, lumia.switchCentre.x); near(28f, lumia.switchCentre.y)
    }

    @Test fun `the capsule's glyphs are 24 from the right at the measured offsets from mid-height`() {
        assertEquals(listOf(254f, 298f, 342f, 386f), lumia.capsuleGlyphs(4).map { it.y })
        assertTrue(lumia.capsuleGlyphs(4).all { it.x == 336f })
        assertEquals(listOf(-43.7f, 0.5f, 45.1f), lumia.capsuleOffsets(3))
        // camera-pass2: y 217 → 423 of 640, 2 → 45.5 from the edge.
        val box = lumia.capsule(4)
        near(216.8f, box.top); near(423.2f, box.bottom); near(360f - 45.5f, box.left); near(358f, box.right)
        // A camera with fewer items keeps the 44-epx pitch about the centre.
        assertEquals(listOf(-22f, 22f), lumia.capsuleOffsets(2))
        assertEquals(listOf(0f), lumia.capsuleOffsets(1))
    }

    @Test fun `the five rings are centred on the nav bar's top at mid-width with the measured radii`() {
        assertEquals(Pt(180f, 592f), lumia.dialCentre)
        assertEquals(Pt(180f, 732f), avd.dialCentre)
        assertEquals(listOf(130.5f, 195.4f, 260.3f, 325.3f, 390.2f), (0..4).map { avd.ringRadius(it) })
        for (k in 0..4) near(130.5f + 65f * k, avd.ringRadius(k), 0.3f)
    }

    @Test fun `each ring's icon lies on its own ring`() {
        for (g in listOf(lumia, avd)) for (k in 0..4) {
            val icon = g.ringIcon(k)
            near(g.ringRadius(k), hypot(icon.x - g.dialCentre.x, icon.y - g.dialCentre.y), 0.01f)
        }
        // The exposure icon on the innermost ring's top; the others at x = 56 on the left (C3: angles 108–130°).
        assertEquals(Pt(180f, 592f - 130.5f), lumia.ringIcon(0))
        for (k in 1..4) near(56f, lumia.ringIcon(k).x, 0.01f)
        near(129.4f, lumia.ringIconAngle(1), 0.1f)
        near(108.5f, lumia.ringIconAngle(4), 0.1f)
    }

    @Test fun `each value label is centred at mid-width 30 point 3 above its ring's top`() {
        // C3: "auto" at 171.1 / 236.1 / 301.1 / 366.1 and "0.0" at 430.6 on 360 × 640.
        val expected = listOf(431.2f, 366.3f, 301.4f, 236.4f, 171.5f)
        for (k in 0..4) { near(180f, lumia.ringLabel(k).x); near(expected[k], lumia.ringLabel(k).y, 0.7f) }
        for (k in 0..4) near(avd.navTop - avd.ringRadius(k) - 30.3f, avd.ringLabel(k).y)
    }

    @Test fun `one control alone is one arc of 130 point 25 with its icon at 139 degrees`() {
        val icon = lumia.singleIcon
        near(130.25f, hypot(icon.x - 180f, icon.y - 592f), 0.01f)
        near(81.7f, icon.x, 0.1f)                                           // L2: x ≈ 80–85
        near(592f - 130.25f - 29.25f, lumia.singleLabel.y)
    }

    @Test fun `a value's angle and the angle's value are inverse over a ring's arc`() {
        for (k in 0..4) {
            val arc = avd.valueArc(avd.ringRadius(k), if (k == 0) null else avd.ringIconAngle(k))
            assertTrue("ring $k arc $arc", arc.second > arc.first + 20f)
            for (count in listOf(2, 5, 19)) for (i in 0 until count) {
                assertEquals(i, avd.valueIndex(arc, avd.valueAngle(arc, i, count), count))
            }
            // Every value of the arc is on the screen, clear of the ring's icon.
            for (i in 0 until 19) {
                val at = avd.onRing(avd.ringRadius(k), avd.valueAngle(arc, i, 19))
                assertTrue("ring $k value $i at $at", at.x in 0f..360f && at.y in 0f..avd.navTop)
                if (k > 0) assertTrue(hypot(at.x - avd.ringIcon(k).x, at.y - avd.ringIcon(k).y) > 16f)
            }
        }
        // The first value is at the arc's left end, the last at its right.
        val arc = avd.valueArc(130.5f, null)
        assertTrue(avd.onRing(130.5f, avd.valueAngle(arc, 0, 5)).x < avd.onRing(130.5f, avd.valueAngle(arc, 4, 5)).x)
    }

    @Test fun `a touch belongs to the nearest ring within half a gap`() {
        val (r, angle) = avd.polar(180f, 732f - 195.4f)
        near(195.4f, r); near(90f, angle)
        assertEquals(1, avd.ringAt(200f, listOf(0, 1, 2, 3, 4)))
        assertEquals(0, avd.ringAt(150f, listOf(0, 1, 2, 3, 4)))
        assertNull(avd.ringAt(60f, listOf(0, 1, 2, 3, 4)))
        assertNull(avd.ringAt(200f, listOf(0, 4)))
    }

    @Test fun `the panorama band is 35 percent of the width tall and centred on the screen`() {
        val band = lumia.panoBand
        near(126f, band.height); near(320f, band.cy); near(360f, band.width)   // camera-pass2 1.5.3: 126 on 360
        near(band.height * 9f / 16f, lumia.panoFrame(0f).width)
        near(360f, lumia.panoFrame(1f).width)
        near(lumia.panoFrame(0f).right + 22.5f, lumia.panoArrow(0f).x)
        assertTrue(lumia.panoFrame(0.5f).width > lumia.panoFrame(0.25f).width)
    }

    @Test fun `the zoom slider's ends are 0 point 565 of the width apart on the left edge`() {
        near(0.565f * 360f, lumia.zoomMinus.y - lumia.zoomPlus.y)
        near(24f, lumia.zoomPlus.x)
        near(320f, (lumia.zoomMinus.y + lumia.zoomPlus.y) / 2f)
    }
}
