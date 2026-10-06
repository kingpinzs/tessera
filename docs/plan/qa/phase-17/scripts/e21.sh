#!/usr/bin/env bash
# Phase 17 E21 — "Watch on" (the QA-Flix fixture; the title id through Wikidata — T17-15), inside the egress guard.
# The fixture's address is port 8091 (INDEX Change Log 2026-10-05 14:27 (2)). The doc's clauses → this driver's legs:
#
#   set-up         the dummy token saved in the TMDB key setting as E20 enters it (removed at the row's end — Q-17-1
#                  (a), RV12); prefs_edit.py sets qa_catalogue_base and qa_wikidata_base to the fixture
#   not installed  with testapps/qa-flix NOT installed, "Blade Runner 2049"'s page (hub_title asserted first — r3 V7)
#                  shows no "Watch on QA-Flix" row, and `[video] watch-on qa-flix "Blade Runner 2049" -> not installed`
#   installed      `adb install` the fixture → the row appears on the next draw (no restart: the discovery is live)
#   id branch      tap → the fixture's log shows the Wikidata-shaped query keyed on the title's TMDB id (335984, movie);
#                  `dumpsys activity activities` shows app.tileshell.testclient.qaflix/.MainActivity resumed, its
#                  qa_flix_uri text equals https://qa-flix.test/title/<the fixture's id> exactly; the lines `… "Blade
#                  Runner 2049": id found (wikidata)` and `… "Blade Runner 2049" -> https://qa-flix.test/title/…`; the
#                  intent names the package (r3 D13): the activity that resumed IS that package's, for a domain no app
#                  can verify
#   search branch  "Blade Runner" 1982 (no QA-Flix id in the fixture's Wikidata answer) → `id none (wikidata)` and the
#                  search form https://qa-flix.test/search?q=Blade+Runner+1982 reaches the fixture exactly
#   no service     a title the fixture catalogue marks as on no service → "Not on this phone" and no row
#   uninstall      the fixture uninstalled → the row is gone
#   guard          the weather lines after each restart; 0 packets elsewhere; the token is sent to no Wikidata request
#   leak scan      leak_scan.sh --logcat --path <this row's folder> -- <the typed token>
#
# Restores: the guard, the key, both QA prefs, the cache folder, QA-Flix uninstalled, the fixture server.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap 'video_cleanup; adb uninstall "$QAFLIX" >/dev/null 2>&1' EXIT
WANT_ID="$(python3 - "$P17/fixtures/catalogue/wikidata.json" <<'PY'
import json, re, sys
t = json.dumps(json.load(open(sys.argv[1])).get("335984", {}))
m = re.search(r'"(qa-[A-Za-z0-9_-]+)"', t)
print(m.group(1) if m else "")
PY
)"

video_row_begin E21 "Watch on: not installed, installed, the id branch, the search branch, no service, uninstalled"
[ -f "$QAFLIX_APK" ] || { _verdict FAIL "the QA-Flix fixture APK" "missing: ./gradlew :testapps:qa-flix:assembleDebug --offline"; row_end; exit 4; }
assert_ne "the fixture's Wikidata answer holds a QA-Flix id for TMDB 335984" "" "$WANT_ID"
fixture_up || { _verdict FAIL "the catalogue fixture on :$VPORT" "did not start"; exit 5; }
adb uninstall "$QAFLIX" >/dev/null 2>&1
assert_eq "precondition: QA-Flix is NOT installed" "" "$(adb shell pm list packages "$QAFLIX" | tr -d '\r')"
guard_on
qa_pref qa_catalogue_base "$FIXTURE_URL/"
qa_pref qa_wikidata_base "$FIXTURE_URL/wikidata/"
assert_eq "both QA prefs read back" "$FIXTURE_URL/ $FIXTURE_URL/wikidata/" "$(qa_pref_now qa_catalogue_base) $(qa_pref_now qa_wikidata_base)"
assert_absent "precondition: no TMDB key is saved at the row's start" "tmdb" "$(cred_names)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
guard_restart "start"
key_enter "$D/key"
search_and_open() { # result id, dump name — Browse, the search, the result's title page
  local m; m="$(ring_mark)"
  browse_blade_runner "$m" "$D/$2-browse.xml"
  tap_node "$D/$2-browse.xml" "hub_result:$1"; sleep 3
  dump_ui "$D/$2.xml"
}

# ------------------------------------------------------------------------------------------------ not installed
log "--- QA-Flix not installed"
MARK="$(ring_mark)"
search_and_open 335984 absent
assert_eq "the title page is the one read (hub_title, r3 V7)" "Blade Runner 2049" "$(node_text "$D/absent.xml" hub_title)"
assert_eq "not installed: no Watch on QA-Flix row" "no" "$(has_node "$D/absent.xml" hub_watch:qa-flix)"
assert_contains "not installed: [video] watch-on qa-flix \"Blade Runner 2049\" -> not installed" '[video] watch-on qa-flix "Blade Runner 2049" -> not installed' "$(vring "$MARK")"
record "not installed: what the page says instead" "$(node_text "$D/absent.xml" hub_watch_none)"

# ------------------------------------------------------------------------------------------------ installed
log "--- adb install the fixture while the page is up"
PID0="$(adb shell pidof app.tileshell:video | tr -d '\r')"
adb install "$QAFLIX_APK" > "$D/qaflix-install.out" 2>&1; sleep 2.5
dump_ui "$D/present.xml"
assert_eq "installed: the title page is still the one read" "Blade Runner 2049" "$(node_text "$D/present.xml" hub_title)"
assert_eq "installed: the row appears on the next draw" "Watch on QA-Flix" "$(node_text "$D/present.xml" hub_watch_label:qa-flix)"
assert_eq "… with no restart (the :video pid is unchanged: the discovery is live)" "$PID0" "$(adb shell pidof app.tileshell:video | tr -d '\r')"

