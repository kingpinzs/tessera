package app.tileshell.start

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Start background's one decode (phase 13 T13-5, an ADD to phase 01's part): Start's page and phase 13's static
 * acrylic source both call it, so the picture is sampled and decoded once per process and URI, exactly as Start
 * decoded it before — the sample size keeps the decoded height at most twice [TARGET].
 */
object BackgroundDecoder {
    private const val TARGET = 2400

    private var cached: Pair<String, ImageBitmap>? = null

    /** The decode already in hand for [uri], if any. */
    @Synchronized
    fun cached(uri: String): ImageBitmap? = cached?.takeIf { it.first == uri }?.second

    /** Decodes [uri] off the main thread, or returns the decode already in hand; the failure carries why. */
    suspend fun decode(context: Context, uri: String): Result<ImageBitmap> {
        cached(uri)?.let { return Result.success(it) }
        return withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val stream = resolver.openInputStream(Uri.parse(uri)) ?: error("cannot open")
                // A bounds-only decode returns null by design; the bounds land in opts.
                stream.use { BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth <= 0 || opts.outHeight <= 0) error("not an image")
                var sample = 1
                while (opts.outHeight / sample > TARGET * 2) sample *= 2
                val bitmap = resolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("decode failed")
                bitmap.asImageBitmap()
            }.onSuccess { remember(uri, it) }
        }
    }

    @Synchronized
    private fun remember(uri: String, bitmap: ImageBitmap) {
        cached = uri to bitmap
    }

    /** Drops the decode when the picture is removed or replaced. */
    @Synchronized
    fun forgetAllBut(uri: String?) {
        if (cached?.first != uri) cached = null
    }
}
