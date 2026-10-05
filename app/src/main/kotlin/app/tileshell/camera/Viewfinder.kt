package app.tileshell.camera

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Where a capture goes once the camera has written it into the cache. Called on the main thread. */
interface CaptureSink {
    /** Whether a still may carry the phone's location (the Camera's own captures do; a capture for another app never). */
    val keepsLocation: Boolean
    fun photo(shot: PhotoShot, clip: LivingClip.Encoded?, panorama: Boolean)
    fun video(take: VideoTake)
    fun failed(why: String)
}

/**
 * What the viewfinder is doing beyond the engine's own state: which overlays are up, the timer, the toast, and the
 * motions Y6 times. Held above the composition so Back and an intent can act on it.
 */
class ViewfinderState {
    var settingsOpen by mutableStateOf(false)
    var capsuleExpanded by mutableStateOf(false)

    /** The five-ring view (opened by flicking the shutter left), or one control's single arc. */
    var dialOpen by mutableStateOf(false)
    var single: ProControl? by mutableStateOf(null)
    var dialSweep by mutableFloatStateOf(1f)
    var dialLabels by mutableFloatStateOf(1f)

    /** The timer toggle: 0 = off, else seconds. */
    var timerSeconds by mutableIntStateOf(0)
    var countdown: Float? by mutableStateOf(null)       // what is left of the ring, 1 → 0
    var toast: String? by mutableStateOf(null)
    var toastAlpha by mutableFloatStateOf(0f)
    var blackout by mutableStateOf(false)
    var slide by mutableFloatStateOf(0f)                 // the preview's offset in screen widths during a mode switch
    var busySaving by mutableStateOf(false)
    var lastThumb: Bitmap? by mutableStateOf(null)
    var focusMark: Pt? by mutableStateOf(null)
    var panorama: PanoramaCapture? by mutableStateOf(null)

    internal var countdownJob: Job? = null
    internal var toastJob: Job? = null

    /** Back: closes the topmost overlay; false when there is nothing of the viewfinder's to close. */
    fun back(): Boolean = when {
        settingsOpen -> { settingsOpen = false; true }
        panorama != null -> { panorama?.cancel("back"); true }
        countdown != null -> { cancelCountdown(); true }
        dialOpen || single != null -> { dialOpen = false; single = null; true }
        capsuleExpanded -> { capsuleExpanded = false; true }
        else -> false
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown = null
    }

    /** camera-pass2 §5 "Toast": fades in over ≈150 ms, holds ≈850 ms, fades out over ≈180 ms. */
    fun say(scope: CoroutineScope, text: String) {
        toastJob?.cancel()
        // The toast is on screen for about a second: the line lets a row read what it said.
        Diagnostics.add("camera", "toast: $text")
        toast = text
        toastJob = scope.launch {
            MotionClock.animate("camera_toast_in", 150, LinearEasing) { toastAlpha = it }
            delay(850)
            MotionClock.animate("camera_toast_out", 180, LinearEasing) { toastAlpha = 1f - it }
            toast = null
        }
    }
}

private val DISC_FILL = Color(0x4D000000)        // the mode and corner discs: black ≈ 30 % (camera-pass2 §1)
private val CAPSULE_FILL = Color(0x66000000)
private val RING_DARK = Color(0xFF2B2B2B)
private fun glyph(size: Float, color: Color) = TextStyle(fontFamily = Brand.iconFont, fontSize = size.sp, color = color)

/** A box of [size] epx centred on [c]. */
@Composable
internal fun BoxScope.At(c: Pt, size: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.offset((c.x - size / 2f).dp, (c.y - size / 2f).dp).size(size.dp).then(modifier), contentAlignment = Alignment.Center, content = content)
}

/** A node centred on [c] whatever its own size is (a label). */
@Composable
internal fun BoxScope.Centred(c: Pt, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(p.width, p.height) { p.place((c.x.dp.toPx() - p.width / 2f).toInt(), (c.y.dp.toPx() - p.height / 2f).toInt()) }
        },
    ) { content() }
}

internal fun Modifier.tap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        awaitFirstDown().consume()
        val up = waitForUpOrCancellation()
        if (up != null) { up.consume(); onTap() }
    }
}

/**
 * The viewfinder (Approximations Y3, Y6, Y7; build task 6): the preview fitted to the width and centred on the full
 * screen, the 72-epx shutter with the other modes as discs beside it, the capsule on the right edge, settings, roll and
 * switch in their corners — no status bar, no mode strip. [captureKind] is null for the Camera app, or "image" /
 * "video" for a capture another app asked for: one mode, no roll, no settings, no mode discs.
 */
