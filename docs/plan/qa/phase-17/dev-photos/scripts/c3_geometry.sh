#!/usr/bin/env bash
# Photos dev proof C3 (build task 5, the editor): crop to the centre half (320 x 240), rotate (sides swapped), straighten
# the 10-degree line by -10 (the gate's size, the line flat), red-eye (red halved inside the disc, untouched outside),
# and the strip's measured geometry (Y11). The JPEG copy's EXIF is c5_jpeg.sh's. Restores the media. Not the gate (E6, E19 are).
. "$(dirname "$0")/pdev.sh"
row_begin C3 "Photos editor: crop, rotate, straighten, red-eye, the strip"
media_census
perm_set READ_MEDIA_IMAGES true
media_push /sdcard/Pictures/QA-Album qa-photo-1.png qa-line.png qa-redeye.png
scan; sleep 2
P1="$(img_id Pictures/QA-Album/ qa-photo-1.png)"; LINE="$(img_id Pictures/QA-Album/ qa-line.png)"; RED="$(img_id Pictures/QA-Album/ qa-redeye.png)"
record "fixture ids (photo-1, line, redeye)" "$P1 $LINE $RED"
FILES="/sdcard/Pictures/QA-Album/qa-photo-1.png /sdcard/Pictures/QA-Album/qa-line.png /sdcard/Pictures/QA-Album/qa-redeye.png"
MD5_BEFORE="$(orig_md5 $FILES)"
adb shell am force-stop app.tileshell

