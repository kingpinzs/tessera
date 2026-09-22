package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crossfade's arithmetic (phase 10, E17). The machine that runs two players is proved on the device
 * by counting AudioFlinger's tracks; this is the part that has to be right about lengths, curves and
 * when a fade is allowed at all.
 */
class CrossfadeTest {

    // ---- how long a fade is ----

    @Test
    fun `a fade is the setting when both tracks are long enough`() {
        assertEquals(5_000L, Crossfade.fadeLength(5_000, 90_000L, 90_000L))
    }

    @Test
    fun `a fade is never longer than half of either track`() {
        assertEquals(4_000L, Crossfade.fadeLength(12_000, 8_000L, 90_000L))
        assertEquals(3_000L, Crossfade.fadeLength(12_000, 90_000L, 6_000L))
    }

    @Test
    fun `a fade that would be under a second is no fade at all`() {
        assertEquals(0L, Crossfade.fadeLength(5_000, 1_500L, 90_000L))
    }

    @Test
    fun `off, or a duration not known yet, is no fade`() {
        assertEquals(0L, Crossfade.fadeLength(Crossfade.OFF, 90_000L, 90_000L))
        assertEquals(0L, Crossfade.fadeLength(5_000, 0L, 90_000L))
        assertEquals(0L, Crossfade.fadeLength(5_000, 90_000L, -1L))
    }

    @Test
    fun `a fade that starts late is shortened to what is left, never overrun`() {
        assertEquals(2_500L, Crossfade.startLength(5_000L, 2_500L))
        assertEquals(5_000L, Crossfade.startLength(5_000L, 5_000L))
        assertEquals(0L, Crossfade.startLength(5_000L, 600L))
    }

    // ---- when a fade is allowed ----

    @Test
    fun `a fade needs the setting on and a next track`() {
        assertTrue(Crossfade.eligible(5_000, hasNext = true, repeatOne = false, sleepAtEndOfTrack = false))
        assertFalse(Crossfade.eligible(Crossfade.OFF, hasNext = true, repeatOne = false, sleepAtEndOfTrack = false))
        assertFalse(Crossfade.eligible(5_000, hasNext = false, repeatOne = false, sleepAtEndOfTrack = false))
    }

    @Test
    fun `repeat-one never fades a track into itself`() {
        assertFalse(Crossfade.eligible(5_000, hasNext = true, repeatOne = true, sleepAtEndOfTrack = false))
    }

    @Test
    fun `an armed end-of-track sleep timer means the music stops, so nothing fades`() {
        // A fade would leave the incoming track playing on after the pause at the end.
        assertFalse(Crossfade.eligible(5_000, hasNext = true, repeatOne = false, sleepAtEndOfTrack = true))
    }

    // ---- the curve ----

    @Test
    fun `the fade starts all outgoing and ends all incoming`() {
        val (out0, in0) = Crossfade.gains(0f)
        val (out1, in1) = Crossfade.gains(1f)
        assertEquals(1f, out0, 1e-6f); assertEquals(0f, in0, 1e-6f)
        assertEquals(0f, out1, 1e-6f); assertEquals(1f, in1, 1e-6f)
    }

    @Test
    fun `it is equal power all the way through, so the loudness does not dip in the middle`() {
        for (i in 0..20) {
            val (out, inc) = Crossfade.gains(i / 20f)
            assertEquals(1f, out * out + inc * inc, 1e-5f)
        }
        // At the midpoint two straight ramps would each be 0.5 (power 0.5, a 3 dB dip); these are 0.707 each.
        val (outMid, inMid) = Crossfade.gains(0.5f)
        assertEquals(0.7071f, outMid, 1e-3f)
        assertEquals(0.7071f, inMid, 1e-3f)
    }

    @Test
    fun `progress outside 0 to 1 is clamped rather than wrapping the curve`() {
        assertEquals(Crossfade.gains(0f), Crossfade.gains(-0.5f))
        assertEquals(Crossfade.gains(1f), Crossfade.gains(3f))
    }

    // ---- the handback ----

    @Test
    fun `two copies within the tolerance are close enough to swap between`() {
        assertTrue(Crossfade.handbackAligned(10_040L, 10_000L))
        assertTrue(Crossfade.handbackAligned(9_960L, 10_000L))
        assertFalse(Crossfade.handbackAligned(10_041L, 10_000L))
        assertFalse(Crossfade.handbackAligned(9_900L, 10_000L))
    }

    // ---- the setting ----

    @Test
    fun `the choices run from off to twelve seconds`() {
        assertEquals(listOf(0, 2_000, 5_000, 8_000, 12_000), Crossfade.Choice.entries.map { it.ms })
    }

    @Test
    fun `the menu entry says what is set, and anything unknown reads as off`() {
        assertEquals("Crossfade: off", Crossfade.menuLabel(0))
        assertEquals("Crossfade: 5 seconds", Crossfade.menuLabel(5_000))
        assertEquals("Crossfade: off", Crossfade.menuLabel(3_333))
    }
}
