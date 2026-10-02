#!/usr/bin/env bash
# Phase 16 E28 — People's write guard and "Can edit" (Q-16-3; H21), clause by clause from the phase doc's row E28.
#
#   start      pm clear app.tileshell → provision.sh → ensure_start (nothing allowed)
#   fixtures   Lou Local (phone-only, +1 555 000 0021), Wade Work (qa.work@example.com / com.example, +1 555 000 0022),
#              Pia Personal (qa.personal@example.com / com.example, +1 555 000 0023), inserted as provision.sh inserts Mom
#   BEFORE     each fixture's raw_contacts row (_id:account_name:account_type:deleted) and data rows (_id:mimetype:data1)
#              read from the provider into files; "unchanged" = those rows read again and EQUAL, the rows still present
#              (a vanished fixture fails). Wade's rows are re-read after EVERY step: that is the point of the row.
#   A list     People's settings → people_page:can_edit lists the two com.example accounts and qa:qa, all unticked, no
#              row for the phone; people_edit.json holds no allowed account
#   B card     Wade's row tapped → people_card:<lookup> (asserted first), no edit, no delete, people_card_readonly
#              naming the account, its tap opens Can edit; Lou's card has both actions (the positive control)
#   C intent   the exported EDIT on Wade → his card, no editor, `[people] edit <lookup>: refused (account not allowed)`;
#              the same intent on Lou → the editor
#   D new      New → the account choice offers the phone only → "Ned New" → a NULL account
#   E mixed    Link Lou with Wade (type 1) → Edit and NO Delete; Lou's number edited → Lou's row changed, Wade's not;
#              Unlink (type 2)
#   F allow    the personal account ticked → people_edit.json lists it and only it; Pia's card gains Edit and Delete; her
#              number edited (`write update raw=<id>: ok`); New offers the phone and that account, and a contact saved
#              with it lands there; Wade still read-only; Pia deleted from her card
#   G un-tick  a remaining contact of that account is read-only again
#   JVM        the guard's JVM test: the lead's result file (the QA writers never run ./gradlew; clauses-open.tsv)
#   restore    the fixtures, Ned New and the personal-account contact deleted by id; pm clear → provision.sh →
#              ensure_start (clears people_edit.json); the baseline layout
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
WORK=qa.work@example.com; PERSONAL=qa.personal@example.com; TYPE=com.example

provision() { # label
  ( bash "$P03S/provision.sh" > "$ROW_DIR/provision-$1.out" 2>&1; echo $? > "$ROW_DIR/provision-$1.rc" )
  cat "$ROW_DIR/provision-$1.rc"
}
rows_of() { # raw id -> the doc's two reads
  q "content query --uri $RAW --projection _id:account_name:account_type:deleted --where \"_id=$1\""
  q "content query --uri $DATA --projection _id:mimetype:data1 --where \"raw_contact_id=$1\""
}
STEP=0
wade_unchanged() { # label — Wade's rows read again: equal to the BEFORE file, and still present
  STEP=$((STEP + 1))
  rows_of "$WADE" > "$D/wade-after-$STEP.txt"
  assert_eq "Wade's rows unchanged and still present — $1" "$(cat "$D/wade-before.txt")" "$(cat "$D/wade-after-$STEP.txt")"
}
pia_unchanged() { rows_of "$PIA" > "$D/.pia-now.txt"; assert_eq "Pia's rows unchanged and still present — $1" "$(cat "$D/pia-before.txt")" "$(cat "$D/.pia-now.txt")"; }
can_edit_page() { # out.xml — People's settings → Can edit
  open_people_settings can_edit "$1"
}

row_begin E28 "People's write guard and \"Can edit\" (Q-16-3)"
require_build
D="$ROW_DIR"
RAW0="$(raw_count)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
note "raw contacts before the row ($RAW0): $(tr '\n' ';' < "$D/raw-before.txt")"

# ------------------------------------------------------------------------------------------------ start: wiped
log "--- pm clear → provision.sh → ensure_start (nothing allowed)"
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 1    # not Home in front while clearing (p12.sh leave_home)
adb shell pm clear app.tileshell >/dev/null
assert_eq "provision.sh rc" "0" "$(provision start)"
assert_contains "the device still holds the build under test after provision.sh" "yes" "$(apk_matches)"
ensure_start
assert_eq "nothing allowed: people_edit.json holds no allowed account (or does not exist yet)" "" "$(people_allowed)"
record "people_edit.json after the wipe" "[$(people_edit_json)] (empty = the file does not exist yet)"
assert_eq "READ_CONTACTS and WRITE_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"