# The strip (Y11; px / 3 = epx): 48 epx on the nav bar, Crop · Enhance · Rotate · Save · More at 286 / 218 / 150 / 82 / 24 from the right.
open_editor "$P1"
screencap "$ROW_DIR/editor.png"
assert_eq "the strip is 48 epx tall on the nav bar" "2052 2196" "$(bounds "$E" edit_bar | awk '{print $2, $4}')"
cx() { bounds "$E" "$1" | awk '{print (1080 - ($1 + $3) / 2) / 3}'; }
assert_eq "Crop · Enhance · Rotate · Save · More centres from the right (epx)" "286 218 150 82 24" "$(cx edit_crop) $(cx edit_enhance) $(cx edit_rotate) $(cx edit_save) $(cx edit_more)"
assert_rgb "the strip's fill is (15,15,15)" "15,15,15" "$(px "$ROW_DIR/editor.png" 60 2120)" 3
assert_eq "no drawn status bar node" "no" "$(has_node "$E" status_bar)"
etap edit_more
assert_eq "the labels, Save being Save a copy" "Crop Enhance Rotate Save a copy" "$(edump; echo "$(node_text "$E" edit_crop_label) $(node_text "$E" edit_enhance_label) $(node_text "$E" edit_rotate_label) $(node_text "$E" edit_save_label)")"
assert_eq "More holds the four added tools" "Straighten Light Colour Filters Red-eye" "$(for t in straighten light colour filters redeye; do node_text "$E" "edit_tool:${t}_text"; done | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# Crop to the centre half: the handles dragged from the 70 % rectangle to 25 % .. 75 %.
etap edit_crop 2; edump; screencap "$ROW_DIR/crop.png"
IB="$(bounds "$E" edit_image)"; set -- $IB; IW=$(( $3 - $1 )); IH=$(( $4 - $2 ))
record "crop page: the image and the rectangle" "[$IB] [$(bounds "$E" edit_crop_rect)]"
assert_eq "the crop opens at 70 % of the picture" "$(python3 -c "print(round($IW*0.7), round($IH*0.7))")" "$(bounds "$E" edit_crop_rect | awk '{print $3-$1, $4-$2}')"
assert_eq "four handle discs of 18 epx" "4 54" "$(pnodes "$E" edit_crop_handle: | wc -l) $(bounds "$E" edit_crop_handle:tl | awk '{print $3-$1}')"
assert_eq "the crop bar: Aspect ratio · Accept · More from the right (epx)" "150 82 24" "$(cx edit_aspect) $(cx edit_accept) $(cx edit_tool_more)"
hc() { bounds "$E" "edit_crop_handle:$1" | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'; }
DX=$(python3 -c "print(round($IW*0.10))"); DY=$(python3 -c "print(round($IH*0.10))")
set -- $(hc tl); adb shell input swipe "$1" "$2" $(( $1 + DX )) $(( $2 + DY )) 600; sleep 1
edump; set -- $(hc br); adb shell input swipe "$1" "$2" $(( $1 - DX )) $(( $2 - DY )) 600; sleep 1
etap edit_accept; edit_save
assert_contains "crop: the op's line" "[photosapp] edit crop -> content://media/" "$ESLICE"
assert_eq "crop: the copy is 320 x 240" "320 240" "$(copy_field width) $(copy_field height)"
assert_eq "crop: the pulled file is 320 x 240" "320x240" "$(img_size "$(copy_pull crop)")"
assert_eq "crop: the copy is in the original's folder" "Pictures/QA-Album/" "$(copy_field relative_path)"

# Rotate: the sides swap.
open_editor "$P1"
MARK="$(ring_mark)"
etap edit_rotate 2; edit_save
assert_contains "rotate: the op's line" "[photosapp] edit rotate -> content://media/" "$ESLICE"
assert_eq "rotate: width and height are swapped" "480 640" "$(copy_field width) $(copy_field height)"
assert_contains "rotate: its motion line" "[motion] edit_rotate t0=" "$(ring_since "$MARK" "$EDIT_RING")"

# Straighten the line by -10 degrees.
open_editor "$LINE"
open_tool straighten
for _ in 1 2 3 4 5 6 7 8 9 10; do tap_node "$E" edit_slider_minus; sleep 0.4; done
assert_eq "straighten: the slider reads -10" "-10°" "$(edump; node_text "$E" edit_slider_value)"
screencap "$ROW_DIR/straighten.png"
etap edit_accept; edit_save
WANT="$(python3 "$EE" straighten 640 480 -10)"
record "straighten: expected size (edit_expect.py straighten 640 480 -10) / got" "$WANT / $(copy_field width) $(copy_field height)"
assert_contains "straighten: the op's line" "[photosapp] edit straighten -> content://media/" "$ESLICE"
set -- $WANT
assert_within "straighten: the copy's width" "$1" "$(copy_field width)" 1
assert_within "straighten: the copy's height" "$2" "$(copy_field height)" 1
SF="$(copy_pull straighten)"
BAND="$(python3 - "$SF" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
w, h = im.size
mid = h // 2
dark = all(im.getpixel((x, mid))[0] < 80 for x in range(w))
white = all(min(im.getpixel((x, y))) > 250 for y in (mid - 10, mid + 10) for x in range(w))
print("line-flat" if dark and white else "not flat: dark=%s white=%s" % (dark, white))
PY
)"
assert_eq "straighten: the line runs flat (dark across the middle row, white 10 px above and below)" "line-flat" "$BAND"

# Red-eye: a tap on the disc.
open_editor "$RED"
open_tool redeye
set -- $(bounds "$E" edit_image); adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
assert_eq "red-eye: the tap found the disc" "1 fixed" "$(edump; node_text "$E" edit_redeye_hint)"
etap edit_accept; edit_save
assert_contains "red-eye: the op's line" "[photosapp] edit redeye -> content://media/" "$ESLICE"
RF="$(copy_pull redeye)"
record "red-eye: centre / 5 px inside the edge / 30 px outside it" "$(px "$RF" 320 240) / $(px "$RF" 335 240) / $(px "$RF" 370 240)"
assert_eq "red-eye: inside the disc the red channel is at most half" "yes yes" "$(python3 -c "print('yes' if int('$(px "$RF" 320 240)'.split(',')[0]) <= 127 else 'no', 'yes' if int('$(px "$RF" 335 240)'.split(',')[0]) <= 127 else 'no')")"
assert_rgb "red-eye: 30 px outside the disc is unchanged" "128,128,128" "$(px "$RF" 370 240)" 1
assert_rgb "red-eye: a corner is unchanged" "128,128,128" "$(px "$RF" 5 5)" 1

assert_eq "the originals' md5 are unchanged" "$MD5_BEFORE" "$(orig_md5 $FILES)"
no_crash 2000
ring_save launcher; ring_save "$EDIT_RING"
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
