#!/usr/bin/env bash
# Phase 16 E5 — all-day, multi-day, recurring (structure here; geometry is E19's).
#
#   A  driver inserts   an all-day event (allDay 1, dtstart at UTC midnight, eventTimezone UTC), a 3-day timed event, a
#                       weekly series (rrule FREQ=WEEKLY;COUNT=10, duration PT1H, no dtend)
#   B  the all-day band the Day view shows the all-day event inside cal_allday:<date>'s bounds, with no cal_event_time
#   C  the 3-day event  on each of its days
#   D  the series       the Agenda lists ten cal_event instances of it; the provider's instances/when agrees
#   E  Repeat = weekly  in the app, on a new event: an rrule and a duration and no dtend
#   F  this occurrence  on the third instance: an exception row (original_id = the master, originalInstanceTime = that
#                       instance); the Agenda shows the changed title on that day only
#   G  this & following from the fifth: UNTIL on the master (query), a new master
#   H  delete all       the master and every instance gone
#   restore             every event the row inserted or created deleted by id; Tessera's count as before the row
#
# An occurrence's time is read from Instances, never start + k weeks: the zone's DST change falls inside the series.
#
# Leg D also walks the Agenda PAST its loading steps (gate review A, finding 2: the list was thrown back to the selected
# day each time it loaded more weeks — 8 → 16 → 32 → 56): leg A inserts one event in week 9, 17, 33 and 40 from today,
# and the walk asserts that the first date of each dump never goes backwards and that all four are reached.
# E5_LEGS=D runs legs A and D and the restore alone — a narrow re-run; the log's first RECORD says so.
set -uo pipefail
LEGS="${E5_LEGS:-all}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"

row_begin E5 "all-day, multi-day and recurring events; the three occurrence scopes"
if [ "$LEGS" != all ]; then
  [ "$LEGS" = D ] || { _verdict FAIL "E5_LEGS" "only E5_LEGS=D is a narrow run of this row (got $LEGS)"; row_end; exit 1; }
  record "legs run" "A (the driver's inserts) and D (the series in the Agenda; the walk past weeks 8, 16 and 32) ONLY — a narrow re-run; legs B, C, E–I stand on the row's earlier run"
fi
c6; ensure_start
copen
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists" "" "$TESS"
BEFORE="$(ctessera_count)"
TODAY="$(cdate 0)"
WEEK=$(( 7 * 86400000 ))
MADE=""   # every event id the row inserted or created

# ----------------------------------------------------------------------------------------------- A: the driver's inserts
log "--- A: the driver's inserts"
ALLDAY="$(cmkevent "$TESS" 'E5 all day' "$(cutc_day_ms 0)" "$(cutc_day_ms 1)" --bind allDay:i:1 --bind eventTimezone:s:UTC)"
THREE="$(cmkevent "$TESS" 'E5 three days' "$(clocal_ms "$TODAY" 10:00)" "$(clocal_ms "$(cdate 2)" 11:00)")"
S0="$(clocal_ms "$TODAY" 18:00)"
SERIES="$(cmkseries "$TESS" 'E5 weekly' "$S0" 'FREQ=WEEKLY;COUNT=10' PT1H)"
MADE="$ALLDAY $THREE $SERIES"
# One event in each of weeks 9, 17, 33 and 40 from today: past each step of the Agenda's loaded window (8, 16, 32 weeks).
FAR=""
for wk in 9 17 33 40; do
  fs="$(clocal_ms "$(cdate $(( wk * 7 )))" 09:00)"
  fid="$(cmkevent "$TESS" "E5 week $wk" "$fs" $(( fs + 3600000 )))"
  FAR="$FAR $wk:$fid:$(cdate $(( wk * 7 )))"; MADE="$MADE $fid"
