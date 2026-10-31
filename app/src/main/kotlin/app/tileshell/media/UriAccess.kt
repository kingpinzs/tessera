package app.tileshell.media

/**
 * One raw answer of the platform: what it said, or the class of what it threw. Never a decision — every rule below maps
 * an answer it does not expect (a throw, a null, any value but the one that means yes) to NO.
 */
sealed interface Platform<out T> {
    data class Said<T>(val value: T) : Platform<T>

    /** [exception] is the thrown class's simple name and nothing else of it. */
    data class Threw(val exception: String) : Platform<Nothing>
}

/** A provider as the package manager describes it. */
data class ProviderFacts(
    /** The uid of the app that declares the provider. */
    val ownerUid: Int,
    /**
     * `ProviderInfo.forceUriPermissions`: the provider decides every access itself, item by item (MediaStore). Only for
     * such a provider does the platform's "may this uid reach this URI" run the provider's own code; for every other
     * provider it is an answer from the manifest (A2-F2, C2-L8), which says nothing about a provider that guards in code.
     */
    val forceUriPermissions: Boolean,
)

/**
 * What the PLATFORM says about who may reach a content URI (phase 17; the trust reviews' A-M1, A2-F1 to A2-F3, C-M4,
 * C2-M1, C2-L1). ONE port for the three places another app can make the shell touch a URI it names: the Camera's
 * capture answer (a write), Photos' exported viewer and the exported video player (reads). The real port is
 * `AndroidUriAccess`, the tests' a fake. Free of Android types.
 *
 * The port is THIN: each method is one platform call and returns the platform's raw answer — the result code, the uid,
 * the provider's facts — or the class of what the call threw. It decides nothing: [UriAccessRules] holds every rule and
 * its tests, and maps every unknown, null and throw to NO.
 */
interface UriAccessPort {
    /** `Build.VERSION.SDK_INT`. */
    val apiLevel: Int

    /** `Process.myUid()`: the shell's own uid. */
    fun shellUid(): Int

    /** `PackageManager.getPackageUid(packageName, 0)`. */
    fun uidOf(packageName: String): Platform<Int>

    /** `PackageManager.resolveContentProvider(authority, 0)`: who declares the provider, or null when nobody does. */
    fun provider(authority: String): Platform<ProviderFacts?>

    /**
     * `Context.checkUriPermission(uri, -1, uid, modeFlag)`: the platform's result code for whether [uid] holds an
     * explicit URI grant (it answers grants only and never asks the provider).
     */
    fun holdsGrant(uri: String, uid: Int, modeFlag: Int): Platform<Int>

    /**
     * `Context.checkContentUriPermissionFull(uri, -1, uid, modeFlag)` (API 35+): the platform's result code. The rules
     * ask it for a [ProviderFacts.forceUriPermissions] provider only.
     */
    fun providerSays(uri: String, uid: Int, modeFlag: Int): Platform<Int>

    /**
     * `Activity.getInitialCaller().checkContentUriPermission(uri, modeFlag)` (API 35+): the platform's LAUNCH answer —
     * whether the app that really started this activity could reach [uri] when it did. It needs no uid: the platform
     * knows the starter even when it does not name it. It throws for a URI the launch intent did not carry (in its data,
     * EXTRA_STREAM or ClipData) and for one the shell itself cannot reach.
     */
    fun launchAnswer(uri: String, modeFlag: Int): Platform<Int>

    /**
     * `Activity.getLaunchedFromUid()`: the uid that started the activity, or -1 — which is what the platform answers
     * for every starter that is neither the shell itself nor an app that chose to share its identity.
     */
    fun launchedFromUid(): Platform<Int>

    /** `PackageManager.getNameForUid(uid)`. */
    fun nameOfUid(uid: Int): Platform<String?>

    // The four reads below were added for D1 (a), 2026-10-31. A port that does not answer them (the clock's own test
    // doubles predate them) answers with a throw, which every rule takes as NO.

