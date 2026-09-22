package app.tileshell.feeds

import app.tileshell.start.TileTiming

/**
 * What the Photos tile should be (Jeremy, 2026-09-21: "the photo one should have a slide show option on
 * the tile and be able to make it a bit bigger when its playing the slide show and also set a main photo
 * kind of like a picture frame and it does not flip when that is set"). INDEX Change Log item 4.
 *
 * Pure, so the one rule that must never bend — a picture frame does not flip — is provable without a
 * device and without a photo.
 */
object PhotoRules {

    /** How many photos the tile cycles normally (the phase 01 behaviour, unchanged). */
    const val MAX_PHOTOS = 8

    /**
     * How many a slideshow cycles. More than the plain tile, because a slideshow that loops 8 photos
     * every 3 s is the same 8 photos every 24 s; still bounded, because each one is a decoded thumbnail
     * held in memory for as long as Start is alive.
     */
    const val MAX_SLIDESHOW_PHOTOS = 24

    /** What the tile does with what it found. */
    data class Plan(
        /** The main photo REPLACES the logo and there are no other faces: the tile cannot flip. */
        val frame: Boolean,
        /** How many photos to publish as live faces. */
        val faces: Int,
        /** The fixed advance cadence, or 0 for R3 A8's own random timer. */
        val slideshowMs: Int,
        /** The tile is drawn one size bigger while this lasts ([app.tileshell.tiles.TileGrowth]). */
        val grow: Boolean,
    )

    /**
     * [frameSet]: a main photo has been chosen. [slideshow]: the slideshow option is on. [photos]: how
     * many photos the feed actually found.
     *
     * **The picture-frame rule, and why it outranks the slideshow.** "it does not flip when that is set"
     * is unconditional, and a slideshow IS flipping, so a main photo turns the slideshow off for as long
     * as it is set rather than the two settings fighting over the tile. That also makes the setting
     * reversible in the obvious way: remove the main photo and whatever the slideshow switch says takes
     * over again, with nothing else to put back. A picture frame does not grow either — growth is for
     * "when its playing the slide show", and a frame is the opposite of something happening.
     *
     * A slideshow needs at least two photos: with one there is nothing to advance to, so the tile is a
     * still picture that would grow and stay grown for no visible reason.
     */
    fun plan(frameSet: Boolean, slideshow: Boolean, photos: Int): Plan = when {
        frameSet -> Plan(frame = true, faces = 0, slideshowMs = 0, grow = false)
        slideshow && photos >= 2 -> Plan(
            frame = false,
            faces = photos.coerceAtMost(MAX_SLIDESHOW_PHOTOS),
            slideshowMs = TileTiming.SLIDESHOW_MS,
            grow = true,
        )
        else -> Plan(frame = false, faces = photos.coerceAtMost(MAX_PHOTOS), slideshowMs = 0, grow = false)
    }

    /** How many photos to read for a given set of settings: a frame needs none of them. */
    fun photosToRead(frameSet: Boolean, slideshow: Boolean): Int = when {
        frameSet -> 0
        slideshow -> MAX_SLIDESHOW_PHOTOS
        else -> MAX_PHOTOS
    }
}
