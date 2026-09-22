#!/usr/bin/env bash
# E9 — the space-bar drag moves the keyboard, it stays there, and the band below passes taps through.
#
#   "Space-bar drag (instrumentation UiDevice.drag() from the space bar): the panel's top moves by the
#    drag distance within the range in Decisions (dump bounds), stays there after the keyboard hides and
#    shows again, and a tap in the transparent band below it reaches the app (fixture field below the
#    panel gets focus)"
#
# Range (Decisions, approximation H13): from rest up to one key-block height, 865 phys, higher. The
# fixture's BottomFieldActivity is adjustNothing with bottom_field anchored at the window's bottom, so
# once the panel is raised that field sits in the band under it.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E9 "space-bar drag: moves by the drag, clamps, persists, band passes taps"
kb_begin
W="$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x.*/\1/p' | tail -1)"
SX="$(python3 -c "print($W/1440)")"
MAXR="$(python3 -c "print(round(865*$SX))")"

open_field top_field BottomFieldActivity
D="$ROW_DIR/e9.xml"; kb_dump "$D"
read -r _ t0 _ _ <<< "$(bounds "$D" kb_panel)"
read -r sx0 sy0 <<< "$(node_center "$D" kb_key_space)"
note "panel top at rest $t0; space centre ($sx0,$sy0); max raise $MAXR px"

drag_by() { # dy (negative = up)
  local out; out="$(drag_pts "$sx0,$sy0" "$sx0,$((sy0 + $1))" 40)"
  echo "$out" | grep -E "drag\.(result|elapsed_ms)" | while read -r l; do note "  $l"; done
  sleep 1
  kb_dump "$D"
  read -r _ t _ _ <<< "$(bounds "$D" kb_panel)"
  echo "$t"
}
# drag_by runs in a command substitution, so the space bar's new centre is re-read out here.
respace() { kb_dump "$D"; read -r sx0 sy0 <<< "$(node_center "$D" kb_key_space)"; }

t1="$(drag_by -300)"
note "after a 300-px drag up: panel top $t1"
assert_within "the panel's top moved up by the drag distance (300 px)" 300 $((t0 - t1)) 3
respace
t2="$(drag_by -900)"
note "after a further 900-px drag up: panel top $t2"
assert_within "Decisions (H13): clamped at one key block (865 phys) above rest" "$MAXR" $((t0 - t2)) 3
screencap "$ROW_DIR/e9_raised.png"
assert_contains "the diagnostics record the move" "keyboard moved" "$(ime_log 'keyboard moved' | tail -1)"

# Hide and show again: it stays where it was dropped.
adb shell input keyevent KEYCODE_BACK; sleep 1.5
F="$ROW_DIR/.e9_fix.xml"; dump_ui "$F"
tap_node "$F" "$FIX:id/top_field"; sleep 2
kb_dump "$D"
read -r _ t3 _ pb <<< "$(bounds "$D" kb_panel)"
assert_eq "it stays raised after the keyboard hides and shows again" "$t2" "$t3"

# The band below the raised panel passes taps to the app.
dump_ui "$F"
read -r bl bt br bb <<< "$(bounds "$F" "$FIX:id/bottom_field")"
note "raised panel [$t3..$pb]; bottom_field [$bt..$bb]"
assert_eq "bottom_field lies in the band below the raised panel" "yes" "$([ "$bt" -gt "$pb" ] && echo yes || echo no)"
band="$(python3 "$HERE/measure.py" px "$ROW_DIR/e9_raised.png" 540 $(( (pb + bt) / 2 )))"
note "a pixel in the band: $band (the app's page shows through; the panel is (22,27,21))"
set -- $band
assert_eq "the band is see-through (not the panel's colour)" "yes" "$([ "$1" -gt 100 ] && echo yes || echo no)"
adb shell input tap $(( (bl + br) / 2 )) $(( (bt + bb) / 2 )); sleep 1.5
assert_eq "a tap in the band reaches the app (bottom_field has focus)" "bottom_field" "$(read_mirror focus)"

# Put it back down (and prove the lower clamp: rest).
respace
t4="$(drag_by 1200)"
assert_eq "dragged back down: clamped at rest" "$t0" "$t4"

kb_end
row_end
