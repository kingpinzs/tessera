#!/usr/bin/env bash
# Phase 17 EDGE, the Camera's bullets (row EDGE_CAMERA) — the Edge-cases bullets that name the camera, recording, a
# capture-intent caller, or the PHOTOS / CAMERA slots re-pointed. One function `edge_<ID>` per bullet (or per clause of
# a bullet), each from its own MARK with its own fixtures and its own restore; scripts/edge_index_camera.tsv maps the
# doc's bullets to them.
#
#   env -u TMPDIR bash edge_camera.sh             every sub-step, in the order below
#   env -u TMPDIR bash edge_camera.sh CALL FRONT  only those (development; the gate run names none)
#
#   STORAGE    storage full while recording        fill_volume 3000000 (C-27), a take with RECORD_AUDIO revoked → the
#                                                  recorder stops with "storage full"; no row left pending; a row that
#                                                  was published plays; unfill_volume
#   INUSE      camera in use by another app        Open Camera in front, then ours via am start; the other app in
#                                                  front again; ours resumed (`[camera] busy`, then `[camera]
#                                                  devices=1`); the reverse order evicts ours cleanly
#   REVOKE     CAMERA revoked, viewfinder open     the platform restarts the process; the next open shows the grant page
#   SCREENOFF  screen off mid-recording            KEYCODE_SLEEP → the take is stopped and finalised and plays;
#                                                  wake_device asserted Awake before the next tap (C-25)
#   CALL       an incoming call mid-recording      `adb emu gsm call 5551234` → the same; `adb emu gsm cancel 5551234`
#   KILLWRITE  :camera killed mid-write            the process is killed (root, by its recorded pid, undone at once)
#                                                  while its pending file is on disk; the next start cleans the
#                                                  is_pending=1 row
#   CALLER     a capture caller cancelled / killed Back mid-take → RESULT_CANCELED, no orphan; the caller force-stopped
#              mid-capture                         mid-take → no orphan file, no pending row
#   FRONT      EXTRA_USE_FRONT_CAMERA on the AVD   the back camera answers, `[camera] devices=1 front=absent`, RESULT_OK
#   REPOINT    PHOTOS / CAMERA re-pointed          a copy of the baseline with the two slots on other apps: the shell's
#                                                  Camera and Photos still resolve as launcher apps and still open;
#                                                  Tess's "take a photo" (type_request) opens the app the CAMERA slot
#                                                  names; the slots pointed back
#
# No sub-step uses the microphone: every one that records runs between mic_off and mic_on.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/cam17.sh"
ALL="STORAGE INUSE REVOKE SCREENOFF CALL KILLWRITE CALLER FRONT REPOINT"
INDEX="$HERE/edge_index_camera.tsv"

# A sub-step's start: the baseline's Start, the census, a fresh ring folder name.
sub_begin() { # id
  E="$1"; D="$ROW_DIR"
  CRASH_T0="$(qa_time)"
  ensure_start
  media_up
}
sub_end() {
  # The crash buffer since the sub-step began, kept beside the row when it holds a crash of the shell's.
  [ -n "$(crash_lines)" ] && adb logcat -d -b crash -v threadtime -T "$CRASH_T0" 2>/dev/null | tr -d '\r' > "$D/$E-crash.txt"
  assert_eq "$E: no crash of the shell in logcat (the crash buffer since the sub-step began)" "" "$(crash_lines)"
  assert_eq "$E: no pending row of the shell's at the end" "0" "$(pending_rows)"
  c6; ensure_start
  media_down
}
# The viewfinder in Video on a fresh process, ready to record. Call between mic_off and mic_on.
video_ready() { # tag
  fresh_camera video
  cgdump "$D/$1-video.xml"
  assert_eq "$E: the viewfinder is in Video (camera_record)" "true yes" "$(node_attr "$D/$1-video.xml" camera_mode:video selected) $(has_node "$D/$1-video.xml" camera_record)"
}
# The newest video row of the shell's, pulled and probed: "plays" = ffprobe reads one video stream.
assert_plays() { # label row out.mp4
  pull_dcim "$(row_field "$2" 2)" "$3"
  assert_eq "$1: the pulled file is the row's size" "$(row_field "$2" 6)" "$(stat -c%s "$3")"
  assert_eq "$1: the file plays (ffprobe: one video stream, no audio)" "1 video" "$(streams "$3")"
}

