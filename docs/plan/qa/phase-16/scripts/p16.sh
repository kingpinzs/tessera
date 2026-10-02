#!/usr/bin/env bash
# Phase 16's driver floor (build task 8 (a); the Acceptance preamble's "Round 3 floor"), sourced by every driver AFTER
# lib.sh (phase 03's floor, symlinked here: row_begin / row_end, the asserts, ring_mark / ring_since / reply_since /
# ring_save, record, ensure_start, type_request, wake_device). Everything here drives the real shell on the emulator;
# nothing is simulated. No row uses the microphone or the host's audio.
#
# Copies, because the files they live in cannot be sourced without their rows' own state (r3 V9, V10):
#   gdump                         qa/phase-15/scripts/p15.sh:24-49
#   jump_clock / clock_restore    qa/phase-15/scripts/p15.sh:57-78
#   shell_bytes                   qa/phase-15/scripts/clock.sh:423-436
#   absent_in, c6                 qa/phase-14/scripts/p14.sh:156-162, :29-35
#   mkcal                         qa/phase-03/scripts/j6.sh:18-20 (it lives inside that row)
# New here: q, cal_lists, cal_count, cal_fixtures_down, people_add, people_fixtures_up / _down, shell_alarms_pending.
export ANDROID_SERIAL=emulator-5554

P16="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P03S="$(cd "$P16/../phase-03/scripts" && pwd)"
P02S="$(cd "$P16/../phase-02/scripts" && pwd)"
P01S="$(cd "$P16/../phase-01/scripts" && pwd)"
P12S="$(cd "$P16/../phase-12/scripts" && pwd)"
P14S="$(cd "$P16/../phase-14/scripts" && pwd)"
BASELINE="$P16/baseline_layout.json"
# Every include a phase 16 driver may source is stamped in the log's header (lib.sh row_begin; gate review B, note 7).
STAMP_FILES="$P16/scripts/p16.sh $P16/scripts/cal_lib.sh $P16/scripts/people_lib.sh $P16/scripts/trust_lib.sh $P16/scripts/cal_geo.py $P16/scripts/cal_px.py $P16/scripts/cal_frames.py"
CALENDAR_ACTIVITY="app.tileshell/.calendar.CalendarActivity"
PEOPLE_ACTIVITY="app.tileshell/.people.PeopleActivity"
DRV_RUNNER="app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner"

# layout_json, layout_save, layout_restore (the verified restore, C-3).
. "$P02S/layout.sh"

# ---------------------------------------------------------------- the device shell

# One command, one string. A URI that carries `&` or a where-clause with a quoted literal must reach the device shell
# as ONE quoted string: passed as separate words adb joins them, the device shell backgrounds at the `&` (J6 run 1) and
# eats the quotes (BUILDSTART run 1: every title lookup read empty).
# stdin is /dev/null: `adb shell` reads its stdin, so inside a `while read … done < file` loop it would swallow the rest
# of the file and the loop would run once (the self-test's run 1: one fixture deleted, seven left).
q() { adb shell "$1" 2>&1 </dev/null | tr -d '\r'; }

# The activity on top, short form (app.tileshell/.calendar.CalendarActivity).
top_activity() {
  adb shell dumpsys activity activities | grep -m1 'topResumedActivity' | tr -d '\r' | sed -E 's/.* u0 ([^ ]+) .*/\1/'
}

perm_granted() { # permission short name -> true / false
  adb shell dumpsys package app.tileshell | tr -d '\r' | grep -m1 "android.permission.$1: granted" | sed -E 's/.*granted=([a-z]+).*/\1/'
}

# The pan into phase 14's pod bay (p14.sh:13): inside the page, clear of the left gesture inset.
swipe_right() { adb shell input swipe 200 1200 950 1200 250; sleep "${1:-1.5}"; }

# ---------------------------------------------------------------- copies (see the header)

# A screen that never idles — Start while the People tile cycles — is dumped through phase 05's gesture driver with
# setWaitForIdleTimeout(0) (C-10). A new file per attempt; after 20 tries the plain uiautomator dump is the fallback.
gdump() { # out.xml
  local out="$1" i name
  : > "$out.drv"
  for i in $(seq 1 20); do
    name="p16_$(date +%s%N)_$i.xml"
    adb shell am instrument -r -w -e op dump -e out "/sdcard/Download/$name" "$DRV_RUNNER" >> "$out.drv" 2>&1
    adb shell cat "/sdcard/Download/$name" > "$out" 2>/dev/null
    adb shell rm -f "/sdcard/Download/$name" >/dev/null 2>&1
    if grep -q '<node' "$out"; then
      grep -o 'gesture.dump.windows=.*' "$out.drv" | tail -1 | tr -d '\r' > "$out.windows"
      return 0
    fi
    echo "(attempt $i read no nodes from $name)" >> "$out.drv"
    sleep 0.25
  done
  echo "(falling back to uiautomator dump)" >> "$out.drv"
  if adb shell uiautomator dump /sdcard/Download/p16_fallback.xml >/dev/null 2>&1; then
    adb shell cat /sdcard/Download/p16_fallback.xml > "$out" 2>/dev/null
    adb shell rm -f /sdcard/Download/p16_fallback.xml >/dev/null 2>&1
    if grep -q '<node' "$out"; then : > "$out.windows"; return 0; fi
  fi
  echo "(dump failed)" > "$out"
  return 1
}

