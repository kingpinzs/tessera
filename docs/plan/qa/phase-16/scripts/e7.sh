#!/usr/bin/env bash
# Phase 16 E7 — thousands of events (T16-13, T16-17; r3 V21).
#
#   fixtures  a driver loop — ONE adb shell running the `content insert` lines on the device, 16 workers — inserts 5,000
#             events across 24 months into the QA calendar (run time RECORDed): 200 in each of 23 months, 400 in the
#             BUSY month, 200 of those on one day. Two more, "E7 soon 1 / 2", start inside the next 24 hours.
#   M  the month drop-down   opened, swiped to the busy month and dumped; a MARK immediately before the tap on
#                            cal_month_cell:<its first day> → Agenda at that day (its dump); the `view agenda` line has
#                            ms ≤ 3000 and wall − MARK ≤ 3000
#   W  the busiest week      the Week view of the 200-event day's week from its own MARK: the `view week` line the same,
#                            and its n equal to the host's instances/when count for that week
#   P  paging                12 weeks forward by `input swipe`, a MARK before each: every `view week` line ms ≤ 3000 and
#                            wall − that swipe's MARK ≤ 3000; dumpsys gfxinfo janky frames ≤ 5 % over the run
#   D  the 200-event day     the Day view lists them scrollably (every one of the 200 reached by scrolling)
#   T  the tile              still shows only the next 24 hours' events (CalendarFeed's window)
#   restore                  the QA calendar deleted (its events cascade)
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
TILES="$P16/../phase-15/scripts/tiles.py"

row_begin E7 "5,000 events: the month drop-down, the busiest week, 12 weeks of paging, a 200-event day, the tile"
tile_texts() { python3 "$TILES" "$1" "$2" | awk -F'\t' '{ print $6 }' | sed 's/^texts=//'; }
# The first `view <mode>` line of a slice at or after a MARK: "<wall − mark> <ms> <n> <from> <to>", or "none".
view_line() { # slice-file mark mode
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
mark, mode = int(sys.argv[2]), sys.argv[3]
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = re.search(r'wall=(\d+) \[calendar\] view %s (\S+?)\.\.(\S+?): (\d+) instances in (\d+) ms' % re.escape(mode), line)
    if m and int(m.group(1)) >= mark:
        print(int(m.group(1)) - mark, m.group(5), m.group(4), m.group(2), m.group(3)); break
else:
    print("none")
PY
}
gfx_janky() { adb shell dumpsys gfxinfo app.tileshell < /dev/null | tr -d '\r' | awk '/^Total frames rendered:/ {t=$4} /^Janky frames:/ {j=$3; p=$4} END {gsub(/[()%]/, "", p); print t, j, p}'; }
CRASH0="$(ccrashes)"
c6; ensure_start
cal_fixtures_down
copen; TESS="$(tessera_id)"; adb shell input keyevent KEYCODE_HOME; sleep 1
BEFORE="$(ctessera_count)"
TZ_ID="$(ctz)"

# ----------------------------------------------------------------------------------------------- fixtures
log "--- fixtures: 5,000 events across 24 months in the QA calendar"
mkcal_qa >/dev/null
QA_ID="$(cal_id qa)"
assert_ne "fixtures: the QA calendar (qa / LOCAL)" "" "$QA_ID"
python3 - "$QA_ID" "$TZ_ID" "$(adb shell date +%Y-%m | tr -d '\r')" "$ROW_DIR/insert.sh" "$ROW_DIR/plan.env" <<'PY'
import sys, datetime
from zoneinfo import ZoneInfo
cal, tz, ym, out, env = sys.argv[1:6]
z = ZoneInfo(tz); y, m = map(int, ym.split("-"))
def month(k):                      # the k-th month AFTER the current one (k = 1..24)
    t = (y * 12 + (m - 1)) + k
    return t // 12, t % 12 + 1
def ms(yy, mm, dd, hh, mi):
    return int(datetime.datetime(yy, mm, dd, hh, mi, tzinfo=z).timestamp() * 1000)
