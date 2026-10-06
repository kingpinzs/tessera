package app.tileshell.photos

import android.content.Context
import android.net.Uri
import android.util.LruCache
import app.tileshell.media.MotionPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Which of Photos' stills are Living Images, and where each one's clip is (build task 6d). The file itself says so:
 * [MotionPhoto.read] on its first [MotionPhoto.HEAD_BYTES] and one box header at the clip's stated place — never the
 * whole file, never on the main thread, two files at a time, and once per state of a row ([LivingRules.key]), so
 * scrolling the collection reads no file twice. What the reader states is then bounded by [LivingRules.clip]; only a
 * clip that passes is a Living Image here (it gets the glyph and plays on a hold).
 */
object LivingImages {
    /** A row's answer; [clip] null = a plain still. */
    private class Answer(val clip: LivingRules.Clip?)

    private val cache = LruCache<LivingRules.Key, Answer>(4096)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loader = Dispatchers.IO.limitedParallelism(2)

    /** The answer already in hand for [item], without reading anything: null when it is a plain still or not yet read. */
    fun cached(item: MediaEntry): LivingRules.Clip? = cache.get(LivingRules.key(item))?.clip

    /** [item]'s clip when its file is a Living Image, else null. Reads the file's head once per state of the row. */
    suspend fun of(context: Context, item: MediaEntry): LivingRules.Clip? {
        if (!LivingRules.mayBeLiving(item.video, item.mime)) return null
        val key = LivingRules.key(item)
        cache.get(key)?.let { return it.clip }
        return withContext(loader) {
            cache.get(key)?.let { return@withContext it.clip }
            // A file that could not be opened now (a row whose file is gone, a scan under way) is asked again next time.
            val read = runCatching { read(context, PhotoStore.uriOf(item)) }.getOrElse { return@withContext null }
            cache.put(key, Answer(read))
            read
        }
    }

    /** The same for a picture the viewer was handed with no row of the shell's own: read each time it is opened. */
    suspend fun of(context: Context, uri: Uri, mime: String?): LivingRules.Clip? {
        if (!LivingRules.mayBeLiving(false, mime)) return null
        return withContext(loader) { runCatching { read(context, uri) }.getOrNull() }
    }

    /**
     * Opens [uri] for reading and asks the reader. The file's length is the descriptor's (`fstat`), not a count of bytes
     * read; a source with no length or no positions (a pipe) is a plain still.
     */
    private fun read(context: Context, uri: Uri): LivingRules.Clip? =
        context.applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val size = pfd.statSize
            if (size <= 0) return@use null
            FileInputStream(pfd.fileDescriptor).use { stream ->
                val head = stream.readNBytes(MotionPhoto.HEAD_BYTES)
                val info = MotionPhoto.read(head, size) { offset, count ->
                    runCatching {
                        val buffer = ByteBuffer.allocate(count)
                        var at = offset
                        while (buffer.hasRemaining()) {
                            val n = stream.channel.read(buffer, at)
                            if (n <= 0) break
                            at += n
                        }
                        if (buffer.hasRemaining()) null else buffer.array()
                    }.getOrNull()
                } ?: return@use null
                LivingRules.clip(info.videoOffset, info.videoLength, size).clip
            }
        }
}
