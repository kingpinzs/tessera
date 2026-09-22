#!/usr/bin/env bash
# E12 — the R2D-12 behaviours, driven with exact timing.
#
#   "Driven by the instrumentation APK's UiDevice with exact sleeps ... a hold on "e" past the long-press
#    timeout shows the alternates popup, and sliding to "é" and lifting commits it; Shift tapped twice
#    150 ms apart locks caps for the next three letters (Shift accent-filled in a screencap), and twice
#    500 ms apart does not; two spaces 500 ms apart after a word insert ". ", and 1500 ms apart insert
#    two spaces; &123 slide-to-type: DOWN on &123, MOVE past the touch slop within 100 ms onto "1", UP →
#    "1" committed and the letters back; &123 hold: DOWN on &123 held still for the long-press timeout +
#    100 ms → the one-handed popup (dump), slide to dock left and UP → the key grid is 0.80 of the panel
#    width ± 3 phys and flush left (dump bounds), dock right mirrors it, and the full-width glyph restores
#    the full width; a tap on &123 shows the symbol layer and it stays; a word typed twice is offered as a
#    suggestion afterwards, and never after typing it twice in a password field"
#
# Timed gestures go through the driver's `script` op (UiAutomation.injectInputEvent with real event
# times); every command's actual start time is in the row's .script files, so the spacing each sub-row
# claims is the spacing that happened. Holds that must be DUMPED while held use one `input swipe`
# process, since only one instrumentation can run at a time.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"

row_begin E12 "alternates, caps lock, double-space period, &123 slide and hold, docking, learning"
kb_begin
W="$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x.*/\1/p' | tail -1)"
SX="$(python3 -c "print($W/1440)")"
LP="$(adb shell settings get secure long_press_timeout | tr -d '\r')"; [ "$LP" = "null" ] && LP=400
note "long-press timeout ${LP} ms; 1 phys = $SX px"
D="$ROW_DIR/e12.xml"
c() { node_center "$D" "kb_key_$1"; }
run_script() { # tag script
  script "$2" > "$ROW_DIR/e12_$1.script"
  note "$1 injected: $(grep -E 'script\.(gap|cmd)\.' "$ROW_DIR/e12_$1.script" | tr -d '\r' | sed 's/INSTRUMENTATION_STATUS: gesture.script.//' | tr '\n' ' ' | cut -c1-400)"
}
fresh() { open_field "${1:-field_text}"; kb_dump "$D"; }

# ---- long-press alternates (stand-in (3), H19) --------------------------------------------------------
fresh
read -r ex ey <<< "$(c e)"
adb shell input swipe $ex $ey $ex $ey 3000 &
hold=$!
sleep 1.4
kb_dump "$ROW_DIR/e12_alt.xml"
screencap "$ROW_DIR/e12_alt.png"
wait $hold
A="$ROW_DIR/e12_alt.xml"
cells="$(for i in 0 1 2 3 4 5 6 7; do node_text "$A" "kb_alt_$i" >/dev/null; grep -o "resource-id=\"kb_alt_$i\"[^>]*" "$A" | grep -o 'content-desc="[^"]*"' | cut -d'"' -f2; done | tr '\n' ' ')"
note "alternates popup cells: $cells"
assert_eq "a hold on e past the long-press timeout shows the alternates popup" "yes" "$(has_node "$A" kb_alt_0)"
assert_contains "é is one of the cells" "é" "$cells"
read -r a2x a2y <<< "$(node_center "$A" kb_alt_2)"
fresh
run_script alt "down $ex $ey; sleep $((LP + 300)); moveto $a2x $ey 120 6; up"
sleep 1
assert_eq "sliding to é and lifting commits it" "[é]" "$(read_mirror text)"

