package app.tileshell.files

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.MotionClock
import app.tileshell.ui.MotionTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** List (64-epx rows) or Icons (three per row) — the app bar's third button (r11/files.md 1.6.4). */
enum class FilesView { LIST, ICONS }

/** What the page under the location bar holds. */
sealed interface PageBody {
    /** Nothing drawn: the one-frame cut every change of folder starts with (pass 2 §5 row 4.3), and a load in flight. */
    data object Loading : PageBody

    data class Rows(val entries: List<FileEntry>) : PageBody

    /** A state the page says in words (`files_error`): the folder is gone, its storage was removed, it cannot be read. */
    data class Error(val text: String) : PageBody
}

/** Search within the current tree (r11/files.md 1.11): the term, the hits so far, and whether the walk still runs. */
class SearchState(val query: String, val hits: List<FileEntry>, val running: Boolean, val folders: Int)

/** A line the page shows over its top for a while: an error (`files_error`) or the reason an entry cannot be read. */
class Notice(val text: String, val tag: String)

/**
 * Files' one state holder (build tasks 2 and 5): where the app is ([location], the in-app history, the ≡ pane), what
 * the page shows ([body], [view], [sort], [search]) and every move between pages. One per `FilesActivity`, made in
 * `onCreate` and kept for the activity's life, so a warm return shows the page that was left.
 *
 * What it does NOT own goes to [actions] — one holder, no-ops by default ([FilesActions]).
 *
 * Reads run off the main thread on [attach]'s scope, which is the composition's (it carries the frame clock the
 * entrance and its `[motion]` line are timed on).
 */
@Stable
class FilesState(context: Context, val actions: FilesActions) {
    private val app = context.applicationContext
    private val prefs = FilesPrefs(app)
    private val history = FilesHistory(FilesLocation.Recent)
    private val main = Handler(Looper.getMainLooper())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** The page shown. Changed only through [go], [up], [back], [choose] and [open]. */
    var location: FilesLocation by mutableStateOf(FilesLocation.Recent)
        private set

    /** The ≡ pane is open. A cold start opens it (pass 2 §1 UNMEASURED-4); an extras launch does not (r3 D2). */
    var paneOpen by mutableStateOf(false)
        private set

    /** The mounted volumes, as [FileVolumes] tracks them. */
    var volumes: List<MountedVolume> by mutableStateOf(FileVolumes.mounted.value)
        private set

    var view by mutableStateOf(FilesView.LIST)
        private set
    var sort by mutableStateOf(FileSort.NAME)
        private set
    var showHidden by mutableStateOf(prefs.showHidden)
        private set
    var body: PageBody by mutableStateOf(PageBody.Loading)
        private set

    /** Null outside search mode. */
    var search: SearchState? by mutableStateOf(null)
        private set
    var notice: Notice? by mutableStateOf(null)
        private set

    /** The ••• expansion and the sort flyout: the page's own two overlays. */
    var moreOpen by mutableStateOf(false)
    var sortOpen by mutableStateOf(false)

    /** How far through the entrance the rows are, as a share of its time (0 = its first frame, 1 = at rest); [ENTER_HIDDEN] before it. */
    val enter = mutableFloatStateOf(1f)

    /** Counts the lists handed to the page: a new one starts scrolled to its top. */
    var listEpoch by mutableIntStateOf(0)
        private set

    /** A row to bring into view once its folder is listed (the `name` extra); taken by the list that shows it. */
    var showName: String? by mutableStateOf(null)

    /** The rows on screen now: the search's hits while a term is typed, else the page's own. */
    val entries: List<FileEntry>
        get() {
            val s = search
            if (s != null && s.query.isNotBlank()) return s.hits
            return (body as? PageBody.Rows)?.entries.orEmpty()
        }

    /** True while a term is typed into the search box: the list shows hits and the sort line reads "Relevance". */
    val searching: Boolean get() = search?.query?.isNotBlank() == true

    private var scope: CoroutineScope? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var searchStop: AtomicBoolean? = null
    private var needsLoad = true

    /** What the open folder held when it was last read, unsorted: a re-sort and a view switch draw from it. */
    private var cache: Cached? = null
    private var observer: FileObserver? = null

