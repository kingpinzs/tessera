package app.tileshell.prefs

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import app.tileshell.brand.Brand
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.tokens.Palette

/** The eight visual items a theme preset sets (phase 12 Q4 C, T12-2): everything visual, and nothing else. */
data class PresetItems(
    val accent: Long,
    val theme: ThemeMode,
    val backgroundUri: String?,
    val transparency: Float,
    val pressStyle: PressStyle,
    val transparencyEffects: Boolean,
    val tessLens: String,
    val keyboardPalette: ThemeMode,
)

/**
 * One of the six presets (Q5 B + the original W10M theme). [picture] names the bundled picture (`preset_<picture>`), or,
 * for the original preset, is null because the picture follows the variant (T12-10). Every value is the preset table's
 * (phase 12 Decisions, T12-2), each an H6 accept except the R12-measured original.
 */
enum class ThemePreset(
    val id: String,
    val displayName: String,
    val accent: Long,
    val theme: ThemeMode,
    val picture: String?,
    val transparency: Float,
    val pressStyle: PressStyle,
    val tessLens: String,
    val keyboardPalette: ThemeMode,
    val transparencyEffects: Boolean,
) {
    /** This shell's own out-of-box look as built and measured (X26 accent, X21 dark, X5 transparency, R3 A10). */
    DEFAULT("default", "Default", Palette.DEFAULT_ACCENT, ThemeMode.DARK, null, 0.5f, PressStyle.NONE, ThemePresets.LENS_HAL, ThemeMode.DARK, true),

    /** R12: Dark, Cobalt, img0 with accent tiles at alpha 0.40, no press style, acrylic off (§5.2, approximation). */
    W10M("w10m", Brand.PRESET_W10M_NAME, Palette.COBALT, ThemeMode.DARK, null, 0.75f, PressStyle.NONE, ThemePresets.LENS_ACCENT, ThemeMode.DARK, false),

    /** Red, the A16 swatch nearest the lens red (T12-11); the exact HAL lens. */
    HAL("hal", "HAL", 0xFFE81123, ThemeMode.DARK, "hal", 0.35f, PressStyle.NONE, ThemePresets.LENS_HAL, ThemeMode.DARK, true),

    /** Purple Shadow, nearest soft-c's three colours; Light, with the light keyboard. */
    SOFT("soft", "Soft", 0xFF8E8CD8, ThemeMode.LIGHT, "soft", 0.6f, PressStyle.P4_PRESS, ThemePresets.LENS_ACCENT, ThemeMode.LIGHT, true),

    /** Seafoam, the A16 swatch nearest WP8.1 Cyan (R12 §1); the WP8 tilt. */
    LUMIA("lumia", Brand.PRESET_LUMIA_NAME, 0xFF00B7C3, ThemeMode.DARK, "lumia", 0.5f, PressStyle.WP8_TILT, ThemePresets.LENS_ACCENT, ThemeMode.DARK, true),

    /** Purple Shadow Dark, nearest midnight-b's aurora by hue; opaque tiles, the dimmed lens, acrylic off for the battery. */
    MIDNIGHT("midnight", "Midnight", 0xFF6B69D6, ThemeMode.DARK, "midnight", 0.0f, PressStyle.NONE, ThemePresets.LENS_HAL_DIM, ThemeMode.DARK, false);

    /** The drawable this preset shows for [variant] (the original's follows the variant), or null for none. */
    fun pictureName(variant: String): String? = when {
        this == W10M -> Brand.presetPicture("w10m_$variant")
        picture != null -> Brand.presetPicture(picture)
        else -> null
    }
}

/**
 * Phase 12's theme presets (build task 3): the pure rules (the Custom rule, the item set) and the three writes a preset
 * page makes — a preset tap, the original's variant chip, and Custom — each through [ShellSettings]' narrower writes.
 */
object ThemePresets {
    const val CUSTOM = "custom"
    const val CUSTOM_NAME = "Custom"
    const val VARIANT_HERO = "hero"
    const val VARIANT_STREAKS = "streaks"
    val VARIANTS = listOf(VARIANT_HERO, VARIANT_STREAKS)

