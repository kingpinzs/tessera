package app.tileshell.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tileshell.applist.AppListMetrics
import app.tileshell.bars.BarMetrics
import app.tileshell.diag.Diagnostics
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.brand.Glyph
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The collection: albums / artists / songs / playlists on a pivot (phase 10 build task 6).
 *
 * ### Where the geometry comes from (Q3)
 *
 * Q3 ruled that only the NOW-PLAYING screen is measured and the collection pivots are approximated from
 * geometry this build already has, so every number here cites the measurement it was taken from rather
 * than inventing one:
 *
 *  - **Rows** are the app list's: 44-epx pitch, 41-epx icon at x = 5, text ink at x = 56 (R3 C2 /
 *    R6 §5.1.4, via [AppListMetrics]). A list of names with a square picture is the same control in
 *    both places, and W10M drew it once.
 *  - **Letter headers and the jump grid** are the app list's too, for the same reason and because
 *    [MusicCollection] already shares its index — one answer about what letter a name files under.
 *  - **The drawn status bar** is 28 epx (R3 C4), and the page starts under it exactly as the app list
 *    does (R3 A14).
 *  - **The pivot header** is the one thing with no prior in this build. It uses the type scale's
 *    `header`/`subheader` light faces (R3 A15's W10M type ramp), the app list's 4-epx left margin
 *    (R3 C2), and the unselected headers at 40 % — a P4 design call, flagged as such.
 *
 * The pivot itself swipes and settles on [app.tileshell.ui.motion.Motion.PIVOT_SETTLE_MS], the same
 * 250 ms Start and the app list settle on (X13), because one shell should not have two pivot speeds.
 */
object MusicMetrics {
    /** R3 C4: the drawn status bar. The page begins under it (R3 A14, as the app list does). */
    val TOP: Dp = BarMetrics.STATUS_EPX.dp
    /** R3 C2: the app list's left margin, so the pivot's ink lines up with the rows under it. */
    val SIDE = 4.dp
    /** P4 design: the app title above the pivot, in the caption face W10M used for it. */
    val TITLE_BLOCK = 28.dp
    /** P4 design: the pivot header strip; the header face is `subheader` (34 epx light) plus its lead. */
    val PIVOT_BLOCK = 54.dp
    /** P4 design: the space between two pivot headers. */
    val PIVOT_GAP = 14.dp
    /** P4 design: the unselected pivot headers, the standard W10M "disabled ink" fraction. */
    const val PIVOT_DIM = 0.4f
    /** R3 C2: the row pitch and its icon, shared with the app list. */
    val ROW = AppListMetrics.ROW
    val ART = AppListMetrics.ICON
    val ART_X = AppListMetrics.ICON_X
    val TEXT_X = AppListMetrics.TEXT_X
}

/**
 * The whole app: the pivot, and the album / artist detail it opens onto.
 *
 * [detail] is state rather than a second activity because Back must return to the pivot ON THE PAGE IT
 * WAS ON — a second activity would come back to a rebuilt pivot sitting on albums again.
 */
@Composable
fun MusicCollectionPage(
    tracks: List<Track>,
    hasAccess: Boolean,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    onWindows: () -> Unit,
) {
    val colors = LocalShellColors.current
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<CollectionItem?>(null) }
    val pager = rememberPagerState { MusicPivot.entries.size }

    BackHandler(enabled = detail != null) { detail = null }

    // The shell's own chrome, drawn by the page as every other shell-owned page draws it: the music
    // player is an app INSIDE the shell, so it gets the same status bar and the same W10M nav bar
    // rather than Android's.
    Column(Modifier.fillMaxSize().background(colors.background).testTag("music_root")) {
        W10mStatusBar()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val open = detail
            if (open != null) {
                DetailPage(open, onPlay)
            } else {
                Column(Modifier.fillMaxSize()) {
                    BasicText(
                        "music",
                        style = ShellType.caption.copy(color = colors.subtleText),
                        modifier = Modifier
                            .padding(start = MusicMetrics.SIDE)
                            .height(MusicMetrics.TITLE_BLOCK)
                            .testTag("music_app_title"),
                    )
                    // Which pivot is actually showing, said out loud. A device row cannot read it off
                    // the dump: the pager keeps a neighbouring page composed, so both pages' rows are
                    // in the tree and "which page am I on" is a guess from the XML.
                    LaunchedEffect(pager.settledPage) {
                        Diagnostics.add("music", "pivot settled on ${MusicPivot.entries[pager.settledPage].title}")
                    }
                    PivotHeaders(pager.currentPage + pager.currentPageOffsetFraction) { page ->
                        scope.launch { pager.animateScrollToPage(page, animationSpec = androidx.compose.animation.core.tween(app.tileshell.ui.motion.Motion.PIVOT_SETTLE_MS)) }
                    }
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("music_pivot")) { index ->
                        val pivot = MusicPivot.entries[index]
                        val page = remember(pivot, tracks, locale) { MusicCollection.page(pivot, tracks, locale) }
                        PivotPage(pivot, page, hasAccess) { item ->
                            when (item) {
                                is SongItem -> onPlay(page.queue, page.startIndexOf(item.track))
                                is AlbumItem, is ArtistItem -> detail = item
                                is LetterHeader -> Unit
                            }
                        }
                    }
                }
            }
        }
        W10mNavBar(onBack = { if (detail != null) detail = null else onBack() }, onWindows = onWindows)
    }
}