# ---------------------------------------------------------------------------------------------------- STORAGE
edge_STORAGE() {
  sub_begin STORAGE
  rings_save; mic_off
  record "STORAGE: df /sdcard before the fill" "$(adb shell df -k /sdcard | awk 'NR==2 {print $4 " KB free"}' | tr -d '\r')"
  c6; ensure_start
  fill_volume 3000000; assert_eq "STORAGE: fill_volume 3000000 took (free ≤ leave + 5 MB, its own check)" "0" "$?"
  record "STORAGE: df /sdcard after the fill" "$(adb shell df -k /sdcard | awk 'NR==2 {print $4 " KB free"}' | tr -d '\r')"
  # The Camera is opened on the full volume and the take started (the bullet's order: fill, then start recording).
  OPEN_MARK="$(ring_mark)"
  open_camera video
  record "STORAGE: camera ready on the full volume after (s)" "$(wait_camera "$OPEN_MARK")"
  cgdump "$D/STORAGE-video.xml" || true
  cam_since "$OPEN_MARK" > "$D/STORAGE-slice-open.txt"
  assert_eq "STORAGE: the viewfinder is in Video on the full volume (camera_record)" "true yes" "$(node_attr "$D/STORAGE-video.xml" camera_mode:video selected) $(has_node "$D/STORAGE-video.xml" camera_record)"
  local MARK S i ROW
  MARK="$OPEN_MARK"
  tap_node "$D/STORAGE-video.xml" camera_record
  local CP0; CP0="$(adb shell pidof app.tileshell:camera | tr -d '\r')"
  : > "$D/STORAGE-slice.txt"
  for i in $(seq 1 45); do
    S="$(cam_since "$MARK")"
    # Kept at every poll: if the process dies with the disk full, its ring dies with it.
    [ -n "$S" ] && printf '%s\n' "$S" > "$D/STORAGE-slice.txt"
    grep -qE 'recording ended|save failed|\[camera\] saved ' "$D/STORAGE-slice.txt" && break
    [ -z "$(adb shell pidof app.tileshell:camera | tr -d '\r')" ] && break
    sleep 1
  done
  record "STORAGE: seconds until the recorder ended by itself" "$i"
  sleep 3
  S="$(cam_since "$MARK")"; [ -n "$S" ] && printf '%s\n' "$S" > "$D/STORAGE-slice.txt"
  S="$(cat "$D/STORAGE-slice.txt")"
  record "STORAGE: the :camera pid before the take / after it" "$CP0 / $(adb shell pidof app.tileshell:camera | tr -d '\r')"
  adb logcat -d -t 600 2>/dev/null | tr -d '\r' | grep -E 'app.tileshell|Recorder|ENOSPC|No space|CameraX|lowmemorykiller|am_kill|Process .* has died' | tail -60 > "$D/STORAGE-logcat.txt"
  cgdump "$D/STORAGE-after.xml" || true
  record "STORAGE: what is on screen after (top activity; saving / toast nodes)" "$(top_activity) saving=$(has_node "$D/STORAGE-after.xml" camera_saving) toast=[$(node_text "$D/STORAGE-after.xml" camera_toast)] rec_time=$(has_node "$D/STORAGE-after.xml" camera_rec_time)"
  screencap "$D/STORAGE-after.png"
  record "STORAGE: the :camera lines of the take" "$(printf '%s\n' "$S" | grep -F '[camera]' | sed 's/^[^[]*//; s/ *wall=.*//' | tr '\n' ';')"
  assert_contains "STORAGE: the recorder stops with \"storage full\"" "storage full" "$S"
  assert_contains "STORAGE: … said on screen too (the toast's line)" "toast: Storage full" "$S"
  assert_eq "STORAGE: nothing is left pending (is_pending=1 rows of the shell's)" "0" "$(pending_rows)"
  unfill_volume
  record "STORAGE: df /sdcard after unfill_volume" "$(adb shell df -k /sdcard | awk 'NR==2 {print $4 " KB free"}' | tr -d '\r')"
  assert_eq "STORAGE: restore — fill.bin is gone" "" "$(adb shell ls /sdcard/fill.bin 2>/dev/null | tr -d '\r')"
  assert_eq "STORAGE: … never left pending: no pending file of a take on disk (root's read)" "files=0 rows=0" "$(root_pending "$D/STORAGE-pending-after.txt")"
  assert_eq "STORAGE: restore — adb is not root" "shell" "$(adb shell whoami | tr -d '\r')"
  if [ "$(media_count video)" -gt "$CENSUS_VIDEO" ]; then
    ROW="$(shell_rows video | head -1)"; record "STORAGE: the partial file was finalised as a row" "$ROW"
    assert_eq "STORAGE: the partial file is finalised (is_pending 0)" "0" "$(row_field "$ROW" 5)"
    assert_plays "STORAGE: the finalised partial take" "$ROW" "$D/STORAGE-partial.mp4"
  else
    record "STORAGE: the partial file" "removed — no new video row (the doc's other allowed outcome)"
    assert_eq "STORAGE: the videos count is the census's (the partial file was removed)" "$CENSUS_VIDEO" "$(media_count video)"
  fi
  c6; mic_on
  sub_end
}

