#!/usr/bin/env bash
# Phase 17 E13 — the VIEW contract, network sources and their failure states (re-cut 2026-09-23, T17-1), inside the
# egress guard (C-29). The doc's clauses → this driver's legs. EVERY `10.0.2.2:8090` / `10.0.2.3 … 8090` of the doc is
# read as port 8091 (INDEX Change Log 2026-10-05 14:27 (2): 8090 is held by another service on this PC).
#
#   resolver    `am start -a VIEW -d content://media/external/video/media/<id> -t video/mp4` → the resolver lists the
#               shell's player beside Aves and Fossify Gallery (dump), and `cmd package query-activities` names
#               PlayerActivity among the handlers. Nothing is chosen there (a choice is remembered by Android).
#   http        `am start -n app.tileshell/.video.PlayerActivity -a VIEW -d http://10.0.2.2:8091/qa-steps.mp4` → it
#               PLAYS under E11's pixel rule with `[video] playing scheme=http`
#   airplane    airplane mode and the same intent → "Can't reach this video", `[video] cannot reach 10.0.2.2:8091`, the
#               player still resumed, no crash; airplane mode off (RV12)
#   404         …/404 → the error state and `[video] cannot decode 404`
#   rtsp        `-d rtsp://10.0.2.2/x` → "Can't play this address", `[video] unsupported scheme=rtsp`, no ExoPlayer
#               source created (no `[video] player ready` line, no picture node)
#   cleartext   (C-16; Q-D A, r3 V6) the sub-step's own counted rule for 10.0.2.3:8091 above the guard's, the same VIEW
#               at http://10.0.2.3:8091/qa-steps.mp4 → that rule counts ≥ 1 packet, `[video] cannot reach
#               10.0.2.3:8091`, no `cleartext refused` line; the rule deleted; the guard's own count still 0
#   fixed hosts force-stop, MARK, Home → the launcher's ring holds `[net] cleartext permitted for <host>: false` for
#               every host of FixedEndpoints (read from the source at run time: nine), none reads true; and the JVM test
#               of task 17 (FixedEndpointsTest) passes — run by this driver before it takes the device
#   guard       after each restart inside the guard: `[weather] refresh ended without new data: no coordinates`, no
#               `[weather] fetch provider=`; at the end 0 packets from the shell to anything but 10.0.2.2
#
# adb root: the guard's span only (its rules need it), undone by egress_guard_off. Restores: the guard, airplane mode,
# the pushed video, the fixture server (its pid), the location grants, the device's media volume.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap video_cleanup EXIT

# The JVM test of task 17, before the device is taken (a build never runs under the lock).
mkdir -p "$QA/E13"
JVM_OUT="$QA/E13/.jvm-fixed-endpoints.out"
( cd "$REPO" && ./gradlew :app:testDebugUnitTest --tests 'app.tileshell.net.FixedEndpointsTest' --offline > "$JVM_OUT" 2>&1; echo $? > "$JVM_OUT.rc" )

video_row_begin E13 "the VIEW contract: the resolver, http, airplane mode, 404, rtsp, cleartext to 10.0.2.3, the fixed hosts"
mv "$JVM_OUT" "$D/jvm-fixed-endpoints.out"; mv "$JVM_OUT.rc" "$D/jvm-fixed-endpoints.rc"
quiet_on
videos_grant
media_up qa-steps.mp4
ID="$(media_id video qa-steps.mp4 Movies/)"
assert_ne "qa-steps.mp4 is in MediaStore" "" "$ID"
fixture_up || { _verdict FAIL "the catalogue fixture on :$VPORT" "did not start"; exit 5; }
assert_eq "airplane mode is off at the start" "0" "$(airplane_now)"
leave() { rings_save; adb shell input keyevent KEYCODE_BACK; sleep 1.2; }

guard_on
guard_restart "start"

# ------------------------------------------------------------------------------------------------ the resolver
log "--- the implicit VIEW of a MediaStore video: the resolver's list"
adb shell am start -a android.intent.action.VIEW -d "content://media/external/video/media/$ID" -t video/mp4 > "$D/implicit.out" 2>&1
sleep 4
assert_eq "the resolver is on top" "android/com.android.internal.app.ResolverActivity" "$(top_activity)"
gdump "$D/resolver.xml"; screencap "$D/resolver.png"
TEXTS="$(grep -o 'text="[^"]*"' "$D/resolver.xml" | sed 's/^text="//;s/"$//;s/&amp;/\&/g' | sort -u | tr '\n' '|')"
record "the resolver's texts" "$TEXTS"
assert_eq "the dump is the resolver's (android:id/resolver_list)" "yes" "$(has_node "$D/resolver.xml" android:id/resolver_list)"
# Two forms of the same resolver: the plain list (a row "Movies & TV" / "Tessera"), or — once any choice was ever made
# for this intent — the last-used form, whose header reads "Open with Tessera" above "Use a different app".
case "|$TEXTS" in
  *"|Movies & TV|"*) FORM="the plain list: a row Movies & TV" ;;
  *"|Open with Tessera|"*) FORM="the last-used form: Open with Tessera, the others under Use a different app" ;;
  *) FORM="" ;;
