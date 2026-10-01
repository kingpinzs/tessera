package app.tileshell.start

import app.tileshell.start.BackRules.KeyguardShown
import app.tileshell.start.BackRules.Resumed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L14-2: the shell has been a keyboard package since phase 05, and Back on Start passes every keyboard package to
 * [BackRules] as a system surface. With the shell among them Start's resumes were skipped, and after a lock the last app
 * used could not be reached by Back until another app had been opened (phase 14 E13, the phase 01 E20 replay:
 * `[back] events=87 keyguards=2 candidate=null`). These run the rules with the set the shell really builds.
 */
class BackHistorySurfacesTest {
    private val shell = "app.tileshell"
    private val latin = "com.android.inputmethod.latin"
    private val start = Resumed(shell, "app.tileshell.StartActivity")
    private val clock = Resumed("com.android.deskclock", "com.android.deskclock.DeskClock")
    private val keyboardPage = Resumed(latin, "com.android.inputmethod.latin.settings.SettingsActivity")

    // What the device reports: the AOSP keyboard and the shell's own.
    private val surfaces = BackHistory.systemSurfaces(shell, setOf(latin, shell))

    private val rules = BackRules(
        shellPackage = shell,
        homeComponents = setOf(shell to "app.tileshell.StartActivity"),
        systemSurfacePackages = surfaces,
    )

    private fun pick(vararg events: BackRules.Event): Resumed? = rules.pick(events.toList()) { it }

    @Test
    fun `the shell is not a system surface though it is a keyboard package`() {
        assertFalse(shell in surfaces)
        assertTrue(latin in surfaces)
        assertTrue("com.android.systemui" in surfaces)
    }

    @Test
    fun `an app opened again after a lock is reached by Back`() {
        // The emulator's sequence: DeskClock used, Home, the phone locked and unlocked on Start, DeskClock opened, Home.
        assertEquals(clock, pick(clock, start, KeyguardShown, start, clock, start))
    }

    @Test
    fun `Start resuming ends the unlock's continuation`() {
        // Phase 01's rule with the real set: re-shown by the unlock it does not count; opened again from Start it does.
        assertNull(pick(clock, KeyguardShown, clock, start))
        assertEquals(clock, pick(clock, KeyguardShown, clock, start, clock, start))
    }

    @Test
    fun `of the shell's own pages only Start and its catalog apps count`() {
        // Gate review of L14-2: a ring over the lock screen must not become a Back target through the package fallback.
        val shellApps = setOf("app.tileshell.weather.WeatherActivity", "app.tileshell.music.MusicActivity")
        assertTrue(BackHistory.shellPageCounts("app.tileshell.StartActivity", shellApps))
        assertTrue(BackHistory.shellPageCounts("app.tileshell.weather.WeatherActivity", shellApps))
        assertFalse(BackHistory.shellPageCounts("app.tileshell.clock.RingActivity", shellApps))
        assertFalse(BackHistory.shellPageCounts("app.tileshell.cortana.CortanaUnlockActivity", shellApps))
        assertFalse(BackHistory.shellPageCounts("app.tileshell.cortana.CortanaPermissionActivity", shellApps))
    }

    @Test
    fun `another keyboard's page still neither counts nor ends the continuation`() {
        assertNull(pick(clock, KeyguardShown, keyboardPage, clock, start))
    }
}
