#!/usr/bin/env bash
# E2 The backdrop, not the element, is blurred (static source, the app list; phase 13 Acceptance E2, T13-12, T13-17,
# T13-18). A second pass at wm size 720x1560 (2 px/epx) checks the radius follows px/epx.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E2 "the app list's backdrop is blurred, its rows are not; the wallpaper on Start stays sharp"

# One pass at the current display size. $1 = W, $2 = H, $3 = radius px, $4 = label.
pass() {
  local W="$1" H="$2" R="$3" tag="$4" lo hi exp
  exp="$(python3 "$P13/edge.py" expected_width "$R")"
  set_pref transparency_effects boolean true
  set_checker "$W" "$H"
  cp "${ROW_DIR}/checker.png" "$ROW_DIR/checker-$tag.png"
  show_start 7
  dump_ui "$ROW_DIR/start-$tag.xml"
  screencap "$ROW_DIR/start-$tag.png"
  local gap
  gap="$(python3 "$P13/dumpq.py" start_gap "$ROW_DIR/start-$tag.xml" | cut -d' ' -f1)"
  read -r SW SX <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/start-$tag.png" "$gap" 20 $(( W - 20 )))"
  note "$tag Start gap y=$gap steepest step at x=$SX"
  assert_within "$tag: the wallpaper on Start is not blurred in place (edge <= 2 px)" 1 "$SW" 1

  local mark
  mark="$(ring_mark)"
  to_app_list 3
  ring_since "$mark" > "$ROW_DIR/slice-applist-show-$tag.txt"   # E12 (T13-22): the show action's own slice
  dump_ui "$ROW_DIR/applist-$tag.xml"
  screencap "$ROW_DIR/applist-$tag.png"
  local page
  page="$(bounds "$ROW_DIR/applist-$tag.xml" app_list)"
  # Compose hands accessibility a covered node's UNCOVERED part, so the backdrop under the list reports only the strip
  # the rows leave open; the page's rectangle (which the backdrop fills) is app_list's. Present, and inside the page.
  local ab
  ab="$(bounds "$ROW_DIR/applist-$tag.xml" acrylic:applist)"
  assert_ne "$tag: acrylic:applist is in the dump" "" "$ab"
  record "$tag: acrylic:applist reported bounds (uncovered part) / app_list" "$ab / $page"
  assert_eq "$tag: acrylic:applist lies inside the page" "yes" "$(python3 -c "
a=list(map(int,'$ab'.split())); p=list(map(int,'$page'.split()))
print('yes' if a[0]>=p[0] and a[1]>=p[1] and a[2]<=p[2] and a[3]<=p[3] else 'no')")"
  local rows y s xa xb
  rows="$(python3 "$P13/dumpq.py" checker_rows "$ROW_DIR/applist-$tag.xml" "$W" "$H")"
  s=$(( W / 4 ))
  xa=$(( 2 * s + s / 2 )); xb=$(( 3 * s + s / 2 ))
  y="$(python3 "$P13/dumpq.py" clear_rows "$ROW_DIR/applist-$tag.xml" "$xa" "$xb" $rows)"
  assert_ne "$tag: a text-free strip on a square-centre row" "" "$y"
  read -r EW PA PB <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/applist-$tag.png" $(( y - 4 )) $(( y + 4 )) "$xa" "$xb")"
  note "$tag strip y=$y x=$xa..$xb width=$EW plateaus=$PA/$PB expected=$exp"
  assert_within "$tag: edge spread = 2.563 sigma(r=$R px) +- 20 %" "$exp" "$EW" "$(python3 -c "print(round($exp*0.2,1))")"
  # The pixels: 0.2 x (the blurred checker), the host oracle from the pulled fixture (column means; T13-2, T13-17).
  local box worst=0 x got want d
  box="$(echo "$page" | tr ' ' ',')"
  while read -r x want _ _; do
    got="$(python3 "$P13/edge.py" patch "$ROW_DIR/applist-$tag.png" "$x" $(( y - 4 )) 1 9 | cut -d' ' -f1)"
    d="$(python3 -c "print(round(abs($got-$want),1))")"
    worst="$(python3 -c "print(max($worst,$d))")"
    echo "$x got=$got want=$want" >> "$ROW_DIR/profile-$tag.txt"
  done < <(python3 "$P13/acrylic_expect.py" static "$ROW_DIR/checker-$tag.png" 0,0,0 "$R" "$box" profile $(( y - 4 )) $(( y + 4 )) "$xa" "$xb" | awk 'NR % 15 == 1')
  assert_within "$tag: the strip's profile = 0.2 x the host-blurred checker (worst |diff|)" 0 "$worst" 3
  # The element: the "A" header's glyph and an app name's text stay sharp in the same capture.
  local hdr row
  hdr="$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/applist-$tag.xml" | grep -m1 '^A|' | cut -d'|' -f2)"
  row="$(python3 "$P13/dumpq.py" text_nodes "$ROW_DIR/applist-$tag.xml" | grep -m1 '^Auxio|' | cut -d'|' -f2)"
  read -r l t r b <<< "$hdr"
  read -r HW _ <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/applist-$tag.png" $(( (t + b) / 2 )) $(( l > 6 ? l - 6 : 0 )) $(( r + 6 )))"
  assert_within "$tag: the A header's glyph edge is sharp" 1 "$HW" 1
  read -r l t r b <<< "$row"
  read -r TW _ <<< "$(python3 "$P13/edge.py" sharpest "$ROW_DIR/applist-$tag.png" $(( (t + b) / 2 )) $(( l - 6 )) $(( r + 6 )))"
  assert_within "$tag: an app name's text edge is sharp" 1 "$TW" 1
  printf '%s\n' "$y" > "$ROW_DIR/strip-$tag.txt"
}

