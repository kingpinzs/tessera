package app.tileshell.media

/**
 * The capture answer's output guard (phase 17; Decisions 2026-10-05, r3 D1; the owner's ruling Q-17-2 (b)). TRUST: this
 * is the one rule that decides whether the shell's Camera writes into a URI another app handed it. Free of Android
 * types so it is unit-tested (`CaptureOutputGuardTest`).
 *
 * `ContentResolver.openOutputStream(uri)` runs as the shell, so it succeeds wherever the SHELL can write — contacts,
 * calendars, after phase 18 all files — whatever the caller itself may reach. The guard therefore accepts a
 * `content://` EXTRA_OUTPUT only when ALL of these hold:
 *  (a) the activity was started for a result (`callingPackage != null`), so the platform names the caller;
 *  (b) the intent's own ClipData holds that same URI and the intent's flags include FLAG_GRANT_WRITE_URI_PERMISSION
 *      (the form Android gives a capture request; it is also what lets the shell write a caller's private provider);
 *  (c) the URI's authority is not one of the shell's own, and names no other user's provider;
 *  (d) THE APPS BEHIND THE REQUEST MAY WRITE THAT URI THEMSELVES ([callerMayWrite], computed by
 *      `UriAccessRules.captureMayWrite` — the one rule the viewer and the player read by, too). BOTH of: (d1) the app
 *      the result goes to owns the provider, or holds an explicit write grant for that URI, or (MediaStore, API 35+)
 *      the provider, asked, says it may; and (d2) on API 35+ the platform says the app that really STARTED the capture
 *      could write it — so a go-between that forwards another app's result gains nothing. A permission-wide access
 *      with no grant (WRITE_CONTACTS, the system uid) is never enough. On API 34 (d2) cannot be asked and a MediaStore
 *      row the caller owns is refused unless it holds a grant for it: the guard fails closed.
 * Anything else is refused and nothing is written.
 *
 * Why (d) exists (build, 2026-10-05; it corrects r3 D1's premise): (b) was written as "Android checked the grant
 * against the CALLER's own access when the activity started". It does not always. When the app being started already
 * holds the access itself, the platform decides that no grant is needed and returns before it ever looks at the caller —
 * and the shell holds WRITE_CONTACTS and WRITE_CALENDAR. So a caller with no contacts permission could set the flag and
 * its own ClipData on a contact's `display_photo` URI, the start would succeed, and (a)–(c) alone would accept it. (b)
 * therefore proves nothing about the caller; (d) asks the question directly.
 */
object CaptureOutputGuard {
    /** `Intent.FLAG_GRANT_WRITE_URI_PERMISSION`. */
    const val FLAG_GRANT_WRITE = 0x00000002

    const val LINE_NO_GRANT = "refused output: no grant"

    fun lineForScheme(scheme: String): String = "refused output scheme=${scheme.filter { it.isLetterOrDigit() || it in "+-." }.take(16)}"

    sealed interface Decision {
        /** The request names no EXTRA_OUTPUT: the no-output contract applies (a thumbnail, or a new DCIM/Camera video). */
        data object NoOutput : Decision

        /** Write to [uri] and nowhere else. Only [decide] makes one. */
        class Accepted internal constructor(val uri: String) : Decision

        /** RESULT_CANCELED, nothing written; [line] is the `[camera]` diagnostics line. */
        data class Refused(val line: String) : Decision
    }

    /**
     * @param output EXTRA_OUTPUT, read from its text ONCE ([ContentUriText.parse] of `Uri.toString()`); null when the
     *   request names none. Its scheme and authority are weighed here and its [ContentUriText.text] is what an
     *   [Decision.Accepted] carries to the write — one value, so the check and the write cannot disagree (A-L1)
     * @param callingPackage `Activity.getCallingPackage()`: null unless started for a result
     * @param clipUris every URI in the intent's ClipData, as `Uri.toString()`
     * @param intentFlags `Intent.getFlags()`
     * @param ownAuthorities the authorities of every provider the shell's package declares
     * @param callerMayWrite condition (d): whether the recipient and the starter may write [output] themselves, as
     *   `UriAccessRules.captureMayWrite` reads it from the platform. False whenever there is no caller.
     */
    fun decide(
        output: ContentUriText?,
        callingPackage: String?,
        clipUris: List<String>,
        intentFlags: Int,
        ownAuthorities: Set<String>,
        callerMayWrite: Boolean,
    ): Decision {
        if (output == null) return Decision.NoOutput
        val scheme = output.scheme?.lowercase().orEmpty()
        if (scheme != "content") return Decision.Refused(lineForScheme(scheme.ifEmpty { "none" }))
        val granted = meetsAToC(output, callingPackage, clipUris, intentFlags, ownAuthorities) && callerMayWrite
        return if (granted) Decision.Accepted(output.text) else Decision.Refused(LINE_NO_GRANT)
    }

    /**
     * Conditions (a), (b) and (c) for a `content` [output], without (d). `CaptureRequestRule` asks the platform about
     * (d) only when this holds, and [decide] accepts only when both do. It makes no [Decision.Accepted].
     */
    fun meetsAToC(output: ContentUriText, callingPackage: String?, clipUris: List<String>, intentFlags: Int, ownAuthorities: Set<String>): Boolean {
        // An authority may carry a user id ("10@media", or "10%40media" before it is decoded): another user's provider
        // is never written, and the shell's own authority is refused however it is spelled.
        val provider = output.authority.orEmpty().substringAfterLast('@').lowercase()
        return output.scheme?.lowercase() == "content" &&
            callingPackage != null &&
            (intentFlags and FLAG_GRANT_WRITE) != 0 &&
            clipUris.any { it == output.text } &&
            provider.isNotEmpty() &&
            output.plainAuthority != null &&
            ownAuthorities.none { it.lowercase() == provider }
    }
}
