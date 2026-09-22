package app.tileshell.ime.engine

/**
 * What kind of field the caret is in, as far as the text engine cares. The IME maps the editor's
 * InputType / imeOptions onto one of these; the engine never sees the Android flags.
 *
 * Each rule the phase doc states per field is a column here, so the four places that ask (suggest,
 * autocorrect, learn, double-space period) read one truth:
 *
 * - [suggests] — phase 05 Edge cases: "Password fields: no suggestions"; `textNoSuggestions` is the
 *   app's own request for none.
 * - [autoCorrects] — never in a password, URL, e-mail or no-suggestions field: an address is not a
 *   misspelling and a password must land exactly as typed.
 * - [learns] — Decisions stand-in (6): "committed twice OUTSIDE PASSWORD FIELDS". Every other field
 *   learns; what counts as a learnable word is [UserDictionary.isLearnable].
 * - [doubleSpacePeriod] — Decisions stand-in (5): "never … in a URL or email field". PASSWORD is an
 *   agent pick: the ruling names the fields where a period is wrong, and a period silently spliced into
 *   a password is worse than either of those, so it is excluded too.
 */
enum class FieldKind(
    val suggests: Boolean,
    val autoCorrects: Boolean,
    val learns: Boolean,
    val doubleSpacePeriod: Boolean,
) {
    TEXT(suggests = true, autoCorrects = true, learns = true, doubleSpacePeriod = true),
    URL(suggests = true, autoCorrects = false, learns = true, doubleSpacePeriod = false),
    EMAIL(suggests = true, autoCorrects = false, learns = true, doubleSpacePeriod = false),
    PASSWORD(suggests = false, autoCorrects = false, learns = false, doubleSpacePeriod = false),
    NO_SUGGESTIONS(suggests = false, autoCorrects = false, learns = true, doubleSpacePeriod = true),
}
