#!/usr/bin/env bash
# Development proof, build task 6 (A): the 2-s timer's window, the framing grid's lines in a screencap, the settings
# page's rows and measured geometry (r11/camera.md 1.7; E19), and the mode-switch motion. Not the gate. It removes the
# stills it takes and puts the grid setting back.
. "$(dirname "$0")/head.sh"
row_begin A2 "timer, grid, settings page, mode switch"
MAXI="$(max_id $IMAGES)"; COUNT0="$(count_rows $IMAGES)"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
open_camera
record "camera ready after (s)" "$(wait_camera "$MARK")"
gdump "$ROW_DIR/vf.xml"

# --- timer: 2 s (Time lapse off) -----------------------------------------------------------------------------------
TM0="$(ring_mark)"
tap_node "$ROW_DIR/vf.xml" "camera_capsule:timer"; sleep 0.6
gdump "$ROW_DIR/t1.xml"
assert_eq "timer toast" "2-second timer" "$(node_text "$ROW_DIR/t1.xml" camera_toast)"
sleep 1.5
XY="$(centre_px "$ROW_DIR/vf.xml" camera_shutter)"
TM="$(adb shell date +%s%3N | tr -d '\r')"
adb shell input tap $XY
sleep 1; gdump "$ROW_DIR/t2.xml"
assert_eq "countdown ring on screen during the wait" "yes" "$(has_node "$ROW_DIR/t2.xml" camera_countdown)"
sleep 4
TS="$(cam_since "$TM")"
assert_contains "timer line" "[camera] timer 2s -> shutter" "$TS"
SAVED_WALL="$(echo "$TS" | grep -F '[camera] saved ' | head -1 | grep -o 'wall=[0-9]*' | cut -d= -f2)"
DELTA=$(( ${SAVED_WALL:-0} - TM )); record "saved.wall - MARK (ms)" "$DELTA"
if [ "$DELTA" -ge 2000 ] && [ "$DELTA" -le 3500 ]; then _verdict PASS "saved 2000..3500 ms after the tap" "$DELTA"; else _verdict FAIL "saved 2000..3500 ms after the tap" "$DELTA"; fi
assert_eq "exactly one new row" "$((COUNT0 + 1))" "$(count_rows $IMAGES)"
# 5 s, then off: the toggle's three labels (Y3).
# The toast lives for about a second, so the next two labels are read from the ring (the first was read on screen).
gdump "$ROW_DIR/t3.xml"; M5="$(ring_mark)"
tap_node "$ROW_DIR/t3.xml" "camera_capsule:timer"; sleep 1.5; gdump "$ROW_DIR/t4.xml"
assert_eq "the toggle shows 5" "5" "$(node_text "$ROW_DIR/t4.xml" "camera_capsule_value:timer")"
tap_node "$ROW_DIR/t3.xml" "camera_capsule:timer"; sleep 1.5; gdump "$ROW_DIR/t5.xml"
assert_eq "the toggle shows no seconds when off" "no" "$(has_node "$ROW_DIR/t5.xml" "camera_capsule_value:timer")"
assert_eq "the toggle's three labels, in order" "2-second timer|5-second timer|Timer off|" "$(cam_since "$TM0" | grep -oE 'toast: (2-second timer|5-second timer|Timer off)' | sed 's/toast: //' | tr '\n' '|')"

