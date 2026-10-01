#!/usr/bin/env bash
# Phase 14 E4 — denied and empty states with their diagnostics, both directions; each runtime revoke kills the process
# and resets the ring, so each is preceded by ring_save and a MARK (the MARK still slices the new ring, r3 V4). Then the
# two launch failures (T14-13): an unassigned MUSIC slot (the picker opens, and the line says why) and no assistant role
# (the role notice, and the line says why).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/../../phase-01/scripts/ui.sh"
. "$HERE/lib.sh"
. "$HERE/p14.sh"
P13="$P13S"
. "$P13S/reminders_fixture.sh"
. "$P01S/music_lib.sh"

row_begin E4 "denied and empty states with diagnostics; the launch failures"
norm() { sed 's/\xe2\x80\xaf/ /g'; }
row_text() { node_text "$1" "$2" | norm; }
open_bay_at() { ensure_start; swipe_right; scroll_to_node "$2" "pod:$1"; }
rows_of() { grep -oE "resource-id=\"pod_row:$2:[0-9]+\"" "$1" | sort -u | wc -l | tr -d ' '; }
last_line() { printf '%s\n' "$1" | grep -F "[podbay] pod $2: " | tail -1 | sed 's/.*\[podbay\] //'; }
TZNAME="$(adb shell getprop persist.sys.timezone | tr -d '\r')"
restore_all() {
  adb shell pm grant app.tileshell android.permission.READ_CALENDAR >/dev/null 2>&1
  adb shell pm grant app.tileshell android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1
  adb shell pm grant app.tileshell android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1
  adb shell pm grant app.tileshell android.permission.ACCESS_BACKGROUND_LOCATION >/dev/null 2>&1
  adb shell cmd role add-role-holder android.app.role.ASSISTANT app.tileshell >/dev/null 2>&1
  adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
  [ -n "${CAL:-}" ] && adb shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" --where "_id=$CAL" >/dev/null 2>&1
}
trap restore_all EXIT

# ============================================================ Calendar denied, and back
# One QA event later today, so "the rows return" after the grant has a row to return.
adb shell content insert --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" \
  --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:qa --bind calendar_displayName:s:QA \
  --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind sync_events:i:1 --bind visible:i:1 --bind calendar_timezone:s:UTC
CAL="$(adb shell content query --uri content://com.android.calendar/calendars --projection _id:name | grep "name=qa" | grep -oE '_id=[0-9]+' | cut -d= -f2 | head -1)"
NOW="$(adb shell date +%s%3N | tr -d '\r')"
EV=$(( ( (NOW / 1000 + 600 + 299) / 300 ) * 300 * 1000 ))
assert_eq "precondition: the QA event begins today" "$(adb shell date +%Y-%m-%d | tr -d '\r')" "$(TZ="$TZNAME" date -d "@$((EV / 1000))" +%Y-%m-%d)"
adb shell content insert --uri content://com.android.calendar/events --bind calendar_id:i:"$CAL" --bind "title:s:QA\\ E4\\ event" \
  --bind dtstart:l:$EV --bind dtend:l:$((EV + 1800000)) --bind "eventTimezone:s:$TZNAME"
sleep 4
open_bay_at agenda "$ROW_DIR/agenda-before.xml"
assert_eq "agenda before the revoke: the QA row" "$(TZ="$TZNAME" date -d "@$((EV / 1000))" '+%-I:%M %p')  QA E4 event" "$(row_text "$ROW_DIR/agenda-before.xml" pod_row:agenda:0)"

ring_save
MARK="$(ring_mark)"
adb shell pm revoke app.tileshell android.permission.READ_CALENDAR
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 5
open_bay_at agenda "$ROW_DIR/agenda-denied.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/agenda-denied-slice.txt"
assert_eq "calendar revoked: the access line" "Calendar access is off — turn it on in Setup" "$(row_text "$ROW_DIR/agenda-denied.xml" pod_empty:agenda)"
assert_eq "calendar revoked: no agenda row" "no" "$(has_node "$ROW_DIR/agenda-denied.xml" pod_row:agenda:0)"
assert_contains "[podbay] pod agenda: empty: no calendar access" "[podbay] pod agenda: empty: no calendar access" "$s"
screencap "$ROW_DIR/agenda-denied.png"
tap_node "$ROW_DIR/agenda-denied.xml" pod_empty:agenda
sleep 3
assert_eq "the access line opens SettingsActivity" "app.tileshell/.settings.SettingsActivity" "$(top_activity)"
scroll_to_node "$ROW_DIR/checklist.xml" checklist:calendar:missing >/dev/null
assert_eq "on the checklist, the Calendar row (missing)" "yes" "$(has_node "$ROW_DIR/checklist.xml" checklist:calendar:missing)"
adb shell pm grant app.tileshell android.permission.READ_CALENDAR
MARK="$(ring_mark)"
ok=no
for i in $(seq 1 9); do
  sleep 10
  open_bay_at agenda "$ROW_DIR/agenda-granted.xml" >/dev/null
  [ "$(has_node "$ROW_DIR/agenda-granted.xml" pod_row:agenda:0)" = yes ] && { ok=yes; break; }
