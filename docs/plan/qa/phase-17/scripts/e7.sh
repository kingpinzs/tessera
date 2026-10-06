#!/usr/bin/env bash
# Phase 17 E7 — still capture on the virtual back camera, the automatic controls and mode availability.
#
#   start   layout_restore of the baseline; Start dumped (the Camera row tile's bounds and face BEFORE); media_up with
#           no names (the census).
#   A open  a fresh :camera process on CameraActivity: `dumpsys media.camera` shows device 1 open, its client the shell
#           and the client's pid = pidof app.tileshell:camera; ONE device and no front camera (the dump's device count,
#           `[camera] devices=1 front=absent`, no camera_switch node).
#   G modes the six keys the row names are read from the dump and recorded; the expected list is derived from them
#           (dev-camera/scripts/derive_modes.py: Photo and Video always; Pro per r3 D10's gates; HDR iff CameraX
#           Extensions report it — read here from the device's own library list, `androidx.camera.extensions.impl`,
#           which is what CameraX asks; Slow motion iff CONSTRAINED_HIGH_SPEED_VIDEO; Panorama never on x86_64; Living
#           Images always) and the dump's camera_mode:<id> nodes equal it EXACTLY; every absent mode has its `[camera]
#           mode <x>: unavailable (<reason>)` line and no shown mode has one (both directions). The Pro dial's controls
#           (derive_modes.py --pro) equal the dial's rings.
#   B still tap camera_shutter → the row 2 s after the tap: images = census + 1, relative_path DCIM/Camera/, is_pending
#           0, width:height = the resolution chosen in settings (and the saved line's); the launcher's `[photos] refresh
#           (mediastore change)` line within 2 s of the row's date_added.
#   E focus autofocus support read from the dump first; a tap at the screen's centre → `[camera] focus at 180,390:
#           locked…` (supported) or `… unsupported` (not) — the expected value chosen from the dump. Change Log
#           2026-10-05 14:27 (8): the emulated camera ends its sweep not in focus, the line reads `locked (not in
#           focus)`; the row matches `locked`.
#   F zoom  max digital zoom read from the dump: above 1.0 → camera_zoom tapped to 2×, a capture, the emulated scene
#           2.0 ± 0.1 × the 1× capture's (zoom_ratio.py); 1.0 → no camera_zoom node and `[camera] mode zoom:
#           unavailable (max zoom 1.0)`.
#   C timer Time lapse off (asserted in settings); the timer set to 2 s; MARK = the device's ms clock immediately
#           before the tap; `[camera] timer 2s -> shutter`, `[camera] saved …` with saved.wall − MARK in 2000..3500,
#           exactly one new row, its date_added ≥ MARK / 1000 + 1 (corroborates). The timer is put back to off.
#   D grid  "Framing grid" → Rule of thirds: a screencap's pixel scan finds two vertical and two horizontal lines at
#           1/3 and 2/3 of camera_preview's bounds ± 2 px. Put back to Off.
#   H living "Capture living images" on, one still → ONE new image row in DCIM/Camera and no video row; the pulled
#           file's tail holds an MP4 (`tail -c 1M | grep -c ftyp` ≥ 1), its XMP reads MotionPhoto 1 (`strings | grep
#           MotionPhoto`, and motion_check.py) with the Container:Directory item length = the trailing MP4's size. The
#           setting is put off after (asserted).
#   end     c6 + ensure_start: the Camera row tile's bounds and face are what they were; media_down (the census's ids).
#
# No exiftool on this host: EXIF and XMP are read with Python and strings (the row's own alternative).
# Changes on the device: stills in DCIM/Camera (removed by media_down), three Camera settings (each put back).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
CAM_TILE="dock:slot:CAMERA"

cam_install E7
row_begin E7 "still capture, timer, grid, tap-to-focus, zoom, mode availability both ways, Living Images"
cam_preamble
D="$ROW_DIR"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
assert_eq "start: CAMERA granted (the baseline device holds it)" "true" "$(perm_granted CAMERA)"
gdump "$D/start-before.xml"; screencap "$D/start-before.png"
assert_eq "start: the dump is Start's" "yes" "$(has_node "$D/start-before.xml" start_page)"
TILE_B0="$(tile_field "$D/start-before.xml" "$CAM_TILE" bounds)"; TILE_T0="$(tile_field "$D/start-before.xml" "$CAM_TILE" texts)"
assert_ne "start: the Camera row tile is in the dump" "" "$TILE_B0"
media_up
adb emu geo fix -122.08 37.42 > "$D/geo.out" 2>&1; record "adb emu geo fix -122.08 37.42" "$(tr '\n' ' ' < "$D/geo.out")"

