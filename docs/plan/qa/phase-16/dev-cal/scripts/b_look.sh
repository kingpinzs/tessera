#!/usr/bin/env bash
# Development look at the Calendar app's pages (build task 4): opens each view, panel and page once, keeps a dump and a
# screenshot of each, and asserts the tags the acceptance rows read exist. Fixtures: three events in Tessera made
# through the provider (an all-day, a timed, a free-outlined), deleted at the end. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin B_LOOK "the views, panels and pages carry their tags"
CRASH0="$(S dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c '^Process: app.tileshell$')"
shot() { dump_ui "$ROW_DIR/$1.xml"; screencap "$ROW_DIR/$1.png"; }
TODAY="$(S date +%Y-%m-%d)"
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN
sleep 2
TESS="$(cal_id Tessera)"
assert_ne "Tessera exists once Calendar has opened" "" "$TESS"
A_ID="$(mkevent "$TESS" 'Look all day' "$(utc_day_ms 0)" "$(utc_day_ms 1)" --bind allDay:i:1 --bind eventTimezone:s:UTC)"
B_ID="$(mkevent "$TESS" 'Look timed' "$(day_ms 0 13:00)" "$(day_ms 0 14:30)" "--bind eventLocation:s:'Room 2'")"
C_ID="$(mkevent "$TESS" 'Look free' "$(day_ms 0 15:00)" "$(day_ms 0 16:00)" --bind availability:i:1)"
note "fixtures: all-day $A_ID timed $B_ID free $C_ID in Tessera $TESS"
sleep 2
shot agenda
D="$ROW_DIR/agenda.xml"
assert_eq "cal_view" yes "$(has_node "$D" cal_view)"
assert_eq "cal_view_mode:agenda selected" true "$(node_attr "$D" cal_view_mode:agenda selected)"
assert_eq "cal_strip_day:$TODAY selected" true "$(node_attr "$D" "cal_strip_day:$TODAY" selected)"
assert_eq "cal_day:$TODAY heading" yes "$(has_node "$D" "cal_day:$TODAY")"
for id in $A_ID $B_ID $C_ID; do
  assert_eq "cal_event:$id" yes "$(has_node "$D" "cal_event:$id")"
  assert_eq "cal_event_bar:$id" yes "$(has_node "$D" "cal_event_bar:$id")"
done
assert_eq "cal_event_title:$B_ID text" "Look timed" "$(node_text "$D" "cal_event_title:$B_ID")"
assert_eq "an all-day row has no cal_event_time" no "$(has_node "$D" "cal_event_time:$A_ID")"
assert_eq "a timed row has cal_event_time" yes "$(has_node "$D" "cal_event_time:$B_ID")"
log "agenda time label: $(node_text "$D" "cal_event_time:$B_ID")"
log "view line: $(line_of "$(ring_since "$MARK")" '[calendar] view agenda')"
log "calendars line: $(line_of "$(ring_since "$MARK")" '[calendar] calendars:')"
assert_eq "app bar buttons" "yes yes yes yes" "$(has_node "$D" cal_bar:today) $(has_node "$D" cal_bar:new) $(has_node "$D" cal_bar:view) $(has_node "$D" cal_bar:more)"

tap cal_bar:view; shot view_menu
assert_eq "View list: Agenda / Day / Week" "yes yes yes" "$(has_node "$ROW_DIR/view_menu.xml" cal_view_pick:agenda) $(has_node "$ROW_DIR/view_menu.xml" cal_view_pick:day) $(has_node "$ROW_DIR/view_menu.xml" cal_view_pick:week)"
tap cal_view_pick:day 2; shot day
D="$ROW_DIR/day.xml"
assert_eq "cal_view_mode:day selected" true "$(node_attr "$D" cal_view_mode:day selected)"
assert_eq "cal_allday:$TODAY" yes "$(has_node "$D" "cal_allday:$TODAY")"
assert_eq "the all-day event has no cal_event_time in the Day view" no "$(has_node "$D" "cal_event_time:$A_ID")"
assert_eq "the timed event's time" yes "$(has_node "$D" "cal_event_time:$B_ID")"
log "day time text: $(node_text "$D" "cal_event_time:$B_ID")"
python3 - "$D" "$A_ID" "$TODAY" > "$ROW_DIR/allday_inside.txt" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(rid):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(rid), xml)
    return [int(x) for x in m.groups()] if m else None
