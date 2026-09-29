#!/usr/bin/env bash
# Phase 12 E9 — regression, the regress.sh pattern: on a freshly provisioned AVD, one row per earlier phase, run
# unchanged; each passes; no dump any of them saved contains wizard_page; no assignSlotOnce ... -> assigned line; and the
# exported components are still exactly phase 03's allow-list.
#
# Unchanged drivers exist for phase 02 E1 (takes its own out dir), phase 03 E1 and E5 and phase 05 E1 (lib.sh rows that
# write into their own phase's row directory: that directory is moved aside first and put back after, so the earlier
# phases' evidence is left exactly as it was, and the new run is kept here). Phase 01 E2, phase 01 E14 and phase 10 E2 were
# run inside combined scripts with no standalone driver, so their row text is replayed here, verbatim in what it checks.
# The children each take the device lock themselves, so this row asserts only after they have run.
HERE="$(cd "$(dirname "$0")" && pwd)"
. "$HERE/lib.sh"; . "$HERE/p12.sh"
OUT="$QA/E9"
mkdir -p "$OUT"
QAR="$QAROOT"

# ---- 0. a fresh provisioned AVD (before any row lock is taken)
leave_home
adb shell pm clear "$PKG" >/dev/null
bash "$PROVISION" > "$OUT/provision-start.txt" 2>&1; echo $? > "$OUT/provision-start.rc"
MARK0="$(ring_mark)"

# ---- 1. unchanged drivers
# Each child starts on a clean Start: the one before it may leave a window up (run 1: phase 03 E5 left Tess's session
# open, and phase 05 E1 then read her window instead of the Setup checklist).
between() {
  adb shell input keyevent KEYCODE_BACK; sleep 0.5; adb shell input keyevent KEYCODE_BACK; sleep 0.5
  adb shell input keyevent KEYCODE_HOME; sleep 2
}
run_lib_row() { # phase row script
  local phase="$1" row="$2" script="$3" dir="$QAR/$1/$2" keep="$QAR/$1/$2.p12-e9-aside"
  [ -e "$dir" ] && mv "$dir" "$keep"
  between
  local m; m="$(ring_mark)"
  bash "$QAR/$phase/scripts/$script" > "$OUT/$phase-$row.out" 2>&1; echo $? > "$OUT/$phase-$row.rc"
  ring_since "$m" > "$OUT/$phase-$row.ring.txt" 2>/dev/null
  rm -rf "$OUT/$phase-$row"; [ -e "$dir" ] && mv "$dir" "$OUT/$phase-$row"
  [ -e "$keep" ] && mv "$keep" "$dir"
}
between
m="$(ring_mark)"
bash "$QAR/phase-02/scripts/e1.sh" "$OUT/phase-02-E1" > "$OUT/phase-02-E1.out" 2>&1; echo $? > "$OUT/phase-02-E1.rc"
ring_since "$m" > "$OUT/phase-02-E1.ring.txt" 2>/dev/null
run_lib_row phase-03 E1 e1.sh
run_lib_row phase-03 E5 e5.sh
run_lib_row phase-05 E1 e1.sh

between

# ---- 2. the row itself
row_begin E9 "regression: one row per earlier phase, unchanged"
assert_eq "fresh provision rc" "0" "$(cat "$OUT/provision-start.rc")"
for r in phase-02-E1 phase-03-E1 phase-03-E5 phase-05-E1; do
  assert_eq "$r passes unchanged (rc)" "0" "$(cat "$OUT/$r.rc")"
done
for r in phase-03-E1 phase-03-E5 phase-05-E1; do
  note "$r summary: $(grep -E '^E[0-9]+: [0-9]+ passed' "$OUT/$r/"*.txt 2>/dev/null | tail -1)"
done
note "phase-02-E1 tail: $(tail -3 "$OUT/phase-02-E1.out" | tr '\n' ' ')"

log "phase 01 E2: the HOME role, and Home shows Start"
assert_eq "phase 01 E2: HOME role holder" "$PKG" "$(adb shell cmd role get-role-holders android.app.role.HOME | tr -d '\r')"
adb shell input keyevent KEYCODE_HOME; sleep 3
screencap "$ROW_DIR/p01-e2-start.png"
dump_ui "$ROW_DIR/p01-e2-start.xml"
assert_eq "phase 01 E2: Home shows Start" "yes" "$(has_node "$ROW_DIR/p01-e2-start.xml" start_page)"

