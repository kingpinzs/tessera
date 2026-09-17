package app.tileshell.applist

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.apps.ProfileKind
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.LocalStartTheme
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** App list geometry in epx (1.dp == 1 epx inside ShellDensity), each value with its source (RV9). */
object AppListMetrics {
    /** R3 C4: drawn status bar 28 epx; the search box top sits directly under it (R3 A14, HIGH). */
    val TOP: Dp = BarMetrics.STATUS_EPX.dp
    /** R3 A14 (HIGH): 32 ± 1 epx tall, 4-6 epx side margins, "Search" left, magnifier right, 1-px outline. */
    val SEARCH_HEIGHT = 32.dp
    val SEARCH_SIDE = 5.dp
    /** Placeholder and glyph insets inside the box: not measured by A14 (left / right alignment only). */
    val SEARCH_INSET = 8.dp
    const val SEARCH_GLYPH_EPX = 15

    /** R3 C2: row pitch 44 ± 1 epx; icon 41 ± 1 epx square with its left edge at 4-6 epx. */
    val ROW = 44.dp
    val ICON = 41.dp
    val ICON_X = 5.dp
    /** R3 C2 / R6 §5.1.4: name and "New" ink start at x = 57 epx; Selawik's side bearing adds ≈1 epx to the box x. */
    val TEXT_X = 56.dp
    val TEXT_END = 5.dp

    /** R6 §5.1.5 (MEDIUM): an uncaptioned name's text centre sits 2.75 epx below the icon centre: baseline 31 epx from the row top. */
    const val NAME_BASELINE = 31f
    /** R6 §5.1.5: a captioned name's text centre sits 7 epx above the icon centre: baseline 22 epx (on the icon centre line). */
    const val NAME_BASELINE_CAPTIONED = 22f
    /** R6 §5.1.5: the "New" baseline sits 18 epx below the name's (cap 8.3 epx, 12-epx class, R6 §5.1.3). */
    const val CAPTION_BASELINE = 40f

    /** R3 C2: previous icon bottom to next icon top across a header is 53 ± 1.5 epx; the block is that less the two 1.5-epx row insets. */
    val HEADER_BLOCK = 50.dp
    /** R3 C2: letter cap 15-18 epx (Title 24 SemiLight: cap 16.8 epx), left edge at 4-6 epx. */
    val HEADER_X = 4.dp
    /** P4 design: profile header glyph sized to the letter cap. */
    const val HEADER_GLYPH_EPX = 20
    /** P4 design: glyph plate in the icon's bottom-right corner for a work / private app. */
    val CORNER_PLATE = 16.dp
    const val CORNER_GLYPH_EPX = 12

    /** X8 (LOW, build 10586): 5 columns, ≈50 epx pitch, ≈19 epx letter cap (27-epx font, Selawik cap = 0.7 em). */
    const val GRID_COLUMNS = 5
    val GRID_PITCH = 50.dp
    const val GRID_LETTER_EPX = 27
    const val GRID_GLYPH_EPX = 24
    /** X8 form: unavailable letters dimmed; the amount and the scrim are not measured. */
    const val GRID_DIM_ALPHA = 0.3f
    const val GRID_SCRIM_ALPHA = 0.92f
}

private fun glyphStyle(color: Color, sizeEpx: Int) = TextStyle(fontFamily = Brand.iconFont, fontSize = sizeEpx.sp, color = color)

/**
 * The W10M app list (build task 11): search box, "#" and A-Z groups with letter headers, the accent "New" caption,
 * the letter jump grid (X8) and the P4 work / private profile groups after Z.
 */
