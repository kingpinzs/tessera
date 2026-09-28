#!/usr/bin/env bash
# L13-13 regression row (INDEX ledger; found by review/2026-09-27-L13-1112-fix-review-a.md): a World Clock hold menu
# could stay open, hidden, under the city search, so the next Back closed the hidden menu and nothing visible happened.
# Two causes: the hold menus did not close when the bar was used over them, and the search page's blank parts did not
# take touches (a hold there reached the city list under it). The fix closes a tab's hold menu when the bar is used
# (the search, Select, the compare strip, the "…" menu) and makes the search page modal. Three cases, each on a seeded
# city (London):
#   (1) New over a hold menu: the hold menu closes; then Back (after the keyboard's) closes the search;
#   (2) a hold on the search page's "No results" area, over where the city row is: no hold menu opens under it;
#   (3) "…" over a hold menu: the hold menu closes, and Back then closes the bar.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

ime_shown() { adb shell dumpsys input_method | tr -d '\r' | grep -c 'mInputShown=true'; }
hide_ime() { [ "$(ime_shown)" != 0 ] && { adb shell input keyevent 4; sleep 0.8; }; true; }
centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }

row_begin L13_13 "a World Clock hold menu never stays hidden under the search (New, a blank search area, the bar)"
assert_clock_empty "baseline"
dismiss_any_ring
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1313 && echo yes || echo cp-failed; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
[ "$WSAVED" = cp-failed ] && { assert_eq "the World Clock store was copied aside before the seed" yes no; row_end; exit 1; }
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\"] > files/world_clock.json'"
open_clock world_clock
gdump "$ROW_DIR/0.xml" > /dev/null
read -r CX CY <<< "$(centre "$ROW_DIR/0.xml" 'clock_row:Europe/London')"
read -r AX AY <<< "$(centre "$ROW_DIR/0.xml" 'clock_bar:add')"
read -r MX MY <<< "$(centre "$ROW_DIR/0.xml" clock_more)"
assert_ne "the city row is on screen" "" "${CX:-}"
assert_ne "the New button is on screen" "" "${AX:-}"
assert_ne "the \"…\" button is on screen" "" "${MX:-}"
hold_city() { adb shell input swipe $CX $CY $CX $CY 1200; sleep 1; }

# ---- (1) New over a hold menu
hold_city
gdump "$ROW_DIR/1-menu.xml" > /dev/null
assert_eq "(1): the hold opened the city's menu" yes "$(has_node "$ROW_DIR/1-menu.xml" clock_row_menu)"
adb shell input tap $AX $AY; sleep 1.5
gdump "$ROW_DIR/1-search.xml" > /dev/null
assert_eq "(1): New opened the search" yes "$(has_node "$ROW_DIR/1-search.xml" clock_search_page)"
# A hold menu left under the search is covered, and the dump leaves covered nodes out, so the dump cannot show it
# (drv1 passed this on the old build); what shows it is the next Back closing the hidden menu instead of the search.
note "(1): hold menu in the dump over the search: $(has_node "$ROW_DIR/1-search.xml" clock_row_menu) (covered nodes are not dumped)"
hide_ime
adb shell input keyevent 4; sleep 1.5
gdump "$ROW_DIR/1-back.xml" > /dev/null
assert_eq "(1): Back closed the search" no "$(has_node "$ROW_DIR/1-back.xml" clock_search_page)"
assert_eq "(1): ... and the Clock is still open" yes "$(has_node "$ROW_DIR/1-back.xml" clock_root)"

# ---- (2) a hold on the search page's blank area, over the city row
open_clock world_clock
adb shell input tap $AX $AY; sleep 1.5
adb shell input text zzzz; sleep 1
hide_ime
gdump "$ROW_DIR/2-search.xml" > /dev/null
assert_eq "(2): the search is open" yes "$(has_node "$ROW_DIR/2-search.xml" clock_search_page)"
assert_absent "(2): its query matches no city (the page under the hold point is blank)" "clock_search_result:" "$(grep -o 'resource-id="clock_search_result:[^"]*"' "$ROW_DIR/2-search.xml")"
hold_city
gdump "$ROW_DIR/2-hold.xml" > /dev/null
assert_eq "(2): the hold on the search page opened no city menu under it" no "$(has_node "$ROW_DIR/2-hold.xml" clock_row_menu)"
assert_eq "(2): ... and the search is still open" yes "$(has_node "$ROW_DIR/2-hold.xml" clock_search_page)"
adb shell input keyevent 4; sleep 1.5
gdump "$ROW_DIR/2-back.xml" > /dev/null
assert_eq "(2): Back closed the search, the Clock still open" "no yes" "$(has_node "$ROW_DIR/2-back.xml" clock_search_page) $(has_node "$ROW_DIR/2-back.xml" clock_root)"

# ---- (3) the "…" bar over a hold menu
open_clock world_clock
hold_city
gdump "$ROW_DIR/3-menu.xml" > /dev/null
assert_eq "(3): the hold opened the city's menu" yes "$(has_node "$ROW_DIR/3-menu.xml" clock_row_menu)"
adb shell input tap $MX $MY; sleep 1
gdump "$ROW_DIR/3-bar.xml" > /dev/null
assert_eq "(3): \"…\" opened the bar's menu" yes "$(has_node "$ROW_DIR/3-bar.xml" clock_more_menu)"
assert_eq "(3): ... and the hold menu closed" no "$(has_node "$ROW_DIR/3-bar.xml" clock_row_menu)"
adb shell input keyevent 4; sleep 1.5
gdump "$ROW_DIR/3-back.xml" > /dev/null
assert_eq "(3): Back closed the bar, the Clock still open" "no yes" "$(has_node "$ROW_DIR/3-back.xml" clock_more_menu) $(has_node "$ROW_DIR/3-back.xml" clock_root)"

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1313 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1313-seeded'"
fi
app_delete_all
assert_clock_empty "restore"
row_end
