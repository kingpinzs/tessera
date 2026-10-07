#!/usr/bin/env bash
# Phase 18 E19: phase 17's guards under All-files access (r3 D10 / V18), on THIS build with the appop `allow`.
#
#   e19.sh a      row E19a — (a) the JVM suites UriAccessRulesTest, ViewerRulesTest, PlayerRulesTest and
#                 CaptureOutputGuardTest, and the two scan guards UriAccessWiringScanTest and TrustWiringScanTest,
#                 each through the floor's gate (rc 0, 0 failures / errors / skipped). No device command is run.
#   e19.sh bc     row E19 — (b) phase 17's drivers trust_photos.sh, trust_video.sh, guard_selftest_video.sh and its
#                 capture-output row e9.sh re-run AS THEY ARE on this build, their output under qa/phase-18/E19/p17/;
#                 (c) RECORDED: Photos' delete of the row's own qa-photo-1.png with All-files held — whether
#                 MediaProvider's write-consent dialog shows (`record photos_delete_dialog <shown|skipped>`).
#   e19.sh        both rows, a first.
#
# (b) HOW phase 17's drivers run without writing under qa/phase-17/: each of them derives every path it writes from
# its own location (lib.sh: HERE = the script's folder, QA = HERE/.., REPO = QA/../../../..; p17.sh: P17 = QA, GEN =
# P17/gen/media). So they are run through a MIRROR: qa/phase-18/E19/p17/scripts/ holds one symlink per file of
# qa/phase-17/scripts/ (the very files — the log's driver blob is phase 17's), E19/p17/fixtures and E19/p17/dev-camera
# are symlinks to phase 17's read-only fixture folders, E19/p17/baseline_layout.json is THIS phase's baseline (phase
# 17's lacks phase 18's addedOnce marker, so on this build the shell would seed the Files tile again), and
# docs/plan/qa/p17-on-p18 is a symlink to E19/p17 (REPO is four folders above QA, so the mirror must be reached as a
# folder directly under docs/plan/qa/; it is removed again at the end). Every row folder, gen/ and log then lands under
# qa/phase-18/E19/p17/. The row ASSERTS that nothing under qa/phase-17/ changed (git status and a newer-than scan).
# PYTHONDONTWRITEBYTECODE keeps python from writing __pycache__ beside a symlinked script's real file.
#
# What must differ on this build, and is the ONLY failure accepted from a phase 17 driver: its own stamp assertion
# "the installed APK is the gate build" names phase 17's APK id (95b543037345b851); this build is phase 18's.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
PART="${1:-all}"
RC=0

part_a() {
  keep_earlier_run E19a
  jvm_row_begin E19a "phase 17's guard suites on this build through the JVM gate (E19 (a)), with the two scan guards"
  jvm_gate_in UriAccessRulesTest 'app.tileshell.media.UriAccessRulesTest' 'app.tileshell.media.UriAccessRulesTest'
  jvm_gate_in ViewerRulesTest 'app.tileshell.photos.ViewerRulesTest' 'app.tileshell.photos.ViewerRulesTest'
  jvm_gate_in PlayerRulesTest 'app.tileshell.video.PlayerRulesTest' 'app.tileshell.video.PlayerRulesTest'
  jvm_gate_in CaptureOutputGuardTest 'app.tileshell.media.CaptureOutputGuardTest' 'app.tileshell.media.CaptureOutputGuardTest'
  jvm_gate_in UriAccessWiringScanTest 'app.tileshell.media.UriAccessWiringScanTest' 'app.tileshell.media.UriAccessWiringScanTest' \
    "only the port asks the platform who started an activity and who may reach a URI" \
    "Music's play extra is weighed against the uid that sent that intent - the port's for the launch, the intent's own caller on API 35+, nobody below" \
    "a play extra honoured for a caller the platform did not name, or a caller's uid read or compared outside the rule, is caught"
  jvm_gate_in TrustWiringScanTest 'app.tileshell.video.TrustWiringScanTest' 'app.tileshell.video.TrustWiringScanTest' \
    "every read of a QA pref passes through a BuildConfig DEBUG gate" \
    "a QA pref read with the gate left out, constant, or made elsewhere is caught" \
    "a copy and a search are paced by the rule's answer and nothing else" \
    "a pace with a fallback, made by hand, or handed to the walk as a constant is caught"
  for c in UriAccessRulesTest ViewerRulesTest PlayerRulesTest CaptureOutputGuardTest UriAccessWiringScanTest TrustWiringScanTest; do
    record "$c: tests in its report" "$(python3 -c "import glob, sys, xml.etree.ElementTree as ET; print(sum(int(ET.parse(f).getroot().get('tests')) for f in glob.glob(sys.argv[1])))" "$ROW_DIR/$c/test-reports/TEST-*.xml" 2>/dev/null)"
  done
  jvm_row_end || RC=1
}

