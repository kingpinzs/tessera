# L13-14 and L13-15 fixes — review brief (2026-09-28)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently; do not read
the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger rows L13-14 and L13-15 in `docs/plan/INDEX.md`, and their source, `docs/plan/review/2026-09-27-L13-13-fix-r2-a.md`.
- The fix, `git show 4a1b7841`:
  - clock/ClockWidgets.kt: the app bar gets a non-consuming hit target;
  - clock/WorldClockTab.kt: the compare strip gets the same, and CityRow now uses `detectTapOrHold` (ClockWidgets.kt).
- The same non-consuming pattern is already reviewed on the city search (72776f4a,
  `docs/plan/review/2026-09-27-L13-13-fix-r2-{a,b}.md`).
- The QA, `git show --stat HEAD`:
  - drivers `docs/plan/qa/phase-15/scripts/l13_14_row.sh` and `l13_15_row.sh`;
  - evidence `L13_14-before-ce79bc17/` (7/3), `L13_14-9e658a94/` (10/0), `L13_15-before-ce79bc17/` (8/1) and
    `L13_15-9e658a94/` (9/0).

## Rules

- Root cause at the producer.
- Stay within the defect's scope.
- Each row fails before the fix and passes after.
- No regression in the Clock's bar, its compare strip or the World Clock rows.

## Lenses

- **A — design and correctness.**
  - Do the bar's buttons, the "…" button and the strip's chevrons still work? Check each for a finger's movement.
  - What does the bar's hit target do while the "…" menu is open?
  - Does `detectTapOrHold` on CityRow keep the hold (and its release, consumed in the Initial pass) and the menu's
    anchor?
  - Does anything else in the Clock have either shape?
- **B — evidence (adversarial).**
  - Can each assertion fail?
  - Does the before run fail and the after run pass, on the builds the headers name?
  - Is the "row under the point" check sound?
  - L13-15 (b) does not discriminate. Is (a) enough?
  - What rests on code alone?

## Output

Write `docs/plan/review/2026-09-28-L13-1415-fix-review-<a|b>.md` with:
- a verdict, PASS or FAIL;
- the blocking findings, each with a path and a failure scenario;
- notes.

Keep it under 80 lines.
