package app.tessera.r4probe

/**
 * R4's toggle list: the ONLY commands the helper will run. It is an allow-list of fixed strings, never a generic shell —
 * the rule phase 19 sets for every helper verb ("a named verb in the allow-list, never a generic command"). The host
 * script's direct pass reads the same list (`HelperMain --list-toggles`), so the two passes cannot drift apart.
 *
 * Each toggle is read, flipped away from its current state, read, flipped back, and read again; [onPattern] decides
 * which way "away" is. A read the pattern cannot place counts as off, so the second flip always turns a toggle OFF
 * that the first one turned on (a hotspot left running, or airplane mode left on, is the failure to avoid).
 */
object Toggles {
    class Toggle(
        val name: String,
        val read: String,
        val on: String,
        val off: String,
        val onPattern: Regex,
        /** Which plan item waits on this toggle. */
        val forItem: String,
    )

    val ALL = listOf(
        // Android will not hold battery saver on while charging, and the phone charges over the USB cable R4 runs on:
        // the host script reports the battery unplugged (dumpsys battery unplug) around every battery-saver step and
        // resets it after, so this verb stays the plain one the product would ship.
        Toggle("battery_saver", "settings get global low_power", "cmd power set-mode 1", "cmd power set-mode 0",
            Regex("^1"), "phase 19 verb (battery saver)"),
        Toggle("location", "cmd location is-location-enabled", "cmd location set-location-enabled true",
            "cmd location set-location-enabled false", Regex("true"), "phase 19 verb (location)"),
        Toggle("auto_time", "settings get global auto_time", "settings put global auto_time 1",
            "settings put global auto_time 0", Regex("^1"), "phase 19 verb (automatic time)"),
        Toggle("nfc", "dumpsys nfc 2>&1 | grep -i -m1 -E 'mState|state='", "svc nfc enable", "svc nfc disable",
            Regex("(?i)(mState|state)[=:]\\s*on"), "phase 19 verb (NFC)"),
        Toggle("bluetooth", "settings get global bluetooth_on", "cmd bluetooth_manager enable",
            "cmd bluetooth_manager disable", Regex("^1"), "phase 04 action center"),
        Toggle("wifi", "cmd wifi status 2>&1 | head -1", "cmd wifi set-wifi-enabled enabled",
            "cmd wifi set-wifi-enabled disabled", Regex("Wifi is enabled"), "phase 04 action center"),
        // No hotspot command is listed for the shell on the Android 16 emulator (a user build); the attempt and its
        // exact failure are the evidence phase 19 needs (a verb R4 cannot prove stays a deep-link).
        Toggle("hotspot", "dumpsys wifi 2>&1 | grep -m1 -E 'WifiApState|SoftApState'",
            "cmd wifi start-softap R4probe wpa2 r4probe-temp-8471", "cmd wifi stop-softap",
            Regex("(?i)(WIFI_AP_STATE_ENABLED|state[=: ]+(enabled|13)\\b)"), "phase 19 verb (hotspot)"),
        Toggle("mobile_data", "settings get global mobile_data", "svc data enable", "svc data disable",
            Regex("^1"), "phase 04 action center"),
        Toggle("airplane", "cmd connectivity airplane-mode", "cmd connectivity airplane-mode enable",
            "cmd connectivity airplane-mode disable", Regex("^enabled"), "phase 04 action center"),
    )

    fun named(name: String): Toggle? = ALL.firstOrNull { it.name == name }
}
