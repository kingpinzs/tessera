#!/usr/bin/env bash
# Phase 18 E13: zip (T18-2), every leg, on make_zips.py's fixtures pushed by files_up zips (paced, for qa-big.zip).
#
#   qa        open qa.zip → a virtual root (files_zip_root) listing one.txt "1.00 KB", dir, ü-name.txt, `[files] zip
#             open …: 3 entries`; tapping one.txt → "Extract first", nothing opens; extract → …/zips/qa/ with every
#             entry's md5 as make_zips.py recorded and ü-name.txt named correctly; extract again → the conflict
#             dialog, keep both → `qa (2)/`
#   bad       qa-bad.zip → ok.txt extracted, `zip: refused entry ../../evil.txt` and `… /sdcard/abs.txt`, and none of
#             /sdcard/evil.txt, /sdcard/QA-Files/evil.txt, /sdcard/abs.txt exists (the control: ok.txt does)
#   corrupt   qa-corrupt.zip → "This zip can't be opened", `zip open …: failed <why>`, no crash
#   enc       qa-enc.zip → "This zip is password-protected", `zip: encrypted …`, nothing written
#   bomb      qa-bomb.zip → `zip: stopped at <n> (declared 1024)` with n ≤ 1,024 + 1,048,576, no temp folder, no partial
#   huge      qa-huge.zip with fill_volume 2147483648 → refused before any write, `zip: refused (needs …, free …)`;
#             unfill_volume
#   big       qa-big.zip → the progress notification in dumpsys notification; cancelled mid-way → no output folder
#             and no temp folder (ls -a: nothing matching .*.extract)
#   nested    qa-nested.zip → qa.zip opens as a zip again; while it is open /sdcard/.Tessera/tmp/ holds its copy, and
#             after leaving the virtual root that folder is empty (a plain ls: it also holds .nomedia — BUILD-NOTES 10)
#   create    a.txt + b.bin → Archive.zip in the current folder; a.txt alone → a.txt.zip; the first pulled: host
#             `unzip -l` lists exactly those two names and `unzip -t` passes
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
Z=/sdcard/QA-Files/zips
HOSTZ="$FXGEN/zips"

keep_earlier_run E13
row_begin E13 "zip: a zip as a folder, extract, the guards (bad names, corrupt, encrypted, bomb, huge), cancel, nested, create"
assert_gate_apk
baseline_start
adb logcat -c
files_up paced zips || { row_end; exit 1; }
ensure_start
host_md5() { grep " $1\$" "$HOSTZ/make_zips.out" | cut -d' ' -f1; }   # make_zips.py's own record: "<md5>  qa.zip:<entry>"
leftovers() { q "ls -a $Z" | grep -E '^\..*\.(extract|part)$' | xargs; }

# ------------------------------------------------------------------------------------------------ qa.zip
log "--- qa.zip: the virtual root, Extract first, extract, extract again"
M="$(ring_mark)"; zopen qa.zip; D 01-qa; S 01-qa
assert_eq "qa.zip: a virtual root (files_zip_root), the last crumb its name" "yes qa.zip" "$(H 01-qa files_zip_root) $(X 01-qa files_crumb:3)"
assert_eq "qa.zip: it lists exactly dir, one.txt, ü-name.txt" "dir|one.txt|ü-name.txt|" "$(row_names 01-qa)"
assert_contains "qa.zip: one.txt's detail reads 1.00 KB (3 significant figures)" "1.00 KB" "$(X 01-qa files_detail:one.txt)"
assert_contains "qa.zip: [files] zip open …/qa.zip: 3 entries" "[files] zip open $ZD/qa.zip: 3 entries" "$(ring_since "$M")"
M="$(ring_mark)"; T 01-qa files_row:one.txt 1.2; D 02-first
assert_eq "qa.zip: a tap on one.txt shows Extract first (files_error)" "Extract first" "$(X 02-first files_error)"
assert_eq "qa.zip: …and nothing opens (Files on top, still the zip's root)" "$FILES_ACTIVITY yes" "$(top_activity) $(H 02-first files_zip_root)"
M="$(ring_mark)"; T 02-first files_extract 0.3; L="$(wait_op "$M" 30)"
assert_contains "qa.zip: [files] zip extract …/qa.zip -> …/zips/qa: done" "[files] zip extract $ZD/qa.zip -> $ZD/qa: done" "$L"
D 03-extracted
assert_eq "qa.zip: Files lands on the folder holding the output (zips, with the row qa)" "zips yes" "$(X 03-extracted files_crumb:2) $(H 03-extracted files_row:qa)"
q "cd $Z/qa && find . -type f | sort" > "$ROW_DIR/03-extracted-files.txt"
assert_eq "qa.zip: the output is /sdcard/QA-Files/zips/qa/ with exactly the three entries (ü-name.txt named correctly)" "./dir/two.bin ./one.txt ./ü-name.txt" "$(xargs < "$ROW_DIR/03-extracted-files.txt")"
for e in one.txt dir/two.bin ü-name.txt; do
  assert_eq "qa.zip: md5 of $e = make_zips.py's record" "$(host_md5 "qa.zip:$e")" "$(md5dev "$Z/qa/$e")"
