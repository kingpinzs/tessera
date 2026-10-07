package app.tileshell.onboarding

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 build task 1: the wizard's pure rules — visibility and its "not shown" precedence, step derivation and order,
 * namespacing, the observation rows never being steps, partialIsDone per row, the resume re-derive, Back, blocked
 * detection and the action-failed runner (T12-17).
 *
 * The fixture mirrors the two checklists' ids, order and flags as built (onboarding/Checklist.kt, cortana/CortanaChecklist.kt);
 * the emulator rows (E1, E2) prove the real lists, whose dumps this test cannot read.
 */
class SetupWizardTest {

    private val setupGrant = listOf("home", "notifications", "photos", "music", "calendar", "location", "usage")
    private val setupObservationsMid = listOf("samsung_badges", "legacy_badges")
    private val setupGrantLate = listOf("keyboard_enabled", "keyboard_selected", "people", "full_screen_alarms", "overlay", "camera", "videos", "files")
    private val tessGrantEarly = listOf("assistant", "microphone", "contacts", "calendar", "sms_send", "call_phone")
    private val tessGrantLate = listOf("background_location", "call_log", "sms_read")
    private val observations = listOf("setup:samsung_badges", "setup:legacy_badges", "setup:listener", "tess:exact_alarms", "tess:models", "tess:service", "tess:speech_process", "tess:person_triggers")

    private val permissionsOf = mapOf(
        "setup:photos" to listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VISUAL_USER_SELECTED"),
        "setup:music" to listOf("READ_MEDIA_AUDIO"),
        "setup:calendar" to listOf("READ_CALENDAR"),
        "setup:location" to listOf("ACCESS_COARSE_LOCATION"),
        "setup:people" to listOf("READ_CONTACTS", "WRITE_CONTACTS"),
        "setup:camera" to listOf("CAMERA"),
        "setup:videos" to listOf("READ_MEDIA_VIDEO"),
        "tess:microphone" to listOf("RECORD_AUDIO"),
        "tess:contacts" to listOf("READ_CONTACTS"),
        "tess:calendar" to listOf("READ_CALENDAR", "WRITE_CALENDAR"),
        "tess:sms_send" to listOf("SEND_SMS"),
        "tess:call_phone" to listOf("CALL_PHONE"),
        "tess:background_location" to listOf("ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION"),
        "tess:call_log" to listOf("READ_CALL_LOG"),
        "tess:sms_read" to listOf("READ_SMS"),
    )

    /** Both lists in walking order, every row [defaultState] unless [states] says otherwise. */
    private fun rows(defaultState: RowState = RowState.GRANTED, states: Map<String, RowState> = emptyMap()): List<WizardRow> {
        fun row(ns: String, id: String, grant: Boolean): WizardRow {
            val key = "$ns:$id"
            return WizardRow(ns, id, id, states[key] ?: defaultState, "detail", permissionsOf[key].orEmpty(), key == "setup:photos", grant) {}
        }
        return setupGrant.map { row("setup", it, true) } +
            setupObservationsMid.map { row("setup", it, false) } +
            setupGrantLate.map { row("setup", it, true) } +
            row("setup", "listener", false) +
            tessGrantEarly.map { row("tess", it, true) } +
            row("tess", "exact_alarms", false) +
            tessGrantLate.map { row("tess", it, true) } +
            listOf("models", "service", "speech_process", "person_triggers").map { row("tess", it, false) }
    }

    /** The E2 state: every grant row missing but Home (which is kept so Start draws); observations missing too. */
    private fun e2(): List<WizardRow> = rows(RowState.MISSING, mapOf("setup:home" to RowState.GRANTED))

    private val e2Steps = listOf(
        "setup:notifications", "setup:photos", "setup:music", "setup:calendar", "setup:location", "setup:usage",
        "setup:keyboard_enabled", "setup:keyboard_selected", "setup:people", "setup:full_screen_alarms", "setup:overlay",
        "setup:camera", "setup:videos", "setup:files",
        "tess:assistant", "tess:microphone", "tess:contacts", "tess:calendar", "tess:sms_send", "tess:call_phone",
        "tess:background_location", "tess:call_log", "tess:sms_read",
    )

