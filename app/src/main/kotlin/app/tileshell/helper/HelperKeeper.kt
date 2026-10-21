package app.tileshell.helper

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The helper's keeper: the restart path and the helper's state (phase 04 build task 3; Q-04-1, addendum B8,
 * Decisions "The trusted list", "The restart path, after round 4").
 *
 * CONTRACT STUB (2026-10-20, docs/plan/qa/phase-04/BUILD-CONTRACT.md): the signatures below are the seam the quick
 * actions (task 6), Tess (task 10) and the Settings pages and checklist (task 12) compile against. Build task 3 fills
 * the bodies and may add members, but does not change these signatures without the lead.
 */
object HelperKeeper {
    /** What wakes the keeper (`RestartPlan.next`'s events). A tile tap and a Tess request go through [switch]. */
    enum class Trigger(val word: String) {
        BOOT("boot"),
        PACKAGE_REPLACED("package replaced"),
        BINDER_DIED("binder died"),
        HOME_START("home start"),
        WIFI_JOINED("wifi joined"),
    }

    private val mutableState = MutableStateFlow<HelperState>(HelperState.NotPaired)
    val state: StateFlow<HelperState> = mutableState.asStateFlow()

    private val mutableDetails = MutableStateFlow(HelperDetails(null, null, null, 0, false))
    val details: StateFlow<HelperDetails> = mutableDetails.asStateFlow()

    /** Called once from the launcher process's start-up: reads the stored state and registers the Wi-Fi-joined trigger. */
    fun init(context: Context) {}

    /** One of the keeper's triggers fired (boot, package replaced, binder death, a home start, a Wi-Fi join). */
    fun trigger(context: Context, trigger: Trigger) {}

    /** The current BSSID's trust, as last read (UNKNOWN without precise location or with Location off). */
    fun bssidTrust(): BssidTrust = BssidTrust.UNKNOWN

    /**
     * Turns [radio] [on] through the helper for a tap or a Tess request: straight to the verb while the helper answers
     * its ping; otherwise starts it first (writing `adb_wifi_enabled=1` from 0 on any network, a request counting as a
     * tap) and then runs the verb. Suspends until the verb ran or the start failed. The caller has already applied
     * [HelperTileRule.onTap] (closing the panel or Tess's pane first where it says so).
     */
    suspend fun switch(context: Context, radio: Radio, on: Boolean, by: Requester): SwitchOutcome =
        SwitchOutcome.Failed(radio, on, state.value)

    /** The helper page's Pair button (`helper_pair`): posts the shell's "Enter code" notification and waits for the code. */
    fun startPairing(context: Context) {}

    /** The helper page's Start button (`helper_start`): a start as a tap would make it, with no verb after it. */
    fun startNow(context: Context) {}
}
