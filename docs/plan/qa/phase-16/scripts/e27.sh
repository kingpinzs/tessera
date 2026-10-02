#!/usr/bin/env bash
# Phase 16 E27 — Groups (T16-14; H16), clause by clause from the phase doc's row E27.
#
#   fixtures   people_fixtures_up — Ann Lee (mobile +1 555 000 0001) and Bob Stone (mobile +1 555 000 0002), phone-only
#   create     people_pivot:groups → people_group_new, "Family" in people_group_name, Ann and Bob added → the doc's
#              groups query lists "Family" with NULL account name and type (the phone: nothing is on "Can edit"), the
#              two members' group_membership data rows point at its _id, `[people] group create <id>: ok`
#   page       people_group:<id> lists Ann and Bob
#   text       people_group_action:text → the SMS role holder's compose (the Fossify Messages fixture) resumes with
#              smsto: both numbers (dumpsys activity activities; the intent's data holds both)
#   rename     people_group_action:rename to "Home" → the provider's title reads Home, `group rename <id>: ok`
#   delete     people_group_action:delete → the group row gone (or marked deleted), both contacts still present,
#              `group delete <id>: ok`
#   revoked    with WRITE_CONTACTS revoked a create is refused with the editor's notice (people_notice) and
#              `group create …: failed <err>`
#   restore    pm grant WRITE_CONTACTS (asserted), any group left deleted by id, people_fixtures_down
#
# Beyond the row's words, for E21 (each group op's ok AND failed): with WRITE_CONTACTS revoked a rename and a delete of
# a second group ("Spare", made while the grant was held) are tried, and their `failed` lines asserted.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
# The doc's query (plus `deleted`, read in a second query so the doc's projection stays as written).
groups_q() { q "content query --uri $GROUPS_URI --projection _id:title:account_type:account_name"; }
groups_live() { q "content query --uri $GROUPS_URI --projection _id:title:deleted --where \"deleted=0\""; }
members() { q "content query --uri $DATA --projection raw_contact_id:mimetype:data1 --where \"mimetype='vnd.android.cursor.item/group_membership' AND data1=$1\""; }
group_id() { groups_q | grep -F "title=$1," | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
open_groups() { # out.xml — the list, then the GROUPS pivot header tapped
  open_people -a android.intent.action.MAIN
  dump_ui "$1.contacts.xml"
  tap_node "$1.contacts.xml" people_pivot:groups; sleep 2
  dump_ui "$1"
}
new_group() { # name -> its _id
  tapid people_group_new 2
  set_field people_group_name "$1"
  tapid people_group_save 3
  group_id "$1"
}

row_begin E27 "Groups: create, members, text the group, rename, delete; WRITE_CONTACTS revoked"
require_build
D="$ROW_DIR"
ensure_start
perm_ensure READ_CONTACTS WRITE_CONTACTS
assert_eq "precondition: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
assert_eq "precondition: nothing is on \"Can edit\"" "" "$(people_allowed)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
groups_q > "$D/groups-before.txt"; G0="$(grep -c '_id=' "$D/groups-before.txt")"
note "groups before the row ($G0): $(tr '\n' ';' < "$D/groups-before.txt")"
HOLDER="$(sms_holder)"; note "the SMS role holder: $HOLDER"

log "--- fixtures"
people_fixtures_up
L_ANN="$(lookup_of "$(contact_of "$ANN")")"; L_BOB="$(lookup_of "$(contact_of "$BOB")")"
assert_contains "Ann Lee: phone-only, mobile +1 555 000 0001" "data1=+1 555 000 0001" "$(data_rows "$ANN")"
assert_contains "Bob Stone: phone-only, mobile +1 555 000 0002" "data1=+1 555 000 0002" "$(data_rows "$BOB")"
assert_eq "both numbers are stored as mobile (data2 = 2)" "2" "$(q "content query --uri $DATA --projection raw_contact_id:data2 --where \"raw_contact_id IN ($ANN,$BOB) AND mimetype='vnd.android.cursor.item/phone_v2' AND data2=2\"" | grep -c 'data2=2')"