# ---------------------------------------------------------------------------------------------------- INUSE
edge_INUSE() {
  sub_begin INUSE
  local OC OCPKG MARK S
  OC="$(adb shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA | tr -d '\r ' | grep -m1 'opencamera')"
  OCPKG="${OC%%/*}"
  record "INUSE: Open Camera's component (the fixture from phase 01 E4)" "${OC:-(not installed)}"
  assert_ne "INUSE: Open Camera is installed" "" "$OC"
  rings_save
  adb shell am force-stop app.tileshell; adb shell am force-stop "$OCPKG"
  # Open Camera in the foreground, then ours via am start.
  adb shell am start -n "$OC" >/dev/null 2>&1; sleep 5
  assert_contains "INUSE: Open Camera is in front" "$OCPKG" "$(top_activity)"
  record "INUSE: the camera's client with Open Camera in front" "$(media_camera | grep -m1 'Client package:' | xargs)"
  MARK="$(ring_mark)"
  open_camera; sleep 3
  record "INUSE: camera ready in ours after (s)" "$(wait_camera "$MARK")"
  assert_eq "INUSE: ours is in front after am start" "$CAMERA_ACTIVITY" "$(top_activity)"
  assert_contains "INUSE: Android's camera service gives the foreground app (ours) the device" "Client package: app.tileshell" "$(media_camera)"
  cgdump "$D/INUSE-ours.xml"
  assert_eq "INUSE: ours shows the viewfinder, not \"camera in use\", while it is in front" "yes no" "$(has_node "$D/INUSE-ours.xml" camera_shutter) $(has_node "$D/INUSE-ours.xml" camera_busy)"
  # The other app in front again: the service hands it the device; then ours resumed.
  adb shell am start -n "$OC" >/dev/null 2>&1; sleep 5
  assert_contains "INUSE: Open Camera is in front again" "$OCPKG" "$(top_activity)"
  assert_contains "INUSE: … and holds the device" "Client package: $OCPKG" "$(media_camera)"
  adb shell am start -n "$CAMERA_ACTIVITY" >/dev/null 2>&1; sleep 6
  cgdump "$D/INUSE-resumed.xml"
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/INUSE-slice.txt"
  record "INUSE: the :camera lines of the hand-over" "$(printf '%s\n' "$S" | grep -E '\[camera\] (busy|devices=)' | sed 's/^[^[]*//; s/ *wall=.*//' | tr '\n' ';')"
  assert_contains "INUSE: [camera] busy: <reason> while the other app held the device" "[camera] busy: " "$S"
  assert_eq "INUSE: … then [camera] devices=1 after it (the camera re-opened on resume)" "yes" "$(printf '%s\n' "$S" | python3 -c '
import sys
seen = False; ok = "no"
for line in sys.stdin:
    if "[camera] busy: " in line: seen = True
    elif seen and "[camera] devices=1" in line: ok = "yes"
print(ok)')"
  assert_contains "INUSE: … and the camera service shows ours holding the device again" "Client package: app.tileshell" "$(media_camera)"
  assert_eq "INUSE: ours shows the viewfinder again on resume" "yes no" "$(has_node "$D/INUSE-resumed.xml" camera_shutter) $(has_node "$D/INUSE-resumed.xml" camera_busy)"
  # The reverse order: ours first, then the other app → ours is evicted cleanly.
  adb shell am start -n "$OC" >/dev/null 2>&1; sleep 5
  assert_contains "INUSE (reverse): Open Camera, started over ours, holds the device" "Client package: $OCPKG" "$(media_camera)"
  assert_ne "INUSE (reverse): ours was evicted cleanly — its process is alive" "" "$(adb shell pidof app.tileshell:camera | tr -d '\r')"
  assert_eq "INUSE (reverse): … with no crash" "" "$(crash_lines)"
  adb shell am force-stop "$OCPKG"
  sub_end
}

# ---------------------------------------------------------------------------------------------------- REVOKE
edge_REVOKE() {
  sub_begin REVOKE
  local CPID MARK
  fresh_camera
  cgdump "$D/REVOKE-vf.xml"
  assert_eq "REVOKE: the viewfinder is open" "yes" "$(has_node "$D/REVOKE-vf.xml" camera_shutter)"
  CPID="$(adb shell pidof app.tileshell:camera | tr -d '\r')"; record "REVOKE: the :camera pid with the viewfinder open" "$CPID"
  assert_ne "REVOKE: :camera runs" "" "$CPID"
  rings_save
  adb shell pm revoke app.tileshell android.permission.CAMERA; sleep 3
  assert_eq "REVOKE: CAMERA is revoked" "false" "$(perm_granted CAMERA)"
  assert_ne "REVOKE: the platform restarted the process (the pid that held the viewfinder is gone)" "$CPID" "$(adb shell pidof app.tileshell:camera | tr -d '\r')"
  record "REVOKE: what is in front after the revoke" "$(top_activity)"
  MARK="$(ring_mark)"
  open_camera
  cgdump "$D/REVOKE-next.xml"; screencap "$D/REVOKE-next.png"
  assert_eq "REVOKE: the next open shows the grant page" "yes no" "$(has_node "$D/REVOKE-next.xml" camera_grant) $(has_node "$D/REVOKE-next.xml" camera_shutter)"
  assert_contains "REVOKE: [camera] no camera permission: grant page" "[camera] no camera permission: grant page" "$(cam_since "$MARK")"
  adb shell pm grant app.tileshell android.permission.CAMERA
  assert_eq "REVOKE: restore — CAMERA granted back" "true" "$(perm_granted CAMERA)"
  sub_end
}

