#!/usr/bin/env bash
# Phase 18 build task 12: the driver floor (scripts/p18.sh), PROVED on the emulator before any row uses it. A real row
# (folder FLOOR): every claim is an assertion that can fail, and each helper whose job is to FAIL is shown failing —
# in a subshell whose verdicts go to a side file (expect-fail-<n>.txt), so the row's own count holds only what the floor
# did right.
#
#   floor_selftest.sh               every leg below
#   floor_selftest.sh <leg>…        only the named legs (the begin / end state checks always run)
#
#   helpers    ensure_start, top_activity, gdump, c6, absent_in (passes on a readable slice, FAILS on an empty one)
#   pubvol     pubvol_up mounts and prints a UUID; the shell writes /storage/<UUID>; pubvol_down leaves sm list-disks
#              empty and is safe to call again; a child that exits with its volume up gives it back through the EXIT
#              trap, and the trap the child had set itself still runs
#   files      files_up media: every fixture's size and date against the table (stat on the device), the md5s, the
#              table's readers and orders; files_down restores both snapshots exactly
#   dirty      an extra file left on /sdcard makes files_down FAIL (both snapshots); cleaned, it passes
#   big        files_up big (200 MB, timed) then files_down
#   tenk       files_up tenk (10,000 files, 10,000 dates, timed) then files_down (timed)
#   paced      the pacing pref written, read back from the prefs file through run-as, cleared
#   mid        mid_progress FAILS on the live ring (no Files UI writes a progress line yet) and on canned slices with
#              only 0/total and total/total; it passes on a canned slice with a line in between
#   baseline   baseline_start on baseline_layout.json: a Files tile on Start, zero `assignSlotOnce … -> assigned`
#              lines; then the layout that was there is put back
#   probe      testapps/qa-capture's send probe: SEND with one MediaStore URI (media_up's, read through a URI grant),
#              SEND_MULTIPLE with two streams — each URI logged with the md5 of its stream
#   zips       files_up zips: make_zips.py's output pushed, each with the host's md5 and its own date; files_down
#   jvm        jvm_gate on a test class that exists (gradle.rc, the TEST-*.xml report, a named case), and it FAILS on
#              a case name the report does not hold
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
LEGS="${*:-helpers pubvol files dirty big tenk zips paced mid baseline probe jvm}"
leg() { case " $LEGS " in *" $1 "*) log "--- leg: $1 ($(date '+%H:%M:%S')) ---"; return 0 ;; *) return 1 ;; esac; }

keep_earlier_run FLOOR
row_begin FLOOR "the phase 18 driver floor (p18.sh), proved on the emulator — legs: $LEGS"
D="$ROW_DIR"

# A helper that must FAIL: run in a subshell whose verdicts go to a side file. PASS here only when that file holds a
# FAIL line carrying the needle; the subshell's exit code is written beside it (read from the file, never a pipe).
EXPECT_N=0
expect_fail() { # name needle command…
  local name="$1" needle="$2" side
  shift 2
  EXPECT_N=$((EXPECT_N + 1))
  side="$D/expect-fail-$EXPECT_N.txt"
  : > "$side"
  ( LOG="$side"; PASS=0; FAIL=0; "$@" >/dev/null 2>&1; echo $? > "$side.rc" )
  if grep '^FAIL' "$side" | grep -qF -- "$needle"; then
    _verdict PASS "$name" "it failed, as it must: $(grep '^FAIL' "$side" | grep -F -- "$needle" | head -1 | tr -s ' ' | cut -c1-150) (expect-fail-$EXPECT_N.txt, rc $(cat "$side.rc"))"
  else
    _verdict FAIL "$name" "no FAIL line holding [$needle] — the helper cannot fail: $(tr '\n' ';' < "$side" | cut -c1-200)"
  fi
}

