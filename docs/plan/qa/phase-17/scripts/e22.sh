#!/usr/bin/env bash
# Phase 17 E22 — the media server (Jellyfin — Q-B; the pinned container on the host, its digest in the row log —
# T17-21), inside the egress guard. The doc's clauses → this driver's legs:
#
#   key         the dummy TMDB token saved first, as E20 enters it, so the credential store holds both tokens
#   wrong pw    THE LEAD'S RULING: tried BEFORE a server exists → "That password isn't right",
#               `[video] server 10.0.2.2:8096: unauthorised`, no pane row (hub_pane:myvideos asserted in the same dump
#               first — r3 V7)
#   insecure    (C-16 (5); Q-D A; the fixes file's B-3) "Add a server" at http://192.0.2.10:8096 → the page asks "This
#               server isn't secure — your password would be sent unencrypted", `[video] server 192.0.2.10:8096:
#               insecure, asked`; Cancel sends nothing: no connected / unreachable line after it (absent_in), the
#               guard's count still 0, and no `cleartext refused` line (r3 V6)
#   add         host 10.0.2.2:8096, user, password → `[video] server 10.0.2.2:8096: connected`; the Media server row
#               in the pane (hub_pane:mediaserver); its page lists qa-steps
#   play        a tap on the item ON THE HUB'S OWN PAGE (the token rides only on the shell's own launch) → it plays
#               under E11's pixel rule with `[video] playing scheme=http`; the stream URL in the :video slice carries
#               no query string
#   not ours    round 2 (B2-M2): the same stream address by `adb shell am start` → `[video] server token not given: not
#               the shell's own launch`, no `server stream` line; the server's answer to it RECORDED
#   token       Change Log (4): read with jellyfin_fixture.sh token-of (the server's own database; GET /Devices
#               carries none on 12.1), asserted non-empty, NEVER printed (compared without printing)
#   persisted   am force-stop and reopen → still connected
#   plaintext   as root (the guard's span): `grep -rlF <token> /data/data/app.tileshell/`, the fixture account's
#               password and the TMDB token each list no file; a control text the app does store IS found
#   stop/start  `docker stop` → "Can't reach your media server", `[video] server …: unreachable`, My videos untouched,
#               no crash; `docker start` → reconnects on the next open
#   remove      "Remove this server" → `[video] server token cleared`, the pane row and the dynamic shortcut gone, the
#               exact-token grep → no file (T17-23)
#   leak scan   leak_scan.sh --logcat --path <this row's folder> -- <token> <password> <TMDB token> (gated)
#   guard       the weather lines after each restart; 0 packets elsewhere
#
# Restores: the guard, the server removed, the key removed, the container and its volumes removed (by recorded id).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap video_cleanup EXIT
PUBLIC="192.0.2.10:8096"; WRONG="not-the-password"

video_row_begin E22 "the media server: wrong password, the insecure prompt, sign-in, library, play, persistence, plaintext, stop / start, removal"
quiet_on
jf_up || { _verdict FAIL "the Jellyfin fixture" "did not come up: $(tail -2 "$D/jellyfin-up.txt" | tr '\n' ' ')"; row_end; exit 5; }
ITEM="$SERVER_ITEM"
assert_eq "precondition: no server is set up and no key is saved" "" "$(cred_names)"
assert_eq "precondition: the server knows no device of the shell's" "0" "$(jf count-of Tessera)"
guard_on
guard_restart "start"
key_enter "$D/key"

# ------------------------------------------------------------------------------------------------ wrong password
log "--- a wrong password, before a server exists"
server_page "$D/wrong"
MARK="$(ring_mark)"
server_form "$D/wrong" "$SERVER_HOST" "$SERVER_USER" "$WRONG"; sleep 3.5
dump_ui "$D/wrong.xml"
assert_eq "wrong password: the page's text" "That password isn't right" "$(node_text "$D/wrong.xml" server_error)"
assert_contains "wrong password: [video] server $SERVER_HOST: unauthorised" "[video] server $SERVER_HOST: unauthorised" "$(vring "$MARK")"
tap_node "$D/wrong.xml" hub_menu; sleep 0.9; dump_ui "$D/wrong-pane.xml"
assert_eq "wrong password: the pane is the one read (hub_pane:myvideos) and has no Media server row" "yes no" "$(has_node "$D/wrong-pane.xml" hub_pane:myvideos) $(has_node "$D/wrong-pane.xml" hub_pane:mediaserver)"
adb shell input tap 960 1200; sleep 0.6
assert_eq "wrong password: nothing is saved for the server" "tmdb" "$(cred_names)"

