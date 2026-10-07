package app.tileshell.files

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import app.tileshell.diag.Diagnostics
import app.tileshell.music.MusicActivity
import app.tileshell.music.MusicPlayExtra
import app.tileshell.music.MusicStore
import app.tileshell.photos.ViewerActivity
import app.tileshell.video.PlayerActivity
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Open-with routing as pure rules (build task 4; Q4 A, r3 D1, "below Q-18-2"): which opener a tapped file goes to and
 * what it is handed. `OpenRulesTest` holds them.
 */
object OpenRules {
    const val NO_HANDLER = "No app on this phone opens this"
    const val NOT_OPENED = "This file can't be opened"

    /** The type of a file MimeTypeMap does not know. */
    const val UNKNOWN = "application/octet-stream"

    /** What a path query on the file's MediaStore collection found: the row's id and, for audio, `IS_RECORDING`. */
    data class MediaRow(val id: Long, val recording: Boolean = false)

    sealed interface Route {
        /** Photos' viewer; [rowId] null = no MediaStore row, so the shell FileProvider's URI. */
        data class Viewer(val rowId: Long?) : Route

        /** The shared video player; [rowId] as [Viewer]'s. */
        data class Player(val rowId: Long?) : Route

        /** Music plays the library track with this MediaStore audio id. */
        data class MusicId(val id: Long) : Route

        /** Music plays the one file by the shell FileProvider's URI, outside its library (Q-18-2). */
        data object MusicUri : Route

        /** Every other type: Android's chooser. */
        data object Chooser : Route
    }

    /**
     * @param kind the file's kind by its name
     * @param row the row a path query found on the kind's own collection, null when there is none
     * @param musicReadsLibrary whether Music can read the audio library at all (`READ_MEDIA_AUDIO`)
     *
     * Audio goes by id only when Music's library HOLDS the row: Music lists every audio row except recordings
     * (`MusicStore`), and nothing without its permission — an id it does not hold is ignored there, so those go by URI.
     */
    fun route(kind: FileKind, row: MediaRow?, musicReadsLibrary: Boolean): Route = when (kind) {
        FileKind.IMAGE -> Route.Viewer(row?.id)
        FileKind.VIDEO -> Route.Player(row?.id)
        FileKind.AUDIO -> if (row != null && !row.recording && musicReadsLibrary) Route.MusicId(row.id) else Route.MusicUri
        else -> Route.Chooser
    }

    /**
     * The MIME type an opener is given. [mapped] is the platform's type for the file's extension (null when it knows
     * none). The viewer and the player are reached by kind, so they always get a type of their own family.
     */
    fun mime(kind: FileKind, mapped: String?): String = when (kind) {
        FileKind.IMAGE -> mapped?.takeIf { it.startsWith("image/") } ?: "image/*"
        FileKind.VIDEO -> mapped?.takeIf { it.startsWith("video/") } ?: "video/*"
        FileKind.AUDIO -> mapped?.takeIf { it.startsWith("audio/") } ?: "audio/*"
        else -> mapped?.takeIf { it.isNotBlank() } ?: UNKNOWN
    }

    /** MediaStore's name for a volume: `external_primary`, or the volume's UUID in lower case (the platform's rule). */
    fun mediaVolumeName(volumeUuid: String): String =
        if (volumeUuid == FilePaths.PRIMARY) "external_primary" else volumeUuid.lowercase(Locale.ROOT)

    /**
     * The path a chooser pick may add to Recent, or null: the path the receiver was handed must be canonical already,
     * lie under a mounted volume and outside the shell's own folder there. Returns the volume with it.
     */
    fun pickTarget(path: String?, volumes: List<FileVolume>, canonical: (String) -> String?): Pair<String, FileVolume>? {
        if (path.isNullOrEmpty()) return null
        val real = runCatching { canonical(path) }.getOrNull() ?: return null
        if (real != path) return null
        val volume = FilePaths.volumeOf(path, volumes, canonical) ?: return null
        if (real == volume.root.trimEnd('/') || FilePaths.inShellDir(path, volume, canonical)) return null
        return path to volume
    }
}

