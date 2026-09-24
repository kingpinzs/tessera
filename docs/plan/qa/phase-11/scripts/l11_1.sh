#!/usr/bin/env bash
# L11-1 — the fix's own gate row (review/2026-09-24-L11-1-fix-plan.md; Jeremy "(a)"): what an app's tile shows is
# decided in one place — a playing session's face > the Live Tile API queue > notifications > a paused track's face.
# (a) the API queue shows, and a notification cannot displace it; clearing the queue lets the notification show;
# (b) phase 01 E8's player (Fossify) playing: its tile keeps the transport strip across notification updates, and every
#     engine publish for its key after the face says it shows the playing session; a pause takes the strip away;
# (c) the same with the shell's own player on the Music tile; (d) a player uninstalled WHILE PLAYING keeps nothing, and
# its reinstall, re-pinned in the same shell process, inherits no face (F-2; the re-judge's R1-1 / R2-3); (e) a secondary
# tile's API content survives a notification-listener rescan (F-1; R2-5); plus the JVM tests' own results (R2-6), phase
# 01 E15 / E17 asserted, and phase 02 E5 re-run as a regression on this build.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin L11-1 "one arbiter: the API beats notifications; a playing face survives notification updates"
seed_fixtures
music_fixtures_in
adb shell pm grant "$A_PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
restore baseline_layout.json
verb_a() { adb shell am start -W -n "$A_PKG/app.tileshell.testclient.VerbActivity" "$@" >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3; }
tile_texts() { python3 "$QROOT/phase-01/scripts/nodes.py" "$1" "$2" 2>/dev/null; }

