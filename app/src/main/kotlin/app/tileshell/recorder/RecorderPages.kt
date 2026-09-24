package app.tileshell.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.start.Edit
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat

/**
 * Voice Recorder's one page (r11/voice-recorder.md 1.1: no pivot, no ≡ pane, no page title) in its two states.
 * Both are drawn inside the content area between phase 01's drawn status bar and nav bar, which the host draws.
 */

// ---------------------------------------------------------------------------------------------------------------
// The record state (§2)
// ---------------------------------------------------------------------------------------------------------------

/**
 * The big button, and while a take runs its timer, level rings, Pause and Flag and markers (2.1–2.14). With no
 * recordings at all it says "No recordings found" at R7 §3.5.9's place (2.11). Without the microphone grant it
 * says so and offers the grant here (E21, phase 10 E18's form), and the button starts nothing.
 */
@Composable
fun RecordState(
    take: RecorderClient.Take,
    hasRecordings: Boolean,
    micGranted: Boolean,
    onRecordTap: () -> Unit,
    onPauseTap: () -> Unit,
    onFlagTap: () -> Unit,
    onMore: () -> Unit,
    onGrantMic: () -> Unit,
) {
    val colors = LocalShellColors.current
    val active = take.active
    val recording = take.phase == RecorderState.RECORDING
    // 7.2: the rings come ≈467 ms after the stop state; U11: they stop while paused.
    var ringsOn by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        ringsOn = false
        if (recording) {
            kotlinx.coroutines.delay(RecorderMetrics.RINGS_APPEAR_MS)
            ringsOn = true
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("rec_page:record")
            .semantics { selected = true },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        // U9: the disc centred in the space between the status bar and the bottom.
        val discCy = h / 2f
        val cx = w / 2f

        if (!active && !hasRecordings) {
            Box(Modifier.padding(start = RecorderMetrics.EMPTY_LEFT.dp, top = capPad(RecorderMetrics.EMPTY_CAP_BELOW_BAR, ShellType.subtitle).dp)) {
                BasicText("No recordings found", style = ShellType.subtitle.copy(color = colors.text), modifier = Modifier.testTag("rec_empty"))
            }
        }
        if (!micGranted && !active) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = (RecorderMetrics.EMPTY_CAP_BELOW_BAR + 40f).dp)) {
                BasicText(
                    "Voice Recorder can't use the microphone yet. You can allow it here, or from Microphone in ${Brand.ASSISTANT_NAME}'s settings.",
                    style = ShellType.body.copy(color = colors.subtleText),
                    modifier = Modifier.testTag("rec_mic_notice"),
                )
                BasicText(
                    "allow access",
                    style = ShellType.body.copy(color = colors.accent),
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onGrantMic).testTag("rec_mic_grant"),
                )
            }
        }

        if (active) {
            // 2.8–2.10: hh:mm:ss, its leading zero fields dimmed, the digits' centre 141.5 epx above the disc's.
            val (dim, live) = RecorderFormat.timer(take.elapsedMs)
            val style = ShellType.subheader.copy(fontSize = RecorderMetrics.TIMER_FONT.sp)
            val capTop = discCy - RecorderMetrics.TIMER_ABOVE_DISC - app.tileshell.ui.tokens.CapMetrics.capHeight(RecorderMetrics.TIMER_FONT) / 2f
            Box(Modifier.fillMaxWidth().padding(top = capPad(capTop, style).dp), contentAlignment = Alignment.TopCenter) {
                BasicText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = RecorderMetrics.TIMER_ZERO)) { append(dim) }
                        withStyle(SpanStyle(color = colors.text)) { append(live) }
                    },
                    style = style,
                    maxLines = 1,
                    modifier = Modifier.testTag("rec_elapsed"),
                )
            }
        }

        if (ringsOn && recording) {
            val level = take.level.coerceIn(0f, 1f)
            val inner = RecorderMetrics.RING_INNER_MIN + (RecorderMetrics.RING_INNER_MAX - RecorderMetrics.RING_INNER_MIN) * level
            val outer = RecorderMetrics.RING_OUTER_MIN + (RecorderMetrics.RING_OUTER_MAX - RecorderMetrics.RING_OUTER_MIN) * level
            Ring(outer, RecorderMetrics.RING_STROKE, colors.accent, Modifier.offset(x = (cx - outer / 2f).dp, y = (discCy - outer / 2f).dp).testTag("rec_ring_outer"))
            Ring(inner, RecorderMetrics.RING_STROKE, colors.accent, Modifier.offset(x = (cx - inner / 2f).dp, y = (discCy - inner / 2f).dp).testTag("rec_ring_inner"))
        }

        // 2.1 / 2.2: one disc, the record button idle and the stop button while a take runs; grey while the take
        // starts or saves (7.1's disabled interval).
        val busy = take.phase == RecorderState.STARTING || take.phase == RecorderState.SAVING
        val discColor = when {
            busy -> RecorderMetrics.DISC_DISABLED
            !micGranted && !active -> colors.accent.copy(alpha = 0.5f)
            else -> colors.accent
        }
        Box(
            Modifier
                .offset(x = (cx - RecorderMetrics.DISC_EPX / 2f).dp, y = (discCy - RecorderMetrics.DISC_EPX / 2f).dp)
                .size(RecorderMetrics.DISC_EPX.dp)
                .background(discColor, CircleShape)
                .clickable(enabled = !busy && (micGranted || active), onClick = onRecordTap)
                .testTag("rec_button"),
            contentAlignment = Alignment.Center,
        ) {
            if (active) RecorderGlyph(Glyph.STOP, RecorderMetrics.DISC_STOP_FONT, Color.White)
            else RecorderGlyph(Glyph.MIC_FILLED, RecorderMetrics.DISC_MIC_FONT, Color.White)
        }

        if (active) {
            // 2.12: Pause (the microphone in its place while paused, U11) and Flag, ±46.7 epx, 139 epx below.
            val rowCy = discCy + RecorderMetrics.CONTROLS_BELOW_DISC
            val t = RecorderMetrics.CONTROL_TOUCH
            val paused = take.phase == RecorderState.PAUSED
            Box(
                Modifier
                    .offset(x = (cx - RecorderMetrics.CONTROLS_DX - t / 2f).dp, y = (rowCy - t / 2f).dp)
                    .size(t.dp)
                    .clickable(enabled = !busy, onClick = onPauseTap)
                    .testTag("rec_pause"),
                contentAlignment = Alignment.Center,
            ) {
                if (paused) RecorderGlyph(Glyph.MIC_FILLED, RecorderMetrics.PAUSE_FONT, colors.text)
                else RecorderGlyph(Glyph.PAUSE, RecorderMetrics.PAUSE_FONT, colors.text)
            }
            Box(
                Modifier
                    .offset(x = (cx + RecorderMetrics.CONTROLS_DX - t / 2f).dp, y = (rowCy - t / 2f).dp)
                    .size(t.dp)
                    .clickable(enabled = !busy, onClick = onFlagTap)
                    .testTag("rec_flag"),
                contentAlignment = Alignment.Center,
            ) {
                RecorderGlyph(Glyph.FLAG, RecorderMetrics.FLAG_FONT, colors.text)
            }
            // 2.14: "⚑ mm:ss" in one centred row 45 epx above the bottom, oldest first (Y, the phone build).
            if (take.markers.isNotEmpty()) {
                MarkerRow(
                    markers = take.markers,
                    label = RecorderFormat::recordingMarker,
                    modifier = Modifier.offset(y = (h - RecorderMetrics.MARKERS_ABOVE_BOTTOM - 12f).dp),
                )
            }
        } else {
            // 2.16: the minimal bar, dots only, with Settings behind them (1.7; Feedback is out, T15-43).
            MinimalBar(onMore, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** A row of "⚑ time" entries at 4.2's 63.5-epx pitch, centred, scrolling sideways when it outgrows the screen. */
@Composable
fun MarkerRow(markers: List<Long>, label: (Long) -> String, modifier: Modifier = Modifier) {
    val colors = LocalShellColors.current
    Box(modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("rec_markers"), verticalAlignment = Alignment.CenterVertically) {
            markers.forEachIndexed { i, at ->
                Row(Modifier.width(RecorderMetrics.MARKER_PITCH.dp), verticalAlignment = Alignment.CenterVertically) {
                    RecorderGlyph(Glyph.FLAG_FILLED, RecorderMetrics.MARKER_FLAG_FONT, colors.accent)
                    BasicText(label(at), style = ShellType.body.copy(color = colors.text), maxLines = 1, modifier = Modifier.padding(start = 4.dp).testTag("rec_marker:${i + 1}"))
                }
            }
        }
    }
}

/** 2.16 / 3.10: the minimal app bar — 24 epx, dots only, the "…" button 48 epx flush right. */
@Composable
fun MinimalBar(onMore: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(RecorderMetrics.MINIMAL_BAR.dp)
            .background(app.tileshell.cortana.ui.CortanaUi.APPBAR_FILL),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(app.tileshell.cortana.ui.CortanaUi.APPBAR_MORE_EPX.dp)
                .height(RecorderMetrics.MINIMAL_BAR.dp)
                .clickable(onClick = onMore)
                .testTag("rec_more"),
            contentAlignment = Alignment.Center,
        ) {
            RecorderGlyph(Glyph.MORE_HORIZONTAL, 20f, Color.White)
        }
    }
}

