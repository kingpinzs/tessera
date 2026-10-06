#!/usr/bin/env bash
# Development proof, build task 6d's last clause (Photos' glyph and hold-to-play) and the probe page's Motion Photo
# read, on the emulator. The shell's Camera takes one plain still and one Living Image ("Capture living images" on, then
# back off). Photos: the collection tile's glyph node exists for the Living Image and for no other tile; the viewer
# shows `viewer_living`; a 2.5-s hold logs the play line with the clip's byte count and then the stop line, and a
# screenshot during the hold differs from the still's; the plain still's hold logs nothing; the same in the viewer
# another app reaches (ViewerActivity). Settings > Diagnostics > Probe: the picked Living Image's line shows the marker,
# the clip's offset and a length equal to the trailing MP4's size. The launcher's pid is the same at the end, no
# AndroidRuntime line names the shell, and both captures are removed (the census back). Not the gate (E7, P12 are).
. "$(dirname "$0")/head.sh"
keep_earlier L1
row_begin L1 "Living Images in Photos: the glyph, hold-to-play, the probe's line"
assert_eq "wake" "Awake" "$(wake_device)"
T0="$(adb shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
MAXI="$(max_id $IMAGES)"; COUNT0="$(count_rows $IMAGES)"; VCOUNT0="$(count_rows $VIDEOS)"
record "census before" "images=$COUNT0 videos=$VCOUNT0 newest image id=$MAXI"
# No microphone: the Living Image's clip is video the camera encodes, so RECORD_AUDIO is revoked for the run (hard rule 9)
# and put back as found at the end. A revoke restarts the shell's processes, so it comes before the pid is read.
MIC_WAS="$(grants RECORD_AUDIO)"; record "RECORD_AUDIO as found" "$MIC_WAS"
mic_off
adb shell am force-stop app.tileshell; ensure_start; sleep 2
PID0="$(launcher_pid)"; record "launcher pid at the start" "$PID0"
assert_ne "the launcher runs" "" "$PID0"