    /** `Activity.getIntent().getFlags()`: the launch intent's flags. */
    fun launchFlags(): Platform<Int> = Platform.Threw(NOT_ANSWERED)

    /** `Activity.getIntent().getData()`, as `Uri.toString()`. */
    fun launchData(): Platform<String?> = Platform.Threw(NOT_ANSWERED)

    /** Every URI in `Activity.getIntent().getClipData()`, as `Uri.toString()`. */
    fun launchClipUris(): Platform<List<String>> = Platform.Threw(NOT_ANSWERED)

    /** `ContentResolver.getPersistedUriPermissions()`: the URI of every grant the shell has persisted, as `Uri.toString()`. */
    fun persistedGrants(): Platform<List<String>> = Platform.Threw(NOT_ANSWERED)

    companion object {
        const val NOT_ANSWERED = "NotAnswered"
    }
}

/**
 * A URI another app handed the shell, read from its text ONCE (A-L1): the scheme and the authority every rule weighs
 * are the ones this text has, and [text] is the very string the write or the read is made through — never a second
 * reading of the caller's `Uri` object, whose parts need not agree with its text. The text is taken exactly as it is:
 * nothing is trimmed and no letter's case is changed (A2-S).
 *
 * The split is the platform's own for a URI made from a string (`Uri.parse`): the scheme ends at the first `:`; an
 * authority follows `//` and ends at the first `/`, `\`, `?` or `#`.
 */
class ContentUriText private constructor(val text: String, val scheme: String?, val authority: String?) {
    /**
     * The authority when it plainly names one provider of this user: not empty, no user id (`10@media`) and no
     * percent-escape (`%40` is `@` once decoded; no real authority holds one). Null otherwise.
     */
    val plainAuthority: String? get() = authority?.takeIf { it.isNotEmpty() && '@' !in it && '%' !in it }

    companion object {
        fun parse(text: String): ContentUriText {
            val colon = text.indexOf(':')
            val scheme = if (colon < 0) null else text.substring(0, colon)
            val start = colon + 1
            val authority = if (text.length > start + 1 && text[start] == '/' && text[start + 1] == '/') {
                var end = start + 2
                while (end < text.length && text[end] != '/' && text[end] != '\\' && text[end] != '?' && text[end] != '#') end++
                text.substring(start + 2, end)
            } else {
                null
            }
            return ContentUriText(text, scheme, authority)
        }
    }
}

