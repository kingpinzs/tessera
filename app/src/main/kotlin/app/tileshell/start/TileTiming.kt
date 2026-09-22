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
     * How long until this tile's next turn.
     *
     * R3 A8 says every tile has its own timer with a random start phase so two tiles do not flip in
     * lockstep, and the build drew that phase at random. Random is not spread: N independent draws out of
     * one band is a birthday problem, and on Jeremy's phone two tiles landed 400 ms apart and stayed
     * there — "they shouldn't do it one after another each should be on their own timer" (2026-09-22).
     * Spreading the phases by index was not enough either, because each tile's phase was measured from
     * when its OWN content arrived, so tiles whose feeds answered at different moments still bunched up:
     * measured on the emulator, the closest pair was 244 ms apart.
     *
     * So the turn is worked out from the CLOCK rather than from when this tile happened to start. Tile i
     * of n takes the slot i/n of the way through each [bandMs] cycle, and waits for the next time the
     * clock is at its slot. Two tiles with different indices are therefore a whole slice apart, always,
     * however late one of them woke up — and no tile is told anything by any other, so there is still no
     * global scheduler, only a shared clock they each read.
     *
     * THE COST, recorded because it is a real one: every tile now turns once per [bandMs] instead of on
     * its own random period inside R3 A8's band. The band is 4.96 ± 0.22 s and [bandMs] is its centre, so
     * the rate is what was measured; what is gone is the variation between tiles. That is the trade
     * Jeremy asked for — tiles that never go one after another — and it is one constant to undo.
     *
     * A slideshow ignores all of this and starts at once: someone who just turned it on is looking at the
     * tile, and a wait of up to a period before anything happens reads as "it did not work".
     */
    fun untilSlotMs(nowMs: Long, bandMs: Long, index: Int, count: Int): Long {
        if (bandMs <= 0L) return 0L
        val n = count.coerceAtLeast(1)
        val slot = bandMs * (index.coerceAtLeast(0) % n) / n
        val wait = ((slot - nowMs) % bandMs + bandMs) % bandMs
        return if (wait == 0L) bandMs else wait
    }

    /**
     * The one rate every cycling tile shares, so that the comb is a single comb.
     *
     * R3 A8 measured two bands — flip tiles at 4.96 ± 0.22 s and crossfade tiles at 4.4 ± 0.4 s — and a
     * tile on each would be on a DIFFERENT comb, drifting against the other until the two coincided,
     * which is the thing this exists to prevent. One comb means one rate. It is the centre of the flip
     * band, which is the band most live tiles are on, and it sits inside the crossfade band's own
     * tolerance (4.4 + 0.4 = 4.8 s is the nearest edge, 4.96 s is 0.16 s past it).
     *
     * This is the deviation from R3 that Jeremy's "each should be on their own timer" costs, written
     * where it can be found and undone: restore [bandCentreMs] per transition and the two bands come
     * back, along with the occasional pair of tiles turning together.
     */
    const val COMB_MS = 4960L

    /** The centre of a measured band. */
    fun bandCentreMs(minMs: Long, maxMs: Long): Long = (minMs + maxMs) / 2

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
