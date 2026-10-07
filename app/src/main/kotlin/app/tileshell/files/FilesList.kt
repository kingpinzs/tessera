package app.tileshell.files

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.cortana.ui.CortanaEaseOut
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlin.math.roundToInt

/** The page's content box (between the status bar and the nav bar): a held row reports its place inside it. */
class FilesBox {
    var coords: LayoutCoordinates? = null
}

val LocalFilesBox = staticCompositionLocalOf { FilesBox() }

/** Top padding that puts 15-epx body text's cap top [capTopEpx] below its box's top. */
internal fun bodyCapPad(capTopEpx: Float): Float = CapMetrics.topPaddingForCapTop(capTopEpx, ShellType.body.fontSize.value)

/**
 * "Sort by: Name ⌄" (r11/files.md 1.4, the 15063 form): "Sort by:" grey, the value white, a thin chevron after it; text
 * left 12, cap top 16 below the bar. One text node (`files_sort`), so its text reads "Sort by: Name". While a search
 * term is typed it reads "Sort by: Relevance" (1.4.5) and opens nothing.
 */
@Composable
fun SortLine(state: FilesState) {
    val searching = state.searching
    val value = if (searching) "Relevance" else state.sort.label
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = FilesMetrics.SORT_LABEL)) { append("Sort by: ") }
        withStyle(SpanStyle(color = Color.White)) { append(value) }
    }
    Box(Modifier.fillMaxWidth().height(FilesMetrics.SORT_BLOCK.dp)) {
        PressRow({ if (!searching) state.sortOpen = true }, Modifier.padding(start = FilesMetrics.SORT_LEFT.dp, top = bodyCapPad(FilesMetrics.SORT_CAP_TOP).dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(text, Modifier.testTag("files_sort"), style = ShellType.body, maxLines = 1)
                if (!searching) FilesGlyphText(FilesGlyph.CHEVRON_DOWN, 12f, FilesMetrics.SORT_CHEVRON, Modifier.padding(start = 6.dp).testTag("files_sort_chevron"))
            }
        }
    }
}

/** A line of text in the sort line's place (pass 2 §1): the selection count, "Choose a folder", Recent's empty line, the bin's note. */
@Composable
fun HeadlineText(line: Headline.Text) {
    Box(Modifier.fillMaxWidth().heightIn(min = FilesMetrics.SORT_BLOCK.dp)) {
        BasicText(
            line.text,
            Modifier.padding(start = line.leftEpx.dp, end = 12.dp, top = bodyCapPad(line.capTopEpx).dp, bottom = 8.dp).testTag(line.tag),
            style = ShellType.body.copy(color = line.color),
        )
    }
}

/**
 * The sort flyout (Y2, UNMEASURED-6: R7 2.2.5's menu — 242.6 wide, 44-epx items, 14-epx inset — under the line, the
 * current key in the accent): exactly Name, Date and Size; W10M had no type sort (r11/files.md 1.4.5). It grows from
 * its top edge (R7 2.2.6, Y5 stand-in) on the shell's motion clock.
 */
