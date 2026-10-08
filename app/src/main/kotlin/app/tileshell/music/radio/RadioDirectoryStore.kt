package app.tileshell.music.radio

import android.content.Context
import app.tileshell.diag.Diagnostics
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.VideoHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The station directory on the phone (phase 20 build task 5; Decisions "the station directory", Q-20-1): the WHOLE
 * directory, fetched in pages of 2,000 by popularity and kept in `radio_directory_v1.json`, with the genres and the
 * countries. Every rule is [RadioDirectory]'s, [RadioMirrors]' or [RadioFavourites]'; this file fetches, keeps and
 * publishes, all of it off the main thread.
 *
 *  - The file is one JSON object a line. A refresh appends each page to a temp file and renames it only when the last
 *    page is in, so a failed or half-finished refresh never replaces a whole cache.
 *  - The FIRST fetch (nothing cached) publishes each page as it lands, so the pivot lists the most popular stations
 *    at once; a refresh over a cache keeps the old list on screen until the new one is whole.
 *  - Fetched by itself only when the pivot is opened with no cache or one older than 7 days, on an unmetered network;
 *    on demand on any network ([RadioDirectory.refreshDue]).
 *  - Every request goes through `VideoHttp.get` with the speaking User-Agent and the 4 MiB cap; the server is a
 *    mirror from the DNS lookup ([RadioMirrors]), or the debug-only override, which bypasses the lookup.
 */
class RadioDirectoryStore private constructor(context: Context) {
    private val app = context.applicationContext

    enum class Status {
        /** Nothing asked yet. */
        IDLE,

        /** The cache file is being read. */
        LOADING,

        /** A fetch is running; [State.index] holds the cache, or — on the first fetch — the pages in so far. */
        FETCHING,

        /** The cache is listed and no fetch is due. */
        READY,

        /** No network, or the fetch failed: the cache is listed under [RadioDirectory.OFFLINE_NOTE]. */
        OFFLINE,

        /** No network (or no answer) and nothing cached: [RadioDirectory.EMPTY_OFFLINE]. */
        EMPTY_OFFLINE,

        /** Nothing cached and the network is metered: nothing is fetched until the user asks ([refresh]). */
        EMPTY_METERED,
    }

    /**
     * @param cacheWall when the cache was fetched (wall clock), 0 with none
     */
    data class State(
        val index: RadioIndex = RadioIndex.EMPTY,
        val tags: List<RadioTag> = emptyList(),
        val countries: List<RadioCountry> = emptyList(),
        val status: Status = Status.IDLE,
        val cacheWall: Long = 0L,
    )