# ------------------------------------------------------------------------------------------- the state at the start
layout_save "$D/layout-at-begin.json"
_snap_sdcard > "$D/device-sdcard-at-begin.txt"
_snap_files > "$D/device-files-at-begin.txt"
APK_AT_BEGIN="$(installed_apk_id)"
APPOP_AT_BEGIN="$(q "appops get app.tileshell MANAGE_EXTERNAL_STORAGE" | cut -d';' -f1)"
QAC_AT_BEGIN="$(q "pm path $QAC")"
PREFS_AT_BEGIN="$(adb shell run-as app.tileshell ls shared_prefs/start_theme.xml >/dev/null 2>&1 && echo present || echo absent)"
record "at the start: the installed shell" "$APK_AT_BEGIN"
record "at the start: appop MANAGE_EXTERNAL_STORAGE" "$APPOP_AT_BEGIN"
record "at the start: sm list-disks / public volumes" "[$(q 'sm list-disks' | xargs)] / [$(q 'sm list-volumes public' | xargs)]"
record "at the start: qa-capture installed" "${QAC_AT_BEGIN:-no}"
record "at the start: start_theme.xml" "$PREFS_AT_BEGIN"
record "at the start: the host's free disk on /" "$(df -h --output=avail / | tail -1 | xargs)"
ensure_start
assert_eq "at the start: Start is on top" "app.tileshell/.StartActivity" "$(top_activity)"
assert_eq "at the start: no QA-Files and no QA-Big" "" "$(q "ls -d $QA_FILES $QA_BIG 2>/dev/null" | xargs)"

# ------------------------------------------------------------------------------------------------------- helpers
if leg helpers; then
  gdump "$D/gdump-start.xml"
  assert_eq "gdump: Start dumped through the gesture driver holds start_page" "yes" "$(has_node "$D/gdump-start.xml" start_page)"
  assert_eq "gdump: its scratch file is gone from /sdcard/Download" "" "$(q "ls /sdcard/Download | grep '^p18_'" | xargs)"
  MARK="$(ring_mark)"
  c6
  ensure_start
  assert_eq "c6 then ensure_start: Start is on top" "app.tileshell/.StartActivity" "$(top_activity)"
  SLICE="$(ring_since "$MARK")"
  assert_contains "c6: the restarted shell's ring is readable from the MARK" "wall=" "$SLICE"
  absent_in "absent_in: passes on a readable slice without the needle" "p18-no-such-line" "$SLICE"
  expect_fail "absent_in: FAILS on an empty slice" "empty or unreadable" absent_in "an absence on nothing" "p18-no-such-line" ""
  expect_fail "absent_in: FAILS when the needle is there" "should NOT contain" absent_in "an absence that is false" "wall=" "$SLICE"
  assert_contains "c6 saved the launcher's ring into the row" "ring-launcher.txt" "$(ls "$D")"
fi

# -------------------------------------------------------------------------------------------------------- pubvol
if leg pubvol; then
  trap 'echo "(the self-test row'"'"'s own EXIT trap ran)"' EXIT
  pubvol_up > "$D/pubvol-up.out"
  cat "$D/pubvol-up.out" >> "$LOG"
  assert_eq "pubvol_up: its last line is the UUID and the volume id" "$PUBVOL_UUID $PUBVOL_ID" "$(tail -1 "$D/pubvol-up.out")"
  assert_eq "pubvol_up: the UUID has the vfat form XXXX-XXXX" "yes" "$([[ "$PUBVOL_UUID" =~ ^[0-9A-F]{4}-[0-9A-F]{4}$ ]] && echo yes || echo "no: [$PUBVOL_UUID]")"
  assert_eq "pubvol_up: the id has the form public:<x>,<y>" "yes" "$([[ "$PUBVOL_ID" =~ ^public:[0-9]+,[0-9]+$ ]] && echo yes || echo "no: [$PUBVOL_ID]")"
  assert_contains "pubvol_up: sm list-volumes shows it mounted with that UUID" "$PUBVOL_ID mounted $PUBVOL_UUID" "$(q 'sm list-volumes public')"
  record "the volume's filesystem and size (df)" "$(q "mount | grep -m1 '$PUBVOL_UUID'" | awk '{ print $5 }') / $(q "df -k $PUBVOL_PATH" | awk 'NR == 2 { print $2 " KB" }')"
  adb shell "printf p18-pubvol > $PUBVOL_PATH/p18-probe.txt"
  assert_eq "the shell can write $PUBVOL_PATH and read it back" "p18-pubvol" "$(q "cat $PUBVOL_PATH/p18-probe.txt")"
  assert_contains "pubvol_up put its safety net in the EXIT trap…" "_pubvol_exit" "$(trap -p EXIT)"
  assert_contains "…in front of the trap the row had set, which is kept" "own EXIT trap ran" "$(trap -p EXIT)"
  pubvol_down
  assert_eq "pubvol_down cleared the exported names" "" "$PUBVOL_UUID$PUBVOL_ID$PUBVOL_UP"
  pubvol_down   # safe to call when none is up
  # A row that dies with its volume up: a child process sets its own trap, calls pubvol_up and exits 7.
  cat > "$D/trap-child.sh" <<'CHILD'
