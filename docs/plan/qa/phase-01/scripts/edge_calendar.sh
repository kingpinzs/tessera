#!/usr/bin/env bash
# Edge cases: the Calendar tile (no calendars, all-day and overlapping events, permission revoked).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_calendar.txt
cal() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[calendar\]' | tail -1 | sed 's/.*\[calendar\] //'; }
echo "# Edge cases: calendar $(date -Iseconds)" > "$LOG"
echo "## no calendars at all (E7 deleted its QA calendar)" >> "$LOG"
echo "calendars on the device: $(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name 2>&1 | head -3 | tr '\n' ' ')" >> "$LOG"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 7
echo "tile: $(cal)" >> "$LOG"
adb exec-out screencap -p > "$OUT/calendar_none.png"
echo '## a local calendar with an all-day event and two overlapping events' >> "$LOG"
adb shell content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=qa&account_type=LOCAL" --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:qa --bind calendar_displayName:s:QA --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind sync_events:i:1 --bind visible:i:1 --bind calendar_timezone:s:UTC
CAL=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 | tail -1 | grep -o '[0-9]*$')
echo "QA calendar id: $CAL" >> "$LOG"
NOW=$(($(date +%s) * 1000))
DAY0=$(( (NOW / 86400000) * 86400000 ))
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:$CAL --bind title:s:"QA all day" --bind dtstart:l:$DAY0 --bind dtend:l:$((DAY0 + 86400000)) --bind allDay:i:1 --bind eventTimezone:s:UTC
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:$CAL --bind title:s:"QA overlap one" --bind dtstart:l:$((NOW + 3600000)) --bind dtend:l:$((NOW + 7200000)) --bind eventTimezone:s:UTC
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:$CAL --bind title:s:"QA overlap two" --bind dtstart:l:$((NOW + 5400000)) --bind dtend:l:$((NOW + 9000000)) --bind eventTimezone:s:UTC
sleep 4; adb shell input keyevent KEYCODE_HOME; sleep 6
echo "tile with three events: $(cal)" >> "$LOG"
for i in 1 2 3 4 5 6 7 8; do adb exec-out screencap -p > "$OUT/calendar_events_$i.png"; sleep 2.5; done
echo '## permission revoked' >> "$LOG"
adb shell pm revoke app.tileshell android.permission.READ_CALENDAR
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 7
echo "tile: $(cal)" >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_calendar_revoked.xml"
echo "checklist calendar row: $(grep -o 'checklist:calendar:[a-z_]*' "$OUT/checklist_calendar_revoked.xml" | head -1)" >> "$LOG"
echo '## restore' >> "$LOG"
adb shell pm grant app.tileshell android.permission.READ_CALENDAR
adb shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true&account_name=qa&account_type=LOCAL" --where "_id=$CAL"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 7
echo "after restore: $(cal); calendars left: $(adb shell content query --uri content://com.android.calendar/calendars --projection _id 2>&1 | head -2 | tr '\n' ' ')" >> "$LOG"
