package app.tileshell.music

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.tileshell.applist.AppMenuMetrics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.tokens.ShellType

/**
 * The playlist surfaces (phase 10 build task 8): the hold menu that carries every playlist verb, and
 * the box that names one.
 *
 * Both are the shell's existing idioms rather than new ones. The menu is the app list's context menu
 * (phase 02 build task 3, approximation H21) — a full-width band in the theme background, anchored
 * under the row that was held, items in the 15-epx body class at 12-epx insets — and the hold that
 * opens it is [app.tileshell.applist.HoldRow], so one hold means one thing everywhere in the shell.
 *
 * Q7's four verbs live here: **create** is the playlists pivot's first row, **rename** and **delete**
 * are the menu on a playlist, and **reorder** is move-up / move-down on a track inside one. Reorder is
 * a menu rather than a drag deliberately: dragging is Start's edit-mode vocabulary and means
 * rearranging the tile grid, and teaching it a second meaning inside a list is how two gestures start
 * fighting over the same finger.
 */
@Composable
fun MusicMenu(anchorPx: Float, items: List<MenuEntry>, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    Box(
        Modifier
            .fillMaxSize()
            .testTag("music_menu_scrim")
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onDismiss()
                }
            },
    ) {
        Layout(
            content = {
                Column(Modifier.fillMaxWidth().background(colors.background).testTag("music_menu")) {
                    items.forEach { entry -> MenuItem(entry) }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            // Full width, only as tall as its items: passing the incoming fixed constraints on would
            // stretch the band's background over the whole page.
            val band = measurables[0].measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val room = (constraints.maxHeight - band.height).coerceAtLeast(0)
                band.place(0, anchorPx.toInt().coerceIn(0, room))
            }
        }
    }
}

data class MenuEntry(val label: String, val tag: String, val onPick: () -> Unit)

@Composable
private fun MenuItem(entry: MenuEntry) {
    val colors = LocalShellColors.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(entry) {
                awaitEachGesture {
                    // Consumed, so the scrim behind the band does not also read this as a tap outside.
                    awaitFirstDown().consume()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) {
                        up.consume()
                        entry.onPick()
                    }
                }
            }
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
            .testTag(entry.tag),
    ) {
        BasicText(entry.label, style = ShellType.body.copy(color = colors.text), modifier = Modifier.padding(AppMenuMetrics.INSET))
    }
}

/**
 * Naming a playlist: a band with a caption and one text box, over the page.
 *
 * The keyboard's own Done key commits it, and a tap outside cancels — the same two ways out the folder
 * name box on Start gives (R6 §1.7.2). There is no OK button because there is nowhere W10M put one on
 * this screen and the IME already carries it.
 */
@Composable
fun PlaylistNameBox(caption: String, initial: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    val colors = LocalShellColors.current
    var value by remember { mutableStateOf(TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("music_name_scrim")
            .pointerInput(onCancel) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onCancel()
                }
            },
        Alignment.TopStart,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(AppMenuMetrics.INSET)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                .testTag("music_name_box"),
        ) {
            BasicText(caption, style = ShellType.caption.copy(color = colors.subtleText))
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = ShellType.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(value.text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .focusRequester(focus)
                    .testTag("music_name_field"),
            )
        }
    }
}
