#!/usr/bin/env bash
# Phase 16 E22 — Sync rules 1–2: no direct write to an account calendar by any path (Q2 D; T16-1; r3 D1, D4, V18).
#
#   fixtures   Personal (qa.personal@example.com) then Work (qa.work@example.com, isPrimary 1) with mkcal; "Offsite"
#              driver-inserted into Work five days ahead (inside Tess's delete window). Every count runs cal_lists first.
#   R1  rule 1          both list inside cal_pane; Offsite shows in the Day view; its page (asserted first) has no edit,
#                       delete or sync action; the editor's calendar field reads Tessera and no picker offers the others
#   H   hide            Work un-ticked in the pane → Offsite leaves the app's views; Work's `visible` column still 1
#   T   Tess's delete   "delete the event Offsite" → "That event isn't in your Tessera calendar.", no Delete card
#   X   the intents     EDIT on Offsite opens its page, never the editor, the row unchanged; INSERT naming Work, with
#                       a title and beginTime / endTime, prefills the title AND the editor's start and end (the
#                       Edge-case bullet B13.2; gate review B, note 10), saves nothing before Save, then saves into
#                       Tessera at those times
#   R2  rule 2          the editor: create Standup, rename it Standup 2, create and delete Scratch; Tess's add — after
#                       every step Personal 0, Work exactly Offsite; every `write … ok` line names a Tessera event
#   J   the JVM test    the write guard's test, each case the row names (clauses-open.tsv: read from the lead's result
#                       file — a row writer never runs gradle)
#   L   the refusal     the trust review's extra leg (fix-round.md F15): a Tessera event moved to Work under its open
#                       editor, then Save → `write update … failed refused (not allowed)`, cal_notice, Work untouched
#   restore    cal_fixtures_down, the test events deleted, Tessera's count as before the row
# E22_LEGS=X runs the fixtures, leg X and the restore alone — a narrow re-run; the log's first RECORD says so.
set -uo pipefail
LEGS="${E22_LEGS:-all}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"

row_begin E22 "Sync rules 1–2: no write reaches an account calendar by the app, Tess, the pane or the exported intents"
if [ "$LEGS" != all ]; then
  [ "$LEGS" = X ] || { _verdict FAIL "E22_LEGS" "only E22_LEGS=X is a narrow run of this row (got $LEGS)"; row_end; exit 1; }
  record "legs run" "the fixtures and X (the exported EDIT and INSERT) ONLY — a narrow re-run; legs R1, H, T, R2, J and L stand on the row's earlier run"
fi
c6; ensure_start
cal_fixtures_down
copen
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists" "" "$TESS"
BEFORE="$(ctessera_count)"
csync_fixtures_up 5
note "tessera=$TESS personal=$PERSONAL work=$WORK offsite=$OFFSITE (five days ahead)"
assert_ne "fixtures: Personal (qa.personal@example.com) exists" "" "$PERSONAL"
assert_ne "fixtures: Work (qa.work@example.com) exists" "" "$WORK"
assert_contains "fixtures: Work is the calendar marked primary" "isPrimary=1" "$(q "content query --uri $CAL --projection _id:isPrimary --where \"_id=$WORK\"")"
assert_ne "fixtures: Offsite is driver-inserted into Work" "" "$OFFSITE"
OFF_DAY="$(cdate 5)"
OFFSITE_ROW="$(cevents title:dtstart:dtend:calendar_id "_id=$OFFSITE")"
KNOWN_TESSERA=" "   # every event id seen in Tessera during the row (a write line must name one of these)
see_tessera() { KNOWN_TESSERA="$KNOWN_TESSERA$(cevent_ids "calendar_id=$TESS") "; }
# After a step: Personal holds 0, Work exactly Offsite with its row unchanged (cal_lists first — r3 V11).
accounts_untouched() { # label
  cal_lists "$PERSONAL" "Personal, $1"
  assert_eq "$1: Personal holds 0" "" "$(ctitles "$PERSONAL")"
  cwork_offsite "$1"
  assert_eq "$1: Offsite's row (title, dtstart, dtend, calendar_id) is unchanged" "$OFFSITE_ROW" "$(cevents title:dtstart:dtend:calendar_id "_id=$OFFSITE")"
  see_tessera
}
accounts_untouched "at the start"

