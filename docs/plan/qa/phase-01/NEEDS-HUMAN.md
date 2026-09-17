# Phase 01 — what needs Jeremy's eyes (H1–H38)

The phase doc says a phase is only `done` when every NEEDS-HUMAN row has Jeremy's sign-off. Most of these are "does
this look and feel like Windows 10 Mobile", which no measurement settles. The phase doc writes them as "Jeremy on the
phone"; the ones marked EMULATOR below can already be judged from the captures listed here, and the rest wait for the
S25 Ultra build.

## Judge from what is already captured (emulator)

| Row | What to look at | Where |
|---|---|---|
| H1 | Start, tiles, app list overall | FINAL/fresh_start.png, E12/applist_top.png, E12/new_row_fixed.png |
| H6 | The unassigned tile look and the app picker | E04/e4b_picker_mail.png, EDGE/mail_picker_empty.png |
| H7 | Work and private groups, their glyphs and locked state | E18/applist_bottom.png, E18/applist_work_locked.png, E18/applist_private_locked.png |
| H8 | The P4 press and the WP8 tilt | E10/press_*_down.png, numbers in E10/press_measure.txt |
| H9 | Weather app pages | E09/weather_app_top.png, E09/weather_denver.png |
| H11 | Music now-playing face | EDGE/music_tile_playing.png |
| H12 | The 2-column grid (note: six default tiles fall outside it until phase 02 reflows) | E13/fewer_tiles_start.png |
| H13 | Transparency default 0.5 | E13/background_start.png |
| H15 | Settings page transition | E13 screenshots, motion in E10 |
| H16 | The jump grid | E12/jump_grid.png, E18/jump_grid_profiles.png |
| H19, H22 | The "New" caption and its rules | E12/new_row_fixed.png, E12/after_3_days.png |
| H24, H25 | Drawn status bar and nav keys | FINAL/fresh_start.png, E19 captures |
| H29 | Dark theme default | FINAL/fresh_start.png |
| H30 | Weather stale state, "Updated h:mm" | E09/stale_tile.png |
| H33 | The onboarding checklist | FINAL/checklist_fresh.png, FINAL/checklist_granted.xml |
| H34 | The keyboard over the drawn nav bar | X25/search_keyboard_later.png |
| H35 | The bottom tile row | FINAL/fresh_start.png, E21 captures |
| H36 | Out-of-box accent (measured 0,120,215 on a fresh install) | FINAL/fresh_start.png |
| H37 | The default tile arrangement | FINAL/fresh_start.png |
| H38 | White monochrome app glyphs, full colour where an app has no monochrome layer | FINAL/fresh_start.png, E04/start_default_slots.png |

## Wait for the phone

H2 (the system return-to-Start animation over ours), H3 (the system splash on cold start), H4 (accept the remaining
approximations), H5 (Selawik and Fluent as stand-ins), H10 (weather background keying), H14 (nav bar height on One
UI), H17 (light-theme accent variants), H18 (easing), H20, H21, H23, H26, H27, H28, H31, H32.

## Phone-only checks that are not H rows (P rows in the phase doc)

Samsung badge provider readability (P6), One UI Home as the crash-loop fallback, Device care optimise, 24-hour idle,
Secure Folder, a revealed Samsung bar over the drawn one, and the Live Tile API uninstall receiver while the shell is
stopped (adversarial F11).
