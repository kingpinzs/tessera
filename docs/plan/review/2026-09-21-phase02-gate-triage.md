# Phase 02 QA gate — reviewer findings and triage (2026-09-21)

Two independent reviewers on the captured evidence, per the Stage C step 12 roster:

- **Reviewer 1** (design / correctness lens, opus): verdict **NOT PROVEN**. 6 blocking, 8 major, 4 minor.
- **Reviewer 2** (testability / evidence-integrity lens, opus): verdict **INSUFFICIENT**. 5 blocking, 8 major, 9 minor.

Both were read-only and neither saw the other's report. The lead verified every blocking claim before
accepting it; the verification commands and their output are in the session log and summarised per row below.

**Gate verdict: NOT CLOSED.** The phase's code is in better shape than its evidence: the two reviewers between
them found **two real product defects** (both fixed below) and a long list of places where the drivers log
state instead of comparing it, or where a row's evidence does not exist at all.

## Verified as true (the lead re-checked each)

| ID | Finding | Verification |
|---|---|---|
| B1 | **E4 cannot fail.** It ran against the pristine default layout, which is what a broken store reseeds to. | `diff E04/e4_before.json baseline_layout.json` → identical |
| B2 | **E1, E7, E8 logs predate their current drivers.** `baseline_layout.json` and the baseline-restore line landed in 601b071; those three logs were committed in c410f72 / 6ff409c / dfc7418, all earlier. Their runs opened on `slot:PEOPLE/SMALL`, the baseline says MEDIUM. | `git log --diff-filter=A` per file |
| B3 | **E8 contains no assertion.** Five `say "expect: …"` lines, no comparison; `dumpdiff.py` exists and is never called, so "no other tile moved" is unproven. | `grep -cE "PASS\|FAIL\|diff " e8.sh` → 1 (the word in a comment) |
| B4 | **The 830-ms bracket is fail-open.** `e7_bracket.py` returns "entered edit mode" whenever the probe point reads page background — any other screen passes. Reviewer 2 reproduced a false PASS by feeding it the app-list capture. | read `e7_bracket.py:40-42` |
| B5 | **The edge suite's two real verdicts never reach its log**, because `layout_json \| tee -a LOG \| python3` sends the Python's PASS/FAIL to the terminal. | `grep -c "folders after the sweep" EDGE/EDGE.txt` → 0 |
| B6 | **E3's band-drop sub-row was never run** (a tile dropped on an empty cell of the expanded band). The code path exists and is distinct. | `e3.sh` has six steps, none of them a band drop |
| B7 | **E8's folder-over-folder clause was never run**: `e8e_nonesting` drags the folder onto a plain tile. Only one folder ever exists in the run. | `e8.sh:84` picks the first NON-folder key |
| B8 | **Three NEEDS-HUMAN rows cite captures that do not exist or show something else**: H10's `e8a_folder_at1000ms.png` (never taken — that path holds 800 ms), H13's wide folder tile (no wide folder was ever rendered), H12's "collapsed face" capture is actually the EXPANDED band. | file listing; `E03/e3_face.xml` contains `folder_band_top` |
| B9 | **E7 does not measure every value its own row lists**: wallpaper dim, the whole light theme, the DRAGGED tile at 1.00, glyph sizes, disc colours, the arrow per size, "no feedback during the hold", the 2-frame undim. | `E07/E07.txt` holds 13 geometry checks, all dark-theme |
| B10 | **E7's settle statistic is operator-dependent.** Reviewer 1 re-ran the committed recordings against other legitimate static tiles: one choice turns the exit undim check into a FAIL (48.5 ms vs 100 ± 40), and the entry dim settle clears its floor by 0.21 ms. The 2 % band is a free parameter; at 3 % the scale settle fails. | reviewer's table, reproducible from `E07/*.mp4` |
| B11 | **The evidence index contradicts the evidence** (rows marked pending that passed, an `E06/` directory that does not exist). | `README.md` vs the tree |

## Product defects found by the review — FIXED in this commit

| ID | Defect | Fix |
|---|---|---|
| P1 | A drag carried above the grid's top row left `hover`, `folderFeedback` and `dropTarget` stale, because `updateTarget`'s null-cell branch returned early without clearing them. Releasing there made a folder with a tile the finger had left — and the dwell timer went on to reflow the abandoned target. Directly contradicts the Decisions' "leaving the tile before the dwell ends clears the feedback with no reflow". The auto-scroll edge band sits exactly where this happens. | `EditGestures.updateTarget` clears all three and sets `DropTarget.None` |
| P2 | An emptied bottom tile row could never be refilled: every row test was gated on the row already holding a tile, so once the last tile was dragged out or unpinned the row was a dead end. The phase doc lists "the row's last tile dragged out (empty row)" as an edge case and no row ran it. | the strip is a drop target whether or not the row has tiles |

## What the gate owed — being worked now (Jeremy, 2026-09-21: "fix the 14 owed items now")

1. **Re-run E1, E7, E8 on the current drivers** (B2), and stamp each log with the driver's git blob SHA.
   *Done in the harness:* `build_guard` in `assert.sh` records the installed APK's md5 against the built one
   (installing the built one when they differ) and prints the driver's blob SHA into the log; every row calls
   it. The re-runs themselves are the suite run at the end.
2. **Rewrite E4 to mutate the layout first** — **DONE**: `e4.sh` now builds a folder, cycles a tile's size and
   moves a tile into the bottom row through the product, then asserts the pre-state is NOT the default, that
   it holds a folder, and that the row is not the default three, before force-stopping and rebooting.
