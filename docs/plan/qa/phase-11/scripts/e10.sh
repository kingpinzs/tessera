#!/usr/bin/env bash
# E10 — transport controls (T11-38, T11-46), with the row's own fixture: phase 01 E8's playback fixture, Fossify Music
# Player (a third-party player; G-E10-1). The playing player's tile must show its transport strip; a 1000-ms hold on
# play/pause must then fire the control (no burst, no edit mode) — before phase 11 it entered edit mode. On this build
# the strip never stays: L11-1 (the notification feed republishes the playing package's key as null and wipes the
# now-playing face; phases 01 / 10's part, recorded, not fixed). The control sub-step is not run without the strip, so
# no vacuous pass is counted (G-E10-2). The art hold — edit mode and Music's four — is proved on the shell's Music tile.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E10 "a hold on a playing player's transport control fires it; the art hold bursts (L11-1 blocks the strip)"
seed_fixtures
FOSSIFY="app:org.fossify.musicplayer/org.fossify.musicplayer.activities.SplashActivity.Green:0"
FIXDIR="$QROOT/phase-01/MUSIC6-fixtures"
for _ in $(seq 1 20); do adb shell input keyevent 25 >/dev/null 2>&1; done       # muted, as phase 10's rows do
adb shell mkdir -p /sdcard/Music/tessera-qa >/dev/null 2>&1
for f in "$FIXDIR"/*.mp3; do adb push "$f" /sdcard/Music/tessera-qa/ >/dev/null 2>&1; done
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1; sleep 3
restore baseline_layout-player.json
adb shell pm grant org.fossify.musicplayer android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell pm grant org.fossify.musicplayer android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
adb shell am force-stop org.fossify.musicplayer
adb shell am start -W -n org.fossify.musicplayer/.activities.SplashActivity.Green >/dev/null 2>&1; sleep 5
dump_ui "$ROW_DIR/fossify.xml"
python3 - "$ROW_DIR/fossify.xml" Tracks > "$ROW_DIR/.xy" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    if 'text="%s"' % sys.argv[2] in n:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
read -r TX TY < "$ROW_DIR/.xy"; tap_xy "$TX" "$TY"; sleep 2
dump_ui "$ROW_DIR/fossify-tracks.xml"
python3 - "$ROW_DIR/fossify-tracks.xml" "An Ending" > "$ROW_DIR/.xy" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    if 'text="%s"' % sys.argv[2] in n:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
read -r TX TY < "$ROW_DIR/.xy"; MARK="$(ring_mark)"; tap_xy "$TX" "$TY"; sleep 4
state() { adb shell dumpsys media_session | awk '/package=org\.fossify\.musicplayer/ { f = 1 } f && /state=PlaybackState/ { print; exit }' | tr -d '\r'; }
note "Fossify session: $(state)"
assert_contains "the fixture is playing (phase 01 E8's player)" "state=PLAYING(3)" "$(state)"
adb shell input keyevent KEYCODE_HOME; sleep 4; ensure_start_page
qdump "$ROW_DIR/start-playing.xml"; screencap "$ROW_DIR/start-playing.png"
S="$(ring_since "$MARK")"
echo "$S" | grep -E '\[engine\] publish pkg:org\.fossify|\[music\] now playing org\.fossify|\[notif\].*fossify' > "$ROW_DIR/l11-1-sequence.txt"
note "publishes for the player's key: $(grep -c 'publish pkg:org.fossify' "$ROW_DIR/l11-1-sequence.txt") (front=true: $(grep -c 'front=true' "$ROW_DIR/l11-1-sequence.txt"), null: $(grep -c 'source=null' "$ROW_DIR/l11-1-sequence.txt"))"
if [ "$(has_node "$ROW_DIR/start-playing.xml" "tile_control:$FOSSIFY:PLAY_PAUSE")" = yes ]; then
  _verdict PASS "the playing player's tile shows its transport strip" "tile_control:…:PLAY_PAUSE present"
  read -r X Y <<< "$(center "$ROW_DIR/start-playing.xml" "tile_control:$FOSSIFY:PLAY_PAUSE")"
  assert_ne "control centre found" "" "$X"
  MARK="$(ring_mark)"; adb shell input swipe "$X" "$Y" "$X" "$Y" 1000; sleep 1.5
  qdump "$ROW_DIR/control-held.xml"
  S="$(ring_since "$MARK")"
  assert_contains "the control fired (no launch)" "[tile_control] tile=$FOSSIFY PLAY_PAUSE (no launch)" "$S"
  assert_absent "no hold (edit mode not entered)" "[edit] hold" "$S"
  assert_absent "no burst" "[quick] burst on" "$S"
  assert_contains "the player's session is PAUSED" "state=PAUSED(2)" "$(state)"
else
  _verdict FAIL "the playing player's tile shows its transport strip — BLOCKED by L11-1 (the key republished as null)" \
    "front=true $(grep -c 'front=true' "$ROW_DIR/l11-1-sequence.txt") then null $(grep -c 'source=null' "$ROW_DIR/l11-1-sequence.txt") (l11-1-sequence.txt)"
  note "the control-hold sub-step is NOT run without the strip (no vacuous pass, G-E10-2); resume point: here, once L11-1 is fixed"
fi
adb shell am force-stop org.fossify.musicplayer
restore baseline_layout.json
qdump "$ROW_DIR/music-rest.xml"
read -r l t r b <<< "$(bounds "$ROW_DIR/music-rest.xml" tile:slot:MUSIC)"
AX=$(( (l + r) / 2 )); AY=$(( t + (b - t) / 4 ))
MARK="$(ring_mark)"; hold_down "$AX" "$AY"; sleep 1.0; qdump "$ROW_DIR/art-held.xml"; hold_up "$AX" "$AY"; sleep 0.8
assert_eq "the Music tile's art: edit mode on" yes "$(has_node "$ROW_DIR/art-held.xml" edit_disc:unpin)"
assert_eq "the Music tile's art: Music's four satellites" "Songs,Albums,Artists,Playlists," "$(for i in 0 1 2 3; do node_text "$ROW_DIR/art-held.xml" "quick_sat_label:$i"; done | tr '\n' ',')"
c6
row_end
