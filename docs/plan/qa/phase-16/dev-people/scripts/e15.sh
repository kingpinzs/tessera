#!/usr/bin/env bash
# DEV-E15 (development proof, E15's core): Share — the logged URI read with `content read` — Import from SIM in
# whichever branch the emulated SIM gives, and "filter contact list" on a com.example account.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E15 "Share, SIM import, filter (E15's core)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
BEFORE="$(raw_count)"
D="$ROW_DIR"
list_n() { ring_since "$1" | grep -F '[people] list:' | tail -1 | sed -E 's/.*list: ([0-9]+) contacts.*/\1/'; }

ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
ANN_C="$(contact_of $ANN)"; L_ANN="$(lookup_of "$ANN_C")"

# ---- Share: ACTION_SEND text/x-vcard, the Contacts provider's own stream
adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" >/dev/null 2>&1; sleep 2
dump_ui "$D/card.xml"; tap_node "$D/card.xml" people_more; sleep 2
dump_ui "$D/menu.xml"; screencap "$D/menu.png"
assert_eq "the card's … menu offers share contact" "yes" "$(has_node "$D/menu.xml" people_card_share)"
MARK="$(ring_mark)"
tap_node "$D/menu.xml" people_card_share; sleep 3
TOP="$(top_activity)"; record "what ACTION_SEND text/x-vcard reached" "$TOP"
SEND="$(S dumpsys activity activities | grep -m1 -E 'act=android.intent.action.(SEND|CHOOSER)' )"; log "intent on top: ${SEND:0:240}"
assert_contains "Android's share sheet is on top" "hooser" "$TOP$SEND"
LINE="$(ring_since "$MARK" | grep -F '[people] share' | tail -1 | sed 's/.*\[people\] //')"; log "$LINE"
URI="content://com.android.contacts/contacts/as_vcard/$L_ANN"
assert_eq "the share line names the provider's own vCard URI" "share $L_ANN: $URI" "$LINE"
dump_ui "$D/chooser.xml"; screencap "$D/chooser.png"
assert_contains "the share sheet names the provider's file for her" 'text="Ann Lee.vcf"' "$(cat "$D/chooser.xml")"
assert_contains "the provider answers for that URI" "_display_name=Ann Lee.vcf" "$(S content query --uri "$URI")"
# `adb shell content read` opens a plain file, and the Contacts provider serves a vCard as an asset stream: on this
# image it is refused ("No files supported by provider"). Recorded; the stream is read below by an app that receives it.
record "adb shell content read on the URI" "$(adb shell content read --uri "$URI" 2>&1 | head -2 | tr -d '\r' | tr '\n' ' ' | cut -c1-150)"
S dumpsys activity permissions > "$D/grants-sheet.txt" 2>/dev/null
G="$(grep -A2 'as_vcard' "$D/grants-sheet.txt" | tr '\n' ' ' | tr -s ' ')"; log "URI grants: $G"
assert_eq "one URI grant names a vCard URI" "1" "$(grep -c 'as_vcard' "$D/grants-sheet.txt")"
assert_contains "it is for that one URI" "UriPermission{" "$(grep "as_vcard/$L_ANN " "$D/grants-sheet.txt")"
assert_contains "read only (mode 0x1), from the Contacts provider, to the share sheet" "sourcePkg=com.android.providers.contacts targetPkg=com.android.intentresolver mode=0x1" "$G"
assert_eq "no file of the shell's holds a vCard" "0" "$(adb shell run-as app.tileshell sh -c 'ls -R files cache 2>/dev/null' | grep -ci 'vcf\|vcard')"
# What the stream holds: the image's Contacts app is chosen on the sheet; it imports the vCard it is handed, so the
# contact it makes is the stream's content read back from the provider.
NB="$(python3 - "$D/chooser.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if 'text="Contacts"' in s:
        l, t, r, b = (int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s).groups()); print((l + r) // 2, (t + b) // 2 - 60); break
PY
)"
# shellcheck disable=SC2086
adb shell input tap $NB; sleep 5
record "after choosing Contacts on the sheet: top activity" "$(top_activity)"
dump_ui "$D/import.xml"; screencap "$D/import.png"
record "what it shows" "$(grep -o 'text="[^"]\+"' "$D/import.xml" | tr '\n' ' ' | cut -c1-300)"
S dumpsys activity permissions > "$D/grants-target.txt" 2>/dev/null
record "the URI grant after the choice" "$(grep -A2 'as_vcard' "$D/grants-target.txt" | tr '\n' ' ' | tr -s ' ' | cut -c1-300)"
sleep 6
COPIES="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name --where "\"display_name='Ann Lee' AND _id!=$ANN\"")"; log "imported: $COPIES"
COPY="$(echo "$COPIES" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
if [ -n "$COPY" ]; then
  echo "$COPIES" | grep -oE '_id=[0-9]+' | cut -d= -f2 >> "$(_fix_file)"
  CD="$(S content query --uri content://com.android.contacts/data --projection mimetype:data1 --where "raw_contact_id=$COPY")"; log "$CD"
  assert_contains "the stream carried her name (FN)" "mimetype=vnd.android.cursor.item/name, data1=Ann Lee" "$CD"
  assert_contains "her number (TEL)" "mimetype=vnd.android.cursor.item/phone_v2, data1=" "$CD"
  assert_contains "the number's digits" "555" "$(echo "$CD" | grep phone_v2)"
  assert_contains "and her e-mail (EMAIL)" "data1=ann@example.com" "$CD"
