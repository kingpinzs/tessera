#!/usr/bin/env bash
# Phase 18 — round 2's DEVICE EXPERIMENTS OWED (BUILD-NOTES "2026-10-07 06:25: round 2 merged", items 1-7), plus the
# round-3 adversarial pass's D2 / D3 / D5 / D6 (the lead, 2026-10-20). Row R2_EXP, run on the gate build; each leg
# asserts the SAFE outcome stated there. Legs can be named: r2_exp.sh [1 2 3 4 5 6 7 D2 D3 D5 D6].
#
#   1   path extra /sdcard/.Teßera/bin → `[files] open ignored: inside the shell's own folder` (folds), else open ignored
#   2   a .Teßera.zip holding bin/.index.json extracted in Files → "failed inside the shell's own folder"; the index
#       unchanged; again with no .Tessera present → the same refusal (an empty .Tessera then exists)
#   3   .Teßera/bin in place of .Tessera; delete a file → "A folder named .Teßera is in the way…", the file stays
#   4   a folder onto a same-named FILE, Replace, Cancel mid-copy → the file still there, no bin row, no .part
#   5   a same-volume file-over-file move with Replace → the old file is a bin row
#   6   .Tessera as a FILE; delete a file → "A file named .Tessera is in the way on This Device…"
#   7   a copy and a move on FUSE complete; a failed rename leaves no 0-byte placeholder (N13's exclusive create — the
#       one assumption no JVM test can check). Identity of two folded names is leg D2's (kept both, no duplicate).
#   D2  stat dev:inode of .Tessera / .Teßera / .Tess<ZWSP>era / .TESSERA (same or "No such file", RECORD which); New
#       folder .Teßera at a root → "inside the shell's own folder"; the same on the public volume (TESSER~1, .Tessera.)
#   D3  a no-replace rename / folder rename / folder move through MediaProvider on /sdcard and the public volume →
#       each Done, no 0-byte leftover, MediaStore shows exactly one b.txt row and no a.txt row
#   D5  after renaming a file at the public volume's root: /storage/<UUID>/.Tessera exists by design (RECORD), hidden in
#       Files, and MediaStore holds no .Tessera row
#   D6  a staged folder-over-file Replace under a kill → the old file still in place, no .<name>.*.part after the sweep,
#       the source intact
#
# The real paths in the ring's lines are /storage/emulated/0/… (BUILD-NOTES pure layer 2). No host audio is touched.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
LEGS="${*:-1 2 3 4 5 6 7 D2 D3 D5 D6}"
# The kernel caches each SPELLING of a folder as its own FUSE dentry: once `.Teßera` (or `.Tess<ZWSP>era`) has been looked
# up, removing `.Tessera` leaves that spelling answering with the gone folder's inode, which the next mkdir may reuse —
# and the reverse. Found on this AVD 2026-10-20 (RESUME-2026-10-20/runner2/probe-r2/stale-alias*.txt; R2_EXP's first run:
# leg 2's e2b conflict dialog, leg 4's copy of a fresh folder refused "inside the shell's own folder", leg 5's "A folder
# named .Tessera is in the way" with nothing named so on disk, D2's second inode and "the name is taken"). In a fresh
# process with fresh caches the shell refused leg 3's folded bin as written (probe-r2/probe3*.log). So every adb-side
# change to the shell folder's names is followed by a drop of the kernel's dentry / inode caches (root on this
# userdebug AVD; `echo 2` leaves the page cache alone), and every experiment starts on fresh ones.
fuse_fresh() { adb shell "su 0 sh -c 'echo 2 > /proc/sys/vm/drop_caches'" >/dev/null 2>&1; }
leg() { case " $LEGS " in *" $1 "*) log "--- experiment $1 ($(date '+%H:%M:%S'))"; fuse_fresh; return 0 ;; *) return 1 ;; esac; }
PRIMARY=/storage/emulated/0
SD=/sdcard
# The folded spellings pass 2's probe (n2probe.out) showed this AVD's /sdcard resolves onto .Tessera / Android.
TSS=".Te$(printf '\303\237')era"           # .Teßera (ß = U+00DF)
TZWSP=".Tess$(printf '\342\200\213')era"    # .Tess<U+200B>era (zero-width space)
TUPPER=".TESSERA"
# The hidden-files setting SET to a state, never toggled blind (2026-10-20: in R2_EXP's first run one toggle landed under
# leg 2's conflict dialog and missed, the setting stayed on, and the next run's first toggle hid the zip — "the zip is
# listed: no"). The state is read from the toggle's `checked`, tapped only when it differs, and asserted after.
hidden_now() { files_at "$PRIMARY"; D ht0; T ht0 files_bar:more 1; D ht1; T ht1 files_more:settings 1.5; D ht2
  grep -o '<node[^>]*resource-id="files_setting_hidden"[^>]*>' "$ROW_DIR/ht2.xml" | grep -o 'checked="[a-z]*"' | head -1 | cut -d'"' -f2; }
