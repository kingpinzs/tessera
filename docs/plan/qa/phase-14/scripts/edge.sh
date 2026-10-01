#!/usr/bin/env bash
# Phase 14 EDGE — the gate review's device checks and the Edge cases no E-row exercised (gate review r2's list), as far
# as the AVD can drive them:
#   W   Location OFF with the permission held and a cached report: the pod shows its location line and Start resuming or
#       the page coming into view sends NOTHING (no `refresh start`, no `fetch provider=`); Location back on → one
#       `refresh start reason=access restored` and the rows return (gate review r1 B1).
#   A   Calendar access granted with an EMPTY calendar: the access line gives way to the empty line on the next resume
#       (gate review r1 S3).
#   P1  the phrase while the pod bay is already open: the line is spoken, nothing moves.
#   P2  "close" on Start: the line is spoken, nothing moves.
#   P3  "open the bay" is phase 03's OpenApp, never the pod bay; "open the podbay doors" (one word) still opens it.
#   P4  the session dismissed mid-line: the utterance is cancelled, the pending open is consumed when focus returns.
#   R1  a pending request (Tess dismissed over another app) is dropped by another request; Start is not pulled over it.
#   R2  a pending request lapses after 30 s: Home afterwards is Start alone.
#   G1  a pan that starts on a bottom-row tile still opens the pod bay.
#   C   content limits: 20 events today → 6 agenda rows; a long title stays one line inside the pod.
# Typed requests only (no audio). Not drivable here, left to the owner's list: Home mid-swipe (this AVD does not re-deliver
# HOME to a resumed home activity — phone row P5's path), a reminder firing / completed while the pane shows, place and
# person reminders, a second request in one session, a pan during a flip, theme / accent / Show more tiles / the X5
# slider, a pod switched off while its feed updates.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin EDGE "gate-review device checks and the uncovered edge cases"
norm() { tr -s ' ' | sed 's/^ //; s/ $//'; }
row_text() { node_text "$1" "$2" | sed 's/  */ /g'; }
open_bay_at() { ensure_start; swipe_right; scroll_to_node "$2" "pod:$1" >/dev/null; }
loc_on() { adb shell cmd location is-location-enabled 2>/dev/null | tr -d '\r' | grep -oE 'true|false' | head -1; }
TZNAME="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
LOC0="$(loc_on)"
CAL=""
restore_all() {
  adb shell cmd location set-location-enabled "${LOC0:-true}" >/dev/null 2>&1
  adb shell pm grant app.tileshell android.permission.READ_CALENDAR >/dev/null 2>&1
  [ -n "$CAL" ] && adb shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" --where "_id=$CAL" >/dev/null 2>&1
  echo "      restored: location ${LOC0:-true}, calendar granted, QA calendar removed" >> "$LOG"
}
trap restore_all EXIT
# A resume of Start with no process restart: the Settings hub over it, then Back.
resume_start() { adb shell am start -n app.tileshell/.settings.SettingsActivity >/dev/null 2>&1; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2; }
count_in() { printf '%s\n' "$1" | grep -cF -- "$2"; }

# ============================================================ W: Location off sends nothing
log "W: Location off, the permission held, a cached report"
# The report must be there first: straight after E17's reset the feed has not fetched yet (the run of 22:40 began on "No
# weather yet", which makes "nothing left the device" true for the wrong reason). Waited for, up to 90 s.
for i in $(seq 1 9); do
  open_bay_at weather "$ROW_DIR/W-00-before.xml"
  [ "$(has_node "$ROW_DIR/W-00-before.xml" pod_row:weather:0)" = yes ] && break
  resume_start; sleep 8
done
assert_eq "W: before: a weather row (a report is cached)" "yes" "$(has_node "$ROW_DIR/W-00-before.xml" pod_row:weather:0)"
adb shell cmd location set-location-enabled false
assert_eq "W: Location is off" "false" "$(loc_on)"
ring_save launcher
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 10
open_bay_at weather "$ROW_DIR/W-01-off.xml"
assert_eq "W: the location line, with the report cached" "Location is off — turn it on in Setup" "$(row_text "$ROW_DIR/W-01-off.xml" pod_empty:weather)"
ensure_start
MW="$(ring_mark)"
for i in 1 2 3 4; do resume_start; done
swipe_right 2; swipe_left 2; swipe_right 2; swipe_left 2
s="$(ring_since "$MW")"; printf '%s\n' "$s" > "$ROW_DIR/W-02-resumes-slice.txt"
note "W: resumes in the slice: $(count_in "$s" '[bars] StartActivity') bars lines, pages: $(pages_in "$s")"
assert_eq "W: the slice saw Start resume at least four times" "yes" "$([ "$(count_in "$s" '[bars] StartActivity')" -ge 4 ] && echo yes || echo no)"
assert_contains "W: and the pod bay come into view" "[start] page=POD_BAY" "$s"
assert_eq "W: no weather refresh from the location line" "0" "$(count_in "$s" '[weather] refresh start')"
assert_eq "W: and no request left the device" "0" "$(count_in "$s" '[weather] fetch provider=')"
MW2="$(ring_mark)"
adb shell cmd location set-location-enabled true
assert_eq "W: Location is back on" "true" "$(loc_on)"
resume_start
ok=no
for i in 1 2 3 4 5 6; do
  open_bay_at weather "$ROW_DIR/W-03-on.xml"
  [ "$(has_node "$ROW_DIR/W-03-on.xml" pod_row:weather:0)" = yes ] && { ok=yes; break; }
  sleep 5