    const val LENS_HAL = "hal"
    const val LENS_ACCENT = "accent"
    const val LENS_HAL_DIM = "hal_dim"

    /** The Custom snapshot's own file (T12-12, r3 D9): never in start_theme.xml, which holds only the live set. */
    private const val SNAPSHOT_FILE = "theme_custom"

    fun byId(id: String?): ThemePreset? = ThemePreset.entries.firstOrNull { it.id == id }

    /** The preset the page shows selected: the stored id, the out-of-box Default when the key is absent, else none. */
    fun selectedId(theme: StartTheme): String = theme.themePreset ?: ThemePreset.DEFAULT.id

    fun itemsOf(theme: StartTheme) = PresetItems(
        theme.accent, theme.theme, theme.backgroundUri, theme.transparency, theme.pressStyle,
        theme.transparencyEffects, theme.tessLens, theme.keyboardPalette,
    )

    fun withItems(theme: StartTheme, items: PresetItems) = theme.copy(
        accent = items.accent, theme = items.theme, backgroundUri = items.backgroundUri, transparency = items.transparency,
        pressStyle = items.pressStyle, transparencyEffects = items.transparencyEffects, tessLens = items.tessLens,
        keyboardPalette = items.keyboardPalette,
    )

    /** The Custom rule (r3 D8): true when any of the eight items differs; the variant and the non-preset keys never count. */
    fun itemsDiffer(a: StartTheme, b: StartTheme): Boolean = itemsOf(a) != itemsOf(b)

    /** A user's item write through [ShellSettings.update]: the Custom rule applied to [next]. */
    fun afterUserChange(prev: StartTheme, next: StartTheme): StartTheme =
        if (itemsDiffer(prev, next) && next.themePreset != CUSTOM) next.copy(themePreset = CUSTOM) else next

    /** [preset]'s items, its picture given as [backgroundUri] (null = no picture). */
    fun itemsFor(preset: ThemePreset, backgroundUri: String?) = PresetItems(
        preset.accent, preset.theme, backgroundUri, preset.transparency, preset.pressStyle,
        preset.transparencyEffects, preset.tessLens, preset.keyboardPalette,
    )

    fun pictureUri(packageName: String, drawable: String) = "android.resource://$packageName/drawable/$drawable"

    /** The eight items under their start_theme.xml key names — the preset write, and the snapshot's. */
    fun putItems(edit: SharedPreferences.Editor, items: PresetItems) {
        edit.putLong("accent", items.accent)
            .putString("theme", items.theme.name)
            .putString("background", items.backgroundUri)
            .putFloat("transparency", items.transparency)
            .putString("press", items.pressStyle.name)
            .putBoolean("transparency_effects", items.transparencyEffects)
            .putString("tess_lens", items.tessLens)
            .putString("keyboard_palette", items.keyboardPalette.name)
    }

