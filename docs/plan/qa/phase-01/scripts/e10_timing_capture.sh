#!/usr/bin/env bash
# E10 tile timing: three 170-s screenrecord segments of Start at rest (RV11 splits long rows), with device uptime at each start.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/timing_capture.txt
echo "# timing capture $(date -Iseconds)" > "$LOG"
echo "screen_off_timeout before: $(adb shell settings get system screen_off_timeout)" >> "$LOG"
adb shell svc power stayon true
adb shell input keyevent KEYCODE_HOME; sleep 3
for i in 1 2 3 4 5 6; do adb shell input swipe 540 700 540 1900 200; sleep 0.4; done; sleep 20
dump "$OUT/timing_start.xml"
for seg in 1 2 3; do
  adb shell rm -f /sdcard/e10_timing_$seg.mp4
  echo "segment $seg: host $(date +%s.%N) device uptime before start $(adb shell cat /proc/uptime | cut -d' ' -f1)" >> "$LOG"
  adb shell screenrecord --bit-rate 20000000 --time-limit 170 /sdcard/e10_timing_$seg.mp4
  echo "segment $seg ended: device uptime $(adb shell cat /proc/uptime | cut -d' ' -f1)" >> "$LOG"
  adb pull /sdcard/e10_timing_$seg.mp4 "$OUT/e10_timing_$seg.mp4" >/dev/null
done
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep "\[tile_anim\]" > "$OUT/timing_tile_anim_diag.txt"
adb shell svc power stayon false
echo "stayon restored false; done $(date -Iseconds)" >> "$LOG"
