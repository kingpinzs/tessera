#!/usr/bin/env bash
# DEV-INTENTS (development proof; Trust (c), build task 9, E18's contacts clause): the exported INSERT, INSERT_OR_EDIT
# and PICK handlers, the three App Shortcuts' pages, and the cannot-read state with its grant offered in place.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-INTENTS "INSERT, INSERT_OR_EDIT, PICK, the shortcuts, the cannot-read state"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS; adb shell pm grant app.tileshell android.permission.READ_CONTACTS
BEFORE="$(raw_count)"
D="$ROW_DIR"
named() { S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name --where "\"display_name='$1'\""; }

LOU=$(fx_raw); fx_name $LOU "Lou Local"; fx_phone $LOU "+1 555 000 0021"
WADE=$(fx_raw qa.work@example.com com.example); fx_name $WADE "Wade Work"; fx_phone $WADE "+1 555 000 0022"
TWO=$(fx_raw); fx_name $TWO "Tess Two"; fx_phone $TWO "+1 555 000 0031"; fx_phone $TWO "+1 555 000 0032" 1
L_LOU="$(lookup_of "$(contact_of $LOU)")"; L_WADE="$(lookup_of "$(contact_of $WADE)")"; L_TWO="$(lookup_of "$(contact_of $TWO)")"

