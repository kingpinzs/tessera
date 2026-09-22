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
    fun `a slideshow keeps its own cadence and never joins the comb`() {
        // The comb is what stops live tiles flipping together; a slideshow is one tile advancing its own
        // photos, so it runs on its fixed cadence from the moment it is switched on. TileView reads this
        // as "slideshowMs > 0", and the value it then uses is the cadence itself.
        assertEquals(
            TileTiming.SLIDESHOW_MS.toLong(),
            TileTiming.periodMs(TileTiming.SLIDESHOW_MS, min, max) { _, _ -> error("a slideshow never draws") },
        )
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
    fun `two tiles never take the same turn, whenever each of them woke up`() {
        // Jeremy, 2026-09-22: "they shouldn't do it one after another each should be on their own timer".
        // Four tiles, each asking at a DIFFERENT moment — which is the case that beat spreading the
        // phases by index, because each tile's phase used to be measured from its own start.
        val band = 4960L
        val fires = (0 until 4).map { i ->
            val askedAt = 1_000_000L + i * 377L
            askedAt + TileTiming.untilSlotMs(askedAt, band, i, 4)
        }
        val slots = fires.map { it % band }
        assertEquals(listOf(0L, 1240L, 2480L, 3720L), slots.sorted())
        assertEquals(4, slots.toSet().size)
    }

    @Test
    fun `the gap between neighbouring tiles is a whole slice of the band`() {
        val band = 4960L
        val slots = (0 until 8).map { TileTiming.untilSlotMs(0L, band, it, 8) % band }.sorted()
        for (i in 1 until slots.size) assertEquals(band / 8, slots[i] - slots[i - 1])
    }

    @Test
    fun `a turn is never zero, so a tile cannot flip twice in the same instant`() {
        val band = 4960L
        for (index in 0 until 4) {
            for (now in listOf(0L, 1240L, 2480L, 3720L, 4959L)) {
                val wait = TileTiming.untilSlotMs(now, band, index, 4)
                assertTrue("index=$index now=$now wait=$wait", wait in 1..band)
            }
        }
    }

    @Test
    fun `every cycling tile shares one comb, whatever its transition`() {
        // Two combs would defeat the point: a flip tile and a crossfade tile on different rates drift
        // against each other until they coincide, which is exactly what this is here to stop.
        assertEquals(TileTiming.COMB_MS, TileTiming.bandCentreMs(Motion.FLIP_PERIOD_MIN_MS, Motion.FLIP_PERIOD_MAX_MS))
        assertTrue(TileTiming.COMB_MS in Motion.FLIP_PERIOD_MIN_MS..Motion.FLIP_PERIOD_MAX_MS)
        // And the deviation it costs the crossfade tiles is recorded rather than hidden: 160 ms past the
        // far edge of their measured band.
        assertEquals(160L, TileTiming.COMB_MS - Motion.CROSSFADE_PERIOD_MAX_MS)
    }

    @Test
    fun `a tile on its own still waits its turn rather than flipping instantly`() {
        assertTrue(TileTiming.untilSlotMs(0L, 4960L, index = 0, count = 1) > 0L)
    }

    @Test
    fun `the comb never divides by zero`() {
        assertEquals(0L, TileTiming.untilSlotMs(0L, 0L, 1, 4))
        assertTrue(TileTiming.untilSlotMs(0L, 4960L, index = 3, count = 0) > 0L)
        assertTrue(TileTiming.untilSlotMs(0L, 4960L, index = -2, count = 4) > 0L)
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
