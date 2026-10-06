package app.tileshell.photos

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.clock.ClockMetrics
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What [EditActivity] was started for: the MediaStore item to edit (an image) or trim (a video). */
class EditNav {
    var uri by mutableStateOf<Uri?>(null)
        private set
    var trim by mutableStateOf(false)
        private set

    fun open(intent: Intent?) {
        uri = intent?.data?.takeIf { it.scheme == "content" && it.authority == "media" }
        trim = intent?.getBooleanExtra(EXTRA_TRIM, false) == true
    }

    companion object {
        const val EXTRA_TRIM = "trim"
    }
}

/** The editor's tools that take the screen over while they are set (the strip gives way to Accept · More). */
enum class EditTool(val id: String, val label: String) {
    CROP("crop", "Crop"),
    STRAIGHTEN("straighten", "Straighten"),
    LIGHT("light", "Light"),
    COLOUR("colour", "Colour"),
    FILTERS("filters", "Filters"),
    REDEYE("redeye", "Red-eye"),
}

/** The preview the editor works on: the picture upright at no more than [MAX] px a side (r3 D8), and a screen-size copy. */
private class Preview(val width: Int, val height: Int, val pixels: IntArray, val fullW: Int, val fullH: Int) {
    companion object {
        const val MAX = 4096
        const val SHOWN = 1280
    }
}

@Composable
fun EditScreen(nav: EditNav, activity: ComponentActivity) {
    val uri = nav.uri
    val entry = remember(uri) { uri?.let { PhotoStore.readOne(activity, it) } }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("edit_root")) {
        when {
            uri == null || entry == null -> {
                LaunchedEffect(Unit) { Diagnostics.add("photosapp", if (nav.trim) "trim failed: the item cannot be read" else "edit failed: the item cannot be read") }
                BasicText("This item can't be edited.", Modifier.align(Alignment.Center).testTag("edit_error"), style = ShellType.body.copy(color = PhotosMetrics.SECONDARY))
            }
            nav.trim -> TrimScreen(uri, entry, activity)
            else -> PhotoEditor(uri, entry, activity)
        }
    }
}

/**
 * The photo editor (build task 5, Y11): full screen, the 48-epx bottom strip Crop · Enhance · Rotate · Save · More —
 * Save being "Save a copy" — and, from More, the four added tools (P4): Straighten, Light, Colour, Filters, Red-eye.
 * A tool that takes settings replaces the strip with its own bar (Accept · More → Cancel), W10M's crop bar form.
 */
