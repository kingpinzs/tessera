#!/usr/bin/env bash
# Phase 14 E3 — pod content from fixtures, each asserted present and then gone; every per-pod read follows
# scroll_to_node (r3 V15). Expected strings are written here by hand (never taken from the shell's own formatters).
#   Agenda     phase 01's local qa calendar: "QA all day" today, "QA overlap one" later today, "QA tomorrow" at 9:30
#   Weather    phase 01 ITEM2's fixture report (weather_fixture.py 3 day) in the feed's cache, airplane mode on; then
#              clock + 61 min -> the X22 "Updated h:mm" row
#   Now playing phase 10's six-track fixture, the first track played; PLAY_PAUSE / NEXT / PREVIOUS; the title tap
#   Reminders  two typed reminders and a Whenever one from the Reminders page's add route
#   Launches   the agenda header, the weather header, a reminder row — each from its own MARK; C-6 after each
# Readings recorded in INDEX's Change Log (2026-09-29 PHASE 14 BUILD): the agenda time text is ReminderText.time's
# ("9:30 AM" on this 12-hour AVD); the all-day event's DAY0 is the UTC midnight of the device's LOCAL date.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# phase 01's ui.sh first (music_lib.sh's goto_pivot needs its dump / tap_id), then the floor, so lib.sh's own bounds win.
. "$HERE/../../phase-01/scripts/ui.sh"
. "$HERE/lib.sh"
. "$HERE/p14.sh"
P13="$P13S"
. "$P13S/reminders_fixture.sh"
. "$P01S/music_lib.sh"

row_begin E3 "pod content from fixtures, present then gone; the launches"

TZNAME="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
note "device time zone: $TZNAME; device time: $(adb shell date | tr -d '\r')"
# The device's time text, as ReminderText.time / DateFormat.getTimeFormat writes it on a 12-hour en-US device.
fmt_time() { TZ="$TZNAME" date -d "@$(( $1 / 1000 ))" '+%-I:%M %p'; }
fmt_clock() { TZ="$TZNAME" date -d "@$(( $1 / 1000 ))" '+%-I:%M'; }
# Android's ICU writes a narrow no-break space (U+202F) before AM / PM; compared as a plain space, recorded raw.
norm() { sed 's/\xe2\x80\xaf/ /g; s/&amp;/\&/g'; }
row_text() { node_text "$1" "$2" | norm; }
open_bay_at() { # pod-id out.xml: the pod bay from Start, scrolled until the pod is laid out
  ensure_start
  swipe_right
  scroll_to_node "$2" "pod:$1"
}

# ============================================================ Agenda
q="$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_CALENDAR | tr -d '\r')"
printf '%s\n' "$q" > "$ROW_DIR/calendar-handlers.txt"
assert_eq "precondition: exactly one APP_CALENDAR activity" "1 activities found:" "$(printf '%s\n' "$q" | head -1)"
CAL_COMPONENT="$(printf '%s\n' "$q" | sed -n '/Activity #0/{n;n;p}' | tr -d ' ')"
note "the CALENDAR slot's app, listed on the host: $CAL_COMPONENT"

adb shell content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" \
  --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:qa --bind calendar_displayName:s:QA \
  --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind sync_events:i:1 --bind visible:i:1 --bind calendar_timezone:s:UTC
