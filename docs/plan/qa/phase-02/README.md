# Phase 02 QA gate — plan and evidence index

Rows come from `docs/plan/phase-02-edit-mode-folders.md` (FINAL). Every row starts from the baseline layout and
restores what it changes (PLAN RV12); motion rows are measured from screenrecord frames (RV11); dumps follow
RV13 (retried up to 3 times).

## Method note — why edit-mode geometry is measured from pixels

The edit-mode contraction is a `graphicsLayer` transform. Probed on 2026-09-21: a `uiautomator dump` reports a
Compose node's **layout** bounds, which are byte-identical in and out of edit mode, and a `testTag`-only node
(the dim overlay, the discs) does not reach the accessibility tree at all. So:

- **layout facts** (what tile exists, where it sits in the grid, what is in the bottom row, what moved) → dumps
  and the layout store's own JSON, which is the layout's truth;
- **edit-mode geometry and the discs** (0.835, 0.90, 1.00, the dim factors, the 31-epx discs) → screencap
  pixels, via `qa.py`;
- **timings** → screenrecord frames, with `show_touches` marking the touch-up for the exit latency.

## Scripts

| Script | What it drives |
|---|---|
| `scripts/gestures.sh` | one continuous touch stream (`input motionevent`), the 783-ms hold, `edit_point` / `corner_point` for edit-mode screen coordinates |
| `scripts/layout.sh` | read / save / restore the layout store (RV12) |
| `scripts/qa.py` | pixel measurement: tile rectangles, plate colours, disc detection, PASS/FAIL rows with an exit code |
| `scripts/e1.sh` | E1 — hold+drag as one gesture, resize disc cycle, unpin disc |
| `scripts/e3.sh` | E3 — folder create, name (placeholder + tap-and-hold), collapse/expand, 3rd and 4th member, dissolve |
| `scripts/e4.sh` + `dumpdiff.py` | E4 — force-stop and reboot leave the layout identical |
| `scripts/e7_capture.sh` + `e7_geometry.py` + `e7_motion.py` + `e7_bracket.py` | E7 — the measured values and the hold bracket |
| `scripts/e8.sh` | E8 — both drag paths, against a tile and against a folder, plus no-nesting |
| `scripts/e9.sh` + `e9_overflow.py` | E9 — bottom row editing, the full-row refusal, the column-change overflow |

## Rows

| Row | Owner | Status | Evidence |
|---|---|---|---|
| E1 long-press + drag, resize, unpin | lead | pending | `E01/` |
| E2 app-list menu → Pin to Start, "New" caption cleared | build task 3 | pending | `agent-tasks-3-5/`, re-run in `E02/` |
| E3 folders end to end | lead | pending | `E03/` |
| E4 force-stop + reboot keep the layout | lead | pending | `E04/` |
| E5 secondary tiles through the client library | build task 6 | pending | `E05/` |
| E6 uninstall drops the tile, `install -r` keeps it | build task 5 | pending | `agent-tasks-3-5/`, re-run in `E06/` |
| E7 edit-mode geometry and motion | lead | pending | `E07/` |
| E8 both drag paths | lead | pending | `E08/` |
| E9 bottom tile row editing | lead | pending | `E09/` |
| Edge cases | lead | pending | `EDGE/` |

## Edge cases (phase doc "Edge cases"), each with its own capture in `EDGE/`

1. Pinning an app already on Start; pinning when Start is long; resizing to wide at the right edge in 2-column mode
2. Changing show-more-tiles reflows a persisted layout without losing tiles — **the defect phase 01 recorded**
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

H1–H23 are Jeremy's to judge; they are collected in `NEEDS-HUMAN.md` as the rows are run, each with the capture
that shows the behaviour.
