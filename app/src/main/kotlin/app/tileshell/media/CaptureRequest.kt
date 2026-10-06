package app.tileshell.media

/**
 * A capture request as the platform hands it over — the real Intent and Activity behind a port, so the mapping from the
 * request to the output guard's inputs is unit-tested (the trust review's A-M2). The real port is `CaptureActivity`'s
 * `IntentCaptureRequest`, a one-line read per method. Any method may throw (an extras Bundle that cannot be
 * unparcelled): [CaptureRequestRule] refuses such a request. Who STARTED the activity is not read here: that is
 * [UriAccessPort]'s, the one port the viewer and the player ask too.
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
 * From a capture request to the output guard's decision (phase 17; A-M2, A-L1, A2-F1, A2-L2). TRUST: with
 * [CaptureOutputGuard] and [UriAccessRules.captureMayWrite] this is everything that decides whether the Camera writes
 * into another app's URI; the activity only carries the outcome out.
 *
 *  - No EXTRA_OUTPUT → the guard's NoOutput (the no-output contract).
 *  - EXTRA_OUTPUT present and not a Uri → refused. Never the no-output contract by accident.
 *  - EXTRA_OUTPUT a Uri → its text is parsed ONCE ([ContentUriText]), exactly as it is; the guard's scheme and
 *    authority, condition (d) and the string the write layer opens all come from that one value.
 *  - Condition (d) is asked only for a request that passes the guard's other conditions — the platform's launch answer
 *    throws for a URI the intent's ClipData does not hold, and a request that fails (a) to (c) is refused whatever (d).
 *  - A forwarded result (A-L2, A2-F1): the platform names who started the activity and it is not the calling package,
 *    or (API 35+) the platform says the starter could not write the output → refused with the guard's no-grant line;
 *    the first also writes a line that names the two packages.
 *  - A request that cannot be read (any read throws — an Error too: a hostile extras Bundle must not crash `:camera`)
 *    → refused, with a line that names the throwable's class only.
 */
object CaptureRequestRule {
    /**
     * [before] are the `[camera]` lines written ahead of the request line and [after] the ones written behind the
     * refusal's own line, which is the decision's.
     */
    class Outcome(val decision: CaptureOutputGuard.Decision, val before: List<String>, val after: List<String> = emptyList())

    /** A package or uid name as a line may carry it: the characters a package name has, and at most 80 of them. */
    fun boundedName(name: String?): String = name.orEmpty().filter { it.isLetterOrDigit() || it in "._:" }.take(80).ifEmpty { "unknown" }

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
                // Only a request that meets (a) to (c) is worth asking (d) about.
                val write = if (CaptureOutputGuard.meetsAToC(output, caller, clipUris, flags, own)) {
                    UriAccessRules.captureMayWrite(access, output, caller)
                } else {
                    UriAccessRules.Write(allowed = false, recipientMayWrite = false, starter = UriAccessRules.LAUNCH_NOT_ASKED)
                }
                // What the guard is about to weigh, for the row and the review to read (the authority only: no path).
                val inputs = "capture guard inputs: scheme=${CaptureOutputGuard.lineForScheme(output.scheme.orEmpty()).substringAfter('=')} " +
                    "authority=${UriAccessRules.lineAuthority(output.authority)} " +
                    "startedForResult=${caller != null} clipHoldsOutput=${clipUris.any { it == output.text }} " +
                    "writeGrantFlag=${(flags and CaptureOutputGuard.FLAG_GRANT_WRITE) != 0} " +
                    "ownAuthority=${own.any { it.equals(output.authority.orEmpty().substringAfterLast('@'), ignoreCase = true) }} callerMayWrite=${write.allowed} " +
                    "recipientMayWrite=${write.recipientMayWrite} starterAtLaunch=${write.starter}"
                val after = write.forwardedFromUid?.let { startedBy ->
                    val name = (access.nameOfUid(startedBy) as? Platform.Said)?.value
                    listOf("capture request forwarded: started by ${boundedName(name)}, result to ${boundedName(caller)}")
                }.orEmpty()
                Outcome(CaptureOutputGuard.decide(output, caller, clipUris, flags, own, write.allowed), listOf(inputs), after)
            }
        }
    } catch (e: Throwable) {
        Outcome(CaptureOutputGuard.Decision.Refused(CaptureOutputGuard.LINE_NO_GRANT), listOf("capture request unreadable (${UriAccessRules.className(e.javaClass.simpleName)})"))
    }
}
