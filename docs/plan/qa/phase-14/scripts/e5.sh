#!/usr/bin/env bash
# Phase 14 E5 — Settings: the hub's Pod bay item, its page, a pod switched off is absent (the rest in the fixed order),
# all four off shows the empty-bay line that opens the page, the switches persist across a force-stop; restore all On.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E5 "Settings: Pod bay page, per-pod switches, empty bay, persistence"

node_checked() { # dump.xml resource-id -> true / false / ''
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for n in re.finditer(r'<node[^>]*>', xml):
    s = n.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'checked="(true|false)"', s); print(m.group(1) if m else ""); break
PY
}
open_settings_page() { # out.xml
  # single-top with page HOME: a Settings left on another page comes back to the hub (onNewIntent resets its stack)
  adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page HOME >/dev/null 2>&1
  sleep 3
  dump_ui "$1"
}
tops() { # dump.xml ids... -> the top y of each, space-separated ('-' when absent)
  local d="$1"; shift
  local id out=""
  for id in "$@"; do
    local b; b="$(bounds "$d" "$id")"
    if [ -n "$b" ]; then out+="$(echo "$b" | cut -d' ' -f2) "; else out+="- "; fi
  done
  echo "${out% }"
}

open_settings_page "$ROW_DIR/hub.xml"
assert_eq "the hub shows settings_pod_bay" "yes" "$(has_node "$ROW_DIR/hub.xml" settings_pod_bay)"
tap_node "$ROW_DIR/hub.xml" settings_pod_bay
sleep 2
dump_ui "$ROW_DIR/page.xml"
assert_eq "the Pod bay page opens (settings_page_pod_bay)" "yes" "$(has_node "$ROW_DIR/page.xml" settings_page_pod_bay)"
for p in agenda weather nowplaying reminders; do
  assert_eq "switch $p starts On" "true" "$(node_checked "$ROW_DIR/page.xml" "pod_switch:$p")"
done
screencap "$ROW_DIR/page.png"

# Weather off. The MARK is before the tap: the pod bay (composed in Start behind Settings) logs its enabled set when
# the setting changes, not when it is next looked at.
MARK="$(ring_mark)"
tap_node "$ROW_DIR/page.xml" pod_switch:weather
sleep 1
dump_ui "$ROW_DIR/page-weather-off.xml"
assert_eq "switch weather now Off" "false" "$(node_checked "$ROW_DIR/page-weather-off.xml" pod_switch:weather)"
ensure_start
swipe_right
dump_ui "$ROW_DIR/bay-no-weather.xml"
for p in agenda nowplaying reminders; do
  scroll_to_node "$ROW_DIR/bay-$p.xml" "pod:$p" >/dev/null; assert_eq "pod $p present" "yes" "$(has_node "$ROW_DIR/bay-$p.xml" "pod:$p")"
done
scroll_to_node "$ROW_DIR/bay-weather-scan.xml" "pod:weather" 4 >/dev/null
assert_eq "pod weather absent (scrolled to the end looking)" "no" "$(has_node "$ROW_DIR/bay-weather-scan.xml" pod:weather)"
t="$(tops "$ROW_DIR/bay-no-weather.xml" pod:agenda pod:nowplaying pod:reminders)"
note "pod tops in the first dump (agenda nowplaying reminders): $t"
if python3 -c 'import sys; v=[int(x) for x in sys.argv[1].split()]; sys.exit(0 if v==sorted(v) and len(set(v))==3 else 1)' "$t" 2>/dev/null; then
  _verdict PASS "the other three in the fixed order" "tops $t"
else
  _verdict FAIL "the other three in the fixed order" "tops $t"
fi
assert_contains "[podbay] pods enabled: agenda,nowplaying,reminders" "[podbay] pods enabled: agenda,nowplaying,reminders" "$(ring_since "$MARK")"

# All four off: the empty-bay line, which opens the Pod bay page.
open_settings_page "$ROW_DIR/hub2.xml"
tap_node "$ROW_DIR/hub2.xml" settings_pod_bay; sleep 2
dump_ui "$ROW_DIR/page2.xml"
assert_eq "the Pod bay page from the hub again" "yes" "$(has_node "$ROW_DIR/page2.xml" settings_page_pod_bay)"
MARK="$(ring_mark)"
for p in agenda nowplaying reminders; do tap_node "$ROW_DIR/page2.xml" "pod_switch:$p"; sleep 0.8; done
dump_ui "$ROW_DIR/page-all-off.xml"
for p in agenda weather nowplaying reminders; do
  assert_eq "switch $p Off" "false" "$(node_checked "$ROW_DIR/page-all-off.xml" "pod_switch:$p")"
done
ensure_start
swipe_right
dump_ui "$ROW_DIR/bay-empty.xml"
screencap "$ROW_DIR/bay-empty.png"
assert_eq "all off: pod_bay_empty present" "yes" "$(has_node "$ROW_DIR/bay-empty.xml" pod_bay_empty)"
assert_eq "all off: its text" "Turn on pods in Start settings" "$(node_text "$ROW_DIR/bay-empty.xml" pod_bay_empty)"
for p in agenda weather nowplaying reminders; do
  assert_eq "all off: no pod:$p" "no" "$(has_node "$ROW_DIR/bay-empty.xml" "pod:$p")"
done
assert_contains "[podbay] pods enabled: none" "[podbay] pods enabled: none" "$(ring_since "$MARK")"
tap_node "$ROW_DIR/bay-empty.xml" pod_bay_empty
sleep 3
dump_ui "$ROW_DIR/from-empty.xml"
assert_eq "the empty-bay line opens SettingsActivity" "app.tileshell/.settings.SettingsActivity" "$(top_activity)"
assert_eq "on the Pod bay page" "yes" "$(has_node "$ROW_DIR/from-empty.xml" settings_page_pod_bay)"

# Persistence across a force-stop.
ring_save
adb shell am force-stop app.tileshell
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 4
swipe_right
dump_ui "$ROW_DIR/bay-after-stop.xml"
assert_eq "after force-stop: still the empty bay" "yes" "$(has_node "$ROW_DIR/bay-after-stop.xml" pod_bay_empty)"
open_settings_page "$ROW_DIR/hub3.xml"
tap_node "$ROW_DIR/hub3.xml" settings_pod_bay; sleep 2
dump_ui "$ROW_DIR/page-after-stop.xml"
for p in agenda weather nowplaying reminders; do
  assert_eq "after force-stop: switch $p still Off" "false" "$(node_checked "$ROW_DIR/page-after-stop.xml" "pod_switch:$p")"
done

# Restore: all On.
for p in agenda weather nowplaying reminders; do tap_node "$ROW_DIR/page-after-stop.xml" "pod_switch:$p"; sleep 0.8; done
dump_ui "$ROW_DIR/page-restored.xml"
for p in agenda weather nowplaying reminders; do
  assert_eq "restore: switch $p On" "true" "$(node_checked "$ROW_DIR/page-restored.xml" "pod_switch:$p")"
done
ensure_start
row_end
