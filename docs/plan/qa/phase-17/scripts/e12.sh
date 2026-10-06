#!/usr/bin/env bash
# Phase 17 E12 — audio focus and media keys. The doc's clauses → this driver's legs:
#
#   music     a track played in the shell's Music (a music file already on the device: phase 01 MUSIC6's fixtures) —
#             its session PLAYING is the precondition, asserted
#   video     the video opened (from My videos): `dumpsys media_session` shows Music's session PAUSED and the video's
#             PLAYING
#   key       `adb shell input keyevent KEYCODE_MEDIA_PAUSE` pauses the VIDEO (the last active session); Music's state
#             is unchanged (still PAUSED — the key did not reach it)
#   back      Back out of the video → Music stays paused (phase 10 E9: nothing resumes by itself), read at once and 3 s on
#
# Sound: the device's media volume is 0 for the row (put back); no host audio, no microphone.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"

video_row_begin E12 "audio focus and media keys: the video pauses Music, the key pauses the video, nothing resumes"
quiet_on
videos_grant
media_up qa-steps.mp4
ID="$(media_id video qa-steps.mp4 Movies/)"
assert_ne "qa-steps.mp4 is in MediaStore" "" "$ID"
SONGS="$(q "content query --uri content://media/external/audio/media --projection _id:is_music" | grep -c 'is_music=1')"
assert_ne "a music track is on the device (phase 01 MUSIC6's fixtures)" "0" "$SONGS"

log "--- a track in the shell's Music"
adb shell am force-stop app.tileshell; sleep 1
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$D/music.xml"; tap_node "$D/music.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$D/songs.xml"
FIRST="$(grep -o 'resource-id="music_song:[0-9]*"' "$D/songs.xml" | head -1 | sed 's/resource-id="//;s/"//')"
assert_ne "a song row is listed" "" "$FIRST"
tap_node "$D/songs.xml" "$FIRST"; sleep 3
assert_eq "precondition: Music's session is PLAYING before the video" "PLAYING" "$(session_state .id.music)"

log "--- the video opened from My videos"
MARK="$(ring_mark)"
play_from_hub "$ID" "$D/open"
LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
assert_contains "the video plays ([video] playing $ID)" "[video] playing $ID" "$LINE"
sleep 1
adb shell dumpsys media_session | tr -d '\r' > "$D/media_session-playing.txt"
assert_eq "dumpsys media_session: the video's session is PLAYING" "PLAYING" "$(session_state .id.video)"
assert_eq "dumpsys media_session: Music's session is PAUSED" "PAUSED" "$(session_state .id.music)"
assert_contains "the media-button session is the video's (the last active session)" ".id.video" "$(grep -m1 'Media button session is' "$D/media_session-playing.txt")"

log "--- KEYCODE_MEDIA_PAUSE"
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 1.2
adb shell dumpsys media_session | tr -d '\r' > "$D/media_session-key.txt"
assert_eq "the key pauses the VIDEO" "PAUSED" "$(session_state .id.video)"
assert_eq "… not Music: its state is unchanged (PAUSED)" "PAUSED" "$(session_state .id.music)"

log "--- Back out of the video"
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 3
assert_ne "Back leaves the player" "$PLAYER_ACTIVITY" "$(top_activity)"
assert_eq "the video's session is gone" "" "$(session_state .id.video)"
assert_eq "Music stays paused (nothing resumes by itself)" "PAUSED" "$(session_state .id.music)"
sleep 3
assert_eq "… and is still paused 3 s later" "PAUSED" "$(session_state .id.music)"
adb shell dumpsys media_session | tr -d '\r' > "$D/media_session-after.txt"

log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
rings_save
adb shell am force-stop app.tileshell
media_down
videos_grant_restore
quiet_off
c6; ensure_start
row_end
