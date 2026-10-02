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

## RESUMED 19:56 on the FIX BUILD — md5 3c1ad1e0e65bd919 (commit 6e524e56). Runs on 686506a7 no longer count.
Every 686506a7 folder is renamed `E<n>-build-686506a7-…` (the tables above name them without that part).
Runner: `qapeople-queue.sh <driver> …` (scratch) — keeps a row's last run as `E<n>-run<k>-<pass|FAIL>-<p>-<f>-<r>`,
then runs it through `qapeople-run4.sh` (blocking flock on the real device lock for the whole run; the lead agreed).
Driver changes at the resume: `people_geo editor` re-cut for D-E20-1's fix (two "+ field" rows of one group 44 epx;
across a rule 61 epx; `nameonly` mode for a contact with no number and no e-mail) and `e20.sh` opens a name-only
fixture's editor; `e16.sh` records whether the work card offers Link; `e28.sh` also reads PeopleWriterTest (F15);
`E21/notrun.tsv` gained `[people] link …: refused (not allowed)` (PeopleWriterTest).
Order: E16, E20, then E10, E13, E28, E14, E27, E15, E11, E25 (each twice alone), EDGE P01–P10, E12 LAST.

| row | fix-build runs (PASS / FAIL / recorded) | evidence | passes alone in a row |
|---|---|---|---|
| E16 | run 1: 35 / 5 / 5 (driver: the switch is on TestDPC's second page); run 2: 39 / **3** / 6 — the switch driven, `0 (+0 enterprise)` passes, but an empty search shows NO line of text ("says so" fails: `defects/D-E16-1.md`); the third failure was the driver's own extra dumpsys read, removed | `E16-run1-driver-testdpc-switch-is-on-its-second-page/`, `E16-run2-empty-search-says-nothing-and-a-driver-dumpsys-read/` | 0 — FAILS on the product (D-E16-1); a third run is queued last |
| E20 | run 1 (re-cut for F31): 425 / 0 / 103 — D-E20-1 fixed ("+ Phone" → "+ Email" 44.0 epx on the name-only editor; across a rule 62.0 against 61 ± 1) | `E20/` until its second run renames it | 1 |
| E10 | run 1: 129 / 0 / 2 | `E10/` | 1 |
| E13 | run 1: 61 / 0 / 3 | `E13/` | 1 |
| E28 | run 1: 118 / 0 / 5 (leg X refused line produced again; PeopleWriterTest read) | `E28/` | 1 |
| E14 | run 1: 37 / 0 / 0 | `E14/` | 1 |
| E27 | run 1: 52 / 0 / 4 | `E27/` | 1 |
| E15 | run 1: 47 / 0 / 8 | `E15/` | 1 |
| E11 | run 1: 89 / 1 / 5 and run 2: 88 / 1 / 5 (both the driver's: a stray changed frame counted into a slide; a gesture-driver dump that came after the bubble's rest); run 3: 87 / 0 / 6 | `E11/`; `E11-run1-driver-capture-burst-took-a-stray-frame/`, `E11-run2-driver-a-late-dump-missed-the-resting-bubble/` | 1 |
| E25 | 58 / 0 / 0 on the fix build (run before the owner's ruling arrived; the ruling says its 686506a7 run stands) | `E25/`; `E25-build-686506a7-run1-pass-58-0-0/` | 1 |
| E20 | a second run, 425 / 0 / 103, also happened before the ruling was read | `E20/` (second), `E20-run1-pass-425-0-103/` (first) | 2 |

### 22:14 — the owner's ruling, relayed by the lead: "Only test the fixes not EVERY THING"
No second passes; no final run of every row. Done once on 3c1ad1e0 and NOT to be touched: E10, E13, E14, E15, E20, E27,
E28 (and E11 now, E25 by its 686506a7 run). Still to do, each once: the ONE EDGE run with P01–P10 (kept as
`EDGE-people-run1-…`; a failing sub-step fixed and re-run alone), E12 LAST, the E21 check, and E16 once on the build
that carries D-E16-1's fix (the lead installs it and says so; do not run E16 before).
D-E16-1 (corrected): an empty search DOES draw `No contacts match "<q>".` — as `people_notice`, in the band above the
app bar, under the keyboard that is up while a query is typed. `e16.sh` is re-cut to: `people_empty` exists, reads
`No contacts match "Wren".`, its bottom edge above the keyboard's top (the IME's visible frame from dumpsys window).
Helper fix: an attribute whose text holds a double quote is dumped single-quoted — the text reads in `people_lib.sh`
and `e16.sh` now take either quote.
E21: producers.tsv gained five lines the lead's TRUST row produces (`open other`, the three `pick:` lines of the fix
build and the two granted lines); notrun.tsv has `link …: refused (not allowed)` (PeopleWriterTest).
Runner now: `qapeople-batch.sh` (scratch) — one hold of the real lock for a short batch; an `edge:<ids>` item runs
`EDGE_ONLY=<ids> edge.sh` and keeps the run as `EDGE-people-run<k>-…`.
| EDGE P01–P10 | the one grouped run: 181 / 1 / 21 — P01…P09 0 failed; P10's one failure was the driver's (it removed the photo of the contact NOT on screen). P10 fixed and re-run alone: 58 / 0 / 4 | `EDGE-people-run1-P01-to-P10-181-1-21/`, `EDGE-people-run2-P10-58-0-4/` | each sub-step once |
| E12 | run 1: 55 / 3 / 12 (driver: three reads — Telecom's foreground-call form, one mailto: handler on the image so no resolver page, OsmAnd's trampoline; and it left the kept-up call up, ended by hand under the lock at 00:04); run 2: 63 / 0 / 14 on 3c1ad1e0. The emulator stayed up through the OsmAnd map action both times | `E12/`; `E12-run1-driver-three-reads-and-a-call-left-up/` | 1 |

