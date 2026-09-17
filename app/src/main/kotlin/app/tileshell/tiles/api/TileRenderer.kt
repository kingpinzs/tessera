package app.tileshell.tiles.api

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.tiles.engine.TileFace
import java.io.File

/**
 * Maps one validated API tile notification onto the engine's [TileFace] contract.
 * The engine's content is size-independent, so one binding is chosen: TileMedium, else TileWide, else TileSmall
 * (Medium is the default W10M tile size).
 * - hint-presentation="photos": one Photo face per image (≤ 9, the phone cap).
 * - background image: a Photo face with the binding's text lines as overlay.
 * - peek image: a Photo face, then the text face (the peek slides in over the main content).
 * - text only: a TextLines face. Inline images have no face in the phase 01 contract; an image-only payload shows
 *   its first image as a Photo face.
 */
object TileRenderer {
    private val ORDER = listOf(TileTemplate.MEDIUM, TileTemplate.WIDE, TileTemplate.SMALL)
    private const val MAX_LINES = 6
    private const val MAX_DECODE_SIDE = 1024

    /** True when the chosen binding has a peek image, so the tile moves between its faces with the peek slide. */
    fun peeks(payload: TilePayload): Boolean =
        ORDER.firstNotNullOfOrNull { payload.binding(it) }?.flatten()
            ?.any { it is TileElement.Image && it.placement == ImagePlacement.PEEK } == true

    fun faces(payload: TilePayload, fileFor: (String) -> File?): List<TileFace> {
        val binding = ORDER.firstNotNullOfOrNull { payload.binding(it) } ?: return emptyList()
        val flat = binding.flatten()
        val lines = flat.filterIsInstance<TileElement.Text>().map { it.content.trim() }.filter { it.isNotEmpty() }.take(MAX_LINES)
        val images = flat.filterIsInstance<TileElement.Image>()
        fun decode(img: TileElement.Image): ImageBitmap? = fileFor(img.src)?.let { decodeFile(it) }

        if (binding.presentation == "photos") {
            val photos = images.take(TileXmlValidator.MAX_PHOTOS).mapNotNull { decode(it) }.map { TileFace.Photo(it, lines) }
            if (photos.isNotEmpty()) return photos
        }
        val faces = mutableListOf<TileFace>()
        val peek = images.firstOrNull { it.placement == ImagePlacement.PEEK }?.let { decode(it) }
        val background = images.firstOrNull { it.placement == ImagePlacement.BACKGROUND }?.let { decode(it) }
        if (peek != null) faces += TileFace.Photo(peek)
        when {
            background != null -> faces += TileFace.Photo(background, lines)
            lines.isNotEmpty() -> faces += TileFace.TextLines(lines)
        }
        if (faces.isEmpty()) images.firstNotNullOfOrNull { decode(it) }?.let { faces += TileFace.Photo(it) }
        return faces
    }

    private fun decodeFile(file: File): ImageBitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    }
}
