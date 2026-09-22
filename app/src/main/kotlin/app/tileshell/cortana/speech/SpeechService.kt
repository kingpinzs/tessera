package app.tileshell.cortana.speech

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteCallbackList
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import app.tileshell.diag.Diagnostics
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When the engines may be released, kept pure so the host tests it.
 *
 * The models are the whole reason this process exists, so they go as soon as nobody is using them — but
 * not so fast that closing and reopening the session reloads them. Five minutes is the phase's number.
 */
class IdleReleaseTimer(private val idleMillis: Long = IDLE_MILLIS) {

    companion object {
        const val IDLE_MILLIS = 5L * 60L * 1000L
    }

    /** When the last client unbound, on a monotonic clock, or null while a client is bound. */
    private var unboundAt: Long? = null

    @Synchronized
    fun bound() {
        unboundAt = null
    }

    @Synchronized
    fun unbound(now: Long) {
        unboundAt = now
    }

    @Synchronized
    fun idle(): Boolean = unboundAt != null

    @Synchronized
    fun shouldRelease(now: Long): Boolean {
        val since = unboundAt ?: return false
        return now - since >= idleMillis
    }

    /** Milliseconds until the release, or null while a client is bound. */
    @Synchronized
    fun millisUntilRelease(now: Long): Long? {
        val since = unboundAt ?: return null
        return (idleMillis - (now - since)).coerceAtLeast(0L)
    }
}

/**
 * The `app.tileshell:speech` process: the only process in the app that ever loads a speech model.
 *
 * Both engines load on the first bind, off the main thread, and are released five minutes after the last
 * client unbinds. Engine work runs on two single-thread workers (one per engine), so a thirty-second
 * utterance never blocks a `speak`, and `stopListening` / `stopSpeaking` never queue behind the work they
 * are cancelling — they flip a generation counter from the Binder thread instead.
 *
 * Nothing here crashes the process on a bad model: a failure is remembered, reported as the right
 * [SpeechError], and [ISpeech.status] keeps answering.
 */
class SpeechService : Service() {

    private lateinit var asr: SherpaAsr
    private lateinit var tts: SherpaTts

    private lateinit var asrWorker: ExecutorService
    private lateinit var ttsWorker: ExecutorService
    private lateinit var idleScheduler: ScheduledExecutorService

    private val idle = IdleReleaseTimer()
    private var idleTask: ScheduledFuture<*>? = null

    private val asrLoadRequested = AtomicBoolean(false)
    private val ttsLoadRequested = AtomicBoolean(false)

    /** The last load failure per engine, as (SpeechError code, detail), replayed to whoever asks next. */
    @Volatile private var asrFailure: Pair<Int, String>? = null
    @Volatile private var ttsFailure: Pair<Int, String>? = null

    /**
     * Every registered client (phase 05: Cortana in the launcher process and the keyboard in `:ime`).
     * A client that dies is dropped by the list itself, and [onCallbackDied] ends its capture.
     */
    private val clients = object : RemoteCallbackList<ISpeechCallback>() {
        override fun onCallbackDied(callback: ISpeechCallback) {
            Diagnostics.add("speech", "a client died")
            releaseMicIf(callback.asBinder(), "owner died")
            if (speakOwner?.asBinder() == callback.asBinder()) speakOwner = null
        }
    }

    /** One microphone: who it is listening for, which capture generation that is, and their pid. */
    private val micLock = Any()
    @Volatile private var micOwner: ISpeechCallback? = null
    @Volatile private var micGeneration = -1
    @Volatile private var micOwnerPid = -1

    /** Who the voice is speaking for; TTS progress and TTS errors go to them alone. */
    @Volatile private var speakOwner: ISpeechCallback? = null

    @Volatile private var asrBytes = -1L
    @Volatile private var ttsBytes = -1L

    /** The most recent thing that went wrong, for `last_error` in [ISpeech.status]. */
    @Volatile private var lastError: String? = null

