#!/usr/bin/env bash
# Development proof of E6 (a) and (c): with the AOSP Calendar disabled, a reminder on a Tessera event notifies ONCE from
# the shell at its time — on the calendar channel, visibility PRIVATE, with the event's time — the alert row goes FIRED,
# the shell arms no alarm of its own, a second poke for the same alert does nothing, the swipe marks the row DISMISSED,
# and a forged poke with nothing due does nothing. Every clock move is FORWARDS (jump_clock), restored with
# clock_restore. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E6_REMINDER "a reminder notifies once through the provider; a forged poke does nothing"
ALERTS=content://com.android.calendar/calendar_alerts
shell_alarms_pending() {
  S dumpsys alarm | python3 -c '
import re, sys
n = 0; inside = False
for l in sys.stdin.read().splitlines():
    if re.match(r"^\s*\d+ pending alarms:", l): inside = True; continue
    if inside and l and not l.startswith(" "): inside = False
    if inside and re.match(r"^\s+(RTC|ELAPSED)", l) and "app.tileshell" in l: n += 1
print(n)'
}
shell_notes() { S dumpsys notification --noredact | python3 -c '
import re, sys
text = sys.stdin.read()
for block in re.split(r"(?=\n\s*NotificationRecord\()", text):
    if "pkg=app.tileshell" not in block or "calendar_reminders" not in block: continue
    title = re.search(r"android\.title=\S+ \((.*?)\)\n", block)
    body = re.search(r"android\.text=\S+ \((.*?)\)\n", block)
    vis = re.search(r"\bvis=(\w+)", block)
    print("title=[%s] text=[%s] vis=%s" % (title.group(1) if title else "", body.group(1) if body else "", vis.group(1) if vis else "?"))'; }
alert_rows() { S "content query --uri $ALERTS --projection _id:event_id:state:minutes --where \"event_id=$1\"" | sed 's/^Row: [0-9]* //' | tr '\n' ';'; }

AOSP_WAS="$(S pm list packages -e com.android.calendar | grep -c '^package:com.android.calendar$')"
adb shell pm disable-user --user 0 com.android.calendar > /dev/null 2>&1
assert_eq "the AOSP Calendar is disabled (only the shell can mark the alert)" 0 "$(S pm list packages -e com.android.calendar | grep -c '^package:com.android.calendar$')"
c6
TESS="$(tessera_id)"; assert_ne "Tessera exists" "" "$TESS"
PENDING0="$(shell_alarms_pending)"
NOW="$(device_ms)"; START=$(( (NOW / 60000 + 30) * 60000 ))
EV="$(mkevent "$TESS" 'E6 standup' "$START" "$(( START + 3600000 ))")"
adb shell "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$EV --bind minutes:i:10 --bind method:i:1" < /dev/null
sleep 2
note "event $EV starts $START; reminder 10 minutes; alerts now: $(alert_rows "$EV")"
assert_contains "the provider armed its own alarm" "com.android.providers.calendar" "$(S dumpsys alarm | grep -m1 'com.android.providers.calendar')"
assert_eq "the shell's pending alarms are unchanged by the reminder (Rule 16: no alarm of its own)" "$PENDING0" "$(shell_alarms_pending)"
assert_eq "no calendar notification before the time" "" "$(shell_notes)"

jump_clock $(( START - 600000 - 5000 )) > /dev/null
MARK="$(ring_mark)"
for i in $(seq 1 15); do [ -n "$(shell_notes)" ] && break; sleep 1; done
NOTES="$(shell_notes)"; log "the shell's calendar notifications: $NOTES (after $i s)"
assert_eq "exactly ONE notification of the shell's on the calendar channel" 1 "$(printf '%s\n' "$NOTES" | grep -c 'title=')"
assert_contains "its title is the event's" "title=[E6 standup]" "$NOTES"
assert_contains "visibility PRIVATE" "vis=PRIVATE" "$NOTES"
record "the notification's text (the event's time)" "$(printf '%s' "$NOTES" | sed -n 's/.*text=\[\(.*\)\] vis.*/\1/p')"
SLICE="$(ring_since "$MARK")"
assert_eq "the notified line, once" 1 "$(printf '%s\n' "$SLICE" | grep -c "\[calendar\] reminder event=$EV minutes=10: notified")"
assert_contains "the alert row is FIRED (state 1)" "event_id=$EV, state=1" "$(alert_rows "$EV")"
assert_eq "still no alarm of the shell's own" "$PENDING0" "$(shell_alarms_pending)"

# ---- a second poke for the same alert (the provider, or anyone, sending the broadcast again): nothing more
MARK2="$(ring_mark)"
adb shell am broadcast -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/$(( START - 600000 )) -n app.tileshell/.calendar.CalendarReminderReceiver > /dev/null 2>&1
sleep 3
absent_in "a second poke for a handled alert logs no notified line" ": notified" "$(ring_since "$MARK2")"
assert_eq "and posts no second notification" 1 "$(shell_notes | grep -c 'title=')"

# ---- the swipe: DISMISSED
MARK3="$(ring_mark)"
adb shell cmd statusbar expand-notifications; sleep 2
adb shell uiautomator dump /sdcard/qa-shade.xml > /dev/null 2>&1; adb shell cat /sdcard/qa-shade.xml > "$ROW_DIR/shade.xml"
B="$(python3 - "$ROW_DIR/shade.xml" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
m = re.search(r'text="E6 standup"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', xml)
print(" ".join(m.groups()) if m else "")
PY
)"
note "the notification's title node in the shade: [$B]"
# shellcheck disable=SC2086
if [ -n "$B" ]; then set -- $B; adb shell input swipe $(( $1 + 20 )) $(( ($2 + $4) / 2 )) 1050 $(( ($2 + $4) / 2 )) 200; fi
sleep 2
adb shell cmd statusbar collapse; sleep 1
assert_eq "the notification is gone after the swipe" 0 "$(shell_notes | grep -c 'title=')"
assert_contains "the alert row is DISMISSED (state 2)" "event_id=$EV, state=2" "$(alert_rows "$EV")"
assert_contains "the dismissed line" "[calendar] reminder event=$EV minutes=10: dismissed" "$(ring_since "$MARK3")"

