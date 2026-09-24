#!/usr/bin/env bash
# E13 — Calculator geometry ([fidelity] H2, judged against 10586; r11/calculator.md §1–§5, gap 1 tolerances: ± 0.5 epx
# for fixed-epx values, ± 1 epx for star-row edges; colours ± 2 levels, the composite selected fill ± 3). Every value is
# computed in epx from the dump's bounds or the screencap's pixels (px per epx = 1080 / 360 = 3; calc_geo.py measures
# ink boxes, row runs and colours) and compared with r11's value; a value r11 marks LOW / UNMEASURED is RECORDed. The
# bars are asserted against BarMetrics.STATUS_EPX / NAV_EPX read from the source (C-17), the accent against
# Palette.DEFAULT_ACCENT (this AVD is fresh: the out-of-box accent). Launcher ring.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"
row_begin E13 "Calculator geometry: header, Standard rows / columns / fills / result, pane, Programmer, Speed converter"
UI="$HERE/calc_ui.py"
GEO="$HERE/calc_geo.py"
D="$ROW_DIR"
CALC="app.tileshell/.calculator.CalculatorActivity"

STATUS_EPX="$(grep -oE 'STATUS_EPX = [0-9]+' "$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt" | grep -oE '[0-9]+$')"
NAV_EPX="$(grep -oE 'NAV_EPX = [0-9]+' "$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt" | grep -oE '[0-9]+$')"
ACCENT_HEX="$(grep -oE 'DEFAULT_ACCENT = 0x[0-9A-Fa-f]+' "$REPO/app/src/main/kotlin/app/tileshell/ui/tokens/Palette.kt" | grep -oE '0x[0-9A-Fa-f]+')"
ACCENT="$(python3 -c "v=int('$ACCENT_HEX',16); print(f'{(v>>16)&255},{(v>>8)&255},{v&255}')")"
SELECTED_FILL="$(python3 -c "
a=[int(v) for v in '$ACCENT'.split(',')]; print(','.join(str(round(0.6*c+0.4*43)) for c in a))")"
note "BarMetrics STATUS_EPX=$STATUS_EPX NAV_EPX=$NAV_EPX; DEFAULT_ACCENT $ACCENT_HEX = ($ACCENT); selected pane fill 0.6·accent+0.4·43 = ($SELECTED_FILL)"

# ---- helpers
P() { python3 -c "print(round($1, 3))"; }
epx() { P "($1)/3.0"; }
b4() { read -r L T R B <<< "$(bounds "$1" "$2")"; [ -n "${L:-}" ]; }   # sets L T R B (px)
cx_epx() { b4 "$1" "$2" && P "($L+$R)/6.0"; }
rgb_near() { # name expected "r,g,b" actual "r,g,b" tol
  local ok
  ok="$(python3 -c "
a='$3'
try:
    e=[int(v) for v in '$2'.split(',')]; g=[int(v) for v in a.split(',')]
    print('yes' if max(abs(x-y) for x,y in zip(e,g)) <= $4 else 'no')
except Exception: print('no')")"
  if [ "$ok" = yes ]; then _verdict PASS "$1" "$3 ≈ $2 ± $4"; else _verdict FAIL "$1" "got [$3], wanted $2 ± $4"; fi
}
ink() { python3 "$GEO" ink "$@"; }        # png x1 y1 x2 y2 [thr] -> left top right bottom w h
px() { python3 "$GEO" px "$@"; }
scan() { python3 "$GEO" scan "$@"; }
vscan() { python3 "$GEO" vscan "$@"; }
child_text_bounds() { # dump rid -> the bounds of the first child TextView with text (the pane rows' labels)
  python3 - "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.get("resource-id") == sys.argv[2]:
        for c in n.iter("node"):
            if c is not n and c.get("text"):
                print(" ".join(re.findall(r"-?\d+", c.get("bounds")))); sys.exit()
print("")
PY
}
keys() { local dump="$D/.keys.xml" k; dump_ui "$dump" || return 1; for k in "$@"; do tap_node "$dump" "calc_key:$k"; done; sleep 0.5; }