CAL="$(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=qa" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
assert_ne "the qa calendar exists" "" "$CAL"
NOW="$(adb shell date +%s%3N | tr -d '\r')"
LOCALDATE="$(adb shell date +%Y-%m-%d | tr -d '\r')"
DAY0=$(( $(date -u -d "$LOCALDATE" +%s) * 1000 ))
# "QA overlap one": the next 5-minute mark at least 10 minutes out, 30 minutes long, beginning today (local).
OV=$(( ( (NOW / 1000 + 600 + 299) / 300 ) * 300 * 1000 ))
OV_DATE="$(TZ="$TZNAME" date -d "@$(( OV / 1000 ))" +%Y-%m-%d)"
assert_eq "precondition: QA overlap one begins today (run before 23:45 local)" "$LOCALDATE" "$OV_DATE"
# Tomorrow's date first, then 09:30 in the device's zone ("<date> 09:30 + 1 day" reads "+ 1" as a UTC offset: run 1
# inserted QA tomorrow at 2:30 AM, which the pod faithfully showed).
TOMDATE="$(date -d "$LOCALDATE +1 day" +%F)"
TOM930=$(( $(TZ="$TZNAME" date -d "$TOMDATE 09:30" +%s) * 1000 ))
note "DAY0=$DAY0 (UTC midnight of $LOCALDATE) OV=$OV ($(fmt_time "$OV")) TOM930=$TOM930 ($(TZ="$TZNAME" date -d "@$((TOM930/1000))"))"
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:"$CAL" --bind "title:s:QA\\ all\\ day" \
  --bind dtstart:l:$DAY0 --bind dtend:l:$((DAY0 + 86400000)) --bind allDay:i:1 --bind eventTimezone:s:UTC
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:"$CAL" --bind "title:s:QA\\ overlap\\ one" \
  --bind dtstart:l:$OV --bind dtend:l:$((OV + 1800000)) --bind "eventTimezone:s:$TZNAME"
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:"$CAL" --bind "title:s:QA\\ tomorrow" \
  --bind dtstart:l:$TOM930 --bind dtend:l:$((TOM930 + 1800000)) --bind "eventTimezone:s:$TZNAME"
adb shell content query --uri content://com.android.calendar/events --projection _id:title:dtstart:allDay --where "calendar_id=$CAL" > "$ROW_DIR/agenda-events.txt"
assert_eq "three QA events inserted" "3" "$(grep -c '^Row:' "$ROW_DIR/agenda-events.txt")"
sleep 4
MARK="$(ring_mark)"
open_bay_at agenda "$ROW_DIR/agenda.xml"
assert_rows_match "$ROW_DIR/agenda.xml" agenda "$(ring_since "$ROW_MARK")"
screencap "$ROW_DIR/agenda.png"
assert_eq "agenda row 0" "All day  QA all day" "$(row_text "$ROW_DIR/agenda.xml" pod_row:agenda:0)"
assert_eq "agenda row 1" "$(fmt_time "$OV")  QA overlap one" "$(row_text "$ROW_DIR/agenda.xml" pod_row:agenda:1)"
assert_eq "agenda row 2" "9:30 AM  QA tomorrow" "$(row_text "$ROW_DIR/agenda.xml" pod_row:agenda:2)"
record "agenda row 1 raw text (U+202F shown as \\u202f)" "$(node_text "$ROW_DIR/agenda.xml" pod_row:agenda:1 | python3 -c 'import sys; print(sys.stdin.read().strip().encode("unicode_escape").decode())')"
assert_eq "the Tomorrow subheader is there" "Tomorrow" "$(node_text "$ROW_DIR/agenda.xml" pod_subheader:agenda:tomorrow)"
st="$(bounds "$ROW_DIR/agenda.xml" pod_subheader:agenda:tomorrow | cut -d' ' -f2)"; r1="$(bounds "$ROW_DIR/agenda.xml" pod_row:agenda:1 | cut -d' ' -f4)"; r2="$(bounds "$ROW_DIR/agenda.xml" pod_row:agenda:2 | cut -d' ' -f2)"
assert_eq "the subheader sits between row 1 and row 2" "yes" "$([ -n "$st" ] && [ "$r1" -le "$st" ] && [ "$st" -lt "$r2" ] && echo yes || echo "no ($r1 $st $r2)")"
assert_eq "exactly three agenda rows" "no" "$(has_node "$ROW_DIR/agenda.xml" pod_row:agenda:3)"
assert_contains "[podbay] pod agenda: 3 rows" "[podbay] pod agenda: 3 rows" "$(ring_since "$ROW_MARK")"
# Gone: the events and the qa calendar deleted.
adb shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" --where "_id=$CAL"
sleep 4
open_bay_at agenda "$ROW_DIR/agenda-gone.xml"
assert_eq "agenda gone: no row 0" "no" "$(has_node "$ROW_DIR/agenda-gone.xml" pod_row:agenda:0)"
assert_eq "agenda gone: the empty line" "Nothing on your calendar today" "$(row_text "$ROW_DIR/agenda-gone.xml" pod_empty:agenda)"
assert_eq "the qa calendar is gone" "0" "$(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name | grep -c 'name=qa')"

