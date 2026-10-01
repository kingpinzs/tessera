# Phase 16 — the People builder's report (2026-10-01 14:00, branch phase-16-people at 0f6dcfb5)

Saved verbatim by the lead from the builder's hand-back (model output, not the owner's words). The lead's triage of it is in INDEX's row 16 and Change Log.

PEOPLE BUILDER REPORT — phase 16, build tasks 5, 6, 7 and People's half of 9
Worktree /home/jeremyking/projects/metro-launcher-p16/.claude/worktrees/people, branch phase-16-people, clean (git status empty). Nothing pushed.

Short version: all four parts are built and committed; the unit suite is 1036 / 0 failures; nine development rows pass on the final build (md5 34b1373d4945c089). Not proven on the device: the work-profile search, a pinned People app tile, the map action into a real maps app, a PICK result as seen by a calling app, and the card / editor geometry. Three clauses of the doc cannot hold as written (section 6).

## 1. Commits (git log --oneline phase-16..HEAD)
```
0f6dcfb5 Phase 16 People: every development row re-run on the final build
2884e3ba Phase 16 People: page tags read selected in a dump; development proof
cf26860e Phase 16 People: long pickers as lazy lists, the tile clock's rest states
6d88b83c Phase 16 task 9: People's App Shortcuts
a7c1aacc Phase 16 task 7: the People tile's face and its feed
776c4fb9 Phase 16 task 6: the People app
867fcdac Phase 16 task 5: People's data layer and the write guard
```

