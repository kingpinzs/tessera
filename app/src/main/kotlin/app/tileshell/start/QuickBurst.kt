package app.tileshell.start

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.TileKey
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** A hold that has entered edit mode and whose shortcuts are still being read (they normally are already). */
class PendingBurst(val key: TileKey, val load: Deferred<QuickLoad>)

/**
 * An open burst: its satellites and where each rests RELATIVE to the held tile, decided once at open
 * (T11-27) and then carried by the tracked tile through the contraction and a scroll.
 */
class OpenBurst(
    val key: TileKey,
    val satellites: List<SatelliteSpec>,
    val layout: BurstLayout,
    /** The held tile's edit-mode rest rectangle the layout was computed against. */
    val restTile: QRect,
) {
    /** Satellite i's rest, placed against the tile's CURRENT drawn rectangle. */
    fun restAt(i: Int, tile: QRect): SatelliteRest {
        val s = layout.satellites[i]
        val dx = tile.l - restTile.l
        val dy = tile.t - restTile.t
        return SatelliteRest(s.square.offset(dx, dy), s.label.offset(dx, dy))
    }
}

/**
 * Phase 11's burst state (build task 2), held by [StartEditState] so the gestures, Back / Home in the
 * activity and the drawn layer all see one burst.
 */
class QuickBurstState {
    var pending by mutableStateOf<PendingBurst?>(null)
    var burst by mutableStateOf<OpenBurst?>(null)
        private set

    /** The close reason while the close motion runs; null while open. */
    var closing by mutableStateOf<String?>(null)
        private set

    /**
     * The held tile's drawn rectangle in page px (Decisions "Tracking"), read every frame from [heldCoords]
     * against [pageCoords]. Read, not pushed: a graphicsLayer change on an ancestor (the entry contraction)
     * moves the tile without a new layout pass, so a positioned callback alone could miss it.
     */
    var heldBounds by mutableStateOf<QRect?>(null)

    /** The held tile's slot as StartPage places it, OUTSIDE TileView's own face layer (T11-41). */
    var heldCoords: LayoutCoordinates? = null

    /** The page's coordinates (the `start_page` root), against which the held tile is measured. */
    var pageCoords: LayoutCoordinates? = null

    /** Re-reads the held tile's bounds; keeps the last ones when its node has left (a drag, an unpin). */
    fun track() {
        val page = pageCoords ?: return
        val tile = heldCoords ?: return
        if (!page.isAttached || !tile.isAttached) return
        val r = page.localBoundingBoxOf(tile, clipBounds = false)
        val next = QRect(r.left, r.top, r.right, r.bottom)
        if (next != heldBounds) heldBounds = next
    }

    /** The open spring's progress, 0 at the held tile's centre, 1 at rest. */
    var progress by mutableFloatStateOf(0f)

    /** Set by the activity: runs satellite i of the open burst from the given page rectangle (T11-24). */
    var onSatelliteTap: (OpenBurst, Int, QRect) -> Unit = { _, _, _ -> }

    /** Open or about to open: what the tap / Back rules treat as "a burst is open". */
    val active: Boolean get() = pending != null || (burst != null && closing == null)

    fun open(b: OpenBurst) {
        closing = null
        progress = 0f
        burst = b
    }

    /**
     * Closes the burst for [reason], writing `[quick] burst closed: <reason>` once. A launch, and Start stopping,
     * remove it at once (it vanishes in the Start exit's first frame, H10); everything else runs the close.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun close(reason: String, animate: Boolean = true) {
        pending?.let { p ->
            pending = null
            // A hold whose shortcuts were in hand but not yet drawn IS a burst: say it closed.
            val ready = p.load.isCompleted && runCatching { p.load.getCompleted() }.getOrNull() is QuickLoad.Ready
            p.load.cancel()
            if (ready) Diagnostics.add("quick", "burst closed: $reason")
            return
        }
        if (burst == null || closing != null) return
        Diagnostics.add("quick", "burst closed: $reason")
        if (animate) closing = reason else clear()
    }

    /** Gone without a close line: the close motion finished, or edit mode is being torn down. */
    fun clear() {
        pending?.load?.cancel()
        pending = null
        burst = null
        closing = null
        progress = 0f
        heldBounds = null
    }

    /** Which satellite a page-space point lands on (square or label, T11-26), while the burst is open. */
    fun satelliteAt(p: Offset): Int? {
        val b = burst ?: return null
        if (closing != null) return null
        val tile = heldBounds ?: b.restTile
        return b.satellites.indices.firstOrNull { i ->
            val r = b.restAt(i, tile).hit
            p.x >= r.l && p.x <= r.r && p.y >= r.t && p.y <= r.b
        }
    }
}

/**
 * The burst layer (build task 3): drawn in `StartPage`'s own root above everything else, DRAW-ONLY — every
 * touch is decided in [startEditGestures]. Satellites spring out from the held tile's centre on
 * `spring(0.65, 1500)` and the shell logs the motion on its own frame clock (T11-5, C-31).
 */
