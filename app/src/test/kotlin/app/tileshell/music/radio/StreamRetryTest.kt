package app.tileshell.music.radio

import app.tileshell.music.radio.StreamRetry.Clock
import app.tileshell.music.radio.StreamRetry.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (Decisions "reconnect", r3 D8; Y4): the one reconnect clock. */
class StreamRetryTest {
    @Test fun `the back-off is 2, 4, 8, 16, 30 seconds`() {
        assertEquals(Step.Retry(2_000), StreamRetry.next(0, 0))
        assertEquals(Step.Retry(4_000), StreamRetry.next(2_000, 1))
        assertEquals(Step.Retry(8_000), StreamRetry.next(6_000, 2))
        assertEquals(Step.Retry(16_000), StreamRetry.next(14_000, 3))
        assertEquals(Step.Retry(30_000), StreamRetry.next(20_000, 4))
        // Past the fifth it stays at 30 s, while one still fits the window.
        assertEquals(Step.Retry(30_000), StreamRetry.next(1_000, 5))
        assertEquals(Step.Retry(30_000), StreamRetry.next(1_000, 99))
        assertEquals(Step.Retry(2_000), StreamRetry.next(0, -3))
    }

    @Test fun `it gives up at 60 000 ms - at once when the window is over, at the window's end when no attempt fits`() {
        assertEquals(60_000L, StreamRetry.WINDOW_MS)
        assertEquals(Step.GiveUp(0), StreamRetry.next(60_000, 0))
        assertEquals(Step.GiveUp(0), StreamRetry.next(61_234, 3))
        assertEquals(Step.GiveUp(0), StreamRetry.next(Long.MAX_VALUE, 0))
        // 31 s in, the next wait is 30 s: it would end after the window, so the give-up comes at T0 + 60 s instead.
        assertEquals(Step.GiveUp(29_000), StreamRetry.next(31_000, 4))
        assertEquals(Step.GiveUp(30_000), StreamRetry.next(30_000, 4))
        assertEquals(Step.Retry(30_000), StreamRetry.next(29_999, 4))
        assertEquals(Step.GiveUp(1_000), StreamRetry.next(59_000, 0))
        assertEquals(Step.Retry(2_000), StreamRetry.next(57_999, 0))
        // A clock that went backwards is the start of the window, not before it.
        assertEquals(Step.Retry(2_000), StreamRetry.next(-500, 0))
    }

    @Test fun `no retry is ever scheduled past the window, and every give-up lands on it`() {
        for (since in 0L..70_000L step 250) for (attempt in 0..8) {
            when (val step = StreamRetry.next(since, attempt)) {
                is Step.Retry -> assertTrue("$since/$attempt", since + step.delayMs < StreamRetry.WINDOW_MS)
                is Step.GiveUp -> assertEquals("$since/$attempt", maxOf(since, StreamRetry.WINDOW_MS), since + step.inMs)
            }
        }
    }

    @Test fun `lost is written on the first load error, once`() {
        val (first, write) = StreamRetry.onLoadError(Clock(), nowMs = 10_000)
        assertTrue(write)
        assertEquals(Clock(lostAtMs = 10_000, attempt = 0), first)
        assertTrue(first.lost)
        // ExoPlayer's own six retries each report a load error: T0 does not move and the line is not written again.
        var clock = first
        for (now in listOf(11_000L, 12_000L, 14_000L, 17_000L, 21_000L)) {
            val (next, again) = StreamRetry.onLoadError(clock, now)
            assertFalse("$now", again)
            assertEquals(first, next)
            clock = next
        }
        assertEquals("stream: lost, retrying", StreamLine.LOST)
    }

    @Test fun `the whole schedule off one clock - player errors at T0 plus 15 s onward, then the give-up`() {
        var clock = StreamRetry.onLoadError(Clock(), 100_000).first
        val steps = ArrayList<Step>()
        var now = 115_000L
        while (true) {
            val (next, step) = StreamRetry.onPlayerError(clock, now)
            steps += step
            clock = next
            if (step is Step.GiveUp) break
            now += (step as Step.Retry).delayMs
        }
        // 15 s: +2 → 17, +4 → 21, +8 → 29, +16 → 45; at 45 s the 30-s wait does not fit: give up 15 s later, at T0 + 60 s.
        assertEquals(listOf(Step.Retry(2_000), Step.Retry(4_000), Step.Retry(8_000), Step.Retry(16_000), Step.GiveUp(15_000)), steps)
        assertEquals(Clock(100_000, 4), clock)
        assertEquals("stream: gave up after 60000 ms", StreamLine.gaveUp())
    }

    @Test fun `a player error with no load error before it starts the clock itself`() {
        val (clock, step) = StreamRetry.onPlayerError(Clock(), 5_000)
        assertEquals(Step.Retry(2_000), step)
        assertEquals(Clock(5_000, 1), clock)
    }

    @Test fun `playing again reports how long it was lost, and nothing when it never was`() {
        assertNull(StreamRetry.reconnectedAfter(Clock(), 9_000))
        assertEquals(21_500L, StreamRetry.reconnectedAfter(Clock(100_000, 3), 121_500))
        assertEquals(0L, StreamRetry.reconnectedAfter(Clock(100_000, 0), 99_000))
        assertEquals("stream: reconnected after 21500 ms", StreamLine.reconnected(21_500))
    }

    @Test fun `the service's own wake lock outlasts the window by a margin, and the two texts are the doc's`() {
        assertEquals(65_000L, StreamRetry.WAKE_LOCK_MS)
        assertEquals("Reconnecting…", StreamRetry.RECONNECTING)
        assertEquals("This station isn't answering", StreamRetry.NOT_ANSWERING)
    }
}
