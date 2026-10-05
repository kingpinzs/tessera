#!/usr/bin/env bash
# Development proof, build task 6 (A): the viewfinder opens on the virtual back camera; its mode list equals the list
# derived from `dumpsys media.camera`, with a line for every hidden mode; Y3's chrome is where R11 measured it (E19's
# Camera geometry, px / 3 = epx); a still lands in DCIM/Camera through the write layer; tap-to-focus writes its line;
# 2x zoom doubles the emulated scene. Not the gate (E7, E19 are the lead's). It removes the stills it takes.
. "$(dirname "$0")/head.sh"
row_begin A1 "viewfinder, modes, Y3 geometry, still, focus, zoom"
MAXI="$(max_id $IMAGES)"; COUNT0="$(count_rows $IMAGES)"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
open_camera
record "camera ready after (s)" "$(wait_camera "$MARK")"
assert_eq "CameraActivity resumed" "app.tileshell/.camera.CameraActivity" "$(top_activity)"
SVC="$(adb shell dumpsys media.camera | tr -d '\r')"
echo "$SVC" > "$ROW_DIR/media_camera.txt"
assert_contains "device 1 is open" "Device 1 is open" "$SVC"
assert_contains "opened by the shell" "Client package: app.tileshell" "$SVC"
assert_ne ":camera process" "" "$(adb shell pidof app.tileshell:camera | tr -d '\r')"
SLICE="$(cam_since "$MARK")"
assert_contains "devices line" "[camera] devices=1 front=absent" "$SLICE"

# --- the mode list, both directions -------------------------------------------------------------------------------
gdump "$ROW_DIR/vf.xml"; screencap "$ROW_DIR/vf.png"
ABI="$(adb shell getprop ro.product.cpu.abi < /dev/null | tr -d '\r')"; record "device ABI" "$ABI"
EXPECTED="$(echo "$SVC" | "$HERE/derive_modes.py" no "$ABI")"
SHOWN="$(grep -o 'resource-id="camera_mode:[a-z]*"' "$ROW_DIR/vf.xml" | sed 's/.*camera_mode:\([a-z]*\)"/\1/' | xargs)"
assert_eq "camera_mode nodes = the list derived from dumpsys media.camera" "$EXPECTED" "$SHOWN"
for m in photo video pro hdr slowmo panorama livingimages; do
  case " $EXPECTED " in
    *" $m "*) assert_absent "no unavailable line for the shown mode $m" "mode $m: unavailable" "$SLICE" ;;
    *) assert_contains "hidden mode $m has its line" "[camera] mode $m: unavailable (" "$SLICE" ;;
  esac
done
record "hidden lines" "$(echo "$SLICE" | grep -o 'mode [a-z.]*: unavailable ([^)]*)' | tr '\n' ';')"
assert_contains "photo is the selected mode" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:photo[^>]*>' "$ROW_DIR/vf.xml")"
assert_eq "no camera switch with no front camera" "no" "$(has_node "$ROW_DIR/vf.xml" camera_switch)"
assert_eq "no drawn status bar" "no" "$(has_node "$ROW_DIR/vf.xml" w10m_status_bar)"
assert_eq "the nav bar is drawn" "yes" "$(has_node "$ROW_DIR/vf.xml" w10m_nav_bar)"

# --- Y3 geometry (360 x 780 epx; nav top 732; H/2 = 390) ---------------------------------------------------------
NAV="$(bounds "$ROW_DIR/vf.xml" w10m_nav_bar)"; set -- ${NAV:-0 0 0 0}
assert_eq "nav bar 48 epx, its top at 732 epx" "144 2196" "$(( $4 - $2 )) $2"
epx_xy() { centre_epx "$ROW_DIR/vf.xml" "$1"; }
chk() { # name node expected-x expected-y tolerance
  local got; got="$(epx_xy "$2")"; set -- "$1" "$2" "$3" "$4" "$5" $got
  assert_within "$1 x" "$3" "${6:-none}" "$5"; assert_within "$1 y" "$4" "${7:-none}" "$5"
}
chk "preview centre" camera_preview 180 390 2
assert_eq "preview 4:3 fitted to the width (360 x 480 epx)" "360.0 480.0" "$(size_epx "$ROW_DIR/vf.xml" camera_preview)"
chk "shutter centre (W/2, nav top - 56)" camera_shutter 180 676 2
assert_eq "shutter 72 epx" "72.0 72.0" "$(size_epx "$ROW_DIR/vf.xml" camera_shutter)"
chk "next-mode disc (W/2 + 60, nav top - 36)" "camera_disc:video" 240 696 2
assert_eq "disc 32 epx" "32.0 32.0" "$(size_epx "$ROW_DIR/vf.xml" "camera_disc:video")"
chk "settings (W - 24, nav top - 27.9)" camera_settings 336 704.1 2
chk "roll (24.7, nav top - 28)" camera_roll 24.7 704 2
ITEMS="$(grep -o 'resource-id="camera_capsule:[a-z:]*"' "$ROW_DIR/vf.xml" | sed 's/.*capsule:\([a-z:]*\)"/\1/' | xargs)"
record "capsule items (photo)" "$ITEMS"
# The emulator's camera has a flash and no HDR extension: flash, timer, chevron — three items at -43.7 / +0.5 / +45.1.
assert_eq "capsule items in Y3's order, HDR left out" "flash timer more" "$ITEMS"
chk "capsule item 1" "camera_capsule:flash" 336 346.3 2
chk "capsule item 2" "camera_capsule:timer" 336 390.5 2
chk "capsule item 3" "camera_capsule:more" 336 435.1 2
CB="$(bounds "$ROW_DIR/vf.xml" camera_capsule)"; set -- ${CB:-0 0 0 0}
assert_within "capsule right edge 2 epx from the screen's" "358" "$(python3 -c "print($3/3)")" 2
assert_within "capsule left edge 45.5 epx from the screen's right" "314.5" "$(python3 -c "print($1/3)")" 2
assert_absent "no horizontal mode strip" "camera_mode_strip" "$(cat "$ROW_DIR/vf.xml")"
# The shutter's drawn rings: white at the centre... the glyph is black; a white pixel at r = 20, the #2B2B2B ring at r = 33.
python3 - "$ROW_DIR/vf.png" >> "$LOG" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB')
cx, cy = 540, 676 * 3
print("      shutter pixels: r=20 %s  r=33 %s  r=35.5 %s  r=40 %s" % tuple(im.getpixel((cx + int(r * 3), cy)) for r in (20, 33, 35.5, 40)))
PY
PIX="$(python3 -c "
from PIL import Image
im=Image.open('$ROW_DIR/vf.png').convert('RGB'); print(im.getpixel((540+60,2028)), im.getpixel((540+99,2028)), im.getpixel((540+106,2028)))")"
assert_eq "shutter: white fill, #2B2B2B ring at r 33, white ring at r 35.3" "(255, 255, 255) (43, 43, 43) (255, 255, 255)" "$PIX"

