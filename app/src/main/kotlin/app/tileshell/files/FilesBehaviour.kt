package app.tileshell.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import app.tileshell.brand.Glyph
import app.tileshell.ui.LocalShellColors
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What build tasks 4 and 10 add beside [FilesBehaviour]: opening a file in an app, and Recent's own hold menu. Both
 * are no-ops until they are built; [FilesBehaviour] hands them everything else they need ([FilesBehaviour.showMenu],
 * [FilesBehaviour.share], [FilesBehaviour.showProperties]).
 */
interface FilesOpening {
    /** A tap on a file that is still there and is not a zip (Q4 A). */
    fun open(state: FilesState, entry: FileEntry) {}

    /** A hold on a row of the Recent page: Remove from recent · Share · Properties. */
    fun holdRecent(behaviour: FilesBehaviour, state: FilesState, press: RowPress) {}

    object None : FilesOpening
}

/**
 * Files' behaviour beyond browsing (build tasks 3, 8 and 9): selection mode, the tap-and-hold menu, Move to / Copy to
 * with the picker, the progress box, the dialogs, rename, new folder, delete into the Recycle Bin, share, the
 * Properties page, the bin page's actions, and zip. It is the ONE [FilesActions] `FilesActivity` passes to
 * [FilesState]; each part lives in its own file and this class joins them.
 *
 * Every write goes through [FilesEnv] (the quick ones) or [FileOpsClient] (the long ones), so nothing here touches a
 * file itself: what may be written or deleted is the write layer's rule, not a screen's.
 */
class FilesBehaviour(val activity: FilesActivity, private val opening: FilesOpening = FilesOpening.None) : FilesActions {
    private val app = activity.applicationContext
    private val scope = activity.lifecycleScope

    val selection = FilesSelection()
    private val zips = ZipSessions { FilesEnv.ops(app) }

    /** The dialog on screen (the copy service's conflict question is drawn from its own state, over this). */
    private var dialog: FilesDialog? by mutableStateOf(null)
    private var menu: HoldMenu? by mutableStateOf(null)
    private var pick: Pick? by mutableStateOf(null)

    /** The theme's accent, as the page's composition last read it: a selected row's fill. */
    private var accent: Color = Color.Unspecified

