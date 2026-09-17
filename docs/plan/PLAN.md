---
project: metro-launcher
status: FINALIZED 2026-09-17 for phases 01-03 and 05-09 (phase 04 DRAFT until R4). Phase docs + INDEX.md are master; this file is the plan source.
created: 2026-09-16
---

# metro-launcher: PLAN

## Vision (Jeremy, 2026-09-16, verbatim)
"I loved the old windows 8 phone UI/UX and there are some that will run on my phone but they are
garabe. I want to be like its the real windows 8 phone ui and I want it to run on top of my
android/samsung phone. I want it to be the animated tiles and if possible I want to run it here
first then load it on my phone via apk"

Addendum 1 (verbatim): "but also a simualted version of cortona because that was bad ass to go with it"
Addendum 2 (verbatim): "(A) but I want it to truley feel like windows 10 mobile"
Addendum 3 (verbatim): "permissions is not an issue I will grant anything and everything to get this to work. Like how
samsung one ui replaces androids default UI I want to replace it with this to make it windows 10 UI because microsoft
cancled it a long time ago I dont see an issue with this and if anything they will buy it from me to make a come backj
because so far there are no choices there is only android and ios and I HATE both of them"

## Target look: Windows 10 Mobile (ruled 2026-09-16)
The reference is **Windows 10 Mobile**, not Windows Phone 8/8.1. It keeps the same idea: Live Tiles,
Metro typography, Cortana. The feel is Windows 10 Mobile's: Segoe UI type, the W10M app list with
search at the top, live folders, and a full-screen background image showing through the tiles.

## Working assumptions (not yet confirmed)
- A1. ~~Launcher only~~. Reopened 2026-09-16 by Addendum 3: Jeremy wants to replace Android's whole UI the way One UI does.
  Ruled Q6: a full W10M shell built from apps, no root.
- A2. Target device: **Galaxy S25 Ultra** (ruled Q4). A regular phone, not a foldable: 6.9" 1440x3120, 12GB+ RAM.
  It shipped with Android 15 / One UI 7; confirm the current OS version with adb when the phone is first connected.
  Emulator AVD (round 2, N-04): factory FHD+ 1080x2340 @ 450 dpi, which is what the phone shows daily. QA precondition on the phone:
  default Screen zoom and default Font size (system-drawn parts follow them; the shell does not).
- A3. Checked 2026-09-16: /dev/kvm exists and Java is installed. Missing: Android SDK, adb,
  emulator, gradle. These get installed when the build starts.
- A4. The bar is "feels like the real Windows 10 Mobile", so fidelity beats feature count.
- A5. Personal project, not Level work: no Asana.
- ~~A6. The target is the WP8.1 look~~. Replaced 2026-09-16 by Jeremy's Windows 10 Mobile ruling.
- A7. (2026-09-16) Cortana is built in layers, and each layer is permanent (Hard Rule 16). The offline
  fixed-command layer (the phrases plus the phone actions they trigger) stays for good. The on-device
  tiny LLM is added later on top of it: it handles whatever the fixed commands don't catch and calls the
  same phone actions. Nothing gets rebuilt or thrown away.
- A9. (2026-09-16) Permissions: Jeremy grants everything (notification access, accessibility, overlay, default-app roles).
- A10. (2026-09-16) Microsoft-owned names and assets (Windows, Cortana, Segoe fonts, Microsoft icons) live in one swappable
  theme/branding module. It's fine on Jeremy's own phone, and they can be swapped out if the project ever goes public.
- A11. (2026-09-16) The Weather tile is in. Amended the same day (phase 08 interview): the launcher's only internet uses are
  Weather and the one-time download of Cortana's AI model; plus visual voicemail downloads if the R4 voicemail probe succeeds (PQ2); plus a one-time OpenStreetMap Nominatim address
  lookup when a place is saved by typing an address (2026-09-17, R3D-02 ruling).
- A8. (2026-09-16) Every item in the feature list below is in the full plan. Build order is what gets
  decided, not whether an item is in.

## Feature list (Windows 10 Mobile). Exact dimensions and motion come from the research task R1.
- Start screen: Live Tiles (small / medium / wide), accent color, "show more tiles" column option,
  full-screen background image showing through transparent tiles