# ---------------------------------------------------------------------------------------------------- SCREENOFF / CALL
# A take interrupted after 3 s by <interrupt>; the take must be stopped, finalised and playable.
interrupted_take() { # id interrupt-function undo-function
  local MARK S ROW
  rings_save; mic_off
  video_ready "$1"
  MARK="$(ring_mark)"
  tap_node "$D/$1-video.xml" camera_record; sleep 3
  cgdump "$D/$1-recording.xml"
  assert_eq "$1: the take is running (the recording clock is on screen)" "yes" "$(has_node "$D/$1-recording.xml" camera_rec_time)"
  "$2"
  sleep 6
  screencap "$D/$1-interrupted.png"
  "$3"
  sleep 2
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/$1-slice.txt"
  record "$1: the :camera lines of the take" "$(printf '%s\n' "$S" | grep -F '[camera]' | sed 's/^[^[]*//; s/ *wall=.*//' | tr '\n' ';')"
  assert_contains "$1: the recording stops" "[camera] recording stopped: " "$S"
  assert_contains "$1: … and is saved" "[camera] saved content://" "$S"
  assert_eq "$1: one new video row" "$((CENSUS_VIDEO + 1))" "$(media_count video)"
  ROW="$(shell_rows video | head -1)"; record "$1: the row" "$ROW"
  assert_eq "$1: finalised — DCIM/Camera/, is_pending 0" "DCIM/Camera/ 0" "$(row_field "$ROW" 7) $(row_field "$ROW" 5)"
  assert_plays "$1" "$ROW" "$D/$1-take.mp4"
  c6; mic_on
}
_sleep_key() { adb shell input keyevent KEYCODE_SLEEP; }
_wake() { assert_eq "SCREENOFF: wake_device printed Awake before the next tap (C-25)" "Awake" "$(wake_device)"; }
edge_SCREENOFF() {
  sub_begin SCREENOFF
  interrupted_take SCREENOFF _sleep_key _wake
  assert_contains "SCREENOFF: the reason is the screen going off" "[camera] recording stopped: screen off" "$(cat "$D/SCREENOFF-slice.txt")"
  sub_end
}
_call() { adb emu gsm call 5551234 > "$D/CALL-gsm-call.out" 2>&1; }
_hangup() { adb emu gsm cancel 5551234 > "$D/CALL-gsm-cancel.out" 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; }
edge_CALL() {
  sub_begin CALL
  interrupted_take CALL _call _hangup
  record "CALL: adb emu gsm call / cancel answered" "$(tr '\n' ' ' < "$D/CALL-gsm-call.out") / $(tr '\n' ' ' < "$D/CALL-gsm-cancel.out")"
  record "CALL: the reason the take logged" "$(grep -o 'recording stopped: [a-z ]*' "$D/CALL-slice.txt" | head -1)"
  assert_absent "CALL: restore — no call is left (telephony registry idle)" "mCallState=1" "$(adb shell dumpsys telephony.registry | tr -d '\r' | grep -m1 'mCallState=')"
  sub_end
}

