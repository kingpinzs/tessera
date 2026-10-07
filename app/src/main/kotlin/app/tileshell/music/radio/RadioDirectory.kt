package app.tileshell.music.radio

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonLong
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.util.Locale

/** A genre of the directory and how many stations carry it. */
data class RadioTag(val name: String, val stationCount: Int)

/** A country of the directory: its name, its ISO 3166-1 alpha-2 code and how many stations it has. */
data class RadioCountry(val name: String, val code: String, val stationCount: Int)

/**
 * The radio-browser.info directory by rule (phase 20 Decisions "the station directory", Q-20-1; r3 D5): what is
 * asked, how an answer is read, how the cache file is written, when it is refreshed, and how it is searched. The
 * WHOLE directory is kept on the phone, so search, genre and country run over the cache by code — there is no online
 * search call, and [What] has no form for one. Pure (`RadioDirectoryTest`); [RadioDirectoryStore] is the thin part
 * that fetches and keeps.
 */
object RadioDirectory {
    /** Stations asked for at a time, most popular first. */
    const val PAGE = 2_000

    /** The most pages one refresh asks for: 80,000 stations, half as many again as the directory has. */
    const val MAX_PAGES = 40
    const val TAGS = 500

    /** Any one answer over this is not used at all (`radio: directory too large`); a 2,000-row page is about 2.4 MB. */
    const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024

    /** The cache file is never longer than this; the slim directory is about 20 MB. */
    const val MAX_CACHE_BYTES = 32L * 1024 * 1024

    /** The cache is refreshed by itself when older than this — on an unmetered network only. */
    const val REFRESH_MS = 7L * 24 * 60 * 60 * 1000

    /** Results are listed this many at a time. */
    const val LIST = 100

    /** The uuids one refresh-by-uuid call names, so the address stays short. */
    const val UUIDS_PER_CALL = 50

    const val FILE_NAME = "radio_directory_v1.json"

    /** The pivot's lines (Q-20-1; Edge cases): the cache shown with no network, and nothing to show yet. */
    const val OFFLINE_NOTE = "No connection — stations need the internet"
    const val EMPTY_OFFLINE = "No connection yet — stations will appear when there is one"

    /** Everything the directory is ever asked. */
    sealed interface What {
        /** The stations from [offset] on, most clicked first, the broken ones left out. */
        data class Page(val offset: Int) : What
        data object Tags : What
        data object Countries : What

        /** The favourites' fresh rows (at most [UUIDS_PER_CALL] a call). */
        data class ByUuid(val uuids: List<String>) : What

        /** The click call: the directory's counter, on each user-initiated start and never on a reconnect. */
        data class Click(val uuid: String) : What
    }

    /** The request's path and query, to be put after a base that ends in `/`. A uuid that is not one is left out. */
    fun request(what: What): String = when (what) {
        is What.Page -> "json/stations?order=clickcount&reverse=true&hidebroken=true&limit=$PAGE&offset=${what.offset.coerceAtLeast(0)}"
        What.Tags -> "json/tags?order=stationcount&reverse=true&limit=$TAGS"
        What.Countries -> "json/countries"
        is What.ByUuid -> "json/stations/byuuid?uuids=" + what.uuids.filter { Station.isUuid(it) }.take(UUIDS_PER_CALL).joinToString(",")
        is What.Click -> "json/url/" + what.uuid.takeIf { Station.isUuid(it) }.orEmpty()
    }

    /**
     * The next page's offset after a page at [offset] that held [rows] rows, or null when the fetch is over: a short
     * page is the last one, and no refresh asks for more than [MAX_PAGES].
     */
    fun nextOffset(offset: Int, rows: Int): Int? {
        if (rows < PAGE) return null
        val next = offset + PAGE
        return if (next / PAGE >= MAX_PAGES) null else next
    }

    sealed interface Parsed {
        data class Rows(val stations: List<Station>, /** The rows the answer held, kept or not: what paging counts. */ val rowCount: Int) : Parsed

        /** Over the cap: nothing of it was read, and the cache is kept. */
        data object TooLarge : Parsed
        data object Malformed : Parsed
    }

