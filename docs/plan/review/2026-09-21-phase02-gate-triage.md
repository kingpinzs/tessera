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

## What the gate still owes (the end-of-build QA pass)

Recorded rather than done now, at Jeremy's instruction to stop spending device time mid-build.

1. **Re-run E1, E7, E8 on the current drivers** (B2), and stamp each log with the driver's git blob SHA.
2. **Rewrite E4 to mutate the layout first** (pin, resize, build a folder, fill the row), and assert the
   pre-state differs from the baseline, so the row can fail (B1).
3. **Assertions with exit codes** in E1, E3, E5, E8, EDGE and E9's first four steps; a runner that fails the
   suite on any non-zero (B3, and both reviewers' "logs state without comparing it").
4. **Close the fail-open bracket** (B4) — a null probe becomes FAIL, edit mode proved positively from the
   contracted position plus `edit_disc:unpin` in a dump.
5. **Tee the EDGE verdicts into the log** and gate on their exit codes (B5).
6. **Run the missing sub-rows**: the band drop (B6), folder-over-folder on the device (B7), H10's create
   feedback capture, a wide folder tile for H13, a collapsed four-member face for H12, selecting another tile
   for H8 (B8).
7. **Measure E7's remaining values** (B9): the light theme end to end, the wallpaper dim, the dragged tile,
   the glyph sizes and the arrow per size on a MEDIUM tile.
8. **Make E7's settle reproducible** (B10): average several static tiles, clamp the scanline to the named
   tile's x-range (reviewer 1 found `e7_motion.py` reads the rightmost bright pixel of the whole row, so a
   different probe tile silently measures its neighbour), and fix the tolerance in the doc rather than in the
   script.
9. **Root-cause the two "emulator limit" claims** instead of routing them: the exit latency can be timed from
   the shell's own ring buffer if the ACTION_UP `eventTime` is logged beside the exit, and "Home while Start
   is showing" has a second path that needs no intent at all — the drawn Windows key emits the same event, and
   nothing tried it. Both reviewers flagged this independently and they are right: neither claim is evidenced.
10. **`layout_restore` is racy** — it writes the store while the shell is still running and then force-stops
    it; reviewer 1 proved a restore was silently lost (`E08/e8_base.json` holds SMALL right after restoring a
    baseline that says MEDIUM). Force-stop first, then write, then read back and compare.
11. **Build-identity guard** on every lead-driven row, the way the agent's own rows already do it.
12. **Record a unit-test run** in the evidence tree, and make `E07.txt` regenerable by a script instead of
    hand-assembled.
13. **Constants have no test**: ~30 values in `EditMode.kt` are transcriptions from R6 that nothing pins.
    A one-assertion-per-constant test turns silent drift into a red test.
14. Minor, worth doing with the rest: `qa.py`'s docstring still carries the retracted "a dump cannot see the
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
