#!/usr/bin/env bash
# Phase 17 — the Edge-cases bullets that are Movies & TV's (the doc's "## Edge cases"; r3 V14, phase 16's EDGE form).
# One function per bullet, each from its own MARK with its own restore, each its own row EDGE_VIDEO_<ID> (its own
# folder and totals):
#
#   edge_video.sh              every edge below, in this order
#   edge_video.sh <ID> [<ID>…] the named ones: VIDEOS APK_UPDATE CATALOGUE WATCH_ON MEDIA_SERVER OFFLINE
#
#   VIDEOS        "Videos: a 4K fixture … a file with no audio track … two audio tracks … subtitles"
#   APK_UPDATE    "APK updated (adb install -r) while a video plays"
#   CATALOGUE     "Catalogue: a rate-limited source (429) … no artwork … no result … the token revoked (401) … replace"
#   WATCH_ON      "Watch on: the service uninstalled between the page draw and the tap …"
#   MEDIA_SERVER  "Media server: the token invalid after a password change … another subnet … removed from the app"
#   OFFLINE       "Offline preferred: … never blocks the My videos page or the player; … a 10-s timeout … off the main thread"
#
# A content video is opened from its My videos tile (the shell's own launch): the adb shell's own `am start` of a content
# item is refused since the trust fixes (TRUST_VIDEO, C-M4 leg (vi)). The catalogue fixture is on port 8091 (INDEX
# Change Log 2026-10-05 14:27 (2)). No edge here uses root or the microphone; sound plays at device volume 0.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap 'video_cleanup; adb uninstall "$QAFLIX" >/dev/null 2>&1' EXIT
leave() { rings_save; adb shell input keyevent KEYCODE_BACK; sleep 1.2; }
edge_end() { c6; ensure_start; row_end; }

