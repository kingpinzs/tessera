#!/usr/bin/env bash
# E6 — dragging the cursor-control dot moves the caret.
#
#   "Dragging the cursor-control dot moves the caret in the target field (the fixture's mirrored
#    selectionStart / selectionEnd in the dump)"
#
# The drag is the documented gesture (R6 2.5.6, D1 l.1542-1545: "tap and hold the cursor controller,
# and drag your finger to the direction you want") and the stepping is R6 2.5.8's candidate (LOW, H5):
# a direction-locked joystick, one character every ≈150 ms left or right, one line every 0.5-1 s up or
# down. The row times its own holds with the driver's injected event clock, so the expected step count
# comes from how long the finger was really held, not from a sleep.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E6 "the cursor-control dot moves the caret"
kb_begin
open_field field_text
D="$ROW_DIR/e6.xml"; kb_dump "$D"
tap_word "$D" "hello world"
sleep 1
assert_eq "start: caret at the end of the text" "11,11" "$(read_mirror sel)"
read -r dx dy <<< "$(node_center "$D" kb_cursor_dot)"
note "dot centre ($dx, $dy)"

hold_dir() { # dx dy hold-ms tag
  local out
  out="$(script "down $dx $dy; moveto $((dx + $1)) $((dy + $2)) 80 4; sleep $3; up")"
  echo "$out" | grep -E "script\.(start|cmd)\." > "$ROW_DIR/e6_$4.script"
  note "$4: $(tr '\n' ' ' < "$ROW_DIR/e6_$4.script" | cut -c1-300)"
}

# Left: held ≈1050 ms outside the ring → ≈7 steps at 150 ms (the first step lands as the axis locks).
( sleep 0.7; screencap "$ROW_DIR/e6_held.png" ) &
hold_dir -140 0 1050 left
wait
sleep 0.8
sel="$(read_mirror sel)"; c="${sel%%,*}"
note "after holding left: sel=$sel"
assert_within "R6 2.5.8 caret stepped left ≈ one char per 150 ms over ≈1.1 s (7 ± 2)" 7 $((11 - c)) 2
assert_eq "the selection stayed collapsed (a caret, not a selection)" "$c,$c" "$sel"

# Right: back the other way.
before="$c"
hold_dir 140 0 600 right
sleep 0.8
sel="$(read_mirror sel)"; c="${sel%%,*}"
note "after holding right: sel=$sel"
assert_within "caret stepped right (4 ± 2 over ≈0.65 s)" 4 $((c - before)) 2

# Direction lock: a drag that goes right first and then drifts down keeps stepping horizontally.
before="$c"
script "down $dx $dy; moveto $((dx + 140)) $dy 80 4; moveto $((dx + 140)) $((dy + 60)) 80 4; sleep 300; up" > /dev/null
sleep 0.8
sel="$(read_mirror sel)"; c="${sel%%,*}"
assert_eq "R6 2.5.8 the axis locks: drifting down after going right still moves the caret sideways" "yes" "$([ "$c" -gt "$before" ] && echo yes || echo no)"

# Releasing leaves the caret in place and restores the keyboard (R6 2.5.6 / 2.5.8).
screencap "$ROW_DIR/e6_released.png"
kb_dump "$D"
read -r ql qt qr qb <<< "$(bounds "$D" kb_key_q)"
dim="$(python3 "$HERE/measure.py" px "$ROW_DIR/e6_held.png" $((ql + 6)) $((qt + 6)))"
back="$(python3 "$HERE/measure.py" px "$ROW_DIR/e6_released.png" $((ql + 6)) $((qt + 6)))"
note "key q while held: $dim; after release: $back"
set -- $dim; held_r=$1
set -- $back; rel_r=$1
assert_within "R6 2.5.7 (LOW, H4) the keyboard dims to ≈50 % while the dot is held (key 48 → ≈24)" 24 "$held_r" 6
assert_within "released: the keyboard is restored (key 48)" 48 "$rel_r" 6

# Up / down in a multi-line field: one line per 0.5-1 s.
open_field field_multiline
kb_dump "$D"
tap_word "$D" "one"; tap_key "$D" enter; tap_word "$D" "two"
sleep 1
start="$(read_mirror sel)"
note "multi-line text $(read_mirror text), caret $start"
hold_dir 0 -140 900 up
sleep 0.8
sel="$(read_mirror sel)"; c="${sel%%,*}"
note "after holding up: sel=$sel"
assert_eq "R6 2.5.8 holding above the dot moves the caret up a line" "yes" "$([ "$c" -le 3 ] && echo yes || echo no)"
hold_dir 0 140 900 down
sleep 0.8
sel="$(read_mirror sel)"; c="${sel%%,*}"
note "after holding down: sel=$sel"
assert_eq "holding below the dot moves it back down" "yes" "$([ "$c" -ge 4 ] && echo yes || echo no)"

kb_end
row_end