pass 1080 2340 90 fhd

# ---- acrylic off (E1's control: the switch): the same surface, its fallback form
MARK="$(ring_mark)"
set_pref transparency_effects boolean false
show_start 6
to_app_list 3
dump_ui "$ROW_DIR/applist-off.xml"
screencap "$ROW_DIR/applist-off.png"
assert_eq "off: acrylic:applist still present" "yes" "$(has_node "$ROW_DIR/applist-off.xml" acrylic:applist)"
Y="$(cat "$ROW_DIR/strip-fhd.txt")"
read -r W A B <<< "$(python3 "$P13/edge.py" edge "$ROW_DIR/applist-off.png" $(( Y - 4 )) $(( Y + 4 )) 675 945)"
# The unblurred form: 0.2 x the fixture's own pixel at the same place (Crop at scale 1: the fixture row is y + the
# page's crop offset, (fixture height - page height) / 2).
read -r _ PT _ PBOT <<< "$(bounds "$ROW_DIR/applist-off.xml" app_list)"
OFF=$(( (2340 - (PBOT - PT)) / 2 - PT ))
WANT_A="$(python3 -c "from PIL import Image; print(round(0.2*Image.open('$ROW_DIR/checker-fhd.png').convert('L').getpixel((675, $Y + $OFF))))")"
WANT_B="$(python3 -c "from PIL import Image; print(round(0.2*Image.open('$ROW_DIR/checker-fhd.png').convert('L').getpixel((945, $Y + $OFF))))")"
assert_within "off: the edge is sharp" 1 "$W" 1
assert_within "off: x=675 reads 0.2 x the fixture ($WANT_A)" "$WANT_A" "$A" 3
assert_within "off: x=945 reads 0.2 x the fixture ($WANT_B)" "$WANT_B" "$B" 3
set_pref transparency_effects boolean true

# ---- 720 x 1560, 2 px/epx: r = 60 px
ring_save
adb shell wm size 720x1560
sleep 3
pass 720 1560 60 hd
ring_save
adb shell wm size reset
sleep 3
assert_contains "wm size restored" "Physical size: 1080x2340" "$(adb shell wm size)"
assert_absent "wm size override gone" "Override size" "$(adb shell wm size)"

clear_background
show_start 5
row_end
