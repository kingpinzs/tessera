#!/usr/bin/env bash
# Phase 18 E4b: the Recycle Bin (T18-1), every leg, on the row's own fixtures (files_up media, pubvol_up).
#
#   A  delete c.bin → gone, `<ms>-<seq>-c.bin` in /sdcard/.Tessera/bin with the md5, .nomedia, .index.json holding the
#      original path, `[files] bin delete …/c.bin: ok`; the bin page (files_pane:bin) lists it with its volume and the
#      privacy note (T18-10)
#   B  restore c.bin → back with the same md5, the row gone
#   C  delete qa-photo-0.png from DCIM/Camera → out of the images collection within 3 s, the Photos tile's refresh
#      line, and it does not come back after a second scan
#   D  r3 D3: search "b", select b.bin and sub/b.bin, delete both in one action → two bin files, two records with
#      different names, both md5s, each restored to its own path
#   E  restore into a deleted folder (sub/'s file binned, then `rm -r sub`) → sub/ made again with the file
#   F  restore over an existing name → the conflict dialog; skip / keep both / replace each do what they say
#   G  Delete permanently one row → gone from the bin folder and the index (a delete made on the bin page is permanent)
#   H  Empty → the bin folder holds only .nomedia and .index.json with zero records
#   I  a delete on the row's public volume lands in THAT volume's bin, and Restore puts it back there (r3 V17)
#   J  r3 D3: a 255-byte name deleted → ok, the bin name ≤ 255 bytes, Restore gives the full name back, md5 unchanged
#   K  negatives: `adb shell rm` never reaches the bin; a bin row opens nothing and adds nothing to Recent; the bin is
#      not in the listing of /sdcard (dot-folders hidden)
#   L  full volume: fill_volume 1048576 → a delete still moves to the bin (a rename needs no space); unfill_volume
#   M  `adb shell rm -r /sdcard/.Tessera` → the next delete makes the bin again, `bin index …: rebuilt (bin folder missing)`
#   N  r3 V14: the bin path is a FILE → delete a.txt → `bin delete …: failed <why>`, files_error, a.txt untouched
#   O  `adb uninstall app.tileshell` → the bin still lists its files; reinstall through provision.sh with the SAME
#      gate APK (md5 asserted), the baseline layout put back
#
# The ring's paths are the real ones (/storage/emulated/0/…; BUILD-NOTES pure layer 2). The `rebuilt (bin folder
# missing)` line is asserted only after the row's own `rm -r .Tessera`, from a MARK (BUILD-NOTES pure layer 3).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
SCRATCH=/tmp/claude-1000/-home-jeremyking/340515de-6745-4b8c-a7bc-95d3c74d1c3e/scratchpad/rows-b
mkdir -p "$SCRATCH"
PRIMARY=/storage/emulated/0
CAM=$PRIMARY/DCIM/Camera
NOTE_TEXT="Deleted files stay on this phone until you empty the Recycle Bin"

keep_earlier_run E4b
row_begin E4b "the Recycle Bin: delete, restore (gone folder, conflict), purge, empty, the public volume, two at once, 255 bytes, the bin path a file, full volume, rm -r, uninstall"
assert_gate_apk
baseline_start
adb logcat -c
files_up media || { row_end; exit 1; }
pubvol_up >/dev/null || { files_down; row_end; exit 1; }
sleep 2
V="$PUBVOL_PATH"
record "start: /sdcard/.Tessera before the row's first delete" "[$(lsdev /sdcard/.Tessera)]"
adb shell "dd if=/dev/urandom of=/sdcard/QA-Files/c.bin bs=1024 count=20" >/dev/null 2>&1
MD5_C="$(md5dev /sdcard/QA-Files/c.bin)"; MD5_A="$(fx_md5 a.txt)"; MD5_B="$(fx_md5 b.bin)"
assert_ne "the row's own c.bin is made (20 KB of urandom), md5 recorded" "" "$MD5_C"
ensure_start

restore_rows() { # the bin page, the named rows selected, Restore; RM = the MARK before the tap
  binsel "$@"; RM="$(ring_mark)"; T b2 files_bin_restore "${SETTLE:-1.5}"
}

