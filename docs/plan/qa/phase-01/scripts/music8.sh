#!/usr/bin/env bash
# PHASE 10 BUILD TASK 8 — playlists: create, rename, reorder, delete, and the two verbs those imply
# (add a track, take one out). Q7's list, on the device, against a store that is this build's own.
#
# The row is bracketed on PERSISTENCE, which is the whole reason the store exists: the playlist is
# made, worked on, and then the shell is FORCE-STOPPED and reopened before anything is asserted about
# it surviving. A row that only looked at the live state would pass just as happily against a
# playlist that was never written to disk at all.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"
source "$(dirname "$0")/music_lib.sh"

hold_id() { # hold_id <xml> <resource-id> — the app list's 783 ms hold, comfortably passed
  local xy; xy=$(center "$1" "$2") || { echo "no node $2" >&2; return 1; }
  # shellcheck disable=SC2086
  adb shell "input swipe $xy $xy 1200"
  command sleep 2
}
type_name() { # type_name <text with %s for spaces>
  adb shell input text "$1"; command sleep 1
  adb shell input keyevent 66   # the IME's Done, which is what commits the box
  command sleep 2
}
# A row's own node carries no text — both lines are children with their own tags — so every read
# here goes through music_pri: / music_sub:. Reading the row node returns "" and an assertion against
# that passes against anything, which is how the first run of this row "passed" four comparisons it
# never made.
pri() { node_text "$1" "music_pri:$2"; }
first_song_title() { pri "$1" "$(grep -o 'resource-id="music_song:[0-9]*"' "$1" | head -1 | sed 's/resource-id="//; s/"$//')"; }
count_prefix() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | wc -l | tr -d ' '; }

row_begin MUSIC8 "playlists: create, add, reorder, remove, rename, survive a restart, delete"

music_mute
music_fixtures
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
# A clean store, so "no playlists" is a state this row created rather than one it inherited.
adb shell am force-stop $PKG; command sleep 1
adb shell run-as $PKG rm -f files/music_playlists.json >/dev/null 2>&1
assert_eq "the store starts with no file" "" "$(adb shell run-as $PKG ls files/ 2>/dev/null | grep music_playlists)"

# ---- the pivot offers the row that makes one, and nothing else ------------------------------------
music_open
goto_pivot playlists "$ROW_DIR/empty.xml"
adb exec-out screencap -p > "$ROW_DIR/empty.png"
assert_contains "the playlists pivot offers 'new playlist'" "music_new_playlist" "$(cat "$ROW_DIR/empty.xml")"
assert_eq "and there are no playlists yet" 0 "$(count_prefix "$ROW_DIR/empty.xml" 'music_playlist:')"

