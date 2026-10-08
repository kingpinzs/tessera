package app.tileshell.music

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tileshell.applist.AppListMetrics
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.music.radio.RadioDirectory
import app.tileshell.music.radio.RadioDirectoryStore
import app.tileshell.music.radio.Station
import app.tileshell.music.radio.StationLogos
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The radio pivot and the pages it opens onto (phase 20 build task 6; Y2, a P4 design — Groove's Radio held artist
 * stations, not a directory, and no measurement of one exists).
 *
 * Every row is the app list's, as the four built pivots' are ([MusicMetrics]); what is listed and in what order is
 * [MusicCollection.radio]'s. The directory is the WHOLE directory, kept on the phone (Q-20-1), so search, genre and
 * country are all read off the cache by [RadioDirectory] — nothing here asks the network for a list. Results come most
 * popular first and are listed 100 at a time: the next hundred when the end of the list is reached.
 *
 * Logos (r3 D2): a row draws the placeholder. Only a favourite's row fetches its logo — the shell's own bounded fetch
 * ([StationLogos]), off the main thread — and the playing station's row shows the one the player already has. Browsing
 * the directory therefore reaches out to no station's host.
 *
 * Tags: `music_list:radio`, `radio_note`, `radio_group:<title>`, `radio_fav:<uuid>`, `radio_row:<uuid>` (each with
 * its `music_pri:` / `music_sub:` lines), the entry rows' own ([RadioEntry.tag], with `music_pri:<tag>`),
 * `radio_search_box`, `radio_empty`, `radio_genre:<name>`, `radio_country:<code>`; the pages are `radio_page:search`,
 * `radio_page:genres`, `radio_page:countries`, `radio_page:genre`, `radio_page:country`.
 */
@Composable
internal fun RadioPivot(
    active: Boolean,
    nav: OnlineNav,
    favourites: List<Station>,
    actions: StationActions,
) {
    val context = LocalContext.current
    val store = remember(context) { RadioDirectoryStore.get(context) }
    val directory by store.directory.collectAsState()
    // The pivot was OPENED — not merely composed beside playlists, which the pager does to every neighbour.
    LaunchedEffect(active) { if (active) store.open() }

    val stations = remember(directory.index, nav.radioShown) { directory.index.stations.take(nav.radioShown) }
    val page = remember(favourites, stations, directory.status) { MusicCollection.radio(RadioView(favourites, stations, directory.status)) }
    val list = rememberLazyListState()
    AtListEnd(list, page.items.size) {
        if (nav.radioShown < directory.index.size) nav.radioShown += RadioDirectory.LIST
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("music_list:radio")) {
        items(page.items.size, key = { page.items[it].key }) { i ->
            when (val item = page.items[i]) {
                is RadioNoteItem -> RadioNote(item.text)
                is RadioGroupHeader -> RadioGroupRow(item.title)
                is RadioFavouriteItem -> StationRow(item.station, "radio_fav:${item.station.uuid}", logo = true, actions)
                is RadioStationItem -> StationRow(item.station, "radio_row:${item.station.uuid}", logo = false, actions)
                is RadioEntryItem -> RadioEntryRow(item) {
                    when (item.entry) {
                        RadioEntry.SEARCH -> nav.open(RadioSearchPage)
                        RadioEntry.GENRE -> nav.open(RadioGenresPage)
                        RadioEntry.COUNTRY -> nav.open(RadioCountriesPage)
                        // On demand: fetched on any network, mobile data included — the person asked.
                        RadioEntry.REFRESH -> store.refresh()
                        RadioEntry.CATALOGUE -> nav.open(CatalogueSearchPage)
                        RadioEntry.SERVER -> nav.open(ServerPage)
                    }
                }
                else -> Unit
            }
        }
    }
}

/** Runs [onEnd] each time the list's last row comes into view — the "100 at a time" of every station list. */
@Composable
internal fun AtListEnd(list: LazyListState, count: Int, onEnd: suspend () -> Unit) {
    LaunchedEffect(list, count) {
        snapshotFlow {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 1
        }.collect { atEnd -> if (atEnd) onEnd() }
    }
}

/** A station row's second line: its first two genres and its country, as the directory gives them. */
internal fun stationFacts(station: Station): String =
    listOf(station.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", "), station.countryCode)
        .filter { it.isNotEmpty() }
        .joinToString(NowPlayingMetrics.META_SEPARATOR)

@Composable
internal fun StationRow(station: Station, tag: String, logo: Boolean, actions: StationActions) {
    val colors = LocalShellColors.current
    val playing = MusicPlayer.nowPlayingId == MusicLive.stationId(station.uuid)
    TwoLineRow(
        tag = tag,
        primary = station.name,
        // A tap that did not play says why on the row itself ("can't play this station").
        secondary = actions.refusalOf(station) ?: stationFacts(station),
        onTap = { actions.onTap(station) },
        primaryColor = if (playing) colors.accent else null,
        onHold = { y -> actions.onHold(station, y) },
    ) {
        AlbumArt(stationArt(station, fetch = logo, playing = playing))
    }
}

