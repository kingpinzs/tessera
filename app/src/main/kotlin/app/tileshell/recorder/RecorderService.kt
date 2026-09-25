package app.tileshell.recorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.storage.StorageManager
import app.tileshell.R
import app.tileshell.brand.Brand
import app.tileshell.cortana.speech.ISpeech
import app.tileshell.cortana.speech.ISpeechCallback
import app.tileshell.cortana.speech.MicHolders
import app.tileshell.cortana.speech.SpeechService
import app.tileshell.diag.Diagnostics
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** The take's phases as [IRecorderCallback.onState] reports them. */
object RecorderState {
    const val IDLE = 0
    const val STARTING = 1
    const val RECORDING = 2
    const val PAUSED = 3
    const val SAVING = 4

    fun word(phase: Int): String = when (phase) {
        IDLE -> "idle"
        STARTING -> "starting"
        RECORDING -> "recording"
        PAUSED -> "paused"
        SAVING -> "saving"
        else -> "phase $phase"
    }
}

/**
 * Voice Recorder's take, in its own `app.tileshell:recorder` process (phase 15 Decisions "Voice Recorder
 * mechanics", build task 7; R10 design 27: a Start crash must not end a take, and capture memory stays out of
 * the launcher process — ShellApp returns early here, so no launcher start-up runs in this process).
 *
 * - **A foreground service of type microphone** with an ongoing notification carrying Stop. Started by the page
 *   with startForegroundService; [onStartCommand] returns START_NOT_STICKY, because a system restart after a
 *   kill would call startForeground(microphone) from the background, which API 34+ refuses (T15-39).
 * - **Kill-safe.** [AacCapture] writes each AAC frame behind its ADTS header as it is encoded; the `.m4a` is
 *   built at stop. A take left on disk by a dead process is recovered in [onCreate] — the next bind (the page
 *   opening) or the next take — into a normal recording with its name and markers (E18).
 * - **One microphone.** The take holds the shell's microphone through the `:speech` process's arbiter as its
 *   third owner, named "recorder" (T15-4 / T15-28): it binds ISpeech with the hold-only action, registers, and
 *   holdMicrophone refuses it while Tess or the keyboard listens — and refuses them while it records. A killed
 *   `:recorder`'s binder death frees the hold (onCallbackDied).
 * - **Pauses**: the user's Pause (T15-16), a call read from the audio mode with no permission (T15-36), and
 *   Android silencing the capture for another app (AudioRecordingCallback, T15-38). Each holds the take — time
 *   and markers leave the pause out — until the user resumes or stops.
 * - **The storage floor**: getAllocatableBytes before a take and every 5 s; at 50 MB the take stops and is
 *   saved with "Not enough space" (T15-27).
 *
 * Its diagnostics are this process's own ring, printed by [dump] (`adb shell dumpsys activity service
 * app.tileshell/.recorder.RecorderService`, T15-17); the page binds while it shows, so the dump answers then.
 */
class RecorderService : Service() {

    /** Take lifecycle, storage checks and recovery: everything that changes a take runs on this one thread. */
    private lateinit var worker: ScheduledExecutorService

    /** Callbacks to the page, in order, one broadcast at a time (RemoteCallbackList allows no overlap). */
    private lateinit var dispatcher: ExecutorService

    private val callbacks = RemoteCallbackList<IRecorderCallback>()

    @Volatile private var phase = RecorderState.IDLE

    private class Take(
        val name: String,
        val files: TakeFiles,
        val out: FileOutputStream,
        val clock: TakeClock,
        val capture: AacCapture,
        val startedWall: Long,
    ) {
        var storageCheck: ScheduledFuture<*>? = null
    }

    @Volatile private var take: Take? = null

    private lateinit var audio: AudioManager

    // ---- the :speech binding that holds the microphone ----------------------------------------------------

    @Volatile private var speech: ISpeech? = null
    @Volatile private var speechBound = false
    @Volatile private var speechReady = CountDownLatch(1)

    /** Set when a bound `:speech` went away: the next connect is a restart whose fresh arbiter must be held again. */
    @Volatile private var speechLost = false

    /**
     * A recovered take saved before any page registered (a reopen binds, and onCreate's recovery races the
     * register): told to the first page that registers, so its list goes to the recovered row (E18).
     */
    private val savedUntold = ArrayList<Pair<Long, String>>()

