package app.tileshell.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Motion Photo container (phase 17 build task 6d): one file, a JPEG with a trailing MP4 and the XMP that says so.
 * Proven on byte arrays: what the writer makes the reader reads back, and what is not a Motion Photo reads as none.
 */
class MotionPhotoTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun segment(marker: Int, payload: ByteArray) = bytes(0xFF, marker, (payload.size + 2) ushr 8, (payload.size + 2) and 0xFF) + payload

    /** SOI, JFIF APP0, an EXIF APP1, a quantisation table, SOS with scan bytes, EOI. */
    private val jfif = segment(0xE0, "JFIF\u0000".toByteArray() + ByteArray(9))
    private val exif = segment(0xE1, "Exif\u0000\u0000".toByteArray() + ByteArray(20) { 7 })
    private val dqt = segment(0xDB, ByteArray(65) { 3 })
    private val scan = segment(0xDA, ByteArray(10)) + ByteArray(200) { (it % 251).toByte() } + bytes(0xFF, 0xD9)
    private val jpeg = bytes(0xFF, 0xD8) + jfif + exif + dqt + scan

    /** An `ftyp` box and some `mdat`. */
    private val mp4 = bytes(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(12) + bytes(0, 0, 1, 0) + "mdat".toByteArray() + ByteArray(248) { (it * 3).toByte() }

    private fun String.count(needle: String) = split(needle).size - 1

    @Test fun `what the writer makes the reader reads back`() {
        val file = MotionPhoto.write(jpeg, mp4, 933_000L)
        val info = MotionPhoto.read(file)
        assertNotNull(info)
        assertEquals(mp4.size.toLong(), info!!.videoLength)
        assertEquals((file.size - mp4.size).toLong(), info.videoOffset)
        assertEquals(933_000L, info.presentationTimestampUs)
        assertEquals("video/mp4", info.videoMime)
        assertArrayEquals(mp4, MotionPhoto.clip(file))
    }

    @Test fun `the file is the jpeg with one xmp segment after exif and then the mp4`() {
        val file = MotionPhoto.write(jpeg, mp4, 0L)
        // The MP4 is the file's tail: `ftyp` four bytes into it (E7 greps the tail for it).
        assertArrayEquals(mp4, file.copyOfRange(file.size - mp4.size, file.size))
        assertEquals("ftyp", String(file, file.size - mp4.size + 4, 4, Charsets.US_ASCII))
        // The JPEG part still starts with SOI, JFIF and EXIF untouched, and still ends with its scan and EOI.
        val head = bytes(0xFF, 0xD8) + jfif + exif
        assertArrayEquals(head, file.copyOfRange(0, head.size))
        assertEquals(0xFF.toByte(), file[head.size]); assertEquals(0xE1.toByte(), file[head.size + 1])
        assertEquals("http://ns.adobe.com/xap/1.0/", String(file, head.size + 4, 28, Charsets.US_ASCII))
        val jpegPart = file.copyOfRange(0, file.size - mp4.size)
        assertArrayEquals(dqt + scan, jpegPart.copyOfRange(jpegPart.size - dqt.size - scan.size, jpegPart.size))
        // Only the XMP segment was added.
        val text = String(jpegPart, Charsets.ISO_8859_1)
        assertEquals(1, text.count("http://ns.adobe.com/xap/1.0/"))
        assertEquals(jpeg.size + 4 + 29 + MotionPhoto.xmp(mp4.size.toLong(), 0L).toByteArray().size, jpegPart.size)
    }

    @Test fun `the xmp says motion photo 1, the timestamp, and a directory whose video length is the mp4's size`() {
        val xmp = MotionPhoto.xmp(4321L, 1_000_000L)
        assertTrue(xmp.contains("xmlns:Camera=\"http://ns.google.com/photos/1.0/camera/\""))
        assertTrue(xmp.contains("Camera:MotionPhoto=\"1\""))
        assertTrue(xmp.contains("Camera:MotionPhotoPresentationTimestampUs=\"1000000\""))
        assertTrue(xmp.contains("<Container:Directory>"))
        assertTrue(xmp.contains("Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"0\""))
        assertTrue(xmp.contains("Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"4321\""))
    }

    @Test fun `a jpeg that already carries xmp ends up with exactly one xmp segment`() {
        val oldXmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>old</x:xmpmeta>".toByteArray())
        val withXmp = bytes(0xFF, 0xD8) + jfif + exif + oldXmp + dqt + scan
        val file = MotionPhoto.write(withXmp, mp4, 5L)
        val text = String(file, Charsets.ISO_8859_1)
        assertEquals(1, text.count("http://ns.adobe.com/xap/1.0/"))
        assertEquals(0, text.count("<x:xmpmeta>old"))
        assertEquals(mp4.size.toLong(), MotionPhoto.read(file)!!.videoLength)
    }

    @Test fun `a plain jpeg, a jpeg with other xmp and other bytes are not motion photos`() {
        assertNull(MotionPhoto.read(jpeg))
        assertNull(MotionPhoto.read(jpeg + mp4))                                  // an MP4 on the end with no XMP saying so
        val other = bytes(0xFF, 0xD8) + segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta/>".toByteArray()) + scan
        assertNull(MotionPhoto.read(other))
        assertNull(MotionPhoto.read(mp4))
        assertNull(MotionPhoto.read(ByteArray(0)))
        assertNull(MotionPhoto.clip(jpeg))
    }

    @Test fun `xmp that points at bytes that are not an mp4 is refused`() {
        val file = MotionPhoto.write(jpeg, mp4, 0L)
        // The clip cut short: the stated length no longer lands on `ftyp`.
        assertNull(MotionPhoto.read(file.copyOfRange(0, file.size - 10)))
        // A length longer than the file.
        val lying = segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000" + MotionPhoto.xmp(999_999L, 0L)).toByteArray())
        assertNull(MotionPhoto.read(bytes(0xFF, 0xD8) + lying + scan + mp4))
    }

    @Test fun `the older micro video form is read from its offset from the end`() {
        val xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:Description xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\" " +
            "GCamera:MicroVideo=\"1\" GCamera:MicroVideoVersion=\"1\" GCamera:MicroVideoOffset=\"${mp4.size}\" GCamera:MicroVideoPresentationTimestampUs=\"77\"/></x:xmpmeta>"
        val file = bytes(0xFF, 0xD8) + segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000$xmp").toByteArray()) + scan + mp4
        val info = MotionPhoto.read(file)!!
        assertEquals((file.size - mp4.size).toLong(), info.videoOffset)
        assertEquals(77L, info.presentationTimestampUs)
    }

    @Test fun `a directory with an item after the video places the video before it`() {
        val gainMap = ByteArray(40) { 9 }
        val xmp = MotionPhoto.xmp(mp4.size.toLong(), 0L).replace(
            "</rdf:Seq>",
            "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"GainMap\" Item:Length=\"40\" Item:Padding=\"0\"/></rdf:li></rdf:Seq>",
        )
        val file = bytes(0xFF, 0xD8) + segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000$xmp").toByteArray()) + scan + mp4 + gainMap
        val info = MotionPhoto.read(file)!!
        assertEquals((file.size - gainMap.size - mp4.size).toLong(), info.videoOffset)
        assertArrayEquals(mp4, MotionPhoto.clip(file))
    }

    @Test fun `the writer refuses what is not a jpeg or not an mp4`() {
        assertTrue(runCatching { MotionPhoto.write(mp4, mp4, 0L) }.isFailure)
        assertTrue(runCatching { MotionPhoto.write(jpeg, jpeg, 0L) }.isFailure)
    }

    // ---- the reader on a file's head and size (Photos and the probe page: the file is never read whole) ----------------

    /** [file] as a reader that holds only its head sees it; [asked] collects what was asked of the rest. */
    private fun headRead(file: ByteArray, headBytes: Int = MotionPhoto.HEAD_BYTES, asked: MutableList<Pair<Long, Int>> = mutableListOf()): MotionPhoto.Info? =
        MotionPhoto.read(file.copyOf(minOf(file.size, headBytes)), file.size.toLong()) { offset, count ->
            asked += offset to count
            if (offset < 0 || offset + count > file.size) null else file.copyOfRange(offset.toInt(), offset.toInt() + count)
        }

    @Test fun `the head and the size give the same answer as the whole file`() {
        val file = MotionPhoto.write(jpeg, mp4, 933_000L)
        assertEquals(MotionPhoto.read(file), headRead(file))
        // A long picture: the clip is megabytes past the head that was read.
        val longScan = segment(0xDA, ByteArray(10)) + ByteArray(3 * 1024 * 1024) { (it % 251).toByte() } + bytes(0xFF, 0xD9)
        val big = MotionPhoto.write(bytes(0xFF, 0xD8) + jfif + exif + dqt + longScan, mp4, 1L)
        assertTrue(big.size > 10 * MotionPhoto.HEAD_BYTES)
        val info = headRead(big)!!
        assertEquals((big.size - mp4.size).toLong(), info.videoOffset)
        assertEquals(mp4.size.toLong(), info.videoLength)
        assertEquals(MotionPhoto.read(big), info)
    }

    @Test fun `beyond the head only one box header at the clip's place is asked for`() {
        val file = MotionPhoto.write(jpeg, mp4, 0L)
        val asked = mutableListOf<Pair<Long, Int>>()
        assertNotNull(headRead(file, asked = asked))
        assertEquals(listOf((file.size - mp4.size).toLong() to 8), asked)
        assertEquals(8, MotionPhoto.CLIP_PROBE_BYTES)
        assertEquals(256 * 1024, MotionPhoto.HEAD_BYTES)
        // A plain still asks for nothing past its head.
        asked.clear()
        assertNull(headRead(jpeg, asked = asked))
        assertTrue(asked.isEmpty())
    }

    @Test fun `xmp past the head, a head cut inside the xmp and a rest that cannot be read are not motion photos`() {
        val file = MotionPhoto.write(jpeg, mp4, 0L)
        assertNull(headRead(file, headBytes = 40))                 // the head ends before the XMP segment does
        assertNull(MotionPhoto.read(file, file.size.toLong()) { _, _ -> null })
        assertNull(MotionPhoto.read(file, file.size.toLong()) { _, _ -> ByteArray(3) })
        assertNull(MotionPhoto.read(file, 0L) { _, _ -> mp4 })
        assertNull(MotionPhoto.read(file, -1L) { _, _ -> mp4 })
    }

    @Test fun `stated lengths and paddings that leave the file are refused before any read`() {
        fun withItem(length: String, padding: String, after: String = "") = bytes(0xFF, 0xD8) + segment(
            0xE1,
            ("http://ns.adobe.com/xap/1.0/\u0000" + MotionPhoto.xmp(mp4.size.toLong(), 0L)
                .replace("Item:Length=\"${mp4.size}\" Item:Padding=\"0\"", "Item:Length=\"$length\" Item:Padding=\"$padding\"")
                .replace("</rdf:Seq>", "$after</rdf:Seq>")).toByteArray(),
        ) + scan + mp4
        fun item(length: String, padding: String) =
            "<rdf:li rdf:parseType=\"Resource\"><Container:Item Item:Mime=\"image/jpeg\" Item:Semantic=\"GainMap\" Item:Length=\"$length\" Item:Padding=\"$padding\"/></rdf:li>"
        assertNotNull(MotionPhoto.read(withItem(mp4.size.toString(), "0")))
        for (length in listOf("0", "-1", "-${mp4.size}", "99999999", "9223372036854775807", "-9223372036854775808")) {
            val asked = mutableListOf<Pair<Long, Int>>()
            assertNull("length $length", headRead(withItem(length, "0"), asked = asked))
            assertTrue("length $length read $asked", asked.isEmpty())
        }
        // An item after the clip whose numbers would move the clip's start out of the file, or wrap the sum.
        for ((length, padding) in listOf("-500" to "0", "0" to "-500", "9223372036854775807" to "0", "10" to "9223372036854775807", "99999999" to "0")) {
            val asked = mutableListOf<Pair<Long, Int>>()
            assertNull("later item $length/$padding", headRead(withItem(mp4.size.toString(), "0", item(length, padding)), asked = asked))
            assertTrue(asked.isEmpty())
        }
    }

    @Test fun `every answer of the head reader lies inside the file`() {
        val file = MotionPhoto.write(jpeg, mp4, 0L)
        // The same head against other file sizes, as if the file had been cut or grown since: wherever an answer comes
        // back (the rest is said to be an MP4 everywhere), it is inside the size asked about.
        for (size in listOf(1L, 8L, mp4.size.toLong() - 1, mp4.size.toLong(), mp4.size.toLong() + 1, file.size.toLong(), file.size + 5_000L, Long.MAX_VALUE)) {
            val info = MotionPhoto.read(file, size) { _, _ -> mp4.copyOf(8) } ?: continue
            assertTrue("size $size: $info", info.videoOffset >= 0 && info.videoLength > 0 && info.videoLength <= size - info.videoOffset)
        }
    }

    @Test fun `the older micro video form's offset from the end is bounded by the file too`() {
        fun micro(offset: String) = bytes(0xFF, 0xD8) + segment(
            0xE1,
            ("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta><rdf:Description GCamera:MicroVideo=\"1\" GCamera:MicroVideoOffset=\"$offset\"/></x:xmpmeta>").toByteArray(),
        ) + scan + mp4
        assertNotNull(headRead(micro(mp4.size.toString())))
        for (offset in listOf("0", "-1", "99999999", "9223372036854775807", "-9223372036854775808")) assertNull("offset $offset", headRead(micro(offset)))
    }

    @Test fun `the marker is read from the xmp segment only`() {
        assertEquals(MotionPhoto.Marker("MotionPhoto", "1"), MotionPhoto.marker(MotionPhoto.write(jpeg, mp4, 0L)))
        val zero = bytes(0xFF, 0xD8) + segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta><rdf:Description GCamera:MotionPhoto = \"0\"/></x:xmpmeta>").toByteArray()) + scan
        assertEquals(MotionPhoto.Marker("MotionPhoto", "0"), MotionPhoto.marker(zero))
        val micro = bytes(0xFF, 0xD8) + segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta><rdf:Description GCamera:MicroVideo=\"1\"/></x:xmpmeta>").toByteArray()) + scan
        assertEquals(MotionPhoto.Marker("MicroVideo", "1"), MotionPhoto.marker(micro))
        assertNull(MotionPhoto.marker(jpeg))
        // The words in a picture's other bytes (a comment, the scan) are not a marker.
        assertNull(MotionPhoto.marker(bytes(0xFF, 0xD8) + segment(0xFE, "Camera:MotionPhoto=\"1\"".toByteArray()) + scan))
        assertNull(MotionPhoto.marker("Camera:MotionPhoto=\"1\"".toByteArray()))
        assertNull(MotionPhoto.marker(ByteArray(0)))
    }
}
