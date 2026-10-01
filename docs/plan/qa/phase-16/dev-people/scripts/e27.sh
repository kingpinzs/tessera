#!/usr/bin/env bash
# DEV-E27 (development proof, E27): a group created, given members, texted, renamed and deleted; and a create refused
# with WRITE_CONTACTS revoked. Restores the grant and deletes what it made.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E27 "Groups: create, members, text, rename, delete (E27)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
BEFORE="$(raw_count)"
D="$ROW_DIR"
groups() { S content query --uri content://com.android.contacts/groups --projection _id:title:account_type:account_name:deleted; }
members() { S content query --uri content://com.android.contacts/data --projection raw_contact_id:mimetype:data1 --where "\"mimetype='vnd.android.cursor.item/group_membership' AND data1=$1\""; }
GROUPS_BEFORE="$(groups | grep -c '_id=')"

ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"
BOB=$(fx_raw); fx_name $BOB "Bob Stone"; fx_phone $BOB "+1 555 000 0002"
L_ANN="$(lookup_of "$(contact_of $ANN)")"; L_BOB="$(lookup_of "$(contact_of $BOB)")"

# ---- the GROUPS pivot, a new group
open_people -a android.intent.action.VIEW --es page groups
dump_ui "$D/groups.xml"; screencap "$D/groups.png"
assert_contains "the GROUPS pivot is on show" 'selected="true"' "$(grep -o '<node[^>]*resource-id="people_pivot:groups"[^>]*>' "$D/groups.xml")"
tap_node "$D/groups.xml" people_group_new; sleep 2
dump_ui "$D/new.xml"; screencap "$D/new.png"
assert_eq "the group editor" "yes" "$(has_node "$D/new.xml" people_page:group_editor)"
assert_eq "a new group is saved to the phone unless an allowed account is chosen" "Phone" "$(node_text "$D/new.xml" people_editor_account)"
set_field people_group_name "Family"
MARK="$(ring_mark)"
dump_ui "$D/new2.xml"; tap_node "$D/new2.xml" people_group_save; sleep 3
G="$(groups)"; log "$G"
GID="$(echo "$G" | grep 'title=Family' | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
assert_contains "the provider lists Family with a NULL account (the phone)" "title=Family, account_type=NULL, account_name=NULL" "$G"
assert_contains "the create line" "[people] group create $GID: ok" "$(ring_since "$MARK")"
dump_ui "$D/group.xml"; screencap "$D/group.png"
assert_eq "Save opens the group's page" "yes" "$(has_node "$D/group.xml" "people_group:$GID")"

# ---- members
tap_node "$D/group.xml" people_group_add_member; sleep 2
dump_ui "$D/picker.xml"; screencap "$D/picker.png"
assert_eq "the member picker" "yes" "$(has_node "$D/picker.xml" people_page:member_picker)"
tap_node "$D/picker.xml" "people_row:$L_ANN"; sleep 2
dump_ui "$D/picker2.xml"; tap_node "$D/picker2.xml" "people_row:$L_BOB"; sleep 2
dump_ui "$D/picker3.xml"
assert_contains "Ann is ticked" 'checked="true"' "$(grep -o "<node[^>]*resource-id=\"people_member:$L_ANN\"[^>]*>" "$D/picker3.xml")"
assert_contains "Bob is ticked" 'checked="true"' "$(grep -o "<node[^>]*resource-id=\"people_member:$L_BOB\"[^>]*>" "$D/picker3.xml")"
M="$(members "$GID")"; log "$M"
assert_contains "Ann's group_membership data row points at the group" "raw_contact_id=$ANN, mimetype=vnd.android.cursor.item/group_membership, data1=$GID" "$M"
assert_contains "Bob's group_membership data row points at the group" "raw_contact_id=$BOB, mimetype=vnd.android.cursor.item/group_membership, data1=$GID" "$M"
adb shell input keyevent KEYCODE_BACK; sleep 2
dump_ui "$D/group2.xml"; screencap "$D/group2.png"
assert_eq "the group's page lists Ann" "yes" "$(has_node "$D/group2.xml" "people_row:$L_ANN")"
assert_eq "the group's page lists Bob" "yes" "$(has_node "$D/group2.xml" "people_row:$L_BOB")"