# ---- the two captures: a plain still, then a Living Image ------------------------------------------------------------
MARK="$(ring_mark)"
open_camera
record "camera ready after (s)" "$(wait_camera "$MARK")"
gdump "$ROW_DIR/vf.xml"
assert_contains "living images starts off" 'selected="false"' "$(grep -o '<node[^>]*camera_mode:livingimages"[^>]*>' "$ROW_DIR/vf.xml")"
M1="$(ring_mark)"
tap_node "$ROW_DIR/vf.xml" camera_shutter; sleep 5
PLAIN_SAVED="$(cam_since "$M1" | grep -o 'saved content://.*' | head -1)"; record "plain still's saved line" "$PLAIN_SAVED"
assert_eq "the plain still is one new image row" "$((COUNT0 + 1))" "$(count_rows $IMAGES)"
PLAIN_ID="$(shell_rows $IMAGES | head -1 | cut -d'|' -f1)"
toggle_living() { # dump prefix
  gdump "$ROW_DIR/$1-a.xml"; tap_node "$ROW_DIR/$1-a.xml" camera_settings; sleep 1.5
  adb shell input swipe 540 1700 540 900 300; sleep 1
  gdump "$ROW_DIR/$1-b.xml"; tap_node "$ROW_DIR/$1-b.xml" "camera_set:living"; sleep 1
  gdump "$ROW_DIR/$1-c.xml"
  adb shell input keyevent KEYCODE_BACK; sleep 3
}
toggle_living on
assert_contains "the setting is on" 'checked="true"' "$(grep -o '<node[^>]*camera_set:living"[^>]*>' "$ROW_DIR/on-c.xml")"
gdump "$ROW_DIR/vf2.xml"
sleep 2                                   # a second of viewfinder before the shutter: the clip's content
M2="$(ring_mark)"
tap_node "$ROW_DIR/vf2.xml" camera_shutter; sleep 6
SAVED="$(cam_since "$M2" | grep -o 'saved content://.*' | head -1)"; record "living image's saved line" "$SAVED"
assert_contains "saved as a living image" "living image clip=" "$SAVED"
assert_eq "two new image rows in all" "$((COUNT0 + 2))" "$(count_rows $IMAGES)"
assert_eq "and no video row (no sidecar)" "$VCOUNT0" "$(count_rows $VIDEOS)"
ROW1="$(shell_rows $IMAGES | head -1)"; record "the living image's row" "$ROW1"
LIVE_ID="$(echo "$ROW1" | cut -d'|' -f1)"; NAME="$(echo "$ROW1" | cut -d'|' -f2)"; SIZE="$(echo "$ROW1" | cut -d'|' -f6)"
assert_ne "two different rows" "$PLAIN_ID" "$LIVE_ID"
toggle_living off
gdump "$ROW_DIR/vf3.xml"
assert_contains "the setting is back off" 'selected="false"' "$(grep -o '<node[^>]*camera_mode:livingimages"[^>]*>' "$ROW_DIR/vf3.xml")"
# The file's own facts, read on the host (dev-camera's reader): where the trailing MP4 starts and how long it is.
adb shell "cat /sdcard/DCIM/Camera/$NAME" > "$ROW_DIR/living.jpg"
assert_eq "the pulled file is the row's size" "$SIZE" "$(stat -c%s "$ROW_DIR/living.jpg")"
FACTS="$(python3 "$LIVING_HERE/../../dev-camera/scripts/motion_check.py" "$ROW_DIR/living.jpg")"; record "container facts (host)" "$FACTS"
CLIP_BYTES="$(echo "$FACTS" | grep -o 'trailing_mp4=[0-9]*' | cut -d= -f2)"
CLIP_AT="$(echo "$FACTS" | grep -o 'jpeg_bytes=[0-9]*' | cut -d= -f2)"
STAMP="$(echo "$FACTS" | grep -o 'timestamp_us=[0-9]*' | cut -d= -f2)"
assert_contains "the file is a Motion Photo whose directory length is the trailing MP4's size" "length_matches=yes" "$FACTS"
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- Photos: the tile's glyph ----------------------------------------------------------------------------------------
MARKP="$(ring_mark)"
adb shell am start -n app.tileshell/.photos.PhotosActivity >/dev/null; sleep 4
C="$ROW_DIR/collection.xml"; dump_ui "$C"
assert_eq "both captures are tiles of the collection" "yes yes" "$(has_node "$C" "photos_item:$LIVE_ID") $(has_node "$C" "photos_item:$PLAIN_ID")"
assert_eq "the Living Image's tile carries the glyph" "yes" "$(has_node "$C" "photos_living:$LIVE_ID")"
assert_eq "the plain still's tile carries none" "no" "$(has_node "$C" "photos_living:$PLAIN_ID")"
assert_eq "the glyph nodes of the page: that one item's and no other's" "$LIVE_ID" "$(ids_of "$C" photos_living:)"
record "tiles on the page" "$(ids_of "$C" photos_item:)"
record "glyph bounds / its tile's bounds (px)" "$(bounds "$C" "photos_living:$LIVE_ID") / $(bounds "$C" "photos_item:$LIVE_ID")"
screencap "$ROW_DIR/collection.png"