. "$1/lib.sh"; . "$1/p18.sh"
ROW=TRAPCHILD; ROW_DIR="$2"; LOG="$2/trap-child.log"; : > "$LOG"
trap 'echo THE_CHILDS_OWN_TRAP_RAN' EXIT
pubvol_up
echo "child: the volume is up ($PUBVOL_UUID); exiting 7 without pubvol_down"
exit 7
CHILD
  bash "$D/trap-child.sh" "$HERE" "$D" > "$D/trap-child.out" 2>&1
  echo $? > "$D/trap-child.rc"
  assert_eq "the child exited 7 with its volume up" "7" "$(cat "$D/trap-child.rc")"
  assert_contains "the child did mount a volume" "child: the volume is up (" "$(cat "$D/trap-child.out")"
  assert_contains "the EXIT trap removed the volume" "removing it (EXIT trap)" "$(cat "$D/trap-child.out")"
  assert_contains "the child's own trap still ran (not clobbered)" "THE_CHILDS_OWN_TRAP_RAN" "$(cat "$D/trap-child.out")"
  assert_eq "after the child: sm list-disks is empty" "" "$(q 'sm list-disks' | xargs)"
  assert_eq "after the child: no public volume" "" "$(q 'sm list-volumes public' | xargs)"
fi

# --------------------------------------------------------------------------------------------------------- files
if leg files; then
  files_up media
  # Each fixture against the table, one assertion each: name|bytes|date-to-the-minute from stat on the device.
  while IFS='|' read -r name bytes date detail how; do
    n="${name%/}"
    got="$(grep -F "$n|" "$D/fixtures-got.txt" | awk -F'|' -v n="$n" '$1 == n')"
    if [ "$date" = now ]; then
      assert_eq "fixture $n: $bytes bytes" "$n|$bytes" "$(printf '%s' "$got" | cut -d'|' -f1,2)"
      assert_eq "fixture $n: detail string with the device's date" "4.81 KB $(q "date +%-m/%-d/%Y")" "$(fx_detail "$name")"
    else
      assert_eq "fixture $n: ${bytes} bytes, $date" "$n|$bytes|$date" "$got"
    fi
  done < <(_fx_rows)
  media_scan
  fx_stat > "$D/fixtures-after-scan.txt"
  assert_eq "a second MediaStore scan moves no fixture's size or date (diff of the two stat listings)" "" "$(diff "$D/fixtures-got.txt" "$D/fixtures-after-scan.txt" | head -4 | xargs)"
  assert_eq "no two fixtures share a size" "0" "$(_fx_rows | awk -F'|' '$2 != "-" { print $2 }' | sort | uniq -d | grep -c .)"
  assert_eq "no two fixtures share a date" "0" "$(_fx_rows | cut -d'|' -f3 | sort | uniq -d | grep -c .)"
  assert_eq "big.bin is not in the table's rows without the big argument" "" "$(fx_names | grep -x big.bin)"
  assert_eq "md5s: one per file fixture, 32 hex digits each" "$(_fx_rows | awk -F'|' '$5 != "dir"' | grep -c .)" "$(grep -cE '^[0-9a-f]{32} ' "$D/fixtures.md5")"
  assert_eq "md5 of a.txt = md5 of the 10 bytes 0123456789" "$(printf 0123456789 | md5sum | cut -d' ' -f1)" "$(fx_md5 a.txt)"
  assert_eq "md5 of b.bin = md5 of 307,200 zero bytes" "$(head -c 307200 /dev/zero | md5sum | cut -d' ' -f1)" "$(fx_md5 b.bin)"
  assert_eq "fx_detail b.bin (the doc's own example)" "300 KB 1/2/2026" "$(fx_detail b.bin)"
  assert_eq "fx_detail sub/ (a folder: the date only)" "1/3/2026" "$(fx_detail sub/)"
  assert_eq "fx_order name (folders first, A → Z ignoring case)" "hidden recent sub 03.mp3 a.txt b.bin img-0.png img-1.png img-2.png img-3.png img-4.png img-5.png qa-steps.mp4" "$(fx_order name | xargs)"
  assert_eq "fx_order date (folders first, newest first)" "recent hidden sub 03.mp3 qa-steps.mp4 img-0.png img-2.png img-4.png img-5.png img-3.png img-1.png b.bin a.txt" "$(fx_order date | xargs)"
  assert_eq "fx_order size (folders by name, largest first)" "hidden recent sub 03.mp3 b.bin qa-steps.mp4 img-5.png img-4.png img-3.png img-2.png img-1.png img-0.png a.txt" "$(fx_order size | xargs)"
  assert_eq "fx_order name with hidden files" ".hidden.txt" "$(fx_order name "" hidden | sed -n 4p)"
  assert_eq "fx_order name recent/" "never.png r1.png r2.png r3.png" "$(fx_order name recent/ | xargs)"
  assert_eq "hidden/ is a .nomedia folder: MediaStore's audio collection holds no qa-hidden.mp3" "0" "$(q "content query --uri content://media/external/audio/media --projection _data" | grep -c 'qa-hidden.mp3')"
  assert_ne "media: qa-photo-0.png reached DCIM/Camera through media_up and has an images row" "" "$(media_id images qa-photo-0.png DCIM/Camera/)"
  assert_ne "media: qa-steps.mp4 reached Movies through media_up and has a video row" "" "$(media_id video qa-steps.mp4 Movies/)"
  files_down
  assert_eq "after files_down: QA-Files and the media_up files are gone" "" "$(q "ls -d $QA_FILES /sdcard/DCIM/Camera/qa-photo-0.png /sdcard/Movies/qa-steps.mp4 2>/dev/null" | xargs)"