# ---- create ----------------------------------------------------------------------------------------
tap_id "$ROW_DIR/empty.xml" music_new_playlist; command sleep 2
dump "$ROW_DIR/naming.xml"
adb exec-out screencap -p > "$ROW_DIR/naming.png"
assert_contains "it opens a name box" "music_name_box" "$(cat "$ROW_DIR/naming.xml")"
assert_eq "pre-filled with the default name, so Done alone is enough" "New playlist" "$(node_text "$ROW_DIR/naming.xml" music_name_field)"
adb shell input keyevent KEYCODE_MOVE_END >/dev/null 2>&1
for _ in $(seq 1 14); do adb shell input keyevent 67 >/dev/null 2>&1; done   # clear it
type_name "Road%strip"
dump "$ROW_DIR/created.xml"
adb exec-out screencap -p > "$ROW_DIR/created.png"
assert_eq "one playlist now exists" 1 "$(count_prefix "$ROW_DIR/created.xml" 'music_playlist:')"
PL="$(grep -o 'resource-id="music_playlist:[^"]*"' "$ROW_DIR/created.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
PLID="${PL#music_playlist:}"
note "created [$PL]"
assert_eq "named what was typed" "Road trip" "$(pri "$ROW_DIR/created.xml" "$PL")"
assert_eq "and it starts empty" "0 songs" "$(node_text "$ROW_DIR/created.xml" "music_sub:$PL")"

# ---- add two tracks from the songs pivot ------------------------------------------------------------
goto_pivot songs "$ROW_DIR/songs.xml"
S1="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | sed -n '1p' | sed 's/resource-id="//; s/"$//')"
S2="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | sed -n '2p' | sed 's/resource-id="//; s/"$//')"
T1="$(pri "$ROW_DIR/songs.xml" "$S1")"; T2="$(pri "$ROW_DIR/songs.xml" "$S2")"
assert_ne "there are two distinct songs to add" "$T1" "$T2"
assert_ne "and their titles were readable" "" "$T1"
note "adding [$T1] then [$T2]"
hold_id "$ROW_DIR/songs.xml" "$S1"
dump "$ROW_DIR/menu.xml"
adb exec-out screencap -p > "$ROW_DIR/menu.png"
assert_contains "holding a song opens a menu" "music_menu" "$(cat "$ROW_DIR/menu.xml")"
assert_contains "offering the playlist it could join" "music_menu_add:$PLID" "$(cat "$ROW_DIR/menu.xml")"
assert_contains "and the option to make a new one for it" "music_menu_new" "$(cat "$ROW_DIR/menu.xml")"
tap_id "$ROW_DIR/menu.xml" "music_menu_add:$PLID"; command sleep 2
dump "$ROW_DIR/added1.xml"
assert_absent "picking one closes the menu" "music_menu_scrim" "$(cat "$ROW_DIR/added1.xml")"
hold_id "$ROW_DIR/added1.xml" "$S2"
dump "$ROW_DIR/menu2.xml"
tap_id "$ROW_DIR/menu2.xml" "music_menu_add:$PLID"; command sleep 2
assert_contains "the store says what it wrote" "playlists add" "$(diag music)"

goto_pivot playlists "$ROW_DIR/two.xml"
assert_eq "the playlist row counts both" "2 songs" "$(node_text "$ROW_DIR/two.xml" "music_sub:$PL")"

# ---- open it, and reorder ---------------------------------------------------------------------------
tap_id "$ROW_DIR/two.xml" "$PL"; command sleep 2
dump "$ROW_DIR/detail.xml"
adb exec-out screencap -p > "$ROW_DIR/detail.png"
assert_contains "the playlist opens onto its own page" "music_playlist_detail" "$(cat "$ROW_DIR/detail.xml")"
assert_eq "titled with its name" "Road trip" "$(node_text "$ROW_DIR/detail.xml" music_playlist_title)"
assert_eq "holding both tracks" 2 "$(count_prefix "$ROW_DIR/detail.xml" 'music_song:')"
FIRST="$(first_song_title "$ROW_DIR/detail.xml")"
note "in the playlist, the first track is [$FIRST] (added first: [$T1])"
assert_eq "in the order they were added, not the library's" "$T1" "$FIRST"

hold_id "$ROW_DIR/detail.xml" "$S1"
dump "$ROW_DIR/track_menu.xml"
adb exec-out screencap -p > "$ROW_DIR/track_menu.png"
for m in up down remove; do
  assert_contains "the track menu offers $m" "music_menu_$m" "$(cat "$ROW_DIR/track_menu.xml")"
done
tap_id "$ROW_DIR/track_menu.xml" music_menu_down; command sleep 2
dump "$ROW_DIR/moved.xml"
note "after move down, the first track is [$(first_song_title "$ROW_DIR/moved.xml")]"
assert_eq "move down swaps the two" "$T2" "$(first_song_title "$ROW_DIR/moved.xml")"
assert_eq "and keeps both" 2 "$(count_prefix "$ROW_DIR/moved.xml" 'music_song:')"

# ---- remove one --------------------------------------------------------------------------------------
hold_id "$ROW_DIR/moved.xml" "$S2"
dump "$ROW_DIR/remove_menu.xml"
tap_id "$ROW_DIR/remove_menu.xml" music_menu_remove; command sleep 2
dump "$ROW_DIR/removed.xml"
assert_eq "remove takes one out" 1 "$(count_prefix "$ROW_DIR/removed.xml" 'music_song:')"
assert_eq "and leaves the other" "$T1" "$(first_song_title "$ROW_DIR/removed.xml")"

# ---- rename ------------------------------------------------------------------------------------------
adb shell input keyevent KEYCODE_BACK; command sleep 2
dump "$ROW_DIR/back_to_pivot.xml"
assert_contains "Back leaves the playlist page" "music_new_playlist" "$(cat "$ROW_DIR/back_to_pivot.xml")"
hold_id "$ROW_DIR/back_to_pivot.xml" "$PL"
dump "$ROW_DIR/pl_menu.xml"
assert_contains "holding a playlist offers rename" "music_menu_rename" "$(cat "$ROW_DIR/pl_menu.xml")"
assert_contains "and delete" "music_menu_delete" "$(cat "$ROW_DIR/pl_menu.xml")"
tap_id "$ROW_DIR/pl_menu.xml" music_menu_rename; command sleep 2
dump "$ROW_DIR/renaming.xml"
assert_eq "the box opens on the CURRENT name, not an empty one" "Road trip" "$(node_text "$ROW_DIR/renaming.xml" music_name_field)"
adb shell input keyevent KEYCODE_MOVE_END >/dev/null 2>&1
for _ in $(seq 1 12); do adb shell input keyevent 67 >/dev/null 2>&1; done
type_name "Long%sdrive"
dump "$ROW_DIR/renamed.xml"
adb exec-out screencap -p > "$ROW_DIR/renamed.png"
assert_eq "the playlist is renamed" "Long drive" "$(pri "$ROW_DIR/renamed.xml" "$PL")"
assert_eq "and keeps its track" "1 song" "$(node_text "$ROW_DIR/renamed.xml" "music_sub:$PL")"

# ---- the bracket: it survives the shell being killed ---------------------------------------------------
assert_contains "the store wrote a file" "music_playlists.json" "$(adb shell run-as $PKG ls files/ 2>/dev/null)"
music_open
goto_pivot playlists "$ROW_DIR/restarted.xml"
adb exec-out screencap -p > "$ROW_DIR/restarted.png"
assert_eq "after a force-stop it is still there" "Long drive" "$(pri "$ROW_DIR/restarted.xml" "$PL")"
assert_eq "with its track still in it" "1 song" "$(node_text "$ROW_DIR/restarted.xml" "music_sub:$PL")"

# ---- delete ---------------------------------------------------------------------------------------------
hold_id "$ROW_DIR/restarted.xml" "$PL"
dump "$ROW_DIR/delete_menu.xml"
tap_id "$ROW_DIR/delete_menu.xml" music_menu_delete; command sleep 2
dump "$ROW_DIR/deleted.xml"
adb exec-out screencap -p > "$ROW_DIR/deleted.png"
assert_eq "delete removes it" 0 "$(count_prefix "$ROW_DIR/deleted.xml" 'music_playlist:')"
assert_contains "and the row that makes one is still there" "music_new_playlist" "$(cat "$ROW_DIR/deleted.xml")"

row_end