device_ms() { adb shell date +%s%3N | tr -d '\r'; }

# Jump the emulator's clock FORWARDS (a row never jumps backwards: ring_since keeps every line whose wall >= the MARK,
# so after a backwards jump a stale line passes — r3 V4). Prints the device's ms afterwards.
jump_clock() { # epoch_ms
  local now; now="$(device_ms)"
  if [ "$1" -lt "$now" ] && [ -z "${ALLOW_BACKWARDS:-}" ]; then
    echo "jump_clock: $1 is before the device's now ($now); a row never jumps backwards (r3 V4)" >&2
    return 2
  fi
  adb shell settings put global auto_time 0
  adb shell cmd alarm set-time "$1" >/dev/null
  sleep 1
  device_ms
}

# RV12's clock restore: the host's UTC time while root, automatic time back on, unroot, the device-host difference
# asserted within 2 s, then force-stop and Home so no ring line stamped on the jumped clock outlives it.
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

# The shell uid's byte counters summed over every bucket of `dumpsys netstats --uid`, as "rx tx" (after a forced poll).
shell_bytes() {
  local uid
  uid="$(adb shell cmd package list packages -U app.tileshell 2>/dev/null | tr -d '\r' | sed -n 's/^package:app.tileshell uid://p' | head -1)"
  adb shell dumpsys netstats --poll >/dev/null 2>&1
  adb shell dumpsys netstats --uid 2>/dev/null | tr -d '\r' | python3 -c '
import re, sys
uid = sys.argv[1]; rx = tx = 0; inside = False
for l in sys.stdin:
    if l.startswith("  ident="):
        inside = ("uid=%s " % uid) in l
        continue
    if inside:
        m = re.search(r"\brb=(\d+) rp=\d+ tb=(\d+)", l)
        if m: rx += int(m.group(1)); tx += int(m.group(2))
print(rx, tx)' "$uid"
}

# An absence check that cannot pass on an unreadable ring: the slice must hold a ring line (a `wall=` stamp) first.
absent_in() { # name needle slice
  if printf '%s\n' "$3" | grep -q 'wall='; then
    assert_absent "$1" "$2" "$3"
  else
    _verdict FAIL "$1" "the ring slice is empty or unreadable, so the absence proves nothing"
  fi
}

# C-6: after any launch — keep the ring, force-stop, Home, wait for Start. Follow it with ensure_start.
c6() {
  ring_save
  adb shell am force-stop app.tileshell
  sleep 1
  adb shell input keyevent KEYCODE_HOME
  sleep 4
}

# ---------------------------------------------------------------- calendars

CAL=content://com.android.calendar/calendars
EVENTS=content://com.android.calendar/events
SA="caller_is_syncadapter=true"

# An account calendar as j6.sh makes one: account type com.google through the sync-adapter URI. The fourth argument is
# the access level (default 700, owner; 200 = read). BUILDSTART 3: such calendars are DROPPED when the calendar
# provider's process restarts — a row makes them after its last restart (a reboot, a pm disable / enable of the provider).
mkcal() { # account display primary [level=700]
  q "content insert --uri '$CAL?$SA&account_name=$1&account_type=com.google' --bind account_name:s:$1 --bind account_type:s:com.google --bind name:s:'$2' --bind calendar_displayName:s:'$2' --bind calendar_access_level:i:${4:-700} --bind ownerAccount:s:$1 --bind visible:i:1 --bind sync_events:i:1 --bind isPrimary:i:$3 --bind calendar_color:i:-16776961"
}
# The LOCAL QA calendar of the preamble (account qa / LOCAL, owner access).
mkcal_qa() {
  q "content insert --uri '$CAL?$SA&account_name=qa&account_type=LOCAL' --bind account_name:s:qa --bind account_type:s:LOCAL --bind name:s:QA --bind calendar_displayName:s:QA --bind calendar_access_level:i:700 --bind ownerAccount:s:qa --bind visible:i:1 --bind sync_events:i:1"
}
cals() { q "content query --uri $CAL --projection _id:account_name:account_type:calendar_displayName:calendar_access_level:visible"; }
# A calendar's _id by its account name ("Tessera", "qa", "qa.work@example.com", "Tessera Birthdays").
cal_id() { # account-name [display-name]
  cals | grep -F "account_name=$1," | { if [ -n "${2:-}" ]; then grep -F "calendar_displayName=$2,"; else cat; fi; } | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1
}
tessera_id() { cals | grep -F 'account_name=Tessera,' | grep -F 'account_type=LOCAL' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1; }

