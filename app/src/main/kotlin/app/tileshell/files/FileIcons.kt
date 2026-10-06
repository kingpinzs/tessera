package app.tileshell.files

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The glyphs Files draws from the branding module's icon font, each standing in for the Segoe MDL2 glyph
 * r11/files.md §3 names (the font is Fluent UI System Icons; code points read from the shipped TTF's cmap,
 * `ic_fluent_<name>_20_regular`).
 */
object FilesGlyph {
    const val MENU = Glyph.NAVIGATION           // GlobalNavigationButton E700
    const val UP = Glyph.ARROW_UP               // Up E74A
    const val RECENT = Glyph.HISTORY            // Recent E823
    const val DEVICE = Glyph.PHONE              // CellPhone E8EA
    const val SD_CARD = ""                // SDCard E7F1 — the font has no SD card; "sim" is the same clipped-corner card
    const val BIN = Glyph.DELETE                // Delete E74D (Y1's stand-in for the Recycle Bin row)
    const val SELECT = Glyph.MULTISELECT        // MultiSelect E762
    const val NEW_FOLDER = ""             // NewFolder E8F4 — "folder add"
    const val ICONS = ""                  // ViewAll E8A9 — "grid"
    const val LIST = ""                   // BulletedList E8FD — "text bullet list ltr"
    const val SEARCH = Glyph.SEARCH             // Search E721
    const val MORE = Glyph.MORE_HORIZONTAL      // More E712
    const val CHEVRON_DOWN = Glyph.CHEVRON_DOWN // ChevronDown E70D
    const val IMAGE = Glyph.IMAGE
    const val VIDEO = Glyph.VIDEO
    const val AUDIO = Glyph.MUSIC
    const val ARCHIVE = ""                // "folder zip"
    const val TEXT = ""                   // "document text"
}

/** A glyph of the icon font in a [sizeEpx] em box, with no text line around it. */
@Composable
fun FilesGlyphText(glyph: String, sizeEpx: Float, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        glyph, modifier,
        style = TextStyle(fontFamily = Brand.iconFont, fontSize = sizeEpx.sp, lineHeight = sizeEpx.sp, color = color, textAlign = TextAlign.Center),
        maxLines = 1, softWrap = false,
    )
}

/**
 * A row's type icon (r11/files.md 1.5.3): W10M drew colour Windows shell icons — a yellow folder, a white page — and
 * never a monochrome glyph. These are the shell's own (A10: no Microsoft artwork): a two-tone folder, and a white page
 * with a folded corner carrying its kind's mark in that kind's colour. Drawn to fill the box it is given (32 × 40 in a
 * list row, 67 × 84 in the icons view).
 */
@Composable
fun FileTypeIcon(kind: FileKind, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        Canvas(Modifier.fillMaxSize()) { if (kind == FileKind.FOLDER) folder() else page() }
        val mark = when (kind) {
            FileKind.IMAGE -> FilesGlyph.IMAGE to Color(0xFF0078D7)
            FileKind.VIDEO -> FilesGlyph.VIDEO to Color(0xFF744DA9)
            FileKind.AUDIO -> FilesGlyph.AUDIO to Color(0xFFDA3B01)
            FileKind.ARCHIVE -> FilesGlyph.ARCHIVE to Color(0xFFB7892B)
            FileKind.TEXT -> FilesGlyph.TEXT to Color(0xFF5D5A58)
            else -> null
        }
        // The mark sits a little under the page's middle, clear of the folded corner.
        if (mark != null) {
            FilesGlyphText(mark.first, maxWidth.value * 0.55f, mark.second, Modifier.align(Alignment.Center).offset(y = (maxHeight.value * 0.07f).dp))
        }
    }
}

private val FOLDER_BACK = Color(0xFFE8A93A)
private val FOLDER_FRONT = Color(0xFFFFD667)
private val PAGE_FILL = Color(0xFFFAFAFA)
private val PAGE_FOLD = Color(0xFFC8C8C8)

