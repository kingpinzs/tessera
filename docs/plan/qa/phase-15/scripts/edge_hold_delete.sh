#!/usr/bin/env bash
# EDGE_HOLD_DELETE — a hold on an alarm row or a timer block opens its menu with Delete (the owner's ruling 2026-09-25,
# after "I cant delete a timer": the Select route existed but a hold, the way cities and recordings are removed, did
# nothing). Also: a tap on an alarm row still opens its editor, a timer's own buttons still work, and in Select a hold
# opens no menu while a tap still checks. No microphone is used.
# Restore: every alarm and timer the row made is deleted; the stores end as they began; Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"
row_begin EDGE_HOLD_DELETE "a hold on an alarm or a timer offers Delete; taps, buttons and Select unchanged"

hold() { adb shell input swipe "$1" "$2" "$1" "$2" 1300; sleep 1; } # x y: a still press past the 783-ms hold
centre_of() { set -- $(bounds "$1" "$2"); [ -n "${4:-}" ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
count() { python3 -c 'import json,sys; d=sys.stdin.read().strip(); print(len(json.loads(d)) if d else 0)'; }

A0="$(store_alarms | count)"; T0="$(store_timers | count)"; note "at the start: $A0 alarm(s), $T0 timer(s)"
AID="$(api_alarm 6 45 HoldProbe)"; TID="$(api_timer 900 HoldProbe)"
assert_ne "an alarm was made for the row" "" "$AID"
assert_ne "a timer was made for the row" "" "$TID"

# ---- alarms: a tap still edits, a hold offers Delete
open_clock alarm
for _ in 1 2 3; do
  dump_ui "$ROW_DIR/alarms.xml"
  [ "$(has_node "$ROW_DIR/alarms.xml" "alarm_row:$AID")" = yes ] && break
  adb shell input keyevent KEYCODE_BACK; sleep 1; open_clock alarm
done
read -r X Y <<< "$(centre_of "$ROW_DIR/alarms.xml" "alarm_row:$AID")"
adb shell input tap "$X" "$Y"; sleep 1.5
dump_ui "$ROW_DIR/alarm_tap.xml"
assert_eq "a tap on an alarm row still opens its editor" yes "$(has_node "$ROW_DIR/alarm_tap.xml" alarm_editor_title)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
open_clock alarm; dump_ui "$ROW_DIR/alarms2.xml"
read -r X Y <<< "$(centre_of "$ROW_DIR/alarms2.xml" "alarm_row:$AID")"
MARK="$(ring_mark)"
hold "$X" "$Y"
dump_ui "$ROW_DIR/alarm_menu.xml"; screencap "$ROW_DIR/alarm_menu.png"
assert_eq "a hold on an alarm row opens its menu" yes "$(has_node "$ROW_DIR/alarm_menu.xml" alarm_row_menu_scrim)"
assert_eq "… offering Delete" yes "$(has_node "$ROW_DIR/alarm_menu.xml" "alarm_delete:$AID")"
assert_eq "… and the hold did not open the editor" no "$(has_node "$ROW_DIR/alarm_menu.xml" alarm_editor_title)"
tap_node "$ROW_DIR/alarm_menu.xml" "alarm_delete:$AID"; sleep 1.5
ring_since "$MARK" > "$ROW_DIR/ring_hold_alarm_launcher.txt"
assert_eq "Delete removes the alarm from the store" no "$(store_alarms | grep -q "\"$AID\"" && echo yes || echo no)"
assert_contains "… and the ring says so" "hold delete alarm $AID" "$(cat "$ROW_DIR/ring_hold_alarm_launcher.txt")"
assert_eq "… and it is gone from the list" no "$(dump_ui "$ROW_DIR/alarms3.xml"; has_node "$ROW_DIR/alarms3.xml" "alarm_row:$AID")"

# ---- timers: the play button still works, a hold offers Delete, Select is unchanged
open_clock timer; dump_ui "$ROW_DIR/timers.xml"
STATE0="$(store_timers | python3 -c "import json,sys; print(next(t['state'] for t in json.load(sys.stdin) if t['id']=='$TID'))")"
tap_node "$ROW_DIR/timers.xml" "timer_play:$TID"; sleep 1
STATE1="$(store_timers | python3 -c "import json,sys; print(next(t['state'] for t in json.load(sys.stdin) if t['id']=='$TID'))")"
assert_ne "the timer's play / pause button still works (state $STATE0 -> $STATE1)" "$STATE0" "$STATE1"
dump_ui "$ROW_DIR/timers_select0.xml"
tap_node "$ROW_DIR/timers_select0.xml" timer_select; sleep 1
dump_ui "$ROW_DIR/timers_select.xml"
read -r X Y <<< "$(centre_of "$ROW_DIR/timers_select.xml" "timer_block:$TID")"
hold "$X" "$(( Y + 150 ))"
dump_ui "$ROW_DIR/timers_select_hold.xml"
assert_eq "in Select a hold opens no menu" no "$(has_node "$ROW_DIR/timers_select_hold.xml" timer_row_menu_scrim)"
# In Select any press on the block toggles its check (the hold above did too), so a tap must flip Delete's state.
del_enabled() { grep -o '<node[^>]*timer_select_delete[^>]*>' "$1" | grep -o 'enabled="[a-z]*"'; }
BEFORE="$(del_enabled "$ROW_DIR/timers_select_hold.xml")"
adb shell input tap "$X" "$(( Y + 150 ))"; sleep 1
dump_ui "$ROW_DIR/timers_select_tap.xml"
assert_ne "… and a tap still checks or unchecks the block (Delete was $BEFORE)" "$BEFORE" "$(del_enabled "$ROW_DIR/timers_select_tap.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/timers2.xml"
assert_eq "Back leaves Select with the timer kept" yes "$(has_node "$ROW_DIR/timers2.xml" "timer_block:$TID")"
dump_ui "$ROW_DIR/timers3.xml"
# A hold on the timer's name, whose tap edits: the menu opens and the editor does NOT.
read -r X Y <<< "$(centre_of "$ROW_DIR/timers3.xml" "timer_block:$TID")"
MARK="$(ring_mark)"
hold "$X" "$(( Y + 150 ))"
dump_ui "$ROW_DIR/timer_menu.xml"; screencap "$ROW_DIR/timer_menu.png"
assert_eq "a hold on a timer's name opens its menu" yes "$(has_node "$ROW_DIR/timer_menu.xml" timer_row_menu_scrim)"
assert_eq "… and not the timer editor" no "$(has_node "$ROW_DIR/timer_menu.xml" timer_editor_title)"
assert_eq "… offering Delete" yes "$(has_node "$ROW_DIR/timer_menu.xml" "timer_delete:$TID")"
tap_node "$ROW_DIR/timer_menu.xml" "timer_delete:$TID"; sleep 1.5
ring_since "$MARK" > "$ROW_DIR/ring_hold_timer_launcher.txt"
assert_eq "Delete removes the timer from the store" no "$(store_timers | grep -q "\"$TID\"" && echo yes || echo no)"
assert_contains "… and the ring says so" "hold delete timer $TID" "$(cat "$ROW_DIR/ring_hold_timer_launcher.txt")"

# ---- restore
assert_eq "restore: the alarm count is as it began" "$A0" "$(store_alarms | count)"
assert_eq "restore: the timer count is as it began" "$T0" "$(store_timers | count)"
adb shell input keyevent KEYCODE_HOME
row_end
