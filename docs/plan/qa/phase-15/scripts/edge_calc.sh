#!/usr/bin/env bash
# EDGE-CALC — the Calculator edge cases (phase doc "Edge cases", Calculator line): every error string in Decisions with
# its `[calc] error <kind>` ring line; 200 !; a 1,000-digit result (e-notation, r11 8.3–8.4); a display wider than the
# screen (the result shrinks to fit, 7.4–7.5, H11); repeated = and % semantics; Programmer negatives in BIN (two's
# complement at the word size); shifts past the word size; switching word size with a value that no longer fits; paste
# of a non-numeric string (the doc says "ignored" — asserted as written, the build's behaviour recorded); memory across
# kill -9; the keypad under `wm size 1080x1920`. Expectations are the oracle's: gen_calc_cases.run_keys (the host port
# of Windows' rules that produced calc-cases.tsv), never the app. Launcher ring.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin EDGE-CALC "Calculator edge cases: errors, 200 !, e-notation, display fit, = and %, BIN negatives, shifts, word size, paste, memory across kill -9, wm size"
UI="$HERE/calc_ui.py"
D="$ROW_DIR"
CALC="app.tileshell/.calculator.CalculatorActivity"

oracle() { # mode "keys" ["setup"] -> the display Windows' rules give (host, gen_calc_cases.py)
  python3 -c "import sys; sys.path.insert(0, '$HERE'); import gen_calc_cases as g; print(g.run_keys('$1', '$2', '${3:-}'))"
}
keys() { local dump="$D/.keys.xml" k; dump_ui "$dump" || return 1; for k in "$@"; do tap_node "$dump" "calc_key:$k"; done; sleep 0.5; }
display() { dump_ui "$D/.disp.xml" >/dev/null; node_text "$D/.disp.xml" calc_display; }
node_on() {
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        print("yes" if ('checked="true"' in s or 'selected="true"' in s) else "no"); break
else:
    print("absent")
PY
}
launch_standard() { adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell am start -W -n "$CALC" --es page standard >/dev/null 2>&1; sleep 2; }
case_check() { # name mode "keys" ["setup"]: presses the keys after C on the page showing and compares with the oracle
  local name="$1" mode="$2" k="$3" setup="${4:-}" want
  want="$(oracle "$mode" "$k" "$setup")"
  # shellcheck disable=SC2086
  keys clear $k
  assert_eq "$name [$k]" "$want" "$(display)"
}
error_check() { # name mode "keys" kind ["setup"]
  local mark
  mark="$(ring_mark)"
  case_check "$1" "$2" "$3" "${5:-}"
  assert_contains "$1: ring [calc] error $4" "[calc] error $4" "$(ring_since "$mark" | grep -F '[calc] error')"
}
set_word() { # QWORD|DWORD|WORD|BYTE on the Programmer page
  local i
  for i in 1 2 3 4; do
    [ "$(python3 "$UI" label calc_key:word)" = "$1" ] && return 0
    keys word
  done
  [ "$(python3 "$UI" label calc_key:word)" = "$1" ]
}
set_radix() { keys "radix_$1"; dump_ui "$D/.rad.xml"; [ "$(node_on "$D/.rad.xml" "calc_key:radix_$1")" = yes ]; }
kill_shell() {
  ring_since "$ROW_MARK" launcher > "$D/ring-launcher-prekill.txt"
  ring_save launcher
  local pid
  pid="$(adb shell pidof app.tileshell | tr -d '\r')"
  note "kill -9 app.tileshell pid $pid"
  adb root >/dev/null 2>&1; adb wait-for-device
  adb shell kill -9 "$pid"
  adb unroot >/dev/null 2>&1; adb wait-for-device
  sleep 4
  assert_ne "the shell's process is a new one after kill -9" "$pid" "$(adb shell pidof app.tileshell | tr -d '\r')"
}

launch_standard
dump_ui "$D/baseline.xml"
assert_eq "baseline: Standard is the page showing" yes "$(node_on "$D/baseline.xml" calc_mode:standard)"
keys clear
tap_node "$D/baseline.xml" calc_key:mc 2>/dev/null || true
read -r _ dT _ dB <<< "$(bounds "$D/baseline.xml" calc_display)"
DISP_H0=$((dB - dT))
note "calc_display node height with '0' at the full 46-epx face: $DISP_H0 px"

