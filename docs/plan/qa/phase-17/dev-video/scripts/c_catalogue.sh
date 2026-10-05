#!/usr/bin/env bash
# Phase 17 development proof, build tasks 12 and 14 (brief C; E20's form): the no-key page with NO request, the TMDB key
# entered, the three results with artwork, bearer ok on every API request and no token sent to the image host, the
# title page, the offline cache, /500, a stopped fixture, the refresh after the cache's time is moved back, the key
# removed. Against the fixture only. Restores: the QA pref removed, the key removed, airplane mode off.
. "$(dirname "$0")/v17.sh"
row_begin C_CATALOGUE "the catalogue: no key, key entered, search, artwork, offline, errors, refresh, key removed"
D="$ROW_DIR"
fixture_up || exit 5
cleanup() { airplane disable; fixture_down; }
trap cleanup EXIT
assert_eq "airplane mode is off at the start" "0" "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
qa_pref qa_catalogue_base "$FIXTURE_URL/"
assert_eq "qa_catalogue_base reads back" "$FIXTURE_URL/" "$(qa_pref_now qa_catalogue_base)"
assert_absent "no TMDB key is saved at the start" "tmdb" "$(cred_names)"
adb shell run-as app.tileshell rm -rf files/video_catalogue

# ---- no key saved: the page says so, links to the setting, and NO request is made
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse
dump_ui "$D/nokey.xml"
assert_eq "the Browse page" "yes" "$(has_node "$D/nokey.xml" hub_page:browse)"
assert_eq "no key: the page says film search needs a key" "Film search needs a TMDB key." "$(node_text "$D/nokey.xml" hub_browse_notice)"
assert_eq "no key: the link to the setting" "yes" "$(has_node "$D/nokey.xml" hub_key_link)"
assert_contains "no key: [video] catalogue: no TMDB key saved" "[video] catalogue: no TMDB key saved" "$(vring "$MARK")"
assert_eq "no key: NO request reached the fixture" "" "$(fixture_since "$OFF")"
curl -s -o /dev/null "http://127.0.0.1:$V17_PORT/control-request"
assert_contains "the control request IS in the log after the offset (the log read is live)" "GET /control-request" "$(fixture_since "$OFF")"
dump_ui "$D/p0.xml"; tap_node "$D/p0.xml" hub_menu; sleep 0.8; dump_ui "$D/pane-browse.xml"
assert_contains "the shortcut's page is the pane's current row (brief E)" 'selected="true"' "$(grep -o '<node[^>]*resource-id="hub_pane:browse"[^>]*>' "$D/pane-browse.xml")"
adb shell input tap 960 1200; sleep 0.6

# ---- enter: the link opens the TMDB key setting; the token is typed and saved
tap_node "$D/nokey.xml" hub_key_link; sleep 1
dump_ui "$D/keypage.xml"
assert_eq "the link opens the TMDB key setting" "yes" "$(has_node "$D/keypage.xml" hub_page:tmdbkey)"
assert_eq "the setting says none is saved" "No key is saved." "$(node_text "$D/keypage.xml" tmdb_key_status)"
MARK="$(ring_mark)"
key_type_and_save "$D/enter"
assert_no_secret "the typed token is not readable in a UI dump (the field is a secret one)" "$DUMMY_TOKEN" "$(cat "$D/enter-typed.xml")"
assert_eq "the field holds ${#DUMMY_TOKEN} dots while typed" "${#DUMMY_TOKEN}" "$(node_text "$D/enter-typed.xml" tmdb_key_field | python3 -c 'import sys; print(len(sys.stdin.read().strip()))')"
assert_eq "saved: the status" "A key is saved." "$(node_text "$D/enter-saved.xml" tmdb_key_status)"
assert_eq "saved: the field is empty again" "" "$(node_text "$D/enter-saved.xml" tmdb_key_field | sed 's/TMDB read access token//')"
assert_contains "saved: [video] TMDB key saved" "[video] TMDB key saved" "$(vring "$MARK")"
assert_contains "saved: [cred] tmdb: saved" "[cred] tmdb: saved" "$(vring "$MARK")"
assert_eq "the credential file holds the entry's name" "tmdb" "$(cred_names)"
assert_no_secret "the credential file holds no plaintext" "$DUMMY_TOKEN" "$(adb shell run-as app.tileshell cat files/credentials_v1.json)"

