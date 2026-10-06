package app.tileshell.files

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.tileshell.diag.Diagnostics
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * What the shell's FileProvider serves (T18-11, r3 D10; a trust rule), as pure functions: the roots and the
 * canonicaliser are parameters, so `FilesProviderRulesTest` runs E7's five cases against the SERVING check on the JVM.
 *
 * The provider's paths XML holds one `root-path` named [ROOT_NAME] over `/` (needed to reach `/storage/<UUID>`), so a
 * URI's path is `/root/<the file's absolute path, percent-encoded>` — and it can NAME any file on the phone. What it
 * can REACH is decided here: [serve] answers with the file's canonical path only when [FileShareGuard.allowed] says
 * that path lies under a volume directory, and the provider opens exactly the path it was given back.
 */
object FilesProviderRules {
    /** The `root-path` entry's name in `res/xml/files_provider_paths.xml`. */
    const val ROOT_NAME = "root"

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

/**
 * The shell's FileProvider (phase 18 build task 3; T18-11, r3 D10): not exported, reached only through a URI grant
 * (Share, open-with) or by the shell itself (Photos' viewer, the video player, Music's one-file play).
 *
 * Its `root-path` covers the whole disk, the shell's private dirs included, and Files holds All-files access — so the
 * scope is enforced twice, by the same rule: [uriFor] (the ONLY caller of `getUriForFile`) hands out a URI only for a
 * file under a `StorageVolume.getDirectory()`, and every entry that could reveal a file ([openFile], [query],
 * [getType]) resolves the URI itself through [FilesProviderRules.serve] and refuses anything else, so a grant holder
 * who rewrites the path of a URI it was given gets nothing. None of them falls through to FileProvider's own
 * resolution. It is read-only: a write mode is refused, and so are insert, update and delete.
 */
class FilesProvider : FileProvider() {

    /** The canonical path this URI may be served from, or null after writing the refusal line. */
    private fun served(uri: Uri): String? {
        val c = context
        val path = if (c == null) null else FilesProviderRules.serve(uri.encodedPath, roots(c), CANONICAL)
        if (path == null) Diagnostics.add("files", FileShareGuard.LINE_REFUSED)
        return path
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val path = served(uri) ?: throw SecurityException(FileShareGuard.LINE_REFUSED)
        if (mode != "r") throw SecurityException("read only")
        val file = File(path)
        // A regular file only: a directory's descriptor is not a file's contents.
        if (!file.isFile) throw FileNotFoundException("no such file")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val path = served(uri) ?: throw SecurityException(FileShareGuard.LINE_REFUSED)
        val file = File(path)
        // FileProvider's own two columns, and only those, whatever is asked for; no row for what is not a file.
        val columns = (projection ?: COLUMNS).filter { it in COLUMNS }
        val cursor = MatrixCursor(columns.toTypedArray(), 1)
        if (file.isFile) cursor.addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) file.name else file.length() })
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val path = served(uri) ?: return null
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** FileProvider's delete removes the file; this provider never does (deleting is Files' own, through the bin). */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("No external deletes")

    companion object {
        /** The authority, as the manifest declares it (the project's form: `app.tileshell.<name>`). */
        const val AUTHORITY = "app.tileshell.files"

        private val COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        private val CANONICAL: (String) -> String? = { runCatching { File(it).canonicalPath }.getOrNull() }

        /** The directory of every mounted volume (`StorageVolume.getDirectory()` is null for one that is not). */
        fun roots(context: Context): List<String> =
            context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty().mapNotNull { it.directory?.path }

        /**
         * The `content://` URI for [file], or null after `[files] share refused: outside shared storage`. The one
         * place `getUriForFile` is called; the URI is built from the canonical path that was checked.
         */
        fun uriFor(context: Context, file: File): Uri? {
            val canonical = CANONICAL(file.path)
            if (!FileShareGuard.check(canonical, roots(context), CANONICAL) { Diagnostics.add("files", it) }) return null
            return getUriForFile(context, AUTHORITY, File(canonical!!))
        }

        /**
         * The file a URI of this provider names, by the serving rule — null (with the refusal line) for another
         * scheme or authority and for anything outside a volume. For the shell's own readers of its own URIs
         * (Music's one-file play), which need the path as well as the stream.
         */
        fun fileFor(context: Context, uri: Uri): File? {
            val path = if (uri.scheme == "content" && uri.authority == AUTHORITY) {
                FilesProviderRules.serve(uri.encodedPath, roots(context), CANONICAL)
            } else {
                null
            }
            if (path == null) Diagnostics.add("files", FileShareGuard.LINE_REFUSED)
            return path?.let { File(it) }
        }
    }
}
