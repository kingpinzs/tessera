#!/usr/bin/env bash
# Development look (not a row): install this build, put E10's fixtures in, and capture the list, the jump grid, a
# search and a card, so the pages can be read off screenshots and dumps. Fixtures are deleted by id at the end.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin LOOK1 "development look: list, jump grid, search, card"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
BEFORE="$(raw_count)"

ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
BOB=$(fx_raw); fx_name $BOB "Bob Stone"
ZOE=$(fx_raw); fx_name $ZOE "Zoë Ǻrén"
WEI=$(fx_raw); fx_name $WEI "张伟"
NUM=$(fx_raw); fx_phone $NUM "+1 555 000 0009"
C1=$(fx_raw); fx_name $C1 "Cara Diaz"; fx_phone $C1 "+1 555 000 0003"
C2=$(fx_raw); fx_name $C2 "Cara Diaz"; fx_phone $C2 "+1 555 000 0003"
log "fixture raw ids: ann=$ANN bob=$BOB zoe=$ZOE wei=$WEI num=$NUM cara=$C1,$C2"
S content query --uri content://com.android.contacts/contacts --projection _id:lookup:display_name:phonebook_label:display_name_source | tee -a "$LOG"
S content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id:account_name:account_type:display_name | tee -a "$LOG"

MARK="$(ring_mark)"
open_people -a android.intent.action.MAIN
assert_eq "People resumed" "app.tileshell/.people.PeopleActivity" "$(top_activity)"
dump_ui "$ROW_DIR/list.xml"; screencap "$ROW_DIR/list.png"
log "ring since open:"; ring_since "$MARK" | grep -F '[people]' | tee -a "$LOG"
assert_eq "people_page:list" "yes" "$(has_node "$ROW_DIR/list.xml" people_page:list)"
grep -o 'resource-id="people_[^"]*"' "$ROW_DIR/list.xml" | sort | uniq -c | tee -a "$LOG"

tap_node "$ROW_DIR/list.xml" "people_letter:A"; sleep 2
dump_ui "$ROW_DIR/grid.xml"; screencap "$ROW_DIR/grid.png"
assert_eq "jump grid open" "yes" "$(has_node "$ROW_DIR/grid.xml" people_jump_grid)"
tap_node "$ROW_DIR/grid.xml" "people_jump_cell:Z"; sleep 2
dump_ui "$ROW_DIR/after_z.xml"; screencap "$ROW_DIR/after_z.png"
assert_eq "grid closed after Z" "no" "$(has_node "$ROW_DIR/after_z.xml" people_jump_grid)"

MARK="$(ring_mark)"
tap_node "$ROW_DIR/after_z.xml" people_search; sleep 1
type_text "ann"; sleep 2
dump_ui "$ROW_DIR/search.xml"; screencap "$ROW_DIR/search.png"
log "search ring:"; ring_since "$MARK" | grep -F '[people] search' | tee -a "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 1

ANN_C=$(contact_of $ANN); ANN_L=$(lookup_of $ANN_C)
log "Ann contact=$ANN_C lookup=$ANN_L"
dump_ui "$ROW_DIR/search2.xml"
tap_node "$ROW_DIR/search2.xml" "people_row:$ANN_L"; sleep 2
dump_ui "$ROW_DIR/card.xml"; screencap "$ROW_DIR/card.png"
assert_eq "Ann's card" "yes" "$(has_node "$ROW_DIR/card.xml" "people_card:$ANN_L")"
grep -o 'resource-id="people_[^"]*"' "$ROW_DIR/card.xml" | sort | uniq -c | tee -a "$LOG"

adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
assert_eq "raw_contacts count restored" "$BEFORE" "$(raw_count)"
ensure_start
row_end
