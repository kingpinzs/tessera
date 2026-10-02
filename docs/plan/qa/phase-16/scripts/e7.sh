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
#
# E7_LEGS=jank runs the fixtures, M, W, P (the legs whose frames are "the run" of the janky-frames clause) and the
# restore, and skips D and T — a narrow re-run after a failed one, as the owner's ruling of 2026-10-01 allows; the
# log's first RECORD says so, and D and T then stand on the row's earlier run.
#
# E7_LEGS=baseline is a DIAGNOSIS run for defects/D-E7-1.md, not the gate row (the lead's order of 2026-10-02): the same
# M, W and P legs — the same months, the same weeks, the same twelve swipes at the same pace, the same resets and the
# same per-leg gfxinfo + framestats files — on a QA calendar holding NO events (the inserts are skipped). It RECORDs the
# legs' sum and has no verdict line for the 5 %; its folder is kept as E7-baseline-empty-calendar-….
#
# E7_LEGS=Dframes is a DIAGNOSIS run of leg D alone, with the row's fixtures (gate review A, note 4: the Day view composes
# every block of the day at once and no leg took frame stats there): the counters are reset before the Day view opens on
# the 200-event day, gfxinfo + framestats are saved after it has drawn (D1) and after every swipe of its scroll (D2-…),
# and the longest frame of each with its stage split is RECORDed (D-legs.tsv). No verdict on the 5 %.
#
# E7_MEASURE=compare (with E7_BASELINE_DIR=<a baseline run's folder> and E7_DFRAMES_DIR=<a Dframes run's folder>, both
# on the build installed now) asserts the comparison instead of the doc's absolute 5 %: every leg's longest frame under
# 100 ms — the legs of this run (P1–P5) AND the Day view's legs of the Dframes run (D1, D2-…) — and the run's janky
# share no more than the baseline's + 5 points. The doc's 5 % reading is RECORDed. The default is the doc's clause as
# worded; the owner has not ruled on the measure.
set -uo pipefail
LEGS="${E7_LEGS:-all}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
. "$HERE/cal_lib.sh"
TILES="$P16/../phase-15/scripts/tiles.py"

row_begin E7 "5,000 events: the month drop-down, the busiest week, 12 weeks of paging, a 200-event day, the tile"
if [ "$LEGS" != all ]; then
  case "$LEGS" in
    jank) record "legs run" "fixtures, M, W, P ONLY (the janky-frames clause's run) — a narrow re-run; D (the 200-event day) and T (the tile) stand on the row's earlier run" ;;
    baseline) record "legs run" "BASELINE — a diagnosis for D-E7-1, NOT the gate row: M, W, P on a QA calendar holding NO events (no inserts); no verdict on the 5 %; D and T not run" ;;
    Dframes) record "legs run" "fixtures and D ONLY, with frame stats (a diagnosis of the Day view on the 200-event day, NOT the gate row; no verdict on the 5 %); M, W, P and T not run" ;;
    *) _verdict FAIL "E7_LEGS" "E7_LEGS is jank, baseline or Dframes (got $LEGS)"; row_end; exit 1 ;;
  esac
