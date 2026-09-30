#!/usr/bin/env bash
# Phase 14 E12 — gesture navigation (the AVD's gestural overlay): a pan from x = 200 opens the pod bay; a swipe from
# inside the left gesture inset (x = 2) is Android's Back and never the pod bay — with DeskClock in the Back history,
# Back fires and DeskClock resumes. systemGestureExclusionRects is not used.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E12 "gesture navigation: the pan vs the left-edge Back"
OVERLAY=com.android.internal.systemui.navbar.gestural
restore_overlay() {
  adb shell cmd overlay disable $OVERLAY >/dev/null 2>&1
  sleep 3
}
trap restore_overlay EXIT

adb shell cmd overlay enable $OVERLAY >/dev/null 2>&1
sleep 4
state="$(adb shell cmd overlay list | grep -F "$OVERLAY" | tr -d '\r' | sed 's/^ *//')"
note "overlay: $state"
assert_contains "the gestural overlay is enabled" "[x] $OVERLAY" "$state"

# (1) A pan from x = 200 opens the pod bay.
ensure_start
MARK="$(ring_mark)"
swipe_right
dump_ui "$ROW_DIR/01-pan.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/01-slice.txt"
assert_eq "gestural: a pan from x = 200 opens the pod bay" "yes" "$(has_node "$ROW_DIR/01-pan.xml" pod_bay)"
assert_contains "gestural: opened by swipe" "[podbay] opened by swipe" "$s"
screencap "$ROW_DIR/01-pan.png"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/02-start.xml"
assert_eq "back to Start" "yes" "$(start_alone "$ROW_DIR/02-start.xml")"

# (2) DeskClock in the Back history, then a swipe from inside the left gesture inset: Android's Back.
adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 3
dump_ui "$ROW_DIR/03-start.xml"
assert_eq "Home from DeskClock: Start alone" "yes" "$(start_alone "$ROW_DIR/03-start.xml")"
bars_state() { adb shell dumpsys window | grep -m2 -E 'InsetsSource id=[0-9a-f]* type=(statusBars|navigationBars)' | grep -oE 'visible=[a-z]+' | tr '\n' ' '; }
MARK="$(ring_mark)"
adb shell input swipe 2 1200 400 1200 250
sleep 0.5
record "bars 0.5 s after the edge swipe (hidden by phase 01's bar rule before it)" "$(bars_state)"
sleep 2.5
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/04-slice.txt"
dump_ui "$ROW_DIR/04-after-edge.xml"
# The doc's clause, kept as written: on an immersive window Android's first edge swipe reveals the bars instead
# (BUILD-NOTES/e12-probe) — ruling asked of Jeremy (qa/phase-14/README.md, "E12 and Android's immersive edge").
assert_eq "the edge swipe is Android's Back: DeskClock resumes" "com.android.deskclock/.DeskClock" "$(top_activity)"
assert_absent "the edge swipe opened no pod bay" "[podbay] opened" "$s"
assert_absent "no [start] page=POD_BAY" "[start] page=POD_BAY" "$s"
assert_eq "no pod_bay in the dump" "no" "$(has_node "$ROW_DIR/04-after-edge.xml" pod_bay)"
assert_contains "the guard took the pan" "edge pan from x=" "$s"
if [ "$(top_activity)" = "app.tileshell/.StartActivity" ]; then
  MARK2="$(ring_mark)"
  adb shell input swipe 2 1200 400 1200 250
  sleep 3
  record "a second edge swipe (bars now showing)" "top=$(top_activity); slice: $(ring_since "$MARK2" | grep -oE '\[(podbay|start)\] [^w]*' | tr '\n' ';' | cut -c1-200)"
fi
c6

restore_overlay
trap - EXIT
state="$(adb shell cmd overlay list | grep -F "$OVERLAY" | tr -d '\r' | sed 's/^ *//')"
note "overlay restored: $state"
assert_contains "restore: the gestural overlay is off" "[ ] $OVERLAY" "$state"
row_end
