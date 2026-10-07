package app.tileshell.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Reading a folder and walking a tree for the folder page (build task 2), over `java.nio` on plain paths — so the JVM
 * tests run it on a temp dir (`FolderReaderTest`). Nothing here writes, and nothing follows a symlink: a link is a
 * row like any file and is never descended into (Edge cases: a path that resolves outside the volume is refused).
 *
 * One `lstat` per entry: a folder of 10,000 files costs 10,000 calls through FUSE, not the 30,000 that
 * `File.isDirectory` + `length` + `lastModified` would make.
 */
object FolderReader {
    /** A folder's own entries as rows, unsorted; null when the folder cannot be read (gone, not a folder, unreadable). */
    fun read(dir: File): List<FileRow>? {
        val out = ArrayList<FileRow>()
        return try {
            Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (p in stream) row(p)?.let { out += it }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    /** One entry's row; null when it vanished between the listing and the read. */
    fun row(path: Path): FileRow? = try {
        val a = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        FileRow(path.fileName.toString(), a.isDirectory, if (a.isDirectory) 0L else a.size(), a.lastModifiedTime().toMillis())
    } catch (e: Exception) {
        null
    }

    /** One hit of a search: the row, its full path, and its path relative to the folder searched ("sub/b.bin"). */
    data class Hit(val row: FileRow, val path: String, val relative: String, val rank: Int)

    /**
     * Search within a tree (r11/files.md 1.11.1: the folder one is in, and below): every entry under [root] whose name
     * matches [query] ([FileListing.searchRank]), handed to [onHit] as it is found. Breadth first, so the folder's own
     * entries come before anything deeper. [stopped] is asked at every folder and every 256 entries; a stopped walk
     * returns false. [skip] names the folders never entered (the shell's `.Tessera`); a dot-file is neither matched nor
     * entered unless [showHidden]. [onFolder] is told how many folders have been read so far — the progress line.
     * [entriesPerSecond] is the debug-only QA pace (Q-18-5): when set, the walk sleeps so that it examines at most
     * that many entries a second and asks [stopped] at each pause; null or 0 = unpaced. Nothing else changes.
     */
    fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        skip: (File) -> Boolean,
        stopped: () -> Boolean,
        onFolder: (Int) -> Unit = {},
        entriesPerSecond: Long? = null,
        onHit: (Hit) -> Unit,
    ): Boolean {
        val base = root.path.trimEnd('/')
        val eps = entriesPerSecond?.takeIf { it > 0 }
        val t0 = System.nanoTime()
        val queue = ArrayDeque<File>()
        queue += root
        var folders = 0
        var seen = 0
        while (queue.isNotEmpty()) {
            if (stopped()) return false
            val dir = queue.removeFirst()
            val entries = try {
                Files.newDirectoryStream(dir.toPath())
            } catch (e: Exception) {
                continue
            }
            entries.use { stream ->
                for (p in stream) {
                    if (++seen % 256 == 0 && stopped()) return false
                    if (eps != null && seen % PACE_EVERY == 0) {
                        if (stopped()) return false
                        val due = seen * 1000L / eps - (System.nanoTime() - t0) / 1_000_000L
                        if (due > 0) Thread.sleep(due)
                    }
                    val name = p.fileName.toString()
                    if (!showHidden && FileListing.isHidden(name)) continue
                    val rank = FileListing.searchRank(name, query)
                    // The attributes are read only for a hit and for a name that may be a folder to enter.
                    if (rank == null && !mayBeFolder(p)) continue
                    val row = row(p) ?: continue
                    val file = p.toFile()
                    if (row.isDirectory && skip(file)) continue
                    if (rank != null) onHit(Hit(row, file.path, file.path.removePrefix("$base/"), rank))
                    if (row.isDirectory) queue += file
                }
            }
            onFolder(++folders)
        }
        return true
    }

    /** The paced walk (Q-18-5) checks its clock this often, so a cancel is never more than a pause away. */
    private const val PACE_EVERY = 16

    private fun mayBeFolder(p: Path): Boolean = Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)

    /** Search's order ("Sort by: Relevance"): the best rank first, then the shallower path, then by name and path. */
    val byRelevance: Comparator<Hit> = compareBy<Hit> { it.rank }
        .thenBy { it.relative.count { c -> c == '/' } }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.row.name }
        .thenBy { it.relative }
}
