package app.tileshell.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Controls inside a tile (INDEX Change Log 2026-09-21 item 3).
 *
 * The failure this is designed against is a stray LAUNCH when someone meant "pause", so most of these
 * tests are about what the strip refuses to claim: a touch it does not own falls through to the tile, and
 * a tile with no controls can never take a touch away from its own launch path.
 *
 * A medium tile on a 360-epx canvas is about 146 epx square, so those are the numbers used here.
 */
class TileControlsTest {

    private val w = 146f
    private val h = 146f
    private val controls = 3

    @Test
    fun `the strip is the bottom third of the tile`() {
        assertEquals(h * (1f - TileControls.STRIP_FRACTION), TileControls.stripTopPx(h), 0.001f)
    }

    @Test
    fun `a touch above the strip belongs to the tile, and therefore launches`() {
        assertNull(TileControls.hitTest(w / 2f, TileControls.stripTopPx(h) - 1f, w, h, controls))
    }

    @Test
    fun `a touch on the album art at the very top is not a control`() {
        assertNull(TileControls.hitTest(w / 2f, 1f, w, h, controls))
    }

    @Test
    fun `each control owns its own third of the strip`() {
        val y = h - 4f
        assertEquals(0, TileControls.hitTest(w * 0.1f, y, w, h, controls))
        assertEquals(1, TileControls.hitTest(w * 0.5f, y, w, h, controls))
        assertEquals(2, TileControls.hitTest(w * 0.9f, y, w, h, controls))
    }

    @Test
    fun `the cells meet with no gap and no overlap`() {
        val y = h - 1f
        val cell = TileControls.cellWidthPx(w, controls)
        assertEquals(0, TileControls.hitTest(cell - 0.01f, y, w, h, controls))
        assertEquals(1, TileControls.hitTest(cell + 0.01f, y, w, h, controls))
    }

    @Test
    fun `the far corners still land on a control rather than on nothing`() {
        assertEquals(0, TileControls.hitTest(0f, h, w, h, controls))
        assertEquals(controls - 1, TileControls.hitTest(w, h, w, h, controls))
    }

    @Test
    fun `a tile with no controls never claims a touch`() {
        // Every other tile in the shell: the hit test is consulted on every down, and it must answer null.
        assertNull(TileControls.hitTest(w / 2f, h - 1f, w, h, 0))
        assertNull(TileControls.hitTest(0f, 0f, w, h, 0))
    }

    @Test
    fun `a touch outside the tile is not a control`() {
        assertNull(TileControls.hitTest(-1f, h - 1f, w, h, controls))
        assertNull(TileControls.hitTest(w + 1f, h - 1f, w, h, controls))
        assertNull(TileControls.hitTest(w / 2f, h + 1f, w, h, controls))
        assertNull(TileControls.hitTest(w / 2f, -1f, w, h, controls))
    }

    @Test
    fun `a tile with no size yet claims nothing`() {
        // The first frame of a tile being placed, and the drag proxy before it is measured.
        assertNull(TileControls.hitTest(0f, 0f, 0f, 0f, controls))
    }

    @Test
    fun `the strip scales with the tile, so a wide tile has wider targets`() {
        val wide = 302f
        assertEquals(wide / controls, TileControls.cellWidthPx(wide, controls), 0.001f)
        assertEquals(2, TileControls.hitTest(wide - 1f, h - 1f, wide, h, controls))
    }

    @Test
    fun `cell left edges follow the same arithmetic the strip is drawn with`() {
        // What is drawn and what is touchable come from one place, so they cannot drift apart.
        val cell = TileControls.cellWidthPx(w, controls)
        (0 until controls).forEach { i ->
            assertEquals(cell * i, TileControls.cellLeftPx(w, controls, i), 0.001f)
            assertEquals(i, TileControls.hitTest(TileControls.cellLeftPx(w, controls, i) + cell / 2f, h - 1f, w, h, controls))
        }
    }

    @Test
    fun `a two-control strip splits in half`() {
        assertEquals(0, TileControls.hitTest(w * 0.25f, h - 1f, w, h, 2))
        assertEquals(1, TileControls.hitTest(w * 0.75f, h - 1f, w, h, 2))
    }

    @Test
    fun `a finger that slid off its control lands on a different answer, which is not a launch either`() {
        // The gesture handler compares the down's answer with the up's: different means "changed their
        // mind", and the whole gesture still ends without reaching onTap.
        val down = TileControls.hitTest(w * 0.1f, h - 2f, w, h, controls)
        val up = TileControls.hitTest(w * 0.9f, h - 2f, w, h, controls)
        assertEquals(0, down)
        assertEquals(2, up)
    }
}
