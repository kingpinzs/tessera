#!/usr/bin/env bash
# Phase 17 E20 — the catalogue (TMDB — Q-A; the key route Q-17-1 (a)): the host fixture, never the live network, inside
# the egress guard (C-29). EVERY `10.0.2.2:8090` of the doc is read as port 8091 (INDEX Change Log 2026-10-05 14:27 (2)).
# The doc's clauses → this driver's legs:
#
#   base        prefs_edit.py sets qa_catalogue_base to http://10.0.2.2:8091/ (read back)
#   no key      MARK, the fixture log's line count on the host as the offset, Browse opened (hub_pane:browse) → the
#               page says film search needs a key, with the link to the setting; `[video] catalogue: no TMDB key saved`
#               in the :video slice; NO request line after the offset; then the driver's own control request (curl from
#               the host) IS after the offset — the proof the log read is the live one (r3 V21's offset form)
#   enter       the link opens the settings page at its TMDB key setting; the dummy token is typed (`input text`) and
#               saved
#   search      back on Browse, "Blade Runner" → exactly three hub_result rows whose title / year texts are the three
#               hand-listed in the doc; artwork loaded: each row's image node has non-zero bounds and the PULLED
#               fixture PNG's colour at its centre ± 4; the fixture's log shows /3/configuration and one /img/ request
#               per poster (T17-20); `[video] catalogue "Blade Runner": 3`
#   bearer      Change Log (3): `bearer ok` is asserted for every /3/ request (no `bearer missing`), no request carries
#               an api_key parameter, and `authorisation PRESENT` is asserted absent — image and Wikidata requests
#               carry NO token by design (T17-13)
#   foot        hub_attribution reads TMDB's text (T17-14)
#   title       the first result opened → the title page (hub_title) with its overview from the fixture and the "Watch
#               on" list from the fixture's providers (QA-Flix installed for this leg; E21 has its branches)
#   offline     airplane mode, force-stop and reopen → the same three rows from the cache with "You're offline — showing
#               what was saved" and `[video] catalogue "Blade Runner": offline`; My videos still shows qa-steps.mp4
#   errors      the base at /500 → "The catalogue isn't answering" and `… : error 500`; a stopped fixture → `error connect`
#   7 days      the cache entry's mtime moved back 8 days with `touch -d` as root (the guard's span is rooted) →
#               re-fetched, `[video] catalogue "Blade Runner": 3 (refreshed)`
#   fresh       THE LEAD'S RULING: "a fresh install offline" is asserted as the NO-CACHE state — the cache folder
#               removed, the key removed and typed again, airplane mode — with a `record` that no wipe was made: the
#               Browse page's empty state names the cause, not a blank. (Run after the 7-day leg, which needs the
#               cache; the doc lists it before.)
#   remove      the key removed in the setting, a MARK, Browse → the no-key page again and `[video] catalogue: no TMDB
#               key saved` (RV12: the row ends with no key saved)
#   leak scan   leak_scan.sh --logcat --path <this row's folder> -- <the typed token> (gated)
#   guard       after each restart inside the guard the weather lines; at the end 0 packets elsewhere
#
# Restores: the guard (root, location), airplane mode, the QA pref, the key, the cache folder, QA-Flix uninstalled, the
# pushed video, the fixture server.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap video_cleanup EXIT
CAT="$P17/fixtures/catalogue"
IDS="78 335984 tv-117884"

video_row_begin E20 "the catalogue: no key, the key entered, search, artwork, bearer, the title page, offline, errors, refresh, removal"
[ -f "$QAFLIX_APK" ] || { _verdict FAIL "the QA-Flix fixture APK" "missing: ./gradlew :testapps:qa-flix:assembleDebug --offline"; }
videos_grant
media_up qa-steps.mp4
VID="$(media_id video qa-steps.mp4 Movies/)"
fixture_up || { _verdict FAIL "the catalogue fixture on :$VPORT" "did not start"; exit 5; }
assert_eq "airplane mode is off at the start" "0" "$(airplane_now)"
adb uninstall "$QAFLIX" >/dev/null 2>&1
guard_on

log "--- the base"
qa_pref qa_catalogue_base "$FIXTURE_URL/"
assert_eq "qa_catalogue_base is set to the fixture (read back)" "$FIXTURE_URL/" "$(qa_pref_now qa_catalogue_base)"
assert_absent "precondition: no TMDB key is saved at the row's start" "tmdb" "$(cred_names)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
guard_restart "start"

