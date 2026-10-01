#!/usr/bin/env bash
# Development proof of E3's core (Q2 D; T16-2): with Tessera deleted, opening Calendar creates it — exactly one row,
# J6's constants — and logs `local calendar created` and `calendars: n (local created id=<id>)`; a force-stop and a
# reopen find it (`local present`), still one. Then the inverted case: with a LOCAL QA calendar present first, Calendar
# STILL creates Tessera, both list in the ≡ pane under their accounts, and the QA calendar's event is read-only.
# NOT a gate row; no pm clear (the device is shared), so the inverted leg is run on the installed state.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E3_LOCAL "Tessera is created at first open and reused"
c6
cal_fixtures_down; rm_tessera
Q="$(cals)"; log "calendars before: $Q"
assert_absent "no Tessera calendar before the app opens" "account_name=Tessera," "$Q"
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2
SLICE="$(ring_since "$MARK")"
Q="$(cals)"; log "calendars after the first open: $Q"
assert_eq "exactly one Tessera row" 1 "$(printf '%s\n' "$Q" | grep -c 'account_name=Tessera,')"
TROW="$(printf '%s\n' "$Q" | grep 'account_name=Tessera,')"
assert_contains "account type LOCAL" "account_type=LOCAL" "$TROW"
assert_contains "display name Tessera" "calendar_displayName=Tessera" "$TROW"
assert_contains "access 700" "calendar_access_level=700" "$TROW"
assert_contains "colour #0063B1" "calendar_color=-16751695" "$TROW"
TESS="$(tessera_id)"
assert_contains "LocalCalendar's line" "[calendar] local calendar created: content://com.android.calendar/calendars/$TESS" "$SLICE"
N="$(printf '%s\n' "$Q" | grep -c '_id=')"
assert_contains "the calendars line" "[calendar] calendars: $N (local created id=$TESS)" "$SLICE"
c6
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2
SLICE="$(ring_since "$MARK")"
assert_eq "still one Tessera row after a force-stop and a reopen" 1 "$(cals | grep -c 'account_name=Tessera,')"
assert_contains "local present" "[calendar] calendars: $N (local present)" "$SLICE"
absent_in "no second create" "local calendar created" "$SLICE"

# ---- inverted (T16-2 line 4): another LOCAL calendar exists first; Tessera is still created
c6; rm_tessera
adb shell "content insert --uri '$CALS?$SA&account_name=qa&account_type=LOCAL' --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:QA --bind calendar_displayName:s:QA --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind visible:i:1 --bind sync_events:i:1" < /dev/null
QA_ID="$(cal_id qa)"
assert_ne "the QA calendar exists" "" "$QA_ID"
QEV="$(mkevent "$QA_ID" 'QA readonly' "$(day_ms 0 16:00)" "$(day_ms 0 17:00)")"
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2
SLICE="$(ring_since "$MARK")"
Q="$(cals)"; log "calendars, inverted: $Q"
assert_eq "Tessera is created though another LOCAL calendar exists" 1 "$(printf '%s\n' "$Q" | grep -c 'account_name=Tessera,')"
TESS2="$(tessera_id)"
assert_contains "local created (two rows)" "(local created id=$TESS2)" "$(line_of "$SLICE" '[calendar] calendars:')"
assert_ne "LocalCalendar did not pick the QA calendar" "$QA_ID" "$TESS2"
tap cal_menu 1.5; dump_ui "$ROW_DIR/pane.xml"
assert_eq "both calendars list in the pane" "yes yes" "$(has_node "$ROW_DIR/pane.xml" "cal_calendar_row:$TESS2") $(has_node "$ROW_DIR/pane.xml" "cal_calendar_row:$QA_ID")"
assert_eq "under their account headers" "yes yes" "$(has_node "$ROW_DIR/pane.xml" cal_account:Tessera) $(has_node "$ROW_DIR/pane.xml" cal_account:qa)"
adb shell input keyevent KEYCODE_BACK; sleep 1
tap "cal_event:$QEV" 2; dump_ui "$ROW_DIR/qa_event.xml"
assert_eq "the QA event's page opened" yes "$(has_node "$ROW_DIR/qa_event.xml" "cal_event_page:$QEV")"
assert_eq "no edit / delete / sync on a calendar that is not Tessera" "no no no" "$(has_node "$ROW_DIR/qa_event.xml" cal_event_action:edit) $(has_node "$ROW_DIR/qa_event.xml" cal_event_action:delete) $(has_node "$ROW_DIR/qa_event.xml" cal_event_action:sync)"

# ---- restore: the QA calendar (its event cascades); Tessera kept
rmcal qa LOCAL
assert_eq "the QA calendar is gone" "" "$(cal_id qa)"
c6; ensure_start
session_end
