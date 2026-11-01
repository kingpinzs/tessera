package app.tileshell.process

import app.tileshell.media.SourceScan
import app.tileshell.media.SourceScan.body
import app.tileshell.media.SourceScan.count
import app.tileshell.media.SourceScan.mutate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3 (a): the viewer other apps open runs in `:photosedit`, and what it needs from the launcher's process is asked of
 * that process, never done in its own. Android cannot run here, so this reads the wiring as code ([SourceScan]: comments
 * removed, white space collapsed) and holds each call site to its one form; each check is shown to catch its mutation.
 *  - its lines: the `:photosedit` ring is held for the process's life (read on Diagnostics while it shows and after);
 *  - its theme: re-read from the launcher's process when the process is warm, before the first frame;
 *  - Set as Start background: the theme's one writer is the launcher's process;
 *  - the Search key: Cortana's session is the launcher's process's to show;
 *  - and (D3 fix round) the code those call sites call, whole: the provider's caller refusal, the Search key's
 *    hand-off with its role-notice fallback, the background answer read, the held copy, the copy adopted unwritten.
 */
class ProcessWiringScanTest {
    private val viewer = SourceScan.read("photos/ViewerActivity.kt")
    private val ring = SourceScan.read("diag/RingDumpService.kt")
    private val actions = SourceScan.read("photos/PhotoActions.kt")
    private val calls = SourceScan.read("process/LauncherCalls.kt")
    private val cortana = SourceScan.read("cortana/CortanaService.kt")
    private val settings = SourceScan.read("prefs/ShellSettings.kt")

    private fun viewerProblems(activity: String): List<String> {
        val problems = mutableListOf<String>()
        val create = body(activity, "override fun onCreate(savedInstanceState: Bundle?)")
        val hold = "RemoteRings.holdForProcess(this, PhotosEditDumpService::class.java)"
        val theme = "LauncherCalls.refreshTheme(this)"
        if (count(create, hold) != 1) problems += "the viewer does not hold the :photosedit ring for its process"
        if (count(create, theme) != 1) problems += "the viewer does not re-read the launcher's theme"
        val content = create.indexOf("setShellAppContent(")
        if (content < 0 || create.indexOf(hold) !in 0 until content || create.indexOf(theme) !in 0 until content) problems += "the ring or the theme comes after the first frame is set"
        if (count(activity, "RemoteRings.release(") != 0 || count(activity, "RemoteRings.hold(") != 0) problems += "the viewer's ring is held for the activity only (gone at its first Back)"
        return problems
    }

    private fun ringProblems(source: String): List<String> {
        val problems = mutableListOf<String>()
        val hold = body(source, "fun holdForProcess(context: Context, service: Class<out RingDumpService>)")
        for (piece in listOf("val app = context.applicationContext", "processHolds.hold(service.name)", "app.bindService(Intent(app, service), ", "Context.BIND_AUTO_CREATE)")) {
            if (count(hold, piece) != 1) problems += "holdForProcess lacks `$piece`"
        }
        if (hold.contains("unbindService") || hold.contains("release(")) problems += "holdForProcess lets the ring go"
        return problems
    }

    private fun backgroundProblems(photoActions: String, launcherCalls: String): List<String> {
        val problems = mutableListOf<String>()
        val set = body(photoActions, "suspend fun setAsBackground(")
        val routed = "val pointed = if (LauncherCalls.isLauncher(app)) { ShellSettings.get(app).update { it.copy(backgroundUri = Uri.fromFile(file).toString()) } true } else { LauncherCalls.setBackground(app, id) }"
        if (count(set, routed) != 1 || count(set, "ShellSettings.get(app).update") != 1) problems += "Set as Start background writes the theme other than in the launcher's process"
        if (count(set, "if (pointed) {") != 1) problems += "Set as Start background says it was set without the launcher's answer"
        val call = body(launcherCalls, "override fun call(method: String, arg: String?, extras: Bundle?): Bundle?")
        val taken = "LauncherCalls.BACKGROUND -> { val file = ShellProcess.backgroundCopy(arg)?.let { File(ctx.filesDir, it) }?.takeIf { it.isFile }"
        if (count(call, taken) != 1) problems += "the launcher's process takes a background other than the copy the rule names"
        if (count(call, ".update {") != 1 || count(call, "ShellSettings.get(ctx).update { it.copy(backgroundUri = Uri.fromFile(file).toString()) }") != 1) problems += "the launcher's process writes more of the theme than the background"
        return problems
    }