    private val noRationale: (String) -> Boolean = { false }
    private val noneGranted: (String) -> Boolean = { false }

    // ------------------------------------------------------------------ visibility and precedence

    @Test fun `twenty-four core rows - fifteen Setup (Camera and Videos since phase 17, Files since phase 18) and Tess's nine`() {
        assertEquals(24, rows().count { it.grant })
        assertEquals(15, rows().count { it.grant && it.ns == "setup" })
        assertEquals(9, rows().count { it.grant && it.ns == "tess" })
    }

    @Test fun `every core row held - not shown, core held, with or without the marker`() {
        assertEquals(Visibility.CoreHeld, WizardRules.visibility(rows(), finished = false))
        assertEquals(Visibility.CoreHeld, WizardRules.visibility(rows(), finished = true))
    }

    @Test fun `a core row missing and the marker set - not shown, finished`() {
        assertEquals(Visibility.Finished, WizardRules.visibility(rows(states = mapOf("setup:usage" to RowState.MISSING)), finished = true))
    }

    @Test fun `a core row missing and no marker - shown with that row`() {
        assertEquals(Visibility.Show(listOf("setup:usage")), WizardRules.visibility(rows(states = mapOf("setup:usage" to RowState.MISSING)), false))
    }

    @Test fun `a PARTIAL row never summons the wizard`() {
        val partials = mapOf("setup:photos" to RowState.PARTIAL, "tess:background_location" to RowState.PARTIAL, "tess:calendar" to RowState.PARTIAL)
        assertEquals(Visibility.CoreHeld, WizardRules.visibility(rows(states = partials), false))
    }

    @Test fun `observation rows missing never summon the wizard`() {
        val obs = observations.associateWith { RowState.MISSING }
        assertEquals(Visibility.CoreHeld, WizardRules.visibility(rows(states = obs), false))
    }

    // ------------------------------------------------------------------ steps: order, namespacing, observations, partial

    @Test fun `the E2 state walks twenty-three steps, Setup order then Tess's, namespaced`() {
        val v = WizardRules.visibility(e2(), false) as Visibility.Show
        assertEquals(e2Steps, v.steps)
        val run = WizardRules.start(v.steps)
        assertEquals(1, run.stepNumber)
        assertEquals(24, run.total)
    }

    @Test fun `both lists' calendar rows are separate steps`() {
        val steps = (WizardRules.visibility(e2(), false) as Visibility.Show).steps
        assertTrue("setup:calendar" in steps && "tess:calendar" in steps)
    }

    @Test fun `the eight observation rows are never steps, even when missing`() {
        val steps = e2().filter(WizardRules::needsStep).map { it.key }
        observations.forEach { assertFalse(it, it in steps) }
        assertEquals(8, e2().count { !it.grant })
    }

    @Test fun `partialIsDone - Photos PARTIAL is done, Tess's PARTIAL rows stay steps`() {
        val r = rows(states = mapOf("setup:photos" to RowState.PARTIAL, "tess:background_location" to RowState.PARTIAL, "tess:calendar" to RowState.PARTIAL))
        val byKey = r.associateBy { it.key }
        assertFalse(WizardRules.needsStep(byKey.getValue("setup:photos")))
        assertTrue(WizardRules.needsStep(byKey.getValue("tess:background_location")))
        assertTrue(WizardRules.needsStep(byKey.getValue("tess:calendar")))
        assertTrue(byKey.getValue("setup:photos").partialIsDone)
        assertEquals(1, r.count { it.partialIsDone })
    }

    // ------------------------------------------------------------------ walking: Not now, resume, Back

