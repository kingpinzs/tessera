#!/usr/bin/env bash
# E11 — Calculator correctness: every standard / scientific / programmer line of calc-cases.tsv (the independent
# oracle, host-computed from Windows' source by gen_calc_cases.py) pressed on the REAL Calculator and read back from
# calc_display; then the paste of "12+3" through the real clipboard. Phase doc E11 (T15-15, T15-58).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E11 "Calculator answers equal Windows' for every oracle line; paste of 12+3 evaluates"

for mode in standard scientific programmer; do
  out="$ROW_DIR/results-$mode.tsv"
  python3 "$(dirname "$0")/calc_drive.py" "$mode" "$out" > "$ROW_DIR/drive-$mode.log" 2>&1
  echo $? > "$ROW_DIR/drive-$mode.rc"
  assert_eq "$mode driver ran to the end" 0 "$(cat "$ROW_DIR/drive-$mode.rc")"
  n=0
  while IFS=$'\t' read -r id expected got keys; do
    n=$((n + 1))
    assert_eq "$id [$keys]" "$expected" "$got"
  done < "$out"
  want="$(awk -F'\t' -v m="$mode" '$2 == m' "$QROOT/phase-15/calc-cases.tsv" | wc -l)"
  assert_eq "$mode: every oracle line was driven" "$want" "$n"
done

# The paste (T15-57): the image has no `cmd clipboard`, so "12+3" goes onto the clipboard the way a person puts it
# there — typed into a real text field (phase 05's fixture app, an EditText) and copied — and is pasted into the
# Calculator from the display's hold menu.
adb shell am start -S -W -n app.tileshell.qa.imefixture/.MainActivity -e focus field_text >/dev/null 2>&1
sleep 2
adb shell input text "12+3"
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_C
sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -W -n app.tileshell/.calculator.CalculatorActivity --es page standard >/dev/null 2>&1
sleep 2
dump_ui "$ROW_DIR/before_paste.xml"
tap_node "$ROW_DIR/before_paste.xml" calc_key:clear
read -r x1 y1 x2 y2 <<< "$(bounds "$ROW_DIR/before_paste.xml" calc_display)"
cx=$(( (x1 + x2) / 2 )); cy=$(( (y1 + y2) / 2 ))
adb shell input swipe "$cx" "$cy" "$cx" "$cy" 1200
sleep 1
dump_ui "$ROW_DIR/paste_menu.xml"
assert_eq "the display's hold menu offers Paste (calc_paste)" yes "$(has_node "$ROW_DIR/paste_menu.xml" calc_paste)"
tap_node "$ROW_DIR/paste_menu.xml" calc_paste
sleep 1
# Windows' paste enters the characters as key presses and sends "=" only when the text holds one
# (StandardCalculatorViewModel.cs OnPaste :1601-1718): "12+3" leaves 3 on the display with "12 +" pending, and the
# next = gives 15 — so the row checks both halves rather than a paste that would evaluate on its own.
dump_ui "$ROW_DIR/after_paste.xml"
assert_eq "after the paste the display shows the last operand" 3 "$(node_text "$ROW_DIR/after_paste.xml" calc_display)"
assert_contains "after the paste 12 + is pending" "12 +" "$(node_text "$ROW_DIR/after_paste.xml" calc_expr)"
tap_node "$ROW_DIR/after_paste.xml" calc_key:equals
sleep 1
dump_ui "$ROW_DIR/after_equals.xml"
assert_eq "12+3 pasted, then =, is 15" 15 "$(node_text "$ROW_DIR/after_equals.xml" calc_display)"

record "Result not defined" "not reachable by a fixture: its only producer, a Lsh / Rsh of the word size or more (scioper.cpp:41-44, 65-68, 74-77), gives the right answer instead (0, or -1 for an arithmetic Rsh of a negative; ruling 2026-09-24)"
record "Not enough memory" "not reachable by a fixture: thrown only when allocation fails (conv.cpp:204-207, 237-240)"

# Restore (RV12): memory and history cleared, the fixture app stopped, Home.
dump_ui "$ROW_DIR/restore.xml"
tap_node "$ROW_DIR/restore.xml" calc_key:clear
tap_node "$ROW_DIR/restore.xml" calc_key:mc 2>/dev/null || true
adb shell am force-stop app.tileshell.qa.imefixture
adb shell input keyevent KEYCODE_HOME
row_end
