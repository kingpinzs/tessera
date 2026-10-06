package app.tileshell.files

/**
 * A page of Files. There is ONE activity for every page (r3 D12), so a page is a value here, never an activity: the
 * history holds these, the ≡ pane and the breadcrumb make them, and [FilesState] shows the current one.
 *
 * A new page is a new subtype: the compiler then asks for its crumbs ([FilesNav.crumbs]), its pane row
 * ([FilesNav.paneId]) and its body (`FilesPageBody`'s `when`).
 */
sealed interface FilesLocation {
    /** The files opened in Files (Q-18-1). */
    data object Recent : FilesLocation

    /** A folder by path on a mounted volume; [path] is canonical and is the volume's root or lies under it. */
    data class Folder(val volumeUuid: String, val path: String) : FilesLocation

    /** Every mounted volume's Recycle Bin in one list. */
    data object Bin : FilesLocation

    /** Files' own settings (hidden files). */
    data object Settings : FilesLocation

    /**
     * A zip opened as a folder (Q2 C, T18-2): the virtual folder [dir] (`""` is its root) of the zip at [file], a real
     * file on a mounted volume — or, when [chain] is not empty, of a zip nested inside it: each element of [chain] is
     * the entry path of a zip inside the one before it (r3 D6).
     */
    data class Zip(val volumeUuid: String, val file: String, val chain: List<String> = emptyList(), val dir: String = "") : FilesLocation

    /** The Properties PAGE of one file or folder (r11/files.md 1.10): [path] is a real path on a mounted volume. */
    data class Properties(val volumeUuid: String, val path: String, val name: String, val isDirectory: Boolean) : FilesLocation
}

/**
 * Files' in-app history (Y3; r11/files.md UNMEASURED-3, D3's UWP rule): Back returns to the previous location — "up
 * one level" on a straight descent, the folder left after a breadcrumb jump — and leaves the app when nothing is left.
 * A `page` / `path` launch [reset]s it (r3 D12), so Back from there leaves Files at once.
 */
class FilesHistory(start: FilesLocation) {
    var current: FilesLocation = start
        private set
    private val stack = ArrayList<FilesLocation>()

    val depth: Int get() = stack.size

    /** Shows [to]; the location left goes on the stack. Going to where one already is changes nothing (false). */
    fun go(to: FilesLocation): Boolean {
        if (to == current) return false
        stack += current
        current = to
        return true
    }

    /** Back: the previous location, now current — or null when the history is empty (Back then leaves the app). */
    fun back(): FilesLocation? {
        if (stack.isEmpty()) return null
        current = stack.removeAt(stack.lastIndex)
        return current
    }

    fun reset(to: FilesLocation) {
        stack.clear()
        current = to
    }
}

/** One breadcrumb segment (`files_crumb:<n>`): its text and where a tap on it goes. */
data class Crumb(val label: String, val location: FilesLocation)

/** What a launch's `page` / `path` extras ask for, once checked (r3 D12). */
sealed interface FilesOpen {
    /** No extra: a cold start shows Recent with the pane open, a warm return the page that was left. */
    data object None : FilesOpen

    data class Go(val location: FilesLocation) : FilesOpen

    /** `[files] open ignored: <why>` — Files shows what it would have shown without the extras. */
    data class Ignored(val why: String) : FilesOpen
}

/** The navigation rules, free of Android and Compose (`FilesNavTest`). */
object FilesNav {
    const val PANE_RECENT = "recent"
    const val PANE_DEVICE = "device"
    const val PANE_BIN = "bin"

    /** The pane row a location belongs to: `recent`, `device`, a volume's uuid, `bin` — or null (Settings has none). */
    fun paneId(location: FilesLocation): String? = when (location) {
        FilesLocation.Recent -> PANE_RECENT
        FilesLocation.Bin -> PANE_BIN
        FilesLocation.Settings -> null
        is FilesLocation.Folder -> volumePane(location.volumeUuid)
        is FilesLocation.Zip -> volumePane(location.volumeUuid)
        is FilesLocation.Properties -> volumePane(location.volumeUuid)
    }

    private fun volumePane(uuid: String): String = if (uuid == FilePaths.PRIMARY) PANE_DEVICE else uuid

