#!/usr/bin/env bash
# Phase 16 E15 — Share, SIM import, filter (the APK clause is the lead's), clause by clause from the phase doc's row.
#
#   fixtures   people_fixtures_up
#   share      from a MARK, Share on Ann → `dumpsys activity activities` shows Android's resolver for ACTION_SEND
#              text/x-vcard; the slice holds `[people] share <Ann's lookup>: <uri>` — the Contacts provider's own
#              vCard stream, content://com.android.contacts/contacts/as_vcard/<lookup> (r3 D10)
#   stream     the doc's `adb shell content read --uri <uri>` is REFUSED on this image (Change Log 2026-10-01;
#              clauses-open.tsv): its output is recorded, and the stream is read back another way — `content query`
#              on the logged URI, and the same URI handed to the image's Contacts app (VIEW text/x-vcard with a read
#              grant), whose import is read from the provider: FN Ann Lee, her TEL, her e-mail. The imported contact
#              is deleted by id.
#   SIM        `content insert --uri content://icc/adn …`, then the host's `content query` decides the branch
#              (recorded); People's Import from SIM is run in whichever branch it is (V16): accepted → people_sim_row:
#              "Sim Bob" → import → a contact with that number under a phone-only raw contact, `sim import: 1 of 1`;
#              refused → no people_sim_row:, `sim import: 0 of 0`
#   filter     a raw contact with account_type com.example / account_name x shows an account group in "filter contact
#              list"; unticking it hides that contact and the row count drops by one; re-ticked
#   restore    people_fixtures_down, the com.example raw contact and the imported contacts deleted by id, the SIM
#              entry deleted (recorded if the SIM refuses)
#   APK        `stat -c%s` / `unzip -l`: the lead's clause, left out here and said so with a record
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
ADN=content://icc/adn
digits() { printf '%s' "$1" | tr -cd '0-9'; }
filter_json() { adb shell run-as app.tileshell cat files/people_filter.json 2>/dev/null | tr -d '\r'; }
list_rows() { # prefix -> the list opened fresh and walked: its row count
  open_people -a android.intent.action.MAIN; list_top
  list_walk "$1" > "$1-merged.tsv"
  grep -c '^row' "$1-merged.tsv"
}

row_begin E15 "Share (the provider's vCard stream), SIM import, filter contact list"
require_build
D="$ROW_DIR"
ensure_start
record "the APK clause (size delta ≤ 2 MB, no new entry ≥ 1 MB)" "NOT asserted here: it is the lead's clause (QA brief; Change Log 2026-10-01 puts the measured delta to the owner)"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"
q "content query --uri $ADN" > "$D/adn-before.txt" 2>&1
note "icc/adn before the row: $(tr '\n' ';' < "$D/adn-before.txt" | cut -c1-300)"
FILTER0="$(filter_json)"; note "people_filter.json before the row: [$FILTER0]"

log "--- fixtures"
people_fixtures_up
ANN_C="$(contact_of "$ANN")"; L_ANN="$(lookup_of "$ANN_C")"
note "Ann: raw $ANN, contact $ANN_C, lookup $L_ANN"