# ----------------------------------------------------------------------------------------------- A: open
log "--- A: CameraActivity on the virtual back camera"
fresh_camera
assert_eq "A: CameraActivity is resumed" "$CAMERA_ACTIVITY" "$(top_activity)"
SVC="$(media_camera)"; printf '%s\n' "$SVC" > "$D/media_camera.txt"
CPID="$(adb shell pidof app.tileshell:camera | tr -d '\r')"
assert_ne "A: the :camera process runs" "" "$CPID"
assert_contains "A: dumpsys media.camera — device 1 is open" "Device 1 is open" "$SVC"
assert_contains "A: … opened by the shell" "Client package: app.tileshell" "$SVC"
assert_contains "A: … by the :camera process (the client's pid = pidof app.tileshell:camera)" "Client PID: $CPID" "$SVC"
assert_contains "A: one camera device on this AVD" "Number of camera devices: 1" "$SVC"
record "A: the dump's facing lines" "$(printf '%s\n' "$SVC" | grep -i 'facing' | sort | uniq -c | xargs)"
OPEN_SLICE="$(cam_since "$OPEN_MARK")"; printf '%s\n' "$OPEN_SLICE" > "$D/A-slice.txt"
assert_contains "A: [camera] devices=1 front=absent" "[camera] devices=1 front=absent" "$OPEN_SLICE"
cgdump "$D/vf.xml"; screencap "$D/vf.png"
assert_eq "A: the viewfinder is the page (camera_shutter in the dump)" "yes" "$(has_node "$D/vf.xml" camera_shutter)"
assert_eq "A: no camera switch (no front camera)" "no" "$(has_node "$D/vf.xml" camera_switch)"

# ----------------------------------------------------------------------------------------------- G: mode availability
log "--- G: mode availability, both directions"
printf '%s\n' "$SVC" | python3 "$HERE/cam_facts.py" > "$D/facts.txt"
fact() { sed -n "s/^$1=//p" "$D/facts.txt"; }
for k in hardwareLevel capabilities aeModes aeCompensationRange minimumFocusDistance awbModes afModes maxDigitalZoom; do
  record "G: dumpsys media.camera $k" "$(fact $k)"
  assert_ne "G: the dump holds $k" "" "$(fact $k)"
done
ABI="$(adb shell getprop ro.product.cpu.abi < /dev/null | tr -d '\r')"; record "G: the device's ABI" "$ABI"
EXT_LIB="$(adb shell pm list libraries | tr -d '\r' | grep -c 'androidx.camera.extensions.impl')"
[ "$EXT_LIB" -ge 1 ] && HDR=yes || HDR=no
record "G: CameraX Extensions library on the device (androidx.camera.extensions.impl)" "$EXT_LIB → HDR expected: $HDR"
EXPECTED="$(printf '%s\n' "$SVC" | python3 "$CAM_DEV/derive_modes.py" "$HDR" "$ABI")"
SHOWN="$(ids_with "$D/vf.xml" camera_mode:)"
assert_eq "G: the camera_mode:<id> nodes equal the derived list exactly" "$EXPECTED" "$SHOWN"
assert_contains "G: Photo is in the derived list (always)" " photo " " $EXPECTED "
assert_contains "G: Video is in the derived list (always)" " video " " $EXPECTED "
assert_contains "G: Living Images is in the derived list (always)" " livingimages " " $EXPECTED "
[ "$ABI" = x86_64 ] && assert_absent "G: Panorama is never in the list on x86_64" " panorama " " $EXPECTED "
for m in photo video pro hdr slowmo panorama livingimages; do
  case " $EXPECTED " in
    *" $m "*) absent_in "G: the shown mode $m has no unavailable line" "mode $m: unavailable" "$OPEN_SLICE" ;;
    *) assert_contains "G: the absent mode $m has its line" "[camera] mode $m: unavailable (" "$OPEN_SLICE"
       assert_eq "G: … and no camera_mode:$m node" "no" "$(has_node "$D/vf.xml" "camera_mode:$m")" ;;
  esac
