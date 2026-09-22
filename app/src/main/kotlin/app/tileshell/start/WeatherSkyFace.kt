package app.tileshell.start

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tileshell.tiles.engine.SkyScene
import app.tileshell.tiles.engine.WeatherSky
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Weather tile's animated main face (Jeremy, INDEX Change Log 2026-09-21 item 2: "the main tile should be
 * the given days weather and it should have animation of sunny, raining, snowing, rainbow ect. something fun").
 *
 * WHY it is drawn and not played: A11 buys this launcher no assets and no second animation runtime, the APK is
 * already large, and a scene here is a handful of Canvas ops a frame — cheaper than one frame of video and it
 * inherits the tile's accent instead of fighting it. The paint is flat, in W10M's register: white and black
 * washes over the accent plate the tile already draws, so the tile still reads as a Start tile with the accent
 * (and the Start background behind a transparent tile) showing through, not as a photograph in a hole.
 *
 * WHY everything is a fraction of `size`: the Weather tile can be 1 unit, 2x2 or 4x2 (TileSize) and the bottom
 * row stretches it further, so a scene with pixel numbers in it would read at exactly one of those. Sun radius,
 * cloud size, drop length and the rainbow's own centre are all fractions of the tile's own width and height,
 * and the only absolute value in the file is a hairline stroke written in epx (1.dp == 1 epx, RV10).
 *
 * WHY the maths is a separate pure object: [SkyMotion] is unit-testable on the JVM, which is the only place
 * this feature CAN be checked — the device is a QA rig, and the rainbow in particular cannot be summoned on
 * demand because it needs real weather.
 *
 * Battery: [WeatherSkyFace] holds the one frame loop, and it runs only while Start is RESUMED **and** the
 * tile's own bounds are on screen. Scroll a weather tile off the top, swipe over to the app list, or open an
 * app, and the loop suspends where it stood; it picks up from the same elapsed time when the tile comes back,
 * so the rain does not jump or restart.
 */
object SkyMotion {

    // ---- periods (ms). Nothing here is an R3 measurement: W10M never animated its weather tile, so these
    // are the agent's picks, chosen slow enough to read as weather rather than as a loading spinner. ----

    /** One full turn of the sun's rays. Slow: the sun should breathe, not spin. */
    const val SUN_SPIN_MS = 28_000

    /** The sun's disc and ray length swelling and settling once. */
    const val SUN_BREATHE_MS = 5_200

    /** A cloud crossing the tile end to end; each further cloud drifts proportionally slower. */
    const val CLOUD_DRIFT_MS = 34_000

    /** A fog band's drift; fog moves visibly but without direction. */
    const val FOG_DRIFT_MS = 21_000

    /** One raindrop's fall at moderate intensity, and one snowflake's (snow is four times slower). */
    const val RAIN_FALL_MS = 1_050
    const val SNOW_FALL_MS = 4_200

    /** A flake's sideways sway — snow never falls straight. */
    const val SNOW_SWAY_MS = 2_900

    /** One star's twinkle. */
    const val STAR_TWINKLE_MS = 3_400

    /** The gap between lightning strikes, and how long one strike lights the tile for. */
    const val STORM_CYCLE_MS = 5_600
    const val STRIKE_MS = 320

    /** A rainbow drawing itself in, holding, and fading out once. */
    const val RAINBOW_CYCLE_MS = 11_000

    // ---- particle budget ----

    /** Drops / flakes per 1000 epx² of tile at full intensity. Rain is denser than snow, as it is outside. */
    const val RAIN_DENSITY = 2.6f
    const val SNOW_DENSITY = 1.7f

    /** Even a 1-unit tile gets weather; even a stretched wide one stays cheap. */
    const val MIN_PARTICLES = 3
    const val MAX_PARTICLES = 48

    // ---- rainbow envelope, as fractions of [RAINBOW_CYCLE_MS] ----
    const val RAINBOW_FADE_IN = 0.12f
    const val RAINBOW_DRAWN_BY = 0.35f
    const val RAINBOW_HOLD_UNTIL = 0.80f