# ---- the viewer: the glyph, the hold ---------------------------------------------------------------------------------
tap_node "$C" "photos_item:$LIVE_ID"; sleep 3
V="$ROW_DIR/viewer.xml"; dump_ui "$V"
assert_eq "the viewer's Living Images glyph" "yes" "$(has_node "$V" viewer_living)"
assert_eq "no clip surface before a hold" "no" "$(has_node "$V" viewer_living_clip)"
IB="$(bounds "$V" viewer_image)"; record "the photo's box (px)" "$IB"
screencap "$ROW_DIR/still.png"
# A hold on the photo: 2.5 s. Screenshots are taken while it lasts; the clip is 1 s long and starts after the hold is
# recognised, so the frame that shows it is the one that differs most from the still.
hold_and_watch() { # prefix -> sets SLICE (the living lines), BEST (the largest difference in percent)
  local i d
  HMARK="$(ring_mark)"
  hold_start 540 1170 2500
  sleep 0.5
  for i in 1 2 3 4 5 6; do screencap "$ROW_DIR/$1-mid$i.png"; done
  wait "$HOLD_PID"; sleep 1.5
  SLICE="$(living_lines "$HMARK")"
  BEST=0
  for i in 1 2 3 4 5 6; do
    d="$(frame_diff "$ROW_DIR/still.png" "$ROW_DIR/$1-mid$i.png" $IB)"
    note "$1-mid$i differs from the still on $d % of the photo's pixels"
    BEST="$(python3 -c "print(max($BEST, $d))")"
  done
}
hold_and_watch hold
record "the hold's lines" "$(echo "$SLICE" | tr '\n' '|')"
assert_contains "the play line, with the trailing MP4's byte count" "living $LIVE_ID: play $CLIP_BYTES bytes" "$SLICE"
assert_contains "then the stop line" "living $LIVE_ID: stop (" "$SLICE"
assert_eq "one play and one stop, in that order" "play stop" "$(echo "$SLICE" | sed -E 's/living [^:]*: ([a-z]+).*/\1/' | xargs)"
STOP="$(echo "$SLICE" | grep -F ': stop (' | head -1)"
if echo "$STOP" | grep -qE 'stop \((released|ended)\)$'; then _verdict PASS "the stop is the hold's end or the clip's" "$STOP"; else _verdict FAIL "the stop is the hold's end or the clip's" "$STOP"; fi
record "largest difference from the still during the hold (% of the photo's pixels)" "$BEST"
if [ "$(python3 -c "print('yes' if $BEST >= 1.0 else 'no')")" = yes ]; then _verdict PASS "a screenshot during the hold differs from the still's" "$BEST %"; else _verdict FAIL "a screenshot during the hold differs from the still's" "$BEST %"; fi
dump_ui "$ROW_DIR/after.xml"; screencap "$ROW_DIR/after.png"
assert_eq "after the hold the clip's surface is gone" "no" "$(has_node "$ROW_DIR/after.xml" viewer_living_clip)"
assert_eq "and the viewer still shows the picture" "yes" "$(has_node "$ROW_DIR/after.xml" viewer_image)"
BACK="$(frame_diff "$ROW_DIR/still.png" "$ROW_DIR/after.png" $IB)"; record "after the hold vs the still (% of the photo's pixels)" "$BACK"
if [ "$(python3 -c "print('yes' if $BACK < 0.5 else 'no')")" = yes ]; then _verdict PASS "the still is back" "$BACK %"; else _verdict FAIL "the still is back" "$BACK %"; fi
# A short hold: released before the clip ends.
HMARK="$(ring_mark)"; adb shell input swipe 540 1170 540 1170 900; sleep 1.5
SHORT="$(living_lines "$HMARK")"; record "a 0.9-s hold's lines" "$(echo "$SHORT" | tr '\n' '|')"
assert_contains "a hold let go mid-clip stops as released" "living $LIVE_ID: stop (released)" "$SHORT"
# Back while nothing plays closes the viewer; the plain still next.
adb shell input keyevent KEYCODE_BACK; sleep 2
dump_ui "$C"
tap_node "$C" "photos_item:$PLAIN_ID"; sleep 3
dump_ui "$ROW_DIR/plain.xml"
assert_eq "the plain still is in the viewer" "yes" "$(has_node "$ROW_DIR/plain.xml" viewer_image)"
assert_eq "with no Living Images glyph" "no" "$(has_node "$ROW_DIR/plain.xml" viewer_living)"
HMARK="$(ring_mark)"; adb shell input swipe 540 1170 540 1170 2500; sleep 1.5
assert_eq "a plain still's hold logs nothing" "" "$(living_lines "$HMARK")"
dump_ui "$ROW_DIR/plain-after.xml"
assert_eq "and plays nothing" "no" "$(has_node "$ROW_DIR/plain-after.xml" viewer_living_clip)"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- the viewer another app reaches (ViewerActivity): reading, not changing ------------------------------------------
HMARK="$(ring_mark)"
adb shell am start -a android.intent.action.VIEW -d "content://media/external/images/media/$LIVE_ID" -t image/jpeg -n app.tileshell/.photos.ViewerActivity >/dev/null; sleep 4
X="$ROW_DIR/external.xml"; dump_ui "$X"
REQ="$(ring_since "$HMARK" launcher | grep -F 'viewer request' | sed 's/.*\[photosapp\] //' | head -1)"; record "ViewerActivity's request line" "$REQ"
assert_eq "ViewerActivity resumed" "app.tileshell/.photos.ViewerActivity" "$(top)"
if [ "$(has_node "$X" viewer_image)" = yes ]; then
  assert_contains "it is another app's viewer: read-only" "shown read-only" "$REQ"
  assert_eq "read-only: no Edit, no Delete" "no no" "$(has_node "$X" viewer_edit) $(has_node "$X" viewer_delete)"
  assert_eq "the glyph is there all the same" "yes" "$(has_node "$X" viewer_living)"
  HMARK="$(ring_mark)"; adb shell input swipe 540 1170 540 1170 2500; sleep 1.5
  EXT="$(living_lines "$HMARK")"; record "the hold's lines in ViewerActivity" "$(echo "$EXT" | tr '\n' '|')"
  assert_contains "the hold plays there too" "living $LIVE_ID: play $CLIP_BYTES bytes" "$EXT"
  assert_contains "and stops" "living $LIVE_ID: stop (" "$EXT"
  # Back during nothing; then a hold cut by the activity's stop (HOME while held) leaves no player behind.
  HMARK="$(ring_mark)"; hold_start 540 1170 2500; sleep 1.1; adb shell input keyevent KEYCODE_HOME; wait "$HOLD_PID"; sleep 1.5
  LEFT="$(living_lines "$HMARK")"; record "a hold cut by HOME" "$(echo "$LEFT" | tr '\n' '|')"
  assert_eq "every play of it has its stop" "$(echo "$LEFT" | grep -c ': play ')" "$(echo "$LEFT" | grep -c ': stop (')"