# ------------------------------------------------------------------------------------------------ insecure address
log "--- an insecure address: asked first, Cancel sends nothing"
MARK="$(ring_mark)"
server_form "$D/ask" "http://$PUBLIC" "$SERVER_USER" "$SERVER_PW"; sleep 2
dump_ui "$D/asked.xml"
assert_eq "insecure: the page asks" "This server isn't secure — your password would be sent unencrypted" "$(node_text "$D/asked.xml" server_insecure)"
assert_contains "insecure: [video] server $PUBLIC: insecure, asked" "[video] server $PUBLIC: insecure, asked" "$(vring "$MARK")"
tap_node "$D/asked.xml" server_insecure_cancel; sleep 2.5
dump_ui "$D/cancelled.xml"
SLICE="$(vring "$MARK")"
assert_eq "Cancel: the form is back" "yes" "$(has_node "$D/cancelled.xml" server_connect)"
for word in connected unreachable unauthorised "cleartext refused"; do absent_in "Cancel: no '$word' line after it" "server $PUBLIC: $word" "$SLICE"; done
assert_eq "Cancel sends nothing: the egress guard's count stays 0" "0" "$(egress_guard_count)"
assert_eq "Cancel: nothing is saved for the server" "tmdb" "$(cred_names)"

# ------------------------------------------------------------------------------------------------ add
log "--- Add a server"
MARK="$(ring_mark)"
server_form "$D/add" "$SERVER_HOST" "$SERVER_USER" "$SERVER_PW"; sleep 4.5
dump_ui "$D/library.xml"
SLICE="$(vring "$MARK")"
assert_contains "[video] server $SERVER_HOST: connected" "[video] server $SERVER_HOST: connected" "$SLICE"
assert_eq "the Media server page lists qa-steps.mp4 (its caption)" "qa-steps" "$(node_text "$D/library.xml" "server_caption:$ITEM")"
tap_node "$D/library.xml" hub_menu; sleep 0.9; dump_ui "$D/pane.xml"
assert_eq "the Media server row appears in the pane (hub_pane:mediaserver)" "yes" "$(has_node "$D/pane.xml" hub_pane:mediaserver)"
adb shell input tap 960 1200; sleep 0.6
assert_eq "the credential store holds both tokens (their names)" "jellyfin tmdb" "$(cred_names)"
TOKEN="$(jf token-of Tessera)"
assert_eq "the token the server issued the shell is read (token-of), non-empty: its length" "32" "${#TOKEN}"

# ------------------------------------------------------------------------------------------------ play
log "--- the item played from the hub's own page"
MARK="$(ring_mark)"
dump_ui "$D/library2.xml"; tap_node "$D/library2.xml" "server_item:$ITEM"
LINE="$(await_vline "$MARK" "[video] playing scheme=http" 120)"
assert_contains "play: [video] playing scheme=http" "[video] playing scheme=http" "$LINE"
shot_at "$(wall_of "$LINE")" 3500 "$D/play-3.5s.png"
assert_pixel_rule "the server's item at 3.5 s" 3 "$SHOT_RGB"
assert_eq "play: the shared player is on top" "$PLAYER_ACTIVITY" "$(top_activity)"
SLICE="$(vring "$MARK")"
STREAM="$(printf '%s\n' "$SLICE" | grep -F '/stream' | sed 's/.*\[video\] //')"
record "play: the :video lines that name the stream" "$(printf '%s' "$STREAM" | tr '\n' '|')"
assert_ne "play: the :video slice names the stream URL" "" "$STREAM"
assert_eq "play: the stream URL in the :video slice carries no query string (no '?' in those lines)" "0" "$(printf '%s\n' "$STREAM" | grep -c '?')"
assert_no_secret "play: the token is in no :video line" "$TOKEN" "$SLICE"
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 1.2
# Round 2 (B2-M2 (e)): the same stream address by `adb shell am start` — not the shell's own launch, so no token rides.
# What the real server then answers is RECORDED (the fixer expected 401; 12.1 serves a direct-play stream with no key).
MARK="$(ring_mark)"
view_shell "http://$SERVER_HOST/Videos/$ITEM/stream?static=true"
sleep 5
SLICE="$(vring "$MARK")"
assert_contains "the stream address by adb shell am start: [video] server token not given: not the shell's own launch" "[video] server token not given: not the shell's own launch" "$SLICE"
absent_in "… and no [video] server stream line for it" "[video] server stream" "$SLICE"
record "RECORDED — what Jellyfin 12.1 answers a stream request with no token (the player's lines)" "$(printf '%s\n' "$SLICE" | grep -E '\[video\] (playing|cannot)' | sed 's/.*\[video\] //' | tr '\n' '|')"
assert_no_secret "… the token is in no :video line of that launch" "$TOKEN" "$SLICE"
rings_save
adb shell input keyevent KEYCODE_BACK; sleep 1.2

