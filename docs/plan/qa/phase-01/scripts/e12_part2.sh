#!/usr/bin/env bash
# E12 part 2: install a test APK -> "New" caption under its letter, no group above A, caption placement screencap.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E12.txt; APK=$OUT/test_app_librecontactsbackup_25.apk; PKG=com.ashkanrafiee.librecontactsbackup
echo "## install test APK ($PKG, label 'Libre Contacts Backup')" >> "$LOG"
echo "\$ adb install $APK: $(adb install "$APK" | tail -1)" >> "$LOG"
sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 900 1200 150 1200 250; sleep 2
# to the top of the list
for i in 1 2 3 4 5 6; do adb shell input swipe 540 700 540 2000 200; sleep 0.6; done
sleep 1; dump "$OUT/new_top.xml"; adb exec-out screencap -p > "$OUT/new_top.png"
echo "list top, ids in order: $(grep -o 'resource-id="\(applist_[a-z]*\)[^"]*"' "$OUT/new_top.xml" | sed 's/resource-id="//; s/"$//' | head -4 | tr '\n' ' ')" >> "$LOG"
dump "$OUT/jump_src.xml"; tap_id "$OUT/jump_src.xml" applist_header:A; sleep 1.5; dump "$OUT/jump_grid2.xml"
echo "jump grid after install, L cell: $(grep -o 'resource-id="jump_cell:L"[^>]*' "$OUT/jump_grid2.xml" | grep -o 'enabled="[a-z]*"')" >> "$LOG"
tap_id "$OUT/jump_grid2.xml" jump_cell:L; sleep 2
dump "$OUT/new_row.xml"; adb exec-out screencap -p > "$OUT/new_row.png"
echo "row: $(python3 "$(dirname "$0")/nodes.py" "$OUT/new_row.xml" applist_row:$PKG)" >> "$LOG"
echo "caption node: $(grep -o "resource-id=\"applist_new:$PKG\"[^>]*bounds=\"[^\"]*\"" "$OUT/new_row.xml" | grep -o 'bounds="[^"]*"')" >> "$LOG"
echo "headers on screen: $(grep -o 'resource-id="applist_header:[^"]*"' "$OUT/new_row.xml" | sed 's/.*://; s/"$//' | tr '\n' ' ')" >> "$LOG"
