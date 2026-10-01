#!/usr/bin/env bash
# E12 probe 3: Start with DeskClock in its Back history, gestural nav: what does the FIRST edge swipe do (transient bars?)
# and does a SECOND one fire Back? The pager's page is read from the ring.
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OV=com.android.internal.systemui.navbar.gestural
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }
bars() { adb shell dumpsys window | grep -m2 -E 'InsetsSource id=[0-9a-f]* type=(statusBars|navigationBars)' | grep -oE 'type=[a-zA-Z]+|visible=[a-z]+' | tr '\n' ' '; }
ring() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[podbay\]|\[start\] page=|\[bars\]" | tail -4 | sed 's/^ *//' | cut -c1-120; }
adb shell cmd overlay enable $OV; sleep 5
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "on Start: top=$(top) bars: $(bars)"
adb shell input swipe 2 1200 400 1200 250; sleep 0.5
echo "0.5 s after edge swipe 1: top=$(top) bars: $(bars)"
sleep 2; echo "2.5 s after: top=$(top)"; ring
adb shell input keyevent KEYCODE_BACK; sleep 1.5   # back to Start if the pod bay opened
adb shell input swipe 2 1200 400 1200 250; sleep 0.3
adb shell input swipe 2 1200 400 1200 250; sleep 2.5
echo "after two quick edge swipes: top=$(top)"; ring
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell cmd overlay disable $OV; sleep 3
echo "overlay after: $(adb shell cmd overlay list | grep -F $OV | tr -d '\r')"
