#!/usr/bin/env bash
# Phase 16 E26 — the wizard step "People" (`setup:people`, T16-15), phase 12 E14's template re-cut to phase 12 AS BUILT
# (r3 V1): a PARTIAL row never summons the wizard and "core held" outranks "finished" (onboarding/SetupWizard.kt), so the
# step is summoned by revoking READ_CONTACTS as well, and (c) reads the two states apart. The row has its own driver
# because phase 12's e14.sh hard-codes "Step 1 of 2" and one missing step.
#   (a)  pm clear → no-marker provision → revoke READ + WRITE_CONTACTS → Home: wizard_step:setup:people first, its why
#        line, "Step 1 of 3", `shown: missing=setup:people,tess:contacts`; grant READ → `partial`, the step stays; grant
#        WRITE → `granted`, the presets page
#   (a') the same state again, the step's own action tapped once: what Android does is RECORDed (both at once, or it asks)
#   (b)  pm clear → provision.sh (its new line grants WRITE; it writes the marker): no wizard, `not shown: core held`;
#        then phase 12's own E1, re-run on this build (a child, run after this row's own legs have released the device)
#   (c)  the finished-install rule: revoke both → no wizard, `not shown: finished`, checklist:people:missing; grant READ
#        only → no wizard, `not shown: core held`, checklist:people:partial, Tess's contacts row granted
# Here "Home" is a bare KEYCODE_HOME, as e14.sh:25 presses it, wherever the wizard and not Start is expected.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$P12S/p12.sh"
STEP="setup:people"
revoke() { adb shell pm revoke "$PKG" "android.permission.$1"; }
grant() { adb shell pm grant "$PKG" "android.permission.$1"; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 4; }
checklist_row() { # state -> yes / no, from the Setup page scrolled to the row
  open_checklist
  scroll_to_node "$ROW_DIR/.checklist.xml" "checklist:people:$1" 8 >/dev/null 2>&1 || true
  dump_ui "$ROW_DIR/checklist-$1-$2.xml"
  has_node "$ROW_DIR/checklist-$1-$2.xml" "checklist:people:$1"
}

row_begin E26 "wizard step added: $STEP (phase 12 E14's template, re-cut to phase 12 as built)"
why="$(awk -F'\t' -v k="$STEP" '$1==k {print $2}' "$P12S/why_lines.tsv")"
assert_eq "phase 12's why table holds the T16-15 line for $STEP" "People shows and edits your contacts. Without it People can't see them." "$why"

# ------------------------------------------------------------------------------------------------ (a)
log "(a) pm clear -> PROVISION_FINISH_WIZARD=0 provision.sh -> revoke READ_CONTACTS and WRITE_CONTACTS -> Home"
leave_home
adb shell pm clear "$PKG" >/dev/null
provision_no_marker a
assert_eq "(a) no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-a.rc")"
assert_contains "(a) provision wrote no marker" "PROVISION_FINISH_WIZARD=0: no marker written" "$(cat "$ROW_DIR/provision-a.txt")"
leave_home
revoke READ_CONTACTS; revoke WRITE_CONTACTS
assert_eq "(a) READ_CONTACTS revoked" "false" "$(perm_granted READ_CONTACTS)"
assert_eq "(a) WRITE_CONTACTS revoked" "false" "$(perm_granted WRITE_CONTACTS)"
MARK="$(ring_mark)"
home
dump_ui "$ROW_DIR/a-step.xml"
assert_eq "(a) wizard_page" "yes" "$(has_node "$ROW_DIR/a-step.xml" wizard_page)"
assert_eq "(a) wizard_step:$STEP is the first step" "$STEP" "$(wiz_step "$ROW_DIR/a-step.xml")"
assert_eq "(a) its why line = phase 12's table" "$why" "$(ntext "$ROW_DIR/a-step.xml" wizard_why)"
assert_eq "(a) wizard_progress" "Step 1 of 3" "$(ntext "$ROW_DIR/a-step.xml" wizard_progress)"
start_slice "$MARK" "(a) shown"
assert_contains "(a) shown with People's step and Tess's contacts step" "[wizard] shown: missing=$STEP,tess:contacts" "$SLICE"