    @Test fun `Not now through the E2 run records the steps in order and ends on the presets page`() {
        var run = WizardRules.start(e2Steps)
        val seen = ArrayList<String>()
        val lines = ArrayList<String>()
        repeat(40) {
            if (run.onPresets) return@repeat
            seen += run.current!!
            val (next, l) = WizardRules.notNow(run, e2())
            run = next; lines += l
        }
        assertTrue("the walk ends on the presets page within 40 steps", run.onPresets)
        assertEquals(e2Steps, seen)
        assertEquals(24, run.stepNumber)
        assertEquals(24, run.total)
        assertEquals("step setup:notifications: not now", lines.first())
    }

    @Test fun `a grant on resume advances with a granted line`() {
        var run = WizardRules.start(e2Steps)
        val after = e2().map { if (it.key == "setup:notifications") it.copy(state = RowState.GRANTED) else it }
        val (next, lines) = WizardRules.reconcile(run, after, noRationale, noneGranted)
        run = next
        assertEquals(listOf("step setup:notifications: granted"), lines)
        assertEquals("setup:photos", run.current)
        assertEquals(2, run.stepNumber)
        assertEquals(24, run.total)
    }

    @Test fun `Photos answered Select photos - PARTIAL advances with a partial line`() {
        val run = WizardRules.start(listOf("setup:photos", "setup:music"))
        val r = rows(states = mapOf("setup:photos" to RowState.PARTIAL, "setup:music" to RowState.MISSING))
        val (next, lines) = WizardRules.reconcile(run, r, noRationale, noneGranted)
        assertEquals(listOf("step setup:photos: partial"), lines)
        assertEquals("setup:music", next.current)
    }

    @Test fun `background location FINE only - the step stays with a partial line`() {
        var run = WizardRules.start(listOf("tess:background_location"))
        run = WizardRules.fired(run, "tess:background_location")
        val r = rows(states = mapOf("tess:background_location" to RowState.PARTIAL))
        val (next, lines) = WizardRules.reconcile(run, r, noRationale, noneGranted)
        assertEquals(listOf("step tess:background_location: partial"), lines)
        assertEquals("tess:background_location", next.current)
        // A second resume with nothing fired and no change says nothing more.
        val (again, lines2) = WizardRules.reconcile(next, r, noRationale, noneGranted)
        assertEquals(emptyList<String>(), lines2)
        assertEquals("tess:background_location", again.current)
    }

    @Test fun `PARTIAL that did not change still reports partial after the action ran (location off)`() {
        val r = rows(states = mapOf("tess:background_location" to RowState.PARTIAL))
        var run = WizardRules.reconcile(WizardRules.start(listOf("tess:background_location")), r, noRationale, noneGranted).first
        run = WizardRules.fired(run, "tess:background_location")
        val (_, lines) = WizardRules.reconcile(run, r, noRationale, noneGranted)
        assertEquals(listOf("step tess:background_location: partial"), lines)
    }

    @Test fun `a row revoked mid-run rejoins the steps not yet shown, ahead of the presets page, once`() {
        var rowsNow = e2().map { if (it.key == "setup:notifications") it.copy(state = RowState.GRANTED) else it }
        var run = WizardRules.reconcile(WizardRules.start(e2Steps), rowsNow, noRationale, noneGranted).first
        assertEquals("setup:photos", run.current)
        rowsNow = e2() // notification access revoked again
        run = WizardRules.reconcile(run, rowsNow, noRationale, noneGranted).first
        assertEquals("setup:photos", run.current) // the current step stays
        assertEquals(25, run.total)                // N grew by one
        val seen = ArrayList<String>()
        repeat(40) {
            if (run.onPresets) return@repeat
            seen += run.current!!
            run = WizardRules.notNow(run, rowsNow).first
        }
        assertTrue("the walk ends on the presets page within 40 steps", run.onPresets)
        assertEquals(1, seen.count { it == "setup:notifications" })
        assertEquals(listOf("setup:photos", "setup:notifications"), seen.take(2))
    }