@Composable
fun Viewfinder(engine: CameraEngine, state: ViewfinderState, sink: CaptureSink, captureKind: String? = null, onRoll: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val colors = LocalShellColors.current
    BackHandler(enabled = state.settingsOpen || state.panorama != null || state.countdown != null || state.dialOpen || state.single != null || state.capsuleExpanded) { state.back() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).clipToBounds().testTag("camera_root")) {
        val g = remember(maxWidth, maxHeight) { CameraGeometry(maxWidth.value, maxHeight.value + BarMetrics.NAV_EPX, BarMetrics.NAV_EPX.toFloat()) }
        val pxPerEpx = with(LocalDensity.current) { 1.dp.toPx() }
        val caps = engine.caps
        val (long, short) = engine.previewAspect
        val p = g.preview(long, short)

        // ---- the preview -------------------------------------------------------------------------------------------
        Box(
            Modifier.offset(p.left.dp, p.top.dp).size(p.width.dp, p.height.dp)
                .graphicsLayer { translationX = state.slide * g.w * pxPerEpx }
                .testTag("camera_preview"),
        ) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        engine.attach(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Tap to focus, pinch to zoom (the pinch is P5's on the phone: adb cannot inject two pointers).
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(engine) { detectTransformGestures { _, _, zoomChange, _ -> if (zoomChange != 1f) engine.setZoomRatio(engine.zoom * zoomChange) } }
                    .pointerInput(engine) {
                        detectTapGestures { at ->
                            if (engine.status != CameraStatus.Ready || state.dialOpen || state.single != null) return@detectTapGestures
                            val x = p.left + at.x / pxPerEpx
                            val y = p.top + at.y / pxPerEpx
                            state.focusMark = Pt(x, y)
                            engine.focusAt(at.x, at.y, Math.round(x), Math.round(y)) { scope.launch { delay(600); state.focusMark = null } }
                        }
                    },
            )
            if (engine.settings.grid && engine.status == CameraStatus.Ready) {
                // "Framing grid": two lines each way at thirds of the preview's bounds — 1 epx of white with a thin dark
                // edge on each side, so a line still shows where the scene itself is white (the edge is the agent's;
                // R11 has no capture of W10M's grid).
                Canvas(Modifier.fillMaxSize().testTag("camera_grid")) {
                    val core = 1.dp.toPx()
                    val edged = core + 2f
                    val shade = Color(0x73000000)
                    for (i in 1..2) {
                        val x = size.width * i / 3f
                        val y = size.height * i / 3f
                        drawLine(shade, Offset(x, 0f), Offset(x, size.height), edged)
                        drawLine(shade, Offset(0f, y), Offset(size.width, y), edged)
                    }
                    for (i in 1..2) {
                        val x = size.width * i / 3f
                        val y = size.height * i / 3f
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), core)
                        drawLine(Color.White, Offset(0f, y), Offset(size.width, y), core)
                    }
                }
            }
            // Capture feedback (Y6): the preview cuts to black while the chrome stays.
            if (state.blackout) Box(Modifier.fillMaxSize().background(Color.Black).testTag("camera_blackout"))
        }
        state.focusMark?.let { At(it, 56f, Modifier.border(1.dp, Color.White)) }

        when (val s = engine.status) {
            CameraStatus.Ready -> Unit
            CameraStatus.Starting -> Unit
            CameraStatus.NoPermission -> { GrantPage(engine); return@BoxWithConstraints }
            CameraStatus.NoCamera -> { StatusPage("camera_nocamera", "No camera", "This phone has no camera the shell can use."); return@BoxWithConstraints }
            is CameraStatus.Busy -> StatusPage("camera_busy", "Camera in use", "Another app is using the camera (${s.reason}). It opens again when you come back.")
        }
        val ready = engine.status == CameraStatus.Ready
        val gates = if (caps != null) CameraGates.modes(caps, engine.env) else emptyList()
        val cycle = if (caps != null) CameraGates.cycle(caps, engine.env) else listOf("photo", "video")
        val proAvailable = caps != null && CameraGates.pro(caps).available && !engine.slowMotion && engine.mode != "panorama"
        val fiveRings = state.dialOpen
        val pano = state.panorama

        // ---- the mode list, as semantics (E7 reads exactly the modes this camera admits) -----------------------------
        // Below the top 48 epx: Android's own status-bar window lies over that strip even while it is hidden, and the
        // accessibility framework reports a node wholly under another window as not visible, so a dump would lose it.
        Row(Modifier.align(Alignment.TopStart).offset(y = 50.dp).testTag("camera_modes")) {
            gates.filter { it.available }.forEach { gate ->
                val on = when (gate.id) {
                    "photo", "video", "panorama" -> engine.mode == gate.id && !(gate.id == "video" && engine.slowMotion)
                    "slowmo" -> engine.mode == "video" && engine.slowMotion
                    "hdr" -> engine.hdr
                    "pro" -> state.dialOpen || state.single != null || engine.pro.isNotEmpty()
                    "livingimages" -> engine.settings.livingImages
                    else -> false
                }
                Box(Modifier.size(8.dp).testTag("camera_mode:${gate.id}").semantics { role = Role.Tab; selected = on; contentDescription = gate.id })
            }
        }

        // ---- the dial (Y4), under the shutter ------------------------------------------------------------------------
        if (caps != null && proAvailable && (fiveRings || state.single != null)) ProDial(engine, state, g, caps)

        // ---- panorama's guide (Y12) ----------------------------------------------------------------------------------
        if (engine.mode == "panorama") PanoramaGuide(g, pano)

        // ---- the bottom row ------------------------------------------------------------------------------------------
        val video = engine.mode == "video"
        fun fire() {
            if (!ready) return
            scope.launch {
                state.blackout = true
                MotionClock.animate("capture_feedback", CameraGeometry.CAPTURE_BLACK_MS, LinearEasing) {}
                state.blackout = false
            }
            val wantClip = engine.settings.livingImages && captureKind == null && !engine.hdr && engine.living.size >= 2
            engine.takePhoto(sink.keepsLocation) { shot, why ->
                if (shot == null) { sink.failed(why ?: "capture failed"); return@takePhoto }
                if (!wantClip) { sink.photo(shot, null, false); return@takePhoto }
                Thread {
                    val clip = engine.living.encode(java.io.File(shot.file.parentFile, shot.file.nameWithoutExtension + "_clip.mp4"), shot.rotationDegrees)
                    scope.launch { sink.photo(shot, clip, false) }
                }.start()
            }
        }
        fun shutter() {
            when {
                !ready -> Unit
                video -> {
                    if (engine.isRecording) engine.stopVideo() else engine.startVideo { take -> sink.video(take) }
                }
                engine.mode == "panorama" -> {
                    val running = state.panorama
                    if (running != null) running.finish() else state.panorama = PanoramaCapture(engine, sink) { state.panorama = null }.also { it.start() }
                }
                state.countdown != null -> { state.cancelCountdown(); Diagnostics.add("camera", "timer cancelled by the shutter") }
                state.timerSeconds > 0 -> {
                    val seconds = state.timerSeconds
                    state.countdownJob = scope.launch {
                        // Time lapse: the countdown restarts after each capture until the shutter is pressed (camera-pass2 §1).
                        do {
                            val start = System.nanoTime()
                            while (true) {
                                val left = 1f - (System.nanoTime() - start) / (seconds * 1e9f)
                                if (left <= 0f) break
                                state.countdown = left
                                androidx.compose.runtime.withFrameNanos { }
                            }
                            state.countdown = 0f
                            Diagnostics.add("camera", "timer ${seconds}s -> shutter")
                            fire()
                            if (engine.settings.timeLapse) delay(470)
                        } while (engine.settings.timeLapse)
                        state.countdown = null
                        state.countdownJob = null
                    }
                }
                else -> fire()
            }
        }
        val shutterC = g.shutterCentre(fiveRings)
        At(
            shutterC, CameraGeometry.SHUTTER_DIAMETER,
            Modifier.testTag(if (video) "camera_record" else "camera_shutter").pointerInput(video, proAvailable) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var dx = 0f
                    var up = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        dx = change.position.x - down.position.x
                        change.consume()
                        if (!change.pressed) { up = true; break }
                    }
                    if (!up) return@awaitEachGesture
                    val flick = 28.dp.toPx()
                    when {
                        // 1.4.9: sliding the shutter left stacks all the sliders — the five-ring view.
                        dx < -flick && proAvailable && !video -> scope.launch {
                            state.single = null
                            state.dialSweep = 0f; state.dialLabels = 0f
                            state.dialOpen = true
                            delay(100)
                            MotionClock.animate("dial_open", CameraGeometry.DIAL_SWEEP_MS, LinearEasing) { state.dialSweep = it }
                            MotionClock.animate("dial_labels", CameraGeometry.DIAL_LABEL_MS, LinearEasing) { state.dialLabels = it }
                        }
                        dx > flick && state.dialOpen -> state.dialOpen = false
                        abs(dx) <= flick -> shutter()
                    }
                }
            },
        ) {
            val counting = state.countdown != null
            Canvas(Modifier.fillMaxSize()) {
                val u = 1.dp.toPx()
                // camera-pass2 §4.1: white fill to r 31.9, a #2B2B2B ring at r 32–34, a white ring to r 36.1.
                drawCircle(if (counting) Color(0xFF999999) else Color.White, CameraGeometry.SHUTTER_OUTER_R * u)
                drawCircle(RING_DARK, (CameraGeometry.SHUTTER_RING_INNER + CameraGeometry.SHUTTER_RING_OUTER) / 2f * u, style = Stroke((CameraGeometry.SHUTTER_RING_OUTER - CameraGeometry.SHUTTER_RING_INNER) * u))
            }
            val icon = when {
                video && engine.recording -> Glyph.STOP
                video -> Glyph.VIDEO_FILLED
                engine.mode == "panorama" -> Glyph.PANORAMA
                else -> Glyph.CAMERA_FILLED
            }
            BasicText(icon, style = glyph(24f, if (video && engine.recording) Color(0xFFE81123) else Color.Black))
        }
        if (captureKind == null && !engine.recording && state.countdown == null && pano == null && !fiveRings) {
            val discs = ModeDiscs.of(cycle, engine.mode)
            fun switchTo(target: String, right: Boolean) {
                if (!ready) return
                scope.launch {
                    // Y6: a ≈300-ms slide along the short axis — the old preview out, away from the tapped button; a black
                    // gap; the new one in from the tapped side.
                    val away = if (right) -1f else 1f
                    // The camera is re-bound before the slide starts (binding blocks the main thread for a moment, and
                    // the slide is timed frame by frame); the shutter's glyph and the capsule change with it, as W10M's
                    // did on the first frame, and the preview's shape changes in the black gap.
                    state.capsuleExpanded = false; state.single = null; state.dialOpen = false
                    engine.switchMode(target)
                    MotionClock.animate("mode_switch", CameraGeometry.MODE_SWITCH_MS, LinearEasing) { f ->
                        if (f < 0.5f) state.slide = away * (f / 0.5f)
                        else {
                            if (engine.shownMode != target) engine.shownMode = target
                            state.slide = -away * (1f - (f - 0.5f) / 0.5f)
                        }
                    }
                    engine.shownMode = target
                    state.slide = 0f
                    state.say(scope, when (target) { "video" -> "Video"; "panorama" -> "Panorama"; else -> "Camera" })
                }
            }
            listOf(discs.left to false, discs.right to true).forEach { (id, right) ->
                if (id != null) At(g.discCentre(right), CameraGeometry.DISC_DIAMETER, Modifier.background(DISC_FILL, CircleShape).testTag("camera_disc:$id").tap { switchTo(id, right) }) {
                    BasicText(when (id) { "video" -> Glyph.VIDEO; "panorama" -> Glyph.PANORAMA; else -> Glyph.CAMERA }, style = glyph(16f, Color.White))
                }
            }
        }
        if (captureKind == null && !engine.recording && pano == null) {
            At(g.settingsCentre, CameraGeometry.CORNER_DISC, Modifier.background(DISC_FILL, CircleShape).testTag("camera_settings").tap { state.back(); state.settingsOpen = true }) {
                BasicText(Glyph.SETTINGS, style = glyph(CameraGeometry.GEAR, Color.White))
            }
            At(g.rollCentre, CameraGeometry.CORNER_DISC, Modifier.border(0.7.dp, Color(0xCCFFFFFF)).background(Color(0xFF1F1F1F)).testTag("camera_roll").tap(onRoll)) {
                val thumb = state.lastThumb
                if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else BasicText(Glyph.IMAGE, style = glyph(16f, Color.White))
            }
        }
        if (engine.hasFront && !engine.recording && pano == null) {
            At(g.switchCentre, CameraGeometry.CORNER_DISC, Modifier.background(DISC_FILL, CircleShape).testTag("camera_switch").tap {
                engine.switchCamera(); state.say(scope, if (engine.front) "Front-facing camera" else "Main camera")
            }) { BasicText(Glyph.CAMERA_SWITCH, style = glyph(18f, Color.White)) }
        }

        // ---- the capsule (Y3) ----------------------------------------------------------------------------------------
        if (caps != null && engine.mode != "panorama" && state.countdown == null) {
            Capsule(engine, state, g, caps, proAvailable) { state.say(scope, it) }
        }

        // ---- the zoom slider (camera-pass2 UNMEASURED-3, LOW) and its readout ----------------------------------------
        if (caps != null && CameraGates.zoom(caps).available && pano == null && !fiveRings) {
            val top = g.zoomPlus
            val bottom = g.zoomMinus
            Box(Modifier.offset((top.x - 1.5f).dp, (top.y + 14f).dp).size(3.dp, (bottom.y - top.y - 28f).dp).background(Color(0x80FFFFFF)))
            val f = if (caps.maxZoom > 1f) ((engine.zoom - 1f) / (caps.maxZoom - 1f)).coerceIn(0f, 1f) else 0f
            At(Pt(top.x, bottom.y - 14f - (bottom.y - top.y - 28f) * f), 12f) { Box(Modifier.size(12.dp, 4.dp).background(colors.accent)) }
            At(top, 28f, Modifier.testTag("camera_zoom_in").tap { engine.setZoomRatio(engine.zoom * 1.25f) }) { BasicText(Glyph.ADD, style = glyph(16f, Color.White)) }
            At(bottom, 28f, Modifier.testTag("camera_zoom_out").tap { engine.setZoomRatio(engine.zoom / 1.25f) }) { BasicText(Glyph.SUBTRACT, style = glyph(16f, Color.White)) }
            // A tap on the readout steps 1× → 2× → 4× → … → the camera's maximum → 1× (a route a single pointer can drive).
            Box(Modifier.offset((bottom.x - 20f).dp, (bottom.y + 16f).dp).size(40.dp, 24.dp).tap { engine.cycleZoom() }, contentAlignment = Alignment.Center) {
                BasicText(zoomLabel(engine.zoom), Modifier.testTag("camera_zoom"), style = ShellType.caption.copy(color = Color.White))
            }
        }

        // ---- the timer ring, the recording clock, the hint and the toast ---------------------------------------------
        state.countdown?.let { left ->
            At(Pt(g.w / 2f, g.h / 2f), CameraGeometry.TIMER_RING_DIAMETER, Modifier.testTag("camera_countdown")) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = CameraGeometry.TIMER_RING_STROKE.dp.toPx()
                    // camera-pass2 §5: erased counter-clockwise from 3 o'clock, linear.
                    drawArc(colors.accent, 0f, -360f * left, false, Offset(stroke / 2f, stroke / 2f), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                }
            }
        }
        if (video && engine.recording) {
            // r11/camera.md 1.2.6: "00:00", 35-epx Light, centre 108.75 above the nav bar.
            Centred(Pt(g.w / 2f, g.navTop - 108.75f)) {
                BasicText("%02d:%02d".format(engine.recordingSeconds / 60, engine.recordingSeconds % 60), Modifier.testTag("camera_rec_time"), style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.Light, fontSize = 35.sp, color = Color.White))
            }
        }
        if (video && engine.soundOff && engine.recording) {
            Centred(Pt(g.w / 2f, 60f)) {
                BasicText(SOUND_HINT, Modifier.testTag("camera_sound_hint"), style = ShellType.caption.copy(color = Color.White), maxLines = 1)
            }
        }
        state.toast?.let { text ->
            Centred(Pt(g.w / 2f, g.toastBaseline - 12f), Modifier.graphicsLayer { alpha = state.toastAlpha }) {
                BasicText(text, Modifier.testTag("camera_toast"), style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.Light, fontSize = 35.sp, color = Color.White), maxLines = 1)
            }
        }
        if (state.busySaving) Centred(Pt(g.w / 2f, 78f)) { BasicText("Saving…", Modifier.testTag("camera_saving"), style = ShellType.caption.copy(color = Color.White)) }

        if (state.settingsOpen) CameraSettingsPage(engine) { state.settingsOpen = false }
    }
}

