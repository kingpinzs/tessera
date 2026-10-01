# Phase 16 — the Calendar builder's report (2026-10-01, branch phase-16-cal at 19ea7813)

Saved verbatim by the lead from the builder's hand-back (model output, not the owner's words). The lead's triage of it is in INDEX's row 16 and Change Log.

CALENDAR BUILDER REPORT — phase 16, build tasks 3, 4 and Calendar's half of 9
Worktree /home/jeremyking/projects/metro-launcher-p16/.claude/worktrees/cal, branch phase-16-cal, clean (git status: 0 lines). Nothing pushed.

Tasks 3, 4 and Calendar's half of 9 are built and committed. The whole unit suite passes (1049, 0 failures) and every development session passes on its final run; the gate rows (E7's scale, E6 b/d, E8's DST night, the pinned tile and the others in section 4) are not proven.

## 1. Commits (oldest first, on top of c98533ec)
- 62817dd6 Phase 16 task 3: the Calendar data layer behind one write guard
- 4603c132 Phase 16 task 4: the Calendar app's views and pages
- 2ba03365 Phase 16 task 9: Calendar's App Shortcuts
- 653b5983 Phase 16 task 3: occurrence edits that keep a local series whole
- 75d7d9c7 Phase 16 task 3: Sync's allowed list follows the provider while the shell is alive
- b1fcf384 Phase 16 Calendar: development proof for Tess, birthdays and the permission states
- 112e2f65 Phase 16 task 4: tags report the box they are drawn in; a vanished row is a failure
- 305d7852 Phase 16 task 4: the event page follows a zone change; a vanished event's page closes
- 5e4d77ad Phase 16 Calendar: development proof that a synced event reminds once
- 78dbb34c Phase 16 task 3: the receiver remembers an alert by what it is, not by its row id
- 19ea7813 Phase 16 Calendar: the four earliest development sessions run again on the final build

Task 4 is one commit, not the five you asked for. The frame (`CalendarApp.kt`) references every page and `git add -p` is not available, so any split would have left commits that do not compile.

## 2. What is built, with its proof
All device proof is development proof under `docs/plan/qa/phase-16/dev-cal/` (scripts in `scripts/`). Earlier runs are kept beside each final run, the folder name saying what the run found.

**Which build each final run used.** The final APK (md5 1c0b1b18e29cac25, code of 78dbb34c) ran B_LOOK, E3_LOCAL, E4_EVENTS, E5_RECURRENCE and E6_REMINDER. The other sessions ran on earlier builds and were NOT re-run after the later fixes:

| Session | Build it ran on |
|---|---|
| E25_SHORTCUTS | 2ba03365 |
| E23_SYNC, E24_SYNC2, E22_TESS, E17_BIRTHDAY, E18_PERMS | 75d7d9c7 code |
| E19_GEOMETRY | 112e2f65 |
| EDGE_CAL, E24_REMINDER | 305d7852 |

### A. Data layer (task 3)

**A1. `calendar_sync.json`** (`CalendarSyncStore.kt`, rules in `CalendarSyncState.kt`; temp file and rename). Shape as written on the device:
`{"version":1,"allowed":[{"id":3,"accountName":"qa.personal@example.com","accountType":"com.google"}],"mappings":[{"id":…,"accountName":…,"accountType":…,"local":L,"copy":C}],"hidden":[…],"firstDayOfWeek":n,"notifiedAlerts":["<event>:<begin>:<alarmTime>"]}`
- The store re-reads the file when its stamp changes, so `run-as … rm` takes effect without a restart.
- Proof: CalendarRulesTest (41 tests, 0 failures) for the rules; E23_SYNC asserts the `allowed` entry and the mapping text.
- A mapping whose local event is gone is dropped at the read: `[calendar] sync mappings dropped (the local event is gone): [213]`.