# ------------------------------------------------------------------------------------------------ A: delete c.bin
log "--- A: delete c.bin"
bin_it "$QF" c.bin
SL="$(ring_since "$BIN_MARK")"
assert_eq "A: ls /sdcard/QA-Files/c.bin fails" "" "$(lsdev /sdcard/QA-Files/c.bin)"
BN="$(q "ls $BINP" | grep -E '^[0-9]+-[0-9]+-c\.bin$')"
assert_ne "A: ls /sdcard/.Tessera/bin/ lists <ms>-<seq>-c.bin" "" "$BN"
record "A: the bin's listing (ls -a)" "$(bin_ls)"
assert_eq "A: the bin file's md5 is unchanged" "$MD5_C" "$(md5dev "$BINP/$BN")"
assert_eq "A: /sdcard/.Tessera/bin/.nomedia exists" "$BINP/.nomedia" "$(lsdev "$BINP/.nomedia")"
adb shell "cat $BINP/.index.json" > "$ROW_DIR/a-index.json" 2>&1
assert_eq "A: .index.json holds its original path against its bin name" "$BN|$QF/c.bin" "$(bin_index | grep -F "|$QF/c.bin")"
assert_contains "A: [files] bin delete …/QA-Files/c.bin: ok" "[files] bin delete $QF/c.bin: ok" "$SL"
files_at "$QF"; D a0; T a0 files_menu 1; D a1; T a1 files_pane:bin 1.5; D a-bin; S a-bin
assert_eq "A: the Recycle Bin page, reached by files_pane:bin (crumb, files_bin)" "Recycle Bin yes" "$(X a-bin files_crumb:0) $(H a-bin files_bin)"
assert_eq "A: it lists files_bin_row:c.bin" "yes" "$(H a-bin files_bin_row:c.bin)"
# Gate build 2: the detail is "<deleted date> <restore folder relative to the volume>", prefixed "<volume name>/" only
# while more than one volume is mounted (the row's public volume is up here).
record "A: the bin row's detail" "$(X a-bin files_detail:c.bin)"
assert_eq "A: …with its volume and restore folder (the detail: <today> This Device/QA-Files)" "$(adb shell date +%-m/%-d/%Y | tr -d '\r') This Device/QA-Files" "$(X a-bin files_detail:c.bin)"
assert_eq "A: files_bin_note's text (T18-10)" "$NOTE_TEXT" "$(X a-bin files_bin_note)"

# ------------------------------------------------------------------------------------------------ B: restore c.bin
log "--- B: restore c.bin"
restore_rows c.bin
assert_contains "B: [files] bin restore …/QA-Files/c.bin: ok" "[files] bin restore $QF/c.bin: ok" "$(ring_since "$RM")"
assert_eq "B: back at /sdcard/QA-Files/c.bin with the same md5" "$MD5_C" "$(md5dev /sdcard/QA-Files/c.bin)"
D b-after
assert_eq "B: the bin row is gone" "no" "$(H b-after files_bin_row:c.bin)"
assert_eq "B: …and the bin folder and the index no longer hold it" "0 0" "$(q "ls $BINP" | grep -c -- '-c\.bin$') $(bin_index | grep -c "|$QF/c.bin")"

# ------------------------------------------------------------------------------------------------ C: the photo
log "--- C: delete qa-photo-0.png from DCIM/Camera"
# By its path: this AVD also holds a /sdcard/Pictures/qa-photo-0.png that is not the row's (found at run 1).
img_listed() { q "content query --uri content://media/external/images/media --projection _display_name:_data" | grep -c "_display_name=qa-photo-0.png, _data=$CAM/qa-photo-0.png"; }
assert_eq "C precondition: the images collection lists qa-photo-0.png (media_up)" "1" "$(img_listed)"
MD5_P="$(md5dev /sdcard/DCIM/Camera/qa-photo-0.png)"
bin_it "$CAM" qa-photo-0.png
T0="$BIN_MARK"; GONE_MS=""
for i in $(seq 1 12); do
  if [ "$(img_listed)" = 0 ]; then GONE_MS=$(( $(device_ms) - T0 )); break; fi
  sleep 0.2