done
assert_eq "qa.zip: no temp folder is left" "" "$(leftovers)"
zopen qa.zip; D 04; M="$(ring_mark)"; T 04 files_extract 1.5; D 05-conflict; S 05-conflict
assert_eq "extract again: the conflict dialog offers exactly replace / keep_both / skip" "files_dialog:replace files_dialog:keep_both files_dialog:skip" \
  "$(ids 05-conflict | tr ' ' '\n' | grep '^files_dialog:' | xargs)"
assert_contains "extract again: its title names qa" "named qa " "$(X 05-conflict files_dialog_title) "
T 05-conflict files_dialog:keep_both 0.3; L="$(wait_op "$M" 30)"
assert_contains "extract again, keep both: [files] zip extract … -> …/zips/qa (2): done" "[files] zip extract $ZD/qa.zip -> $ZD/qa (2): done" "$L"
assert_eq "extract again, keep both: qa (2)/ holds the three entries, qa/ is untouched" "3 $(host_md5 qa.zip:one.txt)" \
  "$(q "find '$Z/qa (2)' -type f | wc -l" | xargs) $(md5dev "$Z/qa/one.txt")"

# ------------------------------------------------------------------------------------------------ qa-bad.zip
log "--- qa-bad.zip: the entry-name guard"
zopen qa-bad.zip; D 10-bad
record "qa-bad.zip: the rows its page lists" "$(row_names 10-bad)"
M="$(ring_mark)"; T 10-bad files_extract 0.3; L="$(wait_op "$M" 30)"; SL="$(ring_since "$M")"
assert_contains "qa-bad.zip: [files] zip: refused entry ../../evil.txt" "[files] zip: refused entry ../../evil.txt" "$SL"
assert_contains "qa-bad.zip: [files] zip: refused entry /sdcard/abs.txt" "[files] zip: refused entry /sdcard/abs.txt" "$SL"
assert_contains "qa-bad.zip: the extract of the rest is done" "[files] zip extract $ZD/qa-bad.zip -> $ZD/qa-bad: done" "$L"
assert_eq "qa-bad.zip: the control — ok.txt is extracted (and only it)" "ok.txt" "$(q "ls -a $Z/qa-bad" | grep -v '^\.\.\?$' | xargs)"
for p in /sdcard/evil.txt /sdcard/QA-Files/evil.txt /sdcard/abs.txt /sdcard/QA-Files/zips/evil.txt /sdcard/QA-Files/zips/qa-bad/abs.txt; do
  assert_eq "qa-bad.zip: ls $p fails" "" "$(lsdev "$p")"
done
assert_eq "qa-bad.zip: no evil.txt or abs.txt anywhere on shared storage" "" "$(q "find /sdcard/ -name evil.txt -o -name abs.txt 2>/dev/null" | xargs)"

