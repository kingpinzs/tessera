#!/usr/bin/env bash
# Phase 16 E8 — time zone and DST.
#
#   Z  the zone         recorded (getprop persist.sys.timezone) and set to America/Denver
#   T  Tokyo            an event at 09:00 America/Denver on a date after the next DST change, and the row's OWN all-day
#                       event on that date; cmd alarm set-timezone Asia/Tokyo → the event shows at its Tokyo wall time
#                       (the text of cal_event_time:<id>) while the all-day event stays on its date (inside
#                       cal_allday:<that date>); back to Denver
#   N  the DST night    an event 23:30–01:30 across the fall-back night shows a 3-HOUR span in the Day view (the
#                       provider's instance end) — measured on the drawn blocks of the two days it covers, in hours of
#                       the same view's own one-hour control block — and the editor shows its stored end wall time
#   restore             the events deleted, the recorded zone set back
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
PXPY="$HERE/cal_px.py"

row_begin E8 "time zone and DST: Tokyo wall time, the all-day event on its date, the fall-back night's 3-hour span"
set_zone() { adb shell cmd alarm set-timezone "$1" >/dev/null 2>&1; sleep 3; }
den() { TZ=America/Denver date -d "$1" +%s000; }                       # "yyyy-mm-dd HH:MM" in Denver -> epoch ms
fmt() { TZ="$1" date -d "@$(( $2 / 1000 ))" "+$3"; }                    # zone ms format
# The drawn height (px) of an event's block in the Day view: the run of its own colour down a column near its right
# edge (clear of its text), which must cover the dump's box; "" when nothing is drawn.
block_px() { # dump.xml shot.png event-id
  local b; b="$(bounds "$1" "cal_event:$3")"
  [ -n "$b" ] || { echo ""; return; }
  # shellcheck disable=SC2086
  set -- $b "$2"
  local x=$(( $3 - 14 )) rgb
  rgb="$(python3 "$PXPY" pixel "$5" "$x" $(( ($2 + $4) / 2 )))"
  # shellcheck disable=SC2086
  python3 "$PXPY" runs "$5" v "$x" $(( $2 - 30 )) $(( $4 + 30 )) $rgb 6 | tr ' ' '\n' | awk -F- -v t="$2" -v bo="$4" '$1 <= (t + bo) / 2 && $2 >= (t + bo) / 2 { print $2 - $1 }' | head -1
}
ZONE0="$(ctz)"
T1224="$(adb shell settings get system time_12_24 | tr -d '\r')"
record "Z: the zone recorded at the start (getprop persist.sys.timezone)" "$ZONE0"
c6; ensure_start

# ----------------------------------------------------------------------------------------------- Z: Denver
set_zone America/Denver
assert_eq "Z: the zone is set to America/Denver" "America/Denver" "$(ctz)"
read -r NEXT_CHANGE FALLBACK <<< "$(python3 - <<'PY'
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo
z = ZoneInfo("America/Denver"); d = datetime.now(z).replace(hour=12, minute=0, second=0, microsecond=0)
nxt = fall = None
for _ in range(800):
    e = (d + timedelta(days=1)).replace(tzinfo=z)
    if e.utcoffset() != d.utcoffset():
        nxt = nxt or e.date()
        if e.utcoffset() < d.utcoffset() and fall is None: fall = e.date()
    if nxt and fall: break
    d = e
print(nxt, fall)
PY
)"
AFTER="$(date -d "$NEXT_CHANGE + 9 days" +%Y-%m-%d)"
record "Z: the next DST change in America/Denver, the date used after it, the next fall-back date" "$NEXT_CHANGE / $AFTER / $FALLBACK"
copen
TESS="$(tessera_id)"
assert_ne "precondition: Tessera exists" "" "$TESS"
BEFORE="$(ctessera_count)"

