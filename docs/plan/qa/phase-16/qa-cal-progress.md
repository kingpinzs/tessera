# Phase 16 QA — the Calendar rows: running progress (the QA row-driver writer's own file)

Last written 2026-10-02 02:08 MDT. Nothing of mine is running; my queue runner has exited.

Builds, in order: `686506a7` (first; every folder `E<n>-build-686506a7-…`), `3c1ad1e0e65bd919` (fix build 1, commit
6e524e56), `6009c0b14a87039f` (fix build 2, dacca941), `2150eba004db6757` (fix build 3, a91837fd — installed now).
The owner's ruling of 2026-10-01 22:14 ("Only test the fixes not EVERY THING"): a row that passed once on a fix build
stands; a failed row is fixed and run again narrowly; EDGE is one grouped run plus single re-runs of what failed.
`E21/builds.txt` (the lead's) counts 3c1ad1e0 and 6009c0b1 runs beside the installed build's.

Include: `scripts/cal_lib.sh` (helpers prefixed `c` / `tess_`; EDGE sub-steps `edge_C01`…`edge_C17`). Pixel helpers:
`scripts/cal_px.py` (E8, E18, C14), `scripts/cal_geo.py` (E19). Narrow modes: `E9_LEGS=A,W`, `E19_LEGS=1`,
`E7_LEGS=jank` (fixtures, M, W, P and the verdict), `E7_LEGS=baseline` (a diagnosis on an empty calendar, no verdict);
every one ends through the row's restore and `row_end`.

## Where each row stands

| Row | The run that is its evidence (PASS / FAIL / recorded, build, time) | Other kept runs on the fix builds | State |
|---|---|---|---|
| E3 | `E3/` 68/0/1, 2150eba0, 01:43 | `E3-run1-on-3c1ad1e0-68-0-1/` 68/0/1 | PASS (run again on build 3: the Agenda's list is new code) |
| E4 | `E4/` 44/0/3, 3c1ad1e0, 22:02 | – | PASS |
| E5 | `E5/` 52/0/4, 3c1ad1e0, 22:33 | – | PASS (leg I = F29) |
| E6 | `E6/` 73/0/8, 3c1ad1e0, 23:34 | `E6-run1-leg-e-precondition-failed-pm-clear-restarts-the-shell/` 62/9/8 | PASS (all five legs; leg (e) uninstalls — clauses-open) |
| E7 | NONE passes. `E7-run2-per-leg-diagnosis-janky-14.38-percent/` 40/1/19, 6009c0b1, 00:37 (the whole row) + `E7-legs-jank-on-2150eba0-32-1-21-janky-14.58-percent/` 32/1/21, 2150eba0, 01:36 (fixtures, M, W, P) | `E7-run1-janky-frames-14-percent/` 40/1/3 (3c1ad1e0); baselines, not gate rows: `E7-baseline-empty-calendar-49-of-435-janky-11.26-percent/` 30/0/20 (6009c0b1), `E7-baseline-empty-calendar-on-2150eba0-48-of-432-janky-11.11-percent/` 30/0/21 | **FAIL on one clause** in every run: janky frames ≤ 5 % (14.19 %, 14.38 %, 14.58 %). Every other clause passes. D-E7-1; the clause's measure is with the owner |
| E8 | `E8/` 33/0/4, 3c1ad1e0, 00:04 | – | PASS |
| E9 | `E9-run2-dentist-outside-tess-24-hours-at-0026/` 69/1/3, 6009c0b1, 00:25 (the whole row) + `E9-legs-A-W-on-2150eba0-17-0-4/` 17/0/4, 2150eba0, 01:54 (legs A, W) | `E9-run1-add-title-keeps-the-phrase/` 54/13/2 (3c1ad1e0, D-E9-1); `E9-legs-A-W-run1-no-summary-the-restore-was-skipped-12-0/` (6009c0b1: no summary line, the restore did not run — a driver fault, fixed) | PASS as a part re-run: no single run of the whole row has 0 failures |
| E17 | `E17/` 65/0/6, 3c1ad1e0, 22:43 | – | PASS |
| E18 | `E18/` 61/0/4, 3c1ad1e0, 23:45 | `E18-run1-killed-mid-leg-C-by-my-runner/` unfinished (not a result) | PASS |
| E19 | `E19-run2-no-events-today-ink-at-25.67/` 235/1/22, 6009c0b1, 00:31 (the whole row) + `E19-legs-1-on-2150eba0-64-0-7/` 64/0/7, 2150eba0, 01:34 (leg 1) | `E19-run1-ink-positions-and-a-leftover-event-today/` 224/12/21 (3c1ad1e0, D-E19-1) | PASS as a part re-run: no single run of the whole row has 0 failures; legs 2–8 were not run on build 3 |
| E22 | `E22/` 134/0/4, 3c1ad1e0, 20:59 | – | PASS (leg L = F15) |
| E23 | `E23/` 59/0/2, 3c1ad1e0, 21:27 | – | PASS |
| E24 | `E24/` 137/0/7, 3c1ad1e0, 23:25 | `E24-run1-standup-crossed-midnight/` 135/2/6 | PASS |

### EDGE C01–C17

| Run | Sub-steps | Build, time | PASS / FAIL / recorded |
|---|---|---|---|
| `EDGE-cal-run1-C01-to-C17-220-7-14/` | all 17, grouped (the ONE run) | 6009c0b1, 00:50–01:18 | 220 / 7 / 14 — 13 sub-steps with 0 failed; C01 1, C05 2, C09 1, C14 3 failed, all four the driver's |
| `EDGE-cal-run2-C01-alone-41-0-3/` | C01 | 6009c0b1, 01:22 | 41 / 0 / 3 |
| `EDGE-cal-run3-C05-alone-41-0-2/` | C05 | 6009c0b1, 01:24 | 41 / 0 / 2 |
| `EDGE-cal-run4-C09-alone-41-0-2/` | C09 | 6009c0b1, 01:26 | 41 / 0 / 2 |
| `EDGE-cal-run5-C14-alone-39-0-1/` | C14 | 6009c0b1, 01:27 | 39 / 0 / 1 |
| `EDGE-cal-run6-C16-alone-39-0-2/` | C16 (it had passed; run again so its jumped-back ring lines are kept, for E21) | 6009c0b1, 01:29 | 39 / 0 / 2 |
| `EDGE-cal-run7-C12-C14-C15-C17-on-2150eba0-79-0-1/` | C12, C14, C15, C17 (they read the Agenda, which build 3 changed) | 2150eba0, 01:47 | 79 / 0 / 1 |

Every one of the 17 sub-steps has a run with no failure. (Each run's totals include edge.sh's own checks of the index.)
The four driver faults of the grouped run: C01 read the device's day name inside a pipe (adb took the pipe); C05 tapped
with two `input` processes started in the same instant (one click reached the app); C09 deleted the Birthdays calendar
after a force-stop, by which time the system had restarted the shell; C14 compared a pane row's texts with its tick-box
glyph in them. The lead's full EDGE (C and P together, no `EDGE_ONLY`) has not been run by me.

### E21 — my share (written)
`E21/producers.tsv`: 36 Calendar lines (34 `[calendar]`, among them `open other`, and 2 `[motion] cal_…`); `E21/notrun.tsv`:
3 lines (`sync … refused (not allowed)` → CalendarWriteGuardTest; `sync … failed the copy could not be read` →
CalendarWriteLayerTest; `reminders count from … (the shell's start: the clock was set back behind <old>)` →
CalendarRulesTest). Checked with e21.sh's own rule (run folders on 2150eba0, 6009c0b1, 3c1ad1e0; `ring*.txt`,
`*slice*.txt`): all 76 producer lines of the table (mine and the People writer's) have a hit; no alternative is in both
tables; the three JVM test files exist. **e21.sh itself has not been run by me** (the lead's row).
Producers that exist only in the 6009c0b1 EDGE runs: `calendars: none` (C01), `failed mapping stale` (C04), `failed
calendar read-only` (C07), `write update … failed the event is gone` (C11), `reminders count from … (the clock was set:
it went back behind …)` (C16). Two producers are the lead's TRUST row (`open other`, `swipe ignored (no such alert now)`).
In NEITHER table (code or fix-round lines with no device producer and no JVM test I found): `reminder poke failed: …`;
`reminder …: failed notifications are off`; `reminder …: dismiss refused… / dismiss failed …`; `calendars: n (local
missing: it could not be created)`; `calendar_sync.json: allowed: n of m entries dropped (…)` and the unreadable-store
line (CalendarSyncStoreTest covers the parse, not named in the table); the raw-exception form of `sync …: failed <err>`
and of `write insert|delete …: failed <err>`.

## Defect files of mine
- `defects/D-E9-1.md` — Tess's typed "add a calendar event called dentist …" kept the phrase in the title. FIXED (33944662); verified on 6009c0b1 and 2150eba0.
- `defects/D-E19-1.md` — the pane's chevron, the calendar names and the event titles sat right of R11 at the ink. FIXED (dacca941); verified on 6009c0b1.
- `defects/D-E19-2.md` — "No events today" ink at 25.67 against 24. FIXED on 2150eba0 (24.00).
- `defects/D-E7-1.md` — janky frames over E7's run: 14.19 % / 14.38 % / 14.58 % on the three fix builds against the 5 % bound; the same legs on an EMPTY calendar read 11.26 % (6009c0b1) and 11.11 % (2150eba0). OPEN: the 266.7 ms Agenda frame is gone on 2150eba0 (66.7 ms); the clause still fails; its measure is with the owner. Per-leg tables for four runs and every frame (`defects/D-E7-1-frames-*.tsv`) are in the file.

## Findings recorded, not defects
- **E24 / F26:** the provider's `instances` for the synced series' COPY in Personal holds 1 row over the series' weeks, not 5 (`E24/W-copy-instances.txt`). The shell's own views show each occurrence once.
- **E24 / E6:** the AOSP Calendar posts no notification of its own for these alerts on this AVD (recorded; the spec expected a double).
- **E4:** a Calendar tile pinned from the app list is MEDIUM and shows only the day face (phase 01 draws event text on WIDE tiles only). clauses-open.tsv, for the owner.
- **E6 / C09:** the system restarts the shell (the Home app) within seconds of a `pm clear` or a force-stop.
- **j6.sh (phase 03, not mine):** its restore deletes nothing; every J6 run leaves a `standup` event. e9.sh purges it around its children.
- **The editor:** Title / Location boxes are drawn x 20–340, the pick boxes x 12–201 (recorded in E19; no clause names the x).
- **C16:** a clock set back under the running shell lowers `remindersSince` to the jumped time (the product's rule, logged). It stays lowered after the clock is restored, until the shell's store is cleared.
- **C06:** with `calendar_sync.json` lost the next start logs `reminders count from <now> (the shell's start: first on this install)` — the cut-off restarts.
- **Diagnostics lines in the code that neither the spec nor the Change Log names:** `edit <id>: not a Tessera event, opened read-only`; `instances query failed`; `alerts query failed`; `sync mappings dropped (the local event is gone): […]`; `can sync to: n calendar(s) no longer on this phone left the list`; `the provider cannot be observed`; `reminder poke: nothing read (READ_CALENDAR)`; `birthdays: not synced (…)`; `permission request <name>: granted|denied`; `the permission will not be asked again: opening the app's settings`; `CalendarActivity created`; `query failed` / `agenda query failed` (the feed).

## Time-of-day dependencies in the spec's own fixtures
- E4: the "tomorrow 09:00" Standup is inside the tile's 24 hours only when the row runs after 09:00.
- E9: the "tomorrow at 2 pm" dentist is inside Tess's 24-hour read only after 14:00; the driver asserts it named only then, and NOT named otherwise (the lead agreed; clauses-open).
- E17: an all-day event's instance is UTC [00:00, 24:00) of its date, so in a zone behind UTC TODAY's birthday leaves the tile's and Tess's 24-hour range at 18:00 MDT; the fixture uses the device's UTC date.
- E19: today's heading is asserted at the ink only on a Wednesday (R11's sample), at its text box otherwise (the lead's ruling).

## clauses-open.tsv lines of mine (14)
E4 ×2 (pinned-tile Dentist; pinned tile compared at the slot tile's size), E22 (JVM test read from the lead's result file), E17 (time-proof birthday date), E9 ×2 (typed stand-ins for phase 03's spoken rows; Tess's 24-hour read), E19 (ink positions with R11's own samples; today's heading), E24 (one Standup per day it covers), E6 ×2 (leg (e) uninstalls; the clock is restored before the reboot leg), EDGE C01 (the tile's front shows between the day face), EDGE C05 (two taps 0.2 s apart), EDGE C10 (the 50 inserts took 23 s, not 10), EDGE C15 (the app's locale, not the system's).

## Not driven, and why
- The lead's whole EDGE (no `EDGE_ONLY`) and E21 itself: the lead's runs.
- E19 legs 2–8, E7 legs D and T, E9's children and legs B, V, D, G, N on 2150eba0: not run again (the ruling; the lead named what the third build changes).
- E4, E5, E6, E8, E17, E18, E22, E23, E24 on 6009c0b1 or 2150eba0: not run again (they stand on 3c1ad1e0).
- A SYSTEM locale change (C15 drives the app's locale); `reminders count from … (the shell's start: the clock was set back …)` (needs the clock set back while the shell is stopped; the system restarts it at once); a Sync the guard refuses; an unreadable copy row.
- Nothing spoken: no microphone, no host audio; every Tess request is typed.

## Device state left (read under the device lock at 01:58–01:59 MDT by my restore, after my last run)
Build 2150eba004db6757 installed, `apk match yes`. The restore the lead ordered: `pm clear app.tileshell` →
`provision.sh` rc 0 → `layout_restore` of `baseline_layout.json` rc 0 → `ensure_start`. After it: `calendar_sync.json`
= `{"version":1,"allowed":[],"mappings":[],"hidden":[],"notifiedAlerts":[],"remindersSince":1790927897447}` (01:58
today; it read 01:44 today before the restore — E3's own leg B had re-provisioned at 01:44 — not the October 2025 that
C16 left at 01:29); one calendar (Tessera, `_id=2`), no events, no `calendar_alerts` row; raw contacts 2 (Mom and the
pre-existing unnamed one), no birthday row; READ/WRITE_CALENDAR, READ/WRITE_CONTACTS, POST_NOTIFICATIONS granted; no
calendar notification of the shell's; no new crash; `com.android.providers.calendar` and `com.android.calendar`
enabled; zone America/Boise, `time_12_24` null, `auto_time` 1, no app locale, clock 0.6 s from the host; the Start
screen on top. Disabled packages seen and not mine: `com.android.nfc`, `com.android.devicelockcontroller`.

## Notes for whoever picks this up
- Scripts are edited only while nothing runs, by write-temp, `bash -n`, rename.
- Scratch helpers (session scratchpad, not evidence): `qacal-row.sh`, `qacal-queue.sh`, `qacal-locked.sh`, `qacal-restore.sh`, `qacal-e21-check.sh`, `qacal-e7-frames.py`, `qacal-e7-split.py`.
- `edge_index.tsv` names no sub-step for the EDGE row's own producer of `write update event=<id>: failed <err>`; it is the second half of `edge_C11`.
- Two `adb shell input tap` processes started in the same instant give the app one click; stagger them.
- `ring_save` / `ring_since` keep lines stamped at or after a MARK: after a backwards clock jump the lines are behind it — C16 keeps its own (`cring_between`).