/**
 * THE ONE RULE (phase 17, the third round of trust fixes): whether the shell may READ (the viewer, the player) or WRITE
 * (the capture answer) a `content://` URI another app named. The shell opens such a URI with its OWN identity — and it
 * can read every picture and video and write the contacts — so the question is never "can the shell" but "could the
 * app that asked". Every answer is the platform's ([UriAccessPort]); the intent's own flags are never taken as proof
 * (THE LAUNCH GRANT below asks for them only beside the grant the platform made). Pure: every combination is
 * unit-tested (`UriAccessRulesTest`, `UriAccessLaunchGrantTest`).
 *
 * Two apps can stand behind a request, and they are not always one:
 *  - the STARTER — the app that really started the activity. The platform names its uid only when it is the shell or
 *    chose to share its identity ([UriAccessPort.launchedFromUid]); from API 35 it answers for it without naming it
 *    ([UriAccessPort.launchAnswer]).
 *  - the RECIPIENT of a result (`Activity.getCallingPackage()`), which a go-between that forwards the result
 *    (FLAG_ACTIVITY_FORWARD_RESULT) makes a different app from the starter.
 *
 * READ ([starterMayRead]) — a launch that is not the shell's own: allowed when
 *  - API 35+: the platform's launch answer says the starter could read the URI; or
 *  - the platform names the starter's uid, and that uid owns the provider, or holds an explicit read grant for the URI,
 *    or (a forceUriPermissions provider, API 35+) the provider, asked, says it may.
 * Nothing else. On API 34 an unnamed starter is refused. An authority with a user id (`10@media`, a work-profile or
 * clone "Open with") is handed to the platform as it is — the platform splits user and authority itself, for the check
 * and for the read alike — so such a URI is read where the platform says the starter could; the own-provider and
 * provider-says branches never fire for it.
 *
 * READ AT THE TWO EXPORTED HELPERS ([helperMayRead]; the owner's ruling D1 (a), 2026-10-31) — the viewer and the player:
 * [starterMayRead], and where it refuses, THE LAUNCH GRANT ([launchGrant]). Clock's alarm API keeps [starterMayRead]
 * alone. The launch grant admits, on API 34, 35 and 36 alike, only when ALL of these hold:
 *  - the URI is a plain library address: `content`, an authority with no `@` or `%`, a path of letters, digits and
 *    `. _ ~ / -` only (no `%`, `?`, `#`), and a forceUriPermissions provider (MediaStore);
 *  - the launch intent carries FLAG_GRANT_READ_URI_PERMISSION (FLAG_GRANT_WRITE_URI_PERMISSION for the capture answer)
 *    and names that very text as its data or in its ClipData;
 *  - the SHELL holds an explicit grant of that mode for that very text (`Context.checkUriPermission(uri, -1, myUid, mode)`);
 *  - no grant the shell has PERSISTED covers it (taken as covering: the same text, any path above it, or any URI of
 *    that authority that is not plain).
 * Its basis, read from android14-release and android16-release source (frameworks/base, packages/providers/MediaProvider):
 *  (1) `ActivityStarter.Request.resolveActivity` collects the launch's grants "without holding lock":
 *      `UriGrantsManagerService.checkGrantUriPermissionFromIntent(intent, resolvedCallingUid, …)` — the uid of the
 *      process that called startActivity (a PendingIntent's creator; for the system's "Open with" chooser,
 *      `ActivityTaskManagerService.startActivityAsCaller`, the app that asked) — over the intent's data and every
 *      ClipData URI, in the mode of the intent's own grant flags (`checkGrantUriPermissionFromIntentUnlocked`).
 *      Android itself builds that ClipData, with the read and write flags, for a capture request's EXTRA_OUTPUT
 *      (`Intent.migrateExtraStreamToClipData`, from `Instrumentation.execStartActivity`).
 *  (2) `checkGrantUriPermissionUnlocked`: a forceUriPermissions provider never takes the "the target already holds the
 *      access, no grant is needed" shortcut that makes the capture guard's (b) worthless for the contacts; MediaStore
 *      allows grants (its manifest); then the STARTER must pass `checkHoldingPermissionsUnlocked`, which for such a
 *      provider asks the provider itself (`ContentProviderHelper.checkContentProviderUriPermission` ->
 *      `MediaProvider.checkUriPermission`: a query run as the starter's uid — a visible row for READ, an updatable one
 *      for WRITE, a photo-picker URI only by an explicit grant), or hold an explicit grant. Otherwise SecurityException
 *      at the starter, and the activity never starts. Where the start runs under the window-manager lock the provider
 *      is not asked and the answer is "denied" — that throws too.
 *  (3) Then the platform grants the SHELL that URI, owned by the started activity
 *      (`grantUriPermissionUncheckedFromIntent(intentGrants, mStartActivity.getUriPermissionsLocked())`), keyed by the
 *      user and the URI's text (`GrantUri.equals`; `Uri.equals` compares `toString()`; a parcelled `Uri` is rebuilt
 *      from its text on API 36 and from one representation per part on API 34, so the provider it names is the one its
 *      text names).
 *  (4) `Context.checkUriPermission` answers explicit grants only (`ActivityManagerService.checkUriPermission` ->
 *      `checkUriPermissionLocked`): the shell's own media permission never counts. It resolves the user from the URI as
 *      the shell's read does (`ContextImpl.resolveUserId`), so a grant for another user's row does not match.
 * Why every condition is needed. The FLAG on the delivered intent is not proof alone:
 * `ActivityTaskManagerService.startNextMatchingActivity` starts the next matching activity with the caller's own flags
 * and ClipData, already resolved, so no grant check runs and no grant is made; and a system or root starter is never
 * checked and never makes a grant (`checkGrantUriPermissionUnlocked`). The shell's GRANT is not proof alone:
 * `checkUriPermissionLocked` folds owned, global and persisted grants into one answer (`UriPermission.getStrength`), so
 * this launch's grant cannot be told from an older one — the persisted ones (the Start background's and the photo
 * frame's picker URIs) are listed and refused. A provider that is not forceUriPermissions took the manifest's word, or
 * the shortcut, at (2) (A2-F2, C2-L8): refused.
 *
 * WHAT THE LAUNCH GRANT LEAVES OPEN (verified in that source; nothing inside the activity can close it): an app that
 * cannot read library item X starts a helper through `startNextMatchingActivity`, with the grant flag, at a moment the
 * shell holds a NON-persisted grant for exactly X's text from somewhere else — another live viewer, player or capture
 * launched with X, a clip the shell read, another app's explicit grant. It is admitted: the viewer shows X read-only
 * in that app's task, the player plays it; a capture still needs (d1). On API 34 a system or root starter with such a
 * stale grant is admitted the same way (on API 35+ the launch answer already says "granted" for those two uids,
 * `checkContentUriPermissionFullUnlocked`).
 *
 * WRITE ([captureMayWrite]) — condition (d) of `CaptureOutputGuard`: BOTH of
 *  (d1) the RECIPIENT may write the URI: it owns the provider, or holds an explicit write grant for the URI, or (a
 *       forceUriPermissions provider, API 35+) the provider, asked, says it may. Never the manifest answer for any other
 *       provider: an app that holds a permission-wide access (WRITE_CONTACTS, the system uid) and no grant is refused
 *       on every Android version (A2-F2);
 *  (d2) API 35+: the STARTER may write it, by the platform's launch answer, or (D1 (a)) by THE LAUNCH GRANT for WRITE
 *       — refused when neither says yes, and when the launch answer throws and no launch grant is held (A2-F1: a
 *       go-between with no access that forwards the result of an app that has it is the starter; the platform answers
 *       for IT, and its start with the write flag throws before any grant is made). Where the platform names the
 *       starter and it is not the recipient, the request is refused on any Android version.
 * An authority with `@` or `%` is refused for a write.
 *
 * Per API level, for a library item (a MediaStore row) another app names: READ — API 34: the launch grant alone (there
 * is no launch answer); API 35 and 36: the launch answer, else the launch grant. WRITE — API 34: refused (MediaStore
 * cannot be asked about the recipient, so (d1) fails); API 35 and 36: (d1) MediaStore's own answer for the recipient
 * AND (d2) the launch answer or the launch grant.
 *
 * WHAT API 34 LEAVES OPEN (for the owner; his phone is on API 36): (d2) cannot be asked there, so (d1) stands alone. A
 * go-between that forwards the result of a victim V is then stopped only by (d1) being about V's OWN provider or a
 * grant V holds: a V that owns the provider (or holds a write grant for the URI), starts another app for a result, and
 * that app forwards the result to the Camera naming V's URI, gets the capture written into it after the user shoots
 * and accepts. V must start the attacker itself, and the attacker must not have shared its identity.
 *
 * WHAT THE PLATFORM'S LAUNCH ANSWER DOES NOT COVER (read from android16-release source: `ActivityStarter.execute` holds
 * the window-manager lock through `handleStartResult` -> `ActivityRecord.computeInitialCallerInfo` ->
 * `UriGrantsManagerService.checkContentUriPermissionFullUnlocked`): for a forceUriPermissions provider the system
 * cannot ask the provider under that lock and assumes "denied" (`ContentProviderHelper.checkContentProviderUriPermission`).
 * So for a MediaStore URI the launch answer is "granted" only for a starter that owns the provider or holds an explicit
 * grant — NOT for one that merely holds the media permission or owns the row. [starterMayRead] admits such a starter
 * only when it shares its identity (the uid-keyed branches then ask the provider directly), and fails closed there;
 * the two helpers and the capture answer admit it also by THE LAUNCH GRANT (above), whose check ran outside that lock.
 */