    private fun cortanaProblems(service: String, launcherCalls: String): List<String> {
        val problems = mutableListOf<String>()
        val open = body(service, "fun open(context: Context, mode: CortanaMode, destination: CortanaDestinationKey? = null): Boolean")
        if (!open.startsWith("{ if (!LauncherCalls.isLauncher(context)) return LauncherCalls.openCortana(context, mode, destination) ")) problems += "Cortana is opened outside the launcher's process without asking it"
        val call = body(launcherCalls, "override fun call(method: String, arg: String?, extras: Bundle?): Bundle?")
        if (count(call, "LauncherCalls.CORTANA -> { val open = ShellProcess.cortanaOpen(arg, extras?.getString(LauncherCalls.KEY_DESTINATION))") != 1) problems += "the launcher's process opens Cortana other than as the rule reads the call"
        return problems
    }

    private fun processProblems(launcherCalls: String): List<String> {
        val problems = mutableListOf<String>()
        if (count(launcherCalls, "fun isLauncher(context: Context): Boolean = ShellProcess.isLauncher(Application.getProcessName(), context.packageName)") != 1) problems += "the launcher's process is not ShellProcess.isLauncher's answer"
        val refresh = body(launcherCalls, "fun refreshTheme(context: Context)")
        if (!refresh.startsWith("{ if (!ShellProcess.asksForTheme(isLauncher(context), ShellSettings.held())) return ")) problems += "the theme is asked for other than as ShellProcess.asksForTheme says"
        if (count(refresh, "ShellSettings.get(context).adopt(theme)") != 1 || count(refresh, ".update") != 0) problems += "the theme read is not adopted as it came, or is written"
        return problems
    }

    /** D3 fix round (L1): the provider's first statement refuses any caller whose uid is not the app's own ([ShellProcess.answersCaller]). */
    private fun callerProblems(launcherCalls: String): List<String> {
        val problems = mutableListOf<String>()
        val call = body(launcherCalls, "override fun call(method: String, arg: String?, extras: Bundle?): Bundle?")
        val refusal = "{ if (!ShellProcess.answersCaller(Binder.getCallingUid(), Process.myUid())) { Diagnostics.add(\"app\", \"launcher call refused: the caller is not this app\") return null } val ctx = context ?: return null "
        if (!call.startsWith(refusal)) problems += "the launcher's process reads a call before it refuses a caller that is not the shell"
        return problems
    }

    /**
     * D3 fix round (the hand-off note, and L2's m05b): the Search key's hand-off, exactly — ask the launcher's process,
     * and when it gives no answer (the call failed or came back empty) show the role notice here, the form the Search
     * key had in another process before D3 ([ShellProcess.noticeHere]). Pinned whole: nothing may be added before the ask.
     */
    private fun handOffProblems(launcherCalls: String): List<String> {
        val problems = mutableListOf<String>()
        val open = body(launcherCalls, "fun openCortana(context: Context, mode: CortanaMode, destination: CortanaDestinationKey?): Boolean")
        val expected = "{ val extras = destination?.let { Bundle().apply { putString(KEY_DESTINATION, it.name) } } " +
            "val answer = ask(context, CORTANA, mode.name, extras) { it.getBoolean(KEY_DONE) } " +
            "val said = when (answer) { true -> \"shown\"; false -> \"not shown\"; null -> \"no answer, so the role notice is shown here\" } " +
            "Diagnostics.add(\"cortana\", \"open(\$mode\${destination?.let { \", \$it\" } ?: \"\"}): asked the launcher's process: \$said\") " +
            "if (ShellProcess.noticeHere(answer)) context.startActivity(Intent(context, CortanaRoleNoticeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) " +
            "return answer == true }"
        if (open != expected) problems += "the Search key's hand-off is not: ask the launcher's process, and with no answer show the role notice here"
        return problems
    }

