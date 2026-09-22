#!/usr/bin/env bash
# E10 — cursor-controller handedness moves the dot.
#
#   "With "Left handed usage" selected, the cursor dot's centre measures 1077.5 ± 3 phys from the left
#    and 218 ± 3 phys above the nav bar, scaled by display width / 1440 (dump bounds, screencap);
#    "Right handed usage" puts it back at 358 phys"
#
# The setting is changed through the real Keyboard page in Start settings, and the dot is measured
# twice: from the keyboard's dump and from the accent pixels actually drawn on the screen.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E10 "handedness: left 1077.5, right 358, both 218 above the nav bar"
kb_begin
W="$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x.*/\1/p' | tail -1)"
SX="$(python3 -c "print($W/1440)")"

set_hand() { # left|right
  adb shell am start -W -f 0x10008000 -n app.tileshell/.settings.SettingsActivity --es page KEYBOARD >/dev/null
  sleep 1.5
  local f="$ROW_DIR/.kp.xml"
  scroll_to_node "$f" "keyboard_cursor_$1" 4 >/dev/null 2>&1
  tap_node "$f" "keyboard_cursor_$1"; sleep 0.8
  dump_ui "$f"
  # Compose reports a selected radio row as CHECKED (checkable="true" checked="true"), not selected.
  assert_eq "Settings: $1-handed is selected" "true" "$(grep -o "resource-id=\"keyboard_cursor_$1\"[^>]*" "$f" | grep -o ' checked="[a-z]*"' | cut -d'"' -f2)"
}

measure() { # tag expected-x
  open_field field_text
  local D="$ROW_DIR/e10_$1.xml" P="$ROW_DIR/e10_$1.png"
  kb_dump "$D"; screencap "$P"
  read -r l t r b <<< "$(bounds "$D" kb_cursor_dot)"
  read -r _ _ _ nav <<< "$(bounds "$D" kb_panel)"
  local cx cy
  cx="$(python3 -c "print((($l+$r)/2)/$SX)")"; cy="$(python3 -c "print(($nav-($t+$b)/2)/$SX)")"
  assert_within "$1-handed: dot centre from the left (dump)" "$2" "$cx" 3
  assert_within "$1-handed: dot centre above the nav bar (dump)" 218 "$cy" 3
  # The drawn accent core, off the screen: its centroid, one device pixel of allowance (1/SX phys).
  local acc
  acc="$(python3 "$HERE/measure.py" accent "$P" "$l" "$t" "$r" "$b")"
  note "$1: dot node [$l,$t][$r,$b]; accent core pixels $acc"
  local ax
  ax="$(echo $acc | awk -v s="$SX" '{print (($2+$4)/2)/s}')"
  assert_within "$1-handed: the accent core is drawn there (screen, +1 px)" "$2" "$ax" "$(python3 -c "print(3 + 1/$SX)")"
}

set_hand left
measure left 1077.5
set_hand right
measure right 358

kb_end
row_end
