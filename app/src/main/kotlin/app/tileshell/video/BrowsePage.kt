package app.tileshell.video

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import app.tileshell.video.catalogue.BrowseSection
import app.tileshell.video.catalogue.Catalogue
import app.tileshell.video.catalogue.CatalogueItem
import app.tileshell.video.catalogue.CatalogueNotice
import app.tileshell.video.catalogue.CatalogueResult
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.video.handoff.StreamingHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Browse's measured and designed places (Y8; r11/movies-tv.md 1.5.2 and r11/movies-tv-pass2.md 1.5.5), in epx. */
object BrowseMetrics {
    /** 1.5.5 (MEDIUM): posters 112 × 160 with a 1.2-epx #1C1C1C border, on a 120 pitch. */
    const val POSTER_W = 112f
    const val POSTER_H = 160f
    const val POSTER_PITCH = 120f
    const val POSTER_BORDER = 1.2f
    val POSTER_EDGE = Color(0xFF1C1C1C)

    /** 1.5.5: the title's first baseline is 22.8 under the poster; two lines at a 20 pitch, clipped at the poster's right edge. */
    const val POSTER_TITLE_BASELINE = 22.8f

    /** 1.5.5: the strip's top is the section title's cap top + 24. */
    const val STRIP_BELOW_CAP = 24f
    const val SIDE = 12f
}

/** The Browse page's state, kept by the activity so it survives a visit to another page. */
class BrowseModel(context: Context) {
    val catalogue: Catalogue = StreamingHandoff.catalogue(context)
    var hasKey by mutableStateOf<Boolean?>(null)
    var text by mutableStateOf("")

    /** The search on screen ("" = the strips). */
    var query by mutableStateOf("")
    var results by mutableStateOf<List<CatalogueItem>>(emptyList())
    var notice by mutableStateOf(CatalogueNotice.NONE)
    var loading by mutableStateOf(false)
    val sections = mutableStateMapOf<BrowseSection, List<CatalogueItem>>()

    /** A strip opened with "Show all", listed like a search's results. */
    var shownAll by mutableStateOf<BrowseSection?>(null)
    private var restored = false

    /**
     * Reads the key's state and loads what the page shows: the search on screen (the last one, when the page first
     * opens), else the strips. [submitted]: the user just sent this search, so it becomes the one the page comes back to.
     */
    fun load(scope: CoroutineScope, submitted: Boolean) {
        scope.launch {
            loading = true
            var q = query
            if (!restored) {
                restored = true
                if (q.isEmpty()) {
                    val last = VideoCalls.io("catalogue last search", "") { catalogue.lastQuery }
                    if (last.isNotEmpty()) { q = last; query = last; text = last }
                }
            }
            if (q.isNotEmpty()) {
                val r = VideoCalls.io("catalogue search", failed<List<CatalogueItem>>()) { catalogue.search(q) }
                hasKey = r.notice != CatalogueNotice.NO_KEY
                notice = r.notice
                results = r.value.orEmpty()
                if (submitted && hasKey == true) VideoCalls.io("catalogue last search", Unit) { catalogue.lastQuery = q }
            } else {
                val key = VideoCalls.io("catalogue key read", false) { catalogue.hasKey() }
                hasKey = key
                results = emptyList()
                if (!key) {
                    // The one no-key form: the page says so, the line is written, and no request is made.
                    notice = CatalogueNotice.NO_KEY
                    Diagnostics.add("video", CatalogueRules.LINE_NO_KEY)
                } else {
                    notice = CatalogueNotice.NONE
                    for (section in BrowseSection.entries) {
                        val r = VideoCalls.io("catalogue section", failed<List<CatalogueItem>>()) { catalogue.section(section) }
                        sections[section] = r.value.orEmpty()
                        if (r.notice != CatalogueNotice.NONE) notice = r.notice
                    }
                }
            }
            loading = false
        }
    }

    /** What a catalogue call that threw gives the page: nothing new, and "The catalogue isn't answering". */
    private fun <T> failed(): CatalogueResult<T> = CatalogueResult(null, CatalogueNotice.NOT_ANSWERING, true)

    fun clearSearch(scope: CoroutineScope) {
        text = ""
        query = ""
        shownAll = null
        scope.launch { VideoCalls.io("catalogue last search", Unit) { catalogue.lastQuery = "" } }
        load(scope, submitted = false)
    }
}

