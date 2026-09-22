package app.tileshell.cortana

import app.tileshell.cortana.reminders.Recurrence

/**
 * A Cortana response card (R6 §3.4). One shape for every card, because W10M's were one shape: the small
 * persona at the top, an accent title, a caption, the request's own fields, and buttons.
 *
 * The measured cards are the text read-back (R6 §3.4.1, wording MEDIUM / layout LOW, H5) and the
 * reminder confirm card (R6 §3.4.2, MEDIUM). Everything else here is patterned on those two and carries
 * its own NEEDS-HUMAN row: the call confirmation (H6), the calendar cards (H17), the delete cards (H18),
 * "Unlock to continue" (H12), the role notice (H30) and the place / person cards (H22, H29).
 */
enum class CardKind {
    /** A spoken answer with nothing to confirm: time, date, weather, what's on my calendar. */
    ANSWER,
    TEXT_READBACK,
    CALL_CONFIRM,
    REMINDER_CONFIRM,
    /** R7 §3.8.1: "I'll remind you." after the reminder is stored. */
    REMINDER_SAVED,
    CALENDAR_CONFIRM,
    DELETE_CONFIRM,
    UNLOCK,
    ROLE_NOTICE,
    NOT_UNDERSTOOD,
}

enum class CardAction { CONFIRM, CANCEL, ADD_MORE, TRY_AGAIN, UNLOCK, SET_DEFAULT_ASSISTANT, PICK_PHOTO }

data class CardButton(val label: String, val action: CardAction, val enabled: Boolean = true)

/** R6 §3.4.1's outlined contact chip: a round avatar, the name, and a number label such as "mobile". */
data class ContactChip(val name: String, val numberLabel: String?, val lookupKey: String?)

/** One outlined field on a card (R6 §3.4.2: reminder text, time, day — or place / contact, H22 / H29). */
data class CardField(val tag: String, val value: String, val placeholder: String = "")

data class Card(
    val kind: CardKind,
    /** The accent title, spoken as well as shown (R6 §3.4.1). */
    val title: String,
    /** The grey caption above the body: "Message", "Call", "Reminder". */
    val caption: String? = null,
    val contact: ContactChip? = null,
    /** R6 §3.4.1's outlined "Enter your message." field; null when the card has no message. */
    val message: String? = null,
    val fields: List<CardField> = emptyList(),
    /** Shown only on a timed reminder card: a place or person reminder fires once (E13, E14). */
    val recurrence: Recurrence? = null,
    /** R6 §3.4.2's "Add a photo" row with its camera glyph. */
    val photoRow: Boolean = false,
    val photoUri: String? = null,
    /** R7 §3.8.1's saved-reminder row: the lightbulb, the reminder text and its subline. */
    val savedText: String? = null,
    val savedSubline: String? = null,
    /** R6 §3.4.2's "you can say Yes, No, or Cancel". */
    val callout: String? = null,
    val buttons: List<CardButton> = emptyList(),
    /** An ANSWER card's lines (the calendar list, the weather reading). */
    val body: List<String> = emptyList(),
)
