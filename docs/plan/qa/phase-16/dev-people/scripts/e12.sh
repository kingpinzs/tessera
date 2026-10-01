#!/usr/bin/env bash
# DEV-E12 (development proof, E12's core): the card's four actions — Call, Text, Mail, Address — each with its
# `[people] action <kind> -> <component> <uri>` line. The Mail and Maps slots are pointed at the K-9 and OsmAnd fixtures
# for the row and put back; the call it places is cancelled.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/people.sh"
row_begin DEV-E12 "card actions: call, text, mail, map (E12's core)"
log "$(ensure_build)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
BEFORE="$(raw_count)"
D="$ROW_DIR"

ANN=$(fx_raw); fx_name $ANN "Ann Lee"; fx_phone $ANN "+1 555 000 0001"; fx_email $ANN "ann@example.com"
fx_data $ANN vnd.android.cursor.item/postal-address_v2 "1 Main St Springfield" 1
ANN_C="$(contact_of $ANN)"; L_ANN="$(lookup_of "$ANN_C")"

# The Mail and Maps slots, pointed at the fixtures the way layout_restore writes a file; the layout is put back at the end.
layout_save "$D/layout-before.json"
full() { local c; c="$(S cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$1" | tail -1)"; python3 -c "
import sys
p,c=sys.argv[1].split('/'); print(p+'/'+(p+c if c.startswith('.') else c))" "$c"; }
K9="$(full com.fsck.k9)"; OSM="$(full net.osmand.plus)"
log "K-9 launcher $K9, OsmAnd launcher $OSM"
python3 - "$D/layout-before.json" "$D/layout-slots.json" "$K9" "$OSM" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
d.setdefault("slots", {})["MAIL"] = sys.argv[3]
d["slots"]["MAPS"] = sys.argv[4]
json.dump(d, open(sys.argv[2], "w"))
PY
layout_restore "$D/layout-slots.json"; assert_eq "the slots were written" "0" "$?"
SLOTS="$(layout_json | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin).get("slots",{}), sort_keys=True))')"; log "slots: $SLOTS"
short() { python3 -c "
import sys
p,c=sys.argv[1].split('/'); print(p+'/'+(c[len(p):] if c.startswith(p+'.') else c))" "$1"; }

card() { adb shell am start -W -n "$PEOPLE" -a android.intent.action.VIEW -d "content://com.android.contacts/contacts/$ANN_C" >/dev/null 2>&1; sleep 2; dump_ui "$D/card.xml"; }
card; screencap "$D/card.png"
assert_eq "the VIEW intent opens Ann's card" "yes" "$(has_node "$D/card.xml" "people_card:$L_ANN")"
log "action rows: $(grep -o 'resource-id="people_card_action:[^"]*"' "$D/card.xml" | tr '\n' ' ')"
assert_eq "the card is a full accent page" "0,120,215" "$(px "$D/card.png" 1060 $(( $(status_bottom "$D/card.xml") + 6 )))"

# ---- Call: through Telecom
MARK="$(ring_mark)"
tap_node "$D/card.xml" people_card_action:call:0; sleep 4
SLICE="$(ring_since "$MARK")"; log "$(echo "$SLICE" | grep -F '[people] action')"
assert_contains "the call line" "[people] action call -> " "$SLICE"
assert_contains "the call line names the number" "tel:+1 555 000 0001" "$SLICE"
GSM="$(adb emu gsm list | tr -d '\r')"; log "gsm list: $GSM"
assert_contains "the emulator's modem lists the outgoing call" "outbound to" "$GSM"
TEL="$(S dumpsys telecom | grep -m3 -iE 'Call TC@|state=|handle' | tr '\n' ' ')"; log "telecom: ${TEL:0:300}"
assert_contains "Telecom holds a call" "Call TC@" "$TEL"
NUMBER="$(echo "$GSM" | grep -oE 'to +[0-9+]+' | head -1 | awk '{print $2}')"
adb emu gsm cancel "$NUMBER" >/dev/null 2>&1; sleep 2
record "gsm cancel $NUMBER; calls left" "$(adb emu gsm list | tr -d '\r' | head -1)"

# ---- Text: ACTION_SENDTO smsto: to the SMS role holder
card
MARK="$(ring_mark)"
tap_node "$D/card.xml" people_card_action:text:0; sleep 4
SLICE="$(ring_since "$MARK")"; log "$(echo "$SLICE" | grep -F '[people] action')"
HOLDER="$(S cmd role get-role-holders android.app.role.SMS)"
assert_contains "the text line" "[people] action text -> $HOLDER" "$SLICE"
assert_contains "the text line names smsto: her number" "smsto:+15550000001" "$SLICE"
assert_contains "the SMS role holder is on top" "$HOLDER/" "$(top_activity)"

# ---- Mail: to the Mail slot's app
card
MARK="$(ring_mark)"
tap_node "$D/card.xml" people_card_action:mail:0; sleep 4
SLICE="$(ring_since "$MARK")"; log "$(echo "$SLICE" | grep -F '[people] action')"
assert_contains "the mail line names the Mail slot's component and the address" "[people] action mail -> $(short "$K9") mailto:ann@example.com" "$SLICE"
assert_contains "K-9 is the top package" "com.fsck.k9/" "$(top_activity)"

# ---- Address: geo:0,0?q= to the Maps slot's app
card
MARK="$(ring_mark)"
tap_node "$D/card.xml" people_card_action:map:0; sleep 5
SLICE="$(ring_since "$MARK")"; log "$(echo "$SLICE" | grep -F '[people] action')"
assert_contains "the map line names the Maps slot's component and a geo: query" "[people] action map -> $(short "$OSM") geo:0,0?q=1%20Main%20St%20Springfield" "$SLICE"
assert_contains "OsmAnd is the top package" "net.osmand.plus/" "$(top_activity)"

# ---- restore: the slots, the fixture, the apps the actions opened
ring_save
layout_restore "$D/layout-before.json"; assert_eq "the layout is back" "0" "$?"
assert_eq "the slots equal the slots before the row" "$(python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1])).get("slots",{}), sort_keys=True))' "$D/layout-before.json")" "$(layout_json | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin).get("slots",{}), sort_keys=True))')"
for p in com.fsck.k9 net.osmand.plus "$HOLDER"; do adb shell am force-stop "$p"; done
people_fixtures_down
assert_eq "raw_contacts count equals the count before the row" "$BEFORE" "$(raw_count)"
assert_eq "no call is left on the modem" "" "$(adb emu gsm list | tr -d '\r' | grep -v '^OK' | head -1)"
ensure_start
row_end