MARK="$(ring_mark)"
grant READ_CONTACTS
resume_start
dump_ui "$ROW_DIR/a-partial.xml"
start_slice "$MARK" "(a) READ granted"
assert_contains "(a) READ alone: [wizard] step $STEP: partial" "[wizard] step $STEP: partial" "$SLICE"
assert_eq "(a) READ alone: the step is still showing (PARTIAL is not done for this row)" "yes" "$(has_node "$ROW_DIR/a-partial.xml" "wizard_step:$STEP")"

MARK="$(ring_mark)"
grant WRITE_CONTACTS
resume_start
dump_ui "$ROW_DIR/a-granted.xml"
start_slice "$MARK" "(a) WRITE granted"
assert_contains "(a) both held: [wizard] step $STEP: granted" "[wizard] step $STEP: granted" "$SLICE"
assert_eq "(a) the step is gone" "no" "$(has_node "$ROW_DIR/a-granted.xml" "wizard_step:$STEP")"
# Tess's contacts row was granted by the READ grant, so no step remains: the presets page.
assert_eq "(a) wizard_presets shows" "yes" "$(has_node "$ROW_DIR/a-granted.xml" wizard_presets)"

# ------------------------------------------------------------------------------------------------ (a')
log "(a') the same state again; the step's own action tapped once (what Android does is recorded)"
ring_save
leave_home
adb shell pm clear "$PKG" >/dev/null
provision_no_marker a2
assert_eq "(a') no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-a2.rc")"
leave_home
revoke READ_CONTACTS; revoke WRITE_CONTACTS
home
dump_ui "$ROW_DIR/a2-step.xml"
assert_eq "(a') wizard_step:$STEP showing" "$STEP" "$(wiz_step "$ROW_DIR/a2-step.xml")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/a2-step.xml" wizard_action; sleep 3
dump_ui "$ROW_DIR/a2-dialog.xml"
if grep -q 'package="com[^"]*permissioncontroller"' "$ROW_DIR/a2-dialog.xml"; then
  record "(a') the step's action raised Android's permission dialog" "yes: $(python3 -c '
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("resource-id", "").endswith("permission_message"): print(n.get("text", "")); break' "$ROW_DIR/a2-dialog.xml")"
  perm_tap "$ROW_DIR/a2-allow.xml" allow_button "^Allow$" || true
  sleep 3
  dump_ui "$ROW_DIR/a2-after.xml"
  if grep -q 'package="com[^"]*permissioncontroller"' "$ROW_DIR/a2-after.xml"; then
    record "(a') Android asked a second time (one dialog per permission)" "yes"
    perm_tap "$ROW_DIR/a2-allow2.xml" allow_button "^Allow$" || true
    sleep 3
  else
    record "(a') Android asked a second time (one dialog per permission)" "no: one Allow granted the group"
  fi
else
  record "(a') the step's action raised Android's permission dialog" "no dialog (see a2-dialog.xml)"
fi
record "(a') after the action: READ_CONTACTS / WRITE_CONTACTS granted" "$(perm_granted READ_CONTACTS) / $(perm_granted WRITE_CONTACTS)"
assert_eq "(a') the step's own action granted READ_CONTACTS" "true" "$(perm_granted READ_CONTACTS)"
assert_eq "(a') … and WRITE_CONTACTS" "true" "$(perm_granted WRITE_CONTACTS)"
dump_ui "$ROW_DIR/a2-granted.xml"
start_slice "$MARK" "(a') action"
assert_contains "(a') [wizard] step $STEP: granted" "[wizard] step $STEP: granted" "$SLICE"
assert_eq "(a') the step is gone" "no" "$(has_node "$ROW_DIR/a2-granted.xml" "wizard_step:$STEP")"

