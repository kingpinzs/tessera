# Phase 16 gate review — Reviewer A (design and correctness), round 1 (2026-10-02)

Saved by the lead from the reviewer's hand-back (an independent Opus agent, read-only; brief
`2026-10-01-phase16-gate-brief.md`). `…/app` = `app/src/main/kotlin/app/tileshell`, `…/qa` = `docs/plan/qa/phase-16`.
The reviewer read the code and the logs only; nothing was run, written or changed.

LENS A

## Findings

**1. BLOCKING — the tile and Tess show all-day events (so every birthday) on the wrong day, and E17's re-cut hides it.**
- What is wrong: both read `Instances` over now … now + 24 h, and an all-day instance is its UTC day. In MDT, today's
  birthday leaves the tile and Tess at 18:00 local and tomorrow's appears as today's.
- Where: `…/app/feeds/CalendarFeed.kt:100-108` (tile); `…/app/calendar/TessCalendar.kt:30-40`, called from
  `…/app/cortana/action/ActionLayer.kt:554-555` (Tess).
- Evidence: `…/qa/E17/E17.txt:21-22` records the fixture birthday as 2026-10-02 against a local date of 2026-10-01 at
  22:44 MDT. Lines 42-43 and 48-49 then pass "the tile shows it today" and Tess's "You have Ann Lee's birthday."
- Why it is a miss, not a reading: the doc's E17 uses the device's own today, and Q3 says the tile and Tess see
  birthdays. `…/qa/clauses-open.tsv:15` calls it "a product question for the owner", but `NEEDS-HUMAN.md` never asks it.
- Fix: give the tile query and `TessCalendar.events` the pod's rule (`AgendaRules.window` / `dayOf`,
  `CalendarFeed.kt:167-192`): range from the local start of today, an all-day instance by its UTC date.
- Device proof: E17 leg B alone, with the doc's fixture (the local today's month-day), run after 18:00 local.
- Alternative: the owner rules it as-is, with a NEEDS-HUMAN row saying so.

**2. BLOCKING — the Agenda throws the list back to the selected day each time it loads more weeks.**
- What is wrong: reaching the end of the loaded window widens `to`; `loaded` goes null until the new window arrives; the
  list collapses and the scroll effect fires again. A user cannot scroll past week 8 without being returned to the top
  three times (8 → 16 → 32 → 56 weeks).
- Where: `…/app/calendar/CalendarViews.kt:117-118` (`loaded` nulled), `:165` and `:259-264` (the "more" item),
  `:246-249` (the effect keyed on `instances != null`).
- Evidence, in the row's own run: `…/qa/E5/D-agenda.tsv.dumps` shows dump 3 at 10-15 … 11-12, then dump 4 back at
  10-02. The same happens at dumps 8 and 13. The window doublings are at `…/qa/E5/ring-launcher.txt:125-127`. E5 passed
  because the walk unions its sightings.
- Doc basis: r11/calendar.md K3.1 (HIGH), "a scrolling list of day groups".
- Not caused by F38; the per-event items kept the same effect.
- Fix: keep the last window's instances while a wider one with the same `from` loads (`it.to <= to`), so `instances`
  never goes null on an extension.
- Device proof: E5 leg D alone, with one added assertion that the first date of each dump never goes backwards.

**3. BLOCKING — a trust proof the ledger assigns to the owner is not asked of him.**
- What is wrong: F25 is closed as "PHONE ROW P8", but P8 has no step that makes a new contact. Whether a "Phone" save on
  Android 16 with a cloud default account lands in an un-ticked account or is refused stays unproven.
- Where: `…/qa/fix-round.md:45` (F25); `docs/plan/review/2026-10-01-phase16-trust-a-write-paths.md:187` (the reviewer's
  ask); `…/qa/NEEDS-HUMAN.md:51` (P8).
- Code side: `PeopleWrites.create` does not read back where the row landed (`…/app/people/PeopleWrites.kt:97-114`); only
  the photo path re-reads the account.
- Fix: add to P8 "New → a name → Save with 'Save to' reading Phone; then read its storage in Samsung Contacts; pass =
  Phone, or People said it could not save". No build, no row.
- Hardening (optional): after the insert, re-read the raw contact's account and fail the save aloud when it is not the
  one asked for; one `PeopleWriterTest` case.

**4. NOTE — E7's measure (not counted as blocking).**
- The empty-calendar comparison is a sound measure of what the events add: about 3.1 and 3.5 points on two builds,
  almost all one settle frame per swipe.
- It is not a clearance of the pager. 11 % on an empty calendar is the Calendar's own cost: phase 13's Start scroll
  reads 3.15 % on this AVD (`…/qa/defects/D-E7-1.md`). Record that as its own finding.
- "At most 5 points above baseline" has no source and would pass a 40 % worse result than today's.
- The "no frame over 100 ms" half does not cover the Day view. `DayView` composes every block of the day at once
  (`CalendarViews.kt:534-539`). `…/qa/E7-run2-per-leg-diagnosis-janky-14.38-percent/D-slice.txt` reads
  `view day 2026-12-15: 200 instances in 185 ms` (query plus first frame), and no leg takes frame stats there. One leg
  would show it: E7 leg D with gfxinfo reset.
- Remaining per-swipe cost: the neighbour page is composed on the first drag frame (`:447-449`) and drawn without
  events (`:168-173`), so events pop in after each swipe. Record it and name it in H15 / H17 rather than fix it now.

**5. NOTE — F38: right fix, right place; two residual risks its leg does not catch.**
- A repeated (event id, begin) within one day would now crash the list on a duplicate key (`CalendarViews.kt:254`). The
  provider's uniqueness makes it unlikely; adding the index to the key removes it.
