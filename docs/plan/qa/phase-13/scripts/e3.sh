#!/usr/bin/env bash
# E3 The live source keeps the measured fill in its own setup and blurs a bright backdrop (the reminder menu; phase 13
# Acceptance E3, T13-2, T13-17, T13-21). Fixture: reminders_fixture.sh (the tomorrow reminder, and the Whenever
# reminder with the black / white split photo). B is the host oracle over the Reminders page captured AFTER the menu is
# dismissed by a tap outside it (the held row's (63,68,64) asserted in that capture), never before the long-press.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
. "$(dirname "$0")/reminders_fixture.sh"

row_begin E3 "the reminder menu: (40,40,40) over the page it was measured on, the photo's edge blurred under it"
R=90                      # 30 epx at 3 px/epx
TINT=47,45,47             # the surface table's T for (40,40,40) over (14,19,13)
HELD=63,68,64             # R7 §3.1.6: the held row's fill
OUTSIDE="1000 1500"       # a tap on the page's empty area, clear of every row: dismisses the menu

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
THEME="$(adb shell run-as $PKG cat shared_prefs/start_theme.xml 2>/dev/null | grep -o 'name="theme">[A-Z]*' | sed 's/.*>//')"
assert_eq "Dark theme (the theme key; absent = the default, DARK)" "DARK" "${THEME:-DARK}"
show_start 6
reminders_setup
cp "$ROW_DIR/reminders.xml" "$ROW_DIR/page.xml"
screencap "$ROW_DIR/page.png"

# The backdrop layer L: the page's child that holds its background, rows and app bar — the window's width, from the
# top to the app bar's bottom (the dump carries that node; its bounds are asserted, not assumed).
read -r _ _ _ AB <<< "$(bounds "$ROW_DIR/page.xml" reminders_appbar)"
L="0,0,1080,$AB"
assert_contains "the backdrop child's bounds are in the dump" "bounds=\"[0,0][1080,$AB]\"" "$(cat "$ROW_DIR/page.xml")"
note "L = $L"

# Menu text boxes (the labels, from the dump) with a 6-px margin: a patch clear of text.
text_boxes() { # dump
  python3 "$P13/dumpq.py" text_nodes "$1" | grep -E '^(Complete|Delete)\|' | cut -d'|' -f2 \
    | while read -r l t r b; do echo "$(( l - 6 )),$(( t - 6 )),$(( r + 6 )),$(( b + 6 ))"; done
}

