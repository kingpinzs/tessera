#!/usr/bin/env bash
# E20 — the Music cross-effect (Q2 A, T15-46): with the shell's own take and the fixtures pushed, Music's songs pivot lists
# NEITHER the take NOR other.m4a (both IS_RECORDING) and the launcher ring holds `[music] skipped recording <id>` for each
# (the silent-empty rule); it DOES list song.m4a (is_recording 0), so the skip is the flag, not the file type. Then song.m4a
# removed (+ scan) with the take and other.m4a still present: MUSIC6's exact counts hold — three albums, three artists,
# six songs (qa/phase-01/scripts/music6.sh:52,60,70) — asserted here on MUSIC6's own fixtures; the lead re-runs music6.sh
# itself (it writes phase 01's evidence dir and takes the device lock). Restore: the take deleted, the fixtures removed,
# the Music fixtures removed if they were absent, the media volume put back.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/rec.sh"
. "$QROOT/phase-01/scripts/music_lib.sh"
RINGS="launcher $REC_SVC"
row_begin E20 "recordings stay out of Music: the IS_RECORDING skip, and MUSIC6's counts hold beside them"

wake_device >/dev/null
adb shell pm grant app.tileshell android.permission.RECORD_AUDIO >/dev/null 2>&1
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
assert_eq "baseline: MediaStore holds no take of the shell's" 0 "$(own_count)"
audio_route "E20"
VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
HAD_FIX="$(adb shell ls /sdcard/Music/tessera-qa 2>/dev/null | grep -c mp3)"
note "media volume before: ${VOL0:-?}; Music fixtures present before: $HAD_FIX"
count_prefix() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | wc -l | tr -d ' '; }
# Music, opened as MUSIC6 opens it (music_lib's music_open: force-stop, start, settle), then a pivot by its header, settled
# on the shell's own `pivot settled on <name>` line (music_lib's goto_pivot form, with lib.sh's dump and tap).
music_pivot() { # name out.xml
  local i=0 cur
  dump_ui "$ROW_DIR/.nav.xml" || return 1
  tap_node "$ROW_DIR/.nav.xml" "music_pivot_header:$1" || return 1
  while [ "$i" -lt 16 ]; do
    cur="$(diag music | grep -o 'pivot settled on [a-z]*' | tail -1 | awk '{print $4}')"
    [ "$cur" = "$1" ] && break
    sleep 0.5; i=$((i + 1))
  done
  sleep 1
  dump_ui "$2"
  note "pivot -> $1 (diagnostics say [$cur])"
  [ "$cur" = "$1" ]
}

