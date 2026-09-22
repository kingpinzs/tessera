package app.tileshell.applist

import app.tileshell.apps.ProfileKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the hold menu offers "Uninstall" (Jeremy, 2026-09-22: "I should be able to long hold app icon to
 * bring up a quick menu to uninstall it").
 *
 * An item that opens a dialog Android then refuses is worse than no item, so the rule is about what can
 * actually happen rather than what would be tidy to show.
 */
class AppUninstallTest {

    private fun can(pkg: String?, system: Boolean = false, profile: ProfileKind = ProfileKind.MAIN) =
        AppUninstall.canUninstall(pkg, system, profile, selfPackage = "app.tileshell")

    @Test
    fun `an ordinary app can be uninstalled`() {
        assertTrue(can("com.example.game"))
    }

    @Test
    fun `an inbox app cannot`() {
        // A system app has no uninstall, only "disable", which is a Settings page and not what was asked for.
        assertFalse(can("com.android.settings", system = true))
    }

    @Test
    fun `the shell does not offer to remove itself`() {
        // It is the Home app drawing the menu; removing it from inside itself leaves no launcher.
        assertFalse(can("app.tileshell"))
    }

    @Test
    fun `work and private apps are left to their profile`() {
        assertFalse(can("com.example.work", profile = ProfileKind.WORK))
        assertFalse(can("com.example.private", profile = ProfileKind.PRIVATE))
    }

    @Test
    fun `a package name that did not survive the framework stub is refused, not guessed`() {
        assertFalse(can(null))
        assertFalse(can(""))
    }
}
