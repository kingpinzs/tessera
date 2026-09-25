package app.tileshell.recorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import app.tileshell.diag.Diagnostics
import java.io.FileOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The microphone to a kill-safe ADTS stream (T15-27): `AudioRecord` PCM, 16-bit mono at 44.1 kHz, into a
 * `MediaCodec` AAC-LC encoder at 64 kbps, and every encoded access unit written to [out] behind its own ADTS
 * header the moment it arrives. [out] is a plain FileOutputStream — each frame is one write() straight to the
 * descriptor, with no buffer in this process for a kill to lose.
 *
 * It runs on its own thread. [clock] decides what counts: while the take is paused the AudioRecord is stopped
 * (the microphone is let go of, so a call or another app can have it) and nothing reaches the encoder, which is
 * why a pause leaves no gap and no silence in the file.
 */
class AacCapture(
    private val out: FileOutputStream,
    private val clock: TakeClock,
    private val onLevel: (level: Float, elapsedMs: Long) -> Unit,
    private val onFailed: (why: String) -> Unit,
) {
    /** A Java monitor: the loop waits on it while paused, and wait / notifyAll are its, not Any's. */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val lock = java.lang.Object()
    @Volatile private var running = false
    @Volatile private var audioPaused = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null

    /** Whole frames written to [out]. */
    @Volatile var frames: Long = 0
        private set

    /** The AudioRecord's session, which [android.media.AudioManager.AudioRecordingCallback] reports it by. */
    @Volatile var audioSessionId: Int = 0
        private set

    /** Opens the microphone and the encoder and starts the loop. @throws on a microphone or codec that will not open. */
    @SuppressLint("MissingPermission") // RecorderService checks RECORD_AUDIO before a take starts.
    fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(RecorderAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val ar = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RecorderAudio.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer * 4, READ_BYTES * 8))
            .build()
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            error("the microphone would not open (AudioRecord state ${ar.state})")
        }
        val format = MediaFormat.createAudioFormat(RecorderAudio.MIME, RecorderAudio.SAMPLE_RATE, RecorderAudio.CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, RecorderAudio.BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, READ_BYTES * 2)
        }
        val enc = try {
            MediaCodec.createEncoderByType(RecorderAudio.MIME).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        } catch (t: Throwable) {
            ar.release()
            throw t
        }
        record = ar
        codec = enc
        audioSessionId = ar.audioSessionId
        ar.startRecording()
        running = true
        thread = Thread({ loop(ar, enc) }, "recorder-capture").apply { start() }
        Diagnostics.add("recorder", "capture open: ${enc.name}, session ${ar.audioSessionId}")
    }

    /** Stop taking audio (a pause of any kind): the microphone is let go of until [resumeAudio]. */
    fun pauseAudio() {
        synchronized(lock) {
            if (audioPaused) return
            audioPaused = true
            runCatching { record?.stop() }
        }
    }

    fun resumeAudio() {
        synchronized(lock) {
            if (!audioPaused) return
            runCatching { record?.startRecording() }
                .onFailure { Diagnostics.add("recorder", "microphone would not restart: $it") }
            audioPaused = false
            lock.notifyAll()
        }
    }

    /** End the take: the loop stops reading, the encoder is drained to its end, and both are released. */
    fun stop() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        runCatching { record?.stop() }
        thread?.join(STOP_JOIN_MS)
        thread = null
    }

    private fun loop(ar: AudioRecord, enc: MediaCodec) {
        val pcm = ByteArray(READ_BYTES)
        val info = MediaCodec.BufferInfo()
        var fedSamples = 0L
        var lastLevelAt = 0L
        try {
            while (true) {
                synchronized(lock) {
                    while (running && audioPaused) lock.wait(PAUSE_WAIT_MS)
                }
                if (!running) break
                val n = ar.read(pcm, 0, pcm.size)
                if (n < 0) {
                    // A read error while paused is the stopped AudioRecord answering; anything else is real.
                    if (audioPaused || !running) continue
                    onFailed("the microphone stopped (read $n)")
                    break
                }
                if (n == 0) continue
                val samples = n / 2
                val counted = synchronized(clock) { clock.onSamples(samples) }
                if (!counted) continue
                val level = levelOf(pcm, n)
                feed(enc, info, pcm, n, fedSamples, eos = false)
                fedSamples += samples
                drain(enc, info, eos = false)
                val now = System.nanoTime() / 1_000_000L
                if (now - lastLevelAt >= LEVEL_EVERY_MS) {
                    lastLevelAt = now
                    onLevel(level, synchronized(clock) { clock.elapsedMs })
                }
            }
            feed(enc, info, ByteArray(0), 0, fedSamples, eos = true)
            drain(enc, info, eos = true)
        } catch (t: Throwable) {
            Diagnostics.add("recorder", "capture loop failed: $t")
            onFailed("the recording stopped: ${t.javaClass.simpleName}")
        } finally {
            runCatching { enc.stop() }
            runCatching { enc.release() }
            runCatching { ar.release() }
            runCatching { out.flush() }
            runCatching { out.fd.sync() }
        }
    }

    /** Queue [length] bytes of PCM (in as many input buffers as it takes); with [eos], the end of the stream after them. */
    private fun feed(enc: MediaCodec, info: MediaCodec.BufferInfo, pcm: ByteArray, length: Int, fedSamples: Long, eos: Boolean) {
        var offset = 0
        var samplesSoFar = fedSamples
        val deadline = System.nanoTime() + CODEC_DEADLINE_NS
        while (offset < length || eos) {
            val index = enc.dequeueInputBuffer(CODEC_WAIT_US)
            if (index < 0) {
                // No input buffer free: the encoder may be waiting for its output to be taken.
                drain(enc, info, eos = false)
                if (System.nanoTime() > deadline) error("the encoder took no input for ${CODEC_DEADLINE_NS / 1_000_000} ms")
                continue
            }
            val buf = enc.getInputBuffer(index) ?: continue
            buf.clear()
            val chunk = minOf(buf.remaining(), length - offset)
            buf.put(pcm, offset, chunk)
            val pts = samplesSoFar * 1_000_000L / RecorderAudio.SAMPLE_RATE
            val last = offset + chunk >= length
            enc.queueInputBuffer(index, 0, chunk, pts, if (eos && last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
            offset += chunk
            samplesSoFar += chunk / 2
            if (last) return
        }
    }

    private fun drain(enc: MediaCodec, info: MediaCodec.BufferInfo, eos: Boolean) {
        val deadline = System.nanoTime() + CODEC_DEADLINE_NS
        while (true) {
            val index = enc.dequeueOutputBuffer(info, if (eos) CODEC_WAIT_US else 0L)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!eos) return
                // The end never came: what is on disk is whole frames, which is all the muxer needs.
                if (System.nanoTime() > deadline) return
                continue
            }
            if (index < 0) continue // format / buffers changed: nothing to write
            val buf = enc.getOutputBuffer(index)
            val config = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (buf != null && info.size > 0 && !config) {
                val frame = ByteArray(Adts.HEADER_BYTES + info.size)
                System.arraycopy(Adts.header(info.size), 0, frame, 0, Adts.HEADER_BYTES)
                buf.position(info.offset)
                buf.get(frame, Adts.HEADER_BYTES, info.size)
                // One write per frame: whatever a kill interrupts, it is at most this last frame.
                out.write(frame)
                frames++
            }
            enc.releaseOutputBuffer(index, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
        }
    }

    /** Input level 0..1 from the chunk's RMS: −60 dBFS and below read 0, full scale reads 1 (the rings, 2.7). */
    private fun levelOf(pcm: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < length) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        val count = (length / 2).coerceAtLeast(1)
        val rms = sqrt(sum / count) / 32768.0
        if (rms <= 0.0) return 0f
        val db = 20.0 * log10(rms)
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        /** 1024 samples of 16-bit mono: one AAC frame's worth per read. */
        const val READ_BYTES = Adts.SAMPLES_PER_FRAME * 2
        const val CODEC_WAIT_US = 10_000L
        const val CODEC_DEADLINE_NS = 2_000_000_000L
        const val PAUSE_WAIT_MS = 250L
        const val LEVEL_EVERY_MS = 33L
        const val STOP_JOIN_MS = 5_000L
    }
}
