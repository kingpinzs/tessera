#!/usr/bin/env bash
# Phase 20 A7 — Play from the home server.
#
#   1  qa/phase-17/fixtures/jellyfin/jellyfin_fixture.sh up, with the Music folder (two MP3s, 30 s and 45 s)
#   2  sign in (the fixture's account) at Music's server entry
#   3  open albums / artists / songs; tap a song
#   4  tap QA Jazz One in radio
#
# Pass (a)–(d) as the phase doc lists them (`:8080` reads `:8092`).
# The fixture account's password is read from jellyfin_fixture.sh (its PASSWORD= line) and the token from the server
# (`token-of <work dir> Tessera`); neither is printed, logged or written to any evidence file — leak_scan.sh gets them
# as arguments and proves it, over this phase's whole evidence folder and logcat (c).
# The Jellyfin work dir is a scratch folder outside the repo (P20_JF_WORK), with a 10 s test video generated there for
# the fixture's Movies folder (its `up` needs one).
#
# Changes on the device: a home server stays saved in the shell (it points at the removed fixture). The container and
# its volumes are removed at the end (also from the EXIT trap), by the id the fixture recorded.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
JF="$QAROOT/phase-17/fixtures/jellyfin/jellyfin_fixture.sh"
WORK="${P20_JF_WORK:?set P20_JF_WORK to a scratch folder outside the repo}"
jf_down() { bash "$JF" down "$WORK" > "$ROW_DIR/jellyfin-down.out" 2>&1; }
trap 'jf_down; fixtures_down' EXIT
secs() { awk -F: '{ print $1 * 60 + $2 }'; }   # m:ss -> seconds

p20_begin A7 "play from the home server"
prefs_guard a b c d
record "df -h / before the container starts" "$(df -h / | tail -1 | xargs)"
fixtures_up
baseline_start

# ----------------------------------------------------------------------------------------------- 1: the fixture
log "--- 1: jellyfin_fixture.sh up with the Music folder"
mkdir -p "$WORK"
[ -f "$WORK/container.id" ] && jf_down
VIDEO="$WORK/qa-steps-src.mp4"
[ -f "$VIDEO" ] || ffmpeg -loglevel error -y -f lavfi -i "testsrc=duration=10:size=320x240:rate=25" -c:v libx264 -pix_fmt yuv420p "$VIDEO"
bash "$JF" up "$WORK" "$VIDEO" > "$ROW_DIR/jellyfin-up.out" 2>&1; echo $? > "$ROW_DIR/jellyfin-up.rc"
assert_eq "jellyfin_fixture.sh up (rc)" "0" "$(cat "$ROW_DIR/jellyfin-up.rc")"
cp "$WORK/audio.ids" "$ROW_DIR/audio.ids" 2>/dev/null
record "the server's two tracks (id, title, length in s as the server reports it)" "$(tr '\t\n' ' ;' < "$ROW_DIR/audio.ids")"
assert_eq "the Music folder's two tracks are in the server's library" "2" "$(grep -c . "$ROW_DIR/audio.ids")"
JF_PASSWORD="$(sed -n 's/.*PASSWORD="\([^"]*\)".*/\1/p' "$JF" | head -1)"
assert_yes "the fixture account's password is read from the fixture (not echoed)" "$([ ${#JF_PASSWORD} -ge 6 ] && echo yes || echo no)"
case "$(emu_reaches 8096)" in *200*|*HTTP*) R8096=yes ;; *) R8096="$(adb shell "toybox timeout 4 nc -z -w 3 10.0.2.2 8096 && echo yes || echo no" | tr -d '\r')" ;; esac
assert_eq "the emulator reaches the server at 10.0.2.2:8096" "yes" "$R8096"

