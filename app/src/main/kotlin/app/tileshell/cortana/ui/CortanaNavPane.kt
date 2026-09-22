package app.tileshell.cortana.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay

/** The ≡ pane's destinations. Phase 09 ADDs Notebook between HOME and REMINDERS (R7 §3.1.1). */
enum class CortanaDestination { HOME, REMINDERS, SETTINGS }

/**
 * Derived (approximation, H23): how far below a pane item's 48-epx fill its label's cap top sits.
 * R7 §3.1.8 gives the label cap tops (72 / 119.5) but no fill top; [CortanaUi.PANE_TOP_GROUP_TOP_EPX]
 * puts the first fill flush with the 52-epx header band, which makes the inset 20 epx. The bottom
 * group reuses it, because R7 §3.1.9 gives Settings' cap top and nothing about its fill either.
 */
private val ITEM_CAP_INSET_EPX = CortanaUi.PANE_ITEM_CAP_TOPS_EPX[0] - CortanaUi.PANE_TOP_GROUP_TOP_EPX

/**
 * Cortana's ≡ navigation pane (R7 §3.1).
 *
 * **Bounds contract:** R7 §3.1.9 measures Settings' cap top *from the screen bottom*, so this
 * composable must be given the **full screen height** — drawn status bar and drawn nav bar included.
 * It draws no bars of its own; it fills only its own 256-epx strip (§3.1.5) and leaves the page
 * beside it untouched, because §3.1.6 shows W10M does not dim it.
 *
 * W10M's **Feedback** item is left out (phase 03 Decisions, approximation A11/H23) and its slot — the
 * one whose label cap top would sit [CortanaUi.PANE_EMPTY_SLOT_CAP_TOP_ABOVE_BOTTOM_EPX] epx above the
 * screen bottom — stays **empty**: nothing is composed there, so E15's dump finds no node in it.
 *
 * @param onSelect fired at touch-down; the host runs the transition.
 */
@Composable
fun CortanaNavPane(
    open: Boolean,
    current: CortanaDestination,
    onSelect: (CortanaDestination) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!open) return
    val accent = LocalShellColors.current.accent
    // R7 §3.1.12: the item the user chose keeps an accent fill until the host blacks the screen out.
    var chosen by remember { mutableStateOf<CortanaDestination?>(null) }
    var pressed by remember { mutableStateOf<CortanaDestination?>(null) }

    // R7 §3.1.10: slides in from the left, ease-out, settling 250 ms after its first frame.
    val slide = remember { Animatable(-CortanaUi.PANE_WIDTH_EPX) }
    LaunchedEffect(Unit) {
        slide.animateTo(0f, tween(CortanaUi.PANE_SLIDE_MS, easing = LinearOutSlowInEasing))
    }

    Box(modifier.fillMaxSize()) {
        // Tapping the page beside the pane closes it (approximation, H23: R7 only rules out dimming).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })

        BoxWithConstraints(
            Modifier
                .offset(x = slide.value.dp)
                .width(CortanaUi.PANE_WIDTH_EPX.dp)
                .fillMaxHeight()
                .background(CortanaUi.PAGE_BG)
                // The pane is modal over its own strip: a tap inside it must not reach the dismiss catcher.
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("cortana_pane"),
        ) {
            val paneHeightEpx = maxHeight.value

            // ---- header band: the ≡ button and "CORTANA" (R7 §3.1.7)
            MenuButton(onDismiss, Modifier.testTag("cortana_pane_menu"))
            CapCentreText(
                text = "CORTANA",
                style = ShellType.base.copy(color = CortanaUi.ROW_TITLE_COLOR),
                capCentreEpx = CortanaUi.PANE_TITLE_CAP_CENTRE_EPX,
                leftEpx = CortanaUi.PANE_TITLE_LEFT_EPX,
                modifier = Modifier.testTag("cortana_pane_title"),
            )

            // ---- top group: Home, Reminders (R7 §3.1.1, §3.1.8)
            PaneItem(
                label = "Home",
                tag = "cortana_pane_item_home",
                destination = CortanaDestination.HOME,
                fillTopEpx = CortanaUi.PANE_TOP_GROUP_TOP_EPX,
                capTopEpx = CortanaUi.PANE_ITEM_CAP_TOPS_EPX[0],
                accent = accent,
                current = current,
                chosen = chosen,
                pressed = pressed,
                icon = { color, mod -> CortanaIcons.Font(Glyph.HOME, color, CortanaUi.PANE_ICON_EPX, mod) },
                onDown = { pressed = CortanaDestination.HOME; chosen = CortanaDestination.HOME; onSelect(CortanaDestination.HOME) },
            )
            PaneItem(
                label = "Reminders",
                tag = "cortana_pane_item_reminders",
                destination = CortanaDestination.REMINDERS,
                fillTopEpx = CortanaUi.PANE_TOP_GROUP_TOP_EPX + CortanaUi.PANE_ITEM_PITCH_EPX,
                capTopEpx = CortanaUi.PANE_ITEM_CAP_TOPS_EPX[1],
                accent = accent,
                current = current,
                chosen = chosen,
                pressed = pressed,
                icon = { color, mod -> CortanaIcons.Font(Glyph.LIGHTBULB, color, CortanaUi.PANE_ICON_EPX, mod) },
                onDown = { pressed = CortanaDestination.REMINDERS; chosen = CortanaDestination.REMINDERS; onSelect(CortanaDestination.REMINDERS) },
            )

            // ---- bottom group: Settings only. The Feedback slot below it stays empty (A11/H23).
            val settingsCapTop = paneHeightEpx - CortanaUi.PANE_SETTINGS_CAP_TOP_ABOVE_BOTTOM_EPX
            PaneItem(
                label = "Settings",
                tag = "cortana_pane_item_settings",
                destination = CortanaDestination.SETTINGS,
                fillTopEpx = settingsCapTop - ITEM_CAP_INSET_EPX,
                capTopEpx = settingsCapTop,
                accent = accent,
                current = current,
                chosen = chosen,
                pressed = pressed,
                icon = { color, mod -> CortanaIcons.Font(Glyph.SETTINGS, color, CortanaUi.PANE_ICON_EPX, mod) },
                onDown = { pressed = CortanaDestination.SETTINGS; chosen = CortanaDestination.SETTINGS; onSelect(CortanaDestination.SETTINGS) },
            )
        }
    }
}