/**
 * The picture on a station's row, or null for the placeholder: a favourite's logo ([fetch]), fetched once and off the
 * main thread, or — for the row of the station that is playing — the logo the player holds. Never anything else,
 * and decoded only through [StationLogos.decode].
 */
@Composable
private fun stationArt(station: Station, fetch: Boolean, playing: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val live = if (playing) MusicPlayer.liveArt else null
    if (!fetch && live == null) return null
    val art by produceState(StationArtMemory.get(station), station.uuid, station.favicon, fetch, live) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                val bytes = live ?: runCatching { StationLogos.get(context).logo(station) }.getOrNull()
                StationLogos.decode(bytes)?.asImageBitmap()
            }?.also { StationArtMemory.put(station, it) }
        }
    }
    return art
}

/** The logos decoded in this run of the app, so a row scrolled back into view does not blink (the bytes are [StationLogos]'). */
private object StationArtMemory {
    private val map = LruCache<String, ImageBitmap>(48)
    private fun key(station: Station) = station.uuid + " " + station.favicon
    fun get(station: Station): ImageBitmap? = map.get(key(station))
    fun put(station: Station, art: ImageBitmap) { map.put(key(station), art) }
}

/** A group's heading: the letter header's place and face, with a word in it and no jump grid behind it. */
@Composable
private fun RadioGroupRow(title: String) {
    val colors = LocalShellColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(AppListMetrics.HEADER_BLOCK)
            .padding(start = AppListMetrics.HEADER_X)
            .testTag("radio_group:$title"),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(title, style = ShellType.title.copy(color = colors.accent), maxLines = 1)
    }
}

@Composable
private fun RadioNote(text: String) {
    BasicText(
        text,
        style = ShellType.body.copy(color = LocalShellColors.current.subtleText),
        modifier = Modifier
            .padding(start = MusicMetrics.SIDE, top = 12.dp, end = MusicMetrics.SIDE, bottom = 8.dp)
            .testTag("radio_note"),
    )
}

/** One of the pivot's own rows, in the "new playlist" row's form: a glyph where the art sits, and the words in the accent. */
@Composable
private fun RadioEntryRow(item: RadioEntryItem, onTap: () -> Unit) {
    val colors = LocalShellColors.current
    val glyph = when (item.entry) {
        RadioEntry.SEARCH -> Glyph.SEARCH
        RadioEntry.GENRE -> Glyph.LIST
        RadioEntry.COUNTRY -> Glyph.GLOBE
        RadioEntry.REFRESH -> Glyph.SYNC
        RadioEntry.CATALOGUE -> Glyph.MUSIC
        RadioEntry.SERVER -> Glyph.HOME
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(MusicMetrics.ROW)
            .clickable(onClick = onTap)
            .testTag(item.entry.tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(MusicMetrics.ART_X))
        Box(Modifier.size(MusicMetrics.ART), Alignment.Center) {
            BasicText(glyph, style = ShellType.body.copy(color = colors.accent, fontFamily = Brand.iconFont))
        }
        BasicText(
            item.label,
            style = ShellType.body.copy(color = colors.accent),
            maxLines = 1,
            modifier = Modifier
                .padding(start = MusicMetrics.TEXT_X - MusicMetrics.ART_X - MusicMetrics.ART)
                .testTag("music_pri:${item.entry.tag}"),
        )
    }
}

/**
 * A list of stations that is asked for 100 at a time: [fetch] is given how many are listed and answers the next
 * hundred ([RadioDirectory.search] / `.byTag` / `.byCountry`, run off the main thread — a search is a pass over 53,000
 * names). Each station is listed once whatever the pages hold.
 */
@Composable
private fun StationResults(key: Any, emptyText: String, actions: StationActions, fetch: (offset: Int) -> List<Station>) {
    var rows by remember(key) { mutableStateOf<List<Station>?>(null) }
    var more by remember(key) { mutableStateOf(true) }
    var loading by remember(key) { mutableStateOf(false) }
    suspend fun load() {
        if (loading || !more) return
        loading = true
        val listed = rows.orEmpty()
        val next = withContext(Dispatchers.Default) { fetch(listed.size) }
        rows = (listed + next).distinctBy { it.uuid }
        more = next.size >= RadioDirectory.LIST
        loading = false
    }
    LaunchedEffect(key) { load() }
    val found = rows ?: return
    if (found.isEmpty()) {
        BasicText(
            emptyText,
            style = ShellType.body.copy(color = LocalShellColors.current.subtleText),
            modifier = Modifier.padding(start = MusicMetrics.SIDE, top = 12.dp, end = MusicMetrics.SIDE).testTag("radio_empty"),
        )
        return
    }
    val list = rememberLazyListState()
    AtListEnd(list, found.size) { load() }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("radio_results")) {
        items(found.size, key = { found[it].uuid }) { i ->
            StationRow(found[i], "radio_row:${found[i].uuid}", logo = false, actions)
        }
    }
}

/**
 * Search (R3 C1's box, as phase 17's Browse has it): over the whole cached directory and the favourites, by name,
 * when the keyboard's Search key is pressed. Only what matches is listed, the favourites among it first.
 */
