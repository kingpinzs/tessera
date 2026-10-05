#!/usr/bin/env bash
# Photos dev proof C2 (build task 5, the editor): the other four filters against the doc's matrices, and the failure
# line — a tool applied, the original's file removed, then Save: no new row and "[photosapp] edit <tool> failed: <why>".
# Restores the media. Not the gate (E6 is).
. "$(dirname "$0")/pdev.sh"
row_begin C2 "Photos editor: filters warm, cool, vivid, fade; the failed save"
media_census
perm_set READ_MEDIA_IMAGES true
push_six
set -- $IDS; ID0="$1"; ID1="$2"; ID2="$3"
adb shell am force-stop app.tileshell
BEFORE="$(img_count)"
i=0
for spec in "warm:$ID0:220,40,40" "cool:$ID1:40,180,80" "vivid:$ID2:40,90,220" "fade:$ID0:220,40,40"; do
  name="${spec%%:*}"; rest="${spec#*:}"; id="${rest%%:*}"; rgb="${rest#*:}"
  open_editor "$id"
  open_tool filters; etap "edit_filter:$name"; etap edit_accept; edit_save
  colour_copy "$name" "filter:$name" "filter:$name" "$rgb" DCIM/Camera/
  i=$((i + 1))
done
assert_eq "four copies: the image count is +4" "$((BEFORE + 4))" "$(img_count)"
# The failure leg: Save a copy re-reads the original, so a file removed after the tool is applied fails the save.
open_editor "$ID2"
etap edit_enhance
adb shell rm /sdcard/DCIM/Camera/qa-photo-2.png; sleep 1
COUNT_NOW="$(img_count)"
edit_save
record "the failed save's status on screen" "$ESTATUS"
assert_contains "the failure's line in the :photosedit ring" "[photosapp] edit enhance failed: " "$ESLICE"
assert_absent "no success line" "[photosapp] edit enhance -> " "$ESLICE"
assert_eq "no new row" "$COUNT_NOW" "$(img_count)"
assert_contains "the screen says the copy was not saved" "Couldn't save a copy" "$ESTATUS"
assert_eq "no pending row is left" "0" "$(adb shell "content query --uri content://media/external/images/media --projection _id --where 'is_pending=1'" | grep -c '_id=')"
no_crash 1500
ring_save launcher; ring_save "$EDIT_RING"
adb shell am force-stop app.tileshell
media_clean
perm_restore
ensure_start
row_end
