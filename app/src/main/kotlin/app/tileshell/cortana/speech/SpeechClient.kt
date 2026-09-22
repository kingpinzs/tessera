package app.tileshell.cortana.speech

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
import java.util.UUID

/** Error codes [ISpeechCallback.onError] reports. */
object SpeechError {
    const val NO_MICROPHONE_PERMISSION = 1
    const val MODEL_MISSING = 2
    const val MODEL_CORRUPT = 3
    const val ESPEAK_DATA_BAD = 4
    const val AUDIO_UNAVAILABLE = 5
    const val INTERNAL = 6

    /**
     * Whether the sentence describing [code] can be SAID, or only shown.
     *
     * [MODEL_MISSING], [MODEL_CORRUPT] and [ESPEAK_DATA_BAD] all mean the TTS engine could not be
     * built, so speaking the notice fails for the same reason and arrives back at the error handler,
     * which speaks it again. On the phone (2026-09-22, espeak data refused) one tap produced over a
     * thousand speak-error-speak round trips in 50 ms and filled the diagnostics ring, which hid the
     * fault it was reporting. A notice about speech being broken is shown; it is never spoken.
     *
     * The microphone faults stay speakable: they say nothing about the voice, and when the voice does
     * turn out to be broken too the reply is one extra hop that ends here rather than a loop.
     */
    fun isSpeakable(code: Int): Boolean = when (code) {
        MODEL_MISSING, MODEL_CORRUPT, ESPEAK_DATA_BAD -> false
        else -> true
    }
}

/** One bundled voice, as Cortana's Settings page lists it (Q3: several voices, pick one). */
data class Voice(val id: Int, val name: String, val description: String)

/** What the session hears back from the speech process. */
sealed interface SpeechEvent {
    data object Listening : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val open: String, val grammar: String, val audioMs: Int) : SpeechEvent
    data class Level(val level: Float) : SpeechEvent
    data class SpeakingLevel(val utteranceId: String, val level: Float) : SpeechEvent
    data class SpeakingDone(val utteranceId: String, val cancelled: Boolean) : SpeechEvent
    data class Error(val code: Int, val detail: String) : SpeechEvent
    /** The process died and the session has to tell the user it is reloading (E12). */
    data object ProcessGone : SpeechEvent
}

/**
 * The launcher process's end of [ISpeech]. Everything that talks to the engines goes through here, so
 * the process boundary and its death are handled in exactly one place.
 *
 * Binding is what loads the models; unbinding starts the 5-minute idle release inside the service. The
 * session binds when it opens and unbinds when it closes, so an idle phone holds no model in RAM.
 */
object SpeechClient {

    enum class Connection { UNBOUND, BINDING, BOUND }

    private val connectionState = MutableStateFlow(Connection.UNBOUND)
    val connection: StateFlow<Connection> = connectionState.asStateFlow()

    private val eventFlow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SpeechEvent> = eventFlow.asSharedFlow()

    private var service: ISpeech? = null
    private var appContext: Context? = null
    private var bindCount = 0

    private val callback = object : ISpeechCallback.Stub() {
        override fun onListening() = emit(SpeechEvent.Listening)
        override fun onPartial(text: String) = emit(SpeechEvent.Partial(text))
        override fun onFinal(open: String, grammar: String, audioMs: Int) {
            Diagnostics.add("speech", "final open=\"$open\" grammar=\"$grammar\" audioMs=$audioMs")
            emit(SpeechEvent.Final(open, grammar, audioMs))
        }
        override fun onLevel(level: Float) = emit(SpeechEvent.Level(level))
        override fun onSpeakingLevel(utteranceId: String, level: Float) = emit(SpeechEvent.SpeakingLevel(utteranceId, level))
        override fun onSpeakingDone(utteranceId: String, cancelled: Boolean) {
            Diagnostics.add("speech", "speaking done $utteranceId cancelled=$cancelled")
            emit(SpeechEvent.SpeakingDone(utteranceId, cancelled))
        }
        override fun onError(code: Int, detail: String) {
            Diagnostics.add("speech", "error code=$code $detail")
            emit(SpeechEvent.Error(code, detail))
        }
    }

    private val connection0 = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val speech = ISpeech.Stub.asInterface(binder)
            service = speech
            connectionState.value = Connection.BOUND
            runCatching { speech.register(callback) }
                .onFailure { Diagnostics.add("speech", "register failed: $it") }
            Diagnostics.add("speech", "bound to :speech")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // The process died (E12 kills it, or the low-memory killer does). Android re-binds on its own
            // because the binding is still alive, so the session says it is reloading and the next request
            // works. Nothing here re-creates the session.
            service = null
            connectionState.value = Connection.BINDING
            Diagnostics.add("speech", ":speech process gone; awaiting re-bind")
            emit(SpeechEvent.ProcessGone)
        }
    }

    @Synchronized
    fun bind(context: Context) {
        val app = context.applicationContext
        appContext = app
        bindCount++
        if (connectionState.value != Connection.UNBOUND) return
        connectionState.value = Connection.BINDING
        val intent = Intent(app, SpeechService::class.java)
        val ok = app.bindService(intent, connection0, Context.BIND_AUTO_CREATE)
        Diagnostics.add("speech", "bind requested -> $ok")
        if (!ok) {
            connectionState.value = Connection.UNBOUND
            emit(SpeechEvent.Error(SpeechError.INTERNAL, "bindService refused"))
        }
    }

    @Synchronized
    fun unbind(context: Context) {
        if (bindCount > 0) bindCount--
        if (bindCount > 0 || connectionState.value == Connection.UNBOUND) return
        runCatching { service?.unregister(callback) }
        runCatching { context.applicationContext.unbindService(connection0) }
        service = null
        connectionState.value = Connection.UNBOUND
        Diagnostics.add("speech", "unbound from :speech")
    }

    val bound: Boolean get() = service != null

    fun preload() = call("preload") { it.preload() }

    /** @param hotwords one boosted phrase per line for the grammar pass; empty runs the open pass alone */
    fun listen(hotwords: String) = call("startListening") { it.startListening(hotwords) }

    fun stopListening() = call("stopListening") { it.stopListening() }

    /** @return the utterance id the SpeakingDone event will carry */
    fun speak(text: String, speakerId: Int): String {
        val id = UUID.randomUUID().toString()
        Diagnostics.add("speech", "speak[$id] voice=$speakerId text=\"$text\"")
        call("speak") { it.speak(id, text, speakerId) }
        return id
    }

    fun stopSpeaking() = call("stopSpeaking") { it.stopSpeaking() }

    fun voices(): List<Voice> {
        val raw = service?.let { runCatching { it.voices() }.getOrNull() } ?: return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 3) null else parts[0].toIntOrNull()?.let { Voice(it, parts[1], parts[2]) }
        }.toList()
    }

    fun status(): String = service?.let { runCatching { it.status() }.getOrNull() } ?: "speech: not bound"

    private inline fun call(what: String, body: (ISpeech) -> Unit) {
        val speech = service
        if (speech == null) {
            Diagnostics.add("speech", "$what dropped: not bound")
            return
        }
        runCatching { body(speech) }.onFailure {
            Diagnostics.add("speech", "$what failed: $it")
            emit(SpeechEvent.Error(SpeechError.INTERNAL, "$what: $it"))
        }
    }

    private fun emit(event: SpeechEvent) {
        eventFlow.tryEmit(event)
    }
}