# ------------------------------------------------------------------------------------------------ corrupt, encrypted
log "--- qa-corrupt.zip, qa-enc.zip"
M="$(ring_mark)"; zopen qa-corrupt.zip; D 20-corrupt; S 20-corrupt
assert_eq "qa-corrupt.zip: This zip can't be opened (files_error), no virtual root" "This zip can't be opened no" "$(X 20-corrupt files_error) $(H 20-corrupt files_zip_root)"
CL="$(ring_since "$M" | grep -o "\[files\] zip open $ZD/qa-corrupt.zip: failed .*" | sed 's/ *wall=.*//' | head -1)"
record "qa-corrupt.zip: the line" "$CL"
assert_contains "qa-corrupt.zip: [files] zip open …/qa-corrupt.zip: failed <why>" "[files] zip open $ZD/qa-corrupt.zip: failed " "$CL "
assert_eq "qa-corrupt.zip: no crash (AndroidRuntime empty of app.tileshell)" "0" "$(crash)"
BEFORE="$(q "ls -a $Z" | xargs)"
M="$(ring_mark)"; zopen qa-enc.zip; D 21-enc; S 21-enc
assert_eq "qa-enc.zip: This zip is password-protected (files_error), no virtual root" "This zip is password-protected no" "$(X 21-enc files_error) $(H 21-enc files_zip_root)"
assert_contains "qa-enc.zip: [files] zip: encrypted …/qa-enc.zip" "[files] zip: encrypted $ZD/qa-enc.zip" "$(ring_since "$M")"
assert_eq "qa-enc.zip: nothing written (the folder's ls -a is unchanged)" "$BEFORE" "$(q "ls -a $Z" | xargs)"

# ------------------------------------------------------------------------------------------------ the bomb
log "--- qa-bomb.zip"
zopen qa-bomb.zip; D 30-bomb; M="$(ring_mark)"; T 30-bomb files_extract 0.3; L="$(wait_op "$M" 120)"; SL="$(ring_since "$M")"
D 31-bomb-after
STOP="$(printf '%s\n' "$SL" | grep -o '\[files\] zip: stopped at [0-9]* (declared [0-9]*)' | head -1)"
record "qa-bomb.zip: the line / the end line / files_error" "$STOP / $(printf '%s' "$L" | grep -o '\[files\].*' | sed 's/ *wall=.*//') / $(X 31-bomb-after files_error)"
assert_contains "qa-bomb.zip: [files] zip: stopped at <n> (declared 1024)" "(declared 1024)" "$STOP"
N="$(printf '%s' "$STOP" | sed -n 's/.*stopped at \([0-9]*\) .*/\1/p')"
assert_eq "qa-bomb.zip: n = ${N:-?} ≤ 1,024 + 1,048,576" "yes" "$([ -n "$N" ] && [ "$N" -le 1049600 ] && echo yes || echo no)"
assert_contains "qa-bomb.zip: the extraction stops (its end line: failed)" "[files] zip extract $ZD/qa-bomb.zip -> $ZD/qa-bomb: failed" "$L"
assert_eq "qa-bomb.zip: no temp folder and no partial file remains (ls -a)" "|" "$(leftovers)|$(lsdev "$Z/qa-bomb")"

