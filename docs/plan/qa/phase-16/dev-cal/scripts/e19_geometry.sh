#!/usr/bin/env bash
# Development check of E19's values on the AVD (360 epx wide): each r11/calendar.md value the views are built to, read
# from the dump's bounds (epx = px / 3) or a screenshot's pixel, relative to the drawn status bar's bottom where R11
# measured under a 24-epx bar. Text nodes are read by their BOX; a value R11 gives as a cap top is asserted through
# the box top + the font's cap offset (CapMetrics: 0.2896 × size). Also the two motion lines. NOT a gate row.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/cal.sh"
session_begin E19_GEOMETRY "the views' measured values, and the two motions"
G="$HERE/geo.py"
b() { python3 "$G" bounds "$1" "$2" "${3:-0}"; }          # dump id [n] -> l t r b (epx)
tb() { python3 "$G" textbounds "$1" "$2" "${3:-0}"; }     # dump text [n]
px() { python3 "$G" pixel "$1" "$2" "$3"; }               # png x y -> r g b
f() { echo "$1" | awk -v i="$2" '{print $i}'; }           # field of "l t r b"
near() { # name expected actual tol
  assert_within "$1" "$2" "$3" "$4"
}
rgb_near() { # name "r g b" "r g b" tol
  python3 - "$2" "$3" "$4" <<'PY' | { read -r v; if [ "$v" = ok ]; then _verdict PASS "$1" "$3 ~ $2 +/- $4"; else _verdict FAIL "$1" "expected ($2) +/- $4, got ($3)"; fi; }
import sys
e = [int(x) for x in sys.argv[1].split()]; a = [int(x) for x in sys.argv[2].split()] if sys.argv[2].strip() else []
print("ok" if len(a) == 3 and all(abs(x - y) <= int(sys.argv[3]) for x, y in zip(e, a)) else "no")
PY
}
c6
open_cal -a android.intent.action.MAIN; sleep 2
TESS="$(tessera_id)"; TODAY="$(device_date 0)"
A1="$(mkevent "$TESS" 'Geo all day 1' "$(utc_day_ms 0)" "$(utc_day_ms 1)" --bind allDay:i:1 --bind eventTimezone:s:UTC)"
A2="$(mkevent "$TESS" 'Geo all day 2' "$(utc_day_ms 0)" "$(utc_day_ms 1)" --bind allDay:i:1 --bind eventTimezone:s:UTC)"
T1="$(mkevent "$TESS" 'Geo timed 1' "$(day_ms 0 13:00)" "$(day_ms 0 14:00)")"
T2="$(mkevent "$TESS" 'Geo timed 2' "$(day_ms 0 15:00)" "$(day_ms 0 16:00)")"
sleep 2
D="$ROW_DIR/agenda.xml"; P="$ROW_DIR/agenda.png"; dump_ui "$D"; screencap "$P"
SB="$(f "$(b "$D" w10m_status_bar)" 4)"; NAVT="$(f "$(b "$D" w10m_nav_bar)" 2)"
record "the drawn status bar's bottom and the nav bar's top (epx)" "$SB / $NAVT"

# ---- header (K1.1–K1.3)
HB="$(b "$D" cal_header_band)"
near "K1.2 the header band is 40 epx tall" 40 "$(python3 -c "print($(f "$HB" 4) - $(f "$HB" 2))")" 1
near "K1.2 and starts at the status bar's bottom" "$SB" "$(f "$HB" 2)" 0.5
MG="$(b "$D" cal_menu_glyph)"
near "K1.1 the menu bars start at x 16" 16 "$(f "$MG" 1)" 1
near "K1.1 and end at x 36" 36 "$(f "$MG" 3)" 1
TT="$(b "$D" cal_month_title)"
near "K1.1 the month and year: left 51.0" 51 "$(f "$TT" 1)" 1
near "K1.1 its cap top 15.5 below the status bar (box top + 0.2896 x 15.7)" 15.5 "$(python3 -c "print($(f "$TT" 2) - $SB + 0.2896 * 15.7)")" 1
rgb_near "K1.3 the page is (26,26,26)" "26 26 26" "$(px "$P" 300 500)" 2
rgb_near "K1.3 the status bar is black" "0 0 0" "$(px "$P" 180 4)" 2
rgb_near "K1.3 the nav bar is black" "0 0 0" "$(px "$P" 60 $(python3 -c "print($NAVT + 6)"))" 2

