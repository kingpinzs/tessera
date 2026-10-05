#!/usr/bin/env bash
# Photos dev proof C1 (build task 5, the editor): auto-enhance, light + one step, colour + one step and two filters on
# the flat fixtures — each copy's centre pixel against the phase doc's matrix result (the lead's edit_expect.py), every
# copy a new published row in the original's folder, the originals' md5 unchanged, :photosedit alive during an edit and
# the launcher's pid unchanged. Restores the media. Not the gate (E6 is).
. "$(dirname "$0")/pdev.sh"
row_begin C1 "Photos editor: enhance, light, colour, filters (mono, sepia)"
media_census
perm_set READ_MEDIA_IMAGES true
push_six
set -- $IDS; ID0="$1"; ID1="$2"; ID2="$3"
FILES="/sdcard/DCIM/Camera/qa-photo-0.png /sdcard/DCIM/Camera/qa-photo-1.png /sdcard/DCIM/Camera/qa-photo-2.png"
MD5_BEFORE="$(orig_md5 $FILES)"
adb shell am force-stop app.tileshell
BEFORE="$(img_count)"
photos_start
LPID="$(adb shell pidof app.tileshell | tr -d '\r')"

open_editor "$ID0"
assert_ne ":photosedit runs while the editor is open" "" "$(adb shell pidof app.tileshell:photosedit | tr -d '\r')"
assert_contains "the pending cleanup ran at the process's start" "[photosapp] pending cleanup: " "$(adb shell dumpsys activity service "$EDIT_RING")"
assert_eq "Save a copy is off until something is changed" "false" "$(grep -o 'resource-id="edit_save"[^>]*enabled="[a-z]*"' "$E" | sed 's/.*enabled="//;s/"//')"
etap edit_enhance; edit_save
colour_copy enhance enhance enhance 220,40,40 DCIM/Camera/
assert_contains "the editor says where the copy went" "Saved a copy in DCIM/Camera" "$ESTATUS"
screencap "$ROW_DIR/enhance.png"

open_editor "$ID1"
open_tool light; etap edit_slider_plus
assert_eq "light: the slider reads +1" "+1" "$(edump; node_text "$E" edit_slider_value)"
etap edit_accept; edit_save
colour_copy light light light:+1 40,180,80 DCIM/Camera/

open_editor "$ID2"
open_tool colour; etap edit_slider_plus; etap edit_accept; edit_save
colour_copy colour colour colour:+1 40,90,220 DCIM/Camera/

open_editor "$ID0"
open_tool filters; screencap "$ROW_DIR/filters.png"; etap edit_filter:mono; etap edit_accept; edit_save
colour_copy mono filter:mono filter:mono 220,40,40 DCIM/Camera/

open_editor "$ID1"
open_tool filters; etap edit_filter:sepia; etap edit_accept; edit_save
colour_copy sepia filter:sepia filter:sepia 40,180,80 DCIM/Camera/

assert_eq "five copies: the image count is +5" "$((BEFORE + 5))" "$(img_count)"
assert_eq "the originals' md5 are unchanged" "$MD5_BEFORE" "$(orig_md5 $FILES)"
assert_eq "the originals' rows are unchanged (same ids)" "$ID0 $ID1 $ID2" "$(img_id DCIM/Camera/ qa-photo-0.png) $(img_id DCIM/Camera/ qa-photo-1.png) $(img_id DCIM/Camera/ qa-photo-2.png)"
assert_eq "the launcher's pid is unchanged" "$LPID" "$(adb shell pidof app.tileshell | tr -d '\r')"
assert_eq "no pending row is left" "0" "$(adb shell "content query --uri content://media/external/images/media --projection _id --where 'is_pending=1'" | grep -c '_id=')"
no_crash 1500
ring_save launcher; ring_save "$EDIT_RING"
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
