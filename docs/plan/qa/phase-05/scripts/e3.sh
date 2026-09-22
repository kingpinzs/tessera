#!/usr/bin/env bash
# E3 — the keyboard's geometry, labels, colours, press popup, cursor dot and strip, measured.
#
#   "Motion and geometry measure within the tolerance of each numbered R6 value in Decisions: key grid,
#    labels, popup and cursor dot from dump bounds and screencaps with phys values scaled by display
#    width / 1440; the suggestion strip in epx; panel and key colours within ± 32 per channel; popup
#    timing and show / hide from screenrecord frames ..."
#
# The motion half (popup timing, show / hide) is e3motion.sh. This is the static half.
#
# Tolerances: a value read from the keyboard's own dump bounds is checked against R6's tolerance as
# written. A value read off SCREEN PIXELS (ink heights, the grip, the dots, the strip text) gets R6's
# tolerance plus one device pixel — the capture's own quantum, the same allowance RV11 gives a frame —
# and the log says so on every such line.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/kb.sh"
M="$HERE/measure.py"

row_begin E3 "geometry, labels, colours, popup, dot and strip against R6 §2"
kb_begin

W="$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1/p' | tail -1)"
SX="$(python3 -c "print($W/1440)")"
EPX="$(python3 -c "print($W/360)")"
PXPH="$(python3 -c "print(1/$SX)")"   # one device pixel, in phys
PXEP="$(python3 -c "print(1/$EPX)")"  # one device pixel, in epx
log "panel ${W}px wide: 1 phys = $SX px, 1 epx = $EPX px; one pixel = $PXPH phys = $PXEP epx"

tol_px() { python3 -c "print(round($1 + $PXPH, 3))"; }   # R6 tolerance + one pixel (phys)
tol_ep() { python3 -c "print(round($1 + $PXEP, 3))"; }   # R6 tolerance + one pixel (epx)
ph() { python3 -c "print($1 / $SX)"; }
ep() { python3 -c "print($1 / $EPX)"; }

# ---- A. the key grid, from the dump (R6 §2.1.1–2.1.13, HIGH) --------------------------------------
open_field field_text
D="$ROW_DIR/e3_letters.xml"
kb_dump "$D"
screencap "$ROW_DIR/e3_letters.png"
python3 "$M" geom "$D" "$SX" > "$ROW_DIR/e3_geom.tsv"
g() { awk -F'\t' -v n="$1" '$1 == n { print $2 }' "$ROW_DIR/e3_geom.tsv"; }
for c in q w e r t y u i o p; do assert_within "R6 2.1.2 row 1 $c width 130" 130 "$(g "row1 $c width")" 3; done
for n in 0 1 2 3 4 5 6 7 8; do assert_within "R6 2.1.3 row 1 gap $n 14" 14 "$(g "row1 gap $n")" 3; done
assert_within "R6 2.1.4 row 1 left margin 5" 5 "$(g "row1 left margin")" 4
assert_within "R6 2.1.4 row 1 right margin 9" 9 "$(g "row1 right margin")" 4
assert_within "R6 2.1.1 column pitch = width / 10 = 144" 144 "$(g "column pitch")" 1.5
for c in a s d f g h j k l; do assert_within "R6 2.1.5 row 2 $c width 128" 128 "$(g "row2 $c width")" 3; done
for n in 0 1 2 3 4 5 6 7; do assert_within "R6 2.1.5 row 2 gap $n 16" 16 "$(g "row2 gap $n")" 3; done
assert_within "R6 2.1.6 row 2 left inset 77.5" 77.5 "$(g "row2 left inset")" 3
assert_within "R6 2.1.6 row 2 right inset 82" 82 "$(g "row2 right inset")" 3
assert_within "R6 2.1.7 shift 201" 201 "$(g "shift width")" 2
assert_within "R6 2.1.7 backspace 201" 201 "$(g "backspace width")" 2
for c in z x c v b n m; do assert_within "R6 2.1.8 row 3 $c width 128" 128 "$(g "row3 $c width")" 3; done
for pair in "z s" "x d" "c f" "v g" "b h" "n j" "m k"; do
  set -- $pair
  assert_within "R6 2.1.8 $1 on the $2 column" 0 "$(g "row3 $1 left vs $2 left")" 1.4
