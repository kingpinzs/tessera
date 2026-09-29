#!/usr/bin/env bash
# Phase 12 E7 — revoked mid-run. From the E2 state, past Notification access (granted) to the Photos step; notification
# access revoked from adb + a resume: N grows by one and no second "[wizard] shown" (same run); a grant made from adb on
# the Photos step advances it on the next resume; walking on, setup:notifications is seen again exactly once, before the
# presets page (it rejoins the steps not yet shown, T12-1 (d)).
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
row_begin E7 "a grant revoked, and one made, mid-run"

e2_state
assert_e2_appops
MARK0="$(ring_mark)"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/s0.xml"
assert_eq "first step: notifications" "setup:notifications" "$(wiz_step "$ROW_DIR/s0.xml")"
adb shell cmd notification allow_listener "$LISTENER"
resume_start
dump_ui "$ROW_DIR/s1.xml"
assert_eq "notification access granted: on the Photos step" "setup:photos" "$(wiz_step "$ROW_DIR/s1.xml")"
assert_eq "Step 2 of 20" "Step 2 of 20" "$(ntext "$ROW_DIR/s1.xml" wizard_progress)"

log "revoke notification access from adb, then a resume"
adb shell cmd notification disallow_listener "$LISTENER"
resume_start
dump_ui "$ROW_DIR/s2.xml"
assert_eq "still on the Photos step" "setup:photos" "$(wiz_step "$ROW_DIR/s2.xml")"
assert_eq "N grew by one: Step 2 of 21" "Step 2 of 21" "$(ntext "$ROW_DIR/s2.xml" wizard_progress)"
start_slice "$MARK0" "run so far"
assert_eq "[wizard] shown not repeated (same run)" "1" "$(printf '%s\n' "$SLICE" | grep -c '\[wizard\] shown: ')"

log "a grant made from adb while the Photos step shows"
MARK="$(ring_mark)"
adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES
resume_start
dump_ui "$ROW_DIR/s3.xml"
assert_ne "the Photos step advanced" "setup:photos" "$(wiz_step "$ROW_DIR/s3.xml")"
start_slice "$MARK" "photos granted from adb"
assert_contains "[wizard] step setup:photos: granted" "[wizard] step setup:photos: granted" "$SLICE"

log "walk on with Not now"
seen="$(walk_not_now "$ROW_DIR/walk" | tr '\n' ' ')"
note "steps seen: $seen"
assert_eq "setup:notifications seen again exactly once" "1" "$(echo "$seen" | tr ' ' '\n' | grep -c '^setup:notifications$')"
assert_contains "... and before the presets page" "setup:notifications" "$(echo "$seen" | sed 's/PRESETS.*//')"
assert_contains "the run ends on the presets page" "PRESETS" "$seen"
assert_absent "setup:photos is not asked again" "setup:photos" "$seen"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
