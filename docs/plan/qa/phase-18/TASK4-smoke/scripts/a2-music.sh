#!/usr/bin/env bash
# E6: the MP3 with no MediaStore row plays in Music by URI; the indexed MP3 plays by id; the own-launch negatives.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg a-e6
MUSIC=app.tileshell/.music.MusicActivity
adb logcat -c
norow() { q "content query --uri content://media/external/$1/media --projection _data:_display_name" | grep -c "$2"; }
tracks() { ring_since "$1" | grep -o '\[music\] library ([^)]*): [0-9]* tracks' | tail -1 | grep -o '[0-9]* tracks'; }
AUD=$(grep -F "_data=$QF/03.mp3" "$ROW_DIR/rows-audio-before.txt" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')
assert_ne "precondition: 03.mp3 has an audio row" "" "$AUD"
assert_eq "precondition: no audio row named qa-hidden.mp3 (content query)" "0" "$(norow audio qa-hidden.mp3)"

# ---- the Songs count before: Music opened once
c6; ensure_start; M0=$(ring_mark); adb shell am start -W -n $MUSIC >/dev/null; sleep 3
N0="$(tracks $M0)"; echo "library before: $N0"; assert_ne "the library line is read" "" "$N0"
c6; ensure_start

# ---- the unindexed MP3 (Music not running)
M=$(ring_mark); tap_row $QF/hidden qa-hidden.mp3 4; S 20-music-hidden; D 20-music-hidden
assert_eq "unindexed MP3: top activity" "$MUSIC" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|music)\]" | tee "$ROW_DIR/20-ring.txt"
assert_contains "unindexed MP3: [music] play file … (not in library)" "[music] play file $QF/hidden/qa-hidden.mp3 (not in library)" "$SL"
assert_contains "unindexed MP3: [files] recent add" "[files] recent add $QF/hidden/qa-hidden.mp3" "$SL"
session_state | tee "$ROW_DIR/20-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/20-media_session.txt"
assert_contains "unindexed MP3: the music session is PLAYING" "state=PLAYING" "$(grep -v session.id.video "$ROW_DIR/20-session.txt")"
assert_contains "unindexed MP3: its title is qa-hidden" "desc=[qa-hidden," "$(grep -v session.id.video "$ROW_DIR/20-session.txt")"
assert_eq "unindexed MP3: the library count is unchanged ($N0)" "$N0" "$(tracks $M)"
assert_eq "after: still no audio row named qa-hidden.mp3" "0" "$(norow audio qa-hidden.mp3)"
echo "now playing nodes: $(ids 20-music-hidden | cut -c1-300)"

# ---- the indexed MP3, with Music already open (onNewIntent)
M=$(ring_mark); tap_row $QF 03.mp3 4; S 21-music-indexed
assert_eq "indexed MP3: top activity" "$MUSIC" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|music)\]" | tee "$ROW_DIR/21-ring.txt"
assert_contains "indexed MP3: [files] recent add" "[files] recent add $QF/03.mp3" "$SL"
absent_in "indexed MP3: no 'play extra ignored'" "play extra ignored" "$SL"
absent_in "indexed MP3: not the file path" "play file" "$SL"
session_state | tee "$ROW_DIR/21-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/21-media_session.txt"
assert_contains "indexed MP3: the music session is PLAYING" "state=PLAYING" "$(grep -v session.id.video "$ROW_DIR/21-session.txt")"
q "content query --uri content://media/external/audio/media --projection _id:title --where _id=$AUD" | tee "$ROW_DIR/21-row.txt"
TITLE="$(sed -n 's/.*title=\(.*\)$/\1/p' "$ROW_DIR/21-row.txt")"
assert_contains "indexed MP3: the session's track is the row's title ($TITLE)" "desc=[$TITLE," "$(grep -v session.id.video "$ROW_DIR/21-session.txt")"
echo "music activities in the task: $(adb shell dumpsys activity activities | grep -c 'Hist.*music.MusicActivity')"

# ---- the indexed MP3, Music cold
c6; ensure_start; M=$(ring_mark); tap_row $QF 03.mp3 4
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|music)\]" | tee "$ROW_DIR/22-ring.txt"
session_state | tee "$ROW_DIR/22-session.txt"
assert_contains "indexed MP3, Music cold: PLAYING the row's title" "state=PLAYING desc=[$TITLE," "$(grep -v session.id.video "$ROW_DIR/22-session.txt")"
absent_in "indexed MP3, Music cold: no 'play extra ignored'" "play extra ignored" "$SL"

