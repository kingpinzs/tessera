# Phase 14 (pod bay) — Jeremy's sign-off checklist

The emulator gate is passed: every row on one build (apk aaf9a1d8, `FINAL/final-run.txt`), gate round 2 PASS / PASS
(`../../review/2026-10-01-phase14-gate-r2.md`). The phase goes `done` when every row below is signed off (reply with the
ids that pass, and what you saw for any that do not). Phone rows need a build on the S25 Ultra: CI builds one when the
`phase-14` branch is pushed.

## Look and feel — accept or reject (emulator screenshots under `qa/phase-14/`, or on the phone)

| id | what you are accepting |
|---|---|
| H1 | the pod bay as a whole: a page left of Start with four at-a-glance pods (no W10M original) |
| H2 | the pod frame: type-only Metro cards, the spacing, accent-coloured headers |
| H3 | each pod's content and caps (Agenda 6 rows, Weather 3 lines, Now playing with transport, Reminders), and Now playing showing a paused session |
| H4 | the empty-state wording ("Nothing on your calendar today", "No weather yet", "Nothing playing", "No reminders — ask Tess to remind you", the access lines) |
| H5 | Settings > Pod bay: one switch per pod, fixed order, the empty-bay line |
| H6 | the doors line: "I'm afraid I can't do that, Dave." spoken on a card, then Tess closes, then the pod bay opens |
| H7 | the plain replies "Opening the pod bay." / "Closing the pod bay." |
| H8 | Back from the pod bay goes to Start, not to the last app |
| H9 | the pod bay stays the page across screen-off and lock |
| H10 | the pod bay sits on the app list's blurred backdrop, not on Start's wallpaper |
| H11 | a pod launch plays no Start exit animation; coming back plays Start's entrance |
| H12 | any approximation not covered by H2–H11 |
| H13 | Tess speaking the doors line on the phone (with P1) |

## Phone rows — on the S25 Ultra only

| id | do this | pass when |
|---|---|---|
| P1 | open Tess, say "open the pod bay doors" in your own voice | she says the doors line out loud, closes, and the pod bay opens |
| P2 | with One UI gesture navigation: swipe right on Start; swipe in from the left edge; swipe from the gesture hint area | the pan opens the pod bay; the left-edge swipe is One UI's Back; the hint area does not open the pod bay |
| P3 | with Samsung Calendar events today, and with Samsung Music or YouTube Music playing | the events show in Agenda; the track shows in Now playing and its previous / play-pause / next work |
| P5 | on the pod bay, press Home | Start shows |
| P6 | start swiping the pod bay open and press Home mid-swipe | it settles on Start |
| P7 | with the pod bay showing: let a reminder fire; complete another in Tess | each one's row leaves the Reminders pod |
| P8 | make a place reminder and a person reminder | each row's second line reads as it does on Tess's Reminders page |
| P9 | in one Tess session say "open the pod bay", then at once another request | the second request is what happens; the pod bay does not open afterwards |
| P10 | swipe right on Start while a live tile is mid-flip | the pod bay opens normally |
| P11 | turn on Show more tiles; switch theme light / dark; change the accent; move the tile transparency slider | the pod bay still lays out; headers follow the accent; pods do not follow the slider |
| P12 | switch a pod off in Settings while its content is changing (e.g. Now playing during a track change) | nothing of that pod is drawn |
| P-L14-1 | lock the phone, open Tess over the lock screen, say "open the pod bay doors", then tap the microphone, start a sentence and tap Unlock mid-sentence; enter the PIN | the PIN pad comes up; after the PIN she says the doors line and the pod bay opens; nothing was answered behind the PIN pad |

(P4 was struck on 2026-09-23.)

## Known, recorded, not blocking (for your information)

- With Location off and a forecast cached, the Weather feed still makes its own start-up refresh for the last place when
  the launcher process starts (phase 01's "keep serving the last place"). What phase 14 fixed is the pod bay adding a
  request on every Start resume.
- Tess opened over the lock screen and the phone then unlocked by fingerprint or face (not by the card): Back on her
  Places page can do nothing until the Windows key is pressed (gate round 2, design N1 — a side effect of the
  locked-destination guard; not built, to keep the one final APK).
- The locked-destination guard has no test (code-level only); `speak.sh` no longer treats a failed injection as fatal once
  a final exists (phase 03's E3 is the one exposed step). Both recorded for a follow-up.
- The spoken edge case of L14-1 (case D) crashed the emulator twice and is replaced by phone row P-L14-1.
