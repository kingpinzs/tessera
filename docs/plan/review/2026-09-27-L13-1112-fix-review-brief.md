# L13-11 and L13-12 fixes: review brief (2026-09-27)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently and do not
read the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger rows L13-11 and L13-12 in `docs/plan/INDEX.md`.
- The fix, `git show 985e77aa`. It touches `clock/`: AlarmTab, TimerTab, WorldClockTab, ClockWidgets.ClockAppBar,
  ClockTabs and ClockActivity.
- The rule it reuses, L13-3's `OverlayLayer` and `dismissOverlay` in
  `app/src/main/kotlin/app/tileshell/ui/components/ModalOverlay.kt`. Why the rule works: the amendments and the
  Compose 1.11.4 correction in `docs/plan/review/2026-09-26-L13-345-fix-plan.md`.
- The QA, `git show --stat 8a843081`:
  - drivers `docs/plan/qa/phase-15/scripts/l13_11_row.sh` and `l13_12_row.sh`;
  - L13-11 evidence: `L13_11-before-a7c9d715/` (the bar 19/30, the search 13/30) and `L13_11-05e543d7/` (30/30, 30/30);
  - L13-12 evidence: `L13_12-before-a7c9d715/` (18/3) and `L13_12-05e543d7/` (21/0), plus the first driver's runs,
    `L13_12-drv1-*`.

## Rules

- Root cause at the producer.
- Stay within the defect's scope.
- The row fails before the fix and passes after.
- No regression in the Clock's Back or its layout.

## Lenses

- **A: design and correctness.**
  - ClockAppBar now takes `isExpanded: () -> Boolean`. Its scrim and menu sit in an inner full-size Box inside the
    layer. Is the menu's placement unchanged? What does the always-composed empty Box cost? Is the bar's own height
    right?
  - The city search in the layer: its focus and keyboard.
  - The hold-menu BackHandlers: their order against the editor's and name field's handlers, and against
    ClockNav.back().
  - The activity Back's `dismissOverlay` wrapper.
  - Does anything else in the Clock still have the shape? Look at the timer editor, the expanded timer, the
    stopwatch, and the compare strip.
- **B: evidence (adversarial).**
  - Can each assertion fail?
  - Does the before run fail and the after run pass, on the builds the headers name?
  - Could a touch that lands before the Back count as a loss in the probes?
  - Is the search probe's keyboard step sound?
  - Is the World Clock store restored?

## Output

Write `docs/plan/review/2026-09-27-L13-1112-fix-review-<a|b>.md` with:
- a verdict: PASS / FAIL;
- blocking findings, each with a path and a failure scenario;
- notes.

Keep it under 90 lines.
