package app.tileshell.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.cortana.CortanaChecklist
import app.tileshell.cortana.CortanaPermissionActivity
import app.tileshell.diag.Diagnostics

/**
 * One grant row of either checklist as the wizard walks it (phase 12 T12-1): the Setup checklist's rows (`setup:`) then
 * Tess's (`tess:`), namespaced because both lists have a `calendar` row. The two checklists are the only sources; this
 * is their rows, read live, never a third list.
 */
data class WizardRow(
    val ns: String,
    val id: String,
    val title: String,
    val state: RowState,
    val detail: String,
    val permissions: List<String>,
    val partialIsDone: Boolean,
    val grant: Boolean,
    val action: () -> Unit,
) {
    val key: String get() = "$ns:$id"
}

/** Whether Start draws the wizard, and the line that says why (the Diagnostics precedence, C-15). */
sealed interface Visibility {
    data object CoreHeld : Visibility
    data object Finished : Visibility
    data class Show(val steps: List<String>) : Visibility
}

/**
 * A run in progress: [seq] is the walk's step keys in order, the steps before [pos] already passed; [pos] == seq.size is
 * the presets page. Nothing here is ever stored (Persistence): a run killed half-way is re-derived from live grant state.
 *
 * [declined] = the steps "Not now" passed (not asked again this run); [visitFired] = the current step's action ran on this
 * visit (the blocked rule needs it); [firedSinceCheck] = it ran since the last resume check (a PARTIAL that did not change
 * is still reported); [blocked] = steps whose Android prompt is blocked (button "Open app info"); [failed] = steps whose
 * intent could not start (button "Open Android settings", T12-17); [lastState] = each step's state at the last check.
 */
data class RunState(
    val seq: List<String>,
    val pos: Int = 0,
    val declined: Set<String> = emptySet(),
    val visitFired: Set<String> = emptySet(),
    val firedSinceCheck: Set<String> = emptySet(),
    val blocked: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
    val lastState: Map<String, RowState> = emptyMap(),
) {
    val current: String? get() = seq.getOrNull(pos)
    val onPresets: Boolean get() = pos >= seq.size

    /** "Step n of N": the presets page is the last step (E2: 19 steps + the presets page = 20). */
    val stepNumber: Int get() = pos + 1
    val total: Int get() = seq.size + 1
}

/** The wizard's pure rules (build task 1), unit-tested on the JVM (SetupWizardTest). */
object WizardRules {
    const val SETUP = "setup"
    const val TESS = "tess"

    /** A row the walk shows: a grant row that is MISSING, or PARTIAL where PARTIAL is not "done" (T12-1 (c)). */
    fun needsStep(row: WizardRow): Boolean =
        row.grant && (row.state == RowState.MISSING || (row.state == RowState.PARTIAL && !row.partialIsDone))

    /**
     * The Visibility rule: show ⇔ some core row (any grant row of either list) is MISSING ∧ ¬finished. A PARTIAL row never
     * summons the wizard. Precedence (C-15): "core held" whenever no core row is MISSING, marker or not; else "finished".
     */
    fun visibility(rows: List<WizardRow>, finished: Boolean): Visibility = when {
        rows.none { it.grant && it.state == RowState.MISSING } -> Visibility.CoreHeld
        finished -> Visibility.Finished
        else -> Visibility.Show(rows.filter(::needsStep).map { it.key })
    }

    /**
     * A row that asks to read and to write one thing (People's contacts, phase 16 T16-15 / r3 V12): MISSING whenever the
     * read is not held, whatever the write is — `pm revoke` is per permission, so "write held, read revoked" is
     * reachable — PARTIAL with the read alone, GRANTED with both.
     */
    fun readWriteState(readHeld: Boolean, writeHeld: Boolean): RowState = when {
        !readHeld -> RowState.MISSING
        !writeHeld -> RowState.PARTIAL
        else -> RowState.GRANTED
    }

    fun start(steps: List<String>) = RunState(seq = steps)