# ------------------------------------------------------------------------------------------------ no key saved
log "--- no key saved"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse
assert_eq "Browse is opened: the pane's current row is hub_pane:browse" "browse" "$(pane_current "$D/nokey-p")"
dump_ui "$D/nokey.xml"
assert_eq "the Browse page" "yes" "$(has_node "$D/nokey.xml" hub_page:browse)"
assert_eq "no key: the page says film search needs a key" "Film search needs a TMDB key." "$(node_text "$D/nokey.xml" hub_browse_notice)"
assert_eq "no key: with the link to the setting" "yes" "$(has_node "$D/nokey.xml" hub_key_link)"
assert_contains "no key: [video] catalogue: no TMDB key saved (:video slice)" "[video] catalogue: no TMDB key saved" "$(vring "$MARK")"
assert_eq "no key: NO request line in the fixture's log after the offset" "" "$(fixture_since "$OFF")"
curl -s -o /dev/null "http://127.0.0.1:$VPORT/control-request"
assert_contains "the driver's control request IS after the offset (the log read is the live one)" "GET /control-request" "$(fixture_since "$OFF")"

# ------------------------------------------------------------------------------------------------ enter
log "--- enter: the link opens the TMDB key setting"
tap_node "$D/nokey.xml" hub_key_link; sleep 1
dump_ui "$D/keypage.xml"
assert_eq "the link opens Movies & TV's settings at its TMDB key setting (hub_page:tmdbkey, the key field)" "yes yes" "$(has_node "$D/keypage.xml" hub_page:tmdbkey) $(has_node "$D/keypage.xml" tmdb_key_field)"
assert_eq "the setting says none is saved" "No key is saved." "$(node_text "$D/keypage.xml" tmdb_key_status)"
MARK="$(ring_mark)"
key_type_and_save "$D/enter"
assert_no_secret "the typed token is not readable in a UI dump (a secret field)" "$DUMMY_TOKEN" "$(cat "$D/enter-typed.xml" "$D/enter-saved.xml")"
assert_eq "saved: the setting's status" "A key is saved." "$(node_text "$D/enter-saved.xml" tmdb_key_status)"
assert_contains "saved: [video] TMDB key saved" "[video] TMDB key saved" "$(vring "$MARK")"
assert_eq "saved: the credential file holds the entry's name" "tmdb" "$(cred_names)"

# ------------------------------------------------------------------------------------------------ search
log "--- back on Browse: search Blade Runner"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
adb shell input keyevent KEYCODE_BACK; sleep 2.5
dump_ui "$D/browse.xml"
assert_eq "with a key, Browse has its search box" "yes" "$(has_node "$D/browse.xml" hub_search_box)"
tap_node "$D/browse.xml" hub_search_box; sleep 0.8
adb shell input text "Blade%sRunner"; sleep 0.5
adb shell input keyevent KEYCODE_ENTER; sleep 3.5
dump_ui "$D/results.xml"; screencap "$D/results.png"
assert_eq "exactly three hub_result rows" "$IDS" "$(result_ids "$D/results.xml")"
FLOG="$(fixture_since "$OFF")"; printf '%s\n' "$FLOG" > "$D/fixture-search.txt"
for spec in "78|Blade Runner|1982|qa-poster-78" "335984|Blade Runner 2049|2017|qa-poster-335984" "tv-117884|Blade Runner: Black Lotus|2021|qa-poster-117884"; do
  IFS='|' read -r rid title year poster <<<"$spec"
  assert_eq "$rid: title" "$title" "$(node_text "$D/results.xml" "hub_result_title:$rid")"
  assert_eq "$rid: year" "$year" "$(node_text "$D/results.xml" "hub_result_year:$rid")"
  B="$(bounds "$D/results.xml" "hub_result_image:$rid")"
  # shellcheck disable=SC2086
  assert_eq "$rid: its image node has non-zero bounds" "yes" "$(set -- ${B:-0 0 0 0}; [ $(( $3 - $1 )) -gt 0 ] && [ $(( $4 - $2 )) -gt 0 ] && echo yes || echo no)"
  assert_eq "$rid: one /img/ request for its poster in the fixture's log, answered" "1" "$(printf '%s\n' "$FLOG" | grep -F "/$poster.png" | grep -c -- '-> 200')"
