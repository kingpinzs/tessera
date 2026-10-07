package app.tileshell.music

import app.tileshell.music.catalogue.RateSpacing
import app.tileshell.music.catalogue.RequestGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (r3 D17): MusicBrainz's one request a second, as the rule that spaces the catalogue's request starts. */
class RateSpacingTest {
    @Test fun `request starts are at least 1000 ms apart`() {
        assertEquals(1000L, RateSpacing.MIN_GAP_MS)
        assertEquals("the first request starts at once", 0L, RateSpacing.waitMs(null, 5_000))
        assertEquals(1000L, RateSpacing.waitMs(5_000, 5_000))
        assertEquals(999L, RateSpacing.waitMs(5_000, 5_001))
        assertEquals(1L, RateSpacing.waitMs(5_000, 5_999))
        assertEquals(0L, RateSpacing.waitMs(5_000, 6_000))
        assertEquals(0L, RateSpacing.waitMs(5_000, 60_000))
    }

    @Test fun `a clock that went backwards waits the whole gap, never longer`() {
        assertEquals(1000L, RateSpacing.waitMs(5_000, 4_999))
        assertEquals(1000L, RateSpacing.waitMs(5_000, 0))
        assertEquals(1000L, RateSpacing.waitMs(Long.MAX_VALUE, Long.MIN_VALUE))
        assertEquals(0L, RateSpacing.waitMs(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test fun `five back-to-back submits start a second apart, one at a time`() {
        var clock = 10_000L
        val starts = mutableListOf<Long>()
        var inFlight = 0
        val gate = RequestGate({ clock }) { clock += it }
        repeat(5) {
            gate.run {
                inFlight++
                assertEquals("one request in flight", 1, inFlight)
                starts += clock
                clock += 30   // the request itself takes a little
                inFlight--
            }
        }
        assertEquals(listOf(10_000L, 11_000L, 12_000L, 13_000L, 14_000L), starts)
        // A submit long after the last one starts at once.
        clock += 5_000
        gate.run { starts += clock }
        assertEquals(19_030L, starts.last())
    }

    @Test fun `submits from several threads still run one at a time, a gap apart`() {
        val lock = Any()
        var clock = 0L
        val starts = mutableListOf<Long>()
        var inFlight = 0
        var overlapped = false
        val gate = RequestGate({ synchronized(lock) { clock } }) { synchronized(lock) { clock += it } }
        val threads = List(5) {
            Thread {
                gate.run {
                    synchronized(lock) { inFlight++; if (inFlight > 1) overlapped = true; starts += clock }
                    Thread.sleep(5)
                    synchronized(lock) { inFlight-- }
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertTrue("never two in flight", !overlapped)
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L), starts.sorted())
    }
}
