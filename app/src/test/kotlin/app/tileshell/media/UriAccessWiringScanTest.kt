package app.tileshell.media

import app.tileshell.media.SourceScan.body
import app.tileshell.media.SourceScan.call
import app.tileshell.media.SourceScan.count
import app.tileshell.media.SourceScan.mutate
import app.tileshell.media.SourceScan.read
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 trust fixes, round 3 (A2-F3, C2-M1): the Android side of the three places another app can make the shell
 * touch a URI it names — the capture answer, the exported viewer, the exported player — read from the SOURCES, in the
 * form of `video/TrustWiringScanTest`. The rules are pure and unit-tested; what no unit test executes is the code that
 * hands them the real launch and carries their answer out. So that code holds no logic, and each piece of it is held
 * here to the one form it must have:
 *  - the platform port (`AndroidUriAccess`) is one platform call per method inside the fail-closed `ask`, and nothing
 *    else in the shell asks the platform who started an activity or who may reach a URI;
 *  - each activity hands the rule the REAL launch (never a constant) and does exactly what the rule's answer says: a
 *    refused URI is never opened, the refused branch finishes and returns, `mayChange` is the rule's and has no default;
 *  - both location strips are called, and a capture is written to a caller only through the strip-then-write order;
 *  - every process builds its write layer through `ShellMediaWrites.of` (one layer, one ledger file per process);
 *  - (phase 18) Music's play extra is weighed by `MusicPlayExtra.decide` against the uid that sent THAT intent, and the
 *    uid has three sources and no other: the port for the launch, the platform's own caller for a new intent on API
 *    35+, and nobody below it.
 *  - (ledger L18-1) a controller's item reaches Music's player only as `MusicItemRule` says — a URI is kept for the
 *    shell's own controller and for nobody else — and PLAY_FILE is the shell's alone, through the provider's own rule.
 *  - (ledger L18-4) every list the session hands the player starts where `MusicQueueStart` says — an index the final
 *    list has — or the request is refused before the player is touched.
 *  - (phase 20, r3 D1) a station's or a home-server track's item is built by its ONE builder and nowhere else under
 *    `music/`, after its URL rule accepted the address; the session finds a station only for the shell's own
 *    controller (`RadioSearchRule`); and items reach a player under `music/` only at the sites held here.
 * Each check has its twin: the same source with the site changed (the reviewers' surviving mutations, applied to a copy
 * in memory) must be caught, so a clean result is never an empty one. That the device does what the source says is
 * still the device legs'.
 */
class UriAccessWiringScanTest {
    // ---------------------------------------------------------------------------------------------- the platform port

    /** The whole of `AndroidUriAccess`, as code. A change to the port is a change to this text, made knowingly. */
    private val portForm = listOf(
        "class AndroidUriAccess(private val activity: Activity) : UriAccessPort {",
        "override val apiLevel: Int get() = Build.VERSION.SDK_INT",
        "override fun shellUid(): Int = Process.myUid()",
        "override fun uidOf(packageName: String): Platform<Int> = ask { activity.packageManager.getPackageUid(packageName, 0) }",
        "override fun provider(authority: String): Platform<ProviderFacts?> = ask { activity.packageManager.resolveContentProvider(authority, 0)?.let { ProviderFacts(it.applicationInfo.uid, it.forceUriPermissions) } }",
        "override fun holdsGrant(uri: String, uid: Int, modeFlag: Int): Platform<Int> = ask { activity.checkUriPermission(Uri.parse(uri), -1, uid, modeFlag) }",
        "override fun providerSays(uri: String, uid: Int, modeFlag: Int): Platform<Int> {",
        "if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return Platform.Threw(BELOW_API_35)",
        "return ask { activity.checkContentUriPermissionFull(Uri.parse(uri), -1, uid, modeFlag) } }",
        "override fun launchAnswer(uri: String, modeFlag: Int): Platform<Int> {",
        "if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return Platform.Threw(BELOW_API_35)",
        "return ask { activity.initialCaller.checkContentUriPermission(Uri.parse(uri), modeFlag) } }",
        "override fun launchedFromUid(): Platform<Int> = ask { activity.launchedFromUid }",
        "override fun nameOfUid(uid: Int): Platform<String?> = ask { activity.packageManager.getNameForUid(uid) }",
        "private inline fun <T> ask(question: () -> T): Platform<T> = try { Platform.Said(question()) } catch (e: Throwable) { Platform.Threw(e.javaClass.simpleName) }",
        "private companion object { const val BELOW_API_35 = \"BelowApi35\" } }",
    ).joinToString(" ")

    private fun portProblems(port: String): List<String> {
        val at = port.indexOf("class AndroidUriAccess(")
        val actual = if (at < 0) "" else port.substring(at).trim()
        return if (actual == portForm) emptyList() else listOf("AndroidUriAccess is not its one form:\n  is:     $actual\n  must be: $portForm")
    }

    /** A platform read about who started an activity, or who may reach a URI, in code (a property read or a call). */
    private val platformReads = Regex(
        "\\blaunchedFromUid\\b(?!\\s*\\()|getLaunchedFromUid|launchedFromPackage|getLaunchedFromPackage|\\binitialCaller\\b|getInitialCaller|\\bcurrentCaller\\b|getCurrentCaller|" +
            "checkContentUriPermission|checkUriPermission|checkCallingUriPermission|checkCallingOrSelfUriPermission|enforceUriPermission",
    )