    /**
     * D3 fix round (L2): the code the call sites above call, held to its exact form, since Android cannot run it here —
     * Set as Start background reports the launcher's own answer (m11), a process holds a copy once it has read the
     * settings (m10: inverted, a warm process would never ask), and the copy is adopted, never written (m16: written,
     * `:photosedit` would rewrite the whole `start_theme` file from its older copy).
     */
    private fun calledProblems(launcherCalls: String, shellSettings: String): List<String> {
        val problems = mutableListOf<String>()
        if (count(launcherCalls, "fun setBackground(context: Context, id: Long): Boolean = ask(context, BACKGROUND, id.toString()) { it.getBoolean(KEY_DONE) } == true") != 1) problems += "Set as Start background reports other than the launcher's answer"
        if (count(shellSettings, "fun held(): Boolean = instance != null") != 1) problems += "held() is not whether this process has read the settings"
        if (body(shellSettings, "fun adopt(theme: StartTheme)") != "{ state.value = theme }") problems += "adopt does more than take the launcher's theme as this process's copy"
        return problems
    }

    @Test fun `what the call sites call keeps its one form - the answer read, the copy held, the copy adopted unwritten`() {
        assertEquals(emptyList<String>(), calledProblems(calls, settings))
    }

    @Test fun `a Search key hand-off the launcher's process does not answer falls back to the role notice`() {
        assertEquals(emptyList<String>(), handOffProblems(calls))
    }

    @Test fun `the launcher's process refuses a call from anything but the shell, before it reads the call`() {
        assertEquals(emptyList<String>(), callerProblems(calls))
    }

    @Test fun `the viewer holds the photosedit ring for its process and re-reads the theme before its first frame`() {
        assertEquals(emptyList<String>(), viewerProblems(viewer))
        assertEquals(emptyList<String>(), ringProblems(ring))
    }

    @Test fun `the theme has one writer - the launcher's process - and Set as Start background asks it`() {
        assertEquals(emptyList<String>(), backgroundProblems(actions, calls))
    }

    @Test fun `the Search key in another process asks the launcher's to open Cortana`() {
        assertEquals(emptyList<String>(), cortanaProblems(cortana, calls))
    }

    @Test fun `which process this is, and when the theme is asked for, are the rule's`() {
        assertEquals(emptyList<String>(), processProblems(calls))
    }

