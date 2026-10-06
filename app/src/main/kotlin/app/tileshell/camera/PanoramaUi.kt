package app.tileshell.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.ExifInterface
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.tokens.ShellType
import java.io.File

/**
 * One panorama sweep (phase 17 build task 6b): frames are taken from the analysis stream as the phone turns to the
 * right — one every [STEP_DEGREES] of yaw, read from the rotation-vector sensor, or every [STEP_MS] where the phone
 * has no such sensor — and stitched off the main thread when the shutter is pressed again or the sweep is complete.
 * The stitched image goes to the sink as a still; nothing reaches MediaStore before that, so an interrupted sweep
 * leaves no row: it logs `[camera] mode panorama: cancelled (<reason>)` and drops its frames.
 */
class PanoramaCapture(private val engine: CameraEngine, private val sink: CaptureSink, private val onEnd: () -> Unit) {
    var progress by mutableFloatStateOf(0f); private set
    var stitching by mutableStateOf(false); private set
    var frameCount by mutableStateOf(0); private set

    private val frames = ArrayList<Bitmap>()
    private val main = Handler(Looper.getMainLooper())
    private var ended = false
    private var yaw: Float? = null
    private var yawStart: Float? = null
    private var yawAtLast = 0f
    private var timeAtLast = 0L
    private var sensors: SensorManager? = null

    private val listener = object : SensorEventListener {
        private val rotation = FloatArray(9)
        private val remapped = FloatArray(9)
        private val orientation = FloatArray(3)
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            // The phone is held upright: its Y axis points up, so the heading is read about the world's Z with X / Z remapped.
            SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            SensorManager.getOrientation(remapped, orientation)
            yaw = Math.toDegrees(orientation[0].toDouble()).toFloat()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        val manager = engine.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager != null && sensor != null) { sensors = manager; manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME) }
        Diagnostics.add("camera", "panorama: sweep started (${if (sensor != null) "gyro-stepped" else "time-stepped"})")
        engine.frameTap = { image ->
            val now = System.currentTimeMillis()
            val heading = yaw
            val turned = if (heading != null) {
                val start = yawStart ?: heading.also { yawStart = it; yawAtLast = it - STEP_DEGREES }
                // Degrees turned to the right since the start, unwrapped across ±180.
                val swept = ((heading - start + 540f) % 360f) - 180f
                if (swept - (((yawAtLast - start + 540f) % 360f) - 180f) >= STEP_DEGREES) { yawAtLast = heading; swept } else null
            } else if (now - timeAtLast >= STEP_MS) frames.size * STEP_DEGREES else null
            if (turned != null && !ended && frames.size < MAX_FRAMES) {
                timeAtLast = now
                val raw = image.toBitmap()
                val rotationDegrees = image.imageInfo.rotationDegrees
                val upright = if (rotationDegrees == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
                synchronized(frames) { frames += upright }
                main.post {
                    frameCount = frames.size
                    progress = (turned / SWEEP_DEGREES).coerceIn(0f, 1f)
                    if (frames.size >= MAX_FRAMES || turned >= SWEEP_DEGREES) finish()
                }
            }
        }
    }

    private fun stopTaking() {
        ended = true
        engine.frameTap = null
        sensors?.unregisterListener(listener)
    }

    /** The shutter pressed again, or the sweep complete: stitch what was taken. */
    fun finish() {
        if (ended) return
        stopTaking()
        val taken = synchronized(frames) { frames.toList() }
        if (taken.size < 2) { end("too few frames"); return }
        stitching = true
        val cache = File(engine.context.cacheDir, "camera").apply { mkdirs() }
        Thread {
            val result = PanoramaStitcher.stitch(taken)
            taken.forEach { it.recycle() }
            when (result) {
                is PanoramaStitcher.Result.Failed -> main.post { end(result.reason) }
                is PanoramaStitcher.Result.Stitched -> {
                    val file = File(cache, "pano_${System.currentTimeMillis()}.jpg")
                    val ok = runCatching {
                        file.outputStream().use { result.image.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                        val exif = ExifInterface(file.path)
                        val now = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, now)
                        exif.setAttribute(ExifInterface.TAG_DATETIME, now)
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        exif.setAttribute(ExifInterface.TAG_MAKE, android.os.Build.MANUFACTURER)
                        exif.setAttribute(ExifInterface.TAG_MODEL, android.os.Build.MODEL)
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Tessera panorama (${taken.size} frames)")
                        exif.saveAttributes()
                    }.isSuccess
                    val shot = PhotoShot(file, result.image.width, result.image.height, 0)
                    result.image.recycle()
                    main.post {
                        stitching = false
                        if (ok) { Diagnostics.add("camera", "panorama: stitched ${taken.size} frames -> ${shot.width}x${shot.height}"); sink.photo(shot, null, true); onEnd() }
                        else { file.delete(); end("the stitched image could not be written") }
                    }
                }
            }
        }.start()
    }

    /** Interrupted (Back, the screen going off, a call, leaving the camera): the partial capture is discarded. */
    fun cancel(reason: String) {
        if (ended && !stitching) return
        stopTaking()
        end(reason)
    }

    private fun end(reason: String) {
        stitching = false
        synchronized(frames) { frames.forEach { if (!it.isRecycled) it.recycle() }; frames.clear() }
        Diagnostics.add("camera", "mode panorama: cancelled ($reason)")
        sink.failed("Panorama cancelled")
        onEnd()
    }

    companion object {
        const val STEP_DEGREES = 8f
        const val SWEEP_DEGREES = 120f
        const val STEP_MS = 450L
        const val MAX_FRAMES = 16
    }
}

