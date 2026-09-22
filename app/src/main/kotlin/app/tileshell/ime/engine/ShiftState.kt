package app.tileshell.ime.engine

/**
 * Shift and caps lock (phase 05 Decisions stand-in (4), H22): "one tap on Shift fills its arrow glyph
 * white for the next letter; a second tap whose touch-down comes within
 * `ViewConfiguration.getDoubleTapTimeout()` (300 ms) of the first turns caps lock on … until Shift is
 * tapped again."
 *
 * A state machine fed explicit timestamps, so the 300 ms window is tested on both sides without a
 * clock. [doubleTapMs] is the platform's double-tap timeout, passed in by the IME.
 *
 * The double tap is judged on Shift taps alone: a one-shot armed by auto-capitalisation
 * ([autoCapitalise], from [SpaceRules.startsSentence]) carries no tap time, so one tap after it just
 * lowers the case and two quick taps still lock.
 */
class ShiftState(val doubleTapMs: Long = 300L) {

    enum class Mode { OFF, ONE_SHOT, CAPS_LOCK }

    var mode: Mode = Mode.OFF
        private set

    /** Whether the next letter is uppercase. */
    val shifted: Boolean get() = mode != Mode.OFF

    val locked: Boolean get() = mode == Mode.CAPS_LOCK

    /** Touch-down time of the previous Shift tap, or [NO_TAP] when none counts. */
    private var lastTapMs = NO_TAP

    /** Shift touched down at [downMs]. Returns the resulting mode. */
    fun tapShift(downMs: Long): Mode {
        val quick = lastTapMs != NO_TAP && downMs - lastTapMs in 0..doubleTapMs
        mode = when {
            quick && mode != Mode.CAPS_LOCK -> Mode.CAPS_LOCK
            mode == Mode.OFF -> Mode.ONE_SHOT
            else -> Mode.OFF // a second tap outside the window cancels one-shot; any tap ends caps lock
        }
        lastTapMs = if (mode == Mode.CAPS_LOCK) NO_TAP else downMs
        return mode
    }

    /** A letter is being typed: returns whether it is uppercase, and spends a one-shot shift. */
    fun typeLetter(): Boolean {
        val upper = shifted
        if (mode == Mode.ONE_SHOT) mode = Mode.OFF
        return upper
    }

    /** The caret is at a sentence start: arm a one-shot shift unless caps lock is already on. */
    fun autoCapitalise() {
        if (mode == Mode.OFF) {
            mode = Mode.ONE_SHOT
            lastTapMs = NO_TAP
        }
    }

    /** A new field: nothing carries over, caps lock included (Edge cases: "caps lock then switching fields"). */
    fun reset() {
        mode = Mode.OFF
        lastTapMs = NO_TAP
    }

    companion object {
        private const val NO_TAP = Long.MIN_VALUE
    }
}
