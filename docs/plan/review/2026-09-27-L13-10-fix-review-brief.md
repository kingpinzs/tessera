# L13-10 fix: review brief (2026-09-27)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently and do not
read the other reviewer's report. Read-only: no builds, no installs, no adb.

## Read

- Ledger row L13-10 in `docs/plan/INDEX.md`.
- The defect: `docs/plan/qa/phase-15/EDGE_ALARM_CONTEXT-run3/DEFECT.md` §1.
- The investigation: `docs/plan/review/2026-09-27-alarm-under-tess-investigation.md`.
- The phase 15 doc's edge case "Alarm firing while Tess is listening (the session hides)".
- The fix: `git show f5d8d22e` (`cortana/CortanaSession.kt`). Read it together with `clock/RingService.kt` (its
  `state`) and `CortanaModel.stop()`.
- The QA: `git show --stat c961051e`.
  - Driver: `docs/plan/qa/phase-15/scripts/l13_10_row.sh`. It uses phase 15's `lib.sh`, `p15.sh` and `clock.sh`, and
    the method of `edge_alarm_context.sh` at git `da848a06`.
  - Evidence: `L13_10-before-8384ebd3/` (13/11) and `L13_10-592a3efd/` (24/0). The first driver version's runs are
    `L13_10-drv1-*`.

## Rules

- Root cause at the producer.
- Stay within the defect's scope.
- The row fails before the fix and passes after it.
- No regression in Tess's own show and hide paths.

## Lenses

- **A: design and correctness.** Check each of these:
  - `drop(1)` against a StateFlow's replay of the current value: a ring already up, a ring that ends, a ring that
    changes while it is up.
  - The job's lifetime across onShow, onHide and onDestroy, and a re-show of the reused session.
  - Threading: which dispatcher collects, and whether `hide()` is safe from there.
  - What the hide drops: the reply, a pending card, a photo draft when Tess has stepped aside (L13-1).
  - The lock screen.
  - Whether anything else should also yield.
- **B: evidence (adversarial).**
  - Can each assertion fail?
  - Does the before run fail and the after run pass, on the builds the headers name?
  - Is the listen window, read from Tess's own `final ... audioMs` line, a sound precondition?
  - Is a 1000 ms bound on the hide meaningful?
  - What rests on code alone? E9 did not run.

## Output

Write `docs/plan/review/2026-09-27-L13-10-fix-review-<a|b>.md` with:
- a verdict, PASS or FAIL;
- blocking findings, each with a path and a failure scenario;
- notes.

Keep it under 90 lines.
