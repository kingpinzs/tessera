package app.tileshell.cortana.match

import app.tileshell.cortana.reminders.Recurrence

/**
 * What Cortana understood. The ruled command list (interview Q2, Jeremy: "A") is W10M Cortana's own
 * phone commands; the settings toggles (Wi-Fi / Bluetooth / data / airplane) are explicitly NOT here —
 * they arrive in phase 04 with the privileged helper, and Rule 16 forbids a placeholder for them.
 */
sealed interface Request {

    /** A request the action layer runs. */
    sealed interface Action : Request

    /** An answer to a confirmation that is already on screen. */
    sealed interface Answer : Request

    // ---- the ruled command list ----
    data class OpenApp(val name: String) : Action
    data class CallContact(val name: String) : Action
    data class TextContact(val name: String, val message: String?) : Action
    data class SetAlarm(val hour: Int, val minute: Int) : Action
    data class SetTimer(val seconds: Int) : Action
    data class SetReminder(
        val text: String,
        val timeMs: Long?,
        val recurrence: Recurrence = Recurrence.ONCE,
        /** "remind me … when I get home" */
        val placeName: String? = null,
        /** "remind me … next time I talk to Mom" */
        val personName: String? = null,
    ) : Action
    data class DeleteReminder(val text: String) : Action
    data class AddCalendarEvent(val title: String, val beginMs: Long) : Action
    data class DeleteCalendarEvent(val title: String) : Action
    data object WhatsOnMyCalendar : Action
    data object TimeQuery : Action
    data object DateQuery : Action
    data class PlayMusic(val query: String?) : Action
    data class Directions(val destination: String) : Action
    data object TakePhoto : Action
    data class TakeNote(val text: String?) : Action
    data object Weather : Action
    /** Place source C: "this is home" saves the spot the phone is standing on. */
    data class SavePlaceHere(val name: String) : Action
    /**
     * Phase 15 (interview Q5 A, P6): arithmetic or a unit conversion, answered offline through the Calculator's own
     * engine — never left for a model to work out.
     */
    data class Arithmetic(val expr: CalcRequest) : Action

    // ---- confirmation answers (R6 §3.4.1 / §3.4.2) ----
    /** "send it", "yes", or the card's confirm button. */
    data object Confirm : Answer
    /** "no", "cancel", or the card's cancel button. */
    data object Cancel : Answer
    /** Text read-back only (R6 §3.4.1); never offered on a reminder or calendar card. */
    data object AddMore : Answer
    data object TryAgain : Answer
    /** At "When would you like to be reminded?": "whenever" / "no time" / "any time" (H16). */
    data object Whenever : Answer

    /**
     * Nothing in the list matched. [text] is the open-vocabulary transcript, which phase 08's model will
     * answer; here it reaches the not-understood handler and is logged (E3).
     */
    data class NotUnderstood(val text: String) : Request

    /** The microphone heard nothing. Distinct from [NotUnderstood]: there is no text to pass on. */
    data object Silence : Request
}