done
record "C: ms from the MARK before the confirming tap to the query that no longer lists it" "${GONE_MS:-not within the poll}"
assert_eq "C: content query no longer lists qa-photo-0.png" "0" "$(img_listed)"
assert_eq "C: …within 3 s (the tap's own 1.5 s settle is inside the figure)" "yes" "$([ -n "$GONE_MS" ] && [ "$GONE_MS" -le 3000 ] && echo yes || echo "no ($GONE_MS ms)")"
PL="$(wait_line "$T0" "[photos] refresh (mediastore change)" 10)"
assert_contains "C: the Photos tile logs its refresh — [photos] refresh (mediastore change)" "[photos] refresh (mediastore change)" "$PL"
assert_contains "C: [files] bin delete …/DCIM/Camera/qa-photo-0.png: ok" "[files] bin delete $CAM/qa-photo-0.png: ok" "$(ring_since "$T0")"
media_scan
assert_eq "C: after a second scan the collection still does not list it" "0" "$(img_listed)"
assert_eq "C: …and the file has not come back to DCIM/Camera" "" "$(lsdev /sdcard/DCIM/Camera/qa-photo-0.png)"
assert_eq "C: the binned photo's md5 is unchanged" "$MD5_P" "$(md5dev "$BINP/$(bin_name_of "$CAM/qa-photo-0.png")")"

# ------------------------------------------------------------------------------------------------ D: two at once
log "--- D (r3 D3): search b, select b.bin and sub/b.bin, delete both in one action"
adb shell "printf 'the other b' > /sdcard/QA-Files/sub/b.bin"
MD5_SB="$(md5dev /sdcard/QA-Files/sub/b.bin)"
files_at "$QF"; search_for b d-hits; S d-hits
assert_eq "D: the search lists two rows named b.bin, with their paths" "2 b.bin|sub/b.bin" "$(count_nodes d-hits files_row:b.bin) $(node_texts d-hits files_detail:b.bin)"
T d-hits files_bar:select 1; D d1; tap_nth d1 files_row:b.bin 1; D d2; tap_nth d2 files_row:b.bin 2; D d-sel; S d-sel
assert_eq "D: both are selected (the sort line)" "2 items selected" "$(X d-sel files_sort)"
T d-sel files_sel:delete 1; D d-confirm
record "D: the confirmation for two" "[$(X d-confirm files_dialog_title)] [$(X d-confirm files_dialog_body)]"
M="$(ring_mark)"; T d-confirm files_dialog:ok 2
SL="$(ring_since "$M")"
assert_contains "D: [files] bin delete …/QA-Files/b.bin: ok" "[files] bin delete $QF/b.bin: ok" "$SL"
assert_contains "D: [files] bin delete …/QA-Files/sub/b.bin: ok" "[files] bin delete $QF/sub/b.bin: ok" "$SL"
BN1="$(bin_name_of "$QF/b.bin")"; BN2="$(bin_name_of "$QF/sub/b.bin")"
record "D: the two bin names (index records)" "[$BN1] [$BN2]"
assert_eq "D: TWO bin files end in -b.bin" "2" "$(q "ls $BINP" | grep -cE '^[0-9]+-[0-9]+-b\.bin$')"
assert_ne "D: two index records, with a bin name each" "|" "$BN1|$BN2"
assert_ne "D: …that differ (<seq> or <ms>)" "$BN1" "$BN2"
assert_eq "D: both md5s are present, each under its own record's name" "$MD5_B $MD5_SB" "$(md5dev "$BINP/$BN1") $(md5dev "$BINP/$BN2")"
files_open --es page bin; D d-b0; T d-b0 files_bar:select 1; D d-b1; tap_nth d-b1 files_bin_row:b.bin 1; D d-b2; tap_nth d-b2 files_bin_row:b.bin 2; D d-b3
assert_eq "D: the bin page shows the two rows, both selected" "2 2 items selected" "$(count_nodes d-b3 files_bin_row:b.bin) $(X d-b3 files_sort)"
RM="$(ring_mark)"; T d-b3 files_bin_restore 2
assert_eq "D: each restores to its own path with its own md5" "$MD5_B $MD5_SB" "$(md5dev /sdcard/QA-Files/b.bin) $(md5dev /sdcard/QA-Files/sub/b.bin)"
assert_eq "D: two restore lines" "2" "$(ring_since "$RM" | grep -cE '\[files\] bin restore .*/b\.bin: ok')"

