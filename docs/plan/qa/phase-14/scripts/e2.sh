#!/usr/bin/env bash
# Phase 14 E2 — bars: the pod bay is a page of StartActivity, so the system status and nav bars are hidden (phase 01
# E19's form: their inset sources visible=false) and the drawn W10M bars are there; the drawn Back and Windows keys
# both go to Start.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E2 "bars on the pod bay; drawn Back and Windows keys"

open_pod_bay "$ROW_DIR/podbay.xml"
assert_eq "the pod bay is showing" "yes" "$(has_node "$ROW_DIR/podbay.xml" pod_bay)"
adb shell dumpsys window > "$ROW_DIR/dumpsys-window.txt"
st="$(grep -m1 'InsetsSource id=[0-9a-f]* type=statusBars' "$ROW_DIR/dumpsys-window.txt" | grep -oE 'visible=[a-z]+')"
nv="$(grep -m1 'InsetsSource id=[0-9a-f]* type=navigationBars' "$ROW_DIR/dumpsys-window.txt" | grep -oE 'visible=[a-z]+')"
assert_eq "system status bar inset source not visible" "visible=false" "$st"
assert_eq "system nav bar inset source not visible" "visible=false" "$nv"
assert_eq "the drawn W10M status bar is in the dump" "yes" "$(has_node "$ROW_DIR/podbay.xml" w10m_status_bar)"
assert_eq "the drawn W10M nav bar is in the dump" "yes" "$(has_node "$ROW_DIR/podbay.xml" w10m_nav_bar)"
screencap "$ROW_DIR/podbay-bars.png"
note "screencap podbay-bars.png: the drawn bars (NEEDS-HUMAN reading of the picture is H1/H2's)"

MARK="$(ring_mark)"
tap_node "$ROW_DIR/podbay.xml" nav_back
sleep 1.5
dump_ui "$ROW_DIR/after-back.xml"
assert_eq "drawn Back from the pod bay: Start alone" "yes" "$(start_alone "$ROW_DIR/after-back.xml")"
assert_contains "drawn Back: closed by back" "[podbay] closed by back" "$(ring_since "$MARK")"

swipe_right
dump_ui "$ROW_DIR/podbay2.xml"
assert_eq "the pod bay again" "yes" "$(has_node "$ROW_DIR/podbay2.xml" pod_bay)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/podbay2.xml" nav_windows
sleep 1.5
dump_ui "$ROW_DIR/after-windows.xml"
assert_eq "drawn Windows key from the pod bay: Start alone" "yes" "$(start_alone "$ROW_DIR/after-windows.xml")"
assert_contains "drawn Windows key: closed by home" "[podbay] closed by home" "$(ring_since "$MARK")"

row_end