# ---- (1) the tomorrow row: the measured fill over the page it was measured on
b="$(bounds "$ROW_DIR/page.xml" "reminder_row:$REM_TOMORROW")"; set -- $b
TX=$(( ($1 + $3) / 2 )); TY=$(( ($2 + $4) / 2 ))
MARK="$(ring_mark)"
adb shell input swipe $TX $TY $TX $TY 1000
sleep 1.5
dump_ui "$ROW_DIR/menu-tomorrow.xml"
screencap "$ROW_DIR/menu-tomorrow.png"
ring_since "$MARK" > "$ROW_DIR/slice-menu-tomorrow.txt"
MB="$(bounds "$ROW_DIR/menu-tomorrow.xml" acrylic:reminder_menu)"
assert_ne "(1) acrylic:reminder_menu is in the dump" "" "$MB"
assert_contains "(1) the show line" "[fluent] reminder_menu source=live tint=($TINT) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-menu-tomorrow.txt")"
adb shell input tap $OUTSIDE
sleep 1.5
screencap "$ROW_DIR/closed-tomorrow.png"
dump_ui "$ROW_DIR/closed-tomorrow.xml"
assert_eq "(1) the tap outside dismissed the menu" "no" "$(has_node "$ROW_DIR/closed-tomorrow.xml" reminder_menu)"
# The held row's fill in the closed capture: right of the row's text, clear of it.
assert_within "(1) closed capture: the held row reads (63,68,64) (worst channel)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/closed-tomorrow.png" 900 $(( TY - 5 )) 10 10 $HELD)" 0
set -- $MB
REGION="$(( $1 + 3 )),$(( $2 + 3 )),$(( $3 - 3 )),$(( $4 - 3 ))"   # inside the 3-px border
# shellcheck disable=SC2046
read -r PX PY PDEV <<< "$(python3 "$P13/acrylic_check.py" flat "$ROW_DIR/closed-tomorrow.png" 14,19,13 $R "$L" "$REGION" $(text_boxes "$ROW_DIR/menu-tomorrow.xml"))"
assert_ne "(1) precondition: a 10x10 patch whose oracle B is (14,19,13) +- 1 at every pixel" "" "${PX:-}"
note "(1) patch ($PX,$PY) 10x10, oracle max |B - Bm| = $PDEV"
read -r MR MG MBL <<< "$(python3 "$P13/acrylic_check.py" patch "$ROW_DIR/menu-tomorrow.png" "$PX" "$PY" 10 10)"
assert_within "(1) interior patch mean R = 40 +- 2" 40 "$MR" 2
assert_within "(1) interior patch mean G = 40 +- 2" 40 "$MG" 2
assert_within "(1) interior patch mean B = 40 +- 2" 40 "$MBL" 2
# The border: opaque (71,76,70), 3 px; the left edge's middle column and the bottom edge's middle row.
set -- $MB
assert_within "(1) border, left edge (worst px of a 1x100 strip)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/menu-tomorrow.png" $(( $1 + 1 )) $(( $4 - 120 )) 1 100 71,76,70)" 2
assert_within "(1) border, bottom edge (worst px of a 100x1 strip)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/menu-tomorrow.png" $(( ($1 + $3) / 2 - 50 )) $(( $4 - 2 )) 100 1 71,76,70)" 2

# ---- (2) the photo row: the menu over the photo's black / white edge
PB="$(bounds "$ROW_DIR/page.xml" "reminder_row_photo:$REM_PHOTO")"; set -- $PB
PL=$1; PT=$2; PR=$3; PBOT=$4
EX=$(( (PL + PR) / 2 ))                     # the split's edge is the photo's horizontal centre
MARK="$(ring_mark)"
adb shell input swipe $EX $(( PBOT - 10 )) $EX $(( PBOT - 10 )) 1000
sleep 1.5
dump_ui "$ROW_DIR/menu-photo.xml"
screencap "$ROW_DIR/menu-photo.png"
ring_since "$MARK" > "$ROW_DIR/slice-menu-photo.txt"
MB="$(bounds "$ROW_DIR/menu-photo.xml" acrylic:reminder_menu)"
assert_ne "(2) acrylic:reminder_menu is in the dump" "" "$MB"
assert_contains "(2) the show line" "[fluent] reminder_menu source=live tint=($TINT) alpha=0.8 blur=30epx" "$(cat "$ROW_DIR/slice-menu-photo.txt")"
set -- $MB; ML=$1; MT=$2; MR_=$3; MBOT=$4
assert_eq "(2) the menu's bounds intersect the photo's" "yes" \
  "$( [ $ML -lt $PR ] && [ $MR_ -gt $PL ] && [ $MT -lt $PBOT ] && [ $MBOT -gt $PT ] && echo yes || echo no)"
