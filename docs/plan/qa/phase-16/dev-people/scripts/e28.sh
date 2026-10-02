#!/usr/bin/env bash
# DEV-E28 (development proof, E28's core): People's write guard and "Can edit" on a com.example account fixture — the
# read-only card, the refused EDIT intent, a new contact on the phone, the mixed contact, an account ticked and
# unticked — with the work fixture's rows read from the provider before and after every step.
# The gate's row starts from `pm clear`; a builder's session may not clear the shared shell, so this row asserts
# instead that nothing is on "Can edit" when it starts, and leaves it so.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E28 "the write guard and Can edit (E28's core)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
BEFORE="$(raw_count)"
D="$ROW_DIR"
WORK=qa.work@example.com; PERSONAL=qa.personal@example.com; TYPE=com.example
edit_json() { adb shell run-as app.tileshell cat files/people_edit.json 2>/dev/null | tr -d '\r'; }
allowed() { edit_json | python3 -c '
import json, sys
try: d = json.load(sys.stdin)
except Exception: d = {}
print(",".join(sorted(a["type"] + ":" + a["name"] for a in d.get("allowed", []))))'; }
rows_of() { # raw id -> its raw_contacts row and its data rows, as the provider holds them
  S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:deleted --where "_id=$1"
  S content query --uri content://com.android.contacts/data --projection _id:mimetype:data1 --where "raw_contact_id=$1"
}
card_of() { adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$(contact_of "$1")" >/dev/null 2>&1; sleep 2; dump_ui "$2"; }
exceptions() { S content query --uri content://com.android.contacts/aggregation_exceptions --projection type:raw_contact_id1:raw_contact_id2; }
id_by_name() { S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name --where "\"display_name='$1'\""; }

assert_eq "nothing is on Can edit when the row starts" "" "$(allowed)"

LOU=$(fx_raw); fx_name $LOU "Lou Local"; fx_phone $LOU "+1 555 000 0021"
WADE=$(fx_raw "$WORK" "$TYPE"); fx_name $WADE "Wade Work"; fx_phone $WADE "+1 555 000 0022"
PIA=$(fx_raw "$PERSONAL" "$TYPE"); fx_name $PIA "Pia Personal"; fx_phone $PIA "+1 555 000 0023"
sleep 2
rows_of "$WADE" > "$D/wade-before.txt"; cat "$D/wade-before.txt" >> "$LOG"
wade_unchanged() { # label
  rows_of "$WADE" > "$D/.wade-now.txt"
  assert_eq "Wade's rows are unchanged and still present ($1)" "$(cat "$D/wade-before.txt")" "$(cat "$D/.wade-now.txt")"
}
assert_contains "Wade's raw contact is in the work account" "account_name=$WORK, account_type=$TYPE, deleted=0" "$(cat "$D/wade-before.txt")"
L_LOU="$(lookup_of "$(contact_of $LOU)")"; L_WADE="$(lookup_of "$(contact_of $WADE)")"; L_PIA="$(lookup_of "$(contact_of $PIA)")"
log "lou=$LOU ($L_LOU) wade=$WADE ($L_WADE) pia=$PIA ($L_PIA)"

# ---- the list: People's settings -> Can edit
open_people -a android.intent.action.MAIN
dump_ui "$D/l.xml"; tap_node "$D/l.xml" people_more; sleep 2
dump_ui "$D/m.xml"; tap_node "$D/m.xml" people_more:settings; sleep 2
dump_ui "$D/settings.xml"; tap_node "$D/settings.xml" people_settings:can_edit; sleep 2
dump_ui "$D/can_edit.xml"; screencap "$D/can_edit.png"
assert_contains "people_page:can_edit is the page on show" 'selected="true"' "$(grep -o '<node[^>]*resource-id="people_page:can_edit"[^>]*>' "$D/can_edit.xml")"
node() { grep -o "<node[^>]*resource-id=\"$2\"[^>]*>" "$1"; }
assert_contains "the work account is listed, unticked" 'checked="false"' "$(node "$D/can_edit.xml" "people_can_edit:$TYPE:$WORK")"
assert_contains "the personal account is listed, unticked" 'checked="false"' "$(node "$D/can_edit.xml" "people_can_edit:$TYPE:$PERSONAL")"
assert_contains "provision's Mom gives a third row, unticked" 'checked="false"' "$(node "$D/can_edit.xml" "people_can_edit:qa:qa")"
assert_eq "exactly those three rows: the phone has none" "3" "$(grep -o 'resource-id="people_can_edit:[^"]*"' "$D/can_edit.xml" | wc -l)"
assert_eq "people_edit.json holds no allowed account" "" "$(allowed)"

# ---- the read-only card
card_of "$WADE" "$D/wade.xml"; screencap "$D/wade.png"
assert_eq "Wade's card (asserted first)" "yes" "$(has_node "$D/wade.xml" "people_card:$L_WADE")"
assert_eq "no people_card_edit" "no" "$(has_node "$D/wade.xml" people_card_edit)"
assert_eq "no people_card_delete" "no" "$(has_node "$D/wade.xml" people_card_delete)"
assert_eq "people_card_readonly names the account" "This contact is in $WORK. To change it, allow that account in Can edit." "$(node_text "$D/wade.xml" people_card_readonly)"
tap_node "$D/wade.xml" people_card_readonly; sleep 2
dump_ui "$D/wade_can_edit.xml"
assert_eq "its tap opens Can edit" "yes" "$(has_node "$D/wade_can_edit.xml" people_page:can_edit)"
card_of "$LOU" "$D/lou.xml"
assert_eq "Lou's card" "yes" "$(has_node "$D/lou.xml" "people_card:$L_LOU")"
assert_eq "Lou's card holds people_card_edit" "yes" "$(has_node "$D/lou.xml" people_card_edit)"
assert_eq "Lou's card holds people_card_delete" "yes" "$(has_node "$D/lou.xml" people_card_delete)"
assert_eq "and no read-only line" "no" "$(has_node "$D/lou.xml" people_card_readonly)"
wade_unchanged "after the cards"

# ---- the exported EDIT intent
MARK="$(ring_mark)"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.EDIT -d "content://com.android.contacts/contacts/lookup/$L_WADE/$(contact_of $WADE)" >/dev/null 2>&1; sleep 3
dump_ui "$D/wade_edit.xml"
assert_eq "EDIT on Wade opens his card" "yes" "$(has_node "$D/wade_edit.xml" "people_card:$L_WADE")"
assert_eq "and no editor" "no" "$(has_node "$D/wade_edit.xml" people_page:editor)"
assert_contains "the refusal is logged" "[people] edit $L_WADE: refused (account not allowed)" "$(ring_since "$MARK")"
wade_unchanged "after the EDIT intent"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.EDIT -d "content://com.android.contacts/contacts/lookup/$L_LOU/$(contact_of $LOU)" >/dev/null 2>&1; sleep 3
dump_ui "$D/lou_edit.xml"
assert_eq "the same intent on Lou opens the editor" "yes" "$(has_node "$D/lou_edit.xml" people_page:editor)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- a new contact: the phone only
open_people -a android.intent.action.VIEW --es page new_contact
dump_ui "$D/new.xml"; tap_node "$D/new.xml" people_editor_account; sleep 2
dump_ui "$D/new_accounts.xml"
assert_eq "people_editor_account offers the phone" "yes" "$(has_node "$D/new_accounts.xml" people_editor_account_row:phone)"
assert_eq "and only the phone" "1" "$(grep -o 'resource-id="people_editor_account_row:[^"]*"' "$D/new_accounts.xml" | wc -l)"
adb shell input keyevent KEYCODE_BACK; sleep 1
set_field people_field:name "Ned New"
dump_ui "$D/new2.xml"; tap_node "$D/new2.xml" people_editor_save; sleep 3
NED="$(id_by_name 'Ned New')"; log "$NED"
echo "$NED" | grep -oE '_id=[0-9]+' | cut -d= -f2 >> "$(_fix_file)"
assert_contains "Ned New's raw contact has a NULL account" "account_name=NULL, account_type=NULL" "$NED"
wade_unchanged "after the new contact"

# ---- the mixed contact: Link is allowed on any contact; Edit for the editable part; no Delete
card_of "$LOU" "$D/lou2.xml"; tap_node "$D/lou2.xml" people_card_link; sleep 2
dump_ui "$D/link.xml"; tap_node "$D/link.xml" people_link_add; sleep 2
dump_ui "$D/picker.xml"
MARK="$(ring_mark)"
tap_node "$D/picker.xml" "people_row:$L_WADE"; sleep 3
LO=$(( LOU < WADE ? LOU : WADE )); HI=$(( LOU < WADE ? WADE : LOU ))
assert_contains "aggregation_exceptions shows type 1 for Lou and Wade" "type=1, raw_contact_id1=$LO, raw_contact_id2=$HI" "$(exceptions)"
assert_contains "the link line" "[people] link $LOU+$WADE: ok" "$(ring_since "$MARK")"
card_of "$LOU" "$D/mixed.xml"; screencap "$D/mixed.png"
assert_eq "the joined card holds people_card_edit" "yes" "$(has_node "$D/mixed.xml" people_card_edit)"
assert_eq "and NO people_card_delete" "no" "$(has_node "$D/mixed.xml" people_card_delete)"
tap_node "$D/mixed.xml" people_card_edit; sleep 2
dump_ui "$D/mixed_edit.xml"; screencap "$D/mixed_edit.png"
assert_eq "the editor opens Lou's number" "+1 555 000 0021" "$(node_text "$D/mixed_edit.xml" people_field:phone)"
scroll_to_node "$D/mixed_ro.xml" "people_editor_readonly:$TYPE:$WORK" 4 >/dev/null 2>&1
assert_eq "and shows the work part read-only" "yes" "$(has_node "$D/mixed_ro.xml" "people_editor_readonly:$TYPE:$WORK")"
adb shell input swipe 540 900 540 1900 200; sleep 1
set_field people_field:phone "5550000091"
MARK="$(ring_mark)"
dump_ui "$D/mixed_edit2.xml"; tap_node "$D/mixed_edit2.xml" people_editor_save; sleep 3
assert_contains "Lou's data row changed" "data1=5550000091" "$(rows_of "$LOU")"
assert_contains "only Lou's raw contact was written" "[people] write update raw=$LOU: ok" "$(ring_since "$MARK")"
assert_absent "nothing was written on Wade's" "raw=$WADE" "$(ring_since "$MARK" | grep -F '[people] write')"
wade_unchanged "after the mixed contact's edit"
card_of "$LOU" "$D/mixed2.xml"; tap_node "$D/mixed2.xml" people_card_link; sleep 2
dump_ui "$D/link2.xml"; tap_node "$D/link2.xml" "people_link_unlink:$WADE"; sleep 3
assert_contains "Unlink: type 2" "type=2, raw_contact_id1=$LO, raw_contact_id2=$HI" "$(exceptions)"
wade_unchanged "after Link and Unlink"

# ---- allowing an account
open_people -a android.intent.action.MAIN
dump_ui "$D/l2.xml"; tap_node "$D/l2.xml" people_more; sleep 2
dump_ui "$D/m2.xml"; tap_node "$D/m2.xml" people_more:settings; sleep 2
dump_ui "$D/s2.xml"; tap_node "$D/s2.xml" people_settings:can_edit; sleep 2
dump_ui "$D/ce2.xml"; tap_node "$D/ce2.xml" "people_can_edit:$TYPE:$PERSONAL"; sleep 2
dump_ui "$D/ce3.xml"; screencap "$D/ce3.png"
assert_contains "the personal account is ticked" 'checked="true"' "$(node "$D/ce3.xml" "people_can_edit:$TYPE:$PERSONAL")"
assert_contains "the work account is still unticked" 'checked="false"' "$(node "$D/ce3.xml" "people_can_edit:$TYPE:$WORK")"
log "people_edit.json: $(edit_json)"
assert_eq "people_edit.json lists that account and only it" "$TYPE:$PERSONAL" "$(allowed)"
card_of "$PIA" "$D/pia.xml"
assert_eq "Pia's card now holds people_card_edit" "yes" "$(has_node "$D/pia.xml" people_card_edit)"
assert_eq "and people_card_delete" "yes" "$(has_node "$D/pia.xml" people_card_delete)"
tap_node "$D/pia.xml" people_card_edit; sleep 2
dump_ui "$D/pia_edit.xml"
assert_eq "her editor's header names her account" "EDIT ${PERSONAL^^} CONTACT" "$(node_text "$D/pia_edit.xml" people_editor_header)"
set_field people_field:phone "5550000093"
MARK="$(ring_mark)"
dump_ui "$D/pia_edit2.xml"; tap_node "$D/pia_edit2.xml" people_editor_save; sleep 3
assert_contains "her data row changes" "data1=5550000093" "$(rows_of "$PIA")"
assert_contains "the write line" "[people] write update raw=$PIA: ok" "$(ring_since "$MARK")"
open_people -a android.intent.action.VIEW --es page new_contact
dump_ui "$D/new3.xml"; tap_node "$D/new3.xml" people_editor_account; sleep 2
dump_ui "$D/new3_accounts.xml"; screencap "$D/new3_accounts.png"
assert_eq "the account choice now offers the phone" "yes" "$(has_node "$D/new3_accounts.xml" people_editor_account_row:phone)"
assert_eq "and the allowed account" "yes" "$(has_node "$D/new3_accounts.xml" "people_editor_account_row:$TYPE:$PERSONAL")"
assert_eq "and nothing else (not the work account)" "2" "$(grep -o 'resource-id="people_editor_account_row:[^"]*"' "$D/new3_accounts.xml" | wc -l)"
tap_node "$D/new3_accounts.xml" "people_editor_account_row:$TYPE:$PERSONAL"; sleep 2
set_field people_field:name "Pat Personal"
dump_ui "$D/new4.xml"
assert_eq "the header follows the choice" "NEW ${PERSONAL^^} CONTACT" "$(node_text "$D/new4.xml" people_editor_header)"
tap_node "$D/new4.xml" people_editor_save; sleep 3
PAT="$(id_by_name 'Pat Personal')"; log "$PAT"
PAT_ID="$(echo "$PAT" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"; [ -n "$PAT_ID" ] && echo "$PAT_ID" >> "$(_fix_file)"
assert_contains "a contact saved with the second lands in that account" "account_name=$PERSONAL, account_type=$TYPE" "$PAT"
card_of "$WADE" "$D/wade2.xml"
assert_eq "Wade's card is still read-only: no edit" "no" "$(has_node "$D/wade2.xml" people_card_edit)"
assert_eq "and the read-only line" "yes" "$(has_node "$D/wade2.xml" people_card_readonly)"
wade_unchanged "with the personal account allowed"
card_of "$PIA" "$D/pia2.xml"; tap_node "$D/pia2.xml" people_card_delete; sleep 2
dump_ui "$D/pia_confirm.xml"; MARK="$(ring_mark)"
tap_node "$D/pia_confirm.xml" people_delete_confirm; sleep 3
assert_eq "Pia's contact row is gone" "0" "$(S content query --uri content://com.android.contacts/contacts --projection _id --where "\"display_name='Pia Personal'\"" | grep -c '_id=')"
assert_contains "the delete line" "[people] write delete raw=$PIA: ok" "$(ring_since "$MARK")"

# ---- un-tick: a remaining contact of that account is read-only again
open_people -a android.intent.action.MAIN
dump_ui "$D/l3.xml"; tap_node "$D/l3.xml" people_more; sleep 2
dump_ui "$D/m3.xml"; tap_node "$D/m3.xml" people_more:settings; sleep 2
dump_ui "$D/s3.xml"; tap_node "$D/s3.xml" people_settings:can_edit; sleep 2
dump_ui "$D/ce4.xml"; tap_node "$D/ce4.xml" "people_can_edit:$TYPE:$PERSONAL"; sleep 2
assert_eq "people_edit.json holds no allowed account again" "" "$(allowed)"
card_of "$PAT_ID" "$D/pat.xml"
assert_eq "Pat's card (the remaining contact of that account)" "yes" "$(has_node "$D/pat.xml" people_card_readonly)"
assert_eq "is read-only again: no edit" "no" "$(has_node "$D/pat.xml" people_card_edit)"
wade_unchanged "at the end"

# ---- the guard's JVM test, as the last whole-suite run wrote it
XML="$REPO/app/build/test-results/testDebugUnitTest/TEST-app.tileshell.people.PeopleWriteGuardTest.xml"
log "PeopleWriteGuardTest: $(grep -o '<testsuite[^>]*>' "$XML" | sed -E 's/.*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*/tests=\1 failures=\2 errors=\3/')"
grep -o 'testcase name="[^"]*"' "$XML" | sed 's/testcase name=/  /' >> "$LOG"
assert_contains "the guard's JVM test ran with no failure" 'failures="0" errors="0"' "$(grep -o '<testsuite[^>]*>' "$XML")"

# ---- restore: every fixture and what the row saved, by id; nothing allowed
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
assert_eq "the raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
MARK="$(ring_mark)"; open_people -a android.intent.action.MAIN; sleep 2
assert_eq "nothing is on Can edit at the end" "" "$(allowed)"
adb shell input keyevent KEYCODE_BACK; sleep 1
ensure_start
row_end
