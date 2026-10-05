#!/usr/bin/env bash
# Photos dev proof B2 (build task 5): Share reaches the system chooser; Delete asks the system's consent — Deny changes
# nothing and writes its line, Accept removes the row; the slideshow writes three lines 5 s apart in collection order,
# each with its motion line. Restores the media it pushed. Not the gate (E5 is).
. "$(dirname "$0")/pdev.sh"
row_begin B2 "Photos actions: share, delete (deny / accept), slideshow"
media_census
perm_set READ_MEDIA_IMAGES true
push_six
set -- $IDS; ID0="$1"; ID1="$2"; ID2="$3"; ID3="$4"
record "fixture image ids (qa-photo-0..5)" "$IDS"
adb shell am force-stop app.tileshell
photos_start; sleep 1
X="$ROW_DIR/collection.xml"; dump_ui "$X"
tap_node "$X" "photos_item:$ID0"; sleep 2
V="$ROW_DIR/viewer.xml"; dump_ui "$V"
# Share: the system chooser.
MARK="$(ring_mark)"
tap_node "$V" viewer_share; sleep 3
record "top activity after Share" "$(top)"
assert_contains "the system chooser is on top" "com.android.intentresolver" "$(top)"
assert_contains "the share line" "[photosapp] share $ID0 -> chooser" "$(ring_since "$MARK" launcher)"
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "Back from the chooser returns to Photos" "app.tileshell/.photos.PhotosActivity" "$(top)"
# Delete, Deny first.
BEFORE="$(img_count)"
MARK="$(ring_mark)"
dump_ui "$V"; tap_node "$V" viewer_delete; sleep 3
C="$ROW_DIR/consent.xml"; dump_ui "$C"
assert_contains "MediaProvider's consent dialog is on top" "com.android.providers.media.module" "$(cat "$C")"
record "the dialog's buttons" "$(pnodes "$C" android:id/button | cut -f1,4 | tr '\t' '=' | xargs)"
tap_node "$C" android:id/button2; sleep 2
assert_eq "Deny: the count is unchanged" "$BEFORE" "$(img_count)"
assert_contains "Deny: the refusal's line" "[photosapp] delete $ID0: refused by user" "$(ring_since "$MARK" launcher)"
dump_ui "$V"
assert_eq "Deny: the viewer still shows the picture" "yes" "$(has_node "$V" viewer_image)"
# Delete again, Accept.
MARK="$(ring_mark)"
tap_node "$V" viewer_delete; sleep 3
dump_ui "$C"; tap_node "$C" android:id/button1; sleep 3
assert_eq "Accept: the count drops by one" "$((BEFORE - 1))" "$(img_count)"
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "Accept: the delete's line" "[photosapp] delete $ID0: deleted" "$SLICE"
assert_contains "Accept: the library is read again without the row" "[photosapp] library: images=$((BEFORE - 1))" "$SLICE"
screencap "$ROW_DIR/after-delete.png"
assert_rgb "the viewer moved to the next picture (qa-photo-1)" "40,180,80" "$(px "$ROW_DIR/after-delete.png" 540 1170)" 4
# Slideshow from the viewer's menu: three steps, 5 s apart, in collection order.
dump_ui "$V"; tap_node "$V" viewer_more; sleep 1
dump_ui "$V.menu"
MARK="$(ring_mark)"
tap_node "$V.menu" viewer_menu_slideshow
sleep 11.5
screencap "$ROW_DIR/slide2.png"
sleep 5
SLICE="$(ring_since "$MARK" launcher)"
LINES="$(echo "$SLICE" | grep -F '[photosapp] slideshow next')"
record "slideshow lines" "$(echo "$LINES" | sed 's/.*wall=//' | xargs)"
assert_eq "three steps, in collection order" "$ID2 $ID3 $5" "$(echo "$LINES" | sed 's/.*slideshow next //' | head -3 | xargs)"
W="$(echo "$LINES" | grep -oE 'wall=[0-9]+' | cut -d= -f2 | head -3 | xargs)"
set -- $W
assert_within "step 1 -> 2 is 5 s" 5000 "$(( $2 - $1 ))" 100
assert_within "step 2 -> 3 is 5 s" 5000 "$(( $3 - $2 ))" 100
STEPS="$(echo "$SLICE" | grep -F '[motion] slideshow_step')"
assert_eq "a motion line per step" "3" "$(echo "$STEPS" | head -3 | grep -c slideshow_step)"
assert_within "slideshow_step settle = 250 ms" 250 "$(echo "$STEPS" | head -1 | grep -oE 'settle=[0-9]+' | cut -d= -f2)" 17
record "slideshow_step lines" "$(echo "$STEPS" | sed 's/.*slideshow_step //' | head -3 | tr '\n' '|')"
assert_rgb "the screen after the second line is that picture's colour (qa-photo-3)" "230,200,40" "$(px "$ROW_DIR/slide2.png" 540 1170)" 4
adb shell input tap 540 600; sleep 1
assert_contains "a tap stops the slideshow" "[photosapp] slideshow stop" "$(ring_since "$MARK" launcher)"
no_crash 800
ring_save launcher
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
