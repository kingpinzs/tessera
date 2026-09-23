#!/usr/bin/env bash
# J6 — Tess never writes to an account calendar (Jeremy, 2026-09-23: "I dont want to add anything to my work
# calander from my phone ever. jsut my personal calander"; phase 16 Q2 rule 2: every event the shell creates
# goes into the shell's own LOCAL calendar). Found by the phase 11-19 design review: ActionLayer.writableCalendarId
# picked the PRIMARY writable calendar, which on the phone is a Google account's calendar.
#
# The AVD has no Google account, so two account-style calendars are created through the sync-adapter path
# (account type com.google, owner access): "QA Work" marked primary and "QA Personal". Tess is asked to add an
# event and the confirm card's Add is tapped. The event must land in the shell's local calendar and in neither
# account calendar. The two QA calendars (and their events) are deleted at the end (RV12).
. "$(dirname "$0")/lib.sh"

row_begin J6 "Tess's calendar events go to the shell's local calendar, never an account calendar"
CAL=content://com.android.calendar/calendars
SA="caller_is_syncadapter=true"
# The whole command goes to the device shell as ONE quoted string: an unquoted & in the URI backgrounded the
# command there and no calendar was created (run 1).
mkcal() { # account name display primary
  adb shell "content insert --uri '$CAL?$SA&account_name=$1&account_type=com.google' --bind account_name:s:$1 --bind account_type:s:com.google --bind name:s:'$2' --bind calendar_displayName:s:'$2' --bind calendar_access_level:i:700 --bind ownerAccount:s:$1 --bind visible:i:1 --bind sync_events:i:1 --bind isPrimary:i:$3 --bind calendar_color:i:-16776961"
}
mkcal "qa.work@example.com" "QA Work" 1
mkcal "qa.personal@example.com" "QA Personal" 0
cals() { adb shell content query --uri "$CAL" --projection _id:account_name:account_type:calendar_displayName | tr -d '\r'; }
note "calendars before: $(cals | tr '\n' ' ')"
id_of() { cals | grep -F "$1" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
WORK="$(id_of 'QA Work')"; PERSONAL="$(id_of 'QA Personal')"
assert_ne "the QA work calendar exists" "" "$WORK"
events_in() { adb shell content query --uri content://com.android.calendar/events --projection _id:calendar_id:title --where "calendar_id=$1" 2>/dev/null | tr -d '\r' | grep -c "title=standup" ; }

ensure_start
cortana_assist; sleep 4
# Tap Add the moment the card is up, as a person would: after asking, Tess listens for yes / no, and on the AVD
# the recogniser turns the silence into "AND" and replaces the card (J7, a separate defect) — run 2 waited 6 s.
type_request "add a meeting called standup to my calendar at ten AM" 1
for _ in 1 2 3 4 5 6; do dump_ui "$ROW_DIR/card.xml"; [ "$(has_node "$ROW_DIR/card.xml" cortana_card_button:confirm)" = yes ] && break; sleep 0.3; done
assert_eq "Tess shows the confirm card" yes "$(has_node "$ROW_DIR/card.xml" cortana_card_button:confirm)"
tap_node "$ROW_DIR/card.xml" cortana_card_button:confirm; sleep 3
note "Tess said: [$(reply_text)]"
note "calendars after: $(cals | tr '\n' ' ')"
# By NAME, not "the first LOCAL calendar": phase 16 adds a second local calendar (Tessera Birthdays).
LOCAL="$(cals | grep -F 'account_name=Tessera,' | grep -F 'account_type=LOCAL' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
note "work=$WORK personal=$PERSONAL local=$LOCAL"
assert_eq "nothing was written to the account calendar marked primary (QA Work)" 0 "$(events_in "$WORK")"
assert_eq "nothing was written to the other account calendar (QA Personal)" 0 "$(events_in "$PERSONAL")"
assert_ne "the shell has a local calendar" "" "$LOCAL"
assert_eq "the event is in the shell's local calendar" 1 "$(events_in "${LOCAL:-none}")"
assert_contains "the diagnostics name the local calendar" "local" "$(diag cortana | grep 'calendar event inserted' | tail -1)"
cortana_close

# ---- restore: the QA account calendars go, with anything written into them; the test event goes too ----
for a in qa.work@example.com qa.personal@example.com; do
  adb shell "content delete --uri '$CAL?$SA&account_name=$a&account_type=com.google' --where \"account_name='$a'\"" >/dev/null 2>&1
done
note "calendars after the restore: $(cals | tr '\n' ' ')"
adb shell content delete --uri content://com.android.calendar/events --where "title='standup'" >/dev/null 2>&1
note "restored: QA calendars and the test event deleted; the shell's local calendar stays (it is the shell's own)"
row_end
