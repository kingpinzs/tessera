#!/usr/bin/env bash
# E11: tapping a tile resumes the target app, with Start's exit frames in a recording.
source "$(dirname "$0")/ui.sh"
OUT=$1; mkdir -p "$OUT"; LOG=$OUT/E11.txt
echo "# E11 $(date -Iseconds)" > "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 2
dump "$OUT/start.xml"
XY=$(center "$OUT/start.xml" tile:slot:CALENDAR) || { echo "no calendar tile" | tee -a "$LOG"; exit 1; }
echo "calendar tile center: $XY" >> "$LOG"
adb shell rm -f /sdcard/e11.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 5 /sdcard/e11.mp4 &
REC=$!
sleep 1.5
T0=$(adb shell 'cat /proc/uptime' | cut -d' ' -f1)
adb shell "input tap $XY"
echo "tap at device uptime $T0 s" >> "$LOG"
wait $REC
adb pull /sdcard/e11.mp4 "$OUT/e11.mp4" >/dev/null
echo '$ adb shell dumpsys activity activities | grep -i resumed' >> "$LOG"
adb shell dumpsys activity activities | grep -i resumed >> "$LOG"
echo '$ diagnostics (launch + motion lines)' >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[launch\]|start exit|entrance" | tail -4 >> "$LOG"
