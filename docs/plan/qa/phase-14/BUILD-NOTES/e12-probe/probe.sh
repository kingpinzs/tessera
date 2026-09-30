#!/usr/bin/env bash
# E12 probe: with the gestural overlay on, does an injected edge swipe fire Android's Back — over DeskClock (an ordinary
# app with Settings behind it) and over Start?
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OV=com.android.internal.systemui.navbar.gestural
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }
adb shell cmd overlay enable $OV; sleep 5
echo "navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')"
adb shell dumpsys window | grep -m3 -E "type=systemGestures|mandatorySystemGestures" | tr -s ' ' | cut -c1-160
echo "== over DeskClock launched from Settings"
adb shell am start -n com.android.settings/.Settings >/dev/null; sleep 3
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null; sleep 3
echo "before: $(top)"
for x in 2 10 30; do
  adb shell input swipe $x 1200 400 1200 250; sleep 2.5; echo "edge swipe from x=$x (250 ms): $(top)"
  adb shell am start -n com.android.deskclock/.DeskClock >/dev/null; sleep 2
done
adb shell input swipe 2 1200 500 1200 600; sleep 2.5; echo "edge swipe from x=2 (600 ms, 500 px): $(top)"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell cmd overlay disable $OV; sleep 3
echo "overlay after: $(adb shell cmd overlay list | grep -F $OV | tr -d '\r')"