hidden_set() { # on | off
  local want=true; [ "$1" = off ] && want=false
  [ "$(hidden_now)" != "$want" ] && { T ht2 files_setting_hidden 1; D ht2; }
  assert_eq "2: the hidden-files setting is $1 (files_setting_hidden checked)" "$want" "$(grep -o '<node[^>]*resource-id="files_setting_hidden"[^>]*>' "$ROW_DIR/ht2.xml" | grep -o 'checked="[a-z]*"' | head -1 | cut -d'"' -f2)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
}

keep_earlier_run R2_EXP
row_begin R2_EXP "round 2's device experiments ($LEGS), each asserting its stated safe outcome"
assert_gate_apk
baseline_start
adb logcat -c
files_up paced big || { row_end; exit 1; }
ensure_start

# ------------------------------------------------------------------------------------------------ folding census (before leg 1)
# Pass 2's device experiment 1, made a recorded precondition: do the folded names resolve onto .Tessera on THIS /sdcard?
# The legs that depend on it branch on FOLDS and say which outcome they assert.
adb shell "mkdir -p $SD/.Tessera/bin" >/dev/null 2>&1
FOLD_SS="$(lsdev "$SD/$TSS")"; FOLD_ZWSP="$(lsdev "$SD/$TZWSP")"; FOLD_UPPER="$(lsdev "$SD/$TUPPER")"
FOLDS=no; [ -n "$FOLD_SS" ] && FOLDS=yes
record "this AVD's /sdcard folds names (ls -d .Teßera / .Tess<ZWSP>era / .TESSERA after mkdir .Tessera)" \
  "folds=$FOLDS [.Teßera=${FOLD_SS:-no such file}] [.Tess<ZWSP>era=${FOLD_ZWSP:-no such file}] [.TESSERA=${FOLD_UPPER:-no such file}]"
adb shell "rm -rf $SD/.Tessera" >/dev/null 2>&1; fuse_fresh

# ------------------------------------------------------------------------------------------------ 1
if leg 1; then
  adb shell "mkdir -p $SD/.Tessera/bin" >/dev/null 2>&1
  M="$(ring_mark)"; files_at "$SD/$TSS/bin"; sleep 1
  SL="$(ring_since "$M")"
  record "1: the open-ignored line for /sdcard/.Teßera/bin" "$(printf '%s\n' "$SL" | grep -o '\[files\] open ignored.*' | sed 's/ *wall=.*//' | head -1)"
  if [ "$FOLDS" = yes ]; then
    assert_contains "1: SAFE (folds) — [files] open ignored: inside the shell's own folder" "[files] open ignored: inside the shell's own folder" "$SL"
  else
    assert_contains "1: SAFE (no fold) — the path was ignored, Files did not open the bin" "[files] open ignored:" "$SL"
  fi
  D e1; assert_absent "1: Files is not showing the bin's contents (no .index.json row)" "files_row:.index.json" "$(cat "$ROW_DIR/e1.xml")"
  adb shell "rm -rf $SD/.Tessera" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 2
