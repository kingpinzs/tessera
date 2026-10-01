#!/usr/bin/env bash
# E12 probe 2: the same edge swipe over the shell's own SettingsActivity (system bars hidden, like Start): does Back pop
# the page? And over Start with DeskClock in its Back history, the ring's lines for the touch.
export ANDROID_SERIAL=emulator-5554 PATH="$HOME/Android/Sdk/platform-tools:$PATH"
OV=com.android.internal.systemui.navbar.gestural
dumpq() { adb shell uiautomator dump /sdcard/p.xml >/dev/null; adb shell cat /sdcard/p.xml; }
adb shell cmd overlay enable $OV; sleep 5
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page KEYBOARD >/dev/null; sleep 3
echo "Settings KEYBOARD page shown: $(dumpq | grep -c 'keyboard_sounds')"
echo "bars: $(adb shell dumpsys window | grep -m2 -E 'InsetsSource id=[0-9a-f]* type=(statusBars|navigationBars)' | grep -oE 'type=[a-zA-Z]+ .*visible=[a-z]+' | sed -E 's/frame=[^ ]+ //' | tr '\n' ' ')"
adb shell input swipe 2 1200 400 1200 250; sleep 2.5
echo "after edge swipe: keyboard page still shown=$(dumpq | grep -c 'keyboard_sounds') hub shown=$(dumpq | grep -c 'settings_keyboard')"
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell cmd overlay disable $OV; sleep 3
echo "overlay after: $(adb shell cmd overlay list | grep -F $OV | tr -d '\r')"
