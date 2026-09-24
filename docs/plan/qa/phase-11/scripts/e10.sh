#!/usr/bin/env bash
# E10 — the Music tile's transport controls (T11-38, T11-46): a 1000-ms hold on the play/pause control opens no
# burst and enters no edit mode — the control fires as a tap does (before phase 11 it entered edit mode and the
# control never fired) — and the same hold on the tile's art opens edit mode and Music's four satellites.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E10 "a hold on a transport control fires the control; a hold on the art bursts with Music's four"
seed_fixtures
restore baseline_layout.json
FIXDIR="$QROOT/phase-01/MUSIC6-fixtures"
for _ in $(seq 1 20); do adb shell input keyevent 25 >/dev/null 2>&1; done       # muted: phase 10's rows do the same
adb shell mkdir -p /sdcard/Music/tessera-qa >/dev/null 2>&1
for f in "$FIXDIR"/*.mp3; do adb push "$f" /sdcard/Music/tessera-qa/ >/dev/null 2>&1; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 3
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell am start -W -n app.tileshell/.music.MusicActivity --es pivot SONGS >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/songs.xml"
SONG="$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
note "playing $SONG"
tap_node "$ROW_DIR/songs.xml" "$SONG"; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
qdump "$ROW_DIR/start-playing.xml"; screencap "$ROW_DIR/start-playing.png"
assert_eq "the Music tile shows its controls" yes "$(has_node "$ROW_DIR/start-playing.xml" tile_control:slot:MUSIC:PLAY_PAUSE)"
state() { adb shell dumpsys media_session | awk '/package=app\.tileshell/ { f = 1 } f && /state=PlaybackState/ { print; exit }' | tr -d '\r'; }
note "session before: $(state)"
read -r X Y <<< "$(center "$ROW_DIR/start-playing.xml" tile_control:slot:MUSIC:PLAY_PAUSE)"
MARK="$(ring_mark)"
adb shell input swipe "$X" "$Y" "$X" "$Y" 1000; sleep 1.5
qdump "$ROW_DIR/control-held.xml"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/control-held.xml" quick_burst)"
assert_eq "no edit mode" no "$(grep -q 'resource-id="edit_disc' "$ROW_DIR/control-held.xml" && echo yes || echo no)"
S="$(ring_since "$MARK")"
assert_contains "the control fired (no launch)" "[tile_control] tile=slot:MUSIC PLAY_PAUSE (no launch)" "$S"
assert_absent "no hold line" "[edit] hold" "$S"
note "session after: $(state)"
assert_contains "the shell's session is PAUSED" "state=PAUSED(2)" "$(state)"
assert_contains "StartActivity still in front (nothing launched)" "app.tileshell/.StartActivity" "$(resumed)"
read -r l t r b <<< "$(bounds "$ROW_DIR/control-held.xml" tile:slot:MUSIC)"
AX=$(( (l + r) / 2 )); AY=$(( t + (b - t) / 4 ))          # the art, above the strip
MARK="$(ring_mark)"
hold_down "$AX" "$AY"; sleep 1.0; qdump "$ROW_DIR/art-held.xml"; hold_up "$AX" "$AY"; sleep 0.8
assert_eq "the art: edit mode on" yes "$(has_node "$ROW_DIR/art-held.xml" edit_disc:unpin)"
assert_eq "the art: Music's four satellites" "Songs,Albums,Artists,Playlists," "$(for i in 0 1 2 3; do node_text "$ROW_DIR/art-held.xml" "quick_sat_label:$i"; done | tr '\n' ',')"
c6
row_end
