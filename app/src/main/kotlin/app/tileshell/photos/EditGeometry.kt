package app.tileshell.photos

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** A rectangle as fractions (0–1) of a frame: the crop, kept free of any one resolution. */
data class NormRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    companion object {
        val FULL = NormRect(0.0, 0.0, 1.0, 1.0)
    }
}

/**
 * The editor's geometry (phase 17 Decisions T17-3), free of Android types. A picture goes through, in this fixed
 * order: [quarterTurns] clockwise quarter turns (Rotate), a straighten by [straighten] degrees (the content turned
 * counter-clockwise for a positive angle, then centre-cropped to the largest axis-aligned rectangle inside the turned
 * picture), then [crop], a fraction of that straightened frame. [srcW] × [srcH] is the UPRIGHT source.
 *
 * The same plan serves the ≤ 4096-px preview and the full-resolution save, because nothing in it is in pixels.
 */
data class EditPlan(val srcW: Int, val srcH: Int, val quarterTurns: Int = 0, val straighten: Double = 0.0, val crop: NormRect = NormRect.FULL) {
    private val q = ((quarterTurns % 4) + 4) % 4
    val turnedW: Int = if (q % 2 == 1) srcH else srcW
    val turnedH: Int = if (q % 2 == 1) srcW else srcH
    private val straightened = EditGeometry.straightenSize(turnedW, turnedH, straighten)
    val straightW: Int = straightened.first
    val straightH: Int = straightened.second
    val cropLeft: Int = (crop.left * straightW).roundToInt().coerceIn(0, straightW - 1)
    val cropTop: Int = (crop.top * straightH).roundToInt().coerceIn(0, straightH - 1)
    val outW: Int = ((crop.right * straightW).roundToInt().coerceIn(cropLeft + 1, straightW)) - cropLeft
    val outH: Int = ((crop.bottom * straightH).roundToInt().coerceIn(cropTop + 1, straightH)) - cropTop

    /** True when every output pixel is exactly one source pixel (no straighten): the save copies, it does not resample. */
    val exact: Boolean get() = straighten == 0.0

    private val rad = Math.toRadians(straighten)
    private val cosA = cos(rad)
    private val sinA = sin(rad)

    /** The upright source point under the output point ([ox], [oy]); continuous coordinates (a pixel's centre is +0.5). */
    fun source(ox: Double, oy: Double, out: DoubleArray) {
        // Output → the straightened frame → the turned frame (undo the turn about the centre).
        val dx = cropLeft + ox - straightW / 2.0
        val dy = cropTop + oy - straightH / 2.0
        val tx = dx * cosA - dy * sinA + turnedW / 2.0
        val ty = dx * sinA + dy * cosA + turnedH / 2.0
        // The turned frame → the upright source (undo the clockwise quarter turns).
        when (q) {
            1 -> { out[0] = ty; out[1] = srcH - tx }
            2 -> { out[0] = srcW - tx; out[1] = srcH - ty }
            3 -> { out[0] = srcW - ty; out[1] = tx }
            else -> { out[0] = tx; out[1] = ty }
        }
    }

    /** The upright source rectangle that the output rectangle [ox0, ox1) × [oy0, oy1) reads, with [pad] px around it, clamped. */
    fun sourceBounds(ox0: Int, oy0: Int, ox1: Int, oy1: Int, pad: Int): PxRect {
        val p = DoubleArray(2)
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for ((x, y) in listOf(ox0 to oy0, ox1 to oy0, ox0 to oy1, ox1 to oy1)) {
            source(x.toDouble(), y.toDouble(), p)
            minX = minOf(minX, p[0]); maxX = maxOf(maxX, p[0]); minY = minOf(minY, p[1]); maxY = maxOf(maxY, p[1])
        }
        return PxRect(
            (floor(minX).toInt() - pad).coerceIn(0, srcW), (floor(minY).toInt() - pad).coerceIn(0, srcH),
            (kotlin.math.ceil(maxX).toInt() + pad).coerceIn(0, srcW), (kotlin.math.ceil(maxY).toInt() + pad).coerceIn(0, srcH),
        )
    }
}

object EditGeometry {
    const val STRAIGHTEN_MIN = -45
    const val STRAIGHTEN_MAX = 45