    // Salts: each particle family scatters differently, so the drops and the flakes are not the same layout.
    const val SALT_CLOUD = 11
    const val SALT_DROP_X = 23
    const val SALT_DROP_Y = 37
    const val SALT_SWAY = 53
    const val SALT_STAR = 71
    const val SALT_STRIKE = 97

    /**
     * Where a [periodMs] loop stands at [elapsedMs], as 0..1, started [offset] of a period in.
     *
     * Always in [0, 1): the caller multiplies it by a travel distance, so a value that reached 1 would put a
     * drop one pixel past its wrap and flicker.
     */
    fun phase(elapsedMs: Long, periodMs: Int, offset: Float = 0f): Float {
        if (periodMs <= 0) return 0f
        val t = elapsedMs.toFloat() / periodMs.toFloat() + offset
        return t - floor(t)
    }

    /** 0 -> 1 -> 0 over one phase: the shape a breath, a twinkle and a flash all have. */
    fun triangle(t: Float): Float = if (t < 0.5f) t * 2f else (1f - t) * 2f

    /**
     * A stable 0..1 from an index — the scatter that makes a scene look random without a Random.
     *
     * It has to be stable: the frame loop redraws from `elapsed` alone, so a drop's column, a cloud's start
     * and a strike's moment must come out the same on every frame or the scene boils. (This is the same
     * reason the tile timers in TileView keep their random draw and re-use it, R3 A8.)
     */
    fun scatter(index: Int, salt: Int): Float {
        var h = index * 374761393 + salt * 668265263
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h and 0x7FFFFFF).toFloat() / 0x7FFFFFF.toFloat()
    }

    /** Where particle [index] is down its fall, 0 at the top and approaching 1 at the bottom. */
    fun particlePhase(elapsedMs: Long, index: Int, periodMs: Int, salt: Int): Float =
        phase(elapsedMs, periodMs, scatter(index, salt))

    /** -1..1 sideways offset for a flake, on its own phase so no two sway together. */
    fun sway(elapsedMs: Long, index: Int): Float =
        sin(phase(elapsedMs, SNOW_SWAY_MS, scatter(index, SALT_SWAY)) * 2f * Math.PI.toFloat())

    /** Heavier weather falls faster: the fall period shortens by up to a third across the intensity range. */
    fun fallPeriod(basePeriodMs: Int, intensity: Float): Int =
        (basePeriodMs * (1.3f - 0.45f * intensity.coerceIn(0f, 1f))).roundToInt().coerceAtLeast(1)

    /** How many drops or flakes a tile of this size carries: density per area, scaled by intensity. */
    fun particleCount(widthEpx: Float, heightEpx: Float, intensity: Float, densityPer1000: Float): Int {
        val area = (widthEpx * heightEpx / 1000f).coerceAtLeast(0f)
        val count = area * densityPer1000 * (0.45f + 0.55f * intensity.coerceIn(0f, 1f))
        return count.roundToInt().coerceIn(MIN_PARTICLES, MAX_PARTICLES)
    }

    /**
     * How bright the lightning is at [elapsedMs], 0 for most of every cycle.
     *
     * One strike per [STORM_CYCLE_MS], at a moment [scatter] picks per cycle so the storm never ticks like a
     * metronome, and the strike itself blinks twice and dies — a single clean fade does not read as lightning.
     */
    fun strikeAlpha(elapsedMs: Long): Float {
        if (elapsedMs < 0L) return 0f
        val cycle = elapsedMs / STORM_CYCLE_MS
        val at = scatter(cycle.toInt(), SALT_STRIKE) * (STORM_CYCLE_MS - STRIKE_MS)
        val t = (elapsedMs - cycle * STORM_CYCLE_MS) - at
        if (t < 0f || t > STRIKE_MS) return 0f
        val u = t / STRIKE_MS
        // Two blinks: the shifted triangle starts AT its peak, so the tile lights the instant the bolt appears.
        val blinks = u * 2f + 0.5f
        return ((1f - u) * triangle(blinks - floor(blinks))).coerceIn(0f, 1f)
    }

    /** How much of the arc has been drawn in at this point of the rainbow's cycle. */
    fun rainbowSweep(phase: Float): Float = (phase / RAINBOW_DRAWN_BY).coerceIn(0f, 1f)

    /** The rainbow's own fade: in, hold, out — a bow that never leaves stops being a surprise. */
    fun rainbowAlpha(phase: Float): Float = when {
        phase < RAINBOW_FADE_IN -> (phase / RAINBOW_FADE_IN).coerceIn(0f, 1f)
        phase < RAINBOW_HOLD_UNTIL -> 1f
        else -> ((1f - phase) / (1f - RAINBOW_HOLD_UNTIL)).coerceIn(0f, 1f)
    }
}

