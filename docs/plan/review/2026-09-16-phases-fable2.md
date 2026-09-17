# Phase-doc review, reviewer 2 of 2 (testability, evidence, internal consistency) — 2026-09-16

Scope read: PLAN.md, INDEX.md, phase-01 … phase-09. Research files opened only to check cited claims (w10m-measurements.md, w10m-reference.md, r2-reuse-check.md, r5-live-tile-api.md). Phase 04 checked for internal consistency only (R4 pending).
Verification limit: adb / emulator are not installed on this machine (`which adb emulator` → not found), so every command-syntax claim below is checked against the AOSP shell tools as documented, not executed. Where I am not certain I say so.

Counts: BLOCKING 3 · MAJOR 16 · MINOR 15.

---

## BLOCKING (must fix before FINAL)

### B1 — Phase 01 + PLAN: "Recently added" app-list group contradicts R3 A13 (HIGH) and Q12
- Claim: the app list has a "recently added" group.
- Evidence: phase-01-start-live-tiles.md L15 "Swiping left opens the W10M app list (search, recently added, A-Z, jump grid)"; L29 "App list: swipe-left pivot, search, recently added, A-Z, letter jump grid"; L100 build task 11; L122 E12. PLAN.md L58 "App list: swipe left from Start, search box, recently added, A-Z list". w10m-measurements.md L167-168: "**None** in either build. S1 (14393) … S2 (15063) … a newly installed app ("Audible") carries an accent-coloured "New" caption (≈12 epx) under its name instead of a group. Confidence: **HIGH** (two recordings, both builds)." w10m-reference.md L160 already had it NOT FOUND for W10M (desktop only).
- Why blocking: RV9 makes R3 source #1 and Q12 makes the final release govern; E12 as written makes QA pass a part W10M never had, and Rule 16 says nothing gets rebuilt later.
- Fix: drop the group from Goal, Scope, build task 11 and E12; add the "New" sub-label (C2: accent, 12-epx class, directly under the name) and rewrite E12 as: after `adb install` of a test APK its row appears under its letter with the "New" caption and no group header exists above "A" (dump check); the caption's clearing rule (first launch? time?) is unmeasured → tag "approximation" + NEEDS-HUMAN. Update PLAN.md L58. Agent call under Q12/RV9; no owner question.

### B2 — Phase 02 E7 cites "(R3) tolerance" for edit-mode motion that R3 never measured
- Claim: "E7 Edit-mode motion measures within (R3) tolerance from screenrecord frames"; Scope "edit mode interactions and motion (R3 values)" (phase-02-edit-mode-folders.md L17, L46).
- Evidence: w10m-measurements.md has no edit-mode row at all (`grep -i "edit mode\|edit-mode"` → 0 hits; §E status summary L387-394 lists none). Nothing sources the W10M edit chrome (unpin/resize glyphs, dimmed neighbours, reflow, folder expand/collapse) either.
- Why blocking: RV9 (PLAN.md L174-176) — FINAL only with every value tagged source/tolerance/reference or "approximation" + NEEDS-HUMAN. E7 is uncheckable: there is no value and no tolerance to check against.
- Fix: either run an R3 edit-mode pass before FINAL (footage of long-press → edit chrome, drag reflow, resize, folder expand) and cite it, or tag each edit-mode value "approximation" in the doc with one NEEDS-HUMAN row per element (H2 "accept approximations" is too generic to satisfy RV9) and rewrite E7 to measure against those tagged numbers.

### B3 — Phase 05 E3 cites "(R3) tolerance" for keyboard motion that R3 never measured
- Claim: "E3 Motion within (R3) tolerance" (phase-05-keyboard.md L46).
- Evidence: w10m-measurements.md has no keyboard row (`grep -i keyboard` → 0 hits). The doc cites no R1/R3 source for key geometry, row heights, popup, Word Flow trail, cursor-dot or emoji panel either; only "H2 accept approximations".
- Fix: same shape as B2 — an R3 keyboard pass before FINAL, or per-element "approximation" tags with specific NEEDS-HUMAN rows, and E3 rewritten against tagged numbers.

---

## MAJOR

### M1 — Phase 01 Goal contradicts the Q6 press-feedback ruling
- Claim: "Every tappable element has press tilt." (phase-01 L14).
- Evidence: phase-01 L65 Decision Q6: default = "W10M final (no press effect, measured R3 A10)"; PLAN.md L57 "Press feedback on Start tiles as a setting: W10M final none (default)"; PLAN.md L109 build order still says "press tilt". w10m-measurements.md L137 A10 HIGH: no visible tilt/depression/scale/highlight.
- Fix: Goal → "press feedback per the Q6 setting (default none on Start tiles)". State what non-tile tappables do: R3 C5 L365 measured "the row lightens while pressed" for list rows — tag it; and say whether the Q6 setting applies beyond Start tiles (PLAN says Start tiles only). Fix PLAN.md L109.