done
assert_contains "the fixture's log shows the /3/configuration request" "GET /3/configuration bearer ok" "$FLOG"
assert_contains "[video] catalogue \"Blade Runner\": 3" '[video] catalogue "Blade Runner": 3' "$(vring "$MARK")"
assert_ne "the /3/ requests after the offset (their count)" "0" "$(printf '%s\n' "$FLOG" | grep -c ' /3/')"
assert_eq "every /3/ request reads bearer ok (Change Log (3))" "$(printf '%s\n' "$FLOG" | grep -c ' /3/')" "$(printf '%s\n' "$FLOG" | grep ' /3/' | grep -c 'bearer ok')"
assert_eq "no request carries an api_key parameter (T17-13)" "0" "$(printf '%s\n' "$FLOG" | grep -c 'api_key=present')"
assert_eq "authorisation PRESENT is absent: no image or Wikidata request carries the token" "0" "$(printf '%s\n' "$FLOG" | grep -c 'authorisation PRESENT')"
assert_ne "… and the image requests are in the slice read (authorisation absent lines)" "0" "$(printf '%s\n' "$FLOG" | grep -c 'authorisation absent')"
assert_eq "the foot's hub_attribution reads TMDB's text (T17-14)" "This product uses the TMDB API but is not endorsed or certified by TMDB." "$(node_text "$D/results.xml" hub_attribution)"
# The artwork: the fixture's PNG pulled from the host, its centre pixel, against the screen at the image node's centre.
for spec in "78|qa-poster-78" "335984|qa-poster-335984" "tv-117884|qa-poster-117884"; do
  IFS='|' read -r rid poster <<<"$spec"
  curl -s -o "$D/pulled-$poster.png" "http://127.0.0.1:$VPORT/img/w342/$poster.png"
  WANT="$(px "$D/pulled-$poster.png" 114 160)"
  # shellcheck disable=SC2046
  set -- $(centre_px "$D/results.xml" "hub_result_image:$rid")
  assert_rgb "$rid: the pulled fixture PNG's colour ($WANT) at its image node's centre ± 4" "$WANT" "$(px "$D/results.png" "${1:-0}" "${2:-0}")" 4
done

# ------------------------------------------------------------------------------------------------ the title page
log "--- the first result's title page"
adb install "$QAFLIX_APK" > "$D/qaflix-install.out" 2>&1
MARK="$(ring_mark)"
tap_node "$D/results.xml" hub_result:78; sleep 3
dump_ui "$D/title.xml"
assert_eq "the title page: hub_title" "Blade Runner" "$(node_text "$D/title.xml" hub_title)"
OVERVIEW="$(python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))["overview"][:60])' "$CAT/movie_78.json")"
assert_contains "the title page: its overview text is the fixture's (movie_78.json, the first 60 characters)" "$OVERVIEW" "$(node_text "$D/title.xml" hub_overview)"
assert_eq "the title page: the Watch on list from the fixture's providers (QA-Flix, installed for this leg)" "Watch on QA-Flix" "$(node_text "$D/title.xml" hub_watch_label:qa-flix)"
adb uninstall "$QAFLIX" >/dev/null 2>&1

# ------------------------------------------------------------------------------------------------ offline
log "--- offline: airplane mode, force-stop and reopen"
airplane enable
assert_eq "airplane mode is on" "1" "$(airplane_now)"
guard_restart "offline"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse 3.5; dump_ui "$D/offline.xml"
assert_eq "offline: the same three rows, from the cache" "$IDS" "$(result_ids "$D/offline.xml")"
assert_eq "offline: the page's line" "You're offline — showing what was saved" "$(node_text "$D/offline.xml" hub_browse_notice)"
assert_contains "offline: [video] catalogue \"Blade Runner\": offline" '[video] catalogue "Blade Runner": offline' "$(vring "$MARK")"
hub myvideos 2.5
scroll_to_node "$D/offline-mine.xml" "video_tile:$VID" 12
assert_eq "offline: the My videos page still shows qa-steps.mp4 (hub_page:myvideos, its tile)" "yes yes" "$(has_node "$D/offline-mine.xml" hub_page:myvideos) $(has_node "$D/offline-mine.xml" "video_tile:$VID")"
airplane disable
assert_eq "airplane mode is off again (RV12)" "0" "$(airplane_now)"

# ------------------------------------------------------------------------------------------------ errors
log "--- errors: the base at /500, then a stopped fixture"
qa_pref qa_catalogue_base "$FIXTURE_URL/500/"
MARK="$(ring_mark)"; browse_blade_runner "$MARK" "$D/500.xml"
assert_eq "/500: the page's line" "The catalogue isn't answering" "$(node_text "$D/500.xml" hub_browse_notice)"
assert_contains "/500: [video] catalogue \"Blade Runner\": error 500" '[video] catalogue "Blade Runner": error 500' "$(vring "$MARK")"
qa_pref qa_catalogue_base "$FIXTURE_URL/"
fixture_down
MARK="$(ring_mark)"; browse_blade_runner "$MARK" "$D/stopped.xml"
assert_contains "a stopped fixture server: [video] catalogue \"Blade Runner\": error connect" '[video] catalogue "Blade Runner": error connect' "$(vring "$MARK")"
record "a stopped fixture server: the page's line" "$(node_text "$D/stopped.xml" hub_browse_notice)"
fixture_again || _verdict FAIL "the catalogue fixture started again" "it did not"

