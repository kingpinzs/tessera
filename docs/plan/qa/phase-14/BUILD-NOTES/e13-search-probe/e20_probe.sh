#!/usr/bin/env bash
# E13 follow-up probe: phase 01 E20's drawn Back on Start after DeskClock, with the ring slice.
export ANDROID_SERIAL=emulator-5554
OUT="$(cd "$(dirname "$0")" && pwd)"
. "$OUT/../../scripts/lib.sh"; . "$QA/scripts/p14.sh"
ROW_DIR="$OUT"; take_device_lock
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r' | sed 's/^ *//'; }
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
echo "usage access: $(adb shell appops get app.tileshell GET_USAGE_STATS | tr -d '\r' | head -1)"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 3
echo "clock: $(top)"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$OUT/e20-start.xml"; echo "home: $(top) start_page=$(has_node "$OUT/e20-start.xml" start_page)"
M="$(ring_mark)"
tap_node "$OUT/e20-start.xml" nav_back; sleep 3
echo "after drawn Back: $(top)"
ring_since "$M" | grep -v tile_anim | sed 's/.*wall=[0-9]* //' | head -8
M="$(ring_mark)"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 3
echo "after KEYCODE_BACK: $(top)"
ring_since "$M" | grep -v tile_anim | sed 's/.*wall=[0-9]* //' | head -8
adb shell input keyevent KEYCODE_HOME; sleep 2
