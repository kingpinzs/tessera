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
- 2026-09-23 night: ALL BUILD TASKS IN. Branches merged into phase-15: phase-15-rec (eee51a5), phase-15-clock (d0e4d8c),
  phase-15-calc (6e62d4a); engine 28ac69f; Tess arithmetic da1d23f. 752 app + 98 :calc unit tests; exported.py 17/17.
- QA (development checks, T15-21): E11 driver (calc_drive.py + e11.sh) running on 5556; E2 and E22 drivers written.
  QA agents: clock rows on emulator-5558 (AVD tileshell_fhd3), calculator rows E12/E13/E29 on emulator-5560 (AVD
  tileshell_fhd4); briefs in docs/plan/qa/phase-15/briefs/qa-*.md. Recorder rows wait on the EasyEffects question.
- Found while driving: Compose reports selected semantics as checked="true" on non-Tab nodes (drivers read either);
  Windows' paste of "12+3" enters 12 + 3 and needs = (OnPaste :1601-1718) — E11 asserts both halves.
- 23:2x E11 run 1 (kept as E11-run1, DEFECT.md): the Calculator's angle / HYP / memory rows never redrew after a key
  (Compose skipped rows taking only `model`); MR stayed disabled after MS and its taps were dropped. Fixed at the producer:
  CalcModel's engine getters read `tick` (0cb5c76, CalcModelObservableTest red 5/5 -> green). E11 run 2 on the rebuilt APK:
  standard 79/80 (standard-076 "mc 5 mminus mr" shows 5, engine and sequential JVM oracle say -5: a dropped UI tap,
  suspected KeyCell's composed `live` gate lagging the engine by a frame — to measure by hand when 5556 is free),
  scientific 87/87, programmer running. Calc QA agent told to reinstall the rebuilt APK.
- Drivers written, not yet run: e0.sh (+ tiles.py), e1.sh, e26.sh (typed half runs now; spoken steps FAIL as NOT RUN while
  audio.sh check fails). E26's six utterance wavs built with a private venv (scratchpad/p15/venv: sherpa-onnx 1.13.8,
  soundfile 0.14.0, numpy 2.4.6 — the versions phase 03's venv used).
- 2026-09-24 (after a login expiry that stopped both QA agents; both resumed by SendMessage):
  E11 run 2 (kept, E11-run2/DEFECT.md): paste crashed the shell process (CopyPaste's UNICODE_CHARACTER_CLASS rejected by
  Android's regex; fd9745d) and MR's tap was dropped one frame after M- enabled it (KeyCell's composed gate; 15/30 -> 0/30;
  01e0f11). E11 run 3: 241/0/2 recorded. E2 12/12. E25 (clean git-archive builds of 298a9d7 and HEAD): +2,405,333 bytes,
  dex +2,401,352, no new entry >= 1 MB (dex shards aggregated; run 1 kept). The earlier "+4.68 MB" was incremental-packaging
  holes in the working APK. All in 4c5347b.
  E0 run 1 (kept, E0-run1/DEFECT.md): Voice Recorder ignored a page request while running (no launchMode; bf1b2a5), a
  deleted timer's notification stayed posted (22f9209); driver faults fixed (paused plan per phase 10, api_timer name with
  a space — clock.sh's owner told). Alarms & Clock had no icon (app list drew the shell's; shortcuts iconless, phase 11
  satellites would be blank): ic_clock / ic_clock_glyph = the tile's bell, Fluent alert_20_regular (05e9169).
  AVD 5556 baseline additions: Auxio music source = System (set once in Auxio's dialog, Music sources -> System -> Save).
  Drivers written: e0.sh, e1.sh, e26.sh, e27.sh (burst half FAIL/NOT RUN until the rebase; at the gate its burst steps
  must be written — the driver says NOT WRITTEN if phase 11's burst is in the APK), tiles.py, e25.sh.
- 2026-09-24 later: E1 run 4 47/0 (runs 1-3 driver faults, kept; phase 01's e12_part1.sh walk now swipes slowly — a
  300-ms swipe flung 1562 px and skipped a row; p15.sh gdump dropped --no-restart). E26 run 2 82/8 (all 19 typed tess
  lines pass; the 8 FAILs are the spoken steps, NOT RUN on the audio route). Calc QA agent done: E12 91/0, E29 54/0/1,
  EDGE-CALC 51/1/6 (paste "abc": Windows' "Invalid input" vs the doc's "ignored" — doc-vs-Windows item for Jeremy),
  E13 136/9/7 (ink vs box) -> builder agent dispatched (briefs/calc-fix.md, emulator-5560: E13 ink, tagged-node text,
  date-picker drag). Committed through 52fdb9c. OPEN for Jeremy, one at a time: (1) EasyEffects blocklist (audio rows
  E9 E14 E15 E17 E18 E19 E26-spoken E30); (2) E0-run2: MusicFeed vs TileNotificationListener share pkg keys (phase
  01/10 parts); (3) doc-vs-Windows corrections (Lsh, BYTE HEX FF+1, add/subtract weeks, converter source; + paste).
- 2026-09-24 ~13:xx: HOST AUDIO BLOCKER CLEARED (Jeremy: "A, edit the EasyEffects config", then "A, restart it now").
  ~/.var/app/com.github.wwmm.easyeffects/config/easyeffects/db/easyeffectsrc [StreamInputs] blocklist=ee-calib-raw ->
  ee-calib-raw,qemu-system-x86_64 (backup scratchpad/p15/easyeffectsrc.before); service restarted with flatpak kill +
  the autostart's own command (flatpak run ... --service-mode --hide-window); the line survived. FOUND: the desktop's
  default source had been vmic.monitor since 2026-09-23 17:47 (phase 03's audio.sh setup with SINK=vmic, run by
  provision.sh). Restored to the BRIO (alsa_input.usb-046d_Logitech_BRIO_9130DEA5-03.analog-stereo — Jeremy's documented
  default; the saved .audio-state.prevsource named a node that no longer exists, rewritten to the BRIO). The default
  change dragged every qemu capture stream onto the BRIO; moved back: 5554 -> vmic.monitor (its phase 03 route),
  5556 -> vmic5556, 5558 -> vmic5558, 5560 -> vmic.monitor. p15.sh now exports AUDIO_SINK=vmic<port> (T15-30: my
  drivers had been using the shared vmic). HAZARD for future provision.sh runs: audio.sh setup with SINK=vmic sets the
  desktop default source to vmic.monitor again — restore the BRIO afterwards.
- 2026-09-24: E26's 8 remaining FAILs are the reply-AUDIBILITY half of phase 03's pass rule (capture of the emulator's own
  output stream reads ~-115 dBFS; that stream is routed through EasyEffects' output processing, which re-routes it back if
  moved; dumpsys audio shows Tess's USAGE_ASSISTANT track for every reply; the reply TEXT checks all pass). Asked whether to
  add qemu-system-x86_64 to EasyEffects' [StreamOutputs] blocklist; Jeremy: "we are good here move on". Output side left
  unchanged; the audibility assertions stay as FAILs with this reason (not bent).
- 2026-09-24 ~12:30: REBASED onto main (Jeremy: "if you had anything waiting on phase 11 they are done"; main's INDEX still
  shows row 11 "blocked (L11-1)" and the phase 11 session is still committing its gate — went on Jeremy's word). Backup ref:
  branch phase-15-prerebase-2026-09-24 (6b4a023). Conflicts resolved: INDEX Change Log lines (union, main's first);
  MusicFeed.kt (main's L11-1 arbiter + my component routing: publishRoute() sends another app's slot through
  LiveTileEngine.publishPackage(pkg, PackageSource.MUSIC, …) and a shell app's component key straight to publish();
  main's forget listener follows publishedRoute); StartPage.kt (main's entryOf + my growthKeyOf/componentOf); the
  linearised merges re-resolved (Glyph.kt duplicates dropped, strings.xml, exported-allowlist union). The cherry-picked
  ring helpers were skipped as already applied. HEAD 3e04aa3 on main 4769695; app unit tests 796/0 (:calc up to date,
  98/0). Main then gained aefe4b9 (phase 11 QA only). My open E0 question is settled by main's L11-1 fix (Jeremy ruled
  "(a)" there). GATE started: 5556 rows E2 E27 E0 E1 E9 E26 E11 (scratchpad/p15/gate5556.sh), then E25 (clean archives:
  main aefe4b9 333,308,001 vs HEAD 335,763,998 = +2,455,997) and E22 last. Agents resumed on the rebased build: clock rows
  (5558, re-run everything on this build), recorder rows (5560, E15 onward + E14 re-run).
- 2026-09-24 afternoon: both Fable QA agents hit the Fable usage limit. Recorder rows continue with an Opus agent on 5560
  (from recorder-qa-state.md); clock rows run by scratchpad/p15/runner.sh on 5558 (the clock agent's drivers). Found and
  fixed: the recorder list stayed stale after background changes (807a664); the rebase dropped merge-resolution content
  (the allow-list's calculator/recorder lines, a Glyph.kt comment) — restored from phase-15-prerebase-2026-09-24 after a
  full per-file comparison (ae5fd76); E27 burst detection (pipefail + grep -q) and E9's timer format (driver).
  Pass 2 on 5556, build 7b1306f3: E2 12/0, E27 80/0 (burst half included), E0 53/0, E1 47/0, E9 32/0, E11 241/0/2,
  E26 111/8 (audibility, host). E25 2/0 (+2,455,997). emulator-5558 powered itself off at ~13:34 (cause not found;
  nothing in the harness shuts an emulator down); relaunched with -allow-host-audio, capture moved to vmic5558, the
  clock agent's leftover "Dead" timer deleted. gdump unified in p15.sh (the clock pass's new-file + fallback fixes,
  no --no-restart, the right windows key) — 341e94b. Main moved again (9f790b7: the L11-1 forget race, app code):
  a final rebase is due before the final gate pass.
- 2026-09-24: Jeremy: "mute the emulators". Every qemu-system-x86_64 output stream muted on the host (pactl
  set-sink-input-mute <id> 1; streams 1366=5554, 3186=5556, 3299=5560, 5839=5558). Undo: the same with 0. Inside the
  AVDs nothing changes (rings still start; rows read dumpsys audio). Reply-audibility captures were already silent.
- 2026-09-24 ~15:20: FINAL REBASE onto main 9f790b7 (backup phase-15-prerebase2-2026-09-24): one conflict (MusicFeed:
  main's once-per-process forget registration + the forget's republish-null kept, on my publishedRoute); a full
  per-file comparison with the backup found no dropped content. 796 app / 98 :calc unit tests pass; build 2356b99f.
  Found since pass 2: the clock pass's EDGE_ALARMS ran on as an ORPHAN after its agent died and drove 5558 alongside
  my runners (the source of the leftover alarms; committed with NOTES.md). Busy card shows the sentence alone
  (1544a1b). e21.sh gained the Voice Recorder permission parts (the recorder pass had left them to e21.sh's owner).
  FINAL GATE started on 2356b99f: 5556 (E2 E27 E0 E1 E9 E26 E11), 5558 (clock rows, scratchpad/p15/clock_reset.sh
  before each row), 5560 (E12 E13 E29 EDGE-CALC, then the recorder rows, E14b last); then E25 and E22, the reviewers.
- 2026-09-24 evening: gate pass on 2356b99f. 5556: E2 12/0, E27 80/0, E0 53/0, E1 47/0, E9 32/0, E11 241/0/2,
  E26 111/8 (the reply-audibility checks, host side, accepted). 5560: E12 91/0, E13 145/0, E29 54/0, EDGE-CALC 51/1
  (the paste item; settled below), E14-E20, E24, E30, E14b all pass. 5558: E4 89/1, E6 40/1, E7 12/3, E8 33/1, E10
  127/5, E31 60/3, E21 42/2, EDGE_ALARMS and EDGE_STOPWATCH fail. Three clock product defects fixed (70a3612 lap text,
  c019ad4 list padding, 8e821d7 "Timer ended while the phone was off"); build 33cc71c1. The clock continuation agent
  (Opus, briefs/qa-clock-2.md) re-runs every clock row on 5558 against 33cc71c1 and triages the rest.
- 2026-09-24: Jeremy's ruling on the doc-vs-Windows differences: "what ever is right and gives the right answer. dont
  includ flaws in anything". Shifts of at least the word size now give 0, and an arithmetic Rsh of a negative gives -1;
  "Result not defined" is no longer reachable. Thirteen converter factors changed to their units' exact definitions;
  an exact-rational audit (scratchpad/p15/conv/audit.py) found the other 116 real-unit factors already exact. BYTE
  255, weeks as 7 days and the "Invalid input" paste stay as Windows (Windows is right there). The Pyeong "400.0" was
  my own misreading of an extraction; the port already had 400.0 / 121.0. calc-cases.tsv regenerated (314 cases);
  :calc 99/0, app 800/0; the new build is c45e7a28 (calculator-only changes). The E11, EDGE-CALC and E12 (converter)
  rows must re-run on the final build. INDEX Change Log line "WHERE WINDOWS IS WRONG".
- 2026-09-24 ~17:30: Jeremy asked for everything to go up before the week's tokens run out, and created the push flag.
  Branch phase-15 went to origin (github.com/kingpinzs/tessera) at 8d78ff3 as a new branch; the remote head was
  verified equal to local. main was NOT sent (54 ahead of origin, the phase 11 session's). 8d78ff3 carries the clock
  pass as an in-progress snapshot (it was on EDGE_ALARMS). Calculator rows on c45e7a28 (5560): E11 243/0/2, E12 91/0,
  EDGE-CALC 52/0/5. Next: the clock agent's report, then the final gate on one build, the reviewers, NEEDS-HUMAN.md
  and row 15.
- 2026-09-24 ~19:40: the clock continuation pass (Opus) reported: every clock row passes on 33cc71c1 except two product
  defects, and I fixed both. EDGE_STOPWATCH: a new lap landed above the viewport once the list was full (6865208). E10:
  the timer editor's splits (cbc15ea), and three texts placed by box where r11 measured ink (ed2bba8). The last needed
  two fixes to the Calculator's ink placement: the node reports the drawn box, and translucent text is measured opaque
  (0e3695a). E7's lap line was 1 ms off the stored lap (5fe3741). On 87ddb3ef / 4ffe58a7: E10 133/0/1, E7 31/0,
  EDGE_STOPWATCH 17/0, E13 145/0/7. Found: CapMetrics' cap-top model is wrong shell-wide (Compose trims the first line's
  leading, so text sits 0.7-2.6 epx high by style). Asked Jeremy A (fix now, shell-wide) / B (plan after) / C (leave);
  waiting. The final gate waits on the answer. NEEDS-HUMAN.md written (P1-P7, H1-H27 with evidence pointers).
- 2026-09-24 ~20:10-22:00: Jeremy "(A) fix then push". CapMetrics fixed shell-wide (4e25ffa), and phase-15 was pushed at
  6d7ce85 (origin had deleted the branch after PR #1 merged it into main at 8d78ff3). FINAL GATE on build 4b7ac321: 5556
  all pass except E26's accepted 8; 5558 all pass (E4b, E23, EDGE_ALARMS and EDGE_STOPWATCH failed under three-emulator
  load and passed alone); 5560 all 14 pass; phase 03 E7 / E15 failures predate the fix; E25 +2,455,997 bytes. E22 had
  never checked anything: its table sat in its own evidence dir, which the runner rotates. Fixed (c2b4125), it found
  real gaps: about 25 of the doc's Edge cases have no driver (the recorder's, alarm-while-X, USE_EXACT_ALARM, the
  locked picked sound, the app list at HD+, 4 Tess cases). Three edge agents were dispatched (briefs/qa-edge-*.md): R on
  5560, C on 5558, T on 5556. The Fable dispatch hit its limit at once; they were re-dispatched on Opus. Next: their
  reports, fixes for any defects, E22 on the final build, the two gate reviewers, INDEX row 15, then the push (flag).
- 2026-09-24 ~23:00: Jeremy reported the timer numbers do not scroll correctly: LoopSpinner fixed (9a34ae1; blank
  rows on a long drag, taps from a stale value, a roll that could not be caught), proved by EDGE_SPINNER (the old build
  fails exactly there, the test build b466469e passes 11/0). EDGE_TESS passes 57/0/5 (4b7ac321). Then Jeremy: "this
  takes hours and hours so lets cut the testing for now and push the changes". The clock (5558) and recorder (5560)
  edge agents were stopped mid-work; their drivers and evidence are committed as an unverified snapshot. The emulators
  were checked afterwards: no stray process, no alarm or timer armed, default wm size and appops, 4b7ac321 on 5558 and
  5560, b466469e on 5556. INDEX row 15 is QA PAUSED, not done. To resume: build the final APK in the worktree, install
  it on all three, finish the four clock edge rows and the two recorder edge rows, run the whole gate on that one build,
  then E25 and E22 last, the two reviewers (review/2026-09-24-phase15-gate-brief.md), and NEEDS-HUMAN sign-offs.
- 2026-09-25: Jeremy: "DO NOT re run the whole gate just the specific tests" (saved to memory), then "kill all testing".
  Built the final APK 61c5b610 (HEAD f785c8b plus nothing; spinner fix in; unit tests pass) and installed it on 5556,
  5558 and 5560. The planned specific rows were E3 E10 E31 E33 EDGE_ALARMS EDGE_TIMERS EDGE_WORLD (the rows that drive
  LoopSpinner), EDGE_SPINNER, the six unfinished edge rows, E21 and E22. Of those, only EDGE_SPINNER was started. Its
  runs on 61c5b610 did not complete: 5556's system_server restarted mid-run (cause not found in its logs; the emulator
  had been up about 39 h). After that its display froze (screencaps hung, BACK did nothing), and an orphaned screencap
  held the harness's device lock. The runs were stopped by pid and 5556 was rebooted. EDGE_SPINNER's driver now sets
  its baseline (the Clock reopens on the page it was left on) and asserts its restore. ALL TESTING STOPPED at
  Jeremy's request; nothing is running. The spinner fix is proved on test build b466469e (EDGE_SPINNER-run2 / -run3:
  11/0), not yet on 61c5b610.
