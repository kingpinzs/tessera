package app.tileshell.photos

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Glyph
import app.tileshell.calculator.InkText
import app.tileshell.clock.ClockMetrics
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.PhotosFeed
import app.tileshell.media.UriAccessPort
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What [ViewerActivity] was asked to show: one `content://` picture, or nothing it can show. Whether it is shown, and
 * whether anything may be changed from here, is [ViewerRules.open]'s answer for who started the activity.
 */
class ViewerNav {
    var uri by mutableStateOf<Uri?>(null)
        private set
    var mime by mutableStateOf<String?>(null)
        private set

    /** The picture was named and refused: the caller could not have read it itself. */
    var refused by mutableStateOf(false)
        private set

    /** Whether Edit, Delete and Set as are offered: only when the shell itself opened the viewer. */
    var mayChange by mutableStateOf(false)
        private set

    /**
     * Takes [ViewerRules.state]'s answer for this launch and nothing else: the rule reads who started the activity from
     * [access], decides, and names the one URI that may be opened. No decision is made here.
     */
    fun open(intent: Intent?, access: UriAccessPort) {
        mime = runCatching { intent?.type }.getOrNull()
        val state = ViewerRules.state({ intent?.data?.toString() }, access)
        state.lines.forEach { Diagnostics.add("photosapp", it) }
        refused = state.refused
        mayChange = state.mayChange
        uri = state.uri?.let(Uri::parse)
    }
}

/**
 * One picture of the viewer. [entry] is its MediaStore row when the shell can read that row by itself — the actions
 * that need a row (Edit, Delete, Set as, File information) appear only then; [id] is what its lines call it.
 */
data class ViewerItem(val key: String, val uri: Uri, val entry: MediaEntry?, val mime: String?, val id: String)

fun viewerItem(entry: MediaEntry) = ViewerItem("m:${entry.id}", PhotoStore.uriOf(entry), entry, entry.mime, entry.id.toString())

private sealed interface Load {
    data object Loading : Load
    data object Failed : Load
    class Ready(val shown: PhotoDecode.Shown) : Load
}

/** A zoomed-in part decoded from the file (r3 D8): [rect] is where it lies in the upright picture. */
private class Detail(val key: String, val rect: PxRect, val bitmap: Bitmap)

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
private val INFO_DATE = DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.getDefault())
private val EDITABLE = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

/**
 * The other apps' viewer ([ViewerActivity]): the one picture the intent names — shown only when [ViewerRules.open] found
 * that whoever started the viewer could read it, and read-only unless that was the shell itself.
 */
@Composable
fun ViewerScreen(nav: ViewerNav, activity: ComponentActivity) {
    val uri = nav.uri
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("viewer_root")) {
        if (uri == null) {
            ViewerError()
            // A refusal wrote its own line when it was decided.
            if (!nav.refused) LaunchedEffect(Unit) { Diagnostics.add("photosapp", "viewer: nothing to show (no content URI)") }
        } else {
            // The row only when the shell can read it by itself (its own query, not the caller's grant on this URI).
            val item = remember(uri) {
                val entry = if (PhotosFeed.access(activity) == PhotosFeed.Access.DENIED) null else PhotoStore.readOne(activity, uri)?.takeIf { !it.video }
                ViewerItem("u:$uri", uri, entry, entry?.mime ?: nav.mime, entry?.id?.toString() ?: "external")
            }
            PhotoViewer(listOf(item), item.key, null, false, { null }, activity, mayChange = nav.mayChange) { activity.finish() }
        }
    }
}

/** [content] laid out exactly [w] × [h] px at ([x], [y]) in a full-size layer, whatever the parent's own size is. */
@Composable
private fun PlacedAt(x: Int, y: Int, w: Int, h: Int, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints.fixed(max(1, w), max(1, h))) }
        layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(x, y) } }
    }
}