fi

# --------------------------------------------------------------------------------------------------------- dirty
if leg dirty; then
  files_up
  adb shell ": > /sdcard/p18-dirty.txt"
  sleep 3   # FUSE gives the file its files-collection row (BUILDSTART: fuse_row_on_create)
  expect_fail "files_down FAILS on a dirty volume: the shared-storage snapshot" "files_down: the sdcard snapshot" files_down
  assert_contains "…and its diff names the extra file" "> /sdcard/p18-dirty.txt" "$(cat "$D/snap-sdcard.diff")"
  cp "$D/snap-sdcard.diff" "$D/dirty-snap-sdcard.diff"; cp "$D/snap-files.diff" "$D/dirty-snap-files.diff"
  assert_contains "…and the files-collection snapshot failed too, on the same file" "p18-dirty.txt" "$(grep '^FAIL' "$D/expect-fail-$EXPECT_N.txt" | grep 'the files snapshot')"
  adb shell rm -f /sdcard/p18-dirty.txt
  files_down   # the same snapshots, now clean: the two PASS lines
fi

# ----------------------------------------------------------------------------------------------------------- big
if leg big; then
  HOST_GB="$(df -BG --output=avail / | tail -1 | tr -dc '0-9')"
  record "before big: the host's free disk on / (GB)" "$HOST_GB"
  if [ "$HOST_GB" -lt 40 ]; then
    _verdict FAIL "big: the host has at least 40 GB free" "$HOST_GB GB — not making a large fixture"
  else
    files_up big
    assert_eq "big.bin is 209,715,200 bytes with the table's date" "big.bin|209715200|2026-01-22 12:00" "$(grep '^big.bin|' "$D/fixtures-got.txt")"
    assert_eq "fx_detail big.bin" "200 MB 1/22/2026" "$(fx_detail big.bin)"
    T0="$(date +%s.%N)"
    files_down
    record "files_down after big, seconds" "$(python3 -c "print('%.1f' % ($(date +%s.%N) - $T0))")"
  fi