/** r3 D12: a silent take says why, naming the row that fixes it. */
const val SOUND_HINT = "No sound: turn on Microphone in Settings > Setup checklist"

internal fun zoomLabel(ratio: Float): String =
    if (abs(ratio - Math.round(ratio)) < 0.05f) "${Math.round(ratio)}×" else "%.1f×".format(java.util.Locale.US, ratio)

/** One capsule item: its tag suffix, its glyph (or a short text badge), whether it is lit, and what a tap does. */
private class CapsuleItem(val id: String, val glyph: String?, val text: String?, val lit: Boolean, val caption: String? = null, val onTap: () -> Unit)

/**
 * The quick-settings capsule, vertical on the right edge (Y3; camera-pass2 §1). Photo: flash, HDR, timer, chevron.
 * Video: video light, slow motion, chevron. The chevron expands it with the manual controls (the dial's entry, 6a): a
 * tap on one opens its single arc. An item the camera lacks is left out and has its `unavailable` line.
 */
@Composable
private fun BoxScope.Capsule(engine: CameraEngine, state: ViewfinderState, g: CameraGeometry, caps: CameraCaps, proAvailable: Boolean, say: (String) -> Unit) {
    val video = engine.mode == "video"
    val items = ArrayList<CapsuleItem>()
    if (!video) {
        if (caps.hasFlash) items += CapsuleItem("flash", when (engine.flash) { FlashSetting.AUTO -> Glyph.FLASH_AUTO; FlashSetting.ON -> Glyph.FLASH; FlashSetting.OFF -> Glyph.FLASH_OFF }, null, engine.flash != FlashSetting.OFF) { say(engine.cycleFlash().label) }
        if (engine.env.hdrExtension && !state.capsuleExpanded) items += CapsuleItem("hdr", if (engine.hdr) Glyph.HDR else Glyph.HDR_OFF, null, engine.hdr) { say(if (engine.toggleHdr()) "Rich capture on" else "Rich capture off") }
        items += CapsuleItem("timer", if (state.timerSeconds == 0) Glyph.TIMER_OFF else Glyph.TIMER, null, state.timerSeconds != 0, caption = if (state.timerSeconds == 0) null else "${state.timerSeconds}") {
            state.timerSeconds = when (state.timerSeconds) { 0 -> 2; 2 -> 5; else -> 0 }
            say(when (state.timerSeconds) { 2 -> "2-second timer"; 5 -> "5-second timer"; else -> "Timer off" })
        }
    } else {
        if (caps.hasFlash) items += CapsuleItem("light", if (engine.videoLight) Glyph.FLASHLIGHT else Glyph.FLASHLIGHT_OFF, null, engine.videoLight) { say(if (engine.toggleVideoLight()) "Video light on" else "Video light off") }
        if (CameraGates.slowmo(engine.env).available) items += CapsuleItem("slowmo", Glyph.SLOW_MOTION, null, engine.slowMotion) {
            state.capsuleExpanded = false; state.single = null
            say(if (engine.toggleSlowMotion()) "Slow motion on" else "Slow motion off")
        }
    }
    if (proAvailable && state.capsuleExpanded) {
        // camera-pass2 §1 "Expanded capsule": WB, focus, ISO, shutter, exposure, with each one's value under it.
        listOf(ProControl.WB, ProControl.FOCUS, ProControl.ISO, ProControl.SHUTTER, ProControl.EXPOSURE).forEach { control ->
            if (CameraGates.proControls(caps).first { it.first == control }.second.available) {
                val value = ProValues.labels(caps, control).getOrNull(ProValues.dialIndex(caps, control, engine.pro[control])).orEmpty()
                items += CapsuleItem("pro:${control.id}", proGlyph(control), proBadge(control), state.single == control || engine.pro.containsKey(control), caption = value) {
                    state.dialOpen = false
                    state.single = if (state.single == control) null else control
                }
            }
        }
    }
    if (proAvailable) items += CapsuleItem("more", if (state.capsuleExpanded) Glyph.CHEVRON_UP else Glyph.CHEVRON_DOWN, null, false) {
        state.capsuleExpanded = !state.capsuleExpanded
        if (!state.capsuleExpanded) state.single = null
    }
    if (items.isEmpty()) return
    val expanded = state.capsuleExpanded && proAvailable
    val centres = if (expanded) expandedOffsets(items.size).map { Pt(g.w - 24f, g.h / 2f + it) } else g.capsuleGlyphs(items.size)
    val box = if (expanded) Box(g.w - 47f, centres.first().y - 26f, g.w - 2f, centres.last().y + 26f) else g.capsule(items.size)
    Box(Modifier.offset(box.left.dp, box.top.dp).size(box.width.dp, box.height.dp).background(CAPSULE_FILL, RoundedCornerShape(50)).testTag("camera_capsule"))
    items.forEachIndexed { i, item ->
        At(centres[i], 40f, Modifier.testTag("camera_capsule:${item.id}").semantics { role = Role.Tab; selected = item.lit }.tap(item.onTap)) {
            val ink = if (item.lit || item.id == "more" || !expanded) Color.White else Color(0xFF7B7B7D)
            if (item.glyph != null) BasicText(item.glyph, style = glyph(if (item.id == "more") 12f else 20f, ink))
            else BasicText(item.text.orEmpty(), style = ShellType.caption.copy(color = ink, fontWeight = FontWeight.SemiBold))
        }
        if (item.caption != null) Centred(Pt(centres[i].x, centres[i].y + 18f)) {
            BasicText(item.caption, Modifier.testTag("camera_capsule_value:${item.id}"), style = TextStyle(fontFamily = Brand.uiFont, fontSize = 10.sp, color = Color(0xFF878787)), maxLines = 1)
        }
    }
}

