package app.tileshell.net

import android.security.NetworkSecurityPolicy
import app.tileshell.diag.Diagnostics

/**
 * Every host the shell itself calls (phase 17 build task 17; Decisions C-16 (1)). Each is https-only by the platform:
 * `res/xml/network_security_config.xml` denies cleartext to every host here and to its subdomains, while the base
 * config permits it for hosts nobody can list — a radio station's stream, a home media server, an `http://` video
 * another app hands the player (the owner's ruling Q-D (a): plain http for media only).
 *
 * A host is added HERE and in both copies of the config (main and the debug source set's); `FixedEndpointsTest` fails
 * a build where the three disagree or where a URL below is not `https://`.
 */
object FixedEndpoints {
    /** The base URL of each fixed endpoint (weather and the place search, phases 01 and 03, hold their own literals). */
    const val OPEN_METEO = "https://api.open-meteo.com/"
    const val NOMINATIM = "https://nominatim.openstreetmap.org/"
    const val TMDB_API = "https://api.themoviedb.org/"
    const val TMDB_IMAGES = "https://image.tmdb.org/"
    const val WIKIDATA_QUERY = "https://query.wikidata.org/"
    const val RADIO_BROWSER = "https://api.radio-browser.info/"
    const val MUSICBRAINZ = "https://musicbrainz.org/"
    const val COVER_ART_ARCHIVE = "https://coverartarchive.org/"

    /** Where coverartarchive.org redirects its images (`archive.org`, then a `*.archive.org` download host). */
    const val COVER_ART_REDIRECT = "https://archive.org/"

    val URLS: List<String> = listOf(
        OPEN_METEO, NOMINATIM, TMDB_API, TMDB_IMAGES, WIKIDATA_QUERY, RADIO_BROWSER, MUSICBRAINZ, COVER_ART_ARCHIVE,
        COVER_ART_REDIRECT,
    )

    /** The hosts of [URLS], in order: the config's `<domain>` list. */
    val HOSTS: List<String> = URLS.map(::hostOf)

    /** The host of an absolute URL ("https://host/…" → "host"); the text itself when it has no scheme. */
    fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':')

    /**
     * The process-start lines the QA rows read (E13): what the platform's policy says for each fixed host. Every one
     * must read `false`; a `true` means the installed config does not cover that host.
     */
    fun logPolicy() {
        val policy = NetworkSecurityPolicy.getInstance()
        for (host in HOSTS) {
            Diagnostics.add("net", "cleartext permitted for $host: ${policy.isCleartextTrafficPermitted(host)}")
        }
    }
}