object UriAccessRules {
    /** `Intent.FLAG_GRANT_READ_URI_PERMISSION` / `FLAG_GRANT_WRITE_URI_PERMISSION` (pinned to the platform's by a test). */
    const val READ = 0x00000001
    const val WRITE = 0x00000002

    /** `PackageManager.PERMISSION_GRANTED`: the one result code that means yes. */
    const val GRANTED = 0

    /** `Build.VERSION_CODES.VANILLA_ICE_CREAM`: the first API with the launch answer and the provider's own answer. */
    const val API_ASKS_PROVIDER = 35

    /** Why a starter may read — or [NONE]: it may not. The word is what the player's line says. */
    enum class ReadWhy(val word: String) {
        NONE("no grant"),
        OWN_PROVIDER("its own provider"),
        URI_GRANT("a read grant"),
        PROVIDER_ALLOWS("the provider allows it"),
        LAUNCH_ACCESS("it had access at launch"),
        LAUNCH_GRANT("a grant from the launch"),
    }

    /**
     * @param launch the launch answer's outcome class, for a diagnostics line: [LAUNCH_GRANTED], [LAUNCH_DENIED],
     *   `threw <ExceptionClass>`, [LAUNCH_NOT_AVAILABLE] (below API 35) or [LAUNCH_NOT_ASKED] (decided before it)
     * @param grant [launchGrant]'s word, or null when it was not asked or the launch asked for no read grant
     */
    data class Read(val why: ReadWhy, val launch: String, val grant: String? = null) {
        val allowed: Boolean get() = why != ReadWhy.NONE
    }