if [ "$LEGS" = all ]; then
# ----------------------------------------------------------------------------------------------- R1: rule 1
log "--- R1: the app shows the account calendars and offers no write on them"
c6; copen
ctap cal_menu 1.5; dump_ui "$ROW_DIR/R1-pane.xml"
assert_eq "R1: Personal lists as cal_calendar_row:<id> inside cal_pane" "yes" "$(cunder "$ROW_DIR/R1-pane.xml" cal_pane "cal_calendar_row:$PERSONAL")"
assert_eq "R1: Work lists as cal_calendar_row:<id> inside cal_pane" "yes" "$(cunder "$ROW_DIR/R1-pane.xml" cal_pane "cal_calendar_row:$WORK")"
cback
copen_day "$(clocal_ms "$OFF_DAY" 12:00)"; dump_ui "$ROW_DIR/R1-day.xml"
assert_eq "R1: the Day view is showing" "true" "$(cattr "$ROW_DIR/R1-day.xml" cal_view_mode:day selected)"
assert_eq "R1: \"Offsite\" shows in the day view" "Offsite" "$(ctext "$ROW_DIR/R1-day.xml" "cal_event_title:$OFFSITE")"
ctap "cal_event:$OFFSITE" 2; dump_ui "$ROW_DIR/R1-page.xml"
assert_eq "R1: tapped — the dump holds cal_event_page:<Offsite id> (asserted first, r3 V8)" "yes" "$(has_node "$ROW_DIR/R1-page.xml" "cal_event_page:$OFFSITE")"
assert_eq "R1: … no cal_event_action:edit" "no" "$(has_node "$ROW_DIR/R1-page.xml" cal_event_action:edit)"
assert_eq "R1: … no cal_event_action:delete" "no" "$(has_node "$ROW_DIR/R1-page.xml" cal_event_action:delete)"
assert_eq "R1: … no cal_event_action:sync" "no" "$(has_node "$ROW_DIR/R1-page.xml" cal_event_action:sync)"
cback
ctap cal_bar:new 2; dump_ui "$ROW_DIR/R1-editor.xml"
assert_eq "R1: the editor is open" "yes" "$(has_node "$ROW_DIR/R1-editor.xml" cal_editor)"
assert_eq "R1: the editor's calendar field reads Tessera" "Tessera" "$(cfield "$ROW_DIR/R1-editor.xml" calendar)"
ctap cal_editor_field:calendar 1.5; dump_ui "$ROW_DIR/R1-editor-tapped.xml"
ET="$(call_texts "$ROW_DIR/R1-editor-tapped.xml")"
assert_eq "R1: a tap on it opens no picker (cal_editor_options)" "no" "$(has_node "$ROW_DIR/R1-editor-tapped.xml" cal_editor_options)"
assert_absent "R1: no picker offers Personal" "Personal" "$ET"
assert_absent "R1: no picker offers Work" "Work" "$ET"
assert_absent "R1: … nor either account's name" "example.com" "$ET"
ctap cal_editor_cancel 1.5
accounts_untouched "after rule 1's reads"

