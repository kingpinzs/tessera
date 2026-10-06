#!/usr/bin/env bash
# Development proof, build task 6d (F): Living Images on the emulator. "Capture living images" on in settings, one
# still -> ONE file in DCIM/Camera whose bytes end with an MP4 (`ftyp` in its tail), whose XMP reads MotionPhoto 1 and
# whose Container:Directory item length equals the trailing MP4's size; the clip decodes. The setting is put back off.
# Not the gate (E7's Living Images sub-row is the lead's).
. "$(dirname "$0")/head.sh"
row_begin F1 "Living Images: one Motion Photo file"
MAXI="$(max_id $IMAGES)"; COUNT0="$(count_rows $IMAGES)"; VCOUNT0="$(count_rows $VIDEOS)"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
open_camera
record "camera ready after (s)" "$(wait_camera "$MARK")"
gdump "$ROW_DIR/vf.xml"
assert_contains "living images starts off" 'selected="false"' "$(grep -o '<node[^>]*camera_mode:livingimages"[^>]*>' "$ROW_DIR/vf.xml")"
toggle_living() { # dump prefix
  gdump "$ROW_DIR/$1-a.xml"; tap_node "$ROW_DIR/$1-a.xml" camera_settings; sleep 1.5
  adb shell input swipe 540 1700 540 900 300; sleep 1
  gdump "$ROW_DIR/$1-b.xml"; tap_node "$ROW_DIR/$1-b.xml" "camera_set:living"; sleep 1
  gdump "$ROW_DIR/$1-c.xml"
  adb shell input keyevent KEYCODE_BACK; sleep 3
}
toggle_living on
assert_contains "the toggle is on" 'checked="true"' "$(grep -o '<node[^>]*camera_set:living"[^>]*>' "$ROW_DIR/on-c.xml")"
gdump "$ROW_DIR/vf2.xml"
assert_contains "living images is on" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:livingimages"[^>]*>' "$ROW_DIR/vf2.xml")"
sleep 2                                   # a second of viewfinder before the shutter: the clip's content
M2="$(ring_mark)"
tap_node "$ROW_DIR/vf2.xml" camera_shutter; sleep 6
S="$(cam_since "$M2")"
SAVED="$(echo "$S" | grep -o 'saved content://.*' | head -1)"; record "saved line" "$SAVED"
assert_contains "saved as a living image" "living image clip=" "$SAVED"
assert_eq "ONE new image row" "$((COUNT0 + 1))" "$(count_rows $IMAGES)"
assert_eq "and no video row (no sidecar)" "$VCOUNT0" "$(count_rows $VIDEOS)"
ROW1="$(shell_rows $IMAGES | head -1)"; record "new row" "$ROW1"
NAME="$(echo "$ROW1" | cut -d'|' -f2)"
assert_eq "in DCIM/Camera, published" "DCIM/Camera/ 0" "$(echo "$ROW1" | cut -d'|' -f7) $(echo "$ROW1" | cut -d'|' -f5)"
adb shell "cat /sdcard/DCIM/Camera/$NAME" > "$ROW_DIR/living.jpg"
assert_eq "the pulled file is the row's size" "$(echo "$ROW1" | cut -d'|' -f6)" "$(stat -c%s "$ROW_DIR/living.jpg")"
FTYP="$(tail -c 1M "$ROW_DIR/living.jpg" | grep -c ftyp)"
if [ "$FTYP" -ge 1 ]; then _verdict PASS "ftyp in the file's tail" "$FTYP"; else _verdict FAIL "ftyp in the file's tail" "$FTYP"; fi
FACTS="$(python3 "$HERE/motion_check.py" "$ROW_DIR/living.jpg" "$ROW_DIR/clip.mp4")"; record "container facts" "$FACTS"
assert_contains "XMP MotionPhoto reads 1" "MotionPhoto=1" "$FACTS"
assert_contains "the directory's video item length = the trailing MP4's size" "length_matches=yes" "$FACTS"
assert_contains "the presentation timestamp is written" "timestamp_us=" "$FACTS"
assert_contains "strings | grep MotionPhoto (the doc's read)" 'Camera:MotionPhoto="1"' "$(strings "$ROW_DIR/living.jpg" | grep -m1 -o 'Camera:MotionPhoto="1"')"
assert_eq "the clip decodes: one video stream" "1 video" "$(streams "$ROW_DIR/clip.mp4")"
record "clip" "$(stream_facts "$ROW_DIR/clip.mp4")"
DUR="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$ROW_DIR/clip.mp4" 2>/dev/null)"
assert_within "the clip is about 1 s (the second before the shutter)" 1.0 "${DUR:-0}" 0.35
assert_contains "the still half still decodes as a JPEG with EXIF" "DateTimeOriginal=20" "$(python3 "$HERE/exif_read.py" "$ROW_DIR/living.jpg")"

# --- restore: the setting off, the capture removed ------------------------------------------------------------------
toggle_living off
gdump "$ROW_DIR/vf3.xml"
assert_contains "living images back off" 'selected="false"' "$(grep -o '<node[^>]*camera_mode:livingimages"[^>]*>' "$ROW_DIR/vf3.xml")"
remove_rows_above $IMAGES "$MAXI"
assert_eq "images count restored" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell; ensure_start
row_end