/**
 * The record → recording change is a one-frame cut (7.1): no animation for the shell to time, so its row grades
 * it by pixels (T15-32), and this line is the secondary clock — from the tap to the first frame that drew the
 * stop state, every frame between them counted (the grey start-up interval of 7.1 is in it).
 */
@Composable
fun RecordCutClock(t0: Long?, phase: Int, onLogged: () -> Unit) {
    val current by rememberUpdatedState(phase)
    LaunchedEffect(t0) {
        if (t0 == null) return@LaunchedEffect
        val trace = MotionTrace("rec_state", t0)
        var drawn = false
        while (true) {
            val done = withFrameNanos { nanos ->
                drawn = current == RecorderState.RECORDING || current == RecorderState.PAUSED
                // A refused start (the microphone busy, the storage floor) never draws the stop state: no cut.
                val refused = current == RecorderState.IDLE && trace.frames > 2
                trace.frame(nanos, if (drawn) 1f else 0f)
                drawn || refused || nanos / 1_000_000L - t0 > CUT_GIVE_UP_MS
            }
            if (done) break
        }
        if (drawn) Diagnostics.add("motion", trace.message())
        onLogged()
    }
}

private const val CUT_GIVE_UP_MS = 10_000L

// ---------------------------------------------------------------------------------------------------------------
// The list state (§3)
// ---------------------------------------------------------------------------------------------------------------

