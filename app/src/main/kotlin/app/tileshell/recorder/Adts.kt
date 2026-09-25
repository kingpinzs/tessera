package app.tileshell.recorder

import java.io.EOFException
import java.io.InputStream

/**
 * AAC in ADTS: the take's kill-safe form on disk (phase 15 Decisions "Voice Recorder mechanics", T15-27).
 *
 * `MediaCodec` gives raw AAC access units; the recorder writes each one behind a 7-byte ADTS header the
 * moment it arrives. Every ADTS frame stands alone — it carries its own length and the stream's sample rate
 * and channel count — so a process killed mid-take leaves a file whose every whole frame is still readable,
 * and the only damage a kill can do is a last frame cut short, which [AdtsReader] drops. The `.m4a` container
 * (whose index is written only at the end, and which is exactly what a kill destroys in `MediaRecorder`'s
 * output) is built from these frames at stop, or at the next start after a death.
 *
 * Pure bytes and streams, so the writer and the parser the recovery depends on are proven on the host JVM.
 */
object Adts {
    /** A header with `protection_absent = 1`: no CRC, seven bytes. */
    const val HEADER_BYTES = 7

    /** A header with a CRC is two bytes longer; the reader accepts one, the writer never makes one. */
    const val HEADER_BYTES_WITH_CRC = 9

    /** MPEG-4 audio object type 2: AAC-LC (T15-27). */
    const val OBJECT_TYPE_LC = 2

    /** One AAC-LC access unit is 1024 PCM samples per channel. */
    const val SAMPLES_PER_FRAME = 1024

    /** ADTS can say at most 13 bits of frame length. */
    const val MAX_FRAME_BYTES = 0x1FFF

    /** The ISO/IEC 14496-3 sampling-frequency table; a header carries the index into it. */
    val SAMPLE_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun frequencyIndex(sampleRate: Int): Int {
        val i = SAMPLE_RATES.indexOf(sampleRate)
        require(i >= 0) { "no ADTS frequency index for $sampleRate Hz" }
        return i
    }

