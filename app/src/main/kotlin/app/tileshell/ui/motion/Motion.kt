package app.tileshell.ui.motion

/**
 * Motion values written in-house (RV5) from R3 measurements (RV9 source order). Where R3 gives per-frame
 * tables instead of a curve (A12), the tables are used directly as keyframes (approximation X10).
 */
object Motion {
    const val FRAME_MS = 1000f / 60f

    // R3 A7 (15063, HIGH): flip = vertical squash about the tile's horizontal centre, 108 ± 17 ms.
    const val FLIP_MS = 108

    // R3 A7 (MEDIUM): cycle crossfade 367 ± 17 ms.
    const val CROSSFADE_MS = 367

    // R3 A7 (MEDIUM): peek panel travel, 50 % at 250 ms, 90 % at 550 ms, gone at ≈920 ms (ease-out).
    const val PEEK_MS = 920
    val peekKeyframes: List<Pair<Int, Float>> = listOf(0 to 0f, 250 to 0.5f, 550 to 0.9f, 920 to 1f)

    // R3 A8 (MEDIUM): per-tile timers, flip tiles 4.96 ± 0.22 s, crossfade tiles 4.4 ± 0.4 s.
    const val FLIP_PERIOD_MIN_MS = 4740L
    const val FLIP_PERIOD_MAX_MS = 5180L
    const val CROSSFADE_PERIOD_MIN_MS = 4000L
    const val CROSSFADE_PERIOD_MAX_MS = 4800L

    // R3 A11 (HIGH form + duration, MEDIUM per frame): Start exit on launch.
    /** Global grid scale per 16.7-ms frame after the first row starts fading (S2 Cortana tap). */
    val exitScaleFrames: List<Float> = listOf(1.00f, 1.04f, 1.04f, 1.08f, 1.12f, 1.16f, 1.24f, 1.32f, 1.44f, 1.56f)
    const val EXIT_ROW_STAGGER_MS = 30
    const val EXIT_ROW_FADE_MS = 150
    const val EXIT_TOTAL_MS = 250
    const val EXIT_TAPPED_EXTRA_MS = 67

    // R3 A11 (MEDIUM, S1 14393): Start entrance on return.
    val entranceScaleFrames: List<Float> = listOf(0.78f, 0.86f, 0.92f, 0.94f, 0.94f, 0.94f, 0.96f, 0.98f, 0.98f, 1.00f)
    val entranceAlphaFrames: List<Float> = listOf(0.01f, 0.13f, 0.35f, 0.51f, 0.59f, 0.73f, 0.88f, 0.92f, 0.95f, 0.99f, 1.00f)

    // X13 (approximation): Start <-> app list pivot settles with an ease-out over 250 ms.
    const val PIVOT_SETTLE_MS = 250

    // X7 (approximation): Settings page-to-page = Start entrance form (scale 0.78 -> 1 + fade, ≈217 ms).
    const val PAGE_ENTER_MS = 217

    // R1 §3.1 WP7/8 toolkit tilt, used only for the optional "Windows Phone 8 tilt" press style (Q6).
    const val TILT_MAX_ANGLE_RAD = 0.3f
    const val TILT_MAX_DEPRESSION_EPX = 25f
    const val TILT_RETURN_DELAY_MS = 200
    const val TILT_RETURN_MS = 100

    /** Linear sample of a per-frame table at [elapsedMs]. */
    fun sampleFrames(frames: List<Float>, elapsedMs: Float): Float {
        if (frames.isEmpty()) return 1f
        val position = elapsedMs / FRAME_MS
        if (position <= 0f) return frames.first()
        if (position >= frames.lastIndex) return frames.last()
        val i = position.toInt()
        val t = position - i
        return frames[i] + (frames[i + 1] - frames[i]) * t
    }

    /** Linear sample of (ms, value) keyframes. */
    fun sampleKeyframes(keys: List<Pair<Int, Float>>, elapsedMs: Float): Float {
        if (elapsedMs <= keys.first().first) return keys.first().second
        for (i in 1 until keys.size) {
            val (t1, v1) = keys[i]
            if (elapsedMs <= t1) {
                val (t0, v0) = keys[i - 1]
                return v0 + (v1 - v0) * ((elapsedMs - t0) / (t1 - t0))
            }
        }
        return keys.last().second
    }
}
