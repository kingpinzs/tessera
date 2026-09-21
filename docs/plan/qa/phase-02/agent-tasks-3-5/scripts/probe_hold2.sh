#!/usr/bin/env bash
# Probe 2: is the menu on screen DURING the hold, and gone after the finger lifts?
source "$(dirname "$0")/p2.sh"
set -u
OUT=/tmp/claude-1000/-home-jeremyking/277bd9d2-7817-47e9-bc8f-0d5611a2abef/scratchpad/a35
open_applist "$OUT/p2_list.xml" || exit 1
ROW=$(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/p2_list.xml" | head -1 | sed 's/resource-id="//; s/"$//')
XY=$(center "$OUT/p2_list.xml" "$ROW")
echo "row: $ROW at $XY"
adb shell "input swipe $XY $XY 5000" &
sleep 2
dump "$OUT/p2_during.xml"
echo "DURING the hold -> menu: $(grep -c 'resource-id="applist_menu"' "$OUT/p2_during.xml") pin item: $(grep -c 'resource-id="applist_menu_pin"' "$OUT/p2_during.xml")"
wait
sleep 2
dump "$OUT/p2_after.xml"
echo "AFTER the finger lifts -> menu: $(grep -c 'resource-id="applist_menu"' "$OUT/p2_after.xml")"
diag | grep -E "\[applist\] context menu" | tail -2
