#!/usr/bin/env bash
# Phase 17 E25 — the wizard steps "Camera" (setup:camera, CAMERA) and "Videos" (setup:videos, READ_MEDIA_VIDEO):
# phase 12 E14's template in its three-part form (C-4 b, C-15), as phase 16's e26.sh cut it to phase 12 as built.
#
#   (a)  pm clear → PROVISION_FINISH_WIZARD=0 provision.sh → pm revoke CAMERA and READ_MEDIA_VIDEO (with Android's
#        Settings in front, so the fresh Start evaluates after both are gone) → Home: the wizard shows
#        `[wizard] shown: missing=setup:camera,setup:videos`; wizard_step:setup:camera is first with task 8's why line
#        and "Step 1 of 3" (two steps and the presets page); "Not now" brings wizard_step:setup:videos, second in the
#        Setup rows' order, with its why line and "Step 2 of 3"; Back returns to the Camera step (asked again). Then
#        pm grant both from adb and a resume → both steps gone and wizard_presets shows, `[wizard] step setup:camera:
#        granted` in the slice.
#   (b)  pm clear → provision.sh (its phase 17 lines grant both; it writes the marker) → Home: start_page and no
#        wizard_page; after a MARK and a resume (am start the Settings hub, Home) `[wizard] not shown: core held`
#        (phase 12 E1's assertion on this build, C-4 c); both checklist rows read granted.
#   (c)  the finished-install rule, from (b)'s marker: pm revoke both → a MARK, a resume as in (b) → no wizard_page,
#        `[wizard] not shown: finished`, checklist:camera:missing and checklist:videos:missing on the Setup page (the
#        rows titled "Camera" and "Videos"); pm grant both (RV12), read back.
#   restore  pm clear → provision.sh → Start → the baseline layout; both grants held.
#   child    phase 12's own E1, unchanged, re-run on this build after the row has released the device (phase 16
#            e26.sh's form; the row's "(phase 12 E1 re-run on this build, C-4 c)"). E25_CHILD=0 skips it.
#
# Recorded, not asserted: a `step setup:videos: granted` line in (a) — phase 12's reconcile logs the line of the step
# that was showing (Camera); Videos was never the current step, so its absence from the walk is read from the dump.
# "Home" where the wizard and not Start is expected is a bare KEYCODE_HOME, as phase 12's e14.sh presses it.
#
# THIS ROW WIPES THE SHELL'S DATA THREE TIMES (pm clear): it runs only when no other session is using the device.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
QAR="$(cd "$QA/.." && pwd)"
P12S="$QAR/phase-12/scripts"
. "$P12S/p12.sh"
OUT="$QA/E25"; mkdir -p "$OUT"
CAMERA_WHY="Camera takes your photos and videos. Without it the Camera tile can't open the shell's camera."
VIDEOS_WHY="Movies & TV and Photos show the videos on this phone. Without it they show none."

revoke_both() { adb shell pm revoke "$PKG" android.permission.CAMERA; adb shell pm revoke "$PKG" android.permission.READ_MEDIA_VIDEO; }
grant_both() { adb shell pm grant "$PKG" android.permission.CAMERA; adb shell pm grant "$PKG" android.permission.READ_MEDIA_VIDEO; }
both() { echo "$(perm_granted CAMERA) $(perm_granted READ_MEDIA_VIDEO)"; }
home() { adb shell input keyevent KEYCODE_HOME; sleep 4; }
# The resume (b) and (c) take from phase 12 E14: the Settings hub over Start, then Home.
resume_by_settings() { adb shell am start -n "$PKG/.settings.SettingsActivity" >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_HOME; sleep 3; }
# A Setup checklist row: scrolled to, dumped, "yes <its texts>" or "no".
checklist_row() { # id state label
  open_checklist
  scroll_to_node "$ROW_DIR/.checklist.xml" "checklist:$1:$2" 8 >/dev/null 2>&1 || true
  dump_ui "$ROW_DIR/checklist-$1-$2-$3.xml"
  if [ "$(has_node "$ROW_DIR/checklist-$1-$2-$3.xml" "checklist:$1:$2")" = yes ]; then
    echo "yes $(python3 - "$ROW_DIR/checklist-$1-$2-$3.xml" "checklist:$1:$2" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        print(" | ".join(t for t in (d.get("text", "") for d in n.iter("node")) if t)); break
PY
)"
  else
    echo no
  fi
}

