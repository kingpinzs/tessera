# Phase 16 — Calendar QA row writer, final report (2026-10-02 02:08 MDT)

Saved by the lead from the row writer's hand-back (a subagent cannot write a report file). The writer is an independent
Opus agent; this is its account, not the lead's. The gate's own table is generated from the run folders
(`qa/phase-16/scripts/gate_evidence.py`). Installed build at the end: 2150eba004db6757 (fix build 3). Paths below are
under `docs/plan/qa/phase-16/`. Totals are PASS / FAIL / recorded.

## 1. Rows — one line each, not rounded up

Ten rows have one whole run with 0 failures. E9 and E19 pass only as a part re-run (no single whole run of either has
0 failures). E7 FAILS one clause in every run.

| Row | Driver | Evidence run (build, time) | Totals | State |
|---|---|---|---|---|
| E3 | scripts/e3.sh | `E3/` (2150eba0, 01:43); also `E3-run1-on-3c1ad1e0-68-0-1/` | 68/0/1 | PASS |
| E4 | scripts/e4.sh | `E4/` (3c1ad1e0, 22:02) | 44/0/3 | PASS |
| E5 | scripts/e5.sh | `E5/` (3c1ad1e0, 22:33) | 52/0/4 | PASS |
| E6 | scripts/e6.sh | `E6/` (3c1ad1e0, 23:34) | 73/0/8 | PASS |
| E7 | scripts/e7.sh | whole row: `E7-run2-per-leg-diagnosis-janky-14.38-percent/` (6009c0b1, 00:37) 40/1/19; jank legs on build 3: `E7-legs-jank-on-2150eba0-32-1-21-janky-14.58-percent/` (01:36) 32/1/21 | see left | **FAIL: one clause** (janky frames ≤ 5 %: 14.19 % on 3c1ad1e0, 14.38 % on 6009c0b1, 14.58 % on 2150eba0). Every other clause passes. Legs D and T were not run on build 3 |
| E8 | scripts/e8.sh | `E8/` (3c1ad1e0, 00:04) | 33/0/4 | PASS |
| E9 | scripts/e9.sh | whole row: `E9-run2-dentist-outside-tess-24-hours-at-0026/` (6009c0b1) 69/1/3 + legs A,W alone: `E9-legs-A-W-on-2150eba0-17-0-4/` (01:54) 17/0/4 | see left | PASS as a part re-run only |
| E17 | scripts/e17.sh | `E17/` (3c1ad1e0, 22:43) | 65/0/6 | PASS |
| E18 | scripts/e18.sh | `E18/` (3c1ad1e0, 23:45) | 61/0/4 | PASS |
| E19 | scripts/e19.sh (+ cal_geo.py, cal_px.py) | whole row: `E19-run2-no-events-today-ink-at-25.67/` (6009c0b1) 235/1/22 + leg 1 alone: `E19-legs-1-on-2150eba0-64-0-7/` (01:34) 64/0/7 | see left | PASS as a part re-run only; legs 2–8 not run on build 3 |
| E22 | scripts/e22.sh | `E22/` (3c1ad1e0, 20:59) | 134/0/4 | PASS |
| E23 | scripts/e23.sh | `E23/` (3c1ad1e0, 21:27) | 59/0/2 | PASS |
| E24 | scripts/e24.sh | `E24/` (3c1ad1e0, 23:25) | 137/0/7 | PASS |

Other kept runs on the fix builds: `E6-run1-leg-e-precondition-failed-pm-clear-restarts-the-shell/` (62/9/8),
`E7-run1-janky-frames-14-percent/` (40/1/3), `E9-run1-add-title-keeps-the-phrase/` (54/13/2),
`E9-legs-A-W-run1-no-summary-the-restore-was-skipped-12-0/` (no summary line),
`E18-run1-killed-mid-leg-C-by-my-runner/` (unfinished, not a result),
`E19-run1-ink-positions-and-a-leftover-event-today/` (224/12/21), `E24-run1-standup-crossed-midnight/` (135/2/6).
First-build runs are the `E<n>-build-686506a7-…` folders.