done
assert_within "R6 2.1.9 &123 201" 201 "$(g "row4 sym width")" 3
assert_within "R6 2.1.9 emoji 128" 128 "$(g "row4 emoji width")" 3
assert_within "R6 2.1.9 comma 128" 128 "$(g "row4 comma width")" 3
assert_within "R6 2.1.9 space 560" 560 "$(g "row4 space width")" 3
assert_within "R6 2.1.9 period 128" 128 "$(g "row4 period width")" 3
assert_within "R6 2.1.9 Enter 201" 201 "$(g "row4 enter width")" 3
assert_within "R6 2.1.10 key height (shortest) 202" 202 "$(g "key height min")" 3
assert_within "R6 2.1.10 key height (tallest) 202" 202 "$(g "key height max")" 3
assert_within "R6 2.1.11 row pitch 217.5" 217.5 "$(g "row pitch")" 1.5
assert_within "R6 2.1.11 vertical gap 15" 15 "$(g "vertical gap")" 3
assert_within "R6 2.1.12 bottom margin to the nav bar 7" 7 "$(g "bottom margin")" 4
assert_within "R6 2.1.13 key block 865" 865 "$(g "key block")" 5
assert_within "R6 2.2.1 strip height 46.5 epx" 46.5 "$(ep "$(g "strip height px")")" 1.3
assert_within "R6 2.5.4 dot centre 358 from the left" 358 "$(g "dot centre x")" 3
assert_within "R6 2.5.4 dot centre 218 above the nav bar" 218 "$(g "dot above nav bar")" 3

# ---- B. colours, off the screen (R6 §2.1.18, MEDIUM, ± 32 per channel) ---------------------------
P="$ROW_DIR/e3_letters.png"
read -r ql qt qr qb <<< "$(bounds "$D" kb_key_q)"
read -r wl wt wr wb <<< "$(bounds "$D" kb_key_w)"
read -r sl st sr sb <<< "$(bounds "$D" kb_key_shift)"
colour() { # name expected-r g b actual "r g b"
  local name="$1" er="$2" eg="$3" eb="$4"; shift 4
  set -- $1
  assert_within "$name R" "$er" "$1" 32; assert_within "$name G" "$eg" "$2" 32; assert_within "$name B" "$eb" "$3" 32
}
colour "R6 2.1.18 panel (22,27,21) in the q/w gap" 22 27 21 "$(python3 "$M" px "$P" $(( (qr + wl) / 2 )) $(( (qt + qb) / 2 )))"
colour "R6 2.1.18 letter key (48,48,48)" 48 48 48 "$(python3 "$M" px "$P" $((ql + 6)) $((qt + 6)))"
colour "R6 2.1.18 function key (73,74,72)" 73 74 72 "$(python3 "$M" px "$P" $((sl + 6)) $((st + 6)))"
qlab="$(python3 "$M" ink "$P" $((ql + 4)) $((qt + 4)) $((qr - 4)) $((qb - 4)) 223)"
assert_ne "R6 2.1.18 labels are white (a pixel >= 223 in q's label)" "none" "$qlab"

