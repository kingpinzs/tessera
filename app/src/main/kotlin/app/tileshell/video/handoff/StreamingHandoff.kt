package app.tileshell.video.handoff

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.tileshell.BuildConfig
import app.tileshell.diag.Diagnostics
import app.tileshell.net.FixedEndpoints
import app.tileshell.video.catalogue.Catalogue
import app.tileshell.video.catalogue.CatalogueCache
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.QaBases
import app.tileshell.video.catalogue.VideoHttp
import app.tileshell.video.server.MediaServer
import java.io.File
import java.net.URLEncoder

/**
 * The streaming hand-off (Decisions T17-1 "The interface"; build task 11): which streaming apps are on the phone, and
 * "open this title in that app". A plain object with no process of its own — the hub calls it in `:video`, and phases
 * 20 and 21 call the same object from where they run. Its stores are files re-read on use.
 *
 * It opens other apps only: it plays nothing, signs in to nothing and reads no service's catalogue (Scope, Out).
 */
object StreamingHandoff {
    /** How a tap on "Watch on <service>" ended. */
    enum class Opened { TITLE, SEARCH, LAUNCH, NOT_INSTALLED }

    /** `catalogue.search(query)` / `catalogue.lookup(id)`: TMDB behind the 7-day cache. */
    fun catalogue(context: Context): Catalogue = Catalogue(context)

    /** `mediaServer`: the Jellyfin client — `connect(host, user, password)`, `library()`, `streamUrl(item)`. */
    fun mediaServer(context: Context): MediaServer = MediaServer(context)

    /**
     * The table's services that are on the phone NOW: resolved against the package manager at every call, never kept
     * across calls, so an app installed a minute ago appears and one just removed is gone.
     */
    fun installedServices(context: Context): List<StreamingService> =
        ServicesTable.services(BuildConfig.DEBUG).filter { installed(context, it) }

    fun installed(context: Context, service: StreamingService): Boolean {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(service.packageName)
        if (pm.queryIntentActivities(launcher, 0).isNotEmpty()) return true
        val probe = service.searchUrl?.replace("%s", "x") ?: return false
        return pm.queryIntentActivities(view(probe, service), 0).isNotEmpty()
    }

    /**
     * `openTitle(service, title)`: the intent a caller starts — the service's own page for the title where Wikidata has
     * that service's id, else its search for the title's name and year, else (no search address) the app itself. Every
     * form names the service's package (r3 D13: since Android 12 an http(s) VIEW with no package opens an app only for
     * a verified domain). Blocking — it may ask Wikidata: call it off the main thread.
     */
    fun openTitle(context: Context, service: StreamingService, title: TitleRef): Intent? =
        intentFor(context, service, ServicesTable.plan(service, title, wikidataIds(context, service, title)), fallback = false)?.first

