package app.tileshell.start

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tileshell.tiles.TileSize

/** Which edit-mode disc a point hit (R6 §1.2.9: unpin unpins, resize moves to the next size). */
enum class Disc { UNPIN, RESIZE }

/**
 * The two edit-mode discs (R6 §1.2, HIGH position / MEDIUM colours and glyphs): 31 ± 1.5 epx across, the unpin
 * disc centred on the held tile's top-right corner and the resize disc on its bottom-right corner. The disc
 * takes the theme's foreground and the glyph its background — a white disc with a dark glyph in the dark
 * theme, a black disc with a white glyph in the light theme (§1.2.4).
 *
 * The glyph set has no push-pin and no diagonal arrow, so both glyphs are drawn: the unpin pin is 16 × 15 epx
 * with the small slashed circle §1.2.5 describes at its lower right, and the resize arrow is one 12 × 12 epx
 * arrow turned to point along the resize cycle medium → small → wide (§1.2.6: ↖ on medium, ↘ on small, ← on wide).
 */
@Composable
fun EditDisc(kind: Disc, size: TileSize, discDp: Dp, discColor: Color, glyphColor: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(discDp).testTag("edit_disc:${kind.name.lowercase()}")) {
        Canvas(Modifier.size(discDp)) {
            val d = this.size.minDimension
            drawCircle(discColor, radius = d / 2f, center = Offset(d / 2f, d / 2f))
            val epx = d / Edit.DISC_EPX // px per epx at this disc size
            when (kind) {
                Disc.UNPIN -> drawUnpin(d, epx, glyphColor)
                Disc.RESIZE -> {
                    // The arrow points at the next size in the cycle: medium → small (↖), small → wide (↘), wide → medium (←).
                    // rotationZ turns clockwise and the drawn arrow points left, so ↖ is +45 and ↘ is -135.
                    val degrees = when (size) {
                        TileSize.MEDIUM -> 45f
                        TileSize.SMALL -> -135f
                        TileSize.WIDE -> 0f
                    }
                    rotate(degrees, pivot = Offset(d / 2f, d / 2f)) { drawArrow(d, epx, glyphColor) }
                }
            }
        }
    }
}

/** A push-pin 16 × 15 epx with a small slashed circle at its lower right (R6 §1.2.5). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUnpin(d: Float, epx: Float, color: Color) {
    val w = 16f * epx
    val h = 15f * epx
    translate(left = (d - w) / 2f, top = (d - h) / 2f) {
        val stroke = Stroke(width = 1.6f * epx, cap = StrokeCap.Round)
        // Pin head: the classic tilted plate, drawn as a quadrilateral, with the needle to the lower left.
        val head = Path().apply {
            moveTo(w * 0.34f, h * 0.06f)
            lineTo(w * 0.78f, h * 0.30f)
            lineTo(w * 0.60f, h * 0.44f)
            lineTo(w * 0.46f, h * 0.66f)
            lineTo(w * 0.16f, h * 0.30f)
            close()
        }
        drawPath(head, color)
        drawLine(color, Offset(w * 0.30f, h * 0.52f), Offset(w * 0.04f, h * 0.96f), strokeWidth = 1.6f * epx, cap = StrokeCap.Round)
        // The slashed circle that makes it "unpin".
        val r = 3.2f * epx
        val cx = w - r - 0.3f * epx
        val cy = h - r - 0.3f * epx
        drawCircle(color, radius = r, center = Offset(cx, cy), style = stroke)
        drawLine(color, Offset(cx - r * 0.7f, cy + r * 0.7f), Offset(cx + r * 0.7f, cy - r * 0.7f), strokeWidth = 1.6f * epx, cap = StrokeCap.Round)
    }
}

/** One arrow 12 × 12 epx pointing left; the caller turns it to the cycle's direction (R6 §1.2.5-§1.2.6). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(d: Float, epx: Float, color: Color) {
    val s = 12f * epx
    translate(left = (d - s) / 2f, top = (d - s) / 2f) {
        val mid = s / 2f
        val stroke = 1.8f * epx
        drawLine(color, Offset(s * 0.95f, mid), Offset(s * 0.12f, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.12f, mid), Offset(s * 0.45f, mid - s * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.12f, mid), Offset(s * 0.45f, mid + s * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Disc size in dp for this panel (1.dp == 1 epx under the shell's density). */
val discSizeDp: Dp get() = Edit.DISC_EPX.dp

@Suppress("unused")
private val unusedSize = Size.Zero
