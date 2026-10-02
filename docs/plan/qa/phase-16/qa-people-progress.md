# Phase 16 QA — People rows: running progress (the QA row-driver writer; picked up from this file alone)

Build under test: `app/build/outputs/apk/debug/app-debug.apk` md5 `686506a7a7936b2e` (worktree commit 83754009).
Drivers: `scripts/e<n>.sh`, include `scripts/people_lib.sh`. Runner (scratch, waits on the device lock):
`/tmp/claude-1000/-home-jeremyking/94273004-6013-4029-a961-ffb26e4dcb1f/scratchpad/qapeople-run2.sh e10.sh`.
Order: E10, E13, E28, E14, E27, E15, E11, E20, E25, E16, EDGE P01–P10, E12 LAST (its map action last inside it).

| row | driver written | last run (PASS / FAIL / recorded) | evidence folder | passes alone, in a row, on the final driver | defects | open clauses (clauses-open.tsv) |
|---|---|---|---|---|---|---|
| E10 | yes | run 2: 129 / 0 / 2 | `E10-run2-pass-129-0-2/` (run 1, same totals, before a helper fix: `E10-run1-pass-129-0-2/`) | 1 | none | none |
| E13 | yes | run 1: 61 / 0 / 3 | `E13/` | 1 | none | none |
| E28 | yes, with the lead's leg X (fix-round F15) | run 1: 116 / 0 / 5 — leg X produced `[people] write update raw=<id>: refused (not allowed)`, the notice, the old number kept | `E28/` | 1 | none | the JVM test is read from the lead's result file |
| E14 | yes | run 1: 37 / 0 / 0 | `E14/` | 1 | none | none |
| E27 | yes | run 2: 52 / 0 / 4 (run 1: 47 / 3 / 3, the driver read the smsto: intent from a dumpsys that no longer held it) | `E27/`; run 1 `E27-run1-driver-read-the-intent-from-dumpsys-after-fossify-forwarded-it/` | 1 | none | the smsto: data is redacted and the trampoline is gone from dumpsys |
| E15 | yes | run 2: 47 / 0 / 8 (run 1: 45 / 1 / 8, the driver parsed contact_id as the raw id) | `E15/`; run 1 `E15-run1-driver-read-contact-id-as-raw-id/` | 1 | none | `content read` refused; the chooser's wrapped intent; the APK clause is the lead's |
| E11 | yes | run 2: 90 / 0 / 5 (run 1: 86 / 3 / 4, the driver's capture threshold and a JSON "\/" grep) | `E11/`; run 1 `E11-run1-driver-capture-threshold-and-json-slash/` | 1 | none | the screenrecord's criterion |
| E20 | yes | run 2: 396 / **1** / 88 — FAILS on the product: "+ Email" → "+ Address" are 62 epx apart, not 44 | `E20-run2-plus-field-rows-62-epx-apart/` (there is no `E20/` folder now); run 1 `E20-run1-driver-search-page-read-with-the-keyboard-up/` (two driver failures beside the same product failure) | 0 (fails) | `defects/D-E20-1.md` | the 34-epx pencil variant; "semibold" |
| E25 | yes | run 1: 58 / 0 / 0 | `E25/` | 1 | none | none |
| E16 | yes; the scroll to TestDPC's switch was fixed AFTER run 1 and is NOT re-run | run 1: 35 / **5** / 4 — the profile, TestDPC as owner, Work Wren, the positive leg (`0 (+1 enterprise)`, the briefcase row), the read-only card and the restore all PASS; the five failures are the negative leg, which was never driven: the driver's fast swipes flung TestDPC's policy list past its switch | `E16-run1-driver-fling-skipped-the-testdpc-switch/` | 0 | none (the driver's) | none yet |
| E12 | yes (map action last; restore split in two around it) | NEVER RUN — it runs LAST of everything | — | 0 | — | two lines are in clauses-open.tsv from the Change Log's facts (gsm list; smsto:), written ahead and NOT yet confirmed by a run |
| EDGE P01–P10 | yes: `edge_P01` … `edge_P10` in `people_lib.sh` | NEVER RUN (`EDGE_ONLY=P03 bash scripts/edge.sh` runs one) | — | 0 | — | — |

