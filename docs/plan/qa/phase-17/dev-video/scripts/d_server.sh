#!/usr/bin/env bash
# Phase 17 development proof, build tasks 13 and 15 (brief D; E22's and E23's forms): the pinned Jellyfin 12.1 container,
# a wrong password, "Add a server", the library, direct play, the token persisting across a force-stop and held only as
# ciphertext, the stopped container, a token the server no longer accepts, removal clearing the token and the dynamic
# shortcut. Restores: the server removed from the app, the TMDB key removed, the container and its volumes removed.
. "$(dirname "$0")/v17.sh"
row_begin D_SERVER "the media server: sign-in, library, play, persistence, plaintext, failures, removal"
D="$ROW_DIR"
colour() { sed -n "s/^$1 //p" "$FIX/colours.txt"; }
HOST=10.0.2.2:8096; PW=qa-password
jf_up || { echo "the Jellyfin fixture did not come up: $(tail -3 "$D/jellyfin-up.txt")" >&2; exit 5; }
trap 'jf down >/dev/null' EXIT
record "fixture image" "$(sed -n 's/^image //p' "$D/jellyfin-up.txt" | head -1)"
record "fixture container" "$(cut -c1-12 "$JF_WORK/container.id")"
ITEM="$(cat "$JF_WORK/item.id")"
adb shell am force-stop app.tileshell
assert_absent "no server token is saved at the start" "jellyfin" "$(cred_names)"
qa_pref qa_catalogue_base "http://10.0.2.2:$V17_PORT/"
# The credential store holds both secrets in this row (E22): the TMDB key first.
hub settings 2; dump_ui "$D/s0.xml"; tap_node "$D/s0.xml" hub_settings:tmdbkey; sleep 1
key_type_and_save "$D/key"
assert_eq "the TMDB key is saved" "A key is saved." "$(node_text "$D/key-saved.xml" tmdb_key_status)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- a wrong password: "That password isn't right", unauthorised, nothing saved, no pane row
dump_ui "$D/s1.xml"; tap_node "$D/s1.xml" hub_settings:server; sleep 1
MARK="$(ring_mark)"
server_form "$D/wrong" "$HOST" qa not-the-password; sleep 3
dump_ui "$D/wrong.xml"
assert_eq "wrong password: the page's text" "That password isn't right" "$(node_text "$D/wrong.xml" server_error)"
assert_contains "wrong password: the line" "[video] server $HOST: unauthorised" "$(vring "$MARK")"
assert_absent "wrong password: no token saved" "jellyfin" "$(cred_names)"
assert_eq "wrong password: the password field is empty again" "" "$(node_text "$D/wrong.xml" server_password)"
assert_no_secret "wrong password: the typed password is in no dump" "not-the-password" "$(cat "$D/wrong-typed.xml" "$D/wrong.xml")"
tap_node "$D/wrong.xml" hub_menu; sleep 0.8; dump_ui "$D/wrong-pane.xml"
assert_eq "wrong password: My videos is in the pane…" "yes" "$(has_node "$D/wrong-pane.xml" hub_pane:myvideos)"
assert_eq "…and no Media server row" "no" "$(has_node "$D/wrong-pane.xml" hub_pane:mediaserver)"
adb shell input tap 960 1200; sleep 0.6
assert_absent "no dynamic shortcut yet" "video_mediaserver" "$(shortcut_dump)"

# ---- Add a server: connected, the pane row, the dynamic shortcut, the library
MARK="$(ring_mark)"
server_form "$D/add" "$HOST" qa "$PW"; sleep 4
dump_ui "$D/library.xml"
SLICE="$(vring "$MARK")"
assert_contains "[video] server <host>: connected" "[video] server $HOST: connected" "$SLICE"
assert_contains "[video] shortcut mediaserver published" "[video] shortcut mediaserver published" "$SLICE"
assert_eq "the Media server page is shown" "yes" "$(has_node "$D/library.xml" hub_page:mediaserver)"
assert_eq "the library lists the fixture's video" "qa-steps" "$(node_text "$D/library.xml" "server_caption:$ITEM")"
assert_near "its tile is My videos' 112 × 112 at x 12 (Y10)" "12 124 112 112" "$(set -- $(epx "$D/library.xml" "server_item:$ITEM"); python3 -c "print($1, $3, round($3-$1,1), round($4-$2,1))")" 0.5
assert_no_secret "the typed password is in no dump" "$PW" "$(cat "$D/add-typed.xml" "$D/library.xml")"
tap_node "$D/library.xml" hub_menu; sleep 0.8; dump_ui "$D/pane.xml"
assert_contains "the pane has its Media server row, current" 'selected="true"' "$(grep -o '<node[^>]*resource-id="hub_pane:mediaserver"[^>]*>' "$D/pane.xml")"
adb shell input tap 960 1200; sleep 0.6
SC="$(shortcut_dump)"; echo "$SC" > "$D/shortcuts.txt"
assert_contains "dumpsys shortcut lists video_mediaserver" "video_mediaserver" "$SC"
assert_contains "…as a dynamic shortcut of VideoActivity" "app.tileshell/app.tileshell.video.VideoActivity" "$(echo "$SC" | grep -A14 'id=video_mediaserver' | grep -iE 'activity=')"
record "the shortcut's flags line" "$(echo "$SC" | grep -A6 'id=video_mediaserver' | grep -iE 'flags=' | head -1 | xargs)"
assert_eq "the token the server issued is in the store's names" "jellyfin tmdb" "$(cred_names)"
TOKEN="$(jf token-of Tessera)"
assert_eq "the server issued the shell (AppName Tessera) a 32-character token" "32" "${#TOKEN}"

