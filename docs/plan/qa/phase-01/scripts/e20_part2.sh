#!/usr/bin/env bash
# E20 part 2: different-affinity page (X24) and the lock step (X12).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E20.txt
echo "## different-affinity page (X24)" >> "$LOG"
echo '$ adb shell am start -a android.settings.WIFI_SETTINGS' >> "$LOG"
adb shell am start -a android.settings.WIFI_SETTINGS >> "$LOG"; sleep 3
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
dump "$OUT/wifi_settings.xml"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump "$OUT/start_before_back2.xml"; BXY=$(center "$OUT/start_before_back2.xml" nav_back)
adb shell "input tap $BXY"; sleep 3
echo '$ after drawn Back' >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
dump "$OUT/after_back_wifi.xml"
echo "page title nodes after Back: $(grep -o 'text="\(Settings\|Internet\|Wi.Fi\|Network & internet\|Search settings\)"' "$OUT/after_back_wifi.xml" | sort | uniq -c | tr '\n' ';')" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[back\]|\[launch\] startMainActivity" | tail -2 >> "$LOG"
echo "## lock step (X12)" >> "$LOG"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null; sleep 3
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
adb shell input keyevent KEYCODE_WAKEUP; sleep 2
adb shell wm dismiss-keyguard; sleep 3
echo '$ after sleep / wake / dismiss-keyguard' >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump "$OUT/start_before_back3.xml"; BXY=$(center "$OUT/start_before_back3.xml" nav_back)
adb shell "input tap $BXY"; sleep 3
echo '$ after drawn Back on Start' >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[back\]" | tail -1 >> "$LOG"
