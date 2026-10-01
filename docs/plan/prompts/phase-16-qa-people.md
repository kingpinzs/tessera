# Phase 16 QA brief — the People rows

Read `docs/plan/prompts/phase-16-qa-common.md` first: its rules bind you. Your scratch prefix is `qapeople-`; your
include file is `docs/plan/qa/phase-16/scripts/people_lib.sh`.

## Your rows (acceptance criteria in `docs/plan/phase-16-inbox-calendar-people.md`)
E10, E11, E12, E13, E14, E15, E16, E20, E25, E27, E28 — one driver each, `scripts/e<n>.sh`.

Order: E10, E13, E28 first (the list, the editor, the owner's write rule), then E14, E27, E15, E11, E20, E25, E16, and
E12 LAST (see its note).

Notes per row (they do not replace the row's text):
- **E11**: the three solid-colour photos are `docs/plan/qa/phase-16/fixtures/solid-{red,green,blue}.jpg`; each is given
  through People's editor and Android's photo picker and asserted as a `vnd.android.cursor.item/photo` data row before
  Start is read; Start never idles while the tile cycles — dump with `gdump`, read `[people] tile event` and `[motion]`
  lines from the ring; a pinned People app tile shows the same events (r3 D12 — the builder did not prove it); the
  no-photo leg. The pause between the two slides is 964 ms as built (Change Log 2026-10-01, P1): E11's clauses are the
  period, the two settles and the 1.88 s event.
- **E12** runs LAST and its map action goes last inside it. The builder's first run of the map action into OsmAnd saw
  the emulator process exit (at 10:44, while the host's disk was filling — the cause is not established). The row's
  text needs OsmAnd in the Maps slot. If the emulator dies at that step: stop all device work and tell the lead; do not
  relaunch it. The call clause: `adb emu gsm list` prints only `OK` on this image — `record` its output, and assert the
  call from `dumpsys telecom` (Change Log 2026-10-01); use a number the emulated network keeps up if the row's own
  number is dropped at once, and say so in `clauses-open.tsv`. `adb emu gsm cancel` / end the call in the restore. The
  resolver's "Always" step and its `pm clear-package-preferred-activities` + `set-home-activity` restore are clauses.
- **E13**: the photo's centre pixel on `people_card_photo`; the WRITE_CONTACTS-revoked leg with
  `checklist:people:partial` and Tess's `contacts` row still granted (`cortana_check:contacts:granted` on her settings
  page — `scripts/e26.sh` shows how it is reached).
- **E15**: `adb shell content read` of the vCard URI is refused on this image (Change Log 2026-10-01): assert the logged
  URI's form, `content query` on it, and the stream's content through an import into the image's Contacts app (the
  builder's `vcardprobe.sh` shows the way), deleting what the import made; put the clause in `clauses-open.tsv`. The SIM
  branch is decided by the host's `content query` and RECORDed; Import from SIM is run in both branches. The APK clause
  (size delta and entries ≥ 1 MB) is the lead's: leave it out and say so in the row's log with a `record`.
- **E16**: the work profile is made with phase 01 E18's commands (find them under `docs/plan/qa/phase-01/scripts/`),
  TestDPC from `docs/plan/qa/phase-16/fixtures/TestDPC_9.0.12.apk` installed INTO THE PROFILE ONLY and made its owner
  before the positive leg; the cross-profile contact-search switch is TestDPC's own, driven at its dump bounds (its
  label recorded); the profile removed at the end, `pm list users` showing user 0 only. If the image refuses a managed
  profile, record exactly what it said and tell the lead.
- **E20**: geometry on the drawn pixels where a dump's bounds are a touch target (a text field's tag node reads 48 epx
  in a dump; the box is measured in a screencap — the builder's `people.sh` `px` / `ink_color` and phase 15's `ink.py`
  show a way; write your checks from r11/people.md's values and the row's tolerances). The card and the editor were NOT
  measured by the builder. Where the row names something that is not built (the 34-epx pencil variant), assert what is
  drawn, and put the clause in `clauses-open.tsv`.
- **E25** covers BOTH apps' App Shortcuts: the tiles are held by phase 11 E3's method (find it under
  `docs/plan/qa/phase-11/scripts/`), every Start dump is `gdump`.
- **E28**: the BEFORE files and the "Wade's rows unchanged after every step" checks are the point of the row.
- A phone-only raw contact with no name (`_id=2`, older than this phase) is on the AVD and lists under "#": your counts
  must not assume an empty book — count against a BEFORE read.

## Your EDGE sub-steps (functions `edge_<ID>` in `people_lib.sh`; ids from `scripts/edge_index.tsv`)
P01 P02 P03 P04 P05 P06 P07 P08 P09 P10.

## Diagnostics alternatives that are yours for E21
Every `[people] …` alternative (list read / write true and false; search with and without enterprise matches; write
ok / failed; link ok / failed and the added `unlink`; sim import; tile photos / tile event; each group op ok and
failed; edit refused; share; each action), `[motion] people_bubble_out`, `people_bubble_in`, `people_pivot`.
`[people] write … refused (not allowed)` and `[people] tile: photo … skipped` belong in `notrun.tsv` with their JVM
tests (`PeopleWriteGuardTest`, `PeopleTileRulesTest`).
