#!/usr/bin/env bash
# J4 — a hold on the last-opened app's tile enters edit mode on it, and launches nothing (found 2026-09-22 by
# phase 02's E7 light pass: its held tile happened to be the promoted one, and the 830-ms hold opened the
# app; the phase 11 writer had seen in the code that the edit layer's hit test never looks at that tile).
#
# Bracketed: the same hold on a grid tile is the control. Edit mode suspends the promotion (StartPage), so
# once the hold lands the tile is held in its own grid place — the row checks that too.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

row_begin J4 "a hold on the promoted (last-opened) tile enters edit mode and launches nothing"
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -d '\r'; }
hold() { # xml resource-id ms
  local b x y; b="$(bounds "$1" "$2")"; read -r x1 y1 x2 y2 <<< "$b"
  x=$(( (x1 + x2) / 2 )); y=$(( (y1 + y2) / 2 ))
  adb shell input swipe $x $y $x $y "$3"; sleep 1.5
}

adb shell am force-stop $PKG; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 5
dump "$ROW_DIR/fresh.xml"
tap_id "$ROW_DIR/fresh.xml" tile:slot:BROWSER; sleep 5
assert_absent "the tap left Start (Browser opened)" "$PKG/.StartActivity" "$(top)"
adb shell input keyevent KEYCODE_HOME; sleep 4
dump "$ROW_DIR/promoted.xml"
REC="$(python3 - "$ROW_DIR/promoted.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("resource-id") == "recent_app_row":
        for c in n.iter("node"):
            if c.get("resource-id", "").startswith("tile:"):
                print(c.get("resource-id")); sys.exit()
PY
)"
note "the promoted tile: [$REC]"
assert_eq "Browser is promoted above the bottom row" "tile:slot:BROWSER" "$REC"

# ---- the hold on the promoted tile -----------------------------------------------------------------------
hold "$ROW_DIR/promoted.xml" "$REC" 1000
T="$(top)"; log "after a 1000-ms hold on the promoted tile: $T"
assert_contains "the hold launched nothing (Start is still in front)" "$PKG/.StartActivity" "$T"
dump "$ROW_DIR/held.xml"; adb exec-out screencap -p > "$ROW_DIR/held.png"
assert_eq "edit mode is on: the held tile's unpin disc is drawn" yes "$(has_node "$ROW_DIR/held.xml" edit_disc:unpin)"
assert_contains "the shell says the hold was on Browser" "hold 783ms on slot:BROWSER: edit mode on" "$(diag edit | tail -3)"
assert_eq "edit mode suspends the promotion: the tile is back in its grid place" no "$(has_node "$ROW_DIR/held.xml" recent_app_row)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ---- control: the same hold on a grid tile --------------------------------------------------------------
dump "$ROW_DIR/control.xml"
hold "$ROW_DIR/control.xml" tile:slot:PEOPLE 1000
assert_contains "control: a hold on a grid tile launches nothing" "$PKG/.StartActivity" "$(top)"
dump "$ROW_DIR/control_held.xml"
assert_eq "control: and enters edit mode" yes "$(has_node "$ROW_DIR/control_held.xml" edit_disc:unpin)"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am force-stop $PKG; adb shell input keyevent KEYCODE_HOME; sleep 3
note "restored: the shell restarted, so nothing is promoted (RecentApp is in memory only)"
row_end