E7 baselines (diagnosis, not gate rows, no 5 % verdict line):
`E7-baseline-empty-calendar-49-of-435-janky-11.26-percent/` (6009c0b1, 30/0/20) and
`E7-baseline-empty-calendar-on-2150eba0-48-of-432-janky-11.11-percent/` (30/0/21). No `E7/`, `E9/` or `E19/` folder
exists.

### EDGE C01–C17 (functions `edge_C01` … `edge_C17` in `scripts/cal_lib.sh`, run by `scripts/edge.sh`)

- The one grouped run: `EDGE-cal-run1-C01-to-C17-220-7-14/` (6009c0b1, 00:50–01:18): 220/7/14. Thirteen sub-steps had 0
  failed. The 7 failures were in C01 (1), C05 (2), C09 (1), C14 (3) — all four driver faults, fixed.
- Each re-run alone, all 0 failed (6009c0b1): `EDGE-cal-run2-C01-alone-41-0-3/`, `EDGE-cal-run3-C05-alone-41-0-2/`,
  `EDGE-cal-run4-C09-alone-41-0-2/`, `EDGE-cal-run5-C14-alone-39-0-1/`, `EDGE-cal-run6-C16-alone-39-0-2/` (C16 had
  passed; re-run so its jumped-back ring lines are kept for E21).
- On build 3: `EDGE-cal-run7-C12-C14-C15-C17-on-2150eba0-79-0-1/` (79/0/1).
- Every one of the 17 sub-steps has a run with no failure. Each log names its APK and its sub-steps. Totals include
  edge.sh's own index checks.
- The whole EDGE (C and P together, no `EDGE_ONLY`) was not run.

## 2. Defect files

- `defects/D-E9-1.md` — Tess's "add a calendar event called dentist …" kept the phrase in the title. FIXED (33944662);
  verified on 6009c0b1 and 2150eba0.
- `defects/D-E19-1.md` — pane chevron, calendar names and event titles sat right of R11 at the ink. FIXED (dacca941);
  verified on 6009c0b1.
- `defects/D-E19-2.md` — "No events today" ink at 25.67 against 24. FIXED on 2150eba0 (24.00).
- `defects/D-E7-1.md` — OPEN. Janky frames over E7's run against the 5 % bound. Both numbers on 2150eba0: 5,000 events
  64 of 439 = 14.58 %; empty calendar 48 of 432 = 11.11 %. On 6009c0b1: 63 of 438 = 14.38 % against 49 of 435 =
  11.26 %. The 266.7 ms Agenda frame is gone on 2150eba0 (66.7 ms). The file holds the per-leg tables of four runs;
  every frame is in `defects/D-E7-1-frames-6009c0b1.tsv`, `-6009c0b1-baseline-empty.tsv`, `-2150eba0.tsv`,
  `-2150eba0-baseline-empty.tsv`. The clause's measure is with the owner.
- No product defect came out of EDGE. C05: two taps 0.2 s apart give `updated` then `ok`, one copy.

## 3. clauses-open.tsv — the writer's 14 lines

