#!/usr/bin/env bash
# L11-1 — the fix's own gate row (review/2026-09-24-L11-1-fix-plan.md; Jeremy "(a)"): what an app's tile shows is
# decided in one place — a playing session's face > the Live Tile API queue > notifications > a paused track's face.
# (a) the API queue shows, and a notification cannot displace it; clearing the queue lets the notification show;
# (b) phase 01 E8's player (Fossify) playing: its tile keeps the transport strip across notification updates, and every
#     engine publish for its key after the face says it shows the playing session; a pause takes the strip away;
# (c) the same with the shell's own player on the Music tile; (d) an uninstalled player keeps nothing (F-2);
# plus phase 01 E15 / E17 and phase 02 E5 (secondary tiles, F-1) re-run on this build.
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

log "--- (d) the fix review's F-2: an uninstalled player keeps nothing; a reinstall inherits no face ---"
# Fossify is sideloaded (phase 01 E04's fixture), so an uninstall removes it: the reinstall is from its own APK, copied
# off the device first (a system-app "install-existing" cannot bring it back — the first run of this sub-step, 09-24).
APKDIR="$(mktemp -d)"
adb pull "$(adb shell pm path org.fossify.musicplayer | sed -n 's/^package://p' | tr -d '\r' | head -1)" "$APKDIR/fossify.apk" >/dev/null 2>&1
assert_eq "the player's APK is phase 01 E04's fixture (copied off the device to reinstall it)" \
  b5d0ce367544df1ac162adf71d642878c5b963a304ca0ddf8f8556f07bbba79b "$(sha256sum "$APKDIR/fossify.apk" 2>/dev/null | cut -c1-64)"
MARK="$(ring_mark)"
adb shell pm uninstall -k org.fossify.musicplayer >/dev/null 2>&1; sleep 4
assert_eq "the player is uninstalled" "" "$(adb shell pm path org.fossify.musicplayer 2>/dev/null | tr -d '\r')"
S="$(ring_since "$MARK")"
assert_contains "the engine forgot the package, every source" "[engine] forget pkg:org.fossify.musicplayer (every source)" "$S"
assert_contains "MusicFeed dropped its remembered track" "[music] forgot org.fossify.musicplayer's track" "$S"
adb install -r -t "$APKDIR/fossify.apk" >/dev/null 2>&1; sleep 3
rm -rf "$APKDIR"
assert_contains "the player is installed again" "package:" "$(adb shell pm path org.fossify.musicplayer 2>/dev/null)"
adb shell pm grant org.fossify.musicplayer android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell pm grant org.fossify.musicplayer android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
restore baseline_layout-player.json
T=""; for i in 1 2 3 4 5 6; do qdump "$ROW_DIR/reinstalled-$i.xml"; T="$T | $(tile_texts "$ROW_DIR/reinstalled-$i.xml" "tile:$FOSSIFY" | sed 's/.*texts=//')"; sleep 2; done
note "the re-pinned player's tile over 6 samples: $T"
assert_absent "the re-pinned tile resolves the reinstalled player" "Tap to choose" "$T"
assert_absent "the reinstalled player's tile shows no inherited track face" "An Ending" "$T"
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
bash "$QROOT/phase-01/scripts/e15.sh" "$ROW_DIR/p01-E15" > "$ROW_DIR/p01-E15/console.txt" 2>&1; rc15=$?
bash "$QROOT/phase-01/scripts/e17.sh" "$ROW_DIR/p01-E17" > "$ROW_DIR/p01-E17/console.txt" 2>&1; rc17=$?
assert_eq "phase 01 e15.sh exits 0" 0 "$rc15"
assert_eq "phase 01 e17.sh exits 0" 0 "$rc17"
# Phase 01's E15 and E17 print what they saw and carry no verdict of their own; their acceptance is asserted here.
# faces <section>: the face lines E15 printed under "<section> faces seen:" (up to the next line that is not a face).
faces() { awk -v h="$1 faces seen:" '$0==h{f=1;next} f&&/^    [^ 0-9]/{print;next} f{exit}' "$ROW_DIR/p01-E15/E15.txt"; }
assert_contains "E15: the API's own text reached the tile" "Meeting at 4" "$(faces update)"
assert_contains "E15: the queue shows its items" "Queue item" "$(faces queue)"
assert_absent "E15: tile.clear takes the queue off the tile" "Queue item" "$(faces cleared)"
assert_contains "E15: the expiring update shows" "Expires in 8 s" "$(faces expiring)"
assert_absent "E15: the update is gone once it expires" "Expires in" "$(faces expired)"
assert_contains "E15: the expiry was the alarm's" "[livetile] expire $A_PKG" "$(cat "$ROW_DIR/p01-E15/E15.txt")"
# E17: the badge by phase 01's order (the API > the legacy broadcast > the notification count), step by step.
assert_eq "E17: badge order 7, 3, 5, 3, 7, none" "7 3 5 3 7 -" \
  "$(for st in notification_7 legacy_3 api_5 after_api_clear after_legacy_clear after_notification_cancel; do
       v="$(grep -m1 "^$st: dump badge node" "$ROW_DIR/p01-E17/E17.txt" | sed -E 's/.*badge node \[([0-9]*)\].*/\1/')"; echo "${v:--}"; done | tr '\n' ' ' | sed 's/ $//')"

log "--- the fix review's F-1: phase 02 E5, secondary tiles, on this build (the rescan no longer clears their content) ---"
mkdir -p "$ROW_DIR/p02-E05"
# From phase 02's own baseline: E5's step 6 uninstalls tileclient-a (its owner fixture) and then restores the layout it
# saved at the start, which cannot come up with tileclient-a's tile in it (the first run of this sub-step, 2026-09-24).
restore baseline_layout-pre-11.json
bash "$QROOT/phase-02/scripts/e5.sh" "$ROW_DIR/p02-E05" > "$ROW_DIR/p02-E05/console.txt" 2>&1; rc=$?
seed_fixtures            # E5 uninstalled tileclient-a
restore baseline_layout.json
note "phase 02 e5.sh exit $rc; verdict: $(grep -h '^---- ' "$ROW_DIR"/p02-E05/*.txt 2>/dev/null | tail -1)"
assert_eq "phase 02 E5 (secondary tiles) exits 0 on this build" 0 "$rc"
row_end
