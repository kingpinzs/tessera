package app.tileshell.diag

/**
 * What the probe page says about a picked picture's Motion Photo marker (phase 17, r3 V15; P12). The lead's interim
 * reader of the XMP's marker only; after the Camera build is merged this delegates to `media/MotionPhoto`'s reader, the
 * one that also knows the clip's offset and length.
 */
object ProbeMotionPhoto {
    private val MARKER = Regex("""(?:GCamera|Camera):MotionPhoto\s*=\s*"(\d+)"""")

    fun describe(bytes: ByteArray): String {
        // The XMP packet is Latin-1-safe text inside the JPEG's first segments: search the head of the file.
        val head = String(bytes, 0, minOf(bytes.size, 256 * 1024), Charsets.ISO_8859_1)
        val value = MARKER.find(head)?.groupValues?.get(1)
        return if (value == null) "no marker" else "MotionPhoto=$value"
    }
}
