package app.tileshell.diag

import app.tileshell.media.MotionPhoto
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe page's Motion Photo line. The trust review's C-L2 (the probe's read bound): the probe reads the head of a
 * picked picture, never the file; and the line is `media/MotionPhoto`'s reader's answer (phase 17 build task 6d).
 */
class ProbeMotionPhotoTest {
    /** A file of [size] bytes that is never held in memory: it counts what was asked of it. */
    private class Counting(private val size: Long, private val head: ByteArray = ByteArray(0)) : InputStream() {
        var served = 0L
        override fun read(): Int = if (served >= size) -1 else (if (served < head.size) head[served.toInt()].toInt() and 0xFF else 0).also { served++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= size) return -1
            val n = minOf(len.toLong(), size - served).toInt()
            for (k in 0 until n) b[off + k] = if (served + k < head.size) head[(served + k).toInt()] else 0
            served += n
            return n
        }
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun segment(marker: Int, payload: ByteArray) = bytes(0xFF, marker, (payload.size + 2) ushr 8, (payload.size + 2) and 0xFF) + payload
    private fun xmpSegment(xmp: String) = segment(0xE1, ("http://ns.adobe.com/xap/1.0/\u0000$xmp").toByteArray())
    private val scan = segment(0xDA, ByteArray(10)) + ByteArray(200) { (it % 251).toByte() } + bytes(0xFF, 0xD9)
    private val jpeg = bytes(0xFF, 0xD8) + segment(0xE0, "JFIF\u0000".toByteArray() + ByteArray(9)) + scan
    private val mp4 = bytes(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(12) + bytes(0, 0, 1, 0) + "mdat".toByteArray() + ByteArray(248)

    /** The line for [file] the way the page reads it: the head from a stream, the size, and bytes by position. */
    private fun line(file: ByteArray, asked: MutableList<Pair<Long, Int>> = mutableListOf()): String =
        ProbeMotionPhoto.describe(ProbeMotionPhoto.readHead(file.inputStream()), file.size.toLong()) { offset, count ->
            asked += offset to count
            if (offset < 0 || offset + count > file.size) null else file.copyOfRange(offset.toInt(), offset.toInt() + count)
        }

    @Test
    fun `a 200 MB picked file is read to the head's bound and no further`() {
        val picture = MotionPhoto.write(jpeg, mp4, 0L)
        val size = 200L * 1024 * 1024
        val file = Counting(size, picture.copyOf(picture.size - mp4.size))
        val head = ProbeMotionPhoto.readHead(file)
        assertTrue("read ${file.served} bytes", file.served <= 256L * 1024)
        assertEquals(ProbeMotionPhoto.HEAD_BYTES, head.size)
        // The clip is the file's last bytes, 200 MB in: one box header there is all that is asked for besides the head.
        val asked = mutableListOf<Pair<Long, Int>>()
        val line = ProbeMotionPhoto.describe(head, size) { offset, count -> asked += offset to count; mp4.copyOf(count) }
        assertEquals("MotionPhoto=1 offset=${size - mp4.size} length=${mp4.size} timestampUs=0", line)
        assertEquals(listOf(size - mp4.size to 8), asked)
    }

    @Test
    fun `the bound is what the marker's reader looks at - a quarter of a megabyte`() {
        assertEquals(256 * 1024, ProbeMotionPhoto.HEAD_BYTES)
        assertEquals(MotionPhoto.HEAD_BYTES, ProbeMotionPhoto.HEAD_BYTES)
        // A marker past the bound is outside what describe reads, so nothing is gained by reading more: a picture whose
        // first segments fill the head, the XMP after them.
        val filler = (0 until 5).fold(ByteArray(0)) { acc, _ -> acc + segment(0xE2, ByteArray(65_000)) }
        val late = bytes(0xFF, 0xD8) + filler + xmpSegment(MotionPhoto.xmp(mp4.size.toLong(), 0L)) + scan + mp4
        assertTrue(filler.size > ProbeMotionPhoto.HEAD_BYTES)
        assertEquals("no marker", line(late))
        // describe itself looks at no more than the bound, whatever it is handed.
        assertEquals("no marker", ProbeMotionPhoto.describe(late, late.size.toLong()) { _, _ -> mp4.copyOf(8) })
        // (The whole file, read whole, is a Motion Photo: only the bound hides it.)
        assertEquals((late.size - mp4.size).toLong(), MotionPhoto.read(late)?.videoOffset)
    }

    @Test
    fun `a living image's line is the marker, the clip's offset and length, and the timestamp`() {
        val file = MotionPhoto.write(jpeg, mp4, 933_000L)
        assertEquals(file.size, ProbeMotionPhoto.readHead(file.inputStream()).size)
        assertEquals("MotionPhoto=1 offset=${file.size - mp4.size} length=${mp4.size} timestampUs=933000", line(file))
    }

    @Test
    fun `a plain still, bytes that are no jpeg and an empty file say no marker`() {
        assertEquals("no marker", line(jpeg))
        assertEquals("no marker", line(jpeg + mp4))
        assertEquals("no marker", line("""Camera:MotionPhoto="1"""".toByteArray()))
        assertEquals("no marker", line(ByteArray(0)))
        val asked = mutableListOf<Pair<Long, Int>>()
        line(jpeg, asked)
        assertTrue("a plain still is read no further than its head", asked.isEmpty())
    }

    @Test
    fun `a marker that is not 1 is shown as written, with no clip`() {
        val zero = bytes(0xFF, 0xD8) + xmpSegment("""<x:xmpmeta><rdf:Description GCamera:MotionPhoto = "0"/></x:xmpmeta>""") + scan
        assertEquals("MotionPhoto=0", line(zero))
    }

    @Test
    fun `a marker of 1 with no clip the reader accepts says so`() {
        // No directory at all.
        val bare = bytes(0xFF, 0xD8) + xmpSegment("""<x:xmpmeta><rdf:Description Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1"/></x:xmpmeta>""") + scan + mp4
        assertEquals("MotionPhoto=1 clip=none", line(bare))
        // A directory whose length is longer than the file, and one that points at bytes that are not an MP4.
        val lying = bytes(0xFF, 0xD8) + xmpSegment(MotionPhoto.xmp(99_999_999L, 0L)) + scan + mp4
        assertEquals("MotionPhoto=1 clip=none", line(lying))
        val notMp4 = bytes(0xFF, 0xD8) + xmpSegment(MotionPhoto.xmp(mp4.size.toLong(), 0L)) + scan + ByteArray(mp4.size) { 5 }
        assertEquals("MotionPhoto=1 clip=none", line(notMp4))
    }

    @Test
    fun `the timestamp is left out when the file gives none, and the older form is named as written`() {
        val noStamp = bytes(0xFF, 0xD8) + xmpSegment(MotionPhoto.xmp(mp4.size.toLong(), 0L).replace(" Camera:MotionPhotoPresentationTimestampUs=\"0\"", "")) + scan + mp4
        assertEquals("MotionPhoto=1 offset=${noStamp.size - mp4.size} length=${mp4.size}", line(noStamp))
        val micro = bytes(0xFF, 0xD8) + xmpSegment("""<x:xmpmeta><rdf:Description GCamera:MicroVideo="1" GCamera:MicroVideoOffset="${mp4.size}"/></x:xmpmeta>""") + scan + mp4
        assertEquals("MicroVideo=1 offset=${micro.size - mp4.size} length=${mp4.size}", line(micro))
    }
}
