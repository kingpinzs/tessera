#!/usr/bin/env bash
# Phase 18 E4: operations (W10M's verbs: select, then "Copy to" / "Move to" and pick the folder in Files' picker).
#
#   copy      "Copy to" sub for b.bin → md5 equal on both, `[files] copy 1 files 307200 -> …/sub done`
#   conflict  copy sub/b.bin back over b.bin → the dialog offers replace / keep both / skip, and each does what it says
#             (the parent's b.bin is first given other bytes, so "replace" can be told from "skip"): skip → nothing
#             changes and no line (BUILD-NOTES pure layer 4); keep both → `b (2).bin` (r3 D6); replace → the parent's
#             b.bin has the copy's md5
#   move      "Move to" sub for a.txt → gone from the parent, present in sub, md5 unchanged
#   rename    b.bin → c.bin through files_dialog_input / files_dialog:ok (`ls`)
#   folder    new folder n (`ls -d`)
#   delete    c.bin after the confirmation → the Recycle Bin (E4b's assertions: gone, `<ms>-<seq>-c.bin` in the bin
#             with the md5, `bin delete …: ok`)
#   big       copy big.bin (paced, 8 MB/s) → files_progress_text "Copying files…" (gdump), the notification in dumpsys
#             notification with a "Cancel" action, HOME mid-copy (a progress line with 0 < bytes < total) leaves the
#             service running — isForeground=true and its type holding dataSync — the copy completes with an equal md5
#             and Files, reopened, shows the destination folder
#   xmove     a Move of big.bin reads "Moving files…" and ends on the destination folder. A same-volume move is a
#             rename with no bytes (Decisions 2026-10-06 (3)), so the move goes to the row's own public volume.
#   cancel    cancel mid-copy from the notification's action → `[files] copy … cancelled`, no file and no temp left
#             (`ls -a`: nothing matching .*.part)
#   kill      `am force-stop app.tileshell` mid-copy → a .big.bin.<opid>.part may remain (RECORDED); Home, relaunch →
#             after the sweep `find /sdcard/ -name '.*.part'` is empty, `[files] sweep: removed <n>`, the source's md5
#             unchanged
#
# Paths in the ring's lines are the real ones (/storage/emulated/0/…; BUILD-NOTES pure layer 2).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"

keep_earlier_run E4
row_begin E4 "operations: copy, move, rename, new folder, delete, the conflict's three answers, big.bin's progress / Home / cancel / kill"
assert_gate_apk
baseline_start
adb logcat -c
files_up paced big || { row_end; exit 1; }
pubvol_up >/dev/null || { files_down; row_end; exit 1; }
sleep 2
V="$PUBVOL_PATH"
MD5_B="$(fx_md5 b.bin)"; MD5_A="$(fx_md5 a.txt)"; MD5_BIG="$(fx_md5 big.bin)"
assert_ne "fixtures: b.bin, a.txt and big.bin have recorded md5s" "" "${MD5_B:+x}${MD5_A:+x}${MD5_BIG:+x}"
ensure_start

# ------------------------------------------------------------------------------------------------ copy b.bin → sub
log "--- Copy to sub for b.bin"
files_at "$QF"; M="$(ring_mark)"
pick_op copy b.bin sub
L="$(wait_op "$M" 20)"; SL="$(ring_since "$M")"
assert_contains "copy b.bin: [files] copy 1 files 307200 -> …/sub done" "[files] copy 1 files 307200 -> $QF/sub done" "$SL"
assert_eq "copy b.bin: md5 of the copy = the fixture's" "$MD5_B" "$(md5dev /sdcard/QA-Files/sub/b.bin)"
assert_eq "copy b.bin: md5 of the original unchanged" "$MD5_B" "$(md5dev /sdcard/QA-Files/b.bin)"
assert_contains "copy b.bin (paced row): [files] qa pace $PACE_BPS" "[files] qa pace $PACE_BPS" "$SL"
D 01-after-copy
assert_eq "copy b.bin: Files lands on the folder holding the output (last crumb sub, the row listed)" "sub yes" "$(X 01-after-copy files_crumb:2) $(H 01-after-copy files_row:b.bin)"