    /** Our identity to the arbiter. The speech process never sends a hold-only client anything that matters. */
    private val speechCallback = object : ISpeechCallback.Stub() {
        override fun onListening() = Unit
        override fun onPartial(text: String?) = Unit
        override fun onFinal(open: String?, grammar: String?, audioMs: Int) = Unit
        override fun onLevel(level: Float) = Unit
        override fun onSpeakingLevel(utteranceId: String?, level: Float) = Unit
        override fun onSpeakingDone(utteranceId: String?, cancelled: Boolean) = Unit
        override fun onError(code: Int, detail: String?) = Unit
    }

    private val speechConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = ISpeech.Stub.asInterface(binder)
            runCatching { s.register(speechCallback) }
                .onFailure { Diagnostics.add("recorder", "speech register failed: $it") }
            speech = s
            speechReady.countDown()
            val restarted = speechLost
            speechLost = false
            Diagnostics.add("recorder", "bound to :speech (microphone hold)")
            // A :speech process that died mid-take came back with a fresh arbiter: take the hold again.
            if (restarted) {
                worker.execute {
                    if (take != null) {
                        val holder = runCatching { s.holdMicrophone(speechCallback, MicHolders.RECORDER) }.getOrNull()
                        Diagnostics.add("recorder", "microphone hold re-taken after :speech restarted -> ${holder ?: "held"}")
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            speech = null
            speechReady = CountDownLatch(1)
            speechLost = true
            Diagnostics.add("recorder", ":speech process gone; the hold is taken again when it returns")
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "recorder-worker") }
        dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "recorder-callbacks") }
        Diagnostics.add("recorder", "service created pid=${Process.myPid()}")
        // T15-39: a take whose process died is recovered by the next start or bind, which is this.
        worker.execute { recoverLeftovers() }
    }

    override fun onBind(intent: Intent?): IBinder {
        Diagnostics.add("recorder", "page bound")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Diagnostics.add("recorder", "page unbound")
        return false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (goForeground()) {
                    worker.execute { startTake() }
                } else {
                    // The page set itself to "starting"; tell it the take never began.
                    pushState()
                    stopSelf(startId)
                }
            }
            ACTION_STOP -> worker.execute { stopTake("notification") }
            else -> Diagnostics.add("recorder", "start command ignored: ${intent?.action}")
        }
        // T15-39: never restarted by the system after a kill — a background startForeground(microphone) is refused.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Diagnostics.add("recorder", "service destroyed")
        val t = take
        if (t != null) {
            // Not reached in normal use (a take keeps the service started); if it is, the take is left on disk
            // whole and the next start recovers it.
            Diagnostics.add("recorder", "destroyed during the take ${t.name}: it is recovered at the next start")
            runCatching { t.capture.stop() }
            runCatching { t.out.close() }
            take = null
        }
        releaseHold()
        if (speechBound) {
            runCatching { speech?.unregister(speechCallback) }
            runCatching { unbindService(speechConnection) }
            speechBound = false
        }
        callbacks.kill()
        worker.shutdown()
        dispatcher.shutdown()
        super.onDestroy()
    }

    /**
     * `adb shell dumpsys activity service app.tileshell/.recorder.RecorderService` — this process's own
     * [Diagnostics] ring, printed exactly as SpeechService prints the `:speech` ring (T15-17), with the take's
     * state above it.
     */
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        writer.println("tileshell recorder process pid=${Process.myPid()}")
        writer.println("--- status ---")
        val t = take
        writer.println("phase=${RecorderState.word(phase)}")
        writer.println("take=${t?.name.orEmpty()}")
        if (t != null) {
            synchronized(t.clock) {
                writer.println("elapsed_ms=${t.clock.elapsedMs}")
                writer.println("paused=${t.clock.paused?.word.orEmpty()}")
                writer.println("markers=${t.clock.markers.joinToString(",")}")
            }
            writer.println("frames=${t.capture.frames}")
            writer.println("file=${t.files.audio.name} bytes=${t.files.audio.length()}")
        }
        writer.println("speech_bound=${speech != null}")
        writer.println("--- diagnostics ---")
        Diagnostics.dump(writer)
    }

    // ---- starting a take ----------------------------------------------------------------------------------------

    /** The foreground half of a start, synchronous in onStartCommand as startForegroundService requires. */
    private fun goForeground(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Diagnostics.add("recorder", "refused: RECORD_AUDIO is not granted")
            notice("Voice Recorder can't use the microphone. Allow it here, or from Microphone in ${Brand.ASSISTANT_NAME}'s settings.")
            return false
        }
        return try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            true
        } catch (e: Exception) {
            Diagnostics.add("recorder", "refused: the foreground start was not allowed: $e")
            notice("Voice Recorder couldn't start recording.")
            false
        }
    }

    private fun startTake() {
        if (phase != RecorderState.IDLE) {
            Diagnostics.add("recorder", "start ignored: a take is ${RecorderState.word(phase)}")
            return
        }
        setPhase(RecorderState.STARTING)
        if (isCallMode(audio.mode)) {
            Diagnostics.add("recorder", "refused: a call is on (audio mode ${audio.mode})")
            endStart("Voice Recorder can't record during a call.")
            return
        }
        val s = speechForHold()
        if (s == null) {
            Diagnostics.add("recorder", "refused: the microphone arbiter did not answer")
            endStart("The microphone couldn't be reserved right now.")
            return
        }
        val holder = try {
            s.holdMicrophone(speechCallback, MicHolders.RECORDER)
        } catch (e: RemoteException) {
            Diagnostics.add("recorder", "refused: the microphone arbiter failed: $e")
            endStart("The microphone couldn't be reserved right now.")
            return
        }
        if (holder != null) {
            // T15-28: the holder's name, for the row and for the page's notice.
            Diagnostics.add("recorder", "refused: microphone busy held by $holder")
            endStart(MicHolders.recorderNotice(holder, Brand.ASSISTANT_NAME))
            return
        }
        val free = allocatableBytes()
        if (!StorageFloor.canStart(free)) {
            Diagnostics.add("recorder", "storage floor")
            releaseHold()
            endStart(StorageFloor.NOTICE)
            return
        }
        val name = RecordingNames.next(RecordingStore.namesInRecordings(this))
        val started = System.currentTimeMillis()
        val files = TakeFiles(TakeFiles.dir(this), started)
        val clock = TakeClock()
        val out: FileOutputStream
        val capture: AacCapture
        try {
            files.writeSidecar(TakeFiles.Sidecar(name, started, emptyList()))
            out = FileOutputStream(files.audio)
            capture = AacCapture(
                out = out,
                clock = clock,
                onLevel = { level, elapsed -> pushLevel(level, elapsed) },
                onFailed = { why -> worker.execute { failTake(why) } },
            )
        } catch (t: Throwable) {
            Diagnostics.add("recorder", "refused: the take could not be written: $t")
            files.delete()
            releaseHold()
            endStart("Voice Recorder couldn't start recording.")
            return
        }
        try {
            capture.start()
        } catch (t: Throwable) {
            Diagnostics.add("recorder", "refused: the microphone would not open: $t")
            runCatching { out.close() }
            files.delete()
            releaseHold()
            endStart("The microphone couldn't be opened.")
            return
        }
        val t = Take(name, files, out, clock, capture, started)
        take = t
        Diagnostics.add("recorder", "start $name free=${RecorderFormat.mebibytes(free)}")
        watchPauses(true)
        t.storageCheck = worker.scheduleWithFixedDelay(
            { checkStorage() },
            StorageFloor.CHECK_EVERY_MS, StorageFloor.CHECK_EVERY_MS, TimeUnit.MILLISECONDS,
        )
        setPhase(RecorderState.RECORDING)
        updateNotification()
    }

    /** A start that ends before any audio: say why, drop the foreground state and the started service. */
    private fun endStart(message: String) {
        notice(message)
        setPhase(RecorderState.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** The bound ISpeech, binding (hold-only: no model load) and waiting for it if need be. */
    private fun speechForHold(): ISpeech? {
        speech?.let { return it }
        if (!speechBound) {
            val intent = Intent(this, SpeechService::class.java).setAction(SpeechService.ACTION_MIC_HOLD)
            speechBound = runCatching { bindService(intent, speechConnection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            Diagnostics.add("recorder", "bind :speech (microphone hold) -> $speechBound")
            if (!speechBound) return null
        }
        speechReady.await(SPEECH_WAIT_MS, TimeUnit.MILLISECONDS)
        return speech
    }

    private fun releaseHold() {
        val s = speech ?: return
        runCatching { s.releaseMicrophone(speechCallback) }
            .onFailure { Diagnostics.add("recorder", "release of the microphone hold failed: $it") }
    }

    // ---- pausing, resuming, markers --------------------------------------------------------------------------

    private fun pauseTake(reason: TakeClock.Pause) {
        val t = take ?: return
        if (phase != RecorderState.RECORDING && phase != RecorderState.PAUSED) return
        val newly = synchronized(t.clock) { t.clock.pause(reason) }
        if (!newly) return
        t.capture.pauseAudio()
        Diagnostics.add("recorder", "paused: ${reason.word}")
        setPhase(RecorderState.PAUSED)
        pushLevel(0f, synchronized(t.clock) { t.clock.elapsedMs })
        if (reason == TakeClock.Pause.SILENCED) notice("Another app is using the microphone. The recording is paused.")
        if (reason == TakeClock.Pause.CALL) notice("The recording is paused for the call.")
        updateNotification()
    }

    private fun resumeTake() {
        val t = take ?: return
        val inCall = isCallMode(audio.mode)
        val resumed = synchronized(t.clock) { t.clock.resume(inCall) }
        if (!resumed) {
            if (inCall) {
                Diagnostics.add("recorder", "resume refused: a call is on (audio mode ${audio.mode})")
                notice("Resume the recording after the call.")
            }
            return
        }
        t.capture.resumeAudio()
        Diagnostics.add("recorder", "resumed at ${synchronized(t.clock) { t.clock.elapsedMs }} ms")
        setPhase(RecorderState.RECORDING)
        updateNotification()
    }

    private fun flag() {
        val t = take ?: return
        val (at, markers) = synchronized(t.clock) { t.clock.flag() to t.clock.markers }
        runCatching { t.files.writeSidecar(TakeFiles.Sidecar(t.name, t.startedWall, markers)) }
            .onFailure { Diagnostics.add("recorder", "marker sidecar write failed: $it") }
        Diagnostics.add("recorder", "marker ${t.name} at=$at")
        pushState()
    }

    /** The audio mode (T15-36, no permission) and Android's silencing of the capture (T15-38). */
    private fun watchPauses(on: Boolean) {
        if (on) {
            runCatching { audio.addOnModeChangedListener(worker, modeListener) }
                .onFailure { Diagnostics.add("recorder", "audio mode listener failed: $it") }
            runCatching { audio.registerAudioRecordingCallback(recordingCallback, Handler(Looper.getMainLooper())) }
                .onFailure { Diagnostics.add("recorder", "recording callback failed: $it") }
        } else {
            runCatching { audio.removeOnModeChangedListener(modeListener) }
            runCatching { audio.unregisterAudioRecordingCallback(recordingCallback) }
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        Diagnostics.add("recorder", "audio mode $mode")
        if (isCallMode(mode)) pauseTake(TakeClock.Pause.CALL)
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            val list = configs?.toList().orEmpty()
            worker.execute {
                val t = take ?: return@execute
                val session = t.capture.audioSessionId
                // Android's concurrent-capture rule silences a capture another app has taken over: pause rather
                // than write silence. Which app it was is not visible to the shell (T15-38).
                if (list.any { it.clientAudioSessionId == session && it.isClientSilenced }) {
                    pauseTake(TakeClock.Pause.SILENCED)
                }
            }
        }
    }

    private fun isCallMode(mode: Int): Boolean =
        mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_CALL_SCREENING

    // ---- the storage floor ---------------------------------------------------------------------------------

    private fun allocatableBytes(): Long = runCatching {
        val sm = getSystemService(StorageManager::class.java)
        sm.getAllocatableBytes(sm.getUuidForPath(filesDir))
    }.onFailure { Diagnostics.add("recorder", "free space unreadable: $it") }.getOrDefault(Long.MAX_VALUE)

    private fun checkStorage() {
        if (take == null) return
        val free = allocatableBytes()
        if (StorageFloor.mustStop(free)) {
            Diagnostics.add("recorder", "storage floor")
            notice(StorageFloor.NOTICE)
            stopTake("storage floor (${RecorderFormat.mebibytes(free)} MB)")
        }
    }

    // ---- stopping, saving, recovering ------------------------------------------------------------------------

    private fun failTake(why: String) {
        if (take == null) return
        Diagnostics.add("recorder", "the capture failed: $why")
        notice("The recording stopped.")
        stopTake("capture failed")
    }

    private fun stopTake(why: String) {
        val t = take ?: run {
            Diagnostics.add("recorder", "stop ignored ($why): no take")
            return
        }
        setPhase(RecorderState.SAVING)
        t.storageCheck?.cancel(false)
        watchPauses(false)
        t.capture.stop()
        runCatching { t.out.close() }
        releaseHold()
        val markers = synchronized(t.clock) { t.clock.markers }
        val saved = save(t.files, t.name, t.startedWall, markers)
        take = null
        when (saved) {
            is Saved.Done -> {
                Diagnostics.add("recorder", "stop ${saved.name} ms=${saved.durationMs} bytes=${saved.bytes}")
                pushSaved(saved.id, saved.name)
            }
            Saved.Empty -> {
                Diagnostics.add("recorder", "stop ${t.name}: no audio reached the file ($why); nothing saved")
                t.files.delete()
                notice("Nothing was recorded.")
            }
            is Saved.Failed -> {
                Diagnostics.add("recorder", "save of ${t.name} failed: ${saved.why}; kept for the next start")
                notice("The recording couldn't be saved yet. Voice Recorder tries again the next time it opens.")
            }
        }
        setPhase(RecorderState.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private sealed interface Saved {
        data class Done(val id: Long, val name: String, val durationMs: Long, val bytes: Long) : Saved
        data object Empty : Saved
        data class Failed(val why: String) : Saved
    }

    /**
     * The take's frames to a published `.m4a` in Recordings/: a pending MediaStore file, the muxer into its
     * descriptor, then publish. recordings.json takes the markers and times under the new id, and only then are
     * the raw stream and its sidecar deleted, so a failure at any step leaves the take for the next recovery.
     */
    private fun save(files: TakeFiles, name: String, recordedAt: Long, markers: List<Long>): Saved {
        val uri = RecordingStore.createPending(this, name) ?: return Saved.Failed("MediaStore refused a new file")
        var bytes = 0L
        val result = try {
            contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                M4aFiles.muxAdts(files.audio, pfd.fileDescriptor).also { bytes = pfd.statSize }
            }
        } catch (t: Throwable) {
            RecordingStore.discard(this, uri)
            return Saved.Failed("mux: $t")
        }
        if (result == null) {
            RecordingStore.discard(this, uri)
            return Saved.Failed("MediaStore gave no descriptor")
        }
        if (result.frames == 0L) {
            RecordingStore.discard(this, uri)
            return Saved.Empty
        }
        if (!RecordingStore.publish(this, uri)) {
            RecordingStore.discard(this, uri)
            return Saved.Failed("publish refused")
        }
        val row = RecordingStore.row(this, uri)
        val id = row?.id ?: android.content.ContentUris.parseId(uri)
        RecordingMetaStore.put(this, id, RecordingMetaStore.Meta(recordedAt, result.durationMs, markers))
        if (row != null && !row.isRecording) {
            Diagnostics.add("recorder", "MediaStore has not marked $id IS_RECORDING (relative path ${RecordingStore.RELATIVE_PATH})")
        }
        files.delete()
        return Saved.Done(id, RecordingNames.stem(row?.displayName ?: RecordingNames.displayName(name)), result.durationMs, bytes)
    }

    /** T15-39 / E18: every take a dead process left on disk becomes a normal recording, name and markers kept. */
    private fun recoverLeftovers() {
        val leftovers = TakeFiles.leftovers(TakeFiles.dir(this))
        if (leftovers.isEmpty()) return
        Diagnostics.add("recorder", "recovery: ${leftovers.size} take(s) left by a process that died")
        for (files in leftovers) {
            val side = files.readSidecar()
            val name = side?.name ?: RecordingNames.next(RecordingStore.namesInRecordings(this))
            when (val saved = save(files, name, side?.recordedAtMs ?: files.started, side?.markers.orEmpty())) {
                is Saved.Done -> {
                    Diagnostics.add("recorder", "recovered ${saved.name} ms=${saved.durationMs}")
                    pushSaved(saved.id, saved.name)
                }
                Saved.Empty -> {
                    Diagnostics.add("recorder", "recovery: ${files.audio.name} held no whole frame; removed")
                    files.delete()
                }
                is Saved.Failed -> Diagnostics.add("recorder", "recovery of ${files.audio.name} failed: ${saved.why}; kept")
            }
        }
    }

    // ---- the ongoing notification --------------------------------------------------------------------------

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Voice Recorder", NotificationManager.IMPORTANCE_LOW))
        val t = take
        val paused = phase == RecorderState.PAUSED
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recorder_glyph)
            .setContentTitle(if (paused) "Paused" else "Recording")
            .setContentText(t?.name ?: getString(R.string.recorder_name))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_recorder_glyph), "Stop", stop).build())
        if (t != null && !paused) {
            val elapsed = synchronized(t.clock) { t.clock.elapsedMs }
            builder.setUsesChronometer(true).setShowWhen(true).setWhen(System.currentTimeMillis() - elapsed)
        }
        return builder.build()
    }

    private fun updateNotification() {
        if (take == null) return
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    }

    // ---- to the page --------------------------------------------------------------------------------------

    private fun setPhase(p: Int) {
        phase = p
        pushState()
    }

    private fun snapshot(): State {
        val t = take
        return if (t == null) {
            State(phase, "", 0L, "", LongArray(0))
        } else synchronized(t.clock) {
            State(phase, t.name, t.clock.elapsedMs, t.clock.paused?.word.orEmpty(), t.clock.markers.toLongArray())
        }
    }

    private class State(val phase: Int, val name: String, val elapsedMs: Long, val paused: String, val markers: LongArray)

    private fun pushState() {
        val s = snapshot()
        broadcast("onState") { it.onState(s.phase, s.name, s.elapsedMs, s.paused, s.markers) }
    }

    private fun pushLevel(level: Float, elapsedMs: Long) = broadcast("onLevel") { it.onLevel(level, elapsedMs) }

    private fun pushSaved(id: Long, name: String) {
        synchronized(savedUntold) {
            if (callbacks.registeredCallbackCount == 0) {
                savedUntold += id to name
                return
            }
        }
        broadcast("onSaved") { it.onSaved(id, name) }
    }

    private fun notice(text: String) = broadcast("onNotice") { it.onNotice(text) }

    private fun broadcast(what: String, body: (IRecorderCallback) -> Unit) {
        if (dispatcher.isShutdown) return
        dispatcher.execute {
            val n = callbacks.beginBroadcast()
            try {
                for (i in 0 until n) {
                    try {
                        body(callbacks.getBroadcastItem(i))
                    } catch (e: RemoteException) {
                        Diagnostics.add("recorder", "$what: the page is gone")
                    }
                }
            } finally {
                callbacks.finishBroadcast()
            }
        }
    }

    // ---- the contract ------------------------------------------------------------------------------------

    private val binder = object : IRecorder.Stub() {
        override fun register(cb: IRecorderCallback?) {
            cb ?: return
            callbacks.register(cb)
            val s = snapshot()
            val untold = synchronized(savedUntold) { savedUntold.toList().also { savedUntold.clear() } }
            dispatcher.execute {
                runCatching { cb.onState(s.phase, s.name, s.elapsedMs, s.paused, s.markers) }
                for ((id, name) in untold) runCatching { cb.onSaved(id, name) }
            }
        }

        override fun unregister(cb: IRecorderCallback?) {
            cb ?: return
            callbacks.unregister(cb)
        }

        override fun stop() = worker.execute { stopTake("the page") }

        override fun pause() = worker.execute { pauseTake(TakeClock.Pause.USER) }

        override fun resume() = worker.execute { resumeTake() }

        override fun flag() = worker.execute { this@RecorderService.flag() }
    }

    companion object {
        const val ACTION_START = "app.tileshell.recorder.action.START"
        const val ACTION_STOP = "app.tileshell.recorder.action.STOP"
        private const val CHANNEL_ID = "recorder_take"
        private const val NOTIFICATION_ID = 1507
        private const val SPEECH_WAIT_MS = 5_000L

        /** A take starts here: a STARTED service outlives the page, and startForeground needs the visible page. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecorderService::class.java).setAction(ACTION_START))
        }
    }
}