    /**
     * @param recipientMayWrite (d1)
     * @param starter (d2)'s outcome class, as [Read.launch]
     * @param forwardedFromUid the starter's uid when the platform names it and it is not the recipient's
     * @param launchGrant [launchGrant]'s word for (d2), or null when it was not asked or the launch asked for no write grant
     */
    data class Write(
        val allowed: Boolean, val recipientMayWrite: Boolean, val starter: String, val forwardedFromUid: Int? = null, val launchGrant: String? = null,
    )

    const val LAUNCH_GRANTED = "granted"
    const val LAUNCH_DENIED = "denied"
    const val LAUNCH_NOT_AVAILABLE = "not available"
    const val LAUNCH_NOT_ASKED = "not asked"

    /** The uid that started the activity, when the platform names one. */
    fun starterUid(access: UriAccessPort): Int? = said(access.launchedFromUid())?.takeIf { it >= 0 }

    /** True only when the platform names the starter and it is the shell itself (Photos, the hub, a tile). */
    fun isOwnLaunch(access: UriAccessPort): Boolean {
        val shell = access.shellUid()
        return shell >= 0 && starterUid(access) == shell
    }

    /** READ: may the app that started this activity read [uri] itself? See the class comment. */
    fun starterMayRead(access: UriAccessPort, uri: ContentUriText): Read {
        if (uri.scheme != "content" || uri.authority.isNullOrEmpty()) return Read(ReadWhy.NONE, LAUNCH_NOT_ASKED)
        val uid = starterUid(access)
        if (uid != null) {
            val provider = uri.plainAuthority?.let { said(access.provider(it)) }
            if (provider != null && provider.ownerUid == uid) return Read(ReadWhy.OWN_PROVIDER, LAUNCH_NOT_ASKED)
            if (said(access.holdsGrant(uri.text, uid, READ)) == GRANTED) return Read(ReadWhy.URI_GRANT, LAUNCH_NOT_ASKED)
            if (providerItselfSays(access, provider, uri, uid, READ)) return Read(ReadWhy.PROVIDER_ALLOWS, LAUNCH_NOT_ASKED)
        }
        val launch = launchWord(access, uri, READ)
        return Read(if (launch == LAUNCH_GRANTED) ReadWhy.LAUNCH_ACCESS else ReadWhy.NONE, launch)
    }

