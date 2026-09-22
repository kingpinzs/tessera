package app.tileshell.cortana.speech;

/**
 * What the speech process reports back to the Cortana session (phase 03 build tasks 3 and 4).
 * Every call is one-way: the session never blocks on the models.
 */
oneway interface ISpeechCallback {
    /** The engines are loaded and the microphone is open; listening has really started. */
    void onListening();

    /** A partial transcript from the OPEN pass, for the query box while the user is still speaking. */
    void onPartial(String text);

    /**
     * The utterance ended (endpoint, or stopListening).
     *
     * @param open    the open-vocabulary transcript: what was said, whatever it was
     * @param grammar the same audio decoded with the command vocabulary boosted (the grammar pass);
     *                empty when the grammar pass produced nothing
     * @param audioMs how long the captured utterance was, so silence is distinguishable from no match
     */
    void onFinal(String open, String grammar, int audioMs);

    /** Microphone level 0..1, for the listening waveform's bars (H10). */
    void onLevel(float level);

    /** Speech began playing out for [utteranceId]; the speaking persona follows [level] 0..1. */
    void onSpeakingLevel(String utteranceId, float level);

    /** Speech for [utteranceId] finished playing (or was cancelled). */
    void onSpeakingDone(String utteranceId, boolean cancelled);

    /**
     * Something the caller has to show.
     *
     * @param code one of SpeechError's constants
     */
    void onError(int code, String detail);
}
