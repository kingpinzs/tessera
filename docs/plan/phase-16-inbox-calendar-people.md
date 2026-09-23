---
phase: 16
slug: inbox-calendar-people
status: DRAFT   # Stage A step 3 (split) 2026-09-22; interview (step 4) done 2026-09-23; review round 1 triaged 2026-09-23 (review/2026-09-23-phases11-19-triage.md, applied here); R11 gates FINAL: r11/calendar.md, r11/people.md (pending on disk 2026-09-23 — docs/plan/r11-inbox-apps.md is the index; r11/src/calendar/ and r11/src/people/ are still empty)
depends-on: [01, 02, 03, 15]   # C-1 2026-09-23: 15 for its build task 0 (the live-tile routing fix), which must land before this phase's slot seed (was "17 for its live-tile routing fix only"); C-2: this phase keeps the assignSlotOnce guard (task 1) and 17 depends on 16
---

# Phase 16 — W10M inbox apps II: Calendar and People

## Goal
Two Windows 10 Mobile (final release) inbox apps live inside the shell APK as launcher activities, like phase 10's
Music. **Calendar** shows every calendar in Android's CalendarProvider and writes only to the shell's own LOCAL calendar
— the one phase 03's `LocalCalendar` already creates for Tess
(`app/src/main/kotlin/app/tileshell/feeds/LocalCalendar.kt`, J6) — with a tapped, allow-listed Sync as the only road
from it to an account calendar, never the work one (interview Q2 D). It shows the views r11/calendar.md records, with
all-day, multi-day and recurring events, per-event reminders that notify at their time, and it takes over the CALENDAR
slot so phase 01's Calendar tile (R3 C3, already built) opens it. **People** is the W10M People hub over Android's
Contacts provider (Q1): an A-Z list with the jump grid, search, a contact card whose actions call, text, mail and map,
an editor with photo, link / unlink, SIM import and vCard share; it takes over the PEOPLE slot, its tile gets W10M's
measured photo-bubble face (R3 A9), and phase 06's "Phone book" and History contact buttons land in it. Tess's calendar
and contact actions keep reading the same providers, and Tess's "add ... to my calendar" writes only into that same
local calendar (J6, already in code: `ActionLayer.insertEvent`, `cortana/action/ActionLayer.kt:485-487`). Every screen
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
  contact and lookup URIs, `ACTION_INSERT` / `ACTION_INSERT_OR_EDIT` on contacts, `ACTION_PICK` on contacts and phone numbers
- Slot takeover: the CALENDAR and PEOPLE slots seeded once to the shell's apps (`LayoutStore.assignSlotOnce`, one marker per
  slot after `slot:music:v1`), never over a user's explicit choice — which needs a guard `assignSlotOnce` does not have today
  (Decisions; C-2: the guard stays this phase's task 1, and phase 17's seed runs under it); re-cuts phase 01 E4 / E4b (named
  in E1); the phase baseline `qa/phase-16/baseline_layout.json` carries the two new markers (C-3)
- Calendar: the views r11/calendar.md records; day paging; a date picker; Today; every calendar in the provider shown, every
  account calendar read-only (Q2 rule 1); the event editor (title, location, start / end, all day, repeat with the
  provider's RRULE, reminder minutes, notes) whose calendar is always the shell's local calendar — no calendar picker offers
  another (Q2 rule 2); edit one occurrence / this and following / all, and delete, on local events only; the local
  calendar is phase 03's `LocalCalendar` reused, never a second one (T16-2); Sync on a local event to a calendar allowed on
  the "Can sync to" page, the "synced to <calendar>" marker, and the delete choice for a synced event (Q2 rules 3–4; the
  T16-1 / T16-3 Decision lines); per-event reminder notifications from the provider's `ACTION_EVENT_REMINDER` broadcast (no
  scheduler of the shell's own, Rule 16); a settings page with the first day of the week and "Can sync to"; the Birthdays
  calendar (Q3)
- People: the A-Z list with the provider's phonebook buckets and phase 01's jump grid (X8), search by name and number, the
  contact card (photo, numbers with call / text, emails with mail, addresses with map, birthday, notes, organisation), create /
  edit / delete with `WRITE_CONTACTS`, photo from Android's photo picker (`MediaStore.ACTION_PICK_IMAGES`, phase 03's route),
  link / unlink through `AggregationExceptions`, share as vCard (`ACTION_SEND text/x-vcard`, phase 06's attach form), import
  from SIM (`content://icc/adn`), filter contact list by account / group, work-profile contacts through the enterprise search
  URI where the profile allows it
- The People tile face (an ADD to phase 01's live tiles, Change Log when built): R3 A9's photo-bubble event with contact
  photos, the static circle pattern without; fed by a `PeopleFeed` that reads contact photos the way `PhotosFeed` reads images
- Tess: `Contacts.byName` and the person reminders unchanged (regression row); `ActionLayer.insertEvent` already writes only
  to the shell's local calendar through `LocalCalendar.id(context)` (J6, `cortana/action/ActionLayer.kt:487`; device row
  qa/phase-03/scripts/j6.sh) — this phase adds nothing to it and re-runs J6 as E9's account-calendar negative
- Phase 06 hand-off: "Phone book" opens the People slot app (already phase 06's text), History rows' contact-card button and
  Messaging's "Contact" attach pick target the shell's People component explicitly (no chooser); People's Call goes through
  `TelecomManager.placeCall` so phase 06's in-call UI shows it once it holds the role
- Manifest ADD: `WRITE_CONTACTS`; Tess's checklist's existing `contacts` row (phase 03's, `cortana/CortanaChecklist.kt:43`;
  corrected 2026-09-23 — it is not a Setup checklist row) asks READ and WRITE together, reads PARTIAL with READ only as the
  `calendar` row beside it does (`:44-53`), and its detail line names editing (an ADD, Change Log); no new calendar row
  (phase 01's `calendar` row and phase 03's calendar read / write rows already exist). C-4: that row is phase 12's Tess
  step `wizard_step:tess:contacts` (a PARTIAL is not done — only Photos' is, phase 12's `partialIsDone`), its why line
  names editing, and `qa/phase-03/scripts/provision.sh` gains `adb shell pm grant app.tileshell
  android.permission.WRITE_CONTACTS` (belt-and-braces beside its `install -r -g`, `provision.sh:35`). Phase 12's
  persistence rule applies: on an install that has finished or skipped the wizard, the widened row does not summon it — it
  shows its state on the checklist (phase 12 Decisions "Persistence", H4)
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
- 2026-09-23: Interview Q3 — a Birthdays calendar (Jeremy: "(a)"): read-only, local, kept in step with contacts' birthdays, so
  the Calendar tile and Tess's "what's on my calendar" see it. It is never offered by Sync (Q2 rule 3 applies to events the user
  created, and a derived calendar is not one).
- 2026-09-23: The App Shortcuts under the phase 11 Q1 standing rule (agent; Jeremy can overrule): Calendar — Agenda, Day,
  Month, New event; People — Contacts, New contact.
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
  actions already act on the provider, so "targeting the shell's app" means the app and Tess share one calendar (agent)
- 2026-09-22: **`assignSlotOnce` has no guard for an existing explicit assignment** (agent, found at the split): its body
  checks only the marker (`if (marker in layout.addedOnce) return`) and then writes `explicitSlots + (slot to component)`, so on
  an upgrade from a build where the user had already pointed the CALENDAR or PEOPLE slot at an app by hand, the seed would
  overwrite that choice — the opposite of the R10 call. Build task 1 ADDs the guard to `LayoutStore.assignSlotOnce` (phase 01's
  part, Change Log): when `explicitSlots` already holds the slot, record the marker and keep the user's component, logging
  `assignSlotOnce <marker> <slot> -> kept user's <component>`. The same hole exists for `slot:music:v1` and closes with it.
  E1's upgrade row proves it both ways
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
  r11/calendar.md" or "from r11/people.md" (not yet written: R11 is running and neither file exists on disk 2026-09-23;
  docs/plan/r11-inbox-apps.md is the index, C-12); (2) already measured and cited: the drawn status bar 28 epx (R3 C4,
  `SystemBars.STATUS_EPX`) and nav bar 48 epx (X6, `SystemBars.NAV_EPX`); the Calendar tile face (R3 C3, MEDIUM: day
  name in the 15-epx body class centred with its top 33 epx below the tile top, the day number 30 epx tall centred at 50
  epx; already built in `CalendarFeed` / `TileFace.CalendarDay`); the People tile motion (R3 A9, MEDIUM/LOW, S5 14393
  camera 24 fps: a photo bubble slides out left in ≈333 ms, ≈6-frame pause, a new bubble slides in from the right
  settling in ≈583 ms, the event 1.88 s, repeating every 7.7 ± 0.2 s; the static circle pattern without photos; C3
  "circle pattern; photo bubbles as in A9"); list rows at a 44-epx pitch with a 41-epx icon and text at x 57 epx (R3 C2,
  R6 §5.1.4; `AppListMetrics`); phase 06's History row as the contact-row stand-in (R7 §1.3, HIGH: 48-epx circular
  avatar centred 36.2 epx from the left, text column 74–75.4 epx, 70.5-epx pitch, no separators; a grey disc with the
  initial without a photo); the jump grid (X8, R3 C2 LOW); the type ramp (R1 §5.1); the pivot header (phase 10 task 6's
  P4 design, `MusicMetrics`) and settle (X13); the app bar (R7 §3.5.8); the flyout (R7 §2.2.5, §3.6.2); the dialog (R7
  §1.3.9); the empty-list line (R7 §3.5.9); the outlined fields of the reminder page (R7 §3.7.1: 43.4 epx tall at a
  53.6-epx pitch) as the editor-field stand-in; sliders (X23); the row press (X19); or (3) an approximation with its
  NEEDS-HUMAN row. When R11 lands, each (2) stand-in is replaced where R11 measured and stays tagged where R11 is
  UNMEASURED. H-row kinds: **[fidelity]** (matches R11 or R3 A9 within tolerance, judged on the phone) and **[accept]**
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
  the PROVIDER schedules them and broadcasts `CalendarContract.ACTION_EVENT_REMINDER` (data `content://com.android.calendar/time/
  <ms>`) to every receiver holding `READ_CALENDAR`, and re-schedules on boot. The shell registers that receiver and posts one
  notification per alert on a calendar channel (look from r11/calendar.md if a reminder toast was captured, else approximation,
  H7), marking the alert row in `calendar_alerts` fired and, on dismiss, dismissed. No alarm of the shell's own is armed for
  calendar events, so `ReminderScheduler` is untouched here. Consequence, stated: any OTHER calendar app on the phone that
  registers the same receiver also notifies — on the AVD the AOSP Calendar fixture does (E6 records the double), on the phone
  Samsung Calendar will (P1); the fix is the user turning that app's notifications off, an Android limit. Added 2026-09-23
  (T16-4): the shell notifies for EVERY calendar's alerts, account calendars included, since the app shows them all under
  Q2 D — so P1's double also covers Samsung Calendar's own events; that is the rule, not a defect. The Birthdays calendar's
  events carry no `Reminders` rows (agent), so nothing fires for them