# ------------------------------------------------------------------------------------------------ create
log "--- people_pivot:groups → people_group_new → \"Family\" → Ann and Bob"
open_groups "$D/groups.xml"; screencap "$D/groups.png"
assert_contains "the GROUPS pivot is on show (people_pivot:groups selected)" 'selected="true"' "$(node_tag "$D/groups.xml" people_pivot:groups)"
assert_eq "people_group_new is offered" "yes" "$(has_node "$D/groups.xml" people_group_new)"
tap_node "$D/groups.xml" people_group_new; sleep 2
dump_ui "$D/new.xml"; screencap "$D/new.png"
assert_eq "the group editor has people_group_name" "yes" "$(has_node "$D/new.xml" people_group_name)"
set_field people_group_name "Family"
MARK="$(ring_mark)"
dump_ui "$D/new2.xml"
assert_eq "\"Family\" is in people_group_name" "Family" "$(xml_text "$D/new2.xml" people_group_name)"
tap_node "$D/new2.xml" people_group_save; sleep 3
G="$(groups_q)"; printf '%s\n' "$G" > "$D/groups-after-create.txt"; printf '%s\n' "$G" >> "$LOG"
GID="$(group_id Family)"
assert_ne "the groups query lists \"Family\"" "" "$GID"
assert_contains "… with NULL account name and type (the phone; Q-16-3)" "title=Family, account_type=NULL, account_name=NULL" "$G"
CREATE_LINE="$(ring_since "$MARK" | grep -F '[people] group create' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$CREATE_LINE"
assert_eq "the slice holds [people] group create <id>: ok" "[people] group create $GID: ok" "$CREATE_LINE"
dump_ui "$D/group.xml"; screencap "$D/group.png"
assert_eq "the group's page is on show (people_group:<id>)" "yes" "$(has_node "$D/group.xml" "people_group:$GID")"
tap_node "$D/group.xml" people_group_add_member; sleep 2
tap_row_clear "people_row:$L_ANN" "$D/picker1.xml"
tap_row_clear "people_row:$L_BOB" "$D/picker2.xml"
dump_ui "$D/picker3.xml"; screencap "$D/picker3.png"
M="$(members "${GID:-0}")"; printf '%s\n' "$M" >> "$LOG"
assert_contains "Ann's group_membership data row points at the group's _id" "raw_contact_id=$ANN, mimetype=vnd.android.cursor.item/group_membership, data1=$GID" "$M"
assert_contains "Bob's group_membership data row points at the group's _id" "raw_contact_id=$BOB, mimetype=vnd.android.cursor.item/group_membership, data1=$GID" "$M"
assert_eq "exactly two membership rows point at it" "2" "$(printf '%s\n' "$M" | grep -c 'group_membership')"
back 2

# ------------------------------------------------------------------------------------------------ the group's page
log "--- people_group:<id> lists Ann and Bob"
dump_ui "$D/group2.xml"; screencap "$D/group2.png"
if [ "$(has_node "$D/group2.xml" "people_row:$L_ANN")" != yes ]; then   # reached from the pivot, as a user would
  open_groups "$D/groups-b.xml"; tap_node "$D/groups-b.xml" "people_group:$GID"; sleep 2; dump_ui "$D/group2.xml"; screencap "$D/group2.png"
fi
assert_eq "the page is people_group:<id>" "yes" "$(has_node "$D/group2.xml" "people_group:$GID")"
assert_eq "it lists Ann" "Ann Lee" "$(xml_text "$D/group2.xml" "people_name:$L_ANN")"
assert_eq "it lists Bob" "Bob Stone" "$(xml_text "$D/group2.xml" "people_name:$L_BOB")"
assert_eq "… and no one else" "2" "$(count_ids "$D/group2.xml" people_row:)"

