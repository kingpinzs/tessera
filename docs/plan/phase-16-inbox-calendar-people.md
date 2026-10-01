---
phase: 16
slug: inbox-calendar-people
status: FINAL   # FINAL 2026-09-30 (Jeremy: "A") after review round 3 (the last; opus + opus) and Q-16-1 / Q-16-2 / Q-16-3; changes from here only by a dated INDEX Change Log entry. History: Stage A step 3 (split) 2026-09-22; interview (step 4) done 2026-09-23; review round 1 triaged 2026-09-23 (review/2026-09-23-phases11-19-triage.md, applied here); review round 2 triaged 2026-09-23 (review/2026-09-23-phases11-20-r2-triage.md, applied here); R11 gates FINAL: r11/calendar.md, r11/people.md landed 2026-09-23 and are applied (T16-13); review round 3 (the last) triaged and applied 2026-09-30 (review/2026-09-30-phase16-r3-triage.md)
depends-on: [01, 02, 03, 11, 12, 14, 15]   # C-23 2026-09-23: 11 for E25's bursts (phase 11's per-activity query), 12 for the wizard rows (C-4 / C-15, E26); C-1 2026-09-23: 15 for its build task 0 (the live-tile routing fix), which must land before this phase's slot seed (was "17 for its live-tile routing fix only"); C-2: this phase keeps the assignSlotOnce guard (task 1) and 17 depends on 16; r3 D8 2026-09-30: 14 for the pod bay — its Agenda pod opens the CALENDAR slot app this phase seeds, and phase 14 E3's launch precondition is re-cut at this phase's build; Q-16-1 2026-09-30: this phase's own two markers take their slots once, the guard is still built here for every later seed
---

# Phase 16 — W10M inbox apps II: Calendar and People

## Goal
Two Windows 10 Mobile (final release) inbox apps live inside the shell APK as launcher activities, like phase 10's
Music. **Calendar** shows every calendar in Android's CalendarProvider and writes only to the shell's own LOCAL calendar
— the one phase 03's `LocalCalendar` already creates for Tess
(`app/src/main/kotlin/app/tileshell/feeds/LocalCalendar.kt`, J6) — with a tapped, allow-listed Sync as the only road
from it to an account calendar, never the work one (interview Q2 D). It shows W10M's views — Agenda, Day and Week, with
the month as a drop-down from the header (r11/calendar.md K5, K6.1; T16-13) — with
all-day, multi-day and recurring events, per-event reminders that notify at their time, and it takes over the CALENDAR
slot (once, on the update that brings it — Q-16-1, 2026-09-30) so phase 01's Calendar tile (R3 C3, already built) and
phase 14's Agenda pod (r3 D8) open it. **People** is the W10M People hub over Android's
Contacts provider (Q1): an A-Z list with People's own jump grid (r11/people.md §3; T16-13), search, a full-accent contact
card whose titled action rows call, text, mail and map, an editor with photo, link / unlink, SIM import and vCard share, and
W10M's Groups (T16-14); it edits, deletes and adds only phone-only contacts and contacts in accounts on its "Can edit"
list, every other account read-only (Q-16-3, 2026-09-30); it takes over the PEOPLE slot (once, Q-16-1), its tile gets W10M's
measured photo-bubble face (R3 A9), and phase 06's "Phone book" and History contact buttons land in it. Tess's calendar
and contact actions keep reading the same providers, and Tess's "add ... to my calendar" writes only into that same
local calendar (J6, already in code: `ActionLayer.insertEvent`, `cortana/action/ActionLayer.kt:490-492`; cite fixed
2026-09-30, r3 D15, was `:485-487`). Every screen
follows phase 01's bar rule; every visual value is from r11/calendar.md / r11/people.md or a tagged approximation with a
NEEDS-HUMAN row; nothing here reaches the internet — out: offline preferred (A11 as amended 2026-09-23); Jeremy can ask
— and synced calendars are other apps' sync adapters, not the shell's (a Sync copy is a provider write the account's own
adapter uploads).

## Scope
**In:**
- App identities: `Calendar` (launcher, `CATEGORY_APP_CALENDAR`) and `People` (launcher, `CATEGORY_APP_CONTACTS`) in the shell
  APK, own task affinities, app-list entries, pinnable (phase 02), excluded from Uninstall (`AppUninstall.canUninstall`),
  ADDs to qa/phase-03/exported-allowlist.txt; intent handlers: Calendar for `ACTION_VIEW` on `content://com.android.calendar/time/…`
  and `vnd.android.cursor.item/event`, `ACTION_INSERT` / `ACTION_EDIT` on events; People for `ACTION_VIEW` / `ACTION_EDIT` on
  contact and lookup URIs, `ACTION_INSERT` / `ACTION_INSERT_OR_EDIT` on contacts, `ACTION_PICK` on contacts and phone numbers.
  Added 2026-09-30: one activity per app hosts every page — `.calendar.CalendarActivity` and `.people.PeopleActivity`,
  `singleTask` with `onNewIntent`, phase 15's form (r3 D11); the exported reminder receiver is an allow-list ADD too (r3
  D6); what each exported handler may and may not do is the Trust line (r3 D10)
- Slot takeover: the CALENDAR and PEOPLE slots seeded once to the shell's apps (`LayoutStore.assignSlotOnce`, one marker per
  slot after `slot:music:v1`), ~~never over a user's explicit choice — which needs a guard `assignSlotOnce` does not have
  today (Decisions; C-2: the guard stays this phase's task 1, and phase 17's seed runs under it)~~ SUPERSEDED 2026-09-30 by
  Q-16-1 (Jeremy: "(A)"): on the update that brings them the two apps take their slots ONCE, even over a pick made by hand,
  logging what they replaced; from then on a hand pick is always kept and the slot can be pointed back in Settings > Tile
  apps. The guard is still built in task 1 and every other seed runs under it (`slot:music:v1`, phase 17's); the slot
  picker and resolver match the handler's component (r3 D3); re-cuts phase 01 E4 / E4b (named in E1); the phase baseline
  `qa/phase-16/baseline_layout.json` carries the two new markers (C-3)
- Phase 14's pod bay (added 2026-09-30, r3 D8): the Agenda pod opens the seeded CALENDAR slot app — the shell's Calendar —
  and shows what `CalendarFeed`'s `agenda` holds (Birthdays included, a synced copy not, Q-16-2); its empty and denied
  lines are unchanged; phase 14 E3's launch precondition is re-cut at this phase's build (E1)
- Calendar: W10M's views — Agenda (a week strip over day groups), Day and Week — and the month drop-down from the header
  (r11/calendar.md §2–§6; no standalone Month page existed, T16-13); the ≡ calendar pane that shows / hides each calendar
  under its account's header (K6.4) — the shell's own state, never `Calendars.VISIBLE` (r3 D4); day paging; a date
  picker; Today; every calendar in the provider shown, every account calendar read-only (Q2 rule 1), except that a synced
  copy is hidden everywhere in the shell while its Tessera original exists (Q-16-2, 2026-09-30); the event editor (title,
  location, start / end, all day, repeat with the provider's RRULE, reminder minutes, notes) whose calendar is always the
  shell's local calendar — no calendar picker offers another (Q2 rule 2); edit one occurrence / this and following / all, and delete, on local events only; the local
  calendar is phase 03's `LocalCalendar` reused, never a second one (T16-2); Sync on a local event to a calendar allowed on
  the "Can sync to" page (only calendars the phone may write, grouped under their account's name — r3 D5), the "synced to
  <calendar>" marker, and the delete choice for a synced event (Q2 rules 3–4; the T16-1 / T16-3 Decision lines);
  per-event reminder notifications from the provider's `ACTION_EVENT_REMINDER` broadcast (no scheduler of the shell's own,
  Rule 16; the receiver as the r3 D6 line specifies it, one reminder per event — Q-16-2); WRITE_CALENDAR offered in place
  by the editor and by Sync (r3 D7); a settings page with the first day of the week and "Can sync to"; the Birthdays
  calendar (Q3), written from `ShellApp.startFeeds` (r3 D14)
- People: the CONTACTS pivot — the A-Z list with the provider's phonebook buckets and People's own jump grid (72-epx cells,
  r11/people.md P2.2; was "phase 01's jump grid (X8)", SUPERSEDED 2026-09-23 by T16-13), search by name and number, the
  contact card (a full accent page with the 124-epx photo and titled action rows: numbers with call / text, emails with
  mail, addresses with map, birthday, notes, organisation; r11/people.md §4), create /
  edit / delete with `WRITE_CONTACTS` — only on phone-only contacts and in accounts on People's "Can edit" list, nothing
  allowed by default, every other account read-only with the card saying why (Q-16-3, 2026-09-30; the People write guard
  line) — photo from Android's photo picker (`MediaStore.ACTION_PICK_IMAGES`, phase 03's route),
  link / unlink through `AggregationExceptions` (local to the phone, allowed on any contact), share as vCard (`ACTION_SEND
  text/x-vcard`, phase 06's attach form; the Contacts provider's own vCard stream, r3 D10), import from SIM
  (`content://icc/adn`, into the phone's contacts), a settings page with "Can edit", filter contact list by account /
  group, work-profile contacts through the enterprise search URI where the profile allows it; the GROUPS pivot — create /
  rename / delete groups through `ContactsContract.Groups` and "Text the group" (T16-14)
- The People tile face (an ADD to phase 01's live tiles, Change Log when built): R3 A9's photo-bubble event with contact
  photos, the static circle pattern without; fed by a `PeopleFeed` that reads contact photos the way `PhotosFeed` reads
  images; `PeopleFeed` and `CalendarFeed` also publish under their app's component key, so a pinned Calendar or People app
  tile is live too (r3 D12)
- Tess: `Contacts.byName` and the person reminders unchanged (regression row); `ActionLayer.insertEvent` already writes only
  to the shell's local calendar through `LocalCalendar.id(context)` (J6, `cortana/action/ActionLayer.kt:492`; device row
  qa/phase-03/scripts/j6.sh) — ~~this phase adds nothing to it and~~ this phase re-runs J6 as E9's account-calendar
  negative. STRUCK 2026-09-30 (r3 D1): this phase ADDs to phase 03's calendar actions (Change Log when built) — Tess's
  delete goes through the write guard and matches only events in the Tessera calendar, her calendar reads skip a synced
  copy (Q-16-2), and `LocalCalendar` creates only after a successful empty query (r3 D7)
- Phase 06 hand-off: "Phone book" opens the People slot app (already phase 06's text), History rows' contact-card button and
  Messaging's "Contact" attach pick target the shell's People component explicitly (no chooser); People's Call goes through
  `TelecomManager.placeCall` so phase 06's in-call UI shows it once it holds the role
- Manifest ADD: `WRITE_CONTACTS`, with People's own Setup row "People" (`setup:people`: READ_CONTACTS + WRITE_CONTACTS,
  `partialIsDone` false, why line "People shows and edits your contacts. Without it People can't see them." — approximation,
  phase 12 H1), an ADD to phase 01's Setup checklist that phase 12's why table gains (T16-15); Tess's `contacts` row
  (`cortana/CortanaChecklist.kt:48`) is unchanged and asks READ only (was "Tess's checklist's existing `contacts` row … asks
  READ and WRITE together, reads PARTIAL with READ only … its detail line names editing" and its Tess wizard step — SUPERSEDED
  2026-09-23 by T16-15); no new calendar row (phase 01's `calendar` row and phase 03's calendar read / write rows already
  exist). C-4: the new row is the wizard step `wizard_step:setup:people` (a PARTIAL is not done — only Photos' is, phase
  12's `partialIsDone`), and `qa/phase-03/scripts/provision.sh` gains `adb shell pm grant app.tileshell
  android.permission.WRITE_CONTACTS` (belt-and-braces beside its `install -r -g`, `provision.sh:35`). Phase 12's
  persistence rule applies: on an install that has finished or skipped the wizard, the new row does not summon it — it
  shows its state on the Setup checklist (phase 12 Decisions "Persistence", H4). Added 2026-09-30 (r3 V1, V12, against
  phase 12 as built): the row reads MISSING when READ_CONTACTS is not held, whatever WRITE is, and PARTIAL when READ is
  held and WRITE is not; a PARTIAL row never SUMMONS the wizard (`onboarding/SetupWizard.kt:76-84`) — only a MISSING one
  does, and the walk then keeps the step until the row is GRANTED (`needsStep`, `:73-74`); E26 is cut to that. The
  Calendar app's own write grant has no Setup row — Setup's `calendar` row asks READ_CALENDAR only
  (`onboarding/Checklist.kt:122`) and WRITE_CALENDAR is on Tess's row (`cortana/CortanaChecklist.kt:50`) — so the editor
  and Sync offer WRITE_CALENDAR in place (r3 D7)
- App Shortcuts under phase 11 Q1's standing rule (Decisions 2026-09-23; build task 9, E25)
- Diagnostics lines for every silent-empty state; `testTagsAsResourceId` on every window root; tags on every read node
**Out (explicitly):**
- Account sign-in, a sync adapter or server connection of the shell's own (Sync is a provider-side copy into a calendar an
  account's own adapter already syncs — Q2 rule 3), W10M People's "What's new" social feed and its "Me" social tile — out:
  offline preferred (A11 as amended 2026-09-23); Jeremy can ask (was "Anything internet: … the shell syncing anything …
  (A11)", SUPERSEDED 2026-09-23 by C-7 / T16-10 and Q2 D). Invitations and attendees stay out as mail (R10-Q4, a ruling)
- Mail, browser and Maps apps (R10-Q4): a card's mail action opens the Mail slot app, its address action the Maps slot app
- Phone's call history, speed dial and dial pad, and Messaging's threads (phase 06 owns them; People links to them)
- Reading another calendar app's private data (Samsung Calendar's stickers, notes); the shell never touches another app's
  storage — the provider is the only shared surface
- Contacts' social / IM fields beyond display (no chat action: no IM app is in the plan)
- Alarms & Clock, Calculator, Voice Recorder (15), Photos / Camera / video (17), Files (18), the Settings front (19)

## Decisions
- 2026-09-30: Round 3 Q-16-1 — the shell's Calendar and People take their slots once (Jeremy: "(A)", Q-16-1,
  review/2026-09-30-phase16-r3-triage.md; rejected: B, keep the earlier pick with a phone row to re-point; C, a prompt on
  first Start). On the update that brings them, the shell's Calendar and People take the CALENDAR and PEOPLE slots ONCE,
  as Music took its slot — even over a pick made by hand. From then on a pick made by hand is always kept, and the slot
  can be pointed back in Settings > Tile apps. This replaces the 2026-09-22 agent decision (the guard keeps an existing
  explicit assignment on this update) for these two markers; the guard stays for every later seed. How it is built
  (agent): `LayoutStore.assignSlotOnce` gains that guard as its default and one argument, `takeOver`, which only
  `slot:calendar:v1` and `slot:people:v1` pass. With `takeOver`, while the marker has not run, the slot is written even
  when `explicitSlots` already holds it, and the line says what was replaced: `assignSlotOnce <marker> <slot> ->
  <component> -> assigned, replaced user's <earlier component>` (plain `-> assigned` when there was no earlier pick). The
  marker is recorded in the same write, so it runs once: a slot pointed back afterwards is a hand pick like any other, and
  no later start or update touches it (`-> already run`). The replaced app stays in the slot's picker (it handles the
  category), so pointing it back is one pick; the line is how Jeremy or a driver learns which app it was. It is in the
  in-memory ring (`diag/Diagnostics.kt:14-26`), so it is read before the shell's process next restarts (E1's upgrade leg
  from a MARK; P0 on Settings > Diagnostics). Every other marker — `slot:music:v1`, phase 17's — runs under the guard
  and logs `-> kept user's <component>`.
- 2026-09-30: Round 3 Q-16-2 — a synced event is shown once (Jeremy: "(A)", Q-16-2; rejected: B, show the copy and hide the
  original; C, both with a marker). The original (Tessera) event is shown and its synced copy is hidden everywhere in the
  shell while the original exists: the Calendar views, `CalendarFeed` (the tile and `agenda`, so phase 14's Agenda pod),
  Tess's calendar reads and the reminder receiver skip an event id `calendar_sync.json` maps as a copy; the event's page
  says "synced to <calendar>". One event, one reminder. How it is built (agent, r3 D2; ADDs to phases 01, 03 and 14,
  Change Log when built): one reader of `calendar_sync.json` (build task 3) gives the set of copy event ids whose local
  original still exists; a recurring copy's exception events count with their master (an event whose `ORIGINAL_ID` is a
  mapped copy). Each reader drops every instance whose `EVENT_ID` is in that set: the Calendar app's view queries (their
  `view …: n instances` count is the count after the drop); `CalendarFeed`'s tile query and `queryAgenda`
  (`feeds/CalendarFeed.kt:88-95,122-130`, which gain `Instances.EVENT_ID` in their projections);
  `ActionLayer.calendarEvents` (`cortana/action/ActionLayer.kt:698-704`); and the reminder receiver, which posts nothing
  for a copy's alert and logs `[calendar] reminder event=<copy id> minutes=<n>: skipped (synced copy)`. The copy keeps its
  own `Reminders` rows (T16-1), so the account's other clients still remind. Once the original is gone ("Delete here"),
  its mapping is dropped and the copy shows as the account event it is, read-only. Other calendar apps on the phone read
  the provider unfiltered and show both; that is outside the shell (P1 `record`s it).
- 2026-09-30: Round 3 Q-16-3 — People follows the Calendar model (Jeremy: "(A)", Q-16-3; rejected: B, edit any account —
  the draft; C, never write to any account). People edits, deletes and adds contacts only in accounts on a "Can edit" list
  in People's settings, and nothing is allowed by default; phone-only (null-account) contacts are always editable; every
  other account, work included, is read-only until allowed. Q1 A (the full hub) stands; this says where it may write. The
  agent consequences — the one write guard, where a new contact goes, Link / Unlink, upstream columns, the read-only card,
  the proof — are the "People write guard" line at the end of Decisions.
- 2026-09-23: Interview Q3 — a Birthdays calendar (Jeremy: "(a)"): read-only, local, kept in step with contacts' birthdays, so
  the Calendar tile and Tess's "what's on my calendar" see it. It is never offered by Sync (Q2 rule 3 applies to events the user
  created, and a derived calendar is not one).
- 2026-09-23: The App Shortcuts under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Calendar — Agenda, Day,
  Month, New event; People — Contacts, New contact. Re-cut 2026-09-23 by review round 2: "Month" opens Agenda with the month
  drop-down open, since W10M had no Month page (T16-13); People gains Groups at rank 2 (T16-14).
