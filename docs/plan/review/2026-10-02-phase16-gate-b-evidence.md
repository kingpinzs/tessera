# Phase 16 gate review — Reviewer B (testability and evidence), round 1 (2026-10-02)

Saved by the lead from the reviewer's hand-back (an independent Opus agent, read-only; brief
`2026-10-01-phase16-gate-brief.md`). Paths are under `docs/plan/qa/phase-16/` unless they begin `app/` or `docs/`.
The regenerated evidence table was identical to the committed one. Severity rule the reviewer used: BLOCKING where no
run on a counted build shows the clause at all; NOTE where the fact is in the kept evidence but the driver or table
does not assert it. E7's janky clause is not counted.

LENS B

**BLOCKING 1 — E19's Agenda-rows leg has no run on a build that contains the third build's Agenda rebuild.**
- Leg 3 (heading x 24, bar 8 epx at x 0, 40 / 56 heights, the 44-epx pitch, label 24.3, title 92.5) stands on 6009c0b1:
  `E19-run2-no-events-today-ink-at-25.67/E19.txt:128-151`, e.g. `:150 PASS K3.5 all-day bars on a 44-epx pitch`.
- 0889d45b then changed how those rows are stacked: `app/src/main/kotlin/app/tileshell/calendar/CalendarViews.kt:238-256`
  (one `DayGroup` item became head item + one item per event + foot).
- Only leg 1 ran on 2150eba0 (`E19-legs-1-on-2150eba0-64-0-7/E19.txt:12`); `E21/rerun.txt:5` and `E21/partials.txt:4`
  ("the other 235 assertions stand") do not cover it.
- `EventRow` itself is unchanged and the list has no item spacing, so the reviewer expects it to pass; it is simply not
  shown.
- Fix: E19 leg 3 alone on 2150eba0. The driver accepts only `E19_LEGS=1` (`scripts/e19.sh:44`), so it needs a leg-3
  mode that reaches the day as leg 2 does.

**BLOCKING 2 — Edge bullet "the allow-list cleared after a sync: the marker stays; the next Sync opens Can sync to" is
mapped to E24, and no driver taps Sync on a synced event whose target is un-ticked.**
- `scripts/edge_index.tsv:13` maps it to E24; `scripts/e24.sh:328-336` un-ticks Personal, opens only the delete prompt,
  re-ticks (`E24/E24.txt:159-162`).
- Every `cal_can_sync` assertion elsewhere is on an unsynced event (`e23.sh:68,121`, `e24.sh:79`).
- The branch is `app/src/main/kotlin/app/tileshell/calendar/CalendarPages.kt:244-250`; JVM tests cover the write layer's
  refusal, not this route.
- `E21/notrun.tsv:4` rests its reason on this undriven path.
- Fix, one step in E24 leg D (or a new EDGE sub-step): with Personal un-ticked, Sync on the third synced event → assert
  `cal_can_sync`, the `no calendar allowed -> can sync to` line, no `-> calendar <id>: ok|updated` line, the copy row
  unchanged, the marker still on the event page.

**NOTE 1 — E21's tables omit the four `assignSlotOnce` forms its own row text names** (`assigned`,
`assigned, replaced user's`, `already run`, `kept user's`); by the row's letter that fails it. The lines are asserted
in `E1-fixbuild-run1-driver-faults-and-auxio-fixture-87-6/E1.txt:85,86,99,105` and held in that folder's slices. Fix
before sign-off: four lines in `E21/producers.tsv`, run `e21.sh` again. Also in neither table (not Decisions lines):
`[motion] cal_week_page`, `people_jump_grid`, `appbar_menu`, `reminder poke failed`,
`calendar_sync.json: … entries dropped`.

**NOTE 2 — TRUST asserts only that a public version exists, not what it says** (`scripts/trust_lib.sh:79-80`;
`TRUST/TRUST.txt:147-148`). The fact is in `TRUST/N-record.txt:33-48` (`android.title=String (Calendar reminder)`, no
event title). Fix: assert that block in `trust_N`; `TRUST_ONLY=N` if device proof is wanted.