# ------------------------------------------------------------------------------------------------ text the group
log "--- people_group_action:text"
adb shell am force-stop "$HOLDER"; sleep 1
T_DEV="$(dev_time)"
MARK="$(ring_mark)"
tap_node "$D/group2.xml" people_group_action:text; sleep 5
TOP="$(top_activity)"; log "resumed: $TOP"
assert_contains "the SMS role holder (the Fossify Messages fixture) is resumed (dumpsys activity activities)" "$HOLDER/" "$TOP"
adb shell dumpsys activity activities | tr -d '\r' > "$D/activities-text.txt"
record "dumpsys activity activities, 5 s on: the resumed activity's own intent (Fossify forwarded the SENDTO and finished the activity that received it; clauses-open.tsv)" "$(grep -m1 -A6 'Hist  #' "$D/activities-text.txt" | grep -m1 'Intent {' | sed 's/^ *//' | cut -c1-160)"
# The start itself, from the activity manager's own log line (people_lib.sh: starts_since).
starts_since "$T_DEV" > "$D/starts-text.txt"
INTENT="$(grep -m1 -F 'smsto:' "$D/starts-text.txt" | sed 's/^.*START u0/START u0/' | cut -c1-300)"; log "the activity manager's start line: $INTENT"
assert_contains "the compose was started with ACTION_SENDTO" "act=android.intent.action.SENDTO" "$INTENT"
assert_contains "… a smsto: URI" "dat=smsto:" "$INTENT"
assert_contains "… to the SMS role holder" "cmp=$HOLDER/" "$INTENT"
assert_contains "… by the shell (from uid <the shell's uid>)" "from uid $(shell_uid) " "$INTENT"
# Android prints an smsto: URI redacted (every character after the scheme as x): its LENGTH is what the system's line
# can prove — both numbers and their separator, "+15550000001;+15550000002" = 25 characters.
SSP="$(printf '%s' "$INTENT" | sed -n 's/.*dat=smsto:\([^ ]*\).*/\1/p')"
record "the intent's data as the activity manager prints it (redacted by Android)" "smsto:$SSP"
assert_eq "… whose data is as long as both members' numbers joined (25 characters)" "25" "${#SSP}"
ACTION_LINE="$(ring_since "$MARK" | grep -F '[people] action text' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$ACTION_LINE"
assert_contains "the intent's data holds both numbers (the shell's own action line, V21)" "smsto:+15550000001;+15550000002" "$ACTION_LINE"
assert_contains "… sent to the SMS role holder" "[people] action text -> $HOLDER" "$ACTION_LINE"
dump_ui "$D/compose.xml"; screencap "$D/compose.png"
COMPOSE_TEXT="$(grep -o 'text="[^"]\+"' "$D/compose.xml" | tr '\n' ' ' | cut -c1-400)"; note "the compose window's texts: $COMPOSE_TEXT"
# The receiving app's own window, read from the device: both members are its recipients (by name or by number).
has_member() { grep -qE "$1" "$D/compose.xml" && echo yes || echo no; }
assert_eq "the compose window names Ann (her name or her number)" "yes" "$(has_member 'Ann Lee|555[ -]?000[ -]?0001|5550000001')"
assert_eq "the compose window names Bob (his name or his number)" "yes" "$(has_member 'Bob Stone|555[ -]?000[ -]?0002|5550000002')"
adb shell am force-stop "$HOLDER"; sleep 1

# ------------------------------------------------------------------------------------------------ rename
log "--- people_group_action:rename to \"Home\""
open_groups "$D/groups2.xml"; tap_node "$D/groups2.xml" "people_group:$GID"; sleep 2
dump_ui "$D/group3.xml"; tap_node "$D/group3.xml" people_group_action:rename; sleep 2
set_field people_group_name "Home"
MARK="$(ring_mark)"
tapid people_group_save 3
G="$(groups_q)"; printf '%s\n' "$G" >> "$LOG"
assert_contains "the provider's title reads Home" "_id=$GID, title=Home," "$G"
assert_absent "… and no group is titled Family any more" "title=Family," "$G"
assert_contains "group rename <id>: ok" "[people] group rename $GID: ok" "$(ring_since "$MARK")"

# ------------------------------------------------------------------------------------------------ delete
log "--- people_group_action:delete"
dump_ui "$D/group4.xml"
if [ "$(has_node "$D/group4.xml" people_group_action:delete)" != yes ]; then
  open_groups "$D/groups3.xml"; tap_node "$D/groups3.xml" "people_group:$GID"; sleep 2; dump_ui "$D/group4.xml"
fi
tap_node "$D/group4.xml" people_group_action:delete; sleep 2
dump_ui "$D/confirm.xml"; screencap "$D/confirm.png"
MARK="$(ring_mark)"
if [ "$(has_node "$D/confirm.xml" people_group_delete_confirm)" = yes ]; then
  record "Delete asks once before it deletes (built so; Change Log 2026-10-01, P2)" "people_group_delete_dialog shown; confirm tapped"
  tap_node "$D/confirm.xml" people_group_delete_confirm; sleep 3
fi
GL="$(groups_live)"; log "live groups after the delete: $(echo "$GL" | tr '\n' ';')"
assert_absent "the group row is gone (or marked deleted)" "_id=$GID," "$GL"
record "the group row after the delete (removed outright, or kept with deleted=1)" "$(q "content query --uri $GROUPS_URI --projection _id:title:deleted --where \"_id=${GID:-0}\"" | tr '\n' ' ')"
assert_eq "both contacts are still present" "2" "$(q "content query --uri $RAW --projection _id:deleted --where \"_id IN ($ANN,$BOB) AND deleted=0\"" | grep -c '_id=')"
assert_eq "… each still its own contact row" "2" "$(q "content query --uri $CONTACTS --projection _id:display_name --where \"_id IN ($(contact_of "$ANN"),$(contact_of "$BOB"))\"" | grep -c '_id=')"
assert_contains "group delete <id>: ok" "[people] group delete $GID: ok" "$(ring_since "$MARK")"