else
  record "the stream's content" "not read back: the app chosen on the sheet made no contact from it"
fi
adb shell am force-stop com.android.contacts; adb shell am force-stop org.fossify.contacts; sleep 1

# ---- Import from SIM: run in whichever branch the emulated SIM gives (V16)
adb shell "content insert --uri content://icc/adn --bind tag:s:'Sim Bob' --bind number:s:5550002" > "$D/sim-insert.txt" 2>&1
SIMQ="$(S content query --uri content://icc/adn)"; log "icc/adn: $SIMQ"
if echo "$SIMQ" | grep -q "5550002"; then BRANCH=accepted; else BRANCH=refused; fi
record "the emulated SIM's phonebook took the insert" "$BRANCH ($(head -c 160 "$D/sim-insert.txt" | tr '\n' ' '))"
open_people -a android.intent.action.MAIN
dump_ui "$D/l.xml"; tap_node "$D/l.xml" people_more; sleep 2
dump_ui "$D/m.xml"; tap_node "$D/m.xml" people_more:settings; sleep 2
dump_ui "$D/settings.xml"
assert_eq "People's settings page" "yes" "$(has_node "$D/settings.xml" people_page:settings)"
tap_node "$D/settings.xml" people_settings:sim; sleep 3
dump_ui "$D/sim.xml"; screencap "$D/sim.png"
assert_eq "the SIM import page" "yes" "$(has_node "$D/sim.xml" people_page:sim)"
MARK="$(ring_mark)"
tap_node "$D/sim.xml" people_sim_import; sleep 4
SIMLINE="$(ring_since "$MARK" | grep -F '[people] sim import' | tail -1 | sed 's/.*\[people\] //')"; log "$SIMLINE"
if [ "$BRANCH" = accepted ]; then
  assert_eq "the page lists the SIM's entry" "Sim Bob" "$(node_text "$D/sim.xml" people_sim_row:0)"
  assert_eq "the import line" "sim import: 1 of 1" "$SIMLINE"
  BOBROW="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name --where "\"display_name='Sim Bob'\"")"; log "$BOBROW"
  BOB="$(echo "$BOBROW" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"; [ -n "$BOB" ] && echo "$BOB" >> "$(_fix_file)"
  assert_contains "the imported contact is on the phone (NULL account)" "account_name=NULL, account_type=NULL" "$BOBROW"
  assert_contains "with the SIM's number" "data1=5550002" "$(S content query --uri content://com.android.contacts/data --projection mimetype:data1 --where "raw_contact_id=$BOB")"
