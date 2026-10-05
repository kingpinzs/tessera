#!/usr/bin/env bash
# Phase 17 development proof, build task 11 (brief C; E21's form): "Watch on <service>" against the QA-Flix fixture app —
# not installed (no row, the line), installed (the row appears with no restart), the id branch (Wikidata has QA-Flix's
# id: the title link, exactly), the search branch (no id: the search link, exactly), a title on no service, the answer
# cached, and the fixture uninstalled (the row gone). Restores: the fixture app uninstalled, the key and prefs removed.
. "$(dirname "$0")/v17.sh"
row_begin C_WATCH "Watch on: not installed, installed, the title link, the search link, no service"
D="$ROW_DIR"
QAFLIX=app.tileshell.testclient.qaflix
QAFLIX_APK="$V17_TREE/testapps/qa-flix/build/outputs/apk/debug/qa-flix-debug.apk"
[ -f "$QAFLIX_APK" ] || { echo "build :testapps:qa-flix:assembleDebug first" >&2; exit 4; }
fixture_up || exit 5
trap 'fixture_down; adb uninstall '"$QAFLIX"' >/dev/null 2>&1' EXIT
adb uninstall "$QAFLIX" >/dev/null 2>&1
qa_pref qa_catalogue_base "$FIXTURE_URL/"
qa_pref qa_wikidata_base "$FIXTURE_URL/wikidata/"
assert_eq "both QA prefs read back" "$FIXTURE_URL/ $FIXTURE_URL/wikidata/" "$(qa_pref_now qa_catalogue_base) $(qa_pref_now qa_wikidata_base)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
hub settings 2; dump_ui "$D/s0.xml"; tap_node "$D/s0.xml" hub_settings:tmdbkey; sleep 1
key_type_and_save "$D/key"
search_and_open() { # result id, dump name
  hub browse 3; dump_ui "$D/$2-browse.xml"
  if [ "$(has_node "$D/$2-browse.xml" "hub_result:$1")" != yes ]; then
    tap_node "$D/$2-browse.xml" hub_search_box; sleep 0.6; adb shell input text "Blade%sRunner"; adb shell input keyevent KEYCODE_ENTER; sleep 3
    dump_ui "$D/$2-browse.xml"
  fi
  tap_node "$D/$2-browse.xml" "hub_result:$1"; sleep 2.5
  dump_ui "$D/$2.xml"
}

# ---- QA-Flix not installed: no row, and the line says so
MARK="$(ring_mark)"
search_and_open 335984 absent
assert_eq "the title page" "Blade Runner 2049" "$(node_text "$D/absent.xml" hub_title)"
assert_eq "not installed: no Watch on QA-Flix row" "no" "$(has_node "$D/absent.xml" hub_watch:qa-flix)"
assert_eq "not installed: the page says so" "Not on this phone" "$(node_text "$D/absent.xml" hub_watch_none)"
assert_contains "not installed: the line" '[video] watch-on qa-flix "Blade Runner 2049" -> not installed' "$(vring "$MARK")"

# ---- installed while the page is up: the row appears on the next draw
adb install "$QAFLIX_APK" >/dev/null 2>&1; sleep 2.5
dump_ui "$D/present.xml"
assert_eq "installed: the row appears with no restart" "Watch on QA-Flix" "$(node_text "$D/present.xml" hub_watch_label:qa-flix)"
assert_contains "installed: JustWatch is credited where the rows are" "JustWatch" "$(node_text "$D/present.xml" hub_justwatch)"
record "the row's epx (R3 C2: a 44-epx app-list row)" "$(epx "$D/present.xml" hub_watch:qa-flix)"

