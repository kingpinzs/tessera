package app.tileshell.start

import app.tileshell.start.BackRules.KeyguardShown
import app.tileshell.start.BackRules.Resumed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackRulesTest {
    private val shell = "app.tileshell"
    private val start = Resumed(shell, "app.tileshell.StartActivity")
    private val clock = Resumed("com.android.deskclock", "com.android.deskclock.DeskClock")
    private val wifi = Resumed("com.android.settings", "com.android.settings.Settings\$WifiSettingsActivity")
    private val fallbackHome = Resumed("com.android.settings", "com.android.settings.FallbackHome")
    private val systemUi = Resumed("com.android.systemui", "com.android.systemui.SomeActivity")

    private val rules = BackRules(
        shellPackage = shell,
        homeComponents = setOf("com.android.settings" to "com.android.settings.FallbackHome", shell to "app.tileshell.StartActivity"),
        systemSurfacePackages = setOf("com.android.systemui"),
    )

    private fun pick(vararg events: BackRules.Event, launchable: (Resumed) -> Boolean = { true }): Resumed? =
        rules.pick(events.toList()) { r -> r.takeIf(launchable) }

    @Test fun mostRecentEligibleAppWins() = assertEquals(wifi, pick(clock, start, wifi, start))

    @Test fun nothingResumedDoesNothing() = assertNull(pick(start))

    @Test fun homeActivityIsExcludedButItsPackagesOtherActivitiesAreNot() {
        assertEquals(wifi, pick(clock, wifi, fallbackHome, start))
    }

    @Test fun excludedLatestFallsBackToTheMostRecentEligible() = assertEquals(clock, pick(clock, fallbackHome, systemUi, start))

    @Test fun lastAppNoLongerLaunchableFallsBackToAnEarlierOne() {
        assertEquals(clock, pick(clock, start, wifi, start, launchable = { it != wifi }))
        assertNull(pick(wifi, start, launchable = { it != wifi }))
    }

    @Test fun keyguardClearsTheHistory() = assertNull(pick(clock, KeyguardShown, start))

    @Test fun appReShownByTheUnlockIsNotAnOpenedApp() {
        // E20 lock step: DeskClock showing, sleep (keyguard), wake, dismiss: DeskClock resumes again, then Home.
        assertNull(pick(clock, KeyguardShown, clock, start))
    }

    @Test fun systemSurfaceDuringUnlockKeepsTheReShownAppFromCounting() = assertNull(pick(clock, KeyguardShown, systemUi, clock, start))

    @Test fun appOpenedAgainAfterGoingHomeCounts() = assertEquals(clock, pick(clock, KeyguardShown, clock, start, clock, start))

    @Test fun anotherAppAfterTheUnlockCounts() = assertEquals(wifi, pick(clock, KeyguardShown, clock, wifi, start))

    @Test fun lockedOnStartThenAppOpenedCounts() = assertEquals(clock, pick(start, KeyguardShown, start, clock, start))

    @Test fun screenOffWithoutKeyguardKeepsTheHistory() = assertEquals(clock, pick(clock, start))
}