# ---- C. labels, off the screen (R6 §2.1.14–2.1.17) --------------------------------------------------
read -r xl xt xr xb <<< "$(bounds "$D" kb_key_x)"
xink="$(python3 "$M" ink "$P" $((xl + 3)) $((xt + 3)) $((xr - 3)) $((xb - 3)) 150)"
note "x label ink: $xink"
assert_within "R6 2.1.14 lowercase x-height 41 (+1 px)" 41 "$(ph "$(echo $xink | cut -d' ' -f5)")" "$(tol_px 2)"
xc="$(python3 -c "print(($xl+$xr)/2)")"; xinkc="$(echo $xink | awk '{print ($2+$4)/2}')"
assert_within "R6 2.1.14 label centred in its key (x)" 0 "$(ph "$(python3 -c "print($xinkc-$xc)")")" "$(tol_px 3)"
tap_key "$D" shift; sleep 0.8
screencap "$ROW_DIR/e3_shifted.png"
Xink="$(python3 "$M" ink "$ROW_DIR/e3_shifted.png" $((xl + 3)) $((xt + 3)) $((xr - 3)) $((xb - 3)) 150)"
note "X label ink: $Xink"
assert_within "R6 2.1.14 capital height 48 (+1 px)" 48 "$(ph "$(echo $Xink | cut -d' ' -f5)")" "$(tol_px 3)"
tap_key "$D" shift; sleep 0.5
read -r yl yt yr yb <<< "$(bounds "$D" kb_key_sym)"
band_top=$(( yt + (yb - yt) * 30 / 100 ))
sink="$(python3 "$M" ink "$P" $((yl + 3)) $band_top $((yr - 3)) $((yb - 3)) 150)"
note "&123 label ink: $sink"
assert_within "R6 2.1.15 &123 text width 112 (+1 px)" 112 "$(ph "$(echo $sink | cut -d' ' -f6)")" "$(tol_px 3)"
read -r st0 sl0 sb0 sr0 sh0 sw0 <<< "$sink"
digits="$(python3 "$M" ink "$P" $(( sl0 + sw0 * 35 / 100 )) $band_top $((sr0 + 1)) $((yb - 3)) 150)"
note "&123 digits (right 65 % of the label) ink: $digits"
assert_within "R6 2.1.15 &123 digit height 40 (+1 px)" 40 "$(ph "$(echo $digits | cut -d' ' -f5)")" "$(tol_px 2)"
dots="$(python3 "$M" segments "$P" $((yt + 8)) $((yt + 30)) $((yl + 2)) $((yl + 70)) 150 1)"
note "&123 hold dots (columns with ink, top-left band): $dots"
assert_eq "R6 2.1.17 &123 carries three hold dots" 3 "$(echo $dots | wc -w)"
d1="$(echo $dots | awk '{split($1,a,"-"); print (a[1]+a[2])/2}')"
d2="$(echo $dots | awk '{split($2,a,"-"); print (a[1]+a[2])/2}')"
assert_within "R6 2.1.17 first dot 20 from the key's left (+1 px)" 20 "$(ph "$(python3 -c "print($d1-$yl)")")" "$(tol_px 4)"
assert_within "R6 2.1.17 dot pitch 19 (+1 px)" 19 "$(ph "$(python3 -c "print($d2-$d1)")")" "$(tol_px 4)"
read -r pl pt pr pb <<< "$(bounds "$D" kb_key_space)"
grip="$(python3 "$M" grey "$P" $((pl + 3)) $((pt + 2)) $((pr - 3)) $((pt + 40)) 65 110)"
note "space grip (luminance 65-110) bbox: $grip"
assert_within "R6 2.1.16 grip width 59 (+1 px)" 59 "$(ph "$(echo $grip | cut -d' ' -f6)")" "$(tol_px 5)"
assert_within "R6 2.1.16 grip height 18 (+1 px)" 18 "$(ph "$(echo $grip | cut -d' ' -f5)")" "$(tol_px 5)"
assert_within "R6 2.1.16 grip top 20 below the key's top (+1 px)" 20 "$(ph "$(python3 -c "print($(echo $grip | cut -d' ' -f1)-$pt)")")" "$(tol_px 4)"
gc="$(echo $grip | awk '{print ($2+$4)/2}')"
assert_within "R6 2.1.16 grip centred on the space bar (+1 px)" 0 "$(ph "$(python3 -c "print($gc-($pl+$pr)/2)")")" "$(tol_px 5)"

