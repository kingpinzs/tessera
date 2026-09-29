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
    /**
     * Phase 13 (interview Q2 A): Windows 10's "Transparency effects" switch. On by default; off, every transient
     * surface draws its measured solid W10M fill instead of acrylic (and phase 12's presets set it).
     */
    val transparencyEffects: Boolean = true,
    /**
     * Phase 12 (T12-2): the theme preset the items came from — `default`, `w10m`, `hal`, `soft`, `lumia`, `midnight` — or
     * `custom` once a write changes any of the preset's items; null (the key absent) reads as the out-of-box Default.
     */
    val themePreset: String? = null,
    /** Phase 12 (T12-10): the original preset's picture variant, `hero` or `streaks`; changing it is not a Custom change. */
    val themePresetVariant: String = ThemePresets.VARIANT_HERO,
    /** Phase 12 (r3 D7): Tess's lens, `hal`, `accent` or `hal_dim` (phase 03's persona reads it). */
    val tessLens: String = ThemePresets.LENS_HAL,
    /** Phase 12 (T12-2): the keyboard's colours, DARK (as phase 05 built it) or LIGHT (phase 05's keyboard reads it). */
    val keyboardPalette: ThemeMode = ThemeMode.DARK,
)

/** Start + theme settings (phase 01 Settings hub). SharedPreferences-backed, exposed as a StateFlow. */
class ShellSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("start_theme", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val theme: StateFlow<StartTheme> = state.asStateFlow()

    private fun read(): StartTheme = readItems().let { t ->
        // A set stored before phase 12 has no theme_preset key: read it as Custom when its items are not Default's, so the
        // first preset tap snapshots it — the user's own picture included — instead of dropping it (adversarial review B1).
        t.copy(themePreset = ThemePresets.presetOnRead(prefs.getString("theme_preset", null), t))
    }

    private fun readItems() = StartTheme(
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
        transparencyEffects = prefs.getBoolean("transparency_effects", true),
        themePresetVariant = ThemePresets.variantOnRead(prefs.getString("theme_preset_variant", null)),
        tessLens = prefs.getString("tess_lens", ThemePresets.LENS_HAL)!!,
        keyboardPalette = runCatching { ThemeMode.valueOf(prefs.getString("keyboard_palette", ThemeMode.DARK.name)!!) }.getOrDefault(ThemeMode.DARK),
    )

    /**
     * A user's change to any item. Phase 12's Custom rule (T12-2, r3 D8): when it changes one of a preset's items the
     * preset reads `custom`; `columns`, `profiles`, `autosize`, `photos_slideshow` and `photo_frame` never do.
     */
    fun update(change: (StartTheme) -> StartTheme) {
        val prev = state.value
        val next = ThemePresets.afterUserChange(prev, change(prev))
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
            .putBoolean("transparency_effects", next.transparencyEffects)
            .putString("theme_preset", next.themePreset)
            .putString("theme_preset_variant", next.themePresetVariant)
            .putString("tess_lens", next.tessLens)
            .putString("keyboard_palette", next.keyboardPalette.name)
            .apply()
        state.value = next
        Diagnostics.add("settings", "start theme changed: $next")
    }

    /**
     * Phase 12's preset write (build task 3, r3 D8): puts ONLY the preset's keys — the eight items, `theme_preset` and,
     * for the original preset, `theme_preset_variant` — and never rewrites the others, unlike [update].
     */
    fun applyPreset(items: PresetItems, presetId: String, variant: String? = null) {
        val edit = prefs.edit()
        ThemePresets.putItems(edit, items)
        edit.putString("theme_preset", presetId)
        if (variant != null) edit.putString("theme_preset_variant", variant)
        edit.apply()
        val next = ThemePresets.withItems(state.value, items).copy(
            themePreset = presetId,
            themePresetVariant = variant ?: state.value.themePresetVariant,
        )
        state.value = next
        Diagnostics.add("settings", "start theme preset written: $next")
    }

    /** Phase 12 (T12-10): the original preset's picture variant — the picture and the variant key only. */
    fun applyVariant(variant: String, backgroundUri: String?) {
        prefs.edit().putString("background", backgroundUri).putString("theme_preset_variant", variant).apply()
        state.value = state.value.copy(backgroundUri = backgroundUri, themePresetVariant = variant)
        Diagnostics.add("settings", "start theme variant written: ${state.value}")
    }

    companion object {
        @Volatile private var instance: ShellSettings? = null
        fun get(context: Context): ShellSettings =
            instance ?: synchronized(this) { instance ?: ShellSettings(context.applicationContext).also { instance = it } }
    }
}