    @Test fun `a declined step is not asked again this run`() {
        var run = WizardRules.notNow(WizardRules.start(e2Steps), e2()).first
        run = WizardRules.reconcile(run, e2(), noRationale, noneGranted).first
        assertFalse("setup:notifications" in run.seq.subList(run.pos, run.seq.size))
    }

    @Test fun `a grant made while the wizard is not showing - the run starts without that step`() {
        val r = e2().map { if (it.key == "setup:photos") it.copy(state = RowState.GRANTED) else it }
        val steps = (WizardRules.visibility(r, false) as Visibility.Show).steps
        assertFalse("setup:photos" in steps)
        assertEquals(23, WizardRules.start(steps).total) // E4: one fewer N
    }

    @Test fun `E14's template on setup usage - Step 1 of 2`() {
        val steps = (WizardRules.visibility(rows(states = mapOf("setup:usage" to RowState.MISSING)), false) as Visibility.Show).steps
        val run = WizardRules.start(steps)
        assertEquals(1, run.stepNumber)
        assertEquals(2, run.total)
        val (next, _) = WizardRules.reconcile(run, rows(), noRationale, noneGranted)
        assertTrue(next.onPresets)
    }

    @Test fun `Back on the first step does nothing, on step 2 it returns to step 1, asked again`() {
        val run0 = WizardRules.start(e2Steps)
        assertNull(WizardRules.back(run0, e2()))
        val run1 = WizardRules.notNow(run0, e2()).first
        val back = WizardRules.back(run1, e2())!!
        assertEquals("setup:notifications", back.current)
        assertEquals(1, back.stepNumber)
        assertFalse("setup:notifications" in back.declined)
        assertEquals(24, back.total)
    }

    // ------------------------------------------------------------------ People's row (phase 16, T16-15 / r3 V12)

    @Test fun `a read-and-write row is missing whenever the read is not held, partial with the read alone`() {
        assertEquals(RowState.MISSING, WizardRules.readWriteState(readHeld = false, writeHeld = false))
        // `pm revoke` is per permission: WRITE held with READ revoked is reachable, and it reads MISSING.
        assertEquals(RowState.MISSING, WizardRules.readWriteState(readHeld = false, writeHeld = true))
        assertEquals(RowState.PARTIAL, WizardRules.readWriteState(readHeld = true, writeHeld = false))
        assertEquals(RowState.GRANTED, WizardRules.readWriteState(readHeld = true, writeHeld = true))
    }

    @Test fun `People's row partial never summons the wizard, missing does, and partial is not done for its step`() {
        // E26 (c): READ held, WRITE revoked on a finished or an unfinished install — no wizard, "core held".
        val partial = rows(states = mapOf("setup:people" to RowState.PARTIAL))
        assertEquals(Visibility.CoreHeld, WizardRules.visibility(partial, finished = false))
        assertEquals(Visibility.CoreHeld, WizardRules.visibility(partial, finished = true))
        // E26 (a): both revoked — Tess's contacts row is missing with it, and People's step comes first.
        val missing = rows(states = mapOf("setup:people" to RowState.MISSING, "tess:contacts" to RowState.MISSING))
        assertEquals(Visibility.Show(listOf("setup:people", "tess:contacts")), WizardRules.visibility(missing, finished = false))
        assertEquals(Visibility.Finished, WizardRules.visibility(missing, finished = true))
        // READ granted during the walk: the step stays (PARTIAL is not done for this row) and says so.
        val run = WizardRules.fired(WizardRules.start(listOf("setup:people", "tess:contacts")), "setup:people")
        val (kept, lines) = WizardRules.reconcile(run, partial, noRationale, noneGranted)
        assertEquals("setup:people", kept.current)
        assertEquals(listOf("step setup:people: partial"), lines)
        assertTrue(WizardRules.needsStep(partial.first { it.key == "setup:people" }))
        // WRITE granted: the step is done.
        val (done, doneLines) = WizardRules.reconcile(kept, rows(), noRationale, noneGranted)
        assertEquals(listOf("step setup:people: granted"), doneLines)
        assertTrue(done.onPresets)
    }

