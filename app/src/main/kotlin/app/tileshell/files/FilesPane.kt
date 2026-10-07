package app.tileshell.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.tokens.ShellType

/**
 * The ≡ pane (r11/files.md 1.3; build task 2): W10M's File Explorer had no root page — its entry points are this
 * overlay, 256 epx wide, #171717, covering the location bar and the app bar from under the status bar to the nav bar.
 * Rows: Recent, This Device, one per other mounted volume named by its label, and the Recycle Bin (P4, Y1).
 *
 * No scrim is drawn (E11: the pixels right of it are the page's own); a tap off it closes it. It opens by a reveal —
 * its right edge grows while the labels stay where they are (pass 2 §5 row 4.1, `[motion] files_pane_open`) — and
 * closes by a one-frame cut (row 4.2, `[motion] files_pane_close`, written by [FilesState.closePane]). On a cold start
 * it is simply there, open, on the first frame (pass 2 §1 UNMEASURED-4).
 */
@Composable
fun FilesPane(state: FilesState) {
    // 1 from the first frame when the page starts with the pane open: a cold start does not animate it.
    var reveal by remember { mutableFloatStateOf(if (state.paneOpen) 1f else 0f) }
    var settled by remember { mutableStateOf(state.paneOpen) }
    LaunchedEffect(state.paneOpen) {
        if (!state.paneOpen) {
            reveal = 0f
            settled = false
        } else if (!settled) {
            MotionClock.animate("files_pane_open", FilesMetrics.PANE_OPEN_MS, FilesEasing.PaneOpen) { reveal = it }
            settled = true
        }
    }
    if (!state.paneOpen) return
    OverlayLayer(active = { state.paneOpen }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().modalOverlay(onTapOff = { dismissOverlay { state.closePane() } })) {
            // The reveal: this box is the part of the pane already uncovered; the pane inside it never moves.
            Box(Modifier.width((FilesMetrics.PANE_W * reveal).dp).fillMaxHeight().clipToBounds()) {
                Column(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(FilesMetrics.PANE_W.dp).fillMaxHeight()
                        .background(FilesMetrics.PANE_FILL).testTag("files_pane")
                        // The pane's own surface is not "off the pane".
                        .overlayItem(onPressedChange = {}, onRun = {}),
                ) {
                    PaneMenuButton { dismissOverlay { state.closePane() } }
                    val current = FilesNav.paneId(state.location)
                    PaneRow(FilesNav.PANE_RECENT, "Recent", FilesGlyph.RECENT, current) { dismissOverlay { state.choose(FilesLocation.Recent) } }
                    for (v in state.volumes) {
                        val id = if (v.primary) FilesNav.PANE_DEVICE else v.uuid
                        PaneRow(id, v.paneLabel, if (v.primary) FilesGlyph.DEVICE else FilesGlyph.SD_CARD, current) {
                            dismissOverlay { state.choose(FilesLocation.Folder(v.uuid, v.root)) }
                        }
                    }
                    PaneRow(FilesNav.PANE_BIN, "Recycle Bin", FilesGlyph.BIN, current) { dismissOverlay { state.choose(FilesLocation.Bin) } }
                }
            }
        }
    }
}

/** The pane's top 48 epx hold its own ≡ (F 1.3.7), over the location bar's: a tap closes the pane. */
@Composable
private fun PaneMenuButton(onRun: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(FilesMetrics.BAR.dp)) {
        Box(
            Modifier.size((FilesMetrics.PANE_GLYPH_CX * 2).dp, FilesMetrics.BAR.dp)
                .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
                .testTag("files_pane_menu").semantics { role = Role.Button }
                .overlayItem(onPressedChange = { pressed = it }, onRun = onRun),
            contentAlignment = Alignment.Center,
        ) { FilesGlyphText(FilesGlyph.MENU, FilesMetrics.BAR_GLYPH, Color.White) }
    }
}

/** One 48-epx row (F 1.3.8–1.3.11): glyph centred at x 24, label from x 60, the current row the accent at 60 % over the pane. */
@Composable
private fun PaneRow(id: String, label: String, glyph: String, current: String?, onRun: () -> Unit) {
    val colors = LocalShellColors.current
    var pressed by remember { mutableStateOf(false) }
    val isCurrent = id == current
    val fill = when {
        isCurrent -> colors.accent.copy(alpha = FilesMetrics.PANE_SELECTED_ACCENT).compositeOver(FilesMetrics.PANE_FILL)
        pressed -> Color.White.copy(alpha = ROW_PRESS_ALPHA).compositeOver(FilesMetrics.PANE_FILL)
        else -> Color.Transparent
    }
    Box(
        Modifier.fillMaxWidth().height(FilesMetrics.PANE_ROW.dp).background(fill)
            .testTag("files_pane:$id")
            // Role.Tab, as the hub's pane: without it Compose reports `selected` as a checkbox state.
            .semantics { role = Role.Tab; selected = isCurrent }
            .overlayItem(onPressedChange = { pressed = it }, onRun = onRun),
    ) {
        Box(Modifier.size((FilesMetrics.PANE_GLYPH_CX * 2).dp, FilesMetrics.PANE_ROW.dp), contentAlignment = Alignment.Center) {
            FilesGlyphText(glyph, 16f, Color.White, Modifier.testTag("files_pane_glyph:$id"))
        }
        BasicText(
            label,
            Modifier.align(Alignment.CenterStart).padding(start = FilesMetrics.PANE_LABEL_LEFT.dp, end = 12.dp).testTag("files_pane_label:$id"),
            style = ShellType.body.copy(color = Color.White), maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
