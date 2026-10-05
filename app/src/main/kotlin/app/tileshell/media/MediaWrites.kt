package app.tileshell.media

import java.io.OutputStream

/** What a new MediaStore row is. */
enum class MediaKind { IMAGE, VIDEO }

/**
 * A row to create. [relativePath] ends with `/` as MediaStore writes it ("DCIM/Camera/"); [dateTakenMs] is copied from
 * the original for an edit or a trim (r3 D7) and is the capture time for a capture.
 */
data class NewMedia(
    val kind: MediaKind,
    val displayName: String,
    val mime: String,
    val relativePath: String,
    val dateTakenMs: Long? = null,
)

/** A row as read back from MediaStore. */
data class MediaRow(
    val uri: String,
    val pending: Boolean,
    val size: Long,
    val mime: String?,
    val relativePath: String?,
    val ownerPackage: String?,
)

/**
 * MediaStore as [MediaWrites] uses it (r3 D7; phase 16's standard — `ContactsPort` in people/PeopleWrites.kt): the real
 * port is [AndroidMediaStorePort], the tests' is a recording fake. [C] is the consent the platform asks the user for a
 * delete (an `IntentSender` on Android).
 */
interface MediaStorePort<C> {
    /** Inserts a row with IS_PENDING = 1 and returns its URI, or null when the provider refused it. */
    fun insertPending(item: NewMedia): String?

    /** Opens [uri] for writing ("w": truncating), or null when it cannot be opened. */
    fun openWrite(uri: String): OutputStream?

    /** Clears IS_PENDING. */
    fun publish(uri: String): Boolean

    /** Deletes the row and its file. */
    fun abandon(uri: String): Boolean

    /** The row as the provider holds it now, pending or not; null when it is gone. */
    fun readBack(uri: String): MediaRow?

    /** Every pending image and video row the provider shows the shell. */
    fun pendingRows(): List<MediaRow>

    /** The system consent dialog for deleting [uris] (`MediaStore.createDeleteRequest`). */
    fun deleteRequest(uris: List<String>): C
}

/**
 * The shell's ONE MediaStore write layer (phase 17, r3 D7), used by Photos (edits, trims) and Camera (captures).
 * Its rules, each proven by `MediaWritesTest` against a recording fake:
 *  1. Nothing here opens an existing row for writing — every write goes to a row this same call inserted — with one
 *     exception, [writeCaptureOutput], which takes the capture caller's own URI and only as the output guard's
 *     [CaptureOutputGuard.Decision.Accepted] token, which nothing else can make (Q-17-2 (b)).
 *  2. Every insert goes pending → written → published, and a failed write abandons its own pending row.
 *  3. [cleanUpPending] deletes only pending rows whose OWNER_PACKAGE_NAME is the shell AND that THIS process's own
 *     earlier run left behind (its [PendingLedger]): never a row another process of the shell is writing right now.
 * Every publish is read back (row present, IS_PENDING 0, SIZE > 0, the MIME and RELATIVE_PATH asked for), or the save
 * fails with its reason — the caller writes that reason into its own `failed:` line.
 */
class MediaWrites<C>(private val port: MediaStorePort<C>, private val shellPackage: String, private val ledger: PendingLedger) {

    sealed interface Result {
        /** Published and read back. [row] says where it is (`relativePath`) for the op's line. */
        data class Saved(val row: MediaRow) : Result

        /** Nothing was left behind: no row, no pending row. [why] is short and holds no path of another app. */
        data class Failed(val why: String) : Result
    }

    /** Creates [item] and writes it with [write]. A [write] that throws fails the save with the exception's name. */
    fun save(item: NewMedia, write: (OutputStream) -> Unit): Result {
        val uri = port.insertPending(item) ?: return Result.Failed("the media store refused the new ${item.kind.name.lowercase()} in ${item.relativePath}")
        ledger.add(uri)
        fun abandoned(why: String): Result {
            port.abandon(uri)
            ledger.remove(uri)
            return Result.Failed(why)
        }
        val out = try { port.openWrite(uri) } catch (e: Exception) { null } ?: return abandoned("the new file could not be opened")
        try {
            out.use(write)
        } catch (e: Exception) {
            return abandoned("write failed (${e.javaClass.simpleName})")
        }
        if (!port.publish(uri)) return abandoned("the new file could not be published")
        val row = port.readBack(uri)
        if (row == null) {
            ledger.remove(uri)
            return Result.Failed("the new file is gone after publishing")
        }
        val wrong = when {
            row.pending -> "still pending after publishing"
            row.size <= 0L -> "the new file is empty"
            row.mime != item.mime -> "saved as ${row.mime}, not ${item.mime}"
            row.relativePath != item.relativePath -> "saved in ${row.relativePath}, not ${item.relativePath}"
            else -> null
        }
        if (wrong != null) return abandoned(wrong)
        ledger.remove(uri)
        return Result.Saved(row)
    }

