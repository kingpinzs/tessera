#!/usr/bin/env bash
# Installs the lead's current build over the one on the emulator (the upgrade path, no grants passed), under the
# device lock, and reads the result back. The fix build of 2026-10-01 goes on this way.
. "$(dirname "$0")/../scripts/lib.sh"
. "$(dirname "$0")/../scripts/p16.sh"
take_device_lock
echo "before: $(installed_apk_id)"
adb install -r "$APK" 2>&1 | tail -1
sleep 6
adb shell ime enable app.tileshell/.ime.KeyboardService >/dev/null 2>&1; adb shell ime set app.tileshell/.ime.KeyboardService >/dev/null 2>&1
echo "after: $(installed_apk_id); match: $(apk_matches)"
for p in READ_CONTACTS WRITE_CONTACTS READ_CALENDAR WRITE_CALENDAR; do echo "$p=$(perm_granted $p)"; done
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "crashes in the dropbox: $(adb shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | grep -c '^Process: app.tileshell')"
echo "sync store: $(adb shell 'run-as app.tileshell cat files/calendar_sync.json' </dev/null 2>/dev/null | tr -d '\r' | cut -c1-200)"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener 2>/dev/null | tr -d '\r' | grep -E 'reminders count from|feeds started|assignSlotOnce' | tail -6
