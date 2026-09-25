package app.tileshell.ui.fluent

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * The two lights on press (Q3 B, T13-1; P4, H4): on a pressed item of a transient surface — menu rows, pane items,
 * phase 11's satellites — (1) a 1-epx border ring, white at 30 % over whatever fill is under it, and (2) a radial
 * light, white, radius 40 epx, alpha 0.10 at the touch point falling linearly to 0 at the radius, clipped to the
 * item, following the finger. Both go when the press ends: at UP, at cancel, or when the pointer leaves the item
 * (T13-13). Both obey the on / off rule: acrylic off → neither. They draw over the item's held look as built and
 * under its text; nothing about what the item draws or does changes.
 */
object RevealLight {
    const val RADIUS_EPX = 40f
    const val RING_EPX = 1f
    const val RING_ALPHA = 0.30f
    const val CENTRE_ALPHA = 0.10f

    /** Draws both lights for a press at [point] (this item's own coordinates). */
    fun draw(scope: DrawScope, point: Offset, radiusPx: Float, ringPx: Float) = with(scope) {
        clipRect {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = CENTRE_ALPHA),
                    1f to Color.White.copy(alpha = 0f),
                    center = point,
                    radius = radiusPx,
                ),
                radius = radiusPx,
                center = point,
            )
            drawRect(
                color = Color.White.copy(alpha = RING_ALPHA),
                topLeft = Offset(ringPx / 2f, ringPx / 2f),
                size = Size(size.width - ringPx, size.height - ringPx),
                style = Stroke(width = ringPx),
            )
        }
    }
}

/**
 * The lights for an ordinary item: its press is OBSERVED (the Initial pass, nothing consumed), so the item's own
 * gestures and pressed fill run exactly as before. Put it after the item's background in the modifier chain so the
 * lights draw over the fill and under the content.
 */
@Composable
fun Modifier.revealLights(): Modifier {
    val state by Fluent.state.collectAsState()
    val density = LocalDensity.current
    val radiusPx = with(density) { RevealLight.RADIUS_EPX.dp.toPx() }
    val ringPx = with(density) { RevealLight.RING_EPX.dp.toPx() }
    var point by remember { mutableStateOf<Offset?>(null) }
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                point = down.position
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val p = change.position
                        if (p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height) break
                        point = p
                    }
                } finally {
                    point = null
                }
            }
        }
        .drawBehind {
            val p = point ?: return@drawBehind
            if (state.on) RevealLight.draw(this, p, radiusPx, ringPx)
        }
}
