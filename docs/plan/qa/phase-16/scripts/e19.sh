#!/usr/bin/env bash
# Phase 16 E19 — Calendar geometry ([fidelity] H1 for Agenda, Week, the drop-down and the pane; r11/calendar.md's values,
# ± 1 epx unless stated, relative to the drawn status bar's bottom where R11 measured under a 24-epx bar; T16-13).
#
# How it measures (scripts/cal_geo.py): every clause that names something DRAWN — a bar, a rule, a border, a box, a
# colour, a cap, the edge of a line of text or of a glyph — is read from the screencap's pixels; the dump only says
# where to look, and gives the layout boxes that draw nothing of their own (the header band, the bars' nodes, a row's
# pitch). R11's positions are half-level crossings of the INK, so a text's "x" here is its ink's left edge, not its
# text box's; and because a glyph's side bearing moves that edge by up to an epx, the fixtures use R11's own sample
# text where R11 names it — the titles "Pay rent" and "Ride a bike" and a Saturday heading (C1 083), a week whose labels
# start with "2" ("23 MON", C2 311), the calendar names "MoNa Events" and "mark guim" (WCS) — and the assertion's name
# says which text was measured. A 1-epx or 2-epx line is held to one device pixel (± 0.34 epx), tighter than the row's
# ± 1, which would pass a line of nothing.
#
#   1  Agenda, today      the bars, the header (≡, the month and year, ⌄, the 40-epx band), the page and bar colours,
#                         the week strip (W/7 centres, day names, two rows, the second dimmed, the 32-epx accent square),
#                         today's heading in accent, "No events today", the app bar
#   2  the drop-down      x 5–355, 235 epx down from the header, its border, six rows at 34.25 on the strip's columns,
#                         other-month dates, ⌃ while open; its [motion] line 200 ± 17 ms, maxGapMs ≤ 33.4
#   3  Agenda, rows       a Saturday's heading, the 8-epx bar at x 0, the label at x 24.3, the title at x 92.5, all-day
#                         bars 40 on a 44 pitch, timed bars 56
#   4  the ≡ pane         rows at 48.1, account headers at x 14, names at x 62, the checked box's fill, #1F1F1F
#   5  Week               2 × 4 cells of 120 split at W/2, "23 MON" labels at x 10, lines at a 19 pitch, the mini month
#   6  Day                U1's structure (the label, the all-day band, 48-epx hours); a day page's [motion] line
#                         250 ± 17 ms, maxGapMs ≤ 33.4
#   7  the event page     U2's structure
#   8  the editor         fields 32 ± 1 epx with a 2-epx (133,133,133) border, by pixel
#   the drawn bars (BarMetrics.STATUS_EPX / NAV_EPX, read from the source, never a literal) and `dumpsys window`'s
#   system bars not visible, on every view and the editor
#
# E19_LEGS=1 runs leg 1 alone (Agenda on today: the capture that draws today's empty day) with the row's own fixtures —
# a narrow re-run after a failed one, as the owner's ruling of 2026-10-01 allows; the log's first RECORD says so, and
# legs 2–8 then stand on the row's earlier run.
# E19_LEGS=2,3 runs legs 2 and 3 alone (the month drop-down with its motion, then the Agenda's rows on the fixtures' day,
# reached as leg 2 reaches it) — for a build that changed how the Agenda's rows are stacked (gate review B, blocking 1).
set -uo pipefail
LEGS="${E19_LEGS:-all}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
GEO="$HERE/cal_geo.py"

row_begin E19 "Calendar geometry on the drawn pixels: Agenda, the month drop-down, the pane, Week, Day, the editor; two motions"
if [ "$LEGS" != all ]; then
  case "$LEGS" in
    1) record "legs run" "1 ONLY (Agenda on today: the chrome, the strip, today's heading, the empty day's line) — a narrow re-run; legs 2–8 stand on the row's earlier run" ;;
    2,3) record "legs run" "2 and 3 ONLY (the month drop-down and its motion; the Agenda's rows on the fixtures' day: the heading, the bars, the label, the title, the pitch) — a narrow re-run; legs 1 and 4–8 stand on the row's earlier runs" ;;
    1,2,3) record "legs run" "1, 2 and 3 ONLY (Agenda on today with the empty day's line; the month drop-down and its motion; the Agenda's rows on the fixtures' day) — a narrow re-run; legs 4–8 stand on the row's earlier run" ;;
    *) _verdict FAIL "E19_LEGS" "E19_LEGS is 1, 2,3 or 1,2,3 (got $LEGS)"; row_end; exit 1 ;;
  esac
