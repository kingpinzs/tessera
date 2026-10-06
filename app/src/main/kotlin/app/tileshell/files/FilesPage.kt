package app.tileshell.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.settings.ToggleRow
import app.tileshell.ui.tokens.ShellType

/**
 * Everything Files shows once it holds All-files access (build tasks 2, 5 and 7): the location bar, the page under
 * it, the app bar, and over them the ≡ pane and whatever [FilesActions.Overlays] draws. One composable for every
 * page — a page is [FilesState.location], not an activity (r3 D12).
 */
@Composable
fun FilesPage(state: FilesState) {
    val scope = rememberCoroutineScope()
    DisposableEffect(state) {
        state.attach(scope)
        onDispose { state.detach() }
    }
    // The folder may have gone, and Recent and the bin may have changed, while Files was away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.revalidate() }
    val box = remember { FilesBox() }
    val at = state.location
    // Settings and Properties are pages with no app bar (r11/files.md 1.10.1).
    val chrome = if (at == FilesLocation.Settings || at is FilesLocation.Properties) null else state.actions.chrome(state, browseChrome(state))
    CompositionLocalProvider(LocalFilesBox provides box) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { box.coords = it }) {
            Column(Modifier.fillMaxSize()) {
                FilesLocationBar(state)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FilesPageBody(state, chrome)
                    // Under the sort line, which sits below the search box while one is open.
                    SortFlyout(state, topEpx = FilesMetrics.SORT_BLOCK - 6f + if (state.search != null) FilesMetrics.SEARCH_TOP + 32f else 0f)
                    state.notice?.let { NoticeBand(it) { state.clearNotice() } }
                }
                if (chrome != null) Spacer(Modifier.height(FilesMetrics.BAR.dp))
            }
            if (chrome != null) FilesAppBar(state, chrome)
            FilesPane(state)
            state.actions.Overlays(state)
        }
    }
}

/**
 * The browse-mode chrome of each page (r11/files.md 1.7; pass 2 §1 / §4.8):
 *  - a folder: the sort line; Select · New folder · Icons (List in the icons view) · Search · •••;
 *  - Recent: no sort line — its empty line in that place when nothing was opened; Select · Icons · Search · •••, dim when empty;
 *  - the Recycle Bin: its one-line note in that place (T18-10); the same three buttons.
 * The overflow is Refresh · Select all · Clear selection · Properties (1.7.5), then Settings (Files' own page: hidden files).
 */
private fun browseChrome(state: FilesState): FilesChrome {
    val actions = state.actions
    val at = state.location
    val rows = state.body as? PageBody.Rows
    val listed = rows != null
    val some = rows?.entries?.isNotEmpty() == true
    // A folder's buttons work whenever it is listed; Recent's and the bin's dim while there is nothing to act on.
    val live = if (at is FilesLocation.Folder) listed else some
    val icons = state.view == FilesView.LIST
    val select = BarButton("files_bar:select", FilesGlyph.SELECT, "Select", live) { actions.onSelect(state) }
    val newFolder = BarButton("files_bar:new_folder", FilesGlyph.NEW_FOLDER, "New folder", live) { actions.onNewFolder(state) }
    val view = BarButton("files_bar:view", if (icons) FilesGlyph.ICONS else FilesGlyph.LIST, if (icons) "Icons" else "List", live) { state.toggleView() }
    val search = BarButton("files_bar:search", FilesGlyph.SEARCH, "Search", live) { state.openSearch() }
    val more = listOf(
        MoreItem("files_more:refresh", "Refresh") { state.refresh() },
        MoreItem("files_more:select_all", "Select all", some) { actions.onSelectAll(state) },
        MoreItem("files_more:clear", "Clear selection", actions.hasSelection(state)) { actions.onClearSelection(state) },
        MoreItem("files_more:properties", "Properties", listed) { actions.onProperties(state) },
        MoreItem("files_more:settings", "Settings") { state.go(FilesLocation.Settings) },
    )
    return when (at) {
        is FilesLocation.Folder -> FilesChrome(if (listed) Headline.Sort else Headline.None, listOf(select, newFolder, view, search), more)
        FilesLocation.Recent -> {
            val empty = rows != null && rows.entries.isEmpty() && !state.searching
            val line = if (empty) {
                Headline.Text(RECENT_EMPTY, "files_recent_empty", FilesMetrics.RECENT_EMPTY, FilesMetrics.RECENT_EMPTY_LEFT, FilesMetrics.RECENT_EMPTY_CAP_TOP)
            } else Headline.None
            FilesChrome(line, listOf(select, view, search), more)
        }
        FilesLocation.Bin -> FilesChrome(Headline.Text(BIN_NOTE, "files_bin_note", FilesMetrics.SORT_LABEL), listOf(select, view, search), more)
        FilesLocation.Settings -> FilesChrome(Headline.None, emptyList(), emptyList())
        is FilesLocation.Properties -> FilesChrome(Headline.None, emptyList(), emptyList())
        // A zip's virtual folder is the folder page with nothing that writes: no Select, no New folder (T18-2).
        is FilesLocation.Zip -> FilesChrome(if (listed) Headline.Sort else Headline.None, listOf(view, search), more.filter { it.tag == "files_more:refresh" || it.tag == "files_more:settings" })
    }
}

