#!/usr/bin/env bash
# Development proof of E18's core, the calendar's two permission-revoked states and the both-revoked one:
#  (a) READ and WRITE revoked: the page says it cannot read and offers the grant in place; `calendars: denied
#      (READ_CALENDAR)` and no query; `pm grant` loads the views with no restart.
#  (b) WRITE alone revoked, Tessera absent: the views load, `calendars: n (local missing: WRITE_CALENDAR)`, nothing is
#      created; Save says it cannot save and offers the grant; after the grant, Save makes Tessera and the event.
#  (c) READ alone revoked (WRITE held): Calendar opened and closed three times and Tess asked to add — no second
#      Tessera is ever created (`local calendar lookup failed`, "I don't have a calendar to add that to.").
# `pm revoke` ends the shell's process each time; every grant is restored and asserted. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E18_PERMS "READ_CALENDAR and WRITE_CALENDAR revoked, together and alone"
READ=android.permission.READ_CALENDAR; WRITE=android.permission.WRITE_CALENDAR
# The ring is in memory and a revoke or a force-stop empties it: every slice here is the saved file plus the live ring.
since() { ring_save; python3 - "$1" "$ROW_DIR/ring-launcher.txt" <<'PY'
import re, sys
mark = int(sys.argv[1]); seen = set()
for line in open(sys.argv[2], encoding='utf-8', errors='replace'):
    m = re.search(r'\bwall=(\d+)', line)
    if m and int(m.group(1)) >= mark and line not in seen:
        seen.add(line); print(line.rstrip())
PY
}
tessera_rows() { cals | grep -c 'account_name=Tessera,'; }
assert_eq "both calendar permissions are held at the start" "true true" "$(granted $READ) $(granted $WRITE)"
c6

# ---------------------------------------------------------------- (a) READ and WRITE revoked
adb shell pm revoke app.tileshell $READ; adb shell pm revoke app.tileshell $WRITE; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2; dump_ui "$ROW_DIR/a_denied.xml"; screencap "$ROW_DIR/a_denied.png"
assert_contains "the page says it cannot read the calendar" "can't read your calendar" "$(node_text "$ROW_DIR/a_denied.xml" cal_notice | sed "s/&apos;/'/g")"
assert_eq "and offers the grant in place" yes "$(has_node "$ROW_DIR/a_denied.xml" cal_grant)"
SLICE="$(since "$MARK")"
assert_contains "the denied line" "[calendar] calendars: denied (READ_CALENDAR)" "$SLICE"
absent_in "no calendars: none line" "calendars: none" "$SLICE"
absent_in "no query ran: no view line" "[calendar] view " "$SLICE"
PID0="$(S pidof app.tileshell)"
MARK="$(ring_mark)"
adb shell pm grant app.tileshell $READ; adb shell pm grant app.tileshell $WRITE
sleep 4; dump_ui "$ROW_DIR/a_granted.xml"
assert_eq "after pm grant the views load: no notice, the week strip is there" "no yes" "$(has_node "$ROW_DIR/a_granted.xml" cal_notice) $(has_node "$ROW_DIR/a_granted.xml" cal_strip)"
assert_eq "with no restart of the shell" "$PID0" "$(S pidof app.tileshell)"
SLICE="$(since "$MARK")"
assert_contains "a calendars line after the grant" "[calendar] calendars: " "$SLICE"
absent_in "and no denied line" "denied" "$SLICE"
log "after the grant: $(line_of "$SLICE" '[calendar] calendars:')"