# ------------------------------------------------------------------------------------------------ the conflict
log "--- conflict: copy sub/b.bin back over b.bin — skip, keep both, replace"
adb shell "printf changed > /sdcard/QA-Files/b.bin"
MD5_CHANGED="$(md5dev /sdcard/QA-Files/b.bin)"
assert_ne "conflict set-up: the parent's b.bin now differs from sub/b.bin" "$MD5_B" "$MD5_CHANGED"
conflict() { # answer
  files_at "$QF/sub"; CM="$(ring_mark)"
  hold_op copy b.bin ..
  sleep 1.5; D "c-$1"; S "c-$1"
  assert_eq "conflict ($1): the dialog offers exactly replace / keep_both / skip" "files_dialog:replace files_dialog:keep_both files_dialog:skip" \
    "$(ids "c-$1" | tr ' ' '\n' | grep '^files_dialog:' | xargs)"
  T "c-$1" "files_dialog:$1" 0.5
  if [ "$1" = skip ]; then sleep 2; else wait_op "$CM" 20 >/dev/null; fi
  CSL="$(ring_since "$CM")"
  D "c-$1-after"
  assert_eq "conflict ($1): the dialog is gone" "no" "$(H "c-$1-after" files_dialog)"
}
conflict skip
assert_eq "skip: the parent's b.bin is untouched" "$MD5_CHANGED" "$(md5dev /sdcard/QA-Files/b.bin)"
assert_eq "skip: no b (2).bin" "" "$(lsdev '/sdcard/QA-Files/b (2).bin')"
# RECORDED, not asserted: BUILD-NOTES says a skipped conflict writes no line for restore / extract / create — it does not
# say so for a copy, and the build writes the operation's summary line all the same (run 1 of this row).
record "skip: the copy's own line after a skip (the operation's summary; nothing was written)" "$(printf '%s\n' "$CSL" | grep -o '\[files\] copy 1 files.*' | sed 's/ *wall=.*//' | tail -1)"
conflict keep_both
assert_eq "keep both: b (2).bin holds the copy (md5)" "$MD5_B" "$(md5dev '/sdcard/QA-Files/b (2).bin')"
assert_eq "keep both: the parent's b.bin is untouched" "$MD5_CHANGED" "$(md5dev /sdcard/QA-Files/b.bin)"
assert_contains "keep both: [files] copy 1 files 307200 -> …/QA-Files done" "[files] copy 1 files 307200 -> $QF done" "$CSL"
conflict replace
assert_eq "replace: the parent's b.bin now has the copy's md5" "$MD5_B" "$(md5dev /sdcard/QA-Files/b.bin)"
# Gate build 2 (the review's H3): Replace no longer deletes what it replaces — the replaced item goes to the Recycle Bin.
assert_contains "replace: the replaced b.bin goes to the bin with its own line" "[files] bin delete $QF/b.bin: ok" "$CSL"
RB="$(bin_name_of "$QF/b.bin")"
assert_eq "replace: …and is in the bin with the bytes it had (md5 of the replaced file)" "$MD5_CHANGED" "$(md5dev "$BINP/$RB")"
files_open --es page bin; D c-replace-bin
assert_eq "replace: …and appears as a bin row" "yes" "$(H c-replace-bin files_bin_row:b.bin)"
assert_eq "replace: b (2).bin is still the one kept before, and no b (3).bin" "$MD5_B|" "$(md5dev '/sdcard/QA-Files/b (2).bin')|$(lsdev '/sdcard/QA-Files/b (3).bin')"
assert_contains "replace: [files] copy 1 files 307200 -> …/QA-Files done" "[files] copy 1 files 307200 -> $QF done" "$CSL"
assert_eq "conflict: sub/b.bin (the source) is unchanged throughout" "$MD5_B" "$(md5dev /sdcard/QA-Files/sub/b.bin)"