fi
# A colour within a tolerance per channel.
crgb() { # name "r g b" "r g b" tol
  local v
  v="$(python3 -c '
import sys
e = [int(x) for x in sys.argv[1].split()] if sys.argv[1].strip() else []
a = [int(x) for x in sys.argv[2].split()] if sys.argv[2].strip() else []
print("ok" if len(e) == 3 and len(a) == 3 and all(abs(x - y) <= int(sys.argv[3]) for x, y in zip(e, a)) else "no")' "$2" "$3" "$4")"
  if [ "$v" = ok ]; then _verdict PASS "$1" "($3) ~ ($2) +/- $4"; else _verdict FAIL "$1" "expected ($2) +/- $4, got ($3)"; fi
}
# One capture measured: every line cal_geo.py prints becomes an assertion or a record. Its table is kept beside the capture.
geo() { # label view [key=value …]  (the capture is $ROW_DIR/<label>.xml / .png)
  local label="$1" view="$2"; shift 2
  local kind name a b c n=0
  python3 "$GEO" "$view" "$ROW_DIR/$label.xml" "$ROW_DIR/$label.png" "$@" > "$ROW_DIR/$label.geo.tsv" 2> "$ROW_DIR/$label.geo.err"
  if [ -s "$ROW_DIR/$label.geo.err" ]; then _verdict FAIL "$label: the measurement ran" "$(tail -1 "$ROW_DIR/$label.geo.err" | cut -c1-200)"; fi
  while IFS=$'\t' read -r kind name a b c; do
    n=$((n + 1))
    case "$kind" in
      W) assert_within "$name" "$a" "$b" "$c" ;;
      C) crgb "$name" "$a" "$b" "$c" ;;
      E) assert_eq "$name" "$a" "$b" ;;
      R) record "$name" "$a" ;;
    esac
  done < "$ROW_DIR/$label.geo.tsv"
  [ "$n" -gt 0 ] || _verdict FAIL "$label: the measurement printed nothing" "see $label.geo.err"
}
shot() { dump_ui "$ROW_DIR/$1.xml"; screencap "$ROW_DIR/$1.png"; }
# `dumpsys window`: the system's own bars are not visible over the view (phase 01 E19's form).
sysbars() { # view
  local w; w="$(adb shell dumpsys window < /dev/null | tr -d '\r' | grep -E 'InsetsSource .*type=(statusBars|navigationBars)' | head -2 | tr -s ' ')"
  assert_eq "$1: dumpsys window — the system status bar is not visible" "visible=false" "$(echo "$w" | grep 'type=statusBars' | grep -o 'visible=[a-z]*' | head -1)"
  assert_eq "$1: dumpsys window — the system nav bar is not visible" "visible=false" "$(echo "$w" | grep 'type=navigationBars' | grep -o 'visible=[a-z]*' | head -1)"
}
motion() { # name slice-line settle-expected
  local line="$2"
  log "$line"
  assert_ne "$1: the [motion] line is in the slice" "" "$line"
  assert_within "$1: settle=<ms> is $3 ± 17 ms" "$3" "$(echo "$line" | sed -n 's/.*settle=\([0-9.]*\).*/\1/p')" 17
  assert_eq "$1: maxGapMs ≤ 33.4 (C-31)" "yes" "$(python3 -c "
import sys
try: print('yes' if float(sys.argv[1]) <= 33.4 else 'no (%s)' % sys.argv[1])
except Exception: print('no (unreadable)')" "$(echo "$line" | sed -n 's/.*maxGapMs=\([0-9.]*\).*/\1/p')")"
}
BARS="$REPO/app/src/main/kotlin/app/tileshell/bars/SystemBars.kt"
STATUS_EPX="$(sed -n 's/.*const val STATUS_EPX = \([0-9]*\).*/\1/p' "$BARS" | head -1)"
NAV_EPX="$(sed -n 's/.*const val NAV_EPX = \([0-9]*\).*/\1/p' "$BARS" | head -1)"
record "BarMetrics.STATUS_EPX / NAV_EPX, read from bars/SystemBars.kt (C-17)" "$STATUS_EPX / $NAV_EPX"
assert_ne "BarMetrics.STATUS_EPX is read from the source" "" "$STATUS_EPX"
assert_ne "BarMetrics.NAV_EPX is read from the source" "" "$NAV_EPX"