# ---- the unindexed MP3, Music already open
M=$(ring_mark); tap_row $QF/hidden qa-hidden.mp3 4
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|music)\]" | tee "$ROW_DIR/23-ring.txt"
session_state | tee "$ROW_DIR/23-session.txt"
assert_contains "unindexed MP3, Music open: PLAYING qa-hidden" "state=PLAYING desc=[qa-hidden," "$(grep -v session.id.video "$ROW_DIR/23-session.txt")"
assert_contains "unindexed MP3, Music open: [music] play file …" "[music] play file $QF/hidden/qa-hidden.mp3 (not in library)" "$SL"
echo "music activities in the task: $(adb shell dumpsys activity activities | grep -c 'Hist.*music.MusicActivity')"

# ---- a recording: it HAS an audio row (IS_RECORDING), and Music's library skips it — so it goes by URI
adb shell mkdir -p /sdcard/Recordings; adb push "$T4/e-recorder/qa-take.m4a" /sdcard/Recordings/qa-take.m4a >/dev/null; media_scan
record "the recording's audio row" "$(q "content query --uri content://media/external/audio/media --projection _id:_data:is_recording" | grep 'qa-take' | xargs)"
M=$(ring_mark); tap_row /storage/emulated/0/Recordings qa-take.m4a 4
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|music)\]" | tee "$ROW_DIR/24-ring.txt"
session_state | tee "$ROW_DIR/24-session.txt"
assert_contains "a recording: [music] play file … (not in library)" "[music] play file /storage/emulated/0/Recordings/qa-take.m4a (not in library)" "$SL"
absent_in "a recording: no 'play extra ignored'" "play extra ignored" "$SL"
assert_contains "a recording: PLAYING qa-take" "state=PLAYING desc=[qa-take," "$(grep -v session.id.video "$ROW_DIR/24-session.txt")"
c6; adb shell rm -f /sdcard/Recordings/qa-take.m4a; media_scan
assert_eq "the recording is removed again" "0" "$(q "content query --uri content://media/external/audio/media --projection _data" | grep -c 'qa-take')"

# ---- own-launch negatives, from adb (the shell uid): Music cold, Music open, single-top
URI=content://app.tileshell.files/root/storage/emulated/0/QA-Files/hidden/qa-hidden.mp3
neg() { # name, am-start args… (NODELIVER=1: the platform does not hand a running Music the intent at all without single-top)
  local name="$1"; shift
  local m; m=$(ring_mark); adb shell am start -W "$@" > "$ROW_DIR/neg-$name.am.txt" 2>&1; sleep 3
  local sl; sl="$(ring_since $m)"; echo "$sl" | grep -F "[music]" | tee "$ROW_DIR/neg-$name.ring.txt"
  session_state > "$ROW_DIR/neg-$name.session.txt"
  if [ -n "$NODELIVER" ]; then
    record "negative $name: am start without single-top to a running Music — [music] lines written" "$(echo "$sl" | grep -cF '[music]') (the intent is not delivered: no onNewIntent, so nothing to ignore)"
    assert_eq "negative $name: no play line of any kind" "0" "$(echo "$sl" | grep -cE '\[music\] play (file|[^e])')"
  else
    assert_contains "negative $name: [music] play extra ignored: not the shell" "[music] play extra ignored: not the shell" "$sl"
    absent_in "negative $name: nothing was played (no play file line)" "play file" "$sl"
  fi
  assert_eq "negative $name: top is Music" "$MUSIC" "$(top_activity)"
  assert_eq "negative $name: the session is not PLAYING" "0" "$(grep -v session.id.video "$ROW_DIR/neg-$name.session.txt" | grep -c 'state=PLAYING')"
  echo "   session: $(grep -v session.id.video "$ROW_DIR/neg-$name.session.txt" | xargs)"
}
c6; ensure_start
neg cold-id -n $MUSIC --el app.tileshell.music.PLAY_ID $AUD
c6; ensure_start
neg cold-uri -n $MUSIC --es app.tileshell.music.PLAY_URI $URI
NODELIVER=1 neg open-id -n $MUSIC --el app.tileshell.music.PLAY_ID $AUD
NODELIVER=1 neg open-uri -n $MUSIC --es app.tileshell.music.PLAY_URI $URI
neg singletop-id -n $MUSIC -f 0x20000000 --el app.tileshell.music.PLAY_ID $AUD
neg singletop-uri -n $MUSIC -f 0x20000000 --es app.tileshell.music.PLAY_URI $URI
neg newtask-singletop-uri -n $MUSIC -f 0x30000000 --es app.tileshell.music.PLAY_URI $URI
c6; ensure_start
leg_end
