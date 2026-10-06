#!/usr/bin/env bash
# Q-18-4 (a): Files' ungranted link and the Setup checklist's Files row each START the app's OWN all-files page,
# com.android.settings/.Settings$AppManageExternalStorageActivity with dat=package:app.tileshell.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg f-grant
WANT='com.android.settings/.Settings$AppManageExternalStorageActivity'
ensure_start; rings_save
record "pid before the revoke" "$(q 'pidof app.tileshell' | xargs)"
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE default; sleep 2
record "pid after the revoke (the revoke kills the app)" "$(q 'pidof app.tileshell' | xargs)"
record "appop" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE' | xargs)"
started() { adb logcat -d -s ActivityTaskManager:I | tr -d '\r' | grep 'START u0' | grep 'com.android.settings'; }
# ---- the link in Files' ungranted state
ensure_start; M=$(ring_mark); ROW_MARK=$M
files_open; D 01-ungranted; S 01-ungranted
assert_eq "ungranted: files_ungranted text" "Files can't see this phone's storage" "$(X 01-ungranted files_ungranted | sed "s/&apos;/'/g")"
assert_contains "ungranted: [files] access=denied (slice from the MARK after the revoke)" "[files] access=denied" "$(ring_since $M)"
adb logcat -c; T 01-ungranted files_grant_link 3; S 02-link-settings
started > "$ROW_DIR/02-start.txt"; cat "$ROW_DIR/02-start.txt"; L="$(head -1 "$ROW_DIR/02-start.txt")"; record "link: what Settings then forwards to" "$(sed -n 2p "$ROW_DIR/02-start.txt" | grep -o "cmp=[^ }]*")"
assert_contains "link: the START line names the per-app page" "cmp=$WANT" "$L"
record "link: the START line's data (logcat prints a redacted URI: scheme only)" "$(echo "$L" | grep -o 'dat=[^ ]*')"
dump_ui "$ROW_DIR/02-settings.xml"; record "link: the Settings page's texts" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/02-settings.xml" | xargs | cut -c1-200)"
assert_contains "link: the page is the shell's OWN all-files page (its label, then the switch)" "text=Tessera text=0.1.0 text=Allow access to manage all files" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/02-settings.xml" | xargs)"
assert_contains "link: …and the per-app action" "act=android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" "$L"
assert_eq "link: the resumed package is Settings" "com.android.settings" "$(top_activity | cut -d/ -f1)"
record "link: top resumed" "$(top_activity)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; adb shell input keyevent KEYCODE_HOME; sleep 1
# ---- the Setup checklist's Files row
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-clear-task --es page CHECKLIST >/dev/null 2>&1; sleep 3
scroll_to_node "$ROW_DIR/03-checklist.xml" checklist:files:missing 10 >/dev/null; S 03-checklist
assert_eq "checklist: the Files row is red (checklist:files:missing)" "yes" "$(H 03-checklist checklist:files:missing)"
adb logcat -c; T 03-checklist checklist:files:missing 3; S 04-row-settings
started > "$ROW_DIR/04-start.txt"; cat "$ROW_DIR/04-start.txt"; L="$(head -1 "$ROW_DIR/04-start.txt")"
assert_contains "checklist row: the START line names the per-app page" "cmp=$WANT" "$L"
record "checklist row: the START line's data (logcat prints a redacted URI: scheme only)" "$(echo "$L" | grep -o 'dat=[^ ]*')"
dump_ui "$ROW_DIR/04-settings.xml"; record "checklist row: the Settings page's texts" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/04-settings.xml" | xargs | cut -c1-200)"
assert_contains "checklist row: the page is the shell's OWN all-files page (its label, then the switch)" "text=Tessera text=0.1.0 text=Allow access to manage all files" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/04-settings.xml" | xargs)"
assert_eq "checklist row: the resumed package is Settings" "com.android.settings" "$(top_activity | cut -d/ -f1)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
# ---- restore
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow; sleep 1
assert_contains "restore: appop allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
c6; ensure_start; M=$(ring_mark); files_open; D 05-granted
assert_contains "restore: [files] access=granted" "[files] access=granted" "$(ring_since $M)"
assert_eq "restore: no ungranted page" "no" "$(H 05-granted files_ungranted)"
adb shell input keyevent KEYCODE_HOME; sleep 1; ensure_start
leg_end
