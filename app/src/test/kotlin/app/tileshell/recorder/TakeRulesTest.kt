package app.tileshell.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The take's clock and markers (pause excluded, E30), the storage floor (T15-27, E19), and the two edit
 * rules whose refusals the pages show (rename U4, trim U3).
 */
class TakeRulesTest {

    private val rate = RecorderAudio.SAMPLE_RATE

    private fun TakeClock.record(ms: Long) {
        // In 1024-sample reads, as the capture loop delivers them.
        var left = ms * rate / 1000
        while (left > 0) {
            val n = minOf(1024L, left).toInt()
            onSamples(n)
            left -= n
        }
    }

    // ---- markers, pause excluded -----------------------------------------------------------------

    @Test fun `E30's take - 3 s, paused 4 s, flags 1 s and 2 s after the resume read 4 s and 5 s`() {
        val c = TakeClock()
        c.record(3_000)
        assertTrue(c.pause(TakeClock.Pause.USER))
        c.record(4_000) // samples arriving while paused are not in the take
        assertTrue(c.resume(inCall = false))
        c.record(1_000)
        val first = c.flag()
        c.record(1_000)
        val second = c.flag()
        c.record(1_000)
        assertEquals(4_000.0, first.toDouble(), 30.0)
        assertEquals(5_000.0, second.toDouble(), 30.0)
        assertEquals(6_000.0, c.elapsedMs.toDouble(), 30.0)
        assertEquals(listOf(first, second), c.markers)
    }

    @Test fun `while paused no time passes and the timer holds`() {
        val c = TakeClock()
        c.record(2_000)
        c.pause(TakeClock.Pause.USER)
        val held = c.elapsedMs
        assertFalse(c.onSamples(44_100))
        assertEquals(held, c.elapsedMs)
    }

    @Test fun `a flag while paused marks the pause point`() {
        val c = TakeClock()
        c.record(2_000)
        c.pause(TakeClock.Pause.USER)
        c.record(3_000)
        assertEquals(2_000.0, c.flag().toDouble(), 30.0)
    }

    @Test fun `the first pause reason stands until the take resumes`() {
        val c = TakeClock()
        assertTrue(c.pause(TakeClock.Pause.CALL))
        assertFalse(c.pause(TakeClock.Pause.USER))
        assertEquals(TakeClock.Pause.CALL, c.paused)
    }

    @Test fun `a call-paused take will not resume during the call, and resumes after it`() {
        val c = TakeClock()
        c.pause(TakeClock.Pause.CALL)
        assertFalse(c.resume(inCall = true))
        assertEquals(TakeClock.Pause.CALL, c.paused)
        assertTrue(c.resume(inCall = false))
        assertNull(c.paused)
    }

    @Test fun `resuming a take that records does nothing`() {
        assertFalse(TakeClock().resume(inCall = false))
    }

    @Test fun `the pause words are the diagnostics line's`() {
        assertEquals(listOf("user", "call", "silenced"), TakeClock.Pause.entries.map { it.word })
    }

    @Test fun `recovered markers are restored`() {
        val c = TakeClock()
        c.restoreMarkers(listOf(1_000L, 2_500L))
        assertEquals(listOf(1_000L, 2_500L), c.markers)
    }

    // ---- the storage floor ----------------------------------------------------------------------------

    @Test fun `a take starts only above 50 MB of allocatable space`() {
        val mb = 1024L * 1024L
        assertTrue(StorageFloor.canStart(51 * mb))
        assertFalse(StorageFloor.canStart(50 * mb))
        assertFalse(StorageFloor.canStart(10 * mb))
    }

    @Test fun `a running take stops at the floor`() {
        val mb = 1024L * 1024L
        assertFalse(StorageFloor.mustStop(50 * mb + 1))
        assertTrue(StorageFloor.mustStop(50 * mb))
        assertTrue(StorageFloor.mustStop(0))
        assertEquals(5_000L, StorageFloor.CHECK_EVERY_MS)
        assertEquals("Not enough space", StorageFloor.NOTICE)
    }

    // ---- rename (U4) -----------------------------------------------------------------------------------

    @Test fun `an ordinary name is accepted, trimmed`() {
        assertEquals(RenameRule.Result.Ok("Standup"), RenameRule.check("  Standup "))
        assertEquals(RenameRule.Result.Ok("Tallenne ä (2)"), RenameRule.check("Tallenne ä (2)"))
    }

    @Test fun `names MediaStore would mangle or hide are refused`() {
        for (bad in listOf("", "   ", "a/b", "a\\b", "what?", "x:y", "a*", "<b>", "p|q", "\"q\"", ".hidden", "tab\there", "x\u007F")) {
            assertTrue(bad, RenameRule.check(bad) is RenameRule.Result.Refused)
        }
    }

    @Test fun `a name too long for one path segment is refused`() {
        assertTrue(RenameRule.check("a".repeat(251)) is RenameRule.Result.Ok)
        assertTrue(RenameRule.check("a".repeat(252)) is RenameRule.Result.Refused)
    }

    // ---- trim (U3) -------------------------------------------------------------------------------------

    @Test fun `a cut inside the recording is a cut`() {
        assertEquals(TrimRule.Result.Cut(1_000, 3_000), TrimRule.check(1_000, 3_000, 5_000))
    }

    @Test fun `a zero-length cut is refused`() {
        assertTrue(TrimRule.check(2_000, 2_000, 5_000) is TrimRule.Result.Refused)
        assertTrue(TrimRule.check(2_000, 2_050, 5_000) is TrimRule.Result.Refused)
        assertTrue(TrimRule.check(3_000, 1_000, 5_000) is TrimRule.Result.Refused)
    }

    @Test fun `keeping the whole recording is no cut`() {
        assertEquals(TrimRule.Result.Whole, TrimRule.check(0, 5_000, 5_000))
        assertEquals(TrimRule.Result.Whole, TrimRule.check(-10, 9_000, 5_000))
    }

    @Test fun `markers inside the kept part move to the new clock and the rest go`() {
        assertEquals(listOf(500L, 1_500L), TrimRule.shiftMarkers(listOf(200L, 1_500L, 2_500L, 3_000L, 4_000L), 1_000, 3_000))
    }
}
