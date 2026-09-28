# L13-8 and L13-9 fixes: review brief (2026-09-27)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), on branch main. Judge independently and do not
read the other reviewer's report. Read-only: no builds, no installs, no adb.

## Read

- Ledger rows L13-8 and L13-9 in `docs/plan/INDEX.md`.
- **L13-9** (the Clock app, phase 15's part), `git show 03abdc05`. The alarm editor's flyouts get L13-3's rule:
  `OverlayLayer` and `dismissOverlay` in `app/src/main/kotlin/app/tileshell/ui/components/ModalOverlay.kt`.
  - Why that rule works: `docs/plan/review/2026-09-26-L13-345-fix-plan.md` (the amendments and the 1.11.4
    correction).
  - Driver: `docs/plan/qa/phase-13/scripts/l13_9_row.sh`.
  - Evidence: `L13_9-before-d74b02ac/` (21/30) and `L13_9-0222df79/` (30/30).
- **L13-8** (Music, phase 10's part), `git show 48ec4ae7`. Both drawn Backs call the activity's back dispatcher, the
  way `clock/ClockActivity.kt` already does.
  - Driver: `docs/plan/qa/phase-13/scripts/l13_8_row.sh`.
  - Evidence: `L13_8-before-0222df79/` (10/3) and `L13_8-8384ebd3/` (13/0). The first driver version is kept as
    `L13_8-drv1-*`.

## Rules

- Root cause at the producer.
- Stay within the defect's scope.
- The row fails before the fix and passes after it.
- No regression in the Back behaviour a row does not cover.

## Lenses

- **A: design and correctness.**
  - L13-9: are the flyouts' layout, placement and BoxScope uses unchanged inside the layer? Is the name field's Back
    still right? Is anything missed in the Clock app: the timer and world tabs, the Sounds page, the music picker?
  - L13-8: with the dispatcher, what does the drawn Back now do in each Music state?
    - the collection: nothing open, the hold menu, the name box with and without the keyboard, an open playlist, the
      detail page;
    - Now Playing: menu levels, the header back.
  - L13-8: what does the dispatcher's fallback do on the task root compared with the old `finish()`? Does any caller
    rely on `finish()`?
- **B: evidence (adversarial).**
  - Can each assertion fail?
  - Does the before run fail and the after run pass, on the builds the headers name?
  - Are the L13-8 cases independent?
  - In the L13-9 probe, could a touch that lands before the Back count as a loss?

## Output

Write `docs/plan/review/2026-09-27-L13-89-fix-review-<a|b>.md` with:
- a verdict: PASS or FAIL;
- blocking findings, each with a path and a failure scenario;
- notes.

Keep it under 90 lines.