/**
 * Browse (build tasks 12 and 14; Y8, a P4 design on R11's store-half forms): R3 C1's search box, then either the
 * search's results or the strips — a section row "title + accent Show all" over one horizontally scrolling strip of
 * 112 × 160 posters on a 120 pitch — and TMDB's attribution at the page's foot. With no key saved the page says film
 * search needs one and links to the setting; with a refused key it says so, with the same link.
 *
 * Tags: `hub_search_box`, `hub_browse_notice`, `hub_key_link`, `hub_result:<id>` with `hub_result_title:<id>`,
 * `hub_result_year:<id>` and `hub_result_image:<id>` (or `hub_result_placeholder:<id>`), `hub_browse_empty`,
 * `hub_section:<id>`, `hub_section_all:<id>`, `hub_poster:<section>:<id>`, `hub_attribution`. A series' id is `tv-<n>`.
 */
@Composable
fun BrowsePage(nav: VideoNav, model: BrowseModel) {
    val colors = LocalShellColors.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(nav.resumes) { model.load(scope, submitted = false) }
    LaunchedEffect(nav.searchRequests) { if (nav.searchRequests > 0 && model.hasKey == true) runCatching { focus.requestFocus() } }

    // The page itself takes the focus first: when a field's focus is cleared Compose gives it to the first focusable
    // node, and with the search box in that place the keyboard came straight back after a search was sent.
    Column(Modifier.fillMaxSize().focusable().testTag("hub_page:browse")) {
        val keyMissing = model.hasKey == false
        if (!keyMissing) {
            OutlinedField(
                value = model.text, onValueChange = { model.text = it }, tag = "hub_search_box",
                modifier = Modifier.padding(start = BrowseMetrics.SIDE.dp, end = BrowseMetrics.SIDE.dp, top = 12.dp).fillMaxWidth().focusRequester(focus),
                maxLength = 100, placeholder = "Search films and TV", imeAction = ImeAction.Search,
                onImeAction = {
                    // force: the box is the page's first focusable, and a plain clear hands the focus straight back to it.
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                    val q = model.text.trim()
                    if (q.isEmpty()) model.clearSearch(scope) else { model.query = q; model.shownAll = null; model.load(scope, submitted = true) }
                },
            )
        }
        model.notice.text?.let { text ->
            BasicText(
                text, Modifier.padding(start = BrowseMetrics.SIDE.dp, end = BrowseMetrics.SIDE.dp, top = 12.dp).testTag("hub_browse_notice"),
                style = (if (keyMissing) ShellType.subtitle else ShellType.body).copy(color = Color.White),
            )
        }
        if (model.notice == CatalogueNotice.NO_KEY || model.notice == CatalogueNotice.BAD_KEY) {
            BasicText(
                if (model.notice == CatalogueNotice.NO_KEY) "Add your TMDB key" else "Replace or remove the key",
                Modifier.padding(start = BrowseMetrics.SIDE.dp, top = 12.dp).testTag("hub_key_link")
                    .pointerInput(Unit) { detectTapGestures { nav.openSub(TmdbKeySub) } },
                style = ShellType.body.copy(color = colors.accent),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val all = model.shownAll
            when {
                keyMissing || model.hasKey == null -> Unit
                model.query.isNotEmpty() -> ResultList(model, model.results, emptyText = "No results for “${model.query}”") { nav.openSub(TitleSub(it)) }
                all != null -> ResultList(model, model.sections[all].orEmpty(), emptyText = "Nothing here yet") { nav.openSub(TitleSub(it)) }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(BrowseSection.entries.filter { !model.sections[it].isNullOrEmpty() }, key = { it.id }) { section ->
                        Strip(model, section, model.sections[section].orEmpty(), onAll = { model.shownAll = section }) { nav.openSub(TitleSub(it)) }
                    }
                }
            }
        }
        // TMDB's terms ask for the attribution where its data shows (T17-14): the page's foot, always laid out.
        if (!keyMissing) {
            BasicText(
                Attribution.TMDB, Modifier.padding(horizontal = BrowseMetrics.SIDE.dp, vertical = 6.dp).testTag("hub_attribution"),
                style = ShellType.caption.copy(color = colors.subtleText),
            )
        }
    }
}