# ----------------------------------------------------------------------------------------------- T: Tokyo
log "--- T: an event at 09:00 America/Denver on $AFTER, the row's own all-day event that day, then Asia/Tokyo"
TS="$(den "$AFTER 09:00")"; TE=$(( TS + 3600000 ))
TIMED="$(q "content insert --uri $EVENTS --bind calendar_id:i:$TESS --bind title:s:'E8 nine' --bind dtstart:l:$TS --bind dtend:l:$TE --bind eventTimezone:s:America/Denver" >/dev/null; event_id 'E8 nine' "$TESS")"
AD0=$(( $(date -u -d "$AFTER" +%s) * 1000 ))
ALLDAY="$(q "content insert --uri $EVENTS --bind calendar_id:i:$TESS --bind title:s:'E8 all day' --bind dtstart:l:$AD0 --bind dtend:l:$(( AD0 + 86400000 )) --bind allDay:i:1 --bind eventTimezone:s:UTC" >/dev/null; event_id 'E8 all day' "$TESS")"
assert_ne "T: an event at 09:00 America/Denver on a date after the next DST change" "" "$TIMED"
assert_ne "T: the row's OWN all-day event on that date (allDay 1, UTC midnight, eventTimezone UTC)" "" "$ALLDAY"
copen_day "$TS"; dump_ui "$ROW_DIR/T-denver.xml"; screencap "$ROW_DIR/T-denver.png"
DEN_TEXT="$(ctext "$ROW_DIR/T-denver.xml" "cal_event_time:$TIMED")"
assert_eq "T: in Denver the Day view of $AFTER shows it (cal_day:$AFTER)" "yes" "$(has_node "$ROW_DIR/T-denver.xml" "cal_day:$AFTER")"
assert_eq "T: … at its Denver wall time" "$(fmt America/Denver "$TS" '%-I:%M %p') – $(fmt America/Denver "$TE" '%-I:%M %p')" "$DEN_TEXT"
W="$(cwithin "$ROW_DIR/T-denver.xml" "cal_allday:$AFTER" "cal_event:$ALLDAY")"
assert_eq "T: … and the all-day event in that date's band" "yes" "${W%% *}"
PID0="$(adb shell pidof app.tileshell | tr -d '\r')"
set_zone Asia/Tokyo
assert_eq "T: cmd alarm set-timezone Asia/Tokyo" "Asia/Tokyo" "$(ctz)"
TOKYO_DAY="$(fmt Asia/Tokyo "$TS" %Y-%m-%d)"
copen_day "$TS"; dump_ui "$ROW_DIR/T-tokyo.xml"; screencap "$ROW_DIR/T-tokyo.png"
TOK_TEXT="$(ctext "$ROW_DIR/T-tokyo.xml" "cal_event_time:$TIMED")"
log "the timed event's time text: [$DEN_TEXT] in America/Denver, [$TOK_TEXT] in Asia/Tokyo"
assert_eq "T: in Tokyo the event is on the Day view of its Tokyo date (cal_day:$TOKYO_DAY)" "yes" "$(has_node "$ROW_DIR/T-tokyo.xml" "cal_day:$TOKYO_DAY")"
assert_eq "T: the event shows at its Tokyo wall time (the text of cal_event_time:<id>, r3 V8)" "$(fmt Asia/Tokyo "$TS" '%-I:%M %p') – $(fmt Asia/Tokyo "$TE" '%-I:%M %p')" "$TOK_TEXT"
assert_ne "T: … which is not its Denver text" "$DEN_TEXT" "$TOK_TEXT"
copen_day "$(clocal_ms "$AFTER" 12:00)"; dump_ui "$ROW_DIR/T-tokyo-allday.xml"; screencap "$ROW_DIR/T-tokyo-allday.png"
assert_eq "T: while the all-day event stays on its date — the Day view of $AFTER (Tokyo) holds its node" "yes" "$(has_node "$ROW_DIR/T-tokyo-allday.xml" "cal_event:$ALLDAY")"
W="$(cwithin "$ROW_DIR/T-tokyo-allday.xml" "cal_allday:$AFTER" "cal_event:$ALLDAY")"
assert_eq "T: … inside cal_allday:$AFTER" "yes" "${W%% *}"
NEXT_DAY="$(date -d "$AFTER + 1 day" +%Y-%m-%d)"
copen_day "$(clocal_ms "$NEXT_DAY" 12:00)"; dump_ui "$ROW_DIR/T-tokyo-nextday.xml"
assert_eq "T: … and not on the next date ($NEXT_DAY) in Tokyo" "no" "$(has_node "$ROW_DIR/T-tokyo-nextday.xml" "cal_event:$ALLDAY")"
PREV_DAY="$(date -d "$AFTER - 1 day" +%Y-%m-%d)"
copen_day "$(clocal_ms "$PREV_DAY" 12:00)"; dump_ui "$ROW_DIR/T-tokyo-prevday.xml"
assert_eq "T: … nor on the date before ($PREV_DAY)" "no" "$(has_node "$ROW_DIR/T-tokyo-prevday.xml" "cal_event:$ALLDAY")"
set_zone America/Denver
assert_eq "T: back to Denver" "America/Denver" "$(ctz)"
record "T: the shell's process across the two zone changes (before / after)" "$PID0 / $(adb shell pidof app.tileshell | tr -d '\r')"

