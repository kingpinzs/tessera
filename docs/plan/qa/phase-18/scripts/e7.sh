#!/usr/bin/env bash
# Phase 18 E7: share.
#
#   two     select two files → Share → the chooser resumed (top_activity = com.android.intentresolver); the slice from
#           a MARK before the tap holds `[files] share 2 files type=<mime> uris=<list>` with two
#           content://app.tileshell.files/ URIs; choosing testapps/qa-capture's send probe (SEND_MULTIPLE */*) → the
#           probe logs both URIs and the md5 of each stream it read, equal to the fixtures' recorded md5s (r3 V2)
#   volume  the same on the row's own public volume (a file under /storage/<UUID>): the line with ONE content:// URI
#           and the probe's md5 equal
#   scope   T18-11's negative, through the floor's JVM gate: `--tests '*FileShareGuard*'` — gradle.rc = 0 AND
#           TEST-*FileShareGuard*.xml showing the five named cases with 0 failures / errors / skipped (r3 V15); and the
#           same five cases against the provider's openFile / query check (FilesProviderRulesTest, r3 D10)
#
# The probe is built with `./gradlew :testapps:qa-capture:assembleDebug --offline` (never the app's own APK), installed
# for the row and uninstalled at its end.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"

keep_earlier_run E7
row_begin E7 "share: two files to the chooser and the send probe, a file on the public volume, the scope negative on the JVM"
assert_gate_apk
baseline_start
adb logcat -c

( cd "$REPO" && ./gradlew ":testapps:qa-capture:assembleDebug" --offline ) > "$ROW_DIR/probe-build.out" 2>&1; echo $? > "$ROW_DIR/probe-build.rc"
assert_eq "the probe builds (:testapps:qa-capture:assembleDebug --offline, exit code)" "0" "$(cat "$ROW_DIR/probe-build.rc")"
assert_eq "…and the app's own APK file is untouched by that build (md5)" "$GATE_APK_MD5" "$(md5sum "$APK" | cut -d' ' -f1)"
adb install -r "$QAC_APK" > "$ROW_DIR/probe-install.out" 2>&1; echo $? > "$ROW_DIR/probe-install.rc"
assert_eq "the probe is installed (adb install -r, exit code)" "0" "$(cat "$ROW_DIR/probe-install.rc")"

files_up || { adb uninstall "$QAC" >/dev/null 2>&1; row_end; exit 1; }
pubvol_up >/dev/null || { files_down; adb uninstall "$QAC" >/dev/null 2>&1; row_end; exit 1; }
sleep 2
V="$PUBVOL_PATH"
MD5_A="$(fx_md5 a.txt)"; MD5_B="$(fx_md5 b.bin)"
ensure_start

# Tap the probe's target in the chooser (scrolling the sheet up once if it is below the fold).
pick_probe() { # dump-name
  local xy
  D "$1"; S "$1"
  xy="$(chooser_xy "$ROW_DIR/$1.xml" "QA Capture")"
  local i
  for i in 1 2 3; do
    [ -n "$xy" ] && break
    adb shell input swipe 540 1900 540 900 400; sleep 1.2; D "$1"; xy="$(chooser_xy "$ROW_DIR/$1.xml" "QA Capture")"; note "the chooser was swiped up ($i) to reach QA Capture"
  done
  record "$1: the chooser's texts" "$(texts "$1" | cut -c1-240)"
  assert_ne "$1: the chooser lists QA Capture (the send probe)" "" "$xy"
  # shellcheck disable=SC2086
  [ -n "$xy" ] && adb shell input tap $xy
  sleep 4
}

# ------------------------------------------------------------------------------------------------ two files
log "--- two files → Share → the chooser → the send probe"
files_at "$QF"; D 01; T 01 files_bar:select 1; D 02; T 02 files_row:a.txt 0.6; D 03; T 03 files_row:b.bin 0.6; D 04
assert_eq "two files are selected, and Share is live" "2 items selected true" "$(X 04 files_sort) $(EN 04 files_sel:share)"
adb logcat -c; M="$(ring_mark)"
T 04 files_sel:share 3
assert_eq "the chooser is resumed (top_activity's package)" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
SL="$(ring_since "$M")"
SHARE="$(printf '%s\n' "$SL" | grep -o '\[files\] share 2 files type=[^ ]* uris=[^ ]*' | head -1)"
record "the share line" "$SHARE"
assert_contains "[files] share 2 files type=<mime> uris=<list>" "[files] share 2 files type=" "$SHARE"
URIS="$(printf '%s' "$SHARE" | sed -n 's/.* uris=//p')"
assert_eq "its list holds exactly two URIs, both content://app.tileshell.files/…" "2 2" \
  "$(printf '%s' "$URIS" | tr ',' '\n' | grep -c .) $(printf '%s' "$URIS" | tr ',' '\n' | grep -c '^content://app\.tileshell\.files/')"