take_device_lock
if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
  adb install -r "$APK" > "$OUT/install.out" 2>&1 || { echo "E25: adb install -r of $APK failed:" >&2; cat "$OUT/install.out" >&2; exit 4; }
fi
row_begin E25 "wizard steps added: setup:camera and setup:videos (phase 12 E14's template, the three-part form)"
assert_contains "the device holds this build" "yes" "$(apk_matches)"
assert_eq "wake" "Awake" "$(wake_device)"
# The why lines are task 8's (the phase doc); the app's table is read here only to say that it holds the same text.
WIZ_KT="$REPO/app/src/main/kotlin/app/tileshell/onboarding/SetupWizard.kt"
assert_eq "SetupWizard.kt holds task 8's why line for setup:camera" "1" "$(grep -cF "\"$CAMERA_WHY\"" "$WIZ_KT")"
assert_eq "SetupWizard.kt holds task 8's why line for setup:videos" "1" "$(grep -cF "\"$VIDEOS_WHY\"" "$WIZ_KT")"

# ------------------------------------------------------------------------------------------------ (a)
log "(a) pm clear -> PROVISION_FINISH_WIZARD=0 provision.sh -> revoke CAMERA and READ_MEDIA_VIDEO -> Home"
rings_save
leave_home
adb shell pm clear "$PKG" >/dev/null
provision_no_marker a
assert_eq "(a) no-marker provision rc" "0" "$(cat "$ROW_DIR/provision-a.rc")"
assert_contains "(a) provision wrote no marker" "PROVISION_FINISH_WIZARD=0: no marker written" "$(cat "$ROW_DIR/provision-a.txt")"
leave_home
revoke_both
assert_eq "(a) CAMERA and READ_MEDIA_VIDEO revoked" "false false" "$(both)"
MARK="$(ring_mark)"
home
dump_ui "$ROW_DIR/a-step1.xml"; screencap "$ROW_DIR/a-step1.png"
assert_eq "(a) wizard_page" "yes" "$(has_node "$ROW_DIR/a-step1.xml" wizard_page)"
assert_eq "(a) wizard_step:setup:camera is the first step (the Setup rows' order)" "setup:camera" "$(wiz_step "$ROW_DIR/a-step1.xml")"
assert_eq "(a) its wizard_why = task 8's line" "$CAMERA_WHY" "$(ntext "$ROW_DIR/a-step1.xml" wizard_why)"
assert_eq "(a) wizard_progress" "Step 1 of 3" "$(ntext "$ROW_DIR/a-step1.xml" wizard_progress)"
start_slice "$MARK" "(a) shown"
assert_eq "(a) the line: shown with the two steps, Camera before Videos, and no other" "1" "$(wizard_lines | grep -cxF '[wizard] shown: missing=setup:camera,setup:videos')"
# The second step, reached without granting the first: Not now, then Back to it.
MARK="$(ring_mark)"
tap_node "$ROW_DIR/a-step1.xml" wizard_not_now; sleep 1.5
dump_ui "$ROW_DIR/a-step2.xml"; screencap "$ROW_DIR/a-step2.png"
assert_eq "(a) wizard_step:setup:videos is the second step" "setup:videos" "$(wiz_step "$ROW_DIR/a-step2.xml")"
assert_eq "(a) its wizard_why = task 8's line" "$VIDEOS_WHY" "$(ntext "$ROW_DIR/a-step2.xml" wizard_why)"
assert_eq "(a) wizard_progress on it" "Step 2 of 3" "$(ntext "$ROW_DIR/a-step2.xml" wizard_progress)"
start_slice "$MARK" "(a) not now"
assert_contains "(a) [wizard] step setup:camera: not now" "[wizard] step setup:camera: not now" "$SLICE"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/a-step1-again.xml"
assert_eq "(a) Back returns to the Camera step" "setup:camera" "$(wiz_step "$ROW_DIR/a-step1-again.xml")"
assert_eq "(a) … at Step 1 of 3 again (both steps still to do)" "Step 1 of 3" "$(ntext "$ROW_DIR/a-step1-again.xml" wizard_progress)"

