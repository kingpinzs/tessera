#!/usr/bin/env bash
# Development proof of Q-16-2's "one event, one reminder" (E24's reminder clause): a synced local event with a
# 10-minute reminder has a copy with its own reminder row, so two alerts fire; the shell posts exactly ONE notification
# — the original's — and logs the copy's alert as `skipped (synced copy)`. The AOSP Calendar is disabled for the run
# so every notification counted is the shell's. The clock moves FORWARDS only and is restored. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E24_REMINDER "a synced event reminds once: the copy's alert is skipped"
shell_notes() { S dumpsys notification --noredact | python3 -c '
import re, sys
for block in re.split(r"(?=\n\s*NotificationRecord\()", sys.stdin.read()):
    if "pkg=app.tileshell" not in block or "calendar_reminders" not in block: continue
    title = re.search(r"android\.title=\S+ \((.*?)\)\n", block)
    print("title=[%s]" % (title.group(1) if title else ""))'; }
AOSP_WAS="$(S pm list packages -e com.android.calendar | grep -c '^package:com.android.calendar$')"
adb shell pm disable-user --user 0 com.android.calendar > /dev/null 2>&1
c6; cal_fixtures_down; sleep 1
sync_fixtures_up
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"
NOW="$(device_ms)"; START=$(( (NOW / 60000 + 30) * 60000 ))
L="$(mkevent "$TESS" 'E24 remind' "$START" "$(( START + 3600000 ))")"
adb shell "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$L --bind minutes:i:10 --bind method:i:1" < /dev/null
open_can_sync; set_can_sync "$PERSONAL" true
open_event "$L"; tap cal_event_action:sync 2; tap "cal_sync_target:$PERSONAL" 2.5
C="$(event_ids "calendar_id=$PERSONAL AND deleted=0" | tr -d ' ')"
assert_ne "the copy exists" "" "$C"
assert_contains "the copy has its own reminder row" "minutes=10" "$(S "content query --uri content://com.android.calendar/reminders --projection event_id:minutes --where \"event_id=$C\"")"
adb shell input keyevent KEYCODE_HOME; sleep 1

jump_clock $(( START - 600000 - 5000 )) > /dev/null
MARK="$(ring_mark)"
for i in $(seq 1 15); do [ -n "$(shell_notes)" ] && break; sleep 1; done
sleep 3
NOTES="$(shell_notes)"; log "the shell's calendar notifications: $(echo "$NOTES" | tr '\n' ' ')"
assert_eq "exactly ONE notification of the shell's, titled the event" "title=[E24 remind]" "$NOTES"
SLICE="$(ring_since "$MARK")"
assert_eq "the original's alert notified, once" 1 "$(printf '%s\n' "$SLICE" | grep -c "\[calendar\] reminder event=$L minutes=10: notified")"
assert_eq "the copy's alert skipped, once" 1 "$(printf '%s\n' "$SLICE" | grep -c "\[calendar\] reminder event=$C minutes=10: skipped (synced copy)")"
ALERTS="$(S "content query --uri content://com.android.calendar/calendar_alerts --projection event_id:state --where \"event_id IN ($L,$C)\"" | sed 's/^Row: [0-9]* //' | sort | tr '\n' ';')"
record "the two alert rows (the copy's did fire and is left as it was)" "$ALERTS"
assert_contains "the original's alert row is FIRED" "event_id=$L, state=1" "$ALERTS"
assert_contains "the copy has an alert row of its own" "event_id=$C, state=" "$ALERTS"

# ---- restore
ring_save
[ "$AOSP_WAS" = 1 ] && adb shell pm enable com.android.calendar > /dev/null 2>&1
assert_eq "the AOSP Calendar is enabled again, as it was" "$AOSP_WAS" "$(S pm list packages -e com.android.calendar | grep -c '^package:com.android.calendar$')"
cal_fixtures_down
purge_tessera_events "title LIKE 'E24 %'"
clock_restore
assert_eq "no calendar notification of the shell's is left" 0 "$(shell_notes | grep -c 'title=')"
assert_eq "the fixtures are gone" "/0" "$(cal_id "$PERSONAL_ACCT")$(cal_id "$WORK_ACCT")/$(event_count "title LIKE 'E24 %'")"
ensure_start
session_end
