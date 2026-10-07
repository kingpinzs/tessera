#!/usr/bin/env bash
# Phase 18 E20, the Edge-cases bullets executed (r3 V17): one function edge_<ID> per AVD sub-step, each its own row
# (folder EDGE_<ID>, its own baseline, its own fixtures and volume, its own restore), runnable alone:
#
#   edge_files.sh <ID> [<ID>…]     the named sub-steps
#   edge_files.sh --list           the sub-steps' names
#   edge_files.sh                  every sub-step, in ALL_IDS's order
#
# The bodies were written by the rows' writer once the Files UI existed (2026-10-06; they replace the skeleton's
# "NOT WRITTEN" FAILs). Every sub-step's set-up and tear-down are the floor's — the baseline restore, files_up with the
# pieces the bullet needs, pubvol_up, and their restores, whose own assertions run. scripts/edge_index.tsv maps each
# bullet of "Edge cases" to its sub-step here (or to the row, P row or JVM test that covers it). The screen-driving
# helpers are scripts/rowsb.sh's.
#
#   TENK          10,000 files in one folder: the first page < 2 s, a sort by Date inside 5 s, first = newest, last =
#                 oldest, no ANR                                              files_up tenk
#   NAMES         unicode, emoji, a 255-byte name; the hidden-files setting (.hidden.txt); a case-only rename on FAT
#                                                                             files_up + pubvol_up
#   GONE          a folder deleted under an open listing; a file deleted between list and tap     files_up
#   BACK          Back after a breadcrumb jump, ↑, selection mode, the picker (Y3, r3 D12)        files_up
#   FULL_VOLUME   copy big.bin onto a full volume: failed, "not enough space", no temp, md5 kept  files_up big
#   UNMOUNT       the volume pulled mid-copy and mid-extract: "storage removed", no partial file, the sweep on remount
#                                                                             files_up paced big zips + pubvol_up
#   REVOKE        the grant revoked during a big.bin copy: the ungranted state after a relaunch, never a crash
#                                                                             files_up paced big
#   XMOVE         a move from /sdcard to the public volume: md5 equal, the source gone             files_up + pubvol_up
#   NOMEDIA       hidden/ listed in Files, absent from the images and audio collections            files_up
#   STALE_PHOTO   a file opened in Photos, then deleted in Files: the stale row's placeholder      files_up media
#   INTERRUPT     screen off, an incoming call and a reboot during a copy: finishes or fails cleanly, the sweep, the bin
#                 untouched                                                   files_up paced big
#   TILE_COLD     the pinned Files tile, cold: Recent with the pane open                           (baseline only)
#   PROPS         Properties' values for b.bin and sub, read from the fixture table                files_up
#   BIN           the Recycle Bin's index cases (missing, stale record, folder-now-a-file, same names, no record)
#                                                                             files_up
#   BIN_PUBVOL    delete and Restore on the public volume (E20's list)                             files_up + pubvol_up
#   KEEP_BOTH     a conflict answered "keep both" leaves `b (2).bin` (E20's list; r3 D6)           files_up
#   ZIP           zip64 (70,000 entries inside 5 s), CP437 names, a zip in the bin                 files_up zips
#   LIVENESS      a reboot and a force-stop leave the grant, the checklist row, the bins' indexes, Recent and an empty
#                 journal intact (N-01)                                       files_up
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
ALL_IDS="TENK NAMES GONE BACK FULL_VOLUME UNMOUNT REVOKE XMOVE NOMEDIA STALE_PHOTO INTERRUPT TILE_COLD PROPS BIN BIN_PUBVOL KEEP_BOTH ZIP LIVENESS"
FAILED_ROWS=""
PRIMARY=/storage/emulated/0

# A sub-step's frame. edge_begin: the earlier run's folder kept, the row begun, the baseline restored (C-3), then the
# fixtures and the volume the bullet needs — a set-up that fails ends the sub-step there (its FAIL is in the log).
# edge_end: the sub-step's own undo (EDGE_UNDO, a function name), the volume and the fixtures given back (their
# snapshot assertions), every ring saved, Start, the gate APK asserted, the verdict.
EDGE_PUBVOL=""; EDGE_UNDO=""
edge_begin() { # ID description pubvol|nopubvol|nofiles [files_up arguments…]
  local id="$1" what="$2" vol="$3"
  shift 3
  keep_earlier_run "EDGE_$id"
  row_begin "EDGE_$id" "edge: $what"
  D="$ROW_DIR"
  EDGE_PUBVOL=""; EDGE_FILES=""; EDGE_UNDO=""
  RINGS=(launcher)
  assert_gate_apk
  baseline_start
  adb logcat -c
  if [ "$vol" != nofiles ]; then
    files_up "$@" || { _verdict FAIL "EDGE_$id set-up: files_up $*" "failed (above)"; return 1; }
    EDGE_FILES=1
  fi
  if [ "$vol" = pubvol ]; then
    EDGE_PUBVOL=1
    pubvol_up >/dev/null || { _verdict FAIL "EDGE_$id set-up: pubvol_up" "failed (above)"; return 1; }
    sleep 2
  fi
  ensure_start
}
edge_end() {
  [ -n "$EDGE_UNDO" ] && "$EDGE_UNDO"
  assert_eq "no crash of the shell in the sub-step (AndroidRuntime)" "0" "$(crash)"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  [ -n "$EDGE_PUBVOL" ] && pubvol_down
  [ -n "${FX_SNAPPED:-}" ] && files_down
  rings_save
  ensure_start
  assert_gate_apk "end: the installed APK is the gate candidate"
  assert_contains "end: All-files access is held" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
  row_end || FAILED_ROWS="$FAILED_ROWS $ROW"
}
yes_if() { if "$@"; then echo yes; else echo no; fi; }

