package app.tileshell.cortana.action

import app.tileshell.cortana.match.Request
import app.tileshell.cortana.reminders.Reminder

/**
 * What a confirmation on screen will do when it is confirmed (Decisions "Confirmation flow"). Nothing
 * sends, dials, inserts or stores until the pending thing is confirmed, and phase 08 calls the same
 * actions and adds no confirmation of its own.
 */
sealed interface Pending {

    /** R6 §3.4.1: the read-back is waiting for send it / add more / try again / cancel. */
    data class SendText(
        val contact: Contacts.Match,
        val number: Contacts.Number,
        val message: String,
    ) : Pending

    /** "Send a text to <contact>. What do you want to say?" — the next utterance IS the message. */
    data class AwaitMessage(val contact: Contacts.Match, val number: Contacts.Number) : Pending

    /** R6 §3.4.4 (UNMEASURED, H6): reads back and asks before dialling. */
    data class PlaceCall(val contact: Contacts.Match, val number: Contacts.Number) : Pending

    /** R6 §3.4.2's card. [placeName] is carried so the spoken line and the subline agree. */
    data class SaveReminder(val draft: Reminder, val placeName: String?) : Pending

    /** R6 §3.4.3 (LOW, H16): "When would you like to be reminded?" — a time, or "whenever". */
    data class AwaitReminderTime(val draft: Reminder) : Pending

    /** Approximation, H17: "Add this to your calendar?" */
    data class AddEvent(val title: String, val beginMs: Long, val endMs: Long) : Pending

    /** Approximation, H18: "Delete this event?" / "Delete this reminder?" */
    data class DeleteEvent(val eventId: Long, val title: String) : Pending
    data class DeleteReminder(val reminderId: String, val text: String) : Pending

    /**
     * H12: the gated request waits behind "Unlock to continue". Only the card on screen holds a pending
     * request — a new request replaces the card and drops this one, and closing the session drops it too.
     */
    data class Locked(val request: Request) : Pending
}
