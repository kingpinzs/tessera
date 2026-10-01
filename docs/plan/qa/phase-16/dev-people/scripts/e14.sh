#!/usr/bin/env bash
# DEV-E14 (development proof, E14): Link and Unlink, read back from aggregation_exceptions.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E14 "Link and Unlink (E14)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
BEFORE="$(raw_count)"
D="$ROW_DIR"
exceptions() { S content query --uri content://com.android.contacts/aggregation_exceptions --projection type:raw_contact_id1:raw_contact_id2; }
contacts_of() { S content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id --where "\"display_name='Sam Reed'\""; }

# Both phone-only, one account for both: the non-merge does not rest on the fixtures' accounts (V15).
S1=$(fx_raw); fx_name $S1 "Sam Reed"; fx_phone $S1 "+1 555 000 0011"
S2=$(fx_raw); fx_name $S2 "Sam Reed"; fx_email $S2 "sam@example.com"
sleep 2
ROWS="$(contacts_of)"; log "$ROWS"
assert_eq "two raw contacts named Sam Reed" "2" "$(echo "$ROWS" | grep -c '_id=')"
assert_eq "two different contacts before the link" "2" "$(echo "$ROWS" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
C1="$(contact_of $S1)"; C2="$(contact_of $S2)"; L1="$(lookup_of "$C1")"; L2="$(lookup_of "$C2")"
log "raw $S1 -> contact $C1 ($L1); raw $S2 -> contact $C2 ($L2)"

# ---- Link from the first card picks the second
adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$C1" >/dev/null 2>&1; sleep 2
dump_ui "$D/card1.xml"
assert_eq "the first Sam Reed's card" "yes" "$(has_node "$D/card1.xml" "people_card:$L1")"
tap_node "$D/card1.xml" people_card_link; sleep 2
dump_ui "$D/link.xml"; screencap "$D/link.png"
assert_eq "the linked-profiles page" "yes" "$(has_node "$D/link.xml" people_page:link)"
assert_eq "it lists the contact's one raw contact" "yes" "$(has_node "$D/link.xml" "people_link_row:$S1")"
assert_eq "a contact with one raw contact offers no Unlink" "0" "$(grep -o 'resource-id="people_link_unlink:[^"]*"' "$D/link.xml" | wc -l)"
tap_node "$D/link.xml" people_link_add; sleep 2
dump_ui "$D/picker.xml"; screencap "$D/picker.png"
assert_eq "the picker: select a contact to link" "yes" "$(has_node "$D/picker.xml" people_page:link_picker)"
assert_eq "the picker does not offer the contact itself" "no" "$(has_node "$D/picker.xml" "people_row:$L1")"
MARK="$(ring_mark)"
tap_node "$D/picker.xml" "people_row:$L2"; sleep 3
EX="$(exceptions)"; log "$EX"
LO=$(( S1 < S2 ? S1 : S2 )); HI=$(( S1 < S2 ? S2 : S1 ))
assert_contains "aggregation_exceptions: type 1 (KEEP_TOGETHER) for the pair" "type=1, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
assert_contains "the link line" "[people] link $S1+$S2: ok" "$(ring_since "$MARK")"
ROWS="$(contacts_of)"; log "$ROWS"
assert_eq "one contact behind both raw contacts" "1" "$(echo "$ROWS" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
dump_ui "$D/link2.xml"
assert_eq "back on the linked-profiles page with both" "yes" "$([ "$(has_node "$D/link2.xml" "people_link_row:$S1")" = yes ] && [ "$(has_node "$D/link2.xml" "people_link_row:$S2")" = yes ] && echo yes || echo no)"
adb shell input keyevent KEYCODE_BACK; sleep 2
dump_ui "$D/joined.xml"; screencap "$D/joined.png"
assert_eq "the joined card carries the number" "yes" "$(has_node "$D/joined.xml" people_card_action:call:0)"
assert_eq "and the e-mail" "yes" "$(has_node "$D/joined.xml" people_card_action:mail:0)"
adb shell input keyevent KEYCODE_BACK; sleep 1
open_people -a android.intent.action.MAIN
dump_ui "$D/list.xml"
assert_eq "the list shows one Sam Reed" "1" "$(grep -c 'text="Sam Reed"' "$D/list.xml")"

# ---- Unlink
JOINED="$(contact_of $S1)"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$JOINED" >/dev/null 2>&1; sleep 2
dump_ui "$D/card2.xml"; tap_node "$D/card2.xml" people_card_link; sleep 2
dump_ui "$D/link3.xml"
MARK="$(ring_mark)"
tap_node "$D/link3.xml" "people_link_unlink:$S2"; sleep 3
EX="$(exceptions)"; log "$EX"
assert_contains "aggregation_exceptions: type 2 (KEEP_SEPARATE) for the pair" "type=2, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
assert_contains "the unlink line" "[people] unlink $S2+$S1: ok" "$(ring_since "$MARK")"
ROWS="$(contacts_of)"; log "$ROWS"
assert_eq "two contacts again" "2" "$(echo "$ROWS" | grep -oE 'contact_id=[0-9]+' | sort -u | wc -l)"
open_people -a android.intent.action.MAIN
dump_ui "$D/list2.xml"
assert_eq "the list shows two Sam Reed rows again" "2" "$(grep -c 'text="Sam Reed"' "$D/list2.xml")"

# ---- restore: deleting the raw contacts removes their exception rows
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
EX="$(exceptions)"; log "after the restore: $EX"
assert_absent "aggregation_exceptions lists neither id (first)" "raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