    /**
     * The largest axis-aligned rectangle inside a [w] × [h] picture turned by [degrees], each side floored — the rule
     * the gate's `edit_expect.py straighten` computes (640 × 480 at −10° → 582 × 384).
     */
    fun straightenSize(w: Int, h: Int, degrees: Double): Pair<Int, Int> {
        if (degrees == 0.0 || w <= 0 || h <= 0) return w to h
        val a = Math.toRadians(degrees)
        val sinA = abs(sin(a))
        val cosA = abs(cos(a))
        val widthIsLonger = w >= h
        val long = if (widthIsLonger) w.toDouble() else h.toDouble()
        val short = if (widthIsLonger) h.toDouble() else w.toDouble()
        val (wr, hr) = if (short <= 2.0 * sinA * cosA * long || abs(sinA - cosA) < 1e-10) {
            // Half-constrained: two corners touch the longer side.
            val x = 0.5 * short
            if (widthIsLonger) x / sinA to x / cosA else x / cosA to x / sinA
        } else {
            val cos2A = cosA * cosA - sinA * sinA
            (w * cosA - h * sinA) / cos2A to (h * cosA - w * sinA) / cos2A
        }
        return floor(wr + 1e-9).toInt().coerceAtLeast(1) to floor(hr + 1e-9).toInt().coerceAtLeast(1)
    }

    /**
     * One output tile sampled from decoded source pixels. [src] holds the upright source rectangle [srcRect] (row-major
     * ARGB); the tile is the output rectangle at ([ox0], [oy0]) of [tw] × [th], written row-major into [out] from
     * [outOffset] with row stride [outStride]. Exact plans copy pixels; a straighten samples bilinearly.
     */
    fun sampleTile(plan: EditPlan, src: IntArray, srcRect: PxRect, ox0: Int, oy0: Int, tw: Int, th: Int, out: IntArray, outOffset: Int, outStride: Int) {
        val p = DoubleArray(2)
        val sw = srcRect.width
        val sh = srcRect.height
        for (y in 0 until th) {
            val row = outOffset + y * outStride
            for (x in 0 until tw) {
                plan.source(ox0 + x + 0.5, oy0 + y + 0.5, p)
                if (plan.exact) {
                    val sx = (floor(p[0]).toInt() - srcRect.left).coerceIn(0, sw - 1)
                    val sy = (floor(p[1]).toInt() - srcRect.top).coerceIn(0, sh - 1)
                    out[row + x] = src[sy * sw + sx]
                } else {
                    val fx = p[0] - 0.5 - srcRect.left
                    val fy = p[1] - 0.5 - srcRect.top
                    val x0 = floor(fx).toInt()
                    val y0 = floor(fy).toInt()
                    val ax = fx - x0
                    val ay = fy - y0
                    val xa = x0.coerceIn(0, sw - 1)
                    val xb = (x0 + 1).coerceIn(0, sw - 1)
                    val ya = y0.coerceIn(0, sh - 1)
                    val yb = (y0 + 1).coerceIn(0, sh - 1)
                    out[row + x] = bilinear(src[ya * sw + xa], src[ya * sw + xb], src[yb * sw + xa], src[yb * sw + xb], ax, ay)
                }
            }
        }
    }

    private fun bilinear(p00: Int, p10: Int, p01: Int, p11: Int, ax: Double, ay: Double): Int {
        var result = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val top = ((p00 ushr shift) and 0xFF) * (1 - ax) + ((p10 ushr shift) and 0xFF) * ax
            val bottom = ((p01 ushr shift) and 0xFF) * (1 - ax) + ((p11 ushr shift) and 0xFF) * ax
            result = result or ((top * (1 - ay) + bottom * ay + 0.5).toInt().coerceIn(0, 255) shl shift)
        }
        return result
    }
}

/**
 * Red-eye (T17-3): red-dominant blobs — pixels with `r > 1.5·max(g, b)`, connected, at least [MIN_ACROSS] px across
 * both ways — have their red channel halved; nothing outside a blob changes. Free of Android types.
 */
object RedEye {
    const val MIN_ACROSS = 6

    fun isRed(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return r > 1.5 * maxOf(g, b)
    }

    /** The red channel halved (rounded down, so the result is never more than half). */
    fun fix(argb: Int): Int = (argb and 0xFF00FFFF.toInt()) or ((((argb shr 16) and 0xFF) shr 1) shl 16)

    /** One blob: its bounding box in the picture it was found in, and which pixels of that box are its own. */
    class Blob(val left: Int, val top: Int, val width: Int, val height: Int, private val bits: BooleanArray) {
        fun contains(x: Int, y: Int): Boolean {
            val bx = x - left
            val by = y - top
            return bx in 0 until width && by in 0 until height && bits[by * width + bx]
        }
        val pixelCount: Int get() = bits.count { it }

