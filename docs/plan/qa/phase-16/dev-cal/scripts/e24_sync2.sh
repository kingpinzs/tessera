#!/usr/bin/env bash
# Development proof of E24's core, part 2, and of the Sync refusals: "Delete here and from <calendar>" removes both;
# with the target un-ticked the delete offers "here" only (T16-12); a weekly series with a changed occurrence and a
# reminder is copied as a master with its rrule and duration, one exception pointing at the COPY's master and a
# reminder row; a target lowered to read-only refuses (`failed calendar read-only`, nothing written); a copy moved to
# another calendar refuses (`failed mapping stale`); a target removed from the phone refuses (`failed calendar gone`,
# the marker keeps a warning). Work holds exactly Offsite throughout. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E24_SYNC2 "delete both, the un-ticked target, a synced series, and the three refusals"
c6; cal_fixtures_down; sleep 2
# The account calendars are gone and the shell is alive (Home): its allowed list has followed the provider.
assert_absent "with no account calendar on the phone, nothing is allowed" "accountName" "$(sync_json | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("allowed"))
except Exception: print("(no file)")')"
sync_fixtures_up
note "personal=$PERSONAL work=$WORK offsite=$OFFSITE"
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; BEFORE="$(event_count "calendar_id=$TESS AND deleted=0")"
START="$(day_ms 1 15:00)"

# ---- allow Personal from Settings > Can sync to
open_can_sync
dump_ui "$ROW_DIR/before_tick.xml"
assert_eq "a calendar created again under the id it had starts NOT allowed (ids are reused after a delete)" false "$(node_attr "$ROW_DIR/before_tick.xml" "cal_settings_can_sync:$PERSONAL" checked)"
set_can_sync "$PERSONAL" true
dump_ui "$ROW_DIR/allowed.xml"
assert_eq "Personal ticked from the settings page" true "$(node_attr "$ROW_DIR/allowed.xml" "cal_settings_can_sync:$PERSONAL" checked)"

sync_new() { # local id -> taps Sync and the Personal target (a first Sync with Personal allowed opens the picker)
  open_event "$1"; tap cal_event_action:sync 2; tap "cal_sync_target:$PERSONAL" 2.5
}
resync() { open_event "$1"; MARK="$(ring_mark)"; tap cal_event_action:sync 2.5; dump_ui "$ROW_DIR/resync_$2.xml"; sync_line "$(ring_since "$MARK")"; }

# ---- delete here and from Personal
A="$(mkevent "$TESS" 'E24 both' "$START" "$(( START + 3600000 ))")"
sync_new "$A"
assert_eq "A is copied" "E24 both" "$(titles_in "$PERSONAL")"
open_event "$A"; tap cal_event_action:delete 1.5; tap cal_delete_choice:both 2.5
assert_eq "both: the local event is gone" 0 "$(event_count "_id=$A")"
assert_eq "both: and the copy" "" "$(titles_in "$PERSONAL")"
assert_absent "both: and the mapping" "\"local\":$A" "$(sync_json)"
work_holds_offsite "after delete both"

# ---- the target un-ticked: "here" only
B="$(mkevent "$TESS" 'E24 kept' "$START" "$(( START + 3600000 ))")"
sync_new "$B"
adb shell input keyevent KEYCODE_BACK; sleep 1
open_can_sync; set_can_sync "$PERSONAL" false
open_event "$B"; dump_ui "$ROW_DIR/unticked_page.xml"
assert_eq "the marker stays after its target is un-ticked" "synced to QA Personal" "$(node_text "$ROW_DIR/unticked_page.xml" "cal_synced_marker:$B")"
tap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/unticked_delete.xml"
assert_eq "delete offers here and NOT both for a target no longer allowed" "yes no" "$(has_node "$ROW_DIR/unticked_delete.xml" cal_delete_choice:here) $(has_node "$ROW_DIR/unticked_delete.xml" cal_delete_choice:both)"
adb shell input keyevent KEYCODE_BACK; sleep 1
MARK="$(ring_mark)"
tap cal_event_action:sync 2; dump_ui "$ROW_DIR/unticked_sync.xml"
assert_eq "with nothing allowed, Sync opens Can sync to again" yes "$(has_node "$ROW_DIR/unticked_sync.xml" cal_can_sync)"
set_can_sync "$PERSONAL" true
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- a weekly series with a changed third occurrence and a reminder
S0="$(day_ms 2 09:00)"; WEEK=$(( 7 * 86400000 ))
W="$(mkseries "$TESS" 'E24 weekly' "$S0" 'FREQ=WEEKLY;COUNT=5' PT1H)"
adb shell "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$W --bind minutes:i:10 --bind method:i:1" < /dev/null
T3=$(( S0 + 2 * WEEK ))
open_event "$W" "$T3" "$(( T3 + 3600000 ))"; tap cal_event_action:edit 1.5; tap cal_occurrence:this 2; type_field title "X"; tap cal_editor_save 2.5
sync_new "$W"
ROWS="$(event_rows _id:title:rrule:duration:original_id:originalInstanceTime "calendar_id=$PERSONAL AND deleted=0 AND title LIKE 'E24 weekly%'")"; log "Personal's rows for the series: $ROWS"
CM="$(printf '%s\n' "$ROWS" | grep 'rrule=FREQ=WEEKLY;COUNT=5' | sed -n 's/^Row: [0-9]* _id=\([0-9]*\),.*/\1/p')"
assert_ne "Personal holds a master with the rrule" "" "$CM"
assert_contains "and the duration" "duration=PT1H" "$(printf '%s\n' "$ROWS" | grep "_id=$CM,")"
assert_contains "one exception whose original_id is the COPY's master" "title=E24 weeklyX, rrule=NULL, duration=NULL, original_id=$CM, originalInstanceTime=$T3" "$ROWS"
assert_eq "exactly two rows" 2 "$(printf '%s\n' "$ROWS" | grep -c '_id=')"
assert_contains "a reminder row of 10 minutes on the copy" "minutes=10" "$(S "content query --uri content://com.android.calendar/reminders --projection event_id:minutes --where \"event_id=$CM\"")"
assert_contains "Sync again with nothing changed: ok" "-> calendar $PERSONAL: ok" "$(resync "$W" series_same)"
open_day "$T3"; dump_ui "$ROW_DIR/series_day.xml"
assert_eq "the views show that occurrence once (the copy's master and exception are hidden)" 1 "$(grep -o 'resource-id="cal_event_title:[0-9]*"' "$ROW_DIR/series_day.xml" | wc -l | tr -d ' ')"
work_holds_offsite "after the series' Sync"

