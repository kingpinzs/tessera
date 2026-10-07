#!/usr/bin/env bash
# The device as it was found, saved before anything is changed; then this build installed.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg 00-begin
adb shell getprop sys.boot_completed
echo "installed before: $(installed_apk_id)"
layout_save "$ROW_DIR/layout-at-begin.json"; python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print('layout at begin: %d tiles, dock %s, slots %s' % (len(d['order']), d['dock'], d.get('slots')))" "$ROW_DIR/layout-at-begin.json"
echo "appop: $(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
echo "disks: [$(q 'sm list-disks' | xargs)] public: [$(q 'sm list-volumes public' | xargs)]"
echo "fixtures: [$(q 'ls -d /sdcard/QA-Files /sdcard/QA-Big /sdcard/.Tessera 2>/dev/null' | xargs)]"
echo "qa-capture: [$(q 'pm list packages app.tileshell.testclient' | xargs)]"
echo "prefs: [$(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml 2>/dev/null | tr -d '\r' | grep -c qa_files)] qa_files keys"
echo "recent: $(recent_json)"
q "run-as app.tileshell ls -la files shared_prefs" > "$ROW_DIR/private-ls.txt"
q "ls -la /sdcard/ /sdcard/Recordings /sdcard/Music /sdcard/DCIM /sdcard/Pictures /sdcard/Movies /sdcard/Download 2>&1" > "$ROW_DIR/sdcard-ls.txt"
adb install -r -g "$APK" 2>&1 | tail -1
adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow
echo "installed now: $(installed_apk_id) match $(apk_matches)"
adb shell input keyevent KEYCODE_HOME; sleep 3; echo "top: $(top_activity)"
