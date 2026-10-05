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
 *  (d) THE CALLER ITSELF MAY WRITE THAT URI ([callerMayWrite], computed by `CaptureCallerAccess`): the provider
 *      belongs to the caller's own uid, or the platform says the caller's uid holds write access to that URI.
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
     * @param output EXTRA_OUTPUT as the caller wrote it (`Uri.toString()`), null when absent
     * @param outputScheme its scheme, lower-cased by the caller of this function from `Uri.getScheme()`
     * @param outputAuthority its authority as `Uri.getAuthority()` gives it (user-info included when present)
     * @param callingPackage `Activity.getCallingPackage()`: null unless started for a result
     * @param clipUris every URI in the intent's ClipData, as `Uri.toString()`
     * @param intentFlags `Intent.getFlags()`
     * @param ownAuthorities the authorities of every provider the shell's package declares
     * @param callerMayWrite condition (d): whether the caller's own uid may write [output], as `CaptureCallerAccess`
     *   reads it from the platform. False whenever there is no caller.
     */
    fun decide(
        output: String?,
        outputScheme: String?,
        outputAuthority: String?,
        callingPackage: String?,
        clipUris: List<String>,
        intentFlags: Int,
        ownAuthorities: Set<String>,
        callerMayWrite: Boolean,
    ): Decision {
        if (output == null) return Decision.NoOutput
        val scheme = outputScheme?.lowercase().orEmpty()
        if (scheme != "content") return Decision.Refused(lineForScheme(scheme.ifEmpty { "none" }))
        val authority = outputAuthority.orEmpty()
        // An authority may carry a user id ("10@media"): another user's provider is never written, and the shell's own
        // authority is refused however it is spelled.
        val provider = authority.substringAfterLast('@').lowercase()
        val granted = callingPackage != null &&
            (intentFlags and FLAG_GRANT_WRITE) != 0 &&
            clipUris.any { it == output } &&
            provider.isNotEmpty() &&
            '@' !in authority &&
            ownAuthorities.none { it.lowercase() == provider } &&
            callerMayWrite
        return if (granted) Decision.Accepted(output) else Decision.Refused(LINE_NO_GRANT)
    }
}