# r3 V11: a count read from the provider first asserts that the calendar it counts still lists — "Work 0" is also what a
# vanished Work calendar reads.
cal_lists() { # id label
  local row
  row="$(q "content query --uri $CAL --projection _id:calendar_displayName --where \"_id=$1\"")"
  assert_contains "cal_lists: calendar $1 (${2:-}) still lists" "_id=$1," "$row"
}
# The events in a calendar, one "id|title" per line (deleted rows left out).
cal_events() { # id
  q "content query --uri $EVENTS --projection _id:title:deleted --where \"calendar_id=$1 AND deleted=0\"" | sed -n 's/.*_id=\([0-9]*\), title=\(.*\), deleted=0.*/\1|\2/p'
}
# cal_lists, then the number of events in the calendar.
cal_count() { # id label
  cal_lists "$1" "${2:-}"
  cal_events "$1" | grep -c '|'
}
event_id() { # title [calendar id] -> the newest live event of that title
  local w="title='$1' AND deleted=0"
  [ -n "${2:-}" ] && w="$w AND calendar_id=$2"
  q "content query --uri $EVENTS --projection _id:title --where \"$w\" --sort '_id DESC'" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1
}
event_row() { # id projection (colon-separated)
  q "content query --uri $EVENTS --projection $2 --where \"_id=$1\""
}
# A driver-inserted event (a normal insert; the caller's calendar decides who may see it change).
mkevent() { # calendar-id title start-ms end-ms [extra --bind args as one string]
  q "content insert --uri $EVENTS --bind calendar_id:i:$1 --bind title:s:'$2' --bind dtstart:l:$3 --bind dtend:l:$4 --bind eventTimezone:s:$(adb shell getprop persist.sys.timezone | tr -d '\r') ${5:-}"
  event_id "$2" "$1"
}
rm_event() { # id  (a never-synced event has no _sync_id, so the provider removes the row rather than marking it deleted)
  q "content delete --uri $EVENTS/$1" >/dev/null
}
rm_events_titled() { # title...
  local t
  for t in "$@"; do q "content delete --uri $EVENTS --where \"title='$t'\"" >/dev/null; done
}

# The fixtures down: the QA, Personal, Shared and Work calendars through the sync-adapter URI (their events cascade),
# the Birthdays calendar, and any test events named (by title, wherever they are — Tessera is kept: it is the shell's own).
cal_fixtures_down() { # [event title...]
  q "content delete --uri '$CAL?$SA&account_name=qa&account_type=LOCAL' --where \"account_name='qa'\"" >/dev/null
  local a
  for a in qa.personal@example.com qa.work@example.com; do
    q "content delete --uri '$CAL?$SA&account_name=$a&account_type=com.google' --where \"account_name='$a'\"" >/dev/null
  done
  q "content delete --uri '$CAL?$SA&account_name=Tessera%20Birthdays&account_type=LOCAL' --where \"account_name='Tessera Birthdays'\"" >/dev/null
  [ "$#" -gt 0 ] && rm_events_titled "$@"
  local left
  left="$(cals | grep -E 'account_name=(qa|qa\.personal@example\.com|qa\.work@example\.com|Tessera Birthdays),' || true)"
  assert_eq "cal_fixtures_down: no QA, account or Birthdays calendar is left" "" "$left"
}
# The preamble's command: the shell's own calendar deleted (a row that needs it absent first).
tessera_down() {
  q "content delete --uri '$CAL?$SA&account_name=Tessera&account_type=LOCAL' --where \"account_name='Tessera'\"" >/dev/null
}

# ---------------------------------------------------------------- contacts

RAW=content://com.android.contacts/raw_contacts
DATA=content://com.android.contacts/data
raw_count() { q "content query --uri $RAW --projection _id --where \"deleted=0\"" | grep -c '_id='; }