fi

# ---------------------------------------------------------------------------------------------------------- tenk
if leg tenk; then
  files_up tenk
  T0="$(date +%s.%N)"
  files_down
  record "files_down after tenk (10,000 files and their MediaStore rows gone), seconds" "$(python3 -c "print('%.1f' % ($(date +%s.%N) - $T0))")"
  assert_eq "after files_down: QA-Big is gone" "" "$(q "ls -d $QA_BIG 2>/dev/null")"
fi

# ---------------------------------------------------------------------------------------------------------- zips
if leg zips; then
  files_up zips
  assert_eq "zips: make_zips.py's zips are in QA-Files/zips, as many as the host made" "$(ls "$FXGEN/zips"/*.zip | grep -c .)" "$(q "ls $QA_FILES/zips" | grep -c '\.zip$')"
  assert_eq "zips: no two share a date" "0" "$(cut -f3 "$D/zips.tsv" | sort | uniq -d | grep -c .)"
  record "zips: the files (zips.tsv)" "$(cut -f1 "$D/zips.tsv" | xargs)"
  files_down
fi

# --------------------------------------------------------------------------------------------------------- paced
if leg paced; then
  files_up paced
  assert_eq "paced: pace_now reads 8388608" "8388608" "$(pace_now)"
  assert_contains "paced: the prefs file itself, read through run-as, holds the string" "<string name=\"qa_files_rate_bps\">8388608</string>" "$(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml | tr -d '\r')"
  assert_eq "paced: Start is on top after the write (the shell restarted on the file)" "app.tileshell/.StartActivity" "$(top_activity)"
  files_down
  assert_eq "paced: the pref is gone after files_down" "" "$(pace_now)"
  assert_eq "paced: start_theme.xml is as it was before ($PREFS_AT_BEGIN)" "$PREFS_AT_BEGIN" "$(adb shell run-as app.tileshell ls shared_prefs/start_theme.xml >/dev/null 2>&1 && echo present || echo absent)"
fi

