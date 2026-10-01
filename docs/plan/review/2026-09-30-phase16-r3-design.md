# Phase 16 r3 — Reviewer 1 (opus, design / correctness), 2026-09-30

As returned (written here by the lead; subagents do not write report files). Read-only; checked against the code on
`phase-14` @ 4ead606a. API facts marked "(AOSP, from memory)" are not verified on a device — confirm at build start.

**D1 — BLOCKING — l.63-65, 105-106, 115-116, E9, E22.** Tess can delete from any calendar. `ActionLayer.deleteEvent`
matches a title over unfiltered Instances (`cortana/action/ActionLayer.kt:510-515`, `calendarEvents` `:698-704`) and
`removeEvent` deletes `Events/<id>` (`:529-531`); phrases at `match/CommandMatcher.kt:175-176`. That is a direct delete on
an account calendar, so rule 1 and "by any path (app, Tess, Sync)" fail. J6 covers insert only, and Scope says "this phase
adds nothing to it".
Fix: task 3 gains "Tess's delete goes through the write guard: the match is limited to `calendar_id = LocalCalendar.id`; a
title found only elsewhere answers 'That event isn't in your Tessera calendar.' (approximation, H13)". Strike "adds
nothing". E22 rule 1 adds "`delete the event Offsite` → no Delete card, Work still 1". T16-11's JVM test gains "Tess delete
→ account calendar". Change Log (phase 03).

**D2 — BLOCKING — l.105, 109-111, 203-204, 313-314, E24.** A synced event becomes two events everywhere. The app shows
every calendar, the tile, pod and Tess read unfiltered Instances (`feeds/CalendarFeed.kt:88-95,122-130`;
`ActionLayer.kt:698-704`), and Sync copies reminders while the shell notifies for every calendar. The source and its copy
therefore both show in the views, tile, Agenda pod and "what's on my calendar", and the shell posts two reminders. No line
says otherwise; E24 cannot see it.
Fix: Decision "a mapped copy is not shown twice: the views, `CalendarFeed` (tile and `agenda`),
`ActionLayer.calendarEvents` and the reminder receiver skip an event id `calendar_sync.json` maps as a copy while its
source exists" (ADDs to 01 / 03 / 14). E24 adds: one `cal_event_title:` "Standup", one pod row, one shell notification.

**D3 — SHOULD-FIX — l.159-163 (E4b re-cut), task 1.** The slot picker is package-keyed. `SlotResolver.candidates` keeps
every catalog entry whose package handles the category (`tiles/SlotResolver.kt:44-45`), and `resolve` ends in
`firstForPackage` (`:39`). Once the shell declares APP_CALENDAR / APP_CONTACTS, both pickers list all eight shell apps
under one tag `slot_candidate:app.tileshell` (`start/SlotPicker.kt:55`). MUSIC's picker already does this.
Fix: task 1 gains "`candidates` and the category branch of `resolve` match the handler's COMPONENT; tag
`slot_candidate:<flattened component>`". E1 asserts exactly two candidates per picker.

**D4 — SHOULD-FIX — l.42-43, 371-373.** Where ≡ show/hide is stored is unstated. Writing `Calendars.VISIBLE` on an account
calendar is a write rule 1 and T16-11 forbid. It would also leave the tile, pod and Tess still showing the calendar (they
pass no `visible` selection), and the provider would stop its alerts (AOSP, from memory).
Fix: "show/hide lives in the shell's own store keyed like `allowed`, never `Calendars.VISIBLE`; it filters the Calendar
app's views only". E22 adds: hide Work → its `visible` column unchanged.

