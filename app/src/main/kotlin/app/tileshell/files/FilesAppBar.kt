package app.tileshell.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tileshell.cortana.ui.CortanaEaseOut
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.tokens.ShellType

/**
 * The W10M app bar (r11/files.md 1.7): 48 epx, #1F1F1F, docked on the nav bar; the buttons right-aligned at the fixed
 * 68-epx pitch (glyph centres 82 / 150 / 218 / 286 from the right edge), ••• centred 24 from it. ••• expands it
 * (R7 2.1.16, Y5 stand-in: `[motion] files_more`): the bar grows to 60 with a 12-epx label under each glyph, and the
 * overflow's lines rise above it on #2B2B2B at a 44-epx pitch. Expanded, it is modal — a tap off it closes it.
 *
 * Fills the page's box and draws at its bottom, over the list, so expanding never moves the page.
 */
@Composable
fun FilesAppBar(state: FilesState, chrome: FilesChrome) {
    var open by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.moreOpen) {
        if (state.moreOpen) MotionClock.animate("files_more", FilesMetrics.MORE_MS, CortanaEaseOut) { open = it } else open = 0f
    }
    if (!state.moreOpen) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { BarAndMenu(state, chrome, open = 0f, modal = false) }
        return
    }
    OverlayLayer(active = { state.moreOpen }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().modalOverlay(onTapOff = { dismissOverlay { state.moreOpen = false } }), contentAlignment = Alignment.BottomStart) {
            BarAndMenu(state, chrome, open, modal = true)
        }
    }
}

@Composable
private fun BarAndMenu(state: FilesState, chrome: FilesChrome, open: Float, modal: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (modal) {
            // The overflow: its full height is laid out and the part risen so far is shown, bottom edge on the bar.
            val full = chrome.more.size * FilesMetrics.MORE_ITEM
            Box(Modifier.fillMaxWidth().height((full * open).dp).clipToBounds()) {
                Column(
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(full.dp).background(FilesMetrics.MORE_FILL)
                        .testTag("files_more").overlayItem(onPressedChange = {}, onRun = {}),
                ) {
                    for (item in chrome.more) MoreLine(item) { dismissOverlay { state.moreOpen = false }; item.onClick() }
                }
            }
        }
        val height = FilesMetrics.BAR + (FilesMetrics.APPBAR_EXPANDED - FilesMetrics.BAR) * open
        Box(
            Modifier.fillMaxWidth().height(height.dp).clipToBounds().background(FilesMetrics.BAR_FILL).testTag("files_appbar")
                .then(if (modal) Modifier.overlayItem(onPressedChange = {}, onRun = {}) else Modifier),
        ) {
            Row(Modifier.align(Alignment.TopEnd)) {
                for (button in chrome.buttons) {
                    BarSlot(button.tag, button.glyph, button.label, button.enabled, FilesMetrics.APPBAR_PITCH, open, modal) {
                        if (modal) dismissOverlay { state.moreOpen = false }
                        button.onClick()
                    }
                }
                BarSlot("files_bar:more", FilesGlyph.MORE, "", true, FilesMetrics.APPBAR_MORE_W, 0f, modal) {
                    if (modal) dismissOverlay { state.moreOpen = false } else state.moreOpen = true
                }
            }
        }
    }
}

/**
 * One button: a 48-epx-tall box centred on its glyph (so the tag's bounds give the glyph centre: 24 below the bar's
 * top), and under it — only while the bar is expanded — its label, centred 46.5 below the bar's top (F 1.7.4).
 */
@Composable
private fun BarSlot(tag: String, glyph: String, label: String, enabled: Boolean, widthEpx: Float, open: Float, modal: Boolean, onRun: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val ink = if (enabled) Color.White else FilesMetrics.DISABLED
    val touch = when {
        !enabled -> Modifier.semantics { role = Role.Button; disabled() }
        modal -> Modifier.semantics { role = Role.Button }.overlayItem(onPressedChange = { pressed = it }, onRun = onRun)
        else -> Modifier.semantics { role = Role.Button }
    }
    Box(Modifier.width(widthEpx.dp)) {
        // The label first, so the glyph's box lies over it where the two meet and keeps its whole bounds in a dump.
        if (open > 0f && label.isNotEmpty()) {
            val style = ShellType.caption.copy(color = ink, textAlign = TextAlign.Center)
            BasicText(
                label,
                // The label's box is the font's natural line (1.2 × 12 = 14.4), centred on 46.5.
                Modifier.width(widthEpx.dp).offset(y = (FilesMetrics.APPBAR_LABEL_CY - 7.2f).dp).graphicsLayer { alpha = open }.testTag("${tag}_label"),
                style = style, maxLines = 1, softWrap = false,
            )
        }
        val glyphBox = Modifier.size(widthEpx.dp, FilesMetrics.BAR.dp).testTag(tag).then(touch)
        if (enabled && !modal) {
            PressRow(onRun, glyphBox) { FilesGlyphText(glyph, FilesMetrics.APPBAR_GLYPH, ink, Modifier.align(Alignment.Center)) }
        } else {
            Box(glyphBox.background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent), contentAlignment = Alignment.Center) {
                FilesGlyphText(glyph, FilesMetrics.APPBAR_GLYPH, ink)
            }
        }
    }
}

/** One line of the overflow (F 1.7.5–1.7.7): 44 epx, text inset 12; one that cannot apply is dimmed and does nothing. */
@Composable
private fun MoreLine(item: MoreItem, onRun: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().height(FilesMetrics.MORE_ITEM.dp)
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
            .testTag(item.tag)
            .then(
                if (item.enabled) Modifier.semantics { role = Role.Button }.overlayItem(onPressedChange = { pressed = it }, onRun = onRun)
                else Modifier.semantics { role = Role.Button; disabled() },
            ),
    ) {
        BasicText(
            item.label, Modifier.align(Alignment.CenterStart).padding(start = FilesMetrics.MORE_INSET.dp),
            style = ShellType.body.copy(color = if (item.enabled) Color.White else FilesMetrics.DISABLED), maxLines = 1,
        )
    }
}
