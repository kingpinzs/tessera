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
 *  - every process builds its write layer through `ShellMediaWrites.of` (one layer, one ledger file per process).
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
        if (made != mapOf("camera/CaptureActivity.kt" to 1, "photos/ViewerActivity.kt" to 1, "video/PlayerActivity.kt" to 2)) problems += "the port is made other than by the three activities, each for itself: $made"
        val all = sources.filterKeys { it != "media/AndroidUriAccess.kt" }.values.sumOf { Regex("(?<!class )AndroidUriAccess\\(").findAll(it).count() }
        if (all != 4) problems += "AndroidUriAccess is constructed $all times, not the four of the three activities"
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
}
