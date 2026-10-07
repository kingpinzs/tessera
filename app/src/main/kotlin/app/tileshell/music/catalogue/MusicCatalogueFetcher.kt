package app.tileshell.music.catalogue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import app.tileshell.BuildConfig
import app.tileshell.diag.Diagnostics
import app.tileshell.music.MusicNet
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.ImageHop
import app.tileshell.video.catalogue.QaBases
import app.tileshell.video.catalogue.VideoHttp

/**
 * The catalogue's requests, with everything they touch handed in (phase 20 build task 8) — so a unit test runs the
 * whole of it with no network and no clock. What it holds to:
 *  - a search is asked only when submitted, through [gate]: one request in flight, starts ≥ 1.0 s apart;
 *  - a failed search is NEVER asked again by itself — a 503 is MusicBrainz saying "too fast", and a retry is the
 *    wrong answer to that; the next request is the user's next submit;
 *  - every request it makes carries the `Tessera/<version> (…)` User-Agent, except a cover's followed redirects,
 *    which carry no header at all ([CoverArt.fetch]);
 *  - rows and artwork are kept for the SESSION only (the process's life), in memory: nothing is written to disk.
 */
class CatalogueSession(
    private val apiBase: () -> String,
    private val coverBase: () -> String,
    private val userAgent: String,
    private val online: () -> Boolean,
    private val get: (url: String, headers: Map<String, String>) -> FetchOutcome,
    private val hop: (url: String, headers: Map<String, String>) -> ImageHop,
    private val gate: RequestGate,
    private val log: (String) -> Unit,
) {
    private val searches = HashMap<String, List<CatalogueTrack>>()

    /** The artwork last fetched, by release: the oldest goes when there are more than [MAX_COVERS]. */
    private val covers = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > MAX_COVERS
    }

    /** The rows this session already has for [query], with no request; null when it has not been answered. */
    fun cached(query: String): List<CatalogueTrack>? = synchronized(searches) { searches[MusicCatalogue.key(query)] }

    /**
     * One submitted search, and its `[music] catalogue "<q>": …` line. Blocking: it waits its turn at the gate. A
     * search this session has answered is given back with no request and no line.
     */
    fun search(query: String): MusicCatalogueResult {
        val q = query.trim()
        if (q.isEmpty()) return MusicCatalogueResult(q, emptyList(), MusicCatalogueNotice.NONE)
        cached(q)?.let { return MusicCatalogueResult(q, it, MusicCatalogueNotice.NONE) }
        if (!online()) {
            log(MusicCatalogue.line(q, MusicCatalogue.STATUS_OFFLINE))
            return MusicCatalogueResult(q, emptyList(), MusicCatalogueNotice.OFFLINE)
        }
        return gate.run {
            // Answered while this submit waited at the gate (the same search, submitted twice): no second request.
            cached(q)?.let { return@run MusicCatalogueResult(q, it, MusicCatalogueNotice.NONE) }
            val outcome = get(MusicCatalogue.searchUrl(apiBase(), q), mapOf("User-Agent" to userAgent, "Accept" to "application/json"))
            val tracks = (outcome as? FetchOutcome.Answer)?.let { MusicCatalogue.parse(it.body) }
            if (tracks != null) {
                synchronized(searches) { searches[MusicCatalogue.key(q)] = tracks }
                log(MusicCatalogue.line(q, tracks.size.toString()))
                MusicCatalogueResult(q, tracks, MusicCatalogueNotice.NONE)
            } else {
                log(MusicCatalogue.line(q, MusicCatalogue.errorStatus(outcome)))
                MusicCatalogueResult(q, emptyList(), MusicCatalogue.notice(outcome))
            }
        }
    }

    /** A row's artwork as bytes (an image of at most `VideoHttp.MAX_IMAGE_BYTES`), or null: the placeholder is drawn. Blocking. */
    fun cover(track: CatalogueTrack): ByteArray? {
        val release = track.releaseId ?: return null
        synchronized(covers) { covers[release] }?.let { return it }
        val url = MusicCatalogue.coverUrl(coverBase(), release) ?: return null
        if (!online()) return null
        val bytes = CoverArt.fetch(url, mapOf("User-Agent" to userAgent), hop) ?: return null
        synchronized(covers) { covers[release] = bytes }
        return bytes
    }

    companion object {
        const val MAX_COVERS = 64
    }
}

/**
 * The catalogue as the Music app calls it: ONE [CatalogueSession] for the process, wired to `VideoHttp` (the project's
 * one HTTP path), the main ring's `[music]` lines and the real clock. Every call blocks: run it off the main thread.
 */
object MusicCatalogueFetcher {
    /** The longer side, in pixels, a cover is decoded to at most (the archive's thumbnail is 250). */
    private const val COVER_PX = 512

    @Volatile private var session: CatalogueSession? = null

    private fun session(context: Context): CatalogueSession = session ?: synchronized(this) {
        session ?: run {
            val app = context.applicationContext
            CatalogueSession(
                // The two fixture routes (T20-6): debug-only prefs, each read at this one site behind the debug gate
                // (`TrustWiringScanTest`); a release build always gets the real endpoints.
                apiBase = { CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.MUSIC_CATALOGUE), MusicCatalogue.API) },
                coverBase = { CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.COVERART), MusicCatalogue.COVERS) },
                userAgent = MusicNet.userAgent(BuildConfig.VERSION_NAME),
                online = { VideoHttp.online(app) },
                get = { url, headers -> VideoHttp.get(url, headers) },
                hop = { url, headers -> VideoHttp.hop(url, headers) },
                gate = RequestGate(SystemClock::elapsedRealtime) { Thread.sleep(it) },
                log = { Diagnostics.add("music", it) },
            )
        }.also { session = it }
    }

    /** One submitted search (submit-only: never call it as the user types). */
    fun search(context: Context, query: String): MusicCatalogueResult = session(context).search(query)

    /** The rows already answered for [query] this session, with no request — what a page redrawn shows at once. */
    fun cached(context: Context, query: String): List<CatalogueTrack>? = session(context).cached(query)

    /** A row's artwork, or null for the placeholder. */
    fun cover(context: Context, track: CatalogueTrack): Bitmap? {
        val bytes = session(context).cover(track) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > COVER_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
