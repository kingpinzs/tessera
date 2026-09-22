#!/usr/bin/env bash
# PHASE 10 BUILD TASK 6 — the collection: albums / artists / songs / playlists on a pivot, the jump
# grid over each list, and a tap that actually starts the session built in tasks 3 and 4.
#
# The library is six tagged MP3s pushed to the device and scanned into MediaStore, chosen so the
# grouping has something to be wrong about: three albums, three artists, one compilation-free album
# with three tracks, and a title starting with a digit so the "#" letter group is exercised.
#
# Bracketed on the permission: the collection must SAY the audio permission is missing rather than
# draw an empty library, because those two states look identical and only one of them is fixable by
# the person holding the phone.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

FIXDIR="$(cd "$(dirname "$0")/../MUSIC6-fixtures" 2>/dev/null && pwd)"
count_prefix() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | wc -l | tr -d ' '; }

# The PlaybackState of the SHELL's session, not of whatever the dump happens to list first.
session_state() { awk '/package=app\.tileshell/ { found = 1 } found && /state=PlaybackState/ { print; exit }' "$1"; }

open_music() {
  adb shell am force-stop $PKG; command sleep 1
  adb shell am start -n $PKG/.music.MusicActivity >/dev/null 2>&1
  command sleep 5
}
pivot() { # pivot <dump> <name>
  tap_id "$1" "music_pivot_header:$2" || return 1
  command sleep 2
}

row_begin MUSIC6 "the music collection: four pivots, the jump grid, and a tap that plays"

# ---- the library ---------------------------------------------------------------------------------
adb shell mkdir -p /sdcard/Music/tessera-qa >/dev/null 2>&1
for f in "$FIXDIR"/*.mp3; do adb push "$f" /sdcard/Music/tessera-qa/ >/dev/null 2>&1; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
command sleep 4
SCANNED=$(adb shell content query --uri content://media/external/audio/media --projection title 2>/dev/null | grep -c 'title=')
note "MediaStore holds $SCANNED audio rows"
assert_eq "the six fixtures scanned in" ok "$([ "$SCANNED" -ge 6 ] && echo ok || echo "$SCANNED")"

# ---- bracket: no audio permission, so the page says so -------------------------------------------
adb shell pm revoke $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
open_music
dump "$ROW_DIR/denied.xml"
adb exec-out screencap -p > "$ROW_DIR/denied.png"
assert_contains "with the permission denied the pivot draws an empty state" "music_empty:albums" "$(cat "$ROW_DIR/denied.xml")"
assert_contains "and it says the permission is the reason" "music access" "$(node_text "$ROW_DIR/denied.xml" music_empty:albums)"
assert_eq "no album row is drawn at all" 0 "$(count_prefix "$ROW_DIR/denied.xml" 'music_album:')"
assert_contains "the shell says why in its diagnostics" "no audio access" "$(diag music)"

# ---- granted: the pivot, and the four headers ----------------------------------------------------
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
open_music
dump "$ROW_DIR/albums.xml"
adb exec-out screencap -p > "$ROW_DIR/albums.png"
assert_contains "the collection opens" "music_root" "$(cat "$ROW_DIR/albums.xml")"
for p in albums artists songs playlists; do
  assert_contains "the $p pivot header is drawn" "music_pivot_header:$p" "$(cat "$ROW_DIR/albums.xml")"
done
assert_contains "it opens on the first pivot" "music_album:" "$(cat "$ROW_DIR/albums.xml")"

# ---- albums: three, grouped by album id, each with its artist ------------------------------------
note "album rows: $(count_prefix "$ROW_DIR/albums.xml" 'music_album:')"
assert_eq "three albums, one per album id" 3 "$(count_prefix "$ROW_DIR/albums.xml" 'music_album:')"
assert_contains "Achtung Baby files under A" "music_header:A" "$(cat "$ROW_DIR/albums.xml")"
assert_contains "The King of Limbs files under T" "music_header:T" "$(cat "$ROW_DIR/albums.xml")"

# ---- artists -------------------------------------------------------------------------------------
pivot "$ROW_DIR/albums.xml" artists
dump "$ROW_DIR/artists.xml"
adb exec-out screencap -p > "$ROW_DIR/artists.png"
note "artist rows: $(count_prefix "$ROW_DIR/artists.xml" 'music_artist:')"
assert_eq "three artists" 3 "$(count_prefix "$ROW_DIR/artists.xml" 'music_artist:')"
assert_contains "an artist row is there by name" "music_artist:Radiohead" "$(cat "$ROW_DIR/artists.xml")"
note "the Radiohead row reads [$(node_text "$ROW_DIR/artists.xml" music_sub:music_artist:Radiohead)]"
assert_eq "and it counts that artist's tracks" "3 songs" "$(node_text "$ROW_DIR/artists.xml" music_sub:music_artist:Radiohead)"


# ---- songs: all six, A-Z, with a digit under "#" --------------------------------------------------
pivot "$ROW_DIR/artists.xml" songs
dump "$ROW_DIR/songs.xml"
adb exec-out screencap -p > "$ROW_DIR/songs.png"
note "song rows: $(count_prefix "$ROW_DIR/songs.xml" 'music_song:')"
assert_eq "all six songs" 6 "$(count_prefix "$ROW_DIR/songs.xml" 'music_song:')"
assert_contains "a title starting with a digit files under the hash group" "music_header:#" "$(cat "$ROW_DIR/songs.xml")"
assert_contains "and the letters are the app list's own index" "music_header:D" "$(cat "$ROW_DIR/songs.xml")"

# ---- playlists: empty, and it says so rather than showing nothing ---------------------------------
pivot "$ROW_DIR/songs.xml" playlists
dump "$ROW_DIR/playlists.xml"
adb exec-out screencap -p > "$ROW_DIR/playlists.png"
assert_contains "the playlists pivot draws its empty state" "music_empty:playlists" "$(cat "$ROW_DIR/playlists.xml")"
assert_contains "and it is about playlists, not about the permission" "playlists" "$(node_text "$ROW_DIR/playlists.xml" music_empty:playlists)"

# ---- the jump grid ---------------------------------------------------------------------------------
pivot "$ROW_DIR/playlists.xml" songs
dump "$ROW_DIR/pre_jump.xml"
tap_id "$ROW_DIR/pre_jump.xml" "music_header:#"; command sleep 2
dump "$ROW_DIR/jump.xml"
adb exec-out screencap -p > "$ROW_DIR/jump.png"
assert_contains "a tap on a letter header opens the jump grid" "music_jump_grid" "$(cat "$ROW_DIR/jump.xml")"
JUMP_N=$(count_prefix "$ROW_DIR/jump.xml" 'music_jump:')
note "jump cells drawn: $JUMP_N"
assert_eq "every letter is a cell, present or not" 27 "$JUMP_N"
tap_id "$ROW_DIR/jump.xml" "music_jump:Z"; command sleep 2
dump "$ROW_DIR/jumped.xml"
adb exec-out screencap -p > "$ROW_DIR/jumped.png"
assert_absent "the grid closes on a pick" "music_jump_grid" "$(cat "$ROW_DIR/jumped.xml")"
assert_contains "and the list lands on that letter" "music_header:Z" "$(cat "$ROW_DIR/jumped.xml")"

# ---- a tap plays, through the session built in tasks 3 and 4 --------------------------------------
dump "$ROW_DIR/before_play.xml"
SONG=$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/before_play.xml" | head -1 | sed 's/resource-id="//; s/"$//')
note "tapping [$SONG]"
assert_ne "there is a song row to tap" "" "$SONG"
# Guarded: tap_id with an empty id matches the ROOT node's empty resource-id and taps the middle of the
# screen, which is how the first run of this row "passed" a playback assertion it never exercised.
[ -n "$SONG" ] && tap_id "$ROW_DIR/before_play.xml" "$SONG"
command sleep 4
adb shell dumpsys media_session > "$ROW_DIR/session.txt" 2>&1
dump "$ROW_DIR/playing.xml"
adb exec-out screencap -p > "$ROW_DIR/playing.png"
assert_contains "the shell owns a media session" "$PKG" "$(cat "$ROW_DIR/session.txt")"
STATE="$(session_state "$ROW_DIR/session.txt")"
note "the shell's session: ${STATE:-none}"
assert_contains "and that session is PLAYING" "PLAYING(3)" "$STATE"
POS="$(echo "$STATE" | sed -n 's/.*position=\([0-9]*\).*/\1/p')"
assert_eq "with the track actually advancing, not merely loaded" ok \
  "$([ -n "$POS" ] && [ "$POS" -gt 0 ] && echo ok || echo "position=${POS:-none}")"