else
  record "ViewerActivity did not show the picture to this starter (adb's shell)" "$(has_node "$X" viewer_error)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- the probe page's line for the picked Living Image ---------------------------------------------------------------
adb shell am start -n app.tileshell/.settings.SettingsActivity --es page DIAGNOSTICS >/dev/null; sleep 4
dump_ui "$ROW_DIR/diag.xml"
tap_node "$ROW_DIR/diag.xml" diag_probe; sleep 3
dump_ui "$ROW_DIR/probe.xml"
tap_node "$ROW_DIR/probe.xml" probe_pick; sleep 3
adb shell uiautomator dump /sdcard/Download/picker.xml >/dev/null 2>&1; adb shell cat /sdcard/Download/picker.xml > "$ROW_DIR/picker.xml"; adb shell rm -f /sdcard/Download/picker.xml
# Android's own picker lists the newest first: its first tile is the Living Image (the last capture).
XY="$(python3 - "$ROW_DIR/picker.xml" <<'PY'
import re, sys
x = open(sys.argv[1], errors='replace').read()
m = re.search(r'resource-id="[^"]*(?:icon_thumbnail|media_item|image_view)[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
print('%d %d' % ((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2) if m else '')
PY
)"
record "picker's first item at" "${XY:-not found}"
if [ -n "$XY" ]; then
  adb shell input tap $XY; sleep 4
  dump_ui "$ROW_DIR/picked.xml"
  FILE="$(node_text "$ROW_DIR/picked.xml" probe_file | sed 's/&#10;/\n/g')"
  record "probe file text" "$(echo "$FILE" | tr '\n' '|' | cut -c1-600)"
  assert_contains "the picked file is the Living Image (its size)" " sizeBytes=$SIZE" "$FILE"
  MP="$(echo "$FILE" | grep -F 'motionPhoto:' | head -1)"; record "the probe's Motion Photo line" "$MP"
  assert_eq "the marker, the clip's offset, a length equal to the trailing MP4's size, the timestamp" \
    "motionPhoto: MotionPhoto=1 offset=$CLIP_AT length=$CLIP_BYTES timestampUs=$STAMP" "$MP"
else
  _verdict FAIL "the picker's first item" "not found in the picker's dump"
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 1

# ---- the process, the log, the restore -------------------------------------------------------------------------------
assert_eq "the launcher's pid is unchanged" "$PID0" "$(launcher_pid)"
assert_eq "no AndroidRuntime line names the shell" "" "$(adb logcat -d -v brief -s AndroidRuntime -T "$T0" 2>/dev/null | grep -F 'app.tileshell' | head -3)"
ring_save launcher; ring_save "$CAM_RING"
remove_rows_above $IMAGES "$MAXI"
assert_eq "images back at the census" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "videos back at the census" "$VCOUNT0" "$(count_rows $VIDEOS)"
assert_eq "the captured files are gone" "" "$(adb shell "ls /sdcard/DCIM/Camera/$NAME 2>/dev/null" | tr -d '\r')"
if [ "$MIC_WAS" = true ]; then mic_on; fi
assert_eq "RECORD_AUDIO as found" "$MIC_WAS" "$(grants RECORD_AUDIO)"
assert_eq "the launcher's pid is still unchanged after the restore" "$PID0" "$(launcher_pid)"
ensure_start
row_end