# ---- direct play in the shared player
MARK="$(ring_mark)"
tap_node "$D/library.xml" "server_item:$ITEM"
LINE="$(await_vline "$MARK" "[video] playing scheme=http" 100)"
assert_contains "play: [video] playing scheme=http" "[video] playing scheme=http" "$LINE"
T0="$(wall_of "$LINE")"; while [ "$(device_ms)" -lt $((T0 + 3200)) ]; do sleep 0.05; done
screencap "$D/play-3.5s.png"
assert_colour "play: the picture at ≈3.5 s is colour 3" "$(colour 3)" "$(pixel "$D/play-3.5s.png" 540 1098)" 20
assert_eq "play: the shared player is on top" "app.tileshell/.video.PlayerActivity" "$(top)"
SLICE="$(vring "$MARK")"
assert_contains "play: the stream's line has no query string" "[video] server stream http://$HOST/Videos/$ITEM/stream" "$SLICE"
assert_absent "play: no line names ApiKey" "ApiKey" "$SLICE"
assert_no_secret "play: the token is in no :video line" "$TOKEN" "$SLICE"
for r in $RINGS; do ring_save "$r"; done
adb shell input keyevent KEYCODE_BACK; sleep 1.2

# ---- the token persisted, and only as ciphertext
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"; hub mediaserver 4; dump_ui "$D/reopened.xml"
assert_contains "after a force-stop: still connected" "[video] server $HOST: connected" "$(vring "$MARK")"
assert_eq "after a force-stop: the library is listed" "yes" "$(has_node "$D/reopened.xml" "server_item:$ITEM")"
assert_eq "no file of the app holds the server's token in the clear" "" "$(app_files_holding "$TOKEN")"
assert_eq "no file of the app holds the fixture password" "" "$(app_files_holding "$PW")"
assert_eq "no file of the app holds the TMDB token in the clear" "" "$(app_files_holding "$DUMMY_TOKEN")"
assert_ne "the control: the same search DOES find a text the app stores (the server's host in media_server.json)" "" "$(app_files_holding "$HOST")"
for r in $RINGS; do ring_save "$r"; done

# ---- the container stopped: "Can't reach your media server", My videos intact; started: connected on the next open
jf stop >/dev/null
MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/stopped.xml"
assert_eq "stopped: the page's text" "Can't reach your media server" "$(node_text "$D/stopped.xml" server_notice)"
assert_contains "stopped: the line" "[video] server $HOST: unreachable" "$(vring "$MARK")"
hub myvideos 2; dump_ui "$D/stopped-mine.xml"
assert_eq "stopped: My videos is untouched" "yes" "$(has_node "$D/stopped-mine.xml" hub_page:myvideos)"
assert_eq "stopped: no crash" "" "$(no_crash)"
jf start >/dev/null
for _ in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:8096/System/Info/Public)" = 200 ] && break; sleep 1; done; sleep 3
MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/restarted.xml"
assert_contains "started again: connected on the next open" "[video] server $HOST: connected" "$(vring "$MARK")"
assert_eq "started again: the library is back" "yes" "$(has_node "$D/restarted.xml" "server_item:$ITEM")"
for r in $RINGS; do ring_save "$r"; done