# ------------------------------------------------------------------------------------------------ E: the folder is gone
log "--- E: restore into a deleted folder"
adb shell "cp /sdcard/QA-Files/a.txt /sdcard/QA-Files/sub/in-sub.txt"
bin_it "$QF/sub" in-sub.txt
assert_contains "E: sub/in-sub.txt is binned" "[files] bin delete $QF/sub/in-sub.txt: ok" "$(ring_since "$BIN_MARK")"
cat > "$ROW_DIR/e-rm-sub.sh" <<'SH'
rm -r /sdcard/QA-Files/sub
ls -d /sdcard/QA-Files/sub 2>&1
SH
adb push "$ROW_DIR/e-rm-sub.sh" /data/local/tmp/p18-e4b.sh >/dev/null 2>&1
assert_contains "E: adb shell rm -r sub — the folder is gone" "No such file" "$(q 'sh /data/local/tmp/p18-e4b.sh')"
restore_rows in-sub.txt
assert_contains "E: [files] bin restore …/sub/in-sub.txt: ok" "[files] bin restore $QF/sub/in-sub.txt: ok" "$(ring_since "$RM")"
assert_eq "E: sub/ is made again, with the file (md5)" "/sdcard/QA-Files/sub $MD5_A" "$(lsdev /sdcard/QA-Files/sub) $(md5dev /sdcard/QA-Files/sub/in-sub.txt)"

