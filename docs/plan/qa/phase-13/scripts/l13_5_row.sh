#!/usr/bin/env bash
# L13-5 regression row (INDEX ledger; review/2026-09-26-L13-345-fix-plan.md): an overlay item does not run when its
# overlay is removed under a held finger. Before the fix, overlayItem read the synthetic, consumed "up" Compose sends
# when a node leaves as a lift on the item, so holding the band's "Pin to Start" and pressing Back pinned the app before
# the finger lifted (qa/phase-13/L13-3-investigation/J-item-cancel/). The other half: the same press with no Back still
# pins on the lift, so the item still works.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$QA/../phase-02/scripts/layout.sh"

row_begin L13_5 "an overlay item does not run when its overlay is removed under the finger"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
clear_background
FIX=app.tileshell.testclient.a
MARK="$(ring_mark)"
layout_restore "$QA/../phase-02/baseline_layout.json"
assert_absent "phase 02's baseline: no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
assert_eq "the fixture is not on Start" "0" "$(layout_json | grep -c "$FIX")"

open_band() { # tag -> PX PY of the band's Pin to Start item
  show_start 5
  to_app_list 3
  dump_ui "$ROW_DIR/$1-list.xml"
  [ "$(has_node "$ROW_DIR/$1-list.xml" "applist_row:$FIX")" = yes ] || scroll_to_node "$ROW_DIR/$1-list.xml" "applist_row:$FIX" 12
  set -- $(bounds "$ROW_DIR/$1-list.xml" "applist_row:$FIX")
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 850
  sleep 0.8
  dump_ui "$ROW_DIR/$1-band.xml"
  set -- $(bounds "$ROW_DIR/$1-band.xml" applist_menu_pin)
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}

# (1) held on Pin to Start, Back 0.3 s in, the finger lifts 0.9 s after the band left
read -r PX PY <<< "$(open_band held)"
assert_ne "(1) the band's Pin to Start item is on screen" "" "${PX:-}"
MARK="$(ring_mark)"
adb shell "echo D \$(date +%s%3N); input swipe $PX $PY $PX $PY 1200 & sleep 0.3; echo K \$(date +%s%3N); input keyevent 4; wait; echo U \$(date +%s%3N)" | tr -d '\r' > "$ROW_DIR/held-ts.txt"
sleep 1.5
ring_since "$MARK" > "$ROW_DIR/held-slice.txt"
note "(1) $(tr '\n' ' ' < "$ROW_DIR/held-ts.txt")"
assert_absent "(1) the band removed under the held finger runs nothing (no pin line)" "pin to Start" "$(cat "$ROW_DIR/held-slice.txt")"
assert_eq "(1) the fixture is still not on Start" "0" "$(layout_json | grep -c "$FIX")"

# (2) the other half: the same press, no Back — Pin runs on the lift. The baseline goes back first, so a pin left by (1)
# (on a build with the defect) cannot make (2) read "already on Start" (the before-run, L13_5-before-ee4bf960/).
layout_restore "$QA/../phase-02/baseline_layout.json"
assert_eq "(2) the fixture is not on Start before the press" "0" "$(layout_json | grep -c "$FIX")"
read -r PX PY <<< "$(open_band lift)"
assert_ne "(2) the band's Pin to Start item is on screen" "" "${PX:-}"
MARK="$(ring_mark)"
adb shell "input swipe $PX $PY $PX $PY 1200"
sleep 1.5
ring_since "$MARK" > "$ROW_DIR/lift-slice.txt"
assert_contains "(2) a lift on the item runs it: the pin line says pinned" "-> pinned" "$(grep -F "pin to Start $FIX" "$ROW_DIR/lift-slice.txt")"
assert_eq "(2) the fixture's tile is on Start" "1" "$(layout_json | grep -o "$FIX" | wc -l | tr -d ' ')"

MARK="$(ring_mark)"
layout_restore "$QA/../phase-02/baseline_layout.json"
assert_absent "restore: phase 02's baseline, no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
assert_eq "restore: the fixture's tile is gone" "0" "$(layout_json | grep -c "$FIX")"
show_start 3
row_end