# "play " alone is not enough: MusicPlayer logs "play ignored: no controller yet" when the session is
# not connected, and that contains it. The row asserts the queue position it printed instead.
assert_contains "the player says which track it started, and of how many" " of 6)" "$(diag music)"
assert_absent "the controller was connected, not skipped" "play ignored" "$(diag music)"
assert_contains "the playback service is up" "MusicService" "$(adb shell dumpsys activity services $PKG 2>/dev/null)"

# ---- an album opens onto its own tracks ------------------------------------------------------------
pivot "$ROW_DIR/playing.xml" albums
dump "$ROW_DIR/albums2.xml"
ALBUM=$(grep -o 'resource-id="music_album:[0-9]*"' "$ROW_DIR/albums2.xml" | head -1 | sed 's/resource-id="//; s/"$//')
note "opening [$ALBUM]"
tap_id "$ROW_DIR/albums2.xml" "$ALBUM"; command sleep 2
dump "$ROW_DIR/detail.xml"
adb exec-out screencap -p > "$ROW_DIR/detail.png"
assert_contains "an album opens onto its own page" "music_detail" "$(cat "$ROW_DIR/detail.xml")"
DETAIL_N=$(count_prefix "$ROW_DIR/detail.xml" 'music_song:')
note "tracks on the album page: $DETAIL_N · title [$(node_text "$ROW_DIR/detail.xml" music_detail_title)]"
assert_eq "holding that album's tracks and no others" ok "$([ "$DETAIL_N" -ge 1 ] && [ "$DETAIL_N" -le 3 ] && echo ok || echo "$DETAIL_N")"
adb shell input keyevent KEYCODE_BACK; command sleep 2
dump "$ROW_DIR/back.xml"
assert_absent "Back leaves the album page" "music_detail_title" "$(cat "$ROW_DIR/back.xml")"
assert_contains "and returns to the pivot it was opened from" "music_pivot_header:albums" "$(cat "$ROW_DIR/back.xml")"

row_end
