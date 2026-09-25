package app.tileshell.recorder

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The ADTS writer and the frame parser the recovery depends on (phase 15 T15-27, E18): a take on disk is a
 * run of self-describing frames, and a kill can only cut the last one short — which must be dropped, never
 * crash the reader, and never cost the frames before it.
 */
class AdtsTest {

    private fun payload(n: Int, seed: Int) = ByteArray(n) { ((it * 31 + seed) and 0xFF).toByte() }

    private fun stream(vararg payloads: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in payloads) {
            out.write(Adts.header(p.size))
            out.write(p)
        }
        return out.toByteArray()
    }

    private fun readAll(bytes: ByteArray): Pair<List<AdtsReader.Frame>, AdtsReader> {
        val reader = AdtsReader(ByteArrayInputStream(bytes))
        val frames = generateSequence { reader.next() }.toList()
        return frames to reader
    }

    @Test fun `the header states AAC-LC, 44_1 kHz, mono and the whole frame's length`() {
        val h = Adts.header(186)
        assertEquals(7, h.size)
        assertEquals(0xFF, h[0].toInt() and 0xFF)
        assertEquals(0xF1, h[1].toInt() and 0xFF)
        val parsed = Adts.parseHeader(h)!!
        assertEquals(2, parsed.objectType)
        assertEquals(44_100, parsed.sampleRate)
        assertEquals(1, parsed.channels)
        assertEquals(7, parsed.headerBytes)
        assertEquals(193, parsed.frameBytes)
        assertEquals(186, parsed.payloadBytes)
    }

    @Test fun `known header bytes for a 193-byte AAC-LC 44_1 kHz mono frame`() {
        // profile 1 (LC-1) << 6 | freq 4 << 2 | ch>>2 = 0x50; (ch&3)<<6 | len>>11 = 0x40;
        // len>>3 = 0x18; (len&7)<<5 | 0x1F = 0x3F; 0xFC.
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x40, 0x18, 0x3F, 0xFC.toByte()),
            Adts.header(186),
        )
    }

    @Test fun `the header round-trips across the length range`() {
        for (n in listOf(0, 1, 255, 256, 2047, 2048, 4000, Adts.MAX_FRAME_BYTES - Adts.HEADER_BYTES)) {
            assertEquals(n, Adts.parseHeader(Adts.header(n))!!.payloadBytes)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a payload past 13 bits of frame length is refused`() {
        Adts.header(Adts.MAX_FRAME_BYTES)
    }

    @Test fun `the audio specific config for the take is 12 08`() {
        assertArrayEquals(byteArrayOf(0x12, 0x08), Adts.audioSpecificConfig())
        // A 48 kHz stereo check of the bit packing: LC(2)<<3 | 3>>1 = 0x11; (3&1)<<7 | 2<<3 = 0x90.
        assertArrayEquals(byteArrayOf(0x11, 0x90.toByte()), Adts.audioSpecificConfig(48_000, 2))
    }

    @Test fun `bytes that are not a header parse as nothing`() {
        assertNull(Adts.parseHeader(ByteArray(7)))
        assertNull(Adts.parseHeader(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50)))
        // Layer bits set.
        assertNull(Adts.parseHeader(byteArrayOf(0xFF.toByte(), 0xF7.toByte(), 0x50, 0x40, 0x18, 0x3F, 0xFC.toByte())))
        // A frame length shorter than its own header.
        assertNull(Adts.parseHeader(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x40, 0x00, 0x1F, 0xFC.toByte())))
    }

    @Test fun `a whole stream reads back frame by frame`() {
        val p = listOf(payload(186, 1), payload(170, 2), payload(201, 3))
        val (frames, reader) = readAll(stream(*p.toTypedArray()))
        assertEquals(3, frames.size)
        p.forEachIndexed { i, bytes -> assertArrayEquals(bytes, frames[i].payload) }
        assertEquals(3L, reader.frames)
        assertEquals("end", reader.stoppedBecause)
    }

    @Test fun `a truncated last frame is dropped and the frames before it kept`() {
        val whole = stream(payload(186, 1), payload(170, 2), payload(201, 3))
        // Cut the file inside the third frame's payload, as a kill mid-write does.
        val cut = whole.copyOf(whole.size - 50)
        val (frames, reader) = readAll(cut)
        assertEquals(2, frames.size)
        assertEquals("truncated frame", reader.stoppedBecause)
    }

    @Test fun `a truncated last header is dropped`() {
        val whole = stream(payload(186, 1), payload(170, 2))
        val cut = whole + Adts.header(100).copyOf(4)
        val (frames, reader) = readAll(cut)
        assertEquals(2, frames.size)
        assertEquals("truncated header", reader.stoppedBecause)
    }

    @Test fun `an empty file and a lone partial header give no frames and no crash`() {
        assertEquals(0, readAll(ByteArray(0)).first.size)
        assertEquals(0, readAll(byteArrayOf(0xFF.toByte())).first.size)
    }

    @Test fun `garbage after the good frames ends the stream there`() {
        val bytes = stream(payload(186, 1)) + ByteArray(40) { 0x11 }
        val (frames, reader) = readAll(bytes)
        assertEquals(1, frames.size)
        assertEquals("not a frame", reader.stoppedBecause)
    }

    @Test fun `a header with a CRC is read past to its payload`() {
        val p = payload(50, 9)
        val h = Adts.header(p.size + 2).copyOf()
        h[1] = 0xF0.toByte() // protection_absent = 0: two CRC bytes follow the header
        val bytes = h + byteArrayOf(0x12, 0x34) + p
        val (frames, _) = readAll(bytes)
        assertEquals(1, frames.size)
        assertEquals(9, frames[0].header.headerBytes)
        assertArrayEquals(p, frames[0].payload)
    }

    @Test fun `a stream that reads in small pieces still parses whole frames`() {
        val bytes = stream(payload(186, 1), payload(170, 2))
        val trickle = object : InputStream() {
            var pos = 0
            override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= bytes.size) return -1
                b[off] = bytes[pos++]
                return 1
            }
        }
        val reader = AdtsReader(trickle)
        assertNotNull(reader.next())
        assertNotNull(reader.next())
        assertNull(reader.next())
    }

    @Test fun `frame timing is 1024 samples per frame`() {
        assertEquals(0L, Adts.presentationUs(0))
        assertEquals(23_219L, Adts.presentationUs(1))
        // 44.1 kHz: 215 frames are 4.99 s, 216 are 5.015 s.
        assertEquals(4_992L, Adts.durationMs(215))
        assertEquals(5_015L, Adts.durationMs(216))
    }
}