- Tile motion: flip, cycle, peek, iconic tiles; staggered random timing
- W10M-style Weather app inside the shell, opened from the Weather tile (added 2026-09-16, Jeremy)
- Live Tile API for apps to update their own tiles and badges, Windows-style; real unread counts where exposed (added 2026-09-16, Jeremy)
- Press feedback on Start tiles as a setting: W10M final none (default) / Windows Phone 8 tilt / P4 subtle press (Jeremy, 2026-09-16)
- App launch and return transitions
- App list: swipe left from Start, search box, New caption on newly installed apps (R3 A13), A-Z list with letter jump-grid
- Edit mode: long-press to rearrange, resize, unpin; live folders
- Bottom tile row (post-FINAL change 2026-09-17, Jeremy; P4 design, not in W10M): one fixed row of up to 6 small tiles above the nav bar, default Phone, Messaging, Camera; tiles move in and out in edit mode (phase 02)
- Settings screens in Windows 10 Mobile style (a permanent hub from phase 1; each phase adds its own page)
- Onboarding/health checklist (from phase 1; each phase adds its own permission and liveness rows)
- Start layout persistence and app uninstall/update handling (no backup, no export/import: Jeremy, 2026-09-16)
- Shell parts (Q6): W10M action center (pull-down overlay), volume panel overlay, glance screen,
  keyboard (IME), phone dialer (default dialer role), messaging (default SMS role), privileged helper for direct toggles (Q13;
  added round 3, R3F-03; its phase is named at the split)
- Cortana persona (final-release look, Q12): ring states (idle / thinking / speaking) + waveform listening state (R3F-04); voice in and out, Cortana tile,
  possibly registered as Samsung's default assistant (side-key long-press)

## Standing principles (Jeremy, 2026-09-16)
- P1. Quality over speed: "I am fine if it takes a while as long as it is good".
- P3. Phases are dev chunks, not rollouts (Jeremy, 2026-09-16): "phases are NOT software roll outs it just breaking dev up to
  make sure nothing gets missed so it dont matter what phase just as long as it gets done". Which phase owns a part is the
  agent's call (by part ownership, Hard Rule 16); only WHAT gets built and how it behaves goes to Jeremy.
- P4. Android gaps get a Microsoft-style design (Jeremy, 2026-09-16): where Android has something W10M never had, "pretend microsoft
  updated there to include that and make soemthing you think they would do in their style". The agent designs it in W10M's visual
  language; each such design is marked "P4 design" in its phase doc and gets a NEEDS-HUMAN look row. This fills Android gaps inside
  this plan; it is not the post-plan "own updates".
- P5. No Google unless required (Jeremy, 2026-09-16): "NO to anything google that I dont have to have". Prefer non-Google apps,
  services, models and runtimes wherever an equivalent exists; a Google dependency needs a stated reason in its phase doc.
- P2. All in one, seamless: "I want it all in one as much as possible and semless as much as possible". Tie-breaker in every
  phase interview: prefer the option with fewer separate apps, fewer manual steps and fewer visible Android seams.

## After the plan (Jeremy, 2026-09-16)
Once the full W10M shell is done (all 9 phases), Jeremy's own new features ("our own updates") come as a new plan. They are out of
scope for this plan, so the split must not pre-build hooks for them (Hard Rule 16 / no future-proofing).

## Android limits
Verified 2026-09-16: **Samsung removed bootloader unlocking in One UI 8** (the OEM Unlocking toggle is hidden by
androidboot.other.locked=1), and US Snapdragon models never had it. So a true OS-level replacement (custom ROM /
modified SystemUI) is **not possible on the S25 Ultra**. Sources: 9to5google.com/2025/07/26/samsung-galaxy-one-ui-8-bootloader-unlock/ ,
notebookcheck.net/Samsung-s-One-UI-8-update-appears-to-restrict-bootloader-access.1070484.0.html
This PC (checked 2026-09-16): 32 cores, 124GB RAM, **43GB free disk** (a ROM build needs hundreds of GB).