if [ "$AUDIO_OK" = yes ]; then
  rec_open record
  TAKE="$(make_take 5)"; assert_ne "the shell's own take is in MediaStore" "" "$TAKE"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  push_fixture_recordings
  music_fixtures
  OTHER="$(id_by_name other.m4a)"; SONG="$(id_by_name song.m4a)"
  note "ids: take $TAKE, other.m4a $OTHER (is_recording $(ms_field "$(row_by_id "$OTHER")" is_recording)), song.m4a $SONG (is_recording $(ms_field "$(row_by_id "$SONG")" is_recording))"
  assert_eq "song.m4a is not a recording" 0 "$(ms_field "$(row_by_id "$SONG")" is_recording)"
  music_mute
  # ---- Music with the recordings present ------------------------------------------------------------------------------
  MARK="$(ring_mark)"
  music_open
  music_pivot songs "$ROW_DIR/songs_with.xml"; screencap "$ROW_DIR/songs_with.png"
  ring_since "$MARK" > "$ROW_DIR/ring_music_with.txt"
  note "song rows: $(count_prefix "$ROW_DIR/songs_with.xml" 'music_song:') — $(ids_with_prefix "$ROW_DIR/songs_with.xml" 'music_song:' | paste -sd' ')"
  assert_eq "the songs pivot does NOT list the take" no "$(has_node "$ROW_DIR/songs_with.xml" "music_song:$TAKE")"
  assert_eq "the songs pivot does NOT list other.m4a" no "$(has_node "$ROW_DIR/songs_with.xml" "music_song:$OTHER")"
  assert_eq "the songs pivot DOES list song.m4a (is_recording 0: the skip is the flag, not the file type)" yes "$(has_node "$ROW_DIR/songs_with.xml" "music_song:$SONG")"
  assert_contains "the launcher ring skipped the take" "[music] skipped recording $TAKE" "$(cat "$ROW_DIR/ring_music_with.txt")"
  assert_contains "the launcher ring skipped other.m4a" "[music] skipped recording $OTHER" "$(cat "$ROW_DIR/ring_music_with.txt")"
  assert_absent "and never skipped song.m4a" "[music] skipped recording $SONG" "$(cat "$ROW_DIR/ring_music_with.txt")"
  assert_eq "seven songs listed (MUSIC6's six + song.m4a)" 7 "$(count_prefix "$ROW_DIR/songs_with.xml" 'music_song:')"
  # ---- song.m4a removed: MUSIC6's counts, with the take and other.m4a still present -------------------------------------
  adb shell rm -f /sdcard/Music/song.m4a
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 2
  assert_eq "song.m4a is gone from MediaStore" "" "$(id_by_name song.m4a)"
  assert_ne "the take is still present" "" "$(row_by_id "$TAKE")"
  assert_ne "other.m4a is still present" "" "$(row_by_id "$OTHER")"
  MARK2="$(ring_mark)"
  music_open
  dump_ui "$ROW_DIR/albums.xml"; screencap "$ROW_DIR/albums.png"
  assert_contains "Music opens on the albums pivot" "music_album:" "$(cat "$ROW_DIR/albums.xml")"
  assert_eq "MUSIC6: three albums, one per album id (music6.sh:52)" 3 "$(count_prefix "$ROW_DIR/albums.xml" 'music_album:')"
  music_pivot artists "$ROW_DIR/artists.xml"; screencap "$ROW_DIR/artists.png"
  assert_eq "MUSIC6: three artists (music6.sh:60)" 3 "$(count_prefix "$ROW_DIR/artists.xml" 'music_artist:')"
  assert_eq "MUSIC6: Radiohead counts 3 songs" "3 songs" "$(node_text "$ROW_DIR/artists.xml" music_sub:music_artist:Radiohead)"
  music_pivot songs "$ROW_DIR/songs.xml"; screencap "$ROW_DIR/songs.png"
  assert_eq "MUSIC6: all six songs (music6.sh:70)" 6 "$(count_prefix "$ROW_DIR/songs.xml" 'music_song:')"
  assert_contains "MUSIC6: a title starting with a digit files under #" "music_header:#" "$(cat "$ROW_DIR/songs.xml")"
  assert_eq "still no take on the songs pivot" no "$(has_node "$ROW_DIR/songs.xml" "music_song:$TAKE")"
  assert_eq "still no other.m4a on the songs pivot" no "$(has_node "$ROW_DIR/songs.xml" "music_song:$OTHER")"
  ring_since "$MARK2" > "$ROW_DIR/ring_music6.txt"
  assert_contains "the skip lines again for the take" "[music] skipped recording $TAKE" "$(cat "$ROW_DIR/ring_music6.txt")"
  assert_contains "and for other.m4a" "[music] skipped recording $OTHER" "$(cat "$ROW_DIR/ring_music6.txt")"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  # ---- restore ----------------------------------------------------------------------------------------------------
  app_delete_take "$TAKE"
  assert_eq "restore: the take is deleted" 0 "$(own_count)"
  [ "$(own_count)" != 0 ] && note "sweeping leftovers -> $(purge_own_takes) remain"
else
  _verdict FAIL "the take for the Music cross-effect" "NOT RUN: the audio route failed its check"
fi
remove_fixture_recordings
if [ "$HAD_FIX" = 0 ]; then
  adb shell rm -rf /sdcard/Music/tessera-qa
  adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
  note "restore: the Music fixtures removed (they were absent before)"
fi
# `cmd media_session volume --set` left the stream at 0 (run 1: music_mute's twenty VOLUME_DOWNs leave STREAM_MUSIC muted
# on the speaker, and that route did not lift it); AudioManager.setStreamVolume through `cmd audio` does, and is read back.
[ -n "$VOL0" ] && adb shell cmd audio set-volume 3 "$VOL0" >/dev/null 2>&1
assert_eq "restore: the media volume is back" "${VOL0:-?}" "$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