### M2 — Phase 01 cites R3 for values R3 marks LOW/UNMEASURED, with no per-value approximation tag
- Claims and evidence (phase-01 → w10m-measurements.md):
  - E9b L119 "screens measure within (R3) tolerance" (Weather app) → C5 L340 "every number below is from the 2015 build and is **LOW as a governing-build value**"; L360 background keying UNMEASURED; header colour differs on 15063.
  - E8 L117 now-playing on the Music tile → L327 "**Music now playing** | UNMEASURED".
  - Scope L31 / E13 "show more tiles" → L82 2-column layout "**UNMEASURED**".
  - Scope L31 / E13 transparency → A3 L94 "**UNMEASURED.**" (default slider position).
  - Decision L62 drawn W10M nav bar → L83 "Nav bar height: UNMEASURED".
  - Motion primitives L23 "page transitions" (Settings sub-pages) → L275 "In-app page-to-page turnstile … UNMEASURED".
  - Decision L67 "jump grid written in-house from R3 values" → C2 L311 "**LOW, build 10586**" (non-governing).
  - Light/dark theme L85 accent variants → A17 L189 "UNMEASURED".
- Why: RV9 requires each value tagged; "H4 Accept every value tagged approximation" only works if the doc actually tags them. E9b as written asserts a tolerance R3 does not vouch for.
- Fix: a value table in the doc (or an appendix) with one row per token: source (R3 row id / UWP doc URL / RV6 derivation / "approximation"), build, tolerance; specific NEEDS-HUMAN rows for the Weather app pages, Music face, 2-column grid, nav bar, Settings transition, jump grid. E9b → "matches C5 geometry with S11's 15063 structural differences applied; NEEDS-HUMAN".

### M3 — Phase 03 E4 measures the waveform against R3, which did not capture it
- Claim: "E4 Persona state changes (idle → listening waveform → thinking → speaking) measure within (R3) tolerance" (phase-03 L59).
- Evidence: w10m-measurements.md L247 "the waveform did not appear in these captures"; A22 has ring radii/timings (MEDIUM) for idle/thinking/listening-ring only; nothing for "speaking".
- Fix: split E4 — ring states against A22 numbers; waveform and speaking tagged "approximation" with their own NEEDS-HUMAN rows (H1 "persona feel" is generic).

### M4 — Phase 07 E3 "layout within (R3) tolerance" against a LOW row with the key value UNMEASURED
- Evidence: phase-07 L35 E3; w10m-measurements.md L230-231 "Vertical position: **UNMEASURED** … the content shifts position over time … Confidence: **LOW**."
- Fix: tag clock size/left inset as LOW candidates and vertical position + burn-in shift rule as approximations with specific NEEDS-HUMAN rows; E3 measures only what is tagged with a number.

### M5 — Acceptance criteria reference thresholds that are never set
- Evidence: phase-01 L114 E5 "within (interview) seconds" and L134 P4 "jank threshold set in the interview" — the phase 01 interview queue is fully closed (L75-81) and neither number was recorded; phase-07 L36 P1 "under the interview budget" (L25 interview 2 = agent, at build start); phase-08 L42 P1/P2 "under the interview thresholds" (L31 interview 4 open); phase-09 L62 P2 "within the phase 08 thresholds".
- Why: a criterion with no number has no pass/fail; RV9 wants values tagged before FINAL.
- Fix: phase 01 — record numbers now (agent call; e.g. tile update ≤ 2 s after the notification is posted; `dumpsys gfxinfo` janky ≤ 5 % and 99th-percentile frame ≤ 1 vsync at the device refresh rate). Phases 07/08/09 — either numbers now or a written derivation rule (baseline measured at build start × factor, recorded in QA evidence before the run), so the pass/fail exists before QA.

