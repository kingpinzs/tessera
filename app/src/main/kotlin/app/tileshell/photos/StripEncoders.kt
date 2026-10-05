package app.tileshell.photos

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The saved copy's EXIF (r3 D7): the picture is written upright, so Orientation is 1, and the original's capture time
 * is carried over (DateTimeOriginal with its offset and sub-seconds where the original has them). A TIFF block, big
 * endian; a JPEG holds it in APP1 after "Exif\0\0", a PNG in an eXIf chunk. Free of Android types.
 */
object ExifBlock {
    private class Entry(val tag: Int, val type: Int, val count: Int, val value: ByteArray)

    private fun ascii(tag: Int, text: String): Entry {
        val bytes = text.toByteArray(Charsets.US_ASCII) + 0
        return Entry(tag, 2, bytes.size, bytes)
    }

    private fun short(tag: Int, v: Int) = Entry(tag, 3, 1, byteArrayOf((v shr 8).toByte(), v.toByte(), 0, 0))
    private fun long(tag: Int, v: Int) = Entry(tag, 4, 1, byteArrayOf((v ushr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte()))

    /** [dateTimeOriginal] is EXIF's own form, "yyyy:MM:dd HH:mm:ss"; [offset] "+hh:mm"; all three copied as they were. */
    fun build(dateTimeOriginal: String?, offset: String?, subSec: String?): ByteArray {
        val exif = buildList {
            if (dateTimeOriginal != null) add(ascii(0x9003, dateTimeOriginal))
            if (dateTimeOriginal != null && offset != null) add(ascii(0x9011, offset))
            if (dateTimeOriginal != null && subSec != null) add(ascii(0x9291, subSec))
        }
        val ifd0Count = 1 + (if (dateTimeOriginal != null) 1 else 0) + (if (exif.isNotEmpty()) 1 else 0)
        val ifd0Size = 2 + 12 * ifd0Count + 4
        val ifd0DataAt = 8 + ifd0Size
        val ifd0Data = ByteArrayOutputStream()
        val out = ByteArrayOutputStream()
        fun u16(o: ByteArrayOutputStream, v: Int) { o.write(v shr 8); o.write(v) }
        fun u32(o: ByteArrayOutputStream, v: Int) { o.write(v ushr 24); o.write(v shr 16); o.write(v shr 8); o.write(v) }
        fun writeIfd(o: ByteArrayOutputStream, entries: List<Entry>, dataAt: Int, data: ByteArrayOutputStream) {
            u16(o, entries.size)
            for (e in entries.sortedBy { it.tag }) {
                u16(o, e.tag); u16(o, e.type); u32(o, e.count)
                if (e.value.size <= 4) { o.write(e.value); repeat(4 - e.value.size) { o.write(0) } }
                else {
                    u32(o, dataAt + data.size())
                    data.write(e.value)
                    if (e.value.size % 2 == 1) data.write(0)
                }
            }
            u32(o, 0)
        }
        // The Exif IFD comes after IFD0 and IFD0's own data; IFD0's data length is known before it is written.
        val ifd0Entries = mutableListOf(short(0x0112, 1))
        if (dateTimeOriginal != null) ifd0Entries += ascii(0x0132, dateTimeOriginal)
        val dateLen = if (dateTimeOriginal != null) (dateTimeOriginal.length + 1).let { it + it % 2 } else 0
        val exifAt = ifd0DataAt + dateLen
        if (exif.isNotEmpty()) ifd0Entries += long(0x8769, exifAt)
        out.write(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8))
        writeIfd(out, ifd0Entries, ifd0DataAt, ifd0Data)
        out.write(ifd0Data.toByteArray())
        if (exif.isNotEmpty()) {
            val exifData = ByteArrayOutputStream()
            val exifSize = 2 + 12 * exif.size + 4
            writeIfd(out, exif, exifAt + exifSize, exifData)
            out.write(exifData.toByteArray())
        }
        return out.toByteArray()
    }
}

/** What the tiled save writes a picture through: rows of ARGB pixels, top to bottom, then [finish]. */
interface StripSink {
    /** [rows] rows of the picture's width, row-major in [argb] from index 0. */
    fun writeRows(argb: IntArray, rows: Int)
    fun finish()
}

/**
 * A PNG written a strip at a time (r3 D8: the saved copy is never held whole in memory): 8-bit RGB, or RGBA when
 * [alpha]; filter 0 on every row, one zlib stream cut into IDAT chunks; [exif] as an eXIf chunk before the pixels.
 */
