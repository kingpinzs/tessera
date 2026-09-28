#!/usr/bin/env bash
# L13-15 regression row (INDEX ledger; found by review/2026-09-27-L13-13-fix-r2-a.md): a World Clock city row read "no
# lift yet, the finger still down" as a hold, which is also what it saw when a scroll or a tab swipe took the press, so a
# scroll that started on a city opened its hold menu. The fix gives the rows the shared tap-or-hold detector (the alarm
# and timer rows'). Slow drags (the finger down past the hold time) that start on a city, vertical and horizontal, must
# open no menu; a still hold must still open it (the control).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
first_row() { grep -o 'resource-id="clock_row:[^"]*"' "$1" | head -1 | sed 's/resource-id="clock_row://; s/"$//'; }

row_begin L13_15 "a scroll or swipe that starts on a World Clock city opens no hold menu; a hold still does"
assert_clock_empty "baseline"
dismiss_any_ring
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1315 && echo yes || echo cp-failed; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
[ "$WSAVED" = cp-failed ] && { assert_eq "the World Clock store was copied aside before the seed" yes no; row_end; exit 1; }
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\",\\\"Europe/Paris\\\",\\\"Europe/Berlin\\\",\\\"Europe/Madrid\\\",\\\"Europe/Rome\\\",\\\"Asia/Tokyo\\\",\\\"Asia/Dubai\\\",\\\"Asia/Kolkata\\\",\\\"Australia/Sydney\\\",\\\"America/New_York\\\",\\\"America/Chicago\\\",\\\"America/Los_Angeles\\\"] > files/world_clock.json'"

# ---- (a) a slow scroll up that starts on a city
open_clock world_clock
gdump "$ROW_DIR/a-0.xml" > /dev/null
R="$(first_row "$ROW_DIR/a-0.xml")"; read -r RX RY <<< "$(centre "$ROW_DIR/a-0.xml" "clock_row:$R")"
assert_ne "(a): a city row is on screen" "" "${RX:-}"
adb shell input swipe $RX $RY $RX $(( RY - 500 )) 1500; sleep 1
gdump "$ROW_DIR/a-1.xml" > /dev/null
note "(a) first city before: $R, after the scroll: $(first_row "$ROW_DIR/a-1.xml"); row $R bounds $(bounds "$ROW_DIR/a-0.xml" "clock_row:$R") -> $(bounds "$ROW_DIR/a-1.xml" "clock_row:$R")"
assert_ne "(a): the drag scrolled the list" "$(bounds "$ROW_DIR/a-0.xml" "clock_row:$R")" "$(bounds "$ROW_DIR/a-1.xml" "clock_row:$R")"
assert_eq "(a): the scroll opened no hold menu" no "$(has_node "$ROW_DIR/a-1.xml" clock_row_menu)"

# ---- (b) a slow swipe sideways (to the next tab) that starts on a city
open_clock world_clock
gdump "$ROW_DIR/b-0.xml" > /dev/null
R="$(first_row "$ROW_DIR/b-0.xml")"; read -r RX RY <<< "$(centre "$ROW_DIR/b-0.xml" "clock_row:$R")"
adb shell input swipe $RX $RY $(( RX - 700 )) $RY 1500; sleep 1.5
gdump "$ROW_DIR/b-1.xml" > /dev/null
note "(b) after the swipe: world page $(has_node "$ROW_DIR/b-1.xml" 'clock_page:world_clock'), timer page $(has_node "$ROW_DIR/b-1.xml" 'clock_page:timer')"
assert_eq "(b): the swipe opened no hold menu" no "$(has_node "$ROW_DIR/b-1.xml" clock_row_menu)"

# ---- (c) control: a still hold on a city opens its menu
open_clock world_clock
gdump "$ROW_DIR/c-0.xml" > /dev/null
R="$(first_row "$ROW_DIR/c-0.xml")"; read -r RX RY <<< "$(centre "$ROW_DIR/c-0.xml" "clock_row:$R")"
adb shell input swipe $RX $RY $RX $RY 1200; sleep 1
gdump "$ROW_DIR/c-1.xml" > /dev/null
assert_eq "(c) control: a still hold on a city opens its menu" yes "$(has_node "$ROW_DIR/c-1.xml" clock_row_menu)"

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1315 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1315-seeded'"
fi
app_delete_all
assert_clock_empty "restore"
row_end
