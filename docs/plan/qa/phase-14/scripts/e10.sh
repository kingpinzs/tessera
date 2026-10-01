#!/usr/bin/env bash
# Phase 14 E10 — edit mode and the pivot: edit mode locks the pivot (a pan opens nothing); a hold on a pod does nothing
# (no edit discs, no quick-action burst, no menu); phase 02 E3's folder-name box, opened and dismissed, never swings the
# pivot to either neighbour (r3 V21: the slice holds NO page=APP_LIST / page=POD_BAY line at all).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
P02="$(cd "$HERE/../../phase-02/scripts" && pwd)"
# gestures.sh first (it brings phase 01's ui.sh), then the floor, so lib.sh's own bounds / has_node are the ones used.
. "$P02/gestures.sh"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E10 "edit mode locks the pivot; a hold on a pod does nothing; the folder-name box never swings the pivot"
seed_baseline

# What "dump unchanged" compares: the grid's and the pager's structure — tiles, their dims and discs, the pages, the pods,
# folders, the burst — by id and bounds. A live tile's own face content (weather_sky:*, a flipping face) is left out: it
# changes on the tile's timer whatever the finger does (run 2: weather_sky:clear came and went between two dumps).
ids_bounds() { # dump.xml -> "id bounds" lines, sorted
  python3 - "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
keep = re.compile(r'^(tile:|dim:|edit_disc:|start_page$|pod_bay|app_list$|acrylic:|pod:|pod_header:|pod_row:|pod_empty:|folder_|quick_burst)')
out = []
for n in re.finditer(r'<node[^>]*>', xml):
    s = n.group(0)
    i = re.search(r'resource-id="([^"]+)"', s); b = re.search(r'bounds="([^"]+)"', s)
    if i and keep.match(i.group(1)): out.append(f"{i.group(1)} {b.group(1) if b else ''}")
print("\n".join(sorted(out)))
PY
}