# ------------------------------------------------------------------------------------------------ move a.txt → sub
log "--- Move to sub for a.txt"
files_at "$QF"; M="$(ring_mark)"
pick_op move a.txt sub
wait_op "$M" 20 >/dev/null; SL="$(ring_since "$M")"
assert_contains "move a.txt: [files] move 1 files 10 -> …/sub done" "[files] move 1 files 10 -> $QF/sub done" "$SL"
assert_eq "move a.txt: gone from the parent" "" "$(lsdev /sdcard/QA-Files/a.txt)"
assert_eq "move a.txt: present in sub with its md5 unchanged" "$MD5_A" "$(md5dev /sdcard/QA-Files/sub/a.txt)"
absent_in "move a.txt: a same-volume move is a rename — no progress line" "move progress" "$SL"

# ------------------------------------------------------------------------------------------------ rename b.bin → c.bin
log "--- rename b.bin → c.bin"
files_at "$QF"; scroll_to_node "$ROW_DIR/r0.xml" files_row:b.bin >/dev/null; hold r0 files_row:b.bin; D r1; T r1 files_hold:rename 1.5; D r2; S r2
assert_eq "rename: the dialog's input holds the name (files_dialog_input)" "b.bin" "$(X r2 files_dialog_input)"
dialog_type c.bin; D r3
assert_eq "rename: the typed name replaces it" "c.bin" "$(X r3 files_dialog_input)"
M="$(ring_mark)"; T r3 files_dialog:ok 2
assert_eq "rename: ls shows c.bin and no b.bin" "/sdcard/QA-Files/c.bin|" "$(lsdev /sdcard/QA-Files/c.bin)|$(lsdev /sdcard/QA-Files/b.bin)"
assert_eq "rename: md5 unchanged" "$MD5_B" "$(md5dev /sdcard/QA-Files/c.bin)"
assert_contains "rename: [files] rename …/b.bin -> c.bin: ok" "[files] rename $QF/b.bin -> c.bin: ok" "$(ring_since "$M")"

# ------------------------------------------------------------------------------------------------ new folder n
log "--- new folder n"
files_at "$QF"; D n0; T n0 files_bar:new_folder 1.5; D n1; S n1
assert_eq "new folder: the dialog (files_dialog_input present)" "yes" "$(H n1 files_dialog_input)"
dialog_type n; D n2
assert_eq "new folder: the typed name" "n" "$(X n2 files_dialog_input)"
M="$(ring_mark)"; T n2 files_dialog:ok 2
assert_eq "new folder: ls -d /sdcard/QA-Files/n" "/sdcard/QA-Files/n" "$(lsdev /sdcard/QA-Files/n)"
assert_contains "new folder: [files] new folder …/n: ok" "[files] new folder $QF/n: ok" "$(ring_since "$M")"

# ------------------------------------------------------------------------------------------------ delete c.bin → the bin
log "--- delete c.bin after the confirmation"
files_at "$QF"; scroll_to_node "$ROW_DIR/x0.xml" files_row:c.bin >/dev/null; hold x0 files_row:c.bin; D x1; T x1 files_hold:delete 1; D x2; S x2
assert_eq "delete: the confirmation (files_dialog_title)" "Delete this item?" "$(X x2 files_dialog_title)"
M="$(ring_mark)"; T x2 files_dialog:ok 2
SL="$(ring_since "$M")"
assert_eq "delete: ls /sdcard/QA-Files/c.bin fails" "" "$(lsdev /sdcard/QA-Files/c.bin)"
BINNAME="$(q "ls $BINP" | grep -E '^[0-9]+-[0-9]+-c\.bin$')"
assert_ne "delete: the bin lists <ms>-<seq>-c.bin" "" "$BINNAME"
assert_eq "delete: the bin file's md5 is the file's" "$MD5_B" "$(md5dev "$BINP/$BINNAME")"
assert_contains "delete: [files] bin delete …/c.bin: ok" "[files] bin delete $QF/c.bin: ok" "$SL"

