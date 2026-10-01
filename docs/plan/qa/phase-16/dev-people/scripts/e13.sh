#!/usr/bin/env bash
# DEV-E13 (development proof, E13's core): create, edit, photo and delete on phone-only contacts with nothing on "Can
# edit", and the cannot-save notice with WRITE_CONTACTS revoked, the grant offered in place. Restores what it changes.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E13 "create, edit, photo, delete; WRITE_CONTACTS revoked (E13's core)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS; adb shell pm grant app.tileshell android.permission.READ_CONTACTS
BEFORE="$(raw_count)"
D="$ROW_DIR"
data_of() { S content query --uri content://com.android.contacts/data --projection _id:raw_contact_id:mimetype:data1 --where "raw_contact_id=$1"; }

ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
L_ANN="$(lookup_of "$(contact_of $ANN)")"
RED="$(push_photo red)"; log "pushed $RED"

# ---- New: the account choice reads "Phone" and offers no other row while nothing is allowed
open_people -a android.intent.action.MAIN
dump_ui "$D/list.xml"; tap_node "$D/list.xml" people_bar:add; sleep 2
dump_ui "$D/new.xml"; screencap "$D/new.png"
assert_eq "New opens the editor" "yes" "$(has_node "$D/new.xml" people_page:editor)"
assert_eq "people_editor_account reads Phone" "Phone" "$(node_text "$D/new.xml" people_editor_account)"
assert_eq "the header names the phone" "NEW PHONE CONTACT" "$(node_text "$D/new.xml" people_editor_header)"
tap_node "$D/new.xml" people_editor_account; sleep 2
dump_ui "$D/account.xml"
assert_eq "the account menu offers one row" "1" "$(grep -o 'resource-id="people_editor_account_row:[^"]*"' "$D/account.xml" | wc -l)"
assert_eq "and it is the phone" "yes" "$(has_node "$D/account.xml" people_editor_account_row:phone)"
adb shell input keyevent KEYCODE_BACK; sleep 1
assert_contains "Save is off until there is something to save" 'enabled="false"' "$(grep -o '<node[^>]*resource-id="people_editor_save"[^>]*>' "$D/new.xml")"
set_field people_field:name "Dan Ford"
set_field people_field:phone "5550000042"
NDATA_BEFORE="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id --where "\"display_name='Dan Ford'\"" | grep -c '_id=')"
assert_eq "nothing is saved before Save is tapped" "0" "$NDATA_BEFORE"
MARK="$(ring_mark)"
dump_ui "$D/new2.xml"; tap_node "$D/new2.xml" people_editor_save; sleep 3
DAN_ROW="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name --where "\"display_name='Dan Ford'\"")"
log "$DAN_ROW"
DAN="$(echo "$DAN_ROW" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
[ -n "$DAN" ] && echo "$DAN" >> "$(_fix_file)"
assert_contains "Dan Ford is a new raw contact on the phone (NULL account)" "account_name=NULL, account_type=NULL" "$DAN_ROW"
DD="$(data_of "$DAN")"; log "$DD"
assert_contains "his number is a phone data row under it" "mimetype=vnd.android.cursor.item/phone_v2, data1=5550000042" "$DD"
assert_contains "the write line" "[people] write insert raw=$DAN: ok" "$(ring_since "$MARK")"
dump_ui "$D/dan_card.xml"
L_DAN="$(lookup_of "$(contact_of "$DAN")")"
assert_eq "Save opens the new contact's card" "yes" "$(has_node "$D/dan_card.xml" "people_card:$L_DAN")"