esac
record "the resolver's form" "${FORM:-neither}"
assert_ne "the resolver lists the shell's player (a row Movies & TV, or the header Open with Tessera)" "" "$FORM"
assert_contains "… beside Aves" "|Aves Libre|" "|$TEXTS"
assert_contains "… and Fossify Gallery (its label is Gallery)" "|Gallery|" "|$TEXTS"
HANDLERS="$(adb shell "cmd package query-activities --brief -a android.intent.action.VIEW -d content://media/external/video/media/$ID -t video/mp4" | tr -d '\r' | grep '/' | tr -d ' ' | sort | tr '\n' ' ')"
record "the VIEW video/mp4 handlers (cmd package query-activities)" "$HANDLERS"
for h in "app.tileshell/.video.PlayerActivity" "deckers.thibault.aves.libre/" "org.fossify.gallery/"; do assert_contains "a handler of the VIEW is $h" "$h" "$HANDLERS"; done
# Nothing is chosen in the resolver: a choice is remembered by Android as the "last chosen" app for this intent (this
# driver's run 1 chose the shell's player once, to record what the adb shell's launch does — as root, inside the guard,
# it played: `source from another app: it had access at launch` — and the resolver has opened in its last-used form
# since; TRUST_VIDEO's leg (vi) records the launch with an explicit component instead).
leave
[ "$(top_activity)" = "android/com.android.internal.app.ResolverActivity" ] && leave

# ------------------------------------------------------------------------------------------------ http
log "--- an http source plays"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
view_shell "$FIXTURE_URL/qa-steps.mp4"
LINE="$(await_vline "$MARK" "[video] playing scheme=http" 120)"
assert_contains "http: [video] playing scheme=http" "[video] playing scheme=http" "$LINE"
shot_at "$(wall_of "$LINE")" 3500 "$D/http-3.5s.png"
record "http: the screencap returned (ms after the playing line)" "$SHOT_DONE"
assert_pixel_rule "http at 3.5 s" 3 "$SHOT_RGB"
assert_eq "http: the player is on top" "$PLAYER_ACTIVITY" "$(top_activity)"
assert_contains "http: the fixture served the file" "GET /qa-steps.mp4" "$(fixture_since "$OFF")"
leave

# ------------------------------------------------------------------------------------------------ airplane mode
log "--- airplane mode and the same intent"
airplane enable
assert_eq "airplane mode is on" "1" "$(airplane_now)"
MARK="$(ring_mark)"
view_shell "$FIXTURE_URL/qa-steps.mp4"
LINE="$(await_vline "$MARK" "[video] cannot reach" 250)"
assert_contains "offline: [video] cannot reach $FIXTURE_HOST" "[video] cannot reach $FIXTURE_HOST" "$LINE"
gdump "$D/offline.xml"
assert_eq "offline: the page's text" "Can't reach this video" "$(node_text "$D/offline.xml" player_error)"
assert_eq "offline: the player is still resumed" "$PLAYER_ACTIVITY" "$(top_activity)"
absent_in "offline: nothing played" "[video] playing" "$(vring "$MARK")"
assert_eq "offline: no crash (logcat -T <MARK> -s AndroidRuntime, app.tileshell)" "" "$(crash_since "$MARK")"
leave
airplane disable
assert_eq "airplane mode is off again (RV12)" "0" "$(airplane_now)"

# ------------------------------------------------------------------------------------------------ 404
log "--- …/404"
MARK="$(ring_mark)"
view_shell "$FIXTURE_URL/404/qa-steps.mp4"
LINE="$(await_vline "$MARK" "[video] cannot decode" 150)"
assert_contains "404: [video] cannot decode 404 (the server's status in the line)" "[video] cannot decode 404" "$LINE"
gdump "$D/404.xml"
assert_eq "404: the error state is shown (player_error)" "yes" "$(has_node "$D/404.xml" player_error)"
record "404: the error state's text" "$(node_text "$D/404.xml" player_error)"
assert_eq "404: the player is still resumed" "$PLAYER_ACTIVITY" "$(top_activity)"
leave

# ------------------------------------------------------------------------------------------------ rtsp
log "--- rtsp://10.0.2.2/x"
MARK="$(ring_mark)"
adb shell am start -n "$PLAYER_ACTIVITY" -a android.intent.action.VIEW -d "rtsp://10.0.2.2/x" >/dev/null 2>&1
LINE="$(await_vline "$MARK" "[video] unsupported scheme" 100)"
assert_contains "rtsp: [video] unsupported scheme=rtsp" "[video] unsupported scheme=rtsp" "$LINE"
gdump "$D/rtsp.xml"
assert_eq "rtsp: the page's text" "Can't play this address" "$(node_text "$D/rtsp.xml" player_error)"
absent_in "rtsp: no ExoPlayer source was created (no [video] player ready line)" "[video] player ready" "$(vring "$MARK")"
assert_eq "rtsp: the page is the player's (player_root), with no picture node" "yes no" "$(has_node "$D/rtsp.xml" player_root) $(has_node "$D/rtsp.xml" video_surface)"
leave