@Composable
fun SortFlyout(state: FilesState, topEpx: Float) {
    if (!state.sortOpen) return
    val colors = LocalShellColors.current
    var grow by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { MotionClock.animate("files_sort_open", FilesMetrics.FLYOUT_GROW_MS, CortanaEaseOut) { grow = it } }
    val keys = FileSort.entries
    val height = FilesMetrics.FLYOUT_PAD * 2 + keys.size * FilesMetrics.FLYOUT_ITEM + 2f
    OverlayLayer(active = { state.sortOpen }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().modalOverlay(onTapOff = { dismissOverlay { state.sortOpen = false } })) {
            Box(
                Modifier.offset(x = FilesMetrics.SORT_LEFT.dp, y = topEpx.dp).width(FilesMetrics.FLYOUT_W.dp)
                    // R7 2.2.6: the box is half open on its first frame and grows to its height.
                    .height((height * (0.5f + 0.5f * grow)).dp).clipToBounds()
                    .background(FilesMetrics.FLYOUT_FILL).border(1.dp, FilesMetrics.FLYOUT_BORDER)
                    .testTag("files_sort_flyout").overlayItem(onPressedChange = {}, onRun = {}),
            ) {
                Column(Modifier.padding(top = (1f + FilesMetrics.FLYOUT_PAD).dp)) {
                    for (key in keys) {
                        var pressed by remember { mutableStateOf(false) }
                        Box(
                            Modifier.fillMaxWidth().height(FilesMetrics.FLYOUT_ITEM.dp)
                                .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
                                .testTag("files_sort_item:${key.name.lowercase()}").semantics { role = Role.Button }
                                .overlayItem(onPressedChange = { pressed = it }, onRun = { dismissOverlay { state.sortBy(key) } }),
                        ) {
                            BasicText(
                                key.label, Modifier.align(Alignment.CenterStart).padding(start = FilesMetrics.FLYOUT_INSET.dp),
                                style = ShellType.body.copy(color = if (key == state.sort) colors.accent else Color.White), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The rows of a page as a list (64-epx two-line rows) or as icons (three per row) — one list for every page: a folder,
 * a search's hits, Recent, the bin, and later a zip's virtual root. Lazy, so a folder of 10,000 entries (or a zip of
 * 70,000) composes only what is on screen.
 *
 * @param listTag a tag for the list itself (`files_bin` on the bin page), or null
 */
@Composable
fun FileEntryList(state: FilesState, entries: List<FileEntry>, modifier: Modifier = Modifier, listTag: String? = null) {
    val tagged = if (listTag != null) modifier.testTag(listTag) else modifier
    val wanted = state.showName
    if (state.view == FilesView.LIST) {
        val list = remember(state.listEpoch) { LazyListState() }
        LaunchedEffect(wanted, entries) {
            if (wanted != null && entries.isNotEmpty()) {
                val at = entries.indexOfFirst { it.name == wanted }
                if (at >= 0) list.scrollToItem(at)
                state.showName = null
            }
        }
        LazyColumn(tagged.fillMaxSize(), state = list) {
            items(entries, key = { it.path }, contentType = { "row" }) { entry -> FileListRow(state, entry) }
        }
    } else {
        val grid = remember(state.listEpoch) { LazyGridState() }
        LaunchedEffect(wanted, entries) {
            if (wanted != null && entries.isNotEmpty()) {
                val at = entries.indexOfFirst { it.name == wanted }
                if (at >= 0) grid.scrollToItem(at)
                state.showName = null
            }
        }
        LazyVerticalGrid(GridCells.Fixed(FilesMetrics.GRID_COLUMNS), tagged.fillMaxSize(), state = grid) {
            items(entries, key = { it.path }, contentType = { "cell" }) { entry -> FileGridCell(state, entry) }
        }
    }
}

/** A row's entrance (pass 2 §5 row 4.3): everything slides up from 7.2 epx below rest; names lead, detail lines and icons follow. */
private object Entrance {
    fun offsetEpx(t: Float): Float = FilesMetrics.FOLDER_ENTER_OFFSET * (1f - FilesEasing.FolderEnter.transform(t.coerceAtLeast(0f)))
    fun name(t: Float): Float = if (t < 0f) 0f else (0.3f + 0.7f * t / FilesMetrics.ENTER_DETAIL_AT).coerceIn(0f, 1f)
    fun detail(t: Float): Float = ((t - FilesMetrics.ENTER_DETAIL_AT) / (1f - FilesMetrics.ENTER_DETAIL_AT) * 2f).coerceIn(0f, 1f)
    fun icon(t: Float): Float = ((t - FilesMetrics.ENTER_ICON_AT) / (1f - FilesMetrics.ENTER_ICON_AT) * 2f).coerceIn(0f, 1f)
}

/**
 * A row's touch: the pressed fill from the press frame (pass 2 §4.7), a tap on a lift, and a hold once the finger has
 * stayed down [FilesMetrics.HOLD_MS] (pass 2 §5 row 4.5) — the hold menu's builder gets the press's time and the row's
 * place. A drag (the list scrolling) ends the press and runs nothing.
 */
@Composable
private fun Modifier.rowTouch(state: FilesState, entry: FileEntry, onPressed: (Boolean) -> Unit): Modifier {
    val current by rememberUpdatedState(entry)
    val pressed by rememberUpdatedState(onPressed)
    val box = LocalFilesBox.current
    val density = LocalDensity.current.density
    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { place[0] = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val downAt = SystemClock.uptimeMillis()
            pressed(true)
            var held = false
            val up = try {
                withTimeout(FilesMetrics.HOLD_MS) { waitForUpOrCancellation() }
            } catch (e: PointerEventTimeoutCancellationException) {
                held = true
                null
            }
            if (held) {
                val row = place[0]
                val root = box.coords
                val rect = if (row != null && root != null && row.isAttached && root.isAttached) root.localBoundingBoxOf(row) else null
                state.actions.onHold(
                    state,
                    RowPress(current, downAt, down.position.x / density + (rect?.left ?: 0f) / density, (rect?.top ?: 0f) / density, (rect?.bottom ?: 0f) / density),
                )
                // The rest of this gesture belongs to the hold: nothing under the finger taps when it lifts.
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
                pressed(false)
            } else {
                pressed(false)
                if (up != null) {
                    up.consume()
                    state.tap(current)
                }
            }
        }
    }
}

/** One 64-epx list row (r11/files.md 1.5; pass 2 §1): the icon or thumbnail at left 20, the name at left 72, the detail line under it. */
@Composable
fun FileListRow(state: FilesState, entry: FileEntry) {
    var pressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val fill = state.actions.rowFill(state, entry) ?: if (pressed) FilesMetrics.ROW_PRESSED else Color.Transparent
    Box(Modifier.fillMaxWidth().height(FilesMetrics.ROW.dp).background(fill).rowTouch(state, entry) { pressed = it }) {
        state.actions.RowLeading(state, entry)
        Box(
            Modifier.fillMaxSize()
                .offset { IntOffset((state.actions.rowShiftEpx(state) * density).roundToInt(), 0) }
                .graphicsLayer { translationY = Entrance.offsetEpx(state.enter.floatValue) * density },
        ) {
            val iconTop = ((FilesMetrics.ROW - FilesMetrics.ICON_H) / 2f).dp
            Box(
                Modifier.offset(x = FilesMetrics.ICON_LEFT.dp, y = iconTop).testTag("files_icon:${entry.name}")
                    .graphicsLayer { alpha = Entrance.icon(state.enter.floatValue) },
            ) {
                FileIconOrThumb(
                    entry, thumbPx = (FilesMetrics.THUMB * density).roundToInt(),
                    icon = { FileTypeIcon(entry.kind, Modifier.size(FilesMetrics.ICON_W.dp, FilesMetrics.ICON_H.dp)) },
                    thumb = { ThumbImage(it, Modifier.size(FilesMetrics.THUMB.dp)) },
                )
            }
            BasicText(
                entry.name,
                Modifier.padding(start = FilesMetrics.NAME_LEFT.dp, end = 12.dp, top = FilesMetrics.NAME_TOP.dp).testTag(entry.rowTag)
                    .graphicsLayer { alpha = Entrance.name(state.enter.floatValue) },
                style = ShellType.body.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                entry.detail,
                Modifier.padding(start = FilesMetrics.NAME_LEFT.dp, end = 12.dp, top = FilesMetrics.DETAIL_TOP.dp).testTag(entry.detailTag)
                    .graphicsLayer { alpha = Entrance.detail(state.enter.floatValue) },
                style = ShellType.body.copy(color = FilesMetrics.DETAIL), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One cell of the Icons view (r11/files.md 1.6; pass 2 §4.6): a third of the width, a 96-epx thumbnail or a 67 × 84
 * type icon, the name centred under it in up to two lines at a 20-epx pitch; the rows 172 epx apart.
 */
@Composable
fun FileGridCell(state: FilesState, entry: FileEntry) {
    var pressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val fill = state.actions.rowFill(state, entry) ?: if (pressed) FilesMetrics.ROW_PRESSED else Color.Transparent
    Box(Modifier.fillMaxWidth().height(FilesMetrics.GRID_ROW.dp).background(fill).rowTouch(state, entry) { pressed = it }) {
        Column(
            Modifier.fillMaxSize().padding(top = FilesMetrics.GRID_TOP_PAD.dp)
                .graphicsLayer { translationY = Entrance.offsetEpx(state.enter.floatValue) * density },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(FilesMetrics.GRID_THUMB.dp).testTag("files_icon:${entry.name}").graphicsLayer { alpha = Entrance.icon(state.enter.floatValue) },
                contentAlignment = Alignment.Center,
            ) {
                FileIconOrThumb(
                    entry, thumbPx = (FilesMetrics.GRID_THUMB * density).roundToInt(),
                    icon = { FileTypeIcon(entry.kind, Modifier.size(FilesMetrics.GRID_ICON_W.dp, FilesMetrics.GRID_ICON_H.dp)) },
                    thumb = { ThumbImage(it, Modifier.fillMaxSize()) },
                )
            }
            BasicText(
                entry.name,
                Modifier.padding(start = 4.dp, end = 4.dp, top = (FilesMetrics.GRID_LABEL_GAP - 4f).dp).testTag(entry.rowTag)
                    .graphicsLayer { alpha = Entrance.name(state.enter.floatValue) },
                style = ShellType.caption.copy(color = FilesMetrics.GRID_LABEL, lineHeight = FilesMetrics.GRID_LABEL_LINE.sp, textAlign = TextAlign.Center),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        // Over the cell: its content does not move aside for the checkbox as a list row's does.
        state.actions.RowLeading(state, entry)
    }
}

/** A drawn-over-the-page line (`files_error`, or why an entry cannot be read): under the location bar, until it is tapped or times out. */
@Composable
fun BoxScope.NoticeBand(notice: Notice, onDismiss: () -> Unit) {
    LaunchedEffect(notice) {
        kotlinx.coroutines.delay(NOTICE_MS)
        onDismiss()
    }
    PressRow(onDismiss, Modifier.align(Alignment.TopStart).fillMaxWidth().background(FilesMetrics.BAR_FILL)) {
        BasicText(notice.text, Modifier.padding(horizontal = 12.dp, vertical = 10.dp).testTag(notice.tag), style = ShellType.body.copy(color = Color.White))
    }
}

private const val NOTICE_MS = 8_000L
