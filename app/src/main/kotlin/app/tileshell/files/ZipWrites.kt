package app.tileshell.files

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A zip inside a zip, once [FileOps.zipOpenNested] has copied it out (r3 D6). */
sealed interface NestedZip {
    /** [file] is the copy in `<volume>/.Tessera/tmp/`: open it with [FileOps.zipOpen], and hand this back to [FileOps.zipCloseNested]. */
    data class Opened(val file: File, val opId: String, val name: String) : NestedZip
    data object Cancelled : NestedZip
    data class Failed(val reason: String) : NestedZip
}

/**
 * [FileOps]' zip half (T18-2, r3 D6): extract, create and the nested-zip copy, reached only through [FileOps]. The
 * guards that make extraction safe, each proven by `FileOpsZipTest` on `make_zips.py`'s fixtures:
 *  - **entry names**: an entry whose name is absolute or holds `..` ([ZipNames.normalise]), or whose canonical path
 *    would land outside the extract root, is refused with `zip: refused entry <name>` while the others extract;
 *  - **free space**: the declared uncompressed total of what will be written is summed first and the extract refused
 *    above the volume's free space − 50 MB, before any write — `zip: refused (needs <bytes>, free <bytes>)`;
 *  - **the bomb**: no byte is written past the declared total + 1 MB; the extract stops, its temp folder is removed —
 *    `zip: stopped at <bytes> (declared <bytes>)`;
 *  - **encrypted** (general-purpose flag bit 0): unsupported, nothing written — `zip: encrypted <path>`;
 *  - **a symlink entry** is written as a regular file holding the link text — nothing here ever makes or follows a link.
 */