/**
 * One pane row: a 48-epx fill across the whole pane (R7 §3.1.8), a ≈16-epx icon from x 17–18 and a
 * label whose **cap top** sits at [capTopEpx] from the pane's top (R7 §3.1.8, §3.1.9).
 *
 * Fills, in the order R7 §3.1.6 and §3.1.12 put them: the current page's item is an accent fill with
 * white text and icon; an item the finger is on but that has not taken effect yet is (63,68,64); the
 * item the user just chose flips to the accent fill [CortanaUi.PANE_PRESS_TO_ACCENT_MS] after
 * touch-down, one frame before the host blacks the screen out at 350 ms.
 */
@Composable
private fun PaneItem(
    label: String,
    tag: String,
    destination: CortanaDestination,
    fillTopEpx: Float,
    capTopEpx: Float,
    accent: Color,
    current: CortanaDestination,
    chosen: CortanaDestination?,
    pressed: CortanaDestination?,
    icon: @Composable (Color, Modifier) -> Unit,
    onDown: () -> Unit,
) {
    // R7 §3.1.12: the accent fill lands a frame before the blackout, not at touch-down.
    var chosenAccent by remember { mutableStateOf(false) }
    LaunchedEffect(chosen) {
        if (chosen == destination) {
            delay(CortanaUi.PANE_PRESS_TO_ACCENT_MS)
            chosenAccent = true
        } else {
            chosenAccent = false
        }
    }

    val isCurrent = current == destination
    val showAccent = isCurrent || chosenAccent
    val showPressed = !showAccent && pressed == destination
    val fill = when {
        showAccent -> accent
        showPressed -> CortanaUi.PRESSED_FILL
        else -> Color.Transparent
    }
    val ink = if (showAccent) CortanaUi.SELECTED_TEXT else CortanaUi.ROW_TITLE_COLOR

    Box(
        Modifier
            .offset(y = fillTopEpx.dp)
            .fillMaxWidth()
            .height(CortanaUi.PANE_ITEM_FILL_EPX.dp)
            .background(fill)
            .pointerInput(destination) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDown()
                }
            }
            .testTag(tag)
            .semantics { role = Role.Tab; selected = isCurrent },
    ) {
        icon(
            ink,
            Modifier
                .offset(
                    x = CortanaUi.PANE_ICON_LEFT_EPX.dp,
                    // Not measured (H23): the icon is centred on the 48-epx fill.
                    y = ((CortanaUi.PANE_ITEM_FILL_EPX - CortanaUi.PANE_ICON_EPX) / 2f).dp,
                )
                .size(CortanaUi.PANE_ICON_EPX.dp),
        )
    }
    // The label is placed against the PANE's top, not the fill's, so it lands on R7's measured cap top
    // whatever the fill does.
    CapTopText(
        text = label,
        style = ShellType.body.copy(color = ink),
        capTopEpx = capTopEpx,
        leftEpx = CortanaUi.PANE_LABEL_LEFT_EPX,
    )
}
