package app.tileshell.video.handoff

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import app.tileshell.video.VideoLines
import app.tileshell.video.catalogue.TitleType
import java.net.URLEncoder
import java.util.Locale

/** A title as a hand-off needs it: what the catalogue calls it, and its TMDB id for the Wikidata lookup. */
data class TitleRef(val type: TitleType, val tmdbId: Long, val name: String, val year: String)

/** A Wikidata property that holds a service's own id for a title, and the SPARQL variable it is read into. */
data class IdProperty(val variable: String, val property: String, val type: TitleType?)

/**
 * One streaming service of the table (BS-4, `qa/phase-17/BUILDSTART/README.md`). Every title and search PATH is
 * unverified until the owner's P13 on the phone; the package names were confirmed on Google Play.
 *
 * @param ids the Wikidata properties that hold this service's title id, in the order they are tried
 * @param titleUrl the title's address from a found id, or null when the value is one to skip
 * @param searchUrl the search address with `%s` for the query, or null when the service has none
 * @param providerNames how TMDB's watch-provider data names the service (compared in lower case, by prefix)
 */
class StreamingService(
    val id: String,
    val label: String,
    val packageName: String,
    val ids: List<IdProperty>,
    val titleUrl: (variable: String, value: String) -> String?,
    val searchUrl: String?,
    val providerNames: List<String>,
)

/** What a tap on "Watch on <service>" opens, decided before any intent exists. */
sealed interface HandoffPlan {
    /** The service's own page for the title; [searchFallback] is tried when nothing answers it. */
    data class Title(val url: String, val searchFallback: String?) : HandoffPlan
    data class Search(val url: String) : HandoffPlan

    /** No id and no search address (Disney+): the app itself is opened. */
    data object Launch : HandoffPlan
}

/**
 * The services table and its logic (build task 11; T17-1, T17-15, r3 D13), free of Android types so the unit tests
 * cover every row: which service a provider name means, the Wikidata query, the answer's parsing, and the plan —
 * the title's deep link where Wikidata has that service's id, else the service's search, else the app.
 */
object ServicesTable {
    /** The QA-Flix fixture's service: in the table of DEBUG builds only, with its fixture Wikidata property. */
    const val QA_FLIX_ID = "qa-flix"
    const val QA_FLIX_PACKAGE = "app.tileshell.testclient.qaflix"
    const val QA_FLIX_PROPERTY = "P99990001"

    private val production: List<StreamingService> = listOf(
        StreamingService(
            "netflix", "Netflix", "com.netflix.mediaclient",
            listOf(IdProperty("netflix", "P1874", null)),
            { _, v -> "https://www.netflix.com/title/$v" },
            "https://www.netflix.com/search?q=%s", listOf("netflix"),
        ),
        StreamingService(
            "prime-video", "Prime Video", "com.amazon.avod.thirdpartyclient",
            listOf(IdProperty("primeGti", "P14462", null), IdProperty("primeId", "P14440", null)),
            { _, v -> "https://app.primevideo.com/detail?gti=$v" },
            "https://www.primevideo.com/search/?phrase=%s", listOf("amazon prime video", "prime video", "amazon video"),
        ),
        StreamingService(
            "disney-plus", "Disney+", "com.disney.disneyplus",
            listOf(IdProperty("disneyBrowse", "P13902", null)),
            { _, v -> "https://www.disneyplus.com/browse/$v" },   // the value includes "entity-"
            null, listOf("disney plus", "disney+"),
        ),
        StreamingService(
            "hulu", "Hulu", "com.hulu.plus",
            listOf(IdProperty("huluMovie", "P6466", TitleType.MOVIE), IdProperty("huluSeries", "P6467", TitleType.TV)),
            { variable, v -> if (variable == "huluMovie") "https://www.hulu.com/movie/$v" else "https://www.hulu.com/series/$v" },
            "https://www.hulu.com/search?q=%s", listOf("hulu"),
        ),
        StreamingService(
            "max", "Max", "com.wbd.stream",
            listOf(IdProperty("hboMax", "P8298", null)),
            // The value carries "movie/" or "show/"; the stale "feature/urn:" and "series/urn:" forms are skipped.
            { _, v -> if (v.startsWith("feature/urn:") || v.startsWith("series/urn:")) null else "https://play.hbomax.com/$v" },
            "https://play.hbomax.com/search?q=%s", listOf("max", "hbo max"),
        ),
        StreamingService(
            "apple-tv", "Apple TV", "com.apple.atve.androidtv.appletv",
            listOf(IdProperty("appleMovie", "P9586", TitleType.MOVIE), IdProperty("appleShow", "P9751", TitleType.TV)),
            { variable, v -> if (variable == "appleMovie") "https://tv.apple.com/movie/$v" else "https://tv.apple.com/show/$v" },
            "https://tv.apple.com/search?term=%s", listOf("apple tv"),
        ),
        // No usable title id on Wikidata: these rows always open the service's search (BS-4, the agent's call; H11 / P13).
        StreamingService("paramount-plus", "Paramount+", "com.cbs.app", emptyList(), { _, _ -> null }, "https://www.paramountplus.com/search/?q=%s", listOf("paramount plus", "paramount+")),
        StreamingService("peacock", "Peacock", "com.peacocktv.peacockandroid", emptyList(), { _, _ -> null }, "https://www.peacocktv.com/watch/search?q=%s", listOf("peacock")),
        StreamingService("youtube", "YouTube", "com.google.android.youtube", emptyList(), { _, _ -> null }, "https://www.youtube.com/results?search_query=%s", listOf("youtube")),
        StreamingService("plex", "Plex", "com.plexapp.android", emptyList(), { _, _ -> null }, "https://watch.plex.tv/search?q=%s", listOf("plex")),
        // Jellyfin's app is left out: it has no link or search intent, and the user's Jellyfin is the Media server page.
    )

