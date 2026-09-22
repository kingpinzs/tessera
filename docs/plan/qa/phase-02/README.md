# Phase 02 QA gate — plan and evidence index

Rows come from `docs/plan/phase-02-edit-mode-folders.md` (FINAL). Every row starts from
`baseline_layout.json` (the phase 01 default Start) and restores what it changes (PLAN RV12); motion rows are
measured from screenrecord frames (RV11); dumps follow RV13 (retried up to 3 times).

## Method note — dumps and pixels

Corrected 2026-09-21. An earlier probe concluded that a `uiautomator` dump cannot see the edit-mode
contraction. That probe was invalid: its long press landed in a gutter, so edit mode never engaged and the two
dumps being compared were both of plain Start. A dump taken **while a tile is held** does carry the
`graphicsLayer` transform — an unheld tile's bounds are 0.835 of its resting size and moved toward the fixed
point, the held tile keeps its size with its centre moved — and the `dim:*` overlays and the two `edit_disc:*`
discs appear with their test tags as resource-ids.

So the drivers address the discs by resource-id, and layout facts come from dumps and the store's own JSON.
E7 still measures the settled geometry from **screencap pixels**, because that is what proves the numbers the
phase doc lists (a tile's drawn size and the dim factor in real pixels, not the bounds Compose reports), and
its motion from screenrecord frames with the tile edge read to **sub-pixel** precision — a hard threshold on an
antialiased edge saturates about 3 px early and hides an ease-out's tail.

## Scripts

| Script | What it drives |
|---|---|
| `scripts/gestures.sh` | one continuous touch stream (`input motionevent`), the 783-ms hold, `edit_point` / `disc_center`, `ensure_start` |
| `scripts/layout.sh` | read / save / restore the layout store (RV12) |
| `scripts/qa.py` | pixel measurement: tile rectangles, plate colours, disc detection, PASS/FAIL rows with an exit code |
| `scripts/regress.sh` | phase 01 behaviours this phase's gesture layer could break |
| `scripts/e1.sh` | E1 — hold+drag as one gesture, resize disc cycle, unpin disc |
| `scripts/e3.sh` | E3 — folder create, name (placeholder + tap-and-hold), collapse/expand, 3rd and 4th member, dissolve |
| `scripts/e4.sh` + `dumpdiff.py` | E4 — force-stop and reboot leave the layout identical |
| `scripts/e7_capture.sh` + `e7_geometry.py` + `e7_motion.py` + `e7_bracket.py` | E7 — the measured values and the hold bracket |
| `scripts/e8.sh` | E8 — both drag paths, against a tile and against a folder, plus no-nesting |
| `scripts/e9.sh` + `e9_overflow.py` | E9 — bottom row editing, the full-row refusal, the column-change overflow |
| `scripts/edge.sh` | the edge cases that can be driven deterministically |

## Rows

| Row | Owner | Status | Evidence |
|---|---|---|---|
| Phase 01 regression | lead | **PASS 6/6** | `REGRESS/REGRESS.txt` |
| E1 long-press + drag, resize, unpin | lead | **PASS** | `E01/E01.txt` |
| E2 app-list menu → Pin to Start, "New" caption cleared | build task 3 | agent PASS, re-run pending | `agent-tasks-3-5/E2.txt`, `E02/` |
| E3 folders end to end | lead | **PASS** | `E03/E03.txt` |
| E4 force-stop + reboot keep the layout | lead | running | `E04/` |
| E5 secondary tiles through the client library | lead (seam from task 6) | pending | `E05/` |
| E6 uninstall drops the tile, `install -r` keeps it | build task 5 | agent PASS, re-run pending | `agent-tasks-3-5/E6*.txt`, `E06/` |
| E7 edit-mode geometry and motion | lead | **PASS** (exit latency → P2) | `E07/E07.txt` |
| E8 both drag paths | lead | **PASS** | `E08/E08.txt` |
| E9 bottom tile row editing | lead | **PASS 6/6** | `E09/E09.txt` |
| Edge cases | lead | pending | `EDGE/` |

## Defects the gate found, all fixed in this phase

| # | Found by | Defect |
|---|---|---|
| 1 | regression check | the gesture layer was a sibling drawn on top, so tapping a tile stopped launching it and Start stopped scrolling |
| 2 | E7 geometry | the contraction's fixed point was a fraction of the page, not the screen (0.446 instead of 0.46-0.49) |
| 3 | E7 geometry | the discs rode the contraction: 28 epx instead of 31, centres about 3 epx inside the drawn corner |
| 4 | E7 motion | the entry's scale curve could not hold both of R6's numbers; a saturating exponential does |
| 5 | E8 | moving a folder tile deleted its folder and lost every member |
| 6 | E8 | a folder with no name came back from the store as the string "null" |
| 7 | E3 | opening the folder name box swung the pivot to the app list |
| 8 | E3 | dismissing it did the same, by handing focus to the app list's search field |
| 9 | build task 3's E2c | a pinned app tile's profile was dropped by the store, so pinning twice made a second tile after a restart |

## Emulator limits — not defects, routed onward

- **The exit latency (150 ± 17 ms)** needs the touch-up's own frame, and this AVD's screenrecord does not
  contain Android's touch indicator (0 bright pixels at the tap point). It is phone row **P2**, which the phase
  doc already assigns for tolerances of 17 ms or less.
- **Home while Start is showing** (phase 01's X20 / H28) never fires here: when the shell is already the
  resumed home activity this AVD does not re-deliver the home intent, so `onNewIntent` is never called. Phase
  01 did not exercise it either; it stays a phone row. The drivers return to Start with a swipe (`ensure_start`).

## Edge cases (phase doc "Edge cases"), captures in `EDGE/`

1. Pinning an app already on Start; pinning when Start is long; resizing to wide at the right edge in 2-column mode
2. Changing show-more-tiles reflows a persisted layout without losing tiles — **the defect phase 01 recorded** (also E9 step 6)
3. Folder with wide tiles; a wide tile added; a folder dropped on a folder; a folder left with one tile (by unpin
   and by uninstall); uninstalling an app inside a folder
4. Back in edit mode, with and without a folder expanded
5. Drag during a live flip; process death mid-drag
6. Drag across several occupied cells without stopping; release exactly as the dwell ends; a wide dragged tile
   whose centre sits over a small tile
7. A layout store written by an older shell build (v2 → v3 upgrade keeps the layout)
8. Secondary tiles: request while Start is not visible; duplicate `tileId`; owner uninstalled with tiles in a
   folder; owner update changes the arguments
9. Bottom row: drop onto a full row; a wide tile dragged in; the row's last tile dragged out; show-more-tiles
   toggled with the row full

## NEEDS-HUMAN

H1–H23 are Jeremy's to judge; `NEEDS-HUMAN.md` lists each with the capture that shows the behaviour.
