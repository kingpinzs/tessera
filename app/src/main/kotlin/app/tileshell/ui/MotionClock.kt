package app.tileshell.ui

import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.withFrameNanos
import app.tileshell.diag.Diagnostics

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
    /** Drives [onValue] from 0 to 1 over [durationMs] on [easing], one value per drawn frame, and logs the frames. */
    suspend fun animate(name: String, durationMs: Int, easing: Easing, onValue: (Float) -> Unit) {
        val trace = MotionTrace(name, SystemClock.uptimeMillis())
        val start = withFrameNanos { it }
        val first = easing.transform(0f)
        onValue(first)
        trace.frame(start, first)
        while (true) {
            val now = withFrameNanos { it }
            val fraction = ((now - start) / 1_000_000f / durationMs).coerceAtMost(1f)
            val v = easing.transform(fraction)
            onValue(v)
            trace.frame(now, v)
            if (fraction >= 1f) break
        }
        Diagnostics.add("motion", trace.message())
    }

    /**
     * A change drawn in one frame (a tab tap's jump): the input at [inputUptime], the first frame showing the new
     * state at [firstFrameUptime] — so `settle` is input-to-frame on the uptime clock.
     */
    fun jump(name: String, inputUptime: Long, firstFrameUptime: Long) {
        val trace = MotionTrace(name, inputUptime)
        trace.frame(firstFrameUptime * 1_000_000L, 1f)
        Diagnostics.add("motion", trace.message())
    }
}