# ------------------------------------------------------------------------------------------------ copy big.bin
log "--- copy big.bin → sub (paced): the box, the notification, Home mid-copy, completion"
ensure_start; files_at "$QF"; S 10-before; M="$(ring_mark)"
pick_op copy big.bin sub
mid_progress copy "$M" 20 > "$ROW_DIR/10-mid.txt"
assert_contains "big copy: [files] qa pace $PACE_BPS (a paced run cannot be read as an unpaced one)" "[files] qa pace $PACE_BPS" "$(ring_since "$M")"
G 11-progress; S 11-progress
assert_eq "big copy: files_progress_text reads Copying files… (gdump)" "Copying files…" "$(X 11-progress files_progress_text)"
adb shell dumpsys notification --noredact > "$ROW_DIR/12-notification.txt"
python3 - "$ROW_DIR/12-notification.txt" > "$ROW_DIR/12-notification-record.txt" <<'PY'
import re, sys
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
recs = re.split(r"\n(?=\s+NotificationRecord\()", t)
for r in recs:
    if "pkg=app.tileshell" in r and "channel=files_ops" in r:
        title = re.search(r"android\.title=String \(([^)]*)\)", r)
        acts = re.findall(r'\[\d+\] "([^"]*)" -> PendingIntent', r)
        print("title=[%s] actions=[%s] flags=%s" % (title.group(1) if title else "", ",".join(acts), (re.search(r"flags=([A-Z_|]+)", r) or [None, ""])[1]))
        break
PY
NREC="$(cat "$ROW_DIR/12-notification-record.txt")"
assert_contains "big copy: the progress notification is in dumpsys notification (channel files_ops)" "title=[Copying files…]" "$NREC"
assert_contains "big copy: …with a Cancel action" "actions=[Cancel]" "$NREC"
adb shell input keyevent KEYCODE_HOME; sleep 1.5; M2="$(ring_mark)"
mid_progress copy "$M2" 10 > "$ROW_DIR/13-mid-home.txt"
assert_ne "big copy: Home mid-copy — Files is no longer on top" "$FILES_ACTIVITY" "$(top_activity)"
adb shell dumpsys activity services app.tileshell > "$ROW_DIR/14-services.txt"
SVC="$(python3 - "$ROW_DIR/14-services.txt" <<'PY'
import re, sys
t = open(sys.argv[1], encoding="utf-8", errors="replace").read()
m = re.search(r"\* ServiceRecord\{[^}]*app\.tileshell/\.files\.FileOpsService[^}]*\}(.*?)(?=\n\s+\* ServiceRecord\{|\Z)", t, re.S)
if m:
    fg = re.search(r"isForeground=(\w+) foregroundId=\d+ types=(0x[0-9a-fA-F]+)", m.group(1))
    if fg: print("isForeground=%s types=%s dataSync=%s" % (fg.group(1), fg.group(2), "yes" if int(fg.group(2), 16) & 1 else "no"))
    else: print("record found, no isForeground line")
