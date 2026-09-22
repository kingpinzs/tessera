package app.tileshell.cortana.speech;

import app.tileshell.cortana.speech.ISpeechCallback;

/**
 * The `app.tileshell:speech` process (phase 03 Decisions "Model storage and process").
 *
 * It owns both models: the launcher process never loads one. It loads them on first bind and releases
 * them after 5 minutes with no bound client. If it dies, Start, tiles, the listener, the IME and the
 * dialer are unaffected, and the next request gets the reload notice (E12).
 */
interface ISpeech {
    /**
     * Register the caller's callback. Several clients may be registered at once — Cortana in the
     * launcher process and the keyboard in `:ime` (phase 05 Decisions: the voice typing key is an ADD
     * to this process's clients). Unregistering a client that owns the microphone ends its capture.
     */
    void register(ISpeechCallback callback);
    void unregister(ISpeechCallback callback);

    /**
     * Open the microphone for [owner] and decode until the endpoint (or stopListening). There is one
     * engine and one microphone: while another client's capture is running this one is refused with
     * SpeechError.MICROPHONE_BUSY, never queued behind it and never allowed to take it over. The same
     * owner starting again replaces its own capture.
     *
     * @param hotwords one boosted phrase per line, in the recognizer's token form (the grammar pass);
     *                 an empty string runs the open pass alone
     */
    void startListening(ISpeechCallback owner, String hotwords);

    /** End [owner]'s utterance now and deliver onFinal with what has been decoded. A non-owner's stop does nothing. */
    void stopListening(ISpeechCallback owner);

    /**
     * Speak [text] with the voice [speakerId] and report progress to [owner] under [utteranceId].
     * A new speak cancels the one playing.
     */
    void speak(ISpeechCallback owner, String utteranceId, String text, int speakerId);

    /**
     * Stop [owner]'s speech in flight (onSpeakingDone arrives with cancelled = true). Like stopListening, a
     * client that is not speaking cannot cancel another's speech.
     */
    void stopSpeaking(ISpeechCallback owner);

    /** Force the models in now, so the first request does not wait for them (the session binds on open). */
    void preload();

    /** The bundled voices, in the order Cortana's Settings page lists them: "id|name|description" lines. */
    String voices();

    /**
     * One line per fact, for the diagnostics dump and P4's memory figures:
     * loaded / not loaded, each model's bytes, the process RSS, and when the idle release will run.
     */
    String status();
}