# ---------------------------------------------------------------------------------------------------- KILLWRITE
# What is really pending, read as root (the shell user's `content query` and its view of /sdcard do not show another
# package's pending rows and files — the sub-step's own control shows that): the `.pending-` files on disk under
# DCIM/Camera and MediaStore's is_pending=1 video rows. Root is undone at once. Prints "files=<n> rows=<n>"; the
# listing goes to <out>.
root_pending() { # out.txt
  adb root >/dev/null 2>&1; sleep 2; adb wait-for-device
  { echo "# ls -a /data/media/0/DCIM/Camera | grep '^\\.pending-'"; adb shell "ls -la /data/media/0/DCIM/Camera | grep '\\.pending-'" | tr -d '\r'
    echo "# content query (as root) … video/media --where is_pending=1"; adb shell "content query --uri content://media/external/video/media --projection _id:_display_name:is_pending:owner_package_name:_size --where 'is_pending=1'" | tr -d '\r'; } > "$1" 2>&1
  adb unroot >/dev/null 2>&1; sleep 2; adb wait-for-device; sleep 1
  echo "files=$(grep -c '\.pending-.*VID_' "$1") rows=$(grep -c 'owner_package_name=app.tileshell' "$1")"
}
ledger() { adb shell run-as app.tileshell cat files/media_pending/app.tileshell_camera.txt 2>/dev/null | tr -d '\r'; adb shell run-as app.tileshell cat files/media_pending/camera.txt 2>/dev/null | tr -d '\r'; }
edge_KILLWRITE() {
  sub_begin KILLWRITE
  local CPID MARK S HIT SEEN AFTER
  rings_save; mic_off
  video_ready KILLWRITE
  tap_node "$D/KILLWRITE-video.xml" camera_record; sleep 12       # a long take: a longer copy through the write layer
  CPID="$(adb shell pidof app.tileshell:camera | tr -d '\r')"; record "KILLWRITE: the :camera pid" "$CPID"
  case "$CPID" in ''|*[!0-9]*) _verdict FAIL "KILLWRITE: a single numeric :camera pid" "[$CPID]"; c6; mic_on; sub_end; return ;; esac
  rings_save
  adb root >/dev/null 2>&1; sleep 2; adb wait-for-device
  # On the device, as root: wait (at most 20 s) for MediaStore's pending file to appear in DCIM/Camera — the write layer's "pending"
  # stage — and kill the recorded pid at once. The stop tap is sent from here just after the watch starts.
  # shellcheck disable=SC2046
  ( sleep 1; adb shell input tap $(centre_px "$D/KILLWRITE-video.xml" camera_record) ) &
  HIT="$(adb shell "end=\$(( \$(date +%s) + 20 )); f=; while [ \$(date +%s) -lt \$end ]; do f=\$(ls -a /data/media/0/DCIM/Camera 2>/dev/null | grep -m1 '^\.pending-'); if [ -n \"\$f\" ]; then kill -9 $CPID; echo \"killed with \$f on disk\"; break; fi; done; [ -n \"\$f\" ] || echo 'no pending file seen in 20 s'" | tr -d '\r')"
  wait
  adb unroot >/dev/null 2>&1; sleep 2; adb wait-for-device; sleep 1
  assert_eq "KILLWRITE: adb is unrooted again" "shell" "$(adb shell whoami | tr -d '\r')"
  record "KILLWRITE: the watch" "$HIT"
  assert_contains "KILLWRITE: :camera was killed while its pending file was on disk (mid-write)" "killed with .pending-" "$HIT"
  assert_ne "KILLWRITE: the killed process is gone" "$CPID" "$(adb shell pidof app.tileshell:camera | tr -d '\r')"
  SEEN="$(root_pending "$D/KILLWRITE-pending-after-kill.txt")"
  record "KILLWRITE: pending after the kill, read as root" "$SEEN"
  record "KILLWRITE: … the same read by the shell user (content query --where is_pending=1)" "$(pending_rows) — the control: the shell user's read is blind to it when root's is not"
  record "KILLWRITE: the :camera process's pending ledger after the kill" "$(ledger | tr '\n' ' ')"
  # `content query` cannot ask MediaStore to match pending rows (root's read shows none either), so the state is read
  # from what it leaves: the pending file on disk and the URI in the process's own pending ledger.
  assert_eq "KILLWRITE: the kill left the pending file on disk (the state the bullet starts from)" "files=1" "$(echo "$SEEN" | cut -d' ' -f1)"
  assert_contains "KILLWRITE: … and its row's URI in :camera's pending ledger" "content://media/" "$(ledger)"
  assert_eq "KILLWRITE: adb is unrooted after the read" "shell" "$(adb shell whoami | tr -d '\r')"
  # The next start cleans it.
  MARK="$(ring_mark)"
  open_camera; record "KILLWRITE: camera ready after (s)" "$(wait_camera "$MARK")"
  sleep 2
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/KILLWRITE-slice.txt"
  record "KILLWRITE: the next start's first :camera lines" "$(printf '%s\n' "$S" | head -6 | sed 's/^[^[]*//; s/ *wall=.*//' | tr '\n' ';')"
  assert_contains "KILLWRITE: the next start says it cleaned its leftover" "[camera] cleaned 1 pending row(s) left by an earlier run" "$S"
  rings_save
  AFTER="$(root_pending "$D/KILLWRITE-pending-after-restart.txt")"
  record "KILLWRITE: pending after the next start, read as root" "$AFTER"
  assert_eq "KILLWRITE: on the next start the shell's pending row is cleaned (no pending file left, root's read)" "files=0 rows=0" "$AFTER"
  assert_eq "KILLWRITE: … and the ledger is empty" "" "$(ledger)"
  assert_eq "KILLWRITE: content query … is_pending shows none (the shell user's read, as the doc words it)" "0" "$(pending_rows)"
  # Whatever is still pending is removed here, as root, so the device is left clean whatever the product did.
  if [ "$AFTER" != "files=0 rows=0" ]; then
    adb root >/dev/null 2>&1; sleep 2; adb wait-for-device
    adb shell "content delete --uri content://media/external/video/media --where \"is_pending=1 AND owner_package_name='app.tileshell'\"" >/dev/null 2>&1
    adb shell "rm -f /data/media/0/DCIM/Camera/.pending-*VID_*" >/dev/null 2>&1
    adb unroot >/dev/null 2>&1; sleep 2; adb wait-for-device; sleep 1
    record "KILLWRITE: restore — what was left pending was removed as root" "$(root_pending "$D/KILLWRITE-pending-after-restore.txt")"
  fi
  c6; mic_on
  sub_end
}

