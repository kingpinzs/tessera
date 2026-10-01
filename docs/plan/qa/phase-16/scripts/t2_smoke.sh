#!/usr/bin/env bash
# Phase 16 build task 2, development smoke (NOT a gate row; E1, E2, E22 and E28 are the gate's proofs of these parts).
# It installs the build over whatever the emulator holds — the upgrade path, no grants passed — and checks that the two
# app identities exist, that the slot seed ran, and that each exported handler routes as CalendarIntents / PeopleIntents say.
. "$(dirname "$0")/lib.sh"
export ANDROID_SERIAL=emulator-5554

S() { adb shell "$@" | tr -d '\r'; }
top_activity() { S dumpsys activity activities | grep -m1 'topResumedActivity' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }
slots() { adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys; d=json.load(sys.stdin); print("slots", json.dumps(d.get("slots", {}), sort_keys=True)); print("addedOnce", sorted(d.get("addedOnce", [])))'; }

BEFORE_APK="$(installed_apk_id)"
# Whether the two markers had already run before this install decides which seed line the new process writes.
SEEDED_BEFORE="$(slots 2>/dev/null | grep -c 'slot:calendar:v1')"
INSTALL_MARK="$(ring_mark)"
adb install -r "$APK" > /tmp/t2-install.$$ 2>&1; INSTALL_RC=$?
INSTALL_OUT="$(cat /tmp/t2-install.$$)"; rm -f /tmp/t2-install.$$
sleep 6

row_begin T2SMOKE "build task 2: app identities and contracts (development smoke)"
log "installed before: $BEFORE_APK; adb install -r rc=$INSTALL_RC: $(echo "$INSTALL_OUT" | tail -1)"
assert_eq "adb install -r succeeded" "0" "$INSTALL_RC"
assert_contains "the device now holds this build" "yes" "$(apk_matches)"
ensure_start

SLICE="$(ring_since "$INSTALL_MARK")"
log "assignSlotOnce lines since the install:"; printf '%s\n' "$SLICE" | grep -F 'assignSlotOnce' | tee -a "$LOG"
if [ "$SEEDED_BEFORE" = "0" ]; then SEED="-> assigned"; else SEED="-> already run"; fi
log "markers present before this install: $SEEDED_BEFORE (expecting '$SEED')"
assert_contains "calendar slot seed line" "assignSlotOnce slot:calendar:v1 CALENDAR -> app.tileshell/.calendar.CalendarActivity $SEED" "$SLICE"
assert_contains "people slot seed line" "assignSlotOnce slot:people:v1 PEOPLE -> app.tileshell/.people.PeopleActivity $SEED" "$SLICE"
L="$(slots)"; log "$L"
assert_contains "slots.CALENDAR is the shell's Calendar" '"CALENDAR": "app.tileshell/app.tileshell.calendar.CalendarActivity"' "$L"
assert_contains "slots.PEOPLE is the shell's People" '"PEOPLE": "app.tileshell/app.tileshell.people.PeopleActivity"' "$L"
assert_contains "marker slot:calendar:v1" "slot:calendar:v1" "$L"
assert_contains "marker slot:people:v1" "slot:people:v1" "$L"

for cat in APP_CALENDAR APP_CONTACTS; do
  Q="$(S cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.$cat | grep '/')"
  log "$cat handlers: $(echo $Q)"
  assert_eq "$cat has two handlers" "2" "$(printf '%s\n' "$Q" | grep -c '/')"
  assert_contains "$cat lists the shell" "app.tileshell/" "$Q"
done

log "exported surface against the allow-list:"
python3 "$HERE/../../phase-03/scripts/exported.py" "$APK" "$HERE/../../phase-03/exported-allowlist.txt" >> "$LOG" 2>&1; assert_eq "exported.py rc" "0" "$?"

# ---- Calendar's handlers (explicit component: the route, not Android's resolver, is what is checked here)
open_cal() { # label expected-route am-args...
  local label="$1" want="$2"; shift 2
  local mark; mark="$(ring_mark)"
  adb shell am start -n app.tileshell/.calendar.CalendarActivity "$@" >/dev/null 2>&1
  sleep 2
  assert_eq "$label: resumed" "app.tileshell/.calendar.CalendarActivity" "$(top_activity)"
  assert_contains "$label: route" "$want" "$(ring_since "$mark" | grep -F '[calendar] open')"
}
open_cal "launcher" "-> open page=default" -a android.intent.action.MAIN
D="$ROW_DIR/calendar.xml"; dump_ui "$D"
assert_eq "calendar window draws the status bar" "yes" "$(has_node "$D" w10m_status_bar)"
assert_eq "calendar window draws the nav bar" "yes" "$(has_node "$D" w10m_nav_bar)"
open_cal "shortcut page" "-> open page=month" -a android.intent.action.VIEW --es page month
open_cal "VIEW time" "-> time 1790000000000" -a android.intent.action.VIEW -d content://com.android.calendar/time/1790000000000
open_cal "VIEW event" "-> event 42" -a android.intent.action.VIEW -d content://com.android.calendar/events/42
open_cal "EDIT event" "-> edit 42" -a android.intent.action.EDIT -d content://com.android.calendar/events/42
open_cal "INSERT with a calendar_id" "-> insert (prefilled, unsaved)" -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --el calendar_id 9 --es title Intruder
open_cal "malformed URI" "-> open page=default" -a android.intent.action.VIEW -d content://com.android.calendar/events/abc
assert_absent "a caller's title never reaches the ring" "Intruder" "$(diag)"
# Android's own resolver finds the shell for the implicit forms (the AOSP Calendar is the other handler here).
# `cmd package query-activities` does not ask a provider for its data's type (run 1: a data-only VIEW listed no handler
# at all, the AOSP Calendar included), so the type the provider reports is passed; `am start` asks the provider itself
# and is checked below with the data alone.
for probe in "-a android.intent.action.VIEW -d content://com.android.calendar/time/1790000000000 -t time/epoch" \
             "-a android.intent.action.INSERT -t vnd.android.cursor.dir/event" \
             "-a android.intent.action.EDIT -t vnd.android.cursor.item/event" \
             "-a android.intent.action.VIEW -t vnd.android.cursor.item/event"; do
  # shellcheck disable=SC2086
  R="$(S cmd package query-activities --brief $probe | grep '/')"
  assert_contains "resolver lists the shell for [$probe]" "app.tileshell/.calendar.CalendarActivity" "$R"
done
adb shell input keyevent KEYCODE_BACK; sleep 1
assert_ne "Back leaves Calendar" "app.tileshell/.calendar.CalendarActivity" "$(top_activity)"
implicit_view() { # label data -> Android's resolver sheet offers the shell (its app name is on the sheet)
  adb shell am start -W -a android.intent.action.VIEW -d "$2" >/dev/null 2>&1; sleep 2
  local d="$ROW_DIR/resolver-$1.xml"; dump_ui "$d" || true
  assert_contains "$1: data-only VIEW reaches Android's resolver" "ResolverActivity" "$(top_activity)"
  assert_contains "$1: the resolver offers the shell" 'text="Tessera"' "$(cat "$d")"
  adb shell input keyevent KEYCODE_BACK; sleep 1
}
implicit_view calendar content://com.android.calendar/time/1790000000000
implicit_view contact content://com.android.contacts/contacts/1

# ---- People's handlers
open_people() { # label expected-route am-args...
  local label="$1" want="$2"; shift 2
  local mark; mark="$(ring_mark)"
  adb shell am start -n app.tileshell/.people.PeopleActivity "$@" >/dev/null 2>&1
  sleep 2
  assert_eq "$label: resumed" "app.tileshell/.people.PeopleActivity" "$(top_activity)"
  assert_contains "$label: route" "$want" "$(ring_since "$mark" | grep -F '[people] open')"
}
open_people "launcher" "-> open page=default" -a android.intent.action.MAIN
D="$ROW_DIR/people.xml"; dump_ui "$D"
assert_eq "people window draws the status bar" "yes" "$(has_node "$D" w10m_status_bar)"
assert_eq "people window draws the nav bar" "yes" "$(has_node "$D" w10m_nav_bar)"
open_people "shortcut page" "-> open page=groups" -a android.intent.action.VIEW --es page groups
open_people "VIEW contact" "-> card" -a android.intent.action.VIEW -d content://com.android.contacts/contacts/1
open_people "VIEW lookup" "-> card" -a android.intent.action.VIEW -d content://com.android.contacts/contacts/lookup/0r1-2A4C/1
open_people "EDIT contact" "-> edit" -a android.intent.action.EDIT -d content://com.android.contacts/contacts/1
open_people "INSERT naming an account" "-> insert (prefilled, unsaved)" -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact --es name Intruder --es account_name qa.work@example.com
open_people "INSERT_OR_EDIT" "-> insert or edit (prefilled, unsaved)" -a android.intent.action.INSERT_OR_EDIT -t vnd.android.cursor.item/contact --es phone 5550002
# Since the fix round (trust review B-F4 / ledger F5): a PICK is honoured only for a caller that can receive its result,
# and am start has none — the plain list opens and the shell says so. PICK from a real caller is the TRUST row's.
PMARK="$(ring_mark)"
open_people "PICK contact, no caller" "-> open page=default" -a android.intent.action.PICK -t vnd.android.cursor.dir/contact
open_people "PICK phone, no caller" "-> open page=default" -a android.intent.action.PICK -t vnd.android.cursor.dir/phone_v2
assert_contains "PICK with no caller: the shell says so" "[people] pick: no caller to return a result to; the list was opened" "$(ring_since "$PMARK")"
open_people "malformed URI" "-> open page=default" -a android.intent.action.VIEW -d content://com.android.contacts/contacts/abc
assert_absent "a caller's name never reaches the ring" "Intruder" "$(diag)"
for probe in "-a android.intent.action.VIEW -d content://com.android.contacts/contacts/1 -t vnd.android.cursor.item/contact" \
             "-a android.intent.action.EDIT -t vnd.android.cursor.item/contact" \
             "-a android.intent.action.INSERT -t vnd.android.cursor.dir/contact" \
             "-a android.intent.action.INSERT_OR_EDIT -t vnd.android.cursor.item/contact" \
             "-a android.intent.action.PICK -t vnd.android.cursor.dir/contact" \
             "-a android.intent.action.PICK -t vnd.android.cursor.dir/phone_v2"; do
  # shellcheck disable=SC2086
  R="$(S cmd package query-activities --brief $probe | grep '/')"
  assert_contains "resolver lists the shell for [$probe]" "app.tileshell/.people.PeopleActivity" "$R"
done
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- the Setup row: an upgrade passes no grants, so WRITE_CONTACTS is not held and READ is (the old build's grant)
perm() { S dumpsys package app.tileshell | grep -m1 "android.permission.$1: granted" | sed -E 's/.*granted=([a-z]+).*/\1/'; }
log "READ_CONTACTS granted=$(perm READ_CONTACTS) WRITE_CONTACTS granted=$(perm WRITE_CONTACTS)"
checklist_state() { # row id -> the state in the Setup checklist's own line
  local mark; mark="$(ring_mark)"
  adb shell am start -n app.tileshell/.settings.SettingsActivity --es page CHECKLIST >/dev/null 2>&1; sleep 2
  ring_since "$mark" | grep -F '[checklist]' | tail -1 | grep -oE "$1=[A-Z]+"
}
if [ "$(perm WRITE_CONTACTS)" = "false" ]; then
  assert_eq "people row with READ held, WRITE not" "people=PARTIAL" "$(checklist_state people)"
  WMARK="$(ring_mark)"; ensure_start
  assert_contains "a PARTIAL People row does not summon the wizard" "[wizard] not shown" "$(ring_since "$WMARK")"
fi
adb shell pm grant app.tileshell android.permission.WRITE_CONTACTS
assert_eq "WRITE_CONTACTS granted" "true" "$(perm WRITE_CONTACTS)"
assert_eq "people row with both held" "people=GRANTED" "$(checklist_state people)"
D="$ROW_DIR/checklist.xml"; dump_ui "$D"; scroll_to_node "$D" checklist:people:granted 6 >/dev/null 2>&1 || true; dump_ui "$D"
assert_eq "checklist:people:granted on the Setup page" "yes" "$(has_node "$D" checklist:people:granted)"
adb shell pm revoke app.tileshell android.permission.READ_CONTACTS
assert_eq "people row with WRITE held, READ revoked" "people=MISSING" "$(checklist_state people)"
adb shell pm grant app.tileshell android.permission.READ_CONTACTS
assert_eq "READ_CONTACTS restored" "true" "$(perm READ_CONTACTS)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start
row_end
