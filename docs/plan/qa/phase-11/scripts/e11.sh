#!/usr/bin/env bash
# E11 — the control rows (testability 29): the app list's and Music's holds keep hold-and-release = menu on the
# same Edit.HOLD_MS, and neither opens a burst.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E11 "the app list's and Music's hold menus are unchanged; no burst there"
seed_fixtures
restore baseline_layout.json
ensure_start_page; adb shell input swipe 900 1200 150 1200 250; sleep 2
qdump "$ROW_DIR/applist.xml"
AROW="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/applist.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
note "app list row $AROW"
read -r X Y <<< "$(center "$ROW_DIR/applist.xml" "$AROW")"
MARK="$(ring_mark)"
adb shell input swipe "$X" "$Y" "$X" "$Y" 1200; sleep 1.2
qdump "$ROW_DIR/applist-menu.xml"
assert_eq "app list: applist_menu present" yes "$(has_node "$ROW_DIR/applist-menu.xml" applist_menu)"
assert_eq "app list: no quick_burst" no "$(has_node "$ROW_DIR/applist-menu.xml" quick_burst)"
assert_absent "app list: no [quick] line" "[quick]" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -W -n app.tileshell/.music.MusicActivity --es pivot SONGS >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/songs.xml"
SONG="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
read -r X Y <<< "$(center "$ROW_DIR/songs.xml" "$SONG")"
MARK="$(ring_mark)"
adb shell input swipe "$X" "$Y" "$X" "$Y" 1200; sleep 1.2
qdump "$ROW_DIR/music-menu.xml"
assert_eq "Music: music_menu present" yes "$(has_node "$ROW_DIR/music-menu.xml" music_menu)"
assert_absent "Music: no [quick] line (MusicActivity shares the launcher's ring)" "[quick]" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 1
c6
row_end
