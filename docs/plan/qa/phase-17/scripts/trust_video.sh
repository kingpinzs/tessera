#!/usr/bin/env bash
# Phase 17 row TRUST_VIDEO — the device legs the Movies & TV trust fixes owe (docs/plan/review/
# 2026-10-05-phase17-trust-fixes.md, the FIRST table; and review round 2's B2-M1 / B2-M2 / B2-L7, the lead's brief).
# Each leg is named by its finding. What a leg asserts is the fixes file's own wording; a leg the file says to RECORD
# is recorded.
#
# The legs are the fixes file's as it stands after rounds 2 and 3 (its "Round 2" letters a, b, c, e, g; "Round 3":
# the same legs for the player under the ONE access rule, Decisions 2026-10-05 19:09).
#
#   outside the guard (the callers' uids matter: inside it adbd is root)
#   C-M4 (i)    another app with NO media permission VIEWs content://media/external/video/media/<id> → refused,
#               "Can't play this address", `[video] launch answer read: denied`, `[video] refused source: no grant`
#   C-M4 (ii)   it VIEWs its own provider's URI with a read grant → plays (the launch-answer line recorded); and with
#               its identity shared → plays
#   C-M4 (iii)  it VIEWs file:///data/user/0/app.tileshell/files/x.mp4 → `[video] unsupported scheme=file`
#   C-M4 (iv)   (i) with a long-array extra `queue` and a `title` → both ignored: no autoplay, refused as (i)
#   C-M4 (v)    the same app WITH READ_MEDIA_VIDEO → REFUSED (the platform limit of the 19:09 line: the launch answer
#               is "denied" for any MediaStore item); RECORDED beside it: the same with the read flag; with its
#               identity shared it plays (MediaStore's own answer for a named starter) and shows NO subtitle line
#               although a .srt is beside the file (the .srt is looked up only for the shell's own launch)
#   C-M4 (vi)   RECORDED: `adb shell am start` VIEW of the content item (the shell uid; and, inside the guard, root)
#   C-M4 (vii)  from the hub → plays, the queue honoured (`[video] autoplay next <id>` with Autoplay on); from Photos
#               → plays
#   C-L1        VIEW http://10.0.2.2:1/a%0A[cred]%20x.mp4 → no ring line begins with the forged `[cred] x`
#
#   inside the egress guard (the fake media server of fixtures/jellyfin/fake_jellyfin.py on 10.0.2.2:8097, whose
#   request log says whether a request carried the token; the catalogue fixture on 8091)
#   B-1 (1)     a key with an interior CR pasted into tmdb_key_field, Save → tmdb_key_error shown, tmdb_key_status
#               "No key is saved.", the :video pid unchanged, nothing stored, logcat holds no part of the key
#   B2-L7 (g)   2,001 pasted characters → refused, "No key is saved."; 2,000 → saved (then removed)
#   B-1 (2)     with a valid key, Browse opens with no crash
#   B-1 (3)     the server answering an AccessToken with \r inside → "Can't reach your media server",
#               `sign-in answer not usable`, no AndroidRuntime line
#   B-9         a sign-in answer over the size cap → "Can't reach your media server", `[video] http: answer over the
#               size cap`
#   B2-M2 (e)   from the hub's Media server page the item plays with `[video] server stream …` and the token on the
#               request; then the same address by `adb shell am start` and from a permission-less app → `[video]
#               server token not given: not the shell's own launch`, no `server stream` line, no token and no ApiKey
#               on those requests (the fake server's log)
#   B-5         the hub's play, the server stopped mid-play → the :video ring and logcat hold no `ApiKey=`
#   B-2         the token made invalid, the page reopened → the sign-in form's server_host reads the address WITH its
#               scheme (http://10.0.2.2:8097; the AVD fixture is http), and signing in from it connects
#   B-4 (a)     media_server.json edited to another host (run-as) → the library still goes to the first server; the
#               other host sees no request
#   B-4 (b)     the sealed entry corrupted → it does not open and is cleared at the hub's start (round 2); no library,
#               no request with the token; Settings → Media server shows the sign-in form again
#   B2-M1 (b)   media_server.json replaced with one lacking `pair`, Settings → Media server opened without a restart
#               → server_left_over + server_remove; Remove → `[video] server token cleared`
#   B2-M1 (a)   media_server.json removed (run-as), force-stop, the hub opened → `[cred] jellyfin: removed`, `[video]
#               server entry cleared at start: it was not a saved server`, no Media server row, no shortcut; a later
#               stream carries no token
#   B2-M1 (c)   a normal removal writes `[video] server token cleared`
#   B2-M3       the sealed `jellyfin` entry's fields moved under the `tmdb` name → both read as absent
#   B2-L8 (h)   NOT RUN: it needs a fresh install with a PIN (a wipe of the shared device) — the lead's
#   leak scan   over this row's folder and logcat: the fake server's token, the passwords, the TMDB token, the pasted keys
#
# Restores: the fixture app uninstalled, the clipboard emptied, the server and the key removed (the two store files
# removed if a corrupted entry is left that the page cannot remove), the QA pref, the pushed videos, the guard, the
# servers (by pid).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap 'video_cleanup; adb uninstall "$QAVIEW" >/dev/null 2>&1' EXIT
CR_A="qa-cr-key-AAAAAAAA"; CR_B="BBBBBBBB-tail"                    # the two halves of the pasted key (a CR between)
KEY_2001="$(python3 -c 'print("qa-long-key-" + "k" * 1989)')"      # 2,001 characters: one over HeaderText.MAX_SECRET
KEY_2000="$(python3 -c 'print("qa-full-key-" + "f" * 1988)')"      # 2,000 characters: the longest key the setting takes
FAKE_PW="fake-password-17"
store_files() { adb shell "run-as app.tileshell sh -c 'ls files/credentials_v1.json files/media_server.json 2>/dev/null'" | tr -d '\r' | xargs; }
vtext() { vring "$1" | grep -F '[video]' | sed 's/.*\[video\] //' | tr '\n' '|'; }
leave() { rings_save; adb shell input keyevent KEYCODE_BACK; sleep 1.2; }
# One VIEW from the fixture app: a MARK, the start, the :video lines and the page's text read after a settle.
qa_leg() { # name settle extras…
  local name="$1" settle="$2"; shift 2
  LEG_MARK="$(ring_mark)"
  qaview "$@"; sleep "$settle"
  LEG_SLICE="$(vring "$LEG_MARK")"
  gdump "$D/$name.xml"
  LEG_TEXT="$(node_text "$D/$name.xml" player_error)"
  LEG_APP="$(qaview_log 4 | sed 's/.*qa-view: //' | tr '\n' '|')"
  record "$name: the fixture app's own log" "$LEG_APP"
  record "$name: the :video lines" "$(printf '%s\n' "$LEG_SLICE" | grep -F '[video]' | sed 's/.*\[video\] //' | tr '\n' '|')"
}