@Composable
private fun BoxScope.ViewerError() {
    Column(Modifier.align(Alignment.Center).testTag("viewer_error"), horizontalAlignment = Alignment.CenterHorizontally) {
        PhotoGlyph(Glyph.IMAGE_OFF, 40f, PhotosMetrics.SECONDARY, Modifier.size(48.dp))
        BasicText("This picture can't be shown.", Modifier.padding(top = 8.dp).testTag("viewer_error_text"), style = ShellType.body.copy(color = PhotosMetrics.SECONDARY))
    }
}

/**
 * The full-screen viewer (build task 5, Y2, Y6): the photo fitted to the width and centred on the whole screen, the
 * 50-epx date header and the 48-epx black bar over it, a swipe to the next and previous picture with a 20-epx gap,
 * pinch and double-tap zoom, and the actions. It opens by expanding from [origin] (the tapped tile) and closes by
 * shrinking back to the tile [tileBounds] gives for the picture then shown. [mayChange] is false in the viewer another
 * app opened: Edit, Delete and Set as are then not offered ([ViewerRules.actions]). It has no default: every caller
 * says which it is (C2-M1).
 *
 * A still that is a Living Image (build task 6d; [LivingImages]) carries the glyph `viewer_living` in the header, and a
 * press-and-hold on it plays its clip in place over the still ([LivingPlayback]) until the finger lifts or the clip
 * ends; the player is also released when another picture is shown, on Back, when the viewer closes and when the
 * activity stops. It reads and changes nothing, so it is the same in the viewer another app opened.
 */
