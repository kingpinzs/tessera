package app.tileshell.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** Decisions stand-in (4): one tap = the next letter, a quick second tap = caps lock. */
enum class ShiftMode { OFF, ONE_SHOT, LOCKED }

/** Decisions "Cursor-controller handedness" (R6 §2.5.5, LOW; labels H14, default H16). */
enum class Handedness { RIGHT, LEFT }

/** The press popup: R6 §2.3, an accent rectangle with a larger white glyph above the pressed key. */
data class KeyPopup(val key: Key, val label: String)

/** Decisions stand-in (3): the long-press alternates popup (H19). */
data class AlternatesPopup(val key: Key, val cells: List<String>, val leftward: Boolean, val selected: Int)

/** Decisions stand-in (2): the one-handed options popup above &123 (H20). */
data class OneHandedPopup(val key: Key, val selected: Dock?)

/** One item in the suggestion strip (R6 §2.2). */
data class StripItem(val text: String, val bold: Boolean = false, val kind: Kind = Kind.WORD) {
    enum class Kind { WORD, VERBATIM, ADD, REMOVE, ORIGINAL }

    /** What the strip shows: R6 §2.2.7 draws add and remove as "+ word" and "– word". */
    val shown: String get() = when (kind) {
        Kind.ADD -> "+ $text"
        Kind.REMOVE -> "– $text"
        else -> text
    }
}

/** The Word Flow trail (R6 §2.4, LOW, H3): the finger's recent path, and how far it has retracted after lift. */
data class Trail(val points: List<Offset>, val retract: Float = 0f)

/** The cursor-control dot while held (R6 §2.5.7, LOW, H4): where the finger is. */
data class CursorDrag(val dot: Offset, val finger: Offset)

/** Voice typing's state in the strip. */
sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String) : VoiceState
}

/** The emoji categories, R6 §2.6.1's ten-cell row minus abc and backspace. */
enum class EmojiCategory { RECENT, SMILEYS, PEOPLE, CELEBRATION, FOOD, TRAVEL, SYMBOLS, TEXT }

/**
 * Everything the keyboard draws, as Compose state. The controller writes it on the main thread; the
 * view only reads it. Nothing in here talks to the app.
 */
class KeyboardState {
    var field by mutableStateOf(FieldInfo.DEFAULT)
    var layout by mutableStateOf(Layouts.build(Layer.LETTERS, FieldInfo.DEFAULT))
    var shift by mutableStateOf(ShiftMode.OFF)

    /** Keys held down right now, by id: pressed keys fill with the accent (R6 §2.3.1). */
    val pressed = mutableStateListOf<String>()
    var popup by mutableStateOf<KeyPopup?>(null)
    var alternates by mutableStateOf<AlternatesPopup?>(null)
    var oneHanded by mutableStateOf<OneHandedPopup?>(null)

    var strip by mutableStateOf<List<StripItem>>(emptyList())
    var trail by mutableStateOf<Trail?>(null)
    var cursorDrag by mutableStateOf<CursorDrag?>(null)

    var emojiOpen by mutableStateOf(false)
    var emojiCategory by mutableStateOf(EmojiCategory.SMILEYS)
    var recentEmoji by mutableStateOf<List<String>>(emptyList())

    /** Space-bar drag (Decisions "Moving the keyboard"): how far above rest the panel sits, in px. */
    var raise by mutableStateOf(0f)
    var dock by mutableStateOf(Dock.FULL)

    var accent by mutableStateOf(0xFF0078D7L)
    var handedness by mutableStateOf(Handedness.RIGHT)
    var voice by mutableStateOf<VoiceState>(VoiceState.Idle)

    /** A one-line notice shown in the strip (microphone busy, permission off). */
    var notice by mutableStateOf<String?>(null)

    /** True when the letters are drawn in capitals. */
    val upper: Boolean get() = shift != ShiftMode.OFF || this.field.capCharacters
}
