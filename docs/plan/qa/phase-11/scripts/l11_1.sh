#!/usr/bin/env bash
# L11-1 — the fix's own gate row (review/2026-09-24-L11-1-fix-plan.md; Jeremy "(a)"): what an app's tile shows is
# decided in one place — a playing session's face > the Live Tile API queue > notifications > a paused track's face.
# (a) the API queue shows, and a notification cannot displace it; clearing the queue lets the notification show;
# (b) phase 01 E8's player (Fossify) playing: its tile keeps the transport strip across notification updates, and every
#     engine publish for its key after the face says it shows the playing session; a pause takes the strip away;
# (c) the same with the shell's own player on the Music tile.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin L11-1 "one arbiter: the API beats notifications; a playing face survives notification updates"
seed_fixtures
music_fixtures_in
adb shell pm grant "$A_PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
restore baseline_layout.json
verb_a() { adb shell am start -W -n "$A_PKG/app.tileshell.testclient.VerbActivity" "$@" >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3; }
tile_texts() { python3 "$QROOT/phase-01/scripts/nodes.py" "$1" "$2" 2>/dev/null; }

log "--- (a) the Live Tile API queue over notifications ---"
MARK="$(ring_mark)"
verb_a --es verb tile.update --es text APIqueue
verb_a --es verb notify --ei number 2 --es title "Note title" --es text "note text"
ensure_start_page
# The tile flips between its logo and its live faces on its own timer (R3 A8), so its texts are sampled across flips,
# as phase 01 E15's faces() does: the API's face in at least one sample, the notification's in none.
T=""; for i in 1 2 3 4 5 6 7 8; do qdump "$ROW_DIR/api-over-notif-$i.xml"; T="$T | $(tile_texts "$ROW_DIR/api-over-notif-$i.xml" "tile:$A_KEY" | sed 's/.*texts=//')"; sleep 2; done
note "tile texts over 8 samples: $T"
S="$(ring_since "$MARK")"
assert_contains "the notification's publish leaves the API showing" "publish pkg:$A_PKG from=notifications" "$(echo "$S" | grep 'from=notifications' | grep -- '-> shows api')"
assert_contains "the tile shows the API text" "APIqueue" "$T"
assert_absent "…not the notification" "Note title" "$T"
MARK="$(ring_mark)"
verb_a --es verb tile.clear
sleep 6; ensure_start_page; qdump "$ROW_DIR/notif-after-clear.xml"
S="$(ring_since "$MARK")"
assert_contains "clearing the queue lets the notification show" "-> shows notifications" "$(echo "$S" | grep "publish pkg:$A_PKG from=api")"
verb_a --es verb notify.cancel
c6

log "--- (b) Fossify playing: the strip survives notification updates ---"
restore baseline_layout-player.json
MARK="$(ring_mark)"
fossify_play
assert_contains "Fossify is playing" "state=PLAYING(3)" "$(session_state org.fossify.musicplayer)"
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
for i in 1 2 3; do
  qdump "$ROW_DIR/fossify-$i.xml"
  assert_eq "strip present, dump $i (+$(( (i - 1) * 3 )) s)" yes "$(has_node "$ROW_DIR/fossify-$i.xml" "tile_control:$FOSSIFY:PLAY_PAUSE")"
  sleep 3
done
screencap "$ROW_DIR/fossify-playing.png"
S="$(ring_since "$MARK")"
echo "$S" | grep "publish pkg:org.fossify.musicplayer" > "$ROW_DIR/fossify-publishes.txt"
after="$(awk '/-> shows music \(playing\)/{f=1} f' "$ROW_DIR/fossify-publishes.txt")"
note "publishes after the face: $(echo "$after" | grep -c publish) ($(echo "$after" | grep -c 'from=notifications') from notifications)"
assert_ne "the notification feed published while it played (the race is exercised)" 0 "$(echo "$after" | grep -c 'from=notifications')"
assert_eq "every publish after the face still shows the playing session" "$(echo "$after" | grep -c publish)" "$(echo "$after" | grep -c -- '-> shows music (playing)')"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 3
qdump "$ROW_DIR/fossify-paused.xml"
assert_contains "Fossify paused" "state=PAUSED(2)" "$(session_state org.fossify.musicplayer)"
assert_eq "paused: the strip goes (item 3's ruling)" no "$(has_node "$ROW_DIR/fossify-paused.xml" "tile_control:$FOSSIFY:PLAY_PAUSE")"
assert_absent "paused: nothing shows the playing session any more" "-> shows music (playing)" "$(ring_since "$MARK" | grep 'publish pkg:org.fossify')"
adb shell am force-stop org.fossify.musicplayer

log "--- (c) the shell's own player on the Music tile ---"
restore baseline_layout.json
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
MARK="$(ring_mark)"
adb shell am start -W -n app.tileshell/.music.MusicActivity --es pivot SONGS >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/songs.xml"
tap_node "$ROW_DIR/songs.xml" "$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
for i in 1 2 3; do
  qdump "$ROW_DIR/music-$i.xml"
  assert_eq "Music tile strip present, dump $i (+$(( (i - 1) * 3 )) s)" yes "$(has_node "$ROW_DIR/music-$i.xml" tile_control:slot:MUSIC:PLAY_PAUSE)"
  sleep 3
done
screencap "$ROW_DIR/music-playing.png"
S="$(ring_since "$MARK")"
after="$(echo "$S" | grep 'publish pkg:app.tileshell ' | awk '/-> shows music \(playing\)/{f=1} f')"
note "publishes after the face: $(echo "$after" | grep -c publish) ($(echo "$after" | grep -c 'from=notifications') from notifications)"
assert_ne "the shell's own media notification published while it played" 0 "$(echo "$after" | grep -c 'from=notifications')"
assert_eq "every publish after the face still shows the playing session" "$(echo "$after" | grep -c publish)" "$(echo "$after" | grep -c -- '-> shows music (playing)')"
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 3
qdump "$ROW_DIR/music-paused.xml"
assert_eq "paused: the Music tile's strip goes" no "$(has_node "$ROW_DIR/music-paused.xml" tile_control:slot:MUSIC:PLAY_PAUSE)"
c6

log "--- phase 01 E15 / E17 captures on this build (their parts changed: the API and the notification feed) ---"
mkdir -p "$ROW_DIR/p01-E15" "$ROW_DIR/p01-E17"
restore baseline_layout.json
bash "$QROOT/phase-01/scripts/e15.sh" "$ROW_DIR/p01-E15" > "$ROW_DIR/p01-E15/console.txt" 2>&1; note "phase 01 e15.sh exit $?"
bash "$QROOT/phase-01/scripts/e17.sh" "$ROW_DIR/p01-E17" > "$ROW_DIR/p01-E17/console.txt" 2>&1; note "phase 01 e17.sh exit $?"
assert_contains "E15: the API's own text reached the tile" "Meeting at 4" "$(cat "$ROW_DIR/p01-E15/E15.txt")"
restore baseline_layout.json
row_end