# ----------------------------------------------------------------------------------------------------------- mid
if leg mid; then
  MARK="$(ring_mark)"
  expect_fail "mid_progress FAILS on the live ring (nothing writes a progress line yet)" "none in the slice" mid_progress copy "$MARK" 3
  assert_ne "…and returns non-zero" "0" "$(cat "$D/expect-fail-$EXPECT_N.txt.rc")"
  # The parser, on canned slices in the ring's own form (ring_since replaced inside the subshell only).
  canned() { # file op -> the subshell's rc in <file>.rc, mid_progress's verdicts in <file>.log
    ( LOG="$1.log"; : > "$LOG"; PASS=0; FAIL=0; ring_since() { cat "$CANNED"; }; CANNED="$1"; mid_progress "$2" 0 1 > "$1.out" 2>&1; echo $? > "$1.rc" )
  }
  printf '%s\n' "wall=1 [files] copy progress 0/209715200" "wall=2 [files] copy progress 209715200/209715200" > "$D/canned-ends.txt"
  canned "$D/canned-ends.txt" copy
  assert_ne "mid_progress: only 0/total and total/total is NOT mid (non-zero)" "0" "$(cat "$D/canned-ends.txt.rc")"
  assert_contains "…with a FAIL verdict" "FAIL  mid-copy" "$(cat "$D/canned-ends.txt.log")"
  printf '%s\n' "wall=1 [files] copy progress 0/209715200" "wall=2 [files] copy progress 104857600/209715200" "wall=3 [files] copy progress 209715200/209715200" > "$D/canned-mid.txt"
  canned "$D/canned-mid.txt" copy
  assert_eq "mid_progress: a line with 0 < bytes < total is mid (zero)" "0" "$(cat "$D/canned-mid.txt.rc")"
  assert_contains "…and it names that line" "copy progress 104857600/209715200" "$(cat "$D/canned-mid.txt.log")"
  printf '%s\n' "wall=1 [files] zip extract progress 5/10" > "$D/canned-zip.txt"
  canned "$D/canned-zip.txt" copy
  assert_ne "mid_progress copy: a zip extract line is not a copy line (non-zero)" "0" "$(cat "$D/canned-zip.txt.rc")"
  canned "$D/canned-zip.txt" "zip extract"
  assert_eq "mid_progress 'zip extract': the same line is mid (zero)" "0" "$(cat "$D/canned-zip.txt.rc")"
fi

# ------------------------------------------------------------------------------------------------------ baseline
if leg baseline; then
  layout_save "$D/layout-before-baseline.json"
  baseline_start
  if scroll_to_node "$D/start-baseline.xml" "$FILES_TILE" 6; then :; fi
  assert_eq "Start shows a Files tile ($FILES_TILE)" "yes" "$(has_node "$D/start-baseline.xml" "$FILES_TILE")"
  record "the Files tile's node" "$(grep -o "<node[^>]*resource-id=\"$FILES_TILE\"[^>]*>" "$D/start-baseline.xml" | head -1 | grep -oE '(text|content-desc|bounds)="[^"]*"' | xargs)"
  layout_save "$D/layout-after-baseline.json"
  assert_eq "the shell kept the baseline as written: order, slots, dock, addedOnce, manualSizes" "same" "$(python3 - "$BASELINE" "$D/layout-after-baseline.json" <<'PY'
import json, sys
a, b = json.load(open(sys.argv[1])), json.load(open(sys.argv[2]))
diff = [k for k in ("order", "slots", "dock", "folders") if a.get(k) != b.get(k)]
diff += [k for k in ("addedOnce", "manualSizes") if sorted(a.get(k, [])) != sorted(b.get(k, []))]
print("same" if not diff else "differs in " + ", ".join(diff))
PY
)"
  assert_eq "baseline_layout-pre-18.json is phase 17's baseline, byte for byte" "same" "$(cmp -s "$P18/baseline_layout-pre-18.json" "$P18/../phase-17/baseline_layout.json" && echo same || echo differs)"
  # Put back the layout that was there.
  rings_save
  if layout_restore "$D/layout-before-baseline.json"; then _verdict PASS "the layout from before the leg is restored (layout_restore)" "verified by layout_restore"
  else _verdict FAIL "the layout from before the leg is restored (layout_restore)" "layout_restore returned non-zero"; fi
  ensure_start
  if scroll_to_node "$D/start-restored.xml" "$FILES_TILE" 6; then :; fi
  assert_eq "after the restore Start has no Files tile, as before" "no" "$(has_node "$D/start-restored.xml" "$FILES_TILE")"
  adb shell input keyevent KEYCODE_HOME; sleep 1
fi

