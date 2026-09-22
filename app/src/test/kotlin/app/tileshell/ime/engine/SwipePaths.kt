package app.tileshell.ime.engine

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Synthesised Word Flow gestures over [TestLayout.w10m], standing in for the finger the emulator's
 * E4 injects with `UiDevice.swipe(Point[], steps)`.
 */
object SwipePaths {

    /** The word's key centres in order, a doubled letter visited once. */
    fun ideal(word: String): List<KeyPoint> {
        val out = ArrayList<KeyPoint>()
        var prev = '\u0000'
        for (ch in word) {
            val c = Character.toLowerCase(ch)
            if (c == prev) continue
            TestLayout.w10m.centres[c]?.let { out.add(it) }
            prev = c
        }
        return out
    }

    /**
     * A finger's path: each corner cut toward its neighbours' midpoint by [cornerCut] of the way but
     * never more than [maxCutPx] (a finger rounds a turn by a fraction of a key, not of the stroke),
     * sampled every [stepPx] along the segments with ±[jitterPx] of wobble, starting up to
     * [startOffsetPx] off the first key's centre. Seeded, so a failing word reproduces.
     */
    fun realistic(
        word: String,
        seed: Int = word.hashCode(),
        jitterPx: Float = 6f,
        cornerCut: Float = 0.2f,
        maxCutPx: Float = 30f,
        stepPx: Float = 25f,
        startOffsetPx: Float = 20f,
    ): List<KeyPoint> {
        val rnd = Random(seed)
        val visits = ideal(word)
        if (visits.isEmpty()) return emptyList()
        val corners = visits.mapIndexed { i, p ->
            if (i == 0 || i == visits.size - 1) p
            else {
                val mx = (visits[i - 1].x + visits[i + 1].x) / 2
                val my = (visits[i - 1].y + visits[i + 1].y) / 2
                val dist = sqrt((mx - p.x) * (mx - p.x) + (my - p.y) * (my - p.y))
                val cut = if (dist == 0f) 0f else minOf(cornerCut, maxCutPx / dist)
                KeyPoint(p.x + (mx - p.x) * cut, p.y + (my - p.y) * cut)
            }
        }
        val out = ArrayList<KeyPoint>()
        fun jitter() = (rnd.nextFloat() * 2 - 1) * jitterPx
        val first = corners[0]
        out.add(KeyPoint(first.x + (rnd.nextFloat() * 2 - 1) * startOffsetPx, first.y + (rnd.nextFloat() * 2 - 1) * startOffsetPx))
        for (i in 1 until corners.size) {
            val a = corners[i - 1]
            val b = corners[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = sqrt(dx * dx + dy * dy)
            val steps = maxOf(1, (len / stepPx).toInt())
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                out.add(KeyPoint(a.x + dx * t + jitter(), a.y + dy * t + jitter()))
            }
        }
        return out
    }

    /** A very fast swipe: the sampler only caught the corners (Edge cases: "very fast swipes"). */
    fun fast(word: String): List<KeyPoint> = ideal(word)
}
