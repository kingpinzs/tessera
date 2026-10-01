#!/usr/bin/env bash
# Phase 16, Decisions "Verify at build start": five Android facts the round-3 fixes took from memory, checked on the AVD
# before the task that rests on each is built. Facts are RECORDed; the two that decide a design the doc already fixed
# (the receiver's data filter, the 29 February rule) are asserted, so a wrong one stops the build here.
#
#   1  the reminder broadcast's data URI form (r3 D6: the receiver's scheme + host filter must match what is sent)
#   2  whether the provider gates a normal app's insert on the target calendar's access level (r3 D5)
#   3  whether non-LOCAL calendars with no account survive a restart of the provider's process (r3 V11)
#   4  what a phone-only raw contact's account is on this image (Q-16-3)
#   5  a 29 February birthday's yearly rule lands on February's last day (r3 D14; re-cut by run 2, see check 5)
. "$(dirname "$0")/lib.sh"
export ANDROID_SERIAL=emulator-5554

S() { adb shell "$@" | tr -d '\r'; }
CAL=content://com.android.calendar/calendars
EVENTS=content://com.android.calendar/events
SA="caller_is_syncadapter=true"
# One quoted string per command whose URI carries an `&` (J6 run 1: unquoted, the device shell backgrounds it).
mkcal() { # account-name account-type display access-level
  adb shell "content insert --uri '$CAL?$SA&account_name=$1&account_type=$2' --bind account_name:s:$1 --bind account_type:s:$2 --bind name:s:'$3' --bind calendar_displayName:s:'$3' --bind calendar_access_level:i:$4 --bind ownerAccount:s:$1 --bind visible:i:1 --bind sync_events:i:1 --bind calendar_color:i:-16776961"
}
rmcal() { adb shell "content delete --uri '$CAL?$SA&account_name=$1&account_type=$2' --where \"account_name='$1'\"" >/dev/null 2>&1; }
cals() { S content query --uri "$CAL" --projection _id:account_name:account_type:calendar_displayName:calendar_access_level; }
id_of() { cals | grep -F "calendar_displayName=$1," | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
# A where clause with a quoted literal goes to the device as ONE string too: passed as separate words, adb joins them
# and the device shell eats the quotes (run 1: every title lookup came back empty).
q() { adb shell "$1" | tr -d '\r'; }
event_id() { q "content query --uri $EVENTS --projection _id:title --where \"title='$1'\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }
cleanup() {
  rmcal bs.local LOCAL; rmcal bs.ro@example.com com.google; rmcal bs.acct@example.com com.google
  [ -n "${RAW_ID:-}" ] && adb shell "content delete --uri 'content://com.android.contacts/raw_contacts/$RAW_ID?$SA'" >/dev/null 2>&1
}
trap cleanup EXIT

row_begin BUILDSTART "Verify at build start: five Android facts, on the AVD"
record "image" "sdk $(S getprop ro.build.version.sdk), $(S getprop ro.build.fingerprint)"
record "calendar provider" "$(S dumpsys package com.android.providers.calendar | grep -m1 versionName | xargs)"
cleanup; RAW_ID=""
note "calendars before: $(cals | tr '\n' ' ')"

mkcal bs.local LOCAL "BS Local" 700 >/dev/null
mkcal bs.ro@example.com com.google "BS ReadOnly" 200 >/dev/null
mkcal bs.acct@example.com com.google "BS Account" 700 >/dev/null
LOCAL="$(id_of 'BS Local')"; RO="$(id_of 'BS ReadOnly')"; ACCT="$(id_of 'BS Account')"
assert_ne "fixture: BS Local exists" "" "$LOCAL"
assert_ne "fixture: BS ReadOnly exists" "" "$RO"
assert_ne "fixture: BS Account exists" "" "$ACCT"
note "fixtures: local=$LOCAL readonly=$RO account=$ACCT"

# ---------------------------------------------------------------- 1: the reminder broadcast
log "--- 1: the reminder broadcast's data URI"
NOW_S="$(S date +%s)"
START_MS=$(( (NOW_S + 11 * 60) * 1000 )); END_MS=$(( START_MS + 3600000 ))
TZ_ID="$(S getprop persist.sys.timezone)"
adb shell "content insert --uri $EVENTS --bind calendar_id:i:$LOCAL --bind title:s:bs-reminder --bind dtstart:l:$START_MS --bind dtend:l:$END_MS --bind eventTimezone:s:$TZ_ID"
EV="$(event_id bs-reminder)"
assert_ne "1: the test event exists" "" "$EV"
adb shell "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$EV --bind minutes:i:10 --bind method:i:1"
sleep 3
S dumpsys alarm > "$ROW_DIR/1-alarm-before.txt"
record "1: the provider's pending alarm" "$(grep -B1 -A3 -m1 'EVENT_REMINDER\|CalendarProvider' "$ROW_DIR/1-alarm-before.txt" | tr '\n' ' ' | cut -c1-300)"
record "1: calendar_alerts before the alarm" "$(S content query --uri content://com.android.calendar/calendar_alerts --projection _id:event_id:alarmTime:state:minutes --where "event_id=$EV" | tr '\n' ' ')"
log "waiting for the alarm (the event starts 11 minutes after $(date -d "@$NOW_S" +%T) device time; the reminder is 10 minutes before)"
ALARM_S=$(( NOW_S + 60 ))
while [ "$(S date +%s)" -lt $(( ALARM_S + 20 )) ]; do sleep 5; done
S dumpsys activity broadcasts history > "$ROW_DIR/1-broadcasts.txt"
grep -n -A6 'act=android.intent.action.EVENT_REMINDER' "$ROW_DIR/1-broadcasts.txt" | head -60 > "$ROW_DIR/1-event-reminder.txt"
LINE="$(grep -m1 -o 'act=android.intent.action.EVENT_REMINDER[^}]*' "$ROW_DIR/1-broadcasts.txt")"
record "1: the broadcast as the system logged it" "$LINE"
assert_contains "1: the broadcast was sent" "act=android.intent.action.EVENT_REMINDER" "$LINE"
assert_contains "1: its data is a content URI on com.android.calendar (the receiver's scheme + host filter)" "dat=content://com.android.calendar/" "$LINE"
record "1: flags (0x01000000 = FLAG_RECEIVER_INCLUDE_BACKGROUND, what lets a manifest receiver get it)" "$(echo "$LINE" | grep -o 'flg=0x[0-9a-f]*')"
record "1: who received it" "$(grep -A40 -m1 'act=android.intent.action.EVENT_REMINDER' "$ROW_DIR/1-broadcasts.txt" | grep -E 'Receiver|ResolveInfo|ActivityInfo|BroadcastFilter|deliver' | head -6 | tr -s ' ' | tr '\n' '|' | cut -c1-500)"
record "1: calendar_alerts after the alarm" "$(S content query --uri content://com.android.calendar/calendar_alerts --projection _id:event_id:alarmTime:state:minutes --where "event_id=$EV" | tr '\n' ' ')"

# ---------------------------------------------------------------- 2: access level and a normal insert
log "--- 2: a normal (not sync-adapter) insert into a calendar of access level 200"
OUT="$(adb shell "content insert --uri $EVENTS --bind calendar_id:i:$RO --bind title:s:bs-readonly --bind dtstart:l:$START_MS --bind dtend:l:$END_MS --bind eventTimezone:s:UTC" 2>&1 | tr -d '\r' | head -3)"
RO_ROW="$(q "content query --uri $EVENTS --projection _id:calendar_id:title:dirty --where \"title='bs-readonly'\"")"
record "2: the insert's own output" "${OUT:-(none)}"
record "2: the row afterwards" "$RO_ROW"
case "$RO_ROW" in
  *"calendar_id=$RO"*) record "2: ANSWER" "the provider ACCEPTS a normal insert into a level-200 calendar; the write layer's own re-read is the only gate (r3 D5), and the edge case expects no provider error" ;;
  *) record "2: ANSWER" "the provider REFUSES a normal insert into a level-200 calendar; the write layer's re-read is still built (r3 D5)" ;;
