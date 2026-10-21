package app.tileshell.music

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ledger L18-5 (adversarial pass 3, P3-1): which index a controller may hand Music's player. An index the list does not
 * have must never reach it — ExoPlayer throws on the launcher's main thread for one it cannot take, and the launcher
 * dies (RUN on the emulator, 2026-10-20: a legacy `skipToQueueItem(2147483648)`).
 */
class MusicIndexRuleTest {
    /**
     * What Media3 1.9.0's legacy stub hands the player for `skipToQueueItem(queueId)` (MediaSessionLegacyStub
     * .onSkipToQueueItem, read from the bytecode: `queueId < 0` is refused, else `l2i` into `seekToDefaultPosition`).
     */
    private fun legacy(queueId: Long): Int? = if (queueId < 0L) null else queueId.toInt()

    /** The values at which ExoPlayer 1.9.0's index checks sit (`checkArgument(index >= 0)`, BasePlayer's `index + 1`). */
    private val edges = listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -2, -1, 0, 1, 2, 3, 4, 5, Int.MAX_VALUE - 1, Int.MAX_VALUE)

    @Test fun `the legacy queue ids of the device run become these indices`() {
        assertEquals(Int.MIN_VALUE, legacy(2_147_483_648L))  // 2^31: the crash
        assertEquals(-2, legacy(4_294_967_294L))             // 2^32 - 2: the crash
        assertEquals(C.INDEX_UNSET, legacy(4_294_967_295L))  // 2^32 - 1: -1, which ExoPlayer itself ignores (the control)
        assertEquals(2, legacy(2L))                          // a real queue item
        assertEquals(null, legacy(-1L))                      // refused by the stub itself
    }

    @Test fun `a legacy skip to any of the device run's queue ids names no item, whatever the queue`() {
        for (count in listOf(0, 1, 3, 500)) {
            for (id in listOf(2_147_483_648L, 4_294_967_294L, 4_294_967_295L, Long.MAX_VALUE, 1L shl 40, (1L shl 32) + 1)) {
                val index = legacy(id)!!
                if (index in 0 until count) continue // (1 << 32) + 1 wraps to 1: a real item of a long enough queue
                assertFalse("queue id $id -> index $index, a queue of $count", MusicIndexRule.item(index, count))
            }
        }
        assertTrue(MusicIndexRule.item(legacy(2L)!!, 3))
        assertTrue(MusicIndexRule.item(legacy((1L shl 32) + 1)!!, 3))
    }

    @Test fun `an item - 0 until the count (N - 1 is the last), and an empty queue has none`() {
        assertTrue(MusicIndexRule.item(0, 1))
        assertTrue(MusicIndexRule.item(0, 3))
        assertTrue(MusicIndexRule.item(2, 3))                 // N - 1
        assertFalse(MusicIndexRule.item(3, 3))                // N
        assertFalse(MusicIndexRule.item(4, 3))
        assertFalse(MusicIndexRule.item(-1, 3))               // C.INDEX_UNSET
        assertFalse(MusicIndexRule.item(-2, 3))
        assertFalse(MusicIndexRule.item(Int.MIN_VALUE, 3))
        assertFalse(MusicIndexRule.item(Int.MAX_VALUE, 3))    // a remove / replace / move of it: BasePlayer's index + 1 wraps
        for (index in edges) assertFalse("empty queue, $index", MusicIndexRule.item(index, 0))
    }

    @Test fun `a place - from 0 on, and past the end is the end (the Player contract)`() {
        assertTrue(MusicIndexRule.place(0))
        assertTrue(MusicIndexRule.place(3))
        assertTrue(MusicIndexRule.place(Int.MAX_VALUE))
        assertFalse(MusicIndexRule.place(-1))
        assertFalse(MusicIndexRule.place(-2))
        assertFalse(MusicIndexRule.place(Int.MIN_VALUE))
    }

    @Test fun `a range - starts in the list or at its end, never runs backwards, and its end past the list is the list's end`() {
        assertTrue(MusicIndexRule.range(0, Int.MAX_VALUE, 3))  // a Media3 controller's clearMediaItems()
        assertTrue(MusicIndexRule.range(0, Int.MAX_VALUE, 0))
        assertTrue(MusicIndexRule.range(1, 2, 3))
        assertTrue(MusicIndexRule.range(2, 2, 3))
        assertTrue(MusicIndexRule.range(3, 4, 3))              // at the end: a replace there appends
        assertTrue(MusicIndexRule.range(0, 0, 0))
        assertFalse(MusicIndexRule.range(4, 5, 3))             // starts past the end
        assertFalse(MusicIndexRule.range(1, 1, 0))
        assertFalse(MusicIndexRule.range(-1, 2, 3))
        assertFalse(MusicIndexRule.range(2, 1, 3))             // backwards
        assertFalse(MusicIndexRule.range(Int.MAX_VALUE, Int.MIN_VALUE, 3)) // the stub's own index + 1, wrapped
        assertFalse(MusicIndexRule.range(Int.MIN_VALUE, 0, 3))
    }

    /** The guarantee itself: whatever the rule lets through, ExoPlayer 1.9.0 takes without throwing, and an item exists. */
    @Test fun `whatever is allowed, the player can take - over every edge value and queue length`() {
        for (count in 0..5) for (a in edges) {
            if (MusicIndexRule.item(a, count)) {
                assertTrue("item $a of $count", a >= 0 && a < count)
                assertTrue("item $a: its + 1 must not wrap", a + 1 > a)
            }
            if (MusicIndexRule.place(a)) assertTrue("place $a", a >= 0)
            for (b in edges) if (MusicIndexRule.range(a, b, count)) assertTrue("range $a..$b of $count", a >= 0 && b >= a && a <= count)
        }
    }

    @Test fun `the line - the caller's uid, the call as it came, the queue's length`() {
        assertEquals(
            "controller uid 10187: seekToDefaultPosition(-2147483648) refused, the queue has 3 item(s)",
            MusicIndexRule.line(10187, "seekToDefaultPosition(-2147483648)", 3),
        )
        assertEquals("controller uid unknown: seekTo(5, 0) refused, the queue has 0 item(s)", MusicIndexRule.line(null, "seekTo(5, 0)", 0))
    }
}
