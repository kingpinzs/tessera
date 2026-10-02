#!/usr/bin/env bash
# DEV-VCARDPROBE (recorded, with one graded read): what the Contacts provider's vCard stream holds for the URI People
# shares. `adb shell content read` cannot open it on this image (DEV-E15), so the image's own Contacts app is handed
# the same URI (VIEW text/x-vcard, with a read grant) and what it imports is read back from the provider.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-VCARDPROBE "the provider's vCard stream for the shared URI, read back through an import"
log "$(ensure_build)"
BEFORE="$(raw_count)"
D="$ROW_DIR"
ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
L_ANN="$(lookup_of "$(contact_of $ANN)")"
URI="content://com.android.contacts/contacts/as_vcard/$L_ANN"
record "handlers of VIEW text/x-vcard" "$(S cmd package query-activities --brief -a android.intent.action.VIEW -t text/x-vcard | grep / | tr '\n' ' ')"
IMPORT="$(S cmd package query-activities --brief -a android.intent.action.VIEW -t text/x-vcard | grep '^ *com.android.contacts/' | head -1 | tr -d ' ')"
record "the image's Contacts import activity" "$IMPORT"
adb shell am start -W -n "$IMPORT" -a android.intent.action.VIEW -d "$URI" -t text/x-vcard --grant-read-uri-permission > "$D/am-start.txt" 2>&1
sleep 4
record "top activity after the VIEW" "$(top_activity)"
dump_ui "$D/import.xml"; screencap "$D/import.png"
record "what it shows" "$(grep -o 'text="[^"]\+"' "$D/import.xml" | tr '\n' ' ' | cut -c1-300)"
sleep 8
COPIES="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:display_name --where "\"display_name='Ann Lee' AND _id!=$ANN\"")"; log "imported: $COPIES"
COPY="$(echo "$COPIES" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
if [ -n "$COPY" ]; then
  echo "$COPIES" | grep -oE '_id=[0-9]+' | cut -d= -f2 >> "$(_fix_file)"
  CD="$(S content query --uri content://com.android.contacts/data --projection mimetype:data1 --where "raw_contact_id=$COPY")"; log "$CD"
  assert_contains "the stream carried her name (FN)" "mimetype=vnd.android.cursor.item/name, data1=Ann Lee" "$CD"
  assert_contains "her number (TEL)" "555" "$(echo "$CD" | grep phone_v2)"
  assert_contains "and her e-mail (EMAIL)" "data1=ann@example.com" "$CD"
else
  record "the stream's content" "not read back: no contact was imported"
fi
adb shell am force-stop com.android.contacts; sleep 1
people_fixtures_down
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
ensure_start
row_end