    /** WRITE: condition (d) of the capture output guard for [recipientPackage]. See the class comment. */
    fun captureMayWrite(access: UriAccessPort, uri: ContentUriText, recipientPackage: String?): Write {
        val refused = Write(allowed = false, recipientMayWrite = false, starter = LAUNCH_NOT_ASKED)
        if (uri.scheme != "content") return refused
        val authority = uri.plainAuthority ?: return refused
        if (recipientPackage == null) return refused
        val recipient = said(access.uidOf(recipientPackage))?.takeIf { it >= 0 } ?: return refused
        val started = starterUid(access)
        if (started != null && started != recipient) return refused.copy(forwardedFromUid = started)
        val provider = said(access.provider(authority))
        val recipientMay = (provider != null && provider.ownerUid == recipient) ||
            said(access.holdsGrant(uri.text, recipient, WRITE)) == GRANTED ||
            providerItselfSays(access, provider, uri, recipient, WRITE)
        if (!recipientMay) return refused
        if (access.apiLevel < API_ASKS_PROVIDER) return Write(allowed = true, recipientMayWrite = true, starter = LAUNCH_NOT_AVAILABLE)
        val launch = launchWord(access, uri, WRITE)
        if (launch == LAUNCH_GRANTED) return Write(allowed = true, recipientMayWrite = true, starter = launch)
        val grant = launchGrant(access, uri, WRITE)
        return Write(allowed = grant == GRANT_HELD, recipientMayWrite = true, starter = launch, launchGrant = grant)
    }

    /**
     * READ at the exported viewer and player (D1 (a)): [starterMayRead], and where it refuses, [launchGrant]. The launch
     * answer's word is kept for the line either way. See the class comment.
     */
    fun helperMayRead(access: UriAccessPort, uri: ContentUriText): Read {
        val read = starterMayRead(access, uri)
        if (read.allowed) return read
        val grant = launchGrant(access, uri, READ)
        return Read(if (grant == GRANT_HELD) ReadWhy.LAUNCH_GRANT else ReadWhy.NONE, read.launch, grant)
    }

    /** [launchGrant]'s words: the first admits, every other refuses (as does `threw <ExceptionClass>`). */
    const val GRANT_HELD = "held"
    const val GRANT_NOT_LIBRARY = "not a library item"
    const val GRANT_NOT_IN_LAUNCH = "not in the launch"
    const val GRANT_NONE = "none held"
    const val GRANT_PERSISTED = "the shell's own persisted grant"

    /** The characters a plain library address's path may hold: no escape, no query, no fragment, nothing to decode. */
    private val PLAIN_PATH = Regex("[A-Za-z0-9._~/-]*")

    /**
     * THE LAUNCH GRANT (the class comment has its conditions and the platform's basis): whether the grant the platform
     * made the shell for THIS launch admits [uri] in [mode] — [GRANT_HELD] admits; [GRANT_NOT_LIBRARY],
     * [GRANT_NOT_IN_LAUNCH], [GRANT_NONE], [GRANT_PERSISTED] and `threw <ExceptionClass>` refuse. Null when the launch
     * intent carries no grant flag of [mode]: the platform checked nobody for it, and nothing more is asked.
     */
    fun launchGrant(access: UriAccessPort, uri: ContentUriText, mode: Int): String? {
        val flags = when (val answer = access.launchFlags()) {
            is Platform.Said -> answer.value
            is Platform.Threw -> return "threw ${className(answer.exception)}"
        }
        if ((flags and mode) == 0) return null
        val authority = uri.plainAuthority
        if (uri.scheme != "content" || authority == null || !PLAIN_PATH.matches(pathOf(uri.text, authority))) return GRANT_NOT_LIBRARY
        val data = when (val answer = access.launchData()) {
            is Platform.Said -> answer.value
            is Platform.Threw -> return "threw ${className(answer.exception)}"
        }
        val clip = when (val answer = access.launchClipUris()) {
            is Platform.Said -> answer.value
            is Platform.Threw -> return "threw ${className(answer.exception)}"
        }
        if (uri.text != data && uri.text !in clip) return GRANT_NOT_IN_LAUNCH
        val provider = said(access.provider(authority))
        if (provider == null || !provider.forceUriPermissions) return GRANT_NOT_LIBRARY
        val shell = access.shellUid()
        if (shell < 0 || said(access.holdsGrant(uri.text, shell, mode)) != GRANTED) return GRANT_NONE
        val persisted = when (val answer = access.persistedGrants()) {
            is Platform.Said -> answer.value
            is Platform.Threw -> return "threw ${className(answer.exception)}"
        }
        if (persisted.any { covers(it, authority, uri.text) }) return GRANT_PERSISTED
        return GRANT_HELD
    }

