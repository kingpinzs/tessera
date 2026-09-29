package app.tileshell.settings

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.onboarding.taggedText
import app.tileshell.prefs.PressStyle
import app.tileshell.prefs.ShellSettings
import app.tileshell.prefs.StartTheme
import app.tileshell.prefs.ThemeMode
import app.tileshell.prefs.ThemePreset
import app.tileshell.prefs.ThemePresets
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.Palette
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Phase 12's theme presets page content (build task 3, T12-2): ONE composable drawn by the wizard's last page and by
 * Settings > Start + theme (the ADD to phase 01's page), with two tag prefixes — `preset:<name>` on the wizard's page and
 * `theme_preset:<name>` on Start + theme; the original preset's variant chips are `<prefix>_variant:<v>`. Every look value
 * here is a P4 design judged in H3 / H6 / H9.
 */

private const val CARD_W = 104f
private const val CARD_H = 124f
private const val CARD_GAP = 8f

/**
 * The six presets and Custom, three to a row. A tap APPLIES the preset to the whole shell at once (Q4 C): the preview is
 * the real thing. [onTap] runs first with the entry's name, so the wizard's page can write its `[wizard] preset` line.
 */
@Composable
fun ThemePresetsGrid(tagPrefix: String, onTap: (String) -> Unit = {}) {
    val context = LocalContext.current
    val theme by ShellSettings.get(context).theme.collectAsState()
    val selected = ThemePresets.selectedId(theme)
    val hasSnapshot = ThemePresets.hasSnapshot(context)
    val entries: List<ThemePreset?> = ThemePreset.entries + null // null = Custom
    Column(Modifier.padding(start = 12.dp, end = 12.dp)) {
        entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CARD_GAP.dp)) {
                row.forEach { preset ->
                    Column {
                        if (preset == null) {
                            CustomCard(tagPrefix, theme, selected == ThemePresets.CUSTOM, hasSnapshot || selected == ThemePresets.CUSTOM) {
                                onTap(ThemePresets.CUSTOM_NAME)
                                ThemePresets.restoreCustom(context)
                            }
                        } else {
                            PresetCard(tagPrefix, preset, theme.themePresetVariant, selected == preset.id) {
                                onTap(preset.displayName)
                                ThemePresets.apply(context, preset)
                            }
                            if (preset == ThemePreset.W10M) VariantChips(tagPrefix, theme)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PresetCard(tagPrefix: String, preset: ThemePreset, variant: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val picture = preset.pictureName(variant)
    val bitmap = rememberThumb(context, picture)
    val dark = preset.theme == ThemeMode.DARK
    // The preview IS the preset's own look: its background, and accent tiles at the alpha they take over its picture.
    val tileAlpha = if (bitmap != null) 1f - preset.transparency * 0.8f else 1f
    Card(
        tag = "$tagPrefix:${preset.displayName}", name = preset.displayName, selected = selected, enabled = true,
        background = if (dark) Palette.darkBackground else Palette.lightBackground, bitmap = bitmap,
        accent = Color(preset.accent), tileAlpha = tileAlpha, onClick = onClick,
    )
}

/** Custom (T12-12): the last custom set, tappable when a snapshot exists; selected once any item is changed. */
@Composable
private fun CustomCard(tagPrefix: String, theme: StartTheme, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        tag = "$tagPrefix:${ThemePresets.CUSTOM_NAME}", name = ThemePresets.CUSTOM_NAME, selected = selected, enabled = enabled,
        background = if (theme.theme == ThemeMode.DARK) Palette.darkChromeMedium else Palette.lightChromeMedium, bitmap = null,
        accent = Color(theme.accent), tileAlpha = 1f, onClick = { if (enabled) onClick() },
    )
}

@Composable
private fun Card(
    tag: String,
    name: String,
    selected: Boolean,
    enabled: Boolean,
    background: Color,
    bitmap: ImageBitmap?,
    accent: Color,
    tileAlpha: Float,
    onClick: () -> Unit,
) {
    val colors = LocalShellColors.current
    Column(
        Modifier
            .width(CARD_W.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .taggedText(tag, name, selected),
    ) {
        Box(
            Modifier.size(CARD_W.dp, CARD_H.dp).background(background)
                .let { if (selected) it.border(3.dp, colors.text) else it.border(1.dp, colors.subtleText.copy(alpha = 0.3f)) },
        ) {
            if (bitmap != null) Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Three mini tiles, a medium pair over a wide one: Start's grid in small.
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(30.dp).background(accent.copy(alpha = tileAlpha)))
                    Box(Modifier.size(30.dp).background(accent.copy(alpha = tileAlpha)))
                }
                Box(Modifier.size(64.dp, 30.dp).background(accent.copy(alpha = tileAlpha)))
            }
        }
        BasicText(
            name,
            style = ShellType.caption.copy(color = colors.text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The original preset's two pictures (Q8 C, T12-10): two chips under its entry; choosing one is not a Custom change. */
@Composable
private fun VariantChips(tagPrefix: String, theme: StartTheme) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ThemePresets.VARIANTS.forEach { v ->
            val bitmap = rememberThumb(context, ThemePreset.W10M.pictureName(v))
            val chosen = theme.themePresetVariant == v
            Box(
                Modifier.size(36.dp).background(Palette.darkBackground)
                    .let { if (chosen) it.border(2.dp, colors.text) else it }
                    .pointerInput(v) { detectTapGestures { ThemePresets.chooseVariant(context, v) } }
                    .taggedText("${tagPrefix}_variant:$v", v, chosen),
            ) {
                if (bitmap != null) Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(2.dp))
            }
        }
    }
}

/** A preset picture decoded small for its card (1/8 of 1872 x 4056), once per process. */
@Composable
private fun rememberThumb(context: Context, drawable: String?): ImageBitmap? {
    val state = produceState(drawable?.let { Thumbs.cached(it) }, drawable) {
        value = drawable?.let { Thumbs.load(context, it) }
    }
    return state.value
}

private object Thumbs {
    private val cache = HashMap<String, ImageBitmap>()

    @Synchronized
    fun cached(name: String): ImageBitmap? = cache[name]

    suspend fun load(context: Context, name: String): ImageBitmap? = cached(name) ?: withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(ThemePresets.pictureUri(context.packageName, name))
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })
            }?.asImageBitmap()
        }.getOrNull()?.also { synchronized(this@Thumbs) { cache[name] = it } }
    }
}

