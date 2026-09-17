# Phase-doc review round 1: triage (2026-09-16)
Inputs: review/2026-09-16-phases-fable.md (reviewer 1, design: B1-B3, M1-M12, m1-m10) and review/2026-09-16-phases-fable2.md
(reviewer 2, testability: B1-B3, M1-M16, m1-m15). Codex was unavailable (see INDEX.md reviewers line).
IDs below: F1-x = reviewer 1, F2-x = reviewer 2.

Dispositions: ASK = Jeremy decides WHAT (recorded under "Owner rulings" when answered) · AGENT = apply as written ·
R6 = needs the R6 measurement pass (docs/plan/r6-measurements.md); until it lands, tag the value "(pending R6)".

## ASK (one at a time, in this order)
| Q | Findings | Question |
|---|---|---|
| PQ1 | F1-B1 | Cortana's Kokoro voices need espeak-ng data (GPL-3.0). Accept GPL in the voice path, or a GPL-free TTS with fewer/lower-quality voices? |
| PQ2 | F1-B2 | T-Mobile visual voicemail needs privileged APIs (mstore + GBA). Drop the voicemail list (button dials voicemail), or keep it as a helper-gated experiment? |
| PQ3 | F1-M6 | What Cortana may do when the phone is locked (side key on the lock screen). |

## AGENT: apply to the docs
### PLAN.md / INDEX.md
- F1-m1, F2-m1: PLAN status line (R3 is done); "all 8 phases" → 9; PLAN build order "press tilt" → "press feedback (Q6 setting)"; PLAN feature list "recently added" → "New caption on newly installed apps (R3 A13)"; INDEX "principles P1-P3" → P1-P5 and "the 8 phase docs" → 9.

### Phase 01
- F1-B3, F2-M5: thresholds. E5: tile content updates ≤ 2 s after the notification is posted. P4: during the scripted Start scroll + tile flips, `dumpsys gfxinfo` janky frames ≤ 5 % and 99th-percentile frame time ≤ one vsync period at the device's current refresh rate.
- F1-M1, F2-m12: new build task for immersive system-bar control + drawn W10M status bar (clock, battery, signal, Wi-Fi feeds) + drawn nav bar with Back and Windows keys; E-row (Samsung bars hidden on Start, drawn bar bounds, swipe reveals transient bars); P-row (3-button nav hide on One UI 8). The Search key is not drawn in phase 01: phase 03 ADDs it wired to Cortana (Rule 16, no placeholder).
- F1-m4: "Back on Start does nothing" is unsourced → tag "(pending R6)".
- F1-M2, F2-M1: Goal → "Press feedback on Start tiles follows the Q6 setting (default: none)". The Q6 setting covers Start tiles only; list rows lighten while pressed (R3 C5, tagged with its confidence).
- F1-M3: copy R5 §4's security summary into Decisions (caller identity via getCallingPackage, caller-owned URI authority check, size caps, 60 verbs/min rate limit, kill switch). Edges: DTD / external entity / entity expansion (parser runs with DTDs disabled); URI whose authority is the shell's own (confused deputy); shared-uid package spoofing; package reinstalled under a different signer. Add: "Trust-touching part: adversarial team review is mandatory before `done`."
- F1-M4: build types: emulator = debug; every phone install = release-signed, debuggable=false; P-row `adb shell dumpsys package app.tileshell | grep -i flags` shows no DEBUGGABLE.
- F1-M12, F2-M2: add an "Approximations" section: one row per LOW/UNMEASURED value phase 01 renders (Weather app pages C5 LOW + background keying UNMEASURED; Music now-playing face UNMEASURED; 2-column grid UNMEASURED; transparency default A3 UNMEASURED; nav-bar height UNMEASURED; Settings page transition UNMEASURED; jump grid C2 LOW 10586; light-theme accent variants A17 UNMEASURED; easing formula A12 UNMEASURED; "New" caption clearing rule) with the stand-in used and its own H-row. E9b → structure per C5 with S11's 15063 differences, NEEDS-HUMAN.
- F1-m5: Weather app shows Open-Meteo attribution (CC BY 4.0).
- F1-m9, F2-m7: name the open-source fixture APKs (installed for tests only, never bundled): an SMS app for E5 on the AOSP image; two handlers per category for E4's "2+ handlers" case.
- F1-m10: edge: Android 14+ partial photo access → tile cycles the selected subset; checklist row shows "partial".
- F2-B1: remove "recently added" (Goal, Scope, task 11, E12); add the accent "New" caption under a newly installed app's name (R3 A13/C2); E12 → after `adb install` of a test APK its row appears under its letter with the caption and no group header above A; clearing rule "(pending R6)".
- F2-M8: E18 steps: `pm create-user --profileOf 0 --managed work` → `am start-user <id>` → `adb install --user <id> <apk>`; lock via quiet mode from the shell's own group action; verify with `adb shell dumpsys user` (quiet mode) + dump; add the private-space case (`--user-type android.os.usertype.profile.PRIVATE`).
- F2-m2: renumber (E14 after E13; H7 before H8). F2-m3: "network off" = `adb shell cmd connectivity airplane-mode enable` + `dumpsys connectivity` shows no active default network. F2-m8: build tasks 4/7 add `testTagsAsResourceId` and semantics nodes for Canvas-drawn tiles. F2-m9: E6 names `adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary`. F2-m13: edges for background image (deleted after pick, huge, permission revoked, none set) and locale / timezone / date changes; E10 asserts a distribution (N intervals within 4.4-5.0 s, phases differ across tiles). F2-m15: E3 confirms the QHD+ override with `dumpsys display`, else uses a second AVD.
- Stale: "Weather in, the only internet use" → per amended A11; queue item 7 strike "Google Play image"; "export-import: phase 02" → removed.