# ----------------------------------------------------------------------------------------------- H: hide is not a write
log "--- H: hide is not a write (r3 D4)"
VIS0="$(q "content query --uri $CAL --projection _id:visible --where \"_id=$WORK\"")"
assert_contains "H: Work's visible column reads 1 before" "visible=1" "$VIS0"
views_offsite() { # label -> "day agenda week" yes/no each
  local a b c
  copen_day "$(clocal_ms "$OFF_DAY" 12:00)"; dump_ui "$ROW_DIR/H-$1-day.xml"; a="$(has_node "$ROW_DIR/H-$1-day.xml" "cal_event:$OFFSITE")"
  cview agenda 2.5; dump_ui "$ROW_DIR/H-$1-agenda.xml"; b="$(has_node "$ROW_DIR/H-$1-agenda.xml" "cal_event:$OFFSITE")"
  cview week 2.5; dump_ui "$ROW_DIR/H-$1-week.xml"; c="$(has_node "$ROW_DIR/H-$1-week.xml" "cal_event:$OFFSITE")"
  echo "$a $b $c"
}
assert_eq "H: before — Offsite is in the Day, Agenda and Week views" "yes yes yes" "$(views_offsite before)"
ctap cal_menu 1.5; dump_ui "$ROW_DIR/H-pane-before.xml"
assert_eq "H: Work's row is ticked in the pane" "true" "$(cattr "$ROW_DIR/H-pane-before.xml" "cal_calendar_row:$WORK" checked)"
ctap "cal_calendar_row:$WORK" 1.2; dump_ui "$ROW_DIR/H-pane-unticked.xml"
assert_eq "H: Work un-ticked in cal_pane" "false" "$(cattr "$ROW_DIR/H-pane-unticked.xml" "cal_calendar_row:$WORK" checked)"
cback
assert_eq "H: \"Offsite\" leaves the Calendar app's views (Day, Agenda, Week)" "no no no" "$(views_offsite hidden)"
assert_eq "H: Work's visible column reads visible=1, unchanged" "$VIS0" "$(q "content query --uri $CAL --projection _id:visible --where \"_id=$WORK\"")"
record "H: the shell's own hidden list (calendar_sync.json)" "$(csync_get hidden)"
ctap cal_menu 1.5; ctap "cal_calendar_row:$WORK" 1.2; dump_ui "$ROW_DIR/H-pane-reticked.xml"
assert_eq "H: Work re-ticked" "true" "$(cattr "$ROW_DIR/H-pane-reticked.xml" "cal_calendar_row:$WORK" checked)"
cback
assert_eq "H: … and Offsite is back in the views" "yes yes yes" "$(views_offsite back)"
accounts_untouched "after hide / show"

# ----------------------------------------------------------------------------------------------- T: Tess's delete
log "--- T: Tess's delete of an event that is only on an account calendar (r3 D1)"
c6
tess_ask "delete the event Offsite" 5
dump_ui "$ROW_DIR/T-tess.xml"
assert_eq "T: the reply (reply_since the MARK)" "That event isn't in your Tessera calendar." "$(reply_since "$TMARK" | sed "s/&apos;/'/g")"
assert_eq "T: Tess's window is the one dumped (cortana_session)" "yes" "$(has_node "$ROW_DIR/T-tess.xml" cortana_session)"
assert_eq "T: … the dump holds no cortana_card:delete_confirm" "no" "$(has_node "$ROW_DIR/T-tess.xml" cortana_card:delete_confirm)"
assert_eq "T: … and no confirm button at all" "no" "$(has_node "$ROW_DIR/T-tess.xml" cortana_card_button:confirm)"
ring_since "$TMARK" > "$ROW_DIR/T-slice.txt"
tess_close
accounts_untouched "after Tess's refused delete"

