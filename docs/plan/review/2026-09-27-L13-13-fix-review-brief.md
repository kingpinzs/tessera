# L13-13 fix: review brief (2026-09-27)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently and do not
read the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger row L13-13 in `docs/plan/INDEX.md`, and where it came from: `docs/plan/review/2026-09-27-L13-1112-fix-review-a.md`.
- The fix: `git show 2f7b5faa` (clock/WorldClockTab.kt, AlarmTab.kt, TimerTab.kt).
  - Each tab gets a LaunchedEffect that closes its hold menu when the bar is used.
  - The city search's root gets `modalOverlay(onTapOff = {})` (L13-2's rule, `ui/components/ModalOverlay.kt`).
- The QA: `git show --stat HEAD` (after 2f7b5faa).
  - Driver: `docs/plan/qa/phase-15/scripts/l13_13_row.sh`.
  - Evidence: `L13_13-before-05e543d7/` (15/5) and `L13_13-bd114d21/` (20/0). The first driver's runs are kept as
    `L13_13-drv1-*`.

## Rules

- Root cause at the producer.
- Stay within the defect's scope.
- The row fails before the fix and passes after.
- No regression in the Clock's Back or its search.

## Lenses

- **A: design and correctness.**
  - The LaunchedEffect keys and conditions: can one close a menu the user just opened, fire at a wrong time, or miss
    a bar action?
  - The search under `modalOverlay`: do the text field (focus, cursor, selection, the keyboard) and the result rows
    still work? What does a drag on the page now do?
  - Anything else in the Clock with the "hidden under" shape?
- **B: evidence (adversarial).**
  - Can each assertion fail?
  - Does the before run fail and the after run pass, on the builds the headers name?
  - Is case (2)'s blank-area hold really over the city row?
  - What rests on code alone?

## Output

Write `docs/plan/review/2026-09-27-L13-13-fix-review-<a|b>.md`:
- a verdict, PASS or FAIL;
- blocking findings, each with a path and a failure scenario;
- notes.

Keep it under 80 lines.
