package app.tessera.r4probe

/**
 * R4's toggle list: the ONLY commands the helper will run. It is an allow-list of fixed strings, never a generic shell —
 * the rule phase 19 sets for every helper verb ("a named verb in the allow-list, never a generic command"). The host
 * script's direct pass reads the same list (`HelperMain --list-toggles`), so the two passes cannot drift apart.
 *
 * A toggle is flipped ONLY when its read matches [onPattern] or [offPattern]; a read that matches neither is recorded
 * and left alone (review/2026-09-28-r4-kit-review.md B1: a pattern that silently failed read NFC as "off" and left it
 * off). The patterns are case-insensitive and written for BOTH engines that apply them — Kotlin's Regex with
 * IGNORE_CASE here and GNU `grep -Ei` in the script — so no inline flags like `(?i)`, which grep reads as literals.
 *
 * A toggle with a [probe] is never flipped: the probe is a command that shows whether the shell uid may use the verb
 * without changing anything (the hotspot: `start-softap` with no arguments starts nothing; on a build that refuses the
 * shell it throws, and that refusal is the answer phase 19 needs).
 */
object Toggles {
    class Toggle(
        val name: String,
        val read: String,
        val on: String,
        val off: String,
        val onPattern: String,
        val offPattern: String,
        /** Which plan item waits on this toggle. */
        val forItem: String,
        val probe: String? = null,
    ) {
        fun state(read: String): String = when {
            Regex(onPattern, RegexOption.IGNORE_CASE).containsMatchIn(read) -> "on"
            Regex(offPattern, RegexOption.IGNORE_CASE).containsMatchIn(read) -> "off"
            else -> "unknown"
        }
    }

    val ALL = listOf(
        // Android will not hold battery saver on while charging, and the phone charges over the USB cable R4 runs on:
        // the host script reports the battery unplugged (dumpsys battery unplug) around every battery-saver step and
        // resets it after, so this verb stays the plain one the product would ship.
        Toggle("battery_saver", "settings get global low_power", "cmd power set-mode 1", "cmd power set-mode 0",
            "^1$", "^0$", "phase 19 verb (battery saver)"),
        Toggle("location", "cmd location is-location-enabled", "cmd location set-location-enabled true",
            "cmd location set-location-enabled false", "^true$", "^false$", "phase 19 verb (location)"),
        Toggle("auto_time", "settings get global auto_time", "settings put global auto_time 1",
            "settings put global auto_time 0", "^1$", "^0$", "phase 19 verb (automatic time)"),
        Toggle("nfc", "dumpsys nfc 2>&1 | grep -i -m1 -E 'mState[=:]'", "svc nfc enable", "svc nfc disable",
            "mState[=:] *on\\b", "mState[=:] *off\\b", "phase 19 verb (NFC)"),
        Toggle("bluetooth", "settings get global bluetooth_on", "cmd bluetooth_manager enable",
            "cmd bluetooth_manager disable", "^1$", "^0$", "phase 04 action center"),
        Toggle("wifi", "cmd wifi status 2>&1 | head -1", "cmd wifi set-wifi-enabled enabled",
            "cmd wifi set-wifi-enabled disabled", "Wifi is enabled", "Wifi is disabled", "phase 04 action center"),
        Toggle("hotspot", "dumpsys wifi 2>&1 | grep -m1 -E 'WifiApState|SoftApState'", "-", "-",
            "a^", "a^", "phase 19 verb (hotspot)", probe = "cmd wifi start-softap 2>&1"),
        Toggle("mobile_data", "settings get global mobile_data", "svc data enable", "svc data disable",
            "^1$", "^0$", "phase 04 action center"),
        Toggle("airplane", "cmd connectivity airplane-mode", "cmd connectivity airplane-mode enable",
            "cmd connectivity airplane-mode disable", "^enabled$", "^disabled$", "phase 04 action center"),
    )

    fun named(name: String): Toggle? = ALL.firstOrNull { it.name == name }
}
