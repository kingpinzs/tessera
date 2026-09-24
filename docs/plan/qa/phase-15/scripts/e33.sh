#!/usr/bin/env bash
# E33 — The AlarmClock API (phase 15 T15-37; H25). An explicit SET_ALARM 6:45 "Gym" with SKIP_UI → dumpsys alarm's
# Next alarm clock holds 06:45, the Alarm tab lists "Gym" at 6:45 AM, the ring holds `[alarms] api … SET_ALARM from
# … -> created <id>`; without SKIP_UI → the editor opens filled in (`alarm_editor_field:name` "Gym") and nothing is
# armed until Save; SET_TIMER LENGTH 120 with SKIP_UI → a 2:00 timer running; SHOW_ALARMS / SHOW_TIMERS → Alarms &
# Clock resumed on clock_pivot:alarm / clock_pivot:timer; HOUR 25 → `refused: hour out of range` and nothing armed; a
# LENGTH past 99:59:59 → refused. The guard: dumpsys package shows the handler's permission, and the resolver lists
# both the handler and DeskClock (so an implicit request raises the chooser — recorded). Restore: the alarm and timer
# deleted through the app.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E33 "the AlarmClock API: set alarm / timer, show, refusals, the guard"
assert_clock_empty "baseline"
api() { adb shell am start -W "$@" -n "$API_ACT" >/dev/null 2>&1; sleep 1.5; }
SET_ALARM="-a android.intent.action.SET_ALARM"; SET_TIMER="-a android.intent.action.SET_TIMER"

# ---- SET_ALARM 6:45 Gym, SKIP_UI -------------------------------------------------------------------------------------------
MARK="$(ring_mark)"
api $SET_ALARM --ei android.intent.extra.alarm.HOUR 6 --ei android.intent.extra.alarm.MINUTES 45 --es android.intent.extra.alarm.MESSAGE Gym --ez android.intent.extra.alarm.SKIP_UI true
APILINE="$(ring_since "$MARK" | grep -F '[alarms] api android.intent.action.SET_ALARM')"; note "${APILINE#*] }"
assert_contains "the ring holds api SET_ALARM from … -> created <id>" "api android.intent.action.SET_ALARM from com.android.shell -> created " "$APILINE"
ID="$(printf '%s\n' "$APILINE" | grep -oE 'created [a-z0-9]+' | awk '{print $2}')"
EXPECT="$(next_wall_ms 6 45)"
assert_eq "dumpsys alarm's Next alarm clock holds 06:45" "$EXPECT" "$(next_alarm_clock_ms)"
assert_eq "… as the shell's RTC_WAKEUP entry" "$EXPECT" "$(alarm_trigger_ms | paste -sd,)"
open_clock alarm
dump_ui "$ROW_DIR/gym.xml"; screencap "$ROW_DIR/gym.png"
assert_eq "the Alarm tab lists Gym" "Gym" "$(node_text "$ROW_DIR/gym.xml" "alarm_name:$ID")"
assert_eq "… at 6:45 AM" "6:45 AM" "$(node_text "$ROW_DIR/gym.xml" "alarm_time:$ID")"

# ---- without SKIP_UI: the editor opens filled in, nothing armed until Save ----------------------------------------------------
MARK="$(ring_mark)"
api $SET_ALARM --ei android.intent.extra.alarm.HOUR 8 --ei android.intent.extra.alarm.MINUTES 15 --es android.intent.extra.alarm.MESSAGE Gym
assert_contains "the ring says the request opened the editor" "api android.intent.action.SET_ALARM from com.android.shell -> opened" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/editor_api.xml"; screencap "$ROW_DIR/editor_api.png"
assert_eq "the editor is open (NEW ALARM)" "NEW ALARM" "$(node_text "$ROW_DIR/editor_api.xml" alarm_editor_title)"
assert_eq "… filled in with the name Gym" "Gym" "$(node_text "$ROW_DIR/editor_api.xml" 'alarm_editor_field:name')"
assert_eq "… at 8" "8" "$(node_desc "$ROW_DIR/editor_api.xml" 'alarm_spinner:hour')"
assert_eq "… :15" "15" "$(node_desc "$ROW_DIR/editor_api.xml" 'alarm_spinner:minute')"
assert_eq "nothing new is armed before Save (still only the 6:45 alarm)" "$EXPECT" "$(alarm_trigger_ms | paste -sd,)"
assert_eq "… and the store still holds one alarm" "$ID" "$(alarm_ids | paste -sd,)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/editor_api.xml" "clock_bar:save"; sleep 2
ID2="$(alarm_ids | grep -v "^$ID$" | head -1)"
assert_ne "Save arms it (a second alarm in the store)" "" "$ID2"
assert_eq "… at 8:15" "8 15" "$(alarm_field "$ID2" hour) $(alarm_field "$ID2" minute)"
assert_eq "… and dumpsys alarm now holds two entries" 2 "$(alarm_trigger_ms | wc -l)"

