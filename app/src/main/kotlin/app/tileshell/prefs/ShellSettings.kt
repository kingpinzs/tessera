package app.tileshell.prefs

import android.content.Context
import android.content.SharedPreferences
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.tokens.Palette
import app.tileshell.ui.tokens.StartGrid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT }

/** Q6: press feedback on Start tiles. W10M final = none (R3 A10, default). */
enum class PressStyle { NONE, WP8_TILT, P4_PRESS }

data class StartTheme(
    val accent: Long = Palette.DEFAULT_ACCENT,
    val theme: ThemeMode = ThemeMode.DARK, // X21 approximation
    val backgroundUri: String? = null,
    val transparency: Float = 0.5f, // X5 approximation
    val mediumColumns: Int = StartGrid.DEFAULT_COLUMNS,
    val pressStyle: PressStyle = PressStyle.NONE,
    val showWorkAndPrivateApps: Boolean = true,
)

/** Start + theme settings (phase 01 Settings hub). SharedPreferences-backed, exposed as a StateFlow. */
class ShellSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("start_theme", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val theme: StateFlow<StartTheme> = state.asStateFlow()

    private fun read() = StartTheme(
        accent = prefs.getLong("accent", Palette.DEFAULT_ACCENT),
        theme = ThemeMode.valueOf(prefs.getString("theme", ThemeMode.DARK.name)!!),
        backgroundUri = prefs.getString("background", null),
        transparency = prefs.getFloat("transparency", 0.5f),
        mediumColumns = prefs.getInt("columns", StartGrid.DEFAULT_COLUMNS),
        pressStyle = PressStyle.valueOf(prefs.getString("press", PressStyle.NONE.name)!!),
        showWorkAndPrivateApps = prefs.getBoolean("profiles", true),
    )

    fun update(change: (StartTheme) -> StartTheme) {
        val next = change(state.value)
        prefs.edit()
            .putLong("accent", next.accent)
            .putString("theme", next.theme.name)
            .putString("background", next.backgroundUri)
            .putFloat("transparency", next.transparency)
            .putInt("columns", next.mediumColumns)
            .putString("press", next.pressStyle.name)
            .putBoolean("profiles", next.showWorkAndPrivateApps)
            .apply()
        state.value = next
        Diagnostics.add("settings", "start theme changed: $next")
    }

    companion object {
        @Volatile private var instance: ShellSettings? = null
        fun get(context: Context): ShellSettings =
            instance ?: synchronized(this) { instance ?: ShellSettings(context.applicationContext).also { instance = it } }
    }
}
