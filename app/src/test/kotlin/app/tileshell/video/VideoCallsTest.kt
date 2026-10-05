package app.tileshell.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 trust review, B-1 (b): a call of the catalogue or the media server that throws something unexpected gives
 * its "did not work" value, and the line names the exception's class — never its message, which may quote a credential.
 */
class VideoCallsTest {
    @Test fun `a call that works gives its value and writes nothing`() {
        val lines = mutableListOf<String>()
        assertEquals(7, VideoCalls.guarded("catalogue search", -1, { lines += it }) { 7 })
        assertTrue(lines.isEmpty())
    }

    @Test fun `an unexpected exception is the fallback, and its message reaches no line`() {
        val lines = mutableListOf<String>()
        val got = VideoCalls.guarded("catalogue search", "fallback", { lines += it }) {
            throw IllegalArgumentException("Unexpected char 0x0d at 14 in Authorization value: Bearer SECRET-TOKEN-VALUE")
        }
        assertEquals("fallback", got)
        assertEquals(listOf("catalogue search: failed (IllegalArgumentException)"), lines)
        assertFalse(lines.any { it.contains("SECRET") || it.contains("Bearer") })
    }

    @Test fun `every kind of exception is caught at the call`() {
        val lines = mutableListOf<String>()
        for (e in listOf(IllegalStateException("s3cret"), NullPointerException("s3cret"), SecurityException("s3cret"), java.io.IOException("s3cret"))) {
            assertEquals(0, VideoCalls.guarded("server library", 0, { lines += it }) { throw e })
        }
        assertEquals(4, lines.size)
        assertTrue(lines.none { it.contains("s3cret") })
    }
}