# ---- back on Browse: search "Blade Runner" → exactly the three titles, with artwork
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
adb shell input keyevent KEYCODE_BACK; sleep 2.5
dump_ui "$D/browse.xml"
assert_eq "with a key Browse has its search box" "yes" "$(has_node "$D/browse.xml" hub_search_box)"
assert_eq "Browse's three strips, each a section row" "movies trending tv" "$(grep -o 'resource-id="hub_section:[a-z]*"' "$D/browse.xml" | sed 's/.*hub_section://;s/"//' | sort | xargs)"
set -- $(epx "$D/browse.xml" hub_poster:trending:335984); P1="$1"
assert_near "a strip's poster is 112 × 160 from x 12" "12 112 160" "$1 $(python3 -c "print(round($3-$1,1), round($4-$2,1))")" 0.5
set -- $(epx "$D/browse.xml" hub_poster:trending:tv-117884)
assert_near "the strip's pitch is 120" "120" "$(python3 -c "print(round($1-$P1,1))")" 0.5
tap_node "$D/browse.xml" hub_search_box; sleep 0.8
adb shell input text "Blade%sRunner"; sleep 0.5
adb shell input keyevent KEYCODE_ENTER; sleep 3
dump_ui "$D/results.xml"; screencap "$D/results.png"
ROWS="$(grep -o 'resource-id="hub_result:[a-z0-9-]*"' "$D/results.xml" | sed 's/.*hub_result://;s/"//' | xargs)"
assert_eq "exactly three result rows" "78 335984 tv-117884" "$ROWS"
for spec in "78|Blade Runner|1982|180 40 40" "335984|Blade Runner 2049|2017|40 120 200" "tv-117884|Blade Runner: Black Lotus|2021|60 170 90"; do
  IFS='|' read -r rid title year rgb <<<"$spec"
  assert_eq "$rid: title" "$title" "$(node_text "$D/results.xml" "hub_result_title:$rid")"
  assert_eq "$rid: year" "$year" "$(node_text "$D/results.xml" "hub_result_year:$rid")"
  set -- $(centre_px "$D/results.xml" "hub_result_image:$rid")
  assert_colour "$rid: the poster's colour at its image node's centre" "$rgb" "$(pixel "$D/results.png" "${1:-0}" "${2:-0}")" 4
done
assert_contains "[video] catalogue \"Blade Runner\": 3" '[video] catalogue "Blade Runner": 3' "$(vring "$MARK")"
FLOG="$(fixture_since "$OFF")"; echo "$FLOG" > "$D/fixture-search.txt"
assert_contains "the fixture saw /3/configuration" "GET /3/configuration bearer ok" "$FLOG"
assert_contains "the fixture saw the search" "GET /3/search/multi bearer ok" "$FLOG"
assert_eq "one /img/ request per poster, each answered" "3" "$(echo "$FLOG" | grep 'GET /img/' | grep -c -- '-> 200')"
assert_eq "every API request carried the right bearer" "0" "$(echo "$FLOG" | grep -c 'bearer missing')"
assert_eq "no request carried an api_key parameter" "0" "$(echo "$FLOG" | grep -c 'api_key=present')"
assert_eq "the token was sent to no image request" "0" "$(echo "$FLOG" | grep -c 'authorisation PRESENT')"
assert_eq "the foot's attribution is TMDB's text" "This product uses the TMDB API but is not endorsed or certified by TMDB." "$(node_text "$D/results.xml" hub_attribution)"