# ------------------------------------------------------------------------------------------------ fixtures and BEFORE
log "--- fixtures and their BEFORE files"
: > "$ROW_DIR/people-fixtures.ids"
RAW_BEFORE="$(raw_count)"
LOU="$(people_add 'Lou Local' '+1 555 000 0021')"
WADE="$(people_add 'Wade Work' '+1 555 000 0022' '' "$WORK" "$TYPE")"
PIA="$(people_add 'Pia Personal' '+1 555 000 0023' '' "$PERSONAL" "$TYPE")"
sleep 3
for n in lou:$LOU wade:$WADE pia:$PIA; do
  rows_of "${n#*:}" > "$D/${n%%:*}-before.txt"
  { echo "BEFORE ${n%%:*} (raw ${n#*:}):"; cat "$D/${n%%:*}-before.txt"; } >> "$LOG"
done
assert_contains "BEFORE: Lou's raw contact is phone-only" "account_name=NULL, account_type=NULL, deleted=0" "$(cat "$D/lou-before.txt")"
assert_contains "BEFORE: Wade's raw contact is in $WORK / $TYPE" "account_name=$WORK, account_type=$TYPE, deleted=0" "$(cat "$D/wade-before.txt")"
assert_contains "BEFORE: Pia's raw contact is in $PERSONAL / $TYPE" "account_name=$PERSONAL, account_type=$TYPE, deleted=0" "$(cat "$D/pia-before.txt")"
assert_contains "BEFORE: Wade's mobile number is a data row" "mimetype=vnd.android.cursor.item/phone_v2, data1=+1 555 000 0022" "$(cat "$D/wade-before.txt")"
assert_contains "BEFORE: Wade's name is a data row" "mimetype=vnd.android.cursor.item/name, data1=Wade Work" "$(cat "$D/wade-before.txt")"
assert_eq "three contacts for three fixtures (none aggregated with another)" "3" "$(printf '%s\n%s\n%s\n' "$(contact_of "$LOU")" "$(contact_of "$WADE")" "$(contact_of "$PIA")" | sort -u | grep -c .)"
lk() { lookup_of "$(contact_of "$1")"; }
L_LOU="$(lk "$LOU")"; L_WADE="$(lk "$WADE")"; L_PIA="$(lk "$PIA")"
note "lou=$LOU ($L_LOU) wade=$WADE ($L_WADE) pia=$PIA ($L_PIA)"

# ------------------------------------------------------------------------------------------------ A: the list
log "--- A: People's settings → Can edit"
can_edit_page "$D/A-can-edit.xml"; screencap "$D/A-can-edit.png"
assert_contains "A: people_page:can_edit is the page on show" 'selected="true"' "$(node_tag "$D/A-can-edit.xml" people_page:can_edit)"
note "rows: $(ids_with_prefix "$D/A-can-edit.xml" people_can_edit: | grep -v '^people_can_edit_' | tr '\n' ' ')"
assert_contains "A: people_can_edit:$TYPE:$WORK is listed, unticked" 'checked="false"' "$(node_tag "$D/A-can-edit.xml" "people_can_edit:$TYPE:$WORK")"
assert_contains "A: people_can_edit:$TYPE:$PERSONAL is listed, unticked" 'checked="false"' "$(node_tag "$D/A-can-edit.xml" "people_can_edit:$TYPE:$PERSONAL")"
assert_contains "A: provision.sh's Mom gives a third row, people_can_edit:qa:qa, unticked" 'checked="false"' "$(node_tag "$D/A-can-edit.xml" "people_can_edit:qa:qa")"
assert_eq "A: exactly those three rows — the phone has no row" \
  "people_can_edit:$TYPE:$PERSONAL people_can_edit:$TYPE:$WORK people_can_edit:qa:qa" \
  "$(ids_with_prefix "$D/A-can-edit.xml" people_can_edit: | grep -v '^people_can_edit_' | sort | tr '\n' ' ' | sed 's/ $//')"
