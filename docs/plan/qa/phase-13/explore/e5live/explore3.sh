#!/usr/bin/env bash
# Exploration only: can a dump taken right after the mic tap (same device shell) catch the listening persona's node?
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/explore3.txt"; : > "$LOG"
wake_device >/dev/null
ensure_start; cortana_assist; sleep 3
dump_ui "$ROW_DIR/home3.xml"
set -- $(bounds "$ROW_DIR/home3.xml" cortana_text_box_mic); MX=$(( ($1+$3)/2 )); MY=$(( ($2+$4)/2 ))
adb shell "echo mic=\$(date +%s%3N); input tap $MX $MY; sleep 0.3; echo dump=\$(date +%s%3N); uiautomator dump /sdcard/l3.xml >/dev/null; echo dumped=\$(date +%s%3N)"
adb shell cat /sdcard/l3.xml > "$ROW_DIR/l3.xml"
grep -o 'resource-id="cortana_persona[^"]*"[^>]*bounds="[^"]*"' "$ROW_DIR/l3.xml" | awk '{print $1,$NF}'
sleep 4; cortana_close