# ---- baseline: the Calculator on Standard showing 512
adb shell input keyevent KEYCODE_HOME; sleep 1
adb shell am start -W -n "$CALC" --es page standard >/dev/null 2>&1; sleep 2
keys clear 5 1 2
dump_ui "$D/std.xml"; screencap "$D/std.png"
assert_eq "Standard shows 512" 512 "$(node_text "$D/std.xml" calc_display)"
b4 "$D/std.xml" calc_root; W=$R; H=$B
note "screen ${W}x${H} px = $(epx $W)x$(epx $H) epx"

# ---- 1. the drawn bars (as E10, C-17) and the header (§1.2–1.7)
b4 "$D/std.xml" w10m_status_bar; SB=$B
assert_within "status bar height = BarMetrics.STATUS_EPX" "$STATUS_EPX" "$(epx $((B - T)))" 0.5
b4 "$D/std.xml" w10m_nav_bar; NT=$T
assert_within "nav bar height = BarMetrics.NAV_EPX" "$NAV_EPX" "$(epx $((B - T)))" 0.5
assert_eq "nav bar bottom = screen bottom" "$H" "$B"
b4 "$D/std.xml" calc_header; HT=$T; HB=$B
assert_within "header top = status bar bottom (1.2)" "$(epx $SB)" "$(epx $HT)" 0.5
assert_within "header 48 epx tall (1.2)" 48 "$(epx $((HB - HT)))" 0.5
runs="$(python3 "$GEO" rows "$D/std.png" 0 "$SB" 144 "$HB" 128)"
note "≡ bar row runs (px): $runs"
assert_eq "≡ is three bars (1.3)" 3 "$(echo "$runs" | wc -w)"
i=0
for want in 19 24 29; do
  i=$((i + 1))
  run="$(echo "$runs" | cut -d' ' -f$i)"
  assert_within "≡ bar $i centre $want epx below the status bar (1.3: y 43/48/53 under a 24-epx bar)" "$want" \
    "$(P "((${run%-*}+${run#*-}+1)/2.0-$SB)/3.0")" 0.5
done
read -r il it ir ib iw ih <<< "$(ink "$D/std.png" 0 "$SB" 144 "$HB" 128)"
assert_within "≡ cx 23.5 epx (1.3)" 23.5 "$(P "($il+$ir+1)/6.0")" 0.5
assert_within "≡ bars 20 epx long (1.3)" 20 "$(epx $iw)" 0.5
read -r il it ir ib iw ih <<< "$(ink "$D/std.png" 150 "$SB" 700 "$HB" 128)"
assert_within "title ink left 60.5 epx (1.5)" 60.5 "$(epx $il)" 0.5
assert_within "title cap 11.0 epx (1.5)" 11 "$(epx $ih)" 0.5
assert_within "title cap centre 26 epx below the status bar (1.5: y 50 under a 24-epx bar)" 26 "$(P "(($it+$ib+1)/2.0-$SB)/3.0")" 0.5
assert_eq "Standard's header has the History glyph (1.7)" yes "$(has_node "$D/std.xml" calc_history_toggle)"
read -r il it ir ib iw ih <<< "$(ink "$D/std.png" 936 "$SB" "$W" "$HB" 128)"
assert_within "History glyph 16 epx wide (1.7)" 16 "$(epx $iw)" 0.5
assert_within "History glyph 16 epx tall (1.7)" 16 "$(epx $ih)" 0.5
assert_within "History glyph right inset 15 epx (1.7)" 15 "$(P "($W-$ir-1)/3.0")" 0.5
assert_within "History glyph centred on the header (cy 24 epx below the status bar, 1.7)" 24 "$(P "(($it+$ib+1)/2.0-$SB)/3.0")" 0.5

# ---- 2. Standard's star rows 20 : 72 : 32 : 308 over content = height − status − 48 − nav (§2.1, §9.2)
CONTENT=$((NT - HB))
exprB="$(P "$HB + 20.0*$CONTENT/432")"; resB="$(P "$HB + 92.0*$CONTENT/432")"; memB="$(P "$HB + 124.0*$CONTENT/432")"
rowpx="$(P "308.0*$CONTENT/432/6")"
note "content $CONTENT px = $(epx $CONTENT) epx; predicted rows (px): expression→$exprB result→$resB memory→$memB key row $rowpx"
b4 "$D/std.xml" calc_key:mc
assert_within "memory row top = result row bottom (92/432 of the content, 2.1)" "$(epx "$resB")" "$(epx $T)" 1
assert_within "memory row bottom (124/432, 2.1)" "$(epx "$memB")" "$(epx $B)" 1
b4 "$D/std.xml" calc_keypad; KT=$T
assert_within "keypad top = memory row bottom (2.1)" "$(epx "$memB")" "$(epx $T)" 1
assert_within "keypad bottom = nav bar top (2.1)" "$(epx $NT)" "$(epx $B)" 1
r=0
for k in percent clear_entry 7 4 1 negate; do
  b4 "$D/std.xml" "calc_key:$k"
  assert_within "key row $((r + 1)) top (6 equal rows of 308/432, 2.1–2.2)" "$(epx "$(P "$memB + $r*$rowpx")")" "$(epx $T)" 1
  r=$((r + 1))
done
b4 "$D/std.xml" calc_key:equals
assert_within "key row 6 bottom = nav bar top" "$(epx $NT)" "$(epx $B)" 1
# the result: 33-epx digits at a 16-epx right inset, cap top 17.4 epx into the result row (2.14; the row top is derived)
read -r il it ir ib iw ih <<< "$(ink "$D/std.png" 600 "${exprB%.*}" "$W" "${resB%.*}" 128)"
assert_within "result digits 33.0 epx tall (2.14)" 33 "$(epx $ih)" 0.5
assert_within "result right inset 16 epx (2.14)" 16 "$(P "($W-$ir-1)/3.0")" 0.5
assert_within "result cap top 17.4 epx into the result row (2.14, ± 1: the row edge is a star edge)" 17.4 "$(P "($it-$exprB)/3.0")" 1
b4 "$D/std.xml" calc_display
assert_within "calc_display's right edge at the 16-epx inset" 344 "$(epx $R)" 0.5

# ---- 3. columns W/4 (2.7), the rule and the fills (2.4–2.6), the memory row on W/6 (2.16)
for pair in 7:45 8:135 9:225 multiply:315; do
  assert_within "key ${pair%:*} centre at ${pair#*:} epx (W/4, 2.7)" "${pair#*:}" "$(cx_epx "$D/std.xml" "calc_key:${pair%:*}")" 0.5
done
b4 "$D/std.xml" calc_key:8
read -r il it ir ib iw ih <<< "$(ink "$D/std.png" "$L" "$T" "$R" "$B" 128)"
assert_within "the 8 glyph's ink centre at 135 epx (2.7)" 135 "$(P "($il+$ir+1)/6.0")" 0.5
rule_rows=$(( 9 - $(vscan "$D/std.png" 100 $((KT - 3)) $((KT + 6)) 25,25,25 2 | cut -d' ' -f1) ))
assert_within "a 1-epx (25,25,25) rule at the keypad's top (2.4: 3 px of 9 sampled)" 3 "$rule_rows" 1
rgb_near "the rule's first row is the keypad's top row" 25,25,25 "$(px "$D/std.png" 100 "$KT")" 2
rgb_near "the row above the rule (memory row) is black" 0,0,0 "$(px "$D/std.png" 100 $((KT - 1)))" 2
y=$(P "$KT + $rowpx/2"); y=${y%.*}
for x in 10 270 540 810 1070; do
  rgb_near "key row 1 on black at x=$x (2.5)" 0,0,0 "$(px "$D/std.png" "$x" "$y")" 2
done
for r in 1 2 3 4 5; do
  y=$(P "$KT + $r*$rowpx + 15"); y=${y%.*}
  assert_eq "key row $((r + 1)) is flat (31,31,31) across all $W px — no borders, gaps, separators or operator fill (2.6)" 0 \
    "$(scan "$D/std.png" "$y" 0 "$W" 31,31,31 2 | cut -d' ' -f1)"
done
y0=$(P "$KT + $rowpx + 3"); y0=${y0%.*}
for x in 5 272 538 812 1075; do
  assert_eq "no vertical separator or fill change at x=$x px from key row 2 to the nav bar (2.6)" 0 \
    "$(vscan "$D/std.png" "$x" "$y0" "$NT" 31,31,31 2 | cut -d' ' -f1)"
done
rgb_near "the = key's fill is (31,31,31), not accent (2.6)" 31,31,31 "$(px "$D/std.png" 1075 $((NT - 5)))" 2
for pair in mc:30 mr:90 mplus:150 mminus:210 ms:270 mlist:330; do
  assert_within "memory key ${pair%:*} centre at ${pair#*:} epx (W/6, 2.16)" "${pair#*:}" "$(cx_epx "$D/std.xml" "calc_key:${pair%:*}")" 0.5
done

# ---- 4. the pane (§3.1–3.12), opened over the Standard page showing 512
tap_node "$D/std.xml" calc_menu; sleep 0.9
dump_ui "$D/pane.xml"; screencap "$D/pane.png"
b4 "$D/pane.xml" calc_pane
assert_eq "pane at the left edge" 0 "$L"
assert_within "pane top = status bar bottom (3.2)" "$(epx $SB)" "$(epx $T)" 0.5
assert_within "pane 256 epx wide (3.1)" 256 "$(epx $((R - L)))" 0.5
assert_within "pane bottom = nav bar top (3.2)" "$(epx $NT)" "$(epx $B)" 0.5
prev=""
for rid in calc_mode:standard calc_mode:scientific calc_mode:programmer calc_mode:date calc_mode:converter calc_converter_category:1; do
  b4 "$D/pane.xml" "$rid"
  if [ -z "$prev" ]; then
    assert_within "first pane row starts at the header's bottom (3.5)" "$(epx $HB)" "$(epx $T)" 0.5
  else
    assert_within "$rid top = the previous row's bottom (48-epx pitch, 3.4)" "$(epx $prev)" "$(epx $T)" 0.5
  fi
  assert_within "$rid is 48 epx tall (3.4)" 48 "$(epx $((B - T)))" 0.5
  prev=$B
done
read -r ll lt lr lb <<< "$(child_text_bounds "$D/pane.xml" calc_mode:programmer)"
assert_within "pane label origin at x 60 epx (3.6: the label's text box)" 60 "$(epx "${ll:-0}")" 0.5
b4 "$D/pane.xml" calc_mode:programmer
read -r il it ir ib iw ih <<< "$(ink "$D/pane.png" 100 "$T" 700 "$B" 128)"
record "pane label ink left (Programmer), epx (3.6 measured 60.0–61.25)" "$(epx $il)"
record "pane label cap top below the row top (Programmer), epx (3.6: 19.0)" "$(P "($it-$T)/3.0")"
rgb_near "the selected row's fill = 0.6·accent + 0.4·43 (3.7)" "$SELECTED_FILL" "$(px "$D/pane.png" 600 $((HB + 72)))" 3
rgb_near "the selected row's fill, second sample" "$SELECTED_FILL" "$(px "$D/pane.png" 700 $((HB + 20)))" 3
rgb_near "pane fill (43,43,43) in the Scientific row (3.2)" 43,43,43 "$(px "$D/pane.png" 600 $((HB + 144 + 72)))" 2
rgb_near "pane fill (43,43,43) above the bottom rule" 43,43,43 "$(px "$D/pane.png" 700 $((NT - 144 - 20)))" 2
RT=$((NT - 144))
rule_rows=$(( 9 - $(vscan "$D/pane.png" 400 $((RT - 3)) $((RT + 6)) 64,64,64 2 | cut -d' ' -f1) ))
assert_within "a 1-epx (64,64,64) rule 48 epx above the nav bar (3.11: 3 px of 9 sampled)" 3 "$rule_rows" 1
rgb_near "the rule's top row is 48 epx above the nav bar (3.11)" 64,64,64 "$(px "$D/pane.png" 400 "$RT")" 2
rgb_near "the row above the rule is the pane fill" 43,43,43 "$(px "$D/pane.png" 400 $((RT - 1)))" 2
assert_eq "the rule spans x 12 → 244 epx (36..732 px) with no break (3.11)" 0 "$(scan "$D/pane.png" $((RT + 1)) 36 732 64,64,64 2 | cut -d' ' -f1)"
rgb_near "the rule is inset 12 epx on the left (x = 10 epx is pane fill)" 43,43,43 "$(px "$D/pane.png" 30 $((RT + 1)))" 2
rgb_near "the rule is inset 12 epx on the right (x = 246 epx is pane fill)" 43,43,43 "$(px "$D/pane.png" 738 $((RT + 1)))" 2
b4 "$D/pane.xml" calc_pane_settings
assert_within "Settings row under the rule, 48 epx above the nav bar (3.12)" "$(epx $RT)" "$(epx $T)" 0.5
assert_within "Settings row ends at the nav bar" "$(epx $NT)" "$(epx $B)" 0.5
read -r il it ir ib iw ih <<< "$(ink "$D/pane.png" 0 "$RT" 150 "$NT" 128)"
record "Settings gear ink: x ${il}-${ir} px = $(epx $il)–$(epx $((ir + 1))) epx, ${iw}x${ih} px (3.12: 20×20 at x 14–34)" "cx $(P "($il+$ir+1)/6.0") epx"
assert_within "the page behind the pane is not dimmed: the result digits stay white (3.3)" 255 \
  "$(python3 "$GEO" maxlum "$D/pane.png" 900 "${exprB%.*}" "$W" "${resB%.*}")" 2
y=$(P "$KT + 2*$rowpx + 15"); y=${y%.*}
rgb_near "the page behind the pane is not dimmed: the pad fill stays (31,31,31) (3.3)" 31,31,31 "$(px "$D/pane.png" 1000 "$y")" 2
adb shell input keyevent KEYCODE_BACK; sleep 0.6
dump_ui "$D/pane_closed.xml"
assert_eq "Back closes the pane" no "$(has_node "$D/pane_closed.xml" calc_pane)"

# the expression line (2.19, LOW): recorded
keys 5 add
dump_ui "$D/expr.xml"
b4 "$D/expr.xml" calc_expr
record "expression line text (2.19 LOW)" "$(node_text "$D/expr.xml" calc_expr)"
record "expression line right edge / bottom, epx (2.19 LOW: right-aligned above the result)" "right $(epx $R) bottom $(epx $B) (result row top $(epx "$exprB"))"
keys clear

# ---- 5. Scientific: the History glyph's presence only (6.1, gap 5; geometry UNMEASURED-2 → recorded)
python3 "$UI" goto scientific > "$D/goto.log" 2>&1
dump_ui "$D/sci.xml"
assert_eq "Scientific's header has the History glyph (1.7 as T15-42 re-cut it)" yes "$(has_node "$D/sci.xml" calc_history_toggle)"
b4 "$D/sci.xml" calc_key:angle
record "Scientific angle row (UNMEASURED-2)" "top $(epx $T) bottom $(epx $B) epx; DEG cell centre x $(cx_epx "$D/sci.xml" calc_key:angle)"
b4 "$D/sci.xml" calc_key:7
record "Scientific key row height / column (UNMEASURED-2)" "$(epx $((B - T))) epx tall; 7 at x $(cx_epx "$D/sci.xml" calc_key:7) (W/5 → 108)"

# ---- 6. Programmer: 20 : 72 : 96 : 32 : 268 on W/6, the word-size button in accent, no History glyph (§4)
python3 "$UI" goto programmer >> "$D/goto.log" 2>&1
dump_ui "$D/prog.xml"; screencap "$D/prog.png"
assert_eq "Programmer's header has no History glyph (4.15)" no "$(has_node "$D/prog.xml" calc_history_toggle)"
pres="$(P "$HB + 92.0*$CONTENT/488")"; pradix="$(P "$HB + 188.0*$CONTENT/488")"; ptabs="$(P "$HB + 220.0*$CONTENT/488")"
prow="$(P "268.0*$CONTENT/488/6")"; rrow="$(P "96.0*$CONTENT/488/4")"
b4 "$D/prog.xml" calc_key:radix_hex
assert_within "radix panel top = result row bottom (92/488, 4.1)" "$(epx "$pres")" "$(epx $T)" 1
assert_within "HEX row = a quarter of the 96* panel (4.3)" "$(epx "$rrow")" "$(epx $((B - T)))" 1
b4 "$D/prog.xml" calc_key:radix_bin
assert_within "radix panel bottom (188/488, 4.1)" "$(epx "$pradix")" "$(epx $B)" 1
b4 "$D/prog.xml" calc_prog_tab:keypad
assert_within "tab row top = radix panel bottom (4.1)" "$(epx "$pradix")" "$(epx $T)" 1
assert_within "tab row bottom (220/488, 4.1)" "$(epx "$ptabs")" "$(epx $B)" 1
b4 "$D/prog.xml" calc_keypad
assert_within "Programmer keypad top = tab row bottom (4.1)" "$(epx "$ptabs")" "$(epx $T)" 1
r=0
for k in lsh inv A C E lparen; do
  b4 "$D/prog.xml" "calc_key:$k"
  assert_within "Programmer key row $((r + 1)) top (6 rows of 268/488, 4.1)" "$(epx "$(P "$ptabs + $r*$prow")")" "$(epx $T)" 1
  r=$((r + 1))
done
b4 "$D/prog.xml" calc_key:equals
assert_within "Programmer key row 6 bottom = nav bar top" "$(epx $NT)" "$(epx $B)" 1
for pair in A:30 B:90 7:150 8:210 9:270 multiply:330; do
  assert_within "Programmer key ${pair%:*} centre at ${pair#*:} epx (W/6, 4.12)" "${pair#*:}" "$(cx_epx "$D/prog.xml" "calc_key:${pair%:*}")" 0.5
done
b4 "$D/prog.xml" calc_key:word
assert_within "word-size button centred at 180 epx over two W/6 cells (4.7)" 180 "$(P "($L+$R)/6.0")" 0.5
rgb_near "the word-size label is in accent (4.7)" "$ACCENT" "$(python3 "$GEO" inkcolor "$D/prog.png" "$L" "$T" "$R" "$B")" 4
for pair in keypad:30 bits:90; do
  assert_within "Programmer tab ${pair%:*} centre at ${pair#*:} epx (4.7)" "${pair#*:}" "$(cx_epx "$D/prog.xml" "calc_prog_tab:${pair%:*}")" 0.5
done
for pair in ms:270 mlist:330; do
  assert_within "Programmer ${pair%:*} centre at ${pair#*:} epx (4.7)" "${pair#*:}" "$(cx_epx "$D/prog.xml" "calc_key:${pair%:*}")" 0.5
done

# ---- 7. the Speed converter: 56 : 32 : 56 : 32 : Auto(50.2) : 272 and 0.25 : 1 : 1 : 1 : 0.25 columns (§5.1–5.2)
python3 "$UI" goto converter:7 >> "$D/goto.log" 2>&1
dump_ui "$D/speed.xml"; screencap "$D/speed.png"
assert_eq "the Speed page is titled SPEED (1.6)" SPEED "$(node_text "$D/speed.xml" calc_title)"
assert_eq "a converter page has no History glyph (1.7)" no "$(has_node "$D/speed.xml" calc_history_toggle)"
AUTO="$(P "50.2*3")"
cstar="$(P "($CONTENT - $AUTO)/448.0")"
v1b="$(P "$HB + 56*$cstar")"; u1b="$(P "$HB + 88*$cstar")"; v2b="$(P "$HB + 144*$cstar")"; u2b="$(P "$HB + 176*$cstar")"
padT="$(P "$u2b + $AUTO")"; crow="$(P "272*$cstar/5")"
note "converter star = $cstar px; predicted (px): value1→$v1b unit1→$u1b value2→$v2b unit2→$u2b about→$padT pad row $crow"
b4 "$D/speed.xml" calc_converter_about_row
assert_within "About-equal row top = unit2 row bottom (176/448 of content − Auto, 5.1)" "$(epx "$u2b")" "$(epx $T)" 1
assert_within "About-equal row 50.2 epx tall (Auto, 5.1)" 50.2 "$(epx $((B - T)))" 1
b4 "$D/speed.xml" calc_converter_pad
assert_within "converter pad top = About-equal row bottom (5.1–5.2)" "$(epx "$padT")" "$(epx $T)" 1
assert_within "converter pad bottom = nav bar top" "$(epx $NT)" "$(epx $B)" 1
r=0
for k in clear 7 4 1 0; do
  b4 "$D/speed.xml" "calc_key:$k"
  assert_within "converter key row $((r + 1)) top (5 rows of 272*, 5.2)" "$(epx "$(P "$padT + $r*$crow")")" "$(epx $T)" 1
  r=$((r + 1))
done
for pair in 7:77.143 8:180 9:282.857; do
  assert_within "converter key ${pair%:*} centre at ${pair#*:} epx (0.25:1:1:1:0.25, 5.2)" "${pair#*:}" "$(cx_epx "$D/speed.xml" "calc_key:${pair%:*}")" 0.5
done
b4 "$D/speed.xml" calc_key:7
assert_within "converter pad left margin 25.71 epx (0.25 of 3.5 columns, 5.2)" 25.714 "$(epx $L)" 0.5
b4 "$D/speed.xml" calc_converter_value1
assert_within "value1 bottom-aligned in its 56* row (5.5)" "$(epx "$v1b")" "$(epx $B)" 1
b4 "$D/speed.xml" calc_converter_unit1
assert_within "unit1 centred in its 32* row (5.1, 5.7)" "$(epx "$(P "($v1b+$u1b)/2")")" "$(P "($T+$B)/6.0")" 1
assert_within "unit label left 13.25 epx (5.7)" 13.25 "$(epx $L)" 0.5
b4 "$D/speed.xml" calc_converter_value2
assert_within "value2 bottom-aligned in its 56* row (5.6)" "$(epx "$v2b")" "$(epx $B)" 1
b4 "$D/speed.xml" calc_converter_unit2
assert_within "unit2 centred in its 32* row (5.1)" "$(epx "$(P "($v2b+$u2b)/2")")" "$(P "($T+$B)/6.0")" 1
b4 "$D/speed.xml" calc_converter_value1
assert_within "value left ≈ 12 epx (5.5)" 12 "$(epx $L)" 0.5

# ---- restore (RV12): the converter's last-used category back to Volume, Standard cleared, Home
python3 "$UI" goto converter:1 >> "$D/goto.log" 2>&1
python3 "$UI" goto standard >> "$D/goto.log" 2>&1
keys clear
adb shell input keyevent KEYCODE_HOME
row_end