# (1) Edit mode: a hold on a Start tile, then a pan.
ensure_start
dump_ui "$ROW_DIR/01-start.xml"
TILE="$(grep -oE 'resource-id="tile:[^"]+"' "$ROW_DIR/01-start.xml" | head -1 | sed 's/resource-id="tile://; s/"$//')"
note "held tile: $TILE"
XY="$(center "$ROW_DIR/01-start.xml" "tile:$TILE")"
enter_edit ${XY% *} ${XY#* }
# The hold also opens phase 11's quick-action burst; Back closes the burst only and edit mode stays (phase 11, R6
# §4.1.7) — the L12-1 pattern: otherwise the pan's own touch closes the burst and the dump changes for that reason.
dump_ui "$ROW_DIR/02a-burst.xml"
note "after the hold: quick_burst=$(has_node "$ROW_DIR/02a-burst.xml" quick_burst)"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/02-edit.xml"
assert_eq "the burst is closed" "no" "$(has_node "$ROW_DIR/02-edit.xml" quick_burst)"
assert_ne "edit mode is on (edit discs in the dump)" "0" "$(grep -c 'resource-id="edit_disc:' "$ROW_DIR/02-edit.xml")"
ids_bounds "$ROW_DIR/02-edit.xml" > "$ROW_DIR/02-edit.ids"
MARK="$(ring_mark)"
swipe_right 2
dump_ui "$ROW_DIR/03-edit-after-pan.xml"
ids_bounds "$ROW_DIR/03-edit-after-pan.xml" > "$ROW_DIR/03-edit-after-pan.ids"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/03-slice.txt"
assert_eq "pan in edit mode: no pod_bay" "no" "$(has_node "$ROW_DIR/03-edit-after-pan.xml" pod_bay)"
assert_eq "pan in edit mode: dump unchanged (ids and bounds)" "same" "$(cmp -s "$ROW_DIR/02-edit.ids" "$ROW_DIR/03-edit-after-pan.ids" && echo same || echo "differs: $(diff "$ROW_DIR/02-edit.ids" "$ROW_DIR/03-edit-after-pan.ids" | head -4 | tr '\n' ' ')")"
absent_in "pan in edit mode: no [podbay] opened" "[podbay] opened" "$s"
absent_in "pan in edit mode: no [start] page=POD_BAY" "[start] page=POD_BAY" "$s"
adb shell input keyevent KEYCODE_BACK   # exit edit mode (R6 §4.1.7)
sleep 1.5
dump_ui "$ROW_DIR/04-exited.xml"
assert_eq "edit mode exited" "0" "$(grep -c 'resource-id="edit_disc:' "$ROW_DIR/04-exited.xml")"

# (2) A hold on a pod does nothing.
swipe_right
dump_ui "$ROW_DIR/05-podbay.xml"
assert_eq "on the pod bay" "yes" "$(has_node "$ROW_DIR/05-podbay.xml" pod_bay)"
ids_bounds "$ROW_DIR/05-podbay.xml" > "$ROW_DIR/05-podbay.ids"
read -r x1 y1 x2 y2 <<< "$(bounds "$ROW_DIR/05-podbay.xml" pod:weather)"
HX=$(( (x1 + x2) / 2 )); HY=$(( (y1 + y2) / 2 ))
note "hold on pod:weather at $HX,$HY for 1000 ms"
MARK="$(ring_mark)"
adb shell input swipe $HX $HY $HX $HY 1000
sleep 1.5
dump_ui "$ROW_DIR/06-after-hold.xml"
ids_bounds "$ROW_DIR/06-after-hold.xml" > "$ROW_DIR/06-after-hold.ids"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/06-slice.txt"
assert_eq "hold on a pod: dump unchanged (ids and bounds)" "same" "$(cmp -s "$ROW_DIR/05-podbay.ids" "$ROW_DIR/06-after-hold.ids" && echo same || echo "differs: $(diff "$ROW_DIR/05-podbay.ids" "$ROW_DIR/06-after-hold.ids" | head -4 | tr '\n' ' ')")"
assert_eq "hold on a pod: no edit_disc" "0" "$(grep -c 'resource-id="edit_disc:' "$ROW_DIR/06-after-hold.xml")"
assert_eq "hold on a pod: no quick_burst" "no" "$(has_node "$ROW_DIR/06-after-hold.xml" quick_burst)"
assert_eq "hold on a pod: no menu (no applist_menu, no *_menu node)" "0" "$(grep -cE 'resource-id="[a-z_]*menu[a-z_:]*"' "$ROW_DIR/06-after-hold.xml")"
assert_eq "hold on a pod: nothing launched" "app.tileshell/.StartActivity" "$(top_activity)"
adb shell input keyevent KEYCODE_BACK
sleep 1.5

# (3) Phase 02 E3's folder-name step: a folder made by one gesture (the last tile dropped on the first), its "Name folder"
#     placeholder opens the name box, which is dismissed; the pivot stays on Start.
ensure_start
A_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][-1]['key'])")
B_ID=$(layout_json | python3 -c "import json,sys; print(json.load(sys.stdin)['order'][0]['key'])")
dump_ui "$ROW_DIR/07-pre.xml"
FROM=$(center "$ROW_DIR/07-pre.xml" "tile:$A_ID"); TO=$(edit_point "$ROW_DIR/07-pre.xml" "$B_ID")
note "folder: $A_ID dropped on $B_ID ($FROM -> $TO)"
down ${FROM% *} ${FROM#* }; sleep 1.1
glide ${FROM% *} ${FROM#* } ${TO% *} ${TO#* } 8
sleep 0.8
up ${TO% *} ${TO#* }; sleep 1.5
dump_ui "$ROW_DIR/08-folder.xml"
FID=$(grep -oE 'resource-id="folder_name_placeholder:[^"]+"' "$ROW_DIR/08-folder.xml" | head -1 | sed 's/.*placeholder://; s/"$//')
assert_ne "a folder with a Name folder placeholder" "" "$FID"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/08-folder.xml" "folder_name_placeholder:$FID"
sleep 1.5
dump_ui "$ROW_DIR/09-namebox.xml"
assert_eq "the name box opened" "yes" "$(has_node "$ROW_DIR/09-namebox.xml" folder_name_box)"
screencap "$ROW_DIR/09-namebox.png"
adb shell input keyevent KEYCODE_BACK   # the IME
sleep 1
dump_ui "$ROW_DIR/10-after-back1.xml"
if [ "$(has_node "$ROW_DIR/10-after-back1.xml" folder_name_box)" = yes ]; then
  adb shell input keyevent KEYCODE_BACK   # the box
  sleep 1.5
fi
dump_ui "$ROW_DIR/11-dismissed.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/11-slice.txt"
assert_eq "name box dismissed" "no" "$(has_node "$ROW_DIR/11-dismissed.xml" folder_name_box)"
assert_eq "the pivot stayed on Start: start_page" "yes" "$(has_node "$ROW_DIR/11-dismissed.xml" start_page)"
assert_eq "the pivot stayed on Start: no pod_bay" "no" "$(has_node "$ROW_DIR/11-dismissed.xml" pod_bay)"
assert_eq "the pivot stayed on Start: no app_list" "no" "$(has_node "$ROW_DIR/11-dismissed.xml" app_list)"
absent_in "no [start] page=APP_LIST in the slice" "[start] page=APP_LIST" "$s"
absent_in "no [start] page=POD_BAY in the slice" "[start] page=POD_BAY" "$s"

restore_device_layout
row_end