# ---- D. the press popup (R6 §2.3), held with one input process while the dump is taken -------------
hold_and_capture() { # key-id tag
  local c
  c="$(node_center "$D" "kb_key_$1")"
  adb shell input swipe $c $c 6000 &
  local pid=$!
  sleep 2
  kb_dump "$ROW_DIR/e3_popup_$2.xml"
  screencap "$ROW_DIR/e3_popup_$2.png"
  wait $pid
}
hold_and_capture g g
PD="$ROW_DIR/e3_popup_g.xml"
read -r ul ut ur ub <<< "$(bounds "$PD" kb_popup)"
read -r gl gt gr gb <<< "$(bounds "$D" kb_key_g)"
note "popup over g: [$ul,$ut][$ur,$ub]; key g [$gl,$gt][$gr,$gb]; row 1 top $qt"
assert_within "R6 2.3.2 popup width 173" 173 "$(ph $((ur - ul)))" 4
assert_within "R6 2.3.2 popup height 233" 233 "$(ph $((ub - ut)))" 4
assert_within "R6 2.3.3 popup centred on the key" 0 "$(ph "$(python3 -c "print(($ul+$ur)/2-($gl+$gr)/2)")")" 2
assert_within "R6 2.3.3 popup bottom 7 above the key's top" 7 "$(ph $((gt - ub)))" 4
assert_within "R6 2.3.3 popup top 25 above the row above's top" 25 "$(ph $((qt - ut)))" 4
pacc="$(python3 "$M" px "$ROW_DIR/e3_popup_g.png" $((ul + 5)) $((ut + 5)))"
note "popup fill pixel: $pacc (the configured accent is 0,120,215)"
colour "R6 2.3.1 popup is accent-filled" 0 120 215 "$pacc"
kacc="$(python3 "$M" px "$ROW_DIR/e3_popup_g.png" $((gl + 5)) $((gt + 5)))"
colour "R6 2.3.1 the pressed key fills with the accent" 0 120 215 "$kacc"
pink="$(python3 "$M" ink "$ROW_DIR/e3_popup_g.png" $((ul + 2)) $((ut + 2)) $((ur - 2)) $((ub - 2)) 200)"
note "popup glyph (g) ink: $pink"
hold_and_capture x x
read -r ul2 ut2 ur2 ub2 <<< "$(bounds "$ROW_DIR/e3_popup_x.xml" kb_popup)"
xpink="$(python3 "$M" ink "$ROW_DIR/e3_popup_x.png" $((ul2 + 2)) $((ut2 + 2)) $((ur2 - 2)) $((ub2 - 2)) 200)"
note "popup glyph (x) ink: $xpink"
assert_within "R6 2.3.4 popup glyph x-height 59.5 (+1 px)" 59.5 "$(ph "$(echo $xpink | cut -d' ' -f5)")" "$(tol_px 5)"
hold_and_capture r r  # r, not e: e has alternates, and a hold would open them
read -r el et er eb <<< "$(bounds "$ROW_DIR/e3_popup_r.xml" kb_popup)"
read -r ktl ktt ktr ktb <<< "$(bounds "$D" kb_strip)"
# A node's accessibility bounds are clipped to its WINDOW, and the IME window reported to accessibility
# ends at the panel's top — so a row-1 popup's top is read off the screen, from its accent pixels (run 1
# recorded the clipped node top, 1419 = the strip's top, as "does not rise").
racc="$(python3 "$M" accent "$ROW_DIR/e3_popup_r.png" "$el" $((ktt - 200)) "$er" "$eb")"
note "row-1 popup over r: node [$el,$et][$er,$eb]; accent pixels $racc; strip top $ktt"
et="$(echo $racc | cut -d' ' -f1)"
assert_within "R6 2.3.2 row-1 popup height 233 on screen (+1 px)" 233 "$(ph "$(echo $racc | cut -d' ' -f5)")" "$(tol_px 4)"
assert_eq "R6 2.3.3 a row-1 popup rises above the panel" "yes" "$([ "$et" -lt "$ktt" ] && echo yes || echo no)"
assert_eq "R6 2.3.3 a row-1 popup covers the strip" "yes" "$([ "$eb" -gt "$ktt" ] && echo yes || echo no)"
hold_and_capture bksp bksp
assert_eq "R6 2.3.5 a function key shows no popup" "no" "$(has_node "$ROW_DIR/e3_popup_bksp.xml" kb_popup)"
fill="$(python3 "$M" px "$ROW_DIR/e3_popup_bksp.png" $(( $(bounds "$D" kb_key_bksp | cut -d' ' -f1) + 5 )) $(( $(bounds "$D" kb_key_bksp | cut -d' ' -f2) + 5 )))"
colour "R6 2.3.5 a function key only fills with the accent" 0 120 215 "$fill"
open_field field_text  # the held backspace emptied nothing that matters; start the strip clean