/**
 * The pivot's headers: the current one in full ink, its neighbours dimmed, tapping one swings to it —
 * and the strip SCROLLS with the page (Jeremy, 2026-09-22: "playing is cut off and should scroll into
 * view when swiping right from songs and go out of view again when swiping left to go to songs but not
 * fully out of view just the way it is now where pla is showing").
 *
 * The rule is **scroll no further than the selected header needs**, not "pin the selected header to
 * the left margin", and his sentence is what settles it: on songs the strip must look exactly as it
 * does now — all four headers with "pla" peeking at the edge — and only playlists, which does not fit,
 * pulls the strip along. Pinning to the left margin would have thrown albums and artists off the
 * screen the moment you left them, which is not what he described.
 *
 * So each page has its own offset, `max(0, headerRight − visibleWidth)`, and the strip interpolates
 * between the two the swipe is between. That makes the scroll-in and the scroll-out the same motion
 * run in opposite directions, and it returns to zero on the way back without any state to reset.
 */
@Composable
private fun PivotHeaders(pageOffset: Float, onPick: (Int) -> Unit) {
    val colors = LocalShellColors.current
    val density = LocalDensity.current
    val widths = remember { mutableStateListOf(*Array(MusicPivot.entries.size) { 0 }) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(MusicMetrics.PIVOT_BLOCK)
            .clipToBounds()
            .testTag("music_pivot_headers"),
    ) {
        val visiblePx = with(density) { (maxWidth - MusicMetrics.SIDE * 2).toPx() }
        val gapPx = with(density) { MusicMetrics.PIVOT_GAP.toPx() }
        // Where each header starts, and how far the strip has to move for that header to be whole.
        val shiftFor = remember(widths.toList(), visiblePx, gapPx) {
            var start = 0f
            widths.map { w ->
                val right = start + w
                start += w + gapPx
                (right - visiblePx).coerceAtLeast(0f)
            }
        }
        val last = MusicPivot.entries.lastIndex
        val lo = pageOffset.toInt().coerceIn(0, last)
        val hi = (lo + 1).coerceAtMost(last)
        val shift = shiftFor[lo] + (shiftFor[hi] - shiftFor[lo]) * (pageOffset - lo).coerceIn(0f, 1f)
        val current = kotlin.math.round(pageOffset).toInt().coerceIn(0, last)
        Row(
            Modifier
                .padding(start = MusicMetrics.SIDE)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = -shift },
            horizontalArrangement = Arrangement.spacedBy(MusicMetrics.PIVOT_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicPivot.entries.forEachIndexed { i, pivot ->
                val ink = if (i == current) colors.text else colors.text.copy(alpha = MusicMetrics.PIVOT_DIM)
                BasicText(
                    pivot.title,
                    style = ShellType.subheader.copy(color = ink),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .onGloballyPositioned { widths[i] = it.size.width }
                        .clickable { onPick(i) }
                        .testTag("music_pivot_header:${pivot.name.lowercase()}"),
                )
            }
        }
    }
}