/**
 * The items below the presets (Q4: each still changeable, after which the preset reads Custom): Start + theme's existing
 * rows — background, Dark / Light, the accent grid, tile transparency, press style — phase 13's Transparency effects, and
 * phase 12's two new rows, "Tess's look" and "Keyboard" (P4, H6). Every change goes through ShellSettings.update, which
 * applies the Custom rule.
 */
@Composable
fun ThemeItems() {
    val context = LocalContext.current
    val settings = ShellSettings.get(context)
    val theme by settings.theme.collectAsState()
    val colors = LocalShellColors.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            settings.update { it.copy(backgroundUri = uri.toString()) }
        }
    }

    SectionHeader("Background")
    PressRow({ pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth().height(44.dp).testTag("theme_background_choose")) {
        BasicText("Choose a picture", style = ShellType.body.copy(color = colors.accent), modifier = Modifier.padding(start = 12.dp, top = 11.dp))
    }
    if (theme.backgroundUri != null) {
        PressRow({ settings.update { it.copy(backgroundUri = null) } }, Modifier.fillMaxWidth().height(44.dp).testTag("theme_background_remove")) {
            BasicText("Remove picture", style = ShellType.body.copy(color = colors.accent), modifier = Modifier.padding(start = 12.dp, top = 11.dp))
        }
        SliderRow("Tile transparency", theme.transparency, "theme_transparency") { v -> settings.update { it.copy(transparency = v) } }
    }

    SectionHeader("Choose your mode")
    RadioRow("Dark", theme.theme == ThemeMode.DARK, "theme_mode_dark") { settings.update { it.copy(theme = ThemeMode.DARK) } }
    RadioRow("Light", theme.theme == ThemeMode.LIGHT, "theme_mode_light") { settings.update { it.copy(theme = ThemeMode.LIGHT) } }

    SectionHeader("Accent color")
    // R3 A16: 6 x 8 swatches, 44 epx square, 4-epx horizontal gap, ≈7-epx vertical gap, left edge 12 epx; Cobalt, the 49th
    // (phase 12 Q7 A), opens a ninth row in the first column (R12 §6).
    Column(Modifier.padding(start = 12.dp).testTag("theme_accent_grid")) {
        Palette.accents.chunked(6).forEach { row ->
            Row(Modifier.padding(bottom = 7.dp)) {
                row.forEach { (name, argb) ->
                    val selected = theme.accent == argb
                    Box(
                        Modifier.size(44.dp).background(Color(argb))
                            .let { if (selected) it.border(3.dp, colors.text) else it }
                            .pointerInput(argb) { detectTapGestures { settings.update { it.copy(accent = argb) } } }
                            .testTag("accent:$name")
                            .semantics { contentDescription = name; this.selected = selected },
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }

    // Phase 13 (interview Q2 A): Windows 10's own wording, from Settings > Personalization > Colors (H5).
    SectionHeader("Effects")
    ToggleRow("Transparency effects", theme.transparencyEffects, "theme_transparency_effects") { on ->
        settings.update { it.copy(transparencyEffects = on) }
    }

    SectionHeader("Tile press effect")
    RadioRow("None (Windows 10 Mobile)", theme.pressStyle == PressStyle.NONE, "press_none") { settings.update { it.copy(pressStyle = PressStyle.NONE) } }
    RadioRow("Tilt (Windows Phone 8)", theme.pressStyle == PressStyle.WP8_TILT, "press_tilt") { settings.update { it.copy(pressStyle = PressStyle.WP8_TILT) } }
    RadioRow("Press", theme.pressStyle == PressStyle.P4_PRESS, "press_p4") { settings.update { it.copy(pressStyle = PressStyle.P4_PRESS) } }

    // Phase 12 (T12-2, r3 D7): Tess's lens — the HAL lens as built, or the lens in the accent's hue. The dimmed lens is
    // Midnight's alone, so neither row reads selected while it is on.
    SectionHeader("Tess's look")
    RadioRow("HAL", theme.tessLens == ThemePresets.LENS_HAL, "theme_tess_lens_hal") { settings.update { it.copy(tessLens = ThemePresets.LENS_HAL) } }
    RadioRow("Accent color", theme.tessLens == ThemePresets.LENS_ACCENT, "theme_tess_lens_accent") { settings.update { it.copy(tessLens = ThemePresets.LENS_ACCENT) } }

    SectionHeader("Keyboard")
    RadioRow("Dark", theme.keyboardPalette == ThemeMode.DARK, "theme_keyboard_dark") { settings.update { it.copy(keyboardPalette = ThemeMode.DARK) } }
    RadioRow("Light", theme.keyboardPalette == ThemeMode.LIGHT, "theme_keyboard_light") { settings.update { it.copy(keyboardPalette = ThemeMode.LIGHT) } }
}
