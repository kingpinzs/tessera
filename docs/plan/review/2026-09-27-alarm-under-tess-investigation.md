# The alarm under Tess — investigation (2026-09-27)

Read-only investigation of phase 15's open EDGE_ALARM_CONTEXT defect: "an alarm firing while Tess is listening does not
hide the session — the toast is drawn UNDER it" (qa/phase-15/EDGE_ALARM_CONTEXT-run3/DEFECT.md §1, build 4b7ac321,
emulator-5558, 2026-09-24). Code read at main 9296d0f4. Nothing was built, installed or run.

## What the evidence shows (run 3)

The fire lands inside the listen: `listening for pid=14814 (cortana)` 22:54:59.053 → `[alarms] fired a9b8bad4c`
22:55:00.019 → `overlay shown` / `surface: toast-overlay` 22:55:00.085 → `asr: final` 22:55:01.319 (ring_tess_*.txt). No
`[cortana] session hidden` follows; at 22:55:01.320 Tess says "I didn't catch that." over the alarm. The all-windows dump
lists only the session window (`TYPE_-1 … focused=true`): the overlay was added but is fully covered. tess_ring.png shows
Tess and no toast; `ring_dismiss` was "not in any of 3 dumps", so the driver ended the ring with dismiss_any_ring.

## Root cause: the session must step aside, and nothing makes it

1. **No ring surface can draw over Tess.** The in-use toast is a `TYPE_APPLICATION_OVERLAY` window
   (clock/RingOverlay.kt:55-62, added by RingService.present() at clock/RingService.kt:194-195). The platform stacks the
   voice-interaction window above app overlays, above SystemUI's shade and heads-up, and above every activity. In AOSP
   `WindowManagerPolicy.getWindowLayerFromTypeLw`, APPLICATION_OVERLAY is layer 11, the status bar and shade 15-17, and
   VOICE_INTERACTION 21 ("should show above the lock screen"). Those numbers come from the AOSP source, not re-read here
   (the SDK jar holds stubs only). The device evidence agrees, as does MICPERM's finding that the permission activity was
   "resumed but hidden UNDER Tess" (cortana/CortanaSession.kt:92-97). All three surfaces are covered: the overlay toast,
   the locked toast's `RingActivity`, and the heads-up. So the fix cannot live in the ring surface.
2. **Only the session can hide itself.** The android-36 `android.jar` (checked with javap) gives `VoiceInteractionService`
   no public hide call, only `showSession` / `setDisabledShowContext`. `VoiceInteractionSession.hide()` is the only way
   out, and a targetSdk-36 app cannot send `ACTION_CLOSE_SYSTEM_DIALOGS`.
3. **The session never learns that a ring started.** CortanaSession calls `hide()` only from `closeRequests` (:125,
   emitted after a closing command, CortanaModel.kt:227, 464, 475), `requestMicrophone` (:100), Back (:246, :258), the
   drawn Windows key (:254), and the framework's default `onCloseSystemDialogs` / `onLockscreenShown` (not overridden).
   Nothing in `cortana/` reads `RingService.state` (RingService.kt:401-403); its only consumers are StartActivity.kt:177
   (0d74245), RingActivity.kt:38,50 and RingOverlay.kt:47. present() (RingService.kt:163-206) publishes the ring
   (`_state.value = shown`, :177) and shows its surface, with no knowledge of a voice-interaction window on top.

**The component that should yield is CortanaSession.** It does not today because nothing connects the ring's published
state to the session's `hide()`. DEFECT.md's own cause line is correct.

## What the phase doc says should happen

Edge cases (phase-15 doc line 1424): "Alarm firing while Tess is listening (the session hides)". The ring-surface
Decisions (lines 209-220, T15-23; 511-525, T15-14 / T15-32) rule the toast "over the app in use as an overlay" and allow
for the gap under SystemUI's status bar, but never consider the voice-interaction window above the overlay. So the ruled
surface cannot meet the edge case unless Tess yields.

## Smallest correct fix (CortanaSession.kt only; do not apply without Jeremy's go)

Add `private var ringJob: Job? = null`. At the end of `onShow` (after `model.open(mode)`, :209):

```kotlin
ringJob?.cancel()
ringJob = scope.launch {
    // Phase 15 Edge cases: "Alarm firing while Tess is listening (the session hides)". Every ring surface (the overlay
    // toast, the locked toast's activity, the heads-up) sits below the voice-interaction window, and nothing outside a
    // session can hide it, so Tess yields. drop(1): a ring already up when she opens does not close her.
    RingService.state.drop(1).filterNotNull().collect { ring ->
        Diagnostics.add("cortana", "a ring started (${ring.logId}): hiding the session")
        hide()
    }
}
```

First line of `onHide` (:212): `ringJob?.cancel(); ringJob = null`. Imports: `kotlinx.coroutines.Job`,
`kotlinx.coroutines.flow.drop`, `kotlinx.coroutines.flow.filterNotNull`, `app.tileshell.clock.RingService`.

