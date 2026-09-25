package app.tileshell.clock

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay

private fun capPad(capTop: Float, size: Float): Dp = CapMetrics.topPaddingForCapTop(capTop, size).dp

/** `[stopwatch] elapsed=<ms> uptime=<ms>` (T15-9): the tab's resume and every 5 s while running (the store logs start / stop / lap / reset). */
private fun logStopwatch(store: ClockStore) {
    Diagnostics.add("stopwatch", "elapsed=${store.stopwatchElapsed()} uptime=${SystemClock.uptimeMillis()}")
}

/** The elapsed time, per frame while running (the hundredths move), read once when stopped. */
@Composable
private fun rememberStopwatchElapsed(store: ClockStore, running: Boolean, generation: Any?): Long {
    var elapsed by remember { mutableLongStateOf(store.stopwatchElapsed()) }
    LaunchedEffect(running, generation) {
        elapsed = store.stopwatchElapsed()
        while (running) {
            withFrameNanos { }
            elapsed = store.stopwatchElapsed()
        }
    }
    return elapsed
}

@Composable
private fun StopwatchLogLoop(store: ClockStore, running: Boolean) {
    LaunchedEffect(running) {
        logStopwatch(store)
        while (running) {
            delay(5_000L)
            logStopwatch(store)
        }
    }
}

/**
 * 7.1: "hh:mm:" grey and the seconds in the text colour at the main size (digit height 32 epx ≈ 45.7-epx heavy),
 * then the hundredths at 55 % (17.7 epx ≈ 25.3) after the decimal point, bottom-aligned on the same baseline.
 */
@Composable
private fun stopwatchDigits(text: String, dim: Color, bright: Color, scale: Float = 1f): AnnotatedString {
    val main = text.substringBefore('.')
    val cents = text.substringAfter('.')
    val (head, secs) = ClockText.splitSeconds(main)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = dim)) { append(head) }
        withStyle(SpanStyle(color = bright)) { append(secs) }
        withStyle(SpanStyle(color = bright, fontSize = (25.3f * scale).sp)) { append(".$cents") }
    }
}

/** One lap row's text with its spans (7.4, U6): the dim index, the lap time (hh:mm grey, ss.cc bright), the split in grey caption. */
internal fun lapText(n: Int, lapMs: Long, splitMs: Long, dim: Color, bright: Color): AnnotatedString {
    val line = ClockText.lapLine(n, lapMs, splitMs)
    val lap = ClockText.stopwatch(lapMs)
    val split = ClockText.stopwatch(splitMs)
    val row = buildAnnotatedString {
        withStyle(SpanStyle(color = dim, fontSize = 12.sp)) { append("$n  ") }
        withStyle(SpanStyle(color = dim, fontSize = 20.sp)) { append(lap.substring(0, lap.length - 5)) }
        withStyle(SpanStyle(color = bright, fontSize = 20.sp, fontWeight = FontWeight.Bold)) { append(lap.takeLast(5)) }
        withStyle(SpanStyle(color = dim, fontSize = 12.sp)) { append("  $split") }
    }
    // Checked on the text the builder made: inside the builder `toString()` is the builder object, which never equals
    // the line, and every lap crashed the app (qa/phase-15/E7-run2/DEFECT.md).
    check(row.text == line) { "lap row text must equal the shared line" }
    return row
}

/**
 * The Stopwatch tab (§7): the digits with hundredths (7.1), the control row reset / Flag · ring · expand at ± 96 epx
 * (7.2–7.3), and the laps list under "Laps / Splits", newest on top (7.4, U6). Share and Pin are the app bar's (1.13).
 */