- 2026-09-23: Interview Q2 — read the Google calendars, write only to a local one, sync by choice, never to work (Jeremy: "D.
  it can only read the google calanders BUT when something is added to the local calander I can click sync and it will ask
  which connected calander to sync with. I dont want to add anything to my work calander from my phone ever. jsut my personal
  calander"). Rules:
  1. The app SHOWS every calendar in Android's CalendarProvider (the phone's Google calendars included) and never writes to
     them directly — no edit, no delete, no new event goes into an account calendar.
  2. Every event created in the app (and by Tess's "add ... to my calendar", an ADD to phase 03's action, INDEX Change Log when
     built) goes into the shell's own LOCAL calendar in the provider, which the app creates.
  3. A local event carries a "Sync" action. It asks which connected calendar to sync with and copies the event there; the local
     event stays as the source, marked "synced to <calendar>", and a later Sync on it updates that copy (a one-way push, only
     ever on the user's tap). This is the ONLY way the shell writes to an account calendar.
  4. Never the work calendar (agent reading of "never", Jeremy can overrule): the Sync picker lists only calendars the user
     has allowed in Calendar settings ("Can sync to"), and every calendar starts NOT allowed — so a calendar the user never
     enabled, the work one included, can never be written, even by a mis-tap. Jeremy enables his personal calendar once.
  Acceptance rows prove each rule on the AVD with a local calendar and two sync-adapter-created account calendars (one
  allowed, one not): no write reaches the disallowed one by any path (app, Tess, Sync), read back from the provider.
- 2026-09-23: Interview Q1 — People is a full W10M People hub over Android's Contacts provider and takes the PEOPLE slot
  (Jeremy: "(a)"): list, card, create / edit / delete, photo, link / unlink, SIM import, share. The People tile, Phone's "Phone
  book" and every contact hand-off inside the shell open it. Samsung Contacts stays installed and untouched; both show the same
  contacts through the provider. Phase 06's Out line ("a W10M People app, not in the feature list") is superseded — INDEX Change
  Log when this phase is built.
- 2026-09-22: From phase 11 interview Q1 (Jeremy: "A"), a standing rule for every shell app: this phase's apps declare their
  own top-level screens as static App Shortcuts, so a hold on their tiles bursts those screens (phase 11). Which screens each app
  declares is settled at this phase's own interview; a build task and an acceptance row carry it.
- 2026-09-22: Scope add (Jeremy: "did you add ALL the apps that need to be created and that side pull out thing at a glance
  thing"). PLAN.md: "The W10M inbox apps that can be built inside the rules, each an app in the shell APK like Music: …
  Calendar, … People, …". Read as A8 reads the feature list; this phase is the agent's placement of these two (P3) (Jeremy)
- 2026-09-22: R10-Q4 (Jeremy: "(a)"): "A11 stands as written." Consequences here: no invitations, no sign-in, no sync of the
  shell's own; synced calendars and contacts appear only because other apps' sync adapters put them in the providers (Jeremy;
  consequences agent)
- 2026-09-22: R10 agent call (PLAN.md Rulings): "the shell's own apps take their slots once, following phase 10 Q5's Music
  precedent (assignSlotOnce, never over a user's explicit choice), and Tess's actions target them. This re-cuts phase 01 E4
  and phase 03 E2 / E10 in the phases that add those apps." Applied: CALENDAR and PEOPLE are seeded here; Tess's calendar
  actions already act on the provider, so "targeting the shell's app" means the app and Tess share one calendar (agent).
  Note 2026-09-30: Q-16-1 (Jeremy: "(A)") rules the other way on "never over a user's explicit choice" for these two slots
  on the update that brings the apps — they take their slots once; the call stands for every later seed
- 2026-09-22: **`assignSlotOnce` has no guard for an existing explicit assignment** (agent, found at the split): its body
  checks only the marker (`if (marker in layout.addedOnce) return`) and then writes `explicitSlots + (slot to component)`, so on
  an upgrade from a build where the user had already pointed the CALENDAR or PEOPLE slot at an app by hand, the seed would
  overwrite that choice — the opposite of the R10 call. Build task 1 ADDs the guard to `LayoutStore.assignSlotOnce` (phase 01's
  part, Change Log): when `explicitSlots` already holds the slot, record the marker and keep the user's component, logging
  `assignSlotOnce <marker> <slot> -> kept user's <component>`. The same hole exists for `slot:music:v1` and closes with it.
  ~~E1's upgrade row proves it both ways~~ REPLACED 2026-09-30 by Q-16-1 for `slot:calendar:v1` and `slot:people:v1`: on
  this update those two take their slots over a hand pick, once, and log what they replaced (the Q-16-1 line at the top of
  Decisions). The guard itself is still built by task 1 and governs every other marker: a JVM test proves its rule (a
  marker without `takeOver` over an existing explicit slot → the slot unchanged, the marker recorded, `kept user's`; with
  `takeOver` → replaced, once), E1's guard leg proves it on the device with the one other marker this build has
  (`slot:music:v1`), and E1's upgrade leg proves the take-over
- 2026-09-22: **The package-keyed live-tile fallback, and the one fix for it** (agent; cross-phase fact from the lead,
  verified in code by the phase 17 writer; re-verified 2026-09-23 at the triage: `start/StartPage.kt:150,173,230,232`,
  `feeds/MusicFeed.kt:159-171`, `tiles/ActiveTiles.kt:51`). `StartPage.kt` gives a slot tile the live content
  published under `LiveTileEngine.packageKey(<the resolved app's package>)`, and `MusicFeed` publishes the now-playing face
  under the session owner's package and grows every tile standing for that package (`ActiveTiles.setPackage`). Once CALENDAR
  and PEOPLE resolve to `app.tileshell`, a playing track would put Music's face on the Calendar and People tiles and grow
  them. ~~**The fix is phase 17's, and this phase invents no second one:** phase-17-inbox-photos-camera-video.md, Decisions
  "the package-keyed live-tile fallback must be fixed before the seed" and its build task 2 … Hence `17` in depends-on~~
  SUPERSEDED 2026-09-23 by C-1: **the fix is phase 15's build task 0** (phase 15 builds first; the same one permanent form
  — a shell-owned session is attributed by its tag, `MediaController.getTag()`, a `music` session goes to the MUSIC slot's
  tile, and `StartPage`'s slot fallback and `ActiveTiles.setPackage` follow that routing, so package-keyed content never
  lands on an in-APK app's tile; an ADD to phases 01 and 10 with its Change Log entry, recorded by phase 15). This phase
  invents no second one. Hence `15` in depends-on: this phase's seed (build task 1) does not land before that routing
  does, so the Calendar and People tiles never carry Music for even one build. The Calendar tile's face is published by
  phase 01's `CalendarFeed` under `LiveTileEngine.CALENDAR` (the slot key, not a package), and the Calendar app publishes
  nothing itself — one publisher, no double face; the People tile's `PeopleFeed` publishes under the PEOPLE slot key the same
  way (People tile line below). E1 proves the negative
- 2026-09-22: Phase 01 E4 / E4b re-cut (agent). On the AVD the QA gate recorded People (the image's Contacts) and Calendar
  (AOSP Calendar, build-start call 6) as ONE-handler categories, auto-assigned; with the shell's People and Calendar declared
  they become two-handler categories whose slots read the SEEDED shell app, not "Tap to choose". E4's auto-assign proof keeps
  its remaining one-handler example (Maps / OsmAnd), its 2+-handler unassigned proof keeps Mail and Store, and E4b's picker
  now lists the shell's app among the candidates. Recorded in the INDEX Change Log when built; E1 here is the re-run
- 2026-09-22: Fidelity (agent, RV9 / Q10; R10 testability 4 and 26). Every visual and motion value is (1) "from
  r11/calendar.md" or "from r11/people.md" (~~not yet written: R11 is running and neither file exists on disk 2026-09-23~~
  SUPERSEDED 2026-09-23 by T16-13: both landed 2026-09-23 and are applied; docs/plan/r11-inbox-apps.md is the index, C-12);
  (2) already measured and cited: phase 01's drawn status bar (`BarMetrics.STATUS_EPX`, `bars/SystemBars.kt:77-80`; 28 epx
  today from R3 C4, which R11 contradicts in-app at 24.0 epx, r11/people.md P0.2 — C-17's R3 C4 re-check decides, so no row
  here writes the literal) and nav bar (`BarMetrics.NAV_EPX`, 48 epx, X6); the Calendar tile face (R3 C3, MEDIUM: day
  name in the 15-epx body class centred with its top 33 epx below the tile top, the day number 30 epx tall centred at 50
  epx; already built in `CalendarFeed` / `TileFace.CalendarDay`); the People tile motion (R3 A9, MEDIUM/LOW, S5 14393
  camera 24 fps: a photo bubble slides out left in ≈333 ms, ≈6-frame pause, a new bubble slides in from the right
  settling in ≈583 ms, the event 1.88 s, repeating every 7.7 ± 0.2 s; the static circle pattern without photos; C3
  "circle pattern; photo bubbles as in A9"); list rows at a 44-epx pitch with a 41-epx icon and text at x 57 epx (R3 C2,
  R6 §5.1.4; `AppListMetrics`); ~~phase 06's History row as the contact-row stand-in (R7 §1.3 …); the jump grid (X8, R3 C2
  LOW)~~ SUPERSEDED 2026-09-23 by T16-13: People's own row (50-epx pitch, 32-epx avatar at x 12, name at x 57.75,
  r11/people.md P1.7–P1.10) and its own jump grid (72-epx cells, P2.2); the type ramp (R1 §5.1); the pivot header (phase 10 task 6's
  P4 design, `MusicMetrics`) and settle (X13); the app bar (R7 §3.5.8); the flyout (R7 §2.2.5, §3.6.2); the dialog (R7
  §1.3.9); the empty-list line (R7 §3.5.9); ~~the outlined fields of the reminder page (R7 §3.7.1: 43.4 epx tall at a
  53.6-epx pitch) as the editor-field stand-in~~ (SUPERSEDED 2026-09-23 by T16-13: People's 32-epx fields with a 2-epx grey
  border, r11/people.md P4.4, which r11/calendar.md U3 also proposes for the Calendar editor); sliders (X23); the row press
  (X19); or (3) an approximation with its NEEDS-HUMAN row. R11 has landed: each (2) stand-in is replaced where R11 measured
  (the T16-13 line lists them) and stays tagged where R11 is UNMEASURED. H-row kinds: **[fidelity]** (matches R11 or R3 A9 within tolerance, judged on the phone) and **[accept]**
  (P4 design or approximation, no footage to close it against) (agent)
- 2026-09-22: Calendar data (agent, ~~pending Q2; written for its lean A~~). SUPERSEDED 2026-09-23 by Q2 D, T16-1 and T16-2
  (the lines at the end of Decisions): ~~The app reads and writes `CalendarContract` — every calendar the provider holds,
  whoever synced it — and on first start, when `Calendars` is empty (…), creates ONE local calendar through the sync-adapter
  URI (`caller_is_syncadapter=true`, `ACCOUNT_TYPE_LOCAL`, name "Calendar", access level OWNER, colour the accent;
  approximation, H4), idempotently (a second start creates none). Phase 03's `ActionLayer.writableCalendarId` then finds it
  (`CAL_ACCESS_CONTRIBUTOR` or better) … if another app removes it and no calendar remains, the next start recreates it. A
  calendar whose access level is below CONTRIBUTOR is shown read-only~~. What stands: the app reads every calendar the
  provider holds, whoever synced it; the local calendar is never deleted by the shell. `writableCalendarId` no longer exists
  (J6 removed it), the local calendar is `LocalCalendar`'s and exists whatever else the provider holds, and every calendar
  but it is read-only in the app
- 2026-09-22: Event reminders (agent, Rule 16). Per-event reminders are `CalendarContract.Reminders` rows with `METHOD_ALERT`;
  the PROVIDER schedules them and broadcasts `CalendarContract.ACTION_EVENT_REMINDER` (~~data `content://com.android.calendar/time/
  <ms>`~~ — the data URI's form is not relied on, r3 D6 2026-09-30: the filter matches scheme and host only and the
  receiver reads nothing from the intent; Verify at build start 1) to every receiver holding `READ_CALENDAR`, and
  re-schedules on boot. The shell registers that receiver and posts one
  notification per alert on a calendar channel (R11 captured no reminder toast, r11/calendar.md U5: the look is the Action
  Center notification item's, R3 A19, an approximation, H7), marking the alert row in `calendar_alerts` fired and, on dismiss, dismissed. No alarm of the shell's own is armed for
  calendar events, so `ReminderScheduler` is untouched here. Consequence, stated: any OTHER calendar app on the phone that
  registers the same receiver also notifies — on the AVD the AOSP Calendar fixture does (E6 records the double), on the phone
  Samsung Calendar will (P1); the fix is the user turning that app's notifications off, an Android limit. Added 2026-09-23
  (T16-4): the shell notifies for EVERY calendar's alerts, account calendars included, since the app shows them all under
  Q2 D — so P1's double also covers Samsung Calendar's own events; that is the rule, not a defect. The Birthdays calendar's
  events carry no `Reminders` rows (agent), so nothing fires for them. Added 2026-09-30: "every calendar's alerts" has one
  exception — a synced copy's alert while its Tessera original exists is skipped (Q-16-2: one event, one reminder); and
  how the receiver is declared, what it re-reads and how it marks the alert rows is the r3 D6 line at the end of Decisions
  (it replaces a plain read of this broadcast's own alert)
- 2026-09-22: Recurrence and time zones (agent). Repeat writes `RRULE` (+ `DURATION` instead of `DTEND`, as the provider
  requires) and the provider expands `Instances`; "edit this occurrence" writes an exception event (`ORIGINAL_ID` /
  `ORIGINAL_INSTANCE_TIME`), "this and following" ends the series with `UNTIL` and starts a new one, "all" edits the master.
  Timed events carry `EVENT_TIMEZONE` and display in the device zone; all-day events are date-anchored (UTC midnight, `ALL_DAY`
  = 1) and keep their date across a zone change. The first day of the week defaults to the locale's (`WeekFields.of(locale)`)
  with a setting on the app's settings page (W10M followed the locale, r11/calendar.md K2.2; the setting's wording is
  UNMEASURED, U7 — approximation, H6). An event with no title shows "(No title)" (approximation, H6). Views window their
  queries to the visible range (the week view asks the provider for its week's instances only, the agenda for the days it
  has loaded; was "a month grid", which T16-13 removed), which is what keeps thousands of events cheap (E7)
- 2026-09-22: Birthdays (Q3 A, ruled 2026-09-23). A read-only local "Birthdays" calendar the app keeps in step with
  contacts' birthday fields (`ContactsContract.CommonDataKinds.Event`, `TYPE_BIRTHDAY`; a yearly all-day event per contact,
  updated from a `ContentObserver` on the Contacts provider), so the Calendar tile and Tess's "what's on my calendar" see
  birthdays too; its access level is READ so the editor refuses it. W10M's Outlook Calendar had exactly this calendar (agent).
  Added 2026-09-23 (T16-2 line 2): it lives under its OWN LOCAL account name, `Tessera Birthdays`, never `Tessera`, because
  `LocalCalendar` finds the shell's calendar by account type LOCAL + account name `Tessera` and takes the first match
  (`feeds/LocalCalendar.kt:26-34`); its events carry no reminder rows (T16-4). Added 2026-09-30 (r3 D14, V15): the
  Birthdays writer and its Contacts observer are started from `ShellApp.startFeeds` (`ShellApp.kt:201`), beside
  `CalendarFeed`, so the tile, the Agenda pod and Tess see birthdays with the Calendar app never opened; the calendar is
  created when the first birthday exists (never empty at first start) and is kept, empty, when the last one goes. Date
  forms: `yyyy-MM-dd` → a yearly all-day event from that date; `--MM-dd` (no year) → the same from this year's date; 29
  February (either form) → `RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1`, so it shows on 29 February in a leap year and on
  28 February otherwise (approximation, H5; Verify at build start 5); a value in neither form is skipped and is not
  counted in `birthdays: n synced`