# ============================================================ Weather
ring_save
adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1
sleep 3
assert_eq "weather: airplane mode on (ITEM2's route: no fetch replaces the fixture)" "1" "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
python3 "$P01S/weather_fixture.py" 3 day "$ROW_DIR/weather-fixture.json"
FETCHED="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["fetchedAtMs"])' "$ROW_DIR/weather-fixture.json")"
adb shell am force-stop app.tileshell
adb shell run-as app.tileshell mkdir -p files/weather
adb shell "run-as app.tileshell sh -c 'cat > files/weather/last-report.json'" < "$ROW_DIR/weather-fixture.json"
adb shell run-as app.tileshell rm -f files/weather/last-report.json.bak
adb shell am force-stop app.tileshell
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 5
open_bay_at weather "$ROW_DIR/weather.xml"
assert_rows_match "$ROW_DIR/weather.xml" weather "$(ring_since "$ROW_MARK")"
screencap "$ROW_DIR/weather.png"
w="$(for n in 0 1 2 3 4; do row_text "$ROW_DIR/weather.xml" "pod_row:weather:$n"; done | tr '\n' '|')"
note "weather rows: $w"
python3 - "$w" <<'PY'
import sys
rows = [r for r in sys.argv[1].split("|") if r]
want = ["Denver", "64°", "Cloudy", "H 72° L 51°", "30%"]
joined = "\n".join(rows)
pos, ok = 0, True
for t in want:
    i = joined.find(t, pos)
    if i < 0: ok = False; break
    pos = i + len(t)
sys.exit(0 if ok else 1)
PY
if [ $? -eq 0 ]; then _verdict PASS "weather rows hold Denver, 64°, Cloudy, H 72° L 51°, 30% in order" "$w"; else _verdict FAIL "weather rows hold Denver, 64°, Cloudy, H 72° L 51°, 30% in order" "$w"; fi
assert_absent "not stale yet: no Updated row" "Updated" "$w"
# Clock + 61 minutes (e9_e12_clock.sh's form); the MARK after the jump, on the new clock (C-20).
T0="$(adb shell date +%s | tr -d '\r')"
HOST0="$(date +%s)"
adb root >/dev/null 2>&1; adb wait-for-device; sleep 2
adb shell settings put global auto_time 0
adb shell "date -u $(date -u -d @$((T0 + 61 * 60)) +%m%d%H%M%Y.%S)" >/dev/null
note "clock: $(adb shell date | tr -d '\r') (+61 min)"
MARK="$(ring_mark)"
want_updated="Updated $(fmt_clock "$FETCHED")"
found=""
for i in $(seq 1 18); do
  sleep 10
  dump_ui "$ROW_DIR/weather-stale.xml"
  if [ "$(has_node "$ROW_DIR/weather-stale.xml" pod:weather)" = no ]; then scroll_to_node "$ROW_DIR/weather-stale.xml" pod:weather >/dev/null; fi
  w2="$(for n in 0 1 2 3 4; do row_text "$ROW_DIR/weather-stale.xml" "pod_row:weather:$n"; done | tr '\n' '|')"
  case "$w2" in *"$want_updated"*) found="$w2"; break ;; esac
done
note "stale rows after $((i * 10)) s: ${w2:-}"
assert_contains "clock + 61 min: the X22 row within 180 s" "$want_updated" "$found"
screencap "$ROW_DIR/weather-stale.png"
# Restore: the clock to the host's now, automatic time, root off, the network back.
adb shell "date -u $(date -u +%m%d%H%M%Y.%S)" >/dev/null
adb shell settings put global auto_time 1
adb unroot >/dev/null 2>&1; adb wait-for-device; sleep 3
adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
sleep 5
assert_eq "restore: automatic time on" "1" "$(adb shell settings get global auto_time | tr -d '\r')"
assert_eq "restore: airplane mode off" "0" "$(adb shell settings get global airplane_mode_on | tr -d '\r')"
note "restore: device clock $(adb shell date +%s | tr -d '\r') vs host $(date +%s)"