/**
 * The search box and "Showing …" line (3.1–3.2, T15-16), the date groups (3.3) of two-line rows (3.4), and the
 * record button docked bottom-centre on the minimal bar (3.8). With READ_MEDIA_AUDIO off, other apps'
 * recordings are hidden and one line says so, naming the Setup checklist's Music row (T15-19).
 */
@Composable
fun ListState(
    snapshot: RecorderLibrary.Snapshot,
    shown: List<Recording>,
    query: String,
    kind: ShowingKind,
    onQuery: (String) -> Unit,
    onShowing: (xEpx: Float, bottomEpx: Float) -> Unit,
    onOpen: (Recording) -> Unit,
    onHold: (Recording, xEpx: Float, topEpx: Float, bottomEpx: Float) -> Unit,
    onRecord: () -> Unit,
    micGranted: Boolean,
    onMore: () -> Unit,
    onGrantAudio: () -> Unit,
) {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val total = snapshot.recordings.size
    var pageTopPx by remember { mutableStateOf(0f) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { pageTopPx = it.boundsInWindow().top }
            .testTag("rec_page:list")
            .semantics { selected = true },
    ) {
        val h = maxHeight.value
        fun toEpx(px: Float) = with(density) { (px - pageTopPx).toDp().value }
        Column(Modifier.fillMaxSize()) {
            if (total == 0) {
                // 2.11: the empty page's line, at R7 §3.5.9's place; with nothing listed there is nothing to search.
                Box(Modifier.padding(start = RecorderMetrics.EMPTY_LEFT.dp, top = capPad(RecorderMetrics.EMPTY_CAP_BELOW_BAR, ShellType.subtitle).dp)) {
                    BasicText("No recordings found", style = ShellType.subtitle.copy(color = colors.text), modifier = Modifier.testTag("rec_empty"))
                }
            } else {
                SearchBox(query, onQuery)
                ShowingLine(kind) { xPx, bottomPx -> onShowing(with(density) { xPx.toDp().value }, toEpx(bottomPx)) }
            }
            if (!snapshot.othersVisible) {
                BasicText(
                    "Other apps' recordings are hidden. Turn on Music in the Setup checklist to see them.",
                    style = ShellType.caption.copy(color = colors.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = RecorderMetrics.SIDE.dp, vertical = 6.dp)
                        .clickable(onClick = onGrantAudio)
                        .testTag("rec_list_notice"),
                )
            }
            if (total > 0 && shown.isEmpty()) {
                Box(Modifier.padding(start = RecorderMetrics.EMPTY_LEFT.dp, top = 12.dp)) {
                    BasicText("No recordings found", style = ShellType.subtitle.copy(color = colors.text), modifier = Modifier.testTag("rec_empty"))
                }
            }
            val groups = remember(shown) {
                RecordingGroups.group(shown, System.currentTimeMillis(), ZoneId.systemDefault(), Locale.getDefault())
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("rec_list"),
                contentPadding = PaddingValues(bottom = (RecorderMetrics.DOCKED_ABOVE_NAV + RecorderMetrics.DOCKED_EPX / 2f).dp),
            ) {
                groups.forEach { (label, rows) ->
                    item(key = "h:$label") { GroupHeader(label) }
                    rows.forEach { r ->
                        item(key = r.id) {
                            RecordingRow(
                                r,
                                dateText = remember(r.recordedAtMs) {
                                    val d = Date(r.recordedAtMs)
                                    "${AndroidDateFormat.getDateFormat(context).format(d)} ${AndroidDateFormat.getTimeFormat(context).format(d)}"
                                },
                                onTap = { onOpen(r) },
                                onHold = { xPx, topPx, bottomPx -> onHold(r, with(density) { xPx.toDp().value }, toEpx(topPx), toEpx(bottomPx)) },
                            )
                        }
                    }
                }
            }
        }
        MinimalBar(onMore, Modifier.align(Alignment.BottomStart))
        // 3.8: the docked button, 76 epx, centred on W/2, its centre 63 epx above the nav bar's top.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (h - RecorderMetrics.DOCKED_ABOVE_NAV - RecorderMetrics.DOCKED_EPX / 2f).dp)
                .size(RecorderMetrics.DOCKED_EPX.dp)
                .background(if (micGranted) colors.accent else colors.accent.copy(alpha = 0.5f), CircleShape)
                .clickable(enabled = micGranted, onClick = onRecord)
                .testTag("rec_button"),
            contentAlignment = Alignment.Center,
        ) {
            RecorderGlyph(Glyph.MIC_FILLED, RecorderMetrics.DOCKED_MIC_FONT, Color.White)
        }
    }
}