fi   # legs R1, H, T
# ----------------------------------------------------------------------------------------------- X: the exported intents
log "--- X: the exported EDIT and INSERT (r3 V18)"
adb shell am start -n "$CALENDAR_ACTIVITY" -a android.intent.action.EDIT -d "content://com.android.calendar/events/$OFFSITE" > "$ROW_DIR/X-edit.out" 2>&1; sleep 3
dump_ui "$ROW_DIR/X-edit.xml"
assert_eq "X: EDIT on Offsite — the dump holds cal_event_page:<Offsite id>" "yes" "$(has_node "$ROW_DIR/X-edit.xml" "cal_event_page:$OFFSITE")"
assert_eq "X: … and no cal_editor" "no" "$(has_node "$ROW_DIR/X-edit.xml" cal_editor)"
assert_eq "X: … Offsite's events row equals its read before" "$OFFSITE_ROW" "$(cevents title:dtstart:dtend:calendar_id "_id=$OFFSITE")"
# beginTime / endTime: three days ahead, 16:00 to 17:30 local — not the editor's own default (the next hour, one hour long).
XB="$(clocal_ms "$(cdate 3)" 16:00)"; XE="$(clocal_ms "$(cdate 3)" 17:30)"
adb shell am start -n "$CALENDAR_ACTIVITY" -a android.intent.action.INSERT -t vnd.android.cursor.dir/event --el calendar_id "$WORK" --es title Intruder --el beginTime "$XB" --el endTime "$XE" > "$ROW_DIR/X-insert.out" 2>&1; sleep 3
dump_ui "$ROW_DIR/X-insert.xml"
assert_eq "X: INSERT naming Work — cal_editor opens" "yes" "$(has_node "$ROW_DIR/X-insert.xml" cal_editor)"
assert_eq "X: … with the title prefilled" "Intruder" "$(ctext "$ROW_DIR/X-insert.xml" cal_editor_field:title)"
XDATE="$(TZ="$(ctz)" date -d "@$(( XB / 1000 ))" '+%a %-d %b %Y')"
assert_eq "X: … and beginTime / endTime prefilled: the editor's start reads that date and 4:00 PM, its end the same date and 5:30 PM" "$XDATE 4:00 PM | $XDATE 5:30 PM" "$(cfield "$ROW_DIR/X-insert.xml" start_date) $(cfield "$ROW_DIR/X-insert.xml" start_time) | $(cfield "$ROW_DIR/X-insert.xml" end_date) $(cfield "$ROW_DIR/X-insert.xml" end_time)"
assert_eq "X: … and its calendar field reading Tessera" "Tessera" "$(cfield "$ROW_DIR/X-insert.xml" calendar)"
assert_eq "X: before Save, events holds no \"Intruder\" (a prefill saves nothing without a tap)" "0" "$(cevent_count "title='Intruder'")"
ctap cal_editor_save 2.5
assert_eq "X: Save → \"Intruder\" is in Tessera" "calendar_id=$TESS" "$(cevents calendar_id "title='Intruder' AND deleted=0" | sed -n 's/^Row: [0-9]* //p' | paste -sd';')"
assert_eq "X: … saved at the prefilled times (dtstart, dtend)" "dtstart=$XB, dtend=$XE" "$(cevents dtstart:dtend "title='Intruder' AND deleted=0" | sed -n 's/^Row: [0-9]* //p' | paste -sd';')"
accounts_untouched "after the exported INSERT's Save"

