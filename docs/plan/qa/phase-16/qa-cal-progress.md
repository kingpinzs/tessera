# Phase 16 QA — the Calendar rows: running progress (the QA row-driver writer's own file)

Last written 2026-10-02 04:05 MDT, after the gate round's fourth and fifth builds. Nothing of mine is running; my queue
runner has exited.

Builds, in order: `686506a7` (first; folders `E<n>-build-686506a7-…`), `3c1ad1e0e65bd919` (fix build 1), `6009c0b14a87039f`
(2), `2150eba004db6757` (3), `3c5e3199554038d1` (4, commit 3573219e: the gate round's fixes, the Day view 24 blocks to a
frame), `e03a1d233ee6ba0c` (5, commit ac4a972d: 8 blocks to a frame — installed now).
The owner's ruling of 2026-10-01 22:14 ("Only test the fixes not EVERY THING"): a row that passed once stands; a failed
row is fixed and run again narrowly; on a later build only what its diff touches is run, each once.

Include: `scripts/cal_lib.sh` (helpers `c…` / `tess_…`; EDGE sub-steps `edge_C01`…`edge_C18`). Helpers: `cal_px.py`,
`cal_geo.py` (E19), `cal_frames.py` (E7: a leg's frames from framestats).
Narrow modes, every one ending through the row's restore and `row_end`: `E4_LEGS=W`, `E5_LEGS=D`, `E7_LEGS=jank |
baseline | Dframes` (+ `E7_MEASURE=compare E7_BASELINE_DIR=… E7_DFRAMES_DIR=…`), `E9_LEGS=A,W`, `E17_LEGS=T`,
`E18_LEGS=G`, `E19_LEGS=1 | 2,3 | 1,2,3`, `E22_LEGS=X`, `E24_LEGS=D`.

## Where each row stands

| Row | The whole row's run (PASS / FAIL / recorded, build) | Narrow runs on later builds | State |
|---|---|---|---|
| E3 | `E3/` 68/0/1, e03a1d23, 03:50 | – (earlier whole runs: `E3-run1-on-3c1ad1e0-68-0-1/`, `E3-run2-on-2150eba0-68-0-1/`) | PASS, whole, on the fifth build |
| E4 | `E4/` 44/0/3, 3c1ad1e0 | `E4-legs-W-on-e03a1d23-15-0-3/` (the tile's and Tess's window: ended today / in 2 h / in 25 h) | PASS; legs A–E not run after 3c1ad1e0 (their Standup is "tomorrow 09:00": inside the tile's 24 h only after 09:00) |
| E5 | `E5/` 52/0/4, 3c1ad1e0 | `E5-legs-D-on-e03a1d23-18-0-2/` (A-2: the walk past weeks 8, 16, 32 while they load). Kept, hollow: `E5-legs-D-run1-on-3c5e3199-hollow-the-weeks-were-loaded-before-the-walk-17-0-2/` | PASS |
| E6 | `E6/` 73/0/8, 3c1ad1e0 | – | PASS. e6.sh's two provider-alarm checks were tightened afterwards (tied to the alert's own alarm time) and NOT run on a device; they hold when applied to the kept run's alarm dumps |
| E7 | `E7-run2-per-leg-diagnosis-janky-14.38-percent/` 40/1/19, 6009c0b1 (the doc's 5 % line fails) | on e03a1d23: `E7-legs-D-frames-on-e03a1d23/` 16/0/14, `E7-baseline-empty-calendar-on-e03a1d23/` 30/0/22, `E7-legs-jank-on-e03a1d23-compare-38-0-25-janky-14.98-percent/` 38/0/25 | **OPEN — the owner's ruling.** As the doc words it (≤ 5 %) the clause FAILS on every build (14.19 / 14.38 / 14.58 / 14.71 / 14.98 %). The comparison (`E7_MEASURE=compare`) PASSES on the fifth build. Legs D and T pass only in the whole run on 6009c0b1 |
| E8 | `E8/` 33/0/4, e03a1d23, 03:54 | – (`E8-run1-on-3c1ad1e0-33-0-4/`) | PASS, whole, on the fifth build |
| E9 | `E9-run2-dentist-outside-tess-24-hours-at-0026/` 69/1/3, 6009c0b1 | `E9-legs-A-W-on-2150eba0-17-0-4/`, `E9-legs-A-W-on-e03a1d23-17-0-4/` | PASS as a part re-run: no single whole run has 0 failures |
| E17 | `E17/` 65/0/6, 3c1ad1e0 (made with the UTC-date fixture) | `E17-legs-T-on-3c5e3199-22-0-3/` (A-1: the doc's fixture at 19:00 local; tomorrow's birthday on neither) | PASS; legs A–F not run again with the doc's fixture |
| E18 | `E18/` 61/0/4, 3c1ad1e0 | `E18-legs-G-on-e03a1d23-25-0-2/` (A-10: the grant made in place). Kept: `E18-legs-G-run1-on-3c5e3199-no-dialog-with-write-held-24-1-2/` (my fixture: no dialog while WRITE was held) | PASS |
| E19 | `E19-run2-no-events-today-ink-at-25.67/` 235/1/22, 6009c0b1 | `E19-legs-1-on-2150eba0-64-0-7/`, `E19-legs-1-2-3-on-3c5e3199-127-0-12/` (B-1: the Agenda rows on the new list) | PASS as a part re-run; legs 4–8 last ran on 6009c0b1 |
| E22 | `E22/` 134/0/4, 3c1ad1e0 | `E22-legs-X-on-e03a1d23-29-0-1/` (B13.2: the INSERT's beginTime / endTime prefill) | PASS |
| E23 | `E23/` 59/0/2, 3c1ad1e0 | – | PASS |
| E24 | `E24/` 137/0/7, 3c1ad1e0 | `E24-legs-D-on-3c5e3199-94-0-7/` (B-2: Sync un-ticked opens "Can sync to" and writes nothing; A-9: the changed occurrence deletes here only) | PASS |

Other kept runs on the fix builds: `E6-run1-leg-e-precondition-failed-pm-clear-restarts-the-shell/`, `E7-run1-janky-frames-14-percent/`,
`E7-legs-jank-on-2150eba0-32-1-21-janky-14.58-percent/`, the two earlier baselines, `E7-baseline-empty-calendar-on-3c5e3199/`,
`E7-legs-D-frames-on-2150eba0-day-view-opens-344.6-ms-16-0-12/`, `E7-legs-D-frames-on-3c5e3199/` (100.1 ms),
`E7-legs-jank-on-e03a1d23-run1-compared-against-fourth-build-folders/` (35/3/25), `E9-run1-add-title-keeps-the-phrase/`,
`E9-legs-A-W-run1-no-summary-the-restore-was-skipped-12-0/`, `E18-run1-killed-mid-leg-C-by-my-runner/`,
`E19-run1-ink-positions-and-a-leftover-event-today/`, `E24-run1-standup-crossed-midnight/`.

### EDGE C01–C18

| Run | Sub-steps | Build | PASS / FAIL / recorded |
|---|---|---|---|
| `EDGE-cal-run1-C01-to-C17-220-7-14/` | all 17, grouped | 6009c0b1 | 220 / 7 / 14 (C01, C05, C09, C14 failed: driver faults) |
| `EDGE-cal-run2…run6-…-alone-…/` | C01, C05, C09, C14, C16, each alone | 6009c0b1 | 41/0/3, 41/0/2, 41/0/2, 39/0/1, 39/0/2 |
| `EDGE-cal-run7-C12-C14-C15-C17-on-2150eba0-79-0-1/` | the four Agenda readers | 2150eba0 | 79 / 0 / 1 |
| `EDGE-cal-run8-C18-alone-on-e03a1d23-the-reboot-kept-the-jumped-clock-34-3-2/` | C18, first cut (a reboot) | e03a1d23 | 34 / 3 / 2 — the METHOD failed: the emulator keeps a jumped clock across `adb reboot` |
| `EDGE-cal-run9-C18-alone-on-e03a1d23-36-0-2/` | C18, re-cut (stop + set the clock in one command) | e03a1d23 | 36 / 0 / 2 |
| `EDGE-cal-run10-C10-alone-on-e03a1d23-33-0-2/` | C10 (rows changing under the open Day view; its layout is memoised now) | e03a1d23 | 33 / 0 / 2 |

Every sub-step C01–C18 has a run with no failure. On the fifth build only C10 and C18 ran; C12, C14, C15, C17 stand
on 2150eba0 and the others on 6009c0b1 (the lead's call on what each build's diff touches). The lead's whole EDGE (no
`EDGE_ONLY`) has not been run by me. `edge_index.tsv`: B04.1's and B13.2's notes updated, a C18 line added.

### E21 — my share
`E21/producers.tsv`: 37 Calendar lines (the 36, plus `reminders count from … (the shell's start: the clock was set back
behind <old>)` → EDGE C18, moved from not-run after `EDGE-cal-run9-…` produced it). `E21/notrun.tsv`: 2 Calendar lines
(`sync … refused (not allowed)` → CalendarWriteLayerTest — the test corrected, the reason now names E24 leg D; `sync …
failed the copy could not be read` → CalendarWriteLayerTest). Checked with e21.sh's rule over every counted build's
folders: all 37 Calendar producer lines have a hit; nothing is in both tables. **e21.sh itself has not been run by me.**
`E21/builds.txt` (the lead's) names 3c1ad1e0 and 6009c0b1 with `*`; checked again over those two and the installed
e03a1d23 ALONE (no 2150eba0 or 3c5e3199 folder counted): all 37 Calendar lines still hit.
In neither table (no device producer, no JVM test named): `reminder poke failed: …`; `reminder …: failed notifications
are off`; `reminder …: dismiss refused / dismiss failed`; `calendars: n (local missing: it could not be created)`;
`calendar_sync.json: allowed: n of m entries dropped (…)` and the unreadable-store line; the raw-exception forms of
`sync …: failed <err>` and `write insert|delete …: failed <err>`; `[motion] cal_week_page`.

## Defect files of mine
- `defects/D-E9-1.md` — FIXED (33944662); verified on 6009c0b1, 2150eba0, e03a1d23.
- `defects/D-E19-1.md` — FIXED (dacca941); verified on 6009c0b1; the Agenda rows again on 3c5e3199.
- `defects/D-E19-2.md` — FIXED on 2150eba0; 24.00 again on 3c5e3199.
- `defects/D-E7-1.md` — the janky-frames clause. OPEN for the owner's ruling on the measure. Fixed along the way: the
  266.7 ms Agenda frame (2150eba0) and the Day view's 344.6 ms frame (100.1 ms on 3c5e3199, 50.1 ms on e03a1d23). The
  file holds every per-leg table; every frame is in `defects/D-E7-1-frames-*.tsv` (four runs up to 2150eba0).
No new defect FILE in the gate round: the Day view's frames were reported to the lead at once from the Dframes runs
and fixed in two builds; reviewer A's findings 1, 2, 9, 10 were proved fixed by the narrow runs above.

## Findings recorded, not defects
- **E24 / F26:** the provider's `instances` for the synced series' COPY holds 1 row, not 5. Recorded.
- **E24 / E6:** the AOSP Calendar posts no notification of its own for these alerts on this AVD.
- **E4:** a Calendar tile pinned from the app list is MEDIUM and shows only the day face. clauses-open, for the owner.
- **E6 / C09:** the system restarts the shell (the Home app) about a second after a `pm clear` or a force-stop.
- **C18 / E6:** with automatic time off, the emulator KEEPS a jumped clock across `adb reboot` (three days ahead before
  and after). My E6 clauses-open line said the opposite; corrected.
- **E18 leg G:** with WRITE_CALENDAR held, Android grants READ_CALENDAR at the request with no dialog (one group); the
  grant still goes through the app's callback (`permission request … granted`, `feeds started (calendar grant)`).
- **E24 leg D:** Delete on a changed occurrence of a synced series shows no prompt at all; the local exception row
  becomes `eventStatus=2`; Personal's copy keeps its master and its changed occurrence.
- **E17 leg T:** phase 14's Agenda pod lists today's birthday and, under its "Tomorrow" sub-header, tomorrow's.
- **E5:** an Agenda left open over an EMPTY calendar loads all its windows (8 → 16 → 32 → 56 weeks) at once.
- **j6.sh (phase 03, not mine):** its restore deletes nothing; e9.sh purges its `standup` around the children.
- **The editor:** Title / Location boxes x 20–340, the pick boxes x 12–201 (recorded in E19).
- **C16:** a clock set back under the running shell lowers `remindersSince` (logged); **C06:** a lost store restarts it.
- **Diagnostics lines in the code that neither the spec nor the Change Log names** — as listed in the first report.

## Time-of-day dependencies in the spec's own fixtures
- E4 legs A–E: the "tomorrow 09:00" Standup is inside the tile's 24 hours only after 09:00. Leg W is time-proof.
- E9: the "tomorrow at 2 pm" dentist is in Tess's read only after 14:00; asserted named only then.
- E17: closed by the product fix (all-day events by their date); leg T drives the doc's fixture at 19:00 local.
- E19: today's heading at the ink only on a Wednesday, at its text box otherwise (the lead's ruling).

## clauses-open.tsv lines of mine (14; two re-worded in the gate round)
E4 ×2, E22, E17 (RE-WORDED: closed by 53ca48dc; the fixture is the doc's again; leg T), E9 ×2, E19, E24, E6 ×2 (the
reboot line CORRECTED: the reason it gave was wrong), EDGE C01, C05, C10, C15.

## Not driven, and why
- The lead's whole EDGE and E21 itself: the lead's runs.
- On the fourth / fifth build only what the lead listed ran. Not run after 3c1ad1e0: E4 legs A–E, E5 legs B, C, E–I,
  E6, E17 legs A–F, E18 legs A–E, E22 legs R1, H, T, R2, J, L, E23, E24 legs H, N, U, G. Not run after 6009c0b1: E7
  legs D (as a row leg) and T, E9's children and legs B, V, D, G, N, E19 legs 4–8.
- e6.sh's tightened provider-alarm assertions: not run on a device.
- A SYSTEM locale change; a Sync the guard refuses; an unreadable copy row.
- Nothing spoken: every Tess request is typed.

## Device state left (read under the device lock at 03:59–04:00 MDT by my restore, after my last run)
Build e03a1d233ee6ba0c, `apk match yes`. `pm clear app.tileshell` → `provision.sh` rc 0 → `layout_restore` of
`baseline_layout.json` rc 0 → `ensure_start`. After it: `calendar_sync.json` =
`{"version":1,"allowed":[],"mappings":[],"hidden":[],"notifiedAlerts":[],"remindersSince":1790935147485}` (03:59 today);
one calendar (Tessera, `_id=2`), no events, no `calendar_alerts` row; raw contacts 2, no birthday row; READ/WRITE_CALENDAR,
READ/WRITE_CONTACTS, POST_NOTIFICATIONS granted; no calendar notification; no new crash; the calendar provider and the
AOSP Calendar enabled; zone America/Boise, `time_12_24` null, `auto_time` 1, no app locale, clock 0.2 s from the host;
Start on top. The People writer took the device after that (an `E13/` on e03a1d23 was being written at 04:01).

## Notes for whoever picks this up
- Scripts are edited only while they do not run: write-temp, `bash -n`, rename; an include is swapped by a queue job
  between runs.
- An install can land between two queued runs (the lock is free for a moment): a run's header says which build it is on;
  e7.sh's compare asserts that its two folders are on the installed build.
- Two `adb shell input tap` processes started in the same instant give the app one click; stagger them.
- `ring_save` / `ring_since` keep lines stamped at or after a MARK: after a backwards clock jump the lines are behind it.
- A walk that should cross the Agenda's load steps needs a FRESH Agenda over a populated calendar.
