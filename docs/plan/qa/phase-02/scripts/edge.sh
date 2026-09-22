#!/usr/bin/env bash
# Phase 02 edge cases that can be driven deterministically. The rest (pinning an app already on Start, the
# secondary-tile cases) belong to the rows that own those parts and run there.
# usage: edge.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/EDGE.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
say "# phase 02 edge cases $(date -Iseconds)"
layout_save "$OUT/layout_before.json"
layout_restore "$(dirname "$0")/../baseline_layout.json"   # every row starts from the same Start

say "=== 1. a layout store written by an older shell build (v2 -> v3 upgrade keeps the layout) ==="
python3 - "$OUT/v2.json" <<'PY'
import json, sys
# The phase 01 (v2) shape: coordinates, no folders. Deliberately in a scrambled array order so the upgrade has
# to read the coordinates, not the array.
v2 = {"version": 2, "placements": [
    {"key": "shell:weather", "x": 0, "y": 4, "size": "WIDE"},
    {"key": "slot:PEOPLE", "x": 0, "y": 0, "size": "MEDIUM"},
    {"key": "slot:MAIL", "x": 4, "y": 0, "size": "MEDIUM"},
    {"key": "slot:BROWSER", "x": 2, "y": 0, "size": "MEDIUM"},
    {"key": "slot:CALENDAR", "x": 0, "y": 2, "size": "WIDE"},
    {"key": "slot:STORE", "x": 4, "y": 4, "size": "SMALL"},
], "slots": {}, "dock": ["slot:PHONE", "slot:MESSAGING", "slot:CAMERA"]}
json.dump(v2, open(sys.argv[1], "w"))
PY
layout_restore "$OUT/v2.json"
say "store after the upgrade (expect version 3, the six tiles in READING order, the row kept):"
layout_json | tee -a "$LOG" | python3 -c "
import json,sys
d=json.load(sys.stdin)
order=[o['key'] for o in d['order']]
want=['slot:PEOPLE','slot:BROWSER','slot:MAIL','slot:CALENDAR','shell:weather','slot:STORE']
print('version', d['version'], 'PASS' if d['version']==3 else 'FAIL')
print('order   ', order)
print('expected', want, 'PASS' if order==want else 'FAIL')
print('row     ', d['dock'], 'PASS' if d['dock']==['slot:PHONE','slot:MESSAGING','slot:CAMERA'] else 'FAIL')"
adb exec-out screencap -p > "$OUT/edge_v2_upgrade.png"
layout_restore "$OUT/layout_before.json"