## 2. What is built, with its proof
Device proof is under `docs/plan/qa/phase-16/dev-people/` (scripts in `scripts/`, each row's log at `<ROW>/<ROW>.txt`). Earlier runs are kept beside each row (`-run1-…`, `-build-<id>-n`).

Final-build runs (emulator-5554, 13:29–13:59 MDT):

| Row | Result | Covers |
|---|---|---|
| DEV-E10 | 65 passed, 0 failed | list, "#" bucket, jump grid, search, list geometry |
| DEV-E13 | 45 / 0, 1 recorded | create, edit, photo, delete, cannot-save notice |
| DEV-E12 | 18 / 0, 3 recorded | the four card actions |
| DEV-E14 | 22 / 0 | Link, Unlink |
| DEV-E15 | 28 / 0, 8 recorded | Share, SIM import, filter |
| DEV-VCARDPROBE | 4 / 0, 4 recorded | the vCard stream's content |
| DEV-E27 | 32 / 0, 1 recorded | groups |
| DEV-E28 | 65 / 0 | write guard, "Can edit" |
| DEV-INTENTS | 50 / 0, 1 recorded | INSERT, INSERT_OR_EDIT, PICK, shortcuts, cannot-read |
| DEV-E11 | 45 / 0 | the tile |

The headers of DEV-E10, DEV-E27 and DEV-INTENTS read `apk match NO (device 2c802c04…)`: the device held the other builder's build when the row began. Each of those rows then installed mine and asserts `the device holds this build` before its first step.

### A. Data layer (task 5)
- **Reads** (`people/PeopleData.kt`): the list by `SORT_KEY_PRIMARY` with `phonebook_label`; a no-name contact files under "#". DEV-E10: `[people] list: 8 contacts read=true write=true`; `张伟's header reads the provider's phonebook label = …` (on this AVD the label is U+2026, and the globe cell jumps to it).
- **Search**: name filter plus phone lookup plus enterprise filter. DEV-E10: `[people] search "555 000 0001": 1 (+0 enterprise)`, `[people] search "ann": 1 (+0 enterprise)`, and "5550000001" typed without spaces finds Ann.
- **Write guard** (`people/PeopleWriteGuard.kt`, pure): `PeopleWriteGuardTest`, 25 tests, 0 failures. It has a named test for each allowed case and each refusal of Decisions point (5), plus:
  - the local account is compared, never a literal null;
  - another profile's contact is never editable;
  - a SIM import that names an allowed account is still refused (stricter than the doc, from "a SIM import goes to the phone").
- **One write layer** (`people/PeopleWriter.kt`): every op resolves the raw contacts from the provider, asks the guard, checks WRITE_CONTACTS, then writes. A grep shows no other insert / update / delete / applyBatch in `people/`, `feeds/PeopleFeed.kt` or `start/PeopleTileFace.kt`.
- **"Can edit" store** (`people/PeopleEditStore.kt`): `people_edit.json`, temp file then rename; it is pruned only after a successful accounts read, from People's reload and from PeopleFeed's provider observer. DEV-E28: `people_edit.json: {"allowed":[{"name":"qa.personal@example.com","type":"com.example"}]}`, then empty again after the untick.
- **Guard on the device** (DEV-E28):
  - `people_card_readonly names the account = This contact is in qa.work@example.com. To change it, allow that account in Can edit.`
  - `[people] edit 3473r123-572B313357474D3F: refused (account not allowed)` with no editor node.
  - Mixed contact: Edit present, `NO people_card_delete`, `only Lou's raw contact was written: [people] write update raw=122: ok`.
  - Wade's raw_contacts row and data rows equal their BEFORE file after every step (8 checks).
- **Writes** (DEV-E13): `[people] write insert raw=113: ok` (NULL account), `write update raw=112: ok`, `write delete raw=113: ok`. The photo goes through the photo picker (`PhotoPickerActivity` resumed) and is saved on Save as a `vnd.android.cursor.item/photo` row; the card's centre pixel reads (254,0,0).
- **WRITE_CONTACTS revoked** (DEV-E13): `[people] write update raw=112: failed WRITE_CONTACTS not held`, with the `people_notice` text and `people_notice_action`. Android granted it from the notice without a dialog (same permission group as the held READ); that is recorded.
- **Link / Unlink** (DEV-E14): `type=1, raw_contact_id1=115, raw_contact_id2=116` with `[people] link 115+116: ok`; then `type=2` with `[people] unlink 116+115: ok`. The `unlink` line is my addition; the doc names only `link`.
- **SIM** (DEV-E15): the emulated SIM accepted the insert; `sim import: 1 of 1`; the import landed on the phone (NULL account). The `0 of 0` branch was not produced.
- **Share** (DEV-E15): `share 0r117-2B4545413333: content://com.android.contacts/contacts/as_vcard/0r117-2B4545413333`. `dumpsys activity permissions` holds exactly one grant naming a vCard URI: `sourcePkg=com.android.providers.contacts targetPkg=com.android.intentresolver mode=0x1`. The stream's content was read back by handing the same URI to the image's `com.android.contacts/.vcard.ImportVCardActivity` (DEV-VCARDPROBE): name Ann Lee, phone `+1-555-000-0001`, e-mail ann@example.com.
- **Groups** (DEV-E27): `title=Family, account_type=NULL, account_name=NULL`; `[people] group create 3: ok`; both membership rows; `smsto:+15550000001;+15550000002` to `org.fossify.messages`; `group rename 3: ok`; `group delete 3: ok`; with WRITE revoked, `[people] group create new: failed WRITE_CONTACTS not held` plus the notice.

`PeopleModelTest` (25 tests) covers buckets, grid cells, search form, card rows, type words, groups, SIM, filter and photo sampling.

### B. People app (task 6)
- **Geometry measured on the device** (DEV-E10, ink read with phase 15's `ink.py`):
  - row pitch 50.00; avatar 32.00 at x 12.00; name ink 57.67 (57.75 wanted);
  - letter ink 14.67, cap 22.33, 43.5 above the first avatar;
  - pivot cap top 19.33 (19.5 wanted), ink from 12.67;
  - search box 48 / 36 / 12 → 348, border (133,133,133);
  - grid pitch 72 × 72, 4 columns, first-row cap top 119.67, cap 14.33, accent or (52,52,52) by cell.
- **Pivot motion** (LOOK2, an earlier build): `[motion] people_pivot t0=1750037 peak=267 overshoot=0 settle=267 frames=17 maxGapMs=17`.
- **Card actions** (DEV-E12):
  - `[people] action call -> com.android.dialer/.main.impl.MainActivity tel:+1 555 000 0001`, and Telecom gained `CallTC@8 … (MO - outgoing)`;
  - `[people] action text -> org.fossify.messages/.activities.SplashActivity.Green smsto:+15550000001`;
  - `[people] action mail -> com.fsck.k9/net.thunderbird.app.common.MainActivity mailto:ann@example.com`, top package K-9;
  - `[people] action map -> org.fossify.notes/.activities.SplashActivity.Green geo:0,0?q=1%20Main%20St%20Springfield`.
- **Routes** (DEV-INTENTS):
  - INSERT prefilled on the phone, nothing in the provider before Save, saved to a NULL account although the intent named `qa.work@example.com`, and the caller's text is not in the ring;
  - INSERT_OR_EDIT: the list with `people_insert_new`; a read-only contact is refused with the notice and the `edit … refused` line; an editable one opens the editor with the number added as `people_field:phone:1`;
  - PICK: `[people] pick: one contact URI granted (read)` and `one phone URI granted (read)`, with a two-number contact asking which; Back cancels;
  - cannot-read: `[people] list: 0 contacts read=false write=true`, the notice and its grant, and the list loads after the grant with no restart.

### C. Tile (task 7)
- `feeds/PeopleFeed.kt`, `feeds/PeopleTileRules.kt` (pure; `PeopleTileRulesTest`, 10 tests: skip decision, timings, next photo), `start/PeopleTileFace.kt`.
- DEV-E11: `[people] tile: 3 photos`, published under `feed:people` and under `cmp:app.tileshell/app.tileshell.people.PeopleActivity`, never a `pkg:` key.
- Events 7701, 7701, 7701, 7705, 7701 ms apart. Per event: out settle 334–339 ms, in settle 586–592 ms, 1884–1888 ms from the out's t0 to the in's settle, `maxGapMs=17` on every slide.
- Real lines: `[people] tile event 2 t0=4984522 lookup=0r132-2D472D4F51474533` / `[motion] people_bubble_out t0=4984522 peak=339 overshoot=0 settle=339 frames=21 maxGapMs=17` / `[motion] people_bubble_in t0=4985819 peak=592 overshoot=0 settle=592 frames=36 maxGapMs=17`.
- Five events had the settled bubble's centre pixel equal to the colour of the contact named by `lookup=`.
- With the photo rows deleted: `[people] tile: 0 photos`, `people_tile_pattern` present, six screencaps over 15 s identical on the tile, no event line.
- `people_tile_face` is on the face's root in both forms, as you asked.

### D. App Shortcuts (task 9)
`res/xml/shortcuts_people.xml`, three strings in `values/people.xml`, the meta-data on the activity. DEV-INTENTS: dumpsys lists the three ids under PeopleActivity, `contacts=0 new_contact=1 groups=2`, and each `page` extra opens its page.

## 3. Whole suite
`./gradlew :app:testDebugUnitTest`: 1036 tests, 0 failures, 0 errors, 0 skipped, rc 0 (baseline 976 plus 60 of mine). Last run on the final code: `docs/plan/qa/phase-16/dev-people/unit-C.out` and `unit-C.rc`. Final debug APK is 339,427,210 bytes against 337,186,240 in `apk-size-before.txt`.

## 4. Not built, or not proven
Not built:
- No writer or UI stars a contact; the STARRED rule exists in the guard and its JVM test only.
- W10M's list "select" (multi-select) and the card's "pin" are not in the doc's scope and are not built. Delete takes pin's place on the card's app bar.
- The jump grid closes at once; P2.8's 83-ms crossfade is not built. Its open motion is (`[motion] people_jump_grid`).
- The editor has no 34-epx pencil variant: Name and Company are single 32-epx boxes with no structured-name sub-editor.
- INSERT returns no result URI to a caller (the doc does not ask for one).

Built but not proven on the device:
- **Work-profile search**: the enterprise filters, the `people_row:enterprise:<lookup>` row with the briefcase, and the read-only enterprise card. Only `(+0 enterprise)` with no profile was seen.
- **A pinned People app tile**; only the two-key publish is proven. Small and wide tile sizes were not looked at.
- **The map action into a real maps app** (see section 6, item 4).
- **A PICK result as a calling app receives it**: `am start` has no caller, so only the line and the finish were seen.
- **Card and editor geometry**: built to r11 values, but only the card photo's 124 epx and the accent page were measured.
- Built, never driven:
  - the `0 of 0` SIM branch;
  - the group filter and "Hide contacts without phone numbers" (JVM only);
  - a read-only group's page (`people_group_readonly`);
  - a card or group closing when deleted elsewhere;
  - `link …: failed` and `write update …: failed <err>` from a vanished raw contact;
  - the remove-photo flyout, the birthday date picker and Notes;
  - 5,000 contacts, 20 numbers, a 20 MB photo;
  - the light theme.
- `[people] tile: photo <lookup> skipped: <why>` is JVM only, as the doc expects.
- Phase 03's Tess rows were not re-run; `cortana/` has no diff.

## 5. ADDs to other phases' parts (for the Change Log)
- **Phase 15**, `clock/ClockWidgets.kt`: `ClockAppBar` gains `fill: Color = ClockMetrics.BAND_FILL` and `tagPrefix: String = "clock"`, placed before `onExpand` so existing trailing-lambda callers compile unchanged. The Calendar builder may touch the same signature.
- **`brand/Glyph.kt`**: LINK, LINK_DISMISS, FILTER, IMPORT, PEOPLE_TEAM, appended at the end of the file.
- **Phase 01**:
  - `tiles/engine/TileContent.kt`: `TileFace.People`, `PersonPhoto`;
  - `tiles/engine/LiveTileEngine.kt`: `PEOPLE = "feed:people"`;
  - `start/StartPage.kt`: two `Slot.PEOPLE` branches;
  - `start/TileView.kt`: one `when` branch;
  - `ShellApp.startFeeds`: one line, `app.tileshell.feeds.PeopleFeed.start(this)`;
  - new files `start/PeopleTileFace.kt`, `feeds/PeopleFeed.kt`, `feeds/PeopleTileRules.kt`.
- **`AndroidManifest.xml`**: only the `<meta-data android:name="android.app.shortcuts">` inside PeopleActivity.
- Reused unchanged: calculator `InkText` / `rememberInkLayout` / `DatePickerPanel`, recorder `RecorderDialog`, settings `TwoLineItem` / `ToggleRow` / `SectionHeader`, clock `ClockFlyout` / `PressBox` / `ClockCheckbox`, `OutlinedField`, `OverlayLayer` / `modalOverlay` / `overlayItem`.

## 6. Deviations, doc clauses that do not hold, defects outside my scope

### Doc clauses that cannot hold as written
1. **R3 A9 is internally inconsistent.** 333 ms out + a "≈6-frame" (250 ms) pause + 583 ms in is 1.17 s, not the 1.88 s event that E11 times from the out's t0 to the in's settle. I built E11's three numbers, which makes the pause 964 ms (`PeopleTileRules.PAUSE_MS`, one constant). The 6-frame pause is not built.
2. **E15's `adb shell content read --uri <as_vcard uri>` is refused on this image**: `java.io.FileNotFoundException: No files supported by provider`. `content read` opens a plain file and the Contacts provider serves the vCard as an asset stream. What does work: `content query` on the URI (`_display_name=Ann Lee.vcf`), the grant in `dumpsys activity permissions`, and an import through `com.android.contacts/.vcard.ImportVCardActivity`. "Contacts" on the share sheet is Fossify Contacts, which imports into its own private storage.
3. **E12's call clause** (DEV-CALLPROBE, recorded):
   - `adb emu gsm list` printed only `OK` even with the dialer's in-call screen up for a live call;
   - a call to `+1 555 000 0001` or `+15550000001` is ended by the emulated network about 0.25 s after Telecom creates it (`DisconnectCause Code: (REMOTE) Reason: (NORMAL)`);
   - a plain `5551230001` stays up;
   - Telecom's `CallTC@n … (MO - outgoing)` record is what proves the call.
4. **E12's Mail clause**: K-9 with no account resolves no `mailto:`. People then starts the slot's own activity and logs `[people] action mail: <component> has no handler for it; the app was opened` before the action line, which names the slot's component as E12 expects. The slot's launcher component is what the action line names for mail and map; for call and text it is the PHONE / MESSAGING slot app's component.

### Deviations of mine
- A new contact's header reads `NEW <ACCOUNT> CONTACT`; an existing one's reads `EDIT <ACCOUNT> CONTACT` as the doc says.
- The read-only line sits in a band above the app bar so it is always on screen. For another profile's contact it reads "This contact is in your work profile. It can't be changed here." and does not open Can edit.
- Row press uses X19's 15 % white (the Decisions' Fidelity list), not r11 U7's fill.
- The photo is saved on Save, not on pick.
- Delete (contact, group) asks once, using the recorder's R7 §1.3.9 dialog.
- The link picker lists contacts as they were when it opened and does not follow the provider, so EDGE's `link …: failed` sub-step is producible.
- The filter applies as ticked, with no Done / Cancel.
- A page tag carries `Role.Tab` so a dump reads `selected="true"`; without the role Android reports it as `checked`.

### Incidents on the shared device, all restored
- **10:44, DEV-E12 run 1**: the emulator process exited about 5 s after the map action started OsmAnd. You later told me the host's disk filled at 10:58, so the cause is not established. I did not start a maps app again: DEV-E12 points the Maps slot at Fossify Notes. One wrinkle for the gate: on this AVD an unassigned MAPS slot may auto-resolve to a maps app (I did not read which), so the gate's E12 will launch one.
- **12:42, DEV-E12 run 2**: my driver sent `KEYCODE_ENDCALL` with no call up, which put the screen to sleep. The next `row_begin` woke it; `people.sh` `end_call` now sends the key only while a call is up.
- **12:44, DEV-CALLPROBE run 1**: left a live call for about a minute; run 2 ended it.

### Driver notes
- After `am force-stop app.tileshell`, Android restarts the home app at once, so a MARK must be taken before the stop.
- A dump's bounds for a tappable text node are Android's 48-epx touch target; measure ink in a screencap.
- `uiautomator` dumps are one line, so count with `grep -o … | wc -l`.
- A phone-only raw contact (id 2, no name, not mine) is on the AVD and shows as "(No name)" under "#".

## 7. Device state left (`dev-people/state-after.out`, 14:00 MDT)
- Installed: my final build, md5 34b1373d4945c089.
- READ_CONTACTS, WRITE_CONTACTS and CALL_PHONE granted.
- raw_contacts holds only Mom (1) and the nameless raw (2). No group, no aggregation exception, icc/adn empty.
- `people_edit.json` is `{"allowed":[]}`; `people_filter.json` hides nothing.
- Slots are CALENDAR / MUSIC / PEOPLE on the shell's apps; no pushed picture; no call; awake on Start.
- Fossify Messages, K-9, Notes, the dialer and Contacts were force-stopped after use.
- Nothing known unrestored.

## 8. Driver map

### Tags beyond the spec's list
- **App bar** (every page with one): `people_app_bar`, `people_more` (the "…"), `people_more_menu`, `people_bar_scrim`.
- **List** (`people_page:list`):
  - `people_pivots`; `people_bar:add` (new contact, CONTACTS pivot); `people_group_new` (same slot, GROUPS pivot); `people_more:settings` (inside the "…" menu);
  - `people_search_box`, `people_search` (the field), `people_search_hint`;
  - `people_contacts`, `people_list`, `people_results`, `people_avatar:<lookup>`, `people_empty`, `people_filter_caption`;
  - `people_jump_grid`; `people_jump_cell:globe`; a live cell reads `checked="true"`.
- **Enterprise marker**: the row is `people_row:enterprise:<lookup>`, its glyph `people_row_briefcase:<lookup>`, its name `people_name:<lookup>`.
- **Pick / insert modes**: `people_pick_header` ("CHOOSE A CONTACT", "CHOOSE A PHONE NUMBER", "ADD TO A CONTACT"); rows are the ordinary `people_row:<lookup>`; `people_insert_new`; `people_page:pick_number` with `people_pick_number:<data id>`.
- **Card** (`people_page:card` wraps `people_card:<lookup>`):
  - `people_card_name`, `people_card_profile`, `people_card_account`, `people_card_info:<company|birthday|notes>:<n>`, `people_action_title`, `people_action_detail`;
  - bar: `people_card_link`, `people_card_edit`, `people_card_delete`; `people_card_share` is inside the "…" menu;
  - delete prompt: `people_delete_dialog`, `people_delete_confirm`, `people_delete_cancel`;
  - action rows: `text:0` is "Send message" (first mobile, else first number); `call:<n>` per number; `text:<n≥1>` for each other number; `mail:<n>`; `map:<n>`.
- **Editor** (`people_page:editor`):
  - `people_editor`, `people_editor_header`, `people_editor_photo`, `people_editor_save` (disabled until a change), `people_editor_cancel`, `people_editor_account_label`, `people_editor_account_menu`;
  - fields: `people_field:name`, `people_field:phone`, `people_field:email`, `people_field:address`, `people_field:company`, `people_field:birthday`, `people_field:notes`; second and later of a kind are `people_field:<kind>:<n>` with n from 1;
  - `people_field_label:<name>`; `people_field_type:<name>` (opens `people_type_menu` with `people_type_option:<type number>`);
  - `people_add_field:<phone|email|address|other>`; "other" opens `people_other_menu` with `people_other:<birthday|notes>`;
  - photo: tap `people_editor_photo`. With no photo it opens the picker directly; with one it opens `people_photo_menu` with `people_photo_choose` / `people_photo_remove`;
  - mixed contact: `people_editor_readonly:<type>:<name>`, `people_field_readonly:<data id>`; `people_rule`.
- **Setting a field's value**: tap the field, delete its text, `adb shell input text`, then Back for the keyboard (`people.sh` `set_field`). Birthday is set on the Calculator's date picker (`calc_date_pick:<column>:<value>`, `calc_date_pick_ok`).
- **Link**: `people_page:link`, `people_link_add`, `people_link_unlink:<raw>`, `people_link_line`; picker `people_page:link_picker` with `people_row:<lookup>` rows, `people_link_empty`.
- **Settings**: `people_page:settings`, `people_settings:can_edit`, `people_settings:filter`, `people_settings:sim`, `people_page_title`.
- **Can edit**: the checkbox itself is `people_can_edit:<type>:<name>` (read `checked=`; a tap anywhere on its row toggles it); `people_can_edit_line`, `people_can_edit_empty`.
- **Filter** (`people_page:filter`): `people_filter_phones` (toggle), `people_filter_account:phone`, `people_filter_account:<type>:<name>`, `people_filter_group:<id>`.
- **SIM** (`people_page:sim`): `people_sim_import`, `people_sim_clear`, `people_sim_check:<n>` (the box); `people_sim_row:<n>` is the entry's name text; `people_sim_empty`.
- **Groups**:
  - pivot rows `people_group:<id>` with `people_group_title:<id>`, `people_group_count:<id>`, `people_group_account:<phone|type:name>`, `people_groups_empty`;
  - group page `people_page:group`, whose content root is also `people_group:<id>`; `people_group_title`, `people_group_members`, `people_group_readonly`; bar: `people_group_action:text`, `people_group_add_member`, `people_group_action:rename`, `people_group_action:delete` (all four on the bar, the last three only where writable);
  - delete prompt: `people_group_delete_dialog`, `people_group_delete_confirm`, `people_group_delete_cancel`;
  - editor `people_page:group_editor`: `people_group_name`, `people_group_save`, `people_group_cancel`, and for a new group `people_editor_account` with its rows;
  - member picker `people_page:member_picker`: tap `people_row:<lookup>` to toggle; `people_member:<lookup>` is the checkbox.
- **Notice**: `people_notice` (text) and `people_notice_action` ("allow access", "allow calls", "open Can edit").
- **Tile**: `people_tile_face` (root, both forms), `people_tile_bubble` (the settled bubble; its content-desc is the lookup key), `people_tile_pattern`.

### Shortest tap paths from a cold launch
(`am start -n app.tileshell/.people.PeopleActivity …`)
- List: `-a android.intent.action.MAIN`.
- GROUPS: `--es page groups`, or tap `people_pivot:groups`.
- New-contact editor: `--es page new_contact`, or `people_bar:add`.
- Card: `-a VIEW -d content://com.android.contacts/contacts/<id>`, or tap `people_row:<lookup>`.
- Editor of a contact: `-a EDIT -d …/contacts/lookup/<lookup>/<id>`, or card → `people_card_edit`.
- Settings: `people_more` → `people_more:settings`; then `people_settings:can_edit`, `:filter` or `:sim`. Can edit is also reached from `people_card_readonly`.
- Link page: card → `people_card_link`; picker: → `people_link_add`.
- Group page: GROUPS → `people_group:<id>`; new group: GROUPS → `people_group_new`; members: group page → `people_group_add_member`.
- Share: card → `people_more` → `people_card_share`.
- Pick: `-a PICK -t vnd.android.cursor.dir/contact` or `…/phone_v2`.
- Insert: `-a INSERT -t vnd.android.cursor.dir/contact --es name … --es phone …`.
- Insert or edit: `-a INSERT_OR_EDIT -t vnd.android.cursor.item/contact --es phone …`.

### Which script exercises which flow
(all in `dev-people/scripts/`)
- `people.sh`: shared helpers — `ensure_build`, the fixtures (`fx_raw`, `fx_name`, `fx_phone`, `fx_email`, `people_fixtures_down` by id through the sync-adapter URI), `gdump`, `set_field`, `push_photo` / `pick_photo` / `remove_photos`, `px` / `ink_color` / `assert_color`, `outgoing_calls` / `end_call`, `status_bottom`.
- `run.sh <script>`: waits for the device lock. `rerun.sh <script> <ROW>`: keeps the last run, then runs.
- `e10.sh`: list, "#", non-Latin, aggregated pair, jump grid, search, list geometry.
- `e13.sh`: new, edit, photo picker, delete, WRITE revoked and the grant.
- `e12.sh`: the four card actions with the slots written through `layout_restore`.
- `e14.sh`: link and unlink.
- `e15.sh`: share and its URI grant, SIM import, account filter.
- `vcardprobe.sh`: the stream read back through an import.
- `e27.sh`: groups end to end.
- `e28.sh`: the guard and "Can edit".
- `intents.sh`: INSERT, INSERT_OR_EDIT, PICK, shortcuts, READ revoked.
- `e11.sh` with `tile_times.py`: tile events, timing, colours, static pattern.
- `callprobe.sh`, `shareprobe.sh`: recorded probes.
- `state.sh`: read-only device state.
- `look1.sh`, `look2.sh`: screenshots of every page.