### 00:19 — the SECOND fix build is installed: md5 6009c0b14a87039f (commit dacca941; `fixbuild2/apk.txt`)
It carries D-E16-1's fix. Rows that passed on 3c1ad1e0 stand (E10, E13, E14, E15, E20, E25, E27, E28, E11, E12, EDGE).
On it, once: E16 (running 00:21), then the E21 check.
| E16 | on 6009c0b1: run 3: 42 / 2 / 7 (driver: misread dumpsys window's ime line); run 4: 44 / 0 / 7 — D-E16-1 fixed (`people_empty` reads `No contacts match "Wren".` at y 398–452, the keyboard's top at y 1422) | `E16/`; `E16-run3-driver-misread-the-keyboard-line/` | 1 |

## DONE 00:38 (2026-10-02). Final state of the People rows — one passing run each, as the owner's ruling asks
| row | counted run | build | folder |
|---|---|---|---|
| E10 | 129 / 0 / 2 | 3c1ad1e0 | `E10/` |
| E11 | 87 / 0 / 6 | 3c1ad1e0 | `E11/` |
| E12 | 63 / 0 / 14 | 3c1ad1e0 | `E12/` |
| E13 | 61 / 0 / 3 | 3c1ad1e0 | `E13/` |
| E14 | 37 / 0 / 0 | 3c1ad1e0 | `E14/` |
| E15 | 47 / 0 / 8 | 3c1ad1e0 | `E15/` |
| E16 | 44 / 0 / 7 | 6009c0b1 | `E16/` |
| E20 | 425 / 0 / 103 | 3c1ad1e0 | `E20-run1-pass-425-0-103/` (and a second, identical, in `E20/`) |
| E25 | 58 / 0 / 0 | 686506a7 by the ruling (`E25-build-686506a7-run1-pass-58-0-0/`); also 58 / 0 / 0 on 3c1ad1e0 in `E25/` | |
| E27 | 52 / 0 / 4 | 3c1ad1e0 | `E27/` |
| E28 | 118 / 0 / 5 | 3c1ad1e0 | `E28/` |
| EDGE P01–P09 | in the grouped run, 181 / 1 / 21 (the 1 is P10's) | 3c1ad1e0 | `EDGE-people-run1-P01-to-P10-181-1-21/` |
| EDGE P10 | 58 / 0 / 4, alone | 3c1ad1e0 | `EDGE-people-run2-P10-58-0-4/` |

Defects: `defects/D-E20-1.md` (fixed in 3c1ad1e0, re-run passes), `defects/D-E16-1.md` (fixed in 6009c0b1, re-run passes).
E21: every People line of `E21/producers.tsv` (40) is found in a kept slice of the two fix builds (checked offline
with e21.sh's own folder rule at 00:30); `E21/notrun.tsv` holds two People lines.
Device at 00:38: build 6009c0b1; user 0 only; raw contacts Mom and the nameless raw; no group, no aggregation
exception, icc/adn empty; READ / WRITE_CONTACTS granted; people_edit.json and people_filter.json absent; baseline
layout; no call; awake on Start. The default input method is the image's LatinIME (every force-stop deselects the
shell's keyboard; not put back by me).
Nothing of mine is left to run.

### 01:36 (2026-10-02) — the third build (md5 2150eba004db6757): ONE narrow run, on the lead's word
`E20_LEGS=plus-glyph bash scripts/e20.sh` (a new mode of e20.sh: only the name-only contact's editor and its "+ field"
rows): 40 / 0 / 19, kept as `E20-legs-plus-glyph-40-0-19/`. The "+" glyph's ink left edge is 12.00 epx for "+ Phone"
and "+ Email" (14.00 in the counted run); each label's ink left edge is 37.33 epx, equal to the counted run's
(read from `E20-run1-pass-425-0-103/editor-nameonly.png` by the same reader), asserted ± 0.34 epx. "+ Phone" →
"+ Email" 44.00 epx, across the rule 62.00. The counted whole-row E20 run stays `E20-run1-pass-425-0-103/`; the extra
second run on 3c1ad1e0 is now `E20-run2-pass-425-0-103/`. Nothing else of the People rows is re-run.

### 02:55 (2026-10-02) — gate round 1: driver work (no device), then the fourth build's narrow legs
Driver changes, on the lead's list (a–e) and its two addenda:
- **E13** `E13_LEGS=create` (New → name + number → Save on the phone: the `ok` line, NULL / NULL, exactly one raw
  contact added, absent_in for any `write delete` in a slice from a MARK before Save, read 9 s after) and
  `E13_LEGS=grant` (Contacts revoked → People's notice → the grant in place → `[app] feeds started (people grant)`; a
  photo given afterwards reaches the People tile; a photo removed over adb leaves it; one pid throughout). The grant leg
  lives in E13 (E18 is the lead's row).
- **E16** `E16_LEGS=negative` (the row without its A–Z walk); the switch is now turned OFF again and `(+1 enterprise)`
  asserted to return; "no people_card_link on the work card" is an assertion (was a RECORD).
- **E27** `E27_LEGS=create` (a group's create alone: `group create <id>: ok`, NULL / NULL, exactly one group added, no
  delete after). **E15** `E15_LEGS=sim` (two SIM entries → `sim import: 2 of 2`, both in the phone's account, one
  `write insert … ok` each, no `write delete`).
- Reviewer B note 12: `e28.sh` asserts the JVM result's tests count ≥ 1 and equal to the file's `<testcase>` count
  (`jvm_ran`, both test classes); `e15.sh` anchors the imported copy to raw ids above the highest before the import and
  asserts no other live "Ann Lee" before it; EDGE P04's three absences assert their page first; `e15.sh`'s record and
  the clauses-open E15 APK line say 5 MB / code exempt / `scripts/e15_apk.sh`.
- B20.1 (Share with no receiver): NOT produced and under no JVM test — `edge_index.tsv` says so and
  `clauses-open.tsv` has an `EDGE P09` line with why (six packages to disable on the shared AVD, one of them
  Bluetooth's, one the SMS role holder) and what is asserted instead. The lead rules.
- `E21/notrun.tsv`: eleven People lines added for the take-back and cloud-default forms (b7c45a56), each with
  PeopleWriterTest's path and case; two are marked NOT COVERED ("whether it was taken back is not known", "no id to
  take back": no case reaches them).
The lock: my runner's blocking hold of the real lock was refused by the session's safety check at 02:53 (it reads as
interfering with the other writer's runs). The legs now run through each driver's own lock (lib.sh, non-blocking), after
waiting for no driver to be running: `qapeople-legs-wait.sh` → `qapeople-legs2.sh`.
03:18 — the lead: a FIFTH build went on at 03:17:18 (md5 e03a1d233ee6ba0c, commit ac4a972d; one Calendar Day-view
constant, nothing of People's). No leg of mine ran on the fourth build (the device was in use each time my drivers
asked for it: rc 3, nothing run); the five legs (E13 create, E27 create, E15 sim, E16 negative, E13 grant) wait for
the device and will name e03a1d23.
03:19 — the lead's two answers: (1) the two NOT COVERED lines of `E21/notrun.tsv` now name their cases
(`aTakeBackWhoseOutcomeCannotBeReadSaysItIsNotKnown`, `anInsertThatReturnsNoRealIdIsNotFollowedByADelete`; commit
100b7d8e; both are in the result file, 52 tests, 0 failures); (2) B20.1 is ruled an open clause as written — the
clauses-open line and the index note stay, nothing to run.

### 04:20 (2026-10-02) — the fifth build (md5 e03a1d233ee6ba0c): five narrow legs, each run once
Run through each driver's own lock (`qapeople-legs2.sh`), after the Calendar writer's queue drained at 04:00.
| leg | passed / failed / recorded | folder |
|---|---|---|
| E13 create | 30 / 0 / 2 | `E13-legs-create-on-e03a1d23-30-0-2/` |
| E27 create (a group) | 21 / 0 / 1 | `E27-legs-create-on-e03a1d23-21-0-1/` |
| E15 sim | 29 / 0 / 1 | `E15-legs-sim-on-e03a1d23-29-0-1/` |
| E16 negative, with the switch back off | 48 / 0 / 7 | `E16-legs-negative-on-e03a1d23-48-0-7/` |
| E13 grant | **21 / 24 / 2 — a PRODUCT defect** | `E13-legs-grant-run1-on-e03a1d23-FAIL-people-crashes-with-no-contacts-permission-21-24-2/` |

**D-E13-1** (`defects/D-E13-1.md`): People crashes when opened with neither READ_CONTACTS nor WRITE_CONTACTS held
(`SecurityException … requires android.permission.READ_CONTACTS or android.permission.WRITE_CONTACTS` at
`PeopleData.observe(PeopleData.kt:43)` ← `PeopleApp.kt:265`). The leg's 24 failures are that one, seen again at each
later step. `[app] feeds started (people grant)` was NOT seen: the notice was never drawn. The driver went on after the
first crash and opened People a second time; the second crash cost the shell its place as the home app (Android's
"Select a Home app" page). Put back by hand at 04:14 with provision.sh's command
(`cmd package set-home-activity app.tileshell/app.tileshell.StartActivity`). The driver now stops the leg at the first
crash and asserts the Home intent in its restore; it also takes `E13_GRANT_REVOKE=read` (READ alone revoked — the
state in which People does open on its notice). Neither is run.
Device at 04:15: build e03a1d23; Start resumed, the Home intent resolves to the shell; user 0 only; raw contacts Mom
and the nameless raw; no group; icc/adn empty; READ / WRITE_CONTACTS granted; no pushed JPEG; awake; LatinIME.
Driver blobs: the E27, E15 and E16 legs' headers name the drivers as they stand (4d43787d, 0ec917d4, a34a575d;
people_lib.sh 7dd997df). The two E13 legs ran `e13.sh` blob 77b5008f; the file was edited AFTER both runs (the grant
leg only: it stops at a People crash, checks the Home intent in its restore, takes E13_GRANT_REVOKE) and is now
1a5c89e0 — `leg_create` is unchanged by that edit, but the blob in the create leg's header is not the file as it stands.

### 04:23 (2026-10-02) — the sixth build (md5 585b457ffc29878c, commit e226bc68): the grant leg, once
`E13_LEGS=grant` (both revoked): **49 / 0 / 4**, kept as `E13-legs-grant-on-585b457f-49-0-4/`. People opens on its
cannot-read notice, no new crash-drop-box entry (2 before, 2 after), one pid (2891) throughout, Android's dialog
tapped, `[app] feeds started (people grant)` in the slice, the photo reaches the PEOPLE tile and leaves it again in the
same process. D-E13-1's fix result is appended to `defects/D-E13-1.md`. `e13.sh` gained the crash-drop-box checks
before this run (blob f096c31b, the file as it stands). `E13_GRANT_REVOKE=read` was not run, on the lead's word.
Open for the lead: `[people] observer not registered: <kind>` (new in e226bc68) has no producer on a device and no
JVM test names it; it is in neither E21 table.
Device after the run: the leg's restore passed (permissions, fixtures, JPEG, raw_contacts, layout, Home intent).