# ---- week strip (K2)
i=0; START="$(python3 -c "import datetime,sys; d=datetime.date.fromisoformat('$TODAY'); print(d - datetime.timedelta(days=(d.isoweekday() % 7)))")"
for want in 25.0 76.6 127.5 178.4 229.0 280.4 331.3; do
  DAY="$(python3 -c "import datetime; print(datetime.date.fromisoformat('$START') + datetime.timedelta(days=$i))")"
  C="$(b "$D" "cal_strip_day:$DAY")"
  near "K2.1 strip column $i centre $want" "$want" "$(python3 -c "print(($(f "$C" 1) + $(f "$C" 3)) / 2)")" 1
  i=$((i + 1))
done
record "the strip's first day (the locale's week start on this AVD)" "$START"
SEL="$(b "$D" cal_strip_selected)"
near "K2.5 the selected day's square is 32 epx wide" 32 "$(python3 -c "print($(f "$SEL" 3) - $(f "$SEL" 1))")" 2
near "K2.5 and 32 epx tall" 32 "$(python3 -c "print($(f "$SEL" 4) - $(f "$SEL" 2))")" 2
assert_eq "K2.4 two week rows: 14 strip days" 14 "$(count_prefix "$D" cal_strip_day:)"
N1="$(tb "$D" Sun)"; [ -n "$N1" ] || N1="$(tb "$D" Mon)"
near "K2.2 day names' cap top 51.3 below the status bar" 51.3 "$(python3 -c "print($(f "$N1" 2) - $SB + 0.2896 * 15)")" 1
rgb_near "K2.2 day names are (151,151,151): the ink's brightest pixel" "151 151 151" "$(python3 - "$P" "$N1" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); l, t, r, b = [int(float(v) * 3) for v in sys.argv[2].split()]
print(" ".join(str(c) for c in max((im.getpixel((x, y)) for x in range(l, r) for y in range(t, b)), key=sum)))
PY
)" 4

# ---- Agenda rows (K3)
H="$(b "$D" "cal_day:$TODAY")"
near "K3.2 the day heading's left 24" 24 "$(f "$H" 1)" 1
B1="$(b "$D" "cal_event_bar:$A1")"; B2="$(b "$D" "cal_event_bar:$A2")"; B3="$(b "$D" "cal_event_bar:$T1")"; B4="$(b "$D" "cal_event_bar:$T2")"
near "K3.8 the colour bar is flush left" 0 "$(f "$B1" 1)" 0.5
near "K3.8 and 8 epx wide" 8 "$(f "$B1" 3)" 1
near "K3.5 an all-day bar is 40 epx" 40 "$(python3 -c "print($(f "$B1" 4) - $(f "$B1" 2))")" 1
near "K3.5 on a 44-epx pitch" 44 "$(python3 -c "print($(f "$B2" 2) - $(f "$B1" 2))")" 1
near "K3.6 a timed bar is 56 epx" 56 "$(python3 -c "print($(f "$B3" 4) - $(f "$B3" 2))")" 1
record "the timed rows' pitch (R11: 59.5 on the 432 canvas)" "$(python3 -c "print($(f "$B4" 2) - $(f "$B3" 2))")"
rgb_near "K3.8 the bar is the calendar's colour (#0063B1)" "0 99 177" "$(px "$P" 4 "$(python3 -c "print(($(f "$B1" 2) + $(f "$B1" 4)) / 2)")")" 4
near "K3.7 the title's left 92.5" 92.5 "$(f "$(b "$D" "cal_event_title:$T1")" 1)" 1
near "K3.7 the timed label's left 24.3" 24.3 "$(f "$(b "$D" "cal_event_time:$T1")" 1)" 1
near "K3.5 the All day label's left 24.3" 24.3 "$(f "$(tb "$D" 'All day')" 1)" 1