if leg 2; then
  # A bin to prove the index is left untouched, and the .Teßera.zip the extract must refuse.
  bin_it "$QF" img-0.png
  IDX0="$(md5dev "$BINP/.index.json")"; assert_ne "2: a bin index exists to compare" "" "$IDX0"
  python3 - "$ROW_DIR/Tessera.zip" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("bin/.index.json", '{"version":1,"records":[{"bin":"1-1-planted.txt","path":"/storage/emulated/0/DCIM/planted.txt","deletedAt":1,"size":7}]}')
    z.writestr("bin/.nomedia", "")
PY
  extract_at_root() { # dump-prefix
    hidden_set on
    files_at "$PRIMARY"; scroll_to_node "$ROW_DIR/$1-0.xml" "files_row:$TSS.zip" >/dev/null
    assert_eq "2 ($1): the zip is listed at the root (hidden shown)" "yes" "$(H "$1-0" "files_row:$TSS.zip")"
    M="$(ring_mark)"; T "$1-0" "files_row:$TSS.zip" 2; D "$1-1"
    if [ "$(H "$1-1" files_extract)" = yes ]; then T "$1-1" files_extract 3; wait_op "$M" 15 >/dev/null; D "$1-2"; else cp "$ROW_DIR/$1-1.xml" "$ROW_DIR/$1-2.xml"; fi
    RSL="$(ring_since "$M")"
    record "2 ($1): the extract line / the page error" "$(printf '%s\n' "$RSL" | grep -o '\[files\] zip extract.*' | sed 's/ *wall=.*//' | tr '\n' '|') / [$(X "$1-2" files_error)]"
    # A dialog left open (run 1: a conflict question) is dismissed first, so the setting's page is reached.
    if [ "$(H "$1-2" files_dialog)" = yes ]; then note "2 ($1): a dialog was open after the extract: [$(X "$1-2" files_dialog_title)] — dismissed with Back"; adb shell input keyevent KEYCODE_BACK; sleep 1; fi
    hidden_set off
  }
  adb push "$ROW_DIR/Tessera.zip" "$SD/$TSS.zip" >/dev/null 2>&1
  assert_eq "2: /sdcard/.Teßera.zip is pushed" "$SD/$TSS.zip" "$(lsdev "$SD/$TSS.zip")"
  extract_at_root e2a
  if [ "$FOLDS" = yes ]; then
    assert_contains "2: SAFE — the extract failed inside the shell's own folder" "failed inside the shell's own folder" "$RSL"
  else
    assert_contains "2: SAFE (no fold) — the extract did not complete over the shell folder" "failed " "$RSL"
  fi
  assert_eq "2: the bin index is unchanged" "$IDX0" "$(md5dev "$BINP/.index.json")"
  assert_eq "2: nothing planted under .Tessera" "0" "$(q "find $SD/.Tessera -name '*planted*' 2>/dev/null" | grep -c .)"
  # Again with no .Tessera present: the same refusal, and an empty .Tessera is left (anchor made it to compare).
  adb shell "rm -rf $SD/.Tessera" >/dev/null 2>&1; fuse_fresh
  extract_at_root e2b
  if [ "$FOLDS" = yes ]; then
    assert_contains "2 (no .Tessera): SAFE — the same refusal, inside the shell's own folder" "failed inside the shell's own folder" "$RSL"
    record "2 (no .Tessera): what stands at /sdcard/.Tessera afterwards (an empty .Tessera may appear)" "[$(bin_ls "$SD/.Tessera")]"
  else
    record "2 (no fold, no .Tessera): the extract line" "$(printf '%s\n' "$RSL" | grep -o '\[files\] zip extract.*' | sed 's/ *wall=.*//' | tr '\n' '|')"
  fi
  adb shell "rm -f $SD/$TSS.zip; rm -rf $SD/.Tessera" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 3
