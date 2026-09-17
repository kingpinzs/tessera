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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import app.tileshell.brand.Brand
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.PressStyle
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.ShellType
import app.tileshell.ui.tokens.StartGrid
import kotlinx.coroutines.delay
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
)

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

    LaunchedEffect(model.id, faces.size, transition) {
        // A content change cancels any running flip / crossfade; restore full visibility first so a tile is never
        // left squashed or faded out until its next cycle.
        cycle.snapTo(0f)
        slide.snapTo(0f)
        slideFrom = -1
        fadeFrom = -1
        if (faceIndex > faces.size) faceIndex = 0
        if (faces.isEmpty()) { faceIndex = 0; return@LaunchedEffect }
        // Peek tiles run on the flip tiles' timer band (R3 A8 has no separate band for them).
        val (min, max) = if (transition == FaceTransition.CROSSFADE)
            Motion.CROSSFADE_PERIOD_MIN_MS to Motion.CROSSFADE_PERIOD_MAX_MS else Motion.FLIP_PERIOD_MIN_MS to Motion.FLIP_PERIOD_MAX_MS
        delay(Random.nextLong(0, max)) // random start phase
        while (true) {
            // R3 A8 periods are start-to-start: the wait after an animation is the period less the animation's own time.
            val startedAt = SystemClock.uptimeMillis()
            val period = Random.nextLong(min, max + 1)
            val next = (faceIndex + 1) % (faces.size + 1)
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
            delay((period - (SystemClock.uptimeMillis() - startedAt)).coerceAtLeast(0L))
        }
    }

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
            .pointerInput(pressStyle, model.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
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
            if (index == 0 || faces.isEmpty() || index > faces.size) LogoFace(model, widthDp, heightDp) else LiveFace(faces[index - 1], model, heightDp)
        }
        val outgoing = slideFrom
        if (fadeFrom >= 0) {
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
        Badge(model)
        if (pressStyle == PressStyle.P4_PRESS && pressed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = P4_PRESS_DIM)))
        }
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
            if (icon != null) {
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
private fun LiveFace(face: TileFace, model: TileModel, heightDp: Dp) {
    val white = Color.White
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
            is TileFace.WeatherNow -> Column(Modifier.padding(start = 7.5.dp, top = 6.dp)) {
                BasicText(face.condition, style = ShellType.caption.copy(color = white))
                BasicText(face.temperature, style = ShellType.subheader.copy(fontSize = 30.sp, lineHeight = 36.sp, color = white))
                face.details.take(2).forEach { BasicText(it, style = ShellType.caption.copy(color = white), maxLines = 1) }
                face.stale?.let { BasicText(it, style = ShellType.caption.copy(color = white), maxLines = 1) }
            }
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
        if (model.size != TileSize.SMALL) {
            BasicText(model.label, style = ShellType.caption.copy(color = white), maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = StartGrid.LABEL_INSET_EPX.dp, bottom = 5.dp))
        }
    }
}

@Composable
private fun PhotoFill(image: ImageBitmap) {
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}
