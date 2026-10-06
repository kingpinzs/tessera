package app.tileshell.files

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import app.tileshell.diag.Diagnostics
import dalvik.system.ZipPathValidator
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Files' writes as the phone runs them (phase 18, r3 D7 / D4): the process's ONE [FileOps] ([FilesStores.ops] — the
 * same object the pages read through, because a second journal in the process would let a sweep delete a temp that is
 * being written), the quick writes the pages make, off the main thread, and the sweep of what a killed operation left.
 * The long writes — copy, move, extract, create — are [FileOpsClient]'s.
 *
 * Loading this object clears the platform's zip path validator for the process (`ZipPathValidator.clearCallback()`):
 * with it set, `ZipFile`'s constructor throws for an archive holding one `../` or absolute entry name, so nothing of
 * that archive could be extracted. Files' own guard ([ZipNames.normalise], then the canonical-path check in
 * [ZipWrites.extract]) is the one that refuses those entries while the others extract (E13). `ShellApp` loads this
 * at the main process's start ([sweep]), so it is cleared before any page can open a zip.
 */
object FilesEnv {
    private val say: (String) -> Unit = { Diagnostics.add("files", it) }

    /** The quick writes' one thread: they are applied in the order they were asked. */
    private val io = Executors.newSingleThreadExecutor { Thread(it, "files-io").apply { isDaemon = true } }.asCoroutineDispatcher()

    /** The sweeps' thread: a sweep can remove a tree of a few gigabytes and must not hold a rename up. */
    private val sweeper = Executors.newSingleThreadExecutor { Thread(it, "files-sweep").apply { isDaemon = true } }

    private var following = false

    init {
        ZipPathValidator.clearCallback()
        say("zip: platform path validator cleared")
    }

    /** Every write goes through this one object (r3 D7). */
    fun ops(context: Context): FileOps = FilesStores.ops(context)

    /**
     * The volumes mounted NOW, read from `StorageManager.getStorageVolumes()` itself — the sweep's own reading, so it
     * does not depend on the volume tracker having started. The same uuid and root [FileVolumes] gives a volume.
     */
    fun mountedVolumes(context: Context): List<FileVolume> = runCatching {
        context.applicationContext.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty().mapNotNull { v ->
            if (v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
            val dir = v.directory ?: return@mapNotNull null
            val uuid = if (v.isPrimary) FilePaths.PRIMARY else v.uuid ?: return@mapNotNull null
            FileVolume(uuid, (FilesStores.canonical(dir.path) ?: dir.path).trimEnd('/'))
        }
    }.getOrDefault(emptyList())

    // ---- the sweep (r3 D4)

    /**
     * The main process's first start after unlock (`ShellApp`): removes the temps the journal names on the volumes
     * mounted now — `[files] sweep: removed <n>`. A temp of an operation this process is running is never touched.
     *
     * From then on the journal is also looked at whenever Files comes on screen: a temp that could not be removed at
     * the start — All-files access was off, as after a revoke that killed the copy — goes once the grant is back
     * (r3 D4: "the temp is swept after the re-grant"; turning the grant on restarts nothing).
     */
    fun sweep(context: Context) {
        val app = context.applicationContext
        sweeper.execute { FilesStores.journal(app).sweep(mountedVolumes(app)) }
        if (following) return
        following = true
        (app as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity is FilesActivity && !FileOpsClient.run.active) sweepJournalled(app, uuid = null)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** A volume was mounted (the volume tracker's call): the temps the journal names on THAT volume are removed. */
    fun onVolumeMounted(context: Context, uuid: String) = sweepJournalled(context.applicationContext, uuid)

    /** A sweep only when the journal names something (on [uuid]'s volume, or on any): otherwise nothing is read or said. */
    private fun sweepJournalled(app: Context, uuid: String?) {
        sweeper.execute {
            val journal = FilesStores.journal(app)
            if (journal.entries().none { uuid == null || it.volumeUuid == uuid }) return@execute
            journal.sweep(mountedVolumes(app).filter { uuid == null || it.uuid == uuid })
        }
    }

    // ---- the quick writes: each runs on the writes' thread and returns FileOps' own result (and writes its line)

    /** Any call on [FileOps] off the main thread (the named ones below are this). */
    suspend fun <T> write(context: Context, body: (FileOps) -> T): T {
        val ops = ops(context)
        return withContext(io) { body(ops) }
    }

    suspend fun rename(context: Context, file: File, newName: String): OpResult = write(context) { it.rename(file, newName) }

    suspend fun newFolder(context: Context, parent: File, name: String): OpResult = write(context) { it.newFolder(parent, name) }

    /** Delete: into the file's own volume's bin. */
    suspend fun binDelete(context: Context, file: File): OpResult = write(context) { it.binDelete(file) }

    /**
     * Restore; [conflict] is asked (the replace / keep both / skip dialog) while the write waits. Leaving the page
     * while it is asked cancels the caller, and with it the question: nothing has been moved by then.
     */
    suspend fun binRestore(context: Context, entry: RecycleBin.Entry, conflict: suspend (File) -> Conflict): OpResult {
        val caller = currentCoroutineContext()[Job]
        return write(context) { ops -> ops.binRestore(entry) { clash -> runBlocking(Job(caller)) { conflict(clash) } } }
    }

    suspend fun binPurge(context: Context, entry: RecycleBin.Entry): OpResult = write(context) { it.binPurge(entry) }

    suspend fun binEmpty(context: Context, volume: FileVolume): OpResult = write(context) { it.binEmpty(volume) }

    /** Recent: [path] was handed to an opener. */
    suspend fun recentAdd(context: Context, path: String, volumeUuid: String) {
        val recent = FilesStores.recent(context)
        withContext(io) { recent.add(path, volumeUuid) }
    }

    /** Recent's "Remove from recent". */
    suspend fun recentRemove(context: Context, path: String) {
        val recent = FilesStores.recent(context)
        withContext(io) { recent.remove(path) }
    }
}