lines = []; n = 0; BUSY = 2
def add(yy, mm, dd, hh, mi, minutes=30):
    global n
    n += 1
    s = ms(yy, mm, dd, hh, mi)
    lines.append("content insert --uri content://com.android.calendar/events --bind calendar_id:i:%s --bind title:s:'E7 %04d' --bind dtstart:l:%d --bind dtend:l:%d --bind eventTimezone:s:%s" % (cal, n, s, s + minutes * 60000, tz))
for k in range(1, 25):
    yy, mm = month(k)
    days = list(range(3, 28))      # 25 days, clear of a month's first two (tomorrow may be the 1st) and its end
    if k == BUSY:
        for i in range(200):       # one day with 200 events, every 7 minutes from 00:10, 30 minutes each
            t = 10 + i * 7
            add(yy, mm, 15, t // 60, t % 60)
        rest = [d for d in days if d != 15]
        for i in range(200):
            add(yy, mm, rest[i % len(rest)], 8 + (i // len(rest)), 0)
    else:
        for i in range(200):
            add(yy, mm, days[i % len(days)], 8 + (i // len(days)), 0)
open(out, "w").write("\n".join(lines) + "\n")
by, bm = month(BUSY)
open(env, "w").write("TOTAL=%d\nBUSY_FIRST=%04d-%02d-01\nBUSY_DAY=%04d-%02d-15\nBUSY_YM=%04d-%02d\nBUSY_K=%d\n" % (n, by, bm, by, bm, by, bm, BUSY))
PY
# shellcheck disable=SC1091
. "$ROW_DIR/plan.env"
assert_eq "fixtures: the driver's loop holds 5,000 insert lines (the busy month is $BUSY_YM, its 200-event day $BUSY_DAY)" "5000 5000" "$TOTAL $(grep -c '^content insert' "$ROW_DIR/insert.sh")"
# `content` starts a process per insert, so the one device-side loop runs its 5,000 lines as 16 workers side by side.
rm -rf "$ROW_DIR/workers"; mkdir -p "$ROW_DIR/workers"
awk -v d="$ROW_DIR/workers" '{ print > (d "/w" (NR % 16) ".sh") }' "$ROW_DIR/insert.sh"
adb shell rm -rf /data/local/tmp/e7; adb shell mkdir -p /data/local/tmp/e7
adb push "$ROW_DIR/workers/." /data/local/tmp/e7/ >/dev/null
T0="$(date +%s)"
adb shell 'for f in /data/local/tmp/e7/w*.sh; do sh $f > /dev/null 2>&1 & done; wait' < /dev/null > "$ROW_DIR/insert.out" 2>&1
T1="$(date +%s)"
adb shell rm -rf /data/local/tmp/e7
rm -rf "$ROW_DIR/workers"
record "fixtures: the insert loop's run time (one adb shell: 5,000 content insert lines, 16 workers)" "$(( T1 - T0 )) s"
N_QA="$(cevent_count "calendar_id=$QA_ID AND deleted=0")"
assert_eq "fixtures: the QA calendar holds 5,000 events" "5000" "$N_QA"
BS="$(clocal_ms "$BUSY_FIRST" 00:00)"; BE="$(clocal_ms "$(date -d "$BUSY_FIRST + 1 month" +%Y-%m-%d)" 00:00)"
assert_eq "fixtures: one month holds 400 of them (the provider's instances over $BUSY_YM)" "400" "$(call_instances "$BS" $(( BE - 1 )))"
DS="$(clocal_ms "$BUSY_DAY" 00:00)"
assert_eq "fixtures: a day with 200 events ($BUSY_DAY)" "200" "$(cevent_count "calendar_id=$QA_ID AND dtstart>=$DS AND dtstart<$(( DS + 86400000 ))")"
NOW="$(device_ms)"; S1=$(( (NOW / 60000 + 90) * 60000 )); S2=$(( S1 + 3 * 3600000 ))
SOON1="$(cmkevent "$QA_ID" 'E7 soon 1' "$S1" $(( S1 + 1800000 )))"; SOON2="$(cmkevent "$QA_ID" 'E7 soon 2' "$S2" $(( S2 + 1800000 )))"
assert_eq "fixtures: exactly two events inside the next 24 hours (E7 soon 1 / 2)" "2" "$(call_instances "$NOW" $(( NOW + 86400000 )))"
sleep 5

# ----------------------------------------------------------------------------------------------- M: the month drop-down
log "--- M: the month drop-down to the busy month, then its first day's cell"
c6; ensure_start
copen; ctap cal_bar:today 1.2; cview agenda 2
adb shell dumpsys gfxinfo app.tileshell reset >/dev/null 2>&1
ctap cal_header 1.5
for i in $(seq 1 "$BUSY_K"); do
  dump_ui "$ROW_DIR/.month.xml"; b="$(bounds "$ROW_DIR/.month.xml" cal_month_dropdown)"
  # shellcheck disable=SC2086
  set -- $b
  adb shell input swipe $(( $3 - 120 )) $(( ($2 + $4) / 2 )) $(( $1 + 120 )) $(( ($2 + $4) / 2 )) 300; sleep 1.5
done
dump_ui "$ROW_DIR/M-month.xml"; screencap "$ROW_DIR/M-month.png"
assert_eq "M: cal_month_dropdown is open and dumped" "yes" "$(has_node "$ROW_DIR/M-month.xml" cal_month_dropdown)"
assert_eq "M: … showing the busy month: cal_month_cell:$BUSY_FIRST" "yes" "$(cunder "$ROW_DIR/M-month.xml" cal_month_dropdown "cal_month_cell:$BUSY_FIRST")"
M_MARK="$(ring_mark)"
tap_node "$ROW_DIR/M-month.xml" "cal_month_cell:$BUSY_FIRST"; sleep 3
dump_ui "$ROW_DIR/M-agenda.xml"; screencap "$ROW_DIR/M-agenda.png"
ring_since "$M_MARK" > "$ROW_DIR/M-slice.txt"
assert_eq "M: Agenda at that day — its dump is available: cal_view_mode:agenda selected" "true" "$(cattr "$ROW_DIR/M-agenda.xml" cal_view_mode:agenda selected)"
assert_eq "M: … with that day's heading in the list (cal_day:$BUSY_FIRST inside cal_agenda)" "yes" "$(cunder "$ROW_DIR/M-agenda.xml" cal_agenda "cal_day:$BUSY_FIRST")"
assert_eq "M: … and the drop-down closed" "no" "$(has_node "$ROW_DIR/M-agenda.xml" cal_month_dropdown)"
VL="$(view_line "$ROW_DIR/M-slice.txt" "$M_MARK" agenda)"; log "view agenda after the tap: $VL (wall−MARK ms, in-ms, n, from, to)"
assert_ne "M: the slice holds a [calendar] view agenda <from>..<to>: n instances in <ms> ms line" "none" "$VL"
# shellcheck disable=SC2086
set -- $VL
assert_eq "M: … its ms ≤ 3000" "yes" "$([ "${2:-99999}" -le 3000 ] && echo yes || echo "no (${2:-})")"
assert_eq "M: … and wall= − MARK ≤ 3000" "yes" "$([ "${1:-99999}" -le 3000 ] && echo yes || echo "no (${1:-})")"
record "M: the agenda's window and its instance count" "${4:-}..${5:-}: ${3:-} instances"

# ----------------------------------------------------------------------------------------------- W: the busiest week
log "--- W: the Week view of the busy month's busiest week (the week of $BUSY_DAY)"
ctap cal_header 1.5; dump_ui "$ROW_DIR/W-month.xml"
tap_node "$ROW_DIR/W-month.xml" "cal_month_cell:$BUSY_DAY"; sleep 2.5
ctap cal_bar:view 1.2; dump_ui "$ROW_DIR/W-menu.xml"
W_MARK="$(ring_mark)"
tap_node "$ROW_DIR/W-menu.xml" cal_view_pick:week; sleep 3
dump_ui "$ROW_DIR/W-week.xml"; screencap "$ROW_DIR/W-week.png"
ring_since "$W_MARK" > "$ROW_DIR/W-slice.txt"
assert_eq "W: the Week view is showing (cal_view_mode:week selected)" "true" "$(cattr "$ROW_DIR/W-week.xml" cal_view_mode:week selected)"
assert_eq "W: … of the week that holds $BUSY_DAY" "yes" "$(has_node "$ROW_DIR/W-week.xml" "cal_day:$BUSY_DAY")"
VL="$(view_line "$ROW_DIR/W-slice.txt" "$W_MARK" week)"; log "view week after the tap: $VL"
assert_ne "W: the slice holds its [calendar] view week line" "none" "$VL"
# shellcheck disable=SC2086
set -- $VL
assert_eq "W: … its ms ≤ 3000" "yes" "$([ "${2:-99999}" -le 3000 ] && echo yes || echo "no (${2:-})")"
assert_eq "W: … and wall= − MARK ≤ 3000" "yes" "$([ "${1:-99999}" -le 3000 ] && echo yes || echo "no (${1:-})")"
WK_FROM="${4:-}"; WK_TO="${5:-}"; WK_N="${3:-}"
HS="$(clocal_ms "$WK_FROM" 00:00)"; HE="$(clocal_ms "$(date -d "$WK_TO + 1 day" +%Y-%m-%d)" 00:00)"
HOST_N="$(call_instances "$HS" $(( HE - 1 )))"
note "the week $WK_FROM..$WK_TO: the line's n $WK_N; the host's instances/when/$HS/$(( HE - 1 )) count $HOST_N"
assert_eq "W: … its n equals the host's content query …/instances/when/<week start>/<week end> count" "$HOST_N" "$WK_N"
assert_eq "W: … and it is the busiest week (it holds the 200-event day: n ≥ 200)" "yes" "$([ "${WK_N:-0}" -ge 200 ] && echo yes || echo no)"

# ----------------------------------------------------------------------------------------------- P: paging 12 weeks
log "--- P: paging 12 weeks forward, a MARK before each swipe"
: > "$ROW_DIR/P-lines.txt"; P_OK_MS=0; P_OK_WALL=0; P_LINES=0
for i in $(seq 1 12); do
  P_MARK="$(ring_mark)"
  adb shell input swipe 900 1200 200 1200 250
  sleep 2.2
  ring_since "$P_MARK" > "$ROW_DIR/.p-slice.txt"
  VL="$(view_line "$ROW_DIR/.p-slice.txt" "$P_MARK" week)"
  echo "$i $VL" >> "$ROW_DIR/P-lines.txt"
  [ "$VL" = none ] && continue
  P_LINES=$((P_LINES + 1))
  # shellcheck disable=SC2086
  set -- $VL
  [ "$2" -le 3000 ] && P_OK_MS=$((P_OK_MS + 1))
  [ "$1" -le 3000 ] && P_OK_WALL=$((P_OK_WALL + 1))
done
log "the 12 swipes (swipe, wall−MARK, in-ms, n, from, to):"; sed 's/^/   /' "$ROW_DIR/P-lines.txt" | tee -a "$LOG" >/dev/null
assert_eq "P: every one of the 12 swipes gave a view week line" "12" "$P_LINES"
assert_eq "P: … twelve different weeks, each seven days on from the last" "12" "$(awk 'NF >= 6 {print $5}' "$ROW_DIR/P-lines.txt" | sort -u | wc -l | tr -d ' ')"
assert_eq "P: every view week line has ms ≤ 3000" "12" "$P_OK_MS"
assert_eq "P: … and wall= − that swipe's MARK ≤ 3000" "12" "$P_OK_WALL"
GFX="$(gfx_janky)"; adb shell dumpsys gfxinfo app.tileshell < /dev/null > "$ROW_DIR/P-gfxinfo.txt" 2>&1
log "dumpsys gfxinfo app.tileshell over the run (total frames, janky frames, janky %): $GFX"
# shellcheck disable=SC2086
set -- $GFX
assert_ne "P: gfxinfo counted frames over the run" "0" "${1:-0}"
assert_eq "P: janky frames ≤ 5 % over the run (phase 01's threshold, as a bound on the emulator)" "yes" "$(python3 -c "print('yes' if float('${3:-100}' or 100) <= 5.0 else 'no (${3:-}%)')")"

# ----------------------------------------------------------------------------------------------- D: the 200-event day
log "--- D: a day with 200 events lists them scrollably in the Day view"
D_MARK="$(ring_mark)"
copen_day "$(clocal_ms "$BUSY_DAY" 00:30)"; sleep 1
dump_ui "$ROW_DIR/D-day.xml"; screencap "$ROW_DIR/D-day.png"
assert_eq "D: the Day view of $BUSY_DAY is showing" "true yes" "$(cattr "$ROW_DIR/D-day.xml" cal_view_mode:day selected) $(has_node "$ROW_DIR/D-day.xml" "cal_day:$BUSY_DAY")"
ring_since "$D_MARK" > "$ROW_DIR/D-slice.txt"
VL="$(view_line "$ROW_DIR/D-slice.txt" "$D_MARK" day)"; log "view day: $VL"
assert_eq "D: its view day line counts 200 instances" "200" "$(echo "$VL" | awk '{print $3}')"
: > "$ROW_DIR/D-ids.txt"; prev=""; same=0
for i in $(seq 0 60); do
  dump_ui "$ROW_DIR/.day.xml"
  cur="$(grep -o 'resource-id="cal_event:[0-9]*"' "$ROW_DIR/.day.xml" | sort -u)"
  printf '%s\n' "$cur" >> "$ROW_DIR/D-ids.txt"
  if [ "$cur" = "$prev" ]; then same=$((same + 1)); else same=0; fi
  [ "$same" -ge 2 ] && break
  prev="$cur"
  adb shell input swipe 540 1700 540 1000 700; sleep 0.9
done
SEEN="$(grep -c . <(sort -u "$ROW_DIR/D-ids.txt" | grep cal_event))"
note "distinct cal_event nodes reached in $i swipe(s) of the Day view: $SEEN"
assert_eq "D: it lists them scrollably — all 200 events of the day are reached by scrolling the Day view" "200" "$SEEN"
assert_ne "D: … the list did scroll (more than one screen of them)" "0" "$i"

# ----------------------------------------------------------------------------------------------- T: the tile
log "--- T: the Calendar tile still shows only the next 24 hours' events"
c6; ensure_start
T_MARK="$(ring_mark)"
: > "$ROW_DIR/T-tile-texts.txt"
for i in $(seq 1 18); do
  gdump "$ROW_DIR/T-start.xml"
  tile_texts "$ROW_DIR/T-start.xml" slot:CALENDAR >> "$ROW_DIR/T-tile-texts.txt"
  sleep 1
done
screencap "$ROW_DIR/T-start.png"
FACES="$(grep -v '^$' "$ROW_DIR/T-tile-texts.txt" | sort -u | paste -sd';')"; log "the CALENDAR tile's faces over 18 dumps: $FACES"
assert_contains "T: the tile shows an event of the next 24 hours (E7 soon 1)" "E7 soon 1" "$FACES"
assert_contains "T: … and the other (E7 soon 2)" "E7 soon 2" "$FACES"
OTHER="$(grep -o 'E7 [0-9][0-9]*' "$ROW_DIR/T-tile-texts.txt" | sort -u | paste -sd' ')"
assert_eq "T: … and none of the 5,000 outside its window (titles E7 <n> seen on the tile)" "" "$OTHER"
assert_eq "T: the feed's faces: the day face and the two (faces=3)" "faces=3" "$(cline "$(csince "$ROW_MARK")" '[calendar] refresh (' | grep -oE 'faces=[0-9]+')"

# ----------------------------------------------------------------------------------------------- restore
log "--- restore: the QA calendar deleted (its events cascade)"
ring_save
R0="$(date +%s)"
cal_fixtures_down
record "restore: the delete's run time" "$(( $(date +%s) - R0 )) s"
assert_eq "restore: the QA calendar is gone" "" "$(cal_id qa)"
assert_eq "restore: its events cascaded — no E7 event is left" "0" "$(cevent_count "title LIKE 'E7 %'")"
assert_eq "restore: Tessera's event count equals the count before the row" "$BEFORE" "$(ctessera_count)"
assert_eq "restore: no new crash of the shell during the row" "$CRASH0" "$(ccrashes)"
c6; ensure_start
row_end
