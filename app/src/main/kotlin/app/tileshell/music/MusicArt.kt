package app.tileshell.music

import android.content.Context
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Album art, read the way [app.tileshell.feeds.PhotosFeed] reads photo thumbnails (phase 10 task 6).
 *
 * `loadThumbnail` on the track's own URI rather than the long-deprecated `content://media/…/albumart`
 * path: that table was removed years ago and the modern call also serves embedded art, which is where
 * the art on a phone full of copied files actually lives.
 *
 * Cached by album id and never evicted while the app is open. An album's art is a few tens of KB at
 * this size and a library has tens of albums, so the cache is bounded by the library itself; a miss
 * costs a disk read on every scroll past the row, which is the thing worth avoiding.
 */
object MusicArt {

    private val cache = HashMap<Long, ImageBitmap?>()

    fun cached(albumId: Long): ImageBitmap? = cache[albumId]

    fun has(albumId: Long): Boolean = cache.containsKey(albumId)

    /** Blocking: call it off the main thread. Returns null when the album has no art at all. */
    fun load(context: Context, album: Album, sizePx: Int): ImageBitmap? {
        synchronized(cache) { if (cache.containsKey(album.id)) return cache[album.id] }
        val track = album.tracks.firstOrNull()
        val art = track?.let {
            runCatching {
                context.contentResolver.loadThumbnail(MusicStore.uriOf(it), Size(sizePx, sizePx), null).asImageBitmap()
            }.getOrNull()
        }
        synchronized(cache) { cache[album.id] = art }
        return art
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }
}