# ---- the app bar (K1.5–K1.6)
AB="$(b "$D" cal_app_bar)"
near "K1.5 the app bar is 47 epx" 47 "$(python3 -c "print($(f "$AB" 4) - $(f "$AB" 2))")" 1
near "K1.5 and ends at the nav bar's top" "$NAVT" "$(f "$AB" 4)" 0.5
rgb_near "K1.5 its fill is (33,33,33)" "33 33 33" "$(px "$P" 40 "$(python3 -c "print($(f "$AB" 2) + 20)")")" 4
rgb_near "K1.5 its top edge is (80,80,80)" "80 80 80" "$(px "$P" 40 "$(python3 -c "print($(f "$AB" 2) + 0.17)")")" 4
for pair in "today 217.9" "new 150.2" "view 82.6" "more 24.5"; do
  set -- $pair; BB="$(b "$D" "cal_bar:$1")"
  near "K1.6 $1's centre $2 epx from the right edge" "$2" "$(python3 -c "print(360 - ($(f "$BB" 1) + $(f "$BB" 3)) / 2)")" 1
done

# ---- the month drop-down (K5) and its motion
MARK="$(ring_mark)"
tap cal_header 1.5; D="$ROW_DIR/month.xml"; P="$ROW_DIR/month.png"; dump_ui "$D"; screencap "$P"
MD="$(b "$D" cal_month_dropdown)"
near "K5.1 the panel's left 5" 5 "$(f "$MD" 1)" 1
near "K5.1 its right 355" 355 "$(f "$MD" 3)" 1
near "K5.1 its top at the header's bottom" "$(python3 -c "print($SB + 40)")" "$(f "$MD" 2)" 1
near "K5.1 235 epx tall" 235 "$(python3 -c "print($(f "$MD" 4) - $(f "$MD" 2))")" 1
rgb_near "K5.1 a (80,80,80) border" "80 80 80" "$(px "$P" 180 "$(python3 -c "print($(f "$MD" 2) + 0.17)")")" 4
FIRST="$(grep -o 'resource-id="cal_month_cell:[0-9-]*"' "$D" | head -1 | sed 's/.*cell://; s/"//')"
C0="$(b "$D" "cal_month_cell:$FIRST")"; C7="$(b "$D" "cal_month_cell:$(python3 -c "import datetime; print(datetime.date.fromisoformat('$FIRST') + datetime.timedelta(days=7))")")"
C35="$(b "$D" "cal_month_cell:$(python3 -c "import datetime; print(datetime.date.fromisoformat('$FIRST') + datetime.timedelta(days=35))")")"
near "K5.2 the date rows' pitch 34.25" 34.25 "$(python3 -c "print($(f "$C7" 2) - $(f "$C0" 2))")" 1
near "K5.2 six rows: the sixth 171.25 below the first" 171.25 "$(python3 -c "print($(f "$C35" 2) - $(f "$C0" 2))")" 1
near "K5.2 on the strip's columns: the first centre 25.0" 25.0 "$(python3 -c "print(($(f "$C0" 1) + $(f "$C0" 3)) / 2)")" 1
M="$(line_of "$(ring_since "$MARK")" '[motion] cal_month_dropdown')"; log "$M"
near "U8 the drop-down settles in 200 ms" 200 "$(echo "$M" | sed -n 's/.*settle=\([0-9.]*\).*/\1/p')" 17
near "C-31 with no frame gap over 33.4 ms" 16.7 "$(echo "$M" | sed -n 's/.*maxGapMs=\([0-9.]*\).*/\1/p')" 16.7
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- the pane (K6.4)
tap cal_menu 1.5; D="$ROW_DIR/pane.xml"; P="$ROW_DIR/pane.png"; dump_ui "$D"; screencap "$P"
ACC="$(b "$D" cal_account:Tessera)"; ROWP="$(b "$D" "cal_calendar_row:$TESS")"
near "K6.4 the rows' pitch 48.1" 48.1 "$(python3 -c "print($(f "$ROWP" 2) - $(f "$ACC" 2))")" 1
near "K6.4 the calendar's name at x 62" 62 "$(f "$(tb "$D" Tessera 1)" 1)" 1
rgb_near "U10 the pane's chrome #1F1F1F" "31 31 31" "$(px "$P" 300 "$(python3 -c "print($(f "$ROWP" 4) + 60)")")" 2
rgb_near "K6.4 a checked box is filled with the calendar's colour" "0 99 177" "$(px "$P" 25 "$(python3 -c "print(($(f "$ROWP" 2) + $(f "$ROWP" 4)) / 2 - 7)")")" 4
adb shell input keyevent KEYCODE_BACK; sleep 1

