#!/usr/bin/env bash
# Photos dev proof A2 (build task 4): the three access states — denied with its text naming the Setup checklist and the
# grant made in place through Android's real dialog, partial with its link to the selected-photos picker, granted.
# Restores every permission it changed. Not the gate (E3 is).
. "$(dirname "$0")/pdev.sh"
row_begin A2 "Photos access states and the in-place grant"
perm_set READ_MEDIA_VIDEO false   # the natural denied state: nothing of the "Photos and videos" group held
perm_set READ_MEDIA_IMAGES false
perm_set READ_MEDIA_VISUAL_USER_SELECTED false
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
photos_start; sleep 1
X="$ROW_DIR/denied.xml"; dump_ui "$X"
assert_eq "Photos' page" "true" "$(pnodes "$X" photos_pivot:collection | cut -f3)"
assert_contains "the denied text names the Setup checklist" "Setup checklist" "$(node_text "$X" photos_denied)"
assert_contains "the denied text says it cannot read the pictures" "can't read the pictures" "$(node_text "$X" photos_denied)"
assert_eq "the grant is offered where the state is" "Allow access" "$(node_text "$X" photos_grant)"
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the denied line" "[photosapp] access=DENIED" "$SLICE"
assert_contains "the library line says DENIED" "[photosapp] library: images=0 videos=0 access=DENIED" "$SLICE"
screencap "$ROW_DIR/denied.png"
# The grant in place: Android's own dialog.
MARK="$(ring_mark)"
tap_node "$X" photos_grant; sleep 2
record "the dialog's activity" "$(top)"
P="$ROW_DIR/dialog.xml"; dump_ui "$P"
assert_contains "Android's permission dialog is on top" "com.android.permissioncontroller" "$(cat "$P")"
record "the dialog's buttons" "$(pnodes "$P" com.android.permissioncontroller:id/permission_ | cut -f1,4 | tr '\t' '=' | xargs)"
tap_node "$P" com.android.permissioncontroller:id/permission_allow_all_button; sleep 3
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the grant's line" "[photosapp] permission request: access=GRANTED" "$SLICE"
assert_contains "the tile's feed restarted after the in-place grant" "[photos] refresh (start)" "$SLICE"
assert_contains "the library is read again" "access=GRANTED" "$(echo "$SLICE" | grep -F '[photosapp] library:')"
dump_ui "$X.granted"
assert_eq "the denied text is gone" "no" "$(has_node "$X.granted" photos_denied)"
assert_ne "tiles show" "0" "$(pnodes "$X.granted" photos_item: | wc -l)"
# Partial: the selected-photos grant with nothing selected.
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_IMAGES
adb shell pm revoke app.tileshell android.permission.READ_MEDIA_VIDEO
adb shell pm grant app.tileshell android.permission.READ_MEDIA_VISUAL_USER_SELECTED
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
photos_start; sleep 1
Y="$ROW_DIR/partial.xml"; dump_ui "$Y"
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the library line says PARTIAL with no image" "[photosapp] library: images=0 videos=" "$SLICE"
assert_contains "the partial line" "[photosapp] access=PARTIAL" "$SLICE"
assert_contains "the partial text names the Setup checklist" "Setup checklist" "$(node_text "$Y" photos_partial)"
assert_eq "the partial state's link" "Select photos" "$(node_text "$Y" photos_grant)"
screencap "$ROW_DIR/partial.png"
tap_node "$Y" photos_grant; sleep 3
record "what the partial link opens" "$(top)"
dump_ui "$ROW_DIR/picker.xml"
record "the picker's package" "$(grep -oE 'package="[^"]*"' "$ROW_DIR/picker.xml" | sort | uniq -c | sort -rn | head -1 | xargs)"
assert_ne "something of Android's is over Photos" "app.tileshell/.photos.PhotosActivity" "$(top)"
screencap "$ROW_DIR/picker.png"
adb shell input keyevent KEYCODE_BACK; sleep 1
no_crash 600
ring_save launcher
adb shell am force-stop app.tileshell
perm_restore
ensure_start
row_end
