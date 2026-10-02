#!/usr/bin/env bash
# Phase 16 E16 — Work profile (P4 design H10; T16-18; r3 V17), clause by clause from the phase doc's row E16.
#
#   profile    a managed profile made with phase 01 E18's commands (pm create-user --profileOf 0 --managed, am
#              start-user); TestDPC from qa/phase-16/fixtures installed INTO THE PROFILE ONLY (adb install --user <id>)
#              and made its owner (dpm set-profile-owner) FIRST, before the positive leg, so the two legs differ only
#              by the switch
#   fixture    "Work Wren" inserted with `content insert --user <id>`
#   positive   People's A–Z list does NOT list her (people_row: absent); search "Wren" lists her with the briefcase glyph
#              (an enterprise marker in the row's tag) — diagnostics `[people] search "Wren": 0 (+1 enterprise)`
#   card       Work Wren's card, opened from the search result, shows no people_card_edit / people_card_delete and the
#              people_card_readonly line (another profile's contact is never writable; Q-16-3)
#   negative   cross-profile contact search disabled by the profile owner — TestDPC's own switch, driven at its dump
#              bounds in the profile, its label recorded — → search finds nothing and says so, and the slice holds
#              `[people] search "Wren": 0 (+0 enterprise)`
#   restore    the profile removed (TestDPC and Work Wren go with it), `pm list users` shows user 0 only,
#              `pm list packages --user 0 com.afwsamples.testdpc` lists nothing
#
# If the image refuses a managed profile, exactly what it said is recorded and the row fails there.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/people_lib.sh"
DPC_APK="$PL_FIX/TestDPC_9.0.12.apk"
DPC=com.afwsamples.testdpc
users() { adb shell pm list users | tr -d '\r' | grep -o 'UserInfo{[^}]*}' | tr '\n' ' ' | sed 's/ $//'; }
qu() { adb shell "$1" 2>&1 </dev/null | tr -d '\r'; }
# The centre of the first node whose text matches a regex (case-insensitive), as "x y".
text_centre() { # dump.xml regex
  python3 - "$1" "$2" <<'PY'
import html, re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for m in re.finditer(r'<node[^>]*>', xml):
    s = m.group(0)
    t = html.unescape(re.search(r'text="([^"]*)"', s).group(1))
    if t and re.search(sys.argv[2], t, re.I):
        l, tp, r, b = (int(v) for v in re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s).groups())
        print((l + r) // 2, (tp + b) // 2, "|", t); break
PY
}
search_wren() { # tag -> dumps $D/<tag>.xml after typing "Wren" into People's search box; sets S_MARK
  open_people -a android.intent.action.MAIN
  wait_node "$D/.w.xml" people_search 8 || true
  tap_node "$D/.w.xml" people_search; sleep 1
  S_MARK="$(ring_mark)"
  type_text "Wren"; sleep 3
  dump_ui "$D/$1.xml"; screencap "$D/$1.png"
}
remove_profile() {
  [ -n "${WU:-}" ] || return 0
  adb shell pm remove-user "$WU" 2>&1 | tr -d '\r' | head -1
}

row_begin E16 "work profile: enterprise search, the profile owner's switch, the read-only card"
require_build
D="$ROW_DIR"
ensure_start
USERS0="$(users)"; note "users before: $USERS0"
assert_eq "before the row: user 0 only" "1" "$(adb shell pm list users | grep -c 'UserInfo{')"
assert_eq "the TestDPC fixture is on the host (qa/phase-16/fixtures; not committed)" "yes" "$([ -s "$DPC_APK" ] && echo yes || echo no)"
assert_eq "TestDPC is not installed in user 0 before the row" "" "$(qu "pm list packages --user 0 $DPC")"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-before.txt"

# ------------------------------------------------------------------------------------------------ the profile
log "--- the managed profile (phase 01 E18's commands), TestDPC inside it, its owner"
CREATE="$(adb shell pm create-user --profileOf 0 --managed work 2>&1 | tr -d '\r')"; log "\$ pm create-user --profileOf 0 --managed work: $CREATE"
WU="$(echo "$CREATE" | grep -oE 'id [0-9]+' | grep -oE '[0-9]+' | head -1)"
assert_ne "the image made a managed profile (its user id)" "" "$WU"
if [ -z "$WU" ]; then
  record "the image refused a managed profile; it said" "$CREATE"
  ensure_start; row_end; exit 1