class PngStripWriter(private val out: OutputStream, private val width: Int, height: Int, private val alpha: Boolean, exif: ByteArray?) : StripSink {
    private val deflater = Deflater(6)
    private val buffer = ByteArray(64 * 1024)
    private val bpp = if (alpha) 4 else 3
    private val row = ByteArray(1 + width * bpp)

    init {
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val header = ByteArrayOutputStream()
        for (v in intArrayOf(width, height)) { header.write(v ushr 24); header.write(v shr 16); header.write(v shr 8); header.write(v) }
        header.write(8); header.write(if (alpha) 6 else 2); header.write(0); header.write(0); header.write(0)
        chunk("IHDR", header.toByteArray(), header.size())
        if (exif != null) chunk("eXIf", exif, exif.size)
    }

    private fun chunk(type: String, data: ByteArray, length: Int) {
        val tag = type.toByteArray(Charsets.US_ASCII)
        out.write(length ushr 24); out.write(length shr 16); out.write(length shr 8); out.write(length)
        out.write(tag); out.write(data, 0, length)
        val crc = CRC32().apply { update(tag); update(data, 0, length) }.value.toInt()
        out.write(crc ushr 24); out.write(crc shr 16); out.write(crc shr 8); out.write(crc)
    }

    override fun writeRows(argb: IntArray, rows: Int) {
        for (y in 0 until rows) {
            var o = 1
            row[0] = 0
            val base = y * width
            for (x in 0 until width) {
                val p = argb[base + x]
                row[o++] = (p shr 16).toByte(); row[o++] = (p shr 8).toByte(); row[o++] = p.toByte()
                if (alpha) row[o++] = (p ushr 24).toByte()
            }
            deflater.setInput(row)
            while (!deflater.needsInput()) {
                val n = deflater.deflate(buffer)
                if (n > 0) chunk("IDAT", buffer, n)
            }
        }
    }

    override fun finish() {
        deflater.finish()
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            if (n > 0) chunk("IDAT", buffer, n)
        }
        deflater.end()
        chunk("IEND", ByteArray(0), 0)
        out.flush()
    }
}

/**
 * A baseline JPEG written a strip at a time (r3 D8): YCbCr 4:2:0, the standard tables scaled to [quality] the way
 * libjpeg scales them (the saved copy is quality 95 — r3 D7), JFIF then [exif] in APP1. Android's own encoder takes a
 * whole Bitmap, which a 200-megapixel picture cannot be; this one holds sixteen rows.
 */
class JpegStripEncoder(private val out: OutputStream, private val width: Int, private val height: Int, quality: Int, exif: ByteArray?) : StripSink {
    private val qLuma = scaled(LUMA_Q, quality)
    private val qChroma = scaled(CHROMA_Q, quality)
    private val dcLuma = Huffman(DC_LUMA_BITS, DC_VALS)
    private val dcChroma = Huffman(DC_CHROMA_BITS, DC_VALS)
    private val acLuma = Huffman(AC_LUMA_BITS, AC_LUMA_VALS)
    private val acChroma = Huffman(AC_CHROMA_BITS, AC_CHROMA_VALS)
    private val band = IntArray(width * 16)
    private var bandRows = 0
    private var rowsSeen = 0
    private var bitBuffer = 0
    private var bitCount = 0
    private var lastY = 0
    private var lastCb = 0
    private var lastCr = 0
    private val block = DoubleArray(64)
    private val temp = DoubleArray(64)
    private val yBlocks = Array(4) { DoubleArray(64) }
    private val cb = DoubleArray(64)
    private val cr = DoubleArray(64)
    private val bytes = ByteArrayOutputStream(1 shl 16)