esac

# ---------------------------------------------------------------- 3: account-less calendars and a provider restart
log "--- 3: non-LOCAL calendars with no account, across a restart of the provider's process"
PID_BEFORE="$(S pidof com.android.providers.calendar)"
adb shell am force-stop com.android.providers.calendar
sleep 2
AFTER1="$(cals)"
PID_AFTER="$(S pidof com.android.providers.calendar)"
sleep 20
AFTER2="$(cals)"
record "3: provider pid before / after the force-stop" "$PID_BEFORE / $PID_AFTER"
note "calendars right after the restart: $(echo "$AFTER1" | tr '\n' ' ')"
note "calendars 20 s later: $(echo "$AFTER2" | tr '\n' ' ')"
assert_ne "3: the provider's process restarted" "$PID_BEFORE" "$PID_AFTER"
if echo "$AFTER2" | grep -qF "calendar_displayName=BS Account," && echo "$AFTER2" | grep -qF "calendar_displayName=BS ReadOnly,"; then
  record "3: ANSWER" "account-less com.google calendars SURVIVE a provider restart (ids $(echo "$AFTER2" | grep -F 'BS Account,' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p') / $(echo "$AFTER2" | grep -F 'BS ReadOnly,' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p'), unchanged: $([ "$(echo "$AFTER2" | grep -F 'BS Account,' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')" = "$ACCT" ] && echo yes || echo NO))"
else
  record "3: ANSWER" "account-less com.google calendars are DROPPED when the provider restarts: a Sync row creates its mkcal calendars after its last restart, never before (r3 V11)"
fi
assert_contains "3: the LOCAL fixture survives either way" "calendar_displayName=BS Local," "$AFTER2"

# ---------------------------------------------------------------- 4: a phone-only contact's account
log "--- 4: a phone-only raw contact's account on this image"
adb shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n:"
RAW_ID="$(q "content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type:deleted --sort '_id DESC'" | head -1 | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')"
RAW="$(S content query --uri content://com.android.contacts/raw_contacts --projection _id:account_name:account_type --where "_id=$RAW_ID")"
record "4: a raw contact inserted with no account reads" "$RAW"
record "4: the image's configured local account name" "$(S cmd overlay lookup android android:string/config_rawContactsLocalAccountName 2>&1 | head -1)"
record "4: the image's configured local account type" "$(S cmd overlay lookup android android:string/config_rawContactsLocalAccountType 2>&1 | head -1)"
assert_contains "4: phone-only is a NULL account name on this image" "account_name=NULL" "$RAW"
assert_contains "4: phone-only is a NULL account type on this image" "account_type=NULL" "$RAW"

# ---------------------------------------------------------------- 5: the 29 February rule
# Run 2 found the doc's form wrong on this provider: FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1 from a 29 February start gives an
# instance in leap years only (Android's recurrence expander honours a negative BYMONTHDAY for MONTHLY rules alone). The
# doc says what follows ("the 29 February form is re-cut before task 3 and Jeremy is told"), so the doc's form is now
# RECORDed and the re-cut form — every 12 months, the month's last day — is what is asserted.
log "--- 5: a 29 February birthday, yearly, on February's last day"
LEAP_MS="$(date -u -d '1992-02-29 00:00:00' +%s)000"
mkleap() { # title rrule -> event id
  adb shell "content insert --uri $EVENTS --bind calendar_id:i:$LOCAL --bind title:s:$1 --bind dtstart:l:$LEAP_MS --bind duration:s:P1D --bind allDay:i:1 --bind eventTimezone:s:UTC --bind rrule:s:'$2'"
  event_id "$1"
}
instances() { # event-id from-date to-date -> the UTC dates of its instances in [from, to)
  local a b
  a="$(date -u -d "$2 00:00:00" +%s)000"; b="$(date -u -d "$3 00:00:00" +%s)000"
  S content query --uri "content://com.android.calendar/instances/when/$a/$b" --projection event_id:begin --where "event_id=$1" \
    | sed -n 's/.*begin=\([0-9]*\).*/\1/p' | while read -r ms; do date -u -d "@$(( ms / 1000 ))" +%F; done | sort | tr '\n' ' ' | sed 's/ $//'
}
DOC="$(mkleap bs-leap-doc 'FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1')"
assert_ne "5: the doc's form inserts" "" "$DOC"
record "5: the doc's form, FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1, in 2027" "[$(instances "$DOC" 2027-01-01 2028-01-01)]"
record "5: the doc's form in 2028" "[$(instances "$DOC" 2028-01-01 2029-01-01)]"
record "5: ANSWER" "the doc's form gives NO instance in a year without a 29 February when it is empty above; the form is re-cut to FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1"
LEAP="$(mkleap bs-leap 'FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1')"
assert_ne "5: the re-cut form inserts" "" "$LEAP"
record "5: the re-cut event row" "$(S content query --uri "$EVENTS" --projection _id:dtstart:duration:allDay:rrule --where "_id=$LEAP")"
assert_eq "5: the whole of 2027 has one instance, on 28 February" "2027-02-28" "$(instances "$LEAP" 2027-01-01 2028-01-01)"
assert_eq "5: the whole of 2028 has one instance, on 29 February" "2028-02-29" "$(instances "$LEAP" 2028-01-01 2029-01-01)"
assert_eq "5: 2100 (not a leap year) has one instance, on 28 February" "2100-02-28" "$(instances "$LEAP" 2100-01-01 2101-01-01)"
assert_eq "5: 2026 to 2032, one a year, the 29th only in 2028" "2026-02-28 2027-02-28 2028-02-29 2029-02-28 2030-02-28 2031-02-28" "$(instances "$LEAP" 2026-01-01 2032-01-01)"
# A no-year 29 February in a year without one starts on that year's 28 February: the same rule from that start.
adb shell "content insert --uri $EVENTS --bind calendar_id:i:$LOCAL --bind title:s:bs-leap-noyear --bind dtstart:l:$(date -u -d '2026-02-28 00:00:00' +%s)000 --bind duration:s:P1D --bind allDay:i:1 --bind eventTimezone:s:UTC --bind rrule:s:'FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1'"
NOYEAR="$(event_id bs-leap-noyear)"
assert_eq "5: the same rule from a 28 February 2026 start" "2026-02-28 2027-02-28 2028-02-29 2029-02-28" "$(instances "$NOYEAR" 2026-01-01 2030-01-01)"

cleanup; RAW_ID=""; trap - EXIT
note "calendars after the restore: $(cals | tr '\n' ' ')"
assert_absent "restore: no BS calendar is left" "BS " "$(cals)"
row_end
