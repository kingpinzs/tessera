package app.tileshell.media

/**
 * A capture request as the platform hands it over — the real Intent and Activity behind a port, so the mapping from the
 * request to the output guard's inputs is unit-tested (the trust review's A-M2). The real port is `CaptureActivity`'s
 * `IntentCaptureRequest`, a one-line read per method. Any method may throw (an extras Bundle that cannot be
 * unparcelled): [CaptureRequestRule] refuses such a request.
 */
interface CaptureRequestPort {
    /** Whether the intent carries EXTRA_OUTPUT at all. */
    fun hasOutput(): Boolean

    /** EXTRA_OUTPUT's `Uri.toString()`, or null when the extra is present and is NOT a Uri. Read once. */
    fun outputText(): String?

    /** `Activity.getCallingPackage()`: who receives the result; null unless started for a result. */
    fun callingPackage(): String?

    /** Every URI in the intent's ClipData, as `Uri.toString()`. */
    fun clipUris(): List<String>

    /** `Intent.getFlags()`. */
    fun flags(): Int

    /** The authorities of every provider the shell's own package declares. */
    fun ownAuthorities(): Set<String>
}

/**
 * From a capture request to the output guard's decision (phase 17; A-M2, A-L1). TRUST: with [CaptureOutputGuard] and
 * [CaptureCallerAccess] this is everything that decides whether the Camera writes into another app's URI; the activity
 * only carries the outcome out.
 *
 *  - No EXTRA_OUTPUT → the guard's NoOutput (the no-output contract).
 *  - EXTRA_OUTPUT present and not a Uri → refused. Never the no-output contract by accident.
 *  - EXTRA_OUTPUT a Uri → its text is parsed ONCE ([ContentUriText]); the guard's scheme and authority, condition (d)
 *    and the string the write layer opens all come from that one value.
 *  - A request that cannot be read (any read throws) → refused, with a line that names the exception's class only.
 */
object CaptureRequestRule {
    /** [before] are the `[camera]` lines written ahead of the request line; the refusal's own line is the decision's. */
    class Outcome(val decision: CaptureOutputGuard.Decision, val before: List<String>)

    fun decide(request: CaptureRequestPort, access: UriAccessPort): Outcome = try {
        if (!request.hasOutput()) {
            Outcome(CaptureOutputGuard.decide(null, null, emptyList(), 0, emptySet(), callerMayWrite = false), emptyList())
        } else {
            val text = request.outputText()
            if (text == null) {
                // EXTRA_OUTPUT is present and is not a Uri: never the no-output contract by accident.
                Outcome(CaptureOutputGuard.Decision.Refused(CaptureOutputGuard.LINE_NO_GRANT), listOf("capture request: EXTRA_OUTPUT is not a Uri"))
            } else {
                val output = ContentUriText.parse(text)
                val caller = request.callingPackage()
                val clipUris = request.clipUris()
                val flags = request.flags()
                val own = request.ownAuthorities()
                val callerMayWrite = CaptureCallerAccess.callerMayWrite(access, output, caller)
                // What the guard is about to weigh, for the row and the review to read (the authority only: no path).
                val inputs = "capture guard inputs: scheme=${CaptureOutputGuard.lineForScheme(output.scheme.orEmpty()).substringAfter('=')} " +
                    "authority=${output.authority.orEmpty().filter { it.isLetterOrDigit() || it in ".-_@" }.take(80)} " +
                    "startedForResult=${caller != null} clipHoldsOutput=${clipUris.any { it == output.text }} " +
                    "writeGrantFlag=${(flags and CaptureOutputGuard.FLAG_GRANT_WRITE) != 0} " +
                    "ownAuthority=${own.any { it.equals(output.authority.orEmpty().substringAfterLast('@'), ignoreCase = true) }} callerMayWrite=$callerMayWrite"
                Outcome(CaptureOutputGuard.decide(output, caller, clipUris, flags, own, callerMayWrite), listOf(inputs))
            }
        }
    } catch (e: Exception) {
        Outcome(CaptureOutputGuard.Decision.Refused(CaptureOutputGuard.LINE_NO_GRANT), listOf("capture request unreadable (${e.javaClass.simpleName})"))
    }
}