# ============================================================ Now playing
music_fixtures
music_open
goto_pivot songs "$ROW_DIR/music-songs.xml" >/dev/null
BLOOM="$(python3 - "$ROW_DIR/music-songs.xml" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    if 'text="Bloom"' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n); x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)"
assert_ne "the first fixture (Bloom) is on the songs list" "" "$BLOOM"
adb shell input tap $BLOOM
sleep 4
adb shell input keyevent KEYCODE_HOME
sleep 3
MARK="$(ring_mark)"
open_bay_at nowplaying "$ROW_DIR/np.xml"
assert_rows_match "$ROW_DIR/np.xml" nowplaying "$(ring_since "$ROW_MARK")"
screencap "$ROW_DIR/np-playing.png"
assert_eq "now playing: the first fixture's title" "Bloom" "$(row_text "$ROW_DIR/np.xml" pod_row:nowplaying:0)"
assert_eq "now playing: its artist" "Radiohead" "$(row_text "$ROW_DIR/np.xml" pod_subline:nowplaying:0)"
for c in PREVIOUS PLAY_PAUSE NEXT; do assert_eq "control $c present" "yes" "$(has_node "$ROW_DIR/np.xml" "pod_control:nowplaying:$c")"; done
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np.xml" pod_control:nowplaying:PLAY_PAUSE
sleep 2
adb shell dumpsys media_session > "$ROW_DIR/ms-paused.txt"
assert_contains "PLAY_PAUSE: the session is PAUSED" "PAUSED" "$(session_state "$ROW_DIR/ms-paused.txt")"
assert_contains "[music] control PLAY_PAUSE -> app.tileshell" "[music] control PLAY_PAUSE -> app.tileshell" "$(ring_since "$MARK")"
dump_ui "$ROW_DIR/np-paused.xml"
screencap "$ROW_DIR/np-paused.png"
note "np-paused.png: the play glyph shown while paused (read by eye; H3)"
assert_eq "paused: the pod still present" "yes" "$(has_node "$ROW_DIR/np-paused.xml" pod:nowplaying)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np-paused.xml" pod_control:nowplaying:NEXT
sleep 2
dump_ui "$ROW_DIR/np-next.xml"
assert_eq "NEXT: the second fixture's title" "Codex" "$(row_text "$ROW_DIR/np-next.xml" pod_row:nowplaying:0)"
assert_contains "[music] control NEXT -> app.tileshell" "[music] control NEXT -> app.tileshell" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np-next.xml" pod_control:nowplaying:PREVIOUS   # inside 3 s of NEXT: the previous item, not a restart
sleep 2
dump_ui "$ROW_DIR/np-prev.xml"
assert_eq "PREVIOUS: the first title again" "Bloom" "$(row_text "$ROW_DIR/np-prev.xml" pod_row:nowplaying:0)"
assert_contains "[music] control PREVIOUS -> app.tileshell" "[music] control PREVIOUS -> app.tileshell" "$(ring_since "$MARK")"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np-prev.xml" pod_row:nowplaying:0
sleep 3
assert_eq "the title tap: the MUSIC slot app resumed" "app.tileshell/.music.MusicActivity" "$(top_activity)"
assert_contains "[podbay] launch nowplaying -> ..." "[podbay] launch nowplaying -> app.tileshell/.music.MusicActivity" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_HOME
sleep 2
swipe_right
# The stop has to reach the shell's session: Android sends a media key to its media-button session, which a PAUSED
# session may not hold (run 2: the stop reached nothing). The pod's own PLAY_PAUSE resumes it first — the play direction.
dump_ui "$ROW_DIR/np-before-stop.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np-before-stop.xml" pod_control:nowplaying:PLAY_PAUSE
sleep 2
adb shell dumpsys media_session > "$ROW_DIR/ms-resumed.txt"
assert_contains "PLAY_PAUSE again: the session is PLAYING" "PLAYING" "$(session_state "$ROW_DIR/ms-resumed.txt")"
record "media button session before the stop" "$(grep -m1 'Media button session' "$ROW_DIR/ms-resumed.txt" | sed 's/^ *//')"
MARK="$(ring_mark)"
adb shell cmd media_session dispatch stop
sleep 3
adb shell dumpsys media_session > "$ROW_DIR/ms-stopped.txt"
record "the shell's session after the stop" "$(session_state "$ROW_DIR/ms-stopped.txt" | sed 's/^ *//' | cut -c1-90)"
scroll_to_node "$ROW_DIR/np-stopped.xml" pod:nowplaying >/dev/null
assert_eq "stopped: Nothing playing" "Nothing playing" "$(row_text "$ROW_DIR/np-stopped.xml" pod_empty:nowplaying)"
note "stop slice: $(ring_since "$MARK" | grep -E '\[music\]|\[podbay\]' | cut -c40- | tr '\n' ';' | cut -c1-400)"
c6

