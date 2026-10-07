#!/usr/bin/env bash
# Phase 18 — the adversarial review's device experiments 3-10 (docs/plan/review/2026-10-06-phase18-gate-adversarial.md,
# "Device experiments owed (the safe outcome stated)"), run on the fixed gate build. Each leg asserts the review's own
# SAFE outcome. Row REVIEW_EXP; legs can be named: review_experiments.sh [3 4 5 6 7 8 9 10].
#
#   3  M4   a `.Tessera.zip` holding bin/.index.json and a bin file, pushed to /sdcard/, extracted in Files.
#           Safe: refused; the bin page (and the bin's folder and index) unchanged.
#   4  H3   delete the FILE /sdcard/QA-Files/n, make a FOLDER n with files, Restore, Replace.
#           Safe: the folder's files survive or are in the bin.
#   5  M6   `ln -s` on shared storage from the shell and from an app in its own Android/data/<pkg>/. Safe: both fail.
#   6  M6   `content read` on a private path, and a qa-capture grant with a rewritten path. Safe: both refused.
#   7  L8   FilesActivity started with a `page` extra holding a newline and a forged `[files]` line. Safe: one line.
#   8  L9   FilesActivity started with a Parcelable as `page`. Safe: no crash. `am start` can send only the platform's
#           own Parcelables (a Uri: --eu) and wrong-typed values (an int, an int array); a CUSTOM Parcelable class needs a
#           sender app, which no fixture has — that form is RECORDED as not run.
#   9  L10  a crafted zip of about 60 MB with 1.3 million entries, tapped. Safe: "can't be opened", the process alive.
#   10 L11  an index record {"bin": ".NOMEDIA"}, the bin opened. Safe: not listed.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
LEGS="${*:-3 4 5 6 7 8 9 10}"
leg() { case " $LEGS " in *" $1 "*) log "--- experiment $1 ($(date '+%H:%M:%S'))"; return 0 ;; *) return 1 ;; esac; }
PRIMARY=/storage/emulated/0

keep_earlier_run REVIEW_EXP
row_begin REVIEW_EXP "the adversarial review's device experiments ($LEGS), each with its stated safe outcome"
assert_gate_apk
baseline_start
adb logcat -c
files_up || { row_end; exit 1; }
ensure_start
bin_state() { echo "$(bin_ls) $(md5dev "$BINP/.index.json")"; }
hidden_toggle() { files_at "$QF"; D h0; T h0 files_bar:more 1; D h1; T h1 files_more:settings 1.5; D h2; T h2 files_setting_hidden 1; adb shell input keyevent KEYCODE_BACK; sleep 1; }
pid_now() { q 'pidof app.tileshell'; }

# ------------------------------------------------------------------------------------------------ 3 (M4)
if leg 3; then
  bin_it "$QF" img-0.png
  B0="$(bin_state)"; MD5_BINNED="$(md5dev "$BINP/$(bin_name_of "$QF/img-0.png")")"
  python3 - "$ROW_DIR/Tessera.zip" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("bin/.index.json", '{"version":1,"records":[{"bin":"1-1-planted.txt","path":"/storage/emulated/0/DCIM/planted.txt","deletedAt":1,"size":7}]}')
    z.writestr("bin/1-1-planted.txt", "planted")