/** One pivot's list, with its jump grid over it. An empty pivot says why it is empty rather than nothing. */
@Composable
private fun PivotPage(pivot: MusicPivot, page: CollectionPage, hasAccess: Boolean, onTap: (CollectionItem) -> Unit) {
    val colors = LocalShellColors.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var gridOpen by remember { mutableStateOf(false) }

    if (page.isEmpty) {
        Box(Modifier.fillMaxSize().padding(start = MusicMetrics.SIDE, top = 12.dp)) {
            BasicText(
                emptyText(pivot, hasAccess),
                style = ShellType.body.copy(color = colors.subtleText),
                modifier = Modifier.testTag("music_empty:${pivot.name.lowercase()}"),
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("music_list:${pivot.name.lowercase()}")) {
            items(page.items.size, key = { page.items[it].key }) { i ->
                when (val item = page.items[i]) {
                    // A tap on a letter header opens the jump grid, exactly as it does in the app list.
                    is LetterHeader -> LetterHeaderRow(item) { gridOpen = true }
                    is AlbumItem -> AlbumRow(item) { onTap(item) }
                    is ArtistItem -> ArtistRow(item) { onTap(item) }
                    is SongItem -> SongRow(item) { onTap(item) }
                }
            }
        }
        if (gridOpen) {
            JumpGrid(page.jump, onDismiss = { gridOpen = false }) { index ->
                gridOpen = false
                scope.launch { list.scrollToItem(index) }
            }
        }
    }
}

private fun emptyText(pivot: MusicPivot, hasAccess: Boolean): String = when {
    !hasAccess -> "Tessera cannot read your music yet. Turn on music access in Settings."
    pivot == MusicPivot.PLAYLISTS -> "You don't have any playlists yet."
    else -> "There is no music on this phone."
}

@Composable
private fun LetterHeaderRow(item: LetterHeader, onTap: () -> Unit) {
    val colors = LocalShellColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(AppListMetrics.HEADER_BLOCK)
            .clickable(onClick = onTap)
            .padding(start = AppListMetrics.HEADER_X)
            .testTag("music_header:${item.letter}"),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(item.letter, style = ShellType.title.copy(color = colors.accent), maxLines = 1)
    }
}

@Composable
private fun AlbumRow(item: AlbumItem, onTap: () -> Unit) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { MusicMetrics.ART.roundToPx() }
    val art by produceState<ImageBitmap?>(MusicArt.cached(item.album.id), item.album.id) {
        if (!MusicArt.has(item.album.id)) value = withContext(Dispatchers.IO) { MusicArt.load(context, item.album, px) }
    }
    TwoLineRow(
        tag = "music_album:${item.album.id}",
        primary = item.album.name,
        secondary = item.album.artist,
        onTap = onTap,
    ) {
        AlbumArt(art)
    }
}

@Composable
private fun ArtistRow(item: ArtistItem, onTap: () -> Unit) {
    val colors = LocalShellColors.current
    TwoLineRow(
        tag = "music_artist:${item.artist.name}",
        primary = item.artist.name,
        secondary = countText(item.artist.trackCount),
        onTap = onTap,
    ) {
        // No art: an artist has no single cover, and picking one album's would be a claim about the rest.
        Box(Modifier.size(MusicMetrics.ART).background(colors.accent), Alignment.Center) {
            BasicText(Glyph.PEOPLE, style = ShellType.body.copy(color = Color.White, fontFamily = app.tileshell.brand.Brand.iconFont))
        }
    }
}

@Composable
private fun SongRow(item: SongItem, onTap: () -> Unit) {
    val colors = LocalShellColors.current
    val playing = MusicPlayer.nowPlayingId == item.track.id.toString()
    TwoLineRow(
        tag = "music_song:${item.track.id}",
        primary = item.track.title,
        secondary = item.track.artist,
        // The row that is loaded in the session is drawn in the accent, so the collection says what the
        // player is on without a second surface having to be open.
        primaryColor = if (playing) colors.accent else colors.text,
        onTap = onTap,
    ) {
        // Drawn, not typed: the icon font this shell ships carries no transport glyphs, which is the
        // same call the tile's transport strip already makes.
        val ink = if (playing) colors.accent else colors.subtleText
        Canvas(Modifier.size(MusicMetrics.ART)) { drawTransportMark(playing && MusicPlayer.isPlaying, ink) }
    }
}

private fun countText(n: Int): String = if (n == 1) "1 song" else "$n songs"

/** A play triangle, or the two pause bars while this row is the one sounding. */
private fun DrawScope.drawTransportMark(paused: Boolean, ink: Color) {
    val side = minOf(size.width, size.height) * 0.42f
    val half = side / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    if (paused) {
        val bar = side * 0.32f
        drawRect(ink, topLeft = Offset(cx - half, cy - half), size = Size(bar, side))
        drawRect(ink, topLeft = Offset(cx + half - bar, cy - half), size = Size(bar, side))
    } else {
        val path = Path().apply {
            moveTo(cx - half * 0.85f, cy - half)
            lineTo(cx - half * 0.85f, cy + half)
            lineTo(cx - half * 0.85f + side * 0.87f, cy)
            close()
        }
        drawPath(path, ink)
    }
}

