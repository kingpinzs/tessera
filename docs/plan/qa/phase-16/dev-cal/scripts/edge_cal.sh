#!/usr/bin/env bash
# Development run of Calendar's own edge cases that no other dev session carries: the calendar provider turned off
# (the notice, `calendars: none`, nothing created, Tess's "I don't have a calendar to add that to."); an event deleted
# under its open editor (`write update …: failed`, the page under it closes with a notice); a time zone change (a timed
# event moves to the new wall time, an all-day one keeps its date) and the 24-hour setting, both with no restart; and
# Tessera deleted by another app while the app is open (its events leave the views; the next start makes it again).
# Each step restores what it changed. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin EDGE_CAL "the provider off, a vanished event, a zone change, 24-hour time, Tessera deleted"
CRASH0="$(S dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c '^Process: app.tileshell$')"
c6

# ---------------------------------------------------------------- an event deleted under its open editor
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; BEFORE="$(event_count "calendar_id=$TESS AND deleted=0")"
V="$(mkevent "$TESS" 'Edge vanish' "$(day_ms 0 13:00)" "$(day_ms 0 14:00)")"
open_event "$V"; tap cal_event_action:edit 2; type_field title "X"
purge_tessera_events "_id=$V"; sleep 1
MARK="$(ring_mark)"
tap cal_editor_save 2.5; dump_ui "$ROW_DIR/vanish_save.xml"
assert_contains "Save of an event that is gone: the failed line" "[calendar] write update event=$V: failed the event is gone" "$(ring_since "$MARK")"
assert_contains "and the editor says so" "couldn't be saved" "$(node_text "$ROW_DIR/vanish_save.xml" cal_notice | sed "s/&apos;/'/g")"
assert_eq "nothing was written" 0 "$(event_count "title LIKE 'Edge vanish%'")"
tap cal_editor_cancel 1.5; dump_ui "$ROW_DIR/vanish_after.xml"
assert_eq "the vanished event's page is not left open under the editor" "no yes" "$(has_node "$ROW_DIR/vanish_after.xml" "cal_event_page:$V") $(has_node "$ROW_DIR/vanish_after.xml" cal_view)"
assert_contains "the views say what happened" "no longer on this phone" "$(node_text "$ROW_DIR/vanish_after.xml" cal_notice)"

# ---------------------------------------------------------------- a time zone change and the 24-hour setting, no restart
ZONE="$(S getprop persist.sys.timezone)"; H24="$(S settings get system time_12_24)"
T_START="$(day_ms 0 13:00)"
T="$(mkevent "$TESS" 'Edge timed' "$T_START" "$(( T_START + 3600000 ))")"
A="$(mkevent "$TESS" 'Edge all day' "$(utc_day_ms 0)" "$(utc_day_ms 1)" --bind allDay:i:1 --bind eventTimezone:s:UTC)"
open_event "$T"; dump_ui "$ROW_DIR/tz_before.xml"; TEXT0="$(node_text "$ROW_DIR/tz_before.xml" "cal_event_time:$T")"
open_event "$A"; dump_ui "$ROW_DIR/tz_allday_before.xml"; ALL0="$(node_text "$ROW_DIR/tz_allday_before.xml" "cal_event_time:$A")"
PID0="$(S pidof app.tileshell)"
adb shell cmd alarm set-timezone Asia/Tokyo; sleep 3
assert_eq "the device zone is Asia/Tokyo" "Asia/Tokyo" "$(S getprop persist.sys.timezone)"
dump_ui "$ROW_DIR/tz_allday_tokyo.xml"
assert_eq "an all-day event keeps its date across a zone change ($ALL0)" "$ALL0" "$(node_text "$ROW_DIR/tz_allday_tokyo.xml" "cal_event_time:$A")"
open_event "$T"; dump_ui "$ROW_DIR/tz_tokyo.xml"; TEXT1="$(node_text "$ROW_DIR/tz_tokyo.xml" "cal_event_time:$T")"
log "the timed event: [$TEXT0] in $ZONE, [$TEXT1] in Asia/Tokyo"
WANT="$(TZ=Asia/Tokyo date -d "@$(( T_START / 1000 ))" '+%a %-d %b %Y, %-I:%M %p')"
assert_ne "the time text changed with the zone" "$TEXT0" "$TEXT1"
assert_contains "a timed event shows at its Tokyo wall time ($WANT)" "$WANT" "$TEXT1"
# The page that was open across the change follows it too, with no reopen.
adb shell cmd alarm set-timezone "$ZONE"; sleep 3; dump_ui "$ROW_DIR/tz_back_open.xml"
assert_eq "the open page follows the zone back, with no reopen" "$TEXT0" "$(node_text "$ROW_DIR/tz_back_open.xml" "cal_event_time:$T")"
adb shell cmd alarm set-timezone "$ZONE"; sleep 3
assert_eq "the zone is restored" "$ZONE" "$(S getprop persist.sys.timezone)"
adb shell settings put system time_12_24 24; sleep 2
open_event "$T"; dump_ui "$ROW_DIR/h24.xml"
assert_contains "with the 24-hour setting every time reads H:mm" "13:00 – 14:00" "$(node_text "$ROW_DIR/h24.xml" "cal_event_time:$T")"
if [ "$H24" = null ]; then adb shell settings delete system time_12_24 > /dev/null; else adb shell settings put system time_12_24 "$H24"; fi
assert_eq "the 12 / 24-hour setting is restored" "$H24" "$(S settings get system time_12_24)"
assert_eq "none of it restarted the shell" "$PID0" "$(S pidof app.tileshell)"

