package app.tileshell.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/**
 * The location bar (r11/files.md 1.1.2, 1.2): 48 epx, #1F1F1F, directly under the status bar — ≡ centred at x 24, the
 * breadcrumb from x 60, ↑ centred 24 from the right edge and dimmed where there is no parent.
 */
@Composable
fun FilesLocationBar(state: FilesState, modifier: Modifier = Modifier) {
    val edge = (FilesMetrics.BAR_GLYPH_CX * 2).dp
    Box(modifier.fillMaxWidth().height(FilesMetrics.BAR.dp).background(FilesMetrics.BAR_FILL).testTag("files_location")) {
        PressRow({ state.openPane() }, Modifier.size(edge, FilesMetrics.BAR.dp).testTag("files_menu").semantics { role = Role.Button }) {
            FilesGlyphText(FilesGlyph.MENU, FilesMetrics.BAR_GLYPH, Color.White, Modifier.align(Alignment.Center))
        }
        Breadcrumb(state, Modifier.fillMaxSize().padding(start = FilesMetrics.CRUMB_LEFT.dp, end = edge))
        val up = state.canGoUp
        val upBox = Modifier.align(Alignment.CenterEnd).size(edge, FilesMetrics.BAR.dp).testTag("files_up")
        if (up) {
            PressRow({ state.up() }, upBox.semantics { role = Role.Button }) {
                FilesGlyphText(FilesGlyph.UP, FilesMetrics.BAR_GLYPH, Color.White, Modifier.align(Alignment.Center))
            }
        } else {
            // At a volume's root, and on Recent and the bin: drawn dimmed (F 1.2.9), and a tap changes nothing.
            Box(upBox.semantics { role = Role.Button; disabled() }, contentAlignment = Alignment.Center) {
                FilesGlyphText(FilesGlyph.UP, FilesMetrics.BAR_GLYPH, FilesMetrics.DIM_GLYPH)
            }
        }
    }
}

/**
 * The breadcrumb (F 1.2.3–1.2.7): mixed case, 15-epx SemiBold, white, "›" between segments, every segment tappable.
 * A path too long for the bar keeps its first and last segments around "…" ([FilesNav.shownCrumbs]); when even that
 * does not fit, the last segment keeps its room and the first is cut with an ellipsis ("This D… › … › lumia 950").
 * A segment's tag is `files_crumb:<n>`, n its place in the whole path, collapsed or not.
 */
@Composable
private fun Breadcrumb(state: FilesState, modifier: Modifier) {
    val crumbs = state.crumbs
    val style = ShellType.base.copy(color = Color.White)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val available = maxWidth.value
        val shown = remember(crumbs, available) {
            fun width(text: String) = measurer.measure(text, style, maxLines = 1).size.width / density
            val separator = width(SEPARATOR) + FilesMetrics.CRUMB_GAP * 2
            FilesNav.shownCrumbs(crumbs.map { width(it.label) }, separator, width(ELLIPSIS), available)
        }
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            shown.forEachIndexed { at, index ->
                if (at > 0) {
                    Separator(style)
                    if (index != shown[at - 1] + 1) {
                        BasicText(ELLIPSIS, Modifier.testTag("files_crumb_more"), style = style, maxLines = 1)
                        Separator(style)
                    }
                }
                val crumb = crumbs[index]
                val last = index == crumbs.lastIndex
                // The first segment gives way (weight) when the bar is too narrow; every other one takes its own width.
                val box = if (at == 0 && shown.size > 1) Modifier.weight(1f, fill = false) else Modifier
                BasicText(
                    crumb.label,
                    box.testTag("files_crumb:$index").then(
                        // The last segment is where one already is: a tap on it goes nowhere.
                        if (last) Modifier else Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { state.go(crumb.location) },
                    ),
                    style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Separator(style: androidx.compose.ui.text.TextStyle) {
    BasicText(
        SEPARATOR, Modifier.padding(horizontal = FilesMetrics.CRUMB_GAP.dp),
        style = style.copy(color = FilesMetrics.CRUMB_SEPARATOR, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), maxLines = 1,
    )
}

private const val SEPARATOR = "›"
private const val ELLIPSIS = "…"
