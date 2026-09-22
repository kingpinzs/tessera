#!/usr/bin/env bash
# E13 — place reminders (place source ruled C, 2026-09-17; both save routes).
#
# Saves Home at the spot from GPS and Work by typing an address through Nominatim, proves the typed
# route fails closed with the network off, then drives a real arrival and requires the reminder to fire
# ONCE — moving out and back in must fire nothing more, because GPS drift at the radius edge crosses it
# repeatedly and firing once has to come from the store, not from the platform.
#
# The platform throttles the location request behind a proximity alert
# (location_background_throttle_proximity_alert_interval_ms, 30 minutes by default in AOSP), so the row
# shortens it and restores what it found. P7 records the real latency on the phone.
. "$(dirname "$0")/lib.sh"

row_begin E13 "place reminders"

THROTTLE=location_background_throttle_proximity_alert_interval_ms
HOME_LON=-122.084; HOME_LAT=37.422        # the AVD's default fix
AWAY_LON=-122.090; AWAY_LAT=37.428        # ~700 m away

throttle_was="$(adb shell settings get global $THROTTLE | tr -d '\r')"
location_was="$(adb shell cmd location is-location-enabled | tr -d '\r')"
restore() {
  if [ "$throttle_was" = "null" ] || [ -z "$throttle_was" ]; then
    adb shell settings delete global $THROTTLE >/dev/null 2>&1
  else
    adb shell settings put global $THROTTLE "$throttle_was" >/dev/null 2>&1
  fi
  adb shell svc wifi enable >/dev/null 2>&1
  adb shell svc data enable >/dev/null 2>&1
  note "restored: throttle=$throttle_was, network enabled (location was $location_was)"
}
trap restore EXIT

adb shell settings put global $THROTTLE 5000
adb shell cmd location set-location-enabled true
note "throttle set to 5000 ms (was $throttle_was)"

places_json() { adb shell run-as app.tileshell cat /data/data/app.tileshell/files/cortana_reminders.json 2>/dev/null; }
notifications() { adb shell dumpsys notification --noredact 2>/dev/null; }
notif_count() { notifications | grep -c 'pkg=app.tileshell'; }

# Background location, as "Allow all the time" (Decisions).
adb shell dumpsys package app.tileshell > "$ROW_DIR/e13_perms.txt" 2>/dev/null
assert_contains "ACCESS_BACKGROUND_LOCATION is granted" "ACCESS_BACKGROUND_LOCATION: granted=true" \
  "$(cat "$ROW_DIR/e13_perms.txt")"
assert_contains "and fine location with it" "ACCESS_FINE_LOCATION: granted=true" "$(cat "$ROW_DIR/e13_perms.txt")"

# ---- route 1: saved at the spot, from GPS, with no network at all -------------------------------
adb shell emu geo fix $HOME_LON $HOME_LAT >/dev/null 2>&1 || adb emu geo fix $HOME_LON $HOME_LAT
sleep 3
ensure_start
cortana_assist
sleep 4
"$HERE/speak.sh" save_home 12 > /dev/null 2>&1
sleep 3
dump_ui "$ROW_DIR/e13_places.xml"
screencap "$ROW_DIR/e13_places.png"
assert_eq "saying 'this is home' opens the Places page with the name filled in" "yes" \
  "$(has_node "$ROW_DIR/e13_places.xml" cortana_places)"
tap_node "$ROW_DIR/e13_places.xml" places_save_current
sleep 4
places_json > "$ROW_DIR/e13_after_gps.json" 2>&1
assert_contains "Home is saved" '"name":"Home"' "$(tr -d ' ' < "$ROW_DIR/e13_after_gps.json")"
python3 "$HERE/coords.py" "$ROW_DIR/e13_after_gps.json" "$HOME_LAT" "$HOME_LON" > "$ROW_DIR/e13_coords.txt" 2>&1
coords_rc=$?
cat "$ROW_DIR/e13_coords.txt" >> "$LOG"
if [ $coords_rc -eq 0 ]; then
  _verdict PASS "at the coordinates the AVD was standing on (+/- 0.0005 deg)" "$(cat "$ROW_DIR/e13_coords.txt")"
else
  _verdict FAIL "at the coordinates the AVD was standing on (+/- 0.0005 deg)" "$(cat "$ROW_DIR/e13_coords.txt")"