# ---------------------------------------------------------------------------------------------- (b) the mirror
MIRROR="$P18/E19/p17"
LINK="$(dirname "$P18")/p17-on-p18"
P17="$(dirname "$P18")/phase-17"
mirror_up() {
  local f
  mkdir -p "$MIRROR/scripts"
  for f in "$P17"/scripts/*; do ln -sfn "$f" "$MIRROR/scripts/$(basename "$f")"; done
  ln -sfn "$P17/fixtures" "$MIRROR/fixtures"
  ln -sfn "$P17/dev-camera" "$MIRROR/dev-camera"
  cp "$BASELINE" "$MIRROR/baseline_layout.json"
  ln -sfn "phase-18/E19/p17" "$LINK"
}
mirror_down() { [ -L "$LINK" ] && rm -f "$LINK"; }
p17_state() { { git -C "$REPO" status --porcelain -- docs/plan/qa/phase-17; find "$P17" -newer "$ROW_DIR/.p17-stamp" 2>/dev/null; } | LC_ALL=C sort; }

# run_p17 <driver.sh> <ROW id it writes>: the driver run through the mirror with the device lock released for its span,
# then its own log read: stamped with this build, its driver blob phase 17's committed one, and no FAIL but the stamp's.
run_p17() {
  local drv="$1" id="$2" log k=1 fails other
  if [ -d "$MIRROR/$id" ]; then while [ -e "$MIRROR/$id-run$k" ]; do k=$((k + 1)); done; mv "$MIRROR/$id" "$MIRROR/$id-run$k"; fi
  rings_save
  flock -u 9
  ( cd "$REPO" && PYTHONDONTWRITEBYTECODE=1 bash "$LINK/scripts/$drv" ) > "$ROW_DIR/b-$id.out" 2>&1
  echo $? > "$ROW_DIR/b-$id.rc"
  flock -n 9 || { _verdict FAIL "$drv: the device lock taken back" "another driver holds it"; }
  wake_device >/dev/null
  log="$MIRROR/$id/$id.txt"
  record "$drv: exit code / its summary line" "$(cat "$ROW_DIR/b-$id.rc") / $(grep -E "^$id: [0-9]+ passed" "$log" 2>/dev/null | tail -1)"
  assert_eq "$drv: its log is under qa/phase-18/E19/p17/$id/" "yes" "$([ -s "$log" ] && echo yes || echo no)"
  [ -s "$log" ] || return 1
  assert_contains "$drv: its log is stamped with this build (apk installed)" "apk installed ${GATE_APK_MD5:0:16}" "$(grep -m1 '^apk installed' "$log")"
  assert_eq "$drv: run as it is — the log's driver blob is the committed qa/phase-17/scripts/$drv" \
    "$(git -C "$REPO" rev-parse "HEAD:docs/plan/qa/phase-17/scripts/$drv")" "$(grep -m1 '^driver ' "$log" | awk '{ print $NF }')"
  fails="$(grep -c '^FAIL' "$log")"
  # A FAIL line the driver itself announces as its control and takes back (guard_selftest_video.sh's span B: "(the next
  # FAIL line is the control …") is not a failure of the driver: its own summary does not count it. Left out here, and
  # the driver's own count is asserted below (exactly 1 failed: the stamp).
  other="$(awk '/^\(the next FAIL line is the control/ { skip = 1; next } /^FAIL/ { if (skip) { skip = 0; next } print } ' "$log" | grep -v 'it is the gate build\|the installed APK is the gate build' | head -8)"
  assert_eq "$drv: its own summary counts exactly 1 failed (the gate-build stamp)" "1" "$(grep -E "^$id: [0-9]+ passed" "$log" | tail -1 | sed -n 's/.* passed, \([0-9]*\) failed.*/\1/p')"
  record "$drv: FAIL lines in its log (the gate-build stamp names phase 17's APK id, so one is expected)" "$fails"
  assert_eq "$drv: no assertion fails but its own gate-build stamp (phase 17's APK id)" "" "$(printf '%s' "$other" | cut -c1-600)"
  assert_ne "$drv: assertions passed" "0" "$(grep -c '^PASS' "$log")"
  assert_eq "$drv: no refused-caller assertion fails" "0" "$(grep '^FAIL' "$log" | grep -ciE 'refus|no grant|ignored|not the shell|unsupported scheme')"
  assert_eq "$drv: its gate-build stamp is the one FAIL (it names phase 17's APK id, got this build's)" "1" "$(grep '^FAIL' "$log" | grep -c "gate build.*got \[${GATE_APK_MD5:0:16}\]")"
  record "$drv: refusal assertions that passed (PASS lines naming refused / no grant / ignored / unsupported scheme)" "$(grep '^PASS' "$log" | grep -ciE 'refus|no grant|ignored|not the shell|unsupported scheme')"
}

