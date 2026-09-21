#!/usr/bin/env bash
# E3: live folders — create by dropping during the dwell, expand and collapse, the mini tiles on the face,
# naming through the placeholder and through tap-and-hold on the name, a third and fourth member, a drop into
# the expanded band, and a two-member folder dissolving when one member is unpinned (H19).
# usage: e3.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E03.txt
mkdir -p "$OUT"; : > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }
say "# E3 $(date -Iseconds)"
layout_save "$OUT/layout_before.json"

make_folder() { # make_folder <A> <B>: one gesture, released inside the dwell
  local A=$1 B=$2 from to
  adb shell input keyevent KEYCODE_HOME; sleep 2
  dump "$OUT/e3_pre.xml"
  from=$(tile_center "$OUT/e3_pre.xml" "$A"); to=$(edit_point "$OUT/e3_pre.xml" "$B")
  down ${from% *} ${from#* }; sleep 1.1
  glide ${from% *} ${from#* } ${to% *} ${to#* } 8
  sleep 0.8
  up ${to% *} ${to#* }; sleep 1.4
}

A_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
B_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
say "--- 1. create a folder from $A_ID dropped on $B_ID ---"
make_folder "$A_ID" "$B_ID"
adb exec-out screencap -p > "$OUT/e3_created_expanded.png"
dump "$OUT/e3_created.xml"
say "expect: the folder is created in place, Start stays in edit mode and the folder auto-expands (R6 §1.6.2)"
layout_order | tee -a "$LOG"

say "--- 2. name it through the 'Name folder' placeholder ---"
PH=$(bounds "$OUT/e3_created.xml" "folder_name_placeholder:f1" || true)
say "placeholder bounds: ${PH:-not exported to the dump; tapping its drawn position instead}"
NX=$(python3 -c "
import re
s=open('$OUT/e3_created.xml').read()
m=re.search(r'resource-id=\"folder_band_top:[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s)
print(f'{60} {int(m.group(2))+30}' if m else '60 900')")
say "tapping the placeholder at $NX"
adb shell input tap ${NX% *} ${NX#* }; sleep 1
adb exec-out screencap -p > "$OUT/e3_name_box.png"
adb shell input text "Work"; sleep 0.5
adb shell input keyevent KEYCODE_ENTER; sleep 1.2
adb exec-out screencap -p > "$OUT/e3_named.png"
say "folder name in the store:"; layout_json | python3 -c "import json,sys; print([f['name'] for f in json.load(sys.stdin)['folders']])" | tee -a "$LOG"

say "--- 3. collapse and re-expand (taps on the folder tile, R6 §1.6.6 / §1.6.7) ---"
adb shell input keyevent KEYCODE_BACK; sleep 1       # out of edit mode, folder stays expanded (H9)
adb exec-out screencap -p > "$OUT/e3_expanded_not_editing.png"
dump "$OUT/e3_expanded.xml"
F=$(python3 -c "
import re
s=open('$OUT/e3_expanded.xml').read()
m=re.search(r'resource-id=\"tile:(folder:[^\"]+)\"', s)
print(m.group(1) if m else '')")
say "folder tile id: $F"
XY=$(tile_center "$OUT/e3_expanded.xml" "$F")
adb shell input tap ${XY% *} ${XY#* }; sleep 1.2
adb exec-out screencap -p > "$OUT/e3_collapsed.png"
dump "$OUT/e3_collapsed.xml"
say "expect: the band folded away and the face shows its mini tiles again"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.2
adb exec-out screencap -p > "$OUT/e3_reexpanded.png"

say "--- 4. add a third and a fourth member by dropping on the collapsed folder tile ---"
adb shell input tap ${XY% *} ${XY#* }; sleep 1.2     # collapse first
for n in 3 4; do
  C=$(layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print([o['key'] for o in d['order'] if not o['key'].startswith('folder:')][-1])")
  say "member $n: dragging $C onto $F"
  make_folder "$C" "$F"
  adb exec-out screencap -p > "$OUT/e3_member_$n.png"
  layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print('members now:', [m['key'] for f in d['folders'] for m in f['members']])" | tee -a "$LOG"
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
say "expect after the fourth: the collapsed face shows 3 + 1 mini tiles (R6 §1.6.3, judged in H12)"
adb exec-out screencap -p > "$OUT/e3_face_3plus1.png"

say "--- 5. unpin one member of a two-member folder: it dissolves into the other (H19) ---"
layout_restore "$OUT/layout_before.json"
make_folder "$A_ID" "$B_ID"
dump "$OUT/e3_dissolve_pre.xml"
M=$(python3 -c "
import re
s=open('$OUT/e3_dissolve_pre.xml').read()
m=re.search(r'resource-id=\"tile:member:([^\"]+)\"', s)
print('member:'+m.group(1) if m else '')")
say "unpinning band member $M"
if [ -n "$M" ]; then
  MC=$(tile_center "$OUT/e3_dissolve_pre.xml" "$M")
  adb shell input tap ${MC% *} ${MC#* }; sleep 0.8       # select it inside the band
  dump "$OUT/e3_dissolve_sel.xml"
  P=$(corner_point "$OUT/e3_dissolve_sel.xml" "$M" top)
  adb shell input tap ${P% *} ${P#* }; sleep 1.3
fi
adb exec-out screencap -p > "$OUT/e3_dissolved.png"
say "order after (expect: no folder, the surviving tile in its place at its own size):"
layout_order | tee -a "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e3_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored"
