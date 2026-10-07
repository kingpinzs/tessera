package app.tileshell.files

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.tileshell.diag.Diagnostics
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * A file the provider has OPENED, as the serving rule sees it (a port: the device's is [AndroidOpenedFile]; the JVM
 * tests hand in their own). The rule proves, on the open descriptor, that it holds the file it checked.
 */
interface OpenedFile {
    /** The real path of the open descriptor as the kernel names it (`/proc/self/fd/<n>`); null when it cannot be read. */
    val realPath: String?

    /** The descriptor is a regular file (not a directory, a pipe or a device). */
    val isRegularFile: Boolean

    /** The open file's own length. */
    val size: Long

    fun close()
}

/**
 * What the shell's FileProvider serves (T18-11, r3 D10; a trust rule), as pure functions: the roots, the canonicaliser
 * and the opener are parameters, so `FilesProviderRulesTest` runs E7's five cases against the SERVING check on the JVM
 * and `FilesProviderServingTest` runs every entry's whole answer.
 *
 * The provider's paths XML holds one `root-path` named [ROOT_NAME] over `/` (needed to reach `/storage/<UUID>`), so a
 * URI's path is `/root/<the file's absolute path, percent-encoded>` — and it can NAME any file on the phone. What it
 * can REACH is decided here, in two steps that every entry takes:
 *  1. [gate] ([serve] plus the refusal line) answers with the file's canonical path only when [FileShareGuard.allowed]
 *     says that path lies under a volume directory and outside the shell's own folders; the path is resolved ONCE.
 *  2. [open] and [row] open exactly that path and then ask the OPEN descriptor where it really is
 *     ([OpenedFile.realPath]): anything but the checked path — a link put there between the check and the open, at the
 *     file or at any folder above it — is closed and refused with [FileShareGuard.LINE_SWAPPED]. So the bytes and the
 *     size handed out are those of the file that was checked. [type] reads nothing from the disk.
 */
object FilesProviderRules {
    /** The `root-path` entry's name in `res/xml/files_provider_paths.xml`. */
    const val ROOT_NAME = "root"

    /** `OpenableColumns.DISPLAY_NAME` and `OpenableColumns.SIZE`: FileProvider's own two columns, and the only ones. */
    const val COLUMN_NAME = OpenableColumns.DISPLAY_NAME
    const val COLUMN_SIZE = OpenableColumns.SIZE
    private val COLUMNS = listOf(COLUMN_NAME, COLUMN_SIZE)

    /** What `query` answers: the columns asked for (of the two), and their values — null for what is not a file. */
    class Row(val columns: List<String>, val values: List<Any>?)

    /**
     * The absolute file path a provider URI's ENCODED path names, or null when it is not this provider's form: no
     * `/root/` prefix, nothing after it, a malformed percent escape, bytes that are not UTF-8, or a NUL.
     */
    fun pathOf(encodedPath: String?): String? {
        val prefix = "/$ROOT_NAME/"
        if (encodedPath == null || !encodedPath.startsWith(prefix)) return null
        val rest = decode(encodedPath.substring(prefix.length)) ?: return null
        if (rest.isEmpty() || rest.contains('\u0000')) return null
        return "/$rest"
    }

    /**
     * The canonical path to serve for [encodedPath], or null: refused. The path is canonicalised ONCE and that one
     * answer is both checked and returned, so what is opened is what was checked.
     */
    fun serve(encodedPath: String?, roots: List<String>, canonical: (String) -> String?): String? {
        val path = pathOf(encodedPath) ?: return null
        var resolved: String? = null
        val once: (String) -> String? = { p -> if (p == path) canonical(p).also { resolved = it } else canonical(p) }
        return if (FileShareGuard.allowed(path, roots, once)) resolved else null
    }