    init {
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            // The activity is going: every open zip is closed and a nested zip's copy removed.
            if (event == Lifecycle.Event.ON_DESTROY) Thread { zips.retain(null) }.start()
        })
    }

    // ------------------------------------------------------------------------------------------ the page's calls

    override fun onSelect(state: FilesState) = selection.enter()

    override fun onSelectAll(state: FilesState) {
        selection.enter()
        selection.set(state.entries.filterNot { it.unreadable }.map { it.path })
    }

    override fun onClearSelection(state: FilesState) = selection.set(emptyList())

    override fun hasSelection(state: FilesState): Boolean = selection.active && selection.paths.isNotEmpty()

    override fun onNewFolder(state: FilesState) {
        val at = state.location as? FilesLocation.Folder ?: return
        ask { d ->
            FilesDialog(
                DialogText.NEW_FOLDER, input = DialogText.NEW_FOLDER,
                choices = listOf(
                    DialogChoice("ok", DialogText.OK) { typed ->
                        named(d(), typed) { name -> FilesEnv.newFolder(app, File(at.path), name).also { if (it is OpResult.Done) state.showName = name } }
                            ?.invokeOnCompletion { state.refresh() }
                    },
                    DialogChoice("cancel", DialogText.CANCEL) { close(d()) },
                ),
                onBack = { close(d()) },
            )
        }
    }

    /** The overflow's Properties: the one selected item's, else the open folder's. */
    override fun onProperties(state: FilesState) {
        selection.of(state.entries).singleOrNull()?.let { return showProperties(state, it) }
        val at = state.location as? FilesLocation.Folder ?: return
        val name = if (at.path == state.rootOf(at.volumeUuid)) state.labelOf(at.volumeUuid) ?: at.path else at.path.substringAfterLast('/')
        state.go(FilesLocation.Properties(at.volumeUuid, at.path, name, isDirectory = true))
    }

    override fun onOpenFile(state: FilesState, entry: FileEntry) = opening.open(state, entry)

    override fun onOpenZip(state: FilesState, entry: FileEntry) {
        val to = FilesLocation.Zip(entry.volumeUuid, entry.path)
        scope.launch {
            // Opened here first, so a zip that cannot be opened says so over the folder it lies in.
            when (val got = withContext(Dispatchers.IO) { zips.archive(to) }) {
                is ZipSessions.Got.Open -> state.go(to)
                is ZipSessions.Got.Not -> state.error(got.text)
            }
        }
    }

    override fun onHold(state: FilesState, press: RowPress) {
        if (pick != null || selection.active || press.entry.unreadable) return
        when (press.entry.source) {
            EntrySource.RECENT -> opening.holdRecent(this, state, press)
            // A bin row acts through the bin's own bar; nothing inside a zip can be changed.
            EntrySource.BIN, EntrySource.ZIP -> Unit
            EntrySource.FOLDER, EntrySource.SEARCH -> {
                val e = press.entry
                val one = listOf(e)
                showMenu(
                    state, press,
                    listOfNotNull(
                        HoldItem("delete", "Delete") { delete(state, one) },
                        HoldItem("move", "Move to") { startPick(state, move = true, one) },
                        HoldItem("copy", "Copy to") { startPick(state, move = false, one) },
                        // r11/files.md 1.9.2: a folder's menu has no Share.
                        if (e.isDirectory) null else HoldItem("share", "Share") { share(state, one) },
                        HoldItem("rename", "Rename") { rename(state, e) },
                        HoldItem("properties", "Properties") { showProperties(state, e) },
                    ),
                )
            }
        }
    }

    override fun onRowTap(state: FilesState, entry: FileEntry): Boolean {
        if (entry.source == EntrySource.ZIP) {
            tapInZip(state, entry)
            return true
        }
        // In the picker a folder opens (the page's own rule); nothing else does anything.
        if (pick != null) return !entry.isDirectory || entry.source == EntrySource.BIN
        if (selection.active) {
            selection.toggle(entry.path)
            return true
        }
        return false
    }

    override fun onBack(state: FilesState): Boolean {
        val op = FileOpsClient.state.value
        if (op is OpState.AwaitingConflict) {
            op.answer(Conflict.SKIP)
            return true
        }
        dialog?.let {
            it.onBack()
            return true
        }
        if (menu != null) {
            menu = null
            return true
        }
        // No cancel inside the app (r3 D2 (4)): Back waits with the page while files are written.
        if (opActive(op)) return true
        if (pick != null) {
            // The picker's own folders first (the page walks its history); at its first one Back is ✕.
            if (state.historyDepth > 0) return false
            cancelPick(state)
            return true
        }
        if (selection.active) {
            selection.leave()
            return true
        }
        return false
    }

    override fun onLocation(state: FilesState, from: FilesLocation, to: FilesLocation) {
        selection.drop()
        menu = null
        val keep = to as? FilesLocation.Zip
        if (from is FilesLocation.Zip) scope.launch(Dispatchers.IO) { zips.retain(keep) }
    }

    override fun onReset(state: FilesState) {
        pick = null
        dialog = null
        menu = null
        selection.drop()
    }

    override fun rows(state: FilesState, at: FilesLocation): PageBody =
        if (at is FilesLocation.Zip) zips.rows(at, ZoneId.systemDefault()) else PageBody.Error(FilesState.FOLDER_GONE)

    // ------------------------------------------------------------------------------------------ chrome and rows

    override fun chrome(state: FilesState, browse: FilesChrome): FilesChrome {
        val at = state.location
        if (pick != null) return FilesPicker.chrome(state, onOk = { confirmPick(state) }, onCancel = { cancelPick(state) })
        if (selection.active) {
            val chosen = selection.of(state.entries)
            return if (at == FilesLocation.Bin) binSelectionChrome(state, chosen) else selectionChrome(state, chosen)
        }
        return when (at) {
            FilesLocation.Bin -> {
                val some = (state.body as? PageBody.Rows)?.entries?.isNotEmpty() == true
                FilesChrome(browse.headline, browse.buttons, browse.more.filterNot { it.tag == "files_more:properties" } + emptyItem(state, some))
            }
            is FilesLocation.Zip -> {
                // Extract, in the bar's first slot. A nested zip is a copy in the shell's own folder: it is browsed, not extracted.
                val can = at.chain.isEmpty() && state.body is PageBody.Rows
                FilesChrome(browse.headline, listOf(BarButton("files_extract", GLYPH_EXTRACT, "Extract", can) { extract(state, at) }) + browse.buttons, browse.more)
            }
            else -> browse
        }
    }

    /** r11/files.md 1.8: the count in the sort line's place; Delete · Move to · Copy to · Share; the selection's overflow. */
    private fun selectionChrome(state: FilesState, chosen: List<FileEntry>): FilesChrome {
        val some = chosen.isNotEmpty()
        val one = chosen.singleOrNull()
        val folder = state.location as? FilesLocation.Folder
        return FilesChrome(
            Headline.Text(FileListing.selectedText(chosen.size), "files_sort"),
            listOf(
                BarButton("files_sel:delete", Glyph.DELETE, "Delete", some) { delete(state, chosen) },
                BarButton("files_sel:move", GLYPH_MOVE, "Move to", some) { startPick(state, move = true, chosen) },
                BarButton("files_sel:copy", GLYPH_COPY, "Copy to", some) { startPick(state, move = false, chosen) },
                // 1.8.4: Share dims once a folder is in the selection.
                BarButton("files_sel:share", Glyph.SHARE, "Share", some && chosen.none { it.isDirectory }) { share(state, chosen) },
            ),
            listOf(
                MoreItem("files_more:select_all", "Select all", state.entries.isNotEmpty()) { onSelectAll(state) },
                MoreItem("files_more:clear", "Clear selection", some) { onClearSelection(state) },
                MoreItem("files_more:rename", "Rename", one != null) { one?.let { rename(state, it) } },
                MoreItem("files_more:properties", "Properties", one != null) { one?.let { showProperties(state, it) } },
                // P4 (T18-2): W10M had no zip, so "Create zip" takes the overflow's last line — the bar stays W10M's four.
                MoreItem("files_zip_create", "Create zip", some && folder != null) { folder?.let { createZip(state, it, chosen) } },
            ),
        )
    }

    /** Y6: the bin's rows select like any list; its bar is Restore · Delete, and Empty is in the overflow. */
    private fun binSelectionChrome(state: FilesState, chosen: List<FileEntry>): FilesChrome {
        val some = chosen.isNotEmpty()
        return FilesChrome(
            Headline.Text(FileListing.selectedText(chosen.size), "files_sort"),
            listOf(
                BarButton("files_bin_restore", GLYPH_RESTORE, "Restore", some) { binRestore(state, chosen) },
                BarButton("files_bin_delete", Glyph.DELETE, "Delete", some) { binPurge(state, chosen) },
            ),
            listOf(
                MoreItem("files_more:select_all", "Select all", state.entries.isNotEmpty()) { onSelectAll(state) },
                MoreItem("files_more:clear", "Clear selection", some) { onClearSelection(state) },
                emptyItem(state, state.entries.isNotEmpty()),
            ),
        )
    }

    private fun emptyItem(state: FilesState, some: Boolean) = MoreItem("files_bin_empty", "Empty Recycle Bin", some) { binEmpty(state) }

    override fun rowShiftEpx(state: FilesState): Float = selection.slide.floatValue * SelectionMetrics.SHIFT

    override fun rowFill(state: FilesState, entry: FileEntry): Color? =
        if (selection.active && entry.path in selection.paths) accent.takeIf { it != Color.Unspecified } else null

    @Composable
    override fun RowLeading(state: FilesState, entry: FileEntry) = SelectionCheck(selection, entry, grid = state.view == FilesView.ICONS)

    @Composable
    override fun PageContent(state: FilesState, at: FilesLocation) {
        if (at is FilesLocation.Properties) FilesPropertiesPage(state, at)
    }

    @Composable
    override fun Overlays(state: FilesState) {
        accent = LocalShellColors.current.accent
        val op by FileOpsClient.state.collectAsState()
        selection.Motion()
        LaunchedEffect(op) { (op as? OpState.Finished)?.let { finished(state, it) } }
        FilesHoldMenuLayer(menu, isOpen = { menu === it }, onDismiss = { menu = null })
        FilesProgressLayer(op)
        // The copy service's question (r3 D2: R7's dialog stays for the conflict), drawn straight from its state: it
        // is gone the moment the operation goes on, whoever answered — this dialog or the notification's Cancel.
        val asking = op as? OpState.AwaitingConflict
        val conflict = remember(asking) { asking?.let { a -> conflictDialog(a.file.name) { a.answer(it) } } }
        if (conflict != null) {
            FilesDialogLayer(conflict) { FileOpsClient.state.value === asking }
        } else {
            FilesDialogLayer(dialog) { dialog === it }
        }
    }

    @Composable
    override fun ScreenOverlays(state: FilesState) {
        val op by FileOpsClient.state.collectAsState()
        FilesProgressBarsWash(op)
    }

    // ------------------------------------------------------------------------------------------ for the next builder

    /** Opens a tap-and-hold menu under the held row. */
    fun showMenu(state: FilesState, press: RowPress, items: List<HoldItem>) {
        state.clearNotice()
        menu = HoldMenu(press, items, grid = state.view == FilesView.ICONS)
    }

    /** Share (E7): files only; a file the provider refuses stops the share. */
    fun share(state: FilesState, entries: List<FileEntry>) {
        val files = entries.filterNot { it.isDirectory }.map { File(it.path) }
        if (files.isEmpty()) return
        if (!FilesShare.share(activity, files)) state.error("These files can't be shared")
    }

    fun showProperties(state: FilesState, entry: FileEntry) {
        state.go(FilesLocation.Properties(entry.volumeUuid, entry.path, entry.name, entry.isDirectory))
    }

    // ------------------------------------------------------------------------------------------ dialogs

    /** Shows the dialog [make] builds; [make] gets a way to name the dialog itself (to close it, to set its error). */
    private fun ask(make: (() -> FilesDialog) -> FilesDialog) {
        lateinit var made: FilesDialog
        made = make { made }
        dialog = made
    }

    private fun close(d: FilesDialog) {
        if (dialog === d) dialog = null
    }

    /**
     * A dialog's name box, answered: an empty name says so; otherwise [write] runs and the dialog closes when it
     * worked, or shows why it did not and stays (the file is untouched). Returns the write's job, null when none ran.
     */
    private fun named(d: FilesDialog, typed: String, write: suspend (String) -> OpResult) = typed.trim().let { name ->
        if (name.isEmpty()) {
            d.error = DialogText.NAME_NEEDED
            null
        } else scope.launch {
            when (val r = write(name)) {
                is OpResult.Failed -> d.error = DialogText.sentence(r.reason)
                else -> close(d)
            }
        }
    }

    private fun conflictDialog(name: String, answer: (Conflict) -> Unit) = FilesDialog(
        DialogText.conflictTitle(name), DialogText.CONFLICT_BODY,
        choices = listOf(
            DialogChoice("replace", DialogText.REPLACE) { answer(Conflict.REPLACE) },
            DialogChoice("keep_both", DialogText.KEEP_BOTH) { answer(Conflict.KEEP_BOTH) },
            DialogChoice("skip", DialogText.SKIP) { answer(Conflict.SKIP) },
        ),
        onBack = { answer(Conflict.SKIP) },
    )

    /** A quick write's conflict (the bin's Restore), asked on the page while the write waits. Called off the main thread. */
    private suspend fun askConflict(clash: File): Conflict {
        val answer = CompletableDeferred<Conflict>()
        withContext(Dispatchers.Main) {
            ask { d -> conflictDialog(clash.name) { close(d()); answer.complete(it) } }
        }
        return answer.await()
    }

    /** A yes / no question; [run] on yes. */
    private fun confirm(title: String, body: String, yes: String, run: () -> Unit) = ask { d ->
        FilesDialog(
            title, body,
            choices = listOf(DialogChoice("ok", yes) { close(d()); run() }, DialogChoice("cancel", DialogText.CANCEL) { close(d()) }),
            onBack = { close(d()) },
        )
    }

    // ------------------------------------------------------------------------------------------ rename, delete

    private fun rename(state: FilesState, entry: FileEntry) = ask { d ->
        FilesDialog(
            DialogText.RENAME, input = entry.name,
            choices = listOf(
                DialogChoice("ok", DialogText.RENAME) { typed ->
                    if (typed.trim() == entry.name) close(d())
                    else named(d(), typed) { name -> FilesEnv.rename(app, File(entry.path), name) }?.invokeOnCompletion {
                        if (dialog !== d()) {
                            selection.leave()
                            state.refresh()
                        }
                    }
                },
                DialogChoice("cancel", DialogText.CANCEL) { close(d()) },
            ),
            onBack = { close(d()) },
        )
    }

    /** Delete, after its confirmation: each item into its own volume's Recycle Bin. One that cannot go stays, and says so. */
    private fun delete(state: FilesState, entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        confirm(DialogText.deleteTitle(entries.size), if (entries.size == 1) DialogText.DELETE_BODY else DialogText.DELETE_BODY_MANY, DialogText.DELETE) {
            each(state, entries, "delete") { FilesEnv.binDelete(app, File(it.path)) }
        }
    }

    /** Runs [write] on each of [entries] in turn, then shows the page as it is now; the first failure is said. */
    private fun each(state: FilesState, entries: List<FileEntry>, verb: String, write: suspend (FileEntry) -> OpResult) {
        scope.launch {
            var failed: String? = null
            for (e in entries) {
                val r = write(e)
                if (r is OpResult.Failed && failed == null) failed = "Couldn't $verb ${e.name}: ${r.reason}"
            }
            selection.leave()
            state.refresh()
            failed?.let { state.error(it) }
        }
    }

    // ------------------------------------------------------------------------------------------ the Recycle Bin

    private fun binRestore(state: FilesState, entries: List<FileEntry>) =
        each(state, entries, "restore") { e -> e.bin?.let { FilesEnv.binRestore(app, it, ::askConflict) } ?: OpResult.Done() }

    private fun binPurge(state: FilesState, entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        confirm(DialogText.purgeTitle(entries.size), DialogText.PURGE_BODY, DialogText.DELETE) {
            each(state, entries, "delete") { e -> e.bin?.let { FilesEnv.binPurge(app, it) } ?: OpResult.Done() }
        }
    }

    /** Empty: every mounted volume's bin (the page lists them as one). */
    private fun binEmpty(state: FilesState) {
        val rows = (state.body as? PageBody.Rows)?.entries.orEmpty()
        if (rows.isEmpty()) return
        val volumes = state.volumes.map { it.volume }
        confirm(DialogText.EMPTY_TITLE, DialogText.emptyBody(rows.size), DialogText.EMPTY) {
            scope.launch {
                var failed: String? = null
                for (v in volumes) {
                    val r = FilesEnv.binEmpty(app, v)
                    if (r is OpResult.Failed && failed == null) failed = "Couldn't empty the Recycle Bin: ${r.reason}"
                }
                selection.leave()
                state.refresh()
                failed?.let { state.error(it) }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Move to / Copy to

    private fun startPick(state: FilesState, move: Boolean, entries: List<FileEntry>) {
        if (entries.isEmpty()) return
        val start = FilesPicker.startOf(state) ?: return
        pick = Pick(move, entries.map { File(it.path) })
        selection.drop()
        state.beginSubHistory(start)
    }

    private fun cancelPick(state: FilesState) {
        pick = null
        state.endSubHistory()
    }

    private fun confirmPick(state: FilesState) {
        val chosen = pick ?: return
        val dest = state.location as? FilesLocation.Folder ?: return
        pick = null
        state.endSubHistory()
        val into = File(dest.path)
        started(state, if (chosen.move) FileOpsClient.move(activity, chosen.sources, into) else FileOpsClient.copy(activity, chosen.sources, into))
    }

    /** What a start that did not start says. */
    private fun started(state: FilesState, start: FileOpsClient.Start) {
        when (start) {
            FileOpsClient.Start.STARTED -> Unit
            FileOpsClient.Start.BUSY -> state.error("Wait for the files that are being copied or moved to finish")
            FileOpsClient.Start.NOT_VISIBLE, FileOpsClient.Start.NOT_ALLOWED -> state.error("Files couldn't start that")
        }
    }

    /**
     * The copy service ended (also while Files was away — the state waits for the page): the destination folder is
     * shown (r3 D2 (4)); a failure is said over the page as it is now; then the state is given back.
     */
    private fun finished(state: FilesState, done: OpState.Finished) {
        FileOpsClient.dismiss()
        if (pick != null) cancelPick(state)
        when (val r = done.result) {
            is OpResult.Done -> {
                val volume = FilePaths.volumeOf(done.destination.path, FileVolumes.volumes(), FilesStores::canonical)
                val path = FilesStores.canonical(done.destination.path)
                if (volume == null || path == null) return state.refresh()
                val to = FilesLocation.Folder(volume.uuid, path.trimEnd('/'))
                state.closePane()
                if (state.location == to) state.refresh() else state.go(to)
            }
            OpResult.Cancelled -> state.refresh()
            is OpResult.Failed -> {
                state.refresh()
                state.error("Couldn't ${WHAT[done.kind]}: ${r.reason}")
            }
        }
    }

    // ------------------------------------------------------------------------------------------ zip

    /** A tap inside a zip (r3 D6): a folder opens, a zip in it opens as a zip again, and nothing else opens. */
    private fun tapInZip(state: FilesState, entry: FileEntry) {
        val at = state.location as? FilesLocation.Zip ?: return
        val inside = entry.zipPath ?: return
        when {
            entry.isDirectory -> state.go(at.copy(dir = inside))
            ZipNames.isZipName(entry.name) -> state.go(FilesLocation.Zip(at.volumeUuid, at.file, at.chain + inside))
            else -> state.error(ZipSessions.EXTRACT_FIRST)
        }
    }

    private fun extract(state: FilesState, at: FilesLocation.Zip) = started(state, FileOpsClient.extract(activity, File(at.file)))

    private fun createZip(state: FilesState, into: FilesLocation.Folder, entries: List<FileEntry>) {
        val sources = entries.map { File(it.path) }
        selection.leave()
        started(state, FileOpsClient.createZip(activity, sources, File(into.path)))
    }

    private companion object {
        // The icon font's glyphs (`ic_fluent_<name>_20_regular`): MoveToFolder E8DE, Copy E8C8, and the P4 actions' own.
        const val GLYPH_MOVE = ""     // "folder arrow right"
        const val GLYPH_COPY = ""     // "copy"
        const val GLYPH_RESTORE = ""  // "arrow undo"
        const val GLYPH_EXTRACT = ""  // "folder arrow up"

        val WHAT = mapOf(
            OpKind.COPY to "copy the files",
            OpKind.MOVE to "move the files",
            OpKind.EXTRACT to "extract the zip",
            OpKind.CREATE_ZIP to "create the zip",
        )
    }
}
