#!/usr/bin/env bash
# E10 Persistence and memory (phase 13 Acceptance E10, T13-15, T13-16). The toggle survives `am force-stop`. Memory:
# three states in ONE process, never force-stopped between them (the picture is set and removed and the toggle flipped
# through the product's own Settings page, so nothing restarts the process), both pages warmed in each, each figure the
# median of three total-PSS samples 1 s apart — A: no picture; B: the checkerboard, acrylic OFF; C: acrylic ON after its
# `static backdrop rebuilt` line. C - B <= 14 MB; after "Remove picture" and the same warm-up, PSS within 2 MB of A.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"

row_begin E10 "the toggle persists; the static layer costs <= 14 MB and is freed with the picture"

pss() { # median of three total-PSS samples (KB), 1 s apart, of the launcher's main process
  local p s=""
  p="$(adb shell pidof $PKG | tr -d '\r' | awk '{print $1}')"
  for _ in 1 2 3; do
    s="$s $(adb shell dumpsys meminfo "$p" | grep -m1 'TOTAL PSS:' | awk '{print $3}' | tr -d '\r')"
    sleep 1
  done
  python3 -c "import statistics,sys; print(int(statistics.median(map(int, sys.argv[1:]))))" $s
}
warm() { show_start 3; to_app_list 2; to_start 2; sleep 2; }
pid() { adb shell pidof $PKG | tr -d '\r' | awk '{print $1}'; }
toggle_to() { # true|false through Settings > Start + theme
  open_transparency
  local now
  now="$(grep -oE 'resource-id="theme_transparency_effects"[^>]*checked="(true|false)"' "$SETTINGS_DUMP" | grep -oE 'checked="[a-z]*"' | cut -d'"' -f2)"
  [ "$now" = "$1" ] || tap_transparency
  leave_settings
}
theme_tap() { # tag on Start + theme
  adb shell am start -n $PKG/.settings.SettingsActivity --es page START_THEME >/dev/null 2>&1; sleep 2
  scroll_to_node "$ROW_DIR/.theme.xml" "$1" 8
  tap_node "$ROW_DIR/.theme.xml" "$1"
}

assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
set_pref transparency_effects boolean true
clear_background

# ---- (1) the toggle survives force-stop
show_start 5
toggle_to false
assert_eq "(1) the prefs hold transparency_effects=false" "false" "$(transparency_state)"
adb shell am force-stop $PKG; sleep 2
show_start 5
open_transparency
assert_contains "(1) after force-stop the switch reads Off" 'checked="false"' "$(grep -oE 'resource-id="theme_transparency_effects"[^>]*' "$SETTINGS_DUMP")"
cp "$SETTINGS_DUMP" "$ROW_DIR/toggle-after-force-stop.xml"
leave_settings
toggle_to true
assert_eq "(1) back On" "true" "$(transparency_state)"

# ---- (2) memory, one process
adb shell am force-stop $PKG; sleep 2
show_start 7
P0="$(pid)"
warm
A="$(pss)"; record "(2) A: no picture (KB)" "$A"
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
assert_ne "(2) the picker shows the checker (newest)" "" "$PICK"
# shellcheck disable=SC2086
adb shell input tap $PICK; sleep 3
leave_settings
assert_contains "(2) the Start background is set" 'name="background"' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml)"
warm
B="$(pss)"; record "(2) B: the checker, acrylic off (KB)" "$B"
MARK="$(ring_mark)"
toggle_to true
for _ in $(seq 1 20); do ring_since "$MARK" | grep -q 'static backdrop rebuilt' && break; sleep 0.5; done
ring_since "$MARK" > "$ROW_DIR/slice-C.txt"
assert_contains "(2) C: its static backdrop rebuilt line" "static backdrop rebuilt" "$(cat "$ROW_DIR/slice-C.txt")"
warm
C="$(pss)"; record "(2) C: the checker, acrylic on (KB)" "$C"
assert_eq "(2) C - B <= 14 MB (one screen layer + noise): $(( C - B )) KB" yes "$( [ $(( C - B )) -le 14336 ] && echo yes || echo no)"
theme_tap theme_background_remove
sleep 1.5
leave_settings
assert_absent "(2) the background key is gone" 'name="background"' "$(adb shell run-as $PKG cat shared_prefs/start_theme.xml)"
warm
A2="$(pss)"; record "(2) after Remove picture (KB)" "$A2"
assert_within "(2) after Remove picture PSS is within 2 MB of A, KB" 0 "$(( A2 - A ))" 2048
assert_eq "(2) one process throughout (pid unchanged)" "$P0" "$(pid)"

adb shell rm -f "$CHECKER_PATH"
adb shell content call --uri content://media --method scan_file --arg "$CHECKER_PATH" >/dev/null 2>&1
show_start 3
row_end
