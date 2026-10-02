#!/usr/bin/env bash
# Phase 16 E3 — the local calendar (Q2 D; T16-2: phase 03's LocalCalendar reused, created whatever else exists).
#
#   A  first open      no calendar at all → opening Calendar creates exactly one row (Tessera / LOCAL / Tessera / 700 /
#                      #0063B1), `local calendar created`, `calendars: 1 (local created id=<id>)`; force-stop and reopen →
#                      still one, `local present`
#   B  inverted        Tessera deleted, the QA calendar inserted first, pm clear → provision.sh → ensure_start: opening
#                      Calendar STILL creates Tessera (two rows, `local created`); both list inside cal_pane under their
#                      account headers; the QA calendar's event is read-only (its page asserted first)
#   C  Tess first      Tessera deleted, J6's typed request creates it; opening Calendar then logs `local present`
#   D  the editor      its calendar field reads Tessera and offers no other calendar
#   E  access 200      a READ calendar lists read-only, as every calendar but Tessera does
#   restore            cal_fixtures_down, "standup" deleted, Tessera kept, the baseline layout (the row cleared the shell)
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"

row_begin E3 "the local calendar: created at first open, beside another LOCAL calendar, by Tess first; read-only others"
PROJ="_id:account_name:account_type:name:calendar_access_level:calendar_color"
calq() { q "content query --uri $CAL --projection $PROJ"; }
tess_rows() { calq | grep -c 'account_name=Tessera,'; }
CRASH0="$(ccrashes)"

# ----------------------------------------------------------------------------------------------- A: the first open
log "--- A: no calendar at all; opening Calendar creates Tessera"
c6
tessera_down
cal_fixtures_down
assert_eq "A: precondition — no contact carries a birthday (so no Birthdays calendar can appear, r3 V15)" "0" "$(cbirthdays_on_phone)"
Q0="$(calq)"; note "calendars before: $Q0"
assert_eq "A: asserted first — the calendars query returns \"No result found\"" "No result found." "$Q0"
A_MARK="$(ring_mark)"
copen; sleep 2
A_SLICE="$(ring_since "$A_MARK")"; printf '%s\n' "$A_SLICE" > "$ROW_DIR/A-slice.txt"
QA1="$(calq)"; log "calendars after the first open: $QA1"
assert_eq "A: opening Calendar creates it — the query lists exactly one row" "1" "$(printf '%s\n' "$QA1" | grep -c '_id=')"
assert_contains "A: … account_name Tessera" "account_name=Tessera," "$QA1"
assert_contains "A: … account_type LOCAL" "account_type=LOCAL," "$QA1"
assert_contains "A: … name Tessera" "name=Tessera," "$QA1"
assert_contains "A: … access 700" "calendar_access_level=700," "$QA1"
assert_contains "A: … colour -16751695 (#0063B1)" "calendar_color=-16751695" "$QA1"
TESS="$(tessera_id)"
assert_contains "A: diagnostics hold [calendar] local calendar created: …" "[calendar] local calendar created: content://com.android.calendar/calendars/$TESS" "$A_SLICE"
assert_contains "A: diagnostics hold [calendar] calendars: 1 (local created id=<id>)" "[calendar] calendars: 1 (local created id=$TESS)" "$A_SLICE"
c6
A2_MARK="$(ring_mark)"
copen; sleep 2
A2_SLICE="$(ring_since "$A2_MARK")"; printf '%s\n' "$A2_SLICE" > "$ROW_DIR/A2-slice.txt"
assert_eq "A: force-stop and reopen → still one row" "1" "$(calq | grep -c '_id=')"
assert_eq "A: … and it is the same Tessera row" "$TESS" "$(tessera_id)"
assert_contains "A: … the line reads local present" "[calendar] calendars: 1 (local present)" "$A2_SLICE"
absent_in "A: … and no second create" "local calendar created" "$A2_SLICE"

