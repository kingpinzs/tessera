#!/usr/bin/env bash
# Development proof, build task 6 (B, TRUST): the capture answer's image legs through testapps/qa-capture — a content
# output through the caller's own provider (the first device proof of CaptureCallerAccess), no output (a thumbnail, no
# file), Back, a file:// output (refused), and a request for the front camera. Not the gate (E9 is the lead's).
. "$(dirname "$0")/head.sh"
install_qac
row_begin B1 "capture answer: image legs"
MAXI="$(max_id $IMAGES)"; COUNT0="$(count_rows $IMAGES)"
adb shell am force-stop app.tileshell; adb shell am force-stop $QAC
record "IMAGE_CAPTURE naming the shell resolves to" "$(adb shell cmd package query-activities --brief -a android.media.action.IMAGE_CAPTURE -p app.tileshell | grep / | tr -d '\r' | xargs)"

shoot_and() { # what: accept | retake-accept | back
  gdump "$ROW_DIR/$LEG-vf.xml"
  assert_eq "$LEG: CaptureActivity on top" "app.tileshell/.camera.CaptureActivity" "$(top_activity)"
  assert_eq "$LEG: one mode — no roll, no settings, no mode disc" "no no no" "$(has_node "$ROW_DIR/$LEG-vf.xml" camera_roll) $(has_node "$ROW_DIR/$LEG-vf.xml" camera_settings) $(has_node "$ROW_DIR/$LEG-vf.xml" "camera_disc:video")"
  if [ "$1" = back ]; then adb shell input keyevent KEYCODE_BACK; return; fi
  tap_node "$ROW_DIR/$LEG-vf.xml" camera_shutter; sleep 3
  gdump "$ROW_DIR/$LEG-review.xml"; screencap "$ROW_DIR/$LEG-review.png"
  assert_eq "$LEG: the accept / retake page" "yes" "$(has_node "$ROW_DIR/$LEG-review.xml" capture_review)"
  tap_node "$ROW_DIR/$LEG-review.xml" capture_accept
}

# --- content output: the caller's own provider -------------------------------------------------------------------
LEG=image-content; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; record "camera ready after (s)" "$(wait_camera "$MARK")"
shoot_and accept
# Y14: Accept at 82, Retake at 150, More at 24 epx from the right (glyph centres), on a 48-epx bar at the bottom.
assert_eq "accept / retake / more centres from the right (epx)" "82.0 150.0 24.0" "$(for n in capture_accept capture_retake capture_more; do set -- $(bounds "$ROW_DIR/$LEG-review.xml" $n); python3 -c "print(360 - ($1+$3)/6)"; done | xargs)"
set -- $(bounds "$ROW_DIR/$LEG-review.xml" capture_bar); assert_eq "bar 48 epx on the page's bottom" "144 2196" "$(( $4 - $2 )) $4"
L="$(leg_lines "$T0" $LEG)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "$LEG: no throw on the sender" "start threw=none" "$L"
assert_contains "$LEG: RESULT_OK" "result=RESULT_OK" "$L"
assert_contains "$LEG: the caller's file exists" "output exists=true" "$L"
SIZE="$(echo "$L" | grep -o 'size=[0-9]*' | head -1 | cut -d= -f2)"; MD5="$(echo "$L" | grep -o 'md5=[0-9a-f]*' | head -1 | cut -d= -f2)"
assert_ne "$LEG: size > 0" "0" "${SIZE:-0}"
adb exec-out run-as $QAC cat cache/out.jpg > "$ROW_DIR/out.jpg"
assert_eq "$LEG: the pulled file is the one the fixture hashed" "$MD5" "$(md5sum "$ROW_DIR/out.jpg" | cut -d' ' -f1)"
EXIF="$(python3 "$HERE/exif_read.py" "$ROW_DIR/out.jpg" 2>&1)"; record "$LEG: the caller's JPEG" "$EXIF"
assert_contains "$LEG: a decodable JPEG at the chosen size" "size=1856x1392" "$EXIF"
assert_contains "$LEG: no GPS EXIF" "gps_tags=0" "$EXIF"
assert_eq "$LEG: images count unchanged (no DCIM copy)" "$COUNT0" "$(count_rows $IMAGES)"
S="$(cam_since "$MARK")"
assert_contains "$LEG: the guard accepted" "capture request image from $QAC: output accepted" "$S"
assert_contains "$LEG: written through the write layer" "[camera] capture image -> the caller's output, $SIZE bytes" "$S"
assert_absent "$LEG: nothing saved to MediaStore" "[camera] saved " "$S"

