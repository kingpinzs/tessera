#!/usr/bin/env bash
# Exploration only (not row evidence): does the ≡ pane open while Tess listens, and does her persona pulse under it?
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/explore.txt"; : > "$LOG"
wake_device
ensure_start; cortana_assist; sleep 3
MARK="$(ring_mark)"
cortana_listen 1
dump_ui "$ROW_DIR/listening.xml"
echo "menu button while listening: $(has_node "$ROW_DIR/listening.xml" cortana_menu_button)"
grep -o 'resource-id="cortana_persona[^"]*"[^>]*bounds="[^"]*"' "$ROW_DIR/listening.xml" | awk '{print $1,$NF}'
adb shell "screencap /sdcard/l1.raw; sleep 0.2; screencap /sdcard/l2.raw"
tap_node "$ROW_DIR/listening.xml" cortana_menu_button; sleep 0.8
adb shell "screencap /sdcard/p1.raw; sleep 0.2; screencap /sdcard/p2.raw; sleep 0.2; screencap /sdcard/p3.raw"
dump_ui "$ROW_DIR/pane.xml"
echo "pane open: $(has_node "$ROW_DIR/pane.xml" cortana_pane)"
grep -o 'resource-id="cortana_persona[^"]*"[^>]*bounds="[^"]*"' "$ROW_DIR/pane.xml" | awk '{print $1,$NF}'
for f in l1 l2 p1 p2 p3; do adb pull /sdcard/$f.raw "$ROW_DIR/$f.raw" >/dev/null; done
sleep 4
ring_since "$MARK" > "$ROW_DIR/slice.txt"
grep -E 'cortana|speech|listen' "$ROW_DIR/slice.txt" | tail -12
adb shell input keyevent KEYCODE_BACK; sleep 1; cortana_close
