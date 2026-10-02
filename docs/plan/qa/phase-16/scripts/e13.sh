#!/usr/bin/env bash
# Phase 16 E13 — Create, edit, photo, delete (WRITE_CONTACTS), clause by clause from the phase doc's row E13.
#
#   fixtures   people_fixtures_up (phone-only, which Q-16-3 keeps editable with nothing on "Can edit")
#   new        New → people_editor_account reads "Phone" and offers no other row while nothing is allowed →
#              "Dan Ford" with a mobile number → the doc's data query shows the number under a NEW raw contact whose
#              account_name and account_type are NULL
#   edit       Ann's number edited → the data row changes
#   photo      Photo → Android's photo picker (dumpsys activity activities) → a pushed solid-colour JPEG chosen → a
#              vnd.android.cursor.item/photo data row for Ann; people_card_photo's centre pixel = its colour ± 8
#   delete     Dan deleted → his contact row is gone
#   revoked    pm revoke WRITE_CONTACTS → the editor says it cannot save and offers the grant in place (people_notice);
#              Setup shows checklist:people:partial while Tess's contacts row still reads granted
#              (cortana_check:contacts:granted); pm grant restores checklist:people:granted
#   restore    pm grant WRITE_CONTACTS (asserted held), people_fixtures_down, the pushed JPEG removed
#
# The `[people] write <op> raw=<id>: ok | failed <err>` lines of each step are asserted too: they are this row's
# producers for E21 (the row's own clauses are the provider reads).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"

row_begin E13 "create, edit, photo, delete; WRITE_CONTACTS revoked"
require_build
D="$ROW_DIR"
ensure_start
perm_ensure READ_CONTACTS WRITE_CONTACTS
assert_eq "precondition: READ_CONTACTS and WRITE_CONTACTS held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
assert_eq "precondition: nothing is on \"Can edit\" (people_edit.json allows no account)" "" "$(people_allowed)"
note "people_edit.json: $(people_edit_json)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"

log "--- fixtures"
people_fixtures_up
MAX_BEFORE="$(q "content query --uri $RAW --projection _id" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tail -1)"
L_ANN="$(lookup_of "$(contact_of "$ANN")")"
assert_contains "Ann Lee is phone-only (Q-16-3: editable with nothing allowed)" "account_name=NULL, account_type=NULL" "$(raw_row "$ANN")"
RED="$(push_photo red)"; note "pushed $RED"
assert_eq "the solid-red fixture is on the device" "1" "$(pushed_photos_left)"

# ------------------------------------------------------------------------------------------------ New
log "--- New: the account choice, then Dan Ford with a mobile number"
open_people -a android.intent.action.MAIN
wait_node "$D/list.xml" people_bar:add 8 || true
tap_node "$D/list.xml" people_bar:add; sleep 2
dump_ui "$D/new.xml"; screencap "$D/new.png"
assert_contains "New opens the editor (people_page:editor selected)" 'selected="true"' "$(node_tag "$D/new.xml" people_page:editor)"
assert_eq "people_editor_account reads \"Phone\"" "Phone" "$(xml_text "$D/new.xml" people_editor_account)"
tap_node "$D/new.xml" people_editor_account; sleep 2
dump_ui "$D/new-accounts.xml"; screencap "$D/new-accounts.png"
note "account rows offered: $(ids_with_prefix "$D/new-accounts.xml" people_editor_account_row: | tr '\n' ' ')"
assert_eq "its menu offers the phone's row" "yes" "$(has_node "$D/new-accounts.xml" people_editor_account_row:phone)"
assert_eq "… and no other row while nothing is allowed" "1" "$(count_ids "$D/new-accounts.xml" people_editor_account_row:)"
back 1
dump_ui "$D/new-back.xml"
if [ "$(has_node "$D/new-back.xml" people_page:editor)" != yes ]; then   # Back left the editor instead of the menu
  note "Back closed the editor, not only the account menu: reopening New"
  open_people -a android.intent.action.MAIN; tapid people_bar:add 2