if [ "$LEGS" = all ]; then
# ----------------------------------------------------------------------------------------------- R2: rule 2
log "--- R2: everything the app and Tess create goes to Tessera"
TOMORROW="$(cdate 1)"
copen; ctap cal_bar:today 1.2; cview agenda 2
ctap "cal_strip_day:$TOMORROW" 1.5
ctap cal_bar:new 2
ctype_field title "Standup"
cset_time start_time 9 00 AM
ctap cal_editor_save 2.5
STANDUP="$(event_id Standup)"
assert_eq "R2: \"Standup\" (tomorrow 09:00) created in the editor is in Tessera" "calendar_id=$TESS, dtstart=$(clocal_ms "$TOMORROW" 09:00)" "$(cevents calendar_id:dtstart "_id=${STANDUP:-0}" | sed -n 's/^Row: [0-9]* //p')"
accounts_untouched "after creating Standup"
copen_event "$STANDUP"
ctap cal_event_action:edit 2
ctype_field title " 2"
ctap cal_editor_save 2.5
assert_eq "R2: renamed — the same row reads \"Standup 2\", still in Tessera" "title=Standup 2, calendar_id=$TESS" "$(cevents title:calendar_id "_id=$STANDUP" | sed -n 's/^Row: [0-9]* //p')"
accounts_untouched "after renaming it Standup 2"
copen; ctap cal_bar:new 2
ctype_field title "Scratch"
ctap cal_editor_save 2.5
SCRATCH="$(event_id Scratch)"
assert_eq "R2: \"Scratch\" created in Tessera" "calendar_id=$TESS" "$(cevents calendar_id "_id=${SCRATCH:-0}" | sed -n 's/^Row: [0-9]* //p')"
accounts_untouched "after creating Scratch"
copen_event "$SCRATCH"
ctap cal_event_action:delete 2.5
assert_eq "R2: \"Scratch\" deleted from the app — its row is gone" "0" "$(cevent_count "title='Scratch'")"
accounts_untouched "after deleting Scratch"
c6
tess_ask "add a meeting called standup to my calendar at ten AM"
tess_card "$ROW_DIR/R2-card.xml"
assert_eq "R2: Tess shows the confirm card" "yes" "$(has_node "$ROW_DIR/R2-card.xml" cortana_card_button:confirm)"
tess_confirm "$ROW_DIR/R2-card.xml"
note "Tess said after the tap: [$(reply_since "$CMARK")]"
ring_since "$TMARK" > "$ROW_DIR/R2-tess-slice.txt"
tess_close
assert_eq "R2: Tess's \"standup\" is in Tessera" "calendar_id=$TESS" "$(cevents calendar_id "title='standup' AND deleted=0" | sed -n 's/^Row: [0-9]* //p' | paste -sd';')"
accounts_untouched "after Tess's add"
TT="|$(ctitles "$TESS")|"; log "Tessera holds: $TT"
assert_contains "R2: Tessera holds \"Standup 2\"" "|Standup 2|" "$TT"
assert_contains "R2: Tessera holds \"standup\"" "|standup|" "$TT"
# Every write line of the row names a Tessera event.
csince "$ROW_MARK" | grep -F '[calendar] write ' > "$ROW_DIR/R2-writes.txt"
log "every [calendar] write line of the row:"; sed 's/^.*\[calendar\] /   [calendar] /' "$ROW_DIR/R2-writes.txt" | tee -a "$LOG" >/dev/null
OKN="$(grep -c ': ok' "$ROW_DIR/R2-writes.txt")"
assert_ne "R2: the row's slices hold write … ok lines (the check below is not vacuous)" "0" "$OKN"
BAD=""
for id in $(grep ': ok' "$ROW_DIR/R2-writes.txt" | sed -n 's/.*event=\([0-9]*\): ok.*/\1/p' | sort -u); do
  case "$KNOWN_TESSERA" in *" $id "*) ;; *) BAD="$BAD $id";; esac
done
assert_eq "R2: every [calendar] write … event=<id>: ok line names a Tessera event (ids not seen in Tessera)" "" "$BAD"
assert_eq "R2: no write line is anything but ok" "0" "$(grep -vc ': ok' "$ROW_DIR/R2-writes.txt")"

# ----------------------------------------------------------------------------------------------- J: the guard's JVM test
log "--- J: the write guard's JVM test (CalendarWriteGuardTest), from the lead's test results"
RES="$REPO/app/build/test-results/testDebugUnitTest/TEST-app.tileshell.calendar.CalendarWriteGuardTest.xml"
SRC="$REPO/app/src/test/kotlin/app/tileshell/calendar/CalendarWriteGuardTest.kt"
GUARD="$REPO/app/src/main/kotlin/app/tileshell/calendar/CalendarWriteGuard.kt"
python3 - "$RES" > "$ROW_DIR/J-guard-test.txt" 2>&1 <<'PY'
import sys, xml.etree.ElementTree as ET
r = ET.parse(sys.argv[1]).getroot()
print("suite %s tests=%s failures=%s errors=%s skipped=%s timestamp=%s" % (r.get("name"), r.get("tests"), r.get("failures"), r.get("errors"), r.get("skipped"), r.get("timestamp")))
for c in r.iter("testcase"):
    bad = [x.tag for x in c if x.tag in ("failure", "error", "skipped")]
    print("%s %s" % ("FAILED" if bad else "passed", c.get("name")))