# --------------------------------------------------------------------------------------------------------- probe
if leg probe; then
  PKG_MEDIA=content://media/external/images/media
  if [ ! -f "$QAC_APK" ]; then
    _verdict FAIL "probe: qa-capture is built" "missing $QAC_APK (./gradlew :testapps:qa-capture:assembleDebug --offline)"
  else
    adb install -r "$QAC_APK" > "$D/qa-capture-install.out" 2>&1
    assert_contains "probe: qa-capture installed" "Success" "$(cat "$D/qa-capture-install.out")"
    for pair in "android.intent.action.SEND_MULTIPLE application/zip" "android.intent.action.SEND text/plain" "android.intent.action.SEND image/png"; do
      set -- $pair
      assert_contains "the probe answers ${1##*.} of $2 (intent filter)" "$QAC/.SendProbeActivity" "$(q "cmd package query-activities --brief -a $1 -t $2")"
    done
    files_up media
    ID0="$(media_id images qa-photo-0.png DCIM/Camera/)"; ID1="$(media_id images qa-photo-1.png DCIM/Camera/)"
    MD0="$(q "md5sum /sdcard/DCIM/Camera/qa-photo-0.png" | cut -d' ' -f1)"
    record "media_up's two images in DCIM/Camera" "$PKG_MEDIA/$ID0 (md5 $MD0), $PKG_MEDIA/$ID1"

    # (a) SEND, one stream: a MediaStore URI the shell grants with the start (the data URI carries the grant; the
    # probe reads EXTRA_STREAM). Phase 17's line form, unchanged.
    adb logcat -c
    adb shell am start -a android.intent.action.SEND -t image/png -d "$PKG_MEDIA/$ID0" --grant-read-uri-permission \
      --eu android.intent.extra.STREAM "$PKG_MEDIA/$ID0" -n "$QAC/.SendProbeActivity" > "$D/probe-send.out" 2>&1
    sleep 2
    probe_lines > "$D/probe-send.txt"
    assert_eq "SEND: one line, phase 17's form, the md5 of the stream read through the grant" \
      "send action=android.intent.action.SEND type=image/png uri=$PKG_MEDIA/$ID0 md5=$MD0" "$(cat "$D/probe-send.txt")"

    # (b) SEND_MULTIPLE, two streams, driven from adb through the test app's sender (it resolves the probe by the
    # manifest filter). The streams are 10 and 17 known bytes.
    adb logcat -c
    adb shell am start -n "$QAC/.SendDriverActivity" --esa texts 0123456789,p18-second-stream --es mime text/plain > "$D/probe-multi.out" 2>&1
    sleep 2
    probe_lines > "$D/probe-multi.txt"
    assert_eq "SEND_MULTIPLE: the count line" "send streams=2 action=android.intent.action.SEND_MULTIPLE type=text/plain" "$(sed -n 1p "$D/probe-multi.txt")"
    assert_eq "SEND_MULTIPLE: two stream lines" "2" "$(grep -c '^send action=android.intent.action.SEND_MULTIPLE ' "$D/probe-multi.txt")"
    assert_eq "SEND_MULTIPLE: stream 1's URI and md5" \
      "send action=android.intent.action.SEND_MULTIPLE type=text/plain uri=content://$QAC.output/send-0.txt md5=$(printf 0123456789 | md5sum | cut -d' ' -f1)" "$(sed -n 2p "$D/probe-multi.txt")"
    assert_eq "SEND_MULTIPLE: stream 2's URI and md5" \
      "send action=android.intent.action.SEND_MULTIPLE type=text/plain uri=content://$QAC.output/send-1.txt md5=$(printf p18-second-stream | md5sum | cut -d' ' -f1)" "$(sed -n 3p "$D/probe-multi.txt")"

    # (c) Recorded, not graded: the same sender asked to forward media_up's two MediaStore URIs, which nobody granted it.
    adb logcat -c
    adb shell am start -n "$QAC/.SendDriverActivity" --esa uris "$PKG_MEDIA/$ID0,$PKG_MEDIA/$ID1" --es mime image/png > "$D/probe-ungranted.out" 2>&1
    sleep 2
    adb logcat -d -s "$QAC_TAG:I" | tr -d '\r' | grep -oE '(send|driver) .*' > "$D/probe-ungranted.txt"
    record "two MediaStore URIs with no grant, through the sender" "$(tr '\n' ';' < "$D/probe-ungranted.txt" | cut -c1-230)"
    adb shell input keyevent KEYCODE_HOME; sleep 1
    files_down
    if [ -z "$QAC_AT_BEGIN" ]; then
      adb uninstall "$QAC" > "$D/qa-capture-uninstall.out" 2>&1
      assert_eq "qa-capture was not installed at the start: uninstalled again" "" "$(q "pm path $QAC")"
    else
      record "qa-capture was installed at the start" "left installed, now this build"
    fi
  fi