# ------------------------------------------------------------------------------------------------ (b)
log "(b) pm clear -> provision.sh (its new line grants WRITE_CONTACTS; it writes the marker) -> Home"
restore_fresh b
assert_eq "(b) provision rc" "0" "$(cat "$ROW_DIR/provision-b.rc")"
assert_contains "(b) provision.sh's WRITE_CONTACTS line ran" "write contacts: android.permission.WRITE_CONTACTS: granted=true" "$(cat "$ROW_DIR/provision-b.txt")"
dump_ui "$ROW_DIR/b-home.xml"
assert_eq "(b) no wizard_page" "no" "$(has_node "$ROW_DIR/b-home.xml" wizard_page)"
MARK="$(ring_mark)"
adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 3
start_slice "$MARK" "(b) resume"
assert_contains "(b) [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"
assert_eq "(b) checklist:people:granted on the Setup page" "yes" "$(checklist_row granted b)"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ------------------------------------------------------------------------------------------------ (c)
log "(c) the finished-install rule, from (b)'s marker"
leave_home
revoke READ_CONTACTS; revoke WRITE_CONTACTS
MARK="$(ring_mark)"
home
dump_ui "$ROW_DIR/c-missing.xml"
assert_eq "(c) both revoked: no wizard_page" "no" "$(has_node "$ROW_DIR/c-missing.xml" wizard_page)"
start_slice "$MARK" "(c) both revoked"
assert_contains "(c) both revoked: [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"
assert_eq "(c) both revoked: checklist:people:missing" "yes" "$(checklist_row missing c)"
leave_home
grant READ_CONTACTS
assert_eq "(c) READ held, WRITE still revoked" "true false" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"
MARK="$(ring_mark)"
home
dump_ui "$ROW_DIR/c-partial.xml"
assert_eq "(c) READ only: no wizard_page" "no" "$(has_node "$ROW_DIR/c-partial.xml" wizard_page)"
start_slice "$MARK" "(c) READ only"
assert_contains "(c) READ only: [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"
assert_eq "(c) READ only: checklist:people:partial" "yes" "$(checklist_row partial c)"
# Tess's contacts row asks READ only (T16-15): granted. Her checklist is the Tess settings page's.
if open_tess_settings "$ROW_DIR/c-tess"; then
  scroll_to_node "$ROW_DIR/c-tess-contacts.xml" "cortana_check:contacts:granted" 8 >/dev/null 2>&1 || true
  assert_eq "(c) READ only: Tess's contacts row reads granted (cortana_check:contacts:granted)" "yes" "$(has_node "$ROW_DIR/c-tess-contacts.xml" cortana_check:contacts:granted)"
else
  _verdict FAIL "(c) READ only: Tess's contacts row reads granted" "Tess's settings page could not be opened"
fi
cortana_close
grant WRITE_CONTACTS
assert_eq "(c) restore: both held" "true true" "$(perm_granted READ_CONTACTS) $(perm_granted WRITE_CONTACTS)"

# ------------------------------------------------------------------------------------------------ restore
log "restore: pm clear -> provision.sh -> ensure_start"
restore_fresh restore
assert_eq "restore: provision rc" "0" "$(cat "$ROW_DIR/provision-restore.rc")"
ensure_start
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
row_end; ROW_RC=$?

# ------------------------------------------------------------------------------------------------ the child
# Phase 12's E1, unchanged, on this build ("phase 12 E1 re-run passes on this build"). It takes the device lock itself,
# so it runs after this row has ended; its own row directory is moved aside and put back, the new run kept under E26/.
exec 9>&-
P12DIR="$(cd "$QA/../phase-12" && pwd)"
[ -e "$P12DIR/E1" ] && mv "$P12DIR/E1" "$P12DIR/E1.p16-e26-aside"
( bash "$P12S/e1.sh" > "$ROW_DIR/phase-12-E1.out" 2>&1; echo $? > "$ROW_DIR/phase-12-E1.rc" )
rm -rf "$ROW_DIR/phase-12-E1"; [ -e "$P12DIR/E1" ] && mv "$P12DIR/E1" "$ROW_DIR/phase-12-E1"
[ -e "$P12DIR/E1.p16-e26-aside" ] && mv "$P12DIR/E1.p16-e26-aside" "$P12DIR/E1"
C_RC="$(cat "$ROW_DIR/phase-12-E1.rc")"
C_SUM="$(grep -E '^E1: [0-9]+ passed' "$ROW_DIR/phase-12-E1/E1.txt" 2>/dev/null | tail -1)"
{
  echo "------------------------------------------------------------------------------"
  echo "child: phase 12 E1 on this build: rc=$C_RC ${C_SUM:-(no summary)} (kept at $ROW_DIR/phase-12-E1/)"
  if [ "$C_RC" = "0" ] && echo "$C_SUM" | grep -q ', 0 failed'; then echo "PASS  phase 12 E1 re-run passes on this build"; else echo "FAIL  phase 12 E1 re-run passes on this build"; fi
} | tee -a "$LOG"
# The child leaves a provisioned device; the layout goes back to this phase's baseline.
layout_restore "$BASELINE" >/dev/null 2>&1
[ "$ROW_RC" = "0" ] && [ "$C_RC" = "0" ] && echo "$C_SUM" | grep -q ', 0 failed'