1. E4 — the pinned-tile "Dentist" (the row's own is deleted two clauses earlier; the driver inserts a second one).
2. E4 — pinned tile is MEDIUM, slot tile WIDE; compared at the slot tile's size.
3. E22 — the JVM test is read from the lead's result file.
4. E17 — birthday fixture uses the device's UTC date.
5. E9 — phase 03's spoken e7/e10/e14 re-run as typed stand-ins.
6. E9 — Tess reads the next 24 hours: "dentist" asserted named only when it starts inside them, NOT named otherwise.
7. E19 — positions read at the ink with R11's own sample text; today's heading at the ink only on a Wednesday.
8. E24 — one Standup per day it covers (midnight crossing).
9. E6 — leg (e) uninstalls, because pm clear lets the system restart the shell.
10. E6 — the clock is restored before the reboot leg.
11. EDGE C01 — "the day only": the tile's front (name alone) shows between the day face.
12. EDGE C05 — "tapped twice quickly" driven as two taps 0.2 s apart.
13. EDGE C10 — the 50 inserts took 23 s, not 10.
14. EDGE C15 — the app's locale was changed, not the system's.

## 4. E21 state

- `E21/producers.tsv`: 36 Calendar lines appended (34 `[calendar]`, 2 `[motion] cal_…`).
- `E21/notrun.tsv`: 3 lines — `sync … refused (not allowed)` (CalendarWriteGuardTest), `sync … failed the copy could not
  be read` (CalendarWriteLayerTest), `reminders count from … (the shell's start: the clock was set back behind <old>)`
  (CalendarRulesTest). Each names its test with its path; the three files exist.
- Checked with e21.sh's own rule over the run folders on 2150eba0, 6009c0b1 and 3c1ad1e0: all 76 producer lines of the
  table (the writer's and the People writer's) have a hit; no alternative is in both tables. The writer did not run
  e21.sh (the lead did: E21 79/0).
- Five producers exist only in the 6009c0b1 EDGE runs (C01 `calendars: none`, C04 `failed mapping stale`, C07
  `failed calendar read-only`, C11 `write update … failed the event is gone`, C16 `reminders count from … (the clock was
  set: it went back behind …)`); they depend on builds.txt's `6009c0b14a87039f *` line. Two producers are the lead's
  TRUST row (`[calendar] open other`, `swipe ignored (no such alert now)`).
- **In NEITHER table (no device producer and no JVM test found):** `reminder poke failed: …`;
  `reminder …: failed notifications are off`; `reminder …: dismiss refused / dismiss failed`;
  `calendars: n (local missing: it could not be created)`; `calendar_sync.json: allowed: n of m entries dropped (…)` and
  the unreadable-store line (CalendarSyncStoreTest covers the parse but is not named in the table); the raw-exception
  form of `sync …: failed <err>` and of `write insert|delete …: failed <err>`.

## 5. Not driven, and why

- Whole EDGE without `EDGE_ONLY`, and E21 itself: the lead's.
- On 2150eba0: E19 legs 2–8; E7 legs D and T; E9's children and legs B, V, D, G, N; all of E4, E5, E6, E8, E17, E18,
  E22, E23, E24 — per the owner's ruling and the lead's list of what build 3 changes.
- A system locale change (C15 drives the app locale).
- The start-form `reminders count from … clock was set back` line: needs the clock set back while the shell is stopped,
  and the system restarts it at once.
- A Sync the guard refuses; an unreadable copy row.
- Nothing spoken: every Tess request is typed.

## 6. Driver faults of the writer's found in the last stretch

- e9.sh: the restore and `row_end` sat inside leg N's `if`, so the 00:46 `E9_LEGS=A,W` run skipped both. Its two events
  stayed in Tessera until EDGE C01 deleted Tessera at 00:51; no row of the writer's ran in between. Fixed; every narrow
  mode now ends through restore + row_end.
- EDGE: C01, C05, C09, C14 as above; C16's RECORD had picked up C06's line.
- Narrow modes added: `E19_LEGS=1`, `E7_LEGS=jank`, `E7_LEGS=baseline`; e7.sh now records each leg's longest frame.

## 7. Device state left

Read under the lock at 01:58–01:59 by the writer's restore, re-read at 02:08.

- Restore as the lead ordered: `pm clear app.tileshell` → provision.sh rc 0 → layout_restore of baseline_layout.json
  rc 0 → ensure_start. 10 checks passed, 0 failed.
- calendar_sync.json: `{"version":1,"allowed":[],"mappings":[],"hidden":[],"notifiedAlerts":[],"remindersSince":1790927897447}`
  — 01:58 today.
- One calendar (Tessera, _id=2); no events; no calendar_alerts row; 2 raw contacts (Mom and the pre-existing unnamed
  one); no birthday row.
- READ/WRITE_CALENDAR, READ/WRITE_CONTACTS, POST_NOTIFICATIONS granted; no calendar notification; no new crash.
- com.android.providers.calendar and com.android.calendar enabled; zone America/Boise; time_12_24 null; auto_time 1; no
  app locale; clock within 1 s of the host; Start screen on top; device lock free.
- The restore's lines are copied into `qa-cal-progress.md`.
- Disabled packages seen, not the writer's: com.android.nfc, com.android.devicelockcontroller.