# ---- 1. every error string in Decisions, each with its [calc] error <kind> line (Standard / Scientific / Programmer)
error_check "1 ÷ 0 = → Cannot divide by zero" standard "1 divide 0 equals" divide_by_zero
error_check "0 ÷ 0 = → Result is undefined" standard "0 divide 0 equals" undefined
error_check "√−1 (Standard) → Invalid input" standard "1 negate sqrt" invalid_input

# ---- 2. repeated = and % (Windows' semantics; the oracle's port)
case_check "2 × = = repeats the last operation" standard "2 multiply equals equals"
case_check "5 + 3 = = = repeats the last operation" standard "5 add 3 equals equals equals"
case_check "5 + = uses the display as the second operand" standard "5 add equals"
case_check "80 + 15 % = (Windows' percent)" standard "8 0 add 1 5 percent equals"
case_check "80 + 15 % % (percent of the percent)" standard "8 0 add 1 5 percent percent"
case_check "80 + 15 % % =" standard "8 0 add 1 5 percent percent equals"

# ---- 3. Scientific: Overflow, 200 !, a 1,000-digit result in e-notation, a display wider than the screen
python3 "$UI" goto scientific > "$D/goto.log" 2>&1
error_check "10 xʸ 10000 = → Overflow" scientific "1 0 pow 1 0 0 0 0 equals" overflow
case_check "200 ! (32 significant digits, e-notation; r11 8.4)" scientific "2 0 0 factorial"
case_check "10 xʸ 999 = : a 1,000-digit result shows in e-notation (r11 8.3)" scientific "1 0 pow 9 9 9 equals"
case_check "10 xʸ 1000 = (4-digit exponent, still within Overflow's 9999)" scientific "1 0 pow 1 0 0 0 equals"
case_check "1 ÷ 3 = : 32 digits, wider than the row at 46 epx" scientific "1 divide 3 equals"
dump_ui "$D/fit32.xml"; screencap "$D/fit32.png"
read -r fL fT fR fB <<< "$(bounds "$D/fit32.xml" calc_display)"
assert_within "the shrunk result keeps the 16-epx right inset (7.4–7.5, H11)" 344 "$(python3 -c "print($fR/3.0)")" 0.5
assert_eq "the shrunk result fits: its left edge is on screen" yes "$([ "$fL" -ge 0 ] && echo yes || echo no)"
assert_eq "the result shrank (node shorter than the 46-epx face's $DISP_H0 px)" yes "$([ $((fB - fT)) -lt "$DISP_H0" ] && echo yes || echo no)"
read -r il it ir ib iw ih <<< "$(python3 "$HERE/calc_geo.py" ink "$D/fit32.png" "$fL" "$fT" "$fR" "$fB" 128)"
record "32-digit result: node ${fL}-${fR} x ${fT}-${fB} px; digit ink height (epx, 33.0 at the full face)" "$(python3 -c "print(round($ih/3.0, 2))")"
keys clear

