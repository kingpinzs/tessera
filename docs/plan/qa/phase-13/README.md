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
| E3 | BLOCKED (L13-1) | `FIXPROBE/`, `L13-1-*/` | the fixture cannot attach a photo: see INDEX's Blocked-on ledger |
| E4–E13, edge cases | not run | — | resume after L13-1 |

`explore/` holds the build's first look at each surface (captures and dumps taken while the drivers were written; not
row evidence): the app list over the checker, the app-list menu band, Tess's pane over Home, the two lights on the held
Home item (U / D / M / A captures: ring (77,161,227), touch point (25,133,218), 0.5 r point (12,126,216) over the accent
(0,120,215); gone after the finger left).

L13-1 repro: `scripts/l13_1_repro.sh <apk> <label>` — `L13-1-pre13-1ee619f8/` on the APK built from 1ee619f8 (before this
phase), `L13-1-phase13-365bd248/` on this phase's (its pid check read the same pid after the crash; the crash count is the
primary evidence; `row-tap.txt`: a tap on an existing reminder row crashes too).
