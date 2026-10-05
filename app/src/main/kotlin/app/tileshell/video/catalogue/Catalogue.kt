package app.tileshell.video.catalogue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.tileshell.BuildConfig
import app.tileshell.diag.Diagnostics
import app.tileshell.net.CredentialStore
import app.tileshell.net.FixedEndpoints
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/** The three debug-only QA redirects (build tasks 11–13): read from the prefs file `prefs_edit.py` writes, DEBUG builds only. */
object QaBases {
    const val CATALOGUE = "qa_catalogue_base"
    const val WIKIDATA = "qa_wikidata_base"
    const val SERVER = "qa_server_base"

    /** The pref's value in a DEBUG build, else null: a release build never reads it. */
    fun read(context: Context, key: String): String? {
        if (!BuildConfig.DEBUG) return null
        return context.applicationContext.getSharedPreferences("start_theme", Context.MODE_PRIVATE).getString(key, null)?.takeIf { it.isNotBlank() }
    }
}

/** One GET, off the main thread, with the project's 10-s timeouts. Nothing here logs: a URL may carry a query. */
object VideoHttp {
    const val TIMEOUT_MS = 10_000

    fun get(url: String, headers: Map<String, String>): FetchOutcome = send(url, headers, null)

    /** A JSON POST. The body may hold a password: it goes to the socket and nowhere else. */
    fun post(url: String, headers: Map<String, String>, json: String): FetchOutcome = send(url, headers, json)

    private fun send(url: String, headers: Map<String, String>, json: String?): FetchOutcome {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return FetchOutcome.NoConnection
        }
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false   // a redirect would carry the request's headers to another host
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (json != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code in 200..299) FetchOutcome.Answer(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            else FetchOutcome.Status(code, conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
        } catch (e: IOException) {
            FetchOutcome.NoConnection
        } finally {
            conn.disconnect()
        }
    }