# ------------------------------------------------------------------------------------------------ WRITE_CONTACTS revoked
log "--- with WRITE_CONTACTS revoked: a create is refused with the editor's notice"
# A second group, made while the grant is held: the E21 producers below rename and delete it.
open_groups "$D/groups4.xml"
SPARE="$(new_group Spare)"; note "the spare group for the failed-op producers: _id=${SPARE:-none}"
ring_save
adb shell pm revoke app.tileshell android.permission.WRITE_CONTACTS; sleep 2
assert_eq "WRITE_CONTACTS is revoked" "false" "$(perm_granted WRITE_CONTACTS)"
ensure_start
GROUPS_BEFORE_DENIED="$(groups_q)"
open_groups "$D/r-groups.xml"
tap_node "$D/r-groups.xml" people_group_new; sleep 2
set_field people_group_name "Denied"
MARK="$(ring_mark)"
tapid people_group_save 3
dump_ui "$D/r-notice.xml"; screencap "$D/r-notice.png"
NOTICE="$(xml_text "$D/r-notice.xml" people_notice)"; log "people_notice: [$NOTICE]"
assert_eq "the create is refused with the notice (people_notice on the page)" "yes" "$(has_node "$D/r-notice.xml" people_notice)"
assert_contains "… the editor's cannot-save notice" "can't save" "$NOTICE"
FAILED_LINE="$(ring_since "$MARK" | grep -F '[people] group create' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$FAILED_LINE"
assert_contains "group create …: failed <err>" "[people] group create " "$FAILED_LINE"
assert_contains "… failed, with its reason" ": failed " "$FAILED_LINE"
assert_eq "no group was made: the groups query equals its read before the attempt" "$GROUPS_BEFORE_DENIED" "$(groups_q)"
back 1; back 1
if [ -n "$SPARE" ]; then
  log "--- E21 producers: rename and delete with WRITE_CONTACTS revoked"
  open_groups "$D/r-groups2.xml"; tap_node "$D/r-groups2.xml" "people_group:$SPARE"; sleep 2
  dump_ui "$D/r-spare.xml"
  if [ "$(has_node "$D/r-spare.xml" people_group_action:rename)" = yes ]; then
    tap_node "$D/r-spare.xml" people_group_action:rename; sleep 2
    set_field people_group_name "Spare2"
    MARK="$(ring_mark)"; tapid people_group_save 3
    assert_contains "E21: group rename <id>: failed <err>" "[people] group rename $SPARE: failed " "$(ring_since "$MARK")"
    assert_contains "… and the provider's title is unchanged" "_id=$SPARE, title=Spare," "$(groups_q)"
    back 1; back 1
    open_groups "$D/r-groups3.xml"; tap_node "$D/r-groups3.xml" "people_group:$SPARE"; sleep 2
    dump_ui "$D/r-spare2.xml"; tap_node "$D/r-spare2.xml" people_group_action:delete; sleep 2
    dump_ui "$D/r-confirm.xml"
    MARK="$(ring_mark)"
    [ "$(has_node "$D/r-confirm.xml" people_group_delete_confirm)" = yes ] && { tap_node "$D/r-confirm.xml" people_group_delete_confirm; sleep 3; }
    assert_contains "E21: group delete <id>: failed <err>" "[people] group delete $SPARE: failed " "$(ring_since "$MARK")"
    assert_contains "… and the group is still there" "_id=$SPARE, title=Spare," "$(groups_q)"
  else
    record "E21: rename / delete with WRITE_CONTACTS revoked" "the group page offers neither action while the grant is missing (r-spare.xml): the failed lines are not producible here"
  fi
fi

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back 1; back 1
ring_save
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "restore: WRITE_CONTACTS held" "true" "$(perm_granted WRITE_CONTACTS)"
for g in $(groups_q | sed -n 's/.*_id=\([0-9]*\),.*/\1/p'); do
  grep -q "_id=$g," "$D/groups-before.txt" || { q "content delete --uri '$GROUPS_URI/$g?$SA'" >/dev/null; note "restore: group $g deleted by id"; }
done
assert_eq "restore: the groups query equals its read before the row" "$(cat "$D/groups-before.txt")" "$(groups_q)"
people_fixtures_down
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
adb shell am force-stop "$HOLDER"
c6; ensure_start
row_end