# ---------------------------------------------------------------------------------------------------- CALLER
edge_CALLER() {
  sub_begin CALLER
  local T0 MARK L S
  rings_save; mic_off
  install_qac
  # Cancelled mid-capture: Back while the take runs.
  adb shell am force-stop "$QAC"; T0="$(qa_time)"; MARK="$(ring_mark)"
  start_leg video-content; sleep 2; wait_camera "$MARK" >/dev/null
  cgdump "$D/CALLER-back-vf.xml"
  assert_eq "CALLER (cancelled): the capture page is on top" "$CAPTURE_ACTIVITY" "$(top_activity)"
  tap_node "$D/CALLER-back-vf.xml" camera_record; sleep 2.5
  adb shell input keyevent KEYCODE_BACK
  L="$(leg_lines "$T0" video-content 12)"; printf '%s\n' "$L" | sed 's/^/      /' >> "$LOG"
  assert_contains "CALLER (cancelled): RESULT_CANCELED" "result=RESULT_CANCELED" "$L"
  assert_contains "CALLER (cancelled): no orphan file at the caller's path" "output exists=false" "$L"
  sleep 2
  assert_eq "CALLER (cancelled): no pending row" "0" "$(pending_rows)"
  assert_eq "CALLER (cancelled): no DCIM row either" "$CENSUS_VIDEO $CENSUS_IMAGES" "$(media_count video) $(media_count images)"
  # Killed mid-capture: the caller force-stopped while the take runs.
  adb shell am force-stop "$QAC"; T0="$(qa_time)"; MARK="$(ring_mark)"
  start_leg video-content; sleep 2; wait_camera "$MARK" >/dev/null
  cgdump "$D/CALLER-kill-vf.xml"
  tap_node "$D/CALLER-kill-vf.xml" camera_record; sleep 2.5
  adb shell am force-stop "$QAC"; sleep 4
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/CALLER-kill-slice.txt"
  record "CALLER (killed): what is in front after the caller died" "$(top_activity)"
  record "CALLER (killed): the :camera lines" "$(printf '%s\n' "$S" | grep -F '[camera]' | sed 's/^[^[]*//; s/ *wall=.*//' | tail -6 | tr '\n' ';')"
  adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 2
  assert_eq "CALLER (killed): no pending row" "0" "$(pending_rows)"
  assert_eq "CALLER (killed): no orphan row in MediaStore" "$CENSUS_VIDEO $CENSUS_IMAGES" "$(media_count video) $(media_count images)"
  assert_eq "CALLER (killed): no orphan file at the caller's path" "" "$(adb shell run-as "$QAC" ls cache/out.mp4 2>/dev/null | tr -d '\r')"
  absent_in "CALLER (killed): nothing was written to the caller's output" "the caller's output" "$S"
  c6
  uninstall_qac
  mic_on
  sub_end
}

# ---------------------------------------------------------------------------------------------------- FRONT
edge_FRONT() {
  sub_begin FRONT
  local T0 MARK L S
  rings_save
  install_qac
  adb shell am force-stop app.tileshell; adb shell am force-stop "$QAC"
  T0="$(qa_time)"; MARK="$(ring_mark)"
  start_leg image-none --ez front true; sleep 2; record "FRONT: camera ready after (s)" "$(wait_camera "$MARK")"
  cgdump "$D/FRONT-vf.xml"
  assert_eq "FRONT: the capture page is on top, with a viewfinder (never an error page)" "$CAPTURE_ACTIVITY yes no no" "$(top_activity) $(has_node "$D/FRONT-vf.xml" camera_shutter) $(has_node "$D/FRONT-vf.xml" camera_nocamera) $(has_node "$D/FRONT-vf.xml" camera_busy)"
  S="$(cam_since "$MARK")"; printf '%s\n' "$S" > "$D/FRONT-slice.txt"
  assert_contains "FRONT: the back camera answers — [camera] devices=1 front=absent" "[camera] devices=1 front=absent" "$S"
  assert_contains "FRONT: dumpsys media.camera — the one device is open for the shell" "Client package: app.tileshell" "$(media_camera)"
  tap_node "$D/FRONT-vf.xml" camera_shutter; sleep 3
  cgdump "$D/FRONT-review.xml"; tap_node "$D/FRONT-review.xml" capture_accept
  L="$(leg_lines "$T0" image-none 12)"; printf '%s\n' "$L" | sed 's/^/      /' >> "$LOG"
  assert_contains "FRONT: the request carried the extra and did not throw" "start threw=none" "$L"
  assert_contains "FRONT: RESULT_OK" "result=RESULT_OK" "$L"
  absent_in "FRONT: no capture failed line" "capture failed" "$(cam_since "$MARK")"
  c6
  uninstall_qac
  sub_end
}

