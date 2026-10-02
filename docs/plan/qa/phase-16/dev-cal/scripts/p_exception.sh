#!/usr/bin/env bash
# Probe (a record, not a proof): what Android's CalendarProvider does to a LOCAL series' instances when a normal app
# inserts an exception through content://com.android.calendar/exception/<master> — the route the write layer uses for
# "edit this occurrence". Everything is recorded; the series is deleted at the end.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin P_EXCEPTION "probe: the provider's exception insert and the master's instances"
TESS="$(tessera_id)"; WEEK=$(( 7 * 86400000 ))
S0="$(day_ms 0 18:00)"
M="$(mkseries "$TESS" 'Probe weekly' "$S0" 'FREQ=WEEKLY;COUNT=10' PT1H)"
inst() { S "content query --uri content://com.android.calendar/instances/when/$S0/$(( S0 + 12 * WEEK )) --projection event_id:begin:title --where \"event_id=$1 OR title LIKE 'Probe%'\"" | sed 's/^Row: [0-9]* //' | sort | tr '\n' ';'; }
record "instances after the insert of the series $M" "$(inst "$M")"
T3=$(( S0 + 2 * WEEK ))
adb shell "content insert --uri content://com.android.calendar/exception/$M --bind originalInstanceTime:l:$T3 --bind title:s:'Probe changed' --bind eventStatus:i:1" < /dev/null
record "event rows" "$(event_rows _id:title:original_id:originalInstanceTime:original_sync_id:_sync_id:dtstart:dtend:duration:rrule:eventStatus:lastDate "title LIKE 'Probe%'" | tr '\n' ';')"
record "instances right after the exception insert" "$(inst "$M")"
record "instances in a WIDER window (forces an expansion)" "$(S "content query --uri content://com.android.calendar/instances/when/$(( S0 - 400 * 86400000 ))/$(( S0 + 800 * 86400000 )) --projection event_id:begin:title --where \"title LIKE 'Probe%'\"" | sed 's/^Row: [0-9]* //' | sort | tr '\n' ';')"
record "instances in the first window again" "$(inst "$M")"
# The same master touched with its own DTSTART (an update that names DTSTART re-expands it).
adb shell "content update --uri $EVENTS/$M --bind dtstart:l:$S0" < /dev/null
record "instances after an update of the master that names DTSTART" "$(inst "$M")"
rmevents "title LIKE 'Probe%'"
record "probe rows left" "$(event_count "title LIKE 'Probe%'")"
session_end
