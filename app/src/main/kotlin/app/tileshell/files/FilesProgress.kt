package app.tileshell.files

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.bars.BarMetrics
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.tokens.ShellType

/** The progress box's values (pass 2 §1 UNMEASURED-2, a camera: LOW; Y4). */
object ProgressMetrics {
    /** Directly under the status bar, about 66 tall, inset about 21 each side. */
    const val HEIGHT = 66f
    const val INSET = 21f

    /** A dark box: the bars' own fill. */
    val FILL = FilesMetrics.BAR_FILL

    /** Y4: the wash over the whole screen, drawn as white at 20 % (the camera's colour is not reliable). */
    val WASH = Color.White.copy(alpha = 0.2f)

    /** The indeterminate dots: five, crossing the box's width once in this long, each a little behind the one before. */
    const val DOTS = 5
    const val DOT = 4f
    const val DOTS_MS = 2400f
    const val DOT_LAG = 0.07f
}

/**
 * What the progress overlay says for [op]: the box's text while an operation runs, null otherwise. While the
 * operation waits for a conflict's answer the dialog stands where the box would, so the box is not drawn.
 */
fun progressText(op: OpState): String? = (op as? OpState.Running)?.let { FileOpsText.title(it.kind) }

/** An operation is running or waiting for an answer: the page under it takes no touch. */
fun opActive(op: OpState): Boolean = op is OpState.Running || op is OpState.AwaitingConflict

/**
 * The progress overlay inside the page's box (r3 D2 (4)): the wash, and on it the dark box `files_progress` with a row
 * of travelling dots over its text `files_progress_text` — "Moving files…", "Copying files…". No percentage and no
 * cancel: both live in the copy service's notification (P4, H5). Modal: nothing under it can be tapped while files
 * are being written.
 */
@Composable
fun FilesProgressLayer(op: OpState) {
    if (!opActive(op)) return
    val text = progressText(op)
    OverlayLayer(active = { true }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(ProgressMetrics.WASH).testTag("files_wash").modalOverlay(onTapOff = {})) {
            if (text != null) {
                Box(
                    Modifier.padding(horizontal = ProgressMetrics.INSET.dp).fillMaxWidth().height(ProgressMetrics.HEIGHT.dp)
                        .background(ProgressMetrics.FILL).testTag("files_progress"),
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressDots(Modifier.padding(top = 18.dp).fillMaxWidth().height(ProgressMetrics.DOT.dp))
                        BasicText(text, Modifier.padding(top = 10.dp).testTag("files_progress_text"), style = ShellType.body.copy(color = Color.White), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The wash's share over the status bar and the nav bar (it covers the whole screen, bars included). Takes no touch. */
@Composable
fun FilesProgressBarsWash(op: OpState) {
    if (!opActive(op)) return
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(BarMetrics.STATUS_EPX.dp).background(ProgressMetrics.WASH))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(BarMetrics.NAV_EPX.dp).background(ProgressMetrics.WASH))
    }
}

/** W10M's indeterminate progress row: dots that enter at the left, slow through the middle and leave at the right. */
@Composable
private fun ProgressDots(modifier: Modifier) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            phase.floatValue = ((now - start) / 1_000_000f / ProgressMetrics.DOTS_MS) % 1f
        }
    }
    Canvas(modifier) {
        val r = ProgressMetrics.DOT / 2f * density
        for (i in 0 until ProgressMetrics.DOTS) {
            val t = phase.floatValue - i * ProgressMetrics.DOT_LAG
            if (t < 0f || t > 0.75f) continue
            val u = t / 0.75f
            // Fast in, slow across the middle, fast out: the cube of a symmetric ramp around the centre.
            val s = 2f * u - 1f
            val x = (0.5f + 0.5f * s * s * s * 0.6f + 0.2f * s) * size.width
            drawCircle(Color.White, r, Offset(x, size.height / 2f))
        }
    }
}
