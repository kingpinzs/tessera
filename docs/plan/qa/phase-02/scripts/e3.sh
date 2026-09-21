#!/usr/bin/env bash
# E3: live folders — create by dropping during the dwell, expand and collapse, the mini tiles on the face,
# naming through the placeholder and through tap-and-hold on the name, a third and fourth member, and a
# two-member folder dissolving when one member is unpinned (H19).
# usage: e3.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E03.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
die() { say "ABORT: $*"; layout_restore "$OUT/layout_before.json"; exit 1; }
folders_now() { layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
for f in d['folders']:
    print('  folder', f['id'], 'name=' + repr(f['name']), 'members=' + str([m['key'] for m in f['members']]))
print('  (no folders)' if not d['folders'] else '', end='')"; }

say "# E3 $(date -Iseconds)"
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
  say "PASS the name box opened: $(bounds "$OUT/e3_name_box.xml" folder_name_box)"
else
  say "FAIL the name box did not open"
fi
adb shell input text "Work"; sleep 0.6
adb shell input keyevent KEYCODE_ENTER; sleep 1.5
adb exec-out screencap -p > "$OUT/e3_named.png"
say "after typing Work:"; folders_now | tee -a "$LOG"

say "--- 3. tap-and-hold on the name opens the same box (R6 §1.7.3) ---"
dump "$OUT/e3_after_name.xml"
NB=$(center "$OUT/e3_after_name.xml" "folder_name_placeholder:$FID" || true)
if [ -n "$NB" ]; then
  down ${NB% *} ${NB#* }; sleep 1.0; up ${NB% *} ${NB#* }; sleep 1.2
  dump "$OUT/e3_hold_name.xml"
  grep -q 'resource-id="folder_name_box"' "$OUT/e3_hold_name.xml" \
    && say "PASS tap-and-hold on the name opened the box" || say "FAIL tap-and-hold did not open the box"
  adb shell input keyevent KEYCODE_ENTER; sleep 1
else
  say "SKIP no name row in the dump"
fi

say "--- 4. collapse and re-expand by tapping the folder tile (R6 §1.6.6 / §1.6.7) ---"
adb shell input keyevent KEYCODE_BACK; sleep 1.5      # out of edit mode; H9: the folder stays expanded
adb exec-out screencap -p > "$OUT/e3_expanded_not_editing.png"
dump "$OUT/e3_expanded.xml"
grep -q "folder_band_top:$FID" "$OUT/e3_expanded.xml" \
  && say "PASS H9: leaving edit mode left the folder expanded" || say "FAIL the band closed when edit mode ended"
XY=$(center "$OUT/e3_expanded.xml" "tile:$FOLDER") || die "no folder tile to tap"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.5
dump "$OUT/e3_collapsed.xml"
adb exec-out screencap -p > "$OUT/e3_collapsed.png"
grep -q "folder_band_top:$FID" "$OUT/e3_collapsed.xml" \
  && say "FAIL the band is still open after the tap" || say "PASS the band folded away"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.5
dump "$OUT/e3_reexpanded.xml"
adb exec-out screencap -p > "$OUT/e3_reexpanded.png"
grep -q "folder_band_top:$FID" "$OUT/e3_reexpanded.xml" \
  && say "PASS the band re-opened" || say "FAIL the band did not re-open"
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
XY=$(center "$OUT/e3_face.xml" "tile:$FOLDER" || true)
[ -n "$XY" ] && adb exec-out screencap -p > "$OUT/e3_face_3plus1.png"
say "the collapsed face with four members is in e3_face_3plus1.png (3 + 1 mini tiles, R6 §1.6.3, judged in H12)"

say "--- 6. unpin one member of a two-member folder: it dissolves into the other (H19) ---"
layout_restore "$(dirname "$0")/../baseline_layout.json"
make_folder "$A_ID" "$B_ID"
dump "$OUT/e3_dissolve_pre.xml"
M=$(python3 -c "
import re
m = re.search(r'resource-id=\"tile:(member:[^\"]+)\"', open('$OUT/e3_dissolve_pre.xml').read())
print(m.group(1) if m else '')")
[ -n "$M" ] || die "no band member to unpin"
say "selecting band member $M"
MC=$(center "$OUT/e3_dissolve_pre.xml" "tile:$M")
adb shell input tap ${MC% *} ${MC#* }; sleep 1.0
dump "$OUT/e3_dissolve_sel.xml"
P=$(disc_center "$OUT/e3_dissolve_sel.xml" unpin) || die "no unpin disc for the selected member"
say "tapping its unpin disc at $P"
adb shell input tap ${P% *} ${P#* }; sleep 1.5
adb exec-out screencap -p > "$OUT/e3_dissolved.png"
say "order after (expect: no folder, the surviving tile in its place at its own size):"
layout_order | tee -a "$LOG"
folders_now | tee -a "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e3_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored"