# ----------------------------------------------------------------------------------------------- N: the DST night
log "--- N: an event across the fall-back night ($FALLBACK), 23:30–01:30"
EVE="$(date -d "$FALLBACK - 1 day" +%Y-%m-%d)"
NS="$(den "$EVE 23:30")"; NE=$(( NS + 3 * 3600000 ))
assert_eq "N: the fixture — 23:30 the evening before, ending at the SECOND 01:30 of the fall-back date, 3 hours on" "$EVE 23:30 MDT / $FALLBACK 01:30 MST" "$(fmt America/Denver "$NS" '%Y-%m-%d %H:%M %Z') / $(fmt America/Denver "$NE" '%Y-%m-%d %H:%M %Z')"
NIGHT="$(q "content insert --uri $EVENTS --bind calendar_id:i:$TESS --bind title:s:'E8 night' --bind dtstart:l:$NS --bind dtend:l:$NE --bind eventTimezone:s:America/Denver" >/dev/null; event_id 'E8 night' "$TESS")"
CS="$(den "$FALLBACK 03:00")"
CONTROL="$(q "content insert --uri $EVENTS --bind calendar_id:i:$TESS --bind title:s:'E8 hour' --bind dtstart:l:$CS --bind dtend:l:$(( CS + 3600000 )) --bind eventTimezone:s:America/Denver" >/dev/null; event_id 'E8 hour' "$TESS")"
assert_ne "N: the event is inserted" "" "$NIGHT"
INST="$(cinstance_times "$(( NS - 86400000 ))" "$(( NE + 86400000 ))" "$NIGHT")"
assert_eq "N: the provider's instance is 3 hours long (its end − its begin, ms)" "10800000" "$(echo "$INST" | awk '{print $2 - $1}')"
copen_day "$NS"; dump_ui "$ROW_DIR/N-eve.xml"; screencap "$ROW_DIR/N-eve.png"
assert_eq "N: the evening's Day view ($EVE) shows its block" "yes yes" "$(has_node "$ROW_DIR/N-eve.xml" "cal_day:$EVE") $(has_node "$ROW_DIR/N-eve.xml" "cal_event:$NIGHT")"
H_EVE="$(block_px "$ROW_DIR/N-eve.xml" "$ROW_DIR/N-eve.png" "$NIGHT")"
copen_day $(( NS + 3600000 )); dump_ui "$ROW_DIR/N-fallback.xml"; screencap "$ROW_DIR/N-fallback.png"
assert_eq "N: the fall-back date's Day view ($FALLBACK) shows its block" "yes yes" "$(has_node "$ROW_DIR/N-fallback.xml" "cal_day:$FALLBACK") $(has_node "$ROW_DIR/N-fallback.xml" "cal_event:$NIGHT")"
H_FALL="$(block_px "$ROW_DIR/N-fallback.xml" "$ROW_DIR/N-fallback.png" "$NIGHT")"
if [ "$(has_node "$ROW_DIR/N-fallback.xml" "cal_event:$CONTROL")" != yes ]; then
  adb shell input swipe 540 1500 540 1100 600; sleep 1.2; dump_ui "$ROW_DIR/N-fallback-control.xml"; screencap "$ROW_DIR/N-fallback-control.png"
