#!/usr/bin/env bash
# Phase 17 E8 — video capture (r3 D12 / V4: no row uses the microphone or the host's audio).
#
#   start   layout_restore of the baseline; media_up with no names (the census); `pm revoke … RECORD_AUDIO` for the row
#           (mic_off, asserted) BEFORE the Camera is opened — revoking a runtime permission restarts the app's processes.
#   take    CameraActivity, the mode disc to video (camera_mode:video selected, camera_record in the dump); tap
#           camera_record, wait 5 s, tap it again → videos = census + 1, the row's duration ≥ 4500, relative_path
#           DCIM/Camera/, is_pending 0.
#   file    the row's file pulled (its size = the row's, so an unread file cannot pass): `ffprobe -show_streams` shows
#           ONE video stream and NO audio stream.
#   sound   the :camera slice (from the MARK before the first tap) holds `[camera] video sound: off (no microphone
#           permission)`, and while recording the dump's camera_sound_hint names the Microphone row.
#   restore `pm grant` RECORD_AUDIO back (mic_on, asserted); media_down; Start.
#
# The sound itself is P1's (the phone). Changes on the device: one video in DCIM/Camera (removed), RECORD_AUDIO
# (granted back).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"

cam_install E8
row_begin E8 "video capture with RECORD_AUDIO revoked: one video stream, no audio stream, the sound-off line and hint"
cam_preamble
D="$ROW_DIR"
layout_restore "$BASELINE" > "$D/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
ensure_start
media_up
MIC_BEFORE="$(perm_granted RECORD_AUDIO)"; record "RECORD_AUDIO before the row" "$MIC_BEFORE"
rings_save
mic_off
trap 'adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1' EXIT

log "--- the take"
fresh_camera
assert_eq "CameraActivity is resumed" "$CAMERA_ACTIVITY" "$(top_activity)"
cgdump "$D/photo.xml"
assert_eq "the viewfinder opens on Photo" "true" "$(node_attr "$D/photo.xml" camera_mode:photo selected)"
tap_node "$D/photo.xml" "camera_disc:video"; sleep 4
cgdump "$D/video.xml"
assert_eq "the mode disc switches to Video (camera_mode:video selected)" "true" "$(node_attr "$D/video.xml" camera_mode:video selected)"
assert_eq "the shutter is camera_record in Video" "yes no" "$(has_node "$D/video.xml" camera_record) $(has_node "$D/video.xml" camera_shutter)"
XY="$(centre_px "$D/video.xml" camera_record)"
MARK="$(ring_mark)"
# shellcheck disable=SC2086
adb shell input tap $XY
T_START="$(device_ms)"
sleep 2
cgdump "$D/recording.xml"; screencap "$D/recording.png"
HINT="$(node_text "$D/recording.xml" camera_sound_hint)"; record "the hint on screen while recording" "$HINT"
assert_eq "the hint node is on screen (dump)" "yes" "$(has_node "$D/recording.xml" camera_sound_hint)"
assert_contains "the hint names the Microphone row" "Microphone" "$HINT"
assert_contains "the recording clock runs" "00:0" "$(node_text "$D/recording.xml" camera_rec_time)"
# "wait 5 s": the second tap lands 5 s after the first (the dump above took part of it).
NOW="$(device_ms)"; REST=$(( 5000 - (NOW - T_START) )); [ "$REST" -gt 0 ] && sleep "$(python3 -c "print($REST/1000)")"
# shellcheck disable=SC2086
adb shell input tap $XY
record "ms between the two taps" "$(( $(device_ms) - T_START ))"
sleep 6
S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/slice.txt"
assert_contains "[camera] video sound: off (no microphone permission)" "[camera] video sound: off (no microphone permission)" "$S"
SAVED="$(printf '%s\n' "$S" | grep -o 'saved content://[^ ]* [0-9]*x[0-9]*' | head -1)"; record "the saved line" "$SAVED"
assert_ne "[camera] saved <uri> <w>x<h>" "" "$SAVED"
assert_eq "videos count +1" "$((CENSUS_VIDEO + 1))" "$(media_count video)"
ROW1="$(shell_rows video | head -1)"; record "the new row (id|name|w|h|pending|size|path|date_added|duration)" "$ROW1"
assert_eq "relative_path" "DCIM/Camera/" "$(row_field "$ROW1" 7)"
assert_eq "is_pending" "0" "$(row_field "$ROW1" 5)"
DUR="$(row_field "$ROW1" 9)"
if [ "${DUR:-0}" -ge 4500 ] 2>/dev/null; then _verdict PASS "duration ≥ 4500" "$DUR"; else _verdict FAIL "duration ≥ 4500" "${DUR:-none}"; fi
assert_contains "the saved line names the row" "/$(row_field "$ROW1" 1) " "$SAVED "

log "--- the file"
pull_dcim "$(row_field "$ROW1" 2)" "$D/take.mp4"
assert_eq "the pulled file is the row's size" "$(row_field "$ROW1" 6)" "$(stat -c%s "$D/take.mp4")"
ffprobe -v error -show_streams "$D/take.mp4" > "$D/ffprobe.txt" 2> "$D/ffprobe.err"; assert_eq "ffprobe -show_streams reads it" "0" "$?"
assert_eq "one video stream" "1" "$(grep -c '^codec_type=video' "$D/ffprobe.txt")"
assert_eq "NO audio stream" "0" "$(grep -c '^codec_type=audio' "$D/ffprobe.txt")"
record "ffprobe" "$(stream_facts "$D/take.mp4")"

log "--- restore"
no_crash
assert_eq "no pending row of the shell's" "0" "$(pending_rows)"
c6
mic_on
trap - EXIT
ensure_start
media_down
row_end