3. **Assertions with exit codes** — **DONE**: `assert.sh` gives every row `check`, `check_contains`,
   `check_absent`, `check_cmd`, a pass/fail tally and `qa_finish`, which decides the row's exit code. E1, E3,
   E5, E8, E9, EDGE, E2/E6 all compare now instead of narrating; `run_all.sh` runs the suite (unit tests
   first) and fails if any row fails.
4. **Close the fail-open bracket** — **DONE**: absence is now a FAIL. Edit mode is proved positively (the
   probe tile found at its CONTRACTED position, dimmed, smaller, and `edit_disc:unpin` present in a dump of
   the same moment), and the check carries a control that feeds it plain Start and requires a reject — the
   thing the fail-open version never had.
5. **Tee the EDGE verdicts into the log** — **DONE**: the v2-upgrade, sweep and process-death verdicts go
   through `check()` into the log, and `edge.sh` exits on the tally.
6. **The missing sub-rows** — **WRITTEN, pending the suite run**: E3 gained the band drop and now collapses
   the folder before capturing the four-member face (H12's capture was of the expanded band); E8 gained a
   second folder and drags one folder onto the other (B7) and always takes the 1000 ms feedback capture
   (H10); E1 gained the select-another-tile step (H8); E9 gained the empty-row refill and the wide-tile drop;
   EDGE gained Back-with-a-folder-expanded and the dwell boundary at 1800/2200 ms.
7. **E7's remaining values** — **PARTLY DONE**: the runner now captures and measures BOTH themes (the light
   theme's `c' = 0.63c + 62` fit was implemented but never run). Still open after this pass: the wallpaper
   dim, the dragged tile at 1.00 mid-drag, the glyph sizes and the arrow direction per size.
8. **E7's settle reproducibility** — **DONE**: the scanline is clamped to the named tile's own x window (it
   used to read the rightmost bright pixel of the whole row, so a different probe tile measured its
   neighbour); every measurable tile is measured, across THREE recordings per theme, and the row is judged on
   the median with the full spread printed; the ±2 % settle band is documented in the script's header and
   printed with every result.
9. **The two "emulator limit" claims** — **DONE, and both were hiding something.**
   *Exit latency:* the shell now records the touch-up's own MotionEvent time and the exit's first frame on one
   clock, and `e7_latency.py` reads the interval out of the ring buffer. First measurement: **169-185 ms, not
   150 ± 17** — a real defect. `delay(150)` was counted from when the coroutine was scheduled, not from the
   touch-up, so the dispatch and the frame boundary were added on top. Fixed by waiting out what remains of
   the 150 ms from the touch-up itself; it now measures 151-158 ms.
   *Home while Start is showing:* the drawn Windows key emits the same event with no intent at all, so the
   behaviour IS testable here. `regress.sh` now scrolls Start, moves to the app list, taps the Windows key and
   asserts the pivot returns AND the grid is back at the top, with the shell's own log line as corroboration.
10. **`layout_restore` race** — **DONE**: force-stop first, then write, then read the store back and compare
    it with what was asked for, retrying up to three times and returning non-zero if it never matches. Every
    row treats a failed restore as a failed check.
11. **Build-identity guard** — **DONE**: `build_guard`, called by every row.
12. **Unit-test run and a regenerable E7** — **DONE**: `run_all.sh` runs the unit tests first, writes
    `UNIT.txt` with the totals and gates on gradle's exit code; `e7.sh` runs capture + all four analysers for
    both themes and writes `E07.txt` itself.
13. **Constants** — **DONE**: `EditValuesTest` pins all ~30 against the numbers the Decisions quote, including
    the dim formulas as colours and a test that the entry curve holds BOTH of R6 §1.1.8's numbers. 113 unit
    tests, 0 failures.
14. Minors — **DONE**: the glyph sizes are constants again (they were hardcoded in a second place), the
    resize cache is pruned with its tile, a gesture another handler consumed no longer counts as a tap,
    `edge.sh` captures its diagnostics before the restore that force-stops the shell, `regress.sh` step 5
    asserts instead of printing, and `e5.sh` reads the prompt's real test tags (`secondary_pin_app` /
    `secondary_pin_name`) so H22's content is actually checked. Still to tidy: `qa.py`'s docstring still carries the retracted "a dump cannot see the
    transform" claim; `corner_point` in `gestures.sh` is superseded by `disc_center`; `edge.sh` captures its
    diagnostics after the restore that force-stops the shell, so `edge_diag.txt` is empty by construction;
    `regress.sh` step 5 has no `ok` call; `e5.sh` reads two prompt fields by tags that do not exist, so H22's
    content is unasserted; `StartPage.lastArea` is an unpruned process-global.

## Where the reviewers agreed, and where they did not

Both, independently: E8 has no assertions; E1 logs without comparing; the H10/H13 captures do not exist; the
E7 row is partially unmeasured; the two "emulator limit" claims are unevidenced; the index contradicts the
tree. That agreement is the strongest signal in the two reports.

Reviewer 1 alone found the two product defects (P1, P2) and the E7 reproducibility problem (B10).
Reviewer 2 alone found the stale-driver pairing (B2), the fail-open bracket (B4) and the lost EDGE verdicts
(B5), and demonstrated the false PASS rather than asserting it.

Neither reviewer disputed the parts that are genuinely proven: E2/E2c, E4's mechanism (as opposed to its
seeding), E9's six steps including the 2-column overflow, E7's geometry numbers (reviewer 1 reproduced 13/13
from the committed captures and corroborated the discs from the dump), and the 106-test unit layer.