    /**
     * A stations answer → the slim rows. Dropped: a row with no usable uuid or name, and a row whose codec is a
     * video's. An empty `url_resolved` is kept — [StationUrl.playable] falls back to `url` at the tap.
     */
    fun parse(bytes: ByteArray, cap: Long = MAX_RESPONSE_BYTES): Parsed {
        if (bytes.size > cap) return Parsed.TooLarge
        val rows = MiniJson.parseOrNull(String(bytes, Charsets.UTF_8)) as? List<*> ?: return Parsed.Malformed
        return Parsed.Rows(rows.mapNotNull { Station.fromJson(it.jsonObject()) }.filterNot { isVideo(it.codec) }, rows.size)
    }

    private val VIDEO = setOf("H.264", "H264", "H.265", "H265", "HEVC", "AVC", "VP8", "VP9", "AV1", "THEORA", "MPEG4", "MP4V", "FLV", "WMV")

    /** The directory lists a few television streams: their codec names a video codec (`AAC,H.264`). */
    fun isVideo(codec: String): Boolean = codec.uppercase(Locale.ROOT).split(',', '/', ' ').any { it in VIDEO }

    /** A tags answer → the genres, in the answer's order (most stations first). */
    fun parseTags(bytes: ByteArray, cap: Long = MAX_RESPONSE_BYTES): List<RadioTag>? {
        if (bytes.size > cap) return null
        val rows = MiniJson.parseOrNull(String(bytes, Charsets.UTF_8)) as? List<*> ?: return null
        return rows.mapNotNull { tag(it.jsonObject(), "name") }.take(TAGS)
    }