video_row_begin TRUST_VIDEO "the Movies & TV trust fixes' device legs: the player's callers, the key, the sign-in answer, the token"
[ -f "$QAVIEW_APK" ] || { _verdict FAIL "the QA View fixture APK" "missing: ./gradlew :testapps:qa-view:assembleDebug --offline"; row_end; exit 4; }
quiet_on
videos_grant
media_up qa-steps.mp4 qa-rot90.mp4 qa-steps.srt
ID="$(media_id video qa-steps.mp4 Movies/)"; ID2="$(media_id video qa-rot90.mp4 Movies/)"
assert_ne "qa-steps.mp4 is in MediaStore" "" "$ID"
assert_ne "qa-rot90.mp4 is in MediaStore" "" "$ID2"
record "the store's files before the row" "$(store_files)"
adb uninstall "$QAVIEW" >/dev/null 2>&1
adb install "$QAVIEW_APK" > "$D/qaview-install.out" 2>&1
adb shell "run-as $QAVIEW sh -c 'mkdir -p files && cat > files/own.mp4'" < "$GEN/qa-steps.mp4"
# Whether the fixture app holds a runtime permission: dumpsys lists it under "runtime permissions" once it has a state;
# a permission never granted and never asked for has no line there, which is "false".
qgranted() { local v; v="$(adb shell dumpsys package "$QAVIEW" | tr -d '\r' | grep -m1 "android.permission.$1: granted=" | sed -E 's/.*granted=([a-z]+).*/\1/')"; echo "${v:-false}"; }
assert_eq "the fixture app holds NO media permission (READ_MEDIA_VIDEO, READ_MEDIA_AUDIO)" "false false" "$(qgranted READ_MEDIA_VIDEO) $(qgranted READ_MEDIA_AUDIO)"
adb shell am force-stop app.tileshell; sleep 1; ensure_start
CONTENT="content://media/external/video/media/$ID"

# ================================================================================================ C-M4
log "--- C-M4 (i): no media permission, a MediaStore item"
qa_leg cm4-i 3.5 --es uri "$CONTENT"
assert_contains "(i): the app itself cannot read the item" "own read=DENIED" "$LEG_APP"
assert_contains "(i): [video] launch answer read: denied" "[video] launch answer read: denied" "$LEG_SLICE"
assert_contains "(i): [video] refused source: no grant" "[video] refused source: no grant" "$LEG_SLICE"
assert_eq "(i): the page's text" "Can't play this address" "$LEG_TEXT"
absent_in "(i): nothing played" "[video] playing" "$LEG_SLICE"
leave

log "--- C-M4 (ii): its own provider's URI with a read grant"
qa_leg cm4-ii 4 --es uri own:own.mp4 --ez grant true
assert_contains "(ii): it plays" "[video] playing scheme=content" "$LEG_SLICE"
assert_contains "(ii): the platform's launch answer is read first ([video] launch answer read: …)" "[video] launch answer read: " "$LEG_SLICE"
record "(ii): the launch answer" "$(printf '%s\n' "$LEG_SLICE" | grep -F 'launch answer read' | sed 's/.*launch answer read: //' | tail -1)"
absent_in "(ii): not refused" "refused source" "$LEG_SLICE"
leave
qa_leg cm4-ii-shared 4 --es uri own:own.mp4 --ez grant true --ez share true
assert_contains "(ii), its identity shared: it plays" "[video] playing scheme=content" "$LEG_SLICE"
leave

log "--- C-M4 (iii): a file: source naming the shell's own data"
qa_leg cm4-iii 3 --es uri "file:///data/user/0/app.tileshell/files/x.mp4"
assert_contains "(iii): the fixture app's start was made (its own file-URI check relaxed)" "launch=OK started" "$LEG_APP"
assert_contains "(iii): [video] unsupported scheme=file" "[video] unsupported scheme=file" "$LEG_SLICE"
assert_eq "(iii): the page's text" "Can't play this address" "$LEG_TEXT"
leave

log "--- C-M4 (iv): (i) with a long-array extra queue"
qa_leg cm4-iv 3.5 --es uri "$CONTENT" --es queue "$ID,$ID2"
record "(iv): the line about the queue, if one is written" "$(printf '%s\n' "$LEG_SLICE" | grep -F 'queue' | sed 's/.*\[video\] //' | tr '\n' '|')"
assert_contains "(iv): still refused" "[video] refused source: no grant" "$LEG_SLICE"
absent_in "(iv): no autoplay" "autoplay next" "$LEG_SLICE"
leave

# Added 2026-10-06 after the gate review (reviewer A, finding 6): a NAMED starter with no access is a no on the device
# too - until now every shared-identity leg was a positive.
log "--- C-M4 (i-shared): (i) with the app's identity shared, still holding no READ_MEDIA_VIDEO"
assert_eq "(i-shared): the fixture app does not hold READ_MEDIA_VIDEO" "false" "$(qgranted READ_MEDIA_VIDEO)"
qa_leg cm4-i-shared 3.5 --es uri "$CONTENT" --ez share true
assert_contains "(i-shared): a named starter MediaStore says no to is REFUSED — [video] refused source: no grant" "[video] refused source: no grant" "$LEG_SLICE"
assert_eq "(i-shared): the page's text" "Can't play this address" "$LEG_TEXT"
absent_in "(i-shared): nothing played" "[video] playing" "$LEG_SLICE"
leave

