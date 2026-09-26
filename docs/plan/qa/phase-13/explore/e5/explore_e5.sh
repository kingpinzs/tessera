#!/usr/bin/env bash
# Exploration only (not row evidence): the pane over Home and over Reminders, and whether a mic-free moving backdrop
# (Tess speaking a typed request's reply) can sit under the open pane.
S="$(cd "$(dirname "$0")/../../scripts" && pwd)"
. "$S/lib.sh"; . "$S/p13.sh"; . "$S/reminders_fixture.sh"
take_device_lock
ROW_DIR="$(cd "$(dirname "$0")" && pwd)"; LOG="$ROW_DIR/explore.txt"; : > "$LOG"
wake_device
ensure_start; cortana_assist; sleep 3
dump_ui "$ROW_DIR/home.xml"; screencap "$ROW_DIR/home.png"
tap_node "$ROW_DIR/home.xml" cortana_menu_button; sleep 1.5
dump_ui "$ROW_DIR/pane-home.xml"; screencap "$ROW_DIR/pane-home.png"
echo "pane $(bounds "$ROW_DIR/pane-home.xml" cortana_pane) acrylic $(bounds "$ROW_DIR/pane-home.xml" acrylic:cortana_pane)"
python3 "$S/dumpq.py" text_nodes "$ROW_DIR/pane-home.xml"
grep -o 'resource-id="cortana_[^"]*"[^>]*bounds="[^"]*"' "$ROW_DIR/home.xml" | awk '{print $1,$NF}'
adb shell input keyevent KEYCODE_BACK; sleep 1
# speaking probe: a typed request, then the pane while she replies
dump_ui "$ROW_DIR/.t.xml"
type_request "what time is it" 0.3
dump_ui "$ROW_DIR/speak.xml"; screencap "$ROW_DIR/speak-a.png"
has_node "$ROW_DIR/speak.xml" cortana_menu_button
tap_node "$ROW_DIR/speak.xml" cortana_menu_button; sleep 0.4
screencap "$ROW_DIR/speak-pane-1.png"; sleep 0.2; screencap "$ROW_DIR/speak-pane-2.png"
dump_ui "$ROW_DIR/speak-pane.xml"; echo "pane while speaking: $(has_node "$ROW_DIR/speak-pane.xml" cortana_pane)"
diag speech | tail -5
cortana_close; cortana_close
show_start 3