if leg 3; then
  adb shell "rm -rf $SD/.Tessera; mkdir -p \"$SD/$TSS/bin\"" >/dev/null 2>&1; fuse_fresh
  adb shell "printf keep3 > $QF/d3keep.txt" >/dev/null 2>&1
  MD5_K="$(md5dev "$QF/d3keep.txt")"
  files_at "$QF"; scroll_to_node "$ROW_DIR/e3-0.xml" files_row:d3keep.txt >/dev/null; hold e3-0 files_row:d3keep.txt; D e3-1; T e3-1 files_hold:delete 1; D e3-2
  M="$(ring_mark)"; T e3-2 files_dialog:ok 2; D e3-3
  SL="$(ring_since "$M")"
  record "3: the bin-delete line / the page error" "$(printf '%s\n' "$SL" | grep -o '\[files\] bin delete.*' | sed 's/ *wall=.*//' | head -1) / [$(X e3-3 files_error)]"
  if [ "$FOLDS" = yes ]; then
    assert_contains "3: SAFE (folds) — the page names the obstacle: A folder named .Teßera is in the way" "A folder named $TSS is in the way" "$(X e3-3 files_error)"
    assert_eq "3: SAFE — the file stays (its md5 is unchanged)" "$MD5_K" "$(md5dev "$QF/d3keep.txt")"
  else
    record "3 (no fold): the delete outcome (no .Tessera obstacle folds; delete may succeed)" "page=[$(X e3-3 files_error)] file=$(lsdev "$QF/d3keep.txt")"
  fi
  adb shell "rm -rf \"$SD/$TSS\"; rm -f $QF/d3keep.txt" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 4
if leg 4; then
  # A folder d4 holding big.bin (a copy long enough to cancel, paced), and a same-named FILE d4 at the destination sub/.
  adb shell "mkdir -p $QF/src/d4 && cp $QF/big.bin $QF/src/d4/big.bin && printf 'the old file' > $QF/sub/d4" >/dev/null 2>&1
  MD5_FILE="$(md5dev "$QF/sub/d4")"
  files_at "$QF/src"; M="$(ring_mark)"
  pick_op copy d4 .. sub
  sleep 2; D e4-conf
  if [ "$(H e4-conf files_dialog:replace)" = yes ]; then T e4-conf files_dialog:replace 1; fi
  mid_progress copy "$M" 25 > "$ROW_DIR/e4-mid.txt" || true
  if notif_cancel e4; then _verdict PASS "4: the copy's Cancel action was tapped" "e4-shade*.xml"
  else _verdict FAIL "4: the copy's Cancel action was tapped" "no Cancel in the shade"; fi
  L="$(wait_op "$M" 20)"; SL="$(ring_since "$M")"
  record "4: the copy's end line / the page" "$(printf '%s' "$L" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
  assert_eq "4: SAFE — the same-named FILE sub/d4 is still there with its old bytes" "$MD5_FILE" "$(md5dev "$QF/sub/d4")"
  assert_eq "4: SAFE — the file is not a folder now (the Replace did not land)" "regular file" "$(q "stat -c %F $QF/sub/d4" | head -1)"
  assert_eq "4: SAFE — no bin row for the displaced d4" "" "$(bin_name_of "$QF/sub/d4")"
  assert_eq "4: SAFE — no .part temp left in sub" "" "$(temps_in "$QF/sub")"
  adb shell "rm -rf $QF/src $QF/sub/d4" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 5
if leg 5; then
  adb shell "printf 'destination a' > $QF/sub/a.txt; printf 'source a 12345' > $QF/a.txt" >/dev/null 2>&1
  MD5_OLD="$(md5dev "$QF/sub/a.txt")"; MD5_SRC="$(md5dev "$QF/a.txt")"
  assert_ne "5: the two a.txt differ" "$MD5_OLD" "$MD5_SRC"
  files_at "$QF"; M="$(ring_mark)"
  pick_op move a.txt sub
  sleep 2; D e5-conf
  if [ "$(H e5-conf files_dialog:replace)" = yes ]; then T e5-conf files_dialog:replace 1.5; fi
  wait_op "$M" 20 >/dev/null; SL="$(ring_since "$M")"
  record "5: the move / bin-delete lines" "$(printf '%s\n' "$SL" | grep -oE '\[files\] (move|bin delete).*' | sed 's/ *wall=.*//' | tr '\n' '|')"
  assert_eq "5: SAFE — the moved file is in sub with the source's bytes" "$MD5_SRC" "$(md5dev "$QF/sub/a.txt")"
  BN="$(bin_name_of "$QF/sub/a.txt")"
  assert_ne "5: SAFE — the OLD a.txt is a bin row (its original path is recorded)" "" "$BN"
  [ -n "$BN" ] && assert_eq "5: …and the binned file has the old bytes" "$MD5_OLD" "$(md5dev "$BINP/$BN")"
  adb shell "rm -f $QF/sub/a.txt" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 6
