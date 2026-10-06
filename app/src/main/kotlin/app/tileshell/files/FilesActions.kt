package app.tileshell.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Where a row came from: it picks the row's tag and what its detail line says. */
enum class EntrySource { FOLDER, SEARCH, RECENT, BIN }

/**
 * One row as a page draws it — a folder's entry, a search hit, a Recent entry, a binned file. [path] is where the file
 * is NOW (a binned file's path inside the bin); [detail] is the text under the name.
 */
data class FileEntry(
    val row: FileRow,
    val path: String,
    val volumeUuid: String,
    val detail: String,
    val kind: FileKind,
    val source: EntrySource = EntrySource.FOLDER,
    /** E5's second form: `Android/data` and `Android/obb`, which Android lets no app list. A tap opens nothing. */
    val unreadable: Boolean = false,
    /** A bin row's record ([RecycleBin.Entry]); null everywhere else. */
    val bin: RecycleBin.Entry? = null,
) {
    val name: String get() = row.name
    val isDirectory: Boolean get() = row.isDirectory

    /** The name's tag: `files_row:<name>`, `files_recent_row:<name>`, `files_bin_row:<name>`. */
    val rowTag: String
        get() = when (source) {
            EntrySource.RECENT -> "files_recent_row:$name"
            EntrySource.BIN -> "files_bin_row:$name"
            else -> "files_row:$name"
        }

    /** The detail line's tag: `files_detail:<name>`, or `files_unreadable` on an entry Android hides. */
    val detailTag: String get() = if (unreadable) "files_unreadable" else "files_detail:$name"
}

/**
 * A press on a row that became a hold: the row, when the finger went down (uptime — the hold menu's `[motion]` t0), and
 * where the row sits in the page's content box in epx, so a menu can hang under it (r11/files.md 1.9.5).
 */
class RowPress(val entry: FileEntry, val downUptimeMs: Long, val xEpx: Float, val rowTopEpx: Float, val rowBottomEpx: Float)

/** One app-bar button: `files_bar:<id>` (or the picker's and the selection bar's own [tag]). */
class BarButton(val tag: String, val glyph: String, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** One line of the ••• overflow: `files_more:<id>`. */
class MoreItem(val tag: String, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** What sits in the sort line's place (r11/files.md 1.4.3; pass 2 §1). */
sealed interface Headline {
    /** "Sort by: Name ⌄" (`files_sort`), or "Sort by: Relevance" while searching. */
    data object Sort : Headline

    /** Nothing: Recent with rows has no sort line (pass 2 §4.8). */
    data object None : Headline

    /**
     * A line of text in the sort line's place — by default at its left edge (12) and cap top (16 below the bar): the
     * selection count (`files_sort`), the picker's "Choose a folder" (`files_pick_title`), Recent's empty line, the
     * bin's note. It wraps when it is longer than the page is wide.
     */
    data class Text(
        val text: String,
        val tag: String,
        val color: Color = Color.White,
        val leftEpx: Float = FilesMetrics.SORT_LEFT,
        val capTopEpx: Float = FilesMetrics.SORT_CAP_TOP,
    ) : Headline
}

/**
 * A page's chrome: the line under the location bar, the app bar's buttons (drawn right-aligned at the W10M pitch, the
 * last one nearest •••) and the overflow. `FilesPage` builds the browse-mode one; [FilesActions.chrome] may return
 * another for selection mode and the picker.
 */
class FilesChrome(val headline: Headline, val buttons: List<BarButton>, val more: List<MoreItem>)

/**
 * Everything the folder page hands to the builders after it: ONE holder, every member a no-op by default. The page
 * calls these and nothing else for what it does not own, so selection, the operations, open-with, zip, the hold menu,
 * Properties and the bin's actions plug in here with no change to the page's own code. [FilesActions.None] is what
 * `FilesActivity` passes until they do.
 *
 * Every function gets the [FilesState] it acts on: the current [FilesState.location], the rows on screen
 * ([FilesState.entries]), `refresh()`, `go()`, `say()` for an error line.
 */
interface FilesActions {
    /** The app bar's Select (`files_bar:select`): enter selection mode. */
    fun onSelect(state: FilesState) {}

    /** The app bar's New folder (`files_bar:new_folder`). */
    fun onNewFolder(state: FilesState) {}

    /** The overflow's Select all (`files_more:select_all`). */
    fun onSelectAll(state: FilesState) {}

    /** The overflow's Clear selection (`files_more:clear`). */
    fun onClearSelection(state: FilesState) {}

    /** The overflow's Properties (`files_more:properties`): the open folder's, or the selection's. */
    fun onProperties(state: FilesState) {}

    /** A tap on a FILE that is still there (not a zip): open-with routing (Q4 A). */
    fun onOpenFile(state: FilesState, entry: FileEntry) {}

    /** A tap on a zip: open it as a folder (Q2 C). */
    fun onOpenZip(state: FilesState, entry: FileEntry) {}

    /** A row held for [FilesMetrics.HOLD_MS]: the tap-and-hold menu. */
    fun onHold(state: FilesState, press: RowPress) {}

    /**
     * Every row tap comes here FIRST. True = taken (selection mode toggles the row; a bin row opens nothing), and the
     * page then does nothing; false = the page's own rule (a folder opens, a file goes to [onOpenFile] / [onOpenZip]).
     */
    fun onRowTap(state: FilesState, entry: FileEntry): Boolean = false

    /** Back, before the page's own history: true when it was used up (leaving selection mode, closing a dialog). */
    fun onBack(state: FilesState): Boolean = false

    /** Whether Clear selection is live (dimmed with nothing selected — r11/files.md 1.7.5). */
    fun hasSelection(state: FilesState): Boolean = false

    /** The chrome to draw: [browse] as it is, or selection mode's / the picker's. Read in composition. */
    fun chrome(state: FilesState, browse: FilesChrome): FilesChrome = browse

    /** How far a row's icon and text sit right of their rest place, in epx (selection mode's 32). Read at layout. */
    fun rowShiftEpx(state: FilesState): Float = 0f

    /** A row's fill under its content (a selected row's accent), or null for none. */
    fun rowFill(state: FilesState, entry: FileEntry): Color? = null

    /** Drawn at a row's left edge over its full height (the selection checkbox column). */
    @Composable
    fun RowLeading(state: FilesState, entry: FileEntry) {}

    /** Drawn over the whole page (the box between the status bar and the nav bar), above everything Files draws: the hold menu, the dialogs, the progress box. */
    @Composable
    fun Overlays(state: FilesState) {}

    /** The default holder: nothing happens and nothing is logged. */
    object None : FilesActions
}