/**
 * Open-with and Recent's own menu (build tasks 4 and 10), the [FilesOpening] `FilesActivity` hands [FilesBehaviour].
 *
 * A tap on a file: an image opens in Photos' viewer, a video in the shared player — explicit component, `ACTION_VIEW`,
 * the MediaStore row's URI when a path query finds one, else the shell FileProvider's (`open <path> via provider`); an
 * audio file plays in Music by its library id, or by the provider's URI when the library does not hold it; anything
 * else goes to Android's chooser with a read grant, and a type nothing handles says so. Every hand-off to the shell's
 * own apps adds the file to Recent there and then; the chooser adds it only when an app was picked
 * ([FilesOpenReceiver]).
 */
class FilesOpener(private val activity: FilesActivity) : FilesOpening {
    private val app = activity.applicationContext
    private val scope: CoroutineScope get() = activity.lifecycleScope

    override fun open(state: FilesState, entry: FileEntry) {
        scope.launch {
            val file = File(entry.path)
            val kind = FileKind.of(entry.name, false)
            val row = withContext(Dispatchers.IO) { mediaRow(kind, entry) }
            val mime = OpenRules.mime(kind, FilesShare.mimeOf(entry.name).takeIf { it != OpenRules.UNKNOWN })
            when (val route = OpenRules.route(kind, row, MusicStore.hasAccess(app))) {
                is OpenRules.Route.Viewer -> view(state, entry, file, ViewerActivity::class.java, route.rowId?.let { rowUri(kind, entry, it) }, mime)
                is OpenRules.Route.Player -> view(state, entry, file, PlayerActivity::class.java, route.rowId?.let { rowUri(kind, entry, it) }, mime)
                is OpenRules.Route.MusicId -> music(state, entry) { it.putExtra(MusicPlayExtra.EXTRA_PLAY_ID, route.id) }
                OpenRules.Route.MusicUri -> {
                    val uri = providerUri(state, file) ?: return@launch
                    music(state, entry) { it.putExtra(MusicPlayExtra.EXTRA_PLAY_URI, uri.toString()) }
                }
                OpenRules.Route.Chooser -> choose(state, entry, file, mime)
            }
        }
    }

    override fun holdRecent(behaviour: FilesBehaviour, state: FilesState, press: RowPress) {
        val entry = press.entry
        behaviour.showMenu(
            state, press,
            listOf(
                // The ENTRY goes, never the file ("below Q-18-1").
                HoldItem("remove_recent", "Remove from recent") {
                    scope.launch {
                        FilesEnv.recentRemove(app, entry.path)
                        state.refresh()
                    }
                },
                HoldItem("share", "Share") { behaviour.share(state, listOf(entry)) },
                HoldItem("properties", "Properties") { behaviour.showProperties(state, entry) },
            ),
        )
    }

    // ------------------------------------------------------------------------------------------ the openers

    /** The shell's viewer or player: `ACTION_VIEW` on a `content://` URI, by explicit component (the shell's own launch). */
    private suspend fun view(state: FilesState, entry: FileEntry, file: File, target: Class<*>, row: Uri?, mime: String) {
        val uri = row ?: providerUri(state, file)?.also { say("open ${FilePaths.lineText(entry.path)} via provider") } ?: return
        handOff(state, entry, Intent(Intent.ACTION_VIEW).setClass(activity, target).setDataAndType(uri, mime))
    }

