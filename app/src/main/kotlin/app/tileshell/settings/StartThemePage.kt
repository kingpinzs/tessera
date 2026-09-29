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

/**
 * Settings > Start + theme (phase 01 Scope): the theme presets (phase 12's ADD, T12-2), then the items they set — background,
 * mode, accent, effects, press effect, Tess's look, keyboard — then show more tiles, profiles and the Photos tile.
 */
@Composable
fun StartThemePage() {
    val context = LocalContext.current
    val settings = ShellSettings.get(context)
    val theme by settings.theme.collectAsState()
    val colors = LocalShellColors.current
    // The picture frame's photo, taken the same way the Start background is: a persisted read grant on the
    // picker's URI, so the tile keeps drawing it whether or not the shell holds gallery access at all.
    val pickFrame = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            settings.update { it.copy(photoFrameUri = uri.toString()) }
        }
    }

    PageHeader(Glyph.PALETTE, "Start + theme")

    // Phase 12 (T12-2): the same presets the wizard's last page shows, each applied the moment it is tapped.
    SectionHeader("Themes")
    Box(Modifier.testTag("theme_presets")) { ThemePresetsGrid("theme_preset") }
    ThemeItems()

    SectionHeader("Start")
    ToggleRow("Show more tiles", theme.mediumColumns == 3, "theme_show_more_tiles") { on -> settings.update { it.copy(mediumColumns = if (on) 3 else 2) } }
    ToggleRow("Show work and private apps", theme.showWorkAndPrivateApps, "theme_show_profiles") { on -> settings.update { it.copy(showWorkAndPrivateApps = on) } }

    // INDEX Change Log 2026-09-21 item 4. It lives here rather than behind a long-press on the tile
    // because Start tiles have no long-press menu — a long press is how edit mode is entered (R6 §1.1) —
    // and this is two rows next to the Start background picker that already does exactly this dance.
    SectionHeader("Photos tile")
    ToggleRow("Slideshow", theme.photosSlideshow, "photos_slideshow") { on ->
        settings.update { it.copy(photosSlideshow = on) }
    }
    if (theme.photoFrameUri == null) {
        PressRow({ pickFrame.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth().height(44.dp).testTag("photos_frame_choose")) {
            BasicText("Choose a main photo", style = ShellType.body.copy(color = colors.accent), modifier = Modifier.padding(start = 12.dp, top = 11.dp))
        }
    } else {
        PressRow({ settings.update { it.copy(photoFrameUri = null) } }, Modifier.fillMaxWidth().height(44.dp).testTag("photos_frame_remove")) {
            BasicText("Remove main photo", style = ShellType.body.copy(color = colors.accent), modifier = Modifier.padding(start = 12.dp, top = 11.dp))
        }
        BasicText(
            "The Photos tile shows this photo and doesn't flip.",
            style = ShellType.caption.copy(color = colors.subtleText),
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp).testTag("photos_frame_note"),
        )
    }

    Spacer(Modifier.height(24.dp))
}
