package app.tileshell.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import app.tileshell.brand.Glyph
import app.tileshell.ime.Handedness
import app.tileshell.ime.KeyboardSettings

/**
 * Phase 05 build tasks 9 and 10: the keyboard's Settings page. W10M kept these under Settings > Time &
 * language > Keyboard > "More keyboard settings" (R6 §2.5.5 K1 @ 71 s); here they sit in Start settings
 * with the rest of the shell's options, drawn with the shell's own toggle and radio rows.
 *
 * The keyboard reads them each time it shows, through KeyboardConfigProvider, so a change here reaches
 * the next keyboard that opens.
 */
@Composable
fun KeyboardPage() {
    val settings = KeyboardSettings.get(LocalContext.current)
    val config by settings.config.collectAsState()
    PageHeader(Glyph.KEYBOARD, "Keyboard")
    // Decisions "Key sounds and vibration" (approximation, H12): each with its own toggle, both On.
    ToggleRow("Play key sounds", config.sounds, "keyboard_sounds") { on -> settings.update { it.copy(sounds = on) } }
    ToggleRow("Vibrate when I press a key", config.vibration, "keyboard_vibration") { on -> settings.update { it.copy(vibration = on) } }
    // R6 §2.5.5 (LOW): "Cursor controller: Right handed usage"; the other label is an agent pick (H14),
    // right-handed the default (H16).
    SectionHeader("Cursor controller")
    RadioRow("Right handed usage", config.handedness == Handedness.RIGHT, "keyboard_cursor_right") { settings.update { it.copy(handedness = Handedness.RIGHT) } }
    RadioRow("Left handed usage", config.handedness == Handedness.LEFT, "keyboard_cursor_left") { settings.update { it.copy(handedness = Handedness.LEFT) } }
    // R6 §2.6.5 (LOW): K1's settings checkbox, default On (approximation, H17).
    ToggleRow("Switch back to letters after I type an emoticon", config.switchBackAfterEmoji, "keyboard_switch_back") { on ->
        settings.update { it.copy(switchBackAfterEmoji = on) }
    }
}