# ---- the id branch: Wikidata has QA-Flix's id for this title → its title link, exactly
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
tap_node "$D/present.xml" hub_watch:qa-flix; sleep 3
assert_eq "id branch: QA-Flix is on top" "$QAFLIX/.MainActivity" "$(top)"
dump_ui "$D/flix-title.xml"
assert_eq "id branch: the URI it was opened with" "https://qa-flix.test/title/qa-2049" "$(node_text "$D/flix-title.xml" "$QAFLIX:id/qa_flix_uri")"
SLICE="$(vring "$MARK")"
assert_contains "id branch: id found" '[video] watch-on qa-flix "Blade Runner 2049": id found (wikidata)' "$SLICE"
assert_contains "id branch: the intent's line" '[video] watch-on qa-flix "Blade Runner 2049" -> https://qa-flix.test/title/qa-2049' "$SLICE"
FLOG="$(fixture_since "$OFF")"
assert_contains "the fixture saw the Wikidata query keyed on the TMDB id" "GET /wikidata/sparql authorisation absent api_key=absent tmdb=335984 kind=movie" "$FLOG"
assert_eq "the TMDB token was not sent to Wikidata" "0" "$(echo "$FLOG" | grep -c 'authorisation PRESENT')"
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ---- the same title again: the answer is cached with the title page, so Wikidata is not asked twice
OFF="$(fixture_lines)"
dump_ui "$D/again.xml"; tap_node "$D/again.xml" hub_watch:qa-flix; sleep 2.5
assert_eq "again: QA-Flix on top" "$QAFLIX/.MainActivity" "$(top)"
assert_eq "again: no second Wikidata query" "0" "$(fixture_since "$OFF" | grep -c '/wikidata/')"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
for r in $RINGS; do ring_save "$r"; done

# ---- the search branch: no QA-Flix id for the 1982 film → the search link with the title and the year, exactly
MARK="$(ring_mark)"
search_and_open 78 search
tap_node "$D/search.xml" hub_watch:qa-flix; sleep 3
dump_ui "$D/flix-search.xml"
assert_eq "search branch: the URI it was opened with" "https://qa-flix.test/search?q=Blade+Runner+1982" "$(node_text "$D/flix-search.xml" "$QAFLIX:id/qa_flix_uri")"
SLICE="$(vring "$MARK")"
assert_contains "search branch: id none" '[video] watch-on qa-flix "Blade Runner": id none (wikidata)' "$SLICE"
assert_contains "search branch: the intent's line" '[video] watch-on qa-flix "Blade Runner" -> https://qa-flix.test/search?q=Blade+Runner+1982' "$SLICE"
adb shell input keyevent KEYCODE_BACK; sleep 1.2

# ---- a title the catalogue marks as on no service
search_and_open tv-117884 none
assert_eq "no service: the title page" "Blade Runner: Black Lotus" "$(node_text "$D/none.xml" hub_title)"
assert_eq "no service: the page says so" "Not on this phone" "$(node_text "$D/none.xml" hub_watch_none)"
assert_eq "no service: no row" "0" "$(grep -c 'resource-id="hub_watch:' "$D/none.xml")"

# ---- uninstalled while a title page is up: the row is gone
search_and_open 335984 live
assert_eq "before the uninstall: the row" "yes" "$(has_node "$D/live.xml" hub_watch:qa-flix)"
adb uninstall "$QAFLIX" >/dev/null 2>&1; sleep 2.5
dump_ui "$D/gone.xml"
assert_eq "uninstalled: the title page is still the one read" "Blade Runner 2049" "$(node_text "$D/gone.xml" hub_title)"
assert_eq "uninstalled: the row is gone" "no" "$(has_node "$D/gone.xml" hub_watch:qa-flix)"
for r in $RINGS; do ring_save "$r"; done

key_remove_if_saved
assert_eq "the TMDB key is removed" "" "$(cred_names)"
assert_eq "the token is in no file of this row's folder" "" "$(grep -rlF "$DUMMY_TOKEN" "$D" | head -3)"
qa_pref qa_catalogue_base --remove; qa_pref qa_wikidata_base --remove
assert_eq "the QA prefs are removed" " " "$(qa_pref_now qa_catalogue_base) $(qa_pref_now qa_wikidata_base)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
assert_eq "no crash of the shell" "" "$(no_crash)"
adb shell am force-stop app.tileshell; ensure_start
row_end
