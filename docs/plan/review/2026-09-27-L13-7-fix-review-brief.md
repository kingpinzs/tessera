# L13-7 fix — review brief (2026-09-27)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently and do not
read the other reviewer's report. Read-only: no builds, no installs, no adb.

## Read

- Ledger row L13-7 in `docs/plan/INDEX.md`. Jeremy's report: the Tess tile, then Back, leaves Start black.
- The fix, `git show 54ecf613` (`StartActivity.onWindowFocusChanged`). Jeremy committed it himself, and its message is
  terse. Also read `launchApp`, `onTileTap`, the exit and entrance effects, and `onResume` in the same file.
- The QA, `git show --stat 6bf35f14`:
  - driver: `docs/plan/qa/phase-13/scripts/l13_7_row.sh`, with the fixture `l13_7_layout.json`;
  - evidence: `L13-7-tess-tile-repro-ad784939/`, `L13_7-before-ad784939/` (14/4), `L13_7-7f00cb9e/` (18/0),
    `L13_4-7f00cb9e/` (22/0).

## Rules

- Root cause at the producer, and stay within the defect's scope.
- The row fails before the fix and passes after it.
- No regression in how Start comes back from an ordinary launch.

## Lenses

- **A: design and correctness.** Is window focus the right signal? Work through each path:
  - an ordinary app launch, where `onResume` runs before the focus returns;
  - Tess opened from her tile, then an app opened from inside Tess;
  - a launch that fails;
  - the notification shade or a system dialog pulled down while Start is showing;
  - a satellite launch (phase 11);
  - Back on Start's back-history launch;
  - the drawn Windows key.

  Could the entrance play twice, or at a wrong moment? Does anything else that `onResume` does "as Start comes back"
  (auto-size, the recent row) now get skipped for the Tess tile?
- **B: evidence (adversarial).** For each assertion, can it fail? Does the before run fail and the after run pass, on
  the builds the headers name? Is the Settings control strong enough to catch a double entrance?

## Output

Write `docs/plan/review/2026-09-27-L13-7-fix-review-<a|b>.md` with:
- a verdict (PASS or FAIL);
- blocking findings, each with a path and a failure scenario;
- notes.

Keep it under 90 lines.