# ---- a token the server no longer accepts: unauthorised, and the sign-in page again with the host prefilled
record "revoking the shell's device on the server" "$(jf revoke Tessera | xargs)"
MARK="$(ring_mark)"; hub mediaserver 4; dump_ui "$D/revoked.xml"
assert_contains "revoked token: the line" "[video] server $HOST: unauthorised" "$(vring "$MARK")"
assert_eq "revoked token: the sign-in form, the host prefilled" "$HOST" "$(node_text "$D/revoked.xml" server_host)"
tap_node "$D/revoked.xml" server_password; sleep 0.6; adb shell input text "$PW"; adb shell input keyevent KEYCODE_BACK; sleep 0.6
MARK="$(ring_mark)"
dump_ui "$D/revoked-typed.xml"; tap_node "$D/revoked-typed.xml" server_connect; sleep 4
dump_ui "$D/resigned.xml"
assert_contains "signed in again: connected" "[video] server $HOST: connected" "$(vring "$MARK")"
assert_eq "signed in again: the library" "yes" "$(has_node "$D/resigned.xml" "server_item:$ITEM")"
TOKEN2="$(jf token-of Tessera)"
assert_eq "the server issued a new token (compared, never printed)" "different" "$([ -n "$TOKEN2" ] && [ "$TOKEN" != "$TOKEN2" ] && echo different || echo same)"
for r in $RINGS; do ring_save "$r"; done

# ---- an error on a request that carries the token: a direct-play address of the saved server for an item it does not
# have. The player's data source adds the token, the server answers 404, and neither the line nor logcat may hold it.
MARK="$(ring_mark)"
adb shell am start -n app.tileshell/.video.PlayerActivity -a android.intent.action.VIEW -d "http://$HOST/Videos/00000000000000000000000000000000/stream" -t video/mp4 >/dev/null
LINE="$(await_vline "$MARK" "[video] cannot" 150)"
record "a token-bearing request that fails: the line" "$(echo "$LINE" | sed 's/.*\[video\] //')"
assert_contains "the token rode on it (the stream line, query string removed)" "[video] server stream http://$HOST/Videos/00000000000000000000000000000000/stream" "$(vring "$MARK")"
assert_no_secret "the failing request's lines hold no token" "$TOKEN2" "$(vring "$MARK")"
assert_eq "…and logcat holds no line with it" "0" "$(adb logcat -d | grep -cF -- "$TOKEN2")"
for r in $RINGS; do ring_save "$r"; done
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- remove the server: the token leaves the store, the pane row and the dynamic shortcut go
hub settings 2; dump_ui "$D/r0.xml"; tap_node "$D/r0.xml" hub_settings:server; sleep 1
dump_ui "$D/r1.xml"
assert_contains "the setting shows the server that is set up" "$HOST" "$(node_text "$D/r1.xml" server_current)"
MARK="$(ring_mark)"
tap_node "$D/r1.xml" server_remove; sleep 1.5
dump_ui "$D/removed.xml"
SLICE="$(vring "$MARK")"
assert_contains "removed: [video] server token cleared" "[video] server token cleared" "$SLICE"
assert_contains "removed: [video] shortcut mediaserver removed" "[video] shortcut mediaserver removed" "$SLICE"
assert_eq "removed: the store holds the TMDB key only" "tmdb" "$(cred_names)"
assert_eq "removed: the form is back" "yes" "$(has_node "$D/removed.xml" server_connect)"
assert_absent "removed: dumpsys shortcut has no video_mediaserver" "video_mediaserver" "$(shortcut_dump)"
assert_contains "the two static shortcuts are still there" "video_myvideos" "$(shortcut_dump)"
tap_node "$D/removed.xml" hub_menu; sleep 0.8; dump_ui "$D/removed-pane.xml"
assert_eq "removed: no Media server row in the pane" "no" "$(has_node "$D/removed-pane.xml" hub_pane:mediaserver)"
assert_eq "removed: the pane is the one read" "yes" "$(has_node "$D/removed-pane.xml" hub_pane:myvideos)"
adb shell input tap 960 1200; sleep 0.6
assert_eq "removed: neither token is in any file of the app" "" "$(app_files_holding "$TOKEN")$(app_files_holding "$TOKEN2")"
for r in $RINGS; do ring_save "$r"; done

# ---- no secret in any saved slice, dump or logcat line; restore
key_remove_if_saved
assert_eq "the TMDB key is removed: the store is empty" "" "$(cred_names)"
for secret in "$TOKEN" "$TOKEN2" "$PW" "$DUMMY_TOKEN" not-the-password; do
  [ -n "$secret" ] || continue
  assert_eq "a secret is in no file of this row's folder" "" "$(grep -rlF -- "$secret" "$D" | head -3)"
  assert_eq "…and in no logcat line" "0" "$(adb logcat -d | grep -cF -- "$secret")"
done
qa_pref qa_catalogue_base --remove
assert_eq "no crash of the shell" "" "$(no_crash)"
adb shell am force-stop app.tileshell; ensure_start
row_end
