#!/usr/bin/env bash
# E9: bottom tile row editing (INDEX Change Log 2026-09-17) — drag a grid tile in, drag a row tile back out,
# reorder two row tiles, unpin a row tile, a drop onto a FULL row is refused, and turning "show more tiles" off
# moves the row's overflow to the end of the grid.
# usage: e9.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E09.txt
mkdir -p "$OUT"; : > "$LOG"
row() { layout_json | python3 -c "import json,sys; print('row:', json.load(sys.stdin)['dock'])"; }
row_keys() { layout_json | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin)['dock']))"; }
row_len() { layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['dock']))"; }
grid_keys() { layout_json | python3 -c "import json,sys; print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))"; }
say "# E9 $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$(dirname "$0")/../baseline_layout.json"   # every row starts from the same Start
ensure_start
say "row at rest:"; row | tee -a "$LOG"

# drag_to <tile id> <x> <y> [hold seconds]: a drop on the GRID has to wait out the 2000 ms dwell or it makes a
# folder with whatever tile is under it; a drop on the row never makes a folder, so 0.9 s is enough there.
drag_to() {
  local T=$1 X=$2 Y=$3 hold=${4:-0.9} from
  ensure_start
  dump "$OUT/e9_pre.xml"
  from=$(tile_center "$OUT/e9_pre.xml" "$T") || { say "no tile $T"; return 1; }
  down ${from% *} ${from#* }; sleep 1.1
  glide ${from% *} ${from#* } "$X" "$Y" 8
  sleep "$hold"
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
say "row now:"; row | tee -a "$LOG"
check_contains "the grid tile joined the row" "$G" "$(row_keys)"
check_absent "and left the grid" "$G" "$(grid_keys)"
check "the row grew by one" 4 "$(row_len)"
dump "$OUT/e9_into_row.xml"
grep -o 'resource-id="tile:dock:[^"]*"' "$OUT/e9_into_row.xml" | tee -a "$LOG"

say "--- 2. drag it back out into the grid ---"
drag_to "dock:$G" 540 700 2.6
adb exec-out screencap -p > "$OUT/e9_out_of_row.png"
say "row now:"; row | tee -a "$LOG"
check_absent "the row tile left the row" "$G" "$(row_keys)"
check_contains "and is back in the grid" "$G" "$(grid_keys)"
check "the row is back to three" 3 "$(row_len)"

say "--- 3. reorder two row tiles (drag the first onto the third slot) ---"
ROW_BEFORE_REORDER=$(row_keys)
dump "$OUT/e9_reorder_pre.xml"
FIRST=$(layout_json | python3 -c "import json,sys; print('dock:'+json.load(sys.stdin)['dock'][0])")
THIRDX=$(python3 -c "
import re
s=open('$OUT/e9_reorder_pre.xml').read()
b=sorted(tuple(map(int,m.groups())) for m in re.finditer(r'resource-id=\"tile:dock:[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s))
print((b[-1][0]+b[-1][2])//2 if b else 900)")
drag_to "$FIRST" "$THIRDX" "$ROWY"
adb exec-out screencap -p > "$OUT/e9_reordered.png"
say "row now:"; row | tee -a "$LOG"
check_absent "the reordered row is not what it was" "$ROW_BEFORE_REORDER" "$(row_keys)"
check "and still holds the same three tiles" "$(echo "$ROW_BEFORE_REORDER" | tr ' ' '\n' | sort | tr '\n' ' ')" \
  "$(row_keys | tr ' ' '\n' | sort | tr '\n' ' ')"

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
say "row now:"; row | tee -a "$LOG"
check "the row is one shorter" 2 "$(row_len)"
check_absent "the unpinned row tile is gone from the layout entirely" "${D#dock:}" "$(row_keys) $(grid_keys)"
adb shell input keyevent KEYCODE_BACK; sleep 1

say "--- 5. fill the row to capacity, then a further drop is refused ---"
for i in 1 2 3 4; do
  G=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
  drag_to "$G" 540 "$ROWY"
  say "after adding $G:"; row | tee -a "$LOG"
done
ROWLEN=$(row_len)
say "row now holds $ROWLEN"
check "the row filled to its capacity of 6" 6 "$ROWLEN"
layout_save "$OUT/e9_full_row.json"
G=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
drag_to "$G" 540 "$ROWY"
adb exec-out screencap -p > "$OUT/e9_refused.png"
say "expect REFUSED — the row is unchanged and $G stayed in the grid:"
row | tee -a "$LOG"; layout_order | head -1 | tee -a "$LOG"
check "the full row refused the drop and is unchanged" \
  "$(python3 -c "import json; print(' '.join(json.load(open('$OUT/e9_full_row.json'))['dock']))")" "$(row_keys)"
check_contains "and the refused tile stayed in the grid" "$G" "$(grid_keys)"

say "--- 6. turn 'show more tiles' off with a full row: the overflow moves to the end of the grid ---"
say "row before:"; row | tee -a "$LOG"; layout_order | tee -a "$LOG"
adb shell am start -n app.tileshell/app.tileshell.settings.SettingsActivity >/dev/null; sleep 2
scroll_to_id "$OUT/e9_settings.xml" "settings_start_theme" >/dev/null 2>&1 || true
tap_id "$OUT/e9_settings.xml" "settings_start_theme" >/dev/null 2>&1 || true
sleep 1.5
scroll_to_id "$OUT/e9_settings.xml" "theme_show_more_tiles" || say "FAIL could not find the show-more-tiles toggle in Settings"
tap_id "$OUT/e9_settings.xml" "theme_show_more_tiles"; sleep 1.5
adb exec-out screencap -p > "$OUT/e9_settings_columns.png"
ensure_start
adb exec-out screencap -p > "$OUT/e9_two_columns.png"
say "row after the column change:"; row | tee -a "$LOG"; layout_order | tee -a "$LOG"
dump "$OUT/e9_two_columns.xml"
python3 "$HERE/e9_overflow.py" "$OUT/e9_two_columns.xml" | tee -a "$LOG"
check "every tile is inside the 2-column grid" 0 "${PIPESTATUS[0]}"
check "the row was cut to the 2-column capacity" 4 "$(row_len)"
check "and the two overflow tiles are at the end of the grid" "slot:CAMERA slot:PHONE" \
  "$(grid_keys | awk '{print $(NF-1), $NF}')"

say "--- 7. the row's LAST tile dragged out leaves an empty row that can be refilled ---"
layout_restore "$HERE/../baseline_layout.json"
ensure_start
for i in 1 2 3; do
  D=$(layout_json | python3 -c "import json,sys; d=json.load(sys.stdin); print('dock:'+d['dock'][0] if d['dock'] else '')")
  [ -n "$D" ] || break
  drag_to "$D" 540 700 2.6
done
say "row after dragging every tile out:"; row | tee -a "$LOG"
check "the row is empty" 0 "$(row_len)"
adb exec-out screencap -p > "$OUT/e9_empty_row.png"
G=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
drag_to "$G" 540 "$ROWY"
say "row after dropping a tile back in:"; row | tee -a "$LOG"
check "an empty row can be refilled" 1 "$(row_len)"
adb exec-out screencap -p > "$OUT/e9_refilled_row.png"

say "--- 8. a WIDE tile dragged into the row becomes a small row tile ---"
layout_restore "$HERE/../baseline_layout.json"
ensure_start
W=$(layout_json | python3 -c "
import json,sys
print(next((o['key'] for o in json.load(sys.stdin)['order'] if o['size']=='WIDE'), ''))")
if [ -n "$W" ]; then
  drag_to "$W" 540 "$ROWY"
  say "row after the wide tile:"; row | tee -a "$LOG"
  check_contains "the wide tile is in the row" "$W" "$(row_keys)"
  adb exec-out screencap -p > "$OUT/e9_wide_in_row.png"
  dump "$OUT/e9_wide_in_row.xml"
  check_contains "and it draws at the row's own size" "tile:dock:$W" \
    "$(grep -o "tile:dock:$W" "$OUT/e9_wide_in_row.xml" | head -1)"
fi

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e9_diag.txt"
layout_restore "$OUT/layout_before.json"
# put the column setting back the way it was (RV12)
adb shell am start -n app.tileshell/app.tileshell.settings.SettingsActivity >/dev/null; sleep 2
scroll_to_id "$OUT/e9_settings_restore.xml" "settings_start_theme" >/dev/null 2>&1 || true
tap_id "$OUT/e9_settings_restore.xml" "settings_start_theme" >/dev/null 2>&1 || true
sleep 1.5
if scroll_to_id "$OUT/e9_settings_restore.xml" "theme_show_more_tiles"; then
  tap_id "$OUT/e9_settings_restore.xml" "theme_show_more_tiles"; sleep 1.5
  say "show more tiles restored to on"
fi
ensure_start
layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout and the column setting restored"
qa_finish
