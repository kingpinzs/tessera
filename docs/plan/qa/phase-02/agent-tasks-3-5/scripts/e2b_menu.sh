#!/usr/bin/env bash
# Build task 3, the rest of the menu's behaviour: pinning an app that is already on Start makes no second
# tile, Back and a tap outside both close the menu without pinning or launching, and the list still behaves
# (a tap launches, search finds, the letter jump grid opens).
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E2b.txt
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
APK_A=$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
A=app.tileshell.testclient.a
TILE_A="tile:app:$A/app.tileshell.testclient.VerbActivity:0"

echo "# E2b $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "\$ adb install -r tileclient-a: $(adb install -r "$APK_A" | tail -1)" >> "$LOG"
sleep 4

echo '' >> "$LOG"
echo '## pin once, then pin the same app again' >> "$LOG"
pin_app "$A" "$OUT/e2b_pin1.xml" || echo "FIRST PIN FAILED" >> "$LOG"
pin_app "$A" "$OUT/e2b_pin2.xml" || echo "SECOND PIN FAILED" >> "$LOG"
echo "pin diagnostics:" >> "$LOG"
diag | grep -E "\[layout\] pin app:$A|\[applist\] pin to Start $A" | tail -4 >> "$LOG"
echo "tiles for A in the layout file: $(adb shell run-as app.tileshell cat files/start_layout.json | grep -o "app:$A[^\"]*" | wc -l) (1 = no second tile)" >> "$LOG"
go_start
scroll_to_id "$OUT/e2b_start.xml" "$TILE_A" >/dev/null
echo "tiles for A drawn on Start: $(grep -c "resource-id=\"$TILE_A\"" "$OUT/e2b_start.xml")" >> "$LOG"

echo '' >> "$LOG"
echo '## Back closes the menu' >> "$LOG"
open_applist "$OUT/e2b_list.xml" || exit 1
ROW=$(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/e2b_list.xml" | head -1 | sed 's/resource-id="//; s/"$//')
hold_id "$OUT/e2b_list.xml" "$ROW" 900; sleep 2
dump "$OUT/e2b_menu_back.xml"
echo "menu open on $ROW: $(grep -c 'resource-id="applist_menu"' "$OUT/e2b_menu_back.xml")" >> "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 2
dump "$OUT/e2b_after_back.xml"
echo "after Back -> menu: $(grep -c 'resource-id="applist_menu"' "$OUT/e2b_after_back.xml") (0 = closed); still on the app list: $(grep -c 'resource-id="app_list"' "$OUT/e2b_after_back.xml"); rows still listed: $(grep -c 'resource-id="applist_row:' "$OUT/e2b_after_back.xml")" >> "$LOG"

echo '' >> "$LOG"
echo '## a tap outside the band closes the menu and launches nothing' >> "$LOG"
hold_id "$OUT/e2b_list.xml" "$ROW" 900; sleep 2
dump "$OUT/e2b_menu_outside.xml"
BAND=$(bounds "$OUT/e2b_menu_outside.xml" applist_menu)
echo "menu open: $(grep -c 'resource-id="applist_menu"' "$OUT/e2b_menu_outside.xml") band=$BAND" >> "$LOG"
echo '$ adb shell input tap 540 1900   # well below the band' >> "$LOG"
adb shell input tap 540 1900; sleep 2
dump "$OUT/e2b_after_outside.xml"
echo "after the tap outside -> menu: $(grep -c 'resource-id="applist_menu"' "$OUT/e2b_after_outside.xml") (0 = closed); foreground: $(grep -o 'package="[^"]*"' "$OUT/e2b_after_outside.xml" | sort -u | tr '\n' ' ')" >> "$LOG"
echo "launches recorded since the menu opened: $(diag | grep -c '\[launch\] startMainActivity' ) total; last: $(diag | grep '\[launch\]' | tail -1 | sed 's/^ *//')" >> "$LOG"

echo '' >> "$LOG"
echo '## the list still behaves: tap launches, search finds, the jump grid opens' >> "$LOG"
open_applist "$OUT/e2b_list2.xml" || exit 1
tap_id "$OUT/e2b_list2.xml" "$ROW"; sleep 3
dump "$OUT/e2b_after_tap.xml"
echo "tap on $ROW -> foreground: $(grep -o 'package="[^"]*"' "$OUT/e2b_after_tap.xml" | sort -u | tr '\n' ' ')" >> "$LOG"
echo "launch diagnostics: $(diag | grep '\[launch\]' | tail -1 | sed 's/^ *//')" >> "$LOG"
open_applist "$OUT/e2b_list3.xml" || exit 1
tap_id "$OUT/e2b_list3.xml" applist_search; sleep 1
adb shell input text tile; sleep 2
dump "$OUT/e2b_search.xml"; adb exec-out screencap -p > "$OUT/e2b_1_search.png"
echo "search 'tile' -> rows: $(grep -o 'resource-id="applist_row:[^"]*"' "$OUT/e2b_search.xml" | sed 's/resource-id="applist_row://; s/"$//' | tr '\n' ' ')" >> "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
open_applist "$OUT/e2b_list4.xml" || exit 1
HEADER=$(grep -o 'resource-id="applist_header:[^"]*"' "$OUT/e2b_list4.xml" | head -1 | sed 's/resource-id="//; s/"$//')
tap_id "$OUT/e2b_list4.xml" "$HEADER"; sleep 2
dump "$OUT/e2b_grid.xml"; adb exec-out screencap -p > "$OUT/e2b_2_jump_grid.png"
echo "tap on $HEADER -> jump grid: $(grep -c 'resource-id="jump_grid"' "$OUT/e2b_grid.xml") cells: $(grep -c 'resource-id="jump_cell:' "$OUT/e2b_grid.xml")" >> "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"