- 2026-09-22: Recurrence and time zones (agent). Repeat writes `RRULE` (+ `DURATION` instead of `DTEND`, as the provider
  requires) and the provider expands `Instances`; "edit this occurrence" writes an exception event (`ORIGINAL_ID` /
  `ORIGINAL_INSTANCE_TIME`), "this and following" ends the series with `UNTIL` and starts a new one, "all" edits the master.
  Timed events carry `EVENT_TIMEZONE` and display in the device zone; all-day events are date-anchored (UTC midnight, `ALL_DAY`
  = 1) and keep their date across a zone change. The first day of the week defaults to the locale's (`WeekFields.of(locale)`)
  with a setting on the app's settings page (W10M's Calendar had one; wording from r11/calendar.md if captured, else
  approximation, H6). An event with no title shows "(No title)" (approximation, H6). Views window their queries to the
  visible range (a month grid asks the provider for that month's instances only), which is what keeps thousands of events
  cheap (E7)
- 2026-09-22: Birthdays (Q3 A, ruled 2026-09-23). A read-only local "Birthdays" calendar the app keeps in step with
  contacts' birthday fields (`ContactsContract.CommonDataKinds.Event`, `TYPE_BIRTHDAY`; a yearly all-day event per contact,
  updated from a `ContentObserver` on the Contacts provider), so the Calendar tile and Tess's "what's on my calendar" see
  birthdays too; its access level is READ so the editor refuses it. W10M's Outlook Calendar had exactly this calendar (agent).
  Added 2026-09-23 (T16-2 line 2): it lives under its OWN LOCAL account name, `Tessera Birthdays`, never `Tessera`, because
  `LocalCalendar` finds the shell's calendar by account type LOCAL + account name `Tessera` and takes the first match
  (`feeds/LocalCalendar.kt:26-34`); its events carry no reminder rows (T16-4)
- 2026-09-22: People over the provider (agent; Q1 A, ruled 2026-09-23). List order and letter buckets come from
  the provider (`SORT_KEY_PRIMARY`, `PHONEBOOK_LABEL`), so non-Latin names file where Android files them, and the jump grid
  (X8) is built from those labels rather than from phase 01's `AppIndex`; a contact with no name shows its number or e-mail
  as its name under "#" (approximation, H8). Duplicates are the provider's aggregation: two raw contacts it merged show as one
  row; Link writes `AggregationExceptions` `TYPE_KEEP_TOGETHER`, Unlink `TYPE_KEEP_SEPARATE`. New contacts go to the account
  the "filter contact list" default names, else the local (null-account) raw contact set, as AOSP Contacts does. Card actions:
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
  check against phase 01's slot-tile content rules, recorded here