    @Test fun `each check catches its mutation`() {
        // The ring held for the activity only (the editor's form), or not at all; the theme read after the first frame.
        assertTrue(viewerProblems(mutate(viewer, "RemoteRings.holdForProcess(this, PhotosEditDumpService::class.java)", "RemoteRings.hold(this, PhotosEditDumpService::class.java)")).isNotEmpty())
        assertTrue(viewerProblems(mutate(viewer, "RemoteRings.holdForProcess(this, PhotosEditDumpService::class.java)", "")).isNotEmpty())
        assertTrue(viewerProblems(mutate(viewer, "LauncherCalls.refreshTheme(this)", "")).isNotEmpty())
        assertTrue(viewerProblems(mutate(viewer, "setShellAppContent(statusBar = false) { ViewerScreen(nav, this) }", "setShellAppContent(statusBar = false) { ViewerScreen(nav, this) }; RemoteRings.release(this, null)")).isNotEmpty())
        assertTrue(ringProblems(mutate(ring, "val app = context.applicationContext processHolds", "val app = context processHolds")).isNotEmpty())
        assertTrue(ringProblems(mutate(ring, "Context.BIND_AUTO_CREATE) }", "0) }")).isNotEmpty())
        // The theme written in this process whatever it is, the failure reported as a success, a path from the caller.
        assertTrue(backgroundProblems(mutate(actions, "if (LauncherCalls.isLauncher(app))", "if (true)"), calls).isNotEmpty())
        assertTrue(backgroundProblems(mutate(actions, "if (pointed) {", "if (true) {"), calls).isNotEmpty())
        assertTrue(backgroundProblems(actions, mutate(calls, "ShellProcess.backgroundCopy(arg)?.let { File(ctx.filesDir, it) }", "arg?.let { File(it) }")).isNotEmpty())
        assertTrue(backgroundProblems(actions, mutate(calls, "?.takeIf { it.isFile }", "")).isNotEmpty())
        // Cortana opened in this process, or from a mode the rule did not read.
        assertTrue(cortanaProblems(mutate(cortana, "if (!LauncherCalls.isLauncher(context)) return LauncherCalls.openCortana(context, mode, destination)", ""), calls).isNotEmpty())
        assertTrue(cortanaProblems(cortana, mutate(calls, "ShellProcess.cortanaOpen(arg, extras?.getString(LauncherCalls.KEY_DESTINATION))", "CortanaMode.valueOf(arg!!) to null")).isNotEmpty())
        // The launcher's process decided elsewhere, the theme asked for in the launcher's own process, or written back.
        assertTrue(processProblems(mutate(calls, "ShellProcess.isLauncher(Application.getProcessName(), context.packageName)", "true")).isNotEmpty())
        assertTrue(processProblems(mutate(calls, "ShellProcess.asksForTheme(isLauncher(context), ShellSettings.held())", "ShellProcess.asksForTheme(false, ShellSettings.held())")).isNotEmpty())
        assertTrue(processProblems(mutate(calls, "ShellSettings.get(context).adopt(theme)", "ShellSettings.get(context).update { theme }")).isNotEmpty())
        // A caller let through: the refusal dropped, decided on two copies of this process's own uid, or after the call is read.
        val refusal = "if (!ShellProcess.answersCaller(Binder.getCallingUid(), Process.myUid())) { Diagnostics.add(\"app\", \"launcher call refused: the caller is not this app\") return null } "
        assertTrue(callerProblems(mutate(calls, refusal, "")).isNotEmpty())
        assertTrue(callerProblems(mutate(calls, "ShellProcess.answersCaller(Binder.getCallingUid(), Process.myUid())", "ShellProcess.answersCaller(Process.myUid(), Process.myUid())")).isNotEmpty())
        assertTrue(callerProblems(mutate(calls, "${refusal}val ctx = context ?: return null ", "val ctx = context ?: return null $refusal")).isNotEmpty())
        // The hand-off silent again (no notice, or the rule bypassed), or refused in one process before it asks (m05b).
        assertTrue(handOffProblems(mutate(calls, "if (ShellProcess.noticeHere(answer)) context.startActivity(Intent(context, CortanaRoleNoticeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ", "")).isNotEmpty())
        assertTrue(handOffProblems(mutate(calls, "if (ShellProcess.noticeHere(answer))", "if (false)")).isNotEmpty())
        assertTrue(handOffProblems(mutate(calls, "val extras = destination?.let { Bundle().apply { putString(KEY_DESTINATION, it.name) } } val answer", "if (Application.getProcessName()?.endsWith(\":photosedit\") == true) return false val extras = destination?.let { Bundle().apply { putString(KEY_DESTINATION, it.name) } } val answer")).isNotEmpty())
        // The launcher's answer ignored (m11), the held copy inverted (m10), the copy written through update (m16).
        assertTrue(calledProblems(mutate(calls, "ask(context, BACKGROUND, id.toString()) { it.getBoolean(KEY_DONE) }", "ask(context, BACKGROUND, id.toString()) { true }"), settings).isNotEmpty())
        assertTrue(calledProblems(calls, mutate(settings, "fun held(): Boolean = instance != null", "fun held(): Boolean = instance == null")).isNotEmpty())
        assertTrue(calledProblems(calls, mutate(settings, "fun adopt(theme: StartTheme) { state.value = theme }", "fun adopt(theme: StartTheme) { update { theme } }")).isNotEmpty())
    }
}
