#!/usr/bin/env bash
# Phase 18 E6: open with (r3 D1 / V1 / D5; Q-18-2 (a)), as the dated entries and BUILD-NOTES re-cut it:
#   * phase 17 writes `[photosapp] viewer open <id>` at an open (`viewer show` is its swipe line) — BUILD-NOTES, the
#     open-with builder's fact 1; for a provider URI the id is `external`
#   * `.xyz` maps to chemical/x-xyz on this image, and application/octet-stream HAS handlers on the AVD — fact 2: the
#     no-handler line is asserted with the type the platform gives q.xyz
#   * on a public volume the APP's MediaStore query finds no row (the shell's does), so every public-volume file opens
#     through the provider — fact 3 (r3 D1's "a volume MediaStore has not indexed")
#   * a plain `am start` to a RUNNING Music delivers no new intent — fact 8: the own-launch negatives start Music cold
#
#   image       an indexed image → photos.ViewerActivity, `[photosapp] viewer open <id>`, the centre pixel = the
#               fixture's colour ± 4; the unindexed ones (hidden/, the public volume) → the same component through the
#               provider (`[files] open <path> via provider`), the picture asserted the same way, Share only
#   video       an indexed video → video.PlayerActivity, `[video] playing <id>` in the :video ring, media_session
#               PLAYING; the unindexed ones → the same through the provider
#   zip         an entry tapped inside qa.zip → "Extract first", no activity started
#   music       03.mp3 → MusicActivity, the shell's music session PLAYING that track; qa-hidden.mp3 (no audio row before
#               or after) → PLAYING with the title qa-hidden, `[music] play file … (not in library)`, the library's
#               count unchanged; the own-launch check, id form and URI form, from adb → not played, `[music] play
#               extra ignored: not the shell`; the JVM rule (`*MusicPlayExtra*`) for a foreign authority
#   chooser     a.txt → com.android.intentresolver with text/plain; q.xyz → "No app on this phone opens this"
# Every opener hand-off logs `[files] recent add <path>`. After each launch: c6 (it saves the :video ring first).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
VIDEO_RING="app.tileshell/.video.VideoDumpService"
RINGS=(launcher "$VIDEO_RING")
VIEWER=app.tileshell/.photos.ViewerActivity
PLAYER=app.tileshell/.video.PlayerActivity
MUSIC=app.tileshell/.music.MusicActivity
HID=$QF/hidden