internal class ZipWrites(
    private val ops: FileOps,
    private val journal: OpsJournal,
    private val say: (String) -> Unit,
    private val freeSpace: (File) -> Long,
) {
    fun extract(zip: File, destDir: File, control: OpControl, conflict: (File) -> Conflict): OpResult {
        val opId = journal.newOpId()
        var target = File(destDir, extractFolderName(zip.name))
        var temp: File? = null
        var skipped = false
        var written = emptyList<String>()
        val result = ops.guarded(destDir, zip) {
            ops.volumeFor(zip) ?: throw Refused(FileOps.OUTSIDE)
            val volume = ops.volumeFor(destDir) ?: throw Refused(FileOps.OUTSIDE)
            val archive = when (val opened = ops.zipOpen(zip)) {
                is ZipOpen.Opened -> opened.archive
                ZipOpen.Encrypted -> throw Refused("password-protected")
                is ZipOpen.Failed -> throw Refused("the zip can't be opened")
            }
            archive.use {
                // The name guard's first half, and the total the archive declares for what WILL be written.
                val accepted = ArrayList<Pair<ZipEntryInfo, String>>()
                var declared = 0L
                for (e in archive.entries) {
                    val relative = ZipNames.normalise(e.name)
                    if (relative == null) { refused(e); continue }
                    accepted += e to relative
                    if (!e.isDirectory) declared = saturatingAdd(declared, e.size)
                }
                roomFor(declared, destDir)

                var merge = false
                // `archive` extracting to `archive`: the folder cannot take the zip's own name, so it is "keep both".
                if (target.name == zip.name) target = FilePaths.keepBoth(destDir, target.name, isDirectory = true)
                if (FilePaths.existsNoFollow(target)) {
                    when (conflict(target)) {
                        Conflict.SKIP -> { skipped = true; return@guarded OpResult.Done(skipped = 1) }
                        Conflict.KEEP_BOTH -> target = FilePaths.keepBoth(destDir, target.name, isDirectory = true)
                        Conflict.REPLACE -> if (FilePaths.isRealDirectory(target)) merge = true
                        else if (!FilePaths.deleteTree(target)) throw Refused("what is there could not be replaced")
                    }
                }

                val root = File(destDir, FilePaths.extractName(zip.name, opId))
                journal.put(opId, root.path, volume.uuid)
                temp = root
                if (!root.mkdir()) throw java.io.IOException("mkdir")
                val rootCanon = ops.canonicalOf(root) ?: throw Refused(FileOps.OUTSIDE)
                val meter = Meter(control, declared)
                for ((e, relative) in accepted) {
                    meter.check()
                    val out = File(root, relative)
                    // The name guard's second half: where this would really be written.
                    val outCanon = ops.canonicalOf(out)
                    if (outCanon == null || !FilePaths.under(outCanon, rootCanon)) { refused(e); continue }
                    if (e.isDirectory) { out.mkdirs(); continue }
                    out.parentFile?.mkdirs()
                    try {
                        // A symlink entry's data is its link text: it lands in a regular file like any other entry (r3 D6).
                        archive.input(e).use { input -> FileOutputStream(out).use { meter.pump(input, it, limit = saturatingAdd(declared, BOMB_ALLOWANCE)) } }
                    } catch (o: Overrun) {
                        say("zip: stopped at ${o.written} (declared $declared)")
                        throw o
                    }
                }
                if (merge) mergeInto(root, target) else FilePaths.rename(root, target, replace = false)
                written = ops.filesUnder(target)
                OpResult.Done(listOf(target.path))
            }
        }
        // A cancel or a failure leaves no partial output: the temp folder goes, and with it its journal entry.
        temp?.let { if (FilePaths.existsNoFollow(it)) FilePaths.deleteTree(it) }
        journal.end(opId)
        if (written.isNotEmpty()) ops.scanPaths(written)
        if (!skipped) say("zip extract ${FilePaths.lineText(zip.path)} -> ${FilePaths.lineText(target.path)}: ${FileOps.tail(result)}")
        return result
    }

    fun create(sources: List<File>, destDir: File, control: OpControl, conflict: (File) -> Conflict): OpResult {
        val opId = journal.newOpId()
        val name = if (sources.size == 1) sources[0].name else ARCHIVE_BASE
        var target = File(destDir, FilePaths.fitName("", name, ZIP_EXT, isDirectory = true))
        var temp: File? = null
        var skipped = false
        var files = 0
        val result = ops.guarded(destDir, sources) {
            val volume = ops.volumeFor(destDir) ?: throw Refused(FileOps.OUTSIDE)
            if (sources.isEmpty()) throw Refused("nothing selected")
            if (!destDir.isDirectory) throw Refused("the folder is gone")
            val destCanon = ops.canonicalOf(destDir) ?: throw Refused(FileOps.OUTSIDE)
            var bytes = 0L
            for (src in sources) {
                ops.volumeFor(src) ?: throw Refused(FileOps.OUTSIDE)
                if (!FilePaths.existsNoFollow(src)) throw Refused("${FilePaths.lineText(src.name)} is gone")
                val srcCanon = ops.canonicalOf(src) ?: throw Refused(FileOps.OUTSIDE)
                // The zip is written in destDir: inside one of its own sources it would be zipping itself.
                if (FilePaths.isRealDirectory(src) && (destCanon == srcCanon || FilePaths.under(destCanon, srcCanon))) {
                    throw Refused("the zip would be inside ${FilePaths.lineText(src.name)}")
                }
                files += ops.countFiles(src)
                bytes += FilePaths.treeBytes(src)
            }
            var replace = false
            if (FilePaths.existsNoFollow(target)) {
                when (conflict(target)) {
                    Conflict.SKIP -> { skipped = true; return@guarded OpResult.Done(skipped = 1) }
                    Conflict.KEEP_BOTH -> target = FilePaths.keepBoth(destDir, target.name, isDirectory = false)
                    Conflict.REPLACE -> if (!FilePaths.isRealDirectory(target)) replace = true
                    else if (!FilePaths.deleteTree(target)) throw Refused("what is there could not be replaced")
                }
            }
            val part = File(destDir, FilePaths.partName(target.name, opId))
            journal.put(opId, part.path, volume.uuid)
            temp = part
            val meter = Meter(control, bytes)
            // DEFLATE, UTF-8 names (ZipOutputStream's default sets the UTF-8 flag where a name needs it).
            ZipOutputStream(BufferedOutputStream(FileOutputStream(part)), Charsets.UTF_8).use { out ->
                val used = HashSet<String>()
                for (src in sources) {
                    // Two selected items of one name (search results from two folders): the second is `name (2).ext`.
                    var entryName = src.name
                    var n = 2
                    while (!used.add(entryName.lowercase())) entryName = FilePaths.keepBothName(src.name, n++, FilePaths.isRealDirectory(src))
                    add(out, src, entryName, meter)
                }
            }
            FilePaths.rename(part, target, replace)
            OpResult.Done(listOf(target.path))
        }
        temp?.let { if (FilePaths.existsNoFollow(it)) it.delete() }
        journal.end(opId)
        if (result is OpResult.Done && !skipped) ops.scanPaths(listOf(target.path))
        if (!skipped) say("zip create $files files -> ${FilePaths.lineText(target.path)}: ${FileOps.tail(result)}")
        return result
    }

    private fun add(out: ZipOutputStream, src: File, entryName: String, meter: Meter) {
        meter.check()
        if (FilePaths.isSymlink(src)) return
        if (FilePaths.isRealDirectory(src)) {
            // The folder's own entry, so an empty folder is in the zip too.
            out.putNextEntry(ZipEntry("$entryName/").apply { time = src.lastModified() })
            out.closeEntry()
            src.listFiles()?.sortedBy { it.name }?.forEach { add(out, it, "$entryName/${it.name}", meter) }
            return
        }
        out.putNextEntry(ZipEntry(entryName).apply { time = src.lastModified() })
        FileInputStream(src).use { meter.pump(it, out) }
        out.closeEntry()
    }

    fun openNested(outer: ZipArchive, row: ZipRow, control: OpControl): NestedZip {
        val opId = journal.newOpId()
        val shown = FilePaths.lineText("${outer.file.path}/${row.path}")
        var temp: File? = null
        var opened: NestedZip.Opened? = null
        val result = ops.guarded(outer.file) {
            val entry = row.entry?.takeIf { !row.isDirectory } ?: throw Refused("not a file")
            val volume = ops.volumeFor(outer.file) ?: throw Refused(FileOps.OUTSIDE)
            // The same three guards as an extract: the name, the room, the declared size.
            if (ZipNames.normalise(entry.name) == null) { refused(entry); throw Refused("refused entry") }
            val dir = tmpDir(volume)
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw Refused("the temp folder cannot be made")
            File(dir, RecycleBin.NOMEDIA).let { if (!it.exists()) it.createNewFile() }
            roomFor(entry.size, dir)
            val part = File(dir, FilePaths.partName(row.name, opId))
            // Journalled for as long as it is open: a kill leaves it for the sweep (r3 D4).
            journal.put(opId, part.path, volume.uuid)
            temp = part
            val meter = Meter(control, entry.size)
            try {
                outer.input(entry).use { input -> FileOutputStream(part).use { meter.pump(input, it, limit = saturatingAdd(entry.size, BOMB_ALLOWANCE)) } }
            } catch (o: Overrun) {
                say("zip: stopped at ${o.written} (declared ${entry.size})")
                throw o
            }
            opened = NestedZip.Opened(part, opId, row.name)
            OpResult.Done(listOf(part.path))
        }
        opened?.let { return it }
        temp?.let { if (FilePaths.existsNoFollow(it)) it.delete() }
        journal.end(opId)
        return when (result) {
            OpResult.Cancelled -> NestedZip.Cancelled
            is OpResult.Failed -> {
                say("zip open $shown: failed ${FilePaths.lineText(result.reason)}")
                NestedZip.Failed(result.reason)
            }
            is OpResult.Done -> NestedZip.Failed("not opened")
        }
    }

    fun closeNested(nested: NestedZip.Opened) {
        // Only ever a temp of this layer's own naming, in a volume's temp folder.
        val f = nested.file
        if (FilePaths.isTempName(f.name) && f.parentFile?.name == TMP_DIR) f.delete()
        journal.end(nested.opId)
    }

    private fun refused(e: ZipEntryInfo) = say("zip: refused entry ${FilePaths.lineText(e.name)}")

    /** Refuses, before any write, a declared size the volume cannot take with [SPACE_MARGIN] to spare. */
    private fun roomFor(declared: Long, dir: File) {
        val free = freeSpace(dir)
        if (declared > free - SPACE_MARGIN) {
            say("zip: refused (needs $declared, free $free)")
            throw Refused(FileOps.NO_SPACE)
        }
    }

    /** Moves everything in [from] into [into], replacing what has the same name; a folder met by a folder merges. */
    private fun mergeInto(from: File, into: File) {
        from.listFiles()?.forEach { child ->
            val there = File(into, child.name)
            if (FilePaths.isRealDirectory(child) && FilePaths.isRealDirectory(there)) {
                mergeInto(child, there)
            } else {
                if (FilePaths.existsNoFollow(there) && (FilePaths.isRealDirectory(child) || FilePaths.isRealDirectory(there)) && !FilePaths.deleteTree(there)) {
                    throw Refused("what is there could not be replaced")
                }
                FilePaths.rename(child, there, replace = true)
            }
        }
        from.delete()
    }

    private fun saturatingAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b

    companion object {
        /** The free space an extract must leave (T18-2). */
        const val SPACE_MARGIN = 50L * 1024 * 1024

        /** How far past the declared total an extract may write before it is a bomb (T18-2). */
        const val BOMB_ALLOWANCE = 1024L * 1024

        const val ARCHIVE_BASE = "Archive"
        const val ZIP_EXT = ".zip"
        const val TMP_DIR = "tmp"

        /** `<volume>/.Tessera/tmp/`: where a nested zip's copy lives while it is open (a `.nomedia` folder beside the bin). */
        fun tmpDir(volume: FileVolume): File = File(File(volume.root, FilePaths.SHELL_DIR), TMP_DIR)

        /** `qa.zip` extracts to `qa` (r3 D6). */
        fun extractFolderName(zipName: String): String =
            if (ZipNames.isZipName(zipName)) zipName.substring(0, zipName.length - ZIP_EXT.length) else FilePaths.splitExtension(zipName).first
    }
}