- 2026-09-22: Bars (agent, phase 01's bar rule of 2026-09-17): every Calendar and People page hides Samsung's bars and draws
  the W10M status bar and the Back / Windows / Search nav bar; Back is Back for the page, Windows goes Home
- 2026-09-22: Harness contracts (agent; R10 testability 24, 25, 33, 34). `testTagsAsResourceId` on every window root; tags on
  the node that carries each read text: `cal_view`, `cal_day:<yyyy-mm-dd>`, `cal_event:<id>`, `cal_event_title:<id>`,
  `cal_month_cell:<yyyy-mm-dd>`, `cal_editor_field:<name>`, `cal_calendar_row:<id>`, `people_row:<lookup>`,
  `people_name:<lookup>`, `people_letter:<label>`, `people_card_action:<kind>:<n>`, `people_field:<name>`, `people_link_row:<raw>`,
  `people_sim_row:<n>`; added 2026-09-23 by the review triage: `cal_sync`, `cal_sync_target:<id>`, `cal_synced_marker:<id>`,
  `cal_settings_can_sync:<id>` (T16-1), `cal_event_action:<edit|delete|sync>`, `cal_delete_choice:<here|both>`,
  `cal_view_mode:<agenda|day|month>`, `cal_editor`, `people_page:<list|editor>` (each page tag `selected="true"` on the
  page showing; E22–E25); drivers symlink qa/phase-03/scripts/lib.sh and stay adb-driven. Diagnostics lines: `[calendar]
  calendars: n (local created id=<id> | local present | none)`, `[calendar] view <name> <from>..<to>: n instances`, `[calendar]
  write <op> event=<id>: ok|failed <err>`, `[calendar] reminder event=<id> minutes=<n>: notified|dismissed`, `[calendar]
  birthdays: n synced`, `[people] list: n contacts read=<bool> write=<bool>`, `[people] search "<q>": n (+m enterprise)`,
  `[people] write <op> raw=<id>: ok|failed <err>`, `[people] link <a>+<b>: ok|failed`, `[people] sim import: n of m`,
  `[people] tile: n photos`, plus `LayoutStore`'s own `assignSlotOnce …` line with the new `kept user's` form; added
  2026-09-23: `LocalCalendar`'s own `[calendar] local calendar created: <uri>` / `… lookup failed: <err>` / `… could not be
  created: <err>` (`feeds/LocalCalendar.kt:34,55,57`), `[calendar] sync event=<id> -> calendar <id>: ok | updated |
  recreated | failed <err> | refused (not allowed)` and `[calendar] sync event=<id>: no calendar allowed -> can sync to`
  (T16-1, T16-3), `[people] tile event <n> t0=<uptime>` (T16-8) and the `[motion]` clock of the Acceptance preamble (C-5).
  Start while the People tile cycles never idles, so its dumps go through phase 05's gesture driver (C-10, Acceptance
  preamble)
- 2026-09-22: APK budget (agent; phase 03's ≤ 600 MB): code only; E15 records the delta, ≤ 2 MB, no new asset ≥ 1 MB
- 2026-09-22: App-list regression (agent; R10 testability 35): two new entries; E2 runs the phase-02 `regress.sh` pattern
  and asserts no "New" caption (X14)
- 2026-09-23 (review triage T16-2, doc update; J6 is built in code, outside the phase docs): **the shell's local calendar is
  phase 03's `LocalCalendar`, reused.** (1) One finder / creator: `LocalCalendar.id(context)`
  (`app/src/main/kotlin/app/tileshell/feeds/LocalCalendar.kt:24`; the triage's `idOrCreate()` is this function) finds the
  calendar and creates it through the sync-adapter URI when absent; `ActionLayer.insertEvent`
  (`cortana/action/ActionLayer.kt:487`) and the Calendar app share it, and the app never creates a second. J6's constants
  replace this doc's old ones (name "Calendar" in "Calendar data", `account_name=Tessera` in the edge case): account name,
  calendar name and display name `Tessera` (`LocalCalendar.ACCOUNT_NAME`, `:21`), `ACCOUNT_TYPE_LOCAL`, owner access,
  visible, colour #0063B1 (`:43-52`); H4 judges the name and colour. (2) The find keys on those constants (account type LOCAL
  + account name `Tessera`, `:26-34`), never on "any LOCAL calendar": the QA driver's LOCAL `qa` calendar and the Birthdays
  calendar (Q3) are LOCAL too and must never be picked, so Birthdays lives under its own account name (Decisions
  "Birthdays"). (3) If the calendar cannot be found or created (the provider refuses or is disabled, WRITE_CALENDAR is
  revoked), Tess refuses with the spoken notice "I don't have a calendar to add that to." (`ActionLayer.kt:487`) and never
  falls back to another calendar (J6's rule); the app shows the same state — its editor refuses to save with that notice
  and its views still show what the provider holds. (4) Under Q2 D the local calendar always exists once the app or Tess
  has run, since it is the only write target: the app calls `LocalCalendar.id` at start, so it is created even when other
  calendars exist (E3 inverted), and the app logs `local present` when Tess created it first; removed by another app, it is
  recreated at the next start or write whatever else remains (the edge case re-cut). (5) E9 cites J6's row
  (qa/phase-03/scripts/j6.sh; evidence qa/phase-03/J6/J6.txt, 7/7 on 2026-09-23) as its account-calendar negative, re-run
  on this phase's build with its local-calendar lookup keyed on `account_name=Tessera` (build task 8), instead of a new row;
  P2 gains the same on the phone. (6) Build task 3 reuses `LocalCalendar` — no second creator; the INDEX Change Log line for
  J6 (the lead's, written when J6 lands) is the record of the ADD to phase 03 that Q2 rule 2 names.
- 2026-09-23 (agent, review triage T16-1): **Sync (Q2 rules 3–4), designed.** The mapping lives in the shell's own
  `calendar_sync.json` (files dir, temp-file-and-rename like `LayoutStore`): local event id → {target calendar `_ID`,
  account name, account type, copy event id, last-pushed hash of the copied fields}; `Events.SYNC_DATA*` and `_SYNC_ID` are
  sync-adapter columns and are not used. The copy is a normal-app insert into the target calendar (no
  `caller_is_syncadapter`), so the provider marks it dirty and the account's own sync adapter uploads it. A re-Sync updates
  the copy by its id when the hash differs (`updated`) and recreates it if it is gone on the other side (`recreated`, the
  notice says so); an edit made on the other side is overwritten by the next Sync (local is the source). Deleting a synced
  local event asks "Delete here" (the default) or "Delete here and from <calendar>" — the second is still a user tap, so rule
  3 holds. An allowed calendar removed from the phone: Sync refuses with "That calendar is no longer on this phone"
  (`failed calendar gone`) and the marker stays with a warning glyph. Recurring events copy RRULE / DURATION / EXDATE, their
  exception events (`ORIGINAL_ID` re-pointed at the copy's master) and their reminders. The "Can sync to" rows list the
  provider's non-LOCAL calendars, keyed on `_ID` + account name + account type and stored in `calendar_sync.json`'s
  `allowed` list, so a removed and re-added account (new ids) starts NOT allowed; the shell's own calendar and Birthdays are
  never listed. The marker reads "synced to <calendar>" (P4 design, H14). The write layer's guard: every insert, update and
  delete refuses a calendar id other than `LocalCalendar.id`'s, except a Sync push to an allowed id (`refused (not
  allowed)`), and a JVM test proves the refusal. Tags and lines in "Harness contracts"; rows E22–E24; P2 re-cut to the push.
  Reason: the sync-adapter columns are not a normal app's to write, a normal insert lets the account's own adapter do the
  upload, and "local is the source, every account write is a tap" is Jeremy's Q2 wording ("when something is added to the
  local calander I can click sync").
- 2026-09-23 (agent, review triage T16-3): the opt-in allow-list stays and its seam is removed: the first Sync with nothing
  allowed opens the "Can sync to" page directly with one line, "Choose which calendars Sync may use", and returns to the Sync
  picker when the user comes back — never a dead-end empty picker (`[calendar] sync event=<id>: no calendar allowed -> can
  sync to`; E23). H14 [accept] shows Jeremy the trade. Reason: Jeremy's "never" — only an opt-in list makes a mis-tap on the
  work calendar impossible — and going straight to the page is one step instead of an empty picker and a hunt (P2).
- 2026-09-23 (agent, review triage C-1 / C-2 / T16-5): the live-tile routing fix is phase 15's build task 0, so depends-on is
  `[01, 02, 03, 15]` and build task 1's precondition is "phase 15's task 0 is built"; the `assignSlotOnce` guard stays this
  phase's build task 1 with its phase 01 Change Log line, and phase 17 depends on 16 and seeds under it. Reason: the inbox
  order stays 15 → 16 → 17 (the smallest change), and the guard belongs to the first phase that seeds a slot a user may
  already have assigned by hand.
- 2026-09-23 (agent, review triage C-5): **the shell logs its own motion clock.** The People tile logs `[people] tile event <n>
  t0=<uptime>` for each bubble event and `[motion] people_bubble_out | people_bubble_in t0=<uptime> settle=<ms>` for its two
  slides; every other motion (a pivot settle, a view change) logs `[motion] <name> t0=<uptime> peak=<ms> overshoot=<%>
  settle=<ms>` from `withFrameNanos`; E11 and E19 assert those numbers against RV11's tolerance and a screenrecord only
  corroborates under phase 05's frame-spacing rule. Reason: the P02 lesson — the emulator's screenrecord is variable-rate
  (qa/phase-05/README.md) and cannot time a 333-ms slide.
- 2026-09-23 (review triage C-7 / T16-10, doc update): the 2026-09-22 R10-Q4 line above gives A11 as the reason for no
  sign-in and no sync of the shell's own; A11 is amended (PLAN.md 2026-09-23: internet is fine, offline preferred), so it is
  no longer the reason — those exclusions stand as offline-preferred calls, Jeremy can ask. That line is Jeremy's ruling and
  is left as written; "synced calendars are other apps' sync adapters" stays true.

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
are proven before anything depends on the slots.
1. **Slot seeding and the guard (touches phase 01).** Precondition: phase 15's build task 0 (the live-tile routing fix) is
   built and its Change Log entry written (Decisions; C-1 — was "phase 17's build task 2", SUPERSEDED 2026-09-23). Then the
   `assignSlotOnce` guard in `LayoutStore` (`tiles/LayoutStore.kt:127-139`: keep an existing explicit assignment, record
   the marker, log `kept user's`; C-2 keeps it here); markers for CALENDAR and PEOPLE claimed from `ShellApp` as
   `claimMusicSlot` does (`ShellApp.kt:122,185`); the phase 01 E4 / E4b re-cut and the upgrade row (E1). INDEX Change Log
   line.
2. **App identities and contracts.** Two launcher activities with their categories and intent filters; app-list entries;
   Uninstall exclusion; exported allow-list ADDs; `testTagsAsResourceId`; `WRITE_CONTACTS` in the manifest; Tess's
   `contacts` checklist row (`cortana/CortanaChecklist.kt:43`) asking READ + WRITE, PARTIAL on READ only, its detail naming
   editing (Change Log); C-4: its phase 12 why line naming editing, `qa/phase-03/scripts/provision.sh` gains `adb shell pm
   grant app.tileshell android.permission.WRITE_CONTACTS`, phase 12 E1 re-run on this build (E26); the APK size before.
3. **Calendar data layer.** The local calendar is phase 03's `LocalCalendar.id(context)` reused
   (`feeds/LocalCalendar.kt:24`, J6) — no second creator (T16-2) — called at start; the calendar list with colours, every
   calendar but the local one read-only; windowed `Instances` queries per view; event write / edit / delete with recurrence
   exceptions on the local calendar only, behind the write guard of the T16-1 line (with its JVM test); Sync — the
   `calendar_sync.json` mapping and `allowed` list, the copy insert / update / recreate, recurring copies with exceptions and
   reminders, the delete choice, the removed-calendar refusal — and its `[calendar] sync` lines; the `ACTION_EVENT_REMINDER`
   receiver and its notification channel; the Birthdays calendar (Q3) under its own account name `Tessera Birthdays`, with
   no reminder rows.
4. **Calendar app.** The views from r11/calendar.md, day paging, the date picker, Today, the editor and its fields (its
   calendar is always the local one; no picker offers another), the event page's actions (`cal_event_action:*`: edit and
   delete on local events only, Sync on local events), the Sync picker and the "synced to" marker, the first Sync routed to
   "Can sync to" when nothing is allowed (T16-3), the settings page (first day of week, "Can sync to"), every value from
   r11/calendar.md once it lands; phase 03 E2's calendar rows re-run (E9).