# --- a still -------------------------------------------------------------------------------------------------------
M2="$(ring_mark)"
tap_node "$ROW_DIR/vf.xml" camera_shutter; sleep 4
S2="$(cam_since "$M2")"
SAVED="$(echo "$S2" | grep -o 'saved content://[^ ]* [0-9]*x[0-9]*' | head -1)"
record "saved line" "$SAVED"
assert_ne "saved line present" "" "$SAVED"
assert_eq "images count +1" "$((COUNT0 + 1))" "$(count_rows $IMAGES)"
ROW1="$(shell_rows $IMAGES | head -1)"; record "new row" "$ROW1"
IFS='|' read -r ID NAME W H PENDING SIZE RPATH <<< "$ROW1"
assert_eq "relative_path" "DCIM/Camera/" "$RPATH"
assert_eq "is_pending" "0" "$PENDING"
assert_eq "row width x height = the saved line's" "${W}x${H}" "${SAVED##* }"
assert_contains "the saved line names the row" "/$ID " "$SAVED "
assert_contains "capture feedback motion line" "[motion] capture_feedback" "$S2"
adb shell "cat /sdcard/DCIM/Camera/$NAME" > "$ROW_DIR/still_1x.jpg"
EXIF="$(python3 "$HERE/exif_read.py" "$ROW_DIR/still_1x.jpg")"; record "EXIF" "$EXIF"
assert_contains "EXIF orientation" "Orientation=" "$EXIF"
assert_contains "EXIF DateTimeOriginal" "DateTimeOriginal=20" "$EXIF"

# --- tap to focus --------------------------------------------------------------------------------------------------
M3="$(ring_mark)"
adb shell input tap 540 1170; sleep 4
FOCUS="$(cam_since "$M3" | grep -o 'focus at [0-9]*,[0-9]*: [a-z ()]*' | head -1)"
record "focus line" "$FOCUS"
# The emulated camera lists autofocus modes (afAvailableModes 0 1 2 3 4), so the sweep runs and the lens locks. Its drawn
# scene never reads as in focus: the sweep ends in Camera2's NOT_FOCUSED_LOCKED, which the line says in brackets.
assert_contains "focus line at the tapped point: locked" "focus at 180,390: locked" "$FOCUS"
record "focus state in full" "${FOCUS#*: }"

# --- zoom: 2x doubles the emulated scene ---------------------------------------------------------------------------
gdump "$ROW_DIR/z0.xml"
assert_eq "zoom readout at 1x" "1×" "$(node_text "$ROW_DIR/z0.xml" camera_zoom)"
tap_node "$ROW_DIR/z0.xml" camera_zoom; sleep 2
gdump "$ROW_DIR/z1.xml"
assert_eq "zoom readout after one tap" "2×" "$(node_text "$ROW_DIR/z1.xml" camera_zoom)"
tap_node "$ROW_DIR/z1.xml" camera_shutter; sleep 4
NAME2="$(shell_rows $IMAGES | head -1 | cut -d'|' -f2)"
adb shell "cat /sdcard/DCIM/Camera/$NAME2" > "$ROW_DIR/still_2x.jpg"
RATIO="$(python3 "$HERE/zoom_ratio.py" "$ROW_DIR/still_1x.jpg" "$ROW_DIR/still_2x.jpg")"
record "scene scale 2x / 1x" "$RATIO"
assert_within "the 2x capture shows the scene at twice the size" "2.0" "$RATIO" 0.1

# --- restore -------------------------------------------------------------------------------------------------------
remove_rows_above $IMAGES "$MAXI"
assert_eq "images count restored" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "no pending row of the shell's" "" "$(shell_rows $IMAGES | awk -F'|' '$5==1')"
assert_eq "no crash" "" "$(adb logcat -d -t 500 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell; ensure_start
row_end