# ---------------------------------------------------------------- Tessera deleted by another app while the app is open
tap cal_event_action:edit 0 > /dev/null 2>&1 || true
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
open_day "$(day_ms 0 12:00)"; dump_ui "$ROW_DIR/before_delete.xml"
assert_eq "the events show before Tessera is deleted" yes "$(has_node "$ROW_DIR/before_delete.xml" "cal_event:$T")"
rm_tessera; sleep 2.5; dump_ui "$ROW_DIR/after_delete.xml"
assert_eq "Tessera deleted by another app: its events leave the open view" "no no" "$(has_node "$ROW_DIR/after_delete.xml" "cal_event:$T") $(has_node "$ROW_DIR/after_delete.xml" "cal_event:$A")"
assert_eq "nothing was recreated behind the open app" 0 "$(cals | grep -c 'account_name=Tessera,')"
c6; MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2
assert_contains "the next start makes it again" "(local created id=" "$(line_of "$(ring_since "$MARK")" '[calendar] calendars:')"
assert_eq "one Tessera" 1 "$(cals | grep -c 'account_name=Tessera,')"

# ---------------------------------------------------------------- the calendar provider turned off
c6
adb shell pm disable-user --user 0 com.android.providers.calendar > /dev/null 2>&1; sleep 2
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2.5; dump_ui "$ROW_DIR/provider_off.xml"; screencap "$ROW_DIR/provider_off.png"
SLICE="$(ring_since "$MARK")"
assert_contains "the page says the calendar storage is off" "turned off" "$(node_text "$ROW_DIR/provider_off.xml" cal_notice)"
assert_contains "calendars: none, with the reason" "[calendar] calendars: none (" "$SLICE"
log "$(line_of "$SLICE" '[calendar] calendars: none')"
absent_in "nothing was created" "local calendar created" "$SLICE"
adb shell input keyevent KEYCODE_HOME; sleep 1
ensure_start; cortana_assist; sleep 4
TMARK="$(ring_mark)"
type_request "add a meeting called standup to my calendar at ten AM" 1
for _ in 1 2 3 4 5 6 7 8; do dump_ui "$ROW_DIR/off_card.xml"; [ "$(has_node "$ROW_DIR/off_card.xml" cortana_card_button:confirm)" = yes ] && break; sleep 0.5; done
tap_node "$ROW_DIR/off_card.xml" cortana_card_button:confirm; sleep 3
assert_eq "Tess's add with no provider" "I don't have a calendar to add that to." "$(ring_since "$TMARK" | grep -F '[speech]' | grep -oE 'text="[^"]*"' | tail -1 | sed 's/^text="//; s/"$//' | sed "s/&apos;/'/g")"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 1
ring_save
adb shell pm enable com.android.providers.calendar > /dev/null 2>&1; sleep 2
assert_contains "the provider is enabled again" "com.android.providers.calendar" "$(S pm list packages -e com.android.providers.calendar)"
c6; MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2.5; dump_ui "$ROW_DIR/provider_on.xml"
assert_eq "with the provider back the views load" "no yes" "$(has_node "$ROW_DIR/provider_on.xml" cal_notice) $(has_node "$ROW_DIR/provider_on.xml" cal_strip)"
assert_contains "and Tessera is still the one calendar (a LOCAL calendar survives the provider's restart)" "(local present)" "$(line_of "$(ring_since "$MARK")" '[calendar] calendars:')"

# ---------------------------------------------------------------- restore
purge_tessera_events "title LIKE 'Edge %' OR title='standup'"
assert_eq "no session event is left" 0 "$(event_count "title LIKE 'Edge %'")"
assert_eq "no new crash of the shell" "$CRASH0" "$(S dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c '^Process: app.tileshell$')"
c6; ensure_start
session_end
