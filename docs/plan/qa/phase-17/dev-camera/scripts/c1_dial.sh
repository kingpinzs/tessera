#!/usr/bin/env bash
# Development proof, build task 6a (C): the pro dial on the emulator, whose camera admits all five controls. Flicking
# the shutter left opens the five-ring view: its arcs fitted from a screencap, each ring's icon on its ring, each value
# label at W/2 30.3 epx above its ring's top, the shutter raised to 74.75 (Y4; E19's dial sub-row; px / 3 = epx). A value
# set on a ring reaches the capture's EXIF. One control picked in the capsule shows one arc. Not the gate.
. "$(dirname "$0")/head.sh"
row_begin C1 "pro dial: geometry, values, EXIF, single arc"
MAXI="$(max_id $IMAGES)"; COUNT0="$(count_rows $IMAGES)"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
open_camera
record "camera ready after (s)" "$(wait_camera "$MARK")"
SVC="$(adb shell dumpsys media.camera | tr -d '\r')"
WANT="$(echo "$SVC" | "$HERE/derive_modes.py" --pro)"; record "controls r3 D10 admits for this camera" "$WANT"
gdump "$ROW_DIR/vf.xml"
XY="$(centre_px "$ROW_DIR/vf.xml" camera_shutter)"; set -- $XY
M1="$(ring_mark)"
adb shell input swipe $1 $2 $(( $1 - 240 )) $2 120
sleep 2
gdump "$ROW_DIR/dial.xml"; screencap "$ROW_DIR/dial.png"
assert_eq "the dial is open" "yes" "$(has_node "$ROW_DIR/dial.xml" camera_dial)"
SHOWN="$(grep -o 'resource-id="camera_dial_icon:[a-z]*"' "$ROW_DIR/dial.xml" | sed 's/.*icon:\([a-z]*\)"/\1/' | xargs)"
assert_eq "rings shown = the admitted controls, inner to outer" "$WANT" "$SHOWN"
assert_contains "pro is marked on" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:pro"[^>]*>' "$ROW_DIR/dial.xml")"
MOTION="$(cam_since "$M1" | grep -o '\[motion\] dial_open .*' | head -1)"; record "motion" "$MOTION"
assert_within "dial-open sweep 333 ms (+/- 33)" 333 "$(echo "$MOTION" | grep -o 'settle=[0-9]*' | cut -d= -f2)" 33
record "label fade" "$(cam_since "$M1" | grep -o '\[motion\] dial_labels .*' | head -1)"
set -- $(centre_epx "$ROW_DIR/dial.xml" camera_shutter)
assert_within "the shutter stays at W/2" 180 "${1:-0}" 1; assert_within "the shutter rises to 74.75 epx above the nav top" 657.25 "${2:-0}" 1
# The arcs, fitted from the screencap: along rays from (W/2, nav top) the ring ink peaks at each radius.
FIT="$(python3 "$HERE/dial_fit.py" "$ROW_DIR/dial.png")"; record "ring radii fitted from pixels (epx)" "$FIT"
i=1; for want in 130.5 195.4 260.3 325.3 390.2; do assert_within "ring $i radius" "$want" "$(echo "$FIT" | cut -d' ' -f$i)" 1; i=$((i + 1)); done
# Icons on their own rings; labels centred at W/2, 30.3 above each ring's top (nav top 732).
k=0
for c in exposure shutter iso focus wb; do
  R="$(python3 -c "print([130.5,195.4,260.3,325.3,390.2][$k])")"
  set -- $(centre_epx "$ROW_DIR/dial.xml" "camera_dial_icon:$c")
  assert_within "$c icon lies on its ring (distance from the centre)" "$R" "$(python3 -c "import math; print(math.hypot(${1:-0}-180, ${2:-0}-732))")" 1
  [ "$k" -gt 0 ] && assert_within "$c icon at x = 56" 56 "${1:-0}" 1
  set -- $(centre_epx "$ROW_DIR/dial.xml" "camera_dial_value:$c")
  assert_within "$c label centred at W/2" 180 "${1:-0}" 1
  assert_within "$c label 30.3 above its ring's top" "$(python3 -c "print(732-$R-30.3)")" "${2:-0}" 1
  k=$((k + 1))
done
set -- $(centre_epx "$ROW_DIR/dial.xml" "camera_dial_icon:exposure")
assert_within "exposure icon at W/2" 180 "${1:-0}" 1; assert_within "exposure icon on the innermost ring's top" 601.5 "${2:-0}" 1
assert_eq "values start automatic" "0.0 auto auto auto auto" "$(for c in exposure shutter iso focus wb; do node_text "$ROW_DIR/dial.xml" "camera_dial_value:$c"; done | xargs)"