# ------------------------------------------------------------------------------------------------ Share
log "--- Share on Ann"
card_of "$ANN" "$D/card.xml"
assert_eq "Ann's card (people_card:<lookup>)" "yes" "$(has_node "$D/card.xml" "people_card:$L_ANN")"
tap_node "$D/card.xml" people_more; sleep 2
dump_ui "$D/menu.xml"; screencap "$D/menu.png"
assert_eq "the card's … menu offers Share (people_card_share)" "yes" "$(has_node "$D/menu.xml" people_card_share)"
MARK="$(ring_mark)"
tap_node "$D/menu.xml" people_card_share; sleep 4
adb shell dumpsys activity activities | tr -d '\r' > "$D/activities-share.txt"
TOP="$(top_activity)"; log "resumed after Share: $TOP"
assert_contains "dumpsys activity activities: Android's resolver (its chooser) is resumed" "com.android.intentresolver/" "$TOP"
CH="$(grep -m1 -E 'Intent \{ act=android.intent.action.CHOOSER' "$D/activities-share.txt" | sed 's/^ *//' | cut -c1-240)"; log "its intent: $CH"
assert_contains "… started with ACTION_CHOOSER (the resolver for an ACTION_SEND)" "act=android.intent.action.CHOOSER" "$CH"
dump_ui "$D/chooser.xml"; screencap "$D/chooser.png"
assert_contains "… and what it offers to send is Ann's vCard (\"Ann Lee.vcf\")" 'Ann Lee.vcf' "$(cat "$D/chooser.xml")"
SEND_HANDLERS="$(adb shell cmd package query-activities --brief -a android.intent.action.SEND -t text/x-vcard | tr -d '\r' | grep '/' | tr -d ' ' | tr '\n' ' ')"
record "the apps that receive ACTION_SEND text/x-vcard on this image" "$SEND_HANDLERS"
SLICE="$(ring_since "$MARK")"
LINE="$(printf '%s\n' "$SLICE" | grep -F '[people] share' | tail -1 | sed 's/.*\[people\] //')"; log "[people] $LINE"
URI="$(printf '%s' "$LINE" | sed -n 's/^share [^ ]*: \(content:[^ ]*\).*/\1/p')"
assert_eq "the slice holds [people] share <Ann's lookup>: <uri>" "share $L_ANN: $URI" "$LINE"
assert_eq "… the Contacts provider's own vCard stream URI (as_vcard/<lookup>)" "content://com.android.contacts/contacts/as_vcard/$L_ANN" "$URI"
# r3 D10 / Trust (f): a read grant for that one URI, from the Contacts provider; no provider or file of the shell's.
adb shell dumpsys activity permissions | tr -d '\r' > "$D/grants.txt"
note "URI grants naming a vCard: $(grep -A2 'as_vcard' "$D/grants.txt" | tr '\n' ' ' | tr -s ' ' | cut -c1-400)"
assert_eq "one URI grant names a vCard URI" "1" "$(grep -c 'as_vcard' "$D/grants.txt")"
assert_contains "… and it is that one URI" "as_vcard/$L_ANN" "$(grep 'as_vcard' "$D/grants.txt")"
assert_contains "… read only (mode 0x1), sourced by the Contacts provider" "sourcePkg=com.android.providers.contacts" "$(grep -A2 'as_vcard' "$D/grants.txt" | tr '\n' ' ')"
assert_contains "… mode 0x1" "mode=0x1" "$(grep -A2 'as_vcard' "$D/grants.txt" | tr '\n' ' ')"
assert_eq "no file of the shell's holds a vCard (files/, cache/)" "0" "$(adb shell run-as app.tileshell sh -c 'ls -R files cache 2>/dev/null' | grep -ci 'vcf\|vcard')"
back 2

# ------------------------------------------------------------------------------------------------ the stream's content
log "--- the stream of the logged URI"
READ="$(adb shell content read --uri "$URI" 2>&1 | tr -d '\r' | head -c 400)"
printf '%s\n' "$READ" > "$D/content-read.txt"
record "adb shell content read --uri <the logged uri> (the doc's command)" "$(printf '%s' "$READ" | head -2 | tr '\n' ' ' | cut -c1-200)"
if printf '%s' "$READ" | grep -q '^BEGIN:VCARD'; then
  # The image serves it after all: the doc's clause, as written.
  assert_contains "content read: text beginning BEGIN:VCARD" "BEGIN:VCARD" "$(printf '%s' "$READ" | head -1)"
  assert_contains "content read: FN:Ann Lee" "FN:Ann Lee" "$READ"
  assert_contains "content read: her TEL" "TEL" "$READ"
else
  note "content read is refused on this image (clauses-open.tsv, E15): the stream is read back through content query and an import"