**D5 — SHOULD-FIX — l.315-316, 923.** "Can sync to" lists every non-LOCAL calendar, read-only ones included. The provider
does not gate an app insert on `calendar_access_level` (AOSP, from memory), so the l.923 edge ("fails with the provider's
error") will not happen: the copy sits dirty and never uploads. The list and marker also show display names only, and
Exchange's is "Calendar".
Fix: list only `calendar_access_level ≥ 500`; the guard re-reads it at push (`failed calendar read-only`); rows on "Can
sync to", the Sync picker and the marker sit under `cal_account:<name>`. Re-cut l.923.

**D6 — SHOULD-FIX — l.195-205, 349-358, Scope l.33-34.** The reminder receiver is under-specified in four ways.
- **Data filter.** The broadcast carries a data URI — `content://com.android.calendar/<alarmTime>` plus an `alarmTime`
  extra, not `/time/<ms>` (AOSP, from memory). A filter with no `<data>` never matches an intent that has data.
- **Exported.** It must be `exported="true"`, so any app can send it; it is in neither the allow-list ADDs nor any trust list.
- **Race.** Other calendar apps flip the same `calendar_alerts` rows SCHEDULED→FIRED, so a `state=SCHEDULED` query can
  find nothing.
- **Guard.** Marking alerts fired / dismissed on account-calendar events contradicts "exactly three cases".

Fix: "filter `<data android:scheme="content" android:host="com.android.calendar"/>`; the broadcast is a poke only — the
receiver re-reads `CalendarAlerts` for `alarmTime ≤ now`, state SCHEDULED or FIRED, de-duplicated by its own notified set;
the guard gains case (4): a `CalendarAlerts.STATE` update, a local table"; add it to the exported allow-list.

**D7 — SHOULD-FIX — l.74-75, 293-298, E18.** The Calendar app's write grant has no home. Setup's `calendar` row asks READ
only (`onboarding/Checklist.kt:122`); WRITE_CALENDAR is on Tess's row (`CortanaChecklist.kt:50`). With READ held and WRITE
not, the app cannot create Tessera, no diagnostics form fits, and the editor shows Tess's notice with no grant offer
(People gets one, E13).
Separately, `LocalCalendar.id` is `find() ?: create()` and `find` returns null on a failed query too
(`feeds/LocalCalendar.kt:24-34`). With READ revoked and WRITE held, every call inserts another Tessera, which breaks
T16-2's "never a second".
Fix: the editor and Sync offer WRITE in place; line `calendars: n (local missing: WRITE_CALENDAR)`; `find` creates only
after a successful empty query (ADD to phase 03); the app skips `LocalCalendar.id` when READ is denied; E18 gains a
WRITE-only-revoked clause.

**D8 — SHOULD-FIX — whole doc; depends-on l.5.** The pod bay is never mentioned. The Agenda pod launches
`SlotApp(Slot.CALENDAR)` (`start/podbay/PodBayPage.kt:303`, `StartActivity.kt:483-492`), and phase 14 E3's launch clause
requires exactly one APP_CALENDAR handler (`phase-14-pod-bay.md` l.415-420). That precondition fails on every re-run once
this phase ships.
Fix: Decision "the Agenda pod opens the seeded CALENDAR slot app; its empty / denied lines are unchanged; Birthdays appear
in it; phase 14 E3's precondition becomes 'baseline `slots.CALENDAR` = the shell's Calendar' (re-cut, Change Log)"; a
clause in E1 (`[podbay] launch agenda -> app.tileshell/…`); depends-on gains 14.

**D9 — SHOULD-FIX (question for Jeremy) — l.17-18, 37-38, 135-141.** The guard cannot tell a forced pick from a
preference. With two calendar apps the slot reads "Tap to choose" (`SlotResolver.kt:63-70`), and that pick is
`explicitSlots`. On such a phone the guard keeps it, so the shell's Calendar never takes the tile or the pod, and no phone
row notices. Music took its slot with no guard.
Fix: phone row "P0: after the update record what the CALENDAR and PEOPLE tiles open; where the guard kept an earlier pick,
re-point in Settings > Tile apps". Ask Jeremy: keep his earlier pick (guard as written), or let the seed take a slot he
was forced to pick.

**D10 — SHOULD-FIX — no trust Decision.** `build-prompt.md:21` names only the write guard. Add a T15-33-style list for the
adversarial review:
- `WRITE_CONTACTS`.
- The exported VIEW / EDIT / INSERT / PICK handlers: an INSERT prefill never saves without a tap and ignores a
  `calendar_id` extra; EDIT on a non-Tessera event opens read-only; PICK grants one contact URI.
- D6's receiver.
- The Sync path and `calendar_sync.json`.
- The vCard stream: use `Contacts.CONTENT_VCARD_URI`, not a new provider.
- The reminder notification on the lock screen (`VISIBILITY_PRIVATE`).

**D11 — NOTE — task 2; brief's L14-2.** The activity shape is unstated. Only catalog classes count for Back on Start
(`start/BackHistory.kt:44-45,69-70,78-80`).
Fix: "one activity per app (`.calendar.CalendarActivity`, `.people.PeopleActivity`; `singleTask`, `onNewIntent`, phase
15's form) hosts every page; any helper activity is non-launcher and does not count".

**D12 — NOTE — l.155-158, 241-246.** `LiveTileEngine` has no PEOPLE key, and `StartPage.kt:160-164,242-246` need
`Slot.PEOPLE`. An AppTile of the shell's Calendar or People reads `cmp:` content (`tiles/engine/TileRouting.kt:61-62`),
which neither feed publishes, so a pinned app tile is faceless. Say whether that is intended, or have the feeds also
publish under `componentKey`.

**D13 — NOTE — l.227-228.** "The account the filter default names" is undefined when the filter is all accounts; name the
rule. Possible question for Jeremy: does "never work" extend to editing or deleting work-account contacts?

**D14 — NOTE — l.215-221, E17.** Say that the Birthdays writer runs from `ShellApp.startFeeds` (so the tile and Tess see
birthdays without Calendar ever opened), and add the `--MM-DD` (no year) and 29 February forms.

**D15 — NOTE — stale cites and wording.**
- `ShellApp.kt:122,185` → `:64,151`.
- `ActionLayer.kt:485-487` → `:490-492`.
- `CortanaChecklist.kt:43` → `:48`.
- `Checklist.kt:89-118` → `:104…`.
- `SettingsActivity.kt:117` → `:118`.
- `lib.sh:47-56` → `:49`; `lib.sh:141-145` → `:155`.
- `provision.sh:66` → `:89`; `provision.sh:50-54` → `:73-74`.
- l.333-334 says depends-on `[01, 02, 03, 15]` against the header.
- l.400 cites an INDEX row "R3 C4 re-check" that is not in INDEX.
- l.481-482's "or": phase 06 builds after 16, so only "a targeted intent contract recorded now" stands — write it.

**Checked and correctly applied:** T16-2 (constants match `LocalCalendar.kt:21,43-52`; but D7), T16-3, T16-4, T16-5 / C-1 /
C-2 (TileRouting as built; guard cite `LayoutStore.kt:127-139` correct), T16-7, T16-12, T16-13 (R11 values spot-checked),
T16-14, T16-15, T16-16, T16-17, T16-19, C-3, C-8, C-19, C-20, C-23 (header), C-26. T16-11 is applied as written but has
holes (D1, D4, D6).

BLOCKING: 2 · SHOULD-FIX: 8 · NOTE: 5
