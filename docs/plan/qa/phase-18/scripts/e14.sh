#!/usr/bin/env bash
# Phase 18 E14: Recent (Q-18-1; re-cut 2026-10-06) — the files OPENED in Files, newest first, one entry per path.
#
#   empty     `pm clear app.tileshell` → provision.sh → ensure_start → Files: files_recent_empty reads exactly "You
#             haven't opened any files recently.", (160,160,160) ± 4, left 14.5 ± 2 epx, cap top 88.8 + (STATUS_EPX −
#             24) = 92.8 ± 2 epx (Decisions 2026-10-06 (1)), no files_recent_row node, the bar's buttons dim, `[files]
#             recent: 0`. This leg WIPES the app, so it runs FIRST and the baseline layout is restored right after it;
#             the reinstall-free wipe keeps the gate APK (md5 asserted).
#   order     r1, r2, r3 opened through Files (each `[files] recent add <path>`) → rows r3, r2, r1 exactly, each detail
#             today's date only; a.txt through Android's chooser, backed out of → nothing added (the handlers the AVD
#             offers RECORDED); r1 re-opened → r1, r3, r2
#   never     never.png (the newest file on the phone), a file only selected, a folder and a zip only browsed: absent
#   remove    hold r3 → exactly files_hold:remove_recent / share / properties; Remove from recent → the row gone,
#             `[files] recent remove <path>`, the FILE still present with its md5
#   in step   rename r1 → r1b in Files: the row reads r1b in the same position; delete r2 in Files: its row gone (the
#             file in the bin); r3 opened again then `adb shell rm`: gone on the next read of the page
#   volume    a file opened on the row's own public volume shows a row; `sm unmount` → hidden; `sm mount` → back
#   …         no bin file appears; `[files] recent: <n>` with n ≤ 100; the list survives c6
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
SCRATCH=/tmp/claude-1000/-home-jeremyking/340515de-6745-4b8c-a7bc-95d3c74d1c3e/scratchpad/rows-b
mkdir -p "$SCRATCH"
RF=$QF/recent
VIEWER=app.tileshell/.photos.ViewerActivity
EMPTY_TEXT="You haven't opened any files recently."

keep_earlier_run E14
row_begin E14 "Recent: the empty state after a wipe, order, re-open, never opened, Remove from recent, kept in step, the volume"
assert_gate_apk
baseline_start
adb logcat -c
recent_page() { files_open --es page recent; D "$1"; S "$1"; }
details() { python3 - "$ROW_DIR/$1.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
print(" ".join("%s=[%s]" % (m.group(2), m.group(1)) for m in re.finditer(r'text="([^"]*)" resource-id="files_detail:([^"]*)"', x)))
PY
}

# ------------------------------------------------------------------------------------------------ empty (the wipe)
log "--- empty: pm clear → provision.sh → ensure_start → Files"
rings_save
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
adb shell pm clear app.tileshell > "$ROW_DIR/pm-clear.txt" 2>&1
assert_eq "pm clear app.tileshell" "Success" "$(tr -d '\r' < "$ROW_DIR/pm-clear.txt" | tail -1)"
record "the appop after pm clear (BUILD-NOTES: pm clear does not reset it)" "$(appop_now)"
TMPDIR="$SCRATCH" bash "$PROVISION" > "$ROW_DIR/provision.out" 2>&1; echo $? > "$ROW_DIR/provision.rc"
assert_eq "provision.sh exit code" "0" "$(cat "$ROW_DIR/provision.rc")"
assert_contains "provision.sh: its all-files line" "all-files access: MANAGE_EXTERNAL_STORAGE: allow" "$(cat "$ROW_DIR/provision.out")"
assert_gate_apk "after provision.sh the installed APK is the gate candidate"
assert_eq "awake" "Awake" "$(wake_device)"
ensure_start; D 00-start
assert_eq "after provision: Start, no wizard page" "no" "$(H 00-start wizard_page)"
assert_eq "the private Recent store after the wipe (files-recent.json)" "" "$(recent_json)"
M="$(ring_mark)"; files_open; D 01-cold; S 01-cold
assert_eq "empty: Files, cold — on top, the pane open with Recent behind it" "$FILES_ACTIVITY yes" "$(top_activity) $(H 01-cold files_pane)"
assert_contains "empty: [files] recent: 0" "[files] recent: 0" "$(ring_since "$M")"
recent_page 02-empty
assert_eq "empty: files_recent_empty's text, exactly" "$EMPTY_TEXT" "$(X 02-empty files_recent_empty)"
assert_eq "empty: no files_recent_row node" "0" "$(grep -c 'files_recent_row:' "$ROW_DIR/02-empty.xml")"
for b in select view search; do assert_eq "empty: the bar's button files_bar:$b is dim (disabled)" "false" "$(EN 02-empty "files_bar:$b")"; done
record "empty: files_bar:more (•••) enabled — its overflow keeps Refresh and Settings" "$(EN 02-empty files_bar:more)"
assert_eq "empty: no New folder button, no sort line" "no no" "$(H 02-empty files_bar:new_folder) $(H 02-empty files_sort)"
# shellcheck disable=SC2046
INK="$(python3 "$HERE/ink.py" "$ROW_DIR/02-empty.png" $(B 02-empty files_recent_empty))"
record "empty: the line's ink (left top right bottom px, brightest pixel) / its node" "$INK / $(B 02-empty files_recent_empty)"
# shellcheck disable=SC2086
set -- $INK
assert_within "empty: the line's left edge, epx (14.5 ± 2)" "14.5" "$(python3 -c "print('%.1f' % (${1:-0}/3))")" 2
assert_within "empty: its cap top, epx (88.8 + (STATUS_EPX 28 − 24) = 92.8 ± 2)" "92.8" "$(python3 -c "print('%.1f' % (${2:-0}/3))")" 2
assert_eq "empty: its colour ${5:-?} = 160,160,160 ± 4" "yes" "$(near "${5:-0,0,0}" 160,160,160 4)"
adb shell input keyevent KEYCODE_BACK; sleep 1

