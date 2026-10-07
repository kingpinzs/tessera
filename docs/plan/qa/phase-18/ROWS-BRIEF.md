# Phase 18 QA rows — the brief every row writer follows

You write and run graded QA rows for phase 18 "Files" of metro-launcher (package app.tileshell). Work ONLY in the git
worktree /home/jeremyking/projects/metro-launcher-p18 (branch phase-18). Never touch ~/projects/metro-launcher or
~/projects/metro-launcher-p17. Under docs/plan/qa/phase-17/ you may READ and RUN drivers but never write, rename or delete
anything (phase 17 is an open gate; its evidence must not change).

## Read first
1. docs/plan/phase-18-files.md — FINAL. All of "Acceptance criteria" down to your rows (the first paragraph, the "Round 3
   floor" paragraph and Fixtures bind every row), your rows, the Decisions they cite (the entries dated 2026-10-06 at the
   TOP of Decisions are build-time rulings and re-cuts: Q-18-3, Q-18-4, Q-18-5, Q-18-6 and the agent entries below them —
   they govern over older text), and Edge cases. Struck-through text (~~…~~) is superseded.
2. docs/plan/qa/phase-18/BUILD-NOTES.md IN FULL — the carry-list: facts measured at the build that change what a row can
   assert (real paths `/storage/emulated/0/…` in lines; `viewer open` not `viewer show`; `.xyz` = chemical/x-xyz; logcat
   redacts `dat=package:`; a same-volume move has no progress; the first delete on a volume logs `bin index … rebuilt`;
   the y values relative to the 28-epx status bar; unverified legs; and so on).
3. docs/plan/qa/phase-18/scripts/p18.sh (read its header and helpers), docs/plan/qa/phase-03/scripts/lib.sh (row_begin /
   row_end / assert_* / record / ring_mark / ring_since / wake_device / fill_volume / unfill_volume / build_guard),
   docs/plan/qa/phase-18/scripts/floor_selftest.sh as a model row, and two of phase 17's rows as models of form
   (docs/plan/qa/phase-17/scripts/e4.sh, e13.sh).
4. The builders' smoke drivers — working examples of driving these exact screens (taps by tag, the notification's
   Cancel, the picker, dialogs, the chooser, the probe): docs/plan/qa/phase-18/TASK3-smoke/scripts/,
   TASK4-smoke/scripts/, TASK2-smoke/, TASK1-smoke/smoke.sh. Reuse their techniques; the rows are new files.

## The form of a row
- One driver per row: docs/plan/qa/phase-18/scripts/e<N>.sh (e4b.sh for E4b). It sources lib.sh then p18.sh, calls
  `row_begin E<N> "<title>"`, starts from the baseline (`baseline_start`: layout_restore of
  qa/phase-18/baseline_layout.json, zero `-> assigned` lines, ensure_start), makes its OWN fixtures (`files_up …`,
  `pubvol_up`, `media_up`) and restores them (`pubvol_down`, `files_down` — which asserts the device is as the row found
  it), and ends with `row_end`. A row is re-runnable ALONE. No row depends on another row's state.
- Every assertion is a real comparison through lib.sh's assert helpers (a PASS / FAIL line and an exit code); a fact
  the doc says to RECORD uses `record`. Absence assertions read a slice from a MARK taken immediately before the action.
  "Mid" = `mid_progress` saw a progress line with 0 < bytes < total (rows needing it call `files_up paced big …`).
  Dumps follow the doc's RV13 (use lib.sh's dump helpers; `gdump` for a screen that never idles).
- The row asserts what the DOC says, as re-cut by the dated entries and BUILD-NOTES. If the doc's text cannot be met and
  no dated entry or BUILD-NOTES line covers it, do NOT invent an assertion and do NOT silently drop it: assert what is
  observable, mark the leg `record` with the reason in the driver's comment, and list it in your report under "doc text
  not met" for the lead to rule.