**NOTE 3 — Three TRUST grant assertions cannot fail.** `scripts/trust.sh:36,78-79,134` read `dumpsys` after the probe
finished: `TRUST/TRUST.txt:61` `grants:` is empty, `:62-63` pass on nothing, `:118` compares 0 with 0. "One read of one
row" is carried by the probe's own lines (`TRUST.txt:44-60`), which can fail. Fix: read the grant before the probe
finishes, or drop the three.

**NOTE 4 — E16's negative leg has only TestDPC's own switch as proof the policy is set.** The system's dump is recorded
as `disableContactsSearch=false` (`E16/E16.txt:68`, `scripts/e16.sh:197-199`), and there is no toggle-back control. Fix:
switch off again and expect `(+1 enterprise)`; device proof is E16's negative leg.

**NOTE 5 — `builds.txt` / `rerun.txt` / `partials.txt` bookkeeping.** `git diff --stat` for 6e524e56..dacca941 and
dacca941..a91837fd lists exactly the files `builds.txt` names; the row mapping has these gaps:
- E20 is in neither `rerun.txt` nor `partials.txt`, though the third build moved the "+" glyph. The table cites
  `E20-run2-pass-425-0-103/` where it read 14.00 (`E20.txt:378`); the proof, `E20-legs-plus-glyph-40-0-19/` on 2150eba0
  (40 / 0), is unpaired.
- `rerun.txt:7` says E3 drives the Agenda list; `e3.sh` never reads it. Agenda readers standing on earlier builds are
  E5, E17, E22, E24 and EDGE C03. That is acceptable, because E7 leg M, C12, C14, C15 and C17 read the new list with
  events on 2150eba0 — say so there.
- `builds.txt:2` (E25 on 686506a7) is stale: E25's evidence is on 3c1ad1e0, and the reason names files E25 does not
  drive.

**NOTE 6 — E7's measure (the owner's clause).**
- The empty-calendar run is a sound control: same 16 legs, swipes and resets, 439 against 432 frames, repeatable
  (14.19 / 14.38 / 14.58 % with events; 11.26 / 11.11 % empty).
- The percentage did not move when the 267 ms frame was fixed (14.38 → 14.58), so it does not measure what a user feels;
  the per-frame cap does.
- The driver cannot fail on option A as written: its only verdict is the absolute 5 % (`scripts/e7.sh:264`), the
  baseline is a RECORD in another run, and the longest frame is recorded for leg P3 only. If A is ruled, assert the
  longest frame over every leg under 100 ms and the difference from a same-build baseline ≤ 5 points.
- What remains is about one 33 ms settle frame per swipe; no paging frame exceeds 49.9 ms, and the first-drag frame is
  late on the empty calendar too. A recorded finding plus a look on the phone is proportionate; the reviewer would not
  ask for a product fix now.
- After the ruling the table stays OPEN: legs D and T pass only on 6009c0b1
  (`E7-run2-per-leg-diagnosis-janky-14.38-percent/E7.txt:96-108`), `rerun.txt:6` counts 2150eba0 only, and
  `partials.txt` has no E7 line.

**NOTE 7 — Logs cannot say which include produced them.** The header stamps the top script and `lib.sh` only. EDGE runs
1 and 4 carry the same `edge.sh` blob while C09's code differs (`EDGE-cal-run1-C01-to-C17-220-7-14/EDGE.txt:165` against
`EDGE-cal-run4-C09-alone-41-0-2/EDGE.txt:39`); `cal_lib.sh` was committed only at 00:40 and 02:07. Fix: `row_begin`
stamps `cal_lib.sh`, `people_lib.sh`, `p16.sh`, `trust_lib.sh`, `cal_geo.py`.

**NOTE 8 — `scripts/gate_evidence.py`.**
- `:97` picks evidence by the summary line and ignores FAIL lines; E26 prints a child verdict after its summary.
- With end `-` (E9, E19) it never checks that the base's FAIL sits in the re-run part. By hand it does: E9 `:51` in leg
  W, E19 `:78` in leg 1.
- The EDGE heading says "on the gate build", but C01–C11, C13 and C16 are on 6009c0b1 and every P sub-step on 3c1ad1e0.
  Every EDGE run is `EDGE_ONLY`, so `edge.sh:61-63` never ran.