# ---- Shift twice 150 ms apart: caps lock (stand-in (4), H22) -------------------------------------------
fresh
read -r shx shy <<< "$(c shift)"
read -r ax ay <<< "$(c a)"; read -r bx by <<< "$(c b)"; read -r cx cy <<< "$(c c)"; read -r dx dy <<< "$(c d)"
run_script caps150 "tap $shx $shy; sleep 110; tap $shx $shy"
sleep 0.6
screencap "$ROW_DIR/e12_capslock.png"
read -r sl st sr sb <<< "$(bounds "$D" kb_key_shift)"
fill="$(python3 "$HERE/measure.py" px "$ROW_DIR/e12_capslock.png" $((sl + 6)) $((st + 6)))"
note "shift key fill after the 150-ms double tap: $fill (accent 0,120,215)"
set -- $fill
assert_eq "caps lock: Shift is accent-filled (screencap)" "yes" "$([ "$3" -gt 180 ] && [ "$1" -lt 60 ] && echo yes || echo no)"
run_script abc "tap $ax $ay; sleep 250; tap $bx $by; sleep 250; tap $cx $cy"
sleep 0.8
assert_eq "the next three letters are capitals" "[ABC]" "$(read_mirror text)"
run_script unlock "tap $shx $shy; sleep 250; tap $dx $dy"
sleep 0.8
assert_eq "one tap on Shift ends caps lock" "[ABCd]" "$(read_mirror text)"
fresh
run_script caps500 "tap $shx $shy; sleep 460; tap $shx $shy"
sleep 0.4
run_script abc2 "tap $ax $ay; sleep 250; tap $bx $by; sleep 250; tap $cx $cy"
sleep 0.8
assert_eq "twice 500 ms apart is no caps lock (the second tap turns shift off)" "[abc]" "$(read_mirror text)"

# ---- double-space period (stand-in (5), H23) -----------------------------------------------------------
fresh
read -r px py <<< "$(c space)"
tap_word "$D" "hi"; sleep 0.5
run_script dsp500 "tap $px $py; sleep 460; tap $px $py"
sleep 0.8
assert_eq "two spaces 500 ms apart after a word insert '. '" "[hi. ]" "$(read_mirror text)"
fresh
tap_word "$D" "hi"; sleep 0.5
run_script dsp1500 "tap $px $py; sleep 1460; tap $px $py"
sleep 0.8
assert_eq "two spaces 1500 ms apart insert two spaces" "[hi  ]" "$(read_mirror text)"

# ---- &123 slide-to-type (stand-in (1), H21) ------------------------------------------------------------
fresh
read -r yx yy <<< "$(c sym)"
read -r qx qy <<< "$(c q)"   # "1" sits where q is: the digits page's first key
run_script slide "down $yx $yy; moveto $qx $qy 60 3; up"
sleep 0.8
assert_eq "&123 slide onto 1 commits 1" "[1]" "$(read_mirror text)"
assert_eq "and the letters are back" "layer=LETTERS" "$(ime_layer)"