@Composable
internal fun RadioSearch(nav: OnlineNav, favourites: List<Station>, actions: StationActions) {
    val context = LocalContext.current
    val store = remember(context) { RadioDirectoryStore.get(context) }
    val directory by store.directory.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OnlineFrame(caption = "radio", title = "search", tag = "radio_page:search") {
        // The page takes the focus first, so clearing the box's focus does not hand it straight back (phase 17's Browse).
        Box(Modifier.fillMaxWidth().focusable()) {
            OutlinedField(
                value = nav.radioText, onValueChange = { nav.radioText = it }, tag = "radio_search_box",
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE, bottom = 8.dp).fillMaxWidth(),
                maxLength = 100, placeholder = "Search stations", imeAction = ImeAction.Search,
                onImeAction = {
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                    nav.radioQuery = nav.radioText.trim()
                },
            )
        }
        val query = nav.radioQuery
        if (query.isNotEmpty()) {
            val favs by rememberUpdatedState(favourites)
            StationResults(query to directory.index, "No stations for “$query”", actions) { offset ->
                RadioDirectory.search(query, directory.index, favs, offset)
            }
        }
    }
}

/** The directory's genres, most stations first (its own order). A tap lists the genre's stations. */
@Composable
internal fun RadioGenres(nav: OnlineNav) {
    val context = LocalContext.current
    val directory by remember(context) { RadioDirectoryStore.get(context) }.directory.collectAsState()
    val colors = LocalShellColors.current
    OnlineFrame(caption = "radio", title = "by genre", tag = "radio_page:genres") {
        val tags = directory.tags
        if (tags.isEmpty()) {
            BasicText(
                "No genres yet — refresh the stations.",
                style = ShellType.body.copy(color = colors.subtleText),
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE).testTag("radio_empty"),
            )
            return@OnlineFrame
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tags.size, key = { tags[it].name }) { i ->
                val tag = tags[i]
                TwoLineRow(tag = "radio_genre:${tag.name}", primary = tag.name, secondary = stationCount(tag.stationCount), onTap = { nav.open(RadioGenrePage(tag)) }) {
                    GlyphSquare(Glyph.LIST)
                }
            }
        }
    }
}

/** The directory's countries, A to Z. A tap lists the country's stations. */
@Composable
internal fun RadioCountries(nav: OnlineNav) {
    val context = LocalContext.current
    val directory by remember(context) { RadioDirectoryStore.get(context) }.directory.collectAsState()
    val colors = LocalShellColors.current
    OnlineFrame(caption = "radio", title = "by country", tag = "radio_page:countries") {
        val countries = directory.countries
        if (countries.isEmpty()) {
            BasicText(
                "No countries yet — refresh the stations.",
                style = ShellType.body.copy(color = colors.subtleText),
                modifier = Modifier.padding(start = MusicMetrics.SIDE, end = MusicMetrics.SIDE).testTag("radio_empty"),
            )
            return@OnlineFrame
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(countries.size, key = { countries[it].code + countries[it].name }) { i ->
                val country = countries[i]
                TwoLineRow(tag = "radio_country:${country.code}", primary = country.name, secondary = stationCount(country.stationCount), onTap = { nav.open(RadioCountryPage(country)) }) {
                    GlyphSquare(Glyph.GLOBE)
                }
            }
        }
    }
}

@Composable
internal fun RadioGenre(page: RadioGenrePage, favourites: List<Station>, actions: StationActions) {
    val context = LocalContext.current
    val directory by remember(context) { RadioDirectoryStore.get(context) }.directory.collectAsState()
    OnlineFrame(caption = "genre", title = page.tag.name, tag = "radio_page:genre") {
        val favs by rememberUpdatedState(favourites)
        StationResults(page.tag.name to directory.index, "No stations in this genre.", actions) { offset ->
            RadioDirectory.byTag(page.tag.name, directory.index, favs, offset)
        }
    }
}

@Composable
internal fun RadioCountryStations(page: RadioCountryPage, favourites: List<Station>, actions: StationActions) {
    val context = LocalContext.current
    val directory by remember(context) { RadioDirectoryStore.get(context) }.directory.collectAsState()
    OnlineFrame(caption = "country", title = page.country.name, tag = "radio_page:country") {
        val favs by rememberUpdatedState(favourites)
        StationResults(page.country.code to directory.index, "No stations in this country.", actions) { offset ->
            RadioDirectory.byCountry(page.country.code, directory.index, favs, offset)
        }
    }
}

/** The accent square with a glyph that stands where a row has no picture of its own (the artists pivot's own form). */
@Composable
internal fun GlyphSquare(glyph: String) {
    Box(Modifier.size(MusicMetrics.ART).background(LocalShellColors.current.accent), Alignment.Center) {
        BasicText(glyph, style = ShellType.body.copy(color = Color.White, fontFamily = Brand.iconFont))
    }
}

private fun stationCount(n: Int): String = if (n == 1) "1 station" else "$n stations"