Without root, apps CAN take over: home + app list (launcher), assistant (Cortana), keyboard, phone dialer, SMS,
and overlays via accessibility (action-center pull-down, volume panel, glance/lock-style screen).
Apps CANNOT fully replace: the real lock screen security, the status bar, system permission dialogs, Samsung's own settings pages,
or the recents/app switcher (added 2026-09-16, review M3). The return-to-Start animation is the system's under BOTH gesture and
3-button navigation, and the Android 12+ cold-start splash screen is system-drawn; the launcher can only request the solid-colour
splash style (review B7, round 2 N-09). Phase 1's transition (corrected 2026-09-16 by R3 A11, whose verdict is in R3 §B, RV9): Start exit = grid scales up about
the screen centre to ~1.5x with fade, rows staggered ~30 ms top-to-bottom, 217-267 ms (ours) -> solid-colour splash (system, style
requested by us) -> app. W10M's final release used NO turnstile on launch; the earlier turnstile wording is superseded. Return animation and splash are NEEDS-HUMAN accept rows.
SUPERSEDED older note (launcher-only framing, kept for history):
- Lock screen, notification shade / action center, system nav bar (it can only hide or theme them)
- Live data on tiles needs user-granted permissions: notification access for unread counts and
  now-playing, calendar, photos. Weather needs an outside API.

## Build order (ruled; re-cut 2026-09-16 by Q9)
1. Start screen + ALL live tiles: tile engine, press feedback (Q6 setting), accent color, launch real apps with the transition,
   swipe to the app list, notification-driven tiles, Photos, Calendar, Music, Weather. Also creates the Settings hub
   and the onboarding/health checklist.
2. Edit mode + live folders + pin to Start + app uninstall-update handling (layout store is in phase 1; no backup)
3. Cortana: offline fixed commands (+ Cortana tile)
4. Action center + volume panel overlays
5. Keyboard
6. Phone dialer + messaging
7. Glance screen
8. Cortana: on-device LLM layer
9. Cortana: tuning harness (Notebook memory in Jeremy's Claude memory format + index, corrections, declarative rules,
   scriptable hooks, PC-editable files, replay tests) (added 2026-09-16, phase 08 interview Q3)

## Rulings (dated; one per answered question)
- 2026-09-16 Q1 Stack: **A. Native Android, Kotlin + Jetpack Compose.** Develop against the Android
  emulator on this Linux box, then sideload the same APK onto the Samsung.
- 2026-09-16 Scope add: **Cortana simulation** goes into the plan alongside the launcher.
- 2026-09-16 Q2 Cortana brain: **C first: offline fixed commands** (no AI, no internet). **Later: an
  on-device tiny LLM, fully offline** (Jeremy: "there is tiny LLMS that can run on the phone offline so
  we will use that but first go with (C)"). No cloud AI.
- 2026-09-16 Q3 Build order: **A. Start screen first.** Target look ruled **Windows 10 Mobile**.
- 2026-09-16 Q4 Device: **A. Galaxy S25 Ultra.** One portrait layout, no foldable handling.
- 2026-09-16 Q14 Phase 1 layout source: **withdrawn, the agent decides (P3).** Resolution: phase 1 renders a code-defined
  W10M-style default Start layout (resolved against installed apps and role holders) in the permanent small-tile-unit model.
  Pin-to-Start, unpin, move, resize and folders belong to the edit-mode part (phase 2), which adds to that same model. (2026-09-16,
  phase 01 interview: the layout store moved into phase 1 because default-tile slot assignments must persist.) Phase 1's notification-tile QA runs on the default layout's notifying apps; phase 2 extends coverage to any pinned app.
- 2026-09-16 Q13 Restricted toggles: **A. Build a Shizuku-style privileged helper into the shell's own APK** (started over
  Wireless debugging) so Wi-Fi / Bluetooth / mobile data / airplane flip directly. RV1 (one APK) holds with no exception. Its phase
  opens with a feasibility + licence check; if that fails, Jeremy is re-asked (no silent fallback). Seamless requirement: that
  phase must look for a way to restart the helper after reboot with no manual steps before settling for a one-tap restart.
  Jeremy: "I want it all in one as much as possible and semless as much as possible".
- 2026-09-16 Q12 Governing build: **C. The final Windows 10 Mobile release, with every update included** (Creators Update
  15063 / Fall Creators 15254 era; the Anniversary Update's Cortana waveform and customisable quick actions are in, since updates
  are cumulative). Jeremy: "so do (a) but make sure all updates are in it then we will make our own updates to it onces its fully done".
  R3 told the same day: final-release footage governs; 14393 footage only for elements unchanged since.
- 2026-09-16 Q11 Scaling: **A. Lumia 950 proportions.** The layout canvas is 360 epx wide mapped to the phone's actual
  screen width (px per epx = screen width px / 360), so it looks like a 950 enlarged (~13% bigger physically on the S25U)
  and the taller screen shows more rows. Derived consequence: the same at FHD+ and QHD+; Samsung's Screen zoom does not
  resize the shell's own UI. Column default is a phase 1 interview topic.
