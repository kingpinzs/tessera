# Phase 16 build brief — the People app and its tile (build tasks 5, 6 and 7, People's half of 9)

Read `docs/plan/prompts/phase-16-build-common.md` first: its rules bind you.

- Worktree: `/home/jeremyking/projects/metro-launcher-p16/.claude/worktrees/people`, branch `phase-16-people`, cut
  from `phase-16` at 9f137626 (build tasks 1 and 2 and the build-start checks are in).
- Scratch files (anything not for the repo): prefix them `people-` in the scratch directory you are given.
- The other builder (Calendar, tasks 3–4) works in `.claude/worktrees/cal`. You do not touch: the `calendar/` package,
  `feeds/CalendarFeed.kt`, `feeds/LocalCalendar.kt`, `cortana/action/ActionLayer.kt`, `res/values/calendar.xml`,
  `res/xml/shortcuts_calendar.xml`, Calendar's manifest elements. The Birthdays calendar (it reads contacts' birthdays)
  is the Calendar builder's. Files you both add a line to (`ShellApp.startFeeds`, `AndroidManifest.xml`): keep your edit
  to your own lines so the lead's merge is clean.

## Read (fully, before code)
1. `docs/plan/phase-16-inbox-calendar-people.md` — all of it (≈1,700 lines). Your clauses: Scope's People bullets, the
   People tile bullet, the Phase 06 hand-off and Manifest ADD bullets; the Decisions lines Q-16-3, Q1, "People over the
   provider", "Intent handlers and the chooser", "People tile", "Bars", "Fidelity", "Harness contracts", T16-13 (People
   half), T16-14, T16-15, C-5 (the motion clock), C-17, r3 D11, r3 D12, "the People write guard and the Can edit
   list" (all five points), "Trust" (b), (c), (f), "Harness, round 3"; Build tasks 5, 6, 7 and 9; the Acceptance
   preamble; rows E10–E16, E20, E21, EDGE, E25, E27, E28; H2, H3, H8–H11, H16, H17, H18, H20, H21; the People, "Can
   edit", non-Latin, WRITE_CONTACTS, SIM, Groups, Share and People-tile bullets of Edge cases.
