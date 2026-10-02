#!/usr/bin/env bash
# Probe 2 (a record, not a proof): which way of writing an occurrence edit leaves a LOCAL series' instances right on
# this provider. V1: the exception URI, then the master written again whole. V2: the master given a _sync_id (the LOCAL
# account's own sync adapter may), then the exception URI. V3: as V2, then the master written again whole.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin P_EXCEPTION2 "probe: three ways to write an occurrence edit on a LOCAL series"
TESS="$(tessera_id)"; WEEK=$(( 7 * 86400000 ))
S0="$(day_ms 0 18:00)"; T3=$(( S0 + 2 * WEEK ))
TZ_ID="$(S getprop persist.sys.timezone)"
SAU="caller_is_syncadapter=true&account_name=Tessera&account_type=LOCAL"
inst() { S "content query --uri content://com.android.calendar/instances/when/$S0/$(( S0 + 12 * WEEK )) --projection event_id:begin --where \"title LIKE '$1%'\"" | sed 's/^Row: [0-9]* event_id=//; s/, begin=/@/' | sort | awk -v s="$S0" -v w="$WEEK" -F@ '{printf "%s@w%d ", $1, ($2 - s) / w}'; }
touch_master() { adb shell "content update --uri $EVENTS/$1 --bind dtstart:l:$S0 --bind rrule:s:'FREQ=WEEKLY;COUNT=10' --bind duration:s:PT1H --bind eventTimezone:s:$TZ_ID" < /dev/null; }

M1="$(mkseries "$TESS" 'PV1 weekly' "$S0" 'FREQ=WEEKLY;COUNT=10' PT1H)"
adb shell "content insert --uri content://com.android.calendar/exception/$M1 --bind originalInstanceTime:l:$T3 --bind title:s:'PV1 changed' --bind eventStatus:i:1" < /dev/null
record "V1 after the exception insert" "$(inst PV1)"
touch_master "$M1"
record "V1 after the master is written again whole" "$(inst PV1)"

M2="$(mkseries "$TESS" 'PV2 weekly' "$S0" 'FREQ=WEEKLY;COUNT=10' PT1H)"
adb shell "content update --uri '$EVENTS/$M2?$SAU' --bind _sync_id:s:tessera-$M2" < /dev/null
record "V2 master row" "$(event_rows _id:_sync_id:dirty "_id=$M2")"
adb shell "content insert --uri content://com.android.calendar/exception/$M2 --bind originalInstanceTime:l:$T3 --bind title:s:'PV2 changed' --bind eventStatus:i:1" < /dev/null
record "V2 rows" "$(event_rows _id:title:_sync_id:original_id:original_sync_id:originalInstanceTime "title LIKE 'PV2%'" | tr '\n' ';')"
record "V2 after the exception insert (master has a _sync_id)" "$(inst PV2)"
touch_master "$M2"
record "V3 = V2, then the master written again whole" "$(inst PV2)"
# A cancelled occurrence the same way (delete this occurrence).
adb shell "content insert --uri content://com.android.calendar/exception/$M2 --bind originalInstanceTime:l:$(( S0 + 5 * WEEK )) --bind eventStatus:i:2" < /dev/null
record "V2 after a CANCELLED exception for week 5" "$(inst PV2)"
touch_master "$M2"
record "V2 cancelled, master written again" "$(inst PV2)"
# Deleting a master that has a _sync_id, as a normal app and as the sync adapter.
adb shell "content delete --uri $EVENTS/$M2" < /dev/null
record "V2 master after a normal app's delete" "$(event_rows _id:deleted:dirty "_id=$M2" | tr '\n' ';') / exceptions: $(S "content query --uri '$EVENTS?$SAU' --projection _id:deleted:eventStatus --where \"original_id=$M2\"" | tr '\n' ';')"
adb shell "content delete --uri '$EVENTS?$SAU' --where \"title LIKE 'PV%'\"" < /dev/null
rmevents "title LIKE 'PV%'"
record "probe rows left (as the sync adapter sees them)" "$(S "content query --uri '$EVENTS?$SAU' --projection _id:title:deleted --where \"title LIKE 'PV%'\"" | tr '\n' ';')"
session_end