# ---- &123 hold: the one-handed options, dock left / right, restore (stand-in (2), H20) -----------------
fresh
adb shell input swipe $yx $yy $yx $yy 2500 &
hold=$!
sleep 1.4
kb_dump "$ROW_DIR/e12_onehanded.xml"
screencap "$ROW_DIR/e12_onehanded.png"
wait $hold
O="$ROW_DIR/e12_onehanded.xml"
assert_eq "&123 held still opens the one-handed popup: dock left" "yes" "$(has_node "$O" kb_onehanded_left)"
assert_eq "... full width" "yes" "$(has_node "$O" kb_onehanded_full)"
assert_eq "... dock right" "yes" "$(has_node "$O" kb_onehanded_right)"
sleep 0.8; kb_dump "$D"
assert_eq "lifting where there is no cell picks nothing (still full width)" "0 $W" "$(bounds "$D" kb_strip | awk '{print $1, $3}')"
assert_eq "... and a hold types nothing" "[]" "$(read_mirror text)"
read -r olx oly <<< "$(node_center "$O" kb_onehanded_left)"
read -r orx ory <<< "$(node_center "$O" kb_onehanded_right)"
dock_check() { # side
  kb_dump "$D"
  read -r stl stt str stb <<< "$(bounds "$D" kb_strip)"
  read -r ql _ _ _ <<< "$(bounds "$D" kb_key_q)"
  read -r _ _ bkr _ <<< "$(bounds "$D" kb_key_bksp)"
  note "$1: strip [$stl..$str], q.left $ql, backspace.right $bkr"
  assert_within "docked $1: the grid and strip are 0.80 of the panel width (1152 phys)" 1152 "$(python3 -c "print(($str-$stl)/$SX)")" 3
  assert_within "docked $1: key block edge to edge (q.left..backspace.right) is 0.80 x 1426 phys" 1140.8 "$(python3 -c "print(($bkr-$ql)/$SX)")" 3
  if [ "$1" = left ]; then assert_eq "docked left: flush with the left edge" 0 "$stl"
  else assert_eq "docked right: flush with the right edge" "$W" "$str"; fi
}
run_script dockleft "down $yx $yy; sleep $((LP + 250)); moveto $olx $oly 100 5; up"
sleep 1
dock_check left
screencap "$ROW_DIR/e12_dock_left.png"
assert_eq "the freed band carries the restore glyph" "yes" "$(has_node "$D" kb_restore_full)"
read -r rx ry <<< "$(node_center "$D" kb_restore_full)"
adb shell input tap $rx $ry; sleep 1
kb_dump "$D"
assert_eq "the full-width glyph restores the full width (from the left dock)" "0 $W" "$(bounds "$D" kb_strip | awk '{print $1, $3}')"
# Dock right from the restored full width, where the popup's cells are where they were measured.
run_script dockright "down $yx $yy; sleep $((LP + 250)); moveto $orx $ory 100 5; up"
sleep 1
dock_check right
screencap "$ROW_DIR/e12_dock_right.png"
read -r rx ry <<< "$(node_center "$D" kb_restore_full)"
adb shell input tap $rx $ry; sleep 1
kb_dump "$D"
assert_eq "the full-width glyph restores the full width (from the right dock)" "0 $W" "$(bounds "$D" kb_strip | awk '{print $1, $3}')"

# ---- a tap on &123: the symbol layer, and it stays ------------------------------------------------------
fresh
tap_key "$D" sym; sleep 0.8
kb_dump "$D"
assert_eq "a tap on &123 shows the symbol layer" "layer=SYMBOLS_1" "$(ime_layer)"
tap_key "$D" 1; sleep 0.6
assert_eq "and it stays after a symbol is typed" "layer=SYMBOLS_1" "$(ime_layer)"
assert_eq "the symbol went in" "[1]" "$(read_mirror text)"

# ---- word learning (stand-in (6)): twice in text = offered; twice in a password = never ---------------
learned() { adb shell run-as app.tileshell cat files/learned_words.txt 2>/dev/null | tr -d '\r'; }
saved="$ROW_DIR/.learned_before.txt"; learned > "$saved"
fresh field_password
for _ in 1 2; do tap_word "$D" "vqzjx"; tap_key "$D" space; sleep 0.4; done
fresh
tap_word "$D" "vqz"; sleep 0.8
assert_absent "typed twice in a password field: never offered" "vqzjx" "$(ime_dump | sed -n 's/^ *strip=//p')"
fresh
for _ in 1 2; do tap_word "$D" "vqzjx"; tap_key "$D" space; sleep 0.4; done
tap_word "$D" "vqz"; sleep 0.8
assert_contains "typed twice in a text field: offered afterwards" "vqzjx" "$(ime_dump | sed -n 's/^ *strip=//p')"
adb shell run-as app.tileshell sh -c "'cat > files/learned_words.txt'" < "$saved"
pid="$(adb shell pidof app.tileshell:ime | tr -d '\r')"; [ -n "$pid" ] && adb shell run-as app.tileshell kill "$pid"
note "learned words restored; :ime (pid $pid) restarted to reload them"

kb_end
row_end
