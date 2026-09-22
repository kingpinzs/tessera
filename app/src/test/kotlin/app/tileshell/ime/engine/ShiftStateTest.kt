package app.tileshell.ime.engine

import app.tileshell.ime.engine.ShiftState.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shift and caps lock (phase 05 Decisions stand-in (4), H22; E12: twice 150 ms apart locks, twice
 * 500 ms apart does not) on both sides of the 300 ms window.
 */
class ShiftStateTest {

    private val shift = ShiftState(doubleTapMs = 300L)

    @Test
    fun `one tap shifts the next letter only`() {
        assertEquals(Mode.ONE_SHOT, shift.tapShift(1000))
        assertTrue(shift.shifted)
        assertFalse(shift.locked)
        assertTrue(shift.typeLetter())
        assertFalse(shift.shifted)
        assertFalse(shift.typeLetter())
    }

    @Test
    fun `a second tap within 300 ms locks caps`() {
        shift.tapShift(1000)
        assertEquals(Mode.CAPS_LOCK, shift.tapShift(1150))
        assertTrue(shift.locked)
        repeat(3) { assertTrue(shift.typeLetter()) }
        assertTrue(shift.locked)
    }

    @Test
    fun `exactly 300 ms still locks, 301 ms does not`() {
        shift.tapShift(1000)
        assertEquals(Mode.CAPS_LOCK, shift.tapShift(1300))
        val late = ShiftState(300L)
        late.tapShift(1000)
        assertEquals(Mode.OFF, late.tapShift(1301))
        assertFalse(late.shifted)
    }

    @Test
    fun `two taps 500 ms apart do not lock, they cancel the one-shot`() {
        shift.tapShift(1000)
        assertEquals(Mode.OFF, shift.tapShift(1500))
        assertFalse(shift.typeLetter())
    }

    @Test
    fun `tapping shift again ends caps lock`() {
        shift.tapShift(1000)
        shift.tapShift(1100)
        assertEquals(Mode.OFF, shift.tapShift(5000))
        assertFalse(shift.shifted)
        // The unlocking tap is a tap like any other: one more within the window is a double tap and
        // locks again (the ruling names no exception); one outside it is a fresh one-shot.
        assertEquals(Mode.CAPS_LOCK, shift.tapShift(5100))
        shift.tapShift(9000)
        assertEquals(Mode.ONE_SHOT, shift.tapShift(9500))
    }

    @Test
    fun `a letter between two taps breaks the double tap`() {
        shift.tapShift(1000)
        shift.typeLetter()
        assertEquals(Mode.ONE_SHOT, shift.tapShift(1100))
    }

    @Test
    fun `the double-tap timeout is the platform's, passed in`() {
        val slow = ShiftState(doubleTapMs = 500L)
        slow.tapShift(0)
        assertEquals(Mode.CAPS_LOCK, slow.tapShift(450))
    }

    @Test
    fun `auto-capitalisation arms a one-shot without disturbing caps lock`() {
        shift.autoCapitalise()
        assertEquals(Mode.ONE_SHOT, shift.mode)
        assertTrue(shift.typeLetter())
        assertFalse(shift.typeLetter())
        shift.tapShift(1000)
        shift.tapShift(1100)
        shift.autoCapitalise()
        assertEquals(Mode.CAPS_LOCK, shift.mode)
    }

    @Test
    fun `a tap after auto-capitalisation lowers the case, and a second quick tap still locks`() {
        shift.autoCapitalise()
        assertEquals(Mode.OFF, shift.tapShift(1000))
        assertEquals(Mode.CAPS_LOCK, shift.tapShift(1100))
        val slow = ShiftState(300L)
        slow.autoCapitalise()
        assertEquals(Mode.OFF, slow.tapShift(1000))
        assertEquals(Mode.ONE_SHOT, slow.tapShift(1500))
    }

    @Test
    fun `switching fields resets everything, caps lock included`() {
        // Edge cases: "caps lock then switching fields".
        shift.tapShift(1000)
        shift.tapShift(1100)
        shift.reset()
        assertEquals(Mode.OFF, shift.mode)
        assertFalse(shift.typeLetter())
        // the tap before the reset does not pair with one after it
        shift.tapShift(2000)
        shift.reset()
        assertEquals(Mode.ONE_SHOT, shift.tapShift(2100))
    }
}
