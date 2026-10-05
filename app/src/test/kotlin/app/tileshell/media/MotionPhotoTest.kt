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
}