/**
 * The panorama guide (Approximation Y12; camera-pass2 1.5.3, MEDIUM): a full-width translucent band 0.35 · W tall
 * centred on the screen centre, the captured strip in a white-outlined frame at its left, a white arrow → on a thin
 * centre line. Progress is the frame's growing width (UNMEASURED-5).
 */
@Composable
fun BoxScope.PanoramaGuide(g: CameraGeometry, capture: PanoramaCapture?) {
    val band = g.panoBand
    val progress = capture?.progress ?: 0f
    val frame = g.panoFrame(progress)
    Box(Modifier.offset(band.left.dp, band.top.dp).size(band.width.dp, band.height.dp).background(Color(0x66000000)).testTag("camera_pano_band"))
    Box(Modifier.offset(frame.left.dp, frame.top.dp).size(frame.width.dp, frame.height.dp).border(1.5.dp, Color.White).testTag("camera_pano_frame"))
    val arrow = g.panoArrow(progress)
    Canvas(Modifier.offset(0.dp, band.top.dp).size(band.width.dp, band.height.dp)) {
        val u = 1.dp.toPx()
        val cy = size.height / 2f
        drawLine(Color(0x99FFFFFF), Offset(frame.right * u, cy), Offset(size.width, cy), 1f * u)
        // 23.6 × 21.4 epx, pointing right.
        val ax = arrow.x * u
        val path = Path().apply {
            moveTo(ax - 11.8f * u, cy - 4f * u); lineTo(ax, cy - 4f * u); lineTo(ax, cy - 10.7f * u); lineTo(ax + 11.8f * u, cy)
            lineTo(ax, cy + 10.7f * u); lineTo(ax, cy + 4f * u); lineTo(ax - 11.8f * u, cy + 4f * u); close()
        }
        drawPath(path, Color.White)
    }
    val text = when {
        capture == null -> "Tap the camera button, then turn slowly to the right"
        capture.stitching -> "Stitching…"
        else -> "Keep turning — tap the camera button to finish"
    }
    Centred(Pt(g.w / 2f, band.bottom + 18f)) { BasicText(text, Modifier.testTag("camera_pano_hint"), style = ShellType.caption.copy(color = Color.White), maxLines = 1) }
}
