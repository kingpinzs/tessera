#!/usr/bin/env bash
# p16.sh's own proof (build task 8 (a)): every helper the rows lean on is run once against the device and its effect
# read back from the provider, so a row's failure is the product's and not the floor's. Restores everything it makes.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p16.sh"

row_begin P16SELF "p16.sh: the driver floor's helpers, each run and read back"

# ---- calendars
note "calendars before: $(cals | tr '\n' ' ')"
T_BEFORE="$(tessera_id)"
mkcal_qa >/dev/null
mkcal "qa.personal@example.com" "Personal" 0 >/dev/null
mkcal "qa.work@example.com" "Work" 1 >/dev/null
mkcal "qa.personal@example.com" "Shared" 0 200 >/dev/null
QA_ID="$(cal_id qa)"; PERSONAL="$(cal_id qa.personal@example.com Personal)"; WORK="$(cal_id qa.work@example.com)"; SHARED="$(cal_id qa.personal@example.com Shared)"
assert_ne "mkcal_qa / cal_id: the QA calendar" "" "$QA_ID"
assert_ne "mkcal: Personal" "" "$PERSONAL"
assert_ne "mkcal: Work" "" "$WORK"
assert_ne "mkcal with a level: Shared" "" "$SHARED"
assert_contains "mkcal's fourth argument is the access level" "calendar_displayName=Shared, calendar_access_level=200" "$(cals | grep -F "_id=$SHARED,")"
assert_contains "mkcal's default level is 700" "calendar_displayName=Work, calendar_access_level=700" "$(cals | grep -F "_id=$WORK,")"
NOW_MS="$(device_ms)"
OFF="$(mkevent "$WORK" Offsite $(( NOW_MS + 5 * 86400000 )) $(( NOW_MS + 5 * 86400000 + 3600000 )))"
assert_ne "mkevent / event_id: Offsite in Work" "" "$OFF"
assert_eq "cal_count: Work holds one event" "1" "$(cal_count "$WORK" Work | tail -1)"
assert_eq "cal_events: it is Offsite" "$OFF|Offsite" "$(cal_events "$WORK")"
assert_eq "cal_count: Personal holds none" "0" "$(cal_count "$PERSONAL" Personal | tail -1)"
assert_contains "event_row" "title=Offsite" "$(event_row "$OFF" _id:title:calendar_id)"
QEV="$(mkevent "$QA_ID" 'Two words' $(( NOW_MS + 3600000 )) $(( NOW_MS + 7200000 )) '--bind eventLocation:s:Room')"
assert_ne "mkevent: a title with a space" "" "$QEV"
rm_event "$QEV"
assert_eq "rm_event: the row is gone" "" "$(event_id 'Two words')"
cal_fixtures_down
assert_eq "cal_fixtures_down: Offsite went with its calendar" "" "$(event_id Offsite)"
assert_eq "cal_fixtures_down keeps Tessera" "$T_BEFORE" "$(tessera_id)"

# ---- contacts
people_fixtures_up
assert_eq "people_fixtures_up: seven raw contacts recorded" "7" "$(grep -c . "$ROW_DIR/people-fixtures.ids")"
assert_eq "raw_count rose by seven" "$(( RAW_BEFORE + 7 ))" "$(raw_count)"
assert_eq "raw_of: Ann Lee" "$ANN" "$(raw_of 'Ann Lee')"
assert_eq "raw_of: a non-Latin name (张伟)" "$ZHANG" "$(raw_of '张伟')"
assert_eq "raw_of: an accented name (Zoë Ǻrén)" "$ZOE" "$(raw_of 'Zoë Ǻrén')"
assert_eq "Cara Diaz is two raw contacts" "$CARA1 $CARA2" "$(raw_of 'Cara Diaz')"
assert_eq "…aggregated into one contact (r3 V15)" "$(contact_of "$CARA1")" "$(contact_of "$CARA2")"
ANN_C="$(contact_of "$ANN")"; ANN_L="$(lookup_of "$ANN_C")"
assert_ne "contact_of / lookup_of: Ann" "" "$ANN_L"
note "Ann: raw $ANN contact $ANN_C lookup $ANN_L"
assert_contains "Ann is phone-only (NULL account)" "account_name=NULL, account_type=NULL" "$(q "content query --uri $RAW --projection _id:account_name:account_type --where \"_id=$ANN\"")"
assert_contains "Ann's number" "data1=+1 555 000 0001" "$(q "content query --uri $DATA --projection raw_contact_id:mimetype:data1 --where \"raw_contact_id=$ANN\"")"
WADE="$(people_add 'Wade Work' '+1 555 000 0022' '' qa.work@example.com com.example)"
assert_contains "people_add with an account" "account_name=qa.work@example.com, account_type=com.example" "$(q "content query --uri $RAW --projection _id:account_name:account_type --where \"_id=$WADE\"")"
people_fixtures_down
assert_eq "people_fixtures_down: Ann is gone" "" "$(raw_of 'Ann Lee')"
assert_contains "provision's Mom is untouched" "display_name=Mom" "$(q "content query --uri $RAW --projection _id:display_name --where \"display_name='Mom' AND deleted=0\"")"

# ---- the rest
P="$(shell_alarms_pending)"
assert_contains "shell_alarms_pending prints a count" "$P" "0 1 2 3 4 5 6 7 8 9 10 11 12"
B="$(shell_bytes)"
assert_eq "shell_bytes prints rx and tx" "2" "$(echo "$B" | wc -w | tr -d ' ')"
note "shell_alarms_pending=$P shell_bytes=[$B]"
assert_eq "perm_granted: READ_CALENDAR" "true" "$(perm_granted READ_CALENDAR)"
ensure_start
gdump "$ROW_DIR/start.xml"; assert_eq "gdump reads Start" "yes" "$(has_node "$ROW_DIR/start.xml" start_page)"
M="$(ring_mark)"
absent_in "absent_in on a readable slice" "no such line anywhere" "$(ring_since "$ROW_MARK")"
T0="$(device_ms)"
J="$(jump_clock $(( T0 + 3600000 )))"
assert_within "jump_clock: one hour forwards" "$(( T0 + 3600000 ))" "$J" 5000
assert_eq "jump_clock refuses a backwards jump" "2" "$(jump_clock $(( T0 - 3600000 )) >/dev/null 2>&1; echo $?)"
clock_restore
ensure_start
row_end
