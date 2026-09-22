package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sleep timer's arithmetic and what the `•••` menu says about it (phase 10 build task 9).
 *
 * The part that actually pauses the music is the service's, and is proved on the device by waiting
 * for it; this is the part that has to be right about minutes and clocks.
 */
class SleepTimerTest {

    private val now = 1_000_000L

    @Test
    fun `a minutes choice arms a deadline that many minutes out on the same clock`() {
        assertEquals(now + 15 * 60_000L, SleepTimer.deadline(now, 15))
        assertEquals(now + 60 * 60_000L, SleepTimer.deadline(now, 60))
    }

    @Test
    fun `off and end-of-track arm no deadline, because neither is a time`() {
        assertEquals(0L, SleepTimer.deadline(now, SleepTimer.OFF))
        assertEquals(0L, SleepTimer.deadline(now, SleepTimer.END_OF_TRACK))
    }

    @Test
    fun `minutes left round UP, so a running timer never claims zero`() {
        assertEquals(15, SleepTimer.minutesLeft(now, now + 14 * 60_000L + 10_000L))
        assertEquals(1, SleepTimer.minutesLeft(now, now + 20_000L))
        assertEquals(15, SleepTimer.minutesLeft(now, now + 15 * 60_000L))
    }

    @Test
    fun `a deadline that has passed has no minutes left`() {
        assertEquals(0, SleepTimer.minutesLeft(now, now))
        assertEquals(0, SleepTimer.minutesLeft(now, now - 5_000L))
    }

    @Test
    fun `the menu entry says what the timer is doing`() {
        assertEquals("Sleep timer", SleepTimer.menuLabel(now, 0L, false))
        assertEquals("Sleep timer: 15 minutes left", SleepTimer.menuLabel(now, now + 15 * 60_000L, false))
        assertEquals("Sleep timer: 1 minute left", SleepTimer.menuLabel(now, now + 30_000L, false))
        assertEquals("Sleep timer: end of this track", SleepTimer.menuLabel(now, 0L, true))
    }

    @Test
    fun `an expired deadline reads as no timer rather than as zero minutes`() {
        assertEquals("Sleep timer", SleepTimer.menuLabel(now, now - 1L, false))
    }

    @Test
    fun `the choices are the quarter hours and the end of the track`() {
        assertEquals(listOf(15, 30, 45, 60, SleepTimer.END_OF_TRACK), SleepTimer.Choice.entries.map { it.minutes })
    }

    @Test
    fun `the equaliser entry names the preset in use, or says off`() {
        val names = listOf("Normal", "Classical", "Rock")
        assertEquals("Equaliser: Rock", Equaliser.menuLabel(2, names))
        assertEquals("Equaliser: off", Equaliser.menuLabel(Equaliser.OFF, names))
        // A preset index the device does not have reads as off, not as a crash.
        assertEquals("Equaliser: off", Equaliser.menuLabel(9, names))
    }

    // ---- preset names, as the device's effect library hands them over ----

    @Test
    fun `a preset name padded with NULs is cut at the first one`() {
        // What MUSIC9's first run found on the emulator's AOSP equaliser.
        assertEquals("Rock", Equaliser.cleanName("Rock\u0000\u0000\u0000\u0000", 0))
        assertEquals("Heavy Metal", Equaliser.cleanName("Heavy Metal\u0000junk", 3))
    }

    @Test
    fun `a clean name is left alone apart from surrounding space`() {
        assertEquals("Normal", Equaliser.cleanName("  Normal ", 0))
    }

    @Test
    fun `a name that is nothing but padding still gets something to show, by position`() {
        assertEquals("Preset 4", Equaliser.cleanName("\u0000\u0000", 3))
        assertEquals("Preset 1", Equaliser.cleanName(null, 0))
        assertEquals("Preset 2", Equaliser.cleanName("   ", 1))
    }
}
