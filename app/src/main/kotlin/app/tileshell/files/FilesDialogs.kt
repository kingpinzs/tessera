package app.tileshell.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.ui.components.OutlinedFieldMetrics
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.tokens.ShellType

/** One button of a dialog: `files_dialog:<id>`. [run] gets the text box's text ("" when the dialog has no box). */
class DialogChoice(val id: String, val label: String, val run: (String) -> Unit)

/**
 * One dialog (Y4): rename, new folder, the delete confirmation, the conflict's three answers, the bin's "Delete
 * permanently" and "Empty". [input] is the text box's first text, null for a dialog with no box. [error] is a line
 * the dialog shows under its box when what was typed could not be used (`files_error`); the dialog stays open.
 * [onBack] is what Back does.
 */
@Stable
class FilesDialog(
    val title: String,
    val body: String? = null,
    val input: String? = null,
    val choices: List<DialogChoice>,
    val onBack: () -> Unit,
) {
    var error: String? by mutableStateOf(null)
}

/** The words of Files' dialogs (H2, H5): plain, short sentences. */
object DialogText {
    const val CANCEL = "Cancel"

    fun deleteTitle(count: Int) = if (count == 1) "Delete this item?" else "Delete these $count items?"
    const val DELETE_BODY = "You can restore it from the Recycle Bin."
    const val DELETE_BODY_MANY = "You can restore them from the Recycle Bin."
    const val DELETE = "Delete"

    fun purgeTitle(count: Int) = if (count == 1) "Permanently delete this item?" else "Permanently delete these $count items?"
    const val PURGE_BODY = "You can't undo this."

    const val EMPTY_TITLE = "Empty the Recycle Bin?"
    fun emptyBody(count: Int) = if (count == 1) "1 item will be permanently deleted. You can't undo this." else "$count items will be permanently deleted. You can't undo this."
    const val EMPTY = "Empty"

    const val RENAME = "Rename"
    const val NEW_FOLDER = "New folder"
    const val OK = "OK"
    const val NAME_NEEDED = "Type a name."

    fun conflictTitle(name: String) = "There's already an item named $name here"
    const val CONFLICT_BODY = "Replace it, keep both, or skip this one?"
    const val REPLACE = "Replace"
    const val KEEP_BOTH = "Keep both"
    const val SKIP = "Skip"

    /** A reason of the write layer ("the name is taken") as a sentence. */
    fun sentence(reason: String): String = reason.replaceFirstChar { it.uppercase() }.trimEnd('.') + "."
}

/** R7 1.3.9's top-anchored W10M dialog (Y4): full width from the top of the page, fill (74,74,74), the page under it dimmed. */
object DialogMetrics {
    val FILL = Color(0xFF4A4A4A)

    /** R7 read the dimmed page as (2,2,2) over a black page; here the page keeps showing through, darkened. */
    val DIM = Color.Black.copy(alpha = 0.6f)
    const val SIDE = 12f
    const val TOP = 24f
    const val BOTTOM = 24f
    const val BUTTON_H = 34f
    const val BUTTON_GAP = 10f
}

/**
 * The dialog layer: modal over the page (the shell's overlay rule, L13-2 / L13-3) — a tap off the dialog does nothing,
 * because every one of them asks a question that needs its answer. Tags `files_dialog`, `files_dialog_title`,
 * `files_dialog_input`, `files_dialog:<id>`.
 */
@Composable
fun FilesDialogLayer(dialog: FilesDialog?, isOpen: (FilesDialog) -> Boolean) {
    if (dialog == null) return
    OverlayLayer(active = { isOpen(dialog) }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(DialogMetrics.DIM).testTag("files_dialog_scrim").modalOverlay(onTapOff = {})) {
            DialogBox(dialog)
        }
    }
}

@Composable
private fun DialogBox(dialog: FilesDialog) {
    val first = dialog.input
    var text by remember(dialog) {
        // The whole name is selected, so typing replaces it (the form of a W10M rename box).
        mutableStateOf(TextFieldValue(first.orEmpty(), TextRange(0, first.orEmpty().length)))
    }
    val focus = remember(dialog) { FocusRequester() }
    if (first != null) LaunchedEffect(dialog) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxWidth().background(DialogMetrics.FILL).testTag("files_dialog")
            .padding(start = DialogMetrics.SIDE.dp, end = DialogMetrics.SIDE.dp, top = DialogMetrics.TOP.dp, bottom = DialogMetrics.BOTTOM.dp),
    ) {
        BasicText(dialog.title, Modifier.testTag("files_dialog_title"), style = ShellType.subtitle.copy(color = Color.White))
        dialog.body?.let {
            BasicText(it, Modifier.padding(top = 8.dp).testTag("files_dialog_body"), style = ShellType.body.copy(color = Color.White))
        }
        if (first != null) {
            // The shell's W10M text box (OutlinedField's measured form), holding a selection — which OutlinedField cannot.
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(OutlinedFieldMetrics.HEIGHT)
                    .background(OutlinedFieldMetrics.FILL).border(OutlinedFieldMetrics.BORDER, OutlinedFieldMetrics.BORDER_COLOR),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { typed ->
                        text = typed.copy(text = typed.text.replace("\n", "").take(FilePaths.NAME_MAX))
                        dialog.error = null
                    },
                    modifier = Modifier.fillMaxSize().padding(horizontal = OutlinedFieldMetrics.TEXT_INSET).focusRequester(focus).testTag("files_dialog_input"),
                    textStyle = ShellType.body.copy(color = Color.White),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onAny = { dialog.choices.first().run(text.text) }),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
                )
            }
        }
        dialog.error?.let {
            BasicText(it, Modifier.padding(top = 8.dp).testTag("files_error"), style = ShellType.body.copy(color = Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            dialog.choices.forEachIndexed { at, choice ->
                if (at > 0) Spacer(Modifier.width(DialogMetrics.BUTTON_GAP.dp))
                DialogButton(choice, Modifier.weight(1f)) { choice.run(text.text) }
            }
        }
    }
}

/** The shell's dialog button (the recorder's and Calendar's form: R7 measured none), as an item of the modal layer. */
@Composable
private fun DialogButton(choice: DialogChoice, modifier: Modifier, onRun: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.height(DialogMetrics.BUTTON_H.dp)
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
            .border(1.dp, Color.White.copy(alpha = 0.7f))
            .testTag("files_dialog:${choice.id}").semantics { role = Role.Button }
            .overlayItem(onPressedChange = { pressed = it }, onRun = onRun),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            choice.label, Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            style = ShellType.body.copy(color = Color.White, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
