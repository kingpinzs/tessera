package app.tileshell.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.location.Location
import android.media.ExifInterface
import android.os.Build
import android.util.Range
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import app.tileshell.diag.Diagnostics
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** What the viewfinder shows instead of a preview. */
sealed interface CameraStatus {
    data object Starting : CameraStatus
    data object NoPermission : CameraStatus
    data object NoCamera : CameraStatus
    data class Busy(val reason: String) : CameraStatus
    data object Ready : CameraStatus
}

enum class FlashSetting(val label: String) { AUTO("Flash auto"), ON("Flash on"), OFF("Flash off") }

/** A still as CameraX wrote it into the cache: nothing is in shared storage yet. */
class PhotoShot(val file: File, val width: Int, val height: Int, val rotationDegrees: Int)

/** A finished recording in the cache. [error] is null, or why the take ended early ("storage full", …). */
class VideoTake(val file: File, val error: String?, val audio: Boolean, val slowMotionFps: Int?)

/**
 * The Camera's CameraX half (phase 17 build tasks 6, 6a, 6c, 6d): one camera bound to the activity's lifecycle, its
 * use cases chosen by the capture mode, every capture written to a file in the app's CACHE and handed back — where it
 * goes from there (DCIM/Camera through `media/MediaWrites`, or a capture caller's output through the output guard) is
 * the activity's business, never this class's. No foreground service (T17-19): everything stops with the activity.
 *
 * State the chrome draws is Compose state; every silent-empty state writes its `[camera]` line (E18).
 */
@SuppressLint("UnsafeOptInUsageError")
class CameraEngine(private val activity: ComponentActivity, private val captureOnly: Boolean = false) {
    /** The activity, as a context for the sensors and the cache. */
    val context: Context get() = activity
    private val main: Executor = activity.mainExecutor
    private val worker = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    var status: CameraStatus by mutableStateOf(CameraStatus.Starting); private set
    var caps: CameraCaps? by mutableStateOf(null); private set
    var env: CameraEnv by mutableStateOf(CameraEnv(false, false, Build.SUPPORTED_ABIS.firstOrNull().orEmpty(), false)); private set
    var hasFront by mutableStateOf(false); private set
    var front by mutableStateOf(false); private set

    /** photo, video or panorama — the three that cycle through the shutter (Y3). */
    var mode by mutableStateOf("photo"); private set
    var slowMotion by mutableStateOf(false); private set
    var hdr by mutableStateOf(false); private set
    var flash by mutableStateOf(FlashSetting.AUTO); private set
    var videoLight by mutableStateOf(false); private set
    var zoom by mutableFloatStateOf(1f); private set
    var recording by mutableStateOf(false); private set
    var recordingSeconds by mutableIntStateOf(0); private set
    var soundOff by mutableStateOf(false); private set
    var settings by mutableStateOf(CameraSettings.load(activity)); private set

    /** The dial's values: a control's index into its value list, absent = automatic. */
    var pro: Map<ProControl, Int> by mutableStateOf(emptyMap()); private set

    /** The photo sizes of the bound camera at each aspect ratio, largest first; the video qualities, highest first. */
    var photoSizes: List<Size> by mutableStateOf(emptyList()); private set
    var videoQualities: List<Pair<String, Size>> by mutableStateOf(emptyList()); private set
    var stabilizationSupported by mutableStateOf(false); private set

    /**
     * The mode whose preview box is drawn. It follows [mode] at once, except through a mode switch's slide (Y6), where
     * the viewfinder moves it at the slide's black gap so the old preview leaves in its own shape.
     */
    var shownMode by mutableStateOf("photo")

    /** The preview's aspect as long side : short side, for [CameraGeometry.preview]. */
    val previewAspect: Pair<Int, Int> get() = if (shownMode == "video" || (shownMode == "photo" && settings.wide)) 16 to 9 else 4 to 3

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null
    private var previewView: PreviewView? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var stateListener: Consumer<CameraState>? = null
    private var stateInfo: CameraInfo? = null
    private var announced = false
    private var heldIso: Int? = null
    private var heldExposureNs: Long? = null
    @Volatile private var lastResult: TotalCaptureResult? = null
    private var stopReason: String? = null