PY
)"
record "big copy, after Home: FileOpsService in dumpsys activity services" "$SVC"
assert_contains "big copy, after Home: the service is isForeground=true" "isForeground=true" "$SVC"
assert_contains "big copy, after Home: its type holds dataSync (types & FOREGROUND_SERVICE_TYPE_DATA_SYNC 0x1)" "dataSync=yes" "$SVC"
L="$(wait_op "$M" 60)"
assert_contains "big copy: it completes — [files] copy 1 files 209715200 -> …/sub done" "[files] copy 1 files 209715200 -> $QF/sub done" "$L"
assert_eq "big copy: md5 of the copy = big.bin's" "$MD5_BIG" "$(md5dev /sdcard/QA-Files/sub/big.bin)"
assert_eq "big copy: no temp left in sub (ls -a: nothing matching .*.part)" "" "$(q 'ls -a /sdcard/QA-Files/sub' | grep -E '^\..*\.part$')"
files_open; sleep 1; D 15-reopened; S 15-reopened
assert_eq "big copy: Files, reopened, shows the destination folder (crumbs QA-Files › sub, big.bin listed, no box)" "QA-Files sub yes no" \
  "$(X 15-reopened files_crumb:1) $(X 15-reopened files_crumb:2) $(H 15-reopened files_row:big.bin) $(H 15-reopened files_progress)"
ring_since "$M" > "$ROW_DIR/ring-bigcopy.txt"

# ------------------------------------------------------------------------------------------------ move big.bin → the volume
log "--- Move of big.bin to the row's public volume (a cross-volume move has bytes): Moving files…"
files_at "$QF"; M="$(ring_mark)"
pick_op_pane move big.bin "$PUBVOL_UUID"
mid_progress move "$M" 20 > "$ROW_DIR/20-mid.txt"
G 21-moving; S 21-moving
assert_eq "big move: files_progress_text reads Moving files… (gdump)" "Moving files…" "$(X 21-moving files_progress_text)"
L="$(wait_op "$M" 90)"
assert_contains "big move: [files] move 1 files 209715200 -> $V done" "[files] move 1 files 209715200 -> $V done" "$L"
sleep 1.5; scroll_to_node "$ROW_DIR/22-landed.xml" files_row:big.bin >/dev/null; S 22-landed   # the root lists its folders first
assert_eq "big move: it ends on the destination folder (the volume's root: crumb 0 = its label, big.bin listed, no box)" "Virtual SD card yes no" \
  "$(X 22-landed files_crumb:0) $(H 22-landed files_row:big.bin) $(H 22-landed files_progress)"
assert_eq "big move: the source is gone" "" "$(lsdev /sdcard/QA-Files/big.bin)"
assert_eq "big move: md5 on the volume = big.bin's" "$MD5_BIG" "$(md5dev "$V/big.bin")"
assert_eq "big move: no temp on either side" "" "$(q "ls -a $V /sdcard/QA-Files" | grep -E '^\..*\.part$')"

# ------------------------------------------------------------------------------------------------ cancel mid-copy
log "--- cancel mid-copy (the notification's action): sub/big.bin → n"
ensure_start; files_at "$QF/sub"; M="$(ring_mark)"
hold_op copy big.bin .. n
mid_progress copy "$M" 20 > "$ROW_DIR/30-mid.txt"
if notif_cancel 31; then _verdict PASS "cancel: the notification's Cancel action was found and tapped" "31-shade*.xml"
else _verdict FAIL "cancel: the notification's Cancel action was found and tapped" "no Cancel node in the shade (31-shade*.xml)"; fi
L="$(wait_op "$M" 20)"
assert_contains "cancel: [files] copy 1 files 209715200 -> …/n cancelled" "[files] copy 1 files 209715200 -> $QF/n cancelled" "$L"
record "cancel: ls -a /sdcard/QA-Files/n" "[$(q 'ls -a /sdcard/QA-Files/n' | xargs)]"
assert_eq "cancel: no file left in the destination" "" "$(lsdev /sdcard/QA-Files/n/big.bin)"
assert_eq "cancel: no temp left (ls -a: nothing matching .*.part)" "" "$(q 'ls -a /sdcard/QA-Files/n' | grep -E '^\..*\.part$')"
assert_eq "cancel: the source's md5 is unchanged" "$MD5_BIG" "$(md5dev /sdcard/QA-Files/sub/big.bin)"

