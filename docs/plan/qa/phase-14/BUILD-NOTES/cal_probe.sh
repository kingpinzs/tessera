#!/usr/bin/env bash
export PATH="$HOME/Android/Sdk/platform-tools:$PATH" ANDROID_SERIAL=emulator-5554
adb shell content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:qa --bind calendar_displayName:s:QA --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind sync_events:i:1 --bind visible:i:1 --bind calendar_timezone:s:UTC
CAL=$(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=qa" | grep -o "_id=[0-9]*" | cut -d= -f2)
TZNAME=$(adb shell getprop persist.sys.timezone | tr -d '\r')
NOW=$(adb shell date +%s%3N | tr -d '\r')
LOCALDATE=$(adb shell date +%Y-%m-%d | tr -d '\r')
DAY0=$(( $(date -u -d "$LOCALDATE" +%s) * 1000 ))
SOD=$(( $(TZ=$TZNAME date -d "$LOCALDATE 00:00" +%s) * 1000 ))
echo "cal=$CAL tz=$TZNAME now=$NOW localdate=$LOCALDATE day0=$DAY0 sod=$SOD"
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:$CAL --bind "title:s:QA\\ probe\\ today" --bind dtstart:l:$DAY0 --bind dtend:l:$((DAY0 + 86400000)) --bind allDay:i:1 --bind eventTimezone:s:UTC
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:$CAL --bind "title:s:QA\\ probe\\ tomorrow" --bind dtstart:l:$((DAY0 + 86400000)) --bind dtend:l:$((DAY0 + 2*86400000)) --bind allDay:i:1 --bind eventTimezone:s:UTC
sleep 2
echo "== events"; adb shell content query --uri content://com.android.calendar/events --projection _id:title:dtstart:allDay --where "calendar_id=$CAL"
echo "== instances [now, now+48h]"; adb shell content query --uri content://com.android.calendar/instances/when/$NOW/$((NOW+172800000)) --projection title:begin:end:allDay:startDay:endDay
echo "== instances [local start of today, now+48h]"; adb shell content query --uri content://com.android.calendar/instances/when/$SOD/$((NOW+172800000)) --projection title:begin:end:allDay:startDay:endDay
adb shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" --where "_id=$CAL"
echo "calendars left:"; adb shell content query --uri content://com.android.calendar/calendars --projection _id:name