fi

# ----------------------------------------------------------------------------------------------------------- jvm
if leg jvm; then
  jvm_gate 'app.tileshell.start.QuickRulesTest' 'app.tileshell.start.QuickRulesTest' aThrowingQueryIsCaughtAndBecomesTheReason
  assert_eq "gradle.rc holds gradle's exit code" "0" "$(cat "$D/gradle.rc")"
  cp "$D/gradle.rc" "$D/gradle-first.rc"; cp "$D/gradle.out" "$D/gradle-first.out"
  expect_fail "jvm_gate FAILS on a required case the report does not hold" "case p18NoSuchCase" \
    jvm_gate 'app.tileshell.start.QuickRulesTest' 'app.tileshell.start.QuickRulesTest' p18NoSuchCase
fi

# --------------------------------------------------------------------------------------------- the state at the end
log "--- the emulator, as it was found ---"
ensure_start
assert_eq "at the end: Start is on top" "app.tileshell/.StartActivity" "$(top_activity)"
assert_eq "at the end: the installed shell is the one from the start" "$APK_AT_BEGIN" "$(installed_apk_id)"
assert_eq "at the end: appop MANAGE_EXTERNAL_STORAGE as at the start" "$APPOP_AT_BEGIN" "$(q "appops get app.tileshell MANAGE_EXTERNAL_STORAGE" | cut -d';' -f1)"
assert_eq "at the end: no virtual disk, no public volume" "" "$(q 'sm list-disks' | xargs)$(q 'sm list-volumes public' | xargs)"
assert_eq "at the end: no QA-Files and no QA-Big" "" "$(q "ls -d $QA_FILES $QA_BIG 2>/dev/null" | xargs)"
# lib.sh's dump_ui (ensure_start uses it) leaves its scratch dump at /sdcard/qa.xml: removed when it was not there before.
grep -qxF /sdcard/qa.xml "$D/device-sdcard-at-begin.txt" || adb shell rm -f /sdcard/qa.xml
sleep 2
_snap_sdcard > "$D/device-sdcard-at-end.txt"; _snap_files > "$D/device-files-at-end.txt"
assert_eq "at the end: the shared-storage listing equals the one from the start" "" "$(diff "$D/device-sdcard-at-begin.txt" "$D/device-sdcard-at-end.txt" | head -6)"
assert_eq "at the end: the files collection equals the one from the start" "" "$(diff "$D/device-files-at-begin.txt" "$D/device-files-at-end.txt" | head -6)"
layout_save "$D/layout-at-end.json"
assert_eq "at the end: the layout file equals the one from the start" "same" "$(python3 -c 'import json,sys; print("same" if json.load(open(sys.argv[1])) == json.load(open(sys.argv[2])) else "differs")' "$D/layout-at-begin.json" "$D/layout-at-end.json")"
assert_eq "at the end: qa-capture installed as at the start" "${QAC_AT_BEGIN:+yes}" "$(q "pm path $QAC" | grep -q . && echo yes)"
assert_eq "at the end: start_theme.xml as at the start" "$PREFS_AT_BEGIN" "$(adb shell run-as app.tileshell ls shared_prefs/start_theme.xml >/dev/null 2>&1 && echo present || echo absent)"
row_end
