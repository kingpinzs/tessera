package app.tileshell.clock

import org.junit.Assert.assertEquals
import org.junit.Test

/** A timer that ended while the phone was off says so on its ring notification (Decisions "Clock rules"; T15-53; E6). */
class RingBodyTest {
    private fun timerRing(endedOff: Boolean) = RingService.Ring(
        true, listOf("t1"), mapOf("t1" to 0L), listOf("Minute"), "0:01:00", 0, RingService.Surface.NOTIFICATION, endedOff,
    )

    @Test fun `an overdue timer's body carries the ended-while-off line`() {
        assertEquals("Minute · 0:01:00 · Timer ended while the phone was off", timerRing(true).body)
    }

    @Test fun `an on-time timer's body is unchanged`() {
        assertEquals("Minute · 0:01:00", timerRing(false).body)
    }
}
