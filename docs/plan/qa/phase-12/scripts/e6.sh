#!/usr/bin/env bash
# Phase 12 E6 — denied for good. Photos denied twice: the button becomes "Open app info" and opens the shell's app-info
# page; Back returns to the same step. Tess's microphone denied twice: CortanaPermissionActivity opens app info at once
# (phase 03's rule), and on the way back the wizard's button says "Open app info" too (r3 D12).
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E6 "denied for good: Photos and Tess's microphone"

e2_state
assert_e2_appops
adb shell input keyevent KEYCODE_HOME; sleep 5
walk_to setup:photos "$ROW_DIR/p"
assert_eq "on the Photos step" "setup:photos" "$(wiz_step "$ROW_DIR/p-at.xml")"
assert_eq "button: Allow" "Allow" "$(ntext "$ROW_DIR/p-at.xml" wizard_action)"

deny_once() { # label dump-in
  tap_node "$2" wizard_action; sleep 2.5
  note "$1: resumed $(resumed_activity)"
  assert_contains "$1: Android's dialog is up" "permissioncontroller" "$(resumed_activity)"
  perm_tap "$ROW_DIR/$1-dialog.xml" deny_button "don.t allow"
  sleep 2.5
}

MARK="$(ring_mark)"
deny_once photos-1 "$ROW_DIR/p-at.xml"
dump_ui "$ROW_DIR/p-after1.xml"
assert_eq "after one denial: still the Photos step" "setup:photos" "$(wiz_step "$ROW_DIR/p-after1.xml")"
assert_eq "after one denial: the button still says Allow (Android will ask again)" "Allow" "$(ntext "$ROW_DIR/p-after1.xml" wizard_action)"
deny_once photos-2 "$ROW_DIR/p-after1.xml"
dump_ui "$ROW_DIR/p-after2.xml"
assert_eq "after the second denial: still the Photos step" "setup:photos" "$(wiz_step "$ROW_DIR/p-after2.xml")"
assert_eq "after the second denial: Open app info" "Open app info" "$(ntext "$ROW_DIR/p-after2.xml" wizard_action)"
start_slice "$MARK" "photos denied"
assert_contains "[wizard] step setup:photos: blocked (app info)" "[wizard] step setup:photos: blocked (app info)" "$SLICE"
assert_eq "blocked logged once" "1" "$(printf '%s\n' "$SLICE" | grep -c 'step setup:photos: blocked')"
tap_node "$ROW_DIR/p-after2.xml" wizard_action; sleep 3
note "app info: $(resumed_activity)"
assert_contains "Open app info resumes com.android.settings" "com.android.settings" "$(resumed_activity)"
dump_ui "$ROW_DIR/p-appinfo.xml"
assert_contains "the page is the shell's app info" "Tessera" "$(cat "$ROW_DIR/p-appinfo.xml")"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump_ui "$ROW_DIR/p-back.xml"
assert_eq "Back returns to the same step" "setup:photos" "$(wiz_step "$ROW_DIR/p-back.xml")"
assert_eq "and it still says Open app info" "Open app info" "$(ntext "$ROW_DIR/p-back.xml" wizard_action)"

log "tess:microphone denied twice"
walk_to tess:microphone "$ROW_DIR/m"
assert_eq "on the microphone step" "tess:microphone" "$(wiz_step "$ROW_DIR/m-at.xml")"
MARK="$(ring_mark)"
deny_once mic-1 "$ROW_DIR/m-at.xml"
dump_ui "$ROW_DIR/m-after1.xml"
assert_eq "mic, one denial: still the step, Allow" "Allow" "$(ntext "$ROW_DIR/m-after1.xml" wizard_action)"
tap_node "$ROW_DIR/m-after1.xml" wizard_action; sleep 2.5
perm_tap "$ROW_DIR/mic-2-dialog.xml" deny_button "don.t allow"
sleep 3.5
note "after the second mic denial: $(resumed_activity)"
assert_contains "the second denial opens app info at once" "com.android.settings" "$(resumed_activity)"
start_slice "$MARK" "mic denied"
assert_contains "[cortana] permission blocked ... opening app info" "[cortana] permission blocked by Android (no prompt shown): [android.permission.RECORD_AUDIO]; opening app info" "$SLICE"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump_ui "$ROW_DIR/m-back.xml"
assert_eq "Back: the microphone step" "tess:microphone" "$(wiz_step "$ROW_DIR/m-back.xml")"
assert_eq "Back: Open app info" "Open app info" "$(ntext "$ROW_DIR/m-back.xml" wizard_action)"
start_slice "$MARK" "mic back"
assert_contains "[wizard] step tess:microphone: blocked (app info)" "[wizard] step tess:microphone: blocked (app info)" "$SLICE"
tap_node "$ROW_DIR/m-back.xml" wizard_action; sleep 3
assert_contains "tapping it resumes app info again" "com.android.settings" "$(resumed_activity)"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump_ui "$ROW_DIR/m-back2.xml"
assert_eq "Back returns to the step" "tess:microphone" "$(wiz_step "$ROW_DIR/m-back2.xml")"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