/** Flat tones the scenes are painted in. They sit ON the accent plate, so every one of them is translucent. */
private object SkyPaint {
    val CLOUD_DAY = Color(0xFFFFFFFF)
    val CLOUD_NIGHT = Color(0xFFCED6E4)
    val SUN = Color(0xFFFFE9A8)
    val MOON = Color(0xFFF4F6FA)
    val WATER = Color(0xFFE6F2FF)
    val FLAKE = Color(0xFFFFFFFF)
    val FLASH = Color(0xFFFFFBE0)

    /** Six bands, not seven: indigo and violet are one band at tile size (H-row: agent pick, P4 design). */
    val BOW = listOf(
        Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFD60A),
        Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFF9B5DE5),
    )
}

/**
 * The tile's animated sky. [sky] is what to draw; the frame loop supplies when.
 *
 * The loop's two gates are the battery rule: RESUMED comes from the window's own lifecycle (so an app on top,
 * the lock screen or a trip into Settings stops it), and `onScreen` comes from the tile's own placed bounds
 * (so a tile scrolled off Start, or the whole Start page swiped over to the app list, stops too).
 */
@Composable
internal fun WeatherSkyFace(sky: WeatherSky, modifier: Modifier = Modifier) {
    // Read in the draw lambda only, so a new frame invalidates the drawing and never recomposes the tile.
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var onScreen by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
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
    LaunchedEffect(resumed, onScreen) {
        if (!resumed || !onScreen) return@LaunchedEffect
        // Carry on from where the scene stopped instead of from zero: coming back from an app must not
        // restart the rain, and a tile that scrolls in and out must not stutter.
        val carried = elapsedMs
        val first = withFrameMillis { it }
        while (true) {
            elapsedMs = carried + (withFrameMillis { it } - first)
        }
    }
    Canvas(
        modifier
            .testTag("weather_sky:${sky.scene.name.lowercase()}")
            // Fires on every placement, which is what a Start scroll and a pivot swipe both are.
            .onGloballyPositioned { onScreen = !it.boundsInWindow().isEmpty },
    ) {
        drawWeatherSky(sky, elapsedMs)
    }
}

/** One frame of [sky]: the sky wash, the scene, then the scrim the face's text sits on. */
internal fun DrawScope.drawWeatherSky(sky: WeatherSky, elapsedMs: Long) {
    drawWash(sky.isDay)
    when (sky.scene) {
        SkyScene.CLEAR -> if (sky.isDay) drawSun(elapsedMs, 1f) else { drawStars(elapsedMs); drawMoon(1f) }
        SkyScene.PARTLY -> {
            if (sky.isDay) drawSun(elapsedMs, 0.85f) else { drawStars(elapsedMs); drawMoon(0.85f) }
            drawClouds(elapsedMs, 2, sky.isDay, 0.75f)
        }
        SkyScene.CLOUDY -> drawClouds(elapsedMs, 3, sky.isDay, 0.9f)
        SkyScene.FOG -> {
            if (sky.isDay) drawSun(elapsedMs, 0.35f) else drawMoon(0.4f)
            drawFog(elapsedMs, sky.intensity)
        }
        SkyScene.RAIN -> {
            drawClouds(elapsedMs, 2, sky.isDay, 0.85f)
            drawRain(elapsedMs, sky.intensity)
        }
        SkyScene.SNOW -> {
            drawClouds(elapsedMs, 2, sky.isDay, 0.8f)
            drawSnow(elapsedMs, sky.intensity)
        }
        SkyScene.THUNDER -> {
            drawClouds(elapsedMs, 2, sky.isDay, 1f)
            drawRain(elapsedMs, sky.intensity)
            drawStorm(elapsedMs)
        }
        SkyScene.RAINBOW -> {
            // The whole picture of the trigger: sun back out, the last of the shower still falling, the bow.
            drawSun(elapsedMs, 0.8f)
            drawRainbow(elapsedMs)
            drawClouds(elapsedMs, 1, sky.isDay, 0.7f)
            drawRain(elapsedMs, 0.2f)
        }
    }
    drawTextScrim()
}

