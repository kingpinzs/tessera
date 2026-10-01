#!/usr/bin/env bash
# Diagnosis check for the E20 failure: BackHistory puts the shell's own package among the "system surfaces" (it is an
# IME package since phase 05), so Start resuming never ends the "showing at the keyguard" continuation and DeskClock —
# the last non-shell app before the last keyguard — is skipped until ANOTHER app resumes. If that is right: opening
# Settings first makes Back on Start resume DeskClock again.
export ANDROID_SERIAL=emulator-5554
OUT="$(cd "$(dirname "$0")" && pwd)"
. "$OUT/../../scripts/lib.sh"; . "$QA/scripts/p14.sh"
ROW_DIR="$OUT"; take_device_lock
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r' | sed 's/^ *//'; }
echo "ime packages: $(adb shell ime list -a -s | tr -d '\r' | tr '\n' ' ')"
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 3; echo "third app: $(top)"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$OUT/e20b-start.xml"
M="$(ring_mark)"
tap_node "$OUT/e20b-start.xml" nav_back; sleep 3
echo "after drawn Back: $(top)"
ring_since "$M" | grep -F '[back]' | sed 's/.*wall=[0-9]* //'
adb shell input keyevent KEYCODE_HOME; sleep 2
