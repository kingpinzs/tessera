# Phase-doc review, round 2 — Reviewer 2 (opus): testability, evidence, internal consistency — 2026-09-16

Scope read: PLAN.md, INDEX.md, phase-01 … phase-09, round-1 files (phases-fable, phases-fable2) and phases-triage (with Owner rulings).
Research files (w10m-measurements.md = R3, r6-measurements.md = R6, w10m-reference.md = R1) opened only to check a cited claim.
Phase 04 checked for internal consistency only (R4 pending).

Verification limit: adb and the emulator are not installed here, so no command was run. Command behaviour was checked against AOSP
source fetched 2026-09-16 (aosp-mirror/platform_frameworks_base `main` and android.googlesource.com `main`):
PhoneWindowManager.java, core/res/values/config.xml, device/generic/goldfish phone overlay config.xml, InputShellCommand.java,
uiautomator DumpCommand.java, packages/apps/Settings AndroidManifest.xml, ActivityRecord.java + Intent.java (dump format),
DeviceIdleController.java, frameworks/av screenrecord.cpp, LockSettingsShellCommand.java, ConnectivityService.java,
VoiceInteractionManagerServiceShellCommand.java, UserManagerService.java, the SDK system-image repository XML (sys-img2-4.xml).
Where a claim still rests on memory rather than source, the finding says "not certain".

Severity used here: BLOCKING = an acceptance row that a correct build cannot pass (or passes vacuously), a row that measures against a
contradicted or missing value, or an RV9 FINAL gate that is not met. MAJOR = a row that is ambiguous or unverifiable as written, a missing
threshold, a contradiction between docs. MINOR = wording, stale text, small gaps.

Counts: BLOCKING 5 · MAJOR 22 · MINOR 16. Owner questions: none (every fix below is an agent call under Q12 / RV9 / existing rulings).

---

## 1. Round-1 BLOCKING and MAJOR findings: status in the current docs

| Round-1 ID | Status | Evidence (current docs) |
|---|---|---|
| F1-B1 Kokoro GPL | RESOLVED | phase-03 L36 "Voice licence: A. Keep the Kokoro voices and accept GPL-3.0 espeak-ng data in the voice path for personal use". Stale residue L35 → T-m11 |
| F1-B2 visual voicemail | RESOLVED | phase-06 L30 "Visual voicemail posture: B. An experiment through the phase 04 privileged helper"; PLAN L200 R4 extended; PLAN A11 L46-47. Residue (silent-fallback edge, stale lines) → T-M22 |
| F1-B3 thresholds "(interview)" | RESOLVED | phase-01 L74 "a tile's content updates ≤ 2 s after its notification is posted (E5) … janky frames ≤ 5 % and 99th-percentile frame time ≤ one vsync period". New problem with P4's numbers → T-M5 |
| F1-M1 drawn bars | RESOLVED | phase-01 task 16 L118; E19 L162; P7 L172; L75 "The Search key is not drawn in this phase". E19's reference to tagged bounds is broken → T-M4 |
| F1-M2 press tilt in Goal | RESOLVED | phase-01 L14 "Press feedback on Start tiles follows the Q6 setting (default: none)"; PLAN L109 "press feedback (Q6 setting)" |
| F1-M3 Live Tile API security | RESOLVED | phase-01 L77 (identity, authority check, caps, 60 verbs/min, kill switch, DTDs disabled, adversarial review); edges L201 |
| F1-M4 build types | RESOLVED | phase-01 L78, task 17 L119, P8 L173; phase-09 L35 "any host paired for adb (USB or Wireless debugging)" |
| F1-M5 secondary tiles | RESOLVED | phase-02 L27, E5 L57, edges L72. E5's observable does not show the arguments → T-M8 |
| F1-M6 Cortana while locked | RESOLVED in text | phase-03 L44 "Locked phone: A."; edge L117; P2 L99; E9 L83. E9's command cannot work → T-B1 |
| F1-M7 glance activation | RESOLVED | phase-07 L22 (showWhenLocked + turnScreenOn under the SYSTEM_ALERT_WINDOW exemption); edges L54-57; P3 L46 |
| F1-M8 LLM process isolation | RESOLVED | phase-08 L26 (`app.tileshell:llm`); edge L52; P2 L47 measures both processes. P2's launcher bound is undefined → T-M17 |
| F1-M9 replay determinism | RESOLVED in text | phase-09 L37 greedy decoding; E6 L63. The edge L76 declares the failure "impossible" instead of testing it → T-M19 |
| F1-M10 helper trust | RESOLVED | phase-04 L26 and edges L64. The helper-vs-phase-09-provider edge cannot run in phase 04 → T-M21 |
| F1-M11 voice write path | RESOLVED in text | phase-09 L36, edge L75, E7 L64. E7 passes vacuously → T-M18 |
| F1-M12 per-value approximations | PARTLY RESOLVED | Phase 01 X1-X12 (L124-137) with H9-H20, phase 07 H4/H5 (L47): done. **Phase 04 not applied:** L60 "H3 accept approximations" is still generic and E4 L52 "Motion within (R3) tolerance" cites motion R3 never measured → T-B5. Phase 01 residue (list-row press, LOW, no H-row) → T-m15 |
| F2-B1 "Recently added" | RESOLVED | phase-01 L15, L31, L81, E12 L155; PLAN L59 "New caption on newly installed apps (R3 A13)" |
| F2-B2 edit-mode values | RESOLVED | phase-02 L26-37 (R6 §1 tagged per value), E7 L59, H3-H19 L63 |
| F2-B3 keyboard values | RESOLVED | phase-05 L24-34 (R6 §2), E3 L56, H3-H9 L65. Residue: show/hide feasibility has no failure path → T-M12 |
| F2-M1 Goal vs Q6 | RESOLVED | phase-01 L14, L76 |
| F2-M2 R3 LOW/UNMEASURED cited as tolerances | RESOLVED | phase-01 Approximations L121-137; E9b L152. New E10 problems → T-M2, T-M3 |
| F2-M3 waveform | RESOLVED in text | phase-03 L45-46, E4 L78. The listening-pulse period now contradicts R6 → T-B4 |
| F2-M4 glance E3 | RESOLVED | phase-07 L23, E3 L43, H4/H5 L47. Inset tolerance still missing → T-m9 |
| F2-M5 thresholds never set | PARTLY RESOLVED | phase-01 L74, phase-07 L24 + P1 L46, phase-08 L27: set. **Phase 09 not resolved:** P2 L66 "replay run time and memory within the phase 08 thresholds": phase 08 has no replay-run-time threshold → T-M17 |
| F2-M6 Cortana tile | RESOLVED | phase-03 L38, task 10 L70, E6 L80, H7 L101 |
| F2-M7 confirmation flow in phase 03 | RESOLVED | phase-03 L39; phase-08 L28 "Phase 08 calls those same actions and adds no confirmation of its own". New contradiction with R6 §3.4.2 → T-M9 |
| F2-M8 E18 profiles | RESOLVED | phase-01 E18 L161 (create → start-user → install --user; quiet mode via the shell; private-space case) |
| F2-M9 selection indices | RESOLVED | phase-05 L25 fixture, E2 L55, E6 L59 |
| F2-M10 glide injection | RESOLVED | phase-05 E4 L57 `UiDevice.swipe(Point[], steps)` |
| F2-M11 emergency dialing | RESOLVED | phase-06 E6 L54; P3 L56 classification only |
| F2-M12 glance vs secure lock | RESOLVED | phase-07 E4 L44, E5 L45, edges L56 |
| F2-M13 hook editing | RESOLVED | phase-09 L21, L35, E7 L64 |
| F2-M14 runtime unnamed | RESOLVED | phase-08 L27 llama.cpp + candidates + thresholds; L5 `depends-on: [03, 04]` |
| F2-M15 phase 06 edges | RESOLVED | phase-06 L63-65; P1/P2 L56 include the locked case |
| F2-M16 audio route | RESOLVED | phase-03 L37 (null-sink route, no test-only text path); E5 L79. Residue → T-m7, T-m16 |