# One raw contact with its data rows, as provision.sh inserts Mom. Phone-only (a NULL account) unless an account is
# named. Its raw-contact id is appended to $ROW_DIR/people-fixtures.ids and printed.
people_add() { # name [phone] [email] [account_name account_type]
  local name="$1" phone="${2:-}" email="${3:-}" acct="${4:-}" type="${5:-}" rid
  if [ -n "$acct" ]; then
    q "content insert --uri $RAW --bind account_name:s:$acct --bind account_type:s:$type" >/dev/null
  else
    q "content insert --uri $RAW --bind account_type:n: --bind account_name:n:" >/dev/null
  fi
  rid="$(q "content query --uri $RAW --projection _id --sort '_id DESC'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)"
  [ -n "$rid" ] || { echo "people_add: no raw contact id for $name" >&2; return 2; }
  echo "$rid" >> "$ROW_DIR/people-fixtures.ids"
  [ -n "$name" ] && q "content insert --uri $DATA --bind raw_contact_id:i:$rid --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$name'" >/dev/null
  [ -n "$phone" ] && q "content insert --uri $DATA --bind raw_contact_id:i:$rid --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:'$phone' --bind data2:i:2" >/dev/null
  [ -n "$email" ] && q "content insert --uri $DATA --bind raw_contact_id:i:$rid --bind mimetype:s:vnd.android.cursor.item/email_v2 --bind data1:s:$email --bind data2:i:1" >/dev/null
  echo "$rid"
}
raw_of() { # display name -> its raw contact ids, space-separated
  q "content query --uri $RAW --projection _id:display_name --where \"display_name='$1' AND deleted=0\"" | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | tr '\n' ' ' | sed 's/ $//'
}
contact_of() { # raw id -> its contact (aggregate) id
  q "content query --uri $RAW --projection _id:contact_id --where \"_id=$1\"" | sed -n 's/.*contact_id=\([0-9]*\).*/\1/p' | head -1
}
lookup_of() { # contact id -> its lookup key
  q "content query --uri content://com.android.contacts/contacts --projection _id:lookup --where \"_id=$1\"" | sed -n 's/.*lookup=\(.*\)$/\1/p' | head -1
}

# The standing fixture set (E10's; E11–E13, E15, E17, E20 and E27 use its Ann and Bob): all phone-only. Cara Diaz is
# two raw contacts sharing one number, so the provider aggregates them (r3 V15).
people_fixtures_up() {
  : > "$ROW_DIR/people-fixtures.ids"
  RAW_BEFORE="$(raw_count)"
  ANN="$(people_add 'Ann Lee' '+1 555 000 0001' ann@example.com)"
  BOB="$(people_add 'Bob Stone' '+1 555 000 0002')"
  ZOE="$(people_add 'Zoë Ǻrén')"
  ZHANG="$(people_add '张伟')"
  NUMBER_ONLY="$(people_add '' '+1 555 000 0009')"
  CARA1="$(people_add 'Cara Diaz' '+1 555 000 0003')"
  CARA2="$(people_add 'Cara Diaz' '+1 555 000 0003')"
  sleep 2   # the provider aggregates on a short delay
  note "people_fixtures_up: raw ids ann=$ANN bob=$BOB zoe=$ZOE zhang=$ZHANG number-only=$NUMBER_ONLY cara=$CARA1,$CARA2 (raw contacts before: $RAW_BEFORE)"
}
# Deleted BY ID, through the sync-adapter path (so the rows are gone, not marked) — never by account: provision.sh's Mom
# (qa / qa, provision.sh:125-137) is never touched. Asserts the raw-contact count is back where the row began.
people_fixtures_down() {
  local id
  if [ -f "$ROW_DIR/people-fixtures.ids" ]; then
    while read -r id; do
      [ -n "$id" ] && q "content delete --uri '$RAW/$id?$SA'" >/dev/null
    done < "$ROW_DIR/people-fixtures.ids"
    mv "$ROW_DIR/people-fixtures.ids" "$ROW_DIR/people-fixtures.deleted-$(date +%H%M%S).ids"
  fi
  [ -n "${RAW_BEFORE:-}" ] && assert_eq "people_fixtures_down: the raw_contacts count is the count before the row" "$RAW_BEFORE" "$(raw_count)"
}

# ---------------------------------------------------------------- alarms

# Every PENDING alarm that names app.tileshell (clock_pending's form, p15.sh:82-91, widened to the whole package — the
# shell's reminders and clock alarms are its own; E6 asserts a calendar reminder adds none of the shell's — r3 V7).
shell_alarms_pending() {
  adb shell dumpsys alarm | tr -d '\r' | python3 -c '
import re, sys
lines = sys.stdin.read().splitlines(); n = 0; inside = False
for l in lines:
    if re.match(r"^\s*\d+ pending alarms:", l): inside = True; continue
    if inside and not l.startswith("    "): inside = False
    if inside and re.search(r"Alarm\{[^}]*\bapp\.tileshell\}", l): n += 1
print(n)'
}