- 2026-09-22: People over the provider (agent; Q1 A, ruled 2026-09-23). List order and letter buckets come from
  the provider (`SORT_KEY_PRIMARY`, `PHONEBOOK_LABEL`), so non-Latin names file where Android files them, and People's own
  jump grid (r11/people.md §3, T16-13; was "the jump grid (X8)") is built from those labels rather than from phase 01's
  `AppIndex`; a contact with no name shows its number or e-mail
  as its name under "#" (approximation, H8). Duplicates are the provider's aggregation: two raw contacts it merged show as one
  row; Link writes `AggregationExceptions` `TYPE_KEEP_TOGETHER`, Unlink `TYPE_KEEP_SEPARATE`. ~~New contacts go to the account
  the "filter contact list" default names, else the local (null-account) raw contact set, as AOSP Contacts does.~~
  SUPERSEDED 2026-09-30 by Q-16-3 (and r3 D13: "the account the filter default names" was undefined when the filter is
  all accounts): a new contact goes to the phone (the null-account raw contact set) unless an allowed account is chosen in
  the editor; every write is behind the People write guard (the line at the end of Decisions). Card actions:
  Call → `TelecomManager.placeCall` (`CALL_PHONE` held; phase 06's in-call UI once it holds the role); Text → `ACTION_SENDTO
  smsto:` to the SMS role holder; Mail → `ACTION_SENDTO mailto:` to the Mail slot app when assigned, else Android's chooser;
  Address → `geo:0,0?q=` to the Maps slot app as Tess's directions do. The Android profile ("Me", `ContactsContract.Profile`)
  is not shown: W10M's Me tile was a social surface (out: offline preferred, A11 as amended 2026-09-23 — Jeremy can ask;
  approximation, H9). Work-profile contacts: a personal-profile app
  cannot list them; search goes through `ENTERPRISE_CONTENT_FILTER_URI` where the profile's policy allows and shows matches
  with phase 01's briefcase glyph (P4 design of phase 01 Q5, extended here; H10)
- 2026-09-22: Intent handlers and the chooser (agent, P2). Declaring VIEW / INSERT / EDIT / PICK for contacts and events makes
  the shell one handler beside Samsung's; another app's first contact tap on the phone shows Android's chooser once and
  "Always" is remembered (P3 records it). Inside the shell every hand-off targets the component explicitly — phase 06's History
  card button and "Contact" pick, the Calendar tile's tap, Tess — so no chooser appears on any shell path. There is no
  CONTACTS or CALENDAR role to hold (R10 testability 23), so the slot is the takeover and the chooser is the only seam
- 2026-09-22: People tile (agent; an ADD to phase 01's feed set, Change Log when built). `PeopleFeed` publishes under
  `LiveTileEngine`'s PEOPLE key the way `CalendarFeed` publishes the calendar face: with ≥ 1 contact photo, R3 A9's bubble
  event on a 7.7-s period drawn in-house (RV5) from the A9 timings, cycling contacts with photos at random; with none, the
  static circle pattern. Photos are read at tile size and re-read on a provider change, never on a timer. The face shows on
  the PEOPLE slot tile whichever app holds the slot, since it is the tile's W10M face and not the app's content — build-start
  check against phase 01's slot-tile content rules, recorded here. Added 2026-09-30 (r3 D12, agent call, consistent with
  phase 15's routing fix): `LiveTileEngine` has no PEOPLE key today (`tiles/engine/LiveTileEngine.kt:44-47`) and
  `StartPage`'s slot branches name PHOTOS, CALENDAR and MUSIC only (`start/StartPage.kt:160-164,242-246`) — task 7 ADDs
  `LiveTileEngine.PEOPLE` and `Slot.PEOPLE` to both. An AppTile of the shell's Calendar or People (pinned from the app
  list) reads the content under its component key (`TileRouting.tileContentKey`, `tiles/engine/TileRouting.kt:61-62`),
  which neither feed publishes, so it would be faceless: `CalendarFeed` also publishes its face under
  `TileRouting.componentKey` of the Calendar activity and `PeopleFeed` under the People activity's, so a pinned app tile
  is live like the slot tile. One publisher per face, two keys; E11 and E4 assert the pinned tiles. (V14) the photo order
  is no longer "at random" for the harness: each `[people] tile event` line names the contact, `lookup=<key>`
- 2026-09-22: Bars (agent, phase 01's bar rule of 2026-09-17): every Calendar and People page hides Samsung's bars and draws
  the W10M status bar and the Back / Windows / Search nav bar; Back is Back for the page, Windows goes Home
- 2026-09-22: Harness contracts (agent; R10 testability 24, 25, 33, 34). `testTagsAsResourceId` on every window root; tags on
  the node that carries each read text: `cal_view`, `cal_day:<yyyy-mm-dd>`, `cal_event:<id>`, `cal_event_title:<id>`,
  `cal_month_cell:<yyyy-mm-dd>`, `cal_editor_field:<name>`, `cal_calendar_row:<id>`, `people_row:<lookup>`,
  `people_name:<lookup>`, `people_letter:<label>`, `people_card_action:<kind>:<n>`, `people_field:<name>`, `people_link_row:<raw>`,
  `people_sim_row:<n>`; added 2026-09-23 by the review triage: `cal_sync`, `cal_sync_target:<id>`, `cal_synced_marker:<id>`,
  `cal_settings_can_sync:<id>` (T16-1), `cal_event_action:<edit|delete|sync>`, `cal_delete_choice:<here|both>`,
  `cal_view_mode:<agenda|day|week>` (Week added, month removed — SUPERSEDED `<agenda|day|month>` 2026-09-23 by T16-13),
  `cal_editor`, `people_page:<list|editor>` (each page tag `selected="true"` on the
  page showing; E22–E25); added 2026-09-23 by review round 2: `cal_month_dropdown` (the open month panel, which holds the
  `cal_month_cell:<yyyy-mm-dd>` nodes), `cal_pane` (the ≡ calendar pane, which holds the `cal_calendar_row:<id>` rows and
  their account headers `cal_account:<name>`), `cal_strip_day:<yyyy-mm-dd>`, `cal_event_bar:<id>` (T16-13),
  `people_pivot:<contacts|groups>`, `people_group:<id>`, `people_group_new`, `people_group_name`,
  `people_group_action:<rename|delete|text>` (T16-14), `people_jump_cell:<label>`, `people_card_photo` (T16-13); drivers
  symlink qa/phase-03/scripts/lib.sh and stay adb-driven. Diagnostics lines: `[calendar]
  calendars: n (local created id=<id> | local present | none)` and `[calendar] calendars: denied (READ_CALENDAR)` (T16-16),
  `[calendar] view <name> <from>..<to>: n instances in <ms> ms` (`in <ms> ms` = the query plus the first composed frame,
  from `withFrameNanos`; T16-17), `[calendar]
  write <op> event=<id>: ok|failed <err>`, `[calendar] reminder event=<id> minutes=<n>: notified|dismissed`, `[calendar]
  birthdays: n synced`, `[calendar] birthdays calendar could not be created: <err>` (LocalCalendar's form, T16-19),
  `[people] list: n contacts read=<bool> write=<bool>`, `[people] search "<q>": n (+m enterprise)`,
  `[people] write <op> raw=<id>: ok|failed <err>`, `[people] link <a>+<b>: ok|failed`, `[people] sim import: n of m`,
  `[people] tile: n photos`, `[people] tile: photo <lookup> skipped: <why>` (T16-19), `[people] group
  <create|rename|delete> <id>: ok | failed <err>` (T16-14), plus `LayoutStore`'s own `assignSlotOnce …` line with the new `kept user's` form
  (and, 2026-09-30, Q-16-1's `-> assigned, replaced user's <earlier component>` form); added
  2026-09-23: `LocalCalendar`'s own `[calendar] local calendar created: <uri>` / `… lookup failed: <err>` / `… could not be
  created: <err>` (`feeds/LocalCalendar.kt:34,55,57`), `[calendar] sync event=<id> -> calendar <id>: ok | updated |
  recreated | failed <err> | failed mapping stale | refused (not allowed)` (`failed mapping stale` added by T16-12) and `[calendar] sync event=<id>: no calendar allowed -> can sync to`
  (T16-1, T16-3), `[people] tile event <n> t0=<uptime>` (T16-8) and the `[motion]` clock of the Acceptance preamble (C-5).
  Every line here is written in the main process and read from the launcher ring (`diag`, `qa/phase-03/scripts/lib.sh:155-159`;
  C-20; cite fixed 2026-09-30, r3 D15 / V9, was `:141-145`).
  Start while the People tile cycles never idles, so its dumps go through phase 05's gesture driver (C-10, Acceptance
  preamble). The tags and lines round 3 added are the "Harness, round 3" line at the end of Decisions
- 2026-09-22: APK budget (agent; phase 03's ≤ 600 MB): code only; E15 records the delta, ≤ 2 MB, no new asset ≥ 1 MB
- 2026-09-22: App-list regression (agent; R10 testability 35): two new entries; E2 runs the phase-02 `regress.sh` pattern
  and asserts no "New" caption (X14)
- 2026-09-23 (review triage T16-2, doc update; J6 is built in code, outside the phase docs): **the shell's local calendar is
  phase 03's `LocalCalendar`, reused.** (1) One finder / creator: `LocalCalendar.id(context)`
  (`app/src/main/kotlin/app/tileshell/feeds/LocalCalendar.kt:24`; the triage's `idOrCreate()` is this function) finds the
  calendar and creates it through the sync-adapter URI when absent; `ActionLayer.insertEvent`
  (`cortana/action/ActionLayer.kt:492`) and the Calendar app share it, and the app never creates a second. J6's constants
  replace this doc's old ones (name "Calendar" in "Calendar data", `account_name=Tessera` in the edge case): account name,
  calendar name and display name `Tessera` (`LocalCalendar.ACCOUNT_NAME`, `:21`), `ACCOUNT_TYPE_LOCAL`, owner access,
  visible, colour #0063B1 (`:43-52`); H4 judges the name and colour. (2) The find keys on those constants (account type LOCAL
  + account name `Tessera`, `:26-34`), never on "any LOCAL calendar": the QA driver's LOCAL `qa` calendar and the Birthdays
  calendar (Q3) are LOCAL too and must never be picked, so Birthdays lives under its own account name (Decisions
  "Birthdays"). (3) If the calendar cannot be found or created (the provider refuses or is disabled, WRITE_CALENDAR is
  revoked), Tess refuses with the spoken notice "I don't have a calendar to add that to." (`ActionLayer.kt:492`) and never
  falls back to another calendar (J6's rule); the app shows the same state — its editor refuses to save with that notice
  and its views still show what the provider holds (2026-09-30, r3 D7: when the cause is WRITE_CALENDAR not held, the
  notice offers the grant in place). (4) Under Q2 D the local calendar always exists once the app or Tess
  has run, since it is the only write target: the app calls `LocalCalendar.id` at start (not while READ_CALENDAR is
  denied, and `id` creates only after a successful empty query — r3 D7), so it is created even when other
  calendars exist (E3 inverted), and the app logs `local present` when Tess created it first; removed by another app, it is
  recreated at the next start or write whatever else remains (the edge case re-cut). (5) E9 cites J6's row
  (qa/phase-03/scripts/j6.sh; evidence qa/phase-03/J6/J6.txt, 7/7 on 2026-09-23) as its account-calendar negative, re-run
  on this phase's build with its local-calendar lookup keyed on `account_name=Tessera` (build task 8), instead of a new row;
  P2 gains the same on the phone. (6) Build task 3 reuses `LocalCalendar` — no second creator; the INDEX Change Log line for
  J6 (the lead's, written when J6 lands) is the record of the ADD to phase 03 that Q2 rule 2 names.
- 2026-09-23 (agent, review triage T16-1): **Sync (Q2 rules 3–4), designed.** The mapping lives in the shell's own
  `calendar_sync.json` (files dir, temp-file-and-rename like `LayoutStore`): local event id → {target calendar `_ID`,
  account name, account type, copy event id, ~~last-pushed hash of the copied fields~~}; `Events.SYNC_DATA*` and `_SYNC_ID` are
  sync-adapter columns and are not used. The copy is a normal-app insert into the target calendar (no
  `caller_is_syncadapter`), so the provider marks it dirty and the account's own sync adapter uploads it. A re-Sync updates
  the copy by its id ~~when the hash differs~~ (`updated`) and recreates it if it is gone on the other side (`recreated`, the
  notice says so); an edit made on the other side is overwritten by the next Sync (local is the source). REPLACED
  2026-09-30 (r3 V13): a hash of the local fields at the last push is unchanged when only the COPY was edited, so the
  overwrite this line promises would read a no-op. Sync re-reads the copy and compares its copied fields (and its
  exceptions and reminders) with the local event's; it writes when they differ (`updated`) and writes nothing when they
  are equal (`ok`). No hash is stored. Deleting a synced
  local event asks "Delete here" (the default) or "Delete here and from <calendar>" — the second is still a user tap, so rule
  3 holds. An allowed calendar removed from the phone: Sync refuses with "That calendar is no longer on this phone"
  (`failed calendar gone`) and the marker stays with a warning glyph. Recurring events copy RRULE / DURATION / EXDATE, their
  exception events (`ORIGINAL_ID` re-pointed at the copy's master) and their reminders. The "Can sync to" rows list the
  provider's non-LOCAL calendars (2026-09-30, r3 D5: only those the phone may write, grouped under their account's name —
  the D5 line at the end of Decisions), keyed on `_ID` + account name + account type and stored in `calendar_sync.json`'s
  `allowed` list, so a removed and re-added account (new ids) starts NOT allowed; the shell's own calendar and Birthdays are
  never listed. The marker reads "synced to <calendar>" (P4 design, H14) and is the line Q-16-2 puts on the event's page;
  the copy itself is hidden in the shell while the original exists (Q-16-2). ~~The write layer's guard: every insert, update and
  delete refuses a calendar id other than `LocalCalendar.id`'s, except a Sync push to an allowed id (`refused (not
  allowed)`), and a JVM test proves the refusal.~~ SUPERSEDED 2026-09-23 by T16-11 (it refused the Birthdays writer; the
  three-case allow set is the agent line at the end of Decisions; four cases since 2026-09-30, r3 D1 / D6). Hardened 2026-09-23 (r2 triage T16-12): before an
  `updated` push or a delete-"both", the write layer re-reads the copy event's `calendar_id` and requires it to equal the
  mapping's target and that target to be still on the `allowed` list; otherwise it refuses (`failed mapping stale`), and
  "Delete here and from <calendar>" is never offered for a target no longer allowed; T16-11's JVM test carries the case.
  Tags and lines in "Harness contracts"; rows E22–E24; P2 re-cut to the push.
  Reason: the sync-adapter columns are not a normal app's to write, a normal insert lets the account's own adapter do the
  upload, and "local is the source, every account write is a tap" is Jeremy's Q2 wording ("when something is added to the
  local calander I can click sync").
- 2026-09-23 (agent, review triage T16-3): the opt-in allow-list stays and its seam is removed: the first Sync with nothing
  allowed opens the "Can sync to" page directly with one line, "Choose which calendars Sync may use", and returns to the Sync
  picker when the user comes back — never a dead-end empty picker (`[calendar] sync event=<id>: no calendar allowed -> can
  sync to`; E23). H14 [accept] shows Jeremy the trade. Reason: Jeremy's "never" — only an opt-in list makes a mis-tap on the
  work calendar impossible — and going straight to the page is one step instead of an empty picker and a hunt (P2).
- 2026-09-23 (agent, review triage C-1 / C-2 / T16-5): the live-tile routing fix is phase 15's build task 0, so depends-on is
  ~~`[01, 02, 03, 15]`~~ `[01, 02, 03, 11, 12, 14, 15]` (corrected 2026-09-30, r3 D15: the line disagreed with the header,
  which C-23 had widened with 11 and 12; 14 added by r3 D8) and build task 1's precondition is "phase 15's task 0 is
  built"; the `assignSlotOnce` guard stays this
  phase's build task 1 with its phase 01 Change Log line, and phase 17 depends on 16 and seeds under it. Reason: the inbox
  order stays 15 → 16 → 17 (the smallest change), and the guard belongs to the first phase that seeds a slot a user may
  already have assigned by hand. Note 2026-09-30 (Q-16-1): still so — the guard is built here and phase 17 seeds under
  it; this phase's own two markers pass `takeOver` and take their slots once.
- 2026-09-23 (agent, review triage C-5): **the shell logs its own motion clock.** The People tile logs `[people] tile event <n>
  t0=<uptime>` for each bubble event and `[motion] people_bubble_out | people_bubble_in t0=<uptime> settle=<ms>` for its two
  slides; every other motion (People's pivot settle, the month drop-down, a day page — re-cut 2026-09-23 by T16-13, was
  "a pivot settle, a view change") logs `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%>
  settle=<ms>` from `withFrameNanos`; every `[motion]` line also carries `frames=<n> maxGapMs=<ms>` (added by C-31); E11 and E19 assert those numbers against RV11's tolerance and a screenrecord only
  corroborates under phase 05's frame-spacing rule. Reason: the P02 lesson — the emulator's screenrecord is variable-rate
  (qa/phase-05/README.md) and cannot time a 333-ms slide.
- 2026-09-23 (review triage C-7 / T16-10, doc update): the 2026-09-22 R10-Q4 line above gives A11 as the reason for no
  sign-in and no sync of the shell's own; A11 is amended (PLAN.md 2026-09-23: internet is fine, offline preferred), so it is
  no longer the reason — those exclusions stand as offline-preferred calls, Jeremy can ask. That line is Jeremy's ruling and
  is left as written; "synced calendars are other apps' sync adapters" stays true.
- 2026-09-23 (agent, r2 triage T16-11): **the calendar write guard's allow set is ~~exactly three cases~~ four cases since
  2026-09-30 (r3 D6 adds (4), the alert-state update; r3 D1 puts Tess's delete behind the guard — the r3 D1 / D6 line at
  the end of Decisions).** (1) The `Tessera`
  calendar (`LocalCalendar.id`, `feeds/LocalCalendar.kt:24`): every op. (2) The `Tessera Birthdays` calendar: the Birthdays
  writer's path only — the sync-adapter URI under its own LOCAL account, never offered to the editor or to Sync. (3) An
  allowed Sync target: only a push, update or delete of a copy that `calendar_sync.json` maps (and, by T16-12, only while the
  copy's re-read `calendar_id` matches). Every other combination is refused with `[calendar] write <op> event=<id>: failed
  refused (not allowed)` / the sync line's `refused (not allowed)`. The JVM test covers each allowed case and the refusal
  of every other combination — the editor → Birthdays, the Birthdays writer → Tessera, Sync → an id not allowed, Sync →
  an allowed id but an unmapped event, any op → an account calendar, and T16-12's stale mapping. Reason: Q2 D's rules 1–4
  are about the user's events; the derived Birthdays calendar is the shell's own and must be writable by exactly one code
  path, which the T16-1 guard as first written refused.
- 2026-09-23 (agent, r2 triage T16-13): **R11 calendar / people applied, with two agent calls.** People (r11/people.md, all
  10586-era, HIGH on two devices unless noted): rows at a 50-epx pitch with a 32-epx circular avatar at x 12 (a grey disc
  with the initial without a photo) and the name at x 57.75, under accent letter headers (cap 22.25 at x 14.75) — P1.5–P1.10,
  replacing the R7 §1.3 History-row stand-in; the card a full accent page from the status bar to the nav bar with the name in
  caps, a 124-epx circular photo at x 12 and titled action rows ("Call Mobile" + the number, "Email Personal" + the address,
  48 / 65.5-epx pitches) — §4; editor fields 32 epx with a 2-epx (133,133,133) border, accent type-labels and "+ field" rows
  at a 44-epx pitch — §5; pivots CONTACTS / GROUPS (What's New stays out) in the §2 caps header. **Agent call: People's own
  jump grid** — 72-epx cells, 4 columns at 360 epx, accent letters where contacts exist, "#" first and a globe last, a full
  page over the list (P2.1–P2.5) — not X8; phase 01's app-list grid is phase 01's matter. H2 is [fidelity] against the 10586
  captures with a version note (no governing-build capture, U1). Calendar (r11/calendar.md): W10M had Agenda / Day / Week
  and a month DROP-DOWN from the header, no Month page (K5, K6.1). **Agent call: the "Month" App Shortcut opens Agenda with
  the month drop-down open** (`cal_view_mode:agenda` selected and `cal_month_dropdown` shown); tags `cal_view_mode:<agenda |
  day | week>` and `cal_month_dropdown`. The ≡ calendar list is W10M's show / hide pane with account group headers (K6.4:
  48.1-epx rows, the checkbox filled with the calendar's colour), so `cal_calendar_row:` lives inside `cal_pane`, and the
  "Can sync to" page is a checkbox list styled like it (where the pane's ticks are stored: the r3 D4 line, 2026-09-30).
  Event rows: an 8-epx colour bar at x 0 (solid, or a 2-epx outline for
  free / tentative, K3.8), the time or "All day" label at x 24.3 tinted with the calendar's colour, the title in white at x
  92.5; all-day rows 40 epx on a 44-epx pitch, timed rows 56 epx (K3.5–K3.7). The page background is #1A1A1A, the status
  and nav bars black (K1.3). The week strip and the selected day take the 15063 form — two week rows, the selected day a
  32-epx accent square (K2.4–K2.5, LOW) — with an H1 note. Day view, event page, editor, reminder toast and settings are
  UNMEASURED (U1–U7) and are built as r11/calendar.md §8 proposes, as [accept] rows (H6, H7, H15); H1 [fidelity] narrows
  to Agenda, Week, the drop-down and the pane. Motion is UNMEASURED (U8): the drop-down grows from its top edge in 200 ms
  ease-out (R7 §2.2.6), day paging settles on X13 — tagged approximations. Status bar per C-17. Reason: the measured forms
  replace stand-ins R11 contradicts; People's grid is measured HIGH on two devices while X8 is LOW, and a shortcut that
  opens the drop-down keeps Jeremy's "Month" satellite without building a page W10M never had.
- 2026-09-23 (agent, r2 triage T16-14): **People's GROUPS pivot is in** (W10M People 10586: CONTACTS / WHAT'S NEW /
  GROUPS, r11/people.md P1.1, P5.2; the triage's Scope rule — A8 + Q12 + A4; What's New stays out as offline-preferred social;
  listed for Jeremy in the triage's §2b). Create / rename / delete through `ContactsContract.Groups` under the same account
  rule new contacts use ("People over the provider" — since 2026-09-30 that rule is Q-16-3's: a new group goes to the
  phone unless an allowed account is chosen, and a group in an account not on "Can edit" is read-only: no rename, delete
  or membership change); a group's page lists its members in the list row form; "Text the
  group" = `ACTION_SENDTO smsto:<n1>;<n2>…` (each member's first mobile number) to the SMS role holder, as the card's Text
  does, so it works before phase 06 holds the role; tags `people_pivot:groups`, `people_group:<id>`; line `[people] group
  <create | rename | delete> <id>: ok | failed <err>`; E27 proves it; H16 [accept] judges it (guide wording only, P5.2 LOW);
  the People shortcut set gains `groups` at rank 2 under phase 11's standing rule. Reason: an in-scope W10M app's own offline
  feature is in by the rulings the triage cites.
- 2026-09-23 (agent, r2 triage T16-15): **People gets its own Setup row `setup:people`** (READ_CONTACTS + WRITE_CONTACTS;
  `partialIsDone` false; why line "People shows and edits your contacts. Without it People can't see them." — approximation,
  phase 12 H1), and Tess's `contacts` row is unchanged (READ). Scope's Manifest ADD paragraph, build task 2, E13's PARTIAL
  clause, E18 and E26 (→ `wizard_step:setup:people`) are re-cut; phase 12's why table gains the row. Reason: phases 15, 17,
  18 and 19 each put their app's grant on the Setup checklist; Tess never writes contacts, so her health page must not read
  PARTIAL for People's need.
- 2026-09-23 (agent, r2 triage C-17): **the status bar is cited, never written as a number.** R3 C4 read 28 epx on Start;
  every in-app W10M measurement since reads 24.0 (r11/people.md P0.2, r11/clock.md 1.1, R8). The lead re-measures R3's own
  footage first (~~INDEX research row "R3 C4 re-check"~~ — no such row exists in INDEX; the conflict is recorded in INDEX's
  R11 research row as "CONFLICT to settle: the status bar measures 24 epx in these captures against R3 C4's 28", `INDEX.md:40`;
  corrected 2026-09-30, r3 D15); meanwhile every row here reads phase 01's drawn status bar through
  `BarMetrics.STATUS_EPX` (`bars/SystemBars.kt:77-80`; the symbol was wrongly written `SystemBars.STATUS_EPX`). Reason: the
  re-check's outcome then needs no edit in this doc.
- 2026-09-30 (agent, r3 D1 / D6): **the calendar write guard covers Tess's delete and the alert state; its allow set is
  four cases.** T16-11's three stand. (4) The reminder receiver's update of `CalendarAlerts.STATE` (fired, dismissed) on
  any calendar's alert — `calendar_alerts` is a table local to the phone that no sync adapter uploads — and no other
  column or table. Tess's delete: `ActionLayer.deleteEvent` matches a title over unfiltered Instances and `removeEvent`
  deletes `Events/<id>` whatever its calendar (`cortana/action/ActionLayer.kt:510-515,529-531`; the phrases are
  `cortana/match/CommandMatcher.kt:175-176`), a direct delete on an account calendar — rule 1 and "by any path (app, Tess,
  Sync)" fail. Build task 3 (an ADD to phase 03's action, Change Log when built): the match is limited to `calendar_id =
  LocalCalendar.id`; a title found only on another calendar answers "That event isn't in your Tessera calendar." with no
  Delete card (approximation, H22); a title found nowhere keeps today's "I don't see <title> on your calendar.";
  `removeEvent` deletes through the write layer; a synced local event is deleted "here" only — the copy kept, its mapping
  dropped, the app's default choice — since Tess never writes to an account calendar. The JVM test gains: Tess delete → an
  account calendar (refused), Tess delete → Tessera (allowed), the alert-state update (allowed), any other write by the
  receiver (refused). Reason: Q2 D rule 1.
- 2026-09-30 (agent, r3 D6; V7): **the reminder receiver, specified.** (a) `.calendar.CalendarReminderReceiver` (an ADD of
  build task 3), manifest-registered for `android.intent.action.EVENT_REMINDER` with `<data android:scheme="content"
  android:host="com.android.calendar"/>`: the provider's broadcast carries a data URI, and a filter with no `<data>`
  never matches an intent that has data. (b) `exported="true"` — the sender is the provider's process — so any app can
  send it: it is on qa/phase-03/exported-allowlist.txt and on the Trust list, and the broadcast is a POKE only, nothing in
  the intent is read. (c) On each poke the receiver re-reads `CalendarAlerts` for rows with `alarmTime` ≤ now in state
  SCHEDULED or FIRED — another calendar app may have flipped the row to FIRED first, so a SCHEDULED-only read can find
  nothing — less the alert ids in its own notified set (kept in the shell's store, since the receiver may run in a fresh
  process) and less a synced copy's alerts (Q-16-2). It posts one notification per remaining alert, records the id and
  marks the row FIRED; dismissing the notification marks it DISMISSED. (d) That state update is the write guard's case
  (4). (e) The notification is `VISIBILITY_PRIVATE`: over a locked screen it shows that a calendar reminder fired, not
  the event's title (H7). Reason: the four gaps r3 D6 found; a forged poke can then do no more than make the receiver
  re-read the provider.
- 2026-09-30 (agent, r3 D7; V12): **grants in place, and `LocalCalendar` never makes a second calendar.** Setup's
  `calendar` row asks READ_CALENDAR only and WRITE_CALENDAR sits on Tess's row (Scope, Manifest ADD), so the Calendar app
  offers WRITE itself: the editor's save and Sync, when WRITE is not held, show `cal_notice` with the grant offered in
  place (phase 10 E18's form, as People's editor does in E13); no new Setup row. With READ held and WRITE not, start logs
  `[calendar] calendars: n (local missing: WRITE_CALENDAR)` when Tessera does not exist yet (`local present` when it
  does), and the views load. `LocalCalendar.id` is `find() ?: create()`, and `find` returns null on a FAILED query as on
  an empty one (`feeds/LocalCalendar.kt:24-34`), so with READ revoked and WRITE held every call would insert another
  Tessera — T16-2's "never a second" broken. Build task 3 (an ADD to phase 03's part, Change Log): `find` tells the two
  apart and `id` creates only after a successful, empty query; a failed lookup returns null (`[calendar] local calendar
  lookup failed: <err>`), Tess then answers "I don't have a calendar to add that to.", and the app does not call
  `LocalCalendar.id` at all while READ is denied. People's states (V12): `setup:people` reads MISSING when READ_CONTACTS
  is not held, whatever WRITE is, and PARTIAL when READ is held and WRITE is not — `pm revoke` is per permission, so
  "WRITE held, READ revoked" is reachable (E18) and reads MISSING.
- 2026-09-30 (agent, r3 D8; V5): **phase 14's Agenda pod.** The pod launches `SlotApp(Slot.CALENDAR)`
  (`start/podbay/PodBayPage.kt:303`, `StartActivity.kt:483-492`), so once this phase's seed has run it opens the shell's
  Calendar and logs `[podbay] launch agenda -> app.tileshell/.calendar.CalendarActivity` (`StartActivity.kt:515`'s form).
  Its rows are `CalendarFeed`'s `agenda`, so Birthdays events appear in it and a synced copy does not (Q-16-2); its empty
  and denied lines are unchanged. Phase 14 E3's launch clause requires exactly one APP_CALENDAR handler
  (phase-14-pod-bay.md, E3's "Launches"; `qa/phase-14/scripts/e3.sh:41`), which is false from this build on: its
  precondition becomes "the baseline's `slots.CALENDAR` is the shell's Calendar" and the expected component is read from
  the baseline file, never from the app — re-cut at this phase's build (the driver edit is task 8's; INDEX Change Log
  then); E1 carries the re-run. Hence `14` in depends-on.
- 2026-09-30 (agent, r3 D3): **the slot picker and resolver match the handler's component, not its package.**
  `SlotResolver.candidates` keeps every catalog entry whose PACKAGE handles the category, and `resolve`'s category branch
  ends in `firstForPackage` (`tiles/SlotResolver.kt:39,43-45`); the picker's rows are tagged by package
  (`start/SlotPicker.kt:55`). Once the shell declares APP_CALENDAR and APP_CONTACTS, both pickers would list every shell
  app under one tag, `slot_candidate:app.tileshell` (MUSIC's picker does today). Build task 1 (phase 01's part, Change
  Log): `candidates` and the category branch of `resolve` match the handler's COMPONENT (package + activity name), and the
  tag becomes `slot_candidate:<flattened component>`; the phase 01 driver that taps the old tag
  (`qa/phase-01/scripts/e4_part2.sh`) is re-cut by task 8. E1 asserts exactly two candidates in each of the two pickers.
- 2026-09-30 (agent, r3 D4): **≡ show / hide is the shell's own state.** The pane's ticks live in a `hidden` list in the
  shell's own store, keyed like `allowed` (`_ID` + account name + account type) — never `Calendars.VISIBLE`: writing
  that column on an account calendar is a write rule 1 and the write guard forbid. It filters the Calendar app's views
  only; the tile, the Agenda pod, Tess and the reminders still read every calendar. E22 asserts Work's `visible` column
  unchanged after Work is hidden.
- 2026-09-30 (agent, r3 D5): **"Can sync to" lists only calendars the phone may write.** Its rows are the non-LOCAL
  calendars whose `calendar_access_level` ≥ 500 (CONTRIBUTOR); a read-only calendar is never listed, so it can never be a
  Sync target. The write layer re-reads the level before every push, update and delete-"both" and refuses below 500 —
  "That calendar is read-only now" (`cal_notice`; approximation, H20) and `[calendar] sync event=<id> -> calendar <id>:
  failed calendar read-only` — because the provider may accept a normal app's insert whatever the level, and the copy
  would then sit dirty and never upload (Verify at build start 2). Display names repeat (Exchange's is "Calendar"), so
  the rows of "Can sync to" and of the Sync picker sit under their account's name (`cal_account:<name>` headers, the
  pane's form), and the marker names the account with the calendar ("synced to <calendar>" with a `cal_account:<name>`
  node beside it).
- 2026-09-30 (agent, r3 D11): **one activity per app.** `.calendar.CalendarActivity` and `.people.PeopleActivity` —
  launcher activities, `singleTask`, own task affinities, `onNewIntent` re-routing a tile tap, an App Shortcut or an
  outside intent into the running app (phase 15's form, `ClockActivity`, `AndroidManifest.xml:146-160`) — each host every
  page of their app and carry every intent filter Scope names. Back on Start counts the shell's catalog apps only
  (`start/BackHistory.kt:40-41,64-65,75`; L14-2); these two are catalog apps, and any helper activity a task adds is
  non-launcher, outside the catalog, and does not count.
- 2026-09-30 (agent, consequences of Q-16-3; r3 D13): **the People write guard and the "Can edit" list.**
  (1) Editable is decided per raw contact: a phone-only raw contact always — one in the device's local account, which is
  a null `account_name` and `account_type` on AOSP; the guard compares against what
  `ContactsContract.RawContacts.getLocalAccountName` / `getLocalAccountType` return (null on AOSP), never a literal, so a
  phone whose maker names its local account is covered (Verify at build start 4; P8); a raw contact whose account is on
  the "Can edit" list; nothing else. Work accounts, every account
  not ticked, and a work profile's contacts found by the enterprise search (another profile's rows) are read-only.
  (2) The list: People's settings page "Can edit" (`people_page:can_edit`), a checkbox list in "Can sync to"'s form, lists
  the accounts the Contacts provider names — the distinct non-null `account_name` + `account_type` pairs of
  `raw_contacts`, read through the provider (no `GET_ACCOUNTS`, no AccountManager read) — every one unticked at first. It
  is stored in People's own `people_edit.json` (files dir, temp-file-and-rename like `calendar_sync.json`), keyed on
  account name + type; an account the provider no longer names is dropped from the file, so a removed and re-added
  account starts NOT allowed.
  (3) One guard, as the calendar has: every `ContactsContract` insert, update and delete People makes goes through one
  write layer, which resolves the raw contacts the op touches and refuses when any is not editable (`[people] write <op>
  raw=<id>: refused (not allowed)`). Its cases: a new contact goes to the phone unless the editor's account choice
  (`people_editor_account`, which offers "Phone" and the allowed accounts only) names an allowed account — this replaces
  "the account the filter default names"; a SIM import goes to the phone; an `ACTION_INSERT` prefill starts on the phone
  and ignores any account its caller names; data rows (fields, photo, group membership) are written only on an editable
  raw contact; deleting a contact (the aggregate, so every raw contact behind it) and updating a `Contacts` column that
  syncs upstream (STARRED, for example) count as writes to every raw contact behind the aggregate and are refused unless
  EVERY one is editable; Link / Unlink write `AggregationExceptions`, which are local to the phone and reach no account,
  and stay allowed on any contact; groups follow the same rule by the group's account.
  (4) The card: a contact with no editable raw contact shows no edit and no delete action (`people_card_edit` and
  `people_card_delete` absent) and one line, `people_card_readonly` — "This contact is in <account>. To change it, allow
  that account in Can edit." (approximation, H21) — whose tap opens the "Can edit" page. A contact with some editable raw
  contacts and some not shows Edit — the editor opens the editable raw contacts' fields and shows the others read-only —
  and no Delete (approximation, H21). The exported `ACTION_EDIT` on a read-only contact opens that card, never the editor
  (`[people] edit <lookup>: refused (account not allowed)`).
  (5) Proof: a JVM test of the guard — each allowed case (phone-only; an allowed account; Link / Unlink on a read-only
  contact) and each refusal (insert, update and delete of a raw contact and of its data rows in an account not allowed;
  the aggregate delete and the STARRED update with one raw contact not editable; a new contact, a SIM import and an INSERT
  prefill naming an account not allowed; a group op in an account not allowed) — and E28 on the AVD, P8 on the phone; the
  Trust list names the guard.
  Reason: Jeremy's "never" for the work calendar, carried to contacts by his Q-16-3 "(A)" — only an opt-in list makes a
  mis-tap on a work contact impossible.
- 2026-09-30 (agent, r3 D10; Q-16-3): **Trust.** These parts get an adversarial team review (team-review, adversarial
  mode) recorded under qa/phase-16/ before `done`; build-prompt.md's trust list, which names only the calendar write
  guard for this phase today (`build-prompt.md:21`), gains them at the build: (a) the calendar write guard (four cases,
  Tess's delete included); (b) `WRITE_CONTACTS` and the People write guard with its "Can edit" list (Q-16-3); (c) the
  exported VIEW / EDIT / INSERT / PICK handlers on the two activities — an INSERT prefill never saves without the user's
  tap and ignores a `calendar_id` extra (the event goes to Tessera) and any account a contact INSERT names (the contact
  goes to the phone); EDIT on a non-Tessera event opens it read-only and EDIT on a read-only contact opens its card; PICK
  grants its caller the one contact or phone URI picked and nothing else; (d) the reminder receiver (exported; a poke
  only); (e) the Sync path and `calendar_sync.json` (its `allowed` list is the only gate to an account calendar); (f) the
  vCard share — the Contacts provider's own stream, `ContactsContract.Contacts.CONTENT_VCARD_URI` + the lookup key, with a
  read grant for that one URI; no provider or file of the shell's; (g) the reminder notification over the lock screen
  (`VISIBILITY_PRIVATE`).
- 2026-09-30 (agent, r3 V3 / V8 / V9 / V14 / V16 / V21 with D3, D5, D7 and the three rulings): **Harness, round 3.** Tags
  added: `cal_event_page:<id>` (the event page's root — every "no `cal_event_action:*`" read first asserts it, so a missed
  tap cannot pass), `cal_event_time:<id>` (the time text on its own node), `cal_allday:<yyyy-mm-dd>` (the Day view's
  all-day band, which holds that day's all-day `cal_event:` nodes), `cal_notice` and `people_notice` (the page's notice
  line: a node that stays until the page changes, never a toast), `cal_account:<name>` also over the rows of "Can sync
  to", in the Sync picker and beside `cal_synced_marker:<id>` (D5), `slot_candidate:<flattened component>` (D3; phase
  01's tag, was `:<package>`), `people_card:<lookup>` (the card's root), `people_card_edit`, `people_card_delete`,
  `people_card_readonly`, `people_page:<list|editor|can_edit>` (was `<list|editor>`), `people_can_edit:<account
  type>:<account name>`, `people_editor_account` and `people_editor_account_row:<phone | account type:account name>`
  (Q-16-3). Lines added or widened: `assignSlotOnce <marker> <slot> -> <component> -> assigned, replaced user's
  <component>` (Q-16-1); `[calendar] calendars: n (local missing: WRITE_CALENDAR)` (D7); `[calendar] counts: <account
  name>/<displayName>=<n>, …` — every calendar's event count, written each time `cal_pane` opens (V3; with the account
  name because display names repeat); `[calendar] sync … : failed calendar read-only` (D5); `[calendar] reminder
  event=<id> minutes=<n>: skipped (synced copy)` (Q-16-2); `[people] write <op> raw=<id>: refused (not allowed)` and
  `[people] edit <lookup>: refused (account not allowed)` (Q-16-3); `[people] tile event <n> t0=<uptime> lookup=<key>`
  (V14); `[people] share <lookup>: <uri>` (V16); `[people] action <call|text|mail|map> -> <component> <uri>` (V21);
  `[motion] people_pivot t0=<uptime> peak=<ms> overshoot=<%> settle=<ms> frames=<n> maxGapMs=<ms>` (V14). The driver
  floor is `qa/phase-16/scripts/p16.sh` (build task 8; Acceptance preamble).
- 2026-09-30 (agent, r3 triage): **Verify at build start.** Some fixes above rest on Android facts given from memory, not
  checked on a device. Each is checked on the AVD before the task that depends on it is built, and the result recorded
  under qa/phase-16/BUILDSTART/ (phase 14's form). The triage's three: (1) the reminder broadcast's data URI form (D6:
  `content://com.android.calendar/<alarmTime>` with an `alarmTime` extra, or `/time/<ms>`) — the receiver's scheme + host
  filter must match what the provider sends; (2) whether the provider gates a normal app's insert on the target
  calendar's `calendar_access_level` (D5) — the write layer's re-read is built either way, and the answer decides whether
  the read-only edge case can expect a provider error at all; (3) whether CalendarProvider drops non-LOCAL calendars whose
  account does not exist when its process restarts (V11) — if it does, a Sync row creates its `mkcal` calendars after its
  last restart, never before. Two more, for facts this round's own text rests on: (4) what a phone-only contact's account
  is — null name and type on AOSP; a phone maker may name its local account
  (`ContactsContract.RawContacts.getLocalAccountName` / `getLocalAccountType`), and if the S25 does, that account is
  "phone-only" for Q-16-3 (P8 checks it on the phone); (5) that the provider expands
  `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1` to February's last day (D14; E17 asserts it — if it does not, the 29 February
  form is re-cut before task 3 and Jeremy is told).

## Interview queue (Stage A step 4)
Ask one at a time, in this order.

1. ~~What People is~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **What People is.** Phase 06 ruled a W10M People app OUT of its scope ("Phone book opens phase 01's People slot app") and
   the 2026-09-22 scope add puts People in. Its shape:
   A. **(lean)** A full W10M People hub over Android's Contacts provider — list, card, create / edit / delete, photo, link /
      unlink, SIM import, share — and it takes the PEOPLE slot, so the People tile, Phone's "Phone book" and every contact
      hand-off inside the shell open it. Samsung Contacts stays installed and untouched; the same contacts show in both.
   B. A read-mostly hub: list, card and the call / text / mail / map actions; "Edit" hands the contact to whatever other
      contacts app the phone has (a visible Android seam on every edit).
   C. The full app, but it does NOT take the PEOPLE slot: reachable from the app list only; the tile and Phone book keep
      pointing at the phone's own contacts app.
   D. Other / let me clarify.
2. ~~Where events live~~ RULED 2026-09-23: D, Jeremy's own design (see Decisions). Original question kept below.
   **Where Calendar's events live.** The AVD has no calendar at all (R10 testability 18) and the phone has Samsung's and any
   account's; phase 01's tile and Tess's calendar commands both read CalendarProvider today.
   A. **(lean)** Android's CalendarProvider, every calendar on the phone (synced ones included — their sync is other apps'
      work, not an internet use of the shell), plus one local calendar the app creates when the phone has none.
   B. Local only: the shell's own calendar in the provider, and the app ignores every other calendar (the tile and Tess keep
      seeing all of them, since they read the provider unfiltered).
   C. The shell's own store outside the provider — then the tile and Tess would NOT see its events without a second reader.
   D. Other / let me clarify.