# ----------------------------------------------------------------------------------------------- 2: sign in
log "--- 2: sign in at Music's server entry"
letter a
to_radio; assert_eq "(a) Music is on the radio pivot (the server's entry is there)" "0" "$?"
radio_reach radio-srv music_server_entry
R0="$(flog "$RLOG.jsonl" 'True' | wc -l | xargs)"
MARK="$(ring_mark)"
tap radio-srv music_server_entry; sleep 3; d srv0; shot 01-server-sign-in
assert_eq "(a) the sign-in form is showing (host, user, password, connect)" "yes yes yes yes" "$(has srv0 server_host) $(has srv0 server_user) $(has srv0 server_password) $(has srv0 server_connect)"
tap srv0 server_host; sleep 0.8; adb shell input text "10.0.2.2:8096"; sleep 0.5
tap srv0 server_user; sleep 0.8; adb shell input text "qa"; sleep 0.5
tap srv0 server_password; sleep 0.8; adb shell input text "$JF_PASSWORD" >/dev/null 2>&1; sleep 0.5
adb shell input keyevent KEYCODE_BACK; sleep 1; d srv1
tap srv1 server_connect; sleep 3; d srv-step
# A plain-http server asks once before it signs in (phase 17's form): continue, as a user with a home server would.
if [ "$(has srv-step server_insecure_continue)" = yes ]; then record "(a) the plain-http notice was shown" "continued"; tap srv-step server_insecure_continue; fi
for _ in $(seq 1 15); do sleep 1; d srv2; [ "$(has srv2 server_pivot)" = yes ] && break; done
shot 02-server-albums
mring "$MARK" > "$ROW_DIR/slice-signin.txt"
CONN="$(grep -F '[music] server 10.0.2.2:8096: connected' "$ROW_DIR/slice-signin.txt" | head -1 | stripped)"
record "(a) the line" "$CONN"
assert_contains "(a) [music] server 10.0.2.2:8096: connected" "[music] server 10.0.2.2:8096: connected" "$CONN"

# ----------------------------------------------------------------------------------------------- 3: the groupings, a song
log "--- 3: open albums / artists / songs; tap a song"
assert_eq "(a) the three groupings are offered (server_pivot_header:albums / artists / songs)" "yes yes yes" "$(has srv2 server_pivot_header:albums) $(has srv2 server_pivot_header:artists) $(has srv2 server_pivot_header:songs)"
ALBUMS="$(ids srv2 server_album: | tr '\n' ' ')"
record "(a) albums" "$ALBUMS$(for r in $ALBUMS; do printf '(%s) ' "$(nt srv2 "music_pri:$r")"; done)"
assert_ne "(a) albums are listed (server_album:<id>)" "" "$ALBUMS"
tap srv2 server_pivot_header:artists; sleep 2.5; d artists; shot 03-server-artists
ARTISTS="$(ids artists server_artist: | tr '\n' ' ')"
record "(a) artists" "$ARTISTS"
assert_contains "(a) artists are listed (server_artist:<name>)" "server_artist:QA Server Artist" "$ARTISTS"
tap artists server_pivot_header:songs; sleep 2.5; d songs; shot 04-server-songs
SONGS="$(ids songs server_song: | tr '\n' ' ')"
record "(a) songs" "$(for r in $SONGS; do printf '%s (%s); ' "$r" "$(nt songs "music_pri:$r")"; done)"
assert_eq "(a) songs are listed (two server_song:<id>)" "2" "$(ids songs server_song: | wc -l | xargs)"

letter b
SONG="$(ids songs server_song: | head -1)"; SONG_ID="${SONG#server_song:}"; SONG_TITLE="$(nt songs "music_pri:$SONG")"
WANT_S="$(awk -F'\t' -v id="$SONG_ID" '$1 == id { print $3 }' "$ROW_DIR/audio.ids")"
record "(b) the song tapped / its length by the server" "$SONG_TITLE ($SONG_ID) / $WANT_S s"
MARK3="$(ring_mark)"
tap songs "$SONG"
for _ in $(seq 1 15); do sleep 1; [ "$(sstate)" = PLAYING ] && break; done
sleep 4; d np; shot 05-server-song-playing
SESSION="$(sboth)"; session_save session-server-song
mring "$MARK3" > "$ROW_DIR/slice-song.txt"
record "(b) the session / tracks" "$SESSION / $(ours)"
assert_eq "(b) the song plays: state" "PLAYING" "${SESSION%% |*}"
assert_contains "(b) …and it is that song" "$SONG_TITLE" "${SESSION#* | }"
TOTAL="$(nt np nowplaying_total)"
record "(b) nowplaying_elapsed / nowplaying_total" "$(nt np nowplaying_elapsed) / $TOTAL"
GOT_S="$(printf '%s' "$TOTAL" | secs)"
assert_yes "(b) nowplaying_total ($TOTAL = $GOT_S s) = the file's length ($WANT_S s) ± 1 s" "$(python3 -c 'import sys
try: print("yes" if abs(float(sys.argv[1]) - float(sys.argv[2])) <= 1.0 else "no")
except Exception: print("no")' "${GOT_S:-x}" "${WANT_S:-y}")"
assert_eq "(b) a normal scrubber (nowplaying_scrubber present, no live caption)" "yes no" "$(has np nowplaying_scrubber) $(has np nowplaying_live_caption)"
SL="$(grep -F 'stream: connected' "$ROW_DIR/slice-song.txt" | head -1 | stripped)"
record "(b) the line" "$SL"
assert_eq "(b) [music] stream: connected http://10.0.2.2:8096/Audio/<id>/stream codec=mp3" "[music] stream: connected http://10.0.2.2:8096/Audio/$SONG_ID/stream codec=mp3" "$SL"
assert_absent "(b) …holding no ?" "?" "$SL"

