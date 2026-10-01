#!/usr/bin/env bash
# Development look (not a row): the editor, a save, the card of what was saved, settings, Can edit, the filter, the SIM
# page, the groups pivot and the group editor — screenshots and dumps to read the pages off. What it makes it deletes.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin LOOK2 "development look: editor, settings pages, groups"
log "$(ensure_build)"
BEFORE="$(raw_count)"
shot() { dump_ui "$ROW_DIR/$1.xml"; screencap "$ROW_DIR/$1.png"; }

open_people -a android.intent.action.VIEW --es page new_contact
shot editor_new
assert_eq "people_page:editor" "yes" "$(has_node "$ROW_DIR/editor_new.xml" people_page:editor)"
grep -o 'resource-id="people_[^"]*"' "$ROW_DIR/editor_new.xml" | sort | uniq -c | tee -a "$LOG"
log "account reads: $(node_text "$ROW_DIR/editor_new.xml" people_editor_account); header: $(node_text "$ROW_DIR/editor_new.xml" people_editor_header)"
tap_node "$ROW_DIR/editor_new.xml" people_field:name; sleep 1; type_text "Dan Ford"
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/e2.xml"
tap_node "$ROW_DIR/e2.xml" people_field:phone; sleep 1; type_text "5550000042"
adb shell input keyevent KEYCODE_BACK; sleep 1
shot editor_filled
tap_node "$ROW_DIR/editor_filled.xml" people_field_type:phone; sleep 2; shot type_menu
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/e3.xml"
tap_node "$ROW_DIR/e3.xml" people_editor_account; sleep 2; shot account_menu
adb shell input keyevent KEYCODE_BACK; sleep 1
MARK="$(ring_mark)"
dump_ui "$ROW_DIR/e4.xml"
tap_node "$ROW_DIR/e4.xml" people_editor_save; sleep 3
shot saved_card
log "ring after save:"; ring_since "$MARK" | grep -F '[people]' | tee -a "$LOG"
DAN="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name | grep 'display_name=Dan Ford')"
log "Dan: $DAN"
echo "$DAN" | grep -oE '_id=[0-9]+' | cut -d= -f2 >> "$(_fix_file)"
assert_contains "Dan is on the phone" "account_name=NULL, account_type=NULL" "$DAN"

tap_node "$ROW_DIR/saved_card.xml" people_card_edit; sleep 2; shot editor_edit
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/l1.xml"
tap_node "$ROW_DIR/l1.xml" people_more; sleep 2; shot more_menu
tap_node "$ROW_DIR/more_menu.xml" people_more:settings; sleep 2; shot settings
tap_node "$ROW_DIR/settings.xml" people_settings:can_edit; sleep 2; shot can_edit
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_node "$ROW_DIR/settings.xml" people_settings:filter; sleep 2; shot filter
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_node "$ROW_DIR/settings.xml" people_settings:sim; sleep 2; shot sim
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
dump_ui "$ROW_DIR/l2.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/l2.xml" people_pivot:groups; sleep 2; shot groups
log "pivot motion:"; ring_since "$MARK" | grep -F '[motion] people_pivot' | tee -a "$LOG"
tap_node "$ROW_DIR/groups.xml" people_group_new; sleep 2; shot group_editor
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
assert_eq "raw_contacts count restored" "$BEFORE" "$(raw_count)"
ensure_start
row_end
