package app.tileshell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `[motion]` line Voice Recorder writes for its flyout grow and its record-state cut (phase 15 C-5 / C-31):
 * `<name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms> frames=<n> maxGapMs=<ms>`, every number measured from t0.
 */
class MotionTraceTest {

    private val line = Regex("^[a-z_]+ t0=\\d+ peak=\\d+ overshoot=\\d+ settle=\\d+ frames=\\d+ maxGapMs=\\d+$")

    private fun ns(ms: Long) = ms * 1_000_000L

    @Test fun `a trace with no frame is all zeros in the contract's form`() {
        val m = MotionTrace("flyout", 1000).message()
        assertEquals("flyout t0=1000 peak=0 overshoot=0 settle=0 frames=0 maxGapMs=0", m)
        assertTrue(m, line.matches(m))
    }

    @Test fun `peak, settle, frames and the longest gap are measured from t0`() {
        val t = MotionTrace("flyout", 1000)
        t.frame(ns(1016), 0f)
        t.frame(ns(1033), 0.5f)
        t.frame(ns(1050), 1f)
        val m = t.message()
        assertEquals("flyout t0=1000 peak=50 overshoot=0 settle=50 frames=3 maxGapMs=17", m)
        assertTrue(m, line.matches(m))
    }

    @Test fun `overshoot is the largest value past 1, in percent, at its own frame`() {
        val t = MotionTrace("flyout", 0)
        t.frame(ns(16), 0f)
        t.frame(ns(33), 1.1f)
        t.frame(ns(50), 1f)
        assertEquals("flyout t0=0 peak=33 overshoot=10 settle=33 frames=3 maxGapMs=17", t.message())
    }

    @Test fun `a motion that never settles reports its last frame as the settle`() {
        val t = MotionTrace("rec_state", 500)
        t.frame(ns(516), 0f)
        t.frame(ns(600), 0f)
        assertEquals("rec_state t0=500 peak=16 overshoot=0 settle=100 frames=2 maxGapMs=84", t.message())
    }

    @Test fun `the record cut is one frame at 1 after frames at 0`() {
        val t = MotionTrace("rec_state", 100)
        t.frame(ns(116), 0f)
        t.frame(ns(133), 0f)
        t.frame(ns(900), 1f)
        assertEquals("rec_state t0=100 peak=800 overshoot=0 settle=800 frames=3 maxGapMs=767", t.message())
        assertEquals(3, t.frames)
    }
}