fi
assert_contains "and it was saved from GPS, not a lookup" '"source":"current"' \
  "$(tr -d ' ' < "$ROW_DIR/e13_after_gps.json")"

# ---- route 2: a typed address, looked up once through Nominatim ---------------------------------
# With the network OFF first: the save has to fail closed and store nothing (edge case).
adb shell svc wifi disable; adb shell svc data disable
sleep 3
before="$(grep -c '"id"' "$ROW_DIR/e13_after_gps.json" 2>/dev/null || echo 0)"
dump_ui "$ROW_DIR/e13_places2.xml"
tap_node "$ROW_DIR/e13_places2.xml" places_type_address
sleep 2
adb shell input text "1600%sAmphitheatre%sParkway"
adb shell input keyevent KEYCODE_ENTER
sleep 8
places_json > "$ROW_DIR/e13_offline.json" 2>&1
screencap "$ROW_DIR/e13_offline.png"
assert_absent "offline, a typed address saves nothing" '"name":"Work"' "$(tr -d ' ' < "$ROW_DIR/e13_offline.json")"
assert_contains "and the shell says the lookup failed" "LookupFailed" "$(diag places | tail -3)"

adb shell svc wifi enable; adb shell svc data enable
sleep 8
dump_ui "$ROW_DIR/e13_places3.xml"
tap_node "$ROW_DIR/e13_places3.xml" places_type_address
sleep 2
adb shell input text "1600%sAmphitheatre%sParkway"
adb shell input keyevent KEYCODE_ENTER
sleep 12
places_json > "$ROW_DIR/e13_online.json" 2>&1
assert_contains "online, the typed address is saved" "Amphitheatre" "$(diag places | tail -5)"
assert_contains "the attribution line is on the page" "OpenStreetMap" \
  "$(dump_ui "$ROW_DIR/e13_places4.xml"; cat "$ROW_DIR/e13_places4.xml")"

# ---- the reminder, and one firing ----------------------------------------------------------------
adb shell emu geo fix $AWAY_LON $AWAY_LAT >/dev/null 2>&1 || adb emu geo fix $AWAY_LON $AWAY_LAT
sleep 4
ensure_start
cortana_assist
sleep 4
"$HERE/speak.sh" reminder_place 12 > /dev/null 2>&1
dump_ui "$ROW_DIR/e13_card.xml"
screencap "$ROW_DIR/e13_card.png"
assert_eq "the place card is on screen (H22)" "yes" "$(has_node "$ROW_DIR/e13_card.xml" "cortana_card:reminder_confirm")"
assert_eq "with a place field" "yes" "$(has_node "$ROW_DIR/e13_card.xml" "cortana_card_field:reminder_place")"
assert_eq "and NO recurrence dropdown: a place reminder fires once" "no" \
  "$(has_node "$ROW_DIR/e13_card.xml" cortana_card_recurrence)"
assert_contains "the spoken wording (H22)" "when you get to Home" "$(reply_text)"

"$HERE/speak.sh" yes 12 > /dev/null 2>&1
dump_ui "$ROW_DIR/e13_saved.xml"
screencap "$ROW_DIR/e13_saved.png"
assert_contains "the saved card's subline (H22)" "When I arrive at Home" \
  "$(node_text "$ROW_DIR/e13_saved.xml" cortana_card_saved_subline)"

notif_before="$(notif_count)"
adb shell emu geo fix $HOME_LON $HOME_LAT >/dev/null 2>&1 || adb emu geo fix $HOME_LON $HOME_LAT
sleep 45
notifications > "$ROW_DIR/e13_notifications.txt" 2>&1
assert_ne "arriving inside the radius fires the reminder within 60 s" "$notif_before" "$(notif_count)"
fired_once="$(notif_count)"

# Out and back in: nothing more. The reminder left the active list when it fired, and the re-arm
# dropped its alert, so drift at the radius edge cannot fire it again.
adb shell emu geo fix $AWAY_LON $AWAY_LAT >/dev/null 2>&1 || adb emu geo fix $AWAY_LON $AWAY_LAT
sleep 15
adb shell emu geo fix $HOME_LON $HOME_LAT >/dev/null 2>&1 || adb emu geo fix $HOME_LON $HOME_LAT
sleep 45
assert_eq "moving out and back in fires nothing more" "$fired_once" "$(notif_count)"

row_end