    /** [serve], writing `share refused: outside shared storage` through [say] when the answer is no. The provider's one way in. */
    fun gate(encodedPath: String?, roots: List<String>, canonical: (String) -> String?, say: (String) -> Unit): String? =
        serve(encodedPath, roots, canonical).also { if (it == null) say(FileShareGuard.LINE_REFUSED) }

    /**
     * `openFile`: the open file at [served] (the gate's answer), proven to be that file. A refused URI and a write mode
     * throw [SecurityException]; nothing there, or something that is not a regular file, [FileNotFoundException].
     */
    fun <T : OpenedFile> open(served: String?, mode: String, opener: (String) -> T?, say: (String) -> Unit): T {
        val path = served ?: throw SecurityException(FileShareGuard.LINE_REFUSED)
        if (mode != "r") throw SecurityException("read only")
        return checked(path, opener, say) ?: throw FileNotFoundException("no such file")
    }

    /**
     * `query`: FileProvider's own two columns, and only those, whatever is asked for; no values for what is not a file.
     * The name is the checked path's; the size is read from the open, checked file.
     */
    fun <T : OpenedFile> row(served: String?, projection: Array<out String>?, opener: (String) -> T?, say: (String) -> Unit): Row {
        val path = served ?: throw SecurityException(FileShareGuard.LINE_REFUSED)
        val columns = (projection?.toList() ?: COLUMNS).filter { it in COLUMNS }
        val file = checked(path, opener, say) ?: return Row(columns, null)
        try {
            return Row(columns, columns.map { if (it == COLUMN_NAME) path.substringAfterLast('/') else file.size })
        } finally {
            file.close()
        }
    }

    /** `getType`: by the checked path's extension through [mimeOf] (`MimeTypeMap`); null for a refused URI. */
    fun type(served: String?, mimeOf: (String) -> String?): String? {
        val path = served ?: return null
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return mimeOf(ext) ?: "application/octet-stream"
    }

    /**
     * Step 2: [path] opened through [opener] (null: no such file) and held only when the open descriptor IS [path] and
     * is a regular file. A descriptor that is somewhere else is closed, said and refused; one whose place cannot be
     * read is refused the same way (it cannot be proven).
     */
    private fun <T : OpenedFile> checked(path: String, opener: (String) -> T?, say: (String) -> Unit): T? {
        val file = opener(path) ?: return null
        try {
            if (file.realPath != path) {
                say(FileShareGuard.LINE_SWAPPED)
                throw SecurityException(FileShareGuard.LINE_SWAPPED)
            }
            if (!file.isRegularFile) {
                file.close()
                return null
            }
            return file
        } catch (e: Throwable) {
            file.close()
            throw e
        }
    }

    /** Strict percent-decoding to UTF-8 (`+` stays a plus: this is a path, not a form). Null when malformed. */
    internal fun decode(s: String): String? {
        if (!s.contains('%')) return s
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                out.write(hi * 16 + lo)
                i += 3
            } else {
                // A run of literal characters, encoded together so a surrogate pair stays one character.
                val end = s.indexOf('%', i).let { if (it < 0) s.length else it }
                out.write(s.substring(i, end).toByteArray(Charsets.UTF_8))
                i = end
            }
        }
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(out.toByteArray()))
                .toString()
        }.getOrNull()
    }
}

/** The device's [OpenedFile]: a read-only descriptor, asked through `fstat` and `/proc/self/fd`. */
class AndroidOpenedFile(val descriptor: ParcelFileDescriptor) : OpenedFile {
    private val stat = runCatching { Os.fstat(descriptor.fileDescriptor) }.getOrNull()
    override val realPath: String? get() = runCatching { Os.readlink("/proc/self/fd/${descriptor.fd}") }.getOrNull()
    override val isRegularFile: Boolean get() = stat != null && OsConstants.S_ISREG(stat.st_mode)
    override val size: Long get() = stat?.st_size ?: 0L
    override fun close() {
        runCatching { descriptor.close() }
    }
}