fi
# A driver that dies mid-row must not leave the profile behind on the shared device (a second remove is harmless).
trap '[ -n "${WU:-}" ] && adb shell pm remove-user "$WU" >/dev/null 2>&1' EXIT
START="$(adb shell am start-user "$WU" 2>&1 | tr -d '\r')"; log "\$ am start-user $WU: $START"
for _ in $(seq 1 30); do [ "$(qu "am get-started-user-state $WU")" = "RUNNING_UNLOCKED" ] && break; sleep 2; done
assert_eq "the profile is running and unlocked" "RUNNING_UNLOCKED" "$(qu "am get-started-user-state $WU")"
note "users now: $(users)"
INSTALL="$(adb install --user "$WU" "$DPC_APK" 2>&1 | tr -d '\r' | tail -1)"; log "\$ adb install --user $WU TestDPC_9.0.12.apk: $INSTALL"
assert_eq "TestDPC installed into the profile" "Success" "$INSTALL"
assert_eq "… listed in the profile" "package:$DPC" "$(qu "pm list packages --user $WU $DPC")"
assert_eq "… and NOT in user 0 (r3 V17)" "" "$(qu "pm list packages --user 0 $DPC")"
OWNER="$(qu "dpm set-profile-owner --user $WU $DPC/.DeviceAdminReceiver")"; log "\$ dpm set-profile-owner --user $WU $DPC/.DeviceAdminReceiver: $OWNER"
assert_contains "TestDPC is made the profile's owner" "Success" "$OWNER"
assert_contains "dumpsys device_policy names it the profile owner" "$DPC" "$(adb shell dumpsys device_policy | tr -d '\r' | grep -A4 -i "Profile Owner (User $WU)" | tr '\n' ' ')"

# ------------------------------------------------------------------------------------------------ Work Wren
log "--- Work Wren, inserted in the profile's provider"
qu "content insert --user $WU --uri $RAW --bind account_type:n: --bind account_name:n:" >/dev/null
WREN_RAW="$(qu "content query --user $WU --uri $RAW --projection _id --sort '_id DESC'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)"
qu "content insert --user $WU --uri $DATA --bind raw_contact_id:i:${WREN_RAW:-0} --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Work Wren'" >/dev/null
qu "content insert --user $WU --uri $DATA --bind raw_contact_id:i:${WREN_RAW:-0} --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:'+1 555 000 0031' --bind data2:i:3" >/dev/null
sleep 3
WREN_ROWS="$(qu "content query --user $WU --uri $CONTACTS --projection _id:display_name:lookup")"; log "the profile's contacts: $WREN_ROWS"
assert_contains "the profile's provider holds Work Wren" "display_name=Work Wren" "$WREN_ROWS"
assert_absent "user 0's provider does not hold her" "Work Wren" "$(q "content query --uri $CONTACTS --projection _id:display_name")"

