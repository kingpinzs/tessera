package app.tileshell.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.cortana.ui.AppBarButton
import app.tileshell.cortana.ui.CortanaAppBar
import app.tileshell.cortana.ui.CortanaUi
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay
import java.util.Date
import android.text.format.DateFormat as AndroidDateFormat

/**
 * The playback page (r11/voice-recorder.md §4) and, over the same geometry, the trim page (U3). No page title:
 * the name and date centred (4.1), the markers (4.2), the play disc (4.3), the flag (4.4), the scrubber with
 * elapsed left and total right (4.5), and the 48-epx app bar Share · Trim · Delete · Rename · "…" (4.7) — for
 * another app's recording Share and "…" alone (T15-3). The page is drawn inside the content area the host
 * leaves between the drawn status and nav bars.
 */
@Composable
fun PlaybackPage(
    recording: Recording,
    caps: RecordingCaps,
    markers: List<Long>,
    player: RecorderPlayer,
    onFlag: (atMs: Long) -> Unit,
    onShare: () -> Unit,
    onTrim: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onMore: () -> Unit,
) {
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(recording.durationMs) }
    // The position label and the thumb are the only things here that move on their own: four reads a second,
    // finer than the label's own resolution (the now-playing page's rule).
    LaunchedEffect(player) {
        while (true) {
            position = player.positionMs
            playing = player.isPlaying
            duration = player.durationMs(recording.durationMs)
            delay(250)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("rec_playback")) {
        val w = maxWidth.value
        val h = maxHeight.value
        val appBarTop = h - CortanaUi.APPBAR_EPX
        val scrubCy = appBarTop - RecorderMetrics.TRACK_ABOVE_APPBAR
        val discCy = h * RecorderMetrics.DISC_AT

        Header(recording)
        if (markers.isNotEmpty()) {
            MarkerRow(
                markers = markers,
                label = RecorderFormat::playbackMarker,
                modifier = Modifier.offset(y = (RecorderMetrics.NAME_CAP_TOP + RecorderMetrics.DATE_BELOW_NAME + RecorderMetrics.MARKERS_BELOW_DATE).dp),
            )
        }
        PlayDisc(w, discCy, playing) { player.toggle() }
        if (caps.markers) {
            val t = RecorderMetrics.CONTROL_TOUCH
            Box(
                Modifier
                    .offset(x = (w / 2f - t / 2f).dp, y = (discCy + RecorderMetrics.PLAY_FLAG_BELOW_DISC - t / 2f).dp)
                    .size(t.dp)
                    .clickable { onFlag(player.positionMs) }
                    .testTag("rec_flag"),
                contentAlignment = Alignment.Center,
            ) {
                RecorderGlyph(Glyph.FLAG, RecorderMetrics.FLAG_FONT, LocalShellColors.current.text)
            }
        }
        Scrubber(
            w = w,
            cy = scrubCy,
            leftLabel = RecorderFormat.position(position),
            leftTag = "rec_position",
            rightLabel = RecorderFormat.duration(duration),
            rightTag = "rec_total",
            durationMs = duration,
            markers = markers,
            thumbs = listOf(position),
            onSeek = { fraction -> player.seekTo((duration * fraction).toLong()) },
        )
        val buttons = buildList {
            add(barButton("rec_bar:share", "share", Glyph.SHARE, onShare))
            if (caps.trim) add(barButton("rec_bar:trim", "trim", Glyph.CUT, onTrim))
            if (caps.delete) add(barButton("rec_bar:delete", "delete", Glyph.DELETE, onDelete))
            if (caps.rename) add(barButton("rec_bar:rename", "rename", Glyph.EDIT, onRename))
            add(barButton("rec_bar:more", "more", Glyph.MORE_HORIZONTAL, onMore))
        }
        CortanaAppBar(labelled = false, tag = "rec_appbar", buttons = buttons, modifier = Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Trim (U3): the playback page with two accent handles on the track marking what to keep, and an app bar of
 * Save and Cancel. Nothing is written until Save; Save writes the kept part as a NEW file and only then removes
 * the original (a real cut, E16). The disc previews the kept part.
 */
@Composable
fun TrimPage(
    recording: Recording,
    player: RecorderPlayer,
    onSave: (startMs: Long, endMs: Long) -> Unit,
    onCancel: () -> Unit,
) {
    var duration by remember { mutableLongStateOf(recording.durationMs) }
    var start by remember { mutableLongStateOf(0L) }
    var end by remember { mutableLongStateOf(recording.durationMs) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        player.pause()
        while (true) {
            val d = player.durationMs(recording.durationMs)
            if (d != duration) {
                if (end == duration || end > d) end = d
                duration = d
            }
            playing = player.isPlaying
            // The preview stops at the out point.
            if (playing && player.positionMs >= end) player.pause()
            delay(100)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("rec_trim")) {
        val w = maxWidth.value
        val h = maxHeight.value
        val appBarTop = h - CortanaUi.APPBAR_EPX
        val scrubCy = appBarTop - RecorderMetrics.TRACK_ABOVE_APPBAR
        val discCy = h * RecorderMetrics.DISC_AT
        Header(recording)
        PlayDisc(w, discCy, playing) {
            if (playing) player.pause() else {
                player.seekTo(start)
                player.play()
            }
        }
        Scrubber(
            w = w,
            cy = scrubCy,
            leftLabel = RecorderFormat.position(start),
            leftTag = "rec_trim_in",
            rightLabel = RecorderFormat.position(end),
            rightTag = "rec_trim_out",
            durationMs = duration,
            markers = emptyList(),
            thumbs = listOf(start, end),
            thumbTags = listOf("rec_trim_start", "rec_trim_end"),
            selection = start to end,
            onSeek = { fraction ->
                // A touch moves whichever handle is nearer.
                val at = (duration * fraction).toLong()
                if (kotlin.math.abs(at - start) <= kotlin.math.abs(at - end)) start = at.coerceAtMost(end) else end = at.coerceAtLeast(start)
            },
        )
        CortanaAppBar(
            labelled = false,
            tag = "rec_trim_appbar",
            buttons = listOf(
                barButton("rec_trim_save", "save", Glyph.SAVE) { onSave(start, end) },
                barButton("rec_trim_cancel", "cancel", Glyph.DISMISS, onCancel),
            ),
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private fun barButton(tag: String, label: String, glyph: String, onClick: () -> Unit) =
    AppBarButton(tag, label, true, onClick) { color, modifier -> RecorderGlyph(glyph, CortanaUi.APPBAR_GLYPH_EPX, color, modifier) }

/** 4.1: the name in base 15 semibold, centred; the date and time under it in grey caption, 22 epx lower. */
@Composable
private fun Header(recording: Recording) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val date = remember(recording.recordedAtMs) {
        val d = Date(recording.recordedAtMs)
        "${AndroidDateFormat.getDateFormat(context).format(d)} ${AndroidDateFormat.getTimeFormat(context).format(d)}"
    }
    CentredCapText(recording.name, ShellType.base.copy(color = colors.text), RecorderMetrics.NAME_CAP_TOP, "rec_play_name")
    CentredCapText(date, ShellType.caption.copy(color = RecorderMetrics.SECONDARY), RecorderMetrics.NAME_CAP_TOP + RecorderMetrics.DATE_BELOW_NAME, "rec_play_date")
}

/** 4.3: the 96-epx grey disc with the outlined play triangle, or pause while playing. */
@Composable
private fun PlayDisc(w: Float, cy: Float, playing: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .offset(x = (w / 2f - RecorderMetrics.PLAY_DISC / 2f).dp, y = (cy - RecorderMetrics.PLAY_DISC / 2f).dp)
            .size(RecorderMetrics.PLAY_DISC.dp)
            .background(RecorderMetrics.PLAY_DISC_FILL, CircleShape)
            .clickable(onClick = onTap)
            .testTag("rec_play"),
        contentAlignment = Alignment.Center,
    ) {
        if (playing) RecorderGlyph(Glyph.PAUSE, RecorderMetrics.PLAY_FONT * 0.875f, Color.White)
        else RecorderGlyph(Glyph.PLAY, RecorderMetrics.PLAY_FONT, Color.White)
    }
}

/**
 * 4.5 / 4.6: elapsed left and total right in caption, a 2-epx track (played part accent), a hollow 24-epx accent
 * ring for the thumb and 6-epx accent dots for the markers — each dot its own node so a row reads its place
 * (`rec_track_marker:<n>`). A drag scrubs and a tap jumps (X23). [selection] paints the kept part on the trim page.
 */
@Composable
private fun Scrubber(
    w: Float,
    cy: Float,
    leftLabel: String,
    leftTag: String,
    rightLabel: String,
    rightTag: String,
    durationMs: Long,
    markers: List<Long>,
    thumbs: List<Long>,
    thumbTags: List<String> = emptyList(),
    selection: Pair<Long, Long>? = null,
    onSeek: (Float) -> Unit,
) {
    val colors = LocalShellColors.current
    val trackLeft = RecorderMetrics.LABEL_INSET + RecorderMetrics.LABEL_BOX + RecorderMetrics.LABEL_GAP
    val trackW = (w - 2f * trackLeft).coerceAtLeast(1f)
    val r = RecorderMetrics.THUMB / 2f
    fun fractionOf(ms: Long) = if (durationMs <= 0L) 0f else (ms.toFloat() / durationMs).coerceIn(0f, 1f)
    // The thumb's centre runs from the track's left end to its right end (the whole track is time).
    fun xOf(ms: Long) = trackLeft + trackW * fractionOf(ms)
    val labelTop = cy - CapMetrics.capHeight(ShellType.caption.fontSize.value) / 2f
    Box(Modifier.offset(x = RecorderMetrics.LABEL_INSET.dp).width(RecorderMetrics.LABEL_BOX.dp).offset(y = capPad(labelTop, ShellType.caption).dp)) {
        BasicText(leftLabel, style = ShellType.caption.copy(color = colors.text), maxLines = 1, modifier = Modifier.testTag(leftTag))
    }
    Box(Modifier.offset(x = (w - RecorderMetrics.LABEL_INSET - RecorderMetrics.LABEL_BOX).dp).width(RecorderMetrics.LABEL_BOX.dp).offset(y = capPad(labelTop, ShellType.caption).dp), contentAlignment = Alignment.TopEnd) {
        BasicText(rightLabel, style = ShellType.caption.copy(color = colors.text), maxLines = 1, modifier = Modifier.testTag(rightTag))
    }
    Box(
        Modifier
            .offset(x = (trackLeft - r).dp, y = (cy - r).dp)
            .width((trackW + 2f * r).dp)
            .height(RecorderMetrics.THUMB.dp)
            .testTag("rec_scrubber")
            .pointerInput(durationMs) {
                detectHorizontalDragGestures { change, _ -> onSeek(((change.position.x / density - r) / trackW).coerceIn(0f, 1f)) }
            }
            .pointerInput(durationMs) {
                detectTapGestures { onSeek(((it.x / density - r) / trackW).coerceIn(0f, 1f)) }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val px = density
            val y = size.height / 2f
            val left = r * px
            val right = left + trackW * px
            val thick = RecorderMetrics.TRACK * px
            drawRect(RecorderMetrics.UNPLAYED, Offset(left, y - thick / 2f), androidx.compose.ui.geometry.Size(right - left, thick))
            if (selection != null) {
                val a = left + trackW * px * fractionOf(selection.first)
                val b = left + trackW * px * fractionOf(selection.second)
                drawRect(colors.accent, Offset(a, y - thick / 2f), androidx.compose.ui.geometry.Size((b - a).coerceAtLeast(0f), thick))
            } else {
                val played = trackW * px * fractionOf(thumbs.firstOrNull() ?: 0L)
                drawRect(colors.accent, Offset(left, y - thick / 2f), androidx.compose.ui.geometry.Size(played, thick))
            }
            for (t in thumbs) {
                val cx = left + trackW * px * fractionOf(t)
                val stroke = RecorderMetrics.THUMB_STROKE * px
                drawCircle(colors.accent, radius = r * px - stroke / 2f, center = Offset(cx, y), style = Stroke(stroke))
            }
        }
    }
    // The marker dots and the trim handles as nodes of their own: a slider's position is in no node's attributes.
    markers.forEachIndexed { i, at ->
        val d = RecorderMetrics.MARKER_DOT
        Box(
            Modifier
                .offset(x = (xOf(at) - d / 2f).dp, y = (cy - d / 2f).dp)
                .size(d.dp)
                .background(colors.accent, CircleShape)
                .testTag("rec_track_marker:${i + 1}"),
        )
    }
    thumbs.forEachIndexed { i, at ->
        val tag = thumbTags.getOrNull(i) ?: return@forEachIndexed
        Box(
            Modifier
                .offset(x = (xOf(at) - r).dp, y = (cy - r).dp)
                .size(RecorderMetrics.THUMB.dp)
                .testTag(tag),
        )
    }
}

/**
 * Settings behind every "…" (1.7, 4.8): an About page (U13's block — name, version, privacy), with no
 * Feedback (T15-43: Microsoft's online service, nothing offline to send it to). Phase 03's microphone
 * checklist row is the Android stand-in for 10166's "Microphone settings" link, so the page does not repeat it.
 */
@Composable
fun RecorderAboutPage() {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    androidx.compose.foundation.layout.Column(
        Modifier
            .fillMaxSize()
            .testTag("about_page")
            .padding(12.dp),
    ) {
        BasicText("Voice Recorder", style = ShellType.subtitle.copy(color = colors.text), modifier = Modifier.testTag("about_name"))
        BasicText(app.tileshell.brand.Brand.PRODUCT_NAME, style = ShellType.body.copy(color = colors.subtleText))
        BasicText("Version ${version ?: "unknown"}", style = ShellType.body.copy(color = colors.subtleText), modifier = Modifier.testTag("about_version"))
        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        BasicText("Privacy", style = ShellType.base.copy(color = colors.text))
        BasicText(
            "Recordings stay on this phone, as .m4a files in its Recordings folder. Voice Recorder sends nothing anywhere and makes no network request.",
            style = ShellType.body.copy(color = colors.subtleText),
            modifier = Modifier.testTag("about_privacy"),
        )
    }
}
