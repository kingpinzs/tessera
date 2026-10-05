package app.tileshell.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.clock.ClockMetrics
import app.tileshell.clock.DIM_INK
import app.tileshell.clock.PressBox
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType

/**
 * Photos' measured values (r11/photos.md and r11/photos-pass2.md; the phase doc's Y1, Y2, Y11, Y13). 1 dp = 1 epx on
 * the shell's 360-epx canvas. Each value names the row it is read from.
 */
object PhotosMetrics {
    // Y1 — the V-2016+ collection (G1): mixed-case titles on the black page, no band, no underline.
    val HEADER = 60.dp
    const val TITLE_SIZE = 28f                       // 1.2.6: cap 19.7 epx
    const val TITLE_INK_TOP = 19.7f                  // 1.2.6: the cap band's top
    val TITLE_INK_LEFT = listOf(13.14f, 156.29f)     // 1.2.7: Collection, Albums
    val TITLE_UNSELECTED = Color(0xFF999999)         // 1.2.8
    const val MONTH_CAP_TOP = 13.1f                  // 1.3.4: cap top 73.1 below the screen top = 13.1 below the header
    const val MONTH_GAP_ABOVE = 40.75f               // 1.3.7: last thumbnail bottom → the next month's cap top
    const val MONTH_TO_DAY = 21.1f                   // 1.3.8: month cap bottom → day digits top
    const val DAY_TO_TILES = 13.4f                   // 1.3.8: day digits bottom → thumbnails
    const val SMALL_CAP = 10.86f                     // 1.3.4 / 1.3.5: the 15-epx class
    val SECONDARY = Color(0xFF999999)                // 1.3.5
    val TILE = 111.dp                                // 1.3.10 (V-2015 at 360 epx, HIGH)
    val GUTTER = 2.dp
    val GRID_LEFT = 11.dp
    val VIDEO_DISC = 36.dp                           // 1.3.14
    val ALBUM_H = 60.dp                              // 1.4.1
    val ALBUM_W = 162.dp
    val ALBUM_MARGIN = 12.dp
    const val ALBUM_LABEL_LEFT = 12.75f              // 1.4.2: inside the tile
    val PLACEHOLDER = Color(0xFF141414)              // 1.4.3: a tile with no image

    // Y2 — the viewer.
    val VIEWER_HEADER = 50.dp                        // 1.6.2
    val VIEWER_HEADER_FILL = Color(0xFF171717)
    const val VIEWER_DATE_LEFT = 25f                 // 1.6.3
    const val VIEWER_DATE_CAP_TOP = 18.75f
    val BAR = 48.dp                                  // 1.1.5 / 1.6.5
    val BAR_EXPANDED = 60.dp                         // 1.6.7
    const val BAR_GLYPH_CY = 24f                     // 1.6.6: the glyph row's centre below the bar's top
    const val BAR_LABEL_CAP_CY = 46.1f               // 1.6.7
    val BAR_PITCH = 68.dp                            // 286 / 218 / 150 / 82 from the right
    val BAR_MORE = 48.dp                             // More's centre 24 from the right
    val LIBRARY_BAR_FILL = Color(0xFF1F1F1F)         // 1.1.6
    val VIEWER_BAR_FILL = Color.Black                // pass 2 §4.4
    val MENU_FILL = Color(0xFF2B2B2B)                // 1.6.9
    val MENU_RULE = Color(0xFF818181)
    val MENU_ROW = 44.dp                             // 1.6.9: pitch 44.1
    const val MENU_TOP = 6.6f                        // the first item's centre 28.6 below the panel top
    const val MENU_RULE_BLOCK = 9.0f                 // the rule 4.5 below the row above, the next row 4.5 below it
    const val MENU_BOTTOM = 7.5f                     // 155.1 tall for three items and the rule
    const val MENU_TEXT_LEFT = 12.86f
    const val MENU_MS = 317                          // §4: the "•••" expand, R7 2.1.16

    // File information (pass 2, UNMEASURED-7 closed LOW): the lower half of the screen.
    val INFO_FILL = Color(22, 22, 22)
    const val INFO_TITLE_CAP_TOP = 15.6f
    const val INFO_TITLE_LEFT = 13.7f
    const val INFO_SIDE = 15.5f
    val INFO_PITCH = 68.6.dp