fi
set_field people_field:name "Dan Ford"
set_field people_field:phone "+1 555 000 0042"
dump_ui "$D/new-filled.xml"; screencap "$D/new-filled.png"
assert_eq "the phone field's type reads Mobile (a mobile number)" "Mobile phone" "$(xml_text "$D/new-filled.xml" people_field_type:phone)"
assert_eq "nothing is in the provider before Save is tapped" "0" "$(q "content query --uri $RAW --projection _id --where \"display_name='Dan Ford'\"" | grep -c '_id=')"
MARK="$(ring_mark)"
tap_node "$D/new-filled.xml" people_editor_save; sleep 3
# The doc's query, whole.
PHONES="$(q "content query --uri $DATA --projection raw_contact_id:mimetype:data1 --where \"mimetype='vnd.android.cursor.item/phone_v2'\"")"
printf '%s\n' "$PHONES" > "$D/phones-after-new.txt"; printf '%s\n' "$PHONES" >> "$LOG"
DAN="$(printf '%s\n' "$PHONES" | grep -F 'data1=+1 555 000 0042' | sed -n 's/.*raw_contact_id=\([0-9]*\),.*/\1/p' | head -1)"
assert_ne "the data query shows the number under a raw contact" "" "$DAN"
[ -n "$DAN" ] && echo "$DAN" >> "$ROW_DIR/people-fixtures.ids"
assert_eq "… exactly one phone row holds it" "1" "$(printf '%s\n' "$PHONES" | grep -cF 'data1=+1 555 000 0042')"
assert_eq "… a NEW raw contact (its _id is above every _id before New)" "yes" "$([ -n "$DAN" ] && [ "$DAN" -gt "${MAX_BEFORE:-0}" ] && echo yes || echo no)"
DAN_ROW="$(raw_row "${DAN:-0}")"; log "$DAN_ROW"
assert_contains "… named Dan Ford" "display_name=Dan Ford" "$DAN_ROW"
assert_contains "… whose account_name and account_type are NULL (the phone, Q-16-3)" "account_name=NULL, account_type=NULL" "$DAN_ROW"
assert_contains "… the number stored as a mobile (data2 = 2)" "data2=2" "$(q "content query --uri $DATA --projection data1:data2 --where \"raw_contact_id=${DAN:-0} AND mimetype='vnd.android.cursor.item/phone_v2'\"")"
assert_contains "E21: [people] write insert raw=<id>: ok" "[people] write insert raw=$DAN: ok" "$(ring_since "$MARK")"
L_DAN="$(lookup_of "$(contact_of "${DAN:-0}")")"

# ------------------------------------------------------------------------------------------------ edit Ann's number
log "--- edit Ann's number"
PH_BEFORE="$(q "content query --uri $DATA --projection _id:mimetype:data1 --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/phone_v2'\"")"; log "before: $PH_BEFORE"
card_of "$ANN" "$D/ann-card.xml"
assert_eq "Ann's card (people_card:<lookup>)" "yes" "$(has_node "$D/ann-card.xml" "people_card:$L_ANN")"
tap_node "$D/ann-card.xml" people_card_edit; sleep 2
dump_ui "$D/ann-edit.xml"
assert_eq "the editor shows her stored number" "+1 555 000 0001" "$(xml_text "$D/ann-edit.xml" people_field:phone)"
set_field people_field:phone "+1 555 000 0077"
MARK="$(ring_mark)"
dump_ui "$D/ann-edit2.xml"; tap_node "$D/ann-edit2.xml" people_editor_save; sleep 3
PH_AFTER="$(q "content query --uri $DATA --projection _id:mimetype:data1 --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/phone_v2'\"")"; log "after:  $PH_AFTER"
assert_contains "her phone data row holds the new number" "data1=+1 555 000 0077" "$PH_AFTER"
assert_absent "… and the old number is gone" "data1=+1 555 000 0001" "$PH_AFTER"
assert_eq "… still one phone row for her" "1" "$(printf '%s\n' "$PH_AFTER" | grep -c 'phone_v2')"
record "the phone data row's _id before → after the edit" "$(printf '%s' "$PH_BEFORE" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p') → $(printf '%s' "$PH_AFTER" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')"
assert_contains "her e-mail row is untouched" "data1=ann@example.com" "$(data_rows "$ANN")"
assert_contains "E21: [people] write update raw=<id>: ok" "[people] write update raw=$ANN: ok" "$(ring_since "$MARK")"