# ------------------------------------------------------------------------------------------------ TENK
edge_TENK() {
  if edge_begin TENK "10,000 files in one folder — the first page, the sort by Date, no ANR" nopubvol tenk; then
    local big=$PRIMARY/QA-Big m l ms dt
    m="$(ring_mark)"; files_at "$big"
    l="$(wait_line "$m" "[files] list $big: 10000 entries" 20)"
    record "the list line" "$(printf '%s' "$l" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
    assert_contains "[files] list …/QA-Big: 10000 entries <ms> ms" "[files] list $big: 10000 entries " "$l"
    ms="$(printf '%s' "$l" | sed -n 's/.* entries \([0-9]*\) ms.*/\1/p')"
    assert_eq "the listing's first page draws in < 2 s (the line's ms = ${ms:-?})" "yes" "$(yes_if test -n "$ms" -a "${ms:-9999}" -lt 2000)"
    sleep 1.5; D t1; S t1
    assert_eq "the page lists QA-Big by name (Sort by: Name; the first row f1)" "Sort by: Name f1" "$(X t1 files_sort) $(first_row t1)"
    T t1 files_sort 1; D t2
    m="$(ring_mark)"; T t2 files_sort_item:date 0
    l="$(wait_line "$m" "[files] list $big: 10000 entries" 20)"
    dt=""; [ -n "$l" ] && dt=$(( $(printf '%s' "$l" | wall_of) - m ))
    record "sort by Date: ms from the MARK before the tap to the list line / the line" "${dt:-no line} / $(printf '%s' "$l" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
    assert_eq "a sort by Date redraws within 5 s (MARK before the tap → the list line)" "yes" "$(yes_if test -n "$dt" -a "${dt:-99999}" -le 5000)"
    sleep 2; D t3; S t3
    assert_eq "sorted by Date: the sort line, and the first row = the newest file (f10000)" "Sort by: Date f10000" "$(X t3 files_sort) $(first_row t3)"
    if fling_end files_row:f1 t4 30 40; then _verdict PASS "scrolled to the end: the oldest file's row (f1) is reached" "t4.xml"
    else _verdict FAIL "scrolled to the end: the oldest file's row (f1) is reached" "not in the dump after the flings; the last row there is $(last_row t4)"; fi
    S t4
    assert_eq "at the end the last row = the oldest file (f1)" "f1" "$(last_row t4)"
    assert_eq "no ANR in app.tileshell (logcat)" "0" "$(anr)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ NAMES
edge_NAMES() {
  if edge_begin NAMES "names — unicode, emoji, 255 bytes, leading dots and the hidden-files setting, a case-only rename on FAT" pubvol; then
    local n255 v="$PUBVOL_PATH" md5n m
    n255="$(python3 -c "print('n' * 251 + '.txt')")"
    dev_script names-make > "$ROW_DIR/names-make.out" <<SH
cd /sdcard/QA-Files || exit 1
printf unicode > 'ünï-cödé 文件.txt'
printf emoji > 'emoji-😀.txt'
printf 'a 255-byte name' > '$n255'
printf fat > '$v/a.txt'
n=0; for f in 'ünï-cödé 文件.txt' 'emoji-😀.txt' '$n255'; do [ -f "\$f" ] && n=\$((n + 1)); done; echo \$n
SH
    assert_eq "the three names are made on the device (ls)" "3" "$(tail -1 "$ROW_DIR/names-make.out")"
    md5n="$(md5dev "/sdcard/QA-Files/$n255")"
    files_at "$QF"; scroll_to_node "$ROW_DIR/n1.xml" "files_row:ünï-cödé 文件.txt" >/dev/null; S n1
    assert_eq "a unicode name is listed" "yes" "$(H n1 "files_row:ünï-cödé 文件.txt")"
    # uiautomator writes a character outside the BMP as a numeric entity: 😀 is &#128512; in the dump (run 1 looked for
    # the character itself and scrolled past the row).
    files_at "$QF"; scroll_to_node "$ROW_DIR/n2.xml" "files_row:emoji-&#128512;.txt" >/dev/null; S n2
    assert_eq "an emoji name is listed (the dump's files_row:emoji-&#128512;.txt)" "yes" "$(H n2 "files_row:emoji-&#128512;.txt")"
    files_at "$QF"; scroll_to_node "$ROW_DIR/n3.xml" "files_row:$n255" >/dev/null
    assert_eq "the 255-byte name is listed" "yes" "$(H n3 "files_row:$n255")"
    hold n3 "files_row:$n255"; D n4; T n4 files_hold:rename 1.5; D n5
    assert_eq "the 255-byte name: the rename dialog holds the whole name" "$n255" "$(X n5 files_dialog_input)"
    dialog_type renamed-255.txt; D n6
    m="$(ring_mark)"; T n6 files_dialog:ok 2
    assert_contains "the 255-byte name is renamed: [files] rename … -> renamed-255.txt: ok" "-> renamed-255.txt: ok" "$(ring_since "$m" | grep -F '[files] rename')"
    assert_eq "…on disk: the new name with the md5, the old name gone" "$md5n 0" "$(md5dev /sdcard/QA-Files/renamed-255.txt) $(q 'ls /sdcard/QA-Files' | grep -cx "$n255")"
    record "the 255-byte name deleted to the bin and restored" "E4b's leg J (r3 D3) and FileOps' JVM case (E18)"

    # leading dots: the hidden-files setting, default off → on → off
    hidden_setting() { files_at "$QF"; D h0; T h0 files_bar:more 1; D h1; T h1 files_more:settings 1.5; D h2; T h2 files_setting_hidden 1; D h3; adb shell input keyevent KEYCODE_BACK; sleep 1; }
    files_at "$QF"; D d0
    assert_eq "leading dots, the setting off (default): .hidden.txt is absent, though it is on disk" "no /sdcard/QA-Files/.hidden.txt" "$(H d0 files_row:.hidden.txt) $(lsdev /sdcard/QA-Files/.hidden.txt)"
    hidden_setting; S h3
    record "the setting's page: its toggle before / after the tap" "$(grep -o '<node[^>]*files_setting_hidden[^>]*>' "$ROW_DIR/h2.xml" | grep -o 'checked="[a-z]*"' | head -1) / $(grep -o '<node[^>]*files_setting_hidden[^>]*>' "$ROW_DIR/h3.xml" | grep -o 'checked="[a-z]*"' | head -1)"
    files_at "$QF"; scroll_to_node "$ROW_DIR/d1.xml" files_row:.hidden.txt >/dev/null
    assert_eq "the setting on: .hidden.txt is present" "yes" "$(H d1 files_row:.hidden.txt)"
    hidden_setting
    files_at "$QF"; D d2
    scroll_to_node "$ROW_DIR/d2.xml" files_row:.hidden.txt 6 >/dev/null
    assert_eq "the setting off again: .hidden.txt is absent (the whole list scrolled)" "no" "$(H d2 files_row:.hidden.txt)"

    # a name that differs only by case, on the sub-step's own FAT volume
    record "the volume's file system" "$(q "mount | grep '$PUBVOL_UUID' | head -1" | awk '{ print $5 }' | xargs) (task 0 (a): vfat)"
    files_at "$v"; scroll_to_node "$ROW_DIR/f0.xml" files_row:a.txt >/dev/null; hold f0 files_row:a.txt; D f1; T f1 files_hold:rename 1.5; D f2   # (scrolled: the root lists its folders first)
    dialog_type A.TXT; D f3
    m="$(ring_mark)"; T f3 files_dialog:ok 2
    record "the rename's line" "$(ring_since "$m" | grep -o '\[files\] rename.*' | sed 's/ *wall=.*//')"
    assert_contains "FAT, a.txt → A.TXT: the rename succeeds" "[files] rename $v/a.txt -> A.TXT: ok" "$(ring_since "$m")"
    assert_eq "FAT: ls lists it once, as A.TXT" "A.TXT" "$(q "ls $v" | grep -i '^a\.txt$' | xargs)"
    files_at "$v"; scroll_to_node "$ROW_DIR/f4.xml" files_row:A.TXT >/dev/null
    assert_eq "FAT: Files lists it once (files_row:A.TXT, no files_row:a.txt)" "1 0" "$(count_nodes f4 files_row:A.TXT) $(count_nodes f4 files_row:a.txt)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ GONE
edge_GONE() {
  if edge_begin GONE "a folder deleted under an open listing; a file deleted between list and tap" nopubvol; then
    adb shell "mkdir -p /sdcard/QA-Files/sub/deep && printf x > /sdcard/QA-Files/sub/deep/in.txt"
    # Reached by a tap from sub, as a user reaches it: a folder opened by the `path` extra has no history, and Back
    # with an empty history sends the task to the back (Decisions 2026-10-06 (5)) — run 1 opened deep that way and Back
    # left Files instead of going up.
    files_at "$QF/sub"; D g00; T g00 files_row:deep 1.5; D g0
    assert_eq "the folder is open (its row listed)" "deep yes" "$(X g0 files_crumb:3) $(H g0 files_row:in.txt)"
    adb shell input keyevent KEYCODE_HOME; sleep 1
    dev_script gone-rm >/dev/null <<'SH'
rm -r /sdcard/QA-Files/sub/deep
SH
    assert_eq "adb shell rm -r: the folder is gone" "" "$(lsdev /sdcard/QA-Files/sub/deep)"
    files_open; D g1; S g1
    assert_eq "the page shows This folder is gone (files_error)" "This folder is gone" "$(X g1 files_error)"
    adb shell input keyevent KEYCODE_BACK; sleep 1.5; D g2
    assert_eq "Back goes up: sub is listed, no error" "sub|" "$(X g2 files_crumb:2)|$(X g2 files_error)"
    assert_eq "…and deep is no longer a crumb" "no" "$(H g2 files_crumb:3)"
    # a file deleted between list and tap
    files_at "$QF"; scroll_to_node "$ROW_DIR/g3.xml" files_row:a.txt >/dev/null
    dev_script gone-rm-file >/dev/null <<'SH'
rm /sdcard/QA-Files/a.txt
SH
    local m; m="$(ring_mark)"
    tap_node "$ROW_DIR/g3.xml" files_row:a.txt; sleep 2; D g4; S g4
    assert_ne "a file deleted between list and tap: the tap shows the error line (files_error)" "" "$(X g4 files_error)"
    record "its text" "$(X g4 files_error)"
    assert_eq "…and nothing opened (Files on top)" "$FILES_ACTIVITY" "$(top_activity)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ BACK
edge_BACK() {
  if edge_begin BACK "Back after a breadcrumb jump, ↑, selection mode, and Back in the picker" nopubvol; then
    local before
    adb shell "mkdir -p /sdcard/QA-Files/sub/deep"
    c6; ensure_start
    files_at "$QF/sub/deep"; D b0
    assert_eq "open /sdcard/QA-Files/sub/deep (crumbs)" "This Device QA-Files sub deep" "$(X b0 files_crumb:0) $(X b0 files_crumb:1) $(X b0 files_crumb:2) $(X b0 files_crumb:3)"
    T b0 files_crumb:0 1.5; D b1
    assert_eq "tap files_crumb:0 → This Device's root (one crumb, DCIM listed)" "This Device no yes" "$(X b1 files_crumb:0) $(H b1 files_crumb:1) $(H b1 files_row:DCIM)"
    adb shell input keyevent KEYCODE_BACK; sleep 1.5; D b2
    assert_eq "Back returns to deep (the folder left), not to its parent" "deep" "$(X b2 files_crumb:3)"
    T b2 files_up 1.5; D b3
    assert_eq "↑ from deep goes to sub" "sub no" "$(X b3 files_crumb:2) $(H b3 files_crumb:3)"
    files_at "$QF"; D b4; T b4 files_bar:select 1; D b5; T b5 files_row:a.txt 0.6; D b6
    assert_eq "selection mode is on" "1 item selected" "$(X b6 files_sort)"
    adb shell input keyevent KEYCODE_BACK; sleep 1.2; D b7
    assert_eq "Back with selection mode on leaves selection mode first and stays in the folder" "Sort by: Name QA-Files $FILES_ACTIVITY" "$(X b7 files_sort) $(X b7 files_crumb:1) $(top_activity)"
    # Back in the picker (r3 D12)
    before="$(q 'find /sdcard/QA-Files | sort | md5sum' | cut -d' ' -f1)"
    files_at "$QF"; scroll_to_node "$ROW_DIR/p0.xml" files_row:a.txt >/dev/null; hold p0 files_row:a.txt; D p1; T p1 files_hold:move 1.5
    _pick_walk sub deep; D p2
    assert_eq "the picker, two folders down (title, last crumb)" "Choose a folder deep" "$(X p2 files_pick_title) $(X p2 files_crumb:3)"
    adb shell input keyevent KEYCODE_BACK; sleep 1.2; D p3
    assert_eq "Back → the picker's previous folder (sub), the picker still open" "Choose a folder sub no" "$(X p3 files_pick_title) $(X p3 files_crumb:2) $(H p3 files_crumb:3)"
    adb shell input keyevent KEYCODE_BACK; sleep 1.2; D p4
    assert_eq "Back → its first folder (QA-Files), still the picker" "Choose a folder QA-Files no" "$(X p4 files_pick_title) $(X p4 files_crumb:1) $(H p4 files_crumb:2)"
    adb shell input keyevent KEYCODE_BACK; sleep 1.2; D p5
    assert_eq "Back at its first folder → the picker closes, Files still on top" "no $FILES_ACTIVITY" "$(H p5 files_pick_title) $(top_activity)"
    assert_eq "nothing moved (the tree's listing is unchanged)" "$before" "$(q 'find /sdcard/QA-Files | sort | md5sum' | cut -d' ' -f1)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ FULL_VOLUME
edge_FULL_VOLUME() {
  if edge_begin FULL_VOLUME "copy big.bin onto a full volume (fill_volume 1048576 … unfill_volume)" nopubvol big; then
    local m l md5big
    md5big="$(fx_md5 big.bin)"
    EDGE_UNDO=_undo_fill
    _undo_fill() { unfill_volume; assert_eq "unfill_volume: fill.bin is gone" "" "$(lsdev /sdcard/fill.bin)"; assert_eq "awake after unfill_volume" "Awake" "$(wake_device)"; EDGE_UNDO=""; }
    if fill_volume 1048576; then
      _verdict PASS "fill_volume 1048576 (its own assert: free <= 1 MB + 5 MB)" "$(q 'df -k /sdcard' | awk 'NR==2 { print $4 " KB free" }')"
      assert_eq "awake after the fill" "Awake" "$(wake_device)"
      ensure_start; files_at "$QF"; m="$(ring_mark)"
      pick_op copy big.bin sub
      l="$(wait_op "$m" 30)"; D f1; S f1
      record "the end line / files_error" "$(printf '%s' "$l" | grep -o '\[files\].*' | sed 's/ *wall=.*//') / $(X f1 files_error)"
      assert_contains "[files] copy … failed <reason>" "[files] copy 1 files 209715200 -> $QF/sub failed " "$l"
      assert_contains "…the reason is not enough space" "failed not enough space" "$l"
      assert_contains "files_error says not enough space" "not enough space" "$(X f1 files_error)"
      assert_eq "no temp in the destination (ls -a: nothing matching .*.part), and no big.bin there" "|" "$(q 'ls -a /sdcard/QA-Files/sub' | grep -E '^\..*\.part$')|$(lsdev /sdcard/QA-Files/sub/big.bin)"
      assert_eq "the source's md5 is unchanged" "$md5big" "$(md5dev /sdcard/QA-Files/big.bin)"
    else
      _verdict FAIL "fill_volume 1048576" "its precondition failed (above)"
    fi
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ UNMOUNT
# The pull itself: `sm unmount` with the pid read before and after and logcat's kill lines kept. vold ends every process
# that holds a file open on a volume being unmounted, so the shell's process — in the middle of writing to it — may not
# live to write its `failed storage removed` line (run 1 of UNMOUNT: no end line, the page gone with the process). Sets
# PULL_SURVIVED=yes|no.
pull_volume() { # tag
  local p0 p1
  p0="$(q 'pidof app.tileshell')"; rings_save
  adb logcat -c
  adb shell sm unmount "$PUBVOL_ID"; sleep 2
  p1="$(q 'pidof app.tileshell')"
  adb logcat -d 2>/dev/null | tr -d '\r' | grep -iE "Killing $p0|Process app.tileshell \(pid $p0\) has died|Sending .* to $p0|vold.*$p0" > "$ROW_DIR/$1-kill-lines.txt"
  if [ -n "$p0" ] && [ "$p0" = "$p1" ]; then PULL_SURVIVED=yes; else PULL_SURVIVED=no; fi
  record "$1: the shell's pid before / after sm unmount; logcat's lines about that pid" "${p0:-none} / ${p1:-none}; $(head -2 "$ROW_DIR/$1-kill-lines.txt" | cut -c1-200 | tr '\n' '|')"
}
# The operation's end as the pull leaves it: its `failed storage removed` line and the page's words when the process
# lived; RECORDED as not observable when the platform ended the process (the safe outcome is asserted either way).
pull_end() { # tag mark op-regex dump
  local l
  if [ "$PULL_SURVIVED" = yes ]; then
    l="$(wait_op "$2" 30)"; D "$4"; S "$4"
    assert_contains "$1: the operation fails with storage removed" "failed storage removed" "$(printf '%s' "$l" | grep -E "$3")"
    assert_contains "$1: the page says so (files_error)" "torage" "$(X "$4" files_error)"
  else
    record "$1: the operation's failed-storage-removed line and the page's words" "NOT OBSERVABLE on this image: the platform ended the shell's process at the unmount (it held the volume's file open), so no line could be written and no page was left to say it"
    assert_eq "$1: no crash of the shell's own making (AndroidRuntime) — the process was ended by the platform" "0" "$(crash)"
  fi
}

edge_UNMOUNT() {
  if edge_begin UNMOUNT "the volume unmounted mid-copy and mid-extract; the sweep when it mounts again" pubvol paced big zips; then
    local v="$PUBVOL_PATH" m l md5big sw
    md5big="$(fx_md5 big.bin)"
    # mid-copy
    files_at "$QF"; m="$(ring_mark)"
    pick_op_pane copy big.bin "$PUBVOL_UUID"
    mid_progress copy "$m" 20 > "$ROW_DIR/u-mid-copy.txt"
    record "the temp on the volume mid-copy" "$(temps_in "$v")"
    pull_volume mid-copy
    pull_end mid-copy "$m" '\[files\] copy 1 files 209715200' u1
    assert_eq "mid-copy: no partial file on the remaining side (no temp under /sdcard), the source's md5 unchanged" "|$md5big" "$(temps_in /sdcard/)|$(md5dev /sdcard/QA-Files/big.bin)"
    m="$(ring_mark)"; adb shell sm mount "$PUBVOL_ID"; sleep 3
    ensure_start; files_open; sleep 2
    sw="$(wait_line "$m" "[files] sweep: removed" 8)"
    record "after the volume mounts again: the sweep's line" "$(printf '%s' "$sw" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
    assert_eq "a temp left on the pulled volume is swept when it mounts again (no .part / .extract on it), and no big.bin there" "|" "$(temps_in "$v")|$(lsdev "$v/big.bin")"
    # a cross-volume MOVE pulled mid-way. Beyond the bullet's own text: E12 lists `[files] move … failed <reason>` and
    # the doc names no AVD producer for it (E12/producers.tsv, the floor's note of 2026-10-06 14:58).
    ensure_start; files_at "$QF"; m="$(ring_mark)"
    pick_op_pane move big.bin "$PUBVOL_UUID"
    mid_progress move "$m" 20 > "$ROW_DIR/u-mid-move.txt"
    pull_volume mid-move
    pull_end mid-move "$m" '\[files\] move 1 files 209715200' u1m
    assert_eq "mid-move: the original is still there with its md5 (never neither), no temp beside it" "$md5big|" "$(md5dev /sdcard/QA-Files/big.bin)|$(temps_in /sdcard/)"
    m="$(ring_mark)"; adb shell sm mount "$PUBVOL_ID"; sleep 3
    ensure_start; files_open; sleep 2
    wait_line "$m" "[files] sweep: removed" 8 >/dev/null
    assert_eq "mid-move: after the volume mounts again no temp and no big.bin is on it" "|" "$(temps_in "$v")|$(lsdev "$v/big.bin")"
    # mid-extract of a zip on that volume
    adb shell "cp /sdcard/QA-Files/zips/qa-big.zip $v/qa-big.zip"; sleep 1
    ensure_start; files_at "$v"; scroll_to_node "$ROW_DIR/u2.xml" files_row:qa-big.zip >/dev/null; T u2 files_row:qa-big.zip 2; D u3
    assert_eq "the zip on the volume opens as a folder" "yes" "$(H u3 files_zip_root)"
    m="$(ring_mark)"; T u3 files_extract 0.3
    mid_progress "zip extract" "$m" 20 > "$ROW_DIR/u-mid-extract.txt"
    pull_volume mid-extract
    pull_end mid-extract "$m" '\[files\] zip extract' u4
    m="$(ring_mark)"; adb shell sm mount "$PUBVOL_ID"; sleep 3
    ensure_start; files_open; sleep 2
    sw="$(wait_line "$m" "[files] sweep: removed" 8)"
    record "after the second mount: the sweep's line" "$(printf '%s' "$sw" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
    assert_eq "mid-extract: no partial output on the volume (no qa-big folder, no temp)" "|" "$(lsdev "$v/qa-big")|$(temps_in "$v")"
    assert_eq "the journal names no temp any more" "0" "$(journal_json | grep -cE '\.part|\.extract')"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ REVOKE
edge_REVOKE() {
  if edge_begin REVOKE "the grant revoked during a big.bin copy (appops … default; restored to allow)" nopubvol paced big; then
    local m l md5big pid0 pid1 m2 sw
    md5big="$(fx_md5 big.bin)"
    EDGE_UNDO=_undo_revoke
    _undo_revoke() { adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow; EDGE_UNDO=""; }
    files_at "$QF"; m="$(ring_mark)"
    pick_op copy big.bin sub
    mid_progress copy "$m" 20 > "$ROW_DIR/r-mid.txt"
    pid0="$(q 'pidof app.tileshell')"
    rings_save; ring_since "$m" > "$ROW_DIR/r-ring-before-revoke.txt"
    adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE default
    sleep 3
    pid1="$(q 'pidof app.tileshell')"
    record "the shell's pid before / after the revoke (task 0 (c): the revoke kills the process)" "${pid0:-none} / ${pid1:-none}"
    l="$(ring_since "$m" | grep -E '\[files\] copy 1 files .*(done|cancelled|failed)' | tail -1)"
    if [ -n "$pid0" ] && [ "$pid0" = "$pid1" ]; then
      assert_contains "the process survived: the copy ended failed access removed" "failed access removed" "$l"
    else
      record "the process did not survive, so no end line can be asked of it; the slice from the copy's MARK holds" "[$(printf '%s' "$l" | grep -o '\[files\].*' | sed 's/ *wall=.*//')]"
    fi
    record "the temp left in the destination after the revoke" "[$(temps_in /sdcard/QA-Files)]"
    ensure_start
    m2="$(ring_mark)"; files_open; sleep 1; D r1; S r1
    assert_eq "after a relaunch (fresh MARK) Files shows the ungranted state (files_ungranted, its link)" "yes yes" "$(H r1 files_ungranted) $(H r1 files_grant_link)"
    assert_contains "…and says so in the ring: [files] access=denied" "[files] access=denied" "$(ring_since "$m2")"
    assert_eq "never an IOException on the page (no files_error node)" "no" "$(H r1 files_error)"
    assert_eq "never a crash (AndroidRuntime)" "0" "$(crash)"
    adb shell appops set app.tileshell MANAGE_EXTERNAL_STORAGE allow; EDGE_UNDO=""
    assert_contains "appops set … allow" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
    m2="$(ring_mark)"       # before the restart: the sweep runs at the main process's start
    c6; ensure_start
    files_open; sleep 2
    sw="$(wait_line "$m2" "[files] sweep: removed" 8)"
    record "after allow and a relaunch: the sweep's line" "$(printf '%s' "$sw" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
    assert_eq "after the sweep no temp remains (find /sdcard/ -name '.*.part' is empty)" "" "$(q "find /sdcard/ -name '.*.part' 2>/dev/null" | xargs)"
    assert_eq "the source's md5 is unchanged" "$md5big" "$(md5dev /sdcard/QA-Files/big.bin)"
    D r2
    assert_eq "with the grant back Files lists again (no files_ungranted)" "no" "$(H r2 files_ungranted)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ XMOVE
edge_XMOVE() {
  if edge_begin XMOVE "a move from /sdcard to the sub-step's own public volume" pubvol; then
    local v="$PUBVOL_PATH" m l md5b
    md5b="$(fx_md5 b.bin)"
    files_at "$QF"; m="$(ring_mark)"
    pick_op_pane move b.bin "$PUBVOL_UUID"
    l="$(wait_op "$m" 30)"
    assert_contains "[files] move 1 files 307200 -> $v done" "[files] move 1 files 307200 -> $v done" "$l"
    assert_eq "the md5 on the volume equals the file's" "$md5b" "$(md5dev "$v/b.bin")"
    assert_eq "the source is gone" "" "$(lsdev /sdcard/QA-Files/b.bin)"
    assert_eq "no temp on either side" "" "$(temps_in "$v")$(temps_in /sdcard/QA-Files)"
    scroll_to_node "$ROW_DIR/x1.xml" files_row:b.bin >/dev/null      # the root lists its folders first
    assert_eq "Files lands on the volume's root with the file listed" "Virtual SD card yes" "$(X x1 files_crumb:0) $(H x1 files_row:b.bin)"
    record "a failure after the copy leaves both files" "FileOps' JVM case (E18): a cross-volume move failing after the copy leaves both files, never neither"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ NOMEDIA
edge_NOMEDIA() {
  if edge_begin NOMEDIA ".nomedia folders — listed in Files, absent from the images and audio collections" nopubvol; then
    local m k
    files_at "$QF/hidden"; D n1; S n1
    assert_eq "Files lists the .nomedia folder's contents (the image, the MP3, the video)" "yes yes yes" "$(H n1 files_row:qa-hidden.png) $(H n1 files_row:qa-hidden.mp3) $(H n1 files_row:qa-hidden.mp4)"
    assert_eq "…and the folder holds .nomedia on disk" "/sdcard/QA-Files/hidden/.nomedia" "$(lsdev /sdcard/QA-Files/hidden/.nomedia)"
    for k in images audio video; do
      assert_eq "content query on the $k collection lists nothing of hidden/ (MediaStore's rule: Photos, Music and Video do not show it)" "0" \
        "$(q "content query --uri content://media/external/$k/media --projection _data" | grep -c "QA-Files/hidden/")"
    done
    assert_ne "(the control) the images collection does list QA-Files' other images" "0" "$(q "content query --uri content://media/external/images/media --projection _data" | grep -c "QA-Files/img-")"
    m="$(ring_mark)"; T n1 files_row:qa-hidden.png 3
    assert_eq "its image opens in the viewer" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
    assert_contains "…through the provider" "[files] open $QF/hidden/qa-hidden.png via provider" "$(ring_since "$m")"
    adb shell input keyevent KEYCODE_BACK; sleep 1.2
    files_open --es page recent; D n2
    assert_contains "opened from Files it IS in Recent (Q-18-1)" "qa-hidden.png" "$(recent_rows n2)"
    c6; ensure_start
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ STALE_PHOTO
edge_STALE_PHOTO() {
  if edge_begin STALE_PHOTO "a file opened in Photos, then deleted in Files" nopubvol media; then
    local id0 id1 ms t0 i
    RINGS=(launcher "app.tileshell/.photos.PhotosEditDumpService")
    id0="$(media_id images qa-photo-0.png DCIM/Camera/)"; id1="$(media_id images qa-photo-1.png DCIM/Camera/)"
    assert_ne "the row's own photos have images rows (media_up)" "" "${id0:+x}${id1:+x}"
    adb shell am start -n app.tileshell/.photos.PhotosActivity >/dev/null 2>&1; sleep 3
    scroll_to_node "$ROW_DIR/s1.xml" "photos_item:$id0" 12 >/dev/null
    assert_eq "Photos' collection shows the photo's tile" "yes" "$(H s1 "photos_item:$id0")"
    tap_node "$ROW_DIR/s1.xml" "photos_item:$id0"; sleep 2; D s2; S s2
    assert_eq "the photo is opened in Photos (its viewer)" "yes" "$(H s2 viewer)"
    adb shell input keyevent KEYCODE_HOME; sleep 1
    bin_it "$PRIMARY/DCIM/Camera" qa-photo-0.png
    t0="$BIN_MARK"; ms=""
    for i in $(seq 1 15); do
      if [ "$(q "content query --uri content://media/external/images/media --projection _id --where _id=$id0" | grep -c '_id=')" = 0 ]; then ms=$(( $(device_ms) - t0 )); break; fi
      sleep 0.2
    done
    assert_contains "the file is deleted in Files (to the bin)" "[files] bin delete $PRIMARY/DCIM/Camera/qa-photo-0.png: ok" "$(ring_since "$t0")"
    record "ms until its images row is gone (the scan landing)" "${ms:-still there after the poll}"
    adb shell am start -n app.tileshell/.photos.PhotosActivity >/dev/null 2>&1; sleep 3; D s3; S s3
    record "Photos, brought back: what is on top / the viewer's state" "$(top_activity) / viewer=$(H s3 viewer) viewer_error=$(H s3 viewer_error)"
    [ "$(H s3 viewer)" = yes ] && { adb shell input keyevent KEYCODE_BACK; sleep 2; D s3; }
    scroll_to_node "$ROW_DIR/s4.xml" "photos_item:$id1" 12 >/dev/null; S s4
    record "the deleted photo's tile / its placeholder (photos_item_missing) in the collection" "$(H s4 "photos_item:$id0") / $(H s4 "photos_item_missing:$id0")"
    assert_eq "Photos never shows the deleted photo as a live picture: its tile is gone, or it is the placeholder" "yes" \
      "$(yes_if test "$(H s4 "photos_item:$id0")" = no -o "$(H s4 "photos_item_missing:$id0")" = yes)"
    assert_eq "(the control) its neighbour's tile is still there, with no placeholder" "yes no" "$(H s4 "photos_item:$id1") $(H s4 "photos_item_missing:$id1")"
    media_scan; sleep 1; D s5
    assert_eq "after a scan the row and the tile are gone" "0 no" "$(q "content query --uri content://media/external/images/media --projection _id --where _id=$id0" | grep -c '_id=') $(H s5 "photos_item:$id0")"
    c6; ensure_start
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ INTERRUPT
edge_INTERRUPT() {
  if edge_begin INTERRUPT "screen off, an incoming call and a reboot during a copy" nopubvol paced big; then
    local m l md5big bin0 idx0
    md5big="$(fx_md5 big.bin)"
    bin_it "$QF" img-0.png
    bin0="$(bin_ls)"; idx0="$(md5dev "$BINP/.index.json")"
    assert_ne "a bin file exists before the interruptions (its listing and index md5 recorded)" "" "$idx0"
    # screen off
    files_at "$QF"; m="$(ring_mark)"; pick_op copy big.bin sub
    mid_progress copy "$m" 20 > "$ROW_DIR/i-mid-sleep.txt"
    adb shell input keyevent KEYCODE_SLEEP; sleep 4
    record "wakefulness after KEYCODE_SLEEP" "$(adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r ' | sed 's/mWakefulness=//')"
    l="$(wait_op "$m" 60)"
    assert_eq "after the screen-off: wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
    assert_contains "screen off mid-copy: the service finishes (copy … done)" "[files] copy 1 files 209715200 -> $QF/sub done" "$l"
    assert_eq "screen off: the copy's md5 equals the source's, no temp" "$md5big|" "$(md5dev /sdcard/QA-Files/sub/big.bin)|$(temps_in /sdcard/QA-Files)"
    # an incoming call
    ensure_start; files_at "$QF"; m="$(ring_mark)"; pick_op copy big.bin recent
    mid_progress copy "$m" 20 > "$ROW_DIR/i-mid-call.txt"
    adb emu gsm call 5551234 > "$ROW_DIR/i-call.out" 2>&1; sleep 5
    record "the call state while it rings (dumpsys telephony.registry mCallState)" "$(adb shell dumpsys telephony.registry | tr -d '\r' | grep -m1 'mCallState' | xargs)"
    adb emu gsm cancel 5551234 >> "$ROW_DIR/i-call.out" 2>&1
    l="$(wait_op "$m" 60)"
    assert_contains "an incoming call mid-copy: the service finishes (copy … done)" "[files] copy 1 files 209715200 -> $QF/recent done" "$l"
    assert_eq "the call: the copy's md5 equals the source's, no temp" "$md5big|" "$(md5dev /sdcard/QA-Files/recent/big.bin)|$(temps_in /sdcard/QA-Files)"
    assert_eq "awake after the call" "Awake" "$(wake_device)"
    # a reboot
    ensure_start; files_at "$QF"; m="$(ring_mark)"; pick_op copy big.bin hidden
    mid_progress copy "$m" 20 > "$ROW_DIR/i-mid-reboot.txt"
    record "the temp in flight before the reboot" "[$(temps_in /sdcard/QA-Files)]"
    rings_save
    adb reboot
    boot_wait
    assert_eq "after the reboot (boot-completed poll): wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
    ROW_MARK_REBOOT="$(ring_mark)"
    ensure_start; sleep 3
    m="$(ring_mark)"; files_open; sleep 3
    record "the sweep's line after the first start (the ring from the boot on)" "$(ring_since "$ROW_MARK" | grep -o '\[files\] sweep.*' | sed 's/ *wall=.*//' | xargs)"
    assert_eq "after the reboot and the shell's first start no temp files remain (find … '.*.part' -o '.*.extract')" "" "$(temps_in /sdcard/)"
    assert_eq "…and no half-made big.bin is in the destination" "" "$(lsdev /sdcard/QA-Files/hidden/big.bin)"
    assert_eq "the source's md5 is unchanged" "$md5big" "$(md5dev /sdcard/QA-Files/big.bin)"
    assert_eq "no bin file was touched: the bin's listing and its index (md5) equal before and after" "$bin0 $idx0" "$(bin_ls) $(md5dev "$BINP/.index.json")"
    assert_contains "the pace pref survived the reboot (the row's own)" "$PACE_BPS" "$(pace_now)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ TILE_COLD
edge_TILE_COLD() {
  if edge_begin TILE_COLD "the pinned Files tile on a cold start: Recent with the pane open" nofiles; then
    local m
    c6; ensure_start
    scroll_to_node "$ROW_DIR/t0.xml" "$FILES_TILE" 6 >/dev/null
    assert_eq "the Files tile is pinned on Start (the baseline)" "yes" "$(H t0 "$FILES_TILE")"
    record "the shell's pid before the tap (a cold start of Files: no FilesActivity in the process)" "$(q 'pidof app.tileshell') / FilesActivity records: $(adb shell dumpsys activity activities | grep -c 'Hist.*files.FilesActivity')"
    m="$(ring_mark)"; tap_node "$ROW_DIR/t0.xml" "$FILES_TILE"; sleep 3; D t1; S t1
    assert_eq "the tile opens Files" "$FILES_ACTIVITY" "$(top_activity)"
    assert_eq "on a cold start the pane is open (files_pane)" "yes" "$(H t1 files_pane)"
    assert_eq "…with Recent the selected row" "true false false" \
      "$(grep -o '<node[^>]*resource-id="files_pane:recent"[^>]*>' "$ROW_DIR/t1.xml" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2) $(grep -o '<node[^>]*resource-id="files_pane:device"[^>]*>' "$ROW_DIR/t1.xml" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2) $(grep -o '<node[^>]*resource-id="files_pane:bin"[^>]*>' "$ROW_DIR/t1.xml" | grep -o 'selected="[a-z]*"' | cut -d'"' -f2)"
    assert_eq "…and the Recent page behind it (the crumb)" "Recent" "$(X t1 files_crumb:0)"
    assert_contains "[files] recent: <n> is read at that start" "[files] recent: " "$(ring_since "$m")"
    record "the app list's hold menu on Files (Pin to Start present, Uninstall absent)" "E10"
    c6; ensure_start
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ PROPS
edge_PROPS() {
  if edge_begin PROPS "Properties' values for b.bin and sub, from the fixture table" nopubvol; then
    local size date
    size="$(fx_detail b.bin | sed 's/ [0-9/]*$//')"; date="$(fx_detail b.bin | awk '{ print $NF }')"
    files_at "$QF"; scroll_to_node "$ROW_DIR/p0.xml" files_row:b.bin >/dev/null; hold p0 files_row:b.bin; D p1; T p1 files_hold:properties 1.5; D p2; S p2
    assert_eq "b.bin: Properties is a page (files_properties), with no app bar node" "yes no" "$(H p2 files_properties) $(H p2 files_appbar)"
    assert_eq "b.bin: File size: reads the table's size ($size)" "File size: $size" "$(X p2 files_prop_label:size) $(X p2 files_prop_value:size)"
    assert_eq "b.bin: Date modified: reads the table's date ($date)" "Date modified: $date" "$(X p2 files_prop_label:date) $(X p2 files_prop_value:date)"
    assert_eq "b.bin: the breadcrumb's path segments equal the file's folder, then its name" "This Device QA-Files b.bin" "$(X p2 files_crumb:0) $(X p2 files_crumb:1) $(X p2 files_crumb:2)"
    record "b.bin: File type:" "$(X p2 files_prop_value:type)"
    adb shell input keyevent KEYCODE_BACK; sleep 1
    files_at "$QF"; scroll_to_node "$ROW_DIR/p3.xml" files_row:sub >/dev/null; hold p3 files_row:sub; D p4; T p4 files_hold:properties 1.5; D p5; S p5
    assert_eq "sub: Properties' page, the last crumb its name, the table's date" "yes sub $(fx_detail sub/)" "$(H p5 files_properties) $(X p5 files_crumb:2) $(X p5 files_prop_value:date)"
    record "sub: its type / its size row (no size is asserted for a folder)" "$(X p5 files_prop_value:type) / $(X p5 files_prop_value:size)"
    adb shell input keyevent KEYCODE_BACK; sleep 1
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ BIN
edge_BIN() {
  if edge_begin BIN "the Recycle Bin's index cases" nopubvol; then
    local m md5a md5s bn stray
    EDGE_UNDO=_undo_restored
    _undo_restored() { dev_script bin-undo >/dev/null <<'SH'
[ -d /sdcard/Download/Restored ] && rm -r /sdcard/Download/Restored
SH
      EDGE_UNDO=""; }
    # (4) two files of one name deleted from different folders
    adb shell "printf 'same, in the root' > /sdcard/QA-Files/same.txt; printf 'same, in sub' > /sdcard/QA-Files/sub/same.txt"
    md5a="$(md5dev /sdcard/QA-Files/same.txt)"; md5s="$(md5dev /sdcard/QA-Files/sub/same.txt)"
    bin_it "$QF" same.txt; bin_it "$QF/sub" same.txt
    files_open --es page bin; D b1; S b1
    assert_eq "same name, different folders: two bin rows" "2" "$(count_nodes b1 files_bin_row:same.txt)"
    assert_ne "…kept apart by their bin names (the deleted-at prefix and <seq>)" "$(bin_name_of "$QF/same.txt")" "$(bin_name_of "$QF/sub/same.txt")"
    T b1 files_bar:select 1; D b2; tap_nth b2 files_bin_row:same.txt 1; D b3; tap_nth b3 files_bin_row:same.txt 2; D b4; T b4 files_bin_restore 2
    assert_eq "…each restoring to its own path (md5s)" "$md5a $md5s" "$(md5dev /sdcard/QA-Files/same.txt) $(md5dev /sdcard/QA-Files/sub/same.txt)"
    # (2) an index record whose file another app deleted
    bin_it "$QF" img-0.png; bin_it "$QF" img-1.png
    bn="$(bin_name_of "$QF/img-0.png")"
    assert_eq "two records are in the index" "2" "$(bin_index_count)"
    adb shell "rm '$BINP/$bn'"
    m="$(ring_mark)"; files_open --es page bin; D b5
    assert_eq "a record whose file another app deleted is dropped on the next read: the page lists only the other" "no yes" "$(H b5 files_bin_row:img-0.png) $(H b5 files_bin_row:img-1.png)"
    record "the index line on that read / the records the index file holds now" "$(ring_since "$m" | grep -o '\[files\] bin index.*' | sed 's/ *wall=.*//' | tail -1) / $(bin_index_count)"
    assert_contains "…[files] bin index …: 1 entries" "[files] bin index $PRIMARY: 1 entries" "$(ring_since "$m")"
    # (3) the original folder is now a FILE of that name
    adb shell "mkdir -p /sdcard/QA-Files/wasdir && printf inside > /sdcard/QA-Files/wasdir/x.txt"
    bin_it "$QF/wasdir" x.txt
    dev_script bin-file >/dev/null <<'SH'
rm -r /sdcard/QA-Files/wasdir
printf 'now a file' > /sdcard/QA-Files/wasdir
SH
    assert_eq "the original folder is now a FILE of that name" "regular file" "$(q 'stat -c %F /sdcard/QA-Files/wasdir')"
    binsel x.txt; T b2 files_bin_restore 1.5; D b6; S b6
    assert_eq "restore: the conflict dialog is asked (replace / keep_both / skip)" "files_dialog:replace files_dialog:keep_both files_dialog:skip" "$(ids b6 | tr ' ' '\n' | grep '^files_dialog:' | xargs)"
    record "the dialog's title" "$(X b6 files_dialog_title)"
    T b6 files_dialog:skip 1.5
    assert_eq "skip: the file of that name is untouched and x.txt stays in the bin" "now a file 1" "$(q 'cat /sdcard/QA-Files/wasdir') $(bin_index | grep -c "|$QF/wasdir/x.txt")"
    # (5) a bin file with no index record
    adb shell "cp /sdcard/QA-Files/b.bin '$BINP/stray-copy.bin'"
    files_open --es page bin; D b7; S b7
    assert_eq "a bin file with no index record is listed by its bin name" "yes" "$(H b7 files_bin_row:stray-copy.bin)"
    binsel stray-copy.bin; m="$(ring_mark)"; T b2 files_bin_restore 2
    stray="$(q 'ls /sdcard/Download/Restored 2>/dev/null' | xargs)"
    record "Download/Restored after its restore / the line" "[$stray] / $(ring_since "$m" | grep -o '\[files\] bin restore.*' | sed 's/ *wall=.*//')"
    assert_eq "…Restore puts it in /sdcard/Download/Restored/ (one file there with the file's md5)" "1" "$(q 'md5sum /sdcard/Download/Restored/* 2>/dev/null' | grep -c "^$(fx_md5 b.bin) ")"
    # (1) .index.json deleted by hand
    bn="$(bin_name_of "$QF/img-1.png")"
    adb shell "rm '$BINP/.index.json'"
    m="$(ring_mark)"; files_open --es page bin; D b8; S b8
    assert_contains "index deleted by hand: [files] bin index …: rebuilt (index missing)" "[files] bin index $PRIMARY: rebuilt (index missing)" "$(ring_since "$m")"
    assert_eq "…the bin lists its files by bin name" "yes" "$(H b8 "files_bin_row:$bn")"
    binsel "$bn"; m="$(ring_mark)"; T b2 files_bin_restore 2
    record "Download/Restored after that restore / the line" "[$(q 'ls /sdcard/Download/Restored 2>/dev/null' | xargs)] / $(ring_since "$m" | grep -o '\[files\] bin restore.*' | sed 's/ *wall=.*//')"
    assert_eq "…Restore puts it in /sdcard/Download/Restored/ (one file there with img-1.png's md5)" "1" "$(q 'md5sum /sdcard/Download/Restored/* 2>/dev/null' | grep -c "^$(fx_md5 img-1.png) ")"
    assert_eq "…and it is gone from the bin folder" "" "$(lsdev "$BINP/$bn")"
    record "the same-millisecond pair" "FileOps' JVM case (E18)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ BIN_PUBVOL
edge_BIN_PUBVOL() {
  if edge_begin BIN_PUBVOL "delete and Restore on the public volume" pubvol; then
    local v="$PUBVOL_PATH" md5b m
    md5b="$(fx_md5 b.bin)"
    adb shell "cp /sdcard/QA-Files/b.bin $v/pv.bin"; sleep 1
    bin_it "$v" pv.bin
    assert_contains "[files] bin delete $v/pv.bin: ok" "[files] bin delete $v/pv.bin: ok" "$(ring_since "$BIN_MARK")"
    assert_eq "the file is in THAT volume's bin (md5), not in the primary one" "$md5b 0" "$(md5dev "$v/.Tessera/bin/$(bin_name_of "$v/pv.bin" "$v/.Tessera/bin")") $(q "ls $BINP 2>/dev/null" | grep -c 'pv\.bin')"
    files_open --es page bin; D p1; S p1
    assert_contains "the bin page names its volume" "Virtual SD card" "$(X p1 files_detail:pv.bin)"
    binsel pv.bin; m="$(ring_mark)"; T b2 files_bin_restore 2
    assert_contains "[files] bin restore $v/pv.bin: ok" "[files] bin restore $v/pv.bin: ok" "$(ring_since "$m")"
    assert_eq "Restore puts the file back on that volume (md5), its bin empty of it" "$md5b 0" "$(md5dev "$v/pv.bin") $(q "ls $v/.Tessera/bin" | grep -c 'pv\.bin')"
    assert_eq "nothing of it is on the primary volume" "" "$(q "find /sdcard/QA-Files /sdcard/.Tessera -name '*pv.bin' 2>/dev/null" | xargs)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ KEEP_BOTH
edge_KEEP_BOTH() {
  if edge_begin KEEP_BOTH "a conflict answered keep both leaves b (2).bin" nopubvol; then
    local m md5b k
    md5b="$(fx_md5 b.bin)"
    adb shell "cp /sdcard/QA-Files/b.bin /sdcard/QA-Files/sub/b.bin; printf 'the one already there' > /sdcard/QA-Files/b.bin"
    for k in 2 3; do
      files_at "$QF/sub"; m="$(ring_mark)"
      hold_op copy b.bin ..
      sleep 1.5; D "k$k"
      assert_eq "copy sub/b.bin over b.bin (#$k): the conflict dialog" "files_dialog:replace files_dialog:keep_both files_dialog:skip" "$(ids "k$k" | tr ' ' '\n' | grep '^files_dialog:' | xargs)"
      T "k$k" files_dialog:keep_both 0.5; wait_op "$m" 20 >/dev/null
      assert_eq "keep both (#$k) leaves b ($k).bin with the copy's md5" "$md5b" "$(md5dev "/sdcard/QA-Files/b ($k).bin")"
    done
    assert_eq "the file that was there is untouched" "the one already there" "$(q 'cat /sdcard/QA-Files/b.bin')"
    files_at "$QF"; scroll_to_node "$ROW_DIR/k9.xml" "files_row:b (2).bin" >/dev/null
    assert_eq "Files lists b (2).bin" "yes" "$(H k9 "files_row:b (2).bin")"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ ZIP
edge_ZIP() {
  if edge_begin ZIP "zip64 (70,000 entries), CP437 names, a zip in the bin" nopubvol zips; then
    local m l dt
    files_at "$ZD"; scroll_to_node "$ROW_DIR/z0.xml" files_row:qa-zip64.zip >/dev/null
    m="$(ring_mark)"; tap_node "$ROW_DIR/z0.xml" files_row:qa-zip64.zip
    l="$(wait_line "$m" "[files] zip open $ZD/qa-zip64.zip: 70000 entries" 15)"
    dt=""; [ -n "$l" ] && dt=$(( $(printf '%s' "$l" | wall_of) - m ))
    record "zip64: ms from the MARK before the tap to the line" "${dt:-no line}"
    assert_contains "zip64: [files] zip open …/qa-zip64.zip: 70000 entries" "70000 entries" "$l"
    assert_eq "zip64: …within 5 s of the tap" "yes" "$(yes_if test -n "$dt" -a "${dt:-99999}" -le 5000)"
    sleep 2; D z1; S z1
    assert_eq "zip64: the first row is e00001.txt" "e00001.txt" "$(first_row z1)"
    if fling_end files_row:e70000.txt z2 40 400; then _verdict PASS "zip64: scrolled to the end — the last entry's row (e70000.txt) is reached" "z2.xml"
    else _verdict FAIL "zip64: scrolled to the end — the last entry's row (e70000.txt) is reached" "not in the dump after the flings; the last row there is $(last_row z2)"; fi
    S z2
    assert_eq "zip64: at the end the last row is e70000.txt" "e70000.txt" "$(last_row z2)"
    assert_eq "zip64: no ANR in app.tileshell (logcat)" "0" "$(anr)"
    # CP437 names without the UTF-8 flag
    m="$(ring_mark)"; zopen qa-cp437.zip; D z3; S z3
    assert_eq "qa-cp437.zip lists its entry decoded (files_row:café.txt)" "yes" "$(H z3 files_row:café.txt)"
    record "qa-cp437.zip: its rows / the line" "$(row_names z3) / $(ring_since "$m" | grep -o '\[files\] zip open.*' | sed 's/ *wall=.*//')"
    # a zip opened from the bin: bin rows open nothing
    bin_it "$ZD" qa.zip
    files_open --es page bin; D z4
    assert_eq "qa.zip is in the bin" "yes" "$(H z4 files_bin_row:qa.zip)"
    m="$(ring_mark)"; T z4 files_bin_row:qa.zip 2; D z5
    assert_eq "a tap on the binned zip opens nothing: still the bin page, no virtual root, Files on top" "Recycle Bin no $FILES_ACTIVITY" "$(X z5 files_crumb:0) $(H z5 files_zip_root) $(top_activity)"
    assert_eq "…and no zip open line was written" "0" "$(ring_since "$m" | grep -c '\[files\] zip open')"
    binsel qa.zip; T b2 files_bin_restore 2
    zopen qa.zip; D z6
    assert_eq "Restore first, then it opens (files_zip_root)" "yes" "$(H z6 files_zip_root)"
  fi
  edge_end
}

# ------------------------------------------------------------------------------------------------ LIVENESS
edge_LIVENESS() {
  if edge_begin LIVENESS "liveness: a reboot and a force-stop (N-01)" nopubvol; then
    local idx0 bin0 rec0 jr0 m
    RF=$QF/recent
    bin_it "$QF" img-0.png
    m="$(ring_mark)"; tap_row "$RF" r1.png 2.5; adb shell input keyevent KEYCODE_BACK; sleep 1
    assert_contains "a file is opened, so Recent holds an entry" "[files] recent add $RF/r1.png" "$(ring_since "$m")"
    adb shell input keyevent KEYCODE_HOME; sleep 1
    idx0="$(md5dev "$BINP/.index.json")"; bin0="$(bin_ls)"; rec0="$(recent_json)"; jr0="$(journal_json)"
    record "before: grant / checklist row / bin listing / index md5 / journal" "$(appop_now) / $(checklist_files l0) / $bin0 / $idx0 / [${jr0}]"
    assert_eq "before: the grant, the checklist row" "MANAGE_EXTERNAL_STORAGE: allow granted" "$(appop_now) $(checklist_files l0)"
    assert_eq "before: with no operation running the journal names no temp" "0" "$(printf '%s' "$jr0" | grep -cE '\.part|\.extract')"
    state_same() { # tag
      assert_eq "$1: the grant (appops get)" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
      assert_eq "$1: the checklist row Files is green" "granted" "$(checklist_files "l-$1")"
      assert_eq "$1: the bin and its index (md5 of .index.json) are intact" "$bin0 $idx0" "$(bin_ls) $(md5dev "$BINP/.index.json")"
      assert_eq "$1: the Recent list (files-recent.json) is intact" "$rec0" "$(recent_json)"
      assert_eq "$1: the operations journal is intact and names no temp" "[$jr0] 0" "[$(journal_json)] $(journal_json | grep -cE '\.part|\.extract')"
      files_open --es page recent; D "l-$1-recent"
      assert_eq "$1: the Recent page lists the file" "r1.png" "$(recent_rows "l-$1-recent")"
      files_open --es page bin; D "l-$1-bin"
      assert_eq "$1: the bin page lists the binned file" "yes" "$(H "l-$1-bin" files_bin_row:img-0.png)"
      adb shell input keyevent KEYCODE_HOME; sleep 1
    }
    c6; ensure_start
    state_same "after a force-stop"
    rings_save
    adb reboot
    boot_wait
    assert_eq "after the reboot: wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
    ensure_start; sleep 3
    state_same "after a reboot"
    record "Device care's optimise" "P1 (the phone)"
    c6; ensure_start
  fi
  edge_end
}

if [ "${1:-}" = --list ]; then printf '%s\n' $ALL_IDS; exit 0; fi
IDS="${*:-$ALL_IDS}"
for id in $IDS; do
  case " $ALL_IDS " in
    *" $id "*) "edge_$id" ;;
    *) echo "edge_files.sh: no sub-step named [$id] (edge_files.sh --list)" >&2; exit 2 ;;
  esac
done
if [ -n "$FAILED_ROWS" ]; then echo "edge_files.sh: failed:$FAILED_ROWS"; exit 1; fi
echo "edge_files.sh: every named sub-step passed"