assert_eq "…and they are a.txt's and b.bin's" "content://app.tileshell.files/root$QF/a.txt,content://app.tileshell.files/root$QF/b.bin" "$URIS"
pick_probe 05-chooser
probe_lines > "$ROW_DIR/06-probe.txt"; sed 's/^/      /' "$ROW_DIR/06-probe.txt" >> "$LOG"
assert_contains "the probe: SEND_MULTIPLE with 2 streams" "send streams=2 action=android.intent.action.SEND_MULTIPLE" "$(cat "$ROW_DIR/06-probe.txt")"
assert_contains "the probe: a.txt's URI, and the md5 of the stream it read = the fixture's" "uri=content://app.tileshell.files/root$QF/a.txt md5=$MD5_A" "$(cat "$ROW_DIR/06-probe.txt")"
assert_contains "the probe: b.bin's URI, and the md5 of the stream it read = the fixture's" "uri=content://app.tileshell.files/root$QF/b.bin md5=$MD5_B" "$(cat "$ROW_DIR/06-probe.txt")"
assert_eq "the probe: no stream was unreadable" "0" "$(grep -c 'md5=unreadable' "$ROW_DIR/06-probe.txt")"
adb shell am force-stop "$QAC"; ensure_start

# ------------------------------------------------------------------------------------------------ the public volume
log "--- a file on the row's own public volume"
adb shell "cp /sdcard/QA-Files/b.bin $V/pv-share.bin"; sleep 1
assert_eq "the file is on the volume (md5)" "$MD5_B" "$(md5dev "$V/pv-share.bin")"
files_at "$V"; scroll_to_node "$ROW_DIR/10.xml" files_row:pv-share.bin >/dev/null; hold 10 files_row:pv-share.bin; D 11
adb logcat -c; M="$(ring_mark)"
T 11 files_hold:share 3
assert_eq "volume: the chooser is resumed" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
SL="$(ring_since "$M")"
SHARE="$(printf '%s\n' "$SL" | grep -o '\[files\] share 1 files type=[^ ]* uris=[^ ]*' | head -1)"
record "volume: the share line" "$SHARE"
assert_eq "volume: [files] share 1 files … with ONE content:// URI, under /storage/$PUBVOL_UUID" "content://app.tileshell.files/root$V/pv-share.bin" "$(printf '%s' "$SHARE" | sed -n 's/.* uris=//p')"
pick_probe 12-chooser
probe_lines > "$ROW_DIR/13-probe.txt"; sed 's/^/      /' "$ROW_DIR/13-probe.txt" >> "$LOG"
assert_contains "volume: the probe read the stream, md5 = the file's" "uri=content://app.tileshell.files/root$V/pv-share.bin md5=$MD5_B" "$(cat "$ROW_DIR/13-probe.txt")"
adb shell am force-stop "$QAC"; ensure_start
assert_eq "no crash of the shell (AndroidRuntime)" "0" "$(crash)"

# ------------------------------------------------------------------------------------------------ restore the device
adb shell input keyevent KEYCODE_HOME; sleep 1
pubvol_down
files_down
adb uninstall "$QAC" > "$ROW_DIR/probe-uninstall.out" 2>&1
assert_eq "the probe is uninstalled" "" "$(q "pm path $QAC")"
ensure_start

# ------------------------------------------------------------------------------------------------ the scope negative (JVM)
log "--- T18-11's scope negative on the JVM (the floor's gate)"
jvm_gate '*FileShareGuard*' '*FileShareGuard*' \
  "the app's own filesDir file is refused" \
  "a dot-dot traversal out of shared storage is refused" \
  "a symlink whose canonical form is private is refused" \
  "a primary-volume path is allowed" \
  "a storage UUID path on a removable volume is allowed"
assert_eq "FileShareGuardTest reads the diagnostics it wrote ([files] share refused: outside shared storage, in the test's source)" "yes" \
  "$(grep -q 'share refused: outside shared storage' "$REPO/app/src/test/kotlin/app/tileshell/files/FileShareGuardTest.kt" && echo yes || echo no)"
jvm_gate_in provider '*FilesProviderRules*' '*FilesProviderRules*' \
  "the app's own filesDir file is refused" \
  "a dot-dot traversal out of shared storage is refused" \
  "a symlink whose canonical form is private is refused" \
  "a primary-volume path is served" \
  "a storage UUID path on a removable volume is served"
assert_eq "the JVM gates left the app's APK file untouched (md5)" "$GATE_APK_MD5" "$(md5sum "$APK" | cut -d' ' -f1)"
assert_gate_apk "end: the installed APK is the gate candidate"
row_end