2. `docs/plan/r11/people.md` — every measured value (P0–P5) and every UNMEASURED item. `docs/plan/w10m-measurements.md`
   §A9 (the People tile's bubble motion) and the C3 row for People ("circle pattern; photo bubbles as in A9").
3. `docs/plan/qa/phase-16/BUILDSTART/README.md` (item 4: a phone-only raw contact is NULL / NULL on the AVD).
4. Code you extend or reuse: `people/` (task 2's activity, nav, routes), `feeds/PhotosFeed.kt` (the form `PeopleFeed`
   follows: reads at tile size, re-reads on a provider change, never on a timer), `feeds/CalendarFeed.kt` (how a feed
   publishes a face), `tiles/engine/LiveTileEngine.kt` (:44-47, no PEOPLE key today), `tiles/engine/TileRouting.kt`
   (`componentKey`, `tileContentKey` :61-62), `start/StartPage.kt` (:160-164, :242-246 — the slot branches that name
   PHOTOS, CALENDAR and MUSIC only), `start/TileView.kt` and the tile-face code (`TileFace.CalendarDay` shows how a
   drawn face is added), `tiles/ActiveTiles.kt`, `tiles/SlotResolver.kt` (resolving the MAIL and MAPS slots),
   `cortana/action/ActionLayer.kt:300-312` (how the shell places a call with `TelecomManager.placeCall`; read only),
   `cortana/action/Contacts.kt` (Tess's `byName` — you change nothing in it), `ui/components/OutlinedField.kt`,
   `ui/components/ModalOverlay.kt`, `ui/MotionClock.kt`, `clock/ClockWidgets.kt`, `clock/ClockTabs.kt`,
   `music/MusicCollectionPage.kt` (phase 10's pivot header, P4 design, `MusicMetrics`), `applist/` (phase 01's jump grid
   — People has its OWN grid, r11/people.md §3; do not reuse phase 01's `AppIndex`), `settings/SettingsWidgets.kt`.

## Build (each clause as its Decisions line specifies it; commit after each lettered part is verified)

### A. People data layer (build task 5)
1. **Reads:** the A–Z list from the provider's `SORT_KEY_PRIMARY` and `PHONEBOOK_LABEL` (non-Latin names file where
   Android files them); a contact with no name shows its number or e-mail under "#"; search by name and by number (the
   provider's phone lookup, so a number typed without spaces matches one stored with them) plus the enterprise filter
   (`ENTERPRISE_CONTENT_FILTER_URI`) for work-profile matches; the card's data kinds (numbers with type labels, e-mails,
   postal addresses, birthday, notes, organisation, photo); raw contacts behind an aggregate with their accounts;
   "filter contact list" by account / group; a `ContentObserver` so pages follow the provider. Lines: `[people] list:
   n contacts read=<bool> write=<bool>`, `[people] search "<q>": n (+m enterprise)`.
2. **The People write guard and "Can edit" (Q-16-3; the five points of that Decisions line; the Trust line (b)).** A
   pure rule (no Android types): editable is decided per raw contact — a phone-only raw contact always (its account
   compared against what `ContactsContract.RawContacts.getLocalAccountName` / `getLocalAccountType` return, never a
   literal null), a raw contact whose account is on the "Can edit" list, nothing else; another profile's contact never.
   `people_edit.json` in `filesDir` (temp-file-and-rename) holds the allowed accounts keyed on account name + type; an
   account the provider no longer names is dropped from it. The list's rows are the distinct non-null account name +
   type pairs of `raw_contacts`, read through the provider (no `GET_ACCOUNTS`, no AccountManager). EVERY
   `ContactsContract` insert, update and delete People makes goes through ONE write layer that resolves the raw
   contacts the op touches and asks the rule first: a new contact goes to the phone unless the editor's account choice
   names an allowed account; a SIM import goes to the phone; an INSERT prefill starts on the phone; data rows (fields,
   photo, group membership) only on an editable raw contact; deleting a contact and updating a `Contacts` column that
   syncs upstream (STARRED) only when EVERY raw contact behind the aggregate is editable; Link / Unlink
   (`AggregationExceptions`) allowed on any contact; groups by the group's account. Lines: `[people] write <op>
   raw=<id>: ok | failed <err> | refused (not allowed)`, `[people] edit <lookup>: refused (account not allowed)`.
   **JVM test** of the rule with every allowed case and every refusal point (5) of the Decisions line lists.
3. **Writes:** create / edit / delete; the photo (from Android's photo picker, `MediaStore.ACTION_PICK_IMAGES`, stored
   as a photo data row); Link (`TYPE_KEEP_TOGETHER`) and Unlink (`TYPE_KEEP_SEPARATE`) with `[people] link <a>+<b>: ok |
   failed`; editing a field of an aggregated contact writes to ITS raw contact and never splits the aggregate.
4. **SIM import:** read `content://icc/adn`, import into the phone's contacts, `[people] sim import: n of m` (also `0
   of 0` when the SIM lists nothing); a SIM contact with no number is skipped; no crash if the SIM goes away.
5. **Share:** `ACTION_SEND` `text/x-vcard` whose stream is the Contacts provider's own vCard URI
   (`ContactsContract.Contacts.CONTENT_VCARD_URI` + the lookup key) with a read grant for that one URI — no writer,
   provider or file of the shell's — and `[people] share <lookup>: <uri>`.
6. **Groups (T16-14):** create / rename / delete through `ContactsContract.Groups`, membership through
   `GroupMembership` data rows, under the same account rule (a new group goes to the phone unless an allowed account is
   chosen; a group in an account not on "Can edit" is read-only); "Text the group" = `ACTION_SENDTO smsto:<n1>;<n2>…`
   (each member's first mobile number) to the SMS role holder. `[people] group <create|rename|delete> <id>: ok | failed <err>`.

### B. People app (build task 6) — every value from r11/people.md with its tolerance
The CONTACTS / GROUPS pivot header (less What's New) with `[motion] people_pivot` (250 ms, X13) through
`MotionClock.animate`; the list (50-epx rows, 32-epx circular avatar at x 12 — a grey disc with the initial without a
photo — the name at x 57.75, accent letter headers); People's own jump grid (72-epx cells, 4 columns, accent letters
where contacts exist, "#" first and a globe last, a full page over the list); the search box; the full-accent contact
card (name in caps, the 124-epx photo, titled action rows — Call through `TelecomManager.placeCall`, Text
`ACTION_SENDTO smsto:` to the SMS role holder, Mail `ACTION_SENDTO mailto:` to the Mail slot app when assigned else
Android's chooser, Address `geo:0,0?q=` to the Maps slot app — each logging `[people] action <call|text|mail|map> ->
<component> <uri>`); the card's Edit and Delete only where the guard allows, the `people_card_readonly` line ("This
contact is in <account>. To change it, allow that account in Can edit.") whose tap opens "Can edit", the mixed contact
(Edit for the editable part, no Delete); the editor (`OutlinedField`, accent type-labels with ⌄, "+ field" rows, group
rules, the header "EDIT <ACCOUNT> CONTACT", `people_editor_account` offering "Phone" and the allowed accounts only, the
photo picker, the cannot-save notice with WRITE_CONTACTS offered in place — phase 10 E18's form); link / unlink pages;
SIM import; "filter contact list"; People's settings page with "Can edit" (`people_page:can_edit`, a checkbox list);
the GROUPS pivot — group rows, a group's page, create / rename / delete, the member picker, "Text the group" and its
no-member / no-mobile notices; `people_notice` (a node that stays until the page changes, never a toast) for every
notice; the cannot-read state with the grant offered in place; the work-profile search result with the briefcase glyph
and an enterprise marker in its tag, always read-only. Routes from task 2's `PeopleNav.route`: Open (the list, or the
shortcut page: `new_contact` = the editor, `groups` = the GROUPS pivot), Card, Edit (the editor only where the guard
allows; else the card and the refused line), Insert (the editor prefilled on the phone, unsaved), InsertOrEdit (the
list, to choose "new contact" or an existing editable contact the fields are added to), Pick (the list in pick mode:
the result is the one contact lookup URI or phone data URI tapped, `setResult` with a read grant for that URI alone,
then finish; Back cancels). Every tag in "Harness contracts" and "Harness, round 3" that begins `people_`. The Android
profile ("Me") is not shown.

### C. People tile (build task 7; touches phase 01's part)
`feeds/PeopleFeed` started from `ShellApp.startFeeds`: with ≥ 1 contact photo, R3 A9's bubble event drawn in-house — a
photo bubble slides out left in ≈333 ms, a ≈6-frame pause, a new bubble slides in from the right settling in ≈583 ms,
the event 1.88 s, repeating every 7.7 s — cycling contacts that have photos; with none, the static circle pattern.
Photos read at tile size, re-read on a provider change, never on a timer. ADD `LiveTileEngine.PEOPLE` and the
`Slot.PEOPLE` branches in `StartPage` (r3 D12); publish the face under the PEOPLE slot key AND under
`TileRouting.componentKey` of `.people.PeopleActivity`, so a pinned People app tile is live too (one publisher, two
keys). Lines: `[people] tile: n photos`, `[people] tile event <n> t0=<uptime> lookup=<key>` per event, `[motion]
people_bubble_out` / `people_bubble_in` with `settle=`, `frames=` and `maxGapMs=` (the `MotionTrace` form), `[people]
tile: photo <lookup> skipped: <why>` for a photo that fails to decode — the skip decision is a pure function with a
JVM test (no fixture can store an undecodable photo). The tile's face scales per phase 01's tile rules at small /
medium / wide. The Music-routing rule of phase 15 must still hold: nothing package-keyed lands on this tile (E1's Music
negative).

### D. People's App Shortcuts (build task 9, People's half)
`res/xml/shortcuts_people.xml` — ids `contacts`, `new_contact`, `groups` in that rank order (labels Contacts, New
contact, Groups), each targeting `.people.PeopleActivity` with the `page` extra — and its `<meta-data>` on the
activity, in the form of `res/xml/shortcuts_calculator.xml`. Strings in `res/values/people.xml`.

## Reuse
`OutlinedField` for the editor; phase 10's pivot header form; `ClockWidgets` for the app bar, flyout, checkbox and
press feedback where r11/people.md's values match them (where r11 measured something else, build to r11);
`PhotosFeed`'s decode-at-size and observer pattern; `MotionClock` / `MotionTrace`; `SlotResolver` for the Mail and Maps
slots. Tess's `Contacts.byName` and her person reminders are unchanged (E9 re-runs them).

## Development proof you owe (device sessions under `docs/plan/qa/phase-16/dev-people/`)
At least: the list, the "#" bucket, the jump grid and search over phone-only fixtures you insert (`content insert` on
`raw_contacts` then `data`, as `docs/plan/qa/phase-03/scripts/provision.sh:125-137` inserts Mom) and delete BY ID
(never by account — provision's Mom, account `qa` / `qa`, is never touched) (E10's core); the four card actions with
their `[people] action` lines (E12's core — `adb emu gsm cancel` any call you place); create / edit / photo / delete
and the WRITE_CONTACTS-revoked notice, restored (E13's core); Link and Unlink read back from `aggregation_exceptions`
(E14); Share's logged URI read with `adb shell content read` (E15); a group created, renamed, texted and deleted (E27);
the guard on a `com.example` account fixture — read-only card, the refused EDIT intent, "Can edit" ticked and unticked,
the fixture's rows unchanged (E28's core); the tile's event lines and both `[motion]` lines over 40 s on Start with
solid-colour photo fixtures, and the static pattern with none (E11's core; Start never idles while the tile cycles —
dump with `gdump`). Delete every fixture and restore every permission before a session ends.