# ---- INSERT: the editor prefilled on the phone, whatever account the caller named; nothing saved without the tap
adb shell am start -W -n "$PEOPLE" -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact --es name Intruder --es phone 5550666 --es account_name qa.work@example.com --es account_type com.example >/dev/null 2>&1; sleep 3
dump_ui "$D/insert.xml"; screencap "$D/insert.png"
assert_eq "INSERT opens the editor" "yes" "$(has_node "$D/insert.xml" people_page:editor)"
assert_eq "prefilled with the name" "Intruder" "$(node_text "$D/insert.xml" people_field:name)"
assert_eq "and the number" "5550666" "$(node_text "$D/insert.xml" people_field:phone)"
assert_eq "it starts on the phone, not the account the caller named" "Phone" "$(node_text "$D/insert.xml" people_editor_account)"
assert_eq "before Save nothing is in the provider" "0" "$(named Intruder | grep -c '_id=')"
tap_node "$D/insert.xml" people_editor_account; sleep 2; dump_ui "$D/insert_accounts.xml"
assert_eq "the account choice offers only the phone" "1" "$(grep -o 'resource-id="people_editor_account_row:[^"]*"' "$D/insert_accounts.xml" | wc -l)"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "Back discards it: still nothing in the provider" "0" "$(named Intruder | grep -c '_id=')"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact --es name Intruder --es phone 5550666 --es account_name qa.work@example.com --es account_type com.example >/dev/null 2>&1; sleep 3
MARK="$(ring_mark)"
dump_ui "$D/insert2.xml"; tap_node "$D/insert2.xml" people_editor_save; sleep 3
ROW="$(named Intruder)"; log "$ROW"
echo "$ROW" | grep -oE '_id=[0-9]+' | cut -d= -f2 >> "$(_fix_file)"
assert_contains "saved by the user's tap, it lands on the phone (NULL account)" "account_name=NULL, account_type=NULL" "$ROW"
assert_absent "never in the account the intent named" "qa.work@example.com" "$ROW"
assert_absent "the caller's text is not in the ring" "Intruder" "$(ring_since "$MARK")"

# ---- INSERT_OR_EDIT: choose "new contact" or an existing editable contact; a read-only one is refused
adb shell am start -W -n "$PEOPLE" -a android.intent.action.INSERT_OR_EDIT -t vnd.android.cursor.item/contact --es phone 5550777 >/dev/null 2>&1; sleep 3
dump_ui "$D/ioe.xml"; screencap "$D/ioe.png"
assert_eq "INSERT_OR_EDIT opens the list" "yes" "$(has_node "$D/ioe.xml" people_page:list)"
assert_eq "with a new-contact row first" "yes" "$(has_node "$D/ioe.xml" people_insert_new)"
MARK="$(ring_mark)"
scroll_to_node "$D/ioe_w.xml" "people_row:$L_WADE" 4 >/dev/null 2>&1
tap_node "$D/ioe_w.xml" "people_row:$L_WADE"; sleep 3
dump_ui "$D/ioe_wade.xml"
assert_eq "a read-only contact does not open the editor" "no" "$(has_node "$D/ioe_wade.xml" people_page:editor)"
assert_contains "the page says why (people_notice)" "allow that account in Can edit" "$(node_text "$D/ioe_wade.xml" people_notice)"
assert_contains "and logs the refusal" "[people] edit $L_WADE: refused (account not allowed)" "$(ring_since "$MARK")"
adb shell input swipe 540 900 540 1900 200; sleep 1
scroll_to_node "$D/ioe_l.xml" "people_row:$L_LOU" 4 >/dev/null 2>&1
tap_node "$D/ioe_l.xml" "people_row:$L_LOU"; sleep 3
dump_ui "$D/ioe_lou.xml"; screencap "$D/ioe_lou.png"
assert_eq "an editable contact opens the editor" "yes" "$(has_node "$D/ioe_lou.xml" people_page:editor)"
assert_eq "with its own number kept" "+1 555 000 0021" "$(node_text "$D/ioe_lou.xml" people_field:phone)"
assert_eq "and the intent's number added as a new field" "5550777" "$(node_text "$D/ioe_lou.xml" people_field:phone:1)"
assert_absent "before Save the provider does not hold it" "5550777" "$(S content query --uri content://com.android.contacts/data --projection data1 --where "raw_contact_id=$LOU")"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- PICK: the list in pick mode; a tap ends it with the one URI; Back cancels
MARK="$(ring_mark)"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.PICK -t vnd.android.cursor.dir/contact >/dev/null 2>&1; sleep 3
dump_ui "$D/pick.xml"; screencap "$D/pick.png"
assert_eq "PICK opens the list" "yes" "$(has_node "$D/pick.xml" people_page:list)"
assert_eq "in pick mode (no app bar, a chooser's header)" "CHOOSE A CONTACT" "$(node_text "$D/pick.xml" people_pick_header)"
assert_eq "no add button in pick mode" "no" "$(has_node "$D/pick.xml" people_bar:add)"
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_ne "Back cancels and leaves People" "app.tileshell/.people.PeopleActivity" "$(top_activity)"
MARK="$(ring_mark)"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.PICK -t vnd.android.cursor.dir/contact >/dev/null 2>&1; sleep 3
scroll_to_node "$D/pick2.xml" "people_row:$L_LOU" 4 >/dev/null 2>&1
tap_node "$D/pick2.xml" "people_row:$L_LOU"; sleep 2
assert_contains "a tap ends the pick with one contact URI, read-granted" "[people] pick: one contact URI granted (read)" "$(ring_since "$MARK")"
assert_ne "and People finished" "app.tileshell/.people.PeopleActivity" "$(top_activity)"
MARK="$(ring_mark)"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.PICK -t vnd.android.cursor.dir/phone_v2 >/dev/null 2>&1; sleep 3
scroll_to_node "$D/pickp.xml" "people_row:$L_TWO" 4 >/dev/null 2>&1
assert_eq "a phone PICK's header" "CHOOSE A PHONE NUMBER" "$(node_text "$D/pickp.xml" people_pick_header)"
tap_node "$D/pickp.xml" "people_row:$L_TWO"; sleep 3
dump_ui "$D/pick_number.xml"; screencap "$D/pick_number.png"
assert_eq "a contact with two numbers asks which" "yes" "$(has_node "$D/pick_number.xml" people_page:pick_number)"
assert_eq "both numbers are offered" "2" "$(grep -o 'resource-id="people_pick_number:[^"]*"' "$D/pick_number.xml" | wc -l)"
FIRST="$(grep -o 'resource-id="people_pick_number:[^"]*"' "$D/pick_number.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
tap_node "$D/pick_number.xml" "$FIRST"; sleep 2
assert_contains "the tap ends the pick with one phone URI, read-granted" "[people] pick: one phone URI granted (read)" "$(ring_since "$MARK")"

# ---- the App Shortcuts: declared, and each page opens
SC="$(S dumpsys shortcut | grep -A400 'Package: app.tileshell' | grep -E 'ShortcutInfo \{id=(contacts|new_contact|groups),|activity=ComponentInfo\{app.tileshell/app.tileshell.people.PeopleActivity\}' | head -12)"
log "$SC"
for id in contacts new_contact groups; do assert_contains "dumpsys shortcut lists $id" "id=$id," "$(S dumpsys shortcut | grep -A400 'Package: app.tileshell')"; done
open_people -a android.intent.action.VIEW --es page contacts; dump_ui "$D/sc_contacts.xml"
assert_contains "contacts: the list, CONTACTS on show" 'selected="true"' "$(grep -o '<node[^>]*resource-id="people_pivot:contacts"[^>]*>' "$D/sc_contacts.xml")"
open_people -a android.intent.action.VIEW --es page new_contact; dump_ui "$D/sc_new.xml"
assert_eq "new_contact: the editor" "yes" "$(has_node "$D/sc_new.xml" people_page:editor)"
adb shell input keyevent KEYCODE_BACK; sleep 2; dump_ui "$D/sc_new_back.xml"
assert_eq "Back discards it and shows the list" "yes" "$(has_node "$D/sc_new_back.xml" people_page:list)"
open_people -a android.intent.action.VIEW --es page groups; dump_ui "$D/sc_groups.xml"
assert_contains "groups: the GROUPS pivot on show" 'selected="true"' "$(grep -o '<node[^>]*resource-id="people_pivot:groups"[^>]*>' "$D/sc_groups.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- READ_CONTACTS revoked (WRITE stays held): People says so and offers the grant in place
adb shell pm revoke app.tileshell android.permission.READ_CONTACTS; sleep 2
assert_eq "READ_CONTACTS is revoked" "false" "$(perm READ_CONTACTS)"
assert_eq "WRITE_CONTACTS is still held" "true" "$(perm WRITE_CONTACTS)"
MARK="$(ring_mark)"
open_people -a android.intent.action.MAIN
dump_ui "$D/noread.xml"; screencap "$D/noread.png"
assert_eq "People says it cannot read (people_notice)" "People can't read your contacts. Allow Contacts to see them here." "$(node_text "$D/noread.xml" people_notice)"
assert_eq "and offers the grant in place" "allow access" "$(node_text "$D/noread.xml" people_notice_action)"
assert_eq "no contact row is shown" "0" "$(grep -o 'resource-id="people_row:[^"]*"' "$D/noread.xml" | wc -l)"
assert_contains "the list line says so" "[people] list: 0 contacts read=false write=true" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
tap_node "$D/noread.xml" people_notice_action; sleep 3
dump_ui "$D/grant.xml"
record "what Android showed for the grant" "$(top_activity)"
ALLOW="$(bounds "$D/grant.xml" com.android.permissioncontroller:id/permission_allow_button)"
if [ -n "$ALLOW" ]; then tap_node "$D/grant.xml" com.android.permissioncontroller:id/permission_allow_button; sleep 3; fi
assert_eq "READ_CONTACTS is granted from the notice" "true" "$(perm READ_CONTACTS)"
dump_ui "$D/granted.xml"
assert_eq "the notice is gone" "no" "$(has_node "$D/granted.xml" people_notice)"
assert_contains "and the list loads without a restart" "read=true write=true" "$(ring_since "$MARK" | grep -F '[people] list:' | tail -1)"

# ---- restore
adb shell pm grant app.tileshell android.permission.READ_CONTACTS; adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "READ_CONTACTS held at the end" "true" "$(perm READ_CONTACTS)"
assert_eq "WRITE_CONTACTS held at the end" "true" "$(perm WRITE_CONTACTS)"
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
