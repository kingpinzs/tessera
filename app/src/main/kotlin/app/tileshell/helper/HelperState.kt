package app.tileshell.helper

/**
 * What the privileged helper is doing (phase 04 build task 3). It feeds the four helper tiles' live-state line
 * (Y16), the helper's Settings page (Y4) and the setup checklist's observation rows (task 12).
 *
 * "Not set up" is never paired; "pairing gone" is paired once with the key now refused (Q-04-11 keeps the two apart:
 * the first opens the helper's page, the second Android's control and reads "Pair again"). [LocationOff] is a Wi-Fi
 * whose BSSID the app cannot read (precise location not granted, or Location off; round 4 D12 b): it is never read as
 * an untrusted network.
 */
sealed interface HelperState {
    /** Never paired (Q-04-11): a helper tile opens the helper's page. */
    data object NotPaired : HelperState

    /** Paired once, the key now refused or expired (Q-04-2, Q-04-11): Android's control, "Pair again". */
    data object PairingGone : HelperState

    /** No Wi-Fi connection (Wireless debugging needs one): Android's control, "No Wi-Fi". */
    data object NoWifi : HelperState

    /** On a Wi-Fi whose BSSID is unknown (round 4 D12 b): startable, but the start brings Android's dialog. */
    data object LocationOff : HelperState

    /** USB debugging and Wireless debugging both off, or Developer options off (R4 4d): no retry loop. */
    data object DebuggingOff : HelperState

    /** On a Wi-Fi not in the trusted list: startable on a tap, which brings Android's dialog (Q-04-2). */
    data object UntrustedNetwork : HelperState

    /** A start is under way. */
    data object Starting : HelperState

    /** The helper answers its ping. */
    data class Running(val pid: Int, val versionCode: Long, val uptimeMs: Long) : HelperState

    /** The helper is down for [reason] (a word for the page and the log, never shown on a tile). */
    data class Dead(val reason: String) : HelperState
}

/** Whether the current Wi-Fi's BSSID is in Android's "Always allow on this network" list (Decisions "The trusted list"). */
enum class BssidTrust { TRUSTED, UNTRUSTED, UNKNOWN }

/** Who asked for a switch. Both count as a tap (Decisions "Q-04-1's reading, corrected"; round 4 V15). */
enum class Requester(val word: String) { TILE("tile"), TESS("tess") }

/** What one switch request through [HelperKeeper.switch] came to. */
sealed interface SwitchOutcome {
    /** The verb ran with exit code 0. */
    data class Done(val radio: Radio, val on: Boolean) : SwitchOutcome

    /** The radio already read [on]; nothing was sent. */
    data class Already(val radio: Radio, val on: Boolean) : SwitchOutcome

    /** The helper could not be started or the verb failed; [state] is why (its Y16 line). */
    data class Failed(val radio: Radio, val on: Boolean, val state: HelperState) : SwitchOutcome
}

/** The helper page's facts (Y4): the running helper's pid, version and uptime, and the trusted networks count. */
data class HelperDetails(
    val pid: Int?,
    val versionCode: Long?,
    val uptimeMs: Long?,
    val trustedNetworks: Int,
    val paired: Boolean,
)