# --- the settings page (1.7; E19) ----------------------------------------------------------------------------------
tap_node "$ROW_DIR/vf.xml" camera_settings; sleep 1.5
gdump "$ROW_DIR/set.xml"; screencap "$ROW_DIR/set.png"
assert_eq "settings page" "yes" "$(has_node "$ROW_DIR/set.xml" camera_settings_page)"
assert_eq "header text" "SETTINGS" "$(node_text "$ROW_DIR/set.xml" camera_settings_title)"
TEXTS="$(grep -o 'text="[^"]*"' "$ROW_DIR/set.xml" | sed 's/text="//;s/"$//' | grep -v '^$' | tr '\n' '|')"
record "page texts" "$TEXTS"
for t in "Aspect ratio" "Framing grid" "Image size for main camera" "Time lapse" "Capture living images" "Video recording" "Photos" "Videos" "About this app"; do
  assert_contains "row: $t" "|$t|" "|$TEXTS"
done
for t in "Lenses" "OneDrive" "Related settings"; do assert_absent "no $t row" "$t" "$TEXTS"; done
# The header's cap height 16.5-16.75 epx at x 12: the ink of "SETTINGS" in the screencap.
INK="$(python3 - "$ROW_DIR/set.png" $(bounds "$ROW_DIR/set.xml" camera_settings_title) <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('L'); l, t, r, b = map(int, sys.argv[2:6])
box = im.crop((l, t, r, b)).point(lambda v: 255 if v > 128 else 0).getbbox()
print('%.2f %.2f %.2f' % ((l + box[0]) / 3, (t + box[1]) / 3, (box[3] - box[1]) / 3))
PY
)"; set -- $INK; record "SETTINGS ink: left, cap top, cap height (epx)" "$INK"
assert_within "header left 12 epx" 12 "$1" 1
assert_within "header cap top 24.5 epx" 24.5 "$2" 1
assert_within "header cap height 16.5-16.75 epx" 16.6 "$3" 1
set -- $(bounds "$ROW_DIR/set.xml" "camera_set:aspect")
assert_eq "combo box 32 epx tall, x 12 -> W - 12" "36 1044 96" "$1 $3 $(( $4 - $2 ))"
A_Y="$(bounds "$ROW_DIR/set.xml" "camera_set_label:aspect" | cut -d' ' -f2)"; G_Y="$(bounds "$ROW_DIR/set.xml" "camera_set_label:grid" | cut -d' ' -f2)"; S_Y="$(bounds "$ROW_DIR/set.xml" "camera_set_label:size" | cut -d' ' -f2)"
assert_eq "label -> label pitch 80 epx (twice)" "240 240" "$(( G_Y - A_Y )) $(( S_Y - G_Y ))"
set -- $(centre_epx "$ROW_DIR/set.xml" "camera_set_chevron:aspect")
assert_within "chevron 22.25 epx from the right" 337.75 "${1:-0}" 1
# The 2-epx border: its colour on the box's top edge, and the page behind it one epx inside.
BORDER="$(python3 -c "
from PIL import Image
im=Image.open('$ROW_DIR/set.png').convert('RGB'); l,t,r,b=$(bounds "$ROW_DIR/set.xml" "camera_set:aspect" | tr ' ' ',')
print(im.getpixel((l+300,t+1)), im.getpixel((l+300,t+4)), im.getpixel((l+300,t+7))[0] < 80)")"
record "border pixels at +1, +4 px and inside at +7 px" "$BORDER"
assert_contains "2-epx border, #828282" "(130, 130, 130) (130, 130, 130) True" "$BORDER"
# Open list: 44-epx pitch, the current item accent-filled.
tap_node "$ROW_DIR/set.xml" "camera_set:grid"; sleep 1
gdump "$ROW_DIR/list.xml"; screencap "$ROW_DIR/list.png"
I0="$(bounds "$ROW_DIR/list.xml" "camera_set_item:grid:0")"; I1="$(bounds "$ROW_DIR/list.xml" "camera_set_item:grid:1")"
assert_eq "list items at a 44-epx pitch" "132" "$(( $(echo $I1 | cut -d' ' -f2) - $(echo $I0 | cut -d' ' -f2) ))"
assert_contains "the current item (Off) is selected" 'selected="true"' "$(grep -o '<node[^>]*camera_set_item:grid:0"[^>]*>' "$ROW_DIR/list.xml")"
FILL="$(python3 -c "
from PIL import Image
im=Image.open('$ROW_DIR/list.png').convert('RGB')
a=[int(v) for v in '$I0'.split()]; b=[int(v) for v in '$I1'.split()]
print(im.getpixel((a[2]-30,(a[1]+a[3])//2)), im.getpixel((b[2]-30,(b[1]+b[3])//2)))")"
record "fill of the current item and of the other" "$FILL"
assert_contains "the other item on the #2B2B2B list" "(43, 43, 43)" "$FILL"
assert_absent "the current item is not list-coloured (accent)" "(43, 43, 43) (43, 43, 43)" "$FILL"

# --- grid on: two lines each way at thirds of the preview ---------------------------------------------------------
tap_node "$ROW_DIR/list.xml" "camera_set_item:grid:1"; sleep 1
gdump "$ROW_DIR/set2.xml"
assert_eq "Framing grid now" "Rule of thirds" "$(node_text "$ROW_DIR/set2.xml" "camera_set_value:grid")"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
gdump "$ROW_DIR/grid.xml"; screencap "$ROW_DIR/grid.png"
assert_eq "back on the viewfinder" "no" "$(has_node "$ROW_DIR/grid.xml" camera_settings_page)"
assert_eq "grid node" "yes" "$(has_node "$ROW_DIR/grid.xml" camera_grid)"
LINES="$(python3 - "$ROW_DIR/grid.png" $(bounds "$ROW_DIR/grid.xml" camera_preview) <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB'); l, t, r, b = map(int, sys.argv[2:6])
def white(p): return min(p) >= 250
# A grid line is 1 epx of white with a thin dark edge each side. So a line is a run of 2-4 consecutive columns (rows)
# that are white over (nearly) all of the preview — sampled every 7 px, clear of the capsule and the zoom slider at
# the sides — with a column (row) that is NOT white directly before and after the run. The emulated scene's white sky
# is a long white run and so is not a line.
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
print('x=' + ','.join('%.1f' % (g - l) for g in cols) + ' y=' + ','.join('%.1f' % (g - t) for g in rows) + ' want x=%.1f,%.1f y=%.1f,%.1f' % ((r - l) / 3, 2 * (r - l) / 3, (b - t) / 3, 2 * (b - t) / 3))
PY
)"; record "grid lines in the preview (px from its left / top)" "$LINES"
# 1080 x 1440 px preview: thirds at 360 / 720 and 480 / 960, each line 3 px wide (centre +1.0), +/- 2 px.
GX="$(echo "$LINES" | sed 's/x=\([^ ]*\) .*/\1/')"; GY="$(echo "$LINES" | sed 's/.* y=\([^ ]*\) want.*/\1/')"
assert_within "vertical line 1" 360 "$(echo "$GX" | cut -d, -f1)" 2; assert_within "vertical line 2" 720 "$(echo "$GX" | cut -d, -f2)" 2
assert_within "horizontal line 1" 480 "$(echo "$GY" | cut -d, -f1)" 2; assert_within "horizontal line 2" 960 "$(echo "$GY" | cut -d, -f2)" 2
assert_eq "two lines each way and no more" "2 2" "$(echo "$GX" | tr ',' '\n' | grep -c .) $(echo "$GY" | tr ',' '\n' | grep -c .)"

# --- the mode switch: a ~300-ms slide, then video's capsule and geometry ------------------------------------------
M4="$(ring_mark)"
tap_node "$ROW_DIR/grid.xml" "camera_disc:video"; sleep 4
V="$(cam_since "$M4")"
MOTION="$(echo "$V" | grep -o '\[motion\] mode_switch .*' | head -1)"; record "motion" "$MOTION"
SETTLE="$(echo "$MOTION" | grep -o 'settle=[0-9]*' | cut -d= -f2)"; GAP="$(echo "$MOTION" | grep -o 'maxGapMs=[0-9]*' | cut -d= -f2)"
assert_within "mode switch settles in 300 ms (+/- 33)" 300 "${SETTLE:-0}" 33
record "mode switch maxGapMs (the gate's bound is 33.4; the camera re-binds mid-slide)" "${GAP:-none}"
gdump "$ROW_DIR/video.xml"
assert_contains "video is the selected mode" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:video"[^>]*>' "$ROW_DIR/video.xml")"
assert_eq "the shutter is camera_record in video" "yes no" "$(has_node "$ROW_DIR/video.xml" camera_record) $(has_node "$ROW_DIR/video.xml" camera_shutter)"
assert_eq "video preview 16:9 fitted to the width, centred on the screen" "360.0 640.0 180.0 390.0" "$(size_epx "$ROW_DIR/video.xml" camera_preview) $(centre_epx "$ROW_DIR/video.xml" camera_preview)"
assert_eq "the previous-mode disc is on the left (W/2 - 60, nav top - 36)" "120.0 696.0" "$(centre_epx "$ROW_DIR/video.xml" "camera_disc:photo")"
record "capsule items (video)" "$(grep -o 'resource-id="camera_capsule:[a-z:]*"' "$ROW_DIR/video.xml" | sed 's/.*capsule:\([a-z:]*\)"/\1/' | xargs)"

# --- restore -------------------------------------------------------------------------------------------------------
adb shell am force-stop app.tileshell
open_camera; sleep 3; gdump "$ROW_DIR/r0.xml"
tap_node "$ROW_DIR/r0.xml" camera_settings; sleep 1.5; gdump "$ROW_DIR/r1.xml"
tap_node "$ROW_DIR/r1.xml" "camera_set:grid"; sleep 1; gdump "$ROW_DIR/r2.xml"
tap_node "$ROW_DIR/r2.xml" "camera_set_item:grid:0"; sleep 1; gdump "$ROW_DIR/r3.xml"
assert_eq "Framing grid back to Off" "Off" "$(node_text "$ROW_DIR/r3.xml" "camera_set_value:grid")"
remove_rows_above $IMAGES "$MAXI"
assert_eq "images count restored" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "no crash" "" "$(adb logcat -d -t 500 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell; ensure_start
row_end
