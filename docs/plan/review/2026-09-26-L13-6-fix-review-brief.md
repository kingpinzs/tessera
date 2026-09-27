# L13-6 fix — review brief (2026-09-26)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently, and do not
read the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger row L13-6 in `docs/plan/INDEX.md`, and the fix plan `docs/plan/review/2026-09-26-L13-6-fix-plan.md`.
- The rule it reuses, L13-3's `OverlayLayer` and `dismissOverlay` in `app/src/main/kotlin/app/tileshell/ui/components/ModalOverlay.kt`:
  - why it works: `2026-09-26-L13-345-fix-plan.md`, both amendments and the Compose 1.11.4 correction;
  - both rounds of its review: `2026-09-26-L13-345-fix-r2-{a,b}.md`.
- The code: `git show 422b6652` (music/MusicCollectionPage.kt, music/MusicNowPlaying.kt).
- The QA: `git show --stat 9b2a1ff9`.
  - Drivers: `docs/plan/qa/phase-13/scripts/l13_6_probe.sh` and `l13_6_row.sh`.
  - Evidence: `docs/plan/qa/phase-13/L13-6-repro-707aa55b/` (the reproduction, 14/30 and 23/30);
    `L13_6-before-707aa55b/` (the row on the old build: 21/30 and 21/30, FAIL);
    `L13_6-ad784939/` (the row on the fix: 30/30 and 30/30);
    `L13-2-row-fix-ad784939/` (72/0).
  - Builds: 707aa55b = b572c6fe (installed id 60628bac); ad784939 = 422b6652's tree (installed id dfcd0c83).

## Rules

- Root cause at the producer.
- Stay inside the defect's scope.
- The row fails before the fix and passes after.
- L13-2 must not regress: an overlay that is open is modal.

## Lenses

- **A — design and correctness.** Is each of the three overlays wired correctly? Look at:
  - the page `BackHandler` and the drawn Back's branches;
  - Now Playing's `BackHandler`;
  - the layers' `active` lambdas;
  - how the layers are measured and placed inside their parents (Box, BoxWithConstraints);
  - focus and keyboard for the name box while it is unplaced for a frame;
  - anything the fix missed.
- **B — evidence (adversarial).**
  - Can the probe fail?
  - Does it fail before the fix and pass after, on the builds the headers name?
  - Could a pre-Back down be counted as a loss?
  - What does the name box, which has no probe, rest on?

## Output

Write `docs/plan/review/2026-09-26-L13-6-fix-review-<a|b>.md`:
- a verdict (PASS / FAIL);
- blocking findings, with a path and a failure scenario;
- notes.

Keep it under 100 lines.
