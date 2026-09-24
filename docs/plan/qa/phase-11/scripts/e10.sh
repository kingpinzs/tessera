#!/usr/bin/env bash
# E10 — transport controls (T11-38, T11-46), with the row's own fixture: phase 01 E8's playback fixture, Fossify Music
# Player (a third-party player; G-E10-1). The playing player's tile must show its transport strip; a 1000-ms hold on
# play/pause must then fire the control (no burst, no edit mode) — before phase 11 it entered edit mode. The strip used
# to be wiped by every notification update (L11-1, fixed in ca73663: one arbiter for an app's tile content); without the
# strip the control sub-step is not run, so no vacuous pass is counted (G-E10-2). The art hold — edit mode and Music's
# four — is proved on the shell's Music tile WHILE it shows its strip (its player playing), above the strip: the
# consumed-DOWN fix is exactly what could swallow it (the re-judge, R2-2 / R1-5).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E10 "a hold on a playing player's transport control fires it; the art hold over a showing strip bursts"
seed_fixtures
music_fixtures_in
restore baseline_layout-player.json
MARK="$(ring_mark)"
fossify_play
state() { session_state org.fossify.musicplayer; }
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
  assert_eq "control dump: Start's page, no burst, no edit discs" "yes no no" \
    "$(has_node "$ROW_DIR/control-held.xml" start_page) $(has_node "$ROW_DIR/control-held.xml" quick_burst) $(has_node "$ROW_DIR/control-held.xml" edit_disc:unpin)"
else
  _verdict FAIL "the playing player's tile shows its transport strip — BLOCKED by L11-1 (the key republished as null)" \
    "front=true $(grep -c 'front=true' "$ROW_DIR/l11-1-sequence.txt") then null $(grep -c 'source=null' "$ROW_DIR/l11-1-sequence.txt") (l11-1-sequence.txt)"
  note "the control-hold sub-step is NOT run without the strip (no vacuous pass, G-E10-2); resume point: here, once L11-1 is fixed"
fi
adb shell am force-stop org.fossify.musicplayer
restore baseline_layout.json
log "--- the art hold on the Music tile while it shows its strip (the shell's own player playing) ---"
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell am start -W -n app.tileshell/.music.MusicActivity --es pivot SONGS >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/songs.xml"
tap_node "$ROW_DIR/songs.xml" "$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
qdump "$ROW_DIR/music-playing.xml"; screencap "$ROW_DIR/music-playing.png"
assert_contains "the shell's player is playing" "state=PLAYING(3)" "$(session_state app.tileshell)"
assert_eq "the Music tile shows its strip" yes "$(has_node "$ROW_DIR/music-playing.xml" tile_control:slot:MUSIC:PLAY_PAUSE)"
read -r l t r b <<< "$(bounds "$ROW_DIR/music-playing.xml" tile:slot:MUSIC)"
read -r cl ct cr cb <<< "$(bounds "$ROW_DIR/music-playing.xml" tile_controls:slot:MUSIC)"
AX=$(( (l + r) / 2 )); AY=$(( t + (ct - t) / 2 ))
note "Music tile [$l,$t][$r,$b], strip [$cl,$ct][$cr,$cb]; the art hold at ($AX,$AY)"
inside="$(python3 - "$ROW_DIR/music-playing.xml" "$AX" "$AY" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); x, y = int(sys.argv[2]), int(sys.argv[3])
hits = [m.group(1) for m in re.finditer(r'resource-id="(tile_control:[^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if int(m.group(2)) <= x < int(m.group(4)) and int(m.group(3)) <= y < int(m.group(5))]
print(" ".join(hits) or "none")
PY
)"
assert_eq "the art point is inside the tile, above the strip, on no control" "yes none" "$([ "$AY" -gt "$t" ] && [ "$AY" -lt "$ct" ] && echo yes || echo no) $inside"
MARK="$(ring_mark)"; hold_down "$AX" "$AY"; sleep 1.0; qdump "$ROW_DIR/art-held.xml"; hold_up "$AX" "$AY"; sleep 0.8
S="$(ring_since "$MARK")"
assert_contains "the art hold entered edit mode (ring)" "[edit] hold" "$S"
assert_absent "the art hold fired no control" "[tile_control]" "$S"
assert_eq "the Music tile's art: edit mode on" yes "$(has_node "$ROW_DIR/art-held.xml" edit_disc:unpin)"
assert_eq "the Music tile's art: Music's four satellites" "Songs,Albums,Artists,Playlists," "$(for i in 0 1 2 3; do node_text "$ROW_DIR/art-held.xml" "quick_sat_label:$i"; done | tr '\n' ',')"
c6
row_end