3. ~~Birthdays~~ RULED 2026-09-23: A (see Decisions). Original question kept below.
   **Birthdays.** W10M's Outlook Calendar showed a Birthday calendar fed from People.
   A. **(lean)** In, as a read-only local calendar the app keeps in step with contacts' birthdays, so the Calendar tile and
      Tess's "what's on my calendar" see them too.
   B. In, shown inside the Calendar app's views only (not written to the provider; the tile and Tess do not see them).
   C. Out.
   D. Other / let me clarify.

## Build tasks
Ordered so the slot seeding and its guard (the one change to shipped code that can destroy a user's choice) land first and
are proven before anything depends on the slots. Before task 1 (added 2026-09-30): the five checks of Decisions "Verify
at build start" are run on the AVD and recorded under qa/phase-16/BUILDSTART/.
1. **Slot seeding and the guard (touches phase 01).** Precondition: phase 15's build task 0 (the live-tile routing fix) is
   built and its Change Log entry written (Decisions; C-1 — was "phase 17's build task 2", SUPERSEDED 2026-09-23). Then the
   `assignSlotOnce` guard in `LayoutStore` (`tiles/LayoutStore.kt:127-139`: keep an existing explicit assignment, record
   the marker, log `kept user's`; C-2 keeps it here) as the default for every marker, with its JVM test; the `takeOver`
   argument (Q-16-1, 2026-09-30) that writes the slot over an existing explicit assignment once and logs `-> assigned,
   replaced user's <earlier component>`; markers for CALENDAR and PEOPLE claimed from `ShellApp` as `claimMusicSlot` does
   (`ShellApp.kt:64,151`; cite fixed 2026-09-30, r3 D15, was `:122,185`), these two passing `takeOver` and
   `slot:music:v1` not; `SlotResolver.candidates` and the category branch of `resolve` matching the handler's component,
   and the picker's tag `slot_candidate:<flattened component>` (r3 D3); the phase 01 E4 / E4b re-cut, the upgrade leg and
   the guard leg (E1). INDEX Change Log line.
2. **App identities and contracts.** Two launcher activities with their categories and intent filters — one per app,
   `.calendar.CalendarActivity` and `.people.PeopleActivity`, `singleTask` with `onNewIntent`, hosting every page; any
   helper activity non-launcher (r3 D11) — and the handler rules of the Trust line (an INSERT prefill saves nothing
   without a tap and ignores a `calendar_id` extra or a named account; EDIT on what the shell may not write opens it
   read-only; PICK grants one URI; r3 D10); app-list entries;
   Uninstall exclusion; exported allow-list ADDs (the two activities and the reminder receiver, r3 D6);
   `testTagsAsResourceId`; `WRITE_CONTACTS` in the manifest; People's own
   Setup row `setup:people` (READ + WRITE, `partialIsDone` false, MISSING whenever READ is not held — r3 V12; an ADD to
   phase 01's `Checklist.rows`, `onboarding/Checklist.kt:104-160`, Change Log; cite fixed 2026-09-30, r3 D15, was
   `:89-118`) with Tess's `contacts` row left as it is (T16-15; was "Tess's `contacts`
   checklist row … asking READ + WRITE …", SUPERSEDED 2026-09-23); C-4: its phase 12 why line (the T16-15 line's text),
   `qa/phase-03/scripts/provision.sh` gains `adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS`, phase 12
   E1 re-run on this build (E26); the APK size before.
3. **Calendar data layer.** The local calendar is phase 03's `LocalCalendar.id(context)` reused
   (`feeds/LocalCalendar.kt:24`, J6) — no second creator (T16-2) — called at start; the calendar list with colours, every
   calendar but the local one read-only; windowed `Instances` queries per view; event write / edit / delete with recurrence
   exceptions on the local calendar only, behind the ~~three-case~~ four-case write guard (T16-11 with the r3 D1 / D6
   line, 2026-09-30) with its JVM test (the
   T16-1 guard SUPERSEDED 2026-09-23); Sync — the
   `calendar_sync.json` mapping and `allowed` list, the copy insert / update / recreate, recurring copies with exceptions and
   reminders, the delete choice, the removed-calendar refusal, the copy's `calendar_id` re-read before an update or a
   delete-"both" (T16-12, `failed mapping stale`) — and its `[calendar] sync` lines; the `ACTION_EVENT_REMINDER`
   receiver and its notification channel; the Birthdays calendar (Q3) under its own account name `Tessera Birthdays`, with
   no reminder rows, and `[calendar] birthdays calendar could not be created: <err>` when the provider refuses (T16-19); the
   `[calendar] calendars: denied (READ_CALENDAR)` line (T16-16) and the `in <ms> ms` of each `view` line (T16-17).
   Added 2026-09-30 by round 3, each as its Decisions line says: Tess's delete through the guard, matched in Tessera only
   (D1; an ADD to phase 03); the one reader of `calendar_sync.json`'s copy ids, used by the views, by `CalendarFeed`'s
   tile and `agenda` queries, by `ActionLayer.calendarEvents` and by the receiver (Q-16-2; ADDs to phases 01, 03 and
   14); the ≡ pane's `hidden` list in the shell's own store, never `Calendars.VISIBLE` (D4); "Can sync to" from
   calendars with access level ≥ 500 and the level's re-read at push, `failed calendar read-only` (D5); the receiver as
   specified — data filter, exported, poke, re-read with the notified set, alert-state writes, `VISIBILITY_PRIVATE` (D6);
   `LocalCalendar.find` telling a failed query from an empty one, `id` creating only after an empty one, no call while
   READ is denied, and the `local missing: WRITE_CALENDAR` line (D7; an ADD to phase 03); the Birthdays writer started
   from `ShellApp.startFeeds`, with the no-year and 29 February forms (D14); Sync comparing the copy with the local event
   instead of a stored hash (V13); the `[calendar] counts: …` line (V3).
4. **Calendar app.** W10M's views (T16-13): the header (≡, the month and year in caps with ⌄, K1.1) opening the month
   drop-down (K5), Agenda with the 15063 week strip over day groups (K2–K3; event rows with the 8-epx bar at x 0 and the
   title at x 92.5), the Week view (K4.1–K4.4), the Day view (U1's approximation), all on #1A1A1A (K1.3); the ≡ calendar
   pane with account headers and colour-filled checkboxes (K6.4); the app bar Today · New · View · … with the View list
   Agenda / Day / Week (K1.5, K6.1–K6.2); day paging, the date picker, Today, the editor (U3's approximation: People's
   32-epx fields) and its fields (its
   calendar is always the local one; no picker offers another), the event page's actions (`cal_event_action:*`: edit and
   delete on local events only, Sync on local events), the Sync picker and the "synced to" marker, the first Sync routed to
   "Can sync to" when nothing is allowed (T16-3), the settings page (first day of week, "Can sync to" as a checkbox list
   styled like the ≡ pane, K6.4; U6's leaf-page approximation), every value from r11/calendar.md with its tolerance; phase
   03 E2's calendar rows re-run (E9). Added 2026-09-30: the event page's root `cal_event_page:<id>` with its time on
   `cal_event_time:<id>`, the Day view's `cal_allday:<date>` band and the `cal_notice` line (V8); the event page of a
   synced event carrying the "synced to <calendar>" marker with its account, and no row anywhere for the copy (Q-16-2);
   "Can sync to" and the Sync picker grouped under `cal_account:<name>` (D5); the editor's save and Sync offering
   WRITE_CALENDAR in place (D7).
5. **People data layer.** Provider reads with phonebook labels, search (plus the enterprise filter), the card's data kinds,
   writes under `WRITE_CONTACTS`, aggregation exceptions, the SIM (`content://icc/adn`) reader, ~~the vCard writer for
   share~~ share through the Contacts provider's own vCard stream (`Contacts.CONTENT_VCARD_URI` + lookup key, a read grant
   on that URI; r3 D10 — no writer, provider or file of the shell's);
   groups through `ContactsContract.Groups` and `GroupMembership` data rows under the new-contact account rule (T16-14).
   Added 2026-09-30 (Q-16-3): the People write guard — the one layer every `ContactsContract` write goes through —
   `people_edit.json` and its "Can edit" list, the per-raw-contact editable rule, new contacts and SIM imports to the
   phone unless an allowed account is chosen, the all-raw-contacts rule for an aggregate's delete and upstream columns,
   Link / Unlink always allowed, and the guard's JVM test (the People write guard line).
6. **People app.** The CONTACTS / GROUPS pivot header (r11/people.md P1.1, less What's New); the list (50-epx rows, 32-epx
   avatar, accent letter headers, P1.5–P1.10) with People's own jump grid (72-epx cells, P2.1–P2.5; was "the jump grid
   (X8)", SUPERSEDED 2026-09-23 by T16-13), the search box (P1.3), the full-accent card and its titled action rows (§4),
   the editor with 32-epx fields and "+ field" rows (§5) and the photo picker, link / unlink, SIM import, filter contact
   list; the Groups pivot — create, rename, delete, the group's member list, "Text the group" (T16-14); the
   explicit-component hand-offs from phase 06's buttons (~~an ADD to phase 06's targets when it is built, or~~ a targeted
   intent contract recorded now — phase 06 builds after this phase, so only the contract stands, r3 D15: "Phone book"
   opens the PEOPLE slot app as Scope says; History's contact-card
   button starts `app.tileshell/.people.PeopleActivity` by component with `ACTION_VIEW` on the contact's lookup URI, and
   Messaging's "Contact" attach starts it by component with `ACTION_PICK`; phase 06's doc takes this as its target when it
   is built); every
   value from r11/people.md with its tolerance. Added 2026-09-30 (Q-16-3): People's settings page with "Can edit"; the
   editor's account choice ("Phone" and the allowed accounts); the card's Edit and Delete only where the guard allows,
   and the `people_card_readonly` line with its route to "Can edit"; the notices on `people_notice` (V8); the card
   actions' `[people] action …` line (V21) and the pivot's `[motion] people_pivot` line (V14).
7. **People tile (touches phase 01).** `PeopleFeed`, the A9 bubble face and the static pattern, published under the PEOPLE key
   (`LiveTileEngine.PEOPLE` and the `Slot.PEOPLE` branches in `StartPage`, ADDs; r3 D12) and, like `CalendarFeed`'s face,
   under its app's component key so a pinned app tile is live,
   with the `[people] tile event` line (now naming the contact, `lookup=<key>`, V14) and `[motion]` lines (C-5) and
   `[people] tile: photo <lookup> skipped: <why>` for a photo that fails to decode (T16-19) — the skip is a pure function
   with a JVM test, since no fixture can store an undecodable photo (V6); INDEX Change Log line; E11 measures it.
8. **Diagnostics, regression, evidence.** Every diagnostics line in Decisions, the app-list regression run, the APK size
   after. The phase baseline (C-3): `qa/phase-16/baseline_layout.json` derived from the newest baseline on disk at build
   (phase 15's), with `addedOnce` gaining `slot:calendar:v1` and `slot:people:v1`, `slots` gaining CALENDAR and PEOPLE → the
   shell's two activities (what the seed writes), `manualSizes` kept; the file it came from kept beside it as
   `qa/phase-16/baseline_layout-pre-16.json`. The pre-16 APK for E1's upgrade pass kept at `qa/phase-16/upgrade/<tag>.apk`
   with its git tag in the row's log (T16-7). `qa/phase-03/scripts/lib.sh:24` becomes
   `APK="${TILESHELL_APK:-$REPO/app/build/outputs/apk/debug/app-debug.apk}"`, so `provision.sh` can install the old build
   for E1's upgrade leg (C-19; this task owns the line; the default is unchanged for every other row). J6's driver lookup
   of the local calendar keyed on `account_name=Tessera` so E9 can re-run it on this build (already so in the driver:
   qa/phase-03/scripts/j6.sh:40-41 finds it by name — the "reads the first `account_type=LOCAL` row" this task once named is
   gone). Added 2026-09-30 by round 3: **(a) `qa/phase-16/scripts/p16.sh`** (V9, V10), sourced by every driver after
   `lib.sh`, carrying copies of `gdump` (`qa/phase-15/scripts/p15.sh:24-49`), `jump_clock` / `clock_restore`
   (`p15.sh:57-78`), `shell_bytes` (`qa/phase-15/scripts/clock.sh:423-436`), `absent_in` and `c6`
   (`qa/phase-14/scripts/p14.sh:156-162`, `:29-35`) and `mkcal` (it lives inside `qa/phase-03/scripts/j6.sh:18-20` and
   cannot be sourced), and new: `cal_lists <id>` (asserts a calendar's `_id` still lists — V11), `cal_fixtures_down`
   (deletes the QA, Personal and Work calendars through the sync-adapter URI, the test events, and the Birthdays calendar
   with `adb shell "content delete --uri
   'content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=Tessera%20Birthdays&account_type=LOCAL'
   --where \"account_name='Tessera Birthdays'\""`), `people_fixtures_up` / `people_fixtures_down` (the contact fixtures of
   E10–E15, E27 and E28, inserted as phone-only raw contacts unless a row names an account, their raw-contact ids kept in
   `$ROW_DIR/people-fixtures.ids` and deleted by id — never by account, so provision.sh's Mom, `qa` / `qa`,
   `provision.sh:125-126`, is never touched) and `shell_alarms_pending` (`clock_pending`'s form, `p15.sh:82-91`, counting
   every pending alarm that names `app.tileshell`; V7). **(b) Fixtures:** solid-colour JPEGs for E11 and E13 (V14) and
   TestDPC for E16 under `qa/phase-16/fixtures/` (not committed; never in `$HOME/android-fixtures`, which `provision.sh`
   installs into user 0 for every phase — V17). **(c) Re-cut drivers:** `qa/phase-14/scripts/e3.sh`'s Agenda-launch
   precondition (D8), `qa/phase-01/scripts/e4_part2.sh`'s `slot_candidate:` tag (D3), and E26's own driver
   (`qa/phase-12/scripts/e14.sh:29,31` hard-codes "Step 1 of 2" and one missing step — V1). **(d) The EDGE row's**
   `qa/phase-16/scripts/edge.sh` and `edge_index.tsv`, and E21's `producers.tsv` / `notrun.tsv` (V6).
9. **App Shortcuts (phase 11 Q1's standing rule; C-8).** A static `shortcuts.xml` per app, ranks 0–3, each targeting its
   activity with the page extra (`page`, the key `SettingsActivity.EXTRA_PAGE` already uses,
   `settings/SettingsActivity.kt:118`): Calendar — ids `agenda`, `day`, `month`, `new_event` (Agenda, Day, Month, New
   event; `month` opens Agenda with the month drop-down open, T16-13); People — `contacts`, `new_contact`, `groups`
   (Contacts, New contact, Groups; `groups` at rank 2, T16-14). E25 proves them.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps follow RV13,
except Start while the People tile cycles (E1's Music negative once photos exist, E11), which never idles and is dumped
through phase 05's gesture driver (`UiDevice.dumpWindowHierarchy` with `Configurator.setWaitForIdleTimeout(0)`,
qa/phase-05/README.md; C-10). **Motion clock (C-5, C-31):** every motion the shell animates logs `[motion] <name> t0=<uptime>
peak=<ms> overshoot=<%> settle=<ms> frames=<n> maxGapMs=<ms>` from `withFrameNanos` (the People tile also `[people] tile
event <n> t0=<uptime>`), and a motion row asserts the logged numbers against RV11's tolerance and `maxGapMs` ≤ 33.4 ms (two
vsyncs, so jank fails on the shell's own clock); a screenrecord corroborates under phase 05's
frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion) and is never the primary clock. **Layout seeding
(C-3):** a row that needs Start's layout starts with phase 02's verified `layout_restore qa/phase-16/baseline_layout.json`
(qa/phase-02/scripts/layout.sh). **Wiped state (C-4):** every row that clears the shell reads `pm clear app.tileshell` →
`qa/phase-03/scripts/provision.sh` → Home (`ensure_start`, Round 3 floor below), or it meets phase 12's `wizard_page`.
**After a launch (C-6):** a row that opened
an app runs `am force-stop app.tileshell` + Home (`c6`, then `ensure_start`) before its next assertion on Start's grid (the promoted recent app is in
memory only, qa/phase-01/scripts/recent0922.sh:19-21). **Recorded clauses (C-13, C-26):** a recorded clause uses `lib.sh`
`record <name> <value>` (prints `RECORD <name> <value>` and counts apart from PASS / FAIL; `row_end` reports "recorded only
(<n> facts)" when a row asserted nothing else), never an assert (was "ends its PASS/FAIL line with 'RECORDED'", SUPERSEDED
2026-09-23 by C-26). **Ring slices (C-20):** every line this phase writes is in the launcher ring (Harness contracts);
every ring assertion reads `ring_since <MARK> launcher` from a MARK (`ring_mark`, `adb shell date +%s%3N`) taken immediately
before the step's action (after any clock jump, so the MARK is on the new clock); absence assertions read the same slice;
`reply_text` is `reply_since <MARK>`; `row_end` saves the slice to `<row>/ring-launcher.txt` (the helpers are phase 11's
build task 7; the `wall=` filter is `qa/phase-03/scripts/j7.sh:13-15`'s). **Wake after sleep (C-25):** after any `adb
reboot` (boot-completed poll), `dumpsys battery unplug` or `KEYCODE_SLEEP` step the driver calls `wake_device`
(`lib.sh:49-58`; cite fixed 2026-09-30, r3 D15 / V9, was `:47-56`) and asserts it printed `Awake` before the next tap.
**Round 3 floor (2026-09-30; r3 V9, V4, V10, V11, V20 — this preamble predated phases 11–15 as built):** every driver
sources `lib.sh` and then `qa/phase-16/scripts/p16.sh` (build task 8). **Home** in any row is `ensure_start`
(`lib.sh:290-310`: Home, then Back while the dump shows the app list or the pod bay — a right swipe on Start now opens
phase 14's pod bay — and Start ALONE asserted), never a bare `KEYCODE_HOME`; C-6 after a launch is `c6`, then
`ensure_start`. **A screen that never idles** — Start while the People tile cycles (E1, E11, E25's bursts) — is dumped with
`gdump`. **Clock moves** are `jump_clock <epoch-ms>` and `clock_restore`. A row never jumps the clock BACKWARDS:
`ring_since` keeps every line whose `wall` ≥ the MARK (`lib.sh:196-197`), so after a backwards jump every earlier line
passes the filter and a stale line can pass the row; a step that must go backwards (the Edge case "the clock jumped
backwards a year") runs `ring_save`, `am force-stop app.tileshell` and `ensure_start` before its MARK. **Absence** in a
ring slice is `absent_in` (the slice must hold a `wall=` line first). **Restores and independence (RV12):** every row
ends by restoring what it made — `cal_fixtures_down`, `people_fixtures_down`, a `pm grant` for every permission it
revoked, `clock_restore` and the recorded time zone, `pm clear-package-preferred-activities` for an "Always" it set,
`layout_restore` of the baseline for a pin or a slot it changed — and no row depends on another row's fixtures: a row
that needs contacts, photos or a birthday makes them itself (`people_fixtures_up`). **Calendar counts (V11):** every count
read from the provider first asserts with `cal_lists <id>` that the calendar still lists — "Work 0" is also what a
vanished Work calendar reads — and E23 / E24 keep E22's "Offsite" sentinel (Work holds exactly Offsite). **Tess steps
(V20):** requests are typed with `type_request`, which cannot carry an apostrophe (`lib.sh:347-348`), so rows type "what
is on my calendar" (`cortana/match/CommandMatcher.kt:101`); a confirmation is a tap on `cortana_card_button:confirm`
(`j6.sh:35-37`), never a spoken "yes"; no row uses the microphone or the host's audio.
Drivers source docs/plan/qa/phase-03/scripts/lib.sh (symlinked into qa/phase-16/scripts as phase 01 did); contact fixtures are
inserted as qa/phase-03/scripts/provision.sh inserts them (`content insert` on `raw_contacts` then `data`); "diagnostics" is
read with phase 01's command. **AVD limits, stated up front:** the AVD has NO calendar and no account
(`content query --uri content://com.android.calendar/calendars` → "No result found") except the shell's own `Tessera`
calendar once J6's row or any Tess "add" has run (a row that needs it absent first deletes it: `content delete --uri
"content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=Tessera&account_type=LOCAL" --where
"account_name='Tessera'"`), so calendar rows either rely on the app's local calendar (E3) or insert a QA calendar first
(`content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=qa&account_type=LOCAL"
--bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:QA --bind calendar_displayName:s:QA --bind calendar_access_level:i:700
--bind ownerAccount:s:qa --bind visible:i:1 --bind sync_events:i:1`) and delete it after — every command whose URI carries
`&` goes to `adb shell` as ONE quoted string, j6.sh's form, because an unquoted `&` backgrounds it on the device and nothing
is written (J6 run 1); the clock is jumped with
`adb shell cmd alarm set-time <epoch-ms>` after `settings put global auto_time 0` (root + `date` if the image refuses) and
restored per RV12 (these are `jump_clock` and `clock_restore`, Round 3 floor); the time zone with `cmd alarm set-timezone
<zone>` (restore to the recorded `getprop persist.sys.timezone`);
`pm list users` shows user 0 only, so the work-profile row creates a managed profile with phase 01 E18's commands and removes it;
the emulated SIM's phonebook (`content://icc/adn`) may refuse inserts — E15 (was written "E10") `record`s that, still
runs the import (V16), and a stored SIM contact's import moves to P4; the Sync
rows' two account calendars are created as qa/phase-03/scripts/j6.sh's `mkcal` creates them (account type `com.google`
through the sync-adapter URI, access 700; `p16.sh` carries the function, since j6.sh's cannot be sourced — V9) and deleted
the same way at the row's end (`cal_fixtures_down`); whether such account-less calendars survive a provider restart is
Verify at build start 3. Geometry rows assert R11's values with
R11's tolerances (T16-13; was "R11-gated: until r11/calendar.md / r11/people.md land …", SUPERSEDED 2026-09-23 — R11
landed); the AVD (1080 px) is 360 epx wide, the width r11's 360-epx captures were measured at, so r11/people.md's 4-column
jump grid and r11/calendar.md's W/7 strip apply as measured. The bars are asserted against phase 01's
`BarMetrics.STATUS_EPX` and `BarMetrics.NAV_EPX` (C-17), never a literal; a value R11 measured under a 24-epx status bar is
asserted relative to the drawn bar's bottom edge.
**Emulator:**
- E1 Slot takeover and the guard (**re-cut of phase 01 E4 / E4b**, recorded in the INDEX Change Log when built): on a
  wiped state (`pm clear app.tileshell` → `qa/phase-03/scripts/provision.sh` → `ensure_start`; C-4), the CALENDAR and PEOPLE slot
  tiles read "Calendar" and "People" and their taps resume the shell's Calendar and People activities (`dumpsys activity
  activities | grep -i resumed` shows `app.tileshell/.calendar.CalendarActivity` and `…/.people.PeopleActivity`; `c6` +
  `ensure_start` after each, C-6), ~~diagnostics show `assignSlotOnce
  slot:calendar:v1 CALENDAR -> app.tileshell/<the Calendar activity> -> assigned` and the PEOPLE line,~~ (STRUCK
  2026-09-30, r3 V2: unobservable here — the ring is in memory, `diag/Diagnostics.kt:14-26`; `provision.sh` starts the
  process before its `am force-stop` at `:160`, and the process that survives logs `already run`,
  `tiles/LayoutStore.kt:137`) `layout_json` shows `addedOnce` ⊇ {`slot:calendar:v1`, `slot:people:v1`} and
  `slots.CALENDAR` / `slots.PEOPLE` = the shell's two components, and `cmd package
  query-activities -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR` (and `APP_CONTACTS`) lists two
  handlers each (the AOSP fixture and the shell), so neither auto-assigns; Settings > Tile apps (`settings/TileAppsPage.kt:14`;
  was "Settings > Start"): `tile_app_slot:CALENDAR` opens a picker holding EXACTLY two `slot_candidate:` nodes —
  `slot_candidate:app.tileshell/.calendar.CalendarActivity` and the AOSP Calendar's — and `tile_app_slot:PEOPLE` exactly
  the shell's People and the image's Contacts, no other shell app in either (r3 D3); a tap on the AOSP Calendar's
  candidate re-points CALENDAR and it sticks across `am force-stop` and `adb reboot`
  (E4b's form; after the reboot's boot poll, `wake_device` asserting `Awake`, C-25; `layout_json` `slots.CALENDAR` = the
  AOSP component after each). **Upgrade pass (C-19; re-cut 2026-09-30 by Q-16-1 and r3 V2):** `adb uninstall
  app.tileshell`; `TILESHELL_APK=qa/phase-16/upgrade/<tag>.apk qa/phase-03/scripts/provision.sh` (the last pre-phase-16
  build, kept there with its git tag in the log, T16-7 — installed with every grant and the wizard's finished marker, C-15);
  `ensure_start` shows `start_page` and no `wizard_page` (asserted, so the leg starts where a user's phone would); assign PEOPLE to the
  image's Contacts through Settings > Tile apps (dump; on the old build the candidate's tag is `slot_candidate:<package>`),
  and `layout_json` shows `slots.PEOPLE` = the image's Contacts component — the hand pick is proven explicit before the
  update — and no `slots.CALENDAR`; a MARK; `adb install -r app/build/outputs/apk/debug/app-debug.apk` (NOT
  `provision.sh`, so the upgrade path runs and no force-stop follows the new process's first start), asserted to succeed
  — the same debug key; `INSTALL_FAILED_UPDATE_INCOMPATIBLE`
  fails the row loudly; `ensure_start` → ~~the PEOPLE tile still reads the image's Contacts and the launcher ring slice shows
  `assignSlotOnce slot:people:v1 PEOPLE -> kept user's com.android.contacts/…`~~ (SUPERSEDED 2026-09-30 by Q-16-1) the
  PEOPLE tile reads "People" and its tap resumes `app.tileshell/.people.PeopleActivity`; the ring slice from the MARK
  holds `assignSlotOnce slot:people:v1 PEOPLE -> app.tileshell/.people.PeopleActivity -> assigned, replaced user's
  <the image's Contacts component, equal to the one read from `layout_json` before the update>` and, for the CALENDAR
  slot the user never touched, `assignSlotOnce slot:calendar:v1 CALENDAR -> app.tileshell/.calendar.CalendarActivity ->
  assigned` with no `replaced`; `layout_json` shows both slots on the shell's components and both markers. **Pointing it
  back sticks:** Settings > Tile apps → `tile_app_slot:PEOPLE` → the image's Contacts candidate; a MARK, `am force-stop
  app.tileshell`, `ensure_start` → the PEOPLE tile reads the image's Contacts, `layout_json` `slots.PEOPLE` is its
  component, and the slice holds `assignSlotOnce slot:people:v1 … -> already run` and no `-> assigned` line (`absent_in`).
  **Guard leg (the guard task 1 still builds, on the one other marker):** a copy of the baseline with `slot:music:v1`
  removed from `addedOnce` and `slots.MUSIC` set to the Auxio fixture's component is written the way `layout_restore`
  writes a file (force-stop, push, `run-as … cat > files/start_layout.json`, force-stop; not `layout_restore` itself,
  whose read-back would reject the marker the shell adds), a MARK, `ensure_start` → `layout_json` `slots.MUSIC` is still
  Auxio's, `addedOnce` holds `slot:music:v1`, and the slice holds `assignSlotOnce slot:music:v1 MUSIC -> kept user's
  org.oxycblt.auxio/.MainActivity`. The row log records both APK ids (`apk match: NO` on the
  first leg is expected and noted). (Was "`adb install -g` the old build and the HOME / ASSISTANT roles … as
  `provision.sh:73-74` sets them" — the cite was `:50-54`, fixed 2026-09-30, r3 D15 — which left the old build's wizard
  showing — SUPERSEDED 2026-09-23 by C-19.) **Agenda pod (re-cut of phase 14 E3's Agenda launch; r3 D8 / V5; INDEX
  Change Log when built):** after `layout_restore qa/phase-16/baseline_layout.json`, `ensure_start`, a right swipe
  (`adb shell input swipe 200 1200 950 1200 250`, `p14.sh:13`'s `swipe_right`; `pod_bay` in the dump), a MARK, `tap_node
  pod_header:agenda` → the resumed activity is the component the baseline FILE's `slots.CALENDAR` names (read on the
  host, never from the app) and the slice holds `[podbay] launch agenda -> app.tileshell/.calendar.CalendarActivity`; `c6`, `ensure_start`; with no event in the pod's window, `pod_empty:agenda`
  reads phase 14's unchanged empty line; `qa/phase-14/scripts/e3.sh` re-run with its precondition re-cut (task 8) passes
  on this build. Phase 01 E4's remaining proofs (Maps one-handler auto-assign; Mail and Store 2+-handler unassigned) re-run and
  pass on the same build. **Music negative (phase 15's build task 0 routing, Decisions):** with the shell's Music
  playing a fixture track (phase 10 E10's form), the CALENDAR tile still shows its `CalendarFeed` day face and the
  PEOPLE tile its People face (dump tags; screencap), neither tile has grown (`ActiveTiles` bounds unchanged), and only
  the MUSIC tile carries the now-playing face — the same build passes phase 10's E10 and E13. Restore: `pm clear
  app.tileshell` → `provision.sh` → `ensure_start`, then `layout_restore qa/phase-16/baseline_layout.json` (which also
  undoes the re-pointed slots of the three legs)
- E2 App list: "Calendar" under C and "People" under P (dump), no "New" caption (X14), hold menus offer Pin to Start and NOT
  Uninstall (phase 10 E2's form), the jump grid marks C and P; `qa/phase-02/scripts/regress.sh`'s pattern passes; each window's
  dump carries resource-ids (`cal_view`, `people_letter:A`); `dumpsys package app.tileshell` matches qa/phase-03/exported-allowlist.txt
  after its ADDs (phase 03 E5's `scripts/exported.py`; the ADDs are the two activities and
  `.calendar.CalendarReminderReceiver`, r3 D6). **Baseline (C-3):** after `layout_restore
  qa/phase-16/baseline_layout.json`, from a MARK taken before it, the slice first holds at least one `assignSlotOnce` line
  (`p14.sh:76`'s form — the zero below is then about this load, not an unreadable ring) and then ZERO `assignSlotOnce … ->
  assigned` lines (`absent_in`; r3 V8), and the restored `addedOnce`
  equals the file's, `slot:calendar:v1` and `slot:people:v1` included (`layout_json`)
- E3 Local calendar (Q2 D; T16-2 — phase 03's `LocalCalendar` reused, created whatever else exists): with `Tessera` deleted
  (the preamble's command), `cal_fixtures_down` run and no contact carrying a birthday — the Birthdays calendar exists only
  once a first birthday does (r3 V15, D14), so "exactly one row" below is decidable; asserted first: the query returns "No
  result found" — opening Calendar creates it: `content query --uri
  content://com.android.calendar/calendars --projection _id:account_name:account_type:name:calendar_access_level:calendar_color`
  lists exactly one row — account_name Tessera, account_type LOCAL, name Tessera, access 700, colour -16751695 (#0063B1) —
  and diagnostics hold `[calendar] local calendar created: …` (`feeds/LocalCalendar.kt:55`) and `[calendar] calendars: 1
  (local created id=<id>)`; force-stop and reopen → still one (`local present`). **Inverted (T16-2 line 4):** with Tessera
  deleted and the QA calendar inserted first, `pm clear` → `provision.sh` → `ensure_start` and opening Calendar STILL creates Tessera
  (two rows, `local created`), both list as `cal_calendar_row:` rows inside `cal_pane` under their account headers
  (`cal_account:Tessera`, `cal_account:qa`; K6.4, T16-13), and the QA calendar is read-only: a driver-inserted event on it
  is tapped, the dump holds `cal_event_page:<its id>` (asserted first, so a missed tap cannot pass — r3 V8) and no
  `cal_event_action:edit` / `:delete`. **Tess first:** with Tessera deleted, J6's typed request ("add a meeting called
  standup to my calendar at ten AM", `cortana_card_button:confirm` tapped) creates it; opening Calendar then logs `local
  present` and the query still shows one Tessera row. The editor's calendar field reads Tessera and offers no other
  calendar (dump); a calendar with
  access level 200 (READ) inserted by the driver lists read-only, as every calendar but Tessera does. Restore
  (r3 V10): `cal_fixtures_down` (the QA calendars and their events), Tess's "standup" event deleted (`content delete --uri
  content://com.android.calendar/events --where "title='standup'"`, j6.sh's form), Tessera kept
- E4 Events both ways (the `ContentObserver`, no restart): an event created in the editor (title "Standup", tomorrow 09:00–
  10:00, location "Room 2") → `content query --uri content://com.android.calendar/events --projection title:dtstart:dtend:
  calendar_id:eventTimezone:eventLocation` lists it in the local calendar with the device zone, and phase 01's Calendar tile
  shows its face within its next flip (E7's form; `CalendarFeed`'s observer); a MARK, then a driver `content insert` of
  "Dentist" today 14:00 → the day view lists `cal_event_title:` "Dentist" with no restart, and the first `[calendar] view day
  …: n instances in <ms> ms` line after the MARK whose n is one more than the line before the MARK has `wall=` − MARK ≤ 2000
  (T16-17; was "within 2 s", a host-polling clock); delete from the app → the provider row is gone; a MARK, then `content
  delete` of Standup → its `cal_event:` node is gone and the next `view` line with n one fewer has `wall=` − MARK ≤ 2000.
  **Pinned app tile (r3 D12):** "Calendar" pinned from the app list (phase 02's Pin to Start) → with "Dentist" inside the
  feed's 24 hours, the pinned tile's dump holds the same face text as the CALENDAR slot tile (both published by
  `CalendarFeed`, one under the slot key, one under the component key). Restore: the test events deleted, `layout_restore
  qa/phase-16/baseline_layout.json` (removes the pin)
- E5 All-day, multi-day, recurring (structure here, geometry in E19): driver inserts an all-day event
  (`--bind allDay:i:1`, dtstart at UTC midnight, eventTimezone UTC), a 3-day timed event, and a weekly series
  (`--bind rrule:s:FREQ=WEEKLY;COUNT=10 --bind duration:s:PT1H`, no dtend) → the day view shows the all-day event in its
  all-day band and not at a time (its `cal_event:<id>` node lies inside `cal_allday:<that date>`'s bounds and no
  `cal_event_time:<id>` node exists for it — r3 V8); the 3-day event appears on each of its days; the agenda lists ten `cal_event:` instances of
  the series with `content query --uri content://com.android.calendar/instances/when/<start>/<end>` agreeing on the count;
  in the app, Repeat = weekly on a new event writes an `rrule` and a `duration` and no `dtend`; "edit this occurrence" on the
  third instance writes an exception row (`original_id` = the master, `originalInstanceTime` = that instance) and the agenda
  shows the changed title on that day only; "this and following" from the fifth sets `UNTIL` on the master (query shows it)
  and creates a new master; "delete all" removes the master and every instance. Restore (r3 V10): every event the row
  inserted or created is deleted by id and the count of Tessera's events equals the count before the row
- E6 Reminders through the provider (Rule 16: no shell alarm; re-cut 2026-09-30 by r3 D6 / V7): every clock move here is
  FORWARDS (`jump_clock`). **(a) The shell alone:** `pm disable-user --user 0 com.android.calendar` (the AOSP Calendar
  fixture, which by Decisions also handles the reminder and marks its alerts fired — with it enabled an "alert fired"
  clause would not test the shell); `shell_alarms_pending` read; a local event "Standup" 30 min ahead with `content insert
  --uri content://com.android.calendar/reminders --bind event_id:i:<id> --bind minutes:i:10 --bind method:i:1`; `dumpsys
  alarm` shows the provider's alarm under `com.android.providers.calendar` and `shell_alarms_pending` reads the SAME count
  as before the insert (was "NOT under `app.tileshell`", which the shell's own reminder and clock alarms —
  `cortana/reminders/ReminderScheduler.kt`, `clock/RingService.kt` — can break without any fault here); `jump_clock` to 5 s
  before T−10 min, then a MARK → within
  10 s `dumpsys notification --noredact` shows ONE notification of the shell's (title "Standup", the time, on the calendar
  channel, visibility PRIVATE), the slice holds `[calendar] reminder event=<id> minutes=10: notified` once, and
  `content query --uri content://com.android.calendar/calendar_alerts --projection event_id:state --where
  "event_id=<id>"` shows state 1 (FIRED) — with the other calendar app disabled, only the shell can have written it;
  dismissing the shell's notification (`adb shell cmd statusbar expand-notifications`, a swipe across its node at the
  dump bounds, `cmd statusbar collapse`) → state 2 (DISMISSED) and `… : dismissed`. **(b) The race and the double:** `pm
  enable com.android.calendar`; a second event and reminder, `jump_clock` to 5 s before its T−10 min, a MARK → the shell
  still posts exactly one notification for it and logs `notified` once, whichever app flipped the alert row first (the
  receiver reads SCHEDULED or FIRED rows less its notified set); the AOSP Calendar
  fixture's own notification is ALSO present (`record`ed, Decisions; a recorded clause, C-26). **(c) A forged poke:**
  from a MARK, `adb shell am broadcast -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/1 -n
  app.tileshell/.calendar.CalendarReminderReceiver` with no alert due → no new notification and no `notified` line
  (`absent_in`). **(d) Reboot:** `adb reboot` with a reminder 3 min ahead, the boot poll and `wake_device`
  asserting `Awake` (C-25) → after boot the provider re-armed it (`dumpsys alarm`) and it notifies at its time. Restore
  (r3 V10): `pm enable com.android.calendar`, the events and their reminders deleted, `clock_restore` (its force-stop also
  removes the shell's notifications — asserted: `dumpsys notification --noredact` then holds none on the calendar channel)
- E7 Thousands of events: a driver loop inserts 5,000 events across 24 months into the QA calendar (run time `record`ed),
  one month holding 400 of them; `cal_month_dropdown` opened and dumped, then a MARK taken immediately before the tap on
  `cal_month_cell:<that month's first day>` (r3 V21: a MARK before the drop-down opened put the driver's own dump time
  inside the 3000 ms) → Agenda at
  that day (its dump available) and the launcher ring slice's `[calendar] view agenda <from>..<to>: n instances in <ms> ms`
  line has ms ≤ 3000 and `wall=` − MARK ≤ 3000; the Week view of that month's busiest week, from its own MARK, the same with
  its `view week` line and n equal to the host's `content query …/instances/when/<week start>/<week end>` count; paging 12
  weeks forward with `input swipe` (a MARK before each swipe) gives every `view week` line ms ≤ 3000 and `wall=` − that
  swipe's MARK ≤ 3000, and `dumpsys gfxinfo app.tileshell` janky frames ≤ 5 % over the run (phase 01's threshold, applied on
  the emulator as a bound not a phone measurement); a day with 200 events lists them scrollably in the Day view; the
  Calendar tile still shows only the next 24 hours' events (`CalendarFeed`'s window); delete the QA calendar afterwards (its
  events cascade). (Was "the month view … within 3 s of the page change (screenrecord frame count)" and "view month …: 400
  instances" — SUPERSEDED 2026-09-23 by T16-13, no Month page, and T16-17, no screenrecord clock.)
- E8 Time zone and DST: the zone recorded (`getprop persist.sys.timezone`) and set to America/Denver; an event at 09:00
  America/Denver on a date after the next DST change, and the row's OWN all-day event on that date (inserted here as E5
  inserts one — no row depends on E5, r3 V10); `cmd alarm set-timezone Asia/Tokyo`
  → the event shows at its Tokyo wall time (the text of `cal_event_time:<id>`, the time's own node — r3 V8; was "dump
  `cal_event_title:` with the time text", a read off the title node) while the all-day event stays on
  its date (inside `cal_allday:<that date>`); back to Denver; an event across a DST night (23:30–01:30 on the fall-back date) shows a 3-hour span in the day
  view (the provider's instance end) and the editor shows its stored end wall time. Restore: the three events deleted, the
  recorded zone set back
- E9 Tess on this AVD (**re-cut of phase 03 E2's calendar rows**, recorded in the Change Log when built): **the
  account-calendar negative is J6's row** (qa/phase-03/scripts/j6.sh; evidence qa/phase-03/J6/, 7/7 on 2026-09-23),
  re-run on this build with its local lookup keyed on `account_name=Tessera` (build task 8) and passing: Tess's event
  lands in Tessera and in neither QA account calendar; re-run again with a Birthdays calendar present — the row gives its
  own fixture contact a birthday (E17's insert) and asserts `Tessera Birthdays` lists; it does not depend on E17 having
  run (r3 V10) — → still
  Tessera, never Birthdays (T16-2 line 2). Then, with Tessera and no fixture calendar, the typed request "add a calendar
  event called dentist tomorrow at 2 pm" → the confirmation card (phase 03 E7's form) and, after a tap on
  `cortana_card_button:confirm` (was `on "yes"`; r3 V20), `content query …/events` lists
  "dentist" with Tessera's id (E2's observable, the id read from the `account_name=Tessera` query) and the reply
  (`reply_since <MARK>`, C-20) "Added to your calendar."; the typed "what is on my calendar" (no apostrophe, V20) names
  the events inserted for the test; the Calendar app's day view lists
  the same event; **Tess's delete, the allowed case (r3 D1):** the typed "delete the event dentist" → the Delete card,
  `cortana_card_button:confirm` → the reply "Deleted.", the `events` row gone, and `[calendar] write delete event=<id>:
  ok` naming a Tessera event (the refused case is E22's); phase 03 E10's gated calendar commands are unchanged and re-run
  passing ("Unlock to continue"). Phase
  03 E7 (text / call by name) and E14 (person reminder) re-run passing on the same fixtures — People changed nothing in
  `Contacts.byName`. Restore (r3 V10): the row's events deleted, the birthday row removed and the Birthdays calendar
  deleted (`cal_fixtures_down`), `people_fixtures_down`
- E10 People list, search, no-name and duplicates: `people_fixtures_up` — Ann Lee (+1 555 000 0001, ann@example.com), Bob Stone, Zoë Ǻrén,
  张伟, a raw contact with only a number (+1 555 000 0009), and two raw contacts "Cara Diaz" sharing one number (+1 555 000
  0003; a same-account pair with only a name in common may not aggregate — r3 V15), all phone-only. Asserted from the
  provider before the app is read (V15): `content query --uri content://com.android.contacts/raw_contacts --projection
  _id:contact_id:display_name --where "display_name='Cara Diaz'"` lists TWO raw contacts with ONE `contact_id` (if the
  provider did not aggregate them the row fails here, on its fixture, not in the app) → the list shows one Cara Diaz row
  (`people_row:` count), the number-only contact under "#" reading its number
  (`people_name:` text), 张伟 under the provider's phonebook label (the letter header text equals `PHONEBOOK_LABEL` from
  `content query --uri content://com.android.contacts/contacts --projection display_name:phonebook_label`), Zoë under Z; a
  letter header tapped opens People's jump grid (`people_jump_cell:<label>`), where exactly the labels that exist are in
  accent and the others (52,52,52) ± 4 levels, "#" first and the globe last (r11/people.md P2.4–P2.5; was "the jump grid
  (X8)", SUPERSEDED 2026-09-23 by T16-13), and tapping `people_jump_cell:Z` lands the list on Z; search "555 000 0001" and
  "ann" each return Ann Lee only (launcher ring slice `[people] search …: 1`); rows at a 50 ± 1-epx pitch, each a 32 ± 1-epx
  circular avatar with its left edge at x 12 ± 1 and the name's left edge at x 57.75 ± 1, the initial on a grey disc where
  no photo exists, under accent letter headers at x 14.75 (r11/people.md P1.5–P1.10; was "rows at 70.5 ± 1.2 epx with the
  48-epx avatar … (R7 §1.3 stand-in until r11/people.md)", SUPERSEDED 2026-09-23 by T16-13). Restore (r3 V10):
  `people_fixtures_down`; the `raw_contacts` count equals the count before the row
- E11 People tile (R3 A9; [fidelity] H3): ~~after E13 gives three fixtures photos~~ the row makes its own fixtures (r3 V10,
  V14): `people_fixtures_up`, then Ann, Bob and Zoë each get a photo through People's editor and Android's photo picker
  (E13's steps, run here) from three solid-colour JPEGs pushed from `qa/phase-16/fixtures/` (pure red, green and blue),
  each asserted as a `vnd.android.cursor.item/photo` data row before Start is read; over 40 s on Start the ring holds ≥ 4
  `[people] tile event <n> t0=<uptime> lookup=<key>` lines spaced by the period 7.7 ± 0.2 s + one frame, and per event `[motion]
  people_bubble_out … settle=<ms>` ≈333 ms and `[motion] people_bubble_in … settle=<ms>` ≈583 ms, each within R3 A9's
  one 24-fps frame (± 42 ms) + one frame, with the event 1.88 ± 0.04 s + one frame from the out's t0 to the in's settle
  (the shell's clock, C-5 / T16-8), and every one of those `[motion]` lines with `maxGapMs` ≤ 33.4 (C-31); a 60-fps screenrecord of the same 40 s corroborates under phase 05's frame-spacing
  rule (RV11; never the clock); Start's dumps here go through phase 05's gesture driver (`gdump`; C-10); ~~the bubbles show the
  fixtures' photos (pixel match against the pulled photos at tile scale)~~ (STRUCK 2026-09-30, r3 V14: with photos cycled
  "at random" a pixel match had nothing to match against) for each event, a screencap taken after that event's
  `people_bubble_in` settle has the settled bubble's centre pixel equal, ± 8 levels per channel, to the fixture colour of
  the contact the event's `lookup=<key>` names, and over the 40 s at least two different lookups appear; a People app tile
  pinned from the app list shows the same bubble events (r3 D12); with every photo removed, 40 s of screencaps
  at 1-s intervals show no change on the tile (the static pattern) and diagnostics `[people] tile: 0 photos`. The tile's
  tap resumes the shell's People (E1). Restore: `people_fixtures_down`, `layout_restore` of the baseline (the pin)
- E12 Card actions (`people_fixtures_up` first; each action from its own MARK, `c6` + `ensure_start` after each launch): on
  Ann Lee's card, Call → `dumpsys telecom` shows an outgoing call placed by the shell and `adb emu gsm
  list` lists it (`gsm cancel` after); Text → the SMS role holder's compose (the Fossify Messages fixture) resumes with
  `smsto:` her number (`dumpsys activity activities`), or phase 06's thread once it holds the role; Mail with the K-9 fixture
  assigned to the Mail slot → the slice holds `[people] action mail -> <K-9's component> mailto:ann@example.com` and the
  top package is K-9's (r3 V21; was "K-9's compose resumes", but K-9 with no account may not stay on compose); with the
  slot unassigned → Android's
  resolver; Address (an added postal row) with OsmAnd in the Maps slot → OsmAnd resumes with a `geo:` query and the slice
  holds `[people] action map -> <OsmAnd's component> geo:0,0?q=…`; `am start -a
  android.intent.action.VIEW -d content://com.android.contacts/contacts/<Ann's id>` → the shell's People card is resumed
  (`dumpsys activity activities`); on the AVD the AOSP Contacts fixture is the other handler, so Android's resolver appears
  first and the driver taps the shell's entry and "Always" at their dump bounds, `record`s that it was needed (a recorded
  clause, C-26), and a second `am start` then resumes the shell's card with no resolver. Restore (r3 V10): `gsm cancel`;
  the Mail and Maps slots back to the baseline (`layout_restore qa/phase-16/baseline_layout.json`); the "Always" cleared
  with `adb shell pm clear-package-preferred-activities app.tileshell`, then — because that also drops the shell's
  preferred HOME activity — `adb shell cmd package set-home-activity app.tileshell/app.tileshell.StartActivity`
  (`provision.sh:77`'s line) and `ensure_start` asserted; `people_fixtures_down`
- E13 Create, edit, photo, delete (`WRITE_CONTACTS`; `people_fixtures_up` first — phone-only fixtures, which Q-16-3 keeps
  editable with nothing on "Can edit"): New → the editor's `people_editor_account` reads "Phone" and offers no other row
  while nothing is allowed → "Dan Ford" with a mobile number → `content query --uri
  content://com.android.contacts/data --projection raw_contact_id:mimetype:data1 --where "mimetype='vnd.android.cursor.item/phone_v2'"`
  shows the number under a new raw contact whose `account_name` and `account_type` are NULL (`raw_contacts` query; the
  phone, Q-16-3); edit Ann's number → the data row changes; Photo → Android's photo picker
  (`dumpsys activity activities` shows the picker) → a pushed JPEG chosen → a `vnd.android.cursor.item/photo` data row exists
  for Ann and the card shows it (the pushed JPEG is one of the solid-colour fixtures; `people_card_photo`'s centre pixel
  equals its colour ± 8 levels); ~~the same for Bob and Zoë (E11's fixtures)~~ (E11 now makes its own photos, r3 V10);
  delete Dan → his contact row is gone; with
  `pm revoke app.tileshell android.permission.WRITE_CONTACTS` the editor says it cannot save and offers the grant in place
  (`people_notice`; phase 10 E18's form), the Setup checklist shows `checklist:people:partial` (READ held, WRITE not) while Tess's `contacts`
  row still reads granted (T16-15; was "Tess's checklist's `contacts` row reads PARTIAL", SUPERSEDED 2026-09-23), and `pm
  grant` restores `checklist:people:granted`. Restore: `pm grant … WRITE_CONTACTS` (asserted held), `people_fixtures_down`,
  the pushed JPEG removed
- E14 Link and unlink: fixtures "Sam Reed" (number only) and "Sam Reed" (e-mail only), both phone-only raw contacts — one
  account for both, stated, so the non-merge does not rest on the fixtures' accounts (r3 V15) — asserted from the provider
  before the app is read: two `raw_contacts` rows with two different `contact_id`s → Link from the first card picks the second → `content query --uri
  content://com.android.contacts/aggregation_exceptions --projection type:raw_contact_id1:raw_contact_id2` shows type 1
  (KEEP_TOGETHER) for the pair and the list shows one Sam Reed with both the number and the e-mail on the card; Unlink → type 2
  and two rows again; diagnostics `[people] link …: ok`. Restore (r3 V10): `people_fixtures_down` (deleting the raw
  contacts removes their exception rows; asserted: the `aggregation_exceptions` query lists neither id)
- E15 Share, SIM import, filter, APK (`people_fixtures_up` first): from a MARK, Share on Ann → `dumpsys activity activities`
  shows the resolver for `ACTION_SEND text/x-vcard`, the slice holds `[people] share <Ann's lookup>: <uri>` — the Contacts
  provider's own vCard stream, `content://com.android.contacts/contacts/as_vcard/<lookup>` (r3 D10) — and `adb shell
  content read --uri <the uri from that line>` yields text beginning `BEGIN:VCARD` with `FN:Ann Lee` and
  her `TEL` (r3 V16: "<the stream uri>" had no stated source; it is now the logged line, and the stream is the Contacts
  provider's, which the adb shell user reads as it reads every `content query` in these rows — no provider or cache file
  of the shell's exists to read with `run-as`); SIM: `content insert --uri content://icc/adn --bind tag:s:Sim Bob --bind number:s:5550002`, then `content
  query --uri content://icc/adn` on the host decides the branch and is `record`ed (accepted / refused); People's Import
  from SIM is run in BOTH branches (V16). Accepted → it lists `people_sim_row:` "Sim Bob" → import → a
  contact row exists with that number under a phone-only raw contact (Q-16-3), diagnostics `[people] sim import: 1 of 1`.
  Refused → the page lists no `people_sim_row:` and the slice holds `[people] sim import: 0 of 0`, and the import of a
  stored SIM contact is P4's. Filter: a raw contact inserted with
  `--bind account_type:s:com.example --bind account_name:s:x` shows an account group in "filter contact list", unticking it
  hides that contact and the row count drops by one (re-ticked). Restore (r3 V10): `people_fixtures_down`, the
  `com.example` raw contact and the imported contact deleted by id, the SIM entry deleted (`content delete --uri
  content://icc/adn --where "tag='Sim Bob' AND number='5550002'"`, `record`ed if the SIM refuses). APK: `stat -c%s` before task 2 and after task 8 differ by
  ≤ 2 MB, `unzip -l` shows no new entry ≥ 1 MB
- E16 Work profile (phase 01 E18's commands to create, start and later remove a managed profile; P4 design H10): TestDPC
  is installed into the profile and made its owner FIRST (the policy fixture below), before the positive leg, so the two
  legs differ only by the switch (r3 V17); a contact
  "Work Wren" inserted with `content insert --user <id>` → the People A-Z list does NOT list her (`people_row:` absent), search
  "Wren" lists her with the briefcase glyph (`people_row:` present with an enterprise marker in its tag) — the provider's
  `ENTERPRISE_CONTENT_FILTER_URI`, diagnostics `[people] search "Wren": 0 (+1 enterprise)`; with cross-profile contact search
  disabled by the profile owner (`DevicePolicyManager.setCrossProfileContactsSearchDisabled`), search finds nothing and says
  so, and the launcher ring slice holds `[people] search "Wren": 0 (+0 enterprise)`. **The policy fixture (T16-18):** TestDPC
  (googlesamples/android-testdpc, Apache-2.0, no Play services) ~~from the fixture stash `provision.sh` reads (`FIXTURES`,
  default `$HOME/android-fixtures`, `provision.sh:89` — cite fixed 2026-09-30, was `:66`)~~ (STRUCK 2026-09-30, r3 V17: it
  is not in that stash, and `provision.sh:89-106` would install it into user 0 for every phase) from
  `qa/phase-16/fixtures/` (build task 8 owns it; not committed), installed inside this row only with `adb install --user
  <id>`, never into user 0, and made the profile's owner with `adb shell dpm
  set-profile-owner --user <id> com.afwsamples.testdpc/.DeviceAdminReceiver`; the switch for cross-profile contact search is
  TestDPC's own, driven at its dump bounds in the profile (its label `record`ed at build start); a QA-only fixture, never
  shipped (P5). The negative stays on the AVD (was "if none can be installed on this AVD, the row records it and the
  negative moves to P6" — SUPERSEDED 2026-09-23 by T16-18). Work Wren's card, opened from the search result, shows no
  `people_card_edit` / `people_card_delete` and the `people_card_readonly` line (another profile's contact is never
  writable; Q-16-3). Restore: the profile removed (phase 01 E18's command — TestDPC and Work Wren go with it), `pm list
  users` shows user 0 only, `pm list packages --user 0 com.afwsamples.testdpc` lists nothing
- E17 Birthdays (Q3): `people_fixtures_up` (the row's own Ann and Bob; no dependency on E10 — r3 V10), no Birthdays
  calendar (asserted), the Calendar app NOT opened since the last `ensure_start` (the writer runs from
  `ShellApp.startFeeds`, r3 D14); a MARK; a birthday on Ann whose month-day is the DEVICE's own today, year 1990 (`content
  insert … --bind mimetype:s:vnd.android.cursor.item/contact_event --bind
  data2:i:3 --bind data1:s:1990-<the MM-dd of adb shell date +%m-%d>`; was `1990-09-23` with the clock jumped BACK to
  2026-09-23, after which every earlier ring line passed `ring_since` and `birthdays: 1 synced` could pass stale — r3 V4;
  the row makes no clock jump) → within 5 s the provider has a "Birthdays" local calendar (account_name `Tessera
  Birthdays`, never `Tessera`; access 200) with a yearly
  all-day event "Ann Lee's birthday" (`content query …/events --where "calendar_id=<birthdays id>"` shows the rrule
  FREQ=YEARLY), the Calendar app shows it on today's date, the tile shows it today, phase 14's Agenda pod lists it (a
  `pod_row:agenda:` text), and Tess's typed "what is on my calendar" names it (V20); the editor refuses to edit it (read-only calendar:
  `cal_event_page:<id>` present, no `cal_event_action:edit`);
  removing the birthday row removes the event; the slice from the MARK holds `[calendar] birthdays: 1 synced`. **Date
  forms (r3 D14):** a no-year birthday on Bob (`data1:s:--<MM-dd of tomorrow>`) → a yearly all-day event on tomorrow's
  date; a 29 February birthday (`data1:s:1992-02-29` on a third fixture) → its event row's rrule is
  `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1`, and `content query --uri content://com.android.calendar/instances/when/<start>/<end>`
  over February 2027 gives one instance on 28 February and over February 2028 one on 29 February. The birthday event has no
  reminder rows (`content query --uri content://com.android.calendar/reminders --where "event_id=<its id>"` → "No result
  found"; T16-4), and a Tess "add" made while Birthdays exists lands in Tessera (T16-2 line 2). **Creation refused
  (T16-19):** with Birthdays deleted and `pm disable-user com.android.providers.calendar`, a birthday added to Bob → the
  launcher ring slice holds `[calendar] birthdays calendar could not be created: <err>` and no crash; `pm enable
  com.android.providers.calendar` → the next contacts change creates `Tessera Birthdays` again. Restore (r3 V10): `pm
  enable com.android.providers.calendar` (asserted enabled), the birthday data rows removed, the Birthdays calendar deleted
  with `cal_fixtures_down`'s command (`account_name=Tessera%20Birthdays`), Tess's added event deleted,
  `people_fixtures_down`
- E18 Permission states: `pm revoke app.tileshell android.permission.READ_CALENDAR` (and WRITE_CALENDAR) → Calendar's page says
  it cannot read the calendar and offers the grant in place, the checklist's `calendar` row (phase 01's) is red, and no query
  runs: the launcher ring slice from the revoke's MARK holds `[calendar] calendars: denied (READ_CALENDAR)` and no
  `calendars: none` (T16-16; was "a denied read logs `read=false`", a form only the People line has); `pm grant` restores it,
  the views load without a restart, and the slice from the grant's MARK holds a `[calendar] calendars: n (…)` line and no
  `denied` line (`absent_in`). **WRITE_CALENDAR alone revoked (r3 D7):** Tessera deleted (the preamble's command), `pm
  revoke app.tileshell android.permission.WRITE_CALENDAR` with READ held, a MARK, Calendar opened → the views load, the
  slice holds `[calendar] calendars: n (local missing: WRITE_CALENDAR)`, and the calendars query lists no Tessera row
  (nothing was created); New → a title → Save → `cal_notice` says it cannot save and offers the grant in place, and
  `events` holds no such title; Tess's typed "add a meeting called standup to my calendar at ten AM" with
  `cortana_card_button:confirm` tapped → "I don't have a calendar to add that to." and `[calendar] local calendar could
  not be created:` (T16-2 line 3), still no Tessera row; `pm grant … WRITE_CALENDAR`, Save again → one Tessera row
  (`local created`) holding the event. **READ_CALENDAR alone revoked (r3 D7, T16-2's "never a second"):** with that one Tessera row, `pm revoke …
  READ_CALENDAR` (WRITE held), a MARK, Calendar opened and closed three times (`c6` + `ensure_start` between), then Tess's
  typed "add a meeting called standup to my calendar at ten AM" with `cortana_card_button:confirm` tapped → the reply is
  "I don't have a calendar to add that to." and the slice holds `[calendar] local calendar lookup failed:` and no `local
  calendar created` (`absent_in`); `pm grant … READ_CALENDAR` → the calendars query lists EXACTLY one `account_name=Tessera`
  row and no "standup" event. **Contacts:** `pm revoke … READ_CONTACTS` alone — `pm revoke` is per permission, so
  WRITE_CONTACTS stays held (asserted: `dumpsys package app.tileshell` shows it `granted=true`), the state the Edge case
  once called impossible (r3 V12) — → People says so and offers the grant (`people_notice`), the Setup checklist shows
  `checklist:people:missing` (T16-15; MISSING whenever READ is not held) and Tess's `contacts` row is red. Restore (r3
  V10): `pm grant` for READ_CALENDAR, WRITE_CALENDAR, READ_CONTACTS and WRITE_CONTACTS, each asserted `granted=true`;
  the row's event deleted; `ensure_start`
- E19 Calendar geometry ([fidelity] H1 for Agenda, Week, the drop-down and the pane; r11/calendar.md values, asserted ± 1 epx
  unless stated, relative to the drawn status bar's bottom where R11 measured under a 24-epx bar; T16-13): the header — `≡`
  (three 1-epx bars at a 5-epx pitch, x 16–36), the month and year in semibold caps (cap 11.0) at x 51.0 with its cap top
  15.5 epx below the status bar, then ⌄ (⌃ while open), the band 40 epx tall (K1.1–K1.2); the page (26,26,26) ± 2 levels with
  the status and nav bars black (K1.3); the week strip on W/7 (centres 25.0 / 76.6 / 127.5 / 178.4 / 229.0 / 280.4 / 331.3),
  day names (151,151,151) ± 4, in the 15063 form — two week rows, the second dimmed, the selected day a 32 × 32-epx accent
  square ± 2 (K2.1–K2.5; LOW, H1's note); Agenda day headings white semibold at x 24, today's in accent, an empty day "No
  events today" in grey (K3.2–K3.4); event rows with `cal_event_bar:<id>` 8 epx wide at x 0 in the calendar's colour ± 4
  levels, the time or "All day" label at x 24.3, the title at x 92.5, all-day bars 40 epx on a 44-epx pitch, timed bars 56
  epx (K3.5–K3.8); the month drop-down `cal_month_dropdown` at x 5–355 from the header's bottom down 235 epx, a 1-epx
  (80,80,80) ± 4 border, six date rows at a 34.25-epx pitch on the strip's W/7 columns, other-month dates (110,110,110) ± 4
  (K5.1–K5.3); the Week view's 2 × 4 cells of 120 epx split at W/2, each labelled "23 MON"-style at x 10 inside its cell,
  events as tinted lines at a 19-epx pitch, the mini month in the eighth cell (K4.1–K4.4); `cal_pane`'s rows at a 48.1-epx
  pitch with account headers at x 14 and calendar names at x 62, a checked box filled with the calendar's colour (K6.4, on
  U10's dark #1F1F1F chrome); the app bar 47 epx, fill (33,33,33) ± 4 with a 1-epx (80,80,80) top edge, Today · New · View ·
  … at the 68-epx CommandBar's positions (K1.5–K1.6, the 15063 form); the drawn bars (`BarMetrics.STATUS_EPX`,
  `BarMetrics.NAV_EPX`, C-17; `dumpsys window` shows the system bars not visible, phase 01 E19's form) on every view and the
  editor. Day view, event page and editor (UNMEASURED, r11/calendar.md U1–U3; [accept] H15) assert their approximations'
  structure: the editor's fields 32 ± 1 epx with a 2-epx (133,133,133) border (People's P4.4, U3). Motion (UNMEASURED, U8;
  tagged approximations): the drop-down's `[motion] cal_month_dropdown … settle=<ms>` 200 ± 17 ms (R7 §2.2.6) and a day page's
  `[motion] cal_day_page … settle=<ms>` 250 ± 17 ms (X13), each with `maxGapMs` ≤ 33.4 (C-31). (Was "R11-gated … the month
  grid … the drawn status bar 28 epx (R3 C4) … the editor fields at 43.4 epx … the pivot settle … where a pivot exists" —
  SUPERSEDED 2026-09-23 by T16-13 / C-17.)
- E20 People geometry ([fidelity] H2, against the 10586 captures with the version note, U1; r11/people.md values, ± 1 epx
  unless stated, relative to the drawn status bar's bottom; T16-13): the list per E10, the letter cap top 43.5 ± 0.5 epx above
  the group's first avatar top (P1.6); the CONTACTS / GROUPS pivot header in semibold caps, cap 11.0, its cap top 19.5 epx
  below the status bar, the selected pivot white and the other (156,156,156) ± 4 (P1.1–P1.2); the search box 36 epx tall
  from 48 epx below the status bar, x 12 → W − 12, a 2-epx (133,133,133) border, "Search" at x 25 (P1.3); the jump grid's
  72-epx cells in 4 columns (at the AVD's 360 epx), the first row's cap top 119.5 epx below the status bar, letters cap 14.4
  (P2.2–P2.4); the card an accent page from the status bar's bottom to the nav bar's top, the name in caps at x 13.25 with
  its cap top 26 epx below the status bar, `people_card_photo` a 124-epx circle at x 12 with its top 96 epx below the status
  bar, action rows' labels at x 13 ± 1 on a 48-epx pitch (one line) / 65.5 ± 1 (two lines) (P3.1–P3.7); the editor's header
  "EDIT <ACCOUNT> CONTACT" in caps at x 13, fields 32 epx (34 with an edit button) with a 2-epx (133,133,133) border from x 12
  to W − 12, label cap top → box top 22.75, "+ field" rows at a 44-epx pitch, 1-epx (103,103,103) group rules (P4.1–P4.7);
  every app bar's buttons at a 68-epx pitch with "…" 24 epx from the right edge (P0.4); the bars as E19. Motion (C-5; added
  2026-09-30, r3 V14 — the pivot's `[motion]` line had no row): from a MARK, an `input swipe` from CONTACTS to GROUPS →
  `people_pivot:groups` selected and the slice holds `[motion] people_pivot … settle=<ms>` 250 ± 17 ms (X13; a tagged
  approximation, H17) with `maxGapMs` ≤ 33.4 (C-31). The row makes its own fixtures (`people_fixtures_up` /
  `people_fixtures_down`). (Was "rows per E10's
  stand-in, the jump grid per X8 (5 columns, ≈50 ± 4 epx pitch)" — SUPERSEDED 2026-09-23 by T16-13.)
- E21 Diagnostics coverage (re-cut 2026-09-30 by r3 V6 to phase 15 E22's form): EVERY ALTERNATIVE of every line named in
  Decisions — each `|` choice separately: `calendars: n (local created id=<id>)`, `local present`, `none`, `local missing:
  WRITE_CALENDAR` and `denied`; each of the sync line's `ok`, `updated`, `recreated`, `failed <err>`, `failed calendar
  gone`, `failed calendar read-only`, `failed mapping stale`; `write … ok` and `failed`; `reminder … notified`,
  `dismissed` and `skipped (synced copy)`; both `link` outcomes; each `group` op's `ok` and `failed`; the three
  `assignSlotOnce` forms of E1; and so on — appears at least once in the union of this build's saved ring
  slices, `qa/phase-16/*/ring-launcher.txt`, the EDGE row's slices included (C-20; only rows whose logged APK id matches
  this build), across E1–E28 and EDGE. `qa/phase-16/E21/producers.tsv` names, for each alternative, the row or EDGE
  sub-step that produces it, and a pattern no slice holds fails the row. `qa/phase-16/E21/notrun.tsv` lists the
  alternatives that cannot be produced on a device, each with its reason and the JVM test that covers it — as written
  today: `sync … refused (not allowed)`, `[calendar] write … failed refused (not allowed)` and `[people] write …
  refused (not allowed)` (unreachable from the UI by design: no page offers the write; the guards' JVM tests), and
  `[people] tile: photo <lookup> skipped: <why>` (the Contacts provider decodes a photo when it is written, so no fixture
  can store an undecodable one; `PeopleFeed`'s skip has a JVM test). An alternative in neither file fails the row. (Was
  "every line … appears at least once … across E1–E18 and E22–E27 — each pattern grepped once", which eight alternatives
  no E-row produced could not pass — r3 V6; and before that
  "appears in the ring", one final ring that `am force-stop`, `pm clear` and `layout_restore` reset — SUPERSEDED 2026-09-23
  by C-20)
- EDGE Edge cases (added 2026-09-30, r3 V6; the form of phases 11, 14 and 15): `qa/phase-16/scripts/edge.sh` runs a
  sub-step for every bullet of "Edge cases" that no other row carries, each from its own MARK with its own restore, and
  `qa/phase-16/scripts/edge_index.tsv` maps EVERY bullet to a sub-step, another row, a phone row or a JVM test — a bullet
  with no entry fails the row. Its sub-steps are the producers of the alternatives no E-row reaches: `[calendar]
  calendars: none` (the calendar provider disabled, the first bullet); `[calendar] write update event=<id>: failed <err>`
  and `[people] write update raw=<id>: failed <err>` (the event, or the raw contact, deleted by the driver while its
  editor is open, then Save); `failed mapping stale` (the copy's
  `calendar_id` moved by a sync-adapter update); `failed calendar read-only` (the target's access level lowered to 200 by
  a sync-adapter update after a first Sync); `[people] link …: failed` (the second raw contact deleted by the driver while
  the Link picker is open). Not drivable on the AVD, mapped elsewhere in the index: the phone off across a reminder's
  time → P7; the SIM removed mid-import → P4; a tile photo that fails to decode → the JVM test of `PeopleFeed`'s skip.
  Restore per sub-step, then `pm enable com.android.providers.calendar` asserted, `cal_fixtures_down`,
  `people_fixtures_down`, `clock_restore` and the recorded time zone and `time_12_24` setting
- E22 Sync rules 1–2 — no direct write to an account calendar by any path (Q2 D; T16-1): create "Personal"
  (qa.personal@example.com) and "Work" (qa.work@example.com, `isPrimary` 1) with `mkcal` (`p16.sh`), and driver-insert
  "Offsite" into Work, 30 days ahead at most (inside Tess's delete window). Every count below first runs `cal_lists` on the
  calendar it counts (r3 V11). **Rule 1:** both list as `cal_calendar_row:<id>` inside `cal_pane` (dump); "Offsite" shows
  in the day view; tapped, the dump holds `cal_event_page:<Offsite id>` (asserted first — r3 V8) and
  no `cal_event_action:edit`, `:delete` or `:sync`; the editor's calendar field reads Tessera and no picker
  offers Personal or Work (dump). **Hide is not a write (r3 D4):** Work un-ticked in `cal_pane` → "Offsite" leaves the
  Calendar app's views, and `content query --uri content://com.android.calendar/calendars --projection _id:visible
  --where "_id=<work id>"` reads `visible=1`, unchanged; Work re-ticked. **Tess's delete (r3 D1):** from a MARK, the
  typed "delete the event Offsite" → the reply (`reply_since`) is "That event isn't in your Tessera calendar.", the dump
  holds no `cortana_card:delete_confirm`, and Work still holds exactly Offsite. **The exported intents (r3 V18; "by any
  path" includes them):** `adb shell am start -n app.tileshell/.calendar.CalendarActivity -a android.intent.action.EDIT
  -d content://com.android.calendar/events/<Offsite id>` → the dump holds `cal_event_page:<Offsite id>` and no
  `cal_editor`, and Offsite's `events` row (title, dtstart, dtend, calendar_id) equals its read before; `adb shell am
  start -n app.tileshell/.calendar.CalendarActivity -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --el
  calendar_id <work id> --es title Intruder` → `cal_editor` opens with the title prefilled and its calendar field
  reading Tessera; before Save, `events` holds no "Intruder" (a prefill saves nothing without a tap); Save → "Intruder"
  is in Tessera and Work still holds exactly Offsite. **Rule 2:** in the editor create "Standup" (tomorrow 09:00), rename it "Standup 2", create
  and delete "Scratch"; then Tess through j6.sh's typed request ("add a meeting called standup to my calendar at ten AM", Add
  tapped). After every step `content query --uri content://com.android.calendar/events --projection _id:calendar_id:title
  --where "calendar_id=<id>"` gives Personal 0 and Work 1 ("Offsite", title unchanged) while Tessera holds "Standup 2" and
  "standup"; every `[calendar] write … event=<id>: ok` line names a Tessera event. The write guard's JVM test (T16-11's
  ~~three-case~~ allow set, four cases since r3 D1 / D6: Tessera every op, Tess's delete included; Tessera Birthdays by
  the Birthdays writer only; an allowed Sync target for a
  mapped copy only; the receiver's `CalendarAlerts.STATE` update — and the refusal, `refused (not allowed)`, of the editor
  → Birthdays, the Birthdays writer → Tessera,
  Sync → an id not allowed, Sync → an unmapped event, any op → an account calendar, Tess delete → an account calendar,
  any other write by the receiver, Sync → a target whose access level fell below 500 (D5), and T16-12's stale mapping)
  runs and
  passes, its output in the row's log (was "refuses … any id but `LocalCalendar`'s", SUPERSEDED 2026-09-23 by T16-11).
  Restore: `cal_fixtures_down` (Personal and Work deleted through the sync-adapter URI, j6.sh's restore
  form), the test events — "Standup 2", "standup", "Intruder" — deleted, Tessera's event count equal to the count before
  the row
- E23 Sync rule 4 and the first Sync (T16-1, T16-3): `pm clear app.tileshell` → `provision.sh` → `ensure_start`; create
  Personal FIRST and then Work (`mkcal`; Work last, so a re-created Personal cannot be handed the id it had — r3 V15), a
  third calendar "Shared" under Personal's account with `calendar_access_level` 200 (`p16.sh`'s `mkcal` takes the level as
  a fourth argument, default 700), "Offsite" driver-inserted into Work
  (E22's sentinel, V11), the row's own birthday fixture (`people_fixtures_up` and E17's insert, with `Tessera Birthdays`
  asserted listed — no dependency on E17, r3 V10), and a local event "Standup"; tap `cal_event_action:sync` → the "Can sync
  to" page opens directly (dump: `cal_settings_can_sync:<personal id>` and `:<work id>` both unchecked, each under its
  account's `cal_account:<name>` header, the line "Choose which
  calendars Sync may use", no `cal_sync_target:*` node), diagnostics `[calendar] sync event=<id>: no calendar allowed -> can
  sync to`; neither Tessera nor Birthdays is listed there, and no `cal_settings_can_sync:<shared id>` — a calendar the
  phone may not write is never offered (r3 D5). Tick Personal, Back → the Sync picker lists
  `cal_sync_target:<personal id>` only (no Work, Shared, Tessera or Birthdays); Back out without syncing → after
  `cal_lists` on each, Personal holds 0 and Work exactly "Offsite"
  (E22's query); `adb shell run-as app.tileshell cat files/calendar_sync.json` lists Personal's `_ID`, account name and type
  under `allowed`. **Re-added account:** delete Personal (sync-adapter URI) and create it again; the driver asserts its
  new `_ID` ≠ the old one (V15) → Sync on
  "Standup" opens "Can sync to" again with the new Personal unchecked. Restore as E22, plus the birthday row, the
  Birthdays calendar and `people_fixtures_down`
- E24 Sync rule 3 — the tapped push (T16-1), and one event shown once (Q-16-2): Personal then Work created, "Offsite" in
  Work five days ahead, Personal ticked on "Can sync to" (E23's steps, run here), no other event in the next two days, and a local event
  "Standup" starting 2 hours from the device's now, 1 hour long, with a 10-minute reminder — inside the tile's 24 hours and
  the Agenda pod's window. Before the Sync the last `[calendar] refresh (…): access=true faces=<f> agenda=<a>` line reads
  `faces=2 agenda=1`, and `shell_bytes` is read. Sync "Standup" → after `cal_lists` on each, Personal holds ONE event
  (title Standup, dtstart / dtend equal to the local event's; E22's query — the copy exists), Work exactly "Offsite", the local event's page shows
  `cal_synced_marker:<id>` reading "synced to Personal" with `cal_account:qa.personal@example.com` beside it (dump; r3
  D5), `calendar_sync.json` maps the local id to Personal's `_ID` and
  the copy's id, diagnostics `[calendar] sync event=<id> -> calendar <personal id>: ok`, and `shell_bytes` reads the same
  rx and tx as before the Sync (`dumpsys netstats --poll`, then the shell uid's buckets of `dumpsys netstats --uid`
  summed; was "phase 06 E18's form", which has no driver — r3 V21). **The copy is hidden everywhere in the shell
  (Q-16-2; r3 D2):** the Agenda dump holds exactly ONE `cal_event_title:` node reading "Standup", it is the local id's,
  and no `cal_event:<copy id>` node (the Day and Week views the same); the next `[calendar] refresh` line still reads
  `faces=2 agenda=1` (the tile and the feed's agenda gained nothing); on the pod bay `pod_row:agenda:0` reads Standup and
  there is no `pod_row:agenda:1`; Tess's typed "what is on my calendar" names Standup once (one occurrence in
  `reply_since`); then `jump_clock` to 5 s before the start − 10 min, a MARK → within 10 s `dumpsys notification
  --noredact` holds exactly ONE notification of the shell's titled Standup, and the slice holds `[calendar] reminder
  event=<local id> minutes=10: notified` once and `[calendar] reminder event=<copy id> minutes=10: skipped (synced copy)`
  (the copy has its own reminder row, asserted from `reminders`, so its alert did fire). Rename the local event "Standup
  2", Sync →
  the copy reads Standup 2, still one row, `updated`. The copy deleted on the other side (sync-adapter delete on Personal) →
  Sync → one copy again, `recreated`, and the notice says so. The copy's title changed on the other side (`content update`
  through the sync-adapter URI) → Sync → the copy's title is the local title again and the line reads `updated` (r3 V13:
  Sync compares the copy with the local event; under the old hash rule this step read a no-op). A Sync with nothing
  changed on either side → no write, `ok`. A weekly local series (`COUNT=5`) with its third occurrence retitled and a
  10-minute reminder → Sync → Personal holds a master with the rrule and duration, one exception whose `original_id` is the
  copy's master, and a `reminders` row of 10 minutes on the copy; the views show each occurrence once (the copy's master
  and its exception are both hidden). Deleting a synced local event shows `cal_delete_choice:here`
  (the default) and `cal_delete_choice:both`: "here" → the local event gone, the copy kept, its mapping gone from
  `calendar_sync.json`, and the copy now SHOWS in the views as Personal's event (`cal_event:<copy id>` present; its
  `cal_event_page:` has no `cal_event_action:*` — the original no longer exists, Q-16-2); on a second synced event "both" →
  both gone. On a third synced event with Personal then un-ticked in "Can sync to", delete offers `cal_delete_choice:here`
  and NO `cal_delete_choice:both` (T16-12); re-tick Personal. Personal removed from the phone (sync-adapter delete of the
  calendar) → Sync on a synced event → "That calendar
  is no longer on this phone" (`cal_notice`, dump), the marker keeps a warning glyph, `failed calendar gone`. Work holds
  exactly "Offsite" after every step (`cal_lists` first; was "Work holds 0 of the shell's events", which a vanished Work
  calendar also reads — r3 V11). Restore as E22, `clock_restore`, then `pm clear` → `provision.sh` → `ensure_start`
  (clears `calendar_sync.json`)
- E25 App Shortcuts (build task 9; phase 11 Q1's standing rule, C-8): `layout_restore qa/phase-16/baseline_layout.json` (the
  CALENDAR and PEOPLE slot tiles resolve to the shell's apps), `ensure_start`; every dump of Start in this row is `gdump`
  (the People tile may be cycling, so Start never idles — r3 V9); hold the CALENDAR tile by phase 11 E3's method →
  `quick_sat_label:0..3` texts equal Agenda / Day / Month / New event in rank order; the PEOPLE tile → `quick_sat_label:0..2`
  equal Contacts / New contact / Groups, no `quick_sat_label:3` (T16-14); each burst's launcher ring slice holds phase 11's
  activity-keyed line (T11-12) naming only that activity's ids — `[quick] shortcuts for app.tileshell/<the Calendar
  activity, short form>/0: 4 (4 shown: agenda,day,month,new_event)` and `… /<the People activity>/0: 3 (3 shown:
  contacts,new_contact,groups)` (was `[quick] shortcuts for app.tileshell/0: …`); `tap_node quick_sat:<i>` for each → the
  app resumed (`dumpsys activity activities`) with `cal_view_mode:agenda` / `cal_view_mode:day` selected, for Month
  `cal_view_mode:agenda` selected AND `cal_month_dropdown` shown (T16-13; was `cal_view_mode:month`), `cal_editor` for New
  event (Back discards it; Tessera's event count unchanged), `people_page:list`, `people_page:editor` for New contact (Back
  discards it; the `raw_contacts` count unchanged), `people_pivot:groups` selected for Groups; `adb shell dumpsys shortcut`
  lists the seven ids for `app.tileshell` with ranks 0–3 per activity; `c6` + `ensure_start` after each
  launch (C-6). Restore: `layout_restore` the baseline
- E26 Wizard step "People" (phase 12 E14's template as C-15 re-cuts it; C-4; the step is `setup:people`, T16-15 — was Tess's
  widened `contacts` row, SUPERSEDED 2026-09-23). RE-CUT 2026-09-30 (r3 V1) to phase 12 as built: a PARTIAL row never
  summons the wizard and "core held" outranks "finished" (`onboarding/SetupWizard.kt:76-84`), so revoking WRITE_CONTACTS
  alone — what (a) and (c) did — shows no wizard in (a) and logs `core held`, not `finished`, in (c). The row has its own
  driver, `qa/phase-16/scripts/e26.sh` (it sources `qa/phase-12/scripts/p12.sh` for `provision_no_marker`,
  `resume_start`, `restore_fresh` and `start_slice`; phase 12's `e14.sh:29,31` hard-codes "Step 1 of 2" and one missing
  step). Here "Home" is a bare `KEYCODE_HOME`, as `e14.sh:25` presses it, wherever the wizard and not Start is expected.
  **(a) the step:** `pm clear app.tileshell` → `PROVISION_FINISH_WIZARD=0
  qa/phase-03/scripts/provision.sh` (every grant, no finished marker) → `adb shell pm revoke app.tileshell
  android.permission.READ_CONTACTS` and `… WRITE_CONTACTS` (the row MISSING) → a MARK, Home → the dump has `wizard_page`
  with `wizard_step:setup:people` first, `wizard_why` equal
  to phase 12's why line for `setup:people` (`qa/phase-12/scripts/why_lines.tsv`, the T16-15 line's text),
  `wizard_progress` "Step 1 of 3", and the slice holds `[wizard] shown: missing=setup:people,tess:contacts`; a MARK, `pm
  grant … READ_CONTACTS`, `resume_start` → the slice holds `[wizard] step setup:people: partial` and the dump still has
  `wizard_step:setup:people` (PARTIAL is not done for this row); a MARK, `pm grant … WRITE_CONTACTS`, `resume_start`
  → `[wizard] step setup:people: granted`, the step is gone and `wizard_presets` shows (Tess's `contacts` row was granted
  by the READ grant, so no step remains); the step's own
  action is also tapped once in a repeat of (a) (Android may grant both at once or ask — whichever happens is
  `record`ed). **(b) provisioned:** `restore_fresh` (`pm clear` → `provision.sh`; its new line grants WRITE; it
  writes the finished marker, C-15) → no `wizard_page`, and after a resume `[wizard] not shown: core held`; phase 12 E1
  re-run passes on
  this build. **(c) the finished-install rule:** with (b)'s marker set, `pm revoke … READ_CONTACTS` and `…
  WRITE_CONTACTS`, a MARK, a resume → no
  `wizard_page`, `[wizard] not shown: finished`, the Setup checklist shows `checklist:people:missing`; `pm grant …
  READ_CONTACTS` only (WRITE still revoked), a MARK, a resume → no `wizard_page`, `[wizard] not shown: core held`, the
  checklist shows `checklist:people:partial` and Tess's `contacts`
  row granted; restore `pm grant` (was a "Skip setup"-based finished-install half on a provisioned install, which
  cannot show the wizard once provisioning finishes it — SUPERSEDED 2026-09-23 by C-15). Restore: `pm clear` →
  `provision.sh` → `ensure_start`
- E27 Groups (T16-14; H16): `people_fixtures_up` — Ann Lee (mobile +1 555 000 0001) and Bob Stone (mobile +1 555 000 0002),
  phone-only; `people_pivot:groups`
  → `people_group_new`, name "Family" in `people_group_name`, add Ann and Bob → `content query --uri
  content://com.android.contacts/groups --projection _id:title:account_type:account_name` lists "Family" with NULL
  account name and type — the phone, since nothing is on "Can edit" (Q-16-3, 2026-09-30; was "under the account the
  new-contact rule names", which had no defined answer — r3 D13), the two members'
  `vnd.android.cursor.item/group_membership` data rows point at its `_id`, and the
  launcher ring slice holds `[people] group create <id>: ok`; `people_group:<id>` lists Ann and Bob; `people_group_action:text`
  → the SMS role holder's compose (the Fossify Messages fixture) resumes with `smsto:` both numbers (`dumpsys activity
  activities`; the intent's data holds both); `people_group_action:rename` to "Home" → the provider's title reads Home and
  `group rename <id>: ok`; `people_group_action:delete` → the group row gone (or marked deleted), both contacts still present,
  `group delete <id>: ok`; with `WRITE_CONTACTS` revoked a create is refused with the editor's notice (`people_notice`) and
  `group create …: failed <err>`. Restore: `pm grant … WRITE_CONTACTS` (asserted), any group left deleted by id,
  `people_fixtures_down`
- E28 People's write guard and "Can edit" (Q-16-3, 2026-09-30; H21): `pm clear app.tileshell` → `provision.sh` →
  `ensure_start` (nothing allowed). Fixtures, inserted by the driver as `provision.sh` inserts Mom: "Lou Local" (phone-only,
  mobile +1 555 000 0021), "Wade Work" (`account_name` `qa.work@example.com`, `account_type` `com.example`, mobile +1 555
  000 0022) and "Pia Personal" (`qa.personal@example.com` / `com.example`, +1 555 000 0023). The driver reads each
  fixture's `raw_contacts` row (`_id:account_name:account_type:deleted`) and its `data` rows
  (`_id:mimetype:data1`) from the provider into BEFORE files; every "unchanged" below is those rows read again and equal,
  the rows still present (a vanished fixture fails the row — V11's rule, for contacts). **The list:** People's settings →
  `people_page:can_edit` lists `people_can_edit:com.example:qa.work@example.com` and
  `people_can_edit:com.example:qa.personal@example.com`, both unticked (provision.sh's Mom gives a third row,
  `people_can_edit:qa:qa`, unticked too; the phone has no row — it is always editable); `adb shell run-as app.tileshell
  cat files/people_edit.json` holds no allowed account (or the file does not exist yet). **Read-only card:**
  Wade's row tapped → the dump holds `people_card:<Wade's lookup>` (asserted first, so a missed tap cannot pass), no
  `people_card_edit`, no `people_card_delete`, and `people_card_readonly` naming `qa.work@example.com`; its tap opens
  `people_page:can_edit`. Lou's card holds `people_card_edit` and `people_card_delete` (the positive control: the
  actions exist where allowed). **The exported EDIT intent:** from a MARK, `adb shell am start -n
  app.tileshell/.people.PeopleActivity -a android.intent.action.EDIT -d
  content://com.android.contacts/contacts/lookup/<Wade's lookup>/<Wade's contact id>` → `people_card:<Wade's lookup>`
  with no `people_page:editor` node, the slice holds `[people] edit <Wade's lookup>: refused (account not allowed)`, and
  Wade's rows are unchanged; the same intent on Lou opens `people_page:editor`. **New contact:** New → the editor's
  `people_editor_account` offers `people_editor_account_row:phone` only → "Ned New" saved → his raw contact's account
  is NULL. **Mixed contact:** Link Lou with Wade (allowed on any contact; `aggregation_exceptions` shows type 1) → the
  joined card holds `people_card_edit` and NO `people_card_delete`; Edit → Lou's number changed and saved → Lou's data row
  changed, Wade's rows unchanged; Unlink (type 2). **Allowing an account:** `people_can_edit:com.example:qa.personal@example.com`
  ticked → `people_edit.json` lists that account and only it; Pia's card now holds `people_card_edit` and
  `people_card_delete`; her number edited → her data row changes (`[people] write update raw=<id>: ok`); New →
  `people_editor_account` now offers the phone and `com.example:qa.personal@example.com`, and a contact saved with the
  second lands in that account; Wade's card is still read-only and his rows unchanged; Pia deleted from her card → her
  contact row gone. Un-tick → a remaining contact of that account is read-only again. After every step Wade's rows are
  unchanged. The guard's JVM test (the People write guard line, (5)) runs and passes, its output in the row's log.
  Restore: the driver's fixtures, "Ned New" and the contact saved into the personal account deleted by id (the
  `raw_contacts` count equals the count before the row), then `pm clear` → `provision.sh` → `ensure_start` (clears
  `people_edit.json`)
**Phone-only (S25 Ultra):** every step is done on the phone alone — nothing over adb, USB or a PC (r3 V3); a value a row
needs is read on a page of the shell (Settings > Diagnostics) and pasted back.
- P0 The slots after the update (added 2026-09-30, Q-16-1; it replaces the row r3 D9 proposed): once the build that brings
  Calendar and People is installed, the Calendar tile opens the shell's Calendar and the People tile the shell's People,
  whatever each opened before; Settings > Diagnostics shows the two `assignSlotOnce slot:calendar:v1 …` / `slot:people:v1
  …` lines — `-> assigned, replaced user's <app>` where a pick made by hand was taken over (the app it names is
  `record`ed, so it can be pointed back), plain `-> assigned` where there was none — read before the phone is next
  restarted. Then one slot is pointed back in Settings > Tile apps (People → Samsung Contacts): the tile opens Samsung
  Contacts, and still does after the phone is restarted and after the next update of the shell (the pick sticks; the
  line then reads `-> already run`). Jeremy leaves each slot on the app he wants
- P1 Event reminders on One UI 8: a reminder on an event in the local calendar notifies from the shell AND from Samsung
  Calendar (`record`ed; a recorded clause, C-26), and an event on a Samsung or Google calendar notifies from
  the shell too (T16-4); with Samsung Calendar's notifications turned off in Android's settings only the shell's remains.
  Added 2026-09-30 (Q-16-2): a synced local event's reminder notifies ONCE from the shell (the copy's alert is skipped);
  what Samsung Calendar shows and reminds for the original and its copy is `record`ed, not judged (another app reads the
  provider unfiltered)
- P2 Synced calendars and the push (re-cut 2026-09-23 by T16-1 / T16-2; was "an event written here to a synced calendar …",
  a direct write Q2 D forbids; re-cut 2026-09-30 by r3 V3 to a row done on the phone alone): every calendar Samsung
  Calendar shows (Samsung account, any Google or Outlook account the
  phone has — the shell adds none) appears in the shell's Calendar with its colour, read-only. Counts are read in the
  app: opening the ≡ pane writes `[calendar] counts: <account name>/<displayName>=<n>, …`, read on Settings >
  Diagnostics BEFORE and AFTER (was "`content query` over adb"). J6 on the phone: Tess's "add
  … to my calendar" and an event made in the editor both land in Tessera — its count rises by two and every account
  calendar's count is unchanged between the two lines. Jeremy ticks his personal calendar once on "Can sync to" (the work
  calendar stays unticked, and a calendar the phone may only read is not listed — r3 D5); Sync on a local event → that
  calendar's count rises by one in the next `counts` line, the work calendar's count is unchanged across every step, the
  shell's views still show the event once (Q-16-2), and the copy appears in that account's other client (web or another
  app) after the account's own sync adapter runs. ~~`dumpsys netstats
  --uid` for the shell's uid is unchanged across the Sync (the upload is the account's adapter, not the shell)~~ (STRUCK
  2026-09-30, r3 V3: an adb read) With the phone in airplane mode, Sync on a second local event still ends on "synced to
  <calendar>" — the shell's part is a provider write and needs no network — and that copy reaches the other client only
  after airplane mode is turned off (the upload is the account's adapter, not the shell)
- P3 The chooser: from Samsung Messages, a tap on a sender's contact shows Android's chooser with the shell's People listed
  and "Always" remembered; Samsung Phone's contact tap the same; `record`ed, not judged (a recorded clause, C-26)
- P4 SIM import with the real SIM (and E15's import if the emulated SIM refused inserts); contacts stored on the SIM appear
  and import once, into the phone's contacts (Q-16-3). Added 2026-09-30 (r3 V6): the SIM tray pulled while an import
  runs — the import stops with a notice, no crash, and what was imported so far stays (`[people] sim import: n of m` on
  Settings > Diagnostics, `record`ed)
- P5 Samsung Contacts' linked contacts show as one here and a link made here shows as one there (the shared
  `AggregationExceptions`)
- P6 Work profile: Jeremy has none (phase 01 Q5); `record`ed as not testable on the phone, with E16's emulator result —
  its TestDPC policy negative included (T16-18) — standing
- P7 Liveness (N-01): the reminder receiver and the People tile feed survive reboot, 24 h idle and a Device care optimise
  (a reminder set the day before notifies on time; the tile still cycles). Added 2026-09-30 (r3 V6): the phone switched
  off across a reminder's time — after it is switched on the shell notifies once, late, and the notification gives the
  event's time, not "now"
- P8 People's "Can edit" on the phone (added 2026-09-30, Q-16-3; Verify at build start 4): with nothing ticked, a contact
  saved to "Phone" in Samsung Contacts opens in People with Edit and Delete, and an edit made in People shows in Samsung
  Contacts; a contact of the Google account and one of the work account (if the phone has one) show no Edit and no
  Delete and the line naming the account; "Can edit" lists the phone's accounts, all unticked; Jeremy ticks his personal
  account only → its contacts become editable, an edit shows in that account's other client after its own sync, and the
  work account's contacts stay read-only. If the "Phone" contact is NOT editable with nothing ticked, the row fails and
  the local-account rule is re-cut (the S25 names its local account)
**NEEDS-HUMAN:**
- H1 [fidelity] Calendar against r11/calendar.md within tolerance on the phone — Agenda, Week, the month drop-down and the ≡
  pane (K1–K6), with a note that the week strip and selected day follow the 15063-era form seen only at 450-wide
  resolution (K2.4–K2.5, LOW); the Day view, event page and editor are H15, the reminder look H7, settings H6 (was
  "Calendar matches r11/calendar.md", SUPERSEDED 2026-09-23 by T16-13)
- H2 [fidelity] People against r11/people.md — the list, jump grid, card and editor — judged against the 10586 captures with
  that version stated: no governing-build People capture exists (U1)
- H3 [fidelity] the People tile's bubble motion matches R3 A9 (MEDIUM/LOW, one 24-fps camera source) on the phone, and the
  static circle pattern without photos looks like C3's
- H4 [accept] the local calendar's name "Tessera" and colour #0063B1 — J6's constants (`feeds/LocalCalendar.kt:21,52`),
  which the app reuses (was "Calendar" and the accent, SUPERSEDED 2026-09-23 by T16-2) (approximation)
- H5 [accept] the Birthdays calendar's look in the views and on the tile (approximation); added 2026-09-30 (r3 D14): a
  birthday stored without a year shown yearly from this year's date, and a 29 February birthday shown on 28 February in
  years without one (approximations)
- H6 [accept] "(No title)", the first-day-of-week default and setting wording, the settings page (approximations; R11
  captured no settings page, r11/calendar.md U6–U7)
- H7 [accept] the event reminder notification's look (approximation; R11 captured no reminder toast, r11/calendar.md U5);
  added 2026-09-30 (r3 D6 / D10): over a locked screen it shows that a calendar reminder fired and hides the event's
  title (`VISIBILITY_PRIVATE`)
- H8 [accept] a contact with no name listed under "#" as its number or e-mail (approximation)
- H9 [accept] the Android profile ("Me") left out (approximation; offline preferred, A11 as amended 2026-09-23 — Jeremy can
  ask)
- H10 [accept] P4 design: work-profile contacts found by search only, with the briefcase glyph (phase 01 Q5's design extended)
- H11 [accept] link / unlink, SIM import and filter pages (approximations; R11 has guide wording only, r11/people.md
  P5.1–P5.6, U2–U4)
- H12 [accept] the chooser seam on the phone the first time another app opens a contact or event (P3), and that the shell's
  People and Calendar are what "Always" should point at
- ~~H13 [accept] any approximation not covered by H4–H12 or H14–H16~~ REPLACED 2026-09-30 (r3 V19: a catch-all nobody can
  judge) by the named rows H17–H22
- H14 [accept] the Sync design (T16-1, T16-3): the Sync picker lists only calendars enabled once in Calendar settings ("Can
  sync to"), so the work calendar can never be chosen by a mis-tap; the first Sync opening "Can sync to" directly; the
  "synced to <calendar>" marker and its warning glyph (P4 design); the delete choice for a synced event; added 2026-09-30:
  "Can sync to" and the Sync picker grouped under each account's name, calendars the phone may only read left off the
  list (r3 D5), and the marker as the only sign of the copy inside the shell — the copy itself is hidden (Q-16-2)
- H15 [accept] the Calendar Day view, event page and editor, and the "this occurrence / this and following / all" prompt,
  built as r11/calendar.md U1–U4 propose (no capture on any build) (T16-13)
- H16 [accept] People's Groups pivot: the group rows, the group page, create / rename / delete and "Text the group" (guide
  wording and one camera photo only, r11/people.md P5.2, LOW) (T16-14, E27)
- H17 [accept] the three motion approximations (added 2026-09-30, r3 V19; R11 measured no motion, U8): the month
  drop-down growing from its top edge in 200 ms, day paging settling on X13's 250 ms, People's pivot settling on X13's
  250 ms (E19, E20)
- H18 [accept] the App Shortcut sets (Calendar: Agenda, Day, Month, New event; People: Contacts, New contact, Groups), their
  icons, and "Month" opening Agenda with the month drop-down open (agent calls of 2026-09-23; E25)
- H19 [accept] the two apps' icons in the app list and on their tiles, and the tiles' static faces (the Calendar tile's
  day face is R3 C3's; the People tile's circle pattern is H3's)
- H20 [accept] the wording of every notice and line the two apps show (approximations): `setup:people`'s why line "People
  shows and edits your contacts. Without it People can't see them." (phase 12's H1 judged the table as it stood before
  this row existed); the cannot-read and cannot-save notices with their grant offers (E13, E18); "Choose which calendars
  Sync may use"; "That calendar is no longer on this phone"; "That calendar is read-only now" (r3 D5); the `recreated`
  notice; the calendar-provider-off notice
- H21 [accept] People's "Can edit" design (Q-16-3 "(A)"; the agent's consequences): the "Can edit" page and its place in
  People's settings; the read-only card's line "This contact is in <account>. To change it, allow that account in Can
  edit." and its tap opening the list; the editor's account choice ("Phone" and the allowed accounts); a contact joined
  from an editable and a read-only account offering Edit for the editable part and no Delete (E28, P8)
- H22 [accept] Tess's line "That event isn't in your Tessera calendar." for an event on another calendar, and her delete
  of a synced event removing it here only, the copy kept (r3 D1; E22, E9)

## Edge cases
Every bullet here is run: the EDGE row's `edge_index.tsv` maps each to a sub-step of `edge.sh`, another row, a phone row or
a JVM test (r3 V6, 2026-09-30).
- Calendar with no calendar and the provider package disabled (`pm disable-user com.android.providers.calendar`, restore
  `enable`): the app says the calendar provider is off, creates nothing, and the tile's face is the day only (phase 01's
  existing behaviour); `[calendar] calendars: none` logged with the reason; Tess's "add" answers "I don't have a calendar to
  add that to." (`cortana/action/ActionLayer.kt:492`) and writes nowhere else (T16-2 line 3)
- The local calendar deleted by another app (`content delete --uri "content://com.android.calendar/calendars/<id>?caller_is_syncadapter=true&account_name=Tessera&account_type=LOCAL"`)
  while the app is open: its events vanish from the views through the observer; ~~the next start recreates a local calendar
  only if no calendar remains; a QA calendar present → none recreated~~ SUPERSEDED 2026-09-23 by T16-2 line 4: the next start
  or write recreates it through `LocalCalendar.id` whatever else remains (`[calendar] local calendar created: …`), a QA
  calendar present or not; synced markers whose local event went with it are dropped from `calendar_sync.json`
- ~~An account (and its calendar) removed while its event's editor is open: the save fails with a notice, `[calendar] write
  update event=<id>: failed …`, and the view refreshes~~ SUPERSEDED 2026-09-23 by T16-1: the editor writes only the local
  calendar, so this is now the Sync failure case — an allowed calendar removed from the phone refuses the Sync with "That
  calendar is no longer on this phone" (E24); an account calendar removed while one of its events is open read-only closes
  that page with a notice and the view refreshes
- Sync (T16-1): the allow-list cleared after a sync (the marker stays; the next Sync opens "Can sync to"; deleting that event
  offers "Delete here" only, never "Delete here and from <calendar>" for a target no longer allowed — T16-12); a copy whose
  `calendar_id` no longer matches the mapping's target (moved on the other side) → the next Sync or delete-"both" refuses
  with `failed mapping stale` and writes nothing (T16-12); a Sync tapped twice
  quickly (one copy; the second compares the copy with the local event and writes nothing, `ok` — was "`updated` or a
  no-op by the hash", r3 V13); a local event edited while its Sync runs (the next Sync
  pushes the edit); `calendar_sync.json` lost (`run-as … rm`): markers vanish and the next Sync makes a new copy — the old one
  stays on the other side and, no longer mapped, shows in the shell beside the original (Q-16-2 hides only a mapped
  copy; `record`ed); a synced local event whose copy's calendar became read-only ~~(the Sync fails
  with the provider's error, `failed <err>`)~~ (RE-CUT 2026-09-30, r3 D5: the provider may accept the write whatever the
  level — Verify at build start 2 — so the write layer's own re-read refuses it: "That calendar is read-only now",
  `failed calendar read-only`, nothing written, the marker kept with its warning glyph; the EDGE sub-step lowers the
  target's `calendar_access_level` to 200 through the sync-adapter URI after a first Sync)
- One event, one reminder (Q-16-2, 2026-09-30): a synced event whose copy was edited on the other side to another time
  (the shell still shows only the original, at the local time, until the next Sync overwrites the copy); the original
  deleted by another app (the mapping is dropped at the next read and the copy shows as the account's event); the ≡
  pane hiding Tessera (the original leaves the Calendar app's views and its copy stays hidden — the mapping, not the
  pane, decides; the tile, the pod and Tess still show the original, r3 D4)
- Slots (Q-16-1, 2026-09-30): the update installed while a slot's hand-picked app has been uninstalled (the slot is
  taken as any other; the line names the component that was there); the shell's Calendar pointed back and the shell then
  updated again (`already run`; the pick is kept); `pm clear` after the update (a fresh install: `-> assigned`, nothing
  replaced)
- The Birthdays calendar deleted by another app: recreated under `Tessera Birthdays` at the next start or contacts change,
  and `Tessera` is untouched; if the provider refuses the create, `[calendar] birthdays calendar could not be created:
  <err>` (LocalCalendar's form; T16-19, E17's sub-step) and the views simply show no Birthdays calendar
- Sync in progress on the phone (rows changing under the observer): the views refresh without a crash; a `content insert`
  loop of 50 events over 10 s on the AVD stands in for it
- An event whose `dtend` < `dtstart` is refused by the editor; a 10-year daily series (`FREQ=DAILY` with no COUNT) inserted by
  the driver: the Agenda and Week views still render with `view … in <ms> ms` ≤ 3000 (windowed instances; was "the month
  view", SUPERSEDED 2026-09-23 by T16-13 / T16-17); an RRULE with BYDAY=2TU (second Tuesday) shows on
  the right days; an exception that moves an occurrence to another day shows on the new day only
- A reminder on an event already past (never notifies; `calendar_alerts` has no scheduled row); a reminder 0 minutes before;
  two reminders on one event (two notifications); the phone off across a reminder's time (after boot the provider delivers it
  late and the notification says the event's time, not "now" — not drivable on the AVD, P7; r3 V6); the reminder
  broadcast sent twice for one alert, or forged by another app (one notification: the receiver's notified set; E6 (c))
- A calendar with a null colour (the accent is drawn); two calendars with one colour (both listed by name); a calendar whose
  display name is empty (its account name is shown)
- Time: `settings put system time_12_24 24` (restore) → every time reads "H:mm"; a locale change re-labels months and the
  first day of the week without a restart; the clock jumped backwards a year (the views follow; no event is lost — the
  one backwards jump in this phase: its sub-step runs `ring_save`, `am force-stop app.tileshell` and `ensure_start`
  before its MARK, r3 V4)
- Opening from intents: the Calendar tile's tap lands on today; `am start -a android.intent.action.INSERT -t
  vnd.android.cursor.dir/event --es title Lunch --el beginTime <ms> --el endTime <ms>` opens the editor prefilled (and
  saves nothing until Save is tapped; a `calendar_id` extra is ignored — E22, r3 V18); a
  `content://com.android.calendar/events/<id>` VIEW opens that event; an EDIT on an event outside Tessera opens it
  read-only (E22); a malformed URI opens today with no crash; the Agenda pod's header tap opens the shell's Calendar on
  today (E1, r3 D8)
- People with 5,000 contacts (a driver loop; the list's dump of the top rows is available within 3 s, the jump grid still
  lands on its letter); a contact with 20 numbers (all listed on the card with their labels); a 20 MB contact photo (the
  provider's thumbnail is drawn in the list, the full photo on the card, decoded at bounds); a contact deleted while its card is
  open (the card closes with a notice); a contact linked across two accounts and then one account removed (the remaining raw
  contact stays a contact); editing a field on an aggregated contact writes to ITS raw contact and never splits the aggregate
  — and only when that raw contact is editable (Q-16-3: a field of a raw contact in an account not on "Can edit" is shown
  read-only in the editor; E28's mixed contact)
- "Can edit" (Q-16-3, 2026-09-30): an
  account removed from the phone (its row leaves the list and `people_edit.json`; re-added, it starts unticked);
  `people_edit.json` lost (`run-as … rm`: nothing is allowed again, phone-only contacts still editable); a starred /
  favourite change on a contact with one read-only raw contact (refused, the JVM test's case); a contact found by the
  work-profile search (always read-only)
- Non-Latin names and their buckets (张伟 under the provider's label, Zoë under Z, "Ærøskøbing" wherever `PHONEBOOK_LABEL`
  puts it); a name with only a family name; a company-only contact (organisation shown as the name); a number stored with
  spaces and dashes matches a search typed without them (the provider's phone lookup)
- `WRITE_CONTACTS` granted while READ is revoked ~~(impossible in one group, but asserted: the app treats the group as
  one)~~ (RE-CUT 2026-09-30, r3 V12: reachable by adb — `pm revoke` is per permission, and E18 produces it — and it reads
  MISSING: People says it cannot read contacts and offers the grant, `checklist:people:missing`);
  READ revoked mid-edit (`pm revoke` ends the shell's process, so the edit is lost and nothing half-written is in the
  provider; People reopens on its cannot-read notice)
- SIM removed while importing (`adb emu` cannot eject; P4 runs it on the phone — r3 V6); a SIM contact with no number; a SIM
  contact that already exists (imported again as a second raw contact — the provider may aggregate it; either outcome is
  `record`ed, no crash)
- Groups (T16-14): a group with no members ("Text the group" shows no compose, a notice instead — approximation, H16); a
  member with no mobile number (left out of the `smsto:` list; none left → the notice); a group under a read-only account
  — since Q-16-3, any account not on "Can edit" — (no rename or delete action is offered on its page, and the page says
  why as the read-only card does; was "rename and delete refused with a notice, `group <op> <id>: failed <err>`"); a
  group deleted by another app while its page
  is open (the page closes with a notice, the list refreshes through the observer)
- Share when no app receives `text/x-vcard` (the resolver's empty state, Android's); share of a contact with a photo (the
  vCard carries `PHOTO;ENCODING=b`)
- The People tile: a contact photo removed while its bubble is on screen (the next event uses another photo; the current
  bubble finishes); every photo removed mid-cycle (the static pattern after the current event); a photo that fails to decode
  (skipped, `[people] tile: photo <lookup> skipped: <why>`, T16-19 — not storable on a device, so a JVM test of
  `PeopleFeed`'s skip carries it, r3 V6); the tile pinned small / medium / wide (the face scales
  per phase 01's tile rules; the bubble geometry is R3 A9's — r11/people.md P5.8 cites it and adds none — an approximation
  under H3)
- Phase 06 not yet built: People's Text action goes to the SMS role holder (the Fossify fixture); Call goes through Telecom
  to whatever dialer holds the role; both re-run when 06 lands (its E5's form)
- Liveness (N-01): reboot, 24 h idle, Device care, force-stop (the reminder receiver is manifest-registered and needs no
  process; the People feed restarts with the shell)

## QA evidence
_None yet._