# ------------------------------------------------------------------------------------------------ photo
log "--- Photo: Android's photo picker, a pushed JPEG, the photo data row, the card"
assert_eq "Ann has no photo data row before" "0" "$(photo_rows "$ANN")"
card_of "$ANN" "$D/ann-card2.xml"; tap_node "$D/ann-card2.xml" people_card_edit; sleep 2
dump_ui "$D/ann-edit3.xml"; tap_node "$D/ann-edit3.xml" people_editor_photo; sleep 3
adb shell dumpsys activity activities | tr -d '\r' | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity' > "$D/picker-activities.txt"
PICKER="$(top_activity)"; log "after the Photo tap, dumpsys activity activities shows: $PICKER"
assert_contains "Android's photo picker is the resumed activity" "photopicker" "$(echo "$PICKER" | tr 'A-Z' 'a-z')"
assert_absent "… not an activity of the shell's" "app.tileshell/" "$PICKER"
pick_photo "${PHOTO_RGB[red]}"
assert_eq "after the choice People's editor is back" "$PEOPLE_ACTIVITY" "$(top_activity)"
MARK="$(ring_mark)"
dump_ui "$D/ann-edit4.xml"; screencap "$D/ann-edit4.png"
tap_node "$D/ann-edit4.xml" people_editor_save; sleep 4
PHOTO="$(q "content query --uri $DATA --projection _id:raw_contact_id:mimetype --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/photo'\"")"; log "$PHOTO"
assert_eq "a vnd.android.cursor.item/photo data row exists for Ann" "1" "$(photo_rows "$ANN")"
sleep 1
dump_ui "$D/ann-card3.xml"; screencap "$D/ann-card3.png"
if [ "$(has_node "$D/ann-card3.xml" "people_card:$L_ANN")" != yes ]; then card_of "$ANN" "$D/ann-card3.xml"; screencap "$D/ann-card3.png"; fi
assert_eq "her card is on show (people_card:<lookup>)" "yes" "$(has_node "$D/ann-card3.xml" "people_card:$L_ANN")"
CB="$(bounds "$D/ann-card3.xml" people_card_photo)"
assert_ne "the card has people_card_photo" "" "$CB"
assert_color "people_card_photo's centre pixel equals the fixture's colour ± 8" "${PHOTO_RGB[red]}" "$(centre_px "$D/ann-card3.png" "${CB:-0 0 2 2}")" 8
# shown again from a cold start of the shell (the photo is read from the provider, not kept from the picker)
c6; ensure_start
card_of "$ANN" "$D/ann-card4.xml"; screencap "$D/ann-card4.png"
CB="$(bounds "$D/ann-card4.xml" people_card_photo)"
assert_color "… and again after the shell's process restarted" "${PHOTO_RGB[red]}" "$(centre_px "$D/ann-card4.png" "${CB:-0 0 2 2}")" 8

# ------------------------------------------------------------------------------------------------ delete Dan
log "--- delete Dan"
assert_eq "Dan's contact row exists before the delete" "1" "$(q "content query --uri $CONTACTS --projection _id --where \"display_name='Dan Ford'\"" | grep -c '_id=')"
card_of "${DAN:-0}" "$D/dan-card.xml"
assert_eq "Dan's card (asserted first)" "yes" "$(has_node "$D/dan-card.xml" "people_card:$L_DAN")"
tap_node "$D/dan-card.xml" people_card_delete; sleep 2
dump_ui "$D/dan-confirm.xml"; screencap "$D/dan-confirm.png"
MARK="$(ring_mark)"
if [ "$(has_node "$D/dan-confirm.xml" people_delete_dialog)" = yes ]; then
  record "Delete asks once before it deletes (built so; Change Log 2026-10-01, P2)" "people_delete_dialog shown; confirm tapped"
  tap_node "$D/dan-confirm.xml" people_delete_confirm; sleep 3
else
  record "Delete asks once before it deletes" "no dialog in the dump"
fi
assert_eq "Dan's contact row is gone" "0" "$(q "content query --uri $CONTACTS --projection _id --where \"display_name='Dan Ford'\"" | grep -c '_id=')"
assert_eq "… and no live raw contact of his is left" "0" "$(q "content query --uri $RAW --projection _id:deleted --where \"_id=${DAN:-0} AND deleted=0\"" | grep -c '_id=')"
assert_contains "E21: [people] write delete raw=<id>: ok" "[people] write delete raw=$DAN: ok" "$(ring_since "$MARK")"
assert_eq "Ann is still there (the delete took Dan only)" "1" "$(q "content query --uri $RAW --projection _id --where \"_id=$ANN AND deleted=0\"" | grep -c '_id=')"