    override fun onCreate() {
        super.onCreate()
        // Nothing is loaded here on purpose: the process may be started for a bind that never arrives.
        asr = SherpaAsr(this)
        tts = SherpaTts(this)
        asrWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "speech-asr") }
        ttsWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "speech-tts") }
        idleScheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "speech-idle") }
        SpeechBreadcrumb.attach(this)
        // A native abort takes this process's diagnostics ring with it, so the first thing a new process
        // says is what the last one was doing when it stopped.
        SpeechBreadcrumb.previous()?.let {
            lastError = "the previous speech process stopped during: $it"
            Diagnostics.add("speech", "PREVIOUS PROCESS DIED DURING: $it")
        }
        Diagnostics.add("speech", "service created pid=${Process.myPid()} (no model loaded yet)")
    }

    override fun onBind(intent: Intent?): IBinder {
        Diagnostics.add("speech", "onBind")
        clientPresent()
        return binder
    }

    override fun onRebind(intent: Intent?) {
        Diagnostics.add("speech", "onRebind")
        clientPresent()
    }

    /** True so [onRebind] is delivered: a client that comes back inside the idle window keeps the models. */
    override fun onUnbind(intent: Intent?): Boolean {
        clientGone()
        return true
    }

    override fun onDestroy() {
        Diagnostics.add("speech", "service destroyed; releasing engines")
        clients.kill()
        idleTask?.cancel(false)
        idleScheduler.shutdownNow()
        asr.interrupt()
        tts.interrupt()
        asrWorker.execute { asr.release() }
        ttsWorker.execute { tts.release() }
        asrWorker.shutdown()
        ttsWorker.shutdown()
        super.onDestroy()
    }

    // ---- bind bookkeeping -------------------------------------------------------------------------

    @Synchronized
    private fun clientPresent() {
        idle.bound()
        idleTask?.cancel(false)
        idleTask = null
        loadAsr()
        loadTts()
    }

    @Synchronized
    private fun clientGone() {
        val now = SystemClock.elapsedRealtime()
        idle.unbound(now)
        idleTask?.cancel(false)
        idleTask = runCatching {
            idleScheduler.schedule(
                { releaseIfStillIdle() },
                IdleReleaseTimer.IDLE_MILLIS,
                TimeUnit.MILLISECONDS,
            )
        }.getOrNull()
        Diagnostics.add("speech", "last client unbound; releasing engines in ${IdleReleaseTimer.IDLE_MILLIS / 1000} s")
    }

    private fun releaseIfStillIdle() {
        if (!idle.shouldRelease(SystemClock.elapsedRealtime())) {
            Diagnostics.add("speech", "idle release skipped: a client came back")
            return
        }
        Diagnostics.add("speech", "idle release: 5 minutes unbound, dropping both engines")
        asrWorker.execute {
            asr.release()
            asrLoadRequested.set(false)
            asrFailure = null
        }
        ttsWorker.execute {
            tts.release()
            ttsLoadRequested.set(false)
            ttsFailure = null
        }
    }

    /**
     * `adb shell dumpsys activity service app.tileshell/.cortana.speech.SpeechService`.
     *
     * This process has its own [Diagnostics] ring — the launcher's dump cannot see it — so without this
     * the only evidence of a model load would be whatever crossed the Binder. QA reads it, and so does
     * the checklist's "Speech engine" row through [status].
     */
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        writer.println("tileshell speech process pid=${Process.myPid()}")
        writer.println("breadcrumb: ${SpeechBreadcrumb.read()}")
        writer.println("--- status ---")
        writer.println(binder.status())
        writer.println("--- diagnostics ---")
        Diagnostics.dump(writer)
    }

    // ---- loading ---------------------------------------------------------------------------------

    private fun loadAsr() {
        if (!asrLoadRequested.compareAndSet(false, true)) return
        asrWorker.execute {
            try {
                asr.load()
                asrFailure = null
            } catch (e: SpeechModelException) {
                asrFailure = e.code to (e.message ?: "ASR load failed")
                report(e.code, e.message ?: "ASR load failed")
            } catch (t: Throwable) {
                Diagnostics.add("speech", "asr: load failed: $t")
                asrFailure = SpeechError.INTERNAL to "ASR load failed: $t"
                report(SpeechError.INTERNAL, "ASR load failed: $t")
            }
        }
    }

    private fun loadTts() {
        if (!ttsLoadRequested.compareAndSet(false, true)) return
        ttsWorker.execute {
            try {
                tts.load()
                ttsFailure = null
            } catch (e: SpeechModelException) {
                ttsFailure = e.code to (e.message ?: "TTS load failed")
                report(e.code, e.message ?: "TTS load failed")
            } catch (t: Throwable) {
                Diagnostics.add("speech", "tts: load failed: $t")
                ttsFailure = SpeechError.INTERNAL to "TTS load failed: $t"
                report(SpeechError.INTERNAL, "TTS load failed: $t")
            }
        }
    }

    // ---- callback dispatch -----------------------------------------------------------------------

    /**
     * Every callback goes through here. A client that died between two events is dropped and the service
     * keeps serving: its process outliving ours is the normal case, not an error.
     */
    private fun dispatch(target: ISpeechCallback?, what: String, body: (ISpeechCallback) -> Unit) {
        target ?: return
        try {
            body(target)
        } catch (e: DeadObjectException) {
            Diagnostics.add("speech", "$what: client is gone; callback dropped")
            releaseMicIf(target.asBinder(), "owner gone during $what")
        } catch (e: RemoteException) {
            Diagnostics.add("speech", "$what: remote exception $e")
        } catch (t: Throwable) {
            Diagnostics.add("speech", "$what: dispatch threw $t")
        }
    }

    /** An event with no owner (a load failure on bind) goes to every registered client. */
    private fun broadcast(what: String, body: (ISpeechCallback) -> Unit) {
        val n = clients.beginBroadcast()
        try {
            for (i in 0 until n) dispatch(clients.getBroadcastItem(i), what, body)
        } finally {
            clients.finishBroadcast()
        }
    }

    /**
     * The single funnel for every error, so `last_error` in [ISpeech.status] is recorded even when no
     * client was registered to hear it — which is exactly the case for a failure during the first load.
     * [to] is the client the error belongs to; null means nobody asked, so everybody hears it.
     */
    private fun report(code: Int, detail: String, to: ISpeechCallback? = null) {
        lastError = "code $code: $detail"
        if (to != null) dispatch(to, "onError") { it.onError(code, detail) } else broadcast("onError") { it.onError(code, detail) }
    }

    /** ASR events for the capture of [owner]; a capture only ever reports to the client it listens for. */
    private fun asrEventsFor(owner: ISpeechCallback) = object : AsrEvents {
        override fun onListening() = dispatch(owner, "onListening") { it.onListening() }
        override fun onPartial(text: String) = dispatch(owner, "onPartial") { it.onPartial(text) }
        override fun onLevel(level: Float) = dispatch(owner, "onLevel") { it.onLevel(level) }
        override fun onFinal(open: String, grammar: String, audioMs: Int) =
            dispatch(owner, "onFinal") { it.onFinal(open, grammar, audioMs) }
        override fun onError(code: Int, detail: String) = report(code, detail, owner)
    }

    private fun ttsEventsFor(owner: ISpeechCallback?) = object : TtsEvents {
        override fun onSpeakingLevel(utteranceId: String, level: Float) =
            dispatch(owner, "onSpeakingLevel") { it.onSpeakingLevel(utteranceId, level) }
        override fun onSpeakingDone(utteranceId: String, cancelled: Boolean) =
            dispatch(owner, "onSpeakingDone") { it.onSpeakingDone(utteranceId, cancelled) }
        override fun onError(code: Int, detail: String) = report(code, detail, owner)
    }

    /** Free the microphone if [binder] holds it, ending its capture. */
    private fun releaseMicIf(binder: IBinder, why: String) {
        synchronized(micLock) {
            if (micOwner?.asBinder() != binder) return
            micOwner = null
            micGeneration = -1
            micOwnerPid = -1
        }
        asr.interrupt()
        Diagnostics.add("speech", "microphone released: $why")
    }

    // ---- the contract ----------------------------------------------------------------------------

    private val binder = object : ISpeech.Stub() {

        override fun register(cb: ISpeechCallback?) {
            cb ?: return
            clients.register(cb)
            Diagnostics.add("speech", "client registered pid=${Binder.getCallingPid()} (${clients.registeredCallbackCount} registered)")
        }

        override fun unregister(cb: ISpeechCallback?) {
            cb ?: return
            clients.unregister(cb)
            releaseMicIf(cb.asBinder(), "owner unregistered")
            if (speakOwner?.asBinder() == cb.asBinder()) speakOwner = null
            Diagnostics.add("speech", "client unregistered pid=${Binder.getCallingPid()} (${clients.registeredCallbackCount} registered)")
        }

        override fun startListening(owner: ISpeechCallback?, hotwords: String?) {
            owner ?: return
            val pid = Binder.getCallingPid()
            // One engine, one microphone (phase 05 edge case): a second client is refused with a notice,
            // never allowed to take the microphone from the one using it. The same client starting again
            // replaces its own capture, as it always has.
            val generation = synchronized(micLock) {
                val holder = micOwner
                if (holder != null && holder.asBinder() != owner.asBinder()) {
                    Diagnostics.add("speech", "startListening from pid=$pid refused: the microphone is listening for pid=$micOwnerPid")
                    null
                } else {
                    // Bumped here, on the Binder thread: a capture already running sees it and unwinds, so
                    // there is never a second AudioRecord.
                    val g = asr.interrupt()
                    micOwner = owner
                    micGeneration = g
                    micOwnerPid = pid
                    g
                }
            }
            if (generation == null) {
                report(SpeechError.MICROPHONE_BUSY, "the microphone is in use by another part of the shell", owner)
                return
            }
            Diagnostics.add("speech", "listening for pid=$pid")
            loadAsr()
            val phrases = hotwords.orEmpty()
            asrWorker.execute {
                val failure = asrFailure
                if (failure != null) {
                    report(failure.first, failure.second, owner)
                } else {
                    asr.capture(phrases, generation, asrEventsFor(owner))
                }
                // The capture is over (endpoint, stop, or failure): the microphone is free again, unless a
                // newer capture by the same owner has already claimed it.
                synchronized(micLock) {
                    if (micGeneration == generation) {
                        micOwner = null
                        micGeneration = -1
                        micOwnerPid = -1
                    }
                }
            }
        }

        override fun stopListening(owner: ISpeechCallback?) {
            val holder = micOwner
            if (owner == null || holder == null || holder.asBinder() != owner.asBinder()) {
                Diagnostics.add("speech", "stopListening from a non-owner ignored (pid=${Binder.getCallingPid()})")
                return
            }
            asr.interrupt()
            Diagnostics.add("speech", "stopListening requested")
        }

        override fun speak(owner: ISpeechCallback?, utteranceId: String?, text: String?, speakerId: Int) {
            val id = utteranceId.orEmpty()
            val words = text.orEmpty()
            speakOwner = owner
            val events = ttsEventsFor(owner)
            val generation = tts.interrupt()
            loadTts()
            ttsWorker.execute {
                val failure = ttsFailure
                if (failure != null) {
                    report(failure.first, failure.second, owner)
                    events.onSpeakingDone(id, true)
                    return@execute
                }
                tts.speak(id, words, speakerId, generation, events)
            }
        }

        override fun stopSpeaking() {
            if (!tts.speaking) {
                Diagnostics.add("speech", "stopSpeaking: nothing playing")
            }
            tts.interrupt()
        }

        override fun preload() {
            Diagnostics.add("speech", "preload requested")
            loadAsr()
            loadTts()
        }

        /**
         * Answerable with no engine in RAM: Settings lists the voices so a voice can be picked before
         * anything is ever spoken, and the Kokoro speaker table is static. Nothing here constructs the
         * engine. When the engine does load, [SherpaTts.load] checks `numSpeakers()` against the table
         * and logs a mismatch loudly; from then on this returns only ids the model actually has.
         */
        override fun voices(): String {
            val reported = tts.numSpeakers
            val count = if (reported > 0) reported else Voices.EXPECTED_SPEAKERS
            return Voices.lines(count)
        }

        override fun status(): String = buildStatus()
    }

    // ---- status ----------------------------------------------------------------------------------

    /**
     * `key=value` per line, for Cortana's Settings checklist and P4's memory row. Machine-readable, not
     * prose, and it answers whether or not anything ever loaded — a checklist needs it most when the
     * engines failed. Nothing in here can throw: every value has a defined answer for "unknown".
     */
    private fun buildStatus(): String = try {
        // Sizes come from the APK's asset table (AssetManager.openFd().declaredLength), which is the
        // real stored length: the .onnx/.bin assets are noCompress, so stored length == file length.
        // Measured once and cached; the APK cannot change under a running process.
        if (asrBytes < 0) {
            asrBytes = SpeechAssets.ASR_FILES.sumOf { SpeechAssets.bytes(assets, it).coerceAtLeast(0L) }
        }
        if (ttsBytes < 0) {
            ttsBytes = SpeechAssets.TTS_MODEL_FILES.sumOf { SpeechAssets.bytes(assets, it).coerceAtLeast(0L) }
        }
        val now = SystemClock.elapsedRealtime()
        val untilRelease = idle.millisUntilRelease(now)
        val speakers = tts.numSpeakers
        val asrPresent = SpeechAssets.firstMissing(assets, SpeechAssets.ASR_FILES) == null
        val ttsPresent = SpeechAssets.firstMissing(assets, SpeechAssets.TTS_MODEL_FILES) == null

        val lines = mutableListOf<String>()
        // --- the keys Cortana's Settings checklist reads, spelled exactly ---
        lines += "asr_present=$asrPresent"
        lines += "tts_present=$ttsPresent"
        // True only once EspeakData.ensure has returned: it returns only after the zip's sha256 matched
        // AND the extracted directory came out populated.
        lines += "espeak_ok=${tts.espeakDataDir != null}"
        lines += "asr_loaded=${asr.loaded}"
        lines += "tts_loaded=${tts.loaded}"
        lines += "asr_bytes=$asrBytes"
        lines += "tts_bytes=$ttsBytes"
        // Field 2 of /proc/self/statm is resident pages, times the real page size (Os.sysconf, because
        // this device family is not necessarily 4 KiB). -1 only if /proc is unreadable.
        lines += "rss_bytes=${rssBytes()}"
        lines += "idle_release_in_ms=${untilRelease ?: -1L}"
        lines += "last_error=${lastError.orEmpty().replace('\n', ' ')}"

        // --- everything else that is useful in a dump ---
        lines += "pid=${Process.myPid()}"
        lines += "process=app.tileshell:speech"
        lines += "asr_listening=${asr.listening}"
        lines += "mic_owner_pid=$micOwnerPid"
        lines += "clients=${clients.registeredCallbackCount}"
        lines += "asr_error=${asrFailure?.let { "code ${it.first}: ${it.second}" }.orEmpty().replace('\n', ' ')}"
        lines += "asr_bpe_vocab=${asr.bpeVocabSource}"
        lines += "asr_decoding=${SherpaAsr.DECODING_METHOD}"
        lines += "tts_speaking=${tts.speaking}"
        lines += "tts_error=${ttsFailure?.let { "code ${it.first}: ${it.second}" }.orEmpty().replace('\n', ' ')}"
        lines += "tts_sample_rate=${tts.sampleRate}"
        lines += "espeak_dir=${tts.espeakDataDir?.absolutePath.orEmpty()}"
        lines += "espeak_bytes=${SpeechAssets.bytes(assets, SpeechAssets.ESPEAK_ZIP).coerceAtLeast(0L)}"
        lines += "voices_reported=$speakers"
        lines += "voices_expected=${Voices.EXPECTED_SPEAKERS}"
        lines += "idle_release_total_ms=${IdleReleaseTimer.IDLE_MILLIS}"
        lines.joinToString("\n")
    } catch (t: Throwable) {
        // status() is the one call that must never fail: it is what the checklist falls back to.
        Diagnostics.add("speech", "status: build failed: $t")
        "asr_present=false\ntts_present=false\nespeak_ok=false\nasr_loaded=false\ntts_loaded=false\n" +
            "asr_bytes=0\ntts_bytes=0\nrss_bytes=-1\nidle_release_in_ms=-1\nlast_error=status failed: $t"
    }

    /** Resident set size in bytes from /proc/self/statm (field 2 is resident pages), or -1. */
    private fun rssBytes(): Long = try {
        val fields = File("/proc/self/statm").readText().trim().split(" ")
        fields[1].toLong() * pageSize()
    } catch (t: Throwable) {
        Diagnostics.add("speech", "status: /proc/self/statm unreadable: $t")
        -1L
    }

    private fun pageSize(): Long = try {
        Os.sysconf(OsConstants._SC_PAGESIZE).takeIf { it > 0 } ?: FALLBACK_PAGE_SIZE
    } catch (t: Throwable) {
        FALLBACK_PAGE_SIZE
    }

    private companion object {
        const val FALLBACK_PAGE_SIZE = 4096L
    }
}
