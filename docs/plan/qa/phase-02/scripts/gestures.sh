#!/usr/bin/env bash
# Phase 02 QA gestures: one continuous touch stream through `adb shell input motionevent`, so the 783-ms hold
# (R6 §1.1.1) and the 2000-ms dwell (Decisions / H20) are real waits inside one gesture, as E1 and E8 require.
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
source "$(dirname "$0")/../../phase-01/scripts/ui.sh"

down() { adb shell input motionevent DOWN "$1" "$2"; }
move() { adb shell input motionevent MOVE "$1" "$2"; }
up()   { adb shell input motionevent UP "$1" "$2"; }

# glide <x1> <y1> <x2> <y2> <steps>: MOVE events along a straight line (the finger travelling).
glide() {
  local x1=$1 y1=$2 x2=$3 y2=$4 n=${5:-6} i
  for i in $(seq 1 "$n"); do
    move $(( x1 + (x2 - x1) * i / n )) $(( y1 + (y2 - y1) * i / n ))
  done
}

# enter_edit <x> <y>: hold past 783 ms on a tile and lift without moving.
enter_edit() { down "$1" "$2"; sleep 1.1; up "$1" "$2"; sleep 0.8; }

# tile_bounds <dump.xml> <tile id>: "x1 y1 x2 y2"
tile_bounds() { bounds "$1" "tile:$2"; }

# tile_center <dump.xml> <tile id>: "x y"
tile_center() { center "$1" "tile:$2"; }

# ensure_start: bring the pivot back to Start.
# KEYCODE_HOME is not enough on this AVD: when the shell is already the resumed home activity, Android does not
# re-deliver the home intent (no onNewIntent), so the pivot stays wherever it is. Phase 01 never exercised that
# either — its own X20 / H28 row for "Home while Start is showing" is a phone row. A right-swipe is what the
# user would do, and it is what these drivers use to get back to page 0.
ensure_start() {
  local i
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  sleep 1.5
  for i in 1 2 3; do
    dump /tmp/qa_ensure_start.xml >/dev/null 2>&1 || true
    if grep -q 'resource-id="start_page"' /tmp/qa_ensure_start.xml 2>/dev/null && ! grep -q 'resource-id="app_list"' /tmp/qa_ensure_start.xml 2>/dev/null; then
      return 0
    fi
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1   # close an IME or a menu first
    sleep 0.6
    adb shell input swipe 200 1200 950 1200 250
    sleep 1.5
  done
  echo "ensure_start: could not get back to Start" >&2
  return 1
}

# edit_point <dump.xml> <tile id>: where that tile's centre sits ON SCREEN while edit mode is on.
# The grid contracts to 0.90 of each centre's distance from the fixed point (R6 §1.1.3), which a uiautomator
# dump cannot show (it reports layout bounds), so the QA driver computes it the same way the shell draws it.
edit_point() {
  python3 - "$1" "$2" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="tile:' + re.escape(sys.argv[2]) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
if not m: sys.exit(1)
x1, y1, x2, y2 = map(int, m.groups())
cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
# The fixed point is a fraction of the SCREEN's height (R6 §1.1.3), not of the page, which stops above the
# drawn nav bar.
fx, fy = 1080 * 0.5, 2340 * 0.475
# The bottom tile row is not in the scrolling grid and does not contract.
print(int(round(cx)), int(round(cy))) if sys.argv[2].startswith("dock:") else \
    print(int(round(fx + (cx - fx) * 0.90)), int(round(fy + (cy - fy) * 0.90)))
PY
}

# disc_center <dump.xml> <unpin|resize>: the centre of an edit-mode disc, read straight out of a dump taken
# WHILE the tile is held. A uiautomator dump of Compose does carry the graphicsLayer transform (verified
# 2026-09-21: in edit mode an unheld tile's bounds are 0.835 of its resting size and moved), and the discs and
# the dim overlays carry their test tags as resource-ids, so nothing has to be recomputed here.
disc_center() { center "$1" "edit_disc:$2"; }

# corner_point <dump.xml> <tile id> <top|bottom>: the same point computed from a dump taken OUTSIDE edit mode,
# for the cases where the dump has to be taken before the gesture starts.
corner_point() {
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r'resource-id="tile:' + re.escape(sys.argv[2]) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
if not m: sys.exit(1)
x1, y1, x2, y2 = map(int, m.groups())
cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
dock = sys.argv[2].startswith("dock:")
fx, fy = 1080 * 0.5, 2340 * 0.475
pitch, counter = 0.90, 1 / 0.90
if dock:
    ecx, ecy, hw, hh = cx, cy, (x2 - x1) / 2, (y2 - y1) / 2
else:
    ecx, ecy = fx + (cx - fx) * pitch, fy + (cy - fy) * pitch
    hw, hh = (x2 - x1) / 2 * counter, (y2 - y1) / 2 * counter
print(int(round(ecx + hw)), int(round(ecy - hh if sys.argv[3] == "top" else ecy + hh)))
PY
}
