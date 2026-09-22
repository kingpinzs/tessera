#!/usr/bin/env bash
# Phase 02 edge cases that can be driven deterministically. The rest (pinning an app already on Start, the
# secondary-tile cases) belong to the rows that own those parts and run there.
# usage: edge.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/EDGE.txt
mkdir -p "$OUT"; : > "$LOG"
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
layout_restore "$OUT/v2.json" || say "note: the v2 file is upgraded on load, so the read-back differs by design"
say "store after the upgrade:"
layout_json > "$OUT/v2_upgraded.json"
cat "$OUT/v2_upgraded.json" >> "$LOG"; echo >> "$LOG"
check "the store upgraded to version 3" 3 "$(python3 -c "import json; print(json.load(open('$OUT/v2_upgraded.json'))['version'])")"
check "the six tiles came back in READING order" \
  "slot:PEOPLE slot:BROWSER slot:MAIL slot:CALENDAR shell:weather slot:STORE" \
  "$(python3 -c "import json; print(' '.join(o['key'] for o in json.load(open('$OUT/v2_upgraded.json'))['order']))")"
check "the bottom row was kept" "slot:PHONE slot:MESSAGING slot:CAMERA" \
  "$(python3 -c "import json; print(' '.join(json.load(open('$OUT/v2_upgraded.json'))['dock']))")"
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
say "order after the sweep:"
layout_order | tee -a "$LOG"
check "sweeping across occupied cells made no folder" 0 \
  "$(layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['folders']))")"
check "and lost no tile" "$(python3 -c "import json; print(len(json.load(open('$OUT/edge_sweep_before.json'))['order']))")" \
  "$(layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['order']))")"
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
check "a drag that never committed wrote nothing" \
  "$(python3 -c "import json; print(' '.join(o['key'] for o in json.load(open('$OUT/edge_death_before.json'))['order']))")" \
  "$(layout_json | python3 -c "import json,sys; print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))")"

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

# Captured BEFORE the restore: layout_restore force-stops the shell, which empties the ring buffer, so this
# file used to be zero bytes by construction.
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -80 > "$OUT/edge_diag.txt"

say "=== 7. Back in edit mode WITH a folder expanded (H9's other half) ==="
layout_restore "$HERE/../baseline_layout.json"
ensure_start
dump "$OUT/edge_h9_pre.xml"
A=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
B=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
FROM=$(tile_center "$OUT/edge_h9_pre.xml" "$A"); TO=$(edit_point "$OUT/edge_h9_pre.xml" "$B")
down ${FROM% *} ${FROM#* }; sleep 1.1
glide ${FROM% *} ${FROM#* } ${TO% *} ${TO#* } 8
sleep 0.8
up ${TO% *} ${TO#* }; sleep 1.5
dump "$OUT/edge_h9_expanded.xml"
check_contains "the new folder is expanded in edit mode" "folder_band_top" "$(grep -o 'folder_band_top:[a-z0-9]*' "$OUT/edge_h9_expanded.xml" | head -1)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump "$OUT/edge_h9_after_back.xml"
adb exec-out screencap -p > "$OUT/edge_h9_after_back.png"
check_absent "Back left edit mode" "edit_disc:unpin" "$(grep -o 'edit_disc:unpin' "$OUT/edge_h9_after_back.xml" | head -1)"
check_contains "and the folder stayed expanded (H9)" "folder_band_top" "$(grep -o 'folder_band_top:[a-z0-9]*' "$OUT/edge_h9_after_back.xml" | head -1)"

say "=== 8. release EXACTLY as the dwell ends (the folder-vs-reflow boundary) ==="
for MS in 1800 2200; do
  layout_restore "$HERE/../baseline_layout.json"
  ensure_start
  dump "$OUT/edge_boundary_${MS}_pre.xml"
  A=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
  B=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
  FROM=$(tile_center "$OUT/edge_boundary_${MS}_pre.xml" "$A"); TO=$(edit_point "$OUT/edge_boundary_${MS}_pre.xml" "$B")
  down ${FROM% *} ${FROM#* }; sleep 1.1
  glide ${FROM% *} ${FROM#* } ${TO% *} ${TO#* } 8
  T0=$(date +%s%3N)
  python3 -c "import time; time.sleep($MS/1000.0)"
  T1=$(date +%s%3N)
  up ${TO% *} ${TO#* }; sleep 1.4
  HELD=$((T1 - T0))
  FOLDERS=$(layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['folders']))")
  say "held ${HELD} ms over the target (asked for ${MS}); folders after = $FOLDERS"
  if [ "$HELD" -lt 2000 ]; then
    check "a release at ${HELD} ms, inside the dwell, makes a folder" 1 "$FOLDERS"
  else
    check "a release at ${HELD} ms, past the dwell, makes no folder" 0 "$FOLDERS"
  fi
  adb exec-out screencap -p > "$OUT/edge_boundary_$MS.png"
  adb shell input keyevent KEYCODE_BACK; sleep 1
done

layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
