package app.tileshell.camera

import android.content.Context
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import app.tileshell.diag.Diagnostics
import app.tileshell.media.AndroidMediaStorePort
import app.tileshell.media.MediaKind
import app.tileshell.media.MediaWrites
import app.tileshell.media.MotionPhoto
import app.tileshell.media.NewMedia
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where the Camera's own captures go: `DCIM/Camera/` and nowhere else, every row through the shell's ONE MediaStore
 * write layer (`media/MediaWrites.save`: pending → written → published → read back; r3 D7). This class never touches
 * a ContentResolver and never writes shared storage by path; it copies a capture out of the app's cache into the
 * stream the layer hands it. Each capture logs `[camera] saved <uri> <w>x<h>`, or `[camera] save failed: <why>`.
 */
class CameraSaver(context: Context) {
    private val cacheDir = File(context.cacheDir, "camera")
    val writes: MediaWrites<IntentSender> = app.tileshell.media.ShellMediaWrites.of(context)

    /** Start-up cleanup (build task 6 clause 7; Edge cases "a process killed mid-write"). */
    fun cleanUpPending() {
        val n = writes.cleanUpPending()
        if (n > 0) Diagnostics.add("camera", "cleaned $n pending row(s) left by an earlier run")
    }

    private fun stamp(now: Long) = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))

    private fun save(item: NewMedia, source: File): MediaWrites.Result =
        writes.save(item) { out -> source.inputStream().use { it.copyTo(out, 1 shl 16) } }

    /**
     * A still. With [clip] (Living Images, 6d) the still and the clip are written as ONE Motion Photo file. Always
     * deletes the cache files. Returns the row's URI, or null with the failure logged.
     */
    fun savePhoto(shot: PhotoShot, clip: LivingClip.Encoded? = null, prefix: String = "IMG"): String? {
        val now = System.currentTimeMillis()
        var source = shot.file
        var living = false
        try {
            if (clip != null) {
                val combined = runCatching { MotionPhoto.write(shot.file.readBytes(), clip.file.readBytes(), clip.presentationTimestampUs) }.getOrNull()
                if (combined != null) {
                    source = File(cacheDir, "motion_${now}.jpg").apply { writeBytes(combined) }
                    living = true
                } else {
                    Diagnostics.add("camera", "living image: the clip could not be joined, saved as a still")
                }
            }
            val name = (if (living) "MVIMG" else prefix) + "_${stamp(now)}.jpg"
            return when (val r = save(NewMedia(MediaKind.IMAGE, name, "image/jpeg", MediaWrites.CAMERA_PATH, now), source)) {
                is MediaWrites.Result.Saved -> {
                    Diagnostics.add("camera", "saved ${r.row.uri} ${shot.width}x${shot.height}" + if (living) " living image clip=${clip?.frames} frames" else "")
                    r.row.uri
                }
                is MediaWrites.Result.Failed -> { Diagnostics.add("camera", "save failed: ${r.why}"); null }
            }
        } finally {
            shot.file.delete()
            clip?.file?.delete()
            if (source != shot.file) source.delete()
        }
    }

    /**
     * A recording. A take that ended early ("storage full", the screen going off, a call) is still finalised and
     * saved when it holds anything playable; an empty or unreadable take is dropped. Never a pending row left: the
     * write layer abandons its own row when the copy fails.
     */
    fun saveVideo(take: VideoTake): String? {
        val now = System.currentTimeMillis()
        try {
            if (take.error != null) Diagnostics.add("camera", "recording ended: ${take.error}")
            val (w, h) = videoSize(take.file) ?: run {
                Diagnostics.add("camera", "save failed: the recording holds no video" + (take.error?.let { " ($it)" } ?: ""))
                return null
            }
            val name = (if (take.slowMotionFps != null) "SLOMO" else "VID") + "_${stamp(now)}.mp4"
            return when (val r = save(NewMedia(MediaKind.VIDEO, name, "video/mp4", MediaWrites.CAMERA_PATH, now), take.file)) {
                is MediaWrites.Result.Saved -> {
                    if (take.slowMotionFps != null) Diagnostics.add("camera", "slowmo ${take.slowMotionFps} fps captured, encoded at 30 -> ${r.row.uri}")
                    Diagnostics.add("camera", "saved ${r.row.uri} ${w}x$h")
                    r.row.uri
                }
                is MediaWrites.Result.Failed -> { Diagnostics.add("camera", "save failed: ${r.why}" + (take.error?.let { " ($it)" } ?: "")); null }
            }
        } finally {
            take.file.delete()
        }
    }

    companion object {
        /** The frame size of a video file as it plays (the rotation applied), or null when it holds no video track. */
        fun videoSize(file: File): Pair<Int, Int>? {
            if (!file.isFile || file.length() == 0L) return null
            val r = MediaMetadataRetriever()
            return try {
                r.setDataSource(file.path)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
                val rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) h to w else w to h
            } catch (e: Exception) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
    }
}
