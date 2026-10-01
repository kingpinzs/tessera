#!/usr/bin/env bash
# Phase 14 E14 — the pod bay's backdrop (T14-11): phase 13 E2's method on the pod bay, with phase 13's checkerboard
# fixture as the Start background (its prefs route). The strip is phase 13 E2's (r3 V14): a checker square-centre row
# clear of every text node in x 675..945 (scripts/pod_dumpq.py = dumpq.py with the pod bay as the page), taken with no
# media session. The box for acrylic_expect.py static is pod_bay's bounds: the edge spread is the blurred width
# (2.563 sigma, r = 30 epx = 90 px) +- 20 %, the pixels 0.2 x (blurred checker) +- 3; a pod header's and a pod row's text
# edges are sharp (<= 2 px); acrylic:pod_bay is in the dump with the page's bounds; the slice from a MARK before the swipe
# holds the [fluent] pod_bay show line. Shared key (A-N4): after that line, Start -> app list -> Start -> pod bay -> Start
# -> app list -> Start rebuilds nothing. Acrylic off: sharp, 0.2 x the fixture +- 3, acrylic:pod_bay still present.
# Restore the switch and the background.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"
. "$QA/../phase-13/scripts/p13.sh"

row_begin E14 "the pod bay's backdrop is the blurred static background; its text is sharp; the shared key; acrylic off"
restore() {
  set_pref transparency_effects boolean true
  clear_background
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  sleep 4
}
trap restore EXIT
W=1080; H=2340; R=90
EXP="$(python3 "$P13/edge.py" expected_width "$R")"
SHOW="[fluent] pod_bay source=static tint=(0,0,0) alpha=0.8 blur=30epx"

set_pref transparency_effects boolean true
set_checker "$W" "$H"
show_start 7
ensure_start
MARK="$(ring_mark)"
swipe_right 3
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/slice-show.txt"
assert_contains "the show line" "$SHOW" "$s"
dump_ui "$ROW_DIR/podbay-on.xml"; screencap "$ROW_DIR/podbay-on.png"
assert_eq "the pod bay" "yes" "$(has_node "$ROW_DIR/podbay-on.xml" pod_bay)"
assert_eq "no media session: Now playing is empty" "yes" "$(has_node "$ROW_DIR/podbay-on.xml" pod_empty:nowplaying)"
PAGE="$(bounds "$ROW_DIR/podbay-on.xml" pod_bay)"
assert_eq "acrylic:pod_bay has the page's bounds" "$PAGE" "$(bounds "$ROW_DIR/podbay-on.xml" acrylic:pod_bay)"
rows="$(python3 "$HERE/pod_dumpq.py" checker_rows "$ROW_DIR/podbay-on.xml" "$W" "$H")"
Y="$(python3 "$HERE/pod_dumpq.py" clear_rows "$ROW_DIR/podbay-on.xml" 675 945 $rows)"
note "checker rows: $rows; strip y=$Y"
assert_ne "a text-free strip on a square-centre row" "" "$Y"
read -r EW PA PB <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/podbay-on.png" $(( Y - 4 )) $(( Y + 4 )) 675 945)"
note "strip width=$EW plateaus=$PA/$PB expected=$EXP"
assert_within "edge spread = 2.563 sigma(r=$R px) +- 20 %" "$EXP" "$EW" "$(python3 -c "print(round($EXP*0.2,1))")"
box="$(echo "$PAGE" | tr ' ' ',')"
worst=0
: > "$ROW_DIR/profile.txt"
while read -r x want _ _; do
  got="$(python3 "$P13/edge.py" patch "$ROW_DIR/podbay-on.png" "$x" $(( Y - 4 )) 1 9 | cut -d' ' -f1)"
  d="$(python3 -c "print(round(abs($got-$want),1))")"
  worst="$(python3 -c "print(max($worst,$d))")"
  echo "$x got=$got want=$want" >> "$ROW_DIR/profile.txt"
done < <(python3 "$P13/acrylic_expect.py" static "$ROW_DIR/checker.png" 0,0,0 "$R" "$box" profile $(( Y - 4 )) $(( Y + 4 )) 675 945 | awk 'NR % 15 == 1')
assert_within "the strip's profile = 0.2 x the host-blurred checker (worst |diff|)" 0 "$worst" 3
# The element: a pod header's glyphs and a pod row's text stay sharp in the same capture.
read -r l t r b <<< "$(bounds "$ROW_DIR/podbay-on.xml" pod_header:agenda)"
read -r HW _ <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/podbay-on.png" $(( (t + b) / 2 )) $(( l > 6 ? l - 6 : 0 )) $(( r + 6 )))"
assert_within "a pod header's text edge is sharp (<= 2 px)" 1 "$HW" 1
read -r l t r b <<< "$(bounds "$ROW_DIR/podbay-on.xml" pod_row:weather:0)"
read -r RW _ <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/podbay-on.png" $(( (t + b) / 2 )) $(( l > 6 ? l - 6 : 0 )) $(( r + 6 )))"
assert_within "a pod row's text edge is sharp (<= 2 px)" 1 "$RW" 1

# ---- the shared key: no rebuild across the pages
ring_save
MK="$(ring_mark)"
swipe_left 2; swipe_left 2; swipe_right 2; swipe_right 2; swipe_left 2; swipe_left 2; swipe_right 2
s="$(ring_since "$MK")"; printf '%s\n' "$s" > "$ROW_DIR/slice-shared-key.txt"
note "pages: $(pages_in "$s")"
absent_in "the shared key: no static backdrop rebuilt" "[fluent] static backdrop rebuilt for" "$s"

# ---- acrylic off
ring_save
set_pref transparency_effects boolean false
show_start 6
ensure_start
swipe_right 3
dump_ui "$ROW_DIR/podbay-off.xml"; screencap "$ROW_DIR/podbay-off.png"
assert_eq "off: the pod bay" "yes" "$(has_node "$ROW_DIR/podbay-off.xml" pod_bay)"
assert_eq "off: acrylic:pod_bay still present" "yes" "$(has_node "$ROW_DIR/podbay-off.xml" acrylic:pod_bay)"
read -r OW OA OB <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/podbay-off.png" $(( Y - 4 )) $(( Y + 4 )) 675 945)"
read -r _ PT _ PBOT <<< "$(bounds "$ROW_DIR/podbay-off.xml" pod_bay)"
OFF=$(( (H - (PBOT - PT)) / 2 - PT ))
WANT_A="$(python3 -c "from PIL import Image; print(round(0.2*Image.open('$ROW_DIR/checker.png').convert('L').getpixel((675, $Y + $OFF))))")"
WANT_B="$(python3 -c "from PIL import Image; print(round(0.2*Image.open('$ROW_DIR/checker.png').convert('L').getpixel((945, $Y + $OFF))))")"
assert_within "off: the edge is sharp" 1 "$OW" 1
assert_within "off: x=675 reads 0.2 x the fixture ($WANT_A)" "$WANT_A" "$OA" 3
assert_within "off: x=945 reads 0.2 x the fixture ($WANT_B)" "$WANT_B" "$OB" 3

ring_save
restore
trap - EXIT
ensure_start
row_end
