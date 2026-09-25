#!/usr/bin/env bash
# E3 — Alarm armed (phase 15). An alarm at now + 3 min created THROUGH THE EDITOR (taps at dump bounds, the
# time picker rolled by swipes inside its loop bounds) → `dumpsys alarm` lists an RTC_WAKEUP alarm for
# app.tileshell whose "Alarm clock" trigger is that instant and whose "Next alarm clock" line shows it for user 0
# (setAlarmClock); the ring slice holds `[alarms] rearm (store change): 1 alarms, 0 timers, exact=true`; the
# toggle off → both gone; on again → back at the same instant. A one-shot alarm at a time already past today
# (seeded through the AlarmClock API — the row's subject there is the arming rule, not the editor) arms for
# tomorrow: the Next alarm clock is tomorrow at that time and the row's `alarm_repeat:<id>` reads "Tomorrow"
# (T15-57). Restore: both alarms deleted through the app, the baseline re-asserted.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E3 "alarm armed through the editor; toggle off / on; a past time arms for tomorrow"
record_fsi
assert_clock_empty "baseline"
dismiss_any_ring

# ---- the editor ----------------------------------------------------------------------------------------------
open_clock alarm
dump_ui "$ROW_DIR/list_empty.xml"
assert_eq "the Alarm tab starts empty (alarm_empty)" "No alarms" "$(node_text "$ROW_DIR/list_empty.xml" alarm_empty)"
tap_node "$ROW_DIR/list_empty.xml" "clock_bar:add"; sleep 1.5
dump_ui "$ROW_DIR/editor_new.xml"; screencap "$ROW_DIR/editor_new.png"
assert_eq "Add opens the editor titled NEW ALARM" "NEW ALARM" "$(node_text "$ROW_DIR/editor_new.xml" alarm_editor_title)"
assert_eq "a new alarm's spinner starts at 7" "7" "$(node_desc "$ROW_DIR/editor_new.xml" 'alarm_spinner:hour')"
assert_eq "… :00" "00" "$(node_desc "$ROW_DIR/editor_new.xml" 'alarm_spinner:minute')"
assert_eq "… AM" "AM" "$(node_desc "$ROW_DIR/editor_new.xml" 'alarm_spinner:ampm')"

# The target: now + 3 min on the device's clock, rounded to the minute it lands in.
NOW="$(device_ms)"
TARGET_MS=$(( (NOW + 180000) / 60000 * 60000 ))
read -r TH TM <<< "$(device_hm "$TARGET_MS")"
H12=$(( TH % 12 )); [ "$H12" = 0 ] && H12=12
AMPM=AM; [ "$TH" -ge 12 ] && AMPM=PM
note "target $(date -d @$((TARGET_MS / 1000)) '+%F %T') device-local ${TH}:${TM} -> spinner $H12 / $(printf %02d "$TM") / $AMPM"
HOURS="1,2,3,4,5,6,7,8,9,10,11,12"
MINUTES="$(seq -f %02g 0 59 | paste -sd,)"
spin_to 'alarm_spinner:hour' "$H12" "$HOURS"
spin_to 'alarm_spinner:minute' "$(printf %02d "$TM")" "$MINUTES"
spin_to 'alarm_spinner:ampm' "$AMPM" "AM,PM"
dump_ui "$ROW_DIR/editor_set.xml"; screencap "$ROW_DIR/editor_set.png"
assert_eq "the hour spinner reads the target hour" "$H12" "$(node_desc "$ROW_DIR/editor_set.xml" 'alarm_spinner:hour')"
assert_eq "the minute spinner reads the target minute" "$(printf %02d "$TM")" "$(node_desc "$ROW_DIR/editor_set.xml" 'alarm_spinner:minute')"
assert_eq "the AM/PM spinner reads the target half" "$AMPM" "$(node_desc "$ROW_DIR/editor_set.xml" 'alarm_spinner:ampm')"
CAPTION="$(node_text "$ROW_DIR/editor_set.xml" alarm_editor_caption)"
assert_contains "the caption counts down to the alarm (In N minutes)" "In " "$CAPTION"

