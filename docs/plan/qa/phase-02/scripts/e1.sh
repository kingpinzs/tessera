#!/usr/bin/env bash
# E1: long-press and drag as ONE gesture enters edit mode and moves a tile; the resize disc cycles the size;
# the unpin disc removes the tile and the tiles after it close the gap. Plus H8's capture (the selection
# moving to another tile), which had no evidence.
#
# Every step COMPARES, and the row exits non-zero if any comparison fails (gate finding: this driver used to
# print the layout after each step and assert nothing, so a step that did nothing read the same as one that worked).
# usage: e1.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E01.txt
mkdir -p "$OUT"; : > "$LOG"
keys() { layout_json | python3 -c "import json,sys; print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))"; }
size_of() { layout_json | python3 -c "
import json,sys
print(next((o['size'] for o in json.load(sys.stdin)['order'] if o['key'] == '$1'), 'ABSENT'))"; }

say "# E1 $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$HERE/../baseline_layout.json" || { say "FAIL could not seed the baseline"; exit 1; }
ensure_start
dump "$OUT/e1_start.xml"
BEFORE=$(keys)
say "order at rest: $BEFORE"
FROM_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
TO_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")

say "--- 1. hold $FROM_ID past 783 ms, drag onto $TO_ID, release past the dwell (a move, not a folder) ---"
A=$(tile_center "$OUT/e1_start.xml" "$FROM_ID"); B=$(edit_point "$OUT/e1_start.xml" "$TO_ID")
down ${A% *} ${A#* }
sleep 1.1
glide ${A% *} ${A#* } ${B% *} ${B#* } 8
sleep 2.6
adb exec-out screencap -p > "$OUT/e1_dragging.png"
up ${B% *} ${B#* }
sleep 1.4
adb exec-out screencap -p > "$OUT/e1_after_move.png"
AFTER=$(keys)
say "order after the move: $AFTER"
check "the dragged tile took the first cell" "$FROM_ID" "$(echo "$AFTER" | cut -d' ' -f1)"
check "no tile gained or lost" "$(echo "$BEFORE" | wc -w)" "$(echo "$AFTER" | wc -w)"
check_absent "no folder (the release was past the dwell)" "folder:" "$AFTER"

say "--- 2. tapping a different tile moves the selection (H8) ---"
dump "$OUT/e1_selected_first.xml"
D1=$(bounds "$OUT/e1_selected_first.xml" "edit_disc:unpin")
say "unpin disc on the held tile: $D1"
OTHER=$(python3 -c "
import re
s = open('$OUT/e1_selected_first.xml').read()
ids = [m.group(1) for m in re.finditer(r'resource-id=\"tile:([^\"]+)\"', s) if not m.group(1).startswith('dock:')]
print(ids[2] if len(ids) > 2 else ids[-1])")
OXY=$(center "$OUT/e1_selected_first.xml" "tile:$OTHER")
say "tapping $OTHER at $OXY"
adb shell input tap ${OXY% *} ${OXY#* }
sleep 1.0
adb exec-out screencap -p > "$OUT/e1_selection_moved.png"
dump "$OUT/e1_selected_other.xml"
D2=$(bounds "$OUT/e1_selected_other.xml" "edit_disc:unpin")
say "unpin disc after the tap: $D2"
check_absent "the discs moved to the newly selected tile" "$D1" "$D2"
check_contains "edit mode is still on" "edit_disc:unpin" "$(grep -o 'edit_disc:unpin' "$OUT/e1_selected_other.xml" | head -1)"

say "--- 3. resize disc: the cycle in R6 §1.2.6 ---"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
ensure_start
dump "$OUT/e1_before_resize.xml"
C=$(tile_center "$OUT/e1_before_resize.xml" "$TO_ID")
enter_edit ${C% *} ${C#* }
START_SIZE=$(size_of "$TO_ID")
say "$TO_ID starts at $START_SIZE"
next_size() { case "$1" in MEDIUM) echo SMALL;; SMALL) echo WIDE;; WIDE) echo MEDIUM;; *) echo UNKNOWN;; esac; }
CUR=$START_SIZE
for step in 1 2 3; do
  dump "$OUT/e1_resize_${step}_dump.xml"
  if ! P=$(disc_center "$OUT/e1_resize_${step}_dump.xml" resize); then
    QA_FAIL=$((QA_FAIL+1)); say "FAIL no resize disc at step $step"; break
  fi
  WANT=$(next_size "$CUR")
  say "step $step: tapping the resize disc at $P, expecting $CUR -> $WANT"
  adb shell input tap ${P% *} ${P#* }
  sleep 1.5
  adb exec-out screencap -p > "$OUT/e1_resize_$step.png"
  GOT=$(size_of "$TO_ID")
  check "resize step $step" "$WANT" "$GOT"
  CUR=$GOT
done
check "the cycle returned to its starting size" "$START_SIZE" "$CUR"

say "--- 4. unpin disc: the tile goes and the gap closes ---"
dump "$OUT/e1_before_unpin.xml"
BEFORE_UNPIN=$(keys)
if P=$(disc_center "$OUT/e1_before_unpin.xml" unpin); then
  say "tapping the unpin disc at $P"
  adb shell input tap ${P% *} ${P#* }
  sleep 1.5
else
  QA_FAIL=$((QA_FAIL+1)); say "FAIL no unpin disc in the dump"
fi
adb exec-out screencap -p > "$OUT/e1_after_unpin.png"
dump "$OUT/e1_after_unpin.xml"
AFTER_UNPIN=$(keys)
say "order after the unpin: $AFTER_UNPIN"
check_absent "the unpinned tile is out of the store" "$TO_ID" "$AFTER_UNPIN"
check "exactly one tile was removed" "$(( $(echo "$BEFORE_UNPIN" | wc -w) - 1 ))" "$(echo "$AFTER_UNPIN" | wc -w)"
check_absent "and off the screen" "tile:$TO_ID\"" "$(grep -o "tile:$TO_ID\"" "$OUT/e1_after_unpin.xml" | head -1)"
adb shell input keyevent KEYCODE_BACK; sleep 1

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -40 > "$OUT/e1_diag.txt"
layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
