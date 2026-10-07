package app.tileshell.files

import java.io.File
import java.time.ZoneId

/**
 * The zips open as folders right now (build task 9; T18-2, r3 D6): one [ZipArchive] per zip on the way to the page
 * shown — the zip itself and, for a nested one, each zip it is nested in. A nested zip is a copy under
 * `<volume>/.Tessera/tmp/` made by [FileOps.zipOpenNested]; leaving its virtual root closes it and removes the copy
 * ([FileOps.zipCloseNested]). Everything here runs OFF the main thread.
 */
class ZipSessions(private val ops: () -> FileOps) {
    private class Session(val file: String, val chain: List<String>, val archive: ZipArchive, val nested: NestedZip.Opened?)

    /** How getting at a zip went: the archive, or the words its page shows. */
    sealed interface Got {
        class Open(val archive: ZipArchive) : Got
        class Not(val text: String) : Got
    }

    private val open = ArrayList<Session>()

    /** The archive [at] lies in, opened now when it is not open yet. */
    @Synchronized
    fun archive(at: FilesLocation.Zip): Got {
        open.firstOrNull { it.file == at.file && it.chain == at.chain }?.let { return Got.Open(it.archive) }
        if (at.chain.isEmpty()) {
            return when (val r = ops().zipOpen(File(at.file))) {
                is ZipOpen.Opened -> Got.Open(r.archive).also { open += Session(at.file, at.chain, r.archive, null) }
                ZipOpen.Encrypted -> Got.Not(ENCRYPTED)
                is ZipOpen.Failed -> Got.Not(CANNOT_OPEN)
            }
        }
        val outer = when (val o = archive(at.copy(chain = at.chain.dropLast(1), dir = ""))) {
            is Got.Open -> o.archive
            is Got.Not -> return o
        }
        val row = outer.row(at.chain.last())?.takeIf { !it.isDirectory } ?: return Got.Not(CANNOT_OPEN)
        val copy = when (val n = ops().zipOpenNested(outer, row)) {
            is NestedZip.Opened -> n
            NestedZip.Cancelled -> return Got.Not(CANNOT_OPEN)
            is NestedZip.Failed -> return Got.Not(if (n.reason == FileOps.NO_SPACE) NO_SPACE else CANNOT_OPEN)
        }
        return when (val r = ops().zipOpen(copy.file)) {
            is ZipOpen.Opened -> Got.Open(r.archive).also { open += Session(at.file, at.chain, r.archive, copy) }
            ZipOpen.Encrypted -> Got.Not(ENCRYPTED).also { ops().zipCloseNested(copy) }
            is ZipOpen.Failed -> Got.Not(CANNOT_OPEN).also { ops().zipCloseNested(copy) }
        }
    }

    /** The rows of the virtual folder [at], unsorted, or the words its page shows instead. */
    fun rows(at: FilesLocation.Zip, zone: ZoneId): PageBody {
        val archive = when (val got = archive(at)) {
            is Got.Open -> got.archive
            is Got.Not -> return PageBody.Error(got.text)
        }
        val base = at.file + at.chain.joinToString("") { "!/$it" }
        return PageBody.Rows(
            archive.list(at.dir).map { row ->
                FileEntry(
                    FileRow(row.name, row.isDirectory, row.size, row.modifiedMs), "$base!/${row.path}", at.volumeUuid,
                    detail(row, zone), FileKind.of(row.name, row.isDirectory), EntrySource.ZIP, zipPath = row.path,
                )
            },
        )
    }

    /**
     * Closes every zip that is not on the way to [keep] (null: all of them). A nested zip's copy is removed as it
     * closes, so `.Tessera/tmp/` holds nothing once its virtual root is left (r3 D6).
     */
    @Synchronized
    fun retain(keep: FilesLocation.Zip?) {
        val leaving = open.filterNot { s -> keep != null && s.file == keep.file && s.chain.size <= keep.chain.size && keep.chain.subList(0, s.chain.size) == s.chain }
        // The innermost first: a nested zip is read out of the one around it.
        for (s in leaving.sortedByDescending { it.chain.size }) {
            runCatching { s.archive.close() }
            s.nested?.let { ops().zipCloseNested(it) }
            open -= s
        }
    }

    companion object {
        /** H7's wording. */
        const val ENCRYPTED = "This zip is password-protected"
        const val CANNOT_OPEN = "This zip can't be opened"
        const val NO_SPACE = "There's not enough space to open this zip"
        const val EXTRACT_FIRST = "Extract first"

        /** A zip row's detail line: the size and the date the archive declares (sizes from the central directory). */
        fun detail(row: ZipRow, zone: ZoneId): String {
            val date = if (row.modifiedMs > 0) FileListing.dateText(row.modifiedMs, zone) else null
            return if (row.isDirectory) date.orEmpty() else listOfNotNull(FileListing.sizeText(row.size), date).joinToString(" ")
        }
    }
}