@Composable
fun BoxScope.StopwatchTab(nav: ClockNav, store: ClockStore) {
    val colors = LocalShellColors.current
    val sw by store.stopwatch.collectAsState()
    val elapsed = rememberStopwatchElapsed(store, sw.running, sw)
    StopwatchLogLoop(store, sw.running)
    // 7.1: cap top 39.2 below the band, the block's centre 180.7 (0.7 right of centre).
    BasicText(
        stopwatchDigits(ClockText.stopwatch(elapsed), colors.text.copy(alpha = 0.36f), colors.text),
        Modifier.offset(x = 0.7.dp, y = capPad(39.2f, 45.7f)).fillMaxWidth().testTag("stopwatch_elapsed"),
        style = ShellType.body.copy(fontSize = 45.7.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1,
    )
    // 7.2–7.3: the control row's centre 125.8 below the band; reset (Flag while running) at x 83.2, the ring at 179, expand at 274.7.
    if (sw.running) {
        GlyphButton(Glyph.FLAG, "stopwatch_lap", true, Modifier.offset(x = (83.2f - 22f).dp, y = (125.8f - 22f).dp)) { store.lapStopwatch() }
    } else {
        GlyphButton(Glyph.RESET, "stopwatch_reset", enabled = elapsed > 0 || sw.laps.isNotEmpty(), Modifier.offset(x = (83.2f - 22f).dp, y = (125.8f - 22f).dp)) { store.resetStopwatch() }
    }
    RingButton(
        glyph = if (sw.running) Glyph.PAUSE else Glyph.PLAY, tag = "stopwatch_play",
        arcFraction = if (sw.running) (elapsed % 60_000L) / 60_000f else null, enabled = true,
        modifier = Modifier.offset(x = (179f - 29.8f).dp, y = (125.8f - 29.8f).dp),
    ) { if (sw.running) store.stopStopwatch() else store.startStopwatch() }
    GlyphButton(Glyph.EXPAND, "stopwatch_expand", true, Modifier.offset(x = (274.7f - 22f).dp, y = (125.8f - 22f).dp)) { nav.page = ClockPage.StopwatchExpanded }
    // 7.4: "Laps" (semibold) over "Splits" (grey), then the laps at R3 A15's 64-epx pitch, newest on top.
    Column(Modifier.fillMaxSize().padding(top = 172.dp)) {
        if (sw.laps.isNotEmpty()) {
            BasicText("Laps", Modifier.padding(start = 12.dp).testTag("stopwatch_laps_header"), style = ShellType.base.copy(color = colors.text))
            BasicText("Splits", Modifier.padding(start = 12.dp).testTag("stopwatch_splits_header"), style = ShellType.caption.copy(color = colors.text.copy(alpha = 0.49f)))
        }
        val durations = remember(sw.laps) { ClockText.lapDurations(sw.laps) }
        // A keyed list keeps its top row in place when rows are inserted above it, so once the laps outgrow the list
        // every new lap would land above the viewport (qa/phase-15/EDGE_STOPWATCH-run5/DEFECT.md): bring the newest into view.
        val lapsState = rememberLazyListState()
        LaunchedEffect(sw.laps.size) { if (sw.laps.isNotEmpty()) lapsState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxSize().testTag("stopwatch_laps"), state = lapsState, contentPadding = PaddingValues(bottom = ClockMetrics.APP_BAR)) {
            itemsIndexed(sw.laps.asReversed(), key = { i, _ -> sw.laps.size - i }) { i, split ->
                val n = sw.laps.size - i
                Box(Modifier.fillMaxWidth().height(64.dp)) {
                    BasicText(
                        lapText(n, durations[n - 1], split, colors.text.copy(alpha = 0.49f), colors.text),
                        Modifier.align(Alignment.CenterStart).offset(x = 12.dp).testTag("stopwatch_lap:$n"),
                        style = ShellType.body.copy(color = colors.text), maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The stopwatch's Share (T15-16, H23): `ACTION_SEND text/plain`, one line per lap exactly as `stopwatch_lap:<n>` shows it. */
fun shareLaps(context: Context, laps: List<Long>) {
    val durations = ClockText.lapDurations(laps)
    val text = laps.indices.joinToString("\n") { i -> ClockText.lapLine(i + 1, durations[i], laps[i]) }
    Diagnostics.add("stopwatch", "share ${laps.size} laps")
    runCatching {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Diagnostics.add("stopwatch", "share failed: $it") }
}

/** The expanded stopwatch (7.5, U7): the accent page, the digits with hundredths centred, the latest lap and split under them, Flag · ring · collapse at the bottom. */
@Composable
fun StopwatchExpandedScreen(nav: ClockNav, store: ClockStore, onBack: () -> Unit, onWindows: () -> Unit) {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    val sw by store.stopwatch.collectAsState()
    val elapsed = rememberStopwatchElapsed(store, sw.running, sw)
    StopwatchLogLoop(store, sw.running)
    ClockScaffold(onBack, onWindows, background = colors.accent) {
        Box(Modifier.fillMaxSize().testTag("stopwatch_expanded")) {
            BasicText(
                stopwatchDigits(ClockText.stopwatch(elapsed), Color.White.copy(alpha = 0.5f), Color.White, scale = 1.5f),
                Modifier.align(Alignment.Center).offset(y = (-40).dp).fillMaxWidth().testTag("stopwatch_elapsed"),
                style = ShellType.body.copy(fontSize = 68.5.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1,
            )
            if (sw.laps.isNotEmpty()) {
                val n = sw.laps.size
                val durations = ClockText.lapDurations(sw.laps)
                BasicText(
                    ClockText.lapLine(n, durations[n - 1], sw.laps[n - 1]),
                    Modifier.align(Alignment.Center).offset(y = 20.dp).fillMaxWidth().testTag("stopwatch_expanded_lap"),
                    style = ShellType.subtitle.copy(color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center), maxLines = 1,
                )
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)) {
                GlyphButton(Glyph.FLAG, "stopwatch_lap", enabled = sw.running, Modifier.align(Alignment.Center).offset(x = (-96).dp), size = 24f) { store.lapStopwatch() }
                RingButton(if (sw.running) Glyph.PAUSE else Glyph.PLAY, "stopwatch_play", if (sw.running) (elapsed % 60_000L) / 60_000f else null, true, Modifier.align(Alignment.Center), scale = 1.5f) {
                    if (sw.running) store.stopStopwatch() else store.startStopwatch()
                }
                GlyphButton(Glyph.COLLAPSE, "stopwatch_collapse", true, Modifier.align(Alignment.Center).offset(x = 96.dp), size = 24f) { nav.page = ClockPage.Tabs }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") context
}
