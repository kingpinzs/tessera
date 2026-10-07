package app.tileshell.files

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonLong
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File

/**
 * The shell-owned Recycle Bin (Q3 C; T18-1, r3 D3): one bin per volume at `<volume root>/.Tessera/bin/`, holding a
 * `.nomedia` marker and `.index.json` — one record per binned file (bin name, original path, deleted-at, size), written
 * temp-and-rename. Nothing is ever removed from it automatically.
 *
 * Reading is public — [list] for the bin page, [stats] for phase 19's Storage row (T18-9). The four WRITES ([delete],
 * [restore], [purge], [empty]) are `internal` and reached only through [FileOps] (r3 D7), which adds the volume check,
 * the scan and Recent's upkeep. Its rules, each proven by `FileOpsBinTest` on a temp dir:
 *  1. **A delete can never overwrite or half-happen.** The index record is written FIRST, then the file is renamed
 *     inside its own volume to `<deleted-at ms>-<seq>-<name cut to fit 255 bytes, extension kept>`; `seq` is bumped
 *     until no bin file and no record holds that name, compared ignoring case (FAT). An index that cannot be written
 *     fails the delete and the file stays where it was.
 *  2. **Damage loses nothing.** Index missing or unreadable → the bin's files are listed by bin name and restore to
 *     `<volume>/Download/Restored/`, with `bin index <volume>: rebuilt (<why>)`; a record whose file is gone is dropped
 *     on the next read; a bin file with no record lists by its bin name; a bin folder that is gone is made again by the
 *     next delete (`rebuilt (bin folder missing)`).
 *  3. **The index is shared storage, so it is not trusted.** A record's original path is honoured only in the exact
 *     form a delete writes ([restorable]): already canonical (no `.` or `..` segment, no doubled separator, no link on
 *     the way), under its own volume, not the volume's root, outside `.Tessera` and outside `Android/`, ending in a
 *     valid name. Any other record is no record at all: it is dropped at the next read, its file lists by its bin name
 *     and restores to `Download/Restored/`. A bin name is one plain file name. Nothing here follows a symlink.
 *  4. **A row says where Restore will put it** ([Entry.restoreDir]), and Restore puts it exactly there
 *     ([restoreTarget] answers both), so the user sees the destination before the tap.
 *
 * @param say one `[files]` diagnostics line, without its tag
 */