fi
QROW="$(q "content query --uri $URI")"; log "content query on the logged URI: $QROW"
assert_contains "content query on the logged URI: the provider answers with her vCard file" "_display_name=Ann Lee.vcf" "$QROW"
GTYPE="$(q "content gettype --uri $URI")"; log "content gettype on the logged URI: $GTYPE"
assert_contains "content gettype on the logged URI: what is shared is text/x-vcard" "text/x-vcard" "$GTYPE"
IMPORT="$(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -t text/x-vcard | tr -d '\r' | grep '^ *com.android.contacts/' | head -1 | tr -d ' ')"
record "the image's Contacts import activity (VIEW text/x-vcard)" "${IMPORT:-none}"
ANN_RAWS_BEFORE="$(q "content query --uri $RAW --projection _id --where \"display_name='Ann Lee' AND deleted=0\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tr '\n' ' ')"
COPY=""
if [ -n "$IMPORT" ]; then
  adb shell am start -W -n "$IMPORT" -a android.intent.action.VIEW -d "$URI" -t text/x-vcard --grant-read-uri-permission > "$D/import-am.txt" 2>&1
  sleep 4
  record "after the VIEW: top activity" "$(top_activity)"
  dump_ui "$D/import.xml"; screencap "$D/import.png"
  record "what the import shows" "$(grep -o 'text="[^"]\+"' "$D/import.xml" | tr '\n' ' ' | cut -c1-300)"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    COPY="$(q "content query --uri $RAW --projection _id --where \"display_name='Ann Lee' AND deleted=0 AND _id!=$ANN\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tail -1)"
    [ -n "$COPY" ] && break
    sleep 2
  done
fi
assert_ne "the image's Contacts app imported a contact from the stream" "" "$COPY"
if [ -n "$COPY" ]; then
  q "content query --uri $RAW --projection _id --where \"display_name='Ann Lee' AND deleted=0 AND _id!=$ANN\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' >> "$ROW_DIR/people-fixtures.ids"
  CD="$(q "content query --uri $DATA --projection mimetype:data1 --where \"raw_contact_id=$COPY\"")"; printf '%s\n' "$CD" > "$D/imported-data.txt"; printf '%s\n' "$CD" >> "$LOG"
  assert_contains "the stream carried her name (FN:Ann Lee)" "mimetype=vnd.android.cursor.item/name, data1=Ann Lee" "$CD"
  TEL="$(printf '%s\n' "$CD" | sed -n 's/.*phone_v2, data1=\(.*\)$/\1/p' | head -1)"
  assert_eq "the stream carried her TEL (the digits of +1 555 000 0001)" "15550000001" "$(digits "$TEL")"
  assert_contains "… and her e-mail" "data1=ann@example.com" "$CD"
fi
adb shell am force-stop com.android.contacts; sleep 1

# ------------------------------------------------------------------------------------------------ SIM
log "--- SIM: the insert, the branch, Import from SIM"
q "content insert --uri $ADN --bind tag:s:'Sim Bob' --bind number:s:5550002" > "$D/sim-insert.txt" 2>&1
SIMQ="$(q "content query --uri $ADN")"; printf '%s\n' "$SIMQ" > "$D/adn-after-insert.txt"; log "icc/adn: $(echo "$SIMQ" | tr '\n' ';' | cut -c1-300)"
if printf '%s' "$SIMQ" | grep -q '5550002'; then BRANCH=accepted; else BRANCH=refused; fi
record "the emulated SIM's phonebook took the insert (decides the branch)" "$BRANCH — insert said: $(head -c 160 "$D/sim-insert.txt" | tr '\n' ' ')"
SIM_N="$(printf '%s\n' "$SIMQ" | grep -c 'Row:')"
open_people_settings sim "$D/sim.xml"; screencap "$D/sim.png"
assert_contains "People's Import from SIM page is on show (people_page:sim)" 'selected="true"' "$(node_tag "$D/sim.xml" people_page:sim)"
note "people_sim_row nodes: $(ids_with_prefix "$D/sim.xml" people_sim_row: | tr '\n' ' ')"
if [ "$BRANCH" = accepted ]; then
  SIMROWS="$(python3 - "$D/sim.xml" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
