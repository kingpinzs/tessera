package app.tileshell.files

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.MotionTrace
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.tokens.ShellType

/** One line of the tap-and-hold menu: `files_hold:<id>`. */
class HoldItem(val id: String, val label: String, val run: () -> Unit)

/** An open tap-and-hold menu: the press that opened it, its lines, and whether the row is a cell of the Icons view. */
class HoldMenu(val press: RowPress, val items: List<HoldItem>, val grid: Boolean)

/** The tap-and-hold menu's measured values (r11/files.md 1.9; pass 2 §5 row 4.5). */
object HoldMetrics {
    /** F 1.9.3: 240.5 wide, #2B2B2B, a 1-epx grey border. F 1.9.4: 43.9-epx pitch (44), text inset 13.7. */
    const val WIDTH = 240.5f
    const val ITEM = 44f
    const val INSET = 13.7f

    /** F 1.9.3: 236.8 tall with five lines — 8.4 above the first and under the last. */
    const val PAD = 8.4f
    val FILL = Color(0xFF2B2B2B)
    val BORDER = Color(0xFF696969)

    /** F 1.9.5: the top edge just under the held item's icon (9 in the capture); the box 1 epx inside the right edge. */
    const val UNDER_ICON = 9f
    const val EDGE = 1f

    /** P2 §5 row 4.5: the box is at 76 % of its height on its first frame. */
    const val FIRST_HEIGHT = 0.76f

    /**
     * P2 §5 row 4.5: 90 % at +100 ms, 95 % at +133, 98 % at +167, settled between +233 and +367. The growth runs over
     * 276 ms (the line then reads 283–300 after the first frame), through the measured points.
     */
    const val GROW_MS = 276
    val Grow = TableEasing(
        listOf(0f to 0f, 100f / 283f to (0.90f - 0.76f) / 0.24f, 133f / 283f to (0.95f - 0.76f) / 0.24f, 167f / 283f to (0.98f - 0.76f) / 0.24f, 1f to 1f),
    )
}

/**
 * The tap-and-hold flyout (build task 3; r11/files.md 1.9, Y5): a 240.5-epx box of 44-epx lines hung under the held
 * row's icon, growing down from 76 % of its height. Modal by the shell's overlay rule: a tap off it closes it.
 *
 * Its `[motion] files_hold` line is timed from the PRESS (`t0` = the finger's down): `first=` is when the menu's first
 * frame was drawn, in ms after the press (700 ± 33), and `settle=` when the box reached its height — so the growth
 * itself is `settle − first`.
 */
@Composable
fun FilesHoldMenuLayer(menu: HoldMenu?, isOpen: (HoldMenu) -> Boolean, onDismiss: () -> Unit) {
    if (menu == null) return
    var grow by remember(menu) { mutableFloatStateOf(0f) }
    LaunchedEffect(menu) {
        // This runs inside the frame that first draws the menu (at 76 %).
        val first = SystemClock.uptimeMillis()
        val trace = MotionTrace("files_hold", menu.press.downUptimeMs)
        trace.frame(first * 1_000_000L, 0f)
        while (true) {
            val now = withFrameNanos { it }
            val fraction = ((now / 1_000_000f - first) / HoldMetrics.GROW_MS).coerceIn(0f, 1f)
            grow = HoldMetrics.Grow.transform(fraction)
            trace.frame(now, grow)
            if (fraction >= 1f) break
        }
        Diagnostics.add("motion", "${trace.message()} first=${first - menu.press.downUptimeMs}")
    }
    val full = menu.items.size * HoldMetrics.ITEM + HoldMetrics.PAD * 2
    OverlayLayer(active = { isOpen(menu) }, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().modalOverlay(onTapOff = { dismissOverlay(onDismiss) })) {
            val w = maxWidth.value
            val h = maxHeight.value
            val left = menu.press.xEpx.coerceIn(0f, (w - HoldMetrics.WIDTH - HoldMetrics.EDGE).coerceAtLeast(0f))
            val iconBottom = if (menu.grid) FilesMetrics.GRID_TOP_PAD + FilesMetrics.GRID_THUMB else (FilesMetrics.ROW + FilesMetrics.ICON_H) / 2f
            val under = menu.press.rowTopEpx + iconBottom + HoldMetrics.UNDER_ICON
            // Under the icon when it fits above the app bar; otherwise it ends at the row's top.
            val top = if (under + full <= h - FilesMetrics.BAR) under else (menu.press.rowTopEpx - full).coerceAtLeast(0f)
            val scale = HoldMetrics.FIRST_HEIGHT + (1f - HoldMetrics.FIRST_HEIGHT) * grow
            Box(
                Modifier.offset(x = left.dp, y = top.dp).width(HoldMetrics.WIDTH.dp).height((full * scale).dp).clipToBounds()
                    .background(HoldMetrics.FILL).border(1.dp, HoldMetrics.BORDER)
                    .testTag("files_hold").overlayItem(onPressedChange = {}, onRun = {}),
            ) {
                Column(Modifier.padding(top = HoldMetrics.PAD.dp)) {
                    for (item in menu.items) {
                        var pressed by remember(item) { mutableStateOf(false) }
                        Box(
                            Modifier.fillMaxWidth().height(HoldMetrics.ITEM.dp)
                                .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
                                .testTag("files_hold:${item.id}").semantics { role = Role.Button }
                                .overlayItem(onPressedChange = { pressed = it }, onRun = { dismissOverlay(onDismiss); item.run() }),
                        ) {
                            BasicText(
                                item.label, Modifier.align(Alignment.CenterStart).padding(start = HoldMetrics.INSET.dp),
                                style = ShellType.body.copy(color = Color.White), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
