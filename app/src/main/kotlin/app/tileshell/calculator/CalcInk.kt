package app.tileshell.calculator

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import kotlin.math.roundToInt

/**
 * r11 measured the Calculator by INK — the glyphs' pixels on the phone — not by text boxes: "STANDARD" 11.0 epx tall
 * with its left edge at 60.5 (1.5), the History glyph 16 × 16 (1.7), "5,512" 33.0 epx tall with its right edge at the
 * 16-epx inset (2.14), a label's cap top 19 epx into its row (3.6), the gear 20 × 20 (3.12). A font's ink sits inside
 * its box by that font's own bearings, cap height and overshoot, so those numbers can only be landed by measuring the
 * ink of the font the shell ships, at the size it is drawn, and placing THAT. E13 run 1 showed the alternative — a
 * box placed by a table ratio — missing by 0.7–3.3 epx on nine rows (qa/phase-15/E13/DEFECT.md).
 *
 * The ink here is what E13 reads off the screen with calc_geo.py: the pixels at least half covered (min(r,g,b) ≥ 128
 * for white on black), measured by rasterising the run with the same Typeface and size Compose draws it with. It is
 * rasterised opaque whatever the text's colour: coverage is a property of the glyphs, and a translucent colour (the
 * Clock's 37 % grey empty line) would leave no pixel over the threshold and so no ink at all.
 */

/** A run's ink in px relative to its pen origin: x from the run's start, y from the baseline (up is negative). */
class InkBox(val left: Int, val top: Int, val right: Int, val bottom: Int, val advance: Float) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** The box Compose lays [InkLayout] out in (px), the baseline inside it and the run's ink — everything a placement needs. */
class InkLayout(val boxWidth: Int, val boxHeight: Int, val baseline: Float, val ink: InkBox) {
    /** The ink's top edge below the box's top. */
    val inkTop: Float get() = baseline + ink.top

    /** The ink's bottom edge below the box's top (exclusive, as the pixel rows are counted). */
    val inkBottom: Float get() = baseline + ink.bottom
    val inkCentre: Float get() = (inkTop + inkBottom) / 2f
}

/** The pure part: sizing a run so its rasterised ink is exactly the height asked for. */
object InkMath {
    /**
     * The size at which a run's ink is [targetPx] tall, given [inkHeight] — a step function of the size, whole px. A
     * proportional step from [start], then a walk in [stepPx] to the first size that gives [targetPx], then the middle
     * of the plateau of sizes that do, so a hairline of rendering difference cannot tip it. The last size tried when
     * no size within [maxSteps] gives it.
     */
    fun sizeForInkHeight(targetPx: Int, start: Float, stepPx: Float, maxSteps: Int, inkHeight: (Float) -> Int): Float {
        var size = start
        var h = inkHeight(size)
        if (h > 0 && h != targetPx) {
            size = size * targetPx / h
            h = inkHeight(size)
        }
        var steps = 0
        while (h != targetPx && steps++ < maxSteps) {
            size += if (h < targetPx) stepPx else -stepPx
            h = inkHeight(size)
        }
        if (h != targetPx) return size
        var lo = size
        steps = 0
        while (steps++ < maxSteps && inkHeight(lo - stepPx) == targetPx) lo -= stepPx
        var hi = size
        steps = 0
        while (steps++ < maxSteps && inkHeight(hi + stepPx) == targetPx) hi += stepPx
        return (lo + hi) / 2f
    }
}

/** Rasterises runs to find their ink; one Paint per call, results cached by face, size and text. */
object CalcInk {
    /** A pixel at least half covered is ink (calc_geo.py's threshold, 128 of 255). */
    const val INK_ALPHA = 128
    private const val MARGIN = 4
    /**
     * The last [CACHE_SIZE] runs measured, least recently used out first: the result display measures every value it
     * shows, so an unbounded map would keep one entry per value typed for the life of the process.
     */
    private const val CACHE_SIZE = 128
    private val cache = object : LinkedHashMap<List<Any>, InkBox>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Any>, InkBox>?): Boolean = size > CACHE_SIZE
    }

    fun measure(face: Typeface, sizePx: Float, text: String): InkBox = synchronized(cache) {
        cache.getOrPut(listOf(face, sizePx, text)) { rasterize(face, sizePx, text) }
    }

    private fun rasterize(face: Typeface, sizePx: Float, text: String): InkBox {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = sizePx
            color = android.graphics.Color.WHITE
        }
        val advance = paint.measureText(text)
        if (text.isEmpty()) return InkBox(0, 0, 0, 0, advance)
        val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val w = bounds.width() + 2 * MARGIN
        val h = bounds.height() + 2 * MARGIN
        // The pen origin inside the bitmap: the glyphs' box starts MARGIN in from each edge, the baseline at oy.
        val ox = MARGIN - bounds.left
        val oy = MARGIN - bounds.top
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawText(text, ox.toFloat(), oy.toFloat(), paint)
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        bitmap.recycle()
        var l = w
        var t = h
        var r = -1
        var b = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) >= INK_ALPHA) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return InkBox(0, 0, 0, 0, advance)
        return InkBox(l - ox, t - oy, r + 1 - ox, b + 1 - oy, advance)
    }
}

