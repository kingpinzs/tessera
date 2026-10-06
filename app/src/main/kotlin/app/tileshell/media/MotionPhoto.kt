package app.tileshell.media

/**
 * The Motion Photo container (phase 17 build task 6d, Decisions T17-2 "Living Images"): ONE file — a JPEG with an MP4
 * appended after its last byte, and an XMP packet in the JPEG that says so (`Camera:MotionPhoto="1"`,
 * `Camera:MotionPhotoPresentationTimestampUs`, and a `Container:Directory` whose items give each part's length). It is
 * the form Android documents ("Motion Photo format 1.0") and Samsung Gallery and Google Photos read, so a Living Image
 * taken here plays there too. No sidecar file.
 *
 * Pure byte work, free of Android types: the Camera writes with [write], Photos and the probe page ask [read] whether a
 * picture is one and where its clip is — from the file's first bytes and its size, never the whole file. JVM-tested on
 * byte arrays (`MotionPhotoTest`).
 */
object MotionPhoto {
    const val NS_CAMERA = "http://ns.google.com/photos/1.0/camera/"
    const val NS_CONTAINER = "http://ns.google.com/photos/1.0/container/"
    const val NS_ITEM = "http://ns.google.com/photos/1.0/container/item/"

    /** The identifier every standard XMP APP1 segment starts with (the NUL included). */
    private val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    /** What [read] found: where the clip sits in the file and when, in the clip, the still was taken. */
    data class Info(
        /** The clip's first byte, from the file's start. */
        val videoOffset: Long,
        /** The clip's length in bytes; it runs to the file's end less any later item. */
        val videoLength: Long,
        /** `MotionPhotoPresentationTimestampUs`, or -1 when the file does not say. */
        val presentationTimestampUs: Long,
        /** The clip's MIME type as the directory names it. */
        val videoMime: String,
    )

    /** The XMP packet [write] embeds, for a clip of [videoLength] bytes. */
    fun xmp(videoLength: Long, presentationTimestampUs: Long): String =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Tessera Motion Photo\">" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\" xmlns:Camera=\"$NS_CAMERA\" xmlns:Container=\"$NS_CONTAINER\" xmlns:Item=\"$NS_ITEM\" " +
            "Camera:MotionPhoto=\"1\" Camera:MotionPhotoVersion=\"1\" Camera:MotionPhotoPresentationTimestampUs=\"$presentationTimestampUs\">" +
            "<Container:Directory><rdf:Seq>" +
            "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\" Item:Padding=\"0\"/></rdf:li>" +
            "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"$videoLength\" Item:Padding=\"0\"/></rdf:li>" +
            "</rdf:Seq></Container:Directory>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta>"

    /**
     * The Motion Photo made of [jpeg] and [mp4]: the JPEG with the XMP packet as an APP1 segment (after its JFIF and
     * EXIF segments; an XMP segment the JPEG already carried is replaced — the camera's own stills carry none), then
     * the MP4's bytes. Throws IllegalArgumentException when [jpeg] is not a JPEG, [mp4] is not an MP4 or the packet
     * would not fit one segment.
     */
    fun write(jpeg: ByteArray, mp4: ByteArray, presentationTimestampUs: Long): ByteArray {
        require(isJpeg(jpeg)) { "not a JPEG" }
        require(isMp4(mp4, 0)) { "not an MP4" }
        val packet = xmp(mp4.size.toLong(), presentationTimestampUs).toByteArray(Charsets.UTF_8)
        val payload = XMP_HEADER.size + packet.size
        require(payload + 2 <= 0xFFFF) { "the XMP packet is too long for one segment" }
        val segments = segments(jpeg)
        // After SOI and the leading APP0 (JFIF) / APP1 (EXIF) segments, where readers expect XMP.
        var insertAt = 2
        for (s in segments) {
            val leading = s.marker == 0xE0 || (s.marker == 0xE1 && !isXmp(jpeg, s))
            if (s.start == insertAt && leading) insertAt = s.end else break
        }
        val out = java.io.ByteArrayOutputStream(jpeg.size + payload + 4 + mp4.size)
        out.write(jpeg, 0, insertAt)
        out.write(0xFF); out.write(0xE1)
        out.write((payload + 2) ushr 8); out.write((payload + 2) and 0xFF)
        out.write(XMP_HEADER); out.write(packet)
        // The rest of the JPEG, less any XMP segment it already had.
        var at = insertAt
        for (s in segments) {
            if (s.start < insertAt || !(s.marker == 0xE1 && isXmp(jpeg, s))) continue
            out.write(jpeg, at, s.start - at)
            at = s.end
        }
        out.write(jpeg, at, jpeg.size - at)
        out.write(mp4)
        return out.toByteArray()
    }