done
s="$(ring_since "$MW2")"; printf '%s\n' "$s" > "$ROW_DIR/W-03-on-slice.txt"
assert_contains "W: Location back on: the feed looks again" "[weather] refresh start reason=access restored" "$s"
assert_eq "W: and the rows return" "yes" "$ok"
ensure_start

# ============================================================ A: a grant with an empty calendar
log "A: calendar access granted with nothing on the calendar"
ring_save launcher
adb shell pm revoke app.tileshell android.permission.READ_CALENDAR
sleep 2
adb shell input keyevent KEYCODE_HOME; sleep 6
open_bay_at agenda "$ROW_DIR/A-01-denied.xml"
assert_eq "A: revoked: the access line" "Calendar access is off — turn it on in Setup" "$(row_text "$ROW_DIR/A-01-denied.xml" pod_empty:agenda)"
adb shell pm grant app.tileshell android.permission.READ_CALENDAR
resume_start
dump_ui "$ROW_DIR/A-02-granted.xml"
note "A: the page after the resume: pod_bay=$(has_node "$ROW_DIR/A-02-granted.xml" pod_bay)"
open_bay_at agenda "$ROW_DIR/A-02-granted.xml"
assert_eq "A: granted, nothing on the calendar: the empty line, not the access line" "Nothing on your calendar today" "$(row_text "$ROW_DIR/A-02-granted.xml" pod_empty:agenda)"
ensure_start

# ============================================================ P1-P4: the phrases
log "P1: the phrase while the pod bay is already open"
open_pod_bay "$ROW_DIR/P1-00.xml"
tess_open
MP="$(ring_mark)"
type_request "open the pod bay" 9
s="$(ring_since "$MP")"; printf '%s\n' "$s" > "$ROW_DIR/P1-slice.txt"
dump_ui "$ROW_DIR/P1-after.xml"
assert_eq "P1: the line is spoken" "Opening the pod bay." "$(reply_since "$MP")"
assert_eq "P1: still the pod bay" "yes" "$(has_node "$ROW_DIR/P1-after.xml" pod_bay)"
assert_eq "P1: nothing moved (no page line)" "0" "$(count_in "$s" '[start] page=')"
ensure_start

log "P2: close on Start"
tess_open
MP="$(ring_mark)"
type_request "close the pod bay" 9
s="$(ring_since "$MP")"; printf '%s\n' "$s" > "$ROW_DIR/P2-slice.txt"
dump_ui "$ROW_DIR/P2-after.xml"
assert_eq "P2: the line is spoken" "Closing the pod bay." "$(reply_since "$MP")"
assert_eq "P2: Start alone" "yes" "$(start_alone "$ROW_DIR/P2-after.xml")"
assert_eq "P2: nothing moved (no page line)" "0" "$(count_in "$s" '[start] page=')"

log "P3: open the pod and open the bay are OpenApp, exactly; one-word podbay still opens"
# The exact form of E6's spoken negative (ruled 2026-09-30): typed, the request is word for word what was asked.
tess_open
MP="$(ring_mark)"
type_request "open the pod" 9
s="$(ring_since "$MP")"; printf '%s\n' "$s" > "$ROW_DIR/P3p-slice.txt"
dump_ui "$ROW_DIR/P3p-after.xml"
assert_contains "P3: open the pod → OpenApp(name=the pod)" "\"open the pod\" -> OpenApp(name=the pod)" "$(match_line "$s")"
assert_eq "P3: its reply, exactly" "I don't see an app called the pod." "$(reply_since "$MP")"
absent_in "P3: no [podbay] opened" "[podbay] opened" "$s"
assert_eq "P3: no pod_bay" "no" "$(has_node "$ROW_DIR/P3p-after.xml" pod_bay)"
cortana_close; ensure_start
tess_open
MP="$(ring_mark)"
type_request "open the bay" 9
s="$(ring_since "$MP")"; printf '%s\n' "$s" > "$ROW_DIR/P3a-slice.txt"
dump_ui "$ROW_DIR/P3a-after.xml"
assert_contains "P3: open the bay → OpenApp" "-> OpenApp(name=the bay)" "$(match_line "$s")"
assert_eq "P3: its reply" "I don't see an app called the bay." "$(reply_since "$MP")"
assert_absent "P3: no [podbay] opened" "[podbay] opened" "$s"
cortana_close; ensure_start
tess_open
MP="$(ring_mark)"
type_request "open the podbay doors" 12
s="$(ring_since "$MP")"; printf '%s\n' "$s" > "$ROW_DIR/P3b-slice.txt"
dump_ui "$ROW_DIR/P3b-after.xml"
assert_contains "P3: one-word podbay → OpenPodBay(doors=true)" "-> OpenPodBay(doors=true)" "$(match_line "$s")"
assert_contains "P3: and the pod bay opens" "[podbay] opened by voice (doors)" "$s"
assert_eq "P3: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/P3b-after.xml" pod_bay)"
ensure_start

