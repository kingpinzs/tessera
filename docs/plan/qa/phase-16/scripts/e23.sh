#!/usr/bin/env bash
# Phase 16 E23 — Sync rule 4 and the first Sync (T16-1, T16-3; r3 D5, V11, V15).
#
#   start      pm clear app.tileshell → provision.sh → ensure_start (nothing allowed)
#   fixtures   Personal FIRST, then "Shared" under Personal's account at access level 200, Work LAST (so a re-created
#              Personal cannot be handed the id it had); "Offsite" in Work (E22's sentinel); the row's own birthday
#              (people_fixtures_up + E17's insert, `Tessera Birthdays` asserted listed); a local event "Standup"
#   F  the first Sync   cal_event_action:sync opens "Can sync to" directly: Personal and Work unchecked, each under its
#                       account's header, the line "Choose which calendars Sync may use", no cal_sync_target node, the
#                       routed diagnostics line; Tessera, Birthdays and the read-only Shared are not listed
#   P  the picker       Personal ticked, Back → the Sync picker lists Personal only; Back out without syncing →
#                       Personal 0, Work exactly Offsite; calendar_sync.json's `allowed` holds Personal's id, name, type
#   R  re-added         Personal deleted (sync-adapter URI) and created again, its new _ID asserted different → Sync
#                       opens "Can sync to" again with the new Personal unchecked
#   restore    as E22, plus the birthday row, the Birthdays calendar, people_fixtures_down, the baseline layout
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"

row_begin E23 "Sync rule 4 and the first Sync: nothing allowed at first, the opt-in list, a re-added account"
c6
cal_fixtures_down
BEFORE="$(ctessera_count)"
ring_save
assert_eq "start: pm clear → provision.sh rc" "0" "$(cprovision start)"
ensure_start
assert_contains "start: the device still holds the build under test" "yes" "$(apk_matches)"
record "start: calendar_sync.json after the clear" "$(csync_json | head -c 200)"

# ----------------------------------------------------------------------------------------------- fixtures
log "--- fixtures"
mkcal "$PERSONAL_ACCT" Personal 0 >/dev/null
mkcal "$PERSONAL_ACCT" Shared 0 200 >/dev/null
mkcal "$WORK_ACCT" Work 1 >/dev/null
PERSONAL="$(cal_id "$PERSONAL_ACCT" Personal)"; SHARED="$(cal_id "$PERSONAL_ACCT" Shared)"; WORK="$(cal_id "$WORK_ACCT" Work)"
assert_ne "fixtures: Personal exists" "" "$PERSONAL"
assert_ne "fixtures: Shared exists under Personal's account" "" "$SHARED"
assert_contains "fixtures: … at calendar_access_level 200" "calendar_access_level=200" "$(cals | grep "_id=$SHARED,")"
assert_ne "fixtures: Work exists" "" "$WORK"
assert_eq "fixtures: Work was created last (the highest _id)" "yes" "$([ "${WORK:-0}" -gt "${PERSONAL:-0}" ] && [ "${WORK:-0}" -gt "${SHARED:-0}" ] && echo yes || echo no)"
OFFSITE="$(cmkevent "$WORK" Offsite "$(cday_ms 5 10:00)" "$(cday_ms 5 11:00)")"
assert_ne "fixtures: Offsite is driver-inserted into Work" "" "$OFFSITE"
assert_eq "fixtures: no Birthdays calendar before the row's birthday" "" "$(cbirthdays_cal)"
people_fixtures_up
cbirthday "$ANN" "1990-$(adb shell date +%m-%d | tr -d '\r')"
for i in 1 2 3 4 5 6; do sleep 1; [ -n "$(cbirthdays_cal)" ] && break; done
BDAY="$(cbirthdays_cal)"
assert_ne "fixtures: the row's own birthday — Tessera Birthdays lists" "" "$BDAY"
copen
TESS="$(tessera_id)"
assert_ne "fixtures: Tessera exists once Calendar has opened" "" "$TESS"
NOW="$(device_ms)"; ST=$(( (NOW / 3600000 + 3) * 3600000 ))
STANDUP="$(cmkevent "$TESS" Standup "$ST" $(( ST + 3600000 )))"
assert_ne "fixtures: a local event \"Standup\"" "" "$STANDUP"
note "tessera=$TESS personal=$PERSONAL shared=$SHARED work=$WORK birthdays=$BDAY offsite=$OFFSITE standup=$STANDUP"
ALLOWED0="$(csync_get allowed)"; note "calendar_sync.json's allowed before the first Sync: $ALLOWED0"
assert_eq "fixtures: nothing is allowed yet (calendar_sync.json's allowed list is empty, or the file does not exist yet)" "yes" "$(case "$ALLOWED0" in "[]"|"(no file)"|null) echo yes;; *) echo no;; esac)"

