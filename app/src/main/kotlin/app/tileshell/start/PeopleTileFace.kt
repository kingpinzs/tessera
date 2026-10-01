package app.tileshell.start

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.PeopleTileRules
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.engine.PersonPhoto
import app.tileshell.tiles.engine.TileFace
import app.tileshell.ui.MotionClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The one clock every People face on Start draws from (phase 16 build task 7; R3 A9).
 *
 * The PEOPLE slot tile and a People tile pinned from the app list show the SAME bubble events (r3 D12), so the event
 * is not each tile's own: whichever face is on screen drives this clock — one driver at a time — and every face draws
 * its state. An event is A9's: the bubble on show slides out to the left in ≈ 333 ms, the tile holds none, the next
 * slides in from the right and settles in ≈ 583 ms — 1.88 s from the out's start to the in's settle — every 7.7 s. Each
 * event writes `[people] tile event <n> t0=<uptime> lookup=<key>` (the contact coming in), and its two slides run on
 * the shell's motion clock as `[motion] people_bubble_out` and `[motion] people_bubble_in`.
 */
object PeopleTileClock {
    /** The contact photos the feed last published; the driver reads them afresh at each event. */
    var photos by mutableStateOf<List<PersonPhoto>>(emptyList())

    /** The bubble at rest, or null between an event's two slides — and while there is no photo at all. */
    var settled by mutableStateOf<PersonPhoto?>(null)
        private set

    /** The bubble leaving, and how far it has gone (0 = at rest, 1 = off the left edge). */
    var leaving by mutableStateOf<PersonPhoto?>(null)
        private set
    var out by mutableFloatStateOf(0f)
        private set

    /** The bubble arriving, and how far it has come (0 = off the right edge, 1 = settled). */
    var arriving by mutableStateOf<PersonPhoto?>(null)
        private set
    var incoming by mutableFloatStateOf(0f)
        private set

    private val driver = Mutex()
    private var events = 0
    private var lastEventUptime: Long? = null

    /** A9 gives the slides' lengths, not their curves: the leaving bubble gathers speed, the arriving one settles (approximation, H3). */
    private val easeIn = CubicBezierEasing(0.7f, 0f, 1f, 0.6f)
    private val easeOut = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

    /**
     * Runs the events for as long as the caller stays on screen. A second caller waits for the first to leave, so two
     * tiles never run two clocks. The caller's coroutine carries the frame clock the motion lines are timed on.
     */
    suspend fun drive() = driver.withLock {
        try {
            while (true) {
                // Nothing to show: the faces draw the static pattern until a photo arrives.
                val list = snapshotFlow { photos }.first { it.isNotEmpty() }
                // A bubble whose photo has since been removed finishes its turn: the next event brings in another.
                if (settled == null) settled = list[PeopleTileRules.next(-1, list.size) { Random.nextInt(it) }]
                val wait = PeopleTileRules.waitMs(SystemClock.uptimeMillis(), lastEventUptime)
                // Every photo removed while a bubble rests: the static pattern, at once.
                val emptied = withTimeoutOrNull(wait) { snapshotFlow { photos }.first { it.isEmpty() } }
                if (emptied != null) {
                    settled = null
                    continue
                }
                event()
            }
        } finally {
            // A driver that leaves mid-event leaves a whole bubble behind, not half a slide.
            if (arriving != null) settled = arriving else if (settled == null && leaving != null) settled = leaving
            leaving = null
            arriving = null
            out = 0f
            incoming = 0f
        }
    }