log "P4: the session dismissed mid-line"
tess_open
MP="$(ring_mark)"
type_request "open the pod bay doors" 1
# Two Backs: the first clears the result page (phase 03's Back rule — run 1 logged "result cleared" and Tess stayed, the
# line ran to its end); the second dismisses the session.
adb shell input keyevent KEYCODE_BACK; sleep 0.4
adb shell input keyevent KEYCODE_BACK
sleep 5
s="$(ring_since "$MP")"; printf '%s\n' "$s" > "$ROW_DIR/P4-slice.txt"
dump_ui "$ROW_DIR/P4-after.xml"
uid="$(printf '%s\n' "$s" | grep -F '[speech] speak[' | grep -F "I'm afraid" | head -1 | sed -E 's/.*speak\[([^]]+)\].*/\1/')"
assert_ne "P4: the doors line was started" "" "$uid"
assert_contains "P4: and cancelled by the dismissal" "speaking done $uid cancelled=true" "$s"
assert_contains "P4: the pending open is still consumed" "[podbay] opened by voice (doors)" "$s"
assert_eq "P4: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/P4-after.xml" pod_bay)"
assert_eq "P4: no session window" "no" "$(session_window)"
ensure_start

# ============================================================ R1, R2: a pending request does not wait for ever
# Owner ruling 2026-09-30 (gate review r1 S2). Tess over DeskClock, dismissed mid-line: focus goes back to DeskClock, not
# Start, so the request stays pending.
pending_over_clock() { # label
  adb shell am start -n com.android.deskclock/.DeskClock >/dev/null 2>&1; sleep 3
  tess_open
  type_request "open the pod bay doors" 1
  adb shell input keyevent KEYCODE_BACK; sleep 0.4
  adb shell input keyevent KEYCODE_BACK; sleep 3
  assert_contains "$1: Tess dismissed over DeskClock" "com.android.deskclock/" "$(top_activity)"
}
log "R1: another request drops the pending one"
MR="$(ring_mark)"
pending_over_clock R1
s="$(ring_since "$MR")"; printf '%s\n' "$s" > "$ROW_DIR/R1-pending-slice.txt"
assert_contains "R1: the request was recorded" "[podbay] request recorded: open (doors)" "$s"
absent_in "R1: and not consumed" "[podbay] opened" "$s"
tess_open
MR="$(ring_mark)"
type_request "what time is it" 9
s="$(ring_since "$MR")"; printf '%s\n' "$s" > "$ROW_DIR/R1-other-slice.txt"
assert_contains "R1: the other request drops it" "[podbay] request dropped: open (doors) (another request)" "$s"
assert_contains "R1: and is answered" "It's " "$(reply_since "$MR")"
assert_contains "R1: Start was not pulled over its result (DeskClock is still on top)" "com.android.deskclock/" "$(top_activity)"
absent_in "R1: nothing opened" "[podbay] opened" "$s"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump_ui "$ROW_DIR/R1-home.xml"
assert_eq "R1: Home afterwards is Start alone" "yes" "$(start_alone "$ROW_DIR/R1-home.xml")"

log "R2: a pending request lapses after 30 s"
MR="$(ring_mark)"
pending_over_clock R2
sleep 33
adb shell input keyevent KEYCODE_HOME; sleep 4
dump_ui "$ROW_DIR/R2-home.xml"
s="$(ring_since "$MR")"; printf '%s\n' "$s" > "$ROW_DIR/R2-slice.txt"
assert_contains "R2: the request was recorded" "[podbay] request recorded: open (doors)" "$s"
assert_contains "R2: it lapsed" "[podbay] request lapsed: open (doors) after " "$s"
absent_in "R2: nothing opened" "[podbay] opened" "$s"
assert_eq "R2: Home is Start alone, not the pod bay" "yes" "$(start_alone "$ROW_DIR/R2-home.xml")"
ring_save launcher
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
ensure_start