- Event-row geometry (K3.5–K3.7) was last measured before the change; E19 leg 1 on the gate build covers only the empty
  day. By reading, the heights are identical. E19 leg 2 alone would prove it.
- Existing before F38: while a window loads, today's head reads "No events today" even when today has events (`:253`,
  `:354`).

**6. NOTE — F35–F37 (glyph bearings): right place.**
- The constants (`CalendarWidgets.kt:137-140`, `PeopleEditorPage.kt:529-540`) are the bearings of R11's sample glyphs.
  `PANE_NAME_BEARING` also moves the "Can sync to" and Sync picker rows, which share `CalendarRow` (`:429`). No
  functional risk found.

**7. NOTE — F32 fixed the symptom, not the cause.**
- People's pages do not end at the keyboard: `PeopleActivity` has no `adjustResize` (`AndroidManifest.xml:579-587`;
  Calendar has it at `:509`). The app bar and `people_notice` (`…/app/people/PeopleWidgets.kt:209-213`) still sit behind
  the keyboard in the contact editor, the group editor and pick-mode search. Name it in H2 or fix it.

**8. NOTE — on a phone whose maker names its local account, "Phone" labels will read the raw account name.**
- `CardRules.accountName` says "Phone" only for a null name (`…/app/people/PeopleModel.kt:261`); the editor header,
  "Save to", the card's account line and the filter rows all use it. Not provable on the AVD (its local account is
  NULL / NULL).
- Fix: return "Phone" when `policy.isPhone(account)`; add "read the Save-to label" to P8.

**9. NOTE — "Delete here and from <calendar>" on a changed occurrence of a synced series deletes here only.**
- The mapping is looked up by the exception's id, not its master's (`…/app/calendar/CalendarPages.kt:169-176`,
  `…/app/calendar/CalendarSync.kt:211-212`).
- Fix: do not offer "both" on an exception row, or look up `originalId`.

**10. NOTE — a grant made in place does not start the feeds' observers.**
- `PeopleFeed`, `BirthdaysWriter` and `CalendarFeed` register observers only from `ShellApp.startFeeds`
  (`…/app/ShellApp.kt:217-228`), which runs at process start and on the Setup checklist's resume. The in-place grants
  (`…/app/people/PeopleApp.kt:278-286`, `…/app/calendar/CalendarActivity.kt:71-80`) do not call it.
- Fix: call `startFeeds("grant")` from both callbacks.

**11. NOTE — NEEDS-HUMAN.md gaps.**
- P9 says "web or another device" (`:52`), and P2 / P8 say "other client"; name an app or the browser on the phone.
- E4's question (a MEDIUM pinned Calendar tile shows no event text, `clauses-open.tsv:3`) is not asked.
- H14 omits that Sync on an event whose calendar is gone can never be pointed elsewhere (`CalendarPages.kt:244-245`).
- H21 omits that an account with no contacts cannot be listed on "Can edit" and loses its tick when its last contact
  is purged (`…/app/people/PeopleData.kt:90-108`).
- The header still says DRAFT (`:3`).

**12. NOTE — smaller.**
- F34's strip needs the article: "add calendar event called X" is still titled with the phrase
  (`…/app/cortana/match/CommandMatcher.kt:190`).
- The `[people] action text | call` line names the slot's app even when the intent went to the role holder
  (`…/app/people/PeopleActions.kt:43,57`).

## Checked and sound

- **Calendar writes:** the resolver is touched only at `…/app/calendar/CalendarProvider.kt:96-101`, called only from
  `CalendarWrites.kt`. Every function re-reads the calendar and asks the guard. `calendar_id` is refused in any UPDATE;
  sync-adapter URIs always name the LOCAL account; the receiver may write alert STATE only; `Calendars.VISIBLE` is never
  written.
- **People writes:** the resolver is touched only in `PeopleWriter.kt`. `PeopleWrites` resolves raw contacts from the
  provider, asks the guard, then writes. Aggregate delete needs every raw contact editable; another profile is refused,
  Link included.
- **Sync:** `allowed` is written only by the "Can sync to" tick and the prune; the key carries the calendar's name; a
  refused re-target keeps the old mapping; a failed copy read is never "gone".
- **Exported surface:** an INSERT carries no calendar or account field; EDIT on a non-Tessera event or a read-only
  contact opens read-only; PICK needs a caller and returns one URI with flags 0x1; nothing a caller types reaches the
  ring.
- **Reminder receiver:** reads nothing from the intent, coalesces pokes, applies the Q-16-4 cut-off, skips copies, posts
  PRIVATE with a public version, uses immutable PendingIntents, and dismisses by four-part identity.
- **What the trust reviews asked for exists:** `CalendarWriteLayerTest`, `PeopleWriterTest`, E22 leg L, E28 leg X, the
  TRUST row.
- **Other rulings in code:** `LocalCalendar` creates only after an empty lookup; `SlotSeed` takes over for the two
  markers only; the Birthdays writer and the Q-16-2 readers are as specified; Tess's delete is Tessera-only.
- **Readings judged fair:** E1's "neither auto-assigns" (`clearSlot` has no caller); C1 `_sync_id` on Tessera series;
  C4; F31's rule reading; E9's typed re-runs (`Contacts.kt` untouched since phase 03); E6's (d) and (e) re-cuts; the
  E12, E15 and E27 re-cuts; the 29 February re-cut. The not-built pencil variant and jump-grid fade are honestly put to
  H2.

GATE: FAIL (3 blocking)