    private val qaFlix = StreamingService(
        QA_FLIX_ID, "QA-Flix", QA_FLIX_PACKAGE,
        listOf(IdProperty("qaflix", QA_FLIX_PROPERTY, null)),
        { _, v -> "https://qa-flix.test/title/$v" },
        "https://qa-flix.test/search?q=%s", listOf("qa-flix"),
    )

    /** The table: BS-4's ten services, plus the QA-Flix fixture service in a DEBUG build only. */
    fun services(debug: Boolean): List<StreamingService> = if (debug) production + qaFlix else production

    /**
     * The service a watch-provider name means: the whole name, or the name before a plan suffix ("Netflix basic with
     * Ads", "Max Amazon Channel" is NOT Max — a channel inside another app is that other app's).
     */
    fun match(providerName: String, debug: Boolean): StreamingService? {
        val n = providerName.trim().lowercase(Locale.ROOT)
        if (n.contains(" channel")) return null
        return services(debug).firstOrNull { s -> s.providerNames.any { p -> n == p || n.startsWith("$p ") } }
    }

    /**
     * BS-4's query for one title: the item by its TMDB id (P4947 film, P4983 series), each service's id property
     * OPTIONAL. Only digits go into it ([tmdbId] is a Long).
     */
    fun sparql(type: TitleType, tmdbId: Long, debug: Boolean): String {
        val properties = services(debug).flatMap { it.ids }
        return buildString {
            append("SELECT ?item")
            for (p in properties) append(" ?").append(p.variable)
            append(" WHERE { ?item wdt:").append(type.wikidataProperty).append(" \"").append(tmdbId).append("\" .")
            for (p in properties) append(" OPTIONAL { ?item wdt:").append(p.property).append(" ?").append(p.variable).append(" }")
            append(" }")
        }
    }

    /**
     * The answer's ids: variable → value, the FIRST value per variable (a property with several values multiplies the
     * rows), `item` left out. Null when the body is not a SPARQL JSON answer.
     */
    fun parseWikidata(body: String): Map<String, String>? {
        val bindings = MiniJson.parseOrNull(body).jsonObject()?.get("results").jsonObject()?.get("bindings") ?: return null
        val out = LinkedHashMap<String, String>()
        for (row in bindings.jsonArray()) {
            for ((variable, cell) in row.jsonObject() ?: continue) {
                if (variable == "item" || variable in out) continue
                cell.jsonObject()?.jsonString("value")?.takeIf { it.isNotBlank() }?.let { out[variable] = it }
            }
        }
        return out
    }

    /** What a service's id may be made of: Wikidata is public data, and the value becomes part of an address. */
    private val SAFE_ID = Regex("[A-Za-z0-9._:/-]{1,200}")

    /**
     * An id that stays where the service's address puts it (B-10): the safe characters, no leading `/` and no `..`
     * segment — either would steer a "Watch on" link to another path of the service's own host.
     */
    fun isSafeId(value: String): Boolean = SAFE_ID.matches(value) && !value.startsWith("/") && value.split('/').none { it == ".." }

    /** The service's own id for the title among Wikidata's [ids], as the title's address; null when it has none. */
    fun titleUrl(service: StreamingService, type: TitleType, ids: Map<String, String>): String? {
        for (p in service.ids) {
            if (p.type != null && p.type != type) continue
            val value = ids[p.variable]?.takeIf(::isSafeId) ?: continue
            service.titleUrl(p.variable, value)?.let { return it }
        }
        return null
    }

    /** The service's search for the title's name and year ("Blade Runner 1982" → `…?q=Blade+Runner+1982`). */
    fun searchUrl(service: StreamingService, title: TitleRef): String? {
        val form = service.searchUrl ?: return null
        val query = listOf(title.name.trim(), title.year.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        return form.replace("%s", URLEncoder.encode(query, "UTF-8"))
    }

    fun plan(service: StreamingService, title: TitleRef, ids: Map<String, String>): HandoffPlan {
        val search = searchUrl(service, title)
        val deep = titleUrl(service, title.type, ids)
        return when {
            deep != null -> HandoffPlan.Title(deep, search)
            search != null -> HandoffPlan.Search(search)
            else -> HandoffPlan.Launch
        }
    }

    /** `[video] watch-on <service> "<title>": id <found|none> (wikidata)`. */
    fun idLine(service: StreamingService, title: TitleRef, found: Boolean): String =
        "watch-on ${service.id} \"${VideoLines.text(title.name)}\": id ${if (found) "found" else "none"} (wikidata)"

    /** `[video] watch-on <service> "<title>" -> <intent> | not installed`: the address opened, `launch`, or `not installed`. */
    fun openLine(service: StreamingService, title: TitleRef, opened: String): String =
        "watch-on ${service.id} \"${VideoLines.text(title.name)}\" -> ${VideoLines.text(opened, VideoLines.ADDRESS_MAX)}"
}
