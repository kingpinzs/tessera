#!/usr/bin/env bash
# L13-12 regression row (INDEX ledger): with a Clock row's hold menu open (alarm, timer, World Clock), Back closed the
# whole Clock instead of the menu — the menus' state is local to each tab and the activity's Back (ClockNav.back()) did
# not know it, so it fell through to finish(). The fix gives each hold menu its own BackHandler. Each case opens the
# menu by a hold on a seeded row, presses Back, and requires the menu gone with the Clock still open; a second Back
# then closes the Clock as before (the control).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin L13_12 "Back with a Clock row's hold menu open closes the menu, not the Clock (alarm, timer, World Clock)"
assert_clock_empty "baseline"
dismiss_any_ring
# Seeds: an alarm 3 h ahead (no ring during the row), a 1-hour timer, and London in the World Clock (its store kept
# aside and put back at the end; with none before, the seeded file is moved aside rather than deleted).
WSAVED="$(adb shell "run-as $PKG sh -c 'if [ -f files/world_clock.json ]; then cp files/world_clock.json files/world_clock.json.l1312; echo yes; else echo no; fi'" | tr -d '\r')"
note "world store existed before: $WSAVED"
NOW="$(device_ms)"; read -r H M <<< "$(device_hm $(( NOW + 3 * 3600000 )))"
AID="$(api_alarm "$H" "$M" "L1312")"; assert_ne "seed: an alarm" "" "$AID"
TID="$(api_timer 3600 "L1312")"; assert_ne "seed: a timer" "" "$TID"
adb shell am force-stop $PKG; sleep 1
adb shell "run-as $PKG sh -c 'echo [\\\"Europe/London\\\"] > files/world_clock.json'"
note "world store: $(adb shell run-as $PKG cat files/world_clock.json | tr -d '\r')"

one() { # label page row-tag menu-tag
  local l="$1"
  open_clock "$2"
  gdump "$ROW_DIR/$l-0.xml" > /dev/null
  set -- "$@" $(bounds "$ROW_DIR/$l-0.xml" "$3")
  assert_ne "$l: the seeded row is on screen ($3)" "" "${5:-}"
  [ -n "${5:-}" ] || return 0
  local x=$(( ($5 + $7) / 2 )) y=$(( ($6 + $8) / 2 ))
  adb shell input swipe $x $y $x $y 1200; sleep 1
  gdump "$ROW_DIR/$l-1-menu.xml" > /dev/null
  assert_eq "$l: the hold opened the menu ($4)" yes "$(has_node "$ROW_DIR/$l-1-menu.xml" "$4")"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  gdump "$ROW_DIR/$l-2-back.xml" > /dev/null
  assert_eq "$l: Back closed the menu" no "$(has_node "$ROW_DIR/$l-2-back.xml" "$4")"
  assert_eq "$l: ... and the Clock is still open" yes "$(has_node "$ROW_DIR/$l-2-back.xml" clock_root)"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  gdump "$ROW_DIR/$l-3-back.xml" > /dev/null
  assert_eq "$l (control): a second Back closes the Clock" no "$(has_node "$ROW_DIR/$l-3-back.xml" clock_root)"
}
one alarm alarm "alarm_row:$AID" alarm_row_menu
# The block's centre is its Play button, which takes a hold; the remaining-time text is not a button, so the hold
# reaches the block (drv1 held the centre and never opened the menu on either build).
one timer timer "timer_remaining:$TID" timer_row_menu
one world world_clock "clock_row:Europe/London" clock_row_menu

adb shell am force-stop $PKG; sleep 1
if [ "$WSAVED" = yes ]; then
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json.l1312 files/world_clock.json'"
else
  adb shell "run-as $PKG sh -c 'mv files/world_clock.json files/world_clock.json.l1312-seeded'"
fi
note "world store after: $(adb shell run-as $PKG cat files/world_clock.json 2>/dev/null | tr -d '\r')"
app_delete_all
assert_clock_empty "restore"
row_end
