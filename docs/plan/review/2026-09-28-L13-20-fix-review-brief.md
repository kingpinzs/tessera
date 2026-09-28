# L13-20 fix — review brief (2026-09-28)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently; do not read
the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger row L13-20 in `docs/plan/INDEX.md` and its source, `docs/plan/review/2026-09-28-L13-1819-fix-review-a.md`
  note 1.
- The fix, `git show 4ca44154`: clock/AlarmTab.kt and clock/TimerTab.kt — each editor's bar callback ends the name
  edit (`editingName = false`) when the "…" menu opens.
- Context: both editors' `BackHandler`s, `NameField` and the timer editor's name field (focus, keyboard, `onDone`),
  ClockActivity.kt's Back (ClockNav.back), ClockWidgets.kt's ClockAppBar. L13-19 (12c17636, reviewed PASS / PASS)
  added the flyout close in the same alarm-editor callback.
- The QA, `git show --stat 48ad1dab`: driver `docs/plan/qa/phase-15/scripts/l13_20_row.sh`; evidence
  `L13_20-before-e03fd03b/` (14/4) and `L13_20-99612792/` (18/0). Builds (APK sha256 / md5 in the headers): e03fd03b /
  3ebde477 = 12c17636 (before); 99612792 / a8e8283a = 4ca44154 (after). APKs in
  `/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad/`: `apk-l131819-e03fd03b.apk`,
  `apk-l1320-99612792.apk` (read-only; unzip to your own scratch).

## Rules

- Root cause at the producer. Stay within the defect's scope.
- The row fails before the fix and passes after.
- No regression in either editor's name field (typing, Done, Back with the keyboard up, the name kept), the keyboard,
  the "…" menu, or Back.

## Lenses

- **A — design and correctness.**
  - Is the bar callback the right producer? Opening "…" with the keyboard still up (the field focused): does ending
    the edit hide the keyboard, keep what was typed (including an IME composition not yet committed), and leave focus
    sane? Any path where the name is lost or the keyboard is left up with no field?
  - Does the order of Back handlers (the editors' BackHandler, the activity's ClockNav.back) now unwind correctly in
    every state: keyboard up, edit mode without keyboard, menu up, a flyout up?
  - Anything else in the Clock with the same shape (transient editor state left alive under the menu)?
- **B — evidence (adversarial).**
  - Can each assertion fail? Does the before run fail for the reason claimed, on the build the header names? Is the
    precondition (keyboard hidden, field still in edit mode) really shown on both builds?
  - Is "the typed name kept" proven by the dump (could the text come from elsewhere)?
  - Do the driver blob and installed md5s match the committed driver and the builds named? Is 99612792's dex
    4ca44154's two editors?
  - What still rests on code alone?

## Output

Write `docs/plan/review/2026-09-28-L13-20-fix-review-<a|b>.md` with a verdict (PASS or FAIL), the blocking findings
(each with a path and a failure scenario), and notes. Keep it under 80 lines.
