#!/usr/bin/env bash
# E6: a.txt → Android's chooser (which handlers the AVD offers; a pick adds to Recent, backing out adds nothing);
# a .xyz file → "No app on this phone opens this"; an entry inside a zip is the zip page's own (not this leg's).
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg a-e6
adb logcat -c
adb shell "printf xyz > /sdcard/QA-Files/q.xyz; printf 'second text file' > /sdcard/QA-Files/c.txt"; sleep 1
c6; ensure_start
# ---- the chooser, backed out of
R0="$(recent_json)"
M=$(ring_mark); tap_row $QF c.txt 3; S 30-chooser; D 30-chooser
assert_eq "c.txt: the chooser is on top" "com.android.intentresolver" "$(top_activity | cut -d/ -f1)"
adb shell dumpsys activity activities | grep -m3 -E "Intent \{ act=android.intent.action.(CHOOSER|VIEW)" | tee "$ROW_DIR/30-intent.txt"
python3 - "$ROW_DIR/30-chooser.xml" <<'PY' | tee "$ROW_DIR/30-handlers.txt"
import re, sys
x = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for m in re.finditer(r"<node[^>]*>", x):
    s = m.group(0)
    t = re.search(r'text="([^"]+)"', s); r = re.search(r'resource-id="([^"]*)"', s); b = re.search(r'bounds="([^"]*)"', s)
    if t: print("%s | %s | %s" % (t.group(1), r.group(1) if r else "", b.group(1)))
PY
adb shell input keyevent KEYCODE_BACK; sleep 2
assert_eq "Back from the chooser returns to Files" "$FILES_ACTIVITY" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/30-ring.txt"
absent_in "chooser backed out of: no recent add" "recent add" "$SL"
assert_eq "chooser backed out of: files-recent.json unchanged" "$R0" "$(recent_json)"
leg_end
