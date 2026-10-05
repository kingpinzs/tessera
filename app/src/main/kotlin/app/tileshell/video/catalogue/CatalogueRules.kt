package app.tileshell.video.catalogue

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonLong
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/** A film or a series, as TMDB's v3 API names the two. */
enum class TitleType(val path: String, val wikidataProperty: String) {
    MOVIE("movie", "P4947"),
    TV("tv", "P4983");

    companion object {
        fun byPath(path: String?): TitleType? = entries.firstOrNull { it.path == path }
    }
}

/** One title of a result list or a strip. */
data class CatalogueItem(val type: TitleType, val id: Long, val title: String, val year: String, val posterPath: String?, val overview: String) {
    /** The tags' id: a film and a series may share a number. */
    val key: String get() = if (type == TitleType.MOVIE) id.toString() else "tv-$id"
}

/** A title page's content. [providers] are the services TMDB's watch-provider data (JustWatch's) names for the country. */
data class TitleDetails(val item: CatalogueItem, val genres: List<String>, val providers: List<String>)

/**
 * TMDB v3's answers, parsed (build task 12; BS-5's shapes). Pure: every function takes the response body and returns
 * what the hub draws, and none throws on a missing or mistyped field.
 */
object TmdbParse {
    /** `/3/configuration` → `images.secure_base_url`. */
    fun imageBase(body: String): String? =
        MiniJson.parseOrNull(body).jsonObject()?.get("images").jsonObject()?.jsonString("secure_base_url")?.takeIf { it.isNotBlank() }

    /** `/3/search/multi` and `/3/trending/all/week`: films and series only (a person row is dropped). */
    fun items(body: String, defaultType: TitleType? = null): List<CatalogueItem> =
        MiniJson.parseOrNull(body).jsonObject()?.get("results").jsonArray().mapNotNull { item(it.jsonObject(), defaultType) }

    /** `/3/movie/<id>` or `/3/tv/<id>` → the title and its genres' names. */
    fun details(body: String, type: TitleType): Pair<CatalogueItem, List<String>>? {
        val o = MiniJson.parseOrNull(body).jsonObject() ?: return null
        val item = item(o, type) ?: return null
        return item to o["genres"].jsonArray().mapNotNull { it.jsonObject()?.jsonString("name") }
    }

    /**
     * `/3/<type>/<id>/watch/providers` → the provider names for [country], in TMDB's display order, each once; the
     * streaming kinds first (flatrate, free, ads), then rent and buy. An unknown country yields none.
     */
    fun providers(body: String, country: String): List<String> {
        val region = MiniJson.parseOrNull(body).jsonObject()?.get("results").jsonObject()?.get(country.uppercase(Locale.ROOT)).jsonObject() ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (kind in listOf("flatrate", "free", "ads", "rent", "buy")) {
            region[kind].jsonArray().mapNotNull { it.jsonObject() }
                .sortedBy { it.jsonLong("display_priority") ?: Long.MAX_VALUE }
                .mapNotNull { it.jsonString("provider_name")?.takeIf(String::isNotBlank) }
                .forEach(out::add)
        }
        return out.toList()
    }

    private fun item(o: Map<String, Any?>?, defaultType: TitleType?): CatalogueItem? {
        o ?: return null
        val type = TitleType.byPath(o.jsonString("media_type")) ?: (if (o.containsKey("media_type")) null else defaultType) ?: return null
        val id = o.jsonLong("id") ?: return null
        val title = (if (type == TitleType.MOVIE) o.jsonString("title") else o.jsonString("name")) ?: o.jsonString("title") ?: o.jsonString("name") ?: return null
        val date = (if (type == TitleType.MOVIE) o.jsonString("release_date") else o.jsonString("first_air_date")).orEmpty()
        return CatalogueItem(type, id, title, date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty(), o.jsonString("poster_path")?.takeIf { it.isNotBlank() }, o.jsonString("overview").orEmpty())
    }
}

/** What the Browse page says above its content. The texts are the doc's (T17-1 "Offline", build task 12, Edge cases). */
enum class CatalogueNotice(val text: String?) {
    NONE(null),
    NO_KEY("Film search needs a TMDB key."),
    BAD_KEY("The saved TMDB key was refused."),
    OFFLINE("You're offline — showing what was saved"),
    NOT_ANSWERING("The catalogue isn't answering"),
    BUSY("The catalogue is busy, try again in a minute"),
}