    // The Edit sheet (1.7.2; pass 2 §4.9).
    val SHEET_FILL = Color(0xFF1F1F1F)
    val SHEET_ICON = 32.dp
    const val SHEET_ICON_TOP = 15f
    const val SHEET_ICON_LEFT = 12f
    const val SHEET_LABEL_LEFT = 57.25f

    // Y11 — the editor's strip (pass 2 "The editor on a phone").
    val EDIT_BAR_FILL = Color(15, 15, 15)

    // Y13 — the trim screen (pass 2 "Video trim").
    val TRIM_BAR_FILL = Color(0xFF1F1F1F)
    val TRIM_SCRIM = 100.dp
    const val TRIM_TRACK_CY_FROM_BOTTOM = 61.6f      // track centre 298.4 on a 360-epx-tall landscape canvas
    val TRIM_TRACK = 3.dp
    val TRIM_INSET = 67.dp
    val TRIM_HANDLE = 18.4.dp
    val TRIM_RING = 37.6.dp
    const val TRIM_RING_BELOW = 24f

    // Y6 — motion.
    const val VIEWER_MS = 250                        // open / close: an approximation (H4)
    const val SWIPE_SETTLE_MS = 167                  // the whole fling ≤ 234 ms (pass 2 §5)
    val SWIPE_GAP = 20.dp
    const val SLIDESHOW_STEP_MS = 5_000L
    const val SLIDESHOW_SETTLE_MS = 250
}

/** One button of a Photos bar. */
data class PhotoBarButton(val glyph: String, val label: String, val tag: String, val enabled: Boolean = true, val on: Boolean = false, val onClick: () -> Unit)

/** One item of a "•••" menu; [ruleAbove] draws W10M's separator before it (1.6.9). */
data class PhotoMenuEntry(val label: String, val tag: String, val ruleAbove: Boolean = false, val onPick: () -> Unit)

/** A menu panel's height for [menu] (1.6.9: 155.1 epx for three items and the rule). */
fun photoMenuHeight(menu: List<PhotoMenuEntry>): Dp =
    (PhotosMetrics.MENU_TOP + PhotosMetrics.MENU_BOTTOM + menu.count { it.ruleAbove } * PhotosMetrics.MENU_RULE_BLOCK).dp + PhotosMetrics.MENU_ROW * menu.size

/** A menu panel's rows (1.6.9): 15-epx white items at a 44-epx pitch, the text 12.86 epx in. */
@Composable
fun PhotoMenuRows(menu: List<PhotoMenuEntry>, onPicked: () -> Unit) {
    Box(Modifier.height(PhotosMetrics.MENU_TOP.dp))
    menu.forEach { entry ->
        if (entry.ruleAbove) {
            Box(Modifier.fillMaxWidth().height(PhotosMetrics.MENU_RULE_BLOCK.dp)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 12.dp).height(1.14.dp).background(PhotosMetrics.MENU_RULE))
            }
        }
        PressBox(Modifier.fillMaxWidth().height(PhotosMetrics.MENU_ROW).testTag(entry.tag), onClick = { onPicked(); entry.onPick() }) {
            BasicText(entry.label, Modifier.align(Alignment.CenterStart).offset(x = PhotosMetrics.MENU_TEXT_LEFT.dp), style = ShellType.body.copy(color = Color.White))
        }
    }
}

/**
 * A Photos bar (Y2, Y11, Y13): [buttons] on W10M's 68-epx pitch beside "•••", 48 epx tall; "•••" grows it to 60 epx
 * with a label under each glyph and raises [menu] over the page in the #2B2B2B panel. On the page's bottom edge, or —
 * the trim screen's — on its top edge with the menu dropping under it ([top]). It is Photos' own and not phase 15's
 * `ClockAppBar` because its measured values differ: 60 not 68 epx expanded, the panel's fill, insets and rule.
 * Tags: `<prefix>_bar`, `<prefix>_more`, `<prefix>_menu`, `<prefix>_bar_scrim`.
 */
