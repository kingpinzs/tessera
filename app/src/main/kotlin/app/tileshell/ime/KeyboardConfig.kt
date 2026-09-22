package app.tileshell.ime

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.ShellSettings
import app.tileshell.ui.tokens.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The keyboard's Settings (build task 9 and 10): key sounds, vibration, cursor-controller handedness
 * and "Switch back to letters after I type an emoticon", plus the shell's accent, which the keyboard
 * fills its pressed keys, popups, trail and dot with.
 */
data class KeyboardConfig(
    /** Decisions: key sounds On by default (approximation, H12). */
    val sounds: Boolean = true,
    /** Decisions: vibration On by default (approximation, H12). */
    val vibration: Boolean = true,
    /** Decisions: right-handed by default (approximation, H16). */
    val handedness: Handedness = Handedness.RIGHT,
    /** Decisions: On by default (approximation, H17). */
    val switchBackAfterEmoji: Boolean = true,
    val accent: Long = Palette.DEFAULT_ACCENT,
) {
    fun toBundle() = Bundle().apply {
        putBoolean(K_SOUNDS, sounds)
        putBoolean(K_VIBRATION, vibration)
        putString(K_HANDEDNESS, handedness.name)
        putBoolean(K_SWITCH_BACK, switchBackAfterEmoji)
        putLong(K_ACCENT, accent)
    }

    companion object {
        private const val K_SOUNDS = "sounds"
        private const val K_VIBRATION = "vibration"
        private const val K_HANDEDNESS = "handedness"
        private const val K_SWITCH_BACK = "switch_back"
        private const val K_ACCENT = "accent"

        fun fromBundle(b: Bundle) = KeyboardConfig(
            sounds = b.getBoolean(K_SOUNDS, true),
            vibration = b.getBoolean(K_VIBRATION, true),
            handedness = runCatching { Handedness.valueOf(b.getString(K_HANDEDNESS) ?: "RIGHT") }.getOrDefault(Handedness.RIGHT),
            switchBackAfterEmoji = b.getBoolean(K_SWITCH_BACK, true),
            accent = b.getLong(K_ACCENT, Palette.DEFAULT_ACCENT),
        )
    }
}

/**
 * The keyboard settings' store, in the MAIN process: the Settings hub writes it and nothing else does.
 *
 * The keyboard runs in its own `:ime` process (so a keyboard crash cannot take Start with it, and the
 * speech process sees the keyboard as a client of its own), and SharedPreferences are not coherent across
 * processes. So the keyboard never opens this file: it asks [KeyboardConfigProvider] each time it shows,
 * which answers from here — one writer, one reader, no stale cache.
 */
class KeyboardSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val config: StateFlow<KeyboardConfig> = state.asStateFlow()

    private fun read() = KeyboardConfig(
        sounds = prefs.getBoolean("sounds", true),
        vibration = prefs.getBoolean("vibration", true),
        handedness = runCatching { Handedness.valueOf(prefs.getString("handedness", "RIGHT")!!) }.getOrDefault(Handedness.RIGHT),
        switchBackAfterEmoji = prefs.getBoolean("switch_back", true),
    )

    fun update(change: (KeyboardConfig) -> KeyboardConfig) {
        val next = change(state.value)
        prefs.edit()
            .putBoolean("sounds", next.sounds)
            .putBoolean("vibration", next.vibration)
            .putString("handedness", next.handedness.name)
            .putBoolean("switch_back", next.switchBackAfterEmoji)
            .apply()
        state.value = next
        Diagnostics.add("settings", "keyboard changed: $next")
    }

    companion object {
        @Volatile private var instance: KeyboardSettings? = null
        fun get(context: Context): KeyboardSettings =
            instance ?: synchronized(this) { instance ?: KeyboardSettings(context.applicationContext).also { instance = it } }
    }
}

/**
 * The keyboard's one read of its settings, across the process boundary. Not exported: only this app's
 * own processes can call it. `call("read")` answers the current [KeyboardConfig] with the shell's accent.
 */
class KeyboardConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (method != METHOD_READ) return null
        val accent = ShellSettings.get(ctx).theme.value.accent
        return KeyboardSettings.get(ctx).config.value.copy(accent = accent).toBundle()
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "app.tileshell.keyboardconfig"
        const val METHOD_READ = "read"

        /** From the keyboard's process. A failure keeps what the keyboard had and says so in the ring. */
        fun read(context: Context): KeyboardConfig? = runCatching {
            context.contentResolver.call(Uri.parse("content://$AUTHORITY"), METHOD_READ, null, null)?.let { KeyboardConfig.fromBundle(it) }
        }.onFailure { Diagnostics.add("ime", "keyboard config read failed: $it") }.getOrNull()
    }
}
