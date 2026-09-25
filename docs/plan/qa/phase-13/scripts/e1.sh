#!/usr/bin/env bash
# E1 The rule and its controls, both directions (phase 13 Acceptance E1; C-18, C-20, T13-9, T13-20). Every step reads
# ring_since a MARK taken just before its action. The preset sub-row (T13-4) is phase 12's: it runs in phase 12's
# build session, because phase 12 builds after this phase (T12-3).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E1 "the acrylic rule and its controls: battery saver, the switch, disable_window_blurs"
BAND_LO=107.6; BAND_HI=161.4   # 2.563 * sigma(90 px) +- 20 %

# ---- process start
set_pref transparency_effects boolean true
MARK="$(ring_mark)"
set_checker
show_start 7
SLICE="$(ring_since "$MARK")"
assert_contains "process start: acrylic=on reason=none" "[fluent] acrylic=on reason=none" "$SLICE"
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"
Y="$(applist_strip "$ROW_DIR/applist.xml")"
assert_ne "a text-free strip on a square-centre row exists" "" "$Y"
note "strip y=$Y (x 675..945, the x = 810 boundary)"
screencap "$ROW_DIR/on-0.png"
read -r W A B <<< "$(applist_edge "$ROW_DIR/on-0.png" "$Y")"
assert_within "acrylic on: edge spread in the blurred band" 134.5 "$W" 26.9
note "on-0: width=$W plateaus=$A/$B"

# ---- battery saver on (its low_power = 1 assertion is the precondition)
battery_saver_on
sleep 1.5
SLICE="$(ring_since "$BS_MARK")"
assert_contains "battery saver: acrylic=off reason=battery-saver" "[fluent] acrylic=off reason=battery-saver" "$SLICE"
WALL="$(printf '%s\n' "$SLICE" | wall_of "acrylic=off reason=battery-saver")"
assert_within "battery saver: the line within 1000 ms of BS_MARK" 500 "$(( ${WALL:-99999999999999} - BS_MARK ))" 500
screencap "$ROW_DIR/saver.png"
read -r W A B <<< "$(applist_edge "$ROW_DIR/saver.png" "$Y")"
assert_within "battery saver: the edge is sharp (<= 2 px)" 1 "$W" 1
assert_within "battery saver: black square reads 0.2 x 0" 0 "$A" 3
assert_within "battery saver: white square reads 0.2 x 255" 51 "$B" 3

# ---- battery saver off
MARK="$(ring_mark)"
assert_eq "battery_saver_off wakes the device" "Awake" "$(battery_saver_off)"
assert_eq "battery saver off: low_power" "0" "$(adb shell settings get global low_power | tr -d '\r')"
sleep 1.5
assert_contains "saver off: acrylic=on" "[fluent] acrylic=on reason=none" "$(ring_since "$MARK")"
screencap "$ROW_DIR/on-1.png"
read -r W A B <<< "$(applist_edge "$ROW_DIR/on-1.png" "$Y")"
assert_within "saver off: blurred again" 134.5 "$W" 26.9

# ---- the switch off, through Settings > Start + theme
open_transparency
MARK="$(ring_mark)"
tap_transparency
SLICE="$(ring_since "$MARK")"
assert_contains "switch off: acrylic=off reason=setting" "[fluent] acrylic=off reason=setting" "$SLICE"
WALL="$(printf '%s\n' "$SLICE" | wall_of "acrylic=off reason=setting")"
assert_within "switch off: the line within 1000 ms of the tap's MARK" 500 "$(( ${WALL:-99999999999999} - MARK ))" 500
assert_eq "switch off: saved" "false" "$(transparency_state)"
leave_settings
dump_ui "$ROW_DIR/applist-off.xml"
assert_eq "back on the app list" "yes" "$(has_node "$ROW_DIR/applist-off.xml" app_list)"
screencap "$ROW_DIR/switch-off.png"
read -r W A B <<< "$(applist_edge "$ROW_DIR/switch-off.png" "$Y")"
assert_within "switch off: the edge is sharp, as under battery saver" 1 "$W" 1
assert_within "switch off: black square reads 0" 0 "$A" 3
assert_within "switch off: white square reads 51" 51 "$B" 3

# ---- the switch on
open_transparency
MARK="$(ring_mark)"
tap_transparency
assert_contains "switch on: acrylic=on" "[fluent] acrylic=on reason=none" "$(ring_since "$MARK")"
assert_eq "switch on: saved" "true" "$(transparency_state)"
leave_settings

# ---- disable_window_blurs gates cross-window blur only (recorded for phase 04)
MARK="$(ring_mark)"
adb shell settings put global disable_window_blurs 1
sleep 2
record "mBlurEnabled with disable_window_blurs 1" "$(adb shell dumpsys window | grep -m1 -o 'mBlurEnabled=[a-z]*')"
assert_absent "disable_window_blurs: no acrylic=off line" "acrylic=off" "$(ring_since "$MARK")"
screencap "$ROW_DIR/disable-blurs.png"
read -r W A B <<< "$(applist_edge "$ROW_DIR/disable-blurs.png" "$Y")"
assert_within "disable_window_blurs: the app list stays blurred" 134.5 "$W" 26.9
adb shell settings delete global disable_window_blurs >/dev/null

# ---- restore
ring_save
set_pref transparency_effects boolean true
clear_background
show_start 5
row_end