log "phase 01 E14: disallow_listener flips the checklist row; allow flips it back (on the moved row list)"
open_checklist
dump_ui "$ROW_DIR/p01-e14-granted.xml"
assert_eq "phase 01 E14: notifications granted before" "yes" "$(has_node "$ROW_DIR/p01-e14-granted.xml" checklist:notifications:granted)"
adb shell cmd notification disallow_listener "$LISTENER"; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 1
open_checklist
dump_ui "$ROW_DIR/p01-e14-revoked.xml"
assert_eq "phase 01 E14: revoke flips it to missing" "yes" "$(has_node "$ROW_DIR/p01-e14-revoked.xml" checklist:notifications:missing)"
adb shell cmd notification allow_listener "$LISTENER"; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 1
open_checklist
dump_ui "$ROW_DIR/p01-e14-regranted.xml"
assert_eq "phase 01 E14: re-allow flips it back" "yes" "$(has_node "$ROW_DIR/p01-e14-regranted.xml" checklist:notifications:granted)"
adb shell input keyevent KEYCODE_HOME; sleep 2

log "phase 10 E2: Music in the app list, its hold menu offers Pin to Start and not Uninstall"
adb shell input swipe 900 1200 150 1200 250; sleep 2
music=""
for i in 1 2 3 4 5 6; do
  dump_ui "$ROW_DIR/p10-e2-applist-$i.xml"
  music="$(python3 - "$ROW_DIR/p10-e2-applist-$i.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    rid = n.get("resource-id", "")
    if rid in ("applist_name:app.tileshell/.music.MusicActivity", "applist_name:app.tileshell/app.tileshell.music.MusicActivity"):
        print(rid, n.get("bounds")); break
PY
)"
  [ -n "$music" ] && break
  adb shell input swipe 540 1700 540 900 300; sleep 1
done
note "Music entry: $music"
assert_ne "phase 10 E2: Music is in the app list" "" "$music"
if [ -n "$music" ]; then
  set -- $(echo "$music" | awk '{print $2}' | grep -oE '[0-9]+')
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 900; sleep 2
  dump_ui "$ROW_DIR/p10-e2-menu.xml"
  screencap "$ROW_DIR/p10-e2-menu.png"
  menu="$(python3 - "$ROW_DIR/p10-e2-menu.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
print("|".join(n.get("text", "") for n in root.iter("node") if n.get("text")))
PY
)"
  note "hold menu texts: $menu"
  assert_contains "phase 10 E2: the hold menu offers Pin to Start" "Pin to Start" "$menu"
  assert_absent "phase 10 E2: and not Uninstall" "Uninstall" "$menu"
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi
adb shell input keyevent KEYCODE_HOME; sleep 2

log "no dump any of them saved contains wizard_page"
hits="$(grep -rl 'resource-id="wizard_page"' "$OUT" --include='*.xml' 2>/dev/null | wc -l)"
note "xml files scanned: $(find "$OUT" -name '*.xml' | wc -l)"
assert_eq "no saved dump holds wizard_page" "0" "$hits"
assert_ne "the scan saw the children's dumps" "0" "$(find "$OUT" -name '*.xml' | wc -l)"

log "no assignSlotOnce ... -> assigned line while they ran (C-3)"
ring_since "$MARK0" > "$ROW_DIR/ring-all.txt" 2>/dev/null
assert_eq "zero assignSlotOnce -> assigned lines" "0" "$(grep -c 'assignSlotOnce.*-> assigned' "$ROW_DIR/ring-all.txt")"
assert_eq "no [wizard] shown line during the regression" "0" "$(cat "$OUT"/*.ring.txt "$ROW_DIR/ring-all.txt" | grep -c '\[wizard\] shown')"

log "exported components still phase 03's allow-list"
python3 "$QAR/phase-03/scripts/exported.py" "$APK" "$QAR/phase-03/exported-allowlist.txt" > "$ROW_DIR/exported.txt" 2>&1
echo $? > "$ROW_DIR/exported.rc"
assert_eq "exported.py: no new exported component" "0" "$(cat "$ROW_DIR/exported.rc")"

log "restore: pm clear -> provision.sh -> Home"
restore_fresh end
assert_eq "restore provision.sh rc" "0" "$(cat "$ROW_DIR/provision-end.rc")"
row_end
