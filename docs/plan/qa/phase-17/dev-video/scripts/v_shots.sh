#!/usr/bin/env bash
# Phase 17 development aid: screenshots of the hub's P4 pages (Browse's strips, a title page, the settings pages, the
# sign-in form and the insecure-server prompt) for a look by eye. It asserts nothing about them — it records that each
# page was reached — and is not proof of anything. Restores: the key and the QA pref removed.
. "$(dirname "$0")/v17.sh"
row_begin V_SHOTS "screenshots of the hub's designed pages (recorded only)"
D="$ROW_DIR"
fixture_up || exit 5
trap 'fixture_down' EXIT
qa_pref qa_catalogue_base "$FIXTURE_URL/"
adb shell run-as app.tileshell rm -rf files/video_catalogue
shot() { screencap "$D/$1.png"; dump_ui "$D/$1.xml"; record "$1" "$(grep -o 'resource-id="hub_page:[a-z]*"' "$D/$1.xml" | head -1 | sed 's/.*hub_page://;s/"//')"; }
hub browse 2.5; shot 01-browse-nokey
hub settings 2; shot 02-settings
dump_ui "$D/s.xml"; tap_node "$D/s.xml" hub_settings:tmdbkey; sleep 1; shot 03-key-empty
key_type_and_save "$D/k"; screencap "$D/04-key-saved.png"
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$D/s2.xml"; tap_node "$D/s2.xml" hub_settings:about; sleep 1; shot 05-about
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$D/s3.xml"; tap_node "$D/s3.xml" hub_settings:server; sleep 1; shot 06-server-form
server_form "$D/f" "http://192.0.2.10:8096" qa pw-for-a-screenshot; sleep 1.5; shot 07-insecure-prompt
dump_ui "$D/p.xml"; tap_node "$D/p.xml" server_insecure_cancel; sleep 1
hub browse 4; shot 08-browse-strips
dump_ui "$D/b.xml"; tap_node "$D/b.xml" hub_search_box; sleep 0.6; adb shell input text "Blade%sRunner"; adb shell input keyevent KEYCODE_ENTER; sleep 3; shot 09-results
dump_ui "$D/r.xml"; tap_node "$D/r.xml" hub_result:335984; sleep 2.5; shot 10-title
key_remove_if_saved
qa_pref qa_catalogue_base --remove
adb shell run-as app.tileshell rm -rf files/video_catalogue
adb shell am force-stop app.tileshell; ensure_start
row_end