    /**
     * How much of a file's start [read] needs: the XMP packet is one of the JPEG's first marker segments (each at most
     * 64 KB — JFIF, EXIF with its thumbnail, then XMP), so a quarter of a megabyte holds it. A reader that has only a
     * stream reads this much and no more; the clip at the file's end is never read to say where it is.
     */
    const val HEAD_BYTES = 256 * 1024

    /** How many bytes at the clip's stated place [read] asks for: one box header, to see `ftyp`. */
    const val CLIP_PROBE_BYTES = 8

    /** The marker a file's XMP carries: [name] is `MotionPhoto` or the older `MicroVideo`, [value] what it is set to. */
    data class Marker(val name: String, val value: String)

    /** The Motion Photo marker in [head] (a file's first bytes), or null when its XMP has none or it has no XMP. */
    fun marker(head: ByteArray): Marker? {
        val xmp = xmp(head) ?: return null
        value(xmp, "MotionPhoto")?.let { return Marker("MotionPhoto", it) }
        value(xmp, "MicroVideo")?.let { return Marker("MicroVideo", it) }
        return null
    }

    /**
     * Whether [file] is a Motion Photo, and where its clip is — null when it is not one (no XMP, `MotionPhoto` not 1,
     * no video item, or the bytes at the stated place are not an MP4). Reads the 1.0 container directory, and the
     * older `MicroVideo` form (`GCamera:MicroVideoOffset`, counted from the file's end) that earlier phones wrote.
     */
    fun read(file: ByteArray): Info? = read(file, file.size.toLong()) { offset, count ->
        if (offset < 0 || offset + count > file.size) null else file.copyOfRange(offset.toInt(), offset.toInt() + count)
    }

    /**
     * The same answer for a file that is not held in memory: [head] is its first bytes ([HEAD_BYTES] of them are
     * enough), [fileSize] its whole length, and [bytesAt] returns `count` bytes from `offset` (null when it cannot) —
     * it is asked once, for [CLIP_PROBE_BYTES] at the clip's stated place. The XMP's numbers are another writer's: a
     * length that is not positive, a negative padding, or a clip that would start before the file's first byte or run
     * past its last is "not a Motion Photo", and so the offset and length returned always lie inside `0 until fileSize`.
     */
    fun read(head: ByteArray, fileSize: Long, bytesAt: (offset: Long, count: Int) -> ByteArray?): Info? {
        if (fileSize <= 0) return null
        val xmp = xmp(head) ?: return null
        val timestamp = (value(xmp, "MotionPhotoPresentationTimestampUs") ?: value(xmp, "MicroVideoPresentationTimestampUs"))?.toLongOrNull() ?: -1L
        fun clipAt(offset: Long, length: Long, mime: String): Info? {
            if (length <= 0 || offset < 0 || length > fileSize - offset) return null
            val box = bytesAt(offset, CLIP_PROBE_BYTES) ?: return null
            return if (isMp4(box, 0)) Info(offset, length, timestamp, mime) else null
        }
        if (value(xmp, "MotionPhoto") == "1") {
            // Items in file order; the first is the primary image, each later one is appended after it.
            val items = Regex("<Container:Item\\b[^>]*>").findAll(xmp).map { it.value }.toList()
            var tail = 0L   // bytes of the items after the one in hand, counted from the file's end
            for (item in items.drop(1).reversed()) {
                val length = attribute(item, "Item:Length")?.toLongOrNull() ?: return null
                val padding = attribute(item, "Item:Padding")?.toLongOrNull() ?: 0L
                if (length < 0 || padding < 0 || length > fileSize - tail) return null
                if (attribute(item, "Item:Semantic") == "MotionPhoto") {
                    return clipAt(fileSize - tail - length, length, attribute(item, "Item:Mime") ?: "video/mp4")
                }
                if (padding > fileSize - tail - length) return null
                tail += length + padding
            }
            return null
        }
        if (value(xmp, "MicroVideo") == "1") {
            val fromEnd = value(xmp, "MicroVideoOffset")?.toLongOrNull() ?: return null
            if (fromEnd <= 0 || fromEnd > fileSize) return null
            return clipAt(fileSize - fromEnd, fromEnd, "video/mp4")
        }
        return null
    }