keep_earlier_run E6
row_begin E6 "open with: images, videos, audio (indexed and not), the own-launch check, the chooser, no handler"
assert_gate_apk
baseline_start
adb logcat -c
files_up zips || { row_end; exit 1; }
pubvol_up >/dev/null || { files_down; row_end; exit 1; }
sleep 2
V="$PUBVOL_PATH"
adb shell "cp /sdcard/QA-Files/img-2.png $V/pv.png; cp /sdcard/QA-Files/qa-steps.mp4 $V/pv.mp4; printf xyz > /sdcard/QA-Files/q.xyz"; sleep 2
rowid() { q "content query --uri content://media/external/$1/media --projection _id:_data" | grep -F "_data=$2" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
norow() { q "content query --uri content://media/external/$1/media --projection _data:_display_name" | grep -c "$2"; }
IMG="$(rowid images "$QF/img-0.png")"; VID="$(rowid video "$QF/qa-steps.mp4")"; AUD="$(rowid audio "$QF/03.mp3")"
assert_ne "precondition: img-0.png has an images row, qa-steps.mp4 a video row, 03.mp3 an audio row" "" "${IMG:+x}${VID:+x}${AUD:+x}"
record "the MediaStore ids (img-0.png / qa-steps.mp4 / 03.mp3)" "$IMG / $VID / $AUD"
for f in "images qa-hidden.png" "video qa-hidden.mp4" "audio qa-hidden.mp3"; do
  # shellcheck disable=SC2086
  set -- $f
  assert_eq "precondition: no $1 row named $2 (content query; a .nomedia folder)" "0" "$(norow "$1" "$2")"
done
record "the shell uid's content query for the public volume's files (the APP's own query finds none — BUILD-NOTES)" \
  "pv.png rows=$(norow images pv.png) pv.mp4 rows=$(norow video pv.mp4)"
ensure_start

open_image() { # leg folder name colour indexed|provider id
  local m sl c
  m="$(ring_mark)"; tap_row "$2" "$3" 3; S "$1"; D "$1"
  assert_eq "$1: top_activity is the viewer" "$VIEWER" "$(top_activity)"
  sl="$(ring_since "$m")"; printf '%s\n' "$sl" | grep -E '\[(files|photosapp)\]' > "$ROW_DIR/$1-ring.txt"
  assert_contains "$1: [files] recent add <path>" "[files] recent add $2/$3" "$sl"
  c="$(px "$ROW_DIR/$1.png" 540 1170)"
  assert_eq "$1: the screencap's centre pixel $c = the fixture's colour $4 ± 4" "yes" "$(near "$c" "$4" 4)"
  if [ "$5" = indexed ]; then
    assert_contains "$1: [photosapp] viewer open $6" "[photosapp] viewer open $6" "$sl"
    absent_in "$1: an indexed image does not go through the provider" "via provider" "$sl"
    assert_eq "$1: the bar offers Share, Edit and Delete" "yes yes yes" "$(H "$1" viewer_share) $(H "$1" viewer_edit) $(H "$1" viewer_delete)"
  else
    assert_contains "$1: [files] open <path> via provider" "[files] open $2/$3 via provider" "$sl"
    assert_contains "$1: [photosapp] viewer open external (a provider URI)" "[photosapp] viewer open external" "$sl"
    assert_eq "$1: the viewer's bar offers Share only (no Delete, no Edit node)" "yes no no" "$(H "$1" viewer_share) $(H "$1" viewer_delete) $(H "$1" viewer_edit)"
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
  assert_eq "$1: Back returns to Files" "$FILES_ACTIVITY" "$(top_activity)"
}
open_video() { # leg folder name indexed|provider id
  local m sl vl
  m="$(ring_mark)"; tap_row "$2" "$3" 4; S "$1"
  assert_eq "$1: top_activity is the player" "$PLAYER" "$(top_activity)"
  sl="$(ring_since "$m")"; vl="$(ring_since "$m" "$VIDEO_RING")"
  { printf '%s\n' "$sl" | grep -F '[files]'; printf '%s\n' "$vl" | grep -F '[video]'; } > "$ROW_DIR/$1-ring.txt"
  assert_contains "$1: [files] recent add <path>" "[files] recent add $2/$3" "$sl"
  if [ "$4" = indexed ]; then
    assert_contains "$1: [video] playing $5 (the :video ring)" "[video] playing $5" "$vl"
    absent_in "$1: an indexed video does not go through the provider" "via provider" "$sl"
  else
    assert_contains "$1: [files] open <path> via provider" "[files] open $2/$3 via provider" "$sl"
    assert_contains "$1: [video] playing scheme=content (the :video ring)" "[video] playing scheme=content" "$vl"
  fi
  session_state > "$ROW_DIR/$1-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/$1-media_session.txt"
  assert_contains "$1: dumpsys media_session — the video session is PLAYING" "session.id.video state=PLAYING" "$(cat "$ROW_DIR/$1-session.txt")"
  adb shell input keyevent KEYCODE_BACK; sleep 1.5
}

# ------------------------------------------------------------------------------------------------ images
log "--- images: indexed, hidden/, the public volume"
open_image 10-image "$QF" img-0.png 220,40,40 indexed "$IMG"
open_image 11-image-hidden "$HID" qa-hidden.png 160,60,200 provider
open_image 12-image-volume "$V" pv.png 40,90,220 provider
c6; ensure_start

# ------------------------------------------------------------------------------------------------ videos
log "--- videos: indexed, hidden/, the public volume"
open_video 20-video "$QF" qa-steps.mp4 indexed "$VID"
open_video 21-video-hidden "$HID" qa-hidden.mp4 provider
open_video 22-video-volume "$V" pv.mp4 provider
assert_eq "after the unindexed opens: still no images / video row for the hidden files" "0 0" "$(norow images qa-hidden.png) $(norow video qa-hidden.mp4)"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ an entry inside a zip
log "--- an entry tapped inside qa.zip"
zopen qa.zip; D 30-zip; M="$(ring_mark)"; T 30-zip files_row:one.txt 1.5; D 31-zip-tap
assert_eq "zip entry: Extract first (files_error)" "Extract first" "$(X 31-zip-tap files_error)"
assert_eq "zip entry: no activity started (Files on top)" "$FILES_ACTIVITY" "$(top_activity)"
ZS="$(ring_since "$M")"
assert_eq "zip entry: no recent add and no open line" "0" "$(printf '%s\n' "$ZS" | grep -cE '\[files\] (recent add|open )')"
c6; ensure_start

# ------------------------------------------------------------------------------------------------ music
log "--- music: the indexed MP3, the MP3 with no MediaStore row, the own-launch check"
tracks() { ring_since "$1" | grep -o '\[music\] library ([^)]*): [0-9]* tracks' | tail -1 | grep -o '[0-9]* tracks'; }
music_session() { grep -v session.id.video "$1"; }
M0="$(ring_mark)"; adb shell am start -W -n "$MUSIC" >/dev/null 2>&1; sleep 3
N0="$(tracks "$M0")"
assert_ne "the Songs count before (Music opened once: [music] library …: <n> tracks)" "" "$N0"
record "the library before" "$N0"
c6; ensure_start

M="$(ring_mark)"; tap_row "$QF" 03.mp3 4; S 40-music
assert_eq "03.mp3: top_activity is Music" "$MUSIC" "$(top_activity)"
SL="$(ring_since "$M")"; printf '%s\n' "$SL" | grep -E '\[(files|music)\]' > "$ROW_DIR/40-ring.txt"
assert_contains "03.mp3: [files] recent add" "[files] recent add $QF/03.mp3" "$SL"
absent_in "03.mp3: the shell's own launch is not ignored" "play extra ignored" "$SL"
session_state > "$ROW_DIR/40-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/40-media_session.txt"
TITLE="$(q "content query --uri content://media/external/audio/media --projection _id:title --where _id=$AUD" | sed -n 's/.*title=\(.*\)$/\1/p')"
record "03.mp3: its audio row's title / the music session" "$TITLE / $(music_session "$ROW_DIR/40-session.txt" | xargs)"
assert_contains "03.mp3: the shell's music session is PLAYING that track" "state=PLAYING desc=[$TITLE," "$(music_session "$ROW_DIR/40-session.txt")"
c6; ensure_start

neg() { # name, am-start arguments… — from adb (the shell uid), Music cold
  local name="$1" m sl; shift
  m="$(ring_mark)"; adb shell am start -W "$@" > "$ROW_DIR/neg-$name.am.txt" 2>&1; sleep 3
  sl="$(ring_since "$m")"; printf '%s\n' "$sl" | grep -F '[music]' > "$ROW_DIR/neg-$name.ring.txt"
  session_state > "$ROW_DIR/neg-$name.session.txt"
  assert_eq "own-launch ($name): Music opens" "$MUSIC" "$(top_activity)"
  assert_contains "own-launch ($name): [music] play extra ignored: not the shell" "[music] play extra ignored: not the shell" "$sl"
  absent_in "own-launch ($name): no play file line" "[music] play file" "$sl"
  assert_eq "own-launch ($name): the session is NOT playing" "0" "$(music_session "$ROW_DIR/neg-$name.session.txt" | grep -c 'state=PLAYING')"
  record "own-launch ($name): the music session" "$(music_session "$ROW_DIR/neg-$name.session.txt" | xargs)"
  c6; ensure_start
}
neg id -n "$MUSIC" --el app.tileshell.music.PLAY_ID "$AUD"

assert_eq "qa-hidden.mp3 precondition: the audio collection lists no row with that _display_name BEFORE the tap" "0" "$(norow audio qa-hidden.mp3)"
M="$(ring_mark)"; tap_row "$HID" qa-hidden.mp3 4; S 41-music-hidden; D 41-music-hidden
assert_eq "qa-hidden.mp3: top_activity is Music" "$MUSIC" "$(top_activity)"
SL="$(ring_since "$M")"; printf '%s\n' "$SL" | grep -E '\[(files|music)\]' > "$ROW_DIR/41-ring.txt"
assert_contains "qa-hidden.mp3: [music] play file …/hidden/qa-hidden.mp3 (not in library)" "[music] play file $HID/qa-hidden.mp3 (not in library)" "$SL"
assert_contains "qa-hidden.mp3: [files] recent add" "[files] recent add $HID/qa-hidden.mp3" "$SL"
session_state > "$ROW_DIR/41-session.txt"; adb shell dumpsys media_session > "$ROW_DIR/41-media_session.txt"
assert_contains "qa-hidden.mp3: the shell's music session is PLAYING with the title qa-hidden" "state=PLAYING desc=[qa-hidden," "$(music_session "$ROW_DIR/41-session.txt")"
assert_eq "qa-hidden.mp3: AFTER it the same content query still lists no row (Music did not add it)" "0" "$(norow audio qa-hidden.mp3)"
assert_eq "qa-hidden.mp3: the Songs count is unchanged ([music] library …: $N0)" "$N0" "$(tracks "$M")"
c6; ensure_start
neg uri -n "$MUSIC" --es app.tileshell.music.PLAY_URI "content://app.tileshell.files/root$HID/qa-hidden.mp3"

# ------------------------------------------------------------------------------------------------ the chooser, no handler
log "--- a.txt → the chooser; q.xyz → no handler"
M="$(ring_mark)"; tap_row "$QF" a.txt 3; D 50-chooser; S 50-chooser
assert_eq "a.txt: the system chooser is on top (com.android.intentresolver)" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
adb shell dumpsys activity activities | tr -d '\r' | grep -m1 -E 'Intent \{ act=android.intent.action.CHOOSER' > "$ROW_DIR/50-intent.txt"
record "a.txt: the chooser's intent line / the handlers the AVD offers" "$(xargs < "$ROW_DIR/50-intent.txt" | cut -c1-200) / $(texts 50-chooser | cut -c1-160)"
assert_contains "a.txt: the chooser carries text/plain" "text/plain" "$(cat "$ROW_DIR/50-intent.txt")"
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "a.txt: Back from the chooser returns to Files" "$FILES_ACTIVITY" "$(top_activity)"
M="$(ring_mark)"; tap_row "$QF" q.xyz 2; D 51-xyz; S 51-xyz
SL="$(ring_since "$M")"
assert_eq "q.xyz: still in Files, with the line No app on this phone opens this" "$FILES_ACTIVITY|No app on this phone opens this" "$(top_activity)|$(X 51-xyz files_error)"
NH="$(printf '%s\n' "$SL" | grep -o '\[files\] no handler for [^ ]*' | head -1)"
record "q.xyz: the line (the doc names application/octet-stream; this image's type map knows .xyz)" "$NH"
assert_eq "q.xyz: [files] no handler for chemical/x-xyz" "[files] no handler for chemical/x-xyz" "$NH"
absent_in "q.xyz: nothing was opened, so no recent add" "recent add" "$SL"
c6; ensure_start
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"

# ------------------------------------------------------------------------------------------------ restore
pubvol_down
# MediaProvider leaves a thumbnail in /sdcard/Pictures/.thumbnails/ for a public-volume picture (BUILD-NOTES, the
# open-with builder's fact 7): the platform's file, made by this row's pv.png, removed here by name and recorded.
_snap_sdcard > "$ROW_DIR/.snap-now.txt"
THUMBS="$(grep -vxFf "$ROW_DIR/snap-sdcard-before.txt" "$ROW_DIR/.snap-now.txt" | grep '^/sdcard/Pictures/\.thumbnails/' | xargs)"
record "MediaProvider's thumbnails made during the row (removed)" "[${THUMBS}]"
for t in $THUMBS; do adb shell "rm -f '$t'"; done
files_down
ensure_start

# ------------------------------------------------------------------------------------------------ the JVM rule
log "--- the play extra's rule on the JVM (*MusicPlayExtra*): a foreign authority, even from the shell's own uid"
jvm_gate '*MusicPlayExtra*' '*MusicPlayExtra*' \
  "a uri whose authority is not the shell's FileProvider is refused, even from the shell's own uid" \
  "a foreign caller is ignored with not the shell - the id form and the uri form alike" \
  "the shell's own launch with its own FileProvider's uri plays that file"
assert_gate_apk "end: the installed APK is the gate candidate"
row_end