print("|".join(html.unescape(re.search(r'text="([^"]*)"', m.group(0)).group(1)) for m in re.finditer(r'<node[^>]*resource-id="people_sim_row:[^"]*"[^>]*>', xml)))
PY
)"
  assert_contains "accepted: the page lists a people_sim_row: reading \"Sim Bob\"" "Sim Bob" "$SIMROWS"
  assert_eq "accepted: one row per SIM entry the host's query lists" "$SIM_N" "$(count_ids "$D/sim.xml" people_sim_row:)"
  MARK="$(ring_mark)"
  tap_node "$D/sim.xml" people_sim_import; sleep 5
  SIMLINE="$(ring_since "$MARK" | grep -F '[people] sim import' | tail -1 | sed 's/.*\[people\] //')"; log "[people] $SIMLINE"
  assert_eq "accepted: diagnostics [people] sim import: n of n for the SIM's $SIM_N entry" "sim import: $SIM_N of $SIM_N" "$SIMLINE"
  [ "$SIM_N" = 1 ] && assert_eq "accepted: … which is 1 of 1" "sim import: 1 of 1" "$SIMLINE"
  BOBROW="$(q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted --where \"display_name='Sim Bob' AND deleted=0\"")"; log "$BOBROW"
  SIMBOB="$(printf '%s\n' "$BOBROW" | sed -n 's/.*Row: [0-9]* _id=\([0-9]*\),.*/\1/p' | head -1)"   # the raw contact's own _id (not contact_id)
  [ -n "$SIMBOB" ] && printf '%s\n' "$BOBROW" | sed -n 's/.*Row: [0-9]* _id=\([0-9]*\),.*/\1/p' >> "$ROW_DIR/people-fixtures.ids"
  assert_eq "accepted: one raw contact was imported" "1" "$(printf '%s\n' "$BOBROW" | grep -c '_id=')"
  assert_contains "accepted: … phone-only (NULL account; Q-16-3)" "account_name=NULL, account_type=NULL" "$BOBROW"
  assert_contains "accepted: … with the SIM's number" "data1=5550002" "$(data_rows "${SIMBOB:-0}")"
  assert_eq "accepted: a contact row exists for it" "1" "$(q "content query --uri $CONTACTS --projection _id --where \"display_name='Sim Bob'\"" | grep -c '_id=')"
else
  assert_eq "refused: the page lists no people_sim_row:" "0" "$(count_ids "$D/sim.xml" people_sim_row:)"
  MARK="$(ring_mark)"
  tap_node "$D/sim.xml" people_sim_import; sleep 4
  SIMLINE="$(ring_since "$MARK" | grep -F '[people] sim import' | tail -1 | sed 's/.*\[people\] //')"; log "[people] $SIMLINE"
  assert_eq "refused: the slice holds [people] sim import: 0 of 0" "sim import: 0 of 0" "$SIMLINE"
  record "the import of a stored SIM contact" "P4's (the emulated SIM refused the insert)"
fi
# The SIM entry deleted (the restore's command), then — as E21's producer of the other alternative — Import from SIM run
# again on the SIM as it then is.
SIMDEL="$(q "content delete --uri $ADN --where \"tag='Sim Bob' AND number='5550002'\"" | head -c 200)"
record "the SIM entry's delete (content delete on icc/adn)" "${SIMDEL:-no output (deleted)}"
SIMQ2="$(q "content query --uri $ADN")"
if [ "$BRANCH" = accepted ] && ! printf '%s' "$SIMQ2" | grep -q 'Row:'; then
  log "--- E21 producer: Import from SIM with nothing on the SIM"
  back 1; back 1; back 1
  open_people_settings sim "$D/sim-empty.xml"; screencap "$D/sim-empty.png"
  assert_eq "with the SIM emptied the page lists no people_sim_row:" "0" "$(count_ids "$D/sim-empty.xml" people_sim_row:)"
  MARK="$(ring_mark)"
  if tap_node "$D/sim-empty.xml" people_sim_import; then sleep 4; fi
  SIMLINE0="$(ring_since "$MARK" | grep -F '[people] sim import' | tail -1 | sed 's/.*\[people\] //')"; log "[people] $SIMLINE0"
  assert_eq "E21: [people] sim import: 0 of 0" "sim import: 0 of 0" "$SIMLINE0"
fi
back 1; back 1; back 1