log "--- C-M4 (v): the same app WITH READ_MEDIA_VIDEO"
adb shell pm grant "$QAVIEW" android.permission.READ_MEDIA_VIDEO
assert_eq "(v): the fixture app now holds READ_MEDIA_VIDEO" "true" "$(qgranted READ_MEDIA_VIDEO)"
qa_leg cm4-v 4 --es uri "$CONTENT"
assert_contains "(v): the app itself can read the item now" "own read=OK" "$LEG_APP"
assert_contains "(v): [video] launch answer read: denied (the platform limit, Decisions 19:09)" "[video] launch answer read: denied" "$LEG_SLICE"
assert_contains "(v): REFUSED — [video] refused source: no grant" "[video] refused source: no grant" "$LEG_SLICE"
assert_eq "(v): the page's text" "Can't play this address" "$LEG_TEXT"
absent_in "(v): nothing played" "[video] playing" "$LEG_SLICE"
record "(v): logcat lines naming the WM lock since the leg (the platform's own reason, recorded)" "$(logcat_since "$LEG_MARK" | grep -ci 'holding WM lock')"
leave
qa_leg cm4-v-flag 4 --es uri "$CONTENT" --ez grant true
record "(v) RECORDED, with FLAG_GRANT_READ_URI_PERMISSION: plays?" "$(printf '%s\n' "$LEG_SLICE" | grep -qF "[video] playing $ID" && echo yes || echo "no — $LEG_TEXT")"
leave
qa_leg cm4-v-shared 4 --es uri "$CONTENT" --ez share true --es title "A title another app chose" --es queue "$ID2"
assert_contains "(v), its identity shared (a NAMED starter): it plays on MediaStore's own answer" "[video] playing $ID" "$LEG_SLICE"
absent_in "(v), another app's launch of a MediaStore item: NO subtitle line, though qa-steps.srt is beside the file" "subtitle file beside" "$LEG_SLICE"
absent_in "(v), another app's queue is ignored: no autoplay" "autoplay next" "$LEG_SLICE"
adb shell dumpsys media_session | tr -d '\r' > "$D/cm4-v-shared-session.txt"
assert_absent "(v), another app's title is ignored: the session's metadata does not carry it" "A title another app chose" "$(cat "$D/cm4-v-shared-session.txt")"
leave
adb shell pm revoke "$QAVIEW" android.permission.READ_MEDIA_VIDEO

log "--- C-M4 (vi): RECORDED — adb shell am start (the shell uid)"
MARK="$(ring_mark)"
view_shell "$CONTENT"; sleep 3.5
gdump "$D/cm4-vi.xml"
record "(vi) the shell uid's am start -n PlayerActivity -a VIEW -d content://…/<id> -t video/mp4: the :video lines" "$(vtext "$MARK")"
record "(vi) … the page's text" "$(node_text "$D/cm4-vi.xml" player_error)"
record "(vi) … the caller's uid (adb shell id -u)" "$(adb shell id -u | tr -d '\r')"
leave

log "--- C-M4 (vii): from the hub (the queue honoured) and from Photos"
# Autoplay goes on to the NEXT video of the same My videos group, so the one opened is whichever of the row's two
# fixtures the page lists first.
hub myvideos 2.5; scroll_to_node "$D/cm4-vii-order.xml" "video_tile:$ID" 12
ORDER="$(grep -o 'resource-id="video_tile:[0-9]*"' "$D/cm4-vii-order.xml" | sed 's/.*video_tile:\([0-9]*\)"/\1/' | grep -E "^($ID|$ID2)$" | xargs)"
record "(vii): the two fixtures in the page's order" "$ORDER"
HUB_ID="$ID"; [ "${ORDER%% *}" = "$ID2" ] && HUB_ID="$ID2"
MARK="$(ring_mark)"
play_from_hub "$HUB_ID" "$D/cm4-vii"
LINE="$(await_vline "$MARK" "[video] playing $HUB_ID" 100)"
assert_contains "(vii) from the hub: it plays" "[video] playing $HUB_ID" "$LINE"
absent_in "(vii) from the hub: no 'queue ignored' and no 'source from another app' line" "queue ignored" "$(vring "$MARK")"
absent_in "(vii) from the hub: the launch is the shell's own" "source from another app" "$(vring "$MARK")"
show_paused "$D/cm4-vii-p.xml"
tap_node "$D/cm4-vii-p.xml" player_more; sleep 0.7; gdump "$D/cm4-vii-menu.xml"; tap_node "$D/cm4-vii-menu.xml" player_menu:autoplay; sleep 0.9
gdump "$D/cm4-vii-a.xml"
[ "$(has_node "$D/cm4-vii-a.xml" player_track)" = yes ] || { adb shell input tap 540 600; sleep 0.6; gdump "$D/cm4-vii-a.xml"; }
MARKA="$(ring_mark)"
# shellcheck disable=SC2046
set -- $(centre_px "$D/cm4-vii-a.xml" player_track); adb shell input tap 1000 "${2:-1917}"; sleep 0.5
tap_node "$D/cm4-vii-a.xml" player_playpause
NEXT="$(await_vline "$MARKA" "[video] autoplay next" 150)"
assert_contains "(vii) from the hub: the queue is honoured ([video] autoplay next <an id of the same group>)" "[video] autoplay next " "$NEXT"
record "(vii) the autoplay line" "$(echo "$NEXT" | sed 's/.*\[video\] //')"
sleep 1.5; show_paused "$D/cm4-vii-off.xml"
tap_node "$D/cm4-vii-off.xml" player_more; sleep 0.7; gdump "$D/cm4-vii-menu2.xml"; tap_node "$D/cm4-vii-menu2.xml" player_menu:autoplay; sleep 0.9
leave
assert_absent "(vii): Autoplay is off again (the player's settings as found)" 'value="true"' "$(adb shell run-as app.tileshell cat shared_prefs/video_player.xml 2>/dev/null | tr -d '\r')"
adb shell am force-stop app.tileshell; sleep 1
MARK="$(ring_mark)"
adb shell am start -W -n "$PHOTOS_ACTIVITY" >/dev/null 2>&1; sleep 3.5
scroll_to_node "$D/cm4-vii-photos.xml" "photos_item:$ID" 8 || true
if [ "$(has_node "$D/cm4-vii-photos.xml" "photos_item:$ID")" = yes ]; then
  tap_node "$D/cm4-vii-photos.xml" "photos_item:$ID"
  LINE="$(await_vline "$MARK" "[video] playing $ID" 120)"
  assert_contains "(vii) from Photos: it plays" "[video] playing $ID" "$LINE"
  absent_in "(vii) from Photos: the launch is the shell's own" "source from another app" "$(vring "$MARK")"
