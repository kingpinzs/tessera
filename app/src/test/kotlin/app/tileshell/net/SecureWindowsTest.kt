package app.tileshell.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17, the owner's ruling D2 (a) 1: no screenshot, screen recording or Recents preview while a credential page shows.
 * The window's FLAG_SECURE follows a COUNT of the credential pages showing in it: set by the first to appear, cleared by
 * the last to leave, never by one leaving while another is still up, and nothing of it left once the last has gone.
 */
class SecureWindowsTest {
    /** A window: compared by identity, as an android.view.Window is. */
    private class Win

    @Test fun `a credential page appearing sets the flag`() {
        val windows = SecureWindows<Win>()
        val window = Win()
        assertEquals(SecureStep.SET, windows.shown(window))
        assertEquals(1, windows.showing(window))
    }

    @Test fun `the flag is cleared when the last credential page leaves`() {
        val windows = SecureWindows<Win>()
        val window = Win()
        windows.shown(window)
        assertEquals(SecureStep.CLEAR, windows.left(window))
        assertEquals(0, windows.showing(window))
    }

    @Test fun `a page leaving while a second credential page is up in the same window keeps the flag`() {
        val windows = SecureWindows<Win>()
        val window = Win()
        assertEquals(SecureStep.SET, windows.shown(window))
        assertEquals(SecureStep.SET, windows.shown(window))
        assertEquals("one left, one still showing", SecureStep.KEEP, windows.left(window))
        assertEquals(1, windows.showing(window))
        assertEquals("the last one left", SecureStep.CLEAR, windows.left(window))
        assertEquals(0, windows.showing(window))
    }

    @Test fun `each window is counted apart, so a page leaving one window never clears another's flag`() {
        val windows = SecureWindows<Win>()
        val hub = Win()
        val music = Win()
        windows.shown(hub)
        windows.shown(music)
        assertEquals(SecureStep.CLEAR, windows.left(hub))
        assertEquals(1, windows.showing(music))
        assertEquals(SecureStep.CLEAR, windows.left(music))
    }

    @Test fun `a configuration change that recreates the activity - the new window is set before the old page leaves the old one`() {
        val windows = SecureWindows<Win>()
        val old = Win()
        val new = Win()
        windows.shown(old)
        // The recreated activity composes the page in its new window; the old composition is disposed after.
        assertEquals(SecureStep.SET, windows.shown(new))
        assertEquals(SecureStep.CLEAR, windows.left(old))
        assertEquals("the new window's page is still counted", 1, windows.showing(new))
        assertEquals(SecureStep.CLEAR, windows.left(new))
    }

    @Test fun `the count never goes below zero - a leave with nothing counted leaves no debt for the next page`() {
        val windows = SecureWindows<Win>()
        val window = Win()
        assertEquals(SecureStep.CLEAR, windows.left(window))
        assertEquals(0, windows.showing(window))
        assertEquals(SecureStep.SET, windows.shown(window))
        assertEquals(1, windows.showing(window))
        windows.left(window)
        windows.left(window)
        assertEquals(0, windows.showing(window))
        assertEquals(SecureStep.SET, windows.shown(window))
        assertEquals("one page, one leave, flag off", SecureStep.CLEAR, windows.left(window))
    }

    @Test fun `each step has its own line, and no line carries a number`() {
        val lines = SecureStep.entries.map { SecureWindows.line(it) }
        assertTrue(lines.all { it.isNotBlank() })
        assertEquals(3, lines.toSet().size)
        assertFalse(lines.any { line -> line.any { it.isDigit() } })
        assertTrue(SecureWindows.line(SecureStep.SET).contains("blocked"))
        assertTrue(SecureWindows.line(SecureStep.CLEAR).contains("allowed"))
        assertTrue(SecureWindows.line(SecureStep.KEEP).contains("still blocked"))
    }
}