    /**
     * Re-derives the walk from live state on a resume (the dialogs and Settings pages all return through one). The current
     * step advances when its row no longer needs a step (granted, or PARTIAL on a partial-is-done row); the steps not yet
     * shown are re-derived in list order — a row that turned MISSING again rejoins them, ahead of the presets page; a
     * declined row does not. Returns the new state and the step lines to log (without the `[wizard] ` prefix).
     */
    fun reconcile(
        state: RunState,
        rows: List<WizardRow>,
        rationale: (String) -> Boolean,
        granted: (String) -> Boolean,
    ): Pair<RunState, List<String>> {
        val byKey = rows.associateBy { it.key }
        val lines = ArrayList<String>()
        val passed = state.seq.subList(0, state.pos.coerceAtMost(state.seq.size)).toMutableList()
        var keep: String? = null
        var blocked = state.blocked
        var visitFired = state.visitFired
        val cur = state.current
        if (cur != null) {
            val row = byKey[cur]
            when {
                // A key no longer in either list is dropped, never reported granted (adversarial review N8).
                row == null -> {
                    blocked = blocked - cur
                    visitFired = visitFired - cur
                }
                !needsStep(row) -> {
                    lines += "step $cur: " + if (row.state == RowState.PARTIAL) "partial" else "granted"
                    passed += cur
                    blocked = blocked - cur
                    visitFired = visitFired - cur
                }
                row.state == RowState.PARTIAL -> {
                    if (cur in state.firedSinceCheck || state.lastState[cur] != RowState.PARTIAL) lines += "step $cur: partial"
                    keep = cur
                }
                else -> {
                    if (cur !in blocked && isBlocked(row, cur in visitFired, rationale, granted)) {
                        blocked = blocked + cur
                        lines += "step $cur: blocked (app info)"
                    }
                    keep = cur
                }
            }
        }
        val remaining = rows.filter { needsStep(it) && it.key !in state.declined && it.key != keep }.map { it.key }
        val seq = passed + listOfNotNull(keep) + remaining
        val next = state.copy(
            seq = seq,
            pos = passed.size,
            blocked = blocked,
            visitFired = visitFired,
            firedSinceCheck = emptySet(),
            lastState = rows.associate { it.key to it.state },
        )
        return next to lines
    }

    /** "Not now": the step is passed and not asked again this run. */
    fun notNow(state: RunState, rows: List<WizardRow>): Pair<RunState, List<String>> {
        val cur = state.current ?: return state to emptyList()
        val passed = state.seq.subList(0, state.pos) + cur
        val declined = state.declined + cur
        val remaining = rows.filter { needsStep(it) && it.key !in declined }.map { it.key }
        return state.copy(
            seq = passed + remaining, pos = passed.size, declined = declined,
            visitFired = state.visitFired - cur, firedSinceCheck = state.firedSinceCheck - cur,
        ) to listOf("step $cur: not now")
    }

    /** Back = the previous step (nothing on the first); that step is asked again. */
    fun back(state: RunState, rows: List<WizardRow>): RunState? {
        if (state.pos <= 0) return null
        val pos = state.pos - 1
        val key = state.seq[pos]
        val declined = state.declined - key
        val remaining = rows.filter { needsStep(it) && it.key !in declined && it.key != key }.map { it.key }
        return state.copy(seq = state.seq.subList(0, pos) + key + remaining, pos = pos, declined = declined)
    }

    /** The current step's action ran. */
    fun fired(state: RunState, key: String) = state.copy(visitFired = state.visitFired + key, firedSinceCheck = state.firedSinceCheck + key)

    fun actionFailed(state: RunState, key: String) = state.copy(failed = state.failed + key)

    /**
     * Android will no longer ask (denied twice or "Don't ask again", phase 03's 2026-09-22 rule): the step's action ran,
     * the row came back MISSING, and none of its permissions still missing would show a rationale.
     */
    fun isBlocked(row: WizardRow, fired: Boolean, rationale: (String) -> Boolean, granted: (String) -> Boolean): Boolean {
        if (!fired || row.state != RowState.MISSING || row.permissions.isEmpty()) return false
        val missing = row.permissions.filterNot(granted)
        return missing.isNotEmpty() && missing.none(rationale)
    }

    /** The accent button's label: the verb the row implies (Decisions "Step page", T12-1 (a)). */
    fun verb(row: WizardRow): String = when {
        row.key == "$SETUP:home" -> "Set as default"
        row.key == "$SETUP:keyboard_enabled" -> "Turn on"
        row.key == "$SETUP:keyboard_selected" -> "Choose"
        row.key == "$TESS:background_location" -> "Allow all the time"
        row.permissions.isNotEmpty() -> "Allow"
        else -> "Open settings"
    }