# ------------------------------------------------------------------------------------------------ the huge zip
log "--- qa-huge.zip on a volume with at most 2 GB free (fill_volume 2147483648)"
if fill_volume 2147483648; then
  FREE="$(q 'df -k /sdcard' | awk 'NR==2 { print $4 * 1024 }')"
  _verdict PASS "qa-huge.zip: fill_volume 2147483648 (its own assert: free <= 2 GB + 5 MB, below the 3 GB declared)" "$FREE bytes free"
  assert_eq "awake after the fill's adb root / unroot" "Awake" "$(wake_device)"
  BEFORE="$(q "ls -a $Z" | xargs)"
  zopen qa-huge.zip; D 40-huge
  record "qa-huge.zip: its page" "zip root=$(H 40-huge files_zip_root) rows=$(rows 40-huge)"
  M="$(ring_mark)"; T 40-huge files_extract 3; SL="$(ring_since "$M")"; D 41-huge-after; S 41-huge-after
  REF="$(printf '%s\n' "$SL" | grep -o '\[files\] zip: refused (needs [0-9]*, free [0-9]*)' | head -1)"
  record "qa-huge.zip: the line / files_error" "$REF / $(X 41-huge-after files_error)"
  assert_contains "qa-huge.zip: [files] zip: refused (needs …, free …)" "[files] zip: refused (needs " "$REF"
  NEEDS="$(printf '%s' "$REF" | sed -n 's/.*needs \([0-9]*\),.*/\1/p')"; HAS="$(printf '%s' "$REF" | sed -n 's/.*free \([0-9]*\)).*/\1/p')"
  assert_eq "qa-huge.zip: needs (${NEEDS:-?}) ≥ the 3 GB declared and > free (${HAS:-?})" "yes" \
    "$([ -n "$NEEDS" ] && [ -n "$HAS" ] && [ "$NEEDS" -ge 3221225472 ] && [ "$NEEDS" -gt "$HAS" ] && echo yes || echo no)"
  assert_eq "qa-huge.zip: refused before any write (ls -a unchanged; no temp, no qa-huge)" "$BEFORE|" "$(q "ls -a $Z" | xargs)|$(leftovers)"
  absent_in "qa-huge.zip: no progress line (not a byte was written)" "zip extract progress" "$SL"
else
  _verdict FAIL "qa-huge.zip: fill_volume 2147483648" "its precondition failed (above); the leg was not run"
fi
unfill_volume
assert_eq "qa-huge.zip: unfill_volume — fill.bin is gone" "" "$(lsdev /sdcard/fill.bin)"
assert_eq "awake after unfill_volume" "Awake" "$(wake_device)"
ensure_start

# ------------------------------------------------------------------------------------------------ qa-big.zip, cancelled
log "--- qa-big.zip: the notification, cancelled mid-way"
zopen qa-big.zip; D 50-big; M="$(ring_mark)"; T 50-big files_extract 0.3
mid_progress "zip extract" "$M" 20 > "$ROW_DIR/50-mid.txt"
assert_contains "qa-big.zip (paced row): [files] qa pace $PACE_BPS" "[files] qa pace $PACE_BPS" "$(ring_since "$M")"
G 51-extracting
record "qa-big.zip: the box's text / the temp while it runs" "$(X 51-extracting files_progress_text) / $(leftovers)"
NREC="$(ops_notification "$ROW_DIR/52-notification.txt")"
record "qa-big.zip: the notification (dumpsys notification)" "$NREC"
assert_contains "qa-big.zip: the progress notification is in dumpsys notification (channel files_ops)" "title=[Extracting" "$NREC"
assert_contains "qa-big.zip: …with a Cancel action" "actions=[Cancel]" "$NREC"
if notif_cancel 53; then _verdict PASS "qa-big.zip: the notification's Cancel action was found and tapped" "53-shade*.xml"
else _verdict FAIL "qa-big.zip: the notification's Cancel action was found and tapped" "no Cancel node in the shade"; fi
L="$(wait_op "$M" 20)"
assert_contains "qa-big.zip: [files] zip extract … cancelled" "[files] zip extract $ZD/qa-big.zip -> $ZD/qa-big: cancelled" "$L"
assert_eq "qa-big.zip: no output folder and no temp folder (ls -a: nothing matching .*.extract)" "|" "$(lsdev "$Z/qa-big")|$(leftovers)"

# ------------------------------------------------------------------------------------------------ the nested zip
log "--- qa-nested.zip"
ensure_start
zopen qa-nested.zip; D 60-nested
assert_eq "qa-nested.zip: qa.zip is listed as a file" "yes" "$(H 60-nested files_row:qa.zip)"
M="$(ring_mark)"; T 60-nested files_row:qa.zip 2; D 61-inner; S 61-inner
assert_eq "qa-nested.zip: qa.zip opens as a zip again (files_zip_root, its three rows)" "yes dir|one.txt|ü-name.txt|" "$(H 61-inner files_zip_root) $(row_names 61-inner)"
TMPL="$(q 'ls -a /sdcard/.Tessera/tmp' | grep -v '^\.\.\?$' | xargs)"
record "qa-nested.zip: /sdcard/.Tessera/tmp while it is open (ls -a)" "$TMPL"
assert_eq "qa-nested.zip: while it is open the folder holds its copy (.qa.zip.<opid>.part), md5 = qa.zip's" "$(host_md5 qa.zip)" \
  "$(q "md5sum /sdcard/.Tessera/tmp/.qa.zip.*.part" | cut -d' ' -f1)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1.5; D 62-left