    /** Living Images' rolling second (6d) and the panorama's frame tap (6b) read the same analysis stream. */
    val living = LivingClip()
    @Volatile var frameTap: ((androidx.camera.core.ImageProxy) -> Unit)? = null

    /** The location a capture carries in its EXIF, set by the activity while it holds a fix; null = none. */
    @Volatile var location: Location? = null

    private val sessionCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            lastResult = result
        }
    }

    fun hasCameraPermission(): Boolean = activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun hasMicrophone(): Boolean = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** The PreviewView the composition made; binding waits for it. */
    fun attach(view: PreviewView) {
        previewView = view
        if (status == CameraStatus.Ready || provider != null) bind()
    }

    /** Called on every resume: opens the camera, or says why it cannot (the grant page, "no camera", "camera in use"). */
    fun start() {
        settings = CameraSettings.load(activity)
        if (!hasCameraPermission()) {
            if (status != CameraStatus.NoPermission) Diagnostics.add("camera", "no camera permission: grant page")
            status = CameraStatus.NoPermission
            return
        }
        val existing = provider
        if (existing != null) { bind(); return }
        status = CameraStatus.Starting
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull()
            if (p == null) { noCamera(0); return@addListener }
            provider = p
            val ext = ExtensionsManager.getInstanceAsync(activity, p)
            ext.addListener({ extensions = runCatching { ext.get() }.getOrNull(); bind() }, main)
        }, main)
    }

    private fun noCamera(count: Int) {
        Diagnostics.add("camera", "devices=$count front=absent")
        status = CameraStatus.NoCamera
    }

    private fun selector(): CameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    /** Binds the use cases of the current mode. Safe to call again: it unbinds first. */
    fun bind() {
        val p = provider ?: return
        val view = previewView ?: return
        if (!hasCameraPermission()) { status = CameraStatus.NoPermission; return }
        val infos = p.availableCameraInfos
        hasFront = infos.any { it.lensFacing == CameraSelector.LENS_FACING_FRONT }
        val hasBack = infos.any { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        if (infos.isEmpty() || (!hasFront && !hasBack)) { noCamera(infos.size); return }
        if (front && !hasFront) front = false
        if (!front && !hasBack) front = true
        val base = selector()
        val info = runCatching { p.getCameraInfo(base) }.getOrNull() ?: run { noCamera(infos.size); return }
        val c = readCaps(info)
        caps = c
        val highSpeed = runCatching { Recorder.getHighSpeedVideoCapabilities(info) }.getOrNull()
        val hdrAvailable = runCatching { extensions?.isExtensionAvailable(base, ExtensionMode.HDR) == true }.getOrDefault(false)
        env = CameraEnv(
            hdrExtension = hdrAvailable,
            highSpeedSession = highSpeed != null && highSpeed.supportedDynamicRanges.any { highSpeed.getSupportedQualities(it).isNotEmpty() },
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            panoramaLibrary = PanoramaStitcher.available,
        )
        readChoices(info)
        if (!announced) {
            announced = true
            Diagnostics.add("camera", "devices=${infos.size} front=${if (hasFront) "present" else "absent"}")
            CameraGates.hiddenLines(c, env).forEach { Diagnostics.add("camera", it) }
        }
        // A mode the camera cannot do falls back to the one it can (an App Shortcut may name any mode).
        if (mode == "panorama" && !CameraGates.panorama(env).available) { mode = "photo"; shownMode = "photo" }
        if (slowMotion && !CameraGates.slowmo(env).available) slowMotion = false
        if (hdr && !hdrAvailable) hdr = false
        runCatching { p.unbindAll() }
        watchState(info)
        try {
            camera = when {
                mode == "video" && slowMotion -> bindSlowMotion(p, base, info, view)
                mode == "video" -> bindVideo(p, base, info, view)
                else -> bindPhoto(p, base, view)
            }
            status = CameraStatus.Ready
            zoom = 1f
            applyPro()
            if (mode == "video") camera?.cameraControl?.enableTorch(videoLight && c.hasFlash)
        } catch (e: Exception) {
            busy("cannot open (${e.javaClass.simpleName})")
        }
    }

    private fun previewBuilder(wide: Boolean): Preview.Builder {
        val b = Preview.Builder().setResolutionSelector(
            ResolutionSelector.Builder().setAspectRatioStrategy(
                if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY,
            ).build(),
        )
        Camera2Interop.Extender(b).setSessionCaptureCallback(sessionCallback)
        return b
    }

    private fun bindPhoto(p: ProcessCameraProvider, base: CameraSelector, view: PreviewView): Camera {
        val wide = mode == "photo" && settings.wide
        val preview = previewBuilder(wide).build().also { it.surfaceProvider = view.surfaceProvider }
        val size = chosenPhotoSize()
        val selectorBuilder = ResolutionSelector.Builder()
            .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            .setAspectRatioStrategy(if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        if (size != null) selectorBuilder.setResolutionStrategy(ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode())
            .setResolutionSelector(selectorBuilder.build())
            .build()
        imageCapture = capture
        videoCapture = null
        val useHdr = hdr && mode == "photo" && env.hdrExtension
        val cases = mutableListOf<UseCase>(preview, capture)
        // The analysis stream feeds Living Images' rolling second and the panorama's frames. An extension session takes
        // a preview and a still only, so an HDR shot carries no clip.
        if (!useHdr && (mode == "panorama" || (settings.livingImages && !captureOnly))) {
            val analysisBuilder = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // Living Images keeps the analysis stream's small default frames; a panorama needs frames worth stitching.
            if (mode == "panorama") analysisBuilder.setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build(),
            )
            val analysis = analysisBuilder.build()
            analysis.setAnalyzer(analysisExecutor) { image ->
                try {
                    val tap = frameTap
                    if (tap != null) tap(image) else if (mode == "photo") living.add(image)
                } finally {
                    image.close()
                }
            }
            cases += analysis
        } else {
            living.clear()
        }
        val selector = if (useHdr) extensions!!.getExtensionEnabledCameraSelector(base, ExtensionMode.HDR) else base
        return p.bindToLifecycle(activity, selector, *cases.toTypedArray())
    }

    private fun recorder(info: CameraInfo): Recorder {
        val wanted = QUALITIES.firstOrNull { it.first == settings.videoQuality }?.second
        val selector = if (wanted != null) QualitySelector.from(wanted, FallbackStrategy.higherQualityOrLowerThan(wanted))
        else QualitySelector.from(Quality.HIGHEST)
        return Recorder.Builder().setQualitySelector(selector).build()
    }

    private fun bindVideo(p: ProcessCameraProvider, base: CameraSelector, info: CameraInfo, view: PreviewView): Camera {
        val preview = previewBuilder(true).build().also { it.surfaceProvider = view.surfaceProvider }
        val builder = VideoCapture.Builder(recorder(info))
        if (settings.stabilization && stabilizationSupported) builder.setVideoStabilizationEnabled(true)
        val video = builder.build()
        videoCapture = video
        imageCapture = null
        living.clear()
        return p.bindToLifecycle(activity, base, preview, video)
    }

    /** The frame rate the slow-motion session captures at; set by [bindSlowMotion]. */
    private var slowFps: Int? = null

    /**
     * BS-2's route: CameraX's own constrained high-speed session at the highest range the camera lists, saved at 30 fps
     * (r3 D11). A session of a VideoCapture and a Preview only — no still, no analysis.
     */
    private fun bindSlowMotion(p: ProcessCameraProvider, base: CameraSelector, info: CameraInfo, view: PreviewView): Camera {
        val capabilities = Recorder.getHighSpeedVideoCapabilities(info) ?: throw IllegalStateException("no high-speed session")
        val quality = capabilities.supportedDynamicRanges.flatMap { capabilities.getSupportedQualities(it) }.firstOrNull()
            ?: throw IllegalStateException("no high-speed quality")
        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
        val video = VideoCapture.withOutput(recorder)
        // A high-speed session takes no resolution selector on its preview (BS-2's limits).
        val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
        val probe = HighSpeedVideoSessionConfig(video, preview, isSlowMotionEnabled = true)
        val range: Range<Int> = info.getSupportedFrameRateRanges(probe).maxWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })
            ?: throw IllegalStateException("no high-speed frame rate")
        slowFps = range.upper
        videoCapture = video
        imageCapture = null
        living.clear()
        return p.bindToLifecycle(activity, base, HighSpeedVideoSessionConfig(video, preview, range, true))
    }

    private fun watchState(info: CameraInfo) {
        stateListener?.let { old -> stateInfo?.removeCameraStateListener(old) }
        val listener = Consumer<CameraState> { state ->
            val error = state.error
            when {
                error != null && error.code in BUSY_CODES -> busy(busyReason(error.code))
                error != null && error.type == CameraState.ErrorType.CRITICAL -> busy(busyReason(error.code))
                state.type == CameraState.Type.OPEN && status is CameraStatus.Busy -> {
                    // The camera came back (the other app left): say so the way a start does (Edge cases).
                    Diagnostics.add("camera", "devices=${provider?.availableCameraInfos?.size ?: 0} front=${if (hasFront) "present" else "absent"}")
                    status = CameraStatus.Ready
                }
            }
        }
        stateListener = listener
        stateInfo = info
        info.addCameraStateListener(main, listener)
    }

    private fun busy(reason: String) {
        if (status != CameraStatus.Busy(reason)) Diagnostics.add("camera", "busy: $reason")
        if (recording) stopVideo("camera lost")
        status = CameraStatus.Busy(reason)
    }

    private fun busyReason(code: Int): String = when (code) {
        CameraState.ERROR_CAMERA_IN_USE -> "camera in use by another app"
        CameraState.ERROR_MAX_CAMERAS_IN_USE -> "too many cameras open"
        CameraState.ERROR_CAMERA_DISABLED -> "camera disabled by policy"
        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> "do not disturb blocks the camera"
        CameraState.ERROR_CAMERA_REMOVED -> "camera removed"
        else -> "camera error $code"
    }

    private fun readCaps(info: CameraInfo): CameraCaps {
        val c2 = Camera2CameraInfo.from(info)
        fun <T> key(k: CameraCharacteristics.Key<T>): T? = runCatching { c2.getCameraCharacteristic(k) }.getOrNull()
        val ev = key(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val step = key(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val iso = key(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposure = key(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val zoomRange = key(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        return CameraCaps(
            hardwareLevel = key(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1,
            capabilities = key(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty(),
            aeModes = key(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.toSet().orEmpty(),
            evLow = ev?.lower ?: 0,
            evHigh = ev?.upper ?: 0,
            evStep = step?.toFloat() ?: 0f,
            minFocusDistance = key(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            awbModes = key(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet().orEmpty(),
            maxZoom = zoomRange?.upper ?: key(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f,
            isoLow = iso?.lower, isoHigh = iso?.upper,
            exposureLowNs = exposure?.lower, exposureHighNs = exposure?.upper,
            hasFlash = info.hasFlashUnit(),
        )
    }

    private fun readChoices(info: CameraInfo) {
        val map = runCatching { Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) }.getOrNull()
        photoSizes = (map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() + map?.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList().orEmpty())
            .distinct().sortedByDescending { it.width.toLong() * it.height }
        val capabilities = runCatching { Recorder.getVideoCapabilities(info) }.getOrNull()
        videoQualities = QUALITIES.mapNotNull { (name, quality) ->
            val size = capabilities?.supportedDynamicRanges?.firstNotNullOfOrNull { capabilities.getResolution(quality, it) }
            if (size != null) name to size else null
        }
        stabilizationSupported = capabilities?.isStabilizationSupported == true
    }

    /** The sizes offered at the current aspect ratio, largest first. */
    fun photoSizesForAspect(wide: Boolean = settings.wide): List<Size> = photoSizes.filter { CameraChoices.matchesAspect(it.width, it.height, wide) }

    private fun chosenPhotoSize(): Size? {
        val offered = photoSizesForAspect(mode == "photo" && settings.wide)
        return offered.firstOrNull { "${it.width}x${it.height}" == settings.photoSize } ?: offered.firstOrNull()
    }

    fun updateSettings(change: (CameraSettings) -> CameraSettings) {
        val next = change(CameraSettings.load(activity))
        CameraSettings.save(activity, next)
        settings = next
        if (!recording) bind()
    }

    // ---- controls --------------------------------------------------------------------------------------------------

    /** The discs' switch: binds the new mode now; [shownMode] is left for the caller's slide to move. */
    fun switchMode(to: String) {
        if (recording || to == mode && !(to == "video" && slowMotion)) return
        mode = to
        if (to != "video") slowMotion = false
        if (to != "photo") pro = emptyMap()
        Diagnostics.add("camera", "mode -> $to")
        bind()
    }

    /** Opens a mode by its App Shortcut / intent id: photo, video, panorama, or slowmo (video with slow motion on). */
    fun openMode(id: String) {
        if (recording) return
        val wantSlow = id == "slowmo"
        val target = when (id) { "video", "slowmo" -> "video"; "panorama" -> "panorama"; else -> "photo" }
        if (target == mode && wantSlow == slowMotion && status == CameraStatus.Ready) return
        mode = target
        shownMode = target
        slowMotion = wantSlow
        if (target != "photo") pro = emptyMap()
        bind()
    }

    fun toggleSlowMotion(): Boolean {
        if (recording || !CameraGates.slowmo(env).available) return slowMotion
        slowMotion = !slowMotion
        bind()
        return slowMotion
    }

    fun switchCamera() {
        if (recording || !hasFront) return
        front = !front
        pro = emptyMap()
        announced = false
        Diagnostics.add("camera", if (front) "front-facing camera" else "main camera")
        bind()
    }

    /** A capture caller asked for the front camera: used where there is one, else the back camera answers. */
    fun preferFront() { front = true }

    private fun flashMode() = when (flash) {
        FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
        FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
    }

    fun cycleFlash(): FlashSetting {
        flash = FlashSetting.entries[(flash.ordinal + 1) % FlashSetting.entries.size]
        imageCapture?.flashMode = flashMode()
        return flash
    }

    fun toggleVideoLight(): Boolean {
        videoLight = !videoLight
        camera?.cameraControl?.enableTorch(videoLight)
        return videoLight
    }

    fun toggleHdr(): Boolean {
        if (!env.hdrExtension) return false
        hdr = !hdr
        if (hdr) pro = emptyMap()
        bind()
        return hdr
    }

    fun setZoomRatio(ratio: Float) {
        val max = caps?.maxZoom ?: 1f
        if (max <= 1f) return
        val r = ratio.coerceIn(1f, max)
        zoom = r
        camera?.cameraControl?.setZoomRatio(r)
    }

    /** The zoom readout's tap: 1× → 2× → 4× → … → the camera's maximum → 1×. */
    fun cycleZoom(): Float {
        val max = caps?.maxZoom ?: 1f
        val next = if (zoom >= max - 0.01f) 1f else minOf(max, if (zoom < 1.5f) 2f else zoom * 2f)
        setZoomRatio(next)
        return zoom
    }

    /**
     * Tap-to-focus at a point of the preview view (its own pixels); [xEpx], [yEpx] are the same point on the screen's
     * canvas, for the line. `[camera] focus at <x>,<y>: locked | locked (not in focus) | not locked | unsupported | failed`.
     */
    fun focusAt(xInView: Float, yInView: Float, xEpx: Int, yEpx: Int, onState: (String) -> Unit = {}) {
        val cam = camera ?: return
        val view = previewView ?: return
        fun say(state: String) { Diagnostics.add("camera", "focus at $xEpx,$yEpx: $state"); onState(state) }
        val action = FocusMeteringAction.Builder(view.meteringPointFactory.createPoint(xInView, yInView))
            .setAutoCancelDuration(5, TimeUnit.SECONDS).build()
        if (!cam.cameraInfo.isFocusMeteringSupported(action)) { say("unsupported"); return }
        val future = cam.cameraControl.startFocusAndMetering(action)
        future.addListener({
            val result = runCatching { future.get() }.getOrNull()
            // Camera2's two ends of a focus sweep both LOCK the lens: FOCUSED_LOCKED, and NOT_FOCUSED_LOCKED ("AF has
            // failed to focus successfully and has locked focus") — what the emulator's camera reports for its drawn
            // scene. A sweep that ended neither way (cancelled, timed out) is "not locked".
            val afState = lastResult?.get(CaptureResult.CONTROL_AF_STATE)
            say(when {
                result == null -> "failed"
                result.isFocusSuccessful -> "locked"
                afState == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "locked (not in focus)"
                else -> "not locked"
            })
        }, main)
    }

    // ---- the pro dial (6a) -----------------------------------------------------------------------------------------

    /** Sets [control] to value [index] of its list, or back to automatic with null. */
    fun setPro(control: ProControl, index: Int?) {
        val c = caps ?: return
        if (mode != "photo" || CameraGates.proControls(c).none { it.first == control && it.second.available }) return
        val wasManualExposure = ProControl.ISO in pro || ProControl.SHUTTER in pro
        val next = pro.toMutableMap()
        if (index == null) next.remove(control) else next[control] = index
        val manualExposure = ProControl.ISO in next || ProControl.SHUTTER in next
        if (manualExposure && !wasManualExposure) {
            // r3 D10: when only one of ISO and shutter is set, the other is held at the last auto-exposure result.
            heldIso = lastResult?.get(CaptureResult.SENSOR_SENSITIVITY)
            heldExposureNs = lastResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            next.remove(ProControl.EXPOSURE)   // EV is disabled while either is manual
        }
        if (manualExposure && control == ProControl.EXPOSURE) next.remove(ProControl.EXPOSURE)
        pro = next
        if (hdr) { hdr = false; bind() } else applyPro()
    }

    fun resetPro() { if (pro.isNotEmpty()) { pro = emptyMap(); applyPro() } }

    /** Whether EV may be changed now (r3 D10: not while ISO or shutter is manual). */
    val evEnabled: Boolean get() = ProControl.ISO !in pro && ProControl.SHUTTER !in pro

    private fun applyPro() {
        val cam = camera ?: return
        val c = caps ?: return
        val options = CaptureRequestOptions.Builder()
        val isoIndex = pro[ProControl.ISO]
        val shutterIndex = pro[ProControl.SHUTTER]
        if (isoIndex != null || shutterIndex != null) {
            val iso = isoIndex?.let { ProValues.isos(c).getOrNull(it) } ?: heldIso ?: c.isoLow ?: 100
            val ns = shutterIndex?.let { ProValues.shutterSpeeds(c).getOrNull(it)?.first } ?: heldExposureNs ?: 16_666_666L
            options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            options.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso.coerceIn(c.isoLow ?: iso, c.isoHigh ?: iso))
            options.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, ns.coerceIn(c.exposureLowNs ?: ns, c.exposureHighNs ?: ns))
        }
        pro[ProControl.FOCUS]?.let { i ->
            ProValues.focusStops(c).getOrNull(i)?.let { dioptres ->
                options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                options.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, dioptres)
            }
        }
        pro[ProControl.WB]?.let { i ->
            CameraGates.wbPresets(c).getOrNull(i)?.let { options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, it.awbMode) }
        }
        runCatching { Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(options.build()) }
        val ev = if (evEnabled) pro[ProControl.EXPOSURE]?.let { ProValues.evIndices(c).getOrNull(it) } ?: 0 else 0
        if (c.evLow != 0 || c.evHigh != 0) runCatching { cam.cameraControl.setExposureCompensationIndex(ev.coerceIn(c.evLow, c.evHigh)) }
    }

    // ---- captures --------------------------------------------------------------------------------------------------

    private fun cacheFile(suffix: String): File {
        val dir = File(activity.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "shot_${System.currentTimeMillis()}_${System.nanoTime() % 100000}$suffix")
    }

    /** Removes what an earlier run left in the capture cache (a process killed mid-capture). */
    fun clearCache() { File(activity.cacheDir, "camera").listFiles()?.forEach { it.delete() } }

    /**
     * One still into the cache. EXIF: CameraX writes the orientation and the HAL's tags; this adds the capture time
     * where it is missing, the location when [withLocation] and a fix is held, and the sensor's ISO, exposure time,
     * white balance and focus distance (6a) — the manual values when the dial set them.
     */
    fun takePhoto(withLocation: Boolean, onDone: (PhotoShot?, String?) -> Unit) {
        val capture = imageCapture ?: run { onDone(null, "the camera is not ready"); return }
        val file = cacheFile(".jpg")
        val metadata = ImageCapture.Metadata()
        if (withLocation) location?.let { metadata.location = it }
        val manual = pro.toMap()
        val result = lastResult
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build(), worker,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val shot = runCatching {
                        writeExif(file, manual, result, withLocation)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, bounds)
                        val rotation = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270
                            else -> 0
                        }
                        PhotoShot(file, bounds.outWidth, bounds.outHeight, rotation)
                    }.getOrNull()
                    main.execute { if (shot != null) onDone(shot, null) else { file.delete(); onDone(null, "the picture could not be read") } }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    main.execute { onDone(null, "capture failed (${exception.imageCaptureError})") }
                }
            },
        )
    }

    private fun writeExif(file: File, manual: Map<ProControl, Int>, result: TotalCaptureResult?, keepLocation: Boolean) {
        val exif = ExifInterface(file.path)
        val now = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(java.util.Date())
        if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, now)
        if (exif.getAttribute(ExifInterface.TAG_DATETIME) == null) exif.setAttribute(ExifInterface.TAG_DATETIME, now)
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        val manualExposure = ProControl.ISO in manual || ProControl.SHUTTER in manual
        fun put(tag: String, value: String?, force: Boolean) {
            if (value != null && (force || exif.getAttribute(tag) == null)) exif.setAttribute(tag, value)
        }
        put(ExifInterface.TAG_ISO_SPEED_RATINGS, result?.get(CaptureResult.SENSOR_SENSITIVITY)?.toString(), manualExposure)
        put(ExifInterface.TAG_EXPOSURE_TIME, result?.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { (it / 1e9).toString() }, manualExposure)
        exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, if (ProControl.WB in manual) "1" else "0")
        if (ProControl.FOCUS in manual) {
            val dioptres = result?.get(CaptureResult.LENS_FOCUS_DISTANCE)
            if (dioptres != null && dioptres > 0f) exif.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, "${Math.round(1000f / dioptres)}/1000")
        }
        if (!keepLocation) stripLocation(exif)
        exif.saveAttributes()
    }

    /** Whether a recording would carry sound: only while RECORD_AUDIO is held, and never in slow motion (r3 D11, D12). */
    @SuppressLint("MissingPermission")
    fun startVideo(onFinal: (VideoTake) -> Unit): Boolean {
        val video = videoCapture ?: return false
        if (recording) return false
        val slow = if (slowMotion) slowFps else null
        val audio = slow == null && hasMicrophone()
        soundOff = slow == null && !audio
        if (soundOff) Diagnostics.add("camera", "video sound: off (no microphone permission)")
        val file = cacheFile(".mp4")
        stopReason = null
        var pending = video.output.prepareRecording(activity, FileOutputOptions.Builder(file).build())
        if (audio) pending = pending.withAudioEnabled()
        recordingSeconds = 0
        activeRecording = pending.start(main) { event ->
            when (event) {
                is VideoRecordEvent.Status -> recordingSeconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                is VideoRecordEvent.Finalize -> {
                    recording = false
                    activeRecording = null
                    val why = when {
                        event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "storage full"
                        event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "nothing was recorded"
                        event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> stopReason ?: "the camera stopped"
                        event.hasError() -> "recording error ${event.error}"
                        else -> stopReason
                    }
                    onFinal(VideoTake(file, why, audio, slow))
                }
            }
        }
        recording = true
        return true
    }

    /** Stops and finalises the take; [reason] ("screen off", "a call") reaches the take when it is not the user's tap. */
    fun stopVideo(reason: String? = null) {
        val r = activeRecording ?: return
        if (reason != null) { stopReason = reason; Diagnostics.add("camera", "recording stopped: $reason") }
        r.stop()
    }

    val isRecording: Boolean get() = activeRecording != null

    fun release() {
        stopVideo("the camera closed")
        stateListener?.let { old -> stateInfo?.removeCameraStateListener(old) }
        runCatching { provider?.unbindAll() }
        worker.shutdown()
        analysisExecutor.shutdown()
    }

    companion object {
        private val BUSY_CODES = setOf(CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE)
        val QUALITIES: List<Pair<String, Quality>> = listOf("UHD" to Quality.UHD, "FHD" to Quality.FHD, "HD" to Quality.HD, "SD" to Quality.SD)

        /** Removes every GPS tag (a capture for another app carries no location — T17-4). */
        fun stripLocation(exif: ExifInterface) {
            GPS_TAGS.forEach { exif.setAttribute(it, null) }
        }

        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF, ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_DOP, ExifInterface.TAG_GPS_MEASURE_MODE, ExifInterface.TAG_GPS_SATELLITES, ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_VERSION_ID, ExifInterface.TAG_GPS_MAP_DATUM, ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_DEST_BEARING, ExifInterface.TAG_GPS_DEST_BEARING_REF, ExifInterface.TAG_GPS_DEST_DISTANCE, ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        )
    }
}

/** Pure choices over the camera's sizes (JVM-tested). */
object CameraChoices {
    /** Whether [w] × [h] (either way round) is 16:9 ([wide]) or 4:3, within 2 %. */
    fun matchesAspect(w: Int, h: Int, wide: Boolean): Boolean {
        if (w <= 0 || h <= 0) return false
        val ratio = maxOf(w, h).toFloat() / minOf(w, h)
        val want = if (wide) 16f / 9f else 4f / 3f
        return kotlin.math.abs(ratio - want) / want < 0.02f
    }

    /** "JPEG (12 MP)": W10M's wording for an image size, to the megapixel (one decimal under 10 MP). */
    fun sizeLabel(w: Int, h: Int): String {
        val mp = w.toLong() * h / 1_000_000.0
        val text = if (mp >= 9.95) Math.round(mp).toString() else "%.1f".format(Locale.US, mp)
        return "JPEG ($text MP)"
    }

    /** "1920x1080p/30 fps": W10M's wording for a video size. */
    fun videoLabel(w: Int, h: Int, fps: Int = 30): String = "${maxOf(w, h)}x${minOf(w, h)}p/$fps fps"
}