assert_eq "A: no row is ticked" "0" "$(grep -o '<node[^>]*resource-id="people_can_edit:[^>]*>' "$D/A-can-edit.xml" | grep -c 'checked="true"')"
assert_eq "A: people_edit.json holds no allowed account" "" "$(people_allowed)"
record "A: people_edit.json" "[$(people_edit_json)]"
wade_unchanged "after opening Can edit"

# ------------------------------------------------------------------------------------------------ B: the read-only card
log "--- B: the read-only card (Wade's row tapped), and Lou's as the positive control"
open_people -a android.intent.action.MAIN
tap_row_clear "people_row:$L_WADE" "$D/B-list.xml"
dump_ui "$D/B-wade.xml"; screencap "$D/B-wade.png"
assert_eq "B: Wade's row tapped → people_card:<Wade's lookup> (asserted first)" "yes" "$(has_node "$D/B-wade.xml" "people_card:$L_WADE")"
assert_eq "B: no people_card_edit" "no" "$(has_node "$D/B-wade.xml" people_card_edit)"
assert_eq "B: no people_card_delete" "no" "$(has_node "$D/B-wade.xml" people_card_delete)"
RO="$(xml_text "$D/B-wade.xml" people_card_readonly)"; log "people_card_readonly: [$RO]"
assert_eq "B: people_card_readonly is on the card" "yes" "$(has_node "$D/B-wade.xml" people_card_readonly)"
assert_contains "B: … naming $WORK" "$WORK" "$RO"
assert_eq "B: … the line as the doc words it (approximation, H21)" "This contact is in $WORK. To change it, allow that account in Can edit." "$RO"
tap_node "$D/B-wade.xml" people_card_readonly; sleep 2
dump_ui "$D/B-readonly-tap.xml"
assert_contains "B: its tap opens people_page:can_edit" 'selected="true"' "$(node_tag "$D/B-readonly-tap.xml" people_page:can_edit)"
wade_unchanged "after his card and the read-only line's tap"
open_people -a android.intent.action.MAIN
tap_row_clear "people_row:$L_LOU" "$D/B-list2.xml"
dump_ui "$D/B-lou.xml"; screencap "$D/B-lou.png"
assert_eq "B: Lou's card (people_card:<Lou's lookup>)" "yes" "$(has_node "$D/B-lou.xml" "people_card:$L_LOU")"
assert_eq "B: Lou's card holds people_card_edit" "yes" "$(has_node "$D/B-lou.xml" people_card_edit)"
assert_eq "B: Lou's card holds people_card_delete" "yes" "$(has_node "$D/B-lou.xml" people_card_delete)"
assert_eq "B: … and no read-only line" "no" "$(has_node "$D/B-lou.xml" people_card_readonly)"
wade_unchanged "after Lou's card"

# ------------------------------------------------------------------------------------------------ C: the exported EDIT
log "--- C: the exported EDIT intent"
back 1; back 1
C_MARK="$(ring_mark)"
adb shell am start -n "$PEOPLE_PKG_ACTIVITY" -a android.intent.action.EDIT -d "content://com.android.contacts/contacts/lookup/$L_WADE/$(contact_of "$WADE")" > "$D/C-am-wade.txt" 2>&1
sleep 3
dump_ui "$D/C-wade-edit.xml"; screencap "$D/C-wade-edit.png"
assert_eq "C: EDIT on Wade → people_card:<Wade's lookup>" "yes" "$(has_node "$D/C-wade-edit.xml" "people_card:$L_WADE")"
assert_eq "C: … with no people_page:editor node" "no" "$(has_node "$D/C-wade-edit.xml" people_page:editor)"
C_SLICE="$(ring_since "$C_MARK")"
log "$(printf '%s\n' "$C_SLICE" | grep -F '[people] edit' | sed 's/.*\[people\]/[people]/')"
assert_contains "C: the slice holds the refusal" "[people] edit $L_WADE: refused (account not allowed)" "$C_SLICE"
wade_unchanged "after the exported EDIT intent"
adb shell am start -n "$PEOPLE_PKG_ACTIVITY" -a android.intent.action.EDIT -d "content://com.android.contacts/contacts/lookup/$L_LOU/$(contact_of "$LOU")" > "$D/C-am-lou.txt" 2>&1
sleep 3
dump_ui "$D/C-lou-edit.xml"
assert_contains "C: the same intent on Lou opens people_page:editor" 'selected="true"' "$(node_tag "$D/C-lou-edit.xml" people_page:editor)"
assert_eq "C: … on Lou's own number" "+1 555 000 0021" "$(xml_text "$D/C-lou-edit.xml" people_field:phone)"
back 1
wade_unchanged "after the EDIT intent on Lou"