fi
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
# The diagnosis the lead asked for (D-E7-1): one leg's frames. Saves `dumpsys gfxinfo` and its `framestats` under the
# leg's name, adds the leg's totals to the legs file (P-legs.tsv; D-legs.tsv for leg D), RECORDs them with the leg's
# longest frame and that frame's stage split (scripts/cal_frames.py), and resets the counters for the next leg.
FRAMES="$HERE/cal_frames.py"
LEGS_TSV="$ROW_DIR/P-legs.tsv"
: > "$LEGS_TSV"
gfx_leg() { # name
  adb shell dumpsys gfxinfo app.tileshell < /dev/null | tr -d '\r' > "$ROW_DIR/$1.gfxinfo.txt"
  adb shell dumpsys gfxinfo app.tileshell framestats < /dev/null | tr -d '\r' > "$ROW_DIR/$1.framestats.txt"
  adb shell dumpsys gfxinfo app.tileshell reset >/dev/null 2>&1
  local t j u d p50 p90 p99 long stages
  t="$(sed -n 's/^Total frames rendered: //p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"
  j="$(sed -n 's/^Janky frames: \([0-9]*\).*/\1/p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"
  u="$(sed -n 's/^Number Slow UI thread: //p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"
  d="$(sed -n 's/^Number Slow issue draw commands: //p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"
  p50="$(sed -n 's/^50th percentile: //p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"; p90="$(sed -n 's/^90th percentile: //p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"; p99="$(sed -n 's/^99th percentile: //p' "$ROW_DIR/$1.gfxinfo.txt" | head -1)"
  # the leg's longest frame (FrameCompleted - IntendedVsync, ms) and its stages: input animation traversal draw | sync issue swap
  stages="$(python3 "$FRAMES" longest "$ROW_DIR/$1.framestats.txt")"
  long="$(printf '%s' "$stages" | cut -f1)"
  stages="$(printf '%s' "$stages" | awk -F'\t' '{printf "input %s + animation %s + traversal %s + draw %s (UI thread) | sync %s + issue %s + swap %s (render thread); frame %s of %s", $2, $3, $4, $5, $6, $7, $8, $9, $10}')"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "${t:-0}" "${j:-0}" "${u:-0}" "${d:-0}" "$p50" "$p90" "$p99" "$long" "$stages" >> "$LEGS_TSV"
  record "gfxinfo, leg $1: total frames / janky / slow UI thread / slow draw; 50th 90th 99th; the longest frame and its stages" "${t:-0} / ${j:-0} / ${u:-0} / ${d:-0}; $p50 $p90 $p99; $long ms = $stages"
}
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
if [ "$LEGS" = baseline ]; then
  BS="$(clocal_ms "$BUSY_FIRST" 00:00)"; BE="$(clocal_ms "$(date -d "$BUSY_FIRST + 1 month" +%Y-%m-%d)" 00:00)"
  assert_eq "baseline: the inserts are skipped — the QA calendar holds no event" "0" "$(cevent_count "calendar_id=$QA_ID AND deleted=0")"
  assert_eq "baseline: … and no calendar has an instance in the month the legs go to ($BUSY_YM)" "0" "$(call_instances "$BS" $(( BE - 1 )))"
  sleep 5
else
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
fi   # the inserts

if [ "$LEGS" != Dframes ]; then
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
gfx_leg P1-dropdown-to-the-busy-month
assert_eq "M: cal_month_dropdown is open and dumped" "yes" "$(has_node "$ROW_DIR/M-month.xml" cal_month_dropdown)"
assert_eq "M: … showing the busy month: cal_month_cell:$BUSY_FIRST" "yes" "$(cunder "$ROW_DIR/M-month.xml" cal_month_dropdown "cal_month_cell:$BUSY_FIRST")"
M_MARK="$(ring_mark)"
tap_node "$ROW_DIR/M-month.xml" "cal_month_cell:$BUSY_FIRST"; sleep 3
dump_ui "$ROW_DIR/M-agenda.xml"; screencap "$ROW_DIR/M-agenda.png"
ring_since "$M_MARK" > "$ROW_DIR/M-slice.txt"
gfx_leg P2-agenda-busy-month-first-day
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
gfx_leg P3-dropdown-to-the-200-event-day
ctap cal_bar:view 1.2; dump_ui "$ROW_DIR/W-menu.xml"
W_MARK="$(ring_mark)"
tap_node "$ROW_DIR/W-menu.xml" cal_view_pick:week; sleep 3
dump_ui "$ROW_DIR/W-week.xml"; screencap "$ROW_DIR/W-week.png"
ring_since "$W_MARK" > "$ROW_DIR/W-slice.txt"
gfx_leg P4-week-busiest
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
if [ "$LEGS" = baseline ]; then
  assert_eq "W: baseline — that week holds no instance" "0" "${WK_N:-}"
else
  assert_eq "W: … and it is the busiest week (it holds the 200-event day: n ≥ 200)" "yes" "$([ "${WK_N:-0}" -ge 200 ] && echo yes || echo no)"
fi

