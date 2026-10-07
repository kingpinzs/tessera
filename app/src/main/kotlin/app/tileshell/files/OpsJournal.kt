package app.tileshell.files

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File
import java.io.IOException

/**
 * The operations journal and the sweep (r3 D4). A kill or a reboot ends the copy service with no chance to clean up, so
 * "nothing half-written" is made true afterwards: every running operation's temp (`.<name>.<opid>.part`,
 * `.<zip>.<opid>.extract/`) is named in `files-ops.json` in the app's private files BEFORE it is created, the entry is
 * removed when the operation ends, and [sweep] — run by the main process at its first start after unlock and when a
 * journalled volume mounts — deletes what a dead operation left.
 *
 * The sweep deletes ONLY a path the journal names, and of those only one that has a temp's name, lies under its own
 * mounted volume and is not `.Tessera/bin` or inside it: the journal can never be made to empty a bin or reach a file
 * the user owns. One object per process (every update rewrites the whole file under the object's lock).
 *
 * @param dir the app's `filesDir`
 * @param say one `[files]` diagnostics line, without its tag
 */
class OpsJournal(
    private val dir: File,
    private val clock: () -> Long,
    private val canonical: (String) -> String?,
    private val say: (String) -> Unit,
) {
    /** [to] is set only for a rename's in-between name ([FilePaths.renameName]): the path the file is on its way to. */
    data class Entry(val opId: String, val path: String, val volumeUuid: String, val to: String? = null)

    private val file = File(dir, FILE_NAME)
    private var counter = 0

    /** The operations begun by THIS process and not ended: their temps are being written now and are never swept. */
    private val live = HashSet<String>()

    /** A new operation id: letters and digits only (it sits inside a file name), unique across restarts. */
    @Synchronized
    fun newOpId(): String = java.lang.Long.toString(clock(), 36) + Integer.toString(counter++, 36).padStart(2, '0')

    /**
     * Names [path] as operation [opId]'s temp (replacing the one it named before — an operation writes one temp at a
     * time). Throws when the journal cannot be written: the caller then makes no temp.
     */
    @Synchronized
    @Throws(IOException::class)
    fun put(opId: String, path: String, volumeUuid: String, to: String? = null) {
        live += opId
        write(entries().filter { it.opId != opId } + Entry(opId, path, volumeUuid, to))
    }

    /**
     * The operation stopped in THIS process with its entry still owed: a case-only rename whose file could be moved
     * neither on nor back. The entry stays, so the next sweep finishes it.
     */
    @Synchronized
    fun abandon(opId: String) {
        live -= opId
    }

    /** The operation ended (done, cancelled or failed) and removed its own temp. */
    @Synchronized
    fun end(opId: String) {
        live -= opId
        val all = entries()
        if (all.any { it.opId == opId }) runCatching { write(all.filter { it.opId != opId }) }
    }

    /** What the journal names now; an unreadable journal names nothing (so nothing is swept). */
    @Synchronized
    fun entries(): List<Entry> {
        val root = runCatching { MiniJson.parseOrNull(file.readText()) }.getOrNull().jsonObject() ?: return emptyList()
        return root["ops"].jsonArray().mapNotNull { row ->
            val o = row.jsonObject() ?: return@mapNotNull null
            Entry(o.jsonString("op") ?: return@mapNotNull null, o.jsonString("path") ?: return@mapNotNull null, o.jsonString("volume") ?: return@mapNotNull null, o.jsonString("to"))
        }
    }

    /**
     * Deletes the journalled temps on the [mounted] volumes and returns how many it removed, writing `sweep: removed
     * <n>`. An entry on a volume that is not mounted waits for its mount; an entry that is not a temp, or is the bin, is
     * dropped from the journal untouched. An entry with a destination ([Entry.to]) is a rename a kill stopped between its
     * two steps: the file it names is the user's own, so it is never deleted — the rename is FINISHED ([finish]).
     */
    @Synchronized
    fun sweep(mounted: List<FileVolume>): Int {
        var removed = 0
        val keep = ArrayList<Entry>()
        for (e in entries()) {
            if (e.opId in live) { keep += e; continue }
            val volume = mounted.firstOrNull { it.uuid == e.volumeUuid }
            if (volume == null) { keep += e; continue }
            if (e.to != null) { if (!finish(e, e.to, volume)) keep += e; continue }
            if (!sweepable(e.path, volume)) continue
            val f = File(e.path)
            if (!FilePaths.existsNoFollow(f)) continue
            if (FilePaths.deleteTree(f)) removed++ else keep += e
        }
        runCatching { write(keep) }
        say("sweep: removed $removed")
        return removed
    }

    /**
     * A rename stopped between its two steps: the file sits under its in-between name and goes on to the name it was
     * given (`rename recovered <in-between path> -> <name>`); were something to hold that name by now, to the first
     * "keep both" name. Nothing is ever deleted or replaced here. True when the entry is settled — also when the
     * in-between name is not there (the kill came before the first step or after the second) or the entry is not one
     * this code wrote (left untouched); false when the move failed, so the entry waits for the next sweep.
     */
    private fun finish(e: Entry, to: String, volume: FileVolume): Boolean {
        val temp = File(e.path)
        val wanted = File(to)
        if (!FilePaths.isRenameName(temp.name) || FilePaths.volumeOf(e.path, listOf(volume), canonical) == null || inBin(e.path, volume)) return true
        if (!FilePaths.existsNoFollow(temp)) return true
        val dir = temp.parentFile ?: return true
        if (wanted.parentFile != dir || !FilePaths.validName(wanted.name) || FilePaths.inShellDir(wanted.path, volume, canonical)) return true
        val dest = if (FilePaths.existsNoFollow(wanted)) FilePaths.keepBoth(dir, wanted.name, FilePaths.isRealDirectory(temp)) else wanted
        return try {
            FilePaths.rename(temp, dest, replace = false)
            say("rename recovered ${FilePaths.lineText(temp.path)} -> ${FilePaths.lineText(dest.name)}")
            true
        } catch (e: Exception) {
            false
        }
    }

    /** A temp by name, under its own volume, and never a bin path — asked of the path as written AND as it resolves. */
    private fun sweepable(path: String, volume: FileVolume): Boolean {
        if (!FilePaths.isTempName(File(path).name)) return false
        if (FilePaths.volumeOf(path, listOf(volume), canonical) == null) return false
        return !inBin(path, volume)
    }

    private fun inBin(path: String, volume: FileVolume): Boolean {
        val bin = RecycleBin.binDir(volume).path
        val resolved = runCatching { canonical(path) }.getOrNull() ?: return true
        val binResolved = runCatching { canonical(bin) }.getOrNull() ?: bin
        fun inside(p: String, b: String) = p.equals(b, ignoreCase = true) || p.startsWith("$b/", ignoreCase = true)
        return inside(path, bin) || inside(resolved, binResolved)
    }

    private fun write(entries: List<Entry>) {
        dir.mkdirs()
        val rows = entries.map { e -> linkedMapOf("op" to e.opId, "path" to e.path, "volume" to e.volumeUuid).also { row -> e.to?.let { row["to"] = it } } }
        FilePaths.writeAtomic(file, MiniJson.write(linkedMapOf("ops" to rows)))
    }

    companion object {
        const val FILE_NAME = "files-ops.json"
    }
}