else
  _verdict FAIL "(vii) from Photos: the video's item on the collection" "no photos_item:$ID on the Photos collection page"
fi
leave
adb shell am force-stop app.tileshell; sleep 1

log "--- C-L1: a forged line in the address"
MARK="$(ring_mark)"
view_shell "http://10.0.2.2:1/a%0A[cred]%20x.mp4"; sleep 5
SL="$(vring "$MARK")"
record "C-L1: the :video lines" "$(printf '%s\n' "$SL" | grep -F '[video]' | sed 's/.*\[video\] //' | tr '\n' '|')"
assert_ne "C-L1: the slice holds the player's lines" "0" "$(printf '%s\n' "$SL" | grep -c 'wall=')"
adb shell dumpsys activity service "$VIDEO_RING" 2>/dev/null | tr -d '\r' > "$D/cl1-ring-raw.txt"
assert_eq "C-L1: no line of the :video ring begins with the forged [cred] x (the ring's line is ONE line)" "0" "$(grep -cE '^\s*\[cred\] x' "$D/cl1-ring-raw.txt")"
leave

# ================================================================================================ inside the guard
FLOG1="$D/fake-8097.log"; FLOG2="$D/fake-8098.log"; : > "$FLOG1"; : > "$FLOG2"
fixture_up || _verdict FAIL "the catalogue fixture on :$VPORT" "did not start"
fake_up "$FAKE_PORT" "$FLOG1" ok || _verdict FAIL "the fake media server on :$FAKE_PORT" "did not start"
guard_on
qa_pref qa_catalogue_base "$FIXTURE_URL/"
guard_restart "start"
MARK="$(ring_mark)"
view_shell "$CONTENT"; sleep 3.5
record "(vi) RECORDED, inside the guard (adbd is root, uid $(adb shell id -u | tr -d '\r')): the :video lines" "$(vtext "$MARK")"
leave

log "--- B-1 (1): a key with an interior CR, pasted"
key_page() { hub settings 2; dump_ui "$D/$1-s.xml"; tap_node "$D/$1-s.xml" hub_settings:tmdbkey; sleep 1; dump_ui "$D/$1-page.xml"; }
paste_key() { # name text
  key_page "$1"
  clip_set "$2"
  record "$1: the clipboard was set by the fixture app" "$(qaview_log 1 | sed 's/.*qa-view: //')"
  dump_ui "$D/$1-back.xml"
  [ "$(has_node "$D/$1-back.xml" tmdb_key_field)" = yes ] || { key_page "$1"; }
  tap_node "$D/$1-page.xml" tmdb_key_field; sleep 0.8
  adb shell input keyevent KEYCODE_PASTE; sleep 1
  dump_ui "$D/$1-pasted.xml"
  PASTED_LEN="$(node_text "$D/$1-pasted.xml" tmdb_key_field | python3 -c 'import sys; print(len(sys.stdin.read().rstrip("\n")))')"
  record "$1: the field's length after the paste (dots; the pasted text is ${#2} characters)" "$PASTED_LEN"
  PID_V="$(adb shell pidof app.tileshell:video | tr -d '\r')"
  PASTE_MARK="$(ring_mark)"
  tap_node "$D/$1-pasted.xml" tmdb_key_save; sleep 1.5
  dump_ui "$D/$1-saved.xml"
}
B1_MARK="$(ring_mark)"
paste_key b1-cr "$(printf '%s\r%s' "$CR_A" "$CR_B")"
assert_eq "B-1 (1): the paste reached the field (it is not empty, and not the placeholder)" "yes" "$([ "${PASTED_LEN:-0}" -gt 10 ] && [ "$(node_text "$D/b1-cr-pasted.xml" tmdb_key_field)" != "TMDB read access token" ] && echo yes || echo no)"
assert_eq "B-1 (1): tmdb_key_error is shown" "yes" "$(has_node "$D/b1-cr-saved.xml" tmdb_key_error)"
record "B-1 (1): its text" "$(node_text "$D/b1-cr-saved.xml" tmdb_key_error)"
assert_eq "B-1 (1): tmdb_key_status" "No key is saved." "$(node_text "$D/b1-cr-saved.xml" tmdb_key_status)"
assert_contains "B-1 (1): [video] TMDB key refused: not a key's characters" "[video] TMDB key refused: not a key's characters" "$(vring "$PASTE_MARK")"
assert_eq "B-1 (1): the :video pid is unchanged" "$PID_V" "$(adb shell pidof app.tileshell:video | tr -d '\r')"
assert_absent "B-1 (1): nothing is stored" "tmdb" "$(cred_names)"
B1_LOG="$(logcat_since "$B1_MARK")"
assert_ne "B-1 (1): the logcat read since the paste is live (its line count)" "0" "$(printf '%s\n' "$B1_LOG" | grep -c .)"
assert_no_secret "B-1 (1): logcat holds no part of the key (its first half)" "$CR_A" "$B1_LOG"
assert_no_secret "B-1 (1): … nor its second half" "$CR_B" "$B1_LOG"
assert_eq "B-1 (1): no AndroidRuntime line names the shell" "" "$(crash_since "$PASTE_MARK")"