# ============================================================ Reminders (typed, r3 V6)
# The QA14 reminders, read from the store file — the source of truth — and deleted through the product's own route,
# the row's hold menu (Delete). The Reminders page can leave a row's TITLE node out of the dump (run 1), so the row is
# found by its reminder_row:<id> node.
qa14_ids() {
  adb shell run-as app.tileshell cat files/cortana_reminders.json 2>/dev/null | python3 -c '
import json, sys
try: d = json.load(sys.stdin)
except Exception: sys.exit(0)
rs = d if isinstance(d, list) else d.get("reminders", [])
for r in rs:
    if "qa14" in (r.get("text") or "").lower() and not r.get("completed"): print(r["id"])'
}
qa14_texts() {
  adb shell run-as app.tileshell cat files/cortana_reminders.json 2>/dev/null | python3 -c '
import json, sys
try: d = json.load(sys.stdin)
except Exception: sys.exit(0)
rs = d if isinstance(d, list) else d.get("reminders", [])
print(" | ".join(r.get("text", "") for r in rs if "qa14" in (r.get("text") or "").lower() and not r.get("completed")))'
}
delete_qa14() {
  local i id b
  for i in 1 2 3 4 5 6; do
    id="$(qa14_ids | head -1)"
    [ -n "$id" ] || return 0
    open_reminders
    b="$(bounds "$ROW_DIR/reminders.xml" "reminder_row:$id")"
    if [ -z "$b" ]; then note "delete_qa14: row $id not in the dump"; cortana_close; continue; fi
    set -- $b
    delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    cortana_close
  done
}
delete_qa14
assert_eq "precondition: no QA14 reminder before the fixture" "" "$(qa14_ids)"
HM_MS=$(( ( ($(adb shell date +%s | tr -d '\r') + 900 + 299) / 300 ) * 300 * 1000 ))
assert_eq "precondition: the today reminder's time is still today" "$(adb shell date +%Y-%m-%d | tr -d '\r')" "$(TZ="$TZNAME" date -d "@$((HM_MS / 1000))" +%Y-%m-%d)"
HM="$(TZ="$TZNAME" date -d "@$((HM_MS / 1000))" '+%-I:%M')"; AP="$(TZ="$TZNAME" date -d "@$((HM_MS / 1000))" '+%P')"
# The doc's utterance, run and RECORDED: phase 03's TimeWords.parseTime consumes one range from the day word to the end of
# the time, so "check the QA14 today list at <h:mm> pm" is stored as "check the qa14" (run 1; phase 03's matcher, out of
# this phase's scope — INDEX phase 03 row). The fixture then uses the form phase 13's fixture already uses for tomorrow
# ("... tomorrow list tomorrow at 9 am"): "... today list today at <h:mm> pm".
make_typed_reminder "remind me to check the QA14 today list at $HM $AP"
record "the doc's utterance \"...today list at $HM $AP\" is stored as" "$(qa14_texts)"
delete_qa14
assert_eq "that reminder deleted again" "" "$(qa14_ids)"
make_typed_reminder "remind me to check the QA14 today list today at $HM $AP"
make_typed_reminder "remind me to check the QA14 tomorrow list tomorrow at 9 am"
open_reminders
tap_node "$ROW_DIR/reminders.xml" reminders_appbar_add
sleep 2
dump_ui "$ROW_DIR/.reminder-new.xml"
tap_node "$ROW_DIR/.reminder-new.xml" reminder_page_text
sleep 1
adb shell input text "check%sthe%sQA14%swhenever"
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
dump_ui "$ROW_DIR/.reminder-page.xml"
tap_node "$ROW_DIR/.reminder-page.xml" reminder_appbar_save
sleep 2
cortana_close
MARK="$(ring_mark)"
open_bay_at reminders "$ROW_DIR/reminders-pod.xml"
assert_rows_match "$ROW_DIR/reminders-pod.xml" reminders "$(ring_since "$ROW_MARK")"
screencap "$ROW_DIR/reminders-pod.png"
# The typed titles pass through phase 03's matcher, which lower-cases a request as it does a transcript: compared
# case-insensitively (the Reminders page shows the same text).
lc() { tr '[:upper:]' '[:lower:]'; }
assert_eq "reminders row 0" "check the qa14 today list" "$(row_text "$ROW_DIR/reminders-pod.xml" pod_row:reminders:0 | lc)"
assert_eq "reminders row 0 subline" "Today at $HM $(echo "$AP" | tr a-z A-Z)" "$(row_text "$ROW_DIR/reminders-pod.xml" pod_subline:reminders:0)"
assert_eq "reminders row 1" "check the qa14 tomorrow list" "$(row_text "$ROW_DIR/reminders-pod.xml" pod_row:reminders:1 | lc)"
assert_eq "reminders row 1 subline" "Tomorrow at 9:00 AM" "$(row_text "$ROW_DIR/reminders-pod.xml" pod_subline:reminders:1)"
assert_eq "reminders row 2" "check the QA14 whenever" "$(row_text "$ROW_DIR/reminders-pod.xml" pod_row:reminders:2)"
assert_eq "reminders row 2: no subline" "no" "$(has_node "$ROW_DIR/reminders-pod.xml" pod_subline:reminders:2)"

