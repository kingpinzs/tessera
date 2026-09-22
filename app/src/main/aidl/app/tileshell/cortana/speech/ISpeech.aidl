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
    /** Register the caller's callback. One caller at a time; a second registration replaces the first. */
    void register(ISpeechCallback callback);
    void unregister(ISpeechCallback callback);

    /**
     * Open the microphone and decode until the endpoint (or stopListening).
     *
     * @param hotwords one boosted phrase per line, in the recognizer's token form (the grammar pass);
     *                 an empty string runs the open pass alone
     */
    void startListening(String hotwords);

    /** End the utterance now and deliver onFinal with what has been decoded. */
    void stopListening();

    /**
     * Speak [text] with the voice [speakerId] and report progress under [utteranceId].
     * A new speak cancels the one playing.
     */
    void speak(String utteranceId, String text, int speakerId);

    /** Stop any speech in flight (onSpeakingDone arrives with cancelled = true). */
    void stopSpeaking();

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
