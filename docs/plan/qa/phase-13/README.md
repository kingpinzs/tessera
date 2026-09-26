# Phase 13 QA — Fluent materials

Specific tests only (INDEX Change Log 2026-09-25, "QA RULING, ALL PHASES"): each row runs as the build task it covers
lands; Jeremy runs the whole gate once at the end of the project. Drivers: `scripts/` (`lib.sh` and `within.py` are
phase 03's, symlinked; `p13.sh` holds this phase's shared helpers; `battery_saver_on` / `_off` are in `lib.sh`, owned
here). All rows on AVD tileshell_fhd, emulator-5554.

| Row | Result | Evidence | Notes |
|---|---|---|---|
| unit | 11/11 | `app/build/test-results/.../TEST-app.tileshell.ui.fluent.FluentMaterialTest.xml` (re-run 2026-09-26 00:05 with 3448c92f) | derivation per table row + negative branch; the rule on all 8 combinations; noise range / spread / determinism |
| E1 | PASS 24/0 (+1 recorded) | `E1/E1.txt`, `E1/slice-*.txt` | apk db078d6a; step slices saved for E12. Earlier: `E1-run1-c2ac10ea/` (24/24), `E1-run2-stopped-120e52ec/` (chain stopped mid-row) |
| E2 | PASS 22/0 (+2 recorded) | `E2/E2.txt`, `E2/profile-*.txt` | apk db078d6a; show slices saved for E12. Earlier: `E2-run1-c2ac10ea/` |
| E3 | PASS 34/0 (+1 recorded) | `E3/E3.txt` | apk 15aa7f5f (no reminder-menu change since; E12's file-list check); `E3-run1-label/` = the first run |
| E4 | PASS 48/0 | `E4/E4.txt` | apk db078d6a (re-run: L13-2 made the band modal). Earlier: `E4-run1-nodiscriminator/`, `E4-run2-15aa7f5f/` (48/0) |
| E5 | PASS 45/0 (+1 recorded) | `E5/E5.txt` | apk 15aa7f5f; live sub-step (mic allowed), threshold re-cut (INDEX Change Log); earlier runs E5-run1..4 |
| E6 | PASS 11/0 (+1 recorded) | `E6/E6.txt` | apk 15aa7f5f; noise std 3.21 levels, pixel-identical 1 s apart, 0 when off |
| E7 | PASS 144/0 (+1 recorded) | `E7/E7.txt` | apk 120e52ec (the L13-2 fix): (d) 20/20; the blocked run on 15aa7f5f (139/5) is `E7-run1-L13-2-15aa7f5f/` |
| E8 | PASS 28/0 (+8 recorded) | `E8/E8.txt`, `E8/motion-lines.txt`, `E8/corroboration-rerun.txt` | apk 120e52ec; pane 245-254, reminder menu 242-244, pivot 264 (x3, inside 250 +- 16.7), band 6 ms; every maxGapMs <= 33.4. Screenrecord corroboration re-analysed with the fixed burst window: pane 18.4, pivot 18.9, reminder menu 36.6 ms gaps -> rejected under phase 05's 18.2 ms rule, so the [motion] lines stand alone (C-5) |
| E9 | recorded only (4 facts) | `E9/E9.txt`, `E9/gfxinfo.txt` | apk 120e52ec; 3540 frames, 3.28 % janky, p90 18 ms, p99 24 ms (host GPU; P2 is the bound) |
| E10 | PASS 13/0 (+4 recorded) | `E10/E10.txt` | apk db078d6a; C - B 9466 KB, A2 - A 1549 KB. Earlier: `E10-run1-layer-while-off-120e52ec/` (the product defect, fixed 3448c92f), `E10-run2-photos-face-db078d6a/`, `E10-run3-settings-code-db078d6a/` (measurement controls, INDEX Change Log); diagnosis `E10_DIAG/` |
| E11 | PASS 47/0 (+1 recorded) | `E11/E11.txt`, `E11/1-e15-compare.txt` | apk db078d6a; (1) phase 03 E15 before / after phase 13: no clause regressed, the 6 failing both read the same values; (2) Pin 12/12; (3) Music menu open / selection / dismissal; (4) search 'Cale' -> Calendar, jump grid, E19 bars 28 / 48 epx; (5) exports = allow-list. Earlier: `E11-run1-empty-query-db078d6a/` (search typed nothing), `E11-run2-music-dump-db078d6a/` (dump caught Start after Done; the id now comes from the store) |
| E11_MENU | PASS 13/0 | `E11_MENU/E11_MENU.txt` | E15's reminder-menu geometry and selection on this build (E15's spoken setup makes no row): 243.33 x 107.0 epx, 23.33 epx above the touch, Delete with no card, 3 rows -> 2. Earlier: `E11_MENU-run1-interrupted/` (my force-stop mid-row), `E11_MENU-run2-grep-c/` (a dump is one line, so `grep -c` counted 1 row; the same count is in phase 03's e15.sh) |
| E12 | PASS 48/0 | `E12/E12.txt` | E1 E2 E4 on db078d6a, E3 E5 on 15aa7f5f (asserts the c73c32cf..3448c92f file list) |
| E13 | PASS 10/0 | `E13/E13.txt` | apk db078d6a; `E13-run1-fullwidth-120e52ec/` = the driver's full-width scan hitting the rows' icons |
| BS_STEP | PASS 7/0 (+2 recorded) | `BS_STEP/BS_STEP.txt` | the isolated half / half step: 128.8 px, sigma 50.25 vs 52.46; `BS_STEP-run1-mark-late/` = MARK after the restart |
| phase 03 E15 (E11's dependency) | 21/6 | `../phase-03/E15/E15.txt` | apk db078d6a; the same 6 clauses fail on the pre-13 build (`../phase-03/E15-run2-a437acd5/`), phase 03's own gate |

**Edge cases** (`scripts/edge.sh <case>` -> `EDGE_<CASE>/`; the others are executed by rows: file deleted = E13, toggle while
the app list shows = E1, surface over acrylic = E4 (1), disable_window_blurs = E1, low-RAM = the unit test, burst = dropped):

| Case | Result | Evidence | Notes |
|---|---|---|---|
| saver_menu | PASS 12/0 | `EDGE_SAVER_MENU/` | fallback line 22 ms after acrylic=off; menu open throughout |
| bg_change | PASS | `EDGE_BG_CHANGE/` | changed: rebuilt for the new picture, 0.2 x 128 in the strip, no checker edge; removed: no rebuild, (0,0,0). `EDGE_BG_CHANGE-run1-uri-form/` = the picker-URI and icon-patch driver faults |
| huge | PASS | `EDGE_HUGE/` | 8000 x 8000: rebuilt, C - B within 14 MB, pid unchanged. `EDGE_HUGE-run1-uri-form/` = the URI-form driver fault |
| no_image | PASS | `EDGE_NO_IMAGE/` | solid (0,0,0), no layer, no failure line |
| light | PASS | `EDGE_LIGHT/` | tint (255,255,255), backdrop >= 200, text 0 |
| edit_mode | PASS | `EDGE_EDIT_MODE/` | pivot locked, exit by a tap elsewhere, no rebuild, layout unchanged. `EDGE_EDIT_MODE-run1-back-kept-edit/` = Back closes only the burst |
| screen_off | PASS | `EDGE_SCREEN_OFF/` | Asleep -> Awake, menu open, patches equal before / after (82-88), noise 3.22. `EDGE_SCREEN_OFF-run1-photo-tag/` = wrong tag, aborted before restore (cleaned by `CLEANUP_REMINDERS/`) |
| process_death | PASS | `EDGE_PROCESS_DEATH/` | new pid, no menu, switch Off survived |
| rv10 | PASS 29/0 | `EDGE_RV10/` | 1440 wide 170.1 px (r 120), density / font unchanged, band 84.0 epx everywhere |
| a11y | PASS 9/0 (+2 recorded) | `EDGE_A11Y/` | Remove animations keeps acrylic; high-text / inversion captures for H1 (screencap may not show the inversion) |
| rapid | PASS | `EDGE_RAPID/` | 10 holds in 8.9 s, PSS +719 KB |
| many_apps | PASS | `EDGE_MANY_APPS/` | 300 installed (341 entries), one rebuilt line, backdrop fixed under the scroll (its right-margin column swings 1-51 with the checker, identical after), scroll 3.80 % janky / p99 16 ms, all uninstalled. `EDGE_MANY_APPS-run1-sdk0/` = targetSdk 0 refused |

`explore/` holds the build's first look at each surface (captures and dumps taken while the drivers were written; not
row evidence): the app list over the checker, the app-list menu band, Tess's pane over Home, the two lights on the held
Home item (U / D / M / A captures: ring (77,161,227), touch point (25,133,218), 0.5 r point (12,126,216) over the accent
(0,120,215); gone after the finger left).

L13-1 repro: `scripts/l13_1_repro.sh <apk> <label>` — `L13-1-pre13-1ee619f8/` on the APK built from 1ee619f8 (before this
phase), `L13-1-phase13-365bd248/` on this phase's (its pid check read the same pid after the crash; the crash count is the
primary evidence; `row-tap.txt`: a tap on an existing reminder row crashes too).
