package app.tileshell.recorder;

/** What the recorder process reports to the Voice Recorder page. Every call is one-way. */
oneway interface IRecorderCallback {
    /**
     * The take's state changed.
     *
     * @param phase     one of RecorderState's phases (idle, starting, recording, paused, saving)
     * @param name      the take's name ("Recording (2)"), empty while idle
     * @param elapsedMs take time, pauses left out
     * @param paused    why it is paused ("user", "call", "silenced"), empty while it records
     * @param markers   the take's markers, take time, oldest first
     */
    void onState(int phase, String name, long elapsedMs, String paused, in long[] markers);

    /** Input level 0..1 and the take time, while recording (the level rings, r11/voice-recorder.md 2.7). */
    void onLevel(float level, long elapsedMs);

    /** Something the page has to say: a refusal, the storage floor, a save that failed. */
    void onNotice(String text);

    /** The take is saved as MediaStore row [mediaId]. */
    void onSaved(long mediaId, String name);
}
