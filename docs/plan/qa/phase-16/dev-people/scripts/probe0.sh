#!/usr/bin/env bash
# Read-only probe of the emulator before anything is built: what the Contacts provider and the image hold.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
take_device_lock
echo "at $(date -Is)"
echo "installed apk: $(installed_apk_id); ours matches: $(apk_matches)"
echo "--- raw_contacts"; S content query --uri content://com.android.contacts/raw_contacts --projection _id:contact_id:account_name:account_type:deleted:display_name
echo "--- contacts"; S content query --uri content://com.android.contacts/contacts --projection _id:lookup:display_name:phonebook_label:sort_key:display_name_source:has_phone_number:photo_id
echo "--- groups"; S content query --uri content://com.android.contacts/groups --projection _id:title:account_name:account_type:deleted
echo "--- icc/adn"; S content query --uri content://icc/adn
echo "--- sms role"; S cmd role get-role-holders android.app.role.SMS
echo "--- dialer role"; S cmd role get-role-holders android.app.role.DIALER
echo "--- packages"; S pm list packages | grep -iE "k9|fsck|osmand|organicmaps|fossify|contacts|testdpc|auxio|calendar|photopicker|providers.media"
echo "--- layout slots"; adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys; d=json.load(sys.stdin); print(json.dumps(d.get("slots",{}),indent=1)); print(sorted(d.get("addedOnce",[])))'
echo "--- perms"; for p in READ_CONTACTS WRITE_CONTACTS CALL_PHONE; do echo "$p $(perm $p)"; done
echo "--- users"; S pm list users
echo "--- top"; top_activity
echo "--- ime"; S settings get secure default_input_method