# ------------------------------------------------------------------------------------------------ the id branch
log "--- the id branch: Blade Runner 2049"
MARK="$(ring_mark)"; OFF="$(fixture_lines)"
tap_node "$D/present.xml" hub_watch:qa-flix; sleep 3.5
assert_eq "id branch: dumpsys activity activities shows $QAFLIX/.MainActivity resumed" "$QAFLIX/.MainActivity" "$(top_activity)"
dump_ui "$D/flix-title.xml"
assert_eq "id branch: its qa_flix_uri text equals the title link exactly" "https://qa-flix.test/title/$WANT_ID" "$(node_text "$D/flix-title.xml" "$QAFLIX:id/qa_flix_uri")"
SLICE="$(vring "$MARK")"
assert_contains "id branch: [video] watch-on qa-flix \"Blade Runner 2049\": id found (wikidata)" '[video] watch-on qa-flix "Blade Runner 2049": id found (wikidata)' "$SLICE"
assert_contains "id branch: [video] watch-on qa-flix \"Blade Runner 2049\" -> https://qa-flix.test/title/…" "[video] watch-on qa-flix \"Blade Runner 2049\" -> https://qa-flix.test/title/$WANT_ID" "$SLICE"
FLOG="$(fixture_since "$OFF")"; printf '%s\n' "$FLOG" > "$D/fixture-id.txt"
assert_contains "id branch: the fixture's log shows the Wikidata-shaped query keyed on the title's TMDB id" "GET /wikidata/sparql authorisation absent api_key=absent tmdb=335984 kind=movie" "$FLOG"
assert_eq "id branch: the TMDB token was sent to no Wikidata request (authorisation PRESENT absent)" "0" "$(printf '%s\n' "$FLOG" | grep -c 'authorisation PRESENT')"
# The intent's package (r3 D13): qa-flix.test is a domain no app can verify, so an http(s) VIEW with no package would
# open the browser on Android 12+. That the fixture resumed is the proof; the handlers of the bare link are recorded.
record "id branch: who answers the bare link with no package (cmd package query-activities; the browser, not QA-Flix by default)" \
  "$(adb shell "cmd package query-activities --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://qa-flix.test/title/$WANT_ID" | tr -d '\r' | grep '/' | tr -d ' ' | tr '\n' ' ')"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
rings_save

# ------------------------------------------------------------------------------------------------ the search branch
log "--- the search branch: Blade Runner (1982)"
MARK="$(ring_mark)"
search_and_open 78 search
assert_eq "search branch: the title page" "Blade Runner" "$(node_text "$D/search.xml" hub_title)"
tap_node "$D/search.xml" hub_watch:qa-flix; sleep 3.5
assert_eq "search branch: QA-Flix resumed" "$QAFLIX/.MainActivity" "$(top_activity)"
dump_ui "$D/flix-search.xml"
assert_eq "search branch: the search form reaches the fixture exactly" "https://qa-flix.test/search?q=Blade+Runner+1982" "$(node_text "$D/flix-search.xml" "$QAFLIX:id/qa_flix_uri")"
SLICE="$(vring "$MARK")"
assert_contains "search branch: [video] watch-on qa-flix \"Blade Runner\": id none (wikidata)" '[video] watch-on qa-flix "Blade Runner": id none (wikidata)' "$SLICE"
record "search branch: the intent's line" "$(printf '%s\n' "$SLICE" | grep -F 'watch-on qa-flix "Blade Runner" ->' | tail -1 | sed 's/.*\[video\] //')"
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ------------------------------------------------------------------------------------------------ on no service
log "--- a title the fixture catalogue marks as on no service"
search_and_open tv-117884 none
assert_eq "no service: the title page (hub_title)" "Blade Runner: Black Lotus" "$(node_text "$D/none.xml" hub_title)"
assert_eq "no service: the page reads" "Not on this phone" "$(node_text "$D/none.xml" hub_watch_none)"
assert_eq "no service: no row" "0" "$(grep -c 'resource-id="hub_watch:' "$D/none.xml")"

# ------------------------------------------------------------------------------------------------ uninstall
log "--- uninstall the fixture while a title page is up"
search_and_open 335984 live
assert_eq "before the uninstall: the row is there" "yes" "$(has_node "$D/live.xml" hub_watch:qa-flix)"
adb uninstall "$QAFLIX" >/dev/null 2>&1; sleep 2.5
dump_ui "$D/gone.xml"
assert_eq "uninstalled: the title page is still the one read (hub_title)" "Blade Runner 2049" "$(node_text "$D/gone.xml" hub_title)"
assert_eq "uninstalled: the row is gone" "no" "$(has_node "$D/gone.xml" hub_watch:qa-flix)"

# ------------------------------------------------------------------------------------------------ restore, leak scan
log "--- restore"
key_remove_if_saved
assert_eq "the TMDB key is removed at the row's end (RV12)" "" "$(cred_names)"
assert_eq "no AndroidRuntime line names the shell since the row's MARK" "" "$(crash_since "$ROW_MARK")"
guard_off
qa_pref qa_catalogue_base --remove; qa_pref qa_wikidata_base --remove
assert_eq "the QA prefs are removed" " " "$(qa_pref_now qa_catalogue_base) $(qa_pref_now qa_wikidata_base)"
adb shell run-as app.tileshell rm -rf files/video_catalogue
fixture_down
assert_eq "QA-Flix is uninstalled" "" "$(adb shell pm list packages "$QAFLIX" | tr -d '\r')"
c6; ensure_start
leak_scan_row "$DUMMY_TOKEN"
row_end
