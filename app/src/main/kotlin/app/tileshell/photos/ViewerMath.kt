package app.tileshell.photos

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A whole-pixel rectangle, right and bottom exclusive. Free of Android types so the rules below are unit-tested. */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * EXIF orientation as geometry (values 1–8). A picture's file holds RAW pixels; the viewer and the editor work in
 * UPRIGHT coordinates (what ImageDecoder shows), and a region decode (BitmapRegionDecoder, which ignores EXIF) needs
 * the raw ones (r3 D8).
 */
object ImageOrientation {
    fun swapsAxes(orientation: Int): Boolean = orientation in 5..8

    /** The upright size of a raw [rawW] × [rawH] picture. */
    fun uprightSize(rawW: Int, rawH: Int, orientation: Int): Pair<Int, Int> = if (swapsAxes(orientation)) rawH to rawW else rawW to rawH

    /** The raw point under the upright point ([x], [y]); continuous coordinates, [rawW] × [rawH] the raw size. */
    fun toRaw(x: Float, y: Float, rawW: Int, rawH: Int, orientation: Int): Pair<Float, Float> = when (orientation) {
        2 -> (rawW - x) to y
        3 -> (rawW - x) to (rawH - y)
        4 -> x to (rawH - y)
        5 -> y to x
        6 -> y to (rawH - x)
        7 -> (rawW - y) to (rawH - x)
        8 -> (rawW - y) to x
        else -> x to y
    }

    /** The raw rectangle under an upright one, clamped to the picture. */
    fun toRaw(r: PxRect, rawW: Int, rawH: Int, orientation: Int): PxRect {
        val (x0, y0) = toRaw(r.left.toFloat(), r.top.toFloat(), rawW, rawH, orientation)
        val (x1, y1) = toRaw(r.right.toFloat(), r.bottom.toFloat(), rawW, rawH, orientation)
        return PxRect(
            min(x0, x1).toInt().coerceIn(0, rawW), min(y0, y1).toInt().coerceIn(0, rawH),
            max(x0, x1).toInt().coerceIn(0, rawW), max(y0, y1).toInt().coerceIn(0, rawH),
        )
    }

    /** Degrees clockwise, then whether to mirror left–right, that turn raw pixels upright. */
    fun rotationAndFlip(orientation: Int): Pair<Int, Boolean> = when (orientation) {
        2 -> 0 to true
        3 -> 180 to false
        4 -> 180 to true
        5 -> 90 to true
        6 -> 90 to false
        7 -> 270 to true
        8 -> 270 to false
        else -> 0 to false
    }
}

/** The viewer's placement and zoom rules (Y2; r11/photos.md 1.6.4, UNMEASURED-8). */
object ViewerMath {
    /** The photo fitted to the screen's width, and inside its height when it is taller than that: width × height. */
    fun fit(imageW: Int, imageH: Int, screenW: Float, screenH: Float): Pair<Float, Float> {
        if (imageW <= 0 || imageH <= 0) return screenW to screenW
        val s = min(screenW / imageW, screenH / imageH)
        return imageW * s to imageH * s
    }

    /** A double-tap's zoom: to the picture's own pixels where that is more than twice the fitted size, else to twice. */
    fun doubleTapScale(imagePx: Int, fittedPx: Float): Float = max(2f, imagePx / fittedPx)

    /** The most a pinch zooms in: twice the double-tap's zoom. */
    fun maxScale(imagePx: Int, fittedPx: Float): Float = 2f * doubleTapScale(imagePx, fittedPx)

    /** A pan kept so the zoomed picture never leaves a gap it could cover: 0 when it is no larger than the view. */
    fun clampPan(pan: Float, contentPx: Float, viewPx: Float): Float {
        val room = (contentPx - viewPx) / 2f
        return if (room <= 0f) 0f else pan.coerceIn(-room, room)
    }

    /**
     * The part of the upright picture the screen shows, in the picture's own pixels. The picture is drawn
     * [shownW] × [shownH] px with its centre [panX], [panY] px off the centre of a [viewW] × [viewH] px view.
     */
    fun visibleRegion(imageW: Int, imageH: Int, shownW: Float, shownH: Float, panX: Float, panY: Float, viewW: Float, viewH: Float): PxRect {
        val left = (viewW - shownW) / 2f + panX
        val top = (viewH - shownH) / 2f + panY
        val sx = imageW / shownW
        val sy = imageH / shownH
        return PxRect(
            floor((0f - left) * sx).toInt().coerceIn(0, imageW), floor((0f - top) * sy).toInt().coerceIn(0, imageH),
            ceil((viewW - left) * sx).toInt().coerceIn(0, imageW), ceil((viewH - top) * sy).toInt().coerceIn(0, imageH),
        )
    }

    /** The power-of-two sample size at which a [regionPx]-wide region decodes to no more than about [targetPx]. */
    fun sampleSize(regionPx: Int, targetPx: Int): Int {
        var sample = 1
        while (regionPx / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** The item a slideshow or a delete lands on after [index] of [count]: the next, wrapping to the first. */
    fun nextIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1) % count
}