# The wipe took the layout: the baseline is put back before anything else (its own assertions run again).
baseline_start
files_up || { row_end; exit 1; }
TODAY="$(adb shell date +%-m/%-d/%Y | tr -d '\r')"
python3 -c "
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'w') as z: z.writestr('inside.txt', 'browsed, never opened')" "$ROW_DIR/browse.zip"
adb push "$ROW_DIR/browse.zip" /sdcard/QA-Files/recent/browse.zip >/dev/null 2>&1
adb shell "touch -d '2026-02-01 12:00' /sdcard/QA-Files/recent/browse.zip"     # adb push keeps the host's mtime: never.png stays the newest
ensure_start

open_png() { # name — through the folder page, then Back
  local m
  m="$(ring_mark)"; tap_row "$RF" "$1" 2.5
  assert_eq "open $1: the shell's viewer" "$VIEWER" "$(top_activity)"
  assert_contains "open $1: [files] recent add <path>" "[files] recent add $RF/$1" "$(ring_since "$m")"
  adb shell input keyevent KEYCODE_BACK; sleep 1.2
}

# ------------------------------------------------------------------------------------------------ order
log "--- order"
open_png r1.png; open_png r2.png; open_png r3.png
R0="$(recent_json)"
M="$(ring_mark)"; tap_row "$QF" a.txt 3; D 09-chooser; S 09-chooser
record "a.txt: what comes on top / the handlers the AVD's chooser offers" "$(top_activity) / $(texts 09-chooser | cut -c1-200)"
assert_eq "a.txt: Android's chooser is on top" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
adb shell input keyevent KEYCODE_BACK; sleep 2
absent_in "a.txt: backing out of the chooser adds nothing (no recent add)" "recent add" "$(ring_since "$M")"
assert_eq "a.txt: …and files-recent.json is unchanged" "$R0" "$(recent_json)"
M="$(ring_mark)"; recent_page 10-order
assert_eq "order: files_recent_row nodes are r3.png, r2.png, r1.png — exactly those three, in that order" "r3.png r2.png r1.png" "$(recent_rows 10-order)"
assert_eq "order: each files_detail is a date only (today's, the opened-at date)" "r3.png=[$TODAY] r2.png=[$TODAY] r1.png=[$TODAY]" "$(details 10-order)"
assert_contains "order: [files] recent: 3" "[files] recent: 3" "$(ring_since "$M")"
assert_eq "with rows: no files_recent_empty and no files_sort node" "no no" "$(H 10-order files_recent_empty) $(H 10-order files_sort)"
M="$(ring_mark)"; T 10-order files_recent_row:r1.png 2.5
assert_eq "re-open r1.png (its Recent row): the viewer" "$VIEWER" "$(top_activity)"
assert_contains "re-open r1.png: [files] recent add" "[files] recent add $RF/r1.png" "$(ring_since "$M")"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; D 11-reopen; S 11-reopen
assert_eq "re-open: r1.png, r3.png, r2.png (one entry per path, moved to the top)" "r1.png r3.png r2.png" "$(recent_rows 11-reopen)"

