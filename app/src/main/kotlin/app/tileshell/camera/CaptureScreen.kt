package app.tileshell.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.ui.tokens.ShellType

/**
 * What [CaptureActivity] shows once the guard has let the request in: the viewfinder with one mode, then — after the
 * shutter — the capture with its accept / retake bar.
 */
@Composable
fun CaptureScreen(
    engine: CameraEngine,
    state: ViewfinderState,
    sink: CaptureSink,
    kind: String,
    review: CaptureReview?,
    finishing: Boolean,
    onAccept: () -> Unit,
    onRetake: () -> Unit,
) {
    Box(Modifier.fillMaxSize().testTag("capture_root")) {
        Viewfinder(engine, state, sink, captureKind = kind)
        if (review != null) ReviewPage(review, finishing, onAccept, onRetake)
    }
}

/**
 * The accept / retake page (Approximation Y14, a P4 design judged by H20; r11/camera.md UNMEASURED-7): the capture on
 * black with the Rich Capture editor's app-bar pattern (photos.md 1.7.5) — a transparent 48-epx bar whose glyph centres
 * are Accept at 82, Retake at 150 and More at 24 epx from the right. More shows each button's label, as W10M's "•••" did.
 */
@Composable
private fun ReviewPage(review: CaptureReview, finishing: Boolean, onAccept: () -> Unit, onRetake: () -> Unit) {
    var labels by remember { mutableStateOf(false) }
    BackHandler(enabled = labels) { labels = false }
    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("capture_review")
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
    ) {
        review.preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (review is CaptureReview.Video) {
            BasicText(Glyph.PLAY, Modifier.align(Alignment.Center), style = TextStyle(fontFamily = Brand.iconFont, fontSize = 36.sp, color = Color.White))
        }
        if (finishing) BasicText("Saving…", Modifier.align(Alignment.TopCenter).offset(y = 16.dp).testTag("capture_saving"), style = ShellType.caption.copy(color = Color.White))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (labels) 60.dp else 48.dp).testTag("capture_bar")) {
            BarButton(Glyph.RETAKE, "retake", "capture_retake", 150f, labels, !finishing, onRetake)
            BarButton(Glyph.CHECKMARK, "accept", "capture_accept", 82f, labels, !finishing, onAccept)
            BarButton(Glyph.MORE_HORIZONTAL, null, "capture_more", 24f, labels, true) { labels = !labels }
        }
    }
}

/** One bar button, 48 epx wide, its glyph centre [fromRight] epx from the right edge. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.BarButton(glyph: String, label: String?, tag: String, fromRight: Float, labels: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ink = if (enabled) Color.White else Color(0x66FFFFFF)
    Box(
        Modifier.align(Alignment.TopEnd).offset(x = (-(fromRight - 24f)).dp).width(48.dp).height(if (labels) 60.dp else 48.dp).testTag(tag)
            .tap { if (enabled) onClick() },
    ) {
        Column(Modifier.align(Alignment.TopCenter).height(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                BasicText(glyph, style = TextStyle(fontFamily = Brand.iconFont, fontSize = 20.sp, color = ink))
            }
        }
        if (labels && label != null) {
            BasicText(label, Modifier.align(Alignment.BottomCenter).offset(y = (-4).dp), style = ShellType.caption.copy(color = ink, textAlign = TextAlign.Center), maxLines = 1)
        }
    }
}
