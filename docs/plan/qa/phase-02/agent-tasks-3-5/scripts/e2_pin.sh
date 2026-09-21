#!/usr/bin/env bash
# E2 (phase 02 build task 3): the app list's long-press context menu (H21) pins the app to Start as a medium
# tile and clears its "New" caption. Fixture: testapps/tileclient-a, installed fresh here so the row carries the
# caption, and removed by restore.sh at the end of the pass.
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E2.txt
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
APK=$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
PKG=app.tileshell.testclient.a
TILE="tile:app:$PKG/app.tileshell.testclient.VerbActivity:0"

echo "# E2 $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "\$ adb uninstall $PKG: $(adb uninstall $PKG 2>&1 | tail -1)   # a fresh install time is what earns the caption" >> "$LOG"
echo "\$ adb install -r tileclient-a-debug.apk: $(adb install -r "$APK" | tail -1)" >> "$LOG"
sleep 5
echo '$ adb shell input swipe 900 1200 150 1200 250   # Start -> app list' >> "$LOG"
open_applist "$OUT/e2_pivot.xml" || exit 1

scroll_to_id "$OUT/e2_row.xml" "applist_row:$PKG" || exit 1
adb exec-out screencap -p > "$OUT/e2_1_row_with_new.png"
echo "row present: $(grep -c "resource-id=\"applist_row:$PKG\"" "$OUT/e2_row.xml"); New caption on the row: $(grep -c "resource-id=\"applist_new:$PKG\"" "$OUT/e2_row.xml")" >> "$LOG"
echo "row bounds: $(bounds "$OUT/e2_row.xml" "applist_row:$PKG")" >> "$LOG"

XY=$(center "$OUT/e2_row.xml" "applist_row:$PKG")
echo "\$ adb shell input swipe $XY $XY 900   # hold past Edit.HOLD_MS (783 ms)" >> "$LOG"
adb shell "input swipe $XY $XY 900"; sleep 2
dump "$OUT/e2_menu.xml"; adb exec-out screencap -p > "$OUT/e2_2_menu.png"
echo "menu band: $(grep -c 'resource-id="applist_menu"' "$OUT/e2_menu.xml") bounds=$(bounds "$OUT/e2_menu.xml" applist_menu)" >> "$LOG"
echo "Pin to Start item: $(grep -c 'resource-id="applist_menu_pin"' "$OUT/e2_menu.xml") bounds=$(bounds "$OUT/e2_menu.xml" applist_menu_pin) node=$(python3 "$NODES" "$OUT/e2_menu.xml" applist_menu_pin)" >> "$LOG"
echo "the list is still under the menu (row still dumped): $(grep -c "resource-id=\"applist_row:$PKG\"" "$OUT/e2_menu.xml")" >> "$LOG"

echo '$ tap the "Pin to Start" item' >> "$LOG"
tap_id "$OUT/e2_menu.xml" applist_menu_pin; sleep 2
dump "$OUT/e2_after.xml"; adb exec-out screencap -p > "$OUT/e2_3_after_pin.png"
echo "menu after the tap: $(grep -c 'resource-id="applist_menu"' "$OUT/e2_after.xml") (0 = closed)" >> "$LOG"
echo "New caption after the pin: $(grep -c "resource-id=\"applist_new:$PKG\"" "$OUT/e2_after.xml") (0 = cleared); the row itself: $(grep -c "resource-id=\"applist_row:$PKG\"" "$OUT/e2_after.xml")" >> "$LOG"

go_start
scroll_to_id "$OUT/e2_start.xml" "$TILE" || echo "TILE NOT FOUND ON START" >> "$LOG"
adb exec-out screencap -p > "$OUT/e2_4_start_tile.png"
echo "tiles named $TILE on Start: $(grep -c "resource-id=\"$TILE\"" "$OUT/e2_start.xml") bounds=$(bounds "$OUT/e2_start.xml" "$TILE")" >> "$LOG"
adb shell run-as app.tileshell cat files/start_layout.json > "$OUT/e2_layout.json"
echo "layout: $(python3 -c 'import json;d=json.load(open("'"$OUT"'/e2_layout.json"));print("version",d["version"],"| last 3 of order:",[(o["key"],o["size"]) for o in d["order"]][-3:])')" >> "$LOG"
echo "diagnostics:" >> "$LOG"
diag | grep -E "\[applist\] (pin to Start|context menu)|new caption cleared|\[layout\] pin " | tail -8 >> "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "done" >> "$LOG"
