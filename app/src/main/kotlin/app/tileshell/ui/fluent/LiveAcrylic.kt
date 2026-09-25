package app.tileshell.ui.fluent

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The live source's backdrop (Decisions (b), T13-11): the layer L one child records its page into — the page's
 * background AND content — and draws to show itself. L carries NO effect: the page is never blurred in place.
 * A surface drawn as a sibling ABOVE that child reads L through [LocalAcrylicBackdrop].
 */
@Stable
class AcrylicBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where L's origin is in the window, so a surface can place L under itself. */
    internal var originInWindow by mutableStateOf(Offset.Zero)
}

val LocalAcrylicBackdrop = compositionLocalOf<AcrylicBackdrop?> { null }

@Composable
fun rememberAcrylicBackdrop(): AcrylicBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { AcrylicBackdrop(layer) }
}

/**
 * Records this node's drawing (its background and everything inside it) into [backdrop]'s layer L and draws L, so
 * the page looks exactly as it did. There is no copy: what a surface blurs is always what the page shows.
 */
fun Modifier.acrylicBackdropSource(backdrop: AcrylicBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.originInWindow = it.positionInWindow() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * One acrylic surface's material, drawn under the surface's own content and carrying the `acrylic:<id>` tag, so a
 * row reads the surface's bounds from the dump.
 *
 * Acrylic on: layer S — this node's bounds grown by 3σ on every side, clamped to the window — records L translated
 * under it, blurs it (HWUI's Gaussian, radius 30 epx) and tints + noises it ([FluentEffects]); S is drawn clipped
 * to this node, so every pixel is blurred from the real backdrop around it and only the window edge is clamped.
 * Acrylic off, or no backdrop in scope: [fill] at opacity 1 and nothing else (Decisions "One fallback rule").
 *
 * @param fill the colour the surface draws today (its measured W10M fill) — the fallback.
 * @param tint T from the surface table ([FluentMaterial.deriveTint] of the fill over its measured backdrop).
 */
@Composable
fun AcrylicLayer(surface: FluentSurface, fill: Color, tint: Rgb, modifier: Modifier = Modifier) {
    val backdrop = LocalAcrylicBackdrop.current
    val state by Fluent.state.collectAsState()
    val acrylic = state.on && backdrop != null
    val radiusPx = with(LocalDensity.current) { FluentMaterial.RADIUS_EPX.dp.toPx() }
    val root = LocalView.current.rootView
    val s = rememberGraphicsLayer()
    val effect = remember(radiusPx, tint) { FluentEffects.material(radiusPx, tint).asComposeRenderEffect() }
    val holder = remember { FormHolder() }
    var origin by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(surface) { Fluent.logShown(surface, tint) }

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .testTag("acrylic:${surface.id}")
            .drawBehind {
                // The form line is written from the frame that draws the new form (T13-9).
                if (holder.drawn != null && holder.drawn != acrylic) Fluent.logForm(surface, acrylic)
                holder.drawn = acrylic
                val o = origin
                if (!acrylic || o == null || backdrop == null) {
                    drawRect(fill)
                    return@drawBehind
                }
                val m = FluentMaterial.marginPx(radiusPx)
                val left = floor(max(0f, o.x - m))
                val top = floor(max(0f, o.y - m))
                val right = ceil(min(root.width.toFloat(), o.x + size.width + m))
                val bottom = ceil(min(root.height.toFloat(), o.y + size.height + m))
                val w = (right - left).roundToInt()
                val h = (bottom - top).roundToInt()
                if (w <= 0 || h <= 0) {
                    drawRect(fill)
                    return@drawBehind
                }
                s.renderEffect = effect
                s.record(size = IntSize(w, h)) {
                    val b = backdrop.originInWindow
                    translate(b.x - left, b.y - top) { drawLayer(backdrop.layer) }
                }
                s.topLeft = IntOffset((left - o.x).roundToInt(), (top - o.y).roundToInt())
                clipRect { drawLayer(s) }
            },
    )
}

private class FormHolder {
    var drawn: Boolean? = null
}
