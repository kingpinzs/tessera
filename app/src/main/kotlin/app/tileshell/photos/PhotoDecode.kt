package app.tileshell.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How Photos decodes a picture (r3 D8): never at full resolution in the launcher's process. The viewer decodes at
 * screen size through ImageDecoder's target size ([screen]), and a zoomed-in part is decoded again from the file with
 * BitmapRegionDecoder ([region]). HEIC goes through the platform's decoder like any other format; a DNG that the
 * platform cannot decode shows its embedded preview (Edge cases).
 */
object PhotoDecode {
    /** A decode for the screen: [bitmap] is at most the size asked for; [width] × [height] is the whole upright picture. */
    class Shown(val bitmap: Bitmap, val width: Int, val height: Int)

    /**
     * [uri] decoded to fit inside [maxW] × [maxH] px (never enlarged). [cover] instead keeps it large enough to cover
     * that box — the Start background's form. [software] asks for a bitmap whose pixels can be read and compressed.
     */
    fun screen(context: Context, uri: Uri, maxW: Int, maxH: Int, cover: Boolean = false, software: Boolean = false): Result<Shown> = runCatching {
        var fullW = 0
        var fullH = 0
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                fullW = info.size.width
                fullH = info.size.height
                val fit = min(maxW.toFloat() / fullW, maxH.toFloat() / fullH)
                val fill = max(maxW.toFloat() / fullW, maxH.toFloat() / fullH)
                val s = min(1f, if (cover) fill else fit)
                decoder.setTargetSize(max(1, (fullW * s).roundToInt()), max(1, (fullH * s).roundToInt()))
                if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            // A raw file the platform's decoder refuses still carries a JPEG preview: show that.
            val preview = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).thumbnailBitmap } ?: throw e
            fullW = preview.width
            fullH = preview.height
            preview
        }
        Shown(bitmap, fullW, fullH)
    }

    /** The file's EXIF orientation (1–8); 1 when it has none or cannot be read. */
    fun orientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
    }.getOrDefault(1).takeIf { it in 1..8 } ?: 1

    /** The file's raw pixel size, without decoding it. */
    fun rawSize(context: Context, uri: Uri): Pair<Int, Int>? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }.getOrNull()

    /**
     * The upright rectangle [upright] of the picture, decoded from the file at [sample] and turned upright. Null for a
     * format BitmapRegionDecoder does not read (the viewer then keeps the screen-size decode).
     */
    fun region(context: Context, uri: Uri, upright: PxRect, sample: Int): Bitmap? = runCatching {
        val orientation = orientation(context, uri)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val decoder = BitmapRegionDecoder.newInstance(stream) ?: return@use null
            try {
                val raw = ImageOrientation.toRaw(upright, decoder.width, decoder.height, orientation)
                if (raw.width <= 0 || raw.height <= 0) return@use null
                val part = decoder.decodeRegion(Rect(raw.left, raw.top, raw.right, raw.bottom), BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@use null
                upright(part, orientation)
            } finally {
                decoder.recycle()
            }
        }
    }.getOrNull()

    /** Raw pixels turned upright for [orientation]. */
    fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val (degrees, flip) = ImageOrientation.rotationAndFlip(orientation)
        if (degrees == 0 && !flip) return bitmap
        val m = Matrix().apply {
            postRotate(degrees.toFloat())
            if (flip) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