say "=== 2. Back in edit mode, without and with a folder expanded (H9) ==="
ensure_start
dump "$OUT/edge_back_pre.xml"
XY=$(tile_center "$OUT/edge_back_pre.xml" "slot:PEOPLE" || tile_center "$OUT/edge_back_pre.xml" "slot:MAIL")
enter_edit ${XY% *} ${XY#* }
adb exec-out screencap -p > "$OUT/edge_back_in_edit.png"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
adb exec-out screencap -p > "$OUT/edge_back_exited.png"
say "expect: Back left edit mode and stayed on Start (top activity: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//'))"

say "=== 3. drag across several occupied cells without stopping: no reflow, no feedback left behind ==="
ensure_start
dump "$OUT/edge_sweep_pre.xml"
layout_save "$OUT/edge_sweep_before.json"
A=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
FROM=$(tile_center "$OUT/edge_sweep_pre.xml" "$A")
down ${FROM% *} ${FROM#* }; sleep 1.1
# one continuous sweep across the whole grid, never pausing on a tile
glide ${FROM% *} ${FROM#* } 120 300 4
glide 120 300 950 300 6
glide 950 300 120 800 6
glide 120 800 950 800 6
adb exec-out screencap -p > "$OUT/edge_sweeping.png"
# The release lands on EMPTY space below the grid: a release ON a tile inside its dwell is supposed to make a
# folder (that is E8's path), so ending there would test the opposite of this edge case.
glide 950 800 540 1500 4
up 540 1500; sleep 1.3
say "order after the sweep (the tile lands where it was dropped; nothing else should have been folded):"
layout_order | tee -a "$LOG"
layout_json | python3 -c "
import json,sys; d=json.load(sys.stdin)
print('folders after the sweep:', d['folders'], 'PASS' if not d['folders'] else 'FAIL (a folder was made without a dwell)')"
adb shell input keyevent KEYCODE_BACK; sleep 1
layout_restore "$OUT/edge_sweep_before.json"

say "=== 4. process death mid-drag ==="
ensure_start
dump "$OUT/edge_death_pre.xml"
layout_save "$OUT/edge_death_before.json"
A=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
FROM=$(tile_center "$OUT/edge_death_pre.xml" "$A")
down ${FROM% *} ${FROM#* }; sleep 1.1
glide ${FROM% *} ${FROM#* } 540 700 6
adb shell am force-stop app.tileshell
sleep 1
up 540 700
ensure_start
adb exec-out screencap -p > "$OUT/edge_after_death.png"
say "store after the process died mid-drag (expect the layout from before the drag: an uncommitted drag writes nothing):"
diff <(python3 -c "import json;d=json.load(open('$OUT/edge_death_before.json'));print([o['key'] for o in d['order']])") \
     <(layout_json | python3 -c "import json,sys;d=json.load(sys.stdin);print([o['key'] for o in d['order']])") \
  && say "PASS: unchanged" || say "FAIL: the layout changed"

say "=== 5. a wide dragged tile whose centre sits over a small tile ==="
ensure_start
dump "$OUT/edge_wide_pre.xml"
layout_save "$OUT/edge_wide_before.json"
W=$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(next((o['key'] for o in d['order'] if o['size']=='WIDE'), ''))")
S=$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(next((o['key'] for o in d['order'] if o['size']=='SMALL'), ''))")
say "wide $W onto small $S"
if [ -n "$W" ] && [ -n "$S" ]; then
  FROM=$(tile_center "$OUT/edge_wide_pre.xml" "$W"); TO=$(edit_point "$OUT/edge_wide_pre.xml" "$S")
  down ${FROM% *} ${FROM#* }; sleep 1.1
  glide ${FROM% *} ${FROM#* } ${TO% *} ${TO#* } 8
  sleep 0.9
  adb exec-out screencap -p > "$OUT/edge_wide_on_small.png"
  up ${TO% *} ${TO#* }; sleep 1.3
  say "order after (a folder at the small tile's place, holding both, is the expected result):"
  layout_order | tee -a "$LOG"
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
layout_restore "$OUT/edge_wide_before.json"

say "=== 6. drag during a live flip ==="
ensure_start
dump "$OUT/edge_flip_pre.xml"
layout_save "$OUT/edge_flip_before.json"
L=$(grep -o 'resource-id="tile:slot:CALENDAR"' "$OUT/edge_flip_pre.xml" >/dev/null && echo slot:CALENDAR || echo shell:weather)
FROM=$(tile_center "$OUT/edge_flip_pre.xml" "$L")
adb shell rm -f /sdcard/edge_flip.mp4
adb shell screenrecord --bit-rate 16000000 --time-limit 8 /sdcard/edge_flip.mp4 &
REC=$!
sleep 1
down ${FROM% *} ${FROM#* }; sleep 1.1
glide ${FROM% *} ${FROM#* } 540 1200 8
sleep 2.5
up 540 1200; sleep 1.5
wait $REC
adb pull /sdcard/edge_flip.mp4 "$OUT/edge_flip.mp4" >/dev/null
say "recorded a drag of a live tile ($L) to $OUT/edge_flip.mp4; order after:"
layout_order | tee -a "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1
layout_restore "$OUT/edge_flip_before.json"

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -80 > "$OUT/edge_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored"