# ------------------------------------------------------------------------------------------------ D: a new contact
log "--- D: New → the phone only → Ned New"
open_people -a android.intent.action.MAIN
tapid people_bar:add 2
dump_ui "$D/D-new.xml"
assert_contains "D: New opens the editor" 'selected="true"' "$(node_tag "$D/D-new.xml" people_page:editor)"
tap_node "$D/D-new.xml" people_editor_account; sleep 2
dump_ui "$D/D-accounts.xml"; screencap "$D/D-accounts.png"
assert_eq "D: people_editor_account offers people_editor_account_row:phone" "yes" "$(has_node "$D/D-accounts.xml" people_editor_account_row:phone)"
assert_eq "D: … only" "people_editor_account_row:phone" "$(ids_with_prefix "$D/D-accounts.xml" people_editor_account_row: | tr '\n' ' ' | sed 's/ $//')"
tap_node "$D/D-accounts.xml" people_editor_account_row:phone; sleep 1
set_field people_field:name "Ned New"
dump_ui "$D/D-new2.xml"; tap_node "$D/D-new2.xml" people_editor_save; sleep 3
NED_ROW="$(q "content query --uri $RAW --projection _id:account_name:account_type:display_name:deleted --where \"display_name='Ned New'\"")"; log "$NED_ROW"
NED="$(printf '%s\n' "$NED_ROW" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
[ -n "$NED" ] && echo "$NED" >> "$ROW_DIR/people-fixtures.ids"
assert_eq "D: \"Ned New\" is saved (one raw contact)" "1" "$(printf '%s\n' "$NED_ROW" | grep -c '_id=')"
assert_contains "D: his raw contact's account is NULL" "account_name=NULL, account_type=NULL" "$NED_ROW"
wade_unchanged "after the new contact"