/** The app list's row, with whatever square belongs on the left of it. */
@Composable
private fun TwoLineRow(
    tag: String,
    primary: String,
    secondary: String,
    onTap: () -> Unit,
    primaryColor: Color? = null,
    leading: @Composable () -> Unit,
) {
    val colors = LocalShellColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(MusicMetrics.ROW)
            .clickable(onClick = onTap)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(MusicMetrics.ART_X))
        leading()
        Column(Modifier.padding(start = MusicMetrics.TEXT_X - MusicMetrics.ART_X - MusicMetrics.ART, end = AppListMetrics.TEXT_END)) {
            BasicText(primary, style = ShellType.body.copy(color = primaryColor ?: colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Tagged: this is the node a device row reads the second line off. A merged
            // contentDescription on the row was tried first and does NOT reach the node uiautomator
            // reports on a testTagsAsResourceId window (MUSIC6 read content-desc="" off a row that had
            // one, before and after testTag in the chain), so it was removed rather than left in place
            // doing nothing. Each line stays its own accessible node, exactly as the app list's do.
            // The prefix is distinct so a count of "music_artist:" rows is not doubled by it.
            BasicText(
                secondary,
                style = ShellType.caption.copy(color = colors.subtleText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_sub:$tag"),
            )
        }
    }
}

@Composable
private fun AlbumArt(art: ImageBitmap?) {
    val colors = LocalShellColors.current
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(MusicMetrics.ART))
    } else {
        // W10M drew a plain accent square with a note glyph for an album with no art; so does this.
        Box(Modifier.size(MusicMetrics.ART).background(colors.accent), Alignment.Center) {
            BasicText(Glyph.MUSIC, style = ShellType.body.copy(color = Color.White, fontFamily = app.tileshell.brand.Brand.iconFont))
        }
    }
}

/**
 * The jump grid: every letter, the empty ones dimmed and untappable (the app list's rule, R3 C2 / X8).
 * 5 columns at the app list's own pitch, so the two grids in the shell are the same control.
 */
@Composable
private fun JumpGrid(targets: List<JumpTarget>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val colors = LocalShellColors.current
    BackHandler(enabled = true, onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(onClick = onDismiss)
            .padding(start = MusicMetrics.SIDE, top = 12.dp)
            .testTag("music_jump_grid"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            targets.chunked(AppListMetrics.GRID_COLUMNS).forEach { rowCells ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowCells.forEach { cell ->
                        val live = cell.index != null
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(if (live) colors.accent else colors.chrome)
                                .then(if (live) Modifier.clickable { onPick(cell.index!!) } else Modifier)
                                .testTag("music_jump:${cell.letter}"),
                            Alignment.Center,
                        ) {
                            BasicText(
                                cell.letter,
                                style = ShellType.title.copy(color = if (live) Color.White else colors.subtleText),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** An album or an artist, opened from the pivot: its tracks, in the order they play. */
@Composable
private fun DetailPage(item: CollectionItem, onPlay: (List<Track>, Int) -> Unit) {
    val colors = LocalShellColors.current
    val queue = remember(item) { MusicCollection.tracksOf(item) }
    val title = when (item) {
        is AlbumItem -> item.album.name
        is ArtistItem -> item.artist.name
        else -> ""
    }
    val subtitle = when (item) {
        is AlbumItem -> item.album.artist
        is ArtistItem -> countText(item.artist.trackCount)
        else -> ""
    }
    Column(Modifier.fillMaxSize().testTag("music_detail")) {
        BasicText(
            subtitle,
            style = ShellType.caption.copy(color = colors.subtleText),
            maxLines = 1,
            modifier = Modifier.padding(start = MusicMetrics.SIDE).height(MusicMetrics.TITLE_BLOCK),
        )
        BasicText(
            title,
            style = ShellType.subheader.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = MusicMetrics.SIDE)
                .height(MusicMetrics.PIVOT_BLOCK)
                .testTag("music_detail_title"),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(queue.size, key = { queue[it].id }) { i ->
                SongRow(SongItem(queue[i])) { onPlay(queue, i) }
            }
        }
    }
}