done
record "G: the unavailable lines" "$(printf '%s\n' "$OPEN_SLICE" | grep -o 'mode [a-z.]*: unavailable[^w]*' | tr '\n' ';')"
assert_eq "G: Photo is the selected mode" "true" "$(node_attr "$D/vf.xml" camera_mode:photo selected)"
PRO_WANT="$(printf '%s\n' "$SVC" | python3 "$CAM_DEV/derive_modes.py" --pro)"; record "G: the dial controls r3 D10 admits for this camera" "$PRO_WANT"
case " $EXPECTED " in
  *" pro "*)
    # shellcheck disable=SC2046
    set -- $(centre_px "$D/vf.xml" camera_shutter)
    adb shell input swipe "$1" "$2" $(( $1 - 240 )) "$2" 120; sleep 2
    cgdump "$D/dial.xml"; screencap "$D/dial.png"
    assert_eq "G: the dial opens (a flick left on the shutter)" "yes" "$(has_node "$D/dial.xml" camera_dial)"
    assert_eq "G: the dial's rings = the admitted controls, inner to outer" "$PRO_WANT" "$(ids_with "$D/dial.xml" camera_dial_icon:)"
    adb shell input keyevent KEYCODE_BACK; sleep 1; cgdump "$D/dial-closed.xml"
    assert_eq "G: Back closes the dial" "no yes" "$(has_node "$D/dial-closed.xml" camera_dial) $(has_node "$D/dial-closed.xml" camera_shutter)" ;;
  *) assert_eq "G: no control is admitted, so no Pro mode" "" "$PRO_WANT" ;;
esac

# ----------------------------------------------------------------------------------------------- B: a still
log "--- B: a still"
open_settings B "camera_set_value:size"
CHOSEN_TEXT="$(node_text "$D/B-set.xml" camera_set_value:size)"; CHOSEN="$(echo "$CHOSEN_TEXT" | grep -oE '[0-9]+x[0-9]+' | tail -1)"
record "B: the resolution chosen in settings" "$CHOSEN_TEXT"
assert_ne "B: the settings page names a resolution" "" "$CHOSEN"
close_settings
cgdump "$D/B-vf.xml"
XY="$(centre_px "$D/B-vf.xml" camera_shutter)"
MARK="$(ring_mark)"
# shellcheck disable=SC2086
adb shell input tap $XY
sleep 2
ROW1="$(shell_rows images | head -1)"; COUNT="$(media_count images)"
record "B: the newest row of the shell's, 2 s after the tap (id|name|w|h|pending|size|path|date_added)" "$ROW1"
assert_eq "B: images count +1" "$((CENSUS_IMAGES + 1))" "$COUNT"
assert_eq "B: relative_path" "DCIM/Camera/" "$(row_field "$ROW1" 7)"
assert_eq "B: is_pending 0 within 2 s" "0" "$(row_field "$ROW1" 5)"
assert_eq "B: width:height = the chosen resolution" "$CHOSEN" "$(row_field "$ROW1" 3)x$(row_field "$ROW1" 4)"
sleep 2
S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/B-slice.txt"
SAVED="$(printf '%s\n' "$S" | grep -o 'saved content://[^ ]* [0-9]*x[0-9]*' | head -1)"; record "B: the saved line" "$SAVED"
assert_contains "B: [camera] saved <uri> <w>x<h> names the row" "/$(row_field "$ROW1" 1) $CHOSEN" "$SAVED"
record "B: saved.wall − MARK (ms)" "$(( $(wall_of "$(cam_line "$MARK" '[camera] saved ')") - MARK ))"
# The Photos tile: the launcher's observer line within 2 s of the row's date_added (seconds; phase 01's threshold).
ADDED="$(row_field "$ROW1" 8)"
LSLICE="$(ring_since "$MARK" launcher)"; printf '%s\n' "$LSLICE" > "$D/B-launcher-slice.txt"
REFRESH_WALL="$(printf '%s\n' "$LSLICE" | grep -F '[photos] refresh (mediastore change)' | python3 -c '
import re, sys
added = int(sys.argv[1]) * 1000
for line in sys.stdin:
    m = re.search(r"wall=(\d+)", line)
    if m and int(m.group(1)) >= added: print(m.group(1)); break' "${ADDED:-0}")"