# ------------------------------------------------------------------------------------------------ E: the mixed contact
log "--- E: Link Lou with Wade; Edit, no Delete; Lou's number edited; Unlink"
LO=$(( LOU < WADE ? LOU : WADE )); HI=$(( LOU < WADE ? WADE : LOU ))
card_of "$LOU" "$D/E-lou.xml"
tap_node "$D/E-lou.xml" people_card_link; sleep 2
tapid people_link_add 2
E_MARK="$(ring_mark)"
tap_row_clear "people_row:$L_WADE" "$D/E-picker.xml"; sleep 1
EX="$(exceptions)"; log "aggregation_exceptions: $(echo "$EX" | tr '\n' ';')"
assert_contains "E: Link (allowed on any contact): aggregation_exceptions shows type 1 for the pair" "type=1, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
assert_contains "E: [people] link <a>+<b>: ok" "[people] link $LOU+$WADE: ok" "$(ring_since "$E_MARK")"
assert_eq "E: one contact now stands behind both raw contacts" "$(contact_of "$LOU")" "$(contact_of "$WADE")"
wade_unchanged "after the Link"
card_of "$LOU" "$D/E-mixed.xml"; screencap "$D/E-mixed.png"
L_MIXED="$(lookup_of "$(contact_of "$LOU")")"
assert_eq "E: the joined card (people_card:<its lookup>, asserted first)" "yes" "$(has_node "$D/E-mixed.xml" "people_card:$L_MIXED")"
assert_eq "E: the joined card holds people_card_edit" "yes" "$(has_node "$D/E-mixed.xml" people_card_edit)"
assert_eq "E: … and NO people_card_delete" "no" "$(has_node "$D/E-mixed.xml" people_card_delete)"
tap_node "$D/E-mixed.xml" people_card_edit; sleep 2
dump_ui "$D/E-mixed-edit.xml"; screencap "$D/E-mixed-edit.png"
assert_eq "E: the editor opens Lou's number" "+1 555 000 0021" "$(xml_text "$D/E-mixed-edit.xml" people_field:phone)"
scroll_to_node "$D/E-mixed-ro.xml" "people_editor_readonly:$TYPE:$WORK" 5 >/dev/null 2>&1 || true
assert_eq "E: … and shows the work account's part read-only (people_editor_readonly)" "yes" "$(has_node "$D/E-mixed-ro.xml" "people_editor_readonly:$TYPE:$WORK")"
assert_absent "E: … Wade's number is in no editable field" '+1 555 000 0022' "$(grep -o '<node[^>]*resource-id="people_field:[^>]*>' "$D/E-mixed-ro.xml")"
adb shell input swipe 540 900 540 1900 200; sleep 1; adb shell input swipe 540 900 540 1900 200; sleep 1
set_field people_field:phone "+1 555 000 0091"
E2_MARK="$(ring_mark)"
dump_ui "$D/E-mixed-edit2.xml"; tap_node "$D/E-mixed-edit2.xml" people_editor_save; sleep 3
LOU_NOW="$(rows_of "$LOU")"; log "Lou now: $(echo "$LOU_NOW" | tr '\n' ';')"
assert_contains "E: Lou's data row changed" "data1=+1 555 000 0091" "$LOU_NOW"
assert_absent "E: … his old number is gone" "data1=+1 555 000 0021" "$LOU_NOW"
E_WRITES="$(ring_since "$E2_MARK" | grep -F '[people] write')"; log "$(echo "$E_WRITES" | sed 's/.*\[people\]/[people]/')"
assert_contains "E: the write names Lou's raw contact" "[people] write update raw=$LOU: ok" "$E_WRITES"
assert_absent "E: no write names Wade's raw contact" "raw=$WADE:" "$E_WRITES"
wade_unchanged "after the mixed contact's edit"
assert_eq "E: the edit did not split the aggregate" "$(contact_of "$LOU")" "$(contact_of "$WADE")"
card_of "$LOU" "$D/E-mixed2.xml"; tap_node "$D/E-mixed2.xml" people_card_link; sleep 2
dump_ui "$D/E-link2.xml"
U_MARK="$(ring_mark)"
tap_node "$D/E-link2.xml" "people_link_unlink:$WADE"; sleep 3
EX="$(exceptions)"; log "aggregation_exceptions: $(echo "$EX" | tr '\n' ';')"
assert_contains "E: Unlink: type 2 for the pair" "type=2, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
assert_absent "E: … and no type 1 left for it" "type=1, raw_contact_id1=$LO, raw_contact_id2=$HI" "$EX"
note "ring: $(ring_since "$U_MARK" | grep -F '[people]' | sed 's/.*\[people\]/[people]/' | tr '\n' ';')"
wade_unchanged "after the Unlink"
pia_unchanged "before her account is allowed"

# ------------------------------------------------------------------------------------------------ F: allowing an account
log "--- F: the personal account ticked"
can_edit_page "$D/F-can-edit.xml"
tap_node "$D/F-can-edit.xml" "people_can_edit:$TYPE:$PERSONAL"; sleep 2
dump_ui "$D/F-can-edit2.xml"; screencap "$D/F-can-edit2.png"
assert_contains "F: people_can_edit:$TYPE:$PERSONAL is ticked" 'checked="true"' "$(node_tag "$D/F-can-edit2.xml" "people_can_edit:$TYPE:$PERSONAL")"
assert_contains "F: the work account is still unticked" 'checked="false"' "$(node_tag "$D/F-can-edit2.xml" "people_can_edit:$TYPE:$WORK")"
log "people_edit.json: $(people_edit_json)"
assert_eq "F: people_edit.json lists that account and only it" "$TYPE:$PERSONAL" "$(people_allowed)"
wade_unchanged "after the personal account was ticked"
card_of "$PIA" "$D/F-pia.xml"; screencap "$D/F-pia.png"
assert_eq "F: Pia's card (asserted first)" "yes" "$(has_node "$D/F-pia.xml" "people_card:$L_PIA")"
assert_eq "F: Pia's card now holds people_card_edit" "yes" "$(has_node "$D/F-pia.xml" people_card_edit)"
assert_eq "F: … and people_card_delete" "yes" "$(has_node "$D/F-pia.xml" people_card_delete)"
tap_node "$D/F-pia.xml" people_card_edit; sleep 2
set_field people_field:phone "+1 555 000 0093"
F_MARK="$(ring_mark)"
dump_ui "$D/F-pia-edit.xml"; tap_node "$D/F-pia-edit.xml" people_editor_save; sleep 3
PIA_NOW="$(rows_of "$PIA")"; log "Pia now: $(echo "$PIA_NOW" | tr '\n' ';')"
assert_contains "F: her number edited → her data row changes" "data1=+1 555 000 0093" "$PIA_NOW"
assert_contains "F: … still in her account" "account_name=$PERSONAL, account_type=$TYPE, deleted=0" "$PIA_NOW"
assert_contains "F: [people] write update raw=<id>: ok" "[people] write update raw=$PIA: ok" "$(ring_since "$F_MARK")"
wade_unchanged "after Pia's edit"
open_people -a android.intent.action.MAIN
tapid people_bar:add 2
tapid people_editor_account 2
dump_ui "$D/F-accounts.xml"; screencap "$D/F-accounts.png"
assert_eq "F: New → people_editor_account now offers the phone and $TYPE:$PERSONAL (and nothing else)" \
  "people_editor_account_row:$TYPE:$PERSONAL people_editor_account_row:phone" \
  "$(ids_with_prefix "$D/F-accounts.xml" people_editor_account_row: | sort | tr '\n' ' ' | sed 's/ $//')"