# ------------------------------------------------------------------------------------------------ WRITE_CONTACTS revoked
log "--- pm revoke WRITE_CONTACTS: the cannot-save notice, the two checklists, pm grant"
ring_save
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS; sleep 2     # ends the shell's process
assert_eq "WRITE_CONTACTS is revoked" "false" "$(perm_granted WRITE_CONTACTS)"
assert_eq "READ_CONTACTS is still held" "true" "$(perm_granted READ_CONTACTS)"
ensure_start
ANN_DATA_BEFORE="$(data_rows "$ANN" | grep -v 'cursor.item/photo')"
card_of "$ANN" "$D/r-card.xml"; tap_node "$D/r-card.xml" people_card_edit; sleep 2
set_field people_field:name "Ann Leigh"
MARK="$(ring_mark)"
dump_ui "$D/r-edit.xml"; tap_node "$D/r-edit.xml" people_editor_save; sleep 3
dump_ui "$D/r-notice.xml"; screencap "$D/r-notice.png"
NOTICE="$(xml_text "$D/r-notice.xml" people_notice)"; log "people_notice: [$NOTICE]  people_notice_action: [$(xml_text "$D/r-notice.xml" people_notice_action)]"
assert_eq "people_notice is on the editor" "yes" "$(has_node "$D/r-notice.xml" people_notice)"
assert_contains "… and says it cannot save" "can't save" "$NOTICE"
assert_eq "… with the grant offered in place (people_notice_action)" "yes" "$(has_node "$D/r-notice.xml" people_notice_action)"
assert_contains "the editor is still the page on show, the edit kept" 'selected="true"' "$(node_tag "$D/r-notice.xml" people_page:editor)"
assert_eq "nothing was written: Ann's data rows equal their read before Save" "$ANN_DATA_BEFORE" "$(data_rows "$ANN" | grep -v 'cursor.item/photo')"
assert_contains "E21: [people] write update raw=<id>: failed <err>" "[people] write update raw=$ANN: failed " "$(ring_since "$MARK")"
log "$(ring_since "$MARK" | grep -F '[people] write' | sed 's/.*\[people\]/[people]/')"
# The notice is a node that stays until the page changes, never a toast (Harness, round 3): still there 6 s later.
sleep 6; dump_ui "$D/r-notice-6s.xml"
assert_eq "the notice is still on the page 6 s later (a node, not a toast)" "yes" "$(has_node "$D/r-notice-6s.xml" people_notice)"
back 1; back 1
SETUP="$(setup_people_row "$D/r-setup.xml")"; screencap "$D/r-setup.png"
assert_eq "the Setup checklist shows checklist:people:partial (READ held, WRITE not)" "checklist:people:partial" "$SETUP"
adb shell input keyevent KEYCODE_HOME; sleep 2
TESS="$(tess_contacts_row "$D/r-tess.xml")"; screencap "$D/r-tess.png"
assert_eq "Tess's contacts row still reads granted (cortana_check:contacts:granted; T16-15)" "cortana_check:contacts:granted" "$TESS"
cortana_close; adb shell input keyevent KEYCODE_HOME; sleep 2
ring_save
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS; sleep 1
assert_eq "pm grant: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
SETUP="$(setup_people_row "$D/g-setup.xml")"; screencap "$D/g-setup.png"
assert_eq "pm grant restores checklist:people:granted" "checklist:people:granted" "$SETUP"
adb shell input keyevent KEYCODE_HOME; sleep 2

# The offer itself, taken once: the in-place grant is tapped and what Android did is recorded (a dialog, or a silent
# grant because READ_CONTACTS of the same group is held); the editor then saves.
log "--- the in-place offer tapped (phase 10 E18's form)"
ring_save
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS; sleep 2
ensure_start
card_of "$ANN" "$D/o-card.xml"; tap_node "$D/o-card.xml" people_card_edit; sleep 2
set_field people_field:name "Ann Leigh"
dump_ui "$D/o-edit.xml"; tap_node "$D/o-edit.xml" people_editor_save; sleep 3
dump_ui "$D/o-notice.xml"
tap_node "$D/o-notice.xml" people_notice_action; sleep 3
dump_ui "$D/o-dialog.xml"; screencap "$D/o-dialog.png"
if grep -q 'package="com[^"]*permissioncontroller"' "$D/o-dialog.xml"; then
  record "the in-place offer raised Android's permission dialog" "yes ($(top_activity))"
  ALLOW="$(bounds "$D/o-dialog.xml" com.android.permissioncontroller:id/permission_allow_button)"
  [ -n "$ALLOW" ] && { tap_node "$D/o-dialog.xml" com.android.permissioncontroller:id/permission_allow_button; sleep 3; }
else
  record "the in-place offer raised Android's permission dialog" "no dialog: Android granted it at once (READ_CONTACTS of the same group is held); top activity $(top_activity)"
fi
assert_eq "the in-place offer leads to WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
dump_ui "$D/o-after.xml"
MARK="$(ring_mark)"
if [ "$(has_node "$D/o-after.xml" people_editor_save)" = yes ]; then tap_node "$D/o-after.xml" people_editor_save; sleep 3; fi
assert_contains "… and Save then writes her name" "data1=Ann Leigh" "$(data_rows "$ANN")"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back 1; back 1
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "restore: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
assert_eq "restore: READ_CONTACTS held" "true" "$(perm_granted READ_CONTACTS)"
people_fixtures_down
remove_photos
assert_eq "restore: the pushed JPEG is removed" "0" "$(pushed_photos_left)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
assert_eq "restore: nothing is on \"Can edit\"" "" "$(people_allowed)"
c6; ensure_start
row_end