if leg 6; then
  adb shell "rm -rf $SD/.Tessera; printf x > $SD/.Tessera" >/dev/null 2>&1; fuse_fresh
  adb shell "printf keep6 > $QF/d6keep.txt" >/dev/null 2>&1
  MD5_K="$(md5dev "$QF/d6keep.txt")"
  files_at "$QF"; scroll_to_node "$ROW_DIR/e6-0.xml" files_row:d6keep.txt >/dev/null; hold e6-0 files_row:d6keep.txt; D e6-1; T e6-1 files_hold:delete 1; D e6-2
  M="$(ring_mark)"; T e6-2 files_dialog:ok 2; D e6-3
  record "6: the bin-delete line / the page error" "$(ring_since "$M" | grep -o '\[files\] bin delete.*' | sed 's/ *wall=.*//' | head -1) / [$(X e6-3 files_error)]"
  assert_contains "6: SAFE — A file named .Tessera is in the way on This Device" "A file named .Tessera is in the way on This Device" "$(X e6-3 files_error)"
  assert_eq "6: SAFE — the file stays" "$MD5_K" "$(md5dev "$QF/d6keep.txt")"
  adb shell "rm -f $SD/.Tessera $QF/d6keep.txt" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ 7
if leg 7; then
  # A copy and a move on FUSE complete (md5 kept); a no-replace rename onto an existing name fails and leaves no 0-byte
  # placeholder (N13's exclusive create). Identity of two folded names is leg D2's.
  adb shell "mkdir -p $QF/f7 && printf '7copy' > $QF/f7/c.txt && printf '7move' > $QF/f7/m.txt && printf 'blocker' > $QF/f7/exists.txt" >/dev/null 2>&1
  MD5_C="$(md5dev "$QF/f7/c.txt")"; MD5_M="$(md5dev "$QF/f7/m.txt")"; MD5_X="$(md5dev "$QF/f7/exists.txt")"
  files_at "$QF/f7"; M="$(ring_mark)"; pick_op copy c.txt .. sub
  wait_op "$M" 20 >/dev/null
  assert_eq "7: a copy on FUSE completes (md5 kept)" "$MD5_C" "$(md5dev "$QF/sub/c.txt")"
  files_at "$QF/f7"; M="$(ring_mark)"; pick_op move m.txt .. sub   # the picker opens in f7: up to QA-Files first (as the copy above)
  wait_op "$M" 20 >/dev/null
  assert_eq "7: a move on FUSE completes (md5 kept, source gone)" "$MD5_M|" "$(md5dev "$QF/sub/m.txt")|$(lsdev "$QF/f7/m.txt")"
  # A no-replace rename onto an existing name: rename c (already in f7 as exists.txt's neighbour) — rename m.txt is gone,
  # so rename c.txt (still in f7) to exists.txt (which exists) through the UI; it must fail and leave no 0-byte file.
  BEFORE="$(q "ls -a $QF/f7" | xargs)"
  files_at "$QF/f7"; scroll_to_node "$ROW_DIR/e7-0.xml" files_row:c.txt >/dev/null; hold e7-0 files_row:c.txt; D e7-1; T e7-1 files_hold:rename 1.5; D e7-2
  dialog_type exists.txt; D e7-3; M="$(ring_mark)"; T e7-3 files_dialog:ok 2; D e7-4
  record "7: the rename-onto-existing line / dialog error" "$(ring_since "$M" | grep -o '\[files\] rename.*' | sed 's/ *wall=.*//' | head -1) / [$(X e7-4 files_error)]"
  assert_eq "7: SAFE — the blocker keeps its bytes (the rename did not replace it)" "$MD5_X" "$(md5dev "$QF/f7/exists.txt")"
  assert_eq "7: SAFE — the source c.txt is untouched" "$MD5_C" "$(md5dev "$QF/f7/c.txt")"
  assert_eq "7: SAFE — no 0-byte placeholder was left (ls -a unchanged)" "$BEFORE" "$(q "ls -a $QF/f7" | xargs)"
  assert_eq "7: SAFE — no rename/part temp under f7" "" "$(temps_in "$QF/f7")"
  adb shell "rm -rf $QF/f7 $QF/sub/c.txt $QF/sub/m.txt" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ D2 (identity, and New folder refusals)
