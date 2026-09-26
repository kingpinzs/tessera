#!/usr/bin/env bash
# E6 Noise (phase 13 Acceptance E6, T13-17): in a 40 x 40 px patch of the ≡ pane over the Home page's black, placed by
# the measured-fill precondition (the oracle's B is (0,0,0) +- 1 there, so the persona, the greeting and every text are
# out of it), the per-pixel standard deviation is 1.5..4 levels and two screencaps 1 s apart are pixel-identical in the
# patch (the noise is deterministic); acrylic off (battery saver): standard deviation 0.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E6 "the material's noise: 1.5..4 levels, deterministic, gone when acrylic is off"
R=90

open_pane() { # out.xml
  dump_ui "$ROW_DIR/.page.xml"
  tap_node "$ROW_DIR/.page.xml" cortana_menu_button
  sleep 1.5
  dump_ui "$1"
}

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
show_start 6
ensure_start
cortana_assist
sleep 3
dump_ui "$ROW_DIR/home.xml"
assert_eq "Tess's Home page is showing" "yes" "$(has_node "$ROW_DIR/home.xml" cortana_menu_button)"
L="$(bounds "$ROW_DIR/home.xml" cortana_session | tr ' ' ',')"
screencap "$ROW_DIR/home-closed.png"
open_pane "$ROW_DIR/pane.xml"
assert_eq "the pane is open" "yes" "$(has_node "$ROW_DIR/pane.xml" acrylic:cortana_pane)"
set -- $(bounds "$ROW_DIR/pane.xml" cortana_pane); PR=$3
EXCL="$(python3 "$P13/dumpq.py" bounds_of "$ROW_DIR/pane.xml" cortana_pane_item_ | awk '{print $1","$2","$3","$4}' | tr '\n' ' ')"
# shellcheck disable=SC2086
read -r X Y DEV <<< "$(python3 "$P13/acrylic_check.py" flat --size=40 "$ROW_DIR/home-closed.png" 0,0,0 $R "$L" "10,450,$(( PR - 10 )),1750" $EXCL)"
assert_ne "precondition: a 40x40 patch whose oracle B is (0,0,0) +- 1 at every pixel (flat backdrop)" "" "${X:-}"
note "patch ($X,$Y) 40x40, oracle max |B - Bm| = $DEV"
screencap "$ROW_DIR/pane-1.png"
sleep 1
screencap "$ROW_DIR/pane-2.png"
SD="$(python3 "$P13/edge.py" std "$ROW_DIR/pane-1.png" "$X" "$Y" 40 40)"
assert_within "on: per-pixel standard deviation in 1.5..4 levels" 2.75 "$SD" 1.25
read -r DMEAN DMAX <<< "$(python3 "$P13/edge.py" diff "$ROW_DIR/pane-1.png" "$ROW_DIR/pane-2.png" "$X" "$Y" 40 40)"
note "two captures 1 s apart: mean |diff| $DMEAN, max $DMAX"
assert_eq "on: two screencaps 1 s apart are pixel-identical in the patch (max |diff|)" "0" "$DMAX"
read -r M1 M2 M3 <<< "$(python3 "$P13/acrylic_check.py" patch "$ROW_DIR/pane-1.png" "$X" "$Y" 40 40)"
record "on: the patch's mean (the noise is centred on the fill)" "($M1,$M2,$M3)"
adb shell input keyevent KEYCODE_BACK
sleep 1

battery_saver_on
open_pane "$ROW_DIR/pane-off.xml"
screencap "$ROW_DIR/pane-off.png"
ring_since "$BS_MARK" > "$ROW_DIR/slice-saver.txt"
assert_contains "off: acrylic=off reason=battery-saver" "[fluent] acrylic=off reason=battery-saver" "$(cat "$ROW_DIR/slice-saver.txt")"
assert_within "off: standard deviation 0 in the same patch" 0 "$(python3 "$P13/edge.py" std "$ROW_DIR/pane-off.png" "$X" "$Y" 40 40)" 0
adb shell input keyevent KEYCODE_BACK
sleep 1
cortana_close
assert_eq "battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"
show_start 3
row_end