part_bc() {
  local a t id1 slice
  keep_earlier_run E19
  row_begin E19 "phase 17's drivers re-run as they are on this build with All-files held (E19 (b)); Photos' delete consent recorded (c)"
  RINGS=(launcher "app.tileshell/.camera.CameraDumpService" "app.tileshell/.video.VideoDumpService" "app.tileshell/.photos.PhotosEditDumpService")
  assert_gate_apk "start: the installed APK is the gate candidate"
  assert_contains "start: All-files access is held (appops get)" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
  baseline_start
  adb logcat -c

  # ---- (b)
  : > "$ROW_DIR/.p17-stamp"; sleep 1
  p17_state > "$ROW_DIR/p17-state-before.txt"
  for a in qa-photoview qa-view qa-capture qa-capture-fwd qa-flix; do
    assert_eq "(b) the fixture APK testapps/$a is built (:testapps:$a:assembleDebug --offline)" "yes" \
      "$([ -f "$REPO/testapps/$a/build/outputs/apk/debug/$a-debug.apk" ] && echo yes || echo no)"
  done
  mirror_up
  assert_eq "(b) the mirror resolves: p17-on-p18/scripts/p17.sh is phase 17's file" \
    "$(git -C "$REPO" hash-object "$P17/scripts/p17.sh")" "$(git -C "$REPO" hash-object "$LINK/scripts/p17.sh")"
  run_p17 guard_selftest_video.sh GUARD_SELF
  run_p17 trust_photos.sh TRUST_PHOTOS
  run_p17 trust_video.sh TRUST_VIDEO
  run_p17 e9.sh E9
  mirror_down
  p17_state > "$ROW_DIR/p17-state-after.txt"
  assert_eq "(b) nothing under qa/phase-17/ was written (git status and a newer-than scan, before = after)" "" \
    "$(diff "$ROW_DIR/p17-state-before.txt" "$ROW_DIR/p17-state-after.txt" | head -6)"
  assert_eq "(b) …and the scan found no file newer than the row's stamp there" "0" "$(find "$P17" -newer "$ROW_DIR/.p17-stamp" | grep -c .)"
  assert_eq "(b) the mirror's link under docs/plan/qa/ is removed again" "no" "$([ -e "$LINK" ] && echo yes || echo no)"
  assert_gate_apk "(b) after phase 17's drivers the installed APK is still the gate candidate"
  assert_contains "(b) All-files access is still held" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
  for t in app.tileshell.testclient.qaphotoview app.tileshell.testclient.qaview app.tileshell.testclient.qacapture app.tileshell.testclient.qacapturefwd app.tileshell.testclient.qaflix; do
    adb uninstall "$t" >/dev/null 2>&1
  done
  # (testclient.a and testclient.b are provision.sh's own fixtures and stay.)
  assert_eq "(b) phase 17's fixture apps are uninstalled" "" "$(q 'pm list packages app.tileshell.testclient' | grep -E 'qaphotoview|qaview|qacapture|qaflix' | xargs)"
  baseline_start

  # ---- (c) RECORDED, not asserted
  files_up media || { _verdict FAIL "(c) files_up media" "failed (above)"; }
  id1="$(media_id images qa-photo-1.png DCIM/Camera/)"
  assert_ne "(c) the row's own qa-photo-1.png has an images row (media_up)" "" "$id1"
  rings_save; adb shell am force-stop app.tileshell; sleep 1
  adb shell am start -n app.tileshell/.photos.PhotosActivity >/dev/null 2>&1; sleep 3
  scroll_to_node "$ROW_DIR/c-collection.xml" "photos_item:$id1" 12 >/dev/null
  tap_node "$ROW_DIR/c-collection.xml" "photos_item:$id1"; sleep 2
  D c-viewer; S c-viewer
  assert_eq "(c) Photos' viewer shows the photo with its Delete button" "yes" "$(H c-viewer viewer_delete)"
  M="$(ring_mark)"
  T c-viewer viewer_delete 3
  D c-after-delete; S c-after-delete
  adb shell dumpsys window | tr -d '\r' | grep -E 'mCurrentFocus|mFocusedApp' > "$ROW_DIR/c-window.txt"
  if grep -q 'com.android.providers.media.module' "$ROW_DIR/c-after-delete.xml"; then
    record photos_delete_dialog "shown"
    record "(c) the dialog's buttons (button2 / button1)" "$(X c-after-delete android:id/button2) / $(X c-after-delete android:id/button1)"
    T c-after-delete android:id/button2 2      # Deny: the file is the row's own and leaves through media_down
  else
    record photos_delete_dialog "skipped"
  fi
  record "(c) dumpsys window's focus after the tap" "$(xargs < "$ROW_DIR/c-window.txt")"
  slice="$(ring_since "$M")"
  record "(c) Photos' delete lines in the slice" "$(printf '%s\n' "$slice" | grep -F '[photosapp] delete' | sed 's/.*\[photosapp\] //; s/ *wall=.*//' | tr '\n' '|')"
  record "(c) the images row after the step (content query)" "$(q "content query --uri content://media/external/images/media --projection _id:_display_name --where _id=$id1" | xargs)"
  record "(c) the file after the step (ls)" "$(q 'ls /sdcard/DCIM/Camera/qa-photo-1.png 2>&1' | xargs)"
  assert_eq "(c) no crash of the shell (AndroidRuntime)" "0" "$(crash)"
  c6; ensure_start
  files_down

  # ---- the device as the row found it
  assert_gate_apk "end: the installed APK is the gate candidate"
  assert_contains "end: All-files access is held" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
  ensure_start
  row_end || RC=1
}

case "$PART" in
  a) part_a ;;
  bc) part_bc ;;
  all) part_a; part_bc ;;
  *) echo "e19.sh [a|bc]" >&2; exit 2 ;;
esac
exit $RC
