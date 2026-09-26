# Phase 13 QA — Fluent materials

Specific tests only (INDEX Change Log 2026-09-25, "QA RULING, ALL PHASES"): each row runs as the build task it covers
lands; Jeremy runs the whole gate once at the end of the project. Drivers: `scripts/` (`lib.sh` and `within.py` are
phase 03's, symlinked; `p13.sh` holds this phase's shared helpers; `battery_saver_on` / `_off` are in `lib.sh`, owned
here). All rows on AVD tileshell_fhd, emulator-5554.

| Row | Result | Evidence | Notes |
|---|---|---|---|
| unit | 18/18 | `unit/TEST-*.xml` (FluentMaterialTest 11/11, StaticBackdropTest 3/3, SingleFlightTest 4/4); the red runs: `unit/red-StaticBackdropTest-old-handling-gradle.txt`, `unit/red-SingleFlightTest-non-sharing-stub-gradle.txt` | StaticBackdropTest: a cancelled build is not a failure (A-B1); SingleFlightTest: the picture decoded once (round 2 A) |
| E1 | PASS 24/0 (+1 recorded) | `E1/E1.txt`, `E1/slice-*.txt` | apk 0eb36905; step slices for E12. Earlier: `E1-run1-c2ac10ea/`, `E1-run2-stopped-120e52ec/`, `E1-run3-db078d6a/`, `E1-run-509f6e49-pre-decode-fix/` |
| E2 | PASS 22/0 (+2 recorded) | `E2/E2.txt`, `E2/profile-*.txt` | apk 0eb36905. Earlier: `E2-run1-c2ac10ea/`, `E2-run2-db078d6a/`, `E2-run-509f6e49-pre-decode-fix/` |
| E3 | PASS 34/0 (+1 recorded) | `E3/E3.txt` | apk 15aa7f5f (no reminder-menu change since; E12's file-list check); `E3-run1-label/` = the first run |
| E4 | PASS 48/0 | `E4/E4.txt` | apk 0eb36905. Earlier: `E4-run1-nodiscriminator/`, `E4-run2-15aa7f5f/`, `E4-run3-db078d6a/`, `E4-run-509f6e49-pre-decode-fix/` |
| E5 | PASS 45/0 (+1 recorded) | `E5/E5.txt` | apk 15aa7f5f; live sub-step (mic allowed), threshold re-cut (INDEX Change Log); earlier runs E5-run1..4 |
| E6 | PASS 11/0 (+1 recorded) | `E6/E6.txt` | apk 15aa7f5f; noise std 3.21 levels, pixel-identical 1 s apart, 0 when off |
| E7 | PASS 144/0 (+1 recorded) | `E7/E7.txt` | apk 120e52ec (the L13-2 fix): (d) 20/20; the blocked run on 15aa7f5f (139/5) is `E7-run1-L13-2-15aa7f5f/` |
| E8 | PASS 32/0 (+8 recorded) | `E8/E8.txt`, `E8/motion-lines.txt`, `E8/captures.sha256` | apk 509f6e49; shell clock: pane 245-254, reminder menu 242-244, pivot 263-264, band 8-21 ms (the asserted runs), maxGapMs <= 33.4; corroboration retaken under phase 05's rule inside each surface: pane attempt 1 (17.1 ms, window 233 vs settle 246), reminder menu attempt 3 (17.0 ms, 233 vs 243), pivot attempt 2 (18.0 ms, 500 >= 263), band a one-frame jump; the .mp4 captures stay local, their hashes are kept. Earlier: `E8-run1-120e52ec/` (28/0, corroboration never retaken) |
| E9 | recorded only (7 facts) | `E9/E9.txt`, `E9/gfxinfo.txt` | apk 509f6e49; 20 reminder-menu opens and 20 pivot settles in the ring; 3.15 % janky, p90 19 ms, p99 24 ms. Earlier: `E9-run1-120e52ec/` (ring lost to the force-stop) |
| E10 | PASS 14/0 (+5 recorded) | `E10/E10.txt` | apk 0eb36905; the picture held once (B - A 10 979 KB against 9871 KB for one decode), C - B 9239 KB, A2 - A 986 KB. Earlier: `E10-run1-layer-while-off-120e52ec/` (fixed 3448c92f), `E10-run2-photos-face-db078d6a/`, `E10-run3-settings-code-db078d6a/`, `E10-run4-db078d6a/`, `E10-run-509f6e49-pre-decode-fix/` (B - A 20 552 KB: two decodes, fixed 9c4d049f); diagnosis `E10_DIAG/` |
| E11 | PASS 47/0 (+1 recorded) | `E11/E11.txt`, `E11/1-e15-compare.txt` | apk db078d6a; (1) phase 03 E15 before / after phase 13: no clause regressed, the 6 failing both read the same values; (2) Pin 12/12; (3) Music menu open / selection / dismissal; (4) search 'Cale' -> Calendar, jump grid, E19 bars 28 / 48 epx; (5) exports = allow-list. Earlier: `E11-run1-empty-query-db078d6a/` (search typed nothing), `E11-run2-music-dump-db078d6a/` (dump caught Start after Done; the id now comes from the store) |
| E11_MENU | PASS 13/0 | `E11_MENU/E11_MENU.txt` | E15's reminder-menu geometry and selection on this build (E15's spoken setup makes no row): 243.33 x 107.0 epx, 23.33 epx above the touch, Delete with no card, 3 rows -> 2. Earlier: `E11_MENU-run1-interrupted/` (my force-stop mid-row), `E11_MENU-run2-grep-c/` (a dump is one line, so `grep -c` counted 1 row; the same count is in phase 03's e15.sh) |
| E12 | PASS 53/0 | `E12/E12.txt` | E1 E2 E4 on 0eb36905, E3 E5 on 15aa7f5f, each with `apk match yes`; asserts the c73c32cf..9c4d049f file list. Earlier: `E12-run1-db078d6a/`, `E12-run-509f6e49-pre-decode-fix/`, `E12-run-0eb36905-no-match-check/` |
| E13 | PASS 10/0 | `E13/E13.txt` | apk 0eb36905. Earlier: `E13-run1-fullwidth-120e52ec/`, `E13-run2-db078d6a/`, `E13-run-509f6e49-pre-decode-fix/` |
| BS_STEP | PASS 7/0 (+2 recorded) | `BS_STEP/BS_STEP.txt` | apk 0eb36905; the isolated step 128.8 px, sigma 50.25 vs 52.46. Earlier: `BS_STEP-run1-mark-late/`, `BS_STEP-run-db078d6a-pre-decode-fix/` |
| B1_PROBE | before: FAIL (the false line); after: 7/0 x3 | `B1_PROBE-before-db078d6a/`, `B1_PROBE-after-509f6e49-{1,2,3}/` | the switch off / on / off ~65 ms apart, then on: db078d6a logs `static backdrop failed … LeftCompositionCancellationException`; 509f6e49 never does and rebuilds after the final on |
| phase 03 E15 (E11's dependency) | 21/6 | `../phase-03/E15/E15.txt` | apk db078d6a; the same 6 clauses fail on the pre-13 build (`../phase-03/E15-run2-a437acd5/`), phase 03's own gate |

**Edge cases** (`scripts/edge.sh <case>` -> `EDGE_<CASE>/`; the others are executed by rows: file deleted = E13, toggle while
the app list shows = E1, surface over acrylic = E4 (1), disable_window_blurs = E1, low-RAM = the unit test, burst = dropped):

| Case | Result | Evidence | Notes |
|---|---|---|---|
| saver_menu | PASS 12/0 | `EDGE_SAVER_MENU/` | fallback line 22 ms after acrylic=off; menu open throughout |
| bg_change | PASS 16/0 | `EDGE_BG_CHANGE/` | apk 0eb36905. Earlier: `EDGE_BG_CHANGE-run1-uri-form/`, `EDGE_BG_CHANGE-run-db078d6a-pre-decode-fix/` |
| huge | PASS 7/0 | `EDGE_HUGE/` | apk 0eb36905: B 150 MB (was 212 MB: the 4000 x 4000 decode held twice), C - B 9072 KB, pid unchanged. Earlier: `EDGE_HUGE-run1-uri-form/`, `EDGE_HUGE-run-db078d6a-pre-decode-fix/` |
| no_image | PASS 5/0 | `EDGE_NO_IMAGE/` | apk 0eb36905. Earlier: `EDGE_NO_IMAGE-run1-mark-late/`, `EDGE_NO_IMAGE-run-509f6e49-pre-decode-fix/` |
| light | PASS 10/0 | `EDGE_LIGHT/` | apk 0eb36905: rebuilt for the bright picture, edge spread 155.4 px (blurred), tint (255,255,255), backdrop >= 200, text 0. Earlier: `EDGE_LIGHT-run1-tint-recorded/`, `EDGE_LIGHT-run2-509f6e49-mark-late/` |
| edit_mode | PASS | `EDGE_EDIT_MODE/` | pivot locked, exit by a tap elsewhere, no rebuild, layout unchanged. `EDGE_EDIT_MODE-run1-back-kept-edit/` = Back closes only the burst |
| screen_off | PASS | `EDGE_SCREEN_OFF/` | Asleep -> Awake, menu open, patches equal before / after (82-88), noise 3.22. `EDGE_SCREEN_OFF-run1-photo-tag/` = wrong tag, aborted before restore (cleaned by `CLEANUP_REMINDERS/`) |
| process_death | PASS | `EDGE_PROCESS_DEATH/` | new pid, no menu, switch Off survived |
| rv10 | PASS 29/0 | `EDGE_RV10/` | 1440 wide 170.1 px (r 120), density / font unchanged, band 84.0 epx everywhere |
| a11y | PASS 9/0 (+2 recorded) | `EDGE_A11Y/` | Remove animations keeps acrylic; high-text / inversion captures for H1 (screencap may not show the inversion) |
| rapid | PASS 5/0 (+5 recorded) | `EDGE_RAPID/` | apk 0eb36905: 10 holds, 10 band show lines, 11.9 s, PSS +1382 KB, GPU 28.56 -> 30.88 MB (recorded). Earlier: `EDGE_RAPID-run1-no-show-assert/`, `EDGE_RAPID-run2-509f6e49-no-gpu-record/` (10/10), `EDGE_RAPID-run3-0eb36905-back-race/` and `-run4-` (1 of 10: a hold right after Back; see the INDEX Change Log), probe `EDGE_RAPID-probe-back-race-0eb36905/` |
| many_apps | PASS | `EDGE_MANY_APPS/` | 300 installed (341 entries), one rebuilt line, backdrop fixed under the scroll (its right-margin column swings 1-51 with the checker, identical after), scroll 3.80 % janky / p99 16 ms, all uninstalled. `EDGE_MANY_APPS-run1-sdk0/` = targetSdk 0 refused |

`explore/` holds the build's first look at each surface (captures and dumps taken while the drivers were written; not
row evidence): the app list over the checker, the app-list menu band, Tess's pane over Home, the two lights on the held
Home item (U / D / M / A captures: ring (77,161,227), touch point (25,133,218), 0.5 r point (12,126,216) over the accent
(0,120,215); gone after the finger left).

L13-1 repro: `scripts/l13_1_repro.sh <apk> <label>` — `L13-1-pre13-1ee619f8/` on the APK built from 1ee619f8 (before this
phase), `L13-1-phase13-365bd248/` on this phase's (its pid check read the same pid after the crash; the crash count is the
primary evidence; `row-tap.txt`: a tap on an existing reminder row crashes too).