@Composable
fun BoxScope.PhotoBar(
    buttons: List<PhotoBarButton>,
    menu: List<PhotoMenuEntry>,
    fill: Color,
    tagPrefix: String,
    isExpanded: () -> Boolean,
    onExpand: (Boolean) -> Unit,
    top: Boolean = false,
    accent: Color = Color.White,
) {
    val expanded = isExpanded()
    val height = if (expanded) PhotosMetrics.BAR_EXPANDED else PhotosMetrics.BAR
    val edge = if (top) Alignment.TopStart else Alignment.BottomStart
    // The scrim and the menu live in an OverlayLayer, which stops placing them the moment Back closes the bar (L13-11).
    OverlayLayer(active = isExpanded) {
        Box(Modifier.fillMaxSize()) {
            if (expanded) {
                Box(Modifier.fillMaxSize().testTag("${tagPrefix}_bar_scrim").pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onExpand(false) }
                })
                if (menu.isNotEmpty()) {
                    var grow by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(Unit) { MotionClock.animate("photos_menu", PhotosMetrics.MENU_MS, ClockMetrics.easeOut) { grow = it } }
                    val full = photoMenuHeight(menu)
                    Column(
                        Modifier.align(edge).offset(y = if (top) height else -height).fillMaxWidth().height(full * grow)
                            .clipToBounds().background(PhotosMetrics.MENU_FILL).testTag("${tagPrefix}_menu")
                            // Touches on the panel's blank parts stay on it: the scrim under it must not read them as a tap off.
                            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); waitForUpOrCancellation()?.consume() } },
                    ) { PhotoMenuRows(menu) { onExpand(false) } }
                }
            }
        }
    }
    Box(Modifier.align(edge).fillMaxWidth().height(height).background(fill).testTag("${tagPrefix}_bar")) {
        // The bar takes the touches on its blank parts, so they never reach the page under it (L13-14) — except while
        // the scrim lies under it, when a tap there closes the menu.
        OverlayLayer(active = { !expanded || !isExpanded() }, Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } })
        }
        androidx.compose.foundation.layout.Row(Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
            buttons.forEach { b ->
                PhotoBarButtonView(b.glyph, if (expanded) b.label else null, b.tag, b.enabled, PhotosMetrics.BAR_PITCH, if (b.on) accent else Color.White) { onExpand(false); b.onClick() }
            }
            PhotoBarButtonView(Glyph.MORE_HORIZONTAL, null, "${tagPrefix}_more", true, PhotosMetrics.BAR_MORE, Color.White) { onExpand(!expanded) }
        }
    }
}

@Composable
private fun PhotoBarButtonView(glyph: String, label: String?, tag: String, enabled: Boolean, width: Dp, ink: Color, onClick: () -> Unit) {
    val color = if (enabled) ink else ink.copy(alpha = DIM_INK)
    Box(
        Modifier.width(width).fillMaxHeight().testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
    ) {
        // 1.6.6: a 20 × 20 glyph box, its centre 24 epx below the bar's top whether or not the bar is expanded.
        Box(Modifier.align(Alignment.TopCenter).offset(y = (PhotosMetrics.BAR_GLYPH_CY - 10f).dp).size(20.dp), contentAlignment = Alignment.Center) {
            BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 20.sp, color = color, textAlign = TextAlign.Center))
        }
        if (label != null) {
            // 1.6.7: 12-epx labels, cap centre 46.1 epx below the bar's top.
            val capTop = PhotosMetrics.BAR_LABEL_CAP_CY - CapMetrics.capHeight(12f) / 2f
            BasicText(
                label,
                Modifier.align(Alignment.TopCenter).offset(y = CapMetrics.topPaddingForCapTop(capTop, 12f).dp).testTag("${tag}_label"),
                style = ShellType.caption.copy(color = color, textAlign = TextAlign.Center), maxLines = 1, softWrap = false,
            )
        }
    }
}

/** A glyph from the icon font, [size] epx, centred in its box. */
@Composable
fun PhotoGlyph(glyph: String, size: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = size.sp, color = color, textAlign = TextAlign.Center))
    }
}
