#!/usr/bin/env bash
# Edge cases: liveness (notification access turned off, a crash loop, recovery as Home).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_liveness.txt
echo "# Edge cases: liveness $(date -Iseconds)" > "$LOG"
echo '## notification access turned off in Settings' >> "$LOG"
echo "listeners before: $(adb shell settings get secure enabled_notification_listeners)" >> "$LOG"
adb shell cmd notification disallow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener
sleep 4; adb shell input keyevent KEYCODE_HOME; sleep 4
echo "listeners now: [$(adb shell settings get secure enabled_notification_listeners)]" >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_listener_off.xml"
echo "checklist rows: $(grep -o 'checklist:[a-z_]*:[a-z_]*' "$OUT/checklist_listener_off.xml" | tr '\n' ' ')" >> "$LOG"
echo "diagnostics: $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener 2>&1 | tail -2 | tr -s ' ' | tr '\n' ' ')" >> "$LOG"
adb shell cmd notification allow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener
sleep 5; adb shell input keyevent KEYCODE_HOME; sleep 4
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_listener_on.xml"
echo "checklist rows after allowing it again: $(grep -o 'checklist:[a-z_]*:[a-z_]*' "$OUT/checklist_listener_on.xml" | tr '\n' ' ')" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 2
echo '## crash loop (am crash five times)' >> "$LOG"
adb logcat -b crash -c
for i in 1 2 3 4 5; do adb shell am crash app.tileshell; sleep 4; adb shell input keyevent KEYCODE_HOME; sleep 3; done
sleep 5
echo "home role holder: $(adb shell cmd role get-role-holders android.app.role.HOME)" >> "$LOG"
echo "resumed now: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' ')" >> "$LOG"
echo "crash entries: $(adb logcat -d -b crash | grep -c 'Process: app.tileshell')" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 4
dump "$OUT/after_crash_loop.xml"
echo "Start after the loop: start_page $(grep -c 'resource-id=\"start_page\"' "$OUT/after_crash_loop.xml"), tiles $(grep -o 'resource-id=\"tile:[^\"]*\"' "$OUT/after_crash_loop.xml" | wc -l)" >> "$LOG"