/**
 * The shell's FileProvider (phase 18 build task 3; T18-11, r3 D10): not exported, reached only through a URI grant
 * (Share, open-with) or by the shell itself (Photos' viewer, the video player, Music's one-file play).
 *
 * Its `root-path` covers the whole disk, the shell's private dirs included, and Files holds All-files access — so the
 * scope is enforced twice, by the same rule: [uriFor] (the ONLY caller of `getUriForFile`) hands out a URI only for a
 * file under a `StorageVolume.getDirectory()`, and every entry that could reveal a file ([openFile], [query],
 * [getType]) sends the URI through [gate] and hands the answer to its rule in [FilesProviderRules], so a grant holder
 * who rewrites the path of a URI it was given gets nothing. None of them falls through to FileProvider's own
 * resolution. It is read-only: a write mode is refused, and so are insert, update and delete.
 *
 * This class holds NO logic: each entry is one expression, and `FilesProviderWiringScanTest` holds the class to this
 * exact text (the GATE review rebuilt `query` without its scope check and every test passed).
 */
class FilesProvider : FileProvider() {

    /** The one way in: this URI's canonical path when it may be served, or null after the refusal line. */
    private fun gate(uri: Uri): String? = FilesProviderRules.gate(uri.encodedPath, context?.let { roots(it) }.orEmpty(), CANONICAL, SAY)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = FilesProviderRules.open(gate(uri), mode, OPENER, SAY).descriptor

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        FilesProviderRules.row(gate(uri), projection, OPENER, SAY).let { row -> MatrixCursor(row.columns.toTypedArray(), 1).apply { row.values?.let { addRow(it) } } }

    override fun getType(uri: Uri): String? = FilesProviderRules.type(gate(uri), MIME)

    override fun insert(uri: Uri, values: ContentValues): Uri = throw UnsupportedOperationException("No external inserts")

    override fun update(uri: Uri, values: ContentValues, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("No external updates")

    /** FileProvider's delete removes the file; this provider never does (deleting is Files' own, through the bin). */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("No external deletes")

    companion object {
        /** The authority, as the manifest declares it (the project's form: `app.tileshell.<name>`). */
        const val AUTHORITY = "app.tileshell.files"

        private val CANONICAL: (String) -> String? = { runCatching { File(it).canonicalPath }.getOrNull() }

        private val SAY: (String) -> Unit = { Diagnostics.add("files", it) }

        private val MIME: (String) -> String? = { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }

        /** Opens a regular file read-only (null: none there), for [FilesProviderRules] to prove and hand out. */
        private val OPENER: (String) -> AndroidOpenedFile? = { path ->
            File(path).takeIf { it.isFile }?.let { AndroidOpenedFile(ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)) }
        }

        /** The directory of every mounted volume (`StorageVolume.getDirectory()` is null for one that is not). */
        fun roots(context: Context): List<String> =
            context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty().mapNotNull { it.directory?.path }

        /**
         * The `content://` URI for [file], or null after `[files] share refused: outside shared storage`. The one
         * place `getUriForFile` is called; the URI is built from the canonical path that was checked.
         */
        fun uriFor(context: Context, file: File): Uri? {
            val canonical = CANONICAL(file.path)
            if (!FileShareGuard.check(canonical, roots(context), CANONICAL, SAY)) return null
            return getUriForFile(context, AUTHORITY, File(canonical!!))
        }

        /**
         * The file a URI of this provider names, by the serving rule's gate — null (with the refusal line) for another
         * scheme or authority and for anything the gate refuses. For the shell's own readers of its own URIs (Music's
         * one-file play), which need the path as well as the stream; what they open by that path is theirs to prove.
         */
        fun fileFor(context: Context, uri: Uri): File? {
            val path = if (uri.scheme == "content" && uri.authority == AUTHORITY) FilesProviderRules.gate(uri.encodedPath, roots(context), CANONICAL, SAY) else null.also { SAY(FileShareGuard.LINE_REFUSED) }
            return path?.let { File(it) }
        }
    }
}