PY
cat "$ROW_DIR/J-guard-test.txt" >> "$LOG"
HEAD="$(head -1 "$ROW_DIR/J-guard-test.txt")"
assert_contains "J: the test ran and passed — 0 failures" "failures=0 " "$HEAD"
assert_contains "J: … 0 errors" "errors=0 " "$HEAD"
assert_contains "J: … none skipped" "skipped=0 " "$HEAD"
assert_eq "J: the result is not older than the test's source or the guard's" "yes" "$([ "$RES" -nt "$SRC" ] && [ "$RES" -nt "$GUARD" ] && echo yes || echo no)"
jcase() { assert_contains "J: $1" "passed $2" "$(cat "$ROW_DIR/J-guard-test.txt")"; }
jcase "allowed — Tessera: every op (the editor)" theEditorMayInsertUpdateAndDeleteEventsAndRemindersInTessera
jcase "allowed — Tessera: Tess's delete included" tessDeleteOfATesseraEventIsAllowed
jcase "allowed — Tessera Birthdays by the Birthdays writer only" theBirthdaysWriterMayCreateItsCalendarAndWriteItsEvents
jcase "allowed — an allowed Sync target for a mapped copy only" syncMayUpdateAndDeleteAMappedCopyAndWriteItsReminders
jcase "allowed — the receiver's CalendarAlerts.STATE update" theReceiverMayUpdateAnAlertsState
jcase "refused — the editor → Birthdays" theEditorIsRefusedOnBirthdays
jcase "refused — the Birthdays writer → Tessera" theBirthdaysWriterIsRefusedOnTessera
jcase "refused — Sync → an id not allowed" syncToAnIdNotAllowedIsRefused
jcase "refused — Sync → an unmapped event" syncToAnAllowedIdButAnUnmappedEventIsRefused
jcase "refused — any op → an account calendar" everyOpOnAnAccountCalendarIsRefusedForEveryPathButSync
jcase "refused — Tess delete → an account calendar" tessDeleteOfAnAccountCalendarEventIsRefused
jcase "refused — any other write by the receiver" anyOtherWriteByTheReceiverIsRefused
jcase "refused — Sync → a target whose access level fell below 500 (D5)" syncToATargetWhoseAccessLevelFellBelow500IsRefusedAsReadOnly
jcase "refused — T16-12's stale mapping" aStaleMappingIsRefused

# ----------------------------------------------------------------------------------------------- L: the refusal, reached
# The adversarial trust review's leg (fix-round.md F15): no page offers a refused write, so the write layer's refusal is
# reached by moving a Tessera event to Work UNDER its open editor, as the sync adapter of Tessera's own account, and
# then tapping Save. It runs last: from here Work holds what adb put there.
log "--- L: a write the guard refuses, reached through an editor whose event was moved to Work under it (F15)"
copen; ctap cal_bar:today 1.2
ctap cal_bar:new 2
ctype_field title "Rehome"
ctap cal_editor_save 2.5
REHOME="$(event_id Rehome "$TESS")"
assert_ne "L: a Tessera event \"Rehome\" made in the editor" "" "$REHOME"
see_tessera
copen_event "$REHOME"
ctap cal_event_action:edit 2
ctype_field title "X"
dump_ui "$ROW_DIR/L-editor-before.xml"
assert_eq "L: its editor is open again with the title changed in the field, not saved" "RehomeX / Rehome" "$(ctext "$ROW_DIR/L-editor-before.xml" cal_editor_field:title) / $(cevents title "_id=$REHOME" | sed 's/^Row: 0 title=//')"
MOVE_OUT="$(q "content update --uri '$EVENTS?$SA&account_name=Tessera&account_type=LOCAL' --bind calendar_id:i:$WORK --where \"_id=$REHOME\"")"
record "L: the provider's answer to the sync-adapter move of the row to Work" "[${MOVE_OUT:-no output}]"
NOWCAL="$(cevents calendar_id "_id=$REHOME" | sed -n 's/.*calendar_id=\([0-9]*\).*/\1/p')"
if [ "$NOWCAL" = "$WORK" ]; then
  VARIANT=moved
  record "L: variant" "the provider moved the row: event $REHOME is now in Work (calendar $WORK)"