# ---- E. the strip (R6 §2.2, epx) --------------------------------------------------------------------
D2="$ROW_DIR/e3_strip.xml"
kb_dump "$D2"
tap_key "$D2" shift; tap_key "$D2" i
sleep 1.2
kb_dump "$D2"
screencap "$ROW_DIR/e3_strip.png"
note "strip after typing I: $(ime_dump | sed -n 's/^ *strip=//p' | tr -d '\r')"
read -r s0l s0t s0r s0b <<< "$(bounds "$D2" kb_sugg_0)"
cap="$(python3 "$M" ink "$ROW_DIR/e3_strip.png" "$s0l" "$s0t" "$s0r" "$s0b" 150)"
note "first strip item ink: $cap (item: $(node_text "$D2" kb_sugg_0))"
assert_within "R6 2.2.3 strip cap height 13.3 epx (+1 px)" 13.3 "$(ep "$(echo $cap | cut -d' ' -f5)")" "$(tol_ep 1)"
read -r ktl ktt ktr ktb <<< "$(bounds "$D2" kb_strip)"
capc="$(echo $cap | awk '{print ($1+$3)/2}')"
assert_within "R6 2.2.3 strip text vertically centred" 0 "$(ep "$(python3 -c "print($capc-($ktt+$ktb)/2)")")" "$(tol_ep 1)"
segs="$(python3 "$M" segments "$ROW_DIR/e3_strip.png" "$ktt" "$ktb" 0 "$W" 150 15)"
note "strip ink runs (mic, then words): $segs"
mic="$(echo $segs | cut -d' ' -f1)"; w1="$(echo $segs | cut -d' ' -f2)"; w2="$(echo $segs | cut -d' ' -f3)"
m0=${mic%-*}; m1=${mic#*-}; a0=${w1%-*}; a1=${w1#*-}; b0=${w2%-*}
assert_within "R6 2.2.5 microphone 13 epx from the left edge (+1 px)" 13 "$(ep "$m0")" "$(tol_ep 1.4)"
assert_within "R6 2.2.5 microphone ink 13.8 epx wide (+1 px)" 13.8 "$(ep $((m1 - m0 + 1)))" "$(tol_ep 1.4)"
micink="$(python3 "$M" ink "$ROW_DIR/e3_strip.png" "$m0" "$ktt" $((m1 + 1)) "$ktb" 150)"
assert_within "R6 2.2.5 microphone ink 20.3 epx tall (+1 px)" 20.3 "$(ep "$(echo $micink | cut -d' ' -f5)")" "$(tol_ep 1.4)"
assert_within "R6 2.2.5 first word 28.6 epx right of the microphone (+1 px)" 28.6 "$(ep $((a0 - m1 - 1)))" "$(tol_ep 1.4)"
assert_within "R6 2.2.4 26 epx between suggestions (+1 px)" 26 "$(ep $((b0 - a1 - 1)))" "$(tol_ep 1.5)"
stripcol="$(python3 "$M" px "$ROW_DIR/e3_strip.png" $((W - 4)) $(( (ktt + ktb) / 2 )))"
colour "R6 2.2.2 the strip is the panel's colour" 22 27 21 "$stripcol"
tap_key "$D2" bksp

# ---- F. URL and search rows (R6 §2.8.2, §2.8.3, MEDIUM) --------------------------------------------
open_field field_url
DU="$ROW_DIR/e3_url.xml"; kb_dump "$DU"; screencap "$ROW_DIR/e3_url.png"
python3 "$M" geom "$DU" "$SX" > "$ROW_DIR/e3_geom_url.tsv"
gu() { awk -F'\t' -v n="$1" '$1 == n { print $2 }' "$ROW_DIR/e3_geom_url.tsv"; }
assert_within "R6 2.8.3 .com 201" 201 "$(gu "row4 dotcom width")" 3
assert_within "R6 2.8.3 space 418.6" 418.6 "$(gu "row4 space width")" 3
assert_within "R6 2.8.3 period 201" 201 "$(gu "row4 period width")" 3
read -r nl nt nr nb <<< "$(bounds "$DU" kb_key_enter)"
colour "R6 2.8.3 URL action key is white-filled" 254 255 253 "$(python3 "$M" px "$ROW_DIR/e3_url.png" $((nl + 5)) $((nt + 5)))"
read -r cl ct cr cb <<< "$(bounds "$DU" kb_key_dotcom)"
com="$(python3 "$M" ink "$ROW_DIR/e3_url.png" $((cl + 3)) $((ct + 3)) $((cr - 3)) $((cb - 3)) 150)"
cink="$(python3 "$M" ink "$ROW_DIR/e3_url.png" $(( $(echo $com | cut -d' ' -f2) + 2 )) $((ct + 3)) $(( $(echo $com | cut -d' ' -f2) + 12 )) $((cb - 3)) 150)"
note ".com label ink $com; the 'c' after the dot: $cink"
assert_within "R6 2.8.3 .com label x-height 28 (+1 px)" 28 "$(ph "$(echo $com | cut -d' ' -f5)")" "$(tol_px 2)"
open_field field_search
DS="$ROW_DIR/e3_search.xml"; kb_dump "$DS"; screencap "$ROW_DIR/e3_search.png"
read -r nl nt nr nb <<< "$(bounds "$DS" kb_key_enter)"
colour "R6 2.8.2 search action key is white-filled" 254 255 253 "$(python3 "$M" px "$ROW_DIR/e3_search.png" $((nl + 5)) $((nt + 5)))"
mag="$(python3 "$M" grey "$ROW_DIR/e3_search.png" $((nl + 5)) $((nt + 5)) $((nr - 5)) $((nb - 5)) 0 90)"
note "dark glyph pixels in the search key: $mag"
assert_ne "R6 2.8.2 the search key carries a dark magnifier" "none" "$mag"
assert_within "R6 2.8.2 search action key 201 wide" 201 "$(ph $((nr - nl)))" 3

kb_end
row_end
