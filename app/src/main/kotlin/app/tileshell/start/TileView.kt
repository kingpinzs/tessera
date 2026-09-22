package app.tileshell.start

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tileshell.brand.Brand
import app.tileshell.cortana.ui.drawLensDisc
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.PressStyle
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import app.tileshell.tiles.engine.Transport
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.ShellType
import app.tileshell.ui.tokens.StartGrid
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import app.tileshell.tiles.ShellTiles
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Everything a tile needs to draw itself. */
data class TileModel(
    val id: String,
    val label: String,
    val size: TileSize,
    val icon: TileIcons.Icon?,
    val fallbackGlyph: String?,
    val content: TileContent?,
    val badge: Int,
    val unassigned: Boolean,
    /** Phase 02: a live folder draws its members' mini tiles instead of one icon (R6 §1.6.3-§1.6.4). */
    val folder: FolderFace? = null,
    /**
     * Phase 03: a shell tile that draws its own face instead of an icon. Cortana's is a static
     * logo/ring (Decisions "Cortana tile"): A11 buys no news internet use, so W10M's headline back face
     * in R3 C3 is out, and the ring geometry comes from R3 A22 at MEDIUM. The composition itself is an
     * approximation with its own row (H7).
     */
    val shellFace: String? = null,
)

/** One member of a folder, drawn as a mini tile on the folder's face (R6 §1.6.3, H12). */
data class MiniTile(val icon: TileIcons.Icon?, val glyph: String?)

/**
 * A folder tile's face (phase 02): the member mini tiles, or the "^" chevron while the folder is expanded
 * (R6 §1.6.5, H14). A wide folder puts the mini grid on the left and one member's live content on the right
 * with a numeric badge (R6 §1.6.4, H13).
 */
data class FolderFace(val minis: List<MiniTile>, val expanded: Boolean, val count: Int, val live: TileContent? = null)

/**
 * One Start tile: accent background (translucent over a Start background, R3 A4), glyph, caption label
 * (R3 A2), badge at the bottom-right (R1 §1.4), and live faces on its own random timer (R3 A7 / A8).
 */
