#!/usr/bin/env bash
# Development proof of E17's core: with the Calendar app NOT opened, a birthday on a contact makes the read-only LOCAL
# calendar "Birthdays" under the account name `Tessera Birthdays` (never `Tessera`) with a yearly all-day event, no
# reminder rows; a no-year birthday starts from this year's date; a 29 February birthday repeats on February's last
# day (28 Feb 2027, 29 Feb 2028); a value in neither form is not counted; removing the birthday removes the event and
# keeps the calendar. Contacts are the session's own phone-only raw contacts, deleted by id. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E17_BIRTHDAY "a contact's birthday becomes a Birthdays calendar event with the Calendar app never opened"
RAW=content://com.android.contacts/raw_contacts
DATA=content://com.android.contacts/data
IDS=""
mkcontact() { # name -> raw contact id (phone-only: no account). Called as X="$(mkcontact …)"; IDS="$IDS $X" — a subshell cannot add to IDS.
  adb shell "content insert --uri $RAW --bind account_type:n: --bind account_name:n:" < /dev/null > /dev/null 2>&1
  local rid; rid="$(S content query --uri $RAW --projection _id | tail -1 | grep -oE '_id=[0-9]+' | cut -d= -f2)"
  adb shell "content insert --uri $DATA --bind raw_contact_id:i:$rid --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$1'" < /dev/null > /dev/null 2>&1
  echo "$rid"
}
rmcontact() { adb shell "content delete --uri '$RAW?caller_is_syncadapter=true' --where \"_id=$1\"" < /dev/null > /dev/null 2>&1; }
birthday() { adb shell "content insert --uri $DATA --bind raw_contact_id:i:$1 --bind mimetype:s:vnd.android.cursor.item/contact_event --bind data2:i:3 --bind data1:s:'$2'" < /dev/null > /dev/null 2>&1; }
bcal() { cals | grep -F 'account_name=Tessera Birthdays,' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
bevents() { S "content query --uri $EVENTS --projection _id:title:dtstart:allDay:rrule:duration --where \"calendar_id=$1 AND deleted=0\"" | sed 's/^Row: [0-9]* //' | sort -t= -k3; }

# What this script's first two runs left: their mkcontact ran in a subshell, so the id list they deleted from was empty
# (raw contacts 134–141: two sets of Ann Lee, Bob Stone, Lea Leap, Odd Form). Removed here by those ids alone.
for rid in 134 135 136 137 138 139 140 141; do
  [ -n "$(S "content query --uri $RAW --projection display_name --where \"_id=$rid AND display_name IN ('Ann Lee','Bob Stone','Lea Leap','Odd Form')\"" | grep 'display_name=')" ] && rmcontact "$rid"
done
c6; cal_fixtures_down; sleep 2
ensure_start
EXISTING="$(S "content query --uri $DATA --projection data1 --where \"mimetype='vnd.android.cursor.item/contact_event' AND data2=3\"" | grep -c 'data1=')"
record "birthdays already on the phone's contacts before the session" "$EXISTING"
assert_eq "no Birthdays calendar before the first birthday" "" "$(bcal)"
TESS="$(tessera_id)"
MD="$(S date +%m-%d)"; TODAY="$(device_date 0)"; TOMORROW="$(device_date 1)"

# ---- the first birthday: today's month-day, 1990
ANN="$(mkcontact 'Ann Lee')"; IDS="$IDS $ANN"
MARK="$(ring_mark)"
birthday "$ANN" "1990-$MD"
for i in 1 2 3 4 5; do sleep 1; [ -n "$(bcal)" ] && break; done
B="$(bcal)"; note "Birthdays calendar id [$B] after $i s"
assert_ne "within 5 s the provider has a Birthdays calendar" "" "$B"
ROWB="$(cals | grep "_id=$B,")"; log "its row: $ROWB"
assert_contains "under its own account, never Tessera" "account_name=Tessera Birthdays, account_type=LOCAL, calendar_displayName=Birthdays, calendar_access_level=200" "$ROWB"
assert_eq "Tessera is untouched: still one, another id" "1 yes" "$(cals | grep -c 'account_name=Tessera,') $([ "$TESS" != "$B" ] && echo yes)"
EV="$(bevents "$B")"; log "its events: $EV"
assert_contains "a yearly all-day event for Ann from 1990" "title=Ann Lee's birthday, dtstart=$(( $(date -u -d "1990-$MD" +%s) * 1000 )), allDay=1, rrule=FREQ=YEARLY" "$EV"
sleep 1
assert_contains "the synced line" "[calendar] birthdays: $(( EXISTING + 1 )) synced" "$(ring_since "$MARK")"
ANN_EV="$(printf '%s\n' "$EV" | grep "Ann Lee" | sed -n 's/^_id=\([0-9]*\),.*/\1/p')"
assert_contains "no reminder rows on a birthday" "No result found" "$(S "content query --uri content://com.android.calendar/reminders --where \"event_id=$ANN_EV\"")"
assert_eq "an instance today, with the Calendar app never opened" 1 "$(instance_count "$(day_ms 0 00:00)" "$(( $(day_ms 1 00:00) - 1 ))" "$ANN_EV")"
assert_contains "the feed (the tile and the Agenda pod) sees it" "agenda=" "$(line_of "$(ring_since "$MARK")" '[calendar] refresh (provider change)')"
record "the feed's refresh line after the birthday" "$(line_of "$(ring_since "$MARK")" '[calendar] refresh (provider change)')"