# ----------------------------------------------------------------------------------------------- P: paging 12 weeks
log "--- P: paging 12 weeks forward, a MARK before each swipe"
: > "$ROW_DIR/P-lines.txt"; P_OK_MS=0; P_OK_WALL=0; P_LINES=0
for i in $(seq 1 12); do
  P_MARK="$(ring_mark)"
  adb shell input swipe 900 1200 200 1200 250
  sleep 2.2
  ring_since "$P_MARK" > "$ROW_DIR/.p-slice.txt"
  gfx_leg "P5-swipe-$(printf '%02d' "$i")"
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
log "the legs (name, total frames, janky, slow UI thread, slow draw, 50th, 90th, 99th, the longest frame in ms, its stages):"; sed 's/^/   /' "$ROW_DIR/P-legs.tsv" | tee -a "$LOG" >/dev/null
LONGEST="$(sort -t$'\t' -k9,9 -g -r "$ROW_DIR/P-legs.tsv" | head -1 | awk -F'\t' '{print $9 " ms in " $1}')"
OVER100="$(awk -F'\t' '$9 + 0 >= 100 {printf "%s%s (%s ms)", s, $1, $9; s = ", "}' "$ROW_DIR/P-legs.tsv")"
record "the longest frame of every leg: the longest of all; the legs with a frame of 100 ms or more" "$LONGEST; [${OVER100}]"
P3_LONG="$(awk -F'\t' '$1 == "P3-dropdown-to-the-200-event-day" {print $9}' "$ROW_DIR/P-legs.tsv")"
record "P3: the longest frame when the Agenda lands on the 200-event day (266.7 ms on build 6009c0b1 with 5,000 events, 50.1 ms on its empty calendar)" "$P3_LONG ms — $(python3 -c "print('the long frame is gone (under 100 ms)' if float('${P3_LONG:-999}') < 100 else 'the long frame is still there (100 ms or more)')")"
GFX="$(awk -F'\t' '{t += $2; j += $3} END {printf "%d %d %.2f", t, j, (t ? 100 * j / t : 100)}' "$ROW_DIR/P-legs.tsv")"
log "dumpsys gfxinfo app.tileshell over the run = the sum of its legs (total frames, janky frames, janky %): $GFX"
# shellcheck disable=SC2086
set -- $GFX
assert_ne "P: gfxinfo counted frames over the run" "0" "${1:-0}"
if [ "$LEGS" = baseline ]; then
  record "P: baseline — the legs' sum on an empty calendar: total frames / janky / janky % (no verdict: a diagnosis)" "${1:-0} / ${2:-0} / ${3:-}%"
elif [ "${E7_MEASURE:-doc}" = compare ]; then
  # The comparison (the lead's option A, asserted only when asked for): against a baseline run of the same legs on an
  # empty calendar, on the same build.
  BASE_DIR="${E7_BASELINE_DIR:-}"; DF_DIR="${E7_DFRAMES_DIR:-}"
  record "P: the doc's clause as worded (janky frames ≤ 5 % over the run) reads" "${3:-}% — NOT asserted in this run (E7_MEASURE=compare)"
  assert_eq "P: compare — the baseline folder is a BASELINE run of this row on the build installed now" "yes yes" "$([ -f "$BASE_DIR/P-legs.tsv" ] && grep -q 'RECORD  legs run .*BASELINE' "$BASE_DIR/E7.txt" 2>/dev/null && echo yes || echo no) $([ "$(grep -m1 '^apk installed' "$BASE_DIR/E7.txt" 2>/dev/null | awk '{print $3}')" = "$(installed_apk_id)" ] && echo yes || echo no)"
  BASE_GFX="$(awk -F'\t' '{t += $2; j += $3} END {printf "%d %d %.2f", t, j, (t ? 100 * j / t : 100)}' "$BASE_DIR/P-legs.tsv" 2>/dev/null)"
  record "P: compare — the baseline's sum (total frames, janky, janky %) from $(basename "${BASE_DIR:-none}")" "$BASE_GFX"
  assert_eq "P: compare — the Dframes folder is a Dframes run of this row on the build installed now" "yes yes" "$([ -f "$DF_DIR/D-legs.tsv" ] && grep -q 'RECORD  legs run .*fixtures and D ONLY' "$DF_DIR/E7.txt" 2>/dev/null && echo yes || echo no) $([ "$(grep -m1 '^apk installed' "$DF_DIR/E7.txt" 2>/dev/null | awk '{print $3}')" = "$(installed_apk_id)" ] && echo yes || echo no)"
  D_OVER100="$(awk -F'\t' '$9 + 0 >= 100 {printf "%s%s (%s ms)", s, $1, $9; s = ", "}' "$DF_DIR/D-legs.tsv" 2>/dev/null)"
  record "P: compare — the Day view's legs from $(basename "${DF_DIR:-none}") (leg: longest frame)" "$(awk -F'\t' '{printf "%s%s: %s ms", s, $1, $9; s = "; "}' "$DF_DIR/D-legs.tsv" 2>/dev/null)"
  assert_eq "P: compare — every leg's longest frame is under 100 ms: this run's legs (P1–P5)" "" "$OVER100"
  assert_eq "P: compare — … and the Day view's legs (D1 the open on the 200-event day, D2 its scroll)" "" "$D_OVER100"
  assert_ne "P: compare — … the Day view's legs were read (their count)" "0" "$(grep -c . "$DF_DIR/D-legs.tsv" 2>/dev/null || echo 0)"
  assert_eq "P: compare — the run's janky share (${3:-}%) is no more than the baseline's ($(echo "$BASE_GFX" | awk '{print $3}')%) + 5 points" "yes" "$(python3 -c "
