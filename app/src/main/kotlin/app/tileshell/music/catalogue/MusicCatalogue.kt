package app.tileshell.music.catalogue

import app.tileshell.net.FixedEndpoints
import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonLong
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.catalogue.FetchOutcome
import java.net.URLEncoder

/**
 * One recording of a catalogue search.
 * @param id the recording's MBID — the row's tag (`catalogue_row:<id>`)
 * @param artist the credited artists as MusicBrainz joins them ("A & B")
 * @param release the first release's title ("" when the recording is on none)
 * @param releaseId the first release's MBID — the artwork's key; null when there is none, and the row draws the placeholder
 * @param year the first release's year, or ""
 */
data class CatalogueTrack(val id: String, val title: String, val artist: String, val release: String, val releaseId: String?, val year: String, val lengthMs: Long?)

/** What the catalogue page says above its rows when a search did not answer. */
enum class MusicCatalogueNotice(val text: String?) {
    NONE(null),
    OFFLINE("You're offline — the catalogue needs a connection"),
    NOT_ANSWERING("The catalogue isn't answering"),
    BUSY("The catalogue is busy, try again in a minute"),
}

/** One submitted search's answer: its rows (empty on a failure) and what the page says. */
class MusicCatalogueResult(val query: String, val tracks: List<CatalogueTrack>, val notice: MusicCatalogueNotice)

/**
 * The music catalogue's rules that need no network (phase 20 build task 8; r3 D17): MusicBrainz's `recording` search and
 * the Cover Art Archive's address for a release's front image. Pure — the fetcher ([CatalogueSession]) is the thin part.
 */
object MusicCatalogue {
    /** MusicBrainz's web service, and the Cover Art Archive: what the two debug-only QA prefs stand in for. */
    const val API = FixedEndpoints.MUSICBRAINZ + "ws/2/"
    const val COVERS = FixedEndpoints.COVER_ART_ARCHIVE

    const val LIMIT = 25

    /** The archive's 250-pixel thumbnail of a release's front image. */
    const val COVER_SIZE = "front-250"

    /** An MBID is a UUID; it becomes part of an address and of a test tag. */
    private val MBID = Regex("[0-9A-Fa-f]{8}(-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}")

    /** `<base>recording?query=<q>&fmt=json&limit=25`: the typed text as MusicBrainz's query, encoded. */
    fun searchUrl(base: String, query: String): String =
        base + "recording?query=" + URLEncoder.encode(query.trim(), "UTF-8") + "&fmt=json&limit=" + LIMIT

    /**
     * A search's answer → its recordings, in MusicBrainz's order (by score). Null when the body is not a recording
     * search's answer; a row with no MBID or no title is dropped. Nothing here throws on a missing or mistyped field.
     */
    fun parse(body: String): List<CatalogueTrack>? {
        val o = MiniJson.parseOrNull(body).jsonObject() ?: return null
        if (!o.containsKey("recordings")) return null
        return o["recordings"].jsonArray().mapNotNull { row ->
            val r = row.jsonObject() ?: return@mapNotNull null
            val id = r.jsonString("id")?.takeIf(MBID::matches) ?: return@mapNotNull null
            val title = r.jsonString("title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val release = r["releases"].jsonArray().firstOrNull().jsonObject()
            CatalogueTrack(
                id, title, artist(r["artist-credit"].jsonArray()),
                release?.jsonString("title").orEmpty().trim(),
                release?.jsonString("id")?.takeIf(MBID::matches),
                release?.jsonString("date").orEmpty().take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty(),
                r.jsonLong("length")?.takeIf { it > 0 },
            )
        }
    }

    /** The credits in order, each followed by its own join phrase (" & ", " feat. "), or ", " where it gives none. */
    private fun artist(credits: List<Any?>): String {
        val named = credits.mapNotNull { c -> c.jsonObject()?.let { o -> o.jsonString("name")?.takeIf(String::isNotBlank)?.let { it to o.jsonString("joinphrase") } } }
        return buildString {
            named.forEachIndexed { i, (name, join) ->
                append(name)
                if (i < named.lastIndex) append(join?.takeIf { it.isNotEmpty() } ?: ", ")
            }
        }.trim()
    }

    /** `<base>release/<mbid>/front-250` for the row's first release; null — the placeholder — when it has none. */
    fun coverUrl(base: String, releaseId: String?): String? =
        releaseId?.takeIf(MBID::matches)?.let { base + "release/" + it + "/" + COVER_SIZE }

    /** The session's key for a search: what was typed, trimmed, in lower case. */
    fun key(query: String): String = query.trim().lowercase()

    // ---- the lines: `[music] catalogue "<q>": <n> | offline | error <code> | error connect`

    const val STATUS_OFFLINE = "offline"

    /** The line's text after the tag: the query and the status only, the query cleaned as phase 17 cleans its own. */
    fun line(query: String, status: String): String = CatalogueRules.line(query, status)

    /** `error <code>` for an HTTP status (a 503 is `error 503`), `error connect` when no connection was made. */
    fun errorStatus(outcome: FetchOutcome): String = CatalogueRules.errorStatus(outcome)

    /** What the page says for a search that failed: MusicBrainz declines with 503 when asked too fast. */
    fun notice(outcome: FetchOutcome): MusicCatalogueNotice = when {
        outcome is FetchOutcome.Status && (outcome.code == 503 || outcome.code == 429) -> MusicCatalogueNotice.BUSY
        else -> MusicCatalogueNotice.NOT_ANSWERING
    }
}