@Composable
fun AppListPage(onLaunch: (AppEntry, Rect?) -> Unit) {
    val context = LocalContext.current
    val theme = LocalStartTheme.current
    val catalog = remember { AppCatalog.get(context) }
    val store = remember { NewAppStore.get(context) }
    val tracker = remember { ProfileTracker.get(context) }
    val apps by catalog.apps.collectAsState()
    val profiles by tracker.profiles.collectAsState()
    val newKeys by store.newKeys.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val showProfiles = theme.showWorkAndPrivateApps
    val model = remember(apps, profiles, showProfiles, locale) {
        buildAppListModel(apps, profiles, showProfiles, locale, store::keyOf, store::serialOf)
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val launchCallback by rememberUpdatedState(onLaunch)

    var query by remember { mutableStateOf("") }
    var gridOpen by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val searchState = rememberLazyListState()
    val iconPx = with(LocalDensity.current) { AppListMetrics.ICON.roundToPx() }
    val icons = remember(iconPx) { IconMemo(catalog, iconPx) }
    val generation = System.identityHashCode(apps)

    LaunchedEffect(model) { Diagnostics.add("applist", "model: ${model.describe()}") }
    LaunchedEffect(apps) {
        tracker.refresh()
        withContext(Dispatchers.IO) {
            store.reconcile(apps)
            store.scanUsage()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tracker.refresh()
        scope.launch(Dispatchers.IO) {
            store.reconcile(catalog.apps.value)
            store.scanUsage()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        gridOpen = false
        focusManager.clearFocus()
    }
    // Leaving the page (pivot back to Start) resets search and the grid so Back on Start stays Start's.
    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            gridOpen = false
            focusManager.clearFocus()
        }
    }
    BackHandler(enabled = visible && gridOpen) { gridOpen = false }
    BackHandler(enabled = visible && !gridOpen && query.isNotEmpty()) {
        query = ""
        focusManager.clearFocus()
    }

    val launch: (AppEntry, Rect?) -> Unit = { entry, bounds ->
        focusManager.clearFocus()
        store.markLaunched(entry)
        launchCallback(entry, bounds)
    }
    val openGrid = {
        focusManager.clearFocus()
        gridOpen = true
        Diagnostics.add("applist", "jump grid opened")
    }

    Box(
        Modifier
            .fillMaxSize()
            .testTag("app_list")
            .onGloballyPositioned { c ->
                val width = c.size.width
                val now = width > 0 && c.boundsInWindow().width >= width * 0.5f
                if (now != visible) visible = now
            },
    ) {
        Column(Modifier.fillMaxSize().padding(top = AppListMetrics.TOP)) {
            SearchBox(query) {
                query = it
                gridOpen = false
                scope.launch { searchState.scrollToItem(0) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (query.isBlank()) {
                    AzList(model, listState, newKeys, icons, generation, launch, openGrid, tracker)
                } else {
                    val results = remember(model, query) { AppIndex.search(model.searchable, { it.normalizedLabel }, query) }
                    LazyColumn(
                        state = searchState,
                        modifier = Modifier.fillMaxSize().testTag("applist_results"),
                        contentPadding = WindowInsets.ime.asPaddingValues(),
                    ) {
                        items(results, key = { it.key }) { item -> AppRow(item, item.newKey in newKeys, icons, generation, launch) }
                    }
                }
                if (gridOpen) {
                    JumpGrid(model.cells, onPick = { cell ->
                        val index = cell.targetIndex
                        gridOpen = false
                        if (index != null) {
                            scope.launch { listState.scrollToItem(index) }
                            Diagnostics.add("applist", "jump to ${cell.id} (item $index)")
                        }
                    }, onDismiss = { gridOpen = false })
                }
            }
        }
    }
}

@Composable
private fun AzList(
    model: AppListModel,
    state: LazyListState,
    newKeys: Set<String>,
    icons: IconMemo,
    generation: Int,
    launch: (AppEntry, Rect?) -> Unit,
    openGrid: () -> Unit,
    tracker: ProfileTracker,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("applist_list"),
        contentPadding = WindowInsets.ime.asPaddingValues(),
    ) {
        items(model.items, key = { it.key }, contentType = { it.contentType }) { item ->
            when (item) {
                is LetterHeaderItem -> LetterHeader(item.letter, openGrid)
                is ProfileHeaderItem -> ProfileHeader(item.profile, openGrid) { tracker.requestQuietMode(item.profile, true) }
                is UnlockItem -> UnlockRow(item.profile) { tracker.requestQuietMode(item.profile, false) }
                is AppRowItem -> AppRow(item, item.newKey in newKeys, icons, generation, launch)
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val colors = LocalShellColors.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // Focused W10 text box: the theme's foreground fills the box and the text takes the background colour.
    val fg = if (focused) colors.background else colors.text
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = ShellType.body.copy(color = fg),
        cursorBrush = SolidColor(fg),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .padding(horizontal = AppListMetrics.SEARCH_SIDE)
            .fillMaxWidth()
            .height(AppListMetrics.SEARCH_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .testTag("applist_search"),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (focused) colors.text else Color.Transparent)
                    .border(Dp.Hairline, if (focused) colors.accent else colors.subtleText),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .padding(start = AppListMetrics.SEARCH_INSET, end = AppListMetrics.SEARCH_INSET + AppListMetrics.SEARCH_GLYPH_EPX.dp + 4.dp),
                ) {
                    if (query.isEmpty()) {
                        BasicText("Search", style = ShellType.body.copy(color = if (focused) fg.copy(alpha = 0.6f) else colors.subtleText))
                    }
                    inner()
                }
                BasicText(
                    Glyph.SEARCH,
                    style = glyphStyle(fg, AppListMetrics.SEARCH_GLYPH_EPX),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = AppListMetrics.SEARCH_INSET),
                )
            }
        },
    )
}

