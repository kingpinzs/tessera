#!/usr/bin/env bash
# Development smoke of the Calendar data layer (build task 3) before the app's pages exist: this build starts, the
# feeds and the Birthdays writer run from ShellApp.startFeeds, the exported surface equals the allow-list, and the
# CalendarFeed still publishes its face (now under two keys). NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin A_SMOKE "data layer: the build starts and the feeds run"
CRASH0="$(S dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c '^Process: app.tileshell$')"
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell; sleep 1
ensure_start; sleep 3
SLICE="$(ring_since "$MARK")"
assert_contains "the launcher's process started" "[app] process start" "$SLICE"
assert_contains "feeds started" "[app] feeds started (process start)" "$SLICE"
assert_contains "the Birthdays writer ran from startFeeds" "[calendar] birthdays:" "$SLICE"
log "birthdays line: $(line_of "$SLICE" '[calendar] birthdays')"
assert_contains "CalendarFeed refreshed" "[calendar] refresh (start): access=true" "$SLICE"
assert_contains "the face is published under the slot key" "[engine] publish feed:calendar" "$SLICE"
assert_contains "the face is published under the Calendar activity's component key (r3 D12)" "[engine] publish cmp:app.tileshell/app.tileshell.calendar.CalendarActivity" "$SLICE"
python3 "$REPO/docs/plan/qa/phase-03/scripts/exported.py" "$APK" "$REPO/docs/plan/qa/phase-03/exported-allowlist.txt" > "$ROW_DIR/exported.txt" 2>&1
assert_eq "exported.py: the APK's exported components equal the allow-list" "0" "$?"
log "$(head -1 "$ROW_DIR/exported.txt")"
assert_contains "the reminder receiver is registered for EVENT_REMINDER" "CalendarReminderReceiver" "$(S cmd package query-receivers --brief -a android.intent.action.EVENT_REMINDER -d content://com.android.calendar/1 | grep app.tileshell)"
open_cal -a android.intent.action.MAIN
assert_eq "Calendar resumes" "app.tileshell/.calendar.CalendarActivity" "$(top_activity)"
assert_eq "no new crash of the shell" "$CRASH0" "$(S dumpsys dropbox --print data_app_crash 2>/dev/null | grep -c '^Process: app.tileshell$')"
c6; ensure_start
session_end
