# Phase 16 QA — the Calendar rows: running progress (the QA row-driver writer's own file)

STOPPED 2026-10-01 about 18:10 MDT on the lead's order (the account's usage limit). Everything below is the state at the stop.

Build under test: `app/build/outputs/apk/debug/app-debug.apk` md5 686506a7a7936b2e. Every run listed logs `apk match yes (686506a7a7936b2e)`.
Include: `scripts/cal_lib.sh` (helpers prefixed `c` / `tess_`; EDGE sub-steps `edge_C01`…`edge_C17`). Pixel helpers: `scripts/cal_px.py` (E8, E18), `scripts/cal_geo.py` (E19).
Run folders: `E<n>/` is the row's last run; an earlier run is kept beside it as `E<n>-run<k>-<what it found>`.

## Where each row stands

| Row | Driver | Runs so far (PASS / FAIL / recorded) | Passed alone, in a row | What is next |
|---|---|---|---|---|
| E3 | `scripts/e3.sh` | run 1 67/1/1 (driver helper), run 2 68/0/1, run 3 `E3/` 68/0/1 | **2** | nothing |
| E4 | `scripts/e4.sh` | run 1 38/3/1 (pinned tile compared at different sizes — see clauses-open), run 2 44/0/3, run 3 `E4/` 44/0/3 | **2** | nothing (owner's call on the MEDIUM pinned tile) |
| E5 | `scripts/e5.sh` | run 1 40/3/3 (driver: agenda walk flung past day groups), run 2 `E5/` 43/0/3 | 1 | one more run |
| E22 | `scripts/e22.sh` | run 1 122/0/1 — BEFORE leg L (fix-round F15) was added; kept as `E22-run1-pass-before-leg-L-was-added` | 0 with leg L | run it (leg L has never run); if leg L yields `write update … failed refused (not allowed)`, move that alternative from `E21/notrun.tsv` to `producers.tsv` |
| E23 | `scripts/e23.sh` | run 1 58/1/2 (driver: no calendar_sync.json after pm clear read as "(no file)"; fixed) kept as `E23-run1-driver-no-sync-file-after-pm-clear` | 0 | run twice |
| E24 | `scripts/e24.sh` | run 1 134/3/6 (driver: the copy's master row was matched by the exception's `original_id=`; fixed) kept as `E24-run1-driver-series-rows-matched-by-original-id`. The product rows were right. | 0 | run twice |
| E17 | `scripts/e17.sh` | run 1 `E17/` 65/0/5, at 17:35 MDT | 1 | run again AFTER 18:00 MDT (see "time of day" below) |
| E18 | `scripts/e18.sh` | run 1 `E18/` 61/0/4 (finished 18:12 MDT, the last thing run) | 1 | one more run |
| E6 | `scripts/e6.sh` (legs a–e; leg (e) with the lead's two guards) | never run | 0 | run; leg (e) is expected to FAIL until the Q-16-4 build (report so, no defect file) |
| E9 | `scripts/e9.sh` | never run | 0 | run; children J6, J6b run before row_begin |
| E8 | `scripts/e8.sh` | never run | 0 | run |
| E7 | `scripts/e7.sh` | never run (insert speed probed: 160 `content insert` in 11.3 s with 16 workers → about 6 min for 5,000) | 0 | run |
| E19 | `scripts/e19.sh` + `scripts/cal_geo.py` | never run on the device. `cal_geo.py` was developed against exploration captures (not evidence) | 0 | run; expect FAILs on ink positions (below) → defect files |
| EDGE C01–C17 | all 17 `edge_C*` functions are in `scripts/cal_lib.sh` | none has ever run | – | `EDGE_ONLY=C01,… bash scripts/edge.sh`; expect driver fixes |
| E21 share | NOT written | – | – | `E21/producers.tsv`, `E21/notrun.tsv` (list below) |

## Findings so far (none is a defect file yet — no product clause has failed in a kept run)
- **E24 / F26 (recorded, as the lead asked):** after the Sync of a weekly series (COUNT=5) with one retitled occurrence, the provider's own `instances` for the COPY in Personal holds **1** row over the series' weeks (the exception, "E24 weeklyX"), not 5. In `E24-run1-…/W-copy-instances.txt`. The shell's own views showed each occurrence once.
- **E24:** the AOSP Calendar posted no notification of its own for the synced event's alerts (the copy's alert row stayed state 0); recorded.
- **E4:** a Calendar tile pinned from the app list is MEDIUM and shows only the day face; event text is drawn on WIDE tiles only (phase 01's rule). clauses-open.tsv, for the owner.
- **Diagnostics lines in the code that neither the spec nor the Change Log names** (a finding for the lead; they need a producer or a notrun line if E21 is to cover them): `edit <id>: not a Tessera event, opened read-only`; `instances query failed`; `alerts query failed`; `sync mappings dropped (the local event is gone): […]`; `can sync to: n calendar(s) no longer on this phone left the list`; `the provider cannot be observed`; `reminder poke: nothing read (READ_CALENDAR)`; `birthdays: not synced (<reason>)` (three forms); `permission request <p>: granted|denied`; `the permission will not be asked again: opening the app's settings`; `CalendarActivity created`; `open <action> -> <route>`; `calendar_sync.json could not be read` / `could not be written`; `[motion] appbar_menu`.
- **E19, predicted from the exploration captures (NOT evidence, the row has not run):** measured at the ink's edge as R11 measured, the pane's account-header chevron starts at x 16.33 (R11: 14), and text sits right of R11's x by its glyph's side bearing (title "Geo…" 93.67 against 92.5; the text BOX is at 92.67). The editor's Title / Location boxes are drawn x 20–340 while its pick boxes are x 12–201. Motions read 190 ms (drop-down, 200 ± 17) and 264 ms (day page, 250 ± 17).

## Time-of-day dependencies in the spec's own fixtures (tell the lead)
- E4: the "tomorrow 09:00" Standup is inside the tile's 24 hours only when the row runs after 09:00.
- E9: the "tomorrow at 2 pm" dentist is inside Tess's 24-hour read only after 14:00 (the driver also inserts "E9 checkup" one hour ahead).
- E17: the tile's and Tess's reads are `Instances` over [now, now + 24 h]; an all-day event's instance is UTC [00:00, 24:00) of its date, so in a zone behind UTC TODAY's birthday leaves that range at 18:00 MDT. E17 passed at 17:35. If it fails after 18:00 on "the tile shows it today" / "Tess names it", that is a product finding (phase 14's pod already reads from the start of today).

## clauses-open.tsv lines of mine
Written: E4 ×2, E22 ×1. Still to add when their rows run:
- E6: "every clock move here is FORWARDS" against leg (d) — `adb reboot` puts the emulator's clock back on the host's, so the driver runs `clock_restore` before leg (d) and takes its MARK after boot.
- E9: phase 03's `e7.sh`, `e10.sh`, `e14.sh` speak every request (speak.sh), and no row may use the microphone — legs G and N assert the same with typed requests.
- E19: text positions are asserted at the ink's left edge with R11's own sample glyphs; 1-epx / 2-epx lines are held to one device pixel.

## E21 — what my share must hold (not written yet)
producers: `calendars: n (local created id=…)`, `(local present)` → E3; `calendars: none (…)` → EDGE C01; `(local missing: WRITE_CALENDAR)`, `denied (READ_CALENDAR)` → E18; `view agenda|day|week` → E5 / E4 / E7; `write insert|update|delete … ok` → E4, E22; `write update … failed <err>` → EDGE C11 (and E22 leg L); `reminder … notified`, `dismissed` → E6; `skipped (synced copy)` → E24; `birthdays: n synced`, `birthdays calendar could not be created` → E17; `local calendar created`, `lookup failed`, `could not be created` → E3, E18; sync `ok`, `updated`, `recreated`, `failed calendar gone`, `no calendar allowed -> can sync to` → E24 / E23; `failed mapping stale` → EDGE C04; `failed calendar read-only` → EDGE C07; `counts: …` → E3 / E22 (the pane opened); `[motion] cal_month_dropdown`, `cal_day_page` → E19; `reminder: n skipped (due before the shell's first start)` and `reminders count from <ms> (the shell's start: first on this install)` → E6 (the Q-16-4 build); `… the clock was set back behind <old>` → EDGE C16 if it appears, else notrun with CalendarRulesTest.
notrun: `sync … refused (not allowed)`, `write … failed refused (not allowed)` (CalendarWriteGuardTest; the second moves to producers if E22 leg L produces it); `sync … failed <err>` (no device producer found); `calendars: n (local missing: it could not be created)` and `reminder …: failed notifications are off` (the builder's additions — no producer written; the second could be produced by revoking POST_NOTIFICATIONS in EDGE C13).

## Notes for whoever picks this up
- Scripts that may be running are edited only by write-temp-and-rename (a running bash keeps the old file).
- The device is shared: the other writer's rows held the lock about half the time. A row's first run found a driver bug in 5 of 7 rows; plan two runs each plus the two "alone, twice" passes.
- Scratch helpers (session scratchpad, not evidence): `qacal-row.sh <row>`, `qacal-locked.sh <script>`, `qacal-queue.sh`; exploration captures in `qacal-shots/`.
- The lead's messages of 2026-10-01, all applied in the drivers: E6 leg (e) with its two guards and three E21 lines; E22 leg L (F15); E24's F26 record.
- `edge_index.tsv` names no sub-step for the EDGE row's own producer of `write update event=<id>: failed <err>`; it is the second half of `edge_C11`.

## Device state left at the stop (read under the device lock, 18:13 MDT, after E18 finished)
Build 686506a7a7936b2e installed; one calendar (Tessera, `_id=1`), no events, no alert rows; contacts Mom and the pre-existing unnamed `_id=2` only; no birthday row; READ/WRITE_CALENDAR, READ/WRITE_CONTACTS, POST_NOTIFICATIONS granted; `com.android.calendar` and `com.android.providers.calendar` enabled; zone America/Boise, `time_12_24` null, `auto_time` 1, clock 1 s from the host; lock screen disabled; no app locale; no calendar notification of the shell's; no pending alarm of the shell's; Start on top; the layout equals `baseline_layout.json`. `files/calendar_sync.json` does not exist (rows E3 / E23 / E24 end on `pm clear` → `provision.sh`; the shell writes it at first use) — at the session's start it held empty lists. Nothing of mine is running and the lock is free.