done
note "the row returned after $((i * 10)) s (the feed's own minute tick / the checklist's feed restart)"
assert_eq "pm grant: the agenda row returns" "yes" "$ok"
assert_contains "and its line says rows" "[podbay] pod agenda: 1 rows" "$(ring_since "$MARK")"
adb shell content delete --uri "content://com.android.calendar/calendars?caller_is_syncadapter=true\&account_name=qa\&account_type=LOCAL" --where "_id=$CAL"
CAL=""

# ============================================================ Location denied, and back
ring_save
MARK="$(ring_mark)"
adb shell pm revoke app.tileshell android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke app.tileshell android.permission.ACCESS_COARSE_LOCATION
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 8
open_bay_at weather "$ROW_DIR/weather-denied.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/weather-denied-slice.txt"
assert_eq "location revoked: the location line, even with a cached report" "Location is off — turn it on in Setup" "$(row_text "$ROW_DIR/weather-denied.xml" pod_empty:weather)"
assert_contains "the cached report was loaded (so it is the problem that wins)" "[weather] cache loaded" "$s"
assert_contains "[podbay] pod weather: empty: no location" "[podbay] pod weather: empty: no location" "$s"
screencap "$ROW_DIR/weather-denied.png"
adb shell pm grant app.tileshell android.permission.ACCESS_FINE_LOCATION
adb shell pm grant app.tileshell android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant app.tileshell android.permission.ACCESS_BACKGROUND_LOCATION
assert_contains "restore: FINE location granted" "android.permission.ACCESS_FINE_LOCATION: granted=true" "$(adb shell dumpsys package app.tileshell | tr -d '\r')"
MARK="$(ring_mark)"
ok=no
for i in $(seq 1 9); do
  sleep 10
  open_bay_at weather "$ROW_DIR/weather-granted.xml" >/dev/null
  [ "$(has_node "$ROW_DIR/weather-granted.xml" pod_row:weather:0)" = yes ] && { ok=yes; break; }
done
assert_eq "grant: the weather rows return" "yes" "$ok"
wl="$(last_line "$(ring_since "$ROW_MARK")" weather)"
assert_eq "the weather line's row count matches the dump's pod_row:weather nodes" "pod weather: $(rows_of "$ROW_DIR/weather-granted.xml" weather) rows" "$wl"

# ============================================================ Empty states: no session, no reminders, never fetched
ensure_start
swipe_right
MARK0="$(ring_mark)"
scroll_to_node "$ROW_DIR/empties.xml" pod:nowplaying >/dev/null
assert_eq "no session: Nothing playing" "Nothing playing" "$(row_text "$ROW_DIR/empties.xml" pod_empty:nowplaying)"
scroll_to_node "$ROW_DIR/empties-rem.xml" pod:reminders >/dev/null
assert_eq "no reminders: the empty line" "No reminders — ask Tess to remind you" "$(row_text "$ROW_DIR/empties-rem.xml" pod_empty:reminders)"
all="$(ring_since "$ROW_MARK")"
assert_contains "[podbay] pod nowplaying: empty: no session" "pod nowplaying: empty: no session" "$all"
assert_contains "[podbay] pod reminders: empty: none" "pod reminders: empty: none" "$all"
# Weather never fetched: no cache, no network (so no fetch lands one).
ring_save
adb shell cmd connectivity airplane-mode enable >/dev/null 2>&1
sleep 3
MARK="$(ring_mark)"
adb shell am force-stop app.tileshell
adb shell run-as app.tileshell rm -f files/weather/last-report.json files/weather/last-report.json.bak
adb shell am force-stop app.tileshell
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 8
open_bay_at weather "$ROW_DIR/weather-never.xml"
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/weather-never-slice.txt"
assert_eq "never fetched: No weather yet" "No weather yet" "$(row_text "$ROW_DIR/weather-never.xml" pod_empty:weather)"
assert_contains "[podbay] pod weather: empty: no report" "[podbay] pod weather: empty: no report" "$s"
adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
MARK="$(ring_mark)"
ok=no
for i in $(seq 1 12); do
  sleep 10
  open_bay_at weather "$ROW_DIR/weather-fetched.xml" >/dev/null
  [ "$(has_node "$ROW_DIR/weather-fetched.xml" pod_row:weather:0)" = yes ] && { ok=yes; break; }
done
assert_eq "restore: network back, a report fetched, the rows fill in place" "yes" "$ok"
assert_contains "restore: a real fetch landed" "[weather] fetch ok" "$(ring_since "$MARK")"

