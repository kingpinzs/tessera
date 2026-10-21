package app.tileshell.helper

import android.provider.Settings

/**
 * The four radios the privileged helper switches (phase 04 Q-04-2, Q-04-5; R4: the only four that changed through
 * the helper on the S25 Ultra). One enum for the quick-action tiles (build task 6), the helper's verb table (task 2)
 * and Tess's `SetRadio` (task 10).
 *
 * - [quickId] is the quick action's id (`quick_action:<id>`, `[ac] quick action <id> -> …`).
 * - [verbStem] names the helper verbs `<stem>_on` / `<stem>_off` and Tess's `[cortana] radio <stem> <on|off>` line.
 * - [label] is the tile's W10M caption; [spoken] is how Tess names it inside a sentence.
 * - [controlAction] is Android's own control for this switch, opened while the helper cannot run (Q-04-2, addendum C4).
 */
enum class Radio(
    val quickId: String,
    val verbStem: String,
    val label: String,
    val spoken: String,
    val controlAction: String,
) {
    WIFI("wifi", "wifi", "Wi-Fi", "Wi-Fi", Settings.Panel.ACTION_WIFI),
    BLUETOOTH("bluetooth", "bluetooth", "Bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
    MOBILE_DATA("cellular", "mobile_data", "Cellular", "mobile data", Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
    AIRPLANE("airplane_mode", "airplane", "Airplane mode", "airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    ;

    /** The helper verb that turns this radio [on] or off (`HelperVerbs`, build task 2). */
    fun verb(on: Boolean): String = "${verbStem}_${if (on) "on" else "off"}"

    companion object {
        fun ofQuickId(id: String): Radio? = entries.firstOrNull { it.quickId == id }
    }
}