# ------------------------------------------------------------------------------------------------ F: the conflict
log "--- F: restore over an existing name — skip, keep both, replace"
for ans in skip keep_both replace; do
  k="k-$ans.txt"
  adb shell "printf original > /sdcard/QA-Files/$k"; bin_it "$QF" "$k"; adb shell "printf newer-one > /sdcard/QA-Files/$k"
  binsel "$k"; RM="$(ring_mark)"; T b2 files_bin_restore 1.5; D "f-$ans"; [ "$ans" = skip ] && S "f-$ans"
  assert_eq "F ($ans): the conflict dialog offers exactly replace / keep_both / skip" "files_dialog:replace files_dialog:keep_both files_dialog:skip" \
    "$(ids "f-$ans" | tr ' ' '\n' | grep '^files_dialog:' | xargs)"
  assert_contains "F ($ans): its title names the item" "$k" "$(X "f-$ans" files_dialog_title)"
  T "f-$ans" "files_dialog:$ans" 1.8
  got="$(q "cd /sdcard/QA-Files && for f in k-$ans*; do echo \"\$f=\$(cat \"\$f\")\"; done" | tr '\n' ';')"
  inbin="$(q "ls $BINP" | grep -c -- "-$k\$")"
  case "$ans" in
    skip)      assert_eq "F (skip): the file there is untouched and the binned one stays in the bin" "k-skip.txt=newer-one; 1" "$got $inbin"
               absent_in "F (skip): no restore line (a skipped conflict writes none)" "bin restore $QF/$k" "$(ring_since "$RM")" ;;
    keep_both) assert_eq "F (keep both): the file there is untouched and the restored one is k-keep_both (2).txt; the bin no longer holds it" \
                 "k-keep_both (2).txt=original;k-keep_both.txt=newer-one; 0" "$got $inbin"
               assert_contains "F (keep both): [files] bin restore …/k-keep_both (2).txt: ok" "[files] bin restore $QF/k-keep_both (2).txt: ok" "$(ring_since "$RM")" ;;
    replace)   # Gate build 2 (the review's H3): what Replace displaces goes to the bin, so the bin now holds THAT file.
               assert_eq "F (replace): the restored file took the place of the one there" "k-replace.txt=original;" "$got"
               assert_contains "F (replace): the displaced file goes to the bin with its own line" "[files] bin delete $QF/$k: ok" "$(ring_since "$RM")"
               assert_eq "F (replace): …the bin holds one k-replace.txt, with the displaced file's bytes" "1 newer-one" "$inbin $(q "cat $BINP/*-$k")"
               assert_contains "F (replace): [files] bin restore …/k-replace.txt: ok" "[files] bin restore $QF/k-replace.txt: ok" "$(ring_since "$RM")" ;;
  esac
done

# ------------------------------------------------------------------------------------------------ G: Delete permanently
log "--- G: Delete permanently one row (a delete made on the bin page is permanent)"
BNK="$(bin_name_of "$QF/k-skip.txt")"
N_BEFORE="$(bin_index_count)"
binsel k-skip.txt; T b2 files_bin_delete 1; D g-confirm; S g-confirm
assert_eq "G: the confirmation's title" "Permanently delete this item?" "$(X g-confirm files_dialog_title)"
M="$(ring_mark)"; T g-confirm files_dialog:ok 1.8
assert_contains "G: [files] bin purge …/k-skip.txt: ok" "[files] bin purge $QF/k-skip.txt: ok" "$(ring_since "$M")"
assert_eq "G: gone from the bin folder" "" "$(lsdev "$BINP/$BNK")"
assert_eq "G: gone from the index, and only that one ($N_BEFORE records before)" "0 $((N_BEFORE - 1))" "$(bin_index | grep -c "|$QF/k-skip.txt") $(bin_index_count)"
assert_eq "G: permanent — no copy of it anywhere under .Tessera, and the newer file in QA-Files is untouched" "0 newer-one" \
  "$(q "find /sdcard/.Tessera -name '*k-skip.txt'" | grep -c .) $(q 'cat /sdcard/QA-Files/k-skip.txt')"
D g-after
assert_eq "G: its row is gone from the page" "no" "$(H g-after files_bin_row:k-skip.txt)"

# ------------------------------------------------------------------------------------------------ H: Empty
log "--- H: Empty"
bin_it "$QF" img-1.png
record "H: the bin before Empty (ls -a) / index records" "$(bin_ls) / $(bin_index_count)"
assert_ne "H precondition: the bin holds records" "0" "$(bin_index_count)"
files_open --es page bin; D h0; T h0 files_bar:more 1; D h1; T h1 files_bin_empty 1; D h-confirm; S h-confirm
assert_eq "H: the confirmation's title" "Empty the Recycle Bin?" "$(X h-confirm files_dialog_title)"
record "H: the confirmation's body" "$(X h-confirm files_dialog_body)"
M="$(ring_mark)"; T h-confirm files_dialog:ok 2
assert_contains "H: [files] bin empty /storage/emulated/0: ok" "[files] bin empty $PRIMARY: ok" "$(ring_since "$M")"
assert_eq "H: the bin folder holds only .nomedia and .index.json (ls -a)" ".index.json .nomedia" "$(bin_ls)"
assert_eq "H: …and the index has zero records" "0 0" "$(bin_index_count) $(bin_index | grep -c UNREADABLE)"
D h-after
assert_eq "H: no row is left on the page" "0" "$(grep -c 'files_bin_row:' "$ROW_DIR/h-after.xml")"

# ------------------------------------------------------------------------------------------------ I: the public volume
log "--- I: a delete on the row's own public volume"
adb shell "cp /sdcard/QA-Files/b.bin $V/pv.bin"; sleep 1
assert_eq "I: the file is on the volume (md5)" "$MD5_B" "$(md5dev "$V/pv.bin")"
bin_it "$V" pv.bin
assert_contains "I: [files] bin delete $V/pv.bin: ok" "[files] bin delete $V/pv.bin: ok" "$(ring_since "$BIN_MARK")"
assert_eq "I: it lands in THAT volume's bin (ls $V/.Tessera/bin/)" "1" "$(q "ls $V/.Tessera/bin" | grep -cE '^[0-9]+-[0-9]+-pv\.bin$')"
assert_eq "I: …not in the primary one" "0" "$(q "ls $BINP" | grep -c 'pv\.bin')"
assert_eq "I: the volume's index holds its path" "1" "$(bin_index "$V/.Tessera/bin" | grep -c "|$V/pv.bin")"
files_open --es page bin; D i-bin; S i-bin
record "I: the bin row's detail (a file deleted at the volume's root: the root reads as the volume's name)" "$(X i-bin files_detail:pv.bin)"
assert_contains "I: the bin page lists it with the volume's name" "Virtual SD card" "$(X i-bin files_detail:pv.bin)"
restore_rows pv.bin
assert_contains "I: [files] bin restore $V/pv.bin: ok" "[files] bin restore $V/pv.bin: ok" "$(ring_since "$RM")"
assert_eq "I: Restore puts the file back on that volume (md5), and its bin no longer lists it" "$MD5_B 0" "$(md5dev "$V/pv.bin") $(q "ls $V/.Tessera/bin" | grep -c 'pv\.bin')"
assert_eq "I: …and nothing of it reached the primary volume" "" "$(q "find /sdcard/QA-Files /sdcard/.Tessera -name '*pv.bin'")"
adb shell input keyevent KEYCODE_HOME; sleep 1
pubvol_down

# ------------------------------------------------------------------------------------------------ J: 255 bytes
log "--- J (r3 D3): a file whose name is 255 bytes long"
N255="$(python3 -c "print('x' * 251 + '.txt')")"
adb shell "printf 'a 255-byte name' > '/sdcard/QA-Files/$N255'"
assert_eq "J: the name is 255 bytes and the file exists" "255 1" "$(printf '%s' "$N255" | LC_ALL=C wc -c | xargs) $(q "ls /sdcard/QA-Files" | grep -cx "$N255")"
MD5_N="$(md5dev "/sdcard/QA-Files/$N255")"
bin_it "$QF" "$N255"
assert_contains "J: [files] bin delete …/<255-byte name>: ok" "[files] bin delete $QF/$N255: ok" "$(ring_since "$BIN_MARK")"
BNN="$(bin_name_of "$QF/$N255")"
record "J: the bin name's length in bytes / its head and tail" "$(printf '%s' "$BNN" | LC_ALL=C wc -c | xargs) / ${BNN:0:24}…${BNN: -8}"
assert_ne "J: the index records a bin name for it" "" "$BNN"
assert_eq "J: the bin name is at most 255 bytes" "yes" "$([ "$(printf '%s' "$BNN" | LC_ALL=C wc -c)" -le 255 ] && echo yes || echo no)"
assert_eq "J: the bin file exists under that name with the md5" "$MD5_N" "$(md5dev "$BINP/$BNN")"
assert_eq "J: gone from QA-Files" "0" "$(q "ls /sdcard/QA-Files" | grep -cx "$N255")"
restore_rows "$N255"
assert_contains "J: [files] bin restore …/<255-byte name>: ok" "[files] bin restore $QF/$N255: ok" "$(ring_since "$RM")"
assert_eq "J: Restore gives back the full original name with the md5 unchanged" "1 $MD5_N" "$(q "ls /sdcard/QA-Files" | grep -cx "$N255") $(md5dev "/sdcard/QA-Files/$N255")"

# ------------------------------------------------------------------------------------------------ K: negatives
log "--- K: negatives"
adb shell "printf gone > /sdcard/QA-Files/neg.txt"; sleep 0.5
cat > "$ROW_DIR/k-rm.sh" <<'SH'
rm /sdcard/QA-Files/neg.txt
SH
adb push "$ROW_DIR/k-rm.sh" /data/local/tmp/p18-e4b.sh >/dev/null 2>&1; q 'sh /data/local/tmp/p18-e4b.sh' >/dev/null
bin_it "$QF" img-0.png
R0="$(recent_json)"
files_open --es page bin; D k-bin
assert_eq "K: a file removed with adb shell rm never appears in the bin (folder, index, page)" "0 0 no" \
  "$(q "ls -a $BINP" | grep -c 'neg\.txt') $(bin_index | grep -c 'neg\.txt') $(H k-bin files_bin_row:neg.txt)"
assert_eq "K: (the control) the file deleted in Files is on the page" "yes" "$(H k-bin files_bin_row:img-0.png)"
M="$(ring_mark)"; T k-bin files_bin_row:img-0.png 2
KS="$(ring_since "$M")"
assert_eq "K: a tap on a bin row opens nothing — Files stays on top" "$FILES_ACTIVITY" "$(top_activity)"
D k-tapped
assert_eq "K: …still the bin page" "Recycle Bin" "$(X k-tapped files_crumb:0)"
if printf '%s\n' "$KS" | grep -q 'wall='; then assert_absent "K: …and no [files] recent add" "recent add" "$KS"
else assert_eq "K: …and no [files] recent add (the slice holds no line at all: nothing was logged)" "" "$(printf '%s' "$KS" | grep -F 'recent add')"; fi
assert_eq "K: the private Recent store is unchanged by the tap" "$R0" "$(recent_json)"
files_open --es page recent; D k-recent
assert_eq "K: no bin file is on the Recent page" "0" "$(grep -o 'files_recent_row:[^"]*' "$ROW_DIR/k-recent.xml" | grep -cE '^files_recent_row:[0-9]+-[0-9]+-|img-0\.png')"
files_at "$PRIMARY"; D k-root
assert_eq "K: the listing of /sdcard does not show .Tessera (dot-folders hidden), though it is there" "no /sdcard/.Tessera" "$(H k-root files_row:.Tessera) $(lsdev /sdcard/.Tessera)"
assert_contains "K: (the control) that listing is /sdcard's" "DCIM|" "$(row_names k-root)"

# ------------------------------------------------------------------------------------------------ L: full volume
log "--- L: full volume (fill_volume 1048576)"
MD5_I2="$(fx_md5 img-2.png)"
if fill_volume 1048576; then
  _verdict PASS "L: fill_volume 1048576 (its own assert: free <= 1 MB + 5 MB)" "$(q 'df -k /sdcard' | awk 'NR==2 { print $4 " KB free" }')"
  bin_it "$QF" img-2.png
  assert_contains "L: [files] bin delete …/img-2.png: ok on the full volume" "[files] bin delete $QF/img-2.png: ok" "$(ring_since "$BIN_MARK")"
  assert_eq "L: it moved to the bin (ls; md5)" " $MD5_I2" "$(lsdev /sdcard/QA-Files/img-2.png) $(md5dev "$BINP/$(bin_name_of "$QF/img-2.png")")"
else
  _verdict FAIL "L: fill_volume 1048576" "its precondition failed (above); the leg was not run"
fi
unfill_volume
assert_eq "L: unfill_volume — fill.bin is gone" "" "$(lsdev /sdcard/fill.bin)"
record "L: free on /sdcard after unfill_volume" "$(q 'df -k /sdcard' | awk 'NR==2 { print $4 " KB" }')"
assert_eq "L: awake after the adb root / unroot of the fill (wake_device)" "Awake" "$(wake_device)"

# ------------------------------------------------------------------------------------------------ M: rm -r .Tessera
log "--- M: adb shell rm -r /sdcard/.Tessera, then a delete"
cat > "$ROW_DIR/m-rm.sh" <<'SH'
rm -r /sdcard/.Tessera
ls -d /sdcard/.Tessera 2>&1
SH
adb push "$ROW_DIR/m-rm.sh" /data/local/tmp/p18-e4b.sh >/dev/null 2>&1
assert_contains "M: rm -r /sdcard/.Tessera — the bin is gone" "No such file" "$(q 'sh /data/local/tmp/p18-e4b.sh')"
bin_it "$QF" img-3.png
SL="$(ring_since "$BIN_MARK")"
assert_contains "M: [files] bin index /storage/emulated/0: rebuilt (bin folder missing)" "[files] bin index $PRIMARY: rebuilt (bin folder missing)" "$SL"
assert_contains "M: the delete is ok" "[files] bin delete $QF/img-3.png: ok" "$SL"
assert_eq "M: the bin is made again: .nomedia, .index.json and the file" "1 1 1" \
  "$(q "ls -a $BINP" | grep -cx '.nomedia') $(q "ls -a $BINP" | grep -cx '.index.json') $(q "ls $BINP" | grep -cE '^[0-9]+-[0-9]+-img-3\.png$')"

# ------------------------------------------------------------------------------------------------ N: the bin path is a FILE
log "--- N (r3 V14): the bin path is a FILE"
cat > "$ROW_DIR/n-file.sh" <<'SH'
rm -r /sdcard/.Tessera; : > /sdcard/.Tessera
ls -la /sdcard/.Tessera
SH
adb push "$ROW_DIR/n-file.sh" /data/local/tmp/p18-e4b.sh >/dev/null 2>&1
NF="$(q 'sh /data/local/tmp/p18-e4b.sh')"
assert_eq "N: /sdcard/.Tessera is a regular file" "-" "${NF:0:1}"
bin_it "$QF" a.txt
SL="$(ring_since "$BIN_MARK")"
D n-failed; S n-failed
FAILLINE="$(printf '%s\n' "$SL" | grep -o "\[files\] bin delete $QF/a.txt: failed .*" | sed 's/ *wall=.*//' | head -1)"
record "N: the line" "$FAILLINE"
assert_contains "N: [files] bin delete …/a.txt: failed <why>" "[files] bin delete $QF/a.txt: failed " "$FAILLINE "
assert_ne "N: …with a reason" "[files] bin delete $QF/a.txt: failed" "$(printf '%s' "$FAILLINE" | sed 's/ *$//')"
assert_ne "N: files_error is shown" "" "$(X n-failed files_error)"
record "N: files_error's text" "$(X n-failed files_error)"
assert_eq "N: a.txt is still present with its md5 unchanged, and still listed" "$MD5_A yes" "$(md5dev /sdcard/QA-Files/a.txt) $(H n-failed files_row:a.txt)"
cat > "$ROW_DIR/n-restore.sh" <<'SH'
[ -f /sdcard/.Tessera ] && rm /sdcard/.Tessera
ls -d /sdcard/.Tessera 2>&1
SH
adb push "$ROW_DIR/n-restore.sh" /data/local/tmp/p18-e4b.sh >/dev/null 2>&1
assert_contains "N: the file /sdcard/.Tessera is removed again (RV12)" "No such file" "$(q 'sh /data/local/tmp/p18-e4b.sh')"