### Phase 02
- F1-M5: E-row for secondary tiles (test APK request → confirmation UI → tile with its arguments; decline → nothing; owner uninstalled → tiles removed) + edges (request while Start not visible / requester in background; duplicate tileId; owner uninstalled with tiles inside a folder; owner update changes arguments).
- F2-B2: edit-mode values "(pending R6)"; E7 measures against the tagged values; one H-row per edit-mode element left as approximation.
- F2-m6: E1 drag via `input motionevent DOWN` + wait + `MOVE` + `UP`, or UiAutomator `UiDevice.drag()` from an instrumentation APK. F2-m2: renumber (no E5 gap).

### Phase 03
- F1-B1: record the fact now in Decisions: Kokoro in sherpa-onnx requires espeak-ng data, GPL-3.0-or-later; posture = PQ1.
- F2-M16: typed requests: W10M's Cortana had a text box, so Cortana accepts typed requests (fidelity A4, agent) → scope + build task + E-row. Emulator audio route recorded: emulator with host audio, a PulseAudio/PipeWire null sink as the default source (`pactl load-module module-null-sink sink_name=vmic`, `pactl set-default-source vmic.monitor`, `paplay -d vmic <utterance.wav>`), replies captured with `parecord` from the emulator's output sink monitor. No test-only text-injection path may exist; typed requests go through the real text box.
- F2-M3: E4 split: ring states against R3 A22; listening waveform + speaking state "(pending R6)" with their own H-rows.
- F2-M6: Cortana tile: build task (ADD a Cortana slot to the default layout + its face: static logo/ring, no news back face because A11 has no news internet use); E-row (present after phase 03 even with a persisted layout; tap opens Cortana); face value tagged.
- F2-M7: the confirmation flow belongs to phase 03's action layer: texts and calls read back and ask; calendar events and reminders (add or delete) confirm; confirm / cancel / "add more" edges; E-row. Phase 08 calls the same actions and adds nothing. W10M read-back flow "(pending R6)".
- F1-m3: record model variants and budget: int8 ASR + TTS models; APK size budget ≤ 600 MB; models stay stored uncompressed in the APK and are memory-mapped, not copied (no double storage), verified at build start.
- F1-m7: reminders use `USE_EXACT_ALARM` (granted at install for a sideloaded app); the checklist row shows it granted, with an edge for a system that revokes it.
- F2-m1: "No internet at any point" → "Cortana itself makes no network request (the Weather part and the one-time model download are separate)".
- F2-m7: a per-command observable table (alarm/timer: DeskClock UI + `dumpsys alarm`; reminder: notification at time, Doze via `dumpsys deviceidle force-idle`; calendar event: `content query --uri content://com.android.calendar/events`; text: `content query --uri content://sms/sent`; call: `dumpsys telecom`; open app / music / directions / photo / note: resumed activity of the slot app) and named fixture apps.
- F2-m12: Search key ADDED here, wired to Cortana; P4 moves to emulator (`input tap` on the Search key, `input keyevent --longpress KEYCODE_HOME`).

### Phase 04 (design consistency only)
- F1-M10: "Trust-touching part: adversarial team review is mandatory before `done`." Edges: another app obtaining the helper binder (handoff verifies client uid + signature); the helper accepts only an allow-listed verb set (no generic shell); the helper's shell uid also passes phase 09's adb-shell-uid provider check (the provider must additionally reject the helper's process).

### Phase 05
- F2-B3: keyboard values "(pending R6)"; E3 measures against tagged values; per-element H-rows for what stays approximate.
- F2-M9: a fixture app mirrors the field's raw text and selectionStart/End into TextViews; E2/E6 read those. F2-M10: E4 injects one continuous gesture with UiAutomator `UiDevice.swipe(Point[], steps)` from an instrumentation APK. F2-m4: E1 runs `ime enable <id>` before `ime set <id>`. F2-m14: edges for imeOptions action keys, multi-line, textNoSuggestions / capitalisation flags, hardware keyboard attached. F1-m8: edges for voice typing while Cortana is listening, and the voice key while another app holds the assistant role (engine is ours, the role isn't required).