/** How one catalogue request ended. */
sealed interface FetchOutcome {
    data class Answer(val body: String) : FetchOutcome
    data class Status(val code: Int) : FetchOutcome

    /** No connection could be made (a stopped server, no route, a timeout). */
    data object NoConnection : FetchOutcome
}

/** The catalogue's rules that need no network (build task 12): the cache's age rule, the lines and the notices. */
object CatalogueRules {
    const val CACHE_DAYS = 7L
    const val CACHE_MS = CACHE_DAYS * 24 * 60 * 60 * 1000

    const val LINE_NO_KEY = "catalogue: no TMDB key saved"

    /** An entry older than 7 days is stale: it is still shown when nothing better can be had, and re-fetched when it can. */
    fun isStale(ageMs: Long): Boolean = ageMs > CACHE_MS

    /**
     * Whether a request goes out. A search the user submits always asks the catalogue; a page that only comes back
     * (the app reopened on its last search, a title opened again) is served from an entry younger than 7 days.
     */
    fun shouldFetch(submitted: Boolean, cacheAgeMs: Long?): Boolean = submitted || cacheAgeMs == null || isStale(cacheAgeMs)

    /** `[video] catalogue "<q>": <n> | <n> (refreshed) | <n> (cached) | offline | error <code>` — the query and the status only (C-32). */
    fun line(subject: String, status: String): String = "catalogue \"$subject\": $status"

    fun countStatus(count: Int, replacedStale: Boolean): String = if (replacedStale) "$count (refreshed)" else count.toString()

    fun errorStatus(outcome: FetchOutcome): String = when (outcome) {
        is FetchOutcome.Status -> "error ${outcome.code}"
        FetchOutcome.NoConnection -> "error connect"
        is FetchOutcome.Answer -> "error parse"
    }

    fun notice(outcome: FetchOutcome): CatalogueNotice = when (outcome) {
        is FetchOutcome.Status -> when (outcome.code) {
            401 -> CatalogueNotice.BAD_KEY
            429 -> CatalogueNotice.BUSY
            else -> CatalogueNotice.NOT_ANSWERING
        }
        else -> CatalogueNotice.NOT_ANSWERING
    }

    /** An image's address: the configuration's base, a size, the title's poster path. */
    fun imageUrl(imageBase: String?, posterPath: String?, size: String = "w342"): String? {
        if (imageBase.isNullOrBlank() || posterPath.isNullOrBlank()) return null
        return imageBase.trimEnd('/') + "/" + size + "/" + posterPath.trimStart('/')
    }

    /**
     * The API's base address: the fixed endpoint, or — in a DEBUG build only — the QA pref's value. A release build
     * cannot be redirected, whatever the pref holds (build task 12).
     */
    fun base(debug: Boolean, qaPref: String?, fixed: String): String {
        val chosen = if (debug) qaPref?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } else null
        return (chosen ?: fixed).trimEnd('/') + "/"
    }
}

/**
 * The 7-day catalogue cache: one file per entry under `files/video_catalogue/`, written to a temp file and renamed,
 * an entry's age being its file's modification time (r3 V22). Plain java.io, so the unit tests run it.
 */
class CatalogueCache(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    class Entry(val body: String, val ageMs: Long)

    fun read(kind: String, key: String): Entry? {
        val f = file(kind, key)
        if (!f.isFile) return null
        return try {
            Entry(f.readText(Charsets.UTF_8), (now() - f.lastModified()).coerceAtLeast(0))
        } catch (e: Exception) {
            null
        }
    }

    fun write(kind: String, key: String, body: String) {
        try {
            dir.mkdirs()
            val target = file(kind, key)
            val temp = File(dir, target.name + ".tmp")
            FileOutputStream(temp).use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (!temp.renameTo(target)) temp.delete()
        } catch (e: Exception) {
            // A cache that cannot be written is a cache miss next time.
        }
    }

    fun file(kind: String, key: String): File = File(dir, "$kind-${safe(key)}.json")

    /** A file name for any key: short plain keys stay readable, anything else is its SHA-1. */
    private fun safe(key: String): String {
        val k = key.trim().lowercase(Locale.ROOT)
        if (k.isNotEmpty() && k.length <= 40 && k.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) return k
        return MessageDigest.getInstance("SHA-1").digest(k.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
