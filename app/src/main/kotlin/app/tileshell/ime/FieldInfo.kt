package app.tileshell.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** The field kinds R6 §2.8 and the Decisions tell apart. */
enum class FieldKind { TEXT, SEARCH, URL, EMAIL, PHONE, NUMBER, PASSWORD }

/** The editor action the Enter key performs. */
enum class FieldAction { NONE, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

/**
 * Everything the keyboard needs to know about the focused field, read once from its EditorInfo.
 *
 * Pure: it takes the two raw ints Android hands an IME (inputType, imeOptions) so the JVM tests can
 * drive every combination the edge cases list (`imeOptions` action keys, multi-line fields,
 * textNoSuggestions, the capitalisation flags) without a device. The constants it reads are
 * compile-time ints and inline into the test classpath.
 */
data class FieldInfo(
    val kind: FieldKind,
    val action: FieldAction,
    val multiLine: Boolean,
    val noSuggestions: Boolean,
    val capSentences: Boolean,
    val capWords: Boolean,
    val capCharacters: Boolean,
    val noEnterAction: Boolean,
) {
    /** A password never shows suggestions and never teaches the dictionary (Decisions; edge case 1). */
    val isPassword: Boolean get() = kind == FieldKind.PASSWORD

    /**
     * The strip offers words everywhere except password fields (edge case 1), textNoSuggestions (the
     * app's own request) and the digit fields. URL and email fields still get completions, but the
     * engine never AUTOcorrects in them — an address is not a misspelling.
     */
    val suggestionsOn: Boolean get() = !isPassword && !noSuggestions && kind != FieldKind.PHONE && kind != FieldKind.NUMBER

    /** Word Flow needs the dictionary, so it follows the same rule. */
    val swipeOn: Boolean get() = suggestionsOn

    /**
     * Words typed here may be learned — everywhere but a password field (Decisions: "learning new words
     * from what Jeremy types (never in password fields)"). The same rule as the engine's own column
     * (engine FieldKind.learns), so there is one truth table, not two (review m13).
     */
    val learningOn: Boolean get() = !isPassword

    /**
     * Enter inserts a newline in a multi-line field, or when the app asked for no action on Enter
     * (IME_FLAG_NO_ENTER_ACTION), or when there simply is no action.
     */
    val enterInsertsNewline: Boolean get() = (multiLine && (noEnterAction || action == FieldAction.NONE)) ||
        (action == FieldAction.NONE && kind != FieldKind.URL && kind != FieldKind.SEARCH)

    /**
     * What pressing Enter DOES: the app's editor action, or NONE for a newline (multi-line) / an Enter
     * key event (single line). IME_FLAG_NO_ENTER_ACTION means the app wants the newline, not the action.
     */
    val enterAction: FieldAction get() = if (noEnterAction) FieldAction.NONE else action

    /** Double-space period never runs in a URL or email field (Decisions stand-in (5)). */
    val doubleSpacePeriodOn: Boolean get() = kind != FieldKind.URL && kind != FieldKind.EMAIL && !isPassword

    companion object {
        val DEFAULT = from(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_UNSPECIFIED)

        fun from(inputType: Int, imeOptions: Int): FieldInfo {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val flags = inputType and InputType.TYPE_MASK_FLAGS
            val kind = when (cls) {
                InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
                InputType.TYPE_CLASS_NUMBER ->
                    if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldKind.PASSWORD else FieldKind.NUMBER
                InputType.TYPE_CLASS_DATETIME -> FieldKind.NUMBER
                InputType.TYPE_CLASS_TEXT -> when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> FieldKind.PASSWORD
                    InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                    else -> FieldKind.TEXT
                }
                else -> FieldKind.TEXT
            }
            val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
            val action = when (actionId) {
                EditorInfo.IME_ACTION_GO -> FieldAction.GO
                EditorInfo.IME_ACTION_SEARCH -> FieldAction.SEARCH
                EditorInfo.IME_ACTION_SEND -> FieldAction.SEND
                EditorInfo.IME_ACTION_NEXT -> FieldAction.NEXT
                EditorInfo.IME_ACTION_DONE -> FieldAction.DONE
                EditorInfo.IME_ACTION_PREVIOUS -> FieldAction.PREVIOUS
                else -> FieldAction.NONE
            }
            val multiLine = cls == InputType.TYPE_CLASS_TEXT && (flags and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
            val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            return FieldInfo(
                kind = if (kind == FieldKind.TEXT && action == FieldAction.SEARCH) FieldKind.SEARCH else kind,
                action = if (noEnterAction && multiLine) FieldAction.NONE else action,
                multiLine = multiLine,
                noSuggestions = cls == InputType.TYPE_CLASS_TEXT && (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0,
                capSentences = cls == InputType.TYPE_CLASS_TEXT && (flags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0,
                capWords = cls == InputType.TYPE_CLASS_TEXT && (flags and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0,
                capCharacters = cls == InputType.TYPE_CLASS_TEXT && (flags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0,
                noEnterAction = noEnterAction,
            )
        }
    }
}
