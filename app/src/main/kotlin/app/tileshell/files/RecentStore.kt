package app.tileshell.files

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonLong
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File

/**
 * Recent (Q-18-1, "below Q-18-1"): the files OPENED in Files, newest first, [CAP] at most, one entry per path, kept in
 * the app's private store (`filesDir/files-recent.json`, temp-and-rename). No MediaStore query, no ContentObserver.
 *
 * An entry is added at every hand-off to an opener ([add]); Files' own rename and move keep its path true
 * ([renamePath]); a file deleted in Files, or found gone on a read, drops its entry; entries on a volume that is not
 * mounted are hidden while it is unmounted and come back with it. [remove] is "Remove from recent": it deletes the
 * entry and never the file. One object per process (every update rewrites the whole file under the object's lock).
 *
 * @param dir the app's `filesDir`
 * @param say one `[files]` diagnostics line, without its tag
 */
class RecentStore(private val dir: File, private val clock: () -> Long, private val say: (String) -> Unit) {
    /** One opened file: where it is, the volume it is on ([FileVolume.uuid]) and when it was last opened. */
    data class Entry(val path: String, val volumeUuid: String, val openedAt: Long)

    private val file = File(dir, FILE_NAME)

    /** [path] was handed to an opener: it goes to the top (re-opening moves it there), and the list is cut to [CAP]. */
    @Synchronized
    fun add(path: String, volumeUuid: String) {
        write((listOf(Entry(path, volumeUuid, clock())) + all().filter { it.path != path }).take(CAP))
        say("recent add ${FilePaths.lineText(path)}")
    }

    /** "Remove from recent", and a file deleted in Files: the entry for [path] — and, for a folder, every entry inside it — goes. */
    @Synchronized
    fun remove(path: String) {
        val all = all()
        val gone = all.filter { covers(path, it.path) }
        if (gone.isEmpty()) return
        write(all - gone.toSet())
        gone.forEach { say("recent remove ${FilePaths.lineText(it.path)}") }
    }

    /**
     * A rename or a move made in Files: the entry at [from] (and, when [from] is a folder, every entry inside it) now
     * names [to], in the same position; [toVolumeUuid] is set for a move onto another volume. An entry already at the
     * new path gives way — one entry per path.
     */
    @Synchronized
    fun renamePath(from: String, to: String, toVolumeUuid: String? = null) {
        val all = all()
        if (all.none { covers(from, it.path) }) return
        val moved = all.map { e ->
            if (covers(from, e.path)) e.copy(path = to + e.path.substring(from.length), volumeUuid = toVolumeUuid ?: e.volumeUuid) else e
        }
        val renamed = all.indices.filter { covers(from, all[it].path) }.map { moved[it].path }.toSet()
        // The renamed entry keeps its place; an older entry that held the new path is the one dropped.
        val seen = HashSet<String>()
        write(moved.filterIndexed { i, e -> (covers(from, all[i].path) || e.path !in renamed) && seen.add(e.path) })
    }

    /**
     * The page's rows, newest first: entries on a volume in [mountedUuids] whose file is still there. An entry whose file
     * is gone is DROPPED from the store; one on an unmounted volume is only hidden. Writes `recent: <n>`.
     */
    @Synchronized
    fun read(mountedUuids: Set<String>, exists: (String) -> Boolean = { File(it).isFile }): List<Entry> {
        val all = all()
        val gone = all.filter { it.volumeUuid in mountedUuids && !exists(it.path) }.toSet()
        if (gone.isNotEmpty()) write(all - gone)
        val shown = all.filter { it.volumeUuid in mountedUuids && it !in gone }
        say("recent: ${shown.size}")
        return shown
    }

    /** Everything stored, hidden entries included; an unreadable store is an empty list. */
    @Synchronized
    fun all(): List<Entry> {
        val root = runCatching { MiniJson.parseOrNull(file.readText()) }.getOrNull().jsonObject() ?: return emptyList()
        val seen = HashSet<String>()
        return root["recent"].jsonArray().mapNotNull { row ->
            val o = row.jsonObject() ?: return@mapNotNull null
            val path = o.jsonString("path")?.takeIf { it.isNotEmpty() && seen.add(it) } ?: return@mapNotNull null
            Entry(path, o.jsonString("volume") ?: return@mapNotNull null, o.jsonLong("openedAt") ?: return@mapNotNull null)
        }.sortedByDescending { it.openedAt }.take(CAP)
    }

    /** [entry] is [path] itself or lies inside the folder [path]. */
    private fun covers(path: String, entry: String): Boolean = entry == path || entry.startsWith(path.trimEnd('/') + "/")

    private fun write(entries: List<Entry>) {
        runCatching {
            dir.mkdirs()
            val rows = entries.map { linkedMapOf("path" to it.path, "volume" to it.volumeUuid, "openedAt" to it.openedAt) }
            FilePaths.writeAtomic(file, MiniJson.write(linkedMapOf("recent" to rows)))
        }
    }

    companion object {
        const val FILE_NAME = "files-recent.json"
        const val CAP = 100
    }
}
