#!/usr/bin/env bash
# E3: live folders — create by dropping during the dwell, expand and collapse, the mini tiles on the face,
# naming through the placeholder and through tap-and-hold on the name, a third and fourth member, and a
# two-member folder dissolving when one member is unpinned (H19).
# usage: e3.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E03.txt
mkdir -p "$OUT"; : > "$LOG"
die() { say "ABORT: $*"; layout_restore "$OUT/layout_before.json"; exit 1; }
folders_now() { layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
for f in d['folders']:
    print('  folder', f['id'], 'name=' + repr(f['name']), 'members=' + str([m['key'] for m in f['members']]))
print('  (no folders)' if not d['folders'] else '', end='')"; }

say "# E3 $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$(dirname "$0")/../baseline_layout.json"   # every row starts from the same Start

# make_folder <A> <B>: one gesture, released inside the dwell. Leaves edit mode ON, folder expanded.
make_folder() {
  local A=$1 B=$2 from to
  ensure_start || die "not on Start"
  dump "$OUT/e3_pre.xml"
  from=$(tile_center "$OUT/e3_pre.xml" "$A") || die "no tile $A"
  to=$(edit_point "$OUT/e3_pre.xml" "$B") || die "no tile $B"
  down ${from% *} ${from#* }; sleep 1.1
  glide ${from% *} ${from#* } ${to% *} ${to#* } 8
  sleep 0.8
  up ${to% *} ${to#* }; sleep 1.5
}

A_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
B_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
say "--- 1. create a folder: $A_ID dropped on $B_ID, released inside the dwell ---"
make_folder "$A_ID" "$B_ID"
adb exec-out screencap -p > "$OUT/e3_created_expanded.png"
dump "$OUT/e3_created.xml"
FOLDER=$(python3 -c "
import re
m = re.search(r'resource-id=\"tile:(folder:[^\"]+)\"', open('$OUT/e3_created.xml').read())
print(m.group(1) if m else '')")
[ -n "$FOLDER" ] || die "no folder tile in the dump after the drop"
FID=${FOLDER#folder:}
say "folder tile = $FOLDER (id $FID); R6 §1.6.2: created in place, Start stays in edit mode, the band opens"
say "band rules in the dump: top=$(bounds "$OUT/e3_created.xml" "folder_band_top:$FID") bottom=$(bounds "$OUT/e3_created.xml" "folder_band_bottom:$FID")"
say "members drawn in the band: $(grep -o 'resource-id="tile:member:[^"]*"' "$OUT/e3_created.xml" | tr '\n' ' ')"
folders_now | tee -a "$LOG"

say "--- 2. name it through the 'Name folder' placeholder (R6 §1.7.1-§1.7.2) ---"
PH=$(bounds "$OUT/e3_created.xml" "folder_name_placeholder:$FID")
[ -n "$PH" ] || die "no Name folder placeholder in the band"
say "placeholder bounds: $PH"
PC=$(center "$OUT/e3_created.xml" "folder_name_placeholder:$FID")
adb shell input tap ${PC% *} ${PC#* }; sleep 1.5
dump "$OUT/e3_name_box.xml"
adb exec-out screencap -p > "$OUT/e3_name_box.png"
if grep -q 'resource-id="folder_name_box"' "$OUT/e3_name_box.xml"; then
  say "the name box opened: $(bounds "$OUT/e3_name_box.xml" folder_name_box)"
fi
check_contains "the placeholder opens the name box" "folder_name_box" \
  "$(grep -o 'folder_name_box' "$OUT/e3_name_box.xml" | head -1)"
adb shell input text "Work"; sleep 0.6
adb shell input keyevent KEYCODE_ENTER; sleep 1.5
adb exec-out screencap -p > "$OUT/e3_named.png"
say "after typing Work:"; folders_now | tee -a "$LOG"
check "the folder is named" "Work" "$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin); print(d['folders'][0]['name'] if d['folders'] else 'NOFOLDER')")"

say "--- 3. tap-and-hold on the name opens the same box (R6 §1.7.3) ---"
dump "$OUT/e3_after_name.xml"
NB=$(center "$OUT/e3_after_name.xml" "folder_name_placeholder:$FID" || true)
if [ -n "$NB" ]; then
  down ${NB% *} ${NB#* }; sleep 1.0; up ${NB% *} ${NB#* }; sleep 1.2
  dump "$OUT/e3_hold_name.xml"
  check_contains "tap-and-hold on the name opens the same box" "folder_name_box" \
    "$(grep -o 'folder_name_box' "$OUT/e3_hold_name.xml" | head -1)"
  adb shell input keyevent KEYCODE_ENTER; sleep 1
else
  say "SKIP no name row in the dump"
fi

say "--- 4. collapse and re-expand by tapping the folder tile (R6 §1.6.6 / §1.6.7) ---"
adb shell input keyevent KEYCODE_BACK; sleep 1.5      # out of edit mode; H9: the folder stays expanded
adb exec-out screencap -p > "$OUT/e3_expanded_not_editing.png"
dump "$OUT/e3_expanded.xml"
check_contains "H9: leaving edit mode leaves the folder expanded" "folder_band_top:$FID" \
  "$(grep -o "folder_band_top:$FID" "$OUT/e3_expanded.xml" | head -1)"
XY=$(center "$OUT/e3_expanded.xml" "tile:$FOLDER") || die "no folder tile to tap"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.5
dump "$OUT/e3_collapsed.xml"
adb exec-out screencap -p > "$OUT/e3_collapsed.png"
check_absent "a tap folds the band away" "folder_band_top:$FID" \
  "$(grep -o "folder_band_top:$FID" "$OUT/e3_collapsed.xml" | head -1)"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.5
dump "$OUT/e3_reexpanded.xml"
adb exec-out screencap -p > "$OUT/e3_reexpanded.png"
check_contains "another tap re-opens it" "folder_band_top:$FID" \
  "$(grep -o "folder_band_top:$FID" "$OUT/e3_reexpanded.xml" | head -1)"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.5      # collapsed again for the next step

say "--- 5. a third and a fourth member, dropped on the collapsed folder tile ---"
for n in 3 4; do
  C=$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print([o['key'] for o in d['order'] if not o['key'].startswith('folder:')][-1])")
  say "member $n: dragging $C onto $FOLDER"
  make_folder "$C" "$FOLDER"
  adb exec-out screencap -p > "$OUT/e3_member_$n.png"
  folders_now | tee -a "$LOG"
  adb shell input keyevent KEYCODE_BACK; sleep 1.2
done
ensure_start
dump "$OUT/e3_face.xml"
# Adding a member re-expands the folder, so the face has to be collapsed again before it is captured — the
# capture H12 pointed at was actually of the EXPANDED band (gate finding).
if grep -q "folder_band_top:$FID" "$OUT/e3_face.xml"; then
  XY=$(center "$OUT/e3_face.xml" "tile:$FOLDER")
  adb shell input tap ${XY% *} ${XY#* }; sleep 1.5
  dump "$OUT/e3_face.xml"
fi
adb exec-out screencap -p > "$OUT/e3_face_3plus1.png"
check_absent "the face capture is COLLAPSED, not the band" "folder_band_top:$FID" \
  "$(grep -o "folder_band_top:$FID" "$OUT/e3_face.xml" | head -1)"
check "the folder holds four members for the 3 + 1 face (H12)" 4 "$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin); print(len(d['folders'][0]['members']) if d['folders'] else 0)")"

say "--- 5b. a tile dropped on an empty cell of the EXPANDED band joins the folder there ---"
ensure_start
dump "$OUT/e3_band_pre.xml"
XY=$(center "$OUT/e3_band_pre.xml" "tile:$FOLDER" || true)
if [ -n "$XY" ]; then
  adb shell input tap ${XY% *} ${XY#* }; sleep 1.6      # expand it
  dump "$OUT/e3_band_open.xml"
  BEFORE_MEMBERS=$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin); print(len(d['folders'][0]['members']) if d['folders'] else 0)")
  G=$(layout_json | python3 -c "
import json,sys
print([o['key'] for o in json.load(sys.stdin)['order'] if not o['key'].startswith('folder:')][-1])")
  # an empty cell inside the band: to the right of the last member row
  BANDY=$(python3 -c "
import re
s=open('$OUT/e3_band_open.xml').read()
t=re.search(r'resource-id="folder_band_top:[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
b=re.search(r'resource-id="folder_band_bottom:[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
print((int(t.group(4))+int(b.group(2)))//2 if t and b else 0)")
  FROM=$(tile_center "$OUT/e3_band_open.xml" "$G")
  say "dragging $G into the band at y=$BANDY (members before: $BEFORE_MEMBERS)"
  if [ -n "$FROM" ] && [ "$BANDY" -gt 0 ]; then
    down ${FROM% *} ${FROM#* }; sleep 1.1
    glide ${FROM% *} ${FROM#* } 900 "$BANDY" 8
    sleep 0.9
    up 900 "$BANDY"; sleep 1.5
    adb exec-out screencap -p > "$OUT/e3_band_drop.png"
    check "the tile dropped in the band joined the folder" "$((BEFORE_MEMBERS + 1))" "$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin); print(len(d['folders'][0]['members']) if d['folders'] else 0)")"
    check_absent "and left the grid" "$G" "$(layout_json | python3 -c "
import json,sys
print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))")"
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 1.2
fi

say "--- 6. unpin one member of a two-member folder: it dissolves into the other (H19) ---"
layout_restore "$(dirname "$0")/../baseline_layout.json"
make_folder "$A_ID" "$B_ID"
dump "$OUT/e3_dissolve_pre.xml"
# Unpin A (the tile that was dropped), so B — the check below — is the survivor. This used to take the
# FIRST member in the dump, which is B, and then assert that B survived: the product dissolved the folder
# correctly into A and the row read that as a failure (2026-09-22 suite).
M="member:$A_ID"
grep -q "resource-id=\"tile:$M\"" "$OUT/e3_dissolve_pre.xml" || die "no band member $M to unpin"
# R6 §1.6.2 (H11): the dropped tile is still SELECTED when its folder is created, and a tap on a selected tile
# deselects it — so tap only when its discs are not already showing.
if disc_center "$OUT/e3_dissolve_pre.xml" unpin >/dev/null 2>&1; then
  say "band member $M is already selected (the dropped tile stays selected, R6 §1.6.2)"
  cp "$OUT/e3_dissolve_pre.xml" "$OUT/e3_dissolve_sel.xml"
else
  say "selecting band member $M"
  MC=$(center "$OUT/e3_dissolve_pre.xml" "tile:$M")
  adb shell input tap ${MC% *} ${MC#* }; sleep 1.0
  dump "$OUT/e3_dissolve_sel.xml"
fi
P=$(disc_center "$OUT/e3_dissolve_sel.xml" unpin) || die "no unpin disc for the selected member"
say "tapping its unpin disc at $P"
adb shell input tap ${P% *} ${P#* }; sleep 1.5
adb exec-out screencap -p > "$OUT/e3_dissolved.png"
say "order after:"
layout_order | tee -a "$LOG"
check "the folder dissolved (H19)" 0 "$(layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['folders']))")"
check_contains "the surviving member is back in the grid" "$B_ID" "$(layout_json | python3 -c "
import json,sys
print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))")"
adb shell input keyevent KEYCODE_BACK; sleep 1

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e3_diag.txt"
layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