@Composable
private fun LetterHeader(letter: String, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(onClick, Modifier.fillMaxWidth().height(AppListMetrics.HEADER_BLOCK).testTag("applist_header:$letter")) {
        BasicText(
            letter,
            style = ShellType.title.copy(color = colors.text),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = AppListMetrics.HEADER_X),
        )
    }
}

@Composable
private fun ProfileHeader(profile: ShellProfile, onOpenGrid: () -> Unit, onLock: () -> Unit) {
    val colors = LocalShellColors.current
    Row(Modifier.fillMaxWidth().height(AppListMetrics.HEADER_BLOCK)) {
        PressRow(onOpenGrid, Modifier.weight(1f).fillMaxHeight().testTag("profile_group:${profile.tagName}")) {
            BasicText(
                profileGlyph(profile.kind),
                style = glyphStyle(colors.text, AppListMetrics.HEADER_GLYPH_EPX),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = AppListMetrics.HEADER_X),
            )
        }
        if (!profile.quiet) {
            // P4 design: the group's own lock action (quiet mode on).
            PressRow(onLock, Modifier.fillMaxHeight().testTag("profile_lock:${profile.tagName}")) {
                BasicText(
                    "Lock",
                    style = ShellType.body.copy(color = colors.accent),
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun UnlockRow(profile: ShellProfile, onUnlock: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(onUnlock, Modifier.fillMaxWidth().height(AppListMetrics.ROW).testTag("profile_unlock:${profile.tagName}")) {
        RowLayout(
            icon = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(Glyph.LOCK, style = glyphStyle(colors.subtleText, AppListMetrics.HEADER_GLYPH_EPX))
                }
            },
            name = { BasicText("Tap to unlock", style = ShellType.body.copy(color = colors.subtleText), maxLines = 1) },
            caption = null,
        )
    }
}

@Composable
private fun AppRow(item: AppRowItem, isNew: Boolean, icons: IconMemo, generation: Int, onLaunch: (AppEntry, Rect?) -> Unit) {
    val colors = LocalShellColors.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val pkg = item.entry.component.packageName
    PressRow(
        onClick = {
            val bounds = coordinates[0]?.takeIf { it.isAttached }?.boundsInWindow()
            onLaunch(item.entry, bounds?.let { Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) })
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(AppListMetrics.ROW)
            .onPlaced { coordinates[0] = it }
            .testTag("applist_row:$pkg"),
    ) {
        RowLayout(
            icon = { AppIcon(item.entry, icons, generation) },
            name = {
                BasicText(item.entry.label, style = ShellType.body.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            caption = if (isNew) {
                { BasicText("New", style = ShellType.caption.copy(color = colors.accent), maxLines = 1, modifier = Modifier.testTag("applist_new:$pkg")) }
            } else {
                null
            },
        )
    }
}

/** Icon at x 5 centred in the 44-epx row; name (and caption) placed by baseline per R6 §5.1.5. */
@Composable
private fun RowLayout(icon: @Composable () -> Unit, name: @Composable () -> Unit, caption: (@Composable () -> Unit)?) {
    Layout(
        content = {
            icon()
            name()
            caption?.invoke()
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val iconSize = AppListMetrics.ICON.roundToPx()
        val iconPlaceable = measurables[0].measure(Constraints.fixed(iconSize, iconSize))
        val textX = AppListMetrics.TEXT_X.roundToPx()
        val textWidth = (width - textX - AppListMetrics.TEXT_END.roundToPx()).coerceAtLeast(0)
        val namePlaceable = measurables[1].measure(Constraints(maxWidth = textWidth))
        val captionPlaceable = measurables.getOrNull(2)?.measure(Constraints(maxWidth = textWidth))
        layout(width, height) {
            iconPlaceable.place(AppListMetrics.ICON_X.roundToPx(), ((height - iconSize) / 2f).roundToInt())
            val nameBaseline = if (captionPlaceable == null) AppListMetrics.NAME_BASELINE else AppListMetrics.NAME_BASELINE_CAPTIONED
            namePlaceable.place(textX, (nameBaseline.dp.toPx() - namePlaceable[FirstBaseline]).roundToInt())
            captionPlaceable?.place(textX, (AppListMetrics.CAPTION_BASELINE.dp.toPx() - captionPlaceable[FirstBaseline]).roundToInt())
        }
    }
}

@Composable
private fun AppIcon(entry: AppEntry, icons: IconMemo, generation: Int) {
    val colors = LocalShellColors.current
    val bitmap by produceState(icons.peek(entry, generation), entry.key, generation) {
        icons.load(entry)?.let { value = it }
    }
    Box(Modifier.fillMaxSize()) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        if (entry.profile != ProfileKind.MAIN) {
            // P4 design: the profile's glyph replaces Android's badge in the icon corner.
            Box(
                Modifier.align(Alignment.BottomEnd).size(AppListMetrics.CORNER_PLATE).background(colors.chrome),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(profileGlyph(entry.profile), style = glyphStyle(colors.text, AppListMetrics.CORNER_GLYPH_EPX))
            }
        }
    }
}

/** X8 jump grid over the list area below the search box: "#", A-Z (letters without apps dimmed), then profile glyph cells. */
@Composable
private fun JumpGrid(cells: List<JumpCell>, onPick: (JumpCell) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = AppListMetrics.GRID_SCRIM_ALPHA))
            .testTag("jump_grid")
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onDismiss()
                }
            },
    ) {
        Column(Modifier.padding(start = AppListMetrics.ICON_X)) {
            for (row in cells.chunked(AppListMetrics.GRID_COLUMNS)) {
                Row {
                    for (cell in row) JumpCellView(cell, onPick)
                }
            }
        }
    }
}

