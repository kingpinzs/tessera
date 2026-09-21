# Phase 02 — NEEDS-HUMAN rows (Jeremy signs each one)

Hard Rule 15: anything only Jeremy can verify gets a row here, and `done` needs his sign-off on each. The
numbers are the phase doc's own H-rows. "Measured" values are proved in E7; these rows are about whether the
result **looks like W10M**, which no measurement can settle.

Status values: `waiting` (capture ready, not yet signed) · `signed` · `rejected (note)`.

| H | What Jeremy is judging | Source of the value | Capture | Status |
|---|---|---|---|---|
| H1 | Edit mode and folders feel like W10M, as a whole | — | E01, E03, E07, E08 captures + the phone | waiting |
| H2 | Any approximation not covered by H3–H23 | — | all captures | waiting |
| H3 | Unpin: the tile goes at once and the tiles after it fill the gap | approximation (R6 §1.2.8 UNMEASURED) | E01 `e1_after_unpin.png` | waiting |
| H4 | The reflow slide itself (≈300 ms, ease-out, never a jump or a fade) | LOW (R6 §1.3.2) | E08 `e8b_reflow_*` | waiting |
| H5 | The reflow while dragging over occupied cells | approximation (R6 §1.3.3) | E08 `e8b_reflow_at2600ms.png` | waiting |
| H6 | Resize medium → small and small → wide | LOW (R6 §1.4.1–§1.4.2) | E01 `e1_resize_*.png` | waiting |
| H7 | Resize wide → medium | approximation (no R6 footage) | E01 `e1_resize_3.png` | waiting |
| H8 | Selecting another tile in edit mode (≈185 ms) | LOW (R6 §1.5.4) | E07 capture | waiting |
| H9 | Back exits edit mode and leaves an expanded folder expanded | LOW (R6 §4.1.7) | EDGE `edge_back_*.png` | waiting |
| H10 | The folder-create feedback (the target's icon shrinking into a mini tile) | LOW (R6 §1.6.1) | E08 `e8a_folder_at1000ms.png` | waiting |
| H11 | The new folder auto-expanding (≈230 ms) with the placeholder showing | LOW (R6 §1.6.2) | E03 `e3_created_expanded.png` | waiting |
| H12 | The collapsed medium folder tile (mini tiles, 3 + 1 for four members) | LOW (R6 §1.6.3) | E03 `e3_face_3plus1.png` | waiting |
| H13 | The wide folder tile (mini grid, live content, numeric badge) | LOW (R6 §1.6.4) | EDGE wide-folder capture | waiting |
| H14 | The expanded band (rules, wallpaper showing, members at full size) | LOW (R6 §1.6.5) | E03 `e3_reexpanded.png` | waiting |
| H15 | The expand motion (≈375 ms, rows revealed top to bottom) | LOW (R6 §1.6.6) | E03 capture | waiting |
| H16 | The collapse motion (fold ≈133 ms, face back ≈100 ms, scroll back ≈370 ms) | LOW (R6 §1.6.7) | E03 `e3_collapsed.png` | waiting |
| H17 | The "Name folder" placeholder | LOW (R6 §1.7.1) | E03 `e3_created_expanded.png` | waiting |
| H18 | The name text box (white fill, ≈0.27 tile tall, keyboard up) | LOW (R6 §1.7.2) | E03 `e3_name_box.png` | waiting |
| H19 | A folder left with one tile dissolving into it | approximation (R6 §1.8.1 UNMEASURED) | E03 `e3_dissolved.png` | waiting |
| H20 | The 2000 ms dwell before the tiles make room | approximation (R6 §1.3.3) | E08 both paths | waiting |
| H21 | The app-list context menu | approximation | build task 3's capture | waiting |
| H22 | The secondary-tile confirmation band | approximation | build task 6's capture | waiting |
| H23 | Adding to a folder: the feedback, the band drop, no nesting | approximation | E03, E08 `e8e_nonesting_*` | waiting |

## Agent calls made during the build (recorded in the INDEX Change Log, not H-rows)

These are choices the phase doc did not settle and the build had to; they are listed here so Jeremy can
overrule any of them while he is looking at the same captures.

1. The fixed point of the contraction is 0.475 of the screen height (R6 gives 46–49 %).
2. The light theme dims the wallpaper by the same formula as its tiles (R6 measured only the dark-theme wallpaper).
3. Bottom-row tiles dim and shrink like grid tiles but do not contract, because the row is not in the grid.
4. In edit mode the pivot to the app list is locked, and Home leaves edit mode.
5. A tile dropped on a bottom-row tile reorders the row; it never makes a folder there.
6. Outside edit mode a tap on a folder tile expands or collapses it; inside edit mode it moves the selection.
7. A folder tile takes the size of the tile it was dropped on, and the resize cycle applies to folders too.
8. Dragging near the top or bottom edge scrolls Start.
9. The R6 §1.6.5 band insets are read as fractions of the medium tile side.
