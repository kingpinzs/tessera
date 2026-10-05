#!/usr/bin/env bash
# Photos dev proof B1 (build task 5): the viewer — it opens from the tile with its motion line, the centre pixel is the
# fixture's colour, a swipe shows the next picture, a double tap zooms, Back returns to the collection at the same
# scroll position, and the chrome's measured geometry (Y2). Restores the media it pushed. Not the gate (E4, E19 are).
. "$(dirname "$0")/pdev.sh"
row_begin B1 "Photos viewer: open, pixel, swipe, double tap, Back, chrome"
media_census
perm_set READ_MEDIA_IMAGES true
push_six
record "fixture image ids (qa-photo-0..5)" "$IDS"
set -- $IDS; ID0="$1"; ID1="$2"
adb shell am force-stop app.tileshell
photos_start; sleep 1
X="$ROW_DIR/collection.xml"; dump_ui "$X"
TOP_BEFORE="$(bounds "$X" "photos_item:$ID0")"
MARK="$(ring_mark)"
tap_node "$X" "photos_item:$ID0"; sleep 2
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the open's motion line" "[motion] viewer_open t0=" "$SLICE"
assert_within "viewer_open settle = 250 ms" 250 "$(motion_field "$SLICE" viewer_open settle)" 17
assert_within "viewer_open maxGapMs <= 33.4" 16.7 "$(motion_field "$SLICE" viewer_open maxGapMs)" 16.7
record "am start to viewer" "tile tapped, viewer up"
record "viewer_open line" "$(echo "$SLICE" | grep -F '[motion] viewer_open' | sed 's/.*\[motion\] //')"
screencap "$ROW_DIR/viewer0.png"
assert_rgb "the centre pixel is qa-photo-0's colour" "220,40,40" "$(px "$ROW_DIR/viewer0.png" 540 1170)" 4
V="$ROW_DIR/viewer.xml"; dump_ui "$V"
# Y2 (px / 3 = epx): header 0 -> 50 epx, bar 48 epx on the nav bar, Share / Edit / Delete / More at 218 / 150 / 82 / 24 from the right.
assert_eq "date header 0 -> 50 epx, full width" "0 0 1080 150" "$(bounds "$V" viewer_header)"
assert_eq "no drawn status bar node" "no" "$(has_node "$V" status_bar)"
assert_eq "the bar is 48 epx tall on the nav bar" "2052 2196" "$(bounds "$V" viewer_bar | awk '{print $2, $4}')"
cx() { bounds "$V" "$1" | awk '{print (1080 - ($1 + $3) / 2) / 3}'; }
assert_eq "Share · Edit · Delete · More centres from the right (epx)" "218 150 82 24" "$(cx viewer_share) $(cx viewer_edit) $(cx viewer_delete) $(cx viewer_more)"
assert_eq "no Favorite button" "no" "$(has_node "$V" viewer_favorite)"
IB="$(bounds "$V" viewer_image)"
assert_eq "the photo is fitted to the width" "0 1080" "$(echo $IB | awk '{print $1, $3}')"
assert_eq "the photo's vertical centre is the screen's (1170 px)" "1170" "$(echo $IB | awk '{print ($2 + $4) / 2}')"
assert_eq "the header's date is today's long date" "$(date +'%A, %B %-d, %Y')" "$(node_text "$V" viewer_date)"
assert_rgb "the header fill is #171717" "23,23,23" "$(px "$ROW_DIR/viewer0.png" 900 75)" 4
assert_rgb "the bar is black" "0,0,0" "$(px "$ROW_DIR/viewer0.png" 100 2120)" 4
# The "..." expansion: 60 epx with labels, the overflow above it.
tap_node "$V" viewer_more; sleep 1
M="$ROW_DIR/menu.xml"; dump_ui "$M"; screencap "$ROW_DIR/menu.png"
assert_eq "the expanded bar is 60 epx" "180" "$(bounds "$M" viewer_bar | awk '{print $4 - $2}')"
assert_eq "the labels" "Share Edit Delete" "$(node_text "$M" viewer_share_label) $(node_text "$M" viewer_edit_label) $(node_text "$M" viewer_delete_label)"
cy() { bounds "$M" "$1" | awk '{print ($2 + $4) / 2 / 3}'; }
record "overflow item centres (epx from the top)" "$(cy viewer_menu_slideshow) $(cy viewer_menu_setas) $(cy viewer_menu_info)"
assert_eq "Slideshow -> Set as pitch is 44 epx" "44" "$(echo "$(cy viewer_menu_slideshow) $(cy viewer_menu_setas)" | awk '{print $2 - $1}')"
assert_eq "the overflow panel is 155.1 epx tall (465 px)" "465" "$(bounds "$M" viewer_menu | awk '{print $4 - $2}')"
assert_rgb "the panel fill is #2B2B2B" "43,43,43" "$(px "$ROW_DIR/menu.png" 900 $(( $(bounds "$M" viewer_menu | cut -d' ' -f2) + 12 )))" 4
adb shell input keyevent KEYCODE_BACK; sleep 1
# The swipe to the next picture.
MARK="$(ring_mark)"
adb shell input swipe 900 1170 180 1170 200; sleep 2
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the swipe's motion line" "[motion] photo_swipe t0=" "$SLICE"
record "photo_swipe line" "$(echo "$SLICE" | grep -F '[motion] photo_swipe' | sed 's/.*\[motion\] //')"
assert_contains "the viewer shows the next picture" "[photosapp] viewer show $ID1" "$SLICE"
screencap "$ROW_DIR/viewer1.png"
assert_rgb "the centre pixel is qa-photo-1's colour" "40,180,80" "$(px "$ROW_DIR/viewer1.png" 540 1170)" 4
# Double tap at the screen centre: the image is wider than the screen.
MARK="$(ring_mark)"
gesture "tap 540 1170; sleep 60; tap 540 1170"; sleep 2
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the zoom line: twice the fitted width" "[photosapp] zoom $ID1 x2.00 width=2160px" "$SLICE"
dump_ui "$ROW_DIR/zoomed.xml"
record "viewer_image bounds while zoomed (the dump clips to the screen)" "$(bounds "$ROW_DIR/zoomed.xml" viewer_image)"
gesture "tap 540 1170; sleep 60; tap 540 1170"; sleep 1
assert_contains "a second double tap returns to the fit" "[photosapp] zoom $ID1 x1.00 width=1080px" "$(ring_since "$MARK" launcher)"
# Back: the collection at the same scroll position.
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK; sleep 2
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the close's motion line" "[motion] viewer_close t0=" "$SLICE"
assert_within "viewer_close settle = 250 ms" 250 "$(motion_field "$SLICE" viewer_close settle)" 17
dump_ui "$X.back"
assert_eq "the viewer is gone" "no" "$(has_node "$X.back" viewer)"
assert_eq "the collection is at the same scroll position" "$TOP_BEFORE" "$(bounds "$X.back" "photos_item:$ID0")"
no_crash 800
ring_save launcher
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