# ============================================================ Launch failure: MUSIC slot unassigned
ring_save
layout_save "$ROW_DIR/device-layout.json"
layout_restore "$P14/baseline_layout-nomusic.json"
assert_eq "layout: phase 02's baseline without slots.MUSIC" "0" "$?"
assert_eq "the MUSIC slot is unassigned in the store" "{}" "$(layout_json | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["slots"]))')"
music_fixtures
music_open
goto_pivot songs "$ROW_DIR/music-songs.xml" >/dev/null
BLOOM="$(python3 - "$ROW_DIR/music-songs.xml" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for n in re.finditer(r"<node[^>]*>", s):
    n = n.group(0)
    if 'text="Bloom"' in n:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)"
adb shell input tap $BLOOM
sleep 4
adb shell input keyevent KEYCODE_HOME
sleep 2
open_bay_at nowplaying "$ROW_DIR/np.xml"
assert_eq "the fixture is playing on the pod" "Bloom" "$(row_text "$ROW_DIR/np.xml" pod_row:nowplaying:0)"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/np.xml" pod_row:nowplaying:0
sleep 2
dump_ui "$ROW_DIR/np-picker.xml"
screencap "$ROW_DIR/np-picker.png"
assert_eq "unassigned MUSIC: the slot picker opens (the tap follows the tile)" "yes" "$(has_node "$ROW_DIR/np-picker.xml" slot_picker)"
assert_contains "[podbay] launch nowplaying failed: slot unassigned" "[podbay] launch nowplaying failed: slot unassigned" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK
sleep 1.5
dump_ui "$ROW_DIR/np-picker-closed.xml"
assert_eq "Back closes the picker" "no" "$(has_node "$ROW_DIR/np-picker-closed.xml" slot_picker)"
adb shell cmd media_session dispatch pause >/dev/null 2>&1
ring_save
layout_restore "$P02S/../baseline_layout.json"
assert_eq "layout: phase 02's baseline back" "0" "$?"

# ============================================================ Launch failure: not the assistant
make_typed_reminder "remind me to check the QA14 role list tomorrow at 9 am"
MARK="$(ring_mark)"
adb shell cmd role remove-role-holder android.app.role.ASSISTANT app.tileshell
sleep 3
assert_eq "the assistant role is gone" "" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
open_bay_at reminders "$ROW_DIR/rem.xml"
tap_node "$ROW_DIR/rem.xml" pod_row:reminders:0
sleep 3
s="$(ring_since "$MARK")"; printf '%s\n' "$s" > "$ROW_DIR/role-slice.txt"
assert_contains "[podbay] launch reminders failed: no session: not the assistant" "[podbay] launch reminders failed: no session: not the assistant" "$s"
assert_contains "phase 03's role-notice line" "cannot show a session" "$(printf '%s\n' "$s" | grep -F '[cortana] open(')"
assert_contains "... showing the role notice" "showing the role notice" "$(printf '%s\n' "$s" | grep -F '[cortana] open(')"
assert_eq "CortanaRoleNoticeActivity resumed (H30)" "app.tileshell/.cortana.CortanaRoleNoticeActivity" "$(top_activity)"
assert_eq "no session window" "no" "$(session_window)"
screencap "$ROW_DIR/role-notice.png"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 2
MARK="$(ring_mark)"
adb shell cmd role add-role-holder android.app.role.ASSISTANT app.tileshell
ok=no
for i in $(seq 1 10); do sleep 2; ring_since "$MARK" | grep -qF '[cortana] VoiceInteractionService ready' && { ok=yes; break; }; done
assert_eq "restore: [cortana] VoiceInteractionService ready" "yes" "$ok"
assert_eq "restore: the role holder is app.tileshell" "app.tileshell" "$(adb shell cmd role get-role-holders android.app.role.ASSISTANT | tr -d '\r')"
# The reminder, removed through its row's hold menu.
for _ in 1 2; do
  id="$(adb shell run-as app.tileshell cat files/cortana_reminders.json | python3 -c '
import json,sys
d=json.load(sys.stdin); rs=d if isinstance(d,list) else d.get("reminders",[])
print(next((r["id"] for r in rs if "qa14 role" in (r.get("text") or "").lower() and not r.get("completed")), ""))')"
  [ -n "$id" ] || break
  open_reminders
  set -- $(bounds "$ROW_DIR/reminders.xml" "reminder_row:$id")
  [ $# -eq 4 ] && delete_reminder_at $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
  cortana_close
done
assert_eq "restore: the QA14 role reminder is gone" "0" "$(adb shell run-as app.tileshell cat files/cortana_reminders.json | grep -ci 'qa14 role')"
ring_save
layout_restore "$ROW_DIR/device-layout.json"
ensure_start
trap - EXIT
restore_all
row_end