    // ------------------------------------------------------------------ blocked and action failed

    @Test fun `blocked - fired, still missing, no rationale on any missing permission`() {
        val photos = e2().first { it.key == "setup:photos" }
        assertTrue(WizardRules.isBlocked(photos, fired = true, noRationale, noneGranted))
        assertFalse("not fired", WizardRules.isBlocked(photos, fired = false, noRationale, noneGranted))
        assertFalse("rationale shown", WizardRules.isBlocked(photos, fired = true, { true }, noneGranted))
        val usage = e2().first { it.key == "setup:usage" }
        assertFalse("a Settings-page row is never blocked", WizardRules.isBlocked(usage, fired = true, noRationale, noneGranted))
    }

    @Test fun `blocked is logged once and relabels the button Open app info`() {
        var run = WizardRules.fired(WizardRules.start(listOf("setup:photos")), "setup:photos")
        val (next, lines) = WizardRules.reconcile(run, e2(), noRationale, noneGranted)
        assertEquals(listOf("step setup:photos: blocked (app info)"), lines)
        run = next
        val photos = e2().first { it.key == "setup:photos" }
        assertEquals("Open app info", WizardRules.label(photos, run))
        val (_, again) = WizardRules.reconcile(run, e2(), noRationale, noneGranted)
        assertEquals(emptyList<String>(), again)
    }

    @Test fun `denied once - the rationale shows, not blocked, the button still says Allow`() {
        val run = WizardRules.fired(WizardRules.start(listOf("setup:photos")), "setup:photos")
        val (next, lines) = WizardRules.reconcile(run, e2(), { it == "READ_MEDIA_IMAGES" }, noneGranted)
        assertEquals(emptyList<String>(), lines)
        assertEquals("Allow", WizardRules.label(e2().first { it.key == "setup:photos" }, next))
    }

    @Test fun `an intent that cannot start - the action-failed line and Open Android settings`() {
        val failure = WizardRules.fire("setup:usage") {
            throw object : ActivityNotFoundException() {
                override val message = "No Activity found to handle Intent { act=android.settings.USAGE_ACCESS_SETTINGS dat=package:app.tileshell }"
            }
        }!!
        assertTrue(failure, failure.startsWith("step setup:usage: action failed android.settings.USAGE_ACCESS_SETTINGS: "))
        val run = WizardRules.actionFailed(WizardRules.start(listOf("setup:usage")), "setup:usage")
        assertEquals("Open Android settings", WizardRules.label(e2().first { it.key == "setup:usage" }, run))
    }

    @Test fun `an action that starts - no failure line`() {
        var ran = false
        assertNull(WizardRules.fire("setup:usage") { ran = true })
        assertTrue(ran)
    }

    @Test fun `intentAction reads act= and nothing else`() {
        assertEquals("android.settings.FOO", WizardRules.intentAction("No Activity found to handle Intent { act=android.settings.FOO }"))
        assertEquals("unknown", WizardRules.intentAction(null))
        assertEquals("unknown", WizardRules.intentAction("no intent here"))
    }

    @Test fun `a blocked row that turned PARTIAL reads its verb again (not Open app info)`() {
        var run = WizardRules.fired(WizardRules.start(listOf("tess:background_location")), "tess:background_location")
        run = WizardRules.reconcile(run, e2(), noRationale, noneGranted).first
        val missing = e2().first { it.key == "tess:background_location" }
        assertEquals("Open app info", WizardRules.label(missing, run))
        val partial = missing.copy(state = RowState.PARTIAL)   // FINE granted from the app-info page
        run = WizardRules.reconcile(run, rows(states = mapOf("tess:background_location" to RowState.PARTIAL)), noRationale, noneGranted).first
        assertEquals("Allow all the time", WizardRules.label(partial, run))
    }

