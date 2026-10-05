package app.tileshell.camera

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.media.AndroidUriAccess
import app.tileshell.media.CaptureOutputGuard
import app.tileshell.media.CaptureRequestPort
import app.tileshell.media.CaptureRequestRule
import app.tileshell.ui.setShellAppContent
import kotlinx.coroutines.MainScope
import java.io.File
import java.util.concurrent.Executors

/** A capture waiting for Accept or Retake: still in the app's cache, nowhere else. */
sealed interface CaptureReview {
    val preview: Bitmap?
    class Image(val shot: PhotoShot, override val preview: Bitmap?) : CaptureReview
    class Video(val take: VideoTake, override val preview: Bitmap?) : CaptureReview
}

/**
 * The capture answer (phase 17; Q5 A kept by Q-17-2 (b)): `IMAGE_CAPTURE` and `VIDEO_CAPTURE` from an app that names
 * the shell or targets API 29 or lower. A helper in standard launch mode in `:camera`, so it runs in its caller's task
 * and is not a catalog entry. Exported and TRUST-TOUCHING — on qa/phase-03/exported-allowlist.txt and under the
 * adversarial review.
 *
 * What it may write is decided ONCE, in [onCreate], before anything is shown, by `CaptureOutputGuard.decide` over the
 * real intent — and by nothing in this class:
 *  - Refused → RESULT_CANCELED, the guard's line, finish; no camera opens and nothing is written.
 *  - Accepted → the capture is written ONLY through `MediaWrites.writeCaptureOutput` with the guard's token (which
 *    nothing but the guard can make), after the user accepts it; no copy goes to DCIM; no location in its EXIF.
 *  - NoOutput → a still returns a small thumbnail Bitmap in `data` and writes NO file; a video is saved as a
 *    `DCIM/Camera/` row through `MediaWrites.save` and its URI returned with a read grant.
 * Back at any point is RESULT_CANCELED; the capture lives only in the app's cache until then, so there is no pending
 * row to leave. This class never opens the caller's URI itself and never writes by path.
 */
class CaptureActivity : ComponentActivity() {
    private var ring: android.content.ServiceConnection? = null
    private var engine: CameraEngine? = null
    private lateinit var saver: CameraSaver
    private val state = ViewfinderState()
    private val scope = MainScope()
    private val worker = Executors.newSingleThreadExecutor()
    private var interruptions: CameraInterruptions? = null
    private var decision: CaptureOutputGuard.Decision = CaptureOutputGuard.Decision.Refused(CaptureOutputGuard.LINE_NO_GRANT)
    private var video = false
    private var review: CaptureReview? by mutableStateOf(null)
    private var finishing by mutableStateOf(false)

    /** T17-4: a capture for another app carries no location — a [CallerCaptureSink] cannot keep one. */
    private val sink = object : CallerCaptureSink() {
        override fun photo(shot: PhotoShot, clip: LivingClip.Encoded?, panorama: Boolean) {
            clip?.file?.delete()
            worker.execute {
                val preview = thumbnailOf(shot.file, shot.rotationDegrees, edge = 1080)
                runOnUiThread { review = CaptureReview.Image(shot, preview) }
            }
        }

        override fun video(take: VideoTake) {
            worker.execute {
                val frame = runCatching {
                    val r = MediaMetadataRetriever()
                    try { r.setDataSource(take.file.path); r.getFrameAtTime(0) } finally { r.release() }
                }.getOrNull()
                runOnUiThread {
                    if (CameraSaver.videoSize(take.file) == null) {
                        take.file.delete()
                        failed(take.error ?: "nothing was recorded")
                    } else {
                        if (take.error != null) state.say(scope, if (take.error == "storage full") "Storage full" else "Recording stopped")
                        review = CaptureReview.Video(take, frame)
                    }
                }
            }
        }

        override fun failed(why: String) {
            Diagnostics.add("camera", "capture failed: $why")
            state.say(scope, why)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("camera", "CaptureActivity created")
        ring = RemoteRings.hold(this, CameraDumpService::class.java)
        CameraDumpService.linger(this)
        setResult(Activity.RESULT_CANCELED)
        val action = intent?.action
        if (action != MediaStore.ACTION_IMAGE_CAPTURE && action != MediaStore.ACTION_VIDEO_CAPTURE) {
            Diagnostics.add("camera", "capture request: unknown action, cancelled")
            finish()
            return
        }
        video = action == MediaStore.ACTION_VIDEO_CAPTURE
        val outcome = decide(intent)
        decision = outcome.decision
        val d = decision
        Diagnostics.add("camera", "capture request ${if (video) "video" else "image"} from ${callingPackage ?: "no caller"}: " + when (d) {
            is CaptureOutputGuard.Decision.Accepted -> "output accepted"
            CaptureOutputGuard.Decision.NoOutput -> "no output"
            is CaptureOutputGuard.Decision.Refused -> "refused"
        })
        if (d is CaptureOutputGuard.Decision.Refused) {
            Diagnostics.add("camera", d.line)
            outcome.after.forEach { Diagnostics.add("camera", it) }
            finish()
            return
        }
        hideSystemBars()
        saver = CameraSaver(this)
        CameraProcess.startOnce(this, saver)
        val e = CameraEngine(this, captureOnly = true)
        engine = e
        if (wantsFront(intent)) e.preferFront()
        e.openMode(if (video) "video" else "photo")
        interruptions = CameraInterruptions(this) { reason -> if (e.isRecording) e.stopVideo(reason); state.cancelCountdown() }
        // Every Back — the drawn key and the system's — goes through back(): an overlay closes first, then the
        // accept / retake page returns to the viewfinder, then the request is cancelled.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        setShellAppContent(statusBar = false) {
            CaptureScreen(e, state, sink, if (video) "video" else "image", review, finishing, onAccept = ::accept, onRetake = ::retake)
        }
    }