# ---- the date forms (r3 D14)
BOB="$(mkcontact 'Bob Stone')"; IDS="$IDS $BOB"; birthday "$BOB" "--$(echo "$TOMORROW" | cut -c6-10)"
LEA="$(mkcontact 'Lea Leap')"; IDS="$IDS $LEA"; birthday "$LEA" "1992-02-29"
MARK2="$(ring_mark)"
ODD="$(mkcontact 'Odd Form')"; IDS="$IDS $ODD"; birthday "$ODD" "Oct 1"
sleep 4
EV="$(bevents "$B")"; log "events after the date forms: $EV"
assert_contains "no year: a yearly event from this year's date (tomorrow)" "title=Bob Stone's birthday, dtstart=$(utc_day_ms 1), allDay=1, rrule=FREQ=YEARLY" "$EV"
assert_contains "29 February: February's last day every twelve months" "title=Lea Leap's birthday, dtstart=$(( $(date -u -d 1992-02-29 +%s) * 1000 )), allDay=1, rrule=FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1" "$EV"
LEA_EV="$(printf '%s\n' "$EV" | grep "Lea Leap" | sed -n 's/^_id=\([0-9]*\),.*/\1/p')"
feb() { S "content query --uri content://com.android.calendar/instances/when/$(( $(date -u -d "$1-02-01" +%s) * 1000 ))/$(( $(date -u -d "$1-03-01" +%s) * 1000 - 1 )) --projection event_id:begin --where \"event_id=$LEA_EV\"" | sed -n 's/.*begin=\([0-9]*\).*/\1/p' | while read -r b; do date -u -d "@$(( b / 1000 ))" +%Y-%m-%d; done | paste -sd' '; }
assert_eq "one instance in February 2027, on the 28th" "2027-02-28" "$(feb 2027)"
assert_eq "one instance in February 2028, on the 29th" "2028-02-29" "$(feb 2028)"
assert_absent "a value in neither form makes no event" "Odd Form" "$EV"
assert_contains "and is not counted" "[calendar] birthdays: $(( EXISTING + 3 )) synced" "$(ring_since "$MARK2")"

# ---- in the Calendar app: shown today, read-only
open_day "$(day_ms 0 12:00)"; dump_ui "$ROW_DIR/day.xml"
assert_eq "today's Day view shows Ann's birthday in the all-day band" "yes Ann Lee's birthday" "$(has_node "$ROW_DIR/day.xml" "cal_allday:$TODAY") $(node_text "$ROW_DIR/day.xml" "cal_event_title:$ANN_EV" | sed "s/&apos;/'/g")"
tap "cal_event:$ANN_EV" 2; dump_ui "$ROW_DIR/page.xml"
assert_eq "its page is read-only (the Birthdays calendar is not Tessera)" "yes 0" "$(has_node "$ROW_DIR/page.xml" "cal_event_page:$ANN_EV") $(count_prefix "$ROW_DIR/page.xml" cal_event_action:)"
adb shell input keyevent KEYCODE_BACK; sleep 1
tap cal_menu 1.5; dump_ui "$ROW_DIR/pane.xml"
assert_eq "the pane lists Birthdays under its own account" "yes yes" "$(has_node "$ROW_DIR/pane.xml" "cal_calendar_row:$B") $(has_node "$ROW_DIR/pane.xml" 'cal_account:Tessera Birthdays')"
c6

# ---- the birthday removed: the event goes, the calendar is kept
MARK3="$(ring_mark)"
adb shell "content delete --uri $DATA --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/contact_event'\"" < /dev/null > /dev/null 2>&1
sleep 4
assert_absent "Ann's birthday removed: her event is gone" "Ann Lee" "$(bevents "$B")"
assert_contains "the count follows" "[calendar] birthdays: $(( EXISTING + 2 )) synced" "$(ring_since "$MARK3")"

# ---- the last birthdays go: the calendar is kept, empty
MARK4="$(ring_mark)"
adb shell "content delete --uri $DATA --where \"raw_contact_id IN ($BOB,$LEA,$ODD) AND mimetype='vnd.android.cursor.item/contact_event'\"" < /dev/null > /dev/null 2>&1
sleep 4
assert_eq "the calendar is kept, empty, when the last birthday goes" "$B/$EXISTING" "$(bcal)/$(bevents "$B" | grep -c 'title=')"
assert_contains "and the line says so" "[calendar] birthdays: $EXISTING synced" "$(ring_since "$MARK4")"

# ---- a contact deleted whole, as a contacts app deletes it: its birthday goes with it
birthday "$BOB" "1985-$MD"; sleep 3
assert_contains "Bob has a birthday again" "Bob Stone" "$(bevents "$B")"
MARK5="$(ring_mark)"
adb shell "content delete --uri $RAW --where \"_id=$BOB\"" < /dev/null > /dev/null 2>&1
sleep 5
record "birthdays lines after the contact's delete" "$(ring_since "$MARK5" | grep -F '[calendar] birthdays' | sed 's/.*\[calendar\] //' | tr '\n' ';')"
assert_absent "the deleted contact's birthday event is gone" "Bob Stone" "$(bevents "$B")"

# ---- restore: the session's raw contacts removed for good, then the Birthdays calendar
for rid in $IDS; do rmcontact "$rid"; done
sleep 1
assert_eq "four contacts were made by the session" 4 "$(echo $IDS | wc -w | tr -d ' ')"
assert_eq "the session's raw contacts are gone" 0 "$(S "content query --uri '$RAW?caller_is_syncadapter=true' --projection _id --where \"_id IN ($(echo $IDS | tr ' ' ','))\"" | grep -c '_id=')"
cal_fixtures_down
assert_eq "the Birthdays calendar is deleted at the end" "" "$(bcal)"
ensure_start
session_end
