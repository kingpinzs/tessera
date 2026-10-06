package app.tileshell.photos

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calculator.InkText
import app.tileshell.clock.detectTapOrHold
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.PhotosFeed
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Photos' library (build task 4, Y1): the pivot titles on the black page, the Collection and Albums pivots under them
 * (a swipe or a title's tap changes pivot), an album's own page, and the 48-epx #1F1F1F app bar on the nav bar.
 */
@Composable
fun BoxScope.LibraryPages(nav: PhotosNav, library: Library, onGrant: () -> Unit, onOpen: (MediaEntry, Long?) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val albums = remember(library.items) { PhotoGroups.albums(library.items) }
    val openAlbum = nav.album?.let { id -> albums.firstOrNull { it.bucketId == id } }
    val collectionState = rememberLazyListState()
    val albumsState = rememberLazyListState()
    var barExpanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().padding(top = PhotosMetrics.HEADER)) {
            if (openAlbum != null) {
                val items = remember(library.items, openAlbum.bucketId) { library.items.filter { it.bucketId == openAlbum.bucketId } }
                val rows = remember(items) { PhotoGroups.collection(items, zone) }
                val state = remember(openAlbum.bucketId) { LazyListState() }
                CollectionList(nav, rows, state, null, null, onGrant) { onOpen(it, openAlbum.bucketId) }
            } else {
                PivotPager(nav) { pivot ->
                    when {
                        !library.loaded -> Box(Modifier.fillMaxSize())
                        library.access == PhotosFeed.Access.DENIED -> StatePage(
                            "Photos can't read the pictures on this phone. Allow it here, or turn on Photos in Settings > Setup checklist.",
                            "Allow access", "photos_denied", onGrant,
                        )
                        library.items.isEmpty() && library.access == PhotosFeed.Access.PARTIAL -> StatePage(PARTIAL_TEXT, PARTIAL_LINK, "photos_partial", onGrant)
                        library.items.isEmpty() -> StatePage(
                            "No photos or videos yet. Pictures you take or save show up here. If some are missing, check Photos and Videos in Settings > Setup checklist.",
                            if (library.videoAccess) null else "Allow videos", "photos_empty", onGrant,
                        )
                        pivot == PhotosPivot.COLLECTION -> {
                            val rows = remember(library.items) { PhotoGroups.collection(library.items, zone) }
                            CollectionList(
                                nav, rows, collectionState,
                                partial = if (library.access == PhotosFeed.Access.PARTIAL) PARTIAL_TEXT to PARTIAL_LINK else null,
                                videosHint = if (library.videoAccess) null else "Videos are hidden. Allow them here, or turn on Videos in Settings > Setup checklist.",
                                onGrant = onGrant,
                            ) { onOpen(it, null) }
                        }
                        else -> AlbumsList(albums, albumsState) { nav.album = it.bucketId; Diagnostics.add("photosapp", "album \"${it.name}\": ${it.count}") }
                    }
                }
            }
        }
        if (openAlbum != null) {
            Box(Modifier.fillMaxWidth().height(PhotosMetrics.HEADER)) {
                InkText(
                    openAlbum.name, titleStyle(Color.White), reference = "H", modifier = Modifier.testTag("photos_album_title"),
                    inkLeftEpx = PhotosMetrics.TITLE_INK_LEFT[0], inkTopEpx = PhotosMetrics.TITLE_INK_TOP,
                )
            }
        } else {
            PivotHeader(nav)
        }
        val firstImage = (if (openAlbum != null) library.items.filter { it.bucketId == openAlbum.bucketId } else library.items).firstOrNull { !it.video }
        val context = LocalContext.current
        PhotoBar(
            buttons = listOf(
                PhotoBarButton(Glyph.SYNC, "Refresh", "photos_refresh") { PhotoStore.refresh(context, "refresh button") },
                PhotoBarButton(Glyph.SLIDESHOW, "Slideshow", "photos_slideshow", enabled = firstImage != null) {
                    firstImage?.let { nav.openViewer(it, openAlbum?.bucketId, slideshow = true) }
                },
            ),
            menu = emptyList(),
            fill = PhotosMetrics.LIBRARY_BAR_FILL,
            tagPrefix = "photos",
            isExpanded = { barExpanded },
            onExpand = { barExpanded = it },
        )
    }
    androidx.activity.compose.BackHandler(enabled = barExpanded && nav.viewer == null) { app.tileshell.ui.components.dismissOverlay { barExpanded = false } }
}