# ---- the title page: the overview from the fixture; QA-Flix is not installed, so "Not on this phone"
MARK="$(ring_mark)"
tap_node "$D/results.xml" hub_result:78; sleep 2.5
dump_ui "$D/title.xml"
assert_eq "title page: hub_title" "Blade Runner" "$(node_text "$D/title.xml" hub_title)"
assert_contains "title page: the overview from the fixture" "In the smog-choked dystopian Los Angeles of 2019" "$(node_text "$D/title.xml" hub_overview)"
assert_eq "title page: year • genre" "1982 • Science Fiction" "$(node_text "$D/title.xml" hub_title_facts)"
assert_eq "title page: no installed service has it" "Not on this phone" "$(node_text "$D/title.xml" hub_watch_none)"
assert_contains "the catalogue names QA-Flix, which is not installed" '[video] watch-on qa-flix "Blade Runner" -> not installed' "$(vring "$MARK")"
for r in $RINGS; do ring_save "$r"; done

# ---- offline: the same three rows from the cache, the line, My videos untouched
airplane enable
adb shell am force-stop app.tileshell
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
hub browse 3
dump_ui "$D/offline.xml"
assert_eq "offline: the same three rows, from the cache" "78 335984 tv-117884" "$(grep -o 'resource-id="hub_result:[a-z0-9-]*"' "$D/offline.xml" | sed 's/.*hub_result://;s/"//' | xargs)"
assert_eq "offline: the page's line" "You're offline — showing what was saved" "$(node_text "$D/offline.xml" hub_browse_notice)"
assert_contains "offline: [video] catalogue \"Blade Runner\": offline" '[video] catalogue "Blade Runner": offline' "$(vring "$MARK")"
assert_eq "offline: artwork still drawn (the image cache)" "yes" "$(has_node "$D/offline.xml" hub_result_image:78)"
assert_eq "offline: nothing reached the fixture" "" "$(fixture_since "$OFF")"
hub myvideos 2; dump_ui "$D/offline-mine.xml"
assert_eq "offline: My videos is still the page it was" "yes" "$(has_node "$D/offline-mine.xml" hub_page:myvideos)"
for r in $RINGS; do ring_save "$r"; done
airplane disable

# ---- errors: /500, then a stopped fixture
qa_pref qa_catalogue_base "$FIXTURE_URL/500/"
MARK="$(ring_mark)"; hub browse 3; dump_ui "$D/500.xml"
assert_eq "/500: the page's line" "The catalogue isn't answering" "$(node_text "$D/500.xml" hub_browse_notice)"
assert_contains "/500: the line" '[video] catalogue "Blade Runner": error 500' "$(vring "$MARK")"
assert_eq "/500: the cache is still shown" "yes" "$(has_node "$D/500.xml" hub_result:335984)"
for r in $RINGS; do ring_save "$r"; done
qa_pref qa_catalogue_base "$FIXTURE_URL/429/"
MARK="$(ring_mark)"; hub browse 3; dump_ui "$D/429.xml"
assert_eq "/429: the page's line" "The catalogue is busy, try again in a minute" "$(node_text "$D/429.xml" hub_browse_notice)"
assert_contains "/429: the line" '[video] catalogue "Blade Runner": error 429' "$(vring "$MARK")"
for r in $RINGS; do ring_save "$r"; done
qa_pref qa_catalogue_base "$FIXTURE_URL/401/"
MARK="$(ring_mark)"; hub browse 3; dump_ui "$D/401.xml"
assert_eq "/401: the page says the saved key was refused" "The saved TMDB key was refused." "$(node_text "$D/401.xml" hub_browse_notice)"
assert_eq "/401: the same link to the setting" "yes" "$(has_node "$D/401.xml" hub_key_link)"
assert_contains "/401: the line" '[video] catalogue "Blade Runner": error 401' "$(vring "$MARK")"
assert_eq "/401: the cache is still shown" "yes" "$(has_node "$D/401.xml" hub_result:78)"
# Replace: the link opens the setting, the key is typed over the saved one, and with the base restored the search answers.
tap_node "$D/401.xml" hub_key_link; sleep 1
key_type_and_save "$D/replace"
assert_eq "replace: still one saved key" "A key is saved." "$(node_text "$D/replace-saved.xml" tmdb_key_status)"
for r in $RINGS; do ring_save "$r"; done
qa_pref qa_catalogue_base "$FIXTURE_URL/"
fixture_down
MARK="$(ring_mark)"; hub browse 3; dump_ui "$D/stopped.xml"
assert_contains "stopped fixture: the line" '[video] catalogue "Blade Runner": error connect' "$(vring "$MARK")"
assert_eq "stopped fixture: the page's line" "The catalogue isn't answering" "$(node_text "$D/stopped.xml" hub_browse_notice)"
for r in $RINGS; do ring_save "$r"; done
KEEP="$(cat "$FIXTURE_LOG")"; fixture_up || exit 5; echo "$KEEP" > "$FIXTURE_LOG"

