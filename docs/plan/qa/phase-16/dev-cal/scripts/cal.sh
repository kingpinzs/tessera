#!/usr/bin/env bash
# Phase 16, the Calendar builder's development sessions: helpers sourced AFTER lib.sh. Development proof, not the gate
# (the lead writes and runs E1–E28 and EDGE from the doc).
#
# The emulator is shared with the People builder, who installs a different build of the same package. So every session
# is ONE script that takes the device lock FIRST (session_begin), installs this worktree's APK when the device holds
# another build, drives, restores what it changed and exits. Never pm clear, uninstall, reboot or relaunch the emulator.
export ANDROID_SERIAL=emulator-5554

# lib.sh computes REPO for a scripts folder one level under qa/phase-NN; this one is a level deeper (dev-cal/scripts).
REPO="$(cd "$QA/../../../../.." && pwd)"
APK="$REPO/app/build/outputs/apk/debug/app-debug.apk"
PX=3   # 1080 px / 360 epx on this AVD
CAL_ACT="app.tileshell/.calendar.CalendarActivity"
CALS=content://com.android.calendar/calendars
EVENTS=content://com.android.calendar/events
SA="caller_is_syncadapter=true"

S() { adb shell "$@" < /dev/null | tr -d '\r'; }

# The lock first, then this build on the device, then the row's header (row_begin re-takes the same lock).
session_begin() { # id description
  take_device_lock
  if [ "$(apk_matches | cut -c1-3)" != "yes" ]; then
    echo "installing this worktree's build (the device held $(installed_apk_id))"
    adb install -r "$APK" > "${TMPDIR:-/tmp}/cal-install.$$" 2>&1 || { cat "${TMPDIR:-/tmp}/cal-install.$$"; rm -f "${TMPDIR:-/tmp}/cal-install.$$"; exit 4; }
    rm -f "${TMPDIR:-/tmp}/cal-install.$$"
    sleep 6
  fi
  row_begin "$1" "$2"
  # Earlier runs are kept (rule 6): the row's log of this run is also copied to <ROW>/runs/<stamp>.txt at session_end.
  RUN_STAMP="$(date +%Y%m%d-%H%M%S)"
}

session_end() {
  row_end; local rc=$?
  mkdir -p "$ROW_DIR/runs"; cp "$LOG" "$ROW_DIR/runs/$RUN_STAMP.txt"
  return $rc
}

top_activity() { S dumpsys activity activities | grep -m1 'topResumedActivity' | sed -E 's/.* u0 ([^ ]+) .*/\1/'; }

# After a launch (C-6): keep the ring, force-stop, Home.
c6() { ring_save; adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 4; }

absent_in() { # name needle slice
  if printf '%s\n' "$3" | grep -q 'wall='; then assert_absent "$1" "$2" "$3"; else _verdict FAIL "$1" "the ring slice is empty or unreadable, so the absence proves nothing"; fi
}

# The first slice line that holds a needle, without its stamp.
line_of() { printf '%s\n' "$1" | grep -F -- "$2" | tail -1 | sed 's/^.*wall=[0-9]* //'; }

open_cal() { # am-args...
  adb shell am start -W -n "$CAL_ACT" "$@" < /dev/null >/dev/null 2>&1
  sleep 2
}

node_desc() { # dump.xml resource-id -> the node's content-desc
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'content-desc="([^"]*)"', s)
        print(m.group(1) if m else "")
        break
PY
}

# One attribute of a node (selected, checked, text ...).
node_attr() { # dump.xml resource-id attr
  python3 - "$1" "$2" "$3" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'\b%s="([^"]*)"' % re.escape(sys.argv[3]), s)
        print(m.group(1) if m else "")
        break
PY
}

# How many nodes carry a resource-id (exact), or begin with a prefix.
count_nodes() { grep -o "resource-id=\"$2\"" "$1" | wc -l | tr -d ' '; }
count_prefix() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | wc -l | tr -d ' '; }
ids_prefix() { grep -o "resource-id=\"$2[^\"]*\"" "$1" | sed 's/resource-id="//; s/"$//' | sort -u | tr '\n' ' '; }

# Dump, then tap a node; fails the row loudly when the node is not there.
tap() { # resource-id [settle]
  local d="$ROW_DIR/.tap.xml"
  dump_ui "$d" || true
  if [ "$(has_node "$d" "$1")" != yes ]; then _verdict FAIL "tap $1" "no such node on screen ($(ids_prefix "$d" cal_ | cut -c1-300))"; return 1; fi
  tap_node "$d" "$1"; sleep "${2:-1.2}"
}

