#!/usr/bin/env bash
# EDGE_WORLD — the phase 15 World clock and 24-hour edge cases: a zone id with no ICU exemplar name shows its id's last
# segment ("Etc/GMT+5" → "GMT+5"); two zones with one exemplar name are both listed with their region; a search with
# no match reads "No results" (R7 §3.5.9's style, U5); a device locale change re-labels cities without a restart;
# the 24-hour setting (`settings put system time_12_24 24`) makes every time in the app read "H:mm". Restore: the
# cities removed, the locale and the 12/24 setting restored.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin EDGE_WORLD "world clock: no-exemplar zone, duplicate names, no match, locale change; the 24-hour setting"
assert_clock_empty "baseline"
search() { # text out.xml — a fresh search page every time (run 1 typed into a page that was not open)
  local i
  for i in 1 2 3; do
    dump_ui "$ROW_DIR/.s.xml"
    [ "$(has_node "$ROW_DIR/.s.xml" clock_search)" = yes ] || break
    adb shell input keyevent KEYCODE_BACK; sleep 1
  done
  dump_ui "$ROW_DIR/.s.xml"
  [ "$(has_node "$ROW_DIR/.s.xml" 'clock_bar:add')" = yes ] || open_clock world_clock
  dump_ui "$ROW_DIR/.s.xml"; tap_node "$ROW_DIR/.s.xml" 'clock_bar:add'; sleep 1.2
  adb shell input text "$1"; sleep 1.5
  dump_ui "$2"
}
open_clock world_clock

# ---- a zone with no ICU exemplar name: Etc/GMT+5 --------------------------------------------------------------------------------------------
search "GMT+5" "$ROW_DIR/gmt5.xml"; screencap "$ROW_DIR/gmt5.png"
assert_eq "search GMT+5 lists Etc/GMT+5 as GMT+5 (the id's last segment)" "GMT+5" "$(node_text "$ROW_DIR/gmt5.xml" 'clock_search_result:Etc/GMT+5')"
tap_node "$ROW_DIR/gmt5.xml" 'clock_search_result:Etc/GMT+5'; sleep 1.2
dump_ui "$ROW_DIR/gmt5_row.xml"
assert_eq "its row is labelled GMT+5" "GMT+5" "$(node_text "$ROW_DIR/gmt5_row.xml" 'clock_name:Etc/GMT+5')"
assert_eq "its difference line is computed (Today/…, N hours …)" "$(world_expect diff Etc/GMT+5 "$(adb shell getprop persist.sys.timezone | tr -d '\r')")" "$(node_text "$ROW_DIR/gmt5_row.xml" 'clock_diff:Etc/GMT+5')"

# ---- two zones with one exemplar name --------------------------------------------------------------------------------------------------------
# WorldClockRules.entries labels a shared name "<name>, <Region>", so every disambiguated entry contains ", ": the search
# for "," lists exactly those. Which names collide is the device's own ICU data; none → the case cannot be produced here.
search "," "$ROW_DIR/comma.xml"; screencap "$ROW_DIR/comma.png"
PAIR="$(python3 - "$ROW_DIR/comma.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
rows = [(m.group(1), m.group(2)) for m in re.finditer(r'resource-id="clock_search_result:([^"]+)"[^>]*text="([^"]*)"', xml)]
base = {}
for zid, label in rows:
    if ", " in label: base.setdefault(label.rsplit(", ", 1)[0], []).append((zid, label))
for b, l in base.items():
    if len(l) >= 2: print(l[0][0], l[0][1], "|", l[1][0], l[1][1]); break