@Composable
private fun BoxScope.PhotoEditor(uri: Uri, entry: MediaEntry, activity: ComponentActivity) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = LocalShellColors.current
    var preview by remember { mutableStateOf<Preview?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(EditState()) }
    var tool by remember { mutableStateOf<EditTool?>(null) }
    var stateAtEntry by remember { mutableStateOf(EditState()) }
    var cropDraft by remember { mutableStateOf(NormRect.FULL) }
    var aspect by remember { mutableStateOf<Double?>(null) }
    var aspectMenu by remember { mutableStateOf(false) }
    var barExpanded by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var spin by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val full = PhotoDecode.screen(activity, uri, Preview.MAX, Preview.MAX, software = true).getOrThrow()
                // The screen works from a copy no larger than it can show; the ≤ 4096-px decode is what red-eye reads.
                val s = min(1f, Preview.SHOWN.toFloat() / max(full.bitmap.width, full.bitmap.height))
                val small = if (s < 1f) Bitmap.createScaledBitmap(full.bitmap, max(1, (full.bitmap.width * s).roundToInt()), max(1, (full.bitmap.height * s).roundToInt()), true) else full.bitmap
                val pixels = IntArray(small.width * small.height)
                small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                EditSession.working = full.bitmap
                Preview(small.width, small.height, pixels, full.width, full.height)
            }.getOrNull()
        }
        if (loaded == null) { loadFailed = true; Diagnostics.add("photosapp", "edit failed: the picture could not be decoded") }
        else { preview = loaded; Diagnostics.add("photosapp", "editor open ${entry.id} ${loaded.fullW}x${loaded.fullH} preview ${EditSession.working?.width}x${EditSession.working?.height}") }
    }

    // The picture as edited so far, redrawn off the main thread whenever a setting changes. In crop mode the whole
    // straightened picture shows, with the crop drawn over it.
    val showCrop = tool == EditTool.CROP
    LaunchedEffect(preview, state, showCrop) {
        val p = preview ?: return@LaunchedEffect
        shown = withContext(Dispatchers.Default) {
            val (pixels, w, h) = EditRender.renderInto(state, p.pixels, p.width, p.height, applyCrop = !showCrop)
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
    }

    fun enter(t: EditTool) {
        stateAtEntry = state
        if (t == EditTool.CROP) {
            // W10M opens the crop at 70 % of the picture (pass 2); an earlier crop opens as it was left.
            cropDraft = if (state.crop == NormRect.FULL) NormRect(0.15, 0.15, 0.85, 0.85) else state.crop
            aspect = null
        }
        tool = t
        Diagnostics.add("photosapp", "editor tool ${t.id}")
    }

    fun accept() {
        if (tool == EditTool.CROP) state = state.copy(crop = cropDraft)
        tool = null
        Diagnostics.add("photosapp", "editor state ${state.toolName.ifEmpty { "none" }}")
    }

    fun cancel() { state = stateAtEntry; tool = null }

    fun save() {
        if (saving || state.tools.isEmpty()) return
        saving = true
        status = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { EditRender.saveCopy(activity, uri, entry, state) }
            saving = false
            when (result) {
                // The editor stays up with the result on screen: the copy is in the collection, Back returns to the viewer,
                // and this process's ring (read through the service this activity holds) still carries the op's line.
                is EditRender.Saved.Ok -> status = "Saved a copy in ${result.where.substringBeforeLast('/')}"
                is EditRender.Saved.Failed -> status = "Couldn't save a copy: ${result.why}"
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelH = when (tool) { null, EditTool.CROP -> 0.dp; else -> 64.dp }
        val areaW = with(density) { maxWidth.toPx() }
        val areaH = with(density) { (maxHeight - PhotosMetrics.BAR - panelH).toPx() }
        val bitmap = shown
        if (loadFailed) {
            BasicText("This picture can't be edited.", Modifier.align(Alignment.Center).testTag("edit_error"), style = ShellType.body.copy(color = PhotosMetrics.SECONDARY))
        } else if (bitmap != null) {
            // Fitted inside the area above the bar with a margin, centred on it (pass 2: the crop page's placement).
            val margin = with(density) { (if (showCrop) 34.dp else 0.dp).toPx() }
            val s = min((areaW - 2 * margin) / bitmap.width, (areaH - 2 * margin) / bitmap.height)
            val w = bitmap.width * s
            val h = bitmap.height * s
            val left = (areaW - w) / 2f
            val top = (areaH - h) / 2f
            val p = preview
            Box(
                Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .graphicsLayer { rotationZ = spin }.testTag("edit_image")
                    .pointerInput(tool, state, p) {
                        if (tool != EditTool.REDEYE || p == null) return@pointerInput
                        detectTapGestures { at ->
                            // The tapped point of the shown picture → the upright preview → the ≤ 4096-px working picture.
                            val plan = state.plan(p.width, p.height)
                            val src = DoubleArray(2)
                            plan.source(at.x.toDouble() / size.width * plan.outW, at.y.toDouble() / size.height * plan.outH, src)
                            val working = EditSession.working ?: return@detectTapGestures
                            val wx = (src[0] / p.width * working.width).toInt()
                            val wy = (src[1] / p.height * working.height).toInt()
                            val reach = max(24, min(working.width, working.height) / 8)
                            val l = (wx - reach).coerceIn(0, working.width - 1)
                            val t = (wy - reach).coerceIn(0, working.height - 1)
                            val r = (wx + reach).coerceIn(l + 1, working.width)
                            val b = (wy + reach).coerceIn(t + 1, working.height)
                            val part = IntArray((r - l) * (b - t))
                            working.getPixels(part, 0, r - l, l, t, r - l, b - t)
                            val near = max(4, reach / 6)
                            val found = RedEye.detect(part, r - l, b - t, PxRect(wx - l - near, wy - t - near, wx - l + near, wy - t + near)).map { it.shifted(l, t) }
                            // Blobs already held stay; a blob found again is not added twice.
                            val fresh = found.filter { f -> state.redEye.none { it.left == f.left && it.top == f.top && it.width == f.width } }
                            if (fresh.isNotEmpty()) state = state.copy(redEye = state.redEye + fresh, redEyeW = working.width, redEyeH = working.height)
                            status = if (found.isEmpty()) "No red eye found there" else "${state.redEye.size} fixed"
                            Diagnostics.add("photosapp", "editor redeye at $wx,$wy: ${found.size} blob(s)")
                        }
                    },
            ) {
                Image(bitmap.asImageBitmap(), contentDescription = entry.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (showCrop) CropOverlay(cropDraft, aspect, w, h) { cropDraft = it }
            }
        }

        // The tool's own controls, above its bar.
        when (tool) {
            EditTool.STRAIGHTEN -> StepSlider(state.straighten, EditGeometry.STRAIGHTEN_MIN, EditGeometry.STRAIGHTEN_MAX, { "$it°" }) { state = state.copy(straighten = it) }
            EditTool.LIGHT -> StepSlider(state.recipe.light, EditMatrices.LIGHT_MIN, EditMatrices.LIGHT_MAX, { signed(it) }) { state = state.copy(recipe = state.recipe.copy(light = it)) }
            EditTool.COLOUR -> StepSlider(state.recipe.colour, EditMatrices.COLOUR_MIN, EditMatrices.COLOUR_MAX, { signed(it) }) { state = state.copy(recipe = state.recipe.copy(colour = it)) }
            EditTool.FILTERS -> ToolPanel {
                // "None" and the six filters share the width equally, so all seven are on screen at once.
                Row(Modifier.fillMaxSize().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    (listOf<String?>(null) + EditMatrices.FILTERS).forEach { f ->
                        val on = state.recipe.filter == f
                        Box(
                            Modifier.weight(1f).height(36.dp).padding(horizontal = 1.dp).background(if (on) colors.accent else Color.Transparent).testTag("edit_filter:${f ?: "none"}")
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state = state.copy(recipe = state.recipe.copy(filter = f)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(f?.let { EditMatrices.FILTER_LABELS.getValue(it) } ?: "None", style = ShellType.body.copy(color = Color.White), maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            EditTool.REDEYE -> ToolPanel {
                BasicText(status ?: "Tap a red eye", Modifier.align(Alignment.CenterStart).padding(start = 12.dp).testTag("edit_redeye_hint"), style = ShellType.body.copy(color = Color.White))
            }
            else -> {}
        }
        if (tool != EditTool.REDEYE) status?.let {
            BasicText(it, Modifier.align(Alignment.BottomCenter).padding(bottom = PhotosMetrics.BAR_EXPANDED + panelH + 8.dp).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp).testTag("edit_status"),
                style = ShellType.body.copy(color = Color.White))
        }

        val current = tool
        if (current == null) {
            PhotoBar(
                buttons = listOf(
                    PhotoBarButton(Glyph.CROP, "Crop", "edit_crop", enabled = preview != null && !saving) { enter(EditTool.CROP) },
                    PhotoBarButton(Glyph.WAND, "Enhance", "edit_enhance", enabled = preview != null && !saving, on = state.recipe.enhance) {
                        state = state.copy(recipe = state.recipe.copy(enhance = !state.recipe.enhance))
                        Diagnostics.add("photosapp", "editor state ${state.toolName.ifEmpty { "none" }}")
                    },
                    PhotoBarButton(Glyph.ROTATE_RIGHT, "Rotate", "edit_rotate", enabled = preview != null && !saving) {
                        state = state.copy(quarterTurns = (state.quarterTurns + 1) % 4)
                        Diagnostics.add("photosapp", "editor state ${state.toolName.ifEmpty { "none" }}")
                        // The turn is shown as a turn (pass 2 §5: 334–500 ms): the new picture settles in from −90°.
                        scope.launch {
                            spin = -90f
                            withFrameNanos { }
                            MotionClock.animate("edit_rotate", 400, ClockMetrics.easeOut) { spin = -90f * (1f - it) }
                        }
                    },
                    PhotoBarButton(Glyph.SAVE_COPY, "Save a copy", "edit_save", enabled = state.tools.isNotEmpty() && !saving) { save() },
                ),
                menu = listOf(EditTool.STRAIGHTEN, EditTool.LIGHT, EditTool.COLOUR, EditTool.FILTERS, EditTool.REDEYE).map { t ->
                    PhotoMenuEntry(t.label, "edit_tool:${t.id}") { enter(t) }
                },
                fill = PhotosMetrics.EDIT_BAR_FILL, tagPrefix = "edit", isExpanded = { barExpanded }, onExpand = { barExpanded = it }, accent = colors.accent,
            )
        } else {
            PhotoBar(
                buttons = buildList {
                    if (current == EditTool.CROP) add(PhotoBarButton(Glyph.ASPECT_RATIO, "Aspect ratio", "edit_aspect") { aspectMenu = true })
                    add(PhotoBarButton(Glyph.CHECKMARK, "Accept", "edit_accept") { accept() })
                },
                menu = listOf(PhotoMenuEntry("Cancel", "edit_cancel") { cancel() }),
                fill = PhotosMetrics.EDIT_BAR_FILL, tagPrefix = "edit_tool", isExpanded = { barExpanded }, onExpand = { barExpanded = it },
            )
        }
        if (aspectMenu && current == EditTool.CROP) {
            val p = preview
            val choices = listOf("Free" to null, "Original" to p?.let { val plan = state.copy(crop = NormRect.FULL).plan(it.width, it.height); plan.outW.toDouble() / plan.outH }, "Square" to 1.0, "4:3" to 4.0 / 3, "3:2" to 1.5, "16:9" to 16.0 / 9)
            val entries = choices.map { (label, ratio) ->
                PhotoMenuEntry(label, "edit_aspect:${label.lowercase().replace(":", "x")}") {
                    aspect = ratio
                    if (ratio != null && p != null) {
                        val plan = state.copy(crop = NormRect.FULL).plan(p.width, p.height)
                        cropDraft = CropMath.withAspect(cropDraft, ratio, plan.outW.toDouble() / plan.outH)
                    }
                }
            }
            Box(Modifier.fillMaxSize().testTag("edit_aspect_scrim").pointerInput(Unit) { detectTapGestures { aspectMenu = false } })
            androidx.compose.foundation.layout.Column(Modifier.align(Alignment.BottomStart).offset(y = -PhotosMetrics.BAR).fillMaxWidth().height(photoMenuHeight(entries)).background(PhotosMetrics.MENU_FILL).testTag("edit_aspect_menu")) {
                PhotoMenuRows(entries) { aspectMenu = false }
            }
        }
        if (saving) {
            // Pass 2 "Save flow": the photo dimmed, "Saving a copy…".
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }) {
                BasicText("Saving a copy…", Modifier.align(Alignment.Center).testTag("edit_saving"), style = ShellType.subtitle.copy(color = Color.White))
            }
        }
    }

    BackHandler(enabled = !saving) {
        when {
            aspectMenu -> aspectMenu = false
            barExpanded -> dismissOverlay { barExpanded = false }
            tool != null -> cancel()
            else -> activity.finish()
        }
    }
    BackHandler(enabled = saving) {}
}

/** The working picture of the one editor this process shows: the ≤ 4096-px upright decode (r3 D8), read by red-eye. */
private object EditSession {
    @Volatile
    var working: Bitmap? = null
}

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

/** The band a tool's controls sit in, on the tool's bar. */
@Composable
private fun BoxScope.ToolPanel(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.align(Alignment.BottomStart).offset(y = -PhotosMetrics.BAR).fillMaxWidth().height(64.dp).background(PhotosMetrics.EDIT_BAR_FILL).testTag("edit_panel"), content = content)
}

/**
 * A stepped slider (P4; R3 C1's slider form): − and + step by one, a drag along the track sets the nearest step, the
 * value shows at the right. Tags `edit_slider`, `edit_slider_minus`, `edit_slider_plus`, `edit_slider_value`.
 */
@Composable
private fun BoxScope.StepSlider(value: Int, min: Int, max: Int, text: (Int) -> String, onChange: (Int) -> Unit) {
    val colors = LocalShellColors.current
    val now by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    ToolPanel {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("−", Modifier.width(44.dp).testTag("edit_slider_minus").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (now > min) change(now - 1) },
                style = ShellType.title.copy(color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            Box(
                Modifier.weight(1f).fillMaxHeight().testTag("edit_slider").pointerInput(min, max) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun at(x: Float) = (min + ((x / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt())
                        change(at(down.position.x))
                        do {
                            val e = awaitPointerEvent()
                            e.changes.firstOrNull()?.let { c -> if (c.pressed) { change(at(c.position.x)); c.consume() } }
                        } while (e.changes.any { it.pressed })
                    }
                },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2f
                    val x = size.width * (value - min) / (max - min).toFloat()
                    val zero = size.width * (0 - min) / (max - min).toFloat()
                    drawRect(Color(0xFF666666), Offset(0f, y - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
                    drawRect(colors.accent, Offset(minOf(x, zero), y - 1.dp.toPx()), Size(abs(x - zero), 2.dp.toPx()))
                    drawRect(Color.White, Offset(x - 4.dp.toPx(), y - 12.dp.toPx()), Size(8.dp.toPx(), 24.dp.toPx()))
                }
            }
            BasicText("+", Modifier.width(44.dp).testTag("edit_slider_plus").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (now < max) change(now + 1) },
                style = ShellType.title.copy(color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            BasicText(text(value), Modifier.width(48.dp).testTag("edit_slider_value"), style = ShellType.body.copy(color = Color.White))
        }
    }
}

/** The crop rectangle's rules, free of Android types. */
object CropMath {
    const val MIN = 0.05

    /** [rect] with its corner ([left], [top] say which) moved by ([dx], [dy]) fractions; [aspect] (w / h in pixels) is kept when set. */
    fun dragCorner(rect: NormRect, left: Boolean, top: Boolean, dx: Double, dy: Double, aspect: Double?, frameAspect: Double): NormRect {
        var l = rect.left
        var t = rect.top
        var r = rect.right
        var b = rect.bottom
        if (left) l = (l + dx).coerceIn(0.0, r - MIN) else r = (r + dx).coerceIn(l + MIN, 1.0)
        if (top) t = (t + dy).coerceIn(0.0, b - MIN) else b = (b + dy).coerceIn(t + MIN, 1.0)
        if (aspect != null) {
            // The height follows the width; the corner opposite the dragged one stays where it is.
            var w = r - l
            var h = w * frameAspect / aspect
            val room = if (top) b else 1.0 - t
            if (h > room) { h = room; w = h * aspect / frameAspect }
            if (left) l = r - w else r = l + w
            if (top) t = b - h else b = t + h
        }
        return NormRect(l, t, r, b)
    }

    /** The largest rectangle of [aspect] inside [rect], on its centre. [frameAspect] is the frame's own w / h. */
    fun withAspect(rect: NormRect, aspect: Double, frameAspect: Double): NormRect {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        val cx = (rect.left + rect.right) / 2
        val cy = (rect.top + rect.bottom) / 2
        // In pixels the rectangle is (w · frameAspect) : h.
        val (nw, nh) = if (w * frameAspect / h > aspect) (h * aspect / frameAspect) to h else w to (w * frameAspect / aspect)
        return NormRect(cx - nw / 2, cy - nh / 2, cx + nw / 2, cy + nh / 2)
    }
}

/**
 * The crop page's overlay (pass 2 "The editor on a phone"): the photo outside the rectangle at about 35 % brightness, a
 * 1-epx white frame with a thirds grid, four white 18-epx handle discs at the corners. A handle follows the finger
 * exactly from where it went down. Tags `edit_crop_rect`, `edit_crop_handle:<tl|tr|bl|br>`.
 */
@Composable
private fun BoxScope.CropOverlay(rect: NormRect, aspect: Double?, widthPx: Float, heightPx: Float, onChange: (NormRect) -> Unit) {
    val density = LocalDensity.current
    val now by rememberUpdatedState(rect)
    val lock by rememberUpdatedState(aspect)
    val change by rememberUpdatedState(onChange)
    Canvas(Modifier.fillMaxSize()) {
        val l = (rect.left * size.width).toFloat()
        val t = (rect.top * size.height).toFloat()
        val r = (rect.right * size.width).toFloat()
        val b = (rect.bottom * size.height).toFloat()
        val dim = Color.Black.copy(alpha = 0.65f)
        drawRect(dim, Offset.Zero, Size(size.width, t))
        drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(dim, Offset(0f, t), Size(l, b - t))
        drawRect(dim, Offset(r, t), Size(size.width - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(1.dp.toPx()))
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f
            val y = t + (b - t) * i / 3f
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(x + 1.dp.toPx(), t), Offset(x + 1.dp.toPx(), b), 1.dp.toPx())
            drawLine(Color.White, Offset(x, t), Offset(x, b), 1.dp.toPx())
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(l, y + 1.dp.toPx()), Offset(r, y + 1.dp.toPx()), 1.dp.toPx())
            drawLine(Color.White, Offset(l, y), Offset(r, y), 1.dp.toPx())
        }
    }
    Box(
        Modifier.offset { IntOffset((rect.left * widthPx).roundToInt(), (rect.top * heightPx).roundToInt()) }
            .size(with(density) { ((rect.right - rect.left) * widthPx).toFloat().toDp() }, with(density) { ((rect.bottom - rect.top) * heightPx).toFloat().toDp() })
            .testTag("edit_crop_rect"),
    )
    val reach = with(density) { 28.dp.toPx() }
    // One static layer takes the drags: a handle that moved under the finger would measure the finger against itself.
    Box(
        Modifier.fillMaxSize().pointerInput(widthPx, heightPx) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val corners = listOf(Triple(true, true, Offset((now.left * widthPx).toFloat(), (now.top * heightPx).toFloat())), Triple(false, true, Offset((now.right * widthPx).toFloat(), (now.top * heightPx).toFloat())),
                    Triple(true, false, Offset((now.left * widthPx).toFloat(), (now.bottom * heightPx).toFloat())), Triple(false, false, Offset((now.right * widthPx).toFloat(), (now.bottom * heightPx).toFloat())))
                val picked = corners.minByOrNull { (it.third - down.position).getDistance() }?.takeIf { (it.third - down.position).getDistance() <= reach } ?: return@awaitEachGesture
                down.consume()
                do {
                    val e = awaitPointerEvent()
                    e.changes.firstOrNull()?.let { c ->
                        val d = c.positionChange()
                        if (d != Offset.Zero) change(CropMath.dragCorner(now, picked.first, picked.second, d.x / widthPx.toDouble(), d.y / heightPx.toDouble(), lock, widthPx.toDouble() / heightPx))
                        c.consume()
                    }
                } while (e.changes.any { it.pressed })
            }
        },
    )
    val handle = 18.dp
    for ((id, isLeft, isTop) in listOf(Triple("tl", true, true), Triple("tr", false, true), Triple("bl", true, false), Triple("br", false, false))) {
        val cx = ((if (isLeft) rect.left else rect.right) * widthPx).toFloat()
        val cy = ((if (isTop) rect.top else rect.bottom) * heightPx).toFloat()
        val half = with(density) { handle.toPx() / 2f }
        Box(Modifier.offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }.size(handle).background(Color.White, CircleShape).testTag("edit_crop_handle:$id"))
    }
}