assert_contains "B: the launcher slice holds [photos] refresh (mediastore change)" "[photos] refresh (mediastore change)" "$LSLICE"
record "B: the refresh line's wall − date_added × 1000 (ms)" "$(( ${REFRESH_WALL:-0} - ${ADDED:-0} * 1000 ))"
if [ -n "$REFRESH_WALL" ] && [ $(( REFRESH_WALL - ADDED * 1000 )) -le 2000 ]; then
  _verdict PASS "B: the Photos tile's refresh follows within 2 s of date_added" "$(( REFRESH_WALL - ADDED * 1000 )) ms"
else
  _verdict FAIL "B: the Photos tile's refresh follows within 2 s of date_added" "refresh wall=[${REFRESH_WALL:-none}] date_added=[$ADDED]"
fi
pull_dcim "$(row_field "$ROW1" 2)" "$D/still_1x.jpg"
EXIF="$(python3 "$CAM_DEV/exif_read.py" "$D/still_1x.jpg" 2>&1)"; record "B: the pulled still" "$EXIF"
assert_contains "B: the pulled file decodes at the chosen resolution" "size=$CHOSEN" "$EXIF"

# ----------------------------------------------------------------------------------------------- E: tap to focus
log "--- E: tap to focus"
AF="$(fact autofocus)"; record "E: autofocus supported, from the dump (afModes: $(fact afModes))" "$AF"
[ "$AF" = yes ] && FOCUS_WANT="locked" || FOCUS_WANT="unsupported"
MARK="$(ring_mark)"
adb shell input tap 540 1170; sleep 4
FOCUS="$(cam_since "$MARK" | grep -o 'focus at [0-9]*,[0-9]*: [a-z ()]*' | head -1)"; record "E: the focus line" "$FOCUS"
# Change Log 2026-10-05 14:27 (8): the sweep on this emulated camera ends `locked (not in focus)`; `locked` is matched.
assert_contains "E: [camera] focus at 180,390: $FOCUS_WANT (the value the dump chose)" "focus at 180,390: $FOCUS_WANT" "$FOCUS"

# ----------------------------------------------------------------------------------------------- F: zoom
log "--- F: zoom"
ZOOM="$(fact zoom)"; record "F: digital zoom, from the dump (maxDigitalZoom: $(fact maxDigitalZoom))" "$ZOOM"
cgdump "$D/z0.xml"
if [ "$ZOOM" = yes ]; then
  assert_eq "F: the zoom readout starts at 1×" "1×" "$(node_text "$D/z0.xml" camera_zoom)"
  tap_node "$D/z0.xml" camera_zoom; sleep 2
  cgdump "$D/z1.xml"
  assert_eq "F: one tap sets 2×" "2×" "$(node_text "$D/z1.xml" camera_zoom)"
  tap_node "$D/z1.xml" camera_shutter; sleep 4
  ROW2="$(shell_rows images | head -1)"
  assert_ne "F: the 2× capture is a new row" "$(row_field "$ROW1" 1)" "$(row_field "$ROW2" 1)"
  pull_dcim "$(row_field "$ROW2" 2)" "$D/still_2x.jpg"
  RATIO="$(python3 "$CAM_DEV/zoom_ratio.py" "$D/still_1x.jpg" "$D/still_2x.jpg" 2> "$D/zoom_ratio.err")"
  record "F: the scene's scale, 2× capture / 1× capture" "$RATIO ($(cat "$D/zoom_ratio.err"))"
  assert_within "F: the emulated scene is 2.0 ± 0.1 × its 1× width" "2.0" "$RATIO" 0.1
else
  assert_eq "F: no zoom control (max digital zoom 1.0)" "no" "$(has_node "$D/z0.xml" camera_zoom)"
  assert_contains "F: [camera] mode zoom: unavailable (max zoom 1.0)" "[camera] mode zoom: unavailable (max zoom 1.0)" "$OPEN_SLICE"