# ---- the Week view (K4)
tap cal_bar:view; tap cal_view_pick:week 2; D="$ROW_DIR/week.xml"; P="$ROW_DIR/week.png"; dump_ui "$D"; screencap "$P"
W0="$(b "$D" "cal_day:$START")"; W1="$(b "$D" "cal_day:$(python3 -c "import datetime; print(datetime.date.fromisoformat('$START') + datetime.timedelta(days=1))")")"
W2="$(b "$D" "cal_day:$(python3 -c "import datetime; print(datetime.date.fromisoformat('$START') + datetime.timedelta(days=2))")")"
near "K4.2 the first cell's label at x 10" 10 "$(f "$W0" 1)" 1
near "K4.2 the second cell's label at x 190 (W/2 + 10)" 190 "$(f "$W1" 1)" 1
near "K4.1 the rows are 120 epx apart" 120 "$(python3 -c "print($(f "$W2" 2) - $(f "$W0" 2))")" 1
near "K4.2 the label's cap top 11 under the cell's top rule (the header's bottom + 1)" 11 "$(python3 -c "print($(f "$W0" 2) + 0.2896 * 18.6 - ($SB + 41))")" 1
L1="$(b "$D" "cal_event_title:$A1")"; L2="$(b "$D" "cal_event_title:$A2")"
near "K4.3 event lines at a 19-epx pitch" 19 "$(python3 -c "print($(f "$L2" 2) - $(f "$L1" 2))")" 1
assert_eq "K4.4 the mini month is in the eighth cell" yes "$(has_node "$D" cal_mini_month)"
near "K4.4 at x 180 (the right column)" 180 "$(f "$(b "$D" cal_mini_month)" 1)" 1

# ---- Day paging's motion (U8 → X13)
tap cal_bar:view; tap cal_view_pick:day 2
MARK="$(ring_mark)"
adb shell input swipe 900 1200 200 1200 250; sleep 2
M="$(line_of "$(ring_since "$MARK")" '[motion] cal_day_page')"; log "$M"
near "U8 a day page settles in 250 ms" 250 "$(echo "$M" | sed -n 's/.*settle=\([0-9.]*\).*/\1/p')" 17
near "C-31 with no frame gap over 33.4 ms" 16.7 "$(echo "$M" | sed -n 's/.*maxGapMs=\([0-9.]*\).*/\1/p')" 16.7
dump_ui "$ROW_DIR/day_next.xml"
assert_eq "the swipe paged to tomorrow" yes "$(has_node "$ROW_DIR/day_next.xml" "cal_day:$(device_date 1)")"

# ---- the editor's picker fields (U3: People's 32-epx box)
tap cal_bar:new 2; D="$ROW_DIR/editor.xml"; P="$ROW_DIR/editor.png"; dump_ui "$D"; screencap "$P"
SD="$(b "$D" cal_editor_field:start_date)"
near "U3 a field is 32 epx tall" 32 "$(python3 -c "print($(f "$SD" 4) - $(f "$SD" 2))")" 1
near "U3 from x 12" 12 "$(f "$SD" 1)" 1
rgb_near "U3 with a (133,133,133) border" "133 133 133" "$(px "$P" 60 "$(python3 -c "print($(f "$SD" 2) + 0.5)")")" 4
record "cal_editor_field:title's node height in the dump (the text field's accessibility bounds)" "$(python3 -c "t='$(b "$D" cal_editor_field:title)'.split(); print(float(t[3]) - float(t[1]))")"
TI="$(b "$D" cal_editor_field:title)"
rgb_near "U3 the title box's border, by pixel, 16 epx above the field's centre (a 32-epx box)" "133 133 133" "$(px "$P" 180 "$(python3 -c "print(($(f "$TI" 2) + $(f "$TI" 4)) / 2 - 15.5)")")" 4
adb shell input keyevent KEYCODE_BACK; sleep 1
assert_eq "the system bars are not visible on the Calendar's window" "false" "$(S dumpsys window | grep -m1 -oE 'statusBars.*?visible=(true|false)' | sed 's/.*visible=//' | head -1)"

# ---- restore
purge_tessera_events "title LIKE 'Geo %'"
assert_eq "the fixtures are gone" 0 "$(event_count "title LIKE 'Geo %'")"
c6; ensure_start
session_end
