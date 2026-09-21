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

# corner_point <dump.xml> <tile id> <top|bottom>: the screen point of the held tile's right-hand corner, where
# the unpin (top) and resize (bottom) discs are centred (R6 §1.2.1-§1.2.3). The held tile stays at scale 1.00,
# so only its centre moves with the contraction.
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
ecx, ecy = (cx, cy) if dock else (fx + (cx - fx) * 0.90, fy + (cy - fy) * 0.90)
w, h = x2 - x1, y2 - y1
print(int(round(ecx + w / 2)), int(round(ecy - h / 2 if sys.argv[3] == "top" else ecy + h / 2)))
PY
}