    /** A countries answer → the countries that have a code, by name. */
    fun parseCountries(bytes: ByteArray, cap: Long = MAX_RESPONSE_BYTES): List<RadioCountry>? {
        if (bytes.size > cap) return null
        val rows = MiniJson.parseOrNull(String(bytes, Charsets.UTF_8)) as? List<*> ?: return null
        return rows.mapNotNull { country(it.jsonObject(), "name", "iso_3166_1") }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun count(row: Map<String, Any?>): Int = (row.jsonLong("stationcount") ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun tag(row: Map<String, Any?>?, nameKey: String): RadioTag? {
        val name = RadioText.key(row?.jsonString(nameKey)).take(RadioText.NAME_MAX).ifEmpty { return null }
        return RadioTag(name, count(row!!))
    }

    private fun country(row: Map<String, Any?>?, nameKey: String, codeKey: String): RadioCountry? {
        val code = row?.jsonString(codeKey).orEmpty().uppercase(Locale.ROOT)
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
        val name = RadioText.shown(row?.jsonString(nameKey), RadioText.NAME_MAX).ifEmpty { return null }
        return RadioCountry(name, code, count(row!!))
    }

    // ---- the cache file: one JSON object a line, so a page is appended as it lands and the file is read a row at a time

    sealed interface CacheRow {
        data class OfStation(val station: Station) : CacheRow
        data class OfTag(val tag: RadioTag) : CacheRow
        data class OfCountry(val country: RadioCountry) : CacheRow
    }

    fun cacheLine(station: Station): String = MiniJson.write(station.toJson())

    fun cacheLine(tag: RadioTag): String = MiniJson.write(linkedMapOf("tag" to tag.name, "stationcount" to tag.stationCount.toLong()))

    fun cacheLine(country: RadioCountry): String =
        MiniJson.write(linkedMapOf("country" to country.name, "code" to country.code, "stationcount" to country.stationCount.toLong()))

    /** One line of the cache file → its row; null for a line that is none (it is skipped, the rest of the file stands). */
    fun cacheRow(line: String): CacheRow? {
        val row = MiniJson.parseOrNull(line).jsonObject() ?: return null
        return when {
            row.containsKey("stationuuid") -> Station.fromJson(row)?.let { CacheRow.OfStation(it) }
            row.containsKey("tag") -> tag(row, "tag")?.let { CacheRow.OfTag(it) }
            row.containsKey("country") -> country(row, "country", "code")?.let { CacheRow.OfCountry(it) }
            else -> null
        }
    }

    /** Room kept at the end of the cache file for the genres and the countries (about 40 KB of lines). */
    const val CACHE_TAIL_BYTES = 256L * 1024

    /** Whether a station line of [add] bytes still fits a cache file that holds [soFar], the tail's room kept. */
    fun fits(soFar: Long, add: Long): Boolean = soFar + add <= MAX_CACHE_BYTES - CACHE_TAIL_BYTES

    /**
     * Whether the directory is fetched now. On demand: always, on any network. By itself (the radio pivot opened):
     * only when there is no cache or it is older than [REFRESH_MS], and only on an unmetered network — a full refresh
     * is tens of megabytes on the wire, never spent on mobile data unasked.
     */
    fun refreshDue(cacheAgeMs: Long?, metered: Boolean, onDemand: Boolean): Boolean =
        onDemand || (!metered && (cacheAgeMs == null || cacheAgeMs < 0 || cacheAgeMs > REFRESH_MS))

    // ---- search, genre, country: over the whole cache plus the favourites, favourites first, then by popularity

    /** Stations whose name contains [query], [LIST] from [offset]. A blank query finds nothing. */
    fun search(query: String, index: RadioIndex, favourites: List<Station>, offset: Int = 0): List<Station> {
        val q = RadioText.key(query)
        if (q.isEmpty()) return emptyList()
        return listed(favourites.filter { RadioText.key(it.name).contains(q) }, index.nameContains(q), offset)
    }

    /** Stations of the genre [tag] (a whole tag, not a part of one). */
    fun byTag(tag: String, index: RadioIndex, favourites: List<Station>, offset: Int = 0): List<Station> {
        val t = RadioText.key(tag)
        if (t.isEmpty()) return emptyList()
        return listed(favourites.filter { t in it.tagKeys() }, index.ofTag(t), offset)
    }

    /** Stations of the country with the ISO code [code]. */
    fun byCountry(code: String, index: RadioIndex, favourites: List<Station>, offset: Int = 0): List<Station> {
        val c = code.trim().uppercase(Locale.ROOT)
        if (c.isEmpty()) return emptyList()
        return listed(favourites.filter { it.countryCode == c }, index.ofCountry(c), offset)
    }

    /** The favourites that matched, then the directory's matches (already by popularity) — each station once. */
    private fun listed(favourites: List<Station>, rest: Sequence<Station>, offset: Int): List<Station> {
        val first = favourites.distinctBy { it.uuid }
        val seen = first.mapTo(HashSet()) { it.uuid }
        return (first.asSequence() + rest.filter { it.uuid !in seen }).drop(offset.coerceAtLeast(0)).take(LIST).toList()
    }
}

/**
 * The cached directory in memory (53,000 rows; built off the main thread): the stations by popularity, each name's
 * comparison key beside it, and the row numbers of every genre and country — so a search is one pass over an array of
 * strings and a genre or a country is a lookup. Pure (`RadioDirectoryTest`).
 */
class RadioIndex private constructor(
    /** Every station, most popular first. */
    val stations: List<Station>,
    private val names: Array<String>,
    private val tags: Map<String, IntArray>,
    private val countries: Map<String, IntArray>,
) {
    val size: Int get() = stations.size

    fun nameContains(key: String): Sequence<Station> = stations.indices.asSequence().filter { names[it].contains(key) }.map { stations[it] }

    /** The most popular station whose name is exactly [key] (a [RadioText.key]), or null. */
    fun exactName(key: String): Station? = names.indexOfFirst { it == key }.takeIf { it >= 0 }?.let { stations[it] }

    fun ofTag(key: String): Sequence<Station> = tags[key]?.asSequence()?.map { stations[it] } ?: emptySequence()

    fun ofCountry(code: String): Sequence<Station> = countries[code]?.asSequence()?.map { stations[it] } ?: emptySequence()

    fun byUuid(uuid: String): Station? = stations.firstOrNull { it.uuid == uuid }

    companion object {
        val EMPTY: RadioIndex = of(emptyList())

        /** The index of [rows]: each station once (the first row of a uuid wins), ordered by click count, ties as they came. */
        fun of(rows: List<Station>): RadioIndex {
            val stations = rows.distinctBy { it.uuid }.sortedByDescending { it.clickCount }
            val names = Array(stations.size) { RadioText.key(stations[it].name) }
            val tags = HashMap<String, MutableList<Int>>()
            val countries = HashMap<String, MutableList<Int>>()
            stations.forEachIndexed { i, s ->
                for (t in s.tagKeys().distinct()) tags.getOrPut(t) { ArrayList() } += i
                if (s.countryCode.isNotEmpty()) countries.getOrPut(s.countryCode) { ArrayList() } += i
            }
            return RadioIndex(stations, names, tags.mapValues { it.value.toIntArray() }, countries.mapValues { it.value.toIntArray() })
        }
    }
}