/** Under the location bar: the search box when one is open, the headline, then the rows — or the page's error line. */
@Composable
private fun FilesPageBody(state: FilesState, chrome: FilesChrome?) {
    val at = state.location
    if (at == FilesLocation.Settings) {
        FilesSettingsPage(state)
        return
    }
    if (at is FilesLocation.Properties) {
        state.actions.PageContent(state, at)
        return
    }
    val body = state.body
    val search = state.search
    Column(Modifier.fillMaxSize()) {
        if (body is PageBody.Error) {
            // "This folder is gone", "This storage was removed": in the sort line's place, the bars unmoved.
            BasicText(
                body.text,
                Modifier.padding(start = FilesMetrics.SORT_LEFT.dp, end = 12.dp, top = bodyCapPad(FilesMetrics.SORT_CAP_TOP).dp).testTag("files_error"),
                style = ShellType.body.copy(color = Color.White),
            )
            return@Column
        }
        if (search != null) FilesSearchBox(state, search)
        when (val line = chrome?.headline ?: Headline.None) {
            Headline.Sort -> SortLine(state)
            Headline.None -> Unit
            is Headline.Text -> HeadlineText(line)
        }
        if (search != null && search.running) SearchProgressLine(state, search)
        val entries = state.entries
        if (state.searching && search != null && !search.running && entries.isEmpty()) {
            SearchEmptyLine()
        } else if (body is PageBody.Rows) {
            FileEntryList(state, entries, Modifier.weight(1f), listTag = if (at == FilesLocation.Bin) "files_bin" else if (at is FilesLocation.Zip) "files_zip_root" else null)
        }
    }
}

/** Files' own settings, in the shell's Settings-page row form (R3 C1): one switch. */
@Composable
private fun FilesSettingsPage(state: FilesState) {
    Column(Modifier.fillMaxSize().padding(top = 8.dp).testTag("files_settings")) {
        ToggleRow("Show hidden files", state.showHidden, "files_setting_hidden") { state.setHidden(it) }
        BasicText(
            "Files and folders whose names start with a dot.",
            Modifier.padding(start = 12.dp, end = 12.dp),
            style = ShellType.caption.copy(color = FilesMetrics.SORT_LABEL),
        )
    }
}

/** W10M's own string (pass 2 §1 UNMEASURED-5). */
const val RECENT_EMPTY = "You haven't opened any files recently."

/** T18-10: the bin page's privacy line. */
const val BIN_NOTE = "Deleted files stay on this phone until you empty the Recycle Bin"
