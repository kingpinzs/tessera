#!/usr/bin/env bash
# Phase 14 build-start checks (Acceptance criteria "Page absence (T14-8)" and the Route Decision's "Build-start check,
# recorded in qa/phase-14/README.md"), run on the build installed BEFORE phase 14's code:
#   (a) an off-screen page the pager has composed (beyondViewportPageCount = 1) is absent from a uiautomator dump —
#       what every "no pod_bay" / "no app_list" assertion relies on;
#   (b) whether a HOME intent started from Tess's session (goHome(), the session's drawn Windows key) reaches
#       StartActivity.onNewIntent while Start is resumed beneath the session — read from Start's `[start] home:` line.
# Facts are RECORDed (C-26); (a) is also asserted, because the rows depend on it.
set -uo pipefail
export ANDROID_SERIAL=emulator-5554
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"

row_begin BUILDSTART "page absence in the dump; the session's HOME intent and onNewIntent"

ensure_start
adb shell input keyevent KEYCODE_BACK   # from the app list, if a previous row left it there
sleep 1
dump_ui "$ROW_DIR/start.xml"
assert_eq "(a) on Start: start_page in the dump" "yes" "$(has_node "$ROW_DIR/start.xml" start_page)"
assert_eq "(a) on Start: the composed app list is absent" "no" "$(has_node "$ROW_DIR/start.xml" app_list)"

adb shell input swipe 900 1200 150 1200 250
sleep 1.5
dump_ui "$ROW_DIR/applist.xml"
assert_eq "(a) on the app list: app_list in the dump" "yes" "$(has_node "$ROW_DIR/applist.xml" app_list)"
assert_eq "(a) on the app list: the composed Start page is absent" "no" "$(has_node "$ROW_DIR/applist.xml" start_page)"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/back.xml"
assert_eq "back on Start" "yes" "$(has_node "$ROW_DIR/back.xml" start_page)"

cortana_assist
sleep 4
dump_ui "$ROW_DIR/tess.xml"
assert_eq "Tess is open over Start" "yes" "$(has_node "$ROW_DIR/tess.xml" cortana_session)"
record "(b) top resumed activity under the session" \
  "$(adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | tr -d '\r' | sed 's/^ *//')"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/tess.xml" nav_windows
sleep 3
slice="$(ring_since "$MARK")"
printf '%s\n' "$slice" > "$ROW_DIR/slice-after-windows.txt"
home_line="$(printf '%s\n' "$slice" | grep -F '[start] home:' | head -1)"
hidden="$(printf '%s\n' "$slice" | grep -F '[cortana] session hidden' | head -1)"
record "(b) session hidden after its Windows key" "${hidden:-(no line)}"
record "(b) Start's home line after the session's HOME intent" "${home_line:-(none: the intent did not reach onNewIntent)}"
if [ -n "$home_line" ]; then
  record "(b) verdict" "the HOME intent from the session REACHES onNewIntent while Start is resumed beneath it"
else
  record "(b) verdict" "the HOME intent from the session does NOT reach onNewIntent; the focus-back PodBayCheck carries the rows"
fi
dump_ui "$ROW_DIR/after.xml"
assert_eq "Start is showing after the session went" "yes" "$(has_node "$ROW_DIR/after.xml" start_page)"
row_end