@Composable
fun BoxScope.PhotoViewer(
    items: List<ViewerItem>,
    startKey: String,
    origin: Rect?,
    startSlideshow: Boolean,
    tileBounds: (ViewerItem) -> Rect?,
    activity: ComponentActivity,
    mayChange: Boolean,
    onClosed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = LocalShellColors.current
    val slop = LocalViewConfiguration.current.touchSlop
    val currentItems by rememberUpdatedState(items)

    var currentKey by remember { mutableStateOf(startKey) }
    var lastIndex by remember { mutableStateOf(max(0, items.indexOfFirst { it.key == startKey })) }
    val found = items.indexOfFirst { it.key == currentKey }
    val index = if (found >= 0) found else lastIndex.coerceIn(0, max(0, items.size - 1))
    val current = items.getOrNull(index)
    // The picture shown left the list (deleted here or elsewhere): the next one takes its place, or the viewer closes.
    LaunchedEffect(items, currentKey) {
        if (items.isEmpty()) onClosed()
        else if (found < 0) currentKey = items[index].key
        else lastIndex = found
    }

    var open by remember { mutableFloatStateOf(0f) }
    var opened by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var closeRect by remember { mutableStateOf<Rect?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var gesturing by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var barExpanded by remember { mutableStateOf(false) }
    var setAsOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var fadeFromKey by remember { mutableStateOf<String?>(null) }
    var fade by remember { mutableFloatStateOf(1f) }
    var status by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    val loads = remember { mutableStateMapOf<String, Load>() }
    var pendingDelete by remember { mutableStateOf<ViewerItem?>(null) }
    // Living Images: the clip of the picture shown (with the item it was read for), and the clip playing under a hold.
    var living by remember { mutableStateOf<LivingPlayback?>(null) }
    val clipRead by produceState<Pair<String, LivingRules.Clip>?>(null, current?.key, current?.entry?.size, current?.entry?.dateModifiedS) {
        val item = current
        value = item?.entry?.let(LivingImages::cached)?.let { item.key to it }
        if (item != null && value == null) {
            val clip = if (item.entry != null) LivingImages.of(activity, item.entry) else LivingImages.of(activity, item.uri, item.mime)
            value = clip?.let { item.key to it }
        }
    }
    val livingClip = clipRead?.takeIf { it.first == current?.key }?.second
    val livingClipNow by rememberUpdatedState(clipRead)

    // What is shown NOW, read from state: the gesture handlers below live across recompositions and must not hold a
    // composition's own `current` / `index`.
    fun nowIndex(): Int = currentItems.indexOfFirst { it.key == currentKey }
    fun now(): ViewerItem? = currentItems.getOrNull(nowIndex())

    fun resetZoom() { scale = 1f; panX = 0f; panY = 0f; detail = null }

    /** The hold began: the shown picture's clip plays, when it has one. False for a plain still (nothing is logged). */
    fun startLiving(): Boolean {
        val item = now() ?: return false
        val clip = livingClipNow?.takeIf { it.first == item.key }?.second ?: return false
        if (!opened || closing || settling || slideshow || living != null) return false
        living = LivingPlayback.start(activity, item.key, item.id, item.uri, clip) { ended -> if (living === ended) living = null }
        return living != null
    }

    fun close() {
        if (closing) return
        closing = true
        barExpanded = false
        resetZoom()
        closeRect = current?.let(tileBounds)
        scope.launch {
            withFrameNanos { }
            MotionClock.animate("viewer_close", PhotosMetrics.VIEWER_MS, ClockMetrics.easeOut) { open = 1f - it }
            onClosed()
        }
    }

    LaunchedEffect(Unit) {
        // The clock starts once the viewer's first frames are down and the picture's screen-size decode is in hand (or
        // 300 ms have gone), so neither composing the viewer nor the decode's upload lands inside the motion as a gap.
        repeat(2) { withFrameNanos { } }
        withTimeoutOrNull(300) { snapshotFlow { loads[startKey] }.first { it != null && it !is Load.Loading } }
        withFrameNanos { }
        MotionClock.animate("viewer_open", PhotosMetrics.VIEWER_MS, ClockMetrics.easeOut) { open = it }
        opened = true
        Diagnostics.add("photosapp", "viewer open ${current?.id}")
        if (startSlideshow) slideshow = true
    }
    LaunchedEffect(status) { if (status != null) { delay(2500); status = null } }
    // A clip never outlives its picture's turn on screen: another picture, the slideshow, the close, the activity's stop.
    LaunchedEffect(currentKey, slideshow, closing) { living?.let { if (it.itemKey != currentKey || slideshow || closing) it.stop(LivingRules.Stop.LEFT) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { living?.stop(LivingRules.Stop.LEFT) }
    DisposableEffect(Unit) { onDispose { living?.stop(LivingRules.Stop.LEFT) } }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val item = pendingDelete ?: return@rememberLauncherForActivityResult
        pendingDelete = null
        if (result.resultCode == Activity.RESULT_OK) {
            Diagnostics.add("photosapp", "delete ${item.id}: deleted")
            item.entry?.let { PhotoThumbs.forget(it.id) }
            loads.remove(item.key)
            if (currentItems.size <= 1) onClosed() else PhotoStore.refresh(activity, "deleted in the viewer")
        } else {
            Diagnostics.add("photosapp", "delete ${item.id}: refused by user")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag("viewer")) {
        val viewW = with(density) { maxWidth.toPx() }
        // The whole screen: this box ends at the drawn nav bar's top, and W10M centres the photo on the screen (1.6.4).
        val screenH = with(density) { (maxHeight + BarMetrics.NAV_EPX.dp).toPx() }
        val gap = with(density) { PhotosMetrics.SWIPE_GAP.toPx() }

        // The decodes: the picture shown and its two neighbours, at screen size (r3 D8); anything further is dropped.
        val wanted = if (opened) listOfNotNull(items.getOrNull(index - 1), current, items.getOrNull(index + 1)) else listOfNotNull(current)
        val keep = wanted.map { it.key } + listOfNotNull(fadeFromKey)
        LaunchedEffect(keep) { loads.keys.filter { it !in keep }.forEach { loads.remove(it) } }
        wanted.forEach { item ->
            key(item.key) {
                LaunchedEffect(item.key, item.entry?.size, item.entry?.dateMs) {
                    loads[item.key] = Load.Loading
                    val result = withContext(Dispatchers.IO) { PhotoDecode.screen(activity, item.uri, viewW.roundToInt(), screenH.roundToInt()) }
                    loads[item.key] = result.fold({ Load.Ready(it) }, {
                        Diagnostics.add("photosapp", "viewer ${item.id}: cannot be shown (${it.javaClass.simpleName})")
                        Load.Failed
                    })
                }
            }
        }

        fun fitOf(item: ViewerItem): Pair<Float, Float> {
            val ready = loads[item.key] as? Load.Ready
            val w = ready?.shown?.width ?: item.entry?.width ?: 1
            val h = ready?.shown?.height ?: item.entry?.height ?: 1
            return ViewerMath.fit(w, h, viewW, screenH)
        }

        fun zoomTo(target: Float, about: Offset?) {
            val item = now() ?: return
            val (fw, fh) = fitOf(item)
            val startScale = scale
            val startX = panX
            val startY = panY
            // The point under the finger stays under it.
            val endX = if (target <= 1f || about == null) 0f else ViewerMath.clampPan((viewW / 2f - about.x) * (target - 1f), fw * target, viewW)
            val endY = if (target <= 1f || about == null) 0f else ViewerMath.clampPan((screenH / 2f - about.y) * (target - 1f), fh * target, screenH)
            scope.launch {
                gesturing = true
                MotionClock.animate("viewer_zoom", 200, ClockMetrics.easeOut) { f ->
                    scale = startScale + (target - startScale) * f
                    panX = startX + (endX - startX) * f
                    panY = startY + (endY - startY) * f
                }
                gesturing = false
                if (target <= 1f) detail = null
                Diagnostics.add("photosapp", "zoom ${item.id} x${String.format(Locale.US, "%.2f", target)} width=${(fw * target).roundToInt()}px")
            }
        }

        fun settleSwipe() {
            if (settling) return
            val from = nowIndex()
            val target = when {
                dragX < -viewW * 0.2f && from < currentItems.size - 1 -> 1
                dragX > viewW * 0.2f && from > 0 -> -1
                else -> 0
            }
            val start = dragX
            val end = -target * (viewW + gap)
            settling = true
            scope.launch {
                MotionClock.animate("photo_swipe", PhotosMetrics.SWIPE_SETTLE_MS, ClockMetrics.easeOut) { dragX = start + (end - start) * it }
                if (target != 0) {
                    currentItems.getOrNull(from + target)?.let { currentKey = it.key; Diagnostics.add("photosapp", "viewer show ${it.id}") }
                    resetZoom()
                }
                dragX = 0f
                settling = false
            }
        }

        // The zoomed-in part, decoded again from the file once the fingers rest (BitmapRegionDecoder, r3 D8).
        LaunchedEffect(currentKey, scale, panX, panY, gesturing, loads[currentKey]) {
            val item = current ?: return@LaunchedEffect
            val ready = loads[item.key] as? Load.Ready ?: return@LaunchedEffect
            if (scale <= 1.01f) { detail = null; return@LaunchedEffect }
            if (gesturing || ready.shown.bitmap.width >= ready.shown.width) return@LaunchedEffect
            delay(120)
            val (fw, fh) = ViewerMath.fit(ready.shown.width, ready.shown.height, viewW, screenH)
            val rect = ViewerMath.visibleRegion(ready.shown.width, ready.shown.height, fw * scale, fh * scale, panX, panY, viewW, screenH)
            if (rect.width <= 0 || rect.height <= 0 || detail?.let { it.key == item.key && it.rect == rect } == true) return@LaunchedEffect
            val sample = ViewerMath.sampleSize(rect.width, viewW.roundToInt())
            val bitmap = withContext(Dispatchers.IO) { PhotoDecode.region(activity, item.uri, rect, sample) } ?: return@LaunchedEffect
            detail = Detail(item.key, rect, bitmap)
            Diagnostics.add("photosapp", "viewer ${item.id}: region ${rect.width}x${rect.height} decoded at 1/$sample")
        }

        // The slideshow (Y6): a step every 5 s on the uptime clock, in the list's order, each a 250-ms cross-fade.
        LaunchedEffect(slideshow) {
            if (!slideshow) return@LaunchedEffect
            Diagnostics.add("photosapp", "slideshow start ${current?.id}")
            val t0 = SystemClock.uptimeMillis()
            var n = 1
            while (true) {
                delay(max(0L, t0 + n * PhotosMetrics.SLIDESHOW_STEP_MS - SystemClock.uptimeMillis()))
                val list = currentItems
                val at = list.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
                val next = list.getOrNull(ViewerMath.nextIndex(at, list.size)) ?: break
                fadeFromKey = currentKey
                fade = 0f
                resetZoom()
                currentKey = next.key
                Diagnostics.add("photosapp", "slideshow next ${next.id}")
                withFrameNanos { }
                MotionClock.animate("slideshow_step", PhotosMetrics.SLIDESHOW_SETTLE_MS, LinearEasing) { fade = it }
                fadeFromKey = null
                n++
            }
        }
        fun stopSlideshow() {
            if (slideshow) { slideshow = false; fadeFromKey = null; fade = 1f; Diagnostics.add("photosapp", "slideshow stop") }
        }

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = open.coerceIn(0f, 1f))))

        // One page: the picture in its place. `shift` is the page's offset in the strip.
        @Composable
        fun Page(item: ViewerItem, shift: Float, isCurrent: Boolean, pageAlpha: Float) {
            val load = loads[item.key] ?: Load.Loading
            val (fw, fh) = fitOf(item)
            val zoom = if (isCurrent) scale else 1f
            val fitRect = Rect(Offset((viewW - fw) / 2f, (screenH - fh) / 2f), androidx.compose.ui.geometry.Size(fw, fh))
            val from = if (closing) closeRect else origin
            val moving = isCurrent && (open < 1f)
            val rect = when {
                moving && from != null -> Rect(
                    from.left + (fitRect.left - from.left) * open, from.top + (fitRect.top - from.top) * open,
                    from.right + (fitRect.right - from.right) * open, from.bottom + (fitRect.bottom - from.bottom) * open,
                )
                else -> {
                    val w = fw * zoom
                    val h = fh * zoom
                    Rect(Offset((viewW - w) / 2f + (if (isCurrent) panX else 0f), (screenH - h) / 2f + (if (isCurrent) panY else 0f)), androidx.compose.ui.geometry.Size(w, h))
                }
            }
            val a = pageAlpha * (if (moving && from == null) open else 1f)
            if (load is Load.Failed) {
                if (isCurrent) Box(Modifier.fillMaxSize().offset { IntOffset(shift.roundToInt(), 0) }.alpha(a)) { ViewerError() }
                return
            }
            val bitmap = (load as? Load.Ready)?.shown?.bitmap?.asImageBitmap() ?: item.entry?.let { PhotoThumbs.cached(it.id) }
            // Placed by hand at its own size: a zoomed picture is larger than the screen, and a Box would be cut to it.
            PlacedAt((rect.left + shift).roundToInt(), rect.top.roundToInt(), rect.width.roundToInt(), rect.height.roundToInt()) {
              Box(Modifier.fillMaxSize().alpha(a).clipToBounds().testTag(if (isCurrent) "viewer_image" else "viewer_image_side")) {
                if (bitmap != null) Image(bitmap, contentDescription = item.entry?.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                val d = detail
                val ready = load as? Load.Ready
                if (isCurrent && d != null && d.key == item.key && ready != null && !moving) {
                    val sx = rect.width / ready.shown.width
                    val sy = rect.height / ready.shown.height
                    Image(
                        d.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds,
                        modifier = Modifier.offset { IntOffset((d.rect.left * sx).roundToInt(), (d.rect.top * sy).roundToInt()) }
                            .size(with(density) { (d.rect.width * sx).toDp() }, with(density) { (d.rect.height * sy).toDp() }).testTag("viewer_detail"),
                    )
                }
                // The Living Image's clip, in the still's own box while the hold lasts.
                val play = living
                if (isCurrent && play != null && play.itemKey == item.key && !moving) LivingSurface(play)
              }
            }
        }

        if (current != null) {
            items.getOrNull(index - 1)?.takeIf { dragX > 0f }?.let { key(it.key) { Page(it, dragX - (viewW + gap), false, 1f) } }
            items.getOrNull(index + 1)?.takeIf { dragX < 0f }?.let { key(it.key) { Page(it, dragX + (viewW + gap), false, 1f) } }
            key(current.key) { Page(current, dragX, true, 1f) }
            fadeFromKey?.let { k -> items.firstOrNull { it.key == k } }?.let { old -> key("fade:${old.key}") { Page(old, 0f, false, 1f - fade) } }
        }

        // Touch: a tap shows or hides the chrome, a double tap zooms, one finger swipes (or pans when zoomed), two pinch.
        Box(
            Modifier.fillMaxSize().testTag("viewer_touch")
                .pointerInput(Unit) {
                    // A hold plays a Living Image's clip until the finger lifts; on a plain still a hold stays what it
                    // was — a slow tap.
                    var plainHold = false
                    detectTapGestures(
                        onPress = {
                            plainHold = false
                            val released = tryAwaitRelease()
                            living?.stop(if (released) LivingRules.Stop.RELEASED else LivingRules.Stop.LEFT)
                            if (released && plainHold) { if (slideshow) stopSlideshow() else if (!closing) chrome = !chrome }
                            plainHold = false
                        },
                        onLongPress = { plainHold = !startLiving() },
                        onTap = { if (slideshow) stopSlideshow() else if (!closing) chrome = !chrome },
                        onDoubleTap = { at ->
                            val item = currentItems.firstOrNull { it.key == currentKey }
                            val ready = item?.let { loads[it.key] as? Load.Ready }
                            if (ready != null && !slideshow && !closing) {
                                val (fw, _) = ViewerMath.fit(ready.shown.width, ready.shown.height, viewW, screenH)
                                if (scale > 1.01f) zoomTo(1f, null) else zoomTo(ViewerMath.doubleTapScale(ready.shown.width, fw), at)
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var mode = 0 // 0 undecided, 1 swipe, 2 pan, 3 pinch, 4 ignored
                        var totalX = 0f
                        var totalY = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            // While a clip plays the finger is holding, not swiping: its drift moves nothing.
                            if (closing || settling || slideshow || living != null) continue
                            val item = currentItems.firstOrNull { it.key == currentKey }
                            val ready = item?.let { loads[it.key] as? Load.Ready }
                            val fit = ready?.let { ViewerMath.fit(it.shown.width, it.shown.height, viewW, screenH) }
                            if (pressed.size >= 2 && fit != null && ready != null) {
                                if (mode == 1) dragX = 0f
                                mode = 3
                                gesturing = true
                                val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val pan = event.calculatePan()
                                val next = (scale * zoom).coerceIn(1f, ViewerMath.maxScale(ready.shown.width, fit.first))
                                val applied = next / scale
                                // The point between the fingers stays between them.
                                panX = ViewerMath.clampPan((panX - (centroid.x - viewW / 2f)) * applied + (centroid.x - viewW / 2f) + pan.x, fit.first * next, viewW)
                                panY = ViewerMath.clampPan((panY - (centroid.y - screenH / 2f)) * applied + (centroid.y - screenH / 2f) + pan.y, fit.second * next, screenH)
                                scale = next
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1 && mode != 3) {
                                val d = pressed[0].positionChange()
                                totalX += d.x
                                totalY += d.y
                                if (mode == 0 && (abs(totalX) > slop || abs(totalY) > slop)) {
                                    mode = if (scale > 1.01f) 2 else if (abs(totalX) > abs(totalY)) 1 else 4
                                    if (mode == 1) dragX = totalX - d.x
                                    if (mode == 2) gesturing = true
                                }
                                when (mode) {
                                    1 -> {
                                        // Finger-tracked 1:1; at the list's two ends the strip gives only a third.
                                        val raw = dragX + d.x
                                        val at = nowIndex()
                                        val atEnd = (raw > 0f && at <= 0) || (raw < 0f && at >= currentItems.size - 1)
                                        dragX = if (atEnd) dragX + d.x / 3f else raw
                                        pressed[0].consume()
                                    }
                                    2 -> if (fit != null) {
                                        panX = ViewerMath.clampPan(panX + d.x, fit.first * scale, viewW)
                                        panY = ViewerMath.clampPan(panY + d.y, fit.second * scale, screenH)
                                        pressed[0].consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (mode == 1) settleSwipe()
                        if (mode == 3 && scale <= 1.01f) resetZoom()
                        gesturing = false
                    }
                },
        )

        val showChrome = chrome && opened && !closing && !slideshow
        if (showChrome && current != null) {
            // Y2: the 50-epx #171717 header with the long date, 15 epx, at x 25.
            Box(Modifier.fillMaxWidth().height(PhotosMetrics.VIEWER_HEADER).background(PhotosMetrics.VIEWER_HEADER_FILL).testTag("viewer_header")) {
                val text = current.entry?.let { Instant.ofEpochMilli(it.dateMs).atZone(ZoneId.systemDefault()).format(HEADER_DATE) }.orEmpty()
                InkText(text, ShellType.body.copy(color = Color.White), reference = "H", modifier = Modifier.testTag("viewer_date"),
                    inkLeftEpx = PhotosMetrics.VIEWER_DATE_LEFT, inkTopEpx = PhotosMetrics.VIEWER_DATE_CAP_TOP)
                // A Living Image says so at the header's right end, as far in as the date is from the left.
                if (livingClip != null) {
                    PhotoGlyph(Glyph.LIVING_IMAGE, 20f, Color.White, Modifier.align(Alignment.CenterEnd).padding(end = PhotosMetrics.VIEWER_DATE_LEFT.dp).size(20.dp).testTag("viewer_living"))
                }
            }
            status?.let {
                BasicText(it, Modifier.align(Alignment.BottomCenter).padding(bottom = PhotosMetrics.BAR_EXPANDED + 12.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp).testTag("viewer_status"),
                    style = ShellType.body.copy(color = Color.White))
            }
            val entry = current.entry
            val offered = ViewerRules.actions(hasRow = entry != null, editable = entry != null && entry.mime in EDITABLE, several = items.size > 1, mayChange = mayChange)
            val buttons = buildList {
                add(PhotoBarButton(Glyph.SHARE, "Share", "viewer_share") { PhotoActions.share(activity, current.uri, current.mime, current.id) })
                if (ViewerRules.Action.EDIT in offered) add(PhotoBarButton(Glyph.EDIT, "Edit", "viewer_edit") { sheetOpen = true })
                if (ViewerRules.Action.DELETE in offered) add(PhotoBarButton(Glyph.DELETE, "Delete", "viewer_delete") {
                    PhotoActions.deleteConsent(activity, current.uri, current.id)?.let { consent ->
                        pendingDelete = current
                        runCatching { deleteLauncher.launch(IntentSenderRequest.Builder(consent).build()) }
                            .onFailure { pendingDelete = null; Diagnostics.add("photosapp", "delete ${current.id} failed: ${it.javaClass.simpleName}") }
                    }
                })
            }
            val menu = buildList {
                if (ViewerRules.Action.SLIDESHOW in offered) add(PhotoMenuEntry("Slideshow", "viewer_menu_slideshow") { chrome = true; slideshow = true })
                if (ViewerRules.Action.SET_AS in offered) add(PhotoMenuEntry("Set as", "viewer_menu_setas") { setAsOpen = true })
                if (ViewerRules.Action.FILE_INFORMATION in offered) add(PhotoMenuEntry("File information", "viewer_menu_info", ruleAbove = size > 0) { infoOpen = true })
            }
            PhotoBar(buttons, menu, PhotosMetrics.VIEWER_BAR_FILL, "viewer", isExpanded = { barExpanded }, onExpand = { barExpanded = it })

            if (setAsOpen && entry != null && ViewerRules.Action.SET_AS in offered) {
                val choices = listOf(
                    PhotoMenuEntry("Start background", "viewer_setas_background") {
                        scope.launch { status = PhotoActions.setAsBackground(activity, current.uri, entry.id)?.let { "Couldn't set the background: $it" } ?: "Set as Start background" }
                    },
                    PhotoMenuEntry("Lock screen", "viewer_setas_lock") {
                        scope.launch { status = PhotoActions.setAsLockScreen(activity, current.uri, entry.id)?.let { "Couldn't set the lock screen: $it" } ?: "Set as lock screen" }
                    },
                )
                OverlayLayer(active = { setAsOpen }) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().testTag("viewer_setas_scrim").pointerInput(Unit) {
                            awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) dismissOverlay { setAsOpen = false } }
                        })
                        Column(Modifier.align(Alignment.BottomStart).offset(y = -PhotosMetrics.BAR).fillMaxWidth().height(photoMenuHeight(choices)).background(PhotosMetrics.MENU_FILL).testTag("viewer_setas")) {
                            PhotoMenuRows(choices) { dismissOverlay { setAsOpen = false } }
                        }
                    }
                }
            }
            if (infoOpen && entry != null) {
                // File information (pass 2): the lower half of the screen, the title top-left and an accent Close top-right.
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(with(density) { (screenH / 2f).toDp() } - BarMetrics.NAV_EPX.dp)
                        .background(PhotosMetrics.INFO_FILL).testTag("viewer_info")
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume(); waitForUpOrCancellation()?.consume() } },
                ) {
                    BasicText("File information", Modifier.offset(x = PhotosMetrics.INFO_TITLE_LEFT.dp, y = CapMetrics.topPaddingForCapTop(PhotosMetrics.INFO_TITLE_CAP_TOP, 24f).dp), style = ShellType.title.copy(color = Color.White))
                    BasicText(
                        "Close",
                        Modifier.align(Alignment.TopEnd).padding(end = PhotosMetrics.INFO_SIDE.dp).offset(y = CapMetrics.topPaddingForCapTop(PhotosMetrics.INFO_TITLE_CAP_TOP + 4f, 15f).dp).testTag("viewer_info_close")
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { infoOpen = false },
                        style = ShellType.body.copy(color = colors.accent),
                    )
                    val fields = listOf(
                        "name" to ("Name" to entry.name),
                        "date" to ("Date" to Instant.ofEpochMilli(entry.dateMs).atZone(ZoneId.systemDefault()).format(INFO_DATE)),
                        "size" to ("Size" to "${PhotoActions.sizeText(entry.size)}, ${entry.width} x ${entry.height}"),
                        "folder" to ("Folder" to entry.relativePath.trimEnd('/')),
                    )
                    fields.forEachIndexed { i, (id, field) ->
                        Column(Modifier.offset(x = PhotosMetrics.INFO_SIDE.dp, y = 52.dp + PhotosMetrics.INFO_PITCH * i)) {
                            BasicText(field.first, style = ShellType.body.copy(color = PhotosMetrics.SECONDARY))
                            BasicText(field.second, Modifier.testTag("viewer_info_$id"), style = ShellType.body.copy(color = Color.White), maxLines = 1)
                        }
                    }
                }
            }
            if (sheetOpen && entry != null && ViewerRules.Action.EDIT in offered) {
                EditSheet(
                    rows = listOf(EditSheetRow(Glyph.CROP, "Crop, Rotate, Auto-enhance", "edit_sheet_editor") { startEditor(activity, entry, trim = false) }),
                    isOpen = { sheetOpen },
                    onDismiss = { dismissOverlay { sheetOpen = false } },
                )
            }
        }

        BackHandler {
            living?.stop(LivingRules.Stop.LEFT)
            when {
                sheetOpen -> dismissOverlay { sheetOpen = false }
                infoOpen -> infoOpen = false
                setAsOpen -> dismissOverlay { setAsOpen = false }
                barExpanded -> dismissOverlay { barExpanded = false }
                slideshow -> stopSlideshow()
                else -> close()
            }
        }
    }
}