`hide()` runs the existing `onHide` (:212-217): `model.stop()` closes the mic, stops speech and unbinds
(CortanaModel.kt:112-123), and logs `[cortana] session hidden`, which the driver asserts. The session, not the ring,
carries the fix because the ring has no API to hide a session; this is 0d74245's pattern (the ring publishes, the surface
that must change watches). Not `setUiEnabled(false)` (L13-1's step-aside): it keeps the card but logs no hide, needs a
come-back rule the doc lacks, and returns Tess over the toast. Both services run in the main process
(AndroidManifest.xml:271-274, 349-353; no `android:process`), so the companion `StateFlow` is shared. **Assumption:** the
hide follows any ring start, not only a listen; a covered toast is the same defect whether Tess listens, speaks or idles.

## How to verify on a device (specific rows only, per the 2026-09-25 QA ruling)

**Driver:** extend `docs/plan/qa/phase-15/scripts/edge_alarm_context.sh`. Replace section 1's `record … NOT RUN` (lines
62-64) with the Tess section from git `da848a06` (lines 61-112; run 3's method, and every helper it calls is still in
clock.sh / phase-01 lib.sh). Restore section 4 (the recorder) from `da848a06` the same way. Jeremy lifted the microphone
rule on 2026-09-25 (INDEX Change Log), so drop `mic_guard_begin` / `mic_guard_end` (lines 25, 166), or they will fail
the row on Tess's own capture.

**Drive:** seed an alarm 2 min ahead; KEYCODE_ASSIST; jump the clock to AT−8 s; tap the mic from a device-side loop at
AT−1 s. Assert the precondition as before (listen start < fired < listen end). **Assert:**

- The existing two: `[cortana] session hidden` at or after the fired wall time (new: within 1000 ms, an agent-picked
  bound), and no `cortana_session` node in the all-windows dump.
- New, reachable toast: `ring_surface` and `ring_dismiss` in tess_ring.xml; the windows list holds `SYSTEM:app.tileshell`
  and no `TYPE_-1`; end_ring's Dismiss tap gives `ring ended <id>: dismiss` (today a note; make it an assertion).
- New, mic released and silent: `[speech] stopListening requested` (SpeechService.kt:435) at or after the fired line,
  and no `[speech] speak[` after it. The fire lands about 1.2 s before the endpoint, and the hide cancels the event job.
- Keep: an ALARM player is started; record the surface line.

**Negative control:** run it once on the pre-fix APK (the new clauses must fail as in run 3), then on the fixed APK.
**Also re-run** phase 15 E9 (e9.sh: Tess sets alarms and timers, through the changed `onShow` / `onHide`). **Optional
sub-case** (same cause, not in the edge case's words): Tess over the keyguard (she always listens there,
CortanaModel.kt:140); assert `surface: toast-locked`, the hidden line, and `RingActivity` top resumed.

## Risks

- **Speaking, or holding a card:** the hide cuts her reply and drops a pending request (phase 03 H12: "closing the
  session drops it too"); intended, the alarm owns the screen. **Stepped aside (L13-1):** her window is already off
  screen but she is still hidden, dropping the card and any picked photo draft. Rare; recorded, not guarded.
- **Lock screen:** hiding over the keyguard leaves the locked toast on top (correct); a screen-off already hides Tess
  through the default `onLockscreenShown`.
- **Tess opened while a ring is up:** `drop(1)` keeps her over the toast by choice (Back reveals it); drop `drop(1)` if
  Jeremy wants no Tess during a ring.
- **Recorder:** untouched (ring audio and take unchanged). Section 4 last passed on 4b7ac321; NOT RUN since.
- **Ownership:** CortanaSession is phase 03's part (doc FINAL), so the fix needs an INDEX Change Log line.

## DEFECT.md clauses: fixed vs open

| Run-3 DEFECT.md clause | State |
|---|---|
| §1 Tess not hidden, toast under the session | **OPEN.** No code change since run 3. NOT RUN (no-mic rule) on 61c5b610 / 6c8ebb18 (EDGE_ALARM_CONTEXT-run4/, EDGE_ALARM_CONTEXT/). INDEX row 15 and the 2026-09-27 Change Log list it as under investigation. |
| §2 Edit mode survives the ring | **FIXED** by 0d74245 (StartActivity.kt:174-183); EDGE_ALARM_CONTEXT 26/0/7 on 6c8ebb18. |
| §3 Music resumes by itself (recorded, not asserted) | **OPEN, unruled.** Still `PLAYING; … started: yes` on 6c8ebb18. Phase 10's part, for the lead to rule on. |
| "What passes": recorder through the ring | Passed on 4b7ac321 only; not re-driven since. |

d7c89d4 and 8833614 fixed EDGE_EXACT's defects (foreground type, Tess's exact-alarm notice), not this one.