tap_node "$D/F-accounts.xml" "people_editor_account_row:$TYPE:$PERSONAL"; sleep 2
set_field people_field:name "Pat Personal"
dump_ui "$D/F-new.xml"; tap_node "$D/F-new.xml" people_editor_save; sleep 3
PAT_ROW="$(q "content query --uri $RAW --projection _id:account_name:account_type:display_name:deleted --where \"display_name='Pat Personal'\"")"; log "$PAT_ROW"
PAT="$(printf '%s\n' "$PAT_ROW" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
[ -n "$PAT" ] && echo "$PAT" >> "$ROW_DIR/people-fixtures.ids"
assert_contains "F: a contact saved with the second lands in that account" "account_name=$PERSONAL, account_type=$TYPE" "$PAT_ROW"
wade_unchanged "after the contact saved into the personal account"
card_of "$WADE" "$D/F-wade.xml"; screencap "$D/F-wade.png"
assert_eq "F: Wade's card (asserted first)" "yes" "$(has_node "$D/F-wade.xml" "people_card:$L_WADE")"
assert_eq "F: Wade's card is still read-only: no people_card_edit" "no" "$(has_node "$D/F-wade.xml" people_card_edit)"
assert_eq "F: … no people_card_delete" "no" "$(has_node "$D/F-wade.xml" people_card_delete)"
assert_eq "F: … and the read-only line" "yes" "$(has_node "$D/F-wade.xml" people_card_readonly)"
wade_unchanged "with the personal account allowed, after his card"
card_of "$PIA" "$D/F-pia2.xml"
tap_node "$D/F-pia2.xml" people_card_delete; sleep 2
dump_ui "$D/F-pia-confirm.xml"
P_MARK="$(ring_mark)"
[ "$(has_node "$D/F-pia-confirm.xml" people_delete_confirm)" = yes ] && { tap_node "$D/F-pia-confirm.xml" people_delete_confirm; sleep 3; }
assert_eq "F: Pia deleted from her card → her contact row gone" "0" "$(q "content query --uri $CONTACTS --projection _id --where \"display_name='Pia Personal'\"" | grep -c '_id=')"
log "$(ring_since "$P_MARK" | grep -F '[people] write' | sed 's/.*\[people\]/[people]/')"
assert_contains "F: … through the write layer (write delete raw=<id>: ok)" "[people] write delete raw=$PIA: ok" "$(ring_since "$P_MARK")"
wade_unchanged "after Pia's delete"