    /**
     * Music, in its own task: a running Music takes the play through `onNewIntent` (single top), a new one through
     * its launch. The extra is honoured there only because this launch is the shell's own (r3 D5).
     */
    private suspend fun music(state: FilesState, entry: FileEntry, extra: (Intent) -> Unit) {
        val play = Intent(activity, MusicActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        extra(play)
        handOff(state, entry, play)
    }

    /** Starts one of the shell's own openers; the file joins Recent at the hand-off. */
    private suspend fun handOff(state: FilesState, entry: FileEntry, intent: Intent) {
        val started = runCatching { activity.startActivity(intent) }
            .onFailure { say("open ${FilePaths.lineText(entry.path)} not started: ${it.javaClass.simpleName}") }
            .isSuccess
        if (!started) return state.error(OpenRules.NOT_OPENED)
        FilesEnv.recentAdd(app, entry.path, entry.volumeUuid)
    }

    /**
     * Android's chooser for every other type: `ACTION_VIEW`, the file's type, the provider's URI with a read grant.
     * The pick comes back to [FilesOpenReceiver] through the chooser's own callback; a chooser backed out of sends
     * nothing, so nothing is added.
     */
    private fun choose(state: FilesState, entry: FileEntry, file: File, mime: String) {
        val uri = providerUri(state, file) ?: return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val handlers = runCatching { activity.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
        if (handlers.isEmpty()) {
            say("no handler for $mime")
            return state.error(OpenRules.NO_HANDLER)
        }
        // Mutable so the chooser can add the component it started; the path is this intent's own extra, which a
        // fill-in cannot replace, and the receiver checks it again. One shot, and a new chooser cancels the last one's.
        val picked = PendingIntent.getBroadcast(
            activity, 0,
            Intent(activity, FilesOpenReceiver::class.java).putExtra(FilesOpenReceiver.EXTRA_PATH, entry.path),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT,
        )
        runCatching { activity.startActivity(Intent.createChooser(view, null, picked.intentSender)) }.onFailure {
            say("open ${FilePaths.lineText(entry.path)} not started: ${it.javaClass.simpleName}")
            state.error(OpenRules.NOT_OPENED)
        }
    }

    // ------------------------------------------------------------------------------------------ URIs

    /** The shell FileProvider's URI; a file it refuses (its own line) opens nothing. */
    private fun providerUri(state: FilesState, file: File): Uri? =
        FilesProvider.uriFor(activity, file) ?: run {
            state.error(OpenRules.NOT_OPENED)
            null
        }

    private fun collection(kind: FileKind, volumeUuid: String): Uri? {
        val volume = OpenRules.mediaVolumeName(volumeUuid)
        return when (kind) {
            FileKind.IMAGE -> MediaStore.Images.Media.getContentUri(volume)
            FileKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
            FileKind.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
            else -> null
        }
    }

    private fun rowUri(kind: FileKind, entry: FileEntry, id: Long): Uri? = collection(kind, entry.volumeUuid)?.let { ContentUris.withAppendedId(it, id) }

    /**
     * The file's row on its own volume's collection, by absolute path; null when MediaStore holds none (a `.nomedia`
     * folder, an unscanned file, a volume it has not indexed) or cannot be asked.
     */
    private fun mediaRow(kind: FileKind, entry: FileEntry): OpenRules.MediaRow? {
        val from = collection(kind, entry.volumeUuid) ?: return null
        val audio = kind == FileKind.AUDIO
        val columns = if (audio) arrayOf(MediaStore.MediaColumns._ID, MediaStore.Audio.Media.IS_RECORDING) else arrayOf(MediaStore.MediaColumns._ID)
        return runCatching {
            app.contentResolver.query(from, columns, "${MediaStore.MediaColumns.DATA} = ?", arrayOf(entry.path), null)?.use { c ->
                if (c.moveToFirst()) OpenRules.MediaRow(c.getLong(0), audio && c.getInt(1) == 1) else null
            }
        }.onFailure { say("open ${FilePaths.lineText(entry.path)}: media query failed: ${it.javaClass.simpleName} ${FilePaths.lineText(it.message, 120)}") }.getOrNull()
    }

    private fun say(line: String) = Diagnostics.add("files", line)
}

/**
 * The chooser's answer for an open (below Q-18-1): Android sends it when the user picks an app, with the component it
 * started in `EXTRA_CHOSEN_COMPONENT`; a chooser left with no pick sends nothing. Not exported, and reached only
 * through the PendingIntent [FilesOpener] hands the chooser. The file then joins Recent — after the path is checked
 * again ([OpenRules.pickTarget]) and found to be a file that is there.
 */
class FilesOpenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        // No component = no pick: nothing is added.
        runCatching { intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT) }.getOrNull() ?: return
        val app = context.applicationContext
        FileVolumes.start(app)
        val (path, volume) = OpenRules.pickTarget(runCatching { intent.getStringExtra(EXTRA_PATH) }.getOrNull(), FileVolumes.volumes(), FilesStores::canonical) ?: return
        val pending = goAsync()
        Thread({
            try {
                if (File(path).isFile) FilesStores.recent(app).add(path, volume.uuid)
            } finally {
                pending.finish()
            }
        }, "files-open-pick").start()
    }

    companion object {
        const val EXTRA_PATH = "app.tileshell.files.OPEN_PATH"
    }
}
