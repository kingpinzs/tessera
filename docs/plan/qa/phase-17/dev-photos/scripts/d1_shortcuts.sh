#!/usr/bin/env bash
# Photos dev proof D1 (build task 15): the two static App Shortcuts as the system holds them, and each shortcut's intent
# (VIEW + the "page" extra) opening its pivot with that header selected — cold and into the running app. Changes
# nothing on the device. Not the gate (E23 is).
. "$(dirname "$0")/pdev.sh"
row_begin D1 "Photos App Shortcuts"
SC="$(adb shell dumpsys shortcut | tr -d '\r')"
BLOCK="$(echo "$SC" | grep -A14 -E 'ShortcutInfo \{id=photos_(collection|albums),')"
record "the system's entries" "$(echo "$BLOCK" | grep -oE 'id=photos_[a-z]+, flags=[^,]*,[^}]*rank=[0-9]+' | tr '\n' '|' | cut -c1-300)"
for spec in "photos_collection:0" "photos_albums:1"; do
  id="${spec%%:*}"; rank="${spec#*:}"
  ONE="$(echo "$SC" | grep -A14 -F "ShortcutInfo {id=$id," | head -15)"
  assert_contains "$id is a manifest shortcut" "Man" "$(echo "$ONE" | grep -oE 'flags=0x[0-9a-f]+ \[[^]]*\]' | head -1)"
  assert_contains "$id has rank $rank" "rank=$rank" "$ONE"
  assert_contains "$id belongs to PhotosActivity" "app.tileshell/app.tileshell.photos.PhotosActivity" "$ONE"
done
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
photos_start -a android.intent.action.VIEW --es page albums
X="$ROW_DIR/albums.xml"; dump_ui "$X"
assert_eq "cold, page=albums: Albums is selected" "true false" "$(pnodes "$X" photos_pivot:albums | cut -f3) $(pnodes "$X" photos_pivot:collection | cut -f3)"
assert_eq "the albums list shows" "yes" "$(has_node "$X" photos_albums)"
photos_start -a android.intent.action.VIEW --es page collection
dump_ui "$ROW_DIR/collection.xml"
assert_eq "running, page=collection: Collection is selected" "true false" "$(pnodes "$ROW_DIR/collection.xml" photos_pivot:collection | cut -f3) $(pnodes "$ROW_DIR/collection.xml" photos_pivot:albums | cut -f3)"
photos_start -a android.intent.action.VIEW --es page albums
dump_ui "$X.2"
assert_eq "running, page=albums: Albums is selected" "true" "$(pnodes "$X.2" photos_pivot:albums | cut -f3)"
photos_start -a android.intent.action.VIEW --es page nonsense
dump_ui "$X.3"
assert_eq "an unknown page opens the collection" "true" "$(pnodes "$X.3" photos_pivot:collection | cut -f3)"
SLICE="$(ring_since "$MARK" launcher)"
assert_contains "the open's line names the page" "[photosapp] open page=albums" "$SLICE"
assert_eq "PhotosActivity is one instance (singleTask)" "1" "$(adb shell dumpsys activity activities | grep -c 'Hist.*app.tileshell/.photos.PhotosActivity')"
no_crash 600
ring_save launcher
adb shell am force-stop app.tileshell
ensure_start
row_end