c6; ensure_start
cal_fixtures_down
copen
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists" "" "$TESS"
BEFORE="$(ctessera_count)"
cpurge "title IN ('Pay rent','Ride a bike','Pay day','Ride home')"
TODAY="$(cdate 0)"
# The fixtures' day: the first Saturday dated 20–26 that is at least two days ahead — a "Saturday <n>" heading (R11's
# sample) in a week whose labels start with "2" (R11's "23 MON").
FIX="$(python3 -c '
import sys, datetime
t = datetime.date.fromisoformat(sys.argv[1]); d = t + datetime.timedelta(days=2)
while not (d.weekday() == 5 and 20 <= d.day <= 26): d += datetime.timedelta(days=1)
print(d)' "$TODAY")"
D0="$(clocal_ms "$TODAY" 00:00)"
assert_eq "precondition: no event today (today's group is the empty day)" "0" "$(call_instances "$D0" $(( D0 + 86400000 - 1 )))"
COLOUR="$(q "content query --uri $CAL --projection _id:calendar_color --where \"_id=$TESS\"" | sed -n 's/.*calendar_color=\(-\?[0-9]*\).*/\1/p' | python3 -c '
import sys
v = int(sys.stdin.read().strip()) & 0xFFFFFF; print((v >> 16) & 255, (v >> 8) & 255, v & 255)')"
record "the calendar's colour, from the provider's calendar_color (r g b)" "$COLOUR"
mkcal "$PERSONAL_ACCT" 'MoNa Events' 0 >/dev/null
mkcal "$WORK_ACCT" 'mark guim' 1 >/dev/null
FU="$(( $(date -u -d "$FIX" +%s) * 1000 ))"
A1="$(cmkevent "$TESS" 'Pay rent' "$FU" $(( FU + 86400000 )) --bind allDay:i:1 --bind eventTimezone:s:UTC)"
A2="$(cmkevent "$TESS" 'Ride a bike' "$FU" $(( FU + 86400000 )) --bind allDay:i:1 --bind eventTimezone:s:UTC)"
T1="$(cmkevent "$TESS" 'Pay day' "$(clocal_ms "$FIX" 13:00)" "$(clocal_ms "$FIX" 14:00)")"
T2="$(cmkevent "$TESS" 'Ride home' "$(clocal_ms "$FIX" 15:00)" "$(clocal_ms "$FIX" 16:00)")"
note "today $TODAY; the fixtures' day $FIX ($(date -d "$FIX" +%A)); all-day $A1 $A2, timed $T1 $T2; Tessera $TESS"
assert_eq "fixtures: two all-day and two timed events on $FIX" "4" "$(cevent_count "_id IN (${A1:-0},${A2:-0},${T1:-0},${T2:-0}) AND deleted=0")"
sleep 2

# ----------------------------------------------------------------------------------------------- 1: Agenda, today
log "--- 1: Agenda on today (the chrome, the strip, today's heading, the empty day, the app bar)"
ctap cal_bar:today 1.2; cview agenda 2.5
shot 1-agenda-today
assert_eq "1: Agenda is showing (cal_view_mode:agenda selected)" "true" "$(cattr "$ROW_DIR/1-agenda-today.xml" cal_view_mode:agenda selected)"
if [ "$LEGS" != 2,3 ]; then
geo 1-agenda-today agenda sections=chrome,today "status=$STATUS_EPX" "nav=$NAV_EPX" "today=$TODAY"
sysbars "Agenda"
fi

