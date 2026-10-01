package app.tileshell.feeds

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import app.tileshell.people.ContactPhotoRules
import app.tileshell.people.PeopleActivity
import app.tileshell.people.PeopleData
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.PersonPhoto
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import app.tileshell.tiles.engine.TileRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The People tile (phase 16 build task 7; R3 A9, C3): contact photos for the tile's bubbles, read the way
 * [PhotosFeed] reads images — decoded at about the size they are drawn, re-read when the Contacts provider changes,
 * never on a timer. With no photo, or no permission to read contacts, the face is the static circle pattern.
 *
 * One publisher, two keys (r3 D12): the face goes out under the PEOPLE slot's key, so the slot tile shows it whichever
 * app holds the slot — it is the tile's W10M face, not an app's content — and under the shell's People component key,
 * so a People tile pinned from the app list is live too. It is never published under a package key, so nothing a
 * package publishes lands on these tiles (phase 15 build task 0's routing).
 */
object PeopleFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private val lock = Any()
    private var running = false
    private var again = false
    private var published: List<String>? = null
    private var publishedCount = 0

    fun start(context: Context) {
        val app = context.applicationContext
        if (PeopleData.canRead(app) && observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(app, "provider change")
            }.also { app.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, it) }
        }
        refresh(app, "start")
    }

    /** One read at a time; a burst of changes (a sync, an import) is let settle and read once. */
    fun refresh(context: Context, reason: String) {
        synchronized(lock) {
            if (running) { again = true; return }
            running = true
        }
        scope.launch {
            var first = true
            while (true) {
                if (!first) delay(BURST_SETTLE_MS)
                first = false
                synchronized(lock) { again = false }
                read(context, reason)
                val more = synchronized(lock) { if (!again) running = false; again }
                if (!more) break
            }
        }
    }

    private fun read(context: Context, reason: String) {
        if (!PeopleData.canRead(context)) {
            publish(context, emptyList(), emptyList())
            Diagnostics.add("people", "tile: 0 photos (no contacts access, $reason)")
            return
        }
        // The same provider change is where an account can have left the phone: "Can edit" drops what is no longer named.
        PeopleData.syncAccounts(context)
        val rows = photoRows(context)
        // What the tile would show, by contact and by the version of its photo: unchanged, nothing is decoded again.
        val signature = rows.map { "${it.lookup}|${it.photoId}|${it.photoFileId}|${it.updated}" }
        if (signature == published) {
            Diagnostics.add("people", "tile: $publishedCount photos")
            return
        }
        val photos = ArrayList<PersonPhoto>()
        for (row in rows) {
            val decoded = decode(context, row) ?: continue
            photos += decoded
        }
        publish(context, photos, signature)
        Diagnostics.add("people", "tile: ${photos.size} photos")
    }

    private class PhotoRow(val id: Long, val lookup: String, val photoId: Long, val photoFileId: Long, val updated: Long)

    /** The contacts that have a photo, in the list's order, at most [PeopleTileRules.MAX_PHOTOS]. */
    private fun photoRows(context: Context): List<PhotoRow> = runCatching {
        val out = ArrayList<PhotoRow>()
        context.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.PHOTO_ID, Contacts.PHOTO_FILE_ID, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP),
            "${Contacts.PHOTO_ID} IS NOT NULL", null, Contacts.SORT_KEY_PRIMARY,
        )?.use { c ->
            while (c.moveToNext() && out.size < PeopleTileRules.MAX_PHOTOS) {
                val lookup = c.getString(1) ?: continue
                out += PhotoRow(c.getLong(0), lookup, c.getLong(2), c.getLong(3), c.getLong(4))
            }
        }
        out
    }.getOrElse {
        Diagnostics.add("people", "tile: photo query failed: ${it.javaClass.simpleName}")
        emptyList()
    }

    /** One photo at about the bubble's size, or null — with the `skipped` line — when it cannot be drawn. */
    private fun decode(context: Context, row: PhotoRow): PersonPhoto? {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, row.id)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = runCatching {
            Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        }.getOrDefault(false)
        val bitmap = if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) null else runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = ContactPhotoRules.sampleSize(bounds.outWidth, bounds.outHeight, PeopleTileRules.TARGET_PX)
            }
            Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
        val why = PeopleTileRules.skipReason(opened, bounds.outWidth, bounds.outHeight, bitmap != null)
        if (why != null || bitmap == null) {
            Diagnostics.add("people", "tile: photo ${row.lookup} skipped: ${why ?: "decode failed"}")
            return null
        }
        return PersonPhoto(row.lookup, bitmap.asImageBitmap())
    }

    private fun publish(context: Context, photos: List<PersonPhoto>, signature: List<String>) {
        published = signature
        publishedCount = photos.size
        val content = TileContent(
            faces = emptyList(),
            transition = FaceTransition.CROSSFADE,
            sourceTimeMs = System.currentTimeMillis(),
            sourceTag = "people",
            front = TileFace.People(photos),
        )
        LiveTileEngine.publish(LiveTileEngine.PEOPLE, content)
        LiveTileEngine.publish(TileRouting.componentKey(context.packageName, PeopleActivity::class.java.name), content)
    }

    private const val BURST_SETTLE_MS = 300L
}