PY
  adb push "$ROW_DIR/Tessera.zip" /sdcard/.Tessera.zip >/dev/null 2>&1
  assert_eq "3: /sdcard/.Tessera.zip is pushed" "/sdcard/.Tessera.zip" "$(lsdev /sdcard/.Tessera.zip)"
  hidden_toggle                                     # a dot-file is listed only with the hidden-files setting on
  files_at "$PRIMARY"; scroll_to_node "$ROW_DIR/e3-0.xml" files_row:.Tessera.zip >/dev/null
  assert_eq "3: with hidden files shown the zip is listed at the volume's root" "yes" "$(H e3-0 files_row:.Tessera.zip)"
  M="$(ring_mark)"; T e3-0 files_row:.Tessera.zip 2; D e3-1; S e3-1
  if [ "$(H e3-1 files_extract)" = yes ]; then T e3-1 files_extract 3; wait_op "$M" 10 >/dev/null; D e3-2; S e3-2; else cp "$ROW_DIR/e3-1.xml" "$ROW_DIR/e3-2.xml"; fi
  SL="$(ring_since "$M")"
  record "3: what Files said / its lines" "[$(X e3-2 files_error)] / $(printf '%s\n' "$SL" | grep -o '\[files\] zip.*' | sed 's/ *wall=.*//' | tr '\n' '|')"
  assert_ne "3: SAFE — refused (the page says why)" "" "$(X e3-2 files_error)"
  assert_eq "3: SAFE — nothing was extracted over the bin: its listing and index are unchanged" "$B0" "$(bin_state)"
  assert_eq "3: …the planted file is nowhere under .Tessera, and the binned file's bytes are unchanged" "0 $MD5_BINNED" \
    "$(q "find /sdcard/.Tessera -name '*planted*'" | grep -c .) $(md5dev "$BINP/$(bin_name_of "$QF/img-0.png")")"
  absent_in "3: no extract … done line" ": done" "wall= $(printf '%s\n' "$SL" | grep -F '[files] zip extract')"
  files_open --es page bin; D e3-3
  assert_eq "3: SAFE — the bin page is unchanged (img-0.png listed, no planted row)" "yes 0" "$(H e3-3 files_bin_row:img-0.png) $(grep -c 'planted' "$ROW_DIR/e3-3.xml")"
  hidden_toggle
  adb shell "rm -f /sdcard/.Tessera.zip"
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 4 (H3)
if leg 4; then
  adb shell "printf 'the file n' > /sdcard/QA-Files/n"
  bin_it "$QF" n
  assert_contains "4: the FILE n is deleted to the bin" "[files] bin delete $QF/n: ok" "$(ring_since "$BIN_MARK")"
  adb shell "mkdir /sdcard/QA-Files/n && printf one > /sdcard/QA-Files/n/keep1.txt && printf two > /sdcard/QA-Files/n/keep2.txt"
  K1="$(md5dev /sdcard/QA-Files/n/keep1.txt)"; K2="$(md5dev /sdcard/QA-Files/n/keep2.txt)"
  binsel n; M="$(ring_mark)"; T b2 files_bin_restore 1.5; D e4-1; S e4-1
  assert_eq "4: Restore asks (the conflict dialog)" "yes" "$(H e4-1 files_dialog:replace)"
  T e4-1 files_dialog:replace 2.5; D e4-2
  SL="$(ring_since "$M")"
  record "4: the lines / files_error / what n is now" "$(printf '%s\n' "$SL" | grep -o '\[files\] bin.*' | sed 's/ *wall=.*//' | tr '\n' '|') / [$(X e4-2 files_error)] / $(q 'stat -c %F /sdcard/QA-Files/n')"
  where() { # md5 -> where a file with it is now: folder | bin | LOST
    if [ "$(q "md5sum /sdcard/QA-Files/n/* 2>/dev/null" | grep -c "^$1 ")" != 0 ]; then echo folder
    elif [ "$(q "find /sdcard/.Tessera/bin -type f -exec md5sum {} + 2>/dev/null" | grep -c "^$1 ")" != 0 ]; then echo bin
    else echo LOST; fi
  }
  W1="$(where "$K1")"; W2="$(where "$K2")"
  record "4: where the folder's two files are now" "$W1 $W2"
  assert_eq "4: SAFE — the folder's files survive or are in the bin (neither is lost)" "0" "$(printf '%s\n' "$W1" "$W2" | grep -c LOST)"
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 5 (M6)
if leg 5; then
  adb shell "ln -s /data/data/app.tileshell/files /sdcard/QA-Files/link-shell" > "$ROW_DIR/e5-shell.out" 2>&1; echo $? > "$ROW_DIR/e5-shell.rc"
  record "5: ln -s from the shell — exit code / output" "$(cat "$ROW_DIR/e5-shell.rc") / $(tr -d '\r' < "$ROW_DIR/e5-shell.out" | xargs)"
  assert_ne "5: SAFE — ln -s on shared storage from the shell fails" "0" "$(cat "$ROW_DIR/e5-shell.rc")"
  assert_eq "5: …and no link exists" "" "$(q 'ls -d /sdcard/QA-Files/link-shell 2>/dev/null')"
  adb install -r "$QAC_APK" > "$ROW_DIR/e5-install.out" 2>&1
  adb shell "run-as $QAC sh -c 'mkdir -p /sdcard/Android/data/$QAC/files; ln -s /data/data/$QAC/files /sdcard/Android/data/$QAC/files/link-app; echo rc=\$?; ls -la /sdcard/Android/data/$QAC/files 2>&1'" > "$ROW_DIR/e5-app.out" 2>&1
  record "5: ln -s from an app in its own Android/data/<pkg>/ (run-as qa-capture)" "$(tr -d '\r' < "$ROW_DIR/e5-app.out" | xargs | cut -c1-240)"
  assert_absent "5: SAFE — ln -s from the app fails (rc is not 0)" "rc=0" "$(cat "$ROW_DIR/e5-app.out")"
  assert_absent "5: …and no link exists there" "link-app ->" "$(cat "$ROW_DIR/e5-app.out")"
  adb shell "run-as $QAC sh -c 'rm -rf /sdcard/Android/data/$QAC/files'" >/dev/null 2>&1
fi

# ------------------------------------------------------------------------------------------------ 6 (M6)
if leg 6; then
  PRIVATE=/data/data/app.tileshell/files/files-recent.json
  tap_row "$QF/recent" r1.png 2.5; adb shell input keyevent KEYCODE_BACK; sleep 1     # the private file exists
  assert_ne "6 precondition: the private file exists (files-recent.json)" "" "$(recent_json)"
  adb shell "content read --uri content://app.tileshell.files/root$PRIVATE" > "$ROW_DIR/e6-read.out" 2>&1
  record "6: content read on the private path" "$(head -c 240 "$ROW_DIR/e6-read.out" | tr '\n' ' ')"
  assert_contains "6: SAFE — content read on a private path is refused" "Exception" "$(cat "$ROW_DIR/e6-read.out")"
  assert_absent "6: …and prints none of the file" '"recent"' "$(cat "$ROW_DIR/e6-read.out")"
  adb install -r "$QAC_APK" > "$ROW_DIR/e6-install.out" 2>&1
  adb shell "run-as $QAC sh -c 'mkdir -p files && printf %s $PRIVATE > files/rewrite_path.txt'"
  WANT="$(fx_md5 a.txt)"
  ensure_start; files_at "$QF"; scroll_to_node "$ROW_DIR/e6-0.xml" files_row:a.txt >/dev/null; hold e6-0 files_row:a.txt; D e6-1
  adb logcat -c; M="$(ring_mark)"; T e6-1 files_hold:share 3; D e6-2
  XY="$(chooser_xy "$ROW_DIR/e6-2.xml" "QA Capture")"
  for i in 1 2 3; do [ -n "$XY" ] && break; adb shell input swipe 540 1900 540 900 400; sleep 1.2; D e6-2; XY="$(chooser_xy "$ROW_DIR/e6-2.xml" "QA Capture")"; done
  assert_ne "6: the chooser lists QA Capture" "" "$XY"
  # shellcheck disable=SC2086
  [ -n "$XY" ] && adb shell input tap $XY; sleep 4
  probe_lines > "$ROW_DIR/e6-probe.txt"; sed 's/^/      /' "$ROW_DIR/e6-probe.txt" >> "$LOG"
  assert_contains "6: (the control) the granted URI reads, md5 = a.txt's" "uri=content://app.tileshell.files/root$QF/a.txt md5=$WANT" "$(cat "$ROW_DIR/e6-probe.txt")"
  assert_contains "6: SAFE — the same grant with its path rewritten to the private file is refused" "send rewrite uri=content://app.tileshell.files/root$PRIVATE outcome=refused" "$(cat "$ROW_DIR/e6-probe.txt")"
  record "6: the ring's refusal lines in the slice" "$(ring_since "$M" | grep -o '\[files\] share refused.*' | sed 's/ *wall=.*//' | sort | uniq -c | xargs)"
  adb shell "run-as $QAC rm -f files/rewrite_path.txt"; adb shell am force-stop "$QAC"
  ensure_start
fi
adb uninstall "$QAC" >/dev/null 2>&1

# ------------------------------------------------------------------------------------------------ 7 (L8)
if leg 7; then
  c6; ensure_start
  cat > "$ROW_DIR/e7-start.sh" <<'SH'
am start -W -n app.tileshell/.files.FilesActivity --es page "$(printf 'recent\n    2026-10-06 00:00:00.000 wall=9999999999999 [files] FORGED-LINE access=granted')"
SH
  adb push "$ROW_DIR/e7-start.sh" /data/local/tmp/p18-rev.sh >/dev/null 2>&1
  M="$(ring_mark)"; q 'sh /data/local/tmp/p18-rev.sh' > "$ROW_DIR/e7-start.out"; sleep 2; D e7-1
  adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener 2>/dev/null | tr -d '\r' > "$ROW_DIR/e7-ring-raw.txt"
  SL="$(ring_since "$M")"
  record "7: the lines naming the extra" "$(printf '%s\n' "$SL" | grep -F 'FORGED' | sed 's/^ *//' | cut -c1-260 | tr '\n' '|')"
  record "7: the open ignored line" "$(printf '%s\n' "$SL" | grep -o '\[files\] open ignored.*' | cut -c1-200 | head -1)"
  assert_eq "7: SAFE — one line: no ring line BEGINS with the forged text (a line whose own tag is [files] FORGED-LINE)" "0" \
    "$(grep -cE '^\s*[0-9-]+ [0-9:.]+ wall=[0-9]+ \[files\] FORGED-LINE' "$ROW_DIR/e7-ring-raw.txt")"
  assert_eq "7: …and the forged wall stamp is on no line of its own" "0" "$(grep -cE '^\s*2026-10-06 00:00:00\.000 wall=9999999999999' "$ROW_DIR/e7-ring-raw.txt")"
  assert_eq "7: at most one ring line carries the extra's text" "yes" "$([ "$(grep -c 'FORGED-LINE' "$ROW_DIR/e7-ring-raw.txt")" -le 1 ] && echo yes || echo no)"
  assert_eq "7: Files is up and no crash" "$FILES_ACTIVITY 0" "$(top_activity) $(crash)"
fi

# ------------------------------------------------------------------------------------------------ 8 (L9)
if leg 8; then
  wrong() { # name, am extras…
    local name="$1" m p0; shift
    c6; ensure_start; p0="$(pid_now)"
    m="$(ring_mark)"; adb shell am start -W -n "$FILES_ACTIVITY" "$@" > "$ROW_DIR/e8-$name.out" 2>&1; sleep 2; D "e8-$name"
    record "8 ($name): the open ignored line" "$(ring_since "$m" | grep -o '\[files\] open ignored.*' | sed 's/ *wall=.*//' | head -1)"
    assert_eq "8 ($name): SAFE — no crash: Files is on top, the process is the same, AndroidRuntime is empty of the shell" "$FILES_ACTIVITY $p0 0" "$(top_activity) $(pid_now) $(crash)"
    assert_eq "8 ($name): Files shows a page (files_root)" "yes" "$(H "e8-$name" files_root)"
  }
  wrong uri --eu page content://app.tileshell.files/root/x
  wrong int --ei page 7
  wrong intarray --eia page 1,2,3
  wrong path-uri --eu path file:///data/data/app.tileshell
  record "8: a CUSTOM Parcelable class as page" "NOT RUN: am start cannot build one and no fixture app sends one; it would need a sender added to testapps/qa-capture"
fi

# ------------------------------------------------------------------------------------------------ 9 (L10)
if leg 9; then
  python3 - "$ROW_DIR/many.zip" <<'PY'
import struct, sys
# One stored empty local entry, then 1,300,000 central-directory records that all point at it, a zip64 end record and
# its locator (the count does not fit 16 bits): about 69 MB of central directory.
N = 1300000
name = b"e0000000"
local = struct.pack("<IHHHHHIIIHH", 0x04034b50, 20, 0, 0, 0, 0x21, 0, 0, 0, len(name), 0) + name
with open(sys.argv[1], "wb") as f:
    f.write(local)
    cd_off = f.tell()
    buf = bytearray()
    for i in range(N):
        n = b"e%07d" % i
        buf += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014b50, 20, 20, 0, 0, 0, 0x21, 0, 0, 0, len(n), 0, 0, 0, 0, 0, 0) + n
        if len(buf) > 1 << 22: f.write(buf); buf = bytearray()
    f.write(buf)
    cd_size = f.tell() - cd_off
    z64 = f.tell()
    f.write(struct.pack("<IQHHIIQQQQ", 0x06064b50, 44, 45, 45, 0, 0, N, N, cd_size, cd_off))
    f.write(struct.pack("<IIQI", 0x07064b50, 0, z64, 1))
    f.write(struct.pack("<IHHHHIIH", 0x06054b50, 0, 0, 0xFFFF, 0xFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0))
PY
  record "9: the crafted zip (bytes; 1,300,000 central-directory records)" "$(stat -c%s "$ROW_DIR/many.zip")"
  adb push "$ROW_DIR/many.zip" /sdcard/QA-Files/many.zip >/dev/null 2>&1
  rm -f "$ROW_DIR/many.zip"
  c6; ensure_start
  files_at "$QF"; scroll_to_node "$ROW_DIR/e9-0.xml" files_row:many.zip >/dev/null; P0="$(pid_now)"
  M="$(ring_mark)"; tap_node "$ROW_DIR/e9-0.xml" files_row:many.zip
  L="$(wait_line "$M" "[files] zip open $QF/many.zip" 30)"; sleep 1; D e9-1; S e9-1
  record "9: the line / ms from the tap" "$(printf '%s' "$L" | grep -o '\[files\].*' | sed 's/ *wall=.*//') / $([ -n "$L" ] && echo $(( $(printf '%s' "$L" | wall_of) - M )))"
  assert_eq "9: SAFE — This zip can't be opened (files_error), no virtual root" "This zip can't be opened no" "$(X e9-1 files_error) $(H e9-1 files_zip_root)"
  assert_contains "9: …[files] zip open …: failed <why>" "[files] zip open $QF/many.zip: failed " "$L "
  assert_eq "9: SAFE — the process is alive (the same pid), no crash, no ANR" "$P0 0 0" "$(pid_now) $(crash) $(anr)"
fi

# ------------------------------------------------------------------------------------------------ 10 (L11)
if leg 10; then
  [ -n "$(lsdev "$BINP/.index.json")" ] || bin_it "$QF" img-1.png
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell "cat $BINP/.index.json" > "$ROW_DIR/e10-index-before.json"
  python3 - "$ROW_DIR/e10-index-before.json" "$ROW_DIR/e10-index.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
d["records"].append({"bin": ".NOMEDIA", "path": "/storage/emulated/0/QA-Files/planted-nomedia", "deletedAt": 1, "size": 0})
json.dump(d, open(sys.argv[2], "w"))
PY
  adb push "$ROW_DIR/e10-index.json" "$BINP/.index.json" >/dev/null 2>&1
  assert_contains "10: the index now holds the record {\"bin\": \".NOMEDIA\"}" ".NOMEDIA" "$(q "cat $BINP/.index.json")"
  c6; ensure_start
  M="$(ring_mark)"; files_open --es page bin; D e10-1; S e10-1
  record "10: the bin page's rows / the index line" "$(bin_rows e10-1) / $(ring_since "$M" | grep -o '\[files\] bin index.*' | sed 's/ *wall=.*//' | tail -1)"
  assert_eq "10: SAFE — the record is not listed (no row for .NOMEDIA / .nomedia / its path's name)" "0" "$(bin_rows e10-1 | tr '|' '\n' | grep -ciE '^\.nomedia$|planted-nomedia')"
  assert_eq "10: …and the bin's marker file is still there" "$BINP/.nomedia" "$(lsdev "$BINP/.nomedia")"
fi

# ------------------------------------------------------------------------------------------------ the end
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
adb shell input keyevent KEYCODE_HOME; sleep 1
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
assert_eq "end: the fixture app is uninstalled" "" "$(q "pm path $QAC")"
row_end
