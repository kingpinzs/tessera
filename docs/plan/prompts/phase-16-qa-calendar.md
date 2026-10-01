# Phase 16 QA brief — the Calendar rows

Read `docs/plan/prompts/phase-16-qa-common.md` first: its rules bind you. Your scratch prefix is `qacal-`; your include
file is `docs/plan/qa/phase-16/scripts/cal_lib.sh`.

## Your rows (acceptance criteria in `docs/plan/phase-16-inbox-calendar-people.md`)
E3, E4, E5, E6, E7, E8, E9, E17, E18, E19, E22, E23, E24 — one driver each, `scripts/e<n>.sh`.

Order: E3, E4, E5 first (they prove the basics the others lean on), then E22, E23, E24 (the owner's rules: no write to
an account calendar by any path), then E6, E17, E18, E9, E19, E8, E7.

Notes per row (they do not replace the row's text):
- **E4** includes the pinned Calendar app tile (r3 D12): pin "Calendar" from the app list (phase 02's Pin to Start; see
  how `docs/plan/qa/phase-15/scripts/e1.sh` holds a row and reads the menu), compare its face text with the CALENDAR
  slot tile's, restore with `layout_restore "$BASELINE"`.
- **E6**: every clock move is `jump_clock` FORWARDS, restored with `clock_restore`; `shell_alarms_pending` before and
  after the reminder insert (the builder notes it read 0 both times — prove the helper can count: arm one of the
  shell's own alarms first, e.g. a Clock timer, read a non-zero count, then clear it); (b) the race with the AOSP
  Calendar enabled, (c) the forged poke, (d) the reboot leg with `wake_device` asserted `Awake`.
- **E7**: 5,000 events by a driver loop into the QA calendar (batch the inserts — one `adb shell` running a loop on the
  device is far faster than 5,000 `adb` calls; record the run time), a MARK immediately before each timed tap or swipe.
- **E8**: the DST-night event's 3-hour span in the Day view is a clause; the builder did not drive it.
- **E9** is the re-cut of phase 03 E2's calendar rows: it re-runs `docs/plan/qa/phase-03/scripts/j6.sh` on this build as
  a child (children run BEFORE your `row_begin`, each takes the device lock itself; move its row folder aside and put
  it back, keeping the new run under `E9/` — `scripts/e1.sh`'s `run_row` is the form), again with a Birthdays calendar
  present, then its own typed requests, Tess's allowed delete, phase 03 E10's gated calendar commands, and phase 03 E7
  and E14 re-run unchanged.
- **E17**: the Calendar app is NOT opened before the first birthday is inserted; the 29 February form is
  `FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1` (re-cut 2026-10-01); the Agenda pod's row and Tess's typed "what is on my
  calendar" are clauses; the "Creation refused" sub-step disables `com.android.providers.calendar` and restores it.
- **E19**: geometry is asserted on the drawn pixels where a dump's bounds are a touch target (a text field's tag node
  reads 48 epx in a dump — the box is measured in a screencap; the builder's `dev-cal/scripts/geo.py` shows a way; write
  your own check from r11/calendar.md's values and the row's tolerances). Both `[motion]` lines with `maxGapMs` ≤ 33.4.
- **E22–E24**: `cal_lists` before every count; Work holds exactly "Offsite" after every step; the copy hidden in every
  view, the feed's `refresh` line, the pod and Tess; one reminder for a synced event (`jump_clock` forwards).
- Removing Tessera rows needs the sync-adapter URI (the builder's `purge_tessera_events`); an occurrence's time comes
  from `Instances`, not start + k weeks (a DST change falls on 1 November).

## Your EDGE sub-steps (functions `edge_<ID>` in `cal_lib.sh`; ids from `scripts/edge_index.tsv`)
C01 C02 C03 C04 C05 C06 C07 C08 C09 C10 C11 C12 C13 C14 C15 C16 C17.
C16 is the one backwards clock jump in the phase: `ring_save`, `am force-stop app.tileshell`, `ensure_start` BEFORE its
MARK, and `ALLOW_BACKWARDS=1 jump_clock …` (p16.sh refuses a backwards jump otherwise).

## Diagnostics alternatives that are yours for E21
Every `[calendar] …` alternative (calendars: created / present / none / local missing / denied; view agenda / day /
week; write ok / failed; reminder notified / dismissed / skipped; birthdays synced / could not be created; local
calendar created / lookup failed / could not be created; each `sync` outcome; `counts`), `[motion] cal_month_dropdown`
and `cal_day_page`. `sync … refused (not allowed)` and `write … failed refused (not allowed)` belong in `notrun.tsv`
with the guard's JVM test (`CalendarWriteGuardTest`). Any alternative the builder added beyond the spec (the Change Log
names them) gets a producer or a `notrun.tsv` line too.
