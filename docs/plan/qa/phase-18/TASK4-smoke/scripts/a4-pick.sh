#!/usr/bin/env bash
# E6: a.txt → the chooser → a pick (HTML Viewer) adds a.txt to Recent; q.xyz → no handler.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg a-e6
adb logcat -c
c6; ensure_start
M=$(ring_mark); tap_row $QF a.txt 3; D 31-chooser
assert_eq "a.txt: the chooser is on top" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
adb shell dumpsys activity activities | grep -m1 -E "Intent \{ act=android.intent.action.CHOOSER" | tee "$ROW_DIR/31-intent.txt"
assert_contains "a.txt: the chooser carries text/plain" "text/plain" "$(cat "$ROW_DIR/31-intent.txt")"
absent_in "a.txt: nothing added while the chooser is only open" "recent add" "$(ring_since $M)"
xy="$(python3 - "$ROW_DIR/31-chooser.xml" <<'PY'
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r"<node[^>]*>", x):
    s = m.group(0)
    if 'text="HTML Viewer"' in s:
        a = [int(v) for v in re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s).groups()]
        print((a[0] + a[2]) // 2, a[1] - 150); break
PY
)"
echo "HTML Viewer at: $xy"; adb shell input tap $xy; sleep 3; S 32-picked
echo "top after the pick: $(top_activity)"
assert_contains "the picked app is in front" "com.android.htmlviewer" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/32-ring.txt"
assert_contains "a pick: [files] recent add a.txt" "[files] recent add $QF/a.txt" "$SL"
assert_contains "a pick: files-recent.json's first entry is a.txt" '{"recent":[{"path":"/storage/emulated/0/QA-Files/a.txt"' "$(recent_json)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5; echo "after Back: $(top_activity)"
files_open --es page recent; D 33-recent; S 33-recent; echo "recent rows: $(recent_rows 33-recent)"
assert_eq "Recent's first row is a.txt" "a.txt" "$(recent_rows 33-recent | cut -d' ' -f1)"

# ---- no handler. Android's own type map knows ".xyz" (chemical/x-xyz), so the doc's "application/octet-stream" is
# read on a file whose extension the map does not know (q.qa18x); q.xyz is run too and its type recorded.
adb shell "printf x > /sdcard/QA-Files/q.qa18x"
nohandler() { # name mime dump
  local m sl; m=$(ring_mark); tap_row $QF "$1" 2; D "$3"; S "$3"
  assert_eq "$1: still in Files" "$FILES_ACTIVITY" "$(top_activity)"
  assert_eq "$1: files_error text" "No app on this phone opens this" "$(X "$3" files_error)"
  sl="$(ring_since $m)"; echo "$sl" | grep -F "[files]" | tee "$ROW_DIR/$3-ring.txt"
  assert_contains "$1: [files] no handler for $2" "[files] no handler for $2" "$sl"
  absent_in "$1: no recent add" "recent add" "$sl"
}
# An extension the map does not know IS application/octet-stream — and this AVD has handlers for it, so the chooser opens.
M=$(ring_mark); tap_row $QF q.qa18x 3; D 34-unknown; S 34-unknown
record "q.qa18x (application/octet-stream): top activity" "$(top_activity)"
record "q.qa18x: the chooser's intent" "$(adb shell dumpsys activity activities | grep -m1 -E 'Intent \{ act=android.intent.action.CHOOSER' | xargs)"
record "q.qa18x: handlers the AVD offers for application/octet-stream" "$(grep -o 'text="[^"]\+"' "$ROW_DIR/34-unknown.xml" | xargs)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
absent_in "q.qa18x: backed out, no recent add" "recent add" "$(ring_since $M)"
nohandler q.xyz chemical/x-xyz 35-xyz
record "q.xyz's type on this image (the doc says application/octet-stream)" "chemical/x-xyz — the platform's MimeTypeMap knows the extension"
c6; ensure_start
leg_end
