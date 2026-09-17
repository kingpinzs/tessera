package app.tileshell.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.prefs.PressStyle
import app.tileshell.prefs.ShellSettings
import app.tileshell.prefs.ThemeMode
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.Palette
import app.tileshell.ui.tokens.ShellType

/** Settings > Start + theme (phase 01 Scope): accent, background, transparency, show more tiles, theme, press effect, profiles. */
@Composable
fun StartThemePage() {
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

    PageHeader(Glyph.PALETTE, "Start + theme")

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
    // R3 A16: 6 x 8 swatches, 44 epx square, 4-epx horizontal gap, ≈7-epx vertical gap, left edge 12 epx.
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

    SectionHeader("Start")
    ToggleRow("Show more tiles", theme.mediumColumns == 3, "theme_show_more_tiles") { on -> settings.update { it.copy(mediumColumns = if (on) 3 else 2) } }
    ToggleRow("Show work and private apps", theme.showWorkAndPrivateApps, "theme_show_profiles") { on -> settings.update { it.copy(showWorkAndPrivateApps = on) } }

    SectionHeader("Tile press effect")
    RadioRow("None (Windows 10 Mobile)", theme.pressStyle == PressStyle.NONE, "press_none") { settings.update { it.copy(pressStyle = PressStyle.NONE) } }
    RadioRow("Tilt (Windows Phone 8)", theme.pressStyle == PressStyle.WP8_TILT, "press_tilt") { settings.update { it.copy(pressStyle = PressStyle.WP8_TILT) } }
    RadioRow("Press", theme.pressStyle == PressStyle.P4_PRESS, "press_p4") { settings.update { it.copy(pressStyle = PressStyle.P4_PRESS) } }
    Spacer(Modifier.height(24.dp))
}