    init {
        marker(0xD8)
        segment(0xE0, byteArrayOf('J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0, 1, 1, 0, 0, 1, 0, 1, 0, 0))
        if (exif != null) segment(0xE1, byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0) + exif)
        segment(0xDB, byteArrayOf(0) + ByteArray(64) { qLuma[ZIGZAG[it]].toByte() })
        segment(0xDB, byteArrayOf(1) + ByteArray(64) { qChroma[ZIGZAG[it]].toByte() })
        segment(0xC0, byteArrayOf(8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 3, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1))
        segment(0xC4, byteArrayOf(0x00) + DC_LUMA_BITS.toBytes() + DC_VALS.toBytes())
        segment(0xC4, byteArrayOf(0x10) + AC_LUMA_BITS.toBytes() + AC_LUMA_VALS.toBytes())
        segment(0xC4, byteArrayOf(0x01) + DC_CHROMA_BITS.toBytes() + DC_VALS.toBytes())
        segment(0xC4, byteArrayOf(0x11) + AC_CHROMA_BITS.toBytes() + AC_CHROMA_VALS.toBytes())
        segment(0xDA, byteArrayOf(3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0))
    }

    private fun IntArray.toBytes() = ByteArray(size) { this[it].toByte() }

    private fun marker(m: Int) { out.write(0xFF); out.write(m) }

    private fun segment(m: Int, payload: ByteArray) {
        require(payload.size + 2 <= 0xFFFF) { "segment too long" }
        marker(m)
        out.write((payload.size + 2) shr 8); out.write(payload.size + 2)
        out.write(payload)
    }

    override fun writeRows(argb: IntArray, rows: Int) {
        var done = 0
        while (done < rows) {
            val take = minOf(16 - bandRows, rows - done)
            System.arraycopy(argb, done * width, band, bandRows * width, take * width)
            bandRows += take
            done += take
            rowsSeen += take
            if (bandRows == 16) encodeBand()
        }
    }

    override fun finish() {
        check(rowsSeen == height) { "wrote $rowsSeen of $height rows" }
        if (bandRows > 0) {
            // The last band's missing rows repeat its last row, as the picture's right edge repeats its last column.
            for (r in bandRows until 16) System.arraycopy(band, (bandRows - 1) * width, band, r * width, width)
            encodeBand()
        }
        if (bitCount > 0) put((1 shl (8 - bitCount)) - 1, 8 - bitCount)
        out.write(bytes.toByteArray())
        marker(0xD9)
        out.flush()
    }

    private fun encodeBand() {
        var mx = 0
        while (mx < width) {
            for (i in 0 until 64) { cb[i] = 0.0; cr[i] = 0.0 }
            for (by in 0 until 2) for (bx in 0 until 2) {
                val yb = yBlocks[by * 2 + bx]
                for (y in 0 until 8) {
                    val rowAt = (by * 8 + y) * width
                    for (x in 0 until 8) {
                        val px = minOf(mx + bx * 8 + x, width - 1)
                        val p = band[rowAt + px]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        yb[y * 8 + x] = 0.299 * r + 0.587 * g + 0.114 * b - 128.0
                        val ci = (by * 4 + y / 2) * 8 + (bx * 4 + x / 2)
                        cb[ci] += (-0.168736 * r - 0.331264 * g + 0.5 * b) / 4.0
                        cr[ci] += (0.5 * r - 0.418688 * g - 0.081312 * b) / 4.0
                    }
                }
            }
            for (i in 0 until 4) lastY = encodeBlock(yBlocks[i], qLuma, dcLuma, acLuma, lastY)
            lastCb = encodeBlock(cb, qChroma, dcChroma, acChroma, lastCb)
            lastCr = encodeBlock(cr, qChroma, dcChroma, acChroma, lastCr)
            mx += 16
        }
        bandRows = 0
        if (bytes.size() > (1 shl 16)) { out.write(bytes.toByteArray()); bytes.reset() }
    }

    private fun encodeBlock(data: DoubleArray, q: IntArray, dc: Huffman, ac: Huffman, lastDc: Int): Int {
        // The forward DCT, rows then columns.
        for (v in 0 until 8) for (u in 0 until 8) {
            var s = 0.0
            for (x in 0 until 8) s += data[v * 8 + x] * COS[x][u]
            temp[v * 8 + u] = s * (if (u == 0) INV_SQRT2 else 1.0) / 2.0
        }
        for (u in 0 until 8) for (v in 0 until 8) {
            var s = 0.0
            for (y in 0 until 8) s += temp[y * 8 + u] * COS[y][v]
            block[v * 8 + u] = s * (if (v == 0) INV_SQRT2 else 1.0) / 2.0
        }
        val dcValue = (block[0] / q[0]).roundToInt()
        val diff = dcValue - lastDc
        val dcSize = bitsOf(diff)
        put(dc.code[dcSize], dc.length[dcSize])
        if (dcSize > 0) put(magnitude(diff, dcSize), dcSize)
        var run = 0
        for (i in 1 until 64) {
            val z = ZIGZAG[i]
            val v = (block[z] / q[z]).roundToInt()
            if (v == 0) { run++; continue }
            while (run > 15) { put(ac.code[0xF0], ac.length[0xF0]); run -= 16 }
            val size = bitsOf(v)
            val symbol = (run shl 4) or size
            put(ac.code[symbol], ac.length[symbol])
            put(magnitude(v, size), size)
            run = 0
        }
        if (run > 0) put(ac.code[0x00], ac.length[0x00])
        return dcValue
    }

    private fun bitsOf(v: Int): Int {
        var a = if (v < 0) -v else v
        var n = 0
        while (a != 0) { n++; a = a shr 1 }
        return n
    }

    private fun magnitude(v: Int, size: Int): Int = if (v >= 0) v else (v + (1 shl size) - 1)

    private fun put(code: Int, length: Int) {
        var n = length
        while (n > 0) {
            val room = 8 - bitCount
            val take = minOf(room, n)
            bitBuffer = (bitBuffer shl take) or ((code shr (n - take)) and ((1 shl take) - 1))
            bitCount += take
            n -= take
            if (bitCount == 8) {
                bytes.write(bitBuffer)
                if (bitBuffer == 0xFF) bytes.write(0)
                bitBuffer = 0
                bitCount = 0
            }
        }
    }

    private class Huffman(bits: IntArray, vals: IntArray) {
        val code = IntArray(256)
        val length = IntArray(256)

        init {
            var c = 0
            var k = 0
            for (len in 1..16) {
                repeat(bits[len - 1]) {
                    code[vals[k]] = c
                    length[vals[k]] = len
                    c++
                    k++
                }
                c = c shl 1
            }
        }
    }

    private companion object {
        val INV_SQRT2 = 1.0 / Math.sqrt(2.0)
        val COS = Array(8) { x -> DoubleArray(8) { u -> cos((2 * x + 1) * u * Math.PI / 16.0) } }

        fun scaled(base: IntArray, quality: Int): IntArray {
            val q = quality.coerceIn(1, 100)
            val scale = if (q < 50) 5000 / q else 200 - q * 2
            return IntArray(64) { ((base[it] * scale + 50) / 100).coerceIn(1, 255) }
        }

        /** ZIGZAG[i] is the row-major index of the i-th coefficient in zigzag order. */
        val ZIGZAG = intArrayOf(
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
        )
        val LUMA_Q = intArrayOf(
            16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99,
        )
        val CHROMA_Q = intArrayOf(
            17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99, 99, 99, 99, 47, 66, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
        )
        val DC_LUMA_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
        val DC_CHROMA_BITS = intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)
        val DC_VALS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        val AC_LUMA_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d)
        val AC_LUMA_VALS = intArrayOf(
            0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
            0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
            0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
            0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
            0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
            0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
            0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa,
        )
        val AC_CHROMA_BITS = intArrayOf(0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77)
        val AC_CHROMA_VALS = intArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71, 0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
            0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0, 0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34, 0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
            0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
            0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
            0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
            0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
            0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa,
        )
    }
}