fi

# ----------------------------------------------------------------------------------------------- C: the 2-s timer
log "--- C: the 2-s timer, Time lapse off"
# A fresh process: zoom back at 1×, the capsule as it starts.
fresh_camera
open_settings C "camera_set:timelapse"
assert_eq "C: Time lapse is off" "false" "$(node_attr "$D/C-set.xml" camera_set:timelapse checked)"
close_settings
cgdump "$D/C-vf.xml"
assert_eq "C: the timer starts off (no seconds on the toggle)" "no" "$(has_node "$D/C-vf.xml" camera_capsule_value:timer)"
COUNT0="$(media_count images)"
tap_node "$D/C-vf.xml" "camera_capsule:timer"; sleep 2
cgdump "$D/C-armed.xml"
assert_eq "C: the toggle shows 2" "2" "$(node_text "$D/C-armed.xml" camera_capsule_value:timer)"
XY="$(centre_px "$D/C-armed.xml" camera_shutter)"
MARK="$(adb shell date +%s%3N | tr -d '\r')"
# shellcheck disable=SC2086
adb shell input tap $XY
sleep 1; cgdump "$D/C-count.xml"
assert_eq "C: the countdown is on screen during the wait" "yes" "$(has_node "$D/C-count.xml" camera_countdown)"
sleep 5
S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/C-slice.txt"
assert_contains "C: [camera] timer 2s -> shutter" "[camera] timer 2s -> shutter" "$S"
assert_contains "C: [camera] saved <uri> …" "[camera] saved content://" "$S"
SAVED_WALL="$(wall_of "$(printf '%s\n' "$S" | grep -F '[camera] saved ' | head -1)")"
DELTA=$(( ${SAVED_WALL:-0} - MARK )); record "C: saved.wall − MARK (ms)" "$DELTA"
if [ "$DELTA" -ge 2000 ] && [ "$DELTA" -le 3500 ]; then _verdict PASS "C: saved.wall − MARK ≥ 2000 and ≤ 3500" "$DELTA"; else _verdict FAIL "C: saved.wall − MARK ≥ 2000 and ≤ 3500" "$DELTA"; fi
assert_eq "C: exactly one new row" "$((COUNT0 + 1))" "$(media_count images)"
ROWT="$(shell_rows images | head -1)"; record "C: the new row" "$ROWT"
if [ "$(row_field "$ROWT" 8)" -ge $(( MARK / 1000 + 1 )) ] 2>/dev/null; then
  _verdict PASS "C: the row's date_added ≥ MARK / 1000 + 1 (corroborates)" "$(row_field "$ROWT" 8) ≥ $(( MARK / 1000 + 1 ))"
else
  _verdict FAIL "C: the row's date_added ≥ MARK / 1000 + 1 (corroborates)" "$(row_field "$ROWT" 8) < $(( MARK / 1000 + 1 ))"
fi
# 2 s → 5 s → off: the timer put back.
cgdump "$D/C-t0.xml"; tap_node "$D/C-t0.xml" "camera_capsule:timer"; sleep 1.5
cgdump "$D/C-t1.xml"; assert_eq "C: the next tap shows 5 (W10M's 2 s, 5 s, off)" "5" "$(node_text "$D/C-t1.xml" camera_capsule_value:timer)"
tap_node "$D/C-t1.xml" "camera_capsule:timer"; sleep 1.5
cgdump "$D/C-t2.xml"; assert_eq "C: restore — the timer is off again" "no" "$(has_node "$D/C-t2.xml" camera_capsule_value:timer)"