# ================================================================================================ VIDEOS
edge_VIDEOS() {
  video_row_begin EDGE_VIDEO_VIDEOS "edge: a 4K file, a file with no audio track, two audio tracks, subtitles beside the file"
  quiet_on; videos_grant
  media_up qa-4k.mp4 qa-silent.mp4 qa-two-audio.mp4 qa-steps.mp4 qa-steps.srt
  adb shell am force-stop app.tileshell; sleep 1; ensure_start
  local ID MARK LINE

  log "--- a 4K fixture above the AVD decoder's capability → the error state, no crash"
  ID="$(media_id video qa-4k.mp4 Movies/)"; assert_ne "qa-4k.mp4 is in MediaStore" "" "$ID"
  record "4K: the AVD's h264 decoders and their size limits (media_codecs*.xml)" "$(adb shell "cat /vendor/etc/media_codecs*.xml" 2>/dev/null | tr -d '\r' | grep -A6 'type="video/avc"' | grep -oE 'name="[^"]*"|Limit name="size"[^/]*' | tr '\n' ' ' | cut -c1-300)"
  MARK="$(ring_mark)"
  open_video "$ID" "$D/4k"; record "4K: opened by" "$OPENED_BY"
  LINE="$(await_vline "$MARK" "[video] cannot decode" 200)"
  record "4K: the :video lines" "$(vring "$MARK" | grep -F '[video]' | sed 's/.*\[video\] //' | tr '\n' '|')"
  gdump "$D/4k.xml"
  assert_contains "4K: the error state's line ([video] cannot decode …)" "[video] cannot decode" "$LINE"
  assert_eq "4K: the error state's text" "can't play this file" "$(node_text "$D/4k.xml" player_error)"
  assert_eq "4K: the player is still resumed" "$PLAYER_ACTIVITY" "$(top_activity)"
  assert_eq "4K: no crash (logcat -T <MARK> -s AndroidRuntime)" "" "$(crash_since "$MARK")"
  leave

  log "--- a file with no audio track still takes audio focus"
  ID="$(media_id video qa-silent.mp4 Movies/)"; assert_ne "qa-silent.mp4 is in MediaStore" "" "$ID"
  MARK="$(ring_mark)"
  open_video "$ID" "$D/silent"
  LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
  assert_contains "no audio track: it plays" "[video] playing $ID" "$LINE"
  sleep 1
  assert_contains "no audio track: the tracks line counts no audio" "audio=0" "$(vline "$MARK" "[video] tracks:")"
  adb shell dumpsys audio | tr -d '\r' > "$D/silent-audio.txt"
  assert_contains "no audio track: it still takes audio focus (the focus stack names app.tileshell)" "pack: app.tileshell" "$(sed -n '/Audio Focus stack entries/,/^$/p' "$D/silent-audio.txt")"
  leave

  log "--- a file with two audio tracks plays the first"
  ID="$(media_id video qa-two-audio.mp4 Movies/)"; assert_ne "qa-two-audio.mp4 is in MediaStore" "" "$ID"
  MARK="$(ring_mark)"
  open_video "$ID" "$D/two"
  LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
  assert_contains "two audio tracks: it plays" "[video] playing $ID" "$LINE"
  shot_at "$(wall_of "$LINE")" 2500 "$D/two-2.5s.png"
  assert_eq "two audio tracks: the picture runs (the nearest fixture colour at 2.5 s is colour 2)" "2" "$(nearest_colour "$SHOT_RGB" | cut -d' ' -f1)"
  assert_contains "two audio tracks: the tracks line counts two" "audio=2" "$(vline "$MARK" "[video] tracks:")"
  assert_eq "two audio tracks: the session is PLAYING" "PLAYING" "$(session_state .id.video)"
  record "RECORDED (the lead's ruling) — 'plays the first': which of the two tracks sounds is not readable from a dump or a line; the claim rests on the player's default track selection" "tracks: $(vline "$MARK" "[video] tracks:" | sed 's/.*tracks: //')"
  leave

  log "--- subtitles: a .srt beside the file toggles"
  ID="$(media_id video qa-steps.mp4 Movies/)"; assert_ne "qa-steps.mp4 is in MediaStore" "" "$ID"
  MARK="$(ring_mark)"
  open_video "$ID" "$D/subs"
  await_vline "$MARK" "[video] playing $ID" 100 >/dev/null
  assert_contains "subtitles: the .srt of the same name is found" "subtitle file beside $ID: found" "$(vring "$MARK")"
  show_paused "$D/subs-paused.xml"
  assert_eq "subtitles: the CC control is there" "yes" "$(has_node "$D/subs-paused.xml" player_cc)"
  assert_eq "subtitles: no line is drawn before CC is on" "no" "$(has_node "$D/subs-paused.xml" player_subtitle)"
  tap_node "$D/subs-paused.xml" player_cc; sleep 0.5
  tap_node "$D/subs-paused.xml" player_playpause; sleep 1.6
  gdump "$D/subs-on.xml"
  assert_contains "subtitles on: [video] captions on" "[video] captions on" "$(vring "$MARK")"
  assert_eq "subtitles on: a line of the .srt is drawn (second <k>)" "yes" "$(node_text "$D/subs-on.xml" player_subtitle | grep -qE '^second [0-9]$' && echo yes || echo no)"
  record "subtitles on: the line drawn" "$(node_text "$D/subs-on.xml" player_subtitle)"
  [ "$(has_node "$D/subs-on.xml" player_cc)" = yes ] || { adb shell input tap 540 600; sleep 0.6; gdump "$D/subs-on.xml"; }
  tap_node "$D/subs-on.xml" player_cc; sleep 1.2
  gdump "$D/subs-off.xml"
  assert_contains "subtitles off: [video] captions off" "[video] captions off" "$(vring "$MARK")"
  assert_eq "subtitles off: the line is gone (the player's page read first)" "yes no" "$(has_node "$D/subs-off.xml" player_root) $(has_node "$D/subs-off.xml" player_subtitle)"
  record "RECORDED — 'or an embedded text track'" "no fixture of make_videos.sh carries an embedded text track (the Fixtures paragraph lists qa-steps.srt only); the toggle is the same control (CC), shown here on the .srt"
  leave

  assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
  adb shell am force-stop app.tileshell
  media_down; videos_grant_restore; quiet_off
  edge_end
}

# ================================================================================================ APK_UPDATE
edge_APK_UPDATE() {
  video_row_begin EDGE_VIDEO_APK_UPDATE "edge: adb install -r while a video plays — the player stops cleanly, no orphan session"
  quiet_on; videos_grant
  media_up qa-steps.mp4
  local ID MARK LINE
  ID="$(media_id video qa-steps.mp4 Movies/)"
  adb shell am force-stop app.tileshell; sleep 1; ensure_start
  MARK="$(ring_mark)"
  play_from_hub "$ID" "$D/play"
  LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
  assert_contains "the video plays" "[video] playing $ID" "$LINE"
  sleep 1.5
  assert_eq "while it plays: the shell's video session is PLAYING" "PLAYING" "$(session_state .id.video)"
  rings_save
  adb install -r "$APK" > "$D/install-r.out" 2>&1; assert_eq "adb install -r of the same build" "0" "$?"
  sleep 3
  adb shell dumpsys media_session | tr -d '\r' > "$D/media_session-after.txt"
  assert_eq "after the update: dumpsys media_session shows no session of the shell (no orphan)" "" "$(shell_sessions)"
  assert_ne "after the update: the player is not on top (it stopped)" "$PLAYER_ACTIVITY" "$(top_activity)"
  assert_eq "after the update: no :video process is left running the old code" "" "$(adb shell pidof app.tileshell:video | tr -d '\r')"
  assert_eq "after the update: no crash (logcat -T <MARK> -s AndroidRuntime)" "" "$(crash_since "$MARK")"
  assert_gate_build
  videos_grant
  media_down; videos_grant_restore; quiet_off
  edge_end
}

# ================================================================================================ CATALOGUE
edge_CATALOGUE() {
  video_row_begin EDGE_VIDEO_CATALOGUE "edge: the catalogue — 429, no artwork, no result, a revoked key (401) and its replacement"
  fixture_up || { _verdict FAIL "the catalogue fixture on :$VPORT" "did not start"; row_end; return; }
  local MARK OFF
  qa_pref qa_catalogue_base "$FIXTURE_URL/"
  assert_absent "precondition: no TMDB key is saved" "tmdb" "$(cred_names)"
  adb shell run-as app.tileshell rm -rf files/video_catalogue
  key_enter "$D/key"
  MARK="$(ring_mark)"
  search_blade_runner "$D/seed.xml"
  assert_eq "the cache is seeded: the search's three rows" "78 335984 tv-117884" "$(result_ids "$D/seed.xml")"

  log "--- a rate-limited source (429)"
  qa_pref qa_catalogue_base "$FIXTURE_URL/429/"
  MARK="$(ring_mark)"; browse_blade_runner "$MARK" "$D/429.xml"
  assert_eq "429: the page's line" "The catalogue is busy, try again in a minute" "$(node_text "$D/429.xml" hub_browse_notice)"
  assert_contains "429: [video] catalogue \"Blade Runner\": error 429" '[video] catalogue "Blade Runner": error 429' "$(vring "$MARK")"
  assert_eq "429: with the cache shown" "78 335984 tv-117884" "$(result_ids "$D/429.xml")"

  log "--- the token revoked on TMDB's side (401): the bad-key form"
  qa_pref qa_catalogue_base "$FIXTURE_URL/401/"
  MARK="$(ring_mark)"; browse_blade_runner "$MARK" "$D/401.xml"
  assert_eq "401: Browse says the saved key was refused" "The saved TMDB key was refused." "$(node_text "$D/401.xml" hub_browse_notice)"
  assert_eq "401: with the link to the setting" "yes" "$(has_node "$D/401.xml" hub_key_link)"
  assert_contains "401: [video] catalogue \"Blade Runner\": error 401" '[video] catalogue "Blade Runner": error 401' "$(vring "$MARK")"
  assert_eq "401: with the cache shown" "78 335984 tv-117884" "$(result_ids "$D/401.xml")"
  absent_in "401: not the no-key form (a key is saved)" "no TMDB key saved" "$(vring "$MARK")"
  assert_eq "401: never a crash (logcat -T <MARK> -s AndroidRuntime)" "" "$(crash_since "$MARK")"
  log "--- replace: the link opens the setting, the key typed over it, the base restored"
  tap_node "$D/401.xml" hub_key_link; sleep 1
  dump_ui "$D/replace-page.xml"
  assert_eq "replace: the link opens the TMDB key setting" "yes" "$(has_node "$D/replace-page.xml" hub_page:tmdbkey)"
  key_type_and_save "$D/replace"
  assert_eq "replace: still one saved key" "A key is saved." "$(node_text "$D/replace-saved.xml" tmdb_key_status)"
  qa_pref qa_catalogue_base "$FIXTURE_URL/"
  MARK="$(ring_mark)"; OFF="$(fixture_lines)"
  browse_blade_runner "$MARK" "$D/replaced.xml"
  assert_contains "replace: the next search answers with bearer ok in the fixture's log" "GET /3/search/multi bearer ok" "$(fixture_since "$OFF")"
  assert_eq "replace: no notice is shown" "no" "$(has_node "$D/replaced.xml" hub_browse_notice)"

  log "--- a title with no artwork, and a query with no result"
  retype() { # dump, characters to delete, text — the search box cleared and a new query sent
    tap_node "$1" hub_search_box; sleep 0.7
    local i; for i in $(seq 1 "$2"); do adb shell input keyevent KEYCODE_DEL; done
    adb shell input text "$3"; adb shell input keyevent KEYCODE_ENTER; sleep 3
  }
  retype "$D/replaced.xml" 14 "No%sArtwork"
  dump_ui "$D/noart.xml"
  assert_eq "no artwork: the page (hub_page:browse) shows the poster placeholder and no image node for it" "yes yes no" \
    "$(has_node "$D/noart.xml" hub_page:browse) $(has_node "$D/noart.xml" hub_result_placeholder:900001) $(has_node "$D/noart.xml" hub_result_image:900001)"
  retype "$D/noart.xml" 12 "zzzz"
  dump_ui "$D/empty.xml"
  assert_eq "no result: the page is the one read and holds no result row" "yes 0" "$(has_node "$D/empty.xml" hub_page:browse) $(grep -c 'resource-id="hub_result:' "$D/empty.xml")"
  assert_ne "no result: the empty line, not a blank grid (hub_browse_empty's text)" "" "$(grep -o '<node[^>]*resource-id="hub_browse_empty"[^>]*>' "$D/empty.xml" | grep -o 'text="[^"]*"' | head -1 | sed 's/^text="//;s/"$//;s/&quot;/"/g')"
  record "no result: the empty line" "$(grep -o '<node[^>]*resource-id="hub_browse_empty"[^>]*>' "$D/empty.xml" | grep -o 'text="[^"]*"' | head -1)"

  log "--- restore"
  key_remove_if_saved
  assert_eq "the key is removed after the edge (Q-17-1 (a))" "" "$(cred_names)"
  qa_pref qa_catalogue_base --remove
  assert_eq "the QA pref is removed" "" "$(qa_pref_now qa_catalogue_base)"
  adb shell run-as app.tileshell rm -rf files/video_catalogue
  fixture_down
  assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
  c6; ensure_start
  leak_scan_row "$DUMMY_TOKEN"
  row_end
}

# ================================================================================================ WATCH_ON
edge_WATCH_ON() {
  video_row_begin EDGE_VIDEO_WATCH_ON "edge: Watch on — the service uninstalled between the page draw and the tap"
  [ -f "$QAFLIX_APK" ] || { _verdict FAIL "the QA-Flix fixture APK" "missing"; row_end; return; }
  fixture_up || { _verdict FAIL "the catalogue fixture on :$VPORT" "did not start"; row_end; return; }
  local MARK try hit="" xy
  qa_pref qa_catalogue_base "$FIXTURE_URL/"; qa_pref qa_wikidata_base "$FIXTURE_URL/wikidata/"
  adb shell run-as app.tileshell rm -rf files/video_catalogue
  key_enter "$D/key"
  # The tap must land after the package is gone and before the page's next draw (the page redraws on Android's
  # package-removed broadcast). The harness cannot order the two, so the uninstall and the tap are sent together, in
  # both orders, up to six times; each attempt's outcome is recorded.
  for try in 1 2 3 4 5 6; do
    adb install "$QAFLIX_APK" >/dev/null 2>&1; sleep 1.5
    MARK="$(ring_mark)"
    browse_blade_runner "$MARK" "$D/t$try-browse.xml"
    tap_node "$D/t$try-browse.xml" hub_result:335984; sleep 3
    dump_ui "$D/t$try-page.xml"
    [ "$(has_node "$D/t$try-page.xml" hub_watch:qa-flix)" = yes ] || { record "attempt $try" "the row was not drawn"; continue; }
    xy="$(centre_px "$D/t$try-page.xml" hub_watch:qa-flix)"
    MARK="$(ring_mark)"
    if [ $((try % 2)) = 1 ]; then adb shell "pm uninstall $QAFLIX >/dev/null 2>&1 & input tap $xy; wait" >/dev/null 2>&1
    else adb shell "input tap $xy & pm uninstall $QAFLIX >/dev/null 2>&1; wait" >/dev/null 2>&1; fi
    sleep 2.5
    dump_ui "$D/t$try-after.xml"
    record "attempt $try: on top / hub_watch_gone / the row / the lines" "$(top_activity) / $(has_node "$D/t$try-after.xml" hub_watch_gone) / $(has_node "$D/t$try-after.xml" hub_watch:qa-flix) / $(vring "$MARK" | grep -F 'watch-on' | sed 's/.*\[video\] //' | tr '\n' '|')"
    if [ "$(has_node "$D/t$try-after.xml" hub_watch_gone)" = yes ]; then hit="$try"; break; fi
    [ "$(top_activity)" = "$VIDEO_ACTIVITY" ] || { adb shell input keyevent KEYCODE_BACK; sleep 1; }
  done
  if [ -n "$hit" ]; then
    assert_eq "the tap after the uninstall: the page says" "That app isn't installed any more" "$(node_text "$D/t$hit-after.xml" hub_watch_gone)"
    assert_eq "… and the entry is gone on that draw (the title page read first)" "yes no" "$(has_node "$D/t$hit-after.xml" hub_title) $(has_node "$D/t$hit-after.xml" hub_watch:qa-flix)"
    assert_contains "… with the line saying not installed" "-> not installed" "$(vring "$MARK" | grep -F 'watch-on qa-flix')"
  else
    record "NOT PRODUCED in six attempts" "the page's redraw on the package-removed broadcast won the race every time: the row was gone before the tap landed (see each attempt). The clause's text is hub_watch_gone's; its rule is StreamingHandoff.open's NOT_INSTALLED."
  fi
  record "RECORDED — 'a service that answers no intent at all (ActivityNotFoundException) → the search URL is tried, then not installed'" "not producible with this fixture: a package with no launcher entry and no VIEW filter is not listed as installed (StreamingHandoff.installed), so it has no row to tap; the fall-back order is ServicesTableTest's (JVM)"
  record "RECORDED — 'the deep link answered by the service's home rather than the title'" "P13 (the real services, on the phone)"
  adb uninstall "$QAFLIX" >/dev/null 2>&1
  key_remove_if_saved
  assert_eq "the key is removed after the edge" "" "$(cred_names)"
  qa_pref qa_catalogue_base --remove; qa_pref qa_wikidata_base --remove
  adb shell run-as app.tileshell rm -rf files/video_catalogue
  fixture_down
  assert_eq "QA-Flix is uninstalled" "" "$(adb shell pm list packages "$QAFLIX" | tr -d '\r')"
  assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
  c6; ensure_start
  leak_scan_row "$DUMMY_TOKEN"
  row_end
}

# ================================================================================================ MEDIA_SERVER
edge_MEDIA_SERVER() {
  video_row_begin EDGE_VIDEO_MEDIA_SERVER "edge: the media server — a password change, the phone with no route to it, the server removed"
  local MARK NEWPW="qa-password-changed" CODE TOKEN STILL
  jf_up || { _verdict FAIL "the Jellyfin fixture" "did not come up"; row_end; return; }
  assert_absent "precondition: no server is set up" "jellyfin" "$(cred_names)"
  server_add "$D/add"
  assert_eq "the library lists the fixture's video" "yes" "$(has_node "$D/add-library.xml" "server_item:$SERVER_ITEM")"
  TOKEN="$(jf token-of Tessera)"
  assert_eq "the token the server issued the shell is read (its length; never printed)" "32" "${#TOKEN}"

  log "--- the phone with no route to the server (airplane mode: another subnet / mobile data on the AVD)"
  airplane enable
  MARK="$(ring_mark)"; hub mediaserver 6; dump_ui "$D/noroute.xml"
  assert_contains "no route: [video] server $SERVER_HOST: unreachable" "[video] server $SERVER_HOST: unreachable" "$(vring "$MARK")"
  assert_eq "no route: the page's text" "Can't reach your media server" "$(node_text "$D/noroute.xml" server_notice)"
  hub myvideos 2.5; dump_ui "$D/noroute-mine.xml"
  assert_eq "no route: the My videos page is intact" "yes" "$(has_node "$D/noroute-mine.xml" hub_page:myvideos)"
  airplane disable
  assert_eq "airplane mode is off again" "0" "$(airplane_now)"

  log "--- the token invalid after a password change on the server"
  # THE LEAD'S RULING: a real password change over Jellyfin's REST first.
  CODE="$(curl -s -o /dev/null -w '%{http_code}' -m 20 -X POST -H 'Content-Type: application/json' \
    -H "Authorization: MediaBrowser Token=\"$(cat "$JF_WORK/admin.token")\"" \
    -d "{\"CurrentPw\":\"$SERVER_PW\",\"NewPw\":\"$NEWPW\"}" "http://127.0.0.1:8096/Users/Password?userId=$(cat "$JF_WORK/user.id")")"
  record "the password change over REST (POST /Users/Password?userId=…): status" "$CODE"
  if [ "$CODE" != 204 ]; then
    CODE="$(curl -s -o /dev/null -w '%{http_code}' -m 20 -X POST -H 'Content-Type: application/json' \
      -H "Authorization: MediaBrowser Token=\"$(cat "$JF_WORK/admin.token")\"" \
      -d "{\"CurrentPw\":\"$SERVER_PW\",\"NewPw\":\"$NEWPW\"}" "http://127.0.0.1:8096/Users/$(cat "$JF_WORK/user.id")/Password")"
    record "… the older route (POST /Users/<id>/Password): status" "$CODE"
  fi
  MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/after-pw.xml"
  STILL="$(vring "$MARK" | grep -F "[video] server $SERVER_HOST:" | tail -1 | sed 's/.*: //')"
  record "after the password change, the shell's saved token: the server's answer on the next open" "$STILL"
  if [ "$STILL" != unauthorised ]; then
    record "the password change did NOT make the token invalid on Jellyfin 12.1; it is made invalid by deleting the shell's device on the server" "$(jf revoke Tessera | xargs)"
    MARK="$(ring_mark)"; hub mediaserver 5; dump_ui "$D/after-pw.xml"
  fi
  assert_contains "an invalid token: [video] server $SERVER_HOST: unauthorised" "[video] server $SERVER_HOST: unauthorised" "$(vring "$MARK")"
  assert_eq "… and the sign-in page again (server_connect)" "yes" "$(has_node "$D/after-pw.xml" server_connect)"
  assert_eq "… with the host prefilled (since the fixes: with its scheme)" "http://$SERVER_HOST" "$(node_text "$D/after-pw.xml" server_host)"
  # Sign in again with whichever password the server now takes, so the removal below removes a live server.
  tap_node "$D/after-pw.xml" server_password; sleep 0.6
  adb shell input text "$([ "$CODE" = 204 ] && echo "$NEWPW" || echo "$SERVER_PW")"; adb shell input keyevent KEYCODE_BACK; sleep 0.6
  MARK="$(ring_mark)"
  dump_ui "$D/again-typed.xml"; tap_node "$D/again-typed.xml" server_connect; sleep 4.5
  assert_contains "signed in again from the prefilled form" "[video] server $SERVER_HOST: connected" "$(vring "$MARK")"

  log "--- the server removed from the app"
  assert_contains "before removal: the dynamic shortcut is published" "video_mediaserver" "$(shortcut_dump)"
  server_page "$D/rm"; dump_ui "$D/rm.xml"
  MARK="$(ring_mark)"
  tap_node "$D/rm.xml" server_remove; sleep 1.8
  assert_contains "removed: [video] server token cleared" "[video] server token cleared" "$(vring "$MARK")"
  assert_contains "removed: [video] shortcut mediaserver removed" "[video] shortcut mediaserver removed" "$(vring "$MARK")"
  assert_absent "removed: the token is deleted from the store (its entry's name)" "jellyfin" "$(cred_names)"
  assert_contains "removed: the shortcut dump is the shell's" "video_myvideos" "$(shortcut_dump)"
  assert_absent "removed: the dynamic shortcut is gone" "video_mediaserver" "$(shortcut_dump)"

  log "--- restore"
  assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
  local TOKEN2; TOKEN2="$(jf token-of Tessera 2>/dev/null)"
  jf_down
  c6; ensure_start
  leak_scan_row "$TOKEN" "$SERVER_PW" "${TOKEN2:-$TOKEN}"
  row_end
}

# ================================================================================================ OFFLINE
edge_OFFLINE() {
  video_row_begin EDGE_VIDEO_OFFLINE "edge: offline preferred — the hub never blocks My videos or the player; a 10-s timeout off the main thread"
  quiet_on; videos_grant
  media_up qa-steps.mp4
  local ID MARK LINE T0 T1 PORT=8092 PID
  ID="$(media_id video qa-steps.mp4 Movies/)"

  log "--- no network at all"
  airplane enable
  rings_save; adb shell am force-stop app.tileshell; sleep 1
  MARK="$(ring_mark)"; T0="$(device_ms)"
  adb shell am start -W -n "$VIDEO_ACTIVITY" >/dev/null 2>&1
  LINE="$(await_vline "$MARK" "[video] library:" 100)"
  T1="$(wall_of "$LINE")"
  record "no network: ms from the start to the My videos page's [video] library: line" "$(( ${T1:-0} - T0 ))"
  assert_eq "no network: the My videos page lists the library within 5 s of the start" "yes" "$([ -n "$T1" ] && [ $(( T1 - T0 )) -lt 5000 ] && echo yes || echo no)"
  scroll_to_node "$D/offline-mine.xml" "video_tile:$ID" 12
  assert_eq "no network: the page and the fixture's tile" "yes yes" "$(has_node "$D/offline-mine.xml" hub_page:myvideos) $(has_node "$D/offline-mine.xml" "video_tile:$ID")"
  MARK="$(ring_mark)"
  tap_node "$D/offline-mine.xml" "video_tile:$ID"
  LINE="$(await_vline "$MARK" "[video] playing $ID" 100)"
  assert_contains "no network: the player plays a video of the phone" "[video] playing $ID" "$LINE"
  shot_at "$(wall_of "$LINE")" 2500 "$D/offline-2.5s.png"
  assert_eq "no network: the picture runs (the nearest fixture colour at 2.5 s is colour 2)" "2" "$(nearest_colour "$SHOT_RGB" | cut -d' ' -f1)"
  leave
  airplane disable
  assert_eq "airplane mode is off again" "0" "$(airplane_now)"

  log "--- a catalogue that accepts the connection and never answers: the 10-s timeout, off the main thread"
  python3 -c '
import socket, sys, time
s = socket.socket(); s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); s.bind(("127.0.0.1", int(sys.argv[1]))); s.listen(16)
held = []
while True:
    c, _ = s.accept(); held.append(c)       # accepted, never answered, never closed
' "$PORT" &
  PID=$!; EXTRA_PIDS="$EXTRA_PIDS $PID"
  qa_pref qa_catalogue_base "http://10.0.2.2:$PORT/"
  adb shell run-as app.tileshell rm -rf files/video_catalogue
  key_enter "$D/key"
  MARK="$(ring_mark)"; T0="$(device_ms)"
  hub browse 2
  hub myvideos 1.5; dump_ui "$D/during.xml"
  assert_eq "while the catalogue call hangs (≈4 s in): the My videos page is drawn and lists the tile" "yes" "$(has_node "$D/during.xml" hub_page:myvideos)"
  record "while it hangs: ms since Browse was opened, and the catalogue lines so far" "$(( $(device_ms) - T0 )) / $(vring "$MARK" | grep -F '[video] catalogue' | sed 's/.*\[video\] //' | tr '\n' '|')"
  LINE="$(await_vline "$MARK" ": error " 250)"
  T1="$(wall_of "$LINE")"
  record "the call's end: the line, and ms after Browse was opened" "$(echo "$LINE" | sed 's/.*\[video\] //') / $(( ${T1:-0} - T0 ))"
  assert_contains "the hanging call ends by itself with an error line" ": error " "$LINE"
  assert_eq "… at the 10-s timeout (between 9 and 14 s after Browse was opened)" "yes" "$([ -n "$T1" ] && [ $(( T1 - T0 )) -ge 9000 ] && [ $(( T1 - T0 )) -le 14000 ] && echo yes || echo no)"
  assert_eq "no StrictMode / main-thread crash: logcat -T <the row's MARK> -s AndroidRuntime is empty of app.tileshell" "" "$(crash_since "$ROW_MARK")"
  assert_ne "the hub's process is still the one that made the call" "" "$(adb shell pidof app.tileshell:video | tr -d '\r')"

  log "--- restore"
  kill "$PID" 2>/dev/null
  key_remove_if_saved
  assert_eq "the key is removed after the edge" "" "$(cred_names)"
  qa_pref qa_catalogue_base --remove
  assert_eq "the QA pref is removed" "" "$(qa_pref_now qa_catalogue_base)"
  adb shell run-as app.tileshell rm -rf files/video_catalogue
  media_down; videos_grant_restore; quiet_off
  c6; ensure_start
  leak_scan_row "$DUMMY_TOKEN"
  row_end
}

ALL="VIDEOS APK_UPDATE CATALOGUE WATCH_ON MEDIA_SERVER OFFLINE"
RC=0
for id in ${*:-$ALL}; do
  case " $ALL " in
    *" $id "*) "edge_$id" || RC=1 ;;
    *) echo "edge_video.sh: no edge named $id (one of: $ALL)" >&2; RC=64 ;;
  esac
done
exit $RC