# ------------------------------------------------------------------------------------------------ filter
log "--- filter contact list: an account group, unticked, hides its contact"
XENA="$(people_add 'Xena Example' '' '' x com.example)"; sleep 2
assert_contains "the raw contact is in account x / com.example" "account_name=x, account_type=com.example" "$(raw_row "$XENA")"
L_X="$(lookup_of "$(contact_of "$XENA")")"
N1="$(list_rows "$D/walk-before")"
assert_eq "before the filter: the list holds one row per contact in the provider" "$(contacts_count)" "$N1"
assert_eq "before the filter: Xena's row is in the list" "1" "$(awk -F'\t' -v k="$L_X" '$1=="row" && $2==k' "$D/walk-before-merged.tsv" | grep -c .)"
open_people_settings filter "$D/filter.xml"; screencap "$D/filter.png"
assert_contains "the \"filter contact list\" page is on show" 'selected="true"' "$(node_tag "$D/filter.xml" people_page:filter)"
assert_eq "it shows an account group for com.example / x (people_filter_account:com.example:x)" "yes" "$(has_node "$D/filter.xml" people_filter_account:com.example:x)"
assert_contains "… ticked (its contacts are shown)" 'checked="true"' "$(node_tag "$D/filter.xml" people_filter_account:com.example:x)"
tap_node "$D/filter.xml" people_filter_account:com.example:x; sleep 2
dump_ui "$D/filter2.xml"
assert_contains "unticked" 'checked="false"' "$(node_tag "$D/filter2.xml" people_filter_account:com.example:x)"
back 1; back 1
M2="$(ring_mark)"
N2="$(list_rows "$D/walk-filtered")"; screencap "$D/list-filtered.png"
assert_eq "the row count drops by one" "$((N1 - 1))" "$N2"
assert_eq "… and it is that contact that is hidden" "0" "$(awk -F'\t' -v k="$L_X" '$1=="row" && $2==k' "$D/walk-filtered-merged.tsv" | grep -c .)"
assert_contains "the filter is the shell's own state: her raw contact is as it was in the provider" "account_name=x, account_type=com.example" "$(raw_row "$XENA")"
note "list line while filtered: $(people_list_line "$M2")"
open_people_settings filter "$D/filter3.xml"
tap_node "$D/filter3.xml" people_filter_account:com.example:x; sleep 2
dump_ui "$D/filter4.xml"
assert_contains "re-ticked" 'checked="true"' "$(node_tag "$D/filter4.xml" people_filter_account:com.example:x)"
back 1; back 1
N3="$(list_rows "$D/walk-reticked")"
assert_eq "re-ticked: the row count is back" "$N1" "$N3"

# ------------------------------------------------------------------------------------------------ restore
log "--- restore"
back 1
people_fixtures_down
assert_eq "restore: no Ann Lee, Sim Bob or Xena Example raw contact is left" "0" "$(q "content query --uri $RAW --projection _id:display_name --where \"display_name IN ('Ann Lee','Sim Bob','Xena Example')\"" | grep -c '_id=')"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: the raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
q "content delete --uri $ADN --where \"tag='Sim Bob' AND number='5550002'\"" >/dev/null 2>&1
q "content query --uri $ADN" > "$D/adn-after.txt" 2>&1
assert_eq "restore: icc/adn equals its read before the row" "$(cat "$D/adn-before.txt")" "$(cat "$D/adn-after.txt")"
assert_eq "restore: people_filter.json hides what it hid before the row" "$(printf '%s' "$FILTER0" | python3 -c 'import json,sys
try: d=json.load(sys.stdin)
except Exception: d={}
print(sorted(map(str, d.get("hiddenAccounts", []))), d.get("hidePhoneless", d.get("hideWithoutPhones", False)))')" "$(filter_json | python3 -c 'import json,sys
try: d=json.load(sys.stdin)
except Exception: d={}
print(sorted(map(str, d.get("hiddenAccounts", []))), d.get("hidePhoneless", d.get("hideWithoutPhones", False)))')"
note "people_filter.json after the row: [$(filter_json)]"
adb shell am force-stop com.android.contacts
c6; ensure_start
row_end