# ----------------------------------------------------------------------------------------------- D: the grid
log "--- D: the framing grid"
set_combo grid 1 D-on
assert_eq "D: Framing grid reads Rule of thirds" "Rule of thirds" "$COMBO_NOW"
cgdump "$D/grid.xml"; screencap "$D/grid.png"
assert_eq "D: back on the viewfinder (camera_shutter, no settings page)" "yes no" "$(has_node "$D/grid.xml" camera_shutter) $(has_node "$D/grid.xml" camera_settings_page)"
assert_eq "D: the grid node is drawn" "yes" "$(has_node "$D/grid.xml" camera_grid)"
# shellcheck disable=SC2046
GRIDL="$(python3 - "$D/grid.png" $(bounds "$D/grid.xml" camera_preview) <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB'); l, t, r, b = map(int, sys.argv[2:6])
def white(p): return min(p) >= 250
# A grid line is 1 epx of the line colour (white) with a thin dark edge each side: a run of 2-4 consecutive columns
# (rows) white over (nearly) all of the preview — sampled every 7 px, clear of the capsule and the zoom control at the
# sides — with a column (row) that is NOT white directly before and after the run. The emulated scene's white sky is a
# long white run and so is not a line. (The builder's scan, dev-camera/scripts/a2_timer_grid_settings.sh.)
ys = range(t + 4, b - 4, 7); xs = range(l + 170, r - 170, 7)
def col_white(x): return sum(white(im.getpixel((x, y))) for y in ys) >= 0.98 * len(ys)
def row_white(y): return sum(white(im.getpixel((x, y))) for x in xs) >= 0.98 * len(xs)
def runs(lo, hi, test):
    out, run = [], []
    for v in range(lo, hi):
        if test(v): run.append(v)
        else:
            if 2 <= len(run) <= 4 and run[0] > lo: out.append(sum(run) / len(run))
            run = []
    return out
cols = runs(l, r, col_white); rows = runs(t, b, row_white)
print('x=' + ','.join('%.1f' % (g - l) for g in cols) + ' y=' + ','.join('%.1f' % (g - t) for g in rows) + ' thirds x=%.1f,%.1f y=%.1f,%.1f' % ((r - l) / 3, 2 * (r - l) / 3, (b - t) / 3, 2 * (b - t) / 3))
PY
)"; record "D: grid lines found in the preview (px from its left / top) and its thirds" "$GRIDL"
GX="$(echo "$GRIDL" | sed 's/x=\([^ ]*\) .*/\1/')"; GY="$(echo "$GRIDL" | sed 's/.* y=\([^ ]*\) thirds.*/\1/')"
WX="$(echo "$GRIDL" | sed 's/.*thirds x=\([^ ]*\) .*/\1/')"; WY="$(echo "$GRIDL" | sed 's/.*thirds x=[^ ]* y=\(.*\)/\1/')"
assert_eq "D: two vertical and two horizontal lines, no more" "2 2" "$(echo "$GX" | tr ',' '\n' | grep -c .) $(echo "$GY" | tr ',' '\n' | grep -c .)"
assert_within "D: vertical line at 1/3 of the preview ± 2 px" "$(echo "$WX" | cut -d, -f1)" "$(echo "$GX" | cut -d, -f1)" 2
assert_within "D: vertical line at 2/3 ± 2 px" "$(echo "$WX" | cut -d, -f2)" "$(echo "$GX" | cut -d, -f2)" 2
assert_within "D: horizontal line at 1/3 ± 2 px" "$(echo "$WY" | cut -d, -f1)" "$(echo "$GY" | cut -d, -f1)" 2
assert_within "D: horizontal line at 2/3 ± 2 px" "$(echo "$WY" | cut -d, -f2)" "$(echo "$GY" | cut -d, -f2)" 2
set_combo grid 0 D-off
assert_eq "D: restore — Framing grid back to Off" "Off" "$COMBO_NOW"