- 2026-09-16 Q10 Fidelity source: **A. Measure the NOT FOUND values from real W10M footage** frame by frame (research R3).
  Each value cites its video URL + timestamp + frame math. Anything that can't be measured is labelled an approximation.
  Jeremy still judges the feel on the phone (NEEDS-HUMAN rows). Jeremy: "it would be so amazing if it worked".
- 2026-09-16 Q9 Live-tile engine: **C. All live tiles move into phase 1** (old phase 3 merged into phase 1).
  Jeremy: "I am fine if it takes a while as long as it is good". Quality over speed is a standing principle for this project.
- 2026-09-16 Q8 Font: **A. Selawik** (Microsoft's open-source Segoe UI substitute), bundled through the swappable
  branding module (A10). Dropping in a real Segoe UI file later is possible for personal builds only.
- 2026-09-16 Q7 Build order: **A. Finish the home screen first**, in the order listed under Build order.
- 2026-09-16 Q6 Depth: **A. Full W10M shell built from apps, no root** (Jeremy: "for now at least"). A custom ROM on
  an unlockable second phone (option B) stays open as a possible later project.
- 2026-09-16 Q5 Live tiles: **A. Notification-driven for every app** plus built-in Photos/Calendar/Music, with all permissions granted. Weather added (A11).

## Review resolutions (round 1, 2026-09-16; agent design calls, Jeremy can overrule)
Full mapping: docs/plan/review/2026-09-16-triage.md
- RV1. One APK holds every part; no part depends on a separately installed app. Install via adb over USB or Wireless debugging;
  the Q13 helper additionally needs Wireless debugging pairing (round 3, R3F-01).
- RV2. Rule 16 guard: one permanent engine per part, named at the phase that introduces it. No "v1 then v2" swaps (rejects R2's Vosk->sherpa, platform TTS->Kokoro, theme->fork suggestions).
- RV3. (amended round 2, N-07) The phase 3 speech engine must be able to yield open-vocabulary text for utterances the commands
  don't match (open-only, or a grammar pass plus an open pass on the same runtime; picked in the phase 3 interview); commands are matched on the text; unmatched speech goes to a permanent handler that the LLM layer later extends.
- RV4. Cortana commands act through real Android intents; later shell parts register as those intents' handlers. Caveat (round 2,
  N-08): that only works for role-backed intents (dial/call, SMS, assist). Settings toggles can't be re-pointed by intent, so how
  Cortana handles them follows Q13. Amended (phase-doc review round 2, T-M10): after confirmation Cortana's action layer sends texts
  itself with `SmsManager` (`SEND_SMS`) and places calls with `TelecomManager.placeCall` (`CALL_PHONE`), because an SMS intent only
  opens a compose screen; the platform writes the text to the SMS provider and the default dialer shows the call, so phase 06's
  Phone and Messaging still show them once they hold the roles.
- RV5. Motion is written in-house; no motion library. Values follow RV9's source order (R1/R3). No third-party control ships its own
  un-retimed motion: MangoTile pivot/jump-grid code is either vendored and retimed from R1/R3 or written in-house (phase 1 interview).
- RV9. (round 2, codex R2R2-02) Fidelity source order: (1) R3 measurements from W10M footage and its follow-up measurement passes R6 and R7 (added by phase-doc review round 2, T-m11); (2) Microsoft W10M/UWP docs;
  (3) legacy WP7/8 toolkit numbers (R1 §3.1 tilt, §3.2 turnstile) count only as candidates. They must be CORROBORATED by R3 or
  accepted by Jeremy in a NEEDS-HUMAN row. Phase docs go FINAL only with every value tagged: source, build, ± tolerance, reference
  (video + timestamp, or doc URL), or "approximation" with a NEEDS-HUMAN row (round 3, R3F-05). Already-sourced [W10M] values from R1
  (10586/14393 era) need the Q12 "unchanged since 14393" check.
- RV10. (round 2, N-04) Neither Samsung Screen zoom nor Font size resizes the shell. Every window the shell owns (activity, overlays,
  IME, dialogs, popups) sets Density(pxPerEpx, fontScale = 1). "Screen width" = the display's portrait width
  (WindowManager.maximumWindowMetrics), never a non-fullscreen overlay's or the IME window's own bounds (round 3, R3F-08). That gives
  2 px/epx at HD+, 3 at FHD+, 4 at QHD+. Phase 1 QA proves it with wm density / wm size / font_scale changes. Jeremy can overrule
  (W10M had its own text-scaling setting; it's not in the feature list).
- RV6. Tile size is computed from column count, canvas width, gutter and edge.
- RV7. Every phase doc has "emulator" and "phone-only" QA lists plus NEEDS-HUMAN rows. compileSdk/targetSdk 36.
- RV8. The Cortana tile belongs to the Cortana commands phase (phase 3 after the Q9 re-cut).

## Review resolutions (phase-doc review round 2, 2026-09-16; agent calls, Jeremy can overrule)
Full mapping: docs/plan/review/2026-09-16-phases-r2-triage.md
- RV11. (T-M1) Motion tolerance and capture rules for every phase. Pass = |measured − value| ≤ the source's tolerance + one capture
  frame (the source tolerance is its measurement error, not an acceptance band). Touch-referenced timings run with
  `adb shell settings put system show_touches 1` (restored to 0). Hold thresholds are bracketed with one-process swipes
  (`adb shell input swipe x y x y <ms>` just below and just above the value). Every capture's fps is read with `ffprobe` and a capture
  below 55 fps is rejected; `screenrecord` stops at 180 s and falls back to 1280x720 when the encoder rejects the native size, so
  long rows are split and a fallen-back capture is not used for geometry. Timings whose tolerance is ≤ 17 ms also get a phone P-row
  from a faster capture (phase 01 P10).
- RV12. (T-M6) Every acceptance row starts from the baseline state (no root, network on, default wm size / density / font scale, no
  lock set, automatic time, default battery state) and ends by restoring whatever it changed (`adb unroot` + `adb wait-for-device`,
  `cmd connectivity airplane-mode disable`, `settings put global auto_time 1`, `wm size reset`, `wm density reset`,
  `settings put system font_scale 1.0`, `locksettings clear --old 1234`, `dumpsys battery reset`, and so on). Clock restore (round 3,
  R3T-M1): a row that moved the emulator clock sets it back while still root with `adb shell date -u $(date -u +%m%d%H%M%Y.%S)` (the
  host's UTC time), then `adb shell settings put global auto_time 1`, `adb unroot`, `adb wait-for-device`, and records
  `adb shell date +%s` within 2 s of the host's `date +%s`, because `auto_time 1` alone does not snap the clock back at once.
- RV13. (T-m2) `uiautomator dump` needs about one second of accessibility idle and prints "ERROR: could not get idle state." otherwise;
  a row retries a failed dump up to 3 times, and rows that must read a screen that never goes idle (flipping tiles, waveform) read it
  through the instrumentation APK's `UiDevice` with `Configurator.setWaitForIdleTimeout(0)` instead.
- PQ1 scope (R2-m2): Jeremy's PQ1 ruling accepts GPL-3.0 only in Cortana's voice path for personal builds. R4's "GPL = not usable"
  for the helper's pairing / TLS client and phase 06's "no GPL code" still stand. sherpa-onnx builds espeak-ng as a static library
  into the same native library that runs ASR, so the pre-release voice swap means rebuilding the speech runtime with TTS off plus a
  GPL-free TTS. That swap happens after this plan, so RV2 (no v1 → v2 swaps) holds inside the plan.

## Research tasks (agent work before the split, not questions for Jeremy)
- R1. Windows 10 Mobile fidelity reference: tile sizes and gutters, column counts, type ramp (Segoe UI),
  accent palette, animation durations and easing, app list layout. Also licensing for icons: Segoe MDL2 Assets is
  proprietary; check Microsoft's MIT-licensed Fluent UI System Icons as a legal source. Sources: Microsoft's UWP / Fluent
  design docs, archived W10M material and videos. Output: docs/plan/w10m-reference.md
- R3. (2026-09-16, Q10) Measure R1's NOT FOUND values from real W10M videos (yt-dlp + ffmpeg frame stepping).
  Output: docs/plan/w10m-measurements.md. Must be done before phase docs go FINAL. Scope extended the same day (all sent to the
  running agent): (a) check the legacy tilt/turnstile values against W10M footage (RV9); (b) record per value the build, source
  device + epx canvas, fps and tolerance (N-05); (c) phase-1 addendum: Settings page, app list rows / letter headers / jump grid,
  Weather / Photos / Calendar / Music / notification tile content layouts (N-06); (d) the final release governs (Q12).
- R4. (round 3, R3F-02) Helper feasibility + licence spike on the S25 Ultra (phone-only, standalone). Runs before the helper
  phase's interview. Proves: app_process under shell uid on One UI 8 + daemonise; binder handoff to the app; each toggle via shell
  uid; survival when Wireless debugging goes off / the app is killed; licence of the on-device ADB pairing + TLS client (GPL = not
  usable). Failure re-asks Jeremy (Q13). Earlier phases must not pre-build helper rows or pages.
  Extended 2026-09-16 (PQ2): also probe whether the helper can reach T-Mobile's visual voicemail (mstore API + GBA SIM auth);
  a failure there re-asks Jeremy about phase 06 voicemail.
  Extended 2026-09-16 (R7 §4.1.10): also probe whether an accessibility overlay can draw over Android's nav bar with gesture
  and with 3-button navigation, since W10M's open action center covered its nav bar (phase 04 P5); the result goes to phase 04's
  interview (queue item 8).
- R3 status: done 2026-09-16 (w10m-measurements.md: 7 HIGH / 10 MEDIUM / 2 LOW / 5 UNMEASURED of R1's gaps, plus legacy check and phase-1
  addendum). Start grid is laid out in physical px, so its tokens are fractions of display width; UWP element sizes are epx and
  agree across both capture devices, so Q11's 360-epx canvas stands. MSN Weather values are LOW (2015 footage only).
- R5. (2026-09-16, phase 01 interview Q2) How Windows did live tiles + badges; real unread-count sources on the S25U; a permanent
  public Live Tile API design. Output: docs/plan/r5-live-tile-api.md. Gates phase 01 FINAL.
- R2. Reuse check: open-source Metro/W10M-style launchers or tile-animation code worth borrowing
- R6. (2026-09-16, phase-doc review round 1) Second measurement pass: edit mode and live folders, keyboard, Cortana listening /
  speaking / text box / confirm flows / lock screen, the Back and Search keys, the app-list "New" caption, glance clock position and
  burn-in. Output: docs/plan/r6-measurements.md. Done 2026-09-16. Gates FINAL of phases 01, 02, 03, 05, 07 (added to this list by
  phase-doc review round 2, T-m11).
- R7. (2026-09-16, phase-doc review round 2, T-B5) Measure the W10M screens no pass covers: the Phone and Messaging apps (phase 06),
  Cortana's Notebook (phase 09), action center and volume panel motion (phase 04). Output: docs/plan/r7-measurements.md. Done 2026-09-16
  (202 rows: 38 HIGH / 114 MEDIUM / 32 LOW / 18 UNMEASURED) and applied the same day. Phases 04, 06 and 09 tag each value
  "R7 §x" per RV9, with LOW values as candidates and UNMEASURED values as approximations, each with its own NEEDS-HUMAN row. Scope
  changes from R7: phase 06 gets W10M's three-tab Phone app, full call controls and attach menu, and its voicemail list becomes a P4 design; phase 09's Notebook is a flat list;
  phase 04's action center is a finger-tracked drag, and R4 gains the nav-bar probe. Follow-up done 2026-09-17: phase 03 took the
  ≡ pane, Reminders and Settings page values first recorded in phase 09 (its R7 Decisions lines, E15, H23–H28), and phase 09 points
  to them; phase-doc review round 3 (2026-09-17, R3D-01) added §3.8, the saved-reminder card, to phase 03 (its E7). Gates FINAL of
  phases 03, 04, 06, 09 (03 added 2026-09-17 with the follow-up).

## Open questions for PLAN.md (from review round 1)
Q9-Q11 ruled 2026-09-16. From review round 2:
14. Withdrawn (P3); resolved by the agent, see Rulings.

## Moved to per-phase interviews (Stage A step 4), 2026-09-16
These only affect one phase each, so they get asked when that phase doc is interviewed (full per-phase list in review/2026-09-16-triage.md):
- Cortana's fixed command list; offline speech-to-text engine; Cortana TTS voice (the real Cortana
  voice belongs to an actress and can't be copied)
- On-device LLM runtime and model (Cortana LLM layer phase)
- Keyboard layout, dialer and messaging details (their own phases)