/** The Typeface Compose draws [family] at [weight] with — the same resolution BasicText makes. */
@Composable
private fun rememberTypeface(family: FontFamily?, weight: FontWeight): Typeface {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver, family, weight) {
        resolver.resolve(family, weight, FontStyle.Normal, FontSynthesis.All).value as Typeface
    }
}

/** The font size (epx) at which [reference]'s ink is [inkHeightEpx] tall in [family] at [weight]. */
@Composable
fun rememberInkFontSize(family: FontFamily?, weight: FontWeight, reference: String, inkHeightEpx: Float): Float {
    val density = LocalDensity.current
    val face = rememberTypeface(family, weight)
    return remember(density, face, reference, inkHeightEpx) {
        val targetPx = (inkHeightEpx * density.density).roundToInt()
        val px = InkMath.sizeForInkHeight(targetPx, start = targetPx / 0.7f, stepPx = 0.125f, maxSteps = 64) { size ->
            CalcInk.measure(face, size, reference).height
        }
        px / density.density
    }
}

/** Where [reference]'s ink sits inside the box Compose lays it out in at [style]. */
@Composable
fun rememberInkLayout(style: TextStyle, reference: String): InkLayout {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val face = rememberTypeface(style.fontFamily, style.fontWeight ?: FontWeight.Normal)
    return remember(density, measurer, face, style, reference) {
        val laid = measurer.measure(AnnotatedString(reference), style, maxLines = 1, softWrap = false)
        val sizePx = with(density) { style.fontSize.toPx() }
        InkLayout(laid.size.width, laid.size.height, laid.firstBaseline, CalcInk.measure(face, sizePx, reference))
    }
}

/**
 * Text placed by its INK. Horizontally, either the text's own ink lands with its left edge at [inkLeftEpx], or the
 * box's origin goes at [leftEpx] (r11 3.6 gives the labels' origin, 60). Vertically, [reference]'s ink — measured at
 * [style]'s size — lands with its centre at [inkCentreEpx] or its top at [inkTopEpx], so every string in the style
 * shares one baseline. Each epx from the parent's top-left; [modifier] carries the tag.
 */
@Composable
fun InkText(
    text: String,
    style: TextStyle,
    reference: String,
    modifier: Modifier = Modifier,
    inkLeftEpx: Float? = null,
    leftEpx: Float? = null,
    inkCentreEpx: Float? = null,
    inkTopEpx: Float? = null,
) {
    val d = LocalDensity.current.density
    val ref = rememberInkLayout(style, reference)
    val ownLeft = if (inkLeftEpx != null) rememberInkLayout(style, text).ink.left else 0
    val x = when {
        inkLeftEpx != null -> inkLeftEpx * d - ownLeft
        leftEpx != null -> leftEpx * d
        else -> 0f
    }
    val y = when {
        inkCentreEpx != null -> inkCentreEpx * d - ref.inkCentre
        inkTopEpx != null -> inkTopEpx * d - ref.inkTop
        else -> 0f
    }
    BasicText(
        text = text,
        // The offset first, so the caller's tag reports the box the text is drawn in, not where it would sit unplaced.
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.then(modifier),
        style = style,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * An icon-font glyph sized and placed by its INK: [inkHeightEpx] tall, its vertical centre at [inkCentreEpx], and
 * horizontally its left edge at [inkLeftEpx], its right edge at [inkRightEpx] or its centre at [inkCentreXEpx] — each
 * epx from the parent's top-left. The width follows the glyph's own design.
 */
@Composable
fun InkGlyph(
    code: String,
    color: Color,
    inkHeightEpx: Float,
    inkCentreEpx: Float,
    modifier: Modifier = Modifier,
    inkLeftEpx: Float? = null,
    inkRightEpx: Float? = null,
    inkCentreXEpx: Float? = null,
) {
    val d = LocalDensity.current.density
    val size = rememberInkFontSize(Brand.iconFont, FontWeight.Normal, code, inkHeightEpx)
    val style = TextStyle(fontFamily = Brand.iconFont, fontSize = size.sp, lineHeight = size.sp, color = color)
    val laid = rememberInkLayout(style, code)
    val x = when {
        inkLeftEpx != null -> inkLeftEpx * d - laid.ink.left
        inkRightEpx != null -> inkRightEpx * d - laid.ink.right
        inkCentreXEpx != null -> inkCentreXEpx * d - (laid.ink.left + laid.ink.right) / 2f
        else -> 0f
    }
    val y = inkCentreEpx * d - laid.inkCentre
    BasicText(
        text = code,
        // The offset first, so the caller's tag reports the box the text is drawn in, not where it would sit unplaced.
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.then(modifier),
        style = style,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Makes the node's box the INK: [vertical]'s ink rows (a reference run's, so the baseline holds) and [horizontal]'s
 * ink columns (the text's own), with the text's box laid around it. The tag on the node then reports r11's own
 * measurement frame — the result's right edge IS the digits' right edge at the 16-epx inset (2.14).
 */
fun Modifier.inkBox(vertical: InkLayout, horizontal: InkBox): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val top = vertical.inkTop.roundToInt()
    val bottom = vertical.inkBottom.roundToInt()
    layout(horizontal.width.coerceAtLeast(0), (bottom - top).coerceAtLeast(0)) {
        placeable.place(-horizontal.left, -top)
    }
}
