package app.tileshell.process

import app.tileshell.cortana.CortanaDestinationKey
import app.tileshell.cortana.CortanaMode

/**
 * Which of the shell's processes this is, and what another of them may ask the launcher's (phase 17, the owner's ruling
 * D3 (a), 2026-10-31). The launcher's process is the one Android names after the package; `:photosedit` (the editor and,
 * since D3, the viewer other apps open), `:camera`, `:video`, `:ime`, `:speech` and `:recorder` are `<package>:<name>`,
 * and `ShellApp` does none of the launcher's start-up work in them.
 *
 * Three things only the launcher's process can do, asked from another through [LauncherCalls]:
 *  - the theme as it is NOW: a process reads the settings file once, when it first opens it, and SharedPreferences are
 *    not coherent across processes, so a change made in Settings later never reaches that copy by itself;
 *  - pointing the Start background at a copy Photos made: the theme has ONE writer, the launcher's process — a write from
 *    another would rewrite the whole file from that process's older copy, and Start's own copy would never follow;
 *  - opening Cortana: the session is shown by the assistant service, and that lives in the launcher's process.
 * Pure, so every answer is unit-tested (`ShellProcessTest`).
 */
object ShellProcess {
    /** True only for the launcher's process. [processName] is `Application.getProcessName()`. */
    fun isLauncher(processName: String?, packageName: String): Boolean = processName == packageName

    /**
     * Whether [LauncherCallsProvider] answers a call at all (D3 fix round, L1): only from the shell's own uid, [ownUid]
     * (`Process.myUid()`), which every one of its processes runs as. The provider is not exported, but Android still
     * lets the system and root reach it; anything whose [callingUid] (`Binder.getCallingUid()`) is not the shell's is
     * refused before the call is read.
     */
    fun answersCaller(callingUid: Int, ownUid: Int): Boolean = callingUid == ownUid

    /**
     * Whether a window opening in this process asks the launcher's for the theme first: only outside the launcher's
     * process, and only when this process already [holdsCopy] — a fresh process reads the file as it is then, so the
     * first open asks nothing more than the window needs.
     */
    fun asksForTheme(launcher: Boolean, holdsCopy: Boolean): Boolean = !launcher && holdsCopy

    /**
     * Where under the shell's files the Start background copy of MediaStore row [id] lies — `backgrounds/<id>.jpg`, the
     * file `PhotoActions.setAsBackground` writes. Null for anything but a row id (digits only), so another process can
     * point Start at Photos' copy of a row and at no other file.
     */
    fun backgroundCopy(id: String?): String? =
        id?.takeIf { it.length in 1..19 && it.all { c -> c in '0'..'9' } }?.let { "backgrounds/$it.jpg" }

    /** The Cortana open another process asked for: a [CortanaMode] and at most one [CortanaDestinationKey], by name. Null = refused. */
    fun cortanaOpen(mode: String?, destination: String?): Pair<CortanaMode, CortanaDestinationKey?>? {
        val m = CortanaMode.entries.firstOrNull { it.name == mode } ?: return null
        if (destination == null) return m to null
        return CortanaDestinationKey.entries.firstOrNull { it.name == destination }?.let { m to it }
    }
}
