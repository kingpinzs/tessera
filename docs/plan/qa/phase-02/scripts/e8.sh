#!/usr/bin/env bash
# E8: both drag paths of the Decisions' dwell rule (R6 §1.3.3 / §1.6.1, H5 / H20 / H23), each gesture starting
# from the SAME layout:
#   (a) release during the dwell  -> a folder holding A and B, nothing else moved
#   (b) stay past the dwell       -> B and the tiles after it make room, A lands in B's cell, no folder
#   the same two against a collapsed folder F, and a folder dragged over a folder (no nesting)
# usage: e8.sh <out dir>
set -u
source "$(dirname "$0")/gestures.sh"
source "$(dirname "$0")/layout.sh"
OUT=$1; LOG=$OUT/E08.txt
mkdir -p "$OUT"
: > "$LOG"
say() { echo "$*" | tee -a "$LOG"; }

say "# E8 $(date -Iseconds)"
layout_save "$OUT/layout_before.json"
BASE=$OUT/e8_base.json
layout_save "$BASE"

# drag <label> <tile A> <tile B> <hold ms after the centre arrives> : one continuous gesture.
# A screencap is taken at ≈1000 ms (inside the dwell) and at ≈2600 ms (past it) whenever the hold runs that long.
drag() {
  local label=$1 A=$2 B=$3 holdms=$4
  adb shell input keyevent KEYCODE_HOME; sleep 2
  dump "$OUT/${label}_start.xml"
  local from to
  from=$(tile_center "$OUT/${label}_start.xml" "$A") || { say "$label: no tile $A"; return 1; }
  to=$(edit_point "$OUT/${label}_start.xml" "$B") || { say "$label: no tile $B"; return 1; }
  say "--- $label: $A -> $B, holding ${holdms} ms over it ---"
  say "    from $from to $to"
  down ${from% *} ${from#* }
  sleep 1.1                                   # past the 783 ms hold (E7 brackets it at 740 / 830)
  glide ${from% *} ${from#* } ${to% *} ${to#* } 8
  if [ "$holdms" -ge 1000 ]; then sleep 1.0; adb exec-out screencap -p > "$OUT/${label}_at1000ms.png"; fi
  if [ "$holdms" -ge 2600 ]; then sleep 1.6; adb exec-out screencap -p > "$OUT/${label}_at2600ms.png"; fi
  if [ "$holdms" -lt 1000 ]; then sleep 0.8; fi
  up ${to% *} ${to#* }
  sleep 1.3
  adb exec-out screencap -p > "$OUT/${label}_after.png"
  dump "$OUT/${label}_after.xml"
  say "    order after:"; layout_order | tee -a "$LOG"
  adb shell input keyevent KEYCODE_BACK; sleep 1
}

FIRST=$(python3 -c "
import re,sys
s=open('$OUT/../E01/e1_start.xml' if False else '$BASE').read()
import json; d=json.loads(s); print(d['order'][0]['key'])" 2>/dev/null || true)
A_ID=$(python3 -c "import json; d=json.load(open('$BASE')); print(d['order'][-1]['key'])")
B_ID=$(python3 -c "import json; d=json.load(open('$BASE')); print(d['order'][0]['key'])")
say "A = $A_ID (last in the order), B = $B_ID (first)"
say "baseline order:"; layout_order | tee -a "$LOG"

# --- (a) release inside the dwell -> folder --------------------------------------------------------------------
drag "e8a_folder" "$A_ID" "$B_ID" 800
say "expect: a folder holding $B_ID and $A_ID, in B's place, and no other tile moved"

# --- (b) stay past the dwell -> move, no folder -----------------------------------------------------------------
layout_restore "$BASE"
drag "e8b_reflow" "$A_ID" "$B_ID" 2600
say "expect: no folder; $A_ID sits in $B_ID's former cell and the tiles there slid down"

# --- (c) and (d): the same two paths against a collapsed folder --------------------------------------------------
layout_restore "$BASE"
say "--- building a folder to drag onto ---"
drag "e8_makefolder" "$A_ID" "$B_ID" 800
FOLDER=$(python3 -c "
import json; d=json.load(open('/dev/stdin'))
print(next((o['key'] for o in d['order'] if o['key'].startswith('folder:')), ''))" < <(layout_json))
say "folder tile = $FOLDER"
if [ -n "$FOLDER" ]; then
  layout_save "$OUT/e8_with_folder.json"
  C_ID=$(python3 -c "import json; d=json.load(open('$OUT/e8_with_folder.json')); print([o['key'] for o in d['order'] if not o['key'].startswith('folder:')][-1])")
  say "C = $C_ID will be dragged onto the folder"
  drag "e8c_addtofolder" "$C_ID" "$FOLDER" 800
  say "expect: $C_ID joined the folder (three members), the folder tile did not move"
  layout_restore "$OUT/e8_with_folder.json"
  drag "e8d_folderreflow" "$C_ID" "$FOLDER" 2600
  say "expect: the folder made room, $C_ID took its cell, the folder's members are unchanged"
  # --- (e) a folder dragged over another tile: no feedback, no nesting -----------------------------------------
  layout_restore "$OUT/e8_with_folder.json"
  D_ID=$(python3 -c "import json; d=json.load(open('$OUT/e8_with_folder.json')); print([o['key'] for o in d['order'] if not o['key'].startswith('folder:')][0])")
  drag "e8e_nonesting" "$FOLDER" "$D_ID" 2600
  say "expect: no folder inside a folder; the screencap at 1000 ms shows no folder feedback on $D_ID"
else
  say "FAIL: no folder was created, so the folder-target paths could not run"
fi

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e8_diag.txt"
layout_restore "$OUT/layout_before.json"
say "baseline layout restored"
