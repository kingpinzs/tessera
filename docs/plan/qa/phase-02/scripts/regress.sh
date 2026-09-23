#!/usr/bin/env bash
# Phase 01 regression check for phase 02's gesture layer. Edit mode puts ONE pointer handler over the whole
# screen, so the things phase 01 passed have to be re-proved: a tap still launches, the pivot still swipes,
# Start still scrolls, Home still works, and the bottom row still launches.
# usage: regress.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
OUT=$1; LOG=$OUT/REGRESS.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
fg() { adb shell dumpsys activity activities | grep -m1 "topResumedActivity" | sed 's/.*u0 //;s/ .*//'; }
say "# phase 01 regression $(date -Iseconds)"
PASS=0; FAIL=0
ok() { if [ "$1" = "$2" ]; then say "PASS  $3 (got $1)"; PASS=$((PASS+1)); else say "FAIL  $3 (expected $2, got $1)"; FAIL=$((FAIL+1)); fi; }
# The y of the first tile in a dump. The regex holds double quotes, so it cannot sit inside a double-quoted
# python -c: the shell ended the string early, redirected to a stray file and printed nothing, so step 7's
# "scrolled back to the top" could never pass (2026-09-22). The path goes in as an argument.
first_tile_y() {
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="tile:[^"]+"[^>]*bounds="\[(\d+),(-?\d+)\]', s)
print(m.group(2) if m else "none")
PY
}

# Rows start from the baseline and restore what they change (PLAN RV12). This script used to run on whatever
# Start the device happened to show, and step 5's press target was not on it (2026-09-22).
layout_save "$OUT/r_layout_device.json"
layout_restore "$HERE/../baseline_layout.json" || { say "FAIL could not seed the baseline"; exit 1; }

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
[ -n "$SXY" ] || say "FAIL no Start settings or Maps tile on screen to press"
adb shell input swipe ${SXY% *} ${SXY#* } ${SXY% *} ${SXY#* } 300; sleep 2.5
TOP=$(fg); say "after a 300 ms press the top activity is $TOP"
# The press was a tap, so it launched something — and a tap never launches while edit mode is on. Coming back
# to Start, there must be no disc either. (This step used to print the activity and assert nothing.)
[ "${TOP%%/*}" != "app.tileshell" ] || [ "${TOP##*/}" = ".settings.SettingsActivity" ]
ok "$?" "0" "a 300 ms press acted as a tap, not a hold"
adb shell input keyevent KEYCODE_HOME; sleep 2
ensure_start
dump "$OUT/r_short_after.xml"
grep -q 'edit_disc' "$OUT/r_short_after.xml" && ok no yes "no edit-mode discs after a short press" || ok yes yes "no edit-mode discs after a short press"

say "--- 6. Home from another app returns to Start ---"
TOPNOW=$(fg); ok "${TOPNOW%%/*}" "app.tileshell" "Home shows the shell"

say "--- 7. the drawn Windows key returns the pivot to Start and scrolls it to the top (X20 / H28) ---"
# The gate wrote this off as an AVD limitation because KEYCODE_HOME is not re-delivered to the resumed home
# activity. That is true of the INTENT path only: W10mNavBar's Windows key emits the same event with no
# intent at all, so the behaviour IS testable here. This is the check that was missing.
layout_save "$OUT/r_layout_before2.json"
python3 "$HERE/make_tall.py" "$OUT/r_layout_before2.json" "$OUT/r_tall2.json"
layout_restore "$OUT/r_tall2.json"
ensure_start
adb shell input swipe 540 1700 540 600 250; sleep 1.5      # scroll Start down
dump "$OUT/r_scrolled.xml"
SCROLLED=$(first_tile_y "$OUT/r_scrolled.xml")
say "first tile's y after scrolling: $SCROLLED"
adb shell input swipe 900 1200 150 1200 250; sleep 1.5     # and go to the app list
dump "$OUT/r_on_applist.xml"
grep -q 'resource-id="app_list"' "$OUT/r_on_applist.xml" && ok yes yes "on the app list before pressing the Windows key" || ok no yes "on the app list before pressing the Windows key"
WK=$(center "$OUT/r_on_applist.xml" "nav_windows")
say "tapping the drawn Windows key at $WK"
adb shell input tap ${WK% *} ${WK#* }; sleep 2.5
dump "$OUT/r_after_windows_key.xml"
adb exec-out screencap -p > "$OUT/r_after_windows_key.png"
grep -q 'resource-id="start_page"' "$OUT/r_after_windows_key.xml" && ! grep -q 'resource-id="app_list"' "$OUT/r_after_windows_key.xml"
ok "$?" "0" "the Windows key brought the pivot back to Start"
AFTER=$(first_tile_y "$OUT/r_after_windows_key.xml")
say "first tile's y after the Windows key: $AFTER (84 is the top of the grid)"
ok "$AFTER" "84" "and scrolled Start back to the top (X20)"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep "\[start\]" | tail -5 > "$OUT/r_home_diag.txt"
say "the shell's own record: $(tail -1 "$OUT/r_home_diag.txt")"
grep -q "home: page 0" "$OUT/r_home_diag.txt" && ok yes yes "the shell logged the home event" || ok no yes "the shell logged the home event"
layout_restore "$OUT/r_layout_before2.json"
layout_restore "$OUT/r_layout_device.json" && say "restored: the device's own layout from before the run"
say "$PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