**Unresolved round-1 IDs: F1-M12 (phase 04 part), F2-M5 (phase 09 P2).**

---

## 2. BLOCKING

### T-B1 — Phase 03 — E8/E9 long-press Home via `input keyevent --longpress KEYCODE_HOME` does nothing on the AOSP emulator, and is ignored while the keyguard shows
- **Claim:** phase-03 E8 L82 "`adb shell input keyevent --longpress KEYCODE_HOME` opens Cortana"; E9 L83 "with a PIN set (`adb shell locksettings set-pin 1234`) and the screen locked, `adb shell input keyevent --longpress KEYCODE_HOME` opens Cortana above the keyguard while the toggle is On".
- **Evidence (AOSP source):** the syntax is valid (InputShellCommand: `keyevent [--longpress|--duration …]`, it injects DOWN, then a repeat with `FLAG_LONG_PRESS` after the long-press timeout). But the long press is handled only by PhoneWindowManager `DisplayHomeButtonHandler.handleHomeButton`: `} else if ((event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0) { if (!keyguardOn) { mHandler.post(() -> handleLongPressOnHome(event)); } }`, and `handleLongPressOnHome` returns at once when `mLongPressOnHomeBehavior == LONG_PRESS_HOME_NOTHING`. AOSP `core/res/res/values/config.xml`: `<integer name="config_longPressOnHomeBehavior">0</integer>` ("0 - Nothing"); the goldfish phone overlay does not override it. The key is consumed (`return true`), so the shell as HOME never receives it either. On a real phone, 3-button long-press Home goes through SystemUI's nav-bar button, not this key path.
- **Why blocking:** E8's third clause and all of E9 fail for a correct build, and E9 is the only emulator test of the PQ3 ruling and the R6 §3.5 toggle.
- **Fix:** E8: replace with `adb shell input keyevent KEYCODE_ASSIST` (PhoneWindowManager `interceptKeyBeforeQueueing` → `MSG_LAUNCH_ASSIST` → `launchAssistAction`, which has no keyguard check; checked in source). E9: wake the screen first (`input keyevent KEYCODE_SLEEP`, `input keyevent KEYCODE_WAKEUP`, confirm the keyguard shows in `dumpsys window`), then `adb shell input keyevent KEYCODE_ASSIST`; if SystemUI's AssistManager declines over the keyguard (not certain), use `adb shell cmd voiceinteraction show` (a real VoiceInteractionManagerService shell verb, confirmed in source). Both are real system entry points, not test-only paths. Keep side-key long-press on the phone (P2).

### T-B2 — Phase 01 — E20 "Back on Start" cannot pass with Settings as the test app, and its reboot branch is ambiguous
- **Claim:** phase-01 E20 L163 "open an app on an inner page (`adb shell am start -a android.settings.WIFI_SETTINGS`) and note its task id … tap the drawn Back key … `dumpsys activity activities` shows that app resumed on the same page in the same task"; the mechanism, L83: "the shell brings that app back with `LauncherApps.startMainActivity`, which brings its existing task to the front, so the app shows the page it was left on".
- **Evidence (AOSP Settings AndroidManifest.xml, main):** the Wi-Fi page is `Settings$WifiSettingsActivity` with no `taskAffinity` (so affinity `com.android.settings`). The launcher entry that `LauncherApps.startMainActivity` must target is the alias `<activity-alias android:name="Settings" … android:taskAffinity="com.android.settings.root" … android:targetActivity=".homepage.SettingsHomepageActivity">`. Starting the launcher entry therefore goes to the `com.android.settings.root` task and shows the Settings home page. It does not bring back the Wi-Fi task. LauncherApps only starts MAIN/LAUNCHER components, so the shell cannot target the Wi-Fi activity. Reboot branch: L137 X12 "no other activity resumed since boot", but L84 "Back history resets when the screen locks", and a rebooted emulator passes through a keyguard, so the row cannot tell which rule produced the result. On FBE images Settings' `FallbackHome` is resumed during direct boot. UsageStats may then report it as the last resumed activity (likely, not certain).
- **Why blocking:** a correct implementation of L83 fails E20 as written. The row also hides a real limit of L83's mechanism: an app whose inner page sits in a task with a different affinity than its launcher entry comes back on its home page.
- **Fix:** (1) E20 main case uses a fixture APK, or AOSP DeskClock, opened from its launcher entry (`am start -n <pkg>/<launcher activity>`), then moved to an inner activity or tab with `adb shell input tap`, then Home, then Back → same task and same activity (`dumpsys activity activities`). (2) Add an edge with a defined outcome: an app whose page lives in a different-affinity task (the Settings deep link) comes back on its launcher page. Tag it as an approximation of R6 §4.1.1 with its own H-row. (3) In L83, filter the Usage events to packages that have a launcher activity, excluding HOME-category and `FallbackHome` activities. (4) Rebuild the reboot branch: `adb reboot`, `adb wait-for-device`, poll `getprop sys.boot_completed`, dismiss the keyguard (`adb shell wm dismiss-keyguard`), then Back. Define whether the boot keyguard counts as "locked" (see T-m6). (5) Also press `adb shell input keyevent KEYCODE_BACK` with the Samsung bar revealed, since L83 says the system Back does the same.

