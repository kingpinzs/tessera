package app.tileshell.files

import app.tileshell.brand.Glyph
import java.io.File

/** A Move to / Copy to in the making: what will be moved or copied once a folder is chosen. */
class Pick(val move: Boolean, val sources: List<File>)

/**
 * The Move to / Copy to picker (build task 3; r3 D2 (3), D12; pass 2 §1 UNMEASURED-2): Files itself in a picker mode —
 * the folder page with "Choose a folder" in the sort line's place, no checkboxes, the ≡ pane working inside it, and
 * the app bar ✓ · ✕ · ••• in the two right-most slots (150 / 82 / 24 from the right). It runs on a history of its own
 * ([FilesState.beginSubHistory]): Back walks the picker's folders, then acts as ✕.
 */
object FilesPicker {
    const val TITLE = "Choose a folder"

    /** Where the picker opens: the folder Files is in, else This Device's root (Recent and a search are not folders). */
    fun startOf(state: FilesState): FilesLocation? {
        (state.location as? FilesLocation.Folder)?.let { return it }
        val device = state.volumes.firstOrNull { it.primary } ?: state.volumes.firstOrNull() ?: return null
        return FilesLocation.Folder(device.uuid, device.root)
    }

    /** The picker's chrome. ✓ is live on a folder that is listed; the overflow holds Refresh alone. */
    fun chrome(state: FilesState, onOk: () -> Unit, onCancel: () -> Unit): FilesChrome {
        val folder = state.location is FilesLocation.Folder && state.body is PageBody.Rows
        return FilesChrome(
            Headline.Text(TITLE, "files_pick_title"),
            listOf(
                BarButton("files_pick_ok", Glyph.CHECKMARK, "Done", folder, onOk),
                BarButton("files_pick_cancel", Glyph.DISMISS, "Cancel", true, onCancel),
            ),
            listOf(MoreItem("files_more:refresh", "Refresh") { state.refresh() }),
        )
    }
}