    /**
     * The breadcrumb (r11/files.md 1.2): the volume's pane name first ("This Device", a card's label), then one segment
     * per folder under its root. [rootOf] and [labelOf] answer for a mounted volume; for one that is gone the folder
     * still reads by its last path segment.
     */
    fun crumbs(location: FilesLocation, labelOf: (String) -> String?, rootOf: (String) -> String?): List<Crumb> = when (location) {
        FilesLocation.Recent -> listOf(Crumb("Recent", location))
        FilesLocation.Bin -> listOf(Crumb("Recycle Bin", location))
        FilesLocation.Settings -> listOf(Crumb("Settings", location))
        is FilesLocation.Folder -> {
            val root = rootOf(location.volumeUuid)?.trimEnd('/')
            val label = labelOf(location.volumeUuid)
            if (root == null || label == null || !(location.path == root || FilePaths.under(location.path, root))) {
                listOf(Crumb(location.path.trimEnd('/').substringAfterLast('/').ifEmpty { location.path }, location))
            } else {
                val out = ArrayList<Crumb>()
                out += Crumb(label, FilesLocation.Folder(location.volumeUuid, root))
                var at = root
                for (segment in location.path.substring(root.length).split('/').filter { it.isNotEmpty() }) {
                    at = "$at/$segment"
                    out += Crumb(segment, FilesLocation.Folder(location.volumeUuid, at))
                }
                out
            }
        }
        is FilesLocation.Zip -> {
            // The folder the zip lies in, the zip, each zip nested in it, then the folders of the innermost one.
            val out = ArrayList(crumbs(FilesLocation.Folder(location.volumeUuid, location.file.substringBeforeLast('/')), labelOf, rootOf))
            out += Crumb(location.file.substringAfterLast('/'), FilesLocation.Zip(location.volumeUuid, location.file))
            for (i in location.chain.indices) {
                out += Crumb(location.chain[i].substringAfterLast('/'), FilesLocation.Zip(location.volumeUuid, location.file, location.chain.take(i + 1)))
            }
            var at = ""
            for (segment in location.dir.split('/').filter { it.isNotEmpty() }) {
                at = if (at.isEmpty()) segment else "$at/$segment"
                out += Crumb(segment, location.copy(dir = at))
            }
            out
        }
        is FilesLocation.Properties -> {
            // The breadcrumb ends in the item's name (r11/files.md 1.10.1); a volume's own root is its one segment.
            val root = rootOf(location.volumeUuid)?.trimEnd('/')
            if (root == null || !FilePaths.under(location.path, root)) {
                listOf(Crumb(labelOf(location.volumeUuid)?.takeIf { location.path == root } ?: location.name, location))
            } else {
                crumbs(FilesLocation.Folder(location.volumeUuid, location.path.substringBeforeLast('/')), labelOf, rootOf) + Crumb(location.name, location)
            }
        }
    }

    /** ↑ (1.2.8): the parent folder, or null at a volume's root and on every page that is not a folder (↑ is dimmed). */
    fun parent(location: FilesLocation, rootOf: (String) -> String?): FilesLocation? = when (location) {
        is FilesLocation.Folder -> folderOf(location.volumeUuid, location.path, rootOf)
        // Inside a zip ↑ walks its folders, then the zip it is nested in, then the folder the zip lies in.
        is FilesLocation.Zip -> when {
            location.dir.isNotEmpty() -> location.copy(dir = location.dir.substringBeforeLast('/', ""))
            location.chain.isNotEmpty() -> location.copy(chain = location.chain.dropLast(1), dir = location.chain.last().substringBeforeLast('/', ""))
            else -> folderOf(location.volumeUuid, location.file, rootOf)
        }
        is FilesLocation.Properties -> folderOf(location.volumeUuid, location.path, rootOf)
        else -> null
    }

    /** The folder [path] lies in, on its volume — null for the volume's root and for a path outside it. */
    private fun folderOf(uuid: String, path: String, rootOf: (String) -> String?): FilesLocation.Folder? {
        val root = rootOf(uuid)?.trimEnd('/') ?: return null
        if (!FilePaths.under(path, root)) return null
        return FilesLocation.Folder(uuid, path.substringBeforeLast('/'))
    }