/** Day lifts the accent towards the sky, night sinks it. Both are washes: the accent stays the tile's colour. */
private fun DrawScope.drawWash(isDay: Boolean) {
    val top = if (isDay) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.34f)
    val bottom = if (isDay) Color.White.copy(alpha = 0f) else Color.Black.copy(alpha = 0.08f)
    drawRect(Brush.verticalGradient(0f to top, 1f to bottom))
}

/**
 * The text scrim: the face's place, temperature and stale line are drawn over this, and the tile's caption
 * label over its foot. Without it a white temperature lands on a white cloud (requirement: the animation is a
 * background the text sits on, never a replacement for it).
 */
private fun DrawScope.drawTextScrim() {
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.34f),
            0.55f to Color.Black.copy(alpha = 0.04f),
            0.78f to Color.Black.copy(alpha = 0.04f),
            1f to Color.Black.copy(alpha = 0.26f),
        ),
    )
}

// ---------------- the scenes ----------------

/** The sun sits to the RIGHT: the face's text runs down the left, and the two must not share a column. */
private fun DrawScope.drawSun(elapsedMs: Long, strength: Float) {
    val radius = size.height * 0.17f
    val centre = Offset(size.width * 0.79f, size.height * 0.31f)
    val breathe = SkyMotion.triangle(SkyMotion.phase(elapsedMs, SkyMotion.SUN_BREATHE_MS))
    val rays = 8
    // Turning by one ray gap per period makes the rotation seamless: the wrap lands on the next ray's place.
    rotate(SkyMotion.phase(elapsedMs, SkyMotion.SUN_SPIN_MS) * (360f / rays), centre) {
        repeat(rays) { i ->
            val angle = (i * 2f * Math.PI.toFloat()) / rays
            val dx = cos(angle)
            val dy = sin(angle)
            val inner = radius * 1.32f
            val outer = radius * (1.58f + 0.28f * breathe)
            drawLine(
                SkyPaint.SUN.copy(alpha = 0.5f * strength),
                Offset(centre.x + dx * inner, centre.y + dy * inner),
                Offset(centre.x + dx * outer, centre.y + dy * outer),
                strokeWidth = radius * 0.17f,
                cap = StrokeCap.Round,
            )
        }
    }
    drawCircle(SkyPaint.SUN.copy(alpha = 0.20f * strength), radius * 1.28f, centre)
    drawCircle(SkyPaint.SUN.copy(alpha = 0.92f * strength), radius * (0.94f + 0.06f * breathe), centre)
}

/** A full moon, flat, with two dimples: a crescent would need to punch a hole in the accent behind it. */
private fun DrawScope.drawMoon(strength: Float) {
    val radius = size.height * 0.145f
    val centre = Offset(size.width * 0.79f, size.height * 0.30f)
    drawCircle(SkyPaint.MOON.copy(alpha = 0.18f * strength), radius * 1.45f, centre)
    drawCircle(SkyPaint.MOON.copy(alpha = 0.92f * strength), radius, centre)
    drawCircle(Color.Black.copy(alpha = 0.10f * strength), radius * 0.28f, Offset(centre.x - radius * 0.32f, centre.y - radius * 0.22f))
    drawCircle(Color.Black.copy(alpha = 0.08f * strength), radius * 0.18f, Offset(centre.x + radius * 0.34f, centre.y + radius * 0.30f))
}

