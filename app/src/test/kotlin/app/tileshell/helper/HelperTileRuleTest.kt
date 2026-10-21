package app.tileshell.helper

import android.provider.Settings
import app.tileshell.helper.HelperTileRule.Tap
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 04 build task 6: `HelperTileRule.onTap` (Q-04-2, Q-04-11, round 4 D5). */
class HelperTileRuleTest {
    private val running = HelperState.Running(pid = 4242, versionCode = 7, uptimeMs = 1_000)

    @Test fun runningGoesStraightToTheVerb() {
        for (radio in Radio.entries) for (trust in BssidTrust.entries) for (wifi in listOf(true, false)) {
            assertEquals(Tap.Verb, HelperTileRule.onTap(radio, running, wifi, trust))
        }
    }

    @Test fun startableOnATrustedBssidStartsWithThePanelOpen() {
        for (state in listOf(HelperState.UntrustedNetwork, HelperState.Starting, HelperState.Dead("binder died"))) {
            assertEquals(Tap.StartThenVerb, HelperTileRule.onTap(Radio.MOBILE_DATA, state, true, BssidTrust.TRUSTED))
        }
    }

    @Test fun startableOnAnUntrustedOrUnknownBssidClosesThePanelFirst() {
        assertEquals(Tap.CloseThenStart, HelperTileRule.onTap(Radio.BLUETOOTH, HelperState.UntrustedNetwork, true, BssidTrust.UNTRUSTED))
        assertEquals(Tap.CloseThenStart, HelperTileRule.onTap(Radio.BLUETOOTH, HelperState.LocationOff, true, BssidTrust.UNKNOWN))
        assertEquals(Tap.CloseThenStart, HelperTileRule.onTap(Radio.AIRPLANE, HelperState.Dead("killed"), true, BssidTrust.UNKNOWN))
    }

    @Test fun notSetUpOpensTheHelpersPageWhateverTheWifi() {
        for (radio in Radio.entries) for (trust in BssidTrust.entries) for (wifi in listOf(true, false)) {
            assertEquals(Tap.OpenHelperPage, HelperTileRule.onTap(radio, HelperState.NotPaired, wifi, trust))
        }
    }

    @Test fun noWifiPairingGoneAndDebuggingOffOpenAndroidsControl() {
        assertEquals(Tap.OpenControl(HelperState.NoWifi), HelperTileRule.onTap(Radio.WIFI, HelperState.NoWifi, false, BssidTrust.UNKNOWN))
        // Wi-Fi off reads as no Wi-Fi whatever the state said last.
        assertEquals(
            Tap.OpenControl(HelperState.NoWifi),
            HelperTileRule.onTap(Radio.WIFI, HelperState.Dead("killed"), false, BssidTrust.TRUSTED),
        )
        assertEquals(
            Tap.OpenControl(HelperState.PairingGone),
            HelperTileRule.onTap(Radio.MOBILE_DATA, HelperState.PairingGone, true, BssidTrust.TRUSTED),
        )
        assertEquals(
            Tap.OpenControl(HelperState.DebuggingOff),
            HelperTileRule.onTap(Radio.AIRPLANE, HelperState.DebuggingOff, true, BssidTrust.TRUSTED),
        )
    }

    @Test fun eachRadiosControlIsAndroidsOwn() {
        assertEquals(Settings.Panel.ACTION_WIFI, Radio.WIFI.controlAction)
        assertEquals(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Radio.MOBILE_DATA.controlAction)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, Radio.BLUETOOTH.controlAction)
        assertEquals(Settings.ACTION_AIRPLANE_MODE_SETTINGS, Radio.AIRPLANE.controlAction)
    }

    @Test fun verbsAndIds() {
        assertEquals("wifi_on", Radio.WIFI.verb(true))
        assertEquals("mobile_data_off", Radio.MOBILE_DATA.verb(false))
        assertEquals("airplane_on", Radio.AIRPLANE.verb(true))
        assertEquals("bluetooth_off", Radio.BLUETOOTH.verb(false))
        assertEquals(Radio.MOBILE_DATA, Radio.ofQuickId("cellular"))
        assertEquals(Radio.AIRPLANE, Radio.ofQuickId("airplane_mode"))
    }
}