done
note "the far events (week:id:date):$FAR"
assert_eq "A: an event in each of weeks 9, 17, 33 and 40 from today" "4" "$(cevent_count "calendar_id=$TESS AND deleted=0 AND title LIKE 'E5 week %'")"
note "all-day $ALLDAY, three-day $THREE, series $SERIES (first at $S0)"
RA="$(cevents _id:allDay:dtstart:eventTimezone "_id=${ALLDAY:-0}")"
assert_contains "A: the all-day event — allDay 1, dtstart at UTC midnight, eventTimezone UTC" "allDay=1, dtstart=$(cutc_day_ms 0), eventTimezone=UTC" "$RA"
assert_ne "A: the 3-day timed event is inserted" "" "$THREE"
RS="$(cevents _id:rrule:duration:dtend "_id=${SERIES:-0}")"
assert_contains "A: the weekly series — rrule FREQ=WEEKLY;COUNT=10, duration PT1H, no dtend" "rrule=FREQ=WEEKLY;COUNT=10, duration=PT1H, dtend=NULL" "$RS"
RANGE_TO=$(( S0 + 12 * WEEK ))
cinstance_times "$S0" "$RANGE_TO" "$SERIES" > "$ROW_DIR/A-instances.txt"
note "the series' instances from the provider: $(tr '\n' ';' < "$ROW_DIR/A-instances.txt")"
inst() { sed -n "${1}p" "$ROW_DIR/A-instances.txt"; }   # k (1-based) -> "begin end"

if [ "$LEGS" = all ]; then
# ----------------------------------------------------------------------------------------------- B: the all-day band
log "--- B: the Day view's all-day band"
copen_day "$(clocal_ms "$TODAY" 12:00)"; dump_ui "$ROW_DIR/B-day.xml"; screencap "$ROW_DIR/B-day.png"
D="$ROW_DIR/B-day.xml"
assert_eq "B: the Day view is showing (cal_view_mode:day selected)" "true" "$(cattr "$D" cal_view_mode:day selected)"
assert_eq "B: the band cal_allday:$TODAY exists" "yes" "$(has_node "$D" "cal_allday:$TODAY")"
assert_eq "B: the all-day event's cal_event:<id> node is on the Day view" "yes" "$(has_node "$D" "cal_event:$ALLDAY")"
W="$(cwithin "$D" "cal_allday:$TODAY" "cal_event:$ALLDAY")"; note "band, event bounds: $W"
assert_eq "B: … and lies inside cal_allday:<that date>'s bounds" "yes" "${W%% *}"
assert_eq "B: … and not at a time: no cal_event_time:<id> node exists for it (r3 V8)" "no" "$(has_node "$D" "cal_event_time:$ALLDAY")"
assert_eq "B: the control — a timed event on the same view does carry cal_event_time (the 3-day event)" "yes" "$(has_node "$D" "cal_event_time:$THREE")"
WT="$(cwithin "$D" "cal_allday:$TODAY" "cal_event:$THREE")"
record "B: the timed 3-day event against the band (its first day; the band holds all-day events)" "$WT"

# ----------------------------------------------------------------------------------------------- C: the 3-day event
log "--- C: the 3-day event appears on each of its days"
for d in 0 1 2; do
  copen_day "$(clocal_ms "$(cdate $d)" 12:00)"; dump_ui "$ROW_DIR/C-day$d.xml"
  assert_eq "C: the 3-day event shows on day $((d + 1)) of 3 ($(cdate $d))" "yes" "$(has_node "$ROW_DIR/C-day$d.xml" "cal_event:$THREE")"
done
copen_day "$(clocal_ms "$(cdate 3)" 12:00)"; dump_ui "$ROW_DIR/C-day3.xml"
assert_eq "C: the control — and not on the day after ($(cdate 3))" "no" "$(has_node "$ROW_DIR/C-day3.xml" "cal_event:$THREE")"

