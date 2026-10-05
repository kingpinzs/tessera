package app.tileshell.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import java.io.OutputStream

/** The real [MediaStorePort]: MediaStore's images and videos on the primary shared volume. */
class AndroidMediaStorePort(context: Context) : MediaStorePort<IntentSender> {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private fun collection(kind: MediaKind): Uri = when (kind) {
        MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    override fun insertPending(item: NewMedia): String? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, item.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            item.dateTakenMs?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        return runCatching { resolver.insert(collection(item.kind), values) }.getOrNull()?.toString()
    }

    override fun openWrite(uri: String): OutputStream? = resolver.openOutputStream(Uri.parse(uri), "w")

    override fun publish(uri: String): Boolean = runCatching {
        resolver.update(Uri.parse(uri), ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1
    }.getOrDefault(false)

    override fun abandon(uri: String): Boolean = runCatching { resolver.delete(Uri.parse(uri), null, null) > 0 }.getOrDefault(false)

    override fun readBack(uri: String): MediaRow? = query(Uri.parse(uri), MediaStore.MATCH_INCLUDE).firstOrNull()

    override fun pendingRows(): List<MediaRow> =
        MediaKind.entries.flatMap { query(collection(it), MediaStore.MATCH_ONLY) }

    override fun deleteRequest(uris: List<String>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris.map(Uri::parse)).intentSender

    private fun query(uri: Uri, matchPending: Int): List<MediaRow> = runCatching {
        val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_PENDING, matchPending) }
        val rows = mutableListOf<MediaRow>()
        resolver.query(uri, PROJECTION, args, null)?.use { c ->
            // A query on a collection returns ids; on one row its own URI is the row's.
            val single = uri.lastPathSegment?.toLongOrNull() != null
            while (c.moveToNext()) {
                val rowUri = if (single) uri else Uri.withAppendedPath(uri, c.getLong(0).toString())
                rows += MediaRow(
                    uri = rowUri.toString(),
                    pending = c.getInt(1) != 0,
                    size = c.getLong(2),
                    mime = c.getString(3),
                    relativePath = c.getString(4),
                    ownerPackage = c.getString(5),
                )
            }
        }
        rows
    }.getOrDefault(emptyList())

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.IS_PENDING,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        )
    }
}