# ------------------------------------------------------------------------------------------------ positive leg
log "--- positive: not in the list; found by search with the briefcase"
c6; ensure_start
open_people -a android.intent.action.MAIN; list_top
list_walk "$D/walk" > "$D/list-merged.tsv"; cat "$D/list-merged.tsv" >> "$LOG"
assert_eq "the walked A–Z list holds a row per contact of user 0 (the walk read the list)" "$(contacts_count)" "$(grep -c '^row' "$D/list-merged.tsv")"
assert_eq "the A–Z list does NOT list Work Wren (no row reads her name)" "0" "$(awk -F'\t' '$1=="row" && $3=="Work Wren"' "$D/list-merged.tsv" | grep -c .)"
assert_eq "… and no enterprise people_row: is in any dump of the list" "0" "$(cat "$D"/walk-*.xml | grep -o 'resource-id="people_row:enterprise:[^"]*"' | grep -c .)"
search_wren pos-search
POS_LINE="$(ring_since "$S_MARK" | grep -F '[people] search "Wren":' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$POS_LINE"
ENT_ROW="$(ids_with_prefix "$D/pos-search.xml" people_row:enterprise: | head -1)"
note "rows in the results: $(ids_with_prefix "$D/pos-search.xml" people_row: | tr '\n' ' ')"
assert_ne "search \"Wren\" lists her: a people_row: with an enterprise marker in its tag" "" "$ENT_ROW"
ENT_KEY="${ENT_ROW#people_row:enterprise:}"
assert_eq "… reading Work Wren" "Work Wren" "$(xml_text "$D/pos-search.xml" "people_name:$ENT_KEY")"
assert_eq "… with the briefcase glyph (people_row_briefcase:<lookup>)" "yes" "$(has_node "$D/pos-search.xml" "people_row_briefcase:$ENT_KEY")"
BB="$(bounds "$D/pos-search.xml" "people_row_briefcase:$ENT_KEY")"
if [ -n "$BB" ]; then
  # shellcheck disable=SC2086
  assert_ne "… drawn: ink inside the briefcase node" "0,0,0" "$(ink_color "$D/pos-search.png" $BB 0,0,0)"
fi
assert_eq "exactly one row in the results" "1" "$(count_ids "$D/pos-search.xml" people_row:)"
assert_eq "diagnostics: [people] search \"Wren\": 0 (+1 enterprise)" "[people] search \"Wren\": 0 (+1 enterprise)" "$POS_LINE"

log "--- her card, opened from the search result: read-only"
tap_node "$D/pos-search.xml" "$ENT_ROW"; sleep 3
dump_ui "$D/wren-card.xml"; screencap "$D/wren-card.png"
CARD_ROOT="$(ids_with_prefix "$D/wren-card.xml" people_card: | head -1)"
assert_ne "Work Wren's card is on show (people_card:<lookup>, asserted first)" "" "$CARD_ROOT"
assert_eq "… it is hers (people_card_name)" "WORK WREN" "$(xml_text "$D/wren-card.xml" people_card_name)"
assert_eq "no people_card_edit" "no" "$(has_node "$D/wren-card.xml" people_card_edit)"
assert_eq "no people_card_delete" "no" "$(has_node "$D/wren-card.xml" people_card_delete)"
assert_eq "the people_card_readonly line is shown" "yes" "$(has_node "$D/wren-card.xml" people_card_readonly)"
record "the read-only line for another profile's contact" "$(xml_text "$D/wren-card.xml" people_card_readonly)"
assert_contains "the profile's provider still holds her, unchanged" "display_name=Work Wren" "$(qu "content query --user $WU --uri $CONTACTS --projection _id:display_name:lookup")"
back 1; back 1; back 1

# ------------------------------------------------------------------------------------------------ negative leg
log "--- negative: TestDPC's own switch — cross-profile contacts search disabled"
qu "pm grant --user $WU $DPC android.permission.POST_NOTIFICATIONS" >/dev/null
adb shell am start --user "$WU" -n "$DPC/.PolicyManagementActivity" > "$D/dpc-start.txt" 2>&1; sleep 5
record "TestDPC's policy page (top activity)" "$(adb shell dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | sed 's/^ *//')"
FOUND=""
# Short SLOW swipes (600 px in 700 ms: a drag, no fling), a dump after each: run 1's fast swipes flung the long policy
# list past the switch between two dumps and reached the list's end without ever reading its label.
for i in $(seq 0 220); do
  dump_ui "$D/dpc.xml" || true
  FOUND="$(text_centre "$D/dpc.xml" 'cross.?profile contacts? search')"
  [ -n "$FOUND" ] && break
  adb shell input swipe 540 1500 540 900 700; sleep 0.5
done
cp "$D/dpc.xml" "$D/dpc-switch.xml" 2>/dev/null; screencap "$D/dpc-switch.png"
record "TestDPC's switch label (found after $i swipes)" "${FOUND#*| }"
assert_ne "TestDPC's cross-profile contacts search switch is on its policy page" "" "$FOUND"
if [ -n "$FOUND" ]; then
  # shellcheck disable=SC2086
  set -- ${FOUND%%|*}
  adb shell input tap "$1" "$2"; sleep 2
  dump_ui "$D/dpc-after.xml"; screencap "$D/dpc-after.png"
fi
POLICY="$(adb shell dumpsys device_policy | tr -d '\r' | grep -i -m3 'crossProfileContactsSearch\|disableContactsSearch' | tr '\n' ' ')"
record "dumpsys device_policy after the switch" "${POLICY:-no matching line}"
adb shell input keyevent KEYCODE_HOME; sleep 2
c6; ensure_start
search_wren neg-search
NEG_LINE="$(ring_since "$S_MARK" | grep -F '[people] search "Wren":' | tail -1 | sed 's/.*\[people\]/[people]/')"; log "$NEG_LINE"
assert_eq "with the search disabled: no row is listed" "0" "$(count_ids "$D/neg-search.xml" people_row:)"
EMPTY="$(xml_text "$D/neg-search.xml" people_empty)"; log "people_empty: [$EMPTY]"
assert_eq "… and the page says so (its empty line is shown)" "yes" "$(has_node "$D/neg-search.xml" people_empty)"
assert_ne "… with words" "" "$EMPTY"
assert_eq "the slice holds [people] search \"Wren\": 0 (+0 enterprise)" "[people] search \"Wren\": 0 (+0 enterprise)" "$NEG_LINE"
assert_contains "she is still in the profile's provider (the legs differ only by the switch)" "display_name=Work Wren" "$(qu "content query --user $WU --uri $CONTACTS --projection _id:display_name")"
back 1; back 1

# ------------------------------------------------------------------------------------------------ restore
log "--- restore: the profile removed"
ring_save
REMOVED="$(remove_profile)"; log "\$ pm remove-user $WU: $REMOVED"
assert_contains "pm remove-user succeeded" "Success" "$REMOVED"
sleep 3
assert_eq "pm list users shows user 0 only" "1" "$(adb shell pm list users | grep -c 'UserInfo{')"
assert_contains "… the owner" "UserInfo{0:" "$(users)"
assert_eq "pm list packages --user 0 com.afwsamples.testdpc lists nothing" "" "$(qu "pm list packages --user 0 $DPC")"
q "content query --uri $RAW --projection _id:contact_id:account_name:account_type:display_name:deleted" > "$D/raw-after.txt"
assert_eq "restore: user 0's raw_contacts rows equal the rows before the row" "$(cat "$D/raw-before.txt")" "$(cat "$D/raw-after.txt")"
assert_eq "restore: the device is awake" "Awake" "$(wake_device)"
c6; ensure_start
row_end