# ============================================================ Launches (each from its own MARK; C-6 after each)
ensure_start; swipe_right; dump_ui "$ROW_DIR/launch-top.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/launch-top.xml" pod_header:agenda
sleep 4
assert_eq "agenda header: the CALENDAR slot's app resumed" "$CAL_COMPONENT" "$(top_activity)"
assert_contains "[podbay] launch agenda -> $CAL_COMPONENT" "[podbay] launch agenda -> $CAL_COMPONENT" "$(ring_since "$MARK")"
c6
ensure_start; swipe_right; dump_ui "$ROW_DIR/launch-top2.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/launch-top2.xml" pod_header:weather
sleep 4
assert_eq "weather header: WeatherActivity resumed" "app.tileshell/.weather.WeatherActivity" "$(top_activity)"
assert_contains "[podbay] launch weather -> ..." "[podbay] launch weather -> app.tileshell/.weather.WeatherActivity" "$(ring_since "$MARK")"
c6
open_bay_at reminders "$ROW_DIR/launch-rem.xml"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/launch-rem.xml" pod_row:reminders:0
sleep 4
dump_ui "$ROW_DIR/launch-rem-after.xml"
assert_eq "reminder row: Tess's session is showing" "yes" "$(has_node "$ROW_DIR/launch-rem-after.xml" cortana_session)"
assert_eq "on her Reminders page" "yes" "$(has_node "$ROW_DIR/launch-rem-after.xml" reminders_appbar_add)"
assert_contains "[podbay] launch reminders -> ..." "[podbay] launch reminders -> app.tileshell/.cortana.CortanaService (Reminders)" "$(ring_since "$MARK")"
screencap "$ROW_DIR/launch-rem.png"
cortana_close
c6

# ============================================================ Reminders gone
delete_qa14
assert_eq "restore: no QA14 reminder left (the store)" "" "$(qa14_ids)"
open_bay_at reminders "$ROW_DIR/reminders-gone.xml"
assert_eq "reminders gone: the empty line" "No reminders — ask Tess to remind you" "$(row_text "$ROW_DIR/reminders-gone.xml" pod_empty:reminders)"
ensure_start
row_end
