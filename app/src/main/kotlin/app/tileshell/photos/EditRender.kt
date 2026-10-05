package app.tileshell.photos

import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import app.tileshell.diag.Diagnostics
import app.tileshell.media.AndroidMediaStorePort
import app.tileshell.media.MediaKind
import app.tileshell.media.MediaWrites
import app.tileshell.media.NewMedia
import java.io.BufferedOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything the editor has been asked to do to one picture (Q1 C). Geometry is fractions and turns, colour is the
 * doc's steps, red-eye is the blobs found in the preview ([redEyeW] × [redEyeH]) — so the same edit renders the
 * preview and, at full resolution, the saved copy.
 */
data class EditState(
    val quarterTurns: Int = 0,
    val straighten: Int = 0,
    val crop: NormRect = NormRect.FULL,
    val recipe: EditMatrices.Recipe = EditMatrices.Recipe(),
    val redEye: List<RedEye.Blob> = emptyList(),
    val redEyeW: Int = 1,
    val redEyeH: Int = 1,
) {
    fun plan(srcW: Int, srcH: Int) = EditPlan(srcW, srcH, quarterTurns, straighten.toDouble(), crop)

    /** The tools applied, as the op's line names them, in a fixed order; empty when nothing is changed. */
    val tools: List<String> get() = buildList {
        if (crop != NormRect.FULL) add("crop")
        if (((quarterTurns % 4) + 4) % 4 != 0) add("rotate")
        if (straighten != 0) add("straighten")
        if (redEye.isNotEmpty()) add("redeye")
        recipe.filter?.let { add("filter:$it") }
        if (recipe.light != 0) add("light")
        if (recipe.colour != 0) add("colour")
        if (recipe.enhance) add("enhance")
    }
    val toolName: String get() = tools.joinToString("+")
}

/**
 * The editor's pixels (r3 D8, V12): [renderInto] draws an edit from any in-memory source (the preview), and [saveCopy]
 * re-reads the ORIGINAL at full resolution through BitmapRegionDecoder, a strip at a time, and writes the result as a
 * COPY through the shell's one write layer. Runs in `:photosedit`.
 */
object EditRender {
    fun writes(context: Context): MediaWrites<IntentSender> = app.tileshell.media.ShellMediaWrites.of(context)

    /** [state] applied to a whole in-memory upright source: the preview's pixels. Returns the output's size. */
    fun renderInto(state: EditState, src: IntArray, srcW: Int, srcH: Int, applyCrop: Boolean): Triple<IntArray, Int, Int> {
        val plan = (if (applyCrop) state else state.copy(crop = NormRect.FULL)).plan(srcW, srcH)
        val pixels = if (state.redEye.isEmpty()) src else IntArray(src.size) { i ->
            if (RedEye.inBlobs(state.redEye, i % srcW, i / srcW, srcW, srcH, state.redEyeW, state.redEyeH)) RedEye.fix(src[i]) else src[i]
        }
        val out = IntArray(plan.outW * plan.outH)
        EditGeometry.sampleTile(plan, pixels, PxRect(0, 0, srcW, srcH), 0, 0, plan.outW, plan.outH, out, 0, plan.outW)
        state.recipe.apply(out)
        return Triple(out, plan.outW, plan.outH)
    }

    sealed interface Saved {
        data class Ok(val uri: String, val where: String, val width: Int, val height: Int) : Saved
        data class Failed(val why: String) : Saved
    }

    /**
     * "Save a copy": the original [uri] re-read at full resolution and [state] rendered strip by strip into a new row —
     * a JPEG at quality 95, or a PNG when the original is one; upright, EXIF Orientation 1, the original's capture
     * time copied; in the original's folder where MediaStore takes a new image there (r3 D7). The original row and
     * file are never opened for writing. Writes `[photosapp] edit <tool> -> <uri>` or `[photosapp] edit <tool> failed: <why>`.
     */
    fun saveCopy(context: Context, uri: Uri, entry: MediaEntry, state: EditState): Saved {
        val tool = state.toolName.ifEmpty { "none" }
        val result = runCatching { render(context, uri, entry, state) }.getOrElse { Saved.Failed(it.message?.takeIf { m -> m.length < 80 } ?: it.javaClass.simpleName) }
        when (result) {
            is Saved.Ok -> Diagnostics.add("photosapp", "edit $tool -> ${result.uri} (${result.where}, ${result.width}x${result.height})")
            is Saved.Failed -> Diagnostics.add("photosapp", "edit $tool failed: ${result.why}")
        }
        return result
    }

