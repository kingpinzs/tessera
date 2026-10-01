#!/usr/bin/env bash
# Gate review S4 probe: does the pod bay come back from SAVED STATE? (Decision: "process death -> Start, the page is not
# persisted".) `am kill` first (a real process death, if Android allows it for the launcher), then "Don't keep
# activities" (the same saved-state restore without killing the process). Restores the setting.
export ANDROID_SERIAL=emulator-5554
OUT="$(cd "$(dirname "$0")" && pwd)"
. "$OUT/../../scripts/lib.sh"; . "$QA/scripts/p14.sh"
ROW_DIR="$OUT"; take_device_lock
trap 'adb shell settings put global always_finish_activities 0' EXIT
page() { dump_ui "$OUT/$1.xml"; echo "$1: pod_bay=$(has_node "$OUT/$1.xml" pod_bay) start_alone=$(start_alone "$OUT/$1.xml")"; }
pid() { adb shell pidof app.tileshell | tr -d '\r'; }
open_pod_bay "$OUT/a0.xml"; echo "a0 pod bay: $(has_node "$OUT/a0.xml" pod_bay) pid=$(pid)"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 3
adb shell am kill app.tileshell; sleep 2; echo "after am kill: pid=$(pid)"
adb shell input keyevent KEYCODE_HOME; sleep 4; page a1-after-kill-home
ensure_start
adb shell settings put global always_finish_activities 1
open_pod_bay "$OUT/b0.xml"; echo "b0 pod bay: $(has_node "$OUT/b0.xml" pod_bay)"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 4; page b1-dont-keep-home
adb shell settings put global always_finish_activities 0
ensure_start
