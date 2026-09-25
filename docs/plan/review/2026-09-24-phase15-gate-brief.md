# Phase 15 gate review — brief (2026-09-24)

You are one of two independent reviewers of phase 15's QA gate (phased-build Stage C step 12). Judge the captured
evidence against the phase doc's acceptance criteria. You do not run the emulators; you read what the runs captured.
Everything is in the worktree `/home/jeremyking/projects/metro-launcher-p15`, branch `phase-15` (read-only for you:
write nothing but your report).

## What to read

- The phase doc: `docs/plan/phase-15-inbox-clock-calculator-recorder.md` (FINAL). Its Decisions, its QA evidence list
  (E0–E33, EDGE-*), its Edge cases, its phone-only P rows and its NEEDS-HUMAN rows.
- The doc's post-FINAL changes: `docs/plan/INDEX.md`, Change Log lines that name PHASE 15 or the cap-top fix. They
  supersede the doc where they say so (for example "WHERE WINDOWS IS WRONG" and "SHELL-WIDE CAP-TOP FIX").
- The evidence: `docs/plan/qa/phase-15/<ROW>/` per row, with `<ROW>.txt` (every PASS / FAIL / RECORD line, and the
  header naming the APK it ran against), dumps, screencaps and ring slices. Earlier runs are kept as `<ROW>-runN`, with
  their `DEFECT.md` where one was written. `docs/plan/qa/phase-15/STATE.md` is the session's running log. The drivers
  are in `docs/plan/qa/phase-15/scripts/`.
- The human-only list: `docs/plan/qa/phase-15/NEEDS-HUMAN.md`.

## The final build and its gate

- The build is `app-debug.apk` md5 4b7ac321… (the row headers' "apk installed"; their "apk built" line is the same
  file's sha256), from commit 4e25ffa. HEAD 6d7ce85 differs only in comments.
- On that build: emulator-5556 ran E2 E27 E0 E1 E9 E26 E11; emulator-5558 the clock rows E3 E4 E4b E5 E6 E6c E7 E8
  E10 E23 E31 E32 E33 E21 EDGE_ALARMS EDGE_STOPWATCH EDGE_TIMERS EDGE_WORLD; emulator-5560 E12 E13 E29 EDGE-CALC E14
  E15 E16 E17 E18 E19 E20 E24 E30 E14b. Then E25 (the APK budget) and E22 (diagnostics coverage).
- Four clock rows failed while all three emulators ran at once (E4b's toast settle 235 vs 217 ± 17 ms, E23's
  ring_surface missing from one dump, EDGE_ALARMS' heads-up step, EDGE_STOPWATCH's dump fallback) and passed when re-run
  alone on the same build. Both runs are kept: the loaded run is the `-runN` just below the current dir. Judge whether
  those failures are what the lead says they are (the dump tool and timing under load) or a product fault.
- E26 keeps 8 reply-audibility FAILs, accepted by the owner for a host-audio reason (INDEX Change Log / STATE.md).
- Phase 03's E7 and E15 (`docs/plan/qa/phase-03/`) were re-run on this build because the cap-top fix touches phase 03's
  text. Their failures predate the fix (compare with their `-run1`); phase 03 is not being closed here.

## Your lens

Reviewer A (design and correctness): does the build do what the Decisions and acceptance criteria say? Were defects
fixed at their producer, not worked around? Do the fixes this session (the commits since 8d78ff3:
`git log --oneline 8d78ff3..phase-15`) hold up: the stopwatch list, the timer editor split, the ink placement
(`calculator/CalcInk.kt`), the stopwatch log, the shift semantics and converter factors, the cap-top model
(`ui/tokens/CapMetrics.kt`)? Anything a later phase would trip on?

Reviewer B (testability and evidence): does each E row's evidence actually prove its acceptance criterion? Is every
Edge case exercised with evidence? Are any criteria asserted by the driver in a way that cannot fail, or recorded where
they should be asserted? Are the flake explanations supported by what the logs show? Is anything claimed with no
evidence behind it?

## Report

Write your report to `docs/plan/review/2026-09-24-phase15-gate-<fable-a|fable-b|codex>.md` (the name you were given).
List findings as BLOCKING (the gate cannot pass: a criterion unmet or unproven, a product defect) or NON-BLOCKING, each
with the file and line or row and check that shows it. End with one line: `VERDICT: PASS` or `VERDICT: FAIL`.
Do not edit any other file.