fi   # legs B and C
# ----------------------------------------------------------------------------------------------- D: the series in the Agenda
log "--- D: the Agenda lists ten instances of the series; the provider agrees"
PN="$(cinstances "$S0" "$RANGE_TO" "$SERIES")"
assert_eq "D: content query instances/when/<start>/<end> — ten instances of the series" "10" "$PN"
# A fresh Agenda, so its window starts at its first step and the further weeks are loaded BY the walk: the shell is
# stopped and Calendar opened again (an Agenda left open over the empty calendar had already loaded every step).
c6; copen
ctap cal_bar:today 1.2; cview agenda 2.5
D_MARK="$(ring_mark)"
cagenda_walk "$ROW_DIR/D-agenda.tsv" 60
ring_since "$D_MARK" | grep -F '[calendar] view agenda ' > "$ROW_DIR/D-view-slice.txt"
AN="$(awk -F'\t' -v id="$SERIES" '$2 == id' "$ROW_DIR/D-agenda.tsv" | wc -l | tr -d ' ')"
log "the Agenda's rows for the series: $(awk -F'\t' -v id="$SERIES" '$2 == id {printf "%s ", $1}' "$ROW_DIR/D-agenda.tsv")"
assert_eq "D: the agenda lists ten cal_event: instances of the series" "10" "$AN"
assert_eq "D: … the provider's count agrees with the agenda's" "$PN" "$AN"
WANT_DAYS="$(while read -r b e; do cdate_of "$b"; done < "$ROW_DIR/A-instances.txt" | tr '\n' ' ' | sed 's/ $//')"
assert_eq "D: … on the provider's ten days" "$WANT_DAYS" "$(awk -F'\t' -v id="$SERIES" '$2 == id {print $1}' "$ROW_DIR/D-agenda.tsv" | sort | tr '\n' ' ' | sed 's/ $//')"
# The walk itself: the list of day groups scrolls on while more weeks load — it is never thrown back.
FIRSTS="$(awk -F'\t' 'NF >= 3 && !($1 in f) { f[$1] = $2; n[++k] = $1 } END { for (i = 1; i <= k; i++) printf "%s%s", (i > 1 ? " " : ""), f[n[i]] }' "$ROW_DIR/D-agenda.tsv.dumps")"
log "the first date of each dump of the walk: $FIRSTS"
WINDOWS="$(sed -n 's/.*view agenda \([0-9-]*\.\.[0-9-]*\):.*/\1/p' "$ROW_DIR/D-view-slice.txt" | awk '!seen[$0]++' | tr '\n' ' ' | sed 's/ $//')"
record "D: the Agenda's windows loaded DURING the walk (view agenda lines since the walk's MARK: from..to, in order)" "$WINDOWS"
assert_eq "D: the walk — more weeks were loaded while it scrolled: at least three wider windows since its MARK (the steps past weeks 8, 16 and 32)" "yes" "$([ "$(echo "$WINDOWS" | wc -w)" -ge 3 ] && echo yes || echo "no ($WINDOWS)")"
assert_eq "D: the walk — the first date of each dump never goes backwards (the list is not thrown back when more weeks load)" "yes" "$(echo "$FIRSTS" | tr ' ' '\n' | awk 'NR > 1 && $1 < prev { printf "no (dump %d shows %s after %s)", NR - 1, $1, prev; bad = 1; exit } { prev = $1 } END { if (!bad) print "yes" }')"
assert_eq "D: … it made more than one dump (the list did scroll)" "yes" "$([ "$(echo "$FIRSTS" | wc -w)" -gt 3 ] && echo yes || echo "no ($FIRSTS)")"
for f in $FAR; do
  wk="${f%%:*}"; rest="${f#*:}"; fid="${rest%%:*}"; fdate="${rest#*:}"
  assert_eq "D: … the walk gets past week $(( wk - 1 )): the event of week $wk is reached, on its date" "$fdate" "$(awk -F'\t' -v id="$fid" '$2 == id {print $1}' "$ROW_DIR/D-agenda.tsv")"
