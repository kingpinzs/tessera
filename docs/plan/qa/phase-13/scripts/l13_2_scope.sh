#!/usr/bin/env bash
# L13-2 scope probe (fix planning, 2026-09-25): does the same leak — a modal overlay inside the app-list page whose
# ancestor, Start's pager, still takes a sideways drag — reach past the band's item? Two more cases on the app list's
# page, each a DOWN, a 60-px sideways MOVE, a dump, an UP: (1) the hold band's scrim, off the band; (2) the letter
# jump grid (X8), on its scrim. Runs on the APK already installed.
#   usage: l13_2_scope.sh <label>
# LEAK = the app list's page moved while the finger was down.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin "L13-2-scope-$1" "L13-2 scope on $1: the band's scrim and the jump grid under a sideways drag"
note "repro apk: ${REPRO_APK:-the build of HEAD} (the header's 'apk built' is the repo's own build, not necessarily this one)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"

drag_probe() { # <name> <x> <y>  -> the page's bounds before, with the finger moved, and after UP
  local name="$1" x="$2" y="$3" p0 p1 p2
  dump_ui "$ROW_DIR/$name-open.xml"; p0="$(bounds "$ROW_DIR/$name-open.xml" app_list)"
  adb shell input motionevent DOWN $x $y; sleep 0.6
  adb shell input motionevent MOVE $(( x + 60 )) $y; sleep 0.6
  dump_ui "$ROW_DIR/$name-moved.xml"; p1="$(bounds "$ROW_DIR/$name-moved.xml" app_list)"
  adb shell input motionevent UP $(( x + 60 )) $y; sleep 1.5
  dump_ui "$ROW_DIR/$name-up.xml"; p2="$(bounds "$ROW_DIR/$name-up.xml" app_list)"
  note "$name: app_list open [$p0], finger moved 60 px [$p1], after UP [$p2]"
  record "$name: LEAK (the page moved with the finger down)" "$([ "$p0" != "$p1" ] && echo yes || echo no)"
}

# (1) The hold band's scrim: hold the third row, then drag from 300 px below the band's bottom edge.
show_start 6
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"
ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID"); HX=$(( ($1 + $3) / 2 )); HY=$(( ($2 + $4) / 2 ))
adb shell input swipe $HX $HY $HX $HY 1000; sleep 1.5
dump_ui "$ROW_DIR/band.xml"
assert_eq "the hold band is open" "yes" "$(has_node "$ROW_DIR/band.xml" applist_menu)"
set -- $(bounds "$ROW_DIR/band.xml" applist_menu); SY=$(( $4 + 300 ))
drag_probe band-scrim 300 $SY
record "band-scrim: the band after UP" "$(has_node "$ROW_DIR/band-scrim-up.xml" applist_menu)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# (2) The jump grid: tap the first letter header, then drag from the grid's centre.
show_start 6
to_app_list 3
dump_ui "$ROW_DIR/applist2.xml"
HID="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/applist2.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/applist2.xml" "$HID"); adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5
dump_ui "$ROW_DIR/grid.xml"
assert_eq "the jump grid is open" "yes" "$(has_node "$ROW_DIR/grid.xml" jump_grid)"
set -- $(bounds "$ROW_DIR/grid.xml" jump_grid); GX=$(( ($1 + $3) / 2 )); GY=$(( $4 - 150 ))
drag_probe jump-grid $GX $GY
record "jump-grid: the grid after UP" "$(has_node "$ROW_DIR/jump-grid-up.xml" jump_grid)"
adb shell input keyevent KEYCODE_BACK; sleep 1
show_start 3
row_end