@Composable
private fun JumpCellView(cell: JumpCell, onPick: (JumpCell) -> Unit) {
    val colors = LocalShellColors.current
    val enabled = cell.targetIndex != null
    val color = if (enabled) colors.text else colors.text.copy(alpha = AppListMetrics.GRID_DIM_ALPHA)
    val modifier = Modifier.size(AppListMetrics.GRID_PITCH).testTag("jump_cell:${cell.id}")
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                cell.label,
                style = if (cell.isGlyph) {
                    glyphStyle(color, AppListMetrics.GRID_GLYPH_EPX)
                } else {
                    ShellType.title.copy(fontSize = AppListMetrics.GRID_LETTER_EPX.sp, lineHeight = 32.sp, color = color)
                },
            )
        }
    }
    if (enabled) PressRow({ onPick(cell) }, modifier) { content() } else Box(modifier) { content() }
}

/**
 * Icons load off the main thread. After a catalog refresh (new [generation]) the previous generation's bitmap stays on
 * screen while the row reloads; only two generations are held, so stale bitmaps are released on the next refresh.
 */
private class IconMemo(private val catalog: AppCatalog, private val sizePx: Int) {
    @Volatile private var generation = 0
    @Volatile private var current = ConcurrentHashMap<String, ImageBitmap>()
    @Volatile private var previous = ConcurrentHashMap<String, ImageBitmap>()

    /** Main thread (composition). */
    fun peek(entry: AppEntry, gen: Int): ImageBitmap? {
        if (gen != generation) {
            previous = current
            current = ConcurrentHashMap()
            generation = gen
        }
        return current[entry.key] ?: previous[entry.key]
    }

    suspend fun load(entry: AppEntry): ImageBitmap? = withContext(Dispatchers.IO) {
        catalog.icon(entry, sizePx, badged = entry.profile == ProfileKind.MAIN)?.also { current[entry.key] = it }
    }
}
