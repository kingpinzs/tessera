# Phase-doc review, round 1 — Reviewer 1 (fable), 2026-09-16

Scope: PLAN.md, INDEX.md, phase-01..09. Lens: design, correctness, Android/Samsung feasibility (S25 Ultra, One UI 8, no root).
Phase 04 reviewed for design consistency only (waits for R4). Default verdict on every claim: not proven.
Research files were opened only to check a named claim. Web sources are cited inline.

Counts: BLOCKING 3 · MAJOR 12 · MINOR 10.

---

## BLOCKING

### B1 — Phase 03 — the named permanent TTS engine pulls GPL-3.0 into the APK
- **Claim under review:** phase-03 line 33: "one runtime, sherpa-onnx (Apache-2.0, verified in R2), for speech-to-text, TTS and future keyword spotting; English open-vocabulary ASR model and Kokoro-82M multi-voice TTS (Apache-2.0, per R2), each model's licence re-verified at build start". RV2 (PLAN line 164) makes this engine permanent: "No 'v1 then v2' swaps".
- **Evidence:** Kokoro in sherpa-onnx cannot run without espeak-ng data: the official usage is `--kokoro-data-dir=./kokoro-multi-lang-v1_0/espeak-ng-data` (https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html). That data and the espeak-ng phonemiser are GPL-3.0-or-later; a mirror of the k2-fsa voice pack notes "392 of its 417 entries are espeak-ng data under GPL-3.0-or-later, while the archive itself carries only an Apache-2.0 LICENSE" (https://github.com/jgisin/agentum-runtime/releases/tag/kokoro-multi-lang-v1_0; see also https://github.com/hexgrad/kokoro/issues/247). R2 line 21 treats exactly this class as a trap: "GPL-3.0 on HayaiTTS and on the Piper engine ... Do not link them." R4 (PLAN line 198) says "GPL = not usable"; phase 06 line 17 excludes "GPL code (R2 licence traps)". The phase 03 decision records only the model's Apache-2.0 licence, not the runtime's transitive GPL dependency.
- **Why blocking:** the doc names a permanent engine under RV2 while carrying an unrecorded licence conflict with the plan's own GPL stance. Discovering it "at build start" forces either a swap (RV2 violation) or a silent policy change.
- **Fix:** record the transitive licence in the phase 03 decision; settle the posture before FINAL; if GPL is refused, name an espeak-free TTS route now (sherpa-onnx models that use a lexicon instead of espeak-ng, or a newer espeak-free model) and re-verify voice count/quality against interview Q3 ("several bundled voices").
- **Owner question (WHAT — licence posture):** The Cortana voice you picked (Kokoro) needs a GPL-3.0 component. On your own phone that is harmless; if the project is ever distributed the whole APK becomes GPL, which cuts against A10's "swap out if it goes public". (a) Accept GPL-3.0 in the voice path. (b) Keep the APK GPL-free and take a lower-quality, smaller-voice-set espeak-free TTS. (c) Other / clarify.

### B2 — Phase 06 — the visual-voicemail ruling rests on a mechanism T-Mobile no longer offers to third-party apps
- **Claim under review:** phase-06 line 24-25: "Voicemail: A. A visual voicemail list in the W10M Phone app where the carrier's standard visual voicemail is reachable" and "Whether T-Mobile's visual voicemail reaches a third-party default dialer through Android's standard VisualVoicemailService is UNVERIFIED ... it's checked phone-only at build start". P4 (line 52) requires unconditionally: "a real voicemail left from the second number appears in the list, plays, and shows an on-device transcript".
- **Evidence:** Android's VisualVoicemailService only delivers the carrier's activation/status SMS to the default dialer; the fetch protocol is the app's job (https://source.android.com/docs/core/permissions/voicemail). T-Mobile's current protocol is not OMTP/CVVM: "new VVM activations via CVVM are no longer permitted. It has been replaced by two HTTP-based protocols. The first is the mstore API" and "the mstore API uses 3GPP-GBA authentication, which requires communication with the SIM card for every API call" (https://github.com/chenxiaolong/tmovvm). The only open client states: "This tool requires access to Android's system APIs. Thus, it must be built within the AOSP tree." Android's GBA entry point `TelephonyManager.bootstrapAuthenticationRequest` requires `MODIFY_PHONE_STATE` or `PERFORM_IMS_SINGLE_REGISTRATION` (https://github.com/aosp-mirror/platform_frameworks_base/blob/master/telephony/java/android/telephony/TelephonyManager.java) — signature/privileged permissions a sideloaded dialer cannot hold. Whether the phase 04 shell-uid helper could hold them is not proven either way. Separately, VVM is an internet use: A11 (PLAN line 46) says "the launcher's only internet uses are Weather and the one-time download of Cortana's AI model" — phase 06 adds a third without amending A11.
- **Why blocking:** a Jeremy ruling (A) was taken on a premise ("carrier's standard visual voicemail ... VisualVoicemailService") the evidence contradicts, and the acceptance row bakes it in. Phase 06 cannot go FINAL with an acceptance criterion that, on the evidence, no unprivileged app can meet.
- **Fix:** rewrite the Voicemail decision around what the app can do: the Voicemail button dials the voicemail number (W10M's own behaviour where VVM was unavailable); make any VVM list conditional on a phone-only probe that names the real protocol (mstore + GBA) and the privilege path; if kept, add the internet use to A11 and a depends-on [04]. Keep the on-device transcription only if VVM audio is obtainable.
- **Owner question (WHAT):** T-Mobile's basic visual voicemail is already free (the paid tier is only voicemail-to-text, which we can do on-device), and their current VVM protocol needs privileged Android APIs a sideloaded dialer cannot get. (a) Drop the VVM list: the Voicemail button dials voicemail, like W10M without VVM. (b) Keep a VVM list as an experiment gated on the phase 04 helper, knowing it may never work and adds an internet use. (c) Other / clarify.

### B3 — Phase 01 — acceptance thresholds still read "(interview)" after the interview was closed
- **Claim under review:** phase-01 line 107: "(interview) values are set in step 4"; E5 line 114: "within (interview) seconds"; P4 line 134: "stays within the jank threshold set in the interview". Every interview-queue item (lines 75-81) is struck through as ruled/closed; INDEX line 16 marks step 4 done for 01.
- **Why blocking:** two acceptance criteria have no threshold; QA cannot pass or fail them, and the doc is about to be frozen with the placeholders in it.
- **Fix:** the agent sets both values now (tile-update latency in seconds; jank threshold as a `framestats` percentile/frame-time bound) and records them in Decisions; no owner question (implementation calls).

---

## MAJOR

### M1 — Phase 01 — the drawn W10M status/nav bar on Start has no build task, no acceptance row, and its feasibility claim has no QA row
- **Claim:** line 62: "on Start the Samsung 3-button bar is hidden (immersive, swipe to reveal) and the W10M Back / Windows / Search bar is drawn ... Hiding the bar on One UI 8 with 3-button nav is UNVERIFIED; QA on phone"; line 86: "Samsung's bars are hidden and W10M status + nav bars are drawn".
- **Evidence:** Build tasks 1-15 (lines 90-104) contain no status-bar or nav-bar task; acceptance E1-E18 / P1-P6 contain no row for "Samsung bars hidden on Start, W10M bars drawn, transient reveal works"; the UNVERIFIED claim has no phone-only P-row. The drawn status bar also needs data feeds (signal, Wi-Fi, battery, clock) that appear nowhere in Scope. The drawn Search key's behaviour in phase 01 is undefined (Cortana arrives in 03; phase-03 P4 assigns it then).
- **Fix:** add a build task (immersive control + drawn status bar with its feeds + drawn nav bar), an emulator E-row (bars hidden, drawn bar bounds, transient reveal), a phone P-row for the 3-button-nav hide, and a one-line decision for what the Search key does before phase 03 (nothing, or app-list search) so it is not a Rule 16 placeholder by accident.

### M2 — Phase 01 / PLAN — Goal text contradicts the Q6 press-feedback ruling
- **Claim:** phase-01 line 14: "Every tappable element has press tilt." PLAN line 109 (Build order): "tile engine, press tilt".
- **Evidence:** phase-01 line 65 (Q6, Jeremy): default = "W10M final (no press effect, measured R3 A10)"; R3 A10 measured "no visible tilt, depression, scale or highlight on Start tiles at pointer-down" (w10m-measurements.md line 137).
- **Fix:** Goal → "Press feedback on Start tiles follows the Q6 setting (default: none)"; PLAN line 109 → "press feedback (Q6 setting)".

### M3 — Phase 01 — the Live Tile API is a trust surface with no adversarial-review note and thin security edges
- **Claim:** line 57: "one exported ContentProvider whose call() verbs ... authorisation = ContentProvider.getCallingPackage() ... payload = adaptive-tile XML ... images passed by content-URI grant and copied at update time".
- **Evidence:** phase 09 line 35 carries "adversarial team review is mandatory before `done`" for its gate; phase 01 carries none for an exported, permission-less provider that parses XML and opens caller-supplied URIs from any installed app. Edge cases (line 151) list "malformed or oversized XML; image URI grant revoked before copy" but not: DTD / external-entity / entity-expansion payloads (XXE, billion-laughs) — the validator must run with DTDs disabled; a caller URI whose authority is the shell's own (confused deputy) — R5 §4 already requires "rejects any URI whose authority does not belong to the caller" (r5-live-tile-api.md line 315) but the phase doc does not carry that rule; two apps sharing a uid spoofing each other's package name in `getCallingPackage()`; a package name re-installed under a different signer after uninstall inheriting nothing (line 151 covers uninstall; say the same for reinstall). R5's rate limit (60 verbs/min) and size caps (line 314, 328) are also not in the phase doc, so the build could omit them.
- **Fix:** copy R5 §4's security summary (identity, authority-of-caller check, size caps, rate limit, kill switch) into Decisions; add the three edges; add the mandatory adversarial review note (same wording as phase 09).

### M4 — Cross-phase (01, 04, 09) — build type for the phone is unstated; a debuggable shell defeats the "adb-only" data model
- **Claim:** phase-01 E1 line 109: "`./gradlew assembleDebug` exits 0 ... `adb install -r <apk>`"; phase-09 line 35: "Only adb over USB with debugging authorised can read or write them".
- **Evidence:** nothing in any phase doc says the phone gets a release-signed, non-debuggable build. With `android:debuggable=true`, `run-as <pkg>` from any authorised adb host reads every app-private file (SMS/MMS store from 06, call history, notification cache, phase 09 memory/rules/hooks) and can attach a debugger to the process that holds the assistant, IME and dialer roles. Phase 04 additionally keeps Wireless debugging enabled for the helper (line 18), so "authorised adb host" becomes any paired host on the LAN.
- **Fix:** decide in phase 01: emulator = debug; every phone install = release-signed, `debuggable=false`, with a P-row (`adb shell dumpsys package <pkg> | grep -i debuggable` / flags). Restate phase 09's threat model to include Wireless debugging.

### M5 — Phase 02 — secondary tiles have a build task but no acceptance criterion and no edge cases
- **Claim:** line 23: "Secondary-tile API verbs, their pin-confirmation UI and publishing the Live Tile client library are added here"; build task 6 (line 37).
- **Evidence:** acceptance E1-E7 (lines 41-46) test only manual pin/unpin/folders; no row exercises `secondary.*` verbs, the confirmation UI, or the published AAR. Edge cases (lines 53-57) have nothing for: a pin request while Start is not visible / the requesting app is in the background; the user declining; duplicate `tileId`; the owner app uninstalled with secondary tiles pinned (inside a folder too); an update to the owner app changing a secondary tile's arguments.
- **Fix:** add E5 (test APK requests a secondary tile → confirmation UI → tile appears with its arguments; decline → nothing; uninstall → tiles removed) and the five edges.

### M6 — Phase 03 — nothing rules what Cortana may do from the locked phone
- **Claim:** line 29: Cortana opens from "the side key where One UI allows"; line 30-31: commands include "call or text a contact; set alarms, timers and reminders; add a calendar event".
- **Evidence:** One UI 7+ lets the side-key long-press launch a third-party assistant ("You can set it to open Bixby or other compatible third-party assistant apps", https://www.samsung.com/ae/support/mobile-devices/why-does-gemini-work-instead-of-bixby-when-pressing-and-holding-the-side-button-in-one-ui-7/), and that gesture works on the lock screen. The edge cases (lines 66-76) have no "invoked while locked" row; the Goal says "never" nothing about the keyguard. Phase 07's Goal promises glance "never weakens the real secure lock screen" — phase 03's assistant is the surface that would.
- **Fix:** add a decision + edge: which commands run while locked; the rest show a "unlock to continue" card and resume after unlock. Add P-row: side-key long-press on the lock screen.
- **Owner question (WHAT):** When the phone is locked and you long-press the side key, Cortana can be reached. (a) Read-only commands work while locked (time, weather, "what's on my calendar", open an app after unlock); anything that calls, texts, writes calendar/reminders or changes a setting asks you to unlock first. (b) Everything works while locked, like a phone with no lock. (c) Other / clarify.

### M7 — Phase 07 — the glance activation path is undecided, unlisted in depends-on and checklist, and has no "power key while glance is showing" edge
- **Claim:** line 16: "the shell's own glance surface kept on at minimum brightness"; E1 line 35: "screen-off ... activates glance per the ruling"; interview item 2 (line 25): "Android 14+ background-activity-start path (at build start)"; depends-on: [01].
- **Evidence:** PLAN line 96 places glance under "overlays via accessibility (action-center pull-down, volume panel, glance/lock-style screen)", i.e. phase 04's accessibility service, but phase 07 depends on 01 only and never mentions accessibility. Android's background-activity-start exemptions that the shell can actually hold are: "The app has the SYSTEM_ALERT_WINDOW permission granted by the user", "The app is the current Input Method Editor" and "bound by a service that has been granted permission to start background activities" (https://developer.android.com/guide/components/activities/background-starts). None appears in phase 07's Scope or checklist rows, and E1 assumes activation succeeds. Edge cases (lines 40-45) lack: power key pressed while glance is showing (must really sleep, not re-trigger glance in a loop); touches on glance must not act (W10M glance was non-interactive); Android lockdown mode; the showWhenLocked activity must finish on unlock.
- **Fix:** pick the mechanism in Decisions — (i) `showWhenLocked` + `turnScreenOn` activity started under the SYSTEM_ALERT_WINDOW exemption, with an "Appear on top" checklist row, or (ii) a `TYPE_ACCESSIBILITY_OVERLAY` from phase 04's service, which changes depends-on to [01, 04]. Add the four edges and a P-row for each exemption's survival across reboot/Device care.

### M8 — Phase 08 — a multi-GB model in the launcher process puts Home, the listener and the tile engine behind one LMK kill
- **Claim:** line 12: "within latency, memory and thermal thresholds measured on the S25 Ultra"; edge (line 46): "low memory kills the runtime".
- **Evidence:** the candidates (Phi-4-mini / Qwen3-4B / SmolLM3 at 4-bit) resident are ~2-3 GB plus KV cache; nothing in the doc says the runtime runs outside the launcher process. If it shares the process, a low-memory kill takes down the HOME activity, the NotificationListenerService, the Live Tile engine and (later) the IME and InCallService together; phase 01's "launcher crash loop" edge then becomes reachable from Cortana usage. P2 measures `dumpsys meminfo <pkg>` as one number.
- **Fix:** decide: LLM runtime in its own `android:process` with the launcher process never loading model weights; edge "runtime process killed → Start unaffected, Cortana reports and reloads"; P2 measures both processes. State the model file size and the download's storage check in Decisions.

### M9 — Phase 09 — replay tests are "checked deterministically" against a stochastic model
- **Claim:** line 16: "Every correction becomes a replay test that re-runs after any model, prompt or harness change and is checked deterministically against the corrected action, with no AI grading."
- **Evidence:** the check is deterministic; the LLM output is not unless decoding is greedy (or seeded) both in replay and in daily use. A correction that passes replay at one sample can fail live. No decision or edge covers "same utterance, different output".
- **Fix:** decide: greedy decoding (temperature 0) for action calling in both replay and live use, or replay runs N samples and requires all to match; add the edge and make E6 state the decoding mode.

### M10 — Phase 04 (design consistency only) — the privileged helper has no adversarial-review note and no handoff-authentication edge
- **Claim:** line 13-14: "a Shizuku-style privileged helper that ships inside the shell APK"; build task 1: "start, daemonise, binder handoff".
- **Evidence:** the helper is arbitrary code at the shell uid reachable over a binder handoff; phase 09 marks a lesser gate as "trust-touching ... adversarial team review is mandatory before `done`"; phase 04 has no such note. Edge cases (lines 62-67) have nothing for: another app obtaining the helper's binder (the handoff must verify the client's uid/signature, as Shizuku does); the helper accepting only an allow-listed verb set (no generic shell); the helper's shell uid also satisfying phase 09's "adb shell uid only" provider check.
- **Fix:** add the adversarial-review note and the three edges now, so R4's spike proves them too.

### M11 — Phase 09 — voice corrections and voice-created rules are an unauthenticated write into an action gate
- **Claim:** line 22-23: "Correction capture by voice ('No, I meant…')"; "Declarative rules ... creatable by voice or in the Notebook".
- **Evidence:** the security decision (line 35) protects files from other apps but not the voice channel: anyone within earshot (and, per M6, possibly from the locked phone) can store a correction that redirects "text the wife" or create a rule that rewrites parameters. Edge cases (lines 67-74) have no unauthorised-correction row; hooks are file-only "per the security decision" yet rules are voice-creatable.
- **Fix:** corrections/rules by voice only while unlocked and after a confirmation card (mirrors phase 08's confirmation ruling); add the edge; extend the adversarial review scope to the voice write path.

### M12 — Phase 01 (and 04/07 by extension) — RV9's per-value tagging is satisfied by pointer only; UNMEASURED/LOW values have no per-value NEEDS-HUMAN rows
- **Claim:** RV9 (PLAN line 174-175): "Phase docs go FINAL only with every value tagged: source, build, ± tolerance, reference ... or 'approximation' with a NEEDS-HUMAN row". Phase-01 line 107: "Values marked (R3) come from docs/plan/w10m-measurements.md"; H4: "Accept every value tagged 'approximation'".
- **Evidence:** w10m-measurements.md line 391-392 lists LOW (People tile, Glance, jump grid, two-column layout, MSN Weather app) and UNMEASURED (transparency default, easing formula, shade formula, nav-bar height, in-app turnstile, Music now-playing tile, Weather background keying). Phase 01 renders the jump grid, two-column mode, Weather app, Music tile, nav bar and easing but names none of them as approximations; one blanket H4 row is not "a NEEDS-HUMAN row" per value.
- **Fix:** add an "Approximations" list to phase 01 (and to 04 for action-center LOW rows, 07 for glance LOW rows) naming each value, the stand-in used, and its own H-row.

---

## MINOR

### m1 — Stale text contradicted by later decisions
- PLAN.md line 3: "R3 running (gates FINAL)" vs line 200 "R3 status: done 2026-09-16".
- PLAN.md line 85: "all 8 phases" — there are 9 (line 118-120).
- INDEX.md line 13: "principles P1-P3" — P1-P5 exist; line 15: "the 8 phase docs below" — 9 listed.
- PLAN.md line 109 "press tilt" (see M2).

### m2 — Phase 08 — depends-on omits phase 04 while the Goal names its toggle commands
- Line 12: "the toggle commands from phase 04"; frontmatter `depends-on: [03]`. Either add 04 or reword to "every command action that exists when this part is built".

### m3 — Phase 03 — "ship inside the APK" without recording the size or asset strategy
- Line 28: "speech and TTS models ship inside the APK (no adb push, no download)". The sherpa-onnx Kokoro archives are ~686-718 MB at fp32 (https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html); with an ASR model the APK approaches 1 GB, re-pushed on every `adb install -r`. Record the chosen variant (int8 exists), the APK size budget, and whether models stay uncompressed in assets or are copied to files at first run (double storage).

### m4 — Phase 01 — "Back on Start does nothing" is an unsourced fidelity claim
- Line 161. Windows Phone's documented model lets Back "navigate ... to the previously running application" (https://learn.microsoft.com/en-us/archive/msdn-magazine/2011/march/msdn-magazine-mobile-matters-windows-phone-navigation-the-basics); w10m-reference.md has no row for Back-on-Start. Per RV9, either source the final-release behaviour or tag it "approximation" with an H-row. A no-recents approximation exists (UsageStats last foreground app → `LauncherApps.startMainActivity`).

### m5 — Phase 01 — Open-Meteo attribution
- Line 67 picks Open-Meteo; its data is CC BY 4.0, so the Weather app needs an attribution line (about page or footer). Not in Scope.

### m6 — Phase 09 — "adb over USB" understates the surface once phase 04 keeps Wireless debugging on
- Line 35 vs phase-04 line 18. Restate: any host paired for adb (USB or Wi-Fi) reaches the provider.

### m7 — Phase 03 — exact-alarm permission not chosen
- Line 18 lists an "exact alarms" checklist row. For a sideloaded app, `USE_EXACT_ALARM` is granted at install (no Play policy applies) and the row is always green; `SCHEDULE_EXACT_ALARM` needs the user toggle. Pick one and word the row accordingly.

### m8 — Phase 05 — one speech engine, one microphone, two callers
- Voice typing (IME) and a Cortana session share the phase 03 engine in one process; no edge for voice typing invoked while Cortana is listening, or the IME's voice key while the assistant role belongs to another app (engine still ours; must not require the role).

### m9 — Phase 01 — E5 assumes an SMS role holder exists on the AOSP 36 image
- E5 (line 114) drives "the Messages tile" with `adb emu sms send`; it is not proven that the non-Google AOSP image ships an SMS app. Line 66's fixture rule covers it; name the fixture in E5 so the row is runnable as written.

### m10 — Phase 01 — Android 14+ partial photo access
- Photos edges (line 152) lack "Select photos" partial access (`READ_MEDIA_VISUAL_USER_SELECTED`): the tile cycles only the chosen subset and the checklist row should show "partial", not "granted".