    fun bytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        val conn = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { return null }
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = headers.isEmpty()   // headers are never carried to a redirect's host
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes() } else null
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun online(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/** What a Browse request gave the page. */
class CatalogueResult<T>(val value: T?, val notice: CatalogueNotice, val fromCache: Boolean)

/**
 * The catalogue (build task 12; T17-1): TMDB's v3 API with the owner's read token as `Authorization: Bearer` — never an
 * `api_key` parameter — over a 7-day cache, so the hub works offline. Every call blocks: run it off the main thread.
 *
 * The token is read from the credential store on each call and goes into the one request header; it is in no URL, no
 * line and no exception. Images come from `/3/configuration`'s base and are fetched WITHOUT the token.
 */
class Catalogue(context: Context) {
    private val app = context.applicationContext
    private val cache = CatalogueCache(File(app.filesDir, DIR))
    private val images = File(File(app.filesDir, DIR), "img")

    private fun apiBase(): String = CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.CATALOGUE), FixedEndpoints.TMDB_API)

    fun hasKey(): Boolean = CredentialStore.of(app).has(CredentialStore.TMDB)

    /** `catalogue.search`: films and series for [query]. */
    fun search(query: String): CatalogueResult<List<CatalogueItem>> {
        val q = query.trim()
        return request(q, "search", q, "3/search/multi?query=${enc(q)}&include_adult=false") { TmdbParse.items(it) }
    }

    /** One of Browse's strips. */
    fun section(section: BrowseSection): CatalogueResult<List<CatalogueItem>> =
        request(section.title, "section", section.id, path = "3/${section.path}") { TmdbParse.items(it, section.defaultType) }

    /** `catalogue.lookup`: a title's page — its details and the services its watch-provider data names. */
    fun lookup(type: TitleType, id: Long, title: String): CatalogueResult<TitleDetails> {
        val key = "${type.path}-$id"
        val details = request(title, "title", key, path = "3/${type.path}/$id") { TmdbParse.details(it, type) }
        val pair = details.value ?: return CatalogueResult(null, details.notice, details.fromCache)
        val providers = request(title, "providers", key, path = "3/${type.path}/$id/watch/providers", quiet = true) {
            TmdbParse.providers(it, country())
        }
        return CatalogueResult(TitleDetails(pair.first, pair.second, providers.value.orEmpty()), details.notice, details.fromCache)
    }

    private fun country(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    /**
     * One GET over the cache, and its diagnostics line: no key → no request at all; offline → the saved answer; else
     * the catalogue is asked, and the saved answer stays on screen when it fails.
     */
    private fun <T> request(subject: String, kind: String, key: String, path: String, quiet: Boolean = false, parse: (String) -> T?): CatalogueResult<T> {
        fun say(status: String) { if (!quiet) Diagnostics.add("video", CatalogueRules.line(subject, status)) }
        val token = CredentialStore.of(app).get(CredentialStore.TMDB)
        if (token == null) {
            if (!quiet) Diagnostics.add("video", CatalogueRules.LINE_NO_KEY)
            return CatalogueResult(null, CatalogueNotice.NO_KEY, false)
        }
        val entry = cache.read(kind, key)
        val cached = entry?.let { parse(it.body) }
        if (!VideoHttp.online(app)) {
            say("offline")
            return CatalogueResult(cached, CatalogueNotice.OFFLINE, true)
        }
        val headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json")
        if (kind == "search" || kind == "section") configuration(headers)
        val outcome = VideoHttp.get(apiBase() + path, headers)
        val fresh = (outcome as? FetchOutcome.Answer)?.let { parse(it.body) }
        if (fresh != null) {
            cache.write(kind, key, (outcome as FetchOutcome.Answer).body)
            say(CatalogueRules.countStatus(count(fresh), replacedStale = entry != null && CatalogueRules.isStale(entry.ageMs)))
            return CatalogueResult(fresh, CatalogueNotice.NONE, false)
        }
        say(CatalogueRules.errorStatus(outcome))
        return CatalogueResult(cached, CatalogueRules.notice(outcome), true)
    }

    private fun count(value: Any?): Int = (value as? Collection<*>)?.size ?: 1

    /** `/3/configuration`'s image base, kept 7 days per API base (the QA redirect carries the image base with it, T17-20). */
    private fun configuration(headers: Map<String, String>): String? {
        val key = sha1(apiBase())
        val entry = cache.read("config", key)
        val known = entry?.let { TmdbParse.imageBase(it.body) }
        if (known != null && !CatalogueRules.isStale(entry.ageMs)) return known
        val outcome = VideoHttp.get(apiBase() + "3/configuration", headers)
        val fresh = (outcome as? FetchOutcome.Answer)?.let { TmdbParse.imageBase(it.body) } ?: return known
        cache.write("config", key, (outcome as FetchOutcome.Answer).body)
        return fresh
    }

    /** The image base last read for this API base, with no request. */
    private fun imageBase(): String? = cache.read("config", sha1(apiBase()))?.let { TmdbParse.imageBase(it.body) }

    /**
     * A poster, from the image cache or the network (no token is sent: the image host is another host). Null when the
     * title has no artwork or it cannot be had — the page then draws the placeholder.
     */
    fun poster(posterPath: String?): Bitmap? {
        val url = CatalogueRules.imageUrl(imageBase(), posterPath) ?: return null
        val file = File(images, sha1(url))
        val fresh = file.isFile && !CatalogueRules.isStale(System.currentTimeMillis() - file.lastModified())
        if (!fresh && VideoHttp.online(app)) {
            VideoHttp.bytes(url)?.let { bytes ->
                runCatching {
                    images.mkdirs()
                    val temp = File(images, file.name + ".tmp")
                    temp.writeBytes(bytes)
                    if (!temp.renameTo(file)) temp.delete()
                }
            }
        }
        return if (file.isFile) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
    }

    /** The last search the Browse page showed, so the page comes back on it (the offline row reopens the app). */
    var lastQuery: String
        get() = runCatching { File(File(app.filesDir, DIR), LAST_QUERY).readText(Charsets.UTF_8) }.getOrDefault("")
        set(value) {
            runCatching {
                val dir = File(app.filesDir, DIR).apply { mkdirs() }
                val temp = File(dir, "$LAST_QUERY.tmp")
                temp.writeText(value, Charsets.UTF_8)
                temp.renameTo(File(dir, LAST_QUERY))
            }
        }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object {
        const val DIR = "video_catalogue"
        private const val LAST_QUERY = "last_query.txt"
    }
}

/** Browse's strips (Y8): each a TMDB list. */
enum class BrowseSection(val id: String, val title: String, val path: String, val defaultType: TitleType?) {
    TRENDING("trending", "Trending", "trending/all/week", null),
    MOVIES("movies", "Popular movies", "movie/popular", TitleType.MOVIE),
    TV("tv", "Popular TV", "tv/popular", TitleType.TV),
}
