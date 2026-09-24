#!/usr/bin/env bash
# EDGE_TIMERS — the phase 15 Timer edge cases: the process dies at the deadline instant (the alarm fires into a dead
# process → ReminderReceiver starts the ring service); 10 timers running (each armed, each listed, each counting);
# a timer set to 0:00 refused (the editor's Save stays dim and nothing is created; the API refuses LENGTH 0); a timer
# paused then the clock jumped (a paused timer holds its remaining, not a deadline). Restore: every timer deleted
# through the app, RV12's clock restore, force-stop + Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin EDGE_TIMERS "timers: dead process at the deadline; 10 at once; 0:00 refused; paused then clock jump"
assert_clock_empty "baseline"
dismiss_any_ring

# ---- the process dies at the deadline instant ------------------------------------------------------------------------------------
TID="$(api_timer 20 "Dead")"
assert_ne "a 20-s timer is running" "" "$TID"
adb shell input keyevent KEYCODE_HOME; sleep 1
MARK="$(ring_mark)"
PID0="$(kill9_shell)"; note "killed $PID0 with ~15 s left"
# The home app is relaunched by the system at once; whichever process the alarm lands in, the receiver starts the ring.
FIRED="$(wait_ring "$MARK" "[alarms] fired $TID kind=timer" 40)"
assert_ne "the timer fired after the kill (the receiver started the ring service)" "" "$FIRED"
assert_within "… on time (late <= 1500 ms; a cold process)" 0 "$(field_of "$FIRED" late)" 1500
sleep 3
assert_ne "… and it rings (an ALARM player of the shell)" 0 "$(alarm_player_started)"
assert_ne "the process that rings is a new one" "$PID0" "$(adb shell pidof app.tileshell | tr -d '\r')"
gdump "$ROW_DIR/dead_ring.xml"; screencap "$ROW_DIR/dead_ring.png"
gtap "$ROW_DIR/dead_ring.xml" ring_dismiss; sleep 2
assert_eq "dismissed" 0 "$(alarm_player_started)"
app_delete_timer "$TID"
sleep 2; shell_notifications > "$ROW_DIR/notifications_after_dead.txt"
record "clock_timers notifications left after the Dead timer rang and was deleted (title|when)" "$(grep -c 'channel=clock_timers' "$ROW_DIR/notifications_after_dead.txt") — $(python3 -c '
import re, sys
for b in open(sys.argv[1]).read().split("----"):
    if "channel=clock_timers" in b:
        t = re.search(r"android.title=[^\n]*", b); w = re.search(r"when=(-?\d+)", b)
        print("%s|%s" % (t.group(0)[:60] if t else "no title", w.group(1) if w else "?"))' "$ROW_DIR/notifications_after_dead.txt" | paste -sd' ')"

# ---- 10 timers running ----------------------------------------------------------------------------------------------------------------
IDS=""
for i in $(seq 1 10); do IDS="$IDS $(api_timer $(( 1800 + i * 60 )) "T$i")"; done
note "ten timers: $IDS"
assert_eq "ten timers were created" 10 "$(echo $IDS | wc -w)"
assert_eq "all ten are RUNNING in the store" 10 "$(store_timers | python3 -c 'import json,sys; print(sum(1 for t in json.load(sys.stdin) if t["state"]=="RUNNING"))')"
assert_eq "all ten are armed in AlarmManager (ELAPSED_WAKEUP entries)" 10 "$(timer_trigger_elapsed | wc -l)"
assert_eq "… and none is the phone's next alarm clock" "" "$(next_alarm_clock_ms)"
open_clock timer
gdump "$ROW_DIR/ten_a.xml"; screencap "$ROW_DIR/ten.png"
sleep 2
gdump "$ROW_DIR/ten_b.xml"
SEEN=0; COUNTING=0
for id in $IDS; do
  gscroll_to_node "$ROW_DIR/ten_a.xml" "timer_remaining:$id" 6 >/dev/null 2>&1 || continue
  A="$(node_text "$ROW_DIR/ten_a.xml" "timer_remaining:$id")"; sleep 1.2; gdump "$ROW_DIR/ten_b.xml"
  B="$(node_text "$ROW_DIR/ten_b.xml" "timer_remaining:$id")"
  SEEN=$((SEEN + 1)); [ -n "$A" ] && [ "$A" != "$B" ] && COUNTING=$((COUNTING + 1))
done
assert_eq "every one of the ten is listed on the Timer tab" 10 "$SEEN"
assert_eq "… and every one is counting down" 10 "$COUNTING"
NOTIFS="$(shell_notifications | grep -c 'channel=clock_timers')"
note "running-timer notifications (title | when): $(shell_notifications | python3 -c '
import re, sys
for b in sys.stdin.read().split("----"):
    if "channel=clock_timers" in b:
        t = re.search(r"android.title=String \(([^)]*)\)", b); w = re.search(r"when=(-?\d+)", b)
        print("%s|%s" % (t.group(1) if t else "?", w.group(1) if w else "?"))' | paste -sd' ')"