**A2. `LocalCalendar`.** `find` returns Found / Absent / Failed; `id` creates only after Absent; `existingId` never creates; the create goes through the write layer.
- E3_LOCAL 20 passed 0 failed: `[calendar] local calendar created: content://com.android.calendar/calendars/1`, `[calendar] calendars: 1 (local created id=1)`, then `(local present)`; Tessera is still created beside another LOCAL calendar.
- E18_PERMS (c): READ revoked with WRITE held, three opens and a Tess add: `[calendar] local calendar lookup failed:` present, `local calendar created` absent, exactly one Tessera row after the grant.

**A3. Write guard and the one write layer** (`CalendarWriteGuard.kt` pure, `CalendarWrites.kt`).
- Every `CalendarContract` insert, update and delete of the shell is in `CalendarWrites.kt`: the editor's, Sync's, the Birthdays writer's, the receiver's STATE update, LocalCalendar's create, and Tess's insert and delete.
- CalendarWriteGuardTest: 32 tests, 0 failures. It covers each of the four cases and every refusal the brief lists, plus a walk of the whole request space asserting nothing is allowed outside the four cases.
- Device lines: `[calendar] write insert event=178: ok`, `write delete event=179: ok`, `write update event=272: failed the event is gone`.
- `failed refused (not allowed)` was not produced on the device (unreachable from the UI by design).

**A4. Reads.** Lines seen on the device:
- `[calendar] calendars: 0 (local missing: WRITE_CALENDAR)`
- `[calendar] calendars: denied (READ_CALENDAR)`
- `[calendar] calendars: none (java.lang.IllegalStateException: the calendar provider gave no answer)`
- `[calendar] view agenda 2026-09-27..2026-11-21: 10 instances in 44 ms`
- `[calendar] counts: Tessera/Tessera=3`
- Observer: E4_EVENTS reads `332 ms after the MARK (n 0 -> 1, in 26 ms)`.

**A5. One reader of synced-copy ids** (`SyncedCopies.hiddenEventIds`), used by the views, both `CalendarFeed` queries, `ActionLayer.calendarEvents` and the receiver.
- E23_SYNC: Day, Agenda and Week show the local event and no `cal_event:<copy>`; the feed line reads `agenda=1`.

**A6. Event writes on Tessera.** E5_RECURRENCE 37 passed 0 failed, 1 recorded:
- Repeat weekly writes `rrule=FREQ=WEEKLY, duration=P3600S, dtend=NULL`.
- This occurrence: `original_id=291, originalInstanceTime=1792108800000`.
- This and following: master `rrule=FREQ=WEEKLY;UNTIL=20261029T235959Z`, new master `rrule=FREQ=WEEKLY;COUNT=6`.
- Delete this occurrence, deleting a changed occurrence, and delete all also pass.
- All-day UTC anchoring and the stored-end rule are JVM-tested.