else
  VARIANT=reinserted
  record "L: variant" "the provider did not change calendar_id (it reads [$NOWCAL]); the Tessera event is deleted and \"Rehome\" re-inserted in Work — the editor's row id $REHOME is gone"
  cpurge "_id=$REHOME"
  cmkevent "$WORK" Rehome "$(cday_ms 1 12:00)" "$(cday_ms 1 13:00)" >/dev/null
fi
WORK_ROWS0="$(cevents _id:title:calendar_id "calendar_id=$WORK AND deleted=0" | sed 's/^Row: [0-9]* //' | sort | paste -sd';')"
note "Work's rows as adb put them: $WORK_ROWS0"
L_MARK="$(ring_mark)"
ctap cal_editor_save 3; dump_ui "$ROW_DIR/L-after-save.xml"; screencap "$ROW_DIR/L-after-save.png"
L_SLICE="$(ring_since "$L_MARK")"; printf '%s\n' "$L_SLICE" > "$ROW_DIR/L-slice.txt"
log "the write line: $(cline "$L_SLICE" '[calendar] write ')"
if [ "$VARIANT" = moved ]; then
  assert_contains "L: the slice holds [calendar] write update event=<id>: failed refused (not allowed)" "[calendar] write update event=$REHOME: failed refused (not allowed)" "$L_SLICE"
else
  assert_contains "L: the slice holds [calendar] write update event=<id>: failed the event is gone" "[calendar] write update event=$REHOME: failed the event is gone" "$L_SLICE"
fi
absent_in "L: … and no write … ok line for the Save" ": ok" "$(printf '%s\n' "$L_SLICE" | grep -F '[calendar] write ')"
assert_eq "L: cal_notice shows on the editor (the editor is still open)" "yes yes" "$(has_node "$ROW_DIR/L-after-save.xml" cal_notice) $(has_node "$ROW_DIR/L-after-save.xml" cal_editor)"
record "L: the notice's wording" "$(ctext "$ROW_DIR/L-after-save.xml" cal_notice)"
cal_lists "$WORK" "Work, after the refused Save"
assert_eq "L: Work's rows are exactly what adb put there" "$WORK_ROWS0" "$(cevents _id:title:calendar_id "calendar_id=$WORK AND deleted=0" | sed 's/^Row: [0-9]* //' | sort | paste -sd';')"
assert_eq "L: … the typed title is NOT on any Work row" "0" "$(cevent_count "calendar_id=$WORK AND title='RehomeX'")"
assert_eq "L: … nor anywhere in the provider" "0" "$(cevent_count "title='RehomeX'")"
assert_eq "L: Offsite's own row is still unchanged" "$OFFSITE_ROW" "$(cevents title:dtstart:dtend:calendar_id "_id=$OFFSITE")"
cal_lists "$PERSONAL" "Personal, after the refused Save"
assert_eq "L: Personal still holds 0" "" "$(ctitles "$PERSONAL")"

fi   # legs R2, J, L
# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
c6
cal_fixtures_down
cpurge "title IN ('Standup','Standup 2','standup','Intruder','Scratch','Rehome','RehomeX')"
assert_eq "restore: the test events — \"Standup 2\", \"standup\", \"Intruder\" — are deleted" "0" "$(cevent_count "title IN ('Standup','Standup 2','standup','Intruder','Scratch','Rehome','RehomeX')")"
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
ensure_start
row_end