/** camera-pass2 §1: the eight-item expanded capsule's measured centres; another count keeps its 46.7-epx pitch. */
private fun expandedOffsets(n: Int): List<Float> =
    if (n == 8) listOf(-163.2f, -119.5f, -73.5f, -25.5f, 22.4f, 70.3f, 118.4f, 163.9f) else (0 until n).map { (it - (n - 1) / 2f) * 46.7f }

internal fun proGlyph(control: ProControl): String? = when (control) {
    ProControl.EXPOSURE -> Glyph.EXPOSURE
    ProControl.SHUTTER -> Glyph.CLOCK
    ProControl.FOCUS -> Glyph.FOCUS
    else -> null
}

internal fun proBadge(control: ProControl): String? = when (control) {
    ProControl.WB -> "WB"
    ProControl.ISO -> "ISO"
    else -> null
}

@Composable
private fun BoxScope.StatusPage(tag: String, title: String, body: String, content: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(Color.Black).testTag(tag).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }) {
        androidx.compose.foundation.layout.Column(Modifier.align(Alignment.Center).width(312.dp)) {
            BasicText(title, Modifier.testTag("${tag}_title"), style = ShellType.title.copy(color = Color.White))
            BasicText(body, Modifier.offset(y = 8.dp).testTag("${tag}_text"), style = ShellType.body.copy(color = Color(0xFF999999)))
            Box(Modifier.size(1.dp, 24.dp))
            content()
        }
    }
}