# ---- Text the group: smsto: both members' mobile numbers, to the SMS role holder
HOLDER="$(S cmd role get-role-holders android.app.role.SMS)"
MARK="$(ring_mark)"
tap_node "$D/group2.xml" people_group_action:text; sleep 4
SLICE="$(ring_since "$MARK")"; log "$(echo "$SLICE" | grep -F '[people] action')"
assert_contains "the text line holds both numbers" "smsto:+15550000001;+15550000002" "$SLICE"
assert_contains "it went to the SMS role holder" "[people] action text -> $HOLDER" "$SLICE"
assert_contains "the SMS role holder is on top" "$HOLDER/" "$(top_activity)"
INTENT="$(S dumpsys activity activities | grep -m1 'smsto:' | head -c 200)"; record "the compose intent as Android holds it" "$INTENT"
adb shell am force-stop "$HOLDER"; sleep 1
open_people -a android.intent.action.VIEW --es page groups
dump_ui "$D/groups2.xml"
assert_eq "the pivot lists the group" "yes" "$(has_node "$D/groups2.xml" "people_group:$GID")"
assert_eq "with its member count" "2 members" "$(node_text "$D/groups2.xml" "people_group_count:$GID")"
tap_node "$D/groups2.xml" "people_group:$GID"; sleep 2

# ---- rename
dump_ui "$D/group3.xml"; tap_node "$D/group3.xml" people_group_action:rename; sleep 2
set_field people_group_name "Home"
MARK="$(ring_mark)"
dump_ui "$D/rename.xml"; tap_node "$D/rename.xml" people_group_save; sleep 3
G="$(groups)"; log "$G"
assert_contains "the provider's title reads Home" "_id=$GID, title=Home" "$G"
assert_contains "the rename line" "[people] group rename $GID: ok" "$(ring_since "$MARK")"

# ---- delete
dump_ui "$D/group4.xml"; tap_node "$D/group4.xml" people_group_action:delete; sleep 2
dump_ui "$D/confirm.xml"
assert_eq "Delete asks once" "yes" "$(has_node "$D/confirm.xml" people_group_delete_dialog)"
MARK="$(ring_mark)"
tap_node "$D/confirm.xml" people_group_delete_confirm; sleep 3
G="$(groups)"; log "after the delete: $G"
assert_eq "the group row is gone (or marked deleted)" "yes" "$(echo "$G" | grep "_id=$GID," | grep -vq 'deleted=1' && echo no || echo yes)"
assert_contains "the delete line" "[people] group delete $GID: ok" "$(ring_since "$MARK")"
assert_eq "both contacts are still present" "2" "$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:deleted --where "_id IN ($ANN,$BOB) AND deleted=0" | grep -c '_id=')"
assert_eq "their membership rows went with the group" "0" "$(members "$GID" | grep -c 'raw_contact_id=')"

# ---- WRITE_CONTACTS revoked: a create is refused with the editor's notice
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS; sleep 2
open_people -a android.intent.action.VIEW --es page groups
dump_ui "$D/r_groups.xml"; tap_node "$D/r_groups.xml" people_group_new; sleep 2
set_field people_group_name "Denied"
MARK="$(ring_mark)"
dump_ui "$D/r_new.xml"; tap_node "$D/r_new.xml" people_group_save; sleep 3
dump_ui "$D/r_notice.xml"; screencap "$D/r_notice.png"
assert_eq "the create is refused with the notice" "People can't save this: it isn't allowed to change contacts." "$(node_text "$D/r_notice.xml" people_notice)"
assert_contains "the create line: failed" "[people] group create new: failed WRITE_CONTACTS not held" "$(ring_since "$MARK")"
assert_eq "no group was made" "0" "$(groups | grep -c 'title=Denied')"

# ---- restore
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "WRITE_CONTACTS held at the end" "true" "$(perm WRITE_CONTACTS)"
adb shell "content delete --uri 'content://com.android.contacts/groups/$GID?caller_is_syncadapter=true'" >/dev/null 2>&1
people_fixtures_down
assert_eq "the groups count equals the count before the row" "$GROUPS_BEFORE" "$(groups | grep -c '_id=')"
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