if leg D2; then
  adb shell "mkdir -p $SD/.Tessera/bin" >/dev/null 2>&1; fuse_fresh
  statline() { q "stat -c '%d:%i' \"$1\" 2>/dev/null"; }
  REAL="$(statline "$SD/.Tessera")"
  for pair in ".Tessera:$SD/.Tessera" ".Teßera:$SD/$TSS" ".Tess<ZWSP>era:$SD/$TZWSP" ".TESSERA:$SD/$TUPPER"; do
    nm="${pair%%:*}"; p="${pair#*:}"; di="$(statline "$p")"
    if [ -z "$di" ]; then record "D2: $nm dev:inode" "No such file"
    elif [ "$di" = "$REAL" ]; then record "D2: $nm dev:inode == .Tessera's ($di)" "same identity (folds)"
    else record "D2: $nm dev:inode" "$di (a DIFFERENT file from .Tessera — unexpected on this AVD)"; fi
    if [ -n "$di" ]; then assert_eq "D2: SAFE — $nm is either absent or the SAME file as .Tessera (never a second one)" "$REAL" "$di"; fi
  done
  # New folder .Teßera at /sdcard's root in Files → refused "inside the shell's own folder".
  newfolder_refused() { # root-path volume-label dump-prefix name
    files_at "$1"; D "$3-0"; T "$3-0" files_bar:new_folder 1.5; D "$3-1"
    dialog_type "$4"; D "$3-2"; M="$(ring_mark)"; T "$3-2" files_dialog:ok 2; D "$3-3"
    NFSL="$(ring_since "$M")"
    record "D2: new folder $4 at $2 — ring / dialog error" "$(printf '%s\n' "$NFSL" | grep -o '\[files\] new folder.*' | sed 's/ *wall=.*//' | head -1) / [$(X "$3-3" files_error)]"
  }
  adb shell "rm -rf $SD/.Tessera" >/dev/null 2>&1; fuse_fresh
  newfolder_refused "$PRIMARY" "This Device" d2nf "$TSS"
  assert_contains "D2: SAFE — New folder .Teßera at the volume root is refused inside the shell's own folder" "new folder" "$NFSL"
  assert_contains "D2: …with the shell-folder reason" "inside the shell's own folder" "$NFSL"
  if [ "$(H d2nf-3 files_dialog)" = yes ]; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi
  # The same on the public volume (a vfat card): TESSER~1 (the 8.3 alias) and .Tessera. (trailing dot).
  if pubvol_up >/dev/null; then
    sleep 2; V="$PUBVOL_PATH"; VLABEL="Virtual SD card"
    for nm in "$TSS" "TESSER~1" ".Tessera."; do
      newfolder_refused "$V" "$VLABEL" "d2pv-$(printf '%s' "$nm" | tr -c 'A-Za-z0-9' _)" "$nm"
      record "D2 (public volume): new folder [$nm] — ring / page" "$(printf '%s\n' "$NFSL" | grep -o '\[files\] new folder.*' | sed 's/ *wall=.*//' | head -1) / [$(X "d2pv-$(printf '%s' "$nm" | tr -c 'A-Za-z0-9' _)-3" files_error)]"
      if [ "$(H "d2pv-$(printf '%s' "$nm" | tr -c 'A-Za-z0-9' _)-3" files_dialog)" = yes ]; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi
    done
    pubvol_down
  else
    record "D2: the public volume for the vfat alias checks" "pubvol_up failed; TESSER~1 / .Tessera. not run"
  fi
  adb shell "rm -rf $SD/.Tessera" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ D3 (no-replace rename through MediaProvider)