log "--- B2-L7 (g): 2,001 pasted characters are refused; 2,000 are saved"
paste_key g-2001 "$KEY_2001"
assert_eq "(g) 2,001: the paste reached the field" "yes" "$([ "${PASTED_LEN:-0}" -gt 100 ] && echo yes || echo no)"
assert_eq "(g) 2,001: refused (tmdb_key_error shown)" "yes" "$(has_node "$D/g-2001-saved.xml" tmdb_key_error)"
assert_eq "(g) 2,001: tmdb_key_status" "No key is saved." "$(node_text "$D/g-2001-saved.xml" tmdb_key_status)"
assert_absent "(g) 2,001: nothing is stored (never stored cut)" "tmdb" "$(cred_names)"
record "(g) 2,001: the :video lines" "$(vtext "$PASTE_MARK")"
paste_key g-2000 "$KEY_2000"
assert_eq "(g) 2,000: saved (tmdb_key_status)" "A key is saved." "$(node_text "$D/g-2000-saved.xml" tmdb_key_status)"
assert_eq "(g) 2,000: no error is shown" "no" "$(has_node "$D/g-2000-saved.xml" tmdb_key_error)"
assert_eq "(g) 2,000: the store names the entry" "tmdb" "$(cred_names)"
tap_node "$D/g-2000-saved.xml" tmdb_key_remove; sleep 1.2
assert_absent "(g): the 2,000-character key is removed again" "tmdb" "$(cred_names)"
clip_clear
record "the clipboard emptied" "$(qaview_log 1 | sed 's/.*qa-view: //')"

log "--- B-1 (2): with a valid key, Browse opens"
key_enter "$D/b1-valid"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse 3.5; dump_ui "$D/b1-browse.xml"
assert_eq "B-1 (2): Browse opens (hub_page:browse, its search box)" "yes yes" "$(has_node "$D/b1-browse.xml" hub_page:browse) $(has_node "$D/b1-browse.xml" hub_search_box)"
assert_contains "B-1 (2): its requests went out with the key (bearer ok)" "bearer ok" "$(fixture_since "$OFF")"
assert_eq "B-1 (2): no crash (logcat -T <MARK> -s AndroidRuntime)" "" "$(crash_since "$MARK")"

log "--- B-1 (3): an AccessToken with a carriage return inside"
fake_mode cr
server_page "$D/b1-3"
MARK="$(ring_mark)"
server_form "$D/b1-3" "$FAKE_HOST" qa "$FAKE_PW"; sleep 4
dump_ui "$D/b1-3.xml"
B13="$(node_text "$D/b1-3.xml" server_error)$(node_text "$D/b1-3.xml" server_notice)"
assert_eq "B-1 (3): the page's text" "Can't reach your media server" "$B13"
assert_contains "B-1 (3): [video] server $FAKE_HOST: sign-in answer not usable" "[video] server $FAKE_HOST: sign-in answer not usable" "$(vring "$MARK")"
assert_eq "B-1 (3): no AndroidRuntime line" "" "$(crash_since "$MARK")"
assert_eq "B-1 (3): nothing is saved for the server" "tmdb" "$(cred_names)"

log "--- B-9: a sign-in answer over the size cap"
fake_mode huge
MARK="$(ring_mark)"
server_form "$D/b9" "$FAKE_HOST" qa "$FAKE_PW"; sleep 8
dump_ui "$D/b9.xml"
assert_eq "B-9: the page's text" "Can't reach your media server" "$(node_text "$D/b9.xml" server_error)$(node_text "$D/b9.xml" server_notice)"
assert_contains "B-9: [video] http: answer over the size cap" "[video] http: answer over the size cap" "$(vring "$MARK")"
assert_eq "B-9: no AndroidRuntime line" "" "$(crash_since "$MARK")"
assert_eq "B-9: nothing is saved for the server" "tmdb" "$(cred_names)"

sign_in_fake() { # name — the form on screen
  fake_mode ok
  local mark; mark="$(ring_mark)"
  server_form "$D/$1" "$FAKE_HOST" qa "$FAKE_PW"; sleep 4.5
  dump_ui "$D/$1-library.xml"
  assert_contains "$1: signed in to the fake server ([video] server $FAKE_HOST: connected)" "[video] server $FAKE_HOST: connected" "$(vring "$mark")"
  assert_eq "$1: its library lists the item" "yes" "$(has_node "$D/$1-library.xml" "server_item:$FAKE_ITEM")"
}
sign_in_fake signin
STREAM="http://$FAKE_HOST/Videos/$FAKE_ITEM/stream"

log "--- B2-M2 (e): the hub's own play carries the token"
hub mediaserver 4; dump_ui "$D/b5-library.xml"
OFF="$(flines "$FLOG1")"
MARK="$(ring_mark)"
tap_node "$D/b5-library.xml" "server_item:$FAKE_ITEM"
LINE="$(await_vline "$MARK" "[video] playing scheme=http" 120)"
assert_contains "(e) from the hub's Media server page: the item plays" "[video] playing scheme=http" "$LINE"
sleep 1.5
assert_contains "(e) from the hub: [video] server stream <the address, no query>" "[video] server stream $STREAM" "$(vring "$MARK")"
REQ="$(fsince "$FLOG1" "$OFF" | grep -F "/Videos/$FAKE_ITEM/stream")"
assert_ne "(e) from the hub: the request DOES carry the token (token=header or apikey=present in the fake server's log)" "0" "$(printf '%s\n' "$REQ" | grep -cE 'token=header|apikey=present')"
record "(e) from the hub: how the token rode (the log's fields of the first stream request)" "$(printf '%s\n' "$REQ" | head -1 | sed 's/^[0-9]* //')"
leave