/** Stars twinkle on their own phases, in the top two thirds, clear of the temperature's column. */
private fun DrawScope.drawStars(elapsedMs: Long) {
    val count = SkyMotion.particleCount(size.width.toDp().value, size.height.toDp().value, 0.5f, 1.1f)
    repeat(count) { i ->
        val x = size.width * (0.18f + 0.80f * SkyMotion.scatter(i, SkyMotion.SALT_STAR))
        val y = size.height * (0.06f + 0.62f * SkyMotion.scatter(i, SkyMotion.SALT_STAR + 1))
        val twinkle = SkyMotion.triangle(SkyMotion.phase(elapsedMs, SkyMotion.STAR_TWINKLE_MS, SkyMotion.scatter(i, SkyMotion.SALT_STAR + 2)))
        val radius = size.height * (0.008f + 0.008f * SkyMotion.scatter(i, SkyMotion.SALT_STAR + 3))
        drawCircle(Color.White.copy(alpha = 0.25f + 0.65f * twinkle), radius, Offset(x, y))
    }
}

/** [count] clouds crossing left to right, each slower and lower than the last, wrapping off both edges. */
private fun DrawScope.drawClouds(elapsedMs: Long, count: Int, isDay: Boolean, alpha: Float) {
    val tone = if (isDay) SkyPaint.CLOUD_DAY else SkyPaint.CLOUD_NIGHT
    repeat(count) { i ->
        val lobe = size.height * (0.20f - 0.035f * i)
        val span = lobe * 2.2f
        val period = (SkyMotion.CLOUD_DRIFT_MS * (1f + 0.42f * i)).toInt()
        val travel = SkyMotion.phase(elapsedMs, period, SkyMotion.scatter(i, SkyMotion.SALT_CLOUD))
        val cx = -span + travel * (size.width + 2f * span)
        val cy = size.height * (0.24f + 0.19f * i)
        drawCloud(cx, cy, lobe, tone.copy(alpha = alpha * (0.42f - 0.07f * i)))
    }
}

/** One flat cloud: three lobes on a rounded base, the shape W10M's own weather glyph is cut from. */
private fun DrawScope.drawCloud(cx: Float, cy: Float, lobe: Float, colour: Color) {
    drawCircle(colour, lobe, Offset(cx, cy))
    drawCircle(colour, lobe * 0.70f, Offset(cx - lobe * 1.10f, cy + lobe * 0.28f))
    drawCircle(colour, lobe * 0.58f, Offset(cx + lobe * 1.05f, cy + lobe * 0.34f))
    drawRoundRect(
        colour,
        topLeft = Offset(cx - lobe * 1.75f, cy + lobe * 0.10f),
        size = Size(lobe * 3.4f, lobe * 0.88f),
        cornerRadius = CornerRadius(lobe * 0.44f),
    )
}

/** Fog is three slow bands crossing in alternate directions: no shape, just the sky thickening. */
private fun DrawScope.drawFog(elapsedMs: Long, intensity: Float) {
    val bandHeight = size.height * 0.17f
    repeat(3) { i ->
        val period = (SkyMotion.FOG_DRIFT_MS * (1f + 0.3f * i)).toInt()
        val travel = SkyMotion.phase(elapsedMs, period, SkyMotion.scatter(i, SkyMotion.SALT_CLOUD))
        val direction = if (i % 2 == 0) travel else 1f - travel
        val x = -size.width * 0.5f + direction * size.width * 1.5f
        drawRoundRect(
            Color.White.copy(alpha = (0.10f + 0.12f * intensity) * (1f - 0.18f * i)),
            topLeft = Offset(x, size.height * (0.28f + 0.21f * i)),
            size = Size(size.width * 0.9f, bandHeight),
            cornerRadius = CornerRadius(bandHeight / 2f),
        )
    }
}