class RecycleBin(
    private val clock: () -> Long,
    private val canonical: (String) -> String?,
    private val say: (String) -> Unit,
) {
    /**
     * One row of the bin page. [name] is the original name — or the bin name when no record names the file ([indexed]
     * false: [originalPath] and [deletedAt] are then null). [size] is the record's, or the file's own.
     */
    data class Entry(
        val volume: FileVolume,
        val binName: String,
        val name: String,
        val originalPath: String?,
        val deletedAt: Long?,
        val size: Long,
        val isDirectory: Boolean,
    ) {
        val indexed: Boolean get() = originalPath != null

        /** The folder Restore puts this entry in: its original folder, or `<volume>/Download/Restored` with no record (rule 4). */
        val restoreDir: String get() = restoreTarget(volume, originalPath, binName).parent ?: volume.root
    }

    /** The published reader's answer for phase 19 (T18-9): how many entries a volume's bin holds, and their bytes. */
    data class Stats(val entries: Int, val bytes: Long)

    private data class Record(val bin: String, val path: String, val deletedAt: Long, val size: Long)

    /** The index as read: its usable records, and why it had to be rebuilt (null when it read cleanly). */
    private class Index(val records: List<Record>, val damage: String?, val dropped: Boolean)

    /** The bin's rows for [volume], newest delete first, then the files no record names. Writes the `bin index` line. */
    @Synchronized
    fun list(volume: FileVolume): List<Entry> {
        val bin = binDir(volume)
        if (!FilePaths.isRealDirectory(bin)) {
            say("bin index ${volume.root}: 0 entries")
            return emptyList()
        }
        val index = load(volume)
        // Rebuilt, or a record dropped: the next read starts from what is true now. Best effort — a read never fails on it.
        if (index.damage != null || index.dropped) runCatching { writeIndex(volume, index.records) }
        val entries = entries(volume, index.records)
        say(if (index.damage != null) "bin index ${volume.root}: rebuilt (${index.damage})" else "bin index ${volume.root}: ${entries.size} entries")
        return entries
    }

    /** The bin's entry count and bytes for [volume], without a line and without changing anything (phase 19's reader). */
    @Synchronized
    fun stats(volume: FileVolume): Stats {
        if (!FilePaths.isRealDirectory(binDir(volume))) return Stats(0, 0)
        val entries = entries(volume, load(volume).records)
        return Stats(entries.size, entries.sumOf { it.size })
    }

    // ---- the writes: FileOps' only (r3 D7)

    /** Moves [file] into its volume's bin. Returns null when it is there, else why it is not — and then [file] is untouched. */
    @Synchronized
    internal fun delete(file: File, volume: FileVolume): String? {
        if (!FilePaths.existsNoFollow(file)) return "not found"
        val bin = binDir(volume)
        var rebuilt: String? = null
        if (!FilePaths.isRealDirectory(bin)) {
            if (!bin.mkdirs() && !FilePaths.isRealDirectory(bin)) return "the bin folder cannot be made"
            rebuilt = "bin folder missing"
        }
        val nomedia = File(bin, NOMEDIA)
        // Without the marker MediaStore would index the bin and Photos would show what was deleted.
        if (!nomedia.exists() && !runCatching { nomedia.createNewFile() }.getOrDefault(false)) return "the bin folder cannot be written"
        val index = if (rebuilt != null) Index(emptyList(), null, false) else load(volume)
        rebuilt = rebuilt ?: index.damage
        if (rebuilt != null) say("bin index ${volume.root}: rebuilt ($rebuilt)")

        val now = clock()
        val taken = (bin.list()?.toList().orEmpty() + index.records.map { it.bin }).map { it.lowercase() }.toHashSet()
        var seq = 0
        var name: String
        do { name = FilePaths.fitName("$now-${seq++}-", file.name, isDirectory = FilePaths.isRealDirectory(file)) } while (name.lowercase() in taken)

        // The record FIRST (r3 D3): a file is never in the bin without its original path on record — in the canonical
        // form of its folder, the only form a read honours (rule 3).
        val folder = file.absoluteFile.parentFile?.let { runCatching { canonical(it.path) }.getOrNull() }
        val record = Record(name, if (folder == null) file.path else File(folder, file.name).path, now, FilePaths.treeBytes(file))
        try {
            writeIndex(volume, index.records + record)
        } catch (e: Exception) {
            return "the bin index cannot be written (${e.javaClass.simpleName})"
        }
        try {
            FilePaths.rename(file, File(bin, name), replace = false)
        } catch (e: Exception) {
            // The file stays where it was; its record goes again (and would be dropped on the next read anyway).
            runCatching { writeIndex(volume, index.records) }
            return "not moved to the bin (${e.javaClass.simpleName})"
        }
        return null
    }

    /** What [restore] did. */
    internal sealed interface Restored {
        data class To(val file: File) : Restored
        data object Skipped : Restored
        data class Failed(val why: String, val target: String) : Restored
    }

    /**
     * Puts [entry] back: at its original path, the folder recreated when it is gone — or, with no usable record, in
     * `<volume>/Download/Restored/` under its bin name. Something already there (the name itself, or a FILE where the
     * original folder was) asks [conflict]. "Replace" removes nothing: what is there is handed to [displace]
     * ([FileOps]' bin delete — null when it is binned, else why not), and when that fails so does the restore.
     */
    @Synchronized
    internal fun restore(entry: Entry, conflict: (File) -> Conflict, displace: (File) -> String?): Restored {
        val volume = entry.volume
        val source = binFile(volume, entry.binName) ?: return Restored.Failed("not a bin entry", entry.binName)
        // The record is asked again here: an entry is only as good as the read it came from.
        var target = restoreTarget(volume, entry.originalPath?.takeIf { restorable(it, volume) }, entry.binName)
        // Whatever the record says and wherever a folder on the way leads: never back into the shell's own folder.
        if (FilePaths.inShellDir(target.path, volume, canonical)) return Restored.Failed(FileOps.SHELL, target.path)
        if (!FilePaths.existsNoFollow(source)) return Restored.Failed("not in the bin any more", target.path)
        val isDir = FilePaths.isRealDirectory(source)

        // The original folder, made again when it is gone; a FILE standing where a folder was is a conflict of its own.
        val parent = when (val p = folderFor(target.parentFile ?: return Restored.Failed("no folder", target.path), conflict, displace)) {
            is Folder.At -> p.dir
            Folder.Skipped -> return Restored.Skipped
            is Folder.Failed -> return Restored.Failed(p.why, target.path)
        }
        target = File(parent, target.name)

        if (FilePaths.existsNoFollow(target)) {
            when (conflict(target)) {
                Conflict.SKIP -> return Restored.Skipped
                Conflict.KEEP_BOTH -> target = FilePaths.keepBoth(parent, target.name, isDir)
                // What is there — a file, or a folder with everything in it — goes to the bin, never off the disk.
                Conflict.REPLACE -> displace(target)?.let { return Restored.Failed(it, target.path) }
            }
        }
        try {
            FilePaths.rename(source, target, replace = false)
        } catch (e: Exception) {
            return Restored.Failed("not moved back (${e.javaClass.simpleName})", target.path)
        }
        // The file is out; its record follows (were this write lost, the record is dropped on the next read).
        runCatching { writeIndex(volume, load(volume).records.filter { it.bin != entry.binName }) }
        return Restored.To(target)
    }

    /** "Delete permanently": [entry]'s file and record are gone. Returns null when done, else why not. */
    @Synchronized
    internal fun purge(entry: Entry): String? {
        val source = binFile(entry.volume, entry.binName) ?: return "not a bin entry"
        if (!FilePaths.deleteTree(source)) return "could not be deleted"
        return try {
            writeIndex(entry.volume, load(entry.volume).records.filter { it.bin != entry.binName })
            null
        } catch (e: Exception) {
            // The file IS gone, and so is its record on the next read.
            null
        }
    }

    /** "Empty": everything in [volume]'s bin but its marker and its index, which is left holding zero records. Null when done. */
    @Synchronized
    internal fun empty(volume: FileVolume): String? {
        val bin = binDir(volume)
        if (!FilePaths.isRealDirectory(bin)) return null
        var failed = 0
        bin.listFiles()?.forEach { if (it.name !in RESERVED && !FilePaths.deleteTree(it)) failed++ }
        return try {
            writeIndex(volume, load(volume).records)
            if (failed == 0) null else "$failed could not be deleted"
        } catch (e: Exception) {
            "the bin index cannot be written (${e.javaClass.simpleName})"
        }
    }

    // ---- reading

    private fun entries(volume: FileVolume, records: List<Record>): List<Entry> {
        val bin = binDir(volume)
        val indexed = records.sortedWith(compareByDescending<Record> { it.deletedAt }.thenByDescending { it.bin }).map { r ->
            Entry(volume, r.bin, File(r.path).name.ifEmpty { r.bin }, r.path, r.deletedAt, r.size, FilePaths.isRealDirectory(File(bin, r.bin)))
        }
        val known = records.map { it.bin }.toHashSet()
        val loose = bin.listFiles().orEmpty().filter { it.name !in RESERVED && it.name !in known }.sortedBy { it.name }.map { f ->
            Entry(volume, f.name, f.name, null, null, FilePaths.treeBytes(f), FilePaths.isRealDirectory(f))
        }
        return indexed + loose
    }

    /** The index as it can be trusted: records that are well-formed, name one plain bin file each, and whose file is there. */
    private fun load(volume: FileVolume): Index {
        val bin = binDir(volume)
        val file = File(bin, INDEX)
        if (!FilePaths.existsNoFollow(file)) return Index(emptyList(), "index missing", false)
        val root = runCatching { MiniJson.parseOrNull(file.readText()) }.getOrNull().jsonObject()
        val rows = root?.get("records") as? List<*> ?: return Index(emptyList(), "index unreadable", false)
        var dropped = false
        val seen = HashSet<String>()
        val records = rows.jsonArray().mapNotNull { row ->
            val o = row.jsonObject()
            val name = o?.jsonString("bin")
            val path = o?.jsonString("path")
            val ok = o != null && name != null && path != null && plainBinName(name) && seen.add(name.lowercase()) && FilePaths.existsNoFollow(File(bin, name))
            // A record whose path Restore would not honour is no record (rule 3): the file lists by its bin name.
            if (!ok || !restorable(path!!, volume)) { dropped = true; return@mapNotNull null }
            Record(name, path, o.jsonLong("deletedAt") ?: 0L, o.jsonLong("size") ?: 0L)
        }
        return Index(records, null, dropped)
    }

    private fun writeIndex(volume: FileVolume, records: List<Record>) {
        val rows = records.map { linkedMapOf("bin" to it.bin, "path" to it.path, "deletedAt" to it.deletedAt, "size" to it.size) }
        FilePaths.writeAtomic(File(binDir(volume), INDEX), MiniJson.write(linkedMapOf("version" to 1, "records" to rows)))
    }

    /** The bin file a name stands for — only a plain name directly in the bin, never the marker or the index. */
    private fun binFile(volume: FileVolume, binName: String): File? = if (plainBinName(binName)) File(binDir(volume), binName) else null

    private fun plainBinName(name: String): Boolean = FilePaths.validName(name) && name !in RESERVED

    /**
     * Rule 3: a record's original path is used only in the form a delete writes. Each clause is one way a forged index
     * could send a restore somewhere the bin's row does not show, or somewhere that is not the user's to fill.
     */
    private fun restorable(path: String, volume: FileVolume): Boolean {
        val target = File(path)
        return target.isAbsolute && FilePaths.validName(target.name) &&
            FilePaths.volumeOf(target.path, listOf(volume), canonical) != null &&
            runCatching { canonical(target.path) }.getOrNull() != runCatching { canonical(volume.root) }.getOrNull() &&
            !FilePaths.inShellDir(target.path, volume, canonical) &&
            // Exactly as it resolves: no `.` or `..` segment, no doubled or trailing separator, no link on the way.
            runCatching { canonical(path) }.getOrNull() == path &&
            !underAndroid(path, volume)
    }

    /** `<volume>/Android` or inside it, in any case: the other apps' folders (and the shell's own), never restored into. */
    private fun underAndroid(path: String, volume: FileVolume): Boolean {
        val root = runCatching { canonical(volume.root) }.getOrNull()?.trimEnd('/') ?: return true
        return FilePaths.under(path, root) && path.substring(root.length + 1).substringBefore('/').equals(ANDROID_DIR, ignoreCase = true)
    }

    private sealed interface Folder {
        data class At(val dir: File) : Folder
        data object Skipped : Folder
        data class Failed(val why: String) : Folder
    }

    /** [dir] as a folder that exists: made when it is gone; a file in its way (at any level) is the caller's to answer. */
    private fun folderFor(dir: File, conflict: (File) -> Conflict, displace: (File) -> String?): Folder {
        if (FilePaths.isRealDirectory(dir) || dir.isDirectory) return Folder.At(dir)
        if (FilePaths.existsNoFollow(dir)) {
            return when (conflict(dir)) {
                Conflict.SKIP -> Folder.Skipped
                Conflict.KEEP_BOTH -> made(FilePaths.keepBoth(dir.parentFile ?: return Folder.Failed("no folder"), dir.name, isDirectory = true))
                Conflict.REPLACE -> displace(dir)?.let { Folder.Failed(it) } ?: made(dir)
            }
        }
        val up = dir.parentFile ?: return Folder.Failed("no folder")
        return when (val parent = folderFor(up, conflict, displace)) {
            is Folder.At -> made(File(parent.dir, dir.name))
            else -> parent
        }
    }

    private fun made(dir: File): Folder = if (dir.mkdir() || dir.isDirectory) Folder.At(dir) else Folder.Failed("the folder could not be made")

    companion object {
        const val BIN_DIR = "bin"
        const val NOMEDIA = ".nomedia"
        const val INDEX = ".index.json"

        /** Where a bin file with no usable record is restored, under its volume's root (T18-1). */
        const val RESTORED_DIR = "Download/Restored"

        /** The platform's per-app folders at a volume's root: no record restores into them (rule 3). */
        const val ANDROID_DIR = "Android"

        /**
         * Rule 4: where Restore puts a bin file — its record's [originalPath], or `Download/Restored/<bin name>` with
         * none. The ONE answer, for the row that shows the folder and for the restore that uses it.
         */
        fun restoreTarget(volume: FileVolume, originalPath: String?, binName: String): File =
            originalPath?.let(::File) ?: File(File(volume.root, RESTORED_DIR), binName)

        /** The bin's own two files (and the index's temp): never listed, restored, purged or emptied. */
        val RESERVED = setOf(NOMEDIA, INDEX, "$INDEX.tmp")

        fun binDir(volume: FileVolume): File = File(File(volume.root, FilePaths.SHELL_DIR), BIN_DIR)
    }
}