@Composable
fun TileView(
    model: TileModel,
    widthDp: Dp,
    heightDp: Dp,
    accent: Color,
    tileAlpha: Float,
    pressStyle: PressStyle,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    /** Edit-mode dimming of everything but the held tile (R6 §1.1.5-§1.1.6); Unspecified = no dim. */
    dim: Color = Color.Unspecified,
    /** False in edit mode: Start owns the gestures there, so a tile never launches or tilts under a drag. */
    interactive: Boolean = true,
    /** Folder-create feedback: the tile holds still and lights up while a tile dwells on it (R6 §1.6.1, H10). */
    folderTarget: Boolean = false,
    /** A resize hides the tile's content while the rectangle changes size, leaving the accent fill (R6 §1.4). */
    contentAlpha: Float = 1f,
    /**
     * A transport control on the tile was tapped (INDEX Change Log 2026-09-21 item 3). Only a face that
     * CARRIES controls can produce one, so every other tile in the shell is untouched by this.
     */
    onControl: (Transport) -> Unit = {},
) {
    val faces = model.content?.faces.orEmpty()
    // Face 0 is the logo face; 1..n are live faces.
    var faceIndex by remember(model.id) { mutableIntStateOf(0) }
    // One face change = one continuous animation (R3 A7 counts 6-7 frames for a 108-ms flip): progress 0..1, the face
    // swaps at the midpoint; a flip squashes with |1 - 2p|, a crossfade fades with the same shape.
    val cycle = remember(model.id) { Animatable(0f) }
    // Peek slide: 0..1 travel of the outgoing face; slideFrom is the face sliding out (-1 when no slide is running).
    val slide = remember(model.id) { Animatable(0f) }
    var slideFrom by remember(model.id) { mutableIntStateOf(-1) }
    // The face fading out during a crossfade (-1 when none).
    var fadeFrom by remember(model.id) { mutableIntStateOf(-1) }
    var slideDown by remember(model.id) { mutableStateOf(true) }
    val transition = model.content?.transition ?: FaceTransition.FLIP

    // Render-latency diagnostics for E5: log once per new source time.
    val sourceTime = model.content?.sourceTimeMs ?: 0L
    SideEffect {
        if (sourceTime > 0L && lastLoggedSource[model.id] != sourceTime) {
            lastLoggedSource[model.id] = sourceTime
            Diagnostics.add("render", "tile=${model.id} sourceTime=$sourceTime renderedAt=${System.currentTimeMillis()} faces=${faces.size}")
        }
    }

    // A slideshow advances on its own fixed cadence instead of R3 A8's random band (INDEX item 4).
    val slideshowMs = model.content?.slideshowMs ?: 0

    // Battery (constraint 3), the WeatherSkyFace pattern: a slideshow runs only while Start is RESUMED and
    // this tile's own bounds are on screen. It is the slideshow that is gated and not every tile's flip,
    // because R3 A8's 5-second band is the measured W10M behaviour this build is judged against (E10) and
    // the slideshow is the new, four-times-faster animation this work adds.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var onScreen by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, slideshowMs) {
        if (slideshowMs <= 0) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val cycling = faces.isNotEmpty() && (slideshowMs <= 0 || (resumed && onScreen))

    LaunchedEffect(model.id, faces.size, transition, slideshowMs, cycling) {
        // A content change cancels any running flip / crossfade; restore full visibility first so a tile is never
        // left squashed or faded out until its next cycle.
        cycle.snapTo(0f)
        slide.snapTo(0f)
        slideFrom = -1
        fadeFrom = -1
        if (faceIndex > faces.size) faceIndex = 0
        if (faces.isEmpty()) { faceIndex = 0; return@LaunchedEffect }
        // A paused slideshow holds the photo it stopped on; it does not rewind and it does not race
        // through the photos it missed when the tile comes back.
        if (!cycling) return@LaunchedEffect
        // Peek tiles run on the flip tiles' timer band (R3 A8 has no separate band for them).
        val (min, max) = if (transition == FaceTransition.CROSSFADE)
            Motion.CROSSFADE_PERIOD_MIN_MS to Motion.CROSSFADE_PERIOD_MAX_MS else Motion.FLIP_PERIOD_MIN_MS to Motion.FLIP_PERIOD_MAX_MS
        delay(TileTiming.startPhaseMs(slideshowMs, max) { Random.nextLong(0, it) }) // random start phase (R3 A8)
        while (true) {
            // R3 A8 periods are start-to-start: the wait after an animation is the period less the animation's own time.
            val startedAt = SystemClock.uptimeMillis()
            val period = TileTiming.periodMs(slideshowMs, min, max) { lo, hi -> Random.nextLong(lo, hi) }
            val next = TileTiming.faceAfter(faceIndex + 1, faces.size)
            Diagnostics.add("tile_anim", "tile=${model.id} kind=$transition uptime=$startedAt faceIndex=$faceIndex next=$next faces=${faces.size}")
            when (transition) {
                FaceTransition.FLIP -> {
                    cycle.snapTo(0f)
                    cycle.animateTo(1f, tween(Motion.FLIP_MS, easing = LinearEasing)) { if (value >= 0.5f) faceIndex = next }
                    faceIndex = next
                    cycle.snapTo(0f)
                }
                FaceTransition.CROSSFADE -> {
                    // A true cross-dissolve: the outgoing face fades out while the next fades in, so the tile's
                    // accent plate, label and badge never blink out (R3 A7 "image -> image crossfade").
                    cycle.snapTo(0f)
                    fadeFrom = faceIndex
                    faceIndex = next
                    cycle.animateTo(1f, tween(Motion.CROSSFADE_MS, easing = LinearEasing))
                    fadeFrom = -1
                    cycle.snapTo(0f)
                }
                FaceTransition.PEEK -> {
                    slideDown = next != 0 && faces[next - 1] is TileFace.Photo
                    slide.snapTo(0f)
                    slideFrom = faceIndex
                    faceIndex = next
                    slide.animateTo(1f, keyframes {
                        durationMillis = Motion.PEEK_MS
                        Motion.peekKeyframes.forEach { (t, v) -> v at t using LinearEasing }
                    })
                    slideFrom = -1
                }
            }
            delay(TileTiming.remainingMs(period, SystemClock.uptimeMillis() - startedAt))
        }
    }

    // Which face is on show right now, so the tile knows whether it is carrying controls. Face 0 is the
    // logo, or the front face for a tile whose front IS its content (Weather, Music while playing, a
    // picture frame); 1..n are the live faces.
    fun faceAt(index: Int): TileFace? = when {
        model.folder != null -> null
        index == 0 || faces.isEmpty() || index > faces.size -> model.content?.front
        else -> faces[index - 1]
    }
    val shownFace = faceAt(faceIndex) as? TileFace.NowPlaying
    // A small tile has no room for three tap targets, so it carries none and behaves like any other tile.
    val controls = if (model.size == TileSize.SMALL || !interactive) emptyList() else shownFace?.controls.orEmpty()
    var pressedControl by remember(model.id) { mutableIntStateOf(-1) }

    // Press styles (Q6).
    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val depress = remember { Animatable(1f) }
    val density = LocalDensity.current

    Box(
        modifier
            .size(widthDp, heightDp)
            .testTag("tile:${model.id}")
            .semantics { contentDescription = model.label }
            .graphicsLayer {
                rotationX = tiltX.value
                rotationY = tiltY.value
                scaleX = depress.value
                // The flip squashes the whole tile; a crossfade fades only the face, so the accent plate, the label
                // and the badge stay put instead of the tile blinking out (R1 section 1.4, review finding).
                scaleY = depress.value * (if (transition == FaceTransition.FLIP) kotlin.math.abs(1f - 2f * cycle.value) else 1f)
                cameraDistance = 12f * density.density
            }
            .let { if (slideshowMs > 0) it.onGloballyPositioned { c -> onScreen = !c.boundsInWindow().isEmpty } else it }
            .pointerInput(pressStyle, model.id, interactive, controls) {
                if (!interactive) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // A CONTROL TAP NEVER REACHES THE LAUNCH PATH. The decision is made here, in the one
                    // handler that owns the tile's touches, against a pure function ([TileControls]) — not
                    // by nesting a second pointerInput inside the tile and trusting consumption order. The
                    // whole gesture returns before the press style, the Start exit and onTap are reached,
                    // so a tap that lands on pause cannot start the music app.
                    val hit = TileControls.hitTest(
                        down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat(), controls.size,
                    )
                    if (hit != null) {
                        down.consume()
                        pressedControl = hit
                        val release = waitForUpOrCancellation()
                        release?.consume()
                        pressedControl = -1
                        // A finger that slid off the control it started on is a change of mind, not a
                        // command, and it must not fall through to a launch either.
                        val stillOn = release != null && TileControls.hitTest(
                            release.position.x, release.position.y, size.width.toFloat(), size.height.toFloat(), controls.size,
                        ) == hit
                        if (stillOn) {
                            Diagnostics.add("tile_control", "tile=${model.id} ${controls[hit]} (no launch)")
                            onControl(controls[hit])
                        }
                        return@awaitEachGesture
                    }
                    pressed = true
                    touch = down.position
                    when (pressStyle) {
                        PressStyle.NONE -> Unit
                        PressStyle.WP8_TILT -> scope.launch {
                            val cx = size.width / 2f; val cy = size.height / 2f
                            val nx = ((touch.x - cx) / cx).coerceIn(-1f, 1f); val ny = ((touch.y - cy) / cy).coerceIn(-1f, 1f)
                            val maxDeg = Math.toDegrees(Motion.TILT_MAX_ANGLE_RAD.toDouble()).toFloat()
                            launch { tiltY.snapTo(nx * maxDeg) }
                            launch { tiltX.snapTo(-ny * maxDeg) }
                            val depth = Motion.TILT_MAX_DEPRESSION_EPX / (size.width / density.density)
                            depress.snapTo(1f - depth * (1f - maxOf(kotlin.math.abs(nx), kotlin.math.abs(ny))) * 0.5f)
                        }
                        PressStyle.P4_PRESS -> scope.launch { depress.snapTo(P4_PRESS_SCALE) }
                    }
                    val up = waitForUpOrCancellation()
                    pressed = false
                    scope.launch {
                        if (pressStyle == PressStyle.WP8_TILT) delay(Motion.TILT_RETURN_DELAY_MS.toLong())
                        launch { tiltX.animateTo(0f, tween(Motion.TILT_RETURN_MS, easing = LinearEasing)) }
                        launch { tiltY.animateTo(0f, tween(Motion.TILT_RETURN_MS, easing = LinearEasing)) }
                        depress.animateTo(1f, tween(Motion.TILT_RETURN_MS, easing = LinearEasing))
                    }
                    if (up != null) onTap()
                }
            }
            .background(accent.copy(alpha = accent.alpha * tileAlpha))
            .clipToBounds(),
    ) {
        @Composable
        fun Face(index: Int) {
            val folder = model.folder
            val front = model.content?.front
            if (folder != null) FolderFaceView(model, folder, widthDp, heightDp)
            // A tile whose front IS its content (Weather, see TileContent.front) draws that instead of its logo.
            else if (index == 0 || faces.isEmpty() || index > faces.size) {
                if (front != null) LiveFace(front, model, widthDp, heightDp) else LogoFace(model, widthDp, heightDp)
            } else LiveFace(faces[index - 1], model, widthDp, heightDp)
        }
        val outgoing = slideFrom
        if (contentAlpha < 1f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) { Face(faceIndex) }
        } else if (fadeFrom >= 0) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - cycle.value }) { Face(fadeFrom) }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = cycle.value }) { Face(faceIndex) }
        } else if (outgoing >= 0) {
            // R3 A7 peek: the outgoing face travels one tile height on the measured ease-out; the next face follows it in.
            val direction = if (slideDown) 1f else -1f
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = direction * slide.value * size.height }) { Face(outgoing) }
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = direction * (slide.value - 1f) * size.height }) { Face(faceIndex) }
        } else {
            Face(faceIndex)
        }
        // The transport strip owns the bottom of the tile while it is there, so the badge (bottom-right)
        // does not share a corner with the skip control.
        if (controls.isEmpty()) Badge(model) else TransportStrip(model.id, controls, shownFace?.playing == true, pressedControl)
        if (pressStyle == PressStyle.P4_PRESS && pressed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = P4_PRESS_DIM)))
        }
        // R6 §1.6.1 (H10): the tile a dragged tile is dwelling on stays highlighted behind it.
        if (folderTarget) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.25f)))
        // Edit-mode dimming is drawn over the whole tile, faces, label and badge alike (R6 §1.1.5).
        if (dim != Color.Unspecified && dim.alpha > 0f) Box(Modifier.fillMaxSize().background(dim).testTag("dim:${model.id}"))
    }
}

