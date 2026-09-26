# Phase 13 QA — Fluent materials

Specific tests only (INDEX Change Log 2026-09-25, "QA RULING, ALL PHASES"): each row runs as the build task it covers
lands; Jeremy runs the whole gate once at the end of the project. Drivers: `scripts/` (`lib.sh` and `within.py` are
phase 03's, symlinked; `p13.sh` holds this phase's shared helpers; `battery_saver_on` / `_off` are in `lib.sh`, owned
here). All rows on AVD tileshell_fhd, emulator-5554.

| Row | Result | Evidence | Notes |
|---|---|---|---|
| unit | 11/11 | `app/build/test-results/.../TEST-app.tileshell.ui.fluent.FluentMaterialTest.xml` (run 2026-09-25 14:16) | derivation per table row + negative branch; the rule on all 8 combinations; noise range / spread / determinism |
| E1 | PASS 24/24 (+1 recorded) | `E1/E1.txt` | apk c2ac10ea; battery saver line 12 ms after BS_MARK, the switch's 117 ms after its MARK |
| E2 | PASS 22/22 (+2 recorded) | `E2/E2.txt`, `E2/profile-*.txt` | edge spread 130.2 px (FHD+) / 94.4 px (720 wide); profiles within 2.7 / 1.5 levels of the host oracle |
| E3 | PASS 34/0 (+1 recorded) | `E3/E3.txt` | apk 15aa7f5f; the fixture attaches the photo through the product (L13-1 fixed); `E3-run1-label/` = the first run (two assertion labels differed) |
| E4 | PASS 48/0 | `E4/E4.txt` | apk 15aa7f5f; adds a noise discriminator to (1) and (3), whose 0.2*B sits within +-4 of the solid fill; `E4-run1-nodiscriminator/` = the run before it |
| E5 | PASS 28/0, live sub-step NOT RUN | `E5/E5.txt` | apk 15aa7f5f; the live sub-step needs Tess listening = the host microphone on these AVDs: waiting on Jeremy |
| E6 | PASS 11/0 (+1 recorded) | `E6/E6.txt` | noise std 3.21 levels, pixel-identical 1 s apart, 0 when off |
| E7 | 139/5 — BLOCKED (L13-2) | `E7/E7.txt` | (d): the sideways MOVE drags Start's pivot under the open band (L13-2, INDEX ledger); 1 FAIL was a driver fault (the Settings-row control's patch lay on its subtitle), fixed in `e7.sh`, not re-run |
| L13-2 repro | defect present on both | `L13-2-pre13-1ee619f8/`, `L13-2-current-15aa7f5f/` | `scripts/l13_2_repro.sh`; `L13-2-runs-unlabelled/` = the same runs before the repro APK was named in the log |
| E8, E9, E10, E13 | drivers written, not run | `scripts/e8.sh`, `e9.sh`, `e10.sh`, `e13.sh` | resume after L13-2 |
| E1, E2 | re-run owed | `scripts/e1.sh`, `e2.sh` | now save action-scoped slices for E12; E12 needs E1–E5 on one APK |
| E11, E12, edge cases | not written | — | resume after L13-2 |

`explore/` holds the build's first look at each surface (captures and dumps taken while the drivers were written; not
row evidence): the app list over the checker, the app-list menu band, Tess's pane over Home, the two lights on the held
Home item (U / D / M / A captures: ring (77,161,227), touch point (25,133,218), 0.5 r point (12,126,216) over the accent
(0,120,215); gone after the finger left).

L13-1 repro: `scripts/l13_1_repro.sh <apk> <label>` — `L13-1-pre13-1ee619f8/` on the APK built from 1ee619f8 (before this
phase), `L13-1-phase13-365bd248/` on this phase's (its pid check read the same pid after the crash; the crash count is the
primary evidence; `row-tap.txt`: a tap on an existing reminder row crashes too).
