# Phase 16 — round 3 (the last) triage, 2026-09-30

Reviewers: opus (design) + opus (testability); codex out until 2026-10-03 16:48; never Fable. Brief:
2026-09-30-phase16-r3-brief.md. Reports: 2026-09-30-phase16-r3-design.md (BLOCKING 2 · SHOULD-FIX 8 · NOTE 5),
2026-09-30-phase16-r3-testability.md (BLOCKING 3 · SHOULD-FIX 16 · NOTE 2). Round 3 is the cap: everything is either
applied to the doc as an agent fix or put to Jeremy; nothing goes to a round 4.

## Questions for Jeremy (Stage A shape, one at a time; answers land here, dated, and in the doc's Decisions)

| # | from | question | status |
|---|---|---|---|
| Q-16-1 | D9 | On the update that brings the shell's own Calendar and People: a CALENDAR / PEOPLE slot already pointed at an app by hand — is that pick kept (the doc's guard, an agent decision of 2026-09-22), or does the new app take the slot once | ANSWERED 2026-09-30: "(A)" — the shell's Calendar and People take their slots ONCE on this update (as Music took its slot); from then on a pick made by hand is always kept, and the slot can be pointed back in Settings > Tile apps. Rejected: B (keep the earlier pick, a phone row to re-point), C (a prompt on first Start). The 2026-09-22 agent decision (the guard keeps an existing pick on this update) is replaced for these two markers; the guard stays for every later seed |
| Q-16-2 | D2 | A local event that has been synced to an account calendar exists twice in the provider (the Tessera event and its copy): which one do the views, the tile, the Agenda pod, Tess and the reminders show | ANSWERED 2026-09-30: "(A)" — the original (Tessera) event is shown and its copy is hidden everywhere in the shell while the original exists: the Calendar views, `CalendarFeed` (tile and agenda), Tess's calendar reads and the reminder receiver skip an event id `calendar_sync.json` maps as a copy; the event's page says "synced to <calendar>". One event, one reminder. Rejected: B (show the copy, hide the original), C (both, with a marker) |
| Q-16-3 | D13 | Does "never work" reach People: are contacts of a work account editable / deletable in People | ANSWERED 2026-09-30: "(A)" — the Calendar model: People edits, deletes and adds contacts only in accounts on a "Can edit" list in People's settings (nothing allowed by default); phone-only (null-account) contacts are always editable; every other account, work included, is read-only until allowed. Rejected: B (edit any account, the draft), C (never write to any account). Agent consequences, written into the doc: one write guard for ContactsContract as for the calendar; a new contact goes to the phone unless an allowed account is chosen in the editor; Link / Unlink (`AggregationExceptions`, local to the phone) stay allowed; a column that syncs upstream (e.g. STARRED) counts as a write; a negative row and a JVM guard test |

## Agent fixes (accepted; applied to the doc 2026-09-30 after the three answers, in one edit)