# ------------------------------------------------------------------------------------------------ G: un-tick
log "--- G: un-tick → a remaining contact of that account is read-only again"
can_edit_page "$D/G-can-edit.xml"
tap_node "$D/G-can-edit.xml" "people_can_edit:$TYPE:$PERSONAL"; sleep 2
dump_ui "$D/G-can-edit2.xml"
assert_contains "G: the personal account is unticked" 'checked="false"' "$(node_tag "$D/G-can-edit2.xml" "people_can_edit:$TYPE:$PERSONAL")"
assert_eq "G: people_edit.json holds no allowed account again" "" "$(people_allowed)"
card_of "${PAT:-0}" "$D/G-pat.xml"; screencap "$D/G-pat.png"
L_PAT="$(lookup_of "$(contact_of "${PAT:-0}")")"
assert_eq "G: the remaining contact's card (Pat Personal; asserted first)" "yes" "$(has_node "$D/G-pat.xml" "people_card:$L_PAT")"
assert_eq "G: … is read-only again: no people_card_edit" "no" "$(has_node "$D/G-pat.xml" people_card_edit)"
assert_eq "G: … no people_card_delete" "no" "$(has_node "$D/G-pat.xml" people_card_delete)"
assert_contains "G: … and the read-only line names its account" "$PERSONAL" "$(xml_text "$D/G-pat.xml" people_card_readonly)"
wade_unchanged "at the end, after the un-tick"
assert_eq "Wade's rows were re-read after every step (the count of reads)" "yes" "$([ "$STEP" -ge 14 ] && echo yes || echo no)"

# ------------------------------------------------------------------------------------------------ X: the write layer refuses
# Added by the adversarial trust review (fix-round.md, row F15; no spec text changed): no page offers a refused write,
# so the write layer's own refusal is reached by changing the ground under an open editor. Lou (phone-only, editable)
# is opened in the editor and his number changed in the field, NOT saved; from adb his raw contact is re-homed to the
# work account as a sync adapter would; Save is tapped. The guard resolves the raw contact from the provider at Save,
# so it must refuse. (If the provider refuses to re-home a raw contact, what it said is recorded, Lou's raw contact is
# deleted instead, and the expected line is the writer's `failed <err>` for a vanished raw contact.)
log "--- X (fix-round F15): Save on an editor whose raw contact was re-homed to an account not allowed"
LOU_PHONE_OLD="$(q "content query --uri $DATA --projection _id:data1 --where \"raw_contact_id=$LOU AND mimetype='vnd.android.cursor.item/phone_v2'\"")"
note "Lou's phone row before the leg: $LOU_PHONE_OLD"
card_of "$LOU" "$D/X-lou.xml"
assert_eq "X: Lou's card offers Edit (he is phone-only and editable)" "yes" "$(has_node "$D/X-lou.xml" people_card_edit)"
tap_node "$D/X-lou.xml" people_card_edit; sleep 2
set_field people_field:phone "+1 555 000 0055"
dump_ui "$D/X-edit.xml"
assert_eq "X: the editor holds the changed number, not saved" "+1 555 000 0055" "$(xml_text "$D/X-edit.xml" people_field:phone)"
assert_eq "X: the provider still holds the old number before Save" "$LOU_PHONE_OLD" "$(q "content query --uri $DATA --projection _id:data1 --where \"raw_contact_id=$LOU AND mimetype='vnd.android.cursor.item/phone_v2'\"")"
REHOME="$(q "content update --uri '$RAW?$SA' --bind account_name:s:$WORK --bind account_type:s:$TYPE --where \"_id=$LOU\"")"
sleep 2
LOU_ROW="$(q "content query --uri $RAW --projection _id:account_name:account_type:deleted --where \"_id=$LOU\"")"
record "X: the sync-adapter content update that re-homes Lou's raw contact said" "[${REHOME:-no output}]; his row then: $LOU_ROW"
dump_ui "$D/X-after-rehome.xml"; screencap "$D/X-after-rehome.png"
record "X: the page on show after the re-home, before Save" "$(grep -o 'resource-id="people_page:[^"]*"[^>]*selected="true"' "$D/X-after-rehome.xml" | sed 's/resource-id="//; s/".*//' | tr '\n' ' ')"
if printf '%s' "$LOU_ROW" | grep -q "account_name=$WORK, account_type=$TYPE"; then
  assert_contains "X: Lou's raw contact is now in the work account (not allowed)" "account_name=$WORK, account_type=$TYPE, deleted=0" "$LOU_ROW"
  X_MARK="$(ring_mark)"
  if tap_node "$D/X-after-rehome.xml" people_editor_save; then sleep 3; else note "X: no people_editor_save on the page after the re-home"; fi
  dump_ui "$D/X-refused.xml"; screencap "$D/X-refused.png"
  X_SLICE="$(ring_since "$X_MARK")"; log "$(printf '%s\n' "$X_SLICE" | grep -F '[people] write' | sed 's/.*\[people\]/[people]/')"
  assert_contains "X: the slice holds [people] write update raw=<id>: refused (not allowed)" "[people] write update raw=$LOU: refused (not allowed)" "$X_SLICE"
  assert_eq "X: people_notice shows" "yes" "$(has_node "$D/X-refused.xml" people_notice)"
  log "X: people_notice: [$(xml_text "$D/X-refused.xml" people_notice)]"
  assert_eq "X: Lou's data row still holds the OLD number (read from the provider)" "$LOU_PHONE_OLD" "$(q "content query --uri $DATA --projection _id:data1 --where \"raw_contact_id=$LOU AND mimetype='vnd.android.cursor.item/phone_v2'\"")"
  absent_in "X: no write … ok line for Lou's raw contact" "raw=$LOU: ok" "$X_SLICE"
