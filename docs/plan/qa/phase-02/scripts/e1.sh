#!/usr/bin/env bash
# E1: long-press and drag as ONE gesture enters edit mode and moves a tile; the resize disc cycles the size;
# the unpin disc removes the tile. Evidence = layout-store order before/after each step plus screencaps.
# usage: e1.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E01.txt
mkdir -p "$OUT"
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }

say "# E1 $(date -Iseconds)"
layout_save "$OUT/layout_before.json"
layout_restore "$(dirname "$0")/../baseline_layout.json"   # every row starts from the same Start
say "baseline layout saved (restored at the end, RV12)"
ensure_start
dump "$OUT/e1_start.xml"
say "--- order at rest ---"; layout_order | tee -a "$LOG"

# --- 1. hold + drag in one gesture: move the LAST grid tile onto the FIRST tile's cell -------------------------
FROM=$(python3 - "$OUT/e1_start.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
ids = [m.group(1) for m in re.finditer(r'resource-id="tile:([^"]+)"', s) if not m.group(1).startswith('dock:')]
print(ids[-1])
PY
)
TO=$(python3 - "$OUT/e1_start.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
ids = [m.group(1) for m in re.finditer(r'resource-id="tile:([^"]+)"', s) if not m.group(1).startswith('dock:')]
print(ids[0])
PY
)
say "--- 1. one gesture: hold $FROM past 783 ms, drag onto $TO's cell, release after the dwell ---"
A=$(tile_center "$OUT/e1_start.xml" "$FROM"); B=$(edit_point "$OUT/e1_start.xml" "$TO")
say "from $A to $B"
down ${A% *} ${A#* }
sleep 1.1                      # past the hold: edit mode is on and the same gesture is now a drag
glide ${A% *} ${A#* } ${B% *} ${B#* } 8
sleep 2.6                      # past the 2000 ms dwell, so this is a move and not a folder
adb exec-out screencap -p > "$OUT/e1_dragging.png"
up ${B% *} ${B#* }
sleep 1.2
adb exec-out screencap -p > "$OUT/e1_after_move.png"
say "--- order after the move ---"; layout_order | tee -a "$LOG"

# --- 2. the resize disc cycles medium -> small -> wide ---------------------------------------------------------
say "--- 2. resize disc ---"
adb shell input keyevent KEYCODE_BACK; sleep 1      # leave edit mode, then hold the tile we are resizing
dump "$OUT/e1_before_resize.xml"
C=$(tile_center "$OUT/e1_before_resize.xml" "$TO")
enter_edit ${C% *} ${C#* }
for step in 1 2 3; do
  dump "$OUT/e1_resize_${step}_dump.xml"          # taken IN edit mode: the discs are in it
  P=$(disc_center "$OUT/e1_resize_${step}_dump.xml" resize) || { say "step $step: no resize disc in the dump"; break; }
  say "step $step: tapping the resize disc at $P"
  adb shell input tap ${P% *} ${P#* }
  sleep 1.4
  adb exec-out screencap -p > "$OUT/e1_resize_$step.png"
  layout_order | head -1 | tee -a "$LOG"
done

# --- 3. the unpin disc removes the tile ------------------------------------------------------------------------
say "--- 3. unpin disc ---"
dump "$OUT/e1_before_unpin.xml"
P=$(disc_center "$OUT/e1_before_unpin.xml" unpin) || say "no unpin disc in the dump"
say "tapping the unpin disc at $P"
adb shell input tap ${P% *} ${P#* }
sleep 1.3
adb exec-out screencap -p > "$OUT/e1_after_unpin.png"
say "--- order after the unpin (the tile is gone, the tiles after it fill the gap) ---"
layout_order | tee -a "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -40 > "$OUT/e1_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored"
say "--- order after restore ---"; layout_order | tee -a "$LOG"
