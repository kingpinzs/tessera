package app.tileshell.start

/**
 * When a tile changes face: R3 A8's own timer for a live tile, or a slideshow's fixed cadence
 * (Jeremy, 2026-09-21: "the photo one should have a slide show option on the tile"). INDEX item 4.
 *
 * It is a separate pure object because [TileView]'s loop is the one place this can go wrong quietly —
 * a tile that drifts, stalls or fires twice looks like "the emulator being slow" and is only ever caught
 * on a device by eye. Here the three decisions the loop makes are each one line, on the JVM.
 *
 * R3 A8 stands unchanged for every tile that is not running a slideshow: its own timer, a random start
 * phase, and a period drawn at random inside the measured band, with no global scheduler.
 */
object TileTiming {

    /**
     * How long a slideshow holds each photo.
     *
     * A slideshow has to read as a slideshow rather than as the Photos tile doing what it always did, so
     * it is faster than R3 A8's 4.0-4.8 s crossfade band and, unlike it, FIXED: a slideshow with a random
     * period is a tile that looks like it is stuttering. 3 s is the agent's pick (no W10M original — W10M
     * had no slideshow tile), and it is one constant to change.
     */
    const val SLIDESHOW_MS = 3000

    /**
     * The wait before a tile's FIRST face change.
     *
     * R3 A8: every tile starts at a random phase so two tiles do not flip in lockstep. A slideshow starts
     * at once instead — someone who just turned it on is looking at the tile, and a wait of up to a period
     * before anything happens reads as "it did not work".
     */
    fun startPhaseMs(slideshowMs: Int, bandMaxMs: Long, draw: (Long) -> Long): Long =
        if (slideshowMs > 0) 0L else draw(bandMaxMs)

    /** The period from one face change to the next: the slideshow's cadence, or a draw inside R3 A8's band. */
    fun periodMs(slideshowMs: Int, bandMinMs: Long, bandMaxMs: Long, draw: (Long, Long) -> Long): Long =
        if (slideshowMs > 0) slideshowMs.toLong() else draw(bandMinMs, bandMaxMs + 1)

    /**
     * What is left of a period after the face change's own animation, never negative.
     *
     * R3 A8's periods are start-to-start, so the animation's time comes out of the period rather than
     * being added to it; an animation longer than the period means the next change is due now.
     */
    fun remainingMs(periodMs: Long, animationMs: Long): Long = (periodMs - animationMs).coerceAtLeast(0L)

    /**
     * The face on show after [changes] face changes of a tile with [faceCount] live faces.
     *
     * Face 0 is the tile's front (its logo, or its own content); 1..n are the live faces. This is the
     * modulo the loop steps through, written where it can be checked: a slideshow of 3 photos shows
     * front, photo, photo, photo, front, ... and comes back to where it started, rather than drifting off
     * the end of the list when the count changes under it.
     */
    fun faceAfter(changes: Int, faceCount: Int): Int =
        if (faceCount <= 0) 0 else ((changes % (faceCount + 1)) + (faceCount + 1)) % (faceCount + 1)
}