band, ev = b("cal_allday:" + sys.argv[3]), b("cal_event:" + sys.argv[2])
print("yes" if band and ev and band[0] <= ev[0] and band[1] <= ev[1] and ev[2] <= band[2] and ev[3] <= band[3] else "no", band, ev)
PY
assert_contains "the all-day event's node lies inside the all-day band" "yes" "$(cat "$ROW_DIR/allday_inside.txt")"

tap cal_bar:view; tap cal_view_pick:week 2; shot week
D="$ROW_DIR/week.xml"
assert_eq "cal_view_mode:week selected" true "$(node_attr "$D" cal_view_mode:week selected)"
assert_eq "week cell cal_day:$TODAY" yes "$(has_node "$D" "cal_day:$TODAY")"
assert_eq "week event line" "Look timed" "$(node_text "$D" "cal_event_title:$B_ID")"

tap cal_bar:view; tap cal_view_pick:agenda 2
MARK="$(ring_mark)"
tap cal_header 1.5; shot month
D="$ROW_DIR/month.xml"
assert_eq "cal_month_dropdown" yes "$(has_node "$D" cal_month_dropdown)"
assert_eq "cal_month_cell:$TODAY" yes "$(has_node "$D" "cal_month_cell:$TODAY")"
assert_eq "42 month cells" 42 "$(count_prefix "$D" cal_month_cell:)"
log "motion: $(line_of "$(ring_since "$MARK")" '[motion] cal_month_dropdown')"
assert_contains "the drop-down's motion line" "[motion] cal_month_dropdown" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 1

MARK="$(ring_mark)"
tap cal_menu 1.5; shot pane
D="$ROW_DIR/pane.xml"
assert_eq "cal_pane" yes "$(has_node "$D" cal_pane)"
assert_eq "cal_account:Tessera" yes "$(has_node "$D" cal_account:Tessera)"
assert_eq "cal_calendar_row:$TESS checked" true "$(node_attr "$D" "cal_calendar_row:$TESS" checked)"
sleep 1
assert_contains "the counts line, written when the pane opens" "[calendar] counts: Tessera/Tessera=" "$(ring_since "$MARK")"
log "counts: $(line_of "$(ring_since "$MARK")" '[calendar] counts')"
adb shell input keyevent KEYCODE_BACK; sleep 1

tap "cal_event:$B_ID" 2; shot event
D="$ROW_DIR/event.xml"
assert_eq "cal_event_page:$B_ID" yes "$(has_node "$D" "cal_event_page:$B_ID")"
assert_eq "local event actions" "yes yes yes" "$(has_node "$D" cal_event_action:edit) $(has_node "$D" cal_event_action:delete) $(has_node "$D" cal_event_action:sync)"
log "event page time: $(node_text "$D" "cal_event_time:$B_ID")"
tap cal_event_action:edit 2; shot editor
D="$ROW_DIR/editor.xml"
assert_eq "cal_editor" yes "$(has_node "$D" cal_editor)"
for f in title location all_day start_date start_time end_date end_time repeat reminder calendar; do
  assert_eq "cal_editor_field:$f" yes "$(has_node "$D" "cal_editor_field:$f")"
done
assert_eq "the editor's calendar field reads Tessera" "Tessera" "$(python3 - "$D" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'<node[^>]*text="([^"]*)"[^>]*/>\s*</node>', xml[xml.find('resource-id="cal_editor_field:calendar"') - 400:xml.find('resource-id="cal_editor_field:calendar"') + 800])
i = xml.find('resource-id="cal_editor_field:calendar"')
seg = xml[i:i + 700]
t = re.findall(r'text="([^"]+)"', seg)
print(t[0] if t else "")
PY
)"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
tap cal_bar:more 1.5; shot more_menu
assert_eq "… menu: Settings" yes "$(has_node "$ROW_DIR/more_menu.xml" cal_more:settings)"
tap cal_more:settings 2; shot settings
assert_eq "cal_settings" yes "$(has_node "$ROW_DIR/settings.xml" cal_settings)"
tap cal_settings_open_can_sync 2; shot can_sync
assert_eq "cal_can_sync with its notice" "yes Choose which calendars Sync may use" "$(has_node "$ROW_DIR/can_sync.xml" cal_can_sync) $(node_text "$ROW_DIR/can_sync.xml" cal_notice)"

# ---- restore
rmevents "_id IN ($A_ID,$B_ID,$C_ID)"
assert_eq "the fixtures are gone" 0 "$(event_count "_id IN ($A_ID,$B_ID,$C_ID)")"
assert_eq "no new crash of the shell" "$CRASH0" "$(S dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c '^Process: app.tileshell$')"
c6; ensure_start
session_end