# ------------------------------------------------------------------------------------------------ persisted, plaintext
log "--- force-stop and reopen; the store holds no plaintext"
guard_restart "persisted"
MARK="$(ring_mark)"; hub mediaserver 4.5; dump_ui "$D/reopened.xml"
assert_contains "after a force-stop: still connected (the token persisted)" "[video] server $SERVER_HOST: connected" "$(vring "$MARK")"
assert_eq "after a force-stop: the library is listed" "yes" "$(has_node "$D/reopened.xml" "server_item:$ITEM")"
assert_eq "root grep -rlF <the issued token> /data/data/app.tileshell/: no file" "" "$(root_files_holding "$TOKEN")"
assert_eq "root grep for the fixture account's password: no file" "" "$(root_files_holding "$SERVER_PW")"
assert_eq "root grep for the TMDB token: no file" "" "$(root_files_holding "$DUMMY_TOKEN")"
assert_ne "the control: the same root grep DOES find a text the app stores (the server's host)" "" "$(root_files_holding "$SERVER_HOST")"

# ------------------------------------------------------------------------------------------------ stop / start
log "--- docker stop, docker start"
jf stop > "$D/docker-stop.txt" 2>&1
MARK="$(ring_mark)"; hub mediaserver 6; dump_ui "$D/stopped.xml"
assert_eq "stopped: the page shows" "Can't reach your media server" "$(node_text "$D/stopped.xml" server_notice)"
assert_contains "stopped: [video] server $SERVER_HOST: unreachable" "[video] server $SERVER_HOST: unreachable" "$(vring "$MARK")"
hub myvideos 2.5; dump_ui "$D/stopped-mine.xml"
assert_eq "stopped: the My videos page is untouched" "yes" "$(has_node "$D/stopped-mine.xml" hub_page:myvideos)"
assert_eq "stopped: no crash (logcat -T <MARK> -s AndroidRuntime)" "" "$(crash_since "$MARK")"
jf start > "$D/docker-start.txt" 2>&1; jf_wait
MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/restarted.xml"
assert_contains "started: it reconnects on the next open" "[video] server $SERVER_HOST: connected" "$(vring "$MARK")"
assert_eq "started: the library is back" "yes" "$(has_node "$D/restarted.xml" "server_item:$ITEM")"

# ------------------------------------------------------------------------------------------------ remove
log "--- Remove the server"
assert_contains "before removal: dumpsys shortcut lists video_mediaserver" "video_mediaserver" "$(shortcut_dump)"
server_page "$D/rm"; dump_ui "$D/rm.xml"
MARK="$(ring_mark)"
tap_node "$D/rm.xml" server_remove; sleep 1.8
dump_ui "$D/removed.xml"
SLICE="$(vring "$MARK")"
assert_contains "removed: [video] server token cleared" "[video] server token cleared" "$SLICE"
tap_node "$D/removed.xml" hub_menu; sleep 0.9; dump_ui "$D/removed-pane.xml"
assert_eq "removed: the pane (hub_pane:myvideos read first) has no Media server row" "yes no" "$(has_node "$D/removed-pane.xml" hub_pane:myvideos) $(has_node "$D/removed-pane.xml" hub_pane:mediaserver)"
adb shell input tap 960 1200; sleep 0.6
SC="$(shortcut_dump)"
assert_contains "removed: the shortcut dump is the shell's (video_myvideos is there)" "video_myvideos" "$SC"
assert_absent "removed: the dynamic shortcut is gone" "video_mediaserver" "$SC"
assert_eq "removed: the exact-token root grep → no file (T17-23)" "" "$(root_files_holding "$TOKEN")"
assert_eq "removed: the store holds the TMDB key only" "tmdb" "$(cred_names)"

# ------------------------------------------------------------------------------------------------ restore, leak scan
log "--- restore"
key_remove_if_saved
assert_eq "the TMDB key is removed at the row's end (RV12): the store is empty" "" "$(cred_names)"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
guard_off
jf_down
assert_eq "the container and its volumes are removed (no recorded id left)" "no" "$([ -f "$JF_WORK/container.id" ] && echo yes || echo no)"
quiet_off
c6; ensure_start
leak_scan_row "$TOKEN" "$SERVER_PW" "$DUMMY_TOKEN" "$WRONG"
row_end