# ------------------------------------------------------------------------------------------------ older than 7 days
log "--- the cache older than 7 days is re-fetched"
guard_restart "before the refresh"
ENTRY="$(adb shell ls /data/data/app.tileshell/files/video_catalogue 2>/dev/null | tr -d '\r' | grep '^search-' | head -1)"
assert_ne "the search's cache entry exists (its file)" "" "$ENTRY"
adb shell "touch -d '$(date -d '8 days ago' '+%Y-%m-%d %H:%M:%S')' /data/data/app.tileshell/files/video_catalogue/$ENTRY"
record "the entry's mtime after touch -d as root (8 days back)" "$(adb shell stat -c %y "/data/data/app.tileshell/files/video_catalogue/$ENTRY" | tr -d '\r')"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
browse_blade_runner "$MARK" "$D/refreshed.xml"
assert_contains "older than 7 days: re-fetched, [video] catalogue \"Blade Runner\": 3 (refreshed)" '[video] catalogue "Blade Runner": 3 (refreshed)' "$(vring "$MARK")"
assert_contains "… and the request went out" "GET /3/search/multi bearer ok" "$(fixture_since "$OFF")"

# ------------------------------------------------------------------------------------------------ "fresh install offline"
log "--- the no-cache state offline (the lead's ruling for 'a fresh install offline')"
record "NO WIPE WAS MADE for the fresh-install clause" "the row never runs pm clear on the shared device: the state is made by removing the cache folder and the key, typing the key again, and airplane mode"
key_remove_if_saved
assert_absent "the key is removed (a fresh install holds none)" "tmdb" "$(cred_names)"
rings_save
adb shell am force-stop app.tileshell
adb shell rm -rf /data/data/app.tileshell/files/video_catalogue
assert_eq "the cache folder is gone" "" "$(adb shell ls /data/data/app.tileshell/files/video_catalogue 2>/dev/null | tr -d '\r')"
key_enter "$D/fresh"
airplane enable
guard_restart "no cache, offline"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse 3.5; dump_ui "$D/fresh-offline.xml"; screencap "$D/fresh-offline.png"
assert_eq "no cache, offline: the Browse page" "yes" "$(has_node "$D/fresh-offline.xml" hub_page:browse)"
CAUSE="$(node_text "$D/fresh-offline.xml" hub_browse_notice)"
record "no cache, offline: the page's line" "$CAUSE"
assert_ne "no cache, offline: the Browse page's empty state names the cause, not a blank" "" "$CAUSE"
assert_eq "no cache, offline: no result row and no strip poster is drawn" "0" "$(grep -c 'resource-id="hub_\(result\|poster\):' "$D/fresh-offline.xml")"
assert_eq "no cache, offline: nothing reached the fixture" "" "$(fixture_since "$OFF")"
record "no cache, offline: the :video lines" "$(vring "$MARK" | grep -F '[video] catalogue' | sed 's/.*\[video\] //' | tr '\n' '|')"
airplane disable
assert_eq "airplane mode is off again (RV12)" "0" "$(airplane_now)"

# ------------------------------------------------------------------------------------------------ remove
log "--- remove: the key removed in the setting"
hub settings 2; dump_ui "$D/rm1.xml"; tap_node "$D/rm1.xml" hub_settings:tmdbkey; sleep 1
dump_ui "$D/rm2.xml"; tap_node "$D/rm2.xml" tmdb_key_remove; sleep 1.2
dump_ui "$D/removed.xml"
assert_eq "removed: the setting says none is saved" "No key is saved." "$(node_text "$D/removed.xml" tmdb_key_status)"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse; dump_ui "$D/nokey-again.xml"
assert_eq "removed: Browse is the no-key page again" "Film search needs a TMDB key." "$(node_text "$D/nokey-again.xml" hub_browse_notice)"
assert_contains "removed: [video] catalogue: no TMDB key saved" "[video] catalogue: no TMDB key saved" "$(vring "$MARK")"
assert_eq "removed: no request after the offset" "" "$(fixture_since "$OFF")"
assert_eq "the row ends with no key saved (RV12)" "" "$(cred_names)"

# ------------------------------------------------------------------------------------------------ restore, leak scan
log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
guard_off
qa_pref qa_catalogue_base --remove
assert_eq "the QA pref is removed" "" "$(qa_pref_now qa_catalogue_base)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
fixture_down
media_down
videos_grant_restore
assert_eq "QA-Flix is uninstalled" "" "$(adb shell pm list packages "$QAFLIX" | tr -d '\r')"
c6; ensure_start
leak_scan_row "$DUMMY_TOKEN"
row_end
