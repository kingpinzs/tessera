package app.tileshell.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Voice Recorder page's end of [IRecorder] (the launcher process). The page binds while it shows — which
 * is also what creates the `:recorder` process that recovers a dead take (T15-39) and keeps its diagnostics
 * dump answering (T15-17) — and reads the take as state.
 */
object RecorderClient {

    data class Take(
        val phase: Int = RecorderState.IDLE,
        val name: String = "",
        val elapsedMs: Long = 0L,
        val paused: String = "",
        val markers: List<Long> = emptyList(),
        val level: Float = 0f,
    ) {
        val active: Boolean get() = phase == RecorderState.RECORDING || phase == RecorderState.PAUSED || phase == RecorderState.STARTING
    }

    private val takeState = MutableStateFlow(Take())
    val take: StateFlow<Take> = takeState.asStateFlow()

    private val noticeFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = noticeFlow.asSharedFlow()

    private val savedFlow = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    val saved: SharedFlow<Pair<Long, String>> = savedFlow.asSharedFlow()

    @Volatile private var service: IRecorder? = null
    private var bindCount = 0

    private val callback = object : IRecorderCallback.Stub() {
        override fun onState(phase: Int, name: String?, elapsedMs: Long, paused: String?, markers: LongArray?) {
            takeState.value = takeState.value.copy(
                phase = phase,
                name = name.orEmpty(),
                elapsedMs = elapsedMs,
                paused = paused.orEmpty(),
                markers = markers?.toList().orEmpty(),
                level = if (phase == RecorderState.RECORDING) takeState.value.level else 0f,
            )
        }

        override fun onLevel(level: Float, elapsedMs: Long) {
            takeState.value = takeState.value.copy(level = level, elapsedMs = elapsedMs)
        }

        override fun onNotice(text: String?) {
            text?.let { noticeFlow.tryEmit(it) }
        }

        override fun onSaved(mediaId: Long, name: String?) {
            savedFlow.tryEmit(mediaId to name.orEmpty())
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = IRecorder.Stub.asInterface(binder)
            runCatching { s.register(callback) }.onFailure { Diagnostics.add("recorder", "register with :recorder failed: $it") }
            service = s
            Diagnostics.add("recorder", "bound to :recorder")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // The recorder process died (E18 kills it). The binding is still alive, so Android re-creates it,
            // and that new process's onCreate recovers the take on disk.
            service = null
            takeState.value = Take()
            Diagnostics.add("recorder", ":recorder process gone; awaiting re-bind")
        }
    }

    @Synchronized
    fun bind(context: Context) {
        bindCount++
        if (bindCount > 1) return
        val ok = context.applicationContext.bindService(
            Intent(context, RecorderService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        Diagnostics.add("recorder", "bind :recorder -> $ok")
    }

    @Synchronized
    fun unbind(context: Context) {
        if (bindCount == 0) return
        bindCount--
        if (bindCount > 0) return
        runCatching { service?.unregister(callback) }
        runCatching { context.applicationContext.unbindService(connection) }
        service = null
        Diagnostics.add("recorder", "unbound from :recorder")
    }

    /** Start a take (the record button). The service does the rest and reports it here. */
    fun start(context: Context) {
        takeState.value = takeState.value.copy(phase = RecorderState.STARTING)
        RecorderService.start(context)
    }

    fun stop() = call("stop") { it.stop() }
    fun pause() = call("pause") { it.pause() }
    fun resume() = call("resume") { it.resume() }
    fun flag() = call("flag") { it.flag() }

    private fun call(what: String, body: (IRecorder) -> Unit) {
        val s = service
        if (s == null) {
            Diagnostics.add("recorder", "$what dropped: not bound to :recorder")
            return
        }
        runCatching { body(s) }.onFailure { Diagnostics.add("recorder", "$what failed: $it") }
    }
}
