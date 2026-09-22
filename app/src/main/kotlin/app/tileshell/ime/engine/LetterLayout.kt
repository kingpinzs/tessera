package app.tileshell.ime.engine

import kotlin.math.sqrt

/** A point in the keyboard's pixel space. Not android.graphics.PointF, so the engine runs on the plain JVM. */
data class KeyPoint(val x: Float, val y: Float)

/**
 * Where the letter keys are, as the text engine sees them.
 *
 * The IME's geometry (R6 §2.1: a 144-phys column pitch, a 217.5-phys row pitch, rows 2 and 3 offset by
 * half a pitch) is the lead's to lay out; the engine only ever receives key CENTRES in pixels plus the
 * two pitches, and derives everything else — which keys neighbour which, how far a swipe point is from
 * a key — from those. Nothing in here assumes the R6 numbers, so a docked one-handed layout (Decisions
 * stand-in (2): pitch 115.2) or another panel width works unchanged.
 *
 * [centres] holds lowercase letters; the apostrophe key may be absent, and any character with no centre
 * simply has no neighbours.
 */
class LetterLayout(val centres: Map<Char, KeyPoint>, val pitchX: Float, val pitchY: Float) {

    init {
        require(pitchX > 0f && pitchY > 0f) { "pitches must be positive: $pitchX x $pitchY" }
    }

    /**
     * Distance from a pixel point to a key's centre in PITCHES, so one column over and one row down both
     * measure ≈ 1.0 even though a row is 1.5× taller than a column is wide (R6 §2.1.1 vs §2.1.11). Every
     * "how close" question in the engine is asked in this unit.
     */
    fun pitchDistance(x: Float, y: Float, key: Char): Float {
        val c = centres[key] ?: return Float.MAX_VALUE
        val dx = (x - c.x) / pitchX
        val dy = (y - c.y) / pitchY
        return sqrt(dx * dx + dy * dy)
    }

    /** [pitchDistance] between two keys' centres; MAX_VALUE when either has no key. */
    fun pitchDistance(a: Char, b: Char): Float {
        val ca = centres[a] ?: return Float.MAX_VALUE
        return pitchDistance(ca.x, ca.y, b)
    }

    /**
     * Two keys are adjacent when their centres are within [ADJACENT_PITCHES]. On a staggered QWERTY the
     * same-row neighbour sits at 1.0 and the diagonal neighbours at √(0.5² + 1²) ≈ 1.12; the next key
     * over is at 2.0 and the key two rows away at 2.0, so the boundary between them is wide.
     */
    fun adjacent(a: Char, b: Char): Boolean = a != b && pitchDistance(a, b) <= ADJACENT_PITCHES

    /**
     * The keys nearest a point, nearest first, at most [count] of them and none farther than
     * [maxPitches]. A swipe's first sample is usually ON a key but a hurried one lands in the gap next
     * to it, which is why callers ask for two or three rather than one.
     */
    fun nearest(x: Float, y: Float, count: Int, maxPitches: Float = Float.MAX_VALUE): List<Char> {
        val best = ArrayList<Pair<Char, Float>>(count + 1)
        for ((key, _) in centres) {
            val d = pitchDistance(x, y, key)
            if (d > maxPitches) continue
            var i = best.size
            while (i > 0 && best[i - 1].second > d) i--
            if (i < count) {
                best.add(i, key to d)
                if (best.size > count) best.removeAt(best.size - 1)
            }
        }
        return best.map { it.first }
    }

    companion object {
        const val ADJACENT_PITCHES = 1.25f
    }
}
