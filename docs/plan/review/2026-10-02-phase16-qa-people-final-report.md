# Phase 16 — People QA row writer, final report (2026-10-02 00:38 MDT)

Saved by the lead from the row writer's hand-back (a subagent cannot write a report file). The writer is an independent
Opus agent; this is its account, not the lead's. The gate's own table is generated from the run folders
(`qa/phase-16/scripts/gate_evidence.py`).

Every People row, both defects' re-runs, the EDGE sub-steps and E12 have one passing run on a fix build; nothing of the
writer's is left to run or is running.

## Rows — the one counted run each

Drivers are `docs/plan/qa/phase-16/scripts/e<n>.sh` with `scripts/people_lib.sh`; folders are under
`docs/plan/qa/phase-16/`. Each log's header reads `apk match yes` for the build named.

| Row | Pass / fail / recorded | Build | Folder | Earlier runs on a fix build |
|---|---|---|---|---|
| E10 | 129 / 0 / 2 | 3c1ad1e0 | `E10/` | — |
| E11 | 87 / 0 / 6 | 3c1ad1e0 | `E11/` | two driver-fault runs kept |
| E12 | 63 / 0 / 14 | 3c1ad1e0 | `E12/` | one driver-fault run kept |
| E13 | 61 / 0 / 3 | 3c1ad1e0 | `E13/` | — |
| E14 | 37 / 0 / 0 | 3c1ad1e0 | `E14/` | — |
| E15 | 47 / 0 / 8 | 3c1ad1e0 | `E15/` | — |
| E16 | 44 / 0 / 7 | 6009c0b1 | `E16/` | three kept (below) |
| E20 | 425 / 0 / 103 | 3c1ad1e0 | `E20-run1-pass-425-0-103/` | — |
| E25 | 58 / 0 / 0 | 686506a7, by the ruling | `E25-build-686506a7-run1-pass-58-0-0/` | — |
| E27 | 52 / 0 / 4 | 3c1ad1e0 | `E27/` | — |
| E28 | 118 / 0 / 5 | 3c1ad1e0 | `E28/` | — |
| EDGE P01–P09 | 0 failed each, in the grouped run (181 / 1 / 21) | 3c1ad1e0 | `EDGE-people-run1-P01-to-P10-181-1-21/` | — |
| EDGE P10 | 58 / 0 / 4, alone | 3c1ad1e0 | `EDGE-people-run2-P10-58-0-4/` | its failing leg is in the grouped run |

Two runs happened that the owner's ruling excludes, because a batch already held the lock when it arrived: a second E20
(425 / 0 / 103, in `E20/`) and E25 on 3c1ad1e0 (58 / 0 / 0, in `E25/`).

Kept non-counted runs on the fix builds:
- **E11**: `E11-run1-driver-capture-burst-took-a-stray-frame/` (89 / 1 / 5) and
  `E11-run2-driver-a-late-dump-missed-the-resting-bubble/` (88 / 1 / 5). Both were the driver's capture and dump
  handling; every clause on the shell's clock passed in both.
- **E12**: `E12-run1-driver-three-reads-and-a-call-left-up/` (55 / 3 / 12). Three reads were wrong for this image, and
  the run left the kept-up call up for about ten minutes; ended under the lock at 00:04.
- **E16**: `E16-run1-driver-testdpc-switch-is-on-its-second-page/` (35 / 5 / 5),
  `E16-run2-empty-search-says-nothing-and-a-driver-dumpsys-read/` (39 / 3 / 6, the run that found D-E16-1),
  `E16-run3-driver-misread-the-keyboard-line/` (42 / 2 / 7 on 6009c0b1).
- All 686506a7 folders are renamed `E<n>-build-686506a7-…`.

The emulator stayed up through E12's OsmAnd map action on both runs.

## Defects — both fixed and re-run

- `defects/D-E20-1.md`: fixed in 3c1ad1e0. E20 re-cut as the lead said: "+ Phone" → "+ Email" is 44.0 epx on a
  name-only contact's editor; across a group rule it is 62.0 epx against 61 ± 1, at the edge of tolerance.