private const val PARTIAL_TEXT = "Photos can see only the pictures you selected. Select more here, or allow all of them in Settings > Setup checklist."
private const val PARTIAL_LINK = "Select photos"

private fun titleStyle(color: Color) = ShellType.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(PhotosMetrics.TITLE_SIZE, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = Brand.semiLight, color = color)

/** Y1's pivot titles: 28-epx mixed case straight on the page, the selected one white, the other #999999, no underline. */
@Composable
private fun PivotHeader(nav: PhotosNav) {
    Box(Modifier.fillMaxWidth().height(PhotosMetrics.HEADER).testTag("photos_pivots")) {
        PhotosPivot.entries.forEachIndexed { i, pivot ->
            val selected = pivot == nav.pivot
            InkText(
                pivot.title, titleStyle(if (selected) Color.White else PhotosMetrics.TITLE_UNSELECTED), reference = "H",
                modifier = Modifier.testTag("photos_pivot:${pivot.id}")
                    // Role.Tab: Compose reports `selected` as checked on any other node (phase 10 E15).
                    .semantics { role = Role.Tab; this.selected = selected }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (nav.pivot != pivot) { nav.pivot = pivot; Diagnostics.add("photosapp", "pivot ${pivot.id} (tap)") }
                    },
                inkLeftEpx = PhotosMetrics.TITLE_INK_LEFT[i], inkTopEpx = PhotosMetrics.TITLE_INK_TOP,
            )
        }
    }
}

/**
 * The pager under the titles: a swipe drags the pivot with its neighbour following, and the release settles on the
 * shell's pivot settle (250 ms, phase 10's MusicMetrics value) through the motion clock as `[motion] photos_pivot`.
 */
@Composable
private fun PivotPager(nav: PhotosNav, content: @Composable (PhotosPivot) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        var animating by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val current = nav.pivot
        val prev = PhotosPivot.entries.getOrNull(current.ordinal - 1)
        val next = PhotosPivot.entries.getOrNull(current.ordinal + 1)

        fun settle() {
            if (animating) return
            val target = when {
                drag < -widthPx / 4f && next != null -> next
                drag > widthPx / 4f && prev != null -> prev
                else -> null
            }
            val end = when (target) { null -> 0f; next -> -widthPx; else -> widthPx }
            val start = drag
            if (start == end) { drag = 0f; return }
            animating = true
            scope.launch {
                MotionClock.animate("photos_pivot", Motion.PIVOT_SETTLE_MS, FastOutSlowInEasing) { f -> drag = start + (end - start) * f }
                target?.let { nav.pivot = it; Diagnostics.add("photosapp", "pivot ${it.id} (swipe)") }
                drag = 0f
                animating = false
            }
        }

        Box(
            Modifier.fillMaxSize().clipToBounds().pointerInput(current, widthPx) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { change, dx ->
                    if (animating) return@detectHorizontalDragGestures
                    drag = (drag + dx).coerceIn(if (next != null) -widthPx else 0f, if (prev != null) widthPx else 0f)
                    change.consume()
                }
            },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag }) { content(current) }
            if (drag > 0f && prev != null) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag - widthPx }) { content(prev) }
            if (drag < 0f && next != null) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag + widthPx }) { content(next) }
        }
    }
}

/** A state with nothing to list: its text names the Setup checklist, and the grant is offered right here (task 4). */
@Composable
private fun StatePage(text: String, link: String?, tag: String, onGrant: () -> Unit) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            BasicText(text, Modifier.testTag(tag), style = ShellType.subtitle.copy(color = Color.White))
            if (link != null) {
                BasicText(
                    link,
                    Modifier.padding(top = 16.dp).testTag("photos_grant").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onGrant),
                    style = ShellType.subtitle.copy(color = colors.accent),
                )
            }
        }
    }
}

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())

/** The 15-epx class's cap top inside the first month row and the first day row, and each row's height (1.3.4, 1.3.8). */
private const val MONTH_ROW = 34f
private const val DAY_CAP_TOP = 11.71f
private const val DAY_ROW = 36f

