# L13-3, L13-4, L13-5 fixes — review brief (2026-09-26)

Repo: metro-launcher (Tessera, an Android launcher; Kotlin + Compose). Branch main. You review the fixes for three
defects that phase 13's L13-3 investigation found. You judge independently; do not read the other reviewer's report.
Read-only: do not edit, build, install or run anything on a device. You may read any file in the repo and run javap
or grep on the Compose UI artifacts named below.

## What to read

- The ledger rows L13-3, L13-4, L13-5 in `docs/plan/INDEX.md` (Blocked-on ledger).
- The investigation: `docs/plan/review/2026-09-26-L13-3-investigation.md` (evidence `docs/plan/qa/phase-13/L13-3-investigation/`).
- The fix plan, including its amendment: `docs/plan/review/2026-09-26-L13-345-fix-plan.md`.
- The commits:
  - `b2d44b7d`: L13-4 (StartActivity), L13-5 (ModalOverlay item lift), and L13-3's first fix (sharing). That first
    fix failed its gate and was replaced.
  - `9a801834`: L13-3's second fix (OverlayLayer, dismissOverlay, an item runs only while its overlay is open). It
    removes the sharing node.
  - Review the code as it stands at `9a801834`: `git show b2d44b7d 9a801834`, `git diff b2d44b7d~1 9a801834 -- app/`.
- Compose UI 1.12.1, the version this build uses. The AAR is at
  `~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-android/1.12.1/95600d9aea586baa1493db8a2c0ab48507cc4ecf/ui.aar`.
  The fix plan's amendment makes claims about these classes: `HitPathTracker.addHitPath`, `NodeParent.removePointerInputModifierNode`,
  `HitTestResult.acceptHits`, `InnerNodeCoordinator.hitTestChild` (it skips unplaced children), `AndroidComposeView.handleMotionEvent`
  (it runs `measureAndLayout` before dispatch), and the snapshot-observer executor (synchronous on the main looper).

## Evidence (all under `docs/plan/qa/phase-13/`)

| Row | Before the fix | After |
|---|---|---|
| L13-3 (`scripts/l13_3_row.sh`: 70 trials at gap 0, then EDGE_RAPID's host loop x3) | `L13_3-before-ee4bf960/` (5554: host loops 4/10 and 8/10) · `L13_3-fix1-598ca8c2/` (the first fix: loops 8, 8, 10 of 10) · `L13_3-before-ee4bf960-emu5558/` (did NOT reproduce: 70/70 and 10/10 x3 on the old build, so 5558 does not discriminate this time) | `L13_3-emu5554-d6a364c8/` · `L13_3-emu5554-d6a364c8-extra-hostloops/` (5 more loops, 10/10 each) · `L13_3-emu5558-d6a364c8/` |
| L13-4 (`scripts/l13_4_row.sh`) | `L13_4-before-ee4bf960/` | `L13_4-fix1-598ca8c2/` (b2d44b7d) · `L13_4/` (d6a364c8) |
| L13-5 (`scripts/l13_5_row.sh`) | `L13_5-before-ee4bf960/` | `L13_5-fix1-598ca8c2/` · `L13_5/` (d6a364c8) |
| L13-2 regression (`scripts/l13_2_row.sh`, the overlays stay modal while open) | `L13-2-row-before-15aa7f5f/` | `L13-2-row-fix-d6a364c8/` (72/0) |
| Diagnosis of the first fix's failure | `L13-3-diag-on-fix-e4dedc5e/`, `L13-3-diag2-row-events-5d1a37d5/` (the diag patch is `diag.patch`; never committed) | — |
| Unit: `ModalOverlayTest` | `unit/red-ModalOverlayTest-L13-5-stub-gradle.txt`, `unit/red-ModalOverlayTest-L13-3-open-stub*` | `unit/green-ModalOverlayTest-L13-3.xml` (12/12) |

Build d6a364c8 is the APK built from 9a801834's tree. Each row log's header carries `apk built / installed / match`.
emulator-5554 is AVD tileshell_fhd. emulator-5558 is AVD tileshell_fhd2, the investigator's AVD, where the trials
themselves lost holds before any fix (the investigator used port 5556).

## Rules the fixes are held to (the project's Hard Rules)

- Root cause at the producer; no workaround at the consumer.
- The fix stays within the three defects' scope (no adjacent refactors).
- The evidence is real device output.
- A row's test must fail before the fix and pass after it.
- L13-2's rule must not regress: an overlay that is open is modal.

## Your lens

- **Reviewer A — design and correctness.** Is each fix at the producer, and is it correct? Check the Compose
  internals the amendment cites against the bytecode. Look for regressions:
  - drawing or semantics of an unplaced overlay;
  - overlay opening;
  - Music's grid Back handler;
  - `Snapshot.sendApplyNotifications` from a Back callback;
  - the item lift rule;
  - L13-4's child launch and its cancellation;
  - anything b2d44b7d left behind that 9a801834 should have removed.

  Mutation thinking: would each unit test fail if its rule were subtly wrong?
- **Reviewer B — evidence and testability.** For each row:
  - Does it fail before and pass after, on the build the header says?
  - Can the row fail at all? Look for assertions that are vacuous or fail-open.
  - Were the earlier runs kept rather than overwritten?
  - Does the evidence support each ledger row's claim?

  Be adversarial: the author's summary is a claim, not evidence.

## Output

Write your report to `docs/plan/review/2026-09-26-L13-345-fix-review-<a|b>.md`:
- a verdict (PASS / FAIL);
- blocking findings, each with a file:line or an evidence path, and a failure scenario;
- non-blocking notes.

Keep it under 150 lines.
