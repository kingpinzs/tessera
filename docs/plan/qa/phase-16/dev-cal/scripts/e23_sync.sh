#!/usr/bin/env bash
# Development proof of E23 / E24's core, part 1: the first Sync with nothing allowed goes straight to "Can sync to"
# (grouped by account, a read-only calendar not listed), ticking Personal and coming back lands on the Sync picker with
# Personal only; the tapped Sync copies the event into Personal and nowhere else; the copy is hidden in the views; a
# re-Sync compares the copy with the local event (updated / ok / the other side's edit overwritten / recreated); and
# "Delete here" keeps the copy, which then shows as Personal's event. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E23_SYNC "the first Sync, the tapped push, the hidden copy, re-Sync, delete here"
c6; cal_fixtures_down
sync_fixtures_up
mkcal "$PERSONAL_ACCT" 'QA Shared' 0 200 > /dev/null 2>&1; SHARED="$(cal_id "$PERSONAL_ACCT" 'QA Shared')"
note "personal=$PERSONAL work=$WORK shared(read-only)=$SHARED offsite=$OFFSITE"
assert_ne "the fixtures exist" "||" "$PERSONAL|$WORK|$SHARED"
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; BEFORE="$(event_count "calendar_id=$TESS AND deleted=0")"
NOW="$(device_ms)"; START=$(( (NOW / 60000 + 120) * 60000 ))
L="$(mkevent "$TESS" 'E24 standup' "$START" "$(( START + 3600000 ))")"
adb shell "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$L --bind minutes:i:10 --bind method:i:1" < /dev/null
assert_absent "nothing is allowed before the first Sync" "$PERSONAL_ACCT" "$(sync_json | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("allowed"))
except Exception: print("(no file)")')"

# ---- the first Sync: straight to "Can sync to"
open_event "$L"
MARK="$(ring_mark)"
tap cal_event_action:sync 2; dump_ui "$ROW_DIR/can_sync.xml"; screencap "$ROW_DIR/can_sync.png"
D="$ROW_DIR/can_sync.xml"
assert_eq "Can sync to opened directly" yes "$(has_node "$D" cal_can_sync)"
assert_eq "Personal and Work are listed, both unchecked" "false false" "$(node_attr "$D" "cal_settings_can_sync:$PERSONAL" checked) $(node_attr "$D" "cal_settings_can_sync:$WORK" checked)"
assert_eq "each under its account's header" "yes yes" "$(has_node "$D" "cal_account:$PERSONAL_ACCT") $(has_node "$D" "cal_account:$WORK_ACCT")"
assert_eq "its one line" "Choose which calendars Sync may use" "$(node_text "$D" cal_notice)"
assert_eq "no Sync target is offered yet" 0 "$(count_prefix "$D" cal_sync_target:)"
assert_eq "a calendar the phone may only read is not listed; nor is Tessera" "no no" "$(has_node "$D" "cal_settings_can_sync:$SHARED") $(has_node "$D" "cal_settings_can_sync:$TESS")"
assert_contains "the routed line" "[calendar] sync event=$L: no calendar allowed -> can sync to" "$(ring_since "$MARK")"
tap "cal_settings_can_sync:$PERSONAL" 1.5; dump_ui "$ROW_DIR/can_sync_ticked.xml"
assert_eq "Personal is ticked" true "$(node_attr "$ROW_DIR/can_sync_ticked.xml" "cal_settings_can_sync:$PERSONAL" checked)"
J="$(sync_json)"; log "calendar_sync.json: $J"
# Fix round F16: the key carries the calendar's own name (the fixture's mkcal binds name = the display name).
assert_contains "calendar_sync.json lists Personal's _ID, account name, type and name under allowed" "\"allowed\":[{\"id\":$PERSONAL,\"accountName\":\"$PERSONAL_ACCT\",\"accountType\":\"com.google\",\"name\":\"QA Personal\"}]" "$J"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; dump_ui "$ROW_DIR/picker.xml"
assert_eq "Back lands on the Sync picker" yes "$(has_node "$ROW_DIR/picker.xml" cal_sync)"
assert_eq "which lists Personal only" "cal_sync_target:$PERSONAL " "$(ids_prefix "$ROW_DIR/picker.xml" cal_sync_target:)"
assert_eq "under its account" yes "$(has_node "$ROW_DIR/picker.xml" "cal_account:$PERSONAL_ACCT")"
assert_eq "before any Sync, Personal holds nothing" "" "$(titles_in "$PERSONAL")"

# ---- the tapped push
MARK="$(ring_mark)"
tap "cal_sync_target:$PERSONAL" 2.5; dump_ui "$ROW_DIR/synced.xml"; screencap "$ROW_DIR/synced.png"
assert_contains "the sync line" "sync event=$L -> calendar $PERSONAL: ok" "$(sync_line "$(ring_since "$MARK")")"
assert_eq "Personal holds ONE event, the copy" "E24 standup" "$(titles_in "$PERSONAL")"
C="$(event_ids "calendar_id=$PERSONAL" | tr -d ' ')"
assert_eq "with the local event's start and end" "$(event_rows dtstart:dtend "_id=$L" | sed 's/^Row: 0 //')" "$(event_rows dtstart:dtend "_id=$C" | sed 's/^Row: 0 //')"
work_holds_offsite "after the Sync"
assert_eq "the marker" "synced to QA Personal" "$(node_text "$ROW_DIR/synced.xml" "cal_synced_marker:$L")"
assert_eq "with its account beside it" yes "$(has_node "$ROW_DIR/synced.xml" "cal_account:$PERSONAL_ACCT")"
J="$(sync_json)"
assert_contains "calendar_sync.json maps the local id to Personal and the copy" "{\"id\":$PERSONAL,\"accountName\":\"$PERSONAL_ACCT\",\"accountType\":\"com.google\",\"name\":\"QA Personal\",\"local\":$L,\"copy\":$C}" "$J"
assert_contains "the copy has its own reminder row" "minutes=10" "$(S "content query --uri content://com.android.calendar/reminders --projection event_id:minutes --where \"event_id=$C\"")"
record "the copy's dirty flag (a normal insert: the account's adapter uploads it)" "$(event_rows dirty "_id=$C" | sed 's/^Row: 0 //')"

