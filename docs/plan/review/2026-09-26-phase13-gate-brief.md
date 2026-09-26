# Phase 13 gate review — brief (2026-09-26)

You are one of two independent reviewers of phase 13's QA gate (phased-build Stage C step 12). Judge the captured
evidence against the phase doc's acceptance criteria. You do not run the emulator; you read what the runs captured.
Everything is in `/home/jeremyking/projects/metro-launcher`, branch `main` (read-only for you: write nothing but your
report). Do not read the other reviewer's report.

## What to read

- The phase doc: `docs/plan/phase-13-fluent-materials.md` (FINAL): Goal, Scope, Decisions (the surface table, the on / off
  rule, the lights' values and sample rule), Acceptance criteria E1–E13 and their preamble (seeding, motion clock, ring
  reads, expected acrylic values, patch-mean reads, the checkerboard fixture), Phone-only P1–P4, NEEDS-HUMAN H1–H9 and
  the Edge cases list.
- Its post-FINAL changes: `docs/plan/INDEX.md`, the Change Log lines that name PHASE 13 (dated 2026-09-25 and 2026-09-26),
  the Blocked-on ledger rows L13-1 and L13-2 (both fixed) and row 13. The Change Log supersedes the doc where it says so.
  Two standing rulings from Jeremy bind this gate: **specific tests only** — an agent never runs a whole gate; a change
  is re-verified by the rows that drive the changed code (INDEX Change Log "QA RULING, ALL PHASES" and the phase 15 gate
  scope line) — and the whole gate is Jeremy's own end-of-project run.
- The evidence: `docs/plan/qa/phase-13/README.md` (the row map), then `docs/plan/qa/phase-13/<ROW>/<ROW>.txt` per row
  (every PASS / FAIL / RECORD line, and the header naming the driver blob and the APK it ran against), with its dumps,
  screencaps, ring slices and saved rings. Earlier runs are kept as `<ROW>-run<N>-<why>/`. Drivers:
  `docs/plan/qa/phase-13/scripts/` (`lib.sh` is phase 03's, symlinked).
- The code: `app/src/main/kotlin/app/tileshell/ui/fluent/` (the engine), its call sites (app list, H21 band, Music
  menus, Tess's pane and reminder menu, phase 11's satellites), `start/BackgroundDecoder.kt`, the Settings toggle, and
  `git log --oneline` for phase 13 (854e12a3, bce25b14, 365bd248, the L13-1 and L13-2 fixes, and 3448c92f).

## The builds

- apk 15aa7f5f = the build of c73c32cf: E3, E5, E6.
- apk 120e52ec = the build of f1b05c0b (the L13-2 fix): E7, E8, E9.
- apk db078d6a = the build of 3448c92f (this session's fix: the static layer only while acrylic is on): E1, E2, E4, E10,
  E11, E12, E13, BS_STEP, the edge cases, phase 03's E15.
- The INDEX Change Log (2026-09-26) states why the earlier builds' rows stand: which files changed between them and why
  those rows do not drive them. Check that claim against `git diff --name-only` yourself.

## Your lens

Reviewer A (design and correctness): does the build do what the Decisions and acceptance criteria say — the material,
the tint derivation per surface, the two backdrop sources, the on / off rule and its diagnostics, the two lights and their
sample rule, the toggle, the static layer's lifetime? Were defects fixed at their producer (3448c92f)? Is anything built
that a later phase (12, 14, 04) will trip on? Do the agent calls in the 2026-09-26 Change Log lines hold up?

Reviewer B (testability and evidence, adversarial): does each E row's and each edge case's evidence actually prove its
criterion as the doc words it? Could any assertion pass on a broken product (a vacuous check, a precondition that is
never asserted, a value read from the capture under judgement)? Are recorded clauses recorded where the doc says
"recorded", and asserted everywhere else? Are the driver re-cuts this session (E10's two measurement controls, E11 (1)'s
before / after reading of phase 03's E15, E13's strip, the corroboration tool, BS_STEP's MARK) faithful to the doc, or do
they weaken it? Is anything claimed with no evidence behind it?

Both: list, per acceptance row E1–E13 and per edge case, PASS or FAIL with the file and line that shows it. The phone
rows P1–P4 and the NEEDS-HUMAN rows H1–H9 are Jeremy's (phone and human judgement); say only whether each has the
captures it needs attached.

## Report

Write your report to `docs/plan/review/2026-09-26-phase13-gate-<a|b>.md` (the letter you were given). Findings are
BLOCKING (a criterion unmet or unproven, a product defect) or NON-BLOCKING, each with the file and line or row and
check that shows it. End with one line: `VERDICT: PASS` or `VERDICT: FAIL`. Do not edit any other file.

## Round 2 (added 2026-09-26, after round 1's triage)

Round 1's reports (`2026-09-26-phase13-gate-a.md`, `-b.md`) and the triage (`2026-09-26-phase13-gate-triage.md`) are
inputs now: read the triage and both round-1 reports. The builds changed: apk **509f6e49** = the build of **a7ef430b**
(the cancellation fix) is the current build; E1, E2, E4, E8, E9, E10, E12, E13, EDGE_RAPID, EDGE_NO_IMAGE,
EDGE_LIGHT and the B1_PROBE rows ran on it; the rest stand where the INDEX Change Log's "PHASE 13 GATE ROUND 1" line says.
For each round-1 BLOCKING finding, judge whether it is resolved by the evidence, not by the triage's word. Then re-judge
the gate as a whole under your lens: anything the fixes broke, anything round 1 missed. Report to
`2026-09-26-phase13-gate-r2-<a|b>.md`, same format, same last line.

## Round 3, the last (added 2026-09-26)

Round 2: A FAIL (one new BLOCKING: the Start picture held twice — two decodes in flight at once), B PASS. Inputs now:
both round-2 reports (`2026-09-26-phase13-gate-r2-a.md`, `-r2-b.md`) and the INDEX Change Log lines "PHASE 13 GATE
ROUND 2" and "PHASE 13 GATE ROUND 2 (B) AND AFTER". The current build is apk **0eb36905** = the build of **9c4d049f**
(concurrent decodes of one picture share one decode in flight: `start/SingleFlight.kt`, `start/BackgroundDecoder.kt`,
`SingleFlightTest`); qa/phase-13/README.md maps every row to the build it ran on and says why each earlier-build row
stands. Judge whether round 2's BLOCKING finding is resolved by the evidence (E10's new clause "the picture is held
once", EDGE_HUGE's B, the unit results and their red runs under `unit/`), whether 9c4d049f is right (sharing, a
cancelled waiter, failure and change of picture, anything it could break for phase 01's Start page), and re-judge the
gate as a whole under your lens. This is the last round: say plainly which findings, if any, still block. Report to
`2026-09-26-phase13-gate-r3-<a|b>.md`, same format, same last line.