else
  record "X: the provider did not re-home the raw contact" "Lou's raw contact is deleted instead (sync-adapter delete); the expected line is the writer's failed <err>"
  q "content delete --uri '$RAW/$LOU?$SA'" >/dev/null; sleep 2
  dump_ui "$D/X-after-delete.xml"
  X_MARK="$(ring_mark)"
  if tap_node "$D/X-after-delete.xml" people_editor_save; then sleep 3; else note "X: no people_editor_save on the page after the delete"; fi
  dump_ui "$D/X-refused.xml"; screencap "$D/X-refused.png"
  X_SLICE="$(ring_since "$X_MARK")"; log "$(printf '%s\n' "$X_SLICE" | grep -F '[people] write' | sed 's/.*\[people\]/[people]/')"
  assert_contains "X: the slice holds [people] write update raw=<id>: failed <err> (the raw contact vanished)" "[people] write update raw=$LOU: failed " "$X_SLICE"
  assert_eq "X: people_notice shows" "yes" "$(has_node "$D/X-refused.xml" people_notice)"
  assert_eq "X: no phone row holds the unsaved number" "0" "$(q "content query --uri $DATA --projection _id:data1 --where \"data1='+1 555 000 0055'\"" | grep -c 'data1=')"
fi
wade_unchanged "after the refused Save (leg X)"
back 1; back 1

# ------------------------------------------------------------------------------------------------ the guard's JVM test
log "--- the guard's JVM test (PeopleWriteGuardTest) — the lead's result on this tree; the QA writers never run ./gradlew"
T=app.tileshell.people.PeopleWriteGuardTest
RES="$(jvm_result "$T")"; log "PeopleWriteGuardTest: ${RES:-no result file}"
jvm_cases "$T" >> "$LOG"
assert_contains "JVM: the guard's test ran" "tests=" "$RES"
assert_contains "JVM: … with no failure and no error" "failures=0 errors=0" "$RES"
assert_eq "JVM: … none skipped" "skipped=0" "$(echo "$RES" | grep -o 'skipped=[0-9]*')"
assert_eq "JVM: the result is newer than the guard, its test and the write layer (the code under test)" "yes" \
  "$(jvm_fresh "$T" "$REPO/app/src/main/kotlin/app/tileshell/people/PeopleWriteGuard.kt" "$REPO/app/src/test/kotlin/app/tileshell/people/PeopleWriteGuardTest.kt")"
CASES="$(jvm_cases "$T")"
record "JVM: test cases in the result" "$(printf '%s\n' "$CASES" | grep -c .)"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back 1; back 1
people_fixtures_down
assert_eq "restore: the raw_contacts count equals the count before the row" "$RAW0" "$(raw_count)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
assert_absent "restore: aggregation_exceptions lists neither fixture id" "raw_contact_id1=$LO, raw_contact_id2=$HI" "$(exceptions)"
ring_save
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 1
adb shell pm clear app.tileshell >/dev/null
assert_eq "restore: provision.sh rc" "0" "$(provision restore)"
ensure_start
assert_eq "restore: people_edit.json is cleared (no allowed account)" "" "$(people_allowed)"
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
assert_eq "restore: READ_CONTACTS and WRITE_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
ensure_start
row_end