    /**
     * Which segments the location bar draws (1.2.6): all of them when they fit; otherwise the first, "…" and the last
     * ("This Device › … › clipstudio perf"). The answer is the indices drawn — a gap between two of them is where the
     * "…" sits. One or two segments never collapse: they are cut with an ellipsis by the text itself.
     *
     * @param widths each segment's text width
     * @param separator one "›" with the gap on both sides
     * @param ellipsis the "…" segment's width
     */
    fun shownCrumbs(widths: List<Float>, separator: Float, ellipsis: Float, available: Float): List<Int> {
        val all = widths.indices.toList()
        if (widths.size <= 2) return all
        val full = widths.sum() + separator * (widths.size - 1)
        return if (full <= available) all else listOf(0, widths.lastIndex)
    }

    /**
     * The extras, checked (r3 D12; GATE item (e)): a `path` is honoured only when its canonical form is a mounted
     * volume's root or a folder under one, outside the shell's own folder; a `page` names Recent, the bin, This Device
     * or a mounted volume. A `path` wins over a `page`. Anything else is [FilesOpen.Ignored] with its reason.
     */
    fun resolve(
        page: String?,
        path: String?,
        volumes: List<FileVolume>,
        canonical: (String) -> String?,
        isDirectory: (String) -> Boolean,
    ): FilesOpen {
        if (path != null) {
            val real = runCatching { canonical(path) }.getOrNull() ?: return FilesOpen.Ignored("the path cannot be resolved")
            val volume = FilePaths.volumeOf(real, volumes, canonical) ?: return FilesOpen.Ignored("outside every mounted volume")
            if (FilePaths.inShellDir(real, volume, canonical)) return FilesOpen.Ignored("inside the shell's own folder")
            if (!isDirectory(real)) return FilesOpen.Ignored("not a folder")
            return FilesOpen.Go(FilesLocation.Folder(volume.uuid, real.trimEnd('/')))
        }
        return when {
            page == null -> FilesOpen.None
            page == FilesIntents.PAGE_RECENT -> FilesOpen.Go(FilesLocation.Recent)
            page == FilesIntents.PAGE_BIN -> FilesOpen.Go(FilesLocation.Bin)
            page == FilesIntents.PAGE_DEVICE -> rootOf(FilePaths.PRIMARY, volumes, canonical)
            page.startsWith(FilesIntents.PAGE_VOLUME_PREFIX) -> rootOf(page.removePrefix(FilesIntents.PAGE_VOLUME_PREFIX), volumes, canonical)
            else -> FilesOpen.Ignored("unknown page")
        }
    }

    private fun rootOf(uuid: String, volumes: List<FileVolume>, canonical: (String) -> String?): FilesOpen {
        val volume = volumes.firstOrNull { it.uuid == uuid } ?: return FilesOpen.Ignored("that storage is not mounted")
        val root = runCatching { canonical(volume.root) }.getOrNull() ?: return FilesOpen.Ignored("that storage cannot be resolved")
        return FilesOpen.Go(FilesLocation.Folder(volume.uuid, root.trimEnd('/')))
    }
}

/** What a row is, for its icon and for what a tap does. By name alone: nothing is opened to find out. */
enum class FileKind {
    FOLDER, IMAGE, VIDEO, AUDIO, ARCHIVE, TEXT, OTHER;

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "dng", "avif")
        private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "mid", "midi")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "jar", "apk")
        private val TEXT_EXT = setOf("txt", "log", "md", "csv", "json", "xml", "html", "htm", "srt", "ini", "cfg", "pdf", "doc", "docx", "rtf")

        fun of(name: String, isDirectory: Boolean): FileKind {
            if (isDirectory) return FOLDER
            return when (FilePaths.splitExtension(name).second.removePrefix(".").lowercase()) {
                in IMAGE_EXT -> IMAGE
                in VIDEO_EXT -> VIDEO
                in AUDIO_EXT -> AUDIO
                in ARCHIVE_EXT -> ARCHIVE
                in TEXT_EXT -> TEXT
                else -> OTHER
            }
        }

        /** A zip opens as a folder (Q2 C): the one archive type a tap does not hand to an opener. */
        fun isZip(name: String, isDirectory: Boolean): Boolean =
            !isDirectory && FilePaths.splitExtension(name).second.equals(".zip", ignoreCase = true)
    }
}
