#!/usr/bin/env bash
# Phase 17 development proof (r3 V15, D16): Settings > Diagnostics shows another process's ring while that process
# runs and "not running" otherwise, and the Probe page reads the cameras and the shell's processes. Not the gate.
export ANDROID_SERIAL=emulator-5554
export TILESHELL_APK="$(cd "$(dirname "$0")/../../../../../.." && pwd)/app/build/outputs/apk/debug/app-debug.apk"
. "$(dirname "$0")/lib.sh"
take_device_lock
[ "$(apk_matches | cut -c1-3)" = "yes" ] || { adb install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 4; }; }
row_begin DIAGPROBE "the other processes' rings and the probe page on Settings > Diagnostics"
assert_eq "wake" "Awake" "$(wake_device)"
adb shell am force-stop app.tileshell; ensure_start
adb shell am start -n app.tileshell/.camera.CameraActivity >/dev/null; sleep 2
CAM_PID="$(adb shell pidof app.tileshell:camera | tr -d '\r')"
assert_ne ":camera runs" "" "$CAM_PID"
# Settings opens over the camera's task; CameraActivity stays created (stopped), so its process and service live on.
adb shell am start -n app.tileshell/.settings.SettingsActivity --es page DIAGNOSTICS >/dev/null; sleep 4
dump_ui "$ROW_DIR/diag.xml"
CAM="$(node_text "$ROW_DIR/diag.xml" diag_ring:camera)"
assert_contains "camera ring header on the page" "tileshell camera process pid=$CAM_PID" "$CAM"
assert_contains "camera ring holds the activity's line" "CameraActivity created" "$CAM"
assert_eq "video ring says not running" "not running" "$(node_text "$ROW_DIR/diag.xml" diag_ring:video)"
assert_eq "photosedit ring says not running" "not running" "$(node_text "$ROW_DIR/diag.xml" diag_ring:photosedit)"
assert_eq "reading the rings started no process" "" "$(adb shell pidof app.tileshell:video | tr -d '\r')$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
tap_node "$ROW_DIR/diag.xml" diag_probe; sleep 3
dump_ui "$ROW_DIR/probe.xml"
CAMS="$(node_text "$ROW_DIR/probe.xml" probe_cameras)"
PROCS="$(node_text "$ROW_DIR/probe.xml" probe_processes)"
assert_contains "probe: one camera" "cameras: 1 id(s)" "$CAMS"
assert_contains "probe: it faces back" "facing=back" "$CAMS"
assert_contains "probe: hardware level 3" "hardwareLevel=3" "$CAMS"
assert_contains "probe: MANUAL_SENSOR named" "MANUAL_SENSOR" "$CAMS"
assert_contains "probe: EV range" "aeCompensationRange=[-9, 9]" "$CAMS"
assert_contains "probe: no high-speed ranges" "highSpeedFpsRanges=none" "$CAMS"
assert_contains "probe: the launcher's process" "process app.tileshell pid=$(adb shell pidof app.tileshell | tr -d '\r') pssKb=" "$PROCS"
assert_contains "probe: the camera's process" "process app.tileshell:camera pid=$CAM_PID pssKb=" "$PROCS"
record "probe file line before a pick" "$(node_text "$ROW_DIR/probe.xml" probe_file)"
# The picker is Android's own: the first tile is tapped by its position in the picker's dump, best effort.
tap_node "$ROW_DIR/probe.xml" probe_pick; sleep 3
adb shell uiautomator dump /sdcard/Download/picker.xml >/dev/null 2>&1; adb shell cat /sdcard/Download/picker.xml > "$ROW_DIR/picker.xml"; adb shell rm -f /sdcard/Download/picker.xml
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
  FILE="$(node_text "$ROW_DIR/picked.xml" probe_file)"
  assert_contains "probe: a picked file's line" "file: name=" "$FILE"
  assert_contains "probe: its size" "sizeBytes=" "$FILE"
  record "probe file text" "$(echo "$FILE" | tr '\n' '|' | cut -c1-400)"
else
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
assert_eq "no crash of the shell" "" "$(adb logcat -d -t 600 -s AndroidRuntime | grep -F 'app.tileshell' | head -3)"
ring_save launcher
adb shell am force-stop app.tileshell; ensure_start
row_end
