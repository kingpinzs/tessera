#!/usr/bin/env bash
# E4 — Word Flow: one continuous gesture over a word's letters commits that word.
#
#   "one continuous gesture injected with UiAutomator UiDevice.swipe(Point[], steps) from an
#    instrumentation APK over the letters of a dictionary word commits that word"
#
# The path goes through each letter's key CENTRE as the keyboard's own dump reports it, so nothing here
# assumes the layout. Five words in a row also prove D1 l.1503-1506 ("keep on swiping without tapping
# the space key. Your phone adds the spaces") and R6 2.4.5 (on lift the strip shows the swiped word's
# candidates, bold first).
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E4 "Word Flow commits the swiped dictionary words, with the spaces added"
kb_begin
open_field field_text
D="$ROW_DIR/e4.xml"
kb_dump "$D"

path_for() { # word -> "x,y;x,y;..." through each distinct consecutive letter's key centre
  local w="$1" i prev="" pts="" c
  for ((i = 0; i < ${#w}; i++)); do
    c="${w:i:1}"
    [ "$c" = "$prev" ] && continue   # a double letter is one key visit
    prev="$c"
    read -r x y <<< "$(node_center "$D" "kb_key_$c")"
    pts="${pts:+$pts;}$x,$y"
  done
  echo "$pts"
}

expected=""
for w in hello world phone water great; do
  pts="$(path_for "$w")"
  note "$w: path $pts"
  out="$(swipe_pts "$pts" 8)"
  echo "$out" | grep -E "swipe\.(result|elapsed_ms)" | while read -r l; do note "  $l"; done
  sleep 1.2
  expected="${expected:+$expected }$w"
  assert_eq "after swiping $w" "[$expected]" "$(read_mirror text)"
  strip="$(ime_dump | sed -n 's/^ *strip=//p' | tr -d '\r')"
  note "  strip: $strip"
  assert_eq "R6 2.4.5 the strip leads with the swiped word, bold" "*$w" "$(echo "$strip" | cut -d'|' -f1 | tr -d ' ')"
done
assert_contains "the diagnostics name the decoder's candidates" "word flow" "$(ime_log 'word flow' | tail -1)"
screencap "$ROW_DIR/e4_after.png"

kb_end
row_end
