package app.tileshell.recorder;

import app.tileshell.recorder.IRecorderCallback;

/**
 * The `app.tileshell:recorder` process (phase 15 Decisions "Voice Recorder mechanics", build task 7).
 *
 * The take runs there, in RecorderService, a foreground service of type microphone, so a crash of Start
 * or of the Voice Recorder page cannot end it and the capture's memory stays out of the launcher process.
 * The page binds while it shows and drives the take through this; a take is STARTED with an intent
 * (startForegroundService), because a started service is what outlives the page.
 */
interface IRecorder {
    /** Register [cb] for the take's state; the current state is sent to it at once. */
    void register(IRecorderCallback cb);
    void unregister(IRecorderCallback cb);

    /** Stop the take and save it (the ongoing notification's Stop does the same). */
    void stop();

    /** The user's own Pause (T15-16): the take holds, nothing is written until Resume. */
    void pause();
    void resume();

    /** A marker at the take's time now, pauses left out (T15-16). */
    void flag();
}
