#!/usr/bin/env bash
# E12 — gesture navigation: the fixture in the bottom row bursts in a line above it, every satellite above the
# gesture area, and a satellite runs. The baseline row's own tiles are AVD apps whose shortcuts are not seeded, so
# the fixture is held in the bottom-row baseline (T11-35).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E12 "gesture navigation: the bottom-row line clears the gesture area; a satellite runs"
seed_fixtures
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1; sleep 3
note "overlay: $(adb shell cmd overlay list | grep -i 'navbar.gestural' | tr -d '\r')"
restore baseline_layout-bottomrow.json
GTOP="$(adb shell dumpsys window | tr -d '\r' | python3 -c '
import re, sys
tops = []
for l in sys.stdin:
    if "mandatorySystemGestures" in l or "type=navigationBars" in l:
        m = re.search(r"frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", l)
        if m and int(m.group(4)) >= 2300 and int(m.group(2)) > 1500: tops.append(int(m.group(2)))
print(min(tops) if tops else "")')"
note "gesture area top: $GTOP"
assert_ne "the gesture area was read from dumpsys window" "" "$GTOP"
qdump "$ROW_DIR/rest.xml"
read -r X Y <<< "$(center "$ROW_DIR/rest.xml" "tile:dock:$A_KEY")"
MARK="$(ring_mark)"; hold "$X" "$Y" 1.0; sleep 1
qdump "$ROW_DIR/burst.xml"; screencap "$ROW_DIR/burst.png"
rest_lines "$MARK" > "$ROW_DIR/burst.rest"
python3 "$(dirname "$0")/e7_geom.py" "$ROW_DIR/burst.xml" "tile:dock:$A_KEY" "$ROW_DIR/burst.rest" > "$ROW_DIR/burst.geom"
assert_eq "the line above the row" line_above "$(awk '$1=="arrangement"{print $2}' "$ROW_DIR/burst.geom")"
maxb="$(rects "$ROW_DIR/burst.xml" quick_sat: | awk '{if($5>m)m=$5} END{print m}')"
assert_eq "every satellite's bottom above the gesture area ($maxb < $GTOP)" ok "$([ -n "$GTOP" ] && [ "$maxb" -lt "$GTOP" ] && echo ok || echo no)"
tap_node "$ROW_DIR/burst.xml" quick_sat:1; sleep 3.5
qdump "$ROW_DIR/launched.xml"
assert_eq "satellite 1 ran qa_two" qa_two "$(node_text "$ROW_DIR/launched.xml" "$A_PKG:id/shortcut_id")"
adb shell cmd overlay disable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1; sleep 3
note "overlay restored: $(adb shell cmd overlay list | grep -i 'navbar.gestural' | tr -d '\r')"
c6
restore baseline_layout.json
row_end