/** A search's results (or a whole strip): rows with the poster at the left, the title and the year. */
@Composable
private fun ResultList(model: BrowseModel, items: List<CatalogueItem>, emptyText: String, onOpen: (CatalogueItem) -> Unit) {
    val colors = LocalShellColors.current
    if (items.isEmpty()) {
        if (!model.loading) {
            BasicText(emptyText, Modifier.padding(start = BrowseMetrics.SIDE.dp, top = 16.dp).testTag("hub_browse_empty"), style = ShellType.subtitle.copy(color = colors.subtleText))
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(items, key = { it.key }) { item ->
            Row(
                Modifier.fillMaxWidth().height(96.dp).padding(start = BrowseMetrics.SIDE.dp).testTag("hub_result:${item.key}")
                    .pointerInput(item.key) { detectTapGestures { onOpen(item) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Poster(model, item, 56f, 80f, "hub_result_image:${item.key}", "hub_result_placeholder:${item.key}")
                Column(Modifier.padding(start = 12.dp, end = BrowseMetrics.SIDE.dp)) {
                    BasicText(item.title, Modifier.testTag("hub_result_title:${item.key}"), style = ShellType.body.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Clip)
                    BasicText(item.year, Modifier.padding(top = 2.dp).testTag("hub_result_year:${item.key}"), style = ShellType.caption.copy(color = colors.subtleText), maxLines = 1)
                }
            }
        }
    }
}

/** 1.5.2 / 1.5.5: "title + accent Show all", then one strip of posters clipped at the right margin. */
@Composable
private fun Strip(model: BrowseModel, section: BrowseSection, items: List<CatalogueItem>, onAll: () -> Unit, onOpen: (CatalogueItem) -> Unit) {
    val colors = LocalShellColors.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = BrowseMetrics.SIDE.dp).height((BrowseMetrics.STRIP_BELOW_CAP + CapMetrics.capTopWithinBox(15f)).dp)) {
            BasicText(section.title, Modifier.testTag("hub_section:${section.id}"), style = ShellType.base.copy(color = Color.White), maxLines = 1)
            BasicText(
                "Show all", Modifier.align(Alignment.TopEnd).testTag("hub_section_all:${section.id}").pointerInput(section.id) { detectTapGestures { onAll() } },
                style = ShellType.body.copy(color = colors.accent), maxLines = 1,
            )
        }
        LazyRow(Modifier.fillMaxWidth().padding(start = BrowseMetrics.SIDE.dp, end = BrowseMetrics.SIDE.dp).clipToBounds()) {
            items(items, key = { it.key }) { item ->
                Column(Modifier.width(BrowseMetrics.POSTER_PITCH.dp).pointerInput(item.key) { detectTapGestures { onOpen(item) } }) {
                    Poster(model, item, BrowseMetrics.POSTER_W, BrowseMetrics.POSTER_H, "hub_poster:${section.id}:${item.key}", "hub_poster_placeholder:${section.id}:${item.key}")
                    BasicText(
                        item.title,
                        Modifier.padding(top = (BrowseMetrics.POSTER_TITLE_BASELINE - 15f * CapMetrics.ASCENT_RATIO).dp).width(BrowseMetrics.POSTER_W.dp).height(40.dp).clipToBounds(),
                        style = ShellType.body.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/** A poster in its bordered box; a title with no artwork (or none to be had) draws the placeholder glyph. */
@Composable
fun Poster(model: BrowseModel, item: CatalogueItem, width: Float, height: Float, imageTag: String, placeholderTag: String) {
    val bitmap by produceState<ImageBitmap?>(PosterMemory.get(item.posterPath), item.posterPath) {
        if (value == null && item.posterPath != null) {
            value = VideoCalls.io("catalogue poster", null) { model.catalogue.poster(item.posterPath)?.asImageBitmap() }?.also { PosterMemory.put(item.posterPath, it) }
        }
    }
    val b = bitmap
    // The tag is on the whole bordered box — the poster as measured, border included.
    Box(
        Modifier.size(width.dp, height.dp).background(BrowseMetrics.POSTER_EDGE).border(BrowseMetrics.POSTER_BORDER.dp, BrowseMetrics.POSTER_EDGE)
            .testTag(if (b != null) imageTag else placeholderTag),
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) {
            Image(b, contentDescription = item.title, modifier = Modifier.fillMaxSize().padding(BrowseMetrics.POSTER_BORDER.dp), contentScale = ContentScale.Crop)
        } else {
            BasicText(
                Glyph.MOVIES_TV,
                style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = (width / 3f).sp, lineHeight = (width / 3f).sp, color = Color.White.copy(alpha = 0.4f)),
            )
        }
    }
}

/** Decoded posters of this run of the app (the files are the catalogue's image cache). */
private object PosterMemory {
    private val map = android.util.LruCache<String, ImageBitmap>(80)
    fun get(path: String?): ImageBitmap? = path?.let(map::get)
    fun put(path: String, bitmap: ImageBitmap) { map.put(path, bitmap) }
}

/** A plain outlined button (W10M's flat rectangle with a 2-epx border, 32 epx tall — R6 §3.4.2's button class). */
@Composable
fun HubButton(label: String, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
    Box(
        modifier.height(32.dp).border(2.dp, ink).testTag(tag).pointerInput(enabled, tag) { detectTapGestures { if (enabled) onClick() } }.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = ShellType.body.copy(color = ink), maxLines = 1)
    }
}
