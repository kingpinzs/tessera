package app.tileshell.ui.fluent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tileshell.diag.Diagnostics
import app.tileshell.start.BackgroundDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The static source (Decisions (a), T13-15, T13-18): the Start background, placed `ContentScale.Crop` to the
 * app-list page's own size at scale 1 (no parallax, no 1.3× — those are Start's own), blurred by HWUI's own blur,
 * tinted and noised by [FluentEffects] — rendered ONCE, off the main thread, by a [HardwareRenderer] into an
 * [ImageReader], and kept as one screen-sized bitmap until the picture, the tint or the page size changes.
 *
 * It is built when the app-list page first composes with its size — the pager composes that page beside Start, so
 * the layer exists before the first swipe — and dropped when the picture is removed or acrylic turns off (it is
 * rebuilt when acrylic turns on again).
 */
object StaticBackdrop {
    data class Key(val uri: String, val size: IntSize, val tint: Rgb, val radiusPx: Float)

    sealed interface Layer {
        data class Built(val key: Key, val bitmap: ImageBitmap) : Layer
        data class Failed(val key: Key) : Layer
    }

    private val mutable = MutableStateFlow<Layer?>(null)
    val layer: StateFlow<Layer?> = mutable.asStateFlow()

    /** Builds the layer for [key] unless it is already built (or already failed) for exactly that key. */
    suspend fun ensure(context: Context, key: Key) = build(key) {
        val decoded = BackgroundDecoder.decode(context, key.uri).getOrThrow()
        render(decoded.asAndroidBitmap(), key).asImageBitmap()
    }

    /** [ensure]'s bookkeeping around one build: [make] decodes and renders; its failure is recorded for [key]. */
    internal suspend fun build(key: Key, make: suspend () -> ImageBitmap) {
        val now = mutable.value
        if (now is Layer.Built && now.key == key) return
        if (now is Layer.Failed && now.key == key) return
        val started = SystemClock.uptimeMillis()
        val result = try {
            Result.success(make())
        } catch (cancelled: CancellationException) {
            // Acrylic turned off (or the picture changed) mid-build: not a failure. Recorded as one it would stick,
            // and the next build for this key would never run (gate review A, B1).
            throw cancelled
        } catch (why: Throwable) {
            Result.failure(why)
        }
        result.fold(
            onSuccess = { bitmap ->
                mutable.value = Layer.Built(key, bitmap)
                Diagnostics.add(
                    "fluent",
                    "static backdrop rebuilt for ${key.uri} in ${SystemClock.uptimeMillis() - started} ms",
                )
            },
            onFailure = { why ->
                mutable.value = Layer.Failed(key)
                Diagnostics.add("fluent", "static backdrop failed for ${key.uri}: ${describe(why)} (fallback)")
            },
        )
    }

    /** Frees the layer (the picture was removed, or acrylic turned off). */
    fun drop() {
        mutable.value = null
    }

    private fun describe(why: Throwable): String =
        "${why::class.java.simpleName}${why.message?.let { ": $it" } ?: ""}".replace('\n', ' ')

    private suspend fun render(source: Bitmap, key: Key): Bitmap = withContext(Dispatchers.IO) {
        val w = key.size.width
        val h = key.size.height
        require(w > 0 && h > 0) { "page size $w x $h" }
        // ContentScale.Crop into the page at scale 1, centred — the same mapping Compose's Image uses.
        val scale = max(w.toFloat() / source.width, h.toFloat() / source.height)
        val srcW = w / scale
        val srcH = h / scale
        val srcLeft = (source.width - srcW) / 2f
        val srcTop = (source.height - srcH) / 2f
        val src = Rect(srcLeft.toInt(), srcTop.toInt(), (srcLeft + srcW).toInt(), (srcTop + srcH).toInt())

        val content = RenderNode("fluent-static-content").apply {
            setPosition(0, 0, w, h)
            val canvas = beginRecording(w, h)
            canvas.drawBitmap(source, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            endRecording()
            setRenderEffect(FluentEffects.material(key.radiusPx, key.tint))
        }
        val root = RenderNode("fluent-static-root").apply {
            setPosition(0, 0, w, h)
            val canvas = beginRecording(w, h)
            canvas.drawRenderNode(content)
            endRecording()
        }
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(root)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage() ?: error("no frame rendered")
            image.use {
                val buffer = it.hardwareBuffer ?: error("no hardware buffer")
                buffer.use { hb ->
                    val hardware = Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?: error("cannot wrap the rendered buffer")
                    // A software copy: the layer is ordinary process memory (E10 measures it) and outlives the buffer.
                    hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
                }
            }
        } finally {
            renderer.destroy()
            reader.close()
            content.discardDisplayList()
            root.discardDisplayList()
        }
    }
}

/**
 * The app-list page's backdrop (`acrylic:applist`), drawn under its rows:
 * - acrylic on and the layer built → the blurred, tinted, noised picture;
 * - acrylic off (or the layer not built) → the fallback, W10M's own form (R3 A18): the picture under the tint at
 *   0.8, unblurred, no noise;
 * - no picture, or the picture cannot be decoded → the solid theme background, and no layer is allocated.
 */
@Composable
fun AppListBackdrop(backgroundUri: String?, background: Color, visible: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Fluent.state.collectAsState()
    val layer by StaticBackdrop.layer.collectAsState()
    val radiusPx = with(LocalDensity.current) { FluentMaterial.RADIUS_EPX.dp.toPx() }
    val tint = Rgb.of(background)
    var size by remember { mutableStateOf(IntSize.Zero) }
    var picture by remember(backgroundUri) { mutableStateOf(backgroundUri?.let { BackgroundDecoder.cached(it) }) }
    val holder = remember { AppListForm() }

    LaunchedEffect(backgroundUri) {
        BackgroundDecoder.forgetAllBut(backgroundUri)
        if (backgroundUri == null) {
            StaticBackdrop.drop()
            picture = null
        } else {
            picture = BackgroundDecoder.decode(context, backgroundUri).getOrNull()
        }
    }
    // The layer exists only while acrylic is on: with it off (the switch, battery saver, a low-RAM device) the page
    // draws the fallback, so a blurred copy would be memory nothing draws (E10's B holds no layer; C builds it).
    LaunchedEffect(backgroundUri, size, tint, radiusPx, state.on) {
        if (!state.on) {
            StaticBackdrop.drop()
        } else if (backgroundUri != null && size.width > 0 && size.height > 0) {
            StaticBackdrop.ensure(context, StaticBackdrop.Key(backgroundUri, size, tint, radiusPx))
        }
    }
    LaunchedEffect(visible) {
        if (visible) Fluent.logShown(FluentSurface.APPLIST, tint)
    }

    val built = (layer as? StaticBackdrop.Layer.Built)?.takeIf { it.key.uri == backgroundUri && it.key.size == size && it.key.tint == tint }
    val acrylic = state.on && built != null
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .testTag("acrylic:${FluentSurface.APPLIST.id}")
            .drawBehind {
                // The form this frame draws: acrylic needs the layer, which is built after acrylic turns on.
                if (visible && holder.drawn != null && holder.drawn != acrylic) Fluent.logForm(FluentSurface.APPLIST, acrylic)
                holder.drawn = acrylic
            },
    ) {
        val shown = picture
        when {
            backgroundUri == null || shown == null -> Box(Modifier.fillMaxSize().background(background))
            acrylic -> Image(built!!.bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            else -> {
                Image(shown, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(background.copy(alpha = FluentMaterial.ALPHA)))
            }
        }
    }
}

private class AppListForm {
    var drawn: Boolean? = null
}