# ---- the next reminder, whose alert row is handed the deleted row's _id: it still notifies
ALERT1="$(alert_rows "$EV" | sed -n 's/^_id=\([0-9]*\),.*/\1/p')"
purge_tessera_events "_id=$EV"; sleep 1
NOW2="$(device_ms)"; START2=$(( (NOW2 / 60000 + 30) * 60000 ))
EV2="$(mkevent "$TESS" 'E6 second' "$START2" "$(( START2 + 3600000 ))")"
adb shell "content insert --uri content://com.android.calendar/reminders --bind event_id:i:$EV2 --bind minutes:i:10 --bind method:i:1" < /dev/null
sleep 2
jump_clock $(( START2 - 600000 - 5000 )) > /dev/null
MARK5="$(ring_mark)"
for i in $(seq 1 15); do [ -n "$(shell_notes)" ] && break; sleep 1; done
ALERT2="$(alert_rows "$EV2" | sed -n 's/^_id=\([0-9]*\),.*/\1/p')"
record "the first alert's row id, the second's (the provider reuses a deleted row's id), and the events' ids" "$ALERT1 / $ALERT2 / $EV $EV2"
assert_contains "the second event's reminder notifies" "title=[E6 second]" "$(shell_notes)"
assert_eq "its notified line, once" 1 "$(ring_since "$MARK5" | grep -c "\[calendar\] reminder event=$EV2 minutes=10: notified")"
purge_tessera_events "_id=$EV2"

# ---- (c) a forged poke with no alert due
MARK4="$(ring_mark)"
adb shell am broadcast -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/1 -n app.tileshell/.calendar.CalendarReminderReceiver > "$ROW_DIR/forged.txt" 2>&1
sleep 3
SLICE4="$(ring_since "$MARK4")"
[ -n "$SLICE4" ] || { adb shell am start -W -n "$CAL_ACT" > /dev/null 2>&1; sleep 2; SLICE4="$(ring_since "$MARK4")"; adb shell input keyevent KEYCODE_HOME; }
absent_in "a forged poke logs no notified line" ": notified" "$SLICE4"
assert_eq "and posts no new notification (the second event's is the only one up)" 1 "$(shell_notes | grep -c 'title=')"
assert_contains "the broadcast was delivered to the receiver" "Broadcast completed" "$(cat "$ROW_DIR/forged.txt")"

# ---- restore
purge_tessera_events "title LIKE 'E6 %'"
assert_eq "the events are gone" 0 "$(event_count "title LIKE 'E6 %'")"
[ "$AOSP_WAS" = 1 ] && adb shell pm enable com.android.calendar > /dev/null 2>&1
assert_eq "the AOSP Calendar is enabled again, as it was" "$AOSP_WAS" "$(S pm list packages -e com.android.calendar | grep -c '^package:com.android.calendar$')"
ring_save
clock_restore
assert_eq "no calendar notification of the shell's is left" 0 "$(shell_notes | grep -c 'title=')"
ensure_start
session_end
