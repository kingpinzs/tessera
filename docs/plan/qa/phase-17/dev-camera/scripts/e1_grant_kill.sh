#!/usr/bin/env bash
# Development proof (E15, Camera's half): with CAMERA revoked the Camera shows its grant page naming the Setup
# checklist and the checklist's Camera row is missing; Android's real dialog, opened from the page, grants it and the
# viewfinder opens; then `:camera` is killed (the one allowed use of root, undone at once) and the launcher's pid is
# unchanged. Not the gate. CAMERA ends granted.
. "$(dirname "$0")/head.sh"
row_begin E1 "grant page, checklist row, real dialog; :camera killed"
adb shell am force-stop app.tileshell
adb shell pm revoke app.tileshell android.permission.CAMERA; sleep 1
assert_eq "CAMERA revoked" "0" "$(granted CAMERA)"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-clear-task --es page CHECKLIST >/dev/null 2>&1; sleep 3
scroll_to_node "$ROW_DIR/check0.xml" "checklist:camera:missing" 6
assert_eq "checklist: the Camera row is missing" "yes" "$(has_node "$ROW_DIR/check0.xml" "checklist:camera:missing")"
MARK="$(ring_mark)"
open_camera
gdump "$ROW_DIR/grant.xml"
assert_eq "the grant page" "yes" "$(has_node "$ROW_DIR/grant.xml" camera_grant)"
TEXT="$(node_text "$ROW_DIR/grant.xml" camera_grant_text)"; record "page text" "$TEXT"
assert_contains "it names the Setup checklist" "Setup checklist" "$TEXT"
assert_eq "no shutter without the permission" "no" "$(has_node "$ROW_DIR/grant.xml" camera_shutter)"
assert_contains "its line" "[camera] no camera permission: grant page" "$(cam_since "$MARK")"
assert_contains "no camera device is open" "Device 1 is closed, no client instance" "$(adb shell dumpsys media.camera | tr -d '\r')"
tap_node "$ROW_DIR/grant.xml" camera_grant_button; sleep 2.5
dump_ui "$ROW_DIR/dialog.xml"
assert_contains "Android's own permission dialog is up" "com.android.permissioncontroller" "$(grep -o 'package="com.android.permissioncontroller"' "$ROW_DIR/dialog.xml" | head -1)"
tap_node "$ROW_DIR/dialog.xml" "com.android.permissioncontroller:id/permission_allow_foreground_only_button"; sleep 1
record "camera ready after the grant (s)" "$(wait_camera "$MARK")"
assert_eq "CAMERA granted through the dialog" "1" "$(granted CAMERA)"
S="$(cam_since "$MARK")"
assert_contains "the request's line" "[camera] camera permission request: granted" "$S"
assert_contains "the camera opened" "[camera] devices=1 front=absent" "$S"
gdump "$ROW_DIR/vf.xml"
assert_eq "the viewfinder" "yes no" "$(has_node "$ROW_DIR/vf.xml" camera_shutter) $(has_node "$ROW_DIR/vf.xml" camera_grant)"

# --- :camera killed: the launcher lives ---------------------------------------------------------------------------
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -n app.tileshell/.camera.CameraActivity >/dev/null; sleep 4
LPID="$(adb shell pidof app.tileshell | tr -d '\r')"; CPID="$(adb shell pidof app.tileshell:camera | tr -d '\r')"
record "launcher pid / :camera pid" "$LPID / $CPID"
assert_ne ":camera runs while the viewfinder is open" "" "$CPID"
adb root >/dev/null 2>&1; sleep 2; adb wait-for-device
adb shell kill -9 "$CPID"; sleep 2
adb unroot >/dev/null 2>&1; sleep 2; adb wait-for-device; sleep 1
assert_eq "adb is unrooted again" "shell" "$(adb shell whoami | tr -d '\r')"
assert_ne "the :camera process that was killed is gone" "$CPID" "$(adb shell pidof app.tileshell:camera | tr -d '\r')"
assert_eq "the launcher's pid is unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"
M2="$(ring_mark)"
ensure_start; sleep 2
assert_contains "Start is still alive (its ring takes new lines)" "wall=" "$(ring_since "$M2" launcher | tail -1)"
# The next start of :camera cleans up and opens normally.
M3="$(ring_mark)"; open_camera; record "camera ready after the kill (s)" "$(wait_camera "$M3")"
assert_contains "a fresh :camera process" "process start: app.tileshell:camera" "$(cam_since "$M3")"
assert_eq "no pending row of the shell's" "0" "$(pending_rows)"
assert_eq "CAMERA ends granted" "1" "$(granted CAMERA)"
ring_save "$CAM_RING"
adb shell am force-stop app.tileshell; ensure_start
row_end