# ------------------------------------------------------------------------------------------------ cleartext, 10.0.2.3
log "--- cleartext to a host the debug exception does not cover (Q-D A: the platform lets the attempt through)"
adb shell iptables -I OUTPUT -m owner --uid-owner "$APP_UID" -d 10.0.2.3 -p tcp --dport "$VPORT" -j REJECT
own_count() { adb shell iptables -L OUTPUT -v -n | tr -d '\r' | awk -v u="owner UID match $APP_UID" -v p="dpt:$VPORT" 'index($0, u) && index($0, p) && /10\.0\.2\.3/ { print $1; exit }'; }
assert_eq "the sub-step's own rule is in place, its count 0" "0" "$(own_count)"
MARK="$(ring_mark)"
view_shell "http://10.0.2.3:$VPORT/qa-steps.mp4"
LINE="$(await_vline "$MARK" "[video] cannot reach" 250)"
assert_contains "10.0.2.3: [video] cannot reach 10.0.2.3:$VPORT" "[video] cannot reach 10.0.2.3:$VPORT" "$LINE"
N="$(own_count)"
record "10.0.2.3: the packets the sub-step's rule counted" "$N"
assert_eq "10.0.2.3: that rule counted ≥ 1 packet (the attempt left the app)" "yes" "$([ "${N:-0}" -ge 1 ] 2>/dev/null && echo yes || echo no)"
absent_in "10.0.2.3: no [video] cleartext refused line (r3 V6)" "cleartext refused" "$(vring "$MARK")"
gdump "$D/10023.xml"
record "10.0.2.3: the page's text" "$(node_text "$D/10023.xml" player_error)"
adb shell iptables -D OUTPUT -m owner --uid-owner "$APP_UID" -d 10.0.2.3 -p tcp --dport "$VPORT" -j REJECT
assert_eq "the sub-step's rule is deleted" "" "$(own_count)"
assert_eq "the guard's own count is still 0" "0" "$(egress_guard_count)"
leave

# ------------------------------------------------------------------------------------------------ the fixed hosts
log "--- the fixed hosts: force-stop, MARK, Home"
HOSTS="$(sed -n 's/^ *const val [A-Z_]* = "https:\/\/\([^"\/]*\)\/".*/\1/p' "$REPO/app/src/main/kotlin/app/tileshell/net/FixedEndpoints.kt")"
assert_eq "FixedEndpoints names nine hosts (read from the source)" "9" "$(printf '%s\n' "$HOSTS" | grep -c .)"
rings_save
# The MARK is taken the moment the force-stop returns, as the doc orders it (force-stop, MARK, Home): Android restarts
# the home app by itself within a second, so a MARK taken any later is after the [net] lines (this driver's run 1).
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"
sleep 1
ensure_start
await_lline "$MARK" "[net] cleartext permitted for $(printf '%s\n' "$HOSTS" | tail -1)" 200 >/dev/null
NET="$(ring_since "$MARK" launcher | grep -F '[net] ')"
printf '%s\n' "$NET" > "$D/net-lines.txt"
for h in $HOSTS; do
  assert_contains "[net] cleartext permitted for $h: false" "[net] cleartext permitted for $h: false" "$NET"
done
absent_in "no fixed host reads true" ": true" "$NET"
WL="$(await_lline "$MARK" "[weather] refresh ended without new data: no coordinates" 200)"
assert_contains "after this restart inside the guard: [weather] refresh ended without new data: no coordinates" "[weather] refresh ended without new data: no coordinates" "$WL"
absent_in "… and no [weather] fetch provider= line" "[weather] fetch provider=" "$(ring_since "$MARK" launcher)"
assert_eq "the JVM test of task 17 passes (FixedEndpointsTest, run by this driver): gradle rc" "0" "$(cat "$D/jvm-fixed-endpoints.rc")"
record "FixedEndpointsTest: tests / failures / errors (the XML report)" "$(python3 - "$REPO" <<'PY'
import glob, re, sys
f = glob.glob(sys.argv[1] + "/app/build/test-results/testDebugUnitTest/TEST-app.tileshell.net.FixedEndpointsTest.xml")
if not f: print("no report"); sys.exit()
m = re.search(r'tests="(\d+)"[^>]*failures="(\d+)" errors="(\d+)"', open(f[0]).read())
print(" / ".join(m.groups()) if m else "unreadable")
PY
)"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
guard_off
fixture_down
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
media_down
videos_grant_restore
quiet_off
c6; ensure_start
row_end