import sys
try: print('yes' if float(sys.argv[1]) <= float(sys.argv[2]) + 5.0 else 'no (%s%% against %s%% + 5)' % (sys.argv[1], sys.argv[2]))
except Exception: print('no (unreadable)')" "${3:-x}" "$(echo "$BASE_GFX" | awk '{print $3}')")"
else
  assert_eq "P: janky frames ≤ 5 % over the run (phase 01's threshold, as a bound on the emulator)" "yes" "$(python3 -c "print('yes' if float('${3:-100}' or 100) <= 5.0 else 'no (${3:-}%)')")"
fi
fi   # legs M, W, P (not in a Dframes run)

if [ "$LEGS" = all ] || [ "$LEGS" = Dframes ]; then
# ----------------------------------------------------------------------------------------------- D: the 200-event day
log "--- D: a day with 200 events lists them scrollably in the Day view"
if [ "$LEGS" = Dframes ]; then c6; ensure_start; copen; ctap cal_bar:today 1.2; sleep 1; fi
LEGS_TSV="$ROW_DIR/D-legs.tsv"; : > "$LEGS_TSV"
adb shell dumpsys gfxinfo app.tileshell reset >/dev/null 2>&1
D_MARK="$(ring_mark)"
copen_day "$(clocal_ms "$BUSY_DAY" 00:30)"; sleep 1
gfx_leg D1-day-view-opens-on-the-200-event-day
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
  adb shell dumpsys gfxinfo app.tileshell reset >/dev/null 2>&1
  adb shell input swipe 540 1700 540 1000 700; sleep 0.9
  gfx_leg "D2-scroll-$(printf '%02d' "$(( i + 1 ))")"
done
SEEN="$(grep -c . <(sort -u "$ROW_DIR/D-ids.txt" | grep cal_event))"
note "distinct cal_event nodes reached in $i swipe(s) of the Day view: $SEEN"
assert_eq "D: it lists them scrollably — all 200 events of the day are reached by scrolling the Day view" "200" "$SEEN"
assert_ne "D: … the list did scroll (more than one screen of them)" "0" "$i"
log "leg D's frames (name, total frames, janky, slow UI thread, slow draw, 50th, 90th, 99th, the longest frame in ms, its stages):"; sed 's/^/   /' "$ROW_DIR/D-legs.tsv" | tee -a "$LOG" >/dev/null
record "D: the Day view opening on the 200-event day — its longest frame and that frame's stages (no verdict)" "$(awk -F'\t' '$1 ~ /^D1/ {print $9 " ms = " $10}' "$ROW_DIR/D-legs.tsv")"
record "D: the Day view's scroll — the longest frame of all its swipes and that frame's stages (no verdict)" "$(grep '^D2' "$ROW_DIR/D-legs.tsv" | sort -t$'\t' -k9,9 -g -r | head -1 | awk -F'\t' '{print $9 " ms in " $1 " = " $10}')"
record "D: the Day view opening — its first four frames (total ms = input + animation + traversal + draw | sync + issue + swap)" "$(python3 "$FRAMES" table "$ROW_DIR/D1-day-view-opens-on-the-200-event-day.framestats.txt" | head -4 | awk -F'\t' '{printf "%s#%s %s %s = %s + %s + %s + %s | %s + %s + %s", s, $1, $3, ($2 == "LATE" ? "(late)" : ""), $4, $5, $6, $7, $8, $9, $10; s = "; "}')"
record "D: the Day view opening — how many frames it took and how many were late" "$(python3 "$FRAMES" longest "$ROW_DIR/D1-day-view-opens-on-the-200-event-day.framestats.txt" | awk -F'\t' '{print $10 " frames, " $11 " late"}')"
record "D: leg D's frames summed (total frames / janky / slow UI thread)" "$(awk -F'\t' '{t += $2; j += $3; u += $4} END {printf "%d / %d / %d", t, j, u}' "$ROW_DIR/D-legs.tsv")"
LEGS_TSV="$ROW_DIR/P-legs.tsv"
fi   # leg D

if [ "$LEGS" = all ]; then
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
fi   # leg T

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
