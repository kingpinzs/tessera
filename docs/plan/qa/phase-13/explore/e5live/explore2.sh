#!/usr/bin/env bash
# Exploration only: the timed sequence on the device itself (tap mic, tap ≡, two raw captures ~200 ms apart), each
# step stamped with the device clock, against the ring's listening start / final lines.
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/explore2.txt"; : > "$LOG"
wake_device >/dev/null
ensure_start; cortana_assist; sleep 3
dump_ui "$ROW_DIR/home.xml"
set -- $(bounds "$ROW_DIR/home.xml" cortana_text_box_mic); MX=$(( ($1+$3)/2 )); MY=$(( ($2+$4)/2 ))
set -- $(bounds "$ROW_DIR/home.xml" cortana_menu_button); BX=$(( ($1+$3)/2 )); BY=$(( ($2+$4)/2 ))
MARK="$(ring_mark)"
adb shell "echo mic=\$(date +%s%3N); input tap $MX $MY; echo menu=\$(date +%s%3N); input tap $BX $BY; echo tapped=\$(date +%s%3N); sleep 0.35; echo c1=\$(date +%s%3N); screencap /sdcard/q1.raw; echo c1done=\$(date +%s%3N); sleep 0.2; echo c2=\$(date +%s%3N); screencap /sdcard/q2.raw; echo c2done=\$(date +%s%3N)" | tee "$ROW_DIR/timeline.txt"
dump_ui "$ROW_DIR/after.xml"; echo "pane after: $(has_node "$ROW_DIR/after.xml" cortana_pane)"
sleep 4
ring_since "$MARK" | grep -E 'speech\] (final|listen)|cortana\] (final|touch|listen)|persona|pane' | cut -c1-160
for f in q1 q2; do adb pull /sdcard/$f.raw "$ROW_DIR/$f.raw" >/dev/null; done
adb shell input keyevent KEYCODE_BACK; sleep 1; cortana_close
