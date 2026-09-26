#!/usr/bin/env bash
# L13-2 repro (found by phase 13 E7(d)): with the app list's hold band open (phase 02's H21 band), a finger that goes
# down on the band's item and moves 60 px sideways drags Start's pivot under the band — the band is modal, but the
# pager (its ancestor) still takes the drag. Runs on the APK already installed; the header records which.
#   usage: l13_2_repro.sh <label>
# Defect PRESENT = the app list's page moved while the finger was down, and the pivot logged a drag's settle.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin "L13-2-$1" "L13-2 repro on $1: a sideways move on the open hold band's item drags the pivot under it"
note "repro apk: ${REPRO_APK:-the build of HEAD} (the header's 'apk built' is the repo's own build, not necessarily this one)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
show_start 6
to_app_list 3
dump_ui "$ROW_DIR/applist.xml"
ROWID="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
set -- $(bounds "$ROW_DIR/applist.xml" "$ROWID"); HX=$(( ($1 + $3) / 2 )); HY=$(( ($2 + $4) / 2 ))
adb shell input swipe $HX $HY $HX $HY 1000
sleep 1.5
dump_ui "$ROW_DIR/band.xml"
assert_eq "the hold band is open" "yes" "$(has_node "$ROW_DIR/band.xml" applist_menu_pin)"
set -- $(bounds "$ROW_DIR/band.xml" applist_menu_pin); TX=$(( ($1 + $3) / 2 )); TY=$(( ($2 + $4) / 2 ))
P0="$(bounds "$ROW_DIR/band.xml" app_list)"
screencap "$ROW_DIR/U.png"
MARK="$(ring_mark)"
adb shell input motionevent DOWN $TX $TY; sleep 0.6
adb shell input motionevent MOVE $(( TX + 60 )) $TY; sleep 0.6
screencap "$ROW_DIR/M.png"
dump_ui "$ROW_DIR/moved.xml"
P1="$(bounds "$ROW_DIR/moved.xml" app_list)"
adb shell input motionevent UP $(( TX + 60 )) $TY; sleep 1.5
ring_since "$MARK" > "$ROW_DIR/slice.txt"
note "app_list before [$P0], with the finger moved 60 px [$P1]"
# The defect's two signs: the page moved with the finger, and the pager logged a drag's settle.
assert_ne "DEFECT: the app list's page moved while the band was open (bounds changed)" "$P0" "$P1"
# (the [motion] pivot line is phase 13's ADD, so a pre-13 APK cannot carry it: recorded, not asserted)
record "the pivot's [motion] line after UP (phase 13's builds only)" "$(grep -F '[motion] pivot' "$ROW_DIR/slice.txt" | tail -1 | sed 's/.*\[motion\] //')"
adb shell input keyevent KEYCODE_BACK; sleep 1
show_start 3
row_end
