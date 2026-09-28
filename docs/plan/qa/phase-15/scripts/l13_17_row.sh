#!/usr/bin/env bash
# L13-17 regression row (INDEX ledger; found by review/2026-09-28-L13-14-fix-r3-a.md note 2): a bar button pressed
# while the bar was expanded left it expanded — only leaveTabModes, Back and About cleared the flag — so "…" then Add
# opened the alarm or timer editor with its "…" menu already up, "…" then Select left the menu over the select list,
# and "…" then Compare left the flag set, so the next Back cleared only the flag and Compare stayed. The fix collapses
# the bar before a button acts, as the "…" menu's items do. Each case starts from a force-stopped Clock with "…" open.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

centre() { set -- $(bounds "$1" "$2"); [ $# -eq 4 ] && echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"; }
# tab case -> opens the Clock on tab with "…" open; leaves the open dump at $ROW_DIR/<case>-0.xml
open_expanded() {
  adb shell am force-stop $PKG; sleep 1
  open_clock "$1"
  gdump "$ROW_DIR/$2-pre.xml" > /dev/null
  local mx my; read -r mx my <<< "$(centre "$ROW_DIR/$2-pre.xml" clock_more)"
  adb shell input tap $mx $my; sleep 1
  gdump "$ROW_DIR/$2-0.xml" > /dev/null
  assert_eq "($2): \"…\" opened the bar's menu" yes "$(has_node "$ROW_DIR/$2-0.xml" clock_more_menu)"
}
press() { # case tag
  local x y; read -r x y <<< "$(centre "$ROW_DIR/$1-0.xml" "$2")"
  assert_ne "($1): $2 is on the expanded bar" "" "${x:-}"
  [ -n "${x:-}" ] && adb shell input tap $x $y; sleep 1.5
  gdump "$ROW_DIR/$1-1.xml" > /dev/null
}

row_begin L13_17 "a bar button pressed with the bar expanded collapses it before it acts"
assert_clock_empty "baseline"
dismiss_any_ring
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1317 && echo yes || echo cp-failed; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
[ "$WSAVED" = cp-failed ] && { assert_eq "the World Clock store was copied aside before the seed" yes no; row_end; exit 1; }
# One alarm 3 h ahead (Select needs one) and one city (Compare needs one).
NOW="$(device_ms)"; read -r H M <<< "$(device_hm $(( NOW + 3 * 3600000 )))"
AID="$(api_alarm "$H" "$M" "L1317")"; assert_ne "seed: an alarm" "" "$AID"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\"] > files/world_clock.json'"

# ---- (a) Alarm: "…" then Add opens the editor with its bar collapsed
open_expanded alarm a
press a clock_bar:add
assert_eq "(a): Add opened the alarm editor" yes "$(has_node "$ROW_DIR/a-1.xml" 'alarm_editor_field:snooze')"
assert_eq "(a): ... with no \"…\" menu up" no "$(has_node "$ROW_DIR/a-1.xml" clock_more_menu)"
assert_eq "(a): ... and no bar scrim" no "$(has_node "$ROW_DIR/a-1.xml" clock_bar_scrim)"

# ---- (b) Timer: "…" then Add opens the timer editor with its bar collapsed
open_expanded timer b
press b clock_bar:add
assert_eq "(b): Add opened the timer editor" yes "$(has_node "$ROW_DIR/b-1.xml" 'timer_editor_field:name')"
assert_eq "(b): ... with no \"…\" menu up" no "$(has_node "$ROW_DIR/b-1.xml" clock_more_menu)"
assert_eq "(b): ... and no bar scrim" no "$(has_node "$ROW_DIR/b-1.xml" clock_bar_scrim)"

# ---- (c) Alarm: "…" then Select enters select mode with the bar collapsed
open_expanded alarm c
press c alarm_select
assert_eq "(c): Select entered select mode" yes "$(has_node "$ROW_DIR/c-1.xml" alarm_select_delete)"
assert_eq "(c): ... with no \"…\" menu up" no "$(has_node "$ROW_DIR/c-1.xml" clock_more_menu)"

# ---- (d) World Clock: "…" then Compare, then one Back closes Compare
open_expanded world_clock d
press d clock_compare
assert_eq "(d): Compare showed the strip" yes "$(has_node "$ROW_DIR/d-1.xml" clock_compare_strip)"
adb shell input keyevent 4; sleep 1.5
gdump "$ROW_DIR/d-2-back.xml" > /dev/null
assert_eq "(d): one Back closed Compare, the Clock still open" "no yes" \
  "$(has_node "$ROW_DIR/d-2-back.xml" clock_compare_strip) $(has_node "$ROW_DIR/d-2-back.xml" clock_app_bar)"

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1317 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1317-seeded'"
fi
app_delete_all
assert_clock_empty "restore"
row_end