        /** The same blob in a picture this one is a part of, [dx], [dy] being the part's corner there. */
        fun shifted(dx: Int, dy: Int) = Blob(left + dx, top + dy, width, height, bits)
    }

    /**
     * The blobs of a [w] × [h] picture that have a pixel inside [seed] (the whole picture when null) — a blob grows
     * past the seed window to its own edge. 4-connected.
     */
    fun detect(pixels: IntArray, w: Int, h: Int, seed: PxRect? = null): List<Blob> {
        val window = seed ?: PxRect(0, 0, w, h)
        val seen = BooleanArray(w * h)
        val blobs = mutableListOf<Blob>()
        val stack = IntArray(w * h)
        for (sy in window.top.coerceAtLeast(0) until window.bottom.coerceAtMost(h)) {
            for (sx in window.left.coerceAtLeast(0) until window.right.coerceAtMost(w)) {
                val start = sy * w + sx
                if (seen[start] || !isRed(pixels[start])) continue
                var top = 0
                stack[top++] = start
                seen[start] = true
                val members = ArrayList<Int>()
                var minX = sx; var maxX = sx; var minY = sy; var maxY = sy
                while (top > 0) {
                    val i = stack[--top]
                    members += i
                    val x = i % w
                    val y = i / w
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    if (x > 0 && !seen[i - 1] && isRed(pixels[i - 1])) { seen[i - 1] = true; stack[top++] = i - 1 }
                    if (x < w - 1 && !seen[i + 1] && isRed(pixels[i + 1])) { seen[i + 1] = true; stack[top++] = i + 1 }
                    if (y > 0 && !seen[i - w] && isRed(pixels[i - w])) { seen[i - w] = true; stack[top++] = i - w }
                    if (y < h - 1 && !seen[i + w] && isRed(pixels[i + w])) { seen[i + w] = true; stack[top++] = i + w }
                }
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                if (bw >= MIN_ACROSS && bh >= MIN_ACROSS) {
                    val bits = BooleanArray(bw * bh)
                    for (i in members) bits[(i / w - minY) * bw + (i % w - minX)] = true
                    blobs += Blob(minX, minY, bw, bh, bits)
                }
            }
        }
        return blobs
    }

    /**
     * Whether the pixel ([x], [y]) of a [fullW] × [fullH] picture lies in one of [blobs], which were found in the same
     * picture at [foundW] × [foundH] (the editor's preview): the save applies the preview's blobs at full resolution.
     */
    fun inBlobs(blobs: List<Blob>, x: Int, y: Int, fullW: Int, fullH: Int, foundW: Int, foundH: Int): Boolean {
        if (blobs.isEmpty()) return false
        val px = (x.toLong() * foundW / fullW).toInt()
        val py = (y.toLong() * foundH / fullH).toInt()
        for (b in blobs) if (b.contains(px, py)) return true
        return false
    }
}

/** The trim screen's arithmetic (Y13), free of Android types. */
object TrimMath {
    /** The handles snap to tenths of a second. */
    const val STEP_MS = 100L

    /** The least a trim keeps. */
    const val MIN_MS = 500L

    fun snap(ms: Long, durationMs: Long): Long = ((ms + STEP_MS / 2) / STEP_MS * STEP_MS).coerceIn(0L, durationMs)

    /** The time under a point [x] px along a track from [trackLeft] of [trackWidth] px. */
    fun timeAt(x: Float, trackLeft: Float, trackWidth: Float, durationMs: Long): Long =
        snap((((x - trackLeft) / trackWidth).coerceIn(0f, 1f) * durationMs).toLong(), durationMs)

    fun xAt(ms: Long, trackLeft: Float, trackWidth: Float, durationMs: Long): Float =
        if (durationMs <= 0) trackLeft else trackLeft + trackWidth * ms / durationMs

    /** The start handle moved to [ms]: never closer than [MIN_MS] to the end. */
    fun moveStart(ms: Long, endMs: Long): Long = ms.coerceIn(0L, (endMs - MIN_MS).coerceAtLeast(0L))

    fun moveEnd(ms: Long, startMs: Long, durationMs: Long): Long = ms.coerceIn((startMs + MIN_MS).coerceAtMost(durationMs), durationMs)

    /** A range's end as the track's labels show it: "0:03", "1:02:03". */
    fun label(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }
}