note "(2) photo [$PB], menu [$MB], edge x=$EX"
adb shell input tap $OUTSIDE
sleep 1.5
screencap "$ROW_DIR/closed-photo.png"
dump_ui "$ROW_DIR/closed-photo.xml"
assert_eq "(2) the tap outside dismissed the menu" "no" "$(has_node "$ROW_DIR/closed-photo.xml" reminder_menu)"
assert_within "(2) closed capture: the held row reads (63,68,64) (worst channel)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/closed-photo.png" $(( PR + 10 )) $(( PBOT + 20 )) 10 10 $HELD)" 0
# The profile: >= 8 rows inside the overlap below the Delete label and above the bottom border, x = edge +- 200.
read -r _ _ _ DB <<< "$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/menu-photo.xml" | grep -m1 '^Delete|' | cut -d'|' -f2)"
Y0=$(( DB + 8 )); Y1=$(( (MBOT - 4) < (PBOT - 1) ? (MBOT - 4) : (PBOT - 1) ))
assert_eq "(2) the profile has >= 8 rows clear of text inside the overlap" "yes" "$( [ $(( Y1 - Y0 + 1 )) -ge 8 ] && echo yes || echo no)"
read -r WORST AT JUD EXP <<< "$(python3 "$P13/acrylic_check.py" profile "$ROW_DIR/menu-photo.png" "$ROW_DIR/closed-photo.png" $TINT $R "$L" $Y0 $Y1 $(( EX - 200 )) $(( EX + 200 )) 2> "$ROW_DIR/profile-photo.txt")"
note "(2) profile rows $Y0..$Y1, x $(( EX - 200 ))..$(( EX + 200 )); worst at x=$AT judged=($JUD) expected=($EXP)"
assert_within "(2) the edge under the menu = 0.8*(47,45,47) + 0.2*B (worst column-mean |diff|)" 0 "$WORST" 4
read -r UW _ _ <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/menu-photo.png" $Y0 $Y1 $(( EX - 200 )) $(( EX + 200 )))"
record "(2) the step's 10-90 % width under the menu (px)" "$UW"
# The same edge outside the menu, in the same capture: below the menu, inside the photo.
OY=$(( (MBOT + PBOT) / 2 ))
assert_eq "(2) a row of the photo lies below the menu" "yes" "$( [ $OY -gt $(( MBOT + 3 )) ] && [ $OY -lt $(( PBOT - 3 )) ] && echo yes || echo no)"
read -r SW SX <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/menu-photo.png" $OY $(( EX - 40 )) $(( EX + 40 )))"
note "(2) outside the menu: y=$OY steepest step at x=$SX"
assert_within "(2) the same edge outside the menu is sharp (<= 2 px)" 1 "$SW" 1

# ---- (3) acrylic off (E1's control: battery saver, live, no restart): the overlap reads (40,40,40)
battery_saver_on
sleep 1
adb shell input swipe $EX $(( PBOT - 10 )) $EX $(( PBOT - 10 )) 1000
sleep 1.5
dump_ui "$ROW_DIR/menu-photo-off.xml"
screencap "$ROW_DIR/menu-photo-off.png"
ring_since "$BS_MARK" > "$ROW_DIR/slice-saver.txt"
assert_contains "(3) battery saver: acrylic=off reason=battery-saver" "[fluent] acrylic=off reason=battery-saver" "$(cat "$ROW_DIR/slice-saver.txt")"
MB="$(bounds "$ROW_DIR/menu-photo-off.xml" acrylic:reminder_menu)"
assert_eq "(3) the menu opened at the same place" "$(bounds "$ROW_DIR/menu-photo.xml" acrylic:reminder_menu)" "$MB"
assert_within "(3) off: over the photo's black half (worst px, 20x8)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/menu-photo-off.png" $(( EX - 120 )) $Y0 20 8 40,40,40)" 2
assert_within "(3) off: over the photo's white half (worst px, 20x8)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/menu-photo-off.png" $(( EX + 100 )) $Y0 20 8 40,40,40)" 2
assert_within "(3) off: across the edge itself (worst px, 40x8)" 0 \
  "$(python3 "$P13/acrylic_check.py" opaque "$ROW_DIR/menu-photo-off.png" $(( EX - 20 )) $Y0 40 8 40,40,40)" 2
adb shell input tap $OUTSIDE
sleep 1.5
assert_eq "battery saver off: awake" "Awake" "$(battery_saver_off)"
assert_eq "battery saver off: low_power = 0" "0" "$(adb shell settings get global low_power | tr -d '\r')"

# ---- restore
cortana_close
reminders_restore
show_start 3
row_end