if leg D3; then
  mediastore_rows() { q "content query --uri content://media/external/file --projection _data --where \"_data LIKE '%$1'\"" | grep -c "_data="; }
  rename_file_ui() { # folder old new
    files_at "$1"; scroll_to_node "$ROW_DIR/.d3r.xml" "files_row:$2" >/dev/null; hold .d3r "files_row:$2"; D .d3r1; T .d3r1 files_hold:rename 1.5; D .d3r2
    dialog_type "$3"; D .d3r3; M="$(ring_mark)"; T .d3r3 files_dialog:ok 2
    wait_line "$M" "[files] rename" 8 >/dev/null
  }
  d3_on_volume() { # root label
    local root="$1" label="$2" pfx; pfx="$(printf '%s' "$label" | tr -c 'A-Za-z0-9' _)"
    adb shell "mkdir -p $root/D3/f1 && printf d3 > $root/D3/a.txt && printf d3f > $root/D3/f1/inner.txt && mkdir -p $root/D3/f2" >/dev/null 2>&1
    adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 1
    rename_file_ui "$root/D3" a.txt b.txt
    assert_eq "D3 ($label): the file renamed (b.txt present, a.txt gone)" "$root/D3/b.txt|" "$(lsdev "$root/D3/b.txt")|$(lsdev "$root/D3/a.txt")"
    rename_file_ui "$root/D3" f1 f1b
    assert_eq "D3 ($label): the folder renamed (f1b present, f1 gone)" "$root/D3/f1b|" "$(lsdev "$root/D3/f1b")|$(lsdev "$root/D3/f1")"
    files_at "$root/D3"; M="$(ring_mark)"; pick_op move f1b f2
    wait_op "$M" 20 >/dev/null
    assert_eq "D3 ($label): the folder moved into f2 (its inner file followed)" "$root/D3/f2/f1b/inner.txt" "$(lsdev "$root/D3/f2/f1b/inner.txt")"
    assert_eq "D3 ($label): no 0-byte rename/part temp under D3" "" "$(temps_in "$root/D3")"
    adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 2
    adb shell "rm -rf $root/D3" >/dev/null 2>&1
    adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1
  }
  d3_on_volume "$QF/.." "This Device"
  # MediaStore reflects the rename: exactly one b.txt, none for a.txt (on /sdcard, which MediaProvider indexes).
  sleep 2
  record "D3: MediaStore rows for QA-Files/b.txt / QA-Files/a.txt after the renames+cleanup" "$(mediastore_rows '/QA-Files/D3/b.txt') / $(mediastore_rows '/QA-Files/D3/a.txt')"
  if pubvol_up >/dev/null; then sleep 2; d3_on_volume "$PUBVOL_PATH" "Virtual SD card"; pubvol_down; else record "D3: public volume leg" "pubvol_up failed"; fi
  rings_save
fi