assert_eq "qa-nested.zip: left the virtual root (no files_zip_root)" "no" "$(H 62-left files_zip_root)"
assert_eq "qa-nested.zip: after leaving, /sdcard/.Tessera/tmp/ is empty (a plain ls)" "" "$(q 'ls /sdcard/.Tessera/tmp' | xargs)"

# ------------------------------------------------------------------------------------------------ create
log "--- create a zip from a selection"
files_at "$QF"; D 70; T 70 files_bar:select 1; D 71; T 71 files_row:a.txt 0.6; D 72; T 72 files_row:b.bin 0.6; D 73; T 73 files_bar:more 1; D 74-more; S 74-more
assert_eq "create: the selection's overflow offers Create zip (files_zip_create)" "yes" "$(H 74-more files_zip_create)"
M="$(ring_mark)"; T 74-more files_zip_create 0.3; L="$(wait_op "$M" 30)"
assert_contains "create: [files] zip create 2 files -> …/QA-Files/Archive.zip: done" "[files] zip create 2 files -> $QF/Archive.zip: done" "$L"
assert_eq "create: Archive.zip is in the current folder" "/sdcard/QA-Files/Archive.zip" "$(lsdev /sdcard/QA-Files/Archive.zip)"
files_at "$QF"; D 75; T 75 files_bar:select 1; D 76; T 76 files_row:a.txt 0.6; D 77; T 77 files_bar:more 1; D 78; M="$(ring_mark)"; T 78 files_zip_create 0.3; L="$(wait_op "$M" 30)"
assert_contains "create: from a.txt alone — [files] zip create 1 files -> …/a.txt.zip: done" "[files] zip create 1 files -> $QF/a.txt.zip: done" "$L"
assert_eq "create: a.txt.zip is in the current folder" "/sdcard/QA-Files/a.txt.zip" "$(lsdev /sdcard/QA-Files/a.txt.zip)"
adb pull /sdcard/QA-Files/Archive.zip "$ROW_DIR/Archive.zip" > "$ROW_DIR/79-pull.out" 2>&1
unzip -l "$ROW_DIR/Archive.zip" > "$ROW_DIR/79-unzip-l.txt" 2>&1; echo $? > "$ROW_DIR/79-unzip-l.rc"
unzip -t "$ROW_DIR/Archive.zip" > "$ROW_DIR/79-unzip-t.txt" 2>&1; echo $? > "$ROW_DIR/79-unzip-t.rc"
assert_eq "create: host unzip -l lists exactly a.txt and b.bin" "a.txt b.bin" "$(unzip -Z1 "$ROW_DIR/Archive.zip" 2>/dev/null | LC_ALL=C sort | xargs)"
assert_eq "create: host unzip -t passes (exit code; its last line)" "0" "$(cat "$ROW_DIR/79-unzip-t.rc")"
record "create: unzip -t's last line" "$(tail -1 "$ROW_DIR/79-unzip-t.txt")"
assert_eq "create: the zipped entries are the fixtures (md5 of a.txt, b.bin read out of the zip)" "$(fx_md5 a.txt) $(fx_md5 b.bin)" \
  "$(unzip -p "$ROW_DIR/Archive.zip" a.txt | md5sum | cut -d' ' -f1) $(unzip -p "$ROW_DIR/Archive.zip" b.bin | md5sum | cut -d' ' -f1)"

# ------------------------------------------------------------------------------------------------ the end
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
assert_eq "no ANR of the shell in the row" "0" "$(anr)"
adb shell input keyevent KEYCODE_HOME; sleep 1
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
row_end
