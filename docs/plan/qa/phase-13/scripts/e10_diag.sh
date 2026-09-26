#!/usr/bin/env bash
# E10 diagnosis (not a row of the doc; its evidence is kept): after "Remove picture" the launcher's PSS stayed 12.7 MB
# above A on two builds (120e52ec, db078d6a). Where does it sit? One process, E10's own route, a full `dumpsys meminfo`
# at each step: M0 fresh start; M1 after one Settings round trip (nothing changed); M2 the checker chosen with acrylic
# off; M3 acrylic on (layer built); M4 picture removed; M5 after a forced GC (SIGUSR1 to the debug build's process:
# ART's signal catcher runs a collection). Each figure the median of three TOTAL PSS samples, E10's method.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E10_DIAG "where the 12.7 MB after Remove picture sits (diagnosis, recorded only)"
pid() { adb shell pidof $PKG | tr -d '\r' | awk '{print $1}'; }
snap() { # tag
  local p s=""
  p="$(pid)"
  for _ in 1 2 3; do
    adb shell dumpsys meminfo "$p" > "$ROW_DIR/meminfo-$1.txt"
    s="$s $(grep -m1 'TOTAL PSS:' "$ROW_DIR/meminfo-$1.txt" | awk '{print $3}' | tr -d '\r')"
    sleep 1
  done
  record "$1 TOTAL PSS (KB, median of 3)" "$(python3 -c "import statistics,sys; print(int(statistics.median(map(int, sys.argv[1:]))))" $s)"
  record "$1 categories (Pss KB)" "$(python3 -c "
import re, sys
out = []
for line in open(sys.argv[1]):
    m = re.match(r'\s*(Native Heap|Dalvik Heap|Dalvik Other|Gfx dev|GL mtrack|EGL mtrack|Other dev|Other mmap|\.so mmap|\.apk mmap|\.dex mmap|\.art mmap|Unknown)\s+(\d+)', line)
    if m: out.append('%s=%s' % (m.group(1).replace(' ', '_'), m.group(2)))
print(' '.join(out))" "$ROW_DIR/meminfo-$1.txt")"
  record "$1 App Summary" "$(sed -n '/App Summary/,/TOTAL SWAP/p' "$ROW_DIR/meminfo-$1.txt" | grep -E 'Java Heap|Native Heap|Code|Stack|Graphics|Private Other|System' | tr -s ' ' | tr '\n' ';')"
  record "$1 Views / Activities" "$(grep -E 'Views:|Activities:' "$ROW_DIR/meminfo-$1.txt" | tr -s ' ' | tr '\n' ' ')"
}
warm() { show_start 3; to_app_list 2; to_start 2; sleep 2; }
toggle_to() {
  open_transparency
  local now
  now="$(grep -oE 'resource-id="theme_transparency_effects"[^>]*checked="(true|false)"' "$SETTINGS_DUMP" | grep -oE 'checked="[a-z]*"' | cut -d'"' -f2)"
  [ "$now" = "$1" ] || tap_transparency
  leave_settings
}
theme_tap() {
  adb shell am start -n $PKG/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
  scroll_to_node "$ROW_DIR/.theme.xml" "$1" 8
  tap_node "$ROW_DIR/.theme.xml" "$1"
}

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
clear_background
adb shell am force-stop $PKG; sleep 2
show_start 7
P0="$(pid)"
warm; snap M0-fresh
adb shell am start -n $PKG/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 3
leave_settings
warm; snap M1-after-settings
toggle_to false
python3 "$P13/make_fixtures.py" checker "$ROW_DIR/checker.png" 1080 2340 >/dev/null
push_picture "$ROW_DIR/checker.png" "$CHECKER_PATH" >/dev/null
theme_tap theme_background_choose
sleep 2.5
adb shell input swipe 540 850 540 150 400; sleep 2
dump_ui "$ROW_DIR/.picker.xml"
PICK="$(python3 - "$ROW_DIR/.picker.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); best = None
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0); d = re.search(r'content-desc="(Photo taken on [^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if d and b:
        x1, y1, x2, y2 = map(int, b.groups())
        if best is None or (y1, x1) < best[0]: best = ((y1, x1), (x1 + x2) // 2, (y1 + y2) // 2)
print(f"{best[1]} {best[2]}" if best else "")
PY
)"
# shellcheck disable=SC2086
adb shell input tap $PICK; sleep 3
leave_settings
warm; snap M2-picture-acrylic-off
toggle_to true
sleep 2
warm; snap M3-acrylic-on
theme_tap theme_background_remove; sleep 1.5; leave_settings
warm; snap M4-removed
adb shell run-as $PKG kill -10 "$(pid)"
sleep 4
snap M5-after-gc
warm; snap M6-after-gc-warm
assert_eq "one process throughout" "$P0" "$(pid)"
adb shell rm -f "$CHECKER_PATH"
adb shell content call --uri content://media --method scan_file --arg "$CHECKER_PATH" >/dev/null 2>&1
show_start 3
row_end
