package app.tileshell.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.RequiresApi
import app.tileshell.R
import app.tileshell.diag.Diagnostics
import app.tileshell.BuildConfig
import app.tileshell.video.catalogue.QaBases
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Files' copy / move / extract / zip service (phase 18, r3 D8): a foreground service of type `dataSync` in the main
 * process, not exported, started only by [FileOpsClient] from a visible [FilesActivity]. It runs ONE operation at a
 * time on its own thread, through [FileOps] — which writes the operation's `done | cancelled | failed <reason>` line,
 * removes its temp, and names a pulled volume `storage removed` and a lost grant `access removed`.
 *
 * - **What it acts on** comes from [FileOpsClient.take], in this process's memory. Its two intents carry an action and
 *   nothing else: `RUN` (take the waiting operation) and `CANCEL` (the notification's action).
 * - **The notification** (its own low-importance channel) names the operation, shows the percentage and a bar, and
 *   holds "Cancel"; the app itself shows no percentage (r3 D2). While a conflict's question cannot be seen it reads
 *   "Waiting for an answer in Files".
 * - **Progress**: `[files] copy|move|zip extract|zip create progress <bytes>/<total>` on [ProgressBeat]'s beat (r3 V7).
 * - **The pace** (Q-18-3): a debug build reads `qa_files_rate_bps` and, when it is set, writes `[files] qa pace <bps>`
 *   once and hands [FileOps] the cap; a release build never reads it.
 * - **Home, a call, the screen going off** do not stop it: it is a started foreground service, and it holds a partial
 *   wake lock while bytes move (not while it waits for an answer).
 * - **The time limit**: the platform limits how long a `dataSync` service may run and calls [onTimeout]; the operation
 *   then ends `failed time limit` with its temp removed, and the service stops.
 * - **A kill or a reboot** ends it with no chance to clean up: the journal names the temp and [FilesEnv.sweep] removes
 *   it. Never restarted by the system (`START_NOT_STICKY`): a restart could not go foreground, and has nothing to run.
 */
class FileOpsService : Service() {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "files-ops") }
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val say: (String) -> Unit = { Diagnostics.add("files", it) }

    /** An operation is on the worker (main thread only). */
    private var running = false
    private var shown: FileOpsText.Note? = null
    private lateinit var wake: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera:files-ops").apply { setReferenceCounted(false) }
        // The notification follows the operation and whether Files is on screen.
        scope.launch {
            FileOpsClient.state.combine(FileOpsClient.filesVisible) { state, visible -> FileOpsText.note(state, visible) }.collect { note ->
                if (running && note != null && note != shown) {
                    shown = note
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(note))
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> run()
            ACTION_CANCEL -> {
                FileOpsClient.cancel()
                if (!running) stopSelf()
            }
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Takes the waiting operation and runs it. */
    private fun run() {
        val request = FileOpsClient.take()
        val note = FileOpsText.note(FileOpsClient.state.value, FileOpsClient.filesVisible.value) ?: FileOpsText.Note(FileOpsText.CHANNEL_NAME, "", null)
        // First, whatever follows: a service started with startForegroundService owes the platform this call.
        val refusal = try {
            startForeground(NOTIFICATION_ID, notification(note), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            shown = note
            null
        } catch (e: Exception) {
            e.javaClass.simpleName
        }
        if (request == null) {
            if (!running) end()
            return
        }
        if (refusal != null) {
            // Android would not put the service in the foreground (Files left the screen in the same breath): nothing is written.
            say("${request.kind.verb} not started: $refusal")
            FileOpsClient.run.finish(OpResult.Failed(FileOpsClient.NOT_STARTED))
            end()
            return
        }
        running = true
        worker.execute {
            val result = perform(request)
            main.post {
                FileOpsClient.run.finish(result)
                end()
            }
        }
    }

    /** The operation itself, on the worker. [FileOps] writes its line and cleans up after it, however it ends. */
    private fun perform(request: FileOpsClient.Request): OpResult {
        val run = FileOpsClient.run
        val kind = request.kind
        val ops = FilesEnv.ops(this)
        val rate = FilePace.rate(BuildConfig.DEBUG, QaBases.read(this, QaBases.FILES_RATE))
        val pace = FilePace.of(rate)
        if (pace != null && rate != null) say(FileOpsText.paceLine(rate))
        val beat = ProgressBeat()
        val control = OpControl(
            progress = { bytes, total ->
                if (beat.due()) {
                    say(FileOpsText.progressLine(kind, bytes, total))
                    run.progress(bytes, total)
                }
            },
            stopped = run::stopped,
            pace = pace,
        )
        val conflict: (java.io.File) -> Conflict = { clash ->
            val known = request.known[clash.path]
            if (known != null) {
                run.ask(clash, known)
            } else {
                // Waiting for a person: the phone may sleep meanwhile.
                wake.release()
                try {
                    run.ask(clash, null)
                } finally {
                    wake.acquire()
                }
            }
        }
        wake.acquire()
        return try {
            when (kind) {
                OpKind.COPY -> ops.copy(request.sources, request.destination, control, conflict)
                OpKind.MOVE -> ops.move(request.sources, request.destination, control, conflict)
                OpKind.EXTRACT -> ops.zipExtract(request.sources.first(), control, conflict)
                OpKind.CREATE_ZIP -> ops.zipCreate(request.sources, request.destination, control, conflict)
            }
        } catch (e: Exception) {
            // FileOps turns every failed write into its own result and line; this is an error outside its writes (the
            // journal's or the scanner's), and the service must still end and say so.
            say("${kind.verb} ended on an error: ${e.javaClass.simpleName}")
            OpResult.Failed(e.javaClass.simpleName)
        } finally {
            wake.release()
        }
    }

    /** The operation is over (or never began): the notification goes and the service stops. */
    private fun end() {
        running = false
        shown = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The platform's limit for a `dataSync` service is reached: the operation is told, ends `failed time limit`, and [end] follows. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        if (!FileOpsClient.run.stop(StopReason.TIME_LIMIT) && !running) end()
    }

    override fun onDestroy() {
        scope.cancel()
        worker.shutdown()
        super.onDestroy()
    }

    private fun notification(note: FileOpsText.Note): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, FileOpsText.CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW))
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, FilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), immutable)
        val cancel = PendingIntent.getService(this, 1, Intent(this, FileOpsService::class.java).setAction(ACTION_CANCEL), immutable)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_files_glyph)
            .setContentTitle(note.title)
            .setContentText(note.text)
            .setProgress(100, note.percent ?: 0, note.percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, FileOpsText.CANCEL, cancel).build())
            .build()
    }

    companion object {
        /** Take the operation [FileOpsClient] holds and run it. */
        const val ACTION_RUN = "app.tileshell.files.action.RUN"

        /** The notification's "Cancel". */
        const val ACTION_CANCEL = "app.tileshell.files.action.CANCEL"

        const val CHANNEL_ID = "files_ops"
        const val NOTIFICATION_ID = 18_001
    }
}