/** P4 press style numbers (designed at build start, recorded in the INDEX change log; judged in H8). */
const val P4_PRESS_SCALE = 0.97f
const val P4_PRESS_DIM = 0.12f

private val lastLoggedSource = HashMap<String, Long>()

@Composable
private fun LogoFace(model: TileModel, widthDp: Dp, heightDp: Dp) {
    val showLabel = model.size != TileSize.SMALL
    val iconSize = when (model.size) {
        // Sized from the shorter side, so a stretched bottom-row tile keeps a small-tile glyph.
        TileSize.SMALL -> minOf(widthDp, heightDp) * 0.52f
        TileSize.MEDIUM -> widthDp * 0.42f
        TileSize.WIDE -> heightDp * 0.42f
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.Center).size(iconSize), contentAlignment = Alignment.Center) {
            val icon = model.icon
            if (model.shellFace == ShellTiles.CORTANA) {
                CortanaTileFace(Modifier.fillMaxSize())
            } else if (icon != null) {
                Image(icon.bitmap, contentDescription = null, modifier = Modifier.fillMaxSize().let { if (icon.monochrome) it else it.padding(iconSize * 0.08f) })
            } else if (model.fallbackGlyph != null) {
                BasicText(model.fallbackGlyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = (iconSize.value * 0.8f).sp, color = Color.White, textAlign = TextAlign.Center))
            }
        }
        if (showLabel) {
            val label = if (model.unassigned) "${model.label} · Tap to choose" else model.label
            BasicText(
                label,
                style = ShellType.caption.copy(color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = StartGrid.LABEL_INSET_EPX.dp, end = 28.dp, bottom = 5.dp),
            )
        }
    }
}