**A7. Sync.**
- E23_SYNC 47 passed 0 failed, 1 recorded: first Sync routed to Can sync to, the picker, the copy in Personal only, `ok`, `updated`, the other side's edit overwritten, `recreated`, delete here.
- E24_SYNC2 34 passed 0 failed, 2 recorded: delete both; "here" only when the target is un-ticked; a series copied as master + exception (`original_id` = the copy's master) + reminder; `failed calendar read-only`; `failed mapping stale` (twice: Sync and delete-both); `failed calendar gone`.
- Line counts across the two sessions: ok 10, updated 2, recreated 1, read-only 1, stale 2, gone 1, `no calendar allowed -> can sync to` 6.
- Work held exactly Offsite at every check.
- `refused (not allowed)` and `failed <err>` were not produced on the device.

**A8. `CalendarReminderReceiver`.** Exported, scheme + host filter, reads nothing from the intent. `exported.py` exits 0 ("20 exported in app.tileshell, 20 on the allow-list").
- E6_REMINDER 25 passed 0 failed, 2 recorded: one notification `title=[E6 standup] text=[Thu 1 Oct, 3:44 PM – 4:44 PM] vis=PRIVATE`, state 1 then 2 after the swipe, `…: notified` once, `…: dismissed`. A second poke and a forged poke do nothing.
- E24_REMINDER 11 passed 0 failed, 1 recorded: `[calendar] reminder event=279 minutes=10: notified` and `[calendar] reminder event=280 minutes=10: skipped (synced copy)`.
- Weak clause: "the shell's pending alarms unchanged" read 0 before and after; the counter may never count anything on this image.

**A9. Tess.** E22_TESS 31 passed 0 failed:
- "That event isn't in your Tessera calendar." with no Delete card.
- "I don't see nonesuch on your calendar."
- "Deleted." with `write delete event=…: ok`.
- "Added to your calendar."; J6's `[cortana] calendar event inserted into the shell's local calendar (2)` line is kept.

**A10. Birthdays.** E17_BIRTHDAY 27 passed 0 failed, 3 recorded:
- Calendar row `account_name=Tessera Birthdays, account_type=LOCAL, calendar_displayName=Birthdays, calendar_access_level=200`.
- `rrule=FREQ=YEARLY` for a full date and for a no-year date (from this year).
- 29 February: `FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1`, instances on 2027-02-28 and 2028-02-29.
- "Oct 1" is not counted; no reminder rows; `[calendar] birthdays: 1 synced`.
- The calendar is kept, empty, when the last birthday goes.
- The date-form rule is JVM-tested. `birthdays calendar could not be created` was not produced on the device.

**A11. Second publish key.** A_SMOKE: `[engine] publish cmp:app.tileshell/app.tileshell.calendar.CalendarActivity`.

### B. Calendar app (task 4)
- B_LOOK 51 passed 0 failed: every page's tags.
- E19_GEOMETRY 57 passed 0 failed, 4 recorded: header, strip columns and the 32-epx square, agenda rows, app bar, month panel, pane, Week view and editor fields against r11/calendar.md.
  - `[motion] cal_month_dropdown … settle=184 … maxGapMs=17`
  - `[motion] cal_day_page … settle=263 … maxGapMs=17`
- E4_EVENTS 22 passed 0 failed: the editor's event read back as `title=Standup, dtstart=1790953200000, dtend=1790956800000, calendar_id=2, eventTimezone=America/Boise, eventLocation=Room 2`.
- E18_PERMS 30 passed 0 failed: cannot-read and cannot-save notices with `cal_grant`; `pm grant` loads the views with the same pid.
- EDGE_CAL 28 passed 0 failed:
  - provider off;
  - an event deleted under its open editor;
  - zone change: `[Thu 1 Oct 2026, 1:00 PM – 2:00 PM] in America/Boise, [Fri 2 Oct 2026, 4:00 AM – 5:00 AM] in Asia/Tokyo`;
  - 24-hour time `13:00 – 14:00`;
  - Tessera deleted while the app is open.
- E22_TESS: the exported EDIT on an account event opens its page read-only; the exported INSERT naming Work prefills, saves nothing before Save, then saves into Tessera; un-ticking Work in the pane leaves `visible=1`.

### C. Shortcuts (task 9)
E25_SHORTCUTS 9 passed 0 failed: `0 agenda|1 day|2 month|3 new_event`, and each `page` extra opens its page.

## 3. Whole unit suite
1049 tests, 0 failures, 0 errors, rc 0: `docs/plan/qa/phase-16/dev-cal/unit-G.out` and `unit-G.rc`. Baseline was 976. Added: CalendarWriteGuardTest 32, CalendarRulesTest 41. The suite was run before every commit that touched app code (`unit-A` … `unit-G`).

## 4. NOT built, or built and not proven

**Not driven on a device (gate rows):**
- E7 (5,000 events, 3000 ms bounds, gfxinfo).
- E6 (b) the race with the AOSP Calendar enabled, and (d) reboot.
- E8's DST-night span. The Day grid has one row per hour of the day; only the stored-end rule is JVM-tested.
- E4's pinned app tile: only the publish line is proven.
- E3's "Tess first" leg and the access-200 calendar leg.
- E9's "what is on my calendar"; E24's Tess and pod clauses.
- E1, E2 and the E25 burst.

**Built, not proven:**
- Tapping `cal_grant` (the system permission dialog); every grant was `pm grant`.
- An exception later than a "this and following" split being moved to the new series.
- Sync tapped twice (serialised by a lock).
- `calendar_sync.json` lost.
- Two reminders on one event, a 0-minute reminder, a past event's reminder.
- The 50-insert burst, the 10-year daily series, BYDAY=2TU.
- A null calendar colour and an empty display name.
- A locale change; the clock jumped backwards.
- The Birthdays calendar deleted by another app; `birthdays calendar could not be created`.

**Not done:** the adversarial trust review (yours); every H row.

## 5. ADDs to other phases' parts (for the Change Log)

**Phase 03:**
- `feeds/LocalCalendar.kt`: `Lookup`, `find`, `existingId`; create only after an empty query; create through the write layer.
- `cortana/action/ActionLayer.kt`: `insertEvent` and `removeEvent` through the write layer; `deleteEvent` matches Tessera only with the new reply; `calendarEvents` drops synced copies and carries the calendar id.
- `qa/phase-03/exported-allowlist.txt`: one line for `CalendarReminderReceiver`.

**Phases 01 and 14, `feeds/CalendarFeed.kt`:**
- `EVENT_ID` in both projections, copies dropped.
- Second publish under the Calendar activity's component key.
- A call to `SyncAllowList.followProvider` on every refresh.

**Phase 01, `ShellApp.startFeeds`:** one line, `BirthdaysWriter.start`.

**`AndroidManifest.xml`:** the exported receiver, a non-exported `CalendarReminderDismissReceiver`, and `windowSoftInputMode="adjustResize"` plus the shortcuts meta-data on my activity.

**Not touched:** `Glyph.kt` (my five code points are `CalGlyph` in `CalendarWidgets.kt`), `TileRouting.kt`, `LiveTileEngine.kt`, `StartPage.kt`, anything of People.

## 6. Deviations, doc clauses that needed more, defects outside my scope

**Provider facts found (probes P_EXCEPTION, P_EXCEPTION2, P_EXCEPTION3, records only):**
1. An exception inserted for a LOCAL series whose master has no `_sync_id` loses the series' other occurrences from Instances.
2. With a `_sync_id` it expands correctly.
3. A normal delete of a row with a `_sync_id` only marks it deleted.
4. A normal delete of an exception row removes it and the original occurrence comes back.
5. Alert row ids are reused after a delete (E6 records `1 / 1`).

**What I did about them:**
- The write layer gives a Tessera series `_sync_id=tessera-<id>` before its first exception, through the sync-adapter URI.
- Deleting an exception row marks it cancelled; any other Tessera row is hard-deleted as the calendar's own sync adapter.
- Guard case 1 therefore allows the sync-adapter URI on Tessera's EVENT rows for the editor and Tess (tested). T16-1's "`_SYNC_ID` not used" was about Sync copies; say if you read it wider.
- A Sync copy cannot be given a `_sync_id` by the shell. Per fact 1, a recurring copy with an exception will expand wrongly for other apps until the account's adapter assigns ids. In the shell it is hidden.

**Deviations from the doc's wording:**
- The receiver's notified set holds `<event>:<begin>:<alarmTime>` keys, not row ids, because of fact 5.
- The allowed list is pruned from `CalendarFeed`'s observer as well as in the app. Without it a calendar deleted and re-created under the same id while the app was closed came back ticked (E24_SYNC2 run 2).
- `calendars: none (<reason>)` is the form I wrote. There is one alternative the doc does not name: `calendars: n (local missing: it could not be created)`, when the create fails with WRITE held.
- The `view` line's n is the provider's rows for [from 00:00, to 00:00 − 1 ms] less synced copies. Calendars hidden in the pane are not subtracted. Dates print inclusive.
- Agenda loads 8 weeks and doubles to 56 when its end is visible, one `view agenda` line per load.

**Behaviour the doc leaves open (my choices):**
- Sync on an already-synced event pushes straight to its mapped calendar; the picker shows only for a first Sync.
- When the target calendar is gone, the marker shows the account name (no display name is stored).
- EDIT on a repeating Tessera event opens the editor in scope "all".
- Tapping the notification opens the event and leaves the alert FIRED; only the swipe marks it DISMISSED.
- An extra line, `reminder …: failed notifications are off`.

**Motion numbers against E19's tolerance:**
- `cal_month_dropdown` settle read 184–196 over five runs, against 200 ± 17 (low side of the band; it has not read below 184).
- `cal_day_page` read 263; phase 15's `clock_swipe` reads 252–266 with the same clock.

**A risk in a FINAL clause, built as written:** r3 D6 (c) notifies every due SCHEDULED or FIRED row not yet handled. On the phone, the first poke after install could notify for alerts Samsung Calendar fired and left un-dismissed in the provider's retention window.

**Outside my scope:**
- `OutlinedField`'s tag node reads 48 epx tall in a dump, not 32 (E19 records 47.99). E19 / E20's "fields 32 ± 1" must be read by pixel, or the tag moved to the outer box.
- The AVD's default input method is `com.android.inputmethod.latin/.LatinIME`, not the shell's keyboard as the common file says.
- `ClockWidgets.PressBox` keys its gesture on the callback, so a recomposition under a finger restarts it. I have no repro; my app uses its own `CalPress`.
- A hardware Enter after `clearFocus()` moves focus into the first text field (seen in E4 run 1).
- Raw contact `_id=2`, with no name and no account, was on the device before my sessions.

## 7. Device state left
Read under the lock after E6_REMINDER's final run. The four reruns that followed each passed their own restore assertions; only installed-APK, calendars, events and alerts were re-read after them.
- Installed APK is this worktree's (md5 1c0b1b18e29cac25).
- One calendar (Tessera), no events, no alert rows.
- `calendar_sync.json`: `{"version":1,"allowed":[],"mappings":[],"hidden":[],"notifiedAlerts":[]}`.
- READ/WRITE_CALENDAR and READ/WRITE_CONTACTS granted.
- Zone America/Boise, `time_12_24` null, `auto_time` 1, clock equal to the host.
- `com.android.calendar` and `com.android.providers.calendar` enabled.
- Contacts: only Mom and the pre-existing `_id=2`.

One thing my sessions changed: Tessera has been deleted and re-created several times, so its `_id` varies (2 at the last read).

## 8. Driver map

### (a) Tags beyond the spec's list

**Views**
- `cal_header_band`, `cal_menu_glyph`, `cal_month_title`, `cal_header_chevron`.
- `cal_menu` (tap: the ≡ pane); `cal_header` (tap: the month panel).
- `cal_strip`, `cal_strip_selected`, `cal_agenda`, `cal_empty:<date>`.
- `cal_day_grid`, `cal_week`, `cal_week_more:<date>`, `cal_mini_month`.
- `cal_month_scrim`, `cal_pane_empty`.
- `selected="true"` is carried by `cal_view_mode:<mode>`, `cal_strip_day:<date>`, `cal_month_cell:<date>` and the open View row.
- The month panel changes month by a swipe on it; there is no button.

**App bar**
- `cal_app_bar`, `cal_bar:today`, `cal_bar:new`, `cal_bar:view`, `cal_bar:more`, `cal_bar_scrim`.
- `cal_view_menu` with `cal_view_pick:<agenda|day|week>`.
- `cal_more_menu` with `cal_more:settings`.

**Event page**
- `cal_event_location`, `cal_event_calendar`, `cal_event_repeat`, `cal_event_reminder`, `cal_event_notes`, `cal_synced_warning`.
- All-day events carry no `cal_event_time` in the views. On the event page they do ("Thu 1 Oct 2026, all day").

**Editor** (`cal_editor_title`, `cal_editor_save`, `cal_editor_cancel`; there is no Delete in the editor)

| Field | How its value is set |
|---|---|
| `cal_editor_field:title`, `:location`, `:notes` | typed: tap, `input text`, then ENTER hides the keyboard |
| `:all_day` | tap toggles; reads `checked` |
| `:start_date`, `:end_date` | tap opens the Calculator's date panel: `calc_date_pick:<month|day|year>:<value>`, `calc_date_pick_ok`, `calc_date_pick_cancel` |
| `:start_time`, `:end_time` | tap opens `cal_time_picker`: loop spinners `cal_time_spinner:<hour|minute|ampm>` read by content-desc, rolled by swipes (`spin_to` in `cal.sh`), then `cal_time_ok` or `cal_time_cancel` |
| `:repeat` | tap opens `cal_editor_options`: `cal_editor_option:<none|daily|weekly|monthly|yearly>` |
| `:reminder` | same list: `cal_editor_option:<none|0|5|10|15|30|60|720|1440|10080>` |
| `:calendar` | reads Tessera; a tap does nothing |

Picker fields carry their text on a child node (`field_text` in `cal.sh`).

**Prompts**
- `cal_occurrence` with `cal_occurrence:<this|following|all>` and `cal_occurrence_cancel`.
- `cal_delete` with the spec's `cal_delete_choice:<here|both>` and `cal_delete_cancel`.

**Sync and settings**
- `cal_can_sync` (the page), `cal_can_sync_empty`. Rows `cal_settings_can_sync:<id>` read `checked`.
- The Sync picker has no confirm: a tap on `cal_sync_target:<id>` is the Sync. `cal_sync_open_can_sync` shows when nothing is allowed.
- `cal_settings`, `cal_settings_first_day`, `cal_settings_options` with `cal_settings_option:<locale|1..7>`.
- `cal_settings_open_can_sync`, `cal_settings_can_sync_summary`.

**Notices and notification**
- `cal_grant` is the grant-in-place button under `cal_notice`.
- Notification channel id `calendar_reminders`, notification tag `calendar_reminder`.

### (b) Shortest tap paths from a cold launch
- Agenda: launch.
- Day or Week: `cal_bar:view` → `cal_view_pick:<mode>`. Day is also reached by a VIEW on `content://com.android.calendar/time/<ms>`.
- Month panel: `cal_header`.
- Pane: `cal_menu`.
- Event page: tap `cal_event:<id>`, or a VIEW on `…/events/<id>` with `beginTime` / `endTime` extras for an occurrence.
- Editor: `cal_bar:new`, or the event page → `cal_event_action:edit` (then `cal_occurrence:*` for a series).
- Settings: `cal_bar:more` → `cal_more:settings`.
- Can sync to: Settings → `cal_settings_open_can_sync`, or the first `cal_event_action:sync` with nothing allowed. Back then lands on `cal_sync`.

### (c) Which script exercises which flow
`cal.sh` holds the helpers; `run.sh` retries on the device lock.

| Script | Flow |
|---|---|
| `a_smoke.sh` | start-up, exported surface |
| `b_look.sh` | every page's tags |
| `e3_local.sh` | Tessera creation |
| `e4_events.sh` | typing, the time picker, Save, the observer, delete |
| `e5_recurrence.sh` | all-day, multi-day, series, the three scopes |
| `e6_reminder.sh` | the reminder, the swipe, the pokes, the reused alert id |
| `e17_birthday.sh` | birthdays |
| `e18_perms.sh` | the three permission states |
| `e19_geometry.sh` + `geo.py` | E19's values and the motions |
| `e22_tess.sh` | Tess, the exported EDIT / INSERT, the pane's hide |
| `e23_sync.sh` | first Sync, picker, push, hidden copy, re-Sync, delete here |
| `e24_sync2.sh` | delete both, un-ticked target, synced series, the three refusals |
| `e24_reminder.sh` | one reminder for a synced event |
| `e25_shortcuts.sh` | the shortcuts |
| `edge_cal.sh` | provider off, vanished event, zone, 24-hour, Tessera deleted |
| `p_exception*.sh`, `p_tick.sh` | probes |

**Two traps for your drivers:**
- Tessera rows must be removed with the sync-adapter URI (`purge_tessera_events`); a normal delete can leave a deleted=1 or cancelled row.
- An occurrence's time must come from Instances, not start + k weeks, across the 1 November DST change.