- E1's "run again alone" overstates it: phase 01 E4 and phase 14 E3 were re-judged from kept transcripts
  (`E1_CHILDREN/E1_CHILDREN.txt:12`).

**NOTE 9 — `notrun.tsv` reasons.**
- `:3` says E16 asserts the work card has no Link; it is a RECORD (`scripts/e16.sh:133`, `E16/E16.txt:57`).
- `:6` says the clock cannot go back with no shell running. A reboot after a forward jump looks like it does exactly
  that (E6's own clauses-open entry); drive it once or correct the reason.
- `:4` names `CalendarWriteGuardTest`; the line is asserted in `CalendarWriteLayerTest.kt:508,581`.

**NOTE 10 — Edge index.**
- B13.2's `beginTime` / `endTime` prefill is mapped to E22, which sends only `calendar_id` and `title`
  (`scripts/e22.sh:126`); only `CalendarIntentsTest.kt:53` covers it.
- B20.1 is mapped to P09 but only recorded as not produced (`EDGE-people-run1-P01-to-P10-181-1-21/EDGE.txt:224`).

**NOTE 11 — E6.** Leg (b) records the AOSP Calendar's own notification as `none` (`E6/E6.txt:52`), so no device run
shows another app handling the alert; that case stands on `CalendarRulesTest.kt:450` and phone row P1. The "provider's
alarm" checks pass on any pending provider alarm (`scripts/e6.sh:122,207`).

**NOTE 12 — Weak drivers; this run's dumps are real.**
- `scripts/e28.sh:345` passes on `tests=0` (the log shows 28).
- `scripts/e15.sh:112` would accept a stale "Ann Lee".
- `scripts/e1.sh:137` (Maps auto-assign) cannot fail.
- `scripts/trust_lib.sh:114` shows no delivery of the 100 pokes; `:107` greps the allow-list file, not the build.
- Unanchored absences: `people_lib.sh:1346,1356,1369`, `e22.sh:73-76`.

**NOTE 13 — Records.**
- `clauses-open.tsv` lacks E1's two readings (`scripts/e1.sh:273`; `docs/plan/INDEX.md:116,122`).
- Its E15 APK line and `e15.sh:40` still say 2 MB.
- Ledger F26 is still OPEN, though recorded (`E24/E24.txt:129`: 1 of 5 instances) and put to the owner as P9.
- K3.3's ink read 26.00 on a Friday (`E19-legs-1-on-2150eba0-64-0-7/E19.txt:75`), so F36's "the W has no bearing" is
  unmeasured.

**Checked and sound**

Three forks of the reviewer read part of the rows; every finding above was re-checked by the reviewer against the file.

- **Level 2, every row:** each evidence log has `apk installed` on a counted build, `apk match yes`, 0 failed. Partials:
  E1's six FAILs are all before `--- W:`; E9's and E19's are in the re-run leg. No newer failed run sits behind any
  evidence run. Each log's driver blob is in git.
- **Level 1, trust-bearing:** E6, E15 (+ E15_APK), E16, E22, E23, E24, E28, TRUST, EDGE C01–C17 and P01–P10 (each
  sub-step 0 FAIL in the run the table names).
- **Level 1, others:** E1 / E1_CHILDREN, E2, E3, E4, E5, E7 (clauses other than janky), E8, E9, E13, E14, E17, E18, E21,
  E25, E26.
- **Level 2 only:** E10, E11, E12, E19 legs 2 and 4–8, E20, E27, T2SMOKE.
- **E21:** all 76 producer patterns hold a real line in the named row's own evidence folder (grep -E; about 30 read by
  eye); the five not-run JVM tests exist.
- **Strong points:** E22 leg L and E28 leg X reach a real refusal and would fail if the write layer ignored the guard;
  E24's "no write" is read from the provider's dirty flag; E6 (e) asserts its precondition; TRUST S proves the stale
  swipe on a reused alert id; E15 shows one read-only grant sourced by the Contacts provider.
- **Not checked:** `cal_geo.py` in full; other phases' child drivers; which API TestDPC's switch calls. No unit test run,
  nothing on the device.

GATE: FAIL (2 blocking)