# ----------------------------------------------------------------------------------------------- F: the first Sync
log "--- F: the first Sync, with nothing allowed"
copen_event "$STANDUP"; dump_ui "$ROW_DIR/F-page.xml"
assert_eq "F: Standup's page is open" "yes" "$(has_node "$ROW_DIR/F-page.xml" "cal_event_page:$STANDUP")"
F_MARK="$(ring_mark)"
ctap cal_event_action:sync 2; dump_ui "$ROW_DIR/F-can-sync.xml"; screencap "$ROW_DIR/F-can-sync.png"
D="$ROW_DIR/F-can-sync.xml"
assert_eq "F: the \"Can sync to\" page opens directly (cal_can_sync)" "yes" "$(has_node "$D" cal_can_sync)"
assert_eq "F: cal_settings_can_sync:<personal id> is listed, unchecked" "false" "$(cattr "$D" "cal_settings_can_sync:$PERSONAL" checked)"
assert_eq "F: cal_settings_can_sync:<work id> is listed, unchecked" "false" "$(cattr "$D" "cal_settings_can_sync:$WORK" checked)"
assert_eq "F: Personal's row sits under its account's cal_account:<name> header" "$PERSONAL_ACCT" "$(cheader_above "$D" "cal_settings_can_sync:$PERSONAL")"
assert_eq "F: Work's row sits under its account's cal_account:<name> header" "$WORK_ACCT" "$(cheader_above "$D" "cal_settings_can_sync:$WORK")"
assert_eq "F: the line \"Choose which calendars Sync may use\"" "Choose which calendars Sync may use" "$(ctext "$D" cal_notice)"
assert_eq "F: no cal_sync_target:* node" "0" "$(ccount_prefix "$D" cal_sync_target:)"
assert_contains "F: diagnostics — sync event=<id>: no calendar allowed -> can sync to" "[calendar] sync event=$STANDUP: no calendar allowed -> can sync to" "$(ring_since "$F_MARK")"
assert_eq "F: Tessera is not listed there" "no" "$(has_node "$D" "cal_settings_can_sync:$TESS")"
assert_eq "F: Birthdays is not listed there" "no" "$(has_node "$D" "cal_settings_can_sync:$BDAY")"
assert_eq "F: no cal_settings_can_sync:<shared id> — a calendar the phone may not write is never offered (r3 D5)" "no" "$(has_node "$D" "cal_settings_can_sync:$SHARED")"
assert_eq "F: … exactly the two writable account calendars are listed" "cal_settings_can_sync:$PERSONAL cal_settings_can_sync:$WORK" "$(cids "$D" cal_settings_can_sync:)"

