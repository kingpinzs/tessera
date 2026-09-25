# Phase 15 edge-case QA — the rules every edge agent follows (2026-09-24)

The final gate found that several of the phase doc's Edge cases have no driver (Hard Rule 5: edge cases are part of the
spec and QA executes them). You are one of three agents filling those gaps, each on its own emulator. Your own brief
names your emulator and your cases; this file is what all three share.

## Where things stand
- Worktree `/home/jeremyking/projects/metro-launcher-p15`, branch `phase-15`. The spec is
  `docs/plan/phase-15-inbox-clock-calculator-recorder.md` (FINAL): read its Acceptance preamble, "Harness contracts",
  the Decisions for your app, and the **Edge cases** section. The INDEX Change Log lines naming PHASE 15 supersede the
  doc where they say so.
- The FINAL build is `app/build/outputs/apk/debug/app-debug.apk`, md5 4b7ac321ce4d0ede, ALREADY installed on all three
  emulators with the home activity set. **Never rebuild the worktree's app** (no gradle in the worktree): E22 counts
  rows by that exact file's sha. A QA-only variant, if your brief needs one, is built from a `git archive` in your own
  scratch dir (see `scratchpad/p15/e25build.sh` for how, including the git-ignored inputs it copies).
- The harness: `docs/plan/qa/phase-15/scripts/` (`lib.sh`, `p15.sh`, `clock.sh`, `rec.sh`) and the existing drivers.
  Write your driver there as `edge_<name>.sh` with `row_begin <ROW> "<title>"` … `row_end`, in the existing drivers'
  idiom: `assert_eq` / `assert_within` / `assert_contains` / `record` / `note`, `ring_mark` then `ring_since "$MARK"`,
  dumps with `gdump` or `dump_ui`, `screencap`. Evidence goes under `docs/plan/qa/phase-15/<ROW>/`.
- The runner: `/tmp/claude-1000/-home-jeremyking/5d5ffc39-5a2a-4c33-953f-07d71da27a45/scratchpad/p15/runner.sh SERIAL
  TMPDIR SUMMARY driver.sh…` keeps a row's previous evidence as `<ROW>-runN` and writes one summary line per row.
- Diagnostics: a line your case must produce (the doc's Harness contracts give each form) is saved in a ring slice
  inside the row dir, named `ring_<what>_<ring>.txt` (ring = launcher, speech or recorder), taken with `ring_since
  "$MARK" [ring] > "$ROW_DIR/ring_<what>_<ring>.txt"` right after the action. The in-app ring is bounded: a slice saved
  only at row_end can miss an early line. E22 reads those slices.

## Rules that are not negotiable
1. Every assertion must be able to FAIL: a node's own text by its tag, a dumpsys line, a MediaStore query, a pulled
   file's analysis, or the ring slice from a MARK taken just before the action. A row with zero assertions fails.
2. Every row starts from the baseline, asserts the state it starts from, and restores what it changed (RV12): alarms,
   timers, takes, fixtures, permissions, appops, settings, `wm size`, the installed APK. Say what you left.
3. Run every driver for real on YOUR emulator only, with `ANDROID_SERIAL` set on every adb and driver call and `TMPDIR`
   set to a private dir. p15.sh derives `AUDIO_SINK=vmic<port>` from the serial. **Never touch emulator-5554.** Never run
   `audio.sh setup` or `provision.sh` without `AUDIO_SINK` set. The emulators' output is muted on the host: read rings
   from `dumpsys audio`, as the existing drivers do.
4. A failed run is kept (the runner renames it) and fixed only where the DRIVER was wrong. If the PRODUCT is wrong,
   write `<ROW>/DEFECT.md` (commands and output), report it, and add no workaround.
5. Something the AVD cannot do is NOT RUN, recorded in the row with the exact reason. Never fake it.
6. Kill processes only by recorded pid, never a pattern kill; leave nothing running when you finish. Read exit codes
   from files, never through a pipe. `adb shell` re-joins its arguments: pass a command with spaces as ONE string. A
   `while read` loop that runs adb inside must read its list on another fd (`done 3< file`, `read … <&3`).
7. Never commit, never edit app code, never edit anything outside `docs/plan/qa/phase-15/scripts/` (your new drivers
   and any shared-helper fix you must make, said in your report) and your rows' evidence dirs.

## Report back (final message, under 90 lines)
Per row: its final summary line on build 4b7ac321, each FAIL or NOT RUN with its reason and whether it is product or
driver. Every product DEFECT (row, repro, one line). The diagnostics lines each row logged (for E22). The state you
left the emulator in, and that no process of yours is running.