# ---- SET_TIMER 120 s, SKIP_UI → a 2:00 timer running ----------------------------------------------------------------------------
MARK="$(ring_mark)"
EL0="$(device_elapsed_ms)"
api $SET_TIMER --ei android.intent.extra.alarm.LENGTH 120 --es android.intent.extra.alarm.MESSAGE Eggs --ez android.intent.extra.alarm.SKIP_UI true
TL="$(ring_since "$MARK" | grep -F '[alarms] api android.intent.action.SET_TIMER')"
assert_contains "the ring holds api SET_TIMER -> created <id>" "api android.intent.action.SET_TIMER from com.android.shell -> created " "$TL"
TID="$(printf '%s\n' "$TL" | grep -oE 'created [a-z0-9]+' | awk '{print $2}')"
assert_eq "the timer is RUNNING in the store" RUNNING "$(timer_field "$TID" state)"
assert_eq "… with a 2:00 length" 120000 "$(timer_field "$TID" lengthMs)"
assert_within "… armed on the elapsed clock 120 s ahead" $(( EL0 + 120000 )) "$(timer_trigger_elapsed | head -1)" 3000
open_clock timer
gdump "$ROW_DIR/timer_running.xml"; screencap "$ROW_DIR/timer_running.png"
REM="$(node_text "$ROW_DIR/timer_running.xml" "timer_remaining:$TID")"; note "timer_remaining: $REM"
assert_eq "the Timer tab shows it counting down under 2:00" yes "$(python3 -c 'import sys; v=int(sys.argv[1] or 0); print("yes" if 100000 <= v < 120000 else "no (%d)" % v)' "$(hms_to_ms "$REM")")"

# ---- SHOW_ALARMS / SHOW_TIMERS ---------------------------------------------------------------------------------------------------
adb shell input keyevent KEYCODE_HOME; sleep 1
api -a android.intent.action.SHOW_ALARMS
assert_contains "SHOW_ALARMS resumes Alarms & Clock" "clock.ClockActivity" "$(resumed)"
dump_ui "$ROW_DIR/show_alarms.xml"
assert_eq "… on the Alarm tab (clock_pivot:alarm selected)" "true" "$(grep -o '<node[^>]*resource-id="clock_pivot:alarm"[^>]*>' "$ROW_DIR/show_alarms.xml" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2)"
adb shell input keyevent KEYCODE_HOME; sleep 1
api -a android.intent.action.SHOW_TIMERS
assert_contains "SHOW_TIMERS resumes Alarms & Clock" "clock.ClockActivity" "$(resumed)"
gdump "$ROW_DIR/show_timers.xml"
assert_eq "… on the Timer tab (clock_pivot:timer selected)" "true" "$(grep -o '<node[^>]*resource-id="clock_pivot:timer"[^>]*>' "$ROW_DIR/show_timers.xml" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2)"

# ---- refusals ----------------------------------------------------------------------------------------------------------------------
adb shell input keyevent KEYCODE_HOME; sleep 1
N0="$(alarm_trigger_ms | wc -l)"
MARK="$(ring_mark)"
api $SET_ALARM --ei android.intent.extra.alarm.HOUR 25 --ei android.intent.extra.alarm.MINUTES 0 --es android.intent.extra.alarm.MESSAGE Bad --ez android.intent.extra.alarm.SKIP_UI true
assert_contains "HOUR 25 -> refused: hour out of range" "api android.intent.action.SET_ALARM from com.android.shell -> refused: hour out of range" "$(ring_since "$MARK")"
assert_eq "… and nothing armed" "$N0" "$(alarm_trigger_ms | wc -l)"
assert_eq "… nothing added to the store" "2" "$(alarm_ids | wc -l)"
MARK="$(ring_mark)"
api $SET_TIMER --ei android.intent.extra.alarm.LENGTH 360000 --es android.intent.extra.alarm.MESSAGE Long --ez android.intent.extra.alarm.SKIP_UI true
assert_contains "LENGTH past 99:59:59 -> refused" "api android.intent.action.SET_TIMER from com.android.shell -> refused: length past 99:59:59" "$(ring_since "$MARK")"
assert_eq "… and only the one timer exists" "$TID" "$(timer_ids | paste -sd,)"

# ---- the guard ----------------------------------------------------------------------------------------------------------------------
# `dumpsys package` prints the resolver table without component permissions; the handler's ActivityInfo from
# `cmd package query-activities` carries `permission=` (run 1), and that is the package manager's own record of it.
RESOLVE="$(adb shell cmd package query-activities -a android.intent.action.SET_ALARM | tr -d '\r')"
printf '%s\n' "$RESOLVE" > "$ROW_DIR/query_activities.txt"
HANDLER_BLOCK="$(printf '%s\n' "$RESOLVE" | python3 -c '
import sys
blocks = sys.stdin.read().split("Activity #")
print(next((b for b in blocks if "name=app.tileshell.clock.AlarmApiActivity" in b), ""))')"
assert_ne "the resolver lists the handler (ActivityInfo name=app.tileshell.clock.AlarmApiActivity)" "" "$HANDLER_BLOCK"
assert_contains "… guarded by permission=com.android.alarm.permission.SET_ALARM" "permission=com.android.alarm.permission.SET_ALARM" "$HANDLER_BLOCK"
assert_contains "… and DeskClock" "com.android.deskclock" "$RESOLVE"
record "an implicit SET_ALARM would raise Android's chooser (two handlers; the P2 seam H25 judges)" "$(printf '%s\n' "$RESOLVE" | grep -cE 'AlarmApiActivity|deskclock') handlers"

# ---- restore ---------------------------------------------------------------------------------------------------------------------------
app_delete_alarm "$ID"; app_delete_alarm "$ID2"; app_delete_timer "$TID"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end