    @Test fun `the first walk lists PARTIAL-not-done rows beside the MISSING ones`() {
        val r = rows(states = mapOf("setup:usage" to RowState.MISSING, "tess:background_location" to RowState.PARTIAL))
        assertEquals(Visibility.Show(listOf("setup:usage", "tess:background_location")), WizardRules.visibility(r, false))
    }

    @Test fun `blocked, then granted, then revoked - the step asks again (blocked is cleared)`() {
        var run = WizardRules.fired(WizardRules.start(listOf("setup:photos", "setup:music")), "setup:photos")
        run = WizardRules.reconcile(run, e2(), noRationale, noneGranted).first
        assertTrue("setup:photos" in run.blocked)
        val granted = e2().map { if (it.key == "setup:photos") it.copy(state = RowState.GRANTED) else it }
        run = WizardRules.reconcile(run, granted, noRationale, noneGranted).first
        assertFalse("blocked cleared on the grant", "setup:photos" in run.blocked)
        run = WizardRules.reconcile(run, e2(), noRationale, noneGranted).first   // revoked again: rejoins
        assertEquals("Allow", WizardRules.label(e2().first { it.key == "setup:photos" }, run))
    }

    @Test fun `a current key missing from both lists is dropped, never reported granted`() {
        val run = WizardRules.start(listOf("setup:gone", "setup:usage"))
        val (next, lines) = WizardRules.reconcile(run, rows(states = mapOf("setup:usage" to RowState.MISSING)), noRationale, noneGranted)
        assertEquals(emptyList<String>(), lines)
        assertEquals("setup:usage", next.current)
        assertFalse("setup:gone" in next.seq)
    }

    @Test fun `a Settings page that refuses the caller - the action-failed line, not a crash`() {
        val failure = WizardRules.fire("setup:full_screen_alarms") { throw SecurityException("Permission Denial: starting Intent { act=android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT }") }!!
        assertTrue(failure, failure.startsWith("step setup:full_screen_alarms: action failed android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT: "))
    }

    // ------------------------------------------------------------------ verbs and why lines

    @Test fun `each step's verb`() {
        val byKey = e2().associateBy { it.key }
        fun verb(k: String) = WizardRules.verb(byKey.getValue(k))
        assertEquals("Set as default", WizardRules.verb(rows().first { it.key == "setup:home" }))
        assertEquals("Open settings", verb("setup:notifications"))
        assertEquals("Allow", verb("setup:photos"))
        assertEquals("Open settings", verb("setup:usage"))
        assertEquals("Turn on", verb("setup:keyboard_enabled"))
        assertEquals("Choose", verb("setup:keyboard_selected"))
        assertEquals("Open settings", verb("setup:overlay"))
        assertEquals("Open settings", verb("setup:files"))
        assertEquals("Open settings", verb("tess:assistant"))
        assertEquals("Allow", verb("tess:microphone"))
        assertEquals("Allow all the time", verb("tess:background_location"))
    }

    @Test fun `every step has a why line - the twenty-four grant rows`() {
        rows().filter { it.grant }.forEach { assertTrue(it.key, WizardRules.WHY[it.key].orEmpty().isNotBlank()) }
        assertEquals(24, WizardRules.WHY.size)
        // Phase 18 build task 1 (T18-3): the line E15 (a) reads, word for word.
        assertEquals("Files can browse everything on this phone. Without it Files sees nothing.", WizardRules.WHY["setup:files"])
        // Phase 17 build task 8: the two lines E25 (a) reads, word for word.
        assertEquals("Camera takes your photos and videos. Without it the Camera tile can't open the shell's camera.", WizardRules.WHY["setup:camera"])
        assertEquals("Movies & TV and Photos show the videos on this phone. Without it they show none.", WizardRules.WHY["setup:videos"])
        assertEquals("People shows and edits your contacts. Without it People can't see them.", WizardRules.WHY["setup:people"])
        assertEquals("Live tiles and unread counts come from your notifications. Without it the tiles stay still.", WizardRules.WHY["setup:notifications"])
    }
}