/** The count in the tile's lower-right corner (R1 section 1.4), drawn over whichever face is showing. */
@Composable
private fun BoxScope.Badge(model: TileModel) {
    if (model.badge <= 0) return
    BasicText(
        if (model.badge > 99) "99+" else model.badge.toString(),
        style = ShellType.caption.copy(color = Color.White, fontWeight = FontWeight.Normal),
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = StartGrid.LABEL_INSET_EPX.dp, bottom = 5.dp).testTag("badge:${model.id}"),
    )
}

@Composable
internal fun LiveFace(face: TileFace, model: TileModel, widthDp: Dp, heightDp: Dp) {
    val white = Color.White
    // A face carrying a transport gives the bottom third of the tile to the strip, so the tile label
    // would sit behind it; the track's own title is right there instead.
    val hasControls = (face as? TileFace.NowPlaying)?.controls?.isNotEmpty() == true
    Box(Modifier.fillMaxSize()) {
        when (face) {
            is TileFace.TextLines -> Column(Modifier.padding(start = 7.5.dp, top = 6.dp, end = 8.dp)) {
                // R3 C3 Mail tile: caption-class lines, 16-epx pitch, up to 4 lines.
                // Line count follows the tile's height (16-epx pitch), so a bottom-row tile shows as many lines as fit.
                face.lines.flatMap { it.split('\n') }.take(((heightDp.value - 8f) / 16f).toInt().coerceIn(0, 4)).forEach {
                    BasicText(it, style = ShellType.caption.copy(color = white, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            is TileFace.Photo -> {
                PhotoFill(face.image)
                if (face.overlayLines.isNotEmpty()) Column(Modifier.background(Color.Black.copy(alpha = 0.35f)).fillMaxWidth().padding(start = 7.5.dp, top = 6.dp)) {
                    face.overlayLines.take(4).forEach { BasicText(it, style = ShellType.caption.copy(color = white), maxLines = 1) }
                }
            }
            is TileFace.CalendarDay -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (face.eventLines.isNotEmpty() && model.size == TileSize.WIDE) {
                    Column(Modifier.fillMaxWidth().padding(start = 7.5.dp, top = 6.dp)) {
                        face.eventLines.take(3).forEach { BasicText(it, style = ShellType.caption.copy(color = white, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                } else {
                    // R3 C3: day name (body) top at 33 epx; day number ≈43-epx Light, top at 50 epx; centred.
                    Box(Modifier.padding(top = 29.dp)) { BasicText(face.dayName, style = ShellType.body.copy(color = white)) }
                    BasicText(face.dayNumber, style = ShellType.header.copy(fontSize = 43.sp, lineHeight = 44.sp, color = white))
                }
            }
            is TileFace.WeatherNow -> WeatherNowFace(face, model)
            is TileFace.WeatherDays -> Column(Modifier.padding(start = 7.5.dp, top = 8.dp)) {
                androidx.compose.foundation.layout.Row {
                    face.days.take(if (model.size == TileSize.WIDE) 3 else 2).forEach { (day, glyph, temps) ->
                        Column(Modifier.size(width = 75.dp, height = 60.dp)) {
                            BasicText(day, style = ShellType.caption.copy(color = white))
                            BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 20.sp, color = white))
                            BasicText(temps, style = ShellType.caption.copy(color = white))
                        }
                    }
                }
                face.stale?.let { BasicText(it, style = ShellType.caption.copy(color = white), maxLines = 1) }
            }
            is TileFace.NowPlaying -> {
                face.art?.let { PhotoFill(it) }
                Column(Modifier.align(Alignment.TopStart).padding(start = 7.5.dp, top = 6.dp)) {
                    BasicText(face.title, style = ShellType.caption.copy(color = white), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(face.artist, style = ShellType.caption.copy(color = white), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (model.size != TileSize.SMALL && !hasControls) {
            BasicText(model.label, style = ShellType.caption.copy(color = white), maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = StartGrid.LABEL_INSET_EPX.dp, bottom = 5.dp))
        }
    }
}

/**
 * The transport strip inside a tile (INDEX Change Log 2026-09-21 item 3, Jeremy's "play pauese stop skip").
 *
 * Its geometry is [TileControls] and nothing else — the same fractions the hit test uses, so what is drawn
 * and what is touchable cannot drift apart. The cells take no pointer input of their own: the tile's one
 * gesture handler decides, and these are the picture plus the test tags a device pass reads.
 *
 * The glyphs are drawn rather than typed: the icon font this shell ships carries no transport glyphs, and
 * a triangle, two bars and a square scale from the cell they sit in with no asset and no new dependency —
 * the same call [CortanaTileFace] and the weather scenes make.
 */
@Composable
private fun BoxScope.TransportStrip(tileId: String, controls: List<Transport>, playing: Boolean, pressedIndex: Int) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(TileControls.STRIP_FRACTION)
            // A scrim, because the face behind it can be any album cover at all.
            .background(Color.Black.copy(alpha = TileControls.SCRIM_ALPHA))
            .testTag("tile_controls:$tileId"),
    ) {
        controls.forEachIndexed { index, transport ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The only press feedback a control has; the tile's own press style belongs to the
                    // tile, and a control that did nothing visible until the music reacted feels dead.
                    .background(if (index == pressedIndex) Color.White.copy(alpha = CONTROL_PRESS_ALPHA) else Color.Transparent)
                    .testTag("tile_control:$tileId:${transport.name}")
                    .semantics { contentDescription = transportLabel(transport, playing) },
            ) {
                Canvas(Modifier.fillMaxSize()) { drawTransport(transport, playing) }
            }
        }
    }
}

/** What a control is called, for the QA dump and for a screen reader. */
private fun transportLabel(transport: Transport, playing: Boolean): String = when (transport) {
    Transport.PLAY_PAUSE -> if (playing) "Pause" else "Play"
    Transport.STOP -> "Stop"
    Transport.NEXT -> "Next"
}

/** The white overlay on the control under the finger. */
private const val CONTROL_PRESS_ALPHA = 0.22f

/** One transport glyph, centred in its own cell and sized from it (never in pixels). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTransport(transport: Transport, playing: Boolean) {
    val side = minOf(size.width, size.height) * TileControls.GLYPH_FRACTION
    val half = side / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    when (transport) {
        Transport.PLAY_PAUSE -> if (playing) {
            val bar = side * 0.32f
            drawRect(Color.White, topLeft = Offset(cx - half, cy - half), size = Size(bar, side))
            drawRect(Color.White, topLeft = Offset(cx + half - bar, cy - half), size = Size(bar, side))
        } else {
            drawPath(rightTriangle(cx - half * 0.85f, cy - half, side), Color.White)
        }
        Transport.STOP -> drawRect(Color.White, topLeft = Offset(cx - half, cy - half), size = Size(side, side))
        Transport.NEXT -> {
            val bar = side * 0.22f
            drawPath(rightTriangle(cx - half, cy - half, side * 0.9f), Color.White)
            drawRect(Color.White, topLeft = Offset(cx + half - bar, cy - half), size = Size(bar, side))
        }
    }
}

/** A play triangle whose bounding box starts at ([left], [top]) and is [side] tall. */
private fun rightTriangle(left: Float, top: Float, side: Float): Path = Path().apply {
    moveTo(left, top)
    lineTo(left, top + side)
    lineTo(left + side * 0.87f, top + side / 2f)
    close()
}

/**
 * The Weather tile's current-conditions face: the animated sky (INDEX Change Log 2026-09-21 item 2) with the
 * day's numbers over it. [WeatherSkyFace] draws the weather; this draws what it is.
 *
 * The text is laid out from the tile's own size rather than one fixed stack, because the same face has to
 * read at 1 unit, 2x2 and 4x2. A small tile has room for the temperature and nothing else — that is the one
 * number that has to survive at every size — so the place, the condition word and the detail line drop as the
 * tile shrinks instead of being clipped in half. The stale line (X22, "Updated h:mm") outranks the details:
 * E9 reads it off the tile, and a wrong-looking temperature with no explanation is worse than no wind speed.
 */
@Composable
private fun WeatherNowFace(face: TileFace.WeatherNow, model: TileModel) {
    val white = Color.White
    Box(Modifier.fillMaxSize()) {
        face.sky?.let { WeatherSkyFace(it, Modifier.fillMaxSize()) }
        if (model.size == TileSize.SMALL) {
            BasicText(
                face.temperature,
                style = ShellType.subheader.copy(fontSize = 20.sp, lineHeight = 24.sp, color = white),
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 7.5.dp),
            )
            return@Box
        }
        val wide = model.size == TileSize.WIDE
        Column(Modifier.padding(start = 7.5.dp, top = 6.dp, end = 6.dp)) {
            // Line 1 is the place when the feed knows it (W10M's weather tile named the city), and the
            // condition word otherwise, so the line is never empty and the word is never lost.
            BasicText(face.place ?: face.condition, style = ShellType.caption.copy(color = white), maxLines = 1, overflow = TextOverflow.Ellipsis)
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    face.temperature,
                    style = ShellType.subheader.copy(fontSize = if (wide) 34.sp else 30.sp, lineHeight = if (wide) 40.sp else 36.sp, color = white),
                    maxLines = 1,
                )
                if (face.place != null) {
                    BasicText(
                        face.condition,
                        style = ShellType.caption.copy(color = white),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    )
                }
            }
            val third = face.stale ?: face.details.joinToString("  ").takeIf { it.isNotEmpty() && wide }
            third?.let { BasicText(it, style = ShellType.caption.copy(color = white), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun PhotoFill(image: ImageBitmap) {
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/**
 * Tess's static tile face (Decisions "Cortana tile", H7): the persona's ring at R3 A22's proportions —
 * outer 70, inner 48, stroke 11 epx — scaled into whatever the tile gives it, and since 2026-09-21 lit
 * as HAL 9000's lens rather than filled white. The geometry is A22's either way. It does not animate: a
 * live face on Start would need a feed, and Tess has none.
 *
 * The tile is the one place the lens is drawn SOLID: at tile size the ring reads as a hoop rather than
 * an eye, and the tile already carries the accent behind it, so the eye needs the dark body to sit in.
 */
@Composable
private fun CortanaTileFace(modifier: Modifier = Modifier) {
    Canvas(modifier.testTag("cortana_tile_face")) {
        val outer = size.minDimension
        // A22's ratios: stroke 11 / outer 70. The bezel takes the ring's band, the lens fills the hole.
        val stroke = outer * (11f / 70f)
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(TILE_LENS_BEZEL, radius = outer / 2f, center = centre)
        drawLensDisc(centre, outer - stroke * 2f, Color.White, 0f)
    }
}

/** The lens body the tile's eye sits in: dark enough to read as an eye on any accent. */
private val TILE_LENS_BEZEL = Color(0xFF14110F)
