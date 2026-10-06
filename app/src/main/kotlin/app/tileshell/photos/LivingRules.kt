package app.tileshell.photos

/**
 * Living Images in Photos (phase 17 build task 6d, Decisions T17-2): the rules, free of Android types so every one is
 * unit-tested (`LivingRulesTest`). A Living Image is a still whose file is a Motion Photo — `media/MotionPhoto`'s reader
 * says so from the file's first bytes — and Photos shows a glyph on it and plays its clip while the photo is held.
 *
 * TRUST: the clip's offset and length come out of a file ANOTHER APP may have written, and the launcher's process plays
 * from them. [clip] is the one gate between those numbers and a read: nothing is played, and no byte is read for
 * playing, outside the range it returns; [window] keeps every read of the player inside that range.
 */
object LivingRules {
    /**
     * The longest clip Photos plays: 64 MB. The shell's own clip is one second (about 1 MB); a phone camera's Motion
     * Photo runs to a few seconds and a few megabytes. A stated length past this is not a clip the hold plays.
     */
    const val MAX_CLIP_BYTES = 64L * 1024 * 1024

    /** The shortest thing that can be a clip: one box header (`size` + `ftyp`). */
    const val MIN_CLIP_BYTES = 8L

    /**
     * What a file's answer is remembered by: the row, its modified time and its size. A file that is edited or replaced
     * under the same row changes one of the last two, so it is read again; a row that is only scrolled past is not.
     */
    data class Key(val id: Long, val dateModifiedS: Long, val size: Long)

    fun key(item: MediaEntry): Key = Key(item.id, item.dateModifiedS, item.size)

    /**
     * Whether a row's file is worth opening for the check at all: a Motion Photo is a JPEG (the reader's form), so a
     * video, a PNG, a HEIC or a GIF is never read. An unknown type (a URI with no row and no stated type) is read.
     */
    fun mayBeLiving(video: Boolean, mime: String?): Boolean =
        !video && (mime.isNullOrEmpty() || mime.equals("image/jpeg", ignoreCase = true) || mime.equals("image/jpg", ignoreCase = true) || mime == "image/*" || mime == "*/*")

    /** Where a clip lies in its file: [length] bytes from [offset]. Made only by [clip]. */
    data class Clip(val offset: Long, val length: Long)

    /** [clip]'s answer: the range, or why there is none ([why] is what the stop line's error form carries). */
    data class Bounded(val clip: Clip?, val why: String?)

    /**
     * The clip a file of [fileSize] bytes may be played from, given the [offset] and [length] its XMP states. Refused,
     * with the reason, unless ALL hold:
     *  - the file has a size (`fileSize > 0`);
     *  - the clip starts after the file's first byte (`offset > 0`: a Motion Photo's clip follows its JPEG);
     *  - the length is at least a box header and at most [MAX_CLIP_BYTES];
     *  - the clip ends at or before the file's last byte — written as `length <= fileSize - offset`, which cannot
     *    overflow whatever the two stated numbers are.
     */
    fun clip(offset: Long, length: Long, fileSize: Long): Bounded = when {
        fileSize <= 0 -> Bounded(null, "no file size")
        offset <= 0 || offset >= fileSize -> Bounded(null, "clip offset outside the file")
        length < MIN_CLIP_BYTES -> Bounded(null, "clip too short")
        length > MAX_CLIP_BYTES -> Bounded(null, "clip over ${MAX_CLIP_BYTES / (1024 * 1024)} MB")
        length > fileSize - offset -> Bounded(null, "clip runs past the file's end")
        else -> Bounded(Clip(offset, length), null)
    }

    /** A read of the player's: [count] bytes from [start], both in the FILE's coordinates. */
    data class Window(val start: Long, val count: Long)

    /**
     * The file range for a read the player asks of [clip]: [position] bytes into the clip, [requested] bytes long
     * (negative: to the clip's end). Null when the position is outside the clip; the count is cut to what the clip
     * still holds, so `start + count` is never past the clip's last byte.
     */
    fun window(clip: Clip, position: Long, requested: Long): Window? {
        if (position < 0 || position > clip.length) return null
        val left = clip.length - position
        return Window(clip.offset + position, if (requested < 0) left else minOf(requested, left))
    }

    /** Why a clip stopped. */
    enum class Stop(val text: String) { RELEASED("released"), ENDED("ended"), LEFT("left") }

    fun playLine(id: String, clipBytes: Long): String = "living $id: play $clipBytes bytes"

    fun stopLine(id: String, reason: Stop): String = "living $id: stop (${reason.text})"

    /** The stop line of a clip that could not be played; [why] is a code or a rule's reason, never a path or a URI. */
    fun errorLine(id: String, why: String): String = "living $id: stop (error ${why.replace('\n', ' ').take(80)})"

    /**
     * The clip's picture laid over the still's box of [boxW] × [boxH]: the size at which a video of [videoW] × [videoH]
     * covers the box with its own proportions kept (what does not fit is cut by the box, the way the still's own
     * thumbnail is centre-cropped). The box itself while the video's size is not yet known.
     */
    fun cover(boxW: Float, boxH: Float, videoW: Int, videoH: Int): Pair<Float, Float> {
        if (videoW <= 0 || videoH <= 0 || boxW <= 0f || boxH <= 0f) return boxW to boxH
        val scale = maxOf(boxW / videoW, boxH / videoH)
        return videoW * scale to videoH * scale
    }
}
