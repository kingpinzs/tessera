package app.tileshell.tiles.api

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Copies a payload's images into shell-owned storage at call time (R5 §4b), so the caller's URI grant may lapse.
 * An image is accepted only when its URI authority belongs to the caller: a content:// provider owned by the calling
 * package (never the shell's own authority: confused deputy), or android.resource://<caller>. Each image is at most
 * 200 KB of decoder input and must decode as a bitmap of sane dimensions.
 */
class ImageIngest(private val context: Context) {
    sealed interface Result {
        data class Ok(val images: List<LiveTileStore.StoredImage>) : Result
        data class Rejected(val reason: String, val detail: String) : Result
    }

    fun ingest(owner: String, images: List<TileElement.Image>, dir: File): Result {
        val written = mutableListOf<File>()
        val stored = mutableListOf<LiveTileStore.StoredImage>()
        fun reject(reason: String, detail: String): Result {
            written.forEach { it.delete() }
            return Result.Rejected(reason, detail)
        }
        for (img in images.distinctBy { it.src }) {
            authorityProblem(owner, img)?.let { return reject(LiveTileProtocol.Error.IMAGE_AUTHORITY, it) }
            val bytes = try {
                val stream = context.contentResolver.openInputStream(Uri.parse(img.src))
                    ?: return reject(LiveTileProtocol.Error.IMAGE_READ, "${img.src}: no stream")
                stream.use { readBounded(it) }
            } catch (e: SecurityException) {
                return reject(LiveTileProtocol.Error.IMAGE_READ, "${img.src}: no read grant to the shell (${e.message})")
            } catch (e: Exception) {
                return reject(LiveTileProtocol.Error.IMAGE_READ, "${img.src}: ${e.javaClass.simpleName}: ${e.message}")
            } ?: return reject(LiveTileProtocol.Error.IMAGE_SIZE, "${img.src}: more than $MAX_IMAGE_BYTES bytes")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return reject(LiveTileProtocol.Error.IMAGE_DECODE, "${img.src}: not a decodable image")
            if (bounds.outWidth > MAX_SIDE || bounds.outHeight > MAX_SIDE) {
                return reject(LiveTileProtocol.Error.IMAGE_DECODE, "${img.src}: ${bounds.outWidth}x${bounds.outHeight} exceeds ${MAX_SIDE}px")
            }
            dir.mkdirs()
            val file = File(dir, LiveTileStore.IMAGE_PREFIX + UUID.randomUUID())
            file.writeBytes(bytes)
            written += file
            stored += LiveTileStore.StoredImage(img.src, file.name, bytes.size.toLong())
        }
        return Result.Ok(stored)
    }

    private fun authorityProblem(owner: String, img: TileElement.Image): String? = when (img.scheme) {
        "android.resource" -> if (img.authority == owner) null else "android.resource package ${img.authority} is not the caller"
        "content" -> {
            val provider = context.packageManager.resolveContentProvider(img.authority, 0)
            when {
                provider == null -> "no provider for authority ${img.authority}"
                provider.packageName == context.packageName -> "authority ${img.authority} belongs to the shell"
                provider.packageName != owner -> "authority ${img.authority} belongs to ${provider.packageName}, not the caller"
                else -> null
            }
        }
        else -> "scheme ${img.scheme}"
    }

    /** Reads at most [MAX_IMAGE_BYTES]; returns null when the stream is longer. */
    private fun readBounded(input: InputStream): ByteArray? {
        val buffer = ByteArray(MAX_IMAGE_BYTES + 1)
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return if (total > MAX_IMAGE_BYTES) null else buffer.copyOf(total)
    }

    companion object {
        const val MAX_IMAGE_BYTES = 200 * 1024
        const val MAX_SIDE = 4096
    }
}
