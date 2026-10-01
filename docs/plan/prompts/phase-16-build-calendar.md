# Phase 16 build brief — the Calendar app (build tasks 3 and 4, Calendar's half of 9)

Read `docs/plan/prompts/phase-16-build-common.md` first: its rules bind you.

- Worktree: `/home/jeremyking/projects/metro-launcher-p16/.claude/worktrees/cal`, branch `phase-16-cal`, cut from
  `phase-16` at 9f137626 (build tasks 1 and 2 and the build-start checks are in).
- Scratch files (anything not for the repo): prefix them `cal-` in the scratch directory you are given.
- The other builder (People, tasks 5–7) works in `.claude/worktrees/people`. You do not touch: the `people/` package,
  `feeds/PeopleFeed*`, `tiles/engine/LiveTileEngine.kt`, `tiles/engine/TileRouting.kt`, `start/StartPage.kt`,
  `res/values/people.xml`, `res/xml/shortcuts_people.xml`, People's manifest element. Files you both add a line to
  (`ShellApp.startFeeds`, `AndroidManifest.xml`, `qa/phase-03/exported-allowlist.txt`): keep your edit to your own
  lines so the lead's merge is clean.

## Read (fully, before code)
1. `docs/plan/phase-16-inbox-calendar-people.md` — all of it (≈1,700 lines). Your clauses: Scope's Calendar bullets,
   the Decisions lines Q-16-2, Q2 (rules 1–4), Q3, "Calendar data", "Event reminders", "Recurrence and time zones",
   "Birthdays", "Fidelity", "Bars", "Harness contracts", T16-2, T16-1, T16-3, T16-11, T16-13 (Calendar half), C-17,
   r3 D1 / D6, the reminder receiver (r3 D6), r3 D7, r3 D8, r3 D4, r3 D5, r3 D11, "Trust", "Harness, round 3",
   "Verify at build start"; Build tasks 3, 4 and 9; the Acceptance preamble; rows E3–E9, E17, E18, E19, E21, EDGE,
   E22–E25; H1, H4–H7, H14, H15, H17, H18, H20, H22; the Calendar bullets of Edge cases.
2. `docs/plan/r11/calendar.md` — every measured value (K1–K6) and every UNMEASURED item's proposal (U1–U10, §8).
   `docs/plan/r11/people.md` P4.3–P4.7 for the editor form U3 points at.