# ---- the target read-only now (r3 D5): refused by the write layer's own re-read
adb shell "content update --uri '$CALS/$PERSONAL?$SA&account_name=$PERSONAL_ACCT&account_type=com.google' --bind calendar_access_level:i:200" < /dev/null
adb shell "content update --uri $EVENTS/$B --bind title:s:'E24 kept 2'" < /dev/null; sleep 1
assert_contains "Sync to a target that is read-only now" "-> calendar $PERSONAL: failed calendar read-only" "$(resync "$B" readonly)"
assert_eq "its notice" "That calendar is read-only now" "$(node_text "$ROW_DIR/resync_readonly.xml" cal_notice)"
assert_eq "the marker keeps a warning" yes "$(has_node "$ROW_DIR/resync_readonly.xml" cal_synced_warning)"
assert_contains "and nothing was written: the copy keeps its old title" "E24 kept|" "$(titles_in "$PERSONAL")|"
adb shell "content update --uri '$CALS/$PERSONAL?$SA&account_name=$PERSONAL_ACCT&account_type=com.google' --bind calendar_access_level:i:700" < /dev/null; sleep 1

# ---- the copy moved to another calendar on the other side (T16-12): stale, nothing written
CB="$(event_ids "calendar_id=$PERSONAL AND title='E24 kept'" | tr -d ' ')"
other_side "$PERSONAL_ACCT" update "$CB" "--bind calendar_id:i:$WORK"
record "the copy's calendar after the move" "$(event_rows calendar_id:title "_id=$CB" | sed 's/^Row: 0 //')"
assert_contains "Sync on a moved copy" "failed mapping stale" "$(resync "$B" stale)"
assert_contains "the moved copy was not written" "title=E24 kept" "$(event_rows calendar_id:title "_id=$CB")"
open_event "$B"; tap cal_event_action:delete 1.5; tap cal_delete_choice:both 2.5; dump_ui "$ROW_DIR/stale_delete.xml"
assert_eq "delete both on a moved copy writes nothing: the local event is still there" 1 "$(event_count "_id=$B AND deleted=0")"
assert_eq "and so is the copy" 1 "$(event_count "_id=$CB AND deleted=0")"
other_side "$WORK_ACCT" update "$CB" "--bind calendar_id:i:$PERSONAL"
work_holds_offsite "after the copy is moved back"

# ---- Personal removed from the phone
rmcal "$PERSONAL_ACCT" com.google; sleep 1
assert_contains "Sync when the calendar is gone" "failed calendar gone" "$(resync "$B" gone)"
assert_eq "its notice" "That calendar is no longer on this phone" "$(node_text "$ROW_DIR/resync_gone.xml" cal_notice)"
assert_eq "the marker keeps a warning glyph" yes "$(has_node "$ROW_DIR/resync_gone.xml" cal_synced_warning)"
work_holds_offsite "at the end"

# ---- restore
c6
cal_fixtures_down
purge_tessera_events "title LIKE 'E24 %'"
assert_eq "the account calendars are gone" "" "$(cal_id "$PERSONAL_ACCT")$(cal_id "$WORK_ACCT")"
assert_eq "Tessera holds what it held before the session" "$BEFORE" "$(event_count "calendar_id=$TESS AND deleted=0")"
open_cal -a android.intent.action.MAIN; sleep 2; c6
record "calendar_sync.json after the restore" "$(sync_json)"
ensure_start
session_end
