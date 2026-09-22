#!/usr/bin/env bash
# E8: both drag paths of the dwell rule (R6 §1.3.3 / §1.6.1, H5 / H20 / H23), each gesture from the SAME
# layout, now with real assertions and the two cases the gate never ran:
#   (a) release during the dwell    -> a folder holding A and B, in B's place, and NO other tile moved
#   (b) stay past the dwell         -> B and the tiles after it make room, A lands in B's cell, no folder
#   (c) / (d) the same two against a collapsed folder
#   (e) a folder dragged over a PLAIN TILE            -> no nesting, the folder keeps its members
#   (f) a folder dragged over ANOTHER FOLDER          -> no nesting either (never run before)
# usage: e8.sh <out dir>
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
source "$HERE/layout.sh"
source "$HERE/assert.sh"
OUT=$1; LOG=$OUT/E08.txt
mkdir -p "$OUT"; : > "$LOG"
BASE=$OUT/e8_base.json
keys() { layout_json | python3 -c "import json,sys; print(' '.join(o['key'] for o in json.load(sys.stdin)['order']))"; }
folder_count() { layout_json | python3 -c "import json,sys; print(len(json.load(sys.stdin)['folders']))"; }
members_of() { layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
f=next((f for f in d['folders'] if f['id']=='$1'), None)
print(' '.join(m['key'] for m in f['members']) if f else 'NOFOLDER')"; }
first_folder() { layout_json | python3 -c "
import json,sys
d=json.load(sys.stdin)
print(next((o['key'] for o in d['order'] if o['key'].startswith('folder:')), ''))"; }

say "# E8 $(date -Iseconds)"
build_guard
layout_save "$OUT/layout_before.json"
layout_restore "$HERE/../baseline_layout.json" || { say "FAIL could not seed the baseline"; exit 1; }
layout_save "$BASE"

# drag <label> <A> <B> <hold ms over the target>. Screencaps at ~1000 ms (inside the dwell) and ~2600 ms.
drag() {
  local label=$1 A=$2 B=$3 holdms=$4 from to
  ensure_start
  dump "$OUT/${label}_start.xml"
  from=$(tile_center "$OUT/${label}_start.xml" "$A") || { say "FAIL $label: no tile $A"; QA_FAIL=$((QA_FAIL+1)); return 1; }
  to=$(edit_point "$OUT/${label}_start.xml" "$B") || { say "FAIL $label: no tile $B"; QA_FAIL=$((QA_FAIL+1)); return 1; }
  say "--- $label: $A -> $B, holding ~${holdms} ms over it ---"
  down ${from% *} ${from#* }
  sleep 1.1
  glide ${from% *} ${from#* } ${to% *} ${to#* } 8
  # the dwell clock starts when the centre arrives, so the wait is timed from HERE and recorded
  local t0 t1
  t0=$(date +%s%3N)
  sleep 1.0
  adb exec-out screencap -p > "$OUT/${label}_at1000ms.png"      # always: H10 needs the feedback capture
  if [ "$holdms" -ge 2600 ]; then sleep 1.6; adb exec-out screencap -p > "$OUT/${label}_at2600ms.png"; fi
  t1=$(date +%s%3N)
  say "    held $((t1 - t0)) ms over the target before the release (dwell is 2000 ms)"
  if [ "$holdms" -lt 2000 ] && [ $((t1 - t0)) -ge 2000 ]; then
    QA_FAIL=$((QA_FAIL+1)); say "FAIL  $label meant to release INSIDE the dwell but took $((t1 - t0)) ms"
  fi
  if [ "$holdms" -ge 2600 ] && [ $((t1 - t0)) -lt 2000 ]; then
    QA_FAIL=$((QA_FAIL+1)); say "FAIL  $label meant to outlast the dwell but took only $((t1 - t0)) ms"
  fi
  up ${to% *} ${to#* }
  sleep 1.4
  adb exec-out screencap -p > "$OUT/${label}_after.png"
  dump "$OUT/${label}_after.xml"
  adb shell input keyevent KEYCODE_BACK; sleep 1
}

A_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
B_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
UNTOUCHED=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][3]['key'])")
say "A = $A_ID (last), B = $B_ID (first), untouched witness = $UNTOUCHED"
BEFORE_KEYS=$(keys)

# --- (a) release inside the dwell -> folder ---------------------------------------------------------------
WITNESS_BEFORE=$(bounds "$OUT/../E08/e8a_folder_start.xml" "tile:$UNTOUCHED" 2>/dev/null || true)
drag "e8a_folder" "$A_ID" "$B_ID" 800
FID=$(first_folder); FID=${FID#folder:}
check_contains "a folder was created" "folder:" "$(keys)"
if [ -n "$FID" ]; then
  check "the folder holds the target and the dragged tile" "$B_ID $A_ID" "$(members_of "$FID")"
  check "it sits in the target's place" "folder:$FID" "$(keys | cut -d' ' -f1)"
fi
WITNESS_AFTER=$(bounds "$OUT/e8a_folder_after.xml" "tile:$UNTOUCHED")
WITNESS_START=$(bounds "$OUT/e8a_folder_start.xml" "tile:$UNTOUCHED")
check "no other tile moved: $UNTOUCHED bounds" "$WITNESS_START" "$WITNESS_AFTER"

# --- (b) stay past the dwell -> move, no folder ------------------------------------------------------------
layout_restore "$BASE"
drag "e8b_reflow" "$A_ID" "$B_ID" 2600
check "no folder was made" 0 "$(folder_count)"
check "the dragged tile took the target's cell" "$A_ID" "$(keys | cut -d' ' -f1)"
check "every tile is still there" "$(echo "$BEFORE_KEYS" | wc -w)" "$(keys | wc -w)"

# --- (c) and (d): the same two paths against a collapsed folder ---------------------------------------------
layout_restore "$BASE"
drag "e8_makefolder" "$A_ID" "$B_ID" 800
FOLDER=$(first_folder); FID=${FOLDER#folder:}
if [ -z "$FOLDER" ]; then
  QA_FAIL=$((QA_FAIL+1)); say "FAIL could not build a folder for the folder-target paths"
else
  layout_save "$OUT/e8_with_folder.json"
  MEMBERS_BEFORE=$(members_of "$FID")
  C_ID=$(layout_json | python3 -c "
import json,sys
print([o['key'] for o in json.load(sys.stdin)['order'] if not o['key'].startswith('folder:')][-1])")
  drag "e8c_addtofolder" "$C_ID" "$FOLDER" 800
  check "the dragged tile joined the folder" "$MEMBERS_BEFORE $C_ID" "$(members_of "$FID")"
  check "still exactly one folder" 1 "$(folder_count)"

  layout_restore "$OUT/e8_with_folder.json"
  drag "e8d_folderreflow" "$C_ID" "$FOLDER" 2600
  check "past the dwell the folder made room instead" "$MEMBERS_BEFORE" "$(members_of "$FID")"
  check "and the dragged tile took its cell" "$C_ID" "$(keys | cut -d' ' -f1)"

  # --- (e) a folder dragged over a PLAIN tile ---------------------------------------------------------------
  layout_restore "$OUT/e8_with_folder.json"
  D_ID=$(layout_json | python3 -c "
import json,sys
print([o['key'] for o in json.load(sys.stdin)['order'] if not o['key'].startswith('folder:')][0])")
  drag "e8e_nonesting" "$FOLDER" "$D_ID" 2600
  check "the folder survived the move with its members" "$MEMBERS_BEFORE" "$(members_of "$FID")"
  check "no second folder appeared" 1 "$(folder_count)"

  # --- (f) a folder dragged over ANOTHER FOLDER (the clause the gate never ran) ------------------------------
  layout_restore "$OUT/e8_with_folder.json"
  E_ID=$(layout_json | python3 -c "
import json,sys
ks=[o['key'] for o in json.load(sys.stdin)['order'] if not o['key'].startswith('folder:')]
print(ks[0], ks[1])")
  say "--- building a SECOND folder from ${E_ID% *} + ${E_ID#* } ---"
  drag "e8_makefolder2" "${E_ID#* }" "${E_ID% *}" 800
  check "two folders now exist" 2 "$(folder_count)"
  F2=$(layout_json | python3 -c "
import json,sys
print([o['key'] for o in json.load(sys.stdin)['order'] if o['key'].startswith('folder:')][-1])")
  F2ID=${F2#folder:}
  M1=$(members_of "$FID"); M2=$(members_of "$F2ID")
  say "folders: $FOLDER=[$M1]  $F2=[$M2]"
  drag "e8f_folder_on_folder" "$F2" "$FOLDER" 2600
  check "folder A kept exactly its members" "$M1" "$(members_of "$FID")"
  check "folder B kept exactly its members" "$M2" "$(members_of "$F2ID")"
  check "still two folders, neither inside the other" 2 "$(folder_count)"
  check_absent "no folder key inside any folder" "folder:" "$(members_of "$FID") $(members_of "$F2ID")"
fi

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[edit\]|\[layout\]" | tail -60 > "$OUT/e8_diag.txt"
layout_restore "$OUT/layout_before.json" || { QA_FAIL=$((QA_FAIL+1)); say "FAIL the layout did not restore"; }
say "baseline layout restored"
qa_finish
