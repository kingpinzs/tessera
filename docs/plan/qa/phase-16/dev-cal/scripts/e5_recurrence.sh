#!/usr/bin/env bash
# Development proof of E5's core: an all-day event sits in the Day view's all-day band and not at a time; a 3-day
# event appears on each of its days; a weekly series of ten shows on its ten days; Repeat = weekly on a new event
# writes an rrule and a duration and no dtend; "this occurrence" writes an exception row, "this and following" sets
# UNTIL on the master and starts a new master, "delete all" removes the master and every instance. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E5_RECURRENCE "all-day, multi-day, a weekly series and the three occurrence edits"
c6
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; assert_ne "Tessera exists" "" "$TESS"
BEFORE="$(event_count "calendar_id=$TESS")"
TODAY="$(device_date 0)"
WEEK=$(( 7 * 86400000 ))

ALLDAY="$(mkevent "$TESS" 'E5 all day' "$(utc_day_ms 0)" "$(utc_day_ms 1)" --bind allDay:i:1 --bind eventTimezone:s:UTC)"
THREE="$(mkevent "$TESS" 'E5 three days' "$(day_ms 0 10:00)" "$(day_ms 2 11:00)")"
S0="$(day_ms 0 18:00)"
SERIES="$(mkseries "$TESS" 'E5 weekly' "$S0" 'FREQ=WEEKLY;COUNT=10' PT1H)"
note "all-day $ALLDAY three-day $THREE series $SERIES (first at $S0)"
assert_ne "the series was inserted" "" "$SERIES"

# ---- the all-day band, and the 3-day event on each of its days
open_day "$(day_ms 0 12:00)"; dump_ui "$ROW_DIR/day0.xml"
python3 - "$ROW_DIR/day0.xml" "$ALLDAY" "$TODAY" > "$ROW_DIR/allday_inside.txt" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
def b(rid):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"' % re.escape(rid), xml)
    return [int(x) for x in m.groups()] if m else None
band, ev = b("cal_allday:" + sys.argv[3]), b("cal_event:" + sys.argv[2])
print("yes" if band and ev and band[0] <= ev[0] and band[1] <= ev[1] and ev[2] <= band[2] and ev[3] <= band[3] else "no", band, ev)
PY
assert_contains "the all-day event lies inside cal_allday:$TODAY" "yes" "$(cat "$ROW_DIR/allday_inside.txt")"
assert_eq "and has no cal_event_time node" no "$(has_node "$ROW_DIR/day0.xml" "cal_event_time:$ALLDAY")"
for d in 0 1 2; do
  open_day "$(day_ms $d 12:00)"; dump_ui "$ROW_DIR/three_$d.xml"
  assert_eq "the 3-day event shows on day $d ($(device_date $d))" yes "$(has_node "$ROW_DIR/three_$d.xml" "cal_event:$THREE")"
done
open_day "$(day_ms 3 12:00)"; dump_ui "$ROW_DIR/three_3.xml"
assert_eq "and not on the day after" no "$(has_node "$ROW_DIR/three_3.xml" "cal_event:$THREE")"

# ---- the weekly series: ten instances, as the provider expands them
assert_eq "the provider expands ten instances" 10 "$(instance_count "$S0" "$(( S0 + 12 * WEEK ))" "$SERIES")"
SHOWN=0
for k in 0 1 2 3 4 5 6 7 8 9; do
  open_day "$(( S0 + k * WEEK ))"; dump_ui "$ROW_DIR/series_$k.xml"
  [ "$(has_node "$ROW_DIR/series_$k.xml" "cal_event:$SERIES")" = yes ] && SHOWN=$(( SHOWN + 1 ))
done
assert_eq "the Day view shows the series on each of its ten days" 10 "$SHOWN"
open_day "$(( S0 + 10 * WEEK ))"; dump_ui "$ROW_DIR/series_10.xml"
assert_eq "and not in the eleventh week" no "$(has_node "$ROW_DIR/series_10.xml" "cal_event:$SERIES")"
tap cal_bar:today; tap cal_bar:view; tap cal_view_pick:agenda 3; dump_ui "$ROW_DIR/agenda.xml"
log "agenda view line: $(line_of "$(ring_since "$ROW_MARK")" '[calendar] view agenda')"
record "cal_event:$SERIES nodes in the agenda's first screen" "$(count_nodes "$ROW_DIR/agenda.xml" "cal_event:$SERIES")"

# ---- Repeat = weekly in the editor: rrule + duration, no dtend
tap cal_bar:new 2
type_field title "E5 app weekly"
tap cal_editor_field:repeat 1.5; tap cal_editor_option:weekly 1.2
dump_ui "$ROW_DIR/editor_weekly.xml"
assert_eq "the Repeat field reads" "Every week" "$(field_text "$ROW_DIR/editor_weekly.xml" repeat)"
tap cal_editor_save 2
R="$(event_rows _id:rrule:duration:dtend "title='E5 app weekly'")"; log "app weekly: $R"
assert_contains "an rrule" "rrule=FREQ=WEEKLY" "$R"
assert_contains "a duration" "duration=P3600S" "$R"
assert_contains "and no dtend" "dtend=NULL" "$R"
APPW="$(event_ids "title='E5 app weekly'" | tr -d ' ')"

