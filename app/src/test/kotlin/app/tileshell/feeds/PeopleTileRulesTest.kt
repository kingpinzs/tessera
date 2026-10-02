package app.tileshell.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The People tile's rules (phase 16 build task 7): R3 A9's timings, the next photo, and the skip decision (T16-19). */
class PeopleTileRulesTest {

    // ------------------------------------------------------------------------------------------- the skip

    @Test
    fun `a photo that decodes is drawn`() {
        assertNull(PeopleTileRules.skipReason(opened = true, width = 720, height = 720, decoded = true))
        assertNull(PeopleTileRules.skipReason(opened = true, width = 96, height = 64, decoded = true))
    }

    @Test
    fun `a photo the provider cannot open is skipped as unreadable`() {
        assertEquals("unreadable", PeopleTileRules.skipReason(opened = false, width = 0, height = 0, decoded = false))
        // Unreadable wins: nothing was read, so nothing else is known about it.
        assertEquals("unreadable", PeopleTileRules.skipReason(opened = false, width = 720, height = 720, decoded = true))
    }

    @Test
    fun `bytes that are not an image are skipped`() {
        assertEquals("not an image", PeopleTileRules.skipReason(opened = true, width = -1, height = -1, decoded = false))
        assertEquals("not an image", PeopleTileRules.skipReason(opened = true, width = 0, height = 480, decoded = false))
        assertEquals("not an image", PeopleTileRules.skipReason(opened = true, width = 480, height = 0, decoded = false))
    }

    @Test
    fun `an image whose bounds read and whose pixels do not is skipped as a failed decode`() {
        assertEquals("decode failed", PeopleTileRules.skipReason(opened = true, width = 720, height = 720, decoded = false))
    }

    // ------------------------------------------------------------------------------------------- R3 A9's timings

    @Test
    fun `the event is A9's - 333 ms out, 583 ms in, 1880 ms from the out's start to the in's settle, every 7700 ms`() {
        assertEquals(333, PeopleTileRules.OUT_MS)
        assertEquals(583, PeopleTileRules.IN_MS)
        assertEquals(1880, PeopleTileRules.EVENT_MS)
        assertEquals(7700, PeopleTileRules.PERIOD_MS)
        assertEquals(PeopleTileRules.EVENT_MS, PeopleTileRules.IN_START_MS + PeopleTileRules.IN_MS)
        assertEquals(PeopleTileRules.EVENT_MS, PeopleTileRules.OUT_MS + PeopleTileRules.PAUSE_MS + PeopleTileRules.IN_MS)
        assertTrue("the event ends before the next begins", PeopleTileRules.EVENT_MS < PeopleTileRules.PERIOD_MS)
    }

    @Test
    fun `events are a period apart, start to start`() {
        assertEquals(7700L, PeopleTileRules.waitMs(nowMs = 10_000, lastEventMs = 10_000))
        assertEquals(5_820L, PeopleTileRules.waitMs(nowMs = 11_880, lastEventMs = 10_000))
        // The first event, and one that fell due while Start was away, come a moment after the tile is back.
        assertEquals(PeopleTileRules.FIRST_EVENT_MS, PeopleTileRules.waitMs(nowMs = 10_000, lastEventMs = null))
        assertEquals(PeopleTileRules.FIRST_EVENT_MS, PeopleTileRules.waitMs(nowMs = 60_000, lastEventMs = 10_000))
    }

    // ------------------------------------------------------------------------------------------- the next photo

    @Test
    fun `the next photo is never the one on show while there is another`() {
        for (count in 2..6) for (current in 0 until count) for (pick in 0 until count) {
            val next = PeopleTileRules.next(current, count) { pick % it }
            assertTrue("in range", next in 0 until count)
            assertNotEquals("count=$count current=$current pick=$pick", current, next)
        }
    }

    @Test
    fun `every other photo can come next`() {
        val reached = (0 until 3).map { pick -> PeopleTileRules.next(0, 4) { pick } }.toSet()
        assertEquals(setOf(1, 2, 3), reached)
    }

    @Test
    fun `one photo leaves and comes back, and no photo is no event`() {
        assertEquals(0, PeopleTileRules.next(0, 1) { 0 })
        assertEquals(-1, PeopleTileRules.next(0, 0) { 0 })
        assertEquals(-1, PeopleTileRules.next(-1, 0) { 0 })
    }

    @Test
    fun `with nothing on show any photo can come first`() {
        assertEquals(setOf(0, 1, 2), (0 until 3).map { pick -> PeopleTileRules.next(-1, 3) { pick } }.toSet())
        // A random source that misbehaves cannot index outside the photos.
        assertEquals(2, PeopleTileRules.next(-1, 3) { 99 })
        assertEquals(0, PeopleTileRules.next(-1, 3) { -5 })
    }
}