    /** What follows `content://<authority>` in [text]. */
    private fun pathOf(text: String, authority: String): String = text.substring("content://".length + authority.length)

    /**
     * Whether the persisted grant [held] may cover the plain library address [text] of [authority]: the same authority
     * (any case) and a path above or equal to its path, segment by segment — or, when [held]'s path is not plain, any
     * address of that authority (the rule does not decode; a persisted grant may be a prefix one).
     */
    private fun covers(held: String, authority: String, text: String): Boolean {
        val grant = ContentUriText.parse(held)
        val scheme = grant.scheme ?: return false
        val heldAuthority = grant.authority ?: return false
        if (!scheme.equals("content", ignoreCase = true) || !heldAuthority.equals(authority, ignoreCase = true)) return false
        val heldPath = held.substring(scheme.length + "://".length + heldAuthority.length)
        if (!PLAIN_PATH.matches(heldPath)) return true
        val above = heldPath.split('/').filter { it.isNotEmpty() }
        val asked = pathOf(text, authority).split('/').filter { it.isNotEmpty() }
        return asked.size >= above.size && asked.subList(0, above.size) == above
    }

    /** `launch grant <read|write>: <word>`. */
    fun grantLine(mode: Int, word: String): String = "launch grant ${if (mode == WRITE) "write" else "read"}: $word"

    /** The provider's own answer: asked only of a forceUriPermissions provider, and only where the platform can ask it. */
    private fun providerItselfSays(access: UriAccessPort, provider: ProviderFacts?, uri: ContentUriText, uid: Int, mode: Int): Boolean =
        provider != null && provider.forceUriPermissions && access.apiLevel >= API_ASKS_PROVIDER &&
            said(access.providerSays(uri.text, uid, mode)) == GRANTED

    private fun launchWord(access: UriAccessPort, uri: ContentUriText, mode: Int): String {
        if (access.apiLevel < API_ASKS_PROVIDER) return LAUNCH_NOT_AVAILABLE
        return when (val answer = access.launchAnswer(uri.text, mode)) {
            is Platform.Said -> if (answer.value == GRANTED) LAUNCH_GRANTED else LAUNCH_DENIED
            is Platform.Threw -> "threw ${className(answer.exception)}"
        }
    }

    /** An exception's class name as a line may carry it. */
    fun className(name: String): String = name.filter { it.isLetterOrDigit() || it == '_' }.take(60).ifEmpty { "Throwable" }

    /** An authority as a line may carry it: the characters an authority has, at most 80 — no path, no query (A2-S). */
    fun lineAuthority(authority: String?): String = authority.orEmpty().filter { it.isLetterOrDigit() || it in ".-_@" }.take(80)

    /** `launch answer <read|write>: <outcome class>` — the line a device run reads to see what the platform answered. */
    fun launchLine(mode: Int, word: String): String = "launch answer ${if (mode == WRITE) "write" else "read"}: $word"

    private fun <T> said(answer: Platform<T>): T? = (answer as? Platform.Said<T>)?.value
}