if [ "$LEGS" = all ] || [ "$LEGS" = 2,3 ] || [ "$LEGS" = 1,2,3 ]; then
# ----------------------------------------------------------------------------------------------- 2: the month drop-down
log "--- 2: the month drop-down (K5) and its motion"
M_MARK="$(ring_mark)"
ctap cal_header 1.8
shot 2-month
geo 2-month month "month=$(echo "$TODAY" | cut -c1-7)" "today=$TODAY"
motion "U8 the drop-down (R7 §2.2.6)" "$(cline "$(ring_since "$M_MARK")" '[motion] cal_month_dropdown')" 200
if [ "$(has_node "$ROW_DIR/2-month.xml" "cal_month_cell:$FIX")" != yes ]; then
  b="$(bounds "$ROW_DIR/2-month.xml" cal_month_dropdown)"
  # shellcheck disable=SC2086
  set -- $b
  adb shell input swipe $(( $3 - 120 )) $(( ($2 + $4) / 2 )) $(( $1 + 120 )) $(( ($2 + $4) / 2 )) 300; sleep 1.5
fi
ctap "cal_month_cell:$FIX" 2.5

# ----------------------------------------------------------------------------------------------- 3: Agenda, the rows
log "--- 3: Agenda at $FIX (a day heading, the event rows)"
shot 3-agenda-rows
assert_eq "3: the Agenda shows the fixtures' day" "yes" "$(cunder "$ROW_DIR/3-agenda-rows.xml" cal_agenda "cal_day:$FIX")"
geo 3-agenda-rows agenda sections=rows "today=$TODAY" "other=$FIX" "allday1=$A1" "allday2=$A2" "timed1=$T1" "colour=$COLOUR"
TINT="$(awk -F'\t' '$1 == "R" && $2 ~ /label.s tint/ {print $3}' "$ROW_DIR/3-agenda-rows.geo.tsv")"
fi   # legs 2 and 3

if [ "$LEGS" = all ]; then
# ----------------------------------------------------------------------------------------------- 4: the pane
log "--- 4: the ≡ calendar pane (K6.4)"
ctap cal_menu 1.8
shot 4-pane
geo 4-pane pane "tessera=$TESS" "colour=$COLOUR"
cback

# ----------------------------------------------------------------------------------------------- 5: Week
log "--- 5: the Week view (K4) of $FIX's week"
cview week 2.5
shot 5-week
assert_eq "5: Week is showing, on the fixtures' week" "true yes" "$(cattr "$ROW_DIR/5-week.xml" cal_view_mode:week selected) $(has_node "$ROW_DIR/5-week.xml" "cal_day:$FIX")"
geo 5-week week "status=$STATUS_EPX" "nav=$NAV_EPX" "allday1=$A1" "allday2=$A2" "tint=$TINT"
sysbars "Week"

# ----------------------------------------------------------------------------------------------- 6: Day
log "--- 6: the Day view (U1's structure) and a day page's motion"
cview day 2.5
shot 6-day
assert_eq "6: Day is showing" "true" "$(cattr "$ROW_DIR/6-day.xml" cal_view_mode:day selected)"
geo 6-day day "status=$STATUS_EPX" "nav=$NAV_EPX" "date=$FIX"
sysbars "Day"
D_MARK="$(ring_mark)"
adb shell input swipe 900 1200 200 1200 250; sleep 2.2
motion "U8 a day page (X13)" "$(cline "$(ring_since "$D_MARK")" '[motion] cal_day_page')" 250
dump_ui "$ROW_DIR/6-day-next.xml"
NEXT="$(date -d "$FIX + 1 day" +%Y-%m-%d)"
assert_eq "6: the swipe paged to the next day (cal_day:$NEXT)" "yes" "$(has_node "$ROW_DIR/6-day-next.xml" "cal_day:$NEXT")"

# ----------------------------------------------------------------------------------------------- 7: the event page
log "--- 7: the event page (U2's structure)"
copen_event "$T1"
shot 7-event-page
geo 7-event-page event_page "event=$T1"

# ----------------------------------------------------------------------------------------------- 8: the editor
log "--- 8: the editor (U3: People's 32-epx fields with a 2-epx (133,133,133) border)"
ctap cal_event_action:edit 2
shot 8-editor
geo 8-editor editor "status=$STATUS_EPX" "nav=$NAV_EPX"
sysbars "the editor"
ctap cal_editor_cancel 1.5
fi   # legs 4–8

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
c6
cal_fixtures_down
cpurge "title IN ('Pay rent','Ride a bike','Pay day','Ride home')"
assert_eq "restore: the row's events are deleted" "0" "$(cevent_count "title IN ('Pay rent','Ride a bike','Pay day','Ride home')")"
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
ensure_start
row_end