# ----------------------------------------------------------------------------------------------- B: inverted
log "--- B: inverted (T16-2 line 4): the QA calendar first, pm clear → provision.sh → ensure_start"
c6
tessera_down
mkcal_qa >/dev/null
QA_ID="$(cal_id qa)"
assert_ne "B: the QA calendar (qa / LOCAL) is inserted first" "" "$QA_ID"
assert_eq "B: … with Tessera deleted" "0" "$(tess_rows)"
ring_save
assert_eq "B: pm clear → provision.sh rc" "0" "$(cprovision inverted)"
ensure_start
assert_contains "B: the device still holds the build under test after provision.sh" "yes" "$(apk_matches)"
assert_eq "B: precondition — still no Tessera row before Calendar is opened" "0" "$(tess_rows)"
assert_ne "B: precondition — the QA calendar survived the clear" "" "$(cal_id qa)"
QEV="$(cmkevent "$QA_ID" 'QA readonly' "$(cday_ms 0 12:00)" "$(cday_ms 0 13:00)")"
assert_ne "B: a driver-inserted event on the QA calendar" "" "$QEV"
B_MARK="$(ring_mark)"
copen; sleep 2
B_SLICE="$(ring_since "$B_MARK")"; printf '%s\n' "$B_SLICE" > "$ROW_DIR/B-slice.txt"
QB="$(calq)"; log "calendars, inverted: $QB"
assert_eq "B: opening Calendar STILL creates Tessera — two rows" "2" "$(printf '%s\n' "$QB" | grep -c '_id=')"
assert_eq "B: … one of them Tessera" "1" "$(printf '%s\n' "$QB" | grep -c 'account_name=Tessera,')"
TESS2="$(tessera_id)"
assert_ne "B: … and LocalCalendar did not take the QA calendar for its own" "$QA_ID" "$TESS2"
assert_contains "B: … local created" "[calendar] calendars: 2 (local created id=$TESS2)" "$B_SLICE"
assert_contains "B: … with LocalCalendar's own line" "[calendar] local calendar created: content://com.android.calendar/calendars/$TESS2" "$B_SLICE"
ctap cal_menu 1.5; dump_ui "$ROW_DIR/B-pane.xml"; screencap "$ROW_DIR/B-pane.png"
P="$ROW_DIR/B-pane.xml"
assert_eq "B: the ≡ pane is open (cal_pane)" "yes" "$(has_node "$P" cal_pane)"
assert_eq "B: Tessera lists as cal_calendar_row:<id> inside cal_pane" "yes" "$(cunder "$P" cal_pane "cal_calendar_row:$TESS2")"
assert_eq "B: the QA calendar lists as cal_calendar_row:<id> inside cal_pane" "yes" "$(cunder "$P" cal_pane "cal_calendar_row:$QA_ID")"
assert_eq "B: the header cal_account:Tessera is in the pane" "yes" "$(cunder "$P" cal_pane cal_account:Tessera)"
assert_eq "B: the header cal_account:qa is in the pane" "yes" "$(cunder "$P" cal_pane cal_account:qa)"
assert_eq "B: Tessera's row sits under its account's header" "Tessera" "$(cheader_above "$P" "cal_calendar_row:$TESS2")"
assert_eq "B: the QA row sits under its account's header" "qa" "$(cheader_above "$P" "cal_calendar_row:$QA_ID")"
cback
copen_day "$(cday_ms 0 12:00)"
ctap "cal_event:$QEV" 2; dump_ui "$ROW_DIR/B-qa-event.xml"
assert_eq "B: the QA event tapped — the dump holds cal_event_page:<its id> (asserted first, r3 V8)" "yes" "$(has_node "$ROW_DIR/B-qa-event.xml" "cal_event_page:$QEV")"
assert_eq "B: … and no cal_event_action:edit" "no" "$(has_node "$ROW_DIR/B-qa-event.xml" cal_event_action:edit)"
assert_eq "B: … and no cal_event_action:delete" "no" "$(has_node "$ROW_DIR/B-qa-event.xml" cal_event_action:delete)"
record "B: every cal_event_action node on the QA event's page" "[$(cids "$ROW_DIR/B-qa-event.xml" cal_event_action:)]"

# ----------------------------------------------------------------------------------------------- C: Tess first
log "--- C: Tess first — Tessera deleted, J6's typed request creates it"
c6
tessera_down
assert_eq "C: Tessera is deleted before the request" "0" "$(tess_rows)"
tess_ask "add a meeting called standup to my calendar at ten AM"
tess_card "$ROW_DIR/C-card.xml"
assert_eq "C: Tess shows the confirm card" "yes" "$(has_node "$ROW_DIR/C-card.xml" cortana_card_button:confirm)"
tess_confirm "$ROW_DIR/C-card.xml"
C_SLICE="$(ring_since "$TMARK")"; printf '%s\n' "$C_SLICE" > "$ROW_DIR/C-slice.txt"
note "Tess said after the tap: [$(reply_since "$CMARK")]"
assert_eq "C: J6's typed request creates Tessera — one Tessera row" "1" "$(tess_rows)"
TESS3="$(tessera_id)"
assert_contains "C: … created by LocalCalendar (its own line, from Tess's write)" "[calendar] local calendar created: content://com.android.calendar/calendars/$TESS3" "$C_SLICE"
assert_eq "C: … and standup is in it" "1" "$(cevent_count "title='standup' AND calendar_id=${TESS3:-0} AND deleted=0")"
tess_close
C_MARK="$(ring_mark)"
copen; sleep 2
C2_SLICE="$(ring_since "$C_MARK")"; printf '%s\n' "$C2_SLICE" > "$ROW_DIR/C2-slice.txt"
assert_contains "C: opening Calendar then logs local present" "(local present)" "$(cline "$C2_SLICE" '[calendar] calendars:')"
absent_in "C: … and creates nothing" "local calendar created" "$C2_SLICE"
assert_eq "C: the query still shows one Tessera row" "1" "$(tess_rows)"
assert_eq "C: … the one Tess made" "$TESS3" "$(tessera_id)"

