#!/usr/bin/env bash
# E12 — Calculator modes: the pane's order (Standard / Scientific / Programmer / Date calculation / CONVERTER and the
# twelve categories, no Currency), the display value kept across modes, every `converter` line of calc-cases.tsv through
# the pane, the unit pickers and the pad (calc_ui.py), history in Standard and Scientific across kill -9 and its Clear,
# no history in Programmer, the pane's `[motion] calc_pane` line, the pane's Settings → About without Feedback.
# Phase doc E12 (T15-8, T15-15, T15-16, T15-32, T15-43). Launcher ring.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E12 "Calculator modes: pane order, categories, display across modes, converter lines, history, About"
UI="$HERE/calc_ui.py"
D="$ROW_DIR"
CALC="app.tileshell/.calculator.CalculatorActivity"

# ---- helpers: every tap is at a tagged node's bounds on a fresh dump; every read is the node's own text
keys() { # key names, tapped in order on the page showing
  local dump="$D/.keys.xml" k
  dump_ui "$dump" || return 1
  for k in "$@"; do tap_node "$dump" "calc_key:$k"; done
  sleep 0.5
}
display() { dump_ui "$D/.disp.xml" >/dev/null; node_text "$D/.disp.xml" calc_display; }
node_on() { # dump rid -> yes/no: Compose reports `selected` as selected="true" on a Tab and checked="true" elsewhere
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
launch_standard() {
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell am start -W -n "$CALC" --es page standard >/dev/null 2>&1; sleep 2
}
open_history() { dump_ui "$D/.hist.xml"; tap_node "$D/.hist.xml" calc_history_toggle; sleep 0.7; dump_ui "$1"; }
close_history() { dump_ui "$D/.hist2.xml"; tap_node "$D/.hist2.xml" calc_history_toggle; sleep 0.5; }
clear_history_now() { # on a Standard / Scientific page: open the pane, tap the trash (a no-op while empty), close
  open_history "$D/.hc.xml"
  tap_node "$D/.hc.xml" calc_history_clear 2>/dev/null || true
  sleep 0.4
  close_history
}
history_list() { # dump -> "n:expr=result" lines, newest first
  python3 - "$1" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
expr, res = {}, {}
for node in re.finditer(r"<node[^>]*>", xml):
    s = node.group(0)
    m = re.search(r'resource-id="calc_history(_result)?:(\d+)"', s)
    if m:
        t = re.search(r'text="([^"]*)"', s).group(1)
        (res if m.group(1) else expr)[int(m.group(2))] = t
for n in sorted(expr):
    print(f"{n}:{expr[n]}={res.get(n, '')}")
PY
}
kill_shell() { # phase 03 E12's form (T15-56): the ring is saved first, since the kill empties it
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

# ---- baseline: the Calculator on Standard, its history and memory cleared (RV12)
launch_standard
dump_ui "$D/baseline.xml"
assert_eq "baseline: calc_mode:standard is the page showing" yes "$(node_on "$D/baseline.xml" calc_mode:standard)"
keys clear
tap_node "$D/baseline.xml" calc_key:mc 2>/dev/null || true
clear_history_now
python3 "$UI" goto scientific >/dev/null 2>&1 && clear_history_now
python3 "$UI" goto standard >/dev/null 2>&1

# ---- 1. the pane: order, the category list, no Currency, and the open's motion line (r11 3.9, M.1; T15-16, T15-32)
MARK="$(ring_mark)"
python3 "$UI" pane-order "$D/pane_order.txt" > "$D/pane_order.log" 2>&1
echo $? > "$D/pane_order.rc"
assert_eq "pane-order ran (pane opened, scrolled to the end, closed)" 0 "$(cat "$D/pane_order.rc")"
assert_eq "pane rows 1–5 are Standard / Scientific / Programmer / Date calculation / CONVERTER (calc_mode:<id>)" \
  "calc_mode:standard calc_mode:scientific calc_mode:programmer calc_mode:date calc_mode:converter" \
  "$(head -5 "$D/pane_order.txt" | cut -f1 | paste -sd' ')"
assert_eq "pane labels 1–5" "Standard|Scientific|Programmer|Date calculation|CONVERTER" "$(head -5 "$D/pane_order.txt" | cut -f2 | paste -sd'|')"
assert_eq "the category list equals W10M's twelve, in order (Weight and Mass, no Currency)" \
  "Volume|Length|Weight and Mass|Temperature|Energy|Area|Speed|Time|Power|Data|Pressure|Angle" \
  "$(tail -n +6 "$D/pane_order.txt" | cut -f2 | paste -sd'|')"
assert_eq "the category tags are calc_converter_category:1..12 in that order" "$(seq -s' ' 1 12)" \
  "$(tail -n +6 "$D/pane_order.txt" | cut -f1 | sed 's/.*://' | paste -sd' ')"
assert_eq "no node reads Currency (pane dump texts)" 0 "$(cat "$D/pane_order.txt" "$D/pane_order.txt.texts" | grep -ci currency || true)"
note "the category label is the text of an untagged child TextView under calc_converter_category:<n> (the tag's own text is empty)"
motion="$(ring_since "$MARK" | grep -F '[motion] calc_pane' | head -1)"
echo "$motion" > "$D/motion-calc_pane.txt"
assert_contains "the pane's open logs [motion] calc_pane" "[motion] calc_pane" "$motion"
settle="$(echo "$motion" | grep -oE 'settle=[0-9.]+' | head -1 | cut -d= -f2)"
gap="$(echo "$motion" | grep -oE 'maxGapMs=[0-9.]+' | head -1 | cut -d= -f2)"
assert_within "calc_pane settle = 167 ± 50 ms (r11 M.1, LOW, tagged)" 167 "${settle:-}" 50
assert_eq "calc_pane maxGapMs ≤ 33.4 (got ${gap:-none})" yes "$(python3 -c "print('yes' if '${gap:-}' and float('${gap:-0}') <= 33.4 else 'no')")"

# ---- 2. switching modes keeps the display value (one engine, CalcModel)
keys clear 1 2 3
assert_eq "Standard shows 123 typed" 123 "$(display)"
python3 "$UI" goto scientific > "$D/goto.log" 2>&1
dump_ui "$D/sci.xml"
assert_eq "Scientific is the page showing" yes "$(node_on "$D/sci.xml" calc_mode:scientific)"
assert_eq "Standard → Scientific keeps 123 on the display" 123 "$(node_text "$D/sci.xml" calc_display)"
python3 "$UI" goto programmer >> "$D/goto.log" 2>&1
dump_ui "$D/prog.xml"
assert_eq "Programmer is the page showing" yes "$(node_on "$D/prog.xml" calc_mode:programmer)"
assert_eq "Scientific → Programmer keeps 123 on the display" 123 "$(node_text "$D/prog.xml" calc_display)"
assert_eq "Programmer's DEC radix row reads 123" 123 "$(node_text "$D/prog.xml" calc_radix:dec)"
python3 "$UI" goto standard >> "$D/goto.log" 2>&1
assert_eq "Programmer → Standard keeps 123 on the display" 123 "$(display)"
keys clear

# ---- 3. the Converter: every `converter` line of the oracle through the app's UI (T15-8); ≥ 2 lines per category
python3 "$UI" converter "$D/results-converter.tsv" > "$D/drive-converter.log" 2>&1
echo $? > "$D/drive-converter.rc"
assert_eq "converter driver ran to the end" 0 "$(cat "$D/drive-converter.rc")"
n=0
while IFS=$'\t' read -r id expected got keys units; do
  n=$((n + 1))
  assert_eq "$id [$keys] $units" "$expected" "$got"
done < "$D/results-converter.tsv"
want="$(awk -F'\t' '$2 == "converter"' "$QROOT/phase-15/calc-cases.tsv" | wc -l)"
assert_eq "every converter oracle line was driven" "$want" "$n"
for cat in VOLUME LENGTH "WEIGHT AND MASS" TEMPERATURE ENERGY AREA SPEED TIME POWER DATA PRESSURE ANGLE; do
  c="$(cut -f5 "$D/results-converter.tsv" | grep -c "^$cat:" || true)"
  assert_eq "$cat: at least 2 oracle lines driven (got $c)" yes "$([ "${c:-0}" -ge 2 ] && echo yes || echo no)"
done
assert_contains "1 mile → 1.609344 kilometers exactly (converter-004)" $'converter-004\t1.609344\t1.609344' "$(grep '^converter-004' "$D/results-converter.tsv")"
assert_contains "100 °C → 212 °F (converter-012)" $'converter-012\t212\t212' "$(grep '^converter-012' "$D/results-converter.tsv")"

# ---- 4. history in Standard and Scientific: the last expressions listed, kept across kill -9, cleared by Clear
python3 "$UI" goto standard >> "$D/goto.log" 2>&1
keys clear 2 add 3 equals
keys 1 0 multiply 4 equals
keys 7 subtract 2 equals
assert_eq "7 − 2 = shows 5" 5 "$(display)"
open_history "$D/hist_std.xml"
history_list "$D/hist_std.xml" > "$D/hist_std.txt"
# The expression is Windows' own string: CalculatorHistory.cpp GetGeneratedExpression joins the engine's tokens —
# operand, " ", operator, " ", operand, "=" (History.cpp AddBinOpToHistory pads the operator with its own " " tokens) —
# with a space, so "7 − 2 =" is stored as "7   -   2 =" (three spaces around the operator, one before the =). Run 1 of
# this row expected single spaces and failed on the driver's guess, not the product.
assert_eq "Standard history lists the three expressions, newest first (calc_history:<n>; Windows' token join)" \
  "1:7   -   2 ==5|2:10   ×   4 ==40|3:2   +   3 ==5" "$(paste -sd'|' "$D/hist_std.txt")"
close_history
python3 "$UI" goto scientific >> "$D/goto.log" 2>&1
keys clear 4 multiply 5 equals
keys 9 subtract 4 equals
open_history "$D/hist_sci.xml"
history_list "$D/hist_sci.xml" > "$D/hist_sci.txt"
assert_eq "Scientific history lists its two expressions, newest first" "1:9   -   4 ==5|2:4   ×   5 ==20" "$(paste -sd'|' "$D/hist_sci.txt")"
close_history
kill_shell
launch_standard
dump_ui "$D/after_kill.xml"
assert_eq "after kill -9 the Calculator opens on Standard" yes "$(node_on "$D/after_kill.xml" calc_mode:standard)"
open_history "$D/hist_std_after.xml"
history_list "$D/hist_std_after.xml" > "$D/hist_std_after.txt"
assert_eq "Standard history survives kill -9" "$(paste -sd'|' "$D/hist_std.txt")" "$(paste -sd'|' "$D/hist_std_after.txt")"
tap_node "$D/hist_std_after.xml" calc_history_clear; sleep 0.5
dump_ui "$D/hist_std_cleared.xml"
assert_eq "Clear history empties Standard's list (calc_history_empty)" yes "$(has_node "$D/hist_std_cleared.xml" calc_history_empty)"
assert_eq "no calc_history:<n> node after Clear (Standard)" 0 "$(grep -c 'resource-id="calc_history:' "$D/hist_std_cleared.xml" || true)"
close_history
python3 "$UI" goto scientific >> "$D/goto.log" 2>&1
open_history "$D/hist_sci_after.xml"
history_list "$D/hist_sci_after.xml" > "$D/hist_sci_after.txt"
assert_eq "Scientific history survives kill -9" "$(paste -sd'|' "$D/hist_sci.txt")" "$(paste -sd'|' "$D/hist_sci_after.txt")"
tap_node "$D/hist_sci_after.xml" calc_history_clear; sleep 0.5
dump_ui "$D/hist_sci_cleared.xml"
assert_eq "Clear history empties Scientific's list" yes "$(has_node "$D/hist_sci_cleared.xml" calc_history_empty)"
close_history

# ---- 5. Programmer: no History glyph, and five calculations leave no history anywhere (r11 4.15)
python3 "$UI" goto programmer >> "$D/goto.log" 2>&1
dump_ui "$D/prog2.xml"
assert_eq "Programmer's header has no History glyph (calc_history_toggle absent)" no "$(has_node "$D/prog2.xml" calc_history_toggle)"
for i in 1 2 3 4 5; do keys clear "$i" add "$i" equals; done
assert_eq "5 + 5 = shows 10 in Programmer" 10 "$(display)"
dump_ui "$D/prog_after5.xml"
assert_eq "after five Programmer calculations the dump holds no calc_history:* node" 0 "$(grep -c 'resource-id="calc_history' "$D/prog_after5.xml" || true)"
python3 "$UI" goto standard >> "$D/goto.log" 2>&1
open_history "$D/hist_std_after_prog.xml"
assert_eq "Standard's history is still empty after the Programmer calculations (no leak)" yes "$(has_node "$D/hist_std_after_prog.xml" calc_history_empty)"
close_history

# ---- 6. the pane's Settings opens about_page with no Feedback entry (T15-43)
dump_ui "$D/.menu.xml"; tap_node "$D/.menu.xml" calc_menu; sleep 0.8
dump_ui "$D/pane_settings.xml"
tap_node "$D/pane_settings.xml" calc_pane_settings; sleep 0.8
dump_ui "$D/about.xml"
assert_eq "Settings opens about_page" yes "$(has_node "$D/about.xml" about_page)"
assert_eq "the About page's title" ABOUT "$(node_text "$D/about.xml" calc_title)"
assert_eq "no Feedback entry on the About page" 0 "$(grep -ci 'feedback' "$D/about.xml" || true)"
adb shell input keyevent KEYCODE_BACK; sleep 0.7
dump_ui "$D/after_about.xml"
assert_eq "Back from About returns to the Standard page" yes "$(node_on "$D/after_about.xml" calc_mode:standard)"

# ---- restore (RV12): history cleared (above), memory cleared, the converter's last-used category back to Volume, Home
python3 "$UI" goto converter:1 >> "$D/goto.log" 2>&1
python3 "$UI" goto standard >> "$D/goto.log" 2>&1
keys clear
dump_ui "$D/restore.xml"
tap_node "$D/restore.xml" calc_key:mc 2>/dev/null || true
adb shell input keyevent KEYCODE_HOME
row_end
