package app.tileshell.diag

import app.tileshell.media.MotionPhoto

/**
 * What the probe page says about a picked picture's Motion Photo form (phase 17, r3 V15; P12): one line from
 * `media/MotionPhoto`'s reader — the one the Camera writes for and Photos plays from — so the page reports exactly what
 * Photos would act on: the XMP's marker, and where the clip is.
 */
object ProbeMotionPhoto {
    /**
     * How much of a picked picture the probe reads (C-L2): 256 KB, which is all the reader looks at ([MotionPhoto.HEAD_BYTES]).
     * The XMP packet that carries the marker is one of the JPEG's first segments (each at most 64 KB), so it lies well
     * inside; the clip at the file's end is not read to say where it is — only its first box header is, by position.
     * It was 64 MB of another app's file, read into the launcher's process.
     */
    const val HEAD_BYTES = MotionPhoto.HEAD_BYTES

    /** The head of a picked picture, and never more than [HEAD_BYTES] of it, however large the file is. */
    fun readHead(input: java.io.InputStream): ByteArray = input.readNBytes(HEAD_BYTES)

    /**
     * The line: `no marker` for a plain still; else the marker as the XMP writes it (`MotionPhoto=1`), and for a file
     * the reader takes as a Motion Photo ` offset=<the clip's first byte> length=<its bytes>` and, when the file gives
     * one, ` timestampUs=<the still's place in the clip>`. A marker that says 1 with no clip the reader accepts (the
     * directory is missing or wrong, or the bytes there are not an MP4) says so: ` clip=none`.
     *
     * [head] is the file's first bytes (at most [HEAD_BYTES] are looked at), [fileSize] its length as the provider gives
     * it, and [bytesAt] returns `count` bytes at `offset`, or null — the reader asks it for one box header.
     */
    fun describe(head: ByteArray, fileSize: Long, bytesAt: (offset: Long, count: Int) -> ByteArray?): String {
        val bounded = if (head.size > HEAD_BYTES) head.copyOf(HEAD_BYTES) else head
        val marker = MotionPhoto.marker(bounded) ?: return "no marker"
        val info = MotionPhoto.read(bounded, fileSize, bytesAt)
        return buildString {
            append(marker.name).append('=').append(marker.value)
            when {
                info != null -> {
                    append(" offset=").append(info.videoOffset).append(" length=").append(info.videoLength)
                    if (info.presentationTimestampUs >= 0) append(" timestampUs=").append(info.presentationTimestampUs)
                }
                marker.value == "1" -> append(" clip=none")
            }
        }
    }
}
