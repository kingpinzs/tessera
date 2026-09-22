package app.tileshell.cortana.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the `:speech` process is allowed to drop its models.
 *
 * Both engines together are the largest allocation the shell ever makes, so an idle phone must not hold
 * them — but closing and reopening the session inside the window must not reload them either. The timer
 * is the whole rule, and it is checked again when it fires, so a client that came back wins the race.
 */
class IdleReleaseTimerTest {

    private val fiveMinutes = IdleReleaseTimer.IDLE_MILLIS

    @Test
    fun `five minutes is the phase's number`() {
        assertEquals(5L * 60L * 1000L, IdleReleaseTimer.IDLE_MILLIS)
    }

    @Test
    fun `a bound client never releases, however long it stays bound`() {
        val timer = IdleReleaseTimer()
        timer.bound()
        assertFalse(timer.idle())
        assertFalse(timer.shouldRelease(0L))
        assertFalse(timer.shouldRelease(fiveMinutes * 100))
        assertNull(timer.millisUntilRelease(fiveMinutes * 100))
    }

    @Test
    fun `a fresh timer holds nothing, so it cannot release something it never loaded`() {
        val timer = IdleReleaseTimer()
        assertFalse(timer.shouldRelease(Long.MAX_VALUE))
        assertNull(timer.millisUntilRelease(0L))
    }

    @Test
    fun `release happens at five minutes, not before`() {
        val timer = IdleReleaseTimer()
        val unboundAt = 1_000_000L
        timer.unbound(unboundAt)

        assertTrue(timer.idle())
        assertFalse(timer.shouldRelease(unboundAt))
        assertFalse(timer.shouldRelease(unboundAt + fiveMinutes - 1))
        assertTrue(timer.shouldRelease(unboundAt + fiveMinutes))
        assertTrue(timer.shouldRelease(unboundAt + fiveMinutes * 3))
    }

    @Test
    fun `the countdown is what status reports and it never goes negative`() {
        val timer = IdleReleaseTimer()
        val unboundAt = 500L
        timer.unbound(unboundAt)

        assertEquals(fiveMinutes, timer.millisUntilRelease(unboundAt))
        assertEquals(fiveMinutes - 1000L, timer.millisUntilRelease(unboundAt + 1000L))
        assertEquals(0L, timer.millisUntilRelease(unboundAt + fiveMinutes))
        assertEquals(0L, timer.millisUntilRelease(unboundAt + fiveMinutes * 10))
    }

    @Test
    fun `a client that re-binds inside the window cancels the release`() {
        val timer = IdleReleaseTimer()
        timer.unbound(0L)
        // The scheduled task fires at five minutes, but the session came back at four.
        timer.bound()
        assertFalse(timer.shouldRelease(fiveMinutes))
        assertNull(timer.millisUntilRelease(fiveMinutes))
    }

    @Test
    fun `unbinding again restarts the countdown from the later unbind`() {
        val timer = IdleReleaseTimer()
        timer.unbound(0L)
        timer.bound()
        timer.unbound(fiveMinutes)

        assertFalse(timer.shouldRelease(fiveMinutes + fiveMinutes - 1))
        assertTrue(timer.shouldRelease(fiveMinutes * 2))
    }

    @Test
    fun `a custom window is honoured, so the rule is the timer's and not the caller's`() {
        val timer = IdleReleaseTimer(idleMillis = 100L)
        timer.unbound(0L)
        assertFalse(timer.shouldRelease(99L))
        assertTrue(timer.shouldRelease(100L))
    }
}