# --- set ISO, shutter and white balance on their rings; the capture's EXIF carries them --------------------------
tap_ring() { python3 -c "import math; r=$1; a=math.radians($2); print(int((180+r*math.cos(a))*3), int((732-r*math.sin(a))*3))"; }
adb shell input tap $(tap_ring 260.3 60); sleep 1
adb shell input tap $(tap_ring 195.4 75); sleep 1
adb shell input tap $(tap_ring 390.2 80); sleep 1.5
gdump "$ROW_DIR/dial2.xml"
ISO="$(node_text "$ROW_DIR/dial2.xml" camera_dial_value:iso)"; SH="$(node_text "$ROW_DIR/dial2.xml" camera_dial_value:shutter)"; WB="$(node_text "$ROW_DIR/dial2.xml" camera_dial_value:wb)"
record "set on the rings" "ISO $ISO, shutter $SH, WB $WB"
assert_ne "ISO is manual" "auto" "$ISO"; assert_ne "shutter is manual" "auto" "$SH"; assert_ne "WB is a preset" "auto" "$WB"
tap_node "$ROW_DIR/dial2.xml" camera_shutter; sleep 4
NAME="$(shell_rows $IMAGES | head -1 | cut -d'|' -f2)"
adb shell "cat /sdcard/DCIM/Camera/$NAME" > "$ROW_DIR/pro.jpg"
EXIF="$(python3 "$HERE/exif_read.py" "$ROW_DIR/pro.jpg")"; record "EXIF of the manual capture" "$EXIF"
assert_contains "EXIF ISO is the ring's value" "ISOSpeedRatings=$ISO " "$EXIF"
WANT_T="$(python3 -c "s='$SH'; print(1/float(s[2:]) if s.startswith('1/') else float(s.split()[0]))")"
GOT_T="$(echo "$EXIF" | grep -o 'ExposureTime=[0-9.e-]*' | cut -d= -f2)"
assert_within "EXIF exposure time is the ring's value" "$WANT_T" "${GOT_T:-0}" "$(python3 -c "print($WANT_T*0.5)")"
assert_contains "EXIF white balance = manual" "WhiteBalance=1" "$EXIF"
# Exposure compensation is disabled while ISO or shutter is manual (r3 D10): a touch on its ring changes nothing.
adb shell input tap $(tap_ring 130.5 45); sleep 1; gdump "$ROW_DIR/dial3.xml"
assert_eq "EV unchanged while ISO / shutter are manual" "0.0" "$(node_text "$ROW_DIR/dial3.xml" camera_dial_value:exposure)"
# A tap on a ring's icon returns the control to automatic.
tap_node "$ROW_DIR/dial3.xml" "camera_dial_icon:iso"; sleep 0.7; tap_node "$ROW_DIR/dial3.xml" "camera_dial_icon:shutter"; sleep 0.7; tap_node "$ROW_DIR/dial3.xml" "camera_dial_icon:wb"; sleep 1
adb shell input tap $(tap_ring 130.5 45); sleep 1; gdump "$ROW_DIR/dial4.xml"
assert_eq "back to automatic" "auto auto auto" "$(for c in shutter iso wb; do node_text "$ROW_DIR/dial4.xml" "camera_dial_value:$c"; done | xargs)"
assert_ne "EV moves once exposure is automatic again" "0.0" "$(node_text "$ROW_DIR/dial4.xml" camera_dial_value:exposure)"

# --- one control alone: one arc of r 130.25, its icon at 139 degrees ---------------------------------------------
adb shell input keyevent KEYCODE_BACK; sleep 1; gdump "$ROW_DIR/c0.xml"
assert_eq "Back closes the dial" "no" "$(has_node "$ROW_DIR/c0.xml" camera_dial)"
tap_node "$ROW_DIR/c0.xml" "camera_capsule:more"; sleep 1; gdump "$ROW_DIR/c1.xml"
record "expanded capsule" "$(grep -o 'resource-id="camera_capsule:[a-z:]*"' "$ROW_DIR/c1.xml" | sed 's/.*capsule:\([a-z:]*\)"/\1/' | xargs)"
tap_node "$ROW_DIR/c1.xml" "camera_capsule:pro:focus"; sleep 1; gdump "$ROW_DIR/c2.xml"; screencap "$ROW_DIR/single.png"
assert_eq "one ring only" "focus" "$(grep -o 'resource-id="camera_dial_icon:[a-z]*"' "$ROW_DIR/c2.xml" | sed 's/.*icon:\([a-z]*\)"/\1/' | xargs)"
set -- $(centre_epx "$ROW_DIR/c2.xml" "camera_dial_icon:focus")
assert_within "single arc: icon on r 130.25" 130.25 "$(python3 -c "import math; print(math.hypot(${1:-0}-180, ${2:-0}-732))")" 1
assert_within "single arc: icon at 139 degrees" 139 "$(python3 -c "import math; print(math.degrees(math.atan2(732-${2:-0}, ${1:-0}-180)))")" 1
assert_within "single arc radius fitted from pixels" 130.25 "$(python3 "$HERE/dial_fit.py" "$ROW_DIR/single.png" | cut -d' ' -f1)" 1
set -- $(centre_epx "$ROW_DIR/c2.xml" camera_shutter)
assert_within "single arc: the shutter stays at 56 above the nav top" 676 "${2:-0}" 1

# --- restore -------------------------------------------------------------------------------------------------------
remove_rows_above $IMAGES "$MAXI"
assert_eq "images count restored" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell; ensure_start
row_end