MARK="$(ring_mark)"
grant_both
assert_eq "(a) pm grant both from adb" "true true" "$(both)"
resume_start
dump_ui "$ROW_DIR/a-granted.xml"; screencap "$ROW_DIR/a-granted.png"
assert_eq "(a) after the grants and a resume: still the wizard's page" "yes" "$(has_node "$ROW_DIR/a-granted.xml" wizard_page)"
assert_eq "(a) wizard_step:setup:camera is gone" "no" "$(has_node "$ROW_DIR/a-granted.xml" wizard_step:setup:camera)"
assert_eq "(a) wizard_step:setup:videos is gone" "no" "$(has_node "$ROW_DIR/a-granted.xml" wizard_step:setup:videos)"
assert_eq "(a) wizard_presets shows" "yes" "$(has_node "$ROW_DIR/a-granted.xml" wizard_presets)"
start_slice "$MARK" "(a) granted"
assert_contains "(a) [wizard] step setup:camera: granted" "[wizard] step setup:camera: granted" "$SLICE"
record "(a) a [wizard] step setup:videos: granted line (the step was never the one showing)" "$(printf '%s\n' "$SLICE" | grep -cF '[wizard] step setup:videos: granted')"

# ------------------------------------------------------------------------------------------------ (b)
log "(b) pm clear -> provision.sh (its phase 17 lines grant both; it writes the marker) -> Home"
restore_fresh b
assert_eq "(b) provision rc" "0" "$(cat "$ROW_DIR/provision-b.rc")"
assert_contains "(b) provision wrote the marker" '<boolean name="finished" value="true" />' "$(grep '^marker:' "$ROW_DIR/provision-b.txt")"
assert_eq "(b) provision.sh's two grant lines ran: CAMERA and READ_MEDIA_VIDEO held" "true true" "$(both)"
dump_ui "$ROW_DIR/b-home.xml"
assert_eq "(b) start_page" "yes" "$(has_node "$ROW_DIR/b-home.xml" start_page)"
assert_eq "(b) no wizard_page" "no" "$(has_node "$ROW_DIR/b-home.xml" wizard_page)"
MARK="$(ring_mark)"
resume_by_settings
start_slice "$MARK" "(b) resume"
assert_contains "(b) [wizard] not shown: core held" "[wizard] not shown: core held" "$SLICE"
assert_absent "(b) no [wizard] shown line" "[wizard] shown" "$SLICE"
assert_contains "(b) checklist:camera:granted on the Setup page" "yes" "$(checklist_row camera granted b)"
assert_contains "(b) checklist:videos:granted on the Setup page" "yes" "$(checklist_row videos granted b)"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ------------------------------------------------------------------------------------------------ (c)
log "(c) the finished-install rule, from (b)'s marker: revoke both -> a MARK, a resume as in (b)"
revoke_both
sleep 3
assert_eq "(c) CAMERA and READ_MEDIA_VIDEO revoked" "false false" "$(both)"
MARK="$(ring_mark)"
resume_by_settings
dump_ui "$ROW_DIR/c-home.xml"; screencap "$ROW_DIR/c-home.png"
assert_eq "(c) start_page" "yes" "$(has_node "$ROW_DIR/c-home.xml" start_page)"
assert_eq "(c) no wizard_page" "no" "$(has_node "$ROW_DIR/c-home.xml" wizard_page)"
start_slice "$MARK" "(c) resume"
assert_contains "(c) [wizard] not shown: finished" "[wizard] not shown: finished" "$SLICE"
assert_absent "(c) no [wizard] shown line" "[wizard] shown" "$SLICE"
C_CAM="$(checklist_row camera missing c)"; C_VID="$(checklist_row videos missing c)"
assert_contains "(c) the Camera checklist row reads missing (checklist:camera:missing)" "yes" "$C_CAM"
assert_contains "(c) … and is the row titled Camera" "Camera" "$C_CAM"
assert_contains "(c) the Videos checklist row reads missing (checklist:videos:missing)" "yes" "$C_VID"
assert_contains "(c) … and is the row titled Videos" "Videos" "$C_VID"
grant_both
assert_eq "(c) pm grant both (RV12)" "true true" "$(both)"
assert_contains "(c) after the grants the Camera row reads granted" "yes" "$(checklist_row camera granted c)"
assert_contains "(c) … and the Videos row" "yes" "$(checklist_row videos granted c)"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ------------------------------------------------------------------------------------------------ restore
log "restore: pm clear -> provision.sh -> ensure_start -> the baseline"
restore_fresh restore
assert_eq "restore: provision rc" "0" "$(cat "$ROW_DIR/provision-restore.rc")"
ensure_start
layout_restore "$BASELINE" > "$OUT/restore-layout.out" 2>&1; assert_eq "restore: layout_restore of the baseline" "0" "$?"
assert_eq "restore: CAMERA and READ_MEDIA_VIDEO held" "true true" "$(both)"
ensure_start
row_end; ROW_RC=$?