# ---------------------------------------------------------------- (b) WRITE alone revoked, Tessera absent
c6; rm_tessera
adb shell pm revoke app.tileshell $WRITE; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3
assert_eq "READ held, WRITE revoked" "true false" "$(granted $READ) $(granted $WRITE)"
MARK="$(ring_mark)"
open_cal -a android.intent.action.MAIN; sleep 2; dump_ui "$ROW_DIR/b_views.xml"
N="$(cals | grep -c '_id=')"
SLICE="$(since "$MARK")"
assert_contains "the local-missing line" "[calendar] calendars: $N (local missing: WRITE_CALENDAR)" "$SLICE"
assert_eq "the views load" "no yes" "$(has_node "$ROW_DIR/b_views.xml" cal_notice) $(has_node "$ROW_DIR/b_views.xml" cal_strip)"
assert_eq "nothing was created: no Tessera row" 0 "$(tessera_rows)"
tap cal_bar:new 2; type_field title "E18 save"; tap cal_editor_save 2; dump_ui "$ROW_DIR/b_cannot_save.xml"; screencap "$ROW_DIR/b_cannot_save.png"
assert_contains "Save says it cannot save" "can't save events" "$(node_text "$ROW_DIR/b_cannot_save.xml" cal_notice | sed "s/&apos;/'/g")"
assert_eq "and offers the grant in place; the editor stays" "yes yes" "$(has_node "$ROW_DIR/b_cannot_save.xml" cal_grant) $(has_node "$ROW_DIR/b_cannot_save.xml" cal_editor)"
assert_eq "no such event, no Tessera" "0 0" "$(event_count "title='E18 save'") $(tessera_rows)"
MARK="$(ring_mark)"
adb shell pm grant app.tileshell $WRITE; sleep 3
tap cal_editor_save 2.5
assert_eq "after the grant, Save again: one Tessera row" 1 "$(tessera_rows)"
TESS="$(tessera_id)"
assert_eq "holding the event" 1 "$(event_count "title='E18 save' AND calendar_id=$TESS")"
SLICE="$(since "$MARK")"
assert_contains "local created" "(local created id=$TESS)" "$SLICE"
assert_contains "the event's write line" "[calendar] write insert event=" "$SLICE"

# ---------------------------------------------------------------- (c) READ alone revoked: never a second Tessera
c6
adb shell pm revoke app.tileshell $READ; sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 3
assert_eq "READ revoked, WRITE held" "false true" "$(granted $READ) $(granted $WRITE)"
MARK="$(ring_mark)"
for k in 1 2 3; do open_cal -a android.intent.action.MAIN; sleep 1; c6; done
ensure_start; cortana_assist; sleep 4
TMARK="$(ring_mark)"
type_request "add a meeting called standup to my calendar at ten AM" 1
for _ in 1 2 3 4 5 6 7 8; do dump_ui "$ROW_DIR/c_card.xml"; [ "$(has_node "$ROW_DIR/c_card.xml" cortana_card_button:confirm)" = yes ] && break; sleep 0.5; done
tap_node "$ROW_DIR/c_card.xml" cortana_card_button:confirm; sleep 3
assert_eq "Tess's reply" "I don't have a calendar to add that to." "$(ring_since "$TMARK" | grep -F '[speech]' | grep -oE 'text="[^"]*"' | tail -1 | sed 's/^text="//; s/"$//' | sed "s/&apos;/'/g")"
SLICE="$(since "$MARK")"
assert_contains "the lookup failed, and said so" "[calendar] local calendar lookup failed:" "$SLICE"
absent_in "no calendar was created while READ was denied" "local calendar created" "$SLICE"
assert_contains "the app logged denied each time it opened" "[calendar] calendars: denied (READ_CALENDAR)" "$SLICE"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell pm grant app.tileshell $READ; sleep 2
assert_eq "after the grant: EXACTLY one Tessera row" 1 "$(tessera_rows)"
assert_eq "and no standup event" 0 "$(event_count "title='standup'")"

# ---------------------------------------------------------------- restore
adb shell pm grant app.tileshell $READ; adb shell pm grant app.tileshell $WRITE
assert_eq "both permissions are held again" "true true" "$(granted $READ) $(granted $WRITE)"
purge_tessera_events "title IN ('E18 save','standup')"
assert_eq "the session's event is gone" 0 "$(event_count "title='E18 save'")"
c6; ensure_start
session_end
