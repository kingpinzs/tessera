#!/usr/bin/env bash
# Development proof, build task 6 (B, TRUST): the capture answer's video legs through testapps/qa-capture, with
# RECORD_AUDIO revoked for the span — a content output (an mp4 at the caller's URI, no DCIM copy) and no output (a
# DCIM/Camera row through the write layer, its URI returned with a read grant). Not the gate (E9 is the lead's).
. "$(dirname "$0")/head.sh"
install_qac
row_begin B2 "capture answer: video legs"
MAXV="$(max_id $VIDEOS)"; COUNT0="$(count_rows $VIDEOS)"
MIC_BEFORE="$(granted RECORD_AUDIO)"
adb shell am force-stop app.tileshell; adb shell am force-stop $QAC
mic_off
assert_eq "RECORD_AUDIO revoked for the row" "0" "$(granted RECORD_AUDIO)"

record_and_accept() {
  gdump "$ROW_DIR/$LEG-vf.xml"
  assert_eq "$LEG: CaptureActivity on top" "app.tileshell/.camera.CaptureActivity" "$(top_activity)"
  tap_node "$ROW_DIR/$LEG-vf.xml" camera_record; sleep 3.5
  tap_node "$ROW_DIR/$LEG-vf.xml" camera_record; sleep 4
  gdump "$ROW_DIR/$LEG-review.xml"
  assert_eq "$LEG: the accept / retake page" "yes" "$(has_node "$ROW_DIR/$LEG-review.xml" capture_review)"
  tap_node "$ROW_DIR/$LEG-review.xml" capture_accept
}

LEG=video-content; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; record "camera ready after (s)" "$(wait_camera "$MARK")"
record_and_accept
L="$(leg_lines "$T0" $LEG 15)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "$LEG: RESULT_OK" "result=RESULT_OK" "$L"
assert_contains "$LEG: the caller's file exists" "output exists=true" "$L"
MD5="$(echo "$L" | grep -o 'md5=[0-9a-f]*' | head -1 | cut -d= -f2)"
adb exec-out run-as $QAC cat cache/out.mp4 > "$ROW_DIR/out.mp4"
assert_eq "$LEG: the pulled file is the one the fixture hashed" "$MD5" "$(md5sum "$ROW_DIR/out.mp4" | cut -d' ' -f1)"
assert_eq "$LEG: ffprobe decodes it — one video stream, no audio" "1 video" "$(streams "$ROW_DIR/out.mp4")"
assert_eq "$LEG: videos count unchanged (no DCIM copy)" "$COUNT0" "$(count_rows $VIDEOS)"
S="$(cam_since "$MARK")"
assert_contains "$LEG: the guard accepted" "capture request video from $QAC: output accepted" "$S"
assert_contains "$LEG: written through the write layer" "[camera] capture video -> the caller's output" "$S"
assert_contains "$LEG: silent, and said so" "[camera] video sound: off (no microphone permission)" "$S"

LEG=video-none; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; wait_camera "$MARK" >/dev/null
record_and_accept
L="$(leg_lines "$T0" $LEG 15)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "$LEG: RESULT_OK" "result=RESULT_OK" "$L"
RET="$(echo "$L" | grep -o 'returned uri=[^ ]*' | cut -d= -f2)"; record "$LEG: returned uri" "$RET"
assert_contains "$LEG: a MediaStore video URI came back" "content://media/external_primary/video/media/" "$RET"
assert_contains "$LEG: readable through the grant" "exists=true" "$(echo "$L" | grep -F 'returned uri=')"
assert_contains "$LEG: with the read-grant flag" "flags=0x1" "$(echo "$L" | grep -F 'returned uri=')"
assert_eq "$LEG: videos count +1" "$((COUNT0 + 1))" "$(count_rows $VIDEOS)"
ROW1="$(shell_rows $VIDEOS | head -1)"; record "$LEG: new row" "$ROW1"
assert_eq "$LEG: in DCIM/Camera, published" "DCIM/Camera/ 0" "$(echo "$ROW1" | cut -d'|' -f7) $(echo "$ROW1" | cut -d'|' -f5)"
assert_contains "$LEG: the returned URI is that row" "/$(echo "$ROW1" | cut -d'|' -f1)" "$RET"

# --- Back while recording: RESULT_CANCELED and nothing left ------------------------------------------------------
LEG=video-content; T0="$(qa_time)"; MARK="$(ring_mark)"
start_leg $LEG; sleep 2; wait_camera "$MARK" >/dev/null
gdump "$ROW_DIR/back-vf.xml"; tap_node "$ROW_DIR/back-vf.xml" camera_record; sleep 2.5
adb shell input keyevent KEYCODE_BACK
L="$(leg_lines "$T0" $LEG 12)"; echo "$L" | sed 's/^/      /' >> "$LOG"
assert_contains "back mid-take: RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
assert_contains "back mid-take: nothing written to the caller" "output exists=false" "$L"
sleep 2
assert_eq "back mid-take: no pending row" "0" "$(pending_rows)"
assert_eq "back mid-take: videos count still +1" "$((COUNT0 + 1))" "$(count_rows $VIDEOS)"

# --- restore --------------------------------------------------------------------------------------------------------
adb shell am force-stop $QAC; adb shell am force-stop app.tileshell
[ "$MIC_BEFORE" -ge 1 ] && mic_on
assert_eq "RECORD_AUDIO as it was" "$MIC_BEFORE" "$(granted RECORD_AUDIO)"
remove_rows_above $VIDEOS "$MAXV"
assert_eq "videos count restored" "$COUNT0" "$(count_rows $VIDEOS)"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -E 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb uninstall $QAC >/dev/null
ensure_start
row_end