# ------------------------------------------------------------------------------------------------ the child
# Phase 12's E1, unchanged, on this build. It takes the device lock itself, so it runs after this row has ended; its own
# row directory is moved aside and put back, the new run kept under E25/ (phase 16 e26.sh's form).
[ "${E25_CHILD:-1}" = "1" ] || { echo "child: SKIPPED (E25_CHILD=0): a development run, not the gate's" | tee -a "$LOG"; exit "$ROW_RC"; }
exec 9>&-
P12DIR="$QAR/phase-12"
[ -e "$P12DIR/E1" ] && mv "$P12DIR/E1" "$P12DIR/E1.p17-e25-aside"
( bash "$P12S/e1.sh" > "$ROW_DIR/phase-12-E1.out" 2>&1; echo $? > "$ROW_DIR/phase-12-E1.rc" )
rm -rf "$ROW_DIR/phase-12-E1.prev"; [ -e "$ROW_DIR/phase-12-E1" ] && mv "$ROW_DIR/phase-12-E1" "$ROW_DIR/phase-12-E1.prev"
[ -e "$P12DIR/E1" ] && mv "$P12DIR/E1" "$ROW_DIR/phase-12-E1"
[ -e "$P12DIR/E1.p17-e25-aside" ] && mv "$P12DIR/E1.p17-e25-aside" "$P12DIR/E1"
C_RC="$(cat "$ROW_DIR/phase-12-E1.rc")"
C_SUM="$(grep -E '^E1: [0-9]+ passed' "$ROW_DIR/phase-12-E1/E1.txt" 2>/dev/null | tail -1)"
C_MATCH="$(grep -m1 '^apk match' "$ROW_DIR/phase-12-E1/E1.txt" 2>/dev/null)"
{
  echo "------------------------------------------------------------------------------"
  echo "child: phase 12 E1 on this build: rc=$C_RC ${C_SUM:-(no summary)}; ${C_MATCH:-(no apk line)} (kept at $ROW_DIR/phase-12-E1/)"
  if [ "$C_RC" = "0" ] && echo "$C_SUM" | grep -q ', 0 failed' && echo "$C_MATCH" | grep -q 'yes'; then
    echo "PASS  phase 12 E1 re-run passes on this build"
  else
    echo "FAIL  phase 12 E1 re-run passes on this build"
  fi
} | tee -a "$LOG"
# The child ends on a provisioned device (pm clear → provision.sh): the layout goes back to this phase's baseline.
layout_restore "$BASELINE" >/dev/null 2>&1
[ "$ROW_RC" = "0" ] && [ "$C_RC" = "0" ] && echo "$C_SUM" | grep -q ', 0 failed' && echo "$C_MATCH" | grep -q 'yes'