/** The collection (Y1): an accent month header, a day row (date left, count right), 3 columns of 111-epx squares. */
@Composable
private fun CollectionList(
    nav: PhotosNav,
    rows: List<CollectionRow>,
    state: LazyListState,
    partial: Pair<String, String>?,
    videosHint: String?,
    onGrant: () -> Unit,
    onOpen: (MediaEntry) -> Unit,
) {
    val colors = LocalShellColors.current
    LazyColumn(Modifier.fillMaxSize().testTag("photos_collection"), state = state, contentPadding = PaddingValues(bottom = PhotosMetrics.BAR + 12.dp)) {
        if (partial != null) {
            item(key = "partial") {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
                    BasicText(partial.first, Modifier.testTag("photos_partial"), style = ShellType.body.copy(color = PhotosMetrics.SECONDARY))
                    BasicText(
                        partial.second,
                        Modifier.padding(top = 6.dp).testTag("photos_grant").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onGrant),
                        style = ShellType.body.copy(color = colors.accent),
                    )
                }
            }
        }
        items(rows, key = { it.key }, contentType = { it::class }) { row ->
            when (row) {
                is CollectionRow.Month -> {
                    val capTop = if (row.first) PhotosMetrics.MONTH_CAP_TOP else PhotosMetrics.MONTH_GAP_ABOVE - PhotosMetrics.GUTTER.value
                    Box(Modifier.fillMaxWidth().height((capTop + MONTH_ROW - PhotosMetrics.MONTH_CAP_TOP).dp)) {
                        InkText(
                            row.month.format(MONTH_FORMAT), ShellType.body.copy(color = colors.accent), reference = "H",
                            modifier = Modifier.testTag("photos_month:${row.month}"), inkLeftEpx = 12f, inkTopEpx = capTop,
                        )
                    }
                }
                is CollectionRow.Day -> {
                    val capTop = if (row.afterTiles) PhotosMetrics.MONTH_TO_DAY - PhotosMetrics.GUTTER.value else DAY_CAP_TOP
                    Box(Modifier.fillMaxWidth().height((capTop + DAY_ROW - DAY_CAP_TOP).dp)) {
                        InkText(
                            row.day.format(DAY_FORMAT), ShellType.body.copy(color = PhotosMetrics.SECONDARY), reference = "1",
                            modifier = Modifier.testTag("photos_day:${row.day}"), inkLeftEpx = 12f, inkTopEpx = capTop,
                        )
                        BasicText(
                            PhotoGroups.countText(row.count),
                            Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = CapMetrics.topPaddingForCapTop(capTop, 15f).dp).testTag("photos_day_count:${row.day}"),
                            style = ShellType.body.copy(color = PhotosMetrics.SECONDARY), maxLines = 1,
                        )
                    }
                }
                is CollectionRow.Tiles -> Row(Modifier.fillMaxWidth().height(PhotosMetrics.TILE + PhotosMetrics.GUTTER).padding(start = PhotosMetrics.GRID_LEFT)) {
                    row.items.forEach { item ->
                        PhotoTile(nav, item, Modifier.padding(end = PhotosMetrics.GUTTER).size(PhotosMetrics.TILE), onTap = { onOpen(item) })
                    }
                }
            }
        }
        if (videosHint != null) {
            item(key = "videos-hint") {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp)) {
                    BasicText(videosHint, Modifier.testTag("photos_videos_hint"), style = ShellType.body.copy(color = PhotosMetrics.SECONDARY))
                    BasicText(
                        "Allow videos",
                        Modifier.padding(top = 6.dp).testTag("photos_grant_videos").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onGrant),
                        style = ShellType.body.copy(color = colors.accent),
                    )
                }
            }
        }
    }
}

/** A tile's picture as loaded: [missing] once MediaStore has said the row has none (its file is gone). */
private data class TileThumb(val bitmap: androidx.compose.ui.graphics.ImageBitmap?, val missing: Boolean)

/**
 * One square tile: MediaStore's thumbnail centre-cropped; a video carries W10M's dark disc with the outline play
 * triangle (1.3.14); a row whose file cannot be read is a placeholder (Edge cases). A tap opens it; a hold on a video
 * raises the Edit sheet (Trim). A still that is a Living Image (build task 6d; [LivingImages] — read off the main thread,
 * once per state of the row) carries the Living Images glyph on a dark disc in its lower left corner. Tags
 * `photos_item:<id>`, `photos_video_disc:<id>`, `photos_item_missing:<id>`, `photos_living:<id>`.
 */
