package app.tileshell.process

import app.tileshell.cortana.CortanaDestinationKey
import app.tileshell.cortana.CortanaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D3 (a): which process the shell is in, and what another of its processes may ask the launcher's ([ShellProcess]). */
class ShellProcessTest {
    private val pkg = "app.tileshell"

    @Test fun `only the process named after the package is the launcher's`() {
        assertTrue(ShellProcess.isLauncher("app.tileshell", pkg))
        for (other in listOf("app.tileshell:photosedit", "app.tileshell:camera", "app.tileshell:video", "app.tileshell:ime", "app.tileshell:", "app.tileshel", "app.tileshell.photosedit", "", null)) {
            assertFalse("$other", ShellProcess.isLauncher(other, pkg))
        }
    }

    @Test fun `a window asks the launcher's process for the theme only outside it, and only once this process holds a copy`() {
        // A warm `:photosedit` (the viewer or the editor opened before): its copy may be older than a change made since.
        assertTrue(ShellProcess.asksForTheme(launcher = false, holdsCopy = true))
        // A fresh one reads the file as it is now: the viewer's first open asks nothing more.
        assertFalse(ShellProcess.asksForTheme(launcher = false, holdsCopy = false))
        // The launcher's process holds THE theme.
        assertFalse(ShellProcess.asksForTheme(launcher = true, holdsCopy = true))
        assertFalse(ShellProcess.asksForTheme(launcher = true, holdsCopy = false))
    }

    @Test fun `the background a process may point Start at is the copy of one row id under backgrounds, and nothing else`() {
        assertEquals("backgrounds/42.jpg", ShellProcess.backgroundCopy("42"))
        assertEquals("backgrounds/0.jpg", ShellProcess.backgroundCopy("0"))
        assertEquals("backgrounds/${Long.MAX_VALUE}.jpg", ShellProcess.backgroundCopy(Long.MAX_VALUE.toString()))
        for (bad in listOf(null, "", "-1", "+1", "4 2", "42.jpg", "../42", "42/..", "/data/x", "1e3", "٤٢", "1".repeat(20), "0x2A")) {
            assertNull("$bad", ShellProcess.backgroundCopy(bad))
        }
    }

    @Test fun `a Cortana open from another process names a mode and at most a page, by their names, or is refused`() {
        assertEquals(CortanaMode.HOME to null, ShellProcess.cortanaOpen("HOME", null))
        assertEquals(CortanaMode.LISTENING to null, ShellProcess.cortanaOpen("LISTENING", null))
        for (d in CortanaDestinationKey.entries) assertEquals(CortanaMode.HOME to d, ShellProcess.cortanaOpen("HOME", d.name))
        for ((mode, destination) in listOf(null to null, "" to null, "home" to null, "HOME " to null, "SPEAK" to null, "HOME" to "", "HOME" to "reminders", "HOME" to "NOWHERE")) {
            assertNull("$mode / $destination", ShellProcess.cortanaOpen(mode, destination))
        }
    }
}
