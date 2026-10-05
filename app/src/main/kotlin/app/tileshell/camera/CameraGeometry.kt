package app.tileshell.camera

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point or a box on the 360-epx canvas, y down from the screen's top (the nav bar is the last 48 epx). */
data class Pt(val x: Float, val y: Float)
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
}

/**
 * The viewfinder's measured geometry (Approximations Y3, Y4, Y12; r11/camera.md and camera-pass2.md, whose §4
 * governs). Pure: [w] and [h] are the screen in epx with the drawn nav bar INCLUDED in [h] ([navEpx] tall at its
 * bottom), so the JVM tests prove every number E19 reads from a dump.
 */
class CameraGeometry(val w: Float, val h: Float, val navEpx: Float) {
    /** The nav bar's top edge. */
    val navTop: Float get() = h - navEpx

    // ---- Y3: the preview -------------------------------------------------------------------------------------------

    /**
     * The preview fitted to the width and centred on the FULL screen (camera-pass2 §1 "4:3 preview, V-2016+"):
     * [longSide] : [shortSide] is the sensor's aspect (4:3 photo, 16:9 video), drawn upright so the long side is
     * vertical.
     */
    fun preview(longSide: Int, shortSide: Int): Box {
        val height = w * longSide / shortSide
        val top = (h - height) / 2f
        return Box(0f, top, w, top + height)
    }

    /** The framing grid: two lines each way at thirds of the preview's bounds (x positions, then y positions). */
    fun gridLines(p: Box): Pair<List<Float>, List<Float>> =
        listOf(p.left + p.width / 3f, p.left + 2f * p.width / 3f) to listOf(p.top + p.height / 3f, p.top + 2f * p.height / 3f)

    // ---- Y3: the bottom row ----------------------------------------------------------------------------------------

    /** Shutter: a 72-epx disc at W/2, centre 56 epx above the nav-bar top (74.75 in the five-ring view, Y4). */
    fun shutterCentre(fiveRings: Boolean = false): Pt = Pt(w / 2f, navTop - if (fiveRings) SHUTTER_RISE_FIVE else SHUTTER_RISE)

    /** The other modes: 32-epx discs at W/2 ± 60, centres 36 above the nav-bar top. */
    fun discCentre(right: Boolean): Pt = Pt(w / 2f + if (right) DISC_OFFSET else -DISC_OFFSET, navTop - DISC_RISE)

    /** Settings: a 36-epx disc bottom-right, 24 from the right and 27.9 above the nav-bar top. */
    val settingsCentre: Pt get() = Pt(w - 24f, navTop - 27.9f)

    /** Camera roll: a 36-epx square bottom-left, centre 24.7 from the left and 28.0 above the nav-bar top. */
    val rollCentre: Pt get() = Pt(24.7f, navTop - 28f)

    /** Camera switch: top-right, 24 from the right and 28 from the top. */
    val switchCentre: Pt get() = Pt(w - 24f, 28f)

    // ---- Y3: the capsule -------------------------------------------------------------------------------------------

    /**
     * Glyph centres of the vertical capsule on the right edge, 24 from the right, as offsets from H/2: four items at
     * − 66 / − 22 / + 22 / + 66, three at − 43.7 / + 0.5 / + 45.1 (both measured); another count takes the 44-epx
     * pitch about the centre (the camera lacks an item — an inference from the two measured forms).
     */
    fun capsuleOffsets(items: Int): List<Float> = when (items) {
        4 -> listOf(-66f, -22f, 22f, 66f)
        3 -> listOf(-43.7f, 0.5f, 45.1f)
        else -> (0 until items).map { (it - (items - 1) / 2f) * 44f }
    }

    fun capsuleGlyphs(items: Int): List<Pt> = capsuleOffsets(items).map { Pt(w - 24f, h / 2f + it) }

    /** The capsule's box: 2 → 45.5 epx from the right edge, centred on the screen centre, 37.2 epx past its end glyphs. */
    fun capsule(items: Int): Box {
        val o = capsuleOffsets(items)
        if (o.isEmpty()) return Box(w - 45.5f, h / 2f, w - 2f, h / 2f)
        return Box(w - 45.5f, h / 2f + o.first() - CAPSULE_END, w - 2f, h / 2f + o.last() + CAPSULE_END)
    }

    /**
     * The zoom slider (camera-pass2 UNMEASURED-3, LOW): on the edge opposite the capsule, 24 from it, centred on the
     * screen centre; "+" above and "−" below, their centres 0.565 · W apart.
     */
    val zoomPlus: Pt get() = Pt(24f, h / 2f - 0.565f * w / 2f)
    val zoomMinus: Pt get() = Pt(24f, h / 2f + 0.565f * w / 2f)

    /** The toast's baseline: 105 epx above the nav-bar top, centred at W/2 (camera-pass2 UNMEASURED-10, LOW). */
    val toastBaseline: Float get() = navTop - 105f

    // ---- Y4: the pro dial ------------------------------------------------------------------------------------------

    /** Every arc is centred on the nav bar's top edge at W/2. */
    val dialCentre: Pt get() = Pt(w / 2f, navTop)

    /** Ring [k] (0 = exposure … 4 = white balance): 130.5 + 65·k, as measured per ring. */
    fun ringRadius(k: Int): Float = RING_RADII[k]

    /** The point of a circle of radius [r] about the dial centre at [degrees] (0° = right, 90° = straight up). */
    fun onRing(r: Float, degrees: Float): Pt {
        val a = Math.toRadians(degrees.toDouble())
        return Pt(dialCentre.x + r * cos(a).toFloat(), dialCentre.y - r * sin(a).toFloat())
    }