    /**
     * The request as the platform gives it, one read per method (`CaptureRequestRule` holds every rule over them, and
     * its tests). EXTRA_OUTPUT is turned to its string here ONCE; nothing else in this class reads the caller's Uri.
     */
    private class IntentCaptureRequest(private val activity: Activity, private val request: Intent) : CaptureRequestPort {
        override fun hasOutput(): Boolean = request.hasExtra(MediaStore.EXTRA_OUTPUT)
        override fun outputText(): String? = request.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)?.toString()
        override fun callingPackage(): String? = activity.callingPackage
        override fun launchedFromUid(): Int? = activity.launchedFromUid.takeIf { it >= 0 }
        override fun clipUris(): List<String> {
            val clip = request.clipData ?: return emptyList()
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri?.toString() }
        }
        override fun flags(): Int = request.flags
        override fun ownAuthorities(): Set<String> =
            activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
                .flatMap { it.authority.orEmpty().split(';') }.filter { it.isNotEmpty() }.toSet()
    }

    /** The guard's decision over the real intent, with the lines `CaptureRequestRule` wrote on the way. */
    private fun decide(request: Intent): CaptureRequestRule.Outcome {
        val outcome = CaptureRequestRule.decide(IntentCaptureRequest(this, request), AndroidUriAccess(this))
        outcome.before.forEach { Diagnostics.add("camera", it) }
        return outcome
    }

    /** `EXTRA_USE_FRONT_CAMERA` and the older forms callers still send. Where there is no front camera the back one answers. */
    private fun wantsFront(request: Intent): Boolean = runCatching {
        request.getBooleanExtra("android.intent.extra.USE_FRONT_CAMERA", false) ||
            request.getIntExtra("android.intent.extras.CAMERA_FACING", -1) == 1 ||
            request.getIntExtra("android.intent.extras.LENS_FACING_FRONT", -1) == 1 ||
            request.getBooleanExtra("camerafacing", false)
    }.getOrDefault(false)

    private fun retake() {
        discard()
        review = null
    }

    private fun discard() {
        when (val r = review) {
            is CaptureReview.Image -> r.shot.file.delete()
            is CaptureReview.Video -> r.take.file.delete()
            null -> Unit
        }
    }

    private fun accept() {
        val r = review ?: return
        if (finishing) return
        finishing = true
        val d = decision
        worker.execute {
            val result: Intent? = try {
                when {
                    // RESULT_OK only when the write layer reported the output written (null = written).
                    d is CaptureOutputGuard.Decision.Accepted && r is CaptureReview.Image ->
                        if (writeToCaller(d, r.shot.file, isImage = true) == null) Intent() else null
                    d is CaptureOutputGuard.Decision.Accepted && r is CaptureReview.Video ->
                        if (writeToCaller(d, r.take.file, isImage = false) == null) Intent().setData(Uri.parse(d.uri)) else null
                    r is CaptureReview.Image -> {
                        // The no-output contract: a small thumbnail in `data`, and no file anywhere.
                        // Small enough for a Binder transaction whatever the sensor: at most 256 px on its long side.
                        val thumb = thumbnailOf(r.shot.file, r.shot.rotationDegrees, edge = 160, maxLong = 256)
                        if (thumb == null) null else {
                            Diagnostics.add("camera", "capture image -> thumbnail ${thumb.width}x${thumb.height}, no file")
                            Intent().putExtra("data", thumb)
                        }
                    }
                    r is CaptureReview.Video -> {
                        // The no-output contract: a DCIM/Camera row through the one write layer, returned with a read grant.
                        val uri = saver.saveVideo(r.take)
                        if (uri == null) null else Intent().setData(Uri.parse(uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Diagnostics.add("camera", "capture answer failed (${e.javaClass.simpleName})")
                null
            }
            discard()
            runOnUiThread {
                if (result != null) setResult(Activity.RESULT_OK, result) else setResult(Activity.RESULT_CANCELED)
                Diagnostics.add("camera", "capture answer: ${if (result != null) "RESULT_OK" else "RESULT_CANCELED"}")
                CameraDumpService.linger(this)
                finish()
            }
        }
    }

    /**
     * Writes [file] into the caller's output — through `MediaWrites.writeCaptureOutput` and the guard's [accepted]
     * token, the only route there is. Returns null when written, else why. A still loses any location tag first.
     */
    private fun writeToCaller(accepted: CaptureOutputGuard.Decision.Accepted, file: File, isImage: Boolean): String? {
        if (isImage) runCatching { ExifInterface(file.path).apply { CameraEngine.stripLocation(this); saveAttributes() } }
        val why = saver.writes.writeCaptureOutput(accepted) { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
        Diagnostics.add("camera", if (why == null) "capture ${if (isImage) "image" else "video"} -> the caller's output, ${file.length()} bytes" else "capture output failed: $why")
        return why
    }

    private fun back() {
        if (state.back()) return
        if (review != null && !finishing) { retake(); return }
        cancel()
    }

    private fun cancel() {
        engine?.let { if (it.isRecording) it.stopVideo("cancelled") }
        discard()
        Diagnostics.add("camera", "capture answer: RESULT_CANCELED (back)")
        setResult(Activity.RESULT_CANCELED)
        CameraDumpService.linger(this)
        finish()
    }

    override fun onResume() {
        super.onResume()
        val e = engine ?: return
        hideSystemBars()
        e.start()
        interruptions?.start()
    }

    override fun onPause() {
        engine?.let { if (it.isRecording) it.stopVideo("left the camera") }
        state.cancelCountdown()
        interruptions?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing && !finishing) discard()
        engine?.release()
        RemoteRings.release(this, ring)
        super.onDestroy()
    }
}