# ----------------------------------------------------------------------------------------------- P: the picker
log "--- P: Personal ticked, Back → the Sync picker"
ctap "cal_settings_can_sync:$PERSONAL" 1.5; dump_ui "$ROW_DIR/P-ticked.xml"
assert_eq "P: Personal is ticked" "true" "$(cattr "$ROW_DIR/P-ticked.xml" "cal_settings_can_sync:$PERSONAL" checked)"
assert_eq "P: … and Work stays unticked" "false" "$(cattr "$ROW_DIR/P-ticked.xml" "cal_settings_can_sync:$WORK" checked)"
cback 1.8; dump_ui "$ROW_DIR/P-picker.xml"; screencap "$ROW_DIR/P-picker.png"
D="$ROW_DIR/P-picker.xml"
assert_eq "P: Back returns to the Sync picker (cal_sync)" "yes" "$(has_node "$D" cal_sync)"
assert_eq "P: the picker lists cal_sync_target:<personal id> only (no Work, Shared, Tessera or Birthdays)" "cal_sync_target:$PERSONAL" "$(cids "$D" cal_sync_target:)"
for pair in "Work:$WORK" "Shared:$SHARED" "Tessera:$TESS" "Birthdays:$BDAY"; do
  assert_eq "P: … no cal_sync_target for ${pair%%:*}" "no" "$(has_node "$D" "cal_sync_target:${pair##*:}")"
done
cback 1.8; dump_ui "$ROW_DIR/P-backed-out.xml"
assert_eq "P: Back out without syncing — the picker is closed" "no" "$(has_node "$ROW_DIR/P-backed-out.xml" cal_sync)"
cal_lists "$PERSONAL" Personal
assert_eq "P: Personal holds 0" "" "$(ctitles "$PERSONAL")"
cwork_offsite "P: after backing out"
J="$(csync_json)"; log "calendar_sync.json: $J"
ALLOWED="$(csync_get allowed)"
assert_eq "P: calendar_sync.json lists Personal's _ID, account name and type under allowed — and nothing else" "[{\"id\":$PERSONAL,\"accountName\":\"$PERSONAL_ACCT\",\"accountType\":\"com.google\"}]" "$ALLOWED"
assert_eq "P: no mapping was made (nothing was synced)" "[]" "$(csync_get mappings)"

# ----------------------------------------------------------------------------------------------- R: the re-added account
log "--- R: Personal deleted and created again"
q "content delete --uri '$CAL/$PERSONAL?$SA&account_name=$PERSONAL_ACCT&account_type=com.google'" >/dev/null
assert_eq "R: Personal is deleted (sync-adapter URI)" "" "$(cal_id "$PERSONAL_ACCT" Personal)"
sleep 2
mkcal "$PERSONAL_ACCT" Personal 0 >/dev/null
PERSONAL2="$(cal_id "$PERSONAL_ACCT" Personal)"
assert_ne "R: Personal is created again" "" "$PERSONAL2"
assert_ne "R: its new _ID differs from the old one (V15)" "$PERSONAL" "$PERSONAL2"
copen_event "$STANDUP"
R_MARK="$(ring_mark)"
ctap cal_event_action:sync 2; dump_ui "$ROW_DIR/R-can-sync.xml"
D="$ROW_DIR/R-can-sync.xml"
assert_eq "R: Sync on \"Standup\" opens \"Can sync to\" again" "yes" "$(has_node "$D" cal_can_sync)"
assert_eq "R: … with the new Personal unchecked" "false" "$(cattr "$D" "cal_settings_can_sync:$PERSONAL2" checked)"
assert_eq "R: … and no Sync target offered" "0" "$(ccount_prefix "$D" cal_sync_target:)"
assert_contains "R: … the routed line again" "[calendar] sync event=$STANDUP: no calendar allowed -> can sync to" "$(ring_since "$R_MARK")"
record "R: calendar_sync.json's allowed after the account came back" "$(csync_get allowed)"
cal_lists "$PERSONAL2" "the new Personal"
assert_eq "R: the new Personal holds 0" "" "$(ctitles "$PERSONAL2")"
cwork_offsite "R: at the end"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
c6
q "content delete --uri $DATA --where \"raw_contact_id=$ANN AND mimetype='vnd.android.cursor.item/contact_event'\"" >/dev/null
assert_eq "restore: the birthday row is removed" "0" "$(cbirthdays_on_phone)"
cal_fixtures_down
cpurge "title='Standup'"
assert_eq "restore: the test event is deleted" "0" "$(cevent_count "title='Standup'")"
people_fixtures_down
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (the row cleared the shell)" "0" "$?"
ensure_start
row_end