done

if [ "$LEGS" = all ]; then

# ----------------------------------------------------------------------------------------------- E: Repeat = weekly in the app
log "--- E: Repeat = weekly on a new event, in the app"
ctap cal_bar:today 1.2
ctap cal_bar:new 2
ctype_field title "E5 app weekly"
ctap cal_editor_field:repeat 1.5; ctap cal_editor_option:weekly 1.2
dump_ui "$ROW_DIR/E-editor.xml"
note "the Repeat field reads [$(cfield "$ROW_DIR/E-editor.xml" repeat)]"
ctap cal_editor_save 2.5
RE="$(cevents _id:rrule:duration:dtend "title='E5 app weekly'")"; log "the app's weekly event: $RE"
APPW="$(event_id 'E5 app weekly' "$TESS")"; MADE="$MADE $APPW"
assert_contains "E: it writes an rrule (weekly)" "rrule=FREQ=WEEKLY" "$RE"
assert_absent "E: … and a duration (not NULL)" "duration=NULL" "$RE"
assert_contains "E: … a duration of the default hour" "duration=P" "$RE"
assert_contains "E: … and no dtend" "dtend=NULL" "$RE"

# ----------------------------------------------------------------------------------------------- F: edit this occurrence
log "--- F: \"edit this occurrence\" on the third instance"
read -r B3 E3 <<< "$(inst 3)"; DAY3="$(cdate_of "$B3")"
copen_event "$SERIES" "$B3" "$E3"
ctap cal_event_action:edit 1.5; dump_ui "$ROW_DIR/F-prompt.xml"
assert_eq "F: the prompt offers this occurrence" "yes" "$(has_node "$ROW_DIR/F-prompt.xml" cal_occurrence:this)"
ctap cal_occurrence:this 2
ctype_field title "X"
F_MARK="$(ring_mark)"
ctap cal_editor_save 3
EX="$(cevents _id:title:original_id:originalInstanceTime:dtstart "original_id=$SERIES")"; log "exception rows: $EX"
assert_eq "F: it writes an exception row — exactly one row whose original_id is the master" "1" "$(printf '%s\n' "$EX" | grep -c '_id=')"
assert_contains "F: … original_id = the master" "original_id=$SERIES," "$EX"
assert_contains "F: … originalInstanceTime = that instance" "originalInstanceTime=$B3," "$EX"
EXID="$(printf '%s\n' "$EX" | sed -n 's/^Row: [0-9]* _id=\([0-9]*\),.*/\1/p' | head -1)"; MADE="$MADE $EXID"
EXTITLE="$(printf '%s\n' "$EX" | sed -n 's/.* title=\(.*\), original_id=.*/\1/p' | head -1)"
assert_ne "F: … with a changed title" "E5 weekly" "$EXTITLE"
copen; ctap cal_bar:today 1.2; cview agenda 2.5
cagenda_walk "$ROW_DIR/F-agenda.tsv"
log "the Agenda after the edit: $(awk -F'\t' '$3 ~ /^E5 weekly/ {printf "%s=%s(%s) ", $1, $3, $2}' "$ROW_DIR/F-agenda.tsv")"
assert_eq "F: the agenda shows the changed title on that day ($DAY3)" "$EXTITLE" "$(awk -F'\t' -v d="$DAY3" -v id="$EXID" '$1 == d && $2 == id {print $3}' "$ROW_DIR/F-agenda.tsv")"
assert_eq "F: … on that day only: the changed title appears once in the whole agenda" "1" "$(awk -F'\t' -v t="$EXTITLE" '$3 == t' "$ROW_DIR/F-agenda.tsv" | wc -l | tr -d ' ')"
assert_eq "F: … that day no longer shows the series' own instance" "0" "$(awk -F'\t' -v d="$DAY3" -v id="$SERIES" '$1 == d && $2 == id' "$ROW_DIR/F-agenda.tsv" | wc -l | tr -d ' ')"
assert_eq "F: … and the series' other nine days keep the series' title" "9" "$(awk -F'\t' -v id="$SERIES" '$2 == id && $3 == "E5 weekly"' "$ROW_DIR/F-agenda.tsv" | wc -l | tr -d ' ')"