E21: `E21/producers.tsv` holds the People lines (35 alternatives: 30 found in the kept ring slices; 5 not produced yet —
`read=false` and the two `failed` lines of EDGE P06 / P03, and E12's call / mail / map actions) and `E21/notrun.tsv`
one line (`[people] tile: photo … skipped`, PeopleTileRulesTest). `[people] write … refused (not allowed)` is in
producers.tsv (E28 leg X produced it), not in notrun.tsv.

## STOPPED at 18:10 on the lead's word (the account's usage limit). What is next, in order
1. E16 again (driver fixed: short slow swipes to TestDPC's "cross-profile contacts search" switch; if the label is still
   not found, read `E16/dpc*.xml` for TestDPC's wording).
2. EDGE P01–P10, one at a time (`EDGE_ONLY=P0x`); none has run, so expect driver fixes. P01 imports 5,000 contacts as
   one vCard through the image's Contacts app and deletes them by name pattern.
3. A second run alone of E10, E13, E28, E14, E27, E15, E11, E25 (each has ONE pass on its final driver).
4. E20 again when a build changes the editor's "+ field" rows or the clause is re-cut (D-E20-1); the driver is final.
5. E12 LAST, its map action into OsmAnd last inside it; then confirm or correct its two clauses-open lines.
6. Regenerate nothing in E21 by hand: append the EDGE / E12 producers' verification once those rows have rings.

## Log
- 15:45 read the two prompts, the spec (all), the 2026-10-01 Change Log entries, BUILDSTART/README, p16.sh, lib.sh, e1.sh,
  the People builder report and its dev scripts (navigation only). Device: emulator-5554 alive, build matches, raw
  contacts are Mom (1, qa/qa) and the nameless phone-only raw (2). User 0 only.
- 16:00 `people_lib.sh` (helpers, list walk, drawn-pixel list geometry) and `e10.sh` written; first run queued.
- 16:07–16:20 E10 run 1 and run 2: 129 / 0 / 2 both. Run 1 kept as `E10-run1-pass-129-0-2`; between them the
  half-level ink test in `people_lib.sh` was made a signed projection (run 1's "initial is drawn" check could have
  passed on the black page around the disc). Run 2 is the first on the final helper.
- 16:20–17:00 the Calendar writer's rows (E4, E5, E22, E23) held the device back to back; a polling runner starved.
  Runner now: `qapeople-run4.sh` — it takes the REAL device lock with a blocking flock and holds it for the driver's
  whole run (the driver's own row_begin lock is pointed at a private file through TMPDIR, because the real one is
  already held on its behalf). Mutual exclusion is unchanged; only the wait is a queue instead of a poll.
- 17:00 drivers written for every row and the ten EDGE sub-steps; the page geometry measurer (`people_geo`) was tried
  offline on the builder's LOOK1 / LOOK2 screenshots.
- Lead's messages: (1) E28 gains leg X (fix-round F15) — done in e28.sh; if its refused line is produced, move
  `[people] write … refused (not allowed)` from E21/notrun.tsv to producers.tsv. (2) The fix build changes PICK started
  with `am start` (no caller → the plain list): none of the People rows or EDGE sub-steps starts PICK, so nothing here
  "changes with the fix build" on that account.
- 17:05–18:07 runs: E13 (61/0/3), E28 (116/0/5), E14 (37/0/0), E27 run 1 (driver) and run 2 (52/0/4), E15 run 1
  (driver) and run 2 (47/0/8), E11 run 1 (driver) and run 2 (90/0/5), E20 run 1 (driver + product) and run 2 (396/1/88,
  D-E20-1), E25 (58/0/0), E16 run 1 (35/5/4, driver). clauses-open.tsv gained the People lines; E21's two files were
  started.
- Findings that are not a row's failure (for the lead): (1) H3 — on the screenrecord the arriving bubble is within one
  capture pixel of rest about 380 ms after it appears (the ease-out's last third); the shell's clock logs 584–600 ms
  (E11's RECORD "for H3"). (2) the editor's "+" glyph ink starts at x 14 epx, r11 P4.6 says x 12 (recorded in E20).
  (3) every `c6` (force-stop) deselects the shell's keyboard; the image's LatinIME then comes up, and while it is up
  `dumpsys window` reads navigationBars visible=true (E20 run 1, on People's search page). Whether the system nav bar
  also shows with the SHELL's keyboard up was not tested. (4) another profile's contact reads "This contact is in your
  work profile. It can't be changed here." (the builder's stated deviation; E16 records it).
- Device state left at 18:08 (read while the Calendar writer's E18 held the lock): user 0 only, TestDPC not in user 0;
  raw contacts Mom (1) and the nameless raw (2) only; no group, no aggregation exception, icc/adn empty;
  READ_CONTACTS and WRITE_CONTACTS granted; people_edit.json absent (nothing allowed), people_filter.json hides nothing;
  the layout equals `baseline_layout.json`; no pushed `qa-solid-*.jpg` / `qa-big-photo.jpg` / `qa-e5k.vcf`; no call;
  awake on Start; the build is 686506a7a7936b2e. NOT restored by me: the default input method is the image's LatinIME
  (every force-stop deselects the shell's keyboard; `adb shell ime enable app.tileshell/.ime.KeyboardService; adb
  shell ime set app.tileshell/.ime.KeyboardService` puts it back — not sent, the other writer's row held the device).
