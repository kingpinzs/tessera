package app.tileshell.files

import android.content.Context
import android.media.MediaScannerConnection
import app.tileshell.diag.Diagnostics
import app.tileshell.onboarding.Checklist
import java.io.File

/**
 * Files' process-wide objects, made once: [RecentStore], [OpsJournal] and [FileOps] each rewrite a whole file under
 * their own lock, so two of any of them in one process would overwrite each other. The pages read through these
 * (Recent's rows, the bin's rows through [FileOps.bin]); the copy service and every write use the same [ops].
 */
object FilesStores {
    private var recent: RecentStore? = null
    private var journal: OpsJournal? = null
    private var ops: FileOps? = null

    private val say: (String) -> Unit = { Diagnostics.add("files", it) }
    private val canonical: (String) -> String? = { runCatching { File(it).canonicalPath }.getOrNull() }

    /** `File.canonicalPath`, null when it cannot be resolved: the canonicaliser every pure rule takes. */
    fun canonical(path: String): String? = canonical.invoke(path)

    @Synchronized
    fun recent(context: Context): RecentStore =
        recent ?: RecentStore(context.applicationContext.filesDir, System::currentTimeMillis, say).also { recent = it }

    @Synchronized
    fun journal(context: Context): OpsJournal =
        journal ?: OpsJournal(context.applicationContext.filesDir, System::currentTimeMillis, canonical, say).also { journal = it }

    /** Every write goes through this one object (r3 D7). */
    @Synchronized
    fun ops(context: Context): FileOps {
        ops?.let { return it }
        val app = context.applicationContext
        return FileOps(
            volumes = { FileVolumes.volumes() },
            canonical = canonical,
            clock = System::currentTimeMillis,
            scan = { paths -> if (paths.isNotEmpty()) MediaScannerConnection.scanFile(app, paths.toTypedArray(), null, null) },
            say = say,
            journal = journal(app),
            recent = recent(app),
            access = { Checklist.allFilesAccess() },
            label = { v -> FileVolumes.byUuid(v.uuid)?.paneLabel ?: v.root },
        ).also { ops = it }
    }

    /** The Recycle Bin's reader (its page's rows; phase 19's stats). */
    fun bin(context: Context): RecycleBin = ops(context).bin
}

/** Files' own settings: one switch today. Kept in the app's private prefs; read at every listing. */
class FilesPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Hidden files (a leading dot) are listed and searched; default off (Edge cases). The shell's `.Tessera` never is. */
    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()
        }

    companion object {
        const val FILE = "files_settings"
        const val KEY_SHOW_HIDDEN = "show_hidden"
    }
}
