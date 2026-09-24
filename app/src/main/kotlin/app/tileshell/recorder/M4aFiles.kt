package app.tileshell.recorder

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * The `.m4a` container, built at stop from the take's ADTS frames, and trim's real cut (phase 15 build task 7).
 *
 * `MediaRecorder`'s MP4 is not used because its index is written only at the end and a kill leaves it
 * unplayable (Decisions "Voice Recorder mechanics"); here the index is written from frames that are already
 * safe on disk, so the same code builds a take at stop and a take recovered after its process died.
 */
object M4aFiles {

    data class Result(val frames: Long, val durationMs: Long)

    /**
     * Mux the ADTS stream in [adts] into an MPEG-4 file on [out]. Frames are laid back to back at 1024 samples
     * each, which is exactly the take's time with its pauses left out. A truncated last frame is dropped by
     * [AdtsReader]. @return the frames written; 0 means the stream held no whole frame and nothing was written.
     */
    fun muxAdts(adts: File, out: FileDescriptor): Result {
        adts.inputStream().buffered().use { input ->
            val reader = AdtsReader(input)
            val first = reader.next() ?: return Result(0, 0)
            val h = first.header
            val format = MediaFormat.createAudioFormat(RecorderAudio.MIME, h.sampleRate, h.channels).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(Adts.audioSpecificConfig(h.sampleRate, h.channels, h.objectType)))
                setInteger(MediaFormat.KEY_AAC_PROFILE, h.objectType)
                setInteger(MediaFormat.KEY_BIT_RATE, RecorderAudio.BIT_RATE)
            }
            val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val track = muxer.addTrack(format)
                muxer.start()
                val info = MediaCodec.BufferInfo()
                var index = 0L
                var frame: AdtsReader.Frame? = first
                while (frame != null) {
                    info.set(0, frame.payload.size, Adts.presentationUs(index, h.sampleRate), MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    muxer.writeSampleData(track, ByteBuffer.wrap(frame.payload), info)
                    index++
                    frame = reader.next()
                }
                muxer.stop()
                return Result(index, Adts.durationMs(index, h.sampleRate))
            } finally {
                runCatching { muxer.release() }
            }
        }
    }

    /**
     * Trim's real cut (U3, E16): copy the audio samples of [source] from [startMs] to [endMs] into a new MPEG-4
     * file on [out], their times moved to start at zero. The source is only read. @return the samples copied.
     */
    fun cut(context: Context, source: Uri, startMs: Long, endMs: Long, out: FileDescriptor): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return Result(0, 0)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val capacity = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 64 * 1024
            val buffer = ByteBuffer.allocate(capacity.coerceAtLeast(8 * 1024))
            val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                val info = MediaCodec.BufferInfo()
                var firstUs = -1L
                var lastUs = 0L
                var copied = 0L
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val t = extractor.sampleTime
                    if (t >= endUs) break
                    if (t >= startUs) {
                        if (firstUs < 0) firstUs = t
                        info.set(0, size, t - firstUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                        muxer.writeSampleData(outTrack, buffer, info)
                        lastUs = t - firstUs
                        copied++
                    }
                    extractor.advance()
                }
                if (copied == 0L) {
                    // A muxer with no sample cannot stop cleanly; the caller discards the pending file.
                    return Result(0, 0)
                }
                muxer.stop()
                val frameUs = Adts.SAMPLES_PER_FRAME * 1_000_000L / RecorderAudio.SAMPLE_RATE
                return Result(copied, (lastUs + frameUs) / 1000L)
            } finally {
                runCatching { muxer.release() }
            }
        } finally {
            extractor.release()
        }
    }
}