# ----------------------------------------------------------------------------------------------- H: Living Images
log "--- H: Living Images"
cgdump "$D/H-vf0.xml"
assert_eq "H: living images starts off" "false" "$(node_attr "$D/H-vf0.xml" camera_mode:livingimages selected)"
set_toggle living on H-on
assert_eq "H: \"Capture living images\" is on in settings" "true" "$TOGGLE_NOW"
cgdump "$D/H-vf1.xml"
assert_eq "H: camera_mode:livingimages is selected" "true" "$(node_attr "$D/H-vf1.xml" camera_mode:livingimages selected)"
ICOUNT0="$(media_count images)"; VCOUNT0="$(media_count video)"
sleep 2                                   # a second of viewfinder before the shutter: the clip's content
MARK="$(ring_mark)"
tap_node "$D/H-vf1.xml" camera_shutter; sleep 7
S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/H-slice.txt"
SAVED="$(printf '%s\n' "$S" | grep -o 'saved content://.*' | head -1 | sed 's/ *wall=.*//')"; record "H: the saved line" "$SAVED"
assert_contains "H: saved as a living image" "living image clip=" "$SAVED"
assert_eq "H: one still → ONE new image row" "$((ICOUNT0 + 1))" "$(media_count images)"
assert_eq "H: … and no video row beside it" "$VCOUNT0" "$(media_count video)"
ROWL="$(shell_rows images | head -1)"; record "H: the new row" "$ROWL"
assert_eq "H: in DCIM/Camera, published" "DCIM/Camera/ 0" "$(row_field "$ROWL" 7) $(row_field "$ROWL" 5)"
pull_dcim "$(row_field "$ROWL" 2)" "$D/living.jpg"
assert_eq "H: the pulled file is the row's size (an unread file cannot pass)" "$(row_field "$ROWL" 6)" "$(stat -c%s "$D/living.jpg")"
FTYP="$(tail -c 1M "$D/living.jpg" | grep -c ftyp)"
if [ "$FTYP" -ge 1 ]; then _verdict PASS "H: tail -c 1M | grep -c ftyp ≥ 1" "$FTYP"; else _verdict FAIL "H: tail -c 1M | grep -c ftyp ≥ 1" "$FTYP"; fi
assert_contains "H: strings | grep MotionPhoto reads 1" 'Camera:MotionPhoto="1"' "$(strings "$D/living.jpg" | grep -m1 -o 'Camera:MotionPhoto="1"')"
FACTS="$(python3 "$CAM_DEV/motion_check.py" "$D/living.jpg" "$D/living-clip.mp4")"; record "H: the container (XMP parsed, the trailing MP4 measured)" "$FACTS"
assert_contains "H: XMP MotionPhoto = 1" "MotionPhoto=1 " "$FACTS"
assert_contains "H: the Container:Directory item length = the trailing MP4's size" "length_matches=yes" "$FACTS"
assert_eq "H: the trailing MP4 decodes: one video stream" "1 video" "$(streams "$D/living-clip.mp4")"
set_toggle living off H-off
assert_eq "H: restore — the setting is off after" "false" "$TOGGLE_NOW"
cgdump "$D/H-vf2.xml"
assert_eq "H: … and camera_mode:livingimages is not selected" "false" "$(node_attr "$D/H-vf2.xml" camera_mode:livingimages selected)"

# ----------------------------------------------------------------------------------------------- end
log "--- end: the Camera row tile, the census"
no_crash
assert_eq "end: no pending row of the shell's" "0" "$(pending_rows)"
c6; ensure_start
gdump "$D/start-after.xml"; screencap "$D/start-after.png"
assert_eq "end: the dump is Start's" "yes" "$(has_node "$D/start-after.xml" start_page)"
assert_eq "end: the Camera row tile's bounds are unchanged" "$TILE_B0" "$(tile_field "$D/start-after.xml" "$CAM_TILE" bounds)"
assert_eq "end: … its texts are unchanged" "$TILE_T0" "$(tile_field "$D/start-after.xml" "$CAM_TILE" texts)"
assert_eq "end: … no badge and no now-playing strip on it" "badge=no controls=no" "$(tile_field "$D/start-after.xml" "$CAM_TILE" badge) $(tile_field "$D/start-after.xml" "$CAM_TILE" controls)"
FACE="$(python3 - "$D/start-before.png" "$D/start-after.png" "$TILE_B0" <<'PY'
import sys
from PIL import Image, ImageChops
box = tuple(int(v) for v in sys.argv[3].split(','))
a = Image.open(sys.argv[1]).convert('RGB').crop(box); b = Image.open(sys.argv[2]).convert('RGB').crop(box)
diff = ImageChops.difference(a, b).convert('L')
changed = sum(1 for v in diff.getdata() if v > 4)
print('%d of %d pixels differ by more than 4' % (changed, a.width * a.height))
PY
)"
assert_eq "end: … its face is unchanged (the tile's pixels before and after, colours ± 4)" "0 of" "$(echo "$FACE" | cut -d' ' -f1-2)"
record "end: the face comparison" "$FACE"
media_down
row_end
