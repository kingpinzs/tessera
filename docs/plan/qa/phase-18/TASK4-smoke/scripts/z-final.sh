#!/usr/bin/env bash
# The device handed back: the layout that was there at the start, no fixtures, no virtual disk, the test app gone.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg z-final
FIRST="$(ls -d "$T4"/a-e6-run* | sort -V | head -1)/snap-sdcard-before.txt"; echo "first snapshot: $FIRST ($(grep -c . "$FIRST") paths)"
_snap_sdcard > "$ROW_DIR/sdcard-now.txt"
diff "$FIRST" "$ROW_DIR/sdcard-now.txt" > "$ROW_DIR/sdcard.diff"; cat "$ROW_DIR/sdcard.diff"
# MediaProvider's thumbnails of the public-volume fixture (made while Start's Photos tile showed the newest picture).
for f in $(grep '^> /sdcard/Pictures/.thumbnails/' "$ROW_DIR/sdcard.diff" | cut -c3-); do adb shell rm -f "$f"; echo "removed $f"; done
_snap_sdcard > "$ROW_DIR/sdcard-after.txt"
assert_eq "shared storage equals the snapshot taken before the first fixture" "" "$(diff "$FIRST" "$ROW_DIR/sdcard-after.txt")"
adb uninstall "$QAC" | tail -1
assert_eq "qa-capture is uninstalled" "" "$(q "pm path $QAC")"
rings_save
if layout_restore "$T4/00-begin/layout-at-begin.json"; then _verdict PASS "the layout that was there at the start is restored" "layout_restore"; else _verdict FAIL "the layout that was there at the start is restored" "layout_restore failed"; fi
ensure_start
files_open --es page recent; D 01-recent; adb shell input keyevent KEYCODE_HOME; sleep 1; c6; ensure_start
assert_eq "no virtual disk, no public volume" "" "$(q 'sm list-disks' | xargs)$(q 'sm list-volumes public' | xargs)"
assert_contains "appop allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(q 'appops get app.tileshell MANAGE_EXTERNAL_STORAGE')"
assert_eq "no pace prefs" "0" "$(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml 2>/dev/null | grep -c qa_files)"
assert_eq "no fixtures" "" "$(q 'ls -d /sdcard/QA-Files /sdcard/QA-Big /sdcard/.Tessera /sdcard/qa.xml 2>/dev/null' | xargs)"
assert_eq "the installed APK is the build" "yes" "$(apk_matches | cut -d' ' -f1)"
assert_eq "top: Start" "app.tileshell/.StartActivity" "$(top_activity)"
echo "apk md5: $(md5sum "$APK")"; echo "recent store: $(recent_json)"; S 02-start
leg_end
