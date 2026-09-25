# EDGE_ALARM_CONTEXT — product defects (build 4b7ac321ce4d0ede, emulator-5558, 2026-09-24)

Row log: `EDGE_ALARM_CONTEXT.txt` (42 passed, 4 failed, 6 recorded). The same four clauses failed in run 2
(`EDGE_ALARM_CONTEXT-run2/`); run 1 (`-run1/`) was stopped by hand after its fire missed the listen (a driver timing
fault, fixed: the clock now jumps first and a device-side loop taps the mic 1 s before the alarm). In every failing
clause the precondition is asserted and passes; the product does not do what the edge case says.

## 1. An alarm firing while Tess is listening does not hide the session — the toast is drawn UNDER it

Repro: an alarm 2 min ahead (AlarmClock API); Tess open (KEYCODE_ASSIST); the clock jumped to 8 s before the alarm; the
mic tapped 1 s before it (a loop on the device's own clock).

- The fire is inside the listen (`ring_tess_speech.txt`, `ring_tess_launcher.txt`):
  `22:54:59.053 [speech] listening for pid=14814 (cortana)` → `22:55:00.019 [alarms] fired a9b8bad4c kind=alarm late=19`
  → `22:55:00.085 [alarms] surface: toast-overlay a9b8bad4c` → `22:55:01.319 [speech] asr: final gen=1 via endpoint`.
- No `[cortana] session hidden` line follows; the gesture driver's all-windows dump at the ring (`tess_ring.xml`) holds
  `cortana_session`, and its window list is the session's window alone
  (`TYPE_-1:app.tileshell:[0,0][1080,2340]:focused=true`). The screencap (`tess_ring.png`) shows Tess's page
  ("I didn't catch that.") and NO toast: the alarm sounds (an ALARM player started — asserted) with Snooze / Dismiss
  hidden under the voice-interaction window.

Cause: `RingService.present()` adds the in-use toast as a `TYPE_APPLICATION_OVERLAY` window
(`clock/RingOverlay.kt`), which sits below the voice-interaction window, and nothing asks `CortanaSession` to hide
(`cortana/CortanaSession.kt` `onHide` is reached only through Back / Home / `closeRequests`).
Failing clauses: "Tess: the session hides — [cortana] session hidden …", "… no cortana_session node …".

## 2. An alarm firing while Start is in edit mode does not end edit mode

Repro: an alarm 2 min ahead; Home; a hold on `tile:slot:PEOPLE` → `edit_disc:unpin` on screen and `[edit] hold 783ms …:
edit mode on` (both asserted); the clock jumped to 3 s before the alarm.

- `[alarms] fired a9d21dea0` and the overlay toast (`ring_surface` in `edit_ring.xml`) — but no `[edit] exit first frame`
  line in `ring_edit_launcher.txt`, and `edit_disc:unpin` is still in the all-windows dump (`edit_ring.png`: the toast
  over Start still in edit mode). The driver leaves edit mode with Back after the ring.

Cause: the overlay window is `FLAG_NOT_FOCUSABLE`, so StartActivity stays resumed and focused; edit mode exits only on
Home, Back, a tap or a launch (`StartActivity.kt:136,151,319`, `start/EditGestures.kt:140,164`).
Failing clauses: "Edit: edit mode ends — [edit] exit first frame …", "… no edit disc is on Start …".

## 3. (Recorded clause, not a failing assertion) Music resumes by itself after the ring

The edge case reads "phase 10's player pauses on the transient focus loss and, per its E9, does not resume by itself —
recorded, not hidden". The pause is asserted and passes (session PAUSED, no USAGE_MEDIA player while the alarm plays).
The RECORD 6 s after Dismiss reads `PLAYING; a USAGE_MEDIA player started: yes` in both runs: the player RESUMED by
itself when the ring abandoned its AUDIOFOCUS_GAIN_TRANSIENT (`audio_music_ring.txt`: `requestAudioFocus() … USAGE_ALARM
… req=2`, then `abandonAudioFocus()`). That contradicts phase 10's rule ("not resuming after a transient loss the user
did not ask to resume", phase-10 build task 4; its E9) — for the lead to rule on; no assertion was added.

## What passes

Recorder: the take continues through the ring (phase recording, elapsed advancing, the :recorder slice from the take's
start holds its `[recorder] start` and no `paused` line), the alarm plays on USAGE_ALARM while the shell's MIC capture is
active with `silenced:false`, and the saved take is as long as its clock. Glance: NOT RUN (phase 07 unbuilt, no glance
component). Restore: overlay grant allow, store empty, the take deleted, fixtures and volume as found.
