# Phase 16 QA — the Calendar rows: running progress (the QA row-driver writer's own file)

RESUMED 2026-10-01 19:57 MDT on the FIX BUILD: `app/build/outputs/apk/debug/app-debug.apk` md5 **3c1ad1e0e65bd919** (commit 6e524e56). Only runs on this build count for the gate; every folder named `E<n>-build-686506a7-…` is a kept run on the earlier build.
Include: `scripts/cal_lib.sh` (helpers prefixed `c` / `tess_`; EDGE sub-steps `edge_C01`…`edge_C17`). Pixel helpers: `scripts/cal_px.py` (E8, E18), `scripts/cal_geo.py` (E19).
Run folders: `E<n>/` is the row's last run on the fix build; an earlier run on it is `E<n>-run<k>-<what it found>`.
Order (the lead's): E22, E23, E24, E6; second runs of E3, E4, E5, E17, E18; E9, E19, E8, E7; EDGE C01–C17; E21.

**OWNER'S RULING 22:14 (relayed by the lead): "Only test the fixes not EVERY THING" — no second pass; a row that passed ONCE on 3c1ad1e0 is done and is not run again; EDGE is one grouped run of C01–C17 plus single re-runs of what fails; then the E21 lines.**

## Where each row stands on the fix build (PASS / FAIL / recorded)

| Row | Runs on 3c1ad1e0, oldest first (PASS / FAIL / recorded) | Passes (ONE is enough — the ruling of 22:14) | Note |
|---|---|---|---|
| E22 | `E22/` 134/0/4 (20:59) | 1 | leg L (F15) ran: `write update … failed refused (not allowed)` |
| E23 | `E23/` 59/0/2 (21:27) | 1 |  |
| E24 | `E24-run1-standup-crossed-midnight/` 135/2/6 (21:38); `E24/` 137/0/7 (23:25) | 1 | run 1 failed on a midnight-crossing fixture (driver made time-proof, clauses-open); run 2 passes |
| E6 | `E6-run1-leg-e-precondition-failed-pm-clear-restarts-the-shell/` 62/9/8 (21:47); `E6/` 73/0/8 (23:34) | 1 | run 1: leg (e) precondition failed (pm clear restarts the shell); run 2 passes all five legs (the driver uninstalls instead — clauses-open) |
| E3 | `E3/` 68/0/1 (21:59) | 1 |  |
| E4 | `E4/` 44/0/3 (22:02) | 1 |  |
| E5 | `E5/` 52/0/4 (22:33) | 1 | leg I (F29) passes |
| E17 | `E17/` 65/0/6 (22:43) | 1 | fixture time-proof (the UTC date); run at 22:44 MDT |
| E18 | `E18-run1-killed-mid-leg-C-by-my-runner/` unfinished (22:48); `E18/` 61/0/4 (23:45) | 1 | run 1 was KILLED mid-leg by my own runner (not a result); permissions re-granted at 22:59 |
| E9 | `E9-run1-add-title-keeps-the-phrase/` 54/13/2 (23:56) | 0 | run 1 FAILED: product defect D-E9-1 (title keeps "a calendar event called") plus driver faults, fixed; to run ONCE on fix build 2 |
| E19 | `E19-run1-ink-positions-and-a-leftover-event-today/` 224/12/21 (00:02) | 0 | run 1 FAILED: product defect D-E19-1 (ink positions: pane chevron 16.33, names 63.33, titles 94.33) plus a leftover event today; to run ONCE on fix build 2 |
| E8 | `E8/` 33/0/4 (00:04) | 1 | passes (DST night 3.00 h) |
| E7 | `E7-run1-janky-frames-14-percent/` 40/1/3 (00:06) | 0 | run 1: 40 pass, 1 FAIL — product defect D-E7-1 (14.19 % janky frames, bound 5 %); awaiting the lead's ruling |
| EDGE C01–C17 | none has run | – | `EDGE_ONLY=…` runs, then the lead's full EDGE |
| E21 share | not written (draft list in the scratchpad: qacal-e21-producers.tsv) | – | write after the rows have slices |

## Defect files of mine
- `defects/D-E9-1.md` — Tess's typed "add a calendar event called dentist …" writes the title "a calendar event called dentist". Lead: fixed in commit 33944662 (fix build 2).
- `defects/D-E19-1.md` — at the ink, the pane's chevron (16.33 vs 14), the names "MoNa Events" / "mark guim" (63.33 vs 62) and titles (94.33 vs 92.5) sit right of R11. Lead: ruled a product miss, fixed in dacca941 (fix build 2).
- `defects/D-E7-1.md` — 14.19 % janky frames over E7's run (bound 5 %). Lead told 00:20; no ruling yet.

## Findings recorded, not defects
- **E24 / F26:** the provider's `instances` for the synced series' COPY in Personal holds 1 row over the series' weeks, not 5 (`E24/W-copy-instances.txt`). The shell's own views show each occurrence once.
- **E24 / E6:** the AOSP Calendar posts no notification of its own for these alerts on this AVD (recorded; the spec expected a double).
- **E4:** a Calendar tile pinned from the app list is MEDIUM and shows only the day face (phase 01 draws event text on WIDE tiles only). clauses-open.tsv, for the owner.
- **E6:** `pm clear` does not leave the shell stopped — the system restarts it within 3 s — so leg (e) uninstalls instead (clauses-open.tsv).
- **j6.sh (phase 03, not mine):** its restore's `content delete … --where "title='standup'"` deletes nothing; every J6 run leaves a `standup` event in Tessera. e9.sh purges it around its children.
- **The editor:** Title / Location boxes are drawn x 20–340, the pick boxes x 12–201 (recorded in E19; no clause names the x).
- **Diagnostics lines in the code that neither the spec nor the Change Log names:** `edit <id>: not a Tessera event, opened read-only`; `instances query failed`; `alerts query failed`; `sync mappings dropped (the local event is gone): […]`; `can sync to: n calendar(s) no longer on this phone left the list`; `the provider cannot be observed`; `reminder poke: nothing read (READ_CALENDAR)`; `birthdays: not synced (<reason>)`; `permission request <p>: granted|denied`; `the permission will not be asked again: …`; `CalendarActivity created`; `open <action> -> <route>`; `calendar_sync.json could not be read / written`; `[motion] appbar_menu`.

## Time-of-day dependencies in the spec's own fixtures (tell the lead)
- E4: the "tomorrow 09:00" Standup is inside the tile's 24 hours only when the row runs after 09:00.
- E9: the "tomorrow at 2 pm" dentist is inside Tess's 24-hour read only after 14:00 (the driver also inserts "E9 checkup" one hour ahead).
- E17: the tile's and Tess's reads are `Instances` over [now, now + 24 h]; an all-day event's instance is UTC [00:00, 24:00) of its date, so in a zone behind UTC TODAY's birthday leaves that range at 18:00 MDT. E17 passed at 17:35. If it fails after 18:00 on "the tile shows it today" / "Tess names it", that is a product finding (phase 14's pod already reads from the start of today).

## clauses-open.tsv lines of mine (all written)
E4 ×2 (pinned-tile Dentist; pinned tile compared at the slot tile's size), E22 (JVM test read from the lead's result file), E17 (time-proof birthday date), E9 (typed stand-ins for phase 03's spoken rows), E19 (ink positions with R11's own samples), E24 (one Standup per day it covers), E6 ×2 (leg (e) uninstalls; the clock is restored before the reboot leg).

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
