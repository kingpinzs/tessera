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
    /** Tile size follows use (INDEX Change Log 2026-09-21 item 1). On by default; a size set by
     *  hand is pinned whatever this says. */
    val autoSizeTiles: Boolean = true,
    /**
     * The Photos tile runs a slideshow (INDEX Change Log 2026-09-21 item 4: "the photo one should have a
     * slide show option on the tile"). Off by default: the tile behaves exactly as phase 01 built it
     * until someone asks for a slideshow.
     */
    val photosSlideshow: Boolean = false,
    /**
     * The Photos tile's main photo — "set a main photo kind of like a picture frame and it does not flip
     * when that is set". Null means no frame.
     *
     * It lives HERE, in the shell's settings, rather than in the layout store, and that is deliberate:
     * the layout store holds what the grid IS (which tiles, where, how big) and is the thing edit mode
     * writes by index; a chosen photo is a preference about one tile's content, the same kind of thing as
     * the Start background two fields up, and it must survive the tile being moved, resized, put in a
     * folder or into the bottom row. It is stored as a persisted content URI, exactly as the Start
     * background is, so it is one already-proven mechanism rather than a second one.
     *
     * The Photos tile is a single slot tile (Slot.PHOTOS), so one setting is one tile: there is no
     * per-tile key to invent and nothing to clean up when a tile is unpinned.
     */
    val photoFrameUri: String? = null,
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
        autoSizeTiles = prefs.getBoolean("autosize", true),
        photosSlideshow = prefs.getBoolean("photos_slideshow", false),
        photoFrameUri = prefs.getString("photo_frame", null),
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
            .putBoolean("autosize", next.autoSizeTiles)
            .putBoolean("photos_slideshow", next.photosSlideshow)
            .putString("photo_frame", next.photoFrameUri)
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