log "--- B2-M2 (e): the same address by adb shell am start, and from a permission-less app"
OFF="$(flines "$FLOG1")"
MARK="$(ring_mark)"
view_shell "$STREAM?static=true"; sleep 5
SL="$(vring "$MARK")"
REQ="$(fsince "$FLOG1" "$OFF" | grep -F "/Videos/$FAKE_ITEM/stream")"
record "(e) adb shell am start (adbd's uid $(adb shell id -u | tr -d '\r')): the :video lines" "$(printf '%s\n' "$SL" | grep -F '[video]' | sed 's/.*\[video\] //' | tr '\n' '|')"
assert_contains "(e) adb shell am start: [video] server token not given: not the shell's own launch" "[video] server token not given: not the shell's own launch" "$SL"
absent_in "(e) adb shell am start: no [video] server stream line" "[video] server stream" "$SL"
assert_ne "(e) adb shell am start: the request reached the fake server" "" "$REQ"
assert_eq "(e) adb shell am start: no token and no ApiKey on it" "0" "$(printf '%s\n' "$REQ" | grep -cE 'token=header|apikey=present')"
leave
OFF="$(flines "$FLOG1")"
qa_leg b2m2 5 --es uri "$STREAM?static=true"
REQ="$(fsince "$FLOG1" "$OFF" | grep -F "/Videos/$FAKE_ITEM/stream")"
printf '%s\n' "$REQ" > "$D/b2m2-requests.txt"
assert_eq "(e) the app holds no permission" "false" "$(qgranted READ_MEDIA_VIDEO)"
assert_contains "(e) a permission-less app: [video] server token not given: not the shell's own launch" "[video] server token not given: not the shell's own launch" "$LEG_SLICE"
absent_in "(e) a permission-less app: no [video] server stream line" "[video] server stream" "$LEG_SLICE"
assert_ne "(e) a permission-less app: the request reached the fake server" "" "$REQ"
assert_eq "(e) a permission-less app: no token and no ApiKey on it" "0" "$(printf '%s\n' "$REQ" | grep -cE 'token=header|apikey=present')"
absent_in "(e) a permission-less app: no ApiKey in the :video ring" "ApiKey" "$LEG_SLICE"
leave

log "--- B-5: the hub's play, the server stopped mid-play"
hub mediaserver 4; dump_ui "$D/b5b-library.xml"
MARK="$(ring_mark)"
tap_node "$D/b5b-library.xml" "server_item:$FAKE_ITEM"
LINE="$(await_vline "$MARK" "[video] playing scheme=http" 120)"
assert_contains "B-5: the server's item plays from the hub's page" "[video] playing scheme=http" "$LINE"
sleep 1.5
fake_down "$FAKE_PORT"
sleep 6
SL="$(vring "$MARK")"
record "B-5: the :video lines after the server stopped" "$(printf '%s\n' "$SL" | grep -F '[video]' | sed 's/.*\[video\] //' | tr '\n' '|')"
absent_in "B-5: the :video ring holds no ApiKey=" "ApiKey=" "$SL"
B5_LOG="$(logcat_since "$MARK")"
assert_ne "B-5: the logcat read since the play began is live (its line count)" "0" "$(printf '%s\n' "$B5_LOG" | grep -c .)"
assert_eq "B-5: logcat holds no ApiKey= (lines since the play began)" "0" "$(printf '%s\n' "$B5_LOG" | grep -c 'ApiKey=')"
assert_no_secret "B-5: … and no line of logcat holds the token" "$FAKE_TOKEN" "$B5_LOG"
leave
fake_up "$FAKE_PORT" "$FLOG1" revoked || _verdict FAIL "the fake media server started again" "it did not"

log "--- B-2: the token made invalid, the page reopened"
MARK="$(ring_mark)"; hub mediaserver 4.5; dump_ui "$D/b2.xml"
assert_contains "B-2: [video] server $FAKE_HOST: unauthorised" "[video] server $FAKE_HOST: unauthorised" "$(vring "$MARK")"
assert_eq "B-2: the sign-in form again (server_connect)" "yes" "$(has_node "$D/b2.xml" server_connect)"
assert_eq "B-2: server_host is prefilled with the address, its scheme shown and kept" "http://$FAKE_HOST" "$(node_text "$D/b2.xml" server_host)"
fake_mode ok
tap_node "$D/b2.xml" server_password; sleep 0.6; adb shell input text "$FAKE_PW"; adb shell input keyevent KEYCODE_BACK; sleep 0.6
MARK="$(ring_mark)"
dump_ui "$D/b2-typed.xml"; tap_node "$D/b2-typed.xml" server_connect; sleep 4.5
assert_contains "B-2: signing in again from the prefilled form connects (the scheme was kept)" "[video] server $FAKE_HOST: connected" "$(vring "$MARK")"

log "--- B-4 (a): media_server.json edited to another host"
fake_up 8098 "$FLOG2" ok || _verdict FAIL "the second fake server on :8098" "did not start"
rings_save; adb shell am force-stop app.tileshell; sleep 0.5
adb shell run-as app.tileshell cat files/media_server.json 2>/dev/null | tr -d '\r' > "$D/media_server-before.json"
record "B-4 (a): the fields media_server.json holds (display fields only)" "$(python3 -c 'import json, sys; print(" ".join(sorted(json.load(open(sys.argv[1])).keys())))' "$D/media_server-before.json" 2>/dev/null)"
assert_no_secret "B-4 (a): media_server.json does not hold the token" "$FAKE_TOKEN" "$(cat "$D/media_server-before.json")"
sed "s/:$FAKE_PORT/:8098/g" "$D/media_server-before.json" > "$D/media_server-edited.json"
assert_ne "B-4 (a): the edit changed the file (every :$FAKE_PORT is :8098)" "$(cat "$D/media_server-before.json")" "$(cat "$D/media_server-edited.json")"
adb shell "run-as app.tileshell sh -c 'cat > files/media_server.json'" < "$D/media_server-edited.json"
OFF1="$(flines "$FLOG1")"
MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/b4a.xml"
record "B-4 (a): the :video lines" "$(vtext "$MARK")"
assert_ne "B-4 (a): the library still goes to the FIRST server (its log holds the request, with the token)" "0" "$(fsince "$FLOG1" "$OFF1" | grep -F ' /Items' | grep -c 'token=header')"
assert_eq "B-4 (a): the library is listed" "yes" "$(has_node "$D/b4a.xml" "server_item:$FAKE_ITEM")"
assert_eq "B-4 (a): the other host saw NO request at all" "0" "$(flines "$FLOG2")"
curl -s -o /dev/null "http://127.0.0.1:8098/System/Info/Public"
assert_eq "B-4 (a): the control — the other host's log is live (the driver's own request is its one line)" "1" "$(flines "$FLOG2")"
rings_save; adb shell am force-stop app.tileshell; sleep 0.5
adb shell "run-as app.tileshell sh -c 'cat > files/media_server.json'" < "$D/media_server-before.json"