@Composable
fun PhotoTile(nav: PhotosNav, item: MediaEntry, modifier: Modifier, onTap: () -> Unit) {
    val context = LocalContext.current
    val loaded by produceState(TileThumb(PhotoThumbs.cached(item.id), PhotoThumbs.knownUnreadable(item.id)), item.id, item.dateMs, item.size) {
        val bitmap = PhotoThumbs.load(context, item)
        value = TileThumb(bitmap, bitmap == null)
    }
    // The key the answer was read for comes back with it, so a tile reused for another row never shows the last one's.
    val key = LivingRules.key(item)
    val livingFor by produceState(if (LivingImages.cached(item) != null) key else null, key) {
        value = if (LivingImages.of(context, item) != null) key else null
    }
    val thumb = loaded.bitmap
    val missing = loaded.missing
    DisposableEffect(item.id) { onDispose { nav.tileBounds.remove(item.id) } }
    Box(
        modifier.background(PhotosMetrics.PLACEHOLDER).testTag("photos_item:${item.id}")
            .onGloballyPositioned { nav.tileBounds[item.id] = it.boundsInRoot() }
            .pointerInput(item.id) { detectTapOrHold(onTap = onTap, onHold = { if (item.video) nav.sheetFor = item }) },
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(bitmap, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (missing) {
            PhotoGlyph(Glyph.IMAGE_OFF, 32f, PhotosMetrics.SECONDARY, Modifier.fillMaxSize().testTag("photos_item_missing:${item.id}"))
        }
        if (item.video) {
            Box(
                Modifier.align(Alignment.Center).size(PhotosMetrics.VIDEO_DISC).background(Color.Black.copy(alpha = 0.58f), CircleShape).testTag("photos_video_disc:${item.id}"),
                contentAlignment = Alignment.Center,
            ) { PhotoGlyph(Glyph.PLAY_OUTLINE, 18f, Color.White, Modifier.offset(x = 1.dp)) }
        }
        if (!item.video && livingFor == key) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(PhotosMetrics.LIVING_DISC).background(Color.Black.copy(alpha = 0.58f), CircleShape).testTag("photos_living:${item.id}"),
                contentAlignment = Alignment.Center,
            ) { PhotoGlyph(Glyph.LIVING_IMAGE, 14f, Color.White) }
        }
    }
}

/** The Albums pivot (Y1, 1.4.1–1.4.2): 60-epx tiles, two columns of 162 epx, the newest picture darkened under the name. */
@Composable
private fun AlbumsList(albums: List<Album>, state: LazyListState, onOpen: (Album) -> Unit) {
    val rows = remember(albums) { albums.chunked(2) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("photos_albums"), state = state,
        contentPadding = PaddingValues(top = PhotosMetrics.ALBUM_MARGIN, bottom = PhotosMetrics.BAR + 12.dp),
    ) {
        items(rows, key = { it.first().bucketId }) { pair ->
            Row(Modifier.fillMaxWidth().height(PhotosMetrics.ALBUM_H + PhotosMetrics.ALBUM_MARGIN).padding(start = PhotosMetrics.ALBUM_MARGIN)) {
                pair.forEach { album -> AlbumTile(album, Modifier.padding(end = PhotosMetrics.ALBUM_MARGIN).size(PhotosMetrics.ALBUM_W, PhotosMetrics.ALBUM_H)) { onOpen(album) } }
            }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, modifier: Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState(PhotoThumbs.cached(album.cover.id), album.cover.id) { value = PhotoThumbs.load(context, album.cover) }
    Box(
        modifier.background(PhotosMetrics.PLACEHOLDER).clipToBounds().testTag("photos_album:${album.bucketId}")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
    ) {
        thumb?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        // 1.4.2: the picture darkened, so the white name reads on any photo.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        BasicText(
            album.name,
            Modifier.align(Alignment.CenterStart).padding(start = PhotosMetrics.ALBUM_LABEL_LEFT.dp).width(100.dp).testTag("photos_album_name:${album.bucketId}"),
            style = ShellType.base.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            album.count.toString(),
            Modifier.align(Alignment.CenterEnd).padding(end = PhotosMetrics.ALBUM_LABEL_LEFT.dp).testTag("photos_album_count:${album.bucketId}"),
            style = ShellType.body.copy(color = Color.White), maxLines = 1,
        )
    }
}
