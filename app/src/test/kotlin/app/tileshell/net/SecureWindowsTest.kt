package app.tileshell.net

import org.junit.Assert.assertEquals
import java.lang.ref.WeakReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `a configuration change that recreates the activity - each window follows its own count, whichever goes first`() {
        // On Android the old activity is torn down first — its composition disposed, the page leaving the old window —
        // and the recreated activity then composes the page in its new window.
        val windows = SecureWindows<Win>()
        val old = Win()
        val new = Win()
        windows.shown(old)
        assertEquals(SecureStep.CLEAR, windows.left(old))
        assertEquals(SecureStep.SET, windows.shown(new))
        assertEquals("the new window's page is counted", 1, windows.showing(new))
        // The other order gives the same result: the windows are counted apart.
        val older = Win()
        val newer = Win()
        windows.shown(older)
        assertEquals(SecureStep.SET, windows.shown(newer))
        assertEquals(SecureStep.CLEAR, windows.left(older))
        assertEquals("the new window's page is still counted", 1, windows.showing(newer))
        assertEquals(SecureStep.CLEAR, windows.left(newer))
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

    @Test fun `the count holds no window whose last page has left, and lets go of a window that is gone`() {
        val windows = SecureWindows<Win>()
        val window = Win()
        windows.shown(window)
        windows.shown(window)
        windows.left(window)
        assertEquals("one page still showing", 1, windows.tracked)
        windows.left(window)
        assertEquals("the last page left: the window's entry is gone", 0, windows.tracked)
        windows.left(window)
        assertEquals("a leave with nothing counted adds none", 0, windows.tracked)
        // A window that goes away with a page still counted (its activity gone without the page's leave) is not held.
        val gone = shownThenDropped(windows)
        repeat(50) { if (gone.get() != null) { System.gc(); Thread.sleep(20) } }
        assertNull("the count let the window go", gone.get())
        assertEquals(0, windows.tracked)
    }

    /** A window counted in and then dropped by its owner: the count's own reference is the only one left. */
    private fun shownThenDropped(windows: SecureWindows<Win>): WeakReference<Win> {
        val window = Win()
        windows.shown(window)
        return WeakReference(window)
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
