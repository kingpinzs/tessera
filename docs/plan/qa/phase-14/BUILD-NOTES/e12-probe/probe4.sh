#!/usr/bin/env bash
# E12 probe 4 (guard build): Start with DeskClock in the Back history, gestural nav. Edge swipe 1 (bars revealed, the
# guard takes it), then edge swipe 2 within 0.5 s while the bars show: is that Android's Back (DeskClock resumes)?
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OV=com.android.internal.systemui.navbar.gestural
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }
ring() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[podbay\]|\[start\] (page=|edge)|\[launch\]" | tail -5 | sed 's/^ *//' | cut -c1-140; }
adb shell cmd overlay enable $OV; sleep 5
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "on Start: top=$(top)"
adb shell "input swipe 2 1200 400 1200 250; sleep 0.4; input swipe 2 1200 400 1200 250"; sleep 3
echo "after edge swipe 1 then 2 (0.4 s apart): top=$(top)"; ring
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell cmd overlay disable $OV; sleep 3
echo "overlay after: $(adb shell cmd overlay list | grep -F $OV | tr -d '\r')"