/** 3.1: "Search Recordings", 32 epx, 12-epx margins (a UWP TextBox: a 1-epx border, the hint in grey). */
@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val colors = LocalShellColors.current
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = ShellType.body.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .padding(start = RecorderMetrics.SIDE.dp, end = RecorderMetrics.SIDE.dp, top = RecorderMetrics.SIDE.dp)
            .fillMaxWidth()
            .height(RecorderMetrics.SEARCH_EPX.dp)
            .testTag("rec_search"),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().border(1.dp, colors.text.copy(alpha = 0.6f)).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) BasicText("Search Recordings", style = ShellType.body.copy(color = RecorderMetrics.SECONDARY))
                inner()
            }
        },
    )
}

/** 3.2: "Showing" + the kind in accent, a link that opens the kinds (U6). */
@Composable
private fun ShowingLine(kind: ShowingKind, onOpen: (xPx: Float, bottomPx: Float) -> Unit) {
    val colors = LocalShellColors.current
    var linkX by remember { mutableStateOf(0f) }
    var bottom by remember { mutableStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(RecorderMetrics.SHOWING_BLOCK.dp)
            .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
            .padding(start = RecorderMetrics.SIDE.dp, top = capPad(RecorderMetrics.SHOWING_CAP_TOP, ShellType.body).dp),
    ) {
        BasicText("Showing ", style = ShellType.body.copy(color = colors.text), modifier = Modifier.testTag("rec_showing"))
        BasicText(
            kind.label,
            style = ShellType.body.copy(color = colors.accent),
            modifier = Modifier
                .onGloballyPositioned { linkX = it.boundsInWindow().center.x }
                .clickable { onOpen(linkX, bottom) }
                .testTag("rec_filter"),
        )
    }
}