# ---- Edit Ann's number
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$D/list2.xml"; tap_node "$D/list2.xml" "people_row:$L_ANN"; sleep 2
dump_ui "$D/ann_card.xml"; tap_node "$D/ann_card.xml" people_card_edit; sleep 2
dump_ui "$D/ann_edit.xml"; screencap "$D/ann_edit.png"
assert_eq "the editor shows her number" "+1 555 000 0001" "$(node_text "$D/ann_edit.xml" people_field:phone)"
assert_eq "an existing contact's header" "EDIT PHONE CONTACT" "$(node_text "$D/ann_edit.xml" people_editor_header)"
set_field people_field:phone "5550000077"
MARK="$(ring_mark)"
dump_ui "$D/ann_edit2.xml"; tap_node "$D/ann_edit2.xml" people_editor_save; sleep 3
AD="$(data_of "$ANN")"; log "$AD"
assert_contains "her phone data row changed" "mimetype=vnd.android.cursor.item/phone_v2, data1=5550000077" "$AD"
assert_absent "the old number is gone" "data1=+1 555 000 0001" "$AD"
assert_contains "her e-mail is untouched" "data1=ann@example.com" "$AD"
assert_contains "the write line" "[people] write update raw=$ANN: ok" "$(ring_since "$MARK")"
assert_eq "still one raw contact, one contact" "$ANN" "$(S content query --uri content://com.android.contacts/raw_contacts --projection _id --where "contact_id=$(contact_of $ANN)" | grep -oE '_id=[0-9]+' | cut -d= -f2)"

# ---- Photo: Android's photo picker, stored as a photo data row
dump_ui "$D/ann_card2.xml"; tap_node "$D/ann_card2.xml" people_card_edit; sleep 2
dump_ui "$D/ann_edit3.xml"; tap_node "$D/ann_edit3.xml" people_editor_photo; sleep 3
PICKER="$(top_activity)"; log "after the photo tap the resumed activity is: $PICKER"
assert_contains "Android's photo picker opened" "photopicker" "$(echo "$PICKER" | tr 'A-Z' 'a-z')"
pick_photo "254,0,0"
assert_eq "back in People's editor" "app.tileshell/.people.PeopleActivity" "$(top_activity)"
dump_ui "$D/ann_edit4.xml"; screencap "$D/ann_edit4.png"
PB="$(bounds "$D/ann_edit4.xml" people_editor_photo)"
assert_color "the editor shows the picked photo" "254,0,0" "$(px "$D/ann_edit4.png" $(( ($(bfield "$PB" 1) + $(bfield "$PB" 3)) / 2 )) $(( ($(bfield "$PB" 2) + $(bfield "$PB" 4)) / 2 )))" 8
assert_absent "no photo row before Save" "vnd.android.cursor.item/photo" "$(data_of "$ANN")"
MARK="$(ring_mark)"
tap_node "$D/ann_edit4.xml" people_editor_save; sleep 4
AD="$(S content query --uri content://com.android.contacts/data --projection _id:raw_contact_id:mimetype --where "raw_contact_id=$ANN")"; log "$AD"
assert_contains "a photo data row exists for Ann" "mimetype=vnd.android.cursor.item/photo" "$AD"
assert_contains "the write line" "[people] write update raw=$ANN: ok" "$(ring_since "$MARK")"
sleep 1; dump_ui "$D/ann_card3.xml"; screencap "$D/ann_card3.png"
CB="$(bounds "$D/ann_card3.xml" people_card_photo)"
assert_color "people_card_photo's centre pixel is the fixture's colour" "254,0,0" "$(px "$D/ann_card3.png" $(( ($(bfield "$CB" 1) + $(bfield "$CB" 3)) / 2 )) $(( ($(bfield "$CB" 2) + $(bfield "$CB" 4)) / 2 )))" 8
assert_within "people_card_photo is 124 epx" "124" "$(epx $(( $(bfield "$CB" 3) - $(bfield "$CB" 1) )))" 1

