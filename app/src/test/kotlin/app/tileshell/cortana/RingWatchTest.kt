package app.tileshell.cortana

import app.tileshell.clock.RingService
import app.tileshell.clock.RingService.Surface
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L13-10: which rings make Tess step aside. The ring's state replays its current value to a new collector and is
 * re-published when the ring's surface changes; only a ring that starts after Tess opened counts.
 */
class RingWatchTest {
    private fun ring(id: String, surface: Surface = Surface.OVERLAY) =
        RingService.Ring(timer = false, ids = listOf(id), occurrences = emptyMap(), names = emptyList(), timeText = "", snoozeMinutes = 10, surface = surface)

    private fun started(vararg values: RingService.Ring?) =
        runBlocking { flowOf(*values).ringsStartingAfterFirst().toList().map { it.logId } }

    @Test
    fun aRingAlreadyUpWhenTessOpensDoesNotCount() {
        assertEquals(emptyList<String>(), started(ring("a")))
    }

    @Test
    fun aRingThatStartsAfterSheOpenedCounts() {
        assertEquals(listOf("a"), started(null, ring("a")))
    }

    // Reviewer A, L13-10 note 2: the screen going off or the keyguard going away re-publishes the SAME ring with another
    // surface. Tess opened over the locked toast must not be closed by her own Unlock.
    @Test
    fun theSameRingOnAnotherSurfaceIsNotANewRing() {
        assertEquals(emptyList<String>(), started(ring("a", Surface.LOCKED), ring("a", Surface.OVERLAY)))
        assertEquals(listOf("a"), started(null, ring("a", Surface.LOCKED), ring("a", Surface.OVERLAY)))
    }

    @Test
    fun aRingThatEndsAndAnotherThatStartsCounts() {
        assertEquals(listOf("b"), started(ring("a"), null, ring("b")))
        // A snoozed alarm ringing again is a new ring, although its id is the same.
        assertEquals(listOf("a"), started(ring("a"), null, ring("a")))
    }
}
