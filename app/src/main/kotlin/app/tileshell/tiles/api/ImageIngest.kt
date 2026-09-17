package app.tileshell.tiles.api

import android.content.Context
import app.tileshell.diag.Diagnostics
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
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

    /**
     * Copies the caller's images under the shell's own names. [alreadyUsedBytes] is what this owner already stores, so
     * a payload that cannot fit the quota is refused before anything is written (adversarial review F4: otherwise an
     * owner at quota could drive megabytes a minute of write-then-delete churn).
     *
     * The bytes come from the caller's own provider, so the read is given a deadline and only a couple of reads may
     * run at once: the stream can be a pipe the caller never writes to, and this runs on an incoming binder thread of
     * the Home app (adversarial review F2).
     */
    fun ingest(owner: String, images: List<TileElement.Image>, dir: File, alreadyUsedBytes: Long = 0): Result {
        val written = mutableListOf<File>()
        val stored = mutableListOf<LiveTileStore.StoredImage>()
        var incoming = 0L
        fun reject(reason: String, detail: String): Result {
            written.forEach { it.delete() }
            return Result.Rejected(reason, detail)
        }
        val unique = images.distinctBy { it.src }
        if (unique.isEmpty()) return Result.Ok(emptyList())
        if (!readSlots.tryAcquire()) {
            return Result.Rejected(LiveTileProtocol.Error.RATE, "too many images are being read for other callers; try again")
        }
        try {
            for (img in unique) {
                authorityProblem(owner, img)?.let { return reject(LiveTileProtocol.Error.IMAGE_AUTHORITY, it) }
                val bytes = when (val read = readWithDeadline(img.src)) {
                    is Read.Bytes -> read.bytes
                    Read.TooBig -> return reject(LiveTileProtocol.Error.IMAGE_SIZE, "${img.src}: more than $MAX_IMAGE_BYTES bytes")
                    Read.TimedOut -> return reject(LiveTileProtocol.Error.IMAGE_READ, "${img.src}: the caller's provider did not deliver the image within ${READ_DEADLINE_MS} ms")
                    is Read.Failed -> return reject(LiveTileProtocol.Error.IMAGE_READ, "${img.src}: ${read.detail}")
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return reject(LiveTileProtocol.Error.IMAGE_DECODE, "${img.src}: not a decodable image")
                if (bounds.outWidth > MAX_SIDE || bounds.outHeight > MAX_SIDE) {
                    return reject(LiveTileProtocol.Error.IMAGE_DECODE, "${img.src}: ${bounds.outWidth}x${bounds.outHeight} exceeds ${MAX_SIDE}px")
                }
                incoming += bytes.size
                if (alreadyUsedBytes + incoming > LiveTileStore.MAX_OWNER_IMAGE_BYTES) {
                    return reject(LiveTileProtocol.Error.QUOTA, "images ${alreadyUsedBytes + incoming} bytes > ${LiveTileStore.MAX_OWNER_IMAGE_BYTES}")
                }
                dir.mkdirs()
                val file = File(dir, LiveTileStore.IMAGE_PREFIX + UUID.randomUUID())
                file.writeBytes(bytes)
                written += file
                stored += LiveTileStore.StoredImage(img.src, file.name, bytes.size.toLong())
            }
        } finally {
            readSlots.release()
        }
        return Result.Ok(stored)
    }

    private sealed interface Read {
        data class Bytes(val bytes: ByteArray) : Read
        data object TooBig : Read
        data object TimedOut : Read
        data class Failed(val detail: String) : Read
    }

    /** Reads the caller's stream on a worker thread; a stream that stalls is closed and the call is refused. */
    private fun readWithDeadline(src: String): Read {
        val outcome = AtomicReference<Read>(Read.TimedOut)
        val stream = AtomicReference<InputStream?>(null)
        val worker = Thread {
            try {
                val s = context.contentResolver.openInputStream(Uri.parse(src))
                if (s == null) {
                    outcome.set(Read.Failed("no stream"))
                    return@Thread
                }
                stream.set(s)
                s.use { outcome.set(readBounded(it)?.let { b -> Read.Bytes(b) } ?: Read.TooBig) }
            } catch (e: SecurityException) {
                outcome.set(Read.Failed("no read grant to the shell (${e.message})"))
            } catch (e: Exception) {
                outcome.set(Read.Failed("${e.javaClass.simpleName}: ${e.message}"))
            }
        }
        worker.isDaemon = true
        worker.name = "livetile-image-read"
        worker.start()
        worker.join(READ_DEADLINE_MS)
        if (worker.isAlive) {
            runCatching { stream.get()?.close() } // closing unblocks a read that is waiting on the caller's pipe
            worker.join(CLOSE_GRACE_MS)
            Diagnostics.add("livetile", "image read from $src passed ${READ_DEADLINE_MS} ms and was abandoned")
            return Read.TimedOut
        }
        return outcome.get()
    }

    /**
     * The caller is told only that an authority is not its own: naming the package that owns it, or even whether a
     * provider exists, would make this a package-visibility oracle for callers with no <queries> declaration
     * (adversarial review F6). The real reason goes to diagnostics, which only the shell reads.
     */
    private fun authorityProblem(owner: String, img: TileElement.Image): String? = when (img.scheme) {
        "android.resource" -> if (img.authority == owner) null else notCallers(owner, img, "android.resource package is not the caller's")
        "content" -> {
            val provider = context.packageManager.resolveContentProvider(img.authority, 0)
            when {
                provider == null -> notCallers(owner, img, "no provider for that authority")
                provider.packageName == context.packageName -> "authority ${img.authority} belongs to the shell"
                provider.packageName != owner -> notCallers(owner, img, "authority belongs to ${provider.packageName}")
                else -> null
            }
        }
        else -> "scheme ${img.scheme}"
    }

    private fun notCallers(owner: String, img: TileElement.Image, detail: String): String {
        Diagnostics.add("livetile", "image authority ${img.authority} refused for $owner: $detail")
        return "authority ${img.authority} is not the caller's"
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
        const val READ_DEADLINE_MS = 3_000L
        const val CLOSE_GRACE_MS = 250L
        /** At most this many caller streams are read at once, so hung reads cannot take the binder pool. */
        const val CONCURRENT_READS = 2
        private val readSlots = Semaphore(CONCURRENT_READS)
    }
}