    /**
     * Starts the hand-off and writes its lines. An intent nothing answers falls back to the search address, and when
     * that is not answered either the line reads `not installed`. Blocking, as [openTitle].
     */
    fun open(context: Context, service: StreamingService, title: TitleRef): Opened {
        val plan = ServicesTable.plan(service, title, wikidataIds(context, service, title))
        for (fallback in listOf(false, true)) {
            val (intent, address) = intentFor(context, service, plan, fallback) ?: continue
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Diagnostics.add("video", ServicesTable.openLine(service, title, address))
                return when {
                    address == "launch" -> Opened.LAUNCH
                    plan is HandoffPlan.Title && !fallback -> Opened.TITLE
                    else -> Opened.SEARCH
                }
            } catch (e: ActivityNotFoundException) {
                // The next form, if there is one.
            } catch (e: SecurityException) {
                // An activity that is there but not open to the shell answers as if it were not.
            }
        }
        Diagnostics.add("video", ServicesTable.openLine(service, title, "not installed"))
        return Opened.NOT_INSTALLED
    }

    /** The line a title page writes for a service its catalogue names that is not on the phone. */
    fun noteNotInstalled(service: StreamingService, title: TitleRef) {
        Diagnostics.add("video", ServicesTable.openLine(service, title, "not installed"))
    }

    private fun intentFor(context: Context, service: StreamingService, plan: HandoffPlan, fallback: Boolean): Pair<Intent, String>? = when (plan) {
        is HandoffPlan.Title -> if (!fallback) view(plan.url, service) to plan.url else plan.searchFallback?.let { view(it, service) to it }
        is HandoffPlan.Search -> if (!fallback) view(plan.url, service) to plan.url else null
        HandoffPlan.Launch -> if (!fallback) context.packageManager.getLaunchIntentForPackage(service.packageName)?.let { it to "launch" } else null
    }

    private fun view(url: String, service: StreamingService): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(service.packageName)

    // ---- Wikidata (T17-15, BS-4): a service's own title id by the TMDB id; keyless; one query at a time; cached 7 days.

    private val wikidataLock = Any()

    /** Uptime (ms) before which Wikidata asked not to be called again (HTTP 429 with Retry-After). */
    @Volatile private var wikidataQuietUntil = 0L

    /**
     * Wikidata's ids for the title, asked once per title and kept with the title page's cache entry. A service with no
     * id property is not asked about. Writes the `id <found|none> (wikidata)` line for [service].
     */
    private fun wikidataIds(context: Context, service: StreamingService, title: TitleRef): Map<String, String> {
        if (service.ids.isEmpty()) return emptyMap()
        val app = context.applicationContext
        val cache = CatalogueCache(File(app.filesDir, Catalogue.DIR))
        val key = "${title.type.path}-${title.tmdbId}"
        val ids = synchronized(wikidataLock) {
            val entry = cache.read("wikidata", key)
            val cached = entry?.let { ServicesTable.parseWikidata(it.body) }
            val now = android.os.SystemClock.elapsedRealtime()
            if (cached != null && !CatalogueRules.isStale(entry.ageMs)) return@synchronized cached
            if (!VideoHttp.online(app) || now < wikidataQuietUntil) return@synchronized cached.orEmpty()
            val base = CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.WIKIDATA), FixedEndpoints.WIKIDATA_QUERY)
            val query = ServicesTable.sparql(title.type, title.tmdbId, BuildConfig.DEBUG)
            val headers = mapOf(
                "Accept" to "application/sparql-results+json",
                // Wikidata's rule: a descriptive User-Agent. No Authorization header: the TMDB token is TMDB's alone.
                "User-Agent" to "Tessera/${BuildConfig.VERSION_NAME} (personal launcher; StreamingHandoff)",
            )
            when (val outcome = VideoHttp.get(base + "sparql?format=json&query=" + URLEncoder.encode(query, "UTF-8"), headers)) {
                is FetchOutcome.Answer -> {
                    val fresh = ServicesTable.parseWikidata(outcome.body)
                    if (fresh != null) cache.write("wikidata", key, outcome.body)
                    fresh ?: cached.orEmpty()
                }
                is FetchOutcome.Status -> {
                    if (outcome.code == 429) {
                        // Wikidata's rule: a 429 says when to come back. With no Retry-After, a minute.
                        val wait = (outcome.retryAfterSeconds ?: 60L).coerceIn(1, 3600)
                        wikidataQuietUntil = now + wait * 1000
                        Diagnostics.add("video", "wikidata: busy, not asked again for $wait s")
                    } else {
                        Diagnostics.add("video", "wikidata: error ${outcome.code}")
                    }
                    cached.orEmpty()
                }
                FetchOutcome.NoConnection -> {
                    Diagnostics.add("video", "wikidata: error connect")
                    cached.orEmpty()
                }
                FetchOutcome.TooLarge -> {
                    Diagnostics.add("video", "wikidata: error too large")
                    cached.orEmpty()
                }
            }
        }
        Diagnostics.add("video", ServicesTable.idLine(service, title, ServicesTable.titleUrl(service, title.type, ids) != null))
        return ids
    }
}