# ------------------------------------------------------------------------------------------------ never opened
log "--- never opened, only selected, only browsed"
files_at "$RF"; D 12a; T 12a files_bar:select 1; D 12b; T 12b files_row:never.png 0.8; D 12c
assert_eq "never.png is selected (not opened)" "1 item selected" "$(X 12c files_sort)"
adb shell input keyevent KEYCODE_BACK; sleep 1
files_at "$QF/hidden"; sleep 0.5
M="$(ring_mark)"; tap_row "$RF" browse.zip 2; D 12d
assert_eq "a zip browsed: its virtual root is shown" "yes" "$(H 12d files_zip_root)"
adb shell input keyevent KEYCODE_BACK; sleep 1
absent_in "browsing a zip adds nothing" "recent add" "$(ring_since "$M")"
recent_page 12-never
assert_eq "never.png (the newest file on the phone, only selected), the folder and the zip browsed are absent" "r1.png r3.png r2.png" "$(recent_rows 12-never)"
assert_eq "never.png IS the newest file of QA-Files (a modified-date list would show it first)" "/sdcard/QA-Files/recent/never.png" \
  "$(q "find /sdcard/QA-Files -type f -exec stat -c '%Y %n' {} + | sort -n | tail -1 | cut -d' ' -f2-")"

# ------------------------------------------------------------------------------------------------ Remove from recent
log "--- Remove from recent"
MD5_R3="$(md5dev /sdcard/QA-Files/recent/r3.png)"
hold 12-never files_recent_row:r3.png; D 13-hold; S 13-hold
assert_eq "hold r3.png: the menu is exactly remove_recent / share / properties" "files_hold:remove_recent files_hold:share files_hold:properties" "$(ids 13-hold | tr ' ' '\n' | grep '^files_hold:' | xargs)"
python3 - "$ROW_DIR/13-hold.xml" > "$ROW_DIR/13-hold-texts.txt" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
nodes = re.findall(r"<node[^>]*>", x)
out = []
for i, s in enumerate(nodes):
    if 'resource-id="files_hold:' in s:
        t = re.search(r'text="([^"]*)"', s).group(1)
        if not t:
            for n in nodes[i + 1:i + 3]:
                t = re.search(r'text="([^"]*)"', n).group(1)
                if t: break
        out.append(t)
print(" / ".join(out))
PY
assert_eq "hold r3.png: its texts" "Remove from recent / Share / Properties" "$(cat "$ROW_DIR/13-hold-texts.txt")"
M="$(ring_mark)"; T 13-hold files_hold:remove_recent 1.5; D 14-removed; S 14-removed
SL="$(ring_since "$M")"
assert_eq "Remove from recent: the row is gone" "r1.png r2.png" "$(recent_rows 14-removed)"
assert_contains "Remove from recent: [files] recent remove <path>" "[files] recent remove $RF/r3.png" "$SL"
assert_eq "Remove from recent: the FILE is still present with its md5 unchanged (ls, md5sum)" "/sdcard/QA-Files/recent/r3.png $MD5_R3" "$(lsdev /sdcard/QA-Files/recent/r3.png) $(md5dev /sdcard/QA-Files/recent/r3.png)"
absent_in "Remove from recent: no bin line — it never touches the file" "bin delete" "$SL"

