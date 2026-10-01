#!/usr/bin/env bash
# Development proof of E4's core: an event made in the editor is read back from the provider (in Tessera, with the
# device zone and its location), and a driver insert appears in the open Day view with no restart — the first `view day`
# line with one more instance comes within 2000 ms of the MARK (the ContentObserver). Delete from the app removes the
# provider row; a driver delete leaves the view. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E4_EVENTS "an event from the editor, and the provider followed with no restart"
c6
TZ_ID="$(S getprop persist.sys.timezone)"
TODAY="$(device_date 0)"; TOMORROW="$(device_date 1)"
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; assert_ne "Tessera exists" "" "$TESS"
BEFORE="$(event_count "calendar_id=$TESS")"

# ---- the editor: Standup, tomorrow 09:00–10:00, Room 2
tap "cal_strip_day:$TOMORROW"
tap cal_bar:new 2
dump_ui "$ROW_DIR/editor_new.xml"
assert_eq "the editor opened" yes "$(has_node "$ROW_DIR/editor_new.xml" cal_editor)"
assert_eq "its calendar field reads Tessera" "Tessera" "$(field_text "$ROW_DIR/editor_new.xml" calendar)"
type_field title "Standup"
type_field location "Room 2"
set_time start_time 9 00 AM
dump_ui "$ROW_DIR/editor_filled.xml"; screencap "$ROW_DIR/editor_filled.png"
assert_eq "title typed" "Standup" "$(node_text "$ROW_DIR/editor_filled.xml" cal_editor_field:title)"
assert_eq "start time" "9:00 AM" "$(field_text "$ROW_DIR/editor_filled.xml" start_time)"
assert_eq "the end keeps its hour from the start" "10:00 AM" "$(field_text "$ROW_DIR/editor_filled.xml" end_time)"
assert_eq "nothing is saved before Save" 0 "$(event_count "title='Standup' AND calendar_id=$TESS")"
MARK="$(ring_mark)"
tap cal_editor_save 2
ROWS="$(event_rows title:dtstart:dtend:calendar_id:eventTimezone:eventLocation "title='Standup'")"; log "provider: $ROWS"
START="$(day_ms 1 09:00)"
assert_contains "the event is in Tessera" "calendar_id=$TESS," "$ROWS"
assert_contains "at tomorrow 09:00 in the device zone" "dtstart=$START, dtend=$(( START + 3600000 ))" "$ROWS"
assert_contains "with the device zone" "eventTimezone=$TZ_ID" "$ROWS"
assert_contains "and its location" "eventLocation=Room 2" "$ROWS"
STANDUP="$(event_ids "title='Standup'" | tr -d ' ')"
assert_contains "the write line names the event" "[calendar] write insert event=$STANDUP: ok" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/after_save.xml"
assert_eq "the editor closed onto the views" "no yes" "$(has_node "$ROW_DIR/after_save.xml" cal_editor) $(has_node "$ROW_DIR/after_save.xml" cal_view)"
assert_eq "the agenda lists it" "Standup" "$(node_text "$ROW_DIR/after_save.xml" "cal_event_title:$STANDUP")"

# ---- the Day view follows a driver insert with no restart
tap cal_bar:today; tap cal_bar:view; tap cal_view_pick:day 2
N0="$(ring_since "$ROW_MARK" | grep -F '[calendar] view day' | tail -1 | sed -n 's/.*: \([0-9]*\) instances.*/\1/p')"
MARK="$(ring_mark)"
DENTIST="$(mkevent "$TESS" Dentist "$(day_ms 0 14:00)" "$(day_ms 0 15:00)")"
sleep 2
python3 - "$MARK" "$N0" > "$ROW_DIR/observer.txt" <<PY
import re, sys
mark, n0 = int(sys.argv[1]), int(sys.argv[2])
out = "none"
for line in """$(ring_since "$MARK" | grep -F '[calendar] view day')""".splitlines():
    m = re.search(r'wall=(\d+).*: (\d+) instances in (\d+) ms', line)
    if m and int(m.group(2)) == n0 + 1:
        out = "%d ms after the MARK (n %d -> %d, in %s ms)" % (int(m.group(1)) - mark, n0, n0 + 1, m.group(3)); print(int(m.group(1)) - mark); break
print(out)
PY
log "observer: $(tail -1 "$ROW_DIR/observer.txt")"
assert_within "the next view line with one more instance is within 2000 ms of the insert's MARK" 1000 "$(head -1 "$ROW_DIR/observer.txt")" 1000
dump_ui "$ROW_DIR/day_dentist.xml"
assert_eq "the Day view lists Dentist with no restart" "Dentist" "$(node_text "$ROW_DIR/day_dentist.xml" "cal_event_title:$DENTIST")"

# ---- delete from the app → the provider row is gone
tap "cal_event:$DENTIST" 2
MARK="$(ring_mark)"
tap cal_event_action:delete 2
assert_eq "Dentist's provider row is gone" 0 "$(event_count "_id=$DENTIST")"
assert_contains "the delete's write line" "[calendar] write delete event=$DENTIST: ok" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/day_after_delete.xml"
assert_eq "its page closed and its node left the view" "no no" "$(has_node "$ROW_DIR/day_after_delete.xml" "cal_event_page:$DENTIST") $(has_node "$ROW_DIR/day_after_delete.xml" "cal_event:$DENTIST")"

# ---- a driver delete leaves the open view
tap cal_bar:view; tap cal_view_pick:agenda 2
dump_ui "$ROW_DIR/agenda_before.xml"
MARK="$(ring_mark)"
rmevents "_id=$STANDUP"; sleep 2
dump_ui "$ROW_DIR/agenda_after.xml"
assert_eq "Standup's node is gone after a driver delete" "no" "$(has_node "$ROW_DIR/agenda_after.xml" "cal_event:$STANDUP")"
assert_contains "a view line followed the delete" "[calendar] view agenda" "$(ring_since "$MARK")"

# ---- restore
assert_eq "Tessera holds what it held before the session" "$BEFORE" "$(event_count "calendar_id=$TESS")"
c6; ensure_start
session_end
