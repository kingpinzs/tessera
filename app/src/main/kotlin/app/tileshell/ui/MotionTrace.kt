package app.tileshell.ui

/**
 * The shell's one motion clock (phase 15 C-5 / C-31 / T15-32): the frames a motion was drawn on, as
 * `withFrameNanos` reported them, turned into the one line rows assert against —
 * `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%> settle=<ms> frames=<n> maxGapMs=<ms>` — every time measured
 * from t0, the input's or the motion's start on the uptime clock. [MotionClock] drives animations through it, and a
 * one-frame cut (the record → recording change) records its frames here directly. Pure, so the arithmetic is proven
 * on the host JVM (MotionTraceTest). (Written for Voice Recorder; made the shell's single clock at its merge, Rule 16.)
 */
class MotionTrace(val name: String, val t0UptimeMs: Long) {

    private val frameMs = ArrayList<Double>()
    private val values = ArrayList<Float>()

    /**
     * One drawn frame at [frameTimeNanos] (Choreographer's clock, the same monotonic base as uptime) showing the
     * motion at [value] of the way to its end (1 = settled; a value past 1 is overshoot).
     */
    fun frame(frameTimeNanos: Long, value: Float) {
        frameMs += frameTimeNanos / 1_000_000.0
        values += value
    }

    val frames: Int get() = frameMs.size

    /**
     * The line's message (Diagnostics adds the `[motion]` tag), measured from [t0UptimeMs]: peak = the frame with
     * the largest value, settle = the first frame at 1, maxGapMs = the longest time between two drawn frames.
     */
    fun message(): String {
        if (frameMs.isEmpty()) return "$name t0=$t0UptimeMs peak=0 overshoot=0 settle=0 frames=0 maxGapMs=0"
        val peakIndex = values.indices.maxByOrNull { values[it] } ?: 0
        val settleIndex = values.indexOfFirst { it >= 1f }.let { if (it < 0) values.lastIndex else it }
        val overshoot = ((values.maxOrNull() ?: 1f) - 1f).coerceAtLeast(0f) * 100f
        var maxGap = 0.0
        for (i in 1 until frameMs.size) maxGap = maxOf(maxGap, frameMs[i] - frameMs[i - 1])
        return "$name t0=$t0UptimeMs peak=${ms(frameMs[peakIndex])} overshoot=${Math.round(overshoot)} " +
            "settle=${ms(frameMs[settleIndex])} frames=${frameMs.size} maxGapMs=${Math.round(maxGap)}"
    }

    /** A frame time as milliseconds after t0. */
    private fun ms(frame: Double): Long = Math.round(frame - t0UptimeMs).coerceAtLeast(0L)
}
