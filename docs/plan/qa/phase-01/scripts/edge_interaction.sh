#!/usr/bin/env bash
# Edge cases: rapid taps, a tap during a flip, an app that cannot start, bar overlap, a theme change mid-animation.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_interaction.txt
echo "# Edge cases: interaction $(date -Iseconds)" > "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3; dump "$OUT/int_start.xml"
XY=$(center "$OUT/int_start.xml" tile:slot:CALENDAR)
echo '## rapid taps on one tile (five taps, 150 ms apart)' >> "$LOG"
adb logcat -c
for i in 1 2 3 4 5; do adb shell "input tap $XY" & sleep 0.15; done; wait
sleep 6
echo "resumed: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' ')" >> "$LOG"
echo "calendar tasks: $(adb shell dumpsys activity activities | grep -c 'Task{.*com.android.calendar'); crashes $(adb logcat -d -b crash | grep -c app.tileshell)" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 4
echo '## taps during flips (10 taps on the weather tile, 1.3 s apart, so some land mid-flip)' >> "$LOG"
dump "$OUT/int_start2.xml"; WXY=$(center "$OUT/int_start2.xml" tile:shell:weather)
for i in $(seq 1 10); do
  adb shell "input tap $WXY"; sleep 1.3
  adb shell input keyevent KEYCODE_HOME; sleep 1.3
done
sleep 3
echo "after ten taps: resumed $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' '); crashes $(adb logcat -d -b crash | grep -c app.tileshell)" >> "$LOG"
echo '## an app that cannot start (suspended)' >> "$LOG"
adb shell pm suspend org.fossify.messages
adb shell input keyevent KEYCODE_HOME; sleep 4; dump "$OUT/int_start3.xml"
tap_id "$OUT/int_start3.xml" tile:dock:slot:MESSAGING; sleep 5
echo "resumed after tapping the suspended app: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' '); crashes $(adb logcat -d -b crash | grep -c app.tileshell)" >> "$LOG"
adb exec-out screencap -p > "$OUT/int_suspended.png"
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell pm unsuspend org.fossify.messages
echo '## the system bars revealed over the drawn bars' >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell input swipe 540 0 540 400 250; sleep 1
adb exec-out screencap -p > "$OUT/int_bars_revealed.png"
echo "transient control targets: $(adb shell dumpsys window | grep -c 'mControlTarget=TransientControlTarget')" >> "$LOG"
sleep 6
adb exec-out screencap -p > "$OUT/int_bars_hidden.png"
echo "after 6 s: $(adb shell dumpsys window | grep -c 'mControlTarget=TransientControlTarget')" >> "$LOG"
echo '## accent changed while the tiles animate' >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 3
scroll_to_id "$OUT/int_theme.xml" "accent:Yellow Gold" && tap_id "$OUT/int_theme.xml" "accent:Yellow Gold"
sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 4
adb exec-out screencap -p > "$OUT/int_accent_gold.png"
echo "crashes: $(adb logcat -d -b crash | grep -c app.tileshell)" >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 3
scroll_to_id "$OUT/int_theme2.xml" "accent:Red" && tap_id "$OUT/int_theme2.xml" "accent:Red"
sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 4
adb exec-out screencap -p > "$OUT/int_accent_back.png"
python3 - <<'PY' >> "$LOG"
from PIL import Image
import numpy as np
for n in ("int_accent_gold", "int_accent_back"):
    a = np.asarray(Image.open(f"EDGE/{n}.png").convert("RGB")).astype(int)
    print(f"{n}: tile colour {a[100:110, 60:300].reshape(-1, 3).mean(axis=0).round().astype(int).tolist()}")
PY