# --- no output: a thumbnail in `data`, no file ----------------------------------------------------------------------
LEG=image-none; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; wait_camera "$MARK" >/dev/null
shoot_and accept
L="$(leg_lines "$T0" $LEG)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "$LEG: RESULT_OK" "result=RESULT_OK" "$L"
BITMAP="$(echo "$L" | grep -o 'data bitmap=[0-9x]*' | cut -d= -f2)"; record "$LEG: data bitmap" "$BITMAP"
assert_ne "$LEG: a bitmap came back" "" "$BITMAP"
assert_contains "$LEG: no URI came back" "returned uri=none" "$L"
assert_eq "$LEG: images count unchanged (no file)" "$COUNT0" "$(count_rows $IMAGES)"
assert_contains "$LEG: the line" "capture image -> thumbnail $BITMAP, no file" "$(cam_since "$MARK")"

# --- Back: RESULT_CANCELED, nothing left ------------------------------------------------------------------------------
LEG=image-content; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; wait_camera "$MARK" >/dev/null
shoot_and back
L="$(leg_lines "$T0" $LEG)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "back: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "back: nothing written to the caller" "output exists=false" "$L"
assert_eq "back: no pending row of the shell's" "0" "$(pending_rows)"
assert_eq "back: images count unchanged" "$COUNT0" "$(count_rows $IMAGES)"
# Back from the accept / retake page is Retake (the capture is dropped), a second Back cancels.
T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; wait_camera "$MARK" >/dev/null
gdump "$ROW_DIR/back2-vf.xml"; tap_node "$ROW_DIR/back2-vf.xml" camera_shutter; sleep 3
adb shell input keyevent KEYCODE_BACK; sleep 1.5; gdump "$ROW_DIR/back2-after.xml"
assert_eq "back on the review page returns to the viewfinder" "no yes" "$(has_node "$ROW_DIR/back2-after.xml" capture_review) $(has_node "$ROW_DIR/back2-after.xml" camera_shutter)"
adb shell input keyevent KEYCODE_BACK
L="$(leg_lines "$T0" $LEG)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "back after a capture: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "back after a capture: nothing written" "output exists=false" "$L"

# --- file:// output: refused at once ----------------------------------------------------------------------------------
LEG=image-file; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG
L="$(leg_lines "$T0" $LEG 8)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "$LEG: the sender did not throw (its VmPolicy relaxed)" "start threw=none" "$L"
assert_contains "$LEG: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "$LEG: nothing at the path" "output exists=false" "$L"
S="$(cam_since "$MARK")"
assert_contains "$LEG: the guard's line" "[camera] refused output scheme=file" "$S"
assert_absent "$LEG: no camera was opened" "[camera] devices=" "$S"

# --- the front camera asked for: the back camera answers --------------------------------------------------------------
LEG=image-none; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG --ez front true; sleep 2; wait_camera "$MARK" >/dev/null
assert_contains "front asked for: the back camera answers" "[camera] devices=1 front=absent" "$(cam_since "$MARK")"
shoot_and accept
L="$(leg_lines "$T0" $LEG)"
assert_contains "front asked for: RESULT_OK" "result=RESULT_OK" "$L"

# --- restore --------------------------------------------------------------------------------------------------------
remove_rows_above $IMAGES "$MAXI"
assert_eq "images count at the end" "$COUNT0" "$(count_rows $IMAGES)"
assert_eq "no pending row at the end" "0" "$(pending_rows)"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -E 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop $QAC; adb shell am force-stop app.tileshell
adb uninstall $QAC >/dev/null
ensure_start
row_end