else
  cp "$ROW_DIR/N-fallback.xml" "$ROW_DIR/N-fallback-control.xml"; cp "$ROW_DIR/N-fallback.png" "$ROW_DIR/N-fallback-control.png"
fi
H_HOUR="$(block_px "$ROW_DIR/N-fallback-control.xml" "$ROW_DIR/N-fallback-control.png" "$CONTROL")"
note "drawn block heights (px): the evening's part $H_EVE, the fall-back date's part $H_FALL, the one-hour control $H_HOUR; dump boxes: eve [$(bounds "$ROW_DIR/N-eve.xml" "cal_event:$NIGHT")] fall-back [$(bounds "$ROW_DIR/N-fallback.xml" "cal_event:$NIGHT")] control [$(bounds "$ROW_DIR/N-fallback-control.xml" "cal_event:$CONTROL")]"
assert_ne "N: the one-hour control block is drawn and measured" "" "$H_HOUR"
# Each block is drawn 1 epx short of its length (the gap to the next block), so the gap is added back to each part.
SPAN="$(python3 -c "
e, f, h = float('${H_EVE:-0}' or 0), float('${H_FALL:-0}' or 0), float('${H_HOUR:-0}' or 0)
print('%.2f' % ((e + $CPX + f + $CPX) / (h + $CPX)) if h else '')")"
log "the span drawn across the two days: $SPAN hours (0.5 h before midnight + 2.5 h after, on a 25-hour day)"
assert_within "N: it shows a 3-hour span in the day view (drawn hours, the two days' blocks together)" 3.0 "$SPAN" 0.1
assert_within "N: … the part on the fall-back date is 2.5 hours (the repeated hour is drawn)" 2.5 "$(python3 -c "print('%.2f' % ((float('${H_FALL:-0}' or 0) + $CPX) / (float('${H_HOUR:-1}' or 1) + $CPX)))")" 0.1
record "N: the block's own time text on the fall-back date" "$(ctext "$ROW_DIR/N-fallback.xml" "cal_event_time:$NIGHT")"
copen_event "$NIGHT"; ctap cal_event_action:edit 2; dump_ui "$ROW_DIR/N-editor.xml"; screencap "$ROW_DIR/N-editor.png"
assert_eq "N: the editor is open on it" "yes" "$(has_node "$ROW_DIR/N-editor.xml" cal_editor)"
note "the editor: start [$(cfield "$ROW_DIR/N-editor.xml" start_date) $(cfield "$ROW_DIR/N-editor.xml" start_time)] end [$(cfield "$ROW_DIR/N-editor.xml" end_date) $(cfield "$ROW_DIR/N-editor.xml" end_time)]"
assert_eq "N: the editor shows its stored end wall time (01:30)" "$(fmt America/Denver "$NE" '%-I:%M %p')" "$(cfield "$ROW_DIR/N-editor.xml" end_time)"
assert_eq "N: … on the fall-back date" "$(fmt America/Denver "$NE" '%a %-d %b %Y')" "$(cfield "$ROW_DIR/N-editor.xml" end_date)"
assert_eq "N: … and its start as stored (23:30 the evening before)" "$(fmt America/Denver "$NS" '%a %-d %b %Y') $(fmt America/Denver "$NS" '%-I:%M %p')" "$(cfield "$ROW_DIR/N-editor.xml" start_date) $(cfield "$ROW_DIR/N-editor.xml" start_time)"
ctap cal_editor_cancel 1.5
assert_eq "N: … the look changed nothing: the stored end is as inserted" "dtend=$NE" "$(cevents dtend "_id=$NIGHT" | sed 's/^Row: 0 //')"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore"
c6
cpurge "title IN ('E8 nine','E8 all day','E8 night','E8 hour')"
assert_eq "restore: the events are deleted" "0" "$(cevent_count "title IN ('E8 nine','E8 all day','E8 night','E8 hour')")"
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
set_zone "$ZONE0"
assert_eq "restore: the recorded zone is set back" "$ZONE0" "$(ctz)"
assert_eq "restore: time_12_24 is as it was" "$T1224" "$(adb shell settings get system time_12_24 | tr -d '\r')"
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 4
ensure_start
row_end
