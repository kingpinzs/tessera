# Phase-doc review round 2: triage (2026-09-16)
Inputs: review/2026-09-16-phases-r2-opus-design.md (reviewer 1: R2D-01…R2D-16, R2-m1…R2-m13) and
review/2026-09-16-phases-r2-opus-test.md (reviewer 2: T-B1…T-B5, T-M1…T-M22, T-m1…T-m16), plus the round-1 IDs both files re-checked.

Dispositions: OWNER = Jeremy decides WHAT; nothing applied, recorded "OWNER, pending" · R7 = needs the R7 footage pass
(docs/plan/r7-measurements.md, written by another agent); affected values tagged "(pending R7)" with a line saying R7 supplies them ·
AGENT = applied to PLAN.md / INDEX.md / the phase docs under P1–P5, A4, Q12, RV9, Rule 4 and Rule 16.

Counts (new round-2 findings, 72): AGENT 68 · OWNER 3 · R7 1. Round-1 items: 2 still open, both AGENT (applied); 32 resolved in round 1, no action.

## OWNER, pending (asked one at a time, nothing changed for them)
| ID | Question |
|---|---|
| R2D-06 | When the phone is locked and Cortana's AI is asked something that isn't a fixed command: (a) answer general questions with no Notebook memory or personal data, anything personal says "Unlock to continue"; (b) work fully while locked, memory included; (c) other / clarify. |
| R2D-12 | W10M's keyboard supported several writing languages (swipe the space bar to switch): (a) English only; (b) English plus languages you name; (c) other / clarify. Held with it: R2D-12's non-language behaviour list (accents on hold, one-handed left / right via &123, &123 slide-to-type, caps lock double-tap, double-space period, learning) and T-m13's language key; the parts T-m13 and the R6 leftovers name (space-bar drag, handedness, switch-back-after-emoticon) were applied. |
| R2D-13 | W10M reminders could also fire "when I get to <place>" and "next time I talk to <person>": (a) time reminders only (with W10M's repeat and photo); (b) time, place and person reminders; (c) other / clarify. The card fix R2D-13 also describes was applied under T-M9 (both options include repeat and photo). **Correction 2026-09-17 (review R3D-10 / R3T-m15):** the question Jeremy actually saw was lettered differently: option A was all three kinds (time, place, person), and he chose A. review/2026-09-16-phases-r2-owner.md records that correctly; the (a) / (b) order in this row is this file's rewording, not the question as asked. The ruling is unchanged. |

## Round-2 findings
| ID | Disposition | Applied fix (one line) |
|---|---|---|
| R2D-01 | AGENT | 07: glance starts only once `isKeyguardLocked()` when a secure lock is set (trigger verified at build start), never `lockNow()`; E6 sets `lock_screen_lock_after_timeout 5000` + `screen_off_timeout 15000`; P4 at 5 s / 30 s; lock-instantly-off edge; H11. |
| R2D-02 | AGENT | 02: dwell rule, a tile under the dragged tile holds still 2000 ms (approximation above R6 §1.6.1's ≈1.7 s folder hover), release in the dwell = folder, reflow only after it; L31 / L34 / E3 rewritten; E8 runs both paths; H20. |
| R2D-03 | AGENT | 01: Back history window = later of `KEYGUARD_SHOWN` and user unlock; candidates need a launcher activity, HOME / FallbackHome / SystemUI / permission controller / installer / resolver / IME excluded; 06 and 07 ADD their activities; E20 reboot step captures `dumpsys usagestats`. |
| R2D-04 | AGENT | 03: memory-map claim withdrawn; espeak-ng-data extracted once with checksum, models in native buffers inside `app.tileshell:speech` (5-min idle release); E12 kills it, P4 records meminfo; 05 / 06 bind it; 08 P2 bound = latest baseline + 50 MB. |
| R2D-05 | AGENT | 03: gate lives in the action layer; alarms / timers as voice activities with `EXTRA_SKIP_UI` (re-ask if refused over keyguard), music via media session / `playFromSearch`, directions / photo / note / add-event gated, date = time; E10 + P6. |
| R2D-06 | OWNER | pending (see above); no change. |
| R2D-07 | AGENT | 06: trust-touching VVM helper verbs (GBA for voicemail only, mstore host only) with adversarial review; P5 + edge for foreign callers; 04 Scope and P1 include the voicemail probe. |
| R2D-08 | AGENT | 07: keep-screen-on + 0.01 brightness override; turn-off via phase 04 accessibility `GLOBAL_ACTION_LOCK_SCREEN` (depends-on [01, 04]); re-trigger guard; E7 asleep ≥ 60 s; P5 fingerprint check; always-on over ceiling re-asks Jeremy. |
| R2D-09 | AGENT | 07: settings page per R6 §6.1.6 (show toggle, "Show Glance screen for" list approximation H6, charging, background picture = Start image H8, night mode 50 % dim H9); defaults approximations H7; E8–E10. |
| R2D-10 | AGENT | 07: date line 0.776 H, notification row 0.842 H left-aligned, "h:mm" format (R6 §6.1.5 / A21, LOW) in Decisions + E3; burn-in re-derived to an 8-position cycle spanning the height, A21 cited; H4 / H5 / H10. |
| R2D-11 | AGENT | 03: locked look (R6 §3.5.5, LOW): opens listening, "What's on your mind?", no ≡ menu / Settings / Reminders / Notebook; E9 dump check; H11. |
| R2D-12 | OWNER | pending (see above); no change beyond the T-m13 / R6-leftover parts. |
| R2D-13 | OWNER | pending (see above); card geometry applied under T-M9. |
| R2D-14 | AGENT | 03: text box §3.3.1–3.3.9 tagged, "Ask me anything" pick H13, keyboard-up approximation H14, black page §3.1.14, Search-tap motion §4.2.2 H15; session hides system bars and draws W10M bars (E11). |
| R2D-15 | AGENT | 01: "New" placement tagged (15063, S2, ± 1.4 epx) and measured in E12; X14 rules (pre-shell apps, updates, unseen launches); §5.1.7 noted, H1 extended; 02 ADD pinning clears caption (E2). |
| R2D-16 | AGENT | 01: status bar 28–29 epx (R3 C4), X16 glyph layout, X17 three-slot nav keys (right slot for phase 03 Search), X18 Back-from-Start exit (R6 §4.1.3 LOW); E19 pointer fixed. |
| R2-m1 | AGENT | Stale text: 03 L33 / L35, 06 L24 struck / L26 / P4 / L69 edge, 01 L48 and 08 L22 A11 lists brought up to date. |
| R2-m2 | AGENT | PLAN round-2 resolutions: PQ1 scope (voice path only; R4 and phase 06 GPL bans stand; swap = rebuild runtime with TTS off + GPL-free TTS; RV2 holds); 03 L35 note. |
| R2-m3 | AGENT | 09: replay freezes recorded context inputs and pins backend / threads / batch; "impossible" edge replaced by a tested harness-defect edge (E8). |
| R2-m4 | AGENT | 09: provider rejects the helper's pid (recorded at handoff, cleared on binder death), logs every call; E5 + edge; 04 allow-list has no provider call. |
| R2-m5 | AGENT | 01: P1 records Motion smoothness; P4 checks the active mode is unchanged during the run. |
| R2-m6 | AGENT | 01: list-row press amount = approximation X19 (15 % white overlay), H27. |
| R2-m7 | AGENT | 03: Search key with another assistant opens Cortana's role notice (Decision + edge); H8 widened to the toggle wording. |
| R2-m8 | AGENT | 03: Cortana slot ADD runs once (stored flag); E6 checks an unpinned tile stays gone; edge. |
| R2-m9 | AGENT | 05: IME show / hide failure branch: system slide accepted in H10, E3 then checks only what the IME draws. |
| R2-m10 | AGENT | 07: E3 clock inset 29 ± 4 epx (R6 §6.1.4). |
| R2-m11 | AGENT | 01: Home on Start scrolls to top = approximation X20, H28. |
| R2-m12 | AGENT | 04: queue item 7 notes W10M's hold-Back app switcher (R6 §4.1.6 MEDIUM) as the fidelity default. |
| R2-m13 | AGENT | 01: shared-uid callers rejected with `error: "shared-uid"`; E16 + edge. |
| T-B1 | AGENT | 03: E8 uses `input keyevent KEYCODE_ASSIST`; E9 sleeps / wakes to the keyguard then `KEYCODE_ASSIST`, falling back to `cmd voiceinteraction show` if SystemUI declines over keyguard; P3 long-press Home on Samsung's bar. |
| T-B2 | AGENT | 01: E20 uses AOSP DeskClock (launcher entry + Timer tab), system Back pass, Settings deep link as X24 edge (H32), lock step with `wm dismiss-keyguard`, reboot step with `sys.boot_completed` polling. |
| T-B3 | AGENT | 01: E10 same-frame clause replaced by per-tile A8 band means, non-identical sequences and a coincidence count ≤ λ + 3√λ + 3 (λ = pairs × D / (60 T²)); tile-timing Decision (no de-collision). |
| T-B4 | AGENT | 03: listening persona from R6 §3.1.6–3.1.10 (1.04 ± 0.02 s, sizes, antiphase, 243.8 epx, 333 ± 17 ms), superseding A22's 600 ms; E4 split. |
| T-B5 | R7 | 06 Phone / Messaging values, 09 Notebook values, 04 action center / volume motion tagged "(pending R7)" with R7 supply lines and E-rows (06 E7, 09 E9, 04 E4); agent parts applied: 04 A19 / A20 geometry tagged (E5, H5 / H6), 09 P4 designs each with a look row (H3–H6). |
| T-M1 | AGENT | PLAN RV11 (source tolerance + 1 capture frame, `show_touches`, swipe bracketing, ffprobe ≥ 55 fps, 180 s / 720p fallback); 02 E7 740 / 830 ms bracket; phone P-rows for ≤ 17 ms tolerances in 01 / 02 / 03 / 05. |
| T-M2 | AGENT | 01: Scope and E10 use R3 A8 per-tile bands (flip 4.96 ± 0.22 s, crossfade 4.4 ± 0.4 s). |
| T-M3 | AGENT | 01: E10 press clause per style (none = zero change, tilt vs R1 §3.1, P4 press vs its Decisions numbers); app-list swipe = X13, H21. |
| T-M4 | AGENT | 01: status bar height tagged, glyph layout X16, nav-key slots X17; E19 checks insets sources + screencap, swipe `input swipe 540 0 540 400`, re-hidden within 5 s (system-owned timeout). |
| T-M5 | AGENT | 01: P4 = gfxinfo reset, defined 20-swipe script, unchanged mode, janky ≤ 5 % and 99th pct ≤ 2 vsync rounded up (reason recorded). |
| T-M6 | AGENT | PLAN RV12 baseline / restore rule; header line in every phase; restores added to 01 E3 / E9 / E12, 03 E2 / Doze, 07 E6 / E8 / E10, 08 E0 / E1, 09 E3 / E7. |
| T-M7 | AGENT | 01: diagnostics ring buffer read via `dumpsys activity service app.tileshell/<listener>` (`Service.dump()`) and a Settings > Diagnostics page; build task 18; later phases cite it. |
| T-M8 | AGENT | 02: E5 reads the secondary tile's arguments from the test APK's TextView via `uiautomator dump`. |
| T-M9 | AGENT | 03: reminder card per R6 §3.4.2 (MEDIUM) with working recurrence (options H19) and photo; missing-time card §3.4.3 (LOW, H16); calendar add (H17) and delete cards (H18) approximations; "add more" texts only; E7 + edge rewritten. |
| T-M10 | AGENT | 03: action layer sends via `SmsManager` and calls via `TelecomManager.placeCall`, checklist rows added; 06 E5 checks the shell's in-call UI and thread; PLAN RV4 amended. |
| T-M11 | AGENT | 03: text box, page background and locked look tagged (with R2D-14 / R2D-11); "Unlock to continue" card approximation H12. |
| T-M12 | AGENT | 05: same fix as R2-m9 (H10, conditional E3). |
| T-M13 | AGENT | 07: same fix as R2D-09 / R2D-10 (settings, defaults H7, E5 retargeted to the new durations, §6.1.5 tags). |
| T-M14 | AGENT | 07: P1 battery measured off USB (`batterystats --reset`, unplug or wireless debugging, level before / after). |
| T-M15 | AGENT | 08: metered policy ("Waiting for Wi-Fi" + "Download now"); E0 uses airplane mode, metered wait checked; E1 and 05 E8 reuse phase 01 E9's network-off proof. |
| T-M16 | AGENT | 01: AVD `hw.ramSize=8192`, `disk.dataPartition.size=16G` in Scope, Decisions and task 1; 08 references it. |
| T-M17 | AGENT | 01 P9 memory baseline; 03 P4 re-records it; 08 P2 = baseline + 50 MB; 09 replay thresholds (≤ 5 s per test, ≤ 5 × N s run, PSS ≤ 4 GB, no SEVERE). |
| T-M18 | AGENT | 09: E7 locked correction goes sleep → wake → keyguard → Cortana over it → WAV → no file diff + refused diagnostics entry → PIN cleared; declined-card variant. |
| T-M19 | AGENT | 09: E8 replays one test 5 × in a run and 5 × after force-stop; identical action required. |
| T-M20 | AGENT | 09: replay triggers named (harness push, model download, first start after app update, Settings / PC script); E6 uses a rules push and `install -r` with a higher versionCode; no template-swap hook. |
| T-M21 | AGENT | 09: pid rule in Scope + Decision + E5 (acceptance and logging from `adb shell`; no test verb makes the helper call, Rule 4, so the rejection is covered by adversarial review); 04 edge = allow-list has no provider call. |
| T-M22 | AGENT | 06: VVM edge = post-probe runtime failure (error + button dials voicemail); L24 struck, L26 "ruled B", P4 gate text fixed. |
| T-m1 | AGENT | 03: Doze observable = `deviceidle enable deep`, `battery unplug`, sleep, `force-idle`, with restores. |
| T-m2 | AGENT | PLAN RV13: dump retry ≤ 3, or `UiDevice` with `setWaitForIdleTimeout(0)` for never-idle screens. |
| T-m3 | AGENT | 01: same fix as R2D-15 (rules X14, geometry measured in E12). |
| T-m4 | AGENT | 01: Camera fixture check uses `query-activities -a android.media.action.STILL_IMAGE_CAMERA`. |
| T-m5 | AGENT | 01: E5 latency from `StatusBarNotification.getPostTime()`. |
| T-m6 | AGENT | 01: one lock rule (`KEYGUARD_SHOWN` / user unlock) in L83–84, X12 and E20 lock step. |
| T-m7 | AGENT | 03: exported-components allow-list in Decisions; E5 compares `dumpsys package` against it. |
| T-m8 | AGENT | 03: reply passes on exact diagnostics text + `parecord` RMS above −40 dBFS. |
| T-m9 | AGENT | 07: E3 inset ± 4 epx; E1 expects `mWakefulness=Awake` and override 0.01. |
| T-m10 | AGENT | 07: reuses phase 04's overlay row (Scope, Decision, task 5). |
| T-m11 | AGENT | "R3 §B" → R3 A11 (PLAN L100, 01 Scope / L70 / task 10 / X7); A11 tolerances replace "~"; A11 internet lists; INDEX R6 + R7 rows and step 5-7 status; PLAN R6 / R7 research rows; RV9 names R6 / R7. |
| T-m12 | AGENT | 08: request set (20 typed requests), timer start / stop, decode formula, thermal polled every second. |
| T-m13 | AGENT | 05: colour tolerance ± 32 (H11), sounds + vibration approximation (H12), space-bar drag (H13, E9), handedness (H14–H16, E10; mirrored dot 1077.5 phys derived from R6 §2.1 instead of 1082), switch-back setting (H17–H18, E11); language key held with R2D-12. |
| T-m14 | AGENT | 02: fixed point as a screen-height fraction; rule thickness unit (≈1–2 epx, 1 epx drawn); context menu (H21) and secondary-tile dialog (H22) approximations. |
| T-m15 | AGENT | 01: X6 derived 47–48 epx from R6 S1; X19 list-row press; X21 dark default; X22 stale state / 60 min; X23 sliders from A20; X15 parallax 15063 check; onboarding checklist marked P4 design (H33). |
| T-m16 | AGENT | 03: Search hold = long-press timeout approximation (H20); emulator flag `-allow-host-audio` / `adb emu avd hostmicon` (verified in Android emulator release notes 29.0.6). |

## Round-1 items re-checked by both reviewers
Still open in round 2, applied now with the round-1 fix shape:
| ID | Disposition | Applied fix (one line) |
|---|---|---|
| F1-M12 | AGENT (phase 04 part) | 04: Approximations table Y1–Y4 with H4–H7, H3 narrowed; A19 / A20 geometry tagged; E4 motion (pending R7); INDEX step 7 says phase 04 does not freeze with the others. |
| F2-M5 | AGENT (phase 09 part) | 09: replay thresholds written with their derivation from phase 08 (≤ 5 s per test, ≤ 5 × N s per run, PSS ≤ 4 GB, no SEVERE); P2 rewritten. |

Resolved in round 1 per both reviewers; no action (their residue is tracked under the round-2 IDs cited by the reviewers):
F1-B1, F1-B2, F1-B3, F1-M1, F1-M2, F1-M3, F1-M4, F1-M5, F1-M6, F1-M7, F1-M8, F1-M9, F1-M10, F1-M11, F2-B1, F2-B2, F2-B3, F2-M1, F2-M2, F2-M3, F2-M4, F2-M6, F2-M7, F2-M8, F2-M9, F2-M10, F2-M11, F2-M12, F2-M13, F2-M14, F2-M15, F2-M16.

## Notes for the next agent
- Deviations from a reviewer's literal fix, with reasons: T-m13 mirrored cursor dot at 1077.5 phys (the reviewer's 1440 − 358 = 1082 is not a gap crossing because the grid margins are asymmetric); T-M21 has no E-row making the helper call the provider (Rule 4 forbids a test verb, and phase 04's allow-list has no provider call); T-B3's bound uses λ + 3√λ + 3 instead of "≤ 2 × expected" (for a faithful independent-timer scheduler, Poisson false-failure rate of 2 × λ is 26 % at λ = 0.3, 10 % at λ = 1.1, 6 % at λ = 3.4; the chosen bound stays ≤ 0.03 % across those); R2D-02's dwell is 2000 ms because R6 §1.6.1's only footage held a target ≈1.7 s and still made a folder.
- UNVERIFIED items recorded for build start: the keyguard-locked trigger (07), `GLOBAL_ACTION_LOCK_SCREEN` leaving fingerprint unlock available (07 P5), hiding system bars over the voice-interaction window (03 E11), the IME owning its slide (05), the IME's transparent band for the raised keyboard (05), SystemUI accepting assist over the keyguard (03 E9 fallback).