    /**
     * Writes a capture into the caller's own URI — the one write to a row this layer did not insert. It takes the
     * output guard's [accepted] token and nothing else, so no code path reaches an existing row without the guard.
     * Returns null when written, else the reason.
     */
    fun writeCaptureOutput(accepted: CaptureOutputGuard.Decision.Accepted, write: (OutputStream) -> Unit): String? {
        val out = try { port.openWrite(accepted.uri) } catch (e: Exception) { null } ?: return "the caller's output could not be opened"
        return try {
            out.use(write)
            null
        } catch (e: Exception) {
            "write failed (${e.javaClass.simpleName})"
        }
    }

    /**
     * Start-up cleanup (Edge cases: a process killed mid-write): deletes the pending rows THIS process's earlier run
     * inserted and never published or abandoned, and returns how many. Only rows named in this process's [ledger] are
     * looked at, so a row another process of the shell is writing at this moment (a recording in `:camera` while
     * `:photosedit` starts) is never touched; and a row is deleted only when it is still pending and the shell's own.
     */
    fun cleanUpPending(): Int {
        var deleted = 0
        for (uri in ledger.all()) {
            val row = port.readBack(uri)
            if (row != null && row.pending && row.ownerPackage == shellPackage && port.abandon(uri)) deleted++
            ledger.remove(uri)
        }
        return deleted
    }

    /** The consent to delete [uris]; the delete itself is the platform's, after the user accepts. */
    fun deleteRequest(uris: List<String>): C = port.deleteRequest(uris)

    companion object {
        /**
         * Where a copy (an edit, a trim) goes (r3 D7): the original's folder when MediaProvider takes a new row of that
         * kind there — images under DCIM/ or Pictures/, videos under DCIM/, Movies/ or Pictures/ — else Pictures/ for an
         * image and Movies/ for a video.
         */
        fun copyPlacement(kind: MediaKind, originalRelativePath: String?): String {
            val path = originalRelativePath.orEmpty().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
            val allowed = when (kind) {
                MediaKind.IMAGE -> listOf("DCIM/", "Pictures/")
                MediaKind.VIDEO -> listOf("DCIM/", "Movies/", "Pictures/")
            }
            // A ".." segment is never a folder MediaProvider would take; such a path falls back like any other.
            val clean = path.isNotEmpty() && path.split('/').none { it == ".." } && allowed.any { path.startsWith(it) }
            return if (clean) path else if (kind == MediaKind.IMAGE) "Pictures/" else "Movies/"
        }

        /** The Camera's folder: every capture of the shell's own Camera lands here. */
        const val CAMERA_PATH = "DCIM/Camera/"
    }
}

/**
 * The rows one process has inserted and not yet published or abandoned. It outlives the process (a file), so the next
 * start of that same process knows exactly which pending rows are its own leftovers.
 */
interface PendingLedger {
    fun add(uri: String)
    fun remove(uri: String)
    fun all(): List<String>
}

/** A ledger kept in memory: the tests', and never a process's real one. */
class MemoryPendingLedger(initial: List<String> = emptyList()) : PendingLedger {
    private val uris = LinkedHashSet(initial)
    @Synchronized override fun add(uri: String) { uris += uri }
    @Synchronized override fun remove(uri: String) { uris -= uri }
    @Synchronized override fun all(): List<String> = uris.toList()
}

/** One URI per line in [file], written temp-and-rename; an unreadable file is an empty ledger. */
class FilePendingLedger(private val file: java.io.File) : PendingLedger {
    @Synchronized override fun add(uri: String) = write(all() + uri)
    @Synchronized override fun remove(uri: String) = write(all() - uri)
    @Synchronized override fun all(): List<String> =
        runCatching { file.readLines().map { it.trim() }.filter { it.startsWith("content://") }.distinct() }.getOrDefault(emptyList())

    private fun write(uris: List<String>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = java.io.File(file.parentFile, file.name + ".tmp")
            tmp.writeText(uris.distinct().joinToString("") { it + "\n" })
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
