package app.tileshell.diag

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trust review's C-L2 (the probe's read bound): the probe reads the head of a picked picture, never the file. */
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

    private val xmp = """<x:xmpmeta><rdf:Description Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1"/></x:xmpmeta>""".toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `a 200 MB picked file is read to the head's bound and no further`() {
        val file = Counting(200L * 1024 * 1024, ByteArray(4096) + xmp)
        val head = ProbeMotionPhoto.readHead(file)
        assertTrue("read ${file.served} bytes", file.served <= 256L * 1024)
        assertEquals(ProbeMotionPhoto.HEAD_BYTES, head.size)
        assertEquals("MotionPhoto=1", ProbeMotionPhoto.describe(head))
    }

    @Test
    fun `the bound is what the marker's reader looks at - a quarter of a megabyte`() {
        assertEquals(256 * 1024, ProbeMotionPhoto.HEAD_BYTES)
        // A marker past the bound is outside what describe reads, so nothing is gained by reading more.
        val late = ByteArray(ProbeMotionPhoto.HEAD_BYTES) + xmp
        assertEquals("no marker", ProbeMotionPhoto.describe(late))
        assertEquals("no marker", ProbeMotionPhoto.describe(ProbeMotionPhoto.readHead(late.inputStream())))
    }

    @Test
    fun `a small file is read whole, and each form of the marker is found`() {
        val small = ByteArray(100) + xmp
        assertEquals(small.size, ProbeMotionPhoto.readHead(small.inputStream()).size)
        assertEquals("MotionPhoto=1", ProbeMotionPhoto.describe(small))
        assertEquals("MotionPhoto=0", ProbeMotionPhoto.describe("""GCamera:MotionPhoto = "0"""".toByteArray()))
        assertEquals("no marker", ProbeMotionPhoto.describe(ByteArray(0)))
    }
}