# ---- Save: armed ---------------------------------------------------------------------------------------------
MARK="$(ring_mark)"
tap_node "$ROW_DIR/editor_set.xml" "clock_bar:save"; sleep 2
SLICE="$(ring_since "$MARK")"
assert_contains "Save re-arms: rearm (store change): 1 alarms, 0 timers, exact=true" "[alarms] rearm (store change): 1 alarms, 0 timers, exact=true" "$SLICE"
ID="$(alarm_ids | head -1)"
assert_ne "the store holds the new alarm" "" "$ID"
assert_eq "the stored hour is the target's" "$TH" "$(alarm_field "$ID" hour)"
assert_eq "the stored minute is the target's" "$TM" "$(alarm_field "$ID" minute)"
dump_ui "$ROW_DIR/list_armed.xml"; screencap "$ROW_DIR/list_armed.png"
assert_eq "the row shows the alarm's time" "$(time_12h "$TH" "$TM")" "$(node_text "$ROW_DIR/list_armed.xml" "alarm_time:$ID")"
assert_eq "the row's day line reads Today (armed for today)" "Today" "$(node_text "$ROW_DIR/list_armed.xml" "alarm_repeat:$ID")"
assert_eq "the row's state reads On" "On" "$(node_text "$ROW_DIR/list_armed.xml" "alarm_state:$ID")"
ENTRIES="$(clock_entries)"; note "dumpsys alarm clock entries: $ENTRIES"
assert_eq "dumpsys alarm: one RTC_WAKEUP alarm for app.tileshell at the target instant" "RTC_WAKEUP $TARGET_MS" "$(printf '%s\n' "$ENTRIES" | awk '{print $1, $2}' | paste -sd'|')"
assert_contains "… armed with setAlarmClock (an Alarm clock trigger time)" "$(date -d @$((TARGET_MS / 1000)) '+%F %H:%M:%S')" "$(printf '%s\n' "$ENTRIES" | cut -d' ' -f4-)"
assert_eq "Next alarm clock for user 0 is that instant" "$TARGET_MS" "$(next_alarm_clock_ms)"

# ---- toggle off, then on -------------------------------------------------------------------------------------
MARK="$(ring_mark)"
tap_node "$ROW_DIR/list_armed.xml" "alarm_toggle:$ID"; sleep 1.5
dump_ui "$ROW_DIR/list_off.xml"
assert_eq "toggle off: the row reads Off" "Off" "$(node_text "$ROW_DIR/list_off.xml" "alarm_state:$ID")"
assert_eq "toggle off: no clock alarm pending in AlarmManager" 0 "$(clock_pending)"
assert_eq "toggle off: no Next alarm clock for user 0" "" "$(next_alarm_clock_ms)"
assert_contains "toggle off: rearm (store change): 0 alarms" "[alarms] rearm (store change): 0 alarms, 0 timers, exact=true" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/list_off.xml" "alarm_toggle:$ID"; sleep 1.5
dump_ui "$ROW_DIR/list_on.xml"
assert_eq "toggle on: the row reads On" "On" "$(node_text "$ROW_DIR/list_on.xml" "alarm_state:$ID")"
assert_eq "toggle on: armed again at the same instant" "RTC_WAKEUP $TARGET_MS" "$(clock_entries | awk '{print $1, $2}' | paste -sd'|')"
assert_eq "toggle on: Next alarm clock is that instant again" "$TARGET_MS" "$(next_alarm_clock_ms)"
assert_contains "toggle on: rearm (store change): 1 alarms" "[alarms] rearm (store change): 1 alarms, 0 timers, exact=true" "$(ring_since "$MARK")"

# The first alarm is deleted through the app now, so the past-today alarm below is the ONLY armed one and the
# Next alarm clock line can only be its own.
app_delete_alarm "$ID"
assert_eq "restore 1: the first alarm is gone from the store" "" "$(alarm_ids | paste -sd,)"
assert_eq "restore 1: nothing pending" 0 "$(clock_pending)"

# ---- a one-shot at a time already past today arms for tomorrow (T15-57) --------------------------------------
NOW="$(device_ms)"
read -r PH PM_ <<< "$(python3 -c '
import sys, datetime, zoneinfo
tz = zoneinfo.ZoneInfo(sys.argv[1] or "UTC"); now = datetime.datetime.fromtimestamp(int(sys.argv[2]) / 1000, tz)
t = now - datetime.timedelta(minutes=2)
if t.date() < now.date(): t = now.replace(hour=0, minute=0)
print(t.hour, t.minute)' "$(adb shell getprop persist.sys.timezone | tr -d '\r')" "$NOW")"
EXPECT_MS="$(next_wall_ms "$PH" "$PM_")"
note "past-today time ${PH}:${PM_}; tomorrow's instant $EXPECT_MS ($(date -d @$((EXPECT_MS / 1000)) '+%F %T'))"
assert_eq "precondition: the chosen time is later than now only tomorrow" "yes" "$([ "$EXPECT_MS" -gt $((NOW + 12 * 3600000)) ] && echo yes || echo no)"
ID2="$(api_alarm "$PH" "$PM_" "Past")"
assert_ne "the past-today alarm was created (AlarmClock API)" "" "$ID2"
assert_eq "its stored date is tomorrow" "$(date -d @$((EXPECT_MS / 1000)) +%F)" "$(alarm_field "$ID2" date)"
assert_eq "dumpsys alarm arms it for tomorrow at that time" "RTC_WAKEUP $EXPECT_MS" "$(clock_entries | awk '{print $1, $2}' | paste -sd'|')"
assert_eq "Next alarm clock for user 0 is tomorrow's instant" "$EXPECT_MS" "$(next_alarm_clock_ms)"
open_clock alarm
dump_ui "$ROW_DIR/list_tomorrow.xml"; screencap "$ROW_DIR/list_tomorrow.png"
assert_eq "the row's day line reads Tomorrow" "Tomorrow" "$(node_text "$ROW_DIR/list_tomorrow.xml" "alarm_repeat:$ID2")"

# ---- restore ---------------------------------------------------------------------------------------------------
app_delete_alarm "$ID2"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end
