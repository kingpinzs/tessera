#!/usr/bin/env bash
# Phase 17 development proof, build task 7 (brief A; E12's form): a video takes audio focus — the shell's Music pauses
# and does not resume after Back — and the media key goes to the video's session. No host audio is touched: the
# DEVICE's media volume is set to 0 for the row and put back. Restores: volume, the pushed video, Music stopped.
. "$(dirname "$0")/v17.sh"
row_begin A_FOCUS "audio focus: the video pauses Music, nothing resumes by itself; the media key"
D="$ROW_DIR"
# The PlaybackState of the shell's session whose header carries <tag> (dumpsys media_session; phase 15 e0.sh's reader).
session_state() { # tag
  adb shell dumpsys media_session | tr -d '\r' | python3 -c '
import re, sys
tag = sys.argv[1]; hit = False
for l in sys.stdin:
    if re.match(r"^\s+\S+ \S+/\S+/\d+ \(userId=\d+\)", l):
        hit = " app.tileshell/" in l and tag in l
    elif hit:
        m = re.search(r"state=PlaybackState \{state=([A-Z_]+)", l)
        if m: print(m.group(1)); break' "$1"
}
VOL0="$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
record "device media volume before (set to 0 for the row, put back after)" "${VOL0:-?}"
adb shell cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1
CENSUS="$(video_count)"; videos_grant
SONG="$(adb shell content query --uri content://media/external/audio/media --projection _id:is_music | grep 'is_music=1' | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')"
assert_ne "a music track is on the device (phase 01's fixtures)" "" "$SONG"
push_videos qa-steps.mp4; ID="$(video_id qa-steps.mp4)"

adb shell am force-stop app.tileshell; sleep 1
adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 4
dump_ui "$D/music.xml"; tap_node "$D/music.xml" "music_pivot_header:songs"; sleep 3
dump_ui "$D/songs.xml"
FIRST="$(grep -o 'resource-id="music_song:[0-9]*"' "$D/songs.xml" | head -1 | sed 's/resource-id="//;s/"//')"
assert_ne "a song row is listed" "" "$FIRST"
tap_node "$D/songs.xml" "$FIRST"; sleep 3
assert_eq "Music is playing before the video" "PLAYING" "$(session_state .id.music)"

MARK="$(ring_mark)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "content://media/external/video/media/$ID" -t video/mp4 >/dev/null
await_vline "$MARK" "[video] playing $ID" >/dev/null; sleep 1
assert_eq "the video's session is playing" "PLAYING" "$(session_state .id.video)"
assert_eq "Music's session is paused by the video" "PAUSED" "$(session_state .id.music)"
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 1
assert_eq "the media key pauses the VIDEO" "PAUSED" "$(session_state .id.video)"
assert_eq "Music is still paused" "PAUSED" "$(session_state .id.music)"
for r in $RINGS; do ring_save "$r"; done
adb shell input keyevent KEYCODE_BACK; sleep 3
assert_eq "the video's session is gone after Back" "" "$(session_state .id.video)"
assert_eq "Music did not resume by itself" "PAUSED" "$(session_state .id.music)"
sleep 3
assert_eq "…and still has not, 3 s later" "PAUSED" "$(session_state .id.music)"

adb shell am force-stop app.tileshell
remove_videos
assert_eq "videos restored to the census" "$CENSUS" "$(video_count)"
[ -n "$VOL0" ] && adb shell cmd media_session volume --stream 3 --set "$VOL0" >/dev/null 2>&1
assert_eq "device media volume put back" "${VOL0:-?}" "$(adb shell cmd media_session volume --stream 3 --get 2>/dev/null | tr -d '\r' | grep -oE 'volume is [0-9]+' | grep -oE '[0-9]+')"
videos_grant_restore
ensure_start
row_end