    private fun render(context: Context, uri: Uri, entry: MediaEntry, state: EditState): Saved {
        val resolver = context.contentResolver
        // The original is opened FIRST: a file that is gone fails the save before any row exists (E6's failure leg).
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return Saved.Failed("the original could not be read")
        descriptor.use { pfd ->
            val exif = runCatching { ExifInterface(pfd.fileDescriptor) }.getOrNull()
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)?.takeIf { it in 1..8 } ?: 1
            val decoder = runCatching { BitmapRegionDecoder.newInstance(pfd) }.getOrNull() ?: return Saved.Failed("the original could not be decoded")
            try {
                val rawW = decoder.width
                val rawH = decoder.height
                val (srcW, srcH) = ImageOrientation.uprightSize(rawW, rawH, orientation)
                val plan = state.plan(srcW, srcH)
                val png = entry.mime == "image/png"
                val mime = if (png) "image/png" else "image/jpeg"
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                val name = "${entry.name.substringBeforeLast('.').ifEmpty { "photo" }}_edit_$stamp.${if (png) "png" else "jpg"}"
                val folder = MediaWrites.copyPlacement(MediaKind.IMAGE, entry.relativePath)
                val exifBlock = ExifBlock.build(
                    exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.takeIf { it.length == 19 },
                    exif?.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
                    exif?.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
                )
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                var alpha = false
                if (png) {
                    // Whether the original has an alpha channel: one pixel tells.
                    decoder.decodeRegion(Rect(0, 0, 1, 1), options)?.let { alpha = it.hasAlpha(); it.recycle() }
                }
                // Strips of about four million pixels: few enough region decodes for a JPEG, which decodes down to each one.
                val stripRows = ((4_000_000 / plan.outW).coerceIn(16, 1024) / 16 * 16).coerceAtLeast(16)
                val tileW = if (plan.exact) plan.outW else minOf(plan.outW, 2048)
                val saved = writes(context).save(NewMedia(MediaKind.IMAGE, name, mime, folder, entry.dateTakenMs)) { raw ->
                    val out = BufferedOutputStream(raw, 1 shl 16)
                    val sink: StripSink = if (png) PngStripWriter(out, plan.outW, plan.outH, alpha, exifBlock) else JpegStripEncoder(out, plan.outW, plan.outH, 95, exifBlock)
                    val strip = IntArray(plan.outW * stripRows)
                    var y = 0
                    while (y < plan.outH) {
                        val rows = minOf(stripRows, plan.outH - y)
                        var x = 0
                        while (x < plan.outW) {
                            val tw = minOf(tileW, plan.outW - x)
                            val need = plan.sourceBounds(x, y, x + tw, y + rows, 2)
                            val rawRect = ImageOrientation.toRaw(need, rawW, rawH, orientation)
                            val part = decoder.decodeRegion(Rect(rawRect.left, rawRect.top, rawRect.right, rawRect.bottom), options) ?: error("the original could not be decoded")
                            val upright = PhotoDecode.upright(part, orientation)
                            val pixels = IntArray(upright.width * upright.height)
                            upright.getPixels(pixels, 0, upright.width, 0, 0, upright.width, upright.height)
                            val got = PxRect(need.left, need.top, need.left + upright.width, need.top + upright.height)
                            if (upright !== part) upright.recycle()
                            part.recycle()
                            if (state.redEye.isNotEmpty()) {
                                for (i in pixels.indices) {
                                    if (RedEye.inBlobs(state.redEye, got.left + i % got.width, got.top + i / got.width, srcW, srcH, state.redEyeW, state.redEyeH)) pixels[i] = RedEye.fix(pixels[i])
                                }
                            }
                            EditGeometry.sampleTile(plan, pixels, got, x, y, tw, rows, strip, x, plan.outW)
                            x += tw
                        }
                        state.recipe.apply(strip, plan.outW * rows)
                        sink.writeRows(strip, rows)
                        y += rows
                    }
                    sink.finish()
                    out.flush()
                }
                return when (saved) {
                    is MediaWrites.Result.Saved -> Saved.Ok(saved.row.uri, "$folder$name", plan.outW, plan.outH)
                    is MediaWrites.Result.Failed -> Saved.Failed(saved.why)
                }
            } finally {
                decoder.recycle()
            }
        }
    }
}
