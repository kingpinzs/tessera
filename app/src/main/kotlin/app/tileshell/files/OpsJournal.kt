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
    data class Entry(val opId: String, val path: String, val volumeUuid: String)

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
    fun put(opId: String, path: String, volumeUuid: String) {
        live += opId
        write(entries().filter { it.opId != opId } + Entry(opId, path, volumeUuid))
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
            Entry(o.jsonString("op") ?: return@mapNotNull null, o.jsonString("path") ?: return@mapNotNull null, o.jsonString("volume") ?: return@mapNotNull null)
        }
    }

    /**
     * Deletes the journalled temps on the [mounted] volumes and returns how many it removed, writing `sweep: removed
     * <n>`. An entry on a volume that is not mounted waits for its mount; an entry that is not a temp, or is the bin, is
     * dropped from the journal untouched.
     */
    @Synchronized
    fun sweep(mounted: List<FileVolume>): Int {
        var removed = 0
        val keep = ArrayList<Entry>()
        for (e in entries()) {
            if (e.opId in live) { keep += e; continue }
            val volume = mounted.firstOrNull { it.uuid == e.volumeUuid }
            if (volume == null) { keep += e; continue }
            if (!sweepable(e.path, volume)) continue
            val f = File(e.path)
            if (!FilePaths.existsNoFollow(f)) continue
            if (FilePaths.deleteTree(f)) removed++ else keep += e
        }
        runCatching { write(keep) }
        say("sweep: removed $removed")
        return removed
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
        val rows = entries.map { linkedMapOf("op" to it.opId, "path" to it.path, "volume" to it.volumeUuid) }
        FilePaths.writeAtomic(file, MiniJson.write(linkedMapOf("ops" to rows)))
    }

    companion object {
        const val FILE_NAME = "files-ops.json"
    }
}
