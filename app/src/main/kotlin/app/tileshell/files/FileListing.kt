package app.tileshell.files

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** One row of a folder page, free of `java.io` and Android: a real folder's entry, a zip's, a search hit. */
data class FileRow(val name: String, val isDirectory: Boolean, val size: Long, val modifiedMs: Long)

/** The sort flyout's three keys (`files_sort_item:name|date|size`). W10M had no type sort (r11/files.md 1.4.5; T18-8). */
enum class FileSort(val label: String) { NAME("Name"), DATE("Date"), SIZE("Size") }

/**
 * The folder page's pure rules (build task 2; r3 V13, Y2; r11/files.md 1.5.8): the order of the rows, the detail line
 * under each name, which rows a folder shows, and what a search matches. `FileListingTest` holds them.
 */
object FileListing {
    private val byName: Comparator<FileRow> = compareBy<FileRow, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name }

    /**
     * The order rules (r3 V13): folders before files in EVERY sort; Name A → Z ignoring case; Date newest first; Size
     * largest first, folders among themselves by name; ties by name.
     */
    fun comparator(sort: FileSort): Comparator<FileRow> {
        val foldersFirst = compareByDescending<FileRow> { it.isDirectory }
        return when (sort) {
            FileSort.NAME -> foldersFirst.then(byName)
            FileSort.DATE -> foldersFirst.thenByDescending { it.modifiedMs }.then(byName)
            FileSort.SIZE -> foldersFirst.thenByDescending { if (it.isDirectory) 0L else it.size }.then(byName)
        }
    }

    fun sorted(rows: List<FileRow>, sort: FileSort): List<FileRow> = rows.sortedWith(comparator(sort))

    /** A dot-file: hidden unless Files' setting shows hidden files (default off; Edge cases). The bin's `.Tessera` is one. */
    fun isHidden(name: String): Boolean = name.startsWith(".")

    fun visible(rows: List<FileRow>, showHidden: Boolean): List<FileRow> = if (showHidden) rows else rows.filterNot { isHidden(it.name) }

    /**
     * The detail line (r11/files.md 1.5.8): the date alone for a folder ("11/24/2015"), size then date for a file
     * ("300 KB 1/2/2026").
     */
    fun detail(row: FileRow, zone: ZoneId): String =
        if (row.isDirectory) dateText(row.modifiedMs, zone) else "${sizeText(row.size)} ${dateText(row.modifiedMs, zone)}"

    /**
     * A Recycle Bin row's detail line (the GATE review's M5): the deleted-at date, then the folder Restore will put the
     * file in, relative to its volume — "1/2/2026 Pictures/Trip"; a file with no usable record reads "Download/Restored".
     * The volume's own root reads as the volume's name, and with more than one volume mounted ([manyVolumes]) the name
     * leads every path — "1/2/2026 Virtual SD card/Pictures". [deleted] is null for a file no record names.
     */
    fun binDetail(deleted: String?, restoreDir: String, volumeRoot: String, volumeLabel: String, manyVolumes: Boolean): String {
        val root = volumeRoot.trimEnd('/')
        val dir = restoreDir.trimEnd('/')
        val where = when {
            dir == root -> volumeLabel
            FilePaths.under(dir, root) -> dir.substring(root.length + 1).let { if (manyVolumes) "$volumeLabel/$it" else it }
            // Not under its volume: never the case for a row the bin made, and then the whole path is the honest text.
            else -> dir
        }
        return listOfNotNull(deleted, FilePaths.lineText(where)).joinToString(" ")
    }

    /** Recent's row (pass 2 §4.8): the date only — the OPENED-AT date ("below Q-18-1"). */
    fun recentDetail(openedAtMs: Long, zone: ZoneId): String = dateText(openedAtMs, zone)

    /**
     * A size to 3 significant figures in bytes / KB / MB / GB (1.5.8): "128 bytes", "1.00 KB", "12.3 KB", "300 KB",
     * "1.21 GB". Binary units (1 KB = 1,024 bytes); under 1,024 bytes the count itself; rounded half up, and a value
     * that rounds up to 1,024 reads as 1.00 of the next unit.
     */
    fun sizeText(bytes: Long): String {
        if (bytes < 1024) return "${maxOf(bytes, 0)} bytes"
        var value = BigDecimal(bytes)
        val k = BigDecimal(1024)
        for (unit in UNITS) {
            value = value.divide(k, 10, RoundingMode.HALF_UP)
            val digits = if (value < BigDecimal(10)) 2 else if (value < BigDecimal(100)) 1 else 0
            val shown = value.setScale(digits, RoundingMode.HALF_UP)
            // 9.999 rounds to 10.00 and 99.99 to 100.0: one digit too many, so the scale is taken from the rounded value.
            val fitted = if (shown >= BigDecimal(100)) shown.setScale(0, RoundingMode.HALF_UP) else if (shown >= BigDecimal(10)) shown.setScale(1, RoundingMode.HALF_UP) else shown
            if (fitted < k || unit == UNITS.last()) return "${fitted.toPlainString()} $unit"
        }
        return "$bytes bytes"
    }

    /** The en-US short date, no leading zeros: "1/2/2026" (Fixtures; r3 V13). */
    fun dateText(ms: Long, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        return "${d.monthValue}/${d.dayOfMonth}/${d.year}"
    }

    /** A search hit: the name holds the term, ignoring case. A blank term matches nothing. */
    fun matches(name: String, query: String): Boolean = searchRank(name, query) != null

    /**
     * How well [name] answers [query] ("Sort by: Relevance" while searching — lower is better), or null for no match:
     * 0 the whole name, 1 the name without its extension, 2 the name starts with it, 3 a word in it starts with it, 4 it
     * is somewhere inside.
     */
    fun searchRank(name: String, query: String): Int? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val n = name.lowercase()
        val at = n.indexOf(q)
        return when {
            at < 0 -> null
            n == q -> 0
            FilePaths.splitExtension(n).first == q -> 1
            at == 0 -> 2
            generateSequence(at) { n.indexOf(q, it + 1).takeIf { i -> i >= 0 } }.any { !n[it - 1].isLetterOrDigit() } -> 3
            else -> 4
        }
    }

    /** "<n> items selected", "1 item selected" in the singular (r3 D2; pass 2 §1). */
    fun selectedText(count: Int): String = if (count == 1) "1 item selected" else "$count items selected"

    private val UNITS = listOf("KB", "MB", "GB")
}