### Phase 06
- F1-B2: posture = PQ2. Until ruled, P4 is suspended.
- F2-M11: emergency moves to emulator E-row (dial 911 on the shell pad → `adb emu gsm list` shows the outbound call, `dumpsys telecom` flags it emergency); phone verifies classification only (`TelephonyManager.isEmergencyNumber` in diagnostics), never dials.
- F2-M15: edges: incoming call while the keyguard is locked (answer without unlocking; hang-up returns to keyguard); MMS with mobile data off / Wi-Fi only; SMS_DELIVER while the shell process is dead. P1/P2 include the locked case. F2-m2: renumber P3/P4.

### Phase 07
- F1-M7: activation = a showWhenLocked + turnScreenOn activity started under the SYSTEM_ALERT_WINDOW background-start exemption; checklist row "Appear on top"; depends-on stays [01]. Edges: power key while glance shows (really sleeps, no loop); touches on glance do nothing but wake to the keyguard; lockdown mode; glance finishes on unlock. P-row: the exemption survives reboot and Device care.
- F2-M4: E3 measures only numbered values (clock size / left inset LOW); vertical position + burn-in shift rule = approximations with H-rows.
- F2-M12: E-row with a lock set (`adb shell locksettings set-pin 1234` → sleep → glance above the keyguard in `dumpsys window`; every tap/key lands on the keyguard, never Start or an app; `locksettings clear --old 1234`). Proximity moves to emulator (`adb emu sensor set proximity 0` / `5`). Edges: no lock set, extend-unlock.
- F2-M5: battery rule: at build start record the 8 h screen-off baseline (`dumpsys batterystats`), then each mode's added drain; hard ceiling for always-on 2 % per hour at minimum brightness; the numbers are accepted by Jeremy in an H-row before `done`.

### Phase 08
- F1-M8: the LLM runtime runs in its own `android:process`; the launcher process never loads weights; edge: runtime process killed → Start unaffected, Cortana says so and reloads; P2 measures both processes. Record model file size and the pre-download storage check.
- F2-M14, F2-M5: runtime = llama.cpp (MIT) with GGUF models; candidates Microsoft Phi-4-mini (MIT), Qwen3-4B (Apache-2.0), SmolLM3-3B (Apache-2.0), licences re-verified at build start. Thresholds on the S25U: time to first spoken word ≤ 2.5 s for a short request; decode ≥ 10 tokens/s; runtime process PSS ≤ 4 GB; no thermal status ≥ SEVERE across 20 consecutive requests. A Google model needs its reason written here and amends the host clause. depends-on → [03, 04].
- F2-m10: E0 happy-path download (progress, checksum pass, Wi-Fi off mid-download then resume).
- F2-M7: confirmations reference phase 03's action layer.

### Phase 09
- F1-M9: action calling uses greedy decoding (temperature 0) in live use and in replay; E6 states it.
- F1-M11, F1-m6, F1-M4: voice corrections and voice-created rules only while unlocked and after a confirmation card; edge for an unauthorised voice correction attempt (locked phone / declined card); threat model covers any adb-paired host (USB or Wireless debugging) and the phone runs a non-debuggable build; adversarial review scope includes the voice write path.
- F2-M13: Notebook = view / enable / disable for hooks (no script editor), edit for memories and rules; Security text rewritten to match; E-row: the Notebook exposes no script editor.
- F2-m5: E3 time change: `adb root`, `adb shell settings put global auto_time 0`, `adb shell date MMDDhhmm`.
- F2-m11: answer-only corrections become memory plus a string/entity assertion on the answer (no LLM judge).

## R6 measurement pass (dispatched 2026-09-16)
Edit mode (F2-B2), keyboard (F2-B3), Cortana listening waveform + speaking state + text box + read-back confirmation (F2-M3, F2-M7), Back key on Start (F1-m4), "New" caption clearing rule (F2-B1), Cortana above the lock screen setting (PQ3 context), glance vertical position / shift (F2-M4).

## Owner rulings
- 2026-09-16 PQ1: A. Keep Kokoro voices and accept GPL-3.0 espeak-ng data in the voice path for now; if the app is ever shared or sold, the voices are swapped before release (A10). Applied to phase 03 Decisions.
- 2026-09-16 PQ2: B. Visual voicemail is tried as an experiment through the phase 04 privileged helper; the probe runs during the R4 spike on the phone. Works → voicemail list + on-device transcription, and voicemail downloads become a third internet use (A11). Fails → Jeremy is re-asked, no silent fallback. Applied to phase 06, PLAN R4/A11 notes, INDEX.
- 2026-09-16 PQ3: A. While locked, Cortana runs time, weather, alarms, timers and music directly; calls, texts, reading the calendar, reminders and opening apps show "Unlock to continue" and finish after unlock. W10M's own setting name/wording used if R6 finds one. Applied to phase 03.