@Composable
fun QuickBurstLayer(
    edit: StartEditState,
    accent: Color,
    tileAlpha: Float,
    textColor: Color,
    satellitePx: Float,
    gutterPx: Float,
    labelHeightPx: Float,
) {
    val quick = edit.quick
    val burst = quick.burst ?: return
    val density = LocalDensity.current
    val tileNow by rememberUpdatedState(quick.heldBounds ?: burst.restTile)
    val scaleSettled by rememberUpdatedState(edit.scaleProgress >= 1f)
    val closing = quick.closing
    var closeBack by androidx.compose.runtime.remember(burst) { mutableFloatStateOf(0f) }
    var openedTo by androidx.compose.runtime.remember(burst) { mutableFloatStateOf(0f) }

    // Tracking: the held tile's bounds, every frame, for as long as the burst is on screen.
    LaunchedEffect(burst) {
        while (true) {
            withFrameNanos { }
            quick.track()
        }
    }

    // The open, on the shell's clock: t0 = the first frame the satellites draw.
    LaunchedEffect(burst) {
        val travel = burst.satellites.indices.maxOf { i ->
            val r = burst.restAt(i, burst.restTile).square
            kotlin.math.hypot(r.cx - burst.restTile.cx, r.cy - burst.restTile.cy)
        }
        val anim = TargetBasedAnimation(
            spring(QuickMotion.DAMPING, QuickMotion.STIFFNESS, visibilityThreshold = 0.25f / travel),
            Float.VectorConverter, 0f, 1f,
        )
        val samples = ArrayList<QuickMotion.Sample>()
        var t0Nanos = -1L
        var t0Uptime = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (t0Nanos < 0) { t0Nanos = now; t0Uptime = now / 1_000_000 }
            val play = now - t0Nanos
            val p = anim.getValueFromNanos(play)
            quick.progress = p
            openedTo = p
            samples += QuickMotion.Sample(play / 1_000_000f, p)
            if (anim.isFinishedFromNanos(play)) break
            if (quick.closing != null) return@LaunchedEffect
        }
        quick.progress = 1f
        openedTo = 1f
        val s = QuickMotion.openStats(samples, travel)
        Diagnostics.add("quick", "motion open ${burst.key.id}: t0=$t0Uptime peak=${s.peakMs.toInt()} " +
            "overshoot=${"%.1f".format(s.overshootPct)} settle=${s.settleMs.toInt()} frames=${s.frames} maxGapMs=${"%.1f".format(s.maxGapMs)}")
        // `[quick] satellite i rest=` — once each time the burst comes to rest: the entry's contraction done and
        // the tracked bounds unchanged since the previous frame; again after each scroll ends (T11-28).
        var last: QRect? = null
        var logged = false
        while (quick.closing == null) {
            withFrameNanos { }
            val tile = tileNow
            val still = last == tile
            if (!still) logged = false
            if (still && scaleSettled && !logged) {
                burst.satellites.indices.forEach { i ->
                    val r = burst.restAt(i, tile).square
                    Diagnostics.add("quick", "satellite $i rest=[${r.l.toInt()},${r.t.toInt()},${r.r.toInt()},${r.b.toInt()}]")
                }
                logged = true
            }
            last = tile
        }
    }

    // The close: the same spring run back to the tile's centre, alpha reaching 0 at the halfway point.
    LaunchedEffect(burst, closing) {
        if (closing == null) return@LaunchedEffect
        val anim = TargetBasedAnimation(spring(QuickMotion.DAMPING, QuickMotion.STIFFNESS), Float.VectorConverter, 0f, 1f)
        val samples = ArrayList<QuickMotion.Sample>()
        var t0Nanos = -1L
        var t0Uptime = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (t0Nanos < 0) { t0Nanos = now; t0Uptime = now / 1_000_000 }
            val play = now - t0Nanos
            val back = anim.getValueFromNanos(play)
            closeBack = back
            samples += QuickMotion.Sample(play / 1_000_000f, back)
            if (QuickMotion.closeAlpha(back) <= 0f || anim.isFinishedFromNanos(play)) break
        }
        val alpha0 = QuickMotion.closeAlpha0(samples)
        Diagnostics.add("quick", "motion close ${burst.key.id}: t0=$t0Uptime alpha0=${alpha0.toInt()} settle=${alpha0.toInt()} " +
            "frames=${samples.size} maxGapMs=${"%.1f".format(QuickMotion.maxGap(samples))}")
        quick.clear()
    }

    val tile = tileNow
    val open = closing == null
    val p = if (open) quick.progress else openedTo * (1f - closeBack)
    val alpha = if (open) QuickMotion.openAlpha(p) else minOf(QuickMotion.openAlpha(openedTo), QuickMotion.closeAlpha(closeBack))
    val scale = QuickMotion.openScale(if (open) p else openedTo)
    val satDp = with(density) { satellitePx.toDp() }
    val labelW = satellitePx + gutterPx
    Box(Modifier.fillMaxSize().let { if (open) it.testTag("quick_burst") else it }) {
        burst.satellites.forEachIndexed { i, sat ->
            val rest = burst.restAt(i, tile)
            // Centre travels from the held tile's centre to its rest centre along the spring.
            val cx = tile.cx + (rest.square.cx - tile.cx) * p
            val cy = tile.cy + (rest.square.cy - tile.cy) * p
            val dx = cx - rest.square.cx
            val dy = cy - rest.square.cy
            Box(
                Modifier
                    .offset { IntOffset((rest.square.l + dx).toInt(), (rest.square.t + dy).toInt()) }
                    .size(satDp)
                    .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
                    .let { if (open) it.testTag("quick_sat:$i") else it }
                    .background(accent.copy(alpha = accent.alpha * tileAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                // The small tile's glyph size: 0.52 of the side (TileView's rule).
                sat.icon?.let { icon ->
                    Image(icon.bitmap, contentDescription = null, modifier = Modifier.size(with(density) { (satellitePx * 0.52f).toDp() }))
                }
            }
            // The label, OUTSIDE the satellite on its outer side, in the theme's text colour (T11-25).
            BasicText(
                sat.label,
                style = ShellType.caption.copy(color = textColor, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset { IntOffset((rest.label.l + dx).toInt(), (rest.label.t + dy).toInt()) }
                    .size(with(density) { labelW.toDp() }, with(density) { labelHeightPx.toDp() })
                    .graphicsLayer { this.alpha = alpha }
                    .let { if (open) it.testTag("quick_sat_label:$i") else it },
            )
        }
    }
}
