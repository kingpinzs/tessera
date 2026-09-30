#!/usr/bin/env bash
# Phase 14 E17 — a pod-bay request under the wizard (T14-7, T14-12; phase 12 E14 (a)'s form, C-15). Every ring read goes
# through phase 12's Start ring read (`p12.sh` start_slice), because the revoked notification listener leaves `diag`
# empty; a missing `tileshell diagnostics:` header fails the row, so no absence assertion can pass on an unreadable ring.
# The reply is the first `[speech] speak[…] text="…"` line in that slice (r3 V3).
#   pm clear → provision with every grant and no finished marker → disallow the listener → Home: the wizard shows;
#   nav_search opens Tess, MARK, type "open the pod bay" → "Opening the pod bay.", the session closes, NOTHING opens and
#   the wizard stays; MARK2, Skip → `[wizard] skip` then `[podbay] opened by voice` with a later wall=, pod_bay in the dump.
#   Restore: pm clear → provision.sh → Home.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"
. "$QA/../phase-12/scripts/p12.sh"

row_begin E17 "a pod-bay request under the wizard waits for it, then opens after Skip"
restore() {
  adb shell pm clear "$PKG" >/dev/null 2>&1
  bash "$PROVISION" > "$ROW_DIR/provision-restore.txt" 2>&1
  echo "rc=$?" >> "$ROW_DIR/provision-restore.txt"
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  sleep 4
  echo "      restored: pm clear, provision.sh ($(tail -1 "$ROW_DIR/provision-restore.txt")), Home" >> "$LOG"
}
trap restore EXIT
reply_in() { printf '%s\n' "$1" | grep -F '[speech] speak[' | grep -oE 'text="[^"]*"' | sed -n '1{s/^text="//;s/"$//;p;q;}'; }

ring_save launcher 2>/dev/null || true
leave_home
adb shell pm clear "$PKG" >/dev/null
provision_no_marker wizard
assert_eq "no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-wizard.rc")"
adb shell cmd notification disallow_listener "$LISTENER"
adb shell input keyevent KEYCODE_HOME; sleep 5
dump_ui "$ROW_DIR/01-wizard.xml"
assert_eq "Home: the wizard shows" "yes" "$(has_node "$ROW_DIR/01-wizard.xml" wizard_page)"

tap_node "$ROW_DIR/01-wizard.xml" nav_search; sleep 3
dump_ui "$ROW_DIR/02-tess.xml"
assert_eq "Search: Tess opens (cortana_session)" "yes" "$(has_node "$ROW_DIR/02-tess.xml" cortana_session)"
MARK="$(ring_mark)"
type_request "open the pod bay" 8
start_slice "$MARK" typed
printf '%s\n' "$SLICE" > "$ROW_DIR/03-typed-slice.txt"
assert_eq "typed: the reply" "Opening the pod bay." "$(reply_in "$SLICE")"
assert_eq "typed: no session window" "no" "$(session_window)"
assert_absent "typed: no [podbay] opened under the wizard" "[podbay] opened" "$SLICE"
assert_absent "typed: no [start] page=POD_BAY" "[start] page=POD_BAY" "$SLICE"
dump_ui "$ROW_DIR/03-after-typed.xml"; screencap "$ROW_DIR/03-after-typed.png"
assert_eq "typed: the wizard still shows" "yes" "$(has_node "$ROW_DIR/03-after-typed.xml" wizard_page)"
assert_eq "typed: no pod_bay" "no" "$(has_node "$ROW_DIR/03-after-typed.xml" pod_bay)"

MARK2="$(ring_mark)"
tap_node "$ROW_DIR/03-after-typed.xml" wizard_skip; sleep 4
start_slice "$MARK2" skip
printf '%s\n' "$SLICE" > "$ROW_DIR/04-skip-slice.txt"
skip_wall="$(wall_of_first "$SLICE" "[wizard] skip")"
open_wall="$(wall_of_first "$SLICE" "[podbay] opened by voice")"
note "skip wall=$skip_wall; opened by voice wall=$open_wall"
assert_ne "Skip: [wizard] skip" "" "$skip_wall"
assert_ne "Skip: [podbay] opened by voice" "" "$open_wall"
assert_eq "Skip: the pod bay opened AFTER the skip" "yes" "$([ -n "$skip_wall" ] && [ -n "$open_wall" ] && [ "$open_wall" -gt "$skip_wall" ] && echo yes || echo no)"
dump_ui "$ROW_DIR/04-after-skip.xml"; screencap "$ROW_DIR/04-after-skip.png"
assert_eq "Skip: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/04-after-skip.xml" pod_bay)"

restore
trap - EXIT
dump_ui "$ROW_DIR/05-restored.xml"
assert_eq "restore: Start" "yes" "$(has_node "$ROW_DIR/05-restored.xml" start_page)"
ensure_start
RINGS="launcher" row_end