log "--- B-4 (b): the sealed entry corrupted"
# The store's file never leaves the device: it is edited there (a few characters of the jellyfin entry swapped).
edit_store() { # python statements over d (the parsed file). The file is READ first, whole, into this shell (it holds
  # ciphertext only), then written back: a single pipe would truncate it before it was read (this driver's run 1).
  local before after
  before="$(adb shell run-as app.tileshell cat files/credentials_v1.json 2>/dev/null)"
  after="$(printf '%s' "$before" | python3 -c '
import json, sys
d = json.load(sys.stdin)
exec(sys.argv[1])
sys.stdout.write(json.dumps(d))' "$1")" || return 1
  [ -n "$after" ] || return 1
  printf '%s' "$after" | adb shell "run-as app.tileshell sh -c 'cat > files/credentials_v1.json'"
}
edit_store '
def spoil(v):
    if isinstance(v, str) and len(v) > 12: return v[:6] + ("AAAA" if v[6:10] != "AAAA" else "BBBB") + v[10:]
    if isinstance(v, dict): return {k: spoil(x) for k, x in v.items()}
    return v
d["jellyfin"] = spoil(d["jellyfin"])'
assert_eq "B-4 (b): the store still names both entries (the jellyfin one is now spoiled)" "jellyfin tmdb" "$(cred_names)"
OFF1="$(flines "$FLOG1")"
MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/b4b.xml"
record "B-4 (b): the :video lines" "$(vring "$MARK" | grep -E '\[(video|cred)\]' | sed 's/.*wall=[0-9]* //' | tr '\n' '|')"
# The fixes file's first table says "the page shows Sign in again". Round 2 (B2-M1) changed what happens first: an entry
# that does not open is removed at the hub's start, so there is no server any more — the Media server page falls back
# to My videos, and the sign-in form is the one under Settings → Media server. Asserted as built after round 2, and
# reported for the lead: the first table's wording is the older one.
SL4="$(vring "$MARK")"
assert_contains "B-4 (b): the spoiled entry does not open ([cred] jellyfin: unreadable …)" "[cred] jellyfin: unreadable" "$SL4"
assert_contains "B-4 (b): … and is cleared at the hub's start" "[video] server entry cleared at start: it was not a saved server" "$SL4"
assert_eq "B-4 (b): no library is shown (the page falls back to My videos)" "yes no" "$(has_node "$D/b4b.xml" hub_page:myvideos) $(has_node "$D/b4b.xml" "server_item:$FAKE_ITEM")"
server_page "$D/b4b-settings"; dump_ui "$D/b4b-form.xml"
assert_eq "B-4 (b): Settings → Media server shows the sign-in form again (server_connect), no left-over entry" "yes no" "$(has_node "$D/b4b-form.xml" server_connect) $(has_node "$D/b4b-form.xml" server_left_over)"
assert_eq "B-4 (b): no request with the token left for the server" "0" "$(fsince "$FLOG1" "$OFF1" | grep -c 'token=header')"
assert_eq "B-4 (b): no AndroidRuntime line" "" "$(crash_since "$MARK")"

log "--- B2-M1 (b): media_server.json replaced with one lacking pair, no restart"
dump_ui "$D/m1b-form.xml"
[ "$(has_node "$D/m1b-form.xml" server_connect)" = yes ] || server_page "$D/m1bp"
sign_in_fake m1b
adb shell run-as app.tileshell cat files/media_server.json 2>/dev/null | tr -d '\r' > "$D/media_server-paired.json"
assert_contains "(b): the pages' file carries the save id (pair)" "pair" "$(python3 -c 'import json, sys; print(" ".join(sorted(json.load(open(sys.argv[1])).keys())))' "$D/media_server-paired.json" 2>/dev/null)"
python3 -c 'import json, sys; d = json.load(open(sys.argv[1])); d.pop("pair", None); sys.stdout.write(json.dumps(d))' "$D/media_server-paired.json" | adb shell "run-as app.tileshell sh -c 'cat > files/media_server.json'"
# "Without restarting": the page is reached through the hub's own pane (≡ → Settings → Media server), not by a new
# start of the activity — a start is when the sweep of leg (a) runs.
dump_ui "$D/m1b-0.xml"; tap_node "$D/m1b-0.xml" hub_menu; sleep 0.9
dump_ui "$D/m1b-pane.xml"; tap_node "$D/m1b-pane.xml" hub_pane:settings; sleep 1
dump_ui "$D/m1b-s.xml"; tap_node "$D/m1b-s.xml" hub_settings:server; sleep 1.2
dump_ui "$D/m1b-page.xml"
assert_eq "(b): Settings → Media server shows server_left_over and server_remove" "yes yes" "$(has_node "$D/m1b-page.xml" server_left_over) $(has_node "$D/m1b-page.xml" server_remove)"
record "(b): server_left_over's text" "$(node_text "$D/m1b-page.xml" server_left_over)"
MARK="$(ring_mark)"
tap_node "$D/m1b-page.xml" server_remove; sleep 1.8
assert_contains "(b): Remove → [video] server token cleared" "[video] server token cleared" "$(vring "$MARK")"
assert_absent "(b): the sealed entry is gone" "jellyfin" "$(cred_names)"