    private val file = File(app.filesDir, RadioDirectory.FILE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow(State())
    private val busy = AtomicBoolean(false)

    @Volatile private var loaded = false

    /** The base that last answered: the click call's, so a click needs no lookup of its own. */
    @Volatile private var lastBase: String? = null

    /** The directory as it is now. The first value is empty and [Status.IDLE]; [open] fills it. */
    val directory: StateFlow<State> = state.asStateFlow()

    /** The radio pivot was opened: the cache is read (once a process), then fetched if it is due by itself. */
    fun open() = work(onDemand = false)

    /** The user asked for a refresh: fetched on any network. */
    fun refresh() = work(onDemand = true)

    /** Tess's and the session's read: the cache loaded now if it was not — BLOCKS on the file, call it off the main thread. */
    fun loadedIndex(): RadioIndex {
        ensureLoaded()
        return state.value.index
    }

    /**
     * The directory's click counter (`GET /json/url/<uuid>`): called on each USER-INITIATED start — a tap, next /
     * previous, Tess — and never on a reconnect. Nothing is read from the answer and a failure says nothing.
     */
    fun click(uuid: String) {
        if (!Station.isUuid(uuid)) return
        scope.launch {
            if (!VideoHttp.online(app)) return@launch
            val base = RadioNet.qaBase(app) ?: lastBase ?: RadioMirrors.bases(null, mirrorNames(), System.nanoTime()).first()
            VideoHttp.get(base + RadioDirectory.request(RadioDirectory.What.Click(uuid)), RadioNet.headers(), CLICK_MAX_BYTES)
        }
    }

    private fun work(onDemand: Boolean) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                ensureLoaded()
                val cached = state.value.index.size > 0
                val age = if (cached) System.currentTimeMillis() - state.value.cacheWall else null
                when {
                    !VideoHttp.online(app) -> failed()
                    RadioDirectory.refreshDue(age, RadioNet.metered(app), onDemand) -> fetch(firstEver = !cached)
                    else -> state.value = state.value.copy(status = if (cached) Status.READY else Status.EMPTY_METERED)
                }
            } finally {
                busy.set(false)
            }
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        state.value = state.value.copy(status = Status.LOADING)
        val stations = ArrayList<Station>()
        val tags = ArrayList<RadioTag>()
        val countries = ArrayList<RadioCountry>()
        if (file.isFile && file.length() <= RadioDirectory.MAX_CACHE_BYTES) {
            runCatching {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) when (val row = RadioDirectory.cacheRow(line)) {
                        is RadioDirectory.CacheRow.OfStation -> stations += row.station
                        is RadioDirectory.CacheRow.OfTag -> tags += row.tag
                        is RadioDirectory.CacheRow.OfCountry -> countries += row.country
                        null -> Unit
                    }
                }
            }
        }
        state.value = State(RadioIndex.of(stations), tags, countries, if (stations.isEmpty()) Status.IDLE else Status.READY, if (stations.isEmpty()) 0L else file.lastModified())
        loaded = true
    }

    /** No network or no answer: the cache stays and the line says how old it is; with none, the offline empty state. */
    private fun failed() {
        val s = state.value
        if (s.cacheWall > 0L && s.index.size > 0) {
            Diagnostics.add(StreamLine.TAG, RadioLine.offline(Instant.ofEpochMilli(s.cacheWall).atZone(ZoneId.systemDefault()).toLocalDate().toString()))
            state.value = s.copy(status = Status.OFFLINE)
        } else {
            Diagnostics.add(StreamLine.TAG, RadioLine.NO_CACHE_OFFLINE)
            state.value = State(status = Status.EMPTY_OFFLINE)
        }
    }

    private enum class Fetch { DONE, TOO_LARGE, NO_ANSWER, FAILED }

    private fun fetch(firstEver: Boolean) {
        val before = state.value
        state.value = before.copy(status = Status.FETCHING)
        val qa = RadioNet.qaBase(app)
        val names = if (RadioMirrors.needsLookup(qa)) mirrorNames() else emptyList()
        var result = Fetch.NO_ANSWER
        for (base in RadioMirrors.bases(qa, names, System.nanoTime())) {
            result = fetchFrom(base, before, firstEver)
            // Only a mirror that gave nothing at all is passed over for the next one.
            if (result != Fetch.NO_ANSWER) break
        }
        File(file.parentFile, file.name + TEMP).delete()
        when (result) {
            Fetch.DONE -> Unit
            Fetch.TOO_LARGE -> {
                Diagnostics.add(StreamLine.TAG, RadioLine.TOO_LARGE)
                // The cache is kept: what was on screen before the fetch is what stays.
                state.value = before.copy(status = if (before.index.size > 0) Status.READY else Status.EMPTY_OFFLINE)
            }
            Fetch.NO_ANSWER, Fetch.FAILED -> {
                state.value = before
                failed()
            }
        }
    }

    private fun fetchFrom(base: String, before: State, firstEver: Boolean): Fetch {
        val headers = RadioNet.headers()
        val temp = File(file.parentFile, file.name + TEMP)
        val all = ArrayList<Station>()
        val seen = HashSet<String>()
        var tags = before.tags
        var countries = before.countries
        var written = 0L
        try {
            temp.bufferedWriter(Charsets.UTF_8).use { out ->
                fun put(line: String, bytes: Long = size(line)) {
                    out.write(line)
                    out.write("\n")
                    written += bytes
                }
                var offset: Int? = 0
                while (offset != null) {
                    val at = offset
                    val parsed = when (val outcome = VideoHttp.get(base + RadioDirectory.request(RadioDirectory.What.Page(at)), headers, RadioDirectory.MAX_RESPONSE_BYTES)) {
                        is FetchOutcome.Answer -> RadioDirectory.parse(outcome.body.toByteArray(Charsets.UTF_8))
                        FetchOutcome.TooLarge -> RadioDirectory.Parsed.TooLarge
                        else -> RadioDirectory.Parsed.Malformed
                    }
                    val rows = when (parsed) {
                        RadioDirectory.Parsed.TooLarge -> return Fetch.TOO_LARGE
                        RadioDirectory.Parsed.Malformed -> return if (at == 0) Fetch.NO_ANSWER else Fetch.FAILED
                        is RadioDirectory.Parsed.Rows -> parsed
                    }
                    var full = false
                    for (station in rows.stations) {
                        // Click counts move while the pages are asked, so a station can turn up on two of them.
                        if (station.uuid in seen) continue
                        val line = RadioDirectory.cacheLine(station)
                        val bytes = size(line)
                        // The file's cap: the most popular stations are in, and the fetch ends here.
                        if (!RadioDirectory.fits(written, bytes)) { full = true; break }
                        seen += station.uuid
                        put(line, bytes)
                        all += station
                    }
                    lastBase = base
                    if (firstEver) state.value = State(RadioIndex.of(all), tags, countries, Status.FETCHING, 0L)
                    offset = if (full) null else RadioDirectory.nextOffset(at, rows.rowCount)
                }
                // The genres and the countries: a failed answer keeps the ones the cache had.
                answer(base, RadioDirectory.What.Tags, headers)?.let { RadioDirectory.parseTags(it) }?.let { tags = it }
                answer(base, RadioDirectory.What.Countries, headers)?.let { RadioDirectory.parseCountries(it) }?.let { countries = it }
                tags.forEach { put(RadioDirectory.cacheLine(it)) }
                countries.forEach { put(RadioDirectory.cacheLine(it)) }
            }
            if (all.isEmpty()) return Fetch.NO_ANSWER
            if (!temp.renameTo(file)) return Fetch.FAILED
        } catch (e: java.io.IOException) {
            return Fetch.FAILED
        }
        state.value = State(RadioIndex.of(all), tags, countries, Status.READY, file.lastModified())
        Diagnostics.add(StreamLine.TAG, RadioLine.fetched(all.size))
        refreshFavourites(base, headers)
        return Fetch.DONE
    }

    /** A line's length in the file: its UTF-8 bytes and the newline. */
    private fun size(line: String): Long = line.toByteArray(Charsets.UTF_8).size + 1L

    private fun answer(base: String, what: RadioDirectory.What, headers: Map<String, String>): ByteArray? =
        (VideoHttp.get(base + RadioDirectory.request(what), headers, RadioDirectory.MAX_RESPONSE_BYTES) as? FetchOutcome.Answer)?.body?.toByteArray(Charsets.UTF_8)

    /** The favourites' rows refreshed by uuid — a favourite the directory now hides as broken is still answered for. */
    private fun refreshFavourites(base: String, headers: Map<String, String>) {
        val store = RadioFavouritesStore.get(app)
        for (uuids in store.stations().map { it.uuid }.chunked(RadioDirectory.UUIDS_PER_CALL)) {
            val rows = answer(base, RadioDirectory.What.ByUuid(uuids), headers)?.let { RadioDirectory.parse(it) } as? RadioDirectory.Parsed.Rows ?: continue
            store.refresh(rows.stations)
        }
    }

    /** The mirrors' names: each address of the round-robin name, by its canonical name. Empty when the lookup fails. */
    private fun mirrorNames(): List<String> =
        runCatching { InetAddress.getAllByName(RadioMirrors.ALL).map { it.canonicalHostName } }.getOrDefault(emptyList())

    companion object {
        private const val TEMP = ".tmp"

        /** The click call's answer is a few hundred bytes and is not read. */
        private const val CLICK_MAX_BYTES = 64L * 1024

        @Volatile
        private var instance: RadioDirectoryStore? = null

        fun get(context: Context): RadioDirectoryStore =
            instance ?: synchronized(this) {
                instance ?: RadioDirectoryStore(context).also { instance = it }
            }
    }
}
