package app.tileshell.photos

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.PhotosFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * What Photos shows: the library as last read. [loaded] is false until the first read ends, so the page never flashes
 * an empty state it has not established. [access] is the tile's own rule ([PhotosFeed.access]); [videoAccess] is
 * READ_MEDIA_VIDEO (the Videos row — r3 V20), without which MediaStore returns no video rows.
 */
data class Library(
    val loaded: Boolean = false,
    val access: PhotosFeed.Access = PhotosFeed.Access.DENIED,
    val videoAccess: Boolean = false,
    val items: List<MediaEntry> = emptyList(),
) {
    val images: Int get() = items.count { !it.video }
    val videos: Int get() = items.count { it.video }
}

/**
 * Photos' library index (phase 17 build task 4): images and videos together from MediaStore, watched with a
 * ContentObserver the way [PhotosFeed] watches images, read off the main thread and in pages, so the first page of a
 * library of thousands is on screen before the rest is read. Main process only. It is not a second copy of the tile's
 * reader: the tile wants the newest N as faces, a gallery wants every row grouped (Decisions, "the Photos tile already
 * exists").
 *
 * After every read: `[photosapp] library: images=<n> videos=<n> access=<GRANTED|PARTIAL|DENIED>`.
 */
object PhotoStore {
    private const val PAGE = 500
    private const val OBSERVER_SETTLE_MS = 250L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(Library())
    val library: StateFlow<Library> = state.asStateFlow()

    private var observer: ContentObserver? = null
    private var reading: Job? = null

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(app, "mediastore change", OBSERVER_SETTLE_MS)
            }.also {
                app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, it)
                app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, it)
            }
        }
        refresh(app, "start")
    }

    /** Reads the library again. A read already running is replaced: the newest request always ends with the newest rows. */
    @Synchronized
    fun refresh(context: Context, reason: String, settleMs: Long = 0L) {
        val app = context.applicationContext
        reading?.cancel()
        reading = scope.launch {
            // A scan changes many rows at once: one read after they settle, not one per row.
            if (settleMs > 0) delay(settleMs)
            val access = PhotosFeed.access(app)
            val videoAccess = app.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            if (access == PhotosFeed.Access.DENIED) {
                // Without the pictures the page is the denied state (E3), whatever the Videos row says.
                publish(Library(true, access, videoAccess, emptyList()), reason)
                return@launch
            }
            val all = mutableListOf<MediaEntry>()
            var offset = 0
            while (true) {
                ensureActive()
                val page = runCatching { readPage(app.contentResolver, offset) }
                    .onFailure { Diagnostics.add("photosapp", "library query failed: ${it.javaClass.simpleName}") }
                    .getOrDefault(emptyList())
                all += page
                offset += page.size
                // The first page goes to the screen at once; while more is read the page is not yet "loaded" empty.
                if (page.size < PAGE) break
                if (offset == PAGE) state.value = Library(true, access, videoAccess, PhotoGroups.sorted(all))
            }
            ensureActive()
            publish(Library(true, access, videoAccess, PhotoGroups.sorted(all)), reason)
        }
    }

    private fun publish(next: Library, reason: String) {
        state.value = next
        Diagnostics.add("photosapp", "library read ($reason)")
        Diagnostics.add("photosapp", "library: images=${next.images} videos=${next.videos} access=${next.access}")
        if (next.access != PhotosFeed.Access.GRANTED) Diagnostics.add("photosapp", "access=${next.access}")
        if (!next.videoAccess) Diagnostics.add("photosapp", "videos: no access (READ_MEDIA_VIDEO)")
    }

    private val FILES: Uri get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    private val PROJECTION = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.BUCKET_ID,
        MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        MediaStore.MediaColumns.MIME_TYPE,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.DURATION,
    )

    private fun readPage(resolver: ContentResolver, offset: Int): List<MediaEntry> {
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE},${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            )
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.Files.FileColumns._ID))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, PAGE)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        val rows = ArrayList<MediaEntry>(PAGE)
        resolver.query(FILES, PROJECTION, args, null)?.use { c ->
            while (c.moveToNext()) rows += entry(c)
        }
        return rows
    }

    private fun entry(c: android.database.Cursor): MediaEntry {
        val taken = if (c.isNull(2)) null else c.getLong(2).takeIf { it > 0 }
        val relative = c.getString(11).orEmpty()
        return MediaEntry(
            id = c.getLong(0),
            video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
            dateMs = PhotoGroups.dateMs(taken, c.getLong(3)),
            bucketId = c.getLong(4),
            // A file at the volume's root has no bucket name; its folder is the volume.
            bucket = c.getString(5) ?: relative.trimEnd('/').substringAfterLast('/').ifEmpty { "Phone" },
            mime = c.getString(6).orEmpty(),
            name = c.getString(7).orEmpty(),
            width = c.getInt(8),
            height = c.getInt(9),
            size = c.getLong(10),
            relativePath = relative,
            durationMs = c.getLong(12),
            dateTakenMs = taken,
        )
    }

    /** One row by its content URI, for the viewer other apps open and for the editor's process: null when the shell cannot read it by itself. */
    fun readOne(context: Context, uri: Uri): MediaEntry? {
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return null
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        val args = Bundle().apply {
            // The collection is asked, not the item's own URI, so a caller's grant on that one URI never answers this.
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns._ID} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(id.toString()))
        }
        return runCatching {
            context.contentResolver.query(FILES, PROJECTION, args, null)?.use { c -> if (c.moveToFirst()) entry(c) else null }
        }.getOrNull()?.takeIf { uriOf(it).toString() == canonical(uri) }
    }

    /** `content://media/external/images/media/<id>` for any volume spelling of the same row (`external_primary`). */
    private fun canonical(uri: Uri): String {
        val segments = uri.pathSegments
        return if (segments.size == 4) "content://media/external/${segments[1]}/${segments[2]}/${segments[3]}" else uri.toString()
    }

    fun uriOf(item: MediaEntry): Uri = ContentUris.withAppendedId(
        if (item.video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        item.id,
    )
}

/**
 * The tiles' thumbnails: MediaStore's own (`loadThumbnail`), a few at a time off the main thread, kept in a small
 * cache by row id. A row whose file is gone has none: the tile is a placeholder (Edge cases), said once per row in
 * `[photosapp] thumbnail <id>: unreadable`.
 */
object PhotoThumbs {
    /** The tile is 111 epx; this is a little over it on the phone's 3 px per epx. */
    private val SIZE = Size(360, 360)

    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }
    private val unreadable: MutableSet<Long> = Collections.synchronizedSet(HashSet())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loader = Dispatchers.IO.limitedParallelism(4)

    fun cached(id: Long): ImageBitmap? = cache.get(id)?.asImageBitmap()

    fun knownUnreadable(id: Long): Boolean = id in unreadable

    suspend fun load(context: Context, item: MediaEntry): ImageBitmap? {
        cache.get(item.id)?.let { return it.asImageBitmap() }
        return withContext(loader) {
            val bitmap = runCatching { context.contentResolver.loadThumbnail(PhotoStore.uriOf(item), SIZE, null) }.getOrNull()
            if (bitmap == null) {
                if (unreadable.add(item.id)) Diagnostics.add("photosapp", "thumbnail ${item.id}: unreadable")
                null
            } else {
                unreadable.remove(item.id)
                cache.put(item.id, bitmap)
                bitmap.asImageBitmap()
            }
        }
    }

    /** A row that changed or left: its picture is read again next time. */
    fun forget(id: Long) {
        cache.remove(id)
        unreadable.remove(id)
    }
}
