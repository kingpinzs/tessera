#!/usr/bin/env bash
# E20 part 3: reboot step (X12) and Usage access revoke / restore.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E20.txt
echo "## reboot step (X12)" >> "$LOG"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null; sleep 3
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
echo '$ adb reboot; adb wait-for-device; poll sys.boot_completed' >> "$LOG"
adb reboot; adb wait-for-device
for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 2; done
echo "boot_completed=$(adb shell getprop sys.boot_completed) after ~$((i*2)) s" >> "$LOG"
sleep 8
adb shell wm dismiss-keyguard; sleep 5
adb shell dumpsys usagestats > "$OUT/usagestats_after_reboot.txt"
echo "FallbackHome events in capture: $(grep -c 'FallbackHome' "$OUT/usagestats_after_reboot.txt")" >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
dump "$OUT/start_after_reboot.xml"; BXY=$(center "$OUT/start_after_reboot.xml" nav_back)
echo "drawn Back at: $BXY" >> "$LOG"
adb shell "input tap $BXY"; sleep 3
echo '$ after drawn Back' >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[back\]" | tail -1 >> "$LOG"
echo "## Usage access revoke" >> "$LOG"
echo '$ adb shell appops set app.tileshell GET_USAGE_STATS ignore' >> "$LOG"
adb shell appops set app.tileshell GET_USAGE_STATS ignore
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null; sleep 3
dump "$OUT/checklist_usage_revoked.xml"
echo "checklist usage row: $(grep -o 'checklist:usage:[a-z_]*' "$OUT/checklist_usage_revoked.xml" | head -1)" >> "$LOG"
echo '$ adb shell appops set app.tileshell GET_USAGE_STATS allow' >> "$LOG"
adb shell appops set app.tileshell GET_USAGE_STATS allow
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null; sleep 3
dump "$OUT/checklist_usage_restored.xml"
echo "checklist usage row after restore: $(grep -o 'checklist:usage:[a-z_]*' "$OUT/checklist_usage_restored.xml" | head -1)" >> "$LOG"
adb shell appops get app.tileshell GET_USAGE_STATS >> "$LOG"
adb shell input keyevent KEYCODE_HOME