    private fun readItems(prefs: SharedPreferences) = PresetItems(
        accent = prefs.getLong("accent", Palette.DEFAULT_ACCENT),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.DARK),
        backgroundUri = prefs.getString("background", null),
        transparency = prefs.getFloat("transparency", 0.5f),
        pressStyle = runCatching { PressStyle.valueOf(prefs.getString("press", null)!!) }.getOrDefault(PressStyle.NONE),
        transparencyEffects = prefs.getBoolean("transparency_effects", true),
        tessLens = prefs.getString("tess_lens", LENS_HAL)!!,
        keyboardPalette = runCatching { ThemeMode.valueOf(prefs.getString("keyboard_palette", null)!!) }.getOrDefault(ThemeMode.DARK),
    )

    // ---------------------------------------------------------------- the writes a preset page makes

    /** A tap on [preset], on either surface: it IS the change, live (Q4 C). The page logs its own `[wizard] preset` first. */
    fun apply(context: Context, preset: ThemePreset, variant: String? = null) {
        val settings = ShellSettings.get(context)
        val current = settings.theme.value
        val chosenVariant = if (preset == ThemePreset.W10M) (variant ?: current.themePresetVariant) else null
        val picture = preset.pictureName(chosenVariant ?: VARIANT_HERO)?.let { pictureUri(context.packageName, it) }
        // T12-7's runtime fallback: a picture that cannot be decoded applies the preset with no picture, and says so.
        val usable = picture?.takeIf { decodable(context, it) }
        snapshotIfCustom(context, current)
        settings.applyPreset(itemsFor(preset, usable), preset.id, chosenVariant)
        Diagnostics.add("theme", "preset ${preset.displayName} applied" + if (picture != null && usable == null) ": no picture" else "")
        if (chosenVariant != null && variant != null) Diagnostics.add("theme", "preset w10m variant $chosenVariant applied")
    }

    /** The original preset's variant chip (T12-10): not an item change, so the preset stays `w10m`. */
    fun chooseVariant(context: Context, variant: String) {
        val settings = ShellSettings.get(context)
        if (settings.theme.value.themePreset != ThemePreset.W10M.id) {
            apply(context, ThemePreset.W10M, variant)
            return
        }
        val picture = pictureUri(context.packageName, ThemePreset.W10M.pictureName(variant)!!)
        settings.applyVariant(variant, picture.takeIf { decodable(context, it) })
        Diagnostics.add("theme", "preset w10m variant $variant applied")
    }

    fun hasSnapshot(context: Context): Boolean = snapshotPrefs(context).contains("saved_at")

    /** `preset:Custom` (T12-12): restores the last custom set, its picture and grant included. */
    fun restoreCustom(context: Context): Boolean {
        val snap = snapshotPrefs(context)
        if (!snap.contains("saved_at")) return false
        val settings = ShellSettings.get(context)
        if (settings.theme.value.themePreset == CUSTOM) return false // already the live set: nothing to bring back
        settings.applyPreset(readItems(snap), CUSTOM)
        Diagnostics.add("theme", "preset Custom restored")
        return true
    }

    /**
     * A preset tap replacing a custom set saves that set as the Custom snapshot (T12-12), replacing the older one, and then
     * releases a `content://` read grant the shell took that nothing references any more — never an `android.resource://`
     * URI (r3 D15: a preset picture carries no grant, and one URI can back both the background and the photo frame).
     */
    private fun snapshotIfCustom(context: Context, current: StartTheme) {
        if (current.themePreset != CUSTOM) return
        val snap = snapshotPrefs(context)
        val older = snap.getString("background", null)
        val edit = snap.edit()
        putItems(edit, itemsOf(current))
        edit.putLong("saved_at", System.currentTimeMillis()).commit()
        Diagnostics.add("theme", "custom snapshot saved (background=${current.backgroundUri})")
        if (older != null && older != current.backgroundUri) releaseIfUnreferenced(context, older, current)
    }

    private fun releaseIfUnreferenced(context: Context, uri: String, current: StartTheme) {
        if (!uri.startsWith("content://")) return
        // After the preset write the live background is the preset's picture, so the references left are the photo frame
        // and the new snapshot (which holds current.backgroundUri).
        if (uri == current.photoFrameUri || uri == current.backgroundUri) return
        val resolver = context.contentResolver
        val held = resolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission }
        if (!held) return
        runCatching { resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onSuccess { Diagnostics.add("theme", "custom snapshot replaced: released the read grant on $uri") }
            .onFailure { Diagnostics.add("theme", "custom snapshot replaced: releasing $uri failed: $it") }
    }

    private fun snapshotPrefs(context: Context) = context.getSharedPreferences(SNAPSHOT_FILE, Context.MODE_PRIVATE)

    /** A bounds-only decode: enough to know the bundled picture is there and is an image. */
    private fun decodable(context: Context, uri: String): Boolean = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        opts.outWidth > 0 && opts.outHeight > 0
    }.getOrDefault(false)
}
