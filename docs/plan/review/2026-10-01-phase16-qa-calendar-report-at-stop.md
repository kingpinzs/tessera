<!-- Saved verbatim by the lead from the hand-back of the Calendar row writer (an Opus subagent; model output, not the owner's words), at the stop of 2026-10-01 18:1x. -->

CALENDAR QA REPORT — stopped at 18:13 MDT on your order (usage limit). The work is NOT finished: 2 of 13 rows have passed alone twice; 6 rows, all 17 EDGE sub-steps and my E21 share have not been run or written.

Everything is in `/home/jeremyking/projects/metro-launcher-p16/docs/plan/qa/phase-16/`. The pick-up file is `qa-cal-progress.md` (row state, what is next, device state). Nothing is committed or staged; no product code touched. Every run below logs `apk match yes (686506a7a7936b2e)`.

## Rows (PASS / FAIL / recorded)

| Row | Driver | Last run and folder | Passed alone, in a row | State |
|---|---|---|---|---|
| E3 | `scripts/e3.sh` | 68 / 0 / 1, `E3/` | 2 (`E3-run2-pass`, `E3`) | done |
| E4 | `scripts/e4.sh` | 44 / 0 / 3, `E4/` | 2 (`E4-run2-pass`, `E4`) | done, with two open clauses |
| E5 | `scripts/e5.sh` | 43 / 0 / 3, `E5/` | 1 | needs one more run |
| E17 | `scripts/e17.sh` | 65 / 0 / 5, `E17/` (17:35 MDT) | 1 | needs a run after 18:00 MDT (see below) |
| E18 | `scripts/e18.sh` | 61 / 0 / 4, `E18/` | 1 | needs one more run |
| E22 | `scripts/e22.sh` | 122 / 0 / 1, `E22-run1-pass-before-leg-L-was-added/` | 0 | leg L (F15) was added after this run and has never run |
| E23 | `scripts/e23.sh` | 58 / 1 / 2, `E23-run1-driver-no-sync-file-after-pm-clear/` | 0 | the one failure was the driver's (fixed, not re-run) |
| E24 | `scripts/e24.sh` | 134 / 3 / 6, `E24-run1-driver-series-rows-matched-by-original-id/` | 0 | the three failures were the driver's (fixed, not re-run) |
| E6 | `scripts/e6.sh` | never run | 0 | legs (a)–(e) written, leg (e) with your two guards |
| E9 | `scripts/e9.sh` | never run | 0 | |
| E8 | `scripts/e8.sh` | never run | 0 | |
| E7 | `scripts/e7.sh` | never run | 0 | |
| E19 | `scripts/e19.sh` + `scripts/cal_geo.py` | never run | 0 | measurement code tried only on exploration captures |

Earlier runs kept: `E3-run1-driver-count-helper-printed-two-lines`, `E4-run1-pinned-tile-compared-at-different-sizes`, `E5-run1-driver-agenda-walk-flung-past-day-groups`.

- **E24's three failures:** the product's rows were correct (master with rrule and `PT1H`, one exception pointing at the copy's master). My grep matched the master through the exception's `original_id=`.
- **E22 leg L and E24's F26 record** are in the drivers as you specified. F26 was recorded in E24 run 1 (below).

## Not run, not written
- **EDGE C01–C17:** all 17 `edge_C*` functions are in `scripts/cal_lib.sh`; none has ever run. Expect driver fixes.
- **E21:** `E21/producers.tsv` and `E21/notrun.tsv` do not have my lines; neither file exists yet. The intended list is in `qa-cal-progress.md`.
- **Edge index gap:** `edge_index.tsv` has no sub-step for the EDGE row's own producer of `write update event=<id>: failed <err>`. I put it in the second half of `edge_C11`.

## Defect files
None of mine. No product clause has failed in a kept run.

## Findings
- **F26 (recorded, E24 run 1):** after syncing a weekly series (COUNT=5) with one retitled occurrence, the provider's `instances` for the copy in Personal holds 1 row over the series' weeks, not 5. Evidence: `E24-run1-…/W-copy-instances.txt`. The shell's own views showed each occurrence once.
- **AOSP Calendar (E24):** it posted no notification of its own for the synced event; the copy's alert row stayed at state 0. Recorded.
- **Pinned Calendar tile (E4):** a tile pinned from the app list is MEDIUM and shows only the day face, because phase 01 draws event lines on WIDE tiles only (`start/TileView.kt:459`). This needs the owner's call.
- **Unnamed diagnostics lines:** the code writes `[calendar]` lines that neither the spec nor the Change Log names, for example `edit <id>: not a Tessera event, opened read-only`, `sync mappings dropped …`, `reminder poke: nothing read (READ_CALENDAR)`, `birthdays: not synced (…)`, `permission request …`, `open <action> -> <route>`. The full list is in `qa-cal-progress.md`.
- **Time-of-day dependencies in the spec's fixtures:**
  - E4's tomorrow-09:00 Standup is inside the tile's 24 hours only after 09:00.
  - E9's tomorrow-2pm dentist is inside Tess's read only after 14:00.
  - E17's tile and Tess clauses read `Instances` over now to now + 24 h, so today's all-day birthday should drop out at 18:00 MDT. E17 passed at 17:35; the after-18:00 run was not made.
- **E19, from exploration captures only (not evidence):** measured at the ink edge, the pane's account-header chevron is at x 16.33 against R11's 14, and titles sit about 1.2 epx right of 92.5. Motions read 190 ms and 264 ms, both inside tolerance.

## `clauses-open.tsv` lines of mine
1. **E4,** "with 'Dentist' inside the feed's 24 hours": the row's Dentist is at 14:00 today and already deleted by then. The driver inserts a second Dentist 2 h ahead.
2. **E4,** "the pinned tile's dump holds the same face text as the CALENDAR slot tile": the driver compares at the slot tile's size (WIDE). At the pinned size it asserts the day face and records both tiles' texts.
3. **E22,** "the write guard's JVM test runs and passes": the driver reads your result file `TEST-app.tileshell.calendar.CalendarWriteGuardTest.xml` and makes 14 per-case assertions.

Three more are owed and not yet written, because their rows have not run:
- E6: `adb reboot` should put the clock back on the host's, so the driver restores the clock before leg (d).
- E9: phase 03's `e7.sh`, `e10.sh` and `e14.sh` speak every request, so legs G and N use typed requests instead.
- E19: text positions are asserted at the ink's edge, using R11's own sample glyphs.

## Device state left
Read under the lock after E18 finished; nothing of mine is running and the lock is free.
- Build 686506a7a7936b2e installed, Start on top, layout equal to `baseline_layout.json`.
- One calendar (Tessera, `_id=1`), no events, no alert rows.
- Contacts: Mom and the pre-existing unnamed `_id=2` only; no birthday rows.
- READ/WRITE_CALENDAR, READ/WRITE_CONTACTS and POST_NOTIFICATIONS granted.
- `com.android.calendar` and `com.android.providers.calendar` enabled.
- Zone America/Boise, `time_12_24` null, `auto_time` 1, clock 1 s from the host, lock screen disabled.
- No calendar notification and no pending alarm of the shell's.
- One difference from the start: `files/calendar_sync.json` does not exist (it held empty lists before). E3, E23 and E24 end on `pm clear` → `provision.sh`.

The other writer held the device about half the time.