else
  assert_eq "the page lists no SIM row" "0" "$(grep -o 'resource-id="people_sim_row:[^"]*"' "$D/sim.xml" | wc -l)"
  assert_eq "the import line (nothing on the SIM)" "sim import: 0 of 0" "$SIMLINE"
fi
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- filter contact list: an account group, unticked, hides its contact
X=$(fx_raw x com.example); fx_name $X "Xena Example"; L_X="$(lookup_of "$(contact_of $X)")"
sleep 2
dump_ui "$D/settings2.xml"
MARK="$(ring_mark)"
tap_node "$D/settings2.xml" people_settings:filter; sleep 2
dump_ui "$D/filter.xml"; screencap "$D/filter.png"
assert_eq "the filter page" "yes" "$(has_node "$D/filter.xml" people_page:filter)"
ROWX="$(grep -o '<node[^>]*resource-id="people_filter_account:com.example:x"[^>]*>' "$D/filter.xml")"
assert_contains "the com.example account is listed, ticked" 'checked="true"' "$ROWX"
assert_eq "the phone is listed too" "yes" "$(has_node "$D/filter.xml" people_filter_account:phone)"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
MARK="$(ring_mark)"; open_people -a android.intent.action.MAIN; N1="$(list_n "$MARK")"
dump_ui "$D/list1.xml"
assert_eq "Xena is listed before the filter" "yes" "$(has_node "$D/list1.xml" "people_row:$L_X")"
tap_node "$D/list1.xml" people_more; sleep 2; dump_ui "$D/m2.xml"; tap_node "$D/m2.xml" people_more:settings; sleep 2
dump_ui "$D/s3.xml"; tap_node "$D/s3.xml" people_settings:filter; sleep 2
dump_ui "$D/filter2.xml"
MARK="$(ring_mark)"
tap_node "$D/filter2.xml" people_filter_account:com.example:x; sleep 2
dump_ui "$D/filter3.xml"
assert_contains "unticked" 'checked="false"' "$(grep -o '<node[^>]*resource-id="people_filter_account:com.example:x"[^>]*>' "$D/filter3.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
dump_ui "$D/list2.xml"; screencap "$D/list2.png"
N2="$(list_n "$MARK")"; log "listed before $N1, after the untick $N2"
assert_eq "the row count drops by one" "$((N1 - 1))" "$N2"
assert_eq "Xena's row is hidden" "no" "$(has_node "$D/list2.xml" "people_row:$L_X")"
assert_eq "the list says a filter is on (P1.4's caption)" "yes" "$(has_node "$D/list2.xml" people_filter_caption)"
assert_contains "nothing was written to the provider: her raw contact is as it was" "account_name=x, account_type=com.example" "$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:deleted --where "_id=$X")"
tap_node "$D/list2.xml" people_more; sleep 2; dump_ui "$D/m3.xml"; tap_node "$D/m3.xml" people_more:settings; sleep 2
dump_ui "$D/s4.xml"; tap_node "$D/s4.xml" people_settings:filter; sleep 2
dump_ui "$D/filter4.xml"
MARK="$(ring_mark)"
tap_node "$D/filter4.xml" people_filter_account:com.example:x; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "re-ticked: the row count is back" "$N1" "$(list_n "$MARK")"

# ---- restore
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
SIMDEL="$(adb shell "content delete --uri content://icc/adn --where \"tag='Sim Bob' AND number='5550002'\"" 2>&1 | tr -d '\r' | head -c 160)"
record "the SIM entry's delete" "${SIMDEL:-ok}"
FJ="$(adb shell run-as app.tileshell cat files/people_filter.json 2>/dev/null | tr -d '\r')"; log "people_filter.json: $FJ"
assert_contains "the filter is back to hiding no account" '"hiddenAccounts":[]' "$FJ"
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
