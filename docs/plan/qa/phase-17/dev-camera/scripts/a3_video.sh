#!/usr/bin/env bash
# Development proof, build task 6 (A.3): a 5-s video with RECORD_AUDIO revoked — one video stream and NO audio stream,
# the sound-off line and the hint naming the Microphone row — saved in DCIM/Camera through the write layer; then a take
# cut short by the screen going off is still finalised. Not the gate (E8 is the lead's). The microphone is never used.
. "$(dirname "$0")/head.sh"
row_begin A3 "video without sound; screen-off ends a take"
MAXV="$(max_id $VIDEOS)"; COUNT0="$(count_rows $VIDEOS)"
MIC_BEFORE="$(granted RECORD_AUDIO)"; record "RECORD_AUDIO granted before" "$MIC_BEFORE"
adb shell am force-stop app.tileshell
mic_off
assert_eq "RECORD_AUDIO revoked for the row" "0" "$(granted RECORD_AUDIO)"
MARK="$(ring_mark)"
open_camera video
record "camera ready after (s)" "$(wait_camera "$MARK")"
gdump "$ROW_DIR/v0.xml"
assert_contains "opened on video" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:video"[^>]*>' "$ROW_DIR/v0.xml")"
tap_node "$ROW_DIR/v0.xml" camera_record
sleep 3; gdump "$ROW_DIR/v1.xml"; screencap "$ROW_DIR/v1.png"
HINT="$(node_text "$ROW_DIR/v1.xml" camera_sound_hint)"; record "hint" "$HINT"
assert_contains "the hint names the Microphone row" "Microphone" "$HINT"
assert_contains "the hint names the Setup checklist" "Setup checklist" "$HINT"
assert_contains "the recording clock runs" "00:0" "$(node_text "$ROW_DIR/v1.xml" camera_rec_time)"
assert_eq "no settings, roll or mode disc while recording" "no no no" "$(has_node "$ROW_DIR/v1.xml" camera_settings) $(has_node "$ROW_DIR/v1.xml" camera_roll) $(has_node "$ROW_DIR/v1.xml" "camera_disc:photo")"
sleep 2.5
tap_node "$ROW_DIR/v0.xml" camera_record
sleep 5
S="$(cam_since "$MARK")"
assert_contains "sound-off line" "[camera] video sound: off (no microphone permission)" "$S"
SAVED="$(echo "$S" | grep -o 'saved content://[^ ]* [0-9]*x[0-9]*' | head -1)"; record "saved line" "$SAVED"
assert_ne "saved line present" "" "$SAVED"
assert_eq "videos count +1" "$((COUNT0 + 1))" "$(count_rows $VIDEOS)"
ROW1="$(shell_rows $VIDEOS | head -1)"; record "new row" "$ROW1"
IFS='|' read -r ID NAME W H PENDING SIZE RPATH <<< "$ROW1"
assert_eq "relative_path" "DCIM/Camera/" "$RPATH"
assert_eq "is_pending" "0" "$PENDING"
DUR="$(adb shell "content query --uri $VIDEOS/$ID --projection duration" | sed -n 's/.*duration=\([0-9]*\).*/\1/p' | tr -d '\r')"; record "duration (ms)" "$DUR"
if [ "${DUR:-0}" -ge 4500 ]; then _verdict PASS "duration >= 4500" "$DUR"; else _verdict FAIL "duration >= 4500" "${DUR:-none}"; fi
adb shell "cat /sdcard/DCIM/Camera/$NAME" > "$ROW_DIR/take.mp4"
assert_eq "one video stream and no audio stream" "1 video" "$(streams "$ROW_DIR/take.mp4")"
record "ffprobe" "$(stream_facts "$ROW_DIR/take.mp4")"

# --- the screen goes off mid-take: the take is stopped and finalised ---------------------------------------------
gdump "$ROW_DIR/v2.xml"
M2="$(ring_mark)"
tap_node "$ROW_DIR/v2.xml" camera_record; sleep 3
adb shell input keyevent KEYCODE_SLEEP; sleep 5
assert_eq "awake again" "Awake" "$(wake_device)"
sleep 1
S2="$(cam_since "$M2")"
assert_contains "the take was stopped by the screen going off" "[camera] recording stopped: screen off" "$S2"
assert_contains "and saved" "[camera] saved content://" "$S2"
assert_eq "videos count +2" "$((COUNT0 + 2))" "$(count_rows $VIDEOS)"
ROW2="$(shell_rows $VIDEOS | head -1)"; record "screen-off take's row" "$ROW2"
assert_eq "finalised: is_pending 0" "0" "$(echo "$ROW2" | cut -d'|' -f5)"
adb shell "cat /sdcard/DCIM/Camera/$(echo "$ROW2" | cut -d'|' -f2)" > "$ROW_DIR/take_off.mp4"
assert_eq "the screen-off take plays: one video stream" "1 video" "$(streams "$ROW_DIR/take_off.mp4")"
assert_eq "no pending row of the shell's" "0" "$(pending_rows)"

# --- restore -------------------------------------------------------------------------------------------------------
adb shell am force-stop app.tileshell
[ "$MIC_BEFORE" -ge 1 ] && mic_on
assert_eq "RECORD_AUDIO as it was" "$MIC_BEFORE" "$(granted RECORD_AUDIO)"
remove_rows_above $VIDEOS "$MAXV"
assert_eq "videos count restored" "$COUNT0" "$(count_rows $VIDEOS)"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
ensure_start
row_end
