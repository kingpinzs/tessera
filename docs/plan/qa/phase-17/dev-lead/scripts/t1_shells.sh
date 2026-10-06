#!/usr/bin/env bash
# Phase 17 development proof, build tasks 1 and 3: the three apps' shells start in their processes, each process's
# ring is readable through its dump service, the helpers resolve, and the two slot seeds write their lines.
# Not the gate (E1, E2, E15, E17 are). It installs the current debug APK; the seeds it triggers stay (they are the
# phase's own state).
export ANDROID_SERIAL=emulator-5554
export TILESHELL_APK="$(cd "$(dirname "$0")/../../../../../.." && pwd)/app/build/outputs/apk/debug/app-debug.apk"
. "$(dirname "$0")/lib.sh"
take_device_lock
[ "$(apk_matches | cut -c1-3)" = "yes" ] || { adb install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 4; }; }
row_begin T1SHELLS "app shells, processes, dump services, seeds"
assert_eq "wake" "Awake" "$(wake_device)"
top() { adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mTopResumedActivity' | grep -oE 'app\.tileshell/[A-Za-z.]+' | head -1 | tr -d '\r'; }
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
ensure_start; sleep 2
SLICE="$(ring_since "$MARK" launcher)"
record "photos seed line" "$(echo "$SLICE" | grep -F 'assignSlotOnce slot:photos:v1' | sed 's/.*\[layout\] //')"
record "camera seed line" "$(echo "$SLICE" | grep -F 'assignSlotOnce slot:camera:v1' | sed 's/.*\[layout\] //')"
assert_contains "photos seed names PhotosActivity" "assignSlotOnce slot:photos:v1 PHOTOS -> app.tileshell/.photos.PhotosActivity ->" "$SLICE"
assert_contains "camera seed names CameraActivity" "assignSlotOnce slot:camera:v1 CAMERA -> app.tileshell/.camera.CameraActivity ->" "$SLICE"

LAUNCHER_PID="$(adb shell pidof app.tileshell | tr -d '\r')"
for spec in "photos.PhotosActivity::" "camera.CameraActivity:camera:camera.CameraDumpService" "video.VideoActivity:video:video.VideoDumpService"; do
  cls="${spec%%:*}"; rest="${spec#*:}"; proc="${rest%%:*}"; svc="${rest#*:}"
  adb shell am start -n "app.tileshell/.$cls" >/dev/null; sleep 2
  assert_eq "$cls resumed" "app.tileshell/.$cls" "$(top)"
  if [ -n "$proc" ]; then
    assert_ne "process :$proc runs" "" "$(adb shell pidof "app.tileshell:$proc" | tr -d '\r')"
    DUMP="$(adb shell dumpsys activity service "app.tileshell/.$svc")"
    assert_contains ":$proc ring header" "tileshell $proc process pid=" "$DUMP"
    assert_contains ":$proc ring holds its activity's line" "${cls##*.} created" "$DUMP"
  fi
done
assert_contains "photos line in the launcher ring" "[photosapp] PhotosActivity created" "$(ring_since "$MARK" launcher)"
assert_eq "launcher pid unchanged" "$LAUNCHER_PID" "$(adb shell pidof app.tileshell | tr -d '\r')"

# The helpers resolve where the doc says other apps reach them.
VID="$(adb shell content query --uri content://media/external/video/media --projection _id | head -1 | sed 's/.*_id=//' | tr -d '\r')"
IMG="$(adb shell content query --uri content://media/external/images/media --projection _id | head -1 | sed 's/.*_id=//' | tr -d '\r')"
assert_contains "VIEW video resolves to PlayerActivity" "app.tileshell/.video.PlayerActivity" "$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t video/mp4 -d content://media/external/video/media/$VID)"
assert_contains "VIEW image resolves to ViewerActivity" "app.tileshell/.photos.ViewerActivity" "$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t image/png -d content://media/external/images/media/$IMG)"
assert_contains "VIEW http video resolves to PlayerActivity" "app.tileshell/.video.PlayerActivity" "$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t video/mp4 -d http://10.0.2.2:8090/qa-steps.mp4)"
# Since Android 11 an implicit capture request reaches only preinstalled cameras, and the same filter answers the shell's
# own query (run 1): the implicit list is recorded, and the request that NAMES the shell must resolve to CaptureActivity.
record "implicit IMAGE_CAPTURE list" "$(adb shell cmd package query-activities --brief -a android.media.action.IMAGE_CAPTURE | grep / | tr -d '\r' | xargs)"
assert_absent "implicit IMAGE_CAPTURE does not list the shell (Android 11 rule)" "app.tileshell" "$(adb shell cmd package query-activities --brief -a android.media.action.IMAGE_CAPTURE)"
assert_contains "IMAGE_CAPTURE naming the shell resolves to CaptureActivity" "app.tileshell/.camera.CaptureActivity" "$(adb shell cmd package query-activities --brief -a android.media.action.IMAGE_CAPTURE -p app.tileshell)"
assert_contains "VIDEO_CAPTURE naming the shell resolves to CaptureActivity" "app.tileshell/.camera.CaptureActivity" "$(adb shell cmd package query-activities --brief -a android.media.action.VIDEO_CAPTURE -p app.tileshell)"
assert_contains "STILL_IMAGE_CAMERA lists CameraActivity" "app.tileshell/.camera.CameraActivity" "$(adb shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA)"
assert_contains "APP_GALLERY lists PhotosActivity" "app.tileshell/.photos.PhotosActivity" "$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_GALLERY)"
assert_absent "EditActivity is not exported" "exported=true" "$(adb shell dumpsys package app.tileshell | grep -A3 'photos.EditActivity' | head -4)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "content://media/external/video/media/$VID" -t video/mp4 >/dev/null; sleep 2
assert_eq "PlayerActivity resumed" "app.tileshell/.video.PlayerActivity" "$(top)"
PERMS="$(adb shell dumpsys package app.tileshell | grep -E 'android.permission.(CAMERA|READ_MEDIA_VIDEO|SET_WALLPAPER|FOREGROUND_SERVICE_CAMERA)' | tr -d '\r')"
assert_contains "CAMERA declared" "android.permission.CAMERA" "$PERMS"
assert_contains "READ_MEDIA_VIDEO declared" "android.permission.READ_MEDIA_VIDEO" "$PERMS"
assert_contains "SET_WALLPAPER declared" "android.permission.SET_WALLPAPER" "$PERMS"
assert_absent "no FOREGROUND_SERVICE_CAMERA" "FOREGROUND_SERVICE_CAMERA" "$PERMS"
assert_eq "no crash of the shell in this run" "" "$(adb logcat -d -t 400 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save launcher; ring_save app.tileshell/.camera.CameraDumpService; ring_save app.tileshell/.video.VideoDumpService
adb shell am force-stop app.tileshell
ensure_start
row_end