    /**
     * The button's label on [row]'s page: "Open Android settings" once its intent could not start (T12-17), "Open app info"
     * while Android will no longer ask (blocked), else the row's verb.
     */
    fun label(row: WizardRow, run: RunState): String = when {
        row.key in run.failed -> "Open Android settings"
        row.key in run.blocked && row.state == RowState.MISSING -> "Open app info"
        else -> verb(row)
    }

    /** `act=<action>` out of an ActivityNotFoundException's message, for the action-failed line (T12-17). */
    fun intentAction(message: String?): String =
        message?.let { Regex("""act=([^\s}]+)""").find(it)?.groupValues?.get(1) } ?: "unknown"

    /**
     * Runs a step's grant [action]; an intent that cannot start (One UI resolves Settings actions differently) is caught and
     * returned as the action-failed line, so the page can relabel its button "Open Android settings" (T12-17).
     */
    fun fire(key: String, action: () -> Unit): String? = try {
        action()
        null
    } catch (e: ActivityNotFoundException) {
        "step $key: action failed ${intentAction(e.message)}: $e"
    } catch (e: SecurityException) {
        // An OEM Settings page that refuses the caller would otherwise crash Home (adversarial review N9): the same way on.
        "step $key: action failed ${intentAction(e.message)}: $e"
    }

    /**
     * The why line under each step's title (Q1 B, T12-4; agent-written P4 copy judged in H1). A later phase's step adds
     * its row here with its checklist row (C-4).
     */
    val WHY: Map<String, String> = mapOf(
        "$SETUP:home" to "Start opens when you press Home. Without it Home goes to another launcher.",
        "$SETUP:notifications" to "Live tiles and unread counts come from your notifications. Without it the tiles stay still.",
        "$SETUP:photos" to "The Photos tile shows your pictures. Without it the tile stays empty.",
        "$SETUP:music" to "Music plays the songs on this phone. Without it Music finds nothing to play.",
        "$SETUP:calendar" to "The Calendar tile shows what's next. Without it the tile stays empty.",
        "$SETUP:location" to "Weather shows the weather where you are. Without it Weather cannot tell where that is.",
        "$SETUP:usage" to "Back on Start returns to the app you were using. Without it Back stays on Start.",
        "$SETUP:keyboard_enabled" to "This turns on the Windows-style keyboard. Without it the keyboard cannot be chosen.",
        "$SETUP:keyboard_selected" to "This makes it the keyboard wherever you type. Without it your old keyboard stays.",
        "$SETUP:people" to "People shows and edits your contacts. Without it People can't see them.",
        "$SETUP:full_screen_alarms" to "Alarms ring over the lock screen. Without it an alarm still sounds, but shows only as a notification.",
        "$SETUP:overlay" to "Alarms ring over the app you're using. Without it an alarm shows as a notification.",
        "$SETUP:camera" to "Camera takes your photos and videos. Without it the Camera tile can't open the shell's camera.",
        "$SETUP:videos" to "Movies & TV and Photos show the videos on this phone. Without it they show none.",
        "$SETUP:files" to "Files can browse everything on this phone. Without it Files sees nothing.",
        "$TESS:assistant" to "The side key and the assist gesture open Tess. Without it they open another assistant.",
        "$TESS:microphone" to "Tess hears what you ask. Without it you can only type to her.",
        "$TESS:contacts" to "Tess calls and texts people by name. Without it she cannot find them.",
        "$TESS:calendar" to "Tess reads your calendar and adds to it. Without it she cannot see or add events.",
        "$TESS:sms_send" to "Tess sends a text after reading it back to you. Without it she cannot send one.",
        "$TESS:call_phone" to "Tess places a call after you confirm. Without it she cannot call.",
        "$TESS:background_location" to "Place reminders go off when you arrive. Without \"all the time\" they never fire.",
        "$TESS:call_log" to "Person reminders go off after you talk to that person. Without it they cannot tell you did.",
        "$TESS:sms_read" to "Person reminders go off after you text that person. Without it they cannot tell you did.",
    )
}

/**
 * The wizard's process-wide state (build task 1): the run in progress, if any, and the finished / skipped marker. A run
 * lives in the process, so an activity re-created mid-run keeps it and a process death re-derives it (E4).
 */
object SetupWizard {
    private const val STORE = "setup_wizard"
    private const val KEY_FINISHED = "finished"

    /** The run in progress; null = Start draws. Snapshot state, so Start's composition follows it. */
    var run by mutableStateOf<RunState?>(null)
        private set

