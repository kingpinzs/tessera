package app.tileshell.diag

/**
 * What the probe page says about a picked picture's Motion Photo marker (phase 17, r3 V15; P12). The lead's interim
 * reader of the XMP's marker only; after the Camera build is merged this delegates to `media/MotionPhoto`'s reader, the
 * one that also knows the clip's offset and length.
 */
object ProbeMotionPhoto {
    private val MARKER = Regex("""(?:GCamera|Camera):MotionPhoto\s*=\s*"(\d+)"""")

    /**
     * How much of a picked picture the probe reads (C-L2): 256 KB, which is all [describe] looks at. The XMP packet that
     * carries the marker is one of the JPEG's first segments (each at most 64 KB), so it lies well inside; the clip at
     * the file's end is not needed to say whether the marker is there. It was 64 MB of another app's file, read into
     * the launcher's process.
     */
    const val HEAD_BYTES = 256 * 1024

    /** The head of a picked picture, and never more than [HEAD_BYTES] of it, however large the file is. */
    fun readHead(input: java.io.InputStream): ByteArray = input.readNBytes(HEAD_BYTES)

    fun describe(bytes: ByteArray): String {
        // The XMP packet is Latin-1-safe text inside the JPEG's first segments: search the head of the file.
        val head = String(bytes, 0, minOf(bytes.size, HEAD_BYTES), Charsets.ISO_8859_1)
        val value = MARKER.find(head)?.groupValues?.get(1)
        return if (value == null) "no marker" else "MotionPhoto=$value"
    }
}