log "--- B2-M1 (a): media_server.json removed while a server is set up"
dump_ui "$D/m1a-form.xml"
[ "$(has_node "$D/m1a-form.xml" server_connect)" = yes ] || server_page "$D/m1a"
sign_in_fake m1a
assert_contains "(a): the dynamic shortcut is published before the removal" "video_mediaserver" "$(shortcut_dump)"
rings_save; adb shell am force-stop app.tileshell; sleep 0.5
adb shell run-as app.tileshell rm -f files/media_server.json
assert_contains "(a): the sealed entry is there, the pages' file is not" "jellyfin" "$(cred_names) / $(store_files)"
MARK="$(ring_mark)"; hub myvideos 4
SL="$(vring "$MARK")"
record "(a): the :video and [cred] lines at the hub's start" "$(printf '%s\n' "$SL" | grep -E '\[(video|cred)\]' | sed 's/.*wall=[0-9]* //' | tr '\n' '|')"
assert_contains "(a): [cred] jellyfin: removed" "[cred] jellyfin: removed" "$SL"
assert_contains "(a): [video] server entry cleared at start: it was not a saved server" "[video] server entry cleared at start: it was not a saved server" "$SL"
assert_absent "(a): the store no longer names the entry" "jellyfin" "$(cred_names)"
assert_eq "(a): no Media server row in the pane (My videos' row read first)" "myvideos no" "$(pane_current "$D/m1a-pane") $(has_node "$D/m1a-pane-pane.xml" hub_pane:mediaserver)"
SC="$(shortcut_dump)"
assert_contains "(a): the shortcut dump is the shell's" "video_myvideos" "$SC"
assert_absent "(a): no Media server shortcut" "video_mediaserver" "$SC"
OFF1="$(flines "$FLOG1")"
view_shell "$STREAM?static=true"; sleep 5
assert_eq "(a): a later stream carries no token (the fake server's log)" "0" "$(fsince "$FLOG1" "$OFF1" | grep -cE 'token=header|apikey=present')"
assert_ne "(a): … and that stream request is in the log read" "0" "$(fsince "$FLOG1" "$OFF1" | grep -c '/stream')"
leave

log "--- B2-M1 (c): a normal removal"
server_page "$D/m1c"; sign_in_fake m1c
server_page "$D/m1c-rm"; dump_ui "$D/m1c-rm.xml"
MARK="$(ring_mark)"
tap_node "$D/m1c-rm.xml" server_remove; sleep 1.8
assert_contains "(c): a normal removal writes [video] server token cleared" "[video] server token cleared" "$(vring "$MARK")"
absent_in "(c): … and not the not-removed line" "server not removed" "$(vring "$MARK")"
assert_absent "(c): the sealed entry is gone" "jellyfin" "$(cred_names)"
record "B2-L8 (h) NOT RUN" "a fresh install with a PIN needs a wipe of the shared device (pm clear / uninstall are forbidden to this row): the lead's liveness edge or a phone row"

log "--- B2-M3: the sealed jellyfin entry's fields moved under the tmdb name"
server_page "$D/m3"; sign_in_fake m3
rings_save; adb shell am force-stop app.tileshell; sleep 0.5
edit_store 'd["tmdb"] = d.pop("jellyfin")'
assert_eq "B2-M3: the store names one entry, tmdb, holding the server's sealed fields" "tmdb" "$(cred_names)"
OFF="$(fixture_lines)"; OFF1="$(flines "$FLOG1")"
MARK="$(ring_mark)"; hub browse 4; dump_ui "$D/m1b-browse.xml"
record "B2-M3: Browse's line, and the :video / [cred] lines" "$(node_text "$D/m1b-browse.xml" hub_browse_notice) — $(vring "$MARK" | grep -E '\[(video|cred)\]' | sed 's/.*wall=[0-9]* //' | tr '\n' '|')"
assert_eq "B2-M3: the key reads as absent: Browse offers the link to the setting and no search box" "yes no" "$(has_node "$D/m1b-browse.xml" hub_key_link) $(has_node "$D/m1b-browse.xml" hub_search_box)"
assert_eq "B2-M3: no request carrying a bearer left for the catalogue" "0" "$(fixture_since "$OFF" | grep -c 'bearer ok')"
MARK="$(ring_mark)"; hub mediaserver 4; dump_ui "$D/m1b-server.xml"
assert_eq "B2-M3: the server reads as absent: no library, no request with the token" "no 0" "$(has_node "$D/m1b-server.xml" "server_item:$FAKE_ITEM") $(fsince "$FLOG1" "$OFF1" | grep -c 'token=header')"
assert_eq "B2-M3: no AndroidRuntime line" "" "$(crash_since "$MARK")"

# ================================================================================================ restore, leak scan
log "--- restore"
server_remove_if_saved
key_remove_if_saved
if [ -n "$(cred_names)" ] || [ -n "$(store_files)" ]; then
  record "restore: entries the pages could not remove (the row's own spoiled / moved ones) — the two store files are removed" "$(cred_names) / $(store_files)"
  rings_save; adb shell am force-stop app.tileshell; sleep 0.5
  adb shell run-as app.tileshell rm -f files/credentials_v1.json files/media_server.json
fi
assert_eq "restore: the store is empty" "" "$(cred_names)"
assert_absent "restore: no dynamic shortcut is left" "video_mediaserver" "$(shortcut_dump)"
guard_off
qa_pref qa_catalogue_base --remove
assert_eq "the QA pref is removed" "" "$(qa_pref_now qa_catalogue_base)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
fixture_down; fake_down "$FAKE_PORT"; fake_down 8098
adb uninstall "$QAVIEW" >/dev/null 2>&1
assert_eq "the QA View fixture app is uninstalled" "" "$(adb shell pm list packages "$QAVIEW" | tr -d '\r')"
media_down
videos_grant_restore
quiet_off
c6; ensure_start
leak_scan_row "$FAKE_TOKEN" "$FAKE_PW" "$DUMMY_TOKEN" "$CR_A" "$CR_B" "qa-long-key-kkkkkkkk" "qa-full-key-ffffffff"
row_end
