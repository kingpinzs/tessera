package app.tileshell.media

/**
 * The capture answer's output guard (phase 17; Decisions 2026-10-05, r3 D1; the owner's ruling Q-17-2 (b)). TRUST: this
 * is the one rule that decides whether the shell's Camera writes into a URI another app handed it. Free of Android
 * types so it is unit-tested (`CaptureOutputGuardTest`).
 *
 * `ContentResolver.openOutputStream(uri)` runs as the shell, so it succeeds wherever the SHELL can write — contacts,
 * calendars, after phase 18 all files — whatever the caller itself may reach. The guard therefore accepts a
 * `content://` EXTRA_OUTPUT only when all three hold:
 *  (a) the activity was started for a result (`callingPackage != null`);
 *  (b) the intent's own ClipData holds that same URI and the intent's flags include FLAG_GRANT_WRITE_URI_PERMISSION —
 *      which Android checked against the CALLER's own access when the activity started (it builds that ClipData itself
 *      only when the caller set none; a caller that sets its own without the flag is never checked, and is refused here);
 *  (c) the URI's authority is not one of the shell's own.
 * Anything else is refused and nothing is written.
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
     */
    fun decide(
        output: String?,
        outputScheme: String?,
        outputAuthority: String?,
        callingPackage: String?,
        clipUris: List<String>,
        intentFlags: Int,
        ownAuthorities: Set<String>,
    ): Decision {
        if (output == null) return Decision.NoOutput
        val scheme = outputScheme?.lowercase().orEmpty()
        if (scheme != "content") return Decision.Refused(lineForScheme(scheme.ifEmpty { "none" }))
        val authority = outputAuthority.orEmpty()
        // An authority may carry a user id ("10@media"); the part after the last '@' is the provider's name.
        val provider = authority.substringAfterLast('@').lowercase()
        val granted = callingPackage != null &&
            (intentFlags and FLAG_GRANT_WRITE) != 0 &&
            clipUris.any { it == output } &&
            provider.isNotEmpty() &&
            ownAuthorities.none { it.lowercase() == provider }
        return if (granted) Decision.Accepted(output) else Decision.Refused(LINE_NO_GRANT)
    }
}
