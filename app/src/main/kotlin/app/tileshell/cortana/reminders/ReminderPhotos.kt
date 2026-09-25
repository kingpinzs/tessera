package app.tileshell.cortana.reminders

import android.content.Context
import android.net.Uri
import app.tileshell.diag.Diagnostics
import java.io.File
import java.util.UUID

/**
 * A reminder's photo is the launcher's own copy (L13-1; review/2026-09-25-L13-1-fix-plan.md, Jeremy's Q2 "(b)").
 *
 * The photo picker's URI is readable only while its grant lasts, and the gallery original can be deleted or moved,
 * so the picked photo is copied into [FOLDER] at pick time and the reminder stores the copy's `file:` URI (the page,
 * the Reminders row and the notification all read it through `ContentResolver.openInputStream`, which opens `file:`).
 *
 * Only a file directly inside [FOLDER] is ever deleted: the URI string comes from the reminder store, and deleting
 * whatever it names would be a way to delete any of the launcher's files.
 */
object ReminderPhotos {
    const val FOLDER = "reminder_photos"

    private const val FILE_SCHEME = "file://"

    fun folder(context: Context): File = File(context.filesDir, FOLDER)

    /** Copy a picked photo into [FOLDER]; the copy's URI, or null when the photo could not be read. */
    fun adopt(context: Context, picked: Uri): String? {
        val started = System.currentTimeMillis()
        // The store sweeps unreferenced copies when it first loads in a process: load it BEFORE this copy exists, or
        // a pick made before anything else touched the store would be swept as an orphan on its own save.
        ReminderStore.get(context)
        val out = File(folder(context).apply { mkdirs() }, UUID.randomUUID().toString())
        return runCatching {
            val input = context.contentResolver.openInputStream(picked) ?: error("the picked photo has no stream")
            input.use { src -> out.outputStream().use { src.copyTo(it) } }
            uriFor(out)
        }.onSuccess {
            Diagnostics.add("reminders", "photo adopted: ${out.name} ${out.length()} bytes in ${System.currentTimeMillis() - started} ms")
        }.onFailure {
            out.delete()
            Diagnostics.add("reminders", "photo not adopted: $it")
        }.getOrNull()
    }

    fun release(context: Context, uri: String?): Boolean = release(folder(context), uri)

    fun sweep(context: Context, referenced: Collection<String>): List<String> = sweep(folder(context), referenced)

    // ---------------- the file rules (JVM-tested) ----------------

    fun uriFor(file: File): String = FILE_SCHEME + file.absolutePath

    /** The copy [uri] names, or null when it is not a file directly inside [folder]. */
    fun copyFile(folder: File, uri: String?): File? {
        if (uri == null || !uri.startsWith(FILE_SCHEME)) return null
        val file = File(uri.removePrefix(FILE_SCHEME)).canonicalFile
        return if (file.parentFile == folder.canonicalFile) file else null
    }

    /** Delete the copy [uri] names; false when it names no copy (or the copy is already gone). */
    fun release(folder: File, uri: String?): Boolean {
        val file = copyFile(folder, uri) ?: return false
        val deleted = file.delete()
        if (deleted) Diagnostics.add("reminders", "photo released: ${file.name}")
        return deleted
    }

    /** Delete every copy no reminder references (a pick whose reminder was never saved); the names deleted. */
    fun sweep(folder: File, referenced: Collection<String>): List<String> {
        val keep = referenced.mapNotNull { copyFile(folder, it) }.toSet()
        val orphans = folder.listFiles()?.filter { it.isFile && it.canonicalFile !in keep }.orEmpty()
        orphans.forEach { it.delete() }
        if (orphans.isNotEmpty()) Diagnostics.add("reminders", "photo sweep deleted ${orphans.size}")
        return orphans.map { it.name }.sorted()
    }
}
