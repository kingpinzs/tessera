package app.tileshell.ui

import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.withFrameNanos
import app.tileshell.diag.Diagnostics
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The shell's own motion clock (phase 15 Decisions C-5 and C-31; Acceptance preamble "Motion clock").
 *
 * The emulator's screenrecord is variable-rate (qa/phase-05/README.md), so a motion row cannot time an animation
 * off a capture. Every motion this phase animates runs through [animate], which drives the value frame by frame
 * from the Choreographer clock and writes one line when it ends:
 *
 *     [motion] <name> t0=<uptime ms> peak=<ms> overshoot=<%> settle=<ms> frames=<n> maxGapMs=<ms>
 *
 * `peak` is when the value was furthest along, `overshoot` how far past the target it went (0 for these
 * curves), `settle` when it reached the target, `frames` how many frames drew it and `maxGapMs` the longest gap
 * between two of them — so a janky motion fails on the shell's own clock (≤ 33.4 ms, two vsyncs).
 */
object MotionClock {
    suspend fun animate(name: String, durationMs: Int, easing: Easing, onValue: (Float) -> Unit) {
        val t0Uptime = SystemClock.uptimeMillis()
        val t0 = withFrameNanos { it }
        onValue(easing.transform(0f))
        var last = t0
        var frames = 0
        var maxGap = 0.0
        var peak = 0f
        var peakAt = 0L
        var settle: Long
        while (true) {
            val now = withFrameNanos { it }
            frames++
            maxGap = max(maxGap, (now - last) / 1_000_000.0)
            last = now
            val elapsed = (now - t0) / 1_000_000
            val fraction = (elapsed.toFloat() / durationMs).coerceAtMost(1f)
            val v = easing.transform(fraction)
            onValue(v)
            if (v > peak) { peak = v; peakAt = elapsed }
            if (fraction >= 1f) { settle = elapsed; break }
        }
        val overshoot = ((peak - 1f).coerceAtLeast(0f) * 100).roundToInt()
        Diagnostics.add("motion", "$name t0=$t0Uptime peak=$peakAt overshoot=$overshoot% settle=$settle frames=$frames maxGapMs=${"%.1f".format(maxGap)}")
    }

    /**
     * A change drawn in one frame (a tab tap's jump, the record button's cut): [settle] is the time from the input
     * to the first frame that shows the new state, measured by the caller with [SystemClock.uptimeMillis].
     */
    fun jump(name: String, inputUptime: Long, firstFrameUptime: Long) {
        Diagnostics.add("motion", "$name t0=$inputUptime peak=0 overshoot=0% settle=${firstFrameUptime - inputUptime} frames=1 maxGapMs=0.0")
    }
}
