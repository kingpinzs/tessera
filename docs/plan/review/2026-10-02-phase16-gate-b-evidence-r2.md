# Phase 16 gate review — Reviewer B (testability and evidence), round 2 (2026-10-02)

Saved by the lead from the reviewer's hand-back (the same independent Opus agent as round 1, read-only; brief
`2026-10-02-phase16-gate-r2-brief.md`). Paths are under `docs/plan/qa/phase-16/` unless they begin `app/` or `docs/`.
The regenerated table for `585b457ffc29878c` was identical to `docs/plan/review/2026-10-02-phase16-gate-evidence-r2.md`.

LENS B — ROUND 2

**B-1 — CLEARED.** `scripts/e19.sh:165-166` (leg 3's anchor and its `geo … sections=rows` call, reached through
`E19_LEGS=1,2,3` at `:146`) ran on 3c5e3199: `E19-legs-1-2-3-on-3c5e3199-127-0-12/E19.txt:136-157`, ending
`:157 PASS K3.5 all-day bars on a 44-epx pitch … 44.00 vs 44.00`. That build holds both list changes (0889d45b,
639fef18). The claim about the later builds holds: `git diff --stat 3573219e..e226bc68 -- app/src/main` lists one
Day-view constant in `CalendarViews.kt`, plus `PeopleApp.kt` and `PeopleData.kt`.

**B-2 — CLEARED.** `scripts/e24.sh:358-369` can fail both ways:
- If Sync wrote or even ran: `:367` refuses any `sync event=<id> -> calendar` line (ok, updated or refused), and `:369`
  compares the copy's row with `dirty=0` set beforehand.
- If "Can sync to" did not open: `:365-366`.
- Log: `E24-legs-D-on-3c5e3199-94-0-7/E24.txt:102-111`, with `:105` (`cal_can_sync = yes`), `:107` (no outcome line) and
  `:109` (`dirty=0`, unchanged) deciding it. Nothing after that build touches the Sync route.

**No new BLOCKING.** E7's janky-frames clause remains the owner's (Q-16-6).

**This round's narrow runs, level 1 — each can fail on its defect**
- **E5 leg D** (`scripts/e5.sh:117-118`; `E5-legs-D-on-e03a1d23-18-0-2/E5.txt:37-38`): three wider windows loaded after
  the walk's MARK, and no first date going backwards. A throw-back to the selected day would show as 2026-10-02
  reappearing. The hollow run is kept and excluded by the supplement's prefix.
- **E17 leg T** (`scripts/e17.sh:97,101`; `E17-legs-T-on-3c5e3199-22-0-3/E17.txt:37,40`): at 19:00 MDT the old read
  would show Bob and drop Ann; both absences are anchored by Ann's presence.
- **E13 create** (`scripts/e13.sh:75,86,90`; `E13-legs-create-on-e03a1d23-30-0-2/E13.txt:35,43`): a take-back would
  remove the row and fail the provider reads, not only the line check.
- **E13 grant** (`scripts/e13.sh:190-192`; `E13-legs-grant-on-585b457f-49-0-4/E13.txt:44`): shown to fail on the defect
  by the kept e03a1d23 run (21 / 24, `:37-40`).
- **E7 compare** (`scripts/e7.sh:273-282`): the same-build check and the 100 ms cap both failed when they should in the
  kept run 1 (`:105,107,110`: fourth-build folders, D1 100.1 ms). The passing run reads D1 50.1 ms and 14.98 % against
  11.55 %.
- **Also sound:** E4 leg W, E18 leg G, E22 leg X, E27 create, E15 sim, E16 negative, TRUST P,N, EDGE C10 and C18.
- Every driver and include blob stamped in these logs is in git.

**Bookkeeping — truthful where followed**
- Followed table → log for E3, E8, E9, E13, E19, E24, TRUST, E15_APK and E21: each named run's header build and counts
  match.
- `builds.txt`'s three new diff claims match `git diff --stat` (13 files; one constant; two People files).
- Gaps are the notes below.