5. **People data layer.** Provider reads with phonebook labels, search (plus the enterprise filter), the card's data kinds,
   writes under `WRITE_CONTACTS`, aggregation exceptions, the SIM (`content://icc/adn`) reader, the vCard writer for share.
6. **People app.** The list with the jump grid (X8), search, the card and its actions, the editor with the photo picker,
   link / unlink, SIM import, filter contact list; the explicit-component hand-offs from phase 06's buttons (an ADD to phase
   06's targets when it is built, or a targeted intent contract recorded now); values from r11/people.md once it lands.
7. **People tile (touches phase 01).** `PeopleFeed`, the A9 bubble face and the static pattern, published under the PEOPLE key,
   with the `[people] tile event` and `[motion]` lines (C-5); INDEX Change Log line; E11 measures it.
8. **Diagnostics, regression, evidence.** Every diagnostics line in Decisions, the app-list regression run, the APK size
   after. The phase baseline (C-3): `qa/phase-16/baseline_layout.json` derived from the newest baseline on disk at build
   (phase 15's), with `addedOnce` gaining `slot:calendar:v1` and `slot:people:v1`, `slots` gaining CALENDAR and PEOPLE → the
   shell's two activities (what the seed writes), `manualSizes` kept; the file it came from kept beside it as
   `qa/phase-16/baseline_layout-pre-16.json`. The pre-16 APK for E1's upgrade pass kept at `qa/phase-16/upgrade/<tag>.apk`
   with its git tag in the row's log (T16-7). J6's driver lookup of the local calendar keyed on `account_name=Tessera`
   (qa/phase-03/scripts/j6.sh reads the first `account_type=LOCAL` row, which the Birthdays calendar would also match) so E9
   can re-run it on this build.
9. **App Shortcuts (phase 11 Q1's standing rule; C-8).** A static `shortcuts.xml` per app, ranks 0–3, each targeting its
   activity with the page extra (`page`, the key `SettingsActivity.EXTRA_PAGE` already uses,
   `settings/SettingsActivity.kt:117`): Calendar — ids `agenda`, `day`, `month`, `new_event` (Agenda, Day, Month, New
   event); People — `contacts`, `new_contact` (Contacts, New contact). E25 proves them.

## Acceptance criteria
Rows start from the baseline state and restore what they change (PLAN RV12); motion rows follow RV11; dumps follow RV13,
except Start while the People tile cycles (E1's Music negative once photos exist, E11), which never idles and is dumped
through phase 05's gesture driver (`UiDevice.dumpWindowHierarchy` with `Configurator.setWaitForIdleTimeout(0)`,
qa/phase-05/README.md; C-10). **Motion clock (C-5):** every motion the shell animates logs `[motion] <name> t0=<uptime>
peak=<ms> overshoot=<%> settle=<ms>` from `withFrameNanos` (the People tile also `[people] tile event <n> t0=<uptime>`), and
a motion row asserts the logged numbers against RV11's tolerance; a screenrecord corroborates under phase 05's
frame-spacing rule (source-frame spacing ≤ 18.2 ms during the motion) and is never the primary clock. **Layout seeding
(C-3):** a row that needs Start's layout starts with phase 02's verified `layout_restore qa/phase-16/baseline_layout.json`
(qa/phase-02/scripts/layout.sh). **Wiped state (C-4):** every row that clears the shell reads `pm clear app.tileshell` →
`qa/phase-03/scripts/provision.sh` → Home, or it meets phase 12's `wizard_page`. **After a launch (C-6):** a row that opened
an app runs `am force-stop app.tileshell` + Home before its next assertion on Start's grid (the promoted recent app is in
memory only, qa/phase-01/scripts/recent0922.sh:19-21). **Recorded clauses (C-13):** a clause that records without gating
ends its PASS/FAIL line with "RECORDED", so `lib.sh`'s `row_end` never counts it as a pass.
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
restored per RV12; the time zone with `cmd alarm set-timezone <zone>` (restore to the recorded `getprop persist.sys.timezone`);
`pm list users` shows user 0 only, so the work-profile row creates a managed profile with phase 01 E18's commands and removes it;
the emulated SIM's phonebook (`content://icc/adn`) may refuse inserts — E10 records that and moves the import to P4; the Sync
rows' two account calendars are created as qa/phase-03/scripts/j6.sh's `mkcal` creates them (account type `com.google`
through the sync-adapter URI, access 700) and deleted the same way at the row's end. Geometry rows are R11-gated: until
r11/calendar.md / r11/people.md land they assert structure and the bars (R3 C4 / X6) and take their numeric assertions from
R11 when it lands; this doc cannot go FINAL before R11 (docs/plan/r11-inbox-apps.md; C-12).
**Emulator:**
- E1 Slot takeover and the guard (**re-cut of phase 01 E4 / E4b**, recorded in the INDEX Change Log when built): on a
  wiped state (`pm clear app.tileshell` → `qa/phase-03/scripts/provision.sh` → Home; C-4), the CALENDAR and PEOPLE slot
  tiles read "Calendar" and "People" and their taps resume the shell's Calendar and People activities (`dumpsys activity
  activities | grep -i resumed`; `am force-stop app.tileshell` + Home after each, C-6), diagnostics show `assignSlotOnce
  slot:calendar:v1 CALENDAR -> app.tileshell/<the Calendar activity> -> assigned` and the PEOPLE line, and `cmd package
  query-activities -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR` (and `APP_CONTACTS`) lists two
  handlers each (the AOSP fixture and the shell), so neither auto-assigns; Settings > Start re-points CALENDAR at the
  AOSP Calendar and it sticks across `am force-stop` and `adb reboot` (E4b's form). **Upgrade pass:** `adb uninstall
  app.tileshell`, `adb install -g qa/phase-16/upgrade/<tag>.apk` (the last pre-phase-16 build, kept there with its git
  tag in the log, T16-7) and the HOME / ASSISTANT roles and home activity as `provision.sh:50-54` sets them, assign
  PEOPLE to the image's Contacts through Settings > Start (dump), `adb install -r` the phase-16 build, Home → the PEOPLE
  tile still reads the image's Contacts and diagnostics show `assignSlotOnce slot:people:v1 PEOPLE -> kept user's
  com.android.contacts/…`; the CALENDAR slot, never touched by the user, is seeded to the shell's Calendar in the same
  start. Phase 01 E4's remaining proofs (Maps one-handler auto-assign; Mail and Store 2+-handler unassigned) re-run and
  pass on the same build. **Music negative (phase 15's build task 0 routing, Decisions):** with the shell's Music
  playing a fixture track (phase 10 E10's form), the CALENDAR tile still shows its `CalendarFeed` day face and the
  PEOPLE tile its People face (dump tags; screencap), neither tile has grown (`ActiveTiles` bounds unchanged), and only
  the MUSIC tile carries the now-playing face — the same build passes phase 10's E10 and E13. Restore: `pm clear
  app.tileshell` → `provision.sh` → Home, then `layout_restore qa/phase-16/baseline_layout.json`
- E2 App list: "Calendar" under C and "People" under P (dump), no "New" caption (X14), hold menus offer Pin to Start and NOT
  Uninstall (phase 10 E2's form), the jump grid marks C and P; `qa/phase-02/scripts/regress.sh`'s pattern passes; each window's
  dump carries resource-ids (`cal_view`, `people_letter:A`); `dumpsys package app.tileshell` matches qa/phase-03/exported-allowlist.txt
  after its ADDs (phase 03 E5's `scripts/exported.py`). **Baseline (C-3):** after `layout_restore
  qa/phase-16/baseline_layout.json` the ring holds ZERO `assignSlotOnce … -> assigned` lines and the restored `addedOnce`
  equals the file's, `slot:calendar:v1` and `slot:people:v1` included (`layout_json`)
- E3 Local calendar (Q2 D; T16-2 — phase 03's `LocalCalendar` reused, created whatever else exists): with `Tessera` deleted
  (the preamble's command) and no other calendar, opening Calendar creates it: `content query --uri
  content://com.android.calendar/calendars --projection _id:account_name:account_type:name:calendar_access_level:calendar_color`
  lists exactly one row — account_name Tessera, account_type LOCAL, name Tessera, access 700, colour -16751695 (#0063B1) —
  and diagnostics hold `[calendar] local calendar created: …` (`feeds/LocalCalendar.kt:55`) and `[calendar] calendars: 1
  (local created id=<id>)`; force-stop and reopen → still one (`local present`). **Inverted (T16-2 line 4):** with Tessera
  deleted and the QA calendar inserted first, `pm clear` → `provision.sh` → Home and opening Calendar STILL creates Tessera
  (two rows, `local created`), both list under `cal_calendar_row:`, and the QA calendar is read-only (an event on it has no
  `cal_event_action:edit` / `:delete`). **Tess first:** with Tessera deleted, J6's typed request ("add a meeting called
  standup to my calendar at ten AM", Add tapped) creates it; opening Calendar then logs `local present` and the query still
  shows one Tessera row. The editor's calendar field reads Tessera and offers no other calendar (dump); a calendar with
  access level 200 (READ) inserted by the driver lists read-only, as every calendar but Tessera does. Restore: the QA
  calendars deleted, Tessera kept
- E4 Events both ways (the `ContentObserver`, no restart): an event created in the editor (title "Standup", tomorrow 09:00–
  10:00, location "Room 2") → `content query --uri content://com.android.calendar/events --projection title:dtstart:dtend:
  calendar_id:eventTimezone:eventLocation` lists it in the local calendar with the device zone, and phase 01's Calendar tile
  shows its face within its next flip (E7's form; `CalendarFeed`'s observer); a driver `content insert` of "Dentist" today
  14:00 → the day view lists `cal_event_title:` "Dentist" within 2 s with no restart; delete from the app → the provider row is
  gone; `content delete` of Standup → its `cal_event:` node is gone within 2 s
- E5 All-day, multi-day, recurring (structure now, geometry from r11/calendar.md): driver inserts an all-day event
  (`--bind allDay:i:1`, dtstart at UTC midnight, eventTimezone UTC), a 3-day timed event, and a weekly series
  (`--bind rrule:s:FREQ=WEEKLY;COUNT=10 --bind duration:s:PT1H`, no dtend) → the day view shows the all-day event in its
  all-day band and not at a time; the 3-day event appears on each of its days; the agenda lists ten `cal_event:` instances of
  the series with `content query --uri content://com.android.calendar/instances/when/<start>/<end>` agreeing on the count;
  in the app, Repeat = weekly on a new event writes an `rrule` and a `duration` and no `dtend`; "edit this occurrence" on the
  third instance writes an exception row (`original_id` = the master, `originalInstanceTime` = that instance) and the agenda
  shows the changed title on that day only; "this and following" from the fifth sets `UNTIL` on the master (query shows it)
  and creates a new master; "delete all" removes the master and every instance
- E6 Reminders through the provider (Rule 16: no shell alarm): an event 30 min ahead with `content insert --uri
  content://com.android.calendar/reminders --bind event_id:i:<id> --bind minutes:i:10 --bind method:i:1`; `dumpsys alarm` shows
  the alarm under `com.android.providers.calendar`, NOT under `app.tileshell`; jump the clock to 5 s before T−10 min → within
  10 s `dumpsys notification --noredact` shows the shell's notification (title "Standup", the time, on the calendar channel),
  diagnostics `[calendar] reminder event=<id> minutes=10: notified`, and `content query --uri content://com.android.calendar/
  calendar_alerts` shows the alert state fired; the AOSP Calendar fixture's own notification is ALSO present (recorded,
  Decisions; that clause's line ends "RECORDED", C-13);
  dismissing the shell's marks the alert dismissed; `adb reboot` with a reminder 3 min ahead → after boot the provider re-armed it
  (`dumpsys alarm`) and it notifies at its time. Restore the clock per RV12
- E7 Thousands of events: a driver loop inserts 5,000 events across 24 months into the QA calendar (recorded run time); the
  month view of a month with 400 events renders (its dump available, `cal_month_cell:` nodes present) within 3 s of the page
  change (screenrecord frame count), paging 12 months forward with `input swipe` shows `dumpsys gfxinfo app.tileshell` janky
  frames ≤ 5 % over the run (phase 01's threshold, applied on the emulator as a bound not a phone measurement), a day with 200
  events lists them scrollably, diagnostics `[calendar] view month …: 400 instances`; the Calendar tile still shows only the
  next 24 hours' events (`CalendarFeed`'s window); delete the QA calendar afterwards (its events cascade)
- E8 Time zone and DST: an event at 09:00 America/Denver on a date after the next DST change; `cmd alarm set-timezone Asia/Tokyo`
  → the event shows at its Tokyo wall time (dump `cal_event_title:` with the time text) while the all-day event of E5 stays on
  its date; back to Denver; an event across a DST night (23:30–01:30 on the fall-back date) shows a 3-hour span in the day
  view (the provider's instance end) and the editor shows its stored end wall time; restore the zone
- E9 Tess on this AVD (**re-cut of phase 03 E2's calendar rows**, recorded in the Change Log when built): **the
  account-calendar negative is J6's row** (qa/phase-03/scripts/j6.sh; evidence qa/phase-03/J6/, 7/7 on 2026-09-23),
  re-run on this build with its local lookup keyed on `account_name=Tessera` (build task 8) and passing: Tess's event
  lands in Tessera and in neither QA account calendar; re-run again with E17's Birthdays calendar present → still
  Tessera, never Birthdays (T16-2 line 2). Then, with Tessera and no fixture calendar, "add a calendar event called
  dentist tomorrow at 2 pm" → the confirmation card (phase 03 E7's form) and on "yes" `content query …/events` lists
  "dentist" with Tessera's id (E2's observable, the id read from the `account_name=Tessera` query) and the reply "Added
  to your calendar."; "what's on my calendar" names the events inserted for the test; the Calendar app's day view lists
  the same event; phase 03 E10's gated calendar commands are unchanged and re-run passing ("Unlock to continue"). Phase
  03 E7 (text / call by name) and E14 (person reminder) re-run passing on the same fixtures — People changed nothing in
  `Contacts.byName`
- E10 People list, search, no-name and duplicates: fixtures Ann Lee (+1 555 000 0001, ann@example.com), Bob Stone, Zoë Ǻrén,
  张伟, a raw contact with only a number (+1 555 000 0009), and two identical raw contacts "Cara Diaz" (which the provider
  aggregates) → the list shows one Cara Diaz row (`people_row:` count), the number-only contact under "#" reading its number
  (`people_name:` text), 张伟 under the provider's phonebook label (the letter header text equals `PHONEBOOK_LABEL` from
  `content query --uri content://com.android.contacts/contacts --projection display_name:phonebook_label`), Zoë under Z; the
  jump grid (X8) marks only labels that exist; search "555 000 0001" and "ann" each return Ann Lee only (diagnostics
  `[people] search …: 1`); rows at 70.5 ± 1.2 epx with the 48-epx avatar centred 36.2 epx from the left (R7 §1.3 stand-in until
  r11/people.md), the initial on a grey disc where no photo exists
- E11 People tile (R3 A9; [fidelity] H3): after E13 gives three fixtures photos, over 40 s on Start the ring holds ≥ 4
  `[people] tile event <n> t0=<uptime>` lines spaced by the period 7.7 ± 0.2 s + one frame, and per event `[motion]
  people_bubble_out … settle=<ms>` ≈333 ms and `[motion] people_bubble_in … settle=<ms>` ≈583 ms, each within R3 A9's
  one 24-fps frame (± 42 ms) + one frame, with the event 1.88 ± 0.04 s + one frame from the out's t0 to the in's settle
  (the shell's clock, C-5 / T16-8); a 60-fps screenrecord of the same 40 s corroborates under phase 05's frame-spacing
  rule (RV11; never the clock); Start's dumps here go through phase 05's gesture driver (C-10); the bubbles show the
  fixtures' photos (pixel match against the pulled photos at tile scale); with every photo removed, 40 s of screencaps
  at 1-s intervals show no change on the tile (the static pattern) and diagnostics `[people] tile: 0 photos`. The tile's
  tap resumes the shell's People (E1)
- E12 Card actions: on Ann Lee's card, Call → `dumpsys telecom` shows an outgoing call placed by the shell and `adb emu gsm
  list` lists it (`gsm cancel` after); Text → the SMS role holder's compose (the Fossify Messages fixture) resumes with
  `smsto:` her number (`dumpsys activity activities`), or phase 06's thread once it holds the role; Mail with the K-9 fixture
  assigned to the Mail slot → K-9's compose resumes with `mailto:ann@example.com`; with the slot unassigned → Android's
  resolver; Address (an added postal row) with OsmAnd in the Maps slot → OsmAnd resumes with a `geo:` query; `am start -a
  android.intent.action.VIEW -d content://com.android.contacts/contacts/<Ann's id>` → the shell's People card is resumed
  (`dumpsys activity activities`); on the AVD the AOSP Contacts fixture is the other handler, so Android's resolver appears
  first and the driver taps the shell's entry and "Always" at their dump bounds, records that it was needed (that clause's
  line ends "RECORDED", C-13), and a second `am start` then resumes the shell's card with no resolver
- E13 Create, edit, photo, delete (`WRITE_CONTACTS`): New → "Dan Ford" with a mobile number → `content query --uri
  content://com.android.contacts/data --projection raw_contact_id:mimetype:data1 --where "mimetype='vnd.android.cursor.item/phone_v2'"`
  shows the number under a new raw contact; edit Ann's number → the data row changes; Photo → Android's photo picker
  (`dumpsys activity activities` shows the picker) → a pushed JPEG chosen → a `vnd.android.cursor.item/photo` data row exists
  for Ann and the card shows it; the same for Bob and Zoë (E11's fixtures); delete Dan → his contact row is gone; with
  `pm revoke app.tileshell android.permission.WRITE_CONTACTS` the editor says it cannot save and offers the grant in place
  (phase 10 E18's form), Tess's checklist's `contacts` row reads PARTIAL (the write state), and `pm grant` restores it
- E14 Link and unlink: fixtures "Sam Reed" (number only) and "Sam Reed" (e-mail only, different raw contacts the provider did
  not merge — asserted: two rows before) → Link from the first card picks the second → `content query --uri
  content://com.android.contacts/aggregation_exceptions --projection type:raw_contact_id1:raw_contact_id2` shows type 1
  (KEEP_TOGETHER) for the pair and the list shows one Sam Reed with both the number and the e-mail on the card; Unlink → type 2
  and two rows again; diagnostics `[people] link …: ok`
- E15 Share, SIM import, filter, APK: Share on Ann → `dumpsys activity activities` shows the resolver for `ACTION_SEND
  text/x-vcard` and `adb shell content read --uri <the stream uri>` yields text beginning `BEGIN:VCARD` with `FN:Ann Lee` and
  her `TEL`; SIM: `content insert --uri content://icc/adn --bind tag:s:Sim Bob --bind number:s:5550002` (if the emulated SIM
  refuses, recorded, and the import moves to P4) → People's Import from SIM lists `people_sim_row:` "Sim Bob" → import → a
  contact row exists with that number, diagnostics `[people] sim import: 1 of 1`; filter: a raw contact inserted with
  `--bind account_type:s:com.example --bind account_name:s:x` shows an account group in "filter contact list", unticking it
  hides that contact and the row count drops by one (restore); APK: `stat -c%s` before task 2 and after task 8 differ by
  ≤ 2 MB, `unzip -l` shows no new entry ≥ 1 MB
- E16 Work profile (phase 01 E18's commands to create, start and later remove a managed profile; P4 design H10): a contact
  "Work Wren" inserted with `content insert --user <id>` → the People A-Z list does NOT list her (`people_row:` absent), search
  "Wren" lists her with the briefcase glyph (`people_row:` present with an enterprise marker in its tag) — the provider's
  `ENTERPRISE_CONTENT_FILTER_URI`, diagnostics `[people] search "Wren": 0 (+1 enterprise)`; with cross-profile contact search
  disabled by the profile owner (`DevicePolicyManager.setCrossProfileContactsSearchDisabled`, which needs a device-policy
  fixture app installed as the profile owner; if none can be installed on this AVD, the row records it and the negative
  moves to P6), search finds nothing and says so
- E17 Birthdays (Q3): a birthday on Ann (`content insert … --bind mimetype:s:vnd.android.cursor.item/contact_event --bind
  data2:i:3 --bind data1:s:1990-09-23`) → within 5 s the provider has a "Birthdays" local calendar (account_name `Tessera
  Birthdays`, never `Tessera`; access 200) with a yearly
  all-day event "Ann Lee's birthday" (`content query …/events --where "calendar_id=<birthdays id>"` shows the rrule
  FREQ=YEARLY), the Calendar app shows it on 23 September, the tile shows it on the day (clock jumped to 2026-09-23 08:00,
  restored per RV12), and Tess's "what's on my calendar" names it; the editor refuses to edit it (read-only calendar);
  removing the birthday row removes the event; diagnostics `[calendar] birthdays: 1 synced`. The birthday event has no
  reminder rows (`content query --uri content://com.android.calendar/reminders --where "event_id=<its id>"` → "No result
  found"; T16-4), and a Tess "add" made while Birthdays exists lands in Tessera (T16-2 line 2)
- E18 Permission states: `pm revoke app.tileshell android.permission.READ_CALENDAR` (and WRITE_CALENDAR) → Calendar's page says
  it cannot read the calendar and offers the grant in place, the checklist's `calendar` row (phase 01's) is red, and no query
  runs (diagnostics `calendars: none` is NOT logged — a denied read logs `read=false`); `pm grant` restores it and the views
  load without a restart; `pm revoke … READ_CONTACTS` → People says so and offers the grant, Tess's `contacts` row is red
- E19 Calendar geometry (R11-gated; [fidelity] H1): the views, the month grid, the day rows, the editor, the reminder
  notification within r11/calendar.md's tolerance once R11 lands; now: the drawn status bar 28 epx (R3 C4) and nav bar 48 epx
  (X6) on every view and the editor (dump bounds; `dumpsys window` shows the system bars not visible, phase 01 E19's form), the
  editor fields at 43.4 epx on a 53.6-epx pitch (R7 §3.7.1 stand-in), the pivot settle 250 ± 17 ms + one frame (X13) where a
  pivot exists, from the shell's `[motion] pivot t0=… settle=<ms>` line (C-5; a screenrecord only corroborates), the app bar
  48.2 ± 1 epx with a 68-epx pitch (R7 §3.5.8)
- E20 People geometry (R11-gated; [fidelity] H2): the list rows, letter headers, the card and the editor within r11/people.md's
  tolerance once R11 lands; now the bars as E19, rows per E10's stand-in, the jump grid per X8 (5 columns, ≈50 ± 4 epx pitch)
- E21 Diagnostics coverage: every line named in Decisions appears in the ring at least once across E1–E18 and E22–E26 (a
  driver greps each pattern)
- E22 Sync rules 1–2 — no direct write to an account calendar by any path (Q2 D; T16-1): create "Personal"
  (qa.personal@example.com) and "Work" (qa.work@example.com, `isPrimary` 1) with j6.sh's `mkcal`, and driver-insert
  "Offsite" into Work. **Rule 1:** both list under `cal_calendar_row:<id>` (dump); "Offsite" shows in the day view and its
  event page offers no `cal_event_action:edit`, `:delete` or `:sync`; the editor's calendar field reads Tessera and no picker
  offers Personal or Work (dump). **Rule 2:** in the editor create "Standup" (tomorrow 09:00), rename it "Standup 2", create
  and delete "Scratch"; then Tess through j6.sh's typed request ("add a meeting called standup to my calendar at ten AM", Add
  tapped). After every step `content query --uri content://com.android.calendar/events --projection _id:calendar_id:title
  --where "calendar_id=<id>"` gives Personal 0 and Work 1 ("Offsite", title unchanged) while Tessera holds "Standup 2" and
  "standup"; every `[calendar] write … event=<id>: ok` line names a Tessera event. The write guard's JVM test (refuses an
  insert, update or delete on any id but `LocalCalendar`'s, and a Sync to an id not allowed, `refused (not allowed)`) runs
  and passes, its output in the row's log. Restore: Personal and Work deleted through the sync-adapter URI (j6.sh's restore
  form), the test events deleted
- E23 Sync rule 4 and the first Sync (T16-1, T16-3): `pm clear app.tileshell` → `provision.sh` → Home; create Personal and
  Work (E22's form), E17's birthday fixture present, and a local event "Standup"; tap `cal_event_action:sync` → the "Can sync
  to" page opens directly (dump: `cal_settings_can_sync:<personal id>` and `:<work id>` both unchecked, the line "Choose which
  calendars Sync may use", no `cal_sync_target:*` node), diagnostics `[calendar] sync event=<id>: no calendar allowed -> can
  sync to`; neither Tessera nor Birthdays is listed there. Tick Personal, Back → the Sync picker lists
  `cal_sync_target:<personal id>` only (no Work, Tessera or Birthdays); Back out without syncing → Personal and Work still 0
  (E22's query); `adb shell run-as app.tileshell cat files/calendar_sync.json` lists Personal's `_ID`, account name and type
  under `allowed`. **Re-added account:** delete Personal (sync-adapter URI) and create it again (a new `_ID`) → Sync on
  "Standup" opens "Can sync to" again with the new Personal unchecked. Restore as E22
- E24 Sync rule 3 — the tapped push (T16-1): with Personal allowed (E23's form), Sync "Standup" → Personal holds ONE event
  (title Standup, dtstart / dtend equal to the local event's; E22's query), Work 0, the local event shows
  `cal_synced_marker:<id>` reading "synced to Personal" (dump), `calendar_sync.json` maps the local id to Personal's `_ID` and
  the copy's id, diagnostics `[calendar] sync event=<id> -> calendar <personal id>: ok`, and the shell uid's byte counters
  in `dumpsys netstats --uid` are unchanged across the Sync (phase 06 E18's form). Rename the local event "Standup 2", Sync →
  the copy reads Standup 2, still one row, `updated`. The copy deleted on the other side (sync-adapter delete on Personal) →
  Sync → one copy again, `recreated`, and the notice says so. The copy's title changed on the other side (`content update`)
  → Sync → overwritten with the local title. A weekly local series (`COUNT=5`) with its third occurrence retitled and a
  10-minute reminder → Sync → Personal holds a master with the rrule and duration, one exception whose `original_id` is the
  copy's master, and a `reminders` row of 10 minutes on the copy. Deleting a synced local event shows `cal_delete_choice:here`
  (the default) and `cal_delete_choice:both`: "here" → the local event gone, the copy kept; on a second synced event "both" →
  both gone. Personal removed from the phone (sync-adapter delete of the calendar) → Sync on a synced event → "That calendar
  is no longer on this phone" (dump), the marker keeps a warning glyph, `failed calendar gone`. Work holds 0 of the shell's
  events after every step. Restore as E22, then `pm clear` → `provision.sh` → Home (clears `calendar_sync.json`)
- E25 App Shortcuts (build task 9; phase 11 Q1's standing rule, C-8): `layout_restore qa/phase-16/baseline_layout.json` (the
  CALENDAR and PEOPLE slot tiles resolve to the shell's apps); hold the CALENDAR tile by phase 11 E3's method →
  `quick_sat_label:0..3` texts equal Agenda / Day / Month / New event in rank order; the PEOPLE tile → `quick_sat_label:0..1`
  equal Contacts / New contact, no `quick_sat_label:2`; each burst's `[quick] shortcuts for app.tileshell/0: …` line names
  only that activity's ids; `tap_node quick_sat:<i>` for each → the app resumed (`dumpsys activity activities`) with
  `cal_view_mode:<agenda|day|month>` selected, `cal_editor` for New event (Back discards it; Tessera's event count
  unchanged), `people_page:list`, `people_page:editor` for New contact (Back discards it; the `raw_contacts` count
  unchanged); `adb shell dumpsys shortcut` lists the six ids for `app.tileshell` with ranks 0–3 per activity; `am
  force-stop app.tileshell` + Home after each launch (C-6). Restore: `layout_restore` the baseline
- E26 Wizard step for the widened `contacts` row (phase 12 E14's template, C-4): `pm clear app.tileshell` →
  `qa/phase-03/scripts/provision.sh` → `adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS` → Home → the dump
  has `wizard_page` with `wizard_step:tess:contacts` and `wizard_why` equal to phase 12's why line for `contacts` (naming
  editing); the step's action grants it (Android may grant WRITE at once, with no dialog, while READ in its group is held —
  whichever happens is recorded) → `[wizard] step tess:contacts: granted` and the step is gone. `pm clear` → `provision.sh`
  → Home (its new line grants WRITE) → no `wizard_page`, `[wizard] not shown: core held`; phase 12 E1 re-run passes on this
  build. Finished-install rule: revoke → Home → "Skip setup" (`[wizard] skip`); grant, revoke again, `am force-stop
  app.tileshell` + Home → no `wizard_page`, `[wizard] not shown: finished`, and Tess's Settings page shows `contacts`
  PARTIAL. Restore: `pm clear` → `provision.sh` → Home
**Phone-only (S25 Ultra):**
- P1 Event reminders on One UI 8: a reminder on an event in the local calendar notifies from the shell AND from Samsung
  Calendar (recorded; that clause's line ends "RECORDED", C-13), and an event on a Samsung or Google calendar notifies from
  the shell too (T16-4); with Samsung Calendar's notifications turned off in Android's settings only the shell's remains
- P2 Synced calendars and the push (re-cut 2026-09-23 by T16-1 / T16-2; was "an event written here to a synced calendar …",
  a direct write Q2 D forbids): every calendar Samsung Calendar shows (Samsung account, any Google or Outlook account the
  phone has — the shell adds none) appears in the shell's Calendar with its colour, read-only. J6 on the phone: Tess's "add
  … to my calendar" and an event made in the editor both land in Tessera and in no account calendar (`content query` over
  adb, each account calendar's count before = after). Jeremy ticks his personal calendar once on "Can sync to" (the work
  calendar stays unticked); Sync on a local event → the copy appears in that account's other client (web or another app)
  after the account's own sync adapter runs; the work calendar's count is unchanged across every step; `dumpsys netstats
  --uid` for the shell's uid is unchanged across the Sync (the upload is the account's adapter, not the shell)
- P3 The chooser: from Samsung Messages, a tap on a sender's contact shows Android's chooser with the shell's People listed
  and "Always" remembered; Samsung Phone's contact tap the same; recorded, not judged (its line ends "RECORDED", C-13)
- P4 SIM import with the real SIM (and E15's import if the emulated SIM refused inserts); contacts stored on the SIM appear
  and import once
- P5 Samsung Contacts' linked contacts show as one here and a link made here shows as one there (the shared
  `AggregationExceptions`)
- P6 Work profile: Jeremy has none (phase 01 Q5); recorded as not testable on the phone, with E16's emulator result standing
- P7 Liveness (N-01): the reminder receiver and the People tile feed survive reboot, 24 h idle and a Device care optimise
  (a reminder set the day before notifies on time; the tile still cycles)
**NEEDS-HUMAN:**
- H1 [fidelity] Calendar matches r11/calendar.md within tolerance on the phone
- H2 [fidelity] People matches r11/people.md
- H3 [fidelity] the People tile's bubble motion matches R3 A9 (MEDIUM/LOW, one 24-fps camera source) on the phone, and the
  static circle pattern without photos looks like C3's
- H4 [accept] the local calendar's name "Tessera" and colour #0063B1 — J6's constants (`feeds/LocalCalendar.kt:21,52`),
  which the app reuses (was "Calendar" and the accent, SUPERSEDED 2026-09-23 by T16-2) (approximation)
- H5 [accept] the Birthdays calendar's look in the views and on the tile (approximation)
- H6 [accept] "(No title)", the first-day-of-week default and setting wording (approximations) — [fidelity] where R11
  captured the settings page
- H7 [accept] the event reminder notification's look (approximation) — [fidelity] if R11 captured W10M's reminder toast
- H8 [accept] a contact with no name listed under "#" as its number or e-mail (approximation)
- H9 [accept] the Android profile ("Me") left out (approximation; offline preferred, A11 as amended 2026-09-23 — Jeremy can
  ask)
- H10 [accept] P4 design: work-profile contacts found by search only, with the briefcase glyph (phase 01 Q5's design extended)
- H11 [accept] link / unlink, SIM import and filter pages (approximations) — [fidelity] where R11 captured them
- H12 [accept] the chooser seam on the phone the first time another app opens a contact or event (P3), and that the shell's
  People and Calendar are what "Always" should point at
- H13 [accept] any approximation not covered by H4–H12 or H14
- H14 [accept] the Sync design (T16-1, T16-3): the Sync picker lists only calendars enabled once in Calendar settings ("Can
  sync to"), so the work calendar can never be chosen by a mis-tap; the first Sync opening "Can sync to" directly; the
  "synced to <calendar>" marker and its warning glyph (P4 design); the delete choice for a synced event

## Edge cases
- Calendar with no calendar and the provider package disabled (`pm disable-user com.android.providers.calendar`, restore
  `enable`): the app says the calendar provider is off, creates nothing, and the tile's face is the day only (phase 01's
  existing behaviour); `[calendar] calendars: none` logged with the reason; Tess's "add" answers "I don't have a calendar to
  add that to." (`cortana/action/ActionLayer.kt:487`) and writes nowhere else (T16-2 line 3)
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
- Sync (T16-1): the allow-list cleared after a sync (the marker stays; the next Sync opens "Can sync to"); a Sync tapped twice
  quickly (one copy, the second is `updated` or a no-op by the hash); a local event edited while its Sync runs (the next Sync
  pushes the edit); `calendar_sync.json` lost (`run-as … rm`): markers vanish and the next Sync makes a new copy — the old one
  stays on the other side (recorded, RECORDED); a synced local event whose copy's calendar became read-only (the Sync fails
  with the provider's error, `failed <err>`)
- The Birthdays calendar deleted by another app: recreated under `Tessera Birthdays` at the next start or contacts change,
  and `Tessera` is untouched
- Sync in progress on the phone (rows changing under the observer): the views refresh without a crash; a `content insert`
  loop of 50 events over 10 s on the AVD stands in for it
- An event whose `dtend` < `dtstart` is refused by the editor; a 10-year daily series (`FREQ=DAILY` with no COUNT) inserted by
  the driver: the month view still renders within 3 s (windowed instances); an RRULE with BYDAY=2TU (second Tuesday) shows on
  the right days; an exception that moves an occurrence to another day shows on the new day only
- A reminder on an event already past (never notifies; `calendar_alerts` has no scheduled row); a reminder 0 minutes before;
  two reminders on one event (two notifications); the phone off across a reminder's time (after boot the provider delivers it
  late and the notification says the event's time, not "now")
- A calendar with a null colour (the accent is drawn); two calendars with one colour (both listed by name); a calendar whose
  display name is empty (its account name is shown)
- Time: `settings put system time_12_24 24` (restore) → every time reads "H:mm"; a locale change re-labels months and the
  first day of the week without a restart; the clock jumped backwards a year (the views follow; no event is lost)
- Opening from intents: the Calendar tile's tap lands on today; `am start -a android.intent.action.INSERT -t
  vnd.android.cursor.dir/event --es title Lunch --el beginTime <ms> --el endTime <ms>` opens the editor prefilled; a
  `content://com.android.calendar/events/<id>` VIEW opens that event; a malformed URI opens today with no crash
- People with 5,000 contacts (a driver loop; the list's dump of the top rows is available within 3 s, the jump grid still
  lands on its letter); a contact with 20 numbers (all listed on the card with their labels); a 20 MB contact photo (the
  provider's thumbnail is drawn in the list, the full photo on the card, decoded at bounds); a contact deleted while its card is
  open (the card closes with a notice); a contact linked across two accounts and then one account removed (the remaining raw
  contact stays a contact); editing a field on an aggregated contact writes to ITS raw contact and never splits the aggregate
- Non-Latin names and their buckets (张伟 under the provider's label, Zoë under Z, "Ærøskøbing" wherever `PHONEBOOK_LABEL`
  puts it); a name with only a family name; a company-only contact (organisation shown as the name); a number stored with
  spaces and dashes matches a search typed without them (the provider's phone lookup)
- `WRITE_CONTACTS` granted while READ is revoked (impossible in one group, but asserted: the app treats the group as one);
  READ revoked mid-edit (the save fails cleanly)
- SIM removed while importing (`adb emu` cannot eject; the phone row records it); a SIM contact with no number; a SIM
  contact that already exists (imported again as a second raw contact — the provider may aggregate it; either outcome is
  recorded, no crash)
- Share when no app receives `text/x-vcard` (the resolver's empty state, Android's); share of a contact with a photo (the
  vCard carries `PHOTO;ENCODING=b`)
- The People tile: a contact photo removed while its bubble is on screen (the next event uses another photo; the current
  bubble finishes); every photo removed mid-cycle (the static pattern after the current event); a photo that fails to decode
  (skipped, logged); the tile pinned small / medium / wide (the face scales per phase 01's tile rules; the bubble geometry is
  from r11/people.md if captured, else approximation under H3)
- Phase 06 not yet built: People's Text action goes to the SMS role holder (the Fossify fixture); Call goes through Telecom
  to whatever dialer holds the role; both re-run when 06 lands (its E5's form)
- Liveness (N-01): reboot, 24 h idle, Device care, force-stop (the reminder receiver is manifest-registered and needs no
  process; the People feed restarts with the shell)

## QA evidence
_None yet._