# ------------------------------------------------------------------------------------------------ D5 (.Tessera at a public volume root after a rename)
if leg D5; then
  if pubvol_up >/dev/null; then
    sleep 2; V="$PUBVOL_PATH"
    adb shell "printf d5 > $V/d5.txt" >/dev/null 2>&1
    adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 1
    files_at "$V"; scroll_to_node "$ROW_DIR/d5-0.xml" files_row:d5.txt >/dev/null; hold d5-0 files_row:d5.txt; D d5-1; T d5-1 files_hold:rename 1.5; D d5-2
    dialog_type d5b.txt; D d5-3; M="$(ring_mark)"; T d5-3 files_dialog:ok 2
    wait_line "$M" "[files] rename" 8 >/dev/null
    record "D5: after a root rename, what stands at the volume root (/.Tessera is expected by design)" "[$(bin_ls "$V")]"
    assert_eq "D5: by design — /storage/<UUID>/.Tessera exists (anchored to compare names)" "$V/.Tessera" "$(lsdev "$V/.Tessera")"
    files_at "$V"; D d5-list
    assert_absent "D5: SAFE — .Tessera is hidden in Files' listing (no files_row for it)" "files_row:.Tessera" "$(cat "$ROW_DIR/d5-list.xml")"
    adb shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null 2>&1; sleep 2
    TESSROWS="$(q "content query --uri content://media/external/file --projection _data --where \"_data LIKE '%/.Tessera%'\"" | grep -c '_data=')"
    assert_eq "D5: SAFE — MediaStore holds no .Tessera row (the .nomedia marker keeps it out)" "0" "$TESSROWS"
    pubvol_down
  else
    record "D5" "pubvol_up failed; leg not run"
  fi
  rings_save
fi

# ------------------------------------------------------------------------------------------------ D6 (staged Replace under a kill)
if leg D6; then
  adb shell "mkdir -p $QF/src6/d6 && cp $QF/big.bin $QF/src6/d6/big.bin && printf 'old d6 file' > $QF/dst6; mkdir -p $QF/dst6dir" 2>/dev/null
  # The destination is a same-named FILE; copy the folder d6 onto it with Replace, then kill the shell mid-copy.
  adb shell "printf 'old d6 file' > $QF/dst6dir/d6" >/dev/null 2>&1
  MD5_OLD="$(md5dev "$QF/dst6dir/d6")"
  files_at "$QF/src6"; M="$(ring_mark)"
  pick_op copy d6 .. dst6dir
  sleep 2; D d6-conf
  if [ "$(H d6-conf files_dialog:replace)" = yes ]; then T d6-conf files_dialog:replace 1; fi
  mid_progress copy "$M" 25 > "$ROW_DIR/d6-mid.txt" || true
  sleep 2; rings_save
  M2="$(ring_mark)"
  adb shell "am force-stop app.tileshell; find $QF/dst6dir -name '.*.part'" > "$ROW_DIR/d6-part-at-kill.txt" 2>&1
  record "D6: the staged temp present at the kill" "[$(tr -d '\r' < "$ROW_DIR/d6-part-at-kill.txt" | xargs)]"
  ensure_start; files_open
  SW="$(ring_since "$M2" | grep -E '\[files\] sweep: removed' | head -1)"; [ -n "$SW" ] || SW="$(wait_line "$M2" "[files] sweep: removed" 15)"
  record "D6: the sweep's line after the relaunch" "$(printf '%s' "$SW" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
  assert_eq "D6: SAFE — the old same-named FILE is still in place with its bytes" "$MD5_OLD" "$(md5dev "$QF/dst6dir/d6")"
  assert_eq "D6: SAFE — it is still a file, not the copied folder" "regular file" "$(q "stat -c %F $QF/dst6dir/d6" | head -1)"
  assert_eq "D6: SAFE — the source folder is intact (big.bin still there)" "$(fx_md5 big.bin)" "$(md5dev "$QF/src6/d6/big.bin")"
  assert_eq "D6: SAFE — no .<name>.*.part left under dst6dir after Files opened (the sweep)" "" "$(temps_in "$QF/dst6dir")"
  assert_eq "D6: SAFE — no bin row for the displaced d6" "" "$(bin_name_of "$QF/dst6dir/d6")"
  adb shell "rm -rf $QF/src6 $QF/dst6dir $QF/dst6" >/dev/null 2>&1
  rings_save
fi

# ------------------------------------------------------------------------------------------------ the end
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
adb shell "rm -rf $SD/.Tessera" >/dev/null 2>&1; fuse_fresh
adb shell input keyevent KEYCODE_HOME; sleep 1
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
row_end