- `defects/D-E16-1.md`: corrected to the lead's reading (the line was drawn as `people_notice` under the keyboard).
  Fixed in 6009c0b1: `people_empty` reads `No contacts match "Wren".` at y 398–452, with the keyboard's top at y 1422
  and the keyboard asserted up.

## clauses-open.tsv — the writer's thirteen lines

- **E28**: the two JVM tests (PeopleWriteGuardTest, and PeopleWriterTest for F15) are read from the lead's result files,
  not run.
- **E27** and **E12 Text**: Fossify's trampoline has gone from dumpsys by dump time and Android redacts smsto: data.
  Asserted instead: the activity manager's START line from logcat, the compose window's recipients, and the shell's
  action line.
- **E12 Call**: `gsm list` prints only OK. Asserted from Telecom's record; a second kept-up number (5551230001) is
  called and read while it is the foreground call.
- **E12 Mail**: the image has one mailto: handler (FairEmail), so Android shows no resolver page. Asserted: the shell's
  `-> resolver` line and the sole handler resumed with the SENDTO.
- **E12 Address**: OsmAnd's trampoline has gone from dumpsys and geo: data is redacted. Asserted: the START line (VIEW,
  geo:, OsmAnd, the shell's uid) and the query from the shell's line.
- **E15**: `content read` is refused; the stream is read back through `content query`, `content gettype` and an import.
- **E15**: dumpsys shows the chooser's own intent, not the SEND it wraps.
- **E15**: the APK clause is the lead's (`scripts/e15_apk.sh`).
- **E20**: the 34-epx pencil variant is not built; every box is asserted at 32 epx.
- **E20**: "semibold" is asserted as stem-over-cap at least 1.2 × the regular name text.
- **E11**: the screenrecord criterion. A slide is a run of changed frames with no pause over 40 ms, rejected if any
  source-frame gap exceeds 18.2 ms, accepted at settle − 1 frame ± 2 frames. Counted run: 5 out-slides and 5 in-slides
  corroborated, none rejected.
- **EDGE P01**: the 5,000 contacts come from one vCard import (19 s), not a `content insert` loop. "Within 3 s" is the
  shell's list line (175 ms after the MARK) plus a dump taken straight after; the dump's own completion time is not
  stamped.

## E21

- `E21/producers.tsv` holds 40 People lines, five of them produced by the lead's TRUST row (`open other`, the three
  `pick:` lines, the two granted lines). Checked offline with `e21.sh`'s own folder rule: every one is found in a kept
  slice of the two fix builds.
- `E21/notrun.tsv` holds two People lines: `tile: photo … skipped` (PeopleTileRulesTest) and
  `link …: refused (not allowed)` (PeopleWriterTest). E16 records that the work-profile card offers no Link.
- The writer did not run `e21.sh` itself.

## Findings that are not a row's failure

1. **H3**: on the screenrecord the arriving bubble is within one capture pixel of rest about 370–385 ms after it
   appears; the shell's clock logs 584–600 ms. E11 records it "for H3".
2. The editor's "+" glyph ink starts at x 14 epx; r11 P4.6 says x 12 (recorded in E20, not worded in the row).
3. Every `c6` force-stop deselects the shell's keyboard, so the image's LatinIME was the keyboard in every typing leg,
   including E16's keyboard-top read (1422 px). The same line under the shell's own keyboard was not tested.
4. Another profile's contact reads "This contact is in your work profile. It can't be changed here." (the builder's
   stated deviation; E16 records it).

## Device state left (read at 00:38)

- Build 6009c0b14a87039f; emulator alive, awake on Start; user 0 only; TestDPC not in user 0.
- Raw contacts are Mom (1) and the nameless raw (2); no group, no aggregation exception, icc/adn empty.
- READ_CONTACTS and WRITE_CONTACTS granted; `people_edit.json` and `people_filter.json` absent.
- Layout equals `baseline_layout.json`; none of the writer's pushed files left; no call.
- **Not put back:** the default input method is LatinIME.
  `adb shell ime enable app.tileshell/.ime.KeyboardService; adb shell ime set app.tileshell/.ime.KeyboardService`
  restores it.

`qa-people-progress.md` holds the row table and the full history.
