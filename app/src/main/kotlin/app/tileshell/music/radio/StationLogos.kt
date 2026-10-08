package app.tileshell.music.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.tileshell.music.MusicLive
import app.tileshell.video.catalogue.VideoHttp
import java.io.ByteArrayOutputStream

/**
 * The shell's own fetch of a station's logo (phase 20, r3 D2; the rules are [StationLogo]'s): the address through
 * [StationUrl.accept], at most [StationLogo.MAX_BYTES] off the wire, decoded at bounds and re-encoded no larger than
 * [StationLogo.MAX_PX] a side — bytes fit for an item's `artworkData` and for a row. Asked only for favourites and
 * the playing station. The request carries a header, so `VideoHttp` follows no redirect with it: a logo's host cannot
 * send the phone on to another address.
 *
 * These bytes are the ONLY art a live item is drawn with (review R20-2): a stream can carry a picture of its own, of
 * any size, and the session hands its controllers that raw metadata before the corrected value. So the screens take a
 * station's art from [cachedFor] — never from the session — and decode it through [decode], bounds first.
 */
class StationLogos private constructor(context: Context) {
    private val app = context.applicationContext

    /** A logo or the knowledge that there is none, by station and address: a miss is not fetched again. */
    private class Entry(val bytes: ByteArray?)

    private val cache = LruCache<String, Entry>(CACHED)

    /** The logo already fetched in this process, with no request; null when there is none (yet). */
    fun cached(station: Station): ByteArray? = cache.get(key(station))?.bytes

    /**
     * The logo already fetched for the station a `station:` media id names — a favourite, else the directory's row,
     * as the service finds it — with no request; null for any other id, and when there is none (yet).
     */
    fun cachedFor(mediaId: String?): ByteArray? {
        val uuid = MusicLive.stationUuid(mediaId) ?: return null
        val station = RadioFavouritesStore.get(app).stations().firstOrNull { it.uuid == uuid } ?: RadioDirectoryStore.get(app).directory.value.index.byUuid(uuid)
        return station?.let { cached(it) }
    }

    /** The station's bounded logo, or null. BLOCKS on the network: call it off the main thread. */
    fun logo(station: Station): ByteArray? {
        val key = key(station)
        cache.get(key)?.let { return it.bytes }
        val url = StationLogo.url(station.favicon, RadioNet.qaHost(app))
        // No network is not "no logo": only an answer that was had is remembered.
        if (url != null && !VideoHttp.online(app)) return null
        val bytes = url?.let { VideoHttp.bytes(it, RadioNet.headers(), StationLogo.MAX_BYTES.toLong()) }?.takeIf { StationLogo.accept(it) }?.let { bounded(it) }
        cache.put(key, Entry(bytes))
        return bytes
    }

    private fun key(station: Station): String = station.uuid + " " + station.favicon

    private fun bounded(raw: ByteArray): ByteArray? = runCatching {
        val bitmap = decode(raw) ?: return@runCatching null
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    companion object {
        private const val CACHED = 48

        @Volatile
        private var instance: StationLogos? = null

        fun get(context: Context): StationLogos =
            instance ?: synchronized(this) {
                instance ?: StationLogos(context).also { instance = it }
            }

        /** [StationLogos.cachedFor] of this process's logos; null when none was ever asked for. Never a request. */
        fun cachedFor(mediaId: String?): ByteArray? = instance?.cachedFor(mediaId)

        /**
         * A live item's art, decoded — the one way such bytes become a picture: no more than [StationLogo.MAX_BYTES],
         * the bounds read first, and never larger than [StationLogo.MAX_PX] a side ([StationLogo.sampleSize]; 0 = not
         * decoded at all). Null for anything else, and for bytes that are no picture.
         */
        fun decode(bytes: ByteArray?): Bitmap? {
            if (bytes == null || !StationLogo.accept(bytes)) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sample = StationLogo.sampleSize(bounds.outWidth, bounds.outHeight)
                if (sample <= 0) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
}
