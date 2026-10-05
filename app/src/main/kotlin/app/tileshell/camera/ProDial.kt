package app.tileshell.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.ui.tokens.ShellType

private val RING_INK = Color(0xFFB9B9B9)      // r11/camera.md 1.4.4: ≈1-epx light grey (#B4B4B4–#BEBEBE)
private val LABEL_INK = Color(0xFF656565)     // 1.4.7
private val ICON_INK = Color(0xFFEEEEEE)      // 1.4.5

/** One ring as drawn: which control, its radius, where its icon and label sit, and the arc its values lie on. */
private class Ring(val control: ProControl, val radius: Float, val icon: Pt, val label: Pt, val arc: Pair<Float, Float>, val labels: List<String>, val index: Int)

/**
 * The Lumia pro dial (Approximation Y4; r11/camera.md 1.4, HIGH; build task 6a). Five concentric arcs centred on the
 * nav bar's top edge at W/2, radii 130.5 + 65·k, inner → outer exposure · shutter · ISO · focus · white balance, each
 * with its icon on the ring and its value label 30.3 epx above the ring's top — or, for one control picked in the
 * capsule, one arc of r 130.25 with its icon at 139°. A control the camera's gates do not admit has no ring (and has
 * its `unavailable` line). A value is set by touching or dragging along a ring: the angle picks it; a tap on a ring's
 * icon returns that control to automatic.
 */
@Composable
fun BoxScope.ProDial(engine: CameraEngine, state: ViewfinderState, g: CameraGeometry, caps: CameraCaps) {
    val admitted = CameraGates.proControls(caps).filter { it.second.available }.map { it.first }
    val single = state.single
    val rings: List<Ring> = if (single != null) {
        if (single !in admitted) return
        val labels = ProValues.labels(caps, single)
        listOf(Ring(single, CameraGeometry.SINGLE_RADIUS, g.singleIcon, g.singleLabel, g.valueArc(CameraGeometry.SINGLE_RADIUS, CameraGeometry.SINGLE_ICON_ANGLE), labels, ProValues.dialIndex(caps, single, engine.pro[single])))
    } else {
        admitted.map { control ->
            val k = control.ordinal
            val labels = ProValues.labels(caps, control)
            Ring(control, g.ringRadius(k), g.ringIcon(k), g.ringLabel(k), g.valueArc(g.ringRadius(k), if (k == 0) null else g.ringIconAngle(k)), labels, ProValues.dialIndex(caps, control, engine.pro[control]))
        }
    }
    val sweep = if (single != null) 1f else state.dialSweep
    val labelAlpha = if (single != null) 1f else state.dialLabels
    val centre = g.dialCentre

    fun pick(x: Float, y: Float, tap: Boolean) {
        val (r, angle) = g.polar(x, y)
        val ring = rings.minByOrNull { kotlin.math.abs(it.radius - r) }?.takeIf { kotlin.math.abs(it.radius - r) <= CameraGeometry.RING_GAP / 2f } ?: return
        if (ring.control == ProControl.EXPOSURE && !engine.evEnabled) return
        val onIcon = kotlin.math.abs(ring.icon.x - x) <= 16f && kotlin.math.abs(ring.icon.y - y) <= 16f
        if (tap && onIcon) { engine.setPro(ring.control, null); return }
        if (onIcon) return
        val index = g.valueIndex(ring.arc, angle, ring.labels.size)
        engine.setPro(ring.control, ProValues.engineIndex(ring.control, index))
    }

    Box(
        Modifier.fillMaxSize().testTag("camera_dial")
            .semantics { contentDescription = "dial " + rings.joinToString(" ") { "${it.control.id}=${it.radius}" } }
            .pointerInput(rings.map { it.control }, single) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val u = 1.dp.toPx()
                    val (r0, _) = g.polar(down.position.x / u, down.position.y / u)
                    // Only a touch on a ring is the dial's; anything else falls through to the viewfinder.
                    if (rings.none { kotlin.math.abs(it.radius - r0) <= CameraGeometry.RING_GAP / 2f } || down.position.y / u > g.navTop) return@awaitEachGesture
                    down.consume()
                    var moved = false
                    var last = down.position
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        last = change.position
                        if (!change.pressed) break
                        if (moved) pick(last.x / u, last.y / u, false)
                    }
                    pick(last.x / u, last.y / u, !moved)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val u = 1.dp.toPx()
            val c = Offset(centre.x * u, centre.y * u)
            // camera-pass2 §1 "Dial details": a dark scrim disc under the rings.
            val scrim = (rings.maxOf { it.radius } + 65f) * u
            drawCircle(Color(0x61000000), scrim, c)
            rings.forEach { ring ->
                val r = ring.radius * u
                // The upper half, swept in from the left as the dial opens (Y6: a rotational sweep about the centre).
                drawArc(RING_INK, 180f, 180f * sweep, false, Offset(c.x - r, c.y - r), Size(2f * r, 2f * r), style = Stroke(1f * u))
                if (sweep >= 1f) {
                    val at = g.onRing(ring.radius, g.valueAngle(ring.arc, ring.index, ring.labels.size))
                    drawCircle(Color.White, 3.5f * u, Offset(at.x * u, at.y * u))
                }
            }
        }
        rings.forEach { ring ->
            val manual = engine.pro.containsKey(ring.control)
            val dim = ring.control == ProControl.EXPOSURE && !engine.evEnabled
            At(ring.icon, 22f, Modifier.background(Color.Black, CircleShape).testTag("camera_dial_icon:${ring.control.id}")) {
                val ink = if (dim) LABEL_INK else ICON_INK
                val glyph = proGlyph(ring.control)
                if (glyph != null) BasicText(glyph, style = TextStyle(fontFamily = Brand.iconFont, fontSize = 17.sp, color = ink))
                else BasicText(proBadge(ring.control).orEmpty(), style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = ink))
            }
            Centred(ring.label, Modifier.graphicsLayer { alpha = labelAlpha }) {
                BasicText(
                    ring.labels.getOrNull(ring.index).orEmpty(),
                    Modifier.testTag("camera_dial_value:${ring.control.id}"),
                    style = ShellType.body.copy(color = if (manual) Color.White else LABEL_INK),
                    maxLines = 1,
                )
            }
        }
    }
}
