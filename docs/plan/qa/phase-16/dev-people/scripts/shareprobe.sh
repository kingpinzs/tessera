#!/usr/bin/env bash
# DEV-SHAREPROBE (a recorded probe): what reads the Contacts provider's vCard URI on this image. DEV-E15 run 1 found
# `adb shell content read` refused it ("No files supported by provider": that command opens a plain file, and the
# Contacts provider serves a vCard as an asset stream). This reads what the URI answers to a query, what the share
# sheet offers, and — after one target is chosen — which URI grant Android records for it.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-SHAREPROBE "who can read the provider's vCard URI (recorded)"
log "$(ensure_build)"
BEFORE="$(raw_count)"
D="$ROW_DIR"
ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
ANN_C="$(contact_of $ANN)"; L_ANN="$(lookup_of "$ANN_C")"
URI="content://com.android.contacts/contacts/as_vcard/$L_ANN"
record "content query on the vCard URI" "$(S content query --uri "$URI" | head -3 | tr '\n' ' ')"
record "content read on the vCard URI" "$(adb shell content read --uri "$URI" 2>&1 | head -2 | tr -d '\r' | tr '\n' ' ' | cut -c1-200)"
adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" >/dev/null 2>&1; sleep 2
dump_ui "$D/card.xml"; tap_node "$D/card.xml" people_more; sleep 2
dump_ui "$D/menu.xml"; tap_node "$D/menu.xml" people_card_share; sleep 3
dump_ui "$D/chooser.xml"; screencap "$D/chooser.png"
record "the share sheet's entries" "$(grep -o 'text="[^"]\+"' "$D/chooser.xml" | tr '\n' ' ' | cut -c1-400)"
S dumpsys activity permissions > "$D/grants-before.txt" 2>/dev/null
record "URI grants naming as_vcard before a target is chosen" "$(grep -c 'as_vcard' "$D/grants-before.txt")"
# One target is chosen — the Notes fixture, which shows a shared text file's text — to read what the stream holds.
NB="$(python3 - "$D/chooser.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if 'text="Notes"' in s:
        l, t, r, b = (int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s).groups()); print((l + r) // 2, (t + b) // 2 - 60); break
PY
)"
# shellcheck disable=SC2086
adb shell input tap $NB; sleep 4
record "after choosing Notes: top activity" "$(top_activity)"
dump_ui "$D/notes.xml"; screencap "$D/notes.png"
record "what Notes shows" "$(grep -o 'text="[^"]\+"' "$D/notes.xml" | tr '\n' ' ' | cut -c1-600)"
S dumpsys activity permissions > "$D/grants-after.txt" 2>/dev/null
record "URI grants naming as_vcard after the choice" "$(grep -A2 'as_vcard' "$D/grants-after.txt" | tr '\n' ' ' | tr -s ' ' | cut -c1-400)"
adb shell input keyevent KEYCODE_BACK; sleep 1
record "after Back: top activity" "$(top_activity)"
dump_ui "$D/after-back.xml"; screencap "$D/after-back.png"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
people_fixtures_down
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