/** 3.3: the group header in accent, base class, at x 12, its 1-epx rule 19 epx below its cap top. */
@Composable
private fun GroupHeader(label: String) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(RecorderMetrics.HEADER_BLOCK.dp)) {
        BasicText(
            label,
            style = ShellType.base.copy(color = colors.accent),
            maxLines = 1,
            modifier = Modifier
                .padding(start = RecorderMetrics.SIDE.dp, top = capPad(RecorderMetrics.HEADER_CAP_TOP, ShellType.base).dp)
                .testTag("rec_group:$label"),
        )
        Box(
            Modifier
                .padding(start = RecorderMetrics.SIDE.dp, end = RecorderMetrics.SIDE.dp)
                .offset(y = (RecorderMetrics.HEADER_CAP_TOP + RecorderMetrics.RULE_BELOW_CAP).dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(RecorderMetrics.RULE),
        )
    }
}

/**
 * 3.4: name in body; line 2 the date and time in grey caption, 22 epx below line 1's cap top, the duration
 * right-aligned on it at a 12-epx inset; 56-epx pitch. Every text carries its own tag (the MUSIC8 lesson).
 * A tap opens the playback page (1.5); a hold, with the shell's one hold threshold, the menu (1.6).
 */
@Composable
private fun RecordingRow(r: Recording, dateText: String, onTap: () -> Unit, onHold: (xPx: Float, topPx: Float, bottomPx: Float) -> Unit) {
    val colors = LocalShellColors.current
    var top by remember { mutableStateOf(0f) }
    var bottom by remember { mutableStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(RecorderMetrics.ROW_EPX.dp)
            .onGloballyPositioned { top = it.boundsInWindow().top; bottom = it.boundsInWindow().bottom }
            .pointerInput(r.id) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var cancelled = false
                    val up = withTimeoutOrNull(Edit.HOLD_MS) {
                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                    }
                    pressed = false
                    when {
                        up != null -> onTap()
                        cancelled -> Unit
                        else -> onHold(down.position.x, top, bottom)
                    }
                }
            }
            // R7 §3.5.7: the pressed row's fill, full width.
            .background(if (pressed) Color(0xFF3F4440) else Color.Transparent)
            .testTag("rec_row:${r.id}"),
    ) {
        BasicText(
            r.name,
            style = ShellType.body.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = RecorderMetrics.SIDE.dp, end = RecorderMetrics.SIDE.dp, top = capPad(RecorderMetrics.ROW_CAP_TOP, ShellType.body).dp)
                .testTag("rec_name:${r.id}"),
        )
        val line2Top = capPad(RecorderMetrics.ROW_CAP_TOP + RecorderMetrics.LINE2_BELOW_CAP, ShellType.caption)
        Row(Modifier.fillMaxWidth().padding(start = RecorderMetrics.SIDE.dp, end = RecorderMetrics.SIDE.dp, top = line2Top.dp)) {
            BasicText(
                dateText,
                style = ShellType.caption.copy(color = RecorderMetrics.SECONDARY),
                maxLines = 1,
                modifier = Modifier.weight(1f).testTag("rec_date:${r.id}"),
            )
            BasicText(
                RecorderFormat.duration(r.durationMs),
                style = ShellType.caption.copy(color = RecorderMetrics.SECONDARY),
                maxLines = 1,
                modifier = Modifier.testTag("rec_duration:${r.id}"),
            )
        }
    }
}
