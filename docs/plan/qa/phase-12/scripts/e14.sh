#!/usr/bin/env bash
# Phase 12 E14 — "wizard step added", the template every later phase runs for its own step, proven here on setup:usage
# (revoke = appops GET_USAGE_STATS ignore, restore = allow; (c)'s row checklist:usage:missing). Three parts (C-15):
#   (a) no-marker provision -> revoke -> Home: the step alone, its why line, "Step 1 of 2"; grant + resume -> the presets page
#   (b) provision (marker) -> Home: no wizard; a resume logs "not shown: core held"
#   (c) from (b), revoke -> a resume: no wizard, "not shown: finished", the checklist row reads missing; restore the grant
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
ROW_ID="${E14_ROW:-E14}"
STEP="${E14_STEP:-setup:usage}"
REVOKE="${E14_REVOKE:-adb shell appops set app.tileshell GET_USAGE_STATS ignore}"
GRANT="${E14_GRANT:-adb shell appops set app.tileshell GET_USAGE_STATS allow}"
CHECK_ROW="${E14_CHECK:-checklist:usage:missing}"
row_begin "$ROW_ID" "wizard step added: $STEP"
why="$(awk -F'\t' -v k="$STEP" '$1==k {print $2}' "$HERE/why_lines.tsv")"

log "(a) pm clear -> PROVISION_FINISH_WIZARD=0 provision.sh -> revoke -> Home"
leave_home
adb shell pm clear "$PKG" >/dev/null
provision_no_marker a
assert_eq "(a) no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-a.rc")"
assert_contains "(a) provision wrote no marker" "PROVISION_FINISH_WIZARD=0: no marker written" "$(cat "$ROW_DIR/provision-a.txt")"
eval "$REVOKE"
MARK="$(ring_mark)"
adb shell input keyevent KEYCODE_HOME; sleep 4
dump_ui "$ROW_DIR/a-step.xml"
assert_eq "(a) wizard_step:$STEP" "yes" "$(has_node "$ROW_DIR/a-step.xml" "wizard_step:$STEP")"
assert_eq "(a) its why line = the table's" "$why" "$(ntext "$ROW_DIR/a-step.xml" wizard_why)"
assert_eq "(a) wizard_progress" "Step 1 of 2" "$(ntext "$ROW_DIR/a-step.xml" wizard_progress)"
start_slice "$MARK" "(a) shown"
assert_contains "(a) shown with that step alone" "[wizard] shown: missing=$STEP" "$SLICE"
eval "$GRANT"
MARK="$(ring_mark)"
resume_start
dump_ui "$ROW_DIR/a-granted.xml"
assert_eq "(a) the step is gone" "no" "$(has_node "$ROW_DIR/a-granted.xml" "wizard_step:$STEP")"
assert_eq "(a) wizard_presets shows" "yes" "$(has_node "$ROW_DIR/a-granted.xml" wizard_presets)"
start_slice "$MARK" "(a) granted"
assert_contains "(a) [wizard] step $STEP: granted" "[wizard] step $STEP: granted" "$SLICE"

log "(b) pm clear -> provision.sh (carries the grant, writes the marker) -> Home"
restore_fresh b
assert_eq "(b) provision rc" "0" "$(cat "$ROW_DIR/provision-b.rc")"
dump_ui "$ROW_DIR/b-home.xml"
assert_eq "(b) no wizard_page" "no" "$(has_node "$ROW_DIR/b-home.xml" wizard_page)"
MARK="$(ring_mark)"
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
start_slice "$MARK" "(b) resume"
assert_contains "(b) [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"

log "(c) the finished-install rule: revoke -> a resume"
eval "$REVOKE"
MARK="$(ring_mark)"
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/c-home.xml"
assert_eq "(c) no wizard_page" "no" "$(has_node "$ROW_DIR/c-home.xml" wizard_page)"
start_slice "$MARK" "(c) resume"
assert_contains "(c) [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"
open_checklist
scroll_to_node "$ROW_DIR/c-checklist.xml" "$CHECK_ROW" 6
assert_eq "(c) $CHECK_ROW" "yes" "$(has_node "$ROW_DIR/c-checklist.xml" "$CHECK_ROW")"
eval "$GRANT"
adb shell input keyevent KEYCODE_HOME; sleep 1
row_end