# ---- the cache older than 7 days is re-fetched: the entry's file time is moved back 8 days (the app's own file, its own uid)
adb shell am force-stop app.tileshell
ENTRY="$(adb shell run-as app.tileshell ls files/video_catalogue | tr -d '\r' | grep '^search-' | head -1)"
assert_ne "the search's cache entry exists" "" "$ENTRY"
adb shell run-as app.tileshell touch -d "$(date -d '8 days ago' '+%Y-%m-%d %H:%M:%S')" "files/video_catalogue/$ENTRY"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"; hub browse 3; dump_ui "$D/refreshed.xml"
assert_contains "stale entry: re-fetched, the line says so" '[video] catalogue "Blade Runner": 3 (refreshed)' "$(vring "$MARK")"
assert_contains "stale entry: the request went out with the replaced key" "GET /3/search/multi bearer ok" "$(fixture_since "$OFF")"
assert_eq "after the refresh no notice is shown" "no" "$(has_node "$D/refreshed.xml" hub_browse_notice)"

# ---- no artwork, and no result
tap_node "$D/refreshed.xml" hub_search_box; sleep 0.6
for _ in $(seq 1 14); do adb shell input keyevent KEYCODE_DEL; done
adb shell input text "No%sArtwork"; adb shell input keyevent KEYCODE_ENTER; sleep 2.5
dump_ui "$D/noart.xml"
assert_eq "no artwork: the poster placeholder" "yes" "$(has_node "$D/noart.xml" hub_result_placeholder:900001)"
assert_eq "no artwork: no image node" "no" "$(has_node "$D/noart.xml" hub_result_image:900001)"
tap_node "$D/noart.xml" hub_search_box; sleep 0.6
for _ in $(seq 1 12); do adb shell input keyevent KEYCODE_DEL; done
adb shell input text "zzzz"; adb shell input keyevent KEYCODE_ENTER; sleep 2.5
dump_ui "$D/empty.xml"
assert_eq "no result: the empty line, not a blank grid" 'No results for “zzzz”' "$(node_text "$D/empty.xml" hub_browse_empty)"
for r in $RINGS; do ring_save "$r"; done

# ---- remove: the key removed in the setting; Browse is the no-key page again
MARK="$(ring_mark)"
key_remove_if_saved
dump_ui "$D/removed.xml"
assert_eq "removed: the setting says none is saved" "No key is saved." "$(node_text "$D/removed.xml" tmdb_key_status)"
assert_eq "removed: the credential file holds no entry" "" "$(cred_names)"
OFF="$(fixture_lines)"
hub browse; dump_ui "$D/nokey-again.xml"
assert_eq "removed: Browse is the no-key page again" "Film search needs a TMDB key." "$(node_text "$D/nokey-again.xml" hub_browse_notice)"
assert_contains "removed: [video] catalogue: no TMDB key saved" "[video] catalogue: no TMDB key saved" "$(vring "$MARK")"
assert_eq "removed: no request" "" "$(fixture_since "$OFF")"
for r in $RINGS; do ring_save "$r"; done

# ---- the token is in no ring slice, no dump and no logcat line; restore
assert_eq "the token is in no file of this row's folder (ring slices, dumps, logs)" "" "$(grep -rlF "$DUMMY_TOKEN" "$D" | head -3)"
assert_eq "the token is in no logcat line" "0" "$(adb logcat -d | grep -cF "$DUMMY_TOKEN")"
assert_eq "no crash of the shell" "" "$(no_crash)"
qa_pref qa_catalogue_base --remove
assert_eq "the QA pref is removed" "" "$(qa_pref_now qa_catalogue_base)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
adb shell am force-stop app.tileshell; ensure_start
row_end