# ----------------------------------------------------------------------------------------------- E fixture, then D
log "--- E / D: a calendar with access level 200, and the editor's calendar field"
mkcal "$PERSONAL_ACCT" 'QA ReadOnly' 0 200 >/dev/null
RO_ID="$(cal_id "$PERSONAL_ACCT" 'QA ReadOnly')"
assert_ne "E: a calendar with access level 200 (READ) is inserted by the driver" "" "$RO_ID"
assert_contains "E: … its level reads 200 in the provider" "calendar_access_level=200" "$(calq | grep "_id=$RO_ID,")"
ROEV="$(cmkevent "$RO_ID" 'RO event' "$(cday_ms 0 14:00)" "$(cday_ms 0 15:00)")"
assert_ne "E: a driver-inserted event on it" "" "$ROEV"

ctap cal_bar:new 2; dump_ui "$ROW_DIR/D-editor.xml"
assert_eq "D: the editor is open (cal_editor)" "yes" "$(has_node "$ROW_DIR/D-editor.xml" cal_editor)"
assert_eq "D: the editor's calendar field reads Tessera" "Tessera" "$(cfield "$ROW_DIR/D-editor.xml" calendar)"
ctap cal_editor_field:calendar 1.5; dump_ui "$ROW_DIR/D-editor-tapped.xml"; screencap "$ROW_DIR/D-editor-tapped.png"
D="$ROW_DIR/D-editor-tapped.xml"
assert_eq "D: a tap on the field opens no option list (cal_editor_options)" "no" "$(has_node "$D" cal_editor_options)"
DT="$(call_texts "$D")"; note "the editor's texts after the tap: $DT"
assert_absent "D: … and offers no QA calendar" "QA" "$DT"
assert_absent "D: … nor the read-only one's account" "$PERSONAL_ACCT" "$DT"
assert_eq "D: … the field still reads Tessera" "Tessera" "$(cfield "$D" calendar)"
ctap cal_editor_cancel 1.5

ctap cal_menu 1.5; dump_ui "$ROW_DIR/E-pane.xml"
assert_eq "E: the level-200 calendar lists in the pane (cal_calendar_row:<id> inside cal_pane)" "yes" "$(cunder "$ROW_DIR/E-pane.xml" cal_pane "cal_calendar_row:$RO_ID")"
cback
copen_day "$(cday_ms 0 12:00)"
ctap "cal_event:$ROEV" 2; dump_ui "$ROW_DIR/E-ro-event.xml"
assert_eq "E: its event tapped — cal_event_page:<id> (asserted first)" "yes" "$(has_node "$ROW_DIR/E-ro-event.xml" "cal_event_page:$ROEV")"
assert_eq "E: … read-only: no cal_event_action:edit" "no" "$(has_node "$ROW_DIR/E-ro-event.xml" cal_event_action:edit)"
assert_eq "E: … read-only: no cal_event_action:delete" "no" "$(has_node "$ROW_DIR/E-ro-event.xml" cal_event_action:delete)"
cback
# "as every calendar but Tessera does": the positive control — Tessera's own event DOES carry the actions.
STANDUP="$(event_id standup "$TESS3")"
copen_event "$STANDUP"; dump_ui "$ROW_DIR/E-tessera-event.xml"
assert_eq "E: the control — Tessera's own event page (cal_event_page:<standup>)" "yes" "$(has_node "$ROW_DIR/E-tessera-event.xml" "cal_event_page:$STANDUP")"
assert_eq "E: … does carry cal_event_action:edit" "yes" "$(has_node "$ROW_DIR/E-tessera-event.xml" cal_event_action:edit)"
assert_eq "E: … and cal_event_action:delete" "yes" "$(has_node "$ROW_DIR/E-tessera-event.xml" cal_event_action:delete)"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore (r3 V10)"
c6
cal_fixtures_down
rm_events_titled standup
assert_eq "restore: Tess's standup event is deleted (j6.sh's form)" "0" "$(cevent_count "title='standup'")"
assert_eq "restore: Tessera is kept — exactly one row" "1" "$(tess_rows)"
assert_eq "restore: Tessera holds no event of the row" "0" "$(ctessera_count)"
assert_eq "restore: no new crash of the shell during the row" "$CRASH0" "$(ccrashes)"
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline (the row cleared the shell)" "0" "$?"
ensure_start
row_end