/** Rain: hairline streaks, leaning the way wind-blown rain does, wrapping a drop's length above the tile. */
private fun DrawScope.drawRain(elapsedMs: Long, intensity: Float) {
    val count = SkyMotion.particleCount(size.width.toDp().value, size.height.toDp().value, intensity, SkyMotion.RAIN_DENSITY)
    val length = size.height * (0.14f + 0.10f * intensity.coerceIn(0f, 1f))
    val period = SkyMotion.fallPeriod(SkyMotion.RAIN_FALL_MS, intensity)
    repeat(count) { i ->
        val x = size.width * SkyMotion.scatter(i, SkyMotion.SALT_DROP_X)
        val y = SkyMotion.particlePhase(elapsedMs, i, period, SkyMotion.SALT_DROP_Y) * (size.height + length) - length
        drawLine(
            SkyPaint.WATER.copy(alpha = 0.30f + 0.35f * intensity.coerceIn(0f, 1f)),
            Offset(x, y),
            Offset(x - length * 0.22f, y + length),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Snow: slower, rounder, and swaying — the same particle field as rain with the fall taken out of it. */
private fun DrawScope.drawSnow(elapsedMs: Long, intensity: Float) {
    val count = SkyMotion.particleCount(size.width.toDp().value, size.height.toDp().value, intensity, SkyMotion.SNOW_DENSITY)
    val period = SkyMotion.fallPeriod(SkyMotion.SNOW_FALL_MS, intensity)
    val drift = size.width * 0.05f
    repeat(count) { i ->
        val flake = size.height * (0.012f + 0.014f * SkyMotion.scatter(i, SkyMotion.SALT_DROP_X + 1))
        val x = size.width * SkyMotion.scatter(i, SkyMotion.SALT_DROP_X) + SkyMotion.sway(elapsedMs, i) * drift
        val y = SkyMotion.particlePhase(elapsedMs, i, period, SkyMotion.SALT_DROP_Y) * (size.height + flake * 2f) - flake
        drawCircle(SkyPaint.FLAKE.copy(alpha = 0.55f + 0.35f * SkyMotion.scatter(i, SkyMotion.SALT_DROP_Y + 1)), flake, Offset(x, y))
    }
}

/** A strike: the whole tile lights, and a flat bolt comes out of the cloud line. */
private fun DrawScope.drawStorm(elapsedMs: Long) {
    val alpha = SkyMotion.strikeAlpha(elapsedMs)
    if (alpha <= 0f) return
    drawRect(SkyPaint.FLASH.copy(alpha = 0.24f * alpha))
    val top = size.height * 0.34f
    val height = size.height * 0.44f
    // The bolt's width comes off the HEIGHT like its length does, so a stretched wide tile gets a bolt with
    // the same proportions as a 1-unit one instead of a broadsword.
    val width = size.height * 0.10f
    val x = size.width * 0.62f
    val bolt = Path().apply {
        moveTo(x + width * 0.55f, top)
        lineTo(x - width * 0.45f, top + height * 0.52f)
        lineTo(x + width * 0.12f, top + height * 0.52f)
        lineTo(x - width * 0.35f, top + height)
        lineTo(x + width * 0.95f, top + height * 0.42f)
        lineTo(x + width * 0.25f, top + height * 0.42f)
        lineTo(x + width * 1.05f, top)
        close()
    }
    drawPath(bolt, SkyPaint.FLASH.copy(alpha = 0.95f * alpha))
}

/**
 * The bow. Its centre sits below the tile's bottom edge, so what shows is the crown of a real arc crossing
 * the sky rather than a smile drawn inside the tile, and it draws itself in from one end each cycle.
 */
private fun DrawScope.drawRainbow(elapsedMs: Long) {
    val cyclePhase = SkyMotion.phase(elapsedMs, SkyMotion.RAINBOW_CYCLE_MS)
    val alpha = SkyMotion.rainbowAlpha(cyclePhase)
    if (alpha <= 0f) return
    val sweep = SkyMotion.rainbowSweep(cyclePhase) * 160f
    val centre = Offset(size.width * 0.5f, size.height * 1.30f)
    val band = size.height * 0.055f
    SkyPaint.BOW.forEachIndexed { i, colour ->
        val radius = size.height * (1.32f - i * 0.055f)
        drawArc(
            color = colour.copy(alpha = 0.62f * alpha),
            startAngle = 190f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(band),
        )
    }
}
