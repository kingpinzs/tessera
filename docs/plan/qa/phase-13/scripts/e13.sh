#!/usr/bin/env bash
# E13 Static backdrop failure (phase 13 Acceptance E13, T13-10, T13-15): the Start background's file deleted under the
# shell, then a NEW process (force-stop + Home, so no decode of the picture exists anywhere): the new process's slice from
# MARK holds `[fluent] static backdrop failed for <the checker's URI>: <why> (fallback)` and no `static backdrop rebuilt`
# line, and the app list reads the no-image fallback — the solid theme background (0,0,0) +- 2 in E2's strip, no checker
# edge anywhere in it. The fixture's URI is its content://media one (the route re-cut at build, Change Log 2026-09-25).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E13 "the picture's file deleted: the failure line, no rebuilt line, the solid theme background"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
set_checker
note "checker URI: $CHECKER_URI"
show_start 7
to_app_list 3
dump_ui "$ROW_DIR/applist-before.xml"
Y="$(applist_strip "$ROW_DIR/applist-before.xml")"
assert_ne "a text-free strip on a square-centre row" "" "$Y"
screencap "$ROW_DIR/before.png"
read -r W _ _ <<< "$(applist_edge "$ROW_DIR/before.png" "$Y")"
assert_within "before: the checker shows blurred on the app list (control)" 134.5 "$W" 26.9
to_start 2
adb shell rm "$CHECKER_PATH"
assert_eq "the picture's file is gone" "gone" "$(adb shell ls "$CHECKER_PATH" >/dev/null 2>&1 && echo present || echo gone)"
ring_save
MARK="$(ring_mark)"
adb shell am force-stop $PKG; sleep 1
show_start 7
to_app_list 3
ring_since "$MARK" > "$ROW_DIR/slice-new-process.txt"
assert_contains "the new process: static backdrop failed for the checker's URI, (fallback)" "[fluent] static backdrop failed for $CHECKER_URI: " "$(cat "$ROW_DIR/slice-new-process.txt")"
assert_contains "the failure line ends (fallback)" "(fallback)" "$(grep -F 'static backdrop failed' "$ROW_DIR/slice-new-process.txt")"
assert_absent "no static backdrop rebuilt line" "static backdrop rebuilt" "$(cat "$ROW_DIR/slice-new-process.txt")"
dump_ui "$ROW_DIR/applist-after.xml"
assert_eq "the app list shows" "yes" "$(has_node "$ROW_DIR/applist-after.xml" app_list)"
screencap "$ROW_DIR/after.png"
assert_within "after: E2's strip reads the theme background (0,0,0) (worst px over the full width, 9 rows)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/after.png" 0 $(( Y - 4 )) 1080 9 0,0,0)" 2
read -r W A B <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/after.png" $(( Y - 4 )) $(( Y + 4 )) 675 945)"
note "after: edge probe across x=810: width=$W plateaus=$A/$B"
assert_eq "after: no checker edge in the strip (flat: plateaus within 1 level)" yes "$(python3 -c "print('yes' if abs($A-$B) <= 1 else 'no')")"
to_start 2
clear_background
show_start 3
row_end