    private fun platformReadProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        for ((file, text) in sources) {
            if (file == "media/AndroidUriAccess.kt") continue
            platformReads.findAll(text).forEach { problems += "$file reads ${it.value} itself: only media/AndroidUriAccess.kt asks the platform" }
        }
        val made = sources.mapValues { (_, text) -> count(text, "AndroidUriAccess(this)") }.filterValues { it > 0 }
        // Phase 18: Music is the fourth activity another app can start with something to weigh (its play extra); it
        // makes the port once, for the launch's caller (`musicProblems` holds that one expression).
        // Ledger L18-2: the AlarmClock API handler is the fifth — it makes the port once, for `AlarmApiRules.parse`, which
        // asks whether the app that started it could read the ringtone it names (`clock/AlarmSoundWiringScanTest` holds
        // that one expression).
        // Ledger L18-3: ClockActivity is the sixth — it makes the port once, for the launch's caller, whom its edit bundle is
        // weighed against (`clock/AlarmSoundWiringScanTest` holds that one expression).
        val makers = mapOf("camera/CaptureActivity.kt" to 1, "photos/ViewerActivity.kt" to 1, "video/PlayerActivity.kt" to 2, "music/MusicActivity.kt" to 1, "clock/AlarmApiActivity.kt" to 1, "clock/ClockActivity.kt" to 1)
        if (made != makers) problems += "the port is made other than by the six activities, each for itself: $made"
        val all = sources.filterKeys { it != "media/AndroidUriAccess.kt" }.values.sumOf { Regex("(?<!class )AndroidUriAccess\\(").findAll(it).count() }
        if (all != 7) problems += "AndroidUriAccess is constructed $all times, not the seven of the six activities"
        return problems
    }

    @Test fun `the platform port is one platform call per method inside the fail-closed ask, and nothing else`() {
        assertEquals(emptyList<String>(), portProblems(read("media/AndroidUriAccess.kt")))
    }

    @Test fun `a port that fails open, compares, inverts, asks another mode or answers with a constant is caught`() {
        val port = read("media/AndroidUriAccess.kt")
        fun with(old: String, new: String) = portProblems(mutate(port, old, new))
        // The reviewers' surviving mutations: an answer of yes on an exception (the launch answer, the provider question) …
        assertTrue(with("catch (e: Throwable) { Platform.Threw(e.javaClass.simpleName) }", "catch (e: Throwable) { Platform.Said(0 as T) }").isNotEmpty())
        assertTrue(with("return ask { activity.initialCaller.checkContentUriPermission(Uri.parse(uri), modeFlag) }", "return runCatching { activity.initialCaller.checkContentUriPermission(Uri.parse(uri), modeFlag) }.fold({ Platform.Said(it) }, { Platform.Said(0) })").isNotEmpty())
        assertTrue(with("return ask { activity.checkContentUriPermissionFull(Uri.parse(uri), -1, uid, modeFlag) }", "return Platform.Said(0)").isNotEmpty())
        assertTrue(with("catch (e: Throwable)", "catch (e: Exception)").isNotEmpty())
        // … the grant check asking WRITE, or inverted, or about the shell's own uid …
        assertTrue(with("activity.checkUriPermission(Uri.parse(uri), -1, uid, modeFlag)", "activity.checkUriPermission(Uri.parse(uri), -1, uid, 2)").isNotEmpty())
        assertTrue(with("activity.checkUriPermission(Uri.parse(uri), -1, uid, modeFlag)", "if (activity.checkUriPermission(Uri.parse(uri), -1, uid, modeFlag) == 0) -1 else 0").isNotEmpty())
        assertTrue(with("activity.checkUriPermission(Uri.parse(uri), -1, uid, modeFlag)", "activity.checkUriPermission(Uri.parse(uri), -1, Process.myUid(), modeFlag)").isNotEmpty())
        // … the activity's own uid passed as the launcher, the launcher a constant, the API gate turned round or dropped.
        assertTrue(with("ask { activity.launchedFromUid }", "ask { Process.myUid() }").isNotEmpty())
        assertTrue(with("ask { activity.launchedFromUid }", "Platform.Said(-1)").isNotEmpty())
        assertTrue(with("override fun shellUid(): Int = Process.myUid()", "override fun shellUid(): Int = activity.launchedFromUid").isNotEmpty())
        assertTrue(with("override val apiLevel: Int get() = Build.VERSION.SDK_INT", "override val apiLevel: Int get() = 34").isNotEmpty())
        assertTrue(with("it.forceUriPermissions", "true").isNotEmpty())
    }

    @Test fun `only the port asks the platform who started an activity and who may reach a URI`() {
        val sources = SourceScan.all()
        assertTrue("the scan read the sources (${sources.size} files)", sources.size > 200)
        assertEquals(emptyList<String>(), platformReadProblems(sources))
    }

    @Test fun `an activity that reads the launcher or a URI permission itself, or a port made elsewhere, is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = platformReadProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        assertTrue(with("photos/ViewerActivity.kt", "nav.open(intent, AndroidUriAccess(this))", "nav.open(intent, AndroidUriAccess(this)); val who = launchedFromUid").isNotEmpty())
        assertTrue(with("video/PlayerActivity.kt", "queue = launch.queue", "queue = if (launchedFromUid == android.os.Process.myUid()) launch.queue else null").isNotEmpty())
        assertTrue(with("camera/CaptureActivity.kt", "decision = outcome.decision", "decision = outcome.decision; initialCaller").isNotEmpty())
        assertTrue(with("photos/ViewerActivity.kt", "nav.open(intent, AndroidUriAccess(this))", "nav.open(intent, AndroidUriAccess(this)); checkUriPermission(intent.data, -1, 0, 1)").isNotEmpty())
        assertTrue(with("photos/PhotosActivity.kt", "super.onCreate(savedInstanceState)", "super.onCreate(savedInstanceState); AndroidUriAccess(this)").isNotEmpty())
        // Phase 18: Music reading who launched it itself (the form this scan first caught), a second port made there,
        // and the rule's parameter named back into a platform read.
        assertTrue(with("music/MusicActivity.kt", "MusicPlayExtra.launchCaller(AndroidUriAccess(this))", "launchedFromUid").isNotEmpty())
        assertTrue(with("music/MusicActivity.kt", "MusicPlayExtra.launchCaller(AndroidUriAccess(this))", "getLaunchedFromUid()").isNotEmpty())
        assertTrue(with("music/MusicActivity.kt", "super.onNewIntent(intent, caller)", "super.onNewIntent(intent, caller); AndroidUriAccess(this)").isNotEmpty())
        assertTrue(with("music/MusicActivity.kt", "takePlay(intent, caller.uid)", "takePlay(intent, currentCaller.uid)").isNotEmpty())
        assertTrue(with("music/MusicService.kt", "val me = Process.myUid()", "val me = Process.myUid(); AndroidUriAccess(null!!)").isNotEmpty())
        // Ledger L18-2: the AlarmClock handler's one port — a second made there, one made by the exported editor or by
        // the ring, the handler reading who started it itself, or its port left out.
        assertTrue(with("clock/AlarmApiActivity.kt", "access = AndroidUriAccess(this),", "access = AndroidUriAccess(this).also { AndroidUriAccess(this).launchedFromUid() },").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "access = AndroidUriAccess(this),", "access = null,").isNotEmpty())
        assertTrue(with("clock/AlarmApiActivity.kt", "val caller = ClockIntents.token(", "val who = launchedFromUid val caller = ClockIntents.token(").isNotEmpty())
        assertTrue(with("clock/ClockActivity.kt", "open.tab?.let { nav.show(it) }", "open.tab?.let { nav.show(it) } AndroidUriAccess(this)").isNotEmpty())
        assertTrue(with("clock/RingService.kt", "AlarmRingtoneRules.sinkRefusal(uri)", "AlarmRingtoneRules.sinkRefusal(uri.also { checkUriPermission(Uri.parse(it), -1, 0, 1) })").isNotEmpty())
    }

    // ------------------------------------------------------------------------------------------- Music's play extra

    /**
     * Phase 18 (r3 D5, "below Q-18-2"): MusicActivity is exported and its play extra starts playback, so the extra is
     * honoured only when the uid that sent THAT intent is the shell's own. `MusicPlayExtra.decide` is the rule
     * (`MusicPlayExtraTest`); held here is what no unit test runs — where the uid it is handed comes from:
     *  - the launch: the platform port, through `MusicPlayExtra.launchCaller` (no name, a negative answer or a throw is
     *    [MusicPlayExtra.NO_CALLER]);
     *  - a new intent on API 35+: the `ComponentCaller` the platform hands `onNewIntent`, its uid and nothing else;
     *  - a new intent below API 35: nobody (the platform does not say who sent it), so its extra is ignored;
     *  - the session's custom command: the controller's uid, as the session reports it.
     */
    private fun musicProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val activity = sources["music/MusicActivity.kt"].orEmpty()
        val extra = sources["music/MusicPlayExtra.kt"].orEmpty()
        val service = sources["music/MusicService.kt"].orEmpty()
        // The three calls, each with its one source of the caller's uid; no fourth.
        if (count(activity, "takePlay(") != 4 || !activity.contains("private fun takePlay(intent: Intent?, callerUid: Int) {")) problems += "the play extra is taken other than by the three calls of takePlay(intent, callerUid)"
        if (!activity.contains("if (savedInstanceState == null) takePlay(intent, MusicPlayExtra.launchCaller(AndroidUriAccess(this)))")) problems += "the launch's play extra is not weighed against the port's answer, on a fresh launch only"
        if (!activity.contains("override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) takePivot(intent) if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) takePlay(intent, Process.INVALID_UID) }")) {
            problems += "below API 35 a new intent's play extra is given a caller"
        }
        if (!activity.contains("@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) override fun onNewIntent(intent: Intent, caller: ComponentCaller) { super.onNewIntent(intent, caller) takePlay(intent, caller.uid) }")) {
            problems += "on API 35+ a new intent's play extra is not weighed against that intent's own caller"
        }
        // The uid goes to the rule untouched, beside the shell's real uid, and nothing plays before the rule answers.
        val take = body(activity, "private fun takePlay(intent: Intent?, callerUid: Int)")
        val decided = "when (val decision = MusicPlayExtra.decide(callerUid, Process.myUid(), id, uri, FilesProvider.AUTHORITY)) {"
        val at = take.indexOf(decided)
        if (at < 0 || count(activity, "MusicPlayExtra.decide(") != 1) problems += "the activity's play extra is not decided by MusicPlayExtra.decide(callerUid, Process.myUid(), …)"
        if (Regex("\\bcallerUid\\b").findAll(activity).count() != 2) problems += "the caller's uid is read, compared or replaced outside the rule's call"
        if (Regex("\\.uid\\b").findAll(activity).count() != 1 || count(activity, "myUid(") != 1 || count(activity, "INVALID_UID") != 1) problems += "a uid is read in the activity outside the three calls and the rule's"
        for (played in listOf("MusicPlayer.play(listOf(track), 0)", "MusicPlayer.playFile(decision.uri)")) {
            if (count(activity, played) != 1 || at < 0 || take.indexOf(played) < at) problems += "$played is reached other than by the rule's answer"
        }
        if (count(activity, "MusicPlayer.playFile(") != 1 || !take.contains("is MusicPlayExtra.Decision.PlayUri -> { MusicPlayer.playFile(decision.uri)") ||
            !take.contains("is MusicPlayExtra.Decision.PlayId -> { val track = MusicStore.library.value.firstOrNull { it.id == decision.id }")
        ) problems += "what is played is not what the rule's answer named"
        // The platform's per-intent caller: only that override, only its uid.
        val callers = sources.mapValues { (_, text) -> count(text, "ComponentCaller") }.filterValues { it > 0 }
        // Ledger L18-3: ClockActivity's onNewIntent is the second, in the same form (its edit bundle is the shell's alone).
        val clock = sources["clock/ClockActivity.kt"].orEmpty()
        if (callers != mapOf("music/MusicActivity.kt" to 2, "clock/ClockActivity.kt" to 2)) problems += "a ComponentCaller is used outside Music's and Clock's one onNewIntent each: $callers"
        if (Regex("\\bcaller\\b").findAll(activity).count() != 3 || Regex("\\bcaller\\b").findAll(clock).count() != 3) problems += "the new intent's caller is used for something other than its uid"
        // The rule's side: the port's answer or nobody, and the caller weighed before the extra's contents are looked at.
        val asked = sources.mapValues { (_, text) -> count(text, "launchCaller(") }.filterValues { it > 0 }
        if (asked != mapOf("music/MusicActivity.kt" to 1, "music/MusicPlayExtra.kt" to 1, "clock/ClockActivity.kt" to 1, "clock/ClockIntents.kt" to 1) ||
            !sources["clock/ClockIntents.kt"].orEmpty().contains("fun launchCaller(access: UriAccessPort): Int = try { UriAccessRules.starterUid(access) ?: NO_CALLER } catch (e: Throwable) { NO_CALLER }") ||
            !sources["clock/ClockIntents.kt"].orEmpty().contains("const val NO_CALLER = -1") ||
            !extra.contains("fun launchCaller(access: UriAccessPort): Int = UriAccessRules.starterUid(access) ?: NO_CALLER") || !extra.contains("const val NO_CALLER = -1")
        ) problems += "the launch's caller is not the port's named starter, or nobody: $asked"
        val decide = body(extra, "fun decide(callerUid: Int, myUid: Int, id: Long?, uri: String?, providerAuthority: String): Decision")
        if (!decide.startsWith("{ if (id == null && uri == null) return Decision.None if (callerUid != myUid) return Decision.Ignored(WHY_NOT_SHELL) ") || Regex("\\bcallerUid\\b").findAll(decide).count() != 1) {
            problems += "the rule does not refuse every caller but the shell before it looks at the extra"
        }
        // The other caller of the rule: the session's command, weighed against the controller the session names.
        val deciders = sources.mapValues { (_, text) -> count(text, "MusicPlayExtra.decide(") }.filterValues { it > 0 }
        if (deciders != mapOf("music/MusicActivity.kt" to 1, "music/MusicService.kt" to 1) ||
            !body(service, "private fun playFile(controller: MediaSession.ControllerInfo, raw: String?): Int").startsWith(
                "{ val me = Process.myUid() val decision = MusicPlayExtra.decide(controller.uid, me, null, raw.orEmpty(), FilesProvider.AUTHORITY) if (decision !is MusicPlayExtra.Decision.PlayUri) { ",
            )
        ) problems += "the rule is asked somewhere else, or the session's command is not weighed against its controller: $deciders"
        return problems
    }

    @Test fun `Music's play extra is weighed against the uid that sent that intent - the port's for the launch, the intent's own caller on API 35+, nobody below`() {
        assertEquals(emptyList<String>(), musicProblems(SourceScan.all()))
    }

    @Test fun `a play extra honoured for a caller the platform did not name, or a caller's uid read or compared outside the rule, is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = musicProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        fun activity(old: String, new: String) = with("music/MusicActivity.kt", old, new)
        // The launch's caller a constant, the shell's own uid, or weighed on a re-creation too.
        assertTrue(activity("takePlay(intent, MusicPlayExtra.launchCaller(AndroidUriAccess(this)))", "takePlay(intent, Process.myUid())").isNotEmpty())
        assertTrue(activity("takePlay(intent, MusicPlayExtra.launchCaller(AndroidUriAccess(this)))", "takePlay(intent, 10077)").isNotEmpty())
        assertTrue(activity("if (savedInstanceState == null) takePlay(intent, MusicPlayExtra", "takePlay(intent, MusicPlayExtra").isNotEmpty())
        // A new intent below API 35 given the LAUNCH's caller (the form the stricter rule replaced), or the gate dropped.
        assertTrue(activity("takePlay(intent, Process.INVALID_UID)", "takePlay(intent, MusicPlayExtra.launchCaller(AndroidUriAccess(this)))").isNotEmpty())
        assertTrue(activity("takePlay(intent, Process.INVALID_UID)", "takePlay(intent, Process.myUid())").isNotEmpty())
        assertTrue(activity("if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) takePlay(intent, Process.INVALID_UID)", "takePlay(intent, Process.INVALID_UID)").isNotEmpty())
        // The per-intent caller moved out of its expression: another uid in its place, compared in the activity, or kept.
        assertTrue(activity("takePlay(intent, caller.uid)", "takePlay(intent, Process.myUid())").isNotEmpty())
        assertTrue(activity("takePlay(intent, caller.uid)", "takePlay(intent, if (caller.uid >= 0) Process.myUid() else -1)").isNotEmpty())
        assertTrue(activity("takePlay(intent, caller.uid)", "takePlay(intent, caller.uid); lastCaller = caller").isNotEmpty())
        assertTrue(activity("super.onNewIntent(intent, caller) takePlay(intent, caller.uid)", "super.onNewIntent(intent, caller)").isNotEmpty())
        // The rule handed something other than the caller and the shell's real uid; the compare made in the activity.
        assertTrue(activity("MusicPlayExtra.decide(callerUid, Process.myUid(), id, uri,", "MusicPlayExtra.decide(Process.myUid(), Process.myUid(), id, uri,").isNotEmpty())
        assertTrue(activity("MusicPlayExtra.decide(callerUid, Process.myUid(), id, uri,", "MusicPlayExtra.decide(callerUid, callerUid, id, uri,").isNotEmpty())
        assertTrue(activity("if (intent == null) return", "if (intent == null) return if (callerUid != Process.myUid()) { MusicPlayer.playFile(intent.getStringExtra(MusicPlayExtra.EXTRA_PLAY_URI).orEmpty()); return }").isNotEmpty())
        assertTrue(activity("if (intent == null) return", "if (intent == null) return MusicPlayer.playFile(intent.getStringExtra(MusicPlayExtra.EXTRA_PLAY_URI).orEmpty())").isNotEmpty())
        assertTrue(activity("MusicPlayer.playFile(decision.uri)", "MusicPlayer.playFile(uri.orEmpty())").isNotEmpty())
        // The rule's side: an unnamed starter taken for the shell, the caller compared the wrong way or after the extra.
        assertTrue(with("music/MusicPlayExtra.kt", "UriAccessRules.starterUid(access) ?: NO_CALLER", "UriAccessRules.starterUid(access) ?: access.shellUid()").isNotEmpty())
        assertTrue(with("music/MusicPlayExtra.kt", "const val NO_CALLER = -1", "const val NO_CALLER = 10077").isNotEmpty())
        assertTrue(with("music/MusicPlayExtra.kt", "if (callerUid != myUid) return Decision.Ignored(WHY_NOT_SHELL)", "if (callerUid == myUid) return Decision.Ignored(WHY_NOT_SHELL)").isNotEmpty())
        assertTrue(with("music/MusicPlayExtra.kt", "if (callerUid != myUid) return Decision.Ignored(WHY_NOT_SHELL)", "if (callerUid < 0) return Decision.Ignored(WHY_NOT_SHELL)").isNotEmpty())
        assertTrue(with("music/MusicPlayExtra.kt", "if (callerUid != myUid) return Decision.Ignored(WHY_NOT_SHELL) ", "").isNotEmpty())
        // The session's command weighed against the shell itself, and a third asker of the rule.
        assertTrue(with("music/MusicService.kt", "MusicPlayExtra.decide(controller.uid, me, null,", "MusicPlayExtra.decide(me, me, null,").isNotEmpty())
        assertTrue(musicProblems(sources + ("music/MusicSearch.kt" to sources.getValue("music/MusicSearch.kt") + " fun x(u: String) = MusicPlayExtra.decide(1, 1, null, u, \"a\")")).isNotEmpty())
        assertTrue(musicProblems(sources + ("files/FilesOpenWith.kt" to sources.getValue("files/FilesOpenWith.kt") + " fun x(c: android.app.ComponentCaller) = c.uid")).isNotEmpty())
    }

    // ------------------------------------------------------------------------------------- Music's session (L18-1)

    /**
     * The whole of `fromLibrary`, as code: every item of every controller is the rule's to decide, and nothing else's —
     * and what each item became is kept apart (one list per item), so the start of a set can be re-derived (L18-4).
     */
    private val partsForm = "{ val lib by lazy { library() } var notKept = 0 val parts = mediaItems.map { item -> " +
        "val hasUri = item.localConfiguration != null " +
        "val decision = MusicItemRule.decide(controller.uid, Process.myUid(), item.mediaId, hasUri, item.requestMetadata.searchQuery != null) " +
        "if (hasUri && decision != MusicItemRule.Decision.Keep) notKept++ " +
        "when (decision) { " +
        "MusicItemRule.Decision.Keep -> listOf(item) " +
        "MusicItemRule.Decision.Search -> MusicSearch.resolve(item.requestMetadata.searchQuery.orEmpty(), lib)?.queue.orEmpty().map { mediaItem(it) } " +
        "is MusicItemRule.Decision.Rebuild -> lib.firstOrNull { it.id == decision.id }?.let { listOf(mediaItem(it)) }.orEmpty() " +
        "MusicItemRule.Decision.Drop -> emptyList() } } " +
        "if (notKept > 0) Diagnostics.add(\"music\", \"controller uid \${controller.uid}: \$notKept item(s) came with a uri of their own, none used (\${parts.sumOf { it.size }} from the library)\") " +
        "return parts }"

    /** The whole of `onAddMediaItems`: the rule's items, refused before the player is touched when nothing is left (L18-4). */
    private val addItemsForm = "{ val parts = fromLibrary(controller, mediaItems) " +
        "if (!MusicQueueStart.mayAdd(parts.map { it.size })) return Futures.immediateFailedFuture(UnsupportedOperationException(\"nothing to add\")) " +
        "return Futures.immediateFuture(parts.flatten().toMutableList()) }"

    /**
     * The whole of `onSetMediaItems` (L18-4; phase 20, r3 D1 / D6): a search, else the rule's items — and in both the
     * index and the position the player is handed are `MusicQueueStart`'s, worked out from what each item BECAME, never
     * the request's own. The search is the ONE resolver's, handed the stations only as `RadioSearchRule.stationsFor`
     * gives them for THIS controller's uid beside the shell's real one; a station match goes to `startedStation` and
     * nowhere else, and a library match is the library's tracks as before.
     */
    private val setItemsForm = "{ val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery if (query != null) { " +
        "val match = MusicSearch.resolve(query, library(), RadioSearchRule.stationsFor(controller.uid, Process.myUid(), ::radioStations)) " +
        "if (match == null) { Diagnostics.add(\"music\", \"search \\\"\${MusicQueueStart.lineQuery(query)}\\\": nothing in the library\") " +
        "return Futures.immediateFailedFuture(UnsupportedOperationException(\"no match in the library\")) } " +
        "match.station?.let { return startedStation(query, it) } " +
        "Diagnostics.add(\"music\", \"search \\\"\${MusicQueueStart.lineQuery(query)}\\\": \${match.kind.name.lowercase()} \${match.label}, \${match.queue.size} track(s)\") " +
        "return started(match.queue.map { mediaItem(it) }, MusicQueueStart.search(match.queue.size, match.startIndex)) } " +
        "val parts = fromLibrary(controller, mediaItems) " +
        "val start = MusicQueueStart.set(parts.map { it.size }, startIndex, startPositionMs) " +
        "MusicQueueStart.line(controller.uid, parts.map { it.size }, startIndex, start)?.let { Diagnostics.add(\"music\", it) } " +
        "return started(parts.flatten(), start) }"

    /**
     * The whole of `startedStation` (phase 20, r3 D1 / D15): the queue `StationStart` plans — the network's answer, the
     * station's own address, the favourites round it — built by `stationItems` (which is `StationItem.build` and
     * nothing else), and started where `MusicQueueStart.search` says for the list that was BUILT.
     */
    private val stationForm = "{ val plan = StationStart.plan(RadioNet.gate(this@MusicService, isStation = true), " +
        "RadioFavourites.queueFor(station, RadioFavouritesStore.get(this@MusicService).stations()), RadioNet.qaHost(this@MusicService)) " +
        "Diagnostics.add(\"music\", \"search \\\"\${MusicQueueStart.lineQuery(query)}\\\": station \${RadioText.shown(station.name, RadioText.NAME_MAX)}\") " +
        "plan.lines.forEach { Diagnostics.add(StreamLine.TAG, it) } " +
        "val items = stationItems(this@MusicService, plan.stations) " +
        "return started(items, MusicQueueStart.search(items.size, plan.start)) }"

    /** The whole of `stationItems`: each station's item from `StationItem.build`, with the debug override's host and no logo — or no item. */
    private val stationItemsForm = "{ val qaHost = RadioNet.qaHost(context) " +
        "return stations.mapNotNull { (StationItem.build(it, qaHost) as? StationItem.Built.Item)?.item } }"

    /** The whole of `RadioSearchRule`, as code: the shell's own uid and nobody else's — and a stranger's stations are never even asked for. */
    private val searchRuleForm = "object RadioSearchRule { " +
        "fun stationsFor(controllerUid: Int, myUid: Int): Boolean = myUid >= 0 && controllerUid == myUid " +
        "fun stationsFor(controllerUid: Int, myUid: Int, all: () -> MusicSearch.Stations): MusicSearch.Stations = " +
        "if (stationsFor(controllerUid, myUid)) all() else MusicSearch.Stations.NONE }"

    /**
     * `StationItem.build` from its first line to the item: no item without an accepted plan, and the item's id and
     * address are that plan's — [StationItem.plan]'s, which is `StationUrl.playable` then `StationUrl.accept`.
     */
    private val stationBuildForm = "{ val play = when (val p = plan(station, qaHost)) { is Plan.Refused -> return Built.Refused(p.line) is Plan.Play -> p } " +
        "val meta = MediaMetadata.Builder() .setAlbumTitle(play.name) .setArtist(play.name) .setIsBrowsable(false) .setIsPlayable(true) " +
        "logo?.let { meta.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } " +
        "val item = MediaItem.Builder() .setMediaId(play.mediaId) .setUri(MusicSources.own.queued(play.url)) .setMediaMetadata(meta.build()) " +
        "play.mimeType?.let { item.setMimeType(it) } return Built.Item(item.build()) }"

    /** The whole of `StationItem.plan`: the one address the directory's row gives, and only when the URL rule accepts it. */
    private val stationPlanForm = "{ val stream = when (val p = StationUrl.playable(station.urlResolved, station.url, station.hls)) { " +
        "StationUrl.Playable.Playlist -> return Plan.Refused(StreamLine.UNSUPPORTED_PLAYLIST) " +
        "StationUrl.Playable.None -> return Plan.Refused(StreamLine.unsupportedScheme(null)) " +
        "is StationUrl.Playable.Stream -> p } " +
        "return when (val a = StationUrl.accept(stream.url, qaHost)) { " +
        "is StationUrl.Accept.UnsupportedScheme -> Plan.Refused(StreamLine.unsupportedScheme(a.scheme)) " +
        "StationUrl.Accept.UnsupportedHost -> Plan.Refused(StreamLine.UNSUPPORTED_HOST) " +
        "StationUrl.Accept.Ok -> Plan.Play( mediaId = MusicLive.stationId(station.uuid), url = stream.url, mimeType = stream.mimeType, name = RadioText.shown(station.name, RadioText.NAME_MAX), ) } }"

    /** `ServerTrackItem.build(url, track)` from its first line to the address: no item unless `accepts` took THAT address. */
    private val serverBuildForm = "{ if (url == null || !accepts(url, track)) return null return MediaItem.Builder() .setMediaId(mediaId(track)) .setUri(MusicSources.own.queued(url)) "

    /** The whole of `started`: the one place a list with a start is made for the player — the rule's index and position, or a refusal. */
    private val startedForm = "{ return when (start) { " +
        "MusicQueueStart.Start.Refuse -> Futures.immediateFailedFuture(UnsupportedOperationException(\"nothing to play\")) " +
        "is MusicQueueStart.Start.At -> Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, start.index, start.positionMs)) } }"

    /** `playFile` from its first line to the file the provider's own rule resolves: the caller, the URI, then the provider. */
    private val playFileForm = "{ val me = Process.myUid() val decision = MusicPlayExtra.decide(controller.uid, me, null, raw.orEmpty(), FilesProvider.AUTHORITY) " +
        "if (decision !is MusicPlayExtra.Decision.PlayUri) { (decision as? MusicPlayExtra.Decision.Ignored)?.let { Diagnostics.add(\"music\", it.line) } " +
        "return if (controller.uid != me) SessionResult.RESULT_ERROR_PERMISSION_DENIED else SessionResult.RESULT_ERROR_BAD_VALUE } " +
        "val uri = Uri.parse(decision.uri) " +
        "val file = FilesProvider.fileFor(this, uri) ?: run { Diagnostics.add(\"music\", \"play extra ignored: outside shared storage\") return SessionResult.RESULT_ERROR_BAD_VALUE } " +
        "val ask = ++fileAsk Thread({ val item = fileItem(uri, file, ask) handler.post { "

    /**
     * Ledger L18-1 (the adversarial GATE review's H1 and its two surviving mutants): MusicService is exported, so any
     * app's controller reaches the session. `MusicItemRule` is the rule (`MusicItemRuleTest`); held here is what no
     * unit test runs — every way a controller's item, or a URI, can reach the player:
     *  - `onAddMediaItems` (where Media3 sends every set, add and replace of a Media3 controller, and every play-from
     *    and queue request of a legacy one): each item is the rule's to decide, with the controller's uid as the session
     *    reports it beside the shell's real uid, and only the rule's Keep hands an item back as it arrived;
     *  - `onSetMediaItems`: a search answered by the one resolver — from the library, and from the stations only for
     *    the shell's own controller, a station match built by `StationItem` (phase 20, `stationProblems`) — else the
     *    rule's items;
     *  - playback resumption is not answered at all (Media3's default refuses), so it hands the player nothing;
     *  - the PLAY_FILE command is offered only to a controller running as the shell, is weighed again in `playFile`
     *    against that controller's uid, and the file is the one the shell's FileProvider resolves by its own rule;
     *  - nothing else in the service reads an item's URI or sets an item on the player;
     *  - (phase 20, r3 D1) an item gets a URI in the service at its two sites and, anywhere else under `music/`, only in
     *    the two builders (`stationProblems`);
     *  - (phase 20, r3 D11 / D12) each of those two sites tells the data source's guard the address it gives
     *    (`queued(…)`), and the player opens nothing else that is not http(s) (`musicSourceProblems`).
     */
    private fun musicSessionProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val service = sources["music/MusicService.kt"].orEmpty()
        val add = body(service, "override fun onAddMediaItems(")
        if (add != addItemsForm) problems += "onAddMediaItems is not its one form:\n  is:      $add\n  must be: $addItemsForm"
        val parts = body(service, "private fun fromLibrary(controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): List<List<MediaItem>>")
        if (parts != partsForm) problems += "fromLibrary is not its one form:\n  is:      $parts\n  must be: $partsForm"
        val deciders = sources.mapValues { (_, text) -> count(text, "MusicItemRule.decide(") }.filterValues { it > 0 }
        if (deciders != mapOf("music/MusicService.kt" to 1)) problems += "the item rule is asked other than once, by the session: $deciders"
        if (count(service, "localConfiguration") != 1 || service.contains("mediaUri")) problems += "the service reads a controller's URI outside the rule's one question"
        // Ledger L18-4: a set is a library search or the rule's items, and the start the player is handed is always
        // MusicQueueStart's. Media3's default (which hands the request's own index on beside a shorter list) is not used.
        val set = body(service, "override fun onSetMediaItems(")
        if (set != setItemsForm) problems += "onSetMediaItems is not its one form:\n  is:      $set\n  must be: $setItemsForm"
        val started = body(service, "private fun started(items: List<MediaItem>, start: MusicQueueStart.Start): ListenableFuture<MediaSession.MediaItemsWithStartPosition>")
        if (started != startedForm) problems += "started is not its one form:\n  is:      $started\n  must be: $startedForm"
        if (count(service, "MediaItemsWithStartPosition(") != 1 || service.contains("super.onSetMediaItems") || service.contains("super.onAddMediaItems") ||
            count(service, "started(") != 4 || count(service, "fromLibrary(") != 3 ||
            Regex("\\bstartIndex\\b").findAll(service).count() != 4 || Regex("\\bstartPositionMs\\b").findAll(service).count() != 2
        ) problems += "a list reaches the player with a start that MusicQueueStart did not work out"
        val starters = sources.mapValues { (_, text) -> count(text, "MusicQueueStart.set(") + count(text, "MusicQueueStart.search(") + count(text, "MusicQueueStart.mayAdd(") }.filterValues { it > 0 }
        // Phase 20: the fourth is the station queue's start (`startedStation`, whose one form `stationProblems` holds).
        if (starters != mapOf("music/MusicService.kt" to 4)) problems += "the queue's start is asked other than four times, by the session: $starters"
        if (service.contains("onPlaybackResumption")) problems += "playback resumption is answered: what it hands the player is not held here"
        // The player is handed items by the session (above) and by playFile's one-item queue; nothing else.
        if (count(service, "setMediaItems(") != 1 || !service.contains("player.setMediaItems(listOf(item))") || service.contains("setMediaItem(") || service.contains("addMediaItem") || service.contains("replaceMediaItem")) {
            problems += "an item is set on the player outside the session's callbacks and playFile"
        }
        if (count(service, ".setUri(") != 2 || !service.contains(".setUri(queued(MusicStore.uriOf(track)))") || !body(service, "private fun fileItem(uri: Uri, file: File, ask: Long): MediaItem").contains(".setMediaId(MusicFile.mediaId(ask)) .setUri(queued(uri)) ")) {
            problems += "an item is given a URI other than a library track's or playFile's checked one"
        }
        // PLAY_FILE: offered to the shell's own controller only, routed to playFile only, checked there again.
        if (count(service, "playFileCommand") != 2 || !service.contains(".add(crossfadeCommand) .apply { if (controller.uid == Process.myUid()) add(playFileCommand) } .build(), ) .build()")) {
            problems += "PLAY_FILE is offered to a controller that is not the shell's own"
        }
        if (count(service, "MusicCommands.PLAY_FILE") != 2 || count(service, "playFile(") != 2 ||
            !service.contains("MusicCommands.PLAY_FILE -> return Futures.immediateFuture(SessionResult(playFile(controller, args.getString(MusicCommands.ARG_URI))))")
        ) problems += "PLAY_FILE reaches something other than playFile(controller, its uri)"
        val play = body(service, "private fun playFile(controller: MediaSession.ControllerInfo, raw: String?): Int")
        if (!play.startsWith(playFileForm) || count(service, "FilesProvider.fileFor(") != 1 || count(service, "Uri.parse(") != 1 || count(service, "fileItem(") != 2 ||
            Regex("\\bFile\\(").containsMatchIn(service) || service.contains("uri.path") || service.contains("getPath") || service.contains("pathSegments")
        ) problems += "playFile's file is not the one FilesProvider.fileFor resolves from the checked URI"
        problems += stationProblems(sources)
        return problems
    }

    /**
     * Phase 20 (r3 D1; the adversarial review reads this diff): a station's address comes from a community directory and
     * a home-server track's from a saved server, and the player opens what an item names with the SHELL's identity. The
     * URL rules are pure (`StationUrlTest`, `StationItemTest`, `ServerTrackItemTest`, `RadioSearchRuleTest`); held here
     * is what no unit test runs — that nothing reaches a player except through them:
     *  - under `music/` an item is BUILT in four places and no fifth: the service's library track and its checked file,
     *    `music/radio/StationItem.kt` and `music/server/ServerTrackItem.kt`. Those are the only `.setUri(` and the only
     *    `MediaItem.Builder(` there; no item is made from a URI or a bundle; nothing but the service's one question
     *    reads an item's `localConfiguration`; and no item is given an `artworkUri` (r3 D2);
     *  - each builder is its one form: the station's id and address are its accepted plan's — `StationUrl.playable`, then
     *    `StationUrl.accept` with the caller's `qaHost` — and a server track's address is the one `accepts` took;
     *  - `StationItem.build` is called by `MusicService.stationItems` alone, with `RadioNet.qaHost`'s host and nothing
     *    else; `ServerTrackItem.buildAll` by `MusicPlayer.playServerTracks` alone
     *    (off the main thread: it opens the sealed server, once for the list);
     *  - the session's search finds a station only through `RadioSearchRule.stationsFor(controller.uid,
     *    Process.myUid(), …)`, which is its one form; the stranger's path (`fromLibrary`) stays the two-argument,
     *    library-only resolve; a station match is `startedStation`'s one form — `StationStart.plan` over the real
     *    network gate, `stationItems`, `MusicQueueStart.search` over the list that was built;
     *  - items are set on a player under `music/` at four sites: the session's `playFile`, Music's own queue of
     *    library tracks, Music's own queue of the two builders' items, and the crossfade's fader (the primary's own
     *    next item). `MusicPlayer.playStations` is its one form, the gate's refusal returned before anything is built.
     */
    private fun stationProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val service = sources["music/MusicService.kt"].orEmpty()
        val player = sources["music/MusicPlayer.kt"].orEmpty()
        val station = sources["music/radio/StationItem.kt"].orEmpty()
        val server = sources["music/server/ServerTrackItem.kt"].orEmpty()
        val rule = sources["music/radio/RadioSearchRule.kt"].orEmpty()
        val music = sources.filterKeys { it.startsWith("music/") }
        fun where(piece: String, among: Map<String, String> = sources) = among.mapValues { (_, text) -> count(text, piece) }.filterValues { it > 0 }
        fun where(piece: Regex, among: Map<String, String>) = among.mapValues { (_, text) -> piece.findAll(text).count() }.filterValues { it > 0 }

        // The two builders, and no other maker of an item with an address, under music/.
        val four = mapOf("music/MusicService.kt" to 2, "music/radio/StationItem.kt" to 1, "music/server/ServerTrackItem.kt" to 1)
        val uris = where(Regex("\\bsetUri\\s*\\("), music)
        if (uris != four) problems += "an item is given a URI under music/ outside the service's two sites and the two builders: $uris"
        val made = where(Regex("\\bMediaItem\\s*\\.\\s*Builder\\b"), music)
        if (made != four) problems += "a MediaItem is built under music/ outside the service's two sites and the two builders: $made"
        val otherWays = where(Regex("\\bfromUri\\b|\\bfromBundle\\b|\\bsetMediaUri\\b|\\bMediaItem\\s*\\(|\\bMediaItem\\s*\\.\\s*LocalConfiguration\\b|\\bsetArtworkUri\\b"), music)
        if (otherWays.isNotEmpty()) problems += "an item, its address or an artwork address is made another way under music/: $otherWays"
        val read = where(Regex("\\blocalConfiguration\\b"), music)
        if (read != mapOf("music/MusicService.kt" to 1)) problems += "an item's own address is read under music/ outside the item rule's one question: $read"

        // Each builder's one form.
        val build = body(station, "fun build(station: Station, qaHost: String?, logo: ByteArray? = null): Built")
        if (build != stationBuildForm) problems += "StationItem.build is not its one form:\n  is:      $build\n  must be: $stationBuildForm"
        val plan = body(station, "fun plan(station: Station, qaHost: String?): Plan")
        if (plan != stationPlanForm) problems += "StationItem.plan is not its one form:\n  is:      $plan\n  must be: $stationPlanForm"
        if (count(station, "StationUrl.accept(") != 1 || count(station, "StationUrl.playable(") != 1 || count(station, "Plan.Play(") != 1 || count(station, "Built.Item(") != 1) {
            problems += "a station's plan or item is made other than once, from the URL rule's answer"
        }
        if (!body(server, "fun build(url: String?, track: ServerTrack): MediaItem?").startsWith(serverBuildForm) ||
            !server.contains("fun buildAll(server: MediaServer, tracks: List<ServerTrack>): List<MediaItem?> = server.audioStreamUrls(tracks).mapIndexed { i, url -> build(url, tracks[i]) }") || count(server, "accepts(") != 2
        ) problems += "a server track's item is built from an address its rule did not accept"

        // Who asks the builders.
        val stationBuilders = where("StationItem.build")
        if (stationBuilders != mapOf("music/MusicService.kt" to 1) || body(service, "fun stationItems(context: Context, stations: List<Station>): List<MediaItem>") != stationItemsForm) {
            problems += "a station's item is asked for other than by MusicService.stationItems, in its one form: $stationBuilders"
        }
        val serverBuilders = where("ServerTrackItem.build")
        if (serverBuilders != mapOf("music/MusicPlayer.kt" to 1) || !player.contains("val built = ServerTrackItem.buildAll(server, tracks) appContext.mainExecutor.execute { if (ask != serverAsk) return@execute val start = MusicQueueStart.own(built.map { it != null }, startIndex) if (start == null) return@execute done(CANT_PLAY_TRACK)")) {
            problems += "a server track's item is asked for other than by MusicPlayer.playServerTracks: $serverBuilders"
        }
        val itemMakers = where("stationItems(")
        if (itemMakers != mapOf("music/MusicService.kt" to 2, "music/MusicPlayer.kt" to 1)) problems += "station items are made other than for the session's station match and Music's own queue: $itemMakers"

        // The session's search: stations for the shell's own controller only; a match is startedStation's one form.
        val at = rule.indexOf("object RadioSearchRule")
        val ruleIs = if (at < 0) "" else rule.substring(at).trim()
        if (ruleIs != searchRuleForm) problems += "RadioSearchRule is not its one form:\n  is:      $ruleIs\n  must be: $searchRuleForm"
        val askers = where("RadioSearchRule.stationsFor(")
        if (askers != mapOf("music/MusicService.kt" to 1)) problems += "the station-search rule is asked other than once, by the session: $askers"
        if (Regex("\\bradioStations\\b").findAll(service).count() != 2 ||
            !service.contains("private fun radioStations(): MusicSearch.Stations = MusicSearch.Stations(RadioFavouritesStore.get(this).favourites.value) { RadioDirectoryStore.get(this).directory.value.index }")
        ) problems += "the session reaches the stations other than through the rule's one call"
        val resolvers = where("MusicSearch.resolve(")
        val stations = where("MusicSearch.Stations(")
        if (resolvers != mapOf("music/MusicService.kt" to 2, "cortana/action/ActionLayer.kt" to 1) || stations != mapOf("music/MusicService.kt" to 1, "cortana/action/ActionLayer.kt" to 1)) {
            problems += "the resolver is asked, or handed stations, somewhere else: $resolvers $stations"
        }
        val started = body(service, "private fun startedStation(query: String, station: Station): ListenableFuture<MediaSession.MediaItemsWithStartPosition>")
        if (started != stationForm || count(service, "startedStation(") != 2) problems += "startedStation is not its one form:\n  is:      $started\n  must be: $stationForm"
        val planners = where("StationStart.plan(")
        if (planners != mapOf("music/MusicService.kt" to 1, "music/MusicPlayer.kt" to 1, "cortana/action/ActionLayer.kt" to 1)) problems += "a station start is planned somewhere else: $planners"

        // Items reach a player under music/ at these sites and no other.
        val sets = where(Regex("\\b(?:set|add|replace)MediaItems?\\s*\\("), music)
        if (sets != mapOf("music/MusicService.kt" to 1, "music/MusicPlayer.kt" to 2, "music/CrossfadeFader.kt" to 1) ||
            !player.contains("c.setMediaItems(queue.map { MusicService.mediaItem(it) }, startIndex, 0L)") || !player.contains("c.setMediaItems(own.items, own.start, 0L)") ||
            !sources["music/CrossfadeFader.kt"].orEmpty().contains("val next = primary.getMediaItemAt(primary.nextMediaItemIndex)") || !sources["music/CrossfadeFader.kt"].orEmpty().contains("f.setMediaItem(next)")
        ) problems += "an item is set on a player under music/ outside the four sites: $sets"
        if (count(player, "OwnQueue(") != 3 || count(player, "playOwn(") != 4 ||
            !player.contains("playOwn(OwnQueue(items, start, \"station \${plan.stations[start].name}\"))") ||
            !player.contains("playOwn(OwnQueue(built.filterNotNull(), start, MusicQueueStart.lineQuery(tracks[startIndex].title)))")
        ) problems += "Music's own queue is made of something other than the two builders' items"
        val stationsBody = body(player, "fun playStations(context: Context, station: app.tileshell.music.radio.Station): String?")
        if (stationsBody != playStationsForm) problems += "MusicPlayer.playStations is not its one form:\n  is:      $stationsBody\n  must be: $playStationsForm"
        return problems
    }

    /**
     * The whole of `MusicPlayer.playStations` (phase 20, r3 D14 / D15): the network's answer and the station's own
     * address first — a refusal is returned before anything is built — then `stationItems`, the shell's own controller.
     */
    private val playStationsForm = "{ val appContext = context.applicationContext " +
        "val plan = StationStart.plan(RadioNet.gate(appContext, isStation = true), RadioFavourites.queueFor(station, RadioFavouritesStore.get(appContext).stations()), RadioNet.qaHost(appContext)) " +
        "plan.lines.forEach { Diagnostics.add(StreamLine.TAG, it) } " +
        "plan.refusal?.let { return it } " +
        "val items = MusicService.stationItems(appContext, plan.stations) " +
        "val start = plan.start.takeIf { it in items.indices } ?: return StationItem.CANT_PLAY " +
        "connect(appContext) " +
        "playOwn(OwnQueue(items, start, \"station \${plan.stations[start].name}\")) " +
        "return null }"

    @Test fun `phase 20 D1 a station or server item is built by its one builder, searched only for the shell, and set on a player only at the held sites`() {
        val sources = SourceScan.all()
        assertEquals(emptyList<String>(), stationProblems(sources))
        // The clean result is not an empty one: the scan saw the builders, the rule and the three callers.
        for (file in listOf("music/radio/StationItem.kt", "music/server/ServerTrackItem.kt", "music/radio/RadioSearchRule.kt", "music/radio/StreamWatch.kt", "music/MusicPlayer.kt", "music/KnownDurationPlayer.kt", "cortana/action/ActionLayer.kt")) {
            assertTrue("$file was read", sources.getValue(file).length > 200)
        }
        assertEquals(1, count(sources.getValue("music/radio/StationItem.kt"), ".setMediaId(play.mediaId) .setUri(MusicSources.own.queued(play.url)) "))
        assertEquals(1, count(sources.getValue("music/server/ServerTrackItem.kt"), ".setMediaId(mediaId(track)) .setUri(MusicSources.own.queued(url)) "))
    }

    @Test fun `phase 20 D1 a third builder, a station found for a stranger, an unchecked address, or an item set somewhere else is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = musicSessionProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        fun added(file: String, code: String) = musicSessionProblems(sources + (file to sources.getValue(file) + " " + code))
        fun service(old: String, new: String) = with("music/MusicService.kt", old, new)
        fun player(old: String, new: String) = with("music/MusicPlayer.kt", old, new)
        fun station(old: String, new: String) = with("music/radio/StationItem.kt", old, new)
        fun server(old: String, new: String) = with("music/server/ServerTrackItem.kt", old, new)
        fun rule(old: String, new: String) = with("music/radio/RadioSearchRule.kt", old, new)

        // ---- the two-builders clause: a third maker of an item with an address, anywhere under music/, in any spelling.
        assertTrue(added("music/radio/RadioNet.kt", "fun x(u: String) = androidx.media3.common.MediaItem.Builder().setUri(u).build()").isNotEmpty())
        assertTrue(added("music/MusicPlayer.kt", "fun x(u: String) = MediaItem.fromUri(u)").isNotEmpty())
        assertTrue(added("music/MusicCollectionPage.kt", "fun x(i: androidx.media3.common.MediaItem, u: String) = i.buildUpon().setUri(u).build()").isNotEmpty())
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(i: MediaItem, u: android.net.Uri) = i.buildUpon() .setUri (u).build()").isNotEmpty())
        assertTrue(added("music/KnownDurationPlayer.kt", "fun x(b: android.os.Bundle) = androidx.media3.common.MediaItem.fromBundle(b)").isNotEmpty())
        assertTrue(added("music/MusicNowPlaying.kt", "fun x(u: android.net.Uri) = androidx.media3.common.MediaItem.RequestMetadata.Builder().setMediaUri(u).build()").isNotEmpty())
        assertTrue(added("music/handoff/MusicHandoff.kt", "fun x() = androidx.media3.common.MediaItem . Builder()").isNotEmpty())
        assertTrue(added("music/radio/StationLogos.kt", "fun x(i: androidx.media3.common.MediaItem) = i.localConfiguration?.uri").isNotEmpty())
        // A logo handed to the session as an address (r3 D2), in a builder or anywhere else under music/.
        assertTrue(station("val meta = MediaMetadata.Builder() .setAlbumTitle(play.name)", "val meta = MediaMetadata.Builder() .setArtworkUri(android.net.Uri.parse(station.favicon)) .setAlbumTitle(play.name)").isNotEmpty())
        assertTrue(added("music/KnownDurationPlayer.kt", "fun x(m: MediaMetadata.Builder, u: android.net.Uri) = m.setArtworkUri(u)").isNotEmpty())

        // ---- the builders: the address not the accepted one, the URL rule not asked or its answer ignored, the override's host a constant.
        assertTrue(station(".setMediaId(play.mediaId) .setUri(MusicSources.own.queued(play.url))", ".setMediaId(play.mediaId) .setUri(MusicSources.own.queued(station.url))").isNotEmpty())
        assertTrue(station(".setMediaId(play.mediaId) .setUri(MusicSources.own.queued(play.url))", ".setMediaId(play.mediaId) .setUri(MusicSources.own.queued(station.urlResolved.ifEmpty { play.url }))").isNotEmpty())
        assertTrue(station("is Plan.Refused -> return Built.Refused(p.line) is Plan.Play -> p }", "is Plan.Refused -> Plan.Play(MusicLive.stationId(station.uuid), station.url, null, station.name) is Plan.Play -> p }").isNotEmpty())
        assertTrue(station("return when (val a = StationUrl.accept(stream.url, qaHost)) {", "return when (val a = StationUrl.Accept.Ok as StationUrl.Accept) {").isNotEmpty())
        assertTrue(station("StationUrl.accept(stream.url, qaHost)", "StationUrl.accept(stream.url, StationUrl.qaHost(stream.url))").isNotEmpty())
        assertTrue(station("StationUrl.Accept.UnsupportedHost -> Plan.Refused(StreamLine.UNSUPPORTED_HOST)", "StationUrl.Accept.UnsupportedHost, StationUrl.Accept.Ok -> Plan.Play(MusicLive.stationId(station.uuid), stream.url, null, station.name) else -> Plan.Refused(StreamLine.UNSUPPORTED_HOST)").isNotEmpty())
        assertTrue(station("StationUrl.Playable.Playlist -> return Plan.Refused(StreamLine.UNSUPPORTED_PLAYLIST)", "StationUrl.Playable.Playlist -> StationUrl.Playable.Stream(station.url, null)").isNotEmpty())
        assertTrue(station("url = stream.url,", "url = station.url,").isNotEmpty())
        assertTrue(server("if (url == null || !accepts(url, track)) return null", "if (url == null) return null").isNotEmpty())
        assertTrue(server("if (url == null || !accepts(url, track)) return null", "if (url == null || accepts(url, track)) return null").isNotEmpty())
        assertTrue(server(".setMediaId(mediaId(track)) .setUri(MusicSources.own.queued(url))", ".setMediaId(mediaId(track)) .setUri(url + \"&ApiKey=\" + track.id)").isNotEmpty())
        assertTrue(server("= server.audioStreamUrls(tracks).mapIndexed { i, url -> build(url, tracks[i]) }", "= server.audioStreamUrls(tracks).mapIndexed { i, url -> MediaItem.Builder().setMediaId(mediaId(tracks[i])).setUri(url).build() }").isNotEmpty())
        assertTrue(service("(StationItem.build(it, qaHost) as? StationItem.Built.Item)?.item", "MediaItem.Builder().setMediaId(MusicLive.stationId(it.uuid)).setUri(it.url).build()").isNotEmpty())
        assertTrue(service("(StationItem.build(it, qaHost) as? StationItem.Built.Item)?.item", "(StationItem.build(it, \"10.0.2.2\") as? StationItem.Built.Item)?.item").isNotEmpty())
        assertTrue(service("val qaHost = RadioNet.qaHost(context) return stations.mapNotNull", "val qaHost = stations.firstOrNull()?.let { StationUrl.qaHost(it.url) } return stations.mapNotNull").isNotEmpty())
        // A builder asked from somewhere that is not its one caller.
        assertTrue(added("cortana/action/ActionLayer.kt", "fun x(s: Station) = StationItem.build(s, null)").isNotEmpty())
        assertTrue(added("music/MusicCollectionPage.kt", "fun x(s: app.tileshell.music.radio.Station) = app.tileshell.music.radio.StationItem.buildAll(listOf(s), \"10.0.2.2\")").isNotEmpty())
        assertTrue(added("music/MusicActivity.kt", "fun x(u: String, t: app.tileshell.video.server.ServerTrack) = app.tileshell.music.server.ServerTrackItem.build(u, t)").isNotEmpty())
        assertTrue(added("music/MusicActivity.kt", "fun x(s: List<app.tileshell.music.radio.Station>) = MusicService.stationItems(this, s)").isNotEmpty())

        // ---- the search: a station found for a controller that is not the shell's own.
        val asked = "RadioSearchRule.stationsFor(controller.uid, Process.myUid(), ::radioStations)"
        assertTrue(service(asked, "RadioSearchRule.stationsFor(Process.myUid(), Process.myUid(), ::radioStations)").isNotEmpty())
        assertTrue(service(asked, "RadioSearchRule.stationsFor(controller.uid, controller.uid, ::radioStations)").isNotEmpty())
        assertTrue(service(asked, "RadioSearchRule.stationsFor(Process.myUid(), controller.uid, ::radioStations)").isNotEmpty())
        assertTrue(service(asked, "radioStations()").isNotEmpty())
        assertTrue(service(asked, "if (controller.uid >= 0) radioStations() else MusicSearch.Stations.NONE").isNotEmpty())
        assertTrue(service(asked, "RadioSearchRule.stationsFor(mediaSession.mediaNotificationControllerInfo?.uid ?: controller.uid, Process.myUid(), ::radioStations)").isNotEmpty())
        // The stranger's own path (an item that is a search, through fromLibrary) handed the stations.
        assertTrue(service("MusicSearch.resolve(item.requestMetadata.searchQuery.orEmpty(), lib)?.queue", "MusicSearch.resolve(item.requestMetadata.searchQuery.orEmpty(), lib, radioStations())?.queue").isNotEmpty())
        assertTrue(service("MusicItemRule.Decision.Search -> MusicSearch.resolve(item.requestMetadata.searchQuery.orEmpty(), lib)?.queue.orEmpty().map { mediaItem(it) }", "MusicItemRule.Decision.Search -> MusicSearch.resolve(item.requestMetadata.searchQuery.orEmpty(), lib, radioStations())?.station?.let { stationItems(this@MusicService, listOf(it)) }.orEmpty()").isNotEmpty())
        // The rule itself: turned round, widened, a constant, or its stations handed out whoever asks.
        assertTrue(rule("myUid >= 0 && controllerUid == myUid", "myUid >= 0 && controllerUid != myUid").isNotEmpty())
        assertTrue(rule("myUid >= 0 && controllerUid == myUid", "myUid >= 0 || controllerUid == myUid").isNotEmpty())
        assertTrue(rule("myUid >= 0 && controllerUid == myUid", "controllerUid >= 0").isNotEmpty())
        assertTrue(rule("myUid >= 0 && controllerUid == myUid", "true").isNotEmpty())
        assertTrue(rule("if (stationsFor(controllerUid, myUid)) all() else MusicSearch.Stations.NONE", "all()").isNotEmpty())
        assertTrue(rule("if (stationsFor(controllerUid, myUid)) all() else MusicSearch.Stations.NONE", "if (stationsFor(controllerUid, myUid)) MusicSearch.Stations.NONE else all()").isNotEmpty())
        assertTrue(rule("if (stationsFor(controllerUid, myUid)) all() else MusicSearch.Stations.NONE", "if (stationsFor(myUid, myUid)) all() else MusicSearch.Stations.NONE").isNotEmpty())
        // A second asker of the rule, of the resolver with stations, or of the stations themselves.
        assertTrue(added("music/MusicSearch.kt", "fun x(u: Int) = app.tileshell.music.radio.RadioSearchRule.stationsFor(u, u)").isNotEmpty())
        assertTrue(added("music/MusicPlayer.kt", "fun x(q: String, s: MusicSearch.Stations) = MusicSearch.resolve(q, emptyList(), s)").isNotEmpty())
        assertTrue(added("music/MusicService.kt", "fun x() = MusicSearch.Stations(emptyList()) { RadioIndex.EMPTY }").isNotEmpty())

        // ---- the station match: the controller's own items in its place, a start the rule did not work out, no gate, a raw query.
        assertTrue(service("match.station?.let { return startedStation(query, it) }", "match.station?.let { return started(mediaItems, MusicQueueStart.search(mediaItems.size, 0)) }").isNotEmpty())
        assertTrue(service("match.station?.let { return startedStation(query, it) } ", "").isNotEmpty())
        assertTrue(service("val items = stationItems(this@MusicService, plan.stations)", "val items = mediaItems").isNotEmpty())
        assertTrue(service("val items = stationItems(this@MusicService, plan.stations)", "val items = stationItems(this@MusicService, listOf(station))").isNotEmpty())
        assertTrue(service("return started(items, MusicQueueStart.search(items.size, plan.start))", "return started(items, MusicQueueStart.Start.At(plan.start, 0L))").isNotEmpty())
        assertTrue(service("return started(items, MusicQueueStart.search(items.size, plan.start))", "return started(items, MusicQueueStart.search(plan.stations.size, plan.start))").isNotEmpty())
        assertTrue(service("return started(items, MusicQueueStart.search(items.size, plan.start))", "return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, plan.start, 0L))").isNotEmpty())
        assertTrue(service("StationStart.plan(RadioNet.gate(this@MusicService, isStation = true),", "StationStart.plan(StreamGate.Decision(play = true),").isNotEmpty())
        assertTrue(service("StationStart.plan(RadioNet.gate(this@MusicService, isStation = true),", "StationStart.plan(RadioNet.gate(this@MusicService, isStation = false),").isNotEmpty())
        assertTrue(service("RadioFavouritesStore.get(this@MusicService).stations()), RadioNet.qaHost(this@MusicService))", "RadioFavouritesStore.get(this@MusicService).stations()), \"10.0.2.2\")").isNotEmpty())
        assertTrue(service("\"search \\\"\${MusicQueueStart.lineQuery(query)}\\\": station \${RadioText.shown(station.name, RadioText.NAME_MAX)}\"", "\"search \\\"\$query\\\": station \${RadioText.shown(station.name, RadioText.NAME_MAX)}\"").isNotEmpty())
        assertTrue(service("station \${RadioText.shown(station.name, RadioText.NAME_MAX)}\"", "station \${station.name}\"").isNotEmpty())

        // ---- a player handed an item somewhere else under music/: the reconnect, the wrapper, a page, Music's own handle.
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(i: MediaItem) = player.setMediaItem(i)").isNotEmpty())
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(i: MediaItem) = player.addMediaItem(i)").isNotEmpty())
        assertTrue(added("music/KnownDurationPlayer.kt", "fun x(i: androidx.media3.common.MediaItem) = replaceMediaItem(0, i)").isNotEmpty())
        assertTrue(added("music/MusicCollection.kt", "fun x(c: androidx.media3.session.MediaController, i: List<androidx.media3.common.MediaItem>) = c.setMediaItems (i)").isNotEmpty())
        assertTrue(player("c.setMediaItems(own.items, own.start, 0L)", "c.setMediaItems(own.items, own.start, 0L); c.addMediaItems(own.items)").isNotEmpty())
        assertTrue(service("Diagnostics.add(StreamLine.TAG, StreamLine.SLEEP_CLEARED)", "Diagnostics.add(StreamLine.TAG, StreamLine.SLEEP_CLEARED); mediaItem?.let { exo.replaceMediaItem(0, it) }").isNotEmpty())
        // Music's own queue: the gate's refusal skipped, the items or the tracks made by hand, a queue from elsewhere.
        assertTrue(player("plan.refusal?.let { return it } ", "").isNotEmpty())
        assertTrue(player("StationStart.plan(RadioNet.gate(appContext, isStation = true),", "StationStart.plan(StreamGate.Decision(play = true),").isNotEmpty())
        assertTrue(player("RadioFavouritesStore.get(appContext).stations()), RadioNet.qaHost(appContext))", "RadioFavouritesStore.get(appContext).stations()), StationUrl.qaHost(station.url))").isNotEmpty())
        assertTrue(player("val items = MusicService.stationItems(appContext, plan.stations)", "val items = MusicService.stationItems(appContext, listOf(station))").isNotEmpty())
        assertTrue(player("val built = ServerTrackItem.buildAll(server, tracks)", "val built = tracks.map { ServerTrackItem.build(server.audioStreamUrl(it) + \"&ApiKey=x\", it) }").isNotEmpty())
        // The newest ask's guard dropped, so an older list's queue could replace a newer one's.
        assertTrue(player("if (ask != serverAsk) return@execute val start", "val start").isNotEmpty())
        assertTrue(player("playOwn(OwnQueue(built.filterNotNull(), start,", "playOwn(OwnQueue(pendingOwn?.items.orEmpty() + built.filterNotNull(), start,").isNotEmpty())
        assertTrue(added("music/MusicPlayer.kt", "fun x(i: List<MediaItem>) = playOwn(OwnQueue(i, 0, \"x\"))").isNotEmpty())
    }

    @Test fun `L18-1 a controller's item reaches the player only as the item rule says, and PLAY_FILE only for the shell through the provider's rule`() {
        assertEquals(emptyList<String>(), musicSessionProblems(SourceScan.all()))
    }

    @Test fun `L18-1 a session that keeps a stranger's URI, ignores the rule's answer, offers PLAY_FILE to anyone or resolves the file by hand is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = musicSessionProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        fun service(old: String, new: String) = with("music/MusicService.kt", old, new)
        // The defect itself (H1): items that all carry a URI handed back untouched, before the rule is asked.
        assertTrue(service("{ val parts = fromLibrary(controller, mediaItems) if (!MusicQueueStart.mayAdd(", "{ if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems) val parts = fromLibrary(controller, mediaItems) if (!MusicQueueStart.mayAdd(").isNotEmpty())
        assertTrue(service("{ val lib by lazy { library() } var notKept = 0", "{ if (mediaItems.all { it.localConfiguration != null }) return mediaItems.map { listOf(it) } val lib by lazy { library() } var notKept = 0").isNotEmpty())
        // The rule asked about the wrong controller, with the uids swapped round, or with a constant for the shell.
        assertTrue(service("MusicItemRule.decide(controller.uid, Process.myUid(), item.mediaId,", "MusicItemRule.decide(Process.myUid(), Process.myUid(), item.mediaId,").isNotEmpty())
        assertTrue(service("MusicItemRule.decide(controller.uid, Process.myUid(), item.mediaId,", "MusicItemRule.decide(Process.myUid(), controller.uid, item.mediaId,").isNotEmpty())
        assertTrue(service("MusicItemRule.decide(controller.uid, Process.myUid(), item.mediaId,", "MusicItemRule.decide(controller.uid, controller.uid, item.mediaId,").isNotEmpty())
        assertTrue(service("item.mediaId, hasUri, item.requestMetadata.searchQuery != null)", "item.mediaId, false, item.requestMetadata.searchQuery != null)").isNotEmpty())
        // The rule's answer ignored at the call site: a rebuilt or dropped item handed back as it arrived.
        assertTrue(service("MusicItemRule.Decision.Drop -> emptyList()", "MusicItemRule.Decision.Drop -> listOf(item)").isNotEmpty())
        assertTrue(service("is MusicItemRule.Decision.Rebuild -> lib.firstOrNull { it.id == decision.id }?.let { listOf(mediaItem(it)) }.orEmpty()", "is MusicItemRule.Decision.Rebuild -> listOf(item)").isNotEmpty())
        assertTrue(service("lib.firstOrNull { it.id == decision.id }?.let { listOf(mediaItem(it)) }.orEmpty()", "lib.firstOrNull { it.id == decision.id }?.let { listOf(mediaItem(it)) } ?: listOf(item)").isNotEmpty())
        assertTrue(service("when (decision) { MusicItemRule.Decision.Keep ->", "when (if (hasUri) MusicItemRule.Decision.Keep else decision) { MusicItemRule.Decision.Keep ->").isNotEmpty())
        assertTrue(service("return Futures.immediateFuture(parts.flatten().toMutableList())", "return Futures.immediateFuture(if (parts.all { it.size == 1 }) parts.flatten().toMutableList() else mediaItems)").isNotEmpty())
        // A second path round the rule: the set callback answering with the controller's own items, a resumption
        // answered, an item set on the player or given a URI somewhere else, the request's URI read.
        assertTrue(service("val parts = fromLibrary(controller, mediaItems) val start = MusicQueueStart.set(", "if (controller.uid >= 0) return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)) val parts = fromLibrary(controller, mediaItems) val start = MusicQueueStart.set(").isNotEmpty())
        assertTrue(service("return started(match.queue.map { mediaItem(it) }, MusicQueueStart.search(", "return started(mediaItems, MusicQueueStart.search(").isNotEmpty())
        assertTrue(service("override fun onGetSession(", "fun onPlaybackResumption() = Unit override fun onGetSession(").isNotEmpty())
        assertTrue(service("Diagnostics.add(\"music\", MusicFile.line(file.path))", "Diagnostics.add(\"music\", MusicFile.line(file.path)); player.setMediaItem(MediaItem.fromUri(raw.orEmpty()))").isNotEmpty())
        assertTrue(service(".setUri(queued(MusicStore.uriOf(track)))", ".setUri(track.path)").isNotEmpty())
        assertTrue(service("val hasUri = item.localConfiguration != null", "val hasUri = item.localConfiguration != null && item.requestMetadata.mediaUri == null").isNotEmpty())
        assertTrue(musicSessionProblems(sources + ("music/MusicSearch.kt" to sources.getValue("music/MusicSearch.kt") + " fun x() = MusicItemRule.decide(1, 1, \"\", true, false)")).isNotEmpty())
        // The review's surviving mutant: PLAY_FILE offered to every controller, or to every controller BUT the shell.
        assertTrue(service(".apply { if (controller.uid == Process.myUid()) add(playFileCommand) }", ".add(playFileCommand)").isNotEmpty())
        assertTrue(service("if (controller.uid == Process.myUid()) add(playFileCommand)", "if (controller.uid != Process.myUid()) add(playFileCommand)").isNotEmpty())
        assertTrue(service("if (controller.uid == Process.myUid()) add(playFileCommand)", "if (controller.uid >= 0) add(playFileCommand)").isNotEmpty())
        assertTrue(service("SessionResult(playFile(controller, args.getString(MusicCommands.ARG_URI)))", "SessionResult(playFile(session.mediaNotificationControllerInfo ?: controller, args.getString(MusicCommands.ARG_URI)))").isNotEmpty())
        // The review's other surviving mutant: the file resolved by hand from the URI instead of by the provider's rule.
        assertTrue(service("val file = FilesProvider.fileFor(this, uri) ?: run {", "val file = uri.path?.removePrefix(\"/root\")?.let { File(it) } ?: run {").isNotEmpty())
        assertTrue(service("val file = FilesProvider.fileFor(this, uri) ?: run {", "val file = java.io.File(uri.pathSegments.drop(1).joinToString(\"/\", \"/\")).takeIf { it.exists() } ?: run {").isNotEmpty())
        assertTrue(service("val uri = Uri.parse(decision.uri)", "val uri = Uri.parse(raw)").isNotEmpty())
        assertTrue(service("val item = fileItem(uri, file, ask)", "val item = fileItem(uri, File(raw.orEmpty()), ask)").isNotEmpty())
        // The caller check dropped from playFile, or its refusal turned into the play.
        assertTrue(service("if (decision !is MusicPlayExtra.Decision.PlayUri) {", "if (false) {").isNotEmpty())
    }

    // ------------------------------------------------------------- the music player's data source (phase 20, D11 / D12)

    /** The whole of `MusicSourceRule.mayOpen`: an address the shell queued; else http(s) on a host `StationUrl.accept` takes; else nothing. */
    private val sourceRuleForm = "{ if (asked in queued) return true " +
        "val scheme = ContentUriText.parse(asked).scheme?.lowercase(Locale.ROOT) " +
        "if (scheme != \"http\" && scheme != \"https\") return false " +
        "return StationUrl.accept(asked, qaHost) == StationUrl.Accept.Ok }"

    /** The service's data source, as code: the default one, the guard outermost, and that factory the media source factory's. */
    private val sourceWiringForm = "val upstream = DefaultDataSource.Factory(this) " +
        "val qaHost = RadioNet.qaHost(this) " +
        "val sources = DataSource.Factory { GuardedDataSource(upstream.createDataSource()) { asked -> MusicSources.own.mayOpen(asked, qaHost) } } " +
        "val mediaSourceFactory = DefaultMediaSourceFactory( sources, DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING), ) " +
        "val exo = ExoPlayer.Builder(this, mediaSourceFactory)"

    /**
     * Phase 20 (r3 D11 / D12; `docs/plan/qa/phase-20/hls-rereview.md` (b)): with HLS linked, a station's PLAYLIST names
     * segments, keys, init segments and nested playlists of its own, and the player's `DefaultDataSource` opens `file:`,
     * `content:`, `asset:`, `android.resource:` and `data:` with the shell's identity. `MusicSourceRule` is the rule
     * (`MusicSourceRuleTest`); held here is what no unit test runs — that every open of the music player is asked:
     *  - the service makes ONE data source factory — `DefaultDataSource` wrapped, outermost, in `GuardedDataSource`
     *    with `MusicSources.own.mayOpen(asked, qaHost)`, `qaHost` being `RadioNet.qaHost`'s (null in a release build) —
     *    and hands that to its one `DefaultMediaSourceFactory`, which is the factory
     *    of the service's player AND of the crossfade's fader; nothing else under `music/` makes a data source, a media
     *    source or a player;
     *  - the guard asks before the upstream opens anything (`playerProblems` holds `GuardedDataSource.open`'s one form);
     *  - the guard's set is told an address at the four `.setUri(` sites under `music/` alone (`stationProblems` holds
     *    that there are four): the service's two through `queued(…)` — a library track's MediaStore URI and the checked
     *    file's — and the two builders', each the address its URL rule accepted; the rule is its one form, asked by
     *    `MusicSources.mayOpen` alone. Any other http(s) address is `StationUrl.accept`'s to take (`StationUrlTest`).
     */
    private fun musicSourceProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val service = sources["music/MusicService.kt"].orEmpty()
        val fader = sources["music/CrossfadeFader.kt"].orEmpty()
        val rule = sources["music/MusicSourceRule.kt"].orEmpty()
        val music = sources.filterKeys { it.startsWith("music/") }
        fun where(piece: Regex, among: Map<String, String> = music) = among.mapValues { (_, text) -> piece.findAll(text).count() }.filterValues { it > 0 }

        // One guarded factory, the media source factory's; no other data source, media source or player under music/.
        if (!service.contains(sourceWiringForm)) problems += "the music player's media source factory is not given the guarded data source"
        // The five: DefaultDataSource.Factory(, DataSource.Factory {, GuardedDataSource(, its upstream.createDataSource( —
        // and the tag reader's setDataSource(.
        val dataSources = where(Regex("\\b\\w*DataSource\\b\\s*(?:\\.\\s*Factory\\s*)?[({]"))
        if (dataSources != mapOf("music/MusicService.kt" to 5) || count(service, "tags.setDataSource(file.path)") != 1) problems += "a data source is made under music/ outside the service's guarded one: $dataSources"
        val mediaSources = where(Regex("\\b\\w*MediaSourceFactory\\s*\\(|\\b\\w+MediaSource\\b|\\bsetDataSourceFactory\\b|\\bsetMediaSourceFactory\\b|\\bcreateMediaSource\\b|\\bsetMediaSource\\w*\\b"))
        if (mediaSources != mapOf("music/MusicService.kt" to 1)) problems += "a media source or its factory is made under music/ outside the service's one: $mediaSources"
        val players = where(Regex("\\bExoPlayer\\s*\\.\\s*Builder\\b|\\bSimpleExoPlayer\\b|\\bMediaPlayer\\s*\\("))
        if (players != mapOf("music/MusicService.kt" to 1, "music/CrossfadeFader.kt" to 1) || !fader.contains("val f = ExoPlayer.Builder(context, mediaSourceFactory)") ||
            !fader.contains("private val mediaSourceFactory: MediaSource.Factory,") || Regex("\\bmediaSourceFactory\\b").findAll(fader).count() != 2 ||
            !service.contains("CrossfadeFader(this, known, audioSession, mediaSourceFactory, attributes)") || Regex("\\bmediaSourceFactory\\b").findAll(service).count() != 3 ||
            Regex("\\bsources\\b").findAll(service).count() != 2 || Regex("\\bupstream\\b").findAll(service).count() != 2
        ) problems += "a player under music/ is built with something other than the service's guarded media source factory: $players"

        // What the guard is told, and by whom; the rule's one form and its one asker.
        // By name: the service's two (the guard's question, its `queued` helper) and each builder's one, its import beside it.
        val told = where(Regex("\\bMusicSources\\b"), sources.filterKeys { it != "music/MusicSourceRule.kt" })
        val tellers = where(Regex("\\.\\s*queued\\s*\\("), sources.filterKeys { it != "music/MusicSourceRule.kt" })
        if (told != mapOf("music/MusicService.kt" to 2, "music/radio/StationItem.kt" to 2, "music/server/ServerTrackItem.kt" to 2) ||
            tellers != mapOf("music/MusicService.kt" to 1, "music/radio/StationItem.kt" to 1, "music/server/ServerTrackItem.kt" to 1) ||
            !service.contains("private fun queued(uri: Uri): Uri = uri.also { MusicSources.own.queued(it.toString()) }") || Regex("(?<!\\.)\\bqueued\\s*\\(").findAll(service).count() != 3 ||
            Regex("\\bqaHost\\b").findAll(service).count() != 7 || count(service, "RadioNet.qaHost(") != 3
        ) problems += "the guard's set is told an address outside the four item builders, or its fixture host is not RadioNet's: $told $tellers"
        if (body(rule, "fun mayOpen(asked: String, queued: Set<String>, qaHost: String?): Boolean") != sourceRuleForm) problems += "MusicSourceRule.mayOpen is not its one form:\n  is:      ${body(rule, "fun mayOpen(asked: String, queued: Set<String>, qaHost: String?): Boolean")}\n  must be: $sourceRuleForm"
        if (!rule.contains("fun mayOpen(asked: String, qaHost: String?): Boolean = MusicSourceRule.mayOpen(asked, queued, qaHost)") ||
            !rule.contains("fun queued(address: String): String = address.also { queued.add(it) }") || Regex("\\bqueued\\b").findAll(rule).count() != 6 || count(rule, "StationUrl.accept(") != 1 || where(Regex("MusicSourceRule\\s*\\.\\s*mayOpen"), sources) != mapOf("music/MusicSourceRule.kt" to 1)
        ) problems += "the music source rule is asked, or its set changed, other than by MusicSources"
        return problems
    }

    @Test fun `phase 20 D11 the music player opens every address through the guarded data source, told only the shell's own queued addresses`() {
        val sources = SourceScan.all()
        assertEquals(emptyList<String>(), musicSourceProblems(sources))
        // Not vacuous: the video player's guard — the same class — is held to its one form by playerProblems.
        assertEquals(emptyList<String>(), playerProblems(read("video/PlayerActivity.kt"), read("video/VideoPlayback.kt")))
    }

    @Test fun `phase 20 D11 an unguarded factory, a second data source or player, a set told a stranger's address, a made-up fixture host or a looser rule is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = musicSourceProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        fun service(old: String, new: String) = with("music/MusicService.kt", old, new)
        fun added(file: String, code: String) = musicSourceProblems(sources + (file to sources.getValue(file) + " " + code))
        // The guard left out of the factory, asked nothing, or put under a second, unguarded source.
        assertTrue(service("DefaultMediaSourceFactory( sources,", "DefaultMediaSourceFactory( upstream,").isNotEmpty())
        assertTrue(service("DefaultMediaSourceFactory( sources,", "DefaultMediaSourceFactory( this,").isNotEmpty())
        assertTrue(service("{ asked -> MusicSources.own.mayOpen(asked, qaHost) }", "{ true }").isNotEmpty())
        assertTrue(service("GuardedDataSource(upstream.createDataSource()) { asked -> MusicSources.own.mayOpen(asked, qaHost) }", "upstream.createDataSource()").isNotEmpty())
        // The fixture host made up, so a private host is let through in a release build.
        assertTrue(service("val qaHost = RadioNet.qaHost(this) val sources", "val qaHost = \"192.168.1.1\" val sources").isNotEmpty())
        assertTrue(service("MusicSources.own.mayOpen(asked, qaHost)", "MusicSources.own.mayOpen(asked, Uri.parse(asked).host)").isNotEmpty())
        assertTrue(service("val exo = ExoPlayer.Builder(this, mediaSourceFactory)", "val exo = ExoPlayer.Builder(this)").isNotEmpty())
        assertTrue(service("val exo = ExoPlayer.Builder(this, mediaSourceFactory)", "val exo = ExoPlayer.Builder(this, DefaultMediaSourceFactory(this))").isNotEmpty())
        assertTrue(service("CrossfadeFader(this, known, audioSession, mediaSourceFactory, attributes)", "CrossfadeFader(this, known, audioSession, DefaultMediaSourceFactory(this), attributes)").isNotEmpty())
        assertTrue(with("music/CrossfadeFader.kt", "val f = ExoPlayer.Builder(context, mediaSourceFactory)", "val f = ExoPlayer.Builder(context)").isNotEmpty())
        assertTrue(with("music/CrossfadeFader.kt", "val f = ExoPlayer.Builder(context, mediaSourceFactory)", "val f = ExoPlayer.Builder(context, androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context))").isNotEmpty())
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(c: android.content.Context) = androidx.media3.exoplayer.ExoPlayer.Builder(c).build()").isNotEmpty())
        assertTrue(added("music/radio/StationLogos.kt", "fun x(c: android.content.Context) = androidx.media3.datasource.DefaultDataSource.Factory(c).createDataSource()").isNotEmpty())
        assertTrue(added("music/MusicPlayer.kt", "fun x(f: androidx.media3.datasource.DataSource.Factory) = androidx.media3.exoplayer.hls.HlsMediaSource.Factory(f)").isNotEmpty())
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(e: androidx.media3.exoplayer.ExoPlayer, s: androidx.media3.exoplayer.source.MediaSource) = e.setMediaSource(s)").isNotEmpty())
        // The set told something else: a controller's own item, a station's address, from another file.
        assertTrue(service("MusicItemRule.Decision.Keep -> listOf(item)", "MusicItemRule.Decision.Keep -> listOf(item).also { MusicSources.own.queued(item.mediaId) }").isNotEmpty())
        assertTrue(added("music/radio/StationItem.kt", "fun x(u: String) = MusicSources.own.queued(u)").isNotEmpty())
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(u: String) = app.tileshell.music.MusicSources.own.queued(u)").isNotEmpty())
        assertTrue(added("music/MusicPlayer.kt", "fun x(u: String) = MusicSources.own.queued(u)").isNotEmpty())
        assertTrue(added("music/MusicService.kt", "fun x(u: Uri) = queued(u)").isNotEmpty())
        // A looser rule: every scheme, a prefix instead of the address itself, or the rule asked round the set.
        assertTrue(with("music/MusicSourceRule.kt", "if (asked in queued) return true", "if (queued.any { asked.startsWith(it) }) return true").isNotEmpty())
        assertTrue(with("music/MusicSourceRule.kt", "if (scheme != \"http\" && scheme != \"https\") return false", "if (scheme == \"file\") return false").isNotEmpty())
        // The review's finding: every http(s) address opened, private literals with the rest.
        assertTrue(with("music/MusicSourceRule.kt", "return StationUrl.accept(asked, qaHost) == StationUrl.Accept.Ok }", "return true }").isNotEmpty())
        assertTrue(with("music/MusicSourceRule.kt", "return StationUrl.accept(asked, qaHost) == StationUrl.Accept.Ok }", "return StationUrl.accept(asked, qaHost) !is StationUrl.Accept.UnsupportedScheme }").isNotEmpty())
        assertTrue(with("music/MusicSourceRule.kt", "= MusicSourceRule.mayOpen(asked, queued, qaHost)", "= MusicSourceRule.mayOpen(asked, queued + asked, qaHost)").isNotEmpty())
        assertTrue(added("music/MusicSourceRule.kt", "fun x(a: String) = MusicSourceRule.mayOpen(a, setOf(a), null)").isNotEmpty())
    }

    // ------------------------------------------------------------------------------------ Music's queue start (L18-4)

    /**
     * Ledger L18-4 (the adversarial review's pass 2, N3): when the item rule drops or expands a controller's items, the
     * request's start index still counts the ORIGINAL list, and ExoPlayer throws for an index its list does not have
     * only AFTER it has taken the list — the player is wedged. `MusicQueueStart` is the rule (`MusicQueueStartTest`);
     * held by `musicSessionProblems` above is that the service cannot hand the player a list without it: `onSetMediaItems`,
     * `onAddMediaItems`, `fromLibrary` and `started` are each one form, the one `MediaItemsWithStartPosition` is made
     * from the rule's index and position, and Media3's default (the request's own index beside a shorter list) is gone.
     */
    @Test fun `L18-4 every list the session hands the player starts where MusicQueueStart says, or is refused first`() {
        val sources = SourceScan.all()
        assertEquals(emptyList<String>(), musicSessionProblems(sources))
        val service = sources.getValue("music/MusicService.kt")
        assertEquals(setItemsForm, body(service, "override fun onSetMediaItems("))
        assertEquals(1, count(service, "MusicQueueStart.set(parts.map { it.size }, startIndex, startPositionMs)"))
    }

    @Test fun `L18-4 a session that hands on the request's own index, clamps by the original list, skips the refusal or goes back to Media3's default is caught`() {
        val sources = SourceScan.all()
        fun service(old: String, new: String) = musicSessionProblems(sources + ("music/MusicService.kt" to mutate(sources.getValue("music/MusicService.kt"), old, new)))
        // The defect itself (N3): Media3's default, or the request's own index and position beside the rule's list.
        assertTrue(service("val parts = fromLibrary(controller, mediaItems) val start = MusicQueueStart.set(", "if (mediaItems.size > 1) return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs) val parts = fromLibrary(controller, mediaItems) val start = MusicQueueStart.set(").isNotEmpty())
        assertTrue(service("MediaSession.MediaItemsWithStartPosition(items, start.index, start.positionMs)", "MediaSession.MediaItemsWithStartPosition(items, startIndexAsked, start.positionMs)").isNotEmpty())
        assertTrue(service("return started(parts.flatten(), start) }", "return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(parts.flatten(), startIndex, startPositionMs)) }").isNotEmpty())
        // The rule asked about the wrong list: the request's size instead of what each item became; or after a clamp of the service's own.
        assertTrue(service("MusicQueueStart.set(parts.map { it.size }, startIndex, startPositionMs)", "MusicQueueStart.set(mediaItems.map { 1 }, startIndex, startPositionMs)").isNotEmpty())
        assertTrue(service("MusicQueueStart.set(parts.map { it.size }, startIndex, startPositionMs)", "MusicQueueStart.set(parts.map { it.size }, startIndex.coerceAtMost(mediaItems.size - 1), startPositionMs)").isNotEmpty())
        assertTrue(service("MusicQueueStart.set(parts.map { it.size }, startIndex, startPositionMs)", "MusicQueueStart.Start.At(startIndex.coerceIn(0, parts.size), startPositionMs)").isNotEmpty())
        // The rule's answer not used: its index dropped, its position swapped for the request's, the refusal answered with an empty list.
        assertTrue(service("MediaItemsWithStartPosition(items, start.index, start.positionMs)", "MediaItemsWithStartPosition(items, 0, start.positionMs)").isNotEmpty())
        assertTrue(service("MediaItemsWithStartPosition(items, start.index, start.positionMs)", "MediaItemsWithStartPosition(items, start.index, 0L)").isNotEmpty())
        assertTrue(service("MusicQueueStart.Start.Refuse -> Futures.immediateFailedFuture(UnsupportedOperationException(\"nothing to play\"))", "MusicQueueStart.Start.Refuse -> Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, 0, 0L))").isNotEmpty())
        // The search's own index handed on unchecked, and the add's refusal dropped or turned round.
        assertTrue(service("MusicQueueStart.search(match.queue.size, match.startIndex))", "MusicQueueStart.Start.At(match.startIndex, 0L))").isNotEmpty())
        assertTrue(service("if (!MusicQueueStart.mayAdd(parts.map { it.size })) return", "if (false) return").isNotEmpty())
        assertTrue(service("if (!MusicQueueStart.mayAdd(parts.map { it.size })) return", "if (MusicQueueStart.mayAdd(parts.map { it.size })) return").isNotEmpty())
        // A second maker of a start, anywhere in the shell.
        assertTrue(musicSessionProblems(sources + ("music/MusicSearch.kt" to sources.getValue("music/MusicSearch.kt") + " fun x() = MusicQueueStart.set(listOf(1), 0, 0L)")).isNotEmpty())
        // N10: the query raw in either of its two lines.
        assertTrue(service("\"search \\\"\${MusicQueueStart.lineQuery(query)}\\\": nothing in the library\"", "\"search \\\"\$query\\\": nothing in the library\"").isNotEmpty())
        assertTrue(service("\"search \\\"\${MusicQueueStart.lineQuery(query)}\\\": \${match.kind.name.lowercase()}", "\"search \\\"\$query\\\": \${match.kind.name.lowercase()}").isNotEmpty())
        assertTrue(service("UnsupportedOperationException(\"no match in the library\")", "UnsupportedOperationException(\"no match for \\\"\$query\\\"\")").isNotEmpty())
    }

    // ---------------------------------------------------------------------------------------------- the capture answer

    private fun captureProblems(capture: String): List<String> {
        val problems = mutableListOf<String>()
        // The decision is the rule's, over the real intent and the real port, made once.
        if (count(capture, "CaptureRequestRule.decide(") != 1 || !capture.contains("val outcome = CaptureRequestRule.decide(IntentCaptureRequest(this, request), AndroidUriAccess(this)) outcome.before.forEach { Diagnostics.add(\"camera\", it) } return outcome }")) {
            problems += "the decision is not CaptureRequestRule.decide over the real request and the real port"
        }
        if (capture.contains("CaptureOutputGuard.decide(") || capture.contains("UriAccessRules.") || capture.contains("meetsAToC")) problems += "the activity weighs the request itself"
        val assigned = Regex("\\bdecision\\s*=[^=]").findAll(capture).count()
        if (assigned != 1 || !capture.contains("private var decision: CaptureOutputGuard.Decision = CaptureOutputGuard.Decision.Refused(CaptureOutputGuard.LINE_NO_GRANT)") ||
            !capture.contains("val outcome = decide(intent) decision = outcome.decision val d = decision")
        ) problems += "the decision is not refused until, and then exactly, what the rule said for the launch intent"
        // The request's reads: one platform read each, EXTRA_OUTPUT taken only as a Uri.
        val request = body(capture, "private class IntentCaptureRequest(private val activity: Activity, private val request: Intent) : CaptureRequestPort")
        val reads = "{ override fun hasOutput(): Boolean = request.hasExtra(MediaStore.EXTRA_OUTPUT) " +
            "override fun outputText(): String? = request.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)?.toString() " +
            "override fun callingPackage(): String? = activity.callingPackage " +
            "override fun clipUris(): List<String> { val clip = request.clipData ?: return emptyList() return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri?.toString() } } " +
            "override fun flags(): Int = request.flags " +
            "override fun ownAuthorities(): Set<String> = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty() " +
            ".flatMap { it.authority.orEmpty().split(';') }.filter { it.isNotEmpty() }.toSet() }"
        if (request != reads) problems += "the request port is not one read per method (EXTRA_OUTPUT only as a Uri):\n  is:      $request\n  must be: $reads"
        if (count(capture, "EXTRA_OUTPUT") != 2) problems += "EXTRA_OUTPUT is read outside the request port"
        // The refused branch writes its lines, finishes and returns — before a camera or a saver exists.
        val refusal = "if (d is CaptureOutputGuard.Decision.Refused) { Diagnostics.add(\"camera\", d.line) outcome.after.forEach { Diagnostics.add(\"camera\", it) } finish() return }"
        val create = body(capture, "override fun onCreate(savedInstanceState: Bundle?)")
        val refusedAt = create.indexOf(refusal)
        if (refusedAt < 0) problems += "the refused branch does not finish and return"
        for (later in listOf("CameraSaver(", "CameraEngine(", "CameraProcess.startOnce(", "setShellAppContent(")) {
            if (count(capture, later) != 1 || create.indexOf(later) < refusedAt) problems += "$later is reached before the refused branch has returned"
        }
        // The one write into a caller's URI: the guard's token, the strip first, RESULT_OK only when written.
        val write = "val why = CallerCaptureWrite.run( isImage, strip = { ExifInterface(file.path).apply { CameraEngine.stripLocation(this); saveAttributes() } }, " +
            "write = { saver.writes.writeCaptureOutput(accepted) { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } } }, )"
        if (count(capture, "writeCaptureOutput(") != 1 || count(capture, "stripLocation(") != 1 || !capture.contains(write)) problems += "the caller's output is written other than through CallerCaptureWrite.run (strip, then write)"
        if (count(capture, "writeToCaller(d, ") != 2 ||
            !capture.contains("d is CaptureOutputGuard.Decision.Accepted && r is CaptureReview.Image -> if (writeToCaller(d, r.shot.file, isImage = true) == null) Intent() else null") ||
            !capture.contains("d is CaptureOutputGuard.Decision.Accepted && r is CaptureReview.Video -> if (writeToCaller(d, r.take.file, isImage = false) == null) Intent().setData(Uri.parse(d.uri)) else null")
        ) problems += "RESULT_OK for an accepted output does not hang on the write layer's answer"
        if (capture.contains("contentResolver") || capture.contains("openOutputStream") || capture.contains("openFileDescriptor")) problems += "the activity opens a URI itself"
        // A capture for another app cannot keep a location: the sink is the one whose flag is final.
        if (!capture.contains("private val sink = object : CallerCaptureSink() {") || capture.contains("keepsLocation")) problems += "the capture answer's sink is not a CallerCaptureSink"
        return problems
    }

    @Test fun `the capture answer hands the rule the real request and does what it says`() {
        assertEquals(emptyList<String>(), captureProblems(read("camera/CaptureActivity.kt")))
    }

    @Test fun `a capture answer that goes round the rule is caught`() {
        val capture = read("camera/CaptureActivity.kt")
        fun with(old: String, new: String) = captureProblems(mutate(capture, old, new))
        // A String extra taken as the output; the refused branch not finishing; the second strip deleted.
        assertTrue(with("request.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)?.toString()", "request.extras?.get(MediaStore.EXTRA_OUTPUT)?.toString()").isNotEmpty())
        assertTrue(with("outcome.after.forEach { Diagnostics.add(\"camera\", it) } finish() return }", "outcome.after.forEach { Diagnostics.add(\"camera\", it) } }").isNotEmpty())
        assertTrue(with("outcome.after.forEach { Diagnostics.add(\"camera\", it) } finish() return }", "outcome.after.forEach { Diagnostics.add(\"camera\", it) } finish() }").isNotEmpty())
        assertTrue(with("strip = { ExifInterface(file.path).apply { CameraEngine.stripLocation(this); saveAttributes() } }", "strip = { }").isNotEmpty())
        assertTrue(with("val why = CallerCaptureWrite.run( isImage, strip", "val why = CallerCaptureWrite.run( false, strip").isNotEmpty())
        // The decision made from a constant, from another intent, or with another port; the calling package a constant.
        assertTrue(with("decision = outcome.decision", "decision = CaptureOutputGuard.Decision.NoOutput").isNotEmpty())
        assertTrue(with("CaptureRequestRule.decide(IntentCaptureRequest(this, request), AndroidUriAccess(this))", "CaptureRequestRule.decide(IntentCaptureRequest(this, request), alwaysYes)").isNotEmpty())
        assertTrue(with("override fun callingPackage(): String? = activity.callingPackage", "override fun callingPackage(): String? = activity.packageName").isNotEmpty())
        assertTrue(with("override fun flags(): Int = request.flags", "override fun flags(): Int = Intent.FLAG_GRANT_WRITE_URI_PERMISSION").isNotEmpty())
        assertTrue(with("clip.getItemAt(it)?.uri?.toString()", "outputText()").isNotEmpty())
        // RESULT_OK whatever the write said; the output opened by the activity itself; a sink that could keep a location.
        assertTrue(with("if (writeToCaller(d, r.shot.file, isImage = true) == null) Intent() else null", "Intent().also { writeToCaller(d, r.shot.file, isImage = true) }").isNotEmpty())
        assertTrue(with("private val sink = object : CallerCaptureSink() {", "private val sink = object : CaptureSink { override val keepsLocation = true").isNotEmpty())
        assertTrue(with("saver = CameraSaver(this)", "saver = CameraSaver(this); contentResolver.openOutputStream(intent.data!!)").isNotEmpty())
    }

    // -------------------------------------------------------------------------------------------- both location strips

    private fun stripProblems(engine: String, viewfinder: String, location: String): List<String> {
        val problems = mutableListOf<String>()
        if (!viewfinder.contains("engine.takePhoto(sink.keepsLocation) {") || count(viewfinder, "engine.takePhoto(") != 1) problems += "a still is not taken with the sink's own keepsLocation"
        if (!engine.contains("writeExif(file, manual, result, withLocation)") || count(engine, "writeExif(") != 2) problems += "the capture's EXIF is not written with the sink's flag"
        val exif = body(engine, "private fun writeExif(file: File, manual: Map<ProControl, Int>, result: TotalCaptureResult?, keepLocation: Boolean)")
        if (!exif.trimEnd().endsWith("if (!keepLocation) stripLocation(exif) exif.saveAttributes() }")) problems += "the first strip is not the last thing before the capture's EXIF is saved"
        if (!engine.contains("if (withLocation) location?.let { metadata.location = it }")) problems += "a location is given to a capture that must not carry one"
        if (!engine.contains("fun stripLocation(exif: ExifInterface) = ExifLocation.strip(exif::setAttribute)")) problems += "stripLocation is not ExifLocation.strip over the file's own EXIF"
        if (!location.contains("fun strip(set: (tag: String, value: String?) -> Unit) { GPS_TAGS.forEach { set(it, null) } }")) problems += "the strip does not remove every GPS tag"
        if (!location.contains("abstract class CallerCaptureSink : CaptureSink { final override val keepsLocation: Boolean get() = false }")) problems += "a caller's sink can keep a location"
        return problems
    }

    @Test fun `both location strips are called - at the capture and again before the caller's output is written`() {
        assertEquals(emptyList<String>(), stripProblems(read("camera/CameraEngine.kt"), read("camera/Viewfinder.kt"), read("camera/ExifLocation.kt")))
        // The second strip is `captureProblems`'s: CallerCaptureWrite.run( isImage, strip = { … stripLocation(this); saveAttributes() }, write = …).
    }

    @Test fun `a first strip that is deleted, skipped or overridden is caught`() {
        val engine = read("camera/CameraEngine.kt")
        val viewfinder = read("camera/Viewfinder.kt")
        val location = read("camera/ExifLocation.kt")
        assertTrue(stripProblems(mutate(engine, "if (!keepLocation) stripLocation(exif) exif.saveAttributes()", "exif.saveAttributes()"), viewfinder, location).isNotEmpty())
        assertTrue(stripProblems(mutate(engine, "if (!keepLocation) stripLocation(exif)", "if (keepLocation) stripLocation(exif)"), viewfinder, location).isNotEmpty())
        assertTrue(stripProblems(mutate(engine, "writeExif(file, manual, result, withLocation)", "writeExif(file, manual, result, true)"), viewfinder, location).isNotEmpty())
        assertTrue(stripProblems(mutate(engine, "if (withLocation) location?.let { metadata.location = it }", "location?.let { metadata.location = it }"), viewfinder, location).isNotEmpty())
        assertTrue(stripProblems(engine, mutate(viewfinder, "engine.takePhoto(sink.keepsLocation) {", "engine.takePhoto(true) {"), location).isNotEmpty())
        assertTrue(stripProblems(engine, viewfinder, mutate(location, "final override val keepsLocation: Boolean get() = false", "override val keepsLocation: Boolean get() = false")).isNotEmpty())
        assertTrue(stripProblems(engine, viewfinder, mutate(location, "GPS_TAGS.forEach { set(it, null) }", "GPS_TAGS.take(5).forEach { set(it, null) }")).isNotEmpty())
    }

    // ------------------------------------------------------------------------------------------------------ the viewer

    private fun viewerProblems(activity: String, screen: String, sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        if (count(activity, "nav.open(") != 1 || !activity.contains("nav.open(intent, AndroidUriAccess(this))")) problems += "the viewer is not opened with the real launch intent and the real port"
        if (activity.contains("mayChange") || activity.contains("Uri.parse") || activity.contains("ViewerRules.")) problems += "the activity decides something itself"
        // ViewerNav's state is the rule's answer, and the URI it holds is the one the rule named.
        val nav = body(screen, "class ViewerNav")
        val open = body(nav, "fun open(intent: Intent?, access: UriAccessPort)")
        val form = "{ mime = runCatching { intent?.type }.getOrNull() val state = ViewerRules.state({ intent?.data?.toString() }, access) " +
            "state.lines.forEach { Diagnostics.add(\"photosapp\", it) } refused = state.refused mayChange = state.mayChange uri = state.uri?.let(Uri::parse) }"
        if (open != form) problems += "ViewerNav.open is not exactly ViewerRules.state's answer:\n  is:      $open\n  must be: $form"
        for (field in listOf("uri", "refused", "mayChange")) {
            if (Regex("\\b$field\\s*=[^=]").findAll(nav).count() != 1) problems += "ViewerNav.$field is set outside open"
            if (!nav.contains("var $field by mutableStateOf") || !Regex("var $field by mutableStateOf(<Uri\\?>)?\\((null|false)\\) private set").containsMatchIn(nav)) problems += "ViewerNav.$field does not start closed, or can be set from outside"
        }
        // The screen shows the nav's URI only, and hands the viewer the nav's mayChange.
        val view = body(screen, "fun ViewerScreen(nav: ViewerNav, activity: ComponentActivity)")
        if (!view.contains("val uri = nav.uri") || !view.contains("if (uri == null) { ViewerError()") || count(view, "PhotoViewer(") != 1 ||
            !view.contains("PhotoViewer(listOf(item), item.key, null, false, { null }, activity, mayChange = nav.mayChange)") || view.contains("intent")
        ) problems += "the viewer's screen shows something other than the nav's URI with the nav's mayChange"
        // mayChange has no default, every caller names it, and the bar is the rule's list.
        val declared = screen.indexOf("fun BoxScope.PhotoViewer(").let { if (it < 0) "" else call(screen, it) }
        if (!declared.contains("mayChange: Boolean, onClosed: () -> Unit,") || declared.contains("mayChange: Boolean =")) problems += "PhotoViewer's mayChange has a default"
        val calls = sources.flatMap { (file, text) -> Regex("(?<!BoxScope\\.)\\bPhotoViewer\\(").findAll(text).map { file to call(text, it.range.first) } }
        if (calls.size != 2 || calls.any { (_, c) -> !c.contains("mayChange = ") }) problems += "a PhotoViewer call does not say mayChange: ${calls.map { it.first }}"
        if (calls.filter { (file, _) -> file != "photos/ViewerScreen.kt" }.map { (file, c) -> file to c.substringAfter("mayChange = ").removeSuffix(")") } != listOf("photos/PhotosOverlays.kt" to "true")) {
            problems += "mayChange = true is said somewhere other than Photos' own viewer"
        }
        val viewer = body(screen, "fun BoxScope.PhotoViewer(")
        if (!viewer.contains("val offered = ViewerRules.actions(hasRow = entry != null, editable = entry != null && entry.mime in EDITABLE, several = items.size > 1, mayChange = mayChange)") || count(viewer, "ViewerRules.actions(") != 1) {
            problems += "the actions offered are not ViewerRules.actions with this viewer's mayChange"
        }
        for ((tag, gate) in listOf(
            "\"viewer_edit\"" to "if (ViewerRules.Action.EDIT in offered) add(PhotoBarButton(Glyph.EDIT, \"Edit\", \"viewer_edit\")",
            "\"viewer_delete\"" to "if (ViewerRules.Action.DELETE in offered) add(PhotoBarButton(Glyph.DELETE, \"Delete\", \"viewer_delete\")",
            "\"viewer_menu_setas\"" to "if (ViewerRules.Action.SET_AS in offered) add(PhotoMenuEntry(\"Set as\", \"viewer_menu_setas\")",
            "\"viewer_menu_info\"" to "if (ViewerRules.Action.FILE_INFORMATION in offered) add(PhotoMenuEntry(\"File information\", \"viewer_menu_info\"",
        )) if (count(viewer, tag) != 1 || !viewer.contains(gate)) problems += "$tag is offered other than by the rule's list"
        if (!viewer.contains("if (setAsOpen && entry != null && ViewerRules.Action.SET_AS in offered)")) problems += "the Set as choices open without the rule"
        // Living Images (the head read and the clip's playback open the picture's URI with the shell's identity): only
        // inside PhotoViewer, on an item it was given — and the other apps' viewer is given the one item made from the
        // nav's URI, which is null unless the rule said the picture is shown. Never from the intent, never before.
        if (!view.contains("ViewerItem(\"u:\$uri\", uri, entry, entry?.mime ?: nav.mime, entry?.id?.toString() ?: \"external\")") || count(view, "ViewerItem(") != 1 || view.contains("Living")) {
            problems += "the other apps' viewer builds its item from something other than the nav's URI, or opens a Living Image itself"
        }
        val byUri = Regex("LivingImages\\.of\\([^()]*\\buri\\b[^()]*\\)|LivingPlayback\\.start\\(")
        val livingSites = sources.flatMap { (file, text) -> byUri.findAll(text).map { file } }
        if (livingSites != listOf("photos/ViewerScreen.kt", "photos/ViewerScreen.kt") ||
            !viewer.contains("val clip = if (item.entry != null) LivingImages.of(activity, item.entry) else LivingImages.of(activity, item.uri, item.mime)") ||
            !viewer.contains("living = LivingPlayback.start(activity, item.key, item.id, item.uri, clip)")
        ) problems += "a Living Image is read or played from a URI other than a shown item's: $livingSites"
        for ((file, text) in sources) if (file != "photos/LivingImages.kt" && file != "photos/LivingPlayback.kt" && file != "photos/ViewerScreen.kt" && file != "photos/LibraryPages.kt" && Regex("\\bLiving(Images|Playback)\\.").containsMatchIn(text)) {
            problems += "$file opens a Living Image"
        }
        return problems
    }

    @Test fun `the viewer opens only the URI the rule named, and offers only what the rule's mayChange allows`() {
        assertEquals(emptyList<String>(), viewerProblems(read("photos/ViewerActivity.kt"), read("photos/ViewerScreen.kt"), SourceScan.all()))
    }

    @Test fun `a viewer that opens a refused URI, says mayChange itself or lets the bar ignore the rule is caught`() {
        val activity = read("photos/ViewerActivity.kt")
        val screen = read("photos/ViewerScreen.kt")
        val sources = SourceScan.all()
        fun screen(old: String, new: String) = mutate(screen, old, new).let { viewerProblems(activity, it, sources + ("photos/ViewerScreen.kt" to it)) }
        // The reviewers' surviving mutations: a refused URI still opened; mayChange = true; the bar ignoring the rule.
        assertTrue(screen("uri = state.uri?.let(Uri::parse)", "uri = intent?.data").isNotEmpty())
        assertTrue(screen("mayChange = state.mayChange", "mayChange = true").isNotEmpty())
        assertTrue(screen("refused = state.refused", "refused = false").isNotEmpty())
        assertTrue(screen("activity, mayChange = nav.mayChange)", "activity, mayChange = true)").isNotEmpty())
        assertTrue(screen("several = items.size > 1, mayChange = mayChange)", "several = items.size > 1, mayChange = true)").isNotEmpty())
        assertTrue(screen("if (ViewerRules.Action.EDIT in offered) add(", "if (entry != null) add(").isNotEmpty())
        assertTrue(screen("if (ViewerRules.Action.DELETE in offered) add(", "if (entry != null) add(").isNotEmpty())
        assertTrue(screen("if (ViewerRules.Action.SET_AS in offered) add(", "if (entry != null) add(").isNotEmpty())
        assertTrue(screen("if (setAsOpen && entry != null && ViewerRules.Action.SET_AS in offered)", "if (setAsOpen && entry != null)").isNotEmpty())
        // Living Images: the clip read or played from the intent's own URI, before or beside the rule.
        assertTrue(screen("else LivingImages.of(activity, item.uri, item.mime)", "else LivingImages.of(activity, activity.intent.data!!, item.mime)").isNotEmpty())
        assertTrue(screen("LivingPlayback.start(activity, item.key, item.id, item.uri, clip)", "LivingPlayback.start(activity, item.key, item.id, activity.intent.data!!, clip)").isNotEmpty())
        assertTrue(screen("val uri = nav.uri Box(", "val uri = nav.uri LaunchedEffect(Unit) { LivingImages.of(activity, activity.intent.data!!, nav.mime) } Box(").isNotEmpty())
        assertTrue(screen("ViewerItem(\"u:\$uri\", uri, entry,", "ViewerItem(\"u:\$uri\", activity.intent.data!!, entry,").isNotEmpty())
        assertTrue(viewerProblems(activity, screen, sources + ("photos/ViewerActivity.kt" to sources.getValue("photos/ViewerActivity.kt") + " suspend fun x(a: ViewerActivity) = LivingImages.of(a, a.intent.data!!, null)")).isNotEmpty())
        // The fail-open default back; a field opened to the outside; the rule handed something other than the launch.
        assertTrue(screen("mayChange: Boolean, onClosed: () -> Unit,", "mayChange: Boolean = true, onClosed: () -> Unit,").isNotEmpty())
        assertTrue(screen("var mayChange by mutableStateOf(false) private set", "var mayChange by mutableStateOf(true) private set").isNotEmpty())
        assertTrue(screen("var mayChange by mutableStateOf(false) private set", "var mayChange by mutableStateOf(false)").isNotEmpty())
        assertTrue(screen("ViewerRules.state({ intent?.data?.toString() }, access)", "ViewerRules.state({ \"content://media/external/images/media/1\" }, access)").isNotEmpty())
        assertTrue(viewerProblems(mutate(activity, "nav.open(intent, AndroidUriAccess(this))", "nav.open(intent, FixedAccess)"), screen, sources).isNotEmpty())
        // Another caller of PhotoViewer that says nothing, or says true.
        val overlays = sources.getValue("photos/PhotosOverlays.kt")
        assertTrue(viewerProblems(activity, screen, sources + ("photos/PhotosOverlays.kt" to mutate(overlays, "activity, mayChange = true) { nav.closeViewer() }", "activity) { nav.closeViewer() }"))).isNotEmpty())
        assertTrue(viewerProblems(activity, screen, sources + ("photos/EditScreen.kt" to sources.getValue("photos/EditScreen.kt") + " fun x() { PhotoViewer(a, b, null, false, { null }, c, mayChange = true) { } }")).isNotEmpty())
    }

    // ------------------------------------------------------------------------------------------------------ the player

    private fun playerProblems(player: String, playback: String): List<String> {
        val problems = mutableListOf<String>()
        // Every source is decided by the rule, from the real URI and the real port; a refused one stops there.
        val open = body(player, "private fun open(source: Uri?)")
        val decided = "val opened = PlayerRules.source(source?.toString(), source?.scheme, source?.encodedAuthority, source?.path, ownRoots(), launch.own, AndroidUriAccess(this)) { File(it).canonicalPath } " +
            "opened.lines.forEach { Diagnostics.add(\"video\", it) } request = opened.request val req = opened.request " +
            "if (opened.failure != null || req !is PlayerRequest.Play) { ui.failure = opened.failure ?: PlayerRules.refused ui.hasSurface = false return } "
        val at = open.indexOf(decided)
        if (at < 0 || count(player, "PlayerRules.source(") != 1) problems += "a source is not decided by PlayerRules.source, or a refused one does not return"
        for (later in listOf("io.execute {", "lookUp(source!!, req)")) if (at < 0 || open.indexOf(later) < at + decided.length) problems += "$later is reached before the source is decided"
        if (Regex("\\brequest\\s*=[^=]").findAll(player).count() != 1 || !player.contains("private var request: PlayerRequest = PlayerRequest.Unsupported(\"none\")")) problems += "the request is set outside the rule's answer"
        // A path another app named is never resolved here: the only resolving is the rule's own, and the shell's own roots.
        if (count(player, "canonicalPath") != 3 || !player.contains("runCatching { dataDir.canonicalPath }.getOrNull(), runCatching { getExternalFilesDir(null)?.canonicalPath }.getOrNull(),")) problems += "a path is made canonical outside the rule"
        // Nothing of a source is opened or queried except for the Play request the rule made.
        val begin = body(player, "private fun begin()")
        if (!begin.startsWith("{ val source = uri ?: return val req = request as? PlayerRequest.Play ?: return if (ui.failure != null) return ")) problems += "the player is made for something other than the rule's Play request"
        if (count(player, ".setUri(source)") != 1 || !begin.contains(".setUri(source)") || count(player, "MediaItem.Builder()") != 1) problems += "a media item is built outside begin"
        val lookUp = body(player, "private fun lookUp(source: Uri, req: PlayerRequest.Play): Pair<String?, Uri?>")
        if (count(player, "contentResolver.query(") != 2 || count(lookUp, "contentResolver.query(") != 2 || count(player, "lookUp(") != 2) problems += "a provider is queried outside the look-up of an accepted source"
        if (!lookUp.contains("val sidecar = PlayerAccess.subtitleLookedUp(launch.own, req.mediaStoreId) val columns = if (sidecar) arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH) else arrayOf(OpenableColumns.DISPLAY_NAME)")) {
            problems += "the subtitle beside a video is looked for without PlayerAccess.subtitleLookedUp"
        }
        // The title an intent names: the rule's, with who launched.
        if (count(player, "getStringExtra(") != 1 || !player.contains("PlayerRules.sessionTitle(launch.own, { intent?.getStringExtra(EXTRA_TITLE) }, displayName, req.name)") || count(player, ".setTitle(") != 1 || !player.contains(".setTitle(titleOf(req))")) {
            problems += "the session title is not PlayerRules.sessionTitle with who launched"
        }
        // What the data source may open: the rule's answer for every address, checked before the upstream opens anything.
        if (!begin.contains("val launchUri = source.toString() val sidecar = subtitle?.toString() val p = VideoPlayback.acquire(this, resolver) { asked -> PlayerAccess.mayOpen(req.scheme, launchUri, sidecar, asked) }")) problems += "the data source is not given PlayerAccess.mayOpen for this source"
        if (!playback.contains("val sources = DataSource.Factory { GuardedDataSource(resolved.createDataSource(), mayOpen) }") || count(playback, ".setDataSourceFactory(") != 1 || !playback.contains(".setDataSourceFactory(sources)") ||
            count(playback, "DefaultDataSource.Factory(") != 1 || count(playback, "DefaultMediaSourceFactory(") != 1
        ) problems += "the media source factory's data source is not the guarded one"
        if (!playback.contains("override fun open(dataSpec: DataSpec): Long { if (!mayOpen(dataSpec.uri.toString())) throw RefusedSourceException() return upstream.open(dataSpec) }") || count(playback, "upstream.open(") != 1) {
            problems += "the guarded data source opens before it asks"
        }
        if (!playback.contains("fun acquire(context: Context, resolver: ((DataSpec) -> DataSpec)?, mayOpen: (String) -> Boolean): VideoPlayback") || !playback.contains("VideoPlayback(context.applicationContext, r, mayOpen)")) problems += "a player can be made without the rule for what it may open"
        return problems
    }

    @Test fun `the player decides every source by the rule before anything of it is opened, and its data source opens only what the rule allows`() {
        assertEquals(emptyList<String>(), playerProblems(read("video/PlayerActivity.kt"), read("video/VideoPlayback.kt")))
    }

    @Test fun `a player that opens a refused source, honours another app's title or queue, or opens whatever it is asked is caught`() {
        val player = read("video/PlayerActivity.kt")
        val playback = read("video/VideoPlayback.kt")
        fun player(old: String, new: String) = playerProblems(mutate(player, old, new), playback)
        fun playback(old: String, new: String) = playerProblems(player, mutate(playback, old, new))
        // The reviewers' surviving mutations (the rest of them are `video/TrustWiringScanTest`'s and the port's).
        assertTrue(player("ui.failure = opened.failure ?: PlayerRules.refused ui.hasSurface = false return }", "ui.failure = opened.failure ?: PlayerRules.refused ui.hasSurface = false }").isNotEmpty())
        assertTrue(player("if (opened.failure != null || req !is PlayerRequest.Play) {", "if (false) {").isNotEmpty())
        assertTrue(player("ownRoots(), launch.own, AndroidUriAccess(this)) { File(it).canonicalPath }", "ownRoots(), true, AndroidUriAccess(this)) { File(it).canonicalPath }").isNotEmpty())
        assertTrue(player("PlayerRules.source(source?.toString(), source?.scheme,", "PlayerRules.source(\"http://x/\", source?.scheme,").isNotEmpty())
        assertTrue(player("request = opened.request val req", "request = PlayerRules.classify(source?.scheme, source?.encodedAuthority, source?.path, ownRoots()) val req").isNotEmpty())
        assertTrue(player("val req = request as? PlayerRequest.Play ?: return if (ui.failure != null) return", "val req = PlayerRequest.Play(\"content\", null, null, \"x\")").isNotEmpty())
        // C2-L9: the path resolved before the decision. C2-L3: the title for every caller. The subtitle for every caller.
        assertTrue(player("ui.cue = \"\" val opened", "ui.cue = \"\" val early = source?.path?.let { File(it).canonicalPath } val opened").isNotEmpty())
        assertTrue(player("PlayerRules.sessionTitle(launch.own, {", "PlayerRules.sessionTitle(true, {").isNotEmpty())
        assertTrue(player(".setTitle(titleOf(req))", ".setTitle(intent?.getStringExtra(EXTRA_TITLE))").isNotEmpty())
        assertTrue(player("PlayerAccess.subtitleLookedUp(launch.own, req.mediaStoreId)", "(req.mediaStoreId != null)").isNotEmpty())
        // C2-M3: the data source opening anything, the guard asked after the open, or left out of the factory.
        assertTrue(player("{ asked -> PlayerAccess.mayOpen(req.scheme, launchUri, sidecar, asked) }", "{ true }").isNotEmpty())
        assertTrue(player("PlayerAccess.mayOpen(req.scheme, launchUri, sidecar, asked)", "PlayerAccess.mayOpen(\"http\", launchUri, sidecar, asked)").isNotEmpty())
        assertTrue(playback("if (!mayOpen(dataSpec.uri.toString())) throw RefusedSourceException() return upstream.open(dataSpec)", "return upstream.open(dataSpec)").isNotEmpty())
        assertTrue(playback("if (!mayOpen(dataSpec.uri.toString())) throw RefusedSourceException() return upstream.open(dataSpec)", "val n = upstream.open(dataSpec) if (!mayOpen(dataSpec.uri.toString())) throw RefusedSourceException() return n").isNotEmpty())
        assertTrue(playback(".setDataSourceFactory(sources)", ".setDataSourceFactory(resolved)").isNotEmpty())
        assertTrue(playback("GuardedDataSource(resolved.createDataSource(), mayOpen)", "GuardedDataSource(resolved.createDataSource()) { true }").isNotEmpty())
    }

    // -------------------------------------------------------------------------------- one write layer per process

    private fun writeLayerProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        fun where(pattern: Regex) = sources.filterValues { pattern.containsMatchIn(it) }.keys.sorted()
        if (where(Regex("(?<![\\w.])MediaWrites\\(")) != listOf("media/ShellMediaWrites.kt")) problems += "a MediaWrites is constructed outside ShellMediaWrites: ${where(Regex("(?<![\\w.])MediaWrites\\("))}"
        if (where(Regex("FilePendingLedger")) != listOf("media/MediaWrites.kt", "media/ShellMediaWrites.kt")) problems += "the ledger file is touched outside the write layer: ${where(Regex("FilePendingLedger"))}"
        if (where(Regex("MemoryPendingLedger")) != listOf("media/MediaWrites.kt")) problems += "a process uses the tests' in-memory ledger"
        if (where(Regex("(?<!class )AndroidMediaStorePort\\(")) != listOf("media/ShellMediaWrites.kt")) problems += "the MediaStore port is made outside ShellMediaWrites"
        val shell = sources["media/ShellMediaWrites.kt"].orEmpty()
        val form = "object ShellMediaWrites { @Volatile private var layer: MediaWrites<IntentSender>? = null " +
            "fun of(context: Context): MediaWrites<IntentSender> = layer ?: synchronized(this) { layer ?: build(context.applicationContext).also { layer = it } } " +
            "private fun build(app: Context): MediaWrites<IntentSender> { " +
            "val process = Application.getProcessName().substringAfter(':', \"main\").filter { it.isLetterOrDigit() }.ifEmpty { \"main\" } " +
            "return MediaWrites(AndroidMediaStorePort(app), app.packageName, FilePendingLedger.of(File(File(app.filesDir, \"media_pending\"), \"\$process.txt\"))) } }"
        if (shell.substringAfter("import java.io.File ", "").trim() != form) problems += "ShellMediaWrites is not one layer per process over that process's own ledger file:\n  is:      ${shell.substringAfter("import java.io.File ", "").trim()}\n  must be: $form"
        val layer = sources["media/MediaWrites.kt"].orEmpty()
        if (!layer.contains("class FilePendingLedger private constructor(private val file: java.io.File) : PendingLedger {") ||
            Regex("(?<!class )FilePendingLedger\\(").findAll(layer).count() != 1 || !layer.contains("perFile.getOrPut(key) { FilePendingLedger(java.io.File(key)) }")
        ) problems += "a second ledger object can be made for one file"
        // The three users of the layer take it from ShellMediaWrites.of.
        for (file in listOf("camera/CameraSaver.kt", "photos/EditRender.kt", "photos/PhotoActions.kt")) {
            if (!sources[file].orEmpty().contains("app.tileshell.media.ShellMediaWrites.of(context)")) problems += "$file does not take its write layer from ShellMediaWrites.of"
        }
        return problems
    }

    @Test fun `every process builds its write layer through ShellMediaWrites of - one layer and one ledger file per process`() {
        assertEquals(emptyList<String>(), writeLayerProblems(SourceScan.all()))
    }

    @Test fun `one ledger file for every process, a second layer, or a ledger made by hand is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = writeLayerProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        // The reviewer's surviving mutation: one ledger file for all the shell's processes.
        assertTrue(with("media/ShellMediaWrites.kt", "\"\$process.txt\"", "\"all.txt\"").isNotEmpty())
        assertTrue(with("media/ShellMediaWrites.kt", "Application.getProcessName().substringAfter(':', \"main\")", "\"main\"").isNotEmpty())
        // A new layer per call (two writers of one process, two ledger objects), or a ledger made by hand.
        assertTrue(with("media/ShellMediaWrites.kt", "= layer ?: synchronized(this) { layer ?: build(context.applicationContext).also { layer = it } }", "= build(context.applicationContext)").isNotEmpty())
        assertTrue(with("media/MediaWrites.kt", "class FilePendingLedger private constructor(", "class FilePendingLedger(").isNotEmpty())
        assertTrue(with("media/MediaWrites.kt", "perFile.getOrPut(key) { FilePendingLedger(java.io.File(key)) }", "FilePendingLedger(java.io.File(key))").isNotEmpty())
        assertTrue(with("camera/CameraSaver.kt", "app.tileshell.media.ShellMediaWrites.of(context)", "MediaWrites(app.tileshell.media.AndroidMediaStorePort(context), context.packageName, app.tileshell.media.MemoryPendingLedger())").isNotEmpty())
        assertTrue(with("photos/PhotoActions.kt", "app.tileshell.media.ShellMediaWrites.of(context)", "app.tileshell.media.MediaWrites(app.tileshell.media.AndroidMediaStorePort(context), context.packageName, app.tileshell.media.FilePendingLedger.of(java.io.File(context.filesDir, \"x\")))").isNotEmpty())
    }

    // ------------------------------------------------------------------ phase 20: a station's text and its logo (review R20-4)

    /** `MusicPlayer.readSession`'s title, as code: a station's is the stream's own StreamTitle only through `LiveMetadata.merge`. */
    private val liveTitleForm = "title = if (live) LiveMetadata.merge(meta?.albumTitle?.toString(), c.mediaMetadata.title?.toString()).title else meta?.title?.toString().orEmpty()"

    /** `KnownDurationPlayer.getMediaMetadata`, as code: the ITEM's own metadata, the merged title, the shell's logo. */
    private val liveMetadataForm = "{ val reported = super.getMediaMetadata() " +
        "val item = currentMediaItem ?: return reported " +
        "if (!MusicLive.isLive(item.mediaId)) return reported " +
        "val own = item.mediaMetadata " +
        "val shown = LiveMetadata.merge(own.albumTitle?.toString(), reported.title?.toString()) " +
        "val meta = own.buildUpon().setTitle(shown.title).setAlbumTitle(shown.albumTitle) " +
        "if (own.artworkData == null) liveArt(item.mediaId)?.let { meta.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } " +
        "return meta.build() }"

    /** `KnownDurationPlayer.liveMetadataChanged`, as code: the listeners are told the value above, never another. */
    private val liveRetellForm = "{ if (!MusicLive.isLive(currentMediaItem?.mediaId)) return " +
        "val shown = mediaMetadata " +
        "listeners.forEach { it.onMediaMetadataChanged(shown) } }"

    /** `StationLogos.logo`, as code: the address by the rule, a header (so no redirect is followed), the cap, then the bounds. */
    private val logoFetchForm = "{ val key = key(station) " +
        "cache.get(key)?.let { return it.bytes } " +
        "val url = StationLogo.url(station.favicon, RadioNet.qaHost(app)) " +
        "if (url != null && !VideoHttp.online(app)) return null " +
        "val bytes = url?.let { VideoHttp.bytes(it, RadioNet.headers(), StationLogo.MAX_BYTES.toLong()) }?.takeIf { StationLogo.accept(it) }?.let { bounded(it) } " +
        "cache.put(key, Entry(bytes)) " +
        "return bytes }"

    /**
     * Phase 20 Decisions "live metadata" and "untrusted URLs" → Logos (r3 D9 / D2), the review's R20-4: the pure rules
     * (`LiveMetadata`, `RadioText`, `StationLogo`) are unit-tested, and what calls them was held by nothing — four of
     * the review's mutations survived. So each call site is its one form:
     *  - `MusicPlayer.readSession` shows a station's title only through `LiveMetadata.merge` (which cleans and cuts a
     *    StreamTitle), and sets the title nowhere else;
     *  - `KnownDurationPlayer.getMediaMetadata` — what the tile and the notification are built from — is the ITEM's
     *    own metadata with the merged title, never the stream's; and a retelling tells that same value;
     *  - `StationLogos.logo` asks for the address `StationLogo.url` accepted, with `RadioNet.headers()` (a request with
     *    a header follows no redirect) and `StationLogo.MAX_BYTES`, and keeps only what `accept` and `bounded` pass;
     *    nothing else under `music/radio/` makes a request for a picture.
     */
    private fun liveWiringProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val player = sources["music/MusicPlayer.kt"].orEmpty()
        val known = sources["music/KnownDurationPlayer.kt"].orEmpty()
        val logos = sources["music/radio/StationLogos.kt"].orEmpty()
        if (count(player, liveTitleForm) != 1 || Regex("\\btitle\\s*=(?!=)").findAll(body(player, "private fun readSession()")).count() != 1) problems += "MusicPlayer.readSession's title is not its one form: a station's through LiveMetadata.merge"
        val metadata = body(known, "override fun getMediaMetadata(): MediaMetadata")
        if (metadata != liveMetadataForm) problems += "KnownDurationPlayer.getMediaMetadata is not its one form:\n  is:      $metadata\n  must be: $liveMetadataForm"
        val retell = body(known, "fun liveMetadataChanged()")
        if (retell != liveRetellForm) problems += "KnownDurationPlayer.liveMetadataChanged is not its one form:\n  is:      $retell\n  must be: $liveRetellForm"
        if (count(known, "onMediaMetadataChanged(") != 2 || count(known, "private val retell = Runnable { liveMetadataChanged() }") != 1) problems += "KnownDurationPlayer tells a listener a station's metadata somewhere other than liveMetadataChanged"
        val fetch = body(logos, "fun logo(station: Station): ByteArray?")
        if (fetch != logoFetchForm) problems += "StationLogos.logo is not its one form:\n  is:      $fetch\n  must be: $logoFetchForm"
        val requests = sources.filterKeys { it.startsWith("music/radio/") }.mapValues { (_, text) -> Regex("\\bVideoHttp\\s*\\.\\s*(?:bytes|hop|image)\\b|\\bopenConnection\\b|\\bopenStream\\b").findAll(text).count() }.filterValues { it > 0 }
        if (requests != mapOf("music/radio/StationLogos.kt" to 1)) problems += "a picture is asked for under music/radio/ outside StationLogos.logo: $requests"
        return problems
    }

    @Test fun `phase 20 R20-4 a station's title, the session's metadata and the logo fetch are each their one form`() {
        val sources = SourceScan.all()
        assertEquals(emptyList<String>(), liveWiringProblems(sources))
        for (file in listOf("music/MusicPlayer.kt", "music/KnownDurationPlayer.kt", "music/radio/StationLogos.kt")) assertTrue("$file was read", sources.getValue(file).length > 200)
    }

    @Test fun `phase 20 R20-4 a raw StreamTitle shown or told, the stream's own metadata passed on, or a logo fetched with no header or no cap is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = liveWiringProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        fun added(file: String, code: String) = liveWiringProblems(sources + (file to sources.getValue(file) + " " + code))
        // The review's four surviving mutations, as it wrote them: M33, M34, M36, M35.
        assertTrue(with("music/MusicPlayer.kt", "title = if (live) LiveMetadata.merge(meta?.albumTitle?.toString(), c.mediaMetadata.title?.toString()).title else", "title = if (live) c.mediaMetadata.title?.toString().orEmpty() else").isNotEmpty())
        assertTrue(with("music/KnownDurationPlayer.kt", "val meta = own.buildUpon().setTitle(shown.title)", "val meta = reported.buildUpon().setTitle(shown.title)").isNotEmpty())
        assertTrue(with("music/KnownDurationPlayer.kt", "val shown = LiveMetadata.merge(own.albumTitle?.toString(), reported.title?.toString())", "val shown = LiveMetadata.Shown(reported.title?.toString() ?: own.albumTitle?.toString().orEmpty(), own.albumTitle?.toString().orEmpty())").isNotEmpty())
        assertTrue(with("music/radio/StationLogos.kt", "VideoHttp.bytes(it, RadioNet.headers(), StationLogo.MAX_BYTES.toLong())", "VideoHttp.bytes(it)").isNotEmpty())
        // Their neighbours: the cap alone, the header alone, the size check or the bounds skipped, the address unchecked.
        assertTrue(with("music/radio/StationLogos.kt", "VideoHttp.bytes(it, RadioNet.headers(), StationLogo.MAX_BYTES.toLong())", "VideoHttp.bytes(it, RadioNet.headers())").isNotEmpty())
        assertTrue(with("music/radio/StationLogos.kt", "VideoHttp.bytes(it, RadioNet.headers(), StationLogo.MAX_BYTES.toLong())", "VideoHttp.bytes(it, emptyMap(), StationLogo.MAX_BYTES.toLong())").isNotEmpty())
        assertTrue(with("music/radio/StationLogos.kt", "?.takeIf { StationLogo.accept(it) }?.let { bounded(it) }", "?.let { bounded(it) }").isNotEmpty())
        assertTrue(with("music/radio/StationLogos.kt", "?.takeIf { StationLogo.accept(it) }?.let { bounded(it) }", "?.takeIf { StationLogo.accept(it) }").isNotEmpty())
        assertTrue(with("music/radio/StationLogos.kt", "val url = StationLogo.url(station.favicon, RadioNet.qaHost(app))", "val url = station.favicon.takeIf { it.isNotEmpty() }").isNotEmpty())
        assertTrue(added("music/radio/StreamWatch.kt", "fun x(u: String) = app.tileshell.video.catalogue.VideoHttp.bytes(u)").isNotEmpty())
        // A second title for a station; the stream's metadata told to the listeners as it came.
        assertTrue(with("music/MusicPlayer.kt", "artist = meta?.artist?.toString().orEmpty()", "artist = meta?.artist?.toString().orEmpty(); if (live) title = c.mediaMetadata.title.toString()").isNotEmpty())
        assertTrue(with("music/KnownDurationPlayer.kt", "val shown = mediaMetadata listeners", "val shown = super.getMediaMetadata() listeners").isNotEmpty())
        assertTrue(with("music/KnownDurationPlayer.kt", "if (!MusicLive.isLive(item.mediaId)) return reported val own", "if (MusicLive.isLive(item.mediaId)) return reported val own").isNotEmpty())
        assertTrue(with("music/KnownDurationPlayer.kt", "handler.post(retell)", "handler.post(retell); listeners.forEach { it.onMediaMetadataChanged(mediaMetadata) }").isNotEmpty())
    }
}