# ---- 4. Programmer: negatives in BIN, shifts past the word size, word-size switches, the 12-epx floor (7.5)
python3 "$UI" goto programmer >> "$D/goto.log" 2>&1
keys clear
set_radix bin; set_word QWORD
case_check "BIN QWORD: 1 ± is 64 ones (two's complement, r11 4.5 grouping)" programmer "1 negate" "radix=bin word=qword"
set_word WORD
case_check "BIN WORD: 1 ± is 16 ones" programmer "1 negate" "radix=bin word=word"
set_word BYTE
case_check "BIN BYTE: 101 ± is 1111 1011" programmer "1 0 1 negate" "radix=bin word=byte"
set_radix dec
case_check "BYTE: 1 Lsh 8 = (every bit shifted out) → 0" programmer "1 lsh 8 equals" "radix=dec word=byte"
case_check "BYTE: 1 Lsh 7 = wraps to −128" programmer "1 lsh 7 equals" "radix=dec word=byte"
case_check "BYTE: 1 Lsh 9 = (past the word size) → 0" programmer "1 lsh 9 equals" "radix=dec word=byte"
set_word QWORD
case_check "QWORD: 1 Lsh 64 = → 0" programmer "1 lsh 6 4 equals" "radix=dec word=qword"
case_check "QWORD: −256 Rsh 64 = → −1 (the sign fills every bit)" programmer "2 5 6 negate rsh 6 4 equals" "radix=dec word=qword"
case_check "300 → DWORD → WORD → BYTE truncates to 44" programmer "3 0 0 word word word" "radix=dec word=qword"
set_word QWORD
case_check "200 → BYTE reads −56 (signed at the word size)" programmer "2 0 0 word word word" "radix=dec word=qword"
set_word QWORD
case_check "65535 → WORD reads −1" programmer "6 5 5 3 5 word word" "radix=dec word=qword"
set_word BYTE; set_radix hex
case_check "HEX BYTE 80 → QWORD sign-extends to FFFF FFFF FFFF FF80" programmer "8 0 word" "radix=hex word=byte"
set_word QWORD; set_radix hex
case_check "HEX FFFFFFFFFFFFFFFF → BIN is 64 ones in nibbles (79 characters)" programmer "F F F F F F F F F F F F F F F F radix_bin" "radix=hex word=qword"
dump_ui "$D/floor.xml"; screencap "$D/floor.png"
read -r fL fT fR fB <<< "$(bounds "$D/floor.xml" calc_display)"
record "79-character BIN result at the 12-epx floor (7.5, H11): calc_display node px" "x ${fL}-${fR} y ${fT}-${fB} (left < 0 means the text is clipped, never scrolled)"
read -r il it ir ib iw ih <<< "$(python3 "$HERE/calc_geo.py" ink "$D/floor.png" 0 "$fT" 1080 "$fB" 128)"
record "79-character BIN result: ink height epx / ink left px" "$(python3 -c "print(round($ih/3.0, 2))") / $il"
set_radix dec; keys clear

# ---- 5. paste of a non-numeric string: the doc says ignored; the build (Windows' DisplayPasteError) may say Invalid input
adb shell am start -S -W -n app.tileshell.qa.imefixture/.MainActivity -e focus field_text >/dev/null 2>&1
sleep 2
adb shell input text "abc"
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_C
sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -W -n "$CALC" --es page standard >/dev/null 2>&1; sleep 2
keys clear 5 1 2
assert_eq "512 typed before the paste" 512 "$(display)"
dump_ui "$D/before_paste.xml"
read -r x1 y1 x2 y2 <<< "$(bounds "$D/before_paste.xml" calc_display)"
adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1200
sleep 1
dump_ui "$D/paste_menu.xml"
assert_eq "the display's hold menu offers Paste" yes "$(has_node "$D/paste_menu.xml" calc_paste)"
MARK="$(ring_mark)"
tap_node "$D/paste_menu.xml" calc_paste; sleep 1
dump_ui "$D/after_paste.xml"
pasted="$(node_text "$D/after_paste.xml" calc_display)"
assert_eq "paste of \"abc\" is ignored — the display still reads 512 (the edge-case line as written)" 512 "$pasted"
record "what the build shows after pasting \"abc\"" "$pasted"
record "the ring's paste line" "$(ring_since "$MARK" | grep -F '[calc] paste' | head -1 | sed 's/.*\[calc\]/[calc]/')"
keys clear

# ---- 6. memory across kill -9 (kept, as history)
keys clear 4 2 ms
keys mlist; dump_ui "$D/mem_before.xml"
assert_eq "MS stores 42 (calc_memory:1)" 42 "$(node_text "$D/mem_before.xml" calc_memory:1)"
keys mlist
kill_shell
launch_standard
keys mlist; dump_ui "$D/mem_after.xml"
assert_eq "memory survives kill -9 (calc_memory:1 = 42)" 42 "$(node_text "$D/mem_after.xml" calc_memory:1)"
keys mlist
keys clear mr
assert_eq "MR recalls 42 after the restart" 42 "$(display)"
keys mc mlist; dump_ui "$D/mem_cleared.xml"
assert_eq "MC empties memory (calc_memory_empty)" yes "$(has_node "$D/mem_cleared.xml" calc_memory_empty)"
keys mlist

