package app.tileshell.feeds

/**
 * The People tile's rules (phase 16 build task 7), pure so the JVM tests pin them: R3 A9's timings, which photo an
 * event brings in, and the decision to skip a photo that cannot be drawn.
 */
object PeopleTileRules {
    /** R3 A9 (S5, 14393, camera 24 fps): a bubble slides out to the left in ≈ 8 frames. */
    const val OUT_MS = 333

    /** R3 A9: the new bubble slides in from the right and settles in ≈ 14 frames. */
    const val IN_MS = 583

    /** R3 A9: the whole event lasts 45 ± 1 frames. */
    const val EVENT_MS = 1880

    /** R3 A9: the event repeats every 7.7 ± 0.2 s, start to start. */
    const val PERIOD_MS = 7700

    /**
     * How long the tile holds no bubble between the two slides. The event is timed from the out's start to the in's
     * settle (E11), so the in starts [EVENT_MS] − [IN_MS] after the out does and the gap between the slides is what
     * is left of the event: 964 ms. (A9's own note reads "≈ 6-frame pause", 250 ms — with its 333 ms and 583 ms that
     * makes a 1.17-s event, not its 1.88 s; the two cannot both hold, and the event's length is what E11 asserts.)
     */
    const val PAUSE_MS = EVENT_MS - OUT_MS - IN_MS

    /** When the in-slide starts, after the event's t0. */
    const val IN_START_MS = OUT_MS + PAUSE_MS

    /** The most photos the tile holds decoded at once. */
    const val MAX_PHOTOS = 24

    /** The side, in px, a photo is decoded to about: a bubble on the largest tile. */
    const val TARGET_PX = 320

    /**
     * Why a contact's photo is left off the tile, or null when it is drawn (T16-19; the line is `[people] tile: photo
     * <lookup> skipped: <why>`). The Contacts provider decodes a photo when it is stored, so no device fixture can
     * hold one that fails here — this rule is what the JVM test proves.
     */
    fun skipReason(opened: Boolean, width: Int, height: Int, decoded: Boolean): String? = when {
        !opened -> "unreadable"
        width <= 0 || height <= 0 -> "not an image"
        !decoded -> "decode failed"
        else -> null
    }

    /**
     * The photo the next event brings in: any of the [count] but the one on show, chosen by [random] (an index below
     * its argument). With one photo the same bubble leaves and comes back; with none there is no event.
     */
    fun next(current: Int, count: Int, random: (Int) -> Int): Int = when {
        count <= 0 -> -1
        count == 1 -> 0
        current !in 0 until count -> random(count).coerceIn(0, count - 1)
        else -> (current + 1 + random(count - 1).coerceIn(0, count - 2)) % count
    }

    /** How long to wait for the next event: one period after the last one began; a moment when that has passed. */
    fun waitMs(nowMs: Long, lastEventMs: Long?): Long {
        if (lastEventMs == null) return FIRST_EVENT_MS
        val due = lastEventMs + PERIOD_MS - nowMs
        return if (due <= 0) FIRST_EVENT_MS else due
    }

    /** The first event after the tile comes on screen: soon enough to be seen, not the instant Start appears. */
    const val FIRST_EVENT_MS = 1500L
}