# ---------------------------------------------------------------------------------------------------- REPOINT
edge_REPOINT() {
  sub_begin REPOINT
  local OC OCPKG GAL LAUNCHERS MARK TOP REPLY
  flat() { python3 -c 'import sys; p, c = sys.argv[1].split("/"); print(p + "/" + (p + c if c.startswith(".") else c))' "$1"; }
  OC="$(adb shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA | tr -d '\r ' | grep '/' | grep -v '^app.tileshell/' | grep -m1 'opencamera')"
  [ -n "$OC" ] || OC="$(adb shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA | tr -d '\r ' | grep '/' | grep -v -m1 '^app.tileshell/')"
  GAL="$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_GALLERY | tr -d '\r ' | grep '/' | grep -v -m1 '^app.tileshell/')"
  OCPKG="${OC%%/*}"
  record "REPOINT: the other camera (CAMERA slot) and the other gallery (PHOTOS slot)" "${OC:-none} / ${GAL:-none}"
  assert_ne "REPOINT: another camera app is installed (phase 01 E4's fixtures)" "" "$OC"
  assert_ne "REPOINT: another gallery app is installed" "" "$GAL"
  python3 - "$BASELINE" "$D/REPOINT-layout.json" "$(flat "$OC")" "$(flat "$GAL")" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
d["slots"]["CAMERA"] = sys.argv[3]; d["slots"]["PHOTOS"] = sys.argv[4]
json.dump(d, open(sys.argv[2], "w"), indent=2)
PY
  rings_save
  MARK="$(ring_mark)"
  layout_restore "$D/REPOINT-layout.json" > "$D/REPOINT-restore.out" 2>&1; assert_eq "REPOINT: layout_restore of the baseline copy with both slots re-pointed" "0" "$?"
  ensure_start
  assert_eq "REPOINT: slots.CAMERA names the other camera" "$(flat "$OC")" "$(slot_of CAMERA)"
  assert_eq "REPOINT: slots.PHOTOS names the other gallery" "$(flat "$GAL")" "$(slot_of PHOTOS)"
  absent_in "REPOINT: the seed does not take the slots back (its markers have run)" "-> assigned" "$(ring_since "$MARK" | grep -F 'assignSlotOnce')"
  # The shell's apps stay in the app list (its source: the launcher activities) and keep working.
  LAUNCHERS="$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | tr -d '\r ' | grep '^app.tileshell/' | xargs)"
  record "REPOINT: the shell's launcher activities" "$LAUNCHERS"
  assert_contains "REPOINT: the shell's Camera is still a launcher app" " $CAMERA_ACTIVITY " " $LAUNCHERS "
  assert_contains "REPOINT: the shell's Photos is still a launcher app" " $PHOTOS_ACTIVITY " " $LAUNCHERS "
  MARK="$(ring_mark)"
  open_camera; record "REPOINT: camera ready after (s)" "$(wait_camera "$MARK")"
  cgdump "$D/REPOINT-camera.xml"
  assert_eq "REPOINT: the shell's Camera keeps working (its viewfinder)" "$CAMERA_ACTIVITY yes" "$(top_activity) $(has_node "$D/REPOINT-camera.xml" camera_shutter)"
  adb shell am start -n "$PHOTOS_ACTIVITY" >/dev/null 2>&1; sleep 4
  dump_ui "$D/REPOINT-photos.xml"
  assert_eq "REPOINT: the shell's Photos keeps working (its collection pivot)" "$PHOTOS_ACTIVITY yes" "$(top_activity) $(has_node "$D/REPOINT-photos.xml" photos_pivot:collection)"
  c6; ensure_start
  # The bottom row's CAMERA tile follows the slot.
  gdump "$D/REPOINT-start.xml"
  tap_node "$D/REPOINT-start.xml" tile:dock:slot:CAMERA; sleep 5
  assert_contains "REPOINT: the bottom row's Camera tile opens the app the slot names" "$OCPKG/" "$(top_activity)/"
  adb shell am force-stop "$OCPKG"; c6; ensure_start
  # Tess's "take a photo" follows the slot (typed: no microphone).
  cortana_assist; sleep 5
  dump_ui "$D/REPOINT-tess.xml"
  if [ "$(has_node "$D/REPOINT-tess.xml" cortana_session)" != yes ]; then adb shell cmd voiceinteraction show >/dev/null 2>&1; sleep 5; dump_ui "$D/REPOINT-tess.xml"; fi
  assert_eq "REPOINT: Tess is open" "yes" "$(has_node "$D/REPOINT-tess.xml" cortana_session)"
  MARK="$(ring_mark)"
  type_request "take a photo" 8; assert_eq "REPOINT: type_request found the text box" "0" "$?"
  TOP="$(top_activity)"; REPLY="$(reply_since "$MARK")"
  screencap "$D/REPOINT-after.png"
  record "REPOINT: Tess's reply" "$REPLY"
  assert_contains "REPOINT: Tess's \"take a photo\" opens the app the CAMERA slot names" "$OCPKG/" "$TOP/"
  assert_ne "REPOINT: … and not the shell's Camera" "$CAMERA_ACTIVITY" "$TOP"
  adb shell am force-stop "$OCPKG"
  # The slots pointed back.
  rings_save
  layout_restore "$BASELINE" > "$D/REPOINT-restore-end.out" 2>&1; assert_eq "REPOINT: restore — layout_restore of the baseline" "0" "$?"
  assert_eq "REPOINT: restore — slots.CAMERA is the shell's Camera" "app.tileshell/app.tileshell.camera.CameraActivity" "$(slot_of CAMERA)"
  assert_eq "REPOINT: restore — slots.PHOTOS is the shell's Photos" "app.tileshell/app.tileshell.photos.PhotosActivity" "$(slot_of PHOTOS)"
  sub_end
}