    /** The XMP packet's text in [head], or null when [head] is not a JPEG's start or carries no XMP segment. */
    private fun xmp(head: ByteArray): String? {
        if (!isJpeg(head)) return null
        return segments(head).firstOrNull { it.marker == 0xE1 && isXmp(head, it) }
            ?.let { String(head, it.start + 4 + XMP_HEADER.size, it.end - it.start - 4 - XMP_HEADER.size, Charsets.UTF_8) }
    }

    /** The clip's bytes, or null when [file] is not a Motion Photo. */
    fun clip(file: ByteArray): ByteArray? = read(file)?.let { file.copyOfRange(it.videoOffset.toInt(), (it.videoOffset + it.videoLength).toInt()) }

    // ---- JPEG and MP4 structure ------------------------------------------------------------------------------------

    private class Segment(val marker: Int, val start: Int, val end: Int)

    private fun isJpeg(b: ByteArray) = b.size >= 4 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xFF) == 0xD8

    /** An ISO base media file starts with a box whose type is `ftyp`. */
    private fun isMp4(b: ByteArray, at: Int) =
        at >= 0 && at + 8 <= b.size && b[at + 4] == 'f'.code.toByte() && b[at + 5] == 't'.code.toByte() && b[at + 6] == 'y'.code.toByte() && b[at + 7] == 'p'.code.toByte()

    /** The marker segments before the scan data (SOS): each `FF xx len …`, with [Segment.start] at its FF. */
    private fun segments(b: ByteArray): List<Segment> {
        val out = ArrayList<Segment>()
        var i = 2
        while (i + 4 <= b.size && (b[i].toInt() and 0xFF) == 0xFF) {
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break
            val length = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (length < 2 || i + 2 + length > b.size) break
            out += Segment(marker, i, i + 2 + length)
            i += 2 + length
        }
        return out
    }

    private fun isXmp(b: ByteArray, s: Segment): Boolean {
        if (s.end - s.start < 4 + XMP_HEADER.size) return false
        for (k in XMP_HEADER.indices) if (b[s.start + 4 + k] != XMP_HEADER[k]) return false
        return true
    }

    /** A property written as an attribute (`ns:Name="v"`) or as an element (`<ns:Name>v</ns:Name>`). */
    private fun value(xmp: String, name: String): String? =
        Regex("[A-Za-z]+:$name\\s*=\\s*[\"']([^\"']*)[\"']").find(xmp)?.groupValues?.get(1)
            ?: Regex("<[A-Za-z]+:$name>\\s*([^<]*?)\\s*</").find(xmp)?.groupValues?.get(1)

    private fun attribute(element: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*[\"']([^\"']*)[\"']").find(element)?.groupValues?.get(1)
}
