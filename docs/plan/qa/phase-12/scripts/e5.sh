#!/usr/bin/env bash
# Phase 12 E5 — Skip, and the marker's rules (every diagnostics read through the Start ring read).
# Skip: Start, [wizard] skip, both lists still red, the marker set; a force-stop, an update install and a core grant
# revoked later never bring the wizard back; pm clear does. Second half: Not now on every step + Done finishes too.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E5 "Skip setup and the finished marker's rules"

e2_state
assert_e2_appops
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/first.xml"
assert_eq "the wizard shows on the first step" "setup:notifications" "$(wiz_step "$ROW_DIR/first.xml")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/first.xml" wizard_skip; sleep 3
dump_ui "$ROW_DIR/after-skip.xml"
assert_eq "start_page after Skip" "yes" "$(has_node "$ROW_DIR/after-skip.xml" start_page)"
assert_eq "no wizard_page after Skip" "no" "$(has_node "$ROW_DIR/after-skip.xml" wizard_page)"
start_slice "$MARK" "skip"
assert_contains "[wizard] skip" "[wizard] skip" "$SLICE"
assert_contains "the marker: shared_prefs/setup_wizard.xml holds finished true" '<boolean name="finished" value="true" />' "$(adb shell run-as $PKG cat shared_prefs/setup_wizard.xml | tr -d '\r')"
adb shell am start -n "$PKG/.settings.SettingsActivity" --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/checklist.xml"
assert_eq "Setup page: checklist:notifications:missing (Skip granted nothing)" "yes" "$(has_node "$ROW_DIR/checklist.xml" checklist:notifications:missing)"
adb shell input keyevent KEYCODE_HOME; sleep 2
# Tess's page lives inside her session, which opens only for the ASSISTANT role holder (without it the Search key shows
# her role notice). The role is added for this one read — the microphone stays revoked, which is what the row checks.
adb shell cmd role add-role-holder android.app.role.ASSISTANT "$PKG"; sleep 2
open_tess_settings "$ROW_DIR/tess"
scroll_to_node "$ROW_DIR/tess-settings.xml" "cortana_check:microphone:missing" 8
assert_eq "Tess's page: cortana_check:microphone:missing (Skip covers both lists)" "yes" "$(has_node "$ROW_DIR/tess-settings.xml" cortana_check:microphone:missing)"
adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell cmd role remove-role-holder android.app.role.ASSISTANT "$PKG"
adb shell input keyevent KEYCODE_HOME; sleep 2

log "am force-stop + Home"
MARK="$(ring_mark)"
ring_save launcher
adb shell am force-stop "$PKG"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/forcestop.xml"
assert_eq "force-stop: no wizard_page" "no" "$(has_node "$ROW_DIR/forcestop.xml" wizard_page)"
start_slice "$MARK" "force-stop"
assert_contains "force-stop: [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"

log "adb install -r <same apk> + Home"
MARK="$(ring_mark)"
ring_save launcher
adb install -r "$APK" > "$ROW_DIR/install-r.txt" 2>&1
assert_contains "install -r succeeded" "Success" "$(cat "$ROW_DIR/install-r.txt")"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/install.xml"
assert_eq "update install: no wizard_page" "no" "$(has_node "$ROW_DIR/install.xml" wizard_page)"
start_slice "$MARK" "install -r"
assert_contains "update install: [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"

log "a core grant made and revoked later"
adb shell cmd notification allow_listener "$LISTENER"; sleep 2
adb shell cmd notification disallow_listener "$LISTENER"; sleep 1
MARK="$(ring_mark)"
resume_start
dump_ui "$ROW_DIR/revoked-later.xml"
assert_eq "revoked later: no wizard_page" "no" "$(has_node "$ROW_DIR/revoked-later.xml" wizard_page)"
start_slice "$MARK" "revoked later"
assert_contains "revoked later: [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"

log "pm clear + disallow_listener + Home: the wizard is back (the marker went with the data)"
leave_home
ring_save launcher
adb shell pm clear "$PKG" >/dev/null
adb shell cmd notification disallow_listener "$LISTENER"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/pmclear.xml"
assert_eq "pm clear: wizard_page again" "yes" "$(has_node "$ROW_DIR/pmclear.xml" wizard_page)"

log "second half: Not now on every step and Done also finish the run"
e2_state
adb shell input keyevent KEYCODE_HOME; sleep 5
seen="$(walk_not_now "$ROW_DIR/walk" | tr '\n' ' ')"
note "steps seen: $seen"
assert_contains "Not now reaches the presets page" "PRESETS" "$seen"
last="$(ls -t "$ROW_DIR"/walk-*.xml | head -1)"
MARK="$(ring_mark)"
tap_node "$last" wizard_done; sleep 3
dump_ui "$ROW_DIR/after-done.xml"
assert_eq "Done: start_page" "yes" "$(has_node "$ROW_DIR/after-done.xml" start_page)"
start_slice "$MARK" "done"
assert_contains "Done: [wizard] finished" "[wizard] finished" "$SLICE"
assert_contains "Done: marker set" '<boolean name="finished" value="true" />' "$(adb shell run-as $PKG cat shared_prefs/setup_wizard.xml | tr -d '\r')"
MARK="$(ring_mark)"
ring_save launcher
adb shell am force-stop "$PKG"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/next-launch.xml"
assert_eq "next launch: no wizard_page" "no" "$(has_node "$ROW_DIR/next-launch.xml" wizard_page)"
start_slice "$MARK" "next launch"
assert_contains "next launch: [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
