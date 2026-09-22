package app.tileshell.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crossfade (phase 10, E17 — the one part of Q7's "everything" the first playback build left out).
 *
 * ExoPlayer has no crossfade, so the service runs a second player for the few seconds of a fade: the
 * session's own player keeps the whole queue and makes every transition itself, and the "fader" plays
 * the incoming track underneath the outgoing one, then hands it back. [CrossfadeFader] is that machine;
 * this object is the arithmetic it runs on, kept pure so each rule is proved on the JVM.
 */
object Crossfade {
    const val OFF = 0

    /** The shortest fade worth doing; anything shorter is heard as a glitch rather than a fade. */
    const val MIN_FADE_MS = 1_000L

    /** How long before the fade the fader is prepared, so its duration is known in time (rule [fadeLength]). */
    const val PREPARE_LEAD_MS = 3_000L

    /** The handback accepts two copies of the same audio this far apart; further means seek again. */
    const val HANDBACK_TOLERANCE_MS = 40L

    /** The micro-fade that moves the incoming track from the fader back to the session's player. */
    const val HANDBACK_FADE_MS = 200L

    /**
     * What the ••• menu offers. No W10M original had a crossfade to measure; 0-12 s is the range every
     * player with one offers, in steps far enough apart to hear. Off is first and is the default, so
     * gapless stays what a phone does until someone asks for otherwise.
     */
    enum class Choice(val ms: Int, val label: String, val tag: String) {
        OFF(0, "Off", "off"),
        S2(2_000, "2 seconds", "2"),
        S5(5_000, "5 seconds", "5"),
        S8(8_000, "8 seconds", "8"),
        S12(12_000, "12 seconds", "12"),
    }

    fun menuLabel(ms: Int): String =
        "Crossfade: " + (Choice.entries.firstOrNull { it.ms == ms && ms > 0 }?.label ?: "off")

    /**
     * How long this fade actually is: the setting, but never more than half of either track — a fade
     * longer than half a track would still be fading the outgoing one when the incoming one ends — and
     * nothing at all below [MIN_FADE_MS].
     */
    fun fadeLength(settingMs: Int, currentDurationMs: Long, nextDurationMs: Long): Long {
        if (settingMs <= 0 || currentDurationMs <= 0L || nextDurationMs <= 0L) return 0L
        val len = minOf(settingMs.toLong(), currentDurationMs / 2, nextDurationMs / 2)
        return if (len < MIN_FADE_MS) 0L else len
    }

    /** A fade that starts late (resumed near the end, a slow prepare) is shortened to what is left, never overrun. */
    fun startLength(plannedMs: Long, leftMs: Long): Long {
        val len = minOf(plannedMs, leftMs)
        return if (len < MIN_FADE_MS) 0L else len
    }

    /**
     * Whether a fade may happen at the end of the current item at all.
     *
     * Repeat-one would fade a track into itself; an armed end-of-track sleep timer means the music is
     * about to STOP, and a fade would leave the incoming track playing on after the pause; and with no
     * next item there is nothing to fade into.
     */
    fun eligible(settingMs: Int, hasNext: Boolean, repeatOne: Boolean, sleepAtEndOfTrack: Boolean): Boolean =
        settingMs > 0 && hasNext && !repeatOne && !sleepAtEndOfTrack

    /**
     * The equal-power pair at progress [t] (0..1): outgoing = cos(t·π/2), incoming = sin(t·π/2).
     * Their squares always sum to 1, so the loudness stays level through the fade instead of dipping in
     * the middle the way two straight-line ramps do.
     */
    fun gains(t: Float): Pair<Float, Float> {
        val x = t.coerceIn(0f, 1f) * (PI.toFloat() / 2f)
        return cos(x) to sin(x)
    }

    /** Whether two copies of the same audio are close enough to swap between without hearing it. */
    fun handbackAligned(primaryPosMs: Long, faderPosMs: Long): Boolean =
        abs(primaryPosMs - faderPosMs) <= HANDBACK_TOLERANCE_MS
}