Design: **D1** (BLOCKING) Tess's delete goes through the write guard — the match is limited to the Tessera calendar, a
title found only elsewhere gets a refusal line (an approximation, its own accept row); E22 and the JVM guard test gain the
Tess-delete case; Change Log line for phase 03. **D3** the slot picker and resolver match the handler's component, tag
`slot_candidate:<component>`, E1 asserts two candidates. **D4** show/hide lives in the shell's own store, never
`Calendars.VISIBLE`; E22 asserts the column unchanged. **D5** "Can sync to" lists only calendars the phone may write
(`calendar_access_level` ≥ 500), re-read at push, rows grouped under the account name. **D6** the reminder receiver:
the data filter, exported and on the allow-list, the broadcast as a poke with the alerts re-read and de-duplicated, the
alert-state write named as the guard's fourth case. **D7** the Calendar app offers WRITE_CALENDAR in place; `LocalCalendar`
creates only after a successful empty query; E18 gains the WRITE-only-revoked clause. **D8** the Agenda pod opens the seeded
CALENDAR slot app; phase 14 E3's precondition is re-cut at this phase's build (Change Log then); depends-on gains 14.
**D10** a trust list for the adversarial review (WRITE_CONTACTS, the exported VIEW / EDIT / INSERT / PICK handlers, the
receiver, the Sync path and its file, the vCard stream, the reminder notification over the lock screen). **D11** one
activity per app, helpers non-launcher (Back on Start's rule since L14-2). **D12** the feeds also publish under the app's
component key, so a pinned app tile is live (agent call, consistent with phase 15's routing fix). **D14** the Birthdays
writer runs from `ShellApp.startFeeds`; the no-year and 29 February forms. **D15** the stale cites and the two wording fixes.

Testability: **V1** (BLOCKING) E26 re-cut to phase 12's wizard as built (a MISSING row summons it, a PARTIAL one does
not), its own driver. **V2** (BLOCKING) E1's wiped leg asserts the layout file; the `-> assigned` line is asserted in the
upgrade leg only. **V3** (BLOCKING) P2 is done on the phone alone: counts from a diagnostics line read in Settings, the
network clause re-cut to what the phone shows. **V4** no backwards clock jump in E17. **V5** = D8's re-cut, as an E1
clause. **V6** E21 in phase 15 E22's form with producers / not-run lists, and an EDGE row with an index of every Edge-case
bullet; three bullets become phone rows or a JVM test. **V7** E6's two clauses re-cut (AOSP Calendar disabled for the
alert-state clause; the shell's pending-alarm count before and after). **V8** the four anchor tags and `absent_in`.
**V9** the preamble on today's floor (`ensure_start`, `gdump`, `jump_clock`), `p16.sh` built by task 8. **V10** a restore
for every row, fixture helpers, no row depending on another. **V11** every count first asserts the calendar still lists;
the "Offsite" sentinel kept. **V12** MISSING when READ is not held whatever WRITE is. **V13** Sync compares the copy with
the local event and writes when they differ. **V14** the photo proof by fixture colour; People's pivot motion in E20.
**V15** fixture preconditions asserted from the provider. **V16** the share line and `run-as` read; the SIM import always
run. **V17** TestDPC owned by task 8, installed into the profile inside the row only. **V18** the exported-intent
negatives in E22. **V19** H13 split into named accept rows. **V20 / V21** the typed forms and the smaller row fixes.

Verify at build start (reviewers' API facts from memory): the reminder broadcast's data URI form (D6); whether the provider
gates an app insert on the calendar's access level (D5); whether CalendarProvider drops non-LOCAL calendars with no account
when its process restarts (V11).

## Applied 2026-09-30 (opus doc writer; only phase-16-inbox-calendar-people.md was edited: 974 → 1702 lines)

Every ruling and every accepted fix is in the doc (its hand-back lists each id and where). Decisions the writer made
itself, for Jeremy to overrule at FINAL or at the build:
- Q-16-1: the take-over is built as a `takeOver` argument only the two markers pass; the line naming the replaced app is
  in the in-memory ring only (no persistence — the replaced app stays in the slot's picker). The guard keeps a device proof
  on `slot:music:v1` (a new "guard leg" in E1) plus a JVM test.
- Q-16-3: a contact made of editable and read-only raw contacts gets Edit for its editable part and no Delete (H21); the
  "Can edit" list is the distinct accounts in `raw_contacts`, stored in `people_edit.json` (no AccountManager read);
  "phone-only" is compared against `RawContacts.getLocalAccountName/Type` rather than a literal null, because Samsung may
  name its local account (from memory: Verify item 4, phone row P8).
- D14: 29 February is written as the last day of February in other years (from memory: Verify item 5, asserted in E17).
- D1: Tess deleting a synced event deletes "here" only.
- V16 against D10: no shell file for the vCard share, so the row reads the logged Contacts-provider vCard URI.
- E6 gains a forged-poke leg so D6's "the broadcast is a poke only" can fail.
- Not edited (one-file limit), owed at phase 16's build: `build-prompt.md:21`'s trust list, phase 14's E3 precondition
  and driver (Change Log then).
- "Verify at build start" in the doc holds five items: the triage's three and the writer's two.
