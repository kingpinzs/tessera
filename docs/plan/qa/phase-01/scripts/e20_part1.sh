#!/usr/bin/env bash
# E20 part 1: DeskClock Timer tab -> Home -> drawn Back resumes the same task on the Timer tab.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E20.txt
echo "# E20 $(date -Iseconds)" > "$LOG"
echo '$ adb shell appops get app.tileshell GET_USAGE_STATS' >> "$LOG"; adb shell appops get app.tileshell GET_USAGE_STATS >> "$LOG"
adb shell am force-stop com.android.deskclock
echo '$ adb shell am start -n com.android.deskclock/.DeskClock' >> "$LOG"
adb shell am start -n com.android.deskclock/.DeskClock >> "$LOG"; sleep 3
dump "$OUT/deskclock_start.xml"
# DeskClock remembers its last tab, so select Alarm first; the Timer tab is then chosen in this run
AXY=$(python3 - "$OUT/deskclock_start.xml" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
for m in re.finditer(r'<node [^>]*>', s):
    n=m.group(0)
    if re.search(r'(content-desc|text)="Alarm"', n):
        x1,y1,x2,y2=map(int,re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()); print((x1+x2)//2,(y1+y2)//2); break
PY
)
adb shell "input tap $AXY"; sleep 2; dump "$OUT/deskclock_alarm.xml"
echo "Alarm tab at: $AXY; Timer selected on Alarm tab: $(grep -o '<node [^>]*\(content-desc\|text\)="Timer"[^>]*>' "$OUT/deskclock_alarm.xml" | grep -o 'selected="[a-z]*"')" >> "$LOG"
python3 - "$OUT/deskclock_start.xml" > "$OUT/timer_xy.txt" <<'PY'
import re,sys
s=open(sys.argv[1]).read()
for m in re.finditer(r'<node [^>]*>', s):
    n=m.group(0)
    if re.search(r'(content-desc|text)="Timer"', n):
        b=re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups(); x1,y1,x2,y2=map(int,b); print((x1+x2)//2,(y1+y2)//2); break
PY
TXY=$(cat "$OUT/timer_xy.txt"); echo "Timer tab at: $TXY" >> "$LOG"
adb shell "input tap $TXY"; sleep 2
dump "$OUT/deskclock_timer.xml"
echo "Timer selected after tapping Timer: $(grep -o '<node [^>]*\(content-desc\|text\)="Timer"[^>]*>' "$OUT/deskclock_timer.xml" | grep -o 'selected="[a-z]*"')" >> "$LOG"
echo "deskclock pid before Home: $(adb shell pidof com.android.deskclock)" >> "$LOG"
echo '$ tasks before Home' >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity|\* Task\{.*deskclock" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump "$OUT/start_before_back.xml"
BXY=$(center "$OUT/start_before_back.xml" nav_back); echo "drawn Back at: $BXY" >> "$LOG"
adb shell rm -f /sdcard/e20_back.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 4 /sdcard/e20_back.mp4 & REC=$!
sleep 1.5; adb shell "input tap $BXY"; wait $REC
adb pull /sdcard/e20_back.mp4 "$OUT/e20_back.mp4" >/dev/null; sleep 1
echo '$ after drawn Back: resumed + deskclock task' >> "$LOG"
echo "deskclock pid after drawn Back: $(adb shell pidof com.android.deskclock)" >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity|\* Task\{.*deskclock" >> "$LOG"
dump "$OUT/after_drawn_back.xml"
echo "Timer tab selected after drawn Back: $(grep -o '<node [^>]*\(content-desc\|text\)="Timer"[^>]*>' "$OUT/after_drawn_back.xml" | grep -o 'selected="[a-z]*"')" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[back\]|tile=back|start exit finished tile=back" | tail -3 >> "$LOG"
# same with KEYCODE_BACK after revealing the system bars
adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell input swipe 540 0 540 400 250; sleep 0.6
echo "E19 reveal method, swipe from top edge: TransientControlTarget count $(adb shell dumpsys window | grep -c 'mControlTarget=TransientControlTarget')" >> "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 3
echo '$ after KEYCODE_BACK: resumed + deskclock task' >> "$LOG"
adb shell dumpsys activity activities | grep -E "topResumedActivity|\* Task\{.*deskclock" >> "$LOG"
dump "$OUT/after_key_back.xml"
echo "Timer tab selected after KEYCODE_BACK: $(grep -o '<node [^>]*\(content-desc\|text\)="Timer"[^>]*>' "$OUT/after_key_back.xml" | grep -o 'selected="[a-z]*"')" >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[back\]" | tail -1 >> "$LOG"
