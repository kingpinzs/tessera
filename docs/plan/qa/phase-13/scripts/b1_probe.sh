#!/usr/bin/env bash
# Gate review A's B1 (2026-09-26): a static-layer build cancelled because acrylic turned off again was recorded as a
# failure, so the next "on" for the same key never built. Probe: with the checker set and acrylic on, tap the switch
# three times ~60 ms apart (off, on, off: the middle "on" starts a build that the last "off" cancels), then once more
# 2 s later (on). Pass: no `static backdrop failed` line, a `static backdrop rebuilt` line after the last on, and the
# app list blurred (E1's edge spread). The on-device race depends on tap spacing vs the ~50-150 ms build, so the
# JVM test StaticBackdropTest is the deterministic red / green; this row records what the device does.
#   b1_probe.sh <label>   ->  qa/phase-13/B1_PROBE-<label>/
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin "B1_PROBE-${1:?label}" "a cancelled static build is not a failure: off / on / off in ~60 ms, then on"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
set_checker
show_start 7
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"
Y="$(applist_strip "$ROW_DIR/applist.xml")"
open_transparency
read -r TX TY <<< "$(python3 -c "
import re; s = open('$SETTINGS_DUMP').read()
m = re.search(r'resource-id=\"theme_transparency_effects\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s)
print((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2) if m else print('')")"
assert_ne "the switch is on screen" "" "${TX:-}"
MARK="$(ring_mark)"
adb shell "input tap $TX $TY & sleep 0.06; input tap $TX $TY & sleep 0.06; input tap $TX $TY; wait"
sleep 2
adb shell input tap "$TX" "$TY"
sleep 2
ring_since "$MARK" > "$ROW_DIR/slice.txt"
grep -F '[fluent]' "$ROW_DIR/slice.txt" | sed 's/^ *//' >> "$LOG"
record "acrylic lines after the MARK" "$(grep -c 'acrylic=' "$ROW_DIR/slice.txt")"
assert_eq "the switch ends On" "true" "$(transparency_state)"
assert_absent "no static backdrop failed line (a cancel is not a failure)" "static backdrop failed" "$(cat "$ROW_DIR/slice.txt")"
LAST_ON="$(grep -F 'acrylic=on' "$ROW_DIR/slice.txt" | tail -1 | grep -oE 'wall=[0-9]+' | cut -d= -f2)"
REBUILT="$(grep -F 'static backdrop rebuilt' "$ROW_DIR/slice.txt" | tail -1 | grep -oE 'wall=[0-9]+' | cut -d= -f2)"
note "last acrylic=on wall=$LAST_ON, last rebuilt wall=$REBUILT"
assert_eq "a rebuilt line after the last on" "yes" "$([ -n "$REBUILT" ] && [ -n "$LAST_ON" ] && [ "$REBUILT" -ge "$LAST_ON" ] && echo yes || echo no)"
leave_settings
dump_ui "$ROW_DIR/applist-after.xml"
assert_eq "back on the app list" "yes" "$(has_node "$ROW_DIR/applist-after.xml" app_list)"
screencap "$ROW_DIR/after.png"
read -r W _ _ <<< "$(applist_edge "$ROW_DIR/after.png" "$Y")"
assert_within "the app list is blurred (E1's band)" 134.5 "$W" 26.9
to_start 2
clear_background
show_start 3
row_end