PY
)"
note "labels holding ', ': $(grep -o 'resource-id="clock_search_result:[^"]*"[^>]*text="[^"]*"' "$ROW_DIR/comma.xml" | grep -o 'text="[^"]*"' | head -6 | paste -sd'|')"
if [ -n "$PAIR" ]; then
  record "a duplicate-name pair" "$PAIR"
  assert_eq "two zones sharing one exemplar name are both listed with their region" yes "$(echo "$PAIR" | python3 -c '
import sys
a, b = sys.stdin.read().split("|"); print("yes" if ", " in a and ", " in b else "no")')"
  Z1="$(echo "$PAIR" | awk '{print $1}')"; Z2="$(echo "$PAIR" | awk -F'|' '{print $2}' | awk '{print $1}')"
  assert_ne "… and they are two different zone ids" "$Z1" "$Z2"
else
  record "duplicate exemplar names: NOT RUN" "this image's ICU canonical zone list labels no two zones alike (the search for ', ' lists nothing)"
fi

# ---- no match ------------------------------------------------------------------------------------------------------------------------------------
search "Zzqx" "$ROW_DIR/nomatch.xml"; screencap "$ROW_DIR/nomatch.png"
assert_eq "a query nothing matches reads No results" "No results" "$(node_text "$ROW_DIR/nomatch.xml" clock_search_empty)"
assert_eq "… and lists nothing" "" "$(grep -o 'resource-id="clock_search_result:[^"]*"' "$ROW_DIR/nomatch.xml" | head -1)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- a device locale change re-labels the cities without a restart -----------------------------------------------------------------------------
search Vienna "$ROW_DIR/vienna.xml"
tap_node "$ROW_DIR/vienna.xml" 'clock_search_result:Europe/Vienna'; sleep 1.2
dump_ui "$ROW_DIR/vienna_row.xml"
assert_eq "Vienna is listed (en)" "Vienna" "$(node_text "$ROW_DIR/vienna_row.xml" 'clock_name:Europe/Vienna')"
PID_BEFORE="$(adb shell pidof app.tileshell | tr -d '\r')"
# The device locale is changed the way Android exposes it to a shell: the app's locale list (LocaleManager,
# `cmd locale set-app-locales`), which delivers a real configuration change to the running app — run 2's
# `setprop persist.sys.locale` + LOCALE_CHANGED broadcast changed the property but reached no running window.
record "app locales before" "$(adb shell cmd locale get-app-locales app.tileshell | tr -d '\r')"
adb shell cmd locale set-app-locales app.tileshell --locales de-DE >/dev/null 2>&1; sleep 4
record "app locales after the change" "$(adb shell cmd locale get-app-locales app.tileshell | tr -d '\r')"
dump_ui "$ROW_DIR/vienna_de.xml"; screencap "$ROW_DIR/vienna_de.png"
[ "$(has_node "$ROW_DIR/vienna_de.xml" 'clock_row:Europe/Vienna')" = yes ] || { open_clock world_clock; dump_ui "$ROW_DIR/vienna_de.xml"; }
assert_eq "the city is re-labelled in German (Wien) with no restart of the process" "Wien" "$(node_text "$ROW_DIR/vienna_de.xml" 'clock_name:Europe/Vienna')"
assert_eq "… the process is the same" "$PID_BEFORE" "$(adb shell pidof app.tileshell | tr -d '\r')"
adb shell cmd locale set-app-locales app.tileshell --locales "" >/dev/null 2>&1; sleep 4
open_clock world_clock
dump_ui "$ROW_DIR/vienna_en.xml"
assert_eq "restore: the label is English again" "Vienna" "$(node_text "$ROW_DIR/vienna_en.xml" 'clock_name:Europe/Vienna')"

# ---- the 24-hour setting ------------------------------------------------------------------------------------------------------------------------
AID="$(api_alarm 19 5 "Late")"
TID="$(api_timer 600 "Ten")"
PREV="$(adb shell settings get system time_12_24 | tr -d '\r')"; note "time_12_24 before: [$PREV]"
adb shell settings put system time_12_24 24; sleep 2
open_clock alarm
dump_ui "$ROW_DIR/h24_alarm.xml"; screencap "$ROW_DIR/h24_alarm.png"
assert_eq "24-hour: the alarm row reads 19:05 (H:mm)" "19:05" "$(node_text "$ROW_DIR/h24_alarm.xml" "alarm_time:$AID")"
open_clock world_clock
dump_ui "$ROW_DIR/h24_world.xml"
assert_eq "24-hour: the local row's time is H:mm (no AM/PM)" yes "$(node_text "$ROW_DIR/h24_world.xml" clock_local_time | grep -qE '^[0-9]{1,2}:[0-9]{2}$' && echo yes || echo "no ($(node_text "$ROW_DIR/h24_world.xml" clock_local_time))")"
assert_eq "24-hour: the city row's time is H:mm" yes "$(node_text "$ROW_DIR/h24_world.xml" 'clock_time:Europe/Vienna' | grep -qE '^[0-9]{1,2}:[0-9]{2}$' && echo yes || echo no)"
tap_node "$ROW_DIR/h24_alarm.xml" "alarm_row:$AID" 2>/dev/null; open_clock alarm; dump_ui "$ROW_DIR/h24_list.xml"; tap_node "$ROW_DIR/h24_list.xml" "alarm_row:$AID"; sleep 1.5
dump_ui "$ROW_DIR/h24_editor.xml"; screencap "$ROW_DIR/h24_editor.png"
assert_eq "24-hour: the editor's spinner has no AM/PM column" no "$(has_node "$ROW_DIR/h24_editor.xml" 'alarm_spinner:ampm')"
assert_eq "24-hour: the hour column reads 19" "19" "$(node_desc "$ROW_DIR/h24_editor.xml" 'alarm_spinner:hour')"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell settings put system time_12_24 "${PREV:-null}" >/dev/null 2>&1
[ "$PREV" = null ] && adb shell settings delete system time_12_24 >/dev/null 2>&1
sleep 2
open_clock alarm; dump_ui "$ROW_DIR/h12_alarm.xml"
assert_eq "restore: the alarm row reads 7:05 PM again" "7:05 PM" "$(node_text "$ROW_DIR/h12_alarm.xml" "alarm_time:$AID")"

# ---- restore -------------------------------------------------------------------------------------------------------------------------------------
open_clock world_clock
for z in Etc/GMT+5 Europe/Vienna; do
  dump_ui "$ROW_DIR/w_restore.xml"; hold_node "$ROW_DIR/w_restore.xml" "clock_row:$z" 1000; sleep 1
  dump_ui "$ROW_DIR/w_restore_menu.xml"; tap_node "$ROW_DIR/w_restore_menu.xml" "clock_remove:$z"; sleep 1
done
dump_ui "$ROW_DIR/w_restored.xml"
assert_eq "restore: no city rows left" "" "$(grep -o 'resource-id="clock_row:[^"]*"' "$ROW_DIR/w_restored.xml" | head -1)"
app_delete_alarm "$AID"; app_delete_timer "$TID"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end
