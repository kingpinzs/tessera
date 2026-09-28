# L13-16 and L13-17 fixes — review brief (2026-09-28)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently; do not read
the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger rows L13-16 and L13-17 in `docs/plan/INDEX.md`, and their sources:
  `docs/plan/review/2026-09-28-L13-1415-fix-review-a.md` note 3 and `2026-09-28-L13-14-fix-r3-a.md` note 2.
- The fixes:
  - `git show ee0b8728` (L13-16): clock/ClockWidgets.kt — ClockFlyout gains `bottomInset` and a layout modifier
    that places it no lower than that limit; RowHoldMenu passes the app bar's height; clock/WorldClockTab.kt's Remove
    menu passes it too.
  - `git show 5dde8cad` (L13-17): clock/ClockWidgets.kt — each bar button calls `onExpand(false)` before it acts.
- Context: clock/ClockTabs.kt (ClockScaffold, the bar buttons), AlarmTab.kt, TimerTab.kt, ClockActivity.kt (Back, the
  flag's other writes), ui/components/ModalOverlay.kt (OverlayLayer). The L13-14 fix 96dc4701 is reviewed (round 3
  PASS / PASS) and is the base of both.
- The QA, `git show --stat b2f22208`: drivers `docs/plan/qa/phase-15/scripts/l13_16_row.sh` and `l13_17_row.sh`;
  evidence `L13_16-before-cb8a791b/` (19/9), `L13_16-0c3e6ac7/` (28/0), `L13_17-before-cb8a791b/` (17/6),
  `L13_17-0c3e6ac7/` (23/0). Builds (APK sha256 / md5 in the headers): cb8a791b / 6680b509 = 96dc4701 (before);
  0c3e6ac7 / 39644167 = 5dde8cad (after). APKs in
  `/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad/`: `apk-l1414c-cb8a791b.apk`,
  `apk-l131617-0c3e6ac7.apk` (read-only; unzip to your own scratch).

## Rules

- Root cause at the producer. Stay within each defect's scope.
- Each row fails before the fix and passes after.
- No regression in the Clock's flyouts (the alarm editor's repeat-days, sound and snooze flyouts also use ClockFlyout,
  with no bottom limit), its row menus, its bar, or Back.

## Lenses

- **A — design and correctness.**
  - L13-16: is the limit right on every tab and state a row menu can open in (the bar collapsed; Compare, Select, the
    search and "…" all close row menus first)? Is `constraints.maxHeight` the page's height where each flyout is
    measured, and does the change leave the editor flyouts where they were? Does the clamped menu ever cover the
    finger that opened it in a way that matters (the release after a hold)? `placeRelative` vs the old `offset`.
  - L13-17: does collapsing first change any button's behaviour (disabled buttons, the editors' Save / Delete, Pin,
    Share, Delete in select mode)? Anything that relied on the bar staying expanded?
  - Anything else in the Clock with either shape?
- **B — evidence (adversarial).**
  - Can each assertion fail? Does each before run fail for the reason claimed, on the build the header names?
  - Is "the lowest row showing above the bar" and the "old placement would reach under the bar" check sound on all
    three tabs? Is the verb-acted check a real proof (the right row gone, nothing else)?
  - Do the driver blobs and installed md5s match the committed drivers and the builds named? Is 0c3e6ac7's dex
    5dde8cad's ClockWidgets?
  - What still rests on code alone?

## Output

Write `docs/plan/review/2026-09-28-L13-1617-fix-review-<a|b>.md` with a verdict (PASS or FAIL), the blocking findings
(each with a path and a failure scenario), and notes. Keep it under 80 lines.
