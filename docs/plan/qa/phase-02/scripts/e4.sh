#!/usr/bin/env bash
# E4: the layout survives process death and a reboot.
#
# The row now MUTATES the layout first — pin nothing new, but resize a tile, build a folder, move a tile into
# the bottom row — and asserts the pre-state differs from the default before it starts. Without that the row
# ran against the pristine default layout, which is exactly what a broken store reseeds to, so it could not
# fail (gate finding).
# usage: e4.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E04.txt
mkdir -p "$OUT"; : > "$LOG"
say "# E4 $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$HERE/../baseline_layout.json" || { say "FAIL could not seed the baseline"; exit 1; }

# --- build a layout that is NOT the default ------------------------------------------------------------------
say "--- mutating the layout through the product so the row has something to lose ---"
ensure_start
dump "$OUT/e4_seed.xml"
A=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
B=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
FROM=$(tile_center "$OUT/e4_seed.xml" "$A"); TO=$(edit_point "$OUT/e4_seed.xml" "$B")
say "folder: dragging $A onto $B and releasing inside the dwell"
down ${FROM% *} ${FROM#* }; sleep 1.1
glide ${FROM% *} ${FROM#* } ${TO% *} ${TO#* } 8
sleep 0.8
up ${TO% *} ${TO#* }; sleep 1.5
adb shell input keyevent KEYCODE_BACK; sleep 1.2

ensure_start
dump "$OUT/e4_seed2.xml"
C=$(layout_json | python3 -c "
import json,sys
print([o['key'] for o in json.load(sys.stdin)['order'] if not o['key'].startswith('folder:')][-1])")
CXY=$(tile_center "$OUT/e4_seed2.xml" "$C")
say "resize: cycling $C once"
enter_edit ${CXY% *} ${CXY#* }
dump "$OUT/e4_seed3.xml"
if P=$(disc_center "$OUT/e4_seed3.xml" resize); then adb shell input tap ${P% *} ${P#* }; sleep 1.5; fi
adb shell input keyevent KEYCODE_BACK; sleep 1.2

ensure_start
dump "$OUT/e4_seed4.xml"
D=$(layout_json | python3 -c "
import json,sys
print([o['key'] for o in json.load(sys.stdin)['order'] if not o['key'].startswith('folder:')][-1])")
ROWY=$(python3 -c "
import re
s=open('$OUT/e4_seed4.xml').read()
m=re.search(r'resource-id=\"tile:dock:[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s)
print((int(m.group(2))+int(m.group(4)))//2 if m else 2050)")
DXY=$(tile_center "$OUT/e4_seed4.xml" "$D")
say "bottom row: dragging $D into the row at y=$ROWY"
down ${DXY% *} ${DXY#* }; sleep 1.1
glide ${DXY% *} ${DXY#* } 540 "$ROWY" 8
sleep 0.9
up 540 "$ROWY"; sleep 1.4
adb shell input keyevent KEYCODE_BACK; sleep 1.2
ensure_start

layout_save "$OUT/e4_before.json"
adb exec-out screencap -p > "$OUT/e4_before.png"
dump "$OUT/e4_before.xml"
say "the layout this row is about:"; layout_order | tee -a "$LOG"

# The precondition: if this row is testing the default layout, it is testing nothing.
check_cmd "the pre-state is NOT the default layout" \
  bash -c "! diff -q '$OUT/e4_before.json' '$HERE/../baseline_layout.json' >/dev/null"
check_cmd "it contains a folder" \
  bash -c "python3 -c \"import json,sys; sys.exit(0 if json.load(open('$OUT/e4_before.json'))['folders'] else 1)\""
check_cmd "the bottom row is not the default three" \
  bash -c "python3 -c \"import json,sys; d=json.load(open('$OUT/e4_before.json')); sys.exit(0 if len(d['dock'])!=3 else 1)\""

# --- force-stop ------------------------------------------------------------------------------------------------
say "--- force-stop then Home ---"
adb shell am force-stop app.tileshell
adb shell input keyevent KEYCODE_HOME; sleep 4
ensure_start
layout_save "$OUT/e4_after_forcestop.json"
dump "$OUT/e4_after_forcestop.xml"
adb exec-out screencap -p > "$OUT/e4_after_forcestop.png"
check_cmd "the store is identical after a force-stop" diff -q "$OUT/e4_before.json" "$OUT/e4_after_forcestop.json"
check_cmd "and so are the tiles on screen" python3 "$HERE/dumpdiff.py" "$OUT/e4_before.xml" "$OUT/e4_after_forcestop.xml"

# --- reboot ------------------------------------------------------------------------------------------------------
say "--- reboot ---"
adb reboot
adb wait-for-device
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 4; done
sleep 8
ensure_start
layout_save "$OUT/e4_after_reboot.json"
dump "$OUT/e4_after_reboot.xml"
adb exec-out screencap -p > "$OUT/e4_after_reboot.png"
check_cmd "the store is identical after a reboot" diff -q "$OUT/e4_before.json" "$OUT/e4_after_reboot.json"
check_cmd "and so are the tiles on screen" python3 "$HERE/dumpdiff.py" "$OUT/e4_before.xml" "$OUT/e4_after_reboot.xml"

layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