    /** Bumped on every re-derive, so the step page re-reads its row's state line. */
    var rowsVersion by mutableIntStateOf(0)
        private set

    val showing: Boolean get() = run != null

    /**
     * The walk's rows, live: [Checklist.rows] (setup:) then [CortanaChecklist.rows] (tess:) — grant rows and observations
     * alike (the rules filter on `grant`). The Setup rows fire through the caller's launchers; Tess's through
     * [CortanaPermissionActivity]'s existing paths, as her Settings page fires them (`CortanaSettingsPage`).
     */
    fun rows(context: Context, requestRole: (Intent) -> Unit, requestPermissions: (Array<String>) -> Unit): List<WizardRow> {
        val setup = Checklist.rows(context, requestRole, requestPermissions).map {
            WizardRow(WizardRules.SETUP, it.id, it.title, it.state, it.detail, it.permissions, it.partialIsDone, it.grant, it.action)
        }
        val tess = CortanaChecklist.rows(context).map { row ->
            WizardRow(WizardRules.TESS, row.id, row.title, row.state, row.detail, row.permissions, row.partialIsDone, row.grant) {
                when {
                    row.id == "assistant" -> CortanaPermissionActivity.requestAssistantRole(context)
                    row.permissions.isNotEmpty() -> CortanaPermissionActivity.request(context, row.permissions)
                }
            }
        }
        return setup + tess
    }

    fun finished(context: Context): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(KEY_FINISHED, false)

    /** Evaluated when Start is created and on every resume while no run is in progress; logs the line either way. */
    fun evaluate(context: Context): Boolean {
        if (run != null) return true
        val rows = rows(context, {}, {})
        return when (val v = WizardRules.visibility(rows, finished(context))) {
            Visibility.CoreHeld -> { Diagnostics.add("wizard", "not shown: core held"); false }
            Visibility.Finished -> { Diagnostics.add("wizard", "not shown: finished"); false }
            is Visibility.Show -> {
                run = WizardRules.start(v.steps)
                rowsVersion++
                Diagnostics.add("wizard", "shown: missing=${v.steps.joinToString(",")}")
                true
            }
        }
    }

    fun reconcile(context: Context, rationale: (String) -> Boolean, granted: (String) -> Boolean) {
        val state = run ?: return
        val (next, lines) = WizardRules.reconcile(state, rows(context, {}, {}), rationale, granted)
        lines.forEach { Diagnostics.add("wizard", it) }
        run = next
        rowsVersion++
    }

    fun notNow(context: Context) {
        val state = run ?: return
        val (next, lines) = WizardRules.notNow(state, rows(context, {}, {}))
        lines.forEach { Diagnostics.add("wizard", it) }
        run = next
        rowsVersion++
    }

    /** Back on a step: true when it moved (nothing happens on the first step). */
    fun back(context: Context): Boolean {
        val state = run ?: return false
        val next = WizardRules.back(state, rows(context, {}, {})) ?: return false
        run = next
        rowsVersion++
        return true
    }

    /** The step's accent button: its row's own grant, or the blocked / action-failed fallbacks. */
    fun fire(context: Context, row: WizardRow) {
        val state = run ?: return
        val key = row.key
        when {
            key in state.failed -> openSafely(context, Intent(android.provider.Settings.ACTION_SETTINGS), key)
            key in state.blocked && row.state == RowState.MISSING -> openSafely(
                context,
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                key,
            )
            else -> {
                val failure = WizardRules.fire(key, row.action)
                if (failure != null) {
                    Diagnostics.add("wizard", failure)
                    run = WizardRules.actionFailed(state, key)
                    return
                }
            }
        }
        run = WizardRules.fired(run ?: return, key)
    }

    private fun openSafely(context: Context, intent: Intent, key: String) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Diagnostics.add("wizard", "step $key: ${intent.action} failed: $it") }
    }

    fun skip(context: Context) {
        if (run == null) return
        markFinished(context)
        Diagnostics.add("wizard", "skip")
        run = null
    }

    /** "Done" on the presets page: the run's end, reached by grants or "Not now" alike. */
    fun done(context: Context) {
        if (run == null) return
        markFinished(context)
        Diagnostics.add("wizard", "finished")
        run = null
    }

    /** Its own file, not ShellSettings (whose update() rewrites every key) nor LayoutStore; committed before Start draws. */
    private fun markFinished(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(KEY_FINISHED, true).commit()
    }
}