- Evidence lands where lib.sh puts it (docs/plan/qa/phase-18/E<N>/). It is kept on disk and never committed. An earlier
  run is RENAMED (`keep_earlier_run E<N>`, which names the run and its build), never deleted, never overwritten.
- Quote real output; read exit codes from files, never through a pipe.

## What you do with a failure
- A DRIVER fault (your script, timing, a wrong selector): fix the driver, keep the failed run, re-run the row.
- A PRODUCT defect in phase 18's code: do NOT fix it and do not change any file under app/. Keep the failing run, write
  the minimal repro (the steps, the expected and the observed, the evidence file), go on to your next row, and report it.
- A defect in another phase's code or in lib.sh: the same — never fix, report with a repro.
- Different failures on retry = something structural: stop retrying that row and report.
- A row that passed stays passed: do not re-run it "to be sure" (the owner's rule: one passing run is the evidence).

## The device
export ANDROID_SERIAL=emulator-5554; PATH=$PATH:$HOME/Android/Sdk/platform-tools. AVD tileshell_fhd, 1080×2340 @ 450 dpi
(px ÷ 3 = epx), API 36, no Google account. The installed build is the gate candidate: debug APK md5
87f6eac1b1ca2a02b263da89713be01f (app/build/outputs/apk/debug/app-debug.apk, commit 80810722). Do NOT rebuild or
reinstall app.tileshell with any other APK; a leg that must reinstall (pm clear → provision.sh; uninstall → reinstall)
uses that same APK file and asserts the md5 afterwards. Never run a gradle task that rebuilds the app's APK
(`assembleDebug`); unit-test gates (`jvm_gate`) and `:testapps:*:assembleDebug` are fine.
- Only ONE row writer uses the emulator at a time. If your assignment says "wait for the emulator", run no adb command
  until the file it names exists.
- Before any large fixture check the HOST's free disk (`df -h /`): stop and report if under 40 GB.
- Never bulk-delete a huge tree with one command on the device (250,000 files crashed the platform's MediaProvider
  once); the floor's own files_down for its 10,000-file folder is proven and fine.
- If a step crashes the emulator twice, STOP and report. If it is down, relaunch with exactly
  `QEMU_AUDIO_DRV=none ~/Android/Sdk/emulator/emulator -avd tileshell_fhd -no-snapshot-load` (background), wait for
  sys.boot_completed, and say so in the report.
- When you finish: the device on Start, the gate APK installed (md5 asserted), appop MANAGE_EXTERNAL_STORAGE allow, no
  virtual disk, no QA-Files / QA-Big / .Tessera, pace prefs cleared, the baseline layout, test apps you installed
  uninstalled.

## The owner's rules (not negotiable)
- Never use codex, Gemini or any non-Opus model; do not spawn subagents.
- NEVER touch the host's audio: no pactl, no audio.sh, no hostmicon, no microphone. Rows are typed and tapped. The Music
  legs play through the emulator only.
- Do NOT git commit, git add, stash or push — the lead commits. Kill processes only by a pid you recorded, never by pattern.
- Do not edit docs/plan/phase-18-files.md, docs/plan/INDEX.md, BUILD-NOTES.md or this file.
- Drivers are script FILES; do not run long inline shell with rm in it (a safety check refuses it).
- Timestamps you write come from `date`.
- Scratch goes under /tmp/claude-1000/-home-jeremyking/340515de-6745-4b8c-a7bc-95d3c74d1c3e/scratchpad/rows-<your tag>/.

## Your report (final message, under 80 lines)
Per row: PASS / FAIL / NOT RUN, the summary line lib.sh printed (`E<N>: <p> passed, <f> failed, <r> recorded`), the
log's path, the build md5 the log is stamped with. Then: product defects found (repro each); doc text not met (each with
what you asserted instead); driver techniques or floor changes the other row writer should know; the device's end state;
anything not done and why.
