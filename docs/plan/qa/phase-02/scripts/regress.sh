#!/usr/bin/env bash
# Phase 01 regression check for phase 02's gesture layer. Edit mode puts ONE pointer handler over the whole
# screen, so the things phase 01 passed have to be re-proved: a tap still launches, the pivot still swipes,
# Start still scrolls, Home still works, and the bottom row still launches.
# usage: regress.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/REGRESS.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
fg() { adb shell dumpsys activity activities | grep -m1 "topResumedActivity" | sed 's/.*u0 //;s/ .*//'; }
say "# phase 01 regression $(date -Iseconds)"
PASS=0; FAIL=0
ok() { if [ "$1" = "$2" ]; then say "PASS  $3 (got $1)"; PASS=$((PASS+1)); else say "FAIL  $3 (expected $2, got $1)"; FAIL=$((FAIL+1)); fi; }

adb shell input keyevent KEYCODE_HOME; sleep 2.5
dump "$OUT/r_start.xml"
say "top activity on Start: $(fg)"

say "--- 1. a tap on a grid tile launches its app ---"
XY=$(center "$OUT/r_start.xml" "tile:slot:BROWSER" || center "$OUT/r_start.xml" "tile:slot:MAIL")
adb shell input tap ${XY% *} ${XY#* }; sleep 3
TOP=$(fg); say "after the tap the top activity is $TOP"
adb exec-out screencap -p > "$OUT/r_after_tap.png"
[ "${TOP%%/*}" != "app.tileshell" ] && ok yes yes "a tile tap launched another app" || ok no yes "a tile tap launched another app"
adb shell input keyevent KEYCODE_HOME; sleep 2.5

say "--- 2. a tap on a bottom-row tile launches its app ---"
dump "$OUT/r_row.xml"
RXY=$(center "$OUT/r_row.xml" "tile:dock:slot:PHONE" || center "$OUT/r_row.xml" "tile:dock:slot:CAMERA")
adb shell input tap ${RXY% *} ${RXY#* }; sleep 3
TOP=$(fg); say "after the row tap the top activity is $TOP"
[ "${TOP%%/*}" != "app.tileshell" ] && ok yes yes "a row tile tap launched another app" || ok no yes "a row tile tap launched another app"
adb shell input keyevent KEYCODE_HOME; sleep 2.5

say "--- 3. the pivot still swipes to the app list and back ---"
adb shell input swipe 900 1200 150 1200 250; sleep 1.5
dump "$OUT/r_applist.xml"
grep -q 'resource-id="app_list"' "$OUT/r_applist.xml" && ok yes yes "swipe opened the app list" || ok no yes "swipe opened the app list"
adb exec-out screencap -p > "$OUT/r_applist.png"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump "$OUT/r_back_on_start.xml"
grep -q 'resource-id="start_page"' "$OUT/r_back_on_start.xml" && ok yes yes "Back returned to Start" || ok no yes "Back returned to Start"

say "--- 4. Start still scrolls (with a layout taller than the screen: the default ten tiles are not) ---"
layout_save "$OUT/r_layout_before.json"
python3 "$(dirname "$0")/make_tall.py" "$OUT/r_layout_before.json" "$OUT/r_tall.json"
layout_restore "$OUT/r_tall.json"
dump "$OUT/r_scroll_before.xml"
adb shell input swipe 540 1600 540 700 250; sleep 1.5
dump "$OUT/r_scroll_after.xml"
python3 "$(dirname "$0")/dumpdiff.py" "$OUT/r_scroll_before.xml" "$OUT/r_scroll_after.xml" > "$OUT/r_scroll.diff" 2>&1
grep -q DIFFERENT "$OUT/r_scroll.diff" && ok yes yes "a swipe scrolled the grid" || ok no yes "a swipe scrolled the grid"
adb shell input swipe 540 700 540 1900 250; sleep 1
layout_restore "$OUT/r_layout_before.json"

say "--- 5. a short press does NOT enter edit mode (under the 783 ms hold) ---"
dump "$OUT/r_short.xml"
SXY=$(center "$OUT/r_short.xml" "tile:shell:settings" || center "$OUT/r_short.xml" "tile:slot:MAPS")
adb shell input swipe ${SXY% *} ${SXY#* } ${SXY% *} ${SXY#* } 300; sleep 2.5
TOP=$(fg); say "after a 300 ms press the top activity is $TOP"
adb shell input keyevent KEYCODE_HOME; sleep 2

say "--- 6. Home from another app returns to Start ---"
TOPNOW=$(fg); ok "${TOPNOW%%/*}" "app.tileshell" "Home shows the shell"
say "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
