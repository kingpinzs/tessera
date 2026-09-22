package app.tileshell.start

import app.tileshell.ui.motion.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a tile changes face: R3 A8's own random timer, or a slideshow's fixed cadence (INDEX Change Log
 * 2026-09-21 item 4).
 *
 * The draws are passed in, so "random" is a value these tests choose and the rules are exact.
 */
class TileTimingTest {

    private val min = Motion.CROSSFADE_PERIOD_MIN_MS
    private val max = Motion.CROSSFADE_PERIOD_MAX_MS

    // ---- the slideshow's advance timing ----

    @Test
    fun `a slideshow advances on its fixed cadence, not on a random draw`() {
        assertEquals(
            TileTiming.SLIDESHOW_MS.toLong(),
            TileTiming.periodMs(TileTiming.SLIDESHOW_MS, min, max) { _, _ -> error("a slideshow never draws") },
        )
    }

    @Test
    fun `a slideshow starts at once, with no random start phase`() {
        assertEquals(0L, TileTiming.startPhaseMs(TileTiming.SLIDESHOW_MS, max) { error("a slideshow never draws") })
    }

    @Test
    fun `the slideshow is faster than R3 A8's band, so it reads as a slideshow`() {
        assertTrue(TileTiming.SLIDESHOW_MS < min)
    }

    @Test
    fun `every advance takes exactly the same time`() {
        val periods = (1..5).map { TileTiming.periodMs(TileTiming.SLIDESHOW_MS, min, max) { _, _ -> 0L } }
        assertEquals(1, periods.toSet().size)
    }

    // ---- R3 A8, untouched for every other tile ----

    @Test
    fun `a live tile draws its period inside the measured band, end inclusive`() {
        var lo = -1L
        var hi = -1L
        TileTiming.periodMs(0, min, max) { a, b -> lo = a; hi = b; a }
        assertEquals(min, lo)
        assertEquals(max + 1, hi) // the draw is exclusive of its upper bound, so max is reachable
    }

    @Test
    fun `a live tile still starts at a random phase inside its period`() {
        var asked = -1L
        TileTiming.startPhaseMs(0, max) { asked = it; 42L }
        assertEquals(max, asked)
        assertEquals(42L, TileTiming.startPhaseMs(0, max) { 42L })
    }

    // ---- periods are start-to-start (R3 A8) ----

    @Test
    fun `the animation's own time comes out of the period`() {
        assertEquals(2600L, TileTiming.remainingMs(3000L, 400L))
    }

    @Test
    fun `an animation longer than the period means the next change is due now`() {
        assertEquals(0L, TileTiming.remainingMs(3000L, 4000L))
    }

    // ---- which face is on show ----

    @Test
    fun `a tile with three photos walks front, photo, photo, photo and comes back`() {
        assertEquals(listOf(1, 2, 3, 0, 1), (1..5).map { TileTiming.faceAfter(it, 3) })
    }

    @Test
    fun `a tile with one live face alternates with its front`() {
        assertEquals(listOf(1, 0, 1, 0), (1..4).map { TileTiming.faceAfter(it, 1) })
    }

    @Test
    fun `a tile with no live faces stays on its front`() {
        // A music tile while a song is playing, and a picture frame: one face, and it never moves.
        assertEquals(0, TileTiming.faceAfter(7, 0))
    }

    @Test
    fun `the index never runs off the end of the list`() {
        (0..50).forEach { assertTrue(TileTiming.faceAfter(it, 4) in 0..4) }
    }
}