# ---- 7. the keypad under wm size 1080x1920: rows stretch, no key clipped; wm size reset
adb shell wm size 1080x1920; sleep 3
dump_ui "$D/wm1920.xml"; screencap "$D/wm1920.png"
read -r _ nT _ nB <<< "$(bounds "$D/wm1920.xml" w10m_nav_bar)"
assert_eq "under 1080x1920 the nav bar ends at 1920" 1920 "$nB"
read -r _ kT _ kB <<< "$(bounds "$D/wm1920.xml" calc_keypad)"
assert_eq "the keypad ends at the nav bar" "$nT" "$kB"
present=0; clipped=0
for k in percent sqrt square reciprocal clear_entry clear backspace divide 7 8 9 multiply 4 5 6 subtract 1 2 3 add negate 0 decimal equals; do
  b="$(bounds "$D/wm1920.xml" "calc_key:$k")"
  if [ -n "$b" ]; then
    present=$((present + 1))
    read -r l t r bb <<< "$b"
    if [ "$l" -lt 0 ] || [ "$r" -gt 1080 ] || [ "$t" -lt "$kT" ] || [ "$bb" -gt "$nT" ]; then clipped=$((clipped + 1)); fi
  fi
done
assert_eq "all 24 Standard keys are laid out under 1080x1920" 24 "$present"
assert_eq "no key is clipped by the screen or the nav bar" 0 "$clipped"
read -r _ sT _ sB <<< "$(bounds "$D/wm1920.xml" w10m_status_bar)"
read -r _ hT _ hB <<< "$(bounds "$D/wm1920.xml" calc_header)"
rowpx="$(python3 -c "print(round(($nT - $hB) * 308.0 / 432 / 6, 2))")"
heights=""
for k in percent clear_entry 7 4 1 negate; do
  read -r l t r bb <<< "$(bounds "$D/wm1920.xml" "calc_key:$k")"
  heights="$heights $((bb - t))"
done
note "key row heights under 1080x1920 (px):$heights; predicted $rowpx"
assert_within "key rows stretch to 308/432 of the smaller content over 6 rows" "$(python3 -c "print(round($rowpx/3.0, 2))")" \
  "$(python3 -c "print(round(($kB - $kT) / 6 / 3.0, 2))")" 1
assert_eq "the six rows are equal within 1 px" yes "$(python3 -c "h=[int(v) for v in '$heights'.split()]; print('yes' if max(h)-min(h) <= 1 else 'no')")"
adb shell wm size reset; sleep 3
assert_absent "wm size reset leaves no override" "Override" "$(adb shell wm size | tr -d '\r')"
dump_ui "$D/wm_reset.xml"
read -r _ _ _ nB <<< "$(bounds "$D/wm_reset.xml" w10m_nav_bar)"
assert_eq "after wm size reset the nav bar ends at 2340 again" 2340 "$nB"

record "Not enough memory" "not reachable by a fixture: thrown only when allocation fails (conv.cpp:204-207, 237-240; E11)"

# ---- restore (RV12): C, MC, the history the row's Standard and Scientific cases wrote cleared (run 1 left four
# Scientific entries in calc_history.txt), the fixture app stopped, Home
clear_history_now() { # on a Standard / Scientific page: open the history pane, tap the trash, close it
  dump_ui "$D/.hc.xml"; tap_node "$D/.hc.xml" calc_history_toggle; sleep 0.7
  dump_ui "$D/.hc2.xml"; tap_node "$D/.hc2.xml" calc_history_clear 2>/dev/null || true; sleep 0.4
  dump_ui "$D/.hc3.xml"; tap_node "$D/.hc3.xml" calc_history_toggle; sleep 0.5
}
keys clear
dump_ui "$D/restore.xml"; tap_node "$D/restore.xml" calc_key:mc 2>/dev/null || true
clear_history_now
python3 "$UI" goto scientific >> "$D/goto.log" 2>&1 && clear_history_now
python3 "$UI" goto standard >> "$D/goto.log" 2>&1
assert_eq "restore: the history store is empty (run-as cat calc_history.txt)" empty \
  "$(adb shell "run-as app.tileshell sh -c 'cat files/calc_history.txt 2>/dev/null'" < /dev/null | tr -d '\r' | grep -c $'\t' | sed 's/^0$/empty/')"
adb shell am force-stop app.tileshell.qa.imefixture
adb shell input keyevent KEYCODE_HOME
row_end
