package app.tileshell.files

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Files' ONE write layer (phase 18, r3 D7; the standard of `media/MediaWrites.kt` and `people/PeopleWrites.kt`): copy,
 * move, rename, new folder, the Recycle Bin's delete / restore / purge / empty, zip extract and zip create — over
 * `java.io`, with everything of the phone injected. The UI and the copy service call nothing else to write.
 * Its rules, each proven on a temp dir by the `FileOps*Test` classes (E18):
 *  1. **Only a path under a mounted volume is written or read** — asked of the CANONICAL path (symlinks and `..`
 *     resolved), so a path that resolves outside every [volumes] root is refused before anything is touched.
 *  2. **A file appears whole or not at all.** A copy is written to `.<name>.<opid>.part` in the destination folder and
 *     renamed when complete; an extract goes to `.<zip>.<opid>.extract/` and is renamed likewise (r3 D4). A cancel or a
 *     failure removes the temp; a kill cannot, so each temp is named in the [journal] BEFORE it is made and
 *     [OpsJournal.sweep] removes it afterwards.
 *  3. **Nothing is overwritten unasked.** A name already there asks [Conflict]: replace, keep both (`name (2).ext`,
 *     then `(3)`…) or skip. Replacing a folder with a folder merges into it (its other contents stay).
 *     **"Replace" never removes anything** (rule 5): what the new item takes the place of — a file, or a folder with
 *     everything in it — goes to its volume's bin first ([displace]), with its own `bin delete <path>: ok` line; when
 *     it cannot be binned the operation fails and what was there stays.
 *  4. **A move never loses the file.** On one volume it is a rename; across volumes it is copy, then delete — and a
 *     failure after the copy leaves both, never neither.
 *  5. **A delete goes to the bin** ([RecycleBin]'s rules), never straight off the disk.
 *  6. **Every write is followed by a scan** of the paths it touched ([scan]; T18-4), and by Recent's upkeep ([recent]).
 *  7. **The shell's own folder is not the user's.** No write here has a source, a destination or a resulting path inside
 *     `<volume>/.Tessera` ([FilePaths.inShellDir]: any case, as written and as resolved) — refused with [SHELL] — and a
 *     whole volume is never a source (it holds that folder). The bin's writes ([RecycleBin]) and the nested-zip copy
 *     ([ZipWrites.openNested]) are the only code that writes there.
 * Each operation writes its one `[files]` line through [say] and returns the same outcome as an [OpResult].
 *
 * @param volumes the volumes mounted NOW (`StorageManager.getStorageVolumes()`); asked again when a write fails, so a
 *   pulled volume reads "storage removed"
 * @param canonical `File.canonicalPath`, null when it cannot be resolved
 * @param scan `MediaScannerConnection.scanFile` for the given paths (files that are there, and files that are gone)
 * @param say one `[files]` diagnostics line, without its tag (`Diagnostics.add("files", it)`)
 * @param access All-files access is held now (`Environment.isExternalStorageManager()`)
 * @param freeSpace usable bytes in the folder's volume
 * @param removeSource removes one copied file (or emptied folder) of a cross-volume move
 * @param openZip the platform's `ZipFile` ([ZipArchive.open])
 */
class FileOps(
    private val volumes: () -> List<FileVolume>,
    private val canonical: (String) -> String?,
    clock: () -> Long,
    private val scan: (List<String>) -> Unit,
    private val say: (String) -> Unit,
    private val journal: OpsJournal,
    private val recent: RecentStore? = null,
    private val access: () -> Boolean = { true },
    private val freeSpace: (File) -> Long = { it.usableSpace },
    private val removeSource: (File) -> Boolean = { it.delete() },
    private val openZip: (File) -> ZipFile = { ZipFile(it, Charsets.ISO_8859_1) },
) {
    /** The bin's reader (its page's rows, phase 19's stats). Its writes are this class's `bin*` functions. */
    val bin = RecycleBin(clock, canonical, say)

    private val zips = ZipWrites(this, journal, say, freeSpace)

    // ---- copy and move

    /** "Copy to": each of [sources] (a file, or a folder with everything in it) into [destDir]. */
    fun copy(sources: List<File>, destDir: File, control: OpControl = OpControl(), conflict: (File) -> Conflict): OpResult =
        transfer("copy", move = false, sources, destDir, control, conflict)

    /** "Move to": a rename on one volume; copy, then delete, across volumes. */
    fun move(sources: List<File>, destDir: File, control: OpControl = OpControl(), conflict: (File) -> Conflict): OpResult =
        transfer("move", move = true, sources, destDir, control, conflict)

    private fun transfer(verb: String, move: Boolean, sources: List<File>, destDir: File, control: OpControl, conflict: (File) -> Conflict): OpResult {
        val opId = journal.newOpId()
        val run = Transfer(move, opId, control, conflict)
        var files = 0
        var bytes = 0L
        val result = guarded(destDir, sources) {
            val dest = volumeFor(destDir) ?: throw Refused(OUTSIDE)
            notShell(destDir)
            if (!destDir.isDirectory) throw Refused("the destination folder is gone")
            val destCanon = canonical(destDir.path) ?: throw Refused(OUTSIDE)
            var toCopy = 0L
            val plan = sources.map { src ->
                val volume = volumeFor(src) ?: throw Refused(OUTSIDE)
                notSource(src, volume)
                if (!FilePaths.existsNoFollow(src)) throw Refused("${FilePaths.lineText(src.name)} is gone")
                val srcCanon = canonical(src.path) ?: throw Refused(OUTSIDE)
                if (FilePaths.isRealDirectory(src) && (destCanon == srcCanon || FilePaths.under(destCanon, srcCanon))) {
                    throw Refused("the destination is inside ${FilePaths.lineText(src.name)}")
                }
                val size = FilePaths.treeBytes(src)
                files += countFiles(src)
                bytes += size
                val cross = volume != dest
                if (!move || cross) toCopy += size
                src to cross
            }
            run.total = bytes
            run.destVolume = dest
            // Asked before the first byte, so a copy that cannot fit leaves nothing behind (and again by the write itself).
            if (toCopy > 0 && toCopy > freeSpace(destDir)) throw Refused(NO_SPACE)
            for ((src, cross) in plan) run.top(src, destDir, cross)
            OpResult.Done(run.outputs, run.skipped)
        }
        journal.end(opId)
        if (run.touched.isNotEmpty()) scan(run.touched)
        say("$verb $files files $bytes -> ${FilePaths.lineText(destDir.path)} ${tail(result)}")
        return result
    }

    /** One copy or move while it runs: what it wrote, what it touched. */
    private inner class Transfer(val move: Boolean, val opId: String, control: OpControl, val conflict: (File) -> Conflict) {
        val meter = Meter(control)
        var total: Long
            get() = meter.total
            set(value) { meter.total = value }
        lateinit var destVolume: FileVolume
        val outputs = ArrayList<String>()
        val touched = ArrayList<String>()
        var skipped = 0

        /** One selected item into [dir]. */
        fun top(src: File, dir: File, cross: Boolean) {
            val target = place(src, dir, cross, forced = null, top = true)
            if (target == null) { skipped++; return }
            outputs += target.path
            if (move) recent?.renamePath(src.path, target.path, destVolume.uuid)
        }

        /**
         * Puts [src] in [dir] and returns where it went, or null when it was skipped. [forced] answers for a merge's
         * contents. [top] is a selected item: its resulting path is asked rule 7 (what lies under it follows it).
         */
        private fun place(src: File, dir: File, cross: Boolean, forced: Conflict?, top: Boolean = false): File? {
            meter.check()
            // A link is never followed and never copied (shared storage holds none — FUSE; Edge cases).
            if (FilePaths.isSymlink(src)) return null
            val isDir = FilePaths.isRealDirectory(src)
            var target = File(dir, src.name)
            var displace = false
            var merge = false
            // An item named like the shell's folder, landing at a volume's root, would BE that folder.
            if (top) notShell(target)
            if (FilePaths.existsNoFollow(target)) {
                if (sameFile(src, target)) {
                    // Onto itself: a move has nothing to do; a copy beside its original is "keep both".
                    if (move) return null
                    target = FilePaths.keepBoth(dir, src.name, isDir)
                } else when (forced ?: conflict(target)) {
                    Conflict.SKIP -> return null
                    Conflict.KEEP_BOTH -> target = FilePaths.keepBoth(dir, src.name, isDir)
                    // A folder met by a folder merges; anything else that is there goes to the bin (rule 5), never off the disk.
                    Conflict.REPLACE -> if (isDir && FilePaths.isRealDirectory(target)) merge = true else displace = true
                }
            }
            if (!isDir) {
                if (move && !cross) {
                    if (displace) displace(target)
                    FilePaths.rename(src, target, replace = false)
                } else {
                    copyFile(src, target, displace)
                }
                touched += target.path
                if (move) {
                    touched += src.path
                    // Copy, THEN delete (rule 4): were this to fail, both files are there.
                    if (cross && !removeSource(src)) throw Refused("copied, but the original could not be removed")
                }
                return target
            }
            if (displace) displace(target)
            if (move && !cross && !merge) {
                val before = filesUnder(src)
                FilePaths.rename(src, target, replace = false)
                touched += before
                touched += before.map { target.path + it.substring(src.path.length) }
                return target
            }
            if (!merge && !target.mkdir()) throw java.io.IOException("mkdir")
            // A new folder has nothing to conflict with; a merged one was answered "replace" as a whole.
            src.listFiles()?.sortedBy { it.name }?.forEach { place(it, target, cross, if (merge) Conflict.REPLACE else forced) }
            if (!merge) target.setLastModified(src.lastModified())
            // The moved folder is empty now unless something in it was skipped; a folder that is not empty stays.
            if (move) { if (cross) removeSource(src) else src.delete() }
            return target
        }

        /**
         * Rule 2: the bytes go to the journalled temp, and the temp becomes [target] only when it is whole. With
         * [displace], what is at [target] is binned at that moment — after the copy, so a cancelled or failed copy
         * leaves it where it was.
         */
        private fun copyFile(src: File, target: File, displace: Boolean) {
            val temp = File(target.parentFile, FilePaths.partName(target.name, opId))
            journal.put(opId, temp.path, destVolume.uuid)
            try {
                FileInputStream(src).use { input -> FileOutputStream(temp).use { out -> meter.pump(input, out) } }
                temp.setLastModified(src.lastModified())
                if (displace) displace(target)
                FilePaths.rename(temp, target, replace = false)
            } finally {
                if (FilePaths.existsNoFollow(temp)) temp.delete()
            }
        }
    }

    // ---- rename and new folder

    /** Renames [file] in its folder; a name something else holds is refused (a change of case alone is allowed — FAT). */
    fun rename(file: File, newName: String): OpResult {
        val target = File(file.parentFile, newName)
        val moved = ArrayList<String>()
        val result = guarded(file) {
            volumeFor(file) ?: throw Refused(OUTSIDE)
            if (!FilePaths.validName(newName)) throw Refused("not a name a file can have")
            notShell(file, target)
            if (!FilePaths.existsNoFollow(file)) throw Refused("${FilePaths.lineText(file.name)} is gone")
            val before = if (FilePaths.isRealDirectory(file)) filesUnder(file) else listOf(file.path)
            if (FilePaths.existsNoFollow(target)) {
                if (!sameFile(file, target)) throw Refused("the name is taken")
                // The same file under another case (FAT): `Files.move` would call that done; rename(2) changes the name.
                if (!file.renameTo(target)) throw java.io.IOException("rename")
            } else {
                FilePaths.rename(file, target, replace = false)
            }
            moved += before
            moved += before.map { target.path + it.substring(file.path.length) }
            recent?.renamePath(file.path, target.path)
            OpResult.Done(listOf(target.path))
        }
        if (moved.isNotEmpty()) scan(moved)
        say("rename ${FilePaths.lineText(file.path)} -> ${FilePaths.lineText(newName)}: ${outcome(result)}")
        return result
    }

    /** Makes the folder [name] in [parent]; a name something holds is refused. */
    fun newFolder(parent: File, name: String): OpResult {
        val target = File(parent, name)
        val result = guarded(parent) {
            volumeFor(parent) ?: throw Refused(OUTSIDE)
            if (!FilePaths.validName(name)) throw Refused("not a name a folder can have")
            notShell(parent, target)
            if (!parent.isDirectory) throw Refused("the folder is gone")
            if (FilePaths.existsNoFollow(target)) throw Refused("the name is taken")
            if (!target.mkdir()) throw java.io.IOException("mkdir")
            OpResult.Done(listOf(target.path))
        }
        if (result is OpResult.Done) scan(listOf(target.path))
        say("new folder ${FilePaths.lineText(target.path)}: ${outcome(result)}")
        return result
    }

    // ---- the Recycle Bin (T18-1, r3 D3): the reader is [bin]; these are its writes

    /** Delete: [file] goes to its own volume's bin, or stays exactly where it was. `bin delete <path>: ok | failed <why>`. */
    fun binDelete(file: File): OpResult {
        var gone = emptyList<String>()
        val result = guarded(file) {
            val volume = volumeFor(file) ?: throw Refused(OUTSIDE)
            if (canonical(file.path) == canonical(volume.root)) throw Refused("a volume cannot be deleted")
            if (FilePaths.inShellDir(file.path, volume, canonical)) throw Refused("not a file Files deletes")
            val before = if (FilePaths.isRealDirectory(file)) filesUnder(file) else listOf(file.path)
            bin.delete(file, volume)?.let { throw Refused(it) }
            gone = before
            recent?.remove(file.path)
            OpResult.Done()
        }
        // The scan of the source path: MediaStore drops the row, so Photos, Music and the Photos tile lose the file.
        if (gone.isNotEmpty()) scan(gone)
        say("bin delete ${FilePaths.lineText(file.path)}: ${outcome(result)}")
        return result
    }

    /**
     * Restore: [entry] back where it was (or in `Download/Restored/` when no record says where); a clash asks
     * [conflict]. A skip is the user's own answer: it returns [OpResult.Done] with `skipped = 1` and writes no line.
     */
    fun binRestore(entry: RecycleBin.Entry, conflict: (File) -> Conflict): OpResult {
        var shown = entry.originalPath ?: entry.binName
        var back = emptyList<String>()
        val result = guarded(File(entry.volume.root)) {
            mounted(entry.volume)
            when (val r = bin.restore(entry, conflict, ::binned)) {
                is RecycleBin.Restored.To -> {
                    shown = r.file.path
                    back = if (FilePaths.isRealDirectory(r.file)) filesUnder(r.file) else listOf(r.file.path)
                    OpResult.Done(listOf(r.file.path))
                }
                RecycleBin.Restored.Skipped -> OpResult.Done(skipped = 1)
                is RecycleBin.Restored.Failed -> { shown = r.target; throw Refused(r.why) }
            }
        }
        if (back.isNotEmpty()) scan(back)
        if (!(result is OpResult.Done && result.skipped > 0)) say("bin restore ${FilePaths.lineText(shown)}: ${outcome(result)}")
        return result
    }

    /** "Delete permanently" (also what a delete made on the bin page is). `bin purge <path>: ok | failed <why>`. */
    fun binPurge(entry: RecycleBin.Entry): OpResult {
        val result = guarded(File(entry.volume.root)) {
            mounted(entry.volume)
            bin.purge(entry)?.let { throw Refused(it) }
            OpResult.Done()
        }
        say("bin purge ${FilePaths.lineText(entry.originalPath ?: File(RecycleBin.binDir(entry.volume), entry.binName).path)}: ${outcome(result)}")
        return result
    }

    /** "Empty", and the `empty(volume)` call phase 19's Storage row uses (T18-9). `bin empty <volume root>: ok | failed <why>`. */
    fun binEmpty(volume: FileVolume): OpResult {
        val result = guarded(File(volume.root)) {
            mounted(volume)
            bin.empty(volume)?.let { throw Refused(it) }
            OpResult.Done()
        }
        say("bin empty ${FilePaths.lineText(volume.root)}: ${outcome(result)}")
        return result
    }

    // ---- zip (T18-2, r3 D6)

    /** Extracts [zip] to `<zip base name>/` beside it, under the entry-name, free-space and bomb guards. */
    fun zipExtract(zip: File, control: OpControl = OpControl(), conflict: (File) -> Conflict): OpResult =
        zips.extract(zip, zip.absoluteFile.parentFile ?: zip, control, conflict)

    /** Makes `<the single item's name>.zip`, or `Archive.zip` for more than one item, in [destDir] (the current folder). */
    fun zipCreate(sources: List<File>, destDir: File, control: OpControl = OpControl(), conflict: (File) -> Conflict): OpResult =
        zips.create(sources, destDir, control, conflict)

    /** A zip inside [outer], copied to `<volume>/.Tessera/tmp/` so it can be opened in its turn; [zipCloseNested] removes it. */
    fun zipOpenNested(outer: ZipArchive, row: ZipRow, control: OpControl = OpControl()): NestedZip = zips.openNested(outer, row, control)

    /** Leaving a nested zip's virtual root: its copy is removed. */
    fun zipCloseNested(nested: NestedZip.Opened) = zips.closeNested(nested)

    /** Opens [zip] as a folder, with its line ([ZipArchive.open]) — a read, here so the caller holds one object. */
    fun zipOpen(zip: File): ZipOpen = ZipArchive.open(zip, say, openZip)

    // ---- what every operation shares

    /**
     * Rule 5 for a conflict's "Replace": [target] — the file, or the folder with everything in it, that a new item is
     * about to take the place of — goes to its volume's bin ([binDelete]: its line, its scan, Recent's upkeep). Null
     * when it is there; else why it is not, and then [target] is untouched and must not be replaced.
     */
    private fun binned(target: File): String? =
        (binDelete(target) as? OpResult.Failed)?.let { "what is there could not go to the Recycle Bin (${it.reason})" }

    /** As [binned], for a running operation: one that cannot bin what is in its way stops there. */
    internal fun displace(target: File) {
        binned(target)?.let { throw Refused(it) }
    }

    /** The mounted volume [file] resolves under, or null (rule 1). */
    internal fun volumeFor(file: File): FileVolume? = FilePaths.volumeOf(file.path, volumes(), canonical)

    internal fun canonicalOf(file: File): String? = runCatching { canonical(file.path) }.getOrNull()

    /** [file] is a mounted volume's shell folder, or inside one (rule 7). */
    internal fun inShell(file: File): Boolean = volumes().any { FilePaths.inShellDir(file.path, it, canonical) }

    /** Rule 7: none of [files] — a source, a destination, a path about to be made — is in a volume's shell folder. */
    internal fun notShell(vararg files: File) {
        if (files.any(::inShell)) throw Refused(SHELL)
    }

    /** Rule 7 for a source on [volume]: not in the shell's folder, and not the volume itself (which holds it). */
    internal fun notSource(src: File, volume: FileVolume) {
        notShell(src)
        if (canonicalOf(src) == canonicalOf(File(volume.root))) throw Refused(VOLUME_SOURCE)
    }

    internal fun scanPaths(paths: List<String>) = scan(paths)

    private fun mounted(volume: FileVolume) {
        if (volume !in volumes()) throw Refused(STORAGE_REMOVED)
    }

    /**
     * Runs [body] as one operation: the grant is asked first, and whatever stops it becomes the [OpResult] — a cancel, the
     * time limit, a refusal with its reason, or a failed write named by what is true of the phone right after it
     * ([why]). [places] are the folders and files the operation works on.
     */
    internal fun guarded(vararg places: Any?, body: () -> OpResult): OpResult = try {
        if (!access()) throw Refused(ACCESS_REMOVED)
        body()
    } catch (e: Stopped) {
        if (e.reason == StopReason.CANCEL) OpResult.Cancelled else OpResult.Failed(TIME_LIMIT)
    } catch (e: Refused) {
        OpResult.Failed(e.reason)
    } catch (e: Overrun) {
        OpResult.Failed("bigger than it declares")
    } catch (e: Exception) {
        OpResult.Failed(why(e, places.flatMap { if (it is Collection<*>) it.filterIsInstance<File>() else listOfNotNull(it as? File) }))
    }

    /** Why a write failed, in the doc's words: the grant gone, the volume gone, the volume full — else the error's name. */
    private fun why(e: Exception, places: List<File>): String = when {
        !runCatching(access).getOrDefault(true) -> ACCESS_REMOVED
        places.any { p -> volumeFor(p)?.let { !File(it.root).isDirectory } ?: true } -> STORAGE_REMOVED
        e.message.orEmpty().let { it.contains("ENOSPC") || it.contains("No space left", ignoreCase = true) } -> NO_SPACE
        places.any { it.isDirectory && freeSpace(it) < Meter.CHUNK } -> NO_SPACE
        e is java.util.zip.ZipException -> "damaged"
        else -> e.javaClass.simpleName
    }

    internal fun filesUnder(dir: File): List<String> {
        val out = ArrayList<String>()
        fun walk(f: File) {
            if (FilePaths.isSymlink(f)) return
            if (FilePaths.isRealDirectory(f)) f.listFiles()?.forEach(::walk) else out += f.path
        }
        walk(dir)
        return out
    }

    internal fun countFiles(f: File): Int = when {
        FilePaths.isSymlink(f) -> 0
        FilePaths.isRealDirectory(f) -> f.listFiles()?.sumOf { countFiles(it) } ?: 0
        else -> 1
    }

    private fun sameFile(a: File, b: File): Boolean = runCatching { Files.isSameFile(a.toPath(), b.toPath()) }.getOrDefault(false)

    companion object {
        // The failure reasons the doc names (Edge cases, r3 D4, r3 D8).
        const val NO_SPACE = "not enough space"
        const val STORAGE_REMOVED = "storage removed"
        const val ACCESS_REMOVED = "access removed"
        const val TIME_LIMIT = "time limit"

        /** A path that resolves under no mounted volume (rule 1; T18-11's words). */
        const val OUTSIDE = "outside shared storage"

        /** A source, a destination or a resulting path in a volume's `.Tessera` (rule 7; the GATE review's M4). */
        const val SHELL = "inside the shell's own folder"

        /** A volume's root handed in as a source (rule 7). */
        const val VOLUME_SOURCE = "a whole volume cannot be the source"

        /** `done | cancelled | failed <reason>`: the end of a copy, move, extract or create line. */
        internal fun tail(result: OpResult): String = when (result) {
            is OpResult.Done -> "done"
            OpResult.Cancelled -> "cancelled"
            is OpResult.Failed -> "failed ${FilePaths.lineText(result.reason)}"
        }

        /** `ok | failed <why>`: the end of a bin, rename or new-folder line. */
        internal fun outcome(result: OpResult): String = when (result) {
            is OpResult.Done -> "ok"
            OpResult.Cancelled -> "failed cancelled"
            is OpResult.Failed -> "failed ${FilePaths.lineText(result.reason)}"
        }
    }
}

/** The operation was told to stop. */
internal class Stopped(val reason: StopReason) : Exception()

/** The operation will not do this; [reason] is its line's `failed <reason>`. */
internal class Refused(val reason: String) : Exception()

/** More bytes came out of a zip entry than the archive declared, plus the allowance: [written] were written. */
internal class Overrun(val written: Long) : Exception()

/**
 * The one byte loop (Q-18-3: "every loop that writes `[files] … progress`"): the stop check before each chunk, the
 * write, the progress call, the pace. [written] and [total] count the whole operation, across its files.
 */
internal class Meter(private val control: OpControl, var total: Long = 0L) {
    var written = 0L
        private set

    fun check() {
        control.stopped()?.let { throw Stopped(it) }
    }

    /** Copies [input] to [out]; never writes a byte past [limit] of the operation's total ([Overrun] instead). */
    fun pump(input: InputStream, out: OutputStream, limit: Long = Long.MAX_VALUE) {
        val buffer = ByteArray(CHUNK)
        while (true) {
            check()
            val n = input.read(buffer)
            if (n < 0) return
            if (n == 0) continue
            if (written + n > limit) throw Overrun(written)
            out.write(buffer, 0, n)
            written += n
            control.progress(written, total)
            try {
                control.pace?.wrote(written)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw Stopped(StopReason.CANCEL)
            }
        }
    }

    companion object {
        const val CHUNK = 64 * 1024
    }
}
