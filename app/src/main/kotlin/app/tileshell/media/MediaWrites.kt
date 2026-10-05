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
 *  3. [cleanUpPending] deletes only pending rows whose OWNER_PACKAGE_NAME is the shell.
 * Every publish is read back (row present, IS_PENDING 0, SIZE > 0, the MIME and RELATIVE_PATH asked for), or the save
 * fails with its reason — the caller writes that reason into its own `failed:` line.
 */
class MediaWrites<C>(private val port: MediaStorePort<C>, private val shellPackage: String) {

    sealed interface Result {
        /** Published and read back. [row] says where it is (`relativePath`) for the op's line. */
        data class Saved(val row: MediaRow) : Result

        /** Nothing was left behind: no row, no pending row. [why] is short and holds no path of another app. */
        data class Failed(val why: String) : Result
    }

    /** Creates [item] and writes it with [write]. A [write] that throws fails the save with the exception's name. */
    fun save(item: NewMedia, write: (OutputStream) -> Unit): Result {
        val uri = port.insertPending(item) ?: return Result.Failed("the media store refused the new ${item.kind.name.lowercase()} in ${item.relativePath}")
        fun abandoned(why: String): Result {
            port.abandon(uri)
            return Result.Failed(why)
        }
        val out = try { port.openWrite(uri) } catch (e: Exception) { null } ?: return abandoned("the new file could not be opened")
        try {
            out.use(write)
        } catch (e: Exception) {
            return abandoned("write failed (${e.javaClass.simpleName})")
        }
        if (!port.publish(uri)) return abandoned("the new file could not be published")
        val row = port.readBack(uri) ?: return Result.Failed("the new file is gone after publishing")
        val wrong = when {
            row.pending -> "still pending after publishing"
            row.size <= 0L -> "the new file is empty"
            row.mime != item.mime -> "saved as ${row.mime}, not ${item.mime}"
            row.relativePath != item.relativePath -> "saved in ${row.relativePath}, not ${item.relativePath}"
            else -> null
        }
        return if (wrong == null) Result.Saved(row) else abandoned(wrong)
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
     * Start-up cleanup (Edge cases: a process killed mid-write): deletes the shell's own pending rows and returns how
     * many. A pending row another app owns is never touched.
     */
    fun cleanUpPending(): Int = port.pendingRows().count { it.pending && it.ownerPackage == shellPackage && port.abandon(it.uri) }

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