3. `docs/plan/qa/phase-16/BUILDSTART/README.md`.
4. Code you extend or reuse: `feeds/LocalCalendar.kt`, `feeds/CalendarFeed.kt`, `cortana/action/ActionLayer.kt`
   (`insertEvent` :490, `deleteEvent` :510, `removeEvent` :529, `calendarEvents` :698 — line numbers at 9f137626),
   `cortana/match/CommandMatcher.kt:101,175-176`, `calendar/` (task 2's activity, nav, routes),
   `start/podbay/PodBayPage.kt` (the Agenda pod reads `CalendarFeed.agenda`; you change nothing there),
   `clock/ClockWidgets.kt`, `clock/ClockTabs.kt`, `calculator/DatePage.kt`, `cortana/ui/ReminderDetailPage.kt`,
   `ui/components/OutlinedField.kt`, `ui/components/ModalOverlay.kt`, `ui/MotionClock.kt`, `settings/SettingsWidgets.kt`,
   `cortana/reminders/` (how the shell posts a notification and declares a channel), `clock/ClockNotifications.kt`.

## Build (each clause as its Decisions line specifies it; commit after each lettered part is verified)

### A. Calendar data layer (build task 3)
1. **The shell's own calendar store** — `calendar_sync.json` in `filesDir` (temp-file-and-rename): the `allowed` list
   (`_ID` + account name + account type), the mapping local event id → {target calendar `_ID`, account name, account
   type, copy event id} (no hash, r3 V13), the ≡ pane's `hidden` list (same key form; never `Calendars.VISIBLE`, r3 D4),
   the first-day-of-week setting, and the receiver's notified alert ids. A removed-and-re-added account (new ids) starts
   NOT allowed; a mapping whose local event is gone is dropped at the next read.
2. **`LocalCalendar` (an ADD to phase 03's part, r3 D7):** `find` tells a failed query from an empty one; `id` creates
   only after a successful, empty query; a failed lookup returns null and logs `[calendar] local calendar lookup failed:
   <err>`; the app does not call `LocalCalendar.id` while READ_CALENDAR is denied. Never a second Tessera calendar.
3. **The one write guard and the one write layer (T16-11 with r3 D1 / D6; the Trust line (a)).** A pure rule (no Android
   types) with exactly the four allowed cases — (1) Tessera: every op; (2) Tessera Birthdays: the Birthdays writer's
   path only; (3) an allowed Sync target: only a push, update or delete of a copy `calendar_sync.json` maps, only while
   the copy's re-read `calendar_id` equals the mapping's target, the target is still allowed and its re-read access
   level is ≥ 500; (4) the receiver's `CalendarAlerts.STATE` update and no other column or table — and a refusal of
   every other combination. EVERY `CalendarContract` insert, update and delete the shell makes goes through the one
   write layer that asks this rule first: the editor, Sync, the Birthdays writer, the receiver, and Tess's insert and
   delete (move `ActionLayer.insertEvent` / `removeEvent`'s provider writes behind it). Lines: `[calendar] write <op>
   event=<id>: ok | failed <err> | failed refused (not allowed)`. **JVM test** of the rule covering each allowed case
   and each refusal the doc lists (T16-11, r3 D1 / D6, E22's list): the editor → Birthdays, the Birthdays writer →
   Tessera, Sync → an id not allowed, Sync → an allowed id but an unmapped event, any op → an account calendar, Tess
   delete → an account calendar, Tess delete → Tessera (allowed), the alert-state update (allowed), any other write by
   the receiver, Sync → a target whose access level fell below 500, T16-12's stale mapping.
4. **Reads:** the calendar list with colours and account names (every calendar but Tessera read-only in the app);
   windowed `Instances` queries per view; the `[calendar] calendars: n (local created id=<id> | local present | none |
   local missing: WRITE_CALENDAR)` and `[calendar] calendars: denied (READ_CALENDAR)` lines; `[calendar] view <name>
   <from>..<to>: n instances in <ms> ms` (ms = the query plus the first composed frame, from `withFrameNanos`; n is the
   count AFTER synced copies are dropped); `[calendar] counts: <account name>/<displayName>=<n>, …` each time the pane
   opens; a `ContentObserver` so the views follow the provider with no restart (E4's 2000 ms).
5. **One reader of synced-copy ids (Q-16-2; ADDs to phases 01, 03 and 14):** the set of copy event ids whose local
   original still exists, a recurring copy's exception events counted with their master. Used by the app's views, by
   `CalendarFeed`'s tile query and `queryAgenda` (they gain `Instances.EVENT_ID` in their projections), by
   `ActionLayer.calendarEvents`, and by the receiver.
6. **Event writes on Tessera only:** create, edit, delete; repeat with `RRULE` + `DURATION` (no `DTEND`); edit this
   occurrence (an exception event), this and following (`UNTIL` on the master, a new master), all; all-day events
   date-anchored in UTC; `EVENT_TIMEZONE` on timed events; reminder minutes as `Reminders` rows with `METHOD_ALERT`.
7. **Sync (T16-1, T16-3, T16-12, r3 D5, r3 V13):** "Can sync to" lists the non-LOCAL calendars with access level ≥ 500,
   never Tessera or Birthdays; the copy is a normal insert (no `caller_is_syncadapter`) into the allowed target; a
   re-Sync re-reads the copy and compares its copied fields, exceptions and reminders with the local event — writes
   when they differ (`updated`), nothing when equal (`ok`), recreates a copy that is gone (`recreated`); recurring
   events copy RRULE / DURATION / EXDATE, their exceptions (`ORIGINAL_ID` re-pointed at the copy's master) and their
   reminders; the delete choice ("here" default / "both", "both" never offered for a target no longer allowed); the
   removed-calendar refusal (`failed calendar gone`), `failed calendar read-only`, `failed mapping stale`, `refused (not
   allowed)`, and `[calendar] sync event=<id>: no calendar allowed -> can sync to`. Every `[calendar] sync …` line of
   "Harness contracts".
8. **`.calendar.CalendarReminderReceiver` exactly as r3 D6 specifies:** manifest-registered for
   `android.intent.action.EVENT_REMINDER` with `<data android:scheme="content" android:host="com.android.calendar"/>`,
   `exported="true"`; the broadcast is a POKE — read nothing from the intent; re-read `CalendarAlerts` for rows with
   `alarmTime` ≤ now in state SCHEDULED or FIRED, less the notified set, less a synced copy's alerts (`[calendar]
   reminder event=<copy id> minutes=<n>: skipped (synced copy)`); one notification per remaining alert on a calendar
   channel, `VISIBILITY_PRIVATE` (the public version says a calendar reminder fired, not the title), showing the
   event's time; record the id, mark the row FIRED; dismissing the notification marks it DISMISSED (`… : notified |
   dismissed`). No alarm of the shell's own (E6 asserts the shell's pending-alarm count is unchanged). Add its line to
   `docs/plan/qa/phase-03/exported-allowlist.txt` (format there; guard: none — the provider's process sends it; say what
   it may do) — E2 compares that file with the APK exactly (`python3 docs/plan/qa/phase-03/scripts/exported.py <apk>
   docs/plan/qa/phase-03/exported-allowlist.txt` must exit 0).
9. **Tess (ADDs to phase 03's actions, r3 D1):** `deleteEvent` matches only `calendar_id = LocalCalendar.id`; a title
   found only on another calendar answers "That event isn't in your Tessera calendar." with no Delete card; a title
   found nowhere keeps "I don't see <title> on your calendar."; `removeEvent` deletes through the write layer; a synced
   local event is deleted "here" only (the copy kept, its mapping dropped). Her reads skip a synced copy. When
   `LocalCalendar.id` is null she answers "I don't have a calendar to add that to." as today.
10. **Birthdays (Q3, T16-2 line 2, r3 D14, with the 2026-10-01 re-cut):** a read-only (access 200) LOCAL calendar
    "Birthdays" under its OWN account name `Tessera Birthdays`, written through the sync-adapter URI by one writer,
    started with its Contacts `ContentObserver` from `ShellApp.startFeeds` beside `CalendarFeed`; created when the
    first birthday exists, kept (empty) when the last goes; `yyyy-MM-dd` → a yearly all-day event "<Name>'s birthday"
    (`FREQ=YEARLY`) from that date; `--MM-dd` → the same from this year's date; 29 February (either form) →
    `FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1` (a no-year 29 February in a year without one starts on that year's 28
    February); a value in neither form is skipped and not counted; no `Reminders` rows; `[calendar] birthdays: n synced`
    and `[calendar] birthdays calendar could not be created: <err>`. The date-form rule is a pure function with a JVM test.
11. **`CalendarFeed` also publishes its face under the Calendar activity's component key** (r3 D12:
    `TileRouting.componentKey` of `.calendar.CalendarActivity`), so a pinned Calendar app tile is live like the slot
    tile (E4). One publisher, two keys. You call the existing key function; you do not edit `TileRouting.kt`.

### B. Calendar app (build task 4) — W10M's views, every value from r11/calendar.md with its tolerance
The header (≡, the month and year in caps with ⌄ / ⌃) opening the month drop-down; Agenda with the 15063 two-row week
strip over day groups and the measured event rows; the Week view; the Day view (U1's proposal) with its all-day band;
the ≡ calendar pane with account headers and colour-filled checkboxes (the shell's own `hidden` state); the app bar
Today · New · View · … with the View list Agenda / Day / Week; day paging, the date picker, Today; the event page
(`cal_event_page:<id>`, `cal_event_time:<id>`, `cal_event_action:<edit|delete|sync>` on local events only,
`cal_synced_marker:<id>` with `cal_account:<name>`); the editor (U3: `OutlinedField`, accent type-labels, group rules;
title, location, start / end, all day, repeat, reminder, notes; its calendar field reads Tessera and offers no other);
the occurrence prompt (this occurrence / this and following / all); the Sync picker and "Can sync to" grouped under
`cal_account:<name>`; the first Sync routed straight to "Can sync to" when nothing is allowed, returning to the picker;
the settings page (first day of the week, "Can sync to"); `cal_notice` (a node that stays until the page changes, never
a toast) for every notice — cannot read (grant offered in place), cannot save (WRITE_CALENDAR offered in place, phase
10 E18's form), "That calendar is no longer on this phone", "That calendar is read-only now", the `recreated` notice,
the provider-off notice, "Choose which calendars Sync may use"; "(No title)"; the motion lines `[motion]
cal_month_dropdown` (200 ms ease-out from its top edge) and `[motion] cal_day_page` (250 ms, X13) through
`MotionClock.animate`. Routes from task 2's `CalendarNav.route`: Open (today, or the shortcut page — `month` = Agenda
with the drop-down open, `new_event` = the editor), Time, Event, Edit (the editor only for a Tessera event; any other
event opens its page read-only), Insert (the editor prefilled, nothing saved until Save). Every tag in "Harness
contracts" and "Harness, round 3" that begins `cal_`.

### C. Calendar's App Shortcuts (build task 9, Calendar's half)
`res/xml/shortcuts_calendar.xml` — ids `agenda`, `day`, `month`, `new_event` in that rank order (labels Agenda, Day,
Month, New event), each targeting `.calendar.CalendarActivity` with the `page` extra — and its `<meta-data>` on the
activity, in the form of `res/xml/shortcuts_calculator.xml`. Strings in `res/values/calendar.xml`.

## Reuse
`OutlinedField` for the editor; `ClockWidgets` / `ClockTabs` for the app bar, flyout, checkbox and press feedback where
r11/calendar.md's values match them (where r11 measured something else — the 47-epx bar with the 68-epx CommandBar
pitch, the 48.1-epx pane rows — build to r11); the drawn date and time pickers that exist; `LocalCalendar` (never a
second creator); `MotionClock`; the notification-channel pattern of `clock/ClockNotifications.kt`.

## Development proof you owe (device sessions under `docs/plan/qa/phase-16/dev-cal/`)
At least: Tessera created at first open and reused (E3's core); an event made in the editor read back from the
provider, a driver insert appearing with no restart (E4's core); all-day / multi-day / weekly series and the three
occurrence edits (E5's core); a reminder notifying once with the AOSP Calendar disabled, FIRED then DISMISSED, and a
forged poke doing nothing (E6 a, c — every clock move FORWARDS with `jump_clock`, restored with `clock_restore`, both
in `docs/plan/qa/phase-15/scripts/p15.sh`); the views' dumps carrying their tags; Sync to an allowed calendar, the copy
hidden in the views, the delete choice (E23 / E24's core — create the `mkcal` calendars inside the session, after any
provider restart); Tess's delete refused on an account calendar and allowed on Tessera (E22 / E9's core); a birthday
appearing with the Calendar app never opened (E17's core); the two permission-revoked states (E18's core). Use local
or `mkcal` QA calendars and delete them, their events and the Birthdays calendar before the session ends.