**NOTE 1 — the sixth build changed where People registers its provider observer, and no run on 585b457f shows a People
page following a provider change with the permissions held.** EDGE P03 and P08 (index lines 53 and 70: card or group
page closes when deleted elsewhere) stand on 3c1ad1e0. `E21/builds.txt:6` says E13's grant leg drives the change; that
leg shows no crash and the tile's observer, not this. Held at NOTE because the normal path is the same call behind a
condition that is true from the first composition (`app/src/main/kotlin/app/tileshell/people/PeopleApp.kt:181,183,267-269`).
Proof if wanted: P03's "deleted while its card is open" step alone on 585b457f.

**NOTE 2 — E28 has no supplement line, though the fourth build changed the create it drives.** "A contact saved with
the allowed account lands there" now passes through the read-back compare
(`app/src/main/kotlin/app/tileshell/people/PeopleWrites.kt:116-121,472-476`); only phone-account creates were re-run.
Covered by `PeopleWriterTest.kt:148-150,196-202`, and the earlier E28 run showed the provider stores the account as
asked. E28's guard-test output in its log also predates the guard's `TakeBack` case. Fix: a supplements line naming the
JVM test, or E28's "Allowing an account" leg alone.

**NOTE 3 — E7, for when it closes.**
- Leg T (the tile with 5,000 events) last passed on 6009c0b1
  (`E7-run2-per-leg-diagnosis-janky-14.38-percent/E7.txt:105-107`), before 53ca48dc changed the tile's read; E4 leg W
  shows the rule on e03a1d23. Name one of them in `partials.txt`.
- `scripts/cal_frames.py:52-54` prints 0.0 for a leg with no readable frames, so `e7.sh:257,279-280` would pass an
  unread leg. Assert frames > 0 per leg.
- `cal_frames.py` is not in `STAMP_FILES` (`scripts/p16.sh:24`).

**NOTE 4 — wording.**
- `E21/partials.txt:4` still says "leg 1 alone … the other 235 assertions stand"; legs 1–3 were re-run and legs 4–8
  stand.
- `E21/rerun.txt:7` (E9): Tess's delete also runs through the changed read (`TessCalendar.kt:63`, the 30-day window).
  It is covered by `InstanceWindowTest.kt:72-77`; E9 leg D and E22 leg T were not re-run. Say so.

**NOTE 5 — E19 leg 6's day-page motion.** Its only measurement is 264 ms against a 267 bound, on 6009c0b1
(`E19-run2-no-events-today-ink-at-25.67/E19.txt:224-226`). No slice on builds four to six holds a `cal_day_page` line,
and the Day view's body changed there (same output for a day of up to 8 blocks). Proof if wanted: leg 6 alone.

**Round-1 notes — does the triage's action answer each?**
1. Yes: four producers, `E21/E21.txt:104-107`, 84 / 0 on 585b457f.
2. Yes: `scripts/trust_lib.sh:84-86`; `TRUST/TRUST.txt:139-141`.
3. Yes: recorded, not asserted (`TRUST/TRUST.txt:68,123`).
4. Yes: the switch off again brings `(+1 enterprise)` back (`E16-legs-negative-on-e03a1d23-48-0-7/E16.txt:84-87`).
5. Yes for E20, E3's reason and E25's line; what is left is notes 1–4 above.
6. Yes, as far as it can be before the ruling (compare mode above).
7. Yes: include stamps in every new header; `cal_frames.py` missing (note 3).
8. Yes: the FAIL-line rule, the heading and "judged again" are in; the partial branch (`scripts/gate_evidence.py:138`)
   still reads the summary only.
9. Yes: Link is now asserted (`scripts/e16.sh:161`; `E16-legs-negative…/E16.txt:57`); the sync line names
   `CalendarWriteLayerTest`; the clock-set-back form is driven as C18
   (`EDGE-cal-run9-C18-alone-on-e03a1d23-36-0-2/EDGE.txt:43`). The reviewer's round-1 suggestion was wrong on one point:
   a reboot keeps the jumped clock on this emulator (run 8 kept); the driver found another way.
10. Yes: B13.2's times are asserted in E22 leg X; B20.1 is marked not produced, an open clause.
11. Yes: `scripts/e6.sh:124-125` ties the alarm to the alert; leg (b) stays with P1.
12. Yes, except `scripts/e1.sh:137` (the Maps assert), which is unchanged.
13. Yes, except that ledger F36 still says "the W has no bearing"; K3.3's ink read 26.00 on a Friday again
    (`E19-legs-1-2-3-on-3c5e3199-127-0-12/E19.txt:81`).

GATE: PASS