# ----------------------------------------------------------------------------------------------- G: this and following
log "--- G: \"this and following\" from the fifth"
read -r B5 E5 <<< "$(inst 5)"
copen_event "$SERIES" "$B5" "$E5"
ctap cal_event_action:edit 1.5; ctap cal_occurrence:following 2
ctype_field title "T"
ctap cal_editor_save 3
M="$(cevents _id:title:rrule "_id=$SERIES")"; log "the master after the split: $M"
assert_contains "G: it sets UNTIL on the master (the query shows it)" "UNTIL=" "$M"
NEWM="$(cevents _id:title:rrule:dtstart:original_id "calendar_id=$TESS AND deleted=0 AND dtstart=$B5 AND rrule IS NOT NULL AND _id!=$SERIES")"; log "the new master: $NEWM"
assert_eq "G: … and creates a new master — one new repeating row starting at the fifth occurrence" "1" "$(printf '%s\n' "$NEWM" | grep -c '_id=')"
assert_contains "G: … a master, not an exception (original_id NULL)" "original_id=NULL" "$NEWM"
TAIL="$(printf '%s\n' "$NEWM" | sed -n 's/^Row: [0-9]* _id=\([0-9]*\),.*/\1/p' | head -1)"; MADE="$MADE $TAIL"
record "G: the old master's and the new master's instances after the split (provider)" "$(cinstances "$S0" "$RANGE_TO" "$SERIES") + exception $(cinstances "$S0" "$RANGE_TO" "$EXID") / new $(cinstances "$S0" "$RANGE_TO" "${TAIL:-0}")"
read -r B4 E4 <<< "$(inst 4)"
assert_eq "G: … the old master's last instance is the fourth (nothing of it at or after the fifth)" "0" "$(cinstances "$B5" "$RANGE_TO" "$SERIES")"
assert_eq "G: … the fourth still stands" "1" "$(cinstances "$B4" "$E4" "$SERIES")"

# ----------------------------------------------------------------------------------------------- H: delete all
log "--- H: \"delete all\" removes the master and every instance"
read -r B1 E1 <<< "$(inst 1)"
copen_event "$SERIES" "$B1" "$E1"
ctap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/H-prompt.xml"
assert_eq "H: the prompt offers all" "yes" "$(has_node "$ROW_DIR/H-prompt.xml" cal_occurrence:all)"
H_MARK="$(ring_mark)"
ctap cal_occurrence:all 3
assert_eq "H: the master is removed (no live events row)" "0" "$(cevent_count "_id=$SERIES AND deleted=0")"
assert_eq "H: … and every instance of it" "0" "$(cinstances "$S0" "$RANGE_TO" "$SERIES")"
assert_eq "H: … the changed occurrence's instance too (it was the series')" "0" "$(cinstances "$S0" "$RANGE_TO" "$EXID")"
record "H: rows left that name the old master (any state)" "$(cevents _id:deleted:original_id "_id=$SERIES OR original_id=$SERIES" | tr '\n' ';')"
copen; ctap cal_bar:today 1.2; cview agenda 2.5
cagenda_walk "$ROW_DIR/H-agenda.tsv"
assert_eq "H: … and the Agenda lists no row of the deleted series" "0" "$(awk -F'\t' -v a="$SERIES" -v b="$EXID" '$2 == a || $2 == b' "$ROW_DIR/H-agenda.tsv" | wc -l | tr -d ' ')"