### T-B3 — Phase 01 — E10 "no two tiles flip on the same frame" fails a correct random scheduler most of the time
- **Claim:** E10 L153 "over N ≥ 20 recorded intervals every interval lies within 4.4–5.0 s and the phase offsets differ across tiles (no two tiles flip on the same frame throughout the capture)".
- **Evidence:** R3 A8 (w10m-measurements.md L124) found independent per-tile timers: "The tiles are not synchronised with each other (different periods and phases) … each tile runs its own timer". Independent timers do collide. For two tiles with period T over a capture of D seconds at 60 fps, expected same-frame coincidences ≈ D / (60·T²). With D = 100 s (20 intervals) and T ≈ 4.7 s that is 0.075 per pair. With 10 live tiles (45 pairs) that is ≈3.4 expected collisions, so P(no collision) ≈ e^-3.4 ≈ 3 %. With 6 live tiles, P ≈ 32 %.
- **Why blocking:** QA fails a faithful implementation roughly 70-97 % of the time; the "fix" a builder would reach for (a global de-collision scheduler) contradicts R3 A8.
- **Fix:** replace the clause with a statistical check: per-tile period estimates differ, and the phase differences between tiles are spread across the period rather than clustered near zero. For example, across all tile pairs the fraction of flips within one frame of another tile's flip is ≤ 2 × the expected D/(60·T²) rate. Or simply assert that each tile's intervals come from its own timer (per-tile interval sequences are not identical).

### T-B4 — Phase 03 — E4 measures the listening pulse against R3 A22 (600 ms, one cycle) while R6 §3.1.8 measured 1.04 s over seven half-cycles
- **Claim:** phase-03 L45 "the listening state keeps the pulsing large persona (R3 A22)"; E4 L78 "Ring states (idle / thinking ring pop-in, Y-axis rotation, listening pulse, move to the top of the page) measure within R3 A22's tolerance".
- **Evidence:** w10m-measurements.md L246 "S1 (14393) listening: outer diameter pulses 82→88→82 px over 36 frames = **600 ms** (one cycle captured)". r6-measurements.md L353 "3.1.8 | Listening pulse period and shape | **1.04 ± 0.02 s** (7 half-cycles, mid-level crossings)" from the same S1 recording; R6 §0.3 "the large persona pulses (1.04-s period) while listening". R6 §3.1.6-3.1.10 also give the form (filled disc + halo, antiphase), sizes (halo 85.8→94.7 epx, disc 41.1→37.3 epx), position (243.8 epx) and the 333 ms entrance, none of which phase 03 carries.
- **Why blocking:** a build that follows R6 (the better measurement) fails E4; a build that follows A22 is off by 73 %. RV9 needs one tagged value.
- **Fix:** in L45, cite R6 §3.1.6-3.1.10 for the listening persona (period 1.04 ± 0.02 s, sizes, antiphase, centre y, entrance 333 ± 17 ms), noting that R6 supersedes A22's single-cycle 600 ms. In E4, measure "listening pulse" against R6 §3.1.8 and keep A22 for pop-in, rotation and move-to-top.

### T-B5 — Phases 06, 09 (and 04) — W10M UIs with no tagged values and no per-element approximation rows (RV9)
- **Claim:** phase-06 L11 "W10M (final release) Phone and Messaging … with the W10M in-call UI and call history"; phase-09 L21 "W10M-style Notebook UI (Cortana's Notebook from W10M, extended per P4)"; phase-04 E4 L52 "Motion within (R3) tolerance from screenrecord frames".
- **Evidence:** PLAN L174-175 (RV9): "Phase docs go FINAL only with every value tagged … or 'approximation' with a NEEDS-HUMAN row". Phase 06 has no value, no Approximations list and NEEDS-HUMAN L58 "H1 feel; H2 call audio routing" only. No research file covers the dialer, in-call UI, call history, threads, compose or MMS UI: R1's sections are §1-§11 with none for them, and R6 targets §1-§6. Phase 09 has H1 L68 only, although P4 designs need "a NEEDS-HUMAN look row" each (PLAN L76-77). Phase 04: R3 A19/A20 (L195-224) are geometry only and §E lists no action-center or volume motion, so E4 cites values that do not exist (the same shape as round-1 F2-B2/B3).
- **Why blocking:** under RV9 phases 06 and 09 cannot go FINAL; phase 04's E4 cannot be measured (due before 04's FINAL, which is gated on R4 anyway).
- **Fix:** run an R-pass for the W10M Phone app (dial pad, incoming/in-call, call history, voicemail entry), Messaging (thread list, thread, compose, attachment picker) and the Cortana Notebook, the way R6 was run for phases 02/05. Then tag each value or list it as an approximation with its own H-row, and mark P4 designs "P4 design" with a look row. Phase 04: tag A19/A20 geometry into Decisions, and turn E4 into "motion values from an R-pass, or approximations with H-rows".

---

## 3. MAJOR

