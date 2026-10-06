#!/usr/bin/env bash
# Phase 12 E1 — absence, both provisioning routes (C-15).
#   (a) pm clear -> provision.sh (marker written) -> a resume: no wizard, "not shown: core held"; then a force-stop (which
#       deselects the keyboard) + Home: still no wizard, "not shown: finished", Setup row keyboard_selected missing.
#   (b) pm clear -> every grant from adb alone -> Home: no wizard, "not shown: core held", and no setup_wizard file.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E1 "absence on a provisioned AVD, both routes"

log "(a) pm clear -> provision.sh (marker written)"
restore_fresh a-start
assert_eq "(a) provision.sh rc" "0" "$(cat "$ROW_DIR/provision-a-start.rc")"
assert_contains "(a) provision wrote the marker" '<boolean name="finished" value="true" />' "$(grep '^marker:' "$ROW_DIR/provision-a-start.txt")"

MARK="$(ring_mark)"
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/a-resume.xml"
assert_eq "(a) start_page after the resume" "yes" "$(has_node "$ROW_DIR/a-resume.xml" start_page)"
assert_eq "(a) no wizard_page after the resume" "no" "$(has_node "$ROW_DIR/a-resume.xml" wizard_page)"
start_slice "$MARK" "(a) resume"
assert_contains "(a) [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"
assert_absent "(a) no [wizard] shown line" "[wizard] shown" "$SLICE"

log "(a) force-stop + Home: the keyboard is deselected, the marker keeps the wizard away"
MARK="$(ring_mark)"
ring_save launcher
leave_home
adb shell am force-stop "$PKG"
adb shell input keyevent KEYCODE_HOME; sleep 4
dump_ui "$ROW_DIR/a-forcestop.xml"
assert_eq "(a) no wizard_page after force-stop" "no" "$(has_node "$ROW_DIR/a-forcestop.xml" wizard_page)"
assert_eq "(a) start_page after force-stop" "yes" "$(has_node "$ROW_DIR/a-forcestop.xml" start_page)"
start_slice "$MARK" "(a) force-stop"
assert_contains "(a) [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"
assert_ne "(a) default_input_method is not the shell keyboard" "$KEYBOARD" "$(adb shell settings get secure default_input_method | tr -d '\r')"
adb shell am start -n "$PKG/.settings.SettingsActivity" --es page CHECKLIST >/dev/null 2>&1; sleep 3
scroll_to_node "$ROW_DIR/a-checklist.xml" "checklist:keyboard_selected:missing" 6
assert_eq "(a) Setup page: checklist:keyboard_selected:missing" "yes" "$(has_node "$ROW_DIR/a-checklist.xml" checklist:keyboard_selected:missing)"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell ime set "$KEYBOARD" >/dev/null
assert_eq "(a) ime set restores the keyboard" "$KEYBOARD" "$(adb shell settings get secure default_input_method | tr -d '\r')"
adb shell input keyevent KEYCODE_HOME; sleep 1

log "(b) pm clear -> every grant from adb alone -> Home"
ring_save launcher
leave_home
adb shell pm clear "$PKG" >/dev/null
adb shell cmd role add-role-holder android.app.role.HOME "$PKG"
adb shell cmd role add-role-holder android.app.role.ASSISTANT "$PKG"
adb shell cmd package set-home-activity "$PKG/$PKG.StartActivity" >/dev/null
adb shell cmd notification allow_listener "$LISTENER"
adb shell appops set "$PKG" GET_USAGE_STATS allow
adb shell appops set "$PKG" USE_FULL_SCREEN_INTENT allow
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
for p in READ_MEDIA_IMAGES READ_MEDIA_AUDIO READ_CALENDAR ACCESS_COARSE_LOCATION \
         RECORD_AUDIO READ_CONTACTS WRITE_CALENDAR SEND_SMS READ_SMS CALL_PHONE READ_CALL_LOG ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION \
         CAMERA READ_MEDIA_VIDEO; do   # phase 17 added the setup:camera and setup:videos steps: "every grant" now includes these two (INDEX Change Log 2026-10-06)
  adb shell pm grant "$PKG" "android.permission.$p"
done
adb shell ime enable "$KEYBOARD" >/dev/null
adb shell ime set "$KEYBOARD" >/dev/null
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/b-home.xml"
assert_eq "(b) start_page" "yes" "$(has_node "$ROW_DIR/b-home.xml" start_page)"
assert_eq "(b) no wizard_page" "no" "$(has_node "$ROW_DIR/b-home.xml" wizard_page)"
start_slice "$MARK" "(b) home"
assert_contains "(b) [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"
prefs="$(adb shell run-as "$PKG" ls shared_prefs 2>&1 | tr -d '\r')"
note "(b) shared_prefs: $prefs"
assert_absent "(b) no setup_wizard file (a device provisioned by adb never ran the wizard)" "setup_wizard" "$prefs"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