# ----------------------------------------------------------------------------------------------- I: a scoped delete with no occurrence
# The lead's added step (fix-round.md F29, found by the builder as a data-loss defect and fixed in build 3c1ad1e0): a
# weekly Tessera series opened by a VIEW with NO occurrence extras, Delete → "this occurrence" — it is about the first
# occurrence, never the whole series.
log "--- I: a series page opened with no occurrence: Delete → this occurrence leaves the series (F29)"
I0="$(clocal_ms "$(cdate 1)" 08:00)"
ISER="$(cmkseries "$TESS" 'E5 scoped' "$I0" 'FREQ=WEEKLY;COUNT=4' PT1H)"; MADE="$MADE $ISER"
cinstance_times "$I0" $(( I0 + 5 * WEEK )) "$ISER" > "$ROW_DIR/I-instances.txt"
assert_eq "I: a weekly Tessera series of four" "4" "$(grep -c . "$ROW_DIR/I-instances.txt")"
read -r IB1 IE1 <<< "$(sed -n 1p "$ROW_DIR/I-instances.txt")"
adb shell am start -W -n "$CALENDAR_ACTIVITY" -a android.intent.action.VIEW -d "content://com.android.calendar/events/$ISER" < /dev/null > "$ROW_DIR/I-view.out" 2>&1; sleep 2.5
dump_ui "$ROW_DIR/I-page.xml"
assert_eq "I: a VIEW with no extras opens the series' page (cal_event_page:<id>)" "yes" "$(has_node "$ROW_DIR/I-page.xml" "cal_event_page:$ISER")"
record "I: the time the page shows (no occurrence was named)" "$(ctext "$ROW_DIR/I-page.xml" "cal_event_time:$ISER")"
ctap cal_event_action:delete 1.5; dump_ui "$ROW_DIR/I-prompt.xml"
assert_eq "I: Delete offers \"this occurrence\"" "yes" "$(has_node "$ROW_DIR/I-prompt.xml" cal_occurrence:this)"
I_MARK="$(ring_mark)"
ctap cal_occurrence:this 3
log "$(cline "$(ring_since "$I_MARK")" '[calendar] write ')"
assert_eq "I: the master row is still there" "1" "$(cevent_count "_id=$ISER AND deleted=0")"
assert_contains "I: … its rule untouched" "rrule=FREQ=WEEKLY;COUNT=4" "$(cevents rrule "_id=$ISER")"
IEX="$(cevents _id:original_id:originalInstanceTime:eventStatus "original_id=$ISER")"; log "exception rows: $IEX"
assert_eq "I: one exception row" "1" "$(printf '%s\n' "$IEX" | grep -c '_id=')"
assert_contains "I: … cancelled (eventStatus 2), for the first occurrence" "originalInstanceTime=$IB1, eventStatus=2" "$IEX"
MADE="$MADE $(printf '%s\n' "$IEX" | sed -n 's/^Row: [0-9]* _id=\([0-9]*\),.*/\1/p' | head -1)"
cinstance_times "$I0" $(( I0 + 5 * WEEK )) "$ISER" > "$ROW_DIR/I-instances-after.txt"
assert_eq "I: the other occurrences are still there (three instances left)" "3" "$(grep -c . "$ROW_DIR/I-instances-after.txt")"
assert_eq "I: … exactly the second, third and fourth" "$(sed -n '2,4p' "$ROW_DIR/I-instances.txt")" "$(cat "$ROW_DIR/I-instances-after.txt")"

fi   # legs E to I
# ----------------------------------------------------------------------------------------------- restore
log "--- restore (r3 V10): every event the row inserted or created, by id"
c6
IDS="$(echo $MADE | tr ' ' ',')"
note "ids made by the row: $IDS"
cpurge "_id IN ($IDS)"
assert_eq "restore: none of the row's events is left (any state)" "0" "$(cevent_count "_id IN ($IDS) OR original_id IN ($IDS)")"
assert_eq "restore: the count of Tessera's events equals the count before the row" "$BEFORE" "$(ctessera_count)"
ensure_start
row_end