### T-M1 — Phases 01/02/03/05 — motion tolerances at or below what a 60 fps emulator screenrecord can resolve; touch-down is not in the recording
- **Claim:** phase-02 E7 L59 "hold 783 ± 33 ms … exit 150 ± 17 ms latency"; phase-05 L28 "appears at full size 17–50 ms after touch-down"; phase-03 L45 "every 128 ± 15 ms", L46 "every 51 ± 17 ms"; phase-01 E10 L153 "within the (R3) tolerance of each value" (R3 A7 flip "108 ± 17 ms").
- **Evidence:** R6 §0.1: "Timing tolerance is ±1 frame at the source fps", which is the source's measurement error, not an acceptance band. A 60 fps capture adds ±1 frame (16.7 ms) at each end. `screenrecord` shows no touch unless `settings put system show_touches 1`, and even then the dot lands on the next composed frame. Emulator frame pacing (software or host GPU) is not the phone's. screenrecord.cpp: `kMaxTimeLimitSec = 180`, and it falls back to 1280x720 when the encoder rejects the native size.
- **Fix:** state one rule once (for example in PLAN RV9 or each phase's acceptance header): pass = |measured − value| ≤ source tolerance + one capture frame. Touch-referenced timings need `show_touches 1`. Measure hold thresholds by bracketing with `adb shell input swipe x y x y <ms>`, one process with exact sleeps (InputShellCommand `runSwipe`): 740 ms → no edit mode, 830 ms → edit mode. Record fps with `ffprobe` and reject captures below 55 fps. Add phone P-rows (120 Hz capture) for timings whose tolerance is ≤ 17 ms.

### T-M2 — Phase 01 — the 4.4–5.0 s flip band is not R3's data (untagged synthesis)
- **Claim:** L24 "unsynchronised ~4.4-5.0 s periods"; E10 L153 "every interval lies within 4.4–5.0 s".
- **Evidence:** R3 A8 L124: Cortana tile intervals "4.91, 4.75, 4.94, 4.90, 5.39, 4.88 → **period 4.96 ± 0.22 s**", Gameloft Hub "**4.4 ± 0.4 s**", Store "irregular (2.4–5.5 s)". A W10M-faithful 4.96 ± 0.22 s tile produces intervals above 5.0 s, and R3 itself saw 5.39 s.
- **Fix:** tag per-tile period rules from A8 (flip tiles 4.96 ± 0.22 s; image-cycle tiles 4.4 ± 0.4 s; MEDIUM, one recording) and make E10 check each tile's intervals against its own rule, with the T-M1 capture allowance.

### T-M3 — Phase 01 — E10 measures the app-list swipe and the press styles against R3 values that do not exist
- **Claim:** E10 L153 "captures of press feedback (per Q6), tile flip / peek / crossfade, Start exit / return and the app list swipe measure … within the (R3) tolerance of each value".
- **Evidence:** R3 has no Start ↔ app-list swipe row: its only mention is L103, "their Start 'scroll' events were Start↔app-list swipes". The press styles are "none" (A10), WP8 tilt from R1 §3.1 legacy candidates (phase-01 L67) and the P4 press (agent design, H8). None of them has an R3 tolerance.
- **Fix:** add X13 "app-list pivot swipe motion" (R3 has S1/S2 footage of it; measure it or tag the approximation) with an H-row. Rewrite E10's press clause: tilt style against R1 §3.1 numbers (candidates, H8), P4 press against its Decisions numbers (H8), none = zero pixel change (A10).

### T-M4 — Phase 01 — E19 checks "bounds tagged in Decisions" that were never tagged; the dump observable and auto-hide timing are undefined
- **Claim:** E19 L162 "the drawn W10M status bar and Back / Windows nav bar have the bounds tagged in Decisions and Approximations (X6) in the dump"; "`adb shell dumpsys window` shows no visible system-bar windows over Start"; "they auto-hide".
- **Evidence:** Decisions L75 name the bars and their feeds but no status-bar height, glyph positions, clock position or nav-key positions. R3 C4 L335 has "Status bar: 28 epx (S1 112 phys) / 29 epx (S2 103 phys)", and it is not carried. With inset-hidden bars (WindowInsetsController), SystemUI's bar windows still exist; their leashes are hidden. So "no visible system-bar windows" is probably not what `dumpsys window` prints. Not certain of the exact dump text; the insets-state source visibility is the likelier observable. There is no auto-hide timeout. Phase 01 draws Back and Windows only, and phase 03 L42 ADDs Search. Where the two keys sit in phase 01 (halves, or thirds with an empty slot) is untagged, and either choice changes when phase 03 ADDs Search (Rule 16).
- **Fix:** tag status-bar height (C4, 28-29 epx, S1/S2), the status glyph layout (measure it, or approximation + H-row), and nav-key positions for both the 2-key and 3-key states (approximation + H-row). E19 asserts the statusBars/navigationBars insets sources are not visible in `dumpsys window` plus screencap pixels of the drawn bars. The swipe is `adb shell input swipe 540 0 540 400`; the auto-hide time is agent-set and tagged.

### T-M5 — Phase 01 — P4 frame thresholds are inconsistent and unmeasurable as written
- **Claim:** L74 / P4 L169 "janky frames ≤ 5 % and 99th-percentile frame time ≤ one vsync period at the device's current refresh rate (`dumpsys display` for the rate)".
- **Evidence:** if the 99th percentile is ≤ one vsync, then ≥ 99 % of frames meet the deadline, which makes "janky ≤ 5 %" dead. gfxinfo reports percentiles in whole-ms histogram buckets, so at 120 Hz (8.33 ms) a reported "99th percentile: 9ms" fails even for 8.4 ms frames. The S25U's adaptive refresh means a single `dumpsys display` read does not give the rate during the scroll. gfxinfo accumulates since process start unless reset. "Scripted Start scroll" is not defined.
- **Fix:** pin the refresh rate for the run (read the active mode during the run), run `adb shell dumpsys gfxinfo app.tileshell reset` first, and define the script (e.g., 20 × `input swipe` at fixed coordinates/duration with tiles flipping). Choose consistent bounds, e.g., janky ≤ 5 % and 99th percentile ≤ 2 vsync periods rounded up to whole ms. Agent call; record the reason.

### T-M6 — Phases 01/09 — acceptance rows leave device state behind that breaks later rows
- **Evidence:** phase-01 E9 L151 enables airplane mode and nothing disables it, yet E9b L152 needs "the provider's response". Phase-01 E12 L155 runs `adb root`, so adbd stays root. E16 L159 then asserts rejection "from the shell uid", but calls now come from uid 0. Phase-01 E3 L144 changes `wm size`, `wm density` and `font_scale` with no reset. Phase-09 E3 L60 runs `adb root`, and after it E1/E6's PC script and E5's "only the adb shell uid" provider see uid 0 callers: they falsely reject, or tempt the builder to accept root and weaken the gate. Phase-09 E7 L64 sets a PIN (`locksettings set-pin`) with no value given and no `clear`.
- **Fix:** each state-changing row ends with its restore: `cmd connectivity airplane-mode disable`; `adb unroot` + `adb wait-for-device`; `settings put global auto_time 1`; `wm size reset`, `wm density reset`, `settings put system font_scale 1.0`; `locksettings clear --old 1234`. Add one line under each Acceptance header: "rows start from the baseline state; a row that changes state restores it".

### T-M7 — Phases 01/03/06/08/09 — the "diagnostics buffer/surface" is the observable for many rows, but no build task creates it and no row says how QA reads it
- **Evidence:** phase-01 L77 "diagnostics ring buffer instead of logcat"; E5 L147 "listener timestamp vs the tile's render timestamp in the diagnostics buffer"; phase-03 E3 L77 "logged to the diagnostics surface", E7 L81 "reply text from the diagnostics surface"; phase-06 P3 L56 "the shell's diagnostics surface shows `TelephonyManager.isEmergencyNumber`"; phase-08 E0 L45 "byte offset in the diagnostics buffer"; phase-09 L36 "the attempt is logged in diagnostics". Phase 01 build tasks 1-17 (L103-119) contain no diagnostics task. The phone runs a non-debuggable build (L78), so `run-as` cannot read files.
- **Fix:** add a phase-01 build task for the diagnostics buffer plus one read path that works on a release build: a Settings > Diagnostics page read with `uiautomator dump`, and/or `adb shell dumpsys activity service app.tileshell/<Service>` through `Service.dump()`. Name that command in E5, and have later phases ADD entries to it.

### T-M8 — Phase 02 — E5 cannot see the secondary tile's arguments in `dumpsys activity`
- **Claim:** E5 L57 "a tile whose tap launches the owner with the request's `arguments` (extras in `dumpsys activity`)".
- **Evidence:** ActivityRecord dump prints `intent.toInsecureString()`, which calls `toShortString(b, false, true, true, false)`; in Intent.java the extras branch appends only `"(has extras)"`. The values are never printed.
- **Fix:** the E5 test APK shows the arguments it received in a TextView, the same fixture pattern as phase-05 L25, and E5 reads them from `uiautomator dump`.

### T-M9 — Phase 03 — "add more" on calendar/reminder cards contradicts R6 §3.4.2; the reminder card values are not carried; the calendar and delete cards are untagged
- **Claim:** L39 "adding or deleting a calendar event or reminder shows a confirmation card; the card's actions are confirm, cancel and 'add more'".
- **Evidence:** r6-measurements.md L408 (3.4.2, MEDIUM, 14393 screen recording): the reminder card title is "Remind you about this?", the buttons are **"Remind" / "Cancel"**, the callout reads "you can say Yes, No, or Cancel", and it gives field geometry (43 epx fields at 53.6 epx pitch; 32.2 epx buttons, 164.6/160.0 epx wide, 3.8 epx gap) and speech ("Sound good?"). "Add more" occurs only in the text read-back (3.4.1). R6 has no calendar-event card and no delete confirmation.
- **Fix:** reminder card = R6 §3.4.2 values, tagged. Calendar-event and delete confirmations = approximations patterned on 3.4.2, each with its own H-row. "Add more" only for texts. Edge L110 and E7 L81 follow.

### T-M10 — Phases 03/06 — how Cortana sends a text or places a call is inconsistent across docs, and its permissions are missing
- **Evidence:** phase-03 L31 "calls and texts … go to the current default dialer / SMS app". E7 L81 "'send it' sends it (`content query --uri content://sms/sent`) and replies 'Message sent.'" and "`dumpsys telecom` shows the call only after confirm", i.e., Cortana itself sends and places. Phase-03 checklist rows L18 "(assistant role, microphone, contacts, calendar write, exact alarms, model presence, service liveness)" have no SMS-send or call permission. Phase-06 E5 L53 "A Cortana 'call' / 'text' command opens the shell's Phone / Messaging". PLAN RV4 L167 "Cortana commands act through real Android intents". An intent to the SMS app (`ACTION_SENDTO`) opens a compose screen and does not send; placing a call without UI needs `CALL_PHONE`; sending needs `SEND_SMS`.
- **Fix:** Cortana's action layer sends via `SmsManager` (`SEND_SMS`) and places calls via `ACTION_CALL` / `TelecomManager.placeCall` (`CALL_PHONE`), which matches R6 3.4.1's "Message sent." and the phase-08 Q2 ruling. Add both checklist rows to phase 03. Rewrite phase-06 E5: the call shows in the shell's in-call UI and the sent text appears in the shell's Messaging thread. Amend RV4's wording for these two actions.

### T-M11 — Phase 03 — R6 §3.3 (Cortana text box), §3.1.14 and §3.5.5 are not carried; §3.3.8 UNMEASURED has no approximation; "Unlock to continue" has no source or H-row
- **Evidence:** task 9 L69 and E5 L79 build and test the text box, but phase 03 cites none of r6-measurements.md L378-L387: 3.3.1 bar 48 epx (HIGH); 3.3.2 docked on the nav bar (HIGH); 3.3.3 fill (HIGH); 3.3.4 mic button 48 × 48 epx accent (HIGH); 3.3.5 placeholder "Ask me anything" vs "Type here to search" (MEDIUM, conflicting); 3.3.6 12 ± 1 epx inset (HIGH); 3.3.7 other bar states; 3.3.9 voice-hint callout. L385 "3.3.8 | Typing into the box with the keyboard up | UNMEASURED". L359 3.1.14: the 15063 page background is black (0,0,0). L421 3.5.5: the locked Cortana is a black page with no ≡ menu and the greeting "What's on your mind?" (LOW). The PQ3 "Unlock to continue" card (L44) has no source and no H-row.
- **Fix:** add a Decisions line for the text box from R6 §3.3, with a placeholder choice and reason. Add approximations with H-rows for typing with the keyboard up (3.3.8) and the "Unlock to continue" card. Tag page background and locked look (3.1.14, 3.5.5). E5 also measures the box.

### T-M12 — Phase 05 — keyboard show/hide motion depends on an UNVERIFIED capability, with no failure path and no H-row
- **Claim:** L32 "Whether the IME can own this motion is checked at build start, since Android animates the IME window's insets itself (UNVERIFIED)"; E3 L56 measures "show / hide from screenrecord frames".
- **Why:** if the system's IME inset animation cannot be replaced, E3 fails with no ruled alternative. Phase 01 handles the equivalent case with H2/H3 ("Accept the system return-to-Start animation").
- **Fix:** add the failure branch now: if the IME cannot own the slide, the system IME animation is used and accepted in a new H-row, and E3's show/hide clause becomes conditional on the build-start result, which is recorded in Decisions.

### T-M13 — Phase 07 — the mode list and default contradict R6 §6.1.6 (15063 Glance settings); the promised default-mode H-row does not exist; §6.1.5 is not carried
- **Claim:** L20 "every W10M mode selectable in Settings (always on / 15 min / 30 s after leaving a pocket via proximity / off)"; L21 "Default mode … W10M's own default if R1/R3 sources show it at build start; otherwise 30 s pocket mode as a labelled approximation with a NEEDS-HUMAN row"; E5 L45 "30 s pocket mode shows glance for 30 s".
- **Evidence:** the mode list is R1 L304, 10586-era ("modes off / 30 s (proximity) / 15 min / always on"). r6-measurements.md L560 (6.1.6, **15063**, MEDIUM) shows the governing build's page instead: toggle "Show Glance screen when your screen is turned off", dropdown "Show Glance screen for" ("5 minutes"), toggle "Always show Glance screen when charging", toggle "Show lock screen background picture", and a "Night mode" time range. The NEEDS-HUMAN list L47 (H1-H5) has no default-mode row. L559 (6.1.5, LOW) gives the date line at 497 ± 10 epx, the notification row at 525-549 epx and the "h:mm" clock format, none of which phase 07 carries.
- **Fix (agent, Q12 + the "every W10M mode" ruling):** rebuild the settings from R6 §6.1.6. The dropdown's option list is unmeasured, so it is an approximation with an H-row. Add charging always-on, background picture and night mode. Set the default now (approximation + H-row) and retarget E5 to the ruled options. Tag the §6.1.5 date/notification layout (LOW, H-row) and the "h:mm" format.

### T-M14 — Phase 07 — P1 battery drain would be measured while charging over USB
- **Claim:** P1 L46 "the 8 h screen-off baseline with glance off, then each mode's added drain (`adb shell dumpsys batterystats`); always-on ≤ 2 % per hour".
- **Why:** adb over USB charges the phone, so drain readings are meaningless. `dumpsys battery unplug` only fakes the state for stats; the device still charges.
- **Fix:** `adb shell dumpsys batterystats --reset`, unplug the cable (or use Wireless debugging with the charger off), run the window, reconnect, then read the `dumpsys batterystats` discharge since reset (and `dumpsys battery` level before and after). The same procedure for each mode.

### T-M15 — Phases 05/08 — "network off" is still unproven or ambiguous outside phase 01
- **Evidence:** phase-08 E0 L45 "`adb shell svc wifi disable` mid-download pauses it and `svc wifi enable` resumes it". The emulator also has emulated cellular data, so traffic fails over to mobile data, and whether the download then pauses depends on a metered-data policy that edge L51 lists ("on metered data") but never defines. Phase-08 E1 L46 and phase-05 E8 L61 say "with the network off" with no command. Phase 01 E9 L151 has the proven form.
- **Fix:** define the metered policy in phase 08 Decisions. E0 uses `cmd connectivity airplane-mode enable` (or `svc wifi disable` + `svc data disable`) for the pause, then disable/enable to resume. E1 and 05 E8 reuse phase 01 E9's command + `dumpsys connectivity` proof.

### T-M16 — Phase 08 — the emulator AVD cannot host E0/E1 at default size
- **Evidence:** L26 "the chosen GGUF is roughly 2–2.5 GB … the shell refuses to start the download unless free storage is at least twice the model size". Phase-01 L21 defines the AVD only as "AOSP … 1080x2340 @ 450 dpi". A default AVD's data partition (a few GB) leaves < 5 GB free after boot. The default AVD RAM (≈2 GB) cannot map a 2.5 GB model plus KV cache in `app.tileshell:llm`.
- **Fix:** record the AVD hardware in phase 01 L21 or phase 08 Decisions (e.g., `hw.ramSize` ≥ 8192, `disk.dataPartition.size` ≥ 16G) and capture it as QA evidence (`emulator -list-avds` + config.ini). Otherwise E0/E1 fail on storage or LMK for a correct build.

### T-M17 — Phases 08/09 — bounds that point at figures nobody records
- **Evidence:** phase-08 P2 L47 "the launcher process unchanged from phase 01's figure": phase 01 has no meminfo row, figure or tolerance. Phase-09 P2 L66 "replay run time and memory within the phase 08 thresholds": phase 08 L27 thresholds are per request (first word ≤ 2.5 s, ≥ 10 tok/s, PSS ≤ 4 GB), with nothing for a replay run (round-1 F2-M5 residue).
- **Fix:** phase 01 adds a P-row capturing `dumpsys meminfo app.tileshell` PSS after the scripted scroll (baseline). Phase 08 P2 bound = baseline + stated tolerance (e.g., ≤ +50 MB). Phase 09 P2 = replay per test ≤ phase 08's per-request thresholds, and whole-run time ≤ N × per-test bound, with the runtime process PSS ≤ 4 GB.

### T-M18 — Phase 09 — E7's locked voice-correction check passes vacuously
- **Claim:** E7 L64 "a voice correction attempted with the keyguard locked (`locksettings set-pin` + `KEYCODE_SLEEP`) stores nothing".
- **Why:** after `KEYCODE_SLEEP` the screen is off and Cortana is not open, so the spoken correction reaches nothing and "stores nothing" is true for any build. L36 promises "the attempt is logged in diagnostics", but E7 does not assert it.
- **Fix:** `locksettings set-pin 1234` → `KEYCODE_SLEEP` → `KEYCODE_WAKEUP` → keyguard showing → Cortana opened over it (T-B1 route, "Lock screen options" On) → play the correction WAV through the phase-03 route → assert that no memory or rule file changed (PC-script diff) **and** a diagnostics entry for the refused attempt exists (T-M7) → `locksettings clear --old 1234`. Add the declined-card variant while unlocked.

### T-M19 — Phase 09 — the determinism edge case is asserted "impossible" instead of tested
- **Claim:** edge L76 "Same utterance, different output: impossible under greedy decoding with the same model, prompt and memory".
- **Why:** greedy decoding in llama.cpp is not guaranteed bit-identical across thread counts, batch sizes or GPU/NPU backends. The prompt also carries dynamic context (clock, the "after 22:00" rules in E3). Written as "impossible", QA has nothing to run (Rule 5).
- **Fix:** replace with a test: replay the same stored correction 5 times in one run and across a process restart, with thread count and batch size pinned in Decisions and dynamic context captured in the test record, and require identical action + parameters. A mismatch is a harness defect.

### T-M20 — Phase 09 — E6's "swapping the prompt template" has no real-system path
- **Claim:** E6 L63 "After swapping the prompt template, the replay runner re-runs every stored correction".
- **Evidence:** the prompt template is phase 08's and is not among phase 09's PC-editable files (L25 "memory, rules and hooks"). No Settings or build path swaps it. The likely shortcut is a debug hook, which Rule 4 forbids.
- **Fix:** name the path. Either the template is a PC-editable harness file pushed with the PC script (add it to L25 Scope and the provider), or E6 triggers replay through a real change it already supports (a model re-download from Settings, or a rules/hooks push).

### T-M21 — Phases 04/09 — "the phase 09 provider rejects the helper's process" is required in 04, cannot be tested there, and is missing from 09
- **Evidence:** phase-04 L26 "the helper's shell uid would pass phase 09's adb-shell-uid provider check, so that provider must additionally reject the helper's own process. R4's spike proves all three"; edge L64 "the helper's process calling phase 09's adb-only provider (refused …)". Phase 09 is built after 04, so the provider does not exist during 04's QA. Phase-09 L25 "a provider that only accepts calls from the adb shell uid" and E5 L62 "rejects … any app other than the adb shell uid" never mention the helper.
- **Fix:** move the requirement into phase 09 Scope + a Decision naming how the provider tells the helper's process from an `adb shell` caller (both uid 2000; e.g., the calling pid equals the helper pid the app launched). Add an E-row to 09 (the helper attempts a provider call → refused → diagnostics entry). Phase 04's edge becomes "the helper's verb allow-list contains no content-provider call".

### T-M22 — Phase 06 — the voicemail edge encodes the silent fallback the PQ2 ruling forbids; stale "open" text remains
- **Evidence:** L30 (PQ2 ruled B) "If it fails: Jeremy is re-asked before this phase is built; no silent fallback". Edge L69 "Voicemail: carrier VVM not reachable (button calls voicemail)". L24's decision A still states the VisualVoicemailService premise. L26 "Posture = PQ2 (open owner question, not ruled here)". P4 L56 "(not run until PQ2 is ruled)".
- **Fix:** edge L69 → "VVM stops working after a successful probe (carrier change, auth failure): the list shows a W10M-style error and the Voicemail button still dials voicemail". The failed-probe case stays a re-ask. Strike L24's premise (superseded by L26/L30), change L26 "open owner question" to "ruled B (L30)", and drop "(not run until PQ2 is ruled)" from P4.

---

## 4. MINOR

- **T-m1 — Phase 03 E2 Doze observable cannot run as written.** L91 "still fires under Doze (`dumpsys deviceidle force-idle`)". DeviceIdleController: `if (!mDeepEnabled) { pw.println("Unable to go deep idle; not enabled"); return -1; }`, and AOSP `config_enableAutoPowerModes` is `false`. Fix: `adb shell dumpsys deviceidle enable deep`, `adb shell dumpsys battery unplug`, `input keyevent KEYCODE_SLEEP`, then `force-idle`; restore with `dumpsys battery reset` and `dumpsys deviceidle unforce`.
- **T-m2 — `uiautomator dump` needs 1 s of accessibility idle.** DumpCommand.java: `uiAutomation.waitForIdle(1000, 1000 * 10)` → `"ERROR: could not get idle state."` with no dump. Rows that dump during continuous change can fail intermittently: phase-03 E8 "Listening..." box (text updates + waveform), phase-01 E5/E12 with tiles flipping content, phase-07 E3/E4. Fix: state a retry rule (≤ 3 tries), or read via an instrumentation `UiDevice` with `Configurator.setWaitForIdleTimeout(0)`.
- **T-m3 — Phase 01 "New" caption rule gaps.** X11 L136 covers first launch only. Undefined: which apps show "New" when the shell first becomes Home (all installed? none?); pinning (R6 L513 5.2.2 notes the pinned app had no caption); launch from outside the shell while Usage access is revoked; whether any time-based clearing exists at all (L82 says only "does not clear on a short timer"). E12 checks presence only, although R6 §5.1.3-5.1.5 (L504) give cap 8.3 ± 1.4 epx, x = 57 epx, name shift −7 ± 1.4 epx, baseline pitch 18 ± 1.4 epx, row pitch unchanged. Fix: state the rules (approximation, H19 extended) and measure the §5.1 geometry in E12.
- **T-m4 — Phase 01 fixture check uses the wrong filter for Camera.** L80 `cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.APP_<X>`, but L56 resolves the camera slot by "still-image camera" (`android.media.action.STILL_IMAGE_CAMERA`), which is not an `APP_*` category. Fix: `query-activities -a android.media.action.STILL_IMAGE_CAMERA` for Camera.
- **T-m5 — Phase 01 E5 latency start point.** L147 "listener timestamp vs the tile's render timestamp", but L74's threshold is "≤ 2 s after its notification is posted". Fix: start from `StatusBarNotification.getPostTime()`.
- **T-m6 — Phase 01 "since boot" vs "since lock".** L83 and X12 L137 say "since boot"; L84 says history "resets when the screen locks". The lock trigger (screen off? keyguard shown? secure keyguard only?) is undefined, and no E-row covers it (only edge L214). Fix: one rule ("since the keyguard was last shown", e.g., `UsageEvents.Event.KEYGUARD_SHOWN`), used in L83, X12 and E20; add an E20 step: open app → `KEYCODE_SLEEP` → `KEYCODE_WAKEUP` → `wm dismiss-keyguard` → Back on Start does nothing.
- **T-m7 — Phase 03 E5's "no test-only injection path" check is not decidable.** L79 "`dumpsys package app.tileshell` shows no exported component that accepts a request as text". The shell legitimately exports HOME, assist, VIS, IME, dialer/SMS and the tile provider. Fix: an allow-list of exported components with the reason for each; E5 diffs `dumpsys package` against it.
- **T-m8 — Phase 03 E2 "spoken reply captured" has no pass criterion.** L76. Fix: reply text from diagnostics equals the expected string, and the `parecord` capture is non-silent (RMS above a stated floor) over the reply window. No ASR grading.
- **T-m9 — Phase 07 E3/E1 bounds.** E3 L43 "clock left inset (≈31 epx) measure within R3 A21's tolerance": A21 gives no tolerance for the inset; R6 L558 gives 29 ± 4 epx. E1 L41 "`dumpsys power` shows the wakefulness and minimum brightness" names no expected values. Fix: tag the inset ± 4 epx; state the expected wakefulness (Awake) and the brightness override value.
- **T-m10 — Phase 07 checklist row duplicates phase 04's.** L16 adds "Appear on top", but phase-04 L18 already adds "overlay", and 04 is built first. Fix: phase 07 reuses 04's row (Rule 16 ADD), or the two docs name one owner.
- **T-m11 — Stale or mis-pointed text.** phase-03 L35 "Licence posture = PQ1 (open owner question, not ruled here); the engine line above stays as written until PQ1 is answered" (answered at L36). phase-01 L48 and phase-08 L22 state two internet uses; PLAN A11 L46-47 adds visual-voicemail downloads. INDEX research table (L22-28) and PLAN Research tasks (L186-207) have no R6 row, although R6 now gates FINAL of 01/02/03/05/07; PLAN RV9's source order names R3 only. INDEX L17 step 5-6 "pending". PLAN L100 "corrected 2026-09-16 by R3 §B" and phase-01 L30, L70 and task 10 cite "R3 §B" for values that live in R3 A11 (§B is the verdict table).
- **T-m12 — Phase 08 P1/P2 definitions.** P1 L47 "time to first spoken word ≤ 2.5 s for a short request": "short" and the timer's start (end of speech? ASR final?) are undefined. The thermal status is read by one `dumpsys thermalservice`, which shows current status, not a history over 20 requests. Fix: define the request set and the start event; poll thermal status during the run.
- **T-m13 — Phase 05 gaps.** L26 targets capture-rendition colours ("panel (22,27,21)") with no colour tolerance (R3 A16 notes video colour error up to 32/255). Key sounds and vibration (Q1 ruling L22) have no values and no H-row. Documented W10M keyboard features in R6 are not in scope despite ruling C "Everything the W10M keyboard had": space-bar drag to move the keyboard (§2.1.16, D1), cursor-controller handedness (§2.5.5, LOW), language key (§2.8.6).
- **T-m14 — Phase 02 ambiguities.** L35 "between 1–2 px light horizontal rules" has no unit (720p video px of a 2015 capture). L28's fixed point "46–49 % of its height" does not say whether the S25U's taller canvas uses the fraction of screen height or a 640-epx offset, and E7 needs one. The secondary-tile confirmation UI (L27) and the app-list "Pin to Start" menu (L11) are rendered but untagged, with no H-row.
- **T-m15 — Phase 01 untagged or unsourced items.** X6 L131 "48 epx (agent pick, no source)", but R6 L529 gives the Windows-glyph centre at 0.962 / 0.968 of screen height (S1/S2) and R6 L378-379 a 48-epx bar docked on the nav bar: derive a candidate. L76 list rows "lighten while pressed": no amount, LOW as a final-release value, no approximation row. L98 "dark default" is untagged. E9 L151 "a labelled stale state" and edge L207 "the stale threshold" have no design or number. The C1 transparency slider geometry (LOW, 2015) is not in Approximations. L28 parallax "(per R3)" is 14342 camera only, "Not verified on 15063" (RV9 Q12 check). The onboarding checklist has no W10M counterpart but is not marked "P4 design" with a look row (PLAN L76-77).
- **T-m16 — Phase 03 small tags.** L49 press-and-hold duration "(Android's long-press timeout)" is an untagged agent pick. L37 says "the emulator runs with host audio" without the flag; the emulator mutes the virtual mic by default, and I believe `-allow-host-audio` is the flag (not certain). Name it in the AVD launch command.

---

## 5. RV9: rendered values still untagged (file:line → finding)

| File:line | Value | Finding |
|---|---|---|
| phase-01:24, :153 | tile flip/cycle periods "4.4–5.0 s" | T-M2 |
| phase-01:153 | app-list swipe motion; tilt and P4 press styles "within (R3) tolerance" | T-M3 |
| phase-01:75, :162 | drawn status bar height (R3 C4 28-29 epx not carried), status glyph layout, nav-key positions (2-key and 3-key states), auto-hide time | T-M4 |
| phase-01:131 | X6 nav-bar 48 epx "no source" (R6 §6.0 glyph-centre data and §3.3.1-3.3.2 unused) | T-m15 |
| phase-01:76 | list-row press "lightens" (no amount; LOW, no H-row) | T-m15 |
| phase-01:98 | dark theme default | T-m15 |
| phase-01:151, :207 | Weather stale-state label and stale threshold | T-m15 |
| phase-01:33 | transparency slider geometry (R3 C1 LOW) | T-m15 |
| phase-01:28 | parallax 0.25 (14342 camera; 15063 unverified) | T-m15 |
| phase-01:34 | onboarding checklist look (not marked P4 design, no look row) | T-m15 |
| phase-01:81-82, :155 | "New" caption geometry (R6 §5.1.3-5.1.5 not carried) | T-m3 |
| phase-01:70 | "~1.5x", "~30 ms" stagger: "~" instead of the A11 tolerances/confidence | T-m11 |
| phase-02:11 | app-list "Pin to Start" menu | T-m14 |
| phase-02:27 | secondary-tile confirmation UI | T-m14 |
| phase-02:35 | folder band rules "1–2 px" (unit) | T-m14 |
| phase-03:45, :78 | listening persona period/size/position/entrance (R6 §3.1.6-3.1.10) | T-B4 |
| phase-03:69, :79 | Cortana text box (R6 §3.3.1-3.3.7, 3.3.9); typing with the keyboard up (3.3.8 UNMEASURED) | T-M11 |
| phase-03:18 | Cortana page background (R6 §3.1.14: 15063 black) | T-M11 |
| phase-03:44 | locked Cortana look (R6 §3.5.5) and "Unlock to continue" card | T-M11 |
| phase-03:39 | reminder confirm card (R6 §3.4.2); calendar-event and delete cards | T-M9 |
| phase-03:49 | Search-key hold duration | T-m16 |
| phase-04:52, :60 | action center / volume motion; A19/A20 geometry not in Decisions | T-B5 |
| phase-05:26 | key colours without colour tolerance | T-m13 |
| phase-05:22 | key sounds, vibration | T-m13 |
| phase-05:32 | show/hide if the IME cannot own the motion | T-M12 |
| phase-06 (whole doc) | Phone and Messaging UI: every value | T-B5 |
| phase-07:20-21, :45 | Glance settings, modes and default (R6 §6.1.6) | T-M13 |
| phase-07:43 | clock left inset tolerance (R6 §6.1.4 29 ± 4) | T-m9 |
| phase-07:12-17 | date line and notification row position, clock format (R6 §6.1.5) | T-M13 |
| phase-09:21, :68 | Notebook, Correct button, rules and hooks pages (P4 designs, H1 only) | T-B5 |

## 6. Owner questions

None. Every finding is an agent call under existing rulings (Q12 final release governs, RV9 tagging, PQ2/PQ3 as ruled, phase-08 Q2 confirmations).
