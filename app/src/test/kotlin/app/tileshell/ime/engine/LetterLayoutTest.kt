package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geometry the engine derives from key centres alone, on the R6 §2.1 grid: no neighbour table is
 * hard-coded anywhere, so this is where the adjacency the edit costs rely on is pinned.
 */
class LetterLayoutTest {

    private val layout = TestLayout.w10m

    @Test
    fun `same-row neighbours are one pitch apart and adjacent`() {
        assertEquals(1f, layout.pitchDistance('q', 'w'), 1e-4f)
        assertTrue(layout.adjacent('q', 'w'))
        assertTrue(layout.adjacent('a', 's'))
        assertTrue(layout.adjacent('n', 'm'))
    }

    @Test
    fun `diagonal neighbours on the staggered rows are adjacent too`() {
        // q → a: about half a pitch across (R6 §2.1.6's 77.5 inset is not exactly half of §2.1.4's
        // margin plus a pitch), one down: √(0.5² + 1) ≈ 1.12
        assertEquals(1.117f, layout.pitchDistance('q', 'a'), 3e-3f)
        assertTrue(layout.adjacent('q', 'a'))
        assertTrue(layout.adjacent('w', 'a'))
        assertTrue(layout.adjacent('g', 'h'))
        assertTrue(layout.adjacent('t', 'g'))
        // Row 3 sits on row 2's COLUMNS (R6 §2.1.8), not half a pitch over as on a desk keyboard: z is
        // straight under s, b straight under h, so h–b are one pitch apart and h–n are not neighbours.
        assertTrue(layout.adjacent('s', 'z'))
        assertEquals(1f, layout.pitchDistance('h', 'b'), 1e-4f)
        assertTrue(layout.adjacent('h', 'b'))
        assertFalse(layout.adjacent('h', 'n'))
    }

    @Test
    fun `a key two columns over, or two rows away, is not adjacent`() {
        assertFalse(layout.adjacent('q', 'e'))
        assertFalse(layout.adjacent('q', 's'))
        assertFalse(layout.adjacent('q', 'z'))
        assertFalse(layout.adjacent('e', 'c'))
        assertFalse(layout.adjacent('a', 'a'))
    }

    @Test
    fun `nearest keys come back nearest first, and a missing key is infinitely far`() {
        val e = TestLayout.centre('e')
        val nearest = layout.nearest(e.x, e.y, 3)
        assertEquals('e', nearest[0])
        assertEquals(setOf('w', 'r'), nearest.drop(1).toSet()) // tied at one pitch either side
        assertEquals(listOf('e', 'r'), layout.nearest(e.x + 20f, e.y, 2))
        // Between e and r, a little below: e/r first, then d (the diagonal below both).
        assertEquals(listOf('e', 'r', 'd').toSet(), layout.nearest(e.x + 72f, e.y + 40f, 3).toSet())
        assertEquals(Float.MAX_VALUE, layout.pitchDistance('e', '\''))
        assertTrue(layout.nearest(-5000f, -5000f, 3, maxPitches = 1.5f).isEmpty())
    }
}