### M6 — Phase 03 promises a Cortana tile it never builds or tests
- Claim: Goal "Cortana … opens from its tile" (phase-03 L11-12); PLAN.md L184 RV8 "The Cortana tile belongs to the Cortana commands phase"; phase-01 L37 and phase-02 L18 push the tile to 03.
- Evidence: phase-03 Scope In L18 lists only "Cortana app-list entry (pinnable via phase 02)"; build tasks L45-52 have no tile task; no E/P row mentions the tile. Also w10m-measurements.md L323: the W10M Cortana tile had a news-headline back face (needs internet), so the offline tile's face is undefined.
- Fix: add build task "Cortana tile: default-layout slot ADD + face" (static logo/ring, no news back face per A11), an E row (present in the default layout after phase 03 even with a persisted layout — that is an ADD to phase 01's store; tap opens Cortana), and a value tag for the face (C3/A22 or approximation).

### M7 — Confirmation flow is defined in phase 08 for actions phase 03 owns (Rule 16)
- Evidence: phase-08 L23 "Cortana confirms before anything that reaches other people or changes Jeremy's data (sending texts, placing calls, adding or deleting calendar events and reminders)"; phase-03 L31 confirms only calendar events ("inserted directly after a W10M-style confirmation card"); texts, calls, reminders have no confirmation in phase 03.
- Why: if phase 08 adds confirmation to the shared actions, phase 03's behaviour changes (a replacement, not an ADD); if not, the same action confirms in one layer and not the other.
- Fix: put the confirmation flow in phase 03's action layer (W10M Cortana read a text back and asked before sending — R1 §9 has no row for it, so the agent must source or tag it) with an E row and edge cases (confirm / cancel / "add more"); phase 08 calls the same actions and adds nothing. Owner question only if the agent reads the phase 08 ruling as LLM-layer-only: "Should the fixed-command layer confirm texts and calls the same way?"

### M8 — Phase 01 E18 uses commands that do not produce the state it asserts
- Claim: "`adb shell pm create-user --profileOf 0 --managed work` + an app installed into it … with the profile stopped (`adb shell am stop-user <id>`) the group shows 'Tap to unlock'" (phase-01 L127).
- Evidence/problems: (a) `pm create-user` creates the profile stopped and without a profile owner; nothing appears until `am start-user <id>` and `adb install --user <id>`. (b) The design's locked state (L64 "a locked profile's group stays collapsed with 'Tap to unlock' and unlocking goes through Android's own prompt") is quiet mode (`UserManager.isQuietModeEnabled`); `am stop-user` stops the user without setting quiet mode, so the shell will see a stopped-but-not-quiet profile and the criterion tests the wrong branch. (c) The design also covers private space, which on API 36 is a different profile type (`pm create-user --profileOf 0 --user-type android.os.usertype.profile.PRIVATE private`) whose locked state hides apps; E18 never exercises it.
- Fix: E18 steps = create, start, install; lock via quiet mode through the shell's own header/Settings action (the default launcher may call `requestQuietModeEnabled`); verify with `adb shell dumpsys user` (quiet-mode flag) plus the dump; add the private-space case.

### M9 — Phase 05 E6 "selection indices in the dump" — the dump has none; E2 password text is masked
- Evidence: phase-05 L49 E6; L45 E2 "(`adb shell uiautomator dump` text value) across … password input types". `uiautomator dump` node attributes are index/text/resource-id/class/package/content-desc/checkable…/password/bounds; there are no selection-start/end attributes, and a password field's `text` is rendered as dots.
- Fix: a test-fixture app (a real installed APK — Rule 4 holds) whose screen mirrors the field's raw text and `selectionStart`/`selectionEnd` into TextViews; E2 and E6 read those from the dump.

### M10 — Phase 05 E4 glide via `adb shell input motionevent` is not a glide
- Claim: "a swipe path injected with `adb shell input motionevent DOWN/MOVE/UP` over the letters of a dictionary word commits that word" (phase-05 L47).
- Evidence: the syntax is valid on API 36, but each call is a separate `input` process, so the path arrives as a few MOVE events ~100 ms+ apart; glide recognisers sample velocity and will treat it as a slow stutter — flaky or a different word, depending on adb latency.
- Fix: inject one continuous gesture: UiAutomator `UiDevice.swipe(Point[] segments, int segmentSteps)` from an instrumentation APK (real input pipeline), or `sendevent` on the emulator's touch device; keep `input motionevent` for long-press only.

### M11 — Phase 06 P3 emergency dialing is unverifiable as written
- Claim: "P3 emergency-number dialing is handed to the system correctly (verify with the dialer's emergency path, never by calling emergency services)" (phase-06 L52) — phone-only.
- Evidence: on a real SIM there is no way to exercise the path without placing the call. On the emulator the emulated modem answers any number and reaches nobody, so it is the safe place: dial 911 on the shell's pad → `adb emu gsm list` shows the outbound call, `adb shell dumpsys telecom` shows it flagged emergency.
- Fix: move to Emulator E6; on the phone verify only classification (`TelephonyManager.isEmergencyNumber` surfaced in the shell's diagnostics), never dial.

### M12 — Phase 07's "never weakens the real secure lock screen" has no test
- Evidence: phase-07 L12 Goal; E1 L35 "activates glance per the ruling" names no observable; no E/P/edge row runs with a lock set.
- Fix: E row: `adb shell locksettings set-pin 1234`, `input keyevent KEYCODE_SLEEP` → glance visible (`dumpsys window` shows the glance window above the keyguard, `dumpsys power` wakefulness/brightness, screencap); any tap/swipe/Home/Back from glance lands on the keyguard, never on Start or an app; `locksettings clear --old 1234` after. Edge cases: no lock set; lockdown mode; extend-unlock. P2 proximity is drivable on the emulator (`adb emu sensor set proximity 0` / `5`) — move it.

### M13 — Phase 09 Security decision contradicts its own Scope on who may edit hooks
- Evidence: L35 "Only adb over USB with debugging authorised can read or write them, and scripts can't be created by voice"; L30 Out "scripts come only from files"; L21 Scope In: Notebook "browse, edit, delete memories, corrections, rules and hooks"; L23 rules "creatable by voice"; corrections write memory on-device (L22).
- Why: the hook runtime is the write gate on actions; which surfaces can author scripts is its trust boundary, and the doc says two different things.
- Fix: Notebook = view/enable/disable for hooks (no script editor), edit for memories/rules; rewrite Security to "no other app; on-device edits only through the shell's Notebook/voice paths; hook scripts only via the adb provider"; add an E row that the Notebook exposes no script editor. Adversarial review stays mandatory.

### M14 — Phase 08 leaves the permanent runtime and model unnamed, and contradicts itself on the host
- Evidence: L31 interview 4 "[agent] Runtime + model pick" still open; RV2 (PLAN.md L164) "one permanent engine per part, named at the phase that introduces it"; the download format (.gguf vs .litertlm) fixes the runtime for good. L22 "from the model maker's official host (non-Google, P5)" vs L31 "Google's Gemma only if no non-Google model meets the thresholds". Goal L12 uses "the toggle commands from phase 04" but depends-on L5 is [03].
- Fix: name the runtime now (r2-reuse-check.md L234: llama.cpp MIT is the non-Google route; L232 LiteRT-LM is Google's), the candidate list, and the benchmark thresholds (see M5); write "a Google model needs the reason here and amends the host clause"; add 04 to depends-on or state toggles are ADDed when 04 lands.

### M15 — Phase 06 edge cases miss the most common call scenario
- Evidence: L57-63 list glance, action center, DND, Bluetooth, dual SIM — nothing for an incoming call while the keyguard is locked (in-call UI over the secure lock screen, answer without unlocking, hang-up returns to the keyguard), MMS with mobile data off / Wi-Fi only (MMS needs the cellular data path and the T-Mobile APN), or SMS_DELIVER arriving while the shell process is dead.
- Fix: add them; P1/P2 state that the locked-phone case is included.

### M16 — Voice-driven emulator criteria hang on an unspecified audio route; the obvious workaround is a mock
- Evidence: phase-03 L33 "emulator audio route chosen at build start, anything it can't drive is phone-only"; L57 E2 "a spoken reply captured from the audio output"; L58 E3; phase-05 L51 E8; phase-08 L41 E1; phase-09 L55-60 E1-E4/E6 all take utterances.
- Why: if the route fails at build time, the builder's fallback is a debug text-injection intent, which is a mock (owner Rule 4).
- Fix: record the route now: emulator `-allow-host-audio` with a PulseAudio/PipeWire null sink whose monitor is the default source (`pactl load-module module-null-sink sink_name=vmic`; `pactl set-default-source vmic.monitor`; `paplay -d vmic utterance.wav`); capture replies with `parecord -d <emulator sink>.monitor`; on the phone `dumpsys audio` player state + NEEDS-HUMAN intelligibility. Forbid any test-only text path in the doc.
- Owner question (WHAT): W10M's Cortana also accepted typed requests in its text box. Should this Cortana? If yes, it is a phase 03 scope item and a legitimate real-system way to drive matcher/LLM/harness criteria (ASR still tested with WAV-through-mic).

---

## MINOR

### m1 — Stale text left behind by later decisions
- phase-01 L36 "export-import: phase 02" (ruled none: phase-02 L25, PLAN.md L63); phase-01 L46 "Weather in, the only internet use" (A11 amended, PLAN.md L46-47); phase-01 L81 queue item 7 still reads "AVD image = Google Play image" un-struck though L66-67 supersede it; PLAN.md L109 "press tilt" (Q6); PLAN.md L85 "all 8 phases" (there are 9); phase-03 L15 "No internet at any point" → "Cortana itself makes no network request" (the shell does: Weather, model download).

### m2 — Numbering
- phase-01: E14 listed after E18 (L128), H8 before H7 (L145-146); phase-02: E5 missing (L44-46); phase-06: P4 before P3 (L52). Renumber.

### m3 — "Network off" is asserted, never proven
- phase-03 L57 (`svc wifi disable` / `svc data disable`), phase-01 L118 E9, phase-05 L51 E8, phase-08 L41 E1. Fix: `adb shell cmd connectivity airplane-mode enable`, then `adb shell dumpsys connectivity` shows no active default network; emulator NAT does not pass ICMP, so ping is not evidence.

### m4 — Phase 05 E1 skips `ime enable`
- L44: `ime set <id>` fails until `ime enable <id>`; the checklist's "IME enabled" row is that step — say so.

### m5 — Phase 09 E3 `adb shell date` needs root
- L57. The AOSP image allows `adb root` (a P5 benefit worth noting): `adb root; adb shell settings put global auto_time 0; adb shell date MMDDhhmm[[CC]YY]`.

### m6 — Phase 02 E1 drag via `input swipe` will not pick up a tile
- L41. A linear swipe exceeds touch slop before the long-press timer fires. Use `input motionevent DOWN`, sleep, `MOVE…`, `UP`, or UiAutomator `UiDevice.drag()`; the resize control's position comes from the dump.

### m7 — Fixture apps and per-command observables unnamed
- phase-01 L112 E4 "a category with 2+ handlers": the AOSP image has ≤ 1 handler per category (no mail/music/maps/store apps) — name the two fixture APKs per category (L66 allows fixtures). phase-03 L57 E2 "each command … produces its real effect": needs fixture apps for maps/music/notes and a per-command observable table (alarm: DeskClock + `dumpsys alarm`; reminder: notification at time, Doze via `dumpsys deviceidle force-idle`; event: `content query --uri content://com.android.calendar/events`; text: `content://sms`; call: `dumpsys telecom`).

### m8 — Phase 01 E3/E13 need node identity in `uiautomator dump`
- L111, L123. Compose emits resource-ids only with `testTagsAsResourceId`; Canvas-drawn tiles need semantics nodes. Add to build task 4/7 so bounds can be matched across sizes.

### m9 — Phase 01 E6 "a MediaStore scan" — name the command
- L115. e.g. `adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary`, or a `MediaScannerConnection` call from the fixture app; otherwise "cycle" may wait on a scan that never ran.

### m10 — Phase 08 has no happy-path download criterion
- Edge cases (L46) cover failures; add E0: first enable → download with progress, checksum pass, `svc wifi disable` mid-way then resume.

### m11 — Phase 09 replay oracle for answer-only corrections
- L16-17 "Every correction becomes a replay test … checked deterministically against the corrected action". A correction to answer text has no action; state that such corrections become memory plus a string/entity assertion on the answer (no LLM judge), or are excluded from replay.

### m12 — Phase 01 Search button on the drawn W10M bar has no phase-01 behaviour
- L62 draws Back/Windows/Search; nothing says what Search does before phase 03 ADDs Cortana. State it (nothing, or app-list search — W10M's Search key without Cortana opened Bing search: a fidelity call). Also phase-03 L61 P4 (Search button, long-press Home) is emulator-drivable (`input tap`, `input keyevent --longpress KEYCODE_HOME`); move it.

### m13 — Phase 01 missing edge cases / soft criterion
- Start background image: file deleted after pick, huge image, permission revoked, none set. System locale/timezone/date change (Calendar tile, app labels, jump grid "#" group). E10's random 4.4-5.0 s periods: assert a distribution (N intervals in range, phases differ across tiles) rather than "within tolerance".

### m14 — Phase 05 missing edge cases
- `imeOptions` action key (Go/Search/Send/Next) label and behaviour; multi-line fields; `textNoSuggestions` / `textCapSentences`; hardware keyboard attached.

### m15 — Phase 01 E3 `wm size 1440x3120` on a 1080x2340 AVD
- L111. An override larger than the panel — confirm with `adb shell dumpsys display` that the override applied, else use a second QHD+ AVD for that row.