    /**
     * Ring [k]'s icon: the exposure icon on the innermost ring's top at W/2; the others on their own ring at the
     * left, x = 56 epx (the 360-epx captures' value).
     */
    fun ringIcon(k: Int): Pt = if (k == 0) Pt(w / 2f, navTop - ringRadius(0)) else onRing(ringRadius(k), ringIconAngle(k))

    fun ringIconAngle(k: Int): Float =
        if (k == 0) 90f else Math.toDegrees(acos(((ICON_X - w / 2f) / ringRadius(k)).toDouble().coerceIn(-1.0, 1.0))).toFloat()

    /** Ring [k]'s value label: centred at W/2, 30.3 epx above the ring's top. */
    fun ringLabel(k: Int): Pt = Pt(w / 2f, navTop - ringRadius(k) - LABEL_RISE)

    /** One control alone: one arc of r 130.25 at the same centre, its icon at 139°, its label 29.25 above the top. */
    val singleIcon: Pt get() = onRing(SINGLE_RADIUS, SINGLE_ICON_ANGLE)
    val singleLabel: Pt get() = Pt(w / 2f, navTop - SINGLE_RADIUS - 29.25f)

    /**
     * The part of a ring of radius [r] a value can sit on, as angles (low = right end, high = left end): what the
     * screen's sides leave of the upper half, less [margin]° at each end, and — for a ring whose icon is on its left —
     * ending [ICON_CLEAR]° short of the icon at [iconAngle].
     */
    fun valueArc(r: Float, iconAngle: Float?, margin: Float = 12f): Pair<Float, Float> {
        val edge = if (r > w / 2f) Math.toDegrees(acos((w / 2f / r).toDouble())).toFloat() else 0f
        val low = edge + margin
        val high = minOf(180f - edge - margin, (iconAngle ?: 180f) - ICON_CLEAR)
        return low to maxOf(high, low + 1f)
    }

    /** The angle of value [index] of [count] on an arc: the first value at the left end, the last at the right. */
    fun valueAngle(arc: Pair<Float, Float>, index: Int, count: Int): Float =
        if (count <= 1) (arc.first + arc.second) / 2f else arc.second - (arc.second - arc.first) * index / (count - 1)

    /** The value index nearest [degrees] on an arc (the inverse of [valueAngle]). */
    fun valueIndex(arc: Pair<Float, Float>, degrees: Float, count: Int): Int {
        if (count <= 1) return 0
        val f = ((arc.second - degrees) / (arc.second - arc.first)).coerceIn(0f, 1f)
        return Math.round(f * (count - 1))
    }

    /** Polar coordinates of ([x], [y]) about the dial centre: radius and angle (0° = right, 90° = up). */
    fun polar(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - dialCentre.x
        val dy = dialCentre.y - y
        val r = sqrt(dx * dx + dy * dy)
        return r to Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /** The ring a touch at radius [r] belongs to among [shown] ring indices: the nearest within half a ring gap. */
    fun ringAt(r: Float, shown: List<Int>): Int? =
        shown.minByOrNull { kotlin.math.abs(ringRadius(it) - r) }?.takeIf { kotlin.math.abs(ringRadius(it) - r) <= RING_GAP / 2f }

    // ---- Y12: panorama ---------------------------------------------------------------------------------------------

    /** The guide band: full width, 0.35 · W tall, centred on the screen centre. */
    val panoBand: Box get() = Box(0f, h / 2f - 0.175f * w, w, h / 2f + 0.175f * w)

    /**
     * The captured strip's frame at the band's left: 9:16 of the band's height wide at the start, growing with
     * [progress] (0 … 1) to the band's full width (UNMEASURED-5: progress as the frame's growing width).
     */
    fun panoFrame(progress: Float): Box {
        val b = panoBand
        val start = b.height * 9f / 16f
        return Box(0f, b.top, start + (w - start) * progress.coerceIn(0f, 1f), b.bottom)
    }

    /** The arrow: 23.6 × 21.4 epx, its centre 22.5 past the frame, on the band's centre line. */
    fun panoArrow(progress: Float): Pt = Pt(panoFrame(progress).right + 22.5f, h / 2f)

    companion object {
        const val SHUTTER_DIAMETER = 72f
        const val SHUTTER_FILL_R = 31.9f
        const val SHUTTER_RING_INNER = 32f
        const val SHUTTER_RING_OUTER = 34f
        const val SHUTTER_OUTER_R = 36.1f
        const val SHUTTER_RISE = 56f
        const val SHUTTER_RISE_FIVE = 74.75f
        const val DISC_DIAMETER = 32f
        const val DISC_OFFSET = 60f
        const val DISC_RISE = 36f
        const val CORNER_DISC = 36f
        const val GEAR = 14f
        const val CAPSULE_END = 37.2f
        val RING_RADII = floatArrayOf(130.5f, 195.4f, 260.3f, 325.3f, 390.2f)
        const val RING_GAP = 65f
        const val ICON_X = 56f
        const val LABEL_RISE = 30.3f
        const val SINGLE_RADIUS = 130.25f
        const val SINGLE_ICON_ANGLE = 139f
        const val ICON_CLEAR = 9f
        const val TIMER_RING_DIAMETER = 98f
        const val TIMER_RING_STROKE = 6.5f

        /** Y6 (camera-pass2 §5): the mode switch's slide, the capture blackout, the dial's sweep and label fade. */
        const val MODE_SWITCH_MS = 300
        const val CAPTURE_BLACK_MS = 167
        const val DIAL_SWEEP_MS = 333
        const val DIAL_LABEL_MS = 100
    }
}
