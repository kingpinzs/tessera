#!/usr/bin/env bash
# Probe 3 (a record, not a proof), on a LOCAL series whose master carries a _sync_id (probe 2's working form): what an
# update of the rule alone does to the instances, whether EXDATE removes an occurrence, what a cancelled exception
# does, what a normal app's delete of an exception row does, whether an exception can be re-pointed at another master,
# and what the sync adapter's delete of a master leaves.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin P_EXCEPTION3 "probe: rule updates, EXDATE, cancelled and re-pointed exceptions, deletes"
TESS="$(tessera_id)"; WEEK=$(( 7 * 86400000 ))
S0="$(day_ms 0 18:00)"
TZ_ID="$(S getprop persist.sys.timezone)"
SAU="caller_is_syncadapter=true&account_name=Tessera&account_type=LOCAL"
inst() { S "content query --uri content://com.android.calendar/instances/when/$S0/$(( S0 + 12 * WEEK )) --projection event_id:begin --where \"title LIKE '$1%'\"" | sed 's/^Row: [0-9]* event_id=//; s/, begin=/@/' | sort | awk -v s="$S0" -v w="$WEEK" -F@ '{printf "%s@w%d ", $1, ($2 - s) / w}'; }
rows() { S "content query --uri '$EVENTS?$SAU' --projection _id:title:_sync_id:original_id:original_sync_id:originalInstanceTime:eventStatus:deleted:rrule:exdate --where \"title LIKE '$1%'\"" | sed 's/^Row: [0-9]* //' | tr '\n' ';'; }
utc() { date -u -d "@$(( $1 / 1000 ))" +%Y%m%dT%H%M%SZ; }

M="$(mkseries "$TESS" 'PW3 weekly' "$S0" 'FREQ=WEEKLY;COUNT=10' PT1H)"
adb shell "content update --uri '$EVENTS/$M?$SAU' --bind _sync_id:s:tessera-$M" < /dev/null
adb shell "content update --uri $EVENTS/$M --bind rrule:s:'FREQ=WEEKLY;UNTIL=$(utc $(( S0 + 8 * WEEK - 1000 )))'" < /dev/null
record "(a) the rule alone updated to end before week 8" "$(inst PW3)"
adb shell "content update --uri $EVENTS/$M --bind rrule:s:'FREQ=WEEKLY;UNTIL=$(utc $(( S0 + 8 * WEEK - 1000 )))' --bind dtstart:l:$S0 --bind duration:s:PT1H" < /dev/null
record "(b) the rule with DTSTART and DURATION" "$(inst PW3)"
adb shell "content update --uri $EVENTS/$M --bind exdate:s:$(utc $(( S0 + 5 * WEEK ))) --bind dtstart:l:$S0 --bind duration:s:PT1H --bind rrule:s:'FREQ=WEEKLY;UNTIL=$(utc $(( S0 + 8 * WEEK - 1000 )))'" < /dev/null
record "(c) EXDATE for week 5" "$(inst PW3)"
OUT="$(adb shell "content insert --uri content://com.android.calendar/exception/$M --bind originalInstanceTime:l:$(( S0 + 3 * WEEK )) --bind eventStatus:i:2" < /dev/null 2>&1 | tail -2 | tr '\n' ' ')"
record "(d) a CANCELLED exception for week 3: insert said [$OUT]" "$(inst PW3) rows: $(rows PW3)"
adb shell "content insert --uri content://com.android.calendar/exception/$M --bind originalInstanceTime:l:$(( S0 + 2 * WEEK )) --bind title:s:'PW3 changed' --bind eventStatus:i:1" < /dev/null
EX="$(S "content query --uri $EVENTS --projection _id --where \"title='PW3 changed'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tail -1)"
record "(e) an edited exception $EX for week 2" "$(inst PW3)"
adb shell "content delete --uri $EVENTS/$EX" < /dev/null
record "(f) that exception deleted by a normal app" "$(inst PW3) rows: $(rows 'PW3 changed')"
# (g) a second master, and week 6's exception of the first re-pointed at it by the sync adapter
adb shell "content insert --uri content://com.android.calendar/exception/$M --bind originalInstanceTime:l:$(( S0 + 6 * WEEK )) --bind title:s:'PW3 six' --bind eventStatus:i:1" < /dev/null
SIX="$(S "content query --uri $EVENTS --projection _id --where \"title='PW3 six'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tail -1)"
adb shell "content update --uri $EVENTS/$M --bind rrule:s:'FREQ=WEEKLY;UNTIL=$(utc $(( S0 + 4 * WEEK - 1000 )))' --bind dtstart:l:$S0 --bind duration:s:PT1H" < /dev/null
N="$(mkseries "$TESS" 'PW3 tail' "$(( S0 + 4 * WEEK ))" 'FREQ=WEEKLY;COUNT=6' PT1H)"
adb shell "content update --uri '$EVENTS/$N?$SAU' --bind _sync_id:s:tessera-$N" < /dev/null
record "(g1) the master ended before week 4 and a tail series $N made; week 6's exception $SIX still points at the old master" "$(inst PW3)"
adb shell "content update --uri '$EVENTS/$SIX?$SAU' --bind original_id:i:$N --bind original_sync_id:s:tessera-$N" < /dev/null
record "(g2) the exception re-pointed (sync adapter), nothing else" "$(inst PW3)"
adb shell "content update --uri $EVENTS/$N --bind rrule:s:'FREQ=WEEKLY;COUNT=6' --bind dtstart:l:$(( S0 + 4 * WEEK )) --bind duration:s:PT1H" < /dev/null
record "(g3) and the tail series written again" "$(inst PW3)"
adb shell "content delete --uri '$EVENTS/$N?$SAU'" < /dev/null
record "(h) the tail master deleted by the sync adapter" "$(inst PW3) rows: $(rows PW3)"
adb shell "content delete --uri '$EVENTS?$SAU' --where \"title LIKE 'PW3%'\"" < /dev/null
record "probe rows left" "$(rows PW3)"
session_end