# ------------------------------------------------------------------------------------------------ cancel mid-move
# Beyond E4's own text: E12 lists `[files] move … cancelled` and the doc names no row that produces it (E12/producers.tsv,
# the floor's note of 2026-10-06 14:58). The cancel leg is repeated on a cross-volume MOVE so that line has a producer.
log "--- cancel mid-move (for E12's move … cancelled line): sub/big.bin → the volume's folder m"
adb shell "mkdir -p $V/m"
ensure_start; files_at "$QF/sub"; M="$(ring_mark)"
pick_op_pane move big.bin "$PUBVOL_UUID" m
mid_progress move "$M" 20 > "$ROW_DIR/35-mid.txt"
if notif_cancel 36; then _verdict PASS "cancel mid-move: the notification's Cancel action was found and tapped" "36-shade*.xml"
else _verdict FAIL "cancel mid-move: the notification's Cancel action was found and tapped" "no Cancel node in the shade (36-shade*.xml)"; fi
L="$(wait_op "$M" 20)"
assert_contains "cancel mid-move: [files] move 1 files 209715200 -> $V/m cancelled" "[files] move 1 files 209715200 -> $V/m cancelled" "$L"
assert_eq "cancel mid-move: the original is still there with its md5 (never neither)" "$MD5_BIG" "$(md5dev /sdcard/QA-Files/sub/big.bin)"
assert_eq "cancel mid-move: no file and no temp in the destination" "|" "$(lsdev "$V/m/big.bin")|$(q "ls -a $V/m" | grep -E '^\..*\.part$')"

# ------------------------------------------------------------------------------------------------ kill mid-copy
log "--- kill mid-copy (r3 D4): force-stop, Home, relaunch, the sweep"
ensure_start; files_at "$QF/sub"; M="$(ring_mark)"
hold_op copy big.bin .. n
mid_progress copy "$M" 20 > "$ROW_DIR/40-mid.txt"
sleep 2
rings_save
# The MARK is taken BEFORE the force-stop: the shell is the HOME app, Android brings its process back at once, and the
# sweep runs at that process's start (FilesEnv) — within a second of the kill (run 2 of this row: `sweep: removed 1`
# was written before a MARK taken after it). The temp is looked for at once, with no pause, and RECORDED.
M2="$(ring_mark)"
adb shell "am force-stop app.tileshell; find /sdcard/QA-Files -name '.*.part'" > "$ROW_DIR/41-part-at-kill.txt" 2>&1
PART="$(tr -d '\r' < "$ROW_DIR/41-part-at-kill.txt" | head -1)"
record "kill: the temp left by the force-stop (find -name '.*.part')" "[${PART}] $(q "ls -la '$PART' 2>/dev/null" | awk '{ print $5 " bytes" }')"
ensure_start; files_open
SW="$(ring_since "$M2" | grep -E '\[files\] sweep: removed [1-9]' | head -1)"
[ -n "$SW" ] || SW="$(wait_line "$M2" "[files] sweep: removed" 15)"
record "kill: the sweep's line" "$(printf '%s' "$SW" | grep -o '\[files\].*' | sed 's/ *wall=.*//')"
assert_contains "kill: after the relaunch [files] sweep: removed <n>" "[files] sweep: removed " "$SW"
assert_eq "kill: …and it removed the killed copy's temp (n >= 1 in the slice from the MARK before the kill)" "yes" "$(ring_since "$M2" | grep -qE '\[files\] sweep: removed [1-9]' && echo yes || echo no)"
assert_eq "kill: after the sweep find /sdcard/ -name '.*.part' is empty" "" "$(q "find /sdcard/ -name '.*.part' 2>/dev/null")"
assert_eq "kill: the source's md5 is unchanged" "$MD5_BIG" "$(md5dev /sdcard/QA-Files/sub/big.bin)"
assert_eq "kill: no big.bin in the destination" "" "$(lsdev /sdcard/QA-Files/n/big.bin)"

# ------------------------------------------------------------------------------------------------ the end
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
adb shell input keyevent KEYCODE_HOME; sleep 1
pubvol_down
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
row_end