# Roll a LoopSpinner to VALUE (phase 15's spin_to, qa/phase-15/scripts/clock.sh:227): swipes inside its own bounds,
# re-reading the spinner's content-desc (its selected value) after each.
spin_to() { # tag value values-csv
  local tag="$1" target="$2" csv="$3" d="$ROW_DIR/.spin.xml" i cur delta b cx cy dy dur steps
  for i in $(seq 0 20); do
    dump_ui "$d" || return 1
    cur="$(node_desc "$d" "$tag")"
    if [ "$cur" = "$target" ]; then note "spin $tag -> [$cur] after $i swipe(s)"; return 0; fi
    delta="$(python3 -c '
import sys
vals = sys.argv[1].split(","); n = len(vals)
try: ci, ti = vals.index(sys.argv[2]), vals.index(sys.argv[3])
except ValueError: print(0); sys.exit()
d = (ti - ci) % n
if d > n // 2: d -= n
print(max(-2, min(2, d)))' "$csv" "$cur" "$target")"
    [ "$delta" != 0 ] || { note "spin $tag: [$cur] and [$target] not both in the values"; return 1; }
    b="$(bounds "$d" "$tag")"
    # shellcheck disable=SC2086
    set -- $b
    cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
    steps=${delta#-}
    dy=$(( -delta * 32 * PX ))
    dur=$(( 400 * steps ))
    adb shell input swipe "$cx" "$cy" "$cx" $(( cy + dy )) "$dur"
    sleep 0.9
  done
  note "spin $tag: gave up at [$cur], wanted [$target]"
  return 1
}

# ---------------------------------------------------------------- the clock (every move FORWARDS; p15.sh:57-78)
device_ms() { adb shell date +%s%3N | tr -d '\r'; }
jump_clock() { # epoch_ms
  adb shell settings put global auto_time 0
  adb shell cmd alarm set-time "$1" >/dev/null
  sleep 1
  device_ms
}
clock_restore() {
  adb root >/dev/null 2>&1; adb wait-for-device
  adb shell date -u "$(date -u +%m%d%H%M%Y.%S)" >/dev/null
  adb shell settings put global auto_time 1
  adb unroot >/dev/null 2>&1; adb wait-for-device
  local d h
  d="$(adb shell date +%s | tr -d '\r')"; h="$(date +%s)"
  assert_within "device clock back within 2 s of the host (RV12)" "$h" "$d" 2
  adb shell am force-stop app.tileshell
  adb shell input keyevent KEYCODE_HOME
  sleep 3
}

# ---------------------------------------------------------------- calendar fixtures (through the provider, as the gate's rows will)
cals() { S content query --uri "$CALS" --projection _id:account_name:account_type:calendar_displayName:calendar_access_level:calendar_color; }
cal_id() { # account_name [display name]
  cals | grep -F "account_name=$1," | { if [ -n "${2:-}" ]; then grep -F "calendar_displayName=$2,"; else cat; fi; } | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1
}
# j6.sh's mkcal: an account-style calendar through the sync-adapter path. account name display primary [access] [type]
mkcal() {
  adb shell "content insert --uri '$CALS?$SA&account_name=$1&account_type=${6:-com.google}' --bind account_name:s:$1 --bind account_type:s:${6:-com.google} --bind name:s:'$2' --bind calendar_displayName:s:'$2' --bind calendar_access_level:i:${4:-700} --bind ownerAccount:s:$1 --bind visible:i:1 --bind sync_events:i:1 --bind isPrimary:i:${3:-0} --bind calendar_color:i:-16776961" < /dev/null
}
rmcal() { # account type
  adb shell "content delete --uri '$CALS?$SA&account_name=$1&account_type=$2' --where \"account_name='$1'\"" < /dev/null >/dev/null 2>&1
}
# The dev sessions' account calendars and the Birthdays calendar, gone (their events cascade).
cal_fixtures_down() {
  rmcal qa.personal@example.com com.google; rmcal qa.work@example.com com.google; rmcal qa LOCAL
  adb shell "content delete --uri '$CALS?$SA&account_name=Tessera%20Birthdays&account_type=LOCAL' --where \"account_name='Tessera Birthdays'\"" < /dev/null >/dev/null 2>&1
}
cal_lists() { [ -n "$1" ] && cals | grep -q "_id=$1,"; }
# An event row, as a normal app's insert (what `content insert` is). calendar title dtstart dtend [extra binds...]; prints the new _id.
mkevent() {
  local cal="$1" title="$2" start="$3" end="$4"; shift 4
  adb shell "content insert --uri $EVENTS --bind calendar_id:i:$cal --bind title:s:'$title' --bind dtstart:l:$start --bind dtend:l:$end --bind eventTimezone:s:$(S getprop persist.sys.timezone) $*" < /dev/null > /dev/null 2>&1
  S "content query --uri $EVENTS --projection _id --where \"title='$title' AND calendar_id=$cal\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tail -1
}
# A repeating event: RRULE + DURATION, no DTEND (the provider's rule). calendar title dtstart rrule duration [extra binds...]
mkseries() {
  local cal="$1" title="$2" start="$3" rrule="$4" duration="$5"; shift 5
  adb shell "content insert --uri $EVENTS --bind calendar_id:i:$cal --bind title:s:'$title' --bind dtstart:l:$start --bind duration:s:$duration --bind rrule:s:'$rrule' --bind eventTimezone:s:$(S getprop persist.sys.timezone) $*" < /dev/null > /dev/null 2>&1
  S "content query --uri $EVENTS --projection _id --where \"title='$title' AND calendar_id=$cal\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tail -1
}
event_ids() { # where
  S "content query --uri $EVENTS --projection _id --where \"$1\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | tr '\n' ' '
}
event_rows() { # projection where
  S "content query --uri $EVENTS --projection $1 --where \"$2\""
}
event_count() { # where
  S "content query --uri $EVENTS --projection _id --where \"$1\"" | grep -c '_id='
}
rmevents() { # where
  adb shell "content delete --uri $EVENTS --where \"$1\"" < /dev/null >/dev/null 2>&1
}
sync_json() { adb shell "run-as app.tileshell cat files/calendar_sync.json" < /dev/null 2>/dev/null | tr -d '\r'; }
granted() { # permission -> true / false
  S dumpsys package app.tileshell | grep -m1 "$1: granted=" | sed 's/.*granted=\([a-z]*\).*/\1/'
}
# The device's local midnight today, and N days on, in epoch ms.
day_ms() { # [days-from-today] [HH:MM]
  adb shell "date -d \"\$(date +%Y-%m-%d) ${2:-00:00}\" +%s" < /dev/null | tr -d '\r' | awk -v d="${1:-0}" '{printf "%d\n", ($1 + d * 86400) * 1000}'
}
# UTC midnight of the device's local date, N days on: an all-day event's dtstart.
utc_day_ms() { # [days-from-today]
  adb shell "date -u -d \"\$(date +%Y-%m-%d) 00:00\" +%s" < /dev/null | tr -d '\r' | awk -v d="${1:-0}" '{printf "%d\n", ($1 + d * 86400) * 1000}'
}
device_date() { # [days-from-today] -> yyyy-mm-dd
  python3 -c 'import sys, datetime; print(datetime.date.fromisoformat(sys.argv[1]) + datetime.timedelta(days=int(sys.argv[2])))' "$(S date +%Y-%m-%d)" "${1:-0}"
}

# ---------------------------------------------------------------- the editor
# Type into an editor field: tap it, type through the shell's own keyboard, Enter (Done gives the focus up and the keyboard goes).
type_field() { # field text
  tap "cal_editor_field:$1" 1.2 || return 1
  adb shell input text "$(printf '%s' "$2" | sed 's/ /%s/g')"; sleep 1
  adb shell input keyevent KEYCODE_ENTER; sleep 1.2
}
# Set a time field through the picker's loop spinners (12-hour form on this AVD). field hour(1-12) minute(00-59) AM|PM
set_time() {
  tap "cal_editor_field:$1" 1.5 || return 1
  local hours="1,2,3,4,5,6,7,8,9,10,11,12" mins; mins="$(seq -w 0 59 | paste -sd,)"
  if [ "$(S settings get system time_12_24)" = 24 ]; then
    spin_to cal_time_spinner:hour "$2" "$(seq -w 0 23 | paste -sd,)"
  else
    spin_to cal_time_spinner:ampm "$4" "AM,PM"
    spin_to cal_time_spinner:hour "$2" "$hours"
  fi
  spin_to cal_time_spinner:minute "$3" "$mins"
  tap cal_time_ok 1.2
}
# The text a pick box shows (its tag is on the box; the text is the child under it).
field_text() { # dump.xml field
  python3 - "$1" "cal_editor_field:$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
i = xml.find('resource-id="%s"' % sys.argv[2])
if i < 0: print(""); sys.exit()
start = xml.rfind('<node', 0, i)
m = re.search(r'text="([^"]*)"', xml[start:xml.find('>', i)])
if m and m.group(1): print(m.group(1)); sys.exit()
t = re.findall(r'text="([^"]+)"', xml[i:i + 900])
print(t[0] if t else "")
PY
}
tessera_id() { cal_id Tessera; }
# The Tessera calendar, gone (the Acceptance preamble's command): its events cascade.
rm_tessera() { rmcal Tessera LOCAL; }

# The Day view of the day holding an instant (the exported VIEW on a time URI).
open_day() { # epoch-ms
  adb shell am start -W -n "$CAL_ACT" -a android.intent.action.VIEW -d "content://com.android.calendar/time/$1" < /dev/null >/dev/null 2>&1
  sleep 2.5
}
# An event's page (the exported VIEW on an event), optionally at one occurrence.
open_event() { # id [begin end]
  if [ -n "${2:-}" ]; then
    adb shell am start -W -n "$CAL_ACT" -a android.intent.action.VIEW -d "content://com.android.calendar/events/$1" --el beginTime "$2" --el endTime "$3" < /dev/null >/dev/null 2>&1
  else
    adb shell am start -W -n "$CAL_ACT" -a android.intent.action.VIEW -d "content://com.android.calendar/events/$1" < /dev/null >/dev/null 2>&1
  fi
  sleep 2.5
}
# How many instances of an event the provider expands inside [from, to].
instance_count() { # from to event_id
  S "content query --uri content://com.android.calendar/instances/when/$1/$2 --projection event_id:begin --where \"event_id=$3\"" | grep -c 'event_id='
}
