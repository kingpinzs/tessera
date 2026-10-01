#!/usr/bin/env bash
# Read-only: the device state a dev-people session leaves (what is installed, the grants, the fixtures, People's stores).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
take_device_lock
echo "at $(date -Is)"
echo "installed apk: $(installed_apk_id); this build: $(apk_matches)"
for p in READ_CONTACTS WRITE_CONTACTS CALL_PHONE; do echo "$p granted=$(perm $p)"; done
echo "--- raw_contacts"; S content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id:account_name:account_type:deleted:display_name
echo "--- groups"; S content query --uri content://com.android.contacts/groups --projection _id:title:deleted
echo "--- aggregation_exceptions"; S content query --uri content://com.android.contacts/aggregation_exceptions --projection type:raw_contact_id1:raw_contact_id2
echo "--- icc/adn"; S content query --uri content://icc/adn
echo "--- people_edit.json: $(adb shell run-as app.tileshell cat files/people_edit.json 2>/dev/null | tr -d '\r')"
echo "--- people_filter.json: $(adb shell run-as app.tileshell cat files/people_filter.json 2>/dev/null | tr -d '\r')"
echo "--- slots: $(layout_json | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps(d.get("slots",{}), sort_keys=True))')"
echo "--- pushed pictures: $(S ls /sdcard/Pictures 2>/dev/null | grep -c people-)"
echo "--- calls: $(S dumpsys telecom | grep -c 'mForegroundCall: \[Call')  top: $(top_activity)  $(S dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*')"
