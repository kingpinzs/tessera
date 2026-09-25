# EDGE_ALARM_CONTEXT — product defects (build 61c5b610716197e3, emulator-5560, 2026-09-25)

Row log: `EDGE_ALARM_CONTEXT.txt` (24 passed, 2 failed, 7 recorded). Cases 1 (Tess listening) and 4 (recorder running)
are NOT RUN under the owner's no-microphone rule (2026-09-25); case 5 (glance) is NOT RUN, phase 07 unbuilt. The two
failing clauses are case 2's, and the same two failed in runs 2 and 3 on 4b7ac321 (emulator-5558,
`EDGE_ALARM_CONTEXT-run2/`, `-run3/`). The only app change between the builds is the LoopSpinner fix (9a34ae17,
`clock/ClockWidgets.kt`), which does not touch the ring or Start.

## 1. An alarm firing while Start is in edit mode does not end edit mode

Repro: an alarm 2 min ahead (AlarmClock API); Home; a hold on the first Start tile (`tile:slot:PEOPLE`); the clock
jumped to 3 s before the alarm.

- Precondition asserted and passing: `edit_disc:unpin` on screen (`edit_on.xml`, `edit_on.png`) and
  `11:30:06.734 [edit] hold 783ms on slot:PEOPLE: edit mode on`.
- The fire: `11:31:02.041 [alarms] fired a352db0b2 kind=alarm late=2041`, `11:31:02.108 [alarms] surface: toast-overlay
  a352db0b2` (`ring_edit_launcher.txt`). No `[edit] exit first frame` line follows the fire in that slice.
- The all-windows dump at the ring (`edit_ring.xml`; windows `SYSTEM:app.tileshell` = the overlay toast, not focused,
  and `APPLICATION:app.tileshell` = Start, focused) still holds `edit_disc:unpin`. `edit_ring.png`: the toast over Start
  with its tiles still dimmed in edit mode. After Dismiss, `edit_after.xml` still holds the disc; the driver leaves edit
  mode with Back, and only then `11:31:14.723 [edit] exit first frame …` is logged (`ring-launcher.txt`).

Cause (read from the source, unchanged since run 3's DEFECT.md): the in-use toast is a `TYPE_APPLICATION_OVERLAY`
window with `FLAG_NOT_FOCUSABLE` (`clock/RingOverlay.kt`), so StartActivity stays resumed and focused, and nothing on the
ring path asks Start to leave edit mode; edit mode exits only on Home, Back, a tap or a launch (`StartActivity.kt`
`homeEvents` / `backEvents` / `launchSatellite`).
Failing clauses: "Edit: edit mode ends — [edit] exit first frame …", "Edit: … and no edit disc is on Start …".

## 2. (Recorded, not a failing assertion) Music resumes by itself after the ring

The edge case: phase 10's player "pauses on the transient focus loss and, per its E9, does not resume by itself —
recorded, not hidden". The pause is asserted and passes (session PAUSED, no USAGE_MEDIA player while the ALARM player
plays). The RECORD 6 s after Dismiss reads `PLAYING; a USAGE_MEDIA player started: yes`, as in runs 2 and 3: the player
resumed by itself when the ring abandoned its AUDIOFOCUS_GAIN_TRANSIENT (`audio_music_ring.txt`: `requestAudioFocus() …
USAGE_ALARM … req=2`, then `abandonAudioFocus()`). That contradicts phase 10's rule — for the lead to rule on; no
assertion added.

## Not driven this run (owner's no-microphone rule)

Case 1's defect (the session is not hidden; the toast is drawn under the voice-interaction window) and case 4's passes
are run 3's evidence on 4b7ac321 (`EDGE_ALARM_CONTEXT-run3/DEFECT.md`, `-run3/EDGE_ALARM_CONTEXT.txt`); neither was
re-driven on 61c5b610. The row asserts that no audio capture happened on the device while it ran (`mic_events_*.txt`).
