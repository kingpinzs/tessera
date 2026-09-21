#!/usr/bin/env bash
# E9: bottom tile row editing (INDEX Change Log 2026-09-17) — drag a grid tile in, drag a row tile back out,
# reorder two row tiles, unpin a row tile, a drop onto a FULL row is refused, and turning "show more tiles" off
# moves the row's overflow to the end of the grid.
# usage: e9.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E09.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
row() { layout_json | python3 -c "import json,sys; print('row:', json.load(sys.stdin)['dock'])"; }
say "# E9 $(date -Iseconds)"
layout_save "$OUT/layout_before.json"
layout_restore "$(dirname "$0")/../baseline_layout.json"   # every row starts from the same Start
ensure_start
say "row at rest:"; row | tee -a "$LOG"

drag_to() { # drag_to <tile id> <x> <y> [holdms]
  local T=$1 X=$2 Y=$3 hold=${4:-900} from
  ensure_start
  dump "$OUT/e9_pre.xml"
  from=$(tile_center "$OUT/e9_pre.xml" "$T") || { say "no tile $T"; return 1; }
  down ${from% *} ${from#* }; sleep 1.1
  glide ${from% *} ${from#* } "$X" "$Y" 8
  sleep 0.$((hold / 100))
  up "$X" "$Y"; sleep 1.3
  adb shell input keyevent KEYCODE_BACK; sleep 1
}

ROWY=$(python3 -c "
import re
s=open('/dev/stdin').read()
m=re.search(r'resource-id=\"tile:dock:[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s)
print((int(m.group(2))+int(m.group(4)))//2 if m else 2050)" < <(dump /dev/stdout))
say "bottom row centre line y=$ROWY"

say "--- 1. drag a grid tile into the row ---"
G=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
drag_to "$G" 540 "$ROWY"
adb exec-out screencap -p > "$OUT/e9_into_row.png"
say "expect $G in the row at small size:"; row | tee -a "$LOG"
dump "$OUT/e9_into_row.xml"
grep -o 'resource-id="tile:dock:[^"]*"' "$OUT/e9_into_row.xml" | tee -a "$LOG"

say "--- 2. drag it back out into the grid ---"
drag_to "dock:$G" 540 700
adb exec-out screencap -p > "$OUT/e9_out_of_row.png"
say "expect the row back to three and $G in the grid:"; row | tee -a "$LOG"; layout_order | head -1 | tee -a "$LOG"

say "--- 3. reorder two row tiles (drag the first onto the third slot) ---"
dump "$OUT/e9_reorder_pre.xml"
FIRST=$(layout_json | python3 -c "import json,sys; print('dock:'+json.load(sys.stdin)['dock'][0])")
THIRDX=$(python3 -c "
import re
s=open('$OUT/e9_reorder_pre.xml').read()
b=sorted(tuple(map(int,m.groups())) for m in re.finditer(r'resource-id=\"tile:dock:[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s))
print((b[-1][0]+b[-1][2])//2 if b else 900)")
drag_to "$FIRST" "$THIRDX" "$ROWY"
adb exec-out screencap -p > "$OUT/e9_reordered.png"
say "expect the order to have changed:"; row | tee -a "$LOG"

say "--- 4. unpin a row tile ---"
ensure_start
dump "$OUT/e9_unpin_pre.xml"
D=$(grep -o 'resource-id="tile:dock:[^"]*"' "$OUT/e9_unpin_pre.xml" | head -1 | sed 's/.*tile:\(.*\)"/\1/')
C=$(tile_center "$OUT/e9_unpin_pre.xml" "$D")
enter_edit ${C% *} ${C#* }
P=$(corner_point "$OUT/e9_unpin_pre.xml" "$D" top)
say "unpin disc for $D at $P"
adb shell input tap ${P% *} ${P#* }; sleep 1.3
adb exec-out screencap -p > "$OUT/e9_row_unpinned.png"
say "expect the row to be one shorter:"; row | tee -a "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1

say "--- 5. fill the row to capacity, then a further drop is refused ---"
for i in 1 2 3 4; do
  G=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
  drag_to "$G" 540 "$ROWY"
  say "after adding $G:"; row | tee -a "$LOG"
done
ROWLEN=$(layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['dock']))")
say "row now holds $ROWLEN (capacity is 6 with show more tiles on)"
layout_save "$OUT/e9_full_row.json"
G=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
drag_to "$G" 540 "$ROWY"
adb exec-out screencap -p > "$OUT/e9_refused.png"
say "expect REFUSED — the row is unchanged and $G stayed in the grid:"
row | tee -a "$LOG"; layout_order | head -1 | tee -a "$LOG"
diff <(python3 -c "import json;print(json.load(open('$OUT/e9_full_row.json'))['dock'])") \
     <(layout_json | python3 -c "import json,sys;print(json.load(sys.stdin)['dock'])") \
  && say "row unchanged by the refused drop: PASS" || say "row CHANGED by the refused drop: FAIL"

say "--- 6. turn 'show more tiles' off with a full row: the overflow moves to the end of the grid ---"
say "row before:"; row | tee -a "$LOG"; layout_order | tee -a "$LOG"
adb shell am start -n app.tileshell/app.tileshell.settings.SettingsActivity >/dev/null; sleep 2
scroll_to_id "$OUT/e9_settings.xml" "setting:columns" || say "could not find the show-more-tiles row in Settings"
tap_id "$OUT/e9_settings.xml" "setting:columns"; sleep 1.5
adb exec-out screencap -p > "$OUT/e9_settings_columns.png"
ensure_start
adb exec-out screencap -p > "$OUT/e9_two_columns.png"
say "row after the column change:"; row | tee -a "$LOG"; layout_order | tee -a "$LOG"
dump "$OUT/e9_two_columns.xml"
python3 "$(dirname "$0")/e9_overflow.py" "$OUT/e9_two_columns.xml" | tee -a "$LOG"

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e9_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored (the column setting is restored by the caller)"
