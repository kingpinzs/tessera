#!/usr/bin/env bash
# E14: order, re-open, never-opened / only-selected, Remove from recent, kept in step (rename, delete, adb rm), survives c6.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg b-e14
adb logcat -c
RF=$QF/recent
TODAY="$(adb shell date +%-m/%-d/%Y | tr -d '\r')"
recent_page() { files_open --es page recent; D "$1"; S "$1"; }
details() { python3 - "$ROW_DIR/$1.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
print(" ".join("%s=[%s]" % (m.group(2), m.group(1)) for m in re.finditer(r'text="([^"]*)" resource-id="files_detail:([^"]*)"', x)))
PY
}
open_png() { # name — through the folder page, then Back
  local m sl; m=$(ring_mark); tap_row $RF "$1" 2.5
  assert_eq "open $1: the viewer" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
  sl="$(ring_since $m)"; assert_contains "open $1: [files] recent add" "[files] recent add $RF/$1" "$sl"
  adb shell input keyevent KEYCODE_BACK; sleep 1.2
}
c6; ensure_start
# ---- order
open_png r1.png; open_png r2.png; open_png r3.png
M=$(ring_mark); recent_page 10-order
assert_eq "order: exactly r3 r2 r1" "r3.png r2.png r1.png" "$(recent_rows 10-order)"
assert_eq "order: each detail is today's date only" "r3.png=[$TODAY] r2.png=[$TODAY] r1.png=[$TODAY]" "$(details 10-order)"
assert_eq "with rows: no files_recent_empty, no files_sort" "no no" "$(H 10-order files_recent_empty) $(H 10-order files_sort)"
assert_contains "[files] recent: 3" "[files] recent: 3" "$(ring_since $M)"
en() { grep -o "<node[^>]*resource-id=\"$2\"[^>]*>" "$ROW_DIR/$1.xml" | grep -o 'enabled="[a-z]*"' | head -1; }
assert_eq "with rows: Select · Icons · Search live, no New folder" 'enabled="true" enabled="true" enabled="true" no' "$(en 10-order files_bar:select) $(en 10-order files_bar:view) $(en 10-order files_bar:search) $(H 10-order files_bar:new_folder)"
# ---- re-open from the Recent row: to the top
M=$(ring_mark); T 10-order files_recent_row:r1.png 2.5
assert_eq "tap a Recent row: the viewer" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
assert_contains "tap a Recent row: recent add" "[files] recent add $RF/r1.png" "$(ring_since $M)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; D 11-reopen; S 11-reopen
assert_eq "re-open: r1 r3 r2 (one entry per path, moved to the top; the page re-read on return)" "r1.png r3.png r2.png" "$(recent_rows 11-reopen)"
# ---- never opened, only selected, only browsed
files_at $RF; D 12a; T 12a files_bar:select 1; D 12b; T 12b files_row:never.png 0.8; D 12c; S 12c
echo "selection: [$(X 12c files_sort)]"; adb shell input keyevent KEYCODE_BACK; sleep 1
recent_page 12-never
assert_eq "never.png (newest file, only selected) is absent; the folder browsed is absent" "r1.png r3.png r2.png" "$(recent_rows 12-never)"
# ---- Remove from recent
MD5="$(q "md5sum $RF/r3.png" | cut -d' ' -f1)"
hold 12-never files_recent_row:r3.png; D 13-hold; S 13-hold
assert_eq "hold menu: exactly remove_recent / share / properties" "files_hold:remove_recent files_hold:share files_hold:properties" "$(ids 13-hold | tr ' ' '\n' | grep '^files_hold:' | xargs)"
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
assert_eq "hold menu: the texts" "Remove from recent / Share / Properties" "$(cat "$ROW_DIR/13-hold-texts.txt")"
M=$(ring_mark); T 13-hold files_hold:remove_recent 1.5; D 14-removed; S 14-removed
assert_eq "Remove from recent: the row is gone" "r1.png r2.png" "$(recent_rows 14-removed)"
assert_contains "Remove from recent: [files] recent remove" "[files] recent remove $RF/r3.png" "$(ring_since $M)"
assert_eq "Remove from recent: the FILE is still there, md5 unchanged" "$MD5" "$(q "md5sum $RF/r3.png" | cut -d' ' -f1)"
absent_in "Remove from recent: no bin line" "bin delete" "$(ring_since $M)"
# ---- Share and Properties from the hold menu
hold 14-removed files_recent_row:r2.png; D 15a; M=$(ring_mark); T 15a files_hold:share 2.5
assert_eq "hold → Share: the chooser" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
assert_contains "hold → Share: [files] share 1 files" "[files] share 1 files type=image/png uris=content://app.tileshell.files/root$RF/r2.png" "$(ring_since $M)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
D 15b; hold 15b files_recent_row:r2.png; D 15c; T 15c files_hold:properties 1.5; D 15d; S 15d
echo "properties nodes: $(ids 15d | cut -c1-400)"
assert_contains "hold → Properties: the Properties page of r2.png" "r2.png" "$(grep -o 'text="[^"]*"' "$ROW_DIR/15d.xml" | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 1
# ---- kept in step: rename r1.png → r1b.png in Files
files_at $RF; D 20a; hold 20a files_row:r1.png; D 20b; T 20b files_hold:rename 1.5; D 20c
adb shell input text r1b.png; sleep 1; D 20d; S 20d; echo "typed: [$(X 20d files_dialog_input)]"
M=$(ring_mark); T 20d files_dialog:ok 2
echo "$(ring_since $M | grep -F '[files] rename')"
assert_eq "rename: the file is r1b.png on disk" "$RF/r1b.png" "$(q "ls $RF/r1b.png")"
recent_page 21-renamed
assert_eq "rename in Files: the row reads r1b.png in the same position" "r1b.png r2.png" "$(recent_rows 21-renamed)"
# ---- kept in step: delete r2.png in Files
files_at $RF; D 22a; hold 22a files_row:r2.png; D 22b; T 22b files_hold:delete 1.2; D 22c; M=$(ring_mark); T 22c files_dialog:ok 2
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | grep -E "bin|recent" | tee "$ROW_DIR/22-ring.txt"
assert_contains "delete in Files: recent remove" "[files] recent remove $RF/r2.png" "$SL"
echo "bin: $(q 'ls /sdcard/.Tessera/bin' | xargs)"
assert_contains "delete in Files: the file is in the bin" "r2.png" "$(q 'ls /sdcard/.Tessera/bin')"
recent_page 23-deleted
assert_eq "delete in Files: its row is gone, and no bin file is listed" "r1b.png" "$(recent_rows 23-deleted)"
# ---- kept in step: open r3.png again, adb rm it, the next read drops it
open_png r3.png; recent_page 24-r3back
assert_eq "r3.png opened again: on top" "r3.png r1b.png" "$(recent_rows 24-r3back)"
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell "rm $RF/r3.png"
M=$(ring_mark); recent_page 25-gone
assert_eq "adb-removed file: its row is gone on the next read" "r1b.png" "$(recent_rows 25-gone)"
assert_contains "[files] recent: 1" "[files] recent: 1" "$(ring_since $M)"
assert_eq "…and its entry is dropped from the store" "0" "$(recent_json | grep -c 'r3.png')"
# ---- survives a force-stop
J="$(recent_json)"; c6; ensure_start
assert_eq "after c6: files-recent.json unchanged" "$J" "$(recent_json)"
recent_page 26-after-c6
assert_eq "after c6: the row is still listed" "r1b.png" "$(recent_rows 26-after-c6)"
echo "store: $(recent_json)"
adb shell input keyevent KEYCODE_HOME
leg_end