# ---- the copy is hidden in the shell's views
open_day "$START"; dump_ui "$ROW_DIR/day.xml"
assert_eq "the Day view shows Standup once, the local one" "1 yes no" "$(grep -o 'resource-id="cal_event_title:[0-9]*"' "$ROW_DIR/day.xml" | wc -l | tr -d ' ') $(has_node "$ROW_DIR/day.xml" "cal_event:$L") $(has_node "$ROW_DIR/day.xml" "cal_event:$C")"
tap cal_bar:view; tap cal_view_pick:agenda 2; dump_ui "$ROW_DIR/agenda.xml"
assert_eq "Agenda the same" "yes no" "$(has_node "$ROW_DIR/agenda.xml" "cal_event:$L") $(has_node "$ROW_DIR/agenda.xml" "cal_event:$C")"
tap cal_bar:view; tap cal_view_pick:week 2; dump_ui "$ROW_DIR/week.xml"
assert_eq "Week the same" "yes no" "$(has_node "$ROW_DIR/week.xml" "cal_event:$L") $(has_node "$ROW_DIR/week.xml" "cal_event:$C")"
sleep 1
assert_contains "the feed's tile and agenda gained nothing: one event" "agenda=1" "$(line_of "$(ring_since "$ROW_MARK")" '[calendar] refresh (')"

# ---- re-Sync: the copy is compared with the local event
resync() { open_event "$L"; MARK="$(ring_mark)"; tap cal_event_action:sync 2.5; dump_ui "$ROW_DIR/resync_$1.xml"; sync_line "$(ring_since "$MARK")"; }
adb shell "content update --uri $EVENTS/$L --bind title:s:'E24 standup 2'" < /dev/null; sleep 1
assert_contains "the local event renamed, Sync: updated" "-> calendar $PERSONAL: updated" "$(resync renamed)"
assert_eq "the copy reads the new title, still one row" "E24 standup 2" "$(titles_in "$PERSONAL")"
assert_contains "nothing changed on either side, Sync: ok" "-> calendar $PERSONAL: ok" "$(resync same)"
other_side "$PERSONAL_ACCT" update "$C" "--bind title:s:'Edited elsewhere'"
assert_eq "the copy's title changed on the other side" "Edited elsewhere" "$(titles_in "$PERSONAL")"
assert_contains "Sync overwrites the other side's edit: updated" "-> calendar $PERSONAL: updated" "$(resync otherside)"
assert_eq "the copy's title is the local title again" "E24 standup 2" "$(titles_in "$PERSONAL")"
other_side "$PERSONAL_ACCT" delete "$C"
assert_eq "the copy deleted on the other side" "" "$(titles_in "$PERSONAL")"
assert_contains "Sync makes it again: recreated" "-> calendar $PERSONAL: recreated" "$(resync recreated)"
assert_eq "one copy again" "E24 standup 2" "$(titles_in "$PERSONAL")"
assert_contains "and the notice says so" "made it again" "$(node_text "$ROW_DIR/resync_recreated.xml" cal_notice)"
C2="$(event_ids "calendar_id=$PERSONAL AND deleted=0" | tr -d ' ')"
work_holds_offsite "after the re-Syncs"

# ---- delete here (the default): the copy is kept and shows from then on as Personal's event
open_event "$L"
tap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/delete_choice.xml"
assert_eq "the delete choice offers here and both" "yes yes" "$(has_node "$ROW_DIR/delete_choice.xml" cal_delete_choice:here) $(has_node "$ROW_DIR/delete_choice.xml" cal_delete_choice:both)"
tap cal_delete_choice:here 2.5
assert_eq "the local event is gone" 0 "$(event_count "_id=$L")"
assert_eq "the copy is kept" "E24 standup 2" "$(titles_in "$PERSONAL")"
assert_absent "its mapping is gone from calendar_sync.json" "\"local\":$L" "$(sync_json)"
open_day "$START"; dump_ui "$ROW_DIR/day_after.xml"
assert_eq "the copy now shows in the views as Personal's event" yes "$(has_node "$ROW_DIR/day_after.xml" "cal_event:$C2")"
tap "cal_event:$C2" 2; dump_ui "$ROW_DIR/copy_page.xml"
assert_eq "read-only: its page has no actions" "yes 0" "$(has_node "$ROW_DIR/copy_page.xml" "cal_event_page:$C2") $(count_prefix "$ROW_DIR/copy_page.xml" cal_event_action:)"
work_holds_offsite "at the end"

# ---- restore
c6
cal_fixtures_down
purge_tessera_events "title LIKE 'E24 %'"
assert_eq "the account calendars are gone" "" "$(cal_id "$PERSONAL_ACCT")$(cal_id "$WORK_ACCT")"
assert_eq "Tessera holds what it held before the session" "$BEFORE" "$(event_count "calendar_id=$TESS AND deleted=0")"
ensure_start
session_end
