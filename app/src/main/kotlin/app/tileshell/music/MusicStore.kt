package app.tileshell.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The music library, read from MediaStore and kept current (phase 10 build task 2).
 *
 * **Everything MediaStore calls audio** (Q6), with no IS_MUSIC predicate: ringtones, alarms,
 * notification sounds and voice recordings are audio and are in the library by ruling. [MusicGrouping]
 * is what copes with their missing metadata, and it is tested on the JVM; this file is the part that
 * has to talk to the platform.
 *
 * **Observed, not scanned once.** A file copied onto the phone while the shell is running appears
 * without a restart — the same contract PhotosFeed has for images, and acceptance row E4. The observer
 * is registered on the audio collection with descendants, because a new file is a new row under it.
 */
object MusicStore {

    private val tracks = MutableStateFlow<List<Track>>(emptyList())

    /** Every audio file on the phone, unsorted; the pivots come from [MusicGrouping]. */
    val library: StateFlow<List<Track>> = tracks.asStateFlow()

    private var observer: ContentObserver? = null

    private val COLLECTION: Uri get() = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
    )

    /** Whether the library can be read at all. Acceptance row E18 is what a denial must look like. */
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Reads the library now, and keeps reading it as MediaStore changes. Safe to call more than once. */
    fun start(context: Context) {
        val app = context.applicationContext
        refresh(app, "start")
        if (observer != null) return
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = refresh(app, "media change")
        }
        runCatching { app.contentResolver.registerContentObserver(COLLECTION, true, watcher) }
            .onSuccess { observer = watcher }
            .onFailure { Diagnostics.add("music", "could not observe the audio collection: $it") }
    }

    fun refresh(context: Context, reason: String) {
        if (!hasAccess(context)) {
            // Said rather than guessed: an empty library and a denied permission look identical on
            // screen, and the difference is the whole of what the Settings row has to offer (E18).
            Diagnostics.add("music", "no audio access: the library is empty until it is granted ($reason)")
            tracks.value = emptyList()
            return
        }
        val found = query(context)
        tracks.value = found
        Diagnostics.add("music", "library ($reason): ${found.size} tracks")
    }

    private fun query(context: Context): List<Track> = runCatching {
        val out = ArrayList<Track>(256)
        context.contentResolver.query(COLLECTION, PROJECTION, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                out += Track(
                    id = c.getLong(id),
                    title = Track.clean(c.getString(title), Track.UNKNOWN_TITLE),
                    artist = Track.clean(c.getString(artist), Track.UNKNOWN_ARTIST),
                    album = Track.clean(c.getString(album), Track.UNKNOWN_ALBUM),
                    albumId = c.getLong(albumId),
                    durationMs = c.getLong(duration),
                    // DATE_ADDED is seconds since the epoch, unlike almost everything else on Android.
                    addedAtMs = c.getLong(added) * 1000L,
                )
            }
        }
        out
    }.onFailure { Diagnostics.add("music", "library query failed: $it") }.getOrDefault(emptyList())

    /** The playable URI for a track, which is what Media3 is handed (build task 3). */
    fun uriOf(track: Track): Uri = ContentUris.withAppendedId(COLLECTION, track.id)
}
