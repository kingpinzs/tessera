#!/usr/bin/env bash
# Phase 12 E4 — persistence and resume. No step index is stored: from the E2 state, grant the Photos step through
# Android's dialog, then (1) am force-stop + Home and (2) adb reboot: each time the wizard is back on
# setup:notifications, "Step 1 of 19" (one fewer N), and setup:photos is never in the run.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E4 "persistence and resume after process death and reboot"

e2_state
assert_e2_appops
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/s0.xml"
assert_eq "E2 state: first step notifications" "setup:notifications" "$(wiz_step "$ROW_DIR/s0.xml")"
assert_eq "E2 state: Step 1 of 20" "Step 1 of 20" "$(ntext "$ROW_DIR/s0.xml" wizard_progress)"
tap_node "$ROW_DIR/s0.xml" wizard_not_now; sleep 1.5
dump_ui "$ROW_DIR/s1.xml"
assert_eq "on the Photos step" "setup:photos" "$(wiz_step "$ROW_DIR/s1.xml")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/s1.xml" wizard_action; sleep 2.5
note "resumed while the dialog shows: $(resumed_activity)"
assert_contains "Android's permission dialog is up" "permissioncontroller" "$(resumed_activity)"
perm_tap "$ROW_DIR/dialog.xml" allow_all_button "allow all"
sleep 3
dump_ui "$ROW_DIR/s2.xml"
assert_eq "the Photos step advanced (music next)" "setup:music" "$(wiz_step "$ROW_DIR/s2.xml")"
start_slice "$MARK" "photos granted"
assert_contains "[wizard] step setup:photos: granted" "[wizard] step setup:photos: granted" "$SLICE"
assert_contains "READ_MEDIA_IMAGES granted" "READ_MEDIA_IMAGES: granted=true" "$(adb shell dumpsys package $PKG | grep 'READ_MEDIA_IMAGES: granted' | head -1)"

check_rederived() { # label
  local label="$1"
  dump_ui "$ROW_DIR/$label-first.xml"
  assert_eq "($label) wizard_page" "yes" "$(has_node "$ROW_DIR/$label-first.xml" wizard_page)"
  assert_eq "($label) first step setup:notifications" "setup:notifications" "$(wiz_step "$ROW_DIR/$label-first.xml")"
  assert_eq "($label) one fewer N: Step 1 of 19" "Step 1 of 19" "$(ntext "$ROW_DIR/$label-first.xml" wizard_progress)"
  local seen
  seen="$(walk_not_now "$ROW_DIR/$label-walk" | tr '\n' ' ')"
  note "($label) steps seen: $seen"
  assert_absent "($label) setup:photos nowhere in the run" "setup:photos" "$seen"
  assert_contains "($label) the run ends on the presets page" "PRESETS" "$seen"
  assert_eq "($label) eighteen steps walked" "18" "$(echo "$seen" | tr ' ' '\n' | grep -c ':')"
}

log "(1) am force-stop + Home"
MARK="$(ring_mark)"
ring_save launcher
adb shell am force-stop "$PKG"
adb shell input keyevent KEYCODE_HOME; sleep 5
check_rederived forcestop
start_slice "$MARK" "force-stop"
assert_contains "(forcestop) a new run: [wizard] shown without setup:photos" "[wizard] shown: missing=setup:notifications,setup:music" "$SLICE"

log "(2) adb reboot in place of the force-stop"
ring_save launcher
MARK="$(ring_mark)"   # the wall clock runs on across the reboot, so the new process's lines all fall after this
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
sleep 12
assert_eq "wake_device after the reboot" "Awake" "$(wake_device)"
adb shell input keyevent KEYCODE_HOME; sleep 6
check_rederived reboot
start_slice "$MARK" "reboot"
assert_contains "(reboot) a new run: [wizard] shown without setup:photos" "[wizard] shown: missing=setup:notifications,setup:music" "$SLICE"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