# ---- edit this occurrence, on the third instance
T3=$(( S0 + 2 * WEEK ))
open_event "$SERIES" "$T3" "$(( T3 + 3600000 ))"
tap cal_event_action:edit 1.5; dump_ui "$ROW_DIR/occurrence.xml"
assert_eq "the occurrence prompt offers this / following / all" "yes yes yes" "$(has_node "$ROW_DIR/occurrence.xml" cal_occurrence:this) $(has_node "$ROW_DIR/occurrence.xml" cal_occurrence:following) $(has_node "$ROW_DIR/occurrence.xml" cal_occurrence:all)"
tap cal_occurrence:this 2
type_field title "X"
MARK="$(ring_mark)"
tap cal_editor_save 2.5
EX="$(event_rows _id:title:original_id:originalInstanceTime:dtstart:dtend:rrule "original_id=$SERIES")"; log "exception: $EX"
assert_contains "an exception row of the master" "original_id=$SERIES, originalInstanceTime=$T3" "$EX"
assert_contains "with the changed title" "title=E5 weeklyX" "$EX"
EXID="$(printf '%s\n' "$EX" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
assert_contains "the write line" "[calendar] write insert event=$EXID: ok" "$(ring_since "$MARK")"
open_day "$T3"; dump_ui "$ROW_DIR/third.xml"
assert_eq "the third day shows the exception, not the master's instance" "yes no" "$(has_node "$ROW_DIR/third.xml" "cal_event:$EXID") $(has_node "$ROW_DIR/third.xml" "cal_event:$SERIES")"
assert_eq "with the changed title on that day only" "E5 weeklyX" "$(node_text "$ROW_DIR/third.xml" "cal_event_title:$EXID")"
open_day "$(( S0 + WEEK ))"; dump_ui "$ROW_DIR/second.xml"
assert_eq "the second day keeps the series' title" "E5 weekly" "$(node_text "$ROW_DIR/second.xml" "cal_event_title:$SERIES")"

# ---- this and following, from the fifth
T5=$(( S0 + 4 * WEEK ))
open_event "$SERIES" "$T5" "$(( T5 + 3600000 ))"
tap cal_event_action:edit 1.5; tap cal_occurrence:following 2
type_field title "T"
tap cal_editor_save 3
M="$(event_rows _id:title:rrule "_id=$SERIES")"; log "master after the split: $M"
assert_contains "UNTIL on the master" "UNTIL=" "$M"
assert_absent "and no COUNT left on it" "COUNT=" "$M"
TAIL="$(event_rows _id:title:rrule:dtstart:duration "title='E5 weeklyT'")"; log "new master: $TAIL"
assert_contains "a new master starts at the fifth occurrence" "dtstart=$T5" "$TAIL"
assert_contains "keeping what is left of the count (10 less the four before)" "rrule=FREQ=WEEKLY;COUNT=6" "$TAIL"
TAILID="$(printf '%s\n' "$TAIL" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
assert_eq "the old master now expands to three instances (the third is the exception's)" 3 "$(instance_count "$S0" "$(( S0 + 12 * WEEK ))" "$SERIES")"
assert_eq "the new master to six" 6 "$(instance_count "$S0" "$(( S0 + 12 * WEEK ))" "$TAILID")"

# ---- delete all: the new series, then the old one with its exception
open_event "$TAILID" "$T5" "$(( T5 + 3600000 ))"
tap cal_event_action:delete 1.5; tap cal_occurrence:all 2.5
assert_eq "the new master is gone" 0 "$(event_count "_id=$TAILID")"
assert_eq "with every instance" 0 "$(instance_count "$S0" "$(( S0 + 12 * WEEK ))" "$TAILID")"
open_event "$SERIES" "$S0" "$(( S0 + 3600000 ))"
tap cal_event_action:delete 1.5; tap cal_occurrence:all 2.5
assert_eq "the old master is gone" 0 "$(event_count "_id=$SERIES")"
record "exception rows left after delete all (deleted=0)" "$(event_rows _id:title:status:deleted "original_id=$SERIES" | tr '\n' ' ')"
assert_eq "no instance of the exception is left" 0 "$(instance_count "$S0" "$(( S0 + 12 * WEEK ))" "$EXID")"

# ---- restore: every event the session made, by id
rmevents "_id IN ($ALLDAY,$THREE,$APPW)"
rmevents "title LIKE 'E5 %'"
assert_eq "Tessera holds what it held before the session" "$BEFORE" "$(event_count "calendar_id=$TESS")"
c6; ensure_start
session_end
