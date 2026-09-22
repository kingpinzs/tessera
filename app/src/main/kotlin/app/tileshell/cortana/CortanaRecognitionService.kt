package app.tileshell.cortana

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.tileshell.cortana.speech.SpeechClient
import app.tileshell.cortana.speech.SpeechError
import app.tileshell.cortana.speech.SpeechEvent
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The offline recogniser behind the assistant (phase 03 build task 3's public face).
 *
 * A `VoiceInteractionService` must name a `RecognitionService`, so this exists whether or not anything
 * else calls it. It is a real one, not a placeholder (Rule 16): it serves the same `app.tileshell:speech`
 * process the session uses, so anything on the phone asking Android for on-device recognition gets
 * Cortana's engine and no network. Phase 05's voice typing binds it as an ordinary client.
 */
class CortanaRecognitionService : RecognitionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listener: Callback? = null
    private var events: Job? = null

    override fun onCreate() {
        super.onCreate()
        Diagnostics.add("recognition", "service created")
    }

    override fun onDestroy() {
        events?.cancel()
        scope.cancel()
        SpeechClient.unbind(this)
        Diagnostics.add("recognition", "service destroyed")
        super.onDestroy()
    }

    override fun onStartListening(recognizerIntent: Intent?, callback: Callback) {
        listener = callback
        SpeechClient.bind(this)
        events?.cancel()
        events = scope.launch {
            SpeechClient.events.collect { event -> deliver(event, callback) }
        }
        // A caller can bias the recogniser exactly as Cortana's own session does, with the same
        // per-stream hotwords: the grammar pass is not private to the session.
        val hotwords = recognizerIntent?.getStringArrayExtra(RecognizerIntent.EXTRA_BIASING_STRINGS)
            ?.joinToString("\n").orEmpty()
        Diagnostics.add("recognition", "startListening hotwords=${hotwords.lineSequence().count()} lines")
        SpeechClient.listen(hotwords)
        callback.readyForSpeech(Bundle())
    }

    override fun onStopListening(callback: Callback) {
        Diagnostics.add("recognition", "stopListening")
        SpeechClient.stopListening()
    }

    override fun onCancel(callback: Callback) {
        Diagnostics.add("recognition", "cancel")
        SpeechClient.stopListening()
        events?.cancel()
        listener = null
        SpeechClient.unbind(this)
    }

    private fun deliver(event: SpeechEvent, callback: Callback) {
        if (listener !== callback) return
        when (event) {
            is SpeechEvent.Listening -> callback.beginningOfSpeech()
            is SpeechEvent.Level -> callback.rmsChanged(event.level * 10f - 2f)
            is SpeechEvent.Partial -> callback.partialResults(resultsBundle(event.text))
            is SpeechEvent.Final -> {
                callback.endOfSpeech()
                // The open pass is what a generic caller asked for; the grammar pass is a bias, not a
                // different answer, so it is offered as the second alternative rather than the first.
                callback.results(resultsBundle(event.open, event.grammar))
                finish(callback)
            }
            is SpeechEvent.Error -> {
                callback.error(
                    when (event.code) {
                        SpeechError.NO_MICROPHONE_PERMISSION -> SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        SpeechError.AUDIO_UNAVAILABLE -> SpeechRecognizer.ERROR_AUDIO
                        SpeechError.MICROPHONE_BUSY -> SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                        SpeechError.MODEL_MISSING, SpeechError.MODEL_CORRUPT, SpeechError.ESPEAK_DATA_BAD ->
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        else -> SpeechRecognizer.ERROR_CLIENT
                    }
                )
                finish(callback)
            }
            is SpeechEvent.ProcessGone -> {
                callback.error(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
                finish(callback)
            }
            is SpeechEvent.SpeakingLevel, is SpeechEvent.SpeakingDone -> Unit
        }
    }

    private fun finish(callback: Callback) {
        if (listener === callback) listener = null
        events?.cancel()
        events = null
        SpeechClient.unbind(this)
    }

    private fun resultsBundle(vararg texts: String) = Bundle().apply {
        putStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION,
            ArrayList(texts.filter { it.isNotBlank() }.distinct()),
        )
    }
}