shell_notifications > "$ROW_DIR/notifications_ten.txt"   # every record, for the 11th one runs 1–3 counted (its when= is the Dead timer's deadline)
assert_eq "ten running-timer notifications are posted" 10 "$NOTIFS"
app_delete_all
assert_eq "the ten are gone" "" "$(timer_ids | paste -sd,)"

# ---- 0:00 refused -------------------------------------------------------------------------------------------------------------------
MARK="$(ring_mark)"
adb shell am start -W -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 0 --es android.intent.extra.alarm.MESSAGE Zero --ez android.intent.extra.alarm.SKIP_UI true -n "$API_ACT" >/dev/null 2>&1; sleep 1.5
assert_contains "the API refuses LENGTH 0" "api android.intent.action.SET_TIMER from com.android.shell -> refused: length out of range" "$(ring_since "$MARK")"
assert_eq "… and created nothing" "" "$(timer_ids | paste -sd,)"
open_clock timer
dump_ui "$ROW_DIR/zero_list.xml"
tap_node "$ROW_DIR/zero_list.xml" 'clock_bar:add'; sleep 1.5
dump_ui "$ROW_DIR/zero_editor.xml"
assert_eq "the editor opens at 00:05:00" "00 05 00" "$(node_desc "$ROW_DIR/zero_editor.xml" 'timer_editor_field:hours') $(node_desc "$ROW_DIR/zero_editor.xml" 'timer_editor_field:minutes') $(node_desc "$ROW_DIR/zero_editor.xml" 'timer_editor_field:seconds')"
spin_to 'timer_editor_field:minutes' "00" "$(seq -f %02g 0 59 | paste -sd,)"
dump_ui "$ROW_DIR/zero_set.xml"; screencap "$ROW_DIR/zero_set.png"
assert_eq "the minutes read 00 (a 0:00:00 length)" "00" "$(node_desc "$ROW_DIR/zero_set.xml" 'timer_editor_field:minutes')"
SAVE="$(grep -o '<node[^>]*resource-id="clock_bar:save"[^>]*>' "$ROW_DIR/zero_set.xml" | grep -o 'enabled="[a-z]*"' | cut -d'"' -f2)"
record "the Save button's enabled flag at 0:00:00 (the build dims it; TimerTab.kt:198)" "$SAVE"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/zero_set.xml" 'clock_bar:save'; sleep 1.5
assert_eq "Save at 0:00:00 creates nothing" "" "$(timer_ids | paste -sd,)"
assert_absent "… and logs no timer created" "timer created" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/zero_after.xml"
assert_eq "… the editor is still open (Save refused)" "NEW TIMER" "$(node_text "$ROW_DIR/zero_after.xml" timer_editor_title)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- paused, then the clock jumped ----------------------------------------------------------------------------------------------------
TID3="$(api_timer 600 "Paused")"
assert_ne "a 10-min timer is running" "" "$TID3"
open_clock timer
sleep 3
gdump "$ROW_DIR/pause_a.xml"
gtap "$ROW_DIR/pause_a.xml" "timer_play:$TID3"; sleep 1.5
assert_eq "paused in the store" PAUSED "$(timer_field "$TID3" state)"
REM0="$(timer_field "$TID3" remainingMs)"
assert_eq "a paused timer holds no deadline" "null null" "$(timer_field "$TID3" deadlineElapsedMs) $(timer_field "$TID3" deadlineWallMs)"
assert_eq "… and no alarm is armed for it" "" "$(timer_trigger_elapsed | paste -sd,)"
gdump "$ROW_DIR/pause_b.xml"
SHOWN0="$(node_text "$ROW_DIR/pause_b.xml" "timer_remaining:$TID3")"
NOW="$(device_ms)"
jump_clock $(( NOW + 3600000 )) >/dev/null
sleep 2
assert_eq "after a +1 h clock jump it is still PAUSED" PAUSED "$(timer_field "$TID3" state)"
assert_eq "… holding the same remaining" "$REM0" "$(timer_field "$TID3" remainingMs)"
open_clock timer
gdump "$ROW_DIR/pause_c.xml"; screencap "$ROW_DIR/pause_c.png"
assert_eq "… and the tab shows the same digits" "$SHOWN0" "$(node_text "$ROW_DIR/pause_c.xml" "timer_remaining:$TID3")"
assert_eq "… with nothing armed" "" "$(timer_trigger_elapsed | paste -sd,)"
gtap "$ROW_DIR/pause_c.xml" "timer_play:$TID3"; sleep 1.5
assert_eq "resumed: RUNNING again" RUNNING "$(timer_field "$TID3" state)"
assert_within "resumed: armed for its remaining on the elapsed clock" $(( $(device_elapsed_ms) + REM0 )) "$(timer_trigger_elapsed | head -1)" 3000

# ---- restore -------------------------------------------------------------------------------------------------------------------------
ring_save launcher
clock_restore
app_delete_all
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 2
assert_clock_empty "restore"
row_end
