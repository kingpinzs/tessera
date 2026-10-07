package app.tileshell.files

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import app.tileshell.R
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

/**
 * One mounted volume as the ≡ pane shows it: [label] is the platform's own text (`StorageVolume.getDescription`, e.g.
 * "Virtual SD card") with no drive letter (T18-8); the primary shared storage is drawn "This Device".
 */
data class MountedVolume(val uuid: String, val root: String, val label: String, val removable: Boolean, val primary: Boolean) {
    val volume: FileVolume get() = FileVolume(uuid, root)

    /** The pane row's and the breadcrumb's text. */
    val paneLabel: String get() = if (primary) THIS_DEVICE else label

    companion object {
        const val THIS_DEVICE = "This Device"
    }
}

/**
 * The storage volumes mounted now (r3 D9), tracked for the life of the shell's main process: started by `ShellApp`,
 * fed by `StorageManager.registerStorageVolumeCallback` and reconciled against `getStorageVolumes()` at every process
 * start. The ≡ pane's rows and the open page read [mounted]; the dynamic "SD card" App Shortcut (`files_sdcard`,
 * T18-6 / C-9) is published and withdrawn from here too, so a card inserted before Files is ever opened has its
 * shortcut and one pulled while the process was dead loses it at the next start.
 */
object FileVolumes {
    const val SHORTCUT_ID = "files_sdcard"
    private const val SHORTCUT_LABEL = "SD card"
    private const val SHORTCUT_RANK = 3

    private val state = MutableStateFlow<List<MountedVolume>>(emptyList())

    /** The mounted volumes, the primary first, then the others in the platform's order. */
    val mounted: StateFlow<List<MountedVolume>> = state

    /** The callback's reads and the shortcut's binder calls run here, off the main thread; one thread, so two changes are applied in the order they came. */
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "files-volumes").apply { isDaemon = true } }

    @Volatile
    private var started = false

    /** The plain volumes, for the pure layer ([FileOps], [FilePaths.volumeOf]). */
    fun volumes(): List<FileVolume> = state.value.map { it.volume }

    fun byUuid(uuid: String): MountedVolume? = state.value.firstOrNull { it.uuid == uuid }

    /** Once per process, from `ShellApp`: the first read (`[files] roots: …`), the shortcut's reconcile, the callback. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val storage = app.getSystemService(StorageManager::class.java) ?: run {
            Diagnostics.add("files", "roots: 0 (no storage manager)")
            return
        }
        // The first read is made here, on the caller's thread, so a page opened in the same breath finds its volumes.
        apply(app, read(app, storage), first = true)
        runCatching {
            storage.registerStorageVolumeCallback(worker, object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) {
                    apply(app, read(app, storage), first = false)
                }
            })
        }.onFailure { Diagnostics.add("files", "volume callback failed: $it") }
    }

    /** What `getStorageVolumes()` holds mounted, with a directory to read. */
    private fun read(context: Context, storage: StorageManager): List<MountedVolume> = runCatching {
        storage.storageVolumes.mapNotNull { v ->
            val mounted = v.state == Environment.MEDIA_MOUNTED || v.state == Environment.MEDIA_MOUNTED_READ_ONLY
            val dir = v.directory ?: return@mapNotNull null
            if (!mounted) return@mapNotNull null
            val uuid = if (v.isPrimary) FilePaths.PRIMARY else v.uuid ?: return@mapNotNull null
            val root = runCatching { dir.canonicalPath }.getOrDefault(dir.path)
            MountedVolume(uuid, root.trimEnd('/'), v.getDescription(context).orEmpty().ifBlank { uuid }, v.isRemovable, v.isPrimary)
        }.sortedByDescending { it.primary }
    }.getOrElse {
        Diagnostics.add("files", "roots: read failed: $it")
        emptyList()
    }

    @Synchronized
    private fun apply(context: Context, now: List<MountedVolume>, first: Boolean) {
        val before = state.value
        if (first) {
            Diagnostics.add("files", "roots: ${now.size} (${now.joinToString(", ") { FilePaths.lineText(it.label, 60) }})")
        } else {
            for (v in now) {
                if (before.any { it.uuid == v.uuid }) continue
                Diagnostics.add("files", "volume mounted ${FilePaths.lineText(v.label, 60)}")
                // r3 D4: a temp a killed operation left on this volume is swept as it mounts.
                FilesEnv.onVolumeMounted(context, v.uuid)
            }
            for (v in before) if (now.none { it.uuid == v.uuid }) Diagnostics.add("files", "volume unmounted ${FilePaths.lineText(v.label, 60)}")
        }
        state.value = now
        val card = now.firstOrNull { it.removable && !it.primary }
        if (first) worker.execute { syncShortcut(context, card) } else syncShortcut(context, card)
    }

    /**
     * `files_sdcard` matches the first removable volume: published while one is mounted, removed when none is. Attached
     * to FilesActivity by `setActivity` — without it a dynamic shortcut hangs on the package's first launcher activity
     * and would burst on the Music tile (C-21). `addDynamicShortcuts`, so the package's other dynamic shortcuts stay.
     */
    private fun syncShortcut(context: Context, card: MountedVolume?) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val page = card?.let { FilesIntents.volumePage(it.uuid) }
        val had = runCatching { manager.dynamicShortcuts.firstOrNull { it.id == SHORTCUT_ID } }.getOrNull()
        if (page == null) {
            if (had == null) return
            runCatching { manager.removeDynamicShortcuts(listOf(SHORTCUT_ID)) }
                .onSuccess { Diagnostics.add("files", "shortcut sdcard removed") }
                .onFailure { Diagnostics.add("files", "shortcut sdcard: not removed: $it") }
            return
        }
        // Already there for this very card: nothing to publish, and no call spent against the rate limit.
        if (had != null && had.intent?.getStringExtra(FilesIntents.EXTRA_PAGE) == page) return
        val activity = ComponentName(context, FilesActivity::class.java)
        val info = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(SHORTCUT_LABEL)
            .setLongLabel(SHORTCUT_LABEL)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_files_glyph))
            .setIntent(Intent(Intent.ACTION_VIEW).setComponent(activity).putExtra(FilesIntents.EXTRA_PAGE, page))
            .setActivity(activity)
            .setRank(SHORTCUT_RANK)
            .build()
        val ok = runCatching { manager.addDynamicShortcuts(listOf(info)) }
        when {
            ok.getOrNull() == true -> Diagnostics.add("files", "shortcut sdcard published")
            ok.isSuccess -> Diagnostics.add("files", "shortcut sdcard failed (rate limit)")
            else -> Diagnostics.add("files", "shortcut sdcard: not published: ${ok.exceptionOrNull()}")
        }
    }
}
