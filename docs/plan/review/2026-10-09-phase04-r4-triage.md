# Phase 04 — round 4 triage (the last round; 2026-10-09 10:36)

Reviews: review/2026-10-09-phase04-r4-design.md (Reviewer 1, Opus: BLOCKING 1 · SHOULD-FIX 10 · NOTE 1) and
review/2026-10-09-phase04-r4-testability.md (Reviewer 2, Opus: BLOCKING 3 · SHOULD-FIX 11 · NOTE 3), both over the doc at
db5d37787. Roster: opus + opus (codex banned in this project, 2026-10-04; never Fable). The lead adjudicated each finding
against the doc, the code and the saved AOSP sources before ruling it.

Dispositions: **APPLY** = an agent fix, applied to the doc by one Opus agent and reviewed by the lead; **QUESTION** = it
touches one of Jeremy's rulings or needs his call, asked in the Stage A shape (Q-04-11 …); **APPLY + QUESTION** = the
mechanical part is applied, the ruling part waits on his answer.

| Id | Sev | Finding (short) | Disposition |
|---|---|---|---|
| D1 = V1 | BLOCKING | provision.sh's own force-stop (:173) turns the new service off; the lines must go after it, as the keyboard's re-assert does (:181-183) | APPLY (V1's form: after the force-stop, print and assert the enabled list, wait for `[ac] service connected`; the `accessibility` row reads `ENABLED_ACCESSIBILITY_SERVICES`) |
| V2 | BLOCKING | wd_pair_dialog.sh hard-codes a dead scratchpad and never answers the "Allow wireless debugging" dialog | APPLY |
| V3 | BLOCKING | A6 step 4: `music_open` force-stops (service off) and nothing starts playback | APPLY |
| D2 | SHOULD-FIX | the daemon loop's new-pid lookup goes through `sh -c`, which the static check forbids | APPLY (argv `pidof`) |
| D3 | SHOULD-FIX | the start token is readable by any uid-2000 process, the start is a shell line, the token is never stored | APPLY (`StartCommand.line`, stored token + versionCode, first-caller pid pinning, refused handoff → exit); the trust section says plainly that the token stops a non-looking planter only and the pid pin is the real door |
| D4 | SHOULD-FIX | restart: missing Wi-Fi-joined trigger; key timeout re-written at every start; on an update start the new helper before the old exits | APPLY |
| D5 | SHOULD-FIX | Android's "Allow wireless debugging" dialog opens under the open panel / Tess's pane | APPLY |
| D6 | SHOULD-FIX | every PendingIntent send needs the background-start opt-in; an activity-starting send closes the panel | APPLY (also settles V10: an action that is not an activity leaves the panel open) |
| D7 | SHOULD-FIX | the three windows need full-screen layout flags and cutout mode ALWAYS, or the strip sits below the S25's punch hole | APPLY (+ task 1 (l) with the emulated hole cutout) |
| D8 | SHOULD-FIX | consuming every volume key breaks the camera's volume shutter | APPLY (pass-through while a still-image camera app is in front; P7 adds the camera check) |
| D9 | SHOULD-FIX | a home / recents gesture over a fully open panel leaves it open | APPLY |
| D10 | SHOULD-FIX | phase 12, 19 and 07 docs disagree (step ids, the wizard steps for pairing and the helper, the overlay row, E4(d)) | APPLY for the ids (`write_settings`), the phase 19 / 07 / E4(d) doc-edit list; **QUESTION Q-04-11** for phase 12's "pairing and helper are wizard steps" |
| D11 | SHOULD-FIX | dropping the phone motion row drops PLAN RV11's phone check for timings ≤ 17 ms | **QUESTION Q-04-12** (RV11 is an agent call in PLAN, but keeping or dropping a phone recording is his time); the Dropped line reads "pending Q-04-12" until he answers |
| D12 (a) | NOTE | "WRITE_SECURE_SETTINGS granted through the helper" → over the app's own adb session; "never by itself on an untrusted one" | APPLY (the wording is the lead's reading in Q-04-1's line, not his words; corrected with a dated note, his answer unchanged) |
| D12 (b) | NOTE | precise location is already declared and asked (Tess's `background_location` step); add a "Location off" reason | APPLY |
| D12 (c) | NOTE | the volume panel over a secure keyguard shows the track title; Q-04-3's reason covers a title | **QUESTION Q-04-13**; "W10M showed it over its lock screen" (line 77) has no source and is marked UNVERIFIED |
| V4 | SHOULD-FIX | every uiautomator dump unbinds the service; overlay steps race the rebind | APPLY (`a11y_wait`) |
| V5 | SHOULD-FIX | A2 (f) asserts `(keyguard)` on a path that logs `(screen off)`; the volume panel over the keyguard has no device check | APPLY (the volume half follows Q-04-13's answer) |
| V6 | SHOULD-FIX | A3 passes vacuously on a re-run; Location on is never asserted | APPLY |
| V7 | SHOULD-FIX | expectations computed by the app's own functions (uid claim, `QuickGrid.layout`, `BrightnessCycle`); unstated start states | APPLY (literal expectations; `ps` label; rotation and battery / date sources) |
| V8 | SHOULD-FIX | A4 (b) and (c) can pass without testing what they name; nothing checks the shell does not re-enable its own service | APPLY |
| V9 | SHOULD-FIX | the four fallback targets have no recorded identity (the Internet panel is a SystemUI dialog since Android 12) | APPLY (task 1 (c) records each; rows assert it) |
| V10 | SHOULD-FIX | A5 step 3 assumes the panel survives Ack | APPLY (follows D6: Ack is not an activity, the panel stays; the absence slice is proven readable) |
| V11 | SHOULD-FIX | p04.sh is missing helpers the rows name | APPLY |
| V12 | SHOULD-FIX | rows read nodes whose tags are never named | APPLY (tag list in the tasks; leaves asserted by equality) |
| V13 | SHOULD-FIX | nothing on the device checks the overlays' RV10 density | APPLY (A2 (a) pixel bounds) |
| V14 | SHOULD-FIX | Flashlight, Quiet hours, Brightness, Rotation lock never tried on One UI | APPLY (P2) |
| V15 | NOTE | edge-case cites their rows do not assert; whether a Tess request counts as a tap | APPLY (a Tess request counts as a tap: Q-04-2's "the same rule") |
| V16 | NOTE | static-check wording; phase 12 e1.sh's grant list; phase 12 lines 44 and 923 need Change Log corrections | APPLY (into the cross-phase doc-edit list; line 44 waits on Q-04-11) |
| V17 | NOTE | restores (qa-notify force-stopped) and the music stream started at 0 | APPLY |

Author's points 1–9 (the brief): answered by the findings above (1 → D10 / Q-04-11; 2 → D1 / V1, no other built driver
breaks per Reviewer 1; 3 → Q-04-12; 4 → Q-04-13; 5 → sound, V14; 6 → D12 (a); 7 → D12 (b); 8 → sound; 9 → D10, V16).

Round 1–3 ids both reviewers found correctly applied: R3T-B1, R3T-B2, R3T-B3, R3T-M8, R3-m4, R2-13, R2-m12, R3F-01,
T-M21 / R2-m4, T-B5 / F1-M12. Superseded by the re-cut: R3T-M3, R3T-M9.

## Questions for Jeremy (Stage A shape, one per message, answers recorded in Decisions)
- Q-04-11 — Should the setup wizard walk the helper's pairing (phase 12's text says yes), or leave it to the helper's own page?
- Q-04-12 — Keep a phone screen-recording row for the motion timings (PLAN RV11), or drop it for this phase?
- Q-04-13 — Over a locked phone, does the volume panel's media section show the track title?

Answered (recorded dated in the phase doc's Decisions and applied): Q-04-11 "(a)" 2026-10-09 12:11 (the wizard walks the
three permission steps only; pairing on the helper's page); Q-04-12 "(a)" 12:18 (no phone recording; RV11 set aside for phase 04,
INDEX Change Log); Q-04-13 "(b)" 12:28 (buttons, no track title, over a secure lock screen).