/**
 * An MP4's creation time (r3 D7: a trim keeps DATE_TAKEN). MediaStore reads a video's DATE_TAKEN from the movie
 * header when it scans the published file, and a freshly muxed file carries the time it was muxed — so the trim's
 * temporary file gets the original's time written into its `mvhd` before it is copied into the new row.
 */
object Mp4Dates {
    private const val EPOCH_1904_S = 2_082_844_800L

    /** Sets the movie header's creation and modification time to [epochMs]. False when the file has no `mvhd`. */
    fun setCreationTime(file: File, epochMs: Long): Boolean = RandomAccessFile(file, "rw").use { f ->
        val moov = findBox(f, 0L, f.length(), "moov") ?: return false
        val mvhd = findBox(f, moov.first, moov.second, "mvhd") ?: return false
        val seconds = epochMs / 1000L + EPOCH_1904_S
        f.seek(mvhd.first)
        val version = f.read()
        f.seek(mvhd.first + 4)
        if (version == 1) { f.writeLong(seconds); f.writeLong(seconds) } else { f.writeInt(seconds.toInt()); f.writeInt(seconds.toInt()) }
        true
    }

    /** The movie header's creation time, or null. */
    fun creationTime(file: File): Long? = RandomAccessFile(file, "r").use { f ->
        val moov = findBox(f, 0L, f.length(), "moov") ?: return null
        val mvhd = findBox(f, moov.first, moov.second, "mvhd") ?: return null
        f.seek(mvhd.first)
        val version = f.read()
        f.seek(mvhd.first + 4)
        val seconds = if (version == 1) f.readLong() else (f.readInt().toLong() and 0xFFFFFFFFL)
        (seconds - EPOCH_1904_S) * 1000L
    }

    /** The payload range [start, end) of the first box named [type] among the boxes in [from, to). */
    private fun findBox(f: RandomAccessFile, from: Long, to: Long, type: String): Pair<Long, Long>? {
        var at = from
        val name = ByteArray(4)
        while (at + 8 <= to) {
            f.seek(at)
            var size = f.readInt().toLong() and 0xFFFFFFFFL
            f.readFully(name)
            var header = 8L
            if (size == 1L) { size = f.readLong(); header = 16L } else if (size == 0L) size = to - at
            if (size < header || at + size > to) return null
            if (String(name, Charsets.US_ASCII) == type) return (at + header) to (at + size)
            at += size
        }
        return null
    }
}