/** A folder: a darker back with its tab, a lighter front leaf. Sits in the lower four fifths of a portrait box. */
private fun DrawScope.folder() {
    val w = size.width
    val h = size.height
    val top = h * 0.16f
    val bottom = h * 0.86f
    val r = CornerRadius(w * 0.05f)
    // The tab and the back.
    drawRoundRect(FOLDER_BACK, Offset(0f, top), GSize(w * 0.44f, h * 0.16f), r)
    drawRoundRect(FOLDER_BACK, Offset(0f, top + h * 0.08f), GSize(w, bottom - top - h * 0.08f), r)
    // The front leaf.
    drawRoundRect(FOLDER_FRONT, Offset(0f, top + h * 0.19f), GSize(w, bottom - top - h * 0.19f), r)
}

/** A page: white, the top-right corner folded. Fills the box's height, a little narrower than it. */
private fun DrawScope.page() {
    val w = size.width
    val h = size.height
    val left = w * 0.09f
    val right = w * 0.91f
    val fold = (right - left) * 0.32f
    val sheet = Path().apply {
        moveTo(left, 0f)
        lineTo(right - fold, 0f)
        lineTo(right, fold)
        lineTo(right, h)
        lineTo(left, h)
        close()
    }
    drawPath(sheet, PAGE_FILL)
    val corner = Path().apply {
        moveTo(right - fold, 0f)
        lineTo(right, fold)
        lineTo(right - fold, fold)
        close()
    }
    drawPath(corner, PAGE_FOLD)
}

/**
 * Image and video thumbnails for the rows (r11/files.md 1.5.3: "or the file's thumbnail"). Decoded off the main thread
 * by the platform's own `ThumbnailUtils`, a few at a time, and kept in a small memory cache keyed by the file's path,
 * size, date and the size asked for — so a file replaced under the same name gets a new picture.
 */
object FileThumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val decoders = Dispatchers.IO.limitedParallelism(3)

    private fun key(entry: FileEntry, px: Int) = "${entry.path}|${entry.row.size}|${entry.row.modifiedMs}|$px"

    fun cached(entry: FileEntry, px: Int): Bitmap? = cache.get(key(entry, px))

    /** The thumbnail, or null when the file is not a picture the platform can decode (the type icon then stays). */
    suspend fun load(entry: FileEntry, px: Int): Bitmap? {
        cached(entry, px)?.let { return it }
        return withContext(decoders) {
            val file = File(entry.path)
            val made = runCatching {
                when (entry.kind) {
                    FileKind.IMAGE -> ThumbnailUtils.createImageThumbnail(file, Size(px, px), null)
                    FileKind.VIDEO -> ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
                    else -> null
                }
            }.getOrNull()
            if (made != null) cache.put(key(entry, px), made)
            made
        }
    }
}

/**
 * The picture a row draws: the thumbnail of an image or a video once it is decoded, the type icon until then and for
 * everything else. [thumbPx] is the thumbnail's longest side in pixels.
 */
@Composable
fun FileIconOrThumb(entry: FileEntry, thumbPx: Int, icon: @Composable () -> Unit, thumb: @Composable (ImageBitmap) -> Unit) {
    val wantsThumb = (entry.kind == FileKind.IMAGE || entry.kind == FileKind.VIDEO) && entry.source != EntrySource.BIN
    val bitmap by produceState(initialValue = if (wantsThumb) FileThumbs.cached(entry, thumbPx) else null, entry.path, entry.row.size, entry.row.modifiedMs, thumbPx) {
        if (wantsThumb && value == null) value = FileThumbs.load(entry, thumbPx)
    }
    val shown = bitmap
    if (shown != null) thumb(shown.asImageBitmap()) else icon()
}

/** A decoded thumbnail, centre-cropped into its box. */
@Composable
fun ThumbImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