    private class Cached(val location: FilesLocation, val rows: List<FileEntry>, val stamp: Long)

    // ---------------------------------------------------------------------------------------------- lifecycle

    /** The composition's scope: loads run on it from here on. Called once per composition of the page. */
    fun attach(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            FileVolumes.mounted.collect {
                val before = volumes
                it.forEach { v -> seen[v.uuid] = v }
                volumes = it
                if (before != it) onVolumesChanged(before, it)
            }
        }
        if (needsLoad) load()
    }

    /** The composition left: nothing runs until the next [attach]. */
    fun detach() {
        scope = null
        loadJob?.cancel()
        stopSearch()
        observer?.stopWatching()
        observer = null
        needsLoad = true
    }

    /**
     * A launch (r3 D12). Extras that name a page open it with the pane closed and RESET the history; ignored extras say
     * why; with none, a cold start is Recent with the pane open and a warm one changes nothing.
     */
    fun open(request: FilesRequest, cold: Boolean) {
        val all = FileVolumes.volumes()
        when (val asked = FilesNav.resolve(request.page, request.path, all, FilesStores::canonical) { File(it).isDirectory }) {
            is FilesOpen.Go -> {
                closeOverlays()
                history.reset(asked.location)
                paneOpen = false
                if (asked.location is FilesLocation.Folder && request.from != null) {
                    say("open at ${FilePaths.lineText(asked.location.path)} (from ${caller(request.from)})")
                }
                showName = request.name?.takeIf { asked.location is FilesLocation.Folder && FilePaths.validName(it) }
                show(asked.location)
            }
            is FilesOpen.Ignored -> {
                say("open ignored: ${asked.why}")
                if (cold) coldStart()
            }
            FilesOpen.None -> if (cold) coldStart()
        }
    }

    private fun coldStart() {
        history.reset(FilesLocation.Recent)
        paneOpen = true
        show(FilesLocation.Recent)
    }

    /** A caller's name fit for a diagnostics line: letters, digits, `.`, `_` and `-`, 32 at most. */
    private fun caller(from: String): String = from.filter { it.isLetterOrDigit() || it in "._-" }.take(32).ifEmpty { "unknown" }

    /** On every resume: a folder deleted while Files was away reads "This folder is gone"; one that changed is re-read. */
    fun revalidate() {
        val at = location
        if (scope == null) return
        when {
            at is FilesLocation.Folder && body is PageBody.Rows -> {
                val dir = File(at.path)
                if (!dir.isDirectory) gone(at) else if (dir.lastModified() != cache?.stamp) load()
            }
            at is FilesLocation.Folder && body is PageBody.Error -> load()
            at == FilesLocation.Recent || at == FilesLocation.Bin -> load()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------------------------------------- navigation

    /** A tap on a folder, a breadcrumb segment, ↑: the location left goes on the history. */
    fun go(to: FilesLocation) {
        closeOverlays()
        history.go(to)
        show(to)
    }

    /** A row of the ≡ pane: the pane is gone and the list empty in the same frame (pass 2 §5 row 4.2). */
    fun choose(to: FilesLocation) {
        closePane()
        history.go(to)
        show(to)
    }

    /** ↑ — the parent folder; nothing at a volume's root (the glyph is dimmed there). */
    fun up() {
        val parent = FilesNav.parent(location) { id -> rootOf(id) } ?: return
        go(parent)
    }

    val canGoUp: Boolean get() = FilesNav.parent(location) { id -> rootOf(id) } != null

    /**
     * Back (Y3). In order: the page's own overlays, the ≡ pane, whatever [FilesActions.onBack] takes (selection mode),
     * search mode, then the previous location in the history. False = nothing left: Back leaves the app.
     */
    fun back(): Boolean {
        if (moreOpen || sortOpen) {
            closeOverlays()
            return true
        }
        if (paneOpen) {
            closePane()
            return true
        }
        if (actions.onBack(this)) return true
        if (search != null) {
            closeSearch()
            return true
        }
        val to = history.back() ?: return false
        show(to)
        return true
    }

    fun openPane() {
        closeOverlays()
        paneOpen = true
    }

    /** Closes the pane by a cut and writes `[motion] files_pane_close`: the pane is gone on the next drawn frame. */
    fun closePane() {
        if (!paneOpen) return
        paneOpen = false
        scope?.launch {
            // One frame draws the page without the pane; the cut has no duration, so the line's times are all 0 from it.
            val frame = withFrameNanos { it }
            val trace = MotionTrace("files_pane_close", frame / 1_000_000L)
            trace.frame(frame, 1f)
            Diagnostics.add("motion", trace.message())
        }
    }

    fun closeOverlays() {
        moreOpen = false
        sortOpen = false
    }

    private fun show(to: FilesLocation) {
        location = to
        stopSearch()
        search = null
        notice = null
        cache = null
        load()
    }

    fun rootOf(uuid: String): String? = volumes.firstOrNull { it.uuid == uuid }?.root
    fun labelOf(uuid: String): String? = volumes.firstOrNull { it.uuid == uuid }?.paneLabel

    /** Every volume seen mounted since Files opened: a folder on one that was pulled still reads by its volume's name. */
    private val seen = HashMap<String, MountedVolume>().apply { volumes.forEach { put(it.uuid, it) } }

    val crumbs: List<Crumb>
        get() {
            // Read first, so the breadcrumb is drawn again when a volume comes or goes.
            val mounted = volumes
            fun known(uuid: String) = mounted.firstOrNull { it.uuid == uuid } ?: seen[uuid]
            return FilesNav.crumbs(location, { known(it)?.paneLabel }, { known(it)?.root })
        }

    // ---------------------------------------------------------------------------------------------- the page's own actions

    /** The overflow's Refresh: the open page is read again. */
    fun refresh() {
        closeOverlays()
        cache = null
        if (searching) setQuery(search?.query.orEmpty()) else load()
    }

    fun toggleView() {
        closeOverlays()
        view = if (view == FilesView.LIST) FilesView.ICONS else FilesView.LIST
        redraw()
    }

    fun sortBy(key: FileSort) {
        closeOverlays()
        if (key == sort) return
        sort = key
        redraw()
    }

    fun setHidden(show: Boolean) {
        prefs.showHidden = show
        showHidden = show
        cache = null
    }

    /** One `[files]` diagnostics line. */
    fun say(line: String) = Diagnostics.add("files", line)

    /** An error line over the page (`files_error`), until the next tap or navigation. */
    fun error(text: String) {
        notice = Notice(text, "files_error")
    }

    fun clearNotice() {
        notice = null
    }

    /** A tap on a row. [FilesActions.onRowTap] sees it first; a folder opens; a file goes to its opener. */
    fun tap(entry: FileEntry) {
        notice = null
        if (entry.unreadable) {
            // E5's second form: the entry says why and opens nothing.
            notice = Notice(UNREADABLE_REASON, "files_unreadable_reason")
            say("unreadable ${FilePaths.lineText(entry.path)}")
            return
        }
        if (actions.onRowTap(this, entry)) return
        if (entry.source == EntrySource.BIN) return // a bin row opens nothing (r3 D6); its actions are the bin's own.
        if (entry.isDirectory) {
            go(FilesLocation.Folder(entry.volumeUuid, entry.path))
            return
        }
        if (!File(entry.path).isFile) {
            // Deleted between the listing and the tap (Edge cases): the line, and the list read again.
            error("This file is no longer here")
            say("open ${FilePaths.lineText(entry.path)}: gone")
            cache = null
            load()
            return
        }
        if (FileKind.isZip(entry.name, false)) actions.onOpenZip(this, entry) else actions.onOpenFile(this, entry)
    }

    // ---------------------------------------------------------------------------------------------- loading

    /** A read's answer; [listed] is the folder path its `[files] list` line names (null for a page that writes none). */
    private class Loaded(val body: PageBody, val listed: String?, val cache: Cached?)

    /** Cuts the page to empty now and reads [location] again; the rows then enter (`[motion] files_folder`). */
    private fun load() {
        val scope = scope ?: run {
            needsLoad = true
            return
        }
        needsLoad = false
        loadJob?.cancel()
        observer?.stopWatching()
        observer = null
        val at = location
        val t0 = SystemClock.uptimeMillis()
        body = PageBody.Loading
        enter.floatValue = 0f
        if (at == FilesLocation.Settings) {
            body = PageBody.Rows(emptyList())
            enter.floatValue = 1f
            return
        }
        val hidden = showHidden
        val by = sort
        val mounted = volumes
        loadJob = scope.launch {
            val loaded = withContext(Dispatchers.IO) { read(at, mounted, hidden, by) }
            present(loaded, t0)
            if (at is FilesLocation.Folder && loaded.body is PageBody.Rows) watch(at)
        }
    }

    /** The same rows again in a new order or a new view: the same cut-then-enter form as a reload (pass 2 §5 row 4.8). */
    private fun redraw() {
        val scope = scope ?: return
        val held = cache?.takeIf { it.location == location } ?: return load()
        if (body !is PageBody.Rows) return
        loadJob?.cancel()
        val t0 = SystemClock.uptimeMillis()
        val by = sort
        body = PageBody.Loading
        enter.floatValue = 0f
        loadJob = scope.launch {
            val sorted = withContext(Dispatchers.Default) { ordered(held.location, held.rows, by) }
            present(Loaded(PageBody.Rows(sorted), (held.location as? FilesLocation.Folder)?.path, held), t0)
        }
    }

    /**
     * Hands a read to the page: at least one drawn frame of the empty list first, then the rows, entering over
     * [FilesMetrics.FOLDER_ENTER_MS] on the shell's motion clock. The `list` line is written as the rows are handed over.
     */
    private suspend fun present(loaded: Loaded, t0: Long) {
        withFrameNanos { }
        cache = loaded.cache
        // Laid out but not drawn yet: the rows are composed on the frames before the entrance, so composing them never
        // lands inside it (a long first frame would be the entrance's own jank).
        enter.floatValue = ENTER_HIDDEN
        body = loaded.body
        listEpoch++
        // The time is taken here, when the rows reach the page — they are drawn on the next frame.
        if (loaded.listed != null && loaded.body is PageBody.Rows) {
            say("list ${FilePaths.lineText(loaded.listed)}: ${loaded.body.entries.size} entries ${SystemClock.uptimeMillis() - t0} ms")
        }
        if (loaded.body is PageBody.Rows && loaded.body.entries.isNotEmpty()) {
            repeat(2) { withFrameNanos { } }
            MotionClock.animate("files_folder", FilesMetrics.FOLDER_ENTER_MS, LinearEasing) { enter.floatValue = it }
        }
        enter.floatValue = 1f
    }

    /** Off the main thread: what [at] holds now. */
    private fun read(at: FilesLocation, mounted: List<MountedVolume>, hidden: Boolean, by: FileSort): Loaded = when (at) {
        is FilesLocation.Folder -> readFolder(at, mounted, hidden, by)
        FilesLocation.Recent -> {
            val rows = FilesStores.recent(app).read(mounted.map { it.uuid }.toSet()).map { e ->
                val f = File(e.path)
                FileEntry(
                    FileRow(f.name, false, f.length(), e.openedAt), e.path, e.volumeUuid,
                    FileListing.recentDetail(e.openedAt, zone), FileKind.of(f.name, false), EntrySource.RECENT,
                )
            }
            Loaded(PageBody.Rows(rows), null, Cached(at, rows, 0))
        }
        FilesLocation.Bin -> {
            val bin = FilesStores.bin(app)
            val rows = mounted.flatMap { v ->
                bin.list(v.volume).map { e ->
                    val date = e.deletedAt?.let { FileListing.dateText(it, zone) }
                    FileEntry(
                        FileRow(e.name, e.isDirectory, e.size, e.deletedAt ?: 0L),
                        File(RecycleBin.binDir(v.volume), e.binName).path, v.uuid,
                        listOfNotNull(date, v.paneLabel).joinToString(" "), FileKind.of(e.name, e.isDirectory), EntrySource.BIN, bin = e,
                    )
                }
            }.sortedWith(compareByDescending<FileEntry> { it.row.modifiedMs }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            Loaded(PageBody.Rows(rows), null, Cached(at, rows, 0))
        }
        FilesLocation.Settings -> Loaded(PageBody.Rows(emptyList()), null, null)
    }

    private fun readFolder(at: FilesLocation.Folder, mounted: List<MountedVolume>, hidden: Boolean, by: FileSort): Loaded {
        val volume = mounted.firstOrNull { it.uuid == at.volumeUuid } ?: return Loaded(PageBody.Error(STORAGE_REMOVED), null, null)
        val dir = File(at.path)
        // The shell's own folder is never browsable, by any route (T18-1): its bin is reached only through its page.
        if (FilePaths.inShellDir(at.path, volume.volume, FilesStores::canonical)) return Loaded(PageBody.Error(FOLDER_GONE), null, null)
        if (!dir.isDirectory) {
            // The volume's own root missing means the volume went, whatever the tracker has heard so far.
            return Loaded(PageBody.Error(if (at.path == volume.root) STORAGE_REMOVED else FOLDER_GONE), null, null)
        }
        val stamp = dir.lastModified()
        val rows = FolderReader.read(dir)
        if (rows == null) {
            say("unreadable ${FilePaths.lineText(at.path)}")
            return Loaded(PageBody.Error(FOLDER_UNREADABLE), null, null)
        }
        val atRoot = at.path == volume.root
        val shown = FileListing.visible(rows, hidden).filterNot { atRoot && it.name.equals(FilePaths.SHELL_DIR, ignoreCase = true) }
        // E5 (task 0 (b): the SECOND form on this image): `<volume>/Android/data` and `/obb` list as null for every
        // app, All-files access included, so the entry itself says so.
        val androidDir = at.path == "${volume.root}/Android"
        val entries = shown.map { row ->
            val path = "${at.path}/${row.name}"
            val hiddenByAndroid = androidDir && row.isDirectory && (row.name == "data" || row.name == "obb") && File(path).list() == null
            if (hiddenByAndroid) say("unreadable ${FilePaths.lineText(path)}")
            FileEntry(
                row, path, at.volumeUuid,
                if (hiddenByAndroid) UNREADABLE_TEXT else FileListing.detail(row, zone),
                FileKind.of(row.name, row.isDirectory), unreadable = hiddenByAndroid,
            )
        }
        val sorted = ordered(at, entries, by)
        return Loaded(PageBody.Rows(sorted), at.path, Cached(at, entries, stamp))
    }

    /** A folder's rows in the sort's order (r3 V13); Recent and the bin keep their own (newest first). */
    private fun ordered(at: FilesLocation, entries: List<FileEntry>, by: FileSort): List<FileEntry> {
        if (at !is FilesLocation.Folder) return entries
        val rows = FileListing.comparator(by)
        return entries.sortedWith { a, b -> rows.compare(a.row, b.row) }
    }

    private fun gone(at: FilesLocation.Folder) {
        if (location != at) return
        loadJob?.cancel()
        observer?.stopWatching()
        observer = null
        stopSearch()
        search = null
        cache = null
        body = PageBody.Error(if (volumes.none { it.uuid == at.volumeUuid }) STORAGE_REMOVED else FOLDER_GONE)
        enter.floatValue = 1f
    }

    /** Watches the open folder itself: deleted or moved away under the listing, the page says so at once (Edge cases). */
    private fun watch(at: FilesLocation.Folder) {
        val dir = File(at.path)
        val o = object : FileObserver(dir, DELETE_SELF or MOVE_SELF) {
            override fun onEvent(event: Int, path: String?) {
                if (event and (DELETE_SELF or MOVE_SELF) != 0) main.post { if (!dir.isDirectory) gone(at) }
            }
        }
        observer = o
        runCatching { o.startWatching() }
    }

    /** A volume came or went: the open folder's storage removed reads so; Recent and the bin show the new set. */
    private fun onVolumesChanged(before: List<MountedVolume>, now: List<MountedVolume>) {
        val at = location
        when {
            at is FilesLocation.Folder && now.none { it.uuid == at.volumeUuid } -> gone(at)
            at is FilesLocation.Folder && before.none { it.uuid == at.volumeUuid } -> load()
            at == FilesLocation.Recent || at == FilesLocation.Bin -> load()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------------------------------------- search

    /** The app bar's Search: the box opens under the location bar, empty. */
    fun openSearch() {
        closeOverlays()
        if (search == null) search = SearchState("", emptyList(), running = false, folders = 0)
    }

    /** Leaving search mode (Back): the page's own rows again. */
    fun closeSearch() {
        stopSearch()
        search = null
    }

    /** The box's text changed: the walk starts again from the open folder (r11/files.md 1.11.1). */
    fun setQuery(query: String) {
        stopSearch()
        val at = location
        val term = query.trim()
        if (term.isEmpty()) {
            search = SearchState(query, emptyList(), running = false, folders = 0)
            return
        }
        if (at !is FilesLocation.Folder) {
            // Recent and the bin are lists, not trees: the term filters the rows already read.
            val own = (cache?.takeIf { it.location == at }?.rows ?: (body as? PageBody.Rows)?.entries).orEmpty()
            val hits = own.mapNotNull { e -> FileListing.searchRank(e.name, term)?.let { it to e } }
                .sortedWith(compareBy<Pair<Int, FileEntry>> { it.first }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.second.name })
                .map { it.second }
            search = SearchState(query, hits, running = false, folders = 0)
            return
        }
        val scope = scope ?: return
        val volume = volumes.firstOrNull { it.uuid == at.volumeUuid }?.volume ?: return
        val stop = AtomicBoolean(false)
        searchStop = stop
        search = SearchState(query, emptyList(), running = true, folders = 0)
        val hidden = showHidden
        val found = ArrayList<FolderReader.Hit>()
        val folders = java.util.concurrent.atomic.AtomicInteger(0)
        fun publish(running: Boolean) {
            val hits = synchronized(found) { found.sortedWith(FolderReader.byRelevance) }.map { h ->
                FileEntry(h.row, h.path, at.volumeUuid, h.relative, FileKind.of(h.row.name, h.row.isDirectory), EntrySource.SEARCH)
            }
            search = SearchState(query, hits, running, folders.get())
        }
        searchJob = scope.launch {
            delay(SEARCH_TYPING_MS)
            val ticker = launch {
                while (isActive) {
                    delay(SEARCH_PUBLISH_MS)
                    publish(running = true)
                }
            }
            withContext(Dispatchers.IO) {
                FolderReader.search(
                    File(at.path), term, hidden,
                    skip = { FilePaths.inShellDir(it.path, volume, FilesStores::canonical) },
                    stopped = { stop.get() },
                    onFolder = { folders.set(it) },
                    onHit = { synchronized(found) { found += it } },
                )
            }
            ticker.cancel()
            if (!stop.get()) publish(running = false)
        }
    }

    /** `files_search_cancel`: the walk stops, the hits found so far stay, and the line says so (E8). */
    fun cancelSearch() {
        val s = search ?: return
        if (!s.running) return
        stopSearch()
        search = SearchState(s.query, s.hits, running = false, folders = s.folders)
        say("search cancelled")
    }

    private fun stopSearch() {
        searchStop?.set(true)
        searchStop = null
        searchJob?.cancel()
        searchJob = null
    }

    companion object {
        /** [enter] while the rows are composed and not yet shown. */
        const val ENTER_HIDDEN = -1f

        const val FOLDER_GONE = "This folder is gone"
        const val STORAGE_REMOVED = "This storage was removed"
        const val FOLDER_UNREADABLE = "Files can't read this folder"

        /** E5's second form: the `Android/data` and `Android/obb` entries' own line (H6). */
        const val UNREADABLE_TEXT = "Android doesn't let apps see other apps' folders here"
        const val UNREADABLE_REASON = "Android doesn't let apps see other apps' folders here. Since Android 11 no app can open this folder, Files included."

        /** The walk starts once typing has paused this long, and its hits reach the page this often. */
        private const val SEARCH_TYPING_MS = 250L
        private const val SEARCH_PUBLISH_MS = 100L
    }
}