# ---- Delete Dan
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$D/list3.xml"; tap_node "$D/list3.xml" "people_row:$L_DAN"; sleep 2
dump_ui "$D/dan_card2.xml"; tap_node "$D/dan_card2.xml" people_card_delete; sleep 2
dump_ui "$D/dan_confirm.xml"
assert_eq "Delete asks once" "yes" "$(has_node "$D/dan_confirm.xml" people_delete_dialog)"
MARK="$(ring_mark)"
tap_node "$D/dan_confirm.xml" people_delete_confirm; sleep 3
assert_eq "Dan's contact row is gone" "0" "$(S content query --uri content://com.android.contacts/contacts --projection _id --where "\"display_name='Dan Ford'\"" | grep -c '_id=')"
assert_eq "and his raw contact" "0" "$(S content query --uri content://com.android.contacts/raw_contacts --projection _id --where "_id=$DAN" | grep -c '_id=')"
assert_contains "the write line" "[people] write delete raw=$DAN: ok" "$(ring_since "$MARK")"
dump_ui "$D/list4.xml"
assert_eq "the list is back, without Dan" "no" "$(has_node "$D/list4.xml" "people_row:$L_DAN")"

# ---- WRITE_CONTACTS revoked: the editor says it cannot save and offers the grant in place
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS   # ends the shell's process
sleep 2
assert_eq "WRITE_CONTACTS is revoked" "false" "$(perm WRITE_CONTACTS)"
assert_eq "READ_CONTACTS is still held" "true" "$(perm READ_CONTACTS)"
open_people -a android.intent.action.MAIN
dump_ui "$D/r_list.xml"; tap_node "$D/r_list.xml" "people_row:$L_ANN"; sleep 2
dump_ui "$D/r_card.xml"; tap_node "$D/r_card.xml" people_card_edit; sleep 2
set_field people_field:name "Ann Leigh"
MARK="$(ring_mark)"
dump_ui "$D/r_edit.xml"; tap_node "$D/r_edit.xml" people_editor_save; sleep 3
dump_ui "$D/r_notice.xml"; screencap "$D/r_notice.png"
log "notice: $(node_text "$D/r_notice.xml" people_notice) / $(node_text "$D/r_notice.xml" people_notice_action)"
assert_eq "the editor says it cannot save (people_notice)" "People can't save this: it isn't allowed to change contacts." "$(node_text "$D/r_notice.xml" people_notice)"
assert_eq "and offers the grant in place" "allow access" "$(node_text "$D/r_notice.xml" people_notice_action)"
assert_eq "the editor is still open with the edit" "Ann Leigh" "$(node_text "$D/r_notice.xml" people_field:name)"
assert_contains "the write line says why" "[people] write update raw=$ANN: failed WRITE_CONTACTS not held" "$(ring_since "$MARK")"
assert_contains "nothing was written" "data1=Ann Lee" "$(data_of "$ANN")"
tap_node "$D/r_notice.xml" people_notice_action; sleep 3
dump_ui "$D/r_dialog.xml"
# READ_CONTACTS is held and WRITE is in the same permission group, so Android may grant it without asking (run 1 did);
# when it does ask, its Allow is tapped. Which happened is recorded, not graded.
record "what Android showed for the grant" "$(top_activity)"
ALLOW="$(bounds "$D/r_dialog.xml" com.android.permissioncontroller:id/permission_allow_button)"
if [ -n "$ALLOW" ]; then tap_node "$D/r_dialog.xml" com.android.permissioncontroller:id/permission_allow_button; sleep 3; fi
assert_eq "WRITE_CONTACTS is granted from the notice" "true" "$(perm WRITE_CONTACTS)"
dump_ui "$D/r_after.xml"
assert_eq "the notice is gone once granted" "no" "$(has_node "$D/r_after.xml" people_notice)"
MARK="$(ring_mark)"
tap_node "$D/r_after.xml" people_editor_save; sleep 3
assert_contains "Save now writes" "[people] write update raw=$ANN: ok" "$(ring_since "$MARK")"
assert_contains "the name changed" "data1=Ann Leigh" "$(data_of "$ANN")"

# ---- restore
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "WRITE_CONTACTS held at the end" "true" "$(perm WRITE_CONTACTS)"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
remove_photos
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
