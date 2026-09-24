# Phase 15 session state (parallel worktree) — read this first if the conversation is lost

INDEX.md is master (Hard Rule 12); this file only records where THIS session is.

## Isolation (done 2026-09-23 17:47-17:52 MDT)
- Worktree `~/projects/metro-launcher-p15`, branch `phase-15`, from main 298a9d7. Never edit `~/projects/metro-launcher`.
- Ignored build inputs copied from the main checkout: local.properties, app/libs/, app/src/main/assets/{keyboard,speech}/,
  the five ignored files in app/src/main/assets/licenses/. Not copied: keystore.properties (release signing only),
  docs/plan/r11/src/ (R11's source screenshots, not a build input). `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- AVD `tileshell_fhd2`: created with avdmanager from system-images;android-36;default;x86_64, then tileshell_fhd's
  config.ini copied over it (sorted diff: identical — 1080x2340, 450 dpi, 8 GB RAM, 16G data). Started detached:
  `emulator -avd tileshell_fhd2 -port 5556 -allow-host-audio`, pid in the session scratchpad (p15/emu5556.pid).
- Every adb / driver call: `ANDROID_SERIAL=emulator-5556`, `TMPDIR=<scratchpad>/p15/tmp` (lib.sh's device lock is private).
- provision.sh on emulator-5556: rc=0, "OK: a tap opens Cortana". Setup wizard: this build has NO wizard (phase 12
  unbuilt; `grep -rli wizard app/src/main/kotlin` empty) and Android reports device_provisioned=1 /
  user_setup_complete=1, so there was nothing to finish.
- Shared-host hazards: the AVD microphone is the host's default PulseAudio source (vmic.monitor), shared by both
  emulators; audio.sh `record` captures the default sink's monitor (both emulators' output); `emulator_sink` takes the first
  qemu sink-input. Microphone / reply-audio rows must check emulator-5554 is silent and re-run `audio.sh setup` first.

## Stage A round 3 (phase 15 only)
- Reviewer roster: codex MCP not loaded; codex CLI rc=1 "You've hit your usage limit … try again at 10:50 PM"; so
  fable + fable (design/correctness; testability/evidence). Brief: docs/plan/review/2026-09-23-phase15-r3-brief.md.
- 17:53: both Fable reviewers died at the Fable limit (HTTP 429, no report written). Re-dispatched on Opus under Jeremy's standing Opus approval (2026-09-16). Roster for INDEX: opus + opus (codex CLI limited until 22:50, Fable 429).
- Reuse-check prep (read-only reference clone; the hook requires personal clones under ~/workspace): microsoft/calculator
  at 4fd3fc53bade573ea2ac1e1ebad9bf603713f85e (2026-08-25) in ~/workspace/calculator. Engine: src/CalcManager (C++,
  Ratpack/ + CEngine/, ~14.4k lines). The ViewModels are C# now: the unit tables are
  src/Calculator.ViewModels/DataLoaders/UnitConverterDataLoader.cs (the doc's "CalcViewModel's UnitConverterDataLoader.cpp"
  is the old C++ path) and the date engine is src/Calculator.ViewModels/Common/DateCalculator.cs.
- 18:2x: Reviewer 2 (testability) done: review/2026-09-23-phase15-r3-testability.md, BLOCKING 7 · SHOULD-FIX 15 · NOTE 3.
  Verified by me: `pactl list source-outputs` → BOTH qemu capture streams (5554 pid 388227, 5556 pid 3728035) are on source
  374 easyeffects_source (the desk mic chain), NOT vmic.monitor, and they share ONE module-stream-restore rule
  ("source-output-by-application-name:qemu-system-x86_64") — moving 5556's stream can re-target 5554's next stream. Do not
  move anything until an audio row needs it; the triage decides the mechanism.
- 18:5x: Reviewer 1 (design) done: review/2026-09-23-phase15-r3-design.md, BLOCKING 4 · SHOULD-FIX 16 · NOTE 5.
- Triage review/2026-09-23-phase15-r3-triage.md: 41 items (10 BLOCKING), all applied; 0 questions. Phase 15 FINAL.
  Cross-doc: phase 12 why table + E14 instances (C-34), phase 13 task 7 drops record (C-33), build-prompt trust list
  (C-35). INDEX: row 15 + one Change Log line only (the shared reviewers line left alone to avoid a rebase conflict; the
  roster is in row 15 and the Change Log line).

## Stage C (build)
- Order: task 0 (routing) → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9. APK size before task 0 recorded on the branch (development
  record; E25's gate value is taken on the rebased main).
- Gate = full run on rebased main (the lead). On the branch: development checks only. Phase 11's lib.sh helpers: cherry-pick
  from local main when that commit exists (git log main -- docs/plan/qa/phase-03/scripts/lib.sh), never copy.
- Commits so far on phase-15: 14a8ac5 (r3 reports), 36d3d2a (triage + FINAL), 371a789 (task 0 routing), b90b2c7 (tasks 2-3
  clock back end + ring), 0c5016f (task 5 part 1 Tess alarm/timer in-process), 94d5973 (cherry-pick of main's ea37bb0:
  phase 11 ring helpers), 6970aff (task 1 part: applist_name tag, phase 10 allow-list lines).
- Parallel agents (background): calc engine (:calc, app.tileshell.calc.engine), converter+date (:calc .convert/.date),
  calc-cases.tsv oracle (host Python, independent), Voice Recorder (worktree ~/projects/metro-launcher-p15-rec, branch
  phase-15-rec), Alarms & Clock UI (worktree ~/projects/metro-launcher-p15-clock, branch phase-15-clock). Briefs in
  docs/plan/qa/phase-15/briefs/. Calculator UI agent: dispatch once the engine lands. The app does NOT depend on :calc yet
  (re-add implementation(project(":calc")) at the Calculator merge).
- emulator-5556 baseline additions made by hand: notification listener allowed (phase 01's final_rows.sh:8 form;
  provision.sh does not grant it); USE_FULL_SCREEN_INTENT + SYSTEM_ALERT_WINDOW allow (now in provision.sh).
- Dev-check facts: ring path verified in use (overlay) and locked (occluded keyguard, wallpaper beneath); status bar now
  hidden by RingActivity (re-verify at E4). After am force-stop, the first launch gets LOCKED_BOOT_COMPLETED + BOOT_COMPLETED.
- 19:3x HOST AUDIO BLOCKER for microphone rows (E14 E15 E17 E18 E19 E26-spoken E30): EasyEffects (flatpak, service mode,
  config dir created 2026-09-23 09:26) processes ALL input streams; its StreamInputs blocklist holds only ee-calib-raw, so
  both emulators' qemu capture streams are pulled onto easyeffects_source (the BRIO "Mic-Clean-Voice" chain) and a
  pactl move-source-output is undone within seconds. audio.sh check now detects it (rc=1). Fix needs the owner's OK:
  add qemu-system-x86_64 to EasyEffects' input blocklist (~/.var/app/com.github.wwmm.easyeffects/config/easyeffects/db/
  easyeffectsrc [StreamInputs] blocklist=ee-calib-raw,qemu-system-x86_64, or its UI). Not changed. Ask before the audio rows.
- 19:4x Opus weekly limit hit (resets 2026-09-27 07:00 Boise) — engine, recorder and clock-UI agents stopped mid-work.
  Re-dispatched all three on Fable as continuations. Engine: ~5k lines in :calc engine, not compiling at stop. Recorder:
  3 commits on phase-15-rec + uncommitted files. Clock UI: nothing written.
- Committed 42111f6 (converter + date), ca74032 (calc-cases oracle, 312 cases). lib.sh gains record / fill_volume /
  unfill_volume / push_ + remove_fixture_recordings (fill measured on the unrooted /sdcard FUSE view: ~170 MB less than
  /data/media); audio.sh per-emulator (AUDIO_SINK, move, check, per-stream record).