# ------------------------------------------------------------------------------------------------ O: uninstall
log "--- O: adb uninstall app.tileshell — the bin stays; reinstall through provision.sh with the same gate APK"
bin_it "$QF" img-4.png
BIN_BEFORE="$(q "ls -a $BINP" | grep -v '^\.\.\?$' | LC_ALL=C sort | tr '\n' ' ')"
MD5_I4="$(md5dev "$BINP/$(bin_name_of "$QF/img-4.png")")"
assert_eq "O precondition: the bin holds img-4.png (md5 the fixture's)" "$(fx_md5 img-4.png)" "$MD5_I4"
INDEX_MD5="$(md5dev "$BINP/.index.json")"
rings_save
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
adb uninstall app.tileshell > "$ROW_DIR/o-uninstall.out" 2>&1; echo $? > "$ROW_DIR/o-uninstall.rc"
assert_eq "O: adb uninstall app.tileshell (exit code, output)" "0 Success" "$(cat "$ROW_DIR/o-uninstall.rc") $(tr -d '\r' < "$ROW_DIR/o-uninstall.out" | tail -1)"
assert_eq "O: the package is gone" "" "$(q 'pm path app.tileshell')"
assert_eq "O: ls /sdcard/.Tessera/bin/ still lists the binned files" "$BIN_BEFORE" "$(q "ls -a $BINP" | grep -v '^\.\.\?$' | LC_ALL=C sort | tr '\n' ' ')"
assert_eq "O: …with the file's md5 and the index's md5 unchanged" "$MD5_I4 $INDEX_MD5" "$(q "md5sum $BINP/*img-4.png" | cut -d' ' -f1) $(md5dev "$BINP/.index.json")"
assert_eq "O: the APK provision.sh installs is the gate candidate (the file's md5)" "$GATE_APK_MD5" "$(md5sum "$APK" | cut -d' ' -f1)"
TMPDIR="$SCRATCH" bash "$PROVISION" > "$ROW_DIR/o-provision.out" 2>&1; echo $? > "$ROW_DIR/o-provision.rc"
assert_eq "O: provision.sh exit code" "0" "$(cat "$ROW_DIR/o-provision.rc")"
assert_gate_apk "O: after the reinstall the installed APK is the gate candidate"
assert_contains "O: All-files access is held again (provision.sh's line)" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
assert_eq "O: awake" "Awake" "$(wake_device)"
ROW_MARK_O="$(ring_mark)"
baseline_start
files_open --es page bin; D o-bin; S o-bin
assert_eq "O: the reinstalled app's bin page lists the file binned before the uninstall" "yes" "$(H o-bin files_bin_row:img-4.png)"
restore_rows img-4.png
assert_eq "O: …and restores it (md5)" "$(fx_md5 img-4.png)" "$(md5dev /sdcard/QA-Files/img-4.png)"

# ------------------------------------------------------------------------------------------------ the end
assert_eq "no crash of the shell since the row began (AndroidRuntime)" "0" "$(crash)"
adb shell input keyevent KEYCODE_HOME; sleep 1
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
assert_contains "end: All-files access is held" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
assert_eq "end: no virtual disk, no QA-Files, no .Tessera" "" "$(q 'sm list-disks' | xargs)$(q 'ls -d /sdcard/QA-Files /sdcard/.Tessera 2>/dev/null' | xargs)"
row_end
