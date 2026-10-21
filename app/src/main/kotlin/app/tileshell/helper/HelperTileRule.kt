package app.tileshell.helper

/**
 * What a tap on one of the four helper tiles does (phase 04 build task 6; Q-04-2, Q-04-11, round 4 D5). Tess's
 * `SetRadio` takes the same decision (Q-04-2's "the same rule"; build task 10), with her pane in the panel's place.
 */
object HelperTileRule {

    sealed interface Tap {
        /** The helper answers its ping: run the verb, nothing written (Decisions "The restart path, after round 4" d). */
        data object Verb : Tap

        /** Startable on a trusted BSSID: start the helper with the panel open, then run the verb. */
        data object StartThenVerb : Tap

        /**
         * Startable on a BSSID that is not trusted or is unknown: the start writes `adb_wifi_enabled=1` and Android's
         * "Allow wireless debugging on this network?" activity follows, which would open unseen under the layer-31 panel.
         * So the panel first closes with the tap wipe (Y8), the tile reads "Starting…", then start and the verb.
         */
        data object CloseThenStart : Tap

        /** Never paired (Q-04-11): the helper's page (`SettingsPage.HELPER`), the panel closed with the tap wipe. */
        data object OpenHelperPage : Tap

        /** The helper cannot run: Android's own control for [Radio.controlAction] and the Y16 line for [state]. */
        data class OpenControl(val state: HelperState) : Tap
    }

    /**
     * [wifiConnected] is whether the phone is on a Wi-Fi network now (Wireless debugging needs one: so also the Wi-Fi
     * tile while Wi-Fi is off). [trust] is the current BSSID's place in the trusted list.
     */
    fun onTap(radio: Radio, state: HelperState, wifiConnected: Boolean, trust: BssidTrust): Tap = when (state) {
        is HelperState.Running -> Tap.Verb
        // Setting up comes first, whatever the Wi-Fi (Q-04-11's reading).
        HelperState.NotPaired -> Tap.OpenHelperPage
        HelperState.PairingGone -> Tap.OpenControl(HelperState.PairingGone)
        HelperState.DebuggingOff -> Tap.OpenControl(HelperState.DebuggingOff)
        HelperState.NoWifi -> Tap.OpenControl(HelperState.NoWifi)
        HelperState.Starting, HelperState.LocationOff, HelperState.UntrustedNetwork, is HelperState.Dead ->
            when {
                !wifiConnected -> Tap.OpenControl(HelperState.NoWifi)
                trust == BssidTrust.TRUSTED -> Tap.StartThenVerb
                else -> Tap.CloseThenStart
            }
    }
}