# ============================================================ G1: a pan from a bottom-row tile
log "G1: a pan that starts on a bottom-row tile"
dump_ui "$ROW_DIR/G1-start.xml"
dock="$(grep -oE 'resource-id="tile:dock:[^"]*"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$ROW_DIR/G1-start.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | grep -oE '[0-9]+' | tr '\n' ' ')"
assert_ne "G1: a bottom-row tile on Start" "" "$dock"
set -- $dock
dx=$(( ($1 + $3) / 2 )); dy=$(( ($2 + $4) / 2 ))
MG="$(ring_mark)"
adb shell input swipe "$dx" "$dy" $(( dx + 750 )) "$dy" 250; sleep 2
dump_ui "$ROW_DIR/G1-after.xml"
s="$(ring_since "$MG")"; printf '%s\n' "$s" > "$ROW_DIR/G1-slice.txt"
note "G1: swipe from the dock tile at $dx,$dy"
assert_eq "G1: the pod bay opens" "yes" "$(has_node "$ROW_DIR/G1-after.xml" pod_bay)"
assert_contains "G1: opened by swipe" "[podbay] opened by swipe" "$s"
assert_absent "G1: and the tile was not launched" "[launch] tile=" "$s"
ensure_start

# ============================================================ C: content limits
log "C: 20 events today → 6 rows; a long title stays one line"
adb shell content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" \
  --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:qa --bind calendar_displayName:s:QA \
  --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind sync_events:i:1 --bind visible:i:1 --bind calendar_timezone:s:UTC
CAL="$(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=qa" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
assert_ne "C: the QA calendar" "" "$CAL"
NOW="$(adb shell date +%s%3N | tr -d '\r')"
END_OF_DAY="$(( $(TZ="$TZNAME" date -d "$(TZ="$TZNAME" date -d "@$((NOW / 1000))" +%Y-%m-%d) 23:59:00" +%s) * 1000 ))"
room=$(( (END_OF_DAY - NOW) / 60000 ))
note "C: $room minutes left today"
step=$(( room > 80 ? 3 : 1 ))
LONG="QA long title that goes on and on well past the width of the pod so that it has to be cut on one line with an ellipsis at the end"
n=0
for i in $(seq 1 20); do
  t=$(( NOW + (i * step + 2) * 60000 ))
  [ "$t" -lt "$END_OF_DAY" ] || break
  title="QA event $i"; [ "$i" = 1 ] && title="$LONG"
  adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:"$CAL" --bind "title:s:$(printf '%s' "$title" | sed 's/ /\\ /g')" \
    --bind dtstart:l:$t --bind dtend:l:$((t + 60000)) --bind "eventTimezone:s:$TZNAME" >/dev/null
  n=$((n + 1))
done
note "C: $n events inserted"
assert_eq "C: more events than the cap" "yes" "$([ "$n" -gt 6 ] && echo yes || echo no)"
# After case A's revoke and grant the feed reads the provider on its minute tick only (the run of 21:55 looked 20 s
# after the inserts and found the empty line; the tick a minute later would have had the rows) — so the rows are
# waited for, up to 80 s.
rows=0
for i in $(seq 1 16); do
  sleep 5
  open_bay_at agenda "$ROW_DIR/C-01-agenda.xml"
  rows="$(grep -oE 'resource-id="pod_row:agenda:[0-9]+"' "$ROW_DIR/C-01-agenda.xml" | sort -u | wc -l | tr -d ' ')"
  [ "$rows" -ge 6 ] && break
done
note "C: rows after $(( i * 5 )) s (plus the dumps): $rows"
screencap "$ROW_DIR/C-01-agenda.png"
assert_eq "C: the Agenda pod shows its cap of 6 rows" "6" "$rows"
read -r l0 t0 r0 b0 <<< "$(bounds "$ROW_DIR/C-01-agenda.xml" pod_row:agenda:0)"
read -r l1 t1 r1 b1 <<< "$(bounds "$ROW_DIR/C-01-agenda.xml" pod_row:agenda:1)"
read -r pl pt pr pb <<< "$(bounds "$ROW_DIR/C-01-agenda.xml" pod:agenda)"
note "C: long row [$l0 $t0 $r0 $b0] next row [$l1 $t1 $r1 $b1] pod [$pl $pt $pr $pb]"
assert_contains "C: row 0 is the long title" "QA long title" "$(row_text "$ROW_DIR/C-01-agenda.xml" pod_row:agenda:0)"
assert_eq "C: the long title is one line (the next row's height)" "$(( b1 - t1 ))" "$(( b0 - t0 ))"
assert_eq "C: and stays inside the pod" "yes" "$([ "$r0" -le "$pr" ] && echo yes || echo no)"

restore_all
CAL=""
trap - EXIT
sleep 3
ensure_start
RINGS="launcher" row_end
