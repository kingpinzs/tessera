package app.tileshell.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.tokens.ShellType

/**
 * The search box (r11/files.md 1.11.2): 32 epx tall, 12 below the location bar, 12-epx side margins, a 2-epx grey
 * border — the shell's W10M text box. It takes the keyboard as it opens; every change of its text starts the walk
 * again ([FilesState.setQuery]).
 */
@Composable
fun FilesSearchBox(state: FilesState, search: SearchState) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxWidth().padding(start = FilesMetrics.SEARCH_SIDE.dp, end = FilesMetrics.SEARCH_SIDE.dp, top = FilesMetrics.SEARCH_TOP.dp)) {
        OutlinedField(
            value = search.query,
            onValueChange = { state.setQuery(it) },
            tag = "files_search_box",
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            maxLength = 255,
            placeholder = "Search",
            imeAction = ImeAction.Search,
            onImeAction = { keyboard?.hide() },
        )
    }
}

/** While the walk runs: what it is doing, and the way to stop it (`files_search_progress`, `files_search_cancel`; E8). */
@Composable
fun SearchProgressLine(state: FilesState, search: SearchState) {
    val colors = LocalShellColors.current
    Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            if (search.folders == 0) "Searching…" else "Searching… ${search.folders} ${if (search.folders == 1) "folder" else "folders"}",
            Modifier.weight(1f).testTag("files_search_progress"), style = ShellType.body.copy(color = FilesMetrics.SORT_LABEL), maxLines = 1,
        )
        Spacer(Modifier.padding(start = 12.dp))
        BasicText(
            "Cancel",
            Modifier.testTag("files_search_cancel").semantics { role = Role.Button }
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.cancelSearch() }
                .padding(vertical = 6.dp),
            style = ShellType.body.copy(color = colors.accent), maxLines = 1,
        )
    }
}

/** A term with no match, once the walk has ended (`files_search_empty`). */
@Composable
fun SearchEmptyLine() {
    BasicText(
        SEARCH_EMPTY,
        Modifier.padding(start = FilesMetrics.SORT_LEFT.dp, top = 8.dp, end = 12.dp).testTag("files_search_empty"),
        style = ShellType.body.copy(color = Color.White),
    )
}

const val SEARCH_EMPTY = "No items match your search."