    /**
     * The 7-byte header for one frame carrying [payloadBytes] of AAC: sync word, MPEG-4, no CRC, the object
     * type, the sampling-frequency index and the channel configuration, the whole frame's length, buffer
     * fullness 0x7FF (variable bit rate) and one raw data block.
     */
    fun header(
        payloadBytes: Int,
        sampleRate: Int = RecorderAudio.SAMPLE_RATE,
        channels: Int = RecorderAudio.CHANNELS,
        objectType: Int = OBJECT_TYPE_LC,
    ): ByteArray {
        val frameBytes = payloadBytes + HEADER_BYTES
        require(payloadBytes >= 0 && frameBytes <= MAX_FRAME_BYTES) { "an ADTS frame cannot carry $payloadBytes bytes" }
        require(channels in 1..7) { "channel configuration $channels" }
        val freq = frequencyIndex(sampleRate)
        val profile = objectType - 1
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            ((profile shl 6) or (freq shl 2) or (channels shr 2)).toByte(),
            (((channels and 3) shl 6) or (frameBytes shr 11)).toByte(),
            ((frameBytes shr 3) and 0xFF).toByte(),
            (((frameBytes and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    /**
     * The two-byte AudioSpecificConfig (`csd-0`) the MP4 muxer needs for the same stream: object type (5
     * bits), frequency index (4), channel configuration (4), then three zero flags. AAC-LC, 44.1 kHz, mono is
     * `12 08`.
     */
    fun audioSpecificConfig(
        sampleRate: Int = RecorderAudio.SAMPLE_RATE,
        channels: Int = RecorderAudio.CHANNELS,
        objectType: Int = OBJECT_TYPE_LC,
    ): ByteArray {
        val freq = frequencyIndex(sampleRate)
        return byteArrayOf(
            ((objectType shl 3) or (freq shr 1)).toByte(),
            (((freq and 1) shl 7) or (channels shl 3)).toByte(),
        )
    }

    /** The stream parameters one header states. */
    data class Header(
        val objectType: Int,
        val sampleRate: Int,
        val channels: Int,
        val headerBytes: Int,
        val frameBytes: Int,
    ) {
        val payloadBytes: Int get() = frameBytes - headerBytes
    }

    /**
     * Reads the header at [offset] in [b] (which must hold at least [HEADER_BYTES] from there), or null when
     * those bytes are not a valid ADTS header — no sync word, a layer other than 0, a reserved frequency
     * index, or a frame length shorter than its own header.
     */
    fun parseHeader(b: ByteArray, offset: Int = 0): Header? {
        if (b.size - offset < HEADER_BYTES) return null
        val b0 = b[offset].toInt() and 0xFF
        val b1 = b[offset + 1].toInt() and 0xFF
        val b2 = b[offset + 2].toInt() and 0xFF
        val b3 = b[offset + 3].toInt() and 0xFF
        val b4 = b[offset + 4].toInt() and 0xFF
        val b5 = b[offset + 5].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) return null
        if (((b1 shr 1) and 3) != 0) return null
        val protectionAbsent = b1 and 1
        val objectType = ((b2 shr 6) and 3) + 1
        val freq = (b2 shr 2) and 0xF
        if (freq >= SAMPLE_RATES.size) return null
        val channels = ((b2 and 1) shl 2) or ((b3 shr 6) and 3)
        val frameBytes = ((b3 and 3) shl 11) or (b4 shl 3) or ((b5 shr 5) and 7)
        val headerBytes = if (protectionAbsent == 1) HEADER_BYTES else HEADER_BYTES_WITH_CRC
        if (frameBytes < headerBytes) return null
        return Header(objectType, SAMPLE_RATES[freq], channels, headerBytes, frameBytes)
    }

    /** How long [frames] AAC frames last at [sampleRate], in milliseconds. */
    fun durationMs(frames: Long, sampleRate: Int = RecorderAudio.SAMPLE_RATE): Long =
        frames * SAMPLES_PER_FRAME * 1000L / sampleRate

    /** The presentation time of frame [index] (0-based) in microseconds: frames are back to back, pauses included in none. */
    fun presentationUs(index: Long, sampleRate: Int = RecorderAudio.SAMPLE_RATE): Long =
        index * SAMPLES_PER_FRAME * 1_000_000L / sampleRate
}

/**
 * Reads ADTS frames one at a time from [input] — the recovery's and the muxer's view of a take on disk.
 *
 * [next] returns each whole frame's header and payload, and null at the end. The end is also wherever the
 * bytes stop being a whole valid frame: a header cut short, a payload cut short (the frame a kill interrupted
 * mid-write), or bytes that are not a header at all. Everything before that point is returned; nothing after
 * it is guessed at, and nothing here throws on a damaged stream.
 */
class AdtsReader(private val input: InputStream) {

    class Frame(val header: Adts.Header, val payload: ByteArray)

    /** How many whole frames have been returned. */
    var frames: Long = 0
        private set

    /** Why reading stopped: "end", "truncated header", "truncated frame" or "not a frame"; null while reading. */
    var stoppedBecause: String? = null
        private set

    private val headerBuf = ByteArray(Adts.HEADER_BYTES_WITH_CRC)

    fun next(): Frame? {
        if (stoppedBecause != null) return null
        val got = readFully(headerBuf, 0, Adts.HEADER_BYTES)
        if (got == 0) return stop("end")
        if (got < Adts.HEADER_BYTES) return stop("truncated header")
        val header = Adts.parseHeader(headerBuf) ?: return stop("not a frame")
        if (header.headerBytes > Adts.HEADER_BYTES) {
            // The CRC's two bytes: read past them; the payload is what the muxer wants.
            if (readFully(headerBuf, Adts.HEADER_BYTES, header.headerBytes - Adts.HEADER_BYTES) < header.headerBytes - Adts.HEADER_BYTES) {
                return stop("truncated header")
            }
        }
        val payload = ByteArray(header.payloadBytes)
        if (readFully(payload, 0, payload.size) < payload.size) return stop("truncated frame")
        frames++
        return Frame(header, payload)
    }

    private fun stop(why: String): Frame? {
        stoppedBecause = why
        return null
    }

    /** Reads up to [len] bytes, as many as the stream still has; never throws on a short stream. */
    private fun readFully(buf: ByteArray, off: Int, len: Int): Int {
        var total = 0
        while (total < len) {
            val n = try {
                input.read(buf, off + total, len - total)
            } catch (e: EOFException) {
                -1
            }
            if (n < 0) break
            total += n
        }
        return total
    }
}

/** The encoding T15-27 fixes: AAC-LC, mono, 44.1 kHz, 64 kbps (about 0.5 MB a minute). */
object RecorderAudio {
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 1
    const val BIT_RATE = 64_000
    const val MIME = "audio/mp4a-latm"

    /** What the published file is: MPEG-4 audio, `.m4a`, which the share intent names too (E16). */
    const val FILE_MIME = "audio/mp4"
}