# ----------------------------------------------------------------------------------------------- 4: a station after it
log "--- 4: tap QA Jazz One in radio"
letter d
MARK4="$(ring_mark)"
play_station "$J1" 25; PS=$?
sleep 6; d np-station; shot 06-station-after-server
record "(d) the session after the station tap" "$(sboth)"
assert_eq "(d) QA Jazz One plays after the server's song" "0 PLAYING yes" "$PS $(sstate) $(smeta | grep -q 'QA Jazz One' && echo yes || echo no)"
mring "$MARK4" > "$ROW_DIR/slice-station.txt"
session_save session-station

rings_save
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
fixtures_down
flog "$RLOG.jsonl" 'True' > "$ROW_DIR/radio-requests.txt"
RN="$(grep -c . "$ROW_DIR/radio-requests.txt")"
record "(d) the radio fixture's requests in this row / since the sign-in" "$RN / $(( RN - R0 ))"
assert_ne "(d) the radio fixture got requests after the sign-in (the stream, the click call)" "0" "$(( RN - R0 ))"
assert_contains "(d) …the station's stream among them" "/stream/jazz-one" "$(cat "$ROW_DIR/radio-requests.txt")"
assert_eq "(d) no Authorization header on any radio-fixture request" "0" "$(flog "$RLOG.jsonl" '"authorization" in h' | wc -l | xargs)"
assert_eq "(d) no ApiKey on any request (header names, header values, the target)" "0" "$(flog "$RLOG.jsonl" 'any(s in (r["target"] + " " + " ".join(k + " " + v for k, v in r.get("headers", []))).lower() for s in ("apikey", "api_key", "x-emby", "x-mediabrowser", "mediabrowser token"))' | wc -l | xargs)"
record "(d) header names the fixture saw on its requests" "$(python3 -c '
import json, sys
print(", ".join(sorted({k for l in open(sys.argv[1]) if l.strip() for k, _ in json.loads(l).get("headers", [])})))' "$RLOG.jsonl")"

# ----------------------------------------------------------------------------------------------- (c) the leak scan
letter c
TOKEN="$(bash "$JF" token-of "$WORK" Tessera 2>/dev/null)"
assert_eq "(c) the token the server issued the shell is read (token-of Tessera): its length" "32" "${#TOKEN}"
bash "$QAROOT/phase-17/scripts/leak_scan.sh" --logcat --path "$QA20" -- "$TOKEN" "$JF_PASSWORD" > "$ROW_DIR/leak-scan.out" 2>&1; echo $? > "$ROW_DIR/leak-scan.rc"
record "(c) leak_scan's lines" "$(tr '\n' ';' < "$ROW_DIR/leak-scan.out" | sed "s#$QA20#qa/phase-20#g")"
assert_eq "(c) leak_scan.sh --logcat --path qa/phase-20 -- <token> <password> exits 0" "0" "$(cat "$ROW_DIR/leak-scan.rc")"
TOKEN=""; JF_PASSWORD=""

letter restore
jf_down
assert_eq "the Jellyfin container is removed (none recorded, none running from the image)" "no 0" "$([ -f "$WORK/container.id" ] && echo yes || echo no) $(docker ps -a --format '{{.Image}}' | grep -ci jellyfin)"
ensure_start
p20_end a b c d