log "--- the fix's JVM tests, from this run's unit results (run_all keeps them in UNIT-results/ before any row) ---"
TSP="$QA11/UNIT-results/TEST-app.tileshell.tiles.engine.TileSourcePrecedenceTest.xml"
cp "$TSP" "$ROW_DIR/" 2>/dev/null
note "results: the XML's own timestamp $(grep -oE 'timestamp="[^"]*"' "$TSP" 2>/dev/null | head -1) (run_all cleans the results before its unit run)"
assert_contains "TileSourcePrecedenceTest: 12 tests, 0 failures, 0 errors" 'tests="12" skipped="0" failures="0" errors="0"' "$(head -3 "$TSP" 2>/dev/null)"
for t in notificationNullsNoLongerWipeAPlayingFace aSecondaryTilesKeyIsNeverClearedByTheArbiter aForgottenPackageKeepsNothing; do
  assert_contains "JVM: $t ran" "testcase name=\"$t\"" "$(cat "$TSP" 2>/dev/null)"
done

log "--- (a) the Live Tile API queue over notifications ---"
MARK="$(ring_mark)"
verb_a --es verb tile.update --es text APIqueue
# One word each: adb shell re-splits extras on spaces (the re-judge, R2-4).
verb_a --es verb notify --ei number 2 --es title NoteTitle --es text NoteText
ensure_start_page
# The tile flips between its logo and its live faces on its own timer (R3 A8), so its texts are sampled across flips,
# as phase 01 E15's faces() does: the API's face in at least one sample, the notification's in none.
T=""; for i in 1 2 3 4 5 6 7 8; do qdump "$ROW_DIR/api-over-notif-$i.xml"; T="$T | $(tile_texts "$ROW_DIR/api-over-notif-$i.xml" "tile:$A_KEY" | sed 's/.*texts=//')"; sleep 2; done
note "tile texts over 8 samples: $T"
S="$(ring_since "$MARK")"
assert_contains "the notification's publish leaves the API showing" "publish pkg:$A_PKG from=notifications" "$(echo "$S" | grep 'from=notifications' | grep -- '-> shows api')"
assert_contains "the tile shows the API text" "APIqueue" "$T"
assert_absent "…not the notification" "NoteTitle" "$T"
MARK="$(ring_mark)"
verb_a --es verb tile.clear
sleep 6; ensure_start_page
N=""; for i in 1 2 3 4 5 6; do qdump "$ROW_DIR/notif-after-clear-$i.xml"; N="$N | $(tile_texts "$ROW_DIR/notif-after-clear-$i.xml" "tile:$A_KEY" | sed 's/.*texts=//')"; sleep 2; done
note "tile texts after the clear over 6 samples: $N"
S="$(ring_since "$MARK")"
assert_contains "clearing the queue lets the notification show" "-> shows notifications" "$(echo "$S" | grep "publish pkg:$A_PKG from=api")"
assert_contains "…and the tile shows the notification (the control for the absence above)" "NoteTitle" "$N"
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
assert_contains "the playing face shows the track (phase 01 E8's playback face)" "An Ending" \
  "$(for i in 1 2 3; do tile_texts "$ROW_DIR/fossify-$i.xml" "tile:$FOSSIFY"; done)"
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

log "--- (d) F-2 and R1-1: a player uninstalled WHILE PLAYING keeps nothing; its reinstall inherits no face ---"
# Fossify is sideloaded (phase 01 E04's fixture), so an uninstall removes it: the reinstall is from its own APK, copied
# off the device first (a system-app "install-existing" cannot bring it back — the first run of this sub-step, 09-24).
# Everything F-2 guards lives in the shell's memory, so the shell keeps ONE process from the uninstall to the read, and
# the reinstall is re-pinned from the app list, not by a layout restore (which restarts it; the re-judge, R2-3).
APKDIR="$(mktemp -d)"
adb pull "$(adb shell pm path org.fossify.musicplayer | sed -n 's/^package://p' | tr -d '\r' | head -1)" "$APKDIR/fossify.apk" >/dev/null 2>&1
assert_eq "the player's APK is phase 01 E04's fixture (copied off the device to reinstall it)" \
  b5d0ce367544df1ac162adf71d642878c5b963a304ca0ddf8f8556f07bbba79b "$(sha256sum "$APKDIR/fossify.apk" 2>/dev/null | cut -c1-64)"
restore baseline_layout-player.json
fossify_play
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
assert_contains "Fossify is playing when it is uninstalled" "state=PLAYING(3)" "$(session_state org.fossify.musicplayer)"
PID0="$(adb shell pidof app.tileshell | tr -d '\r')"
MARK="$(ring_mark)"
adb shell pm uninstall -k org.fossify.musicplayer >/dev/null 2>&1; sleep 4
assert_eq "the player is uninstalled" "" "$(adb shell pm path org.fossify.musicplayer 2>/dev/null | tr -d '\r')"
S="$(ring_since "$MARK")"
assert_contains "the engine forgot the package, every source" "[engine] forget pkg:org.fossify.musicplayer (every source)" "$S"
assert_contains "MusicFeed dropped its remembered track" "[music] forgot org.fossify.musicplayer's track" "$S"
echo "$S" | grep -E "\[engine\] (publish|forget) pkg:org\.fossify\.musicplayer |\[music\].*fossify" > "$ROW_DIR/uninstall-sequence.txt"
note "the player's engine lines across the uninstall: $(grep -c . "$ROW_DIR/uninstall-sequence.txt") (uninstall-sequence.txt)"
assert_absent "after the forget, the player's key ends with nothing shown" "-> shows music" \
  "$(grep '\[engine\] ' "$ROW_DIR/uninstall-sequence.txt" | tail -1)"
# 9f790b7 (the re-judge's R1-1): MusicFeed's forget publishes its own null for the package on main, AFTER the engine's forget;
# only that commit writes this line, so the row fails without it. Whether a session died INSIDE the window between the two
# (the race itself) depends on timing adb cannot force: recorded from uninstall-sequence.txt, not asserted (round 2, EV-2).
assert_contains "9f790b7's own null publish follows the engine's forget" "from=music faces=0" \
  "$(awk '/\[engine\] forget pkg:org.fossify.musicplayer/{f=1; next} f' "$ROW_DIR/uninstall-sequence.txt" | grep '\[engine\] publish')"
note "the race window itself: $(awk '/\[engine\] forget/{f=1; next} f && /from=music faces=1/{x=1} END{print (x ? "a music face was published inside it" : "not produced in this run (the session died before the forget)")}' "$ROW_DIR/uninstall-sequence.txt")"
adb install -r -t "$APKDIR/fossify.apk" >/dev/null 2>&1; sleep 3
rm -rf "$APKDIR"
assert_contains "the player is installed again" "package:" "$(adb shell pm path org.fossify.musicplayer 2>/dev/null)"
adb shell pm grant org.fossify.musicplayer android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
adb shell pm grant org.fossify.musicplayer android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
# Re-pin from the app list (E9's route): hold its row, Pin to Start.
ensure_start_page; adb shell input swipe 900 1200 150 1200 250; sleep 2
APPROW=""; for i in 1 2 3 4 5 6 7 8 9 10; do
  qdump "$ROW_DIR/.applist.xml"; APPROW="$(center "$ROW_DIR/.applist.xml" applist_row:org.fossify.musicplayer 2>/dev/null)"
  [ -n "$APPROW" ] && break; adb shell input swipe 540 1800 540 700 300; sleep 1.2
done
assert_ne "the reinstalled player is in the app list" "" "$APPROW"
read -r PX PY <<< "$APPROW"; hold "$PX" "$PY" 1.1; sleep 0.8
qdump "$ROW_DIR/repin-menu.xml"; tap_node "$ROW_DIR/repin-menu.xml" applist_menu_pin; sleep 2
ensure_start_page
T=""; for i in 1 2 3 4 5 6; do
  qdump "$ROW_DIR/reinstalled-$i.xml"
  if [ "$(has_node "$ROW_DIR/reinstalled-$i.xml" "tile:$FOSSIFY")" = no ]; then adb shell input swipe 540 1600 540 700 400; sleep 1.5; qdump "$ROW_DIR/reinstalled-$i.xml"; fi
  T="$T | $(tile_texts "$ROW_DIR/reinstalled-$i.xml" "tile:$FOSSIFY" | sed 's/.*texts=//')"; sleep 2
done
note "the re-pinned player's tile over 6 samples: $T"
assert_eq "one shell process from the uninstall to the read" "$PID0" "$(adb shell pidof app.tileshell | tr -d '\r')"
assert_contains "the re-pinned tile shows the reinstalled player" "Music Player" "$T"
assert_absent "the reinstalled player's tile shows no inherited track face" "An Ending" "$T"
adb shell am force-stop org.fossify.musicplayer
c6

log "--- (c) the shell's own player on the Music tile ---"
restore baseline_layout.json
adb shell pm grant app.tileshell android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1
MARK="$(ring_mark)"
adb shell am start -W -n app.tileshell/.music.MusicActivity --es pivot SONGS >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/songs.xml"
tap_node "$ROW_DIR/songs.xml" "$(grep -o 'resource-id="music_song:[0-9]*"' "$ROW_DIR/songs.xml" | head -1 | sed 's/resource-id="//; s/"$//')"; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3; ensure_start_page
assert_contains "the shell's player is playing" "state=PLAYING(3)" "$(session_state app.tileshell)"
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
assert_contains "the shell's player paused" "state=PAUSED(2)" "$(session_state app.tileshell)"
assert_eq "paused: the Music tile's strip goes" no "$(has_node "$ROW_DIR/music-paused.xml" tile_control:slot:MUSIC:PLAY_PAUSE)"
c6

log "--- phase 01 E15 / E17 captures on this build (their parts changed: the API and the notification feed) ---"
mkdir -p "$ROW_DIR/p01-E15" "$ROW_DIR/p01-E17"
restore baseline_layout.json
note "phase 01 e15.sh blob $(git -C "$QROOT" hash-object "$QROOT/phase-01/scripts/e15.sh" | cut -c1-8); e17.sh blob $(git -C "$QROOT" hash-object "$QROOT/phase-01/scripts/e17.sh" | cut -c1-8)"
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

log "--- (e) F-1: a secondary tile's API content survives a notification-listener rescan ---"
restore baseline_layout.json
adb shell am start -n "$A_PKG/app.tileshell.testclient.VerbActivity" --es verb secondary.requestCreate --es tileId l111 \
  --es displayName L111 --es arguments "x=1" --es size medium >/dev/null 2>&1
sleep 2.5; adb shell input keyevent KEYCODE_HOME; sleep 3
qdump "$ROW_DIR/sec-prompt.xml"; tap_node "$ROW_DIR/sec-prompt.xml" secondary_pin_accept; sleep 2
SEC="secondary:$A_PKG:l111"
assert_contains "the secondary tile is pinned" "$SEC" "$(layout_json | python3 -c 'import json,sys; print(" ".join(o["key"] for o in json.load(sys.stdin)["order"]))')"
verb_a --es verb tile.update --es tileId l111 --es text SecContent
ensure_start_page
sec_texts() { # samples the secondary tile's texts ($1 = tag), scrolling to it (it is appended at the end of Start)
  local i out=""
  for i in 1 2 3 4 5 6; do
    qdump "$ROW_DIR/$1-$i.xml"
    if [ "$(has_node "$ROW_DIR/$1-$i.xml" "tile:$SEC")" = no ]; then adb shell input swipe 540 1600 540 700 400; sleep 1.5; qdump "$ROW_DIR/$1-$i.xml"; fi
    out="$out | $(tile_texts "$ROW_DIR/$1-$i.xml" "tile:$SEC" | sed 's/.*texts=//')"; sleep 2
  done
  echo "$out"
}
B="$(sec_texts sec-before)"; note "the secondary tile before the rescan: $B"
assert_contains "the secondary tile shows its API content (the control)" "SecContent" "$B"
PID0="$(adb shell pidof app.tileshell | tr -d '\r')"
MARK="$(ring_mark)"
adb shell cmd notification disallow_listener app.tileshell/.feeds.TileNotificationListener; sleep 2
adb shell cmd notification allow_listener app.tileshell/.feeds.TileNotificationListener; sleep 4
assert_contains "the listener reconnected and rescanned" "[notif] rescan (connected)" "$(ring_since "$MARK")"
A="$(sec_texts sec-after)"; note "the secondary tile after the rescan: $A"
assert_eq "one shell process across the rescan" "$PID0" "$(adb shell pidof app.tileshell | tr -d '\r')"
assert_contains "the secondary tile still shows its API content after the rescan (F-1)" "SecContent" "$A"
c6

log "--- phase 02 E5 (secondary tiles) re-run on this build: a regression check, not F-1's proof ((e) is) ---"
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