# ------------------------------------------------------------------------------------------------ kept in step
log "--- kept in step: rename, delete, adb rm"
files_at "$RF"; D 20a; hold 20a files_row:r1.png; D 20b; T 20b files_hold:rename 1.5; D 20c
dialog_type r1b.png; D 20d
assert_eq "rename: the typed name" "r1b.png" "$(X 20d files_dialog_input)"
M="$(ring_mark)"; T 20d files_dialog:ok 2
assert_eq "rename: the file is r1b.png on disk" "/sdcard/QA-Files/recent/r1b.png" "$(lsdev /sdcard/QA-Files/recent/r1b.png)"
recent_page 21-renamed
assert_eq "rename r1.png → r1b.png in Files: the row reads r1b.png in the same position" "r1b.png r2.png" "$(recent_rows 21-renamed)"
files_at "$RF"; D 22a; hold 22a files_row:r2.png; D 22b; T 22b files_hold:delete 1.2; D 22c; M="$(ring_mark)"; T 22c files_dialog:ok 2
SL="$(ring_since "$M")"
assert_contains "delete r2.png in Files: its Recent entry is dropped ([files] recent remove)" "[files] recent remove $RF/r2.png" "$SL"
assert_contains "delete r2.png in Files: the file is in the bin (E4b's form: the line, and <ms>-<seq>-r2.png)" "[files] bin delete $RF/r2.png: ok" "$SL"
assert_eq "…the bin lists it" "1" "$(q "ls $BINP" | grep -cE '^[0-9]+-[0-9]+-r2\.png$')"
recent_page 23-deleted
assert_eq "delete r2.png in Files: its row is gone, and no bin file is listed" "r1b.png" "$(recent_rows 23-deleted)"
open_png r3.png; recent_page 24-r3back
assert_eq "r3.png opened again: on top" "r3.png r1b.png" "$(recent_rows 24-r3back)"
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell "rm /sdcard/QA-Files/recent/r3.png"
M="$(ring_mark)"; recent_page 25-gone
assert_eq "r3.png removed with adb shell rm: on the next read of the page its row is gone" "r1b.png" "$(recent_rows 25-gone)"
RL="$(ring_since "$M" | grep -o '\[files\] recent: [0-9]*' | tail -1)"
assert_eq "[files] recent: <n> on that read" "[files] recent: 1" "$RL"

# ------------------------------------------------------------------------------------------------ survives c6
J="$(recent_json)"; c6; ensure_start
assert_eq "the list survives c6: files-recent.json unchanged (the private store)" "$J" "$(recent_json)"
recent_page 26-after-c6
assert_eq "the list survives c6: the row is still listed" "r1b.png" "$(recent_rows 26-after-c6)"

# ------------------------------------------------------------------------------------------------ the volume
log "--- the volume: opened there, unmount, mount"
ensure_start
pubvol_up >/dev/null || _verdict FAIL "pubvol_up" "failed (above)"
sleep 2
adb shell "cp /sdcard/QA-Files/img-2.png $PUBVOL_PATH/pv.png"; sleep 1
M="$(ring_mark)"; tap_row "$PUBVOL_PATH" pv.png 3; S 30-pv-viewer
assert_eq "volume: the file opens in the viewer" "$VIEWER" "$(top_activity)"
assert_contains "volume: [files] recent add <path on the volume>" "[files] recent add $PUBVOL_PATH/pv.png" "$(ring_since "$M")"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
recent_page 31-pv-row
assert_eq "volume: a file opened on the public volume shows a row (on top)" "pv.png r1b.png" "$(recent_rows 31-pv-row)"
M="$(ring_mark)"; adb shell sm unmount "$PUBVOL_ID"; sleep 3; D 32-unmounted; S 32-unmounted
assert_eq "volume: sm unmount → the row hidden" "r1b.png" "$(recent_rows 32-unmounted)"
assert_contains "volume: …its entry kept in the store" "pv.png" "$(recent_json)"
M="$(ring_mark)"; adb shell sm mount "$PUBVOL_ID"; sleep 4; D 33-mounted; S 33-mounted
assert_eq "volume: sm mount → the row back" "pv.png r1b.png" "$(recent_rows 33-mounted)"
M="$(ring_mark)"; recent_page 34-count
N="$(ring_since "$M" | grep -o '\[files\] recent: [0-9]*' | tail -1 | grep -o '[0-9]*$')"
assert_eq "[files] recent: <n> with n = ${N:-?} ≤ 100" "yes" "$([ -n "$N" ] && [ "$N" -le 100 ] && echo yes || echo no)"
assert_eq "no bin file appears on the Recent page" "0" "$(grep -o 'files_recent_row:[^"]*' "$ROW_DIR/34-count.xml" | grep -cE '^files_recent_row:[0-9]+-[0-9]+-')"
hold 34-count files_recent_row:pv.png; D 35; T 35 files_hold:remove_recent 1.5
adb shell input keyevent KEYCODE_HOME; sleep 1
pubvol_down
_snap_sdcard > "$ROW_DIR/.snap-now.txt"
THUMBS="$(grep -vxFf "$ROW_DIR/snap-sdcard-before.txt" "$ROW_DIR/.snap-now.txt" | grep '^/sdcard/Pictures/\.thumbnails/' | xargs)"
record "MediaProvider's thumbnails made for the public volume's picture (the platform's; removed)" "[${THUMBS}]"
for t in $THUMBS; do adb shell "rm -f '$t'"; done

# ------------------------------------------------------------------------------------------------ the end
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
c6; ensure_start
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
assert_contains "end: All-files access is held" "MANAGE_EXTERNAL_STORAGE: allow" "$(appop_now)"
row_end
