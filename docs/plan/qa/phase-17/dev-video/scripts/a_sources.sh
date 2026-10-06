#!/usr/bin/env bash
# Phase 17 development proof, build task 7 (brief A): the VIEW contract's sources and their failure states — an http
# source playing, airplane mode, a 404, an unsupported scheme, a truncated and an empty file — and the :video process
# killed without taking Start down. Restores: airplane mode off, the pushed videos removed, the fixture stopped.
. "$(dirname "$0")/v17.sh"
row_begin A_SOURCES "the player's sources: http, unreachable, 404, rtsp, bad files; :video killed"
D="$ROW_DIR"
colour() { sed -n "s/^$1 //p" "$FIX/colours.txt"; }
fixture_up || exit 5
trap 'airplane disable; fixture_down' EXIT
CENSUS="$(video_count)"; videos_grant
assert_eq "airplane mode is off at the start" "0" "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
push_videos qa-truncated.mp4 qa-empty.mp4
adb shell am force-stop app.tileshell; ensure_start; sleep 1
LAUNCHER_PID="$(adb shell pidof app.tileshell | tr -d '\r')"
view() { adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "$1" -t video/mp4 >/dev/null; }
leave() { for r in $RINGS; do ring_save "$r"; done; adb shell input keyevent KEYCODE_BACK; sleep 1; }

# An http source plays (10.0.2.2 is the debug config's cleartext exception; Q-D A permits it everywhere).
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
view "$FIXTURE_URL/qa-steps.mp4?token=QUERYSECRET17"
LINE="$(await_vline "$MARK" "[video] playing scheme=http")"
assert_contains "http: [video] playing scheme=http" "[video] playing scheme=http" "$LINE"
T0="$(wall_of "$LINE")"; while [ "$(device_ms)" -lt $((T0 + 3200)) ]; do sleep 0.05; done
screencap "$D/http-3.5s.png"
record "http: centre pixel at ≈3.5 s (nominal $(colour 3))" "$(pixel "$D/http-3.5s.png" 540 1098)"
assert_colour "http: the picture at ≈3.5 s is colour 3" "$(colour 3)" "$(pixel "$D/http-3.5s.png" 540 1098)" 20
assert_contains "the fixture served the file" "GET /qa-steps.mp4" "$(fixture_since "$OFF")"
assert_absent "no MediaStore id line for a network source" "[video] playing 1" "$(vring "$MARK" | grep -F '[video] playing' | grep -v scheme)"
assert_absent "the address's query string is in no :video line" "QUERYSECRET17" "$(vring "$MARK")"
assert_absent "nor in the launcher's ring" "QUERYSECRET17" "$(ring_since "$MARK" launcher)"
record "logcat lines of the shell's own processes holding the query string (0 expected)" "$(adb logcat -d -t 3000 | grep -F QUERYSECRET17 | grep -cE 'tileshell|ExoPlayer|Media3')"
record "logcat lines holding it from anything (Android's own START line prints an intent's data)" "$(adb logcat -d -t 3000 | grep -cF QUERYSECRET17)"
leave

# Airplane mode: "Can't reach this video", the host named, the player still on top.
airplane enable
MARK="$(ring_mark)"
view "$FIXTURE_URL/qa-steps.mp4"
LINE="$(await_vline "$MARK" "[video] cannot reach" 200)"
assert_contains "offline: [video] cannot reach <host:port>" "[video] cannot reach 10.0.2.2:$V17_PORT" "$LINE"
gdump "$D/offline.xml"
assert_eq "offline: the page's text" "Can't reach this video" "$(node_text "$D/offline.xml" player_error)"
assert_eq "offline: the player is still on top" "app.tileshell/.video.PlayerActivity" "$(top)"
assert_absent "offline: nothing played" "[video] playing" "$(vring "$MARK")"
leave
airplane disable
assert_eq "airplane mode is off again" "0" "$(adb shell settings get global airplane_mode_on | tr -d '\r')"

# A 404: the error state with the server's status in the line.
MARK="$(ring_mark)"
view "$FIXTURE_URL/404/qa-steps.mp4"
LINE="$(await_vline "$MARK" "[video] cannot decode" 150)"
assert_contains "404: [video] cannot decode 404" "[video] cannot decode 404" "$LINE"
gdump "$D/404.xml"
assert_eq "404: the page's text" "can't play this file" "$(node_text "$D/404.xml" player_error)"
assert_eq "404: the player is still on top" "app.tileshell/.video.PlayerActivity" "$(top)"
leave

# A scheme the player does not take: no player is made at all.
MARK="$(ring_mark)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "rtsp://10.0.2.2/x" >/dev/null
LINE="$(await_vline "$MARK" "[video] unsupported scheme")"
assert_contains "rtsp: [video] unsupported scheme=rtsp" "[video] unsupported scheme=rtsp" "$LINE"
gdump "$D/rtsp.xml"
assert_eq "rtsp: the page's text" "Can't play this address" "$(node_text "$D/rtsp.xml" player_error)"
assert_absent "rtsp: no ExoPlayer was made" "[video] player ready" "$(vring "$MARK")"
assert_eq "rtsp: no picture node" "no" "$(has_node "$D/rtsp.xml" video_surface)"
assert_absent "rtsp: no session of the shell" "app.tileshell" "$(adb shell dumpsys media_session | grep -E 'package=app\.tileshell' | head -2)"
leave

# A truncated file and an empty one: the error state, the player resumed, no crash.
for name in qa-truncated.mp4 qa-empty.mp4; do
  ID="$(video_id "$name")"
  assert_ne "$name is in MediaStore" "" "$ID"
  MARK="$(ring_mark)"
  view "content://media/external/video/media/$ID"
  LINE="$(await_vline "$MARK" "[video] cannot decode" 150)"
  assert_contains "$name: [video] cannot decode <name>" "[video] cannot decode $name" "$LINE"
  gdump "$D/$name.xml"
  assert_eq "$name: the page's text" "can't play this file" "$(node_text "$D/$name.xml" player_error)"
  assert_eq "$name: the player is still on top" "app.tileshell/.video.PlayerActivity" "$(top)"
  leave
done
assert_eq "no crash of the shell in this row" "" "$(no_crash)"

# :video killed while it plays: Start's process is another one.
MARK="$(ring_mark)"
view "$FIXTURE_URL/qa-steps.mp4"
await_vline "$MARK" "[video] playing scheme=http" >/dev/null
VPID="$(adb shell pidof app.tileshell:video | tr -d '\r')"
assert_ne ":video runs while the player is open" "" "$VPID"
for r in $RINGS; do ring_save "$r"; done
adb shell run-as app.tileshell kill -9 "$VPID"; sleep 1.5
assert_ne ":video's pid is gone or new" "$VPID" "$(adb shell pidof app.tileshell:video | tr -d '\r')"
assert_eq "the launcher's pid is unchanged" "$LAUNCHER_PID" "$(adb shell pidof app.tileshell | tr -d '\r')"
assert_absent "no session of the shell is left" "app.tileshell" "$(adb shell dumpsys media_session | grep -E 'package=app\.tileshell' | head -2)"
adb shell am force-stop app.tileshell; ensure_start
assert_eq "Start is drawn after the kill" "yes" "$(dump_ui "$D/start.xml"; has_node "$D/start.xml" start_page)"

remove_videos
assert_eq "videos restored to the census" "$CENSUS" "$(video_count)"
videos_grant_restore
row_end
