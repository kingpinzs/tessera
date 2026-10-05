package app.tileshell.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.camera.core.ImageProxy
import java.io.File

/**
 * Living Images' clip (phase 17 build task 6d): the last second or so of the viewfinder, kept in memory as small
 * frames while "Capture living images" is on, and encoded into an MP4 when the shutter fires. The still and this clip
 * become ONE Motion Photo file (`media/MotionPhoto.kt`).
 *
 * Frames arrive from CameraX's ImageAnalysis (YUV 4:2:0, the analysis stream's small default size) and are held as
 * packed I420. Nothing is written anywhere until a capture asks for the clip.
 */
class LivingClip(private val windowNs: Long = 1_100_000_000L) {
    private class Frame(val timestampNs: Long, val i420: ByteArray)

    private val frames = ArrayDeque<Frame>()
    private var width = 0
    private var height = 0

    /** How many frames are held now. */
    val size: Int @Synchronized get() = frames.size

    @Synchronized
    fun clear() = frames.clear()

    /** Keeps [image] (it is not closed here) and drops what is older than the window. */
    fun add(image: ImageProxy) {
        val w = image.width
        val h = image.height
        if (w % 2 != 0 || h % 2 != 0 || image.planes.size < 3) return
        val out = ByteArray(w * h * 3 / 2)
        copyPlane(image.planes[0], w, h, out, 0)
        copyPlane(image.planes[1], w / 2, h / 2, out, w * h)
        copyPlane(image.planes[2], w / 2, h / 2, out, w * h + w * h / 4)
        synchronized(this) {
            if (w != width || h != height) { frames.clear(); width = w; height = h }
            val t = image.imageInfo.timestamp
            frames.addLast(Frame(t, out))
            while (frames.size > 1 && t - frames.first().timestampNs > windowNs) frames.removeFirst()
        }
    }

    private fun copyPlane(plane: ImageProxy.PlaneProxy, w: Int, h: Int, out: ByteArray, offset: Int) {
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val row = ByteArray(rowStride)
        for (y in 0 until h) {
            buffer.position(y * rowStride)
            val n = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, n)
            if (pixelStride == 1) {
                System.arraycopy(row, 0, out, offset + y * w, w)
            } else {
                var o = offset + y * w
                var i = 0
                for (x in 0 until w) { out[o++] = row[i]; i += pixelStride }
            }
        }
    }

    /** The encoded clip: the file, and the last frame's time in it (the still's moment). */
    class Encoded(val file: File, val presentationTimestampUs: Long, val frames: Int, val width: Int, val height: Int)

    /**
     * Encodes the held frames as H.264 in an MP4 at [into], turned by [rotationDegrees] with the container's
     * orientation hint. Returns null when fewer than two frames are held or the device has no encoder for the size.
     * Blocking: call it off the main thread.
     */
    fun encode(into: File, rotationDegrees: Int): Encoded? {
        val (list, w, h) = synchronized(this) { Triple(frames.toList(), width, height) }
        if (list.size < 2) return null
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, w * h * 6)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(into.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer.setOrientationHint(((rotationDegrees % 360) + 360) % 360)
            val t0 = list.first().timestampNs
            val info = MediaCodec.BufferInfo()
            var track = -1
            var fed = 0
            var done = false
            var lastPtsUs = 0L
            while (!done) {
                if (fed <= list.size) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        if (fed == list.size) {
                            codec.queueInputBuffer(index, 0, 0, lastPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val f = list[fed]
                            val image = codec.getInputImage(index) ?: return null
                            fill(image, f.i420, w, h)
                            lastPtsUs = (f.timestampNs - t0) / 1000
                            codec.queueInputBuffer(index, 0, w * h * 3 / 2, lastPtsUs, 0)
                        }
                        fed++
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    out >= 0 -> {
                        val data = codec.getOutputBuffer(out)
                        if (data != null && info.size > 0 && track >= 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            muxer.writeSampleData(track, data, info)
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                        codec.releaseOutputBuffer(out, false)
                    }
                }
            }
            muxer.stop()
            return Encoded(into, lastPtsUs, list.size, w, h)
        } catch (e: Exception) {
            into.delete()
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
        }
    }

    private fun fill(image: android.media.Image, i420: ByteArray, w: Int, h: Int) {
        val sizes = intArrayOf(w, h, w / 2, h / 2, w / 2, h / 2)
        val offsets = intArrayOf(0, w * h, w * h + w * h / 4)
        for (p in 0 until 3) {
            val plane = image.planes[p]
            val pw = sizes[p * 2]
            val ph = sizes[p * 2 + 1]
            val buffer = plane.buffer
            for (y in 0 until ph) {
                val src = offsets[p] + y * pw
                if (plane.pixelStride == 1) {
                    buffer.position(y * plane.rowStride)
                    buffer.put(i420, src, pw)
                } else {
                    var at = y * plane.rowStride
                    for (x in 0 until pw) { buffer.put(at, i420[src + x]); at += plane.pixelStride }
                }
            }
        }
    }
}
