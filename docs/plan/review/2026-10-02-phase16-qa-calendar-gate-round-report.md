# Phase 16 — Calendar QA row writer, gate-round report (2026-10-02 04:05 MDT)

Saved by the lead from the row writer's hand-back (a subagent cannot write a report file). The writer is an independent
Opus agent; this is its account, not the lead's. It arrived after the gate's second round; everything in it was already
in the run folders the round-2 evidence table reads. Paths are under `docs/plan/qa/phase-16/`. Totals are PASS / FAIL /
recorded. Installed when it was written: the fifth build e03a1d233ee6ba0c (the sixth, 585b457f, followed at 04:18 and
changes People's observer only).

## 1. Runs in the round

Every run the lead listed has a 0-failure run. Three first attempts are kept beside their re-runs (two the driver's,
one caused by an install landing between two queued runs).

| What | Build | Folder | Totals | Shows |
|---|---|---|---|---|
| E17 leg T (A-1) | 3c5e3199 | `E17-legs-T-on-3c5e3199-22-0-3/` | 22/0/3 | The doc's fixture at 19:00 MDT: tile and Tess show today's birthday, tomorrow's on neither; one process; clock restored |
| E19 legs 1,2,3 (B-1) | 3c5e3199 | `E19-legs-1-2-3-on-3c5e3199-127-0-12/` | 127/0/12 | Agenda rows on the new list: heading 24.67, bar x 0 / 8 wide, 40 and 56 tall, pitch 44.00, labels 24.67 / 25.00, titles 92.33; empty day 24.00 |
| E24 leg D (B-2, A-9) | 3c5e3199 | `E24-legs-D-on-3c5e3199-94-0-7/` | 94/0/7 | Un-ticked Sync opens `cal_can_sync`, the `no calendar allowed` line, no `-> calendar` line, copy row unchanged (dirty 0), marker kept. Changed occurrence: no prompt at all, deleted here only (eventStatus 2); Personal's two rows live |
| E18 leg G (A-10) | e03a1d23 | `E18-legs-G-on-e03a1d23-25-0-2/` | 25/0/2 | The dialog's Allow tapped; `permission request READ_CALENDAR: granted`, `[app] feeds started (calendar grant)`; a driver insert reaches the tile; one pid |
| E7 Dframes | e03a1d23 | `E7-legs-D-frames-on-e03a1d23/` | 16/0/14 | section 2 |
| E7 baseline | e03a1d23 | `E7-baseline-empty-calendar-on-e03a1d23/` | 30/0/22 | 50 of 433 = 11.55 % |
| E7 jank + compare | e03a1d23 | `E7-legs-jank-on-e03a1d23-compare-38-0-25-janky-14.98-percent/` | 38/0/25 | section 2 |
| EDGE C18 (re-cut) | e03a1d23 | `EDGE-cal-run9-C18-alone-on-e03a1d23-36-0-2/` | 36/0/2 | `reminders count from 1790934198279 (the shell's start: the clock was set back behind 1791193388654)` |
| E22 leg X (B13.2) | e03a1d23 | `E22-legs-X-on-e03a1d23-29-0-1/` | 29/0/1 | INSERT with beginTime / endTime: the editor reads "Mon 5 Oct 2026 4:00 PM / 5:30 PM"; saved in Tessera at those times |
| E5 leg D (A-2) | e03a1d23 | `E5-legs-D-on-e03a1d23-18-0-2/` | 18/0/2 | Three wider windows loaded during the walk (to 2027-01-16, 2027-05-08, 2027-10-23); first dates never go backwards; weeks 9, 17, 33, 40 reached |
| E4 leg W | e03a1d23 | `E4-legs-W-on-e03a1d23-15-0-3/` | 15/0/3 | Tile and Tess show the event in 2 h only; not the one ended 10 min ago, not the one in 25 h; faces=2 |
| E9 A,W | e03a1d23 | `E9-legs-A-W-on-e03a1d23-17-0-4/` | 17/0/4 | Card title "dentist"; Tess's read as before |
| E3 whole | e03a1d23 | `E3/` | 68/0/1 | – |
| E8 whole | e03a1d23 | `E8/` | 33/0/4 | – |
| EDGE C10 | e03a1d23 | `EDGE-cal-run10-C10-alone-on-e03a1d23-33-0-2/` | 33/0/2 | 50 inserts under the open Day view: the view line counts 50 more, nodes shown, same process |

Kept first attempts and earlier runs: `E5-legs-D-run1-on-3c5e3199-hollow-the-weeks-were-loaded-before-the-walk-17-0-2/`
(a hollow pass, the driver's: the Agenda had loaded every window while the calendar was empty);
`E18-legs-G-run1-on-3c5e3199-no-dialog-with-write-held-24-1-2/` (the fixture: with WRITE held Android grants READ with
no dialog; the product lines passed in it); `EDGE-cal-run8-C18-alone-on-e03a1d23-the-reboot-kept-the-jumped-clock-34-3-2/`
(the reboot method does not work); `E7-legs-jank-on-e03a1d23-run1-compared-against-fourth-build-folders/` (35/3/25: the
install landed between the baseline and this run); `E7-legs-D-frames-on-3c5e3199/` (100.1 ms),
`E7-baseline-empty-calendar-on-3c5e3199/` (11.37 %), `E7-legs-D-frames-on-2150eba0-day-view-opens-344.6-ms-16-0-12/`.
Earlier whole runs moved aside: `E3-run2-on-2150eba0-68-0-1/`, `E8-run1-on-3c1ad1e0-33-0-4/`. The whole-row folders
`E4/`, `E5/`, `E17/`, `E18/`, `E22/`, `E24/` (3c1ad1e0) are back under their own names.

## 2. E7 on the fifth build

- The two sums: 5,000 events 65 of 434 = 14.98 %; empty calendar 50 of 433 = 11.55 %. Difference 3.43 points.
- The compare passes, both halves: every leg under 100 ms; 14.98 % ≤ 11.55 % + 5.
- The doc's 5 % reading: 14.98 %, recorded in that run, not asserted. E7 as the doc words it still fails on every
  build; the measure is the owner's ruling (Q-16-6).
- Twelve swipes: 48 of 382 = 12.57 % against 38 of 370 = 10.27 %. P1–P4: 17 of 52 against 12 of 63. Slow UI thread 37
  against 24.

Longest frame of every leg (ms):

| Leg | 5,000 events | Empty |
|---|---|---|
| P1 | 66.7 | 66.7 |
| P2 | 50.1 | 33.4 |
| P3 | 50.1 | 66.7 |
| P4 | 50.1 | 50.1 |
| P5-01 | 44.3 | 41.5 |
| P5-02 | 33.4 | 37.3 |
| P5-03 | 33.4 | 36.0 |
| P5-04 … P5-12 | 33.4 each | 33.4 each |
| D1 (the Day view opens on the 200-event day) | 50.1 | – |
| D2-scroll-01…05 | 16.8 each | – |

Day view open across builds: 344.6 ms (2150eba0, 4 frames) → 100.1 ms (3c5e3199, 13 frames) → 50.1 ms (e03a1d23, 29
frames, 9 late, each building frame 14–23 ms of UI work). All 200 blocks reached by the scroll on each.
`view day … 200 instances in 49 ms` (357 ms on the third build). Everything is in `defects/D-E7-1.md`.

## 3. Driver and table changes

- `e17.sh` — leg T (`E17_LEGS=T`); the row's own fixture is the doc's again (local today); the pod record prints its
  day groups (Bob's birthday is under `pod_subheader:agenda:tomorrow`).
- `e5.sh` — leg A adds events in weeks 9, 17, 33, 40; leg D reopens a fresh Agenda and asserts that windows load during
  the walk, monotone first dates, and the four weeks reached (`E5_LEGS=D`). Applied to the kept 3c1ad1e0 run, the check
  shows the defect: "dump 4 shows 2026-10-02 after 2026-10-15".
- `e19.sh` — `E19_LEGS=2,3` and `1,2,3`. `e24.sh` — leg D's two new steps; `E24_LEGS=D`. `e18.sh` — leg G.
- `e7.sh` + `scripts/cal_frames.py` — every leg records its longest frame with the stage split; `E7_LEGS=Dframes`;
  `E7_MEASURE=compare` with `E7_BASELINE_DIR` and `E7_DFRAMES_DIR`, which asserts both folders are on the installed
  build.
- `e22.sh` — leg X sends and asserts beginTime / endTime. `e4.sh` — leg W.
- `e6.sh` — the two provider-alarm checks need an RTC alarm whose origWhen is the alert's own time. Not run on a
  device; applied to the kept E6 run's alarm dumps they hold.
- `cal_lib.sh` — `edge_C18`. `edge_index.tsv` — B04.1 and B13.2 notes updated; a C18 line added.
- `E21/notrun.tsv` — the `sync … refused (not allowed)` line names CalendarWriteLayerTest and its reason names E24 leg
  D; the `shell's start: the clock was set back` line moved to producers (EDGE C18).
- `clauses-open.tsv` — E17's line re-worded (closed by 53ca48dc); E6's reboot line corrected.

## 4. Where the reviewers or the writer were wrong

- Reviewer B, note 9 (a reboot after a forward jump puts the clock back): it does not on this emulator with automatic
  time off (2026-10-05 against the host's 2026-10-02 after `adb reboot`). The error was the writer's first, in its E6
  clauses-open line; corrected. The line is producible another way: stop the shell and set the clock in one device
  command.
- E4: the whole row cannot pass before 09:00 by its own fixture; leg W is the time-proof form.
- No other finding about the Calendar rows was wrong.

## 5. Not run, by the lead's scope

Whole EDGE (no `EDGE_ONLY`) and `e21.sh` (the lead's). After 3c1ad1e0: E4 legs A–E, E5 legs B, C, E–I, E6, E17 legs
A–F, E18 legs A–E, E22 legs other than X, E23, E24 legs H, N, U, G. After 6009c0b1: E7 legs D (as a row leg) and T,
E9's children and other legs, E19 legs 4–8. C12, C14, C15, C17 stand on 2150eba0; the other C sub-steps on 6009c0b1,
except C10 and C18 on e03a1d23.

## 6. Device state left

The writer's restore ran under the lock 03:59–04:00, 10 checks passed: `pm clear` → provision.sh rc 0 → the baseline
layout rc 0. One Tessera, no events, no alert rows; 2 raw contacts; the five permissions held; no calendar
notification; no new crash; auto_time 1, clock 0.2 s from the host; Start on top.