/**
 * CAMERA is not held: the page says so, names the Setup checklist row, and offers Android's own dialog here, where
 * the empty state is (phase 10 task 10's pattern). After "don't ask again" the button opens the app's settings page.
 */
@Composable
private fun BoxScope.GrantPage(engine: CameraEngine) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    var asked by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        Diagnostics.add("camera", "camera permission request: ${if (granted) "granted" else "denied"}")
        asked = true
        engine.start()
    }
    StatusPage("camera_grant", "Camera", "The camera is off for the shell. Turn on Camera in Settings > Setup checklist, or allow it here.") {
        val colors = LocalShellColors.current
        Box(
            Modifier.size(120.dp, 32.dp).background(colors.accent).testTag("camera_grant_button").tap {
                val blocked = asked && activity != null && !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)
                if (blocked) {
                    Diagnostics.add("camera", "camera permission: opening the app's settings page")
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
                } else {
                    launcher.launch(android.Manifest.permission.CAMERA)
                }
            },
            contentAlignment = Alignment.Center,
        ) { BasicText("allow", style = ShellType.body.copy(color = Color.White)) }
    }
}

/** The small square thumbnail the roll shows, decoded from a capture still in the cache. */
internal fun thumbnailOf(file: java.io.File, rotationDegrees: Int, edge: Int = 144, maxLong: Int? = null): Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= edge) sample *= 2
    val raw = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val upright = if (rotationDegrees == 0) raw
    else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
    val long = maxOf(upright.width, upright.height)
    if (maxLong == null || long <= maxLong) upright
    else Bitmap.createScaledBitmap(upright, maxOf(1, upright.width * maxLong / long), maxOf(1, upright.height * maxLong / long), true)
}.getOrNull()
