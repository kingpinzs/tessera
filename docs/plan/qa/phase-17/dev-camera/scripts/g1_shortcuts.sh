#!/usr/bin/env bash
# Development proof, build task 15 (G, Camera's half): camera_photo and camera_video are manifest shortcuts at ranks 0
# and 1 and each opens its mode; camera_panorama and camera_slowmo are DYNAMIC and absent on this emulator (panorama is
# never on x86_64, slow motion needs a high-speed session). The publishing rule is CameraGatesTest's. Not the gate (E23).
. "$(dirname "$0")/head.sh"
row_begin G1 "App Shortcuts: static open their mode, the dynamic two absent on the AVD"
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
open_camera
record "camera ready after (s)" "$(wait_camera "$MARK")"
assert_contains "the publishing rule ran" "[camera] shortcuts dynamic: none" "$(cam_since "$MARK")"
SC="$(adb shell dumpsys shortcut | tr -d '\r' | awk '/Package: app.tileshell /{p=1} /Package: /{if($2!="app.tileshell")p=0} p')"
echo "$SC" > "$ROW_DIR/shortcuts.txt"
for id in camera_photo camera_video; do
  BLOCK="$(echo "$SC" | grep -A14 "ShortcutInfo {id=$id,")"
  assert_contains "$id is a manifest shortcut" "Man" "$(echo "$BLOCK" | grep -o 'flags=0x[0-9a-f]* \[[^]]*\]' | head -1)"
  assert_contains "$id is on CameraActivity" "activity=ComponentInfo{app.tileshell/app.tileshell.camera.CameraActivity}" "$(echo "$BLOCK" | grep -m1 -o 'activity=[^,]*')"
done
assert_eq "ranks 0 and 1" "0 1" "$(for id in camera_photo camera_video; do echo "$SC" | grep -A14 "ShortcutInfo {id=$id," | grep -m1 -o 'rank=[0-9]*' | cut -d= -f2; done | xargs)"
assert_absent "camera_panorama absent on the AVD" "id=camera_panorama" "$SC"
assert_absent "camera_slowmo absent on the AVD" "id=camera_slowmo" "$SC"
record "camera shortcut ids in dumpsys shortcut" "$(echo "$SC" | grep -o 'id=camera_[a-z]*' | sort -u | xargs)"
# Each static shortcut's intent (VIEW + the mode extra) opens its mode in the running singleTask activity.
adb shell am start -n app.tileshell/.camera.CameraActivity -a android.intent.action.VIEW --es mode video >/dev/null; sleep 4
gdump "$ROW_DIR/video.xml"
assert_eq "top" "app.tileshell/.camera.CameraActivity" "$(top_activity)"
assert_contains "camera_video -> video selected" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:video"[^>]*>' "$ROW_DIR/video.xml")"
assert_contains "photo not selected" 'selected="false"' "$(grep -o '<node[^>]*camera_mode:photo"[^>]*>' "$ROW_DIR/video.xml")"
adb shell am start -n app.tileshell/.camera.CameraActivity -a android.intent.action.VIEW --es mode photo >/dev/null; sleep 4
gdump "$ROW_DIR/photo.xml"
assert_contains "camera_photo -> photo selected" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:photo"[^>]*>' "$ROW_DIR/photo.xml")"
# A mode this camera cannot do (a stale pin of a dynamic shortcut) opens Photo, never an error.
adb shell am start -n app.tileshell/.camera.CameraActivity -a android.intent.action.VIEW --es mode panorama >/dev/null; sleep 4
gdump "$ROW_DIR/pano.xml"
assert_contains "mode=panorama on x86_64 falls back to photo" 'selected="true"' "$(grep -o '<node[^>]*camera_mode:photo"[^>]*>' "$ROW_DIR/pano.xml")"
assert_eq "no panorama node" "no" "$(has_node "$ROW_DIR/pano.xml" "camera_mode:panorama")"
assert_eq "one CameraActivity instance (singleTask)" "1" "$(adb shell dumpsys activity activities | grep -c 'Hist.*app.tileshell/.camera.CameraActivity')"
assert_eq "no crash" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell; ensure_start
row_end