# ==================================================================================================== the row
cam_install EDGE_CAMERA
row_begin EDGE_CAMERA "the Camera's Edge-case bullets, by scripts/edge_index_camera.tsv"
cam_preamble
layout_restore "$BASELINE" > "$ROW_DIR/restore0.out" 2>&1; assert_eq "start: layout_restore of the baseline" "0" "$?"
trap 'adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1; adb shell pm grant app.tileshell android.permission.CAMERA >/dev/null 2>&1; adb emu gsm cancel 5551234 >/dev/null 2>&1; adb uninstall app.tileshell.testclient.qacapture >/dev/null 2>&1' EXIT

# The index itself: every sub-step it names is written, and every sub-step written is named.
NAMED="$(grep -v '^#' "$INDEX" | awk -F'\t' '{print $2}' | grep -oE '^edge_[A-Z]+' | sed 's/^edge_//' | sort -u | xargs)"
assert_eq "the index names exactly the sub-steps written here" "$(echo $ALL | tr ' ' '\n' | sort | xargs)" "$NAMED"
assert_eq "no index line is without a place it is run" "0" "$(grep -v '^#' "$INDEX" | awk -F'\t' 'NF>=1 && $2=="" {n++} END{print n+0}')"

if [ "$#" -gt 0 ]; then RUN="$*"; record "sub-steps" "only [$*]: a development run, not the gate's"; else RUN="$ALL"; fi
: > "$ROW_DIR/substeps.txt"
for id in $RUN; do
  if ! declare -F "edge_$id" >/dev/null; then _verdict FAIL "sub-step $id" "no function edge_$id is written"; continue; fi
  log "--- $id"
  before_fail=$FAIL
  rings_save
  "edge_$id"
  rings_save
  echo "$id $(( FAIL - before_fail )) failed" >> "$ROW_DIR/substeps.txt"
done
[ "$#" -eq 0 ] && assert_eq "every sub-step ran" "$ALL" "$(awk '{print $1}' "$ROW_DIR/substeps.txt" | xargs)"

# The floor: each sub-step restores its own; this is asserted once more at the end.
log "--- restore (the floor)"
trap - EXIT
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.CAMERA >/dev/null 2>&1
assert_eq "restore: RECORD_AUDIO and CAMERA held" "true true" "$(perm_granted RECORD_AUDIO) $(perm_granted CAMERA)"
assert_eq "restore: adb is not root" "shell" "$(adb shell whoami | tr -d '\r')"
assert_eq "restore: no fill file" "" "$(adb shell ls /sdcard/fill.bin 2>/dev/null | tr -d '\r')"
assert_eq "restore: qa-capture is not installed" "" "$(adb shell pm list packages "$QAC" | tr -d '\r' | grep -x "package:$QAC")"
layout_restore "$BASELINE" > "$ROW_DIR/restore-end.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
row_end