    private suspend fun event() {
        val list = photos
        val current = list.indexOfFirst { it.lookup == settled?.lookup }
        val next = list.getOrNull(PeopleTileRules.next(current, list.size) { Random.nextInt(it) }) ?: return
        val t0 = SystemClock.uptimeMillis()
        lastEventUptime = t0
        events++
        Diagnostics.add("people", "tile event $events t0=$t0 lookup=${next.lookup}")

        leaving = settled
        settled = null
        out = 0f
        MotionClock.animate("people_bubble_out", PeopleTileRules.OUT_MS, easeIn) { out = it }
        leaving = null
        out = 0f

        // The in-slide starts a fixed time after the event began, whatever the out-slide's last frame cost.
        delay((t0 + PeopleTileRules.IN_START_MS - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        incoming = 0f
        arriving = next
        MotionClock.animate("people_bubble_in", PeopleTileRules.IN_MS, easeOut) { incoming = it }
        settled = next
        arriving = null
        incoming = 0f
    }
}

/**
 * The People tile's face (R3 A9, C3; approximation under H3 — A9 measured the event's timing from one 24-fps camera
 * source and no geometry): one photo bubble centred over the accent plate, or the static circle pattern while no
 * contact has a photo. The root node is `people_tile_face` in both forms; the bubble at rest is
 * `people_tile_bubble` with the contact's lookup key as its description, the pattern `people_tile_pattern`.
 *
 * The face scales with the tile: the bubble's diameter and place are fractions of the tile's own size, so small,
 * medium and wide tiles draw the same face (phase 01's tile rules).
 */
@Composable
internal fun PeopleTileFace(face: TileFace.People, model: TileModel, widthDp: Dp, heightDp: Dp) {
    SideEffect { PeopleTileClock.photos = face.photos }

    // Battery (constraint 3), the weather sky's rule: the events run only while Start is RESUMED and this tile is on screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var onScreen by remember { mutableStateOf(false) }
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
        if (resumed && onScreen) PeopleTileClock.drive()
    }

    val small = model.size == TileSize.SMALL
    val side = minOf(widthDp, heightDp)
    val diameter = side * if (small) 0.66f else 0.56f
    val centreX = widthDp / 2
    // Medium and wide tiles carry their label along the bottom; the bubble sits a little above the middle.
    val centreY = heightDp * if (small) 0.5f else 0.44f
    val density = LocalDensity.current

    Box(
        Modifier.fillMaxSize().testTag("people_tile_face")
            .onGloballyPositioned { onScreen = !it.boundsInWindow().isEmpty },
    ) {
        val settled = PeopleTileClock.settled
        val leaving = PeopleTileClock.leaving
        val arriving = PeopleTileClock.arriving
        if (settled == null && leaving == null && arriving == null && PeopleTileClock.photos.isEmpty()) {
            CirclePattern(Modifier.fillMaxSize().testTag("people_tile_pattern"))
            return@Box
        }
        val left = centreX - diameter / 2
        val top = centreY - diameter / 2
        // How far a bubble travels to clear each edge, in px.
        val offLeft = with(density) { (centreX + diameter / 2).toPx() }
        val offRight = with(density) { (widthDp - centreX + diameter / 2).toPx() }
        if (settled != null) {
            Bubble(settled, Modifier.offset(x = left, y = top).size(diameter).testTag("people_tile_bubble").semantics { contentDescription = settled.lookup })
        }
        if (leaving != null) {
            Bubble(leaving, Modifier.offset(x = left, y = top).size(diameter).graphicsLayer { translationX = -offLeft * PeopleTileClock.out })
        }
        if (arriving != null) {
            Bubble(arriving, Modifier.offset(x = left, y = top).size(diameter).graphicsLayer { translationX = offRight * (1f - PeopleTileClock.incoming) })
        }
    }
}

@Composable
private fun Bubble(photo: PersonPhoto, modifier: Modifier) {
    Image(photo.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(CircleShape))
}

/**
 * The People tile with no contact photo (R3 C3: "circle pattern"): circles of several sizes in tints of the tile's own
 * colour, still. R3 gives the pattern's existence, not its geometry (approximation, H3 / H19).
 */
@Composable
private fun CirclePattern(modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension
        // Centre (fractions of the tile), radius (a fraction of its short side) and how much white is laid over the accent.
        val circles = listOf(
            Circle(0.30f, 0.36f, 0.20f, 0.30f),
            Circle(0.62f, 0.30f, 0.13f, 0.18f),
            Circle(0.74f, 0.58f, 0.17f, 0.24f),
            Circle(0.47f, 0.64f, 0.10f, 0.14f),
            Circle(0.16f, 0.70f, 0.07f, 0.20f),
            Circle(0.88f, 0.22f, 0.06f, 0.12f),
        )
        circles.forEach { drawCircle(Color.White.copy(alpha = it.white), radius = unit * it.r, center = Offset(size.width * it.x, size.height * it.y)) }
    }
}

private class Circle(val x: Float, val y: Float, val r: Float, val white: Float)
