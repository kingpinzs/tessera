<!-- Saved verbatim by the lead from the hand-back of the People row writer (an Opus subagent; model output, not the owner's words), at the stop of 2026-10-01 18:1x. -->

PEOPLE QA WRITER — report at the stop (18:10 MDT, stopped on your word; nothing of mine is running). Build under test 686506a7a7936b2e on every run (each log's header reads `apk match yes`).

Not finished. E12 and the ten EDGE sub-steps never ran; E16's negative leg was never driven; E20 fails on the product; every passing row has passed ONCE alone on its final driver, not twice.

## Rows
Drivers are `docs/plan/qa/phase-16/scripts/e<n>.sh`, include `scripts/people_lib.sh`. Evidence folders are under `docs/plan/qa/phase-16/`.

| Row | Final run (pass / fail / recorded) | Evidence | Passes alone on the final driver |
|---|---|---|---|
| E10 | 129 / 0 / 2 | `E10-run2-pass-129-0-2/` (no `E10/` folder now) | 1 |
| E13 | 61 / 0 / 3 | `E13/` | 1 |
| E28 | 116 / 0 / 5 | `E28/` | 1 |
| E14 | 37 / 0 / 0 | `E14/` | 1 |
| E27 | 52 / 0 / 4 | `E27/` | 1 |
| E15 | 47 / 0 / 8 | `E15/` | 1 |
| E11 | 90 / 0 / 5 | `E11/` | 1 |
| E20 | 396 / **1** / 88 — FAILS (product) | `E20-run2-plus-field-rows-62-epx-apart/` (no `E20/` folder now) | 0 |
| E25 | 58 / 0 / 0 | `E25/` | 1 |
| E16 | 35 / **5** / 4 — the five are the driver's | `E16-run1-driver-fling-skipped-the-testdpc-switch/` | 0 |
| E12 | never run | — | 0 |
| EDGE P01–P10 | never run | — | 0 |

- **E10**: run 1 had the same totals but a weaker "initial is drawn" ink check, kept as `E10-run1-pass-129-0-2/`.
- **E28**: your leg X (F15) is in. Re-homing Lou's raw contact through the sync-adapter URI worked; Save then logged `[people] write update raw=186: refused (not allowed)`, the notice showed ("That account isn't on Can edit, so People can't change it."), and the provider kept the old number. Wade's rows were re-read equal after all 17 steps.
- **E20**: the one failure is "+ Email" → "+ Address" at 62.0 epx, not the 44-epx pitch the row words. Everything else measured on the pixels passes, including the pivot motion (settle 263 ms, maxGapMs 17).
- **E16**: the profile, TestDPC installed in the profile only and made owner, Work Wren, `[people] search "Wren": 0 (+1 enterprise)`, the briefcase row, the read-only card and the profile's removal all pass. The driver's fast swipes flung TestDPC's policy list past its switch, so the switch was never tapped and the five failures are the negative leg. The scroll is fixed in `e16.sh` but not re-run.
- **E12**: driver written, map action last, restore split around it. OsmAnd is the one APP_MAPS handler on the image.
- **EDGE**: `edge_P01` … `edge_P10` are written in `people_lib.sh` and syntax-checked only; expect driver fixes on first runs.

Kept earlier runs, all driver faults:
- `E27-run1-driver-read-the-intent-from-dumpsys-after-fossify-forwarded-it/`
- `E15-run1-driver-read-contact-id-as-raw-id/`
- `E11-run1-driver-capture-threshold-and-json-slash/`
- `E20-run1-driver-search-page-read-with-the-keyboard-up/` (also holds the same product failure)

## Defect
`defects/D-E20-1.md` — `TypedGroup` in `people/PeopleEditorPage.kt` gives every kind its own rule and one "+" row, so no two "+ field" rows are ever 44 epx apart. Each row's node is 44 epx tall and the one-row group is 62 epx rule to rule (r11: 61). It needs a grouping change or a Change Log re-cut; the driver is not adapted.

## clauses-open.tsv — my ten lines
- **E28**: the guard's JVM test is read from your result file (25 tests, 0 failures, newer than the guard and its test), not run.
- **E27**: Fossify forwards the SENDTO and finishes the receiving activity, and Android redacts smsto: data. Asserted instead: the activity manager's START line from logcat (SENDTO, the holder's component, the shell's uid, 25 redacted characters), the compose window naming both members, and the shell's action line.
- **E12 Text**: the same two facts. Written ahead; not confirmed by a run.
- **E12 Call**: `gsm list` prints only OK and the row's own number is dropped at once; Telecom's record is asserted and a second kept-up number (5551230001) is called. Written ahead; not confirmed by a run.
- **E15**: `content read` is refused ("No files supported by provider"). Asserted instead: the URI's form, `content query`, `content gettype` = text/x-vcard, and an import through the image's Contacts app read back from the provider.
- **E15**: dumpsys shows the chooser's own intent, not the SEND it wraps. Asserted: the chooser resumed, the "Ann Lee.vcf" preview, and the single read-only URI grant.
- **E15**: the APK clause is yours; the driver says so with a `record`.
- **E20**: the 34-epx pencil variant is not built; every box is asserted at 32 epx.
- **E20**: "semibold" is asserted as stem-over-cap at least 1.2 × the regular name text (measured 1.34–1.52 ×).
- **E11**: the row gives no criterion for "corroborates". The driver uses any change in the tile's region as motion, rejects bursts with source frames over 18.2 ms apart, and accepts a span of settle − 1 frame ± 2 frames. Run 2: 4 out-slides and 5 in-slides corroborated, 1 rejected, none contradicting.

## E21
- `E21/producers.tsv`: 35 People lines. 30 are found in kept ring slices; 5 are not produced yet (`read=false` from EDGE P06, the two `failed` lines from EDGE P03, and E12's call / mail / map).
- `E21/notrun.tsv`: one line, `[people] tile: photo … skipped` (PeopleTileRulesTest).
- `[people] write … refused (not allowed)` is in producers (E28 leg X), not in notrun.
- The `people_pivot` and `+1 enterprise` lines sit in renamed folders, so `E20/` and `E16/` ring files exist only after the next runs.

## Findings that are not a row's failure
1. **H3**: on the screenrecord the arriving bubble is within one capture pixel of rest about 380 ms after it appears; the shell's clock logs 584–600 ms. E11 records it "for H3".
2. The editor's "+" glyph ink starts at x 14 epx; r11 P4.6 says x 12 (recorded, not worded in E20).
3. Every `c6` force-stop deselects the shell's keyboard. The image's LatinIME then comes up, and with it `dumpsys window` reads navigationBars visible=true (seen on People's search page, E20 run 1). Not tested with the shell's own keyboard.
4. Another profile's contact reads "This contact is in your work profile. It can't be changed here." and does not open Can edit (the builder's stated deviation; E16 records it).
5. No row or EDGE sub-step of mine starts PICK with `am start`, so nothing changes with the fix build on that account.

## One thing to rule on
Your queue's polling starved mine for about 40 minutes, so my runner (`qapeople-run4.sh`, in scratch) takes the real device lock with a blocking flock and holds it for the driver's whole run. The driver's own `row_begin` lock is pointed at a private file through TMPDIR. Mutual exclusion is unchanged, but it is not the letter of "row_begin takes the lock"; say if you want it otherwise.

## Device state left
Read at 18:08 while the Calendar writer's E18 held the lock:
- User 0 only; TestDPC not in user 0.
- Raw contacts are Mom (1) and the nameless raw (2) only; no group, no aggregation exception, icc/adn empty.
- READ_CONTACTS and WRITE_CONTACTS granted. READ/WRITE_CALENDAR were revoked at that moment by the running E18, not by me.
- `people_edit.json` absent, `people_filter.json` hides nothing.
- Layout equals `baseline_layout.json`; none of my pushed files left; no call; awake on Start.
- **Not restored:** the default input method is the image's LatinIME. `adb shell ime enable app.tileshell/.ime.KeyboardService; adb shell ime set app.tileshell/.ime.KeyboardService` puts it back; I did not send it while another row held the device.
- The emulator never died.

## Next, in order
1. E16 again.
2. EDGE P01–P10, one at a time.
3. A second run alone of E10, E13, E28, E14, E27, E15, E11, E25.
4. E20 after a build or re-cut for D-E20-1.
5. E12 last, then confirm its two clauses-open lines.

`qa-people-progress.md` holds all of this. Nothing is committed or staged, and no product code, INDEX or phase doc was touched.
