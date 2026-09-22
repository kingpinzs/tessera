package app.tileshell.ime

import android.content.Context
import app.tileshell.diag.Diagnostics
import java.io.File

/**
 * What the keyboard remembers about itself: where the panel was dragged to, the one-handed dock, the
 * recent emoji. It lives in the `:ime` process and is written ONLY from there, so a plain file with a
 * temp-and-rename write has exactly one writer (the user's settings, which the Settings hub writes, live
 * in the main process and reach the keyboard through [KeyboardConfig] instead).
 *
 * The raise is stored in phys, not pixels, so a dragged-up keyboard stays at the same place relative to
 * its own size after a display-size change.
 */
class ImeStore(context: Context) {
    private val file = File(context.filesDir, "ime_state.properties")
    private val props = java.util.Properties()

    init {
        runCatching { if (file.exists()) file.inputStream().use { props.load(it) } }
            .onFailure { Diagnostics.add("ime", "ime_state unreadable, starting clean: $it") }
    }

    var raise: Float
        get() = props.getProperty("raise_phys")?.toFloatOrNull() ?: 0f
        set(v) { props.setProperty("raise_phys", v.toString()); save() }

    var dock: Dock
        get() = runCatching { Dock.valueOf(props.getProperty("dock", Dock.FULL.name)) }.getOrDefault(Dock.FULL)
        set(v) { props.setProperty("dock", v.name); save() }

    /** Most recent first, at most [RECENT_MAX]. */
    var recentEmoji: List<String>
        get() = props.getProperty("recent_emoji").orEmpty().split('\u001f').filter { it.isNotEmpty() }
        set(v) { props.setProperty("recent_emoji", v.take(RECENT_MAX).joinToString("\u001f")); save() }

    fun addRecent(emoji: String) {
        recentEmoji = listOf(emoji) + recentEmoji.filter { it != emoji }
    }

    private fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            tmp.outputStream().use { props.store(it, "tessera keyboard state") }
            check(tmp.renameTo(file)) { "rename failed" }
        }.onFailure { Diagnostics.add("ime", "ime_state write failed: $it") }
    }

    companion object {
        const val RECENT_MAX = 40
    }
}
