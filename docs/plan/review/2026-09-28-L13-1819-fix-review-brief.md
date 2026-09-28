# L13-18 and L13-19 fixes — review brief (2026-09-28)

Repo: metro-launcher (Tessera, an Android launcher in Kotlin + Compose), branch main. Judge independently; do not read
the other reviewer's report. Read-only: no builds, installs or adb.

## Read

- Ledger rows L13-18 and L13-19 in `docs/plan/INDEX.md`, and their source,
  `docs/plan/review/2026-09-28-L13-1617-fix-review-a.md` note 7.
- The fixes:
  - `git show 3bebd69b` (L13-18): clock/ClockActivity.kt — ClockNav gains `aboutFrom` and `openAbout()`, and `back()`
    returns from About to it; clock/ClockTabs.kt's About menu entry calls `openAbout()`.
  - `git show 12c17636` (L13-19): clock/AlarmTab.kt — the alarm editor's bar callback closes the flyout when the menu
    opens.
- Context: ClockActivity.kt (ClockNav, the activity's Back, external opens via `show()`), ClockTabs.kt (ClockApp's
  pages, the "…" menu), AlarmTab.kt and TimerTab.kt (the editors, their drafts, the flyouts and their Back),
  ui/components/ModalOverlay.kt. Earlier Clock fixes today (L13-13 … L13-17) are reviewed; the base is 5dde8cad.
- The QA, `git show --stat ffffcc12`: drivers `docs/plan/qa/phase-15/scripts/l13_18_row.sh` and `l13_19_row.sh`;
  evidence `L13_18-before-0c3e6ac7/` (15/3), `L13_18-e03fd03b/` (18/0), `L13_19-before-0c3e6ac7/` (16/6),
  `L13_19-e03fd03b/` (22/0). Builds (APK sha256 / md5 in the headers): 0c3e6ac7 / 39644167 = 5dde8cad (before);
  e03fd03b / 3ebde477 = 12c17636 (after). APKs in
  `/tmp/claude-1000/-home-jeremyking/9dace4c7-c342-4879-961a-84511afd4f90/scratchpad/`: `apk-l131617-0c3e6ac7.apk`,
  `apk-l131819-e03fd03b.apk` (read-only; unzip to your own scratch).

## Rules

- Root cause at the producer. Stay within each defect's scope.
- Each row fails before the fix and passes after.
- No regression in the Clock's Back (every page and mode), About, the editors, their flyouts, or the "…" menu.

## Lenses

- **A — design and correctness.**
  - L13-18: every way About can be reached and left — from the tabs, either editor; an external open (`show()`, a
    tile, a notification, the AlarmClock API) while About or an editor is up; the process recreated (ClockNav is held
    where?); the editor's draft being null or saved meanwhile. Can Back from About ever land somewhere wrong or loop?
  - L13-19: is the bar callback the right producer (every way the menu opens)? Does closing the flyout there interact
    with L13-9's flyout Back, the OverlayLayer the flyouts are drawn in, or the name field's editing state? Any other
    editor overlay left under the menu?
  - Anything else in the Clock with either shape?
- **B — evidence (adversarial).**
  - Can each assertion fail? Does each before run fail for the reason claimed, on the build the header names?
  - L13-18 (a): is "the edit kept" proven by the dump (the snooze text), and could the text come from somewhere else
    on the page? L13-19: does each flyout's tag prove that flyout, and "not left open" hold after the menu closes?
  - Do the driver blobs and installed md5s match the committed drivers and the builds named? Is e03fd03b's dex
    12c17636's ClockNav and AlarmEditorScreen?
  - What still rests on code alone?

## Output

Write `docs/plan/review/2026-09-28-L13-1819-fix-review-<a|b>.md` with a verdict (PASS or FAIL), the blocking findings
(each with a path and a failure scenario), and notes. Keep it under 80 lines.
